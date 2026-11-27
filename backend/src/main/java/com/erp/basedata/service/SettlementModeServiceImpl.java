package com.erp.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.SettlementMode;
import com.erp.basedata.mapper.SettlementModeMapper;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.serialno.support.SegmentCodeProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettlementModeServiceImpl extends ServiceImpl<SettlementModeMapper, SettlementMode> implements SettlementModeService {

    private final SysSerialNoConfigService serialNoService;

    @Override
    public SettlementMode getByCode(String settleModeCode) {
        return getOne(new QueryWrapper<SettlementMode>().eq("settle_mode_code", settleModeCode));
    }

    @Override
    public boolean existsByCode(String settleModeCode) {
        return baseMapper.exists(new QueryWrapper<SettlementMode>().eq("settle_mode_code", settleModeCode));
    }

    @Override
    public boolean existsByCodeExcludeId(String settleModeCode, Long settleModeId) {
        return baseMapper.exists(new QueryWrapper<SettlementMode>()
                .eq("settle_mode_code", settleModeCode).ne("settle_mode_id", settleModeId));
    }

    @Override
    public boolean save(SettlementMode entity) {
        if (entity.getSettleModeCode() == null || entity.getSettleModeCode().isEmpty()) {
            // 设置中心已关闭自动生成时，要求手动输入编码，不再兜底自动生成
            if (!serialNoService.isAutoGenerate("BD_SETTLE_MODE")) {
                throw new BusinessException("结算方式已关闭自动编号，请手动输入编码");
            }
            // 三参重载：以业务表实况取号（删除即释放 + 补最小空洞）
            entity.setSettleModeCode(serialNoService.generateSerialNo("BD_SETTLE_MODE", this::nextSettleModeCode,
                    SegmentCodeProbe.of(baseMapper, "settle_mode_code")));
        }
        if (existsByCode(entity.getSettleModeCode())) {
            throw new BusinessException("结算方式编码已存在: " + entity.getSettleModeCode());
        }
        return super.save(entity);
    }

    /**
     * 防重续号探测：查询结算方式表当前编号段已用最大编码，返回"下一张完整编码"。
     * 手动录码/停用规则期间产生的编码不会推进自动编号计数，重开自动后以业务表真实流水抬底续号。
     */
    private String nextSettleModeCode(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        SettlementMode last = getOne(new QueryWrapper<SettlementMode>().likeRight("settle_mode_code", prefix)
                .orderByDesc("settle_mode_code").last("LIMIT 1"));
        if (last == null || last.getSettleModeCode() == null) {
            return null;
        }
        try {
            return prefix + (Long.parseLong(last.getSettleModeCode().substring(prefix.length())) + 1);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean updateById(SettlementMode entity) {
        if (entity.getSettleModeCode() != null && !entity.getSettleModeCode().isEmpty()
                && entity.getSettleModeId() != null) {
            if (existsByCodeExcludeId(entity.getSettleModeCode(), entity.getSettleModeId())) {
                throw new BusinessException("结算方式编码已存在: " + entity.getSettleModeCode());
            }
        }
        return super.updateById(entity);
    }
}
