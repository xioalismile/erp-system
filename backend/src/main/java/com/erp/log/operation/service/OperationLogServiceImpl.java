package com.erp.log.operation.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.util.SortHelper;
import com.erp.log.operation.entity.OperationLog;
import com.erp.log.operation.mapper.OperationLogMapper;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "cost_time",
            "doc_id",
            "doc_no",
            "doc_type",
            "id",
            "ip_address",
            "log_no",
            "module",
            "module_name",
            "operation_name",
            "operation_time",
            "operation_type",
            "remark",
            "request_method",
            "request_params",
            "request_url",
            "result_code",
            "result_message",
            "status",
            "user_agent",
            "user_id",
            "username"
        );

    @Override
    public Page<OperationLog> pageQuery(int current, int size, String keyword, String module,
                                        String operationType, Long userId, String startTime, String endTime,
                                        String sortBy, String sortOrder) {
        Page<OperationLog> page = new Page<>(current, size);
        QueryWrapper<OperationLog> wrapper = new QueryWrapper<>();

        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like("operation_name", keyword)
                    .or().like("username", keyword)
                    .or().like("doc_no", keyword));
        }
        if (StrUtil.isNotBlank(module)) {
            wrapper.eq("module", module);
        }
        if (StrUtil.isNotBlank(operationType)) {
            wrapper.eq("operation_type", operationType);
        }
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        if (StrUtil.isNotBlank(startTime)) {
            wrapper.ge("operation_time", LocalDateTime.parse(startTime, DATE_TIME_FORMATTER));
        }
        if (StrUtil.isNotBlank(endTime)) {
            wrapper.le("operation_time", LocalDateTime.parse(endTime, DATE_TIME_FORMATTER));
        }

        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) wrapper.orderByDesc("operation_time");
        return page(page, wrapper);
    }

    @Override
    public OperationLog getDetail(Long id) {
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createLog(OperationLog operationLog) {
        return save(operationLog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchCreateLog(List<OperationLog> list) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        return saveBatch(list);
    }

    @Override
    public List<Map<String, Object>> getModuleStats(int days) {
        return baseMapper.getModuleStats(days);
    }

    @Override
    public List<Map<String, Object>> getOperationTypeStats(int days) {
        return baseMapper.getOperationTypeStats(days);
    }

    @Override
    public List<Map<String, Object>> getUserOperationStats(Long userId, int days) {
        return baseMapper.getUserOperationStats(userId, days);
    }
}
