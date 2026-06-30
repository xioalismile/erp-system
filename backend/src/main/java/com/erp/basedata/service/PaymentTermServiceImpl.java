package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.PaymentTerm;
import com.erp.basedata.mapper.PaymentTermMapper;
import org.springframework.stereotype.Service;

@Service
public class PaymentTermServiceImpl extends ServiceImpl<PaymentTermMapper, PaymentTerm> implements PaymentTermService {

    @Override
    public PaymentTerm getByCode(String paymentTermCode) {
        return getOne(new QueryWrapper<PaymentTerm>().eq("payment_term_code", paymentTermCode));
    }

    @Override
    public boolean existsByCode(String paymentTermCode) {
        return baseMapper.exists(new QueryWrapper<PaymentTerm>().eq("payment_term_code", paymentTermCode));
    }

    @Override
    public boolean existsByCodeExcludeId(String paymentTermCode, Long paymentTermId) {
        return baseMapper.exists(new QueryWrapper<PaymentTerm>()
                .eq("payment_term_code", paymentTermCode).ne("payment_term_id", paymentTermId));
    }
}
