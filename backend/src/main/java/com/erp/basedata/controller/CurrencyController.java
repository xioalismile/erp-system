package com.erp.basedata.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.basedata.entity.Currency;
import com.erp.basedata.service.CurrencyService;
import com.erp.common.Result;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

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

    /** 自定义字段搜索实体类型（sys_custom_field.entity_type='currency'） */
    @Override
    protected String getCustomFieldEntityType() { return "currency"; }

    @Override
    protected List<String> getSearchFields() { return List.of("currency_code", "currency_name"); }

    /** 可排序列白名单（实体全部数据库列，自动生成） */
    @Override
    protected Set<String> getSortableColumns() {
        return Set.of(
            "created_at",
            "created_by",
            "currency_code",
            "currency_id",
            "currency_name",
            "currency_symbol",
            "exchange_rate",
            "is_base_currency",
            "remark",
            "status",
            "updated_at",
            "updated_by"
        );
    }

    /**
     * 币种下拉列表：**基础币种（人民币）永远排第一**（2026-09-19 修正）。
     *
     * <p>背景（用户实测反馈）：销售订单等 14+ 个编辑页新建时默认币种取的是 {@code currencies[0]}，
     * 而基类的 {@code /list} 是按 {@code currency_code} 升序 → 排序结果其实是"按字母"。
     * 本库 CNY 恰好排第一所以看着是对的，但客户库里只要有 AUD/CAD 之类的编码，
     * 默认币种就会变成外币（用户预期："系统默认的币种就是人民币"）。</p>
     *
     * <p>现在统一改为：基础币种优先（{@code is_base_currency DESC}），其余按 id 升序。
     * 一处后端改动，前端 14+ 个编辑页零改动即拿到正确默认值；下拉里人民币也在最上面。</p>
     */
    @Override
    @GetMapping("/list")
    public Result<List<Currency>> list(@RequestParam(required = false) String status) {
        QueryWrapper<Currency> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) qw.eq("status", status);
        qw.orderByDesc("is_base_currency").orderByAsc("currency_id");
        return Result.success(currencyService.list(qw));
    }
}
