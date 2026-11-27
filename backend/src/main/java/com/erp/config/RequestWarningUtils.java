package com.erp.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 请求级预警收集器：设置中心「预警提示」策略命中后调用，
 * 把预警文案挂到本次响应头，由前端统一弹出提示（后端不阻断保存）。
 *
 * <p>只在有 HTTP 请求上下文时生效；单元测试/定时任务等无请求上下文场景静默跳过，
 * 不影响业务逻辑与异常处理。</p>
 */
@Slf4j
public final class RequestWarningUtils {

    /** 响应头名称（前端 request.js 统一读取） */
    public static final String HEADER_NAME = "X-Erp-Warning";

    /** 请求级累积属性名 */
    private static final String ATTR_NAME = "ERP_REQUEST_WARNINGS";

    /** 单次响应最多展示的预警条数，避免长单据把响应头撑爆 */
    private static final int MAX_MESSAGES = 5;

    private RequestWarningUtils() {
    }

    /**
     * 记录一条用户可见预警。多次调用会合并到同一个响应头，重复文案自动去重。
     */
    public static void warn(String message) {
        if (message == null || message.isBlank()) return;
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return;
            HttpServletRequest request = attrs.getRequest();
            HttpServletResponse response = attrs.getResponse();
            if (request == null || response == null) return;

            @SuppressWarnings("unchecked")
            Set<String> messages = (Set<String>) request.getAttribute(ATTR_NAME);
            if (messages == null) {
                messages = new LinkedHashSet<>();
                request.setAttribute(ATTR_NAME, messages);
            }
            if (!messages.add(message)) return;

            StringBuilder joined = new StringBuilder();
            int shown = 0;
            for (String msg : messages) {
                if (shown >= MAX_MESSAGES) break;
                if (joined.length() > 0) joined.append("；");
                joined.append(msg);
                shown++;
            }
            if (messages.size() > MAX_MESSAGES) {
                joined.append("；另有").append(messages.size() - MAX_MESSAGES).append("项预警未展示");
            }
            String encoded = URLEncoder.encode(joined.toString(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            response.setHeader(HEADER_NAME, encoded);
        } catch (Exception e) {
            // 预警链路不允许影响业务结果
            log.debug("记录响应预警失败: {}", e.getMessage());
        }
    }
}
