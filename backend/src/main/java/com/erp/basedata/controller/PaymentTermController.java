package com.erp.basedata.controller;

import com.erp.basedata.entity.PaymentTerm;
import com.erp.basedata.service.PaymentTermService;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

import java.util.List;

/**
 * 付款条件 Controller
 * 路由: /api/basedata/payment-term
 */
@RestController
@RequestMapping("/api/basedata/payment-term")
public class PaymentTermController extends BaseCrudController<PaymentTerm, Long> {

    private final PaymentTermService paymentTermService;

    public PaymentTermController(PaymentTermService paymentTermService) {
        this.paymentTermService = paymentTermService;
    }

    @Override
    protected IService<PaymentTerm> getService() { return paymentTermService; }

    @Override
    protected String getCodeField() { return "payment_term_code"; }

    @Override
    protected String getIdField() { return "payment_term_id"; }

    @Override
    protected List<String> getSearchFields() { return List.of("payment_term_code", "payment_term_name"); }

    /** 可排序列白名单（实体全部数据库列，自动生成） */
    @Override
    protected Set<String> getSortableColumns() {
        return Set.of(
            "advance_rate",
            "created_at",
            "created_by",
            "discount_days",
            "discount_rate",
            "payment_days",
            "payment_term_code",
            "payment_term_id",
            "payment_term_name",
            "remark",
            "status",
            "updated_at",
            "updated_by"
        );
    }
}
