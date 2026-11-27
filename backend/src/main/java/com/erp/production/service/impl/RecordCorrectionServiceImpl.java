package com.erp.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.config.BusinessException;
import com.erp.common.util.SecurityUtils;
import com.erp.production.entity.RecordCorrection;
import com.erp.production.mapper.RecordCorrectionMapper;
import com.erp.production.service.RecordCorrectionService;
import com.erp.production.service.SignatureService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记录更正服务实现（化妆品批记录）。
 *
 * <p>执行顺序：原因非空校验 → 电子签名（密码校验失败即中止，不落任何数据）→ 写更正记录。
 * 原记录**不做任何修改**，保证审计链完整。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordCorrectionServiceImpl implements RecordCorrectionService {

    private final RecordCorrectionMapper correctionMapper;
    private final SignatureService signatureService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long correct(String targetTable, Long targetId, String targetField, String newValue,
                        String oldValue, String reason, HttpServletRequest request, String password,
                        String signImage) {
        if (targetTable == null || targetTable.isBlank() || targetId == null) {
            throw new BusinessException("请指定要更正的记录");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("更正必须填写原因");
        }
        // 先签名：密码不通过则整笔不落库
        signatureService.sign("记录更正", targetTable, targetId,
                "更正记录（" + targetTable + "#" + targetId + " 字段 " + targetField
                        + " → " + newValue + "，原因：" + reason + "）", request, password, signImage);

        RecordCorrection c = new RecordCorrection();
        c.setTargetTable(targetTable);
        c.setTargetId(targetId);
        c.setTargetField(targetField);
        c.setOldValue(oldValue);
        c.setNewValue(newValue);
        c.setReason(reason);
        c.setCorrectedBy(displayName(request));
        c.setCorrectedAt(LocalDateTime.now());
        c.setSigned(1);
        correctionMapper.insert(c);
        log.info("[批记录] 记录更正 {}#{} 字段{} 原因{} 操作人{}",
                targetTable, targetId, targetField, reason, c.getCorrectedBy());
        return c.getCorrectionId();
    }

    @Override
    public List<RecordCorrection> listByTarget(String targetTable, Long targetId) {
        return correctionMapper.selectList(new LambdaQueryWrapper<RecordCorrection>()
                .eq(RecordCorrection::getTargetTable, targetTable)
                .eq(RecordCorrection::getTargetId, targetId)
                .orderByAsc(RecordCorrection::getCorrectedAt));
    }

    private static String displayName(HttpServletRequest request) {
        String realName = SecurityUtils.getCurrentRealName(request);
        return realName == null || realName.isBlank()
                ? SecurityUtils.getCurrentUsername(request) : realName;
    }
}
