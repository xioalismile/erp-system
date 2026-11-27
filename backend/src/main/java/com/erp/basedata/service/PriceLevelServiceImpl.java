package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.PriceLevel;
import com.erp.basedata.mapper.PriceLevelMapper;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.serialno.support.SegmentCodeProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PriceLevelServiceImpl extends ServiceImpl<PriceLevelMapper, PriceLevel> implements PriceLevelService {

    private final SysSerialNoConfigService serialNoService;

    @Override
    public PriceLevel getByCode(String priceLevelCode) {
        return getOne(new QueryWrapper<PriceLevel>().eq("price_level_code", priceLevelCode));
    }

    @Override
    public boolean existsByCode(String priceLevelCode) {
        return baseMapper.exists(new QueryWrapper<PriceLevel>().eq("price_level_code", priceLevelCode));
    }

    @Override
    public boolean existsByCodeExcludeId(String priceLevelCode, Long priceLevelId) {
        return baseMapper.exists(new QueryWrapper<PriceLevel>()
                .eq("price_level_code", priceLevelCode).ne("price_level_id", priceLevelId));
    }

    @Override
    public boolean save(PriceLevel entity) {
        if (entity.getPriceLevelCode() == null || entity.getPriceLevelCode().isEmpty()) {
            // 设置中心已关闭自动生成时，要求手动输入编码，不再兜底自动生成
            if (!serialNoService.isAutoGenerate("BD_PRICE_LEVEL")) {
                throw new BusinessException("价格等级已关闭自动编号，请手动输入编码");
            }
            // 三参重载：以业务表实况取号（删除即释放 + 补最小空洞）
            entity.setPriceLevelCode(serialNoService.generateSerialNo("BD_PRICE_LEVEL", this::nextPriceLevelCode,
                    SegmentCodeProbe.of(baseMapper, "price_level_code")));
        }
        if (existsByCode(entity.getPriceLevelCode())) {
            throw new BusinessException("价格等级编码已存在: " + entity.getPriceLevelCode());
        }
        return super.save(entity);
    }

    /**
     * 防重续号探测：查询价格等级表当前编号段已用最大编码，返回"下一张完整编码"。
     * 手动录码/停用规则期间产生的编码不会推进自动编号计数，重开自动后以业务表真实流水抬底续号。
     */
    private String nextPriceLevelCode(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        PriceLevel last = getOne(new QueryWrapper<PriceLevel>().likeRight("price_level_code", prefix)
                .orderByDesc("price_level_code").last("LIMIT 1"));
        if (last == null || last.getPriceLevelCode() == null) {
            return null;
        }
        try {
            return prefix + (Long.parseLong(last.getPriceLevelCode().substring(prefix.length())) + 1);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean updateById(PriceLevel entity) {
        if (entity.getPriceLevelCode() != null && !entity.getPriceLevelCode().isEmpty()
                && entity.getPriceLevelId() != null) {
            if (existsByCodeExcludeId(entity.getPriceLevelCode(), entity.getPriceLevelId())) {
                throw new BusinessException("价格等级编码已存在: " + entity.getPriceLevelCode());
            }
        }
        return super.updateById(entity);
    }
}
