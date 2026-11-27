package com.erp.ai.tool.impl;

import com.erp.ai.service.AiVisionService;
import com.erp.ai.tool.AiTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：看图读内容（通用）。
 *
 * <p>与图片三件套的分工：{@code image_search} 识物料 / {@code image_create_order} 按图下单 /
 * {@code image_create_bom} 按图录BOM 都是"业务动作"；本工具只做一件事——把图片里的内容
 * （文字、表格、字段名、版面）读成文字，交给模型理解，由模型决定后续动作。
 *
 * <p>历史问题（用户 2026-10-10 反馈）：上传报价单模板截图要求"按这张图建模板"时，模型手里
 * 没有任何能拿到图中文字的工具，只能如实回"读不了"。
 *
 * <p>复用现成视觉能力（{@link AiVisionService#recognizeImage}，走"用户本次所选模型"）；
 * 图片路径安全统一在 AiUploadFileReader 内做白名单校验，本工具不自行拼路径。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageReadTool implements AiTool {

    private final AiVisionService aiVisionService;

    @Override
    public String getName() {
        return "image_read";
    }

    @Override
    public String getDescription() {
        return "【只读】读取用户上传图片里的内容（文字、表格、字段名、版面），原样读成文字返回，不做业务判断。"
                + "适用场景：用户上传截图/照片/扫描件并要求\"读出里面的内容\"\"按这张图建模板/建单据/建资料\""
                + "\"这张单据（报价单/送货单/制造单）上写了什么\"时，先调用本工具拿到图中内容，再决定后续动作。"
                + "与其它图片工具的分工：找相似物料用 image_search；按图下单用 image_create_order；按图录BOM用 image_create_bom。"
                + "参数：imageUrl（必填，原样取自本次附件里的图片地址，严禁编造）；question（可选，想重点读什么）。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("imageUrl", Map.of("type", "string",
                "description", "图片地址，原样取自本次用户上传的图片列表（严禁编造或拼接路径）"));
        props.put("question", Map.of("type", "string",
                "description", "（可选）本次想重点读取的内容，如\"把这张报价单模板的字段与标题都读出来\"；留空则读取图中全部文字与表格"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("imageUrl"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        String imageUrl = params == null ? null : (String) params.get("imageUrl");
        if (imageUrl == null || imageUrl.isBlank()) {
            return fail("请提供图片地址（imageUrl）");
        }
        String question = params.get("question") == null ? "" : String.valueOf(params.get("question"));
        try {
            String text = aiVisionService.recognizeImage(imageUrl, buildPrompt(question));
            if (text == null || text.isBlank()) {
                return fail("没能从这张图片里读出有效内容，请确认图片清晰、且确实包含文字或表格");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("content", text);
            result.put("hint", "以上是从图片中读出的内容（原样读出，可能存在识别误差）。"
                    + "请先把关键字段向用户复述并确认，再执行写操作；看不清或图中没有的字段要问用户，不要凭空补全。");
            return result;
        } catch (Exception e) {
            String reason = e.getMessage() == null ? "图片识别失败" : e.getMessage();
            log.warn("[看图] 识别失败 imageUrl={}", imageUrl, e);
            return fail(reason);
        }
    }

    private String buildPrompt(String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("请把这张图片里的内容尽可能完整、原样地读出来，用于后续录入系统。要求：\n")
                .append("1. 逐行输出图中所有文字，保留原始顺序与层级（标题 / 表头 / 正文 / 页脚分开）。\n")
                .append("2. 表格请按“行”输出，每行内各列用 | 分隔，第一行是表头；不要漏行漏列。\n")
                .append("3. 勾选框请标注“已勾选 / 未勾选”；有明显合并单元格请注明。\n")
                .append("4. 不要翻译、不要总结、不要补全图中没有的内容；看不清的字用“□”占位。\n");
        if (question != null && !question.isBlank()) {
            sb.append("5. 本次用户特别关注：").append(question.trim()).append("\n");
        }
        return sb.toString();
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message);
        return m;
    }
}
