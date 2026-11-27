package com.erp.finance.expense.sale.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.common.bill.ApprovalGateHelper;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillStatusEnum;
import com.erp.common.bill.BillStatusUtils;
import com.erp.config.BusinessException;
import com.erp.finance.expense.sale.entity.SaleExpense;
import com.erp.finance.expense.sale.entity.SaleExpenseItem;
import com.erp.finance.expense.sale.mapper.SaleExpenseItemMapper;
import com.erp.finance.expense.sale.mapper.SaleExpenseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class SaleExpenseServiceImpl extends com.erp.common.bill.AbstractBillService<SaleExpenseMapper, SaleExpense>
        implements SaleExpenseService {

    /** 下游引用校验（统一状态机）：重置草稿/作废/删除前禁止存在已生效下游 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.bill.BillRelationService billRelationService;


    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "base_amount",
            "cost_sharing_method",
            "create_by",
            "create_time",
            "created_at",
            "currency_code",
            "customer_id",
            "customer_name",
            "exchange_rate",
            "expense_date",
            "expense_id",
            "expense_no",
            "expense_type_id",
            "expense_type_name",
            "id",
            "related_doc_id",
            "related_doc_no",
            "related_doc_type",
            "remark",
            "status",
            "total_amount",
            "update_time"
        );

    private final SaleExpenseItemMapper saleExpenseItemMapper;
    private final ApprovalGateHelper approvalGateHelper;

    /** 整单删除级联服务（删主表前清理明细，2026-09-19） */
    private final BillCascadeDeleteService billCascadeDeleteService;

    @Override
    public PageResult<SaleExpense> pageQuery(int current, int size, String keyword, Integer status, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder) {
        QueryWrapper<SaleExpense> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("expense_no", keyword)
                    .or().like("customer_name", keyword)
                    .or().like("expense_type_name", keyword)
                    .or().like("related_doc_no", keyword)
                    .or().like("remark", keyword));
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        // 数据权限（2026-09-02 配置驱动）：sale_expense 表使用 create_by 列存储创建人
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null, "create_by");
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SaleExpense.class);
        Page<SaleExpense> page = new Page<>(current, size);
        Page<SaleExpense> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SaleExpense getDetail(Long id) {
        SaleExpense expense = super.getById(id);
        if (expense == null) {
            throw new BusinessException("销售费用单不存在");
        }
        QueryWrapper<SaleExpenseItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("expense_id", id);
        itemWrapper.orderByAsc("id");
        List<SaleExpenseItem> items = saleExpenseItemMapper.selectList(itemWrapper);
        expense.setItems(items);
        return expense;
    }

    @Override
    @Transactional
    public SaleExpense saveExpense(SaleExpense expense, List<SaleExpenseItem> items) {
        if (expense.getId() == null) {
            if (expense.getExpenseNo() == null || expense.getExpenseNo().isBlank()) {
                expense.setExpenseNo(generateExpenseNo());
            }
            // 状态初始化与落库统一走状态机（保存=草稿），保存方法内不直接写状态
            if (expense.getStatus() == null) setStatus(expense, "草稿");
            if (expense.getApprovalStatus() == null) setApprovalStatus(expense, "草稿");
            if (expense.getTotalAmount() == null) {
                expense.setTotalAmount(BigDecimal.ZERO);
            }
            if (expense.getBaseAmount() == null && expense.getTotalAmount() != null) {
                if (expense.getExchangeRate() != null) {
                    expense.setBaseAmount(expense.getTotalAmount().multiply(expense.getExchangeRate()));
                } else {
                    expense.setBaseAmount(expense.getTotalAmount());
                }
            }
            expense.setCreateTime(LocalDateTime.now());
            expense.setUpdateTime(LocalDateTime.now());
            saveDraft(expense);

            if (items != null && !items.isEmpty()) {
                for (SaleExpenseItem item : items) {
                    item.setExpenseId(expense.getId());
                    item.setCreateTime(LocalDateTime.now());
                    saleExpenseItemMapper.insert(item);
                }
            }
            log.info("新增销售费用单: {}", expense.getExpenseNo());
            // 口径统一（2026-09-21 用户指令「按标准来」）：保存=草稿，保存链路里绝不自动审核；生效只走提交侧。
        } else {
            SaleExpense existing = super.getById(expense.getId());
            if (existing == null) {
                throw new BusinessException("销售费用单不存在");
            }
            if (existing.getStatus() != null && existing.getStatus() != 0) {
                throw new BusinessException("只有草稿状态可修改，当前状态: " + existing.getStatus());
            }
            if (expense.getTotalAmount() == null) {
                expense.setTotalAmount(BigDecimal.ZERO);
            }
            if (expense.getBaseAmount() == null && expense.getTotalAmount() != null) {
                if (expense.getExchangeRate() != null) {
                    expense.setBaseAmount(expense.getTotalAmount().multiply(expense.getExchangeRate()));
                } else {
                    expense.setBaseAmount(expense.getTotalAmount());
                }
            }
            expense.setUpdateTime(LocalDateTime.now());
            baseMapper.updateById(expense);

            QueryWrapper<SaleExpenseItem> deleteWrapper = new QueryWrapper<>();
            deleteWrapper.eq("expense_id", expense.getId());
            saleExpenseItemMapper.delete(deleteWrapper);

            if (items != null && !items.isEmpty()) {
                for (SaleExpenseItem item : items) {
                    item.setId(null);
                    item.setExpenseId(expense.getId());
                    item.setCreateTime(LocalDateTime.now());
                    saleExpenseItemMapper.insert(item);
                }
            }
            log.info("修改销售费用单: {}", expense.getExpenseNo());
        }
        return getDetail(expense.getId());
    }

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "销售费用单"; }
    @Override protected String getApprovalBillType() { return "SALE_EXPENSE"; }
    @Override protected String getBillNo(SaleExpense b) { return b.getExpenseNo(); }
    @Override protected Object getId(SaleExpense b) { return b.getId(); }
    @Override protected String getStatus(SaleExpense b) { return toStatusLabel(b.getStatus()); }
    @Override protected void setStatus(SaleExpense b, String s) { b.setStatus(toStatusCode(s)); }
    @Override protected Integer getCloseStatus(SaleExpense b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(SaleExpense b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(SaleExpense b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(SaleExpense b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(SaleExpense b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(SaleExpense b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(SaleExpense b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(SaleExpense b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(SaleExpense b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(SaleExpense b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(SaleExpense b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(SaleExpense b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(SaleExpense b, String v) { b.setVoidReason(v); }

    /** 数字状态 ↔ 统一状态文案（0草稿/1待审核/2已生效/3已分摊(按生效类)/4已作废） */
    private static String toStatusLabel(Integer v) {
        if (v == null) return "草稿";
        return switch (v) {
            case 0 -> "草稿";
            case 1 -> "待审核";
            case 4 -> "已作废";
            default -> "已生效";
        };
    }

    private static Integer toStatusCode(String label) {
        if (label == null) return 0;
        return switch (label) {
            case "草稿", "已驳回", "已取消", "驳回" -> 0;
            case "待审核", "待审批", "审批中" -> 1;
            case "已作废", "作废" -> 4;
            default -> 2;
        };
    }

    @Override
    public List<String> listApprovedDownstreamBills(SaleExpense bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getExpenseNo());
    }

    @Override
    protected void validateStrictSubmit(SaleExpense expense) {
        // 销售费用单原审核入口无额外强校验；保持行为不变（保存=草稿宽松）
    }

    /**
     * 审核入口兼容（前端「提交/审核」共用本方法）：
     * 草稿 → 统一状态机提交（免审批即生效 / 开启审批转待审核）；待审核 → 统一状态机审核。
     */
    @Override
    @Transactional
    public void audit(Long id) {
        SaleExpense expense = super.getById(id);
        if (expense == null) {
            throw new BusinessException("销售费用单不存在");
        }
        Integer st = expense.getStatus();
        if (st == null || st == 0) {
            super.submitForApproval(expense, com.erp.common.bill.BillApprovalAdapter.currentSubmitter());
            return;
        }
        if (st == 1) {
            approve(id, com.erp.common.bill.BillApprovalAdapter.currentSubmitter());
            return;
        }
        throw new BusinessException("只有草稿或待审核状态可审核，当前状态: " + st);
    }

    /** 审核：审批在途禁止绕过引擎 + 状态迁移由统一状态机执行 */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("SALE_EXPENSE", (Long) id) != null) {
            throw new BusinessException("销售费用单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    @Override
    public void onApproved(SaleExpense expense) {
        log.info("销售费用单审核生效: {}", expense.getExpenseNo());
    }

    /** 作废：已分摊(3)不允许作废；状态校验/下游校验/作废前状态记录由统一状态机执行 */
    @Override
    @Transactional
    public void voidExpense(Long id) {
        SaleExpense expense = super.getById(id);
        if (expense == null) throw new BusinessException("销售费用单不存在");
        if (expense.getStatus() != null && expense.getStatus() == 3) {
            throw new BusinessException("已分摊的销售费用单不能作废，请先反分摊");
        }
        super.voidBill(id, com.erp.common.bill.BillApprovalAdapter.currentSubmitter(), null);
    }

    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        super.voidBill(id, operator, reason);
    }

    @Transactional
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }

    @Override
    @Transactional
    public void allocateCost(Long id) {
        SaleExpense expense = super.getById(id);
        if (expense == null) {
            throw new BusinessException("销售费用单不存在");
        }
        if (expense.getStatus() == null || expense.getStatus() != 2) {
            throw new BusinessException("只有已生效状态可分摊，当前状态: " + expense.getStatus());
        }
        if (expense.getCostSharingMethod() == null || "NONE".equals(expense.getCostSharingMethod())) {
            throw new BusinessException("当前费用单不分摊成本");
        }

        QueryWrapper<SaleExpenseItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("expense_id", id);
        List<SaleExpenseItem> items = saleExpenseItemMapper.selectList(itemWrapper);

        if (items == null || items.isEmpty()) {
            throw new BusinessException("费用明细为空，无法分摊");
        }

        BigDecimal totalAmount = expense.getTotalAmount();
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("费用总金额必须大于0");
        }

        BigDecimal totalBase = BigDecimal.ZERO;
        switch (expense.getCostSharingMethod()) {
            case "AMOUNT":
                for (SaleExpenseItem item : items) {
                    if (item.getAmount() != null) {
                        totalBase = totalBase.add(item.getAmount());
                    }
                }
                break;
            case "QUANTITY":
                for (SaleExpenseItem item : items) {
                    if (item.getQuantity() != null) {
                        totalBase = totalBase.add(item.getQuantity());
                    }
                }
                break;
            case "WEIGHT":
                throw new BusinessException("按重量分摊需要商品重量数据，暂未实现");
            default:
                throw new BusinessException("不支持的分摊方式: " + expense.getCostSharingMethod());
        }

        if (totalBase.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("分摊基数为0，无法分摊");
        }

        BigDecimal allocatedAmount = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            SaleExpenseItem item = items.get(i);
            BigDecimal baseValue = BigDecimal.ZERO;
            switch (expense.getCostSharingMethod()) {
                case "AMOUNT":
                    baseValue = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
                    break;
                case "QUANTITY":
                    baseValue = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                    break;
            }

            BigDecimal ratio = baseValue.divide(totalBase, 6, RoundingMode.HALF_UP);
            BigDecimal sharingAmount;

            if (i == items.size() - 1) {
                sharingAmount = totalAmount.subtract(allocatedAmount);
            } else {
                sharingAmount = totalAmount.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                allocatedAmount = allocatedAmount.add(sharingAmount);
            }

            item.setSharingRatio(ratio.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP));
            item.setSharingAmount(sharingAmount);
            saleExpenseItemMapper.updateById(item);
        }

        expense.setStatus(3);
        expense.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(expense);

        log.info("分摊销售费用单: {} 分摊总金额: {}", expense.getExpenseNo(), totalAmount);
    }

    @Override
    @Transactional
    public boolean delete(Long id) {
        SaleExpense expense = super.getById(id);
        if (expense == null) {
            return true;
        }
        // 草稿校验 + 下游校验 + 整单级联删除由统一状态机执行
        super.deleteBill(id);
        log.info("删除销售费用单: {}", expense.getExpenseNo());
        return true;
    }

    private String generateExpenseNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "SE-" + dateStr + "-";
        QueryWrapper<SaleExpense> wrapper = new QueryWrapper<>();
        wrapper.likeRight("expense_no", prefix).orderByDesc("expense_no").last("LIMIT 1");
        SaleExpense last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getExpenseNo() != null) {
            try {
                seq = Integer.parseInt(last.getExpenseNo().substring(prefix.length())) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }

    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
    }

    @Transactional
    public void reverseApprove(Long id, String operator) {
        SaleExpense bill = super.getById(id);
        if (bill == null) {
            throw new BusinessException("销售费用单不存在");
        }
        if (bill.getStatus() != null && bill.getStatus() == 3) {
            throw new BusinessException("已分摊的销售费用单不能重置草稿，请先反分摊");
        }
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        update(new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SaleExpense>()
                .eq("id", id).set("approved_by", null).set("approved_at", null));
    }

    /** 结案：状态校验/结案字段由统一状态机执行 */
    @Transactional
    public void closeOrder(Long id, String operator) {
        super.close(id, operator, "手动结案");
    }

    @Transactional
    public void reopenOrder(Long id, String operator) {
        super.reopen(id, operator);
    }

    @Transactional
    public void deleteBill(Long id) {
        super.deleteBill(id);
    }
}
