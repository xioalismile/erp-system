package com.erp.qc.spc;

import com.erp.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * SPC 质量看板（数据看板 → SPC质量看板）。
 *
 * <p>只读接口：检验项候选 + 全量分析。全部 SPC 标准公式（控制限 / Cp / Cpk / 八条判异）
 * 在服务端统一计算，前端只做呈现。</p>
 */
@RestController
@RequestMapping("/api/spc")
@RequiredArgsConstructor
public class SpcController {

    private final SpcAnalysisService spcAnalysisService;

    /** 检验项候选（仅数值型实测值；period/orderNo/operationName 与分析接口同口径） */
    @GetMapping("/items")
    public Result<List<Map<String, Object>>> items(@RequestParam(defaultValue = "year") String period,
                                                   @RequestParam(required = false) String orderNo,
                                                   @RequestParam(required = false) String operationName) {
        LocalDate[] r = SpcAnalysisService.resolveRange(period);
        return Result.success(spcAnalysisService.items(r[0], r[1], orderNo, operationName));
    }

    /** 全量分析：KPI + X̄-R / I-MR / P 图 + Cp/Cpk + 判异明细 + 实测值序列 */
    @GetMapping("/analysis")
    public Result<Map<String, Object>> analyze(@RequestParam String item,
                                               @RequestParam(defaultValue = "month") String period,
                                               @RequestParam(required = false) String orderNo,
                                               @RequestParam(required = false) String operationName) {
        LocalDate[] r = SpcAnalysisService.resolveRange(period);
        return Result.success(spcAnalysisService.analyze(item, r[0], r[1], orderNo, operationName));
    }
}
