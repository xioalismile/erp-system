package com.erp.finance.prepayment.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.common.bill.ApprovalGateHelper;
import com.erp.common.bill.BillApprovalAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillStatusEnum;
import com.erp.common.bill.BillStatusUtils;
import com.erp.common.query.ListQuerySupport;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.config.BusinessException;
import com.erp.finance.prepayment.purchase.entity.PurchasePrePayment;
import com.erp.finance.prepayment.purchase.entity.PurchasePrePaymentItem;
import com.erp.finance.prepayment.purchase.mapper.PurchasePrePaymentItemMapper;
import com.erp.finance.prepayment.purchase.mapper.PurchasePrePaymentMapper;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.basedata.entity.BankAccount;
import com.erp.basedata.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class PurchasePrePaymentServiceImpl extends com.erp.common.bill.AbstractBillService<PurchasePrePaymentMapper, PurchasePrePayment>
        implements PurchasePrePaymentService {

    /** 下游引用校验（统一状态机）：重置草稿/作废/删除前禁止存在已生效下游 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.bill.BillRelationService billRelationService;


    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "account_id",
            "account_name",
            "amount",
            "base_amount",
            "create_by",
            "create_time",
            "created_at",
            "currency_code",
            "exchange_rate",
            "id",
            "order_id",
            "order_no",
            "payment_date",
            "payment_method",
            "payment_no",
            "pre_payment_id",
            "reject_reason",
            "rejected_at",
            "remain_amount",
            "remark",
            "reverse_approved_at",
            "reverse_approver",
            "status",
            "supplier_id",
            "supplier_name",
            "update_time",
            "used_amount",
            "void_reason",
            "voided_at",
            "voided_by"
        );

    private final PurchasePrePaymentItemMapper purchasePrePaymentItemMapper;
    private final ApprovalGateHelper approvalGateHelper;
    /** 审批通用接入件（提交进入审批引擎 + 构建条件路由 billData） */
    private final BillApprovalAdapter billApprovalAdapter;

    /**
     * 审批运行时引擎：仅用于「已有进行中审批任务时禁止绕过引擎直审」的防护判断。
     * @Lazy 打破循环依赖：审批引擎按 BillApprovalCallback 收集全部单据Service，
     * 单据Service又需要审批引擎 —— 延迟代理避免启动期循环创建失败。
     */
    @Lazy
    @Autowired
    private ApprovalRuntimeService approvalRuntimeService;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final BankAccountService bankAccountService;

    /** 整单删除级联服务（删主表前清理明细，2026-09-19） */
    private final BillCascadeDeleteService billCascadeDeleteService;

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义 · 财务推广批）。
        // 键 = 前端筛选条件定义键（与「采购预付款单明细报表」引擎筛选键同键）；值 = 实体属性名。
        // 注意：本单 status 是**数字**列（0草稿/1待审核/2已生效/3已核销/7已作废）→ 用 EQ_LONG，
        // 绝不能用 EQ_STRING（字符串精确对 int 列会直接 SQL 报错）。
        Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("paymentNo", new BillHeaderFilterSupport.Spec("paymentNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("orderNo", new BillHeaderFilterSupport.Spec("orderNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("accountName", new BillHeaderFilterSupport.Spec("accountName", BillHeaderFilterSupport.Kind.TEXT));
        f.put("paymentMethod", new BillHeaderFilterSupport.Spec("paymentMethod", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceType", new BillHeaderFilterSupport.Spec("sourceType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 外键 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("supplierName", new BillHeaderFilterSupport.Spec("supplierId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("cashierName", new BillHeaderFilterSupport.Spec("cashierId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("closeStatus", new BillHeaderFilterSupport.Spec("closeStatus", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 / 数值区间 ──
        f.put("paymentDate", new BillHeaderFilterSupport.Spec("paymentDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("amount", new BillHeaderFilterSupport.Spec("amount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("baseAmount", new BillHeaderFilterSupport.Spec("baseAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("usedAmount", new BillHeaderFilterSupport.Spec("usedAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("remainAmount", new BillHeaderFilterSupport.Spec("remainAmount", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("purchasePrePayment", PurchasePrePayment.class, f);
    }

    @Override
    public PageResult<PurchasePrePayment> pageQuery(int current, int size, String keyword, Integer status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder) {
        QueryWrapper<PurchasePrePayment> wrapper = new QueryWrapper<>();
        // 关键词：单号 + 供应商名称 + 来源单号 + 备注，强制成组（原实现已是成组 OR，此处改用统一件，语义不变）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "payment_no", "supplier_name", "order_no", "remark");
        if (status != null) {
            wrapper.eq("status", status);
        }
        // 日期区间（本页真实列 payment_date）+ 供应商维度（本页真实列 supplier_id）
        ListQuerySupport.applyDateRange(wrapper, "payment_date", startDate, endDate);
        ListQuerySupport.applyId(wrapper, "supplier_id", supplierId);
        // 员工维度（统一入参 employeeId → 本页真实列 cashier_id，业务口径「付款人」）
        ListQuerySupport.applyEmployee(wrapper, employeeId, "cashier_id");
        // 数据权限（2026-09-02 配置驱动）：purchase_pre_payment 表使用 create_by 列存储创建人
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null, "create_by");
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PurchasePrePayment.class);
        Page<PurchasePrePayment> page = new Page<>(current, size);
        Page<PurchasePrePayment> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 · 财务推广批）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    @Override
    public PageResult<PurchasePrePayment> pageQuery(int current, int size, String keyword, Integer status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, Map<String, String> headerFilters) {
        QueryWrapper<PurchasePrePayment> wrapper = new QueryWrapper<>();
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "payment_no", "supplier_name", "order_no", "remark");
        if (status != null) {
            wrapper.eq("status", status);
        }
        ListQuerySupport.applyDateRange(wrapper, "payment_date", startDate, endDate);
        ListQuerySupport.applyId(wrapper, "supplier_id", supplierId);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "cashier_id");
        // 表头字段过滤：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "purchasePrePayment", headerFilters);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null, "create_by");
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PurchasePrePayment.class);
        Page<PurchasePrePayment> page = new Page<>(current, size);
        Page<PurchasePrePayment> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public PurchasePrePayment getDetail(Long id) {
        PurchasePrePayment prePayment = super.getById(id);
        if (prePayment == null) {
            throw new BusinessException("预付款单不存在");
        }
        QueryWrapper<PurchasePrePaymentItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("pre_payment_id", id);
        // 只回显「付款明细」（source_type 为空）；核销明细(source_type 非空)由核销弹窗单独查询
        itemWrapper.and(w -> w.isNull("source_type").or().eq("source_type", ""));
        // 明细回显必须按录入顺序（主键升序）：原先按 create_time DESC → 两行金额倒序显示（2026-09-20 端到端闭环实测）
        itemWrapper.orderByAsc("id");
        List<PurchasePrePaymentItem> items = purchasePrePaymentItemMapper.selectList(itemWrapper);
        prePayment.setItems(items);
        return prePayment;
    }

    @Override
    @Transactional
    public PurchasePrePayment savePrePayment(PurchasePrePayment prePayment) {
        if (prePayment.getId() == null) {
            if (prePayment.getPaymentNo() == null || prePayment.getPaymentNo().isBlank()) {
                // 设置中心已关闭自动生成时，要求用户手动输入编号，不再兜底自动生成
                if (!sysSerialNoConfigService.isAutoGenerate("PURCHASE_PRE_PAYMENT")) {
                    throw new BusinessException("采购预付款单已关闭自动编号，请手动输入单据编号");
                }
                prePayment.setPaymentNo(generatePaymentNo());
            }
            // 状态初始化与落库统一走状态机（保存=草稿），保存方法内不直接写状态
            if (prePayment.getStatus() == null) setStatus(prePayment, "草稿");
            // 新建草稿时同步 approval_status，避免该列留空（与全系统审批口径一致）
            if (prePayment.getApprovalStatus() == null) setApprovalStatus(prePayment, "草稿");
            if (prePayment.getAmount() == null) {
                prePayment.setAmount(BigDecimal.ZERO);
            }
            if (prePayment.getUsedAmount() == null) {
                prePayment.setUsedAmount(BigDecimal.ZERO);
            }
            prePayment.setRemainAmount(prePayment.getAmount().subtract(prePayment.getUsedAmount()));
            if (prePayment.getBaseAmount() == null && prePayment.getAmount() != null) {
                if (prePayment.getExchangeRate() != null) {
                    prePayment.setBaseAmount(prePayment.getAmount().multiply(prePayment.getExchangeRate()));
                } else {
                    prePayment.setBaseAmount(prePayment.getAmount());
                }
            }
            prePayment.setCreateTime(LocalDateTime.now());
            prePayment.setUpdateTime(LocalDateTime.now());
            saveDraft(prePayment);
            // 保存付款明细（账户信息）
            savePaymentItems(prePayment.getId(), prePayment.getItems());
            log.info("新增预付款单: {}", prePayment.getPaymentNo());
        } else {
            PurchasePrePayment existing = super.getById(prePayment.getId());
            if (existing == null) {
                throw new BusinessException("预付款单不存在");
            }
            if (existing.getStatus() != null && existing.getStatus() != 0) {
                throw new BusinessException("只有草稿状态可修改，当前状态: " + existing.getStatus());
            }
            if (prePayment.getAmount() == null) {
                prePayment.setAmount(BigDecimal.ZERO);
            }
            if (prePayment.getUsedAmount() == null) {
                prePayment.setUsedAmount(existing.getUsedAmount());
            }
            prePayment.setRemainAmount(prePayment.getAmount().subtract(prePayment.getUsedAmount()));
            if (prePayment.getBaseAmount() == null && prePayment.getAmount() != null) {
                if (prePayment.getExchangeRate() != null) {
                    prePayment.setBaseAmount(prePayment.getAmount().multiply(prePayment.getExchangeRate()));
                } else {
                    prePayment.setBaseAmount(prePayment.getAmount());
                }
            }
            prePayment.setUpdateTime(LocalDateTime.now());
            baseMapper.updateById(prePayment);
            // 更新付款明细（账户信息）
            savePaymentItems(prePayment.getId(), prePayment.getItems());
            log.info("修改预付款单: {}", prePayment.getPaymentNo());
        }
        return getDetail(prePayment.getId());
    }

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "采购预付款单"; }
    @Override protected String getApprovalBillType() { return "PURCHASE_PRE_PAYMENT"; }
    @Override protected String getBillNo(PurchasePrePayment b) { return b.getPaymentNo(); }
    @Override protected Object getId(PurchasePrePayment b) { return b.getId(); }
    @Override protected String getStatus(PurchasePrePayment b) { return toStatusLabel(b.getStatus()); }
    @Override protected void setStatus(PurchasePrePayment b, String s) { b.setStatus(toStatusCode(s)); }
    @Override protected Integer getCloseStatus(PurchasePrePayment b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(PurchasePrePayment b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(PurchasePrePayment b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(PurchasePrePayment b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(PurchasePrePayment b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(PurchasePrePayment b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(PurchasePrePayment b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(PurchasePrePayment b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(PurchasePrePayment b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(PurchasePrePayment b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(PurchasePrePayment b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(PurchasePrePayment b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(PurchasePrePayment b, String v) { b.setVoidReason(v); }

    /** 数字状态 ↔ 统一文案（0草稿/1待审核/2已生效/3已核销(按生效类)/7已作废） */
    private static String toStatusLabel(Integer v) {
        if (v == null) return "草稿";
        return switch (v) {
            case 0 -> "草稿";
            case 1 -> "待审核";
            case 7 -> "已作废";
            default -> "已生效";
        };
    }

    private static Integer toStatusCode(String label) {
        if (label == null) return 0;
        return switch (label) {
            case "草稿", "已驳回", "已取消", "驳回" -> 0;
            case "待审核", "待审批", "审批中" -> 1;
            case "已作废", "作废" -> 7;
            default -> 2;
        };
    }

    @Override
    public List<String> listApprovedDownstreamBills(PurchasePrePayment bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getPaymentNo());
    }

    @Override
    protected void validateStrictSubmit(PurchasePrePayment bill) {
        // 原提交入口无额外强校验；保持行为不变
    }

    /** 提交：状态校验/审批开关/状态迁移由统一状态机执行 */
    @Override
    @Transactional
    public void submit(Long id) {
        PurchasePrePayment bill = super.getById(id);
        if (bill == null) throw new BusinessException("预付款单不存在");
        super.submitForApproval(bill, com.erp.common.bill.BillApprovalAdapter.currentSubmitter());
    }

    /**
     * 保存付款明细（账户信息）
     * 先删除旧的「付款明细」（source_type 为空的行），再插入本次明细；
     * 核销明细（source_type 非空）不在此处处理，避免被误删。
     */
    private void savePaymentItems(Long prePaymentId, List<PurchasePrePaymentItem> items) {
        if (prePaymentId == null) return;
        QueryWrapper<PurchasePrePaymentItem> del = new QueryWrapper<>();
        del.eq("pre_payment_id", prePaymentId)
           .and(w -> w.isNull("source_type").or().eq("source_type", ""));
        purchasePrePaymentItemMapper.delete(del);
        if (items == null || items.isEmpty()) return;
        for (PurchasePrePaymentItem item : items) {
            item.setId(null);
            item.setPrePaymentId(prePaymentId);
            // 付款明细：source 相关字段置空
            item.setSourceId(null);
            item.setSourceNo(null);
            item.setSourceType(null);
            if (item.getCreateTime() == null) item.setCreateTime(LocalDateTime.now());
            purchasePrePaymentItemMapper.insert(item);
        }
    }

    /** 审核入口兼容：草稿/待审核 → 统一状态机审核（含审批在途防护） */
    @Override
    @Transactional
    public void audit(Long id) {
        PurchasePrePayment bill = super.getById(id);
        if (bill == null) throw new BusinessException("预付款单不存在");
        Integer st = bill.getStatus();
        if (st == null || (st != 0 && st != 1)) {
            throw new BusinessException("只有草稿或待审核状态可审核，当前状态: " + st);
        }
        approve(id, com.erp.common.bill.BillApprovalAdapter.currentSubmitter());
    }

    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("PURCHASE_PRE_PAYMENT", (Long) id) != null) {
            throw new BusinessException("预付款单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    @Override
    public void onApproved(PurchasePrePayment bill) {
        log.info("采购预付款单审核生效: {}", bill.getPaymentNo());
    }

    /** 作废：已核销金额>0 不允许作废；状态校验/下游校验/作废前状态记录由统一状态机执行 */
    @Override
    @Transactional
    public void voidPayment(Long id) {
        PurchasePrePayment bill = super.getById(id);
        if (bill == null) throw new BusinessException("预付款单不存在");
        if (bill.getUsedAmount() != null && bill.getUsedAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("已核销的预付款单不能作废，请先反核销");
        }
        super.voidBill(id, com.erp.common.bill.BillApprovalAdapter.currentSubmitter(), null);
    }

    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        super.voidBill(id, operator, reason);
    }

    @Override
    @Transactional
    public void writeOff(Long id, List<PurchasePrePaymentItem> items) {
        PurchasePrePayment prePayment = super.getById(id);
        if (prePayment == null) {
            throw new BusinessException("预付款单不存在");
        }
        if (prePayment.getStatus() == null || prePayment.getStatus() != 2) {
            throw new BusinessException("只有已生效状态可核销，当前状态: " + prePayment.getStatus());
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException("核销明细不能为空");
        }
        BigDecimal totalWriteOffAmount = BigDecimal.ZERO;
        for (PurchasePrePaymentItem item : items) {
            if (item.getAmount() == null) {
                throw new BusinessException("核销金额不能为空");
            }
            if (item.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("核销金额必须大于0");
            }
            totalWriteOffAmount = totalWriteOffAmount.add(item.getAmount());
        }
        BigDecimal remainAmount = prePayment.getRemainAmount() != null ? prePayment.getRemainAmount() : prePayment.getAmount();
        if (totalWriteOffAmount.compareTo(remainAmount) > 0) {
            throw new BusinessException("核销金额不能大于剩余金额，剩余金额: " + remainAmount);
        }
        for (PurchasePrePaymentItem item : items) {
            item.setPrePaymentId(id);
            item.setCreateTime(LocalDateTime.now());
            purchasePrePaymentItemMapper.insert(item);
        }
        BigDecimal newUsedAmount = prePayment.getUsedAmount() != null ? prePayment.getUsedAmount() : BigDecimal.ZERO;
        newUsedAmount = newUsedAmount.add(totalWriteOffAmount);
        BigDecimal newRemainAmount = prePayment.getAmount().subtract(newUsedAmount);
        prePayment.setUsedAmount(newUsedAmount);
        prePayment.setRemainAmount(newRemainAmount);
        if (newRemainAmount.compareTo(BigDecimal.ZERO) == 0) {
            prePayment.setStatus(3);
        }
        prePayment.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(prePayment);
        log.info("核销预付款单: {} 核销金额: {}", prePayment.getPaymentNo(), totalWriteOffAmount);
    }

    @Override
    @Transactional
    public void reverseWriteOff(Long id, BigDecimal amount) {
        PurchasePrePayment prePayment = super.getById(id);
        if (prePayment == null) {
            throw new BusinessException("预付款单不存在");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal used = prePayment.getUsedAmount() == null ? BigDecimal.ZERO : prePayment.getUsedAmount();
        BigDecimal newUsed = used.subtract(amount);
        if (newUsed.compareTo(BigDecimal.ZERO) < 0) {
            newUsed = BigDecimal.ZERO;
        }
        prePayment.setUsedAmount(newUsed);
        prePayment.setRemainAmount(prePayment.getAmount().subtract(newUsed));
        // 核销完(3)回退到已生效(2)，恢复可继续核销
        if (Integer.valueOf(3).equals(prePayment.getStatus()) && newUsed.compareTo(BigDecimal.ZERO) > 0) {
            prePayment.setStatus(2);
        }
        prePayment.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(prePayment);
        log.info("反核销预付款单: {} 回减金额: {}", prePayment.getPaymentNo(), amount);
    }

    @Override
    @Transactional
    public boolean delete(Long id) {
        PurchasePrePayment prePayment = super.getById(id);
        if (prePayment == null) {
            return true;
        }
        // 草稿校验 + 下游校验 + 整单级联删除由统一状态机执行
        super.deleteBill(id);
        log.info("删除预付款单: {}", prePayment.getPaymentNo());
        return true;
    }

    /** 来源标记：采购订单预付自动生成的预付款单 */
    public static final String SOURCE_PURCHASE_ORDER_PREPAY = "PURCHASE_ORDER_PREPAY";

    /**
     * 采购订单审核通过后按订单预付金额自动生成草稿采购预付款单（幂等，草稿待财务确认提交/审核）。
     * 与人工新增（savePrePayment）的区别：不触发免审批自动生效，始终落草稿；create_by=当前操作人。
     */
    @Override
    @Transactional
    public PurchasePrePayment createFromPurchaseOrder(PurchaseOrder order) {
        if (order == null || order.getOrderId() == null) return null;
        BigDecimal amt = order.getPrepaymentAmount();
        if (amt == null || amt.compareTo(BigDecimal.ZERO) <= 0) return null;
        if (!sysSerialNoConfigService.isAutoGenerate("PURCHASE_PRE_PAYMENT")) {
            log.warn("采购预付款单已关闭自动编号，跳过订单[{}]预付的自动生成", order.getOrderNo());
            return null;
        }
        // 幂等：该订单已有来源=预付自动生成的预付款单（任何状态），不再重复生成
        Long exist = count(new QueryWrapper<PurchasePrePayment>()
                .eq("order_id", order.getOrderId()).eq("source_type", SOURCE_PURCHASE_ORDER_PREPAY));
        if (exist != null && exist > 0) {
            return null;
        }
        PurchasePrePayment p = new PurchasePrePayment();
        p.setPaymentNo(generatePaymentNo());
        p.setSupplierId(order.getSupplierId());
        p.setSupplierName(order.getSupplierName());
        p.setOrderId(order.getOrderId());
        p.setOrderNo(order.getOrderNo());
        p.setPaymentDate(LocalDate.now());
        p.setAmount(amt);
        BigDecimal rate = order.getExchangeRate() != null ? order.getExchangeRate() : BigDecimal.ONE;
        p.setExchangeRate(rate);
        p.setBaseAmount(amt.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP));
        p.setCurrencyCode(order.getCurrencyCode());
        p.setAccountId(order.getAccountId());
        p.setAccountName(order.getAccountName());
        p.setStatus(0);
        p.setUsedAmount(BigDecimal.ZERO);
        p.setRemainAmount(amt);
        p.setSourceType(SOURCE_PURCHASE_ORDER_PREPAY);
        p.setCreateBy(resolveCurrentUsername());
        p.setCreateTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        save(p);
        // 自动单必须补一条付款明细（账户行），保证财务打开单据明细不为空；
        // 账户允许为空 —— 之前用 accountId != null 做守卫会导致"无账户时明细为 0 行、打开即空白"
        if (p.getId() != null) {
            String accountNo = null;
            if (order.getAccountId() != null) {
                try {
                    BankAccount acc = bankAccountService.getById(order.getAccountId());
                    if (acc != null) accountNo = acc.getAccountNo();
                } catch (Exception ex) {
                    log.warn("读取付款账户[{}]账号失败: {}", order.getAccountId(), ex.getMessage());
                }
            }
            PurchasePrePaymentItem it = new PurchasePrePaymentItem();
            it.setAccountId(order.getAccountId());
            it.setAccountName(order.getAccountName());
            it.setAccountNo(accountNo);
            it.setAmount(amt);
            savePaymentItems(p.getId(), java.util.List.of(it));
        }
        log.info("采购订单[{}]预付{}{}自动生成草稿采购预付款单[{}]", order.getOrderNo(), amt,
                order.getSupplierName(), p.getPaymentNo());
        return p;
    }

    /**
     * 采购订单反审联动：删除该订单预付自动生成的草稿预付款单（连同付款明细）。
     * 若该订单的自动预付款单已生效（非草稿），抛出 BusinessException 阻止订单反审，
     * 由调用方（订单反审事务）整体回滚。
     */
    @Override
    @Transactional
    public int rollbackAutoByOrder(Long orderId) {
        if (orderId == null) return 0;
        QueryWrapper<PurchasePrePayment> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", orderId).eq("source_type", SOURCE_PURCHASE_ORDER_PREPAY);
        List<PurchasePrePayment> autos = list(wrapper);
        if (autos == null || autos.isEmpty()) return 0;
        for (PurchasePrePayment r : autos) {
            if (r.getStatus() != null && r.getStatus() != 0) {
                throw new BusinessException("采购订单已生成已生效的采购预付款单[" + r.getPaymentNo() + "]，请先在财务模块处理该预付款单，再重置草稿订单");
            }
        }
        int removed = 0;
        for (PurchasePrePayment r : autos) {
            delete(r.getId());
            removed++;
        }
        return removed;
    }

    /** 读取当前请求登录人（AuthInterceptor 注入 currentUsername），无请求上下文回退 system */
    private String resolveCurrentUsername() {
        try {
            jakarta.servlet.http.HttpServletRequest req = ((org.springframework.web.context.request.ServletRequestAttributes)
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).getRequest();
            Object u = req.getAttribute("currentUsername");
            if (u instanceof String s && !s.isBlank()) return s;
        } catch (Exception ignored) {
        }
        return "system";
    }

    private String generatePaymentNo() {
        // 优先走设置中心自动编号规则（PURCHASE_PRE_PAYMENT），未配置或规则异常时退回内置默认规则
        String docType = "PURCHASE_PRE_PAYMENT";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "PPP-" + dateStr + "-";
            QueryWrapper<PurchasePrePayment> wrapper = new QueryWrapper<>();
            wrapper.likeRight("payment_no", prefix).orderByDesc("payment_no").last("LIMIT 1");
            PurchasePrePayment last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getPaymentNo() != null) {
                try {
                    seq = Integer.parseInt(last.getPaymentNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    @Override
    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
        update(new UpdateWrapper<PurchasePrePayment>().eq("id", id)
                .set("reject_reason", reason)
                .set("rejector", rejector)
                .set("rejected_at", LocalDateTime.now()));
        log.info("驳回预付款单: {} 操作人: {} 原因: {}", id, rejector, reason);
    }

    /** 重置草稿：已核销金额>0 不允许重置（否则核销明细与单据状态不一致）；其余由统一状态机执行 */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        PurchasePrePayment bill = super.getById(id);
        if (bill == null) {
            throw new BusinessException("预付款单不存在");
        }
        if (bill.getUsedAmount() != null && bill.getUsedAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("已核销的预付款单不能重置草稿，请先反核销");
        }
        super.reverseApprove(id, operator);
        update(new UpdateWrapper<PurchasePrePayment>().eq("id", id)
                .set("approved_by", null).set("approved_at", null)
                .set("reverse_approver", operator)
                .set("reverse_approved_at", LocalDateTime.now()));
        log.info("重置草稿预付款单: {} 操作人: {}", id, operator);
    }

    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }

    @Override
    @Transactional
    public void closeOrder(Long id, String operator) {
        super.close(id, operator, "手动结案");
    }

    @Override
    @Transactional
    public void reopenOrder(Long id, String operator) {
        super.reopen(id, operator);
    }

    @Transactional
    public void deleteBill(Long id) {
        super.deleteBill(id);
    }
}
