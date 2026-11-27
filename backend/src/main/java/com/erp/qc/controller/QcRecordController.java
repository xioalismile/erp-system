package com.erp.qc.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.query.ListQuerySupport;
import com.erp.qc.entity.QcRecord;
import com.erp.qc.entity.QcRecordItem;
import com.erp.qc.service.QcRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工序检验记录 Controller（生产管理 → 工序质检报表）。
 * 路由: /api/qc/record
 *
 * <p>只读接口：分页列表 + 汇总 + 明细。检验记录由工序报工/检验流程写入，本控制器不做任何写操作。</p>
 */
@RestController
@RequestMapping("/api/qc/record")
@RequiredArgsConstructor
public class QcRecordController {

    private final QcRecordService qcRecordService;

    /** 分页查询（dateFrom/dateTo 按检验时间、qcType/result/orderNo/operationName/inspectorName 精筛，keyword 匹配检验单号/工单号） */
    @GetMapping("/page")
    public Result<PageResult<QcRecord>> page(@RequestParam(required = false) Integer current,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size,
                                             @RequestParam(required = false) String dateFrom,
                                             @RequestParam(required = false) String dateTo,
                                             @RequestParam(required = false) String qcType,
                                             @RequestParam(value = "result", required = false) String resultFilter,
                                             @RequestParam(required = false) String orderNo,
                                             @RequestParam(required = false) String operationName,
                                             @RequestParam(required = false) String inspectorName,
                                             @RequestParam(required = false) String keyword) {
        int[] p = ListQuerySupport.resolvePage(current, page, size);
        return Result.success(qcRecordService.pageQuery(p[0], p[1], dateFrom, dateTo, qcType,
                resultFilter, orderNo, operationName, inspectorName, keyword));
    }

    /** 汇总（与 /page 同一套筛选条件）：检验批次 / 合格批次 / 不合格批次 / 合格率 / 检验总数 / 合格数 / 不合格数 */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) String dateFrom,
                                               @RequestParam(required = false) String dateTo,
                                               @RequestParam(required = false) String qcType,
                                               @RequestParam(value = "result", required = false) String resultFilter,
                                               @RequestParam(required = false) String orderNo,
                                               @RequestParam(required = false) String operationName,
                                               @RequestParam(required = false) String inspectorName,
                                               @RequestParam(required = false) String keyword) {
        return Result.success(qcRecordService.summary(dateFrom, dateTo, qcType,
                resultFilter, orderNo, operationName, inspectorName, keyword));
    }

    /** 【第 19 期】某条检验记录的实测明细：逐项实测值 / 单项判定 / 留证图片（逗号分隔 URL） */
    @GetMapping("/{recordId}/items")
    public Result<List<QcRecordItem>> items(@PathVariable Long recordId) {
        return Result.success(qcRecordService.listItems(recordId));
    }
}
