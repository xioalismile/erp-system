package com.erp.common;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量删除「预检」结果：给前端弹窗展示"哪些可删、哪些不可删（含原因）"，由用户自己决定是否继续。
 *
 * <p><b>语义（重要）</b>：本结果<b>仅供用户决策展示，不构成删除授权</b> ——
 * 预检与执行之间数据可能变化（别人刚好引用了该记录），因此执行阶段<b>必须重新校验</b>，
 * 不允许把"预检说可删"当成"免检通行证"。</p>
 *
 * @since 2026-09-16
 */
@Data
public class BatchDeleteCheckDTO {

    /** 可删除的记录 ID */
    private List<Long> deletable = new ArrayList<>();

    /** 不可删除的记录（含原因） */
    private List<Blocked> blocked = new ArrayList<>();

    /** 不可删除项 */
    @Data
    public static class Blocked {
        /** 记录 ID */
        private Long id;
        /** 不可删除原因（直接展示给用户，需为业务语言） */
        private String reason;

        public Blocked() {
        }

        public Blocked(Long id, String reason) {
            this.id = id;
            this.reason = reason;
        }
    }
}
