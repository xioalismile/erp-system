package com.erp.inventory.serial.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.inventory.serial.entity.SerialNumber;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SerialNumberMapper extends BaseMapper<SerialNumber> {

    @Select("SELECT * FROM serial_number WHERE serial_no = #{serialNo}")
    SerialNumber selectBySerialNo(@Param("serialNo") String serialNo);

    @Select("SELECT * FROM serial_number WHERE serial_no IN (#{serialNos})")
    List<SerialNumber> selectBySerialNos(@Param("serialNos") List<String> serialNos);

    @Select("SELECT * FROM serial_number WHERE serial_no = #{serialNo} AND status = 0")
    SerialNumber selectAvailableBySerialNo(@Param("serialNo") String serialNo);
}
