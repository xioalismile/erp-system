package com.erp.production.controller;

import com.erp.common.Result;
import com.erp.production.entity.BatchProductionRecord;
import com.erp.production.entity.RecordCorrection;
import com.erp.production.service.BatchRecordService;
import com.erp.production.service.RecordCorrectionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 批生产记录 Controller（化妆品批记录）。
 * 路由: /api/production/batch-records
 *
 * <p>工单详情「批生产记录」页签的数据源：全量聚合 + 环境记录 + 记录更正。</p>
 */
@RestController
@RequestMapping("/api/production/batch-records")
@RequiredArgsConstructor
public class BatchRecordController {

    private final BatchRecordService batchRecordService;
    private final RecordCorrectionService recordCorrectionService;
    private final com.erp.production.service.SignatureService signatureService;

    /** 批生产记录全量聚合（头 + 投料 + 工序 + 检验 + 更正 + 签名） */
    @GetMapping("/by-work-order/{workOrderId}")
    public Result<Map<String, Object>> byWorkOrder(@PathVariable Long workOrderId) {
        return Result.success(batchRecordService.getFullRecord(workOrderId));
    }

    /** 确保记录头存在（工单已生效但记录缺失时补偿生成） */
    @PostMapping("/ensure/{workOrderId}")
    public Result<BatchProductionRecord> ensure(@PathVariable Long workOrderId) {
        return Result.success(batchRecordService.ensureRecord(workOrderId));
    }

    /** 环境记录保存（温湿度可选填，留空不拦） */
    @PostMapping("/{workOrderId}/env")
    public Result<Boolean> saveEnv(@PathVariable Long workOrderId,
                                   @RequestBody Map<String, BigDecimal> body,
                                   HttpServletRequest request) {
        batchRecordService.saveEnv(workOrderId, body.get("temperature"), body.get("humidity"), request);
        return Result.success(true);
    }

    /** 新增记录更正：body = { targetTable, targetId, targetField, newValue, oldValue, reason, password } */
    @PostMapping("/corrections")
    public Result<Long> correct(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return Result.success(recordCorrectionService.correct(
                str(body.get("targetTable")),
                body.get("targetId") == null ? null : Long.valueOf(body.get("targetId").toString()),
                str(body.get("targetField")),
                str(body.get("newValue")),
                str(body.get("oldValue")),
                str(body.get("reason")),
                request,
                str(body.get("password")),
                str(body.get("signImage"))));
    }

    /** 某目标的电子签名留痕（检验记录只读详情展示手写签名图） */
    @GetMapping("/signatures")
    public Result<List<com.erp.production.entity.SignatureRecord>> signatures(@RequestParam String targetTable,
                                                                             @RequestParam Long targetId) {
        return Result.success(signatureService.listByTarget(targetTable, targetId));
    }

    /** 某条记录的更正链 */
    @GetMapping("/corrections")
    public Result<List<RecordCorrection>> corrections(@RequestParam String targetTable,
                                                      @RequestParam Long targetId) {
        return Result.success(recordCorrectionService.listByTarget(targetTable, targetId));
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
