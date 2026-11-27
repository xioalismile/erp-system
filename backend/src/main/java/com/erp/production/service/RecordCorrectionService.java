package com.erp.production.service;

import com.erp.production.entity.RecordCorrection;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 记录更正服务（化妆品批记录）。
 *
 * <p>只增不改体系下唯一的纠错通道：原记录**保持不变**，更正以新增记录的方式留痕，
 * 最新一条更正记录即「当前有效值」。更正必须填原因 + 电子签名，两者缺一不可。</p>
 */
public interface RecordCorrectionService {

    /**
     * 新增更正。
     *
     * @param targetTable 被更正记录所在表（qc_record / production_order / work_operation_log 等）
     * @param targetId 被更正记录ID
     * @param targetField 被更正字段
     * @param newValue 更正后的值
     * @param oldValue 改前值（由调用方传入真实旧值；传空则自动从目标表取）
     * @param reason 更正原因（必填）
     * @param password 登录密码（历史密码签名兜底）
     * @param signImage 手写签名图（PNG DataURL；有则直接通过，不校验密码）
     */
    Long correct(String targetTable, Long targetId, String targetField, String newValue,
                 String oldValue, String reason, HttpServletRequest request, String password,
                 String signImage);

    /** 某条记录的更正链（按时间升序） */
    java.util.List<RecordCorrection> listByTarget(String targetTable, Long targetId);
}
