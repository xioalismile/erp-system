package com.erp.finance.purchasebill.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillStatusEnum;
import com.erp.common.bill.BillStatusUtils;
import com.erp.config.BusinessException;
import com.erp.finance.entity.PaymentVoucher;
import com.erp.finance.mapper.PaymentVoucherMapper;
import com.erp.finance.purchasebill.entity.PurchaseStatement;
import com.erp.finance.purchasebill.entity.PurchaseStatementItem;
import com.erp.finance.purchasebill.mapper.PurchaseStatementItemMapper;
import com.erp.finance.purchasebill.mapper.PurchaseStatementMapper;
import com.erp.purchase.entity.PurchaseInbound;
import com.erp.purchase.entity.PurchaseInvoice;
import com.erp.purchase.mapper.PurchaseInboundMapper;
import com.erp.purchase.mapper.PurchaseInvoiceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;

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

public class PurchaseStatementServiceImpl extends ServiceImpl<PurchaseStatementMapper, PurchaseStatement>
        implements PurchaseStatementService {

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "create_by",
            "create_time",
            "created_at",
            "end_date",
            "id",
            "invoice_date",
            "paid_amount",
            "payment_date",
            "receipt_date",
            "remark",
            "source_date",
            "start_date",
            "statement_id",
            "statement_no",
            "status",
            "supplier_id",
            "supplier_name",
            "total_amount",
            "unpaid_amount",
            "update_time"
        );

    private final PurchaseStatementItemMapper purchaseStatementItemMapper;
    private final PurchaseInboundMapper purchaseInboundMapper;
    private final PurchaseInvoiceMapper purchaseInvoiceMapper;
    private final PaymentVoucherMapper paymentVoucherMapper;

    /** 整单删除级联服务（删主表前清理明细，2026-09-19） */
    private final BillCascadeDeleteService billCascadeDeleteService;

    @Override
    public PageResult<PurchaseStatement> pageQuery(int current, int size, String keyword, Integer status, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder) {
        QueryWrapper<PurchaseStatement> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("statement_no", keyword)
                    .or().like("supplier_name", keyword)
                    .or().like("remark", keyword));
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        // 数据权限（2026-09-02 配置驱动）：purchase_statement 表使用 create_by 列存储创建人
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null, "create_by");
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PurchaseStatement.class);
        Page<PurchaseStatement> page = new Page<>(current, size);
        Page<PurchaseStatement> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public PurchaseStatement getDetail(Long id) {
        PurchaseStatement statement = super.getById(id);
        if (statement == null) {
            throw new BusinessException("采购对账单不存在");
        }
        QueryWrapper<PurchaseStatementItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("statement_id", id);
        itemWrapper.orderByAsc("source_date", "id");
        List<PurchaseStatementItem> items = purchaseStatementItemMapper.selectList(itemWrapper);
        statement.setItems(items);
        return statement;
    }

    @Override
    @Transactional
    public PurchaseStatement createStatement(Long supplierId, LocalDate startDate, LocalDate endDate) {
        if (supplierId == null) {
            throw new BusinessException("供应商ID不能为空");
        }
        if (startDate == null || endDate == null) {
            throw new BusinessException("对账期间不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }

        PurchaseStatement statement = new PurchaseStatement();
        statement.setSupplierId(supplierId);
        statement.setStartDate(startDate);
        statement.setEndDate(endDate);
        statement.setStatus(0);
        statement.setStatementNo(generateStatementNo());
        statement.setCreateTime(LocalDateTime.now());
        statement.setUpdateTime(LocalDateTime.now());

        List<PurchaseStatementItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;

        QueryWrapper<PurchaseInbound> inboundWrapper = new QueryWrapper<>();
        inboundWrapper.eq("supplier_id", supplierId);
        inboundWrapper.between("receipt_date", startDate, endDate);
        inboundWrapper.eq("status", "已生效");
        List<PurchaseInbound> inbounds = purchaseInboundMapper.selectList(inboundWrapper);
        String supplierName = null;
        for (PurchaseInbound inbound : inbounds) {
            if (supplierName == null && inbound.getSupplierName() != null) {
                supplierName = inbound.getSupplierName();
            }
            PurchaseStatementItem item = new PurchaseStatementItem();
            item.setSourceId(inbound.getReceiptId());
            item.setSourceNo(inbound.getReceiptNo());
            item.setSourceType("PURCHASE_INBOUND");
            item.setSourceDate(inbound.getInDate());
            // 2026-11-07：质检退货时应付按 payable_amount（已扣减不合格自动退货部分），未质检时回退单据总额
            BigDecimal inboundAmount = inbound.getPayableAmount() != null
                    ? inbound.getPayableAmount()
                    : (inbound.getTotalAmount() != null ? inbound.getTotalAmount() : BigDecimal.ZERO);
            item.setAmount(inboundAmount);
            item.setPaidAmount(BigDecimal.ZERO);
            item.setUnpaidAmount(item.getAmount());
            items.add(item);
            totalAmount = totalAmount.add(item.getAmount());
        }

        QueryWrapper<PurchaseInvoice> invoiceWrapper = new QueryWrapper<>();
        invoiceWrapper.eq("supplier_id", supplierId);
        invoiceWrapper.between("invoice_date", startDate, endDate);
        invoiceWrapper.eq("status", "已生效");
        List<PurchaseInvoice> invoices = purchaseInvoiceMapper.selectList(invoiceWrapper);
        for (PurchaseInvoice invoice : invoices) {
            if (supplierName == null && invoice.getSupplierName() != null) {
                supplierName = invoice.getSupplierName();
            }
            PurchaseStatementItem item = new PurchaseStatementItem();
            item.setSourceId(invoice.getInvoiceId());
            item.setSourceNo(invoice.getInvoiceNo());
            item.setSourceType("PURCHASE_INVOICE");
            item.setSourceDate(invoice.getInvoiceDate());
            BigDecimal amount = invoice.getTaxTotalAmount() != null ? invoice.getTaxTotalAmount() :
                    (invoice.getAmount() != null ? invoice.getAmount() : BigDecimal.ZERO);
            item.setAmount(amount);
            item.setPaidAmount(BigDecimal.ZERO);
            item.setUnpaidAmount(item.getAmount());
            items.add(item);
            totalAmount = totalAmount.add(item.getAmount());
        }

        QueryWrapper<PaymentVoucher> paymentWrapper = new QueryWrapper<>();
        paymentWrapper.eq("supplier_id", supplierId);
        paymentWrapper.between("payment_date", startDate, endDate);
        paymentWrapper.eq("status", "已生效");
        List<PaymentVoucher> payments = paymentVoucherMapper.selectList(paymentWrapper);
        for (PaymentVoucher payment : payments) {
            if (supplierName == null && payment.getSupplierName() != null) {
                supplierName = payment.getSupplierName();
            }
            PurchaseStatementItem item = new PurchaseStatementItem();
            item.setSourceId(payment.getPaymentId());
            item.setSourceNo(payment.getPaymentNo());
            item.setSourceType("PAYMENT");
            item.setSourceDate(payment.getPaymentDate());
            BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
            item.setAmount(amount.negate());
            item.setPaidAmount(amount);
            item.setUnpaidAmount(BigDecimal.ZERO);
            items.add(item);
            totalPaid = totalPaid.add(amount);
        }

        statement.setSupplierName(supplierName != null ? supplierName : "");
        statement.setTotalAmount(totalAmount);
        statement.setPaidAmount(totalPaid);
        statement.setUnpaidAmount(totalAmount.subtract(totalPaid));

        baseMapper.insert(statement);

        for (PurchaseStatementItem item : items) {
            item.setStatementId(statement.getId());
            purchaseStatementItemMapper.insert(item);
        }

        statement.setItems(items);
        log.info("自动生成采购对账单: {} 供应商: {}", statement.getStatementNo(), supplierName);
        return statement;
    }

    @Override
    @Transactional
    public void confirm(Long id) {
        PurchaseStatement statement = super.getById(id);
        if (statement == null) {
            throw new BusinessException("采购对账单不存在");
        }
        if (statement.getStatus() != null && statement.getStatus() != 0 && statement.getStatus() != 1) {
            throw new BusinessException("只有草稿或待确认状态可确认，当前状态: " + statement.getStatus());
        }
        statement.setStatus(2);
        statement.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(statement);
        log.info("确认采购对账单: {}", statement.getStatementNo());
    }

    @Override
    @Transactional
    public void close(Long id) {
        PurchaseStatement statement = super.getById(id);
        if (statement == null) {
            throw new BusinessException("采购对账单不存在");
        }
        if (statement.getStatus() != null && statement.getStatus() != 2) {
            throw new BusinessException("只有已确认状态可关闭，当前状态: " + statement.getStatus());
        }
        statement.setStatus(3);
        statement.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(statement);
        log.info("关闭采购对账单: {}", statement.getStatementNo());
    }

    @Override
    @Transactional
    public PurchaseStatement saveStatement(PurchaseStatement statement, List<PurchaseStatementItem> items) {
        if (statement.getId() == null) {
            if (statement.getStatementNo() == null || statement.getStatementNo().isBlank()) {
                statement.setStatementNo(generateStatementNo());
            }
            if (statement.getStatus() == null) {
                statement.setStatus(0);
            }
            statement.setCreateTime(LocalDateTime.now());
            statement.setUpdateTime(LocalDateTime.now());
            baseMapper.insert(statement);
            if (items != null) {
                for (PurchaseStatementItem item : items) {
                    item.setStatementId(statement.getId());
                    purchaseStatementItemMapper.insert(item);
                }
            }
            log.info("新增采购对账单: {}", statement.getStatementNo());
        } else {
            PurchaseStatement existing = super.getById(statement.getId());
            if (existing == null) {
                throw new BusinessException("采购对账单不存在");
            }
            if (existing.getStatus() != null && existing.getStatus() != 0) {
                throw new BusinessException("只有草稿状态可修改，当前状态: " + existing.getStatus());
            }
            statement.setUpdateTime(LocalDateTime.now());
            baseMapper.updateById(statement);
            QueryWrapper<PurchaseStatementItem> deleteWrapper = new QueryWrapper<>();
            deleteWrapper.eq("statement_id", statement.getId());
            purchaseStatementItemMapper.delete(deleteWrapper);
            if (items != null) {
                for (PurchaseStatementItem item : items) {
                    item.setStatementId(statement.getId());
                    item.setId(null);
                    purchaseStatementItemMapper.insert(item);
                }
            }
            log.info("修改采购对账单: {}", statement.getStatementNo());
        }
        return getDetail(statement.getId());
    }

    @Override
    @Transactional
    public boolean delete(Long id) {
        PurchaseStatement statement = super.getById(id);
        if (statement == null) {
            throw new BusinessException("采购对账单不存在");
        }
        if (!BillStatusEnum.isDraftClass(BillStatusUtils.toNumber(statement.getStatus()))) {
            throw new BusinessException("非草稿不能删除（当前状态：" + statement.getStatus() + "），请先重置草稿");
        }
        QueryWrapper<PurchaseStatementItem> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("statement_id", id);
        purchaseStatementItemMapper.delete(deleteWrapper);
        // 【2026-09-19 整单删除】先清明细/从表，再删主表（用户硬要求：不允许留孤儿明细）
        billCascadeDeleteService.deleteChildren("purchase_statement", id);
        boolean ok = removeById(id);
        log.info("删除采购对账单: {} 结果: {}", statement.getStatementNo(), ok);
        return ok;
    }

    private String generateStatementNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "PS-" + dateStr + "-";
        QueryWrapper<PurchaseStatement> wrapper = new QueryWrapper<>();
        wrapper.likeRight("statement_no", prefix).orderByDesc("statement_no").last("LIMIT 1");
        PurchaseStatement last = getOne(wrapper);
        int seq = 1;
        if (last != null && last.getStatementNo() != null) {
            try {
                seq = Integer.parseInt(last.getStatementNo().substring(prefix.length())) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }
}
