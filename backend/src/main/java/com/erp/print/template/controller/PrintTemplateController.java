package com.erp.print.template.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.Result;
import com.erp.config.service.SysConfigService;
import com.erp.print.template.dto.PrintTemplateDTO;
import com.erp.print.template.entity.PrintTemplate;
import com.erp.print.template.entity.PrintTemplateField;
import com.erp.print.template.service.PrintTemplateService;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/print-template")
public class PrintTemplateController {

    private final PrintTemplateService printTemplateService;
    private final SysConfigService sysConfigService;
    private final JdbcTemplate jdbcTemplate;
    private final com.erp.print.template.service.PrintTemplateFieldCatalog fieldCatalog;
    private final com.erp.print.template.service.PrintPdfService printPdfService;
    private final com.erp.customfield.service.CustomFieldService customFieldService;

    public PrintTemplateController(PrintTemplateService printTemplateService,
                                   SysConfigService sysConfigService,
                                   JdbcTemplate jdbcTemplate,
                                   com.erp.print.template.service.PrintTemplateFieldCatalog fieldCatalog,
                                   com.erp.print.template.service.PrintPdfService printPdfService,
                                   com.erp.customfield.service.CustomFieldService customFieldService) {
        this.printTemplateService = printTemplateService;
        this.sysConfigService = sysConfigService;
        this.jdbcTemplate = jdbcTemplate;
        this.fieldCatalog = fieldCatalog;
        this.printPdfService = printPdfService;
        this.customFieldService = customFieldService;
    }

    @GetMapping
    public Result<Page<PrintTemplate>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(printTemplateService.pageQuery(current, size, keyword, docType, status, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<PrintTemplateDTO> getDetail(@PathVariable Long id) {
        return Result.success(printTemplateService.getDetail(id));
    }

    @GetMapping("/default/{docType}")
    public Result<PrintTemplate> getDefaultTemplate(@PathVariable String docType) {
        return Result.success(printTemplateService.getDefaultTemplate(docType));
    }

    /**
     * 打印模板「可用字段」全量（唯一来源，2026-10-10 起由后端提供）。
     * 前端打印模板页面改为读这里；结构与原页面前端常量完全一致（commonItemFields + docTypeFieldsMap）。
     * 2026-11-23：在实体全量字段之上，按当前租户追加「自定义字段」定义（表头 + 明细行）。
     */
    @GetMapping("/field-map")
    public Result<Map<String, Object>> fieldMap() {
        Map<String, Object> all = new java.util.LinkedHashMap<>(fieldCatalog.all());
        try {
            Object dtmObj = all.get("docTypeFieldsMap");
            if (dtmObj instanceof Map<?, ?> dtm) {
                Map<String, Object> copy = new java.util.LinkedHashMap<String, Object>();
                for (Map.Entry<?, ?> e : dtm.entrySet()) {
                    String docType = String.valueOf(e.getKey());
                    Object entryObj = e.getValue();
                    String[] customTypes = fieldCatalog.customEntityTypesOf(docType);
                    if (customTypes == null || !(entryObj instanceof Map<?, ?> entry)) {
                        copy.put(docType, entryObj);
                        continue;
                    }
                    // 只深拷贝需要追加自定义字段的类型，避免污染共享缓存
                    Map<String, Object> entryCopy = new java.util.LinkedHashMap<String, Object>((Map<String, Object>) entry);
                    appendCustomFields(entryCopy, "header", customTypes[0]);
                    if (customTypes[1] != null && !customTypes[1].isBlank()) {
                        appendCustomFields(entryCopy, "items", customTypes[1]);
                    }
                    // 【2026-11-25】生产工单：追加「工序表」可用字段（标准工序字段 + 工序自定义字段）
                    List<Map<String, Object>> opFields = fieldCatalog.operationFields(docType);
                    if (!opFields.isEmpty()) {
                        entryCopy.put("operations", opFields);
                    }
                    copy.put(docType, entryCopy);
                }
                all.put("docTypeFieldsMap", copy);
            }
        } catch (Exception e) {
            log.warn("[打印模板] 自定义字段并入可用字段失败，按不含自定义字段的清单返回", e);
        }
        // 客户信息 / 员工信息是全局字段组（不随单据类型），自定义字段同样按当前租户动态追加
        try {
            appendCustomFieldsToGroup(all, "customerFields", "customer", "custCf");
            appendCustomFieldsToGroup(all, "employeeFields", "employee", "salerCf");
        } catch (Exception e) {
            log.warn("[打印模板] 客户/员工自定义字段并入可用字段失败，按不含自定义字段的清单返回", e);
        }
        return Result.success(all);
    }

    /** 全局字段组追加自定义字段（客户信息 / 员工信息）：key 带前缀，避免与单据字段或组内固定字段同名 */
    @SuppressWarnings("unchecked")
    private void appendCustomFieldsToGroup(Map<String, Object> all, String groupKey, String entityType, String prefix) {
        Object groupObj = all.get(groupKey);
        if (!(groupObj instanceof List<?>)) return;
        List<Map<String, Object>> fields = new java.util.ArrayList<>((List<Map<String, Object>>) (List<?>) groupObj);
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        for (Map<String, Object> f : fields) keys.add(String.valueOf(f.get("key")));
        int added = 0;
        for (com.erp.customfield.entity.CustomField def : customFieldService.listEnabledByEntity(entityType)) {
            if (def.getFieldKey() == null || def.getFieldKey().isBlank()) continue;
            String key = "custCf".equals(prefix)
                    ? fieldCatalog.customerCustomFieldKey(def.getFieldKey())
                    : "salerCf".equals(prefix)
                    ? fieldCatalog.employeeCustomFieldKey(def.getFieldKey())
                    : def.getFieldKey();
            if (key.isBlank() || keys.contains(key)) continue;
            Map<String, Object> one = new java.util.LinkedHashMap<>();
            one.put("key", key);
            one.put("label", def.getFieldLabel());
            fields.add(one);
            keys.add(key);
            added++;
        }
        if (added > 0) all.put(groupKey, fields);
    }

    /** 把某实体的启用自定义字段定义（key/label）追加到 header / items 字段清单，已存在同 key 的不重复追加 */
    @SuppressWarnings("unchecked")
    private void appendCustomFields(Map<String, Object> entry, String group, String entityType) {
        Object groupObj = entry.get(group);
        if (!(groupObj instanceof List<?>)) return;
        List<Map<String, Object>> fields = new java.util.ArrayList<>((List<Map<String, Object>>) (List<?>) groupObj);
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        for (Map<String, Object> f : fields) keys.add(String.valueOf(f.get("key")));
        int added = 0;
        for (com.erp.customfield.entity.CustomField def : customFieldService.listEnabledByEntity(entityType)) {
            if (def.getFieldKey() == null || keys.contains(def.getFieldKey())) continue;
            Map<String, Object> one = new java.util.LinkedHashMap<>();
            one.put("key", def.getFieldKey());
            one.put("label", def.getFieldLabel());
            fields.add(one);
            keys.add(def.getFieldKey());
            added++;
        }
        if (added > 0) entry.put(group, fields);
    }

    @PostMapping
    public Result<Void> save(@RequestBody PrintTemplateDTO templateDTO) {
        printTemplateService.saveTemplate(templateDTO);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody PrintTemplateDTO templateDTO) {
        printTemplateService.updateTemplate(templateDTO);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        printTemplateService.deleteTemplate(id);
        return Result.success();
    }

    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        printTemplateService.setDefault(id);
        return Result.success();
    }

    @PostMapping("/{id}/copy")
    public Result<Long> copy(@PathVariable Long id,
                             @RequestParam(required = false) String newName) {
        Long newId = printTemplateService.copyTemplate(id, newName);
        return Result.success(newId);
    }

    @GetMapping("/{id}/fields")
    public Result<List<PrintTemplateField>> getFields(@PathVariable Long id) {
        return Result.success(printTemplateService.getTemplateFields(id));
    }

    @PostMapping("/{id}/render")
    public Result<String> render(@PathVariable Long id,
                                 @RequestBody Map<String, Object> data) {
        String html = printTemplateService.renderTemplate(id, data);
        return Result.success(html);
    }

    /**
     * 设计态预览：用样例数据渲染当前正在编辑、尚未保存的模板内容。
     * 与真实打印同一套渲染逻辑，保证预览与打印一致（2026-11-24）。
     */
    @PostMapping("/preview")
    public Result<String> preview(@RequestBody PrintTemplateDTO templateDTO) {
        return Result.success(printTemplateService.previewHtml(templateDTO));
    }

    /**
     * 按单据打印: 自动查找默认模板 → 获取单据数据 → 渲染HTML返回
     * 前端直接用此接口, 无需手动获取单据数据
     * 设置中心：sys.print.when.effective=true 时仅生效单据可打印
     */
    @GetMapping("/print/{billType}/{billId}")
    public Result<String> printByBill(@PathVariable String billType,
                                      @PathVariable Long billId,
                                      HttpServletRequest request) {
        Result<Void> check = checkPrintWhenEffective(billType, billId);
        if (check != null) return Result.error(check.getMessage());
        String html = printTemplateService.printByBill(billType, billId, resolveQrBaseUrl(request));
        return Result.success(html);
    }

    /**
     * 按指定模板打印单据: 用指定模板渲染单据数据
     * 设置中心：sys.print.when.effective=true 时仅生效单据可打印
     */
    @GetMapping("/print/{billType}/{billId}/{templateId}")
    public Result<String> printByTemplate(@PathVariable String billType,
                                           @PathVariable Long billId,
                                           @PathVariable Long templateId,
                                           HttpServletRequest request) {
        Result<Void> check = checkPrintWhenEffective(billType, billId);
        if (check != null) return Result.error(check.getMessage());
        String html = printTemplateService.printByTemplate(templateId, billType, billId, resolveQrBaseUrl(request));
        return Result.success(html);
    }

    /**
     * 按单据打印成 PDF（2026-11-11 手机端打印）
     *
     * <p>手机端（微信小程序）没有打印接口，内嵌浏览器也不支持 window.print()，
     * 所以由服务端把同一份打印模板渲染成 PDF，客户端用系统/微信自带 PDF 阅读器打开，
     * 在那里另存、转发或用打印 App 打印。</p>
     *
     * <p>返回二进制流（不是统一的 Result 包装），Content-Type = application/pdf。
     * 打印口径与 {@link #printByBill} 完全一致（同一模板、同一“仅生效可打印”开关）。</p>
     */
    @GetMapping("/print-pdf/{billType}/{billId}")
    public org.springframework.http.ResponseEntity<byte[]> printPdfByBill(@PathVariable String billType,
                                                                          @PathVariable Long billId,
                                                                          HttpServletRequest request) {
        Result<Void> check = checkPrintWhenEffective(billType, billId);
        if (check != null) {
            return org.springframework.http.ResponseEntity.badRequest()
                    .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                    .body(String.valueOf(check.getMessage()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        String html;
        try {
            html = printTemplateService.printByBill(billType, billId, resolveQrBaseUrl(request));
        } catch (Exception e) {
            log.error("取打印模板失败: billType={}, billId={}", billType, billId, e);
            return org.springframework.http.ResponseEntity.badRequest()
                    .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                    .body(String.valueOf(e.getMessage()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        try {
            byte[] pdf = printPdfService.render(html, resolveQrBaseUrl(request));
            return org.springframework.http.ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .header("Content-Disposition",
                            "inline; filename=\"" + billType + "-" + billId + ".pdf\"")
                    .body(pdf);
        } catch (Exception e) {
            log.error("打印模板转 PDF 失败: billType={}, billId={}", billType, billId, e);
            return org.springframework.http.ResponseEntity.badRequest()
                    .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                    .body(String.valueOf(e.getMessage()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * 二维码域名：系统参数 qrcode.base_url 优先；未配置时按当前请求域名自动生成
     * （换域名/换服务器不必重新生成二维码模板）。
     */
    private String resolveQrBaseUrl(HttpServletRequest request) {
        String configured = sysConfigService.getValue("qrcode.base_url", "");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        if (request == null) {
            return "";
        }
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        }
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            int port = request.getServerPort();
            host = request.getServerName() + (port > 0 && port != 80 && port != 443 ? ":" + port : "");
        }
        return scheme + "://" + host;
    }

    /**
     * 设置中心-单据在生效状态下打印校验
     * 当 sys.print.when.effective=true 时，仅允许生效状态单据打印
     * @return null=放行，非null=拦截
     */
    private Result<Void> checkPrintWhenEffective(String billType, Long billId) {
        boolean enforce = sysConfigService.getBooleanValue("sys.print.when.effective", false);
        log.info("[PrintWhenEffective] billType={}, billId={}, enforce={}", billType, billId, enforce);
        if (!enforce) return null;

        // 单据类型 → (表名, 主键字段名) 映射
        // 注意: 各表主键字段名不统一，必须显式映射；purchase_inbound 实际表名为 purchase_receipt
        String[] tableInfo = switch (billType) {
            case "sale_order", "sale-order", "SaleOrder" -> new String[]{"sale_order", "order_id"};
            case "sale_delivery", "sale-delivery", "SaleDelivery" -> new String[]{"sale_delivery", "delivery_id"};
            case "sale_return", "sale-return", "SaleReturn" -> new String[]{"sale_return", "return_id"};
            case "sale_invoice", "sale-invoice", "SaleInvoice" -> new String[]{"sale_invoice", "invoice_id"};
            case "purchase_order", "purchase-order", "PurchaseOrder" -> new String[]{"purchase_order", "order_id"};
            case "purchase_inbound", "purchase-inbound", "PurchaseInbound" -> new String[]{"purchase_receipt", "receipt_id"};
            case "purchase_return", "purchase-return", "PurchaseReturn" -> new String[]{"purchase_return", "return_id"};
            default -> null;
        };
        if (tableInfo == null) return null; // 未知单据类型不拦截
        String table = tableInfo[0];
        String idField = tableInfo[1];
        log.info("[PrintWhenEffective] table={}, idField={}", table, idField);

        try {
            String sql = "SELECT status FROM " + table + " WHERE " + idField + " = ?";
            log.info("[PrintWhenEffective] SQL: {} | param={}", sql, billId);
            String status = jdbcTemplate.queryForObject(sql, String.class, billId);
            log.info("[PrintWhenEffective] status={}", status);
            if (status == null) return null;
            // 生效状态：已生效/已生效/approved/APPROVED
            boolean effective = status.equals("已生效") || status.equals("已生效")
                    || status.equalsIgnoreCase("approved") || status.equals("Approved");
            log.info("[PrintWhenEffective] effective={}", effective);
            if (!effective) {
                log.info("[PrintWhenEffective] 拦截：单据状态为[{}]，仅生效单据可打印", status);
                return Result.error("【设置中心-单据打印】单据状态为[" + status + "]，仅生效单据可打印");
            }
        } catch (Exception e) {
            log.warn("[PrintWhenEffective] exception: {}", e.getMessage());
            // 查询失败不拦截（单据可能不存在）
        }
        return null;
    }

    /**
     * 获取指定单据类型的所有可用模板 (供前端选择模板打印)
     */
    @GetMapping("/list/{docType}")
    public Result<List<PrintTemplate>> listByDocType(@PathVariable String docType) {
        return Result.success(printTemplateService.list()
                .stream()
                .filter(t -> (docType.equals(t.getDocType()) || "GENERIC".equals(t.getDocType()))
                        && t.getStatus() == 1)
                .sorted(Comparator.comparing(PrintTemplate::getSortOrder))
                .toList());
    }
}
