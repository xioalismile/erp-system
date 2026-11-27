package com.erp.board.custom.controller;

import com.erp.board.custom.service.CustomBoardService;
import com.erp.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 卡片式自定义看板（数据看板 → 自定义看板）+ 工作台自定义卡。
 *
 * <p>路由：/api/board/custom/**（字面量路径优先于 /{id} 通配）。
 * 全部接口经统一登录鉴权；看板可见性（私有 / 按角色共享）在服务层二次校验。</p>
 */
@RestController
@RequestMapping("/api/board/custom")
@RequiredArgsConstructor
public class CustomBoardController {

    private final CustomBoardService customBoardService;

    /** 列表（boardType=board / workbench） */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(HttpServletRequest req,
                                            @RequestParam(defaultValue = "board") String boardType,
                                            @RequestParam(required = false) String keyword) {
        return Result.success(customBoardService.list(req, boardType, keyword));
    }

    /** 当前登录人的工作台自定义卡（没有则空清单） */
    @GetMapping("/workbench")
    public Result<Map<String, Object>> workbench(HttpServletRequest req) {
        return Result.success(customBoardService.workbench(req));
    }

    /** 保存当前登录人的工作台自定义卡（最多 6 张，只影响本人） */
    @PutMapping("/workbench")
    public Result<Map<String, Object>> saveWorkbench(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        return Result.success(customBoardService.saveWorkbench(req, body));
    }

    /** 现有看板页（财务/销售/仓储/经营）的卡片布局：显隐 / 顺序 / 尺寸 / 自定义取数 / 新增卡 */
    @GetMapping("/page")
    public Result<Map<String, Object>> pageLayout(HttpServletRequest req, @RequestParam String key) {
        return Result.success(customBoardService.pageLayout(req, key));
    }

    @PutMapping("/page")
    public Result<Map<String, Object>> savePageLayout(HttpServletRequest req, @RequestParam String key,
                                                      @RequestBody Map<String, Object> body) {
        return Result.success(customBoardService.savePageLayout(req, key, body));
    }

    /** 设计器可用的数据源清单 */
    @GetMapping("/meta/sources")
    public Result<List<Map<String, Object>>> sources() {
        return Result.success(customBoardService.sources());
    }

    /** 某数据源的字段清单（维度 / 数值 / 时间 标记） */
    @GetMapping("/meta/fields")
    public Result<List<Map<String, Object>>> fields(@RequestParam String source) {
        return Result.success(customBoardService.fields(source));
    }

    /** 某数据源可关联的单据清单（多级关联配置用；含各关联单字段） */
    @GetMapping("/meta/joins")
    public Result<List<Map<String, Object>>> joins(@RequestParam String source) {
        return Result.success(customBoardService.joins(source));
    }

    /** 设计器单卡预览（不落库） */
    @PostMapping("/preview")
    public Result<Map<String, Object>> preview(HttpServletRequest req, @RequestBody Map<String, Object> body,
                                               @RequestParam(defaultValue = "month") String period) {
        Object card = body.get("card");
        if (!(card instanceof Map<?, ?>)) throw new IllegalArgumentException("缺少卡片配置");
        Map<String, Object> c = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) card).entrySet()) c.put(String.valueOf(e.getKey()), e.getValue());
        return Result.success(customBoardService.preview(c, period, req));
    }

    /** 卡片下钻：点数字/图表点 → 单据级明细清单 */
    @PostMapping("/preview/drill")
    public Result<Map<String, Object>> drill(HttpServletRequest req, @RequestBody Map<String, Object> body,
                                             @RequestParam(defaultValue = "month") String period,
                                             @RequestParam(required = false) String dimLabel,
                                             @RequestParam(required = false, defaultValue = "200") Integer limit,
                                             @RequestParam(required = false) Integer itemIndex) {
        Object card = body.get("card");
        if (!(card instanceof Map<?, ?>)) throw new IllegalArgumentException("缺少卡片配置");
        Map<String, Object> c = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) card).entrySet()) c.put(String.valueOf(e.getKey()), e.getValue());
        return Result.success(customBoardService.drill(req, c, period, dimLabel, limit, itemIndex));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(HttpServletRequest req, @PathVariable Long id) {
        return Result.success(customBoardService.detail(req, id));
    }

    /** 逐卡取数（单卡异常隔离） */
    @GetMapping("/{id}/data")
    public Result<Map<String, Object>> data(HttpServletRequest req, @PathVariable Long id,
                                            @RequestParam(defaultValue = "month") String period) {
        return Result.success(customBoardService.data(req, id, period));
    }

    @PostMapping
    public Result<Map<String, Object>> create(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        return Result.success(customBoardService.create(req, body));
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(HttpServletRequest req, @PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        return Result.success(customBoardService.update(req, id, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest req, @PathVariable Long id) {
        customBoardService.delete(req, id);
        return Result.success();
    }
}
