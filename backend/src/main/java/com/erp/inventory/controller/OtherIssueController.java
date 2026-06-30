package com.erp.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.inventory.entity.OtherIssue;
import com.erp.inventory.service.OtherIssueService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/other-issues")
@RequiredArgsConstructor
public class OtherIssueController {

    private final OtherIssueService otherIssueService;

    /** 分页查询 */
    @GetMapping("/page")
    public Result<PageResult<OtherIssue>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.success(otherIssueService.pageQuery(current, size, keyword, status));
    }

    /** 全量列表 */
    @GetMapping("/list")
    public Result<List<OtherIssue>> list() {
        return Result.success(otherIssueService.list(new QueryWrapper<OtherIssue>().orderByDesc("created_at")));
    }

    /** 详情(含明细) */
    @GetMapping("/{id}")
    public Result<OtherIssue> detail(@PathVariable Long id) {
        return Result.success(otherIssueService.getDetail(id));
    }

    /** 新增 */
    @PostMapping
    public Result<OtherIssue> create(@RequestBody OtherIssue issue) {
        otherIssueService.saveIssueWithItems(issue, issue.getItems());
        return Result.success(issue);
    }

    /** 编辑 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody OtherIssue issue) {
        issue.setIssueId(id);
        otherIssueService.saveIssueWithItems(issue, issue.getItems());
        return Result.success();
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        otherIssueService.removeById(id);
        return Result.success();
    }

    /** 审核: 草稿 → 已审核 (扣减库存) */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestParam(required = false) String approver,
                                HttpServletRequest req) {
        String operator = (approver != null && !approver.isBlank())
                ? approver
                : (String) req.getAttribute("currentUsername");
        otherIssueService.approve(id, operator);
        return Result.success();
    }

    /** 关闭: 已审核 → 已关闭 */
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id,
                              @RequestParam(required = false) String reason,
                              HttpServletRequest req) {
        String closer = (String) req.getAttribute("currentUsername");
        otherIssueService.close(id, closer, reason);
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id,
                               @RequestParam(required = false) String reason) {
        otherIssueService.cancel(id, reason);
        return Result.success();
    }
}
