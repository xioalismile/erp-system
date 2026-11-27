package com.erp.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.system.entity.SysDictData;

import java.util.List;

public interface SysDictDataService extends IService<SysDictData> {

    /** 按类型ID查字典数据 */
    List<SysDictData> listByTypeId(Long typeId);

    /** 按类型编码查字典数据（类型不存在返回空列表） */
    List<SysDictData> listByTypeCode(String typeCode);

    /**
     * 按类型编码新增字典选项（供前端"回车保存新选项"）
     * 类型不存在时自动创建；label 已存在时直接返回已有记录（幂等）
     */
    SysDictData saveOptionByTypeCode(String typeCode, String label);

    /** 新增字典数据 */
    void saveDictData(SysDictData dictData);

    /** 更新字典数据 */
    void updateDictData(SysDictData dictData);

    /** 删除字典数据 */
    void deleteDictData(Long id);
}
