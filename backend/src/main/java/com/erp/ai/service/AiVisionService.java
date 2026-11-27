package com.erp.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.gateway.AiChannelAdapter;
import com.erp.ai.util.AiModelContextHolder;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;

/**
 * AI视觉识别公共服务
 * 提供图片识别、物料匹配等通用能力，供图片下单、图片BOM等工具复用
 */
@Slf4j
@Service
public class AiVisionService {

    @Resource
    private MaterialService materialService;

    @Resource
    private List<AiChannelAdapter> adapters;

    @Resource
    private AiConfigService aiConfigService;

    /** 路径安全统一入口：upload 根 + 采集截图目录白名单校验（安全审计 H-2） */
    @Resource
    private com.erp.ai.tool.support.AiUploadFileReader uploadFileReader;

    private static final String DEFAULT_CHANNEL = null;

    /**
     * 识别图片内容（用"用户本次所选模型"，即小助手右下角模型选择器里的那一个）
     * @param imageUrl 图片URL或本地路径
     * @param prompt 识别提示词
     * @return 识别结果文本
     * @throws RuntimeException 识别失败时抛出带真实原因与换模型指引的异常（不再静默返回空串）
     */
    public String recognizeImage(String imageUrl, String prompt) {
        String resolvedUrl = resolveImageUrl(imageUrl);
        if (resolvedUrl == null) {
            throw new RuntimeException("图片无法读取（可能未上传成功或文件路径已失效）");
        }

        AiChannelAdapter adapter = getAdapter();
        String defaultPrompt = "请仔细识别这张图片中的物品，提取以下关键信息：\n" +
                "1. 物品名称\n2. 规格型号\n3. 颜色特征\n4. 品牌标识\n5. 其他特征\n请用中文清晰描述。";
        String usePrompt = (prompt != null && !prompt.isBlank()) ? prompt : defaultPrompt;

        try {
            return adapter.visionChat(
                    "你是一个专业的工业产品识别助手，擅长识别五金、机械、电子等物料产品。",
                    usePrompt,
                    Collections.singletonList(resolvedUrl)
            );
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            String modelName = AiModelContextHolder.getModelName();
            String modelText = (modelName == null) ? "当前所选模型" : "当前所选模型（" + modelName + "）";
            log.warn("[视觉识别] 识别失败: {}", reason);
            throw new RuntimeException(modelText + "无法识别图片：" + reason
                    + "。请点输入框右下角的模型选择换一个支持图片的模型（DeepSeek 用 deepseek-flash）");
        }
    }

    /**
     * 从识别结果中匹配物料（按名称/编码/型号）
     * @param recognitionResult 识别结果文本
     * @param limit 返回数量上限
     * @return 匹配到的物料列表
     */
    public List<Material> matchMaterials(String recognitionResult, int limit) {
        if (recognitionResult == null || recognitionResult.isBlank()) {
            return Collections.emptyList();
        }
        try {
            // 提取关键词
            List<String> keywords = extractKeywords(recognitionResult);
            QueryWrapper<Material> qw = new QueryWrapper<>();
            qw.in("status", "启用", "正常");
            if (!keywords.isEmpty()) {
                qw.and(w -> {
                    for (String kw : keywords) {
                        if (kw != null && kw.length() >= 2) {
                            w.or().like("material_name", kw)
                                    .or().like("full_name", kw)
                                    .or().like("model", kw)
                                    .or().like("material_code", kw);
                        }
                    }
                });
            }
            qw.last("LIMIT " + Math.max(1, limit));
            List<Material> result = materialService.list(qw);

            // 降级：返回所有启用物料
            if (result.isEmpty() && !keywords.isEmpty()) {
                QueryWrapper<Material> qw2 = new QueryWrapper<>();
                qw2.in("status", "启用", "正常");
                qw2.last("LIMIT " + Math.max(1, limit));
                result = materialService.list(qw2);
            }
            return result;
        } catch (Exception e) {
            log.warn("[物料匹配] 匹配失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 调用大模型进行文本对话（JSON输出）
     */
    public String chatForJson(String systemPrompt, String userMessage) {
        try {
            AiChannelAdapter adapter = getAdapter();
            return adapter.chatJson(systemPrompt, userMessage);
        } catch (Exception e) {
            log.warn("[文本对话] 失败", e);
            return "";
        }
    }

    private List<String> extractKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        if (text == null || text.isBlank()) return keywords;
        String[] lines = text.split("[\n,，;；、。]");
        for (String line : lines) {
            line = line.trim();
            if (line.contains("：")) {
                String value = line.substring(line.indexOf("：") + 1).trim();
                value = value.replace("【", "").replace("】", "").trim();
                if (value.length() >= 2 && value.length() <= 20) {
                    keywords.add(value);
                }
            } else if (line.length() >= 2 && line.length() <= 20) {
                keywords.add(line);
            }
        }
        return keywords;
    }

    /**
     * 解析图片来源（安全审计 H-2）：HTTP/data URI 直通；本地路径统一经
     * AiUploadFileReader 做 canonical 规范化 + 白名单根目录校验。
     * 历史实现直接 new File(url).exists() 即读，等于开放任意本地文件读取
     * （可读 application.yml 等敏感文件并随出站请求外送）。
     */
    public String resolveImageUrl(String url) {
        if (url == null || url.isBlank()) return null;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("data:image/")) return url;
        File file = uploadFileReader.resolveImageFile(url);
        if (file == null) return null;
        return fileToBase64(file.getPath());
    }

    private String fileToBase64(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) return null;
            byte[] bytes = new byte[(int) file.length()];
            try (FileInputStream fis = new FileInputStream(file)) {
                fis.read(bytes);
            }
            String ext = getFileExtension(filePath).toLowerCase();
            String mime = switch (ext) {
                case "png" -> "image/png";
                case "jpg", "jpeg" -> "image/jpeg";
                case "gif" -> "image/gif";
                case "webp" -> "image/webp";
                case "bmp" -> "image/bmp";
                default -> "image/jpeg";
            };
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.warn("[图片转Base64] 失败: {}", filePath, e);
            return null;
        }
    }

    private String getFileExtension(String fileName) {
        int dotIdx = fileName.lastIndexOf('.');
        return dotIdx >= 0 ? fileName.substring(dotIdx + 1) : "";
    }

    private AiChannelAdapter getAdapter() {
        // 优先用"用户本次所选模型"（用户 2026-09-28：我选那个模型就给我用那个模型，不走固定后台通道）
        AiChannelAdapter ctx = AiModelContextHolder.get();
        if (ctx != null && ctx.isEnabled()) {
            return ctx;
        }
        // 无请求上下文时（无界面的后台调用）仍按默认通道，行为与升级前一致
        String preferred = aiConfigService.getDefaultChannel();
        if (preferred != null) {
            for (AiChannelAdapter a : adapters) {
                if (a.getChannel().equalsIgnoreCase(preferred) && a.isEnabled()) return a;
            }
        }
        for (AiChannelAdapter a : adapters) {
            if (a.isEnabled()) return a;
        }
        throw new RuntimeException("没有可用的AI渠道");
    }
}
