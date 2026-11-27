package com.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.system.entity.SysOperateLogDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysOperateLogDetailMapper extends BaseMapper<SysOperateLogDetail> {

    @Select("SELECT * FROM sys_operate_log_detail WHERE log_id = #{logId} ORDER BY detail_id")
    List<SysOperateLogDetail> selectByLogId(Long logId);
}
