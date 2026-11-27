package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.imports.ImportPermissionResolver;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.AiUploadFileReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：通用文件读取（把 bom_file_read 的能力泛化到通用导入场景）。
 *
 * <p>与 {@code bom_file_read} 同源：文件路径安全与全量读取统一在 {@link AiUploadFileReader}
 * （仅 /uploads/ 前缀 + 规范化 + 必须在 erp.upload.path 内；全量读取不截断，嵌入图片按行抽出）。
 * 本工具不做字段映射、不写库；仅把文件原样读出交给模型。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportFileReadTool implements AiTool {

    private final AiUploadFileReader fileReader;
    private final ImportPermissionResolver permissionResolver;

    @Override
    public String getName() {
        return "import_file_read";
    }

    @Override
    public String getDescription() {
        return "【只读】读取用户上传的 Excel/CSV 文件内容（所有工作表、表头行、数据行），不做任何字段映射与业务判断。"
                + "支持 csv/xlsx/xls；**全量读取**（所有行所有列，不截断）；"
                + "单元格里的 ■ ● ☑ √ 表示勾选/打点，□ ○ ☐ 表示未勾选，请按符号判断（不要声称读不到）；"
                + "Excel 里的嵌入图片会**按行抽出**：每个工作表 images 里 row=第几行、url=图片地址，需要图片信息时用 image_read 读，"
                + "imageCount 为全簿总张数。"
                + "通用导入前必须先调用本工具读文件，再由你自行理解结构；对照 import_template_list 的模板列校验后展示给用户，"
                + "用户确认后才调用 import_file 或 import_rows 导入。缺失信息要问用户，不要编造。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> fileUrl = new LinkedHashMap<>();
        fileUrl.put("type", "string");
        fileUrl.put("description", "待读取文件的URL或路径（以 /uploads/ 开头，如 /uploads/ai-chat/xxx.xlsx），原样取自本次用户上传的附件列表");
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
        String deny = permissionResolver.checkAny(scope);
        if (deny != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("success", false);
            m.put("message", deny);
            return m;
        }
        String fileUrl = params == null ? null : (String) params.get("fileUrl");
        Map<String, Object> result = fileReader.read(fileUrl);
        if (!Boolean.TRUE.equals(result.get("success"))) {
            return result;
        }
        result.put("hint", "以上是文件**全量**内容（所有行所有列，未截断；totalRows = returnedRows）。"
                + "用户要求逐条列出时**必须覆盖所有数据行**，不能只举例或只报数量。"
                + "单元格里的 ■ ● ☑ √ 表示勾选，□ ○ ☐ 表示未勾选；"
                + "每个工作表的 images 是嵌入图片清单（row=第几行、url=图片地址），需要图片信息时用 image_read 逐张读，"
                + "**禁止说\"图片只有数量、读不到内容\"**。"
                + "请先调用 import_template_list 拿到目标实体的模板列，"
                + "对照校验表头与必填列；把文件数据与目标实体、预计导入行数展示给用户确认后，再调用 import_file（文件导入）或 "
                + "import_rows（结构化行导入）。表头列不在模板中或必填列缺失时，如实报告差异，不要猜测映射。");
        return result;
    }
}
