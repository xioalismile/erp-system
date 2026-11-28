package com.erp.freesample.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.BillApprovalAdapter;
import com.erp.common.bill.BillRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import com.erp.config.BusinessException;
import com.erp.crm.entity.FollowUp;
import com.erp.crm.mapper.FollowUpMapper;
import com.erp.freesample.entity.FreeSampleOrder;
import com.erp.freesample.entity.FreeSampleOrderItem;
import com.erp.freesample.mapper.FreeSampleOrderItemMapper;
import com.erp.freesample.mapper.FreeSampleOrderMapper;
import com.erp.basedata.entity.Warehouse;
import com.erp.inventory.service.InventoryService;
import com.erp.inventory.service.WarehouseService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.MaterialService;
import com.erp.config.service.SysConfigService;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.common.util.CustomFieldSearchHelper;
import com.erp.common.util.SortHelper;

/**
 * 免费样品订单 Service 实现
 * 业务场景：业务员录入免费样品订单（金额为0），仓库接单后直接发货，关联客户跟进记录
 * 状态流转: 草稿 → 待审核 → 已生效 → 已发货 → 已完成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FreeSampleOrderServiceImpl extends AbstractBillService<FreeSampleOrderMapper, FreeSampleOrder> implements FreeSampleOrderService {

    private final FreeSampleOrderItemMapper itemMapper;
    private final FollowUpMapper followUpMapper;
    private final InventoryService inventoryService;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final CustomerService customerService;
    private final MaterialService materialService;
    private final WarehouseService warehouseService;
    private final SysConfigService sysConfigService;

    /** 统一数据权限解析器：按角色 data_scope（仅本人/本部门/全部数据）过滤，创建人列 created_by */
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 下游引用校验（统一状态机）：重置草稿/作废/删除前禁止存在已生效下游 */
    @Lazy
    @Autowired
    private BillRelationService billRelationService;

    /** 免费样品金额固定为 0.00 */
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2);

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "免费样品订单"; }
    @Override protected String getApprovalBillType() { return "FREE_SAMPLE"; }
    @Override protected String getBillNo(FreeSampleOrder b) { return b.getOrderNo(); }
    @Override protected Object getId(FreeSampleOrder b) { return b.getOrderId(); }
    @Override protected String getStatus(FreeSampleOrder b) { return b.getStatus(); }
    @Override protected void setStatus(FreeSampleOrder b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(FreeSampleOrder b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(FreeSampleOrder b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(FreeSampleOrder b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(FreeSampleOrder b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(FreeSampleOrder b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(FreeSampleOrder b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(FreeSampleOrder b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(FreeSampleOrder b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(FreeSampleOrder b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(FreeSampleOrder b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(FreeSampleOrder b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(FreeSampleOrder b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(FreeSampleOrder b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(FreeSampleOrder bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getOrderNo());
    }

    /**
     * 审核通过影响：原审核动作只做状态迁移（免费样品出库出账在「发货」环节完成，样品成本归集随出库流水产生），
     * 无审核期正向影响，故此处仅留审核日志；后续新增正向影响一律写在本钩子。
     */
    @Override
    public void onApproved(FreeSampleOrder bill) {
        log.info("免费样品订单审核: {} 审核人: {}", bill.getOrderNo(), bill.getApprovedBy());
    }

    /** 重置草稿影响：已发货的按冻结成本退回库存（未发货无影响） */
    @Override
    public void onReverseApproved(FreeSampleOrder bill) {
        reverseShippedStock(bill);
        log.info("免费样品订单重置草稿回冲完成: {} 操作人: {}", bill.getOrderNo(), bill.getApprovedBy());
    }

    /** 作废影响：与重置草稿严格镜像（已发货的退回库存） */
    @Override
    public void onVoided(FreeSampleOrder bill) {
        onReverseApproved(bill);
    }

    /** 反作废影响：恢复为已生效；库存已在作废时退回，不重复扣减（需要出库时重新发货） */
    @Override
    public void onUnvoided(FreeSampleOrder bill) {
        log.info("免费样品订单反作废恢复: {} 操作人: {}", bill.getOrderNo(), bill.getApprovedBy());
    }

    /** 已发货单据退回库存：按发货时冻结的出库成本原价加回，随后清掉已扣库存标记 */
    private void reverseShippedStock(FreeSampleOrder bill) {
        if (bill == null || bill.getOrderId() == null) return;
        if (bill.getStockDeducted() == null || bill.getStockDeducted() != 1) return;
        Long stockId = bill.getWarehouseId();
        if (stockId == null) return;
        String operator = bill.getApprovedBy() != null && !bill.getApprovedBy().isBlank()
                ? bill.getApprovedBy() : "系统";
        List<FreeSampleOrderItem> items = itemMapper.selectList(
                new QueryWrapper<FreeSampleOrderItem>().eq("order_id", bill.getOrderId()).orderByAsc("line_no"));
        if (items != null) {
            for (FreeSampleOrderItem item : items) {
                if (item.getMaterialId() == null) continue;
                BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal stockQty = item.effectiveBaseQuantity() != null ? item.effectiveBaseQuantity() : qty;
                BigDecimal cost = item.getAvgCost() != null ? item.getAvgCost() : BigDecimal.ZERO;
                inventoryService.addStock(
                        stockId, item.getBinId(), item.getMaterialId(), null,
                        stockQty, cost,
                        bill.getOrderNo(), "FREE_SAMPLE_ISSUE_REVERSE",
                        "FREE_SAMPLE_ORDER", bill.getOrderNo(), operator);
            }
        }
        update(new UpdateWrapper<FreeSampleOrder>().eq("order_id", bill.getOrderId())
                .set("stock_deducted", 0));
        bill.setStockDeducted(0);
        log.info("免费样品订单退回库存: {}", bill.getOrderNo());
    }


    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义全面推广）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键）；值 = 实体属性名
        // （registerProps 由 MyBatis-Plus 元数据解析成真实列名，防手写列名笔误）。
        // 客户/业务员：按 ID 精确（前端下拉传主档 ID），Spec.column 写外键属性名。
        Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("orderNo", new BillHeaderFilterSupport.Spec("orderNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("warehouseName", new BillHeaderFilterSupport.Spec("warehouseName", BillHeaderFilterSupport.Kind.TEXT));
        f.put("expressCompany", new BillHeaderFilterSupport.Spec("expressCompany", BillHeaderFilterSupport.Kind.TEXT));
        f.put("expressNo", new BillHeaderFilterSupport.Spec("expressNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（状态枚举 / 外键 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("customerName", new BillHeaderFilterSupport.Spec("customerId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("salerName", new BillHeaderFilterSupport.Spec("salesId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("orderDate", new BillHeaderFilterSupport.Spec("orderDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("deliveryDate", new BillHeaderFilterSupport.Spec("deliveryDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalQuantity", new BillHeaderFilterSupport.Spec("totalQuantity", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("totalAmount", new BillHeaderFilterSupport.Spec("totalAmount", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("freeSampleOrder", FreeSampleOrder.class, f);
    }

    /** 旧签名（老调用方零影响）：委托新重载，不附带表头字段过滤 */
    @Override
    public PageResult<FreeSampleOrder> pageList(int current, int size, String keyword, String status, String customSearch) {
        return pageList(current, size, keyword, status, customSearch, null);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）：
     * 白名单 + 参数化，非法/空白/白名单外入参在支持类内部忽略，不阻断列表查询。
     */
    @Override
    public PageResult<FreeSampleOrder> pageList(int current, int size, String keyword, String status, String customSearch, Map<String, String> headerFilters) {
        QueryWrapper<FreeSampleOrder> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("order_no", keyword)
                    .or().like("customer_name", keyword)
                    .or().like("sales_name", keyword)
                    .or().like("express_no", keyword));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 表头字段过滤（2026-10-14 筛选条件自定义）：f_* → 白名单翻译（非法/空白/白名单外一律忽略，不阻断列表查询）
        BillHeaderFilterSupport.apply(wrapper, "freeSampleOrder", headerFilters);
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "free_sample_order", customSearch, CustomFieldSearchHelper.idColumnOf("free_sample_order"));
        // 数据权限（按角色 data_scope：仅本人/本部门/全部数据）—— 创建人列 created_by，total 随过滤变化
        dataScopeResolver.applyIfLimited(wrapper, null, "created_by");
        SortHelper.orderByNewest(wrapper, FreeSampleOrder.class);
        Page<FreeSampleOrder> page = new Page<>(current, size);
        Page<FreeSampleOrder> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public FreeSampleOrder getById(Long id) {
        FreeSampleOrder order = baseMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("免费样品订单不存在");
        }
        List<FreeSampleOrderItem> items = itemMapper.selectList(
                new QueryWrapper<FreeSampleOrderItem>().eq("order_id", id).orderByAsc("line_no"));
        order.setItems(items);
        return order;
    }

    @Override
    @Transactional
    public FreeSampleOrder saveWithItems(FreeSampleOrder order) {
        // 单号自动生成: FS-yyyyMMdd-0001
        if (order.getOrderNo() == null || order.getOrderNo().isBlank()) {
            order.setOrderNo(generateOrderNo());
        }
        // 默认值
        if (order.getStatus() == null) order.setStatus("草稿");
        // 新建草稿时同步 approval_status，避免该列留空（与全系统审批口径一致）
        if ("草稿".equals(order.getStatus()) && order.getApprovalStatus() == null) {
            order.setApprovalStatus("草稿");
        }
        if (order.getOrderDate() == null) order.setOrderDate(LocalDate.now());
        if (order.getCreatedAt() == null) order.setCreatedAt(LocalDateTime.now());
        if (order.getUpdatedAt() == null) order.setUpdatedAt(LocalDateTime.now());
        // createdBy/updatedBy 交由 AuditMetaObjectHandler 按当前登录人自动填充（无登录态时落 system）
        // 免费样品金额强制为 0.00
        order.setTotalAmount(ZERO_AMOUNT);
        // 汇总数量
        recalculateQuantity(order, order.getItems());
        // 保存主表
        save(order);
        // 保存明细（行号从1开始步长1）
        saveItems(order.getOrderId(), order.getItems());
        // 同步更新 crm_follow_up.related_sample_order_id
        if (order.getFollowUpId() != null) {
            updateFollowUpRelation(order.getFollowUpId(), order.getOrderId());
        }
        log.info("免费样品订单新增: {} 客户: {} 明细{}项 状态: {}",
                order.getOrderNo(), order.getCustomerName(),
                order.getItems() != null ? order.getItems().size() : 0, order.getStatus());
        return order;
    }

    @Override
    @Transactional
    public FreeSampleOrder update(Long id, FreeSampleOrder order) {
        FreeSampleOrder existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("免费样品订单不存在");
        }
        // 状态校验：已生效及之后状态不可直接修改（需通过状态流转接口处理）
        String status = existing.getStatus();
        if ("已生效".equals(status) || "已发货".equals(status) || "已完成".equals(status)) {
            throw new BusinessException("当前状态(" + status + ")不可修改");
        }
        order.setOrderId(id);
        // 单号不可修改
        order.setOrderNo(existing.getOrderNo());
        // 状态不可通过修改接口变更（仅由状态流转接口控制）
        order.setStatus(existing.getStatus());
        // 免费样品金额强制为 0.00
        order.setTotalAmount(ZERO_AMOUNT);
        if (order.getUpdatedAt() == null) order.setUpdatedAt(LocalDateTime.now());
        // updatedBy 交由 AuditMetaObjectHandler 按当前登录人自动填充（无登录态时落 system）
        // 汇总数量
        recalculateQuantity(order, order.getItems());
        // 更新主表
        updateById(order);
        // 保存明细：先删除旧明细再插入新明细
        itemMapper.delete(new QueryWrapper<FreeSampleOrderItem>().eq("order_id", id));
        saveItems(id, order.getItems());
        // 同步更新 crm_follow_up.related_sample_order_id
        if (order.getFollowUpId() != null) {
            updateFollowUpRelation(order.getFollowUpId(), id);
        }
        log.info("免费样品订单修改: {} 明细{}项",
                existing.getOrderNo(), order.getItems() != null ? order.getItems().size() : 0);
        return order;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        FreeSampleOrder existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("免费样品订单不存在");
        }
        // 草稿校验 + 下游校验 + 整单级联删除（主表 + 明细/从表）由统一状态机执行
        super.deleteBill(id);
        // 解除跟进记录关联
        if (existing.getFollowUpId() != null) {
            UpdateWrapper<FollowUp> uw = new UpdateWrapper<>();
            uw.eq("follow_up_id", existing.getFollowUpId()).set("related_sample_order_id", null);
            followUpMapper.update(null, uw);
        }
        log.info("免费样品订单删除: {}", existing.getOrderNo());
    }

    /** 提交审核：状态校验/严格校验/审批开关/状态迁移全部由统一状态机执行 */
    @Override
    @Transactional
    public void submit(Long id, String operator) {
        FreeSampleOrder order = baseMapper.selectById(id);
        if (order == null) throw new BusinessException("免费样品订单不存在");
        String submitter = (operator != null && !operator.isBlank()) ? operator : BillApprovalAdapter.currentSubmitter();
        super.submitForApproval(order, submitter);
    }

    /** 提交前的严格业务校验（统一状态机在提交/草稿直审时调用） */
    @Override
    protected void validateStrictSubmit(FreeSampleOrder order) {
        Long id = order.getOrderId();
        List<FreeSampleOrderItem> items = itemMapper.selectList(
                new QueryWrapper<FreeSampleOrderItem>().eq("order_id", id));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("没有明细，请至少添加一行物料后再提交");
        }

        if (order.getCustomerId() == null) {
            throw new BusinessException("请选择客户");
        }
        if (customerService.getById(order.getCustomerId()) == null) {
            throw new BusinessException("关联客户不存在(customerId=" + order.getCustomerId() + ")，请重新选择客户");
        }
        if (order.getWarehouseId() == null) {
            throw new BusinessException("请选择出库仓库");
        }
        Warehouse wh = warehouseService.getById(order.getWarehouseId());
        if (wh == null) {
            throw new BusinessException("关联仓库不存在(warehouseId=" + order.getWarehouseId() + ")，请重新选择仓库");
        }

        boolean negativeAllowed = sysConfigService.getBooleanValue("inventory.negative.allowed", false);
        for (int i = 0; i < items.size(); i++) {
            FreeSampleOrderItem item = items.get(i);
            int lineNo = i + 1;
            if (item.getMaterialId() == null) {
                throw new BusinessException("第" + lineNo + "行物料ID不能为空");
            }
            Material mat = materialService.getById(item.getMaterialId());
            if (mat == null) {
                throw new BusinessException("第" + lineNo + "行物料不存在(materialId=" + item.getMaterialId() + ")，请重新选择物料");
            }
            // 多单位：出库按「基本单位数量」校验库存（辅单位录入时行数量≠库存口径）
            BigDecimal qty = item.effectiveBaseQuantity() != null
                    ? item.effectiveBaseQuantity()
                    : (item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                String matLabel = (mat.getMaterialCode() != null ? mat.getMaterialCode() : "")
                        + " " + (mat.getMaterialName() != null ? mat.getMaterialName() : "");
                throw new BusinessException("第" + lineNo + "行[" + matLabel.trim() + "]数量必须大于0");
            }

            if (!negativeAllowed) {
                List<com.erp.inventory.entity.Inventory> invList = inventoryService.getByMaterialAndStock(item.getMaterialId(), order.getWarehouseId());
                BigDecimal available = BigDecimal.ZERO;
                for (com.erp.inventory.entity.Inventory inv : invList) {
                    if (inv != null && inv.getAvailableQuantity() != null) {
                        available = available.add(inv.getAvailableQuantity());
                    }
                }
                if (available.compareTo(qty) < 0) {
                    String matLabel = (mat.getMaterialCode() != null ? mat.getMaterialCode() : "")
                            + " " + (mat.getMaterialName() != null ? mat.getMaterialName() : "");
                    String whName = wh.getStockName() != null ? wh.getStockName() : "ID=" + order.getWarehouseId();
                    throw new BusinessException("第" + lineNo + "行物料[" + matLabel.trim() + "]库存不足，仓库["
                            + whName + "]可用 " + available + "，需出库 " + qty);
                }
            }
        }
    }

    /**
     * 审核：审批引擎防护 + 状态迁移全部由统一状态机执行，业务影响在 onApproved 钩子。
     * 必须覆盖 Object 签名：审批引擎回调（基类 onApprovalApproved）按 Object 签名调用，否则防绕过校验失效。
     */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask(getApprovalBillType(), (Long) id) != null) {
            throw new BusinessException("免费样品订单已提交审批流程，请由指定审批人在审批中心处理");
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
    public void ship(Long id, String expressNo, String expressCompany, String operator) {
        FreeSampleOrder order = baseMapper.selectById(id);
        if (order == null) throw new BusinessException("免费样品订单不存在");
        if (!"已生效".equals(order.getStatus())) {
            throw new BusinessException("只有已生效状态可发货，当前: " + order.getStatus());
        }
        // 发货：设置快递单号、快递公司、发货日期、状态为已发货
        UpdateWrapper<FreeSampleOrder> uw = new UpdateWrapper<>();
        uw.eq("order_id", id)
                .set("status", "已发货")
                .set("express_no", expressNo)
                .set("express_company", expressCompany)
                .set("delivery_date", LocalDate.now())
                .set("updated_by", operator)
                .set("updated_at", LocalDateTime.now());
        update(uw);

        // 根据明细扣减库存：成本按出库时冻结，作废/删除按同一成本退回；扣减失败整单回滚，不再吞异常
        List<FreeSampleOrderItem> items = itemMapper.selectList(
                new QueryWrapper<FreeSampleOrderItem>().eq("order_id", id).orderByAsc("line_no"));
        if (items != null && !items.isEmpty() && order.getWarehouseId() != null) {
            int deducted = 0;
            for (FreeSampleOrderItem item : items) {
                if (item.getMaterialId() == null) continue;
                BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                // 库存记账只认基本单位数量（多单位由 baseQuantity 折算）
                BigDecimal stockQty = item.effectiveBaseQuantity() != null ? item.effectiveBaseQuantity() : qty;
                BigDecimal unitCost = inventoryService.getOutUnitCost(
                        order.getWarehouseId(), item.getBinId(), item.getMaterialId(), null);
                item.setAvgCost(unitCost);
                itemMapper.updateById(item);
                inventoryService.deductStock(
                        order.getWarehouseId(),
                        item.getBinId(),
                        item.getMaterialId(),
                        null,
                        stockQty,
                        order.getOrderNo(),
                        "FREE_SAMPLE_ISSUE",
                        "FREE_SAMPLE_ORDER",
                        order.getOrderNo(),
                        operator);
                deducted += 1;
            }
            update(new UpdateWrapper<FreeSampleOrder>().eq("order_id", id)
                    .set("stock_deducted", deducted > 0 ? 1 : 0));
            log.info("免费样品订单发货并扣减库存: {} 明细{}项 扣减{}项",
                    order.getOrderNo(), items.size(), deducted);
        } else {
            log.info("免费样品订单发货（无明细或无仓库，跳过库存扣减）: {}", order.getOrderNo());
        }
        log.info("免费样品订单发货: {} 快递: {} {} 操作人: {}",
                order.getOrderNo(), expressCompany, expressNo, operator);
    }

    @Override
    @Transactional
    public void complete(Long id, String operator) {
        FreeSampleOrder order = baseMapper.selectById(id);
        if (order == null) throw new BusinessException("免费样品订单不存在");
        if (!"已发货".equals(order.getStatus())) {
            throw new BusinessException("只有已发货状态可完成，当前: " + order.getStatus());
        }
        UpdateWrapper<FreeSampleOrder> uw = new UpdateWrapper<>();
        uw.eq("order_id", id)
                .set("status", "已完成")
                .set("updated_by", operator)
                .set("updated_at", LocalDateTime.now());
        update(uw);
        log.info("免费样品订单完成: {} 操作人: {}", order.getOrderNo(), operator);
    }

    @Override
    public String generateOrderNo() {
        String docType = "FREE_SAMPLE_ORDER";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "FS-" + dateStr + "-";
            QueryWrapper<FreeSampleOrder> wrapper = new QueryWrapper<>();
            wrapper.likeRight("order_no", prefix).orderByDesc("order_no").last("LIMIT 1");
            FreeSampleOrder last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getOrderNo() != null) {
                try {
                    seq = Integer.parseInt(last.getOrderNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    /** 保存明细：行号从1开始，步长1 */
    private void saveItems(Long orderId, List<FreeSampleOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        int lineNo = 1;
        for (FreeSampleOrderItem item : items) {
            item.setItemId(null);
            item.setOrderId(orderId);
            item.setLineNo(lineNo);
            itemMapper.insert(item);
            lineNo += 1;
        }
    }

    /** 汇总数量，并强制金额为 0.00 */
    private void recalculateQuantity(FreeSampleOrder order, List<FreeSampleOrderItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        if (items != null) {
            for (FreeSampleOrderItem item : items) {
                BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                total = total.add(qty);
            }
        }
        order.setTotalQuantity(total);
        // 免费样品金额强制为 0.00
        order.setTotalAmount(ZERO_AMOUNT);
    }

    /** 同步更新 crm_follow_up.related_sample_order_id */
    private void updateFollowUpRelation(Long followUpId, Long orderId) {
        UpdateWrapper<FollowUp> uw = new UpdateWrapper<>();
        uw.eq("follow_up_id", followUpId).set("related_sample_order_id", orderId);
        followUpMapper.update(null, uw);
    }

    /** 驳回：状态校验/审批引擎防护由统一状态机执行 */
    @Override
    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
    }

    /** 重置草稿：保留"仅已生效可重置"守卫（已发货/已完成不可回退），迁移与下游校验由基类执行 */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        FreeSampleOrder order = getById(id);
        if (order == null) throw new BusinessException("免费样品订单不存在");
        if (!"已生效".equals(order.getStatus())) {
            throw new BusinessException("只有已生效状态可重置草稿，当前: " + order.getStatus());
        }
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        update(new UpdateWrapper<FreeSampleOrder>().eq("order_id", id)
                .set("approved_by", null).set("approved_at", null));
    }

    /** 作废：状态校验/下游校验/作废前状态记录由统一状态机执行，冲回在 onVoided 钩子 */
    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        super.voidBill(id, operator, reason);
    }

    /** 反作废：恢复作废前状态，不重复释放正向影响 */
    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }

    /** 结案：状态校验/结案字段（含结案人、时间、原因）由统一状态机执行 */
    @Override
    @Transactional
    public void closeOrder(Long id, String operator) {
        super.close(id, operator, "手动结案");
    }

    /** 反结案：状态校验/结案字段由统一状态机执行 */
    @Override
    @Transactional
    public void reopenOrder(Long id, String operator) {
        super.reopen(id, operator);
    }
}
