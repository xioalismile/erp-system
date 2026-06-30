package com.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.system.entity.SysOperateLog;
import com.erp.system.mapper.SysOperateLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysOperateLogServiceImpl extends ServiceImpl<SysOperateLogMapper, SysOperateLog> implements SysOperateLogService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public PageResult<SysOperateLog> pageQuery(int current, int size, String keyword, String operateType, String startDate, String endDate) {
        QueryWrapper<SysOperateLog> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("user_name", keyword)
                    .or().like("real_name", keyword)
                    .or().like("user_code", keyword)
                    .or().like("module_name", keyword)
                    .or().like("operate_summary", keyword));
        }
        if (operateType != null && !operateType.isBlank()) {
            wrapper.eq("operate_type", operateType);
        }
        if (startDate != null && !startDate.isBlank()) {
            LocalDateTime start = LocalDate.parse(startDate, DATE_FMT).atStartOfDay();
            wrapper.ge("operate_time", start);
        }
        if (endDate != null && !endDate.isBlank()) {
            LocalDateTime end = LocalDate.parse(endDate, DATE_FMT).atTime(23, 59, 59);
            wrapper.le("operate_time", end);
        }
        wrapper.orderByDesc("operate_time");
        Page<SysOperateLog> page = new Page<>(current, size);
        Page<SysOperateLog> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    @Transactional
    public void saveLog(SysOperateLog operateLog) {
        if (operateLog.getOperateTime() == null) {
            operateLog.setOperateTime(LocalDateTime.now());
        }
        if (operateLog.getOperateResult() == null) {
            operateLog.setOperateResult("成功");
        }
        save(operateLog);
        log.info("记录操作日志: user={} module={} type={}",
                operateLog.getUserName(), operateLog.getModuleName(), operateLog.getOperateType());
    }
}
