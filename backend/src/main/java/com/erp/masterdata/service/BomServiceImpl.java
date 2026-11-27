package com.erp.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.BillRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import com.erp.config.BusinessException;
import com.erp.masterdata.entity.Bom;
import com.erp.masterdata.entity.BomItem;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.BomItemMapper;
import com.erp.masterdata.mapper.BomMapper;
import com.erp.masterdata.mapper.MaterialMapper;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.serialno.support.SegmentCodeProbe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.CustomFieldSearchHelper;
import com.erp.common.util.SortHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BomServiceImpl extends AbstractBillService<BomMapper, Bom> implements BomService {

    private final BomItemMapper bomItemMapper;
    private final MaterialMapper materialMapper;
    private final SysSerialNoConfigService serialNoService;

    /** 统一数据权限解析器：按角色 data_scope（仅本人/本部门/全部数据）过滤，创建人列 created_by */
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 下游引用校验（统一状态机）：重置草稿/作废/删除前禁止存在已生效下游 */
    @Lazy
    @Autowired
    private BillRelationService billRelationService;

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "BOM"; }
    @Override protected String getApprovalBillType() { return "BOM"; }
    @Override protected String getBillNo(Bom b) { return b.getBomCode(); }
    @Override protected Object getId(Bom b) { return b.getBomId(); }
    @Override protected String getStatus(Bom b) { return b.getStatus(); }
    @Override protected void setStatus(Bom b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(Bom b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(Bom b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(Bom b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(Bom b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(Bom b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(Bom b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(Bom b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(Bom b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(Bom b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(Bom b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(Bom b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(Bom b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(Bom b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(Bom bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getBomCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBomWithItems(Bom bom, List<BomItem> items) {
        boolean isNew = bom.getBomId() == null;

        if (bom.getBomCode() == null || bom.getBomCode().isEmpty()) {
            // 设置中心已关闭自动生成时，要求手动输入编码，不再兜底自动生成
            if (!serialNoService.isAutoGenerate("MD_BOM")) {
                throw new BusinessException("BOM已关闭自动编号，请手动输入编码");
            }
            // generateBomCode() 走 generateOrDefault：关闭自动编号/手动录号期间的单号会以业务表真实流水续号，杜绝重号
            bom.setBomCode(generateBomCode());
        }

        if (isNew) {
            if (baseMapper.exists(new QueryWrapper<Bom>().eq("bom_code", bom.getBomCode()))) {
                throw new BusinessException("BOM编码已存在: " + bom.getBomCode());
            }
        } else {
            if (baseMapper.exists(new QueryWrapper<Bom>()
                    .eq("bom_code", bom.getBomCode()).ne("bom_id", bom.getBomId()))) {
                throw new BusinessException("BOM编码已存在: " + bom.getBomCode());
            }
        }
        // 保存=草稿（统一状态机）：新建一律草稿；编辑保持库里原状态，不接受前端回传的状态值
        if (isNew) {
            bom.setStatus("草稿");
            bom.setApprovalStatus("草稿");
        } else {
            Bom existing = baseMapper.selectById(bom.getBomId());
            bom.setStatus(existing != null ? existing.getStatus() : "草稿");
            bom.setApprovalStatus(existing != null ? existing.getApprovalStatus() : "草稿");
        }
        if (bom.getBomType() == null || bom.getBomType().isEmpty()) {
            bom.setBomType("标准BOM");
        }
        if (bom.getIsPrimary() == null) {
            bom.setIsPrimary((short) 1);
        }
        if (bom.getIsEnabled() == null) {
            bom.setIsEnabled((short) 1);
        }
        if (bom.getQuantity() == null) {
            bom.setQuantity(BigDecimal.ONE);
        }
        // 默认BOM/主BOM：同物料下若无默认/主BOM，则设为1；否则设为0确保唯一
        if (bom.getIsDefault() == null) {
            boolean hasDefault = baseMapper.exists(new QueryWrapper<Bom>()
                    .eq("material_id", bom.getMaterialId())
                    .eq("is_default", (short) 1)
                    .ne(bom.getBomId() != null, "bom_id", bom.getBomId()));
            bom.setIsDefault(hasDefault ? (short) 0 : (short) 1);
        }
        if (bom.getIsPrimary() == null) {
            boolean hasPrimary = baseMapper.exists(new QueryWrapper<Bom>()
                    .eq("material_id", bom.getMaterialId())
                    .eq("is_primary", (short) 1)
                    .ne(bom.getBomId() != null, "bom_id", bom.getBomId()));
            bom.setIsPrimary(hasPrimary ? (short) 0 : (short) 1);
        }
        if (bom.getIsEnabled() == null) {
            bom.setIsEnabled((short) 1);
        }
        if (bom.getMaterialId() != null) {
            Material material = materialMapper.selectById(bom.getMaterialId());
            if (material != null) {
                if (bom.getMaterialCode() == null || bom.getMaterialCode().isEmpty()) {
                    bom.setMaterialCode(material.getMaterialCode());
                }
                if (bom.getMaterialName() == null || bom.getMaterialName().isEmpty()) {
                    bom.setMaterialName(material.getMaterialName());
                }
            }
        }

        if (isNew) {
            save(bom);
        } else {
            updateById(bom);
        }

        // 循环 insert items, lineNo 从 10 递增 10
        if (items != null && !items.isEmpty()) {
            // items 为 null 时不删除既有明细，避免未携带明细的更新把表体清空
            bomItemMapper.delete(new QueryWrapper<BomItem>().eq("bom_id", bom.getBomId()));
            int lineNo = 10;
            for (BomItem item : items) {
                item.setBomItemId(null);
                item.setBomId(bom.getBomId());
                item.setLineNo(lineNo);

                // 通过 materialId 查 Material 拿 materialCode/materialName/specification(用Material的model字段)
                if (item.getMaterialId() != null) {
                    Material m = materialMapper.selectById(item.getMaterialId());
                    if (m != null) {
                        if (item.getMaterialCode() == null || item.getMaterialCode().isEmpty()) {
                            item.setMaterialCode(m.getMaterialCode());
                        }
                        if (item.getMaterialName() == null || item.getMaterialName().isEmpty()) {
                            item.setMaterialName(m.getMaterialName());
                        }
                        if (item.getSpecification() == null || item.getSpecification().isEmpty()) {
                            item.setSpecification(m.getModel());
                        }
                        if (item.getModelNo() == null || item.getModelNo().isEmpty()) {
                            item.setModelNo(m.getModel());
                        }
                        if (item.getSpecProperty() == null || item.getSpecProperty().isEmpty()) {
                            // Material暂未提供specProperty时跳过
                        }
                        if (item.getImageUrl() == null || item.getImageUrl().isEmpty()) {
                            // Material暂未提供imageUrl时跳过
                        }
                    }
                }
                if (item.getIsOptional() == null) {
                    item.setIsOptional((short) 0);
                }
                if (item.getQuantity() == null) {
                    item.setQuantity(BigDecimal.ZERO);
                }
                if (item.getQtyType() == null || item.getQtyType().isEmpty()) {
                    item.setQtyType("固定");
                }
                if (item.getIsKeyPart() == null) {
                    item.setIsKeyPart((short) 0);
                }
                if (item.getSupplyType() == null || item.getSupplyType().isEmpty()) {
                    item.setSupplyType("领料");
                }

                bomItemMapper.insert(item);
                lineNo += 10;
            }
        }
    }

    /** 提交审核：状态校验 / 严格校验 / 审批开关 / 状态迁移全部由统一状态机执行 */
    @Override
    @Transactional
    public void submit(Long id, String operator) {
        Bom bom = getById(id);
        if (bom == null) throw new BusinessException("BOM不存在");
        super.submitForApproval(bom, operator);
    }

    /** 提交前严格校验：母件物料必须存在（BOM 展开/取料都以母件为入口） */
    @Override
    protected void validateStrictSubmit(Bom bom) {
        if (bom.getMaterialId() == null) {
            throw new BusinessException("母件物料为空，请选择母件后再提交");
        }
        if (materialMapper.selectById(bom.getMaterialId()) == null) {
            throw new BusinessException("关联母件物料不存在(materialId=" + bom.getMaterialId() + ")，请重新选择母件");
        }
    }

    /** 审核：审批引擎防绕过 + 状态迁移由统一状态机执行 */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("BOM", (Long) id) != null) {
            throw new BusinessException("BOM已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 驳回：待审核 → 草稿（审批引擎防绕过 + 状态校验由统一状态机执行） */
    @Override
    @Transactional
    public void reject(Long id, String operator) {
        super.reject(id, operator, null);
    }

    /** 审核通过影响：BOM 置「已生效」后即可被 MRP/展开按「已生效」取用（取用规则见 findActiveBom） */
    @Override
    public void onApproved(Bom bom) {
        log.info("BOM审核生效: {} 审核人: {}", bom.getBomCode(), bom.getApprovedBy());
    }

    @Override
    @Transactional
    public void disable(Long id, String operator) {
        Bom bom = getById(id);
        if (bom == null) throw new BusinessException("BOM不存在");
        if (!"已生效".equals(bom.getStatus())) {
            throw new BusinessException("只有已生效状态可停用, 当前: " + bom.getStatus());
        }
        bom.setStatus("已停用");
        bom.setIsEnabled((short) 0);
        bom.setIsDefault((short) 0);
        bom.setUpdatedBy(operator);
        updateById(bom);
        log.info("BOM停用: {} 操作人: {}", bom.getBomCode(), operator);
    }

    @Override
    @Transactional
    public void enable(Long id, String operator) {
        Bom bom = getById(id);
        if (bom == null) throw new BusinessException("BOM不存在");
        if (!"已停用".equals(bom.getStatus())) {
            throw new BusinessException("只有已停用状态可启用, 当前: " + bom.getStatus());
        }
        bom.setStatus("已生效");
        bom.setIsEnabled((short) 1);
        bom.setUpdatedBy(operator);
        updateById(bom);
        log.info("BOM启用: {} 操作人: {}", bom.getBomCode(), operator);
    }

    @Override
    @Transactional
    public void setDefault(Long id, String operator) {
        Bom bom = getById(id);
        if (bom == null) throw new BusinessException("BOM不存在");
        if (!"已生效".equals(bom.getStatus())) {
            throw new BusinessException("只有已生效的BOM可设为默认, 当前: " + bom.getStatus());
        }
        // 同一物料下其他BOM的is_default置为0
        bomItemMapper.selectList(new QueryWrapper<BomItem>().eq("bom_id", id));
        Bom updateOther = new Bom();
        updateOther.setIsDefault((short) 0);
        update(updateOther, new QueryWrapper<Bom>()
                .eq("material_id", bom.getMaterialId())
                .ne("bom_id", id));
        // 当前BOM设为默认
        bom.setIsDefault((short) 1);
        bom.setUpdatedBy(operator);
        updateById(bom);
        log.info("BOM设为默认: {} 物料: {} 操作人: {}", bom.getBomCode(), bom.getMaterialCode(), operator);
    }

    @Override
    @Transactional
    public int batchSubmit(List<Long> ids, String operator) {
        if (ids == null || ids.isEmpty()) return 0;
        int count = 0;
        for (Long id : ids) {
            try {
                submit(id, operator);
                count++;
            } catch (BusinessException e) {
                log.warn("BOM批量提交跳过 id={}: {}", id, e.getMessage());
            }
        }
        return count;
    }

    @Override
    @Transactional
    public int batchApprove(List<Long> ids, String operator) {
        if (ids == null || ids.isEmpty()) return 0;
        int count = 0;
        for (Long id : ids) {
            try {
                approve(id, operator);
                count++;
            } catch (BusinessException e) {
                log.warn("BOM批量审核跳过 id={}: {}", id, e.getMessage());
            }
        }
        return count;
    }

    @Override
    @Transactional
    public int batchUpdateStatus(List<Long> ids, String status, String operator) {
        if (ids == null || ids.isEmpty()) return 0;
        Bom update = new Bom();
        if ("启用".equals(status)) {
            update.setIsEnabled((short) 1);
        } else if ("禁用".equals(status)) {
            update.setIsEnabled((short) 0);
        }
        update.setUpdatedBy(operator);
        boolean affected = update(update, new QueryWrapper<Bom>().in("bom_id", ids));
        return affected ? ids.size() : 0;
    }

    @Override
    @Transactional
    public int updateScrapRate(Long id, BigDecimal scrapRate, String operator) {
        if (id == null || scrapRate == null) return 0;
        Bom bom = getById(id);
        if (bom == null) throw new BusinessException("BOM不存在");
        // 更新表头损耗率
        bom.setScrapRate(scrapRate);
        bom.setUpdatedBy(operator);
        updateById(bom);
        // 同步更新所有子项的损耗率
        BomItem itemUpdate = new BomItem();
        itemUpdate.setScrapRate(scrapRate);
        itemUpdate.setUpdatedBy(operator);
        int count = bomItemMapper.update(itemUpdate,
                new QueryWrapper<BomItem>().eq("bom_id", id));
        log.info("BOM更新损耗率: {} 损耗率: {} 同步子项: {} 行", bom.getBomCode(), scrapRate, count);
        return count;
    }

    @Override
    public PageResult<Bom> pageQuery(int current, int size, String keyword,
                                     String bomCategory, String status, String isEnabled,
                                     LocalDate dateFrom, LocalDate dateTo, String sortBy, String sortOrder, String customSearch) {
        QueryWrapper<Bom> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like("bom_code", keyword)
                    .or().like("material_code", keyword)
                    .or().like("material_name", keyword));
        }
        if (bomCategory != null && !bomCategory.isEmpty()) {
            wrapper.eq("bom_category", bomCategory);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        if (isEnabled != null && !isEnabled.isEmpty()) {
            wrapper.eq("is_enabled", Integer.parseInt(isEnabled));
        }
        if (dateFrom != null) {
            wrapper.ge("bom_date", dateFrom);
        }
        if (dateTo != null) {
            wrapper.le("bom_date", dateTo);
        }
        // 数据权限（按角色 data_scope：仅本人/本部门/全部数据）—— 创建人列 created_by，total 随过滤变化
        dataScopeResolver.applyIfLimited(wrapper, null, "created_by");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "bom", customSearch, CustomFieldSearchHelper.idColumnOf("bom"));

        // 列表默认排序统一口径「最新在前」：BOM 列表此前无任何默认排序 → 新单不保证第 1 行（2026-09-20 端到端闭环实测）
        SortHelper.orderByNewest(wrapper, Bom.class);
        Page<Bom> page = new Page<>(current, size);
        Page<Bom> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 兼容旧方法签名（4参）
     */
    public PageResult<Bom> pageQuery(int current, int size, String keyword, String status) {
        return pageQuery(current, size, keyword, null, status, null, null, null, null, null, null);
    }

    @Override
    public Bom getDetail(Long id) {
        Bom bom = getById(id);
        if (bom == null) {
            throw new RuntimeException("BOM不存在: " + id);
        }
        List<BomItem> items = bomItemMapper.selectList(
                new QueryWrapper<BomItem>().eq("bom_id", id).orderByAsc("line_no"));
        bom.setItems(items);
        return bom;
    }

    @Override
    public Map<String, Object> expandBom(String materialCode, BigDecimal quantity) {
        return expandBomRecursive(materialCode, quantity, 1, new java.util.HashSet<>(), null);
    }

    @Override
    public Map<String, Object> expandBomByBomId(Long bomId, BigDecimal quantity) {
        Map<String, Object> empty = new HashMap<>();
        if (bomId == null) {
            return empty;
        }
        // 数据权限：与 BOM 列表同口径（创建人列 created_by）
        QueryWrapper<Bom> bomWrapper = new QueryWrapper<Bom>().eq("bom_id", bomId);
        dataScopeResolver.applyIfLimited(bomWrapper, null, "created_by");
        Bom bom = getOne(bomWrapper);
        if (bom == null || bom.getMaterialId() == null) {
            return empty;
        }
        Material parent = materialMapper.selectById(bom.getMaterialId());
        if (parent == null) {
            return empty;
        }
        return expandBomRecursive(parent.getMaterialCode(), quantity, 1, new java.util.HashSet<>(), bom);
    }

    /**
     * 查找物料生效BOM，优先级：默认BOM(is_default=1) → 主BOM(is_primary=1) → 最新已生效BOM → 最新BOM
     */
    private Bom findActiveBom(Long materialId) {
        if (materialId == null) return null;
        Bom bom = selectFirstBom(new QueryWrapper<Bom>()
                .eq("material_id", materialId)
                .eq("is_default", (short) 1));
        if (bom == null) {
            bom = selectFirstBom(new QueryWrapper<Bom>()
                    .eq("material_id", materialId)
                    .eq("is_primary", (short) 1));
        }
        if (bom == null) {
            bom = selectFirstBom(new QueryWrapper<Bom>()
                    .eq("material_id", materialId)
                    .eq("status", "已生效"));
        }
        if (bom == null) {
            bom = selectFirstBom(new QueryWrapper<Bom>()
                    .eq("material_id", materialId));
        }
        return bom;
    }

    /** 取最新一条 BOM 并套统一数据权限（创建人列 created_by）——BOM 展开/扁平化与列表同口径 */
    private Bom selectFirstBom(QueryWrapper<Bom> wrapper) {
        dataScopeResolver.applyIfLimited(wrapper, null, "created_by");
        return getOne(wrapper.orderByDesc("bom_id").last("LIMIT 1"));
    }

    /** BOM展开最大层级，防止循环引用导致栈溢出 */
    private static final int MAX_BOM_LEVEL = 15;

    private Map<String, Object> expandBomRecursive(String materialCode, BigDecimal quantity,
                                                   int level, java.util.Set<String> visitedPath, Bom overrideBom) {
        Map<String, Object> result = new HashMap<>();
        if (materialCode == null || level > MAX_BOM_LEVEL || visitedPath.contains(materialCode)) {
            return result;
        }

        Material parent = materialMapper.selectOne(
                new QueryWrapper<Material>().eq("material_code", materialCode).last("LIMIT 1"));
        if (parent == null) {
            return result;
        }

        // 顶层调用可指定BOM（列表页展开当前行BOM），其余层级按优先级自动查找
        Bom bom = (level == 1 && overrideBom != null) ? overrideBom : findActiveBom(parent.getMaterialId());

        result.put("materialCode", parent.getMaterialCode());
        result.put("materialName", parent.getMaterialName());
        result.put("specification", parent.getModel());
        result.put("unit", parent.getBaseUnitId() != null ? parent.getBaseUnitId().toString() : "");
        result.put("quantity", quantity);
        result.put("level", level);

        List<Map<String, Object>> children = new ArrayList<>();
        if (bom != null) {
            result.put("bomCode", bom.getBomCode());
            visitedPath.add(materialCode);
            List<BomItem> bomItems = bomItemMapper.selectList(
                    new QueryWrapper<BomItem>().eq("bom_id", bom.getBomId()).orderByAsc("line_no"));
            for (BomItem bomItem : bomItems) {
                Material child = materialMapper.selectById(bomItem.getMaterialId());
                if (child == null) {
                    continue;
                }

                BigDecimal childQty = quantity.multiply(bomItem.getQuantity()).setScale(4, RoundingMode.HALF_UP);

                Map<String, Object> childNode = new HashMap<>();
                childNode.put("materialCode", child.getMaterialCode());
                childNode.put("materialName", child.getMaterialName());
                childNode.put("specification", child.getModel());
                childNode.put("unit", child.getBaseUnitId() != null ? child.getBaseUnitId().toString() : "");
                childNode.put("usageQuantity", bomItem.getQuantity());
                childNode.put("totalQuantity", childQty);
                childNode.put("level", level + 1);

                // 子件自身的BOM编号（半成品下级结构入口）
                Bom childBom = findActiveBom(child.getMaterialId());
                if (childBom != null) {
                    childNode.put("childBomCode", childBom.getBomCode());
                }

                Map<String, Object> subBom = expandBomRecursive(child.getMaterialCode(), childQty,
                        level + 1, visitedPath, null);
                if (!subBom.isEmpty() && subBom.get("children") != null
                        && !((List<?>) subBom.get("children")).isEmpty()) {
                    childNode.put("children", subBom.get("children"));
                }

                children.add(childNode);
            }
            visitedPath.remove(materialCode);
        }

        result.put("children", children);
        return result;
    }

    @Override
    public List<Map<String, Object>> flattenBom(String materialCode, BigDecimal quantity) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (materialCode == null) {
            return result;
        }

        Map<String, BigDecimal> materialQuantities = new LinkedHashMap<>();
        Map<String, Map<String, Object>> materialDetails = new HashMap<>();

        flattenBomRecursive(materialCode, quantity, materialQuantities, materialDetails, new java.util.HashSet<>());

        for (Map.Entry<String, BigDecimal> entry : materialQuantities.entrySet()) {
            Map<String, Object> item = new HashMap<>(materialDetails.get(entry.getKey()));
            item.put("totalQuantity", entry.getValue());
            result.add(item);
        }

        return result;
    }

    private void flattenBomRecursive(String materialCode, BigDecimal quantity,
                                     Map<String, BigDecimal> materialQuantities,
                                     Map<String, Map<String, Object>> materialDetails,
                                     java.util.Set<String> visitedPath) {
        if (materialCode == null || visitedPath.contains(materialCode)) {
            return;
        }
        Material parent = materialMapper.selectOne(
                new QueryWrapper<Material>().eq("material_code", materialCode).last("LIMIT 1"));
        if (parent == null) {
            return;
        }

        Bom bom = findActiveBom(parent.getMaterialId());
        if (bom == null) {
            return;
        }
        visitedPath.add(materialCode);

        List<BomItem> bomItems = bomItemMapper.selectList(
                new QueryWrapper<BomItem>().eq("bom_id", bom.getBomId()));

        for (BomItem bomItem : bomItems) {
            Material child = materialMapper.selectById(bomItem.getMaterialId());
            if (child == null) {
                continue;
            }

            BigDecimal childQty = quantity.multiply(bomItem.getQuantity()).setScale(4, RoundingMode.HALF_UP);
            String key = child.getMaterialCode();
            materialQuantities.merge(key, childQty, BigDecimal::add);

            if (!materialDetails.containsKey(key)) {
                Map<String, Object> details = new HashMap<>();
                details.put("materialCode", child.getMaterialCode());
                details.put("materialName", child.getMaterialName());
                details.put("specification", child.getModel());
                details.put("unit", child.getBaseUnitId() != null ? child.getBaseUnitId().toString() : "");
                details.put("purchasePrice", child.getPurchasePrice());
                details.put("salePrice", child.getSalePrice());
                details.put("leadTimeDays", 7);
                materialDetails.put(key, details);
            }

            flattenBomRecursive(child.getMaterialCode(), childQty, materialQuantities, materialDetails, visitedPath);
        }
    }

    @Override
    public String generateBomCode() {
        String docType = "MD_BOM";
        // 三参重载：既保留"未配置/异常时回退本地规则"的兜底语义，又对已配置规则叠加"以业务表实况取号（补最小空洞）"
        return serialNoService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "BOM-" + dateStr + "-";
            QueryWrapper<Bom> wrapper = new QueryWrapper<>();
            wrapper.likeRight("bom_code", prefix).orderByDesc("bom_code").last("LIMIT 1");
            Bom last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getBomCode() != null) {
                try {
                    seq = Integer.parseInt(last.getBomCode().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        }, SegmentCodeProbe.of(baseMapper, "bom_code"));
    }

    /** 重置草稿：状态守卫保留，状态迁移/下游校验由统一状态机执行 */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        Bom bom = getById(id);
        if (bom == null) throw new BusinessException("BOM不存在");
        if (!"已生效".equals(bom.getStatus())) {
            throw new BusinessException("只有已生效状态可重置草稿，当前: " + bom.getStatus());
        }
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        update(new UpdateWrapper<Bom>().eq("bom_id", id)
                .set("approved_by", null)
                .set("approved_at", null)
                .set("updated_by", operator)
                .set("updated_at", LocalDateTime.now()));
        log.info("重置草稿BOM: {} 操作人: {}", bom.getBomCode(), operator);
    }

    /** 作废：状态校验/下游校验/作废前状态记录由统一状态机执行 */
    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        super.voidBill(id, operator, reason);
    }

    /** 反作废：恢复作废前状态，不重复释放业务影响 */
    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }
}
