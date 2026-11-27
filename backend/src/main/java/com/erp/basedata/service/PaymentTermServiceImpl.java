package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.PaymentTerm;
import com.erp.basedata.mapper.PaymentTermMapper;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentTermServiceImpl extends ServiceImpl<PaymentTermMapper, PaymentTerm> implements PaymentTermService {

    private final SysSerialNoConfigService serialNoService;

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

    @Override
    public boolean save(PaymentTerm entity) {
        if (entity.getPaymentTermCode() == null || entity.getPaymentTermCode().isEmpty()) {
            entity.setPaymentTermCode(serialNoService.generateSerialNo("BD_PAYMENT_TERM"));
        }
        if (existsByCode(entity.getPaymentTermCode())) {
            throw new BusinessException("付款条件编码已存在: " + entity.getPaymentTermCode());
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(PaymentTerm entity) {
        if (entity.getPaymentTermCode() != null && !entity.getPaymentTermCode().isEmpty()
                && entity.getPaymentTermId() != null) {
            if (existsByCodeExcludeId(entity.getPaymentTermCode(), entity.getPaymentTermId())) {
                throw new BusinessException("付款条件编码已存在: " + entity.getPaymentTermCode());
            }
        }
        return super.updateById(entity);
    }
}
