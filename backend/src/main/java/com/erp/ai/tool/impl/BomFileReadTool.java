package com.erp.ai.tool.impl;

import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.AiUploadFileReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用文件读取工具（只读）：把用户上传的 Excel/CSV 内容原样读出来交给大模型。
 *
 * 设计原则：本工具**不做**任何字段映射与业务判断——"这份文件是什么、怎么转成系统标准BOM"
 * 完全由大模型负责。这样换任何新格式的文件都不用改代码（历史问题：把表头映射规则写死在
 * 导入工具里，遇到"生产制造单"这类非标准文件就直接失败）。
 *
 * 本工具不写库、不导入；导入由 bom_import_rows 负责。
 * 文件路径安全与全量读取统一在 {@link AiUploadFileReader}，本工具不自行拼路径。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BomFileReadTool implements AiTool {

    private final AiUploadFileReader fileReader;

    @Override
    public String getName() {
        return "bom_file_read";
    }

    @Override
    public String getDescription() {
        return "通用文件读取（只读，不写库）：把用户上传的 Excel/CSV 文件内容原样读出来（所有工作表、表头行、数据行），"
                + "不做任何字段映射或业务判断。当用户上传了文件并希望你转换 / 导入 / 分析时，**必须先调用本工具读取内容**，"
                + "再由你自己理解文件结构（标准BOM表、生产制造单、物料表、工艺表等都可以），"
                + "转换成系统标准BOM行后先展示给用户确认，最后才调用 bom_import_rows 导入。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> fileUrl = new LinkedHashMap<>();
        fileUrl.put("type", "string");
        fileUrl.put("description", "待读取文件的URL或路径（如 /uploads/xxx.xls），原样取自本次用户上传的附件列表");
        props.put("fileUrl", fileUrl);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of("fileUrl"));
        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        String fileUrl = params == null ? null : (String) params.get("fileUrl");
        Map<String, Object> result = fileReader.read(fileUrl);
        if (!Boolean.TRUE.equals(result.get("success"))) {
            return result;
        }
        result.put("hint", "以上是文件**全量**内容（所有行所有列，未截断；totalRows = returnedRows）。"
                + "单元格里的 ■ ● ☑ √ 表示勾选/打点，□ ○ ☐ 表示未勾选——请按符号判断，不要声称读不到；"
                + "每个工作表的 images 是嵌入图片清单（row=第几行、url=图片地址），需要图片里的信息时用 image_read 逐张读，"
                + "**禁止说\"图片只有数量、读不到内容\"**。"
                + "用户要求分析 / 导入 / 逐条列出时，**必须逐条覆盖所有数据行**，不能只举几个例子或只报数量；"
                + "行数多时分段列出，但不得漏行。"
                + "请自行理解表头与数据结构并转换成系统标准BOM行（母件编码/子件编码/用量为必填），"
                + "先把转换结果展示给用户确认；用户确认后再调用 bom_import_rows 导入。缺失的必填信息要问用户，不要编造。");
        return result;
    }
}
