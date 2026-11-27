package com.erp.production.controller;

import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.production.entity.WorkOperationLog;
import com.erp.production.entity.WorkOrderOperation;
import com.erp.production.service.WorkOperationStatus;
import com.erp.production.service.WorkOrderOperationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工单工序（车间执行）Controller
 * 路由: /api/production/work-order-operation
 *
 * <p>前端「工序操作面板」的全部取数与动作都走这里；状态与允许的动作由后端下发，
 * 前端只负责渲染与提交动作（不在前端自己算状态）。</p>
 */
@RestController
@RequestMapping("/api/production/work-order-operation")
@RequiredArgsConstructor
public class WorkOrderOperationController {

    private final WorkOrderOperationService workOrderOperationService;

    /** 某工单的全部工序实例 */
    @GetMapping("/list")
    public Result<List<WorkOrderOperation>> list(@RequestParam Long orderId) {
        return Result.success(workOrderOperationService.listByOrder(orderId));
    }

    /**
     * 车间看板（线路图）：按「工作中心」或「工单」分组返回在制工序线路。只读，不改任何状态。
     *
     * @param view            workcenter（默认，按工作中心排线）| order（按工单排线）
     * @param workCenterId    可选，只看某个工作中心
     * @param includeFinished 是否含已完工/已取消（默认 false）
     * @param assigneeFilter  可选，派工状态筛选：all（默认、不过滤）| assigned（只留已派工）| unassigned（只留未派工）；
     *                        不传 = all，行为与改造前逐条一致
     */
    @GetMapping("/board")
    public Result<List<Map<String, Object>>> board(@RequestParam(defaultValue = "workcenter") String view,
                                                   @RequestParam(required = false) Long workCenterId,
                                                   @RequestParam(defaultValue = "false") boolean includeFinished,
                                                   @RequestParam(required = false) String assigneeFilter,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String planEndFrom,
                                                   @RequestParam(required = false) String planEndTo,
                                                   @RequestParam(required = false) String sortField,
                                                   @RequestParam(required = false) String sortOrder) {
        return Result.success(workOrderOperationService.board(view, workCenterId, includeFinished, assigneeFilter,
                keyword, status, planEndFrom, planEndTo, sortField, sortOrder));
    }

    /**
     * 【第 15 期】APS 排程看板（泳道 + 负载热力 + 物料齐套倒排 + 产能排队）。只读，不改任何状态，
     * 也不回写任何单据日期（回写只发生在显式调用 {@link #applyReschedule} 时）。
     *
     * @param days            日期跨度，默认 7（仅 7 / 14 / 30，其余回落 7）
     * @param workCenterId    可选，只看某个工作中心
     * @param includeFinished 是否含已完工/已取消（默认 false）
     * @param assigneeFilter  派工状态筛选：all（默认）/ assigned / unassigned
     */
    @GetMapping("/schedule-board")
    public Result<Map<String, Object>> scheduleBoard(@RequestParam(defaultValue = "7") int days,
                                                     @RequestParam(required = false) Long workCenterId,
                                                     @RequestParam(defaultValue = "false") boolean includeFinished,
                                                     @RequestParam(required = false) String assigneeFilter) {
        return Result.success(workOrderOperationService.scheduleBoard(days, workCenterId, includeFinished, assigneeFilter));
    }

    /**
     * 【第 15 期】应用重排：按"挤到最近空档日"回写计划开工/完工日期 + 写工序操作日志。
     * <b>必须显式调用才写库</b>（看板查询本身绝不动单据日期）。
     *
     * @param body { orderIds: [..]（空/不传 = 全部在排工单）, days: 7 }
     */
    @PostMapping("/apply-reschedule")
    public Result<Map<String, Object>> applyReschedule(@RequestBody(required = false) Map<String, Object> body,
                                                       HttpServletRequest request) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        int days = payload.get("days") == null ? 7 : Integer.parseInt(String.valueOf(payload.get("days")));
        return Result.success(workOrderOperationService.applyReschedule(
                toLongList(payload.get("orderIds")), days,
                SecurityUtils.getCurrentUserId(request), SecurityUtils.getCurrentUsername(request)));
    }

    /**
     * 【第 9 期】我今天要做的工序：当前登录人作为工序负责人、未完工/未取消、且所属工单已生效的工序（只读）。
     *
     * @param days 只取「计划完工日期 ≤ 今天 + days 天」，默认 7
     */
    @GetMapping("/my-open-operations")
    public Result<List<Map<String, Object>>> myOpenOperations(@RequestParam(required = false, defaultValue = "7") Integer days,
                                                             HttpServletRequest request) {
        return Result.success(workOrderOperationService.myOpenOperations(
                SecurityUtils.getCurrentUserId(request), days));
    }

    /** 某道工序的操作日志（时间倒序） */
    @GetMapping("/{id}/logs")
    public Result<List<WorkOperationLog>> logs(@PathVariable Long id) {
        return Result.success(workOrderOperationService.logs(id));
    }

    /**
     * 【第 4 版 2026-10-08】「谁可以改负责人」判定：返回该工序档位 + 当前登录人能否改负责人。
     * 工序操作面板打开时调用，决定「负责人」下拉是"可改"还是"只读"。
     */
    @GetMapping("/{id}/assign-permission")
    public Result<Map<String, Object>> assignPermission(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(workOrderOperationService.assignPermission(
                id, SecurityUtils.getCurrentUserId(request), SecurityUtils.getCurrentUsername(request)));
    }

    /**
     * 状态与动作元数据：状态清单 + 每个状态下允许的动作。
     * 前端按它渲染操作按钮（避免前后端各写一份流转表）。
     *
     * <p>【2026-09-28】新增可选参数 {@code operationType}：传了则按「工序类型」过滤动作
     * （只有「数量类型」才保留「数量报工」；状态/金额/入库类型剔除该动作）；不传则与改造前完全一致。</p>
     */
    @GetMapping("/status-meta")
    public Result<Map<String, Object>> statusMeta(
            @RequestParam(required = false) String operationType) {
        Map<String, Object> meta = new LinkedHashMap<>();
        List<Map<String, Object>> states = new ArrayList<>();
        for (String s : WorkOperationStatus.ALL) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", s);
            row.put("actions", WorkOperationStatus.allowedActions(s, operationType));
            row.put("terminal", WorkOperationStatus.isTerminal(s));
            states.add(row);
        }
        meta.put("states", states);
        return Result.success(meta);
    }

    /** 执行工序动作（派工/开工/暂停/恢复/完工/检验通过/检验退回/取消/数量报工） */
    @PostMapping("/{id}/action")
    public Result<WorkOrderOperation> action(@PathVariable Long id,
                                             @RequestBody Map<String, Object> body,
                                             HttpServletRequest request) {
        String action = body.get("action") == null ? "" : String.valueOf(body.get("action"));
        if (action.isBlank()) return Result.error(400, "缺少动作参数");
        // 操作人取当前登录态，不接受前端传值（审计要求）
        return Result.success(workOrderOperationService.act(
                id, action, body,
                SecurityUtils.getCurrentUserId(request),
                SecurityUtils.getCurrentUsername(request)));
    }

    // ---------------- 【2026-10-25】扫码认领与按人报工 ----------------
    // 口径：扫码「我来做」= 加入为共同负责人（不覆盖主负责人）；报工在工序页直接填数量，不跳任何单据。
    // 资格判定全部在服务端（登录 + 生产权限 + 绑定在职员工 + 工单生效未结案 + 工序可执行 + 工序开关）。

    /** 工序详情（扫码页）：含在岗参与人、我的认领/已报/剩余、能否认领及原因 */
    @GetMapping("/{id}/detail")
    public Result<WorkOrderOperation> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(workOrderOperationService.detailForWorker(
                id, SecurityUtils.getCurrentUserId(request), SecurityUtils.getCurrentUsername(request)));
    }

    /** 在岗参与人（主负责人不在参与人表时补一行展示） */
    @GetMapping("/{id}/workers")
    public Result<List<com.erp.production.entity.WorkOrderOperationWorker>> workers(@PathVariable Long id) {
        return Result.success(workOrderOperationService.workers(id));
    }

    /** 【2026-11-26】工序操作界面「负责人多选」：整份名单提交，第一位为主负责人 */
    @PutMapping("/{id}/assignees")
    public Result<WorkOrderOperation> setAssignees(@PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, Object> body,
                                                   HttpServletRequest request) {
        List<Long> ids = new ArrayList<>();
        Object raw = body == null ? null : body.get("employeeIds");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o == null || String.valueOf(o).isBlank()) continue;
                try {
                    ids.add(Long.valueOf(String.valueOf(o)));
                } catch (NumberFormatException ignored) {
                    // 非法ID直接忽略，由服务层统一提示「至少选择一位负责人」
                }
            }
        }
        return Result.success(workOrderOperationService.setAssignees(
                id, ids, SecurityUtils.getCurrentUserId(request), SecurityUtils.getCurrentUsername(request)));
    }

    /** 扫码认领：我来做（可选填「我打算做多少」） */
    @PostMapping("/{id}/claim")
    public Result<WorkOrderOperation> claim(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> body,
                                            HttpServletRequest request) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        return Result.success(workOrderOperationService.claim(
                id, toDecimal(payload.get("claimQuantity")),
                SecurityUtils.getCurrentUserId(request), SecurityUtils.getCurrentUsername(request)));
    }

    /** 退出认领：已报数量保留，未报份额释放 */
    @PostMapping("/{id}/unclaim")
    public Result<WorkOrderOperation> unclaim(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(workOrderOperationService.unclaim(
                id, SecurityUtils.getCurrentUserId(request), SecurityUtils.getCurrentUsername(request)));
    }

    /**
     * 扫码报工：直接填本次完成数量。
     * 入参 { quantity, rejectedQuantity, employeeId/employeeName（代报，可空）, idemKey（防重号）, remark }
     */
    @PostMapping("/{id}/report")
    public Result<Map<String, Object>> report(@PathVariable Long id,
                                              @RequestBody Map<String, Object> body,
                                              HttpServletRequest request) {
        return Result.success(workOrderOperationService.reportByWorker(
                id, body,
                SecurityUtils.getCurrentUserId(request), SecurityUtils.getCurrentUsername(request)));
    }

    /** 撤销报工：本人撤自己当班最近一笔；管理员可指定流水ID撤任意一笔 */
    @PostMapping("/{id}/undo-report")
    public Result<Map<String, Object>> undoReport(@PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, Object> body,
                                                  HttpServletRequest request) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        Long flowId = payload.get("flowId") == null || String.valueOf(payload.get("flowId")).isBlank()
                ? null : Long.valueOf(String.valueOf(payload.get("flowId")));
        return Result.success(workOrderOperationService.undoReport(
                id, flowId,
                SecurityUtils.getCurrentUserId(request), SecurityUtils.getCurrentUsername(request)));
    }

    // ---------------- 第 4 期：回退确认 / 判废确认 / 下推补料 ----------------
    // 说明：这三个动作**不改工序状态**（只记数量 + 留痕 / 生成补料单），
    // 所以不走 /{id}/action 的状态机入口，避免被"终态不可操作"的守卫挡住
    // （真实场景：已完工的工序被下道退回，仍需登记回退数并判废、补料）。

    /** 回退确认：下道工序退回本道的数量 */
    @PostMapping("/{id}/rework")
    public Result<WorkOrderOperation> rework(@PathVariable Long id,
                                             @RequestBody Map<String, Object> body,
                                             HttpServletRequest request) {
        return Result.success(workOrderOperationService.confirmRework(
                id, toDecimal(body.get("quantity")), toStr(body.get("remark")),
                SecurityUtils.getCurrentUserId(request), SecurityUtils.getCurrentUsername(request)));
    }

    /** 判废确认：本道师傅判定"回退料中不能用"的数量 */
    @PostMapping("/{id}/scrap")
    public Result<WorkOrderOperation> scrap(@PathVariable Long id,
                                            @RequestBody Map<String, Object> body,
                                            HttpServletRequest request) {
        return Result.success(workOrderOperationService.confirmScrap(
                id, toDecimal(body.get("quantity")), toStr(body.get("remark")),
                SecurityUtils.getCurrentUserId(request), SecurityUtils.getCurrentUsername(request)));
    }

    /** 下推补料：按"尚未下推的判废数量 × 主BOM 子件用量"生成补料领料单（草稿），返回单号 */
    @PostMapping("/{id}/push-supplement")
    public Result<Map<String, Object>> pushSupplement(@PathVariable Long id,
                                                      @RequestBody(required = false) Map<String, Object> body,
                                                      HttpServletRequest request) {
        String billNo = workOrderOperationService.pushSupplement(
                id, body == null ? null : toStr(body.get("remark")),
                SecurityUtils.getCurrentUserId(request), SecurityUtils.getCurrentUsername(request));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("billNo", billNo);
        // 一并回传变更后的工序行，前端无需再发一次查询就能刷新"已下推数 / 补料单号"
        data.put("operation", workOrderOperationService.getById(id));
        return Result.success(data);
    }

    // ---------------- 第 11 期：入库类型工序一键发起入库单 / 删除已取消工序 ----------------

    /**
     * 入库类型工序：一键发起成品入库单（草稿）。
     * 入参 { quantity: 入库数量 }；数量取当前登录态，不接受前端传值。
     * 返回 { billNo: 入库单号, operation: 变更后的工序行 }。
     */
    @PostMapping("/{id}/push-receipt")
    public Result<Map<String, Object>> pushReceipt(@PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, Object> body,
                                                   HttpServletRequest request) {
        return Result.success(workOrderOperationService.pushReceipt(
                id, toDecimal(body == null ? null : body.get("quantity")),
                SecurityUtils.getCurrentUserId(request), SecurityUtils.getCurrentUsername(request)));
    }

    /** 删除一道工序（仅允许「已取消」的行）：用户口径的第二条回程「删掉重新添加」 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workOrderOperationService.deleteOperation(id);
        return Result.success();
    }

    // ---------------- 第 12 期：工序检验（质检模板 + 填质检） ----------------

    /**
     * 取该工序应使用的质检模板：工单工序显式绑定 → 按工序名称绑定 → 按物料绑定 → 都没有返回 null。
     * 工序面板「填质检」弹窗按它渲染检验项表单。
     */
    @GetMapping("/{id}/qc-template")
    public Result<com.erp.qc.entity.QcTemplate> qcTemplate(@PathVariable Long id) {
        return Result.success(workOrderOperationService.resolveQcTemplate(id));
    }

    /**
     * 提交工序检验（仅「完工待检」状态可用）：按质检类型（首检/抽检/全检）自动判定，
     * 合格 → 工序推入「已完工」；不合格 → 按所选处理方式处置后走「检验退回」。
     * 操作人取当前登录态，不接受前端传值。
     */
    @PostMapping("/{id}/qc")
    public Result<Map<String, Object>> submitQc(@PathVariable Long id,
                                                @RequestBody Map<String, Object> body,
                                                HttpServletRequest request) {
        return Result.success(workOrderOperationService.submitInspection(
                id, body,
                SecurityUtils.getCurrentUserId(request),
                SecurityUtils.getCurrentUsername(request)));
    }

    // ---------------- 2026-09-28：工序通知（@下道工序负责人 / 手选员工） ----------------

    /**
     * 工序通知：把一条系统通知发给所选员工（员工档案未绑定登录账号的会被跳过）。
     * 入参 { receiverEmployeeIds: [员工ID...], content: 自定义内容（可空，空则用默认模板） }。
     * 操作人取当前登录态，不接受前端传值。
     */
    @PostMapping("/{id}/notify")
    public Result<Map<String, Object>> notifyOperation(@PathVariable Long id,
                                                       @RequestBody(required = false) Map<String, Object> body,
                                                       HttpServletRequest request) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        return Result.success(workOrderOperationService.notifyOperation(
                id,
                toLongList(payload.get("receiverEmployeeIds")),
                toStr(payload.get("content")),
                SecurityUtils.getCurrentUserId(request),
                SecurityUtils.getCurrentUsername(request)));
    }

    private static java.math.BigDecimal toDecimal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return new java.math.BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 员工ID列表解析：接受数组/单值，非法项忽略 */
    private static List<Long> toLongList(Object v) {
        List<Long> ids = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object o : list) {
                if (o == null || String.valueOf(o).isBlank()) continue;
                try {
                    ids.add(Long.valueOf(String.valueOf(o).trim()));
                } catch (NumberFormatException ignore) {
                    // 非法项直接忽略
                }
            }
        } else if (v != null && !String.valueOf(v).isBlank()) {
            try {
                ids.add(Long.valueOf(String.valueOf(v).trim()));
            } catch (NumberFormatException ignore) {
                // 非法项直接忽略
            }
        }
        return ids;
    }

    private static String toStr(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
