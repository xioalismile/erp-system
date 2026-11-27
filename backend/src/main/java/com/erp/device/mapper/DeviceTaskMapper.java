package com.erp.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.device.entity.DeviceTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DeviceTaskMapper extends BaseMapper<DeviceTask> {

    /** 该设备当前有效的关联（同一设备同时只允许一条执行中） */
    @Select("SELECT * FROM device_task WHERE device_id = #{deviceId} AND task_status = '执行中' LIMIT 1")
    DeviceTask findRunning(@Param("deviceId") Long deviceId);
}
