package com.erp.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.production.entity.WorkOrderShare;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生产工单进度对外分享 Mapper。
 */
@Mapper
public interface WorkOrderShareMapper extends BaseMapper<WorkOrderShare> {
}
