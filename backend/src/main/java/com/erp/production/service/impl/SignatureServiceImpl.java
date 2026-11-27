package com.erp.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.config.BusinessException;
import com.erp.common.util.SecurityUtils;
import com.erp.production.entity.SignatureRecord;
import com.erp.production.mapper.SignatureRecordMapper;
import com.erp.production.service.SignatureService;
import com.erp.system.entity.SysUser;
import com.erp.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 电子签名服务实现（化妆品批记录）
 * 密码校验与登录链路同源：SysUser.password（BCrypt）+ BCryptPasswordEncoder.matches。
 * 签名记录只增不改：本类与 Mapper 均不提供 update/delete 调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignatureServiceImpl implements SignatureService {

    private final SignatureRecordMapper signatureRecordMapper;
    private final SysUserMapper sysUserMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Long sign(String actionType, String targetTable, Long targetId, String targetDesc,
                     HttpServletRequest request, String password, String signImage) {
        Long userId = SecurityUtils.getCurrentUserId(request);
        if (userId == null) {
            throw new BusinessException("当前登录状态无效，请重新登录后再签名");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("当前登录用户不存在，签名未通过");
        }
        // 手写签名优先：有签名图即通过（用户口径：只手写签名，不输密码）
        boolean handwritten = signImage != null && !signImage.isBlank();
        if (!handwritten) {
            // 兜底：历史密码签名（旧调用未传签名图时仍按密码校验）
            if (password == null || password.isBlank()) {
                throw new BusinessException("请先手写签名再提交");
            }
            if (!passwordEncoder.matches(password, user.getPassword() == null ? "" : user.getPassword())) {
                // 密码错误：不落任何记录，直接拒绝
                throw new BusinessException("密码不正确，签名未通过");
            }
        }
        SignatureRecord record = new SignatureRecord();
        record.setActionType(actionType);
        record.setTargetTable(targetTable);
        record.setTargetId(targetId);
        record.setTargetDesc(targetDesc);
        record.setSignerName(user.getRealName() == null || user.getRealName().isBlank()
                ? user.getUsername() : user.getRealName());
        record.setSignTime(LocalDateTime.now());
        record.setVerifyResult("通过");
        record.setSignMethod(handwritten ? "手写" : "密码");
        record.setSignImage(handwritten ? signImage : null);
        signatureRecordMapper.insert(record);
        log.info("[电子签名] {} | 目标 {}.{} | 签名人 {} | 方式 {}",
                actionType, targetTable, targetId, record.getSignerName(), record.getSignMethod());
        return record.getSignatureId();
    }

    @Override
    public List<SignatureRecord> listByTarget(String targetTable, Long targetId) {
        return signatureRecordMapper.selectList(new LambdaQueryWrapper<SignatureRecord>()
                .eq(SignatureRecord::getTargetTable, targetTable)
                .eq(SignatureRecord::getTargetId, targetId)
                .orderByAsc(SignatureRecord::getSignTime));
    }
}
