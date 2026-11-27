package com.erp.device.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.device.entity.DeviceInfo;

import java.util.List;

/** 设备台账 */
public interface DeviceInfoService extends IService<DeviceInfo> {

    PageResult<DeviceInfo> pageQuery(int current, int size, String keyword, Long categoryId,
                                     Long workCenterId, String status);

    DeviceInfo getDetail(Long id);

    DeviceInfo createDevice(DeviceInfo device);

    DeviceInfo updateDevice(Long id, DeviceInfo device);

    void deleteDevice(Long id);

    void changeStatus(Long id, String status);

    /** 设备二维码 PNG（内容 = 设备编码） */
    byte[] qrcode(Long id, Integer size);

    /** 设备二维码 Data URI（前端 img 直接引用，避免图片请求带不上 Token） */
    String qrcodeDataUri(Long id, Integer size);

    /** 启用状态的设备下拉（后续接入管理、设备任务用） */
    List<DeviceInfo> listEnabled();
}
