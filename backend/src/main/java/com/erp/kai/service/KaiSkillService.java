package com.erp.kai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小楷独立版 · 技能库（FEAT-KAI-STANDALONE-011）。
 * 技能 = 一段可维护的行为设定（名称 + 说明 + 提示词正文）。
 * 启用中的技能按排序整段拼进对话系统提示词——用户改小楷的行为不再需要改代码发版。
 * 设计蓝本：XXL-AI SKILL 模块（按本工程形态裁剪为提示词级技能，文件树/沙箱形态后续按需扩展）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiSkillService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String SEL = "SELECT id, name, description, content, enabled, sort_order FROM kai_skill WHERE kai_user_id = ?";

    public List<Map<String, Object>> list(Long kaiUserId) {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbcTemplate.query(SEL + " ORDER BY sort_order ASC, id DESC", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("name", rs.getString("name"));
            m.put("description", rs.getString("description"));
            m.put("content", rs.getString("content"));
            m.put("enabled", rs.getInt("enabled") == 1);
            m.put("sortOrder", rs.getInt("sort_order"));
            out.add(m);
        }, kaiUserId);
        return out;
    }

    public Long save(Long kaiUserId, Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(body.get("id").toString());
        String name = str(body.get("name")).trim();
        String content = str(body.get("content"));
        if (name.isBlank()) throw new IllegalArgumentException("请填写技能名称");
        if (content.isBlank()) throw new IllegalArgumentException("请填写技能内容");
        if (content.length() > 20000) throw new IllegalArgumentException("技能内容最长 20000 字，当前 " + content.length() + " 字");
        int sortOrder = body.get("sortOrder") == null ? 0 : Integer.parseInt(body.get("sortOrder").toString());
        boolean enabled = body.get("enabled") == null || Boolean.parseBoolean(body.get("enabled").toString());
        if (id == null) {
            jdbcTemplate.update(
                    "INSERT INTO kai_skill (kai_user_id, name, description, content, enabled, sort_order) VALUES (?,?,?,?,?,?)",
                    kaiUserId, name, str(body.get("description")), content, enabled ? 1 : 0, sortOrder);
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM kai_skill WHERE kai_user_id=? ORDER BY id DESC LIMIT 1", Long.class, kaiUserId);
        }
        Integer owner = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kai_skill WHERE id=? AND kai_user_id=?", Integer.class, id, kaiUserId);
        if (owner == null || owner == 0) throw new IllegalArgumentException("技能不存在或无权操作");
        jdbcTemplate.update(
                "UPDATE kai_skill SET name=?, description=?, content=?, enabled=?, sort_order=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                name, str(body.get("description")), content, enabled ? 1 : 0, sortOrder, id);
        return id;
    }

    public void delete(Long kaiUserId, Long id) {
        jdbcTemplate.update("DELETE FROM kai_skill WHERE id=? AND kai_user_id=?", id, kaiUserId);
    }

    public void toggle(Long kaiUserId, Long id, boolean enabled) {
        jdbcTemplate.update("UPDATE kai_skill SET enabled=? WHERE id=? AND kai_user_id=?", enabled ? 1 : 0, id, kaiUserId);
    }

    /**
     * 生成对话系统提示词里的技能块（只含启用中的技能）。
     * 无启用技能返回 null——调用方原样跳过，保证「不配任何新能力时对话行为与升级前完全一致」。
     */
    public String skillPromptBlock(Long kaiUserId) {
        List<String> parts = new ArrayList<>();
        jdbcTemplate.query(SEL + " AND enabled = 1 ORDER BY sort_order ASC, id ASC", rs -> {
            String name = rs.getString("name");
            String content = rs.getString("content");
            StringBuilder sb = new StringBuilder("### 技能：").append(name).append("\n").append(content);
            parts.add(sb.toString());
        }, kaiUserId);
        if (parts.isEmpty()) return null;
        return "【技能设定】以下技能是用户为本助手定制的长期要求，请在所有回复中遵循：\n"
                + String.join("\n\n", parts);
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
