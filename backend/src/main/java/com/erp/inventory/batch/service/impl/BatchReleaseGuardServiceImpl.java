package com.erp.inventory.batch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.config.BusinessException;
import com.erp.config.service.SysConfigService;
import com.erp.inventory.batch.entity.BatchInfo;
import com.erp.inventory.batch.mapper.BatchInfoMapper;
import com.erp.inventory.batch.service.BatchReleaseGuardService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 批次放行守卫实现（化妆品批记录）。
 *
 * <p>参数缺失或读取异常时按「预警提示」降级（不阻断业务），保证不会因配置问题把发货卡死。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchReleaseGuardServiceImpl implements BatchReleaseGuardService {

    private static final String CONFIG_KEY = "batch.release.outbound.control";
    private static final String MODE_ALLOW = "允许";
    private static final String MODE_WARN = "预警提示";
    private static final String MODE_BLOCK = "不允许";
    private static final List<String> NOT_RELEASABLE = List.of("待检", "不合格", "已冻结");

    private final SysConfigService sysConfigService;
    private final BatchInfoMapper batchInfoMapper;
    private final MaterialMapper materialMapper;

    @Override
    public String currentMode() {
        try {
            String v = sysConfigService.getValue(CONFIG_KEY, MODE_WARN);
            if (v == null || v.isBlank()) return MODE_WARN;
            String t = v.trim();
            if (MODE_ALLOW.equals(t) || MODE_WARN.equals(t) || MODE_BLOCK.equals(t)) return t;
            return MODE_WARN;
        } catch (Exception e) {
            log.warn("[批记录] 读取未放行出库控制参数失败，按预警提示降级: {}", e.getMessage());
            return MODE_WARN;
        }
    }

    @Override
    public void enforceOutbound(Long materialId, String batchNo, String lineLabel) {
        String warn = warningOf(materialId, batchNo, lineLabel);
        if (warn == null) return;
        String mode = currentMode();
        if (MODE_BLOCK.equals(mode)) {
            throw new BusinessException(warn + "。当前设置为「不允许」，未放行批次不能出库，请先完成检验放行或联系质量负责人");
        }
        if (MODE_WARN.equals(mode)) {
            log.warn("[批记录] 出库预警（放行提示可继续）: {}", warn);
        }
        // 允许档：不记录不阻断，保持与旧行为一致
    }

    @Override
    public String warningOf(Long materialId, String batchNo, String lineLabel) {
        if (materialId == null) return null;
        Material material = materialMapper.selectById(materialId);
        if (material == null) return null;
        // 仅批次管理物料参与放行控制
        if (material.getIsLotControl() == null || material.getIsLotControl() != 1) return null;
        if (batchNo == null || batchNo.isBlank()) return null;

        BatchInfo batch = batchInfoMapper.selectOne(new LambdaQueryWrapper<BatchInfo>()
                .eq(BatchInfo::getBatchNo, batchNo)
                .eq(BatchInfo::getMaterialId, materialId)
                .last("LIMIT 1"));
        if (batch == null) {
            // 批次档案缺失（历史数据）：不拦，避免老数据无法发货
            return null;
        }
        String status = batch.getQcStatus();
        if (status == null || status.isBlank()) {
            // 存量未回填：按历史批次处理，不拦
            return null;
        }
        if ("已放行".equals(status)) return null;
        if (!NOT_RELEASABLE.contains(status)) {
            // 「合格」等中间态（历史批次无放行单）不拦
            return null;
        }
        String prefix = lineLabel == null || lineLabel.isBlank() ? "" : lineLabel + " ";
        return prefix + "批次 " + batchNo + "（" + (material.getMaterialName() == null
                ? material.getMaterialCode() : material.getMaterialName())
                + "）当前质量状态为「" + status + "」，未完成检验放行";
    }
}
