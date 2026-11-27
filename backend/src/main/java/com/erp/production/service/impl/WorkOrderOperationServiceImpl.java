package com.erp.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.basedata.entity.Employee;
import com.erp.basedata.entity.Warehouse;
import com.erp.basedata.mapper.EmployeeMapper;
import com.erp.basedata.mapper.WarehouseMapper;
import com.erp.common.scope.DataScopeResolver;
import com.erp.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.erp.config.BusinessException;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.entity.Routing;
import com.erp.production.entity.RoutingOperation;
import com.erp.production.entity.WorkCenter;
import com.erp.production.entity.WorkOperationLog;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderOperation;
import com.erp.production.entity.WorkOrderOperationWorker;
import com.erp.production.entity.OperationReport;
import com.erp.production.entity.OperationReportFlow;
import com.erp.production.mapper.WorkOrderOperationWorkerMapper;
import com.erp.production.mapper.OperationReportFlowMapper;
import com.erp.production.mapper.OperationReportMapper;
import com.erp.production.entity.WorkOrderProduct;
import com.erp.notification.entity.SysNotification;
import com.erp.notification.service.SysNotificationService;
import com.erp.notification.service.WxSubscribeService;
import com.erp.production.mapper.ProductionReceiptMapper;
import com.erp.production.mapper.RoutingMapper;
import com.erp.production.mapper.RoutingOperationMapper;
import com.erp.production.mapper.WorkCenterMapper;
import com.erp.production.mapper.WorkOperationLogMapper;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.production.mapper.WorkOrderOperationMapper;
import com.erp.production.mapper.WorkOrderProductMapper;
import com.erp.qc.entity.QcRecord;
import com.erp.qc.entity.QcRecordItem;
import com.erp.qc.entity.QcTemplate;
import com.erp.qc.entity.QcTemplateItem;
import com.erp.qc.mapper.QcRecordItemMapper;
import com.erp.qc.mapper.QcRecordMapper;
import com.erp.qc.service.QcTemplateService;
import com.erp.production.service.ProductionSupplementHelper;
import com.erp.production.service.WorkOperationStatus;
import com.erp.production.service.WorkOrderOperationService;
import com.erp.production.service.WorkOrderService;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.mapper.InventoryMapper;
import com.erp.produce.mrp.service.BomExplosionService;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.entity.PurchaseOrderItem;
import com.erp.purchase.mapper.PurchaseOrderItemMapper;
import com.erp.purchase.mapper.PurchaseOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工单工序状态流转 + 操作日志实现。
 *
 * <p><b>为什么集中在这里</b>：改造前工序状态被多处各写一份（报工单审核、页面快速报工），
 * 谁写的、写的什么值都不统一（`PENDING/待开始/进行中/已完成`混用）。本次收口成：
 * 状态取值 {@link WorkOperationStatus} 唯一、迁移经本服务、每次迁移都留 {@code work_operation_log}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderOperationServiceImpl
        extends ServiceImpl<WorkOrderOperationMapper, WorkOrderOperation>
        implements WorkOrderOperationService {

    private final WorkOperationLogMapper logMapper;
    /** 生产工单（下推补料需要工单上的成品物料与仓库） */
    private final WorkOrderMapper workOrderMapper;
    /** 补料领料单生成器（唯一入口：与"报工报废自动补料"共用同一套口径） */
    private final ProductionSupplementHelper supplementHelper;
    /** 工作中心档案（看板按工作中心排线时补名称） */
    private final WorkCenterMapper workCenterMapper;
    /** 统一数据权限解析器：看板纳入的工单与工单列表同一口径（按角色 data_scope 过滤） */
    private final DataScopeResolver dataScopeResolver;
    /** 员工档案（个人工作表：登录账号 → 员工，按 assignee_id 取"我是负责人的工序"） */
    private final EmployeeMapper employeeMapper;

    /** 成品明细行（第10期：看板按「工单+成品」拆线、取该成品名称） */
    private final WorkOrderProductMapper workOrderProductMapper;

    /** 生产工单服务（第11期：入库类型工序一键发起入库单时复用工单级「下推入库单」方法） */
    private final WorkOrderService workOrderService;

    /** 生产入库单 Mapper（第11期：按来源工序行汇总已下推入库量，防重复） */
    private final ProductionReceiptMapper productionReceiptMapper;

    /** 【第12期】质检模板服务（解析该工序应使用的模板；模板在「基础资料 → 质检模板」维护） */
    private final QcTemplateService qcTemplateService;
    /** 【第12期】工序检验记录 Mapper */
    private final QcRecordMapper qcRecordMapper;
    /** 【第12期】检验记录实测明细 Mapper */
    private final QcRecordItemMapper qcRecordItemMapper;

    /** 【2026-09-28】工艺路线 Mapper：读取「自动通知下道工序负责人 / 按顺序执行」两个开关 */
    private final RoutingMapper routingMapper;

    /** 【2026-10-09】工序明细 Mapper：读取「本道工序」的「谁可以改负责人」档位（工序级，非路线级） */
    private final RoutingOperationMapper routingOperationMapper;

    /** 【2026-09-28】系统通知服务：工序通知复用其统一插入/未读去重口径（禁止另写 INSERT） */
    private final SysNotificationService sysNotificationService;
    private final WxSubscribeService wxSubscribeService;

    /** 【第 15 期】BOM 展开（齐套需求）：复用 MRP 现成能力，不另写一套 BOM 展开 */
    private final BomExplosionService bomExplosionService;
    /** 【第 15 期】现有库存（齐套"已到"口径：库存本身就是"实际入库 − 实际出库"的净结果） */
    private final InventoryMapper inventoryMapper;
    /** 【2026-10-09】仓库档案：齐套"已到"只统计「参与MRP」的仓库（不合格品仓/废料仓里的货不能投产） */
    private final WarehouseMapper warehouseMapper;
    /** 【第 15 期】采购订单明细（齐套"预计"口径：仅取未到货交货日期，不参与齐套判定） */
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    /** 【第 15 期】采购订单主表（过滤已作废/草稿的预计到货） */
    private final PurchaseOrderMapper purchaseOrderMapper;

    /** 【2026-10-25】工序共同负责人（扫码认领 / 退出 / 参与人清单） */
    private final WorkOrderOperationWorkerMapper operationWorkerMapper;
    /** 【2026-10-25】工序报工流水（按人记账：工序已报与个人已报都从这里汇总） */
    private final OperationReportFlowMapper operationReportFlowMapper;
    /** 【2026-10-25】工序汇报单：扫码报工后台留存一条已生效记录，保证工资/成本取数不断（界面不展示） */
    private final OperationReportMapper operationReportMapper;

    /** 【2026-11-07】物料档案：读取「日产能」，APS 占用天数取「工时产能」与「物料产能」的较大值 */
    private final com.erp.masterdata.mapper.MaterialMapper materialMapper;

    /** 【2026-11-25】工序自定义字段：随「保存」/任一动作/报工一并写入通用自定义字段值表 */
    private final com.erp.customfield.service.CustomFieldService customFieldService;

    /** 【2026-11-26】工艺路线工序字段定义：按工序稳定标识取该工序自己的字段 */
    private final com.erp.production.mapper.RoutingOperationFieldMapper routingOperationFieldMapper;

    @Override
    public List<WorkOrderOperation> listByOrder(Long orderId) {
        List<WorkOrderOperation> ops = baseMapper.selectByOrderId(orderId);
        if (ops == null || ops.isEmpty()) return ops;
        // 工序操作面板有两个入口（车间看板 / 工单编辑页），必须看到同一份信息：
        // 看板由 board() 填充展示字段，这里也要填一遍 —— 否则从工单编辑页点「操作」打开面板时，
        // 工作中心、成品、父工序、子工序、已入库量等全是空白，同一块面板两个样子。
        Map<Long, WorkOrder> orderMap = new LinkedHashMap<>();
        WorkOrder order = workOrderMapper.selectById(orderId);
        if (order != null) orderMap.put(order.getOrderId(), order);
        fillDisplayFields(ops, ops, orderMap);
        // 【2026-10-25】共同负责人与扫码认领开关：列表直接显示「负责人：张三、李四」
        for (WorkOrderOperation op : ops) {
            op.setWorkers(buildWorkers(op));
            op.setAllowScanClaim(scanClaimAllowed(op) ? "是" : "否");
        }
        // 【2026-11-26】每道工序自己的字段定义（工单编辑页的工序弹窗按此决定显示哪些字段）
        fillOperationFieldDefsBatch(ops);
        return ops;
    }

    /** 批量回填「每道工序自己的字段定义」（列表用；不查值，值由前端按行批量取） */
    private void fillOperationFieldDefsBatch(List<WorkOrderOperation> ops) {
        if (ops == null || ops.isEmpty()) return;
        Set<Long> routingIds = new LinkedHashSet<>();
        for (WorkOrderOperation op : ops) {
            if (op.getRoutingId() != null && op.getRoutingOperationKey() != null
                    && !op.getRoutingOperationKey().isBlank()) {
                routingIds.add(op.getRoutingId());
            }
        }
        if (routingIds.isEmpty()) return;
        List<com.erp.production.entity.RoutingOperationField> all =
                routingOperationFieldMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.erp.production.entity.RoutingOperationField>()
                                .in("routing_id", routingIds)
                                .orderByAsc("sort_no"));
        Map<String, List<com.erp.production.entity.RoutingOperationField>> byKey = new HashMap<>();
        for (com.erp.production.entity.RoutingOperationField f : all) {
            byKey.computeIfAbsent(f.getRoutingId() + "|" + f.getOperationKey(), k -> new ArrayList<>()).add(f);
        }
        Map<Long, String> fieldKeyById = new HashMap<>();
        for (com.erp.customfield.entity.CustomField cf
                : customFieldService.listEnabledByEntity("work_order_operation")) {
            fieldKeyById.put(cf.getFieldId(), cf.getFieldKey());
        }
        for (WorkOrderOperation op : ops) {
            if (op.getRoutingId() == null || op.getRoutingOperationKey() == null) continue;
            List<com.erp.production.entity.RoutingOperationField> defs =
                    byKey.get(op.getRoutingId() + "|" + op.getRoutingOperationKey());
            if (defs == null || defs.isEmpty()) continue;
            List<Map<String, Object>> views = new ArrayList<>();
            for (com.erp.production.entity.RoutingOperationField d : defs) {
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("id", d.getId());
                v.put("fieldLabel", d.getFieldLabel());
                v.put("fieldType", d.getFieldType());
                v.put("fieldOptions", d.getFieldOptions());
                v.put("isRequired", d.getIsRequired());
                v.put("sortOrder", d.getSortNo());
                v.put("precisionVal", 2);
                v.put("fieldKey", d.getCustomFieldId() == null ? ("rof_" + d.getId())
                        : fieldKeyById.getOrDefault(d.getCustomFieldId(), "rof_" + d.getId()));
                views.add(v);
            }
            op.setOperationFieldDefs(views);
        }
    }

    /**
     * 填充「非库字段」展示信息（看板与工序操作面板**共用同一处**，保证两个入口看到同一份信息）：
     * 工作中心名称、成品名称与规格、成品物料ID、父工序名称、子工序数/已完工数/汇总进度、
     * 已入库量（已作废不计）、超期天数。
     *
     * @param display  需要填充的行
     * @param scope    同范围内的全部工序 —— 父子关系只在本范围内解析（工单侧天然限同一工单/同一成品行）
     * @param orderMap 相关工单（成品名称/物料ID 的兜底来源）
     */
    private void fillDisplayFields(List<WorkOrderOperation> display, List<WorkOrderOperation> scope,
                                   Map<Long, WorkOrder> orderMap) {
        if (display == null || display.isEmpty()) return;
        if (scope == null || scope.isEmpty()) scope = display;

        // 父子关系（只在本范围内解析）：父工序已删除 / 跨范围引用 → 一律视为顶层
        Map<Long, WorkOrderOperation> byId = new HashMap<>();
        for (WorkOrderOperation op : scope) byId.put(op.getId(), op);
        Map<Long, List<WorkOrderOperation>> childrenOf = new HashMap<>();
        for (WorkOrderOperation op : scope) {
            if (op.getParentRowId() != null && byId.containsKey(op.getParentRowId())) {
                childrenOf.computeIfAbsent(op.getParentRowId(), k -> new ArrayList<>()).add(op);
            }
        }
        // 父节点的子项汇总：只认叶子后代，按计划数量加权（Σ叶子产出 / Σ叶子计划量 × 100）
        for (WorkOrderOperation op : scope) {
            List<WorkOrderOperation> kids = childrenOf.get(op.getId());
            if (kids == null || kids.isEmpty()) continue;
            op.setChildCount(kids.size());
            op.setChildFinishedCount((int) kids.stream()
                    .filter(k -> WorkOperationStatus.FINISHED.equals(k.getOperationStatus())).count());
            BigDecimal planSum = BigDecimal.ZERO;
            BigDecimal outSum = BigDecimal.ZERO;
            for (WorkOrderOperation leaf : collectLeafDescendants(op.getId(), childrenOf)) {
                planSum = planSum.add(nvl(leaf.getPlanQuantity()));
                outSum = outSum.add(nvl(leaf.getOutputQuantity()));
            }
            op.setChildProgress(planSum.compareTo(BigDecimal.ZERO) > 0
                    ? outSum.multiply(BigDecimal.valueOf(100)).divide(planSum, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
        }

        // 工作中心名称（工序表无名称列，join 档案取值；已停用工作中心也照常显示）
        Set<Long> wcIds = new HashSet<>();
        for (WorkOrderOperation op : display) {
            if (op.getWorkCenterId() != null) wcIds.add(op.getWorkCenterId());
        }
        Map<Long, String> wcNameMap = new HashMap<>();
        if (!wcIds.isEmpty()) {
            for (WorkCenter wc : workCenterMapper.selectBatchIds(wcIds)) {
                wcNameMap.put(wc.getWorkCenterId(), wc.getWorkCenterName());
            }
        }

        // 成品明细行（一张工单多个商品：成品名称 / 规格 / 物料ID 都取自所属成品行）
        Map<Long, WorkOrderProduct> productMap = new HashMap<>();
        Set<Long> orderIds = new HashSet<>();
        for (WorkOrderOperation op : display) {
            if (op.getOrderId() != null) orderIds.add(op.getOrderId());
        }
        if (!orderIds.isEmpty()) {
            List<WorkOrderProduct> products = workOrderProductMapper.selectList(
                    new QueryWrapper<WorkOrderProduct>().in("order_id", orderIds));
            if (products != null) {
                for (WorkOrderProduct p : products) productMap.put(p.getId(), p);
            }
        }

        // 已入库量：入库类型工序 = 按来源工序行汇总的入库单数量（一次批量查完；已作废不计）
        Set<Long> displayIds = new HashSet<>();
        for (WorkOrderOperation op : display) displayIds.add(op.getId());
        if (!displayIds.isEmpty()) {
            for (Map<String, Object> row : productionReceiptMapper.sumReceivedByOperations(displayIds)) {
                Long id = row.get("operationId") == null
                        ? null : Long.valueOf(String.valueOf(row.get("operationId")));
                WorkOrderOperation op = id == null ? null : byId.get(id);
                if (op != null) op.setReceivedQty(toDecimal(row.get("qty")));
            }
        }

        LocalDate today = LocalDate.now();
        for (WorkOrderOperation op : display) {
            op.setOperationRowId(op.getId());
            // 【第14期】派工状态判定（看板输出字段；前端只渲染、不自算，避免两端口径漂移）
            op.setUnassigned(isUnassigned(op));
            op.setWorkCenterName(op.getWorkCenterId() == null ? null : wcNameMap.get(op.getWorkCenterId()));

            WorkOrderProduct p = op.getOrderProductId() == null ? null : productMap.get(op.getOrderProductId());
            WorkOrder order = orderMap == null ? null : orderMap.get(op.getOrderId());
            op.setMaterialName(p != null && p.getMaterialName() != null
                    ? p.getMaterialName() : (order == null ? null : order.getMaterialName()));
            op.setSpecification(p == null ? null : p.getSpecification());
            // 【第 2 版】物料编码（优先成品明细行，回落整单物料）：进度跟踪表格「物料编码」列用
            op.setMaterialCode(p != null && p.getMaterialCode() != null
                    ? p.getMaterialCode() : (order == null ? null : order.getMaterialCode()));
            // 成品物料ID：优先取所属成品明细行，回落整单物料（站点设置按它取「物料自定义字段」）
            op.setMaterialId(p != null && p.getMaterialId() != null ? p.getMaterialId()
                    : (order == null ? null : order.getMaterialId()));
            // 生产工单附件：只读带入每道工序的报工面板（图片可预览、文件可下载）
            op.setBillAttachments(order == null ? null : order.getAttachmentUrls());

            WorkOrderOperation parent = op.getParentRowId() == null ? null : byId.get(op.getParentRowId());
            op.setParentOperationName(parent == null ? null : parent.getOperationName());

            // 超期天数：计划完工日期已过、且未完工/未取消 → 到今天的自然日差
            String st = op.getOperationStatus();
            if (op.getPlanEndDate() != null
                    && !WorkOperationStatus.FINISHED.equals(st) && !WorkOperationStatus.CANCELLED.equals(st)) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(op.getPlanEndDate(), today);
                if (days > 0) op.setOverdueDays((int) days);
            }
        }
    }

    /** 已生效工单状态值（与 production_order.status 口径一致） */
    private static final String STATUS_RELEASED = "已生效";

    @Override
    public List<Map<String, Object>> myOpenOperations(Long userId, Integer days) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (userId == null) return result;

        // 1) 登录账号 → 员工档案（历史上可能有重复绑定，全部纳入；没有档案则返回空，不报错）
        List<Employee> employees = employeeMapper.selectList(
                new QueryWrapper<Employee>().eq("user_id", userId));
        if (employees.isEmpty()) return result;
        Set<Long> assigneeIds = new HashSet<>();
        for (Employee e : employees) {
            if (e.getEmployeeId() != null) assigneeIds.add(e.getEmployeeId());
        }
        if (assigneeIds.isEmpty()) return result;

        // 2) 我是负责人 且 工序未完工/未取消（历史空状态视为未开始，保留）
        QueryWrapper<WorkOrderOperation> opQw = new QueryWrapper<>();
        opQw.in("assignee_id", assigneeIds);
        opQw.and(w -> w.isNull("operation_status")
                .or().notIn("operation_status", WorkOperationStatus.FINISHED, WorkOperationStatus.CANCELLED));
        // 3) 只取「计划完工日期 ≤ 今天 + days 天」（默认 7；空值也纳入，避免漏掉未排期的工序）
        int ahead = (days == null || days < 0) ? 7 : days;
        LocalDate limit = LocalDate.now().plusDays(ahead);
        opQw.and(w -> w.isNull("plan_end_date").or().le("plan_end_date", limit));
        List<WorkOrderOperation> ops = list(opQw);
        if (ops.isEmpty()) return result;

        // 4) 只保留所属工单「已生效」的工序
        Set<Long> orderIds = new HashSet<>();
        for (WorkOrderOperation op : ops) {
            if (op.getOrderId() != null) orderIds.add(op.getOrderId());
        }
        if (orderIds.isEmpty()) return result;
        List<WorkOrder> orders = workOrderMapper.selectList(new QueryWrapper<WorkOrder>()
                .in("order_id", orderIds).eq("status", STATUS_RELEASED));
        if (orders.isEmpty()) return result;
        Map<Long, WorkOrder> orderMap = new HashMap<>();
        for (WorkOrder o : orders) orderMap.put(o.getOrderId(), o);
        ops.removeIf(op -> !orderMap.containsKey(op.getOrderId()));
        if (ops.isEmpty()) return result;

        // 5) 工作中心名称（工序表无名称列，join 档案取值；已停用工作中心也照常显示）
        Set<Long> wcIds = new HashSet<>();
        for (WorkOrderOperation op : ops) {
            if (op.getWorkCenterId() != null) wcIds.add(op.getWorkCenterId());
        }
        Map<Long, String> wcNameMap = new HashMap<>();
        if (!wcIds.isEmpty()) {
            for (WorkCenter wc : workCenterMapper.selectBatchIds(wcIds)) {
                wcNameMap.put(wc.getWorkCenterId(), wc.getWorkCenterName());
            }
        }

        // 6) 排序：计划完工日期升序（空值排最后）→ 工单号
        ops.sort(Comparator
                .<WorkOrderOperation, LocalDate>comparing(WorkOrderOperation::getPlanEndDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(op -> {
                    WorkOrder o = orderMap.get(op.getOrderId());
                    String no = o != null && o.getOrderNo() != null ? o.getOrderNo() : op.getOrderNo();
                    return no == null ? "" : no;
                }));

        // 7) 组装返回字段（前端卡片展示用；不含工序状态写入，纯只读）
        LocalDate today = LocalDate.now();
        for (WorkOrderOperation op : ops) {
            WorkOrder order = orderMap.get(op.getOrderId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("operationRowId", op.getId());
            row.put("orderId", op.getOrderId());
            row.put("orderNo", order == null ? op.getOrderNo() : order.getOrderNo());
            row.put("productName", order == null ? null : order.getMaterialName());
            row.put("lineNo", op.getLineNo());
            row.put("operationName", op.getOperationName());
            row.put("workCenterName", op.getWorkCenterId() == null ? null : wcNameMap.get(op.getWorkCenterId()));
            row.put("operationStatus", op.getOperationStatus());
            row.put("planEndDate", op.getPlanEndDate() == null ? null : op.getPlanEndDate().toString());
            row.put("planQuantity", op.getPlanQuantity());
            row.put("reportedQuantity", op.getReportedQuantity());
            row.put("overdue", op.getPlanEndDate() != null && op.getPlanEndDate().isBefore(today));
            result.add(row);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> board(String view, Long workCenterId, boolean includeFinished,
                                           String assigneeFilter,
                                           String keyword, String status, String planEndFrom, String planEndTo,
                                           String sortField, String sortOrder) {
        // 1) 只纳入已生效工单（与工单列表同一数据权限口径）
        QueryWrapper<WorkOrder> orderQw = new QueryWrapper<WorkOrder>().eq("status", STATUS_RELEASED);
        dataScopeResolver.applyIfLimited(orderQw, null, "created_by");
        List<WorkOrder> orders = workOrderMapper.selectList(orderQw);
        if (orders.isEmpty()) return new ArrayList<>();
        Map<Long, WorkOrder> orderMap = new LinkedHashMap<>();
        for (WorkOrder o : orders) orderMap.put(o.getOrderId(), o);

        // 2) 这些工单下的**全部**工序（父子汇总需要全量：子工序被终态开关滤掉时，父节点的汇总环仍要算得准）
        QueryWrapper<WorkOrderOperation> opQw = new QueryWrapper<>();
        opQw.in("order_id", orderMap.keySet());
        List<WorkOrderOperation> allOps = list(opQw);
        if (allOps.isEmpty()) return new ArrayList<>();

        // 2.1) 展示集合：按工作中心筛选 + 默认排除终态（历史空状态行视为未开始，保留）+ 派工状态筛选
        //      守则：先按终态开关过滤，再按派工状态过滤，两个条件叠加、互不覆盖。
        boolean onlyUnassigned = "unassigned".equalsIgnoreCase(assigneeFilter);
        boolean onlyAssigned = "assigned".equalsIgnoreCase(assigneeFilter);
        List<WorkOrderOperation> ops = new ArrayList<>();
        for (WorkOrderOperation op : allOps) {
            if (workCenterId != null && !workCenterId.equals(op.getWorkCenterId())) continue;
            String st = op.getOperationStatus();
            if (!includeFinished && (WorkOperationStatus.FINISHED.equals(st)
                    || WorkOperationStatus.CANCELLED.equals(st))) continue;
            boolean unassigned = isUnassigned(op);
            if (onlyUnassigned && !unassigned) continue;
            if (onlyAssigned && unassigned) continue;
            ops.add(op);
        }
        if (ops.isEmpty()) return new ArrayList<>();

        // 2.2)【第 13 期】填充展示信息（父子关系 / 子项汇总 / 工作中心名称 / 成品与规格 /
        //      已入库量 / 超期天数）—— 与工序操作面板共用同一处实现，保证两个入口看到同一份信息。
        //      注意：汇总的数据源传 allOps（全量），否则已完工的子工序被终态开关滤掉会让父节点汇总低报。
        fillDisplayFields(ops, allOps, orderMap);

        // 2.3)【第 2 版 2026-10-07】进度跟踪表格筛选（放在 display 字段填充之后：物料编码/物料名称此时才可用）
        //      与「含已完工」「派工筛选」叠加，互不覆盖；筛选后某条线路无工序 → 该线路整条不出现。
        if (hasText(keyword) || hasText(status) || hasText(planEndFrom) || hasText(planEndTo)) {
            ops.removeIf(op -> !boardKeep(op, keyword, status, planEndFrom, planEndTo));
            if (ops.isEmpty()) return new ArrayList<>();
        }

        // 4) 组内排序：计划完工日期（空值排最后） → 工序号
        ops.sort(Comparator
                .<WorkOrderOperation, LocalDate>comparing(WorkOrderOperation::getPlanEndDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(WorkOrderOperation::getLineNo, Comparator.nullsLast(Comparator.naturalOrder())));

        // 5) 分组（第10期：按工单视图下，一张工单有多个商品时 → 按「工单 + 成品行」各出一条线路）
        //    第14期：workcenter 视图线 key 由「工作中心」改为「工作中心 + 工单 + 成品行」（同一工作中心下
        //    不同工单各自成线，不再混排），线名 = 「工作中心名 · 工单号」；order 视图口径保持不变。
        //    成品名称与工作中心名称已在 fillDisplayFields 里填好，这里直接取，避免两处口径漂移。
        boolean byOrder = "order".equalsIgnoreCase(view);
        Map<String, Map<String, Object>> groups = new LinkedHashMap<>();
        for (WorkOrderOperation op : ops) {
            WorkOrder order = orderMap.get(op.getOrderId());
            String productName = op.getMaterialName();
            String orderNo = order != null ? order.getOrderNo() : op.getOrderNo();
            String orderPart = op.getOrderId() == null ? "0" : String.valueOf(op.getOrderId());
            String prodPart = op.getOrderProductId() == null ? "0" : String.valueOf(op.getOrderProductId());

            String key = byOrder
                    ? orderPart + "-" + prodPart
                    : (op.getWorkCenterId() == null ? "0" : String.valueOf(op.getWorkCenterId()))
                        + "-" + orderPart + "-" + prodPart;

            Map<String, Object> group = groups.get(key);
            if (group == null) {
                String wcName = op.getWorkCenterId() == null
                        ? "未分配工作中心"
                        : (op.getWorkCenterName() == null ? "未命名工作中心" : op.getWorkCenterName());
                String name;
                if (byOrder) {
                    name = (orderNo == null ? "" : orderNo)
                            + (productName == null || productName.isBlank() ? "" : " · " + productName);
                } else {
                    // 线名 = 「工作中心名 · 工单号」；工单号取不到时只留左侧部分
                    name = (orderNo == null || orderNo.isBlank()) ? wcName : wcName + " · " + orderNo;
                }
                group = new LinkedHashMap<>();
                group.put("key", key);
                group.put("name", name);
                group.put("productName", byOrder ? productName : null);
                // 第14期新增（供前端做线路标签与工单号跳转）：只增不删，老字段全部保留
                group.put("orderId", op.getOrderId());
                group.put("orderNo", orderNo);
                // 【第 2 版】进度跟踪表格列：物料编码 / 物料名称 / 规格（只增字段，老前端不受影响）
                group.put("materialCode", op.getMaterialCode());
                group.put("materialName", op.getMaterialName());
                group.put("specification", op.getSpecification());
                group.put("workCenterId", op.getWorkCenterId());
                group.put("workCenterName", op.getWorkCenterName());
                group.put("ops", new ArrayList<WorkOrderOperation>());
                groups.put(key, group);
            }
            @SuppressWarnings("unchecked")
            List<WorkOrderOperation> rows = (List<WorkOrderOperation>) group.get("ops");
            rows.add(op);
        }

        // 6) 编排线路顺序：工作中心按名称；工单按"最早计划完工日期（空值排最后）→工单号"
        List<Map<String, Object>> result = new ArrayList<>(groups.values());
        if (byOrder) {
            result.sort(Comparator
                    .<Map<String, Object>, LocalDate>comparing(
                            WorkOrderOperationServiceImpl::firstPlanEnd,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(g -> String.valueOf(g.get("name"))));
        } else {
            result.sort(Comparator.<Map<String, Object>, String>comparing(g -> String.valueOf(g.get("name"))));
        }
        // 【第 2 版】进度跟踪表格列头排序（⇅）；不传 sortField 时保持上面这套默认顺序（行为与改造前一致）
        applyBoardSort(result, sortField, sortOrder);
        return result;
    }

    // -------------------- 【第 2 版】进度跟踪表格：筛选与排序辅助 --------------------

    private static boolean hasText(String s) { return s != null && !s.isBlank(); }

    private static String nvlStr(String s) { return s == null ? "" : s; }

    private static LocalDate parseBoardDate(String s) {
        try {
            return LocalDate.parse(s.trim().substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    /** 该工序行是否通过「关键字（工单号/物料编码/物料名称）+ 工序状态 + 计划完工区间」筛选 */
    private static boolean boardKeep(WorkOrderOperation op, String keyword, String status,
                                     String planEndFrom, String planEndTo) {
        if (hasText(status) && !status.equals(op.getOperationStatus())) return false;
        if (hasText(keyword)) {
            String hay = (nvlStr(op.getOrderNo()) + " " + nvlStr(op.getMaterialCode()) + " "
                    + nvlStr(op.getMaterialName())).toLowerCase();
            if (!hay.contains(keyword.trim().toLowerCase())) return false;
        }
        LocalDate end = op.getPlanEndDate();
        if (hasText(planEndFrom)) {
            LocalDate f = parseBoardDate(planEndFrom);
            if (f != null && (end == null || end.isBefore(f))) return false;
        }
        if (hasText(planEndTo)) {
            LocalDate t = parseBoardDate(planEndTo);
            if (t != null && (end == null || end.isAfter(t))) return false;
        }
        return true;
    }

    /** 线路级状态排名：已完工=2 / 执行中=1 / 未开始=0（供「状态」列排序，前端不自算） */
    private static int boardLineStatusRank(Map<String, Object> g) {
        Object opsObj = g.get("ops");
        if (!(opsObj instanceof java.util.List<?> list) || list.isEmpty()) return 0;
        boolean allDone = true;
        boolean anyRun = false;
        for (Object o : list) {
            if (!(o instanceof WorkOrderOperation op)) continue;
            String st = op.getOperationStatus();
            if (WorkOperationStatus.CANCELLED.equals(st)) continue;
            if (!WorkOperationStatus.FINISHED.equals(st)) allDone = false;
            if (WorkOperationStatus.STARTED.equals(st) || WorkOperationStatus.RUNNING.equals(st)) anyRun = true;
        }
        if (allDone) return 2;
        return anyRun ? 1 : 0;
    }

    /** 列头排序：orderNo / materialCode / materialName / status / planEndDate（默认升序，sortOrder=desc 时倒序） */
    private static void applyBoardSort(List<Map<String, Object>> result, String sortField, String sortOrder) {
        if (sortField == null || sortField.isBlank() || result.isEmpty()) return;
        boolean desc = "desc".equalsIgnoreCase(sortOrder);
        Comparator<Map<String, Object>> cmp;
        switch (sortField) {
            case "orderNo" -> cmp = Comparator.comparing(g -> nvlStr(String.valueOf(g.get("orderNo"))));
            case "materialCode" -> cmp = Comparator.comparing(g -> nvlStr((String) g.get("materialCode")));
            case "materialName" -> cmp = Comparator.comparing(g -> nvlStr((String) g.get("materialName")));
            case "status" -> cmp = Comparator.comparingInt(WorkOrderOperationServiceImpl::boardLineStatusRank);
            case "planEndDate" -> cmp = Comparator.comparing(
                    WorkOrderOperationServiceImpl::firstPlanEnd, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> {
                return;
            }
        }
        if (desc) cmp = cmp.reversed();
        result.sort(cmp);
    }

    // ================= 【第 15 期】APS 排程看板（泳道 + 负载热力 + 物料齐套倒排 + 产能排队） =================

    /** 允许的日期跨度（写死三档，与页面工具条一致；非三档一律回落 7） */
    private static final List<Integer> APS_ALLOWED_DAYS = List.of(7, 14, 30);
    /** 效率以"百分数"存库（前端标签「效率(%)」、默认 100）；>2 视为百分数换算为倍率，否则按倍率原样用 */
    private static final BigDecimal APS_EFF_PERCENT_THRESHOLD = new BigDecimal("2");
    private static final BigDecimal APS_HUNDRED = new BigDecimal("100");

    @Override
    public Map<String, Object> scheduleBoard(int days, Long workCenterId, boolean includeFinished, String assigneeFilter) {
        int span = APS_ALLOWED_DAYS.contains(days) ? days : 7;
        LocalDate rangeStart = LocalDate.now();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rangeStart", rangeStart.toString());
        result.put("days", span);

        // 1) 已生效工单（与 /board 同一数据权限口径）
        QueryWrapper<WorkOrder> orderQw = new QueryWrapper<WorkOrder>().eq("status", STATUS_RELEASED);
        dataScopeResolver.applyIfLimited(orderQw, null, "created_by");
        List<WorkOrder> orders = workOrderMapper.selectList(orderQw);
        Map<Long, WorkOrder> orderMap = new LinkedHashMap<>();
        for (WorkOrder o : orders) orderMap.put(o.getOrderId(), o);

        // 2) 这些工单的全部工序（父子汇总/齐套都要全量；展示集合再按开关过滤）
        List<WorkOrderOperation> allOps = orders.isEmpty() ? new ArrayList<>()
                : list(new QueryWrapper<WorkOrderOperation>().in("order_id", orderMap.keySet()));

        // 2.1) 展示集合：终态开关 + 工作中心 + 派工状态（三个条件叠加，互不覆盖）
        boolean onlyUnassigned = "unassigned".equalsIgnoreCase(assigneeFilter);
        boolean onlyAssigned = "assigned".equalsIgnoreCase(assigneeFilter);
        List<WorkOrderOperation> ops = new ArrayList<>();
        for (WorkOrderOperation op : allOps) {
            if (workCenterId != null && !workCenterId.equals(op.getWorkCenterId())) continue;
            String st = op.getOperationStatus();
            if (!includeFinished && (WorkOperationStatus.FINISHED.equals(st) || WorkOperationStatus.CANCELLED.equals(st))) continue;
            boolean un = isUnassigned(op);
            if (onlyUnassigned && !un) continue;
            if (onlyAssigned && un) continue;
            ops.add(op);
        }

        if (ops.isEmpty()) {
            return apsEmpty(result);
        }

        // 3) 工作中心档案（日产能/单位/效率）
        Set<Long> wcIdSet = new LinkedHashSet<>();
        for (WorkOrderOperation op : ops) if (op.getWorkCenterId() != null) wcIdSet.add(op.getWorkCenterId());
        Map<Long, WorkCenter> wcMap = new HashMap<>();
        if (!wcIdSet.isEmpty()) {
            for (WorkCenter wc : workCenterMapper.selectBatchIds(wcIdSet)) wcMap.put(wc.getWorkCenterId(), wc);
        }

        // 4) 成品明细行（齐套按成品行的物料与数量；老单据无成品行则回落整单）
        Set<Long> orderIdSet = new LinkedHashSet<>();
        for (WorkOrderOperation op : ops) if (op.getOrderId() != null) orderIdSet.add(op.getOrderId());
        Map<Long, WorkOrderProduct> productMap = new HashMap<>();
        if (!orderIdSet.isEmpty()) {
            List<WorkOrderProduct> products = workOrderProductMapper.selectList(
                    new QueryWrapper<WorkOrderProduct>().in("order_id", orderIdSet));
            if (products != null) for (WorkOrderProduct p : products) productMap.put(p.getId(), p);
        }
        // 4.1) 物料日产能（2026-11-07）：按成品行/整单物料批量取；0 或空 = 不限制（升级前行为不变）
        Set<Long> matIdSet = new LinkedHashSet<>();
        for (WorkOrderProduct p : productMap.values()) {
            if (p.getMaterialId() != null) matIdSet.add(p.getMaterialId());
        }
        for (WorkOrder o : orderMap.values()) {
            if (o.getMaterialId() != null) matIdSet.add(o.getMaterialId());
        }
        Map<Long, BigDecimal> matCapMap = new HashMap<>();
        if (!matIdSet.isEmpty()) {
            for (com.erp.masterdata.entity.Material m : materialMapper.selectBatchIds(matIdSet)) {
                if (m.getDailyCapacity() != null && m.getDailyCapacity().compareTo(BigDecimal.ZERO) > 0) {
                    matCapMap.put(m.getMaterialId(), m.getDailyCapacity());
                }
            }
        }

        // 5) 齐套：按「工单 + 成品行」缓存（同工单多道工序只算一次）
        //    2026-10-09：齐套「已到」只统计「参与MRP」的仓库（不合格品仓/废料仓的货不能投产），整块看板只查一次仓库清单
        Set<Long> mrpWarehouseIds = mrpAvailableWarehouseIds();
        Map<String, Map<String, Object>> kittingCache = new HashMap<>();
        for (WorkOrderOperation op : ops) {
            String k = op.getOrderId() + "#" + op.getOrderProductId();
            if (!kittingCache.containsKey(k)) {
                WorkOrderProduct p = op.getOrderProductId() == null ? null : productMap.get(op.getOrderProductId());
                kittingCache.put(k, computeKitting(orderMap.get(op.getOrderId()), p, mrpWarehouseIds));
            }
        }

        // 5.5)【工时体系 2026-10-07】建议效率系数（按工作中心，历史「实际÷计划」中位数；样本<5 不给建议）
        Map<Long, Map<String, Object>> efficiencySuggest = apsSuggestedEfficiency();

        // 6) 工序序号（同一工单 + 成品行内按行号）
        Map<String, List<WorkOrderOperation>> seqGroups = new HashMap<>();
        for (WorkOrderOperation op : allOps) {
            seqGroups.computeIfAbsent(op.getOrderId() + "#" + op.getOrderProductId(), x -> new ArrayList<>()).add(op);
        }
        Map<Long, Integer> seqIndex = new HashMap<>();
        Map<Long, Integer> seqTotal = new HashMap<>();
        for (List<WorkOrderOperation> g : seqGroups.values()) {
            g.sort(Comparator.comparing(WorkOrderOperation::getLineNo, Comparator.nullsLast(Comparator.naturalOrder())));
            int i = 0;
            for (WorkOrderOperation op : g) {
                i++;
                seqIndex.put(op.getId(), i);
                seqTotal.put(op.getId(), g.size());
            }
        }

        // 7) 分泳道（一条泳道 = 一个工作中心；无工作中心单独成道）
        Map<Long, List<WorkOrderOperation>> laneOps = new LinkedHashMap<>();
        for (WorkOrderOperation op : ops) laneOps.computeIfAbsent(op.getWorkCenterId(), x -> new ArrayList<>()).add(op);

        List<Map<String, Object>> lanes = new ArrayList<>();
        List<Map<String, Object>> alerts = new ArrayList<>();
        int unassignedCount = 0;
        int overloadedCenters = 0;
        BigDecimal loadSum = BigDecimal.ZERO;
        int loadLaneCount = 0;

        for (Map.Entry<Long, List<WorkOrderOperation>> entry : laneOps.entrySet()) {
            Long wid = entry.getKey();
            WorkCenter wc = wid == null ? null : wcMap.get(wid);
            BigDecimal cap = wc == null ? null : wc.getCapacityPerDay();
            boolean capacityMissing = cap == null || cap.compareTo(BigDecimal.ZERO) <= 0;
            BigDecimal eff = apsNormalizeEfficiency(wc);
            BigDecimal capEff = capacityMissing ? null : cap.multiply(eff);

            // 排队顺序：交期先后（工单计划完工日期，空值排最后）→ 工序计划完工 → 行号
            List<WorkOrderOperation> laneList = entry.getValue();
            laneList.sort(Comparator
                    .comparing((WorkOrderOperation op) -> apsOrderPlanEnd(op, orderMap),
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(WorkOrderOperation::getPlanEndDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(WorkOrderOperation::getLineNo, Comparator.nullsLast(Comparator.naturalOrder())));

            // 第一遍：算占用天数与每日占用，累计逐日负载
            Map<Integer, BigDecimal> dayHours = new LinkedHashMap<>();
            List<Object[]> rows = new ArrayList<>();
            for (WorkOrderOperation op : laneList) {
                BigDecimal hours = nvl(op.getSetupHours()).add(nvl(op.getProcessHours()));
                // 2026-11-07：占用天数 = max(工时产能占用, 物料日产能占用)；物料日产能 0/空 = 不限制
                BigDecimal matCap = apsMaterialCapacity(op, productMap, orderMap, matCapMap);
                int occupancy = Math.max(apsOccupancyDays(hours, capEff, capacityMissing),
                        apsMaterialOccupancyDays(nvl(op.getPlanQuantity()), matCap));
                LocalDate start = apsResolveStart(op, occupancy);
                LocalDate end = start == null ? null : start.plusDays(occupancy - 1L);
                BigDecimal perDay = hours.compareTo(BigDecimal.ZERO) > 0
                        ? hours.divide(BigDecimal.valueOf(occupancy), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                int fromIdx = -1, toIdx = -1;
                if (start != null) {
                    for (int i = 0; i < span; i++) {
                        LocalDate d = rangeStart.plusDays(i);
                        if (d.isBefore(start) || d.isAfter(end)) continue;
                        dayHours.merge(i, perDay, BigDecimal::add);
                        if (fromIdx < 0) fromIdx = i;
                        toIdx = i;
                    }
                }
                rows.add(new Object[]{op, hours, occupancy, start, end, perDay, fromIdx, toIdx});
            }

            // 第二遍：逐日负载/超载 + 块（块超载 = 其覆盖日里超出产能之和）
            List<Map<String, Object>> dayList = new ArrayList<>();
            boolean laneOverloaded = false;
            for (int i = 0; i < span; i++) {
                BigDecimal h = nvl(dayHours.get(i));
                boolean over = !capacityMissing && h.compareTo(cap) > 0;
                BigDecimal overHours = over ? h.subtract(cap) : BigDecimal.ZERO;
                Map<String, Object> day = new LinkedHashMap<>();
                day.put("date", rangeStart.plusDays(i).toString());
                day.put("hours", h);
                day.put("loadPercent", capacityMissing ? 0
                        : (int) Math.round(h.multiply(APS_HUNDRED).divide(cap, 4, RoundingMode.HALF_UP).doubleValue()));
                day.put("overloaded", over);
                dayList.add(day);
                if (over) {
                    laneOverloaded = true;
                    Map<String, Object> alert = new LinkedHashMap<>();
                    alert.put("workCenterId", wid);
                    alert.put("workCenterName", wc == null ? "未分配工作中心" : wc.getWorkCenterName());
                    alert.put("date", rangeStart.plusDays(i).toString());
                    alert.put("hours", h);
                    alert.put("capacity", cap);
                    alert.put("overloadHours", overHours);
                    alerts.add(alert);
                }
            }

            List<Map<String, Object>> blocks = new ArrayList<>();
            for (Object[] row : rows) {
                WorkOrderOperation op = (WorkOrderOperation) row[0];
                BigDecimal hours = (BigDecimal) row[1];
                int occupancy = (Integer) row[2];
                LocalDate start = (LocalDate) row[3];
                LocalDate end = (LocalDate) row[4];
                int fromIdx = (Integer) row[6];
                int toIdx = (Integer) row[7];
                BigDecimal perDay = (BigDecimal) row[5];

                BigDecimal blockOverHours = BigDecimal.ZERO;
                if (!capacityMissing && fromIdx >= 0 && perDay.compareTo(BigDecimal.ZERO) > 0) {
                    for (int i = fromIdx; i <= toIdx; i++) {
                        BigDecimal h = nvl(dayHours.get(i));
                        if (h.compareTo(cap) > 0) blockOverHours = blockOverHours.add(h.subtract(cap));
                    }
                }

                Map<String, Object> kit = kittingCache.get(op.getOrderId() + "#" + op.getOrderProductId());
                String kitStatus = (String) kit.get("status");
                LocalDate kitDate = (LocalDate) kit.get("date");

                int waitDays = 0;
                String waitFrom = null;
                if ((LocalDate) row[3] != null && kitDate != null
                        && ("READY".equals(kitStatus) || "EXPECTED".equals(kitStatus))
                        && kitDate.isAfter((LocalDate) row[3])) {
                    waitDays = (int) java.time.temporal.ChronoUnit.DAYS.between((LocalDate) row[3], kitDate);
                    waitFrom = ((LocalDate) row[3]).toString();
                }

                WorkOrder order = orderMap.get(op.getOrderId());
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("operationRowId", op.getId());
                block.put("orderId", op.getOrderId());
                block.put("orderNo", order == null ? op.getOrderNo() : order.getOrderNo());
                block.put("lineNo", op.getLineNo());
                block.put("operationName", op.getOperationName());
                block.put("seqIndex", seqIndex.get(op.getId()));
                block.put("seqTotal", seqTotal.get(op.getId()));
                block.put("assigneeId", op.getAssigneeId());
                block.put("assigneeName", op.getAssigneeName());
                block.put("unassigned", isUnassigned(op));
                block.put("planStartDate", start == null ? null : start.toString());
                block.put("planEndDate", end == null ? null : end.toString());
                block.put("occupancyDays", occupancy);
                block.put("hours", hours);
                block.put("overloaded", blockOverHours.compareTo(BigDecimal.ZERO) > 0);
                block.put("overloadHours", blockOverHours);
                block.put("kittingStatus", kitStatus);
                block.put("kittingDate", kitDate == null ? null : kitDate.toString());
                block.put("kittingShortageCount", kit.get("shortageCount"));
                block.put("waitDays", waitDays);
                block.put("waitFrom", waitFrom);
                blocks.add(block);
                if (isUnassigned(op)) unassignedCount++;
            }

            BigDecimal laneHours = BigDecimal.ZERO;
            for (BigDecimal v : dayHours.values()) laneHours = laneHours.add(v);
            Map<String, Object> lane = new LinkedHashMap<>();
            lane.put("workCenterId", wid);
            lane.put("workCenterName", wc == null ? "未分配工作中心" : wc.getWorkCenterName());
            lane.put("capacityPerDay", capacityMissing ? null : cap);
            lane.put("capacityUnit", wc == null ? null : wc.getCapacityUnit());
            lane.put("efficiency", capacityMissing ? null : eff);
            lane.put("capacityMissing", capacityMissing);
            // 【工时体系 2026-10-07】建议效率系数：只给建议，**不自动写入**工作中心效率（用户口径：只出报表 + 建议值）
            Map<String, Object> sg = wid == null ? null : efficiencySuggest.get(wid);
            lane.put("suggestedEfficiency", sg == null ? null : sg.get("suggestedEfficiency"));
            lane.put("suggestActualRate", sg == null ? null : sg.get("actualRate"));
            lane.put("suggestSampleSize", sg == null ? 0 : sg.get("sampleSize"));
            lane.put("loadPercent", capacityMissing ? 0
                    : (int) Math.round(laneHours.multiply(APS_HUNDRED)
                        .divide(cap.multiply(BigDecimal.valueOf(span)), 4, RoundingMode.HALF_UP).doubleValue()));
            lane.put("days", dayList);
            lane.put("blocks", blocks);
            lanes.add(lane);

            if (!capacityMissing) {
                loadSum = loadSum.add(BigDecimal.valueOf(((Number) lane.get("loadPercent")).longValue()));
                loadLaneCount++;
            }
            if (laneOverloaded) overloadedCenters++;
        }

        // 泳道排序：有名称按名称；"未分配工作中心"排最后
        lanes.sort(Comparator
                .comparing((Map<String, Object> l) -> l.get("workCenterId") == null)
                .thenComparing(l -> String.valueOf(l.get("workCenterName"))));

        int avgLoad = loadLaneCount == 0 ? 0
                : (int) Math.round(loadSum.divide(BigDecimal.valueOf(loadLaneCount), 4, RoundingMode.HALF_UP).doubleValue());
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("orderCount", orderIdSet.size());
        metrics.put("overloadedCenters", overloadedCenters);
        metrics.put("unassignedCount", unassignedCount);
        metrics.put("avgLoadPercent", avgLoad);

        result.put("metrics", metrics);
        result.put("alerts", alerts);
        result.put("lanes", lanes);
        return result;
    }

    /** 空态：结构与正常返回一致，只是各项为 0 / 空数组（前端据 metrics.orderCount==0 走引导） */
    private static Map<String, Object> apsEmpty(Map<String, Object> result) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("orderCount", 0);
        metrics.put("overloadedCenters", 0);
        metrics.put("unassignedCount", 0);
        metrics.put("avgLoadPercent", 0);
        result.put("metrics", metrics);
        result.put("alerts", new ArrayList<>());
        result.put("lanes", new ArrayList<>());
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> applyReschedule(List<Long> orderIds, int days, Long operatorId, String operatorName) {
        int span = APS_ALLOWED_DAYS.contains(days) ? days : 7;
        LocalDate rangeStart = LocalDate.now();

        QueryWrapper<WorkOrder> orderQw = new QueryWrapper<WorkOrder>().eq("status", STATUS_RELEASED);
        dataScopeResolver.applyIfLimited(orderQw, null, "created_by");
        if (orderIds != null && !orderIds.isEmpty()) orderQw.in("order_id", orderIds);
        List<WorkOrder> orders = workOrderMapper.selectList(orderQw);
        Map<Long, WorkOrder> orderMap = new LinkedHashMap<>();
        for (WorkOrder o : orders) orderMap.put(o.getOrderId(), o);

        List<Map<String, Object>> details = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", span);
        result.put("rangeStart", rangeStart.toString());
        if (orders.isEmpty()) {
            result.put("updated", 0);
            result.put("details", details);
            return result;
        }

        List<WorkOrderOperation> allOps = list(new QueryWrapper<WorkOrderOperation>().in("order_id", orderMap.keySet()));
        List<WorkOrderOperation> ops = new ArrayList<>();
        for (WorkOrderOperation op : allOps) {
            if (WorkOperationStatus.isTerminal(op.getOperationStatus())) continue;   // 终态工序不改日期
            ops.add(op);
        }

        Set<Long> wcIdSet = new LinkedHashSet<>();
        for (WorkOrderOperation op : ops) if (op.getWorkCenterId() != null) wcIdSet.add(op.getWorkCenterId());
        Map<Long, WorkCenter> wcMap = new HashMap<>();
        if (!wcIdSet.isEmpty()) {
            for (WorkCenter wc : workCenterMapper.selectBatchIds(wcIdSet)) wcMap.put(wc.getWorkCenterId(), wc);
        }

        // 2026-11-07：重排同样使用物料日产能（占用天数取工时产能与物料产能的较大值）
        Map<Long, WorkOrderProduct> productMap = new HashMap<>();
        if (!orderMap.isEmpty()) {
            List<WorkOrderProduct> products = workOrderProductMapper.selectList(
                    new QueryWrapper<WorkOrderProduct>().in("order_id", orderMap.keySet()));
            if (products != null) for (WorkOrderProduct p : products) productMap.put(p.getId(), p);
        }
        Set<Long> matIdSet = new LinkedHashSet<>();
        for (WorkOrderProduct p : productMap.values()) {
            if (p.getMaterialId() != null) matIdSet.add(p.getMaterialId());
        }
        for (WorkOrder o : orderMap.values()) {
            if (o.getMaterialId() != null) matIdSet.add(o.getMaterialId());
        }
        Map<Long, BigDecimal> matCapMap = new HashMap<>();
        if (!matIdSet.isEmpty()) {
            for (com.erp.masterdata.entity.Material m : materialMapper.selectBatchIds(matIdSet)) {
                if (m.getDailyCapacity() != null && m.getDailyCapacity().compareTo(BigDecimal.ZERO) > 0) {
                    matCapMap.put(m.getMaterialId(), m.getDailyCapacity());
                }
            }
        }

        Map<Long, List<WorkOrderOperation>> groups = new LinkedHashMap<>();
        for (WorkOrderOperation op : ops) groups.computeIfAbsent(op.getWorkCenterId(), x -> new ArrayList<>()).add(op);

        for (Map.Entry<Long, List<WorkOrderOperation>> entry : groups.entrySet()) {
            // 无工作中心 / 产能未维护 → 无法估产能，不参与重排（与看板"产能未维护不做超载判定"一致）
            if (entry.getKey() == null) continue;
            WorkCenter wc = wcMap.get(entry.getKey());
            BigDecimal cap = wc == null ? null : wc.getCapacityPerDay();
            if (cap == null || cap.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal capEff = cap.multiply(apsNormalizeEfficiency(wc));

            List<WorkOrderOperation> laneList = entry.getValue();
            laneList.sort(Comparator
                    .comparing((WorkOrderOperation op) -> apsOrderPlanEnd(op, orderMap),
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(WorkOrderOperation::getPlanEndDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(WorkOrderOperation::getLineNo, Comparator.nullsLast(Comparator.naturalOrder())));

            Map<LocalDate, BigDecimal> occ = new HashMap<>();
            for (WorkOrderOperation op : laneList) {
                BigDecimal hours = nvl(op.getSetupHours()).add(nvl(op.getProcessHours()));
                BigDecimal matCap = apsMaterialCapacity(op, productMap, orderMap, matCapMap);
                int occupancy = Math.max(apsOccupancyDays(hours, capEff, false),
                        apsMaterialOccupancyDays(nvl(op.getPlanQuantity()), matCap));
                BigDecimal perDay = hours.compareTo(BigDecimal.ZERO) > 0
                        ? hours.divide(BigDecimal.valueOf(occupancy), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                LocalDate desired = apsResolveStart(op, occupancy);
                if (desired == null) desired = rangeStart;

                LocalDate start = desired;
                for (int guard = 0; guard < 365; guard++) {
                    boolean fits = true;
                    for (int i = 0; i < occupancy; i++) {
                        BigDecimal cur = nvl(occ.get(start.plusDays(i)));
                        if (cur.add(perDay).compareTo(cap) > 0) { fits = false; break; }
                    }
                    if (fits) break;
                    start = start.plusDays(1);
                }
                for (int i = 0; i < occupancy; i++) occ.merge(start.plusDays(i), perDay, BigDecimal::add);
                LocalDate newEnd = start.plusDays(occupancy - 1L);

                LocalDate oldStart = op.getPlanStartDate();
                LocalDate oldEnd = op.getPlanEndDate();
                boolean changed = !start.equals(oldStart) || !newEnd.equals(oldEnd);
                if (!changed) continue;

                op.setPlanStartDate(start);
                op.setPlanEndDate(newEnd);
                updateById(op);
                String st = op.getOperationStatus();
                writeLog(op, WorkOperationStatus.ACT_RESCHEDULE, st, st, null, null, operatorId, operatorName,
                        "计划重排：计划开工 " + apsDateStr(oldStart) + " → " + start
                                + "，计划完工 " + apsDateStr(oldEnd) + " → " + newEnd);
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("operationRowId", op.getId());
                d.put("orderNo", orderMap.get(op.getOrderId()) == null ? op.getOrderNo() : orderMap.get(op.getOrderId()).getOrderNo());
                d.put("oldStart", apsDateStr(oldStart));
                d.put("newStart", start.toString());
                details.add(d);
            }
        }

        result.put("updated", details.size());
        result.put("details", details);
        return result;
    }

    /**
     * 工单总齐套（口径 4.2）：
     * <ul>
     *   <li>需求 = Σ(子件用量 × 计划数量)，按成品主 BOM 多级展开（复用 {@link BomExplosionService}）；</li>
     *   <li>已到 = 现有库存（{@code inventory_current.available_quantity} 汇总）—— 库存本身就是"实际入库 − 实际出库"的净结果，
     *       即"按实际入库"判定；<b>不含任何未入库的采购在途</b>；
     *       <b>只统计「参与MRP」的仓库</b>（仓库档案开关；不开的仓库如不合格品仓/废料仓里的货不能投产，不计入"已到"）；</li>
     *   <li>缺料且存在未到货采购行 → {@code EXPECTED}（预计齐套，交货日期仅作参考，不参与判定）；</li>
     *   <li>无主 BOM → {@code NO_BOM}；有缺口且无在途采购 → {@code SHORT}。</li>
     * </ul>
     */
    private Map<String, Object> computeKitting(WorkOrder order, WorkOrderProduct product, Set<Long> mrpWarehouseIds) {
        Map<String, Object> r = new LinkedHashMap<>();
        Long materialId = product != null && product.getMaterialId() != null ? product.getMaterialId()
                : (order == null ? null : order.getMaterialId());
        BigDecimal planQty = product != null && product.getQuantity() != null ? product.getQuantity()
                : (order == null ? null : order.getQuantity());
        if (materialId == null || planQty == null || planQty.compareTo(BigDecimal.ZERO) <= 0) {
            r.put("status", "NO_BOM");
            r.put("date", null);
            r.put("shortageCount", 0);
            return r;
        }
        List<BomExplosionService.BomExplosionItem> items =
                bomExplosionService.explodeBomMultiLevel(materialId, planQty, 10);
        if (items == null || items.isEmpty()) {
            r.put("status", "NO_BOM");
            r.put("date", null);
            r.put("shortageCount", 0);
            return r;
        }

        Set<Long> childIds = new LinkedHashSet<>();
        for (BomExplosionService.BomExplosionItem it : items) {
            if (it.getMaterialId() != null) childIds.add(it.getMaterialId());
        }
        Map<Long, BigDecimal> stock = new HashMap<>();
        Map<Long, LocalDate> lastIn = new HashMap<>();
        if (!childIds.isEmpty() && mrpWarehouseIds != null && !mrpWarehouseIds.isEmpty()) {
            List<Inventory> invs = inventoryMapper.selectList(
                    new QueryWrapper<Inventory>().in("material_id", childIds).in("stock_id", mrpWarehouseIds));
            if (invs != null) {
                for (Inventory inv : invs) {
                    Long mid = inv.getMaterialId();
                    if (mid == null) continue;
                    BigDecimal q = inv.getAvailableQuantity() != null
                            ? inv.getAvailableQuantity() : nvl(inv.getQuantity());
                    stock.merge(mid, q, BigDecimal::add);
                    if (inv.getLastInDate() != null) {
                        LocalDate cur = lastIn.get(mid);
                        if (cur == null || inv.getLastInDate().isAfter(cur)) lastIn.put(mid, inv.getLastInDate());
                    }
                }
            }
        }

        int shortCnt = 0;
        Set<Long> shortIds = new LinkedHashSet<>();
        for (BomExplosionService.BomExplosionItem it : items) {
            BigDecimal demand = nvl(it.getTotalQuantity());
            BigDecimal supply = stock.getOrDefault(it.getMaterialId(), BigDecimal.ZERO);
            if (supply.compareTo(demand) < 0) {
                shortCnt++;
                if (it.getMaterialId() != null) shortIds.add(it.getMaterialId());
            }
        }

        if (shortCnt == 0) {
            LocalDate d = null;
            for (BomExplosionService.BomExplosionItem it : items) {
                LocalDate x = lastIn.get(it.getMaterialId());
                if (x != null && (d == null || x.isAfter(d))) d = x;
            }
            r.put("status", "READY");
            r.put("date", d == null ? LocalDate.now() : d);
            r.put("shortageCount", 0);
            return r;
        }
        LocalDate expected = apsExpectedDeliveryDate(shortIds);
        r.put("status", expected == null ? "SHORT" : "EXPECTED");
        r.put("date", expected);
        r.put("shortageCount", shortCnt);
        return r;
    }

    /**
     * 「参与MRP」的仓库 id 集合（2026-10-09）：仓库档案里「参与MRP」开关关闭的仓库
     * （建库默认把「不合格品仓」设为不参与）库存不得计入齐套的「已到」。
     * <p>老数据里该列可能为 NULL（本地实测存在），NULL 按「开关默认开」处理 = 参与，避免老仓库被整体排除 → 齐套永远缺料。</p>
     */
    private Set<Long> mrpAvailableWarehouseIds() {
        Set<Long> ids = new LinkedHashSet<>();
        try {
            List<Warehouse> whs = warehouseMapper.selectList(
                    new QueryWrapper<Warehouse>().select("stock_id")
                            .and(w -> w.isNull("is_mrp_available").or().eq("is_mrp_available", (short) 1)));
            if (whs != null) {
                for (Warehouse w : whs) {
                    if (w.getStockId() != null) ids.add(w.getStockId());
                }
            }
        } catch (Exception e) {
            log.warn("APS 齐套：查询「参与MRP」仓库失败: {}", e.getMessage());
        }
        return ids;
    }

    /** 缺料子件的预计到货日：未到货采购行的交货日期最大值（已作废/草稿的采购单不计） */
    private LocalDate apsExpectedDeliveryDate(Set<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) return null;
        List<PurchaseOrderItem> items = purchaseOrderItemMapper.selectList(
                new QueryWrapper<PurchaseOrderItem>().in("material_id", materialIds).isNotNull("delivery_date"));
        if (items == null || items.isEmpty()) return null;
        List<PurchaseOrderItem> pending = new ArrayList<>();
        Set<Long> poIds = new LinkedHashSet<>();
        for (PurchaseOrderItem it : items) {
            if (nvl(it.getReceivedQuantity()).compareTo(nvl(it.getQuantity())) < 0) {
                pending.add(it);
                if (it.getOrderId() != null) poIds.add(it.getOrderId());
            }
        }
        if (pending.isEmpty()) return null;
        Set<Long> validPo = new LinkedHashSet<>();
        if (!poIds.isEmpty()) {
            List<PurchaseOrder> pos = purchaseOrderMapper.selectBatchIds(poIds);
            if (pos != null) {
                for (PurchaseOrder po : pos) {
                    String st = po.getStatus();
                    if (st == null || (!st.contains("草稿") && !st.contains("作废"))) validPo.add(po.getOrderId());
                }
            }
        }
        LocalDate max = null;
        for (PurchaseOrderItem it : pending) {
            if (it.getOrderId() != null && !validPo.contains(it.getOrderId())) continue;
            LocalDate d = it.getDeliveryDate();
            if (d != null && (max == null || d.isAfter(max))) max = d;
        }
        return max;
    }

    /** 单道占用天数 = ⌈(准备工时 + 加工工时) ÷ (日产能 × 效率)⌉；产能未维护或工时为 0 → 降级 1 天 */
    private static int apsOccupancyDays(BigDecimal hours, BigDecimal capEff, boolean capacityMissing) {
        if (capacityMissing || capEff == null || capEff.compareTo(BigDecimal.ZERO) <= 0) return 1;
        if (hours == null || hours.compareTo(BigDecimal.ZERO) <= 0) return 1;
        BigDecimal d = hours.divide(capEff, 4, RoundingMode.HALF_UP);
        return (int) Math.ceil(d.doubleValue());
    }

    /**
     * 2026-11-07：取该工序所属成品的物料日产能（成品行 materialId 优先，回落整单物料）。
     * 返回 null = 未维护/不限制。
     */
    private static BigDecimal apsMaterialCapacity(WorkOrderOperation op,
                                                  Map<Long, WorkOrderProduct> productMap,
                                                  Map<Long, WorkOrder> orderMap,
                                                  Map<Long, BigDecimal> matCapMap) {
        if (op == null) return null;
        Long materialId = null;
        WorkOrderProduct p = op.getOrderProductId() == null ? null : productMap.get(op.getOrderProductId());
        if (p != null) materialId = p.getMaterialId();
        if (materialId == null) {
            WorkOrder o = op.getOrderId() == null ? null : orderMap.get(op.getOrderId());
            if (o != null) materialId = o.getMaterialId();
        }
        return materialId == null ? null : matCapMap.get(materialId);
    }

    /** 2026-11-07：按物料日产能估算占用天数；日产能为空/<=0 或数量<=0 → 0（表示不限制，由工时产能决定） */
    private static int apsMaterialOccupancyDays(BigDecimal planQty, BigDecimal dailyCapacity) {
        if (dailyCapacity == null || dailyCapacity.compareTo(BigDecimal.ZERO) <= 0) return 0;
        if (planQty == null || planQty.compareTo(BigDecimal.ZERO) <= 0) return 0;
        return planQty.divide(dailyCapacity, 0, RoundingMode.CEILING).intValue();
    }

    /** 计划开工：老数据为空时按"计划完工 − (占用天数 − 1)"倒推（只用于展示，不写库） */
    private static LocalDate apsResolveStart(WorkOrderOperation op, int occupancy) {
        LocalDate s = op.getPlanStartDate();
        if (s == null && op.getPlanEndDate() != null) {
            s = op.getPlanEndDate().minusDays(Math.max(0, occupancy - 1));
        }
        return s;
    }

    /** 效率换算：库里按百分数存（默认 100）→ 倍率（1.0）；为空/0 → 1.0 */
    private static BigDecimal apsNormalizeEfficiency(WorkCenter wc) {
        if (wc == null || wc.getEfficiency() == null || wc.getEfficiency().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        BigDecimal e = wc.getEfficiency();
        return e.compareTo(APS_EFF_PERCENT_THRESHOLD) > 0
                ? e.divide(APS_HUNDRED, 4, RoundingMode.HALF_UP) : e;
    }

    /** 排队用交期：工单计划完工日期（取不到返回 null，排最后） */
    private static LocalDate apsOrderPlanEnd(WorkOrderOperation op, Map<Long, WorkOrder> orderMap) {
        WorkOrder o = orderMap == null ? null : orderMap.get(op.getOrderId());
        return o == null ? null : o.getPlanEndDate();
    }

    private static String apsDateStr(LocalDate d) {
        return d == null ? null : d.toString();
    }

    /**
     * 【第 13 期】递归收集某节点的全部叶子后代（无子节点者即叶子）。
     * 用于父工序的「子项汇总进度」—— 状态类父节点本身不报工，进度只能由叶子说了算；
     * 用已访问集合防环，即便数据异常成环也不会死循环。
     */
    private static List<WorkOrderOperation> collectLeafDescendants(Long rootId,
                                                                   Map<Long, List<WorkOrderOperation>> childrenOf) {
        List<WorkOrderOperation> leaves = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        java.util.Deque<WorkOrderOperation> stack =
                new java.util.ArrayDeque<>(childrenOf.getOrDefault(rootId, List.of()));
        while (!stack.isEmpty()) {
            WorkOrderOperation cur = stack.pop();
            if (!visited.add(cur.getId())) continue;
            List<WorkOrderOperation> kids = childrenOf.get(cur.getId());
            if (kids == null || kids.isEmpty()) {
                leaves.add(cur);
            } else {
                for (WorkOrderOperation k : kids) stack.push(k);
            }
        }
        return leaves;
    }

    /** 取一条线路里第一道工序的计划完工日期（工序已按日期升序，首个即最早；用于工单线路排序） */
    private static LocalDate firstPlanEnd(Map<String, Object> group) {
        Object rows = group.get("ops");
        if (!(rows instanceof List<?> list) || list.isEmpty()) return null;
        Object first = list.get(0);
        return first instanceof WorkOrderOperation op ? op.getPlanEndDate() : null;
    }

    @Override
    public List<WorkOperationLog> logs(Long operationRowId) {
        return logMapper.selectList(new QueryWrapper<WorkOperationLog>()
                .eq("operation_row_id", operationRowId)
                .orderByDesc("operate_time")
                .orderByDesc("log_id"));
    }

    // ================= 【第 4 版 2026-10-08】「谁可以改负责人」（按工艺路线档位） =================
    // 口径（唯一事实来源，前端下拉与后端强校验共用同一判定）：
    //   全部人     = 任何人（默认，等价升级前行为）
    //   当前负责人  = 该工序当前负责人本人（待派工无负责人时仅管理员可派）
    //   指定角色   = 登录人所属角色 ∩ routing.assign_role_ids（角色编码，逗号分隔）
    //   工单开单人  = 该工单的开单人本人（production_order.created_by，与登录账号同口径）
    //   兜底：管理员（ADMIN/MANAGER/租户版本管理员/SUPER_ADMIN）始终放行，避免"没人能派工"的死结。

    /** 取当前 HTTP 请求（读取登录人角色/员工；非 HTTP 场景返回 null） */
    private static HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return (attrs instanceof ServletRequestAttributes sra) ? sra.getRequest() : null;
    }

    /** 该工序所属工艺路线（取不到返回 null） */
    private Routing routingOf(WorkOrderOperation op) {
        if (op == null || op.getRoutingId() == null) return null;
        try {
            return routingMapper.selectById(op.getRoutingId());
        } catch (Exception e) {
            return null; // 读路线失败不阻断：按「全部人」兜底（不改变升级前行为）
        }
    }

    /** 工艺路线的「谁可以改负责人」档位；未配置/空白 → 「全部人」 */
    private static String assignScopeOf(Routing r) {
        String s = r == null ? null : r.getAssignScope();
        return (s == null || s.isBlank()) ? WorkOperationStatus.ASSIGN_SCOPE_ALL : s.trim();
    }

    /** 【2026-10-09】该工序在工艺路线明细里的那一行（按 operation_id 反查）；取不到返回 null */
    private RoutingOperation routingOperationOf(WorkOrderOperation op) {
        if (op == null || op.getOperationId() == null) return null;
        try {
            return routingOperationMapper.selectById(op.getOperationId());
        } catch (Exception e) {
            return null; // 读失败不阻断：回落路线级设置，再回落「全部人」
        }
    }

    /**
     * 【2026-10-09】该工序的「谁可以改负责人」档位：**工序行自己 → 所属工艺路线（旧值兜底）→ 全部人**。
     * 每道工序各自设、互不影响；工序行未配置时回落路线级（兼容未迁移数据 = 升级前行为）。
     */
    private String assignScopeOf(WorkOrderOperation op) {
        RoutingOperation ro = routingOperationOf(op);
        if (ro != null && ro.getAssignScope() != null && !ro.getAssignScope().isBlank()) {
            return ro.getAssignScope().trim();
        }
        return assignScopeOf(routingOf(op));
    }

    /** 【2026-10-09】该工序档位对应的「可改负责人角色」清单（仅「指定角色」使用；工序优先、路线兜底） */
    private Set<String> assignRolesOf(WorkOrderOperation op) {
        RoutingOperation ro = routingOperationOf(op);
        if (ro != null && ro.getAssignScope() != null && !ro.getAssignScope().isBlank()) {
            return splitCsv(ro.getAssignRoleIds());
        }
        Routing r = routingOf(op);
        return splitCsv(r == null ? null : r.getAssignRoleIds());
    }

    /** 当前登录人对应的员工ID（优先 sys_user.employee_id，否则按 employee.user_id 反查） */
    private Long currentEmployeeId(HttpServletRequest req, Long operatorId) {
        if (req != null) {
            Long emp = SecurityUtils.getCurrentEmployeeId(req);
            if (emp != null) return emp;
        }
        if (operatorId != null) {
            List<Employee> list = employeeMapper.selectList(
                    new QueryWrapper<Employee>().eq("user_id", operatorId).last("LIMIT 1"));
            if (list != null && !list.isEmpty()) return list.get(0).getEmployeeId();
        }
        return null;
    }

    /** 当前登录人是否被允许修改该工序的负责人（前端 canAssign 与后端强校验共用同一判定） */
    public boolean canChangeAssignee(WorkOrderOperation op, Long operatorId, String operatorName) {
        if (op == null) return false;
        String scope = assignScopeOf(op);
        if (WorkOperationStatus.ASSIGN_SCOPE_ALL.equals(scope)) return true;
        HttpServletRequest req = currentRequest();
        // 管理员（含租户版本管理员 / 平台超管）始终放行，避免"没人能派工"的死结
        if (req != null && (SecurityUtils.isAdmin(req) || SecurityUtils.hasAnyRole(req, "SUPER_ADMIN"))) return true;
        if (WorkOperationStatus.ASSIGN_SCOPE_ROLE.equals(scope)) {
            if (req == null) return false;
            Set<String> allow = assignRolesOf(op);
            if (allow.isEmpty()) return false;
            for (String roleCode : SecurityUtils.getCurrentRoles(req)) {
                if (allow.contains(roleCode)) return true;
            }
            return false;
        }
        if (WorkOperationStatus.ASSIGN_SCOPE_CREATOR.equals(scope)) {
            if (operatorName == null || op.getOrderId() == null) return false;
            WorkOrder wo = workOrderMapper.selectById(op.getOrderId());
            return wo != null && operatorName.equals(wo.getCreatedBy());
        }
        if (WorkOperationStatus.ASSIGN_SCOPE_ASSIGNEE.equals(scope)) {
            Long myEmp = currentEmployeeId(req, operatorId);
            return myEmp != null && op.getAssigneeId() != null && myEmp.equals(op.getAssigneeId());
        }
        return false;
    }

    /** 强校验：不允许改负责人时抛出中文业务提示 */
    private void assertCanChangeAssignee(WorkOrderOperation op, Long operatorId, String operatorName) {
        if (!canChangeAssignee(op, operatorId, operatorName)) {
            throw new BusinessException("按该工序的「谁可以改负责人」设置，你没有权限修改负责人");
        }
    }

    /** 逗号分隔 → 去空白集合（保序） */
    private static Set<String> splitCsv(String csv) {
        Set<String> set = new LinkedHashSet<>();
        if (csv == null) return set;
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) set.add(t);
        }
        return set;
    }

    @Override
    public Map<String, Object> assignPermission(Long operationRowId, Long operatorId, String operatorName) {
        WorkOrderOperation op = getById(operationRowId);
        Map<String, Object> data = new LinkedHashMap<>();
        if (op == null) {
            data.put("scope", WorkOperationStatus.ASSIGN_SCOPE_ALL);
            data.put("canAssign", false);
            return data;
        }
        data.put("scope", assignScopeOf(op));
        data.put("canAssign", canChangeAssignee(op, operatorId, operatorName));
        return data;
    }

    @Override
    @Transactional
    public WorkOrderOperation act(Long operationRowId, String action, Map<String, Object> payload,
                                  Long operatorId, String operatorName) {
        WorkOrderOperation op = getById(operationRowId);
        if (op == null) throw new BusinessException("工序不存在");
        Map<String, Object> body = payload == null ? Map.of() : payload;

        String from = op.getOperationStatus() == null ? WorkOperationStatus.PENDING_ASSIGN : op.getOperationStatus();
        // 【第 2 版 2026-10-07】历史别名归一：AI 小助手仍按「更新计划」传参，语义等价于面板的「保存」
        // （只改负责人/计划完工/备注/附件，不推进状态）。归一后才能通过下面的 allowedActions 校验。
        if (WorkOperationStatus.ACT_PLAN.equals(action)) {
            action = WorkOperationStatus.ACT_SAVE;
        }
        // 【第 4 版 2026-10-08】旧动作名归一：「重新派工」→「恢复工序」（AI 小助手旧参数/历史脚本不炸）
        if (WorkOperationStatus.ACT_REOPEN_LEGACY.equals(action)) {
            action = WorkOperationStatus.ACT_REOPEN;
        }
        // 第 11 期：已取消仍是终态，但「重新派工」是唯一允许把它拉回「待派工」的动作（取消必须有回程）
        // 终态守卫的两个例外：① 已取消 → 重新派工；② 已完工 → 撤销完工（用户 2026-10-07 口径：填错要能改）
        boolean reopenFromCancelled = WorkOperationStatus.CANCELLED.equals(from)
                && WorkOperationStatus.ACT_REOPEN.equals(action);
        boolean unfinishFromFinished = WorkOperationStatus.FINISHED.equals(from)
                && WorkOperationStatus.ACT_UNFINISH.equals(action);
        if (WorkOperationStatus.isTerminal(from) && !reopenFromCancelled && !unfinishFromFinished) {
            throw new BusinessException("工序已是终态[" + from + "]，不能再操作");
        }
        if (!WorkOperationStatus.allowedActions(from).contains(action)) {
            throw new BusinessException("当前状态[" + from + "]不允许[" + action + "]");
        }
        // 【第 4 版 2026-10-08】派工/改派 = 改负责人：按工序所属工艺路线的「谁可以改负责人」档位强校验（防绕界面）
        if (WorkOperationStatus.ACT_ASSIGN.equals(action) || WorkOperationStatus.ACT_REASSIGN.equals(action)) {
            assertCanChangeAssignee(op, operatorId, operatorName);
        } else if (WorkOperationStatus.ACT_SAVE.equals(action) || WorkOperationStatus.ACT_REOPEN.equals(action)) {
            // 【2026-10-09 修复 · 权限绕过】「保存 / 恢复工序」在下面的 switch 分支里也会写负责人（带 assigneeId 即落库），
            // 而先前只有派工/改派过了档位校验 → 无权限的人可用「保存」把负责人改成任意人，整条「谁可以改负责人」限制被击穿。
            // 仅当本次确实要改人（传入值非空且与当前负责人不同）时才校验：只存计划日期/备注的正常保存（值不变）不受影响。
            Object newAssignee = body.get("assigneeId");
            boolean willSetAssignee = newAssignee != null && !String.valueOf(newAssignee).isBlank();
            if (willSetAssignee && !String.valueOf(newAssignee).equals(String.valueOf(op.getAssigneeId()))) {
                assertCanChangeAssignee(op, operatorId, operatorName);
            }
        }
        boolean qcRequired = op.getIsQcRequired() != null && op.getIsQcRequired() == 1;

        // 【第 19 期 · 用户口径：封堵"跳过质检直接判通过"】开了「需质检」的工序必须先有检验记录，
        // 才允许执行「检验通过 / 检验退回」。填质检本身（submitInspection）会先落 qc_record 再调本方法，故不受影响。
        if ((WorkOperationStatus.ACT_QC_PASS.equals(action) || WorkOperationStatus.ACT_QC_REJECT.equals(action)) && qcRequired) {
            Long qcCount = qcRecordMapper.selectCount(
                    new QueryWrapper<QcRecord>().eq("operation_row_id", op.getId()));
            if (qcCount == null || qcCount == 0) {
                throw new BusinessException("该工序需质检，请先填写质检（填质检）后再判定");
            }
        }

        // 流转守卫【2026-09-28 用户口径：按顺序执行开关控制】：
        // 「工艺路线 → 按顺序执行」= 1 时，开工/数量报工前前道工序必须已完工，否则拒绝；
        // = 0（默认，或工艺路线查不到 / 已停用 / 已删除）时不拦截 —— 与用户口径「默认关 = 后道可自由开工」一致。
        if (WorkOperationStatus.ACT_START.equals(action) || WorkOperationStatus.ACT_REPORT.equals(action)) {
            assertSequenceAllowed(op, action);
        }

        String to = WorkOperationStatus.targetStatus(action, from, qcRequired);
        BigDecimal quantity = toDecimal(body.get("quantity"));
        BigDecimal rejected = toDecimal(body.get("rejectedQuantity"));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));

        // 【第12期】下道卡量：上道有质检（approve_qty 非空）时，本道开工/报工数量不得超过"上道合格数"
        if (WorkOperationStatus.ACT_START.equals(action) || WorkOperationStatus.ACT_REPORT.equals(action)) {
            assertWithinPrevApprove(op, action, quantity, body);
        }
        // 第 11 期：取消工序必须填写取消原因（便于追溯"为什么取消"）
        if (WorkOperationStatus.ACT_CANCEL.equals(action) && (remark == null || remark.isBlank())) {
            throw new BusinessException("请填写取消原因");
        }

        switch (action) {
            case WorkOperationStatus.ACT_ASSIGN, WorkOperationStatus.ACT_REASSIGN,
                 WorkOperationStatus.ACT_SAVE, WorkOperationStatus.ACT_REOPEN -> {
                // 派工/改派要选人；保存只改日期也可只改人；重新派工（已取消→待派工）可选新负责人，都不改额外状态
                if (body.get("assigneeId") != null && !String.valueOf(body.get("assigneeId")).isBlank()) {
                    op.setAssigneeId(Long.valueOf(String.valueOf(body.get("assigneeId"))));
                    op.setAssigneeName(body.get("assigneeName") == null ? null : String.valueOf(body.get("assigneeName")));
                } else if (WorkOperationStatus.ACT_ASSIGN.equals(action)
                        || WorkOperationStatus.ACT_REASSIGN.equals(action)) {
                    throw new BusinessException("请选择工序负责人");
                }
            }
            case WorkOperationStatus.ACT_START -> markStart(op);
            case WorkOperationStatus.ACT_REPORT -> {
                markStart(op);
                // 第 11 期：数量类型工序可手工填「投入数量」（非空且 >0 时覆盖自动推算值）
                BigDecimal inputQty = toDecimalOrNull(body.get("inputQuantity"));
                if (inputQty != null && inputQty.compareTo(BigDecimal.ZERO) > 0) {
                    op.setInputQuantity(inputQty);
                }
                BigDecimal add = quantity.compareTo(BigDecimal.ZERO) > 0 ? quantity : BigDecimal.ZERO;
                op.setReportedQuantity(nvl(op.getReportedQuantity()).add(add));
                op.setOutputQuantity(nvl(op.getOutputQuantity()).add(add));
                op.setRejectedQuantity(nvl(op.getRejectedQuantity()).add(rejected));
                // 累计合格数达到计划量 → 自动完工（需质检则落"完工待检"）
                BigDecimal plan = nvl(op.getPlanQuantity());
                if (plan.compareTo(BigDecimal.ZERO) > 0 && op.getReportedQuantity().compareTo(plan) >= 0) {
                    to = qcRequired ? WorkOperationStatus.WAIT_QC : WorkOperationStatus.FINISHED;
                    if (WorkOperationStatus.FINISHED.equals(to)) {
                        op.setActualEndDate(LocalDate.now());
                    }
                }
            }
            case WorkOperationStatus.ACT_QC_PASS -> op.setActualEndDate(LocalDate.now());
            case WorkOperationStatus.ACT_QC_REJECT -> {
                // 检验退回：不写完工日期，回到进行中继续做
                op.setActualEndDate(null);
            }
            case WorkOperationStatus.ACT_FINISH -> {
                if (!qcRequired) op.setActualEndDate(LocalDate.now());
            }
            case WorkOperationStatus.ACT_UNFINISH -> {
                // 撤销完工（用户 2026-10-07 口径）：先判下游能不能退，再退回「进行中」
                assertUnfinishAllowed(op);
                op.setActualEndDate(null);
            }
            default -> {
                // 派工以外无需附加处理（暂停/恢复/取消只改状态）
            }
        }

        // 【第 13 期】金额类型工序：登记「本次金额」时累加到「已确认金额」。
        // 改造前只把金额写进操作日志备注、不落库（命中"前端字段存在但后端不持久化"硬约束），本次补上。
        if (WorkOperationStatus.TYPE_AMOUNT.equals(op.getOperationType())) {
            BigDecimal amount = toDecimalOrNull(body.get("amount"));
            if (amount != null && amount.compareTo(BigDecimal.ZERO) != 0) {
                op.setConfirmedAmount(nvl(op.getConfirmedAmount()).add(amount));
            }
        }

        // 计划完工日期：可随时手选（也可沿用系统自动推算值）
        Object planEnd = body.get("planEndDate");
        if (planEnd != null && !String.valueOf(planEnd).isBlank()) {
            try {
                op.setPlanEndDate(LocalDate.parse(String.valueOf(planEnd).substring(0, 10)));
            } catch (Exception e) {
                throw new BusinessException("计划完工日期格式不正确，应为 yyyy-MM-dd");
            }
        }

        // 第 11 期：重新派工的日志要能一眼看出"从已取消恢复"（配合取消原因形成完整留痕）
        if (WorkOperationStatus.ACT_REOPEN.equals(action)) {
            remark = (remark == null || remark.isBlank() ? "" : remark + "；") + "从已取消恢复";
        }

        // 【工时体系 2026-10-07】工时计时与结算（唯一入口，禁止各分支自己算）：
        //   开始计时 = 开工 / 恢复 / 检验退回 / 「报工使状态转开工·进行中」
        //   停表结算 = 暂停 / 取消 / 完工 / 检验通过 / 「报工后落到终态或完工待检」
        // 用户口径：跨天与放假一律靠人工点「暂停」停表，不做班次自动扣减（只算这一批做完的时间）。
        LocalDateTime timingNow = LocalDateTime.now();
        boolean timingStart = WorkOperationStatus.ACT_START.equals(action)
                || WorkOperationStatus.ACT_RESUME.equals(action)
                || WorkOperationStatus.ACT_QC_REJECT.equals(action)
                // 撤销完工 = 重新开始计时（把已完工这条"停表"重新打开，工时继续累计）
                || WorkOperationStatus.ACT_UNFINISH.equals(action)
                || (WorkOperationStatus.ACT_REPORT.equals(action) && isTimingStatus(to));
        boolean timingStop = WorkOperationStatus.ACT_PAUSE.equals(action)
                || WorkOperationStatus.ACT_CANCEL.equals(action)
                || WorkOperationStatus.ACT_FINISH.equals(action)
                || WorkOperationStatus.ACT_QC_PASS.equals(action)
                || (WorkOperationStatus.ACT_REPORT.equals(action) && !isTimingStatus(to));
        boolean timingDirty;
        if (timingStart) {
            timingDirty = startTiming(op, timingNow);
        } else if (timingStop) {
            timingDirty = stopTiming(op, timingNow);
        } else {
            timingDirty = false;
        }
        // 重新派工 = 新一轮：工时清零（用户口径「只算这一批做完的时间」）
        if (WorkOperationStatus.ACT_REOPEN.equals(action)) {
            op.setActualHours(BigDecimal.ZERO);
            op.setTimingSince(null);
            timingDirty = true;
        }

        op.setOperationStatus(to);
        updateById(op);

        // timing_since 停表时要被"置空"，MyBatis-Plus 全局 FieldStrategy 会跳过 null → 必须显式写列
        if (timingDirty) {
            lambdaUpdate()
                    .eq(WorkOrderOperation::getId, op.getId())
                    .set(WorkOrderOperation::getActualHours, nvl(op.getActualHours()))
                    .set(WorkOrderOperation::getTimingSince, op.getTimingSince())
                    .update();
        }

        // 撤销完工要把「实际完工日期」真的写回空：MyBatis-Plus 全局 FieldStrategy 会跳过 null
        // （与附件 / 计时列同款坑，实测"撤销后完工日期还在"）→ 必须 lambdaUpdate().set() 显式写列
        if (WorkOperationStatus.ACT_UNFINISH.equals(action)) {
            lambdaUpdate()
                    .eq(WorkOrderOperation::getId, op.getId())
                    .set(WorkOrderOperation::getActualEndDate, null)
                    .update();
        }

        // 【第 2 版 2026-10-07】工序附件：随「保存」或任一动作一并落库。
        // 必须用 lambdaUpdate().set() **显式写列**：项目的 MyBatis-Plus 全局 FieldStrategy 会把
        // null / 空串跳过（实测清空附件不生效，库里旧串还在）→ 只能绕过实体更新策略强制 SET。
        if (body.containsKey("attachments")) {
            String att = normalizeAttachments(body.get("attachments"));
            op.setAttachments(att);
            lambdaUpdate()
                    .eq(WorkOrderOperation::getId, op.getId())
                    .set(WorkOrderOperation::getAttachments, att)
                    .update();
        }

        // 【2026-11-25】工序自定义字段：随「保存」或任一动作一并落库（同一事务，失败即整单回滚，不出现假成功）
        saveOperationCustomFields(op.getId(), body.get("customFields"));

        writeLog(op, action, from, to, quantity, rejected, operatorId, operatorName, remark);

        // 【2026-11-06 用户口径 · 写死规则】入库类型工序完工（含质检通过落地完工）→ 自动生成草稿生产入库单。
        // 不做设置中心开关：工序类型选成「入库类型」即代表允许。数量 = 合格产出 − 已入库量，差值 ≤ 0 不生成。
        if (WorkOperationStatus.TYPE_RECEIPT.equals(op.getOperationType())
                && WorkOperationStatus.FINISHED.equals(to)
                && !WorkOperationStatus.FINISHED.equals(from)) {
            autoPushReceiptOnFinish(op, operatorId, operatorName);
        }
        // 【2026-09-28】工序「完工 / 检验通过」成功之后：工艺路线开启「自动通知下道工序负责人」时，
        // 给下道工序负责人发一条通知。通知失败只打 warn 日志，绝不影响工序状态流转本身。
        if ((WorkOperationStatus.ACT_FINISH.equals(action) || WorkOperationStatus.ACT_QC_PASS.equals(action))
                && WorkOperationStatus.FINISHED.equals(to)) {
            tryAutoNotifyNext(op, operatorId, operatorName);
        }
        return op;
    }

    /**
     * 「撤销完工」守卫（用户 2026-10-07 口径）：只有"下游还没动"才允许把已完工退回进行中。
     *
     * <p>三类命中即拒绝，避免「前道退回进行中、后道却已按前道完工开工」这类数据打架：</p>
     * <ol>
     *   <li>所属工单已结案 —— 先反结案；</li>
     *   <li>本工序已发起入库单（入库单来源指向本工序行）—— 先处理入库单；</li>
     *   <li>后道工序已开工 / 进行中 / 已完工 / 完工待检 —— 先处理它。</li>
     * </ol>
     * 提示语都写明"下一步先做什么"，避免用户只看到"不能操作"却不知道怎么办。
     */
    private void assertUnfinishAllowed(WorkOrderOperation op) {
        if (op.getOrderId() != null) {
            WorkOrder order = workOrderMapper.selectById(op.getOrderId());
            if (order != null && order.getIsClosed() != null && order.getIsClosed() == 1) {
                throw new BusinessException("所属工单已结案，请先反结案再撤销完工");
            }
        }
        Long receiptCnt = productionReceiptMapper.selectCount(new QueryWrapper<ProductionReceipt>()
                .eq("source_operation_id", op.getId()));
        if (receiptCnt != null && receiptCnt > 0) {
            throw new BusinessException("该工序已发起入库单，请先处理入库单再撤销完工");
        }
        if (op.getLineNo() == null) return;
        QueryWrapper<WorkOrderOperation> qw = new QueryWrapper<WorkOrderOperation>()
                .eq("order_id", op.getOrderId())
                .gt("line_no", op.getLineNo());
        if (op.getOrderProductId() != null) {
            qw.eq("order_product_id", op.getOrderProductId());
        } else {
            qw.isNull("order_product_id");
        }
        // 后道只要"不是初始态、也没被取消"就算已动过 → 不许退回
        for (WorkOrderOperation n : list(qw)) {
            String st = n.getOperationStatus();
            boolean untouched = st == null
                    || WorkOperationStatus.PENDING_ASSIGN.equals(st)
                    || WorkOperationStatus.ASSIGNED.equals(st)
                    || WorkOperationStatus.CANCELLED.equals(st);
            if (!untouched) {
                throw new BusinessException("后道工序["
                        + (n.getOperationName() == null ? "" : n.getOperationName())
                        + "]已开工，请先处理它再撤销完工");
            }
        }
    }

    // -------------------- 【工时体系 2026-10-07】计时与结算 --------------------

    /**
     * 按工作中心统计历史「实际工时 ÷ 计划工时」的中位数 → 建议效率系数 = 1 ÷ 中位数。
     * 只做建议（**不自动写入**工作中心效率）；样本 &lt; 5 不给建议；计划 ≤ 0 或实际为 0 的行不参与。
     */
    private Map<Long, Map<String, Object>> apsSuggestedEfficiency() {
        List<WorkOrderOperation> rows = list(new QueryWrapper<WorkOrderOperation>()
                .gt("actual_hours", 0)
                .isNotNull("work_center_id"));
        Map<Long, List<BigDecimal>> rates = new LinkedHashMap<>();
        for (WorkOrderOperation op : rows) {
            BigDecimal plan = nvl(op.getSetupHours()).add(nvl(op.getProcessHours()));
            BigDecimal actual = nvl(op.getActualHours());
            if (plan.compareTo(BigDecimal.ZERO) <= 0 || actual.compareTo(BigDecimal.ZERO) <= 0) continue;
            rates.computeIfAbsent(op.getWorkCenterId(), k -> new ArrayList<>())
                    .add(actual.divide(plan, 4, RoundingMode.HALF_UP));
        }
        Map<Long, Map<String, Object>> out = new HashMap<>();
        for (Map.Entry<Long, List<BigDecimal>> e : rates.entrySet()) {
            List<BigDecimal> list = e.getValue();
            if (list.size() < 5) continue;
            Collections.sort(list);
            int idx = Math.max(0, Math.min(list.size() - 1, (int) Math.ceil(0.5 * list.size()) - 1));
            BigDecimal median = list.get(idx);
            if (median.compareTo(BigDecimal.ZERO) <= 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("suggestedEfficiency", BigDecimal.ONE.divide(median, 2, RoundingMode.HALF_UP));
            m.put("actualRate", median);
            m.put("sampleSize", list.size());
            out.put(e.getKey(), m);
        }
        return out;
    }

    /** 计时中状态（开工 / 进行中） */
    private static boolean isTimingStatus(String st) {
        return WorkOperationStatus.STARTED.equals(st) || WorkOperationStatus.RUNNING.equals(st);
    }

    /** 开始计时（已在计时则不重置起点）；返回是否改动，供显式写列判断 */
    private static boolean startTiming(WorkOrderOperation op, LocalDateTime now) {
        if (op.getTimingSince() != null) return false;
        op.setTimingSince(now);
        return true;
    }

    /**
     * 停表并结算：actualHours += (now − timingSince)，随后清空计时起点。
     * 精度到秒 → 换算成小时保留 4 位；时长 &lt;= 0 只停表不累加（防脏数据）。
     */
    private static boolean stopTiming(WorkOrderOperation op, LocalDateTime now) {
        if (op.getTimingSince() == null) return false;
        long secs = java.time.Duration.between(op.getTimingSince(), now).getSeconds();
        if (secs > 0) {
            BigDecimal add = BigDecimal.valueOf(secs)
                    .divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP);
            op.setActualHours(nvl(op.getActualHours()).add(add));
        }
        op.setTimingSince(null);
        return true;
    }

    /**
     * 工序附件串归一（第 2 版 2026-10-07）：接受 List 或逗号分隔字符串，
     * 去空白、去重、保序，最多保留 20 个。
     * <b>清空时必须返回空串而不是 null</b>：MyBatis-Plus 默认 FieldStrategy=NOT_NULL，
     * 字段为 null 时不会写库 → 删掉最后一个附件再保存，库里旧的附件串还在（实测踩到）。
     */
    private static String normalizeAttachments(Object raw) {
        java.util.List<String> urls = new java.util.ArrayList<>();
        if (raw instanceof java.util.List<?> list) {
            for (Object o : list) {
                if (o != null && !String.valueOf(o).isBlank()) urls.add(String.valueOf(o).trim());
            }
        } else if (raw != null && !String.valueOf(raw).isBlank()) {
            for (String s : String.valueOf(raw).split(",")) {
                if (!s.isBlank()) urls.add(s.trim());
            }
        }
        java.util.List<String> uniq = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(urls));
        if (uniq.size() > 20) uniq = uniq.subList(0, 20);
        return String.join(",", uniq);
    }

    @Override
    @Transactional
    public void driveByBill(Long operationRowId, String targetStatus, String reason, String remark) {
        WorkOrderOperation op = getById(operationRowId);
        if (op == null) return;
        String from = op.getOperationStatus();
        if (WorkOperationStatus.isTerminal(from) || from.equals(targetStatus)) return;
        op.setOperationStatus(targetStatus);
        if (WorkOperationStatus.FINISHED.equals(targetStatus) && op.getActualEndDate() == null) {
            op.setActualEndDate(LocalDate.now());
        }
        if (op.getActualStartDate() == null && !WorkOperationStatus.PENDING_ASSIGN.equals(targetStatus)) {
            op.setActualStartDate(LocalDate.now());
        }
        updateById(op);
        // 单据驱动也留痕（守则 11：单据回写才是"真状态"，必须可追溯）
        writeLog(op, WorkOperationStatus.ACT_DRIVEN, from, targetStatus, null, null, null, reason, remark);
    }

    // ---------------- 第 4 期：回退确认 / 判废确认 / 下推补料 ----------------

    @Override
    @Transactional
    public WorkOrderOperation confirmRework(Long operationRowId, BigDecimal quantity, String remark,
                                            Long operatorId, String operatorName) {
        return recordScrapOrRework(operationRowId, WorkOperationStatus.ACT_REWORK, quantity, remark, operatorId, operatorName);
    }

    @Override
    @Transactional
    public WorkOrderOperation confirmScrap(Long operationRowId, BigDecimal quantity, String remark,
                                           Long operatorId, String operatorName) {
        return recordScrapOrRework(operationRowId, WorkOperationStatus.ACT_SCRAP, quantity, remark, operatorId, operatorName);
    }

    /**
     * 回退/判废共用的数量登记：只累计数量 + 留痕，**不动工序状态**。
     * 因此不套 {@link WorkOperationStatus#isTerminal} 守卫 —— 已完工的工序也可能被下道退回。
     */
    private WorkOrderOperation recordScrapOrRework(Long operationRowId, String action, BigDecimal quantity,
                                                   String remark, Long operatorId, String operatorName) {
        WorkOrderOperation op = getById(operationRowId);
        if (op == null) throw new BusinessException("工序不存在");
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(action + "数量必须大于 0");
        }
        if (WorkOperationStatus.ACT_REWORK.equals(action)) {
            op.setReworkQuantity(nvl(op.getReworkQuantity()).add(quantity));
        } else {
            op.setScrapQuantity(nvl(op.getScrapQuantity()).add(quantity));
        }
        updateById(op);
        // 数量登记不改状态：from = to = 当前状态（日志里一眼能看出"只记数量、未流转"）
        String st = op.getOperationStatus();
        writeLog(op, action, st, st, quantity, null, operatorId, operatorName, remark);
        return op;
    }

    @Override
    @Transactional
    public String pushSupplement(Long operationRowId, String remark, Long operatorId, String operatorName) {
        WorkOrderOperation op = getById(operationRowId);
        if (op == null) throw new BusinessException("工序不存在");

        BigDecimal scrap = nvl(op.getScrapQuantity());
        BigDecimal pushed = nvl(op.getScrapPushedQuantity());
        BigDecimal pending = scrap.subtract(pushed);
        if (pending.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("该工序没有待下推的判废数量（已判废 " + plain(scrap)
                    + " / 已下推 " + plain(pushed) + "）");
        }
        WorkOrder wo = op.getOrderId() == null ? null : workOrderMapper.selectById(op.getOrderId());
        if (wo == null) throw new BusinessException("未找到对应生产工单，无法下推补料");

        String who = (operatorName == null || operatorName.isBlank()) ? "" : operatorName;
        String billNo = supplementHelper.generateSupplement(wo, pending,
                "工序[" + op.getOperationName() + "]回退判废" + plain(pending) + "件，由" + who + "下推补料",
                "生产工单 " + wo.getOrderNo() + " 工序[" + op.getOperationName() + "]回退判废" + plain(pending)
                        + "件，车间下推补料" + (remark == null || remark.isBlank() ? "" : "（" + remark + "）"));
        if (billNo == null) {
            throw new BusinessException("未生成补料单：请检查该成品是否配置了主BOM及其子件用量");
        }

        op.setScrapPushedQuantity(pushed.add(pending));
        op.setSupplementBillNo(billNo);
        updateById(op);
        String st = op.getOperationStatus();
        writeLog(op, WorkOperationStatus.ACT_PUSH_SUPPLEMENT, st, st, pending, null, operatorId, operatorName,
                (remark == null || remark.isBlank() ? "" : remark + "；") + "生成补料领料单 " + billNo);
        return billNo;
    }

    // ---------------- 第 11 期：入库类型工序一键发起入库单 / 删除已取消工序 ----------------

    @Override
    @Transactional
    public Map<String, Object> pushReceipt(Long operationRowId, BigDecimal quantity, Long operatorId, String operatorName) {
        WorkOrderOperation op = getById(operationRowId);
        if (op == null) throw new BusinessException("工序不存在");
        if (op.getOrderId() == null) throw new BusinessException("该工序未关联生产工单，无法发起入库单");
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("请输入大于 0 的入库数量");
        }
        // 剩余可入库量 = 该工序合格产出 − 已下推入库量（按来源工序行汇总），用于防重复
        BigDecimal produced = nvl(op.getOutputQuantity());
        BigDecimal pushed = productionReceiptMapper.sumPushedByOperation(operationRowId);
        BigDecimal remaining = produced.subtract(pushed == null ? BigDecimal.ZERO : pushed);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("该工序没有可入库数量");
        }
        if (quantity.compareTo(remaining) > 0) {
            throw new BusinessException("入库数量超过该工序剩余可入库量（剩余 " + plain(remaining) + "）");
        }

        // 复用工单级「下推入库单」：生成成品入库单（草稿），数量取入参，来源记录该工序行
        ProductionReceipt receipt = workOrderService.pushToProductionReceipt(op.getOrderId(), quantity, operationRowId);

        // 留痕：发起入库单不改工序状态，只写一条操作日志
        String st = op.getOperationStatus();
        writeLog(op, WorkOperationStatus.ACT_PUSH_RECEIPT, st, st, quantity, null, operatorId, operatorName,
                "生成入库单 " + receipt.getReceiptNo() + "（数量 " + plain(quantity) + "）");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("billNo", receipt.getReceiptNo());
        data.put("operation", op);
        return data;
    }

    /**
     * 入库类型工序完工 → 自动生成草稿生产入库单（2026-11-06 用户口径，写死不做设置开关）。
     *
     * <p>数量 = 工序合格产出 − 已入库量（明细行口径，已作废不计）；差值 ≤ 0 不生成，避免重复入库。
     * 生成失败只打 warn 日志，不影响工序完工本身。</p>
     */
    private void autoPushReceiptOnFinish(WorkOrderOperation op, Long operatorId, String operatorName) {
        if (op.getOrderId() == null) return;
        // 合格产出取值顺序：已报工产出 → 检验合格数 → 计划数量
        // （入库类型工序不参与「数量报工」，很多单子只有计划量/上道合格数，取 0 会导致永远不生成）
        BigDecimal produced = nvl(op.getOutputQuantity());
        if (produced.compareTo(BigDecimal.ZERO) <= 0) produced = nvl(op.getApproveQty());
        if (produced.compareTo(BigDecimal.ZERO) <= 0) produced = nvl(op.getPlanQuantity());
        BigDecimal pushed = productionReceiptMapper.sumPushedByOperation(op.getId());
        BigDecimal remaining = produced.subtract(nvl(pushed));
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) return;
        try {
            ProductionReceipt receipt = workOrderService.pushToProductionReceipt(
                    op.getOrderId(), remaining, op.getId());
            writeLog(op, WorkOperationStatus.ACT_PUSH_RECEIPT, op.getOperationStatus(), op.getOperationStatus(),
                    remaining, null, operatorId, operatorName,
                    "完工自动生成入库单 " + receipt.getReceiptNo() + "（数量 " + plain(remaining) + "）");
            log.info("工序 {} 完工自动生成入库单 {}", op.getId(), receipt.getReceiptNo());
        } catch (Exception e) {
            log.warn("工序 {} 完工自动生成入库单失败: {}", op.getId(), e.getMessage());
        }
    }

    @Override
    @Transactional
    public void deleteOperation(Long operationRowId) {
        WorkOrderOperation op = getById(operationRowId);
        if (op == null) return;
        // 只允许删除「已取消」的工序（已生效工单的其它工序一律锁死，防止误删在制数据）
        if (!WorkOperationStatus.CANCELLED.equals(op.getOperationStatus())) {
            throw new BusinessException("只有「已取消」的工序才能删除");
        }
        // 操作日志只增不删：删工序行，保留其历史日志（追溯口径）
        removeById(operationRowId);
        // 【2026-11-25】工序行删除后同步清理其自定义字段值，避免孤儿数据
        try {
            customFieldService.deleteFieldValues("work_order_operation", operationRowId);
        } catch (Exception e) {
            log.warn("删除工序自定义字段值失败（工序已删除） opId={}: {}", operationRowId, e.getMessage());
        }
    }

    /**
     * 【2026-11-25】保存工序自定义字段值（仅接受 Map 结构）。
     * 字段定义在「设置中心 → 自定义字段配置 → 生产工序」维护；值统一存通用自定义字段值表。
     */
    private void saveOperationCustomFields(Long operationId, Object raw) {
        if (operationId == null || !(raw instanceof Map<?, ?> m)) return;
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() == null) continue;
            String key = String.valueOf(e.getKey());
            if (!key.isBlank()) values.put(key, e.getValue());
        }
        if (values.isEmpty()) return;
        customFieldService.saveFieldValues("work_order_operation", operationId, values);
    }

    // ---------------- 第 12 期：工序检验（质检模板 + 检验记录 + 合格数驱动下道） ----------------

    @Override
    public QcTemplate resolveQcTemplate(Long operationRowId) {
        return qcTemplateService.resolveTemplateForOperation(operationRowId);
    }

    /**
     * 提交工序检验（「完工待检」状态下按模板填质检）。
     *
     * <p><b>三种质检类型的判定公式（唯一口径，代码即事实来源）：</b></p>
     * <ul>
     *   <li><b>首检</b>：检验项全部合格 → 整批放行（合格数 = 报工数量）；任一必填项不合格 →
     *       整批不合格（合格数 0、不合格数 = 报工数量）。</li>
     *   <li><b>抽检</b>：抽样数 = 提交的检验数（未提交则 = 报工数量 × 抽样比例 / 100，比例也为 0 时按报工数量）；
     *       判定不良数 = 提交的不合格数（未提交则 = 实测不合格项数）；
     *       不良率(%) = 判定不良数 / 抽样数 × 100；<b>不良率 ≤ 允许不良率</b> → 放行
     *       （合格数 = 报工数量 − 判定不良数）；否则整批不合格（合格数 0、不合格数 = 报工数量）。</li>
     *   <li><b>全检</b>：合格数 = 提交的合格数（未提交则 = 报工数量 − 实测不合格项数）；
     *       不合格数 = 提交的不合格数（未提交则 = 报工数量 − 合格数）；不合格数 = 0 才判合格。</li>
     * </ul>
     *
     * <p>判定合格 → 走统一状态机的「检验通过」推入「已完工」，并把合格数写到下一道的可开工数量；
     * 判定不合格 → 先按所选处理方式（补料 / 返工回退 / 报废）处置，再走「检验退回」回到「进行中」。</p>
     *
     * <p>操作人一律取当前登录态（不接受前端传值）。</p>
     *
     * @return { operation: 变更后的工序行, record: 检验记录, result: 合格/不合格, handleBillNo: 补料单号(可空) }
     */
    @Override
    @Transactional
    public Map<String, Object> submitInspection(Long operationRowId, Map<String, Object> payload,
                                                Long operatorId, String operatorName) {
        WorkOrderOperation op = getById(operationRowId);
        if (op == null) throw new BusinessException("工序不存在");
        if (op.getIsQcRequired() == null || op.getIsQcRequired() != 1) {
            throw new BusinessException("该工序未开启「需质检」，无需填写质检");
        }
        String from = op.getOperationStatus();
        if (!WorkOperationStatus.WAIT_QC.equals(from)) {
            throw new BusinessException("当前状态[" + (from == null ? "待派工" : from)
                    + "]不能填质检，请先完工到「完工待检」");
        }
        Map<String, Object> body = payload == null ? Map.of() : payload;

        // 解析该工序应使用的模板：工单工序显式绑定 → 按工序绑定 → 按物料绑定；都没有则报错
        QcTemplate template = qcTemplateService.resolveTemplateForOperation(operationRowId);
        if (template == null) {
            throw new BusinessException("未找到该工序的质检模板：请先在「基础资料 → 质检模板」按工序或按物料配置并启用");
        }
        String qcType = (template.getQcType() == null || template.getQcType().isBlank()) ? "首检" : template.getQcType();

        // 报工数量（整批判定的基数）；报工为 0 时回落计划数量
        BigDecimal reportQty = nvl(op.getReportedQuantity());
        if (reportQty.compareTo(BigDecimal.ZERO) <= 0) reportQty = nvl(op.getPlanQuantity());

        // 1) 逐项实测明细（必填/上下限取模板定义；实测值取提交内容）
        List<QcRecordItem> recordItems = buildRecordItems(template, body);
        int failedItems = 0;
        for (QcRecordItem it : recordItems) {
            if ("不合格".equals(it.getResult())) failedItems++;
        }

        // 2) 按质检类型判定
        BigDecimal inspectQty;
        BigDecimal qualifiedQty;
        BigDecimal defectQty;
        boolean pass;
        switch (qcType) {
            case "抽检" -> {
                BigDecimal ratio = nvl(template.getSampleRatio());
                BigDecimal providedSample = toDecimalOrNull(body.get("inspectQty"));
                inspectQty = (providedSample != null && providedSample.compareTo(BigDecimal.ZERO) > 0)
                        ? providedSample
                        : (ratio.compareTo(BigDecimal.ZERO) > 0
                            ? reportQty.multiply(ratio).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP).max(BigDecimal.ONE)
                            : reportQty);
                BigDecimal providedDefect = toDecimalOrNull(body.get("defectQty"));
                BigDecimal defect = (providedDefect != null && providedDefect.compareTo(BigDecimal.ZERO) > 0)
                        ? providedDefect
                        : BigDecimal.valueOf(Math.min(failedItems, inspectQty.intValue()));
                defect = defect.min(inspectQty);
                BigDecimal allowed = nvl(template.getAllowedDefectRatio());
                BigDecimal defectRate = inspectQty.compareTo(BigDecimal.ZERO) > 0
                        ? defect.multiply(new BigDecimal("100")).divide(inspectQty, 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                pass = defectRate.compareTo(allowed) <= 0;
                if (pass) {
                    qualifiedQty = reportQty.subtract(defect).max(BigDecimal.ZERO);
                    defectQty = defect;
                } else {
                    qualifiedQty = BigDecimal.ZERO;
                    defectQty = reportQty;
                }
            }
            case "全检" -> {
                inspectQty = reportQty;
                BigDecimal providedOk = toDecimalOrNull(body.get("qualifiedQty"));
                BigDecimal providedNg = toDecimalOrNull(body.get("defectQty"));
                qualifiedQty = (providedOk != null && providedOk.compareTo(BigDecimal.ZERO) >= 0)
                        ? providedOk
                        : reportQty.subtract(BigDecimal.valueOf(failedItems)).max(BigDecimal.ZERO);
                defectQty = (providedNg != null && providedNg.compareTo(BigDecimal.ZERO) > 0)
                        ? providedNg
                        : reportQty.subtract(qualifiedQty).max(BigDecimal.ZERO);
                pass = defectQty.compareTo(BigDecimal.ZERO) <= 0;
            }
            default -> {   // 首检：只检首件/首批，检验项全部合格 → 整批放行
                inspectQty = reportQty;
                pass = failedItems == 0;
                qualifiedQty = pass ? reportQty : BigDecimal.ZERO;
                defectQty = pass ? BigDecimal.ZERO : reportQty;
            }
        }
        String result = pass ? "合格" : "不合格";

        // 3) 不合格 → 处理方式必填（补料 / 返工回退 / 报废）
        String handleType = toStr(body.get("handleType"));
        String handleOperationName = toStr(body.get("handleOperationName"));
        String handleRemark = toStr(body.get("handleRemark"));
        if (!pass) {
            if (handleType == null || handleType.isBlank()) {
                throw new BusinessException("判定不合格，请选择处理方式（补料 / 返工回退 / 报废）");
            }
            if (!List.of("补料", "返工回退", "报废").contains(handleType)) {
                throw new BusinessException("不合格处理方式只能是 补料 / 返工回退 / 报废");
            }
            if ("返工回退".equals(handleType) && (handleOperationName == null || handleOperationName.isBlank())) {
                throw new BusinessException("请选择返工回退的目标工序");
            }
        } else {
            handleType = null;
            handleOperationName = null;
        }

        // 4) 落检验记录 + 实测明细
        QcRecord record = new QcRecord();
        record.setRecordNo(nextRecordNo());
        record.setOrderId(op.getOrderId());
        record.setOrderNo(op.getOrderNo());
        record.setOperationRowId(operationRowId);
        record.setOperationName(op.getOperationName());
        record.setTemplateId(template.getTemplateId());
        record.setQcType(qcType);
        record.setInspectQty(inspectQty);
        record.setQualifiedQty(qualifiedQty);
        record.setDefectQty(defectQty);
        record.setResult(result);
        record.setHandleType(handleType);
        record.setHandleOperationName(handleOperationName);
        record.setHandleRemark(handleRemark);
        record.setInspectorId(operatorId);
        record.setInspectorName(operatorName);
        record.setInspectTime(LocalDateTime.now());
        record.setRemark(toStr(body.get("remark")));
        qcRecordMapper.insert(record);
        for (QcRecordItem it : recordItems) {
            it.setRecordId(record.getRecordId());
            qcRecordItemMapper.insert(it);
        }

        // 5) 合格数写回工序（= 下一道可开工数量，供下道卡量）
        op.setApproveQty(qualifiedQty);
        updateById(op);

        String summary = qcSummaryRemark(qcType, result, inspectQty, qualifiedQty, defectQty, template);
        String handleBillNo = null;

        if (pass) {
            // 合格 → 「检验通过」推入「已完工」；同时把合格数传给下一道
            Map<String, Object> passPayload = new LinkedHashMap<>();
            passPayload.put("remark", summary);
            act(operationRowId, WorkOperationStatus.ACT_QC_PASS, passPayload, operatorId, operatorName);
            propagateApproveQtyToNext(op, qualifiedQty);
        } else {
            // 不合格 → 先按所选方式处置，再走「检验退回」回到「进行中」
            switch (handleType) {
                case "补料" -> {
                    WorkOrder wo = op.getOrderId() == null ? null : workOrderMapper.selectById(op.getOrderId());
                    if (wo == null) throw new BusinessException("未找到对应生产工单，无法生成补料单");
                    String billNo = supplementHelper.generateSupplement(wo, defectQty,
                            "工序[" + op.getOperationName() + "]质检不合格" + plain(defectQty) + "件补料",
                            "生产工单 " + wo.getOrderNo() + " 工序[" + op.getOperationName() + "]质检不合格"
                                    + plain(defectQty) + "件，按BOM下推补料");
                    if (billNo == null) {
                        throw new BusinessException("未生成补料单：请检查该成品是否配置了主BOM及其子件用量");
                    }
                    handleBillNo = billNo;
                    record.setHandleRemark((handleRemark == null || handleRemark.isBlank() ? "" : handleRemark + "；")
                            + "补料领料单 " + billNo);
                    qcRecordMapper.updateById(record);
                }
                case "返工回退" -> {
                    WorkOrderOperation target = findOperationByName(op, handleOperationName);
                    if (target == null) {
                        throw new BusinessException("未找到返工目标工序[" + handleOperationName + "]，请重新选择");
                    }
                    confirmRework(target.getId(), defectQty,
                            "由工序[" + op.getOperationName() + "]质检不合格回退" + plain(defectQty) + "件返工",
                            operatorId, operatorName);
                }
                case "报废" -> {
                    op.setRejectedQuantity(nvl(op.getRejectedQuantity()).add(defectQty));
                    op.setScrapQuantity(nvl(op.getScrapQuantity()).add(defectQty));
                    updateById(op);
                    writeLog(op, WorkOperationStatus.ACT_QC_HANDLE_SCRAP, op.getOperationStatus(), op.getOperationStatus(),
                            defectQty, defectQty, operatorId, operatorName,
                            "质检不合格报废" + plain(defectQty) + "件");
                }
                default -> {
                    // 处理方式已在上面校验过，不会走到这里
                }
            }
            Map<String, Object> rejectPayload = new LinkedHashMap<>();
            rejectPayload.put("remark", summary + "；处理方式：" + handleType
                    + (handleBillNo == null ? "" : "（补料领料单 " + handleBillNo + "）"));
            act(operationRowId, WorkOperationStatus.ACT_QC_REJECT, rejectPayload, operatorId, operatorName);
        }

        // 6) 留痕：填质检（本身不改状态，只记录本次检验数量与判定，便于追溯）
        WorkOrderOperation latest = getById(operationRowId);
        writeLog(latest, WorkOperationStatus.ACT_QC_SUBMIT, from, latest.getOperationStatus(),
                qualifiedQty, defectQty, operatorId, operatorName,
                summary + (handleBillNo == null ? "" : "；补料领料单 " + handleBillNo));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("record", record);
        data.put("result", result);
        data.put("handleBillNo", handleBillNo);
        data.put("operation", latest);
        return data;
    }

    /** 逐项实测明细：以模板检验项为准（必填/上下限取模板），实测值取提交内容；模板外额外填报项也留档 */
    private List<QcRecordItem> buildRecordItems(QcTemplate template, Map<String, Object> body) {
        Map<String, String> measured = new LinkedHashMap<>();
        // 【第 19 期】逐项留证图片：itemName → 逗号分隔图片 URL（可空，不强制）
        Map<String, String> imagesByName = new LinkedHashMap<>();
        Object raw = body.get("items");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    String name = toStr(m.get("itemName"));
                    if (name != null && !name.isBlank()) {
                        measured.put(name, toStr(m.get("measuredValue")));
                        imagesByName.put(name, joinImages(m.get("images")));
                    }
                }
            }
        }
        List<QcRecordItem> rows = new ArrayList<>();
        List<QcTemplateItem> tplItems = template.getItems() == null ? List.of() : template.getItems();
        int idx = 0;
        for (QcTemplateItem ti : tplItems) {
            if (ti == null || ti.getItemName() == null || ti.getItemName().isBlank()) continue;
            idx++;
            QcRecordItem row = new QcRecordItem();
            row.setLineNo(ti.getLineNo() != null ? ti.getLineNo() : idx * 10);
            row.setItemName(ti.getItemName());
            row.setStandardValue(ti.getStandardValue());
            row.setUpperLimit(ti.getUpperLimit());
            row.setLowerLimit(ti.getLowerLimit());
            String mv = measured.get(ti.getItemName());
            row.setMeasuredValue(mv);
            row.setImages(imagesByName.get(ti.getItemName()));
            row.setResult(judgeItem(mv, ti.getUpperLimit(), ti.getLowerLimit(),
                    ti.getIsRequired() != null && ti.getIsRequired() == 1));
            rows.add(row);
        }
        // 模板之外额外填报的项也一并留档（不丢数据）
        for (Map.Entry<String, String> e : measured.entrySet()) {
            boolean exists = rows.stream().anyMatch(r -> e.getKey().equals(r.getItemName()));
            if (!exists && e.getValue() != null && !e.getValue().isBlank()) {
                QcRecordItem row = new QcRecordItem();
                row.setLineNo((rows.size() + 1) * 10);
                row.setItemName(e.getKey());
                row.setMeasuredValue(e.getValue());
                row.setImages(imagesByName.get(e.getKey()));
                row.setResult("合格");
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * 【第 19 期】把提交上来的留证图片归一成"逗号分隔 URL 串"（与全站附件列口径一致）：
     * 支持前端传数组（List）或已是逗号串；空值返回 null。
     */
    private String joinImages(Object raw) {
        if (raw == null) return null;
        if (raw instanceof List<?> list) {
            List<String> urls = new ArrayList<>();
            for (Object o : list) {
                String s = toStr(o);
                if (s != null && !s.isBlank()) urls.add(s.trim());
            }
            return urls.isEmpty() ? null : String.join(",", urls);
        }
        String s = toStr(raw);
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /**
     * 单项判定：实测值落在 [下限, 上限] 内为合格；上下限都为空时只要能填到值即视为合格（描述性标准）；
     * 必填项没填 → 不合格（无法确认即视为不合格）。
     */
    private static String judgeItem(String measured, BigDecimal upper, BigDecimal lower, boolean required) {
        if (measured == null || measured.isBlank()) return required ? "不合格" : "合格";
        BigDecimal v = parseDecimal(measured);
        if (v == null) {
            // 非数值描述性结果：有上下限时无法做数值范围判定 → 计不合格；否则视为合格
            return (upper != null || lower != null) ? "不合格" : "合格";
        }
        if (lower != null && v.compareTo(lower) < 0) return "不合格";
        if (upper != null && v.compareTo(upper) > 0) return "不合格";
        return "合格";
    }

    /** 解析数值：非数值返回 null（不抛异常，用于"描述性实测值"场景） */
    private static BigDecimal parseDecimal(String s) {
        try {
            return new BigDecimal(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 检验单号：QCR + 时间戳（毫秒级，单机唯一；不依赖编号规则配置，避免未配置时抛错） */
    private static String nextRecordNo() {
        return "QCR" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    /** 质检摘要（写操作日志 + 检验记录备注复用的同一段文案） */
    private static String qcSummaryRemark(String qcType, String result, BigDecimal inspectQty,
                                          BigDecimal qualifiedQty, BigDecimal defectQty, QcTemplate template) {
        return "质检类型：" + qcType
                + "；模板：" + (template.getTemplateName() == null ? "" : template.getTemplateName())
                + "；判定：" + result
                + "；检验数 " + plain(inspectQty)
                + " / 合格数 " + plain(qualifiedQty)
                + " / 不合格数 " + plain(defectQty);
    }

    /** 质检通过后把合格数写到「下一道工序」的可开工数量（页面展示 + 下道卡量同源） */
    private void propagateApproveQtyToNext(WorkOrderOperation op, BigDecimal approveQty) {
        WorkOrderOperation next = nextOperation(op);
        if (next == null) return;
        next.setCanStartQty(approveQty);
        updateById(next);
    }

    /** 同一工单里行号大于本工序的最前一道（= 后道工序） */
    private WorkOrderOperation nextOperation(WorkOrderOperation op) {
        if (op.getOrderId() == null || op.getLineNo() == null) return null;
        return getOne(new QueryWrapper<WorkOrderOperation>()
                .eq("order_id", op.getOrderId())
                .gt("line_no", op.getLineNo())
                .orderByAsc("line_no")
                .last("LIMIT 1"));
    }

    /** 按工序名称在同工单（有成品行归属时限定同一成品行）内找目标工序，用于返工回退 */
    private WorkOrderOperation findOperationByName(WorkOrderOperation op, String name) {
        if (op.getOrderId() == null || name == null || name.isBlank()) return null;
        QueryWrapper<WorkOrderOperation> qw = new QueryWrapper<WorkOrderOperation>()
                .eq("order_id", op.getOrderId())
                .eq("operation_name", name);
        if (op.getId() != null) qw.ne("id", op.getId());
        if (op.getOrderProductId() != null) qw.eq("order_product_id", op.getOrderProductId());
        qw.orderByAsc("line_no").last("LIMIT 1");
        return getOne(qw);
    }

    /**
     * 下道卡量：上道工序做过质检（approve_qty 非空）时，本道报工/开工数量不得超过"上道合格数"。
     *
     * <p>没有上道（首道工序）或上道没做质检（approve_qty 为空）→ 不限制（与改造前行为完全一致）。</p>
     */
    private void assertWithinPrevApprove(WorkOrderOperation op, String action, BigDecimal quantity, Map<String, Object> body) {
        WorkOrderOperation prev = prevOperation(op);
        if (prev == null) return;                  // 首道工序：按计划数量，不受此限制
        BigDecimal avail = prev.getApproveQty();
        if (avail == null) return;                 // 上道未做质检 → 不卡量
        if (WorkOperationStatus.ACT_REPORT.equals(action)) {
            BigDecimal after = nvl(op.getReportedQuantity()).add(quantity);
            if (after.compareTo(avail) > 0) {
                throw new BusinessException("报工数量超过上道合格数：上道工序[" + prev.getOperationName()
                        + "]质检合格数 " + plain(avail) + "，本道累计报工 " + plain(after));
            }
        } else {
            BigDecimal input = toDecimalOrNull(body.get("inputQuantity"));
            if (input != null && input.compareTo(avail) > 0) {
                throw new BusinessException("投入数量超过上道合格数：上道工序[" + prev.getOperationName()
                        + "]质检合格数 " + plain(avail) + "，本次投入 " + plain(input));
            }
        }
    }

    // ==================== 【2026-10-25】扫码认领与按人报工 ====================

    /** 生产模块权限：管理员放行；普通账号必须有 /production 菜单路径（无权限或菜单为空一律拒绝） */
    private void assertProductionPermission() {
        HttpServletRequest req = currentRequest();
        if (req == null) return; // 内部调用（无 HTTP 上下文）不拦
        if (SecurityUtils.isAdmin(req) || SecurityUtils.hasAnyRole(req, "SUPER_ADMIN")) return;
        List<String> paths = SecurityUtils.getCurrentMenuPaths(req);
        if (paths != null) {
            for (String p : paths) {
                if (p == null) continue;
                if ("*".equals(p) || p.startsWith("/production")) return;
            }
        }
        throw new BusinessException("没有生产模块权限，无法操作工序");
    }

    /** 当前登录人对应的在职员工；未绑定/离职返回 null（调用方给中文原因） */
    private Employee currentWorkerEmployee(Long operatorId) {
        Long empId = currentEmployeeId(currentRequest(), operatorId);
        if (empId == null) return null;
        Employee emp = employeeMapper.selectById(empId);
        if (emp == null) return null;
        if (emp.getStatus() != null && !emp.getStatus().isBlank() && !"在职".equals(emp.getStatus())) return null;
        return emp;
    }

    /** 不能操作的原因（null = 可以）；原因直接展示给用户 */
    private String workerBlockReason(WorkOrderOperation op) {
        if (op == null) return "该工序不存在或已被删除";
        WorkOrder wo = op.getOrderId() == null ? null : workOrderMapper.selectById(op.getOrderId());
        if (wo == null) return "所属生产工单不存在";
        String billStatus = (wo.getApprovalStatus() != null && !wo.getApprovalStatus().isBlank())
                ? wo.getApprovalStatus() : wo.getStatus();
        if (billStatus != null && !billStatus.contains("已生效")) {
            return "工单尚未生效（当前：" + billStatus + "），不能操作";
        }
        if (wo.getIsClosed() != null && wo.getIsClosed() == 1) return "工单已结案，不能再操作";
        String st = op.getOperationStatus() == null ? WorkOperationStatus.PENDING_ASSIGN : op.getOperationStatus();
        if (WorkOperationStatus.FINISHED.equals(st)) return "工序已完工，不能再操作";
        if (WorkOperationStatus.CANCELLED.equals(st)) return "工序已取消，不能再操作";
        if (WorkOperationStatus.WAIT_QC.equals(st)) return "工序已完工待检，不能再操作";
        return null;
    }

    /** 工序是否允许扫码认领（工序级开关；空 = 是） */
    private boolean scanClaimAllowed(WorkOrderOperation op) {
        RoutingOperation ro = routingOperationOf(op);
        String v = ro == null ? null : ro.getAllowScanClaim();
        return !"否".equals(v == null ? "" : v.trim());
    }

    /** 【2026-10-30】认领即视为已有人做：状态停在「待派工」时推进为「已派工」（更后的状态不动） */
    private void markAssignedIfPending(WorkOrderOperation op) {
        if (op == null) return;
        if (op.getOperationStatus() == null || WorkOperationStatus.PENDING_ASSIGN.equals(op.getOperationStatus())) {
            op.setOperationStatus(WorkOperationStatus.ASSIGNED);
            updateById(op);
        }
    }

    /** 在岗的共同负责人 */
    private List<WorkOrderOperationWorker> activeWorkers(Long operationRowId) {
        if (operationRowId == null) return new ArrayList<>();
        return operationWorkerMapper.selectList(new QueryWrapper<WorkOrderOperationWorker>()
                .eq("operation_row_id", operationRowId).eq("status", "在岗").orderByAsc("id"));
    }

    /** 某人在本道工序的有效报工合计（流水汇总） */
    private BigDecimal sumFlow(Long operationRowId, Long employeeId) {
        if (operationRowId == null || employeeId == null) return BigDecimal.ZERO;
        List<OperationReportFlow> rows = operationReportFlowMapper.selectList(new QueryWrapper<OperationReportFlow>()
                .eq("operation_row_id", operationRowId).eq("employee_id", employeeId).eq("flow_status", "有效"));
        BigDecimal sum = BigDecimal.ZERO;
        for (OperationReportFlow f : rows) sum = sum.add(nvl(f.getQuantity()));
        return sum;
    }

    /** 本道工序还可认领多少（计划 − 在岗认领 − 已报）；计划<=0 返回 null = 不设上限 */
    private BigDecimal remainingClaimable(WorkOrderOperation op) {
        BigDecimal plan = nvl(op.getPlanQuantity());
        if (plan.compareTo(BigDecimal.ZERO) <= 0) return null;
        BigDecimal claimed = BigDecimal.ZERO;
        for (WorkOrderOperationWorker w : activeWorkers(op.getId())) {
            if (w.getClaimQuantity() != null) claimed = claimed.add(w.getClaimQuantity());
        }
        BigDecimal remain = plan.subtract(claimed).subtract(nvl(op.getReportedQuantity()));
        return remain.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remain;
    }

    /** 在岗参与人 + 主负责人（不在参与人表时补一行展示，不改库） */
    private List<WorkOrderOperationWorker> buildWorkers(WorkOrderOperation op) {
        List<WorkOrderOperationWorker> list = activeWorkers(op == null ? null : op.getId());
        if (op != null && op.getAssigneeId() != null) {
            boolean hit = list.stream().anyMatch(w -> op.getAssigneeId().equals(w.getEmployeeId()));
            if (!hit) {
                WorkOrderOperationWorker primary = new WorkOrderOperationWorker();
                primary.setOperationRowId(op.getId());
                primary.setEmployeeId(op.getAssigneeId());
                primary.setEmployeeName(op.getAssigneeName());
                primary.setPrimaryAssignee(true);
                primary.setStatus("在岗");
                primary.setReportedQuantity(sumFlow(op.getId(), op.getAssigneeId()));
                list.add(0, primary);
            }
        }
        for (WorkOrderOperationWorker w : list) {
            // 【2026-11-26】主负责人若同时是共同负责人，也必须标出「主」，否则多选名单看不出谁是主
            if (op != null && op.getAssigneeId() != null && op.getAssigneeId().equals(w.getEmployeeId())) {
                w.setPrimaryAssignee(true);
            } else if (w.getPrimaryAssignee() == null) {
                w.setPrimaryAssignee(false);
            }
            if (w.getClaimQuantity() != null) {
                BigDecimal remain = w.getClaimQuantity().subtract(nvl(w.getReportedQuantity()));
                w.setRemainingQuantity(remain.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remain);
            }
        }
        return list;
    }

    /**
     * 【2026-11-26】工序操作界面「负责人多选」：一次提交整份名单。
     * 第一位为主负责人，其余同步为共同负责人；移出名单的人置为「已退出」。
     */
    @Override
    @Transactional
    public WorkOrderOperation setAssignees(Long operationRowId, List<Long> employeeIds,
                                           Long operatorId, String operatorName) {
        assertProductionPermission();
        WorkOrderOperation op = getById(operationRowId);
        if (op == null) throw new BusinessException("工序不存在");
        List<Long> ids = new ArrayList<>();
        if (employeeIds != null) {
            for (Long id : employeeIds) {
                if (id != null && !ids.contains(id)) ids.add(id);
            }
        }
        if (ids.isEmpty()) throw new BusinessException("请至少选择一位负责人");
        String from = op.getOperationStatus() == null ? WorkOperationStatus.PENDING_ASSIGN : op.getOperationStatus();
        if (!(WorkOperationStatus.PENDING_ASSIGN.equals(from) || WorkOperationStatus.ASSIGNED.equals(from)
                || WorkOperationStatus.STARTED.equals(from) || WorkOperationStatus.RUNNING.equals(from)
                || WorkOperationStatus.PAUSED.equals(from))) {
            throw new BusinessException("当前状态[" + from + "]不能修改负责人");
        }
        assertCanChangeAssignee(op, operatorId, operatorName);

        Map<Long, Employee> empMap = new LinkedHashMap<>();
        for (Employee e : employeeMapper.selectBatchIds(ids)) empMap.put(e.getEmployeeId(), e);
        Employee first = empMap.get(ids.get(0));
        if (first == null) throw new BusinessException("负责人不存在或已删除，请重新选择");

        op.setAssigneeId(first.getEmployeeId());
        op.setAssigneeName(first.getEmployeeName());
        if (WorkOperationStatus.PENDING_ASSIGN.equals(from)) {
            op.setOperationStatus(WorkOperationStatus.ASSIGNED);
        }
        updateById(op);

        List<WorkOrderOperationWorker> actives = activeWorkers(op.getId());
        Map<Long, WorkOrderOperationWorker> byEmp = new LinkedHashMap<>();
        for (WorkOrderOperationWorker w : actives) byEmp.put(w.getEmployeeId(), w);
        for (Long empId : ids) {
            if (empId.equals(first.getEmployeeId()) || byEmp.containsKey(empId)) continue;
            Employee emp = empMap.get(empId);
            if (emp == null) continue;
            WorkOrderOperationWorker w = new WorkOrderOperationWorker();
            w.setOperationRowId(op.getId());
            w.setEmployeeId(emp.getEmployeeId());
            w.setEmployeeName(emp.getEmployeeName());
            w.setStatus("在岗");
            w.setClaimTime(java.time.LocalDateTime.now());
            w.setClaimedBy(operatorId);
            w.setClaimedByName(operatorName);
            w.setRemark("派工指定");
            operationWorkerMapper.insert(w);
        }
        for (WorkOrderOperationWorker w : actives) {
            if (!ids.contains(w.getEmployeeId())) {
                w.setStatus("已退出");
                w.setExitTime(java.time.LocalDateTime.now());
                operationWorkerMapper.updateById(w);
            }
        }

        StringBuilder names = new StringBuilder();
        for (Long empId : ids) {
            Employee e = empMap.get(empId);
            if (e == null) continue;
            if (names.length() > 0) names.append("、");
            names.append(e.getEmployeeName());
        }
        writeLog(op, WorkOperationStatus.PENDING_ASSIGN.equals(from)
                        ? WorkOperationStatus.ACT_ASSIGN : WorkOperationStatus.ACT_REASSIGN,
                from, op.getOperationStatus(), null, null, operatorId, operatorName,
                "负责人：" + names);
        return detailForWorker(op.getId(), operatorId, operatorName);
    }

    /** 填充参与人 + 当前登录人的认领/已报/剩余 + 能否认领及原因（前端直接渲染，不做第二套判断） */
    private void fillWorkerInfo(WorkOrderOperation op, Long operatorId) {
        op.setWorkers(buildWorkers(op));
        op.setAllowScanClaim(scanClaimAllowed(op) ? "是" : "否");
        op.setRemainingClaimable(remainingClaimable(op));
        Employee emp = currentWorkerEmployee(operatorId);
        String block = workerBlockReason(op);
        if (emp == null) {
            op.setCanClaim(false);
            op.setClaimDisabledReason(block != null ? block : "当前账号未绑定员工档案，请联系管理员绑定后再认领");
            op.setMyReportedQuantity(BigDecimal.ZERO);
            op.setMyJoined(false);
            return;
        }
        WorkOrderOperationWorker mine = activeWorkers(op.getId()).stream()
                .filter(w -> emp.getEmployeeId().equals(w.getEmployeeId())).findFirst().orElse(null);
        boolean iAmPrimary = op.getAssigneeId() != null && op.getAssigneeId().equals(emp.getEmployeeId());
        op.setMyJoined(mine != null || iAmPrimary);
        op.setMyClaimQuantity(mine == null ? null : mine.getClaimQuantity());
        op.setMyReportedQuantity(mine != null ? nvl(mine.getReportedQuantity()) : sumFlow(op.getId(), emp.getEmployeeId()));
        if (mine != null && mine.getClaimQuantity() != null) {
            BigDecimal remain = mine.getClaimQuantity().subtract(nvl(mine.getReportedQuantity()));
            op.setMyRemainingQuantity(remain.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remain);
        }
        String reason = block;
        if (reason == null && !scanClaimAllowed(op)) reason = "该工序未开启扫码认领，请联系管理员";
        if (reason == null && (mine != null || iAmPrimary)) reason = "你已在本道工序，无需重复认领";
        op.setCanClaim(reason == null);
        op.setClaimDisabledReason(reason);
    }

    @Override
    public List<WorkOrderOperationWorker> workers(Long operationRowId) {
        assertProductionPermission();
        WorkOrderOperation op = getById(operationRowId);
        if (op == null) throw new BusinessException("该工序不存在或已被删除");
        return buildWorkers(op);
    }

    /**
     * 【2026-11-26】工序自己的字段定义 + 已填值。
     * 定义来自工艺路线「该道工序」；老工单没有 routingOperationKey 时返回空，前端回落原全局字段口径。
     */
    private void fillOperationCustomFields(WorkOrderOperation op) {
        if (op == null) return;
        op.setCustomFields(customFieldService.getFieldValues("work_order_operation", op.getId()));
        if (op.getRoutingId() == null || op.getRoutingOperationKey() == null || op.getRoutingOperationKey().isBlank()) {
            return;
        }
        java.util.List<com.erp.production.entity.RoutingOperationField> defs =
                routingOperationFieldMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.erp.production.entity.RoutingOperationField>()
                                .eq("routing_id", op.getRoutingId())
                                .eq("operation_key", op.getRoutingOperationKey())
                                .orderByAsc("sort_no"));
        if (defs == null || defs.isEmpty()) return;
        java.util.List<java.util.Map<String, Object>> views = new java.util.ArrayList<>();
        for (com.erp.production.entity.RoutingOperationField d : defs) {
            java.util.Map<String, Object> v = new java.util.LinkedHashMap<>();
            v.put("id", d.getId());
            // 与操作面板既有字段定义结构保持一致（fieldLabel / sortOrder / precisionVal）
            v.put("fieldLabel", d.getFieldLabel());
            v.put("fieldType", d.getFieldType());
            v.put("fieldOptions", d.getFieldOptions());
            v.put("isRequired", d.getIsRequired());
            v.put("sortOrder", d.getSortNo());
            v.put("precisionVal", 2);
            String key = null;
            if (d.getCustomFieldId() != null) {
                for (com.erp.customfield.entity.CustomField cf
                        : customFieldService.listEnabledByEntity("work_order_operation")) {
                    if (d.getCustomFieldId().equals(cf.getFieldId())) {
                        key = cf.getFieldKey();
                        break;
                    }
                }
            }
            v.put("fieldKey", key == null ? ("rof_" + d.getId()) : key);
            views.add(v);
        }
        op.setOperationFieldDefs(views);
    }

    @Override
    public WorkOrderOperation detailForWorker(Long operationRowId, Long operatorId, String operatorName) {
        assertProductionPermission();
        WorkOrderOperation op = getById(operationRowId);
        if (op == null) throw new BusinessException("该工序不存在或已被删除");
        fillWorkerInfo(op, operatorId);
        // 【2026-11-26】带出本工序自己的字段定义与已填值（操作面板只显示自己的字段）
        fillOperationCustomFields(op);
        // 【2026-10-30】生产工单附件只读带入：手机端/电脑端工序页都能看到（与工单进度接口同口径）
        WorkOrder order = op.getOrderId() == null ? null : workOrderMapper.selectById(op.getOrderId());
        if (order != null) {
            op.setBillAttachments(order.getAttachmentUrls());
        }
        // 【2026-11-09 手机端便捷工序】补物料名称/编码/规格/主图：原先详情接口只回填附件，
        // 手机端看不到在做什么料；与看板接口同口径（优先所属成品明细行，回落整单物料）
        WorkOrderProduct product = op.getOrderProductId() == null ? null
                : workOrderProductMapper.selectById(op.getOrderProductId());
        op.setMaterialName(product != null && product.getMaterialName() != null
                ? product.getMaterialName() : (order == null ? null : order.getMaterialName()));
        op.setSpecification(product == null ? null : product.getSpecification());
        op.setMaterialCode(product != null && product.getMaterialCode() != null
                ? product.getMaterialCode() : (order == null ? null : order.getMaterialCode()));
        op.setMaterialId(product != null && product.getMaterialId() != null
                ? product.getMaterialId() : (order == null ? null : order.getMaterialId()));
        if (op.getMaterialId() != null) {
            com.erp.masterdata.entity.Material material = materialMapper.selectById(op.getMaterialId());
            op.setMaterialImage(material == null ? null : material.getMainImage());
        }
        return op;
    }

    @Override
    @Transactional
    public WorkOrderOperation claim(Long operationRowId, BigDecimal claimQuantity, Long operatorId, String operatorName) {
        assertProductionPermission();
        WorkOrderOperation op = lambdaQuery().eq(WorkOrderOperation::getId, operationRowId)
                .last("FOR UPDATE").one();
        if (op == null) throw new BusinessException("该工序不存在或已被删除");
        Employee emp = currentWorkerEmployee(operatorId);
        if (emp == null) throw new BusinessException("当前账号未绑定员工档案，请联系管理员绑定后再认领");
        String block = workerBlockReason(op);
        if (block != null) throw new BusinessException(block);
        if (!scanClaimAllowed(op)) throw new BusinessException("该工序未开启扫码认领，请联系管理员");

        BigDecimal qty = claimQuantity;
        if (qty != null && qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("认领数量必须大于 0（不填则不设个人上限）");
        }
        BigDecimal remain = remainingClaimable(op);
        if (qty != null && remain != null && qty.compareTo(remain) > 0) {
            throw new BusinessException("认领数量超过本道工序剩余可认领量：" + plain(remain));
        }

        // 幂等：已在岗直接返回（只更新认领数量）
        WorkOrderOperationWorker mine = activeWorkers(op.getId()).stream()
                .filter(w -> emp.getEmployeeId().equals(w.getEmployeeId())).findFirst().orElse(null);
        if (mine != null) {
            if (qty != null && qty.compareTo(nvl(mine.getClaimQuantity())) != 0) {
                mine.setClaimQuantity(qty);
                operationWorkerMapper.updateById(mine);
            }
            // 【2026-10-30】已在岗但状态仍停在「待派工」：重复认领时一并推进，保证报工可用
            markAssignedIfPending(op);
            return detailForWorker(op.getId(), operatorId, operatorName);
        }

        WorkOrderOperationWorker w = new WorkOrderOperationWorker();
        w.setOperationRowId(op.getId());
        w.setEmployeeId(emp.getEmployeeId());
        w.setEmployeeName(emp.getEmployeeName());
        w.setClaimQuantity(qty);
        w.setReportedQuantity(BigDecimal.ZERO);
        w.setStatus("在岗");
        w.setClaimTime(LocalDateTime.now());
        w.setClaimedBy(operatorId);
        w.setClaimedByName(operatorName);
        operationWorkerMapper.insert(w);

        // 原来没有负责人 → 认领人补为主负责人（否则工序推不动）；有负责人一律不覆盖
        boolean becamePrimary = false;
        if (op.getAssigneeId() == null) {
            op.setAssigneeId(emp.getEmployeeId());
            op.setAssigneeName(emp.getEmployeeName());
            if (op.getOperationStatus() == null
                    || WorkOperationStatus.PENDING_ASSIGN.equals(op.getOperationStatus())) {
                op.setOperationStatus(WorkOperationStatus.ASSIGNED);
            }
            updateById(op);
            becamePrimary = true;
        } else {
            // 【2026-10-30】已有主负责人、状态却还停在「待派工」（工序清单里填过负责人、保存时被写成待派工）：
            // 有人认领即说明这道工序已有人做 → 推进为「已派工」，手机端/电脑端的数量报工才会放行
            markAssignedIfPending(op);
        }

        writeLog(op, "扫码认领", op.getOperationStatus(), op.getOperationStatus(), qty, null,
                operatorId, operatorName, "加入为共同负责人" + (becamePrimary ? "（并补为主负责人）" : ""));

        // 追加共同负责人时通知主负责人（失败只记日志，不影响认领）
        if (!becamePrimary && op.getAssigneeId() != null && !op.getAssigneeId().equals(emp.getEmployeeId())) {
            try {
                notifyOperation(op.getId(), List.of(op.getAssigneeId()), null, operatorId, operatorName);
            } catch (Exception e) {
                log.warn("扫码认领通知主负责人失败（不影响认领） opId={}: {}", op.getId(), e.getMessage());
            }
        }
        return detailForWorker(op.getId(), operatorId, operatorName);
    }

    @Override
    @Transactional
    public WorkOrderOperation unclaim(Long operationRowId, Long operatorId, String operatorName) {
        assertProductionPermission();
        WorkOrderOperation op = getById(operationRowId);
        if (op == null) throw new BusinessException("该工序不存在或已被删除");
        Employee emp = currentWorkerEmployee(operatorId);
        if (emp == null) throw new BusinessException("当前账号未绑定员工档案，无法退出");
        WorkOrderOperationWorker mine = activeWorkers(op.getId()).stream()
                .filter(w -> emp.getEmployeeId().equals(w.getEmployeeId())).findFirst().orElse(null);
        if (mine == null) throw new BusinessException("你不在本道工序的负责人里");
        mine.setStatus("已退出");
        mine.setExitTime(LocalDateTime.now());
        operationWorkerMapper.updateById(mine);

        // 退出人正是主负责人、且工序还没开工也没报工 → 退回「待派工」，不把工序卡在一个已退出的人名下；
        // 已开工/已报工的不清空负责人（避免产量归属丢失，改由管理员改派）。
        boolean wasPrimary = op.getAssigneeId() != null && op.getAssigneeId().equals(emp.getEmployeeId());
        String remark = "已报数量保留，未报份额释放";
        if (wasPrimary && nvl(op.getReportedQuantity()).compareTo(BigDecimal.ZERO) == 0
                && (WorkOperationStatus.ASSIGNED.equals(op.getOperationStatus())
                    || WorkOperationStatus.PENDING_ASSIGN.equals(op.getOperationStatus())
                    || op.getOperationStatus() == null)) {
            op.setAssigneeId(null);
            op.setAssigneeName(null);
            op.setOperationStatus(WorkOperationStatus.PENDING_ASSIGN);
            lambdaUpdate().eq(WorkOrderOperation::getId, op.getId())
                    .set(WorkOrderOperation::getAssigneeId, null)
                    .set(WorkOrderOperation::getAssigneeName, null)
                    .set(WorkOrderOperation::getOperationStatus, WorkOperationStatus.PENDING_ASSIGN)
                    .update();
            remark += "；工序退回待派工";
        }
        writeLog(op, "退出认领", op.getOperationStatus(), op.getOperationStatus(), null, null,
                operatorId, operatorName, remark);
        return detailForWorker(op.getId(), operatorId, operatorName);
    }

    @Override
    @Transactional
    public Map<String, Object> reportByWorker(Long operationRowId, Map<String, Object> payload,
                                              Long operatorId, String operatorName) {
        assertProductionPermission();
        Map<String, Object> body = payload == null ? Map.of() : payload;
        // 行锁：防两人同时报工把合计冲破计划量
        WorkOrderOperation op = lambdaQuery().eq(WorkOrderOperation::getId, operationRowId)
                .last("FOR UPDATE").one();
        if (op == null) throw new BusinessException("该工序不存在或已被删除");
        String block = workerBlockReason(op);
        if (block != null) throw new BusinessException(block);
        Employee me = currentWorkerEmployee(operatorId);
        HttpServletRequest httpReq = currentRequest();
        boolean admin = httpReq != null
                && (SecurityUtils.isAdmin(httpReq) || SecurityUtils.hasAnyRole(httpReq, "SUPER_ADMIN"));

        // 实际生产员工：默认当前登录人；选了别人 = 代报（班长代临时工）
        Long empId = me == null ? null : me.getEmployeeId();
        String empName = me == null ? null : me.getEmployeeName();
        boolean proxy = false;
        Object empObj = body.get("employeeId");
        if (empObj != null && !String.valueOf(empObj).isBlank()) {
            Long id;
            try {
                id = Long.valueOf(String.valueOf(empObj).trim());
            } catch (NumberFormatException e) {
                throw new BusinessException("生产员工参数不正确");
            }
            if (me == null || !id.equals(me.getEmployeeId())) {
                Employee target = employeeMapper.selectById(id);
                if (target == null) throw new BusinessException("所选生产员工不存在，请重新选择");
                if (target.getStatus() != null && !target.getStatus().isBlank() && !"在职".equals(target.getStatus())) {
                    throw new BusinessException("所选员工状态为" + target.getStatus() + "，不能报工");
                }
                empId = target.getEmployeeId();
                empName = target.getEmployeeName();
                proxy = true;
            }
        }
        // 管理员/未绑定员工的账号：未选实际员工时回落主负责人（保证后台也能一键报工）
        if (empId == null) {
            if (op.getAssigneeId() == null) {
                throw new BusinessException("当前账号未绑定员工档案，请选择实际生产员工后再报工");
            }
            empId = op.getAssigneeId();
            empName = op.getAssigneeName();
        }
        if (me == null && !admin) throw new BusinessException("当前账号未绑定员工档案，不能报工");

        final Long workerEmpId = empId;
        final String workerEmpName = empName;
        // 资格：主负责人或在岗参与人（否则先认领，防"谁都能报"）；管理员代报放行
        boolean iAmPrimary = op.getAssigneeId() != null && op.getAssigneeId().equals(workerEmpId);
        WorkOrderOperationWorker mine = activeWorkers(op.getId()).stream()
                .filter(w -> workerEmpId.equals(w.getEmployeeId())).findFirst().orElse(null);
        if (!iAmPrimary && mine == null && !admin) {
            throw new BusinessException(proxy ? "所选员工不在本道工序的负责人里，请先让他认领"
                    : "你不在本道工序的负责人里，请先点「我来做」");
        }
        BigDecimal qty = toDecimal(body.get("quantity"));
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("请填写本次完成数量（大于 0）");
        }
        BigDecimal rejected = toDecimal(body.get("rejectedQuantity"));
        if (rejected == null) rejected = BigDecimal.ZERO;
        if (rejected.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("报废数量不能小于 0");

        // 个人上限（认领时填了数量才卡）
        if (mine != null && mine.getClaimQuantity() != null) {
            BigDecimal myRemain = mine.getClaimQuantity().subtract(nvl(mine.getReportedQuantity()));
            if (myRemain.compareTo(BigDecimal.ZERO) < 0) myRemain = BigDecimal.ZERO;
            if (qty.compareTo(myRemain) > 0) {
                throw new BusinessException("超过你认领的数量：认领 " + plain(mine.getClaimQuantity())
                        + "，已报 " + plain(mine.getReportedQuantity()) + "，本次最多 " + plain(myRemain));
            }
        }
        // 合计上限
        BigDecimal plan = nvl(op.getPlanQuantity());
        if (plan.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal total = nvl(op.getReportedQuantity()).add(qty);
            if (total.compareTo(plan) > 0) {
                BigDecimal remain = plan.subtract(nvl(op.getReportedQuantity()));
                throw new BusinessException("超过本道工序计划数量：计划 " + plain(plan)
                        + "，已报 " + plain(op.getReportedQuantity()) + "，本次最多 "
                        + plain(remain.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remain));
            }
        }
        // 【2026-10-30】历史数据「已有负责人/已认领、状态却停在待派工」：认领人直接报工视为已派工，
        // 前面数量校验全部通过后再把状态推进为「已派工」，再走统一状态机，避免"认领了却报不了工"
        if (op.getOperationStatus() == null || WorkOperationStatus.PENDING_ASSIGN.equals(op.getOperationStatus())) {
            op.setOperationStatus(WorkOperationStatus.ASSIGNED);
            lambdaUpdate().eq(WorkOrderOperation::getId, op.getId())
                    .set(WorkOrderOperation::getOperationStatus, WorkOperationStatus.ASSIGNED)
                    .update();
        }
        // 状态允许数量报工（暂停状态要先恢复）
        // 【2026-10-30】工序类型不是「数量类型」时给出明确原因，不再统一报"当前状态不能报工"
        if (!WorkOperationStatus.TYPE_QUANTITY.equals(
                op.getOperationType() == null ? "" : op.getOperationType().trim())) {
            throw new BusinessException("该工序不是「数量类型」，不能按数量报工");
        }
        String st = op.getOperationStatus() == null ? WorkOperationStatus.PENDING_ASSIGN : op.getOperationStatus();
        if (!WorkOperationStatus.allowedActions(st, op.getOperationType()).contains(WorkOperationStatus.ACT_REPORT)) {
            throw new BusinessException("当前状态[" + st + "]不能报工"
                    + (WorkOperationStatus.PAUSED.equals(st) ? "，请先恢复工序" : ""));
        }
        // 防重号：同一请求重试直接返回已有流水
        String idem = body.get("idemKey") == null ? null : String.valueOf(body.get("idemKey")).trim();
        if (idem != null && !idem.isEmpty()) {
            OperationReportFlow exist = operationReportFlowMapper.selectOne(new QueryWrapper<OperationReportFlow>()
                    .eq("idem_key", idem).last("LIMIT 1"));
            if (exist != null) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("operation", detailForWorker(op.getId(), operatorId, operatorName));
                data.put("flow", exist);
                return data;
            }
        }

        // 走统一状态机（顺序执行 / 上道卡量 / 操作日志全部复用，不另写一套）
        Map<String, Object> actBody = new LinkedHashMap<>();
        actBody.put("quantity", qty);
        actBody.put("rejectedQuantity", rejected);
        if (body.get("inputQuantity") != null) actBody.put("inputQuantity", body.get("inputQuantity"));
        if (body.get("remark") != null) actBody.put("remark", body.get("remark"));
        // 【2026-11-25】页内数量报工同样携带工序自定义字段，随报工一起落库
        if (body.get("customFields") != null) actBody.put("customFields", body.get("customFields"));
        act(op.getId(), WorkOperationStatus.ACT_REPORT, actBody, operatorId, operatorName);

        OperationReportFlow flow = new OperationReportFlow();
        flow.setOperationRowId(op.getId());
        flow.setOrderId(op.getOrderId());
        flow.setOrderNo(op.getOrderNo());
        flow.setEmployeeId(empId);
        flow.setEmployeeName(empName);
        flow.setQuantity(qty);
        flow.setRejectedQuantity(rejected);
        flow.setSource("扫码报工");
        flow.setOperatorId(operatorId);
        flow.setOperatorName(operatorName);
        flow.setIsProxy(proxy ? 1 : 0);
        flow.setFlowStatus("有效");
        flow.setIdemKey(idem == null || idem.isEmpty() ? null : idem);
        flow.setReportTime(LocalDateTime.now());
        flow.setRemark(body.get("remark") == null ? null : String.valueOf(body.get("remark")));
        operationReportFlowMapper.insert(flow);

        // 个人累计（个人账）：主负责人首次报工也建一条在岗记录，保证个人已报可追溯
        if (mine == null) {
            mine = new WorkOrderOperationWorker();
            mine.setOperationRowId(op.getId());
            mine.setEmployeeId(empId);
            mine.setEmployeeName(empName);
            mine.setReportedQuantity(qty);
            mine.setStatus("在岗");
            mine.setClaimTime(LocalDateTime.now());
            mine.setClaimedBy(operatorId);
            mine.setClaimedByName(operatorName);
            mine.setRemark("主负责人报工自动登记");
            operationWorkerMapper.insert(mine);
        } else {
            mine.setReportedQuantity(nvl(mine.getReportedQuantity()).add(qty));
            operationWorkerMapper.updateById(mine);
        }

        // 后台留存一条已生效的汇报记录（界面不展示）：工资与成本核算继续按它取数
        saveAutoReportBill(op, qty, rejected, empId, empName, flow.getId(), operatorName);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("operation", detailForWorker(op.getId(), operatorId, operatorName));
        data.put("flow", flow);
        return data;
    }

    /** 扫码报工后台留存的汇报记录（已生效、界面不展示、不可编辑） */
    private void saveAutoReportBill(WorkOrderOperation op, BigDecimal qty, BigDecimal rejected,
                                    Long employeeId, String employeeName, Long flowId, String operatorName) {
        try {
            OperationReport r = new OperationReport();
            r.setReportNo(nextAutoReportNo());
            r.setReportDate(LocalDate.now());
            r.setProductionOrderId(op.getOrderId());
            r.setProductionOrderNo(op.getOrderNo());
            r.setProcessNo(op.getOperationCode());
            r.setOperationId(op.getOperationId());
            r.setOperationName(op.getOperationName());
            r.setEmployeeId(employeeId);
            r.setEmployeeName(employeeName);
            r.setReportType("扫码报工");
            r.setTotalQuantity(qty.add(rejected));
            r.setQualifiedQuantity(qty);
            r.setRejectedQuantity(rejected);
            r.setWorkHours(BigDecimal.ZERO);
            r.setOvertimeHours(BigDecimal.ZERO);
            r.setShiftType("白班");
            r.setStatus("已生效");
            r.setApprovalStatus("已生效");
            r.setApprovedBy(operatorName);
            r.setApprovedAt(LocalDateTime.now());
            r.setRemark("扫码报工自动留存（流水#" + flowId + "）");
            r.setCreatedBy(operatorName);
            r.setUpdatedBy(operatorName);
            operationReportMapper.insert(r);
        } catch (Exception e) {
            log.warn("扫码报工留存汇报记录失败（不影响报工，工资/成本需人工补） opId={}: {}", op.getId(), e.getMessage());
        }
    }

    /** 自动留存的汇报单号：PRP-yyyyMMdd-S0001（与正式汇报单号区分，不抢占正式序列） */
    private String nextAutoReportNo() {
        String prefix = "PRP-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-S";
        OperationReport last = operationReportMapper.selectOne(new QueryWrapper<OperationReport>()
                .likeRight("report_no", prefix).orderByDesc("report_no").last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getReportNo() != null) {
            try {
                seq = Integer.parseInt(last.getReportNo().substring(prefix.length())) + 1;
            } catch (Exception ignore) {
                // 单号格式异常 → 从 1 重新计
            }
        }
        return prefix + String.format("%04d", seq);
    }

    @Override
    @Transactional
    public Map<String, Object> undoReport(Long operationRowId, Long flowId,
                                          Long operatorId, String operatorName) {
        assertProductionPermission();
        WorkOrderOperation op = lambdaQuery().eq(WorkOrderOperation::getId, operationRowId)
                .last("FOR UPDATE").one();
        if (op == null) throw new BusinessException("该工序不存在或已被删除");
        String st = op.getOperationStatus() == null ? "" : op.getOperationStatus();
        if (WorkOperationStatus.FINISHED.equals(st) || WorkOperationStatus.WAIT_QC.equals(st)) {
            throw new BusinessException("工序已完工/完工待检，请先撤销完工再撤销报工");
        }
        HttpServletRequest req = currentRequest();
        boolean admin = req != null && (SecurityUtils.isAdmin(req) || SecurityUtils.hasAnyRole(req, "SUPER_ADMIN"));

        QueryWrapper<OperationReportFlow> qw = new QueryWrapper<OperationReportFlow>()
                .eq("operation_row_id", op.getId()).eq("flow_status", "有效");
        if (flowId != null) qw.eq("id", flowId);
        else qw.eq("operator_id", operatorId);
        qw.orderByDesc("id").last("LIMIT 1");
        OperationReportFlow flow = operationReportFlowMapper.selectOne(qw);
        if (flow == null) throw new BusinessException("没有可撤销的报工记录");
        if (!admin && !operatorId.equals(flow.getOperatorId())) {
            throw new BusinessException("只能撤销自己的报工；他人报工需管理员操作");
        }
        flow.setFlowStatus("已冲销");
        operationReportFlowMapper.updateById(flow);

        BigDecimal qty = nvl(flow.getQuantity());
        BigDecimal rejected = nvl(flow.getRejectedQuantity());
        BigDecimal reported = nvl(op.getReportedQuantity()).subtract(qty);
        if (reported.compareTo(BigDecimal.ZERO) < 0) reported = BigDecimal.ZERO;
        BigDecimal rejectedTotal = nvl(op.getRejectedQuantity()).subtract(rejected);
        if (rejectedTotal.compareTo(BigDecimal.ZERO) < 0) rejectedTotal = BigDecimal.ZERO;
        op.setReportedQuantity(reported);
        op.setOutputQuantity(reported);
        op.setRejectedQuantity(rejectedTotal);
        updateById(op);

        // 个人累计回退
        if (flow.getEmployeeId() != null) {
            WorkOrderOperationWorker mine = activeWorkers(op.getId()).stream()
                    .filter(w -> flow.getEmployeeId().equals(w.getEmployeeId())).findFirst().orElse(null);
            if (mine != null) {
                BigDecimal pr = nvl(mine.getReportedQuantity()).subtract(qty);
                mine.setReportedQuantity(pr.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : pr);
                operationWorkerMapper.updateById(mine);
            }
        }

        // 后台留存的汇报记录同步作废（工资/成本按已生效取数，作废后自然不再计入）
        try {
            operationReportMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<OperationReport>()
                            .eq("remark", "扫码报工自动留存（流水#" + flow.getId() + "）")
                            .set("status", "已作废").set("approval_status", "已作废"));
        } catch (Exception e) {
            log.warn("撤销报工时作废留存汇报记录失败: {}", e.getMessage());
        }

        writeLog(op, "撤销报工", op.getOperationStatus(), op.getOperationStatus(), qty, rejected,
                operatorId, operatorName, "冲销流水#" + flow.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("operation", detailForWorker(op.getId(), operatorId, operatorName));
        return data;
    }

    // ---------------- 2026-09-28：工序通知（@下道工序负责人 / 手选员工） ----------------

    @Override
    @Transactional
    public Map<String, Object> notifyOperation(Long operationRowId, List<Long> receiverEmployeeIds, String content,
                                               Long operatorId, String operatorName) {
        WorkOrderOperation op = getById(operationRowId);
        if (op == null) throw new BusinessException("工序不存在");

        // 员工ID → 系统用户ID（员工档案 user_id 绑定；未绑定登录账号的员工跳过，不报错）
        Set<Long> receiverUserIds = new LinkedHashSet<>();
        List<String> receiverNames = new ArrayList<>();
        List<Long> skippedEmployeeIds = new ArrayList<>();
        if (receiverEmployeeIds != null) {
            for (Long empId : receiverEmployeeIds) {
                if (empId == null) continue;
                Employee emp = employeeMapper.selectById(empId);
                if (emp == null || emp.getUserId() == null) {
                    skippedEmployeeIds.add(empId);
                    continue;
                }
                if (receiverUserIds.add(emp.getUserId())) {
                    receiverNames.add(emp.getEmployeeName() == null ? String.valueOf(empId) : emp.getEmployeeName());
                }
            }
        }
        // 没有任何有效接收人 → 明确业务提示，不静默成功
        if (receiverUserIds.isEmpty()) {
            throw new BusinessException("未找到有效的通知接收人：所选员工尚未在员工档案绑定登录账号，请先绑定后再通知");
        }

        String operator = (operatorName == null || operatorName.isBlank()) ? "系统" : operatorName;
        String msg = (content == null || content.isBlank())
                ? "工序【" + nz(op.getOperationName()) + "】已完成"
                : content;
        String title = operator + " 通知你：工序【" + nz(op.getOperationName()) + "】已完成";

        int sent = 0;
        for (Long uid : receiverUserIds) {
            SysNotification n = new SysNotification();
            n.setReceiverId(uid);
            n.setType("operation_mention");
            n.setTitle(title);
            n.setContent(msg);
            n.setSourceType("WORK_ORDER_OPERATION");
            n.setSourceId(op.getId());
            n.setSourceNo(op.getOrderNo());
            n.setRoutePath(op.getOrderId() == null ? null : "/production/workorders/" + op.getOrderId());
            n.setUrgency("normal");
            // 复用系统通知统一入口：内部做「接收人+类型+来源+未读」去重与索引冲突兜底
            if (sysNotificationService.insertOne(n)) sent++;
            // 微信订阅消息提醒（2026-11-20）：未绑定微信/无额度时服务内部静默跳过；状态=被通知的工序名称
            wxSubscribeService.pushOperationMention(uid, op.getOrderNo(), msg, operator, op.getOperationName());
        }

        // 留痕：动作码「工序通知」，备注带接收人姓名与内容（不改工序状态，from = to）
        String st = op.getOperationStatus();
        writeLog(op, WorkOperationStatus.ACT_NOTIFY, st, st, null, null, operatorId, operatorName,
                "通知给：" + String.join("、", receiverNames) + "；内容：" + msg);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sent", sent);
        data.put("receivers", receiverNames);
        data.put("skippedEmployeeIds", skippedEmployeeIds);
        return data;
    }

    /**
     * 【2026-09-28】自动通知下道工序负责人：受工艺路线「自动通知下道工序负责人」开关控制。
     * 开关 =1 才发；=0 / 工艺路线查不到 / 已停用 → 不发（默认行为，与改造前完全一致）。
     * <b>整段 try/catch</b>：通知失败只打 warn，绝不影响工序状态流转本身。
     */
    private void tryAutoNotifyNext(WorkOrderOperation op, Long operatorId, String operatorName) {
        try {
            Routing routing = op.getRoutingId() == null ? null : routingMapper.selectById(op.getRoutingId());
            if (routing == null || routing.getAutoNotifyNext() == null || routing.getAutoNotifyNext() != 1) return;
            if (routing.getIsEnabled() != null && routing.getIsEnabled() == 0) return;   // 已停用 → 视为关

            WorkOrderOperation next = nextOperationForSequence(op);
            String st = op.getOperationStatus();
            if (next == null) return;   // 最后一道工序，无下道可通知
            if (next.getAssigneeId() == null) {
                // 下道未派工 → 不发，只写日志说明（守则：空态要有交代，不静默）
                writeLog(op, WorkOperationStatus.ACT_NOTIFY, st, st, null, null, operatorId, operatorName,
                        "下道工序【" + nz(next.getOperationName()) + "】未派工，自动通知未发出");
                return;
            }
            Employee emp = employeeMapper.selectById(next.getAssigneeId());
            if (emp == null || emp.getUserId() == null) {
                writeLog(op, WorkOperationStatus.ACT_NOTIFY, st, st, null, null, operatorId, operatorName,
                        "下道工序负责人【" + nz(next.getAssigneeName()) + "】未绑定登录账号，自动通知未发出");
                return;
            }

            String content = "工序【" + nz(op.getOperationName()) + "】已完成，请你开始【"
                    + nz(next.getOperationName()) + "】。（工单 " + nz(op.getOrderNo()) + "）";
            SysNotification n = new SysNotification();
            n.setReceiverId(emp.getUserId());
            n.setType("operation_mention");
            n.setTitle("工序通知：工序【" + nz(op.getOperationName()) + "】已完成");
            n.setContent(content);
            n.setSourceType("WORK_ORDER_OPERATION");
            n.setSourceId(op.getId());
            n.setSourceNo(op.getOrderNo());
            n.setRoutePath(op.getOrderId() == null ? null : "/production/workorders/" + op.getOrderId());
            n.setUrgency("normal");
            sysNotificationService.insertOne(n);
            // 微信订阅消息提醒（2026-11-20）：未绑定微信/无额度时服务内部静默跳过；状态=刚完成的工序名称
            wxSubscribeService.pushOperationMention(emp.getUserId(), op.getOrderNo(), content, operatorName, op.getOperationName());
        } catch (Exception e) {
            // 通知失败绝不拉挂工序状态流转
            log.warn("[工序通知] 自动通知下道负责人失败（不影响工序状态流转）operationRowId={}: {}",
                    op.getId(), e.getMessage());
        }
    }

    /** 名称兜底：空值返回空串（页面/通知里不出现 null 占位） */
    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ---------------- 内部工具 ----------------

    @Override
    @Transactional
    public void recordDriven(WorkOrderOperation op, String fromStatus, String toStatus, String reason, String remark) {
        if (op == null || fromStatus == null || fromStatus.equals(toStatus)) return;
        writeLog(op, WorkOperationStatus.ACT_DRIVEN, fromStatus, toStatus, null, null, null, reason, remark);
    }

    /** 开工：补实际开始日期 + 首道投入数（首道 = 本工序计划量，其余 = 上道产出） */
    private void markStart(WorkOrderOperation op) {
        if (op.getActualStartDate() == null) op.setActualStartDate(LocalDate.now());
        if (nvl(op.getInputQuantity()).compareTo(BigDecimal.ZERO) == 0) {
            WorkOrderOperation prev = prevOperation(op);
            op.setInputQuantity(prev != null ? nvl(prev.getOutputQuantity()) : nvl(op.getPlanQuantity()));
        }
    }

    /** 同一工单里行号小于本工序的最后一道（= 前道工序） */
    private WorkOrderOperation prevOperation(WorkOrderOperation op) {
        if (op.getOrderId() == null || op.getLineNo() == null) return null;
        return getOne(new QueryWrapper<WorkOrderOperation>()
                .eq("order_id", op.getOrderId())
                .lt("line_no", op.getLineNo())
                .orderByDesc("line_no")
                .last("LIMIT 1"));
    }

    /**
     * 【2026-09-28 用户口径】「按顺序执行」校验：只有工艺路线「按顺序执行」=1 时才拦截。
     *
     * <p>开关 =0（默认）/ 工艺路线查不到 / 已停用 / 已删除 → 视为开关关闭，放行
     * （即"后道工序可自由开工"，与升级前口径一致）。</p>
     *
     * <p>拦截条件：前道工序存在 且 其状态不是「已完工」。前道不存在（本道即第一道）→ 放行。</p>
     */
    private void assertSequenceAllowed(WorkOrderOperation op, String action) {
        Routing routing = op.getRoutingId() == null ? null : routingMapper.selectById(op.getRoutingId());
        if (!sequenceEnforced(routing)) return;
        WorkOrderOperation prev = prevOperationForSequence(op);
        if (prev == null) return;   // 本道就是第一道 → 放行
        String st = prev.getOperationStatus();
        if (!WorkOperationStatus.FINISHED.equals(st)) {
            throw new BusinessException("前道工序【" + prev.getOperationName() + "】尚未完工，按工艺路线要求不能"
                    + action + "，请先完成前道工序");
        }
    }

    /** 工艺路线是否开启「按顺序执行」（查不到 / 已停用 → 关，默认关） */
    private static boolean sequenceEnforced(Routing routing) {
        return routing != null
                && routing.getEnforceSequence() != null && routing.getEnforceSequence() == 1
                && (routing.getIsEnabled() == null || routing.getIsEnabled() == 1);
    }

    /**
     * 前道工序（用于顺序校验）：优先按工序行上的「前道工序行号」取；
     * 取不到时退回「同工单 + 同成品明细行内、行号小于本道的最靠后一道」。
     */
    private WorkOrderOperation prevOperationForSequence(WorkOrderOperation op) {
        if (op.getOrderId() == null) return null;
        if (op.getPrevLineNo() != null) {
            QueryWrapper<WorkOrderOperation> byPrev = new QueryWrapper<WorkOrderOperation>()
                    .eq("order_id", op.getOrderId())
                    .eq("line_no", op.getPrevLineNo());
            if (op.getOrderProductId() != null) byPrev.eq("order_product_id", op.getOrderProductId());
            WorkOrderOperation found = getOne(byPrev.last("LIMIT 1"));
            if (found != null) return found;
        }
        if (op.getLineNo() == null) return null;
        QueryWrapper<WorkOrderOperation> byLine = new QueryWrapper<WorkOrderOperation>()
                .eq("order_id", op.getOrderId())
                .lt("line_no", op.getLineNo())
                .orderByDesc("line_no")
                .last("LIMIT 1");
        if (op.getOrderProductId() != null) byLine.eq("order_product_id", op.getOrderProductId());
        return getOne(byLine);
    }

    /**
     * 下道工序（用于自动通知）：优先按工序行上的「后道工序行号」取；
     * 取不到时退回「同工单 + 同成品明细行内、行号大于本道的最前一道」。
     */
    private WorkOrderOperation nextOperationForSequence(WorkOrderOperation op) {
        if (op.getOrderId() == null) return null;
        if (op.getNextLineNo() != null) {
            QueryWrapper<WorkOrderOperation> byNext = new QueryWrapper<WorkOrderOperation>()
                    .eq("order_id", op.getOrderId())
                    .eq("line_no", op.getNextLineNo());
            if (op.getOrderProductId() != null) byNext.eq("order_product_id", op.getOrderProductId());
            WorkOrderOperation found = getOne(byNext.last("LIMIT 1"));
            if (found != null) return found;
        }
        if (op.getLineNo() == null) return null;
        QueryWrapper<WorkOrderOperation> byLine = new QueryWrapper<WorkOrderOperation>()
                .eq("order_id", op.getOrderId())
                .gt("line_no", op.getLineNo())
                .orderByAsc("line_no")
                .last("LIMIT 1");
        if (op.getOrderProductId() != null) byLine.eq("order_product_id", op.getOrderProductId());
        return getOne(byLine);
    }

    private void writeLog(WorkOrderOperation op, String action, String from, String to,
                          BigDecimal quantity, BigDecimal rejected, Long operatorId, String operatorName, String remark) {
        WorkOperationLog logRow = new WorkOperationLog();
        logRow.setOrderId(op.getOrderId());
        logRow.setOrderNo(op.getOrderNo());
        logRow.setOperationRowId(op.getId());
        logRow.setOperationCode(op.getOperationCode());
        logRow.setOperationName(op.getOperationName());
        logRow.setActionType(action);
        logRow.setFromStatus(from);
        logRow.setToStatus(to);
        logRow.setQuantity(quantity);
        logRow.setRejectedQuantity(rejected);
        logRow.setOperatorId(operatorId);
        logRow.setOperatorName(operatorName);
        logRow.setOperateTime(LocalDateTime.now());
        logRow.setRemark(remark);
        logMapper.insert(logRow);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 【第 14 期】是否未派工（派工状态筛选与看板输出共用的唯一口径）：
     * 工序状态为「待派工」<b>或</b> 负责人为空 → 未派工；其余为已派工。
     */
    private static boolean isUnassigned(WorkOrderOperation op) {
        return op != null
                && (WorkOperationStatus.PENDING_ASSIGN.equals(op.getOperationStatus()) || op.getAssigneeId() == null);
    }

    /** 数量去尾零显示（1.0000 → 1、1.5000 → 1.5），用于提示与备注文案 */
    private static String plain(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).stripTrailingZeros().toPlainString();
    }

    /** 对象转字符串（可空安全，用于 map 取参） */
    private static String toStr(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new BusinessException("数量格式不正确：" + v);
        }
    }

    /** 可空数值解析（缺省/空串返回 null，用于「没填就不改」的字段，如投入数量） */
    private static BigDecimal toDecimalOrNull(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new BusinessException("数量格式不正确：" + v);
        }
    }
}
