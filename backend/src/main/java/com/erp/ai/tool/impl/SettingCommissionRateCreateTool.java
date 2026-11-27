package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.system.entity.CommissionRate;
import com.erp.system.service.CommissionRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：新增一版提成率配置（设置中心 → 提成率设置），走强制确认。
 * 会关闭当前生效版本并把新版本置为当前，版本号自动递增。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingCommissionRateCreateTool implements AiTool {

    private final CommissionRateService commissionRateService;

    @Override
    public String getName() {
        return "setting_commission_rate_create";
    }

    @Override
    public String getDescription() {
        return "新增一版提成率配置（执行前会弹确认卡片）。新版本会关闭当前生效版本并成为当前生效版本，版本号自动递增。"
                + "必填参数：baseRate(基础提成率，小数，如 0.05 表示 5%)。"
                + "可选：basis(计算基础 order订单/contract合同/collection回款，默认order)、"
                + "overAchievementMultiplier(超额倍数阈值，如1.20，默认1.20)、"
                + "overAchievementBonus(超额奖励率，如0.03，默认0.0300)、"
                + "effectiveDate(生效日期 yyyy-MM-dd，默认今天，不能早于或等于当前版本生效日期)、remark。"
                + "改前建议先用 setting_commission_rate_current 查看当前版本。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("baseRate", Map.of("type", "number", "description", "基础提成率（小数，如 0.05），必填"));
        props.put("basis", Map.of("type", "string", "description", "计算基础：order/contract/collection，默认order"));
        props.put("overAchievementMultiplier", Map.of("type", "number", "description", "超额倍数阈值，如1.20"));
        props.put("overAchievementBonus", Map.of("type", "number", "description", "超额奖励率，如0.03"));
        props.put("effectiveDate", Map.of("type", "string", "description", "生效日期 yyyy-MM-dd，默认今天"));
        props.put("remark", Map.of("type", "string", "description", "备注"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("baseRate"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            Map<String, Object> p = params == null ? Map.of() : params;
            java.math.BigDecimal baseRate = SettingSupport.decimal(p.get("baseRate"));
            if (baseRate == null) {
                return Map.of("success", false, "message", "参数缺失：需要 baseRate（基础提成率，如 0.05）");
            }

            CommissionRate rate = new CommissionRate();
            rate.setBaseRate(baseRate);
            if (p.containsKey("basis")) rate.setBasis(SettingSupport.str(p.get("basis")));
            if (p.containsKey("overAchievementMultiplier")) {
                rate.setOverAchievementMultiplier(SettingSupport.decimal(p.get("overAchievementMultiplier")));
            }
            if (p.containsKey("overAchievementBonus")) {
                rate.setOverAchievementBonus(SettingSupport.decimal(p.get("overAchievementBonus")));
            }
            String effectiveDate = SettingSupport.str(p.get("effectiveDate"));
            rate.setEffectiveDate(effectiveDate == null ? LocalDate.now() : LocalDate.parse(effectiveDate));
            if (p.containsKey("remark")) rate.setRemark(SettingSupport.str(p.get("remark")));

            String operator = scope != null ? scope.getUsername() : null;
            CommissionRate saved = commissionRateService.createVersion(rate, operator);
            log.info("[AI设置] 用户{}新增提成率版本 v{} baseRate={} effectiveDate={}",
                    operator, saved.getVersionNo(), saved.getBaseRate(), saved.getEffectiveDate());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("versionNo", saved.getVersionNo());
            result.put("effectiveDate", saved.getEffectiveDate());
            result.put("message", "已新增提成率版本 v" + saved.getVersionNo() + "，基础提成率 "
                    + saved.getBaseRate() + "，自 " + saved.getEffectiveDate() + " 起生效。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 新增提成率版本失败", e);
            return Map.of("success", false, "message", "新增提成率版本失败: " + e.getMessage());
        }
    }
}
