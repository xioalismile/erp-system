package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Currency;
import com.erp.basedata.mapper.CurrencyMapper;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.serialno.support.SegmentCodeProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrencyServiceImpl extends ServiceImpl<CurrencyMapper, Currency> implements CurrencyService {

    private final SysSerialNoConfigService serialNoService;

    @Override
    public Currency getByCode(String currencyCode) {
        return getOne(new QueryWrapper<Currency>().eq("currency_code", currencyCode));
    }

    @Override
    public boolean existsByCode(String currencyCode) {
        return baseMapper.exists(new QueryWrapper<Currency>().eq("currency_code", currencyCode));
    }

    @Override
    public boolean existsByCodeExcludeId(String currencyCode, Long currencyId) {
        return baseMapper.exists(new QueryWrapper<Currency>()
                .eq("currency_code", currencyCode).ne("currency_id", currencyId));
    }

    @Override
    public boolean save(Currency entity) {
        if (entity.getCurrencyCode() == null || entity.getCurrencyCode().isEmpty()) {
            // 设置中心已关闭自动生成时，要求手动输入编码，不再兜底自动生成
            if (!serialNoService.isAutoGenerate("BD_CURRENCY")) {
                throw new BusinessException("币种已关闭自动编号，请手动输入编码");
            }
            // 三参重载：以业务表实况取号（删除即释放 + 补最小空洞）
            entity.setCurrencyCode(serialNoService.generateSerialNo("BD_CURRENCY", this::nextCurrencyCode,
                    SegmentCodeProbe.of(baseMapper, "currency_code")));
        }
        if (existsByCode(entity.getCurrencyCode())) {
            throw new BusinessException("币种编码已存在: " + entity.getCurrencyCode());
        }
        return super.save(entity);
    }

    /**
     * 防重续号探测：查询币种表当前编号段已用最大编码，返回"下一张完整编码"。
     * 手动录码/停用规则期间产生的编码不会推进自动编号计数，重开自动后以业务表真实流水抬底续号。
     */
    private String nextCurrencyCode(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        Currency last = getOne(new QueryWrapper<Currency>().likeRight("currency_code", prefix)
                .orderByDesc("currency_code").last("LIMIT 1"));
        if (last == null || last.getCurrencyCode() == null) {
            return null;
        }
        try {
            return prefix + (Long.parseLong(last.getCurrencyCode().substring(prefix.length())) + 1);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean updateById(Currency entity) {
        if (entity.getCurrencyCode() != null && !entity.getCurrencyCode().isEmpty()
                && entity.getCurrencyId() != null) {
            if (existsByCodeExcludeId(entity.getCurrencyCode(), entity.getCurrencyId())) {
                throw new BusinessException("币种编码已存在: " + entity.getCurrencyCode());
            }
        }
        return super.updateById(entity);
    }
}
