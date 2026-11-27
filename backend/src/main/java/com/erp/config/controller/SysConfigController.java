package com.erp.config.controller;

import com.erp.common.Result;
import com.erp.config.entity.SysConfig;
import com.erp.config.service.SysConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sys-config")
public class SysConfigController {

    private final SysConfigService sysConfigService;

    public SysConfigController(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    /**
     * 写操作角色校验（安全审计 V-004b）：系统配置写操作仅 ADMIN 角色可执行。
     * 角色来源：AuthInterceptor 写入的 currentRoles 请求属性（sys_user_role 真实数据）。
     * 只读接口（含匿名白名单 /public/basic）不受影响。
     */
    private boolean isAdminRole() {
        Object roles = currentRequest().getAttribute("currentRoles");
        // 与 AuthInterceptor 的 fullAdmin 口径保持一致：ADMIN / SUPER_ADMIN
        // （原实现只认 ADMIN，会把平台超级管理员挡在门外）
        return roles instanceof List<?> roleList
                && roleList.stream().anyMatch(r -> "ADMIN".equals(String.valueOf(r))
                        || "SUPER_ADMIN".equals(String.valueOf(r)));
    }

    /**
     * 系统配置【写】权限（2026-10-08 收紧口径：<b>必须显式勾选写按钮才可保存</b>）。
     *
     * <p>放行条件（任一满足）：</p>
     * <ol>
     *   <li>管理级角色码 {@code ADMIN} / {@code SUPER_ADMIN}；</li>
     *   <li>权限含通配 {@code *}（超级管理员，来自 sys_role_permission.menu_id='*'）；</li>
     *   <li>角色被<b>显式勾选</b>了设置中心（含各设置子页）的写按钮：
     *       {@code system:<对象>:save|edit|add}，对象可为「设置中心」本身或任一设置子页
     *       （{@code system:sys-param} / {@code system:basic-info} / {@code system:role-perm} …，
     *        与前端 pagePerm 的 {@code system:<子页itemKey>} 口径一致）。</li>
     * </ol>
     *
     * <p><b>默认关闭</b>：未勾选写按钮一律拒绝（用户 2026-10-08 明确口径："必须是勾选了按钮的才能保存"）。
     * 注意排除历史遗留的分组容器码 {@code system:sub-*}（sub-system/sub-business/sub-custom/sub-log/sub-ai，
     * 早期 UI 把分组容器当页面写下，非真实页面，不应视作授权）。</p>
     *
     * <p><b>本次修复的坑（用户实测）</b>：原实现只认 {@code system:settings:} 前缀，且把
     * {@code system:settings:view}（"能进设置中心"的菜单权限码，任何能打开设置中心的角色都有）
     * 误当成"该对象已有显式按钮配置" → 直接判严格模式 → 非管理员即使被勾了「系统参数设置」的
     * 编辑/保存按钮也一律 403。现改为"只按是否有写动作判定"，并兼容设置子页对象编码。</p>
     *
     * <p>只读接口的敏感项过滤仍走 {@link #isAdminRole()}，不受本方法影响。</p>
     */
    private boolean canWriteSysConfig() {
        if (isAdminRole()) return true;

        HttpServletRequest req = currentRequest();
        List<String> perms = readStringListAttr(req, "currentPermissions");
        if (perms.contains("*")) return true;

        // 必须显式勾选设置中心（含各设置子页）的写按钮：system:<对象>:save|edit|add
        for (String p : perms) {
            if (p.startsWith("system:") && !p.startsWith("system:sub-")
                    && (p.endsWith(":save") || p.endsWith(":edit") || p.endsWith(":add"))) {
                return true;
            }
        }
        // 默认关闭：未勾选写按钮一律拒绝
        return false;
    }

    private HttpServletRequest currentRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }

    private List<String> readStringListAttr(HttpServletRequest req, String attr) {
        Object v = req.getAttribute(attr);
        if (v instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return java.util.Collections.emptyList();
    }

    /**
     * 公开只读基础配置（匿名可访问，登录页/全局初始化用）
     * 对应前端 getPublicBasicConfig()，白名单在 AuthInterceptor 中配置
     */
    @GetMapping("/public/basic")
    public Result<Map<String, String>> getPublicBasic() {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("system.name", sysConfigService.getValue("system.name", "智能ERP管理系统"));
        cfg.put("system.logo", sysConfigService.getValue("system.logo", ""));
        cfg.put("system.subtitle", sysConfigService.getValue("system.subtitle", ""));
        cfg.put("system.copyright", sysConfigService.getValue("system.copyright", ""));
        cfg.put("system.customer_service_phone", sysConfigService.getValue("system.customer_service_phone", ""));
        // 登录页改版（2026-09-13）：新布局开关与文案，匿名只读；空值=现有布局（各环境独立配置）
        cfg.put("system.login_bg", sysConfigService.getValue("system.login_bg", ""));
        cfg.put("system.login_title", sysConfigService.getValue("system.login_title", ""));
        // 登录页样式尺寸（可维护，改完刷新登录页即生效，无需发版）
        cfg.put("system.login_label_size", sysConfigService.getValue("system.login_label_size", ""));
        cfg.put("system.login_title_size", sysConfigService.getValue("system.login_title_size", ""));
        cfg.put("system.login_title_offset", sysConfigService.getValue("system.login_title_offset", ""));
        return Result.success(cfg);
    }

    /**
     * 敏感配置键（即使 config_type 未标 secret 也必须隐藏）
     */
    private static final java.util.Set<String> SENSITIVE_KEYS = java.util.Set.of(
            "ai.crypto.secret"   // AI 配置的 AES 主密钥+IV：泄露即可离线解密全部 API Key
    );

    /**
     * 无写权限时的提示：给出可操作的下一步（原提示"仅系统管理员可修改系统配置"会让
     * 已被授权的用户无从下手，2026-09-02 实测用户就卡在这句话上）。
     */
    private static final String NO_WRITE_PERM_MSG =
            "无权限修改系统配置：请在【设置中心 → 角色权限设置 → 按钮权限】为该角色勾选「系统设置」的 编辑/保存 权限";

    /**
     * 是否敏感配置（安全审计 C-2）：secret 类型 / 敏感键 / 已加密密文（ENC:）。
     * 原实现读接口无任何校验，任意已登录用户可 GET 全表配置并拿到 ai.crypto.secret。
     * 处理方式：非管理员读取时**不返回**这些行/值（不掩码，避免设置中心把掩码回写覆盖真实密钥）。
     */
    private boolean isSensitive(SysConfig c) {
        if (c == null) return false;
        if ("secret".equalsIgnoreCase(c.getConfigType())) return true;
        // 密码型（如物流授权码/签名密钥/17TRACK密钥）与 secret 型同等对待：非管理员读取时不返回
        if ("password".equalsIgnoreCase(c.getConfigType())) return true;
        if (c.getConfigKey() != null && SENSITIVE_KEYS.contains(c.getConfigKey())) return true;
        String v = c.getConfigValue();
        return v != null && v.startsWith("ENC:");
    }

    /**
     * 按配置键判断是否敏感（安全加固 2026-09-29）：
     * 除 secret 型 / 敏感键 / 密文外，**password 型**（物流授权码、签名密钥、17TRACK 密钥）也算敏感，
     * 其值只能由对应功能页的专用接口读写，通用接口一律不给明文。
     */
    private boolean isSensitiveByKey(String key) {
        if (key == null || key.isBlank()) return false;
        if (SENSITIVE_KEYS.contains(key)) return true;
        for (SysConfig c : sysConfigService.listAll()) {
            if (key.equals(c.getConfigKey())) return isSensitive(c);
        }
        String v = sysConfigService.getValue(key);
        return v != null && v.startsWith("ENC:");
    }

    @GetMapping
    public Result<List<SysConfig>> listAll() {
        // 安全加固（2026-09-29）：密钥/密码类配置**对所有角色都不返回**（含管理员），
        // 否则管理员打开旧系统参数页即可在界面上看到 AI Key 与 AI 加密主密钥、物流授权码明文。
        List<SysConfig> all = sysConfigService.listAll().stream().filter(c -> !isSensitive(c)).toList();
        return Result.success(all);
    }

    @GetMapping("/group/{group}")
    public Result<List<SysConfig>> listByGroup(@PathVariable String group) {
        List<SysConfig> list = sysConfigService.listByGroup(group).stream().filter(c -> !isSensitive(c)).toList();
        return Result.success(list);
    }

    @GetMapping("/{key}")
    public Result<SysConfig> getByKey(@PathVariable String key) {
        // 安全加固（2026-09-29）：密钥/密码类配置不通过接口返回明文（对任何角色，含管理员）
        if (isSensitiveByKey(key)) {
            return Result.error(403, "该配置项为密钥/密码类，接口不提供查询；请在对应功能页修改");
        }
        String value = sysConfigService.getValue(key);
        if (value == null) {
            // 不存在时返回空值（前端 Kgw* 设置页面会使用默认值）
            return Result.success(null);
        }
        SysConfig config = new SysConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        return Result.success(config);
    }

    /**
     * 全局开关只读暴露（2026-11-10 多单位换算第二阶段）：是否启用「浮动单位」。
     *
     * <p>单据编辑页用它决定明细「浮动单位/浮动数量」两列是否整列显示；
     * 未配置 / 读取异常一律按关闭（与后端 UnitConversionService#floatUnitEnabled 同源同默认）。</p>
     */
    @GetMapping("/float-unit/enabled")
    public Result<Boolean> floatUnitEnabled() {
        return Result.success(sysConfigService.getBooleanValue("sys.float.unit.enabled", false));
    }

    @PutMapping("/{key}")
    public Result<Void> updateValue(@PathVariable String key, @RequestBody SysConfig config) {
        if (!canWriteSysConfig()) return Result.error(403, NO_WRITE_PERM_MSG);
        boolean success = sysConfigService.updateValue(key, config.getConfigValue());
        if (success) {
            return Result.success();
        }
        return Result.error("更新失败，参数不存在");
    }

    @PutMapping("/batch")
    public Result<Void> batchUpdate(@RequestBody List<SysConfig> list) {
        if (!canWriteSysConfig()) return Result.error(403, NO_WRITE_PERM_MSG);
        boolean success = sysConfigService.batchUpdate(list);
        if (success) {
            return Result.success();
        }
        return Result.error("批量更新失败");
    }

    @PostMapping("/refresh")
    public Result<Void> refreshCache() {
        if (!canWriteSysConfig()) return Result.error(403, NO_WRITE_PERM_MSG);
        sysConfigService.refreshCache();
        return Result.success();
    }
}
