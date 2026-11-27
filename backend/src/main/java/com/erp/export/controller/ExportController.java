package com.erp.export.controller;

import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.export.dto.ExportConfigDTO;
import com.erp.export.dto.ImportResultDTO;
import com.erp.export.dto.BomImportCheckDTO;
import com.erp.export.service.ExcelService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExcelService excelService;
    /** 字段权限：导出统一标记页面键（写盘时由 {@code FieldPermissionOutputFilter} 按列剔） */
    private final com.erp.permission.service.FieldPermissionOutputFilter fieldPermissionOutputFilter;

    /** 导出前统一标记字段权限页面键（按实体类自动推导，不依赖接口路径） */
    private void markFieldPermission(String entityName) {
        com.erp.export.registry.BillExcelRegistry.Bill bill =
                com.erp.export.registry.BillExcelRegistry.get(entityName);
        if (bill != null && bill.entityClass() != null) {
            fieldPermissionOutputFilter.markExportPageKey(bill.entityClass());
        }
    }

    @GetMapping("/config/{entityName}")
    public Result<ExportConfigDTO> getExportConfig(@PathVariable String entityName) {
        return Result.success(excelService.getExportConfig(entityName));
    }

    @GetMapping("/template/{entityName}")
    public void exportTemplate(@PathVariable String entityName,
                               @RequestParam(value = "fields", required = false) List<String> fields,
                               @RequestParam(value = "billType", required = false) String billType,
                               HttpServletResponse response) {
        // 模板分派完全由登记表 Group 决定：未登记实体显式报错。
        // 不再用 startsWith 前缀猜 —— 历史 BUG：startsWith("operation") 把"工序汇报单(operationReport)"
        // 误判成财务的"费用支出单"，模板下载报"不支持的财务单据"。
        com.erp.export.registry.BillExcelRegistry.Bill bill =
                com.erp.export.registry.BillExcelRegistry.get(entityName);
        if (bill == null) {
            throw new IllegalArgumentException("不支持的实体类型: " + entityName);
        }
        // 台账已登记但导入导出尚未开放（如 采购费用单/打样单/免费样品单：实体零个 @ExportField，待补专用导出列）
        if (!bill.exportable()) {
            throw new IllegalArgumentException("该单据尚未开放导出: " + entityName + "（" + bill.name() + "）");
        }
        // 库存现存量：查询视图，无 @ExportField 且需关联物料/仓库/单位，走专用导出
        if ("inventory".equals(entityName)) {
            excelService.exportInventoryCurrent(response, true);
            return;
        }
        // 销售/采购价格策略：表头+明细结构，导出/模板/导入共用一套扁平列（一行=一个对象的价格），走专用导出
        if ("salePriceStrategy".equals(entityName) || "purchasePriceStrategy".equals(entityName)) {
            excelService.exportPriceStrategyTemplate(entityName, response);
            return;
        }
        switch (bill.group()) {
            case SALE -> excelService.exportSaleBillTemplate(entityName, fields, response);
            case PURCHASE -> excelService.exportPurchaseTemplate(entityName, fields, response);
            case INVENTORY -> excelService.exportInventoryTemplate(entityName, fields, billType, response);
            case FINANCE -> excelService.exportFinanceTemplate(entityName, fields, response);
            // 基础资料 / 生产 / 委外 / 查询视图：走实体 @ExportField 反射列
            // （BILL_ITEM_SPECS 有登记的会自动追加明细列）
            case BASE, PRODUCTION, SUBCONTRACT, OTHER -> excelService.exportTemplate(entityName, response);
        }
    }

    /** 销售订单导入模板字段清单（导入弹窗"模板字段设置"数据源） */
    @GetMapping("/template/saleOrder/fields")
    public Result<Map<String, List<String>>> getSaleOrderTemplateFields() {
        return Result.success(excelService.getSaleOrderTemplateFields());
    }

    /** 通用单据模板字段清单（销售+采购+库存+财务，required=必带列含明细+自定义字段；optional=可勾选表头列） */
    @GetMapping("/template/{entityName}/fields")
    public Result<Map<String, List<String>>> getTemplateFields(@PathVariable String entityName) {
        com.erp.export.registry.BillExcelRegistry.Group group =
                com.erp.export.registry.BillExcelRegistry.groupOf(entityName);
        if (group == com.erp.export.registry.BillExcelRegistry.Group.SALE) {
            return Result.success(excelService.getSaleTemplateFields(entityName));
        }
        if (group == com.erp.export.registry.BillExcelRegistry.Group.PURCHASE) {
            return Result.success(excelService.getPurchaseTemplateFields(entityName));
        }
        if (group == com.erp.export.registry.BillExcelRegistry.Group.INVENTORY) {
            return Result.success(excelService.getInventoryTemplateFields(entityName));
        }
        if (group == com.erp.export.registry.BillExcelRegistry.Group.FINANCE) {
            return Result.success(excelService.getFinanceTemplateFields(entityName));
        }
        return Result.success(excelService.getSaleTemplateFields(entityName));
    }

    /**
     * 导出数据。对 customer 实体支持按 poolType 过滤：
     *   poolType=0 仅导出私海客户（私海入口再按当前用户 ownerUserId 过滤）
     *   poolType=1 仅导出公海客户
     *   不传 poolType 时导出全部客户（兼容旧逻辑）
     */
    @PostMapping("/data/{entityName}")
    public void exportData(@PathVariable String entityName,
                           @RequestParam(value = "poolType", required = false) Integer poolType,
                           HttpServletRequest request,
                           HttpServletResponse response) {
        // 台账已登记但导入导出尚未开放的单据：显式拒绝，避免又变成"用户点了导出只看到一句失败"
        com.erp.export.registry.BillExcelRegistry.Bill bill =
                com.erp.export.registry.BillExcelRegistry.get(entityName);
        if (bill != null && !bill.exportable()) {
            throw new IllegalArgumentException("该单据尚未开放导出: " + entityName + "（" + bill.name() + "）");
        }
        markFieldPermission(entityName);
        List<?> dataList;
        // 库存现存量：查询视图，无 @ExportField，走专用导出（导全量现存量，含关联的物料/仓库/单位）
        if ("inventory".equals(entityName)) {
            excelService.exportInventoryCurrent(response, false);
            return;
        }
        // 销售/采购价格策略：表头+明细结构，一行=一个对象的价格，走专用导出（对象编码/名称取对象实际值）
        if ("salePriceStrategy".equals(entityName) || "purchasePriceStrategy".equals(entityName)) {
            excelService.exportPriceStrategyData(entityName, response);
            return;
        }
        if ("customer".equals(entityName) && poolType != null) {
            Long ownerUserId = null;
            // 私海导出按当前用户过滤；公海导出不按用户过滤
            if (poolType == 0) {
                ownerUserId = SecurityUtils.getCurrentUserId(request);
            }
            dataList = excelService.listCustomersByPoolType(poolType, ownerUserId);
        } else {
            dataList = excelService.listAllData(entityName);
        }
        excelService.exportData(entityName, dataList, response);
    }

    /**
     * 导出「勾选的 N 条」（列表页批量导出的"导出选中"专用，2026-09-19 新增）。
     *
     * <p>为什么不改上面那个 {@code /data/{entityName}}：那个接口有 30 个页面在调（全量导出），
     * 动它要回归所有导出链路；这里单开一个端点，零回归风险。</p>
     *
     * @param ids 勾选记录的主键数组；为空则导出空表头（不回退全量）
     */
    @PostMapping("/data/{entityName}/selected")
    public void exportSelected(@PathVariable String entityName,
                               @RequestBody(required = false) List<Long> ids,
                               HttpServletResponse response) {
        com.erp.export.registry.BillExcelRegistry.Bill bill =
                com.erp.export.registry.BillExcelRegistry.get(entityName);
        if (bill != null && !bill.exportable()) {
            throw new IllegalArgumentException("该单据尚未开放导出: " + entityName + "（" + bill.name() + "）");
        }
        markFieldPermission(entityName);
        excelService.exportSelected(entityName, ids, response);
    }

    /**
     * 自定义列导出：用户选择要导出的字段
     * @param entityName 实体名
     * @param selectedFields 用户选择的字段名列表（Java 字段名）
     */
    @PostMapping("/data/{entityName}/custom")
    public void exportDataWithSelectedFields(
            @PathVariable String entityName,
            @RequestBody(required = false) List<String> selectedFields,
            HttpServletResponse response) {
        markFieldPermission(entityName);
        excelService.exportDataWithSelectedFields(entityName, selectedFields, response);
    }

    /**
     * 「页面看到什么就导出什么」通用导出（2026-09-24 用户口径，全站统一）。
     *
     * <p>与 {@code /data/{entityName}}（固定全量列）和 {@code /custom}（全量行 + 前端选列）的区别：
     * 列 = **页面当前可见列**（顺序、列名、自定义字段列全部照页面），行 = **页面当前筛选结果的这批主键**。
     * 列表页的导出统一走这里，不再由各页/后端各自决定列。</p>
     *
     * <p>body：{@code {"ids":[主键...]（可空=全量）, "fields":[{"key":"customerName","label":"客户名称","custom":false}, ...]}}</p>
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/data/{entityName}/visible")
    public void exportVisible(@PathVariable String entityName,
                              @RequestBody(required = false) Map<String, Object> body,
                              HttpServletResponse response) {
        com.erp.export.registry.BillExcelRegistry.Bill bill =
                com.erp.export.registry.BillExcelRegistry.get(entityName);
        if (bill != null && !bill.exportable()) {
            throw new IllegalArgumentException("该单据尚未开放导出: " + entityName + "（" + bill.name() + "）");
        }
        markFieldPermission(entityName);
        List<Long> ids = new java.util.ArrayList<>();
        Object idsObj = body == null ? null : body.get("ids");
        if (idsObj instanceof List) {
            for (Object o : (List<?>) idsObj) {
                if (o instanceof Number) {
                    ids.add(((Number) o).longValue());
                } else if (o != null) {
                    try { ids.add(Long.valueOf(o.toString().trim())); } catch (NumberFormatException ignore) { }
                }
            }
        }
        List<Map<String, Object>> fields = new java.util.ArrayList<>();
        Object fieldsObj = body == null ? null : body.get("fields");
        if (fieldsObj instanceof List) {
            for (Object o : (List<?>) fieldsObj) {
                if (o instanceof Map) fields.add((Map<String, Object>) o);
            }
        }
        // 页面手上的行数据（推荐传）：直接用页面显示的那批值导出，展示字段/别名列/蛇形列都能带上
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        Object rowsObj = body == null ? null : body.get("rows");
        if (rowsObj instanceof List) {
            for (Object o : (List<?>) rowsObj) {
                if (o instanceof Map) rows.add((Map<String, Object>) o);
            }
        }
        excelService.exportVisibleColumns(entityName, ids, rows, fields, response);
    }

    /**
     * 导入数据。对 customer 实体支持 poolType 参数：
     *   poolType=0 导入到私海（归属当前用户）
     *   poolType=1 导入到公海（无归属人）
     *   不传 poolType 时走通用导入（兼容旧逻辑）
     */
    @PostMapping("/import/{entityName}")
    public Result<ImportResultDTO> importData(@PathVariable String entityName,
                                              @RequestParam(value = "poolType", required = false) Integer poolType,
                                              @RequestParam(value = "billType", required = false) String billType,
                                              @RequestParam("file") MultipartFile file,
                                              HttpServletRequest request) {
        // 原先这里是一条 24 分支的 if/else 实体清单（漏加一个就掉进"通用导入"只落表头、明细静默丢失）。
        // 现已改为登记表分派：见 ExcelServiceImpl#importByEntity 与 BillExcelRegistry。
        // 仅"客户池导入"因需带归属人 userId 单独处理。
        ImportResultDTO result;
        if ("customer".equals(entityName) && poolType != null) {
            Long ownerUserId = SecurityUtils.getCurrentUserId(request);
            result = excelService.importDataWithPool(entityName, file, poolType, ownerUserId);
        } else {
            result = excelService.importByEntity(entityName, file, billType);
        }
        return Result.success(result);
    }

    /**
     * 物料导入预检查：与物料库比对存在性（先编码、后名称），返回冲突清单供用户选择"覆盖/跳过"。
     * 该接口不写库、不落盘图片。
     */
    @PostMapping("/material/import/check")
    public Result<com.erp.export.dto.MaterialImportCheckDTO> checkMaterialImport(
            @RequestParam("file") MultipartFile file) {
        return Result.success(excelService.checkMaterialImport(file));
    }

    /**
     * 物料导入确认：按策略处理已存在物料。
     * @param onExistingConflict SKIP=跳过已存在（默认）/ OVERWRITE=覆盖已存在（物料编码保持原值）
     */
    @PostMapping("/material/import/confirm")
    public Result<ImportResultDTO> confirmMaterialImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "onExistingConflict", defaultValue = "SKIP") String onExistingConflict) {
        return Result.success(excelService.confirmMaterialImport(file, onExistingConflict));
    }

    @GetMapping("/bom/template")
    public void exportBomTemplate(
            @RequestParam(value = "fields", required = false) List<String> fields,
            HttpServletResponse response) {
        // 精简 18 列统一模板（默认）+ 支持按勾选字段自定义列（锁定必填列自动兜底）
        excelService.exportBomTemplate(fields, response);
    }

    /** 旧版兼容模板（11 列纯树形格式；保留历史存量文件兼容，不支持字段自定义） */
    @GetMapping("/bom/legacy-template")
    public void exportBomLegacyTemplate(HttpServletResponse response) {
        excelService.exportBomLegacyTemplate(response);
    }

    /** BOM导入模板可选字段清单（前端"自定义模板字段"勾选面板数据源） */
    @GetMapping("/bom/template/fields")
    public Result<Map<String, Object>> getBomTemplateFields() {
        return Result.success(excelService.getBomTemplateFields());
    }

    @GetMapping("/bom/multi-level/export/{bomId}")
    public void exportBomMultiLevel(@PathVariable Long bomId, HttpServletResponse response) {
        excelService.exportBomMultiLevel(bomId, response);
    }

    @PostMapping("/bom/multi-level/import")
    public Result<ImportResultDTO> importBomMultiLevel(@RequestParam("file") MultipartFile file) {
        ImportResultDTO result = excelService.importBomMultiLevel(file);
        return Result.success(result);
    }

    /**
     * BOM导入预检查 - 检测文件中缺失的物料
     */
    @PostMapping("/bom/import/check")
    public Result<BomImportCheckDTO> checkBomImport(@RequestParam("file") MultipartFile file) {
        BomImportCheckDTO result = excelService.checkBomImport(file);
        return Result.success(result);
    }

    /**
     * BOM导入 - 一键同步缺失物料到物料库（只补物料主档，不导入BOM）
     */
    @PostMapping("/bom/import/sync-materials")
    public Result<java.util.Map<String, Object>> syncBomMissingMaterials(@RequestParam("file") MultipartFile file) {
        return Result.success(excelService.syncBomMissingMaterials(file));
    }

    /**
     * BOM确认导入 - 可选择自动创建缺失物料 + 同母件已有BOM冲突处理策略
     */
    @PostMapping("/bom/import/confirm")
    public Result<ImportResultDTO> confirmBomImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "autoCreateMaterials", defaultValue = "false") boolean autoCreateMaterials,
            @RequestParam(value = "onExistingConflict", defaultValue = "NEW_VERSION") String onExistingConflict) {
        ImportResultDTO result = excelService.confirmBomImport(file, autoCreateMaterials, onExistingConflict);
        return Result.success(result);
    }
}