package com.erp.config;

import com.erp.auth.util.JwtUtil;
import com.erp.tenant.TenantContextResolver;
import com.erp.system.entity.SysRole;
import com.erp.system.entity.SysUser;
import com.erp.system.entity.SysUserRole;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserMapper sysUserMapper;
    private final JdbcTemplate jdbcTemplate;
    /**
     * 租户上下文解析器（双保险）。
     * 角色/权限查询均"按当前租户库"执行；若租户上下文尚未建立（执行顺序异常），
     * 查询会落到主库 erp_db，取到同 user_id 的其它账号角色 → 越权判定错误（实测 403 的根因）。
     * 因此鉴权前再幂等调用一次，确保上下文一定正确。
     */
    private final TenantContextResolver tenantContextResolver;

    /**
     * 鉴权上下文缓存有效期（毫秒）。
     * 取 30 秒：既能把"每个请求重跑 5 类查询"压成"每用户每 30 秒一次"，
     * 又能在权限变更处漏调 evict 时于 30 秒内自愈，不会永久错权。
     */
    private static final long AUTH_CACHE_TTL_MS = 30_000L;

    /**
     * 鉴权上下文缓存：key = userId。
     * 【安全红线】必须按 userId 隔离，绝不允许跨用户复用，否则会串权限。
     */
    private final Map<String, AuthContext> authCache = new ConcurrentHashMap<>();

    /**
     * 鉴权缓存的 key：**必须带租户维度**（2026-09-20 线上定位到的真实缺陷）。
     *
     * 原先 key 只有 userId，而"角色/权限/员工"查询都是**按当前租户库**执行的。于是：
     *   `/api/tenant/select` 这类**尚未建立租户上下文**的请求（携带的是登录前、未含 tenantId 的 token）
     *   也会走鉴权，用 userId 去查**主库 erp_db**，把"主库里同 user_id 的其它账号角色"
     *   （实测拿到 SALES_REP）写进缓存；随后 30 秒内该用户在**租户库**下的所有请求都命中这份被污染的数据
     *   → 租户管理员被判为普通用户（建/删用户一律 403），且"隔离判定"整体失真。
     * 加入租户库名作前缀后，"无租户上下文(default)"与"各租户"的缓存互不干扰。
     */
    private String cacheKey(Long userId) {
        String db = com.erp.tenant.TenantContextHolder.getTenantDbName();
        return (db == null ? "default" : db) + ":" + userId;
    }

    /**
     * 权限版本号：任何角色/权限变更（evictAll / evict）时自增。
     * 供 /api/auth/perm-version 下发，前端轮询发现变化即重拉 userinfo——
     * 按钮权限改动因此**无需重新登录**即可在各端生效（2026-10-12 用户明确口径：
     * "按钮的开关开了就是能用，关了就是不能用，不允许重新登录才生效"）。
     * 重启归零无碍：前端发现版本与上次不同即重拉一次。
     */
    private final java.util.concurrent.atomic.AtomicLong permissionVersion =
            new java.util.concurrent.atomic.AtomicLong(0);

    /** 当前权限版本号（前端轮询比对用）。 */
    public long getPermissionVersion() {
        return permissionVersion.get();
    }

    /** 角色/权限发生变更时调用：清空全部鉴权缓存（受影响用户难以精确定位，全清最安全）。 */
    public void evictAll() {
        authCache.clear();
        permissionVersion.incrementAndGet();
    }

    /** 指定用户的角色/部门/员工发生变更时调用。 */
    public void evict(Long userId) {
        if (userId == null) return;
        // key 现在含租户库前缀，需清理该用户在所有租户下的缓存条目
        String suffix = ":" + userId;
        authCache.keySet().removeIf(k -> k.endsWith(suffix));
        permissionVersion.incrementAndGet();
    }

    /** 鉴权上下文：一次装配、短期缓存，替代原先每个请求都重跑的 5 类查询。 */
    private static class AuthContext {
        final List<String> roles;
        final List<String> permissions;
        /** 菜单/子功能路径权限（sys_role_permission.menu_id 中 '/' 开头者，含 '/system/settings?item=xxx'） */
        final List<String> menuPaths;
        final Long employeeId;
        final Long deptId;
        /**
         * 真实姓名（sys_user.real_name）。供操作日志等"要写清是谁"的场景使用：
         * 原先日志里的姓名被写成了登录账号（lyc123），用户看不出是谁改的。
         * 与 employeeId/deptId 共用同一次 sys_user 查询，不额外查库。
         */
        final String realName;
        /**
         * 租户管理员标记（sys_user.is_admin=1，2026-10-13 新增）。
         * 数据权限直通规则既有口径：「SUPER_ADMIN / ADMIN 角色、is_admin=1 的用户 → 全部数据」——
         * sc_wudong 等测试/客户租户的管理员账号角色编码不叫 ADMIN_*（如实测 Lyc123），
         * 按钮权限等"管理级放行"判定必须同样认 is_admin=1，否则租户管理员被当成普通角色拦死。
         */
        final boolean adminUser;
        final long expireAt;

        AuthContext(List<String> roles, List<String> permissions, List<String> menuPaths,
                    Long employeeId, Long deptId, String realName, boolean adminUser, long expireAt) {
            this.roles = roles;
            this.permissions = permissions;
            this.menuPaths = menuPaths;
            this.employeeId = employeeId;
            this.deptId = deptId;
            this.realName = realName;
            this.adminUser = adminUser;
            this.expireAt = expireAt;
        }
    }

    /**
     * 取鉴权上下文：命中缓存直接返回；未命中则按**原口径**逐项加载后写入缓存。
     * 装载逻辑与改造前逐行等价，只是外面套了一层缓存壳。
     */
    private AuthContext loadAuthContext(Long userId, String username) {
        long now = System.currentTimeMillis();
        AuthContext cached = authCache.get(cacheKey(userId));
        if (cached != null && now <= cached.expireAt) {
            return cached;
        }
        List<Long> roleIds = loadUserRoleIds(userId);
        List<String> roles = loadRoleCodes(roleIds, userId, username);
        List<String> permissions = loadUserPermissions(roleIds);
        List<String> menuPaths = loadMenuPaths(roleIds);
        // 【去重】原 loadEmployeeId/loadDeptId 各自 selectById(userId) 一次，同一行查两遍；
        // 这里合并为一次查询，两个字段共用。
        SysUser user = loadUserRow(userId);
        AuthContext fresh = new AuthContext(roles, permissions, menuPaths,
                user == null ? null : user.getEmployeeId(),
                user == null ? null : user.getDeptId(),
                user == null ? null : user.getRealName(),
                user != null && user.getIsAdmin() != null && user.getIsAdmin() == 1,
                now + AUTH_CACHE_TTL_MS);
        authCache.put(cacheKey(userId), fresh);
        return fresh;
    }

    /** 一次取出 sys_user（employeeId 与 deptId 共用），失败返回 null，不阻断请求。 */
    private SysUser loadUserRow(Long userId) {
        if (userId == null) return null;
        try {
            return sysUserMapper.selectById(userId);
        } catch (Exception e) {
            log.warn("加载用户信息失败 userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // OPTIONS预检放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        // 小楷独立版接口由 KaiAuthInterceptor 专管（独立用户体系），主鉴权直接放行
        if (path.startsWith("/api/kai-standalone/")) {
            return true;
        }
        // 登录、公开配置、AI回调、官网线索收集、构建信息（部署闸门探针）等公开接口放行
        if (path.contains("/api/auth/login")
                || path.contains("/api/ai/public")
                || path.startsWith("/api/sys-config/public")
                || path.startsWith("/api/public/lead")
                || path.startsWith("/api/public/work-order-share")
                || path.startsWith("/api/system/i18n/package")
                || path.equals("/api/system/build-info")) {
            return true;
        }

        // ★ 兜底：确保租户上下文已建立（2026-09-20 修复"隔离不生效/管理员被误判"）。
        // 鉴权里的角色与权限查询都"按当前租户库"执行；一旦上下文未建立，
        // 查询会落到主库 erp_db，取到主库里同 user_id 的其它账号角色（线上实测拿到 SALES_REP），
        // 使租户管理员被判为普通用户（建/删用户一律 403），同时也意味着隔离在请求层面失效。
        // 此处幂等调用（已建立则直接返回），彻底消除对 Filter/Interceptor 执行顺序的依赖。
        tenantContextResolver.resolve(request);
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            try {
                // 【安全隔离】小楷独立版 token 禁止访问主系统接口：
                // 独立用户 ID 与 sys_user 主键空间重叠，放行会导致越权串数据（红线）
                String scope = jwtUtil.parseScope(token);
                if ("kai-standalone".equals(scope)) {
                    log.warn("小楷独立版 token 尝试访问主系统接口: path={}", path);
                    writeJsonError(response, 401, "该登录凭据仅用于小楷独立版，不能访问本系统");
                    return false;
                }
                Long userId = jwtUtil.parseUserId(token);
                String username = jwtUtil.parseUsername(token);
                request.setAttribute("currentUserId", userId);
                request.setAttribute("currentUsername", username);
                // 【性能】鉴权上下文（角色/权限/员工/部门）改为 30 秒内存缓存。
                // 改造前：每个 /api/** 请求都重跑 5 类查询（其中 sys_user 同一行还被查两遍），
                // 实测单请求固定开销约 130ms、16 个并发时涨到 400ms，是"打开单据要好几秒"的根因。
                AuthContext ctx = loadAuthContext(userId, username);
                // 角色编码列表（用于平台接口放行等按角色判断的场景）
                request.setAttribute("currentRoles", ctx.roles);
                // 注入当前用户的权限码列表（sys_role_permission -> sys_menu.permission_code，
                // 并已并入「按钮权限」码 模块:对象:动作，与前端 v-permission 同码）
                // 按权限码判断，不按角色名硬编码；通配符 '*' 来自权限表记录
                request.setAttribute("currentPermissions", ctx.permissions);
                // 注入菜单/子功能路径权限（'/xxx' 形式，含 '/system/settings?item=xxx'），
                // 供"能进某菜单即视为该菜单未细分按钮"的默认口径判定（R2）
                request.setAttribute("currentMenuPaths", ctx.menuPaths);
                // 平台管理接口：按"管理员全权"或"显式授予的平台菜单"判定，不依赖某个特殊角色名
                // 判定口径：
                //   ① 角色编码为 SUPER_ADMIN/ADMIN（管理级），或角色在权限表持有通配权限 menu_id='*' → 全权
                //   ② 角色被显式授予「平台管理 → 客户管理」菜单（permission_code=platform:tenant-manage
                //      或菜单路径 /admin/tenant/manage）→ 放行客户管理接口（2026-10-10 与菜单授权对齐）
                // 注意：/api/admin/deploy/**（客户升级部署）始终仅超级管理员/系统管理员，不因菜单授权放行，
                //       与 DeployController 内部 isSuperAdmin 校验保持一致（升级/回滚风险高）。
                boolean fullAdmin = ctx.roles.contains("SUPER_ADMIN") || ctx.roles.contains("ADMIN") || ctx.permissions.contains("*");
                if (path.startsWith("/api/admin/") && !fullAdmin) {
                    boolean isDeployApi = path.startsWith("/api/admin/deploy");
                    boolean grantedCustomerAdmin = ctx.permissions.contains("platform:tenant-manage")
                            || ctx.menuPaths.contains("/admin/tenant/manage");
                    if (!(grantedCustomerAdmin && !isDeployApi)) {
                        log.warn("非授权用户尝试访问平台管理接口: path={}, username={}", path, username);
                        writeJsonError(response, 403, "无权限执行该操作");
                        return false;
                    }
                }
                // 注入当前用户的员工ID（来自 sys_user.employee_id），用于销售/采购等数据权限过滤
                request.setAttribute("currentEmployeeId", ctx.employeeId);
                // 注入部门ID
                if (ctx.deptId != null) {
                    request.setAttribute("currentDeptId", ctx.deptId);
                }
                // 注入当前用户真实姓名（操作日志写"是谁改的"用，取不到时由调用方回落账号）
                if (ctx.realName != null && !ctx.realName.isBlank()) {
                    request.setAttribute("currentRealName", ctx.realName);
                }
                // 注入租户管理员标记（sys_user.is_admin=1）：与数据权限直通规则同口径，
                // 供按钮权限等"管理级放行"判定使用——租户管理员角色编码不一定是 ADMIN_*（实测 Lyc123）
                request.setAttribute("currentUserIsAdmin", ctx.adminUser);
                
                return true;
            } catch (Exception e) {
                log.warn("JWT解析失败: {}", e.getMessage());
            }
        }
        writeJsonError(response, 401, "登录已过期或未登录，请重新登录");
        return false;
    }

    /**
     * 鉴权失败时返回 JSON 响应体。
     * 只 setStatus 而不写响应体，会让前端 fetch 拿到空内容后执行 response.json()，
     * 抛出 "Failed to execute 'json' on 'Response': Unexpected end of JSON input"，
     * 用户既看不懂也无法处置；这里统一返回可读提示，前端据此引导重新登录。
     */
    private void writeJsonError(HttpServletResponse response, int status, String message) {
        try {
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
            response.getWriter().flush();
        } catch (Exception e) {
            log.warn("写鉴权失败响应体异常: {}", e.getMessage());
        }
    }

    /**
     * 加载用户角色ID列表（sys_user_role.role_id）。
     */
    private List<Long> loadUserRoleIds(Long userId) {
        if (userId == null) return Collections.emptyList();
        String dbName = com.erp.tenant.TenantContextHolder.getTenantDbName();
        try {
            List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
            int hit = (userRoles == null) ? -1 : userRoles.size();
            // 诊断留痕：能一眼看出"角色查询是否落在正确的租户库"
            log.info("[鉴权诊断] loadUserRoleIds userId={}, 命中={}条, 当前租户库={}", userId, hit, dbName);
            if (userRoles != null && !userRoles.isEmpty()) {
                return userRoles.stream()
                        .map(SysUserRole::getRoleId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("加载用户角色ID失败 userId={}, 库={}: {}", userId, dbName, e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * 根据角色ID列表查询角色编码。无角色记录时降级为 ["VIEWER"]（只读）。
     * 禁止按用户名关键词推断角色（安全审计 V-002）。
     */
    private List<String> loadRoleCodes(List<Long> roleIds, Long userId, String username) {
        if (roleIds == null || roleIds.isEmpty()) return List.of("VIEWER");
        try {
            List<SysRole> roles = sysRoleMapper.selectBatchIds(roleIds);
            List<String> codes = roles.stream()
                    .map(SysRole::getRoleCode)
                    .filter(java.util.Objects::nonNull)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            if (!codes.isEmpty()) {
                log.info("[鉴权诊断] loadRoleCodes userId={}, roleIds={}, 角色编码={}", userId, roleIds, codes);
                return codes;
            }
        } catch (Exception e) {
            log.warn("加载角色编码失败 userId={}, 降级为 VIEWER: {}", userId, e.getMessage());
        }
        return List.of("VIEWER");
    }

    /**
     * 加载用户权限码列表（sys_role_permission -> sys_menu.permission_code）。
     * 通配符 '*' 来自权限表记录（menu_id='*'），不按角色名硬编码。
     */
    private List<String> loadUserPermissions(List<Long> roleIds) {
        List<String> perms = new ArrayList<>();
        if (roleIds == null || roleIds.isEmpty()) return perms;
        try {
            String placeholders = String.join(",", Collections.nCopies(roleIds.size(), "?"));
            // 1. 检查是否有通配符权限（menu_id='*'）
            List<Map<String, Object>> wildcardRows = jdbcTemplate.queryForList(
                    "SELECT 1 FROM sys_role_permission WHERE role_id IN (" + placeholders + ") AND menu_id = '*'",
                    roleIds.toArray());
            if (!wildcardRows.isEmpty()) {
                perms.add("*");
                return perms;
            }
            // 2. 查具体权限码（关联 sys_menu 拿 permission_code）
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT DISTINCT m.permission_code FROM sys_role_permission p " +
                            "JOIN sys_menu m ON m.route_path = p.menu_id " +
                            "WHERE p.role_id IN (" + placeholders + ") AND p.permission_type = '菜单' " +
                            "AND m.permission_code IS NOT NULL AND m.permission_code <> ''",
                    roleIds.toArray());
            for (Map<String, Object> row : rows) {
                Object pc = row.get("permission_code");
                if (pc != null && !pc.toString().isBlank()) {
                    perms.add(pc.toString());
                }
            }
            // 3. 按钮权限码：permission_type='按钮' 的记录，其 menu_id 存的就是按钮码本身
            //    （形如 模块:对象:动作，与前端 v-permission、后端写接口校验同一套码）。
            //    历史缺陷：这批记录既不进 permissions、也不被任何校验读取 → "角色里勾了按钮权限却毫无效果"。
            List<Map<String, Object>> btnRows = jdbcTemplate.queryForList(
                    "SELECT DISTINCT menu_id FROM sys_role_permission WHERE role_id IN (" + placeholders + ")"
                            + " AND permission_type = '按钮'",
                    roleIds.toArray());
            for (Map<String, Object> row : btnRows) {
                Object code = row.get("menu_id");
                if (code != null && !code.toString().isBlank() && !perms.contains(code.toString())) {
                    perms.add(code.toString());
                }
            }
        } catch (Exception e) {
            log.warn("加载用户权限码失败: {}", e.getMessage());
        }
        return perms;
    }

    /**
     * 加载菜单/子功能路径权限（sys_role_permission.menu_id 中 '/' 开头者，以及通配 '*'）。
     *
     * <p>用途：设置中心等"按子功能路径授权"的场景判定 —— 拥有某菜单路径（如
     * {@code /system/settings?item=sys-param}）即视为"该菜单未细分按钮"，按默认口径放行。
     * 按钮码（模块:对象:动作）与纯数字菜单ID 不计入本列表。</p>
     */
    private List<String> loadMenuPaths(List<Long> roleIds) {
        List<String> paths = new ArrayList<>();
        if (roleIds == null || roleIds.isEmpty()) return paths;
        try {
            String placeholders = String.join(",", Collections.nCopies(roleIds.size(), "?"));
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT DISTINCT menu_id FROM sys_role_permission WHERE role_id IN (" + placeholders + ")"
                            + " AND (menu_id = '*' OR menu_id LIKE '/%')",
                    roleIds.toArray());
            for (Map<String, Object> row : rows) {
                Object id = row.get("menu_id");
                if (id != null && !id.toString().isBlank()) {
                    paths.add(id.toString());
                }
            }
        } catch (Exception e) {
            log.warn("加载菜单路径权限失败: {}", e.getMessage());
        }
        return paths;
    }

    /**
     * 已移除 loadEmployeeId / loadDeptId 两个方法：
     * 二者原先各自 sysUserMapper.selectById(userId) 一次，同一行查两遍；
     * 现由 loadUserRow(userId) 一次取出，employeeId 与 deptId 共用。
     */
}
