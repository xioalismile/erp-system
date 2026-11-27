package com.erp.ai.query;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.finance.entity.PaymentReceipt;
import com.erp.finance.mapper.PaymentReceiptMapper;
import org.springframework.stereotype.Service;

/**
 * 收款单：供 AI 查询工具使用的只读 IService 适配（原服务不是 MyBatis-Plus 的 IService）。
 */
@Service("paymentReceiptAiQueryService")
public class PaymentReceiptAiQueryService extends ServiceImpl<PaymentReceiptMapper, PaymentReceipt> {
}
