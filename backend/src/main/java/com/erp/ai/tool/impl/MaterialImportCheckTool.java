package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.imports.ImportPermissionResolver;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.AiUploadFileReader;
import com.erp.ai.tool.support.ImportExecSupport;
import com.erp.ai.tool.support.InMemoryMultipartFile;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.export.dto.MaterialImportCheckDTO;
import com.erp.export.service.ExcelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：物料导入预检查（不写库）。与界面物料导入弹窗同一来源
 * （{@link ExcelService#checkMaterialImport}）。
 *
 * <p>物料导入必须两段式：本工具出「新增/冲突/校验失败」统计与冲突清单 → 用户明确选择
 * SKIP/OVERWRITE → 再调 {@code material_import_confirm}。AI 不得代选策略。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialImportCheckTool implements AiTool {

    /** 冲突明细返回上限（防上下文爆炸） */
    private static final int MAX_CONFLICTS = 50;

    private final ExcelService excelService;
    private final AiUploadFileReader fileReader;
    private final ImportPermissionResolver permissionResolver;

    @Override
    public String getName() {
        return "material_import_check";
    }

    @Override
    public String getDescription() {
        return "【只读】物料导入预检查（不写库）：解析用户上传的物料 Excel，与物料库比对存在性，"
                + "返回总行数、可新增数、冲突数、校验失败数及冲突清单。物料导入必须先调用本工具，"
                + "由用户明确选择「跳过已存在（SKIP）」或「覆盖已存在（OVERWRITE）」后，再调用 material_import_confirm；不要替用户选策略。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> fileUrl = new LinkedHashMap<>();
        fileUrl.put("type", "string");
        fileUrl.put("description", "待检查文件的URL或路径（以 /uploads/ 开头），原样取自本次用户上传的附件列表");
        props.put("fileUrl", fileUrl);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of("fileUrl"));
        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        String deny = permissionResolver.check("material", scope);
        if (deny != null) return fail(deny);

        String fileUrl = SettingSupport.str(params == null ? null : params.get("fileUrl"));
        if (fileUrl == null) return fail("未提供文件路径（fileUrl）");
        File file = fileReader.resolveFile(fileUrl);
        if (file == null || !file.exists()) {
            return fail("文件不存在或不在系统上传目录内: " + fileUrl);
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            MultipartFile mf = new InMemoryMultipartFile(file.getName(), bytes);
            MaterialImportCheckDTO check = excelService.checkMaterialImport(mf);
            if (!check.isParseSuccess()) {
                return fail("文件解析失败: " + check.getParseError());
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("fileName", file.getName());
            result.put("totalCount", check.getTotalCount());
            result.put("newCount", check.getNewCount());
            result.put("conflictCount", check.getConflictCount());
            result.put("errorCount", check.getErrorCount());
            result.put("hasConflict", check.isHasConflict());

            List<Map<String, Object>> conflicts = new ArrayList<>();
            if (check.getConflicts() != null) {
                for (MaterialImportCheckDTO.ConflictItem c : check.getConflicts()) {
                    if (conflicts.size() >= MAX_CONFLICTS) break;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("rowIndex", c.getRowIndex());
                    item.put("matchType", c.getMatchType());
                    item.put("excelCode", c.getExcelCode());
                    item.put("excelName", c.getExcelName());
                    item.put("existingCode", c.getExistingCode());
                    item.put("existingName", c.getExistingName());
                    conflicts.add(item);
                }
            }
            result.put("conflicts", conflicts);
            List<String> errors = ImportExecSupport.aggregateErrors(check.getErrors());
            if (!errors.isEmpty()) result.put("errorMessages", errors);
            result.put("message", "物料预检查完成：共" + check.getTotalCount() + "行，可新增" + check.getNewCount()
                    + "行，冲突" + check.getConflictCount() + "行，校验失败" + check.getErrorCount() + "行。"
                    + (check.isHasConflict() ? "存在冲突，请让用户选择：跳过已存在（SKIP）还是覆盖已存在（OVERWRITE）？" : "无冲突。"));
            return result;
        } catch (Exception e) {
            log.error("[AI物料导入] 预检查失败 file={}", fileUrl, e);
            return fail("预检查失败: " + e.getMessage());
        }
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message);
        return m;
    }
}
