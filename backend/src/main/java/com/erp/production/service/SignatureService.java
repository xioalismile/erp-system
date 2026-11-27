package com.erp.production.service;

import com.erp.production.entity.SignatureRecord;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 电子签名服务（化妆品批记录）
 * 检验提交 / 检验复核 / 记录更正 关键动作的手写签名。
 * 口径：以手写签名图为准（有签名图即通过并落库）；历史密码签名保留为兜底，密码不通过不落库。
 * signature_record 只增不改。
 */
public interface SignatureService {

    /**
     * 手写签名（推荐）：传签名图即通过，不再校验登录密码。
     *
     * @param signImage 手写签名图（PNG DataURL）
     */
    Long sign(String actionType, String targetTable, Long targetId, String targetDesc,
              HttpServletRequest request, String password, String signImage);

    /**
     * 历史密码签名（兼容旧调用）：未传签名图时按登录密码校验。
     */
    default Long sign(String actionType, String targetTable, Long targetId, String targetDesc,
                      HttpServletRequest request, String password) {
        return sign(actionType, targetTable, targetId, targetDesc, request, password, null);
    }

    /** 按目标查询签名记录（批生产记录聚合用，按签名时间升序） */
    List<SignatureRecord> listByTarget(String targetTable, Long targetId);
}
