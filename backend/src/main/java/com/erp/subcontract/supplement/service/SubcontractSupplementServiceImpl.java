package com.erp.subcontract.supplement.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.BillApprovalAdapter;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillStatusUtils;
import org.springframework.beans.factory.annotation.Autowired;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.basedata.entity.Warehouse;
import com.erp.inventory.service.InventoryService;
import com.erp.inventory.service.WarehouseService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import com.erp.masterdata.service.SupplierService;
import com.erp.config.service.SysConfigService;
import com.erp.subcontract.supplement.entity.SubcontractSupplement;
import com.erp.subcontract.supplement.entity.SubcontractSupplementItem;
import com.erp.subcontract.supplement.mapper.SubcontractSupplementItemMapper;
import com.erp.subcontract.supplement.mapper.SubcontractSupplementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;
import com.erp.common.util.CustomFieldSearchHelper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class SubcontractSupplementServiceImpl extends AbstractBillService<SubcontractSupplementMapper, SubcontractSupplement>
        implements SubcontractSupplementService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义全面推广 · 委外模块批次）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键）；值 = 实体属性名（防手写列名笔误）。
        // 供应商：按 ID 精确（前端下拉传主档 ID），Spec.column 写外键属性名 supplierId。
        // 状态列是数字（0 草稿/1 待审核/2 已生效）→ EQ_LONG（EQ_STRING 拿字符串比 integer 列会报类型错）。
        java.util.Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        f.put("supplementNo", new BillHeaderFilterSupport.Spec("supplementNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("orderNo", new BillHeaderFilterSupport.Spec("orderNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("pickNo", new BillHeaderFilterSupport.Spec("pickNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("reason", new BillHeaderFilterSupport.Spec("reason", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillType", new BillHeaderFilterSupport.Spec("sourceBillType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("supplierName", new BillHeaderFilterSupport.Spec("supplierId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("supplementDate", new BillHeaderFilterSupport.Spec("supplementDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("totalQuantity", new BillHeaderFilterSupport.Spec("totalQuantity", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("subcontractSupplement", SubcontractSupplement.class, f);
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "create_by",
            "create_time",
            "created_at",
            "id",
            "order_id",
            "order_no",
            "reason",
            "remark",
            "status",
            "supplement_date",
            "supplement_id",
            "supplement_no",
            "supplier_id",
            "supplier_name",
            "total_quantity",
            "update_time",
            "void_reason",
            "voided_at",
            "voided_by"
        );

    private final SubcontractSupplementItemMapper itemMapper;
    private final InventoryService inventoryService;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final SupplierService supplierService;
    private final MaterialService materialService;
    private final WarehouseService warehouseService;
    private final SysConfigService sysConfigService;

    /** 统一数据权限解析件：按角色 sys_role.data_scope（仅本人/本部门/全部）过滤 */
    @Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 下游单据扫描件：作废/重置草稿/删除前的"下游已生效单据"校验 */
    @Autowired
    private BillRelationService billRelationService;

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "委外补料单"; }
    @Override protected String getApprovalBillType() { return "SUBCONTRACT_SUPPLEMENT"; }
    @Override protected String getBillNo(SubcontractSupplement b) { return b.getSupplementNo(); }
    @Override protected Object getId(SubcontractSupplement b) { return b.getId(); }
    @Override protected String getStatus(SubcontractSupplement b) { return BillStatusUtils.toString(b.getStatus()); }
    @Override protected void setStatus(SubcontractSupplement b, String s) { b.setStatus(BillStatusUtils.toNumber(s)); }
    @Override protected Integer getCloseStatus(SubcontractSupplement b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(SubcontractSupplement b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(SubcontractSupplement b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(SubcontractSupplement b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(SubcontractSupplement b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(SubcontractSupplement b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(SubcontractSupplement b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(SubcontractSupplement b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(SubcontractSupplement b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(SubcontractSupplement b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(SubcontractSupplement b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(SubcontractSupplement b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(SubcontractSupplement b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(SubcontractSupplement bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getSupplementNo());
    }

    @Override
    @Transactional
    public void saveSupplementWithItems(SubcontractSupplement supplement, List<SubcontractSupplementItem> items) {
        boolean isNew = supplement.getId() == null;

        if (!isNew) {
            SubcontractSupplement existing = getById(supplement.getId());
            if (existing != null) {
                Integer st = existing.getStatus();
                if (st != null && (st == 2 || st == 3)) {
                    throw new BusinessException("当前状态不可修改，如需修改请先重置草稿");
                }
            }
        }

        // 单号只在新建时生成：更新时不重新取号（否则每次保存都会吃掉一个序号，页面显示与实际不一致）
        if (isNew && (supplement.getSupplementNo() == null || supplement.getSupplementNo().isBlank())) {
            supplement.setSupplementNo(generateSupplementNo());
        }

        if (supplement.getStatus() == null) supplement.setStatus(0);
        // 新建草稿时同步 approval_status，避免该列留空（与全系统审批口径一致）
        if (supplement.getStatus() != null && supplement.getStatus() == 0 && supplement.getApprovalStatus() == null) {
            supplement.setApprovalStatus("草稿");
        }
        if (supplement.getCloseStatus() == null) supplement.setCloseStatus(0);
        if (supplement.getTotalQuantity() == null) supplement.setTotalQuantity(BigDecimal.ZERO);
        if (supplement.getSupplementDate() == null) supplement.setSupplementDate(LocalDate.now());

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<SubcontractSupplementItem> validItems = new ArrayList<>();
            for (SubcontractSupplementItem it : items) { if (it != null && it.getMaterialId() != null) validItems.add(it); }
            items = validItems;
        }

        recalculateSupplement(supplement, items);

        if (isNew) {
            save(supplement);
        } else {
            updateById(supplement);
        }

        if (items != null) {
            // items 为 null 时不删除既有明细，避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<SubcontractSupplementItem>().eq("supplement_id", supplement.getId()));
            for (SubcontractSupplementItem item : items) {
                item.setId(null);
                item.setSupplementId(supplement.getId());
                itemMapper.insert(item);
            }
        }

        log.info("委外补料单保存成功: {} 状态: {}", supplement.getSupplementNo(), supplement.getStatus());
    }

    private void recalculateSupplement(SubcontractSupplement supplement, List<SubcontractSupplementItem> items) {
        BigDecimal totalQuantity = BigDecimal.ZERO;
        if (items != null) {
            for (SubcontractSupplementItem item : items) {
                totalQuantity = totalQuantity.add(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO);
            }
        }
        supplement.setTotalQuantity(totalQuantity);
    }

    @Override
    public PageResult<SubcontractSupplement> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder) {
        // 老签名委托新重载（headerFilters 空 = 行为与改造前完全一致）
        return pageQuery(current, size, keyword, status, sortBy, sortOrder, null, java.util.Collections.emptyMap());
    }

    /** 老签名（仅表头字段过滤）委托新重载（customSearch 空 = 行为与改造前完全一致） */
    @Override
    public PageResult<SubcontractSupplement> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder,
                                                       java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, sortBy, sortOrder, null, headerFilters);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤 + 自定义字段搜索」（2026-10-14 / 2026-11-24）：
     * 白名单 + 参数化，非法/空白/白名单外入参在支持类内部忽略，不阻断列表查询。
     */
    @Override
    public PageResult<SubcontractSupplement> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder,
                                                       String customSearch, java.util.Map<String, String> headerFilters) {
        QueryWrapper<SubcontractSupplement> wrapper = new QueryWrapper<>();
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SubcontractSupplement.class);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("supplement_no", keyword);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        // 表头字段过滤（2026-10-14 筛选条件自定义）：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "subcontractSupplement", headerFilters);
        // 数据权限（统一机制）：按角色 data_scope（仅本人/本部门/全部）过滤；无 Web 请求/全部数据时不加条件
        dataScopeResolver.applyIfLimited(wrapper, null, "create_by");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "subcontract_supplement", customSearch, CustomFieldSearchHelper.idColumnOf("subcontract_supplement"));
        Page<SubcontractSupplement> page = new Page<>(current, size);
        Page<SubcontractSupplement> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SubcontractSupplement getDetail(Long id) {
        SubcontractSupplement supplement = getById(id);
        if (supplement == null) throw new BusinessException("委外补料单不存在");
        List<SubcontractSupplementItem> items = itemMapper.selectList(
                new QueryWrapper<SubcontractSupplementItem>().eq("supplement_id", id).orderByAsc("id"));
        supplement.setItems(items);
        return supplement;
    }

    // ============ 统一状态机动作（全部 delegate 基类） ============

    @Override
    @Transactional
    public void submitForApproval(Long id) {
        SubcontractSupplement supplement = getById(id);
        if (supplement == null) throw new BusinessException("委外补料单不存在");
        super.submitForApproval(supplement, BillApprovalAdapter.currentSubmitter());
    }

    /** 提交审核前的严格校验（原 submitForApproval 校验整体迁移；保存草稿不触发） */
    @Override
    protected void validateStrictSubmit(SubcontractSupplement supplement) {
        List<SubcontractSupplementItem> items = itemMapper.selectList(
                new QueryWrapper<SubcontractSupplementItem>().eq("supplement_id", supplement.getId()));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("没有明细，请至少添加一行物料后再提交");
        }

        if (supplement.getSupplierId() == null) {
            throw new BusinessException("请选择委外供应商");
        }
        if (supplierService.getById(supplement.getSupplierId()) == null) {
            throw new BusinessException("关联供应商不存在(supplierId=" + supplement.getSupplierId() + ")，请重新选择供应商");
        }

        boolean negativeAllowed = sysConfigService.getBooleanValue("inventory.negative.allowed", false);
        for (int i = 0; i < items.size(); i++) {
            SubcontractSupplementItem item = items.get(i);
            int lineNo = i + 1;
            if (item.getMaterialId() == null) {
                throw new BusinessException("第" + lineNo + "行物料ID不能为空");
            }
            Material mat = materialService.getById(item.getMaterialId());
            if (mat == null) {
                throw new BusinessException("第" + lineNo + "行物料不存在(materialId=" + item.getMaterialId() + ")，请重新选择物料");
            }
            if (item.getWarehouseId() == null) {
                throw new BusinessException("第" + lineNo + "行仓库ID不能为空");
            }
            Warehouse wh = warehouseService.getById(item.getWarehouseId());
            if (wh == null) {
                throw new BusinessException("第" + lineNo + "行仓库不存在(warehouseId=" + item.getWarehouseId() + ")，请重新选择仓库");
            }
            // 多单位：补料出库按「基本单位数量」校验库存（辅单位录入时行数量≠库存口径）
            BigDecimal qty = item.effectiveBaseQuantity() != null
                    ? item.effectiveBaseQuantity()
                    : (item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                String matLabel = (mat.getMaterialCode() != null ? mat.getMaterialCode() : "")
                        + " " + (mat.getMaterialName() != null ? mat.getMaterialName() : "");
                throw new BusinessException("第" + lineNo + "行[" + matLabel.trim() + "]数量必须大于0");
            }

            if (!negativeAllowed) {
                List<com.erp.inventory.entity.Inventory> invList = inventoryService.getByMaterialAndStock(item.getMaterialId(), item.getWarehouseId());
                BigDecimal available = BigDecimal.ZERO;
                for (com.erp.inventory.entity.Inventory inv : invList) {
                    if (inv != null && inv.getAvailableQuantity() != null) {
                        available = available.add(inv.getAvailableQuantity());
                    }
                }
                if (available.compareTo(qty) < 0) {
                    String matLabel = (mat.getMaterialCode() != null ? mat.getMaterialCode() : "")
                            + " " + (mat.getMaterialName() != null ? mat.getMaterialName() : "");
                    String whName = wh.getStockName() != null ? wh.getStockName() : "ID=" + item.getWarehouseId();
                    throw new BusinessException("第" + lineNo + "行物料[" + matLabel.trim() + "]库存不足，仓库["
                            + whName + "]可用 " + available + "，需补料 " + qty);
                }
            }
        }
    }

    /**
     * 覆盖基类 Object 签名：审批引擎回调在基类内部按 Object 签名调用，
     * 只有覆盖 Object 签名才能保证「绕过引擎直审」的防护对所有入口生效。
     */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("SUBCONTRACT_SUPPLEMENT", (Long) id) != null) {
            throw new BusinessException("委外补料单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    @Override
    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
    }

    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        SubcontractSupplement bill = getById(id);
        if (bill == null) throw new BusinessException("委外补料单不存在");
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        UpdateWrapper<SubcontractSupplement> uw = new UpdateWrapper<>();
        uw.eq("id", id).set("approved_by", null).set("approved_at", null);
        update(uw);
    }

    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        super.voidBill(id, operator, reason);
    }

    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }

    /**
     * 审核生效影响（统一状态机钩子）：生效即扣减库存并冻结成本；重置草稿按冻结成本退回。
     */
    @Override
    @Transactional
    public void onApproved(SubcontractSupplement supplement) {
        moveStock(supplement, supplement.getApprovedBy(), true);
    }

    /** 重置草稿影响：与 onApproved 严格镜像（按冻结成本把料退回库存） */
    @Override
    @Transactional
    public void onReverseApproved(SubcontractSupplement supplement) {
        moveStock(supplement, supplement.getApprovedBy(), false);
    }

    /** 补料库存动作：outbound=true 扣减，false 退回 */
    private void moveStock(SubcontractSupplement supplement, String operator, boolean outbound) {
        List<SubcontractSupplementItem> items = itemMapper.selectList(
                new QueryWrapper<SubcontractSupplementItem>().eq("supplement_id", supplement.getId()).orderByAsc("id"));
        if (items == null || items.isEmpty()) {
            return;
        }
        for (SubcontractSupplementItem item : items) {
            Long warehouseId = item.getWarehouseId();
            Long materialId = item.getMaterialId();
            BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            if (warehouseId == null || materialId == null) {
                continue;
            }
            // 库存记账只认基本单位数量
            BigDecimal stockQty = item.effectiveBaseQuantity() != null ? item.effectiveBaseQuantity() : quantity;
            if (outbound) {
                BigDecimal outUnitCost = inventoryService.getOutUnitCost(warehouseId, null, materialId, item.getBatchNo());
                inventoryService.deductStock(
                        warehouseId,
                        null,
                        materialId,
                        item.getBatchNo(),
                        stockQty,
                        supplement.getSupplementNo(),
                        "SUBCONTRACT_SUPPLEMENT",
                        "SubcontractSupplement",
                        supplement.getSupplementNo(),
                        operator);
                item.setAvgCost(outUnitCost);
                itemMapper.updateById(item);
            } else {
                BigDecimal reverseUnitCost = item.getAvgCost() != null && item.getAvgCost().compareTo(BigDecimal.ZERO) > 0
                        ? item.getAvgCost()
                        : BigDecimal.ZERO;
                inventoryService.addStock(
                        warehouseId,
                        null,
                        materialId,
                        item.getBatchNo(),
                        stockQty,
                        reverseUnitCost,
                        supplement.getSupplementNo(),
                        "SUBCONTRACT_SUPPLEMENT_REVERSE",
                        "SubcontractSupplement",
                        supplement.getSupplementNo(),
                        operator);
            }
        }
        log.info("委外补料单{}: {} 操作人: {}", outbound ? "扣减库存" : "退回库存", supplement.getSupplementNo(), operator);
    }

    @Override
    public String generateSupplementNo() {
        String docType = "SUBCONTRACT_SUPPLEMENT";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "SC-SUPP-" + dateStr + "-";
            QueryWrapper<SubcontractSupplement> wrapper = new QueryWrapper<>();
            wrapper.likeRight("supplement_no", prefix).orderByDesc("supplement_no").last("LIMIT 1");
            SubcontractSupplement last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getSupplementNo() != null) {
                try {
                    seq = Integer.parseInt(last.getSupplementNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    /** 删除（统一状态机）：桥接接口的 Long 签名，逻辑全部走基类（草稿直接删；已生效先对冲库存再删） */
    @Override
    @Transactional
    public void deleteBill(Long id) {
        super.deleteBill(id);
    }
}
