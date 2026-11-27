package com.erp.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.util.SortHelper;
import com.erp.system.entity.SysDownloadLog;
import com.erp.system.entity.SysImportTask;
import com.erp.system.entity.SysOperateLog;
import com.erp.system.entity.SysOperateLogDetail;
import com.erp.system.mapper.SysDownloadLogMapper;
import com.erp.system.mapper.SysImportTaskMapper;
import com.erp.system.mapper.SysOperateLogDetailMapper;
import com.erp.system.service.SysOperateLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 操作日志 Controller
 * 路由: /api/system/log
 */
@RestController
@RequestMapping("/api/system/log")
@RequiredArgsConstructor
public class SysOperateLogController {

    private final SysOperateLogService sysOperateLogService;
    private final SysOperateLogDetailMapper sysOperateLogDetailMapper;
    private final SysDownloadLogMapper sysDownloadLogMapper;
    private final SysImportTaskMapper sysImportTaskMapper;

    /** 下载日志排序列白名单（数据库列名，防注入） */
    private static final Set<String> DOWNLOAD_LOG_SORTABLE_COLUMNS = Set.of(
            "download_time", "operator", "file_name", "file_type", "file_size", "ip", "created_at");

    private static final DateTimeFormatter DOWNLOAD_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DOWNLOAD_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/page")
    public Result<PageResult<SysOperateLog>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String operateType,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String moduleName,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String operateUser) {
        String type = operateType;
        if ((type == null || type.isBlank()) && operationType != null && !operationType.isBlank()) {
            type = operationType;
        }
        return Result.success(sysOperateLogService.pageQuery(current, size, keyword, type, moduleName, startDate, endDate, sortBy, sortOrder, operateUser));
    }

    @GetMapping("/detail/{logId}")
    public Result<List<SysOperateLogDetail>> getDetail(@PathVariable Long logId) {
        return Result.success(sysOperateLogDetailMapper.selectByLogId(logId));
    }

    @GetMapping("/bill/{billType}/{billNo}")
    public Result<PageResult<SysOperateLog>> getByBill(
            @PathVariable String billType,
            @PathVariable String billNo,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(sysOperateLogService.getByBill(billType, billNo, current, size));
    }

    // ========== 下载日志 / 导入任务（设置中心「下载日志」「导入进度」页面） ==========

    /**
     * 下载日志分页查询
     * 前端列 prop 为 time/operator/fileName/fileType/fileSize/ip，其中 time 特判映射为 download_time
     */
    @GetMapping("/downloadPage")
    public Result<PageResult<SysDownloadLog>> downloadPage(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        QueryWrapper<SysDownloadLog> wrapper = new QueryWrapper<>();
        if (operator != null && !operator.isBlank()) {
            wrapper.like("operator", operator);
        }
        if (fileType != null && !fileType.isBlank()) {
            wrapper.eq("file_type", fileType);
        }
        // 日期范围：startDate 当天 00:00:00 ~ endDate 当天 23:59:59
        if (startDate != null && !startDate.isBlank()) {
            wrapper.ge("download_time", LocalDate.parse(startDate, DOWNLOAD_DATE_FMT).atStartOfDay());
        }
        if (endDate != null && !endDate.isBlank()) {
            wrapper.le("download_time", LocalDate.parse(endDate, DOWNLOAD_DATE_FMT).atTime(23, 59, 59));
        }
        // 前端 time 列排序映射为 download_time 列，其余列经驼峰转下划线后过白名单
        String sortCol = "time".equals(sortBy) ? "download_time" : sortBy;
        if (!SortHelper.applyToWrapper(wrapper, sortCol, sortOrder, DOWNLOAD_LOG_SORTABLE_COLUMNS)) {
            wrapper.orderByDesc("download_time");
        }
        Page<SysDownloadLog> page = sysDownloadLogMapper.selectPage(new Page<>(current, size), wrapper);
        List<SysDownloadLog> records = page.getRecords();
        // 填充前端展示字段 time（yyyy-MM-dd HH:mm:ss 字符串）
        for (SysDownloadLog r : records) {
            r.setTime(r.getDownloadTime() == null ? null : DOWNLOAD_TIME_FMT.format(r.getDownloadTime()));
        }
        return Result.success(PageResult.of(records, page.getTotal(), current, size));
    }

    /**
     * 清空全部下载日志（前端「清空日志」按钮已带确认弹窗）
     */
    @DeleteMapping("/download")
    public Result<Void> clearDownloadLog() {
        sysDownloadLogMapper.delete(null);
        return Result.success();
    }

    /**
     * 导入任务列表（当前无写入方，返回空列表，页面显示空表为正确行为）
     */
    @GetMapping("/importTasks")
    public Result<List<SysImportTask>> importTasks() {
        List<SysImportTask> list = sysImportTaskMapper.selectList(
                new QueryWrapper<SysImportTask>().orderByAsc("created_at is null").orderByDesc("created_at"));
        for (SysImportTask t : list) {
            // 填充前端展示字段
            t.setName(t.getTaskName());
            t.setType(t.getImportType());
            t.setSuccess(t.getSuccessCount());
            t.setFailed(t.getFailedCount());
            t.setCreateBy(t.getCreatedBy());
        }
        return Result.success(list);
    }
}
