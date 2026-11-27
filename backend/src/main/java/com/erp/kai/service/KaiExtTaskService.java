package com.erp.kai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 小楷独立版 · 外部操作任务留痕（FEAT-KAI-STANDALONE-001）。
 * 所有写操作（建档/分类/开单/提交）执行后落一条记录，审计可追溯。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiExtTaskService {

    private final JdbcTemplate jdbcTemplate;

    public void log(Long kaiUserId, Long connectionId, String action, String objectType,
                    String summary, String detail, String status) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO kai_ext_task_log (kai_user_id, connection_id, action, object_type, summary, detail, status) "
                            + "VALUES (?,?,?,?,?,?,?)",
                    kaiUserId, connectionId, action, objectType, truncate(summary, 500), detail, status);
        } catch (Exception e) {
            // 留痕失败不阻断业务，但要能被运维发现
            log.warn("[小楷独立版] 任务留痕失败: {}", e.getMessage());
        }
    }

    private String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() > len ? s.substring(0, len) : s;
    }
}
