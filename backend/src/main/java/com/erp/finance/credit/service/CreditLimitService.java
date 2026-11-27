package com.erp.finance.credit.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.finance.credit.dto.CreditCheckResult;
import com.erp.finance.credit.dto.CreditStatistics;
import com.erp.finance.credit.entity.CreditLimit;
import com.erp.finance.credit.entity.CreditLimitLog;

import java.math.BigDecimal;

public interface CreditLimitService extends IService<CreditLimit> {

    Page<CreditLimit> pageQuery(int current, int size, String keyword, Integer status, String sortBy, String sortOrder);

    CreditLimit getDetail(Long id);

    CreditLimit getByCustomerId(Long customerId);

    CreditLimit createCreditLimit(CreditLimit creditLimit);

    CreditLimit updateCreditLimit(CreditLimit creditLimit);

    CreditLimit adjustCreditLimit(Long id, BigDecimal changeAmount, String reason);

    void freezeCreditLimit(Long id);

    void unfreezeCreditLimit(Long id);

    Page<CreditLimitLog> getCreditLog(Long creditLimitId, int current, int size);

    CreditCheckResult checkCreditLimit(Long customerId, BigDecimal amount);

    void occupyCredit(Long customerId, BigDecimal amount, Long relatedDocId, String relatedDocNo, String relatedDocType);

    void releaseCredit(Long customerId, BigDecimal amount, Long relatedDocId, String relatedDocNo, String relatedDocType);

    void recoveryCredit(Long customerId, BigDecimal amount, Long relatedDocId, String relatedDocNo, String relatedDocType);

    CreditStatistics getCreditStatistics();
}
