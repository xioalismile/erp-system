package com.erp.print.template.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.util.SortHelper;
import com.erp.print.template.dto.PrintTemplateDTO;
import com.erp.print.template.entity.PrintTemplate;
import com.erp.print.template.entity.PrintTemplateField;
import com.erp.print.template.mapper.PrintTemplateFieldMapper;
import com.erp.print.template.mapper.PrintTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintTemplateServiceImpl extends ServiceImpl<PrintTemplateMapper, PrintTemplate>
        implements PrintTemplateService {

    private final PrintTemplateFieldMapper fieldMapper;
    private final PrintDataFetcherService printDataFetcherService;
    private final com.erp.common.qrcode.QrCodeService qrCodeService;
    private final com.erp.common.qrcode.QrCodeSigner qrCodeSigner;
    private final com.erp.config.service.SysConfigService sysConfigService;
    private final PrintTemplateFieldCatalog fieldCatalog;
    private final com.erp.customfield.service.CustomFieldService customFieldService;

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "create_by",
            "create_time",
            "doc_type",
            "footer_content",
            "header_content",
            "id",
            "is_default",
            "margin_bottom",
            "margin_left",
            "margin_right",
            "margin_top",
            "page_orientation",
            "page_size",
            "remark",
            "sort_order",
            "status",
            "template_code",
            "template_content",
            "template_name",
            "template_style",
            "template_type",
            "update_time",
            "watermark",
            "watermark_enabled"
        );

    @Override
    public Page<PrintTemplate> pageQuery(int current, int size, String keyword, String docType, Integer status, String sortBy, String sortOrder) {
        Page<PrintTemplate> page = new Page<>(current, size);
        QueryWrapper<PrintTemplate> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like("template_code", keyword)
                    .or().like("template_name", keyword));
        }
        if (docType != null && !docType.isBlank()) {
            qw.eq("doc_type", docType);
        }
        if (status != null) {
            qw.eq("status", status);
        }
        if (!SortHelper.applyToWrapper(qw, sortBy, sortOrder, SORTABLE_COLUMNS)) qw.orderByAsc("sort_order").orderByDesc("create_time");
        return page(page, qw);
    }

    @Override
    public PrintTemplateDTO getDetail(Long id) {
        PrintTemplate template = getById(id);
        if (template == null) {
            return null;
        }
        PrintTemplateDTO dto = new PrintTemplateDTO();
        BeanUtils.copyProperties(template, dto);
        dto.setFields(getTemplateFields(id));
        return dto;
    }

    @Override
    public PrintTemplate getDefaultTemplate(String docType) {
        QueryWrapper<PrintTemplate> qw = new QueryWrapper<>();
        qw.eq("doc_type", docType)
                .eq("is_default", 1)
                .eq("status", 1)
                .orderByAsc("sort_order")
                .last("LIMIT 1");
        return getOne(qw);
    }

    @Override
    @Transactional
    public void saveTemplate(PrintTemplateDTO templateDTO) {
        if (templateDTO.getIsDefault() == null) {
            templateDTO.setIsDefault(0);
        }
        if (templateDTO.getStatus() == null) {
            templateDTO.setStatus(1);
        }
        if (templateDTO.getSortOrder() == null) {
            templateDTO.setSortOrder(0);
        }
        if (templateDTO.getWatermarkEnabled() == null) {
            templateDTO.setWatermarkEnabled(0);
        }
        if (templateDTO.getPageSize() == null) {
            templateDTO.setPageSize("A4");
        }
        if (templateDTO.getPageOrientation() == null) {
            templateDTO.setPageOrientation("portrait");
        }

        if (templateDTO.getIsDefault() == 1) {
            clearDefault(templateDTO.getDocType());
        }

        save(templateDTO);

        List<PrintTemplateField> fields = templateDTO.getFields();
        if (fields != null && !fields.isEmpty()) {
            for (PrintTemplateField field : fields) {
                field.setTemplateId(templateDTO.getId());
                if (field.getVisible() == null) {
                    field.setVisible(1);
                }
                if (field.getSortOrder() == null) {
                    field.setSortOrder(0);
                }
                fieldMapper.insert(field);
            }
        }

        log.info("打印模板创建: {}", templateDTO.getTemplateCode());
    }

    @Override
    @Transactional
    public void updateTemplate(PrintTemplateDTO templateDTO) {
        if (templateDTO.getIsDefault() != null && templateDTO.getIsDefault() == 1) {
            clearDefault(templateDTO.getDocType());
        }

        updateById(templateDTO);

        fieldMapper.delete(new QueryWrapper<PrintTemplateField>().eq("template_id", templateDTO.getId()));

        List<PrintTemplateField> fields = templateDTO.getFields();
        if (fields != null && !fields.isEmpty()) {
            for (PrintTemplateField field : fields) {
                field.setId(null);
                field.setTemplateId(templateDTO.getId());
                if (field.getVisible() == null) {
                    field.setVisible(1);
                }
                if (field.getSortOrder() == null) {
                    field.setSortOrder(0);
                }
                fieldMapper.insert(field);
            }
        }

        log.info("打印模板更新: {}", templateDTO.getTemplateCode());
    }

    @Override
    @Transactional
    public void deleteTemplate(Long id) {
        removeById(id);
        fieldMapper.delete(new QueryWrapper<PrintTemplateField>().eq("template_id", id));
        log.info("打印模板删除: {}", id);
    }

    @Override
    @Transactional
    public void setDefault(Long id) {
        PrintTemplate template = getById(id);
        if (template == null) {
            return;
        }
        clearDefault(template.getDocType());
        template.setIsDefault(1);
        updateById(template);
        log.info("设置默认模板: {}", id);
    }

    @Override
    @Transactional
    public Long copyTemplate(Long id, String newName) {
        PrintTemplate source = getById(id);
        if (source == null) {
            return null;
        }

        PrintTemplate copy = new PrintTemplate();
        BeanUtils.copyProperties(source, copy);
        copy.setId(null);
        copy.setTemplateCode(source.getTemplateCode() + "_copy");
        copy.setTemplateName(newName != null ? newName : source.getTemplateName() + "副本");
        copy.setIsDefault(0);
        save(copy);

        List<PrintTemplateField> fields = getTemplateFields(id);
        if (fields != null && !fields.isEmpty()) {
            for (PrintTemplateField field : fields) {
                PrintTemplateField fieldCopy = new PrintTemplateField();
                BeanUtils.copyProperties(field, fieldCopy);
                fieldCopy.setId(null);
                fieldCopy.setTemplateId(copy.getId());
                fieldMapper.insert(fieldCopy);
            }
        }

        log.info("复制模板: {} -> {}", id, copy.getId());
        return copy.getId();
    }

    @Override
    public List<PrintTemplateField> getTemplateFields(Long templateId) {
        QueryWrapper<PrintTemplateField> qw = new QueryWrapper<>();
        qw.eq("template_id", templateId).orderByAsc("sort_order").orderByAsc("id");
        return fieldMapper.selectList(qw);
    }

    @Override
    public String renderTemplate(Long templateId, Map<String, Object> data) {
        PrintTemplate template = getById(templateId);
        if (template == null) {
            return "";
        }
        return renderHtml(template, data);
    }

    @Override
    public String previewHtml(PrintTemplateDTO templateDTO) {
        if (templateDTO == null) return "";
        PrintTemplate template = new PrintTemplate();
        BeanUtils.copyProperties(templateDTO, template);
        if (template.getTemplateName() == null || template.getTemplateName().isBlank()) {
            template.setTemplateName("打印预览");
        }
        if (template.getPageSize() == null || template.getPageSize().isBlank()) {
            template.setPageSize("A4");
        }
        if (template.getPageOrientation() == null || template.getPageOrientation().isBlank()) {
            template.setPageOrientation("portrait");
        }
        return renderHtml(template, buildPreviewData(template.getDocType()));
    }

    /**
     * 设计器预览的样例数据：字段清单与真实打印同一份后端字段目录，值按字段名/中文名生成。
     * 目的只是让预览能看出真实版式、行数、合计与格式，不会写库、不会查业务单据。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildPreviewData(String docType) {
        Map<String, Object> data = new LinkedHashMap<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        data.put("billType", docType);
        data.put("billTypeName", docType);
        data.put("billId", 1L);
        data.put("billNo", "示例单号");
        data.put("billDate", today);
        data.put("createTime", today);
        data.put("status", "已生效");
        data.put("materialId", 1L);
        data.put("operationRowId", 1L);
        Map<String, Object> entry = null;
        Object all = fieldCatalog.all();
        try {
            if (all instanceof Map<?, ?> allMap) {
                Object dtm = allMap.get("docTypeFieldsMap");
                if (dtm instanceof Map<?, ?> dtmMap) {
                    Object e = dtmMap.get(docType);
                    if (e instanceof Map<?, ?> em) {
                        // 预览用副本：下面要追加当前租户的自定义字段，不能改到共享字段目录
                        Map<String, Object> copy = new LinkedHashMap<>();
                        copy.put("header", new ArrayList<>((List<Map<String, Object>>) (List<?>) em.get("header")));
                        copy.put("items", new ArrayList<>((List<Map<String, Object>>) (List<?>) em.get("items")));
                        copy.put("footer", new ArrayList<>((List<Map<String, Object>>) (List<?>) em.get("footer")));
                        entry = copy;
                    }
                }
                if (entry == null) {
                    entry = new LinkedHashMap<>();
                    entry.put("header", new ArrayList<>());
                    entry.put("items", new ArrayList<>());
                    entry.put("footer", new ArrayList<>());
                    Object common = allMap.get("commonItemFields");
                    if (common instanceof List<?>) entry.put("items", new ArrayList<>((List<?>) common));
                }
                Object employee = allMap.get("employeeFields");
                entry.put("employee", employee instanceof List<?> l ? new ArrayList<>(l) : new ArrayList<>());
                Object customer = allMap.get("customerFields");
                entry.put("customer", customer instanceof List<?> l ? new ArrayList<>(l) : new ArrayList<>());
                // 【2026-11-25】生产工单：预览同样带出「工序表」字段（标准工序字段 + 工序自定义字段）
                entry.put("operations", new ArrayList<>(fieldCatalog.operationFields(docType)));
                // 自定义字段：表头 / 明细 / 员工 / 客户，与「可用字段」接口同一口径（预览才能看到自定义字段样例值）
                String[] customTypes = fieldCatalog.customEntityTypesOf(docType);
                if (customTypes != null) {
                    appendPreviewCustomFields(entry, "header", customTypes[0], null);
                    if (customTypes[1] != null && !customTypes[1].isBlank()) {
                        appendPreviewCustomFields(entry, "items", customTypes[1], null);
                    }
                }
                appendPreviewCustomFields(entry, "employee", "employee", "salerCf");
                appendPreviewCustomFields(entry, "customer", "customer", "custCf");
            }
        } catch (Exception e) {
            log.warn("设计器预览取字段清单失败，按空字段渲染", e);
        }
        fillSampleFields(data, entry, "header", 0);
        fillSampleFields(data, entry, "footer", 0);
        fillSampleFields(data, entry, "employee", 0);
        fillSampleFields(data, entry, "customer", 0);
        List<Map<String, Object>> items = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            Map<String, Object> item = new LinkedHashMap<>();
            fillSampleFields(item, entry, "items", row);
            items.add(item);
        }
        data.put("items", items);
        // 【2026-11-25】生产工单预览：造 3 道工序（含工序自定义字段样例值），让「工序表」预览能看出真实版式
        if (entry != null && entry.get("operations") instanceof List<?> opFields && !opFields.isEmpty()) {
            List<Map<String, Object>> ops = new ArrayList<>();
            String[] names = {"裁床", "车缝", "包装"};
            for (int row = 0; row < 3; row++) {
                Map<String, Object> op = new LinkedHashMap<>();
                fillSampleFields(op, entry, "operations", row);
                op.put("operationName", names[row]);
                op.put("operationCode", "OP-0" + (row + 1));
                op.put("operationStatus", row == 0 ? "已完工" : (row == 1 ? "进行中" : "待派工"));
                op.put("assigneeName", row == 2 ? "" : ("张" + (row + 1)));
                op.put("workCenterName", "生产车间");
                op.put("productName", "女士衬衫");
                op.put("planQuantity", 100);
                op.put("reportedQuantity", row == 0 ? 100 : (row == 1 ? 60 : 0));
                op.put("rejectedQuantity", 0);
                ops.add(op);
            }
            data.put("operations", ops);
        }
        return data;
    }

    /** 预览数据追加当前租户的自定义字段定义（key/label），prefix 为空表示单据表头/明细用原始字段标识 */
    @SuppressWarnings("unchecked")
    private void appendPreviewCustomFields(Map<String, Object> entry, String group, String entityType, String prefix) {
        if (entityType == null || entityType.isBlank()) return;
        Object listObj = entry.get(group);
        List<Map<String, Object>> fields = listObj instanceof List<?> l
                ? (List<Map<String, Object>>) l : new ArrayList<>();
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        for (Map<String, Object> f : fields) keys.add(String.valueOf(f.get("key")));
        for (com.erp.customfield.entity.CustomField def : customFieldService.listEnabledByEntity(entityType)) {
            if (def.getFieldKey() == null || def.getFieldKey().isBlank()) continue;
            String key = "salerCf".equals(prefix) ? fieldCatalog.employeeCustomFieldKey(def.getFieldKey())
                    : "custCf".equals(prefix) ? fieldCatalog.customerCustomFieldKey(def.getFieldKey())
                    : def.getFieldKey();
            if (key.isBlank() || keys.contains(key)) continue;
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("key", key);
            one.put("label", def.getFieldLabel());
            fields.add(one);
            keys.add(key);
        }
        entry.put(group, fields);
    }

    @SuppressWarnings("unchecked")
    private void fillSampleFields(Map<String, Object> target, Map<String, Object> entry, String group, int row) {
        if (target == null || entry == null) return;
        Object listObj = entry.get(group);
        if (!(listObj instanceof List<?> list)) return;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> f)) continue;
            String key = asText(f.get("key"));
            if (key == null || key.isBlank() || target.containsKey(key)) continue;
            String label = asText(f.get("label"));
            // 勾选类样例值按用户口径显示 ✔（真实打印同样是 ✔/空白）
            if ("switch".equals(asText(f.get("fieldType")))) {
                target.put(key, "✔");
                continue;
            }
            target.put(key, sampleValue(key, label == null ? key : label, row));
        }
    }

    private Object sampleValue(String key, String label, int row) {
        String k = key == null ? "" : key.toLowerCase(Locale.ROOT);
        String lab = label == null ? "" : label;
        if (k.matches(".*(image|picture|img|photo).*") || lab.contains("图片") || lab.contains("照片")) return "";
        if (k.contains("attachmenturls") || lab.contains("附件地址")) return "";
        if (k.contains("attachmentcount") || lab.contains("附件数量")) return 2;
        if (k.contains("attachmentnames") || lab.contains("附件名称")) return "附件一.pdf、附件二.png";
        if (k.matches(".*(date|time).*") || lab.contains("日期") || lab.contains("时间")) {
            return java.time.LocalDate.now().minusDays(row);
        }
        if (lab.contains("金额") || k.contains("amount") || k.contains("money") || k.contains("total")) {
            return new BigDecimal("100.50").add(new BigDecimal(200L * row));
        }
        if (lab.contains("单价") || lab.contains("成本") || k.contains("price") || k.contains("cost") || k.contains("fee")) {
            return new BigDecimal("88.50");
        }
        if (lab.contains("数量") || k.contains("qty") || k.contains("quantity")) {
            return BigDecimal.valueOf(Math.max(1, 3 - row));
        }
        if (lab.contains("税率") || k.contains("rate") || k.contains("percent")) return new BigDecimal("13");
        if (lab.contains("单位") || k.contains("unit")) return "个";
        if (lab.contains("规格") || k.contains("spec")) return "规格示例";
        if (lab.contains("电话") || k.contains("phone") || k.contains("mobile")) return "13800000000";
        if (lab.contains("邮箱") || k.contains("email")) return "demo@example.com";
        if (lab.contains("地址") || k.contains("address")) return "示例地址";
        if (lab.contains("状态") || k.contains("status")) return "已生效";
        if (lab.contains("物料")) return "示例物料" + (char) ('A' + row);
        if (lab.contains("编号") || lab.contains("编码") || lab.contains("单号") || lab.contains("合同")) {
            return "示例编号00" + (row + 1);
        }
        if (lab.contains("名称") || k.contains("name")) return "示例名称" + (char) ('A' + row);
        return lab + "示例";
    }

    @Override
    public String printByBill(String billType, Long billId) {
        return printByBill(billType, billId, null);
    }

    @Override
    public String printByBill(String billType, Long billId, String qrBaseUrl) {
        // 1. 查找该单据类型的默认模板
        PrintTemplate template = getDefaultTemplate(billType);
        if (template == null) {
            // 回退到通用模板
            template = getDefaultTemplate("GENERIC");
        }
        if (template == null) {
            return "<html><body><h3>未找到打印模板, 请先在设置中心配置模板</h3></body></html>";
        }

        // 2. 获取单据数据
        Map<String, Object> data = printDataFetcherService.fetchBillData(billType, billId);
        // 补充通用字段
        data.putIfAbsent("billType", billType);
        data.putIfAbsent("billTypeName", printDataFetcherService.getBillTypeName(billType));
        data.put("billId", billId);
        if (qrBaseUrl != null && !qrBaseUrl.isBlank()) data.put(QR_BASE_URL_KEY, qrBaseUrl);
        enrichCommonAliases(data);

        // 3. 渲染HTML
        log.info("打印单据: type={}, id={}, template={}", billType, billId, template.getTemplateName());
        return renderHtml(template, data);
    }

    @Override
    public String printByTemplate(Long templateId, String billType, Long billId) {
        return printByTemplate(templateId, billType, billId, null);
    }

    @Override
    public String printByTemplate(Long templateId, String billType, Long billId, String qrBaseUrl) {
        PrintTemplate template = getById(templateId);
        if (template == null) {
            return "";
        }
        Map<String, Object> data = printDataFetcherService.fetchBillData(billType, billId);
        data.putIfAbsent("billType", billType);
        data.putIfAbsent("billTypeName", printDataFetcherService.getBillTypeName(billType));
        data.put("billId", billId);
        if (qrBaseUrl != null && !qrBaseUrl.isBlank()) data.put(QR_BASE_URL_KEY, qrBaseUrl);
        enrichCommonAliases(data);
        return renderHtml(template, data);
    }

    /** 单号候选字段（按常见命名优先级） */
    private static final String[] BILL_NO_KEYS = {
            "billNo", "orderNo", "receiptNo", "paymentNo", "contractNo", "quoteNo",
            "invoiceNo", "deliveryNo", "returnNo", "transferNo", "checkNo", "issueNo",
            "sampleNo", "expenseNo", "voucherNo", "requisitionNo", "planNo", "reportNo", "serial_no"
    };
    /** 日期候选字段 */
    private static final String[] BILL_DATE_KEYS = {
            "billDate", "orderDate", "receiptDate", "paymentDate", "contractDate", "quoteDate",
            "invoiceDate", "deliveryDate", "returnDate", "transferDate", "checkDate", "issueDate",
            "sampleDate", "expenseDate", "assembleDate", "disassembleDate", "createTime", "createdAt"
    };

    /**
     * 通用别名兜底：各单据实体的单号/日期字段名不同（如收款单是 receiptNo/receiptDate），
     * 把它们映射到 billNo/billDate，让通用模板（GENERIC）对任意单据类型都能打印出关键信息。
     */
    private void enrichCommonAliases(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return;
        if (isBlankValue(data.get("billNo"))) {
            for (String k : BILL_NO_KEYS) {
                Object v = data.get(k);
                if (v != null && !String.valueOf(v).isBlank()) {
                    data.put("billNo", v);
                    break;
                }
            }
        }
        if (isBlankValue(data.get("billDate"))) {
            for (String k : BILL_DATE_KEYS) {
                Object v = data.get(k);
                if (v != null && !String.valueOf(v).isBlank()) {
                    data.put("billDate", v);
                    break;
                }
            }
        }
    }

    private boolean isBlankValue(Object v) {
        return v == null || String.valueOf(v).isBlank();
    }

    /**
     * 渲染HTML: 支持 {{field}} 变量替换 + {{#list}}...{{/list}} 循环 + {{#header}}...{{/header}} 条件块
     */
    private String renderHtml(PrintTemplate template, Map<String, Object> data) {
        String content = template.getTemplateContent();
        if (content == null || content.isEmpty()) {
            return "";
        }
        // 画布坐标以纸张左上角为原点；@page 页边距会把内容整体内移，这里反向补偿，
        // 保证设计器画布、预览与真实打印三处坐标一致（2026-11-24 预览与打印不一致修复）
        content = offsetBlockPositions(content, template);

        if (data != null && !data.isEmpty()) {
            // 数据表格块（设计器改版）：列绑定 + 明细行数两模式 + 合计行 + 分组统计 + 单元格格式
            content = renderDataTableBlocks(content, data);
            // 再处理循环块 {{#items}}...{{/items}}（非表格块的历史用法）
            content = renderLoops(content, data);
            // 再处理条件块 {{#header}}...{{/header}} (有内容就显示)
            content = renderConditionBlocks(content, data);
            // 再处理二维码占位 {{qr:...}}（必须在普通变量之前，否则会被当成普通变量清空）
            content = renderQrCodes(content, data);
            // 给带 data-fmt 的元素内的占位符附加格式注记（设计器格式控制）
            content = renderFmtAnnotations(content);
            // 最后处理普通变量 {{fieldName}} / {{fieldName@fmt}}
            content = renderVariables(content, data);
        }

        // 组装完整HTML
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html><head><meta charset=\"UTF-8\">");
        html.append("<title>").append(template.getTemplateName()).append("</title>");
        // 默认字体与设计器画布一致（微软雅黑优先）；模板自定义 CSS 在它之后加载，可覆盖
        html.append("<style>body{font-family:\"Microsoft YaHei\",\"微软雅黑\",SimSun,sans-serif;}</style>");
        if (template.getTemplateStyle() != null) {
            html.append("<style>").append(template.getTemplateStyle()).append("</style>");
        }
        html.append("<style>");
        html.append("html,body{margin:0;padding:0;}");
        html.append(".content{position:relative;}");
        // 屏幕预览按页边距模拟纸张内容区，与打印态 @page 页边距保持同一套坐标
        html.append("@media screen{body{margin:")
                .append(mm(template.getMarginTop())).append("mm ")
                .append(mm(template.getMarginRight())).append("mm ")
                .append(mm(template.getMarginBottom())).append("mm ")
                .append(mm(template.getMarginLeft())).append("mm;}}");
        html.append("@page { size: ").append(template.getPageSize() != null ? template.getPageSize() : "A4")
                .append(" ").append(template.getPageOrientation() != null ? template.getPageOrientation() : "portrait").append(";");
        if (template.getMarginTop() != null) html.append(" margin-top: ").append(template.getMarginTop()).append("mm;");
        if (template.getMarginBottom() != null) html.append(" margin-bottom: ").append(template.getMarginBottom()).append("mm;");
        if (template.getMarginLeft() != null) html.append(" margin-left: ").append(template.getMarginLeft()).append("mm;");
        if (template.getMarginRight() != null) html.append(" margin-right: ").append(template.getMarginRight()).append("mm;");
        html.append(" }");
        html.append("@media print { body { margin:0; -webkit-print-color-adjust: exact; } }");
        if (template.getWatermarkEnabled() != null && template.getWatermarkEnabled() == 1 && template.getWatermark() != null) {
            html.append("body::before { content: '").append(template.getWatermark()).append("';");
            html.append(" position: fixed; top: 50%; left: 50%;");
            html.append(" transform: translate(-50%, -50%) rotate(-45deg);");
            html.append(" font-size: 80px; color: rgba(0,0,0,0.1);");
            html.append(" z-index: 9999; pointer-events: none; }");
        }
        html.append("</style>");
        html.append("</head><body>");

        if (template.getHeaderContent() != null) {
            html.append("<div class=\"header\">").append(template.getHeaderContent()).append("</div>");
        }

        html.append("<div class=\"content\">").append(content).append("</div>");

        if (template.getFooterContent() != null) {
            html.append("<div class=\"footer\">").append(template.getFooterContent()).append("</div>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    // ==================== 数据表格渲染（打印模板设计器改版 2026-11-22） ====================

    private static final Pattern TABLE_BLOCK_PATTERN = Pattern.compile("<table\\b([^>]*)>(.*?)</table>", Pattern.DOTALL);
    private static final Pattern LOOP_ITEMS_PATTERN = Pattern.compile("\\{\\{#items\\}\\}(.*?)\\{\\{/items\\}\\}", Pattern.DOTALL);
    private static final Pattern CELL_PATTERN = Pattern.compile("<(td|th)\\b[^>]*>.*?</\\1>", Pattern.DOTALL);
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([^#/{}@]+)\\}\\}");
    private static final Pattern FMT_ELEMENT_PATTERN = Pattern.compile("<([a-zA-Z]+)([^>]*data-fmt=\"[^\"]+\"[^>]*)>((?:(?!data-fmt=).)*?)</\\1>", Pattern.DOTALL);
    /** 设计器块根节点的绝对定位四项（position:absolute;left:Xmm;top:Ymm） */
    private static final Pattern ABS_BLOCK_POS_PATTERN = Pattern.compile(
            "(position\\s*:\\s*absolute\\s*;\\s*left\\s*:\\s*)(-?\\d+(?:\\.\\d+)?)(mm\\s*;\\s*top\\s*:\\s*)(-?\\d+(?:\\.\\d+)?)(mm)",
            Pattern.CASE_INSENSITIVE);

    /** 块坐标按页边距反向补偿：画布坐标 = 纸张坐标，@page 内移多少就减多少 */
    private String offsetBlockPositions(String content, PrintTemplate template) {
        if (content == null || content.isEmpty()) return content;
        int ml = template.getMarginLeft() == null ? 0 : template.getMarginLeft();
        int mt = template.getMarginTop() == null ? 0 : template.getMarginTop();
        if (ml == 0 && mt == 0) return content;
        Matcher m = ABS_BLOCK_POS_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            double left = Double.parseDouble(m.group(2)) - ml;
            double top = Double.parseDouble(m.group(4)) - mt;
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    m.group(1) + trimMm(left) + m.group(3) + trimMm(top) + "mm"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String trimMm(double v) {
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    /** 页边距毫米值（null 视为 0） */
    private String mm(Integer v) {
        return String.valueOf(v == null ? 0 : v);
    }

    /** 数据表格单元格结构：跨列数 / 格式 / 绑定字段 */
    private static class CellSpec {
        int colspan = 1;
        String fmt;
        String bind;
    }

    /**
     * 渲染数据表格块：<table data-table-kind="data" ...> 内的 {{#items}} 明细行。
     * 支持：明细行按单据数据展开、每页固定行数补空行、合计行、按字段分组统计、单元格格式（data-fmt）。
     * 任一异常回退为按原样输出，不影响打印出纸。
     */
    private String renderDataTableBlocks(String content, Map<String, Object> data) {
        // 2026-11-25：除明细数据表格外，工序表（data-table-kind="operation"）也走本入口
        if (content == null
                || (content.indexOf("data-table-kind=\"data\"") < 0
                    && content.indexOf("data-table-kind=\"operation\"") < 0)) return content;
        Matcher m = TABLE_BLOCK_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String attrs = m.group(1);
            String body = m.group(2);
            String replaced = m.group(0);
            try {
                if (attrs.contains("data-table-kind=\"data\"")) {
                    replaced = renderDataTableTable(attrs, body, data);
                } else if (attrs.contains("data-table-kind=\"operation\"")) {
                    // 【2026-11-25】生产工单「工序表」：行/合并/自定义内容三种版式，见 renderOperationTable
                    replaced = renderOperationTable(attrs, body, data);
                }
            } catch (Exception e) {
                log.warn("数据表格块渲染失败，按原样输出", e);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replaced));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String renderDataTableTable(String attrs, String body, Map<String, Object> data) {
        Matcher lm = LOOP_ITEMS_PATTERN.matcher(body);
        if (!lm.find()) {
            // 无明细循环：仅清理占位符，按原样输出
            return "<table" + attrs + ">" + body + "</table>";
        }
        String rowTpl = lm.group(1);
        String before = body.substring(0, lm.start());
        String after = body.substring(lm.end());

        List<CellSpec> specs = parseCellSpecs(rowTpl);
        int colCount = 0;
        for (CellSpec s : specs) colCount += Math.max(1, s.colspan);

        Object listData = getValueFromMap(data, "items");
        List<Map<String, Object>> items = new ArrayList<>();
        if (listData instanceof List) {
            for (Object o : (List<?>) listData) {
                if (o instanceof Map) items.add((Map<String, Object>) o);
            }
        }

        int fixedRows = 0;
        Matcher fm = Pattern.compile("data-rows=\"fixed:(\\d+)\"").matcher(attrs);
        if (fm.find()) fixedRows = Math.max(1, Math.min(50, Integer.parseInt(fm.group(1))));
        String rowHeight = attrVal(attrs, "data-row-height");
        String totalCols = attrVal(attrs, "data-total");
        String totalRmbKey = attrVal(attrs, "data-total-rmb");
        String groupKey = attrVal(attrs, "data-group");
        String groupLabel = attrVal(attrs, "data-group-label");
        String groupTotalCols = attrVal(attrs, "data-group-total");
        String tdStyle = firstTdStyle(rowTpl);

        StringBuilder rows = new StringBuilder();
        boolean grouped = groupKey != null && !groupKey.isBlank();
        if (!items.isEmpty()) {
            if (grouped) {
                Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
                for (Map<String, Object> it : items) {
                    Object gv = getValueFromMap(it, groupKey);
                    String key = gv == null ? "" : String.valueOf(gv);
                    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(it);
                }
                for (Map.Entry<String, List<Map<String, Object>>> g : groups.entrySet()) {
                    String label = groupLabel == null || groupLabel.isBlank() ? groupKey : groupLabel;
                    rows.append("<tr class=\"group-head\"><td colspan=\"").append(colCount)
                            .append("\" style=\"font-weight:bold;text-align:left;\">")
                            .append(escHtml(label)).append("：").append(escHtml(g.getKey())).append("</td></tr>");
                    for (Map<String, Object> it : g.getValue()) rows.append(renderRowCells(rowTpl, specs, it, data));
                    if (groupTotalCols != null && !groupTotalCols.isBlank()) {
                        rows.append(buildTotalRow(specs, colCount, g.getValue(), groupTotalCols, "小计", tdStyle));
                    }
                }
            } else {
                for (Map<String, Object> it : items) rows.append(renderRowCells(rowTpl, specs, it, data));
            }
        }
        // 每页固定行数：仅非分组模式补空行，补齐到 N 的整数倍，空行保留边框（样式沿用明细行单元格）
        if (fixedRows > 0 && !grouped) {
            int target = (int) Math.ceil(Math.max(items.size(), 1) / (double) fixedRows) * fixedRows;
            for (int i = items.size(); i < target; i++) {
                rows.append(buildEmptyRow(specs, rowHeight, tdStyle));
            }
        }
        // 总计行
        if (totalCols != null && !totalCols.isBlank()) {
            rows.append(buildTotalRow(specs, colCount, items, totalCols, "合计", tdStyle));
            // 金额大写行：按模板指定的合计字段求和
            if (totalRmbKey != null && !totalRmbKey.isBlank()) {
                BigDecimal sum = BigDecimal.ZERO;
                for (Map<String, Object> it : items) {
                    BigDecimal d = toBigDecimal(getValueFromMap(it, totalRmbKey));
                    if (d != null) sum = sum.add(d);
                }
                rows.append(buildCapitalRow(colCount, sum, tdStyle));
            }
        }
        return "<table" + attrs + ">" + before + rows + after + "</table>";
    }

    // ==================== 工序表渲染（2026-11-25 生产工单打印） ====================

    /** 工序表单元格：从保存的行模板里解析出绑定字段、来源（工序/物料）、样式 */
    private static class OpCell {
        String style;
        int colspan = 1;
        String bind;
        String fmt;
        /** operation=工序字段；item=物料明细字段 */
        String src = "operation";
        boolean fixedName;
        String inner = "";
    }

    private static final Pattern OP_DETAIL_ROW_PATTERN =
            Pattern.compile("<tr[^>]*data-role=\"detail\"[^>]*>(.*?)</tr>", Pattern.DOTALL);
    private static final Pattern OP_THEAD_PATTERN =
            Pattern.compile("<thead[^>]*>.*?</thead>", Pattern.DOTALL);
    private static final Pattern OP_OPEN_TAG_PATTERN =
            Pattern.compile("<(td|th)[^>]*>", Pattern.CASE_INSENSITIVE);

    /**
     * 设计器「工序表」三种版式：
     * <ul>
     *   <li>rows：一行一道工序，勾选的工序字段各占一列；</li>
     *   <li>detail：明细表式，工序名称固定首列并合并；勾了物料字段时按工序展开全部物料行；</li>
     *   <li>custom：工序名称合并 + 右列每行一个「字段名：值」，空值也占行。</li>
     * </ul>
     * 数据来源：map.operations（工序清单，已由打印取数补齐工作中心/成品/自定义字段）、map.items（物料明细）。
     */
    private String renderOperationTable(String attrs, String body, Map<String, Object> data) {
        String thead = "";
        Matcher tm = OP_THEAD_PATTERN.matcher(body);
        if (tm.find()) thead = tm.group(0);
        String rowTpl = "";
        Matcher rm = OP_DETAIL_ROW_PATTERN.matcher(body);
        if (rm.find()) rowTpl = rm.group(1);
        List<OpCell> cells = parseOpCells(rowTpl);
        List<Map<String, Object>> ops = asMapList(data.get("operations"));
        if (cells.isEmpty() || ops.isEmpty()) {
            return "<table" + attrs + ">" + body + "</table>";
        }
        List<Map<String, Object>> items = asMapList(data.get("items"));
        String mode = attrVal(attrs, "data-op-mode");
        if (mode == null || mode.isBlank()) mode = "rows";
        StringBuilder rows = new StringBuilder();
        if ("custom".equals(mode)) {
            rows.append(renderOperationCustomRows(attrs, cells, ops));
        } else {
            boolean hasItem = false;
            for (OpCell c : cells) {
                if ("item".equals(c.src)) { hasItem = true; break; }
            }
            boolean merge = "detail".equals(mode) || "1".equals(attrVal(attrs, "data-op-merge"));
            if (merge && hasItem && !items.isEmpty()) {
                rows.append(renderOperationMergedRows(cells, ops, items, data));
            } else {
                for (Map<String, Object> op : ops) {
                    Map<String, Object> ctx = new HashMap<>(data);
                    ctx.putAll(op);
                    rows.append("<tr>");
                    for (OpCell c : cells) {
                        // 没有物料数据时，物料列留空，不拿单据表头的同名字段冒充
                        String content = ("item".equals(c.src) && items.isEmpty()) ? "" : opCellContent(c, ctx);
                        rows.append(opCellHtml(c, content, 1));
                    }
                    rows.append("</tr>");
                }
            }
        }
        if (rows.length() == 0) return "<table" + attrs + ">" + body + "</table>";
        return "<table" + attrs + ">" + thead + "<tbody>" + rows + "</tbody></table>";
    }

    /** 明细表式：每道工序下展开全部物料行，工序字段（含工序名称）纵向合并，物料字段逐行输出 */
    private String renderOperationMergedRows(List<OpCell> cells, List<Map<String, Object>> ops,
                                             List<Map<String, Object>> items, Map<String, Object> data) {
        StringBuilder rows = new StringBuilder();
        for (Map<String, Object> op : ops) {
            Map<String, Object> opCtx = new HashMap<>(data);
            opCtx.putAll(op);
            int n = Math.max(1, items.size());
            for (int i = 0; i < n; i++) {
                Map<String, Object> itemCtx = null;
                if (i < items.size()) {
                    // 物料格只取物料数据：不能叠加工序字段，否则 remark 等同名字段会串号
                    itemCtx = new HashMap<>(data);
                    itemCtx.putAll(items.get(i));
                }
                rows.append("<tr>");
                for (OpCell c : cells) {
                    if ("item".equals(c.src)) {
                        rows.append(opCellHtml(c, opCellContent(c, itemCtx == null ? opCtx : itemCtx), 1));
                    } else if (i == 0) {
                        rows.append(opCellHtml(c, opCellContent(c, opCtx), n));
                    }
                }
                rows.append("</tr>");
            }
        }
        return rows.toString();
    }

    /** 工序名称 + 自定义内容：每道工序占 N 行（N=勾选字段数，空值也占行），左列工序名称纵向合并 */
    private String renderOperationCustomRows(String attrs, List<OpCell> cells, List<Map<String, Object>> ops) {
        List<String[]> lines = parseOpLines(attrVal(attrs, "data-op-lines"));
        if (lines.isEmpty()) lines = new ArrayList<>();
        OpCell nameCell = cells.get(0);
        OpCell lineCell = cells.size() > 1 ? cells.get(1) : cells.get(0);
        StringBuilder rows = new StringBuilder();
        for (Map<String, Object> op : ops) {
            List<String> contents = new ArrayList<>();
            for (String[] line : lines) {
                String key = line[0];
                String label = line[1];
                Object v = key == null || key.isBlank() ? null : getValueFromMap(op, key);
                String val = formatValue(v, null, key);
                contents.add(escHtml(label) + "：" + escHtml(val));
            }
            if (contents.isEmpty()) contents.add("");
            int span = contents.size();
            for (int i = 0; i < span; i++) {
                rows.append("<tr>");
                if (i == 0) {
                    rows.append(opCellHtml(nameCell, opCellContent(nameCell, op), span));
                }
                rows.append(opCellHtml(lineCell, contents.get(i), 1));
                rows.append("</tr>");
            }
        }
        return rows.toString();
    }

    private List<String[]> parseOpLines(String spec) {
        List<String[]> out = new ArrayList<>();
        if (spec == null || spec.isBlank()) return out;
        for (String part : spec.split(",")) {
            if (part == null || part.isBlank()) continue;
            int idx = part.indexOf('|');
            String key = idx >= 0 ? part.substring(0, idx) : part;
            String label = idx >= 0 ? part.substring(idx + 1) : key;
            out.add(new String[]{key.trim(), label});
        }
        return out;
    }

    private List<OpCell> parseOpCells(String rowTpl) {
        List<OpCell> list = new ArrayList<>();
        if (rowTpl == null || rowTpl.isBlank()) return list;
        Matcher cm = CELL_PATTERN.matcher(rowTpl);
        while (cm.find()) {
            String cell = cm.group(0);
            Matcher om = OP_OPEN_TAG_PATTERN.matcher(cell);
            if (!om.find()) continue;
            String tag = om.group(0);
            OpCell c = new OpCell();
            c.style = attrVal(tag, "style");
            String cs = attrVal(tag, "colspan");
            if (cs != null) {
                try { c.colspan = Math.max(1, Integer.parseInt(cs.trim())); } catch (Exception ignore) { }
            }
            c.bind = attrVal(tag, "data-bind");
            c.fmt = attrVal(tag, "data-fmt");
            String src = attrVal(tag, "data-col-src");
            if (src != null && !src.isBlank()) c.src = src;
            c.fixedName = "1".equals(attrVal(tag, "data-op-fixed"));
            int openEnd = cell.indexOf('>');
            int close = cell.lastIndexOf("</");
            c.inner = (openEnd >= 0 && close > openEnd) ? cell.substring(openEnd + 1, close) : "";
            list.add(c);
        }
        return list;
    }

    private String opCellHtml(OpCell c, String content, int rowspan) {
        StringBuilder sb = new StringBuilder("<td");
        if (rowspan > 1) sb.append(" rowspan=\"").append(rowspan).append("\"");
        if (c.colspan > 1) sb.append(" colspan=\"").append(c.colspan).append("\"");
        if (c.style != null && !c.style.isBlank()) sb.append(" style=\"").append(c.style).append("\"");
        sb.append(">").append(content).append("</td>");
        return sb.toString();
    }

    /** 单元格内容：有绑定字段则按字段取值+格式；没有绑定则按原模板内容解析占位符 */
    private String opCellContent(OpCell c, Map<String, Object> ctx) {
        if (c.bind != null && !c.bind.isBlank()) {
            Object v = getValueFromMap(ctx, c.bind);
            return escHtml(formatValue(v, c.fmt, c.bind));
        }
        String html = c.inner == null ? "" : c.inner;
        html = renderQrCodes(html, ctx);
        html = renderVariables(html, ctx);
        return html.replaceAll("\\{\\{[^}]*\\}\\}", "");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object obj) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (obj instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private List<CellSpec> parseCellSpecs(String rowTpl) {
        List<CellSpec> list = new ArrayList<>();
        Matcher cm = CELL_PATTERN.matcher(rowTpl);
        while (cm.find()) {
            CellSpec s = new CellSpec();
            String cell = cm.group(0);
            String cs = attrVal(cell, "colspan");
            if (cs != null) { try { s.colspan = Math.max(1, Integer.parseInt(cs.trim())); } catch (Exception ignore) { } }
            s.fmt = attrVal(cell, "data-fmt");
            s.bind = attrVal(cell, "data-bind");
            list.add(s);
        }
        return list;
    }

    /** 渲染一条明细行：逐格按 data-bind/data-fmt 取值格式化，再渲染行内二维码，清掉剩余占位 */
    private String renderRowCells(String rowTpl, List<CellSpec> specs, Map<String, Object> item, Map<String, Object> data) {
        Map<String, Object> rowCtx = new HashMap<>(data);
        rowCtx.putAll(item);
        StringBuilder out = new StringBuilder();
        Matcher cm = CELL_PATTERN.matcher(rowTpl);
        int last = 0;
        while (cm.find()) {
            String cell = cm.group(0);
            String fmt = attrVal(cell, "data-fmt");
            String bind = attrVal(cell, "data-bind");
            Matcher vm = VAR_PATTERN.matcher(cell);
            StringBuffer csb = new StringBuffer();
            while (vm.find()) {
                String key = vm.group(1).trim();
                Object v = getValueFromMap(rowCtx, key);
                String s = (v == null) ? "" : formatValue(v, fmt, key);
                s = maybeImage(fmt, s);
                vm.appendReplacement(csb, Matcher.quoteReplacement(s));
            }
            vm.appendTail(csb);
            out.append(rowTpl, last, cm.start()).append(csb);
            last = cm.end();
        }
        out.append(rowTpl.substring(last));
        String html = out.toString();
        html = renderQrCodes(html, rowCtx);
        return html.replaceAll("\\{\\{[^}]*\\}\\}", "");
    }

    private String buildEmptyRow(List<CellSpec> specs, String rowHeight, String tdStyle) {
        StringBuilder sb = new StringBuilder("<tr>");
        for (CellSpec s : specs) {
            int span = Math.max(1, s.colspan);
            sb.append("<td colspan=\"").append(span).append("\"");
            List<String> st = new ArrayList<>();
            if (tdStyle != null && !tdStyle.isBlank()) st.add(tdStyle);
            if (rowHeight != null && !rowHeight.isBlank()) st.add("height:" + escHtml(rowHeight) + "mm");
            if (!st.isEmpty()) sb.append(" style=\"").append(String.join(";", st)).append("\"");
            sb.append(">&nbsp;</td>");
        }
        sb.append("</tr>");
        return sb.toString();
    }

    /** 取明细行模板里第一个带样式的 td 的 style（用于空行继承边框等外观） */
    private String firstTdStyle(String rowTpl) {
        Matcher m = Pattern.compile("<td[^>]*style=\"([^\"]*)\"").matcher(rowTpl);
        return m.find() ? m.group(1) : null;
    }

    /** 合计/小计行：按列 data-bind 汇总，格式沿用该列 data-fmt */
    private String buildTotalRow(List<CellSpec> specs, int colCount, List<Map<String, Object>> items, String colsSpec, String label, String tdStyle) {
        Set<String> want = new LinkedHashSet<>();
        for (String s : colsSpec.split(",")) {
            if (!s.isBlank()) want.add(s.trim());
        }
        int n = specs.size();
        BigDecimal[] sums = new BigDecimal[n];
        boolean[] isTotal = new boolean[n];
        for (int i = 0; i < n; i++) {
            CellSpec s = specs.get(i);
            if (s.bind != null && want.contains(s.bind)) isTotal[i] = true;
        }
        for (Map<String, Object> it : items) {
            for (int i = 0; i < n; i++) {
                if (!isTotal[i]) continue;
                BigDecimal d = toBigDecimal(getValueFromMap(it, specs.get(i).bind));
                if (d != null) sums[i] = (sums[i] == null) ? d : sums[i].add(d);
            }
        }
        boolean anyTotal = false;
        for (boolean b : isTotal) if (b) { anyTotal = true; break; }
        String styleAttr = (tdStyle == null || tdStyle.isBlank()) ? "" : " style=\"" + tdStyle + "\"";
        StringBuilder cells = new StringBuilder();
        int labelSpan = 0;
        boolean labelDone = false;
        for (int i = 0; i < n; i++) {
            int span = Math.max(1, specs.get(i).colspan);
            if (isTotal[i]) {
                String val = formatValue(sums[i] == null ? BigDecimal.ZERO : sums[i],
                        (specs.get(i).fmt == null || specs.get(i).fmt.isBlank()) ? "thousand,d2" : specs.get(i).fmt, specs.get(i).bind);
                if (!labelDone && labelSpan == 0) {
                    cells.append("<td").append(styleAttr).append(">").append(label).append("：").append(val).append("</td>");
                } else {
                    if (!labelDone) {
                        cells.append("<td").append(styleAttr).append(" colspan=\"").append(labelSpan).append("\">").append(label).append("</td>");
                        labelDone = true;
                    }
                    cells.append("<td").append(styleAttr).append(">").append(val).append("</td>");
                }
            } else if (!labelDone) {
                labelSpan += span;
            } else {
                cells.append("<td").append(styleAttr).append("></td>");
            }
        }
        if (!anyTotal) {
            return "<tr class=\"total-row\"><td" + styleAttr + " colspan=\"" + colCount + "\">" + label + "</td></tr>";
        }
        return "<tr class=\"total-row\">" + cells + "</tr>";
    }

    /** 金额大写行：整行跨列，金额按指定合计字段汇总 */
    private String buildCapitalRow(int colCount, BigDecimal sum, String tdStyle) {
        String styleAttr = (tdStyle == null || tdStyle.isBlank()) ? "" : " style=\"" + tdStyle + "\"";
        return "<tr class=\"total-cap\"><td" + styleAttr + " colspan=\"" + colCount + "\">金额大写：人民币"
                + moneyToChineseUpper(sum == null ? BigDecimal.ZERO : sum) + "</td></tr>";
    }

    /** 给带 data-fmt 元素内的占位符附加 @fmt 注记，交给 renderVariables 统一格式化 */
    private String renderFmtAnnotations(String content) {
        if (content == null || content.indexOf("data-fmt=") < 0) return content;
        Matcher m = FMT_ELEMENT_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String tag = m.group(1);
            String attrs = m.group(2);
            String body = m.group(3);
            Matcher vm = VAR_PATTERN.matcher(body);
            StringBuffer bsb = new StringBuffer();
            while (vm.find()) {
                String key = vm.group(1).trim();
                if (key.contains("@")) { vm.appendReplacement(bsb, Matcher.quoteReplacement(vm.group(0))); continue; }
                String fmt = attrVal(attrs, "data-fmt");
                vm.appendReplacement(bsb, Matcher.quoteReplacement("{{" + key + "@" + fmt + "}}"));
            }
            vm.appendTail(bsb);
            String replaced = "<" + tag + attrs + ">" + bsb + "</" + tag + ">";
            m.appendReplacement(sb, Matcher.quoteReplacement(replaced));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 图片字段：格式含 image 时把取值输出为图片标签，空值不输出死链 */
    private String maybeImage(String fmt, String value) {
        if (value == null || value.isBlank() || fmt == null || fmt.isBlank()) return value;
        for (String f : fmt.toLowerCase(Locale.ROOT).split("[,，]")) {
            if (!"image".equals(f.trim())) continue;
            String url = value.trim();
            if (!(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/uploads") || url.startsWith("data:image"))) {
                return "";
            }
            return "<img src=\"" + escHtml(url) + "\" style=\"max-width:100%;max-height:30mm;\" />";
        }
        return value;
    }

    /** 按格式说明取值：rmb（金额大写）> 日期 > 数字（千分位/小数位）> 大小写；未匹配数字的走大小写 */
    private String formatValue(Object v, String fmt, String key) {
        if (v == null) return "";
        if (fmt == null || fmt.isBlank()) {
            return formatNumericForPrint(key, v);
        }
        String[] flags = fmt.toLowerCase(Locale.ROOT).split("[,，]");
        boolean thousand = false, rmb = false, hasDate = false, hasD = false;
        int scale = 2;
        String dateFlag = null;
        for (String f : flags) {
            f = f.trim();
            if (f.equals("thousand")) thousand = true;
            else if (f.equals("rmb")) rmb = true;
            else if (f.equals("date") || f.equals("datetime") || f.equals("month")) { hasDate = true; dateFlag = f; }
            else if (f.matches("d[0-4]")) { hasD = true; scale = f.charAt(1) - '0'; }
        }
        if (rmb) {
            BigDecimal d = toBigDecimal(v);
            if (d != null) return moneyToChineseUpper(d);
        }
        if (hasDate) {
            String s = formatDateValue(v, dateFlag);
            if (s != null) return s;
        }
        BigDecimal num = toBigDecimal(v);
        if (num != null) {
            if (thousand || hasD) {
                BigDecimal scaled = num.setScale(scale, java.math.RoundingMode.HALF_UP);
                String s = scaled.toPlainString();
                return thousand ? groupDigits(s) : s;
            }
            return formatNumericForPrint(key, num);
        }
        String str = String.valueOf(v);
        for (String f : flags) {
            if (f.equals("upper")) str = str.toUpperCase(Locale.ROOT);
            else if (f.equals("lower")) str = str.toLowerCase(Locale.ROOT);
            else if (f.equals("cap") && !str.isEmpty()) str = capitalizeWords(str);
        }
        return str;
    }

    private String capitalizeWords(String s) {
        StringBuilder b = new StringBuilder(s.toLowerCase(Locale.ROOT));
        boolean up = true;
        for (int i = 0; i < b.length(); i++) {
            char c = b.charAt(i);
            if (up && Character.isLetter(c)) { b.setCharAt(i, Character.toUpperCase(c)); up = false; }
            else if (!Character.isLetter(c)) up = true;
        }
        return b.toString();
    }

    private String groupDigits(String plain) {
        boolean neg = plain.startsWith("-");
        String s = neg ? plain.substring(1) : plain;
        int dot = s.indexOf('.');
        String intPart = dot >= 0 ? s.substring(0, dot) : s;
        String decPart = dot >= 0 ? s.substring(dot) : "";
        if (intPart.length() <= 3) return plain;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < intPart.length(); i++) {
            if (i > 0 && (intPart.length() - i) % 3 == 0) b.append(',');
            b.append(intPart.charAt(i));
        }
        return (neg ? "-" : "") + b + decPart;
    }

    /** 日期值格式化：识别 ISO 日期/日期时间字符串与常见时间类型，非日期返回 null 走后续逻辑 */
    private String formatDateValue(Object v, String flag) {
        try {
            if (v instanceof java.time.LocalDateTime) {
                return formatDateTime((java.time.LocalDateTime) v, flag);
            }
            if (v instanceof java.time.LocalDate) {
                return formatDate((java.time.LocalDate) v, flag);
            }
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) return null;
            if (s.length() >= 19 && s.charAt(10) == 'T') {
                return formatDateTime(java.time.LocalDateTime.parse(s.substring(0, 19)), flag);
            }
            if (s.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                return formatDate(java.time.LocalDate.parse(s.substring(0, 10)), flag);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String formatDateTime(java.time.LocalDateTime t, String flag) {
        if ("month".equals(flag)) return t.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        if ("datetime".equals(flag)) return t.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        return t.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    private String formatDate(java.time.LocalDate d, String flag) {
        if ("month".equals(flag)) return d.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        return d.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    /** 金额大写：人民币中文大写（负数带「负」，无角分补「整」） */
    private String moneyToChineseUpper(BigDecimal amount) {
        try {
            BigDecimal abs = amount.abs().setScale(2, java.math.RoundingMode.HALF_UP);
            long all = abs.movePointRight(2).longValue();
            long yuan = all / 100;
            int jiao = (int) ((all % 100) / 10);
            int fen = (int) (all % 10);
            if (yuan == 0 && jiao == 0 && fen == 0) return "零元整";
            String[] D = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
            String[] U = {"", "拾", "佰", "仟"};
            String[] B = {"", "万", "亿"};
            StringBuilder intSb = new StringBuilder();
            if (yuan > 0) {
                List<Integer> secs = new ArrayList<>();
                long n = yuan;
                while (n > 0) { secs.add((int) (n % 10000)); n /= 10000; }
                for (int i = secs.size() - 1; i >= 0; i--) {
                    int sec = secs.get(i);
                    if (sec == 0) {
                        boolean lowerNonZero = false;
                        for (int j = i - 1; j >= 0; j--) {
                            if (secs.get(j) != 0) { lowerNonZero = true; break; }
                        }
                        if (intSb.length() > 0 && lowerNonZero && intSb.charAt(intSb.length() - 1) != '零') intSb.append("零");
                        continue;
                    }
                    if (sec < 1000 && i < secs.size() - 1 && intSb.length() > 0 && intSb.charAt(intSb.length() - 1) != '零') intSb.append("零");
                    String s4 = String.format("%04d", sec);
                    boolean emitted = false, zeroPending = false;
                    for (int d = 0; d < 4; d++) {
                        int digit = s4.charAt(d) - '0';
                        if (digit == 0) { zeroPending = emitted; continue; }
                        if (zeroPending && emitted) intSb.append("零");
                        intSb.append(D[digit]).append(U[3 - d]);
                        emitted = true;
                        zeroPending = false;
                    }
                    intSb.append(B[i]);
                }
                intSb.append("元");
            }
            StringBuilder decSb = new StringBuilder();
            if (jiao == 0 && fen == 0) {
                if (yuan > 0) decSb.append("整");
            } else {
                if (yuan > 0 && jiao == 0) decSb.append("零");
                if (jiao > 0) decSb.append(D[jiao]).append("角");
                if (fen > 0) decSb.append(D[fen]).append("分");
            }
            String prefix = amount.signum() < 0 ? "负" : "";
            String s = prefix + intSb + decSb;
            return s.isEmpty() ? "零元整" : s;
        } catch (Exception e) {
            log.warn("金额大写转换失败: {}", amount, e);
            return String.valueOf(amount);
        }
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return new BigDecimal(String.valueOf(v));
        try {
            String s = String.valueOf(v).trim().replace(",", "");
            if (s.isEmpty()) return null;
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    private String attrVal(String tag, String name) {
        Matcher m = Pattern.compile(name + "=\"([^\"]*)\"").matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    private String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * 处理循环块: {{#items}}...{{/items}}
     * 遍历data中对应的List, 为每个元素复制一遍块内模板, 并替换变量
     */
    @SuppressWarnings("unchecked")
    private String renderLoops(String content, Map<String, Object> data) {
        // 匹配 {{#key}}...{{/key}}
        Pattern loopPattern = Pattern.compile("\\{\\{#(\\w+)\\}\\}(.*?)\\{\\{/\\1\\}\\}", Pattern.DOTALL);
        Matcher matcher = loopPattern.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String listKey = matcher.group(1);
            // 保留条件块（header/footer/section）原样，交给 renderConditionBlocks 处理；
            // 否则此处会因 data 无同名数组而把整个条件块误删
            if ("header".equals(listKey) || "footer".equals(listKey) || "section".equals(listKey)) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            String blockTemplate = matcher.group(2);
            Object listData = getValueFromMap(data, listKey);
            StringBuilder replacement = new StringBuilder();
            if (listData instanceof List) {
                for (Object item : (List<?>) listData) {
                    if (item instanceof Map) {
                        // 明细行：二维码可用行内字段（如每行物料码），因此先合并主表+行上下文再渲染二维码
                        Map<String, Object> rowCtx = new HashMap<>(data);
                        rowCtx.putAll((Map<String, Object>) item);
                        String rowHtml = renderQrCodes(blockTemplate, rowCtx);
                        replacement.append(renderVariables(rowHtml, (Map<String, Object>) item));
                    } else {
                        replacement.append(blockTemplate);
                    }
                }
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 处理条件块: {{#header}}...{{/header}} - 直接输出内容 (用于标记区块)
     */
    private String renderConditionBlocks(String content, Map<String, Object> data) {
        Pattern condPattern = Pattern.compile("\\{\\{#(header|footer|section)\\}\\}(.*?)\\{\\{/\\1\\}\\}", Pattern.DOTALL);
        Matcher matcher = condPattern.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String blockContent = matcher.group(2);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(blockContent));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 处理普通变量替换: {{fieldName}} / {{object.field}} / {{fieldName@fmt}}（设计器格式注记）
     */
    private String renderVariables(String content, Map<String, Object> data) {
        Pattern pattern = Pattern.compile("\\{\\{([^#/{}]+)\\}\\}");
        Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1).trim();
            String key = token;
            String fmt = null;
            int at = token.indexOf('@');
            if (at > 0) {
                key = token.substring(0, at).trim();
                fmt = token.substring(at + 1).trim();
            }
            Object value = getValueFromMap(data, key);
            String strValue = (value == null) ? "" : formatValue(value, fmt, key);
            strValue = maybeImage(fmt, strValue);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(strValue));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 打印数字字段按设置中心小数位数格式化的分类规则（与前端 usePrecision PROP_RULES 同款、顺序一致：先具体后宽泛） */
    private static final List<Object[]> PRINT_PRECISION_RULES = List.of(
            new Object[]{"rate",    Pattern.compile("exchange|汇率", Pattern.CASE_INSENSITIVE)},
            new Object[]{"percent", Pattern.compile("tax.*rate|discount.*rate|percent|税率|折扣率|百分比", Pattern.CASE_INSENSITIVE)},
            new Object[]{"price",   Pattern.compile("price|cost|fee|单价|成本|加工费|售价|进价", Pattern.CASE_INSENSITIVE)},
            new Object[]{"amount",  Pattern.compile("amount|money|金额|税额|价税", Pattern.CASE_INSENSITIVE)},
            new Object[]{"qty",     Pattern.compile("quantity|qty|stock|数量|库存|换算率", Pattern.CASE_INSENSITIVE)}
    );

    /**
     * 数字字段按设置中心「小数位数设置」格式化后再进打印模板（2026-10-13）。
     * 打印数据由实体原样转 Map，6 位精度的单价会原样印出（2654.867257）；
     * 这里按字段名分类（单价/金额/数量/汇率/百分比，规则与前端界面显示同款）取对应配置位数，
     * 使打印与界面精度口径一致。主键/状态等整数字段与未分类数字原样输出，观感不变。
     */
    private String formatNumericForPrint(String key, Object value) {
        if (value == null) return "";
        if (!(value instanceof BigDecimal)) return value.toString();
        try {
            String field = key;
            int dot = key.lastIndexOf('.');
            if (dot >= 0) field = key.substring(dot + 1);
            if (field.matches(".*[Ii]d$")) return value.toString(); // 主键/外键不格式化
            String category = null;
            for (Object[] rule : PRINT_PRECISION_RULES) {
                if (((Pattern) rule[1]).matcher(field).find()) { category = (String) rule[0]; break; }
            }
            if (category == null) return value.toString(); // 未分类数字原样
            int scale;
            switch (category) {
                case "rate":    scale = intConfig("precision.rate", 6); break;
                case "percent": scale = intConfig("precision.percent", 2); break;
                case "price":   scale = intConfig("precision.price", 4); break;
                case "qty":     scale = intConfig("precision.quantity", 4); break;
                default:        scale = intConfig("precision.amount", 2); break;
            }
            java.math.RoundingMode rm = "DOWN".equalsIgnoreCase(sysConfigService.getValue("precision.rounding.mode", "HALF_UP"))
                    ? java.math.RoundingMode.DOWN : java.math.RoundingMode.HALF_UP;
            BigDecimal scaled = ((BigDecimal) value).setScale(scale, rm);
            if ("true".equalsIgnoreCase(sysConfigService.getValue("precision.trim.zero", "false"))) {
                return scaled.stripTrailingZeros().toPlainString();
            }
            return scaled.toPlainString();
        } catch (Exception e) {
            return value.toString(); // 任何异常回退原样，绝不影响打印出纸
        }
    }

    private int intConfig(String key, int dft) {
        try {
            int n = Integer.parseInt(sysConfigService.getValue(key, String.valueOf(dft)).trim());
            return (n < 0 || n > 6) ? dft : n;
        } catch (Exception e) {
            return dft;
        }
    }


    /** 二维码域名在渲染数据里的保留键 */
    private static final String QR_BASE_URL_KEY = "__qrBaseUrl";

    /** 二维码占位：{{qr:bill}} / {{qr:material}} / {{qr:operation}} / {{qr:text:任意文本}}，可带 ,size=200 */
    private static final Pattern QR_PATTERN = Pattern.compile("\\{\\{qr:([^}]*)\\}\\}");

    /** 把二维码占位替换为内联 <img>（内联 base64 → 打印时不依赖网络/跨域，保证码一定打得出来） */
    private String renderQrCodes(String content, Map<String, Object> data) {
        if (content == null || content.indexOf("{{qr:") < 0) return content;
        Matcher matcher = QR_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1) == null ? "" : matcher.group(1).trim();
            String replacement = "";
            try {
                replacement = buildQrImg(token, data);
            } catch (Exception e) {
                log.warn("二维码占位渲染失败: {}", token, e);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String buildQrImg(String token, Map<String, Object> data) {
        int size = 160;
        String target = token;
        int comma = token.indexOf(',');
        if (comma >= 0) {
            target = token.substring(0, comma).trim();
            Matcher sm = Pattern.compile("size\\s*=\\s*(\\d+)").matcher(token.substring(comma + 1));
            if (sm.find()) size = Integer.parseInt(sm.group(1));
        }
        String baseUrl = data == null ? null : asText(data.get(QR_BASE_URL_KEY));
        String lower = target.toLowerCase(Locale.ROOT);
        // 公司标识随码下发并纳入签名（2026-10-25）：防跨公司撞号（各自独立库、主键会重复）
        String qrTenant = com.erp.tenant.TenantContextHolder.getTenantDbName();
        String content;
        if (lower.startsWith("text:")) {
            content = target.substring(5).trim();
        } else if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("/")) {
            content = target;
        } else if ("bill".equals(lower)) {
            Long id = asLong(data == null ? null : data.get("billId"));
            if (id == null) return "";
            String billTypeVal = asText(data.get("billType"));
            content = com.erp.common.qrcode.QrCodeContent.bill(baseUrl, billTypeVal, id,
                    qrCodeSigner.sign(com.erp.common.qrcode.QrCodeContent.TYPE_BILL, billTypeVal, id, qrTenant), qrTenant);
        } else if ("material".equals(lower)) {
            Long id = asLong(firstNonNull(data, "materialId", "id"));
            if (id == null) return "";
            content = com.erp.common.qrcode.QrCodeContent.material(baseUrl, id,
                    qrCodeSigner.sign(com.erp.common.qrcode.QrCodeContent.TYPE_MATERIAL, null, id, qrTenant), qrTenant);
        } else if ("operation".equals(lower)) {
            Long id = asLong(firstNonNull(data, "operationRowId", "operationId", "id"));
            if (id == null) return "";
            content = com.erp.common.qrcode.QrCodeContent.operation(baseUrl, id,
                    qrCodeSigner.sign(com.erp.common.qrcode.QrCodeContent.TYPE_OPERATION, null, id, qrTenant), qrTenant);
        } else {
            content = target;
        }
        if (content == null || content.isBlank()) return "";
        String uri = qrCodeService.dataUri(content, size);
        return "<img class=\"qr-code\" src=\"" + uri + "\" width=\"" + size + "\" height=\"" + size + "\" alt=\"QR\" />";
    }

    private Object firstNonNull(Map<String, Object> data, String... keys) {
        if (data == null) return null;
        for (String k : keys) {
            Object v = data.get(k);
            if (v != null && !String.valueOf(v).isBlank()) return v;
        }
        return null;
    }

    private String asText(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private void clearDefault(String docType) {
        QueryWrapper<PrintTemplate> qw = new QueryWrapper<>();
        qw.eq("doc_type", docType).eq("is_default", 1);
        List<PrintTemplate> defaults = list(qw);
        for (PrintTemplate t : defaults) {
            t.setIsDefault(0);
            updateById(t);
        }
    }

    private Object getValueFromMap(Map<String, Object> data, String key) {
        if (data == null || key == null) {
            return null;
        }
        if (data.containsKey(key)) {
            return data.get(key);
        }
        if (key.contains(".")) {
            String[] keys = key.split("\\.");
            Object current = data;
            for (String k : keys) {
                if (current instanceof Map) {
                    current = ((Map<?, ?>) current).get(k);
                } else {
                    return null;
                }
            }
            return current;
        }
        return null;
    }
}
