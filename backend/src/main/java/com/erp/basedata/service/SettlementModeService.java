package com.erp.basedata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.SettlementMode;

public interface SettlementModeService extends IService<SettlementMode> {
    SettlementMode getByCode(String settleModeCode);
    boolean existsByCode(String settleModeCode);
    boolean existsByCodeExcludeId(String settleModeCode, Long settleModeId);
}
