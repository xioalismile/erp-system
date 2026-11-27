package com.erp.kai.service;

import com.erp.ai.adapter.CustomProviderAdapter;
import com.erp.ai.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 小楷独立版 · 会话标题自动生成（2026-10-19）。
 * 口径：用平台默认模型对首条用户输入做一次轻量总结（≤16 字），不调用客户自带模型、
 *      不计入客户用量、不扣积分（平台运营成本）；平台模型不可用时回退本地截断。
 * 仅当会话标题仍为默认「新对话」时调用方才会写入，手动重命名永不被覆盖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiSessionTitleService {

    private static final int MAX_TITLE_LEN = 16;
    private static final Pattern FILE_WRAPPER =
            Pattern.compile("^【用户上传了(文件|图片)：(.+?)（文件标识");

    private final KaiCreditService creditService;
    private final AiConfigService aiConfigService;

    /** 生成会话标题；失败返回 null（调用方保持「新对话」）。 */
    public String generateTitle(String firstUserInput) {
        String input = firstUserInput == null ? "" : firstUserInput.trim();
        if (input.isEmpty()) return null;
        String summarized = summarizeByModel(input);
        if (summarized != null && !summarized.isBlank()) return summarized;
        return localFallback(input);
    }

    /** 平台默认模型总结；任何异常静默降级。 */
    private String summarizeByModel(String input) {
        try {
            KaiCreditService.DefaultModel dm = creditService.defaultModel();
            if (dm == null || !dm.usable()) return null;
            CustomProviderAdapter adapter = new CustomProviderAdapter(
                    "session-title", dm.name(), dm.baseUrl(), dm.apiKey(), dm.model(), null, false);
            adapter.setConfigService(aiConfigService);
            if (!adapter.isEnabled()) return null;
            String reply = adapter.chat(
                    "你是会话命名助手。根据用户的第一条消息生成一个不超过" + MAX_TITLE_LEN
                            + "个字的简短中文会话标题，概括用户要做的事。"
                            + "直接输出标题本身：不要引号、书名号、句号和任何解释。",
                    input.length() > 500 ? input.substring(0, 500) : input);
            return sanitize(reply);
        } catch (Exception e) {
            log.info("[小楷独立版] 模型命名会话失败（回退本地截断）: {}", e.getMessage());
            return null;
        }
    }

    /** 本地兜底：文件/图片上传用文件名，否则截前 16 字。 */
    private String localFallback(String input) {
        Matcher m = FILE_WRAPPER.matcher(input);
        if (m.find()) {
            String fileName = m.group(2).trim();
            int dot = fileName.lastIndexOf('.');
            if (dot > 0) fileName = fileName.substring(0, dot);
            return fileName.isEmpty() ? null : fileName;
        }
        String flat = input.replaceAll("\\s+", " ").trim();
        return flat.isEmpty() ? null : truncate(flat);
    }

    /** 清洗模型输出：去换行/引号/书名号，超长截断。 */
    private String sanitize(String reply) {
        if (reply == null) return null;
        String s = reply.replaceAll("[\\r\\n]+", " ").trim();
        s = s.replaceAll("^[「『“\"'《<]+", "").replaceAll("[」』”\"'》>]+$", "").trim();
        s = s.split("[。；;，,]")[0].trim();
        if (s.isEmpty()) return null;
        return truncate(s);
    }

    private String truncate(String s) {
        if (s.codePointCount(0, s.length()) <= MAX_TITLE_LEN) return s;
        return s.substring(0, s.offsetByCodePoints(0, MAX_TITLE_LEN)) + "…";
    }
}
