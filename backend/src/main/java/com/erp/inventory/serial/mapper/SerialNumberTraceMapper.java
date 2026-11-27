package com.erp.inventory.serial.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.inventory.serial.entity.SerialNumberTrace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SerialNumberTraceMapper extends BaseMapper<SerialNumberTrace> {

    @Select("SELECT * FROM serial_number_trace WHERE serial_number_id = #{serialNumberId} ORDER BY operation_time DESC")
    List<SerialNumberTrace> selectBySerialNumberId(@Param("serialNumberId") Long serialNumberId);

    @Select("SELECT * FROM serial_number_trace WHERE serial_no = #{serialNo} ORDER BY operation_time DESC")
    List<SerialNumberTrace> selectBySerialNo(@Param("serialNo") String serialNo);
}
