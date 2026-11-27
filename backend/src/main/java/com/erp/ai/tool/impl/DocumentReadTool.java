package com.erp.ai.tool.impl;

import com.erp.ai.knowledge.service.DocumentParserService;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.AiUploadFileReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：读文档（Word/PDF/txt/md）。
 *
 * <p>与 {@code import_file_read}（Excel/CSV 表格）分工：本工具负责**文档**——把正文读成文字
 * （Word 含表格，按行用 " | " 分隔），并把**文档里的图片**抽出来存进系统上传目录、返回可访问的
 * 图片地址（供"建资料时把文档里的图带进去"）。Excel/CSV 仍走 import_file_read（本工具会引导）。
 *
 * <p>历史问题（用户 2026-10-10 反馈）：Word/PDF 解析能力只挂在"AI知识库"上，对话链路没有，
 * 用户发来 Word 形式的工单/资料时，模型拿不到任何内容。本工具把该能力接入对话链路。
 *
 * <p>安全：文件路径统一经 {@link AiUploadFileReader#resolveFile}（仅 /uploads/ 前缀 + 规范化后
 * 必须落在 erp.upload.path 内），本工具不自行拼接任何外部路径；解析失败一律降级为明确中文提示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentReadTool implements AiTool {

    private final AiUploadFileReader fileReader;
    private final DocumentParserService parserService;

    /** 本工具直接处理的文档类型；其余类型给出明确的引导 */
    private static final List<String> DOC_TYPES = List.of("docx", "pdf", "txt", "md");
    private static final List<String> EXCEL_TYPES = List.of("xlsx", "xls", "csv");

    @Override
    public String getName() {
        return "document_read";
    }

    @Override
    public String getDescription() {
        return "【只读】读取用户上传的文档内容（Word/PDF/txt/md），返回正文文字与文档内图片的地址。"
                + "Word 会连**表格**一起读（每行各列用 | 分隔，第一行是表头）；文档里的**图片**会被抽出来存成系统图片地址，"
                + "返回在 images 字段里，可直接用于后续\"建物料/建资料时带上图片\"或再调用 image_read 读图里内容。"
                + "适用场景：用户上传 Word/PDF 形式的单据、工单、资料、模板并要求\"分析/照着建/录入系统\"时，先调用本工具。"
                + "注意：Excel/CSV 表格请用 import_file_read；参数：fileUrl（必填，原样取自本次附件列表）。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("fileUrl", Map.of("type", "string",
                "description", "文档地址，原样取自本次用户上传的附件列表（以 /uploads/ 开头，严禁编造或拼接）"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("fileUrl"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        String fileUrl = params == null ? null : (String) params.get("fileUrl");
        if (fileUrl == null || fileUrl.isBlank()) {
            return fail("请提供文档地址（fileUrl）");
        }
        File file = fileReader.resolveFile(fileUrl);
        if (file == null || !file.exists()) {
            return fail("文件不存在或不在系统上传目录内：" + fileUrl);
        }
        String ext = extOf(file.getName());

        // Excel/CSV：复用既有表格读取链路，避免同一件事两份实现
        if (EXCEL_TYPES.contains(ext)) {
            Map<String, Object> excel = fileReader.read(fileUrl);
            excel.put("fileType", ext);
            excel.put("hint", "这是 Excel/CSV 表格内容。若要做数据导入，请按 import_file_read / import_template_list 的流程走。");
            return excel;
        }
        if (!DOC_TYPES.contains(ext)) {
            return fail("document_read 支持 docx/pdf/txt/md（表格请用 import_file_read）；当前文件类型：" + ext);
        }

        try {
            String text = parserService.parse(file, ext);
            if (text == null) text = "";

            List<DocumentParserService.DocImage> docImages = parserService.extractImages(file, ext);
            List<String> imageUrls = new ArrayList<>();
            String baseName = baseNameOf(file.getName());
            for (DocumentParserService.DocImage img : docImages) {
                // 落盘统一走 AiUploadFileReader（与 Excel 嵌入图片同一份实现，禁止两处各写一份）
                String url = fileReader.saveExtractedImage(img.getData(), baseName + "_" + img.getFileName());
                if (url != null) imageUrls.add(url);
            }

            if (text.isBlank() && imageUrls.isEmpty()) {
                return fail("没能从这份文档里读出有效内容（可能是纯图片型 PDF 或空文档）；"
                        + "若是扫描件，请把页面截图后按图片上传，我会用 image_read 读取。");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("fileType", ext);
            result.put("text", text);
            result.put("images", imageUrls);
            result.put("imageCount", imageUrls.size());
            result.put("hint", "以上是文档正文（**全量、未截断**）；images 里是文档内抽出的图片地址——"
                    + "需要图片里的信息时用 image_read 逐张读，**禁止说\"图片只有数量、读不到内容\"**。"
                    + "请自行理解内容，把要录入的字段/明细先展示给用户确认，"
                    + "确认后再调用相应的创建/导入工具；缺失信息要问用户，不要编造。");
            return result;
        } catch (Exception e) {
            log.warn("[读文档] 失败 fileUrl={}", fileUrl, e);
            return fail("文档读取失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private String extOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    private String baseNameOf(String fileName) {
        if (fileName == null) return "doc";
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base.isBlank() ? "doc" : base;
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message);
        return m;
    }
}
