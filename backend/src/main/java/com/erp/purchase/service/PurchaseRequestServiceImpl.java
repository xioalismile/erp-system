package com.erp.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.approval.service.ApprovalRuntimeService;
import com.erp.approval.service.BillApprovalCallback;
import com.erp.common.bill.BillApprovalAdapter;
import com.erp.common.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.ApprovalGateHelper;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.common.bill.BillStatusEnum;
import com.erp.common.bill.BillStatusUtils;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.basedata.entity.Currency;
import com.erp.masterdata.entity.Material;
import com.erp.basedata.service.EmployeeService;
import com.erp.basedata.service.CurrencyService;
import com.erp.masterdata.service.MaterialService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.entity.PurchaseOrderItem;
import com.erp.purchase.entity.PurchaseRequest;
import com.erp.purchase.entity.PurchaseRequestItem;
import com.erp.purchase.mapper.PurchaseOrderMapper;
import com.erp.purchase.mapper.PurchaseRequestItemMapper;
import com.erp.purchase.mapper.PurchaseRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.query.ListQuerySupport;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.common.util.CustomFieldSearchHelper;
import com.erp.common.util.SortHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 采购申请单服务实现
 * 文档3.1 单据状态流转: 草稿 → 待审批 → 已审批 → 已关闭
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class PurchaseRequestServiceImpl extends AbstractBillService<PurchaseRequestMapper, PurchaseRequest> implements PurchaseRequestService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义全面推广）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键，两种模式一套参数）；
        // 值 = 实体属性名（registerProps 由 MyBatis-Plus 元数据解析成真实列名，防手写列名笔误）。
        // 按名称筛但列是外键的场景（申请人/部门/结算方式）：Spec.column 写外键「属性名」，
        // 不写裸列名 —— 属性名注册口径下解析不到同名属性时该条件会被忽略（见 BillHeaderFilterSupport.apply）。
        Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("requestNo", new BillHeaderFilterSupport.Spec("requestNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("applyType", new BillHeaderFilterSupport.Spec("applyType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceType", new BillHeaderFilterSupport.Spec("sourceType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceBillNo", new BillHeaderFilterSupport.Spec("sourceBillNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("approvedBy", new BillHeaderFilterSupport.Spec("approvedBy", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（枚举 / 外键 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("applierName", new BillHeaderFilterSupport.Spec("applicantId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("deptName", new BillHeaderFilterSupport.Spec("departmentId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("settleModeName", new BillHeaderFilterSupport.Spec("settleModeId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("requestDate", new BillHeaderFilterSupport.Spec("requestDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("demandDate", new BillHeaderFilterSupport.Spec("demandDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("totalQty", new BillHeaderFilterSupport.Spec("totalQty", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("purchaseRequest", PurchaseRequest.class, f);
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "applicant_id",
            "applier_name",
            "apply_dept_name",
            "apply_type",
            "approval_status",
            "approved_at",
            "approved_by",
            "close_date",
            "created_at",
            "created_by",
            "demand_date",
            "department_id",
            "is_closed",
            "is_urgent",
            "line_no",
            "org_code",
            "remark",
            "request_date",
            "request_id",
            "request_no",
            "source_bill_no",
            "source_bill_type",
            "source_type",
            "status",
            "total_qty",
            "updated_at",
            "updated_by",
            "void_reason",
            "voided_at",
            "voided_by"
        );

    private final PurchaseRequestItemMapper itemMapper;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final EmployeeService employeeService;
    private final MaterialService materialService;
    private final UnitOfMeasureService unitOfMeasureService;
    private final PurchaseOrderService purchaseOrderService;
    private final CurrencyService currencyService;

    /** 下游引用校验（统一状态机重置草稿/作废/删除前使用） */
    @Lazy
    @Autowired
    private BillRelationService billRelationService;

    private static final int SCALE = 4;

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "采购申请单"; }
    @Override protected String getApprovalBillType() { return "PURCHASE_REQUEST"; }
    @Override protected String getBillNo(PurchaseRequest b) { return b.getRequestNo(); }
    @Override protected Object getId(PurchaseRequest b) { return b.getRequestId(); }
    @Override protected String getStatus(PurchaseRequest b) { return b.getStatus(); }
    @Override protected void setStatus(PurchaseRequest b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(PurchaseRequest b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(PurchaseRequest b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(PurchaseRequest b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(PurchaseRequest b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(PurchaseRequest b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(PurchaseRequest b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(PurchaseRequest b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(PurchaseRequest b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(PurchaseRequest b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(PurchaseRequest b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(PurchaseRequest b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(PurchaseRequest b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(PurchaseRequest b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用（下游=采购订单） */
    @Override
    public List<String> listApprovedDownstreamBills(PurchaseRequest bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getRequestNo());
    }

    /** 审核通过影响：审批数量未填时默认等于申请数量（统一状态机在生效后调用） */
    @Override
    public void onApproved(PurchaseRequest request) {
        List<PurchaseRequestItem> items = itemMapper.selectList(
                new QueryWrapper<PurchaseRequestItem>().eq("request_id", request.getRequestId()));
        if (items != null) {
            for (PurchaseRequestItem item : items) {
                if (item.getApprovedQty() == null || item.getApprovedQty().compareTo(BigDecimal.ZERO) <= 0) {
                    item.setApprovedQty(item.getApplyQty());
                    itemMapper.updateById(item);
                }
            }
        }
        log.info("采购申请单审核影响: {} 审批人: {}", request.getRequestNo(), request.getApprovedBy());
    }

    @Override
    @Transactional
    public void saveRequestWithItems(PurchaseRequest request, List<PurchaseRequestItem> items) {
        boolean isNew = request.getRequestId() == null;

        // 业务规则: 待审批不可修改, 已审批需先重置草稿, 已关闭不可修改
        if (!isNew) {
            PurchaseRequest existing = getById(request.getRequestId());
            if (existing != null) {
                if ("待审批".equals(existing.getStatus()) || "待审核".equals(existing.getStatus())) {
                    throw new BusinessException("待审核状态的申请单不可修改");
                }
                if ("已审批".equals(existing.getStatus()) || "已生效".equals(existing.getStatus())) {
                    throw new BusinessException("已生效的申请单不可直接修改，请先重置草稿");
                }
                if ("已关闭".equals(existing.getStatus())) {
                    throw new BusinessException("已关闭的申请单不可修改");
                }
                if ("已取消".equals(existing.getStatus())) {
                    throw new BusinessException("已取消的申请单不可修改");
                }
            }
        }

        // 业务规则: 申请单号按编码规则自动生成, 不可手动修改
        if (request.getRequestNo() == null || request.getRequestNo().isBlank()) {
            request.setRequestNo(generateRequestNo());
        }

        // 设置默认值
        if (request.getStatus() == null) request.setStatus("草稿");
        // 草稿态单据的审批状态只能是"草稿"，与 status 保持镜像(提交后才允许出现"待审核")
        if (request.getApprovalStatus() == null || request.getApprovalStatus().isBlank()
                || ("草稿".equals(request.getStatus()) && "待审核".equals(request.getApprovalStatus()))) {
            request.setApprovalStatus("草稿");
        }
        if (request.getRequestDate() == null) request.setRequestDate(LocalDate.now());
        if (request.getApplyType() == null) request.setApplyType("普通采购");
        if (request.getDemandDate() == null) request.setDemandDate(LocalDate.now().plusDays(7));
        if (request.getIsUrgent() == null) request.setIsUrgent(0);
        if (request.getIsClosed() == null) request.setIsClosed(0);
        if (request.getTotalQty() == null) request.setTotalQty(BigDecimal.ZERO);
        if (request.getCreatedAt() == null) request.setCreatedAt(LocalDateTime.now());
        if (request.getUpdatedAt() == null) request.setUpdatedAt(LocalDateTime.now());
        // createdBy/updatedBy 不在服务层兜底，交由 AuditMetaObjectHandler 按当前登录人自动填充
        // （导入/UI 保存=登录人；MRP 后台下推等无请求上下文场景自动落 system）
        // 部门默认值: MRP下推或未指定部门时默认采购部
        if (request.getDepartmentId() == null) {
            request.setDepartmentId(3L);
            request.setApplyDeptName("采购部");
        }
        // 申请人默认值: 未指定时使用创建人作为申请人
        if (request.getApplicantId() == null) {
            request.setApplicantId(1L);
            request.setApplierName(request.getCreatedBy() != null ? request.getCreatedBy() : "system");
        }

        // 保存宽松：丢弃未选择物料的空行（这些字段数据库为 NOT NULL，无法落库）
        if (items != null) {
            List<PurchaseRequestItem> validItems = new ArrayList<>();
            for (PurchaseRequestItem it : items) {
                if (it != null && it.getMaterialId() != null) validItems.add(it);
            }
            items = validItems;
        }

        // 汇总申请总数量
        recalculateTotalQty(request, items);

        if (isNew) {
            save(request);
        } else {
            updateById(request);
        }

        if (items != null) {
            // items为null时不删除既有明细, 避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<PurchaseRequestItem>().eq("request_id", request.getRequestId()));
            // 业务规则: 表体行号从1开始, 步长1
            int lineNo = 1;
            for (PurchaseRequestItem item : items) {
                item.setRequestItemId(null);
                item.setRequestId(request.getRequestId());
                item.setLineNo(lineNo);
                if (item.getApprovedQty() == null) item.setApprovedQty(BigDecimal.ZERO);
                if (item.getCreatedAt() == null) item.setCreatedAt(LocalDateTime.now());
                if (item.getUpdatedAt() == null) item.setUpdatedAt(LocalDateTime.now());
                itemMapper.insert(item);
                lineNo += 1;
            }
        }

        log.info("采购申请单保存成功: {} 状态: {} 总数量: {}", request.getRequestNo(), request.getStatus(), request.getTotalQty());
    }

    /**
     * 业务规则校验
     * - 申请数量必须大于0
     * - 审批数量不可超过申请数量
     */
    private void validateRequest(PurchaseRequest request, List<PurchaseRequestItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("采购申请单至少需要一行明细");
        }
        for (int i = 0; i < items.size(); i++) {
            PurchaseRequestItem item = items.get(i);
            // 申请数量必须大于0
            if (item.getApplyQty() == null || item.getApplyQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("第" + (i + 1) + "行申请数量必须大于0");
            }
            // 审批数量不可超过申请数量
            if (item.getApprovedQty() != null && item.getApprovedQty().compareTo(BigDecimal.ZERO) > 0
                    && item.getApprovedQty().compareTo(item.getApplyQty()) > 0) {
                throw new BusinessException("第" + (i + 1) + "行审批数量不可超过申请数量");
            }
        }
        // 紧急采购类型校验
        if (request.getIsUrgent() != null && request.getIsUrgent() == 1
                && !"紧急采购".equals(request.getApplyType())) {
            request.setApplyType("紧急采购");
        }
    }

    /** 汇总申请总数量 FTotalQty */
    private void recalculateTotalQty(PurchaseRequest request, List<PurchaseRequestItem> items) {
        BigDecimal totalQty = BigDecimal.ZERO;
        if (items != null) {
            for (PurchaseRequestItem item : items) {
                BigDecimal qty = item.getApplyQty() != null ? item.getApplyQty() : BigDecimal.ZERO;
                totalQty = totalQty.add(qty);
            }
        }
        request.setTotalQty(totalQty.setScale(SCALE, java.math.RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<PurchaseRequest> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch) {
        QueryWrapper<PurchaseRequest> wrapper = new QueryWrapper<>();
        // 排序白名单（ListQuerySupport.applySort 内部走 SortHelper 列名白名单，未命中则用默认排序）
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PurchaseRequest.class);
        // 关键词：申请单号 + 申请人 多列匹配，强制成组（前端提示"输入申请单号/申请人搜索"，原实现只匹配单号）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "request_no", "applier_name");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 日期区间（本页真实列 request_date）；员工维度=申请人（本页真实列 applicant_id）
        // 供应商维度不接入：本表无 supplier_id 列
        ListQuerySupport.applyDateRange(wrapper, "request_date", startDate, endDate);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "applicant_id");
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建/申请的；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "applicant_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "purchase_request", customSearch, CustomFieldSearchHelper.idColumnOf("purchase_request"));
        Page<PurchaseRequest> page = new Page<>(current, size);
        Page<PurchaseRequest> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）：
     * 白名单 + 参数化，非法/空白/白名单外入参在支持类内部忽略，不阻断列表查询。
     */
    @Override
    public PageResult<PurchaseRequest> pageQuery(int current, int size, String keyword, String status, String startDate, String endDate, String supplierId, String employeeId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch, Map<String, String> headerFilters) {
        QueryWrapper<PurchaseRequest> wrapper = new QueryWrapper<>();
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, PurchaseRequest.class);
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "request_no", "applier_name");
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 日期区间（本页真实列 request_date）；员工维度=申请人（本页真实列 applicant_id）
        ListQuerySupport.applyDateRange(wrapper, "request_date", startDate, endDate);
        ListQuerySupport.applyEmployee(wrapper, employeeId, "applicant_id");
        // 表头字段过滤：f_* → 白名单翻译（采购申请主表无供应商列，供应商维度不接入）
        BillHeaderFilterSupport.apply(wrapper, "purchaseRequest", headerFilters);
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "applicant_id");
        CustomFieldSearchHelper.apply(wrapper, "purchase_request", customSearch, CustomFieldSearchHelper.idColumnOf("purchase_request"));
        Page<PurchaseRequest> page = new Page<>(current, size);
        Page<PurchaseRequest> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public PurchaseRequest getDetail(Long id) {
        PurchaseRequest request = getById(id);
        if (request == null) throw new BusinessException("采购申请单不存在");
        List<PurchaseRequestItem> items = itemMapper.selectList(
                new QueryWrapper<PurchaseRequestItem>().eq("request_id", id).orderByAsc("line_no"));
        request.setItems(items);
        return request;
    }

    /** 提交审核：状态校验/严格校验/审批开关/审批任务统一由统一状态机基类执行 */
    @Override
    @Transactional
    public void submit(Long id, String submitter) {
        PurchaseRequest request = getById(id);
        if (request == null) throw new BusinessException("采购申请单不存在");
        super.submitForApproval(request, submitter != null && !submitter.isBlank()
                ? submitter : BillApprovalAdapter.currentSubmitter());
    }

    /** 提交审核前的严格校验（明细/申请人/物料/单位/申请数量 + 业务规则） */
    @Override
    protected void validateStrictSubmit(PurchaseRequest request) {
        Long id = request.getRequestId();
        List<PurchaseRequestItem> items = itemMapper.selectList(
                new QueryWrapper<PurchaseRequestItem>().eq("request_id", id).orderByAsc("line_no"));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("采购申请单没有明细，请至少添加一行物料后再提交");
        }

        if (request.getApplicantId() == null) {
            throw new BusinessException("申请人不能为空，请选择申请人");
        }
        if (employeeService.getById(request.getApplicantId()) == null) {
            throw new BusinessException("关联申请人不存在(applicantId=" + request.getApplicantId() + ")，请重新选择申请人");
        }

        for (int i = 0; i < items.size(); i++) {
            PurchaseRequestItem item = items.get(i);
            int lineNo = i + 1;
            if (item.getMaterialId() == null) {
                throw new BusinessException("第" + lineNo + "行物料不能为空");
            }
            Material mat = materialService.getById(item.getMaterialId());
            if (mat == null) {
                throw new BusinessException("第" + lineNo + "行关联物料不存在(materialId=" + item.getMaterialId() + ")，请重新选择物料");
            }
            String matInfo = (mat.getMaterialCode() != null ? mat.getMaterialCode() : "") + " " + (mat.getMaterialName() != null ? mat.getMaterialName() : "");
            if (item.getUnitId() != null && unitOfMeasureService.getById(item.getUnitId()) == null) {
                throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]关联单位不存在(unitId=" + item.getUnitId() + ")，请重新选择单位");
            }
            BigDecimal qty = item.getApplyQty() != null ? item.getApplyQty() : BigDecimal.ZERO;
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("第" + lineNo + "行物料[" + matInfo + "]申请数量必须大于0");
            }
        }

        // 明细业务校验（申请数量、审批数量、紧急采购类型）
        validateRequest(request, items);
    }

    // ================= 审核入口（统一状态机 + 审批引擎防绕过） =================

    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("PURCHASE_REQUEST", (Long) id) != null) {
            throw new BusinessException("采购申请单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 重置草稿：已关闭守卫 → 基类（下游校验+状态迁移+钩子）→ 显式清空审核人/时间 */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        PurchaseRequest request = getById(id);
        if (request == null) throw new BusinessException("采购申请单不存在");
        // 已关闭不可重置草稿（legacy close 只写 is_closed，未写 close_status，故此处显式守卫）
        if (request.getIsClosed() != null && request.getIsClosed() == 1) {
            throw new BusinessException("已关闭的申请单不可重置草稿");
        }
        // 状态校验 + 下游（采购订单）校验 + 状态迁移统一由基类执行
        super.reverseApprove(id, operator);
        // 使用 UpdateWrapper 显式置空 approved_by/approved_at
        UpdateWrapper<PurchaseRequest> uw = new UpdateWrapper<>();
        uw.eq("request_id", id)
                .set("approved_by", null)
                .set("approved_at", null)
                .set("updated_at", LocalDateTime.now())
                .set("updated_by", operator);
        update(uw);
        log.info("采购申请单重置草稿: {} 操作人: {}", request.getRequestNo(), operator);
    }

    /** 关闭: 已审批 → 已关闭 (全部生成采购订单后自动关闭, 或手动关闭) */
    @Override
    @Transactional
    public void close(Long id, String closer, String reason) {
        PurchaseRequest request = getById(id);
        if (request == null) throw new BusinessException("采购申请单不存在");
        if (!"已审批".equals(request.getStatus()) && !"已生效".equals(request.getStatus())) {
            throw new BusinessException("只有已生效状态可关闭，当前状态: " + request.getStatus());
        }
        UpdateWrapper<PurchaseRequest> uw = new UpdateWrapper<>();
        uw.eq("request_id", id)
                .set("status", "已关闭")
                // 双写：迁移期保持 status 不变，另写业务执行态（bus_status，见 BusStatusEnum 注释）
                .set("bus_status", "已关闭")
                .set("is_closed", 1)
                .set("close_date", LocalDateTime.now())
                .set("updated_at", LocalDateTime.now())
                .set("updated_by", closer);
        update(uw);
        log.info("采购申请单关闭: {} 关闭人: {} 原因: {}", request.getRequestNo(), closer, reason);
    }

    /** 取消: 草稿 → 已取消 */
    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        PurchaseRequest request = getById(id);
        if (request == null) throw new BusinessException("采购申请单不存在");
        if (!"草稿".equals(request.getStatus())) {
            throw new BusinessException("只有草稿状态可取消，当前状态: " + request.getStatus());
        }
        request.setStatus("已取消");
        if (reason != null && !reason.isBlank()) {
            request.setRemark((request.getRemark() != null ? request.getRemark() + " | " : "") + "取消原因: " + reason);
        }
        updateById(request);
        log.info("采购申请单取消: {} 原因: {}", request.getRequestNo(), reason);
    }

    /** 申请单号生成: 文档格式 PR-yyyyMMdd-0001 (带连字符, 4位流水号) */
    @Override
    public String generateRequestNo() {
        String docType = "PURCHASE_REQUEST";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String prefix = "PR-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
            QueryWrapper<PurchaseRequest> wrapper = new QueryWrapper<>();
            wrapper.likeRight("request_no", prefix).orderByDesc("request_no").last("LIMIT 1");
            PurchaseRequest last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getRequestNo() != null) {
                try {
                    seq = Integer.parseInt(last.getRequestNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    @Override
    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
    }

    /** 结案：统一状态机（已生效 + 未结案校验，写 close_status/closed_*） */
    @Override
    @Transactional
    public void closeOrder(Long id, String operator) {
        super.close(id, operator, "手动结案");
    }

    /** 反结案：统一状态机 */
    @Override
    @Transactional
    public void reopenOrder(Long id, String operator) {
        super.reopen(id, operator);
    }

    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        PurchaseRequest bill = getById(id);
        if (bill == null) throw new BusinessException("采购申请单不存在");
        // 已关闭（legacy close 写 is_closed）不可作废，须先反结案
        if (bill.getIsClosed() != null && bill.getIsClosed() == 1) {
            throw new BusinessException("采购申请单[" + bill.getRequestNo() + "]已结案，如需作废请先反结案");
        }
        // 重复作废/结案/下游引用校验 + 作废前状态记录 + 审计字段由基类执行
        super.voidBill(id, operator, reason);
    }

    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }

    @Override
    @Transactional
    public void deleteBill(Long id) {
        // 草稿校验 + 下游校验 + 整单级联删除由基类执行
        super.deleteBill(id);
    }

    @Override
    @Transactional
    public PurchaseOrder pushToOrder(Long id, String operator) {
        PurchaseRequest request = getDetail(id);
        if (request == null) {
            throw new BusinessException("采购申请单不存在");
        }
        if (!"已审批".equals(request.getStatus()) && !"已生效".equals(request.getStatus())) {
            throw new BusinessException("只有已生效的采购申请单才能下推");
        }
        List<PurchaseRequestItem> reqItems = request.getItems();
        if (reqItems == null || reqItems.isEmpty()) {
            throw new BusinessException("采购申请单无明细，无法下推");
        }

        // 构建采购订单
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo(purchaseOrderService.generateOrderNo());
        order.setOrderDate(LocalDate.now());
        // 币种：采购申请单没有币种字段，默认取基础币种并置汇率 1；
        // 否则下推后自动审核会被"币种不能为空"校验拦截，导致"申请→订单"链路走不通
        order.setCurrencyId(resolveBaseCurrencyId());
        order.setExchangeRate(BigDecimal.ONE);
        // 从第一个明细的建议供应商带出
        PurchaseRequestItem firstItem = reqItems.get(0);
        if (firstItem.getSuggestedSupplierId() != null) {
            order.setSupplierId(firstItem.getSuggestedSupplierId());
            order.setSupplierName(firstItem.getSuggestedSupplierName());
        }
        order.setDepartmentId(request.getDepartmentId());
        order.setDeptName(request.getApplyDeptName());
        order.setBuyerId(request.getApplicantId());
        order.setBuyerName(request.getApplierName());
        order.setDeliveryDate(request.getDemandDate());
        order.setSourceBillType("采购申请单");
        order.setSourceBillNo(request.getRequestNo());
        order.setRequestNo(request.getRequestNo());
        order.setRemark("由采购申请单 " + request.getRequestNo() + " 下推生成");
        order.setStatus("草稿");
        // 下推生成的采购订单处于草稿态, 审批状态须同为"草稿"(与保存兜底口径一致)
        order.setApprovalStatus("草稿");
        order.setIsClosed(0);
        String username = operator;
        if (username != null) order.setCreatedBy(username);

        // 构建采购订单明细
        List<PurchaseOrderItem> orderItems = new ArrayList<>();
        int lineNo = 1;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        for (PurchaseRequestItem ri : reqItems) {
            PurchaseOrderItem oi = new PurchaseOrderItem();
            oi.setLineNo(lineNo++);
            oi.setMaterialId(ri.getMaterialId());
            oi.setMaterialCode(ri.getMaterialCode());
            oi.setMaterialName(ri.getMaterialName());
            oi.setSpecification(ri.getModel());
            oi.setUnitId(ri.getUnitId());
            oi.setQuantity(ri.getApplyQty() != null ? ri.getApplyQty() : ri.getApprovedQty());
            oi.setReceivedQuantity(BigDecimal.ZERO);
            oi.setInvoicedQuantity(BigDecimal.ZERO);
            // 单价按优先级取（不得凭空编造价格），否则下推后自动审核会被设置中心参数 purchase.order.allow.zero.price=false 拦截：
            // 1) 采购申请明细(PurchaseRequestItem)无单价字段；2) 取物料主档采购价 purchase_price；
            // 3) 两者都取不到(为 null 或 0)则保持 0，并由系统参数校验拦截提示用户（见 resolveUnitPrice）
            BigDecimal unitPrice = resolveUnitPrice(ri);
            oi.setUnitPrice(unitPrice);
            oi.setTaxRate(BigDecimal.ZERO);
            oi.setDiscountRate(BigDecimal.ZERO);
            oi.setDiscountAmount(BigDecimal.ZERO);
            oi.setSourceBillType("采购申请单");
            oi.setSourceBillNo(request.getRequestNo());
            oi.setSourceLineNo(ri.getLineNo());
            oi.setSourceId(request.getRequestId());
            oi.setSourceItemId(ri.getRequestItemId());
            oi.setSourceType("采购申请单");
            oi.setDetailCloseStatus(0);
            oi.setTurnedInputQuantity(BigDecimal.ZERO);
            oi.setTurnedReturnQuantity(BigDecimal.ZERO);
            oi.setDeliveryDate(ri.getDemandDate());
            oi.setRemark(ri.getRowRemark());
            // 行金额必须随单价一起计算：否则会出现"单价有值、金额为0、订单总额为0"的数据不一致
            BigDecimal qty = oi.getQuantity() != null ? oi.getQuantity() : BigDecimal.ZERO;
            BigDecimal lineAmount = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            oi.setAmount(lineAmount);
            oi.setTaxAmount(BigDecimal.ZERO);
            oi.setNetAmount(lineAmount);
            totalAmount = totalAmount.add(lineAmount);
            totalNet = totalNet.add(lineAmount);
            orderItems.add(oi);
        }
        order.setTotalAmount(totalAmount);
        order.setTaxAmount(totalTax);
        order.setNetAmount(totalNet);

        purchaseOrderService.saveOrderWithItems(order, orderItems);
        // 免审批（设置中心-单据审批开关关闭）：转单生成的采购订单"提交即生效"。
        // 必须走 submit：它内部会按免审批规则自动通过；
        // 若改用 approve，免审批下 submitForApproval 已把单据置为"已生效"，随后的 super.approve
        // 会报"只有待审核状态可审核，当前状态: 已生效"（该接口在免审批场景下不可用）。
        PurchaseOrder saved = purchaseOrderService.getDetail(order.getOrderId());
        if (!approvalGateHelper.needApproval("PURCHASE_ORDER") && saved != null
                && !"已生效".equals(saved.getApprovalStatus())) {
            purchaseOrderService.submit(order.getOrderId(), "系统(免审批)");
            saved = purchaseOrderService.getDetail(order.getOrderId());
        }
        return saved != null ? saved : order;
    }

    /**
     * 解析下推采购订单明细的单价：
     * 1) 采购申请明细(PurchaseRequestItem)无单价字段，跳过；
     * 2) 取物料主档采购价 material.purchase_price；
     * 3) 取不到(为 null 或 0)则保持 0 并告警（不做编造），由设置中心参数 purchase.order.allow.zero.price 校验按既有逻辑拦截提示用户。
     */
    private BigDecimal resolveUnitPrice(PurchaseRequestItem ri) {
        if (ri.getMaterialId() != null) {
            Material m = materialService.getById(ri.getMaterialId());
            if (m != null && m.getPurchasePrice() != null && m.getPurchasePrice().compareTo(BigDecimal.ZERO) != 0) {
                return m.getPurchasePrice();
            }
        }
        log.warn("采购申请单明细物料[{} {}]未维护采购价(material.purchase_price)，下推采购订单单价为0，将按设置中心参数校验",
                ri.getMaterialCode(), ri.getMaterialName());
        return BigDecimal.ZERO;
    }

    /**
     * 取基础币种 ID（采购申请单无币种字段，下推采购订单时用它兜底）；取不到时回退 1 并告警。
     * 注意：库里存在多行 is_base_currency=1 的情况（实测有 4 行 CNY），
     * 因此必须按 currency_id 升序取第一条，否则 LIMIT 1 会随机命中，导致下推出来的币种不可预期。
     */
    private Long resolveBaseCurrencyId() {
        try {
            QueryWrapper<Currency> wrapper = new QueryWrapper<>();
            wrapper.eq("is_base_currency", 1).orderByAsc("currency_id").last("LIMIT 1");
            Currency base = currencyService.getOne(wrapper);
            if (base != null && base.getCurrencyId() != null) {
                return base.getCurrencyId();
            }
        } catch (Exception e) {
            log.warn("读取基础币种失败: {}", e.getMessage());
        }
        log.warn("未找到基础币种(is_base_currency=1)，下推采购订单币种回退为 1");
        return 1L;
    }

}
