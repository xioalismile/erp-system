package com.erp.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.production.entity.WorkOrderOperationWorker;
import org.apache.ibatis.annotations.Mapper;

/** 工序共同负责人（2026-10-25 扫码认领） */
@Mapper
public interface WorkOrderOperationWorkerMapper extends BaseMapper<WorkOrderOperationWorker> {
}
