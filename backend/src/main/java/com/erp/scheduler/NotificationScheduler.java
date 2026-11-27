package com.erp.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.config.service.SysConfigService;
import com.erp.dashboard.dto.MyTodoDTO;
import com.erp.dashboard.service.DashboardService;
import com.erp.notification.entity.SysNotification;
import com.erp.notification.mapper.SysNotificationMapper;
import com.erp.system.entity.SysRole;
import com.erp.system.entity.SysUser;
import com.erp.system.entity.SysUserRole;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 系统通知生成定时任务
 * 复用 DashboardService 的查询能力，为每个启用用户生成待审批/超期/草稿/应收应付跟进通知
 *
 * 去重策略：
 * - 通过 uk_sys_notification_unread_source 唯一索引保证 (receiver_id, source_type, source_id) 三元组的未读通知唯一
 * - 已存在的未读通知不会被重复插入，避免铃铛重复推送
 * - 用户已读后**永久已读、不再重复打扰**：同一 (接收人, 来源类型, 来源单据) 只要生成过一次通知
 *   （无论已读/未读）就不再生成（2026-09-22 用户口径："点击过已读之后永久已读"）
 *
 * 通知类型映射（2026-09-22 收敛）：
 * - 待我审批任务 → type=approval（口径 = 审批引擎真实指派给我）
 * - 已超期/即将超期 → type=overdue (urgency=OVERDUE) 或 soon_overdue (urgency=URGENT/SOON)
 * - 应收应付跟进 → type=ar_ap
 * - 草稿单据 → type=draft **已下线**（用户 2026-09-22 口径：铃铛不要草稿提醒）
 *
 * 发送范围铁律（2026-09-22 用户口径）：**通知只能发给"与我有关"的单据**
 * —— 我提交/我创建的单据、真实指派给我的审批任务、被列为抄送人的我；
 * 不允许因为账号是管理员就把全公司单据推成他的红点（121 实测：ADMIN 账号铃铛挂着别人的单）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysNotificationMapper notificationMapper;
    private final DashboardService dashboardService;
    private final SysConfigService sysConfigService;

    /**
     * 每 2 分钟扫描一次，为所有启用用户生成通知
     * 频率考量：业务场景下用户切换页面 / 操作单据频繁，2 分钟既能及时推送又不过载
     */
    @Scheduled(cron = "0 */2 * * * ?")
    public void scanAndGenerateNotifications() {
        // 读取设置中心开关：消息通知总开关
        boolean notifEnabled = sysConfigService.getBooleanValue("notification.enabled", true);
        if (!notifEnabled) {
            log.info("【设置中心】消息通知总开关已关闭，跳过通知生成");
            return;
        }
        long start = System.currentTimeMillis();
        int totalUsers = 0;
        int totalGenerated = 0;
        try {
            // 查所有启用用户
            QueryWrapper<SysUser> qw = new QueryWrapper<>();
            qw.eq("is_enabled", 1).eq("is_locked", 0);
            List<SysUser> users = sysUserMapper.selectList(qw);
            totalUsers = users.size();

            for (SysUser user : users) {
                try {
                    totalGenerated += generateForUser(user);
                } catch (Exception e) {
                    log.warn("[通知扫描] 用户 {} 通知生成失败: {}", user.getUsername(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[通知扫描] 扫描任务异常", e);
        }
        log.info("[通知扫描] 完成：扫描用户 {} 个，新生成通知 {} 条，耗时 {} ms",
                totalUsers, totalGenerated, System.currentTimeMillis() - start);
    }

    /**
     * 为单个用户生成通知
     * @return 实际新增的通知数
     */
    private int generateForUser(SysUser user) {
        Long userId = user.getUserId();
        String username = user.getUsername();
        Long employeeId = user.getEmployeeId();
        List<String> roles = loadRealRoles(user);

        int count = 0;
        // 1. 待我审批 → approval（口径 = 审批引擎真实指派，与「我的待办」页面同源）
        count += generateApprovalNotifications(userId, username);
        // 2. 已超期/即将超期 → overdue / soon_overdue（**只取"与我有关"**，见方法注释）
        count += generateOverdueNotifications(userId, username, employeeId, roles);
        // 3. 应收应付跟进 → ar_ap（**只取"与我有关"**）
        count += generateArApNotifications(userId, username, employeeId, roles);
        // 4. 草稿提醒（type=draft）已整体下线（2026-09-22 用户口径）：
        //    不再生成 + 前端不再展示该类型；历史 draft 通知保留在库中，不做破坏性删除。
        return count;
    }

    /** 待审批通知 */
    private int generateApprovalNotifications(Long userId, String username) {
        // 读取设置中心开关：是否启用待审批通知
        boolean approvalEnabled = sysConfigService.getBooleanValue("notification.type.approval.enabled", true);
        if (!approvalEnabled) return 0;
        // 只推「审批引擎真实指派给该用户」的待办；不再用模块维度的 approval_status='待审核' 列表，
        // 否则会把不属于该用户的待审批单据推成假红点（历史 bug：按 username 猜角色误判 ADMIN → 全员推送）
        List<MyTodoDTO> todos = dashboardService.getMyEnginePendingApprovals(username);
        int count = 0;
        for (MyTodoDTO t : todos) {
            SysNotification n = buildNotification(userId, username, "approval",
                    "待审批：" + t.getTypeName(),
                    "单据 " + t.getBillNo() + " 待您审批",
                    t, "NORMAL");
            count += insertIfNotExists(n);
        }
        return count;
    }

    /**
     * 超期/即将超期通知。
     *
     * <p>2026-09-22 口径修正：改用 {@code getMyOverdueTasksSelfOnly} —— **通知只发"与我有关"的单据**
     * （created_by = 当前账号）。此前走工作台口径，管理员角色会拿到全公司的超期单，
     * 导致"铃铛挂着别人的单子"（用户明确不允许：通知只能看当前账号的信息）。</p>
     */
    private int generateOverdueNotifications(Long userId, String username, Long employeeId, List<String> roles) {
        // 读取设置中心开关：是否启用已超期通知 / 即将超期通知
        boolean overdueEnabled = sysConfigService.getBooleanValue("notification.type.overdue.enabled", true);
        boolean soonOverdueEnabled = sysConfigService.getBooleanValue("notification.type.soon_overdue.enabled", true);
        if (!overdueEnabled && !soonOverdueEnabled) return 0;
        List<MyTodoDTO> todos = dashboardService.getMyOverdueTasksSelfOnly(username, employeeId, roles);
        int count = 0;
        for (MyTodoDTO t : todos) {
            String urgency = t.getUrgency() != null ? t.getUrgency() : "NORMAL";
            String type = "OVERDUE".equals(urgency) ? "overdue" : "soon_overdue";
            // 按类型单独开关过滤
            if ("overdue".equals(type) && !overdueEnabled) continue;
            if ("soon_overdue".equals(type) && !soonOverdueEnabled) continue;
            String titlePrefix = "OVERDUE".equals(urgency) ? "已超期" : "即将超期";
            int days = t.getRemainingDays() != null ? t.getRemainingDays() : 0;
            String dayDesc = days < 0 ? "已超期 " + (-days) + " 天" :
                            days == 0 ? "今天到期" :
                            "剩余 " + days + " 天";
            SysNotification n = buildNotification(userId, username, type,
                    titlePrefix + "：" + t.getTypeName(),
                    "单据 " + t.getBillNo() + " " + dayDesc,
                    t, urgency);
            count += insertIfNotExists(n);
        }
        return count;
    }

    /**
     * 应收应付跟进通知。
     *
     * <p>2026-09-22 口径修正：改用 {@code getMyArApFollowUpSelfOnly} —— 同超期通知，
     * 只发"与我有关"（created_by = 当前账号）的单据，避免管理员账号收到全公司往来提醒。</p>
     */
    private int generateArApNotifications(Long userId, String username, Long employeeId, List<String> roles) {
        // 读取设置中心开关：是否启用应收应付跟进通知
        boolean arApEnabled = sysConfigService.getBooleanValue("notification.type.ar_ap.enabled", true);
        if (!arApEnabled) return 0;
        List<MyTodoDTO> todos;
        try {
            todos = dashboardService.getMyArApFollowUpSelfOnly(username, employeeId, roles);
        } catch (Exception e) {
            return 0; // 非财务角色调用会抛异常
        }
        int count = 0;
        for (MyTodoDTO t : todos) {
            String urgency = t.getUrgency() != null ? t.getUrgency() : "NORMAL";
            int days = t.getRemainingDays() != null ? t.getRemainingDays() : 0;
            String dayDesc = days < 0 ? "已逾期 " + (-days) + " 天" :
                            days == 0 ? "今天到期" :
                            "剩余 " + days + " 天";
            SysNotification n = buildNotification(userId, username, "ar_ap",
                    "应收应付跟进：" + t.getTypeName(),
                    "单据 " + t.getBillNo() + " " + dayDesc,
                    t, urgency);
            count += insertIfNotExists(n);
        }
        return count;
    }

    /** 构建 SysNotification 实体 */
    private SysNotification buildNotification(Long userId, String username, String type,
                                              String title, String content,
                                              MyTodoDTO t, String urgency) {
        SysNotification n = new SysNotification();
        n.setReceiverId(userId);
        n.setReceiverName(username);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setSourceType(t.getType() != null ? t.getType().toLowerCase() : null);
        n.setSourceId(t.getId());
        n.setSourceNo(t.getBillNo());
        n.setRoutePath(t.getRoutePath());
        n.setUrgency(urgency);
        n.setIsRead((short) 0);
        n.setCreatedAt(LocalDateTime.now());
        n.setCreatedBy("system-scheduler");
        return n;
    }

    /**
     * 插入通知，若该来源已有通知（无论已读/未读）则跳过。
     *
     * 修复"全部已读后刷新又出现"：原查询条件带 is_read=0，用户标记已读后记录变 is_read=1，
     * 下次定时扫描查不到未读记录就重新插入一条未读通知，导致铃铛红点反复出现。
     * 改为只要 (receiver_id, source_type, source_id) 存在过通知就不再生成——
     * 同一单据只提醒一次，用户已读后不重复打扰；用户主动清空已读(clearRead物理删除)后才会再次提醒。
     * 并发兜底仍由 uk_sys_notification_unread_source 唯一索引保障。
     */
    private int insertIfNotExists(SysNotification n) {
        if (n.getSourceId() == null || n.getSourceType() == null) return 0;
        try {
            // 查询该来源是否已存在通知（不区分已读/未读）
            QueryWrapper<SysNotification> qw = new QueryWrapper<>();
            qw.eq("receiver_id", n.getReceiverId())
              .eq("source_type", n.getSourceType())
              .eq("source_id", n.getSourceId());
            Long exists = notificationMapper.selectCount(qw);
            if (exists != null && exists > 0) return 0;
            notificationMapper.insert(n);
            return 1;
        } catch (Exception e) {
            // 唯一索引冲突时返回 0
            return 0;
        }
    }

    /**
     * 加载用户真实角色编码（口径与 AuthInterceptor 完全一致：sys_user_role → sys_role.role_code，
     * 无角色时降级 VIEWER）。
     * 禁止再用「按 username 关键字猜角色」——那会把 lyc123 这类账号默认当成 ADMIN，
     * 导致通知把不属于该用户的待审批单据推给他（铃铛有红点、页面却空）。
     */
    private List<String> loadRealRoles(SysUser user) {
        if (user.getUserId() == null) return List.of("VIEWER");
        try {
            List<SysUserRole> urs = sysUserRoleMapper.selectList(
                    new QueryWrapper<SysUserRole>().eq("user_id", user.getUserId()));
            List<Long> roleIds = urs.stream().map(SysUserRole::getRoleId)
                    .filter(Objects::nonNull).collect(Collectors.toList());
            if (roleIds.isEmpty()) return List.of("VIEWER");
            List<SysRole> roles = sysRoleMapper.selectBatchIds(roleIds);
            List<String> codes = roles.stream().map(SysRole::getRoleCode)
                    .filter(c -> c != null && !c.isEmpty()).collect(Collectors.toList());
            return codes.isEmpty() ? List.of("VIEWER") : codes;
        } catch (Exception e) {
            log.warn("[通知扫描] 加载用户角色失败 userId={}，降级 VIEWER: {}", user.getUserId(), e.getMessage());
            return List.of("VIEWER");
        }
    }
}
