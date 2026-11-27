package com.erp.tenant.controller;

import com.erp.ai.credit.AiCreditService;
import com.erp.ai.service.AiConfigService;
import com.erp.common.Result;
import com.erp.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * 平台管理控制器 - 仅超级管理员可访问
 * 用于管理所有租户（客户），包括创建、更新、禁用、删除等操作
 */
@RestController
@RequestMapping("/api/admin/tenant")
@RequiredArgsConstructor
public class AdminTenantController {

    private final TenantService tenantService;
    private final AiConfigService aiConfigService;
    private final AiCreditService aiCreditService;

    /**
     * 获取租户列表（分页）
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> listTenants(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        
        List<Map<String, Object>> allTenants = tenantService.getAllTenants(status);
        
        // 关键词搜索
        if (keyword != null && !keyword.trim().isEmpty()) {
            allTenants.removeIf(t -> {
                String name = String.valueOf(t.getOrDefault("tenant_name", ""));
                String code = String.valueOf(t.getOrDefault("tenant_code", ""));
                String contact = String.valueOf(t.getOrDefault("contact_person", ""));
                return !name.contains(keyword) && !code.contains(keyword) && !contact.contains(keyword);
            });
        }
        
        int total = allTenants.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        List<Map<String, Object>> records = fromIndex < total ? allTenants.subList(fromIndex, toIndex) : new ArrayList<>();
        
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("records", records);
        result.put("page", page);
        result.put("size", size);
        
        return Result.success(result);
    }

    /**
     * 创建租户
     */
    @PostMapping("/create")
    public Result<Map<String, Object>> createTenant(@RequestBody CreateTenantRequest req) {
        try {
            // 计算到期日期
            LocalDate expireDate = null;
            if (req.getTrialDays() != null && req.getTrialDays() > 0) {
                expireDate = LocalDate.now().plusDays(req.getTrialDays());
            } else if (req.getExpireDate() != null) {
                expireDate = req.getExpireDate();
            }
            
            // 生成默认管理员用户名
            String adminUsername = req.getAdminUsername();
            if (adminUsername == null || adminUsername.trim().isEmpty()) {
                adminUsername = req.getTenantCode().toLowerCase() + "_admin";
            }
            
            // 生成默认密码
            String adminPassword = req.getAdminPassword();
            if (adminPassword == null || adminPassword.trim().isEmpty()) {
                adminPassword = "Admin@123456";
            }
            
            Map<String, Object> result = tenantService.createTenantWithAdmin(
                    req.getTenantCode(),
                    req.getTenantName(),
                    req.getVersion() != null ? req.getVersion() : "STANDARD",
                    adminUsername,
                    adminPassword,
                    req.getContactPerson(),
                    req.getContactPhone(),
                    req.getContactEmail(),
                    expireDate,
                    req.getMaxUsers(),
                    req.getIndustry());
            
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("创建租户失败: " + e.getMessage());
        }
    }

    /**
     * 更新租户信息
     */
    @PutMapping("/{id}")
    public Result<Void> updateTenant(@PathVariable Long id, @RequestBody UpdateTenantRequest req) {
        try {
            if (req.getVersion() != null) {
                tenantService.updateTenantVersion(id, req.getVersion());
            }
            if (req.getTenantName() != null
                    || req.getContactPerson() != null
                    || req.getContactPhone() != null
                    || req.getContactEmail() != null
                    || req.getAddress() != null
                    || req.getMaxUsers() != null
                    || req.getExpireDate() != null) {
                tenantService.updateTenantInfo(id,
                        req.getTenantName(),
                        req.getContactPerson(),
                        req.getContactPhone(),
                        req.getContactEmail(),
                        req.getAddress(),
                        req.getMaxUsers(),
                        req.getExpireDate());
            }
            return Result.success();
        } catch (Exception e) {
            return Result.error("更新租户失败: " + e.getMessage());
        }
    }

    /**
     * 续费
     */
    @PostMapping("/{id}/renew")
    public Result<Void> renewTenant(@PathVariable Long id, @RequestBody RenewRequest req) {
        try {
            Integer months = req.getMonths();
            if (months == null || months <= 0) {
                months = 12; // 默认续期1年
            }

            // 到期日顺延（当前到期日晚于今天则从到期日顺延，否则从今天起算）
            tenantService.renewTenant(id, months);
            
            return Result.success();
        } catch (Exception e) {
            return Result.error("续费失败: " + e.getMessage());
        }
    }

    /**
     * 禁用租户
     */
    @DeleteMapping("/{id}")
    public Result<Void> disableTenant(@PathVariable Long id) {
        try {
            tenantService.disableTenant(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error("禁用租户失败: " + e.getMessage());
        }
    }

    /**
     * 启用租户
     */
    @PostMapping("/{id}/enable")
    public Result<Void> enableTenant(@PathVariable Long id) {
        try {
            tenantService.enableTenant(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error("启用租户失败: " + e.getMessage());
        }
    }

    /**
     * 删除租户（物理删除：独立库整库 DROP + 平台库登记/关联/孤儿用户清理）。
     * 与"禁用"不同，删除不可恢复。DEFAULT 租户及共享库有后端保护性检查。
     */
    @PostMapping("/{id}/delete")
    public Result<Void> deleteTenant(@PathVariable Long id) {
        try {
            tenantService.deleteTenant(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error("删除租户失败: " + e.getMessage());
        }
    }

    /**
     * 重置租户管理员密码（兜底：无手机号验证时的管理员密码重置）
     */
    @PostMapping("/{id}/reset-password")
    public Result<Map<String, Object>> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest req) {
        try {
            String newPassword = req.getNewPassword();
            if (newPassword == null || newPassword.trim().isEmpty()) {
                return Result.error("新密码不能为空");
            }
            if (newPassword.length() < 6) {
                return Result.error("密码长度不能少于6位");
            }
            tenantService.resetTenantAdminPassword(id, newPassword);
            Map<String, Object> data = new HashMap<>();
            data.put("tenantId", id);
            data.put("message", "密码已重置");
            return Result.success(data);
        } catch (Exception e) {
            return Result.error("重置密码失败: " + e.getMessage());
        }
    }

    /**
     * 读取平台分配给某客户的 AI 月度总额度 + 该客户本月已消耗（AI额度弹窗数据源）。
     * quota=0 表示不限；monthUsed=-1 表示该客户库暂不可统计。
     */
    @GetMapping("/{id}/ai-quota")
    public Result<Map<String, Object>> getAiQuota(@PathVariable Long id) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("quota", aiConfigService.getPlatformTenantQuota(id));
            data.put("monthUsed", tenantService.getTenantAiMonthUsed(id));
            return Result.success(data);
        } catch (Exception e) {
            return Result.error("读取AI额度失败: " + e.getMessage());
        }
    }

    /**
     * 平台分配/调整某客户的 AI 月度总额度（quota<=0 = 不限并清除配置）。
     */
    @PostMapping("/{id}/ai-quota")
    public Result<Void> saveAiQuota(@PathVariable Long id, @RequestBody AiQuotaRequest req) {
        try {
            if (req.getQuota() == null) {
                return Result.error("额度不能为空（0表示不限）");
            }
            if (req.getQuota() < 0) {
                return Result.error("额度不能为负数");
            }
            aiConfigService.savePlatformTenantQuota(id, req.getQuota());
            return Result.success();
        } catch (Exception e) {
            return Result.error("保存AI额度失败: " + e.getMessage());
        }
    }

    // ============================================================
    // 小楷助手积分制（2026-10-24）
    // ============================================================

    /** 平台「AI 默认规则」：积分规则 + 平台统一模型密钥（只回是否已配置） */
    @GetMapping("/ai-defaults")
    public Result<Map<String, Object>> getAiDefaults() {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rules", aiCreditService.globalRules());
            String channel = aiCreditService.defaultChannel();
            data.put("model", aiConfigService.getPlatformChannelSettings(channel));
            data.put("voice", Map.of("cmdWaitSec", aiConfigService.getVoiceWaitSec()));
            return Result.success(data);
        } catch (Exception e) {
            return Result.error("读取AI默认规则失败: " + e.getMessage());
        }
    }

    /**
     * 保存平台「AI 默认规则」。
     * body: { tokensPerCredit, modelRatesText, deepThinkingMultiplier, ownModelFree,
     *         lowBalance, newTenantCredits, newUserCredits, defaultChannel,
     *         baseUrl, model, apiKey }
     */
    @PostMapping("/ai-defaults")
    public Result<Map<String, Object>> saveAiDefaults(@RequestBody Map<String, Object> body) {
        try {
            aiCreditService.saveGlobalRules(body);
            String channel = body.get("defaultChannel") == null
                    ? aiCreditService.defaultChannel() : String.valueOf(body.get("defaultChannel"));
            aiConfigService.savePlatformChannelSettings(channel,
                    body.get("baseUrl") == null ? null : String.valueOf(body.get("baseUrl")),
                    body.get("model") == null ? null : String.valueOf(body.get("model")),
                    body.get("apiKey") == null ? null : String.valueOf(body.get("apiKey")));
            // 全局语音参数：等待指令时长（秒）；不传=不改
            Object waitSec = body.get("voiceCmdWaitSec");
            if (waitSec != null && !String.valueOf(waitSec).isBlank()) {
                try {
                    aiConfigService.saveVoiceWaitSec(Integer.valueOf(String.valueOf(waitSec).trim()));
                } catch (NumberFormatException ex) {
                    return Result.error("等待指令时长必须是整数秒");
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rules", aiCreditService.globalRules());
            data.put("model", aiConfigService.getPlatformChannelSettings(aiCreditService.defaultChannel()));
            data.put("voice", Map.of("cmdWaitSec", aiConfigService.getVoiceWaitSec()));
            return Result.success(data);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /** 平台「积分」弹窗：读取该客户公司总积分/累计消耗/本月消耗/已分配员工 */
    @GetMapping("/{id}/ai-credit")
    public Result<Map<String, Object>> getAiCredit(@PathVariable Long id) {
        try {
            return Result.success(tenantService.getTenantCreditSummary(id));
        } catch (Exception e) {
            return Result.error("读取客户积分失败: " + e.getMessage());
        }
    }

    /** 平台给客户增加/扣减公司总积分（delta 正数增加、负数扣减） */
    @PostMapping("/{id}/ai-credit")
    public Result<Map<String, Object>> adjustAiCredit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            BigDecimal delta = parseDecimal(body.get("delta"));
            if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
                return Result.error("请填写要调整的积分数量");
            }
            String remark = body.get("remark") == null ? "" : String.valueOf(body.get("remark"));
            return Result.success(tenantService.adjustTenantCredit(id, delta, remark));
        } catch (Exception e) {
            return Result.error("调整客户积分失败: " + e.getMessage());
        }
    }

    private BigDecimal parseDecimal(Object v) {
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    @Data
    public static class AiQuotaRequest {
        /** 月度token总额度；0=不限 */
        private Long quota;
    }

    @Data
    public static class ResetPasswordRequest {
        private String newPassword;
    }

    @Data
    public static class CreateTenantRequest {
        private String tenantCode;
        private String tenantName;
        private String version; // STANDARD, CRM, ULTIMATE
        private Integer trialDays;
        private LocalDate expireDate;
        private Integer maxUsers = 50;
        private String adminUsername;
        private String adminPassword;
        private String contactPerson;
        private String contactPhone;
        private String contactEmail;
        private String address;
        private String remark;
        /** 所属行业（2026-10-11 开账引导，选填；传了则建完自动按行业预设，客户首登不再弹向导） */
        private String industry;
    }

    @Data
    public static class UpdateTenantRequest {
        private String tenantName;
        private String version;
        private String contactPerson;
        private String contactPhone;
        private String contactEmail;
        private String address;
        private Integer maxUsers;
        private java.time.LocalDate expireDate;
        private String remark;
    }

    @Data
    public static class RenewRequest {
        private Integer months;
    }
}
