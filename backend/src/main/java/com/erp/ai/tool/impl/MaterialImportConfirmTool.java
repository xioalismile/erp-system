package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.imports.ImportPermissionResolver;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.AiUploadFileReader;
import com.erp.ai.tool.support.ImportExecSupport;
import com.erp.ai.tool.support.InMemoryMultipartFile;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.export.dto.ImportResultDTO;
import com.erp.export.service.ExcelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 写工具：物料导入确认（两段式第二段）。
 *
 * <p>冲突策略 {@code onExistingConflict} 必须由用户在对话中明确选择（SKIP/OVERWRITE）；
 * 未提供或非法时必须拒绝，返回"请先让用户选择…"，AI 不得代选。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialImportConfirmTool implements AiTool {

    private static final String POLICY_SKIP = "SKIP";
    private static final String POLICY_OVERWRITE = "OVERWRITE";
    private static final String ASK_POLICY = "请先让用户选择：跳过已存在（SKIP）还是覆盖（OVERWRITE）";

    private final ExcelService excelService;
    private final AiUploadFileReader fileReader;
    private final ImportPermissionResolver permissionResolver;

    @Override
    public String getName() {
        return "material_import_confirm";
    }

    @Override
    public String getDescription() {
        return "【写操作，用户确认后执行】物料导入确认：按用户明确选择的冲突策略导入已预检查的物料文件。"
                + "必须先调用 material_import_check 预检查，并由用户明确选择策略：SKIP=跳过已存在 / OVERWRITE=覆盖已存在；"
                + "不得代替用户选择。未提供策略时本工具会拒绝执行并要求用户先做选择。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> fileUrl = new LinkedHashMap<>();
        fileUrl.put("type", "string");
        fileUrl.put("description", "待导入文件的URL或路径（以 /uploads/ 开头），必须与 material_import_check 检查的是同一文件");
        props.put("fileUrl", fileUrl);
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("type", "string");
        policy.put("description", "已存在物料的处理策略（必填，由用户明确选择）：SKIP=跳过已存在 / OVERWRITE=覆盖已存在");
        policy.put("enum", List.of(POLICY_SKIP, POLICY_OVERWRITE));
        props.put("onExistingConflict", policy);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of("fileUrl", "onExistingConflict"));
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

        String policy = SettingSupport.str(params.get("onExistingConflict"));
        policy = policy == null ? null : policy.toUpperCase(Locale.ROOT);
        if (!POLICY_SKIP.equals(policy) && !POLICY_OVERWRITE.equals(policy)) {
            return fail(ASK_POLICY);
        }

        File file = fileReader.resolveFile(fileUrl);
        if (file == null || !file.exists()) {
            return fail("文件不存在或不在系统上传目录内: " + fileUrl);
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            MultipartFile mf = new InMemoryMultipartFile(file.getName(), bytes);
            ImportResultDTO imp = excelService.confirmMaterialImport(mf, policy);
            Map<String, Object> result = ImportExecSupport.summarize(imp, "物料");
            result.put("policy", policy);
            log.info("[AI物料导入] 用户{} 文件{} 策略{} 新增{} 覆盖{} 跳过{} 失败{}",
                    scope == null ? null : scope.getUsername(), file.getName(), policy,
                    imp.getSuccessCount(), imp.getUpdatedCount(), imp.getSkipCount(), imp.getFailCount());
            return result;
        } catch (Exception e) {
            log.error("[AI物料导入] 确认导入失败 file={}", fileUrl, e);
            return fail("导入失败: " + e.getMessage());
        }
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message);
        return m;
    }
}
