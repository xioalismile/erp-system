package com.erp.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.util.CustomFieldSearchHelper;
import com.erp.common.util.SortHelper;
import com.erp.inventory.entity.InventoryBatch;
import com.erp.inventory.service.InventoryBatchService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.entity.Supplier;
import com.erp.masterdata.service.MaterialService;
import com.erp.masterdata.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/inventory/batch")
@RequiredArgsConstructor
public class InventoryBatchController {

    private final InventoryBatchService batchService;
    private final MaterialService materialService;
    private final SupplierService supplierService;
    /** 批次号规则（2026-11-06）：单据页自动带出与只读口径 */
    private final com.erp.inventory.service.BatchNoRuleService batchNoRuleService;
    /** 批次溯源（2026-11-06）：单据 + 物料 + 多层组成批次 */
    private final com.erp.inventory.batch.service.BatchTraceQueryService batchTraceQueryService;

    /** 统一数据权限解析器：按角色 data_scope（仅本人/本部门/全部数据）过滤，创建人列 created_by */
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 批次库存可排序列白名单（关联字段走子查询表达式）；不在白名单的列忽略，防注入 */
    private static final Map<String, String> BATCH_SORTABLE = Map.ofEntries(
            Map.entry("batch_no", "batch_no"),
            Map.entry("material_code", "(SELECT m.material_code FROM material m WHERE m.material_id = batch.material_id)"),
            Map.entry("material_name", "(SELECT m.material_name FROM material m WHERE m.material_id = batch.material_id)"),
            Map.entry("supplier_name", "(SELECT s.supplier_name FROM supplier s WHERE s.supplier_id = batch.supplier_id)"),
            Map.entry("manufacturing_no", "manufacturing_no"),
            Map.entry("quantity", "quantity"),
            Map.entry("available_quantity", "available_quantity"),
            Map.entry("frozen_quantity", "frozen_quantity"),
            Map.entry("unit_cost", "unit_cost"),
            Map.entry("incoming_date", "incoming_date"),
            Map.entry("production_date", "production_date"),
            Map.entry("expiry_date", "expiry_date"),
            Map.entry("status", "status"),
            Map.entry("created_at", "created_at")
    );

    /**
     * 批次库存分页查询（口径对齐 docs/列表页使用说明书-以销售订单为基准.md）
     * 入参：current/size（current 优先、page 旧名兜底）、keyword（批次号 + 物料编码 + 物料名称，成组）、
     *      batchNo、supplierName、status、sortBy/sortOrder（白名单）、customSearch（物料自定义字段）
     * 出参：PageResult{records,total,current,size}，records 带物料编码/名称与供应商名称展示字段
     * 2026-09-16 修复：此前接口只接 materialId/batchNo/onlyExpired 且返回全量数组，
     *     页面传的 物料/供应商/状态/分页/排序 全部被忽略（三个搜索框点了没反应）
     */
    @GetMapping("/list")
    public Result<PageResult<InventoryBatch>> list(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String batchNo,
            // 列表页通用口径：一个框同时匹配 批次号 / 物料编码 / 物料名称
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String status,
            // 列表页通用口径：自定义字段搜索（JSON {fieldKey: 值}），按物料的自定义字段过滤
            @RequestParam(required = false) String customSearch,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(defaultValue = "false") Boolean onlyExpired) {
        if (Boolean.TRUE.equals(onlyExpired)) {
            List<InventoryBatch> expired = batchService.getExpiredBatches();
            fillDisplayFields(expired);
            return Result.success(PageResult.of(expired, expired.size(), 1, expired.isEmpty() ? 20 : expired.size()));
        }
        int[] p = com.erp.common.query.ListQuerySupport.resolvePage(current, page, size);
        QueryWrapper<InventoryBatch> wrapper = new QueryWrapper<>();
        if (materialId != null) {
            wrapper.eq("material_id", materialId);
        }
        if (batchNo != null && !batchNo.isBlank()) {
            wrapper.like("batch_no", batchNo.trim());
        }
        // 关键词成组：批次号 或 物料编码/名称。整体作为一个 AND 条件，不会绕过供应商/状态/自定义字段筛选
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            wrapper.and(w -> w.like("batch_no", keyword.trim())
                    .or().apply("material_id IN (SELECT material_id FROM material WHERE material_code ILIKE {0} OR material_name ILIKE {0})", kw));
        }
        if (supplierName != null && !supplierName.isBlank()) {
            wrapper.apply("supplier_id IN (SELECT supplier_id FROM supplier WHERE supplier_name ILIKE {0})",
                    "%" + supplierName.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            // "已过期"不是库里的状态值，而是"有效期早于今天"的派生状态（不这样处理该页签会永远 0 条）
            if ("已过期".equals(status.trim())) {
                wrapper.lt("expiry_date", java.time.LocalDate.now());
            } else {
                wrapper.eq("status", status.trim());
            }
        }
        // 物料自定义字段搜索（设置中心勾了「可搜索」的字段）
        CustomFieldSearchHelper.apply(wrapper, "material", customSearch, "material_id");
        // 数据权限（按角色 data_scope：仅本人/本部门/全部数据）：batch 表创建人列 created_by，分页 total 随过滤变化
        dataScopeResolver.applyIfLimited(wrapper, null, "created_by");
        // 排序：白名单（关联列走子查询表达式）；无有效排序时按有效期升序（与原行为一致）
        String sortCol = SortHelper.camelToSnake(sortBy == null ? "" : sortBy.trim());
        String sortExpr = BATCH_SORTABLE.get(sortCol);
        boolean asc = "asc".equalsIgnoreCase(sortOrder == null ? "" : sortOrder.trim());
        boolean desc = "desc".equalsIgnoreCase(sortOrder == null ? "" : sortOrder.trim());
        if (sortExpr == null || (!asc && !desc)) {
            wrapper.orderByAsc("expiry_date");
        } else {
            wrapper.orderBy(true, asc, sortExpr);
        }
        Page<InventoryBatch> pageParam = new Page<>(p[0], p[1]);
        Page<InventoryBatch> result = batchService.page(pageParam, wrapper);
        fillDisplayFields(result.getRecords());
        return Result.success(PageResult.of(result.getRecords(), result.getTotal(), p[0], p[1]));
    }

    /**
     * 批量补齐列表展示字段：物料编码/名称、供应商名称。
     * 一次查两类主档（listByIds），不做逐行查询。
     */
    private void fillDisplayFields(List<InventoryBatch> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> materialIds = new HashSet<>();
        Set<Long> supplierIds = new HashSet<>();
        for (InventoryBatch b : records) {
            if (b.getMaterialId() != null) materialIds.add(b.getMaterialId());
            if (b.getSupplierId() != null) supplierIds.add(b.getSupplierId());
        }
        Map<Long, Material> materialMap = new HashMap<>();
        if (!materialIds.isEmpty()) {
            for (Material m : materialService.listByIds(materialIds)) {
                if (m != null && m.getMaterialId() != null) materialMap.put(m.getMaterialId(), m);
            }
        }
        Map<Long, Supplier> supplierMap = new HashMap<>();
        if (!supplierIds.isEmpty()) {
            for (Supplier s : supplierService.listByIds(supplierIds)) {
                if (s != null && s.getSupplierId() != null) supplierMap.put(s.getSupplierId(), s);
            }
        }
        for (InventoryBatch b : records) {
            Material m = materialMap.get(b.getMaterialId());
            if (m != null) {
                b.setMaterialCode(m.getMaterialCode());
                b.setMaterialName(m.getMaterialName());
            }
            Supplier s = supplierMap.get(b.getSupplierId());
            if (s != null) {
                b.setSupplierName(s.getSupplierName());
            }
        }
    }

    /**
     * 批次字段口径 + 自动生成预览（2026-11-06）
     * 入参：materialId 物料主键；used 当前单据已用批次号（逗号分隔，生成时跳过）
     * 出参：enabled 批次功能是否可用（按物料批次管理，不再有全局开关）/ lotControl 物料批次管理 /
     *       autoGenerate 自动生成 / allowModify 允许修改 / batchNo 下一个可用号
     * 说明：materialId 为空时只返回默认规则口径（页面初始化用）。
     */
    @GetMapping("/rule-info")
    public Result<Map<String, Object>> ruleInfo(@RequestParam(required = false) Long materialId,
                                                @RequestParam(required = false) String used) {
        if (materialId == null) {
            Map<String, Object> out = new HashMap<>();
            Map<String, Object> rule = batchNoRuleService.rule(null);
            // 批次功能按物料管理：页面初始化时未选物料，按"可用"返回，具体显隐由每行物料决定
            out.put("enabled", true);
            out.put("lotControl", false);
            out.put("autoGenerate", batchNoRuleService.isAutoGenerate(rule));
            out.put("allowModify", batchNoRuleService.isAllowModify(rule));
            out.put("batchNo", null);
            return Result.success(out);
        }
        List<String> usedNos = new java.util.ArrayList<>();
        if (used != null && !used.isBlank()) {
            for (String s : used.split(",")) {
                if (!s.isBlank()) usedNos.add(s.trim());
            }
        }
        return Result.success(batchNoRuleService.ruleInfo(materialId, usedNos));
    }

    /**
     * 批次溯源（2026-11-06）
     * 入参：batchNo 批次号、materialId 物料主键、depth 组成物料递归层数（默认 5，最大 8）
     * 出参：batch 批次概要 / inbound 入库单据 / outbound 出库单据 / components 多层组成物料 / downstream 下游去向 / notes 提示
     */
    @GetMapping("/trace")
    public Result<Map<String, Object>> trace(@RequestParam String batchNo,
                                             @RequestParam Long materialId,
                                             @RequestParam(required = false) Integer depth) {
        return Result.success(batchTraceQueryService.trace(batchNo, materialId, depth));
    }

    /**
     * 批次库存顶部统计卡片（独立请求）
     * 返回：totalCount 批次总数、normalCount 正常、frozenCount 冻结、usedUpCount 已用完
     * 说明：卡片值取接口，页面不再用"当前页行"自行统计（分页时那样会算错）；
     *      batch 表没有仓库列，本接口不涉及仓库数据权限。
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new HashMap<>();
        data.put("totalCount", batchService.count(scopeWrapper(null)));
        data.put("normalCount", batchService.count(scopeWrapper("正常")));
        data.put("frozenCount", batchService.count(scopeWrapper("冻结")));
        data.put("usedUpCount", batchService.count(scopeWrapper("已用完")));
        return Result.success(data);
    }

    /** 统计查询条件（含统一数据权限：按角色 data_scope 过滤创建人，卡片与列表同口径） */
    private QueryWrapper<InventoryBatch> scopeWrapper(String status) {
        QueryWrapper<InventoryBatch> w = new QueryWrapper<>();
        if (status != null) {
            w.eq("status", status);
        }
        dataScopeResolver.applyIfLimited(w, null, "created_by");
        return w;
    }

    @GetMapping("/material/{materialId}")
    public Result<List<InventoryBatch>> getByMaterial(@PathVariable Long materialId) {
        return Result.success(batchService.getByMaterial(materialId));
    }

    @GetMapping("/{id}")
    public Result<InventoryBatch> getById(@PathVariable Long id) {
        return Result.success(batchService.getById(id));
    }

    @GetMapping("/expired")
    public Result<List<InventoryBatch>> getExpired() {
        return Result.success(batchService.getExpiredBatches());
    }

    @GetMapping("/expiring")
    public Result<List<InventoryBatch>> getExpiring(@RequestParam(defaultValue = "7") Integer days) {
        return Result.success(batchService.getExpiringBatches(days));
    }

    @PostMapping("/allocate")
    public Result<Map<String, Object>> allocate(
            @RequestParam Long materialId,
            @RequestParam BigDecimal quantity) {
        return Result.success(batchService.allocateByFifo(materialId, quantity));
    }

    @PostMapping
    public Result<InventoryBatch> create(@RequestBody InventoryBatch batch) {
        batchService.addBatch(batch);
        return Result.success(batch);
    }

    @PutMapping("/{id}")
    public Result<InventoryBatch> update(@PathVariable Long id, @RequestBody InventoryBatch batch) {
        batch.setBatchId(id);
        batchService.updateById(batch);
        return Result.success(batch);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        batchService.removeById(id);
        return Result.success();
    }
}
