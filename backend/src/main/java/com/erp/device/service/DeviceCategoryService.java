package com.erp.device.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.device.entity.DeviceCategory;

import java.util.List;

/** 设备分类（树形主档） */
public interface DeviceCategoryService extends IService<DeviceCategory> {

    /** 分类树（含每个分类下的设备数） */
    List<DeviceCategory> tree();

    void saveCategory(DeviceCategory category);

    void updateCategory(Long id, DeviceCategory category);

    void deleteCategory(Long id);
}
