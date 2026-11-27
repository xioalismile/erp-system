package com.erp.production.service;

import java.util.List;

/**
 * 工单工序状态与动作口径（唯一事实来源）。
 *
 * <p>用户口径（2026-09-23）：工序状态共 8 个，前端按钮按"当前状态允许的动作"渲染，
 * 后端同样按此表校验（前端只传动作、不自己算状态 —— 守则 11 的"状态迁移只允许由统一入口执行"）。</p>
 *
 * <p>颜色由前端 `utils/operationStatus.js` 按状态名映射，本类只定义状态与流转。</p>
 */
public final class WorkOperationStatus {

    private WorkOperationStatus() {
    }

    /** 待派工（初始态） */
    public static final String PENDING_ASSIGN = "待派工";
    /** 已派工（已指定负责人，未开工） */
    public static final String ASSIGNED = "已派工";
    /** 已开工 */
    public static final String STARTED = "已开工";
    /** 进行中 */
    public static final String RUNNING = "进行中";
    /** 暂停（异常/待料/设备故障等） */
    public static final String PAUSED = "暂停";
    /** 完工待检（需质检工序完工后的中间态，未检验不得流转下道） */
    public static final String WAIT_QC = "完工待检";
    /** 已完工（终态） */
    public static final String FINISHED = "已完工";
    /** 已取消（终态） */
    public static final String CANCELLED = "已取消";

    public static final List<String> ALL = List.of(
            PENDING_ASSIGN, ASSIGNED, STARTED, RUNNING, PAUSED, WAIT_QC, FINISHED, CANCELLED);

    /** 【2026-09-28】工序类型：唯一允许出现「数量报工」动作的类型（其余类型一律剔除该动作） */
    public static final String TYPE_QUANTITY = "数量类型";

    /** 【第 13 期】工序类型「金额类型」：唯一可填「计划金额」/ 累加「已确认金额」的类型 */
    public static final String TYPE_AMOUNT = "金额类型";

    /**
     * 【2026-11-06】工序类型「入库类型」：完工即自动生成草稿生产入库单（写死规则，不做设置开关）。
     * 用户口径：工序选成入库类型，就等于允许自动生成入库单。
     */
    public static final String TYPE_RECEIPT = "入库类型";

    /** 动作常量 */
    public static final String ACT_ASSIGN = "派工";
    public static final String ACT_START = "开工";
    public static final String ACT_PAUSE = "暂停";
    public static final String ACT_RESUME = "恢复";
    public static final String ACT_FINISH = "完工";
    public static final String ACT_QC_PASS = "检验通过";
    public static final String ACT_QC_REJECT = "检验退回";
    public static final String ACT_CANCEL = "取消";
    public static final String ACT_REPORT = "数量报工";
    /**
     * 【第 11 期 · 第 4 版改名】把「已取消」的工序拉回「待派工」的唯一合法动作
     * （用户口径：既然给了取消，就必须有回程 —— ① 恢复工序 ② 删掉重新添加）。
     *
     * <p>名称由「重新派工」改为「恢复工序」（第 4 版 2026-10-08 用户拍板），避免与「派工/改派」混淆；
     * 旧动作名「重新派工」保留为历史别名（见 {@link #ACT_REOPEN_LEGACY}），{@code act(...)} 会归一。</p>
     */
    public static final String ACT_REOPEN = "恢复工序";

    /** 「恢复工序」的历史动作名（旧「重新派工」）：仅用于旧调用（AI 小助手/历史脚本）归一，不再作为可选按钮 */
    public static final String ACT_REOPEN_LEGACY = "重新派工";

    /** 【第 4 版 2026-10-08】「谁可以改负责人」四个档位（与 routing.assign_scope 取值一致） */
    public static final String ASSIGN_SCOPE_ALL = "全部人";
    public static final String ASSIGN_SCOPE_ASSIGNEE = "当前负责人";
    public static final String ASSIGN_SCOPE_ROLE = "指定角色";
    public static final String ASSIGN_SCOPE_CREATOR = "工单开单人";

    public static final List<String> ASSIGN_SCOPES = List.of(
            ASSIGN_SCOPE_ALL, ASSIGN_SCOPE_ASSIGNEE, ASSIGN_SCOPE_ROLE, ASSIGN_SCOPE_CREATOR);

    /**
     * 【2026-10-07 用户口径】撤销完工：把「已完工」退回「进行中」，供"数量 / 备注填错要改"的场景使用。
     *
     * <p>「已完工」原本是纯终态、一个动作都不给（用户实测反馈"报工完成后不能编辑了，填错了怎么办"）。
     * 本条是该状态的唯一回程，与「已取消 → 重新派工」同类。下游守卫（后道已开工 / 已发起入库 /
     * 工单已结案）在 {@code act()} 里判定，任一命中即拒绝并提示先处理下游 ——
     * 避免"前道退回进行中、后道却已按前道完工开工"的不一致。</p>
     */
    public static final String ACT_UNFINISH = "撤销完工";
    /**
     * 更新计划（只改负责人/计划完工日期，不改状态；车间经常要临时调日期）。
     * 【第 2 版 2026-10-07】面板上的「保存」已改用 {@link #ACT_SAVE}；本常量保留为**历史别名**
     * —— AI 小助手仍按"更新计划"传参，{@code WorkOrderOperationServiceImpl.act} 会把它归一成 ACT_SAVE，
     * 因此本常量不再出现在 {@link #allowedActions(String)} 里（即不再作为可选按钮）。
     */
    public static final String ACT_PLAN = "更新计划";

    /**
     * 【第 2 版 2026-10-07】保存：只把面板上改的东西（负责人 / 计划完工 / 备注 / 附件）存下来，
     * <b>不推进状态</b>。这是工序操作面板上**唯一**的保存类动作（原「更新计划」按钮已删除）。
     */
    public static final String ACT_SAVE = "保存";

    /**
     * 【第 2 版 2026-10-07】改派：已派工状态下重新指定负责人。
     * 替代原「派工」在该状态下的位置，避免"派工之后还能再派工"的重复语义（用户明确指出的逻辑错）。
     */
    public static final String ACT_REASSIGN = "改派";
    /** 单据驱动（工序汇报单审核）：不是人工点出来的动作，日志里必须能区分 */
    public static final String ACT_DRIVEN = "单据驱动";

    /**
     * 【第 15 期】计划重排：APS 排程看板「应用重排」回写计划开工/完工日期时留痕的动作码。
     * 与「更新计划」同属"改日期不改状态"一类，单列一个动作码便于把"算法重排"与"人工改期"区分开。
     */
    public static final String ACT_RESCHEDULE = "计划重排";

    /** 【2026-09-28】工序通知：@通知下道工序负责人/手选员工，**不改工序状态**，只落系统通知 + 留痕 */
    public static final String ACT_NOTIFY = "工序通知";

    /**
     * 【第 4 期】回退/判废与补料：这三个动作**不改工序状态**，只记数量与留痕，
     * 因此不进入 {@link #allowedActions(String)} 的流转表，由独立端点直接调用（避免和终态守卫冲突）。
     */
    /** 回退确认：下道工序发现不良 → 退回本道的数量 */
    public static final String ACT_REWORK = "回退确认";
    /** 判废确认：本道师傅判定回退料中不能用的数量 */
    public static final String ACT_SCRAP = "判废确认";
    /** 下推补料：按判废数量生成补料领料单 */
    public static final String ACT_PUSH_SUPPLEMENT = "下推补料";

    /**
     * 【第 11 期】发起入库单：入库类型工序一键生成成品入库单（草稿）。
     * 与回退/判废一样**不改工序状态**，只生成下游单据 + 写一条操作日志留痕。
     */
    public static final String ACT_PUSH_RECEIPT = "发起入库单";

    /**
     * 【第 12 期】填质检 / 不合格三种处理方式：**不改工序状态本身**（状态改由「检验通过 / 检验退回」承担），
     * 仅作为 {@code work_operation_log.action_type} 的留痕动作码，便于追溯"这批不合格是怎么处置的"。
     * 不进入 {@link #allowedActions(String)} 流转表。
     */
    /** 填质检：在「完工待检」状态下按模板录入实测值并提交检验 */
    public static final String ACT_QC_SUBMIT = "填质检";
    /** 不合格处理：补料（按 BOM 生成补料领料单） */
    public static final String ACT_QC_HANDLE_SUPPLEMENT = "质检补料";
    /** 不合格处理：返工回退到指定工序重做 */
    public static final String ACT_QC_HANDLE_REWORK = "质检返工回退";
    /** 不合格处理：记为报废 */
    public static final String ACT_QC_HANDLE_SCRAP = "质检报废";

    /** 该状态下允许的动作（前端按钮 + 后端校验共用） */
    public static List<String> allowedActions(String status) {
        String st = (status == null || status.isBlank()) ? PENDING_ASSIGN : status;
        return switch (st) {
            case PENDING_ASSIGN -> List.of(ACT_ASSIGN, ACT_SAVE, ACT_CANCEL);
            case ASSIGNED -> List.of(ACT_START, ACT_REPORT, ACT_REASSIGN, ACT_SAVE, ACT_CANCEL);
            case STARTED -> List.of(ACT_REPORT, ACT_PAUSE, ACT_FINISH, ACT_SAVE, ACT_CANCEL);
            case RUNNING -> List.of(ACT_REPORT, ACT_PAUSE, ACT_FINISH, ACT_SAVE, ACT_CANCEL);
            case PAUSED -> List.of(ACT_RESUME, ACT_FINISH, ACT_SAVE, ACT_CANCEL);
            case WAIT_QC -> List.of(ACT_QC_PASS, ACT_QC_REJECT, ACT_SAVE);
            // 已取消（终态）：唯一放行的动作是「重新派工」，把它拉回「待派工」
            case CANCELLED -> List.of(ACT_REOPEN);
            // 已完工：原为纯终态（无任何动作）→ 用户实测"填错了没法改"，补「撤销完工」作唯一回程
            case FINISHED -> List.of(ACT_UNFINISH);
            default -> List.of();
        };
    }

    /**
     * 【2026-09-28 用户口径】在「状态」基础上再叠加「工序类型」过滤：
     * <b>只有「数量类型」工序才允许出现动作「数量报工」</b>；
     * 状态类型 / 金额类型 / 入库类型一律从动作列表里剔除「数量报工」。
     *
     * <p>{@code operationType} 为空/空白 → 按「状态类型」处理（与前端 {@code op.operationType || '状态类型'}
     * 口径一致），即同样剔除「数量报工」。</p>
     *
     * <p>其余动作（派工/开工/暂停/恢复/完工/检验通过/检验退回/取消/重新派工/更新计划）不做类型过滤，
     * 完全沿用 {@link #allowedActions(String)} 的结果（状态机本身不动，只加类型这一维）。</p>
     */
    public static List<String> allowedActions(String status, String operationType) {
        List<String> base = allowedActions(status);
        String type = (operationType == null || operationType.isBlank()) ? "状态类型" : operationType.trim();
        if (TYPE_QUANTITY.equals(type)) {
            return base;
        }
        return base.stream().filter(a -> !ACT_REPORT.equals(a)).toList();
    }

    /**
     * 动作 → 目标状态。
     *
     * @param qcRequired 该工序是否需质检（true 时"完工"落到"完工待检"，检验通过才到"已完工"）
     */
    public static String targetStatus(String action, String fromStatus, boolean qcRequired) {
        return switch (action) {
            case ACT_ASSIGN -> ASSIGNED;
            // 【第 2 版】改派：已派工状态下换人，目标状态仍是「已派工」
            case ACT_REASSIGN -> ASSIGNED;
            case ACT_START -> STARTED;
            case ACT_PAUSE -> PAUSED;
            case ACT_RESUME -> RUNNING;
            case ACT_FINISH -> qcRequired ? WAIT_QC : FINISHED;
            case ACT_QC_PASS -> FINISHED;
            case ACT_QC_REJECT -> RUNNING;
            case ACT_CANCEL -> CANCELLED;
            // 第 11 期：已取消 → 重新派工 → 回到待派工（可同时改负责人/计划完工）
            case ACT_REOPEN -> PENDING_ASSIGN;
            // 撤销完工：退回「进行中」（保留已报工数量，可继续报工或重新完工）
            case ACT_UNFINISH -> RUNNING;
            // 报工本身不改状态之外的东西，只是累计数量：
            // 【第 2 版修正】已派工 / 已开工 上报工都要推进到「进行中」
            //（原写法只在"已开工"推进 → 已派工上报工状态不动，工序永远卡在"已派工"= 逻辑漏洞）
            case ACT_REPORT -> (STARTED.equals(fromStatus) || ASSIGNED.equals(fromStatus)) ? RUNNING : fromStatus;
            // 【第 2 版】保存：只存负责人/计划完工/备注/附件，状态不动
            case ACT_SAVE -> fromStatus;
            // 更新计划：仅作为历史别名保留（AI 小助手旧参数），语义与保存一致
            case ACT_PLAN -> fromStatus;
            default -> fromStatus;
        };
    }

    /** 终态判定（终态不可再推进） */
    public static boolean isTerminal(String status) {
        return FINISHED.equals(status) || CANCELLED.equals(status);
    }
}
