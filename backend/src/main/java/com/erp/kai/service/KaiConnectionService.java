package com.erp.kai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.kai.entity.KaiExtConnection;
import com.erp.kai.mapper.KaiExtConnectionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 小楷独立版 · 外部系统连接管理（FEAT-KAI-STANDALONE-001）。
 * 口径：
 * ① 按 kaiUserId 严格隔离（只能看到/操作自己的连接）；
 * ② 凭据 AES-GCM 加密落库、永不回显明文 —— 列表只给脱敏尾号 + 是否已配置标记，编辑留空=保持原值；
 * ③ 附加配置（端点映射 / 字段映射）以 JSON 存 extra_config。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiConnectionService {

    private final KaiExtConnectionMapper connectionMapper;
    private final KaiCryptoService cryptoService;

    /** 连接类型 → 展示名（前端下拉与 AI 工具说明共用口径） */
    public static final Map<String, String> TYPE_LABELS = Map.of(
            "DEMO", "演示系统",
            "GENERIC_REST", "通用 REST 接口",
            "KAIYUN", "楷煜 ERP（满血）",
            "KINGDEE", "金蝶系",
            "YONYOU", "用友系");

    public static final Map<String, String> AUTH_LABELS = Map.of(
            "API_KEY", "密钥（请求头）",
            "BEARER_TOKEN", "访问令牌",
            "BASIC", "账号密码",
            "LOGIN_TOKEN", "登录换令牌",
            "API_KEY_PAIR", "密钥对拼地址（authId/authKey）");

    /** 连接列表（脱敏）：凭据只回 {@code credentialMasked} 与 {@code hasCredential}。 */
    public List<Map<String, Object>> list(Long kaiUserId) {
        List<KaiExtConnection> rows = connectionMapper.selectList(
                new LambdaQueryWrapper<KaiExtConnection>()
                        .eq(KaiExtConnection::getKaiUserId, kaiUserId)
                        .orderByDesc(KaiExtConnection::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (KaiExtConnection c : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("type", c.getType());
            m.put("typeLabel", TYPE_LABELS.getOrDefault(c.getType(), c.getType()));
            m.put("baseUrl", c.getBaseUrl());
            m.put("authType", c.getAuthType());
            m.put("authTypeLabel", AUTH_LABELS.getOrDefault(c.getAuthType(), c.getAuthType()));
            m.put("enabled", c.getEnabled() != null && c.getEnabled() == 1);
            m.put("timeoutSeconds", c.getTimeoutSeconds());
            m.put("hasCredential", c.getCredentialEnc() != null && !c.getCredentialEnc().isBlank());
            m.put("credentialMasked", maskCredential(c));
            m.put("extraConfig", parseExtra(c.getExtraConfig()));
            m.put("createdAt", c.getCreatedAt());
            out.add(m);
        }
        return out;
    }

    private String maskCredential(KaiExtConnection c) {
        if (c.getCredentialEnc() == null || c.getCredentialEnc().isBlank()) return "";
        try {
            String plain = cryptoService.decrypt(c.getCredentialEnc());
            return cryptoService.maskTail(plain);
        } catch (Exception e) {
            return "****";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseExtra(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /** 新建/更新连接。id=null 新建；凭据留空=保持原值（仅更新时）。
     * 凭据口径（FEAT-KAI-STANDALONE-008 分框修复）：前端按鉴权方式分框传入
     * credentialUsername/credentialPassword（账号密码类）或 credential（密钥/令牌），
     * 后端统一组 JSON 加密落库；兼容一期旧格式（解密后非 JSON 视为单值凭据）。 */
    public Long save(Long kaiUserId, Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(body.get("id").toString());
        String name = str(body.get("name"));
        String type = str(body.get("type"));
        String baseUrl = str(body.get("baseUrl"));
        String authType = str(body.get("authType"));
        String credential = str(body.get("credential"));
        String credUser = str(body.get("credentialUsername"));
        String credPass = str(body.get("credentialPassword"));
        String extraConfig = body.get("extraConfig") == null ? null : String.valueOf(body.get("extraConfig"));

        if (type.isBlank()) throw new IllegalArgumentException("请选择系统类型");
        if (!"DEMO".equals(type) && baseUrl.isBlank()) {
            throw new IllegalArgumentException("请填写服务地址");
        }
        if (authType.isBlank()) authType = "API_KEY";
        boolean accountBased = "BASIC".equals(authType) || "LOGIN_TOKEN".equals(authType);
        boolean pairBased = "API_KEY_PAIR".equals(authType);
        String credentialJson;
        if (accountBased) {
            if (!credUser.isBlank() || !credPass.isBlank()) {
                Map<String, String> cred = new LinkedHashMap<>();
                cred.put("username", credUser.trim());
                cred.put("password", credPass);
                credentialJson = toJson(cred);
            } else {
                credentialJson = credential; // 兼容旧前端整体传参（账号:密码 或 JSON）
            }
        } else if (pairBased) {
            if (!credUser.isBlank() || !credPass.isBlank()) {
                Map<String, String> cred = new LinkedHashMap<>();
                cred.put("authId", credUser.trim());
                cred.put("authKey", credPass);
                credentialJson = toJson(cred);
            } else {
                credentialJson = credential;
            }
        } else {
            credentialJson = credential;
        }

        KaiExtConnection c;
        if (id == null) {
            c = new KaiExtConnection();
            c.setKaiUserId(kaiUserId);
            c.setCreatedAt(LocalDateTime.now());
        } else {
            c = connectionMapper.selectById(id);
            if (c == null || !c.getKaiUserId().equals(kaiUserId)) {
                throw new IllegalArgumentException("连接不存在或无权操作");
            }
        }
        // 连接名称不再手输（2026-10-19）：新建按系统类型自动命名，编辑留空保持原名；重名允许
        String effectiveName;
        if (!name.isBlank()) {
            effectiveName = name.trim();
        } else if (c.getName() != null && !c.getName().isBlank()) {
            effectiveName = c.getName();
        } else {
            effectiveName = "KAIYUN".equals(type) ? "楷煜ERP" : ("DEMO".equals(type) ? "演示系统" : "自定义系统");
        }
        c.setName(effectiveName);
        c.setType(type);
        c.setBaseUrl(baseUrl == null ? "" : baseUrl.trim());
        c.setAuthType(authType);
        // 凭据：非空才覆盖；留空=保持原值（编辑场景）
        if (credentialJson != null && !credentialJson.isBlank()) {
            c.setCredentialEnc(cryptoService.encrypt(credentialJson.trim()));
        }
        if (id == null && (c.getCredentialEnc() == null || c.getCredentialEnc().isBlank())
                && !"DEMO".equals(type)) {
            throw new IllegalArgumentException("请填写访问凭据（密钥/令牌/账号密码）");
        }
        if (extraConfig != null && !extraConfig.isBlank()) {
            // 校验是合法 JSON 再落库
            try {
                new com.fasterxml.jackson.databind.ObjectMapper().readValue(extraConfig, Map.class);
                c.setExtraConfig(extraConfig);
            } catch (Exception e) {
                throw new IllegalArgumentException("附加配置必须是合法的 JSON 文本");
            }
        } else if (body.containsKey("extraConfig")) {
            c.setExtraConfig(null);
        }
        if (body.get("enabled") != null) {
            c.setEnabled(Boolean.parseBoolean(body.get("enabled").toString()) ? 1 : 0);
        } else if (id == null) {
            c.setEnabled(1);
        }
        c.setTimeoutSeconds(body.get("timeoutSeconds") == null ? 30
                : Math.max(5, Math.min(120, Integer.parseInt(body.get("timeoutSeconds").toString()))));
        c.setUpdatedAt(LocalDateTime.now());
        if (id == null) {
            connectionMapper.insert(c);
        } else {
            connectionMapper.updateById(c);
        }
        return c.getId();
    }

    /** 删除（仅本人连接）。 */
    public void delete(Long kaiUserId, Long id) {
        KaiExtConnection c = connectionMapper.selectById(id);
        if (c == null || !c.getKaiUserId().equals(kaiUserId)) {
            throw new IllegalArgumentException("连接不存在或无权操作");
        }
        connectionMapper.deleteById(id);
    }

    /** 聊天路径：修改已有连接的配置（字段映射/接口地址/名称），合并式更新（FEAT-KAI-STANDALONE-011）。
     * 仅更新传入的键；fieldMap/endpoints 按对象键合并进现有附加配置；返回更新后的附加配置。 */
    public Map<String, Object> updateConfig(Long kaiUserId, Long id, String name,
                                            Map<String, Object> fieldMap, Map<String, Object> endpoints) {
        KaiExtConnection c = connectionMapper.selectById(id);
        if (c == null || !c.getKaiUserId().equals(kaiUserId)) {
            throw new IllegalArgumentException("连接不存在或无权操作");
        }
        if ((fieldMap == null || fieldMap.isEmpty()) && (endpoints == null || endpoints.isEmpty())
                && (name == null || name.isBlank())) {
            throw new IllegalArgumentException("没有要修改的内容：请说明要改字段映射、接口地址还是连接名称");
        }
        if (name != null && !name.isBlank()) {
            c.setName(name.trim());
        }
        Map<String, Object> cfg = parseExtra(c.getExtraConfig());
        if (fieldMap != null && !fieldMap.isEmpty()) {
            Map<String, Object> fm = cfg.get("fieldMap") instanceof Map ? new LinkedHashMap<>((Map<String, Object>) cfg.get("fieldMap")) : new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : fieldMap.entrySet()) {
                fm.put(String.valueOf(e.getKey()), e.getValue() instanceof Map ? e.getValue() : e.getValue());
            }
            cfg.put("fieldMap", fm);
        }
        if (endpoints != null && !endpoints.isEmpty()) {
            Map<String, Object> ep = cfg.get("endpoints") instanceof Map ? new LinkedHashMap<>((Map<String, Object>) cfg.get("endpoints")) : new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : endpoints.entrySet()) {
                ep.put(String.valueOf(e.getKey()), e.getValue());
            }
            cfg.put("endpoints", ep);
        }
        try {
            c.setExtraConfig(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(cfg));
        } catch (Exception e) {
            throw new IllegalArgumentException("附加配置序列化失败：" + e.getMessage());
        }
        c.setUpdatedAt(LocalDateTime.now());
        connectionMapper.updateById(c);
        return cfg;
    }

    /** 启用/停用。 */
    public void toggle(Long kaiUserId, Long id, boolean enabled) {
        KaiExtConnection c = connectionMapper.selectById(id);
        if (c == null || !c.getKaiUserId().equals(kaiUserId)) {
            throw new IllegalArgumentException("连接不存在或无权操作");
        }
        c.setEnabled(enabled ? 1 : 0);
        c.setUpdatedAt(LocalDateTime.now());
        connectionMapper.updateById(c);
    }

    /** 取本人一个可用连接（对话绑定用）：优先指定 id，否则取第一个启用的。 */
    public KaiExtConnection getUsable(Long kaiUserId, Long connectionId) {
        if (connectionId != null) {
            KaiExtConnection c = connectionMapper.selectById(connectionId);
            if (c == null || !c.getKaiUserId().equals(kaiUserId)) {
                throw new IllegalArgumentException("连接不存在或无权使用");
            }
            return c;
        }
        List<KaiExtConnection> rows = connectionMapper.selectList(
                new LambdaQueryWrapper<KaiExtConnection>()
                        .eq(KaiExtConnection::getKaiUserId, kaiUserId)
                        .eq(KaiExtConnection::getEnabled, 1)
                        .orderByDesc(KaiExtConnection::getId)
                        .last("LIMIT 1"));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("还没有接入任何系统，请先到「系统连接」页新建连接");
        }
        return rows.get(0);
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String toJson(Map<String, String> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "";
        }
    }
}
