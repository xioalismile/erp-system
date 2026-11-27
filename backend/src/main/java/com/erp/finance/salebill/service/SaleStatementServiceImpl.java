package com.erp.finance.salebill.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillStatusEnum;
import com.erp.common.bill.BillStatusUtils;
import com.erp.config.BusinessException;
import com.erp.finance.entity.PaymentReceipt;
import com.erp.finance.mapper.PaymentReceiptMapper;
import com.erp.finance.salebill.entity.SaleStatement;
import com.erp.finance.salebill.entity.SaleStatementItem;
import com.erp.finance.salebill.mapper.SaleStatementItemMapper;
import com.erp.finance.salebill.mapper.SaleStatementMapper;
import com.erp.sale.entity.SaleDelivery;
import com.erp.sale.entity.SaleInvoice;
import com.erp.sale.mapper.SaleDeliveryMapper;
import com.erp.sale.mapper.SaleInvoiceMapper;
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

public class SaleStatementServiceImpl extends ServiceImpl<SaleStatementMapper, SaleStatement>
        implements SaleStatementService {

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "create_by",
            "create_time",
            "created_at",
            "customer_id",
            "customer_name",
            "delivery_date",
            "end_date",
            "id",
            "invoice_date",
            "receipt_date",
            "received_amount",
            "remark",
            "source_date",
            "start_date",
            "statement_id",
            "statement_no",
            "status",
            "total_amount",
            "unpaid_amount",
            "update_time"
        );

    private final SaleStatementItemMapper saleStatementItemMapper;
    private final SaleDeliveryMapper saleDeliveryMapper;
    private final SaleInvoiceMapper saleInvoiceMapper;
    private final PaymentReceiptMapper paymentReceiptMapper;

    /** 整单删除级联服务（删主表前清理明细，2026-09-19） */
    private final BillCascadeDeleteService billCascadeDeleteService;

    @Override
    public PageResult<SaleStatement> pageQuery(int current, int size, String keyword, Integer status, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder) {
        QueryWrapper<SaleStatement> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("statement_no", keyword)
                    .or().like("customer_name", keyword)
                    .or().like("remark", keyword));
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        // 数据权限（2026-09-02 配置驱动）：sale_statement 表使用 create_by 列存储创建人
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null, "create_by");
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SaleStatement.class);
        Page<SaleStatement> page = new Page<>(current, size);
        Page<SaleStatement> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SaleStatement getDetail(Long id) {
        SaleStatement statement = super.getById(id);
        if (statement == null) {
            throw new BusinessException("销售对账单不存在");
        }
        QueryWrapper<SaleStatementItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("statement_id", id);
        itemWrapper.orderByAsc("source_date", "id");
        List<SaleStatementItem> items = saleStatementItemMapper.selectList(itemWrapper);
        statement.setItems(items);
        return statement;
    }

    @Override
    @Transactional
    public SaleStatement createStatement(Long customerId, LocalDate startDate, LocalDate endDate) {
        if (customerId == null) {
            throw new BusinessException("客户ID不能为空");
        }
        if (startDate == null || endDate == null) {
            throw new BusinessException("对账期间不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }

        SaleStatement statement = new SaleStatement();
        statement.setCustomerId(customerId);
        statement.setStartDate(startDate);
        statement.setEndDate(endDate);
        statement.setStatus(0);
        statement.setStatementNo(generateStatementNo());
        statement.setCreateTime(LocalDateTime.now());
        statement.setUpdateTime(LocalDateTime.now());

        List<SaleStatementItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalReceived = BigDecimal.ZERO;

        QueryWrapper<SaleDelivery> deliveryWrapper = new QueryWrapper<>();
        deliveryWrapper.eq("customer_id", customerId);
        deliveryWrapper.between("delivery_date", startDate, endDate);
        deliveryWrapper.eq("status", "已生效");
        List<SaleDelivery> deliveries = saleDeliveryMapper.selectList(deliveryWrapper);
        String customerName = null;
        for (SaleDelivery delivery : deliveries) {
            if (customerName == null && delivery.getCustomerName() != null) {
                customerName = delivery.getCustomerName();
            }
            SaleStatementItem item = new SaleStatementItem();
            item.setSourceId(delivery.getDeliveryId());
            item.setSourceNo(delivery.getDeliveryNo());
            item.setSourceType("SALE_DELIVERY");
            item.setSourceDate(delivery.getDeliveryDate());
            item.setAmount(delivery.getNetAmount() != null ? delivery.getNetAmount() : BigDecimal.ZERO);
            item.setReceivedAmount(BigDecimal.ZERO);
            item.setUnpaidAmount(item.getAmount());
            items.add(item);
            totalAmount = totalAmount.add(item.getAmount());
        }

        QueryWrapper<SaleInvoice> invoiceWrapper = new QueryWrapper<>();
        invoiceWrapper.eq("customer_id", customerId);
        invoiceWrapper.between("invoice_date", startDate, endDate);
        invoiceWrapper.eq("status", "已生效");
        List<SaleInvoice> invoices = saleInvoiceMapper.selectList(invoiceWrapper);
        for (SaleInvoice invoice : invoices) {
            if (customerName == null && invoice.getCustomerName() != null) {
                customerName = invoice.getCustomerName();
            }
            SaleStatementItem item = new SaleStatementItem();
            item.setSourceId(invoice.getInvoiceId());
            item.setSourceNo(invoice.getInvoiceNo());
            item.setSourceType("SALE_INVOICE");
            item.setSourceDate(invoice.getInvoiceDate());
            item.setAmount(invoice.getNetAmount() != null ? invoice.getNetAmount() : BigDecimal.ZERO);
            item.setReceivedAmount(BigDecimal.ZERO);
            item.setUnpaidAmount(item.getAmount());
            items.add(item);
            totalAmount = totalAmount.add(item.getAmount());
        }

        QueryWrapper<PaymentReceipt> receiptWrapper = new QueryWrapper<>();
        receiptWrapper.eq("customer_id", customerId);
        receiptWrapper.between("receipt_date", startDate, endDate);
        receiptWrapper.eq("status", "已生效");
        List<PaymentReceipt> receipts = paymentReceiptMapper.selectList(receiptWrapper);
        for (PaymentReceipt receipt : receipts) {
            if (customerName == null && receipt.getCustomerName() != null) {
                customerName = receipt.getCustomerName();
            }
            SaleStatementItem item = new SaleStatementItem();
            item.setSourceId(receipt.getReceiptId());
            item.setSourceNo(receipt.getReceiptNo());
            item.setSourceType("RECEIPT");
            item.setSourceDate(receipt.getReceiptDate());
            BigDecimal amount = receipt.getAmount() != null ? receipt.getAmount() : BigDecimal.ZERO;
            item.setAmount(amount.negate());
            item.setReceivedAmount(amount);
            item.setUnpaidAmount(BigDecimal.ZERO);
            items.add(item);
            totalReceived = totalReceived.add(amount);
        }

        statement.setCustomerName(customerName != null ? customerName : "");
        statement.setTotalAmount(totalAmount);
        statement.setReceivedAmount(totalReceived);
        statement.setUnpaidAmount(totalAmount.subtract(totalReceived));

        baseMapper.insert(statement);

        for (SaleStatementItem item : items) {
            item.setStatementId(statement.getId());
            saleStatementItemMapper.insert(item);
        }

        statement.setItems(items);
        log.info("自动生成销售对账单: {} 客户: {}", statement.getStatementNo(), customerName);
        return statement;
    }

    @Override
    @Transactional
    public void confirm(Long id) {
        SaleStatement statement = super.getById(id);
        if (statement == null) {
            throw new BusinessException("销售对账单不存在");
        }
        if (statement.getStatus() != null && statement.getStatus() != 0 && statement.getStatus() != 1) {
            throw new BusinessException("只有草稿或待确认状态可确认，当前状态: " + statement.getStatus());
        }
        statement.setStatus(2);
        statement.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(statement);
        log.info("确认销售对账单: {}", statement.getStatementNo());
    }

    @Override
    @Transactional
    public void close(Long id) {
        SaleStatement statement = super.getById(id);
        if (statement == null) {
            throw new BusinessException("销售对账单不存在");
        }
        if (statement.getStatus() != null && statement.getStatus() != 2) {
            throw new BusinessException("只有已确认状态可关闭，当前状态: " + statement.getStatus());
        }
        statement.setStatus(3);
        statement.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(statement);
        log.info("关闭销售对账单: {}", statement.getStatementNo());
    }

    @Override
    @Transactional
    public SaleStatement saveStatement(SaleStatement statement, List<SaleStatementItem> items) {
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
                for (SaleStatementItem item : items) {
                    item.setStatementId(statement.getId());
                    saleStatementItemMapper.insert(item);
                }
            }
            log.info("新增销售对账单: {}", statement.getStatementNo());
        } else {
            SaleStatement existing = super.getById(statement.getId());
            if (existing == null) {
                throw new BusinessException("销售对账单不存在");
            }
            if (existing.getStatus() != null && existing.getStatus() != 0) {
                throw new BusinessException("只有草稿状态可修改，当前状态: " + existing.getStatus());
            }
            statement.setUpdateTime(LocalDateTime.now());
            baseMapper.updateById(statement);
            QueryWrapper<SaleStatementItem> deleteWrapper = new QueryWrapper<>();
            deleteWrapper.eq("statement_id", statement.getId());
            saleStatementItemMapper.delete(deleteWrapper);
            if (items != null) {
                for (SaleStatementItem item : items) {
                    item.setStatementId(statement.getId());
                    item.setId(null);
                    saleStatementItemMapper.insert(item);
                }
            }
            log.info("修改销售对账单: {}", statement.getStatementNo());
        }
        return getDetail(statement.getId());
    }

    @Override
    @Transactional
    public boolean delete(Long id) {
        SaleStatement statement = super.getById(id);
        if (statement == null) {
            throw new BusinessException("销售对账单不存在");
        }
        if (!BillStatusEnum.isDraftClass(BillStatusUtils.toNumber(statement.getStatus()))) {
            throw new BusinessException("非草稿不能删除（当前状态：" + statement.getStatus() + "），请先重置草稿");
        }
        QueryWrapper<SaleStatementItem> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("statement_id", id);
        saleStatementItemMapper.delete(deleteWrapper);
        // 【2026-09-19 整单删除】先清明细/从表，再删主表（用户硬要求：不允许留孤儿明细）
        billCascadeDeleteService.deleteChildren("sale_statement", id);
        boolean ok = removeById(id);
        log.info("删除销售对账单: {} 结果: {}", statement.getStatementNo(), ok);
        return ok;
    }

    private String generateStatementNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "SS-" + dateStr + "-";
        QueryWrapper<SaleStatement> wrapper = new QueryWrapper<>();
        wrapper.likeRight("statement_no", prefix).orderByDesc("statement_no").last("LIMIT 1");
        SaleStatement last = getOne(wrapper);
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
