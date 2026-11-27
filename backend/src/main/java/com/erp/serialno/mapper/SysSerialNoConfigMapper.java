package com.erp.serialno.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.serialno.entity.SysSerialNoConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysSerialNoConfigMapper extends BaseMapper<SysSerialNoConfig> {

    @Select("SELECT * FROM sys_serial_no_config WHERE doc_type = #{docType} FOR UPDATE")
    SysSerialNoConfig selectByDocTypeForUpdate(@Param("docType") String docType);

    @Update("UPDATE sys_serial_no_config SET current_serial = #{currentSerial}, \"current_date\" = #{currentDate}, " +
            "updated_at = NOW() WHERE id = #{id}")
    int updateCurrentSerial(@Param("id") Long id,
                            @Param("currentSerial") Integer currentSerial,
                            @Param("currentDate") String currentDate);
}
