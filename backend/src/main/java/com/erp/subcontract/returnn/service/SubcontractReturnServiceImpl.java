package com.erp.subcontract.returnn.service;

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
import com.erp.inventory.service.InventoryService;
import com.erp.inventory.service.MaterialCostService;
import com.erp.inventory.service.WarehouseService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import com.erp.masterdata.service.SupplierService;
import com.erp.subcontract.pick.entity.SubcontractPickItem;
import com.erp.subcontract.pick.mapper.SubcontractPickItemMapper;
import com.erp.subcontract.returnn.entity.SubcontractReturn;
import com.erp.subcontract.returnn.entity.SubcontractReturnItem;
import com.erp.subcontract.returnn.mapper.SubcontractReturnItemMapper;
import com.erp.subcontract.returnn.mapper.SubcontractReturnMapper;
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

public class SubcontractReturnServiceImpl extends AbstractBillService<SubcontractReturnMapper, SubcontractReturn>
        implements SubcontractReturnService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义全面推广 · 委外模块批次）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键）；值 = 实体属性名（防手写列名笔误）。
        // 供应商：按 ID 精确（前端下拉传主档 ID），Spec.column 写外键属性名 supplierId。
        // 状态列是数字（0 草稿/1 待审核/2 已生效/3 已入库）→ EQ_LONG（EQ_STRING 拿字符串比 integer 列会报类型错）。
        java.util.Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        f.put("returnNo", new BillHeaderFilterSupport.Spec("returnNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("orderNo", new BillHeaderFilterSupport.Spec("orderNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("pickNo", new BillHeaderFilterSupport.Spec("pickNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillType", new BillHeaderFilterSupport.Spec("sourceBillType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("supplierName", new BillHeaderFilterSupport.Spec("supplierId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("returnDate", new BillHeaderFilterSupport.Spec("returnDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("totalQuantity", new BillHeaderFilterSupport.Spec("totalQuantity", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("subcontractReturn", SubcontractReturn.class, f);
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "create_by",
            "create_time",
            "created_at",
            "id",
            "order_id",
            "order_no",
            "remark",
            "return_date",
            "return_id",
            "return_no",
            "status",
            "supplier_id",
            "supplier_name",
            "total_quantity",
            "update_time",
            "void_reason",
            "voided_at",
            "voided_by"
        );

    private final SubcontractReturnItemMapper itemMapper;
    private final InventoryService inventoryService;
    private final MaterialCostService materialCostService;
    private final SubcontractPickItemMapper subcontractPickItemMapper;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final SupplierService supplierService;
    private final MaterialService materialService;
    private final WarehouseService warehouseService;

    /** 统一数据权限解析件：按角色 sys_role.data_scope（仅本人/本部门/全部）过滤 */
    @Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 下游单据扫描件：作废/重置草稿/删除前的"下游已生效单据"校验 */
    @Autowired
    private BillRelationService billRelationService;

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "委外退料单"; }
    @Override protected String getApprovalBillType() { return "SUBCONTRACT_RETURN"; }
    @Override protected String getBillNo(SubcontractReturn b) { return b.getReturnNo(); }
    @Override protected Object getId(SubcontractReturn b) { return b.getId(); }
    @Override protected String getStatus(SubcontractReturn b) { return BillStatusUtils.toString(b.getStatus()); }
    @Override protected void setStatus(SubcontractReturn b, String s) { b.setStatus(BillStatusUtils.toNumber(s)); }
    @Override protected Integer getCloseStatus(SubcontractReturn b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(SubcontractReturn b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(SubcontractReturn b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(SubcontractReturn b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(SubcontractReturn b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(SubcontractReturn b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(SubcontractReturn b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(SubcontractReturn b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(SubcontractReturn b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(SubcontractReturn b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(SubcontractReturn b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(SubcontractReturn b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(SubcontractReturn b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(SubcontractReturn bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getReturnNo());
    }

    @Override
    @Transactional
    public void saveReturnWithItems(SubcontractReturn returnBill, List<SubcontractReturnItem> items) {
        boolean isNew = returnBill.getId() == null;

        if (!isNew) {
            SubcontractReturn existing = getById(returnBill.getId());
            if (existing != null) {
                Integer st = existing.getStatus();
                if (st != null && (st == 2 || st == 3)) {
                    throw new BusinessException("当前状态不可修改，如需修改请先重置草稿");
                }
            }
        }

        // 单号只在新建时生成：更新时不重新取号（否则每次保存都会吃掉一个序号，页面显示与实际不一致）
        if (isNew && (returnBill.getReturnNo() == null || returnBill.getReturnNo().isBlank())) {
            returnBill.setReturnNo(generateReturnNo());
        }

        if (returnBill.getStatus() == null) returnBill.setStatus(0);
        // 新建草稿时同步 approval_status，避免该列留空（与全系统审批口径一致）
        if (returnBill.getStatus() != null && returnBill.getStatus() == 0 && returnBill.getApprovalStatus() == null) {
            returnBill.setApprovalStatus("草稿");
        }
        if (returnBill.getCloseStatus() == null) returnBill.setCloseStatus(0);
        if (returnBill.getTotalQuantity() == null) returnBill.setTotalQuantity(BigDecimal.ZERO);
        if (returnBill.getReturnDate() == null) returnBill.setReturnDate(LocalDate.now());

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<SubcontractReturnItem> validItems = new ArrayList<>();
            for (SubcontractReturnItem it : items) { if (it != null && it.getMaterialId() != null) validItems.add(it); }
            items = validItems;
        }

        recalculateReturn(returnBill, items);

        if (isNew) {
            save(returnBill);
        } else {
            updateById(returnBill);
        }

        if (items != null) {
            // items 为 null 时不删除既有明细，避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<SubcontractReturnItem>().eq("return_id", returnBill.getId()));
            for (SubcontractReturnItem item : items) {
                item.setId(null);
                item.setReturnId(returnBill.getId());
                itemMapper.insert(item);
            }
        }

        log.info("委外退料单保存成功: {} 状态: {}", returnBill.getReturnNo(), returnBill.getStatus());
    }

    private void recalculateReturn(SubcontractReturn returnBill, List<SubcontractReturnItem> items) {
        BigDecimal totalQuantity = BigDecimal.ZERO;
        if (items != null) {
            for (SubcontractReturnItem item : items) {
                totalQuantity = totalQuantity.add(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO);
            }
        }
        returnBill.setTotalQuantity(totalQuantity);
    }

    @Override
    public PageResult<SubcontractReturn> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder) {
        // 老签名委托新重载（headerFilters 空 = 行为与改造前完全一致）
        return pageQuery(current, size, keyword, status, sortBy, sortOrder, null, java.util.Collections.emptyMap());
    }

    /** 老签名（仅表头字段过滤）委托新重载（customSearch 空 = 行为与改造前完全一致） */
    @Override
    public PageResult<SubcontractReturn> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder,
                                                   java.util.Map<String, String> headerFilters) {
        return pageQuery(current, size, keyword, status, sortBy, sortOrder, null, headerFilters);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤 + 自定义字段搜索」（2026-10-14 / 2026-11-24）：
     * 白名单 + 参数化，非法/空白/白名单外入参在支持类内部忽略，不阻断列表查询。
     */
    @Override
    public PageResult<SubcontractReturn> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder,
                                                   String customSearch, java.util.Map<String, String> headerFilters) {
        QueryWrapper<SubcontractReturn> wrapper = new QueryWrapper<>();
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SubcontractReturn.class);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("return_no", keyword);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        // 表头字段过滤（2026-10-14 筛选条件自定义）：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "subcontractReturn", headerFilters);
        // 数据权限（统一机制）：按角色 data_scope（仅本人/本部门/全部）过滤；无 Web 请求/全部数据时不加条件
        dataScopeResolver.applyIfLimited(wrapper, null, "create_by");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "subcontract_return", customSearch, CustomFieldSearchHelper.idColumnOf("subcontract_return"));
        Page<SubcontractReturn> page = new Page<>(current, size);
        Page<SubcontractReturn> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SubcontractReturn getDetail(Long id) {
        SubcontractReturn returnBill = getById(id);
        if (returnBill == null) throw new BusinessException("委外退料单不存在");
        List<SubcontractReturnItem> items = itemMapper.selectList(
                new QueryWrapper<SubcontractReturnItem>().eq("return_id", id).orderByAsc("id"));
        returnBill.setItems(items);
        return returnBill;
    }

    // ============ 统一状态机动作（全部 delegate 基类） ============

    @Override
    @Transactional
    public void submitForApproval(Long id) {
        SubcontractReturn returnBill = getById(id);
        if (returnBill == null) throw new BusinessException("委外退料单不存在");
        super.submitForApproval(returnBill, BillApprovalAdapter.currentSubmitter());
    }

    /** 提交审核前的严格校验（原 submitForApproval 校验整体迁移；保存草稿不触发） */
    @Override
    protected void validateStrictSubmit(SubcontractReturn returnBill) {
        List<SubcontractReturnItem> items = itemMapper.selectList(
                new QueryWrapper<SubcontractReturnItem>().eq("return_id", returnBill.getId()));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("没有明细，请至少添加一行物料后再提交");
        }

        if (returnBill.getSupplierId() == null) {
            throw new BusinessException("请选择委外供应商");
        }
        if (supplierService.getById(returnBill.getSupplierId()) == null) {
            throw new BusinessException("关联供应商不存在(supplierId=" + returnBill.getSupplierId() + ")，请重新选择供应商");
        }

        for (int i = 0; i < items.size(); i++) {
            SubcontractReturnItem item = items.get(i);
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
            if (warehouseService.getById(item.getWarehouseId()) == null) {
                throw new BusinessException("第" + lineNo + "行仓库不存在(warehouseId=" + item.getWarehouseId() + ")，请重新选择仓库");
            }
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                String matLabel = (mat.getMaterialCode() != null ? mat.getMaterialCode() : "")
                        + " " + (mat.getMaterialName() != null ? mat.getMaterialName() : "");
                throw new BusinessException("第" + lineNo + "行[" + matLabel.trim() + "]数量必须大于0");
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
        if (approvalRuntimeService.getRunningTask("SUBCONTRACT_RETURN", (Long) id) != null) {
            throw new BusinessException("委外退料单已提交审批流程，请由指定审批人在审批中心处理");
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
        SubcontractReturn bill = getById(id);
        if (bill == null) throw new BusinessException("委外退料单不存在");
        // 本单据特有守卫：只有原始状态"已生效(2)"可重置草稿（已入库(3) 不允许回退，避免库存已补还回草稿）
        if (bill.getStatus() == null || bill.getStatus() != 2) {
            throw new BusinessException("只有已生效状态可重置草稿，当前状态: " + bill.getStatus());
        }
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        UpdateWrapper<SubcontractReturn> uw = new UpdateWrapper<>();
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
     * 审核生效影响（统一状态机钩子）：生效即按发料成本加回库存；重置草稿按原成本红字冲回。
     */
    @Override
    @Transactional
    public void onApproved(SubcontractReturn returnBill) {
        moveStock(returnBill, returnBill.getApprovedBy(), true);
    }

    /** 重置草稿影响：与 onApproved 严格镜像（按原入库成本冲回） */
    @Override
    @Transactional
    public void onReverseApproved(SubcontractReturn returnBill) {
        moveStock(returnBill, returnBill.getApprovedBy(), false);
    }

    /** 退料库存动作：inbound=true 加库存，false 红字冲回 */
    private void moveStock(SubcontractReturn returnBill, String operator, boolean inbound) {
        // stock_effect=0 表示质检自动退货（不合格品从未入库），只走单据，不产生库存移动
        boolean stockEffect = returnBill.getStockEffect() == null || returnBill.getStockEffect() == 1;
        if (!stockEffect) {
            return;
        }
        List<SubcontractReturnItem> items = itemMapper.selectList(
                new QueryWrapper<SubcontractReturnItem>().eq("return_id", returnBill.getId()).orderByAsc("id"));
        if (items == null || items.isEmpty()) {
            return;
        }
        for (SubcontractReturnItem item : items) {
            Long warehouseId = item.getWarehouseId();
            Long materialId = item.getMaterialId();
            BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            if (warehouseId == null || materialId == null) {
                continue;
            }
            BigDecimal stockQty = item.effectiveBaseQuantity() != null ? item.effectiveBaseQuantity() : quantity;
            if (inbound) {
                // 退回成本优先取同一委外加工单行最近一次发料的实际成本，取不到回落最新核算成本
                BigDecimal unitCost = resolveReturnUnitCost(returnBill, item, materialId);
                inventoryService.addStock(
                        warehouseId,
                        null,
                        materialId,
                        item.getBatchNo(),
                        stockQty,
                        unitCost,
                        returnBill.getReturnNo(),
                        "SUBCONTRACT_RETURN",
                        "SubcontractReturn",
                        returnBill.getReturnNo(),
                        operator);
                item.setAvgCost(unitCost);
                itemMapper.updateById(item);
            } else {
                BigDecimal frozenCost = item.getAvgCost() != null && item.getAvgCost().compareTo(BigDecimal.ZERO) > 0
                        ? item.getAvgCost()
                        : BigDecimal.ZERO;
                inventoryService.deductStockAtCost(
                        warehouseId,
                        null,
                        materialId,
                        item.getBatchNo(),
                        stockQty,
                        frozenCost,
                        returnBill.getReturnNo(),
                        "SUBCONTRACT_RETURN_REVERSE",
                        "SubcontractReturn",
                        returnBill.getReturnNo(),
                        operator);
            }
        }
        log.info("委外退料单{}: {} 操作人: {}", inbound ? "入库" : "冲回", returnBill.getReturnNo(), operator);
    }

    /**
     * 委外退料入库成本：优先取同一加工单行最近一次发料冻结的实际成本；
     * 取不到时回落物料最新核算成本，再取不到用物料采购价，避免 0 成本回库。
     */
    private BigDecimal resolveReturnUnitCost(SubcontractReturn returnBill, SubcontractReturnItem item, Long materialId) {
        try {
            QueryWrapper<SubcontractPickItem> qw = new QueryWrapper<SubcontractPickItem>()
                    .eq("material_id", materialId)
                    .isNotNull("avg_cost")
                    .orderByDesc("id")
                    .last("LIMIT 1");
            if (item.getOrderItemId() != null) {
                qw.eq("order_item_id", item.getOrderItemId());
            } else if (returnBill.getOrderId() != null) {
                qw.inSql("pick_id", "select id from subcontract_pick where order_id = " + returnBill.getOrderId());
            }
            SubcontractPickItem pick = subcontractPickItemMapper.selectOne(qw);
            if (pick != null && pick.getAvgCost() != null && pick.getAvgCost().compareTo(BigDecimal.ZERO) > 0) {
                return pick.getAvgCost();
            }
        } catch (Exception e) {
            log.warn("[委外退料] 读取发料成本失败，回落最新核算成本: {}", e.getMessage());
        }
        BigDecimal latest = materialCostService.getLatestCalcCost(materialId);
        if (latest != null && latest.compareTo(BigDecimal.ZERO) > 0) {
            return latest;
        }
        try {
            Material material = materialService.getById(materialId);
            if (material != null && material.getPurchasePrice() != null) {
                return material.getPurchasePrice();
            }
        } catch (Exception ignored) {
        }
        return BigDecimal.ZERO;
    }

    @Override
    public String generateReturnNo() {
        String docType = "SUBCONTRACT_RETURN";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "SC-RETURN-" + dateStr + "-";
            QueryWrapper<SubcontractReturn> wrapper = new QueryWrapper<>();
            wrapper.likeRight("return_no", prefix).orderByDesc("return_no").last("LIMIT 1");
            SubcontractReturn last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getReturnNo() != null) {
                try {
                    seq = Integer.parseInt(last.getReturnNo().substring(prefix.length())) + 1;
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
