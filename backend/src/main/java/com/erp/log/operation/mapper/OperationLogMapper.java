package com.erp.log.operation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.log.operation.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    @Select("SELECT module, module_name, COUNT(*) as count FROM operation_log " +
            "WHERE operation_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY) " +
            "GROUP BY module, module_name ORDER BY count DESC")
    List<Map<String, Object>> getModuleStats(@Param("days") int days);

    @Select("SELECT operation_type, operation_name, COUNT(*) as count FROM operation_log " +
            "WHERE operation_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY) " +
            "GROUP BY operation_type, operation_name ORDER BY count DESC")
    List<Map<String, Object>> getOperationTypeStats(@Param("days") int days);

    @Select("SELECT user_id, username, COUNT(*) as count FROM operation_log " +
            "WHERE user_id = #{userId} AND operation_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY) " +
            "GROUP BY user_id, username ORDER BY count DESC")
    List<Map<String, Object>> getUserOperationStats(@Param("userId") Long userId, @Param("days") int days);
}
