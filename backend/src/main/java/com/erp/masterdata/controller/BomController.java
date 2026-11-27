package com.erp.masterdata.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.masterdata.entity.Bom;
import com.erp.masterdata.entity.BomItem;
import com.erp.masterdata.mapper.BomItemMapper;
import com.erp.masterdata.service.BomService;
import com.erp.sample.entity.SampleOrder;
import com.erp.sample.mapper.SampleOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * BOM 物料清单控制器
 *
 * 接口对齐UI截图（生产管理 → BOM）：
 * - 列表页：查询 / 重置 / 列设置
 * - 列表按钮：新建/复制/批量提交/批量更新/更新默认BOM编号/打印/删除/更新损耗率/批量导入/导出Excel
 * - 详情页：新建/提交/保存/刷新
 * - 状态流转：审核/停用/启用
 * - 关联：BOM展开/扁平化
 */
@RestController
@RequestMapping("/api/masterdata/bom")
@RequiredArgsConstructor
public class BomController {

    private final BomService bomService;
    private final BomItemMapper bomItemMapper;
    private final SampleOrderMapper sampleOrderMapper;
    /** 统一数据权限解析器：全量列表 /list 与列表页同口径（创建人列 created_by） */
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 分页查询（对齐UI列表查询区：单据编号/单据日期/BOM分类/母件编号/名称/规格/规格属性/色号/产地/材质/状态/单据状态） */
    @GetMapping("/page")
    public Result<PageResult<Bom>> page(@RequestParam(defaultValue = "1") int current,
                                        @RequestParam(defaultValue = "10") int size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String bomCategory,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String isEnabled,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String customSearch) {
        return Result.success(bomService.pageQuery(current, size, keyword, bomCategory, status, isEnabled, dateFrom, dateTo, sortBy, sortOrder, customSearch));
    }

    /** 全量列表（2026-10-09 权限收口：与 BOM 列表页同口径，按角色数据范围过滤，创建人列 created_by） */
    @GetMapping("/list")
    public Result<List<Bom>> list() {
        QueryWrapper<Bom> wrapper = new QueryWrapper<>();
        dataScopeResolver.applyIfLimited(wrapper, null, "created_by");
        return Result.success(bomService.list(wrapper));
    }

    /** 详情(含明细) */
    @GetMapping("/{id}")
    public Result<Bom> detail(@PathVariable Long id) {
        return Result.success(bomService.getDetail(id));
    }

    /** 新增 */
    @PostMapping
    public Result<Bom> create(@RequestBody Bom bom) {
        bomService.saveBomWithItems(bom, bom.getItems());
        return Result.success(bom);
    }

    /** 编辑 */
    @PutMapping("/{id}")
    public Result<Bom> update(@PathVariable Long id, @RequestBody Bom bom) {
        bom.setBomId(id);
        bomService.saveBomWithItems(bom, bom.getItems());
        return Result.success(bom);
    }

    /** 删除（级联清理BOM子项，并解锁来源打样单的进度锁定） */
    @DeleteMapping("/{id}")
    @Transactional
    public Result<Void> delete(@PathVariable Long id) {
        bomService.removeById(id);
        bomItemMapper.delete(new QueryWrapper<BomItem>().eq("bom_id", id));
        unlockSampleLink(List.of(id));
        return Result.success();
    }

    /** 批量删除（级联清理BOM子项，并解锁来源打样单的进度锁定） */
    @DeleteMapping("/batch")
    @Transactional
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            bomService.removeByIds(ids);
            bomItemMapper.delete(new QueryWrapper<BomItem>().in("bom_id", ids));
            unlockSampleLink(ids);
        }
        return Result.success();
    }

    /** BOM删除后解锁来源打样单：is_bom_generated 回置0、generated_bom_id 清空，允许再次修改进度 */
    private void unlockSampleLink(List<Long> bomIds) {
        UpdateWrapper<SampleOrder> uw = new UpdateWrapper<>();
        uw.in("generated_bom_id", bomIds)
                .set("is_bom_generated", 0)
                .set("generated_bom_id", null);
        sampleOrderMapper.update(null, uw);
    }

    /** 单据编号生成（UI: 系统自动生成） */
    @GetMapping("/generate-no")
    public Result<String> generateNo() {
        return Result.success(bomService.generateBomCode());
    }

    /** 提交审核: 草稿 → 待审批 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id,
                               @RequestHeader(value = "X-Operator", required = false) String operator) {
        bomService.submit(id, operator);
        return Result.success();
    }

    /** 审核: 待审批 → 已生效 */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        bomService.approve(id, operator);
        return Result.success();
    }

    /** 重置草稿: 已生效 → 草稿 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id,
                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        bomService.reject(id, operator);
        return Result.success();
    }

    /** 批量提交审核: 草稿 → 待审批 */
    @PostMapping("/batch-submit")
    public Result<Integer> batchSubmit(@RequestBody Map<String, Object> body,
                                        @RequestHeader(value = "X-Operator", required = false) String operator) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) body.get("ids");
        int count = bomService.batchSubmit(ids, operator);
        return Result.success(count);
    }

    /** 批量审核: 待审批 → 已生效 */
    @PostMapping("/batch-approve")
    public Result<Integer> batchApprove(@RequestBody Map<String, Object> body,
                                        @RequestHeader(value = "X-Operator", required = false) String operator) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) body.get("ids");
        int count = bomService.batchApprove(ids, operator);
        return Result.success(count);
    }

    /** 批量更新: 启用/禁用 (UI: 批量更新) */
    @PostMapping("/batch-update")
    public Result<Integer> batchUpdate(@RequestBody Map<String, Object> body,
                                       @RequestHeader(value = "X-Operator", required = false) String operator) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) body.get("ids");
        String status = (String) body.get("status");
        int count = bomService.batchUpdateStatus(ids, status, operator);
        return Result.success(count);
    }

    /** 停用: 已生效 → 已停用 */
    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id,
                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        bomService.disable(id, operator);
        return Result.success();
    }

    /** 启用: 已停用 → 已生效 */
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id,
                               @RequestHeader(value = "X-Operator", required = false) String operator) {
        bomService.enable(id, operator);
        return Result.success();
    }

    /** 设为默认BOM (UI: 默认BOM复选框) */
    @PostMapping("/{id}/set-default")
    public Result<Void> setDefault(@PathVariable Long id,
                                   @RequestHeader(value = "X-Operator", required = false) String operator) {
        bomService.setDefault(id, operator);
        return Result.success();
    }

    /** 更新BOM所有子项的损耗率 (UI: 更新损耗率) */
    @PostMapping("/{id}/update-scrap-rate")
    public Result<Integer> updateScrapRate(@PathVariable Long id,
                                           @RequestBody Map<String, Object> body,
                                           @RequestHeader(value = "X-Operator", required = false) String operator) {
        BigDecimal scrapRate = new BigDecimal(body.get("scrapRate").toString());
        int count = bomService.updateScrapRate(id, scrapRate, operator);
        return Result.success(count);
    }

    /** BOM 树形展开（bomId 可选：指定时从该BOM展开，否则按物料编码自动查找生效BOM） */
    @GetMapping("/expand")
    public Result<Map<String, Object>> expand(
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) Long bomId,
            @RequestParam(defaultValue = "1") BigDecimal quantity) {
        if (bomId != null) {
            return Result.success(bomService.expandBomByBomId(bomId, quantity));
        }
        return Result.success(bomService.expandBom(materialCode, quantity));
    }

    /** BOM 扁平化展开(按物料聚合) */
    @GetMapping("/flatten")
    public Result<List<Map<String, Object>>> flatten(
            @RequestParam String materialCode,
            @RequestParam(defaultValue = "1") BigDecimal quantity) {
        return Result.success(bomService.flattenBom(materialCode, quantity));
    }

    /** 重置草稿: 已生效 -> 草稿 */
    @PostMapping("/{id}/reverse-approve")
    public Result<Void> reverseApprove(@PathVariable Long id, HttpServletRequest req) {
        bomService.reverseApprove(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 作废 */
    @PostMapping("/{id}/void")
    public Result<Void> voidBill(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpServletRequest req) {
        bomService.voidBill(id, (String) req.getAttribute("currentUsername"), body.get("reason"));
        return Result.success();
    }

    /** 反作废 */
    @PostMapping("/{id}/unvoid")
    public Result<Void> unvoidBill(@PathVariable Long id, HttpServletRequest req) {
        bomService.unvoidBill(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

}
