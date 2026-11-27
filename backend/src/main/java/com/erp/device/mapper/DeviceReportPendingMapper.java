package com.erp.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.device.entity.DeviceReportPending;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DeviceReportPendingMapper extends BaseMapper<DeviceReportPending> {

    /** 当前任务已结算（含已驳回）的合格数合计，用于算下一段增量 */
    @Select("SELECT COALESCE(SUM(qualified_count),0) FROM device_report_pending " +
            "WHERE device_id = #{deviceId} AND operation_row_id = #{operationRowId}")
    java.math.BigDecimal sumQualified(@Param("deviceId") Long deviceId,
                                      @Param("operationRowId") Long operationRowId);

    @Select("SELECT COALESCE(SUM(rejected_count),0) FROM device_report_pending " +
            "WHERE device_id = #{deviceId} AND operation_row_id = #{operationRowId}")
    java.math.BigDecimal sumRejected(@Param("deviceId") Long deviceId,
                                     @Param("operationRowId") Long operationRowId);
}
