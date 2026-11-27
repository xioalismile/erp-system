package com.erp.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.production.entity.WorkOrderOperation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工单工序实例 Mapper
 */
@Mapper
public interface WorkOrderOperationMapper extends BaseMapper<WorkOrderOperation> {

    /** 按工单ID查询所有工序实例(按行号升序) */
    @Select("SELECT * FROM work_order_operation WHERE order_id = #{orderId} ORDER BY line_no")
    List<WorkOrderOperation> selectByOrderId(@Param("orderId") Long orderId);
}
