package com.erp.produce.mrp.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillStatusUtils;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.service.InventoryService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import com.erp.produce.mrp.entity.MrpPlan;
import com.erp.produce.mrp.entity.MrpPlanItem;
import com.erp.produce.mrp.mapper.MrpPlanItemMapper;
import com.erp.produce.mrp.mapper.MrpPlanMapper;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderMaterial;
import com.erp.production.service.WorkOrderService;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.entity.PurchaseOrderItem;
import com.erp.purchase.service.PurchaseOrderService;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.service.SaleOrderService;
import com.erp.subcontract.order.entity.SubcontractOrder;
import com.erp.subcontract.order.entity.SubcontractOrderItem;
import com.erp.subcontract.order.service.SubcontractOrderService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;
import com.erp.basedata.entity.Warehouse;
import com.erp.basedata.mapper.WarehouseMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor

public class MrpServiceImpl extends ServiceImpl<MrpPlanMapper, MrpPlan> implements MrpService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义全面推广）。
        // 键 = 前端筛选条件定义键（与 MRP 运算计划明细报表引擎筛选键同键，汇总/明细两种模式一套参数）；
        // 值 = 实体属性名（registerProps 由 MyBatis-Plus 元数据解析成真实列名，防手写列名笔误）。
        // 状态不入白名单：plan_status 是 integer 列且 std 状态条件传中文文案，走 pageQuery 专用入参
        // （planStatusCode 中文→数字码），白名单若收 f_status 会拿中文与 integer 列等值比较直接 SQL 报错。
        Map<String, BillHeaderFilterSupport.Spec> f = new LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("planNo", new BillHeaderFilterSupport.Spec("planNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("planName", new BillHeaderFilterSupport.Spec("planName", BillHeaderFilterSupport.Kind.TEXT));
        f.put("demandSource", new BillHeaderFilterSupport.Spec("demandSource", BillHeaderFilterSupport.Kind.TEXT));
        f.put("lotSizeType", new BillHeaderFilterSupport.Spec("lotSizeType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 日期区间 ──
        f.put("planDate", new BillHeaderFilterSupport.Spec("planDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("planHorizon", new BillHeaderFilterSupport.Spec("planHorizon", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("timeFenceDays", new BillHeaderFilterSupport.Spec("timeFenceDays", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("totalNetDemand", new BillHeaderFilterSupport.Spec("totalNetDemand", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("totalPurchaseSuggestion", new BillHeaderFilterSupport.Spec("totalPurchaseSuggestion", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("totalProduceSuggestion", new BillHeaderFilterSupport.Spec("totalProduceSuggestion", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("mrpPlan", MrpPlan.class, f);
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "create_by",
            "create_time",
            "created_at",
            "demand_order_ids",
            "demand_source",
            "id",
            "material_id",
            "plan_date",
            "plan_id",
            "plan_name",
            "plan_no",
            "plan_status",
            "remark",
            "suggest_type",
            "total_net_demand",
            "total_produce_suggestion",
            "total_purchase_suggestion",
            "update_time"
        );

    private final MrpPlanItemMapper mrpPlanItemMapper;
    private final BomExplosionService bomExplosionService;
    private final MaterialService materialService;
    private final InventoryService inventoryService;
    private final SaleOrderService saleOrderService;
    private final WorkOrderService workOrderService;
    private final SubcontractOrderService subcontractOrderService;
    private final PurchaseOrderService purchaseOrderService;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    /** 仓库档案（2026-10-09）：MRP 可用库存只统计「参与MRP」的仓库（不合格品仓/废料仓默认不参与） */
    private final WarehouseMapper warehouseMapper;

    /** 【2026-11-06 行级 BOM】计划明细持久化 BOM 清单编码/版本（重开计划仍在） */
    private final com.erp.masterdata.mapper.BomMapper bomMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 整单删除级联服务（删主表前清理明细，2026-09-19） */
    private final BillCascadeDeleteService billCascadeDeleteService;

    /** 统一数据权限解析器：按角色 data_scope（统一数据权限）过滤 MRP 计划列表 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @Override
    public PageResult<MrpPlan> pageQuery(int current, int size, String keyword, String status, String sortBy, String sortOrder) {
        // 老签名委托新重载（headerFilters 空 = 行为与改造前完全一致）
        return pageQuery(current, size, keyword, status, sortBy, sortOrder, java.util.Collections.emptyMap());
    }

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）：
     * 白名单 + 参数化，非法/空白/白名单外入参在支持类内部忽略，不阻断列表查询。
     */
    @Override
    public PageResult<MrpPlan> pageQuery(int current, int size, String keyword, String status, String sortBy, String sortOrder,
                                         java.util.Map<String, String> headerFilters) {
        QueryWrapper<MrpPlan> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("plan_no", keyword)
                    .or().like("plan_name", keyword));
        }
        if (status != null && !status.isBlank()) {
            // 兼容前端传状态文案或数字：数字直接过滤，文案转对应 plan_status
            Integer statusCode = planStatusCode(status);
            if (statusCode != null) {
                wrapper.eq("plan_status", statusCode);
            }
        }
        // 表头字段过滤：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "mrpPlan", headerFilters);
        // 数据权限：按角色 data_scope（统一数据权限）过滤；创建人列沿用本表现有列名 create_by
        dataScopeResolver.applyIfLimited(wrapper, null, "create_by");
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, MrpPlan.class);
        Page<MrpPlan> page = new Page<>(current, size);
        Page<MrpPlan> result = page(page, wrapper);
        result.getRecords().forEach(this::fillStatusText);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /** 前端状态文案 → plan_status 数字（planStatusText 的逆映射） */
    private Integer planStatusCode(String text) {
        if (text == null) return null;
        return switch (text.trim()) {
            case "草稿", "未审核" -> 0;
            case "待审核", "已运算" -> 1;
            case "已生效", "已确认" -> 2;
            case "已执行" -> 3;
            case "已作废", "作废" -> 7;
            default -> {
                // 纯数字字符串直接解析
                try {
                    yield Integer.parseInt(text.trim());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
        };
    }

    @Override
    public MrpPlan getDetail(Long id) {
        MrpPlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException("MRP计划不存在");
        }
        List<MrpPlanItem> items = mrpPlanItemMapper.selectList(
                new QueryWrapper<MrpPlanItem>().eq("plan_id", id).orderByAsc("level", "id"));
        plan.setItems(items);
        fillStatusText(plan);
        return plan;
    }

    @Override
    @Transactional
    public void createPlan(MrpPlan plan) {
        if (plan.getPlanNo() == null || plan.getPlanNo().isBlank()) {
            plan.setPlanNo(generatePlanNo());
        }
        if (plan.getPlanDate() == null) {
            plan.setPlanDate(LocalDate.now());
        }
        if (plan.getPlanStatus() == null) {
            plan.setPlanStatus(0);
        }
        if (plan.getTotalNetDemand() == null) {
            plan.setTotalNetDemand(BigDecimal.ZERO);
        }
        if (plan.getTotalPurchaseSuggestion() == null) {
            plan.setTotalPurchaseSuggestion(BigDecimal.ZERO);
        }
        if (plan.getTotalProduceSuggestion() == null) {
            plan.setTotalProduceSuggestion(BigDecimal.ZERO);
        }
        save(plan);
        // 落明细（2026-10-06 修复）：原实现只存方案头，导致方案明细为空 →
        // “已分析过”判定失效、二次重新分析无数据可依。
        if (plan.getItems() != null && !plan.getItems().isEmpty()) {
            java.util.Map<Long, com.erp.masterdata.entity.Bom> bomCache = new java.util.HashMap<>();
            for (MrpPlanItem item : plan.getItems()) {
                item.setId(null);              // 前端回传的历史 id 不沿用，避免跨方案串行
                item.setPlanId(plan.getId());
                if (item.getLevel() == null) item.setLevel(0);
                fillItemBomSnapshot(item, bomCache);
                mrpPlanItemMapper.insert(item);
            }
        }
        log.info("创建MRP计划: {} 明细{}条", plan.getPlanNo(),
                plan.getItems() == null ? 0 : plan.getItems().size());
    }

    /** 更新计划基本信息(仅草稿态可改, 不允许改状态) */
    @Override
    @Transactional
    public void updatePlan(Long id, MrpPlan plan) {
        MrpPlan exist = getById(id);
        if (exist == null) {
            throw new BusinessException("MRP计划不存在");
        }
        if (exist.getPlanStatus() != null && exist.getPlanStatus() != 0) {
            throw new BusinessException("只有草稿状态的计划可编辑，当前状态: " + planStatusText(exist.getPlanStatus()));
        }
        UpdateWrapper<MrpPlan> uw = new UpdateWrapper<>();
        uw.eq("id", id)
                .set("plan_name", plan.getPlanName() != null ? plan.getPlanName() : exist.getPlanName())
                .set("plan_date", plan.getPlanDate() != null ? plan.getPlanDate() : exist.getPlanDate())
                .set("demand_source", plan.getDemandSource() != null ? plan.getDemandSource() : exist.getDemandSource())
                .set("demand_order_ids", plan.getDemandOrderIds() != null ? plan.getDemandOrderIds() : exist.getDemandOrderIds())
                .set("remark", plan.getRemark() != null ? plan.getRemark() : exist.getRemark())
                // 2026-10-06：运算参数/运算范围此前保存会被丢弃（页面改了不生效），此处一并落库；附件同理
                .set("plan_horizon", plan.getPlanHorizon() != null ? plan.getPlanHorizon() : exist.getPlanHorizon())
                .set("time_fence_days", plan.getTimeFenceDays() != null ? plan.getTimeFenceDays() : exist.getTimeFenceDays())
                .set("include_so", plan.getIncludeSo() != null ? plan.getIncludeSo() : exist.getIncludeSo())
                .set("include_forecast", plan.getIncludeForecast() != null ? plan.getIncludeForecast() : exist.getIncludeForecast())
                .set("include_safety_stock", plan.getIncludeSafetyStock() != null ? plan.getIncludeSafetyStock() : exist.getIncludeSafetyStock())
                .set("include_open_po", plan.getIncludeOpenPo() != null ? plan.getIncludeOpenPo() : exist.getIncludeOpenPo())
                .set("include_open_mo", plan.getIncludeOpenMo() != null ? plan.getIncludeOpenMo() : exist.getIncludeOpenMo())
                .set("include_onhand_stock", plan.getIncludeOnhandStock() != null ? plan.getIncludeOnhandStock() : exist.getIncludeOnhandStock())
                .set("lot_size_type", plan.getLotSizeType() != null ? plan.getLotSizeType() : exist.getLotSizeType())
                .set("lot_size", plan.getLotSize() != null ? plan.getLotSize() : exist.getLotSize())
                .set("min_order_qty", plan.getMinOrderQty() != null ? plan.getMinOrderQty() : exist.getMinOrderQty())
                .set("attachment_urls", plan.getAttachmentUrls() != null ? plan.getAttachmentUrls() : exist.getAttachmentUrls());
        update(uw);
        log.info("更新MRP计划基本信息: {}", exist.getPlanNo());
    }

    /** 审核: 草稿(0)/已运算(1) → 已生效(2) */
    @Override
    @Transactional
    public void approvePlan(Long id, String operator) {
        MrpPlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException("MRP计划不存在");
        }
        Integer st = plan.getPlanStatus() == null ? 0 : plan.getPlanStatus();
        if (st != 0 && st != 1) {
            throw new BusinessException("只有草稿或待审核状态的计划可审核，当前状态: " + planStatusText(st));
        }
        if (Integer.valueOf(1).equals(plan.getCloseStatus())) {
            throw new BusinessException("已结案的计划不能审核，请先反结案");
        }
        plan.setPlanStatus(2);
        updateById(plan);
        log.info("审核MRP计划: {} 操作人: {}", plan.getPlanNo(), operator);
    }

    /** 执行: 已生效(2) → 已执行(3) */
    @Override
    @Transactional
    public void executePlan(Long id, String operator) {
        MrpPlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException("MRP计划不存在");
        }
        Integer st = plan.getPlanStatus();
        if (st == null || st != 2) {
            throw new BusinessException("只有已生效状态的计划可执行，当前状态: " + planStatusText(st));
        }
        if (Integer.valueOf(1).equals(plan.getCloseStatus())) {
            throw new BusinessException("已结案的计划不能执行，请先反结案");
        }
        plan.setPlanStatus(3);
        updateById(plan);
        log.info("执行MRP计划: {} 操作人: {}", plan.getPlanNo(), operator);
    }

    /** 驳回: 待审核(1, 已运算) → 草稿(0) */
    @Override
    @Transactional
    public void rejectPlan(Long id, String rejector, String reason) {
        MrpPlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException("MRP计划不存在");
        }
        Integer st = plan.getPlanStatus();
        if (st == null || st != 1) {
            throw new BusinessException("只有待审核(已运算)状态的计划可驳回，当前状态: " + planStatusText(st));
        }
        plan.setPlanStatus(0);
        if (reason != null && !reason.isBlank()) {
            plan.setRemark((plan.getRemark() != null ? plan.getRemark() + " | " : "") + "驳回原因: " + reason);
        }
        updateById(plan);
        log.info("驳回MRP计划: {} 操作人: {} 原因: {}", plan.getPlanNo(), rejector, reason);
    }

    /** 重置草稿: 已生效(2) → 草稿(0) */
    @Override
    @Transactional
    public void reverseApprovePlan(Long id, String operator) {
        MrpPlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException("MRP计划不存在");
        }
        Integer st = plan.getPlanStatus();
        if (st == null || st != 2) {
            throw new BusinessException("只有已生效状态的计划可重置草稿，当前状态: " + planStatusText(st));
        }
        if (Integer.valueOf(1).equals(plan.getCloseStatus())) {
            throw new BusinessException("已结案的计划不能重置草稿，请先反结案");
        }
        plan.setPlanStatus(0);
        updateById(plan);
        log.info("重置草稿MRP计划: {} 操作人: {}", plan.getPlanNo(), operator);
    }

    /** 作废: → plan_status=7, 记录作废前状态和 void 字段 */
    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        MrpPlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException("MRP计划不存在");
        }
        Integer st = plan.getPlanStatus();
        if (st != null && st == 7) {
            throw new BusinessException("计划已作废，不能重复作废");
        }
        if (st != null && st == 3) {
            throw new BusinessException("已执行的计划不能作废");
        }
        if (Integer.valueOf(1).equals(plan.getCloseStatus())) {
            throw new BusinessException("已结案的计划不能作废，请先反结案");
        }
        UpdateWrapper<MrpPlan> uw = new UpdateWrapper<>();
        uw.eq("id", id)
                .set("plan_status", 7)
                .set("bill_before_status", st != null ? st : 0)
                .set("voided_by", operator)
                .set("voided_at", LocalDateTime.now())
                .set("void_reason", reason);
        update(uw);
        log.info("作废MRP计划: {} 操作人: {} 原因: {}", plan.getPlanNo(), operator, reason);
    }

    /** 反作废: plan_status=7 → 恢复作废前状态 */
    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        MrpPlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException("MRP计划不存在");
        }
        Integer st = plan.getPlanStatus();
        if (st == null || st != 7) {
            throw new BusinessException("只有已作废状态的计划可反作废，当前状态: " + planStatusText(st));
        }
        Integer before = plan.getBillBeforeStatus() != null ? plan.getBillBeforeStatus() : 0;
        UpdateWrapper<MrpPlan> uw = new UpdateWrapper<>();
        uw.eq("id", id)
                .set("plan_status", before)
                .set("bill_before_status", null)
                .set("voided_by", null)
                .set("voided_at", null)
                .set("void_reason", null);
        update(uw);
        log.info("反作废MRP计划: {} 恢复状态: {} 操作人: {}", plan.getPlanNo(), planStatusText(before), operator);
    }

    /** 结案: 已生效类(2/3) → close_status=1 */
    @Override
    @Transactional
    public void closeOrder(Long id, String operator) {
        MrpPlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException("MRP计划不存在");
        }
        if (!BillStatusUtils.isApprovedClass(plan.getPlanStatus())) {
            throw new BusinessException("只有已生效或已执行状态的计划可结案，当前状态: " + planStatusText(plan.getPlanStatus()));
        }
        if (Integer.valueOf(1).equals(plan.getCloseStatus())) {
            throw new BusinessException("MRP计划[" + plan.getPlanNo() + "]已结案，不能重复结案");
        }
        UpdateWrapper<MrpPlan> uw = new UpdateWrapper<>();
        uw.eq("id", id)
                .set("close_status", 1)
                .set("closed_by", operator)
                .set("closed_at", LocalDateTime.now())
                .set("closed_reason", "手动结案");
        update(uw);
        log.info("MRP计划结案: {}  操作人: {}", plan.getPlanNo(), operator);
    }

    /** 反结案: close_status=0 */
    @Override
    @Transactional
    public void reopenOrder(Long id, String operator) {
        MrpPlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException("MRP计划不存在");
        }
        if (!Integer.valueOf(1).equals(plan.getCloseStatus())) {
            throw new BusinessException("只有已结案状态可反结案");
        }
        UpdateWrapper<MrpPlan> uw = new UpdateWrapper<>();
        uw.eq("id", id)
                .set("close_status", 0)
                .set("closed_by", operator)
                .set("closed_at", LocalDateTime.now());
        update(uw);
        log.info("MRP计划反结案: {}  操作人: {}", plan.getPlanNo(), operator);
    }

    /** planStatus(0草稿/1已运算/2已确认/3已执行/7已作废) → 前端状态文案 */
    private String planStatusText(Integer planStatus) {
        if (planStatus == null) return "草稿";
        switch (planStatus) {
            case 0: return "草稿";
            case 1: return "待审核";   // 已运算, 待确认
            case 2: return "已生效";   // 已确认
            case 3: return "已执行";
            case 7: return "已作废";
            default: return "已生效";
        }
    }

    /** 填充前端展示用状态文案 */
    private void fillStatusText(MrpPlan plan) {
        if (plan != null) {
            plan.setStatus(planStatusText(plan.getPlanStatus()));
        }
    }

    /** 前端状态筛选参数 → plan_status 数字码(支持中文文案和数字) */
    private Integer parseStatusCode(String status) {
        try {
            return Integer.parseInt(status.trim());
        } catch (NumberFormatException ignored) {
        }
        switch (status.trim()) {
            case "草稿":
            case "已驳回":
            case "已取消":
                return 0;
            case "待审核":
                return 1;
            case "已生效":
            case "已确认":
                return 2;
            case "已执行":
                return 3;
            case "已作废":
                return 7;
            default:
                return null;
        }
    }

    @Override
    @Transactional
    public void runMrp(Long id) {
        MrpPlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException("MRP计划不存在");
        }
        if (plan.getPlanStatus() != null && plan.getPlanStatus() != 0) {
            throw new BusinessException("只有草稿状态的计划可以运行MRP运算");
        }

        log.info("开始MRP运算: planId={}, planNo={}", id, plan.getPlanNo());

        mrpPlanItemMapper.delete(new QueryWrapper<MrpPlanItem>().eq("plan_id", id));

        List<MrpDemand> demands = getDemands(plan);
        if (demands.isEmpty()) {
            throw new BusinessException("未找到需求来源数据");
        }

        Map<String, MrpPlanItem> itemMap = new LinkedHashMap<>();
        Map<String, LocalDate> earliestDemandDates = new HashMap<>();

        for (MrpDemand demand : demands) {
            processDemand(demand, itemMap, earliestDemandDates);
        }

        List<MrpPlanItem> items = new ArrayList<>();
        BigDecimal totalNetDemand = BigDecimal.ZERO;
        BigDecimal totalPurchaseSuggestion = BigDecimal.ZERO;
        BigDecimal totalProduceSuggestion = BigDecimal.ZERO;

        // 2026-10-09：可用库存只统计「参与MRP」的仓库（不合格品仓/废料仓默认不参与），全单只查一次仓库档案
        java.util.Set<Long> mrpWarehouseIds = mrpAvailableWarehouseIds();

        for (MrpPlanItem item : itemMap.values()) {
            item.setPlanId(id);

            BigDecimal availableStock = calculateAvailableStock(item.getMaterialId(), mrpWarehouseIds);
            item.setAvailableStock(availableStock);

            BigDecimal safetyStock = item.getSafetyStock() != null ? item.getSafetyStock() : BigDecimal.ZERO;
            BigDecimal netDemand = item.getGrossDemand().subtract(availableStock).add(safetyStock);
            if (netDemand.compareTo(BigDecimal.ZERO) < 0) {
                netDemand = BigDecimal.ZERO;
            }
            item.setNetDemand(netDemand);
            item.setPlanQuantity(netDemand);

            String suggestType = determineSuggestType(item.getMaterialId());
            item.setSuggestType(suggestType);

            Integer leadTime = calculateLeadTime(item.getMaterialId());
            item.setLeadTime(leadTime);

            LocalDate demandDate = earliestDemandDates.get(item.getMaterialCode());
            if (demandDate != null) {
                item.setSuggestDate(demandDate.minusDays(leadTime));
            } else {
                item.setSuggestDate(LocalDate.now().plusDays(leadTime));
            }

            totalNetDemand = totalNetDemand.add(netDemand);
            if ("PURCHASE".equals(suggestType) || "外购件".equals(suggestType)) {
                totalPurchaseSuggestion = totalPurchaseSuggestion.add(netDemand);
            } else if ("PRODUCE".equals(suggestType) || "自制件".equals(suggestType)) {
                totalProduceSuggestion = totalProduceSuggestion.add(netDemand);
            }

            items.add(item);
        }

        java.util.Map<Long, com.erp.masterdata.entity.Bom> bomCache = new java.util.HashMap<>();
        for (MrpPlanItem item : items) {
            fillItemBomSnapshot(item, bomCache);
            mrpPlanItemMapper.insert(item);
        }

        plan.setTotalNetDemand(totalNetDemand);
        plan.setTotalPurchaseSuggestion(totalPurchaseSuggestion);
        plan.setTotalProduceSuggestion(totalProduceSuggestion);
        plan.setPlanStatus(1);
        updateById(plan);

        log.info("MRP运算完成: 共{}条明细, 净需求{}, 采购建议{}, 生产建议{}",
                items.size(), totalNetDemand, totalPurchaseSuggestion, totalProduceSuggestion);
    }

    private List<MrpDemand> getDemands(MrpPlan plan) {
        List<MrpDemand> demands = new ArrayList<>();
        String demandSource = plan.getDemandSource();

        if ("SALE_ORDER".equals(demandSource)) {
            List<Long> orderIds = parseDemandOrderIds(plan.getDemandOrderIds());
            for (Long orderId : orderIds) {
                SaleOrder order = saleOrderService.getDetail(orderId);
                if (order != null && order.getItems() != null) {
                    for (SaleOrderItem item : order.getItems()) {
                        MrpDemand demand = new MrpDemand();
                        demand.setMaterialId(item.getMaterialId());
                        demand.setMaterialCode(item.getMaterialCode());
                        demand.setMaterialName(item.getMaterialName());
                        demand.setSpecification(item.getSpecification());
                        demand.setQuantity(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO);
                        demand.setDemandDate(item.getDeliveryDate() != null ? item.getDeliveryDate() : LocalDate.now());
                        // 保留销售订单明细关联，用于转生产/委外时回写
                        demand.setSaleOrderId(order.getOrderId());
                        demand.setSaleOrderItemId(item.getOrderItemId());
                        demand.setSaleOrderNo(order.getOrderNo());
                        demands.add(demand);
                    }
                }
            }
        }

        return demands;
    }

    private List<Long> parseDemandOrderIds(String demandOrderIds) {
        List<Long> ids = new ArrayList<>();
        if (demandOrderIds == null || demandOrderIds.isBlank()) {
            return ids;
        }
        try {
            ids = objectMapper.readValue(demandOrderIds, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.warn("解析需求订单ID失败: {}", demandOrderIds);
        }
        return ids;
    }

    private void processDemand(MrpDemand demand, Map<String, MrpPlanItem> itemMap,
                               Map<String, LocalDate> earliestDemandDates) {
        if (demand.getMaterialId() == null) {
            return;
        }

        String parentCode = demand.getMaterialCode();
        LocalDate demandDate = demand.getDemandDate();

        updateEarliestDate(earliestDemandDates, parentCode, demandDate);

        boolean hasBom = bomExplosionService.hasBom(demand.getMaterialId());

        if (hasBom) {
            List<BomExplosionService.BomExplosionItem> bomItems =
                    bomExplosionService.explodeBomMultiLevel(demand.getMaterialId(), demand.getQuantity(), 10);

            // 顶层物料（销售订单直产物料）：保留销售订单明细关联，用于转单回写
            addOrUpdateItem(itemMap, parentCode, demand.getMaterialId(), demand.getMaterialName(),
                    demand.getSpecification(), demand.getQuantity(), 0, null, demandDate,
                    demand.getSaleOrderId(), demand.getSaleOrderItemId(), demand.getSaleOrderNo());

            for (BomExplosionService.BomExplosionItem bomItem : bomItems) {
                // BOM 子件物料：不关联销售订单明细（子件无法对应到销售订单明细行）
                addOrUpdateItem(itemMap, bomItem.getMaterialCode(), bomItem.getMaterialId(),
                        bomItem.getMaterialName(), bomItem.getSpecification(),
                        bomItem.getTotalQuantity(), bomItem.getLevel(),
                        bomItem.getBomId(), demandDate, null, null, null);

                updateEarliestDate(earliestDemandDates, bomItem.getMaterialCode(), demandDate);
            }
        } else {
            // 无 BOM 的物料（如贸易型/原料）：保留销售订单明细关联
            addOrUpdateItem(itemMap, parentCode, demand.getMaterialId(), demand.getMaterialName(),
                    demand.getSpecification(), demand.getQuantity(), 0, null, demandDate,
                    demand.getSaleOrderId(), demand.getSaleOrderItemId(), demand.getSaleOrderNo());
        }
    }

    private void addOrUpdateItem(Map<String, MrpPlanItem> itemMap, String materialCode,
                                 Long materialId, String materialName, String specification,
                                 BigDecimal quantity, Integer level, Long bomId, LocalDate demandDate,
                                 Long saleOrderId, Long saleOrderItemId, String saleOrderNo) {
        if (itemMap.containsKey(materialCode)) {
            MrpPlanItem existing = itemMap.get(materialCode);
            existing.setGrossDemand(existing.getGrossDemand().add(quantity));
            // 同一物料来自多个销售订单明细时，保留首个非空的销售订单明细关联
            // （注：MRP以materialCode聚合，多来源时无法精确分摊到多个销售订单明细；
            //  此处保留首个来源以保证至少有一条关联可回写）
            if (existing.getSaleOrderItemId() == null && saleOrderItemId != null) {
                existing.setSaleOrderId(saleOrderId);
                existing.setSaleOrderItemId(saleOrderItemId);
                existing.setSaleOrderNo(saleOrderNo);
            }
        } else {
            MrpPlanItem item = new MrpPlanItem();
            item.setMaterialId(materialId);
            item.setMaterialCode(materialCode);
            item.setMaterialName(materialName);
            item.setSpecification(specification);
            item.setGrossDemand(quantity);
            item.setAvailableStock(BigDecimal.ZERO);
            item.setSafetyStock(BigDecimal.ZERO);
            item.setNetDemand(BigDecimal.ZERO);
            item.setPlanQuantity(BigDecimal.ZERO);
            item.setLevel(level);
            item.setRelatedBomId(bomId);
            item.setUnit("");
            item.setSaleOrderId(saleOrderId);
            item.setSaleOrderItemId(saleOrderItemId);
            item.setSaleOrderNo(saleOrderNo);
            itemMap.put(materialCode, item);
        }
    }

    /**
     * 填充计划明细的 BOM 清单编码/版本（2026-11-06 行级 BOM）。
     * <p>按 relatedBomId 查一次 BOM 档案并缓存，避免同一方案多次查询；查不到则留空，不编造。</p>
     */
    private void fillItemBomSnapshot(MrpPlanItem item,
                                     java.util.Map<Long, com.erp.masterdata.entity.Bom> bomCache) {
        if (item == null || item.getRelatedBomId() == null) return;
        if (item.getBomCode() != null && !item.getBomCode().isBlank()) return;
        Long bomId = item.getRelatedBomId();
        com.erp.masterdata.entity.Bom bom = bomCache.computeIfAbsent(bomId, id -> {
            try {
                return bomMapper.selectById(id);
            } catch (Exception e) {
                return null;
            }
        });
        if (bom != null) {
            item.setBomCode(bom.getBomCode());
            item.setBomVersion(bom.getBomVersion());
        }
    }

    private void updateEarliestDate(Map<String, LocalDate> dateMap, String materialCode, LocalDate date) {
        if (date == null) {
            return;
        }
        LocalDate existing = dateMap.get(materialCode);
        if (existing == null || date.isBefore(existing)) {
            dateMap.put(materialCode, date);
        }
    }

    /**
     * 可用库存（2026-10-09 修正）：只统计「参与MRP = 开」的仓库。
     * <p>修正前按物料把所有仓库的可用量加总，会把「不合格品仓 / 废料仓」里不能用（待报废/已判废）的存货
     * 也算成可用，导致净需求被少算 → 少采购 / 少生产 → 现场投料时才发现欠料。</p>
     */
    private BigDecimal calculateAvailableStock(Long materialId, Set<Long> mrpWarehouseIds) {
        if (materialId == null) {
            return BigDecimal.ZERO;
        }
        // 没有任何仓库参与 MRP（或未取到仓库清单）→ 库存一律不计入可用量（宁可多买，不可少买）
        if (mrpWarehouseIds == null || mrpWarehouseIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalStock = BigDecimal.ZERO;
        List<Inventory> invList = inventoryService.list(
                new QueryWrapper<Inventory>().eq("material_id", materialId).in("stock_id", mrpWarehouseIds));
        for (Inventory inv : invList) {
            if (inv.getAvailableQuantity() != null) {
                totalStock = totalStock.add(inv.getAvailableQuantity());
            } else if (inv.getQuantity() != null) {
                totalStock = totalStock.add(inv.getQuantity());
            }
        }
        return totalStock;
    }

    /**
     * 「参与MRP」的仓库 id 集合（2026-10-09）：仓库档案里「参与MRP」开关关闭的仓库
     * （建库默认把「不合格品仓」设为不参与）库存不得计入 MRP 可用量。
     * <p>老数据里该列可能为 NULL（本地实测存在），NULL 按「开关默认开」处理 = 参与 MRP，
     * 否则老仓库会被整体排除 → 可用库存全为 0 → 全面多买。</p>
     */
    private Set<Long> mrpAvailableWarehouseIds() {
        Set<Long> ids = new java.util.HashSet<>();
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
            log.warn("查询「参与MRP」仓库失败: {}", e.getMessage());
        }
        return ids;
    }

    private String determineSuggestType(Long materialId) {
        if (materialId == null) {
            return "PURCHASE";
        }
        // 优先按物料供应类型字段判断
        Material material = materialService.getById(materialId);
        if (material != null && material.getSupplyType() != null && !material.getSupplyType().isEmpty()) {
            String st = material.getSupplyType();
            if ("SELF_MADE".equalsIgnoreCase(st)) return "PRODUCE";
            if ("SUBCONTRACT".equalsIgnoreCase(st)) return "SUBCONTRACT";
            if ("PURCHASE".equalsIgnoreCase(st)) return "PURCHASE";
        }
        // 回退到 BOM 判断：有 BOM 视为自制
        boolean hasBom = bomExplosionService.hasBom(materialId);
        if (hasBom) {
            return "PRODUCE";
        } else {
            return "PURCHASE";
        }
    }

    private Integer calculateLeadTime(Long materialId) {
        if (materialId == null) {
            return 7;
        }
        Material material = materialService.getById(materialId);
        if (material != null) {
            return 7;
        }
        return 7;
    }

    @Override
    @Transactional
    public void confirmPlan(Long id) {
        MrpPlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException("MRP计划不存在");
        }
        if (plan.getPlanStatus() == null || plan.getPlanStatus() != 1) {
            throw new BusinessException("只有已运算状态的计划可以确认");
        }
        plan.setPlanStatus(2);
        updateById(plan);
        log.info("确认MRP计划: {}", plan.getPlanNo());
    }

    @Override
    @Transactional
    public void generatePurchaseOrder(Long id, List<Long> itemIds) {
        MrpPlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException("MRP计划不存在");
        }
        if (plan.getPlanStatus() == null || plan.getPlanStatus() != 2) {
            throw new BusinessException("只有已确认状态的计划可以生成采购订单");
        }

        List<MrpPlanItem> items;
        if (itemIds != null && !itemIds.isEmpty()) {
            items = mrpPlanItemMapper.selectBatchIds(itemIds);
        } else {
            items = mrpPlanItemMapper.selectList(
                    new QueryWrapper<MrpPlanItem>().eq("plan_id", id)
                            .in("suggest_type", "PURCHASE", "外购件"));
        }

        // 采购订单一个订单只能有一个供应商：按“物料默认供应商”分组建单
        // 分组稳定可预期：supplierId 升序（TreeMap 自然序），无默认供应商的统一排到最后一张“未指定供应商”草稿单
        Map<Long, List<MrpPlanItem>> supplierGroups = new TreeMap<>();
        Map<Long, String> supplierNames = new HashMap<>();
        List<MrpPlanItem> noSupplierItems = new ArrayList<>();
        for (MrpPlanItem item : items) {
            if (item.getPlanQuantity() == null || item.getPlanQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Material mat = materialService.getById(item.getMaterialId());
            Long supplierId = mat != null ? mat.getDefaultSupplierId() : null;
            if (supplierId == null) {
                noSupplierItems.add(item);
            } else {
                supplierGroups.computeIfAbsent(supplierId, k -> new ArrayList<>()).add(item);
                if (!supplierNames.containsKey(supplierId)) {
                    supplierNames.put(supplierId, mat.getDefaultSupplierName());
                }
            }
        }

        int orderCount = 0;
        for (Map.Entry<Long, List<MrpPlanItem>> entry : supplierGroups.entrySet()) {
            createPurchaseOrderForGroup(plan, entry.getKey(), supplierNames.get(entry.getKey()),
                    entry.getValue(), null);
            orderCount++;
        }
        if (!noSupplierItems.isEmpty()) {
            createPurchaseOrderForGroup(plan, null, null, noSupplierItems,
                    "以下明细的物料未维护默认供应商，请补充供应商后提交");
            orderCount++;
        }

        log.info("生成采购订单: planNo={}, 共{}条采购建议, 生成{}张草稿采购订单（其中未指定供应商{}条明细）",
                plan.getPlanNo(), items.size(), orderCount, noSupplierItems.size());
    }

    /**
     * 按分组生成一张草稿采购订单，并逐行落库明细。
     *
     * @param supplierId   供应商ID；为 null 表示“未指定供应商”分组
     * @param supplierName 供应商名称
     * @param groupItems   该分组的 MRP 采购建议明细
     * @param extraRemark  主表附加备注（未指定供应商提示）
     */
    private void createPurchaseOrderForGroup(MrpPlan plan, Long supplierId, String supplierName,
                                             List<MrpPlanItem> groupItems, String extraRemark) {
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo(purchaseOrderService.generateOrderNo());
        order.setOrderDate(LocalDate.now());
        order.setSupplierId(supplierId);
        order.setSupplierName(supplierName);
        // currencyId：取基础币种（实测库中 currency_id=1 为 CNY 且 is_base_currency=1）。
        // 说明：当前库 purchase_order.supplier_id / currency_id 均可空（已用 information_schema 核实），
        // 故"未指定供应商"的草稿单可以落库，由用户后续补供应商。
        order.setCurrencyId(1L);
        order.setStatus("草稿");
        order.setApprovalStatus("待审核");
        order.setExchangeRate(BigDecimal.ONE);
        order.setIsClosed(0);
        order.setSourceBillType("MRP计划");
        order.setSourceBillNo(plan.getPlanNo());
        String baseRemark = "MRP计划生成: " + plan.getPlanName() + "（计划号:" + plan.getPlanNo() + "）";
        order.setRemark(extraRemark == null ? baseRemark : baseRemark + "；" + extraRemark);

        List<PurchaseOrderItem> poItems = new ArrayList<>();
        int lineNo = 1;
        for (MrpPlanItem item : groupItems) {
            PurchaseOrderItem poItem = new PurchaseOrderItem();
            poItem.setLineNo(lineNo);
            poItem.setMaterialId(item.getMaterialId());
            poItem.setMaterialCode(item.getMaterialCode());
            poItem.setMaterialName(item.getMaterialName());
            poItem.setSpecification(item.getSpecification());
            // 单位取自物料主档：基本单位优先，兜底采购单位（purchase_order_item.unit_id 为 NOT NULL）
            Material mat = materialService.getById(item.getMaterialId());
            if (mat != null) {
                poItem.setUnitId(mat.getBaseUnitId() != null ? mat.getBaseUnitId() : mat.getPurchaseUnitId());
            }
            poItem.setQuantity(item.getPlanQuantity());
            poItem.setUnitPrice(BigDecimal.ZERO);
            poItem.setTaxRate(BigDecimal.ZERO);
            poItem.setDiscountRate(BigDecimal.ZERO);
            poItem.setDiscountAmount(BigDecimal.ZERO);
            poItem.setAmount(BigDecimal.ZERO);
            poItem.setTaxAmount(BigDecimal.ZERO);
            poItem.setNetAmount(BigDecimal.ZERO);
            poItem.setReceivedQuantity(BigDecimal.ZERO);
            poItem.setInvoicedQuantity(BigDecimal.ZERO);
            poItem.setTurnedInputQuantity(BigDecimal.ZERO);
            poItem.setTurnedReturnQuantity(BigDecimal.ZERO);
            poItem.setDetailCloseStatus(0);
            poItem.setDeliveryDate(item.getSuggestDate());
            poItem.setSourceBillType("MRP计划");
            poItem.setSourceBillNo(plan.getPlanNo());
            // sourceType 在本项目语义为“源单据类型(中文，如 采购申请单)”，写 "mrp" 不匹配语义，故留空，仅用 sourceBillType/No 溯源
            poItem.setRemark("MRP计划[" + plan.getPlanNo() + "]第" + lineNo + "行生成(来源明细ID:" + item.getId() + ")");
            poItems.add(poItem);
            lineNo++;
        }

        purchaseOrderService.saveOrderWithItems(order, poItems);

        // 铁律：自动生成单据必须逐行落库明细，落库后校验明细行数
        PurchaseOrder saved = purchaseOrderService.getDetail(order.getOrderId());
        int savedLines = saved.getItems() == null ? 0 : saved.getItems().size();
        if (savedLines != groupItems.size()) {
            throw new BusinessException("采购订单明细落库校验失败：订单[" + order.getOrderNo()
                    + "]预期" + groupItems.size() + "行，实际" + savedLines + "行");
        }
    }

    @Override
    @Transactional
    public void generateProduceOrder(Long id, List<Long> itemIds) {
        MrpPlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException("MRP计划不存在");
        }
        if (plan.getPlanStatus() == null || plan.getPlanStatus() != 2) {
            throw new BusinessException("只有已确认状态的计划可以生成生产订单");
        }

        List<MrpPlanItem> items;
        if (itemIds != null && !itemIds.isEmpty()) {
            items = mrpPlanItemMapper.selectBatchIds(itemIds);
        } else {
            items = mrpPlanItemMapper.selectList(
                    new QueryWrapper<MrpPlanItem>().eq("plan_id", id)
                            .in("suggest_type", "PRODUCE", "自制件"));
        }

        int count = 0;
        for (MrpPlanItem item : items) {
            if (item.getPlanQuantity() == null || item.getPlanQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            WorkOrder workOrder = new WorkOrder();
            workOrder.setMaterialId(item.getMaterialId());
            workOrder.setMaterialCode(item.getMaterialCode());
            workOrder.setMaterialName(item.getMaterialName());
            // 从物料主数据获取单位（production_order.unit_id 为 NOT NULL）
            Material mat = materialService.getById(item.getMaterialId());
            if (mat != null) {
                workOrder.setUnitId(mat.getBaseUnitId() != null ? mat.getBaseUnitId() : mat.getPurchaseUnitId());
                if (mat.getModel() != null) workOrder.setModel(mat.getModel());
            }
            workOrder.setQuantity(item.getPlanQuantity());
            workOrder.setPlanStartDate(item.getSuggestDate());
            workOrder.setPlanEndDate(item.getSuggestDate() != null ?
                    item.getSuggestDate().plusDays(item.getLeadTime() != null ? item.getLeadTime() : 7) : null);
            workOrder.setStatus("草稿");
            workOrder.setSourceType("MRP");
            workOrder.setSourceNo(plan.getPlanNo());
            workOrder.setRemark("MRP计划生成: " + plan.getPlanName());
            workOrder.setBomId(item.getRelatedBomId());
            // 保留销售订单明细关联，用于追溯和回写
            if (item.getSaleOrderId() != null) {
                workOrder.setSourceSaleOrderId(item.getSaleOrderId());
                workOrder.setSourceSaleOrderItemId(item.getSaleOrderItemId());
                workOrder.setSalesOrderNo(item.getSaleOrderNo());
            }

            List<WorkOrderMaterial> materials = new ArrayList<>();
            workOrderService.saveOrderWithItems(workOrder, materials);
            // 生成即按 BOM 展开用料清单，避免草稿态工单"原料明细"为空（下达时会再展开一次，方法幂等）
            workOrderService.generateMaterialsFromBom(workOrder);
            count++;

            // 回写销售订单明细的"已转生产数量"
            if (item.getSaleOrderId() != null && item.getSaleOrderItemId() != null) {
                saleOrderService.updateTurnedQuantity(item.getSaleOrderId(), item.getSaleOrderItemId(),
                        item.getPlanQuantity(), "produce");
            }
        }

        log.info("生成生产订单: planNo={}, 共{}张工单", plan.getPlanNo(), count);
    }

    /**
     * MRP批量生成委外订单
     */
    @Override
    @Transactional
    public void generateSubcontractOrder(Long id, List<Long> itemIds) {
        MrpPlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException("MRP计划不存在");
        }
        if (plan.getPlanStatus() == null || plan.getPlanStatus() != 2) {
            throw new BusinessException("只有已确认状态的计划可以生成委外订单");
        }

        List<MrpPlanItem> items;
        if (itemIds != null && !itemIds.isEmpty()) {
            items = mrpPlanItemMapper.selectBatchIds(itemIds);
        } else {
            items = mrpPlanItemMapper.selectList(
                    new QueryWrapper<MrpPlanItem>().eq("plan_id", id)
                            .in("suggest_type", "SUBCONTRACT", "委外件"));
        }

        int count = 0;
        for (MrpPlanItem item : items) {
            if (item.getPlanQuantity() == null || item.getPlanQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // 创建委外订单
            SubcontractOrder so = new SubcontractOrder();
            so.setOrderNo(generateSubcontractOrderNo());
            so.setOrderDate(LocalDate.now());
            so.setExpectDate(item.getSuggestDate());
            so.setSupplierId(1L);
            so.setSupplierName("默认供应商");
            so.setProcessItem(item.getMaterialName());
            so.setTotalQuantity(item.getPlanQuantity());
            so.setUnitPrice(BigDecimal.ZERO);
            so.setTotalAmount(BigDecimal.ZERO);
            so.setTaxRate(BigDecimal.ZERO);
            so.setTaxAmount(BigDecimal.ZERO);
            so.setNetAmount(BigDecimal.ZERO);
            so.setCurrencyCode("CNY");
            so.setExchangeRate(BigDecimal.ONE);
            so.setBaseTotalAmount(BigDecimal.ZERO);
            so.setBaseTaxAmount(BigDecimal.ZERO);
            so.setBaseNetAmount(BigDecimal.ZERO);
            so.setSourceBillType("MRP计划");
            so.setSourceBillNo(plan.getPlanNo());
            so.setStatus(0);
            so.setCloseStatus(0);
            so.setRemark("MRP计划生成: " + plan.getPlanName());

            // 创建委外订单明细（含销售订单关联，用于回写）
            SubcontractOrderItem soItem = new SubcontractOrderItem();
            soItem.setMaterialId(item.getMaterialId());
            soItem.setMaterialCode(item.getMaterialCode());
            soItem.setMaterialName(item.getMaterialName());
            soItem.setSpecification(item.getSpecification());
            soItem.setUnit(item.getUnit());
            soItem.setQuantity(item.getPlanQuantity());
            soItem.setFinishedQty(BigDecimal.ZERO);
            soItem.setActualQty(BigDecimal.ZERO);
            soItem.setUnitPrice(BigDecimal.ZERO);
            soItem.setAmount(BigDecimal.ZERO);
            soItem.setTaxRate(BigDecimal.ZERO);
            soItem.setTaxAmount(BigDecimal.ZERO);
            soItem.setNetAmount(BigDecimal.ZERO);
            soItem.setDetailCloseStatus(0);
            if (item.getSaleOrderId() != null) {
                soItem.setSourceSaleOrderId(item.getSaleOrderId());
                soItem.setSourceSaleOrderItemId(item.getSaleOrderItemId());
            }
            soItem.setRemark("MRP计划生成");

            List<SubcontractOrderItem> soItems = new ArrayList<>();
            soItems.add(soItem);

            subcontractOrderService.saveOrderWithItems(so, soItems);
            count++;

            // 回写销售订单明细的"已转委外数量"
            if (item.getSaleOrderId() != null && item.getSaleOrderItemId() != null) {
                saleOrderService.updateTurnedQuantity(item.getSaleOrderId(), item.getSaleOrderItemId(),
                        item.getPlanQuantity(), "subcontract");
            }
        }

        log.info("生成委外订单: planNo={}, 共{}张委外单", plan.getPlanNo(), count);
    }

    private String generateSubcontractOrderNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "SUB-" + dateStr + "-";
        return prefix + String.format("%04d", System.currentTimeMillis() % 10000);
    }

    @Override
    @Transactional
    public void deletePlan(Long id) {
        MrpPlan plan = getById(id);
        if (plan == null) {
            throw new BusinessException("MRP计划不存在");
        }
        mrpPlanItemMapper.delete(new QueryWrapper<MrpPlanItem>().eq("plan_id", id));
        // 【2026-09-19 整单删除】先清明细/从表，再删主表（用户硬要求：不允许留孤儿明细）
        billCascadeDeleteService.deleteChildren("mrp_plan", id);
        removeById(id);
        log.info("删除MRP计划: {}", plan.getPlanNo());
    }

    @Override
    public String generatePlanNo() {
        String docType = "MRP_PLAN";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "MRP-" + dateStr + "-";
            QueryWrapper<MrpPlan> wrapper = new QueryWrapper<>();
            wrapper.likeRight("plan_no", prefix).orderByDesc("plan_no").last("LIMIT 1");
            MrpPlan last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getPlanNo() != null) {
                try {
                    seq = Integer.parseInt(last.getPlanNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    @lombok.Data
    private static class MrpDemand {
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String specification;
        private BigDecimal quantity;
        private LocalDate demandDate;
        // 销售订单关联字段（用于MRP明细追溯销售订单明细，转单时回写）
        private Long saleOrderId;
        private Long saleOrderItemId;
        private String saleOrderNo;
    }
}
