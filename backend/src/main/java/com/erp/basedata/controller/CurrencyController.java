package com.erp.basedata.controller;

import com.erp.basedata.entity.Currency;
import com.erp.basedata.service.CurrencyService;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 币种 Controller
 * 路由: /api/basedata/currency
 */
@RestController
@RequestMapping("/api/basedata/currency")
public class CurrencyController extends BaseCrudController<Currency, Long> {

    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    @Override
    protected IService<Currency> getService() { return currencyService; }

    @Override
    protected String getCodeField() { return "currency_code"; }

    @Override
    protected String getIdField() { return "currency_id"; }

    @Override
    protected List<String> getSearchFields() { return List.of("currency_code", "currency_name"); }
}
