package com.erp.finance.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.finance.entity.AccountBalance;
import com.erp.finance.entity.Voucher;
import com.erp.finance.entity.VoucherEntry;
import com.erp.finance.mapper.AccountBalanceMapper;
import com.erp.finance.mapper.VoucherEntryMapper;
import com.erp.finance.mapper.VoucherMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 会计凭证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements VoucherService {

    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper entryMapper;
    private final AccountBalanceMapper balanceMapper;

    @Override
    public PageResult<Voucher> pageVoucher(int current, int size, String voucherNo, String voucherType,
                                            String status, Integer periodYear, Integer periodMonth) {
        QueryWrapper<Voucher> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        if (voucherNo != null && !voucherNo.isBlank()) wrapper.like("voucher_no", voucherNo);
        if (voucherType != null && !voucherType.isBlank()) wrapper.eq("voucher_type", voucherType);
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        if (periodYear != null) wrapper.eq("period_year", periodYear);
        if (periodMonth != null) wrapper.eq("period_month", periodMonth);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Voucher> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Voucher> result = voucherMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public Voucher getVoucherDetail(Long voucherId) {
        Voucher voucher = voucherMapper.selectById(voucherId);
        if (voucher == null) return null;
        List<VoucherEntry> entries = entryMapper.selectList(
                new QueryWrapper<VoucherEntry>().eq("voucher_id", voucherId).orderByAsc("line_no"));
        voucher.setEntries(entries);
        return voucher;
    }

    @Override
    @Transactional
    public Voucher createVoucher(Voucher voucher) {
        // 校验借贷平衡
        validateBalance(voucher);

        if (voucher.getVoucherNo() == null || voucher.getVoucherNo().isBlank()) {
            voucher.setVoucherNo(generateVoucherNo());
        }
        if (voucher.getVoucherDate() == null) voucher.setVoucherDate(LocalDate.now());
        if (voucher.getVoucherType() == null) voucher.setVoucherType("记账凭证");
        if (voucher.getPeriodYear() == null) voucher.setPeriodYear(voucher.getVoucherDate().getYear());
        if (voucher.getPeriodMonth() == null) voucher.setPeriodMonth(voucher.getVoucherDate().getMonthValue());
        if (voucher.getStatus() == null) voucher.setStatus("待记账");
        if (voucher.getApprovalStatus() == null) voucher.setApprovalStatus("待审核");
        if (voucher.getIsReversed() == null) voucher.setIsReversed((short) 0);
        if (voucher.getTotalDebit() == null) voucher.setTotalDebit(BigDecimal.ZERO);
        if (voucher.getTotalCredit() == null) voucher.setTotalCredit(BigDecimal.ZERO);
        if (voucher.getAttachmentCount() == null) voucher.setAttachmentCount(0);

        // 计算合计
        recalcTotals(voucher);

        voucherMapper.insert(voucher);

        // 保存分录
        saveEntries(voucher);

        log.info("凭证创建成功: {} 借方: {} 贷方: {}", voucher.getVoucherNo(), voucher.getTotalDebit(), voucher.getTotalCredit());
        return voucher;
    }

    @Override
    @Transactional
    public Voucher updateVoucher(Voucher voucher) {
        Voucher existing = voucherMapper.selectById(voucher.getVoucherId());
        if (existing == null) throw new BusinessException("凭证不存在");
        if (!"待审核".equals(existing.getApprovalStatus())) {
            throw new BusinessException("只有待审核状态可修改");
        }

        // 校验借贷平衡
        validateBalance(voucher);

        // 删除原有分录
        entryMapper.delete(new QueryWrapper<VoucherEntry>().eq("voucher_id", voucher.getVoucherId()));

        // 重新计算合计
        recalcTotals(voucher);

        voucherMapper.updateById(voucher);

        // 重新保存分录
        saveEntries(voucher);

        log.info("凭证更新成功: {}", voucher.getVoucherNo());
        return voucher;
    }

    @Override
    @Transactional
    public void deleteVoucher(Long voucherId) {
        Voucher existing = voucherMapper.selectById(voucherId);
        if (existing == null) throw new BusinessException("凭证不存在");
        if (!"待审核".equals(existing.getApprovalStatus())) {
            throw new BusinessException("只有待审核状态可删除");
        }
        entryMapper.delete(new QueryWrapper<VoucherEntry>().eq("voucher_id", voucherId));
        voucherMapper.deleteById(voucherId);
        log.info("凭证删除: {}", existing.getVoucherNo());
    }

    @Override
    @Transactional
    public void postVoucher(Long voucherId, String operator) {
        Voucher voucher = voucherMapper.selectById(voucherId);
        if (voucher == null) throw new BusinessException("凭证不存在");
        if (!"待记账".equals(voucher.getStatus())) {
            throw new BusinessException("只有待记账状态可记账");
        }

        voucher.setStatus("已记账");
        voucher.setPosterName(operator);
        voucher.setUpdatedAt(LocalDateTime.now());
        voucher.setUpdatedBy(operator);
        voucherMapper.updateById(voucher);

        // 更新科目余额表
        List<VoucherEntry> entries = entryMapper.selectList(
                new QueryWrapper<VoucherEntry>().eq("voucher_id", voucherId));
        for (VoucherEntry entry : entries) {
            updateAccountBalance(voucher, entry);
        }

        log.info("凭证记账成功: {} 操作人: {}", voucher.getVoucherNo(), operator);
    }

    @Override
    @Transactional
    public void auditVoucher(Long voucherId, String operator) {
        Voucher voucher = voucherMapper.selectById(voucherId);
        if (voucher == null) throw new BusinessException("凭证不存在");
        if (!"待审核".equals(voucher.getApprovalStatus())) {
            throw new BusinessException("只有待审核状态可审核");
        }

        voucher.setApprovalStatus("已审核");
        voucher.setApprovedBy(operator);
        voucher.setApprovedAt(LocalDateTime.now());
        voucher.setStatus("待记账");
        voucher.setCheckerName(operator);
        voucher.setUpdatedAt(LocalDateTime.now());
        voucher.setUpdatedBy(operator);
        voucherMapper.updateById(voucher);

        log.info("凭证审核成功: {} 操作人: {}", voucher.getVoucherNo(), operator);
    }

    // ========== 私有方法 ==========

    /** 校验借贷平衡 */
    private void validateBalance(Voucher voucher) {
        if (voucher.getEntries() == null || voucher.getEntries().isEmpty()) {
            throw new BusinessException("凭证分录不能为空");
        }
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (VoucherEntry entry : voucher.getEntries()) {
            BigDecimal debit = entry.getDebitAmount() != null ? entry.getDebitAmount() : BigDecimal.ZERO;
            BigDecimal credit = entry.getCreditAmount() != null ? entry.getCreditAmount() : BigDecimal.ZERO;
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new BusinessException("借贷不平衡：借方合计 " + totalDebit + "，贷方合计 " + totalCredit);
        }
    }

    /** 重新计算合计 */
    private void recalcTotals(Voucher voucher) {
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        if (voucher.getEntries() != null) {
            for (VoucherEntry entry : voucher.getEntries()) {
                BigDecimal debit = entry.getDebitAmount() != null ? entry.getDebitAmount() : BigDecimal.ZERO;
                BigDecimal credit = entry.getCreditAmount() != null ? entry.getCreditAmount() : BigDecimal.ZERO;
                totalDebit = totalDebit.add(debit);
                totalCredit = totalCredit.add(credit);
            }
        }
        voucher.setTotalDebit(totalDebit);
        voucher.setTotalCredit(totalCredit);
    }

    /** 保存分录 */
    private void saveEntries(Voucher voucher) {
        if (voucher.getEntries() == null || voucher.getEntries().isEmpty()) return;
        int lineNo = 10;
        for (VoucherEntry entry : voucher.getEntries()) {
            entry.setEntryId(null);
            entry.setVoucherId(voucher.getVoucherId());
            entry.setLineNo(lineNo);
            if (entry.getDebitAmount() == null) entry.setDebitAmount(BigDecimal.ZERO);
            if (entry.getCreditAmount() == null) entry.setCreditAmount(BigDecimal.ZERO);
            if (entry.getQuantity() == null) entry.setQuantity(BigDecimal.ZERO);
            if (entry.getUnitPrice() == null) entry.setUnitPrice(BigDecimal.ZERO);
            if (entry.getExchangeRate() == null) entry.setExchangeRate(BigDecimal.ONE);
            if (entry.getForeignAmount() == null) entry.setForeignAmount(BigDecimal.ZERO);
            entryMapper.insert(entry);
            lineNo += 10;
        }
    }

    /** 更新科目余额表 */
    private void updateAccountBalance(Voucher voucher, VoucherEntry entry) {
        if (entry.getAccountId() == null) return;

        QueryWrapper<AccountBalance> wrapper = new QueryWrapper<>();
        wrapper.eq("period_year", voucher.getPeriodYear())
                .eq("period_month", voucher.getPeriodMonth())
                .eq("account_id", entry.getAccountId());
        AccountBalance balance = balanceMapper.selectOne(wrapper);

        BigDecimal debit = entry.getDebitAmount() != null ? entry.getDebitAmount() : BigDecimal.ZERO;
        BigDecimal credit = entry.getCreditAmount() != null ? entry.getCreditAmount() : BigDecimal.ZERO;

        if (balance == null) {
            balance = new AccountBalance();
            balance.setPeriodYear(voucher.getPeriodYear());
            balance.setPeriodMonth(voucher.getPeriodMonth());
            balance.setAccountId(entry.getAccountId());
            balance.setAccountCode(entry.getAccountCode());
            balance.setAccountName(entry.getAccountName());
            balance.setAccountDirection(entry.getAccountDirection());
            balance.setBeginDebitBalance(BigDecimal.ZERO);
            balance.setBeginCreditBalance(BigDecimal.ZERO);
            balance.setCurrentDebit(debit);
            balance.setCurrentCredit(credit);
            recalcEndBalance(balance);
            balanceMapper.insert(balance);
        } else {
            balance.setCurrentDebit(balance.getCurrentDebit().add(debit));
            balance.setCurrentCredit(balance.getCurrentCredit().add(credit));
            recalcEndBalance(balance);
            balanceMapper.updateById(balance);
        }
    }

    /** 计算期末余额 */
    private void recalcEndBalance(AccountBalance balance) {
        BigDecimal beginDebit = balance.getBeginDebitBalance() != null ? balance.getBeginDebitBalance() : BigDecimal.ZERO;
        BigDecimal beginCredit = balance.getBeginCreditBalance() != null ? balance.getBeginCreditBalance() : BigDecimal.ZERO;
        BigDecimal currentDebit = balance.getCurrentDebit() != null ? balance.getCurrentDebit() : BigDecimal.ZERO;
        BigDecimal currentCredit = balance.getCurrentCredit() != null ? balance.getCurrentCredit() : BigDecimal.ZERO;

        // 借方科目：期末借 = 期初借 + 本期借 - 本期贷
        if ("借".equals(balance.getAccountDirection())) {
            BigDecimal endDebit = beginDebit.add(currentDebit).subtract(currentCredit);
            if (endDebit.compareTo(BigDecimal.ZERO) >= 0) {
                balance.setEndDebitBalance(endDebit);
                balance.setEndCreditBalance(BigDecimal.ZERO);
            } else {
                balance.setEndDebitBalance(BigDecimal.ZERO);
                balance.setEndCreditBalance(endDebit.negate());
            }
        } else {
            // 贷方科目：期末贷 = 期初贷 + 本期贷 - 本期借
            BigDecimal endCredit = beginCredit.add(currentCredit).subtract(currentDebit);
            if (endCredit.compareTo(BigDecimal.ZERO) >= 0) {
                balance.setEndCreditBalance(endCredit);
                balance.setEndDebitBalance(BigDecimal.ZERO);
            } else {
                balance.setEndCreditBalance(BigDecimal.ZERO);
                balance.setEndDebitBalance(endCredit.negate());
            }
        }
    }

    /** 生成凭证号: PZ+yyyyMMdd+4位流水号 */
    private String generateVoucherNo() {
        String prefix = "PZ" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<Voucher> wrapper = new QueryWrapper<>();
        wrapper.likeRight("voucher_no", prefix).orderByDesc("voucher_no").last("LIMIT 1");
        Voucher last = voucherMapper.selectOne(wrapper);
        int seq = 1;
        if (last != null && last.getVoucherNo() != null) {
            try {
                seq = Integer.parseInt(last.getVoucherNo().substring(prefix.length())) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }
}
