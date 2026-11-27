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
import java.util.Map;

/**
 * 写工具：通用文件导入（用户上传的 /uploads 内文件 → 复用界面导入同一链路）。
 *
 * <p>安全：
 * <ul>
 *   <li>文件路径三处校验，统一走 {@link AiUploadFileReader#resolveFile}（不自行拼路径）；</li>
 *   <li>entityName 必须命中后端白名单（{@link ExcelService#getImportableEntities}），AI 不得扩展；</li>
 *   <li>权限走 {@link ImportPermissionResolver#check}（执行层按具体实体严格判定）；</li>
 *   <li>不绕过后端既有校验/事务，一律调 {@link ExcelService}；AI 不默认选覆盖/跳过策略。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportFileTool implements AiTool {

    private final ExcelService excelService;
    private final AiUploadFileReader fileReader;
    private final ImportExecSupport execSupport;
    private final ImportPermissionResolver permissionResolver;

    @Override
    public String getName() {
        return "import_file";
    }

    @Override
    public String getDescription() {
        return "【写操作，用户确认后执行】把用户上传的 Excel/CSV 文件导入到指定实体（客户/供应商/物料/销售订单/采购订单等）。"
                + "标准流程：先用 import_template_list 拿模板列 → 用 import_file_read 读文件 → 对照校验并把文件与目标实体、预计行数"
                + "展示给用户确认 → 用户确认后再调用本工具。物料导入请改用 material_import_check + material_import_confirm 两段式；"
                + "customer 可传 poolType（0 私海/1 公海），不传则按系统默认；缺列/缺必填/列名不符时拒绝导入，不猜测映射。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> entityName = new LinkedHashMap<>();
        entityName.put("type", "string");
        entityName.put("description", "目标实体名（必须来自 import_template_list 返回的实体清单，如 customer/supplier/material/saleOrder/purchaseOrder）");
        props.put("entityName", entityName);
        Map<String, Object> fileUrl = new LinkedHashMap<>();
        fileUrl.put("type", "string");
        fileUrl.put("description", "待导入文件的URL或路径（以 /uploads/ 开头），原样取自本次用户上传的附件列表");
        props.put("fileUrl", fileUrl);
        Map<String, Object> poolType = new LinkedHashMap<>();
        poolType.put("type", "integer");
        poolType.put("description", "可选，仅 customer 有效：0=私海（归属当前用户）/1=公海（无归属人）；不传则按系统默认");
        props.put("poolType", poolType);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of("entityName", "fileUrl"));
        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        String entityName = SettingSupport.str(params == null ? null : params.get("entityName"));
        if (entityName == null || !excelService.getImportableEntities().contains(entityName)) {
            return fail("不支持导入的实体: " + entityName + "。请先调用 import_template_list 查询可导入实体清单。");
        }
        // 物料/BOM 有专用链路：物料必须两段式（冲突策略由用户明确选择），BOM 必须走标准行导入；
        // 用本工具会落进"默认策略"分支，风险不可控，故直接拦下并引导到正确的工具。
        if ("material".equals(entityName)) {
            return fail("物料导入请走两段式：先调 material_import_check 预检查，"
                    + "由用户明确选择冲突策略（SKIP=跳过已存在 / OVERWRITE=覆盖）后，再调 material_import_confirm。");
        }
        if ("bom".equals(entityName)) {
            return fail("BOM导入请先调 bom_file_read 读取文件内容，转换成系统标准BOM行并展示给用户确认后，"
                    + "再调 bom_import_rows 导入。");
        }
        String deny = permissionResolver.check(entityName, scope);
        if (deny != null) return fail(deny);

        String fileUrl = SettingSupport.str(params.get("fileUrl"));
        if (fileUrl == null) return fail("未提供文件路径（fileUrl）");
        File file = fileReader.resolveFile(fileUrl);
        if (file == null || !file.exists()) {
            return fail("文件不存在或不在系统上传目录内: " + fileUrl);
        }

        Integer poolType = SettingSupport.integer(params.get("poolType"));
        // poolType 仅 customer 有效；其它实体忽略
        if (!"customer".equals(entityName)) {
            poolType = null;
        }

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            MultipartFile mf = new InMemoryMultipartFile(file.getName(), bytes);
            ImportResultDTO imp = execSupport.dispatch(entityName, mf, poolType, scope == null ? null : scope.getUserId());
            Map<String, Object> result = ImportExecSupport.summarize(imp, permissionResolver.entityLabel(entityName));
            log.info("[AI导入] 用户{} 实体{} 文件{} 成功{} 失败{}", scope == null ? null : scope.getUsername(),
                    entityName, file.getName(), imp.getSuccessCount(), imp.getFailCount());
            return result;
        } catch (Exception e) {
            log.error("[AI导入] 文件导入失败 entity={} file={}", entityName, fileUrl, e);
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
