package com.erp.acquisition.controller;

import com.erp.acquisition.entity.AcquisitionResult;
import com.erp.acquisition.service.AcquisitionResultService;
import com.erp.ai.service.AiVisionService;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/acquisition/result")
@RequiredArgsConstructor
public class AcquisitionResultController {

    private final AcquisitionResultService acquisitionResultService;
    private final AiVisionService aiVisionService;
    private final ObjectMapper objectMapper;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 分页查询（支持taskId/keyword/reviewStatus筛选；按所属采集任务归属套数据权限） */
    @GetMapping("/page")
    public Result<PageResult<AcquisitionResult>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String reviewStatus,
            jakarta.servlet.http.HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(acquisitionResultService.pageQuery(current, size, taskId, keyword, reviewStatus, scopeCtx, sortBy, sortOrder));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public Result<AcquisitionResult> detail(@PathVariable Long id) {
        return Result.success(acquisitionResultService.getDetail(id));
    }

    /** 批量保存采集结果（内部调用） */
    @PostMapping("/batch")
    public Result<List<AcquisitionResult>> batchSave(@RequestBody List<AcquisitionResult> results) {
        return Result.success(acquisitionResultService.batchSave(results));
    }

    /** 审核单个结果 */
    @PutMapping("/{id}/review")
    public Result<Void> review(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long reviewerId = body.get("reviewerId") != null ? Long.valueOf(body.get("reviewerId")) : null;
        acquisitionResultService.review(id, body.get("reviewStatus"), reviewerId, body.get("rejectReason"));
        return Result.success();
    }

    /** 批量导入CRM线索 */
    @PostMapping("/batch-import")
    public Result<Integer> batchImport(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) body.get("resultIds");
        if (rawIds == null || rawIds.isEmpty()) {
            return Result.error("请选择要导入的记录");
        }
        List<Long> resultIds = rawIds.stream().map(Integer::longValue).toList();
        Long assignedSalesId = body.get("assignedSalesId") != null
                ? Long.valueOf(body.get("assignedSalesId").toString())
                : null;
        int success = acquisitionResultService.batchImportToLeads(resultIds, assignedSalesId);
        return Result.success(success);
    }

    /** 获取待审核数量（与列表同数据权限口径） */
    @GetMapping("/pending-count")
    public Result<Long> pendingCount(@RequestParam(required = false) Long taskId,
                                     jakarta.servlet.http.HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(acquisitionResultService.countPending(taskId, scopeCtx));
    }

    /** 删除单个采集结果（逻辑删除） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        acquisitionResultService.deleteById(id);
        return Result.success();
    }

    /** 批量删除采集结果（逻辑删除） */
    @PostMapping("/batch-delete")
    public Result<Integer> batchDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) body.get("resultIds");
        if (rawIds == null || rawIds.isEmpty()) {
            return Result.error("请选择要删除的记录");
        }
        List<Long> ids = rawIds.stream().map(Integer::longValue).toList();
        return Result.success(acquisitionResultService.batchDelete(ids));
    }

    /** 按任务清空采集结果（用于重新采集前清空旧数据，避免累积） */
    @DeleteMapping("/task/{taskId}/clear")
    public Result<Integer> clearByTask(@PathVariable Long taskId) {
        return Result.success(acquisitionResultService.deleteByTaskId(taskId));
    }

    /** AI识别截图提取企业信息
     * 传入采集结果ID，调用AI视觉模型识别截图，提取企业字段并回写到result记录
     */
    @PostMapping("/{id}/ai-recognize")
    public Result<AcquisitionResult> aiRecognize(@PathVariable Long id) {
        AcquisitionResult result = acquisitionResultService.getDetail(id);
        if (result.getScreenshotPath() == null || result.getScreenshotPath().isBlank()) {
            return Result.error("该采集结果无截图");
        }

        // screenshotPath是JSON数组，取第一张
        String screenshotPath = result.getScreenshotPath();
        String firstImage;
        try {
            if (screenshotPath.startsWith("[")) {
                List<String> paths = objectMapper.readValue(screenshotPath, List.class);
                if (paths.isEmpty()) return Result.error("截图列表为空");
                firstImage = paths.get(0);
            } else {
                firstImage = screenshotPath;
            }
        } catch (Exception e) {
            firstImage = screenshotPath;
        }

        // 调用AI视觉识别
        String prompt = "请识别这张企查查企业信息截图，提取以下字段并以JSON格式返回：\n" +
                "{\n" +
                "  \"companyName\": \"公司名称\",\n" +
                "  \"creditCode\": \"统一社会信用代码\",\n" +
                "  \"legalPerson\": \"法定代表人\",\n" +
                "  \"registeredCapital\": \"注册资本\",\n" +
                "  \"contactPhone\": \"联系电话\",\n" +
                "  \"contactEmail\": \"联系邮箱\",\n" +
                "  \"registeredAddress\": \"注册地址\",\n" +
                "  \"businessScope\": \"经营范围\",\n" +
                "  \"industry\": \"所属行业\",\n" +
                "  \"establishDate\": \"成立日期\",\n" +
                "  \"companyStatus\": \"企业状态\"\n" +
                "}\n" +
                "只返回JSON，不要其他文字。如某字段在截图中无法识别，填空字符串。";

        try {
            String aiResult = aiVisionService.recognizeImage(firstImage, prompt);
            log.info("AI识别完成 resultId={} 原始结果: {}", id, aiResult);

            // 保存原始AI结果
            result.setAiRawResult(aiResult);

            // 尝试解析JSON并回填字段
            String jsonStr = extractJson(aiResult);
            if (jsonStr != null) {
                @SuppressWarnings("unchecked")
                Map<String, String> fields = objectMapper.readValue(jsonStr, Map.class);
                if (fields.get("companyName") != null && !fields.get("companyName").isEmpty()) {
                    result.setCompanyName(fields.get("companyName"));
                }
                if (fields.get("creditCode") != null) result.setCreditCode(fields.get("creditCode"));
                if (fields.get("legalPerson") != null) result.setLegalPerson(fields.get("legalPerson"));
                if (fields.get("registeredCapital") != null) result.setRegisteredCapital(fields.get("registeredCapital"));
                if (fields.get("contactPhone") != null) result.setContactPhone(fields.get("contactPhone"));
                if (fields.get("contactEmail") != null) result.setContactEmail(fields.get("contactEmail"));
                if (fields.get("registeredAddress") != null) result.setRegisteredAddress(fields.get("registeredAddress"));
                if (fields.get("businessScope") != null) result.setBusinessScope(fields.get("businessScope"));
                if (fields.get("industry") != null) result.setIndustry(fields.get("industry"));
                if (fields.get("establishDate") != null) result.setEstablishDate(fields.get("establishDate"));
                if (fields.get("companyStatus") != null) result.setCompanyStatus(fields.get("companyStatus"));
            }
            acquisitionResultService.updateById(result);
            log.info("AI识别字段已回写 resultId={}", id);
        } catch (Exception e) {
            log.error("AI识别失败 resultId={}", id, e);
            return Result.error("AI识别失败: " + e.getMessage());
        }

        return Result.success(acquisitionResultService.getDetail(id));
    }

    /** 批量AI识别 */
    @PostMapping("/batch-ai-recognize")
    public Result<Map<String, Object>> batchAiRecognize(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("resultIds");
        if (ids == null || ids.isEmpty()) {
            return Result.error("请选择要识别的记录");
        }

        int success = 0, fail = 0;
        for (Integer idInt : ids) {
            Long id = idInt.longValue();
            try {
                aiRecognize(id);
                success++;
            } catch (Exception e) {
                log.error("批量AI识别失败 resultId={}", id, e);
                fail++;
            }
        }
        return Result.success(Map.of("success", success, "fail", fail, "total", ids.size()));
    }

    /** 从AI返回文本中提取JSON */
    private String extractJson(String text) {
        if (text == null) return null;
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }
}
