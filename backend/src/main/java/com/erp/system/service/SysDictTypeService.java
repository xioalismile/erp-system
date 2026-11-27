package com.erp.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.system.entity.SysDictType;

public interface SysDictTypeService extends IService<SysDictType> {

    /** 分页查询: keyword模糊搜字典类型编码/名称 */
    PageResult<SysDictType> pageQuery(int current, int size, String keyword, String sortBy, String sortOrder);

    /** 详情: 含dictDataList */
    SysDictType getDetail(Long id);

    /** 新增字典类型 */
    void saveDictType(SysDictType dictType);

    /** 更新字典类型 */
    void updateDictType(SysDictType dictType);

    /** 删除字典类型(同时删除关联的dict_data) */
    void deleteDictType(Long id);
}
