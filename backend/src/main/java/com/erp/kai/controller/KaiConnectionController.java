package com.erp.kai.controller;

import com.erp.common.Result;
import com.erp.kai.entity.KaiExtConnection;
import com.erp.kai.service.KaiConnectionService;
import com.erp.kai.service.adapter.KaiAdapterFactory;
import com.erp.kai.service.adapter.KaiExtAdapter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小楷独立版 · 外部系统连接管理（FEAT-KAI-STANDALONE-001）。
 * 全部按登录用户隔离；凭据永不回显明文。
 */
@Slf4j
@RestController
@RequestMapping("/api/kai-standalone/connections")
@RequiredArgsConstructor
public class KaiConnectionController {

    private final KaiConnectionService connectionService;
    private final KaiAdapterFactory adapterFactory;
    private final com.erp.kai.service.KaiCryptoService kaiCryptoService;
    private final com.erp.kai.service.KaiDocAnalyzerService docAnalyzerService;

    /** AI 读接口文档 → 自动分析出连接配置（endpoints/字段映射/鉴权建议），回填前不落库。 */
    @PostMapping("/analyze-doc")
    public Result<?> analyzeDoc(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            kaiUserId(request); // 需登录
            String docUrl = body.get("docUrl") == null ? "" : String.valueOf(body.get("docUrl")).trim();
            String docText = body.get("docText") == null ? "" : String.valueOf(body.get("docText"));
            if (docText.isBlank() && docUrl.isBlank()) {
                return Result.error(400, "请粘贴接口文档内容，或填写文档地址");
            }
            String text = docText.isBlank() ? docAnalyzerService.fetchDoc(docUrl) : docText;
            return Result.success(docAnalyzerService.analyze(kaiUserId(request), text));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 我的连接列表（凭据脱敏）。 */
    @GetMapping
    public Result<?> list(HttpServletRequest request) {
        return Result.success(connectionService.list(kaiUserId(request)));
    }

    /** 新建/更新连接。 */
    @PostMapping
    public Result<?> save(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            Long id = connectionService.save(kaiUserId(request), body);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id);
            out.put("message", "连接已保存");
            return Result.success(out);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 删除连接。 */
    @DeleteMapping("/{id}")
    public Result<?> delete(HttpServletRequest request, @PathVariable Long id) {
        try {
            connectionService.delete(kaiUserId(request), id);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 启用/停用。 */
    @PostMapping("/{id}/toggle")
    public Result<?> toggle(HttpServletRequest request, @PathVariable Long id,
                            @RequestParam boolean enabled) {
        try {
            connectionService.toggle(kaiUserId(request), id, enabled);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 测试已保存的连接。 */
    @PostMapping("/{id}/test")
    public Result<?> testSaved(HttpServletRequest request, @PathVariable Long id) {
        try {
            KaiExtConnection conn = connectionService.getUsable(kaiUserId(request), id);
            conn.setEnabled(1); // 测试不受停用状态影响
            return Result.success(adapterFactory.of(conn).testConnection(conn));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 测试未保存的连接草稿（新建向导里先测再存）。 */
    @PostMapping("/test")
    public Result<?> testDraft(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            KaiExtConnection draft = new KaiExtConnection();
            draft.setName(str(body.get("name")));
            draft.setType(str(body.get("type")));
            draft.setBaseUrl(str(body.get("baseUrl")));
            String authType = str(body.get("authType")).isBlank() ? "API_KEY" : str(body.get("authType"));
            draft.setAuthType(authType);
            // 账号密码类鉴权分框传参：与保存口径一致，后端组 JSON 后临时加密包装（不落库）
            String credUser = str(body.get("credentialUsername"));
            String credPass = str(body.get("credentialPassword"));
            String credentialText = str(body.get("credential"));
            if (("BASIC".equals(authType) || "LOGIN_TOKEN".equals(authType))
                    && (!credUser.isBlank() || !credPass.isBlank())) {
                Map<String, String> cred = new LinkedHashMap<>();
                cred.put("username", credUser.trim());
                cred.put("password", credPass);
                try {
                    credentialText = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(cred);
                } catch (Exception jsonEx) {
                    throw new IllegalArgumentException("凭据组包失败，请重新填写账号密码");
                }
            } else if ("API_KEY_PAIR".equals(authType) && (!credUser.isBlank() || !credPass.isBlank())) {
                Map<String, String> cred = new LinkedHashMap<>();
                cred.put("authId", credUser.trim());
                cred.put("authKey", credPass);
                try {
                    credentialText = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(cred);
                } catch (Exception jsonEx) {
                    throw new IllegalArgumentException("凭据组包失败，请重新填写 authId / authKey");
                }
            }
            if (!credentialText.isBlank()) {
                // 草稿凭据直接进加密服务临时包装：写入加密串再由适配器解密，不落库
                draft.setCredentialEnc(kaiCryptoForDraft(credentialText));
            }
            draft.setExtraConfig(body.get("extraConfig") == null ? null : String.valueOf(body.get("extraConfig")));
            draft.setTimeoutSeconds(body.get("timeoutSeconds") == null ? 30
                    : Integer.parseInt(body.get("timeoutSeconds").toString()));
            KaiExtAdapter adapter = adapterFactory.of(draft);
            return Result.success(adapter.testConnection(draft));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    private String kaiCryptoForDraft(Object credential) {
        // 草稿测试的凭据是用户刚输入的明文：走同一加密形态（适配层统一 decrypt(credentialEnc)），不落库
        return kaiCryptoService.encrypt(String.valueOf(credential));
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private Long kaiUserId(HttpServletRequest request) {
        Object v = request.getAttribute("kaiUserId");
        if (v == null) {
            throw new IllegalArgumentException("请先登录小楷");
        }
        return (Long) v;
    }
}
