package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.UnitOfMeasure;
import com.erp.basedata.mapper.UnitOfMeasureMapper;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.serialno.support.SegmentCodeProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UnitOfMeasureServiceImpl extends ServiceImpl<UnitOfMeasureMapper, UnitOfMeasure> implements UnitOfMeasureService {

    private final SysSerialNoConfigService serialNoService;

    @Override
    public UnitOfMeasure getByCode(String unitCode) {
        return getOne(new QueryWrapper<UnitOfMeasure>().eq("unit_code", unitCode));
    }

    @Override
    public boolean existsByCode(String unitCode) {
        return baseMapper.exists(new QueryWrapper<UnitOfMeasure>().eq("unit_code", unitCode));
    }

    @Override
    public boolean existsByCodeExcludeId(String unitCode, Long unitId) {
        return baseMapper.exists(new QueryWrapper<UnitOfMeasure>()
                .eq("unit_code", unitCode).ne("unit_id", unitId));
    }

    @Override
    public boolean save(UnitOfMeasure entity) {
        if (entity.getUnitCode() == null || entity.getUnitCode().isEmpty()) {
            // 设置中心已关闭自动生成时，要求手动输入编码，不再兜底自动生成
            if (!serialNoService.isAutoGenerate("BD_UNIT")) {
                throw new BusinessException("计量单位已关闭自动编号，请手动输入编码");
            }
            // 三参重载：nextNoQuery 防重抬底 + segmentCodesQuery 以业务表实况取号（删除即释放 + 补最小空洞）
            entity.setUnitCode(serialNoService.generateSerialNo("BD_UNIT", this::nextUnitCode,
                    SegmentCodeProbe.of(baseMapper, "unit_code")));
        }
        if (existsByCode(entity.getUnitCode())) {
            throw new BusinessException("计量单位编码已存在: " + entity.getUnitCode());
        }
        return super.save(entity);
    }

    /**
     * 防重续号探测：查询计量单位表当前编号段已用最大编码，返回"下一张完整编码"。
     * 手动录码/停用规则期间产生的编码不会推进自动编号计数，重开自动后以业务表真实流水抬底续号。
     */
    private String nextUnitCode(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        UnitOfMeasure last = getOne(new QueryWrapper<UnitOfMeasure>().likeRight("unit_code", prefix)
                .orderByDesc("unit_code").last("LIMIT 1"));
        if (last == null || last.getUnitCode() == null) {
            return null;
        }
        try {
            return prefix + (Long.parseLong(last.getUnitCode().substring(prefix.length())) + 1);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean updateById(UnitOfMeasure entity) {
        if (entity.getUnitCode() != null && !entity.getUnitCode().isEmpty()
                && entity.getUnitId() != null) {
            if (existsByCodeExcludeId(entity.getUnitCode(), entity.getUnitId())) {
                throw new BusinessException("计量单位编码已存在: " + entity.getUnitCode());
            }
        }
        return super.updateById(entity);
    }
}
