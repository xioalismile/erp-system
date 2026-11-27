package com.erp.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.production.entity.WorkOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单工序操作日志 Mapper
 */
@Mapper
public interface WorkOperationLogMapper extends BaseMapper<WorkOperationLog> {
}
