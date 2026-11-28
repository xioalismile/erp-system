package com.erp.production.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillStatusEnum;
import com.erp.common.bill.BillStatusUtils;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.config.BusinessException;
import com.erp.inventory.entity.MaterialRequisition;
import com.erp.inventory.entity.MaterialRequisitionItem;
import com.erp.inventory.service.MaterialRequisitionService;
import com.erp.masterdata.entity.Bom;
import com.erp.masterdata.entity.BomItem;
import com.erp.masterdata.mapper.BomItemMapper;
import com.erp.masterdata.mapper.BomMapper;
import com.erp.produce.mrp.entity.MrpPlan;
import com.erp.produce.mrp.entity.MrpPlanItem;
import com.erp.produce.mrp.mapper.MrpPlanItemMapper;
import com.erp.produce.mrp.mapper.MrpPlanMapper;
import com.erp.production.entity.OperationReport;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.entity.ProductionReceiptItem;
import com.erp.production.entity.RoutingOperation;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderMaterial;
import com.erp.production.entity.WorkOrderOperation;
import com.erp.production.entity.WorkOrderProduct;
import com.erp.production.mapper.OperationReportMapper;
import com.erp.production.mapper.RoutingOperationMapper;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.production.mapper.WorkOrderMaterialMapper;
import com.erp.production.mapper.WorkOrderOperationMapper;
import com.erp.production.mapper.WorkOrderProductMapper;
import com.erp.production.vo.OperationProgressVO;
import com.erp.production.vo.WorkOrderProgressVO;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.mapper.SaleOrderItemMapper;
import com.erp.sale.mapper.SaleOrderMapper;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.subcontract.order.entity.SubcontractOrder;
import com.erp.subcontract.order.entity.SubcontractOrderItem;
import com.erp.subcontract.order.service.SubcontractOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service

public class WorkOrderServiceImpl extends AbstractBillService<WorkOrderMapper, WorkOrder> implements WorkOrderService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义全面推广）。
        // 键 = 前端筛选条件定义键（与生产工单明细报表引擎筛选键同键，汇总/明细两种模式一套参数）；
        // 值 = 实体属性名（registerProps 由 MyBatis-Plus 元数据解析成真实列名，防手写列名笔误）。
        // 负责人/部门/仓库：列是外键，按名称筛的下拉传的是主键 ID → Spec.column 写外键「属性名」。
        Map<String, BillHeaderFilterSupport.Spec> f = new LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("orderNo", new BillHeaderFilterSupport.Spec("orderNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("materialCode", new BillHeaderFilterSupport.Spec("materialCode", BillHeaderFilterSupport.Kind.TEXT));
        f.put("materialName", new BillHeaderFilterSupport.Spec("materialName", BillHeaderFilterSupport.Kind.TEXT));
        f.put("model", new BillHeaderFilterSupport.Spec("model", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceType", new BillHeaderFilterSupport.Spec("sourceType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceNo", new BillHeaderFilterSupport.Spec("sourceNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("salesOrderNo", new BillHeaderFilterSupport.Spec("salesOrderNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("bomVersion", new BillHeaderFilterSupport.Spec("bomVersion", BillHeaderFilterSupport.Kind.TEXT));
        f.put("routingVersion", new BillHeaderFilterSupport.Spec("routingVersion", BillHeaderFilterSupport.Kind.TEXT));
        f.put("taxCalcMethod", new BillHeaderFilterSupport.Spec("taxCalcMethod", BillHeaderFilterSupport.Kind.TEXT));
        f.put("approvedBy", new BillHeaderFilterSupport.Spec("approvedBy", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 外键 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("managerName", new BillHeaderFilterSupport.Spec("managerId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("deptName", new BillHeaderFilterSupport.Spec("departmentId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("warehouseName", new BillHeaderFilterSupport.Spec("warehouseId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("orderDate", new BillHeaderFilterSupport.Spec("orderDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("planStartDate", new BillHeaderFilterSupport.Spec("planStartDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("planEndDate", new BillHeaderFilterSupport.Spec("planEndDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("quantity", new BillHeaderFilterSupport.Spec("quantity", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("completedQuantity", new BillHeaderFilterSupport.Spec("completedQuantity", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("workOrder", WorkOrder.class, f);
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "actual_end_date",
            "actual_start_date",
            "approval_status",
            "approved_at",
            "approved_by",
            "bom_id",
            "bom_version",
            "close_status",
            "completed_quantity",
            "created_at",
            "created_by",
            "department_id",
            "is_closed",
            "is_primary",
            "is_urgent",
            "lead_time_days",
            "line_no",
            "manager_id",
            "material_code",
            "material_id",
            "material_name",
            "model",
            "order_date",
            "order_id",
            "order_no",
            "org_code",
            "plan_end_date",
            "plan_start_date",
            "priority",
            "production_order_id",
            "quantity",
            "rejected_quantity",
            "remark",
            "reported_quantity",
            "routing_id",
            "routing_version",
            "sales_order_no",
            "source_no",
            "source_sale_order_id",
            "source_sale_order_item_id",
            "source_type",
            "status",
            "unit_id",
            "updated_at",
            "updated_by",
            "warehouse_id",
            "work_center_id",
            "work_order_no"
        );

    /** 明细计税类型：1=含税价 2=不含税价（默认） 3=不计税（2026-10-09 计税口径铺开，与销售订单试点同口径） */
    private static final int TAX_TYPE_INCLUSIVE = 1;
    private static final int TAX_TYPE_EXCLUSIVE = 2;
    private static final int TAX_TYPE_NO_TAX = 3;

    /** 统一数据权限解析器：按角色 data_scope（统一数据权限）过滤工单列表 */
    @Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 下游引用扫描（删除/重置草稿/作废前校验：工单已被下游「已生效」单据引用时禁止操作） */
    @Autowired
    private BillRelationService billRelationService;

    /** 币种档案：工单下推领料单时补本位币，避免领料单币种为空 */
    @Autowired
    private com.erp.basedata.service.CurrencyService currencyService;

    /** 采购批量政策（2026-11-25）：领料单按采购批量校验/取整的开关与物料包装量 */
    @Autowired
    private com.erp.config.service.SysConfigService sysConfigService;
    @Autowired
    private com.erp.masterdata.service.MaterialService materialService;

    private final WorkOrderMaterialMapper materialMapper;
    private final WorkOrderProductMapper workOrderProductMapper;
    private final BomMapper bomMapper;
    private final BomItemMapper bomItemMapper;
    private final RoutingOperationMapper routingOperationMapper;
    private final OperationReportMapper operationReportMapper;
    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final MrpPlanMapper mrpPlanMapper;
    private final MrpPlanItemMapper mrpPlanItemMapper;
    private final MaterialRequisitionService materialRequisitionService;
    private final OperationReportService operationReportService;
    private final ProductionReceiptService productionReceiptService;
    private final SubcontractOrderService subcontractOrderService;
    private final WorkOrderOperationMapper workOrderOperationMapper;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    /** 【2026-09-13 仓库兜底】工单/领料/入库都需要仓库，缺仓库时取"启用状态中 stock_id 最小"的仓库 */
    private final com.erp.basedata.service.WarehouseService warehouseService;

    /** 【2026-10-09】自定义字段服务：MRP 下推生成工单时把「物料档案」的自定义字段值带到工单 */
    private final com.erp.customfield.service.CustomFieldService customFieldService;

    /** 【2026-10-09】工艺路线主表 Mapper：MRP 下推时按 物料/BOM 解析默认工艺路线 */
    private final com.erp.production.mapper.RoutingMapper routingMapper;

    /** 【2026-11-06 化妆品批记录】工单生效时预生成批生产记录头（固化配方/工艺快照） */
    private final com.erp.production.service.BatchRecordService batchRecordService;

    public WorkOrderServiceImpl(WorkOrderMaterialMapper materialMapper,
                                WorkOrderProductMapper workOrderProductMapper,
                                BomMapper bomMapper,
                                BomItemMapper bomItemMapper,
                                RoutingOperationMapper routingOperationMapper,
                                OperationReportMapper operationReportMapper,
                                SaleOrderMapper saleOrderMapper,
                                SaleOrderItemMapper saleOrderItemMapper,
                                MrpPlanMapper mrpPlanMapper,
                                MrpPlanItemMapper mrpPlanItemMapper,
                                @Lazy MaterialRequisitionService materialRequisitionService,
                                @Lazy OperationReportService operationReportService,
                                @Lazy ProductionReceiptService productionReceiptService,
                                @Lazy SubcontractOrderService subcontractOrderService,
                                WorkOrderOperationMapper workOrderOperationMapper,
                                SysSerialNoConfigService sysSerialNoConfigService,
                                com.erp.basedata.service.WarehouseService warehouseService,
                                com.erp.customfield.service.CustomFieldService customFieldService,
                                com.erp.production.mapper.RoutingMapper routingMapper,
                                @Lazy com.erp.production.service.BatchRecordService batchRecordService) {
        this.materialMapper = materialMapper;
        this.workOrderProductMapper = workOrderProductMapper;
        this.bomMapper = bomMapper;
        this.bomItemMapper = bomItemMapper;
        this.routingOperationMapper = routingOperationMapper;
        this.operationReportMapper = operationReportMapper;
        this.saleOrderMapper = saleOrderMapper;
        this.saleOrderItemMapper = saleOrderItemMapper;
        this.mrpPlanMapper = mrpPlanMapper;
        this.mrpPlanItemMapper = mrpPlanItemMapper;
        this.materialRequisitionService = materialRequisitionService;
        this.operationReportService = operationReportService;
        this.productionReceiptService = productionReceiptService;
        this.subcontractOrderService = subcontractOrderService;
        this.workOrderOperationMapper = workOrderOperationMapper;
        this.sysSerialNoConfigService = sysSerialNoConfigService;
        this.warehouseService = warehouseService;
        this.customFieldService = customFieldService;
        this.routingMapper = routingMapper;
        this.batchRecordService = batchRecordService;
    }

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "生产工单"; }
    @Override protected String getApprovalBillType() { return "PRODUCTION_ORDER"; }
    @Override protected String getBillNo(WorkOrder b) { return b.getOrderNo(); }
    @Override protected Object getId(WorkOrder b) { return b.getOrderId(); }
    @Override protected String getStatus(WorkOrder b) { return b.getStatus(); }
    @Override protected void setStatus(WorkOrder b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(WorkOrder b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(WorkOrder b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(WorkOrder b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(WorkOrder b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(WorkOrder b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(WorkOrder b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(WorkOrder b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(WorkOrder b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(WorkOrder b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(WorkOrder b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(WorkOrder b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(WorkOrder b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(WorkOrder b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：重置草稿/作废/删除前由统一状态机调用 */
    @Override
    public List<String> listApprovedDownstreamBills(WorkOrder bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getOrderNo());
    }

    /** 提交审核前严格校验：计划数量>0 + 原料明细（为空时按BOM/来源单展开，仍为空则拦截） */
    @Override
    protected void validateStrictSubmit(WorkOrder order) {
        if (order.getQuantity() == null || order.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("计划数量必须大于0");
        }
        ensureMaterials(order);
    }

    /** 审核生效影响：生成工序实例 + 预生成批生产记录 */
    @Override
    public void onApproved(WorkOrder order) {
        executeRelease(order, order.getApprovedBy());
    }

    /**
     * 重置草稿影响：工单生效本身不产生库存/成本移动，故无需冲回；
     * 已生成的工序实例保留（可能已录入进度，删除会造成进度丢失）。
     */
    @Override
    public void onReverseApproved(WorkOrder order) { }

    /** 结案：同步 is_closed=1（status/close_status 由统一状态机写） */
    @Override
    public void onClosed(WorkOrder order) {
        UpdateWrapper<WorkOrder> uw = new UpdateWrapper<>();
        uw.eq("order_id", order.getOrderId()).set("is_closed", 1);
        update(uw);
    }

    /** 反结案：同步 is_closed=0 */
    @Override
    public void onReopened(WorkOrder order) {
        UpdateWrapper<WorkOrder> uw = new UpdateWrapper<>();
        uw.eq("order_id", order.getOrderId()).set("is_closed", 0);
        update(uw);
    }

    /** 作废（作废前为已生效）：释放占用的销售订单已排产数量 */
    @Override
    public void onVoided(WorkOrder order) {
        recalcSaleOrderTurnedForOrder(order.getOrderId());
    }

    /** 反作废（恢复为已生效）：重新占用销售订单已排产数量 */
    @Override
    public void onUnvoided(WorkOrder order) {
        recalcSaleOrderTurnedForOrder(order.getOrderId());
    }

    @Override
    @Transactional
    public void saveOrderWithItems(WorkOrder order, List<WorkOrderMaterial> materials) {
        boolean isNew = order.getOrderId() == null;

        // 保存=草稿宽松：「计划数量必须大于0」等业务校验不在保存时执行，统一在提交(下达)时校验

        // 单号只在新建时生成：更新时不重新取号（否则每次保存都会吃掉一个序号）
        if (isNew && (order.getOrderNo() == null || order.getOrderNo().isBlank())) {
            order.setOrderNo(generateOrderNo());
        }
        // 保存=草稿：空串按空处理；草稿态单据的审批状态只能是"草稿"（"待审核"只在提交后出现）
        if (order.getStatus() == null || order.getStatus().isBlank()) order.setStatus("草稿");
        if (order.getApprovalStatus() == null || order.getApprovalStatus().isBlank()
                || ("草稿".equals(order.getStatus()) && "待审核".equals(order.getApprovalStatus()))) {
            order.setApprovalStatus("草稿");
        }
        if (order.getIsUrgent() == null) order.setIsUrgent((short) 0);
        if (order.getIsClosed() == null) order.setIsClosed((short) 0);
        if (order.getCompletedQuantity() == null) order.setCompletedQuantity(BigDecimal.ZERO);
        if (order.getReportedQuantity() == null) order.setReportedQuantity(BigDecimal.ZERO);
        if (order.getRejectedQuantity() == null) order.setRejectedQuantity(BigDecimal.ZERO);
        if (order.getOrderDate() == null) order.setOrderDate(LocalDate.now());

        // 若关联BOM, 自动带出 bom_version
        if (order.getBomId() != null && (order.getBomVersion() == null || order.getBomVersion().isBlank())) {
            Bom bom = bomMapper.selectById(order.getBomId());
            if (bom != null) {
                order.setBomVersion(bom.getBomVersion());
            }
        }

        // 【2026-09-13 修复·仓库兜底】工单/成品明细/领料单/入库单都取 order.warehouseId。
        //   原实现：来源销售订单没有仓库时一路 NULL 传到底，领料/入库审核里 `stockId == null → continue`
        //   变成"静默跳过"——不扣库存、不归集材料成本、还不报错（实测库存台账 0 行）。
        //   兜底规则与出库单一致：启用状态中取 stock_id 最小的仓库（warehouse 表无 id=1，禁止硬编码 1）。
        if (order.getWarehouseId() == null) {
            com.erp.basedata.entity.Warehouse def = warehouseService.getOne(
                    new QueryWrapper<com.erp.basedata.entity.Warehouse>()
                            .eq("status", "启用").orderByAsc("stock_id").last("LIMIT 1"));
            if (def != null && def.getStockId() != null) {
                order.setWarehouseId(def.getStockId());
                log.info("工单 {} 未指定仓库，已兜底取仓库 stock_id={}", order.getOrderNo(), def.getStockId());
            }
        }

        // 成品明细兼容: 若成品明细为空但表头有 materialId, 自动创建一条成品明细(兼容旧数据)
        List<WorkOrderProduct> products = order.getProducts();
        if ((products == null || products.isEmpty()) && order.getMaterialId() != null) {
            products = new ArrayList<>();
            WorkOrderProduct header = new WorkOrderProduct();
            header.setMaterialId(order.getMaterialId());
            header.setMaterialCode(order.getMaterialCode());
            header.setMaterialName(order.getMaterialName());
            header.setSpecification(order.getModel());
            header.setModel(order.getModel());
            header.setUnitId(order.getUnitId());
            header.setQuantity(order.getQuantity());
            header.setCompletedQuantity(order.getCompletedQuantity());
            header.setReportedQuantity(order.getReportedQuantity());
            header.setRejectedQuantity(order.getRejectedQuantity());
            header.setWarehouseId(order.getWarehouseId());
            if (order.getSourceType() != null) header.setSourceType(order.getSourceType());
            if (order.getSourceNo() != null) header.setSourceNo(order.getSourceNo());
            products.add(header);
            order.setProducts(products);
        }

        // 保存宽松：丢弃未选择物料的空行（material_id/material_code/material_name/unit_id 数据库为 NOT NULL，无法落库）
        if (products != null) {
            List<WorkOrderProduct> validProducts = new ArrayList<>();
            for (WorkOrderProduct p : products) {
                if (p != null && p.getMaterialId() != null) validProducts.add(p);
            }
            products = validProducts;
            order.setProducts(products);
        }
        if (materials != null) {
            List<WorkOrderMaterial> validMaterials = new ArrayList<>();
            for (WorkOrderMaterial m : materials) {
                if (m != null && m.getMaterialId() != null) validMaterials.add(m);
            }
            materials = validMaterials;
        }

        // 表头产品信息与成品明细对齐（2026-10-26）：第10期后成品明细才是物料录入入口，
        // 手工建单时表头 material_* 始终为空 → 列表汇总模式「产品编码/产品名称」整列为空、导出也空。
        // 口径：有成品明细时表头始终镜像第一行成品（列表/导出/下游取的是表头字段），无成品明细时保持原值。
        if (products != null && !products.isEmpty()) {
            WorkOrderProduct firstProduct = products.get(0);
            order.setMaterialId(firstProduct.getMaterialId());
            order.setMaterialCode(firstProduct.getMaterialCode());
            order.setMaterialName(firstProduct.getMaterialName());
            order.setModel(firstProduct.getModel() != null ? firstProduct.getModel() : firstProduct.getSpecification());
            if (firstProduct.getUnitId() != null) order.setUnitId(firstProduct.getUnitId());
        }

        // 第10期（2026-09-27）：成品明细行保存后主键会**重建**（见下方"孤儿明细"处理：先按 order_id 全删、再 setId(null) 重插），
        // 而工单工序用 order_product_id 绑到成品行 —— 若不处理，工序会绑到**失效的旧主键**，
        // 进度流程与车间看板就会把它落到"未指定成品"。这里先记下「成品行号 → 旧主键」，保存完成后按行号把归属迁到新主键。
        Map<Integer, Long> oldProductIdByLineNo = new HashMap<>();
        // 【2026-11-09 多成品行BOM】本次保存后「成品行号 → 新主键」，原料行用它把归属绑到对应成品行
        Map<Integer, Long> savedProductIdByLineNo = new HashMap<>();
        // 【2026-11-07 标准下推占用】编辑时也要把「原来源行」纳入重算范围（删行/改量要能回落）
        Set<Long> affectedSaleItemIds = new HashSet<>();
        if (products != null && order.getOrderId() != null) {
            for (WorkOrderProduct old : workOrderProductMapper.selectByOrderId(order.getOrderId())) {
                if (old.getLineNo() != null) oldProductIdByLineNo.put(old.getLineNo(), old.getId());
                if (old.getSourceItemId() != null && "销售订单".equals(old.getSourceType())) {
                    affectedSaleItemIds.add(old.getSourceItemId());
                }
            }
        }

        if (isNew) {
            save(order);
            // 【2026-09-13 修复·孤儿明细】新增时也按 order_id 清掉残留明细。
            //   id 复用/历史孤儿（工单删除后明细未级联删除）会让 generateMaterialsFromBom 把别人的成品明细
            //   当成"多个成品明细"重复展开 BOM —— 实测 WO-20260913-0001 因此产出 8 行用料（BOM 只有 4 行），
            //   领料需求与材料成本直接翻倍。
            materialMapper.delete(new QueryWrapper<WorkOrderMaterial>().eq("order_id", order.getOrderId()));
            workOrderProductMapper.delete(new QueryWrapper<WorkOrderProduct>().eq("order_id", order.getOrderId()));
        } else {
            // 防孤儿明细: 主表必须真实存在。否则 updateById 会静默更新 0 行却照插明细,
            // 产生"有明细无主表"的孤儿行(实测出现过 order_id=11 的孤儿成品明细)。
            if (getById(order.getOrderId()) == null) {
                throw new BusinessException("工单不存在(可能已被删除)，请刷新列表后重试");
            }
            // 编辑: 先按 order_id 删除旧明细(成品 + 原料), 再更新主单
            // items 为 null 时不删除既有明细，避免未携带明细的更新把表体清空
            if (materials != null) {
                materialMapper.delete(new QueryWrapper<WorkOrderMaterial>().eq("order_id", order.getOrderId()));
            }
            if (products != null) {
                workOrderProductMapper.delete(new QueryWrapper<WorkOrderProduct>().eq("order_id", order.getOrderId()));
            }
            updateById(order);
        }

        // 保存成品明细
        if (products != null && !products.isEmpty()) {
            int lineNo = 10;
            for (WorkOrderProduct product : products) {
                product.setId(null);
                product.setOrderId(order.getOrderId());
                if (product.getLineNo() == null) {
                    product.setLineNo(lineNo);
                    lineNo += 10;
                }
                if (product.getCompletedQuantity() == null) product.setCompletedQuantity(BigDecimal.ZERO);
                if (product.getReportedQuantity() == null) product.setReportedQuantity(BigDecimal.ZERO);
                if (product.getRejectedQuantity() == null) product.setRejectedQuantity(BigDecimal.ZERO);
                workOrderProductMapper.insert(product);
            }

            // 成品行主键已重建 → 记录「成品行号 → 新主键」，供工序与原料行归位
            for (WorkOrderProduct saved : workOrderProductMapper.selectByOrderId(order.getOrderId())) {
                if (saved.getLineNo() != null) savedProductIdByLineNo.put(saved.getLineNo(), saved.getId());
            }

            // 第10期：按「成品行号」把工序的成品归属从旧主键迁到新主键
            if (!oldProductIdByLineNo.isEmpty()) {
                Map<Long, Long> idRemap = new HashMap<>();
                for (Map.Entry<Integer, Long> e : oldProductIdByLineNo.entrySet()) {
                    Long newId = savedProductIdByLineNo.get(e.getKey());
                    if (newId != null && !newId.equals(e.getValue())) {
                        idRemap.put(e.getValue(), newId);
                    }
                }
                for (Map.Entry<Long, Long> e : idRemap.entrySet()) {
                    workOrderOperationMapper.update(null,
                            new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<WorkOrderOperation>()
                                    .eq("order_id", order.getOrderId())
                                    .eq("order_product_id", e.getKey())
                                    .set("order_product_id", e.getValue()));
                }
                if (!idRemap.isEmpty()) {
                    log.info("工单 {} 成品行主键重建，已迁移工序的成品归属 {} 条", order.getOrderNo(), idRemap.size());
                }
            }
        }

        // 【2026-11-07 标准下推占用】成品行带「销售订单」来源行主键的（下推进入/选单带入的行都会写 sourceItemId）：
        // 按「该销售明细行被未作废工单占用的合计」重算已下推生产数量 —— 新建、编辑、删行、改量都走同一口径；
        // 超量整单拒绝；工单作废/删除时同一口径自动释放（见 voidBill / unvoidBill / deleteBill）。
        // 不影响：复制单（来源行主键已被前端清空）、手工单、MRP 下推（来源类型不是“销售订单”）。
        if (products != null) {
            for (WorkOrderProduct p : products) {
                if (p != null && p.getSourceItemId() != null && "销售订单".equals(p.getSourceType())) {
                    affectedSaleItemIds.add(p.getSourceItemId());
                }
            }
            syncSaleOrderTurnedProduce(affectedSaleItemIds);
        }

        if (materials != null && !materials.isEmpty()) {
            int lineNo = 10;
            for (WorkOrderMaterial material : materials) {
                // 前端把「成品行号」放在 orderProductId 里传上来；这里换成成品行的新主键，保证多成品行用料不混。
                // 归属只写非主键列 order_product_id；主键 order_item_id 必须留给自增 ——
                // 此前把成品行主键写进原料主键，两表主键序列独立、值域重合就会撞主键（保存报「数据重复」，2026-11-10 修复）。
                Integer productLineNo = material.getOrderProductId() == null ? null : material.getOrderProductId().intValue();
                material.setOrderProductId(productLineNo == null ? null : savedProductIdByLineNo.get(productLineNo));
                material.setOrderItemId(null);
                material.setOrderId(order.getOrderId());
                if (material.getLineNo() == null) {
                    material.setLineNo(lineNo);
                    lineNo += 10;
                }
                if (material.getSupplyType() == null) material.setSupplyType("领料");
                applyMaterialNetAmount(material);
                materialMapper.insert(material);
            }
        }

        // 关联场景(下推/页面选源单/已指定BOM)下原料明细为空时，按BOM自动展开用料清单。
        // 纯手工录物料(无源单、无BOM)不展开，交由提交/下达时校验拦截 —— 与金蝶一致：
        // 下推/选源单自动带出用料，手工录料则自行维护。
        boolean hasSource = (order.getSourceNo() != null && !order.getSourceNo().isBlank())
                || order.getBomId() != null;
        if (hasSource && (materials == null || materials.isEmpty())) {
            generateMaterialsFromBom(order);
            materials = materialMapper.selectList(
                    new QueryWrapper<WorkOrderMaterial>().eq("order_id", order.getOrderId()).orderByAsc("line_no"));
            order.setMaterials(materials);
            log.info("工单 {} 原料明细为空，已按BOM自动展开 {} 项", order.getOrderNo(),
                    materials != null ? materials.size() : 0);
        }

        // 口径统一（守则 11）：保存=草稿，保存链路不写任何状态、不释放生效影响；
        // 是否生效、是否需审批，全部由提交/审核链路（统一状态机基类）决定。
        // 工单工序清单同步（2026-09-27 第7期）：草稿可随意增删改序；生效后按锁定规则
        syncWorkOrderOperations(order, order.getOperations());

        log.info("工单{}: {} 成品明细{}项 物料明细{}项",
                isNew ? "新增" : "编辑", order.getOrderNo(),
                products != null ? products.size() : 0,
                materials != null ? materials.size() : 0);
    }

    /**
     * 【2026-11-07 标准下推占用】重算销售订单明细行的「已下推生产数量」。
     *
     * <p>口径：该明细行被所有<b>未作废</b>生产工单的成品行占用数量合计（含草稿/待审核/已生效）。
     * 新建、编辑、删行、改量、工单作废/删除/反作废统一走本方法，避免“只加不减”。</p>
     */
    private void syncSaleOrderTurnedProduce(Set<Long> saleItemIds) {
        if (saleItemIds == null || saleItemIds.isEmpty()) return;
        for (Long itemId : saleItemIds) {
            if (itemId == null) continue;
            SaleOrderItem saleItem = saleOrderItemMapper.selectById(itemId);
            if (saleItem == null) continue;
            BigDecimal total = workOrderProductMapper.sumProduceQtyBySourceItem(itemId);
            if (total == null) total = BigDecimal.ZERO;
            BigDecimal orderQty = saleItem.getQuantity() != null ? saleItem.getQuantity() : BigDecimal.ZERO;
            if (total.compareTo(orderQty) > 0) {
                SaleOrder so = saleItem.getOrderId() == null ? null : saleOrderMapper.selectById(saleItem.getOrderId());
                throw new BusinessException("销售订单[" + (so != null ? so.getOrderNo() : saleItem.getOrderId())
                        + "]第" + (saleItem.getLineNo() != null ? saleItem.getLineNo() : "-")
                        + "行累计下推生产数量 " + total.stripTrailingZeros().toPlainString()
                        + " 超过订单数量 " + orderQty.stripTrailingZeros().toPlainString()
                        + "，请调整本次数量");
            }
            saleItem.setTurnedProduceQuantity(total);
            saleOrderItemMapper.updateById(saleItem);
        }
    }

    /** 取工单成品行引用的销售订单明细行主键（作废/删除前先取，删除后成品行已不存在） */
    private Set<Long> saleItemIdsOfOrder(Long orderId) {
        Set<Long> ids = new HashSet<>();
        if (orderId == null) return ids;
        for (WorkOrderProduct p : workOrderProductMapper.selectByOrderId(orderId)) {
            if (p.getSourceItemId() != null && "销售订单".equals(p.getSourceType())) {
                ids.add(p.getSourceItemId());
            }
        }
        return ids;
    }

    /** 按工单当前成品行，重算其来源销售订单行的已下推生产数量（作废/反作废后调用） */
    private void recalcSaleOrderTurnedForOrder(Long orderId) {
        syncSaleOrderTurnedProduce(saleItemIdsOfOrder(orderId));
    }

    /** 数量对象转 BigDecimal（查询结果可能是 BigDecimal/Long/Double/String） */
    private static BigDecimal dec(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 工单工序清单与「工单工序」表同步（2026-09-27 第7期）。
     *
     * <p><b>用户口径</b>：「在单据生效之后，工序不允许再删除、新增、调整顺序；如单据生效之前没有选择过工序支持选择；
     * 带了进度模板生效的不再支持修改；草稿状态允许随时修改」。</p>
     *
     * <p>落到实现：<br>
     * ① 草稿 → 全删全插（草稿期工序还没有任何进度，安全）<br>
     * ② 已生效且表里已有工序 → <b>锁死</b>，忽略本次提交的清单<br>
     * ③ 已生效但表里一道工序都没有（生效时没配）→ 允许<b>补选追加</b><br>
     * ④ 未携带工序清单（materials 式 null 语义）→ 完全不动既有数据</p>
     */
    private void syncWorkOrderOperations(WorkOrder order, List<WorkOrderOperation> submitted) {
        if (submitted == null) return;
        Long orderId = order.getOrderId();
        if (orderId == null) return;

        boolean isDraft = order.getStatus() == null || "草稿".equals(order.getStatus())
                || "DRAFT".equals(order.getStatus());
        long existing = workOrderOperationMapper.selectCount(
                new QueryWrapper<WorkOrderOperation>().eq("order_id", orderId));

        if (!isDraft && existing > 0) {
            log.info("工单 {} 已生效且已有 {} 道工序 → 工序清单锁定，忽略本次提交的 {} 道",
                    order.getOrderNo(), existing, submitted.size());
            return;
        }

        // 过滤掉没有工序名的空行（工序名称为空无法落库展示）
        List<WorkOrderOperation> rows = new ArrayList<>();
        for (WorkOrderOperation op : submitted) {
            if (op != null && op.getOperationName() != null && !op.getOperationName().isBlank()) {
                rows.add(op);
            }
        }

        // 计划数量默认取"所属成品行的数量"（2026-09-27 第10期），取不到再回落整单数量
        Map<Long, BigDecimal> productQty = new HashMap<>();
        List<WorkOrderProduct> products = workOrderProductMapper.selectByOrderId(orderId);
        if (products != null) {
            for (WorkOrderProduct p : products) {
                productQty.put(p.getId(), p.getQuantity());
            }
        }

        // 【2026-11-25】工序自定义字段：草稿保存是"全删全插"（行主键会变），
        // 必须在删除前把旧行的自定义字段值按旧行ID缓存，重建后按旧ID跟到新行，否则保存一次值就丢。
        List<WorkOrderOperation> oldOps = existing > 0 ? workOrderOperationMapper.selectByOrderId(orderId) : List.of();
        List<Long> oldOpIds = new ArrayList<>();
        for (WorkOrderOperation old : oldOps) {
            if (old.getId() != null) oldOpIds.add(old.getId());
        }
        Map<Long, Map<String, Object>> oldCustomValues = new HashMap<>();
        if (!oldOpIds.isEmpty()) {
            oldCustomValues.putAll(customFieldService.batchGetFieldValues("work_order_operation", oldOpIds));
        }
        Set<Long> carriedOldIds = new HashSet<>();

        workOrderOperationMapper.delete(new QueryWrapper<WorkOrderOperation>().eq("order_id", orderId));

        // 按成品明细行分组落库：**组内各自从 10 开始编号**（不同成品互不干扰），组内自动补前道/后道行号
        Map<Long, List<WorkOrderOperation>> grouped = new LinkedHashMap<>();
        for (WorkOrderOperation src : rows) {
            grouped.computeIfAbsent(src.getOrderProductId(), k -> new ArrayList<>()).add(src);
        }
        int saved = 0;
        // 【第 13 期】本次落库的工序行（父子关系需在拿到自增主键后统一解析）
        List<WorkOrderOperation> inserted = new ArrayList<>();
        for (Map.Entry<Long, List<WorkOrderOperation>> entry : grouped.entrySet()) {
            Long productId = entry.getKey();
            List<WorkOrderOperation> group = entry.getValue();
            for (int i = 0; i < group.size(); i++) {
                WorkOrderOperation src = group.get(i);
                int lineNo = (i + 1) * 10;
                WorkOrderOperation row = new WorkOrderOperation();
                row.setOrderId(orderId);
                row.setOrderNo(order.getOrderNo());
                row.setOrderProductId(productId);
                row.setRoutingId(src.getRoutingId() != null ? src.getRoutingId() : order.getRoutingId());
                row.setOperationId(src.getOperationId());
                // 【2026-11-26】工序稳定标识随工序清单落库：操作面板与打印据此取该工序自己的字段
                row.setRoutingOperationKey(src.getRoutingOperationKey());
                row.setOperationCode(src.getOperationCode());
                row.setOperationName(src.getOperationName());
                row.setLineNo(lineNo);
                row.setWorkCenterId(src.getWorkCenterId());
                row.setWorkstationId(src.getWorkstationId());
                row.setOperationType(src.getOperationType() == null || src.getOperationType().isBlank()
                        ? "状态类型" : src.getOperationType());
                BigDecimal qty = src.getPlanQuantity();
                if (qty == null && productId != null) qty = productQty.get(productId);
                if (qty == null) qty = order.getQuantity();
                row.setPlanQuantity(qty);
                row.setPlanEndDate(src.getPlanEndDate());
                row.setAssigneeId(src.getAssigneeId());
                row.setAssigneeName(src.getAssigneeName());
                row.setIsQcRequired(src.getIsQcRequired() == null ? 0 : src.getIsQcRequired());
                // 第 12 期：本道工序绑定的质检模板（可空 = 不绑定，运行时按物料/工序解析）
                row.setQcTemplateId(src.getQcTemplateId());
                row.setIsCritical(src.getIsCritical() == null ? 0 : src.getIsCritical());
                row.setPieceRate(src.getPieceRate());
                // 计税口径（2026-10-09）：按表头「计税方式」由「计件单价」换算 含税价 / 不含税价
                BigDecimal[] opPrices = convertPriceByTaxType(row.getPieceRate(), null,
                        resolveTaxType(order.getTaxCalcMethod()));
                row.setPriceWithTax(opPrices[0]);
                row.setPriceWithoutTax(opPrices[1]);
                // 【第 13 期】父子工序 + 金额（金额类型）随工序清单一并落库
                row.setParentLineNo(src.getParentLineNo());
                row.setPlanAmount(WorkOperationStatus.TYPE_AMOUNT.equals(row.getOperationType())
                        ? src.getPlanAmount() : null);
                row.setConfirmedAmount(WorkOperationStatus.TYPE_AMOUNT.equals(row.getOperationType())
                        ? (src.getConfirmedAmount() != null ? src.getConfirmedAmount() : BigDecimal.ZERO) : null);
                row.setSetupHours(src.getSetupHours());
                row.setProcessHours(src.getProcessHours());
                // 【2026-10-30】工序清单里已填负责人 → 状态直接「已派工」，避免"有负责人却停在待派工"
                // 导致手机端/电脑端都不出现数量报工（用户实报：认领后无报工入口）
                row.setOperationStatus(row.getAssigneeId() != null
                        ? WorkOperationStatus.ASSIGNED : WorkOperationStatus.PENDING_ASSIGN);
                // 相邻工序衔接（组内）：看板连线与"前道未完工不可开工"守卫都依赖它
                row.setPrevLineNo(i == 0 ? null : lineNo - 10);
                row.setNextLineNo(i == group.size() - 1 ? null : lineNo + 10);
                workOrderOperationMapper.insert(row);
                inserted.add(row);
                saved++;
                // 【2026-11-25】工序自定义字段随工序行重建：优先用本次提交的值；没提交则按旧行ID迁移旧值
                Long oldId = src.getId();
                Map<String, Object> customValues = src.getCustomFields();
                if ((customValues == null || customValues.isEmpty()) && oldId != null) {
                    customValues = oldCustomValues.get(oldId);
                }
                if (customValues != null && !customValues.isEmpty()) {
                    customFieldService.saveFieldValues("work_order_operation", row.getId(), customValues);
                }
                if (oldId != null) carriedOldIds.add(oldId);
            }
        }
        // 【第 13 期】父子解析：按「成品行 + 行号」复合键解析 parent_row_id（键含成品行 → 跨成品行解析不到，自动降级顶层）
        Map<String, Long> idByProductLine = new HashMap<>();
        for (WorkOrderOperation r : inserted) {
            if (r.getLineNo() != null) {
                idByProductLine.put(productLineKey(r.getOrderProductId(), r.getLineNo()), r.getId());
            }
        }
        for (WorkOrderOperation r : inserted) {
            if (r.getParentLineNo() == null) continue;
            Long parentId = idByProductLine.get(productLineKey(r.getOrderProductId(), r.getParentLineNo()));
            if (parentId == null || parentId.equals(r.getId())) continue;
            r.setParentRowId(parentId);
            workOrderOperationMapper.updateById(r);
        }
        // 【2026-11-25】用户删掉的旧工序行：同步清理其自定义字段值，避免孤儿数据（删除失败只告警，不影响整单保存）
        for (Long oldId : oldOpIds) {
            if (carriedOldIds.contains(oldId)) continue;
            try {
                customFieldService.deleteFieldValues("work_order_operation", oldId);
            } catch (Exception e) {
                log.warn("清理已删除工序的自定义字段值失败 opId={}: {}", oldId, e.getMessage());
            }
        }
        log.info("工单{} {} 工序清单已保存 {} 道 / {} 个成品行（草稿={}，原{}道）",
                order.getOrderNo(), isDraft ? "草稿" : "生效后补选", saved, grouped.size(), isDraft, existing);
    }

    /**
     * 表头「计税方式」归一：含税价=1、不含税价=2（默认）、不计税=3；兼容历史值 价内税→含税价、价外税→不含税价。
     * （2026-10-09 计税口径铺开，与销售订单试点同口径。）
     */
    private int resolveTaxType(String method) {
        if (method == null || method.isBlank()) return TAX_TYPE_EXCLUSIVE;
        String v = method.trim();
        if ("不计税".equals(v)) return TAX_TYPE_NO_TAX;
        if ("含税价".equals(v) || "价内税".equals(v)) return TAX_TYPE_INCLUSIVE;
        return TAX_TYPE_EXCLUSIVE;
    }

    /** 按表头「计税方式」把「单价」换算成 含税价 / 不含税价（返回值 [含税价, 不含税价]） */
    private BigDecimal[] convertPriceByTaxType(BigDecimal unitPrice, BigDecimal taxRate, int taxType) {
        BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        BigDecimal rate = taxRate != null ? taxRate : BigDecimal.ZERO;
        BigDecimal factor = BigDecimal.ONE.add(rate.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        BigDecimal withTax;
        BigDecimal withoutTax;
        if (taxType == TAX_TYPE_INCLUSIVE) {
            withTax = price;
            withoutTax = factor.compareTo(BigDecimal.ZERO) != 0
                    ? price.divide(factor, 6, RoundingMode.HALF_UP) : price;
        } else if (taxType == TAX_TYPE_NO_TAX) {
            withTax = price;
            withoutTax = price;
        } else {
            withoutTax = price;
            withTax = price.multiply(factor);
        }
        return new BigDecimal[]{
                withTax.setScale(6, RoundingMode.HALF_UP),
                withoutTax.setScale(6, RoundingMode.HALF_UP)
        };
    }

    /** 【第 13 期】父子解析用的复合键：成品行 + 工序行号（成品行为空时用 0 兜底） */
    private static String productLineKey(Long orderProductId, Integer lineNo) {
        return (orderProductId == null ? 0L : orderProductId) + "#" + lineNo;
    }

    @Override
    @Transactional
    public List<WorkOrderOperation> saveOrderOperations(Long orderId, List<WorkOrderOperation> operations) {
        WorkOrder order = getById(orderId);
        if (order == null) throw new BusinessException("工单不存在");
        long existing = workOrderOperationMapper.selectCount(
                new QueryWrapper<WorkOrderOperation>().eq("order_id", orderId));
        boolean isDraft = order.getStatus() == null || "草稿".equals(order.getStatus())
                || "DRAFT".equals(order.getStatus());
        // 已生效且已有工序 → 锁死（用户口径：生效后不允许删除/新增/调整顺序）
        if (!isDraft && existing > 0) {
            throw new BusinessException("单据已生效且已配置工序，工序清单不允许再增删改序");
        }
        syncWorkOrderOperations(order, operations);
        return workOrderOperationMapper.selectByOrderId(orderId);
    }

    @Override
    public PageResult<WorkOrder> pageQuery(int current, int size, String keyword, String status, String priority,
                                           String startDate, String endDate, String employeeId,
                                           String sortBy, String sortOrder) {
        // 老签名委托新重载（headerFilters/customSearch 空 = 行为与改造前完全一致）
        return pageQuery(current, size, keyword, status, priority, startDate, endDate, employeeId,
                sortBy, sortOrder, java.util.Collections.emptyMap(), null);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）：
     * 白名单 + 参数化，非法/空白/白名单外入参在支持类内部忽略，不阻断列表查询。
     */
    @Override
    public PageResult<WorkOrder> pageQuery(int current, int size, String keyword, String status, String priority,
                                           String startDate, String endDate, String employeeId,
                                           String sortBy, String sortOrder, Map<String, String> headerFilters,
                                           String customSearch) {
        QueryWrapper<WorkOrder> wrapper = new QueryWrapper<>();
        // 关键词多列强制成组（平铺 or() 会让命中第一列的行绕过状态筛选与数据权限）
        com.erp.common.query.ListQuerySupport.applyKeywordGroup(wrapper, keyword,
                "order_no", "material_code", "material_name");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        if (priority != null && !priority.isBlank()) {
            wrapper.eq("priority", priority);
        }
        // 单据日期区间（order_date）
        com.erp.common.query.ListQuerySupport.applyDateRange(wrapper, "order_date", startDate, endDate);
        // 员工维度：统一入参 employeeId → 本页「负责人」列 manager_id
        com.erp.common.query.ListQuerySupport.applyEmployee(wrapper, employeeId, "manager_id");
        // 表头字段过滤：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "workOrder", headerFilters);
        // 数据权限：按角色 data_scope（统一数据权限）
        dataScopeResolver.applyIfLimited(wrapper, null, "created_by");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        com.erp.common.util.CustomFieldSearchHelper.apply(wrapper, "production_order", customSearch,
                com.erp.common.util.CustomFieldSearchHelper.idColumnOf("production_order"));
        if (!com.erp.common.query.ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, WorkOrder.class);
        Page<WorkOrder> page = new Page<>(current, size);
        Page<WorkOrder> result = page(page, wrapper);
        fillHeaderProductInfo(result.getRecords());
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 列表兜底（2026-10-26）：历史手工工单表头 material_code/material_name 为空时，
     * 用该工单成品明细的第一行补全，保证汇总模式列表能显示产品编码/产品名称（不改库，只补展示值）。
     */
    private void fillHeaderProductInfo(List<WorkOrder> records) {
        if (records == null || records.isEmpty()) return;
        List<Long> needIds = new ArrayList<>();
        for (WorkOrder r : records) {
            if (r != null && r.getOrderId() != null
                    && (isBlankText(r.getMaterialCode()) || isBlankText(r.getMaterialName()))) {
                needIds.add(r.getOrderId());
            }
        }
        if (needIds.isEmpty()) return;
        List<WorkOrderProduct> products = workOrderProductMapper.selectList(
                new QueryWrapper<WorkOrderProduct>().in("order_id", needIds)
                        .orderByAsc("order_id").orderByAsc("line_no"));
        Map<Long, WorkOrderProduct> firstByOrder = new HashMap<>();
        for (WorkOrderProduct p : products) {
            if (p != null && p.getOrderId() != null) firstByOrder.putIfAbsent(p.getOrderId(), p);
        }
        for (WorkOrder r : records) {
            if (r == null || r.getOrderId() == null) continue;
            WorkOrderProduct first = firstByOrder.get(r.getOrderId());
            if (first == null) continue;
            if (isBlankText(r.getMaterialCode())) r.setMaterialCode(first.getMaterialCode());
            if (isBlankText(r.getMaterialName())) r.setMaterialName(first.getMaterialName());
            if (isBlankText(r.getModel())) {
                r.setModel(first.getModel() != null ? first.getModel() : first.getSpecification());
            }
        }
    }

    private static boolean isBlankText(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public WorkOrder getDetail(Long id) {
        WorkOrder order = getById(id);
        if (order == null) throw new BusinessException("工单不存在");
        List<WorkOrderMaterial> materials = materialMapper.selectList(
                new QueryWrapper<WorkOrderMaterial>().eq("order_id", id).orderByAsc("line_no"));
        order.setMaterials(materials);
        // 查询成品明细
        List<WorkOrderProduct> products = workOrderProductMapper.selectByOrderId(id);
        order.setProducts(products);
        // 【2026-11-07】下推占用数量（含草稿/待审核）回填给选单弹窗，实际已领料/已入库仍取生效累计
        fillPushedQuantities(order, materials, products);
        // 查询工单工序清单（2026-09-27 第7期）：编辑页「工序明细」按它渲染与配置
        order.setOperations(workOrderOperationMapper.selectByOrderId(id));
        return order;
    }

    /** 回填工单明细行的「已下推」数量（下游草稿+待审核+已生效 − 已作废，按行汇总） */
    private void fillPushedQuantities(WorkOrder order, List<WorkOrderMaterial> materials, List<WorkOrderProduct> products) {
        if (products != null && !products.isEmpty()) {
            Map<Integer, BigDecimal> byLine = new HashMap<>();
            for (Map<String, Object> row : workOrderProductMapper.sumPushedReceiptByLine(order.getOrderId())) {
                Object lineNo = row.get("lineNo");
                if (lineNo != null) byLine.put(Integer.valueOf(String.valueOf(lineNo)), dec(row.get("qty")));
            }
            for (WorkOrderProduct p : products) {
                // 无行级下游记录时保持 null（前端回退实际已入库，兼容历史单据）
                p.setPushedQuantity(p.getLineNo() == null ? null : byLine.get(p.getLineNo()));
            }
        }
        if (materials != null && !materials.isEmpty() && order.getOrderNo() != null) {
            Map<Integer, BigDecimal> byLine = new HashMap<>();
            for (Map<String, Object> row : materialMapper.sumPushedPickByLine(order.getOrderNo())) {
                Object lineNo = row.get("lineNo");
                if (lineNo != null) byLine.put(Integer.valueOf(String.valueOf(lineNo)), dec(row.get("qty")));
            }
            for (WorkOrderMaterial m : materials) {
                // 无行级下游记录时保持 null（前端回退实际已领料，兼容历史单据）
                m.setPushedQuantity(m.getLineNo() == null ? null : byLine.get(m.getLineNo()));
            }
        }
    }

    @Override
    @Transactional
    public void submit(Long id, String operator) {
        // 提交校验 + 审批开关 + 免审批直通 + 创建审批待办，全部由统一状态机执行
        super.submitForApprovalById(id, operator);
    }

    /**
     * 审核: 待审核 → 已生效（并生成工序实例）。
     * 审批引擎回调同样走本方法；「审批在途禁止绕过」防护在此保留。
     */
    @Override
    @Transactional
    public void approve(Object id, String operator) {
        if (approvalRuntimeService.getRunningTask("PRODUCTION_ORDER", (Long) id) != null) {
            throw new BusinessException("生产工单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, operator != null && !operator.isBlank() ? operator : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String operator) {
        approve((Object) id, operator);
    }

    /** 用料清单准备：基于BOM自动展开 + 「必须有原料明细」的提交校验 */
    private void ensureMaterials(WorkOrder order) {
        // 基于BOM自动生成用料清单: 仅在用料清单为空时展开, 不覆盖用户已维护的原料明细
        List<WorkOrderMaterial> existingMaterials = materialMapper.selectList(
                new QueryWrapper<WorkOrderMaterial>().eq("order_id", order.getOrderId()));
        if (existingMaterials == null || existingMaterials.isEmpty()) {
            if (order.getBomId() != null || (order.getSourceNo() != null && !order.getSourceNo().isBlank())) {
                generateMaterialsFromBom(order);
                existingMaterials = materialMapper.selectList(
                        new QueryWrapper<WorkOrderMaterial>().eq("order_id", order.getOrderId()));
            }
        }
        // 提交(下达)校验: 必须有原料明细, 否则领料/生产无从执行
        if (existingMaterials == null || existingMaterials.isEmpty()) {
            throw new BusinessException("未选择原料明细，请先添加原料明细或选择来源单/BOM后再下达");
        }
    }

    /** 审核生效后的业务影响：基于工艺路线生成工序实例（状态/审核人由统一状态机写入） */
    private void executeRelease(WorkOrder order, String operator) {
        // 工单工序实例（2026-09-27 第7期）：
        //   ① 工单里已配好工序（草稿期配的 / 从工艺路线带出的）→ 直接沿用，不再按工艺路线覆盖（用户口径）
        //   ② 一道都没有且选了工艺路线 → 仍按工艺路线生成（兜底，保持旧行为）
        long existingOps = workOrderOperationMapper.selectCount(
                new QueryWrapper<WorkOrderOperation>().eq("order_id", order.getOrderId()));
        if (existingOps == 0) {
            // 【2026-11-26】整单路线为空时不能直接跳过：第10期起路线挂在「成品明细行」上，
            // 只要任一成品行选了路线，就必须按该行路线生成工序（否则多成品工单生效后一道工序都没有）
            boolean hasRouting = order.getRoutingId() != null;
            if (!hasRouting) {
                List<WorkOrderProduct> productRows = workOrderProductMapper.selectByOrderId(order.getOrderId());
                hasRouting = productRows != null && productRows.stream()
                        .anyMatch(p -> p != null && p.getRoutingId() != null);
            }
            if (hasRouting) {
                generateOperationsFromRouting(order);
            }
        } else {
            log.info("工单 {} 生效：沿用已配置的 {} 道工序（不按工艺路线覆盖）", order.getOrderNo(), existingOps);
        }

        // 化妆品批记录：工单生效时预生成批生产记录头，并固化配方/工艺路线快照（失败不阻断生效）
        try {
            batchRecordService.ensureRecord(order.getOrderId());
        } catch (Exception e) {
            log.warn("工单 {} 批生产记录预生成失败（不阻断生效）: {}", order.getOrderNo(), e.getMessage());
        }

        log.info("工单生效: {} 操作人: {}", order.getOrderNo(), operator);
    }

    /**
     * 基于工艺路线快照生成工单工序实例（2026-09-27 第10期：**按成品明细行逐个生成**）。
     *
     * <p>一张工单可能有多个商品（成品明细多行），每个商品各自一套工序：因此先清空，再按成品行循环，
     * 每行用**它自己的工艺路线**；成品行没选路线时回落到整单的工艺路线。没有成品明细的老工单
     * 仍按整单生成一条工序线（兼容历史数据）。</p>
     */
    private void generateOperationsFromRouting(WorkOrder order) {
        // 先清除既有工序实例 (避免重复下达生成多份)
        workOrderOperationMapper.delete(
                new QueryWrapper<WorkOrderOperation>().eq("order_id", order.getOrderId()));

        List<WorkOrderProduct> products = workOrderProductMapper.selectByOrderId(order.getOrderId());
        if (products == null || products.isEmpty()) {
            generateRoutingOpsForOne(order, null, order.getRoutingId(),
                    order.getQuantity() != null ? order.getQuantity() : BigDecimal.ZERO);
            return;
        }
        for (WorkOrderProduct product : products) {
            Long routingId = product.getRoutingId() != null ? product.getRoutingId() : order.getRoutingId();
            if (routingId == null) continue;
            BigDecimal planQty = product.getQuantity() != null ? product.getQuantity() : BigDecimal.ZERO;
            generateRoutingOpsForOne(order, product.getId(), routingId, planQty);
        }
    }

    /** 为「一个成品行」（orderProductId 可为空 = 老工单整单）按其工艺路线生成工序实例 */
    private void generateRoutingOpsForOne(WorkOrder order, Long orderProductId, Long routingId, BigDecimal planQty) {
        if (routingId == null) return;

        List<RoutingOperation> routingOperations = routingOperationMapper.selectList(
                new QueryWrapper<RoutingOperation>()
                        .eq("routing_id", routingId)
                        .orderByAsc("line_no"));
        if (routingOperations == null || routingOperations.isEmpty()) {
            return;
        }
        if (planQty == null) planQty = BigDecimal.ZERO;
        LocalDate planStart = order.getPlanStartDate() != null ? order.getPlanStartDate() : LocalDate.now();
        LocalDate workOrderPlanEnd = order.getPlanEndDate();

        int size = routingOperations.size();
        // 【第 13 期】本次为「该成品行」生成的工序实例（落库后统一解析父子关系用）
        List<WorkOrderOperation> created = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            RoutingOperation ro = routingOperations.get(i);
            WorkOrderOperation op = new WorkOrderOperation();
            op.setOrderId(order.getOrderId());
            op.setOrderNo(order.getOrderNo());
            op.setRoutingId(routingId);
            op.setOrderProductId(orderProductId);
            op.setOperationId(ro.getOperationId());
            // 【2026-11-26】带出工艺路线工序稳定标识，供工序自己的自定义字段取值
            op.setRoutingOperationKey(ro.getOperationKey());
            op.setOperationCode(ro.getOperationCode());
            op.setOperationName(ro.getOperationName());
            op.setLineNo(ro.getLineNo());
            op.setWorkCenterId(ro.getWorkCenterId());
            op.setWorkstationId(ro.getWorkstationId());
            op.setSetupHours(ro.getSetupHours());
            // 【工时体系 2026-10-07】单件加工工时 → 本工序总工时：
            // 路线填了 unitHours（默认带出物料标准工时）→ process_hours = unitHours × 该成品行计划数量；
            // 未填（0/空）→ 沿用原 process_hours（老行为，存量路线零影响，APS 口径不变）。
            if (ro.getUnitHours() != null && ro.getUnitHours().compareTo(BigDecimal.ZERO) > 0) {
                op.setProcessHours(ro.getUnitHours().multiply(planQty));
            } else {
                op.setProcessHours(ro.getProcessHours());
            }
            op.setPlanQuantity(planQty);
            op.setReportedQuantity(BigDecimal.ZERO);
            op.setRejectedQuantity(BigDecimal.ZERO);
            // 工序状态统一口径（2026-09-23 第 2 期）：新生成的工序实例从"待派工"开始
            op.setOperationStatus(com.erp.production.service.WorkOperationStatus.PENDING_ASSIGN);
            op.setIsOutsourced(ro.getIsOutsourced() != null ? ro.getIsOutsourced().intValue() : 0);
            op.setIsQcRequired(ro.getIsQcRequired() != null ? ro.getIsQcRequired().intValue() : 0);
            op.setIsCritical(ro.getIsCritical() != null ? ro.getIsCritical().intValue() : 0);
            // 工序类型 / 计件单价随快照带到工单（工序操作面板按类型渲染；计件单价供工序工资核算）
            op.setOperationType(ro.getOperationType() != null && !ro.getOperationType().isBlank()
                    ? ro.getOperationType() : "状态类型");
            op.setPieceRate(ro.getPieceRate() != null ? ro.getPieceRate() : BigDecimal.ZERO);
            // 投入/产出初始为 0：首道投入 = 工单数量 由工序操作时写入（第 2 期操作面板）
            op.setInputQuantity(BigDecimal.ZERO);
            op.setOutputQuantity(BigDecimal.ZERO);
            // 前后道工序行号
            op.setPrevLineNo(ro.getPreviousOperationLineNo());
            op.setNextLineNo(ro.getNextOperationLineNo());
            // 【第 13 期】父子工序快照：父行号先带过来，落库后在下方按"本成品行内 line_no"解析成父工序行ID
            op.setParentLineNo(ro.getParentLineNo());
            // 【第 13 期】计划金额（仅「金额类型」工序）；已确认金额从 0 起累加
            op.setPlanAmount(WorkOperationStatus.TYPE_AMOUNT.equals(op.getOperationType()) ? ro.getPlanAmount() : null);
            op.setConfirmedAmount(WorkOperationStatus.TYPE_AMOUNT.equals(op.getOperationType())
                    ? BigDecimal.ZERO : null);
            // 计划日期: 基于工单计划开始日期 + 工序累计工时推算 (按8小时/天简化计算)
            BigDecimal cumulativeHours = BigDecimal.ZERO;
            for (int j = 0; j <= i; j++) {
                RoutingOperation roj = routingOperations.get(j);
                BigDecimal sh = roj.getSetupHours() != null ? roj.getSetupHours() : BigDecimal.ZERO;
                BigDecimal ph = roj.getProcessHours() != null ? roj.getProcessHours() : BigDecimal.ZERO;
                cumulativeHours = cumulativeHours.add(sh).add(ph);
            }
            // 当道工序累计工时(含本工序)
            BigDecimal prevCumulativeHours = BigDecimal.ZERO;
            if (i > 0) {
                for (int j = 0; j < i; j++) {
                    RoutingOperation roj = routingOperations.get(j);
                    BigDecimal sh = roj.getSetupHours() != null ? roj.getSetupHours() : BigDecimal.ZERO;
                    BigDecimal ph = roj.getProcessHours() != null ? roj.getProcessHours() : BigDecimal.ZERO;
                    prevCumulativeHours = prevCumulativeHours.add(sh).add(ph);
                }
            }
            op.setPlanStartDate(plusWorkingDays(planStart, prevCumulativeHours));
            op.setPlanEndDate(plusWorkingDays(planStart, cumulativeHours));
            // 若推算的工序完成日期超过工单计划完成日期, 以工单计划完成日期为上限
            if (workOrderPlanEnd != null && op.getPlanEndDate() != null
                    && op.getPlanEndDate().isAfter(workOrderPlanEnd)) {
                op.setPlanEndDate(workOrderPlanEnd);
            }
            workOrderOperationMapper.insert(op);
            created.add(op);
        }
        // 【第 13 期】父子解析：把 parent_line_no 解析成 parent_row_id。
        // 解析表只装本方法生成的工序 → 父子关系天然限制在「同一成品行」内，跨成品行的引用一律解析不到、自动降级为顶层。
        Map<Integer, Long> idByLine = new HashMap<>();
        for (WorkOrderOperation o : created) {
            if (o.getLineNo() != null) idByLine.put(o.getLineNo(), o.getId());
        }
        int linked = 0;
        for (WorkOrderOperation o : created) {
            if (o.getParentLineNo() == null) continue;
            Long parentId = idByLine.get(o.getParentLineNo());
            if (parentId == null || parentId.equals(o.getId())) continue;
            o.setParentRowId(parentId);
            workOrderOperationMapper.updateById(o);
            linked++;
        }
        log.info("工单 {} 成品行 {} 基于工艺路线 {} 生成 {} 道工序实例（父子工序 {} 道已解析）",
                order.getOrderNo(), orderProductId, routingId, size, linked);
    }

    /** 按工时累加工作日 (简化: 1个工作日 = 8小时, 不跳过周末) */
    private LocalDate plusWorkingDays(LocalDate baseDate, BigDecimal hours) {
        if (hours == null || hours.compareTo(BigDecimal.ZERO) <= 0) {
            return baseDate;
        }
        BigDecimal days = hours.divide(BigDecimal.valueOf(8), 0, RoundingMode.HALF_UP);
        return baseDate.plusDays(days.longValue());
    }

    /** 基于BOM展开自动生成用料清单（生成/下达工单时都需要，故对外暴露） */
    @Override
    public void generateMaterialsFromBom(WorkOrder order) {
        // 先清除既有明细
        materialMapper.delete(new QueryWrapper<WorkOrderMaterial>().eq("order_id", order.getOrderId()));

        // 多成品场景: 优先基于成品明细展开BOM, 没有成品明细时回退用表头(兼容旧数据)
        List<WorkOrderProduct> products = workOrderProductMapper.selectByOrderId(order.getOrderId());
        int lineNo = 10;
        if (products != null && !products.isEmpty()) {
            for (WorkOrderProduct product : products) {
                Long bomId = resolveProductBomId(order, product);
                if (bomId == null) {
                    continue;
                }
                BigDecimal productQty = product.getQuantity() != null ? product.getQuantity() : BigDecimal.ZERO;
                lineNo = expandBomToMaterials(order.getOrderId(), bomId, productQty, lineNo, product.getId());
            }
            return;
        }

        // 回退: 单成品场景, 用表头 bomId + 工单计划数量展开
        if (order.getBomId() == null) {
            return;
        }
        BigDecimal planQty = order.getQuantity() != null ? order.getQuantity() : BigDecimal.ONE;
        expandBomToMaterials(order.getOrderId(), order.getBomId(), planQty, lineNo, null);
    }

    /** 解析成品对应的BOM ID：成品行 BOM 优先 → 单成品回退整单 BOM → 物料主 BOM */
    private Long resolveProductBomId(WorkOrder order, WorkOrderProduct product) {
        // 【2026-11-07】行级 BOM 优先：成品明细新增「BOM清单/版本」后，每行按自己选的 BOM 展开原料。
        // 多成品时整单 BOM 只代表其中一行，不能套到其它成品行（否则第二件成品用错料）。
        if (product.getBomId() != null) {
            return product.getBomId();
        }
        Long productCount = workOrderProductMapper.selectCount(
                new QueryWrapper<WorkOrderProduct>().eq("order_id", order.getOrderId()));
        if ((productCount == null || productCount <= 1) && order.getBomId() != null) {
            return order.getBomId();
        }
        if (product.getMaterialId() == null) {
            return null;
        }
        // 自动匹配只认「已生效」的主 BOM，并按 bom_id 倒序取最新版本（对齐金蝶"自动带出已生效的最新版本BOM"）。
        // LIMIT 1 必须有：同一物料存在多条主 BOM 时 selectOne 会抛 TooManyResultsException。
        Bom primaryBom = bomMapper.selectOne(
                new QueryWrapper<Bom>()
                        .eq("material_id", product.getMaterialId())
                        .eq("is_primary", (short) 1)
                        .eq("status", "已生效")
                        .orderByDesc("bom_id")
                        .last("LIMIT 1"));
        return primaryBom != null ? primaryBom.getBomId() : null;
    }

    /** 价税合计补齐（2026-11-23）：优先「含税价 × 应发数量」，无价时回落金额 */
    private void applyMaterialNetAmount(WorkOrderMaterial material) {
        BigDecimal qty = material.getRequiredQuantity() != null ? material.getRequiredQuantity() : BigDecimal.ZERO;
        BigDecimal withTax = material.getPriceWithTax();
        if (withTax != null) {
            material.setNetAmount(withTax.multiply(qty).setScale(2, RoundingMode.HALF_UP));
        } else {
            material.setNetAmount(material.getTotalCost() != null
                    ? material.getTotalCost().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        }
    }

    /** 将指定 BOM 展开为工单用料明细, 返回下一行号 */
    private int expandBomToMaterials(Long orderId, Long bomId, BigDecimal planQty, int startLineNo, Long orderProductId) {
        List<BomItem> bomItems = bomItemMapper.selectList(
                new QueryWrapper<BomItem>().eq("bom_id", bomId).orderByAsc("line_no"));
        if (bomItems == null || bomItems.isEmpty()) {
            return startLineNo;
        }
        int lineNo = startLineNo;
        for (BomItem bomItem : bomItems) {
            WorkOrderMaterial material = new WorkOrderMaterial();
            material.setOrderId(orderId);
            // 归属成品行（多成品各自一套用料）；单成品回退场景没有成品行，留空
            material.setOrderProductId(orderProductId);
            material.setLineNo(lineNo);
            material.setMaterialId(bomItem.getMaterialId());
            material.setMaterialCode(bomItem.getMaterialCode());
            material.setMaterialName(bomItem.getMaterialName());
            material.setSpecification(bomItem.getSpecification());
            material.setUnitId(bomItem.getUnitId());
            // BOM基本用量
            material.setBomQuantity(bomItem.getQuantity() != null ? bomItem.getQuantity() : BigDecimal.ZERO);
            // 应发数量 = BOM基本用量 × 工单计划数量 (考虑损耗率)
            BigDecimal scrapRate = bomItem.getScrapRate() != null ? bomItem.getScrapRate() : BigDecimal.ZERO;
            BigDecimal baseQty = material.getBomQuantity();
            BigDecimal requiredQty = baseQty.multiply(planQty)
                    .multiply(BigDecimal.ONE.add(scrapRate.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                    .setScale(4, RoundingMode.HALF_UP);
            material.setRequiredQuantity(requiredQty);
            material.setIssuedQuantity(BigDecimal.ZERO);
            material.setReturnedQuantity(BigDecimal.ZERO);
            material.setConsumedQuantity(BigDecimal.ZERO);
            material.setScrapQuantity(BigDecimal.ZERO);
            material.setSupplyType(bomItem.getSupplyType() != null ? bomItem.getSupplyType() : "领料");
            material.setProcessNo(bomItem.getProcessNo());
            applyMaterialNetAmount(material);
            materialMapper.insert(material);
            lineNo += 10;
        }
        return lineNo;
    }

    /** 结案：已生效且未结案 → close_status=1（is_closed 由 onClosed 同步） */
    @Override
    @Transactional
    public void close(Long id, String operator, String reason) {
        WorkOrder order = getById(id);
        if (order == null) throw new BusinessException("工单不存在");
        if (reason != null && !reason.isBlank()) {
            String existing = order.getRemark();
            order.setRemark((existing != null && !existing.isBlank() ? existing + " | " : "") + "结案: " + reason);
            updateById(order);
        }
        super.close(id, operator, reason);
    }

    /** 驳回: 待审核 → 草稿（状态校验/审批在途防护由统一状态机执行） */
    @Override
    @Transactional
    public void rejectBill(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
    }

    // ============ 统一状态机：重置草稿 / 作废 / 反作废 / 反结案 / 删除 ============
    // 生产工单走全站通用状态机：草稿 →（提交）→ 待审核 →（审批通过）→ 已生效；
    // 业务闭环用 close_status 独立表达（生产入库达标自动结案）。

    /** 重置草稿(撤销生效): 已生效且未结案 → 草稿（状态/下游校验由基类执行） */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        UpdateWrapper<WorkOrder> uw = new UpdateWrapper<>();
        uw.eq("order_id", id).set("approved_by", null).set("approved_at", null);
        update(uw);
    }

    /** 作废: 草稿/已生效类(未结案) → 已作废（状态迁移/作废前状态记录由基类执行） */
    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        WorkOrder order = getById(id);
        boolean draftBefore = order != null && BillStatusUtils.isDraft(order.getStatus());
        super.voidBill(id, operator, reason);
        // 草稿态作废不触发 onVoided，但草稿也占用销售订单排产数量，需单独释放
        if (draftBefore) {
            recalcSaleOrderTurnedForOrder(id);
        }
    }

    /** 反作废: 已作废 → 恢复作废前状态 */
    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        WorkOrder order = getById(id);
        Integer before = order != null ? order.getBillBeforeStatus() : null;
        super.unvoidBill(id, operator);
        // 恢复到非生效态时不触发 onUnvoided，需单独重新占用销售订单排产数量
        if (before != null && BillStatusUtils.toNumber(before) != BillStatusEnum.APPROVED.getCode()) {
            recalcSaleOrderTurnedForOrder(id);
        }
    }

    /** 反结案: 已结案 → 未结案（is_closed 由 onReopened 同步） */
    @Override
    @Transactional
    public void reopenOrder(Long id, String operator) {
        super.reopen(id, operator);
    }

    /** 删除工单(仅草稿态可删且无下游已生效单据, 连同用料/成品/工序明细一起删除) */
    @Override
    @Transactional
    public void deleteBill(Long id) {
        WorkOrder order = getById(id);
        if (order == null) return; // 幂等
        // 【2026-11-07 标准下推占用】删除前先记下来源销售明细行，删除后释放已排产数量
        Set<Long> affectedSaleItemIds = saleItemIdsOfOrder(id);
        // 草稿校验 + 下游校验 + 整单级联删除统一由基类执行
        super.deleteBill(id);
        syncSaleOrderTurnedProduce(affectedSaleItemIds);
        log.info("工单删除: {} 操作含明细", order.getOrderNo());
    }

    @Override
    public String generateOrderNo() {
        String docType = "WORK_ORDER";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "MO-" + dateStr + "-";
            QueryWrapper<WorkOrder> wrapper = new QueryWrapper<>();
            wrapper.likeRight("order_no", prefix).orderByDesc("order_no").last("LIMIT 1");
            WorkOrder last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getOrderNo() != null) {
                try {
                    seq = Integer.parseInt(last.getOrderNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    @Override
    public WorkOrderProgressVO getProgressDetail(Long orderId) {
        // 用 getDetail 而非 getById：详情页的"成品明细/原料明细"与提交前的明细校验都依赖这两个集合，
        // getById 不带明细 → 前端恒显示 0 行，且保存后 F5 再点提交会被"未选择原料明细"误拦
        WorkOrder workOrder = getDetail(orderId);

        WorkOrderProgressVO vo = new WorkOrderProgressVO();
        vo.setWorkOrder(workOrder);

        List<OperationReport> reports = operationReportMapper.selectList(
                new QueryWrapper<OperationReport>()
                        .eq("production_order_id", orderId)
                        .orderByAsc("created_at is null").orderByDesc("created_at"));
        vo.setRecentReports(reports.size() > 10 ? reports.subList(0, 10) : reports);

        // 进度计算: 若工单有成品明细, 从成品明细汇总计划/已完工/已报工数量; 否则回退用表头字段(兼容旧数据)
        List<WorkOrderProduct> products = workOrderProductMapper.selectByOrderId(orderId);
        BigDecimal planQty;
        BigDecimal reportedQtyAgg;
        if (products != null && !products.isEmpty()) {
            BigDecimal sumPlan = BigDecimal.ZERO;
            BigDecimal sumReported = BigDecimal.ZERO;
            for (WorkOrderProduct p : products) {
                if (p.getQuantity() != null) sumPlan = sumPlan.add(p.getQuantity());
                if (p.getReportedQuantity() != null) sumReported = sumReported.add(p.getReportedQuantity());
            }
            planQty = sumPlan;
            reportedQtyAgg = sumReported;
        } else {
            planQty = workOrder.getQuantity() != null ? workOrder.getQuantity() : BigDecimal.ZERO;
            reportedQtyAgg = workOrder.getReportedQuantity() != null ? workOrder.getReportedQuantity() : BigDecimal.ZERO;
        }

        // 优先从工单工序实例表(work_order_operation)读取, 没有则回退到工艺路线主表(routing_operation)
        List<WorkOrderOperation> workOrderOperations = workOrderOperationMapper.selectByOrderId(orderId);

        if ((workOrderOperations != null && !workOrderOperations.isEmpty()) || workOrder.getRoutingId() == null) {
            if (workOrderOperations == null) workOrderOperations = new java.util.ArrayList<>();
            java.util.Map<Long, OperationProgressVO> opMap = new java.util.HashMap<>();
            List<OperationProgressVO> opList = new java.util.ArrayList<>();

            // 成品行名称表（第10期：进度流程按成品分组展示）
            java.util.Map<Long, String> productNameById = new java.util.HashMap<>();
            if (products != null) {
                for (WorkOrderProduct p : products) {
                    productNameById.put(p.getId(), p.getMaterialName());
                }
            }

            // 【第 19 期】直接子工序数：按 parent_row_id 反查（父工序在进度流程里显示「总·N」并收拢子工序）
            java.util.Map<Long, Integer> childCountByParentId = new java.util.HashMap<>();
            for (WorkOrderOperation op : workOrderOperations) {
                if (op.getParentRowId() != null) {
                    childCountByParentId.merge(op.getParentRowId(), 1, Integer::sum);
                }
            }

            for (WorkOrderOperation op : workOrderOperations) {
                OperationProgressVO opVo = new OperationProgressVO();
                opVo.setOperationId(op.getOperationId());
                opVo.setLineNo(op.getLineNo());
                opVo.setOrderProductId(op.getOrderProductId());
                opVo.setProductName(op.getOrderProductId() == null ? null : productNameById.get(op.getOrderProductId()));
                // 生产工单附件：只读带入工序报工面板（图片可预览、文件可下载）
                opVo.setBillAttachments(workOrder.getAttachmentUrls());
                opVo.setOperationCode(op.getOperationCode());
                opVo.setOperationName(op.getOperationName());
                opVo.setSetupHours(op.getSetupHours() != null ? op.getSetupHours() : BigDecimal.ZERO);
                opVo.setProcessHours(op.getProcessHours() != null ? op.getProcessHours() : BigDecimal.ZERO);
                BigDecimal sh = opVo.getSetupHours();
                BigDecimal ph = opVo.getProcessHours();
                opVo.setTotalHours(sh.add(ph));
                opVo.setPlanQty(op.getPlanQuantity() != null ? op.getPlanQuantity() : planQty);
                opVo.setReportedQty(op.getReportedQuantity() != null ? op.getReportedQuantity() : BigDecimal.ZERO);
                opVo.setQualifiedQty(op.getReportedQuantity() != null ? op.getReportedQuantity() : BigDecimal.ZERO);
                opVo.setRejectedQty(op.getRejectedQuantity() != null ? op.getRejectedQuantity() : BigDecimal.ZERO);
                opVo.setActualWorkHours(BigDecimal.ZERO);
                // 直接采用工序实例的状态字段 (由报工/转移驱动)
                String opStatus = op.getOperationStatus();
                if (opStatus == null || opStatus.isBlank()) opStatus = "未开始";
                opVo.setStatus(opStatus);
                opVo.setIsCritical(op.getIsCritical() != null && op.getIsCritical() == 1 ? (short) 1 : (short) 0);
                opVo.setIsQcRequired(op.getIsQcRequired() != null && op.getIsQcRequired() == 1 ? (short) 1 : (short) 0);
                // 【第 19 期】父子归组所需：本行ID / 父工序行ID / 直接子工序数
                opVo.setOperationRowId(op.getId());
                opVo.setParentRowId(op.getParentRowId());
                opVo.setChildCount(childCountByParentId.getOrDefault(op.getId(), 0));
                opMap.put(op.getOperationId(), opVo);
                opList.add(opVo);
            }

            // 累加报工工时(工序实例不存工时, 从报工单聚合)
            for (OperationReport report : reports) {
                if (report.getOperationId() != null && opMap.containsKey(report.getOperationId())) {
                    OperationProgressVO opVo = opMap.get(report.getOperationId());
                    BigDecimal workHours = report.getWorkHours() != null ? report.getWorkHours() : BigDecimal.ZERO;
                    opVo.setActualWorkHours(opVo.getActualWorkHours().add(workHours));
                }
            }

            int currentIdx = -1;
            String currentOpName = null;
            BigDecimal totalProgress = BigDecimal.ZERO;

            // 当前工序 = 第一个非"已完成"工序
            for (int i = 0; i < opList.size(); i++) {
                OperationProgressVO opVo = opList.get(i);
                if (!"已完成".equals(opVo.getStatus())) {
                    currentIdx = i;
                    currentOpName = opVo.getOperationName();
                    break;
                }
            }
            // 全部已完成或为空时, 取最后一道工序
            if (currentIdx == -1 && !opList.isEmpty()) {
                currentIdx = opList.size() - 1;
                currentOpName = opList.get(opList.size() - 1).getOperationName();
            }

            if (!opList.isEmpty() && planQty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal completedOps = BigDecimal.ZERO;
                for (OperationProgressVO opVo : opList) {
                    if ("已完成".equals(opVo.getStatus())) {
                        completedOps = completedOps.add(BigDecimal.ONE);
                    } else if ("进行中".equals(opVo.getStatus())) {
                        BigDecimal opProgress = opVo.getReportedQty().divide(planQty, 4, RoundingMode.HALF_UP);
                        completedOps = completedOps.add(opProgress);
                    }
                }
                totalProgress = completedOps.divide(BigDecimal.valueOf(opList.size()), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            }

            // 无工艺路线且无工序实例时, 用报工合格量计算完成率(成品明细汇总优先)
            if (opList.isEmpty() && planQty.compareTo(BigDecimal.ZERO) > 0) {
                totalProgress = reportedQtyAgg.divide(planQty, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            }

            vo.setOperations(opList);
            vo.setCurrentOperation(currentOpName);
            vo.setCurrentOperationIndex(currentIdx >= 0 ? currentIdx : null);
            vo.setTotalProgress(totalProgress);
        } else {
            // 回退: 工单未下达生成工序实例, 但有 routing_id 引用, 直接查 routing_operation 主表
            List<RoutingOperation> routingOperations = routingOperationMapper.selectList(
                    new QueryWrapper<RoutingOperation>()
                            .eq("routing_id", workOrder.getRoutingId())
                            .orderByAsc("line_no"));

            java.util.Map<Long, OperationProgressVO> opMap = new java.util.HashMap<>();
            List<OperationProgressVO> opList = new java.util.ArrayList<>();

            for (RoutingOperation ro : routingOperations) {
                OperationProgressVO opVo = new OperationProgressVO();
                opVo.setOperationId(ro.getOperationId());
                opVo.setLineNo(ro.getLineNo());
                opVo.setOperationCode(ro.getOperationCode());
                opVo.setOperationName(ro.getOperationName());
                opVo.setWorkCenterName(ro.getWorkCenterName());
                opVo.setBillAttachments(workOrder.getAttachmentUrls());
                opVo.setSetupHours(ro.getSetupHours() != null ? ro.getSetupHours() : BigDecimal.ZERO);
                opVo.setProcessHours(ro.getProcessHours() != null ? ro.getProcessHours() : BigDecimal.ZERO);
                opVo.setTotalHours(ro.getTotalHours() != null ? ro.getTotalHours() : BigDecimal.ZERO);
                opVo.setPlanQty(planQty);
                opVo.setReportedQty(BigDecimal.ZERO);
                opVo.setQualifiedQty(BigDecimal.ZERO);
                opVo.setRejectedQty(BigDecimal.ZERO);
                opVo.setActualWorkHours(BigDecimal.ZERO);
                opVo.setStatus("未开始");
                opVo.setIsCritical(ro.getIsCritical());
                opVo.setIsQcRequired(ro.getIsQcRequired());
                opMap.put(ro.getOperationId(), opVo);
                opList.add(opVo);
            }

            // 下道工序计划数量按上道合格数扣减（级联）
            for (int i = 1; i < opList.size(); i++) {
                OperationProgressVO prevOp = opList.get(i - 1);
                OperationProgressVO currOp = opList.get(i);
                BigDecimal prevQualified = prevOp.getQualifiedQty();
                if (prevQualified.compareTo(BigDecimal.ZERO) > 0) {
                    currOp.setPlanQty(prevQualified);
                }
            }

            for (OperationReport report : reports) {
                if (report.getOperationId() != null && opMap.containsKey(report.getOperationId())) {
                    OperationProgressVO opVo = opMap.get(report.getOperationId());
                    BigDecimal totalQty = report.getTotalQuantity() != null ? report.getTotalQuantity() : BigDecimal.ZERO;
                    BigDecimal qualifiedQty = report.getQualifiedQuantity() != null ? report.getQualifiedQuantity() : BigDecimal.ZERO;
                    BigDecimal rejectedQty = report.getRejectedQuantity() != null ? report.getRejectedQuantity() : BigDecimal.ZERO;
                    BigDecimal workHours = report.getWorkHours() != null ? report.getWorkHours() : BigDecimal.ZERO;
                    opVo.setReportedQty(opVo.getReportedQty().add(totalQty));
                    opVo.setQualifiedQty(opVo.getQualifiedQty().add(qualifiedQty));
                    opVo.setRejectedQty(opVo.getRejectedQty().add(rejectedQty));
                    opVo.setActualWorkHours(opVo.getActualWorkHours().add(workHours));
                }
            }

            int currentIdx = -1;
            String currentOpName = null;
            BigDecimal totalProgress = BigDecimal.ZERO;

            for (int i = 0; i < opList.size(); i++) {
                OperationProgressVO opVo = opList.get(i);
                if (opVo.getReportedQty().compareTo(BigDecimal.ZERO) > 0) {
                    if (planQty.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal opProgress = opVo.getReportedQty().divide(planQty, 4, RoundingMode.HALF_UP);
                        if (opProgress.compareTo(BigDecimal.ONE) >= 0) {
                            opVo.setStatus("已完成");
                        } else {
                            opVo.setStatus("进行中");
                            if (currentIdx == -1) {
                                currentIdx = i;
                                currentOpName = opVo.getOperationName();
                            }
                        }
                    } else {
                        opVo.setStatus("进行中");
                        if (currentIdx == -1) {
                            currentIdx = i;
                            currentOpName = opVo.getOperationName();
                        }
                    }
                }
            }

            if (currentIdx == -1 && !opList.isEmpty()) {
                currentIdx = 0;
                currentOpName = opList.get(0).getOperationName();
            }

            if (!opList.isEmpty() && planQty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal completedOps = BigDecimal.ZERO;
                for (OperationProgressVO opVo : opList) {
                    if ("已完成".equals(opVo.getStatus())) {
                        completedOps = completedOps.add(BigDecimal.ONE);
                    } else if ("进行中".equals(opVo.getStatus())) {
                        BigDecimal opProgress = opVo.getReportedQty().divide(planQty, 4, RoundingMode.HALF_UP);
                        completedOps = completedOps.add(opProgress);
                    }
                }
                totalProgress = completedOps.divide(BigDecimal.valueOf(opList.size()), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            }

            vo.setOperations(opList);
            vo.setCurrentOperation(currentOpName);
            vo.setCurrentOperationIndex(currentIdx >= 0 ? currentIdx : null);
            vo.setTotalProgress(totalProgress);
        }

        return vo;
    }

    // ====================== 下推实现: 来源单 → 生产工单 ======================

    /** 销售订单明细 → 生产工单 (一单一扣: 一个销售明细行生成一张工单) */
    @Override
    @Transactional
    public WorkOrder convertFromSaleOrder(Long saleOrderId, Long saleOrderItemId) {
        SaleOrder saleOrder = saleOrderMapper.selectById(saleOrderId);
        if (saleOrder == null) {
            throw new BusinessException("销售订单不存在");
        }
        if (!"已生效".equals(saleOrder.getStatus())) {
            throw new BusinessException("只有已生效状态的销售订单可下推生成工单，当前状态: " + saleOrder.getStatus());
        }

        SaleOrderItem saleItem = saleOrderItemMapper.selectById(saleOrderItemId);
        if (saleItem == null || !saleOrderId.equals(saleItem.getOrderId())) {
            throw new BusinessException("销售订单明细不存在或不属于该订单");
        }

        // 剩余可生成量 = 订单数量 − 已下推生产数量
        // 【2026-09-11 修复】原实现误用 deliveredQuantity（已发货数量）：
        //   delivered_quantity 全系统只有"初始化为0"，没有任何回写点，恒为 0，
        //   导致 toProduce 永远等于订单数量 → 同一明细行可无限次下推（实测连续下推生成两张工单）。
        //   且语义上判断"能否下推生产"本就该看已下推生产数量。
        BigDecimal qty = saleItem.getQuantity() != null ? saleItem.getQuantity() : BigDecimal.ZERO;
        BigDecimal turnedProduce = saleItem.getTurnedProduceQuantity() != null
                ? saleItem.getTurnedProduceQuantity() : BigDecimal.ZERO;
        BigDecimal toProduce = qty.subtract(turnedProduce);
        if (toProduce.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("该销售明细行已全部下推，剩余可生成数量为0");
        }

        WorkOrder order = new WorkOrder();
        order.setOrderNo(generateOrderNo());
        order.setOrderDate(LocalDate.now());
        order.setMaterialId(saleItem.getMaterialId());
        order.setMaterialCode(saleItem.getMaterialCode());
        order.setMaterialName(saleItem.getMaterialName());
        order.setModel(saleItem.getSpecification());
        order.setUnitId(saleItem.getUnitId());
        order.setQuantity(toProduce);
        order.setCompletedQuantity(BigDecimal.ZERO);
        order.setReportedQuantity(BigDecimal.ZERO);
        order.setRejectedQuantity(BigDecimal.ZERO);
        order.setWarehouseId(saleOrder.getWarehouseId());
        order.setDepartmentId(saleOrder.getDepartmentId());
        order.setPlanStartDate(LocalDate.now());
        order.setPlanEndDate(saleOrder.getDeliveryDate());
        order.setSourceType("销售订单");
        order.setSourceNo(saleOrder.getOrderNo());
        order.setSalesOrderNo(saleOrder.getOrderNo());
        order.setStatus("草稿");
        order.setApprovalStatus("待审核");
        order.setIsUrgent((short) 0);
        order.setIsClosed((short) 0);
        order.setRemark("由销售订单 " + saleOrder.getOrderNo() + " 第" + saleItem.getLineNo() + "行下推生成");

        // 若物料有主BOM，自动带出
        if (saleItem.getMaterialId() != null) {
            Bom primaryBom = bomMapper.selectOne(
                    new QueryWrapper<Bom>()
                            .eq("material_id", saleItem.getMaterialId())
                            .eq("is_primary", (short) 1)
                            .last("LIMIT 1"));
            if (primaryBom != null) {
                order.setBomId(primaryBom.getBomId());
                order.setBomVersion(primaryBom.getBomVersion());
            }
        }

        // 自动生成/下推的工单必须带明细, 否则打开明细区空白; 表头兜底逻辑会按 materialId 补出成品明细行
        saveOrderWithItems(order, order.getMaterials());
        // 生成即按 BOM 展开用料清单，避免草稿态工单"原料明细"为空（下达时还会再展开一次，方法幂等）
        generateMaterialsFromBom(order);
        // 单据审批开关: PRODUCTION_ORDER 免审批时, 下推生成后自动通过审批
        if (!approvalGateHelper.needApproval("PRODUCTION_ORDER")) {
            order.setApprovalStatus("已生效");
            // 免审批没有人工审核人，审核人留空（用户 2026-10-28 口径）
            order.setApprovedBy(null);
            order.setApprovedAt(LocalDateTime.now());
            updateById(order);
        }

        log.info("销售订单 {} 明细行 {} 下推生成工单 {}", saleOrder.getOrderNo(), saleOrderItemId, order.getOrderNo());

        // 回写订单行"已下推生产数量"，作为下次下推计算剩余量的依据（与上方判定配套，缺此回写＝可重复下推）
        saleItem.setTurnedProduceQuantity(turnedProduce.add(toProduce));
        saleOrderItemMapper.updateById(saleItem);
        return order;
    }

    /** MRP计划明细 → 生产工单 (按建议生产数量生成工单) */
    @Override
    @Transactional
    public WorkOrder convertFromMrp(Long mrpPlanItemId) {
        MrpPlanItem planItem = mrpPlanItemMapper.selectById(mrpPlanItemId);
        if (planItem == null) {
            throw new BusinessException("MRP计划明细不存在");
        }

        MrpPlan plan = mrpPlanMapper.selectById(planItem.getPlanId());
        if (plan == null) {
            throw new BusinessException("MRP计划不存在");
        }

        // 仅生产建议类型可下推（建议类型落库值域：PRODUCE / 自制件；兼容历史中文"生产"/英文"make"）
        String suggestType = planItem.getSuggestType();
        if (suggestType != null) {
            String st = suggestType.trim();
            boolean isProduce = st.contains("生产") || "PRODUCE".equalsIgnoreCase(st)
                    || "自制件".equals(st) || "make".equalsIgnoreCase(st);
            if (!isProduce) {
                throw new BusinessException("仅生产建议类型的MRP明细可下推生成工单，当前建议类型: " + suggestType);
            }
        }

        BigDecimal planQty = planItem.getPlanQuantity() != null ? planItem.getPlanQuantity() : planItem.getNetDemand();
        if (planQty == null || planQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("MRP明细计划数量必须大于0");
        }

        WorkOrder order = new WorkOrder();
        order.setOrderNo(generateOrderNo());
        order.setOrderDate(LocalDate.now());
        order.setMaterialId(planItem.getMaterialId());
        order.setMaterialCode(planItem.getMaterialCode());
        order.setMaterialName(planItem.getMaterialName());
        order.setModel(planItem.getSpecification());
        order.setQuantity(planQty);
        order.setCompletedQuantity(BigDecimal.ZERO);
        order.setReportedQuantity(BigDecimal.ZERO);
        order.setRejectedQuantity(BigDecimal.ZERO);
        order.setSourceType("MRP");
        order.setSourceNo(plan.getPlanNo());
        order.setStatus("草稿");
        order.setApprovalStatus("待审核");
        order.setIsUrgent((short) 0);
        order.setIsClosed((short) 0);
        order.setRemark("由MRP计划 " + plan.getPlanNo() + " 下推生成");

        // 计划日期
        if (planItem.getSuggestDate() != null) {
            order.setPlanStartDate(planItem.getSuggestDate());
            if (planItem.getLeadTime() != null && planItem.getLeadTime() > 0) {
                order.setPlanEndDate(planItem.getSuggestDate().plusDays(planItem.getLeadTime()));
                order.setLeadTimeDays(planItem.getLeadTime());
            } else {
                order.setPlanEndDate(planItem.getSuggestDate());
            }
        }

        // 【2026-10-09 用户口径】下推生成的生产加工单要带上「物料档案」的 BOM 与工艺路线、自定义字段。
        // BOM / 工艺路线的取数口径与「MRP 生产建议补显」完全一致（不另造规则）：
        //   BOM：MRP 明细已挂关联BOM 就用它；否则按物料找「主BOM(is_primary=1 且 已生效)」，找不到退该物料最新一条。
        //   工艺路线：优先取该 BOM 下的路线，其次取该物料的路线。
        Long bomId = planItem.getRelatedBomId() != null ? planItem.getRelatedBomId()
                : resolveMaterialBomId(planItem.getMaterialId());
        if (bomId != null) {
            Bom bom = bomMapper.selectById(bomId);
            if (bom != null) {
                order.setBomId(bom.getBomId());
                order.setBomVersion(bom.getBomVersion());
            }
        }
        Long routingId = resolveMaterialRoutingId(planItem.getMaterialId(), bomId);

        // 成品明细行显式建一条（带工艺路线）：既是「成品明细」页签的内容，也是"按工艺路线生成工序"的依据
        WorkOrderProduct product = new WorkOrderProduct();
        product.setMaterialId(planItem.getMaterialId());
        product.setMaterialCode(planItem.getMaterialCode());
        product.setMaterialName(planItem.getMaterialName());
        product.setSpecification(planItem.getSpecification());
        product.setModel(planItem.getSpecification());
        product.setQuantity(planQty);
        product.setCompletedQuantity(BigDecimal.ZERO);
        product.setReportedQuantity(BigDecimal.ZERO);
        product.setRoutingId(routingId);
        if (routingId != null) {
            com.erp.production.entity.Routing routing = routingMapper.selectById(routingId);
            if (routing != null) product.setRoutingVersion(routing.getRoutingVersion());
        }
        List<WorkOrderProduct> productRows = new ArrayList<>();
        productRows.add(product);
        order.setProducts(productRows);

        saveOrderWithItems(order, null);
        // 按 BOM 展开用料清单 → 「原材料明细」不为空（下达时还会再展开一次，方法幂等）
        generateMaterialsFromBom(order);
        // 按成品行的工艺路线生成工序实例（没有工艺路线时该方法自然跳过，不会报错）
        generateOperationsFromRouting(order);
        // 把「物料档案」上填的自定义字段值带到工单（只带工单这边也定义了、且键名相同的字段）
        carryMaterialCustomFields(planItem.getMaterialId(), order.getOrderId());
        // 单据审批开关: PRODUCTION_ORDER 免审批时, 下推生成后自动通过审批
        if (!approvalGateHelper.needApproval("PRODUCTION_ORDER")) {
            order.setApprovalStatus("已生效");
            // 免审批没有人工审核人，审核人留空（用户 2026-10-28 口径）
            order.setApprovedBy(null);
            order.setApprovedAt(LocalDateTime.now());
            updateById(order);
        }

        log.info("MRP计划 {} 明细 {} 下推生成工单 {}", plan.getPlanNo(), mrpPlanItemId, order.getOrderNo());
        return order;
    }

    /**
     * 【2026-10-09】按物料找 BOM：口径与「MRP 生产建议补显」一致 ——
     * 先取「主BOM（is_primary=1 且 已生效）」按 bom_id 倒序最新一条；找不到再退化为该物料最新一条。
     */
    private Long resolveMaterialBomId(Long materialId) {
        if (materialId == null) return null;
        try {
            Bom primary = bomMapper.selectOne(new QueryWrapper<Bom>()
                    .eq("material_id", materialId)
                    .eq("is_primary", (short) 1)
                    .eq("status", "已生效")
                    .orderByDesc("bom_id").last("LIMIT 1"));
            if (primary != null) return primary.getBomId();
            Bom latest = bomMapper.selectOne(new QueryWrapper<Bom>()
                    .eq("material_id", materialId).orderByDesc("bom_id").last("LIMIT 1"));
            return latest == null ? null : latest.getBomId();
        } catch (Exception e) {
            log.warn("下推：按物料解析BOM失败 materialId={}", materialId, e);
            return null;
        }
    }

    /** 【2026-10-09】按 BOM/物料找工艺路线：优先该 BOM 下的路线，其次该物料的路线（与 MRP 生产建议补显口径一致） */
    private Long resolveMaterialRoutingId(Long materialId, Long bomId) {
        try {
            if (bomId != null) {
                com.erp.production.entity.Routing byBom = routingMapper.selectOne(
                        new QueryWrapper<com.erp.production.entity.Routing>()
                                .eq("bom_id", bomId).orderByDesc("routing_id").last("LIMIT 1"));
                if (byBom != null) return byBom.getRoutingId();
            }
            if (materialId == null) return null;
            com.erp.production.entity.Routing byMaterial = routingMapper.selectOne(
                    new QueryWrapper<com.erp.production.entity.Routing>()
                            .eq("material_id", materialId).orderByDesc("routing_id").last("LIMIT 1"));
            return byMaterial == null ? null : byMaterial.getRoutingId();
        } catch (Exception e) {
            log.warn("下推：按BOM/物料解析工艺路线失败 materialId={} bomId={}", materialId, bomId, e);
            return null;
        }
    }

    /** 【2026-10-09】把「物料档案」的自定义字段值带到该单据（只带单据侧也定义了、且键名相同的字段） */
    private void carryMaterialCustomFields(Long materialId, Long orderId) {
        if (materialId == null || orderId == null) return;
        try {
            java.util.Map<String, Object> materialValues = customFieldService.getFieldValues("material", materialId);
            if (materialValues == null || materialValues.isEmpty()) return;
            java.util.Set<String> billKeys = new java.util.HashSet<>();
            java.util.List<com.erp.customfield.entity.CustomField> billFields =
                    customFieldService.listByEntity("production_order");
            if (billFields != null) {
                for (com.erp.customfield.entity.CustomField f : billFields) {
                    if (f.getFieldKey() != null) billKeys.add(f.getFieldKey());
                }
            }
            if (billKeys.isEmpty()) return;
            java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
            materialValues.forEach((k, v) -> {
                if (k != null && billKeys.contains(k) && v != null) values.put(k, v);
            });
            if (!values.isEmpty()) customFieldService.saveFieldValues("production_order", orderId, values);
        } catch (Exception e) {
            log.warn("下推：带物料自定义字段到工单失败 materialId={} orderId={}", materialId, orderId, e);
        }
    }

    // ====================== 下推实现: 生产工单 → 下游单据 ======================

    /**
     * 下推守卫（全站通用 canPushDown 口径）：只有「已生效」且「未结案（close_status != 1）」的工单可下推下游单据。
     * 原实现分别要求「已下达/执行中」或「执行中/已完工」，这些业务执行态已退役。
     */
    private void assertPushable(WorkOrder order, String targetLabel) {
        if (!BillStatusUtils.isApprovedClass(order.getStatus())) {
            throw new BusinessException("只有已生效状态的工单可下推" + targetLabel + "，当前状态: " + order.getStatus());
        }
        if (Integer.valueOf(1).equals(order.getCloseStatus())) {
            throw new BusinessException("工单已结案，不能下推" + targetLabel + "，如需下推请先反结案");
        }
    }

    /**
     * 领料下推按物料采购批量向上取整（2026-11-25）。
     * 仅当设置中心「领料单按采购批量校验」开启且物料配了采购批量时生效；默认关 = 原样返回。
     * 例：待领 25.1kg、采购批量 25kg → 50kg（2 包）。
     */
    private BigDecimal alignToPurchaseBatch(BigDecimal qty, Long materialId) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0 || materialId == null) return qty;
        if (!sysConfigService.getBooleanValue("inventory.requisition.batch.check.enabled", false)) return qty;
        com.erp.masterdata.entity.Material m = materialService.getById(materialId);
        if (m == null || m.getPurchaseBatchQty() == null
                || m.getPurchaseBatchQty().compareTo(BigDecimal.ZERO) <= 0) {
            return qty;
        }
        return qty.divide(m.getPurchaseBatchQty(), 0, RoundingMode.CEILING).multiply(m.getPurchaseBatchQty());
    }

    /** 生产工单 → 生产领料单 (基于工单用料明细下推) */
    @Override
    @Transactional
    public MaterialRequisition pushToMaterialRequisition(Long orderId) {
        WorkOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("工单不存在");
        }
        assertPushable(order, "领料单");

        List<WorkOrderMaterial> woMaterials = materialMapper.selectList(
                new QueryWrapper<WorkOrderMaterial>().eq("order_id", orderId).orderByAsc("line_no"));
        if (woMaterials == null || woMaterials.isEmpty()) {
            throw new BusinessException("工单用料明细为空，无法下推领料单");
        }

        MaterialRequisition requisition = new MaterialRequisition();
        requisition.setBillType("PICK");
        requisition.setRequisitionDate(LocalDate.now());
        requisition.setWorkOrderNo(order.getOrderNo());
        requisition.setWarehouseId(order.getWarehouseId());
        requisition.setDepartmentId(order.getDepartmentId());
        requisition.setSourceBillType("生产工单");
        requisition.setSourceBillNo(order.getOrderNo());
        requisition.setStatus("草稿");
        // 下推统一口径（2026-11-28）：下推只生成草稿，审核动作由用户单独完成
        requisition.setApprovalStatus("草稿");
        requisition.setRemark("由生产工单 " + order.getOrderNo() + " 下推生成");
        // 币种兜底（2026-11-09）：下推时补本位币，避免领料单币种为空导致保存后仍不持久化
        java.util.List<com.erp.basedata.entity.Currency> currencies = currencyService.list();
        com.erp.basedata.entity.Currency baseCurrency = currencies.stream()
                .filter(c -> c.getIsBaseCurrency() != null && c.getIsBaseCurrency() == 1)
                .findFirst()
                .orElse(currencies.isEmpty() ? null : currencies.get(0));
        if (baseCurrency != null) {
            requisition.setCurrencyId(baseCurrency.getCurrencyId());
        }
        requisition.setExchangeRate(BigDecimal.ONE);

        List<MaterialRequisitionItem> items = new ArrayList<>();
        int lineNo = 1;
        for (WorkOrderMaterial wom : woMaterials) {
            BigDecimal required = wom.getRequiredQuantity() != null ? wom.getRequiredQuantity() : BigDecimal.ZERO;
            BigDecimal issued = wom.getIssuedQuantity() != null ? wom.getIssuedQuantity() : BigDecimal.ZERO;
            BigDecimal toIssue = required.subtract(issued);
            if (toIssue.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // 该行已足额领料, 跳过
            }
            // 2026-11-25：领料单按采购批量校验开启时，下推数量按物料包装量向上取整（默认关 = 原样）
            toIssue = alignToPurchaseBatch(toIssue, wom.getMaterialId());
            MaterialRequisitionItem item = new MaterialRequisitionItem();
            item.setLineNo(lineNo);
            item.setMaterialId(wom.getMaterialId());
            item.setMaterialCode(wom.getMaterialCode());
            item.setMaterialName(wom.getMaterialName());
            item.setSpecification(wom.getSpecification());
            item.setUnitId(wom.getUnitId());
            item.setQuantity(toIssue);
            item.setIssuedQuantity(BigDecimal.ZERO);
            item.setUnitPrice(wom.getUnitCost() != null ? wom.getUnitCost() : BigDecimal.ZERO);
            item.setStockId(wom.getStockId());
            item.setBinId(wom.getBinId());
            item.setWorkOrderLineNo(wom.getLineNo());
            item.setSourceBillType("生产工单");
            item.setSourceBillNo(order.getOrderNo());
            item.setSourceLineNo(wom.getLineNo());
            items.add(item);
            lineNo += 1;
        }

        if (items.isEmpty()) {
            throw new BusinessException("工单所有用料行已足额领料，无需生成领料单");
        }

        materialRequisitionService.saveRequisitionWithItems(requisition, items);
        // 下推统一口径（2026-11-28）：只生成草稿，不再按免审批开关自动审核生效

        log.info("生产工单 {} 下推生成领料单 {}", order.getOrderNo(), requisition.getRequisitionNo());
        return requisition;
    }

    /** 生产工单 → 生产入库单 (完工产品入库) */
    @Override
    @Transactional
    public ProductionReceipt pushToProductionReceipt(Long orderId) {
        return pushToProductionReceipt(orderId, null, null);
    }

    /**
     * 生产工单 → 生产入库单 (完工产品入库)，可指定本次入库数量与来源工序行。
     * 第 11 期：入库类型工序「一键发起入库单」复用本方法（quantity 不为空时按传入数量入库）。
     */
    @Override
    @Transactional
    public ProductionReceipt pushToProductionReceipt(Long orderId, BigDecimal quantity, Long sourceOperationId) {
        WorkOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("工单不存在");
        }
        assertPushable(order, "入库单");

        BigDecimal toReceive;
        if (quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
            // 工序级发起：数量取入参（剩余量校验由调用方按工序口径完成）
            toReceive = quantity;
        } else {
            BigDecimal completed = order.getCompletedQuantity() != null ? order.getCompletedQuantity() : BigDecimal.ZERO;
            BigDecimal planQty = order.getQuantity() != null ? order.getQuantity() : BigDecimal.ZERO;
            toReceive = planQty.subtract(completed);
        }
        if (toReceive.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("工单已全部入库，剩余可入库数量为0");
        }

        ProductionReceipt receipt = new ProductionReceipt();
        receipt.setReceiptDate(LocalDate.now());
        receipt.setWarehouseId(order.getWarehouseId());
        receipt.setProductionOrderId(order.getOrderId());
        receipt.setProductionOrderNo(order.getOrderNo());
        receipt.setWorkOrderNo(order.getOrderNo());
        receipt.setDepartmentId(order.getDepartmentId());
        receipt.setSourceBillType("生产工单");
        receipt.setSourceBillNo(order.getOrderNo());
        // 第 11 期：来源工序行（能追溯到是哪道工序发起的入库）
        receipt.setSourceOperationId(sourceOperationId);
        receipt.setStatus("草稿");
        // 下推统一口径（2026-11-28）：下推只生成草稿，审核动作由用户单独完成
        receipt.setApprovalStatus("草稿");
        receipt.setRemark("由生产工单 " + order.getOrderNo() + " 下推生成"
                + (sourceOperationId == null ? "" : "（来源工序行 " + sourceOperationId + "）"));

        List<ProductionReceiptItem> items = new ArrayList<>();
        ProductionReceiptItem item = new ProductionReceiptItem();
        item.setLineNo(10);
        item.setMaterialId(order.getMaterialId());
        item.setMaterialCode(order.getMaterialCode());
        item.setMaterialName(order.getMaterialName());
        item.setSpecification(order.getModel());
        item.setUnitId(order.getUnitId());
        item.setQuantity(toReceive);
        item.setQualifiedQuantity(toReceive);
        item.setRejectedQuantity(BigDecimal.ZERO);
        item.setUnitPrice(BigDecimal.ZERO);
        item.setStockId(order.getWarehouseId());
        item.setWorkOrderLineNo(10);
        // 2026-11-06：明细行同样记录来源工序（已入库量按行汇总，一张单可对应多道入库工序）
        item.setSourceOperationId(sourceOperationId);
        items.add(item);

        productionReceiptService.saveReceiptWithItems(receipt, items);
        log.info("生产工单 {} 下推生成入库单 {}{}", order.getOrderNo(), receipt.getReceiptNo(),
                sourceOperationId == null ? "" : "（来源工序行 " + sourceOperationId + "）");
        return receipt;
    }
}
