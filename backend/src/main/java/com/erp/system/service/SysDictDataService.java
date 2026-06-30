package com.erp.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.system.entity.SysDictData;

import java.util.List;

public interface SysDictDataService extends IService<SysDictData> {

    /** 按类型ID查字典数据 */
    List<SysDictData> listByTypeId(Long typeId);

    /** 新增字典数据 */
    void saveDictData(SysDictData dictData);

    /** 更新字典数据 */
    void updateDictData(SysDictData dictData);

    /** 删除字典数据 */
    void deleteDictData(Long id);
}
