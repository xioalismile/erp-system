package com.erp.inventory.warning.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.export.dto.ImportResultDTO;
import com.erp.inventory.warning.dto.InventoryWarningImportCheckDTO;
import com.erp.inventory.warning.dto.InventoryWarningRuleBatchDTO;
import com.erp.inventory.warning.entity.InventoryWarningLog;
import com.erp.inventory.warning.entity.InventoryWarningRule;
import com.erp.inventory.warning.service.InventoryWarningService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/inventory-warning")
@RequiredArgsConstructor
public class InventoryWarningController {

    private final InventoryWarningService inventoryWarningService;

    @GetMapping("/rule")
    public Result<PageResult<InventoryWarningRule>> ruleList(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(inventoryWarningService.pageQuery(current, size, keyword, status, sortBy, sortOrder));
    }

    @GetMapping("/rule/{id}")
    public Result<InventoryWarningRule> ruleDetail(@PathVariable Long id) {
        return Result.success(inventoryWarningService.getDetail(id));
    }

    @PostMapping("/rule")
    public Result<Void> saveRule(@RequestBody InventoryWarningRule rule) {
        inventoryWarningService.saveRule(rule);
        return Result.success();
    }

    /** 批量新增：一次提交一批货品，每个货品生成一条规则 */
    @PostMapping("/rule/batch")
    public Result<Void> saveRuleBatch(@RequestBody InventoryWarningRuleBatchDTO dto) {
        inventoryWarningService.saveRuleBatch(dto);
        return Result.success();
    }

    /** 下载导入模板 */
    @GetMapping("/rule/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        byte[] data = inventoryWarningService.buildImportTemplate();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("库存预警规则导入模板", StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        response.getOutputStream().write(data);
        response.getOutputStream().flush();
    }

    /** 导入预检查（只读不写库） */
    @PostMapping("/rule/import/check")
    public Result<InventoryWarningImportCheckDTO> checkImport(@RequestParam("file") MultipartFile file) {
        return Result.success(inventoryWarningService.checkImport(file));
    }

    /** 导入确认：onExistingConflict = SKIP 跳过已存在 / OVERWRITE 覆盖已存在 */
    @PostMapping("/rule/import/confirm")
    public Result<ImportResultDTO> confirmImport(@RequestParam("file") MultipartFile file,
                                                 @RequestParam(value = "onExistingConflict", defaultValue = "SKIP") String onExistingConflict) {
        return Result.success(inventoryWarningService.confirmImport(file, onExistingConflict));
    }

    @PutMapping("/rule")
    public Result<Void> updateRule(@RequestBody InventoryWarningRule rule) {
        inventoryWarningService.updateRule(rule);
        return Result.success();
    }

    @DeleteMapping("/rule/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        inventoryWarningService.deleteRule(id);
        return Result.success();
    }

    @PutMapping("/rule/{id}/toggle")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        inventoryWarningService.toggleStatus(id);
        return Result.success();
    }

    @GetMapping("/log")
    public Result<PageResult<InventoryWarningLog>> warningList(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer isHandled) {
        return Result.success(inventoryWarningService.getWarningList(current, size, isHandled));
    }

    @PutMapping("/log/{id}/handle")
    public Result<Void> handleWarning(@PathVariable Long id, @RequestParam(required = false) String handleRemark) {
        inventoryWarningService.handleWarning(id, handleRemark);
        return Result.success();
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(inventoryWarningService.getWarningStatistics());
    }

    @PostMapping("/check")
    public Result<Void> checkWarning() {
        inventoryWarningService.checkWarning();
        return Result.success();
    }
}
