package com.erp.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.production.entity.OperationReportFlow;
import org.apache.ibatis.annotations.Mapper;

/** 工序报工流水（2026-10-25）：按人记账，工序已报与个人已报都从它汇总 */
@Mapper
public interface OperationReportFlowMapper extends BaseMapper<OperationReportFlow> {
}
