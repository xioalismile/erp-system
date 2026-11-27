package com.erp.finance.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.finance.entity.CostAccountingConfig;

import java.util.List;

/**
 * 成本核算配置服务
 *
 * defaultMethod 取值:
 * - moving_avg:   移动加权平均法
 * - weighted_avg: 全月一次加权平均法
 * - fixed:        固定成本法
 */
public interface CostAccountingConfigService extends IService<CostAccountingConfig> {

    /** 查询全部配置 */
    List<CostAccountingConfig> listAll();

    /** 保存配置（新增或编辑） */
    CostAccountingConfig saveEntity(CostAccountingConfig config);

    /** 删除配置 */
    boolean delete(Long configId);

    /** 获取当前启用的配置（is_enabled=1 的最新一条） */
    CostAccountingConfig getActiveConfig();
}
