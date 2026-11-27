package com.erp.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.device.entity.DeviceInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DeviceInfoMapper extends BaseMapper<DeviceInfo> {

    /** 该设备已有的设备任务数（删除前校验：有任务不允许删除） */
    @Select("SELECT COUNT(*) FROM device_task WHERE device_id = #{deviceId}")
    long countTasks(@Param("deviceId") Long deviceId);

    /** 该设备已有的接入凭证数（删除前校验：有凭证不允许删除） */
    @Select("SELECT COUNT(*) FROM device_access_credential WHERE device_id = #{deviceId}")
    long countCredentials(@Param("deviceId") Long deviceId);

    /** 该设备已有的维修单数（删除前校验：有维修记录不允许删除） */
    @Select("SELECT COUNT(*) FROM device_repair_order WHERE device_id = #{deviceId}")
    long countRepairs(@Param("deviceId") Long deviceId);

    /** 该设备已有的点检记录数 */
    @Select("SELECT COUNT(*) FROM device_inspection_record WHERE device_id = #{deviceId}")
    long countInspections(@Param("deviceId") Long deviceId);

    /** 该设备已有的保养记录数 */
    @Select("SELECT COUNT(*) FROM device_maintenance_order WHERE device_id = #{deviceId}")
    long countMaintenanceOrders(@Param("deviceId") Long deviceId);
}
