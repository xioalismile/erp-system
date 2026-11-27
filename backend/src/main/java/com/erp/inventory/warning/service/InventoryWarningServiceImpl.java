package com.erp.inventory.warning.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Warehouse;
import com.erp.basedata.mapper.WarehouseMapper;
import com.erp.common.PageResult;
import com.erp.common.util.SortHelper;
import com.erp.export.dto.ImportResultDTO;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.service.InventoryService;
import com.erp.inventory.warning.dto.InventoryWarningImportCheckDTO;
import com.erp.inventory.warning.dto.InventoryWarningRuleBatchDTO;
import com.erp.inventory.warning.entity.InventoryWarningLog;
import com.erp.inventory.warning.entity.InventoryWarningRule;
import com.erp.inventory.warning.mapper.InventoryWarningLogMapper;
import com.erp.inventory.warning.mapper.InventoryWarningRuleMapper;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryWarningServiceImpl extends ServiceImpl<InventoryWarningRuleMapper, InventoryWarningRule> implements InventoryWarningService {

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "create_by",
            "create_time",
            "created_at",
            "id",
            "is_handled",
            "material_code",
            "material_id",
            "material_name",
            "max_quantity",
            "min_quantity",
            "notify_type",
            "notify_users",
            "remark",
            "rule_id",
            "rule_name",
            "status",
            "update_time",
            "warehouse_id",
            "warehouse_name",
            "warning_level",
            "warning_type"
        );

    private final InventoryWarningLogMapper warningLogMapper;
    private final InventoryService inventoryService;
    private final MaterialService materialService;
    private final WarehouseMapper warehouseMapper;

    /** 统一数据权限解析器：按角色 data_scope（仅本人/本部门/全部数据）过滤 */
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @Override
    public PageResult<InventoryWarningRule> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder) {
        QueryWrapper<InventoryWarningRule> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("rule_name", keyword)
                    .or().like("material_code", keyword)
                    .or().like("material_name", keyword));
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        // 数据权限（按角色 data_scope）：创建人列 create_by（实体 InventoryWarningRule.createBy），total 随过滤变化
        dataScopeResolver.applyIfLimited(wrapper, null, "create_by");
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, InventoryWarningRule.class);
        Page<InventoryWarningRule> page = new Page<>(current, size);
        Page<InventoryWarningRule> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public InventoryWarningRule getDetail(Long id) {
        return getById(id);
    }

    /** 导入模板列（顺序即解析顺序；带 * 为演示用必填标记，解析时按归一化表头匹配） */
    private static final List<String> TEMPLATE_HEADERS = Arrays.asList(
            "货品编码*", "货品名称", "仓库", "最低库存", "最高库存", "通知方式", "状态", "备注");

    @Override
    @Transactional
    public void saveRule(InventoryWarningRule rule) {
        fillReferences(rule);
        // 预警类型内置"双向"
        rule.setWarningType("BOTH");
        if (rule.getStatus() == null) {
            rule.setStatus(1);
        }
        if (!StringUtils.hasText(rule.getRuleName())) {
            rule.setRuleName(buildAutoName(rule));
        }
        save(rule);
    }

    @Override
    @Transactional
    public void saveRuleBatch(InventoryWarningRuleBatchDTO dto) {
        if (dto == null || dto.getLines() == null || dto.getLines().isEmpty()) {
            throw new IllegalArgumentException("请至少添加一个货品");
        }
        Set<String> seen = new HashSet<>();
        List<InventoryWarningRule> rules = new ArrayList<>();
        for (InventoryWarningRuleBatchDTO.Line line : dto.getLines()) {
            if (line == null || line.getMaterialId() == null) {
                continue;
            }
            // 同一货品 + 同一仓库只保留一条
            String key = line.getMaterialId() + "#" + (line.getWarehouseId() == null ? "all" : line.getWarehouseId());
            if (!seen.add(key)) {
                continue;
            }
            InventoryWarningRule rule = new InventoryWarningRule();
            rule.setMaterialId(line.getMaterialId());
            rule.setWarehouseId(line.getWarehouseId());
            fillReferences(rule);
            // 预警类型内置"双向"：上下限各自独立判断（填了才按该方向预警），界面不再出现预警类型
            rule.setWarningType("BOTH");
            rule.setMinQuantity(line.getMinQuantity());
            rule.setMaxQuantity(line.getMaxQuantity());
            rule.setNotifyType(dto.getNotifyType() == null ? 1 : dto.getNotifyType());
            rule.setNotifyUsers(dto.getNotifyUsers());
            rule.setRemark(dto.getRemark());
            rule.setStatus(line.getStatus() == null ? 1 : line.getStatus());
            rule.setRuleName(buildAutoName(rule));
            rules.add(rule);
        }
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("请至少添加一个货品");
        }
        saveBatch(rules);
    }

    @Override
    @Transactional
    public void updateRule(InventoryWarningRule rule) {
        fillReferences(rule);
        // 预警类型内置"双向"
        rule.setWarningType("BOTH");
        if (!StringUtils.hasText(rule.getRuleName())) {
            InventoryWarningRule old = rule.getId() == null ? null : getById(rule.getId());
            rule.setRuleName(old != null && StringUtils.hasText(old.getRuleName()) ? old.getRuleName() : buildAutoName(rule));
        }
        updateById(rule);
    }

    /** 按货品ID/仓库ID回填编码、名称（货品/仓库被删时保持原值不覆盖） */
    private void fillReferences(InventoryWarningRule rule) {
        if (rule.getMaterialId() != null) {
            Material material = materialService.getById(rule.getMaterialId());
            if (material != null) {
                rule.setMaterialCode(material.getMaterialCode());
                rule.setMaterialName(material.getMaterialName());
            }
        }
        if (rule.getWarehouseId() != null) {
            Warehouse warehouse = warehouseMapper.selectById(rule.getWarehouseId());
            if (warehouse != null) {
                rule.setWarehouseName(warehouse.getStockName());
            }
        } else {
            rule.setWarehouseName(null);
        }
    }

    /** 规则名称已从界面去掉，保存时按「货品名·仓库名」自动生成（数据库该列非空，需要兜底值） */
    private String buildAutoName(InventoryWarningRule rule) {
        String base = StringUtils.hasText(rule.getMaterialName()) ? rule.getMaterialName()
                : (StringUtils.hasText(rule.getMaterialCode()) ? rule.getMaterialCode() : "货品");
        if (StringUtils.hasText(rule.getWarehouseName())) {
            base = base + "·" + rule.getWarehouseName();
        }
        return base + " 库存预警";
    }

    // ==================== 导入（模板 / 预检查 / 确认） ====================

    @Override
    public byte[] buildImportTemplate() {
        List<List<String>> head = new ArrayList<>();
        for (String h : TEMPLATE_HEADERS) {
            head.add(Collections.singletonList(h));
        }
        List<List<Object>> demo = new ArrayList<>();
        demo.add(Arrays.asList("M-1001", "不锈钢板 304", "原料仓", 100, 5000, "系统消息", "启用", ""));
        demo.add(Arrays.asList("M-1007", "铝型材 6061", "", 200, "", "系统消息", "启用", "未填仓库 = 全部仓库；只填下限 = 只按缺料预警"));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        EasyExcel.write(bos)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet("库存预警规则")
                .head(head)
                .doWrite(demo);
        return bos.toByteArray();
    }

    @Override
    public InventoryWarningImportCheckDTO checkImport(MultipartFile file) {
        ParseOutcome outcome = parseImport(file);
        InventoryWarningImportCheckDTO dto = new InventoryWarningImportCheckDTO();
        dto.setParseSuccess(outcome.parseSuccess);
        dto.setParseError(outcome.parseError);
        dto.setTotalCount(outcome.totalCount);
        dto.setErrors(outcome.errors);
        dto.setErrorCount(outcome.errors.size());
        for (ParsedRow row : outcome.rows) {
            InventoryWarningRule existing = findExisting(row.materialId, row.warehouseId);
            if (existing != null) {
                InventoryWarningImportCheckDTO.Conflict c = new InventoryWarningImportCheckDTO.Conflict();
                c.setRowIndex(row.rowIndex);
                c.setMaterialCode(row.materialCode);
                c.setMaterialName(row.materialName);
                c.setWarehouseId(row.warehouseId);
                c.setWarehouseName(row.warehouseName);
                c.setExistingId(existing.getId());
                dto.getConflicts().add(c);
            } else {
                dto.setNewCount(dto.getNewCount() + 1);
            }
        }
        dto.setConflictCount(dto.getConflicts().size());
        dto.setHasConflict(dto.getConflictCount() > 0);
        return dto;
    }

    @Override
    @Transactional
    public ImportResultDTO confirmImport(MultipartFile file, String onExistingConflict) {
        boolean overwrite = "OVERWRITE".equalsIgnoreCase(onExistingConflict);
        ParseOutcome outcome = parseImport(file);
        ImportResultDTO result = new ImportResultDTO();
        result.setTotalCount(outcome.totalCount);
        result.getErrors().addAll(outcome.errors);
        result.setFailCount(outcome.errors.size());
        int success = 0, updated = 0, skipped = 0;
        List<InventoryWarningRule> toInsert = new ArrayList<>();
        for (ParsedRow row : outcome.rows) {
            InventoryWarningRule existing = findExisting(row.materialId, row.warehouseId);
            if (existing != null) {
                if (overwrite) {
                    // 预警类型内置"双向"
                    existing.setWarningType("BOTH");
                    existing.setMinQuantity(row.minQuantity);
                    existing.setMaxQuantity(row.maxQuantity);
                    existing.setNotifyType(row.notifyType);
                    existing.setStatus(row.status);
                    existing.setRemark(row.remark);
                    updateById(existing);
                    updated++;
                } else {
                    skipped++;
                }
            } else {
                toInsert.add(buildRuleFromRow(row));
            }
        }
        if (!toInsert.isEmpty()) {
            saveBatch(toInsert);
            success = toInsert.size();
        }
        result.setSuccessCount(success);
        result.setUpdatedCount(updated);
        result.setSkipCount(skipped);
        result.setMessage("导入完成：新增 " + success + " 条，覆盖 " + updated + " 条，跳过 " + skipped + " 条"
                + (result.getFailCount() > 0 ? "，失败 " + result.getFailCount() + " 条" : ""));
        return result;
    }

    private InventoryWarningRule buildRuleFromRow(ParsedRow row) {
        InventoryWarningRule rule = new InventoryWarningRule();
        rule.setMaterialId(row.materialId);
        rule.setMaterialCode(row.materialCode);
        rule.setMaterialName(row.materialName);
        rule.setWarehouseId(row.warehouseId);
        rule.setWarehouseName(row.warehouseName);
        // 预警类型内置"双向"
        rule.setWarningType("BOTH");
        rule.setMinQuantity(row.minQuantity);
        rule.setMaxQuantity(row.maxQuantity);
        rule.setNotifyType(row.notifyType);
        rule.setStatus(row.status);
        rule.setRemark(row.remark);
        rule.setRuleName(buildAutoName(rule));
        return rule;
    }

    private InventoryWarningRule findExisting(Long materialId, Long warehouseId) {
        QueryWrapper<InventoryWarningRule> qw = new QueryWrapper<>();
        qw.eq("material_id", materialId);
        if (warehouseId == null) {
            qw.isNull("warehouse_id");
        } else {
            qw.eq("warehouse_id", warehouseId);
        }
        qw.last("LIMIT 1");
        return getOne(qw);
    }

    /** 导入文件解析结果 */
    private static class ParseOutcome {
        boolean parseSuccess = true;
        String parseError;
        int totalCount;
        final List<ParsedRow> rows = new ArrayList<>();
        final List<ImportResultDTO.ErrorDetail> errors = new ArrayList<>();

        void addError(int rowIndex, String field, String message) {
            ImportResultDTO.ErrorDetail e = new ImportResultDTO.ErrorDetail();
            e.setRowIndex(rowIndex);
            e.setFieldName(field);
            e.setMessage(message);
            errors.add(e);
        }
    }

    /** 一行 = 一个货品规则 */
    private static class ParsedRow {
        int rowIndex;
        Long materialId;
        String materialCode;
        String materialName;
        Long warehouseId;
        String warehouseName;
        BigDecimal minQuantity;
        BigDecimal maxQuantity;
        Integer notifyType = 1;
        Integer status = 1;
        String remark;
    }

    private ParseOutcome parseImport(MultipartFile file) {
        ParseOutcome outcome = new ParseOutcome();
        Map<String, Material> materialCache = new HashMap<>();
        Map<String, Warehouse> warehouseCache = new HashMap<>();
        try (InputStream in = file.getInputStream()) {
            List<Object> raw = EasyExcel.read(in).headRowNumber(0).sheet().doReadSync();
            List<List<Object>> data = convertRows(raw);
            if (data.isEmpty()) {
                return outcome;
            }
            List<String> headers = new ArrayList<>();
            for (Object o : data.get(0)) {
                headers.add(o == null ? "" : String.valueOf(o).trim());
            }
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                idx.put(normalizeHeader(headers.get(i)), i);
            }

            for (int i = 1; i < data.size(); i++) {
                List<Object> row = data.get(i);
                if (isRowEmpty(row)) {
                    continue;
                }
                outcome.totalCount++;
                int rowIndex = i + 1;
                int errBefore = outcome.errors.size();
                ParsedRow parsed = new ParsedRow();
                parsed.rowIndex = rowIndex;

                String materialCode = cell(row, idx, "货品编码");
                if (!StringUtils.hasText(materialCode)) {
                    outcome.addError(rowIndex, "货品编码", "不能为空");
                } else {
                    Material material = materialCache.computeIfAbsent(materialCode, code ->
                            materialService.getOne(new QueryWrapper<Material>().eq("material_code", code).last("LIMIT 1")));
                    if (material == null) {
                        outcome.addError(rowIndex, "货品编码", "货品[" + materialCode + "]不存在");
                    } else {
                        parsed.materialId = material.getMaterialId();
                        parsed.materialCode = material.getMaterialCode();
                        parsed.materialName = material.getMaterialName();
                    }
                }

                String warehouseText = cell(row, idx, "仓库");
                if (StringUtils.hasText(warehouseText)) {
                    Warehouse warehouse = warehouseCache.computeIfAbsent(warehouseText, key ->
                            warehouseMapper.selectOne(new QueryWrapper<Warehouse>()
                                    .and(w -> w.eq("stock_name", key).or().eq("stock_code", key)).last("LIMIT 1")));
                    if (warehouse == null) {
                        outcome.addError(rowIndex, "仓库", "仓库[" + warehouseText + "]不存在");
                    } else {
                        parsed.warehouseId = warehouse.getStockId();
                        parsed.warehouseName = warehouse.getStockName();
                    }
                }

                parsed.minQuantity = parseDecimal(outcome, rowIndex, "最低库存", cell(row, idx, "最低库存"));
                parsed.maxQuantity = parseDecimal(outcome, rowIndex, "最高库存", cell(row, idx, "最高库存"));

                // 预警类型内置双向：上下限各自独立判断，填了才按该方向预警（0 或留空 = 该方向不预警）
                boolean hasMin = parsed.minQuantity != null && parsed.minQuantity.compareTo(BigDecimal.ZERO) > 0;
                boolean hasMax = parsed.maxQuantity != null && parsed.maxQuantity.compareTo(BigDecimal.ZERO) > 0;
                if (!hasMin && !hasMax) {
                    outcome.addError(rowIndex, "最低库存/最高库存", "至少要填一个（填 0 或留空视为该方向不预警）");
                }

                // 通知方式目前只有「系统消息」可用，其余方式为后续功能预留；导入一律按系统消息处理
                parsed.notifyType = 1;

                String statusText = cell(row, idx, "状态");
                parsed.status = StringUtils.hasText(statusText) && (statusText.contains("停用") || "0".equals(statusText)) ? 0 : 1;

                parsed.remark = cell(row, idx, "备注");

                if (outcome.errors.size() == errBefore) {
                    outcome.rows.add(parsed);
                }
            }
        } catch (IOException e) {
            log.error("库存预警规则导入解析失败", e);
            outcome.parseSuccess = false;
            outcome.parseError = "文件解析失败: " + e.getMessage();
        } catch (Exception e) {
            log.error("库存预警规则导入解析异常", e);
            outcome.parseSuccess = false;
            outcome.parseError = "文件解析失败: " + e.getMessage();
        }
        return outcome;
    }

    private BigDecimal parseDecimal(ParseOutcome outcome, int rowIndex, String field, String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            outcome.addError(rowIndex, field, field + "[" + text + "]不是有效数字");
            return null;
        }
    }

    private String cell(List<Object> row, Map<String, Integer> idx, String header) {
        Integer i = idx.get(normalizeHeader(header));
        if (i == null || i < 0 || i >= row.size()) {
            return null;
        }
        Object v = row.get(i);
        return v == null ? null : String.valueOf(v).trim();
    }

    private String normalizeHeader(String h) {
        if (h == null) return "";
        return h.replace("*", "").replace("　", "").replace(" ", "").trim();
    }

    /** EasyExcel 原始行 → List<List<Object>> */
    private List<List<Object>> convertRows(List<Object> rawData) {
        List<List<Object>> result = new ArrayList<>();
        if (rawData == null) return result;
        for (Object obj : rawData) {
            if (obj instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) obj;
                int max = -1;
                for (Object k : map.keySet()) {
                    if (k instanceof Number) {
                        max = Math.max(max, ((Number) k).intValue());
                    }
                }
                List<Object> row = new ArrayList<>();
                for (int i = 0; i <= max; i++) {
                    row.add(map.get(i));
                }
                result.add(row);
            } else if (obj instanceof List) {
                result.add(new ArrayList<>((List<?>) obj));
            }
        }
        return result;
    }

    private boolean isRowEmpty(List<Object> row) {
        if (row == null) return true;
        for (Object o : row) {
            if (o != null && !String.valueOf(o).trim().isEmpty()) return false;
        }
        return true;
    }

    @Override
    @Transactional
    public void deleteRule(Long id) {
        removeById(id);
    }

    @Override
    @Transactional
    public void toggleStatus(Long id) {
        InventoryWarningRule rule = getById(id);
        if (rule == null) {
            return;
        }
        rule.setStatus(rule.getStatus() == 1 ? 0 : 1);
        updateById(rule);
    }

    @Override
    @Transactional
    public void checkWarning() {
        QueryWrapper<InventoryWarningRule> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1);
        List<InventoryWarningRule> rules = list(wrapper);

        if (rules.isEmpty()) {
            return;
        }

        List<InventoryWarningLog> newLogs = new ArrayList<>();

        for (InventoryWarningRule rule : rules) {
            List<Inventory> inventories;
            if (rule.getWarehouseId() != null) {
                inventories = inventoryService.getByMaterialAndStock(rule.getMaterialId(), rule.getWarehouseId());
            } else {
                QueryWrapper<Inventory> invWrapper = new QueryWrapper<>();
                invWrapper.eq("material_id", rule.getMaterialId());
                inventories = inventoryService.list(invWrapper);
            }

            if (inventories.isEmpty()) {
                continue;
            }

            Map<Long, List<Inventory>> warehouseInventoryMap = inventories.stream()
                    .collect(Collectors.groupingBy(Inventory::getStockId));

            for (Map.Entry<Long, List<Inventory>> entry : warehouseInventoryMap.entrySet()) {
                Long warehouseId = entry.getKey();
                List<Inventory> warehouseInventories = entry.getValue();

                BigDecimal totalQty = warehouseInventories.stream()
                        .map(inv -> inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                String warningType = null;
                String warningLevel = "一般";

                // 预警类型内置"双向"：上下限各自独立判断——填了（>0）才按该方向预警；填 0 或留空 = 该方向不预警
                boolean hasMin = rule.getMinQuantity() != null && rule.getMinQuantity().compareTo(BigDecimal.ZERO) > 0;
                boolean hasMax = rule.getMaxQuantity() != null && rule.getMaxQuantity().compareTo(BigDecimal.ZERO) > 0;

                if (hasMin && totalQty.compareTo(rule.getMinQuantity()) < 0) {
                    warningType = "MIN";
                    BigDecimal diff = rule.getMinQuantity().subtract(totalQty);
                    BigDecimal ratio = diff.divide(rule.getMinQuantity(), 2, RoundingMode.HALF_UP);
                    if (ratio.compareTo(new BigDecimal("0.5")) >= 0) {
                        warningLevel = "严重";
                    }
                }

                if (warningType == null && hasMax && totalQty.compareTo(rule.getMaxQuantity()) > 0) {
                    warningType = "MAX";
                    BigDecimal diff = totalQty.subtract(rule.getMaxQuantity());
                    BigDecimal ratio = diff.divide(rule.getMaxQuantity(), 2, RoundingMode.HALF_UP);
                    if (ratio.compareTo(new BigDecimal("0.5")) >= 0) {
                        warningLevel = "严重";
                    }
                }

                if (warningType != null) {
                    QueryWrapper<InventoryWarningLog> existWrapper = new QueryWrapper<>();
                    existWrapper.eq("rule_id", rule.getId());
                    existWrapper.eq("material_id", rule.getMaterialId());
                    existWrapper.eq("warehouse_id", warehouseId);
                    existWrapper.eq("is_handled", 0);
                    InventoryWarningLog existLog = warningLogMapper.selectOne(existWrapper);

                    if (existLog == null) {
                        InventoryWarningLog logEntity = new InventoryWarningLog();
                        logEntity.setRuleId(rule.getId());
                        logEntity.setMaterialId(rule.getMaterialId());
                        logEntity.setMaterialName(rule.getMaterialName());
                        logEntity.setWarehouseId(warehouseId);
                        logEntity.setWarehouseName(rule.getWarehouseName());
                        logEntity.setCurrentQty(totalQty);
                        logEntity.setWarningType(warningType);
                        logEntity.setWarningLevel(warningLevel);
                        logEntity.setIsHandled(0);
                        newLogs.add(logEntity);
                    }
                }
            }
        }

        for (InventoryWarningLog logEntity : newLogs) {
            warningLogMapper.insert(logEntity);
        }

        log.info("库存预警检查完成，生成 {} 条预警记录", newLogs.size());
    }

    @Override
    public PageResult<InventoryWarningLog> getWarningList(int current, int size, Integer isHandled) {
        QueryWrapper<InventoryWarningLog> wrapper = new QueryWrapper<>();
        if (isHandled != null) {
            wrapper.eq("is_handled", isHandled);
        }
        // 预警记录由系统自动生成、创建人字段恒为空，故不套数据权限（否则"仅本人/本部门"范围将查不到任何预警记录）
        wrapper.orderByDesc("create_time");
        Page<InventoryWarningLog> page = new Page<>(current, size);
        Page<InventoryWarningLog> result = warningLogMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    @Transactional
    public void handleWarning(Long id, String handleRemark) {
        InventoryWarningLog log = warningLogMapper.selectById(id);
        if (log == null) {
            return;
        }
        log.setIsHandled(1);
        log.setHandleRemark(handleRemark);
        log.setHandledAt(LocalDateTime.now());
        warningLogMapper.updateById(log);
    }

    /** 预警记录统计条件（含统一数据权限：与 /log 列表同口径） */
    private QueryWrapper<InventoryWarningLog> logCountWrapper(Integer isHandled, String warningType, String warningLevel) {
        QueryWrapper<InventoryWarningLog> w = new QueryWrapper<>();
        if (isHandled != null) {
            w.eq("is_handled", isHandled);
        }
        if (warningType != null) {
            w.eq("warning_type", warningType);
        }
        if (warningLevel != null) {
            w.eq("warning_level", warningLevel);
        }
        // 预警记录由系统自动生成、创建人字段恒为空，故不套数据权限（否则"仅本人/本部门"范围将查不到任何预警记录）
        return w;
    }

    @Override
    public Map<String, Object> getWarningStatistics() {
        Map<String, Object> result = new HashMap<>();

        Long pendingCount = warningLogMapper.selectCount(logCountWrapper(0, null, null));
        Long handledCount = warningLogMapper.selectCount(logCountWrapper(1, null, null));
        Long minCount = warningLogMapper.selectCount(logCountWrapper(0, "MIN", null));
        Long maxCount = warningLogMapper.selectCount(logCountWrapper(0, "MAX", null));
        Long seriousCount = warningLogMapper.selectCount(logCountWrapper(0, null, "严重"));

        result.put("pendingCount", pendingCount);
        result.put("handledCount", handledCount);
        result.put("minWarningCount", minCount);
        result.put("maxWarningCount", maxCount);
        result.put("seriousCount", seriousCount);

        return result;
    }
}
