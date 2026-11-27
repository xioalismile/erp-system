package com.erp.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.config.entity.SysConfig;
import com.erp.config.service.SysConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小楷助手「服务协议与免责声明」同意记录服务。
 *
 * <p>存储口径（零建表）：复用 {@code sys_config} 的用户级键
 * {@code ai.disclaimer.accepted.<userId>}，与既有 {@code ai.quota.user.<id>} 同一套先例
 * （老库零 DDL 迁移风险）。值为 JSON：{@code {"version":"1.0","acceptedAt":"...","username":"..."}}。
 *
 * <p>判定口径：无记录 或 记录中的 version ≠ 当前版本 → 视为未同意（前端据此弹协议）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDisclaimerService {

    private final SysConfigService sysConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String KEY_PREFIX = "ai.disclaimer.accepted.";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String keyOf(Long userId) {
        return KEY_PREFIX + userId;
    }

    /** 原始同意记录（无记录返回 null） */
    public String getRawAccepted(Long userId) {
        if (userId == null) return null;
        return sysConfigService.getValue(keyOf(userId));
    }

    /** 是否已同意指定版本（无记录 / 版本不一致 / 解析失败 → false） */
    public boolean isAccepted(Long userId, String version) {
        String raw = getRawAccepted(userId);
        if (raw == null || raw.isBlank()) return false;
        if (version == null || version.isBlank()) return true;
        try {
            Map<?, ?> m = objectMapper.readValue(raw, Map.class);
            Object v = m.get("version");
            return v != null && version.equals(String.valueOf(v));
        } catch (Exception e) {
            log.warn("[AI协议] 同意记录解析失败，按未同意处理: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 同意状态查询。
     *
     * @param version 前端常量里的当前协议版本号（协议文本写死在前端，版本号由其上报）
     * @return {currentVersion, accepted, acceptedVersion, acceptedAt}
     */
    public Map<String, Object> status(Long userId, String version) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentVersion", version);
        result.put("accepted", isAccepted(userId, version));
        String raw = getRawAccepted(userId);
        if (raw != null && !raw.isBlank()) {
            try {
                Map<?, ?> m = objectMapper.readValue(raw, Map.class);
                result.put("acceptedVersion", m.get("version"));
                result.put("acceptedAt", m.get("acceptedAt"));
            } catch (Exception e) {
                log.warn("[AI协议] 同意记录解析失败: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 记录同意（幂等：重复同意只覆盖版本与时间）。
     *
     * @return 同意时间（yyyy-MM-dd HH:mm:ss）
     */
    public String accept(Long userId, String username, String version) {
        String acceptedAt = LocalDateTime.now().format(FMT);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("version", version);
        value.put("acceptedAt", acceptedAt);
        value.put("username", username == null ? "" : username);
        String json;
        try {
            json = objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            // 兜底：极端情况下手工拼装（字段均为受控字符串，无注入风险）
            json = "{\"version\":\"" + version + "\",\"acceptedAt\":\"" + acceptedAt
                    + "\",\"username\":\"" + (username == null ? "" : username) + "\"}";
        }
        String key = keyOf(userId);
        SysConfig config = sysConfigService.getOne(new QueryWrapper<SysConfig>().eq("config_key", key));
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigName("小楷助手协议同意记录-" + userId);
            config.setConfigGroup("ai");
            config.setConfigType("string");
            config.setRemark("用户勾选《小楷助手 AI 服务使用协议与免责声明》的记录（版本+时间），系统写入，请勿手工修改");
            config.setSortOrder(0);
            config.setConfigValue(json);
            sysConfigService.save(config);
        } else {
            config.setConfigValue(json);
            sysConfigService.updateById(config);
        }
        sysConfigService.refreshCache();
        log.info("[AI协议] 用户 {} 已同意协议版本 {}（时间 {}）", userId, version, acceptedAt);
        return acceptedAt;
    }
}
