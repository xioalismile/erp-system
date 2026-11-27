package com.erp.log.operation.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.log.operation.entity.OperationLog;

import java.util.List;
import java.util.Map;

public interface OperationLogService extends IService<OperationLog> {

    Page<OperationLog> pageQuery(int current, int size, String keyword, String module,
                                 String operationType, Long userId, String startTime, String endTime,
                                 String sortBy, String sortOrder);

    OperationLog getDetail(Long id);

    boolean createLog(OperationLog log);

    boolean batchCreateLog(List<OperationLog> list);

    List<Map<String, Object>> getModuleStats(int days);

    List<Map<String, Object>> getOperationTypeStats(int days);

    List<Map<String, Object>> getUserOperationStats(Long userId, int days);
}
