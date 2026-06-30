package com.erp.basedata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.Location;

public interface LocationService extends IService<Location> {
    Location getByCode(String binCode);
    boolean existsByCode(String binCode);
    boolean existsByCodeExcludeId(String binCode, Long binId);
}
