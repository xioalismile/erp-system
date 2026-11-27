package com.erp.finance.prepayment.sale.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.common.bill.ApprovalGateHelper;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillStatusEnum;
import com.erp.common.bill.BillStatusUtils;
import com.erp.common.query.ListQuerySupport;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.config.BusinessException;
import com.erp.finance.prepayment.sale.entity.SalePreReceipt;
import com.erp.finance.prepayment.sale.entity.SalePreReceiptItem;
import com.erp.finance.prepayment.sale.mapper.SalePreReceiptItemMapper;
import com.erp.finance.prepayment.sale.mapper.SalePreReceiptMapper;
import com.erp.sale.entity.SaleOrder;
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

public class SalePreReceiptServiceImpl extends com.erp.common.bill.AbstractBillService<SalePreReceiptMapper, SalePreReceipt>
        implements SalePreReceiptService {

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
            "customer_id",
            "customer_name",
            "exchange_rate",
            "id",
            "order_id",
            "order_no",
            "payment_method",
            "pre_receipt_id",
            "receipt_date",
            "receipt_no",
            "reject_reason",
            "rejected_at",
            "remain_amount",
            "remark",
            "reverse_approved_at",
            "reverse_approver",
            "status",
            "update_time",
            "used_amount",
            "void_reason",
            "voided_at",
            "voided_by"
        );

    private final SalePreReceiptItemMapper salePreReceiptItemMapper;
    private final ApprovalGateHelper approvalGateHelper;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final BankAccountService bankAccountService;

    /** 整单删除级联服务（删主表前清理明细，2026-09-19） */
    private final BillCascadeDeleteService billCascadeDeleteService;

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义 · 财务推广批）。
        // 键 = 前端筛选条件定义键（与「销售预收款单明细报表」引擎筛选键同键）；值 = 实体属性名。
        // 注意：本单 status 是**数字**列（0草稿/1待审核/2已生效/3已核销/7已作废）→ 用 EQ_LONG，
        // 绝不能用 EQ_STRING（字符串精确对 int 列会直接 SQL 报错）。
        Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("receiptNo", new BillHeaderFilterSupport.Spec("receiptNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("orderNo", new BillHeaderFilterSupport.Spec("orderNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("accountName", new BillHeaderFilterSupport.Spec("accountName", BillHeaderFilterSupport.Kind.TEXT));
        f.put("paymentMethod", new BillHeaderFilterSupport.Spec("paymentMethod", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceType", new BillHeaderFilterSupport.Spec("sourceType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 外键 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("customerName", new BillHeaderFilterSupport.Spec("customerId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("cashierName", new BillHeaderFilterSupport.Spec("cashierId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("closeStatus", new BillHeaderFilterSupport.Spec("closeStatus", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 / 数值区间 ──
        f.put("receiptDate", new BillHeaderFilterSupport.Spec("receiptDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("exchangeRate", new BillHeaderFilterSupport.Spec("exchangeRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("amount", new BillHeaderFilterSupport.Spec("amount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("baseAmount", new BillHeaderFilterSupport.Spec("baseAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("usedAmount", new BillHeaderFilterSupport.Spec("usedAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("remainAmount", new BillHeaderFilterSupport.Spec("remainAmount", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("salePreReceipt", SalePreReceipt.class, f);
    }

    @Override
    public PageResult<SalePreReceipt> pageQuery(int current, int size, String keyword, Integer status, String startDate, String endDate, String customerId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder) {
        QueryWrapper<SalePreReceipt> wrapper = new QueryWrapper<>();
        // 关键词：单号 + 客户名称 + 来源单号 + 备注，强制成组（原实现已是成组 OR，此处改用统一件，语义不变）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "receipt_no", "customer_name", "order_no", "remark");
        if (status != null) {
            wrapper.eq("status", status);
        }
        // 日期区间（本页真实列 receipt_date）+ 客户维度（本页真实列 customer_id）
        ListQuerySupport.applyDateRange(wrapper, "receipt_date", startDate, endDate);
        ListQuerySupport.applyId(wrapper, "customer_id", customerId);
        // 员工维度（统一入参 employeeId → 本页真实列 cashier_id，业务口径「收款人」）
        ListQuerySupport.applyEmployee(wrapper, employeeId, "cashier_id");
        // 数据权限（2026-09-02 配置驱动）：sale_pre_receipt 表使用 create_by 列存储创建人
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null, "create_by");
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SalePreReceipt.class);
        Page<SalePreReceipt> page = new Page<>(current, size);
        Page<SalePreReceipt> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义 · 财务推广批）。
     * headerFilters 键 = 字段键（前端已剥掉 f_ 前缀），由 BillHeaderFilterSupport 按白名单翻译；
     * null / 空 = 行为与上一重载完全一致（老调用方零影响）。
     */
    @Override
    public PageResult<SalePreReceipt> pageQuery(int current, int size, String keyword, Integer status, String startDate, String endDate, String customerId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, Map<String, String> headerFilters) {
        QueryWrapper<SalePreReceipt> wrapper = new QueryWrapper<>();
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "receipt_no", "customer_name", "order_no", "remark");
        if (status != null) {
            wrapper.eq("status", status);
        }
        ListQuerySupport.applyDateRange(wrapper, "receipt_date", startDate, endDate);
        ListQuerySupport.applyId(wrapper, "customer_id", customerId);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "cashier_id");
        // 表头字段过滤：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "salePreReceipt", headerFilters);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null, "create_by");
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SalePreReceipt.class);
        Page<SalePreReceipt> page = new Page<>(current, size);
        Page<SalePreReceipt> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SalePreReceipt getDetail(Long id) {
        SalePreReceipt preReceipt = super.getById(id);
        if (preReceipt == null) {
            throw new BusinessException("预收款单不存在");
        }
        QueryWrapper<SalePreReceiptItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("pre_receipt_id", id);
        // 只回显「收款明细」（source_type 为空）；核销明细(source_type 非空)由核销弹窗单独查询，避免污染收款明细表格
        itemWrapper.and(w -> w.isNull("source_type").or().eq("source_type", ""));
        // 明细回显必须按录入顺序（主键升序）：原先按 create_time DESC → 两行金额倒序显示（2026-09-20 端到端闭环实测）
        itemWrapper.orderByAsc("id");
        List<SalePreReceiptItem> items = salePreReceiptItemMapper.selectList(itemWrapper);
        preReceipt.setItems(items);
        return preReceipt;
    }

    @Override
    @Transactional
    public SalePreReceipt savePreReceipt(SalePreReceipt preReceipt) {
        if (preReceipt.getId() == null) {
            if (preReceipt.getReceiptNo() == null || preReceipt.getReceiptNo().isBlank()) {
                // 设置中心已关闭自动生成时，要求用户手动输入编号，不再兜底自动生成
                if (!sysSerialNoConfigService.isAutoGenerate("SALE_PRE_RECEIPT")) {
                    throw new BusinessException("销售预收款单已关闭自动编号，请手动输入单据编号");
                }
                preReceipt.setReceiptNo(generateReceiptNo());
            }
            // 状态初始化与落库统一走状态机（保存=草稿），保存方法内不直接写状态
            if (preReceipt.getStatus() == null) setStatus(preReceipt, "草稿");
            if (preReceipt.getApprovalStatus() == null) setApprovalStatus(preReceipt, "草稿");
            if (preReceipt.getAmount() == null) {
                preReceipt.setAmount(BigDecimal.ZERO);
            }
            if (preReceipt.getUsedAmount() == null) {
                preReceipt.setUsedAmount(BigDecimal.ZERO);
            }
            preReceipt.setRemainAmount(preReceipt.getAmount().subtract(preReceipt.getUsedAmount()));
            if (preReceipt.getBaseAmount() == null && preReceipt.getAmount() != null) {
                if (preReceipt.getExchangeRate() != null) {
                    preReceipt.setBaseAmount(preReceipt.getAmount().multiply(preReceipt.getExchangeRate()));
                } else {
                    preReceipt.setBaseAmount(preReceipt.getAmount());
                }
            }
            preReceipt.setCreateTime(LocalDateTime.now());
            preReceipt.setUpdateTime(LocalDateTime.now());
            saveDraft(preReceipt);
            // 保存收款明细（账户信息）
            saveReceiptItems(preReceipt.getId(), preReceipt.getItems());
            log.info("新增预收款单: {}", preReceipt.getReceiptNo());
        } else {
            SalePreReceipt existing = super.getById(preReceipt.getId());
            if (existing == null) {
                throw new BusinessException("预收款单不存在");
            }
            if (existing.getStatus() != null && existing.getStatus() != 0) {
                throw new BusinessException("只有草稿状态可修改，当前状态: " + existing.getStatus());
            }
            if (preReceipt.getAmount() == null) {
                preReceipt.setAmount(BigDecimal.ZERO);
            }
            if (preReceipt.getUsedAmount() == null) {
                preReceipt.setUsedAmount(existing.getUsedAmount());
            }
            preReceipt.setRemainAmount(preReceipt.getAmount().subtract(preReceipt.getUsedAmount()));
            if (preReceipt.getBaseAmount() == null && preReceipt.getAmount() != null) {
                if (preReceipt.getExchangeRate() != null) {
                    preReceipt.setBaseAmount(preReceipt.getAmount().multiply(preReceipt.getExchangeRate()));
                } else {
                    preReceipt.setBaseAmount(preReceipt.getAmount());
                }
            }
            preReceipt.setUpdateTime(LocalDateTime.now());
            baseMapper.updateById(preReceipt);
            // 更新收款明细（账户信息）
            saveReceiptItems(preReceipt.getId(), preReceipt.getItems());
            log.info("修改预收款单: {}", preReceipt.getReceiptNo());
        }
        return getDetail(preReceipt.getId());
    }

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "销售预收款单"; }
    @Override protected String getApprovalBillType() { return "SALE_PRE_RECEIPT"; }
    @Override protected String getBillNo(SalePreReceipt b) { return b.getReceiptNo(); }
    @Override protected Object getId(SalePreReceipt b) { return b.getId(); }
    @Override protected String getStatus(SalePreReceipt b) { return toStatusLabel(b.getStatus()); }
    @Override protected void setStatus(SalePreReceipt b, String s) { b.setStatus(toStatusCode(s)); }
    @Override protected Integer getCloseStatus(SalePreReceipt b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(SalePreReceipt b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(SalePreReceipt b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(SalePreReceipt b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(SalePreReceipt b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(SalePreReceipt b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(SalePreReceipt b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(SalePreReceipt b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(SalePreReceipt b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(SalePreReceipt b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(SalePreReceipt b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(SalePreReceipt b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(SalePreReceipt b, String v) { b.setVoidReason(v); }

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
    public List<String> listApprovedDownstreamBills(SalePreReceipt bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getReceiptNo());
    }

    @Override
    protected void validateStrictSubmit(SalePreReceipt bill) {
        // 原提交入口无额外强校验；保持行为不变
    }

    /** 提交：状态校验/审批开关/状态迁移由统一状态机执行 */
    @Override
    @Transactional
    public void submit(Long id) {
        SalePreReceipt bill = super.getById(id);
        if (bill == null) throw new BusinessException("预收款单不存在");
        super.submitForApproval(bill, com.erp.common.bill.BillApprovalAdapter.currentSubmitter());
    }

    /**
     * 保存收款明细（账户信息）
     * 先删除旧的「收款明细」（source_type 为空的行），再插入本次明细；
     * 核销明细（source_type 非空）不在此处处理，避免被误删。
     */
    private void saveReceiptItems(Long preReceiptId, List<SalePreReceiptItem> items) {
        if (preReceiptId == null) return;
        // 删除旧收款明细
        QueryWrapper<SalePreReceiptItem> del = new QueryWrapper<>();
        del.eq("pre_receipt_id", preReceiptId)
           .and(w -> w.isNull("source_type").or().eq("source_type", ""));
        salePreReceiptItemMapper.delete(del);
        if (items == null || items.isEmpty()) return;
        for (SalePreReceiptItem item : items) {
            item.setId(null);
            item.setPreReceiptId(preReceiptId);
            // 收款明细：source 相关字段置空
            item.setSourceId(null);
            item.setSourceNo(null);
            item.setSourceType(null);
            if (item.getCreateTime() == null) item.setCreateTime(LocalDateTime.now());
            salePreReceiptItemMapper.insert(item);
        }
    }

    /** 审核入口兼容：草稿/待审核 → 统一状态机审核 */
    @Override
    @Transactional
    public void audit(Long id) {
        SalePreReceipt bill = super.getById(id);
        if (bill == null) throw new BusinessException("预收款单不存在");
        Integer st = bill.getStatus();
        if (st == null || (st != 0 && st != 1)) {
            throw new BusinessException("只有草稿或待审核状态可审核，当前状态: " + st);
        }
        approve(id, com.erp.common.bill.BillApprovalAdapter.currentSubmitter());
    }

    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("SALE_PRE_RECEIPT", (Long) id) != null) {
            throw new BusinessException("预收款单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    @Override
    public void onApproved(SalePreReceipt bill) {
        log.info("销售预收款单审核生效: {}", bill.getReceiptNo());
    }

    /** 作废：已核销金额>0 不允许作废；状态校验/下游校验/作废前状态记录由统一状态机执行 */
    @Override
    @Transactional
    public void voidReceipt(Long id) {
        SalePreReceipt bill = super.getById(id);
        if (bill == null) throw new BusinessException("预收款单不存在");
        if (bill.getUsedAmount() != null && bill.getUsedAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("已核销的预收款单不能作废，请先反核销");
        }
        super.voidBill(id, com.erp.common.bill.BillApprovalAdapter.currentSubmitter(), null);
    }

    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        super.voidBill(id, operator, reason);
    }

    @Override
    @Transactional
    public void writeOff(Long id, List<SalePreReceiptItem> items) {
        SalePreReceipt preReceipt = super.getById(id);
        if (preReceipt == null) {
            throw new BusinessException("预收款单不存在");
        }
        if (preReceipt.getStatus() == null || preReceipt.getStatus() != 2) {
            throw new BusinessException("只有已生效状态可核销，当前状态: " + preReceipt.getStatus());
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException("核销明细不能为空");
        }
        BigDecimal totalWriteOffAmount = BigDecimal.ZERO;
        for (SalePreReceiptItem item : items) {
            if (item.getAmount() == null) {
                throw new BusinessException("核销金额不能为空");
            }
            if (item.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("核销金额必须大于0");
            }
            totalWriteOffAmount = totalWriteOffAmount.add(item.getAmount());
        }
        BigDecimal remainAmount = preReceipt.getRemainAmount() != null ? preReceipt.getRemainAmount() : preReceipt.getAmount();
        if (totalWriteOffAmount.compareTo(remainAmount) > 0) {
            throw new BusinessException("核销金额不能大于剩余金额，剩余金额: " + remainAmount);
        }
        for (SalePreReceiptItem item : items) {
            item.setPreReceiptId(id);
            item.setCreateTime(LocalDateTime.now());
            salePreReceiptItemMapper.insert(item);
        }
        BigDecimal newUsedAmount = preReceipt.getUsedAmount() != null ? preReceipt.getUsedAmount() : BigDecimal.ZERO;
        newUsedAmount = newUsedAmount.add(totalWriteOffAmount);
        BigDecimal newRemainAmount = preReceipt.getAmount().subtract(newUsedAmount);
        preReceipt.setUsedAmount(newUsedAmount);
        preReceipt.setRemainAmount(newRemainAmount);
        if (newRemainAmount.compareTo(BigDecimal.ZERO) == 0) {
            preReceipt.setStatus(3);
        }
        preReceipt.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(preReceipt);
        log.info("核销预收款单: {} 核销金额: {}", preReceipt.getReceiptNo(), totalWriteOffAmount);
    }

    /** 来源标记：销售订单定金自动生成的预收款单 */
    public static final String SOURCE_SALE_ORDER_DEPOSIT = "SALE_ORDER_DEPOSIT";

    /**
     * 销售订单审核通过后按订单定金自动生成草稿销售预收款单（幂等，草稿待财务确认提交/审核）。
     * 与人工新增（savePreReceipt）的区别：不触发免审批自动生效，始终落草稿；create_by=当前操作人。
     */
    @Override
    @Transactional
    public SalePreReceipt createFromSaleOrder(SaleOrder order) {
        if (order == null || order.getOrderId() == null) return null;
        BigDecimal amt = order.getDepositAmount();
        if (amt == null || amt.compareTo(BigDecimal.ZERO) <= 0) return null;
        if (!sysSerialNoConfigService.isAutoGenerate("SALE_PRE_RECEIPT")) {
            log.warn("销售预收款单已关闭自动编号，跳过订单[{}]定金的自动预收生成", order.getOrderNo());
            return null;
        }
        // 幂等：该订单已有来源=定金自动生成的预收（任何状态），不再重复生成
        Long exist = count(new QueryWrapper<SalePreReceipt>()
                .eq("order_id", order.getOrderId()).eq("source_type", SOURCE_SALE_ORDER_DEPOSIT));
        if (exist != null && exist > 0) {
            return null;
        }
        SalePreReceipt p = new SalePreReceipt();
        p.setReceiptNo(generateReceiptNo());
        p.setCustomerId(order.getCustomerId());
        p.setCustomerName(order.getCustomerName());
        p.setOrderId(order.getOrderId());
        p.setOrderNo(order.getOrderNo());
        p.setReceiptDate(LocalDate.now());
        p.setAmount(amt);
        BigDecimal rate = order.getExchangeRate() != null ? order.getExchangeRate() : BigDecimal.ONE;
        p.setExchangeRate(rate);
        p.setBaseAmount(amt.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP));
        p.setCurrencyCode(order.getCurrencyCode());
        p.setPaymentMethod(order.getPaymentMethod());
        p.setAccountId(order.getAccountId());
        p.setAccountName(order.getAccountName());
        p.setStatus(0);
        p.setUsedAmount(BigDecimal.ZERO);
        p.setRemainAmount(amt);
        p.setSourceType(SOURCE_SALE_ORDER_DEPOSIT);
        p.setCreateBy(resolveCurrentUsername());
        p.setCreateTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        save(p);
        // 自动单必须补一条收款明细（账户行），保证财务打开单据明细不为空；
        // 账户允许为空 —— 之前用 accountId != null 做守卫会导致"无账户时明细为 0 行、打开即空白"
        if (p.getId() != null) {
            String accountNo = null;
            if (order.getAccountId() != null) {
                try {
                    BankAccount acc = bankAccountService.getById(order.getAccountId());
                    if (acc != null) accountNo = acc.getAccountNo();
                } catch (Exception ex) {
                    log.warn("读取收款账户[{}]账号失败: {}", order.getAccountId(), ex.getMessage());
                }
            }
            SalePreReceiptItem it = new SalePreReceiptItem();
            it.setAccountId(order.getAccountId());
            it.setAccountName(order.getAccountName());
            it.setAccountNo(accountNo);
            it.setAmount(amt);
            saveReceiptItems(p.getId(), java.util.List.of(it));
        }
        log.info("销售订单[{}]定金{}{}自动生成草稿销售预收款单[{}]", order.getOrderNo(), amt,
                order.getCustomerName(), p.getReceiptNo());
        return p;
    }

    /**
     * 销售订单反审联动：删除该订单定金自动生成的草稿预收单（连同收款明细）。
     * 若该订单的自动预收单已生效（非草稿），抛出 BusinessException 阻止订单反审，
     * 由调用方（订单反审事务）整体回滚。
     */
    @Override
    @Transactional
    public int rollbackAutoByOrder(Long orderId) {
        if (orderId == null) return 0;
        QueryWrapper<SalePreReceipt> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", orderId).eq("source_type", SOURCE_SALE_ORDER_DEPOSIT);
        List<SalePreReceipt> autos = list(wrapper);
        if (autos == null || autos.isEmpty()) return 0;
        for (SalePreReceipt r : autos) {
            if (r.getStatus() != null && r.getStatus() != 0) {
                throw new BusinessException("销售订单已生成已生效的销售预收款单[" + r.getReceiptNo() + "]，请先在财务模块处理该预收款单，再重置草稿订单");
            }
        }
        int removed = 0;
        for (SalePreReceipt r : autos) {
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

    @Override
    @Transactional
    public void reverseWriteOff(Long id, BigDecimal amount) {
        SalePreReceipt preReceipt = super.getById(id);
        if (preReceipt == null) {
            throw new BusinessException("预收款单不存在");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal used = preReceipt.getUsedAmount() == null ? BigDecimal.ZERO : preReceipt.getUsedAmount();
        BigDecimal newUsed = used.subtract(amount);
        if (newUsed.compareTo(BigDecimal.ZERO) < 0) {
            newUsed = BigDecimal.ZERO;
        }
        preReceipt.setUsedAmount(newUsed);
        preReceipt.setRemainAmount(preReceipt.getAmount().subtract(newUsed));
        // 核销完(3)回退到已生效(2)，恢复可继续核销
        if (Integer.valueOf(3).equals(preReceipt.getStatus()) && newUsed.compareTo(BigDecimal.ZERO) > 0) {
            preReceipt.setStatus(2);
        }
        preReceipt.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(preReceipt);
        log.info("反核销预收款单: {} 回减金额: {}", preReceipt.getReceiptNo(), amount);
    }

    @Override
    @Transactional
    public boolean delete(Long id) {
        SalePreReceipt preReceipt = super.getById(id);
        if (preReceipt == null) {
            return true;
        }
        // 草稿校验 + 下游校验 + 整单级联删除由统一状态机执行
        super.deleteBill(id);
        log.info("删除预收款单: {}", preReceipt.getReceiptNo());
        return true;
    }

    private String generateReceiptNo() {
        // 优先走设置中心自动编号规则（SALE_PRE_RECEIPT），未配置或规则异常时退回内置默认规则
        String docType = "SALE_PRE_RECEIPT";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "SPR-" + dateStr + "-";
            QueryWrapper<SalePreReceipt> wrapper = new QueryWrapper<>();
            wrapper.likeRight("receipt_no", prefix).orderByDesc("receipt_no").last("LIMIT 1");
            SalePreReceipt last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getReceiptNo() != null) {
                try {
                    seq = Integer.parseInt(last.getReceiptNo().substring(prefix.length())) + 1;
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
        update(new UpdateWrapper<SalePreReceipt>().eq("id", id)
                .set("reject_reason", reason)
                .set("rejector", rejector)
                .set("rejected_at", LocalDateTime.now()));
        log.info("驳回预收款单: {} 操作人: {} 原因: {}", id, rejector, reason);
    }

    /** 重置草稿：已核销金额>0 不允许重置（否则核销明细与单据状态不一致）；其余由统一状态机执行 */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        SalePreReceipt bill = super.getById(id);
        if (bill == null) {
            throw new BusinessException("预收款单不存在");
        }
        if (bill.getUsedAmount() != null && bill.getUsedAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("已核销的预收款单不能重置草稿，请先反核销");
        }
        super.reverseApprove(id, operator);
        update(new UpdateWrapper<SalePreReceipt>().eq("id", id)
                .set("approved_by", null).set("approved_at", null)
                .set("reverse_approver", operator)
                .set("reverse_approved_at", LocalDateTime.now()));
        log.info("重置草稿预收款单: {} 操作人: {}", id, operator);
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
