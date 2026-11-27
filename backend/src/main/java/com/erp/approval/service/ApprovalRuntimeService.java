package com.erp.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.approval.entity.*;
import com.erp.approval.mapper.*;
import com.erp.basedata.entity.Employee;
import com.erp.basedata.mapper.EmployeeMapper;
import com.erp.config.BusinessException;
import com.erp.config.service.SysConfigService;
import com.erp.notification.service.SysNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 审批运行时引擎 - 任务流转/通知
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalRuntimeService extends ServiceImpl<ApprovalTaskMapper, ApprovalTask> {

    private final ApprovalFlowService flowService;
    private final ApprovalNodeMapper nodeMapper;
    private final ApprovalNodeApproverMapper approverMapper;
    private final ApprovalConditionMapper conditionMapper;
    private final ApprovalLogMapper logMapper;
    private final ApprovalNotificationMapper notificationMapper;
    private final SysConfigService sysConfigService;
    private final EmployeeMapper employeeMapper;
    private final com.erp.system.mapper.SysUserMapper sysUserMapper;
    private final SysNotificationService sysNotificationService;
    /** 事务管理器：通知类写库用编程式 REQUIRES_NEW 独立事务，失败不毒化审批主事务 */
    private final PlatformTransactionManager transactionManager;
    /** 全部单据Service的审批回调（流程终结时按 billType 路由回写单据状态） */
    private final List<BillApprovalCallback> billCallbacks;

    // =============== 身份统一：登录用户名 ↔ 审批人姓名 ===============
    // 控制器传入的是登录用户名（如 qa_apv01），而节点审批人/任务记录的是员工姓名（如"QA审批人"）。
    // 统一在引擎侧解析为姓名后再匹配与展示；无员工档案的账号（如 admin）回退为用户名本身。

    /** 用户名 → 员工姓名（employee 表无 username 列，需经 sys_user.user_id 关联） */
    private String resolveDisplayName(String username) {
        if (username == null || username.isBlank()) return username;
        // 1) 登录名 → sys_user.user_id
        com.erp.system.entity.SysUser user = sysUserMapper.selectOne(
                new QueryWrapper<com.erp.system.entity.SysUser>()
                        .eq("username", username).last("LIMIT 1"));
        if (user == null || user.getUserId() == null) return username;
        // 2) user_id → employee.employee_name
        Employee emp = employeeMapper.selectOne(new QueryWrapper<Employee>()
                .eq("user_id", user.getUserId()).last("LIMIT 1"));
        if (emp != null && emp.getEmployeeName() != null && !emp.getEmployeeName().isBlank()) {
            return emp.getEmployeeName();
        }
        // 3) 兜底：无员工档案的账号（如用户管理直接创建的审批账号）用 sys_user.real_name，
        //    与流程配置里保存的审批人名（approverName=real_name）保持同一口径，避免待办匹配断链
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName();
        }
        return username;
    }

    /**
     * 校验节点审批人是否可解析到有效账号（存在且启用）。
     * 解析口径与 getMyPendingTasks 的匹配口径保持一致：
     *   ① approver.user_id 直接指向启用账号；
     *   ② approver_name = sys_user.real_name（无员工档案的审批账号）；
     *   ③ approver_name = employee.employee_name 且该员工绑定了启用账号。
     * 校验不通过直接抛业务异常，避免生成「任何人都看不到」的待办。
     */
    private void validateNodeApprovers(ApprovalFlow flow, ApprovalNode node) {
        if (node == null || node.getApprovers() == null || node.getApprovers().isEmpty()) return;
        List<String> invalid = new ArrayList<>();
        for (ApprovalNodeApprover a : node.getApprovers()) {
            if (!isApproverResolvable(a)) {
                String label = (a.getApproverName() == null || a.getApproverName().isBlank())
                        ? ("userId=" + a.getUserId()) : a.getApproverName();
                invalid.add(label);
            }
        }
        if (invalid.isEmpty()) return;
        throw new BusinessException("审批流程「" + (flow != null ? flow.getFlowName() : "")
                + "」节点「" + node.getNodeName() + "」的审批人无法对应到有效账号："
                + String.join("、", invalid)
                + "。请在 设置中心-审批设置-审批流程管理 中重新选择审批人后再提交");
    }

    /** 单个审批人是否可解析到启用账号 */
    private boolean isApproverResolvable(ApprovalNodeApprover a) {
        if (a == null) return false;
        // ① 直接给了账号ID
        if (a.getUserId() != null && isEnabledUser(sysUserMapper.selectById(a.getUserId()))) return true;
        String name = a.getApproverName();
        if (name == null || name.isBlank()) return false;
        // ② 姓名 = 某个启用账号的真实姓名
        if (sysUserMapper.selectCount(new QueryWrapper<com.erp.system.entity.SysUser>()
                .eq("real_name", name).eq("is_enabled", 1)) > 0) return true;
        // ③ 姓名 = 员工姓名，且该员工绑定了启用账号
        List<Employee> emps = employeeMapper.selectList(new QueryWrapper<Employee>().eq("employee_name", name));
        for (Employee e : emps) {
            if (e.getUserId() != null && isEnabledUser(sysUserMapper.selectById(e.getUserId()))) return true;
        }
        return false;
    }

    private boolean isEnabledUser(com.erp.system.entity.SysUser u) {
        return u != null && u.getIsEnabled() != null && u.getIsEnabled() == 1;
    }

    /** assigneeIds（形如 ["张三","李四"]）中是否精确包含某人（避免字符串contains的子串误判） */
    private boolean assigneeContains(String assigneeIds, String who) {
        if (assigneeIds == null || who == null || who.isBlank()) return false;
        String s = assigneeIds.trim();
        if (s.startsWith("[")) s = s.substring(1);
        if (s.endsWith("]")) s = s.substring(0, s.length() - 1);
        for (String p : s.split(",")) {
            if (who.equals(p.trim().replace("\"", ""))) return true;
        }
        return false;
    }

    /**
     * 判断单据类型是否启用审批 - 单据级开关
     */
    public boolean isBillApprovalEnabled(String billType) {
        // 1. 全局开关
        boolean globalEnabled = sysConfigService.getBooleanValue("approval.enabled", true);
        if (!globalEnabled) return false;
        // 2. 单据级开关
        String key = "approval.bill." + billType + ".enabled";
        return sysConfigService.getBooleanValue(key, false);
    }

    /** 更新单据级审批开关 */
    public void updateBillApprovalSwitch(String billType, boolean enabled, String operator) {
        String key = "approval.bill." + billType + ".enabled";
        boolean ok = sysConfigService.updateValue(key, String.valueOf(enabled));
        if (!ok) {
            // 不存在则新增
            com.erp.config.entity.SysConfig cfg = new com.erp.config.entity.SysConfig();
            cfg.setConfigKey(key);
            cfg.setConfigValue(String.valueOf(enabled));
            cfg.setConfigName(billType + " 审批开关");
            cfg.setConfigGroup("approval");
            cfg.setConfigType("boolean");
            cfg.setConfigOptions("[{\"label\":\"启用\",\"value\":true},{\"label\":\"停用\",\"value\":false}]");
            cfg.setRemark("由 " + operator + " 创建");
            cfg.setSortOrder(99);
            cfg.setCreatedBy(operator);
            sysConfigService.save(cfg);
        }
        sysConfigService.refreshCache();
        log.info("更新单据审批开关: {} = {}", key, enabled);
    }

    /**
     * 提交单据进入审批流程
     * @param billType 单据类型
     * @param billId 单据ID
     * @param billNo 单据编号（可空）
     * @param submitBy 提交人
     * @param billData 单据数据（用于条件判断，可为空）
     */
    @Transactional
    public ApprovalTask startApproval(String billType, Long billId, String billNo, String submitBy, Map<String, Object> billData) {
        // 检查单据级审批开关
        if (!isBillApprovalEnabled(billType)) {
            log.info("单据类型 {} 未启用审批，跳过", billType);
            return null;
        }

        // 检查是否已有进行中的任务
        ApprovalTask existing = getRunningTask(billType, billId);
        if (existing != null) {
            throw new BusinessException("单据已有进行中的审批任务，不能重复提交");
        }

        // 获取启用的流程（支持条件路由：传入单据数据进行条件匹配）
        ApprovalFlow flow = flowService.getEnabledFlowByBillType(billType, billData);
        if (flow == null || flow.getNodes() == null || flow.getNodes().isEmpty()) {
            throw new BusinessException("未找到" + billType + "的启用审批流程，请先在审批流程管理中配置");
        }

        // 根据条件确定起始节点
        Integer startNodeOrder = determineStartNodeOrder(flow, billData);
        ApprovalNode startNode = flow.getNodes().stream()
                .filter(n -> n.getNodeOrder().equals(startNodeOrder))
                .findFirst()
                .orElse(flow.getNodes().get(0));

        // 审批人有效性校验：节点审批人必须能落到「一个可登录的启用账号」，
        // 否则任务生成后 current_approver 与任何账号的姓名都对不上 → 待办对所有人不可见，
        // 单据永久卡在待审核（历史 bug：脏流程审批人姓名乱码，详见 db/76_approval_dirty_flow_cleanup.sql）。
        validateNodeApprovers(flow, startNode);

        // 创建第一个任务（提交人统一记录为姓名，便于待办列表展示）
        String submitByName = resolveDisplayName(submitBy);
        ApprovalTask task = new ApprovalTask();
        task.setFlowId(flow.getFlowId());
        task.setNodeId(startNode.getNodeId());
        task.setBillType(billType);
        task.setBillId(billId);
        task.setBillNo(billNo);
        task.setTaskStatus("pending");
        task.setSubmitBy(submitByName);
        task.setSubmitTime(LocalDateTime.now());
        task.setNodeOrder(startNode.getNodeOrder());
        task.setAssigneeIds(buildAssigneeIds(startNode.getApprovers()));
        // single 模式自动指定第一个；any/sequence 模式由审批人选择
        if ("single".equals(startNode.getApproverMode()) && startNode.getApprovers() != null && !startNode.getApprovers().isEmpty()) {
            task.setCurrentApprover(startNode.getApprovers().get(0).getApproverName());
        } else {
            task.setCurrentApprover(null); // 待选择
        }

        // 系统自动通过节点（approverMode=auto）：条件命中免人工审批，直接放行（支持连续自动节点）
        if ("auto".equals(startNode.getApproverMode())) {
            return completeAutoNode(task, flow, startNode, submitByName);
        }

        save(task);

        // 记录提交日志
        writeLog(task, flow, startNode, submitByName, "submit", "提交审批");

        // 发送通知
        sendNotifications(task, startNode, "task");

        log.info("启动审批任务: billType={}, billId={}, node={}", billType, billId, startNode.getNodeName());
        return task;
    }

    /** 构建下一节点的待办任务（复用上一任务的流程/单据/提交人信息） */
    private ApprovalTask buildNextTask(ApprovalTask prev, ApprovalNode node) {
        ApprovalTask t = new ApprovalTask();
        t.setFlowId(prev.getFlowId());
        t.setNodeId(node.getNodeId());
        t.setBillType(prev.getBillType());
        t.setBillId(prev.getBillId());
        t.setBillNo(prev.getBillNo());
        t.setTaskStatus("pending");
        t.setSubmitBy(prev.getSubmitBy());
        t.setSubmitTime(LocalDateTime.now());
        t.setNodeOrder(node.getNodeOrder());
        t.setAssigneeIds(buildAssigneeIds(node.getApprovers()));
        if ("single".equals(node.getApproverMode()) && node.getApprovers() != null && !node.getApprovers().isEmpty()) {
            t.setCurrentApprover(node.getApprovers().get(0).getApproverName());
        }
        return t;
    }

    /**
     * 系统自动通过节点处理：无需人工审批，直接置为已通过并推进。
     * 后续节点若仍为 auto 则循环放行；遇到人工节点创建待办；流程终结时回写单据（通过）。
     */
    private ApprovalTask completeAutoNode(ApprovalTask task, ApprovalFlow flow, ApprovalNode node, String operator) {
        task.setTaskStatus("approved");
        task.setCurrentApprover("系统(自动通过)");
        task.setComment("满足条件自动放行");
        task.setFinishTime(LocalDateTime.now());
        save(task);
        writeLog(task, flow, node, operator, "approve", "系统自动通过（满足条件免审）");

        // 推进到下一节点
        ApprovalNode nextNode = flowService.getFlowDetail(flow.getFlowId()).getNodes().stream()
                .filter(n -> n.getNodeOrder() > node.getNodeOrder())
                .min(Comparator.comparing(ApprovalNode::getNodeOrder))
                .orElse(null);
        if (nextNode == null) {
            // 流程结束：回写单据（置「已生效」并触发业务影响）
            sendNotifications(task, node, "result");
            finishBill(task, "系统(自动通过)", "满足条件自动放行", true);
            notifyAfterFinish(task, flow, "系统(自动通过)", "满足条件自动放行", true);
            log.info("审批流程自动通过终结: billType={}, billId={}", task.getBillType(), task.getBillId());
            return task;
        }
        if ("auto".equals(nextNode.getApproverMode())) {
            // 连续自动节点：继续放行
            return completeAutoNode(buildNextTask(task, nextNode), flow, nextNode, operator);
        }
        // 下一节点为人工节点：创建待办
        ApprovalTask nextTask = buildNextTask(task, nextNode);
        save(nextTask);
        sendNotifications(nextTask, nextNode, "task");
        log.info("自动放行后推进到下一节点: {}, approver={}", nextNode.getNodeName(), nextTask.getCurrentApprover());
        return nextTask;
    }

    /** 获取运行中的任务 */
    public ApprovalTask getRunningTask(String billType, Long billId) {
        List<ApprovalTask> tasks = list(new QueryWrapper<ApprovalTask>()
                .eq("bill_type", billType).eq("bill_id", billId).eq("task_status", "pending"));
        return tasks.isEmpty() ? null : tasks.get(0);
    }

    /**
     * 判断某登录用户是否为该审批任务的审批人（当前节点指定人 / any 模式候选人）。
     * 口径与 approve()/getMyPendingTasks() 完全一致：兼容「登录用户名」与「解析后的显示名」两种身份。
     * 2026-11-19 新增：供单据基类驳回放行判断（用户本人是审批人时允许在编辑页直接驳回）。
     */
    public boolean isTaskApprover(ApprovalTask task, String username) {
        if (task == null || username == null || username.isBlank()) return false;
        String displayName = resolveDisplayName(username);
        String cur = task.getCurrentApprover();
        if (cur != null && !cur.isEmpty()) {
            return username.equals(cur) || displayName.equals(cur);
        }
        // currentApprover 为空 = any/sequence 模式：按候选人列表判断
        return assigneeContains(task.getAssigneeIds(), username)
                || assigneeContains(task.getAssigneeIds(), displayName);
    }

    /** 获取单据的所有任务 */
    public List<ApprovalTask> getBillTasks(String billType, Long billId) {
        return list(new QueryWrapper<ApprovalTask>()
                .eq("bill_type", billType).eq("bill_id", billId).orderByAsc("node_order"));
    }

    /** 我的待办（按审批人过滤，兼容登录用户名与审批人姓名两种身份） */
    public List<ApprovalTask> getMyPendingTasks(String approver) {
        String displayName = resolveDisplayName(approver);
        List<ApprovalTask> all = list(new QueryWrapper<ApprovalTask>()
                .eq("task_status", "pending").orderByDesc("submit_time"));
        return all.stream().filter(t -> {
            // currentApprover 为空表示 any/sequence 模式，需候选审批人选择
            if (t.getCurrentApprover() == null || t.getCurrentApprover().isEmpty()) {
                return assigneeContains(t.getAssigneeIds(), approver)
                        || assigneeContains(t.getAssigneeIds(), displayName);
            }
            return approver.equals(t.getCurrentApprover()) || displayName.equals(t.getCurrentApprover());
        }).collect(Collectors.toList());
    }

    /**
     * 审批通过
     */
    @Transactional
    public ApprovalTask approve(Long taskId, String approver, String comment) {
        ApprovalTask task = getById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        if (!"pending".equals(task.getTaskStatus())) throw new BusinessException("任务非待办状态");

        // 控制器传入登录用户名，任务记录的是审批人姓名 → 统一解析为姓名再校验/展示
        String approverName = resolveDisplayName(approver);

        // 校验审批人权限（兼容用户名与姓名两种身份）
        String cur = task.getCurrentApprover();
        if (cur != null && !cur.isEmpty() && !approver.equals(cur) && !approverName.equals(cur)) {
            throw new BusinessException("您不是当前节点的指定审批人");
        }
        if (cur == null || cur.isEmpty()) {
            if (!assigneeContains(task.getAssigneeIds(), approver)
                    && !assigneeContains(task.getAssigneeIds(), approverName)) {
                throw new BusinessException("您不是当前节点的候选审批人");
            }
        }

        task.setTaskStatus("approved");
        task.setCurrentApprover(approverName);
        task.setComment(comment);
        task.setFinishTime(LocalDateTime.now());
        updateById(task);

        // 获取流程和当前节点
        ApprovalFlow flow = flowService.getById(task.getFlowId());
        ApprovalNode currentNode = nodeMapper.selectById(task.getNodeId());
        writeLog(task, flow, currentNode, approverName, "approve", comment);

        // 推进到下一节点
        ApprovalNode nextNode = flowService.getFlowDetail(task.getFlowId()).getNodes().stream()
                .filter(n -> n.getNodeOrder() > task.getNodeOrder())
                .min(Comparator.comparing(ApprovalNode::getNodeOrder))
                .orElse(null);

        if (nextNode == null) {
            // 流程结束：回写单据（置「已生效」并触发业务影响）
            sendNotifications(task, currentNode, "result");
            finishBill(task, approverName, comment, true);
            // 抄送通知给发起人+flow配置的抄送人（小铃铛）
            notifyAfterFinish(task, flow, approverName, comment, true);
            log.info("审批流程结束: billType={}, billId={}", task.getBillType(), task.getBillId());
            return task;
        }

        // 下一节点为系统自动通过节点时，自动放行并继续推进（含连续自动节点）
        if ("auto".equals(nextNode.getApproverMode())) {
            return completeAutoNode(buildNextTask(task, nextNode), flow, nextNode, approverName);
        }

        // 创建下一节点任务
        ApprovalTask nextTask = buildNextTask(task, nextNode);
        save(nextTask);
        sendNotifications(nextTask, nextNode, "task");
        log.info("推进到下一节点: {}, approver={}", nextNode.getNodeName(), nextTask.getCurrentApprover());
        return nextTask;
    }

    /**
     * 驳回（单据退回草稿）
     */
    @Transactional
    public ApprovalTask reject(Long taskId, String approver, String comment) {
        ApprovalTask task = getById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        if (!"pending".equals(task.getTaskStatus())) throw new BusinessException("任务非待办状态");

        // 身份校验（2026-11-19 补齐，与 approve() 完全同口径）：
        // 此前 reject 无校验=任何登录用户可驳回任何待审单（越权漏洞），工作台驳回接口暴露了它
        String approverName = resolveDisplayName(approver);
        String cur = task.getCurrentApprover();
        if (cur != null && !cur.isEmpty() && !approver.equals(cur) && !approverName.equals(cur)) {
            throw new BusinessException("您不是该单据的审批人，无法驳回");
        }
        if (cur == null || cur.isEmpty()) {
            if (!assigneeContains(task.getAssigneeIds(), approver)
                    && !assigneeContains(task.getAssigneeIds(), approverName)) {
                throw new BusinessException("您不是该单据的审批人，无法驳回");
            }
        }
        task.setTaskStatus("rejected");
        task.setCurrentApprover(approverName);
        task.setComment(comment);
        task.setFinishTime(LocalDateTime.now());
        updateById(task);

        ApprovalFlow flow = flowService.getById(task.getFlowId());
        ApprovalNode currentNode = nodeMapper.selectById(task.getNodeId());
        writeLog(task, flow, currentNode, approverName, "reject", comment);
        sendNotifications(task, currentNode, "result");
        // 驳回即流程终结：回写单据（退回草稿）
        finishBill(task, approverName, comment, false);
        // 抄送通知给发起人+flow配置的抄送人（小铃铛）
        notifyAfterFinish(task, flow, approverName, comment, false);
        log.info("审批驳回: billType={}, billId={}", task.getBillType(), task.getBillId());
        return task;
    }

    /**
     * 流程终结回写单据：按 billType 路由到对应单据Service。
     * 通过 → 单据置「已生效」并触发业务影响；驳回 → 单据退回「草稿」。
     */
    private void finishBill(ApprovalTask task, String operator, String comment, boolean approved) {
        if (task.getBillType() == null) return;
        for (BillApprovalCallback cb : billCallbacks) {
            // 按回调声明的「全部身份」匹配：默认实现=单个 getBillType()（既有单据零影响），
            // 一表多身份（生产领料/退料/补料）的实现返回多个身份，审批结果同样路由得回。
            Collection<String> cbTypes = cb.supportedBillTypes();
            if (cbTypes != null && cbTypes.contains(task.getBillType())) {
                // 传 billType：一表/一服务多身份（收款单+付款单、领料+退料+补料）时，
                // 单据主键可能同号，回写必须靠审批任务身份明确分流，不能靠 billId 猜身份。
                if (approved) {
                    cb.onApprovalApproved(task.getBillType(), task.getBillId(), operator, comment);
                } else {
                    cb.onApprovalRejected(task.getBillType(), task.getBillId(), operator, comment);
                }
                log.info("审批结果已回写单据: billType={}, billId={}, result={}",
                        task.getBillType(), task.getBillId(), approved ? "通过" : "驳回");
                return;
            }
        }
        log.warn("审批流程结束但该单据类型未接入回调: billType={}, billId={}",
                task.getBillType(), task.getBillId());
    }

    /**
     * 系统自动终结待办任务（语义等同"系统驳回"），用于付款管控分类变更致客户授信自动失效等场景。
     * 不做审批人权限校验，走完整回写（客户授信回调会把挂起单据退回草稿），通知按流程结果发送。
     */
    @Transactional
    public ApprovalTask autoFinishReject(Long taskId, String operator, String comment) {
        ApprovalTask task = getById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        if (!"pending".equals(task.getTaskStatus())) {
            log.info("任务已非待办，跳过系统自动驳回: taskId={}, status={}", taskId, task.getTaskStatus());
            return task;
        }
        String operatorName = resolveDisplayName(operator);
        task.setTaskStatus("rejected");
        task.setCurrentApprover(operatorName);
        task.setComment(comment);
        task.setFinishTime(LocalDateTime.now());
        updateById(task);

        ApprovalFlow flow = flowService.getById(task.getFlowId());
        ApprovalNode currentNode = nodeMapper.selectById(task.getNodeId());
        writeLog(task, flow, currentNode, operatorName, "reject", comment);
        sendNotifications(task, currentNode, "result");
        finishBill(task, operatorName, comment, false);
        notifyAfterFinish(task, flow, operatorName, comment, false);
        log.info("审批任务系统自动驳回(状态失效): taskId={}, billType={}, billId={}", taskId, task.getBillType(), task.getBillId());
        return task;
    }

    /**
     * 转交
     */
    @Transactional
    public ApprovalTask transfer(Long taskId, String fromApprover, String toApprover, String comment) {
        ApprovalTask task = getById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        if (!"pending".equals(task.getTaskStatus())) throw new BusinessException("任务非待办状态");

        String fromApproverName = resolveDisplayName(fromApprover);
        ApprovalFlow flow = flowService.getById(task.getFlowId());
        ApprovalNode currentNode = nodeMapper.selectById(task.getNodeId());
        writeLog(task, flow, currentNode, fromApproverName, "transfer", "转交给" + toApprover + "，" + (comment == null ? "" : comment));

        task.setCurrentApprover(toApprover);
        task.setComment(comment);
        updateById(task);
        sendNotifications(task, currentNode, "task");
        return task;
    }

    /**
     * 选择审批人（any/sequence 模式，从多个候选中选一个）
     */
    @Transactional
    public ApprovalTask selectApprover(Long taskId, String selector, String selectedApprover) {
        ApprovalTask task = getById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        if (!"pending".equals(task.getTaskStatus())) throw new BusinessException("任务非待办状态");
        if (task.getAssigneeIds() == null || !task.getAssigneeIds().contains(selectedApprover)) {
            throw new BusinessException("所选审批人不在候选列表中");
        }
        task.setCurrentApprover(selectedApprover);
        updateById(task);
        return task;
    }

    /**
     * 根据条件确定起始节点序号（支持表头全部字段类型：数字/字符串/日期/下拉/布尔）。
     * 操作符与流程路由 matchSingleRule 同口径：eq/ne/contains/gt/ge/lt/le/between，
     * 兼容旧符号写法 >=、>、<=、<、==（以及历史演示数据的大写 GT 等，统一小写归一）。
     * 数字条件值取 valueLow/valueHigh，文本/日期/下拉条件值取 valueText。
     */
    private Integer determineStartNodeOrder(ApprovalFlow flow, Map<String, Object> billData) {
        if (billData == null) return 1;
        List<ApprovalCondition> conditions = conditionMapper.selectList(
                new QueryWrapper<ApprovalCondition>().eq("flow_id", flow.getFlowId()));
        for (ApprovalCondition c : conditions) {
            Object actual = billData.get(c.getFieldKey());
            String op = normalizeOperator(c.getOperator());
            // between 上界走 valueHigh，单独比较；其余操作符统一走期望值比较
            boolean matched = "between".equals(op)
                    ? matchBetween(c, actual)
                    : matchConditionRule(op, actual, resolveExpectedValue(c));
            if (matched && c.getTargetNodeOrder() != null) {
                return c.getTargetNodeOrder();
            }
        }
        return 1;
    }

    /** 条件期望值：优先 valueText（字符串/日期/下拉），否则 valueLow（数字类旧条件） */
    private Object resolveExpectedValue(ApprovalCondition c) {
        if (c.getValueText() != null && !c.getValueText().isBlank()) return c.getValueText().trim();
        return c.getValueLow();
    }

    /** 操作符归一：去空白、转小写，旧符号映射到语义名（'GT'→gt、'>='→ge、'=='→eq 等） */
    private String normalizeOperator(String op) {
        if (op == null) return "eq";
        String s = op.trim().toLowerCase();
        return switch (s) {
            case ">=" -> "ge";
            case ">" -> "gt";
            case "<=" -> "le";
            case "<" -> "lt";
            case "==" -> "eq";
            default -> s; // eq/ne/contains/gt/ge/lt/le/between 原样
        };
    }

    /** 单条条件匹配：null 语义与流程路由一致（eq/ne 判空，大小比较空值不成立） */
    private boolean matchConditionRule(String op, Object actual, Object expected) {
        switch (op) {
            case "eq":
                if (actual == null || expected == null) return actual == expected;
                return compareConditionValue(actual, expected) == 0;
            case "ne":
                if (actual == null || expected == null) return actual != expected;
                return compareConditionValue(actual, expected) != 0;
            case "contains":
                if (actual == null || expected == null) return false;
                return actual.toString().contains(expected.toString());
            case "gt": return compareConditionValue(actual, expected) > 0;
            case "ge": return compareConditionValue(actual, expected) >= 0;
            case "lt": return compareConditionValue(actual, expected) < 0;
            case "le": return compareConditionValue(actual, expected) <= 0;
            default:
                log.warn("未知审批条件操作符: {}，按不匹配处理", op);
                return false;
        }
    }

    /** between 专用：actual 在 [expected(=valueLow), c.valueHigh] 区间内 */
    private boolean matchBetween(ApprovalCondition c, Object actual) {
        if (actual == null || c.getValueLow() == null) return false;
        int low = compareConditionValue(actual, c.getValueLow());
        if (low < 0) return false;
        if (c.getValueHigh() == null) return true;
        return compareConditionValue(actual, c.getValueHigh()) <= 0;
    }

    /** 数字按数值比较；日期/字符串统一转字符串比较（ISO 格式字典序=时间序） */
    private int compareConditionValue(Object actual, Object expected) {
        if (actual == null || expected == null) return -1;
        BigDecimal a = toConditionBigDecimal(actual);
        BigDecimal b = toConditionBigDecimal(expected);
        if (a != null && b != null) return a.compareTo(b);
        String as = toConditionString(actual);
        String bs = toConditionString(expected);
        // 日期对齐：expected 为纯日期(yyyy-MM-dd)时 actual 截断到天，eq 即"同一天"
        if (bs != null && bs.matches("^\\d{4}-\\d{2}-\\d{2}$") && as != null && as.length() > 10) {
            as = as.substring(0, 10);
        }
        return as.compareTo(bs);
    }

    /** LocalDateTime/LocalDate/Date → 可比较字符串；其余 toString（ISO 的 T 分隔符统一为空格） */
    private String toConditionString(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime dt) return dt.toString().replace('T', ' ');
        if (v instanceof java.time.LocalDate d) return d.toString();
        if (v instanceof java.time.OffsetDateTime odt) return odt.toLocalDateTime().toString().replace('T', ' ');
        if (v instanceof java.util.Date d) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
        }
        return v.toString();
    }

    private BigDecimal toConditionBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal d) return d;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        if (v instanceof Boolean) return null;
        try {
            return new BigDecimal(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String buildAssigneeIds(List<ApprovalNodeApprover> approvers) {
        if (approvers == null || approvers.isEmpty()) return "[]";
        return approvers.stream().map(ApprovalNodeApprover::getApproverName)
                .collect(Collectors.joining(",", "[", "]")).replaceAll(",", "\",\"")
                .replace("[", "[\"").replace("]", "\"]");
    }

    private void writeLog(ApprovalTask task, ApprovalFlow flow, ApprovalNode node, String approver, String action, String comment) {
        ApprovalLog log = new ApprovalLog();
        log.setTaskId(task.getTaskId());
        log.setFlowId(task.getFlowId());
        log.setNodeId(task.getNodeId());
        log.setBillType(task.getBillType());
        log.setBillId(task.getBillId());
        log.setBillNo(task.getBillNo());
        log.setNodeName(node != null ? node.getNodeName() : null);
        log.setNodeOrder(task.getNodeOrder());
        log.setApprover(approver);
        log.setAction(action);
        log.setComment(comment);
        log.setActionTime(LocalDateTime.now());
        logMapper.insert(log);
    }

    /**
     * 通知类写库统一走 REQUIRES_NEW 独立事务（编程式，私有方法自调用不会过代理，必须用 TransactionTemplate）：
     * 独立事务内失败则该通知事务单独回滚并只打 WARN，不影响审批主事务（也不会把外层事务标记为只回滚）。
     */
    private void runNotificationInNewTx(String action, Runnable write) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            tx.execute(status -> {
                write.run();
                return null;
            });
        } catch (Exception e) {
            log.warn("[Approval] {}失败（独立事务已回滚，不影响审批主流程）: {}", action, e.getMessage());
        }
    }

    private void sendNotifications(ApprovalTask task, ApprovalNode node, String notifyType) {
        // 先把该单据旧的「待审批」未读置为已读（2026-09-22 用户口径：审批推进/结束后红点必须自动消）：
        //   - 推进到下一节点：上一审批人的提醒已处理完，不该继续红着
        //   - 流程终结：待办已结束，不能永久残留（121 实锤：合同已「已生效」，审批人铃铛仍挂"待办审批"未读）
        // 只置已读、不物理删除；置已读后即释放未读唯一索引 → 下一节点的待办通知可正常插入。
        // REQUIRES_NEW 独立事务：失败只告警，不毒化审批主事务。
        runNotificationInNewTx("置旧待办通知已读",
                () -> sysNotificationService.markApprovalNoticesRead(task.getBillType(), task.getBillId()));
        if (node == null || node.getApprovers() == null) return;
        for (ApprovalNodeApprover a : node.getApprovers()) {
            ApprovalNotification n = new ApprovalNotification();
            n.setReceiverId(a.getUserId());
            n.setReceiverName(a.getApproverName());
            n.setBillType(task.getBillType());
            n.setBillId(task.getBillId());
            n.setBillNo(task.getBillNo());
            n.setTaskId(task.getTaskId());
            n.setNodeName(node.getNodeName());
            n.setNotifyType(notifyType);
            n.setTitle("task".equals(notifyType) ? "待办: " + node.getNodeName() : "审批结果: " + task.getBillNo());
            n.setContent("task".equals(notifyType)
                    ? "您有新的审批任务：" + task.getBillType() + " " + task.getBillNo()
                    : "审批流程结束：" + task.getBillType() + " " + task.getBillNo());
            n.setIsRead((short) 0);
            n.setCreateTime(LocalDateTime.now());
            notificationMapper.insert(n);

            // 同步一份到 sys_notification → 小铃铛红点（修复历史两表脱节bug：
            // 之前ApprovalNotification是审批独立表，铃铛红点sys_notification完全读不到）
            // REQUIRES_NEW 独立事务：同步失败只告警，审批照样成功且主事务不被毒化。
            runNotificationInNewTx("[ApprovalNotify->SysNoti] 铃铛同步", () ->
                    sysNotificationService.notifyApprovalTask(
                            a.getUserId(), a.getApproverName(),
                            task.getBillType(), task.getBillId(), task.getBillNo(),
                            node.getNodeName(), notifyType, task.getSubmitBy()));
        }
    }

    /**
     * 审批流程结束（通过/驳回）后给 发起人 + flow配置的抄送人 推送铃铛通知。
     * 通知写库包在 REQUIRES_NEW 独立事务里：失败只告警并回滚通知事务，绝不毒化审批主事务。
     */
    private void notifyAfterFinish(ApprovalTask task, ApprovalFlow flow,
                                   String operatorName, String comment, boolean approved) {
        if (task == null) return;
        String notifyUserIdsJson = (flow != null) ? flow.getNotifyUserIds() : null;
        runNotificationInNewTx("流程结束抄送通知", () ->
                sysNotificationService.notifyApprovalFinish(
                        task.getBillType(),
                        task.getBillId(),
                        task.getBillNo(),
                        approved,
                        operatorName,
                        comment,
                        task.getSubmitBy(),     // 发起人 username（自动进通知列表）
                        notifyUserIdsJson       // 抄送人JSON数组 [1,2,3]
                ));
    }

    /** 获取通知列表 */
    public List<ApprovalNotification> getNotifications(Long receiverId, Short isRead) {
        QueryWrapper<ApprovalNotification> qw = new QueryWrapper<>();
        if (receiverId != null) qw.eq("receiver_id", receiverId);
        if (isRead != null) qw.eq("is_read", isRead);
        qw.orderByDesc("create_time");
        return notificationMapper.selectList(qw);
    }

    /** 标记通知已读 */
    @Transactional
    public void markRead(Long notificationId) {
        ApprovalNotification n = notificationMapper.selectById(notificationId);
        if (n != null) {
            n.setIsRead((short) 1);
            n.setReadTime(LocalDateTime.now());
            notificationMapper.updateById(n);
        }
    }

    /** 获取未读数量 */
    public int getUnreadCount(Long receiverId) {
        return Math.toIntExact(notificationMapper.selectCount(
                new QueryWrapper<ApprovalNotification>().eq("receiver_id", receiverId).eq("is_read", 0)));
    }
}
