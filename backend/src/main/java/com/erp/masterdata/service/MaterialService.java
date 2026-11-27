package com.erp.masterdata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.masterdata.entity.Material;

import java.util.List;

public interface MaterialService extends IService<Material> {
    Material getByCode(String materialCode);

    /**
     * 查询指定编号段（前缀）下的全部物料编码。
     * 用途：物料编码"删除即释放 + 补最小空洞"的流水探测（见 SysSerialNoConfigService 三参重载）。
     *
     * @param prefix 编号段前缀（不含流水，如 WL0100 / MAT）
     * @return 该号段下已使用的物料编码列表
     */
    List<String> listCodesByPrefix(String prefix);
}
