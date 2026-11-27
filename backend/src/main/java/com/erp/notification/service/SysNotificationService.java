package com.erp.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.notification.entity.SysNotification;
import com.erp.notification.mapper.SysNotificationMapper;
import com.erp.system.entity.SysUser;
import com.erp.system.service.SysUserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import com.erp.common.util.SortHelper;

/**
 * 系统通知 Service
 * - 列表查询 (支持按类型/已读状态筛选)
 * - 未读数统计
 * - 标记已读 (单条 / 全部 / 批量)
 * - 删除通知
 * - 自动建表: 启动时检查 sys_notification 表是否存在，不存在则创建
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysNotificationService {

    private final SysNotificationMapper notificationMapper;

    /** 微信订阅消息推送（审批提醒）：未授权/未绑定/未配置时静默跳过。 */
    private final WxSubscribeService wxSubscribeService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private SysUserService sysUserService;

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * 审批单据类型 → 中文名 / 前端路由前缀（点击通知跳转用）。
     * 与 MyDashboardHelper.BILL_TYPE_META 保持同步，版本独立。
     */
    private static final Map<String, String[]> BILL_TYPE_META = Map.ofEntries(
            Map.entry("SALE_QUOTE",     new String[]{"销售报价单",  "/sale/quotes/"}),
            Map.entry("SALE_ORDER",     new String[]{"销售订单",   "/sale/orders/"}),
            Map.entry("SALE_CONTRACT",  new String[]{"销售合同",   "/sale/contracts/"}),
            Map.entry("SALE_DELIVERY",  new String[]{"销售出库单",  "/sale/deliveries/"}),
            Map.entry("SALE_RETURN",    new String[]{"销售退货单",  "/sale/returns/"}),
            Map.entry("SALE_EXCHANGE",  new String[]{"销售换货单",  "/sale/exchanges/"}),
            Map.entry("SALE_REPAIR",    new String[]{"售后维修单",  "/sale/repairs/"}),
            Map.entry("SALE_INVOICE",   new String[]{"销售发票",   "/sale/invoices/"}),
            Map.entry("PURCHASE_REQUEST", new String[]{"采购申请单","/purchase/requests/"}),
            Map.entry("PURCHASE_ORDER", new String[]{"采购订单",   "/purchase/orders/"}),
            Map.entry("PURCHASE_CONTRACT",new String[]{"采购合同", "/purchase/contracts/"}),
            Map.entry("PURCHASE_INBOUND",new String[]{"采购入库单", "/purchase/inbounds/"}),
            Map.entry("PURCHASE_RETURN",new String[]{"采购退货单",  "/purchase/returns/"}),
            Map.entry("PURCHASE_INVOICE",new String[]{"采购发票",  "/purchase/invoices/"}),
            Map.entry("STOCK_TRANSFER", new String[]{"库存调拨单",  "/inventory/stock-transfers/edit/"}),
            Map.entry("STOCK_CHECK",    new String[]{"库存盘点单",  "/inventory/stock-checks/edit/"}),
            Map.entry("MATERIAL_REQUISITION", new String[]{"生产领料单","/inventory/material-requisitions/"}),
            Map.entry("MATERIAL_RETURN", new String[]{"生产退料单","/production/returns/"}),
            Map.entry("MATERIAL_SUPPLEMENT", new String[]{"生产补料单","/production/supplements/"}),
            Map.entry("OTHER_ISSUE",    new String[]{"其他出库单",  "/inventory/other-issues/"}),
            Map.entry("OTHER_RECEIPT",  new String[]{"其他入库单",  "/inventory/other-receipts/"}),
            Map.entry("WORK_ORDER",     new String[]{"生产工单",   "/production/workorders/"}),
            Map.entry("PRODUCTION_ORDER",new String[]{"生产工单",  "/production/workorders/"}),
            Map.entry("PRODUCE_ASSEMBLE",  new String[]{"生产组装单","/production/assemble/"}),
            Map.entry("PRODUCE_DISASSEMBLE",new String[]{"生产拆卸单","/production/disassemble/"}),
            Map.entry("OPERATION_REPORT",new String[]{"工序汇报",  "/production/operation-reports/"}),
            Map.entry("PRODUCTION_RECEIPT",new String[]{"生产入库单","/production/receipts/"}),
            Map.entry("BOM",            new String[]{"BOM清单",    "/masterdata/bom/"}),
            Map.entry("SAMPLE_ORDER",   new String[]{"打样单",     "/sample/edit/"}),
            Map.entry("FREE_SAMPLE",    new String[]{"免费样品订单",  "/freesample/edit/"}),
            Map.entry("SUBCONTRACT_ORDER",     new String[]{"委外加工单", "/subcontract/orders/"}),
            Map.entry("SUBCONTRACT_DELIVERY",  new String[]{"委外发料单","/subcontract/picks/"}),
            Map.entry("SUBCONTRACT_RECEIPT",   new String[]{"委外入库单","/subcontract/inbounds/"}),
            Map.entry("SUBCONTRACT_RETURN",    new String[]{"委外退料单","/subcontract/returns/"}),
            Map.entry("SUBCONTRACT_SUPPLEMENT",new String[]{"委外补料单","/subcontract/supplements/"}),
            Map.entry("PURCHASE_EXPENSE",      new String[]{"采购费用单", null}),
            Map.entry("PURCHASE_PRE_PAYMENT",  new String[]{"采购预付款单","/finance/purchase-pre-payments/"}),
            Map.entry("PAYMENT_RECEIPT",       new String[]{"收款单",    "/finance/receipt/"}),
            Map.entry("PAYMENT_VOUCHER",       new String[]{"付款单",    "/finance/payment/"}),
            Map.entry("OPERATION_EXPENSE",     new String[]{"费用分摊",  "/finance/operation-expense/"}),
            Map.entry("CUSTOMER_CREDIT",       new String[]{"客户授信", null}));

    /** 根据 billType 取中文显示名 */
    public static String billTypeName(String billType) {
        if (billType == null) return "单据";
        String[] m = BILL_TYPE_META.get(billType);
        return m != null ? m[0] : billType;
    }
    /** 根据 billType + billId 取前端跳转路径 */
    public static String billTypeRoute(String billType, Long billId) {
        if (billType == null || billId == null) return null;
        String[] m = BILL_TYPE_META.get(billType);
        if (m == null || m[1] == null || m[1].isEmpty()) return null;
        return m[1] + billId;
    }

    @PostConstruct
    public void init() {
        try {
            ensureTableExists();
        } catch (Exception e) {
            log.warn("sys_notification 初始化失败: {}", e.getMessage());
        }
    }

    /** 自动建表 + 索引 */
    private void ensureTableExists() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_notification", Integer.class);
            log.debug("sys_notification table exists.");
        } catch (Exception e) {
            log.info("sys_notification 表不存在，开始创建...");
            String createSql = """
                CREATE TABLE IF NOT EXISTS sys_notification (
                    id              BIGSERIAL PRIMARY KEY,
                    receiver_id     BIGINT      NOT NULL,
                    receiver_name   VARCHAR(64),
                    type            VARCHAR(32) NOT NULL,
                    title           VARCHAR(255) NOT NULL,
                    content         TEXT,
                    source_type     VARCHAR(64),
                    source_id       BIGINT,
                    source_no       VARCHAR(64),
                    route_path      VARCHAR(255),
                    urgency         VARCHAR(16),
                    is_read         SMALLINT    NOT NULL DEFAULT 0,
                    read_at         TIMESTAMP,
                    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    created_by      VARCHAR(64)
                )
                """;
            jdbcTemplate.execute(createSql);
            try {
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_notification_receiver_read ON sys_notification (receiver_id, is_read, created_at DESC)");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_notification_receiver_type ON sys_notification (receiver_id, type, created_at DESC)");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sys_notification_source ON sys_notification (source_type, source_id)");
                // 去重维度【含 type】：同一张单的「待审批 / 超期 / 即将超期 / 应收应付」等不同类别提醒可各自存在。
                // 历史 bug：维度不含 type → 先到的那类会把后到的顶掉（实锤：草稿提醒吃掉了"待审批"提醒）
                jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_notification_unread_source ON sys_notification (receiver_id, type, source_type, source_id) WHERE is_read = 0");
            } catch (Exception ex) {
                log.warn("创建索引失败: {}", ex.getMessage());
            }
            log.info("sys_notification 表创建完成。");
        }
    }

    /**
     * 分页查询当前用户的通知列表
     * @param userId     接收人ID
     * @param type       通知类型 (可空)
     * @param isRead     已读状态: null=全部, 0=未读, 1=已读
     * @param current    当前页
     * @param size       每页大小
     */
    public PageResult<SysNotification> pageQuery(Long userId, String type, Short isRead, int current, int size, String sortBy, String sortOrder) {
        QueryWrapper<SysNotification> qw = new QueryWrapper<>();
        qw.eq("receiver_id", userId);
        if (type != null && !type.isBlank()) {
            qw.eq("type", type);
        } else {
            // 草稿提醒（type=draft）已整体下线（2026-09-22 用户口径）：老库里的历史 draft 通知
            // 一律不再展示，但**保留在库中不删**（不做破坏性数据变更）。
            qw.ne("type", "draft");
        }
        if (isRead != null) qw.eq("is_read", isRead);
        qw.orderByDesc("is_read");          // 未读在前
        SortHelper.orderByNewest(qw, SysNotification.class);        // 新的在前
        Page<SysNotification> page = new Page<>(current, size);
        Page<SysNotification> result = notificationMapper.selectPage(page, qw);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /** 未读数量 (总数 + 按类型分组的明细) */
    public Map<String, Object> unreadCount(Long userId) {
        QueryWrapper<SysNotification> qw = new QueryWrapper<>();
        // 草稿提醒已下线：历史 draft 通知既不在列表展示，也不计入红点数字（行仍保留在库中）
        qw.eq("receiver_id", userId).eq("is_read", 0).ne("type", "draft");
        List<SysNotification> allUnread = notificationMapper.selectList(qw);

        Map<String, Long> byType = new HashMap<>();
        for (SysNotification n : allUnread) {
            byType.merge(n.getType(), 1L, Long::sum);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", (long) allUnread.size());
        result.put("byType", byType);
        return result;
    }

    /** 标记单条已读 */
    public void markRead(Long id, Long userId) {
        SysNotification n = notificationMapper.selectById(id);
        if (n == null) return;
        // 安全检查: 只能标记自己的通知
        if (!n.getReceiverId().equals(userId)) return;
        if (n.getIsRead() != null && n.getIsRead() == 1) return;
        n.setIsRead((short) 1);
        n.setReadAt(LocalDateTime.now());
        notificationMapper.updateById(n);
    }

    /** 标记全部已读 */
    public int markAllRead(Long userId) {
        SysNotification update = new SysNotification();
        update.setIsRead((short) 1);
        update.setReadAt(LocalDateTime.now());
        QueryWrapper<SysNotification> qw = new QueryWrapper<>();
        qw.eq("receiver_id", userId).eq("is_read", 0);
        return notificationMapper.update(update, qw);
    }

    /** 批量标记已读 */
    public int batchMarkRead(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) return 0;
        SysNotification update = new SysNotification();
        update.setIsRead((short) 1);
        update.setReadAt(LocalDateTime.now());
        QueryWrapper<SysNotification> qw = new QueryWrapper<>();
        qw.in("id", ids).eq("receiver_id", userId).eq("is_read", 0);
        return notificationMapper.update(update, qw);
    }

    /** 删除单条 (仅能删除自己的) */
    public void delete(Long id, Long userId) {
        SysNotification n = notificationMapper.selectById(id);
        if (n == null) return;
        if (!n.getReceiverId().equals(userId)) return;
        notificationMapper.deleteById(id);
    }

    /** 清空已读通知 */
    public int clearRead(Long userId) {
        QueryWrapper<SysNotification> qw = new QueryWrapper<>();
        qw.eq("receiver_id", userId).eq("is_read", 1);
        return notificationMapper.delete(qw);
    }

    // ======================================================================
    // 审批场景通用通知创建：写入 sys_notification（小铃铛红点来源）
    // 全部使用 try/catch 降级：通知失败不阻塞任何审批/业务事务
    // ======================================================================

    /**
     * 写一条通知入库（底层统一入口，做去重+索引冲突吞+userId校验）。
     * 【2026-09-28】可见性改为 public：供工序通知（{@code WorkOrderOperationServiceImpl}）复用同一插入/去重口径，
     * 禁止在业务侧另写 INSERT 绕过未读去重（uk_sys_notification_unread_source）。
     * @return true=写成功 false=跳过/失败
     */
    public boolean insertOne(SysNotification n) {
        if (n == null || n.getReceiverId() == null) return false;
        // 安全：userId必须真实存在，避免离职/无效用户留下不可读垃圾
        try {
            SysUser receiver = sysUserService.getById(n.getReceiverId());
            if (receiver == null) {
                log.debug("[SysNotification] skip invalid userId={}", n.getReceiverId());
                return false;
            }
            if (n.getReceiverName() == null || n.getReceiverName().isBlank()) {
                n.setReceiverName(receiver.getRealName() != null ? receiver.getRealName() : receiver.getUsername());
            }
        } catch (Exception ex) {
            log.warn("[SysNotification] user check fail userId={}: {}", n.getReceiverId(), ex.getMessage());
        }
        if (n.getIsRead() == null) n.setIsRead((short) 0);
        if (n.getUrgency() == null) n.setUrgency("NORMAL");
        if (n.getCreatedAt() == null) n.setCreatedAt(LocalDateTime.now());
        // 预查重（与唯一索引 uk_sys_notification_unread_source 同口径：receiver + 【type】 + source 且未读）：
        // 必须带 type —— 同一张单的"待审批"与"即将超期"是两条不同语义的提醒，不能被互相顶掉。
        // PostgreSQL 中语句异常会污染整个事务——即使 Java 层捕获 DuplicateKeyException，
        // 最终 COMMIT 也会变成 ROLLBACK，导致审批等业务事务静默回滚（接口仍返回成功）。
        // 因此必须先查后插，禁止依赖 catch 异常去重。
        if (n.getSourceType() != null && n.getSourceId() != null) {
            try {
                Long dup = notificationMapper.selectCount(new QueryWrapper<SysNotification>()
                        .eq("receiver_id", n.getReceiverId())
                        .eq("type", n.getType())
                        .eq("source_type", n.getSourceType())
                        .eq("source_id", n.getSourceId())
                        .eq("is_read", 0));
                if (dup != null && dup > 0) {
                    log.debug("[SysNotification] dedup skip(pre-check): receiver={} type={} sourceType={} sourceId={}",
                            n.getReceiverId(), n.getType(), n.getSourceType(), n.getSourceId());
                    return false;
                }
            } catch (Exception pe) {
                log.warn("[SysNotification] dedup pre-check fail(继续插入): {}", pe.getMessage());
            }
        }
        try {
            notificationMapper.insert(n);
            return true;
        } catch (DuplicateKeyException dke) {
            // DB唯一索引：同一接收人+同一来源单据未读时不重复堆叠，静默跳过
            // （并发竞态兜底；常态路径已被上方预查重拦截，避免污染事务）
            log.debug("[SysNotification] dedup skip: receiver={} sourceType={} sourceId={}",
                    n.getReceiverId(), n.getSourceType(), n.getSourceId());
            return false;
        } catch (Exception e) {
            // 通知写入失败绝不拉挂业务事务
            log.warn("[SysNotification] insert FAILED (ignored): type={} receiver={} err={}",
                    n.getType(), n.getReceiverId(), e.getMessage());
            return false;
        }
    }

    /**
     * 审批结束（通过/驳回）通知：给抄送人 + 发起人。
     * 调用方=ApprovalRuntimeService.finishBill（事务完成后调用）。
     *
     * @param billType           单据类型（SALE_CONTRACT等）
     * @param billId             单据ID
     * @param billNo             单据编号
     * @param approved           true=审批通过 false=被驳回
     * @param operatorName       审批最终操作人显示名（可为空）
     * @param comment            审批附言（可为空）
     * @param submitterUsername  发起人 username（ApprovalTask.submitBy），用于自动给发起人推送；可为空
     * @param notifyUserIdsJson  抄送人ID列表JSON数组（ApprovalFlow.notifyUserIds），如"[1,2,3]"；可为空/空数组
     */
    public void notifyApprovalFinish(String billType, Long billId, String billNo,
                                     boolean approved, String operatorName, String comment,
                                     String submitterUsername, String notifyUserIdsJson) {
        try {
            String typeName = billTypeName(billType);
            String route = billTypeRoute(billType, billId);
            String title = (approved ? "【审批通过】" : "【审批驳回】") + typeName
                    + (billNo != null ? " " + billNo : "");
            String content = "由 " + (operatorName == null ? "审批人" : operatorName) + " 审批完成"
                    + (comment == null || comment.isBlank() ? "" : "，附言：" + comment)
                    + "。点击查看单据详情；发起人自动接收本通知。";

            // 1. 收集所有接收人userId → 用Set去重
            Set<Long> receiverIds = new LinkedHashSet<>();

            // 1a) 发起人（submitterUsername -> userId；兼容登录名与姓名两种存法，
            //     历史实测 approval_task.submit_by 存的是姓名"System Administrator"，按 username 查不到会整条静默跳过）
            if (submitterUsername != null && !submitterUsername.isBlank()) {
                try {
                    SysUser submitter = sysUserService.getOne(
                            new QueryWrapper<SysUser>().and(w -> w.eq("username", submitterUsername)
                                    .or().eq("real_name", submitterUsername)).last("LIMIT 1"), false);
                    if (submitter != null && submitter.getUserId() != null) {
                        receiverIds.add(submitter.getUserId());
                    }
                } catch (Exception ex) {
                    log.warn("[SysNotification] 发起人解析失败 {}: {}", submitterUsername, ex.getMessage());
                }
            }

            // 1b) 抄送人（JSON数组）
            if (notifyUserIdsJson != null && !notifyUserIdsJson.isBlank()) {
                try {
                    List<Long> ids = JSON_MAPPER.readValue(notifyUserIdsJson, new TypeReference<List<Long>>() {});
                    if (ids != null) receiverIds.addAll(ids);
                } catch (Exception ex) {
                    log.warn("[SysNotification] 抄送人JSON解析失败: {} | raw={}", ex.getMessage(), notifyUserIdsJson);
                }
            }

            if (receiverIds.isEmpty()) return;

            // 2. 批量写sys_notification（给每个接收人单独一条）
            for (Long rid : receiverIds) {
                SysNotification n = new SysNotification();
                n.setReceiverId(rid);
                n.setType("approval_finish");
                n.setTitle(title);
                n.setContent(content);
                // source_type 统一小写口径：与 NotificationScheduler 生成的通知保持一致，
                // 否则同一单据(大小写不同)会被 (receiver_id, source_type, source_id) 去重索引判为两条 → 铃铛重复红点
                n.setSourceType(billType == null ? null : billType.toLowerCase(java.util.Locale.ROOT));
                n.setSourceId(billId);
                n.setSourceNo(billNo);
                n.setRoutePath(route);
                n.setUrgency(approved ? "NORMAL" : "URGENT");
                insertOne(n);
                // 微信订阅消息提醒（审批结果）：未授权/未绑定微信时服务内部静默跳过
                wxSubscribeService.pushApprovalResult(rid, typeName, billNo, approved, operatorName);
            }
            log.info("[SysNotification] 审批结束通知已推送 billType={} billId={} approved={} receivers={}",
                    billType, billId, approved, receiverIds.size());
        } catch (Exception e) {
            // 外层兜底：通知全失败不影响审批
            log.warn("[SysNotification] notifyApprovalFinish 整体失败（不影响业务）: {}", e.getMessage());
        }
    }

    /**
     * 审批流转待办通知：给某审批人推送"您有新的待办审批任务"铃铛。
     * 顺带修复历史脱节bug：之前只写ApprovalNotification独立表，铃铛红点sys_notification没同步=待办不进铃铛。
     *
     * @param approverUserId   审批人userId（由ApprovalNodeApprover.getUserId()传入）
     * @param approverName     审批人显示名
     * @param billType         单据类型
     * @param billId           单据ID
     * @param billNo           单据编号
     * @param nodeName         当前节点名
     * @param notifyType       "task"=新待办， "result"=审批结果（结果请走notifyApprovalFinish，这里仅做task待办兼容）
     */
    public void notifyApprovalTask(Long approverUserId, String approverName,
                                   String billType, Long billId, String billNo,
                                   String nodeName, String notifyType, String submitterName) {
        if (approverUserId == null) return;
        try {
            String typeName = billTypeName(billType);
            boolean isTask = !"result".equals(notifyType);
            // 任务通知跳「审批设置→我的待办」（审批按钮所在地）；结果通知跳单据详情页
            String route = isTask
                    ? "/system/settings?item=approval&sub=tasks"
                    : billTypeRoute(billType, billId);
            SysNotification n = new SysNotification();
            n.setReceiverId(approverUserId);
            n.setReceiverName(approverName);
            n.setType("approval");   // 铃铛type=approval（与现有待办分类tab一致，不是approval_finish）
            n.setTitle((isTask ? "待办审批: " : "审批结果: ") + typeName
                    + (billNo != null ? " " + billNo : "")
                    + (isTask && nodeName != null ? "（" + nodeName + "）" : ""));
            n.setContent(isTask
                    ? "您有新的审批任务：" + typeName + (billNo != null ? " " + billNo : "") + "，请前往审批设置办理。"
                    : "审批流程结束：" + typeName + (billNo != null ? " " + billNo : ""));
            // source_type 统一小写：NotificationScheduler 生成的通知同样用小写，
            // 口径不一致会让同一单据产生两条未读通知（去重索引按 receiver+source_type+source_id）
            n.setSourceType(billType == null ? null : billType.toLowerCase(java.util.Locale.ROOT));
            n.setSourceId(billId);
            n.setSourceNo(billNo);
            n.setRoutePath(route);
            n.setUrgency(isTask ? "URGENT" : "NORMAL");
            insertOne(n);
            // 微信订阅消息提醒（新待办才推；未授权/未绑定微信时服务内部静默跳过）
            if (isTask) {
                wxSubscribeService.pushApprovalTask(approverUserId, approverName,
                        typeName, billNo, nodeName, submitterName);
            }
        } catch (Exception e) {
            log.warn("[SysNotification] notifyApprovalTask 失败（不影响业务）: {}", e.getMessage());
        }
    }

    /**
     * 【2026-09-22】把某张单据的「待审批」未读通知置为已读（审批推进到下一节点 / 流程终结时调用）。
     *
     * <p><b>为什么必须有</b>：审批推进或终结后，前一个审批人铃铛里那条"待办审批：X"没有任何人负责
     * 标记已读 → 红点永久残留（121 实测：销售合同已「已生效」、审批任务已 approved，审批人的铃铛
     * 仍挂着"待办审批"未读，用户报"为什么挂到我的名下"）。</p>
     *
     * <p><b>只置已读、不物理删除</b>：① 用户口径"点击过已读之后永久已读"，删行会让定时扫描重新生成；
     * ② 未读唯一索引是部分索引（{@code WHERE is_read = 0}），置已读后索引即释放，
     * 下一节点的待办通知照常插入，不会互相顶掉。</p>
     */
    public void markApprovalNoticesRead(String billType, Long billId) {
        if (billId == null) return;
        try {
            SysNotification update = new SysNotification();
            update.setIsRead((short) 1);
            update.setReadAt(LocalDateTime.now());
            QueryWrapper<SysNotification> qw = new QueryWrapper<>();
            qw.eq("type", "approval")
              .eq("source_id", billId)
              .eq("is_read", 0);
            if (billType != null && !billType.isBlank()) {
                // 大小写不敏感匹配：写入侧现统一小写，但历史数据里两种大小写都存在（老版本写入过大写），
                // 若按等值匹配会漏掉历史行 → 那批红点永远消不掉。
                qw.apply("lower(source_type) = {0}", billType.toLowerCase(java.util.Locale.ROOT));
            }
            int n = notificationMapper.update(update, qw);
            if (n > 0) {
                log.info("[SysNotification] 审批推进/结束 → 自动置已读 {} 条待办通知 billType={} billId={}",
                        n, billType, billId);
            }
        } catch (Exception e) {
            // 通知清理失败绝不阻塞审批
            log.warn("[SysNotification] markApprovalNoticesRead 失败（不影响审批）: {}", e.getMessage());
        }
    }
}
