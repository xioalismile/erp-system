package com.erp.production.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.qc.entity.QcTemplate;
import com.erp.production.entity.WorkOperationLog;
import com.erp.production.entity.WorkOrderOperation;

import java.util.List;
import java.util.Map;

/**
 * 工单工序（车间执行）服务：状态流转 + 操作日志。
 *
 * <p>所有状态迁移都必须经此服务，禁止各页面/各单据自己给 operation_status 赋值
 * （守则 11：状态迁移只允许由统一入口执行；旧代码在报工单里各自写状态，本次收口）。</p>
 */
public interface WorkOrderOperationService extends IService<WorkOrderOperation> {

    /** 按工单查工序实例（行号升序） */
    List<WorkOrderOperation> listByOrder(Long orderId);

    /**
     * 车间看板（线路图）：按「工作中心」或「工单」分组返回在制工单的工序线路。**只读，不改任何状态**。
     *
     * <p>只纳入已生效工单；默认排除终态工序（已完工/已取消），includeFinished=true 时放开。
     * 组内按「计划完工日期（空值排最后）→ 工序号」升序；工序行额外带上工作中心名称与成品名称。</p>
     *
     * <p>【第 14 期】分线口径：workcenter 视图 = 「工作中心 + 工单 + 成品行」，线名 = {@code 工作中心名 · 工单号}；
     * order 视图 = 「工单 + 成品行」（与改造前一致）。group 与工序行均带上派工状态判定。</p>
     *
     * @param view            workcenter=按工作中心排线 / order=按工单排线
     * @param workCenterId    可选，只看某个工作中心（两视图均生效）
     * @param includeFinished 是否包含已完工/已取消工序
     * @param assigneeFilter  可选，派工状态筛选：all（默认、不过滤）/ assigned（只留已派工）/ unassigned（只留未派工）；
     *                        「未派工」= 工序状态为「待派工」或 负责人为空。过滤在展示集合阶段生效
     *                        （父子汇总仍用全量数据源，不受影响）；筛选后工序为空的线路整条不返回
     * 【第 2 版 2026-10-07】新增进度跟踪表格用的可选筛选与排序参数（**均为可选，不传 = 与改造前完全一致**）：
     * keyword（工单号/物料编码/物料名称 模糊）、status（工序状态精确）、planEndFrom/planEndTo（计划完工区间，
     * yyyy-MM-dd）、sortField（orderNo/materialCode/materialName/status/planEndDate）、sortOrder（asc/desc）。
     *
     * @return 每个元素形如 { key, name, productName, orderId, orderNo, materialCode, materialName, specification,
     *         workCenterId, workCenterName, ops:[工序行...] }
     */
    List<Map<String, Object>> board(String view, Long workCenterId, boolean includeFinished, String assigneeFilter,
                                    String keyword, String status, String planEndFrom, String planEndTo,
                                    String sortField, String sortOrder);

    /**
     * 【第 15 期】APS 排程看板（泳道 + 负载热力 + 物料齐套倒排 + 产能排队）。**只读，不改任何状态**。
     *
     * <p>与 {@link #board} 只增不删：既有的 {@code /board} 与全部参数原样保留。</p>
     *
     * <ul>
     *   <li>一条泳道 = 一个工作中心（无工作中心的历史工序单独成泳道 {@code workCenterId=null}）；</li>
     *   <li>块 = 某工单的某道工序占用该中心的时段（计划开工 → 计划完工，占用天数按工时 ÷ 日产能向上取整）；</li>
     *   <li>齐套 = 只算工单总齐套日：需求按成品主 BOM 展开，已到取现有库存（实际入库结果；
     *       <b>只统计仓库档案里「参与MRP」的仓库</b>，不合格品仓/废料仓的货不能投产、不计入），
     *       采购交货日期只用于「预计齐套」（{@code EXPECTED}），不参与判定；</li>
     *   <li>产能排队 = 同一工作中心逐日累计占用小时，超过日产能 → 该日超载并进 {@code alerts}。</li>
     * </ul>
     *
     * @param days            日期跨度，默认 7（仅接受 7 / 14 / 30，其余回落 7）
     * @param workCenterId    可选，只看某个工作中心
     * @param includeFinished 是否含已完工/已取消（默认 false）
     * @param assigneeFilter  all（默认）/ assigned / unassigned
     * @return { rangeStart, days, metrics, alerts, lanes }
     */
    Map<String, Object> scheduleBoard(int days, Long workCenterId, boolean includeFinished, String assigneeFilter);

    /**
     * 【第 15 期】应用重排：把同工作中心上撞车的工序按"挤到最近空档日"重排，
     * <b>回写</b> {@code work_order_operation.plan_start_date} / {@code plan_end_date}，并写一条工序操作日志留痕。
     *
     * <p><b>必须显式调用才写库</b>（看板查询本身绝不写库）。无工作中心或产能未维护的工序不参与重排。</p>
     *
     * @param orderIds     只重排这些工单的工序；为空 = 全部在排（已生效）工单
     * @param days         日期跨度（仅用于对齐看板口径，非档位一律回落 7）
     * @param operatorId   操作人ID（取当前登录态，不接受前端传值）
     * @param operatorName 操作人姓名
     * @return { updated: 改写条数, details: [{operationRowId, oldStart, newStart}] }
     */
    Map<String, Object> applyReschedule(List<Long> orderIds, int days, Long operatorId, String operatorName);

    /** 某道工序的操作日志（时间倒序） */
    List<WorkOperationLog> logs(Long operationRowId);

    /**
     * 【第 4 版 2026-10-08】「谁可以改负责人」判定：返回该工序的档位与当前登录人能否改负责人。
     *
     * <p>档位取自工序所属工艺路线（{@code routing.assign_scope}）；工序操作面板据此决定「负责人」下拉
     * 是否可改。与 {@link #act} 内的强校验**同源**（前端只控制可编辑性，后端才是最终门槛）。</p>
     *
     * @return { scope: 全部人/当前负责人/指定角色/工单开单人, canAssign: true/false }
     */
    Map<String, Object> assignPermission(Long operationRowId, Long operatorId, String operatorName);

    /**
     * 【第 9 期】我今天要做的工序（个人工作台卡片，**只读**）：
     * 当前登录人 = 工序负责人（employee.user_id → work_order_operation.assignee_id）、工序未完工/未取消、
     * 且所属工单已生效；只取「计划完工日期 ≤ 今天 + days 天」（默认 7，空值也纳入）。
     *
     * <p>当前登录人没有对应员工档案时返回空列表（不报错）。本方法只读，绝不给 operation_status 赋值。</p>
     */
    List<Map<String, Object>> myOpenOperations(Long userId, Integer days);

    /**
     * 执行一个工序动作。
     *
     * @param operationRowId 工单工序行ID（work_order_operation.id）
     * @param action         动作（见 {@link WorkOperationStatus}），如 派工/开工/暂停/恢复/完工/检验通过/检验退回/取消/数量报工
     * @param payload        附加参数：assigneeId / assigneeName（派工）、quantity / rejectedQuantity（报工）、
     *                       planEndDate（计划完工日期，可随时手选；不传则不动）、remark（暂停/退回原因）
     * @param operatorId     操作人ID（取当前登录态，不接受前端传值）
     * @param operatorName   操作人姓名
     * @return 变更后的工序行
     */
    WorkOrderOperation act(Long operationRowId, String action, Map<String, Object> payload,
                           Long operatorId, String operatorName);

    /** 供单据驱动链路调用（工序汇报单审核）：写状态 + 留痕，一次搞定 */
    void driveByBill(Long operationRowId, String targetStatus, String reason, String remark);

    /**
     * 【第 4 期】回退确认：下道工序发现不良 → 退回本道的数量。
     *
     * <p>只累计回退数量并留痕，**不改工序状态**（已完工的工序也可能被下道退回，故不受终态守卫限制）。</p>
     */
    WorkOrderOperation confirmRework(Long operationRowId, java.math.BigDecimal quantity, String remark,
                                     Long operatorId, String operatorName);

    /**
     * 【第 4 期】判废确认：本道师傅判定"回退料中不能用"的数量。
     *
     * <p>只累计判废数量并留痕，**不改工序状态**；判废数量决定能否下推补料。</p>
     */
    WorkOrderOperation confirmScrap(Long operationRowId, java.math.BigDecimal quantity, String remark,
                                    Long operatorId, String operatorName);

    /**
     * 【第 4 期】下推补料：按"尚未下推的判废数量 × 成品主BOM 子件用量"生成补料领料单（草稿）。
     *
     * <p>发起人 = 当前登录人；同一工序的可下推量随判废累计增长，已下推部分不会重复补料。</p>
     *
     * @return 生成的补料领料单号
     */
    String pushSupplement(Long operationRowId, String remark, Long operatorId, String operatorName);

    /**
     * 【第 11 期】入库类型工序：一键发起成品入库单（草稿）。
     *
     * <p>复用「工单 → 生产入库单」的服务方法生成入库单，来源记录该工序行（source_operation_id）；
     * 按"剩余可入库量 = 该工序合格产出 − 已下推入库量"控制，防重复。</p>
     *
     * @param quantity 本次入库数量（必须 &gt; 0 且不超过剩余可入库量）
     * @return { billNo: 生成的入库单号, operation: 变更后的工序行 }
     */
    Map<String, Object> pushReceipt(Long operationRowId, java.math.BigDecimal quantity,
                                    Long operatorId, String operatorName);

    /**
     * 【第 11 期】删除一道工序（用户口径的第二条「回程」：删掉重新添加）。
     *
     * <p>仅允许删除「已取消」的工序（已生效工单的其它工序一律锁死）；操作日志只增不删，删除后日志保留。</p>
     */
    void deleteOperation(Long operationRowId);

    /**
     * 【第 12 期】取该工序应使用的质检模板：工单工序显式绑定 → 按工序名称绑定 → 按物料绑定 → 未找到返回 null。
     *
     * <p>供工序面板「填质检」弹窗按模板渲染检验项表单。</p>
     */
    QcTemplate resolveQcTemplate(Long operationRowId);

    /**
     * 【第 12 期】提交工序检验（「完工待检」状态下按模板填质检）。
     *
     * <p>服务层按质检类型（首检/抽检/全检）判定结果，写 {@code qc_record} + {@code qc_record_item}，
     * 并把「合格数」写回工序 {@code approve_qty}（= 下一道可开工数量）。</p>
     * <ul>
     *   <li>判定合格 → 走统一状态机动作「检验通过」推入「已完工」；</li>
     *   <li>判定不合格 → 按所选处理方式（补料 / 返工回退 / 报废）处置后，走「检验退回」回到「进行中」。</li>
     * </ul>
     *
     * @param payload 含 items（逐项实测值）、inspectQty/qualifiedQty/defectQty、handleType、
     *                handleOperationName、handleRemark、remark
     * @param operatorId   检验人ID（取当前登录态，不接受前端传值）
     * @param operatorName 检验人姓名
     * @return { operation: 变更后的工序行, record: 检验记录, result: 合格/不合格, handleBillNo: 补料单号 }
     */
    Map<String, Object> submitInspection(Long operationRowId, Map<String, Object> payload,
                                         Long operatorId, String operatorName);

    /**
     * 单据驱动留痕：状态由调用方按业务算好并已落库，这里只补一条操作日志（不改状态）。
     * 用于报工单这类"改了数量顺带改状态"的场景。
     */
    void recordDriven(WorkOrderOperation op, String fromStatus, String toStatus, String reason, String remark);

    /**
     * 【2026-09-28】工序通知：把一条系统通知发给所选员工。
     *
     * <p>员工ID → 系统用户ID（员工档案 {@code user_id} 绑定）；未绑定登录账号的员工<b>跳过不报错</b>；
     * 一个有效接收人都没有时抛 {@link com.erp.config.BusinessException}（不静默成功）。
     * 通知写入复用 {@code SysNotificationService.insertOne}（含未读去重），并写一条工序操作日志留痕。</p>
     *
     * @param operationRowId      工序行ID
     * @param receiverEmployeeIds 接收人员工ID列表（可空/空 → 无有效接收人）
     * @param content             自定义内容（空则用默认模板「工序【X】已完成」）
     * @param operatorId          操作人ID（取当前登录态）
     * @param operatorName        操作人姓名
     * @return { sent: 实际写入条数, receivers: [接收人姓名...], skippedEmployeeIds: [未绑定账号的员工ID...] }
     */
    Map<String, Object> notifyOperation(Long operationRowId, List<Long> receiverEmployeeIds, String content,
                                        Long operatorId, String operatorName);

    // ==================== 【2026-10-25】扫码认领与按人报工 ====================

    /**
     * 扫码认领（「我来做」）：当前登录人对应的在职员工加入为<b>共同负责人</b>。
     *
     * <p>口径：不覆盖系统派工的主负责人；只有工序处于「待派工」且工单已生效时，认领人同时补为主负责人
     * （否则工序推不动）。认领不是改负责人，不受「谁可以改负责人」四档拦截，但受工序级
     * 「允许扫码认领」开关与身份资格约束（登录有效、生产权限、绑定在职员工、工序可执行）。</p>
     *
     * @param claimQuantity 认领数量（可空；空 = 不设个人上限；填了则个人累计报工不得超过它）
     * @return 变更后的工序行（含参与人与当前登录人的数据）
     */
    WorkOrderOperation claim(Long operationRowId, java.math.BigDecimal claimQuantity,
                             Long operatorId, String operatorName);

    /** 退出认领：结束自己的在岗记录；已报数量保留，未报份额释放回工序（主负责人不清空，避免工序无负责人） */
    WorkOrderOperation unclaim(Long operationRowId, Long operatorId, String operatorName);

    /** 该工序在岗的共同负责人（主负责人不在参与人表时补一行，primaryAssignee=true） */
    List<com.erp.production.entity.WorkOrderOperationWorker> workers(Long operationRowId);

    /**
     * 【2026-11-26】工序操作界面「负责人多选」：一次提交整份负责人名单。
     *
     * <p>第一位为主负责人（写 assignee_id），其余同步到共同负责人表；
     * 从名单里移除的人置为「已退出」。派工/改派仍按「谁可以改负责人」四档强校验。</p>
     */
    WorkOrderOperation setAssignees(Long operationRowId, List<Long> employeeIds,
                                    Long operatorId, String operatorName);

    /**
     * 工序详情（扫码页用）：工序行 + 在岗参与人 + 当前登录人的认领/已报/剩余 + 能否认领及原因。
     */
    WorkOrderOperation detailForWorker(Long operationRowId, Long operatorId, String operatorName);

    /**
     * 按人报工（扫码页直接填数量）：写报工流水 → 推进工序状态与数量 → 后台留存一条已生效的汇报记录。
     *
     * <p>校验（任一不满足即拒绝并给中文原因）：工序可报工；数量 &gt; 0；认领了数量的个人不得超个人剩余；
     * 所有人合计不得超过计划数量；同一防重号只成功一次。</p>
     *
     * @param payload { quantity: 本次合格数量（必填）, rejectedQuantity: 报废数量（可空）,
     *                  employeeId/employeeName: 代报的实际生产员工（可空 = 当前登录人）,
     *                  idemKey: 防重号（可空）, remark: 备注 }
     * @return { operation: 变更后的工序行, flow: 报工流水, myReportedQuantity, myRemainingQuantity }
     */
    Map<String, Object> reportByWorker(Long operationRowId, Map<String, Object> payload,
                                       Long operatorId, String operatorName);

    /**
     * 撤销报工：本人只能撤自己当班最近一笔；管理员可撤任意一笔。
     * 流水置「已冲销」，工序与个人数量同步回退，后台留存的汇报记录置「已作废」。
     *
     * @param flowId 指定流水ID（可空 = 撤销本人最近一笔）
     */
    Map<String, Object> undoReport(Long operationRowId, Long flowId,
                                   Long operatorId, String operatorName);
}
