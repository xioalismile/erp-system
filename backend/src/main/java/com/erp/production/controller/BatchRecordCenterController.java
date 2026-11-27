package com.erp.production.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.inventory.batch.mapper.BatchRecordCenterMapper;
import com.erp.inventory.batch.service.BatchTraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批记录中心 Controller（化妆品批记录）。
 * 路由: /api/production/batch-record-center
 *
 * <p>总查询门面：批次总览（一屏汇总全部批次与状态）+ 留样台账 + 追溯树。
 * 检验项设置页签直接复用现有质检模板管理，不在此重复提供接口。</p>
 */
@RestController
@RequestMapping("/api/production/batch-record-center")
@RequiredArgsConstructor
public class BatchRecordCenterController {

    private final BatchRecordCenterMapper centerMapper;
    private final BatchTraceService batchTraceService;

    /** 批次总览分页 */
    @GetMapping("/list")
    public Result<PageResult<Map<String, Object>>> list(@RequestParam(required = false) Integer current,
                                                        @RequestParam(required = false) Integer size,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String qcStatus,
                                                        @RequestParam(required = false) Long materialId,
                                                        @RequestParam(required = false) String dateFrom,
                                                        @RequestParam(required = false) String dateTo) {
        int page = current == null || current < 1 ? 1 : current;
        int pageSize = size == null || size < 1 ? 20 : size;
        List<Map<String, Object>> records = centerMapper.pageList(keyword, qcStatus, materialId,
                dateFrom, dateTo, (page - 1) * pageSize, pageSize);
        long total = centerMapper.pageCount(keyword, qcStatus, materialId, dateFrom, dateTo);
        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setCurrent(page);
        result.setSize(pageSize);
        return Result.success(result);
    }

    /** 留样台账分页 */
    @GetMapping("/samples")
    public Result<PageResult<Map<String, Object>>> samples(@RequestParam(required = false) Integer current,
                                                           @RequestParam(required = false) Integer size,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String remindStatus) {
        int page = current == null || current < 1 ? 1 : current;
        int pageSize = size == null || size < 1 ? 20 : size;
        List<Map<String, Object>> records = centerMapper.sampleList(keyword, remindStatus,
                (page - 1) * pageSize, pageSize);
        long total = centerMapper.sampleCount(keyword, remindStatus);
        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setCurrent(page);
        result.setSize(pageSize);
        return Result.success(result);
    }

    /** 追溯树：direction = up / down / both */
    @GetMapping("/trace/{batchId}")
    public Result<Map<String, Object>> trace(@PathVariable Long batchId,
                                             @RequestParam(required = false) String direction) {
        return Result.success(batchTraceService.trace(batchId, direction));
    }

    /** 批次状态统计（页头统计卡用） */
    @GetMapping("/status-summary")
    public Result<Map<String, Object>> statusSummary() {
        Map<String, Object> out = new LinkedHashMap<>();
        String[] statuses = {"待检", "合格", "不合格", "已放行", "已冻结"};
        for (String s : statuses) {
            out.put(s, centerMapper.pageCount(null, s, null, null, null));
        }
        out.put("全部", centerMapper.pageCount(null, null, null, null, null));
        return Result.success(out);
    }
}
