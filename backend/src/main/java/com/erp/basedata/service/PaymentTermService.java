package com.erp.basedata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.PaymentTerm;

public interface PaymentTermService extends IService<PaymentTerm> {
    PaymentTerm getByCode(String paymentTermCode);
    boolean existsByCode(String paymentTermCode);
    boolean existsByCodeExcludeId(String paymentTermCode, Long paymentTermId);
}
