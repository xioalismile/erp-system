package com.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.BusinessException;
import com.erp.finance.entity.CostAccountingConfig;
import com.erp.finance.mapper.CostAccountingConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 成本核算配置服务实现
 *
 * defaultMethod 取值:
 * - moving_avg:   移动加权平均法
 * - weighted_avg: 全月一次加权平均法
 * - fixed:        固定成本法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostAccountingConfigServiceImpl extends ServiceImpl<CostAccountingConfigMapper, CostAccountingConfig>
        implements CostAccountingConfigService {

    /** 允许的核算方法 */
    private static final List<String> ALLOWED_METHODS = List.of("moving_avg", "weighted_avg", "fixed");

    @Override
    public List<CostAccountingConfig> listAll() {
        QueryWrapper<CostAccountingConfig> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("config_id");
        return list(wrapper);
    }

    @Override
    @Transactional
    public CostAccountingConfig saveEntity(CostAccountingConfig config) {
        // 校验核算方法
        if (config.getDefaultMethod() == null || !ALLOWED_METHODS.contains(config.getDefaultMethod())) {
            throw new BusinessException("默认核算方法非法，可选值: moving_avg / weighted_avg / fixed");
        }
        if (config.getConfigName() == null || config.getConfigName().isBlank()) {
            throw new BusinessException("配置名称不能为空");
        }
        if (config.getIsEnabled() == null) {
            config.setIsEnabled(1);
        }

        if (config.getConfigId() == null) {
            // 新增
            config.setCreatedAt(LocalDateTime.now());
            save(config);
            // 若新增的为启用，则禁用其他配置
            if (config.getIsEnabled() == 1) {
                disableOthers(config.getConfigId());
            }
            log.info("新增成本核算配置: {} method={}", config.getConfigName(), config.getDefaultMethod());
        } else {
            // 编辑
            updateById(config);
            // 若启用了当前配置，则禁用其他配置
            if (config.getIsEnabled() != null && config.getIsEnabled() == 1) {
                disableOthers(config.getConfigId());
            }
            log.info("修改成本核算配置: configId={}", config.getConfigId());
        }
        return config;
    }

    @Override
    public boolean delete(Long configId) {
        if (configId == null) return false;
        return removeById(configId);
    }

    @Override
    public CostAccountingConfig getActiveConfig() {
        QueryWrapper<CostAccountingConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("is_enabled", 1).orderByDesc("config_id").last("LIMIT 1");
        return getOne(wrapper);
    }

    /** 禁用除当前配置外的其他配置（保证同一时刻仅一条启用） */
    private void disableOthers(Long currentConfigId) {
        UpdateWrapper<CostAccountingConfig> uw = new UpdateWrapper<>();
        uw.ne("config_id", currentConfigId).eq("is_enabled", 1).set("is_enabled", 0);
        update(uw);
    }
}
