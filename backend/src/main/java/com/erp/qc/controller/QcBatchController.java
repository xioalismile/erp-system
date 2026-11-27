package com.erp.qc.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.Result;
import com.erp.qc.entity.QcRecord;
import com.erp.qc.entity.QcRecordItem;
import com.erp.qc.mapper.QcRecordItemMapper;
import com.erp.qc.mapper.QcRecordMapper;
import com.erp.qc.service.QcBatchService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 批次检验 Controller（化妆品批记录）。
 * 路由: /api/qc/batch
 *
 * <p>建检（按批次，来料检/成品检）→ 检验录入提交（电子签名）→ 复核（电子签名）。
 * 与工序质检（/api/qc/record，只读报表）并存互不影响。</p>
 */
@RestController
@RequestMapping("/api/qc/batch")
@RequiredArgsConstructor
public class QcBatchController {

    private final QcBatchService qcBatchService;
    private final QcRecordMapper qcRecordMapper;
    private final QcRecordItemMapper qcRecordItemMapper;

    /** 按批次建检：qcStage=来料检/成品检；成功返回检验记录ID */
    @PostMapping("/create")
    public Result<Long> create(@RequestParam Long batchId,
                               @RequestParam String qcStage,
                               HttpServletRequest request) {
        return Result.success(qcBatchService.createFromBatch(qcStage, batchId, request));
    }

    /** 检验记录 + 检项明细（检验录入弹窗回显） */
    @GetMapping("/{recordId}")
    public Result<Map<String, Object>> detail(@PathVariable Long recordId) {
        QcRecord record = qcRecordMapper.selectById(recordId);
        List<QcRecordItem> items = qcRecordItemMapper.selectList(
                new LambdaQueryWrapper<QcRecordItem>()
                        .eq(QcRecordItem::getRecordId, recordId)
                        .orderByAsc(QcRecordItem::getLineNo));
        return Result.success(Map.of("record", record == null ? new QcRecord() : record, "items", items));
    }

    /** 提交检验（手写签名）：body = QcRecord（含 result/remark/items 实测值 + signImage 手写签名图） */
    @PostMapping("/{recordId}/submit")
    public Result<Boolean> submit(@PathVariable Long recordId,
                                  @RequestBody QcRecord payload,
                                  @RequestParam(required = false) String password,
                                  HttpServletRequest request) {
        qcBatchService.submitWithSign(recordId, payload, request, password, payload.getSignImage());
        return Result.success(true);
    }

    /** 检验复核（手写签名）：body = { signImage, password? } */
    @PostMapping("/{recordId}/review")
    public Result<Boolean> review(@PathVariable Long recordId,
                                  @RequestBody Map<String, String> body,
                                  HttpServletRequest request) {
        qcBatchService.reviewWithSign(recordId, request, body.get("password"), body.get("signImage"));
        return Result.success(true);
    }

    /** 某批次最近一条检验记录（批记录中心「查看检验」用）：无记录时返回空对象 */
    @GetMapping("/by-batch/{batchId}")
    public Result<Map<String, Object>> byBatch(@PathVariable Long batchId) {
        QcRecord record = qcRecordMapper.selectOne(new LambdaQueryWrapper<QcRecord>()
                .eq(QcRecord::getBatchId, batchId)
                .orderByDesc(QcRecord::getRecordId)
                .last("LIMIT 1"));
        if (record == null) {
            return Result.success(Map.of("record", new QcRecord(), "items", List.of()));
        }
        List<QcRecordItem> items = qcRecordItemMapper.selectList(
                new LambdaQueryWrapper<QcRecordItem>()
                        .eq(QcRecordItem::getRecordId, record.getRecordId())
                        .orderByAsc(QcRecordItem::getLineNo));
        return Result.success(Map.of("record", record, "items", items));
    }
}
