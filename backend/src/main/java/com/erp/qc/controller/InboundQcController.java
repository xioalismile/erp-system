package com.erp.qc.controller;

import com.erp.common.Result;
import com.erp.qc.dto.InboundQcSaveRequest;
import com.erp.qc.entity.QcRecord;
import com.erp.qc.service.InboundQcService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 入库单质检控制器（采购入库 / 生产入库 / 委外入库内嵌质检）。
 * 路由: /api/qc/inbound
 */
@RestController
@RequestMapping("/api/qc/inbound")
@RequiredArgsConstructor
public class InboundQcController {

    private final InboundQcService inboundQcService;

    /** 质检上下文：可用单据级模板、已保存质检记录、入库明细行、默认报废仓 */
    @GetMapping("/context")
    public Result<Map<String, Object>> context(@RequestParam String billType, @RequestParam Long billId) {
        return Result.success(inboundQcService.context(billType, billId));
    }

    /** 保存质检（写质检记录并回写入库单合格/不合格数量） */
    @PostMapping("/save")
    public Result<QcRecord> save(@RequestBody InboundQcSaveRequest request) {
        return Result.success(inboundQcService.save(request));
    }
}
