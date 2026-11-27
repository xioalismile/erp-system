package com.erp.sample.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.BillApprovalAdapter;
import com.erp.common.bill.BillRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import com.erp.config.BusinessException;
import com.erp.config.SysParamValidator;
import com.erp.masterdata.entity.Bom;
import com.erp.masterdata.entity.BomItem;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.MaterialMapper;
import com.erp.masterdata.service.BomService;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.MaterialService;
import com.erp.inventory.entity.MaterialRequisition;
import com.erp.inventory.entity.MaterialRequisitionItem;
import com.erp.inventory.mapper.MaterialRequisitionMapper;
import com.erp.inventory.mapper.MaterialRequisitionItemMapper;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.entity.ProductionReceiptItem;
import com.erp.production.mapper.ProductionReceiptMapper;
import com.erp.production.mapper.ProductionReceiptItemMapper;
import com.erp.sample.entity.SampleOrder;
import com.erp.sample.entity.SampleOrderItem;
import com.erp.sample.mapper.SampleOrderItemMapper;
import com.erp.sample.mapper.SampleOrderMapper;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.common.util.SortHelper;
import com.erp.common.util.CustomFieldSearchHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 打样单 Service 实现
 * 文档5.9 单据状态流转: 草稿 → 待审核 → 已生效 → 打样中 → 已完成 → 已确认 → 已转BOM；已确认前可取消
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class SampleOrderServiceImpl extends AbstractBillService<SampleOrderMapper, SampleOrder> implements SampleOrderService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义全面推广）。
        // 键 = 前端筛选条件定义键（与单据明细报表引擎筛选键同键）；值 = 实体属性名
        // （registerProps 由 MyBatis-Plus 元数据解析成真实列名，防手写列名笔误）。
        // 客户/责任工程师/责任部门：按 ID 精确（前端下拉传主档 ID），Spec.column 写外键属性名。
        Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("sampleNo", new BillHeaderFilterSupport.Spec("sampleNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sampleName", new BillHeaderFilterSupport.Spec("sampleName", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sampleType", new BillHeaderFilterSupport.Spec("sampleType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sampleStage", new BillHeaderFilterSupport.Spec("sampleStage", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPerson", new BillHeaderFilterSupport.Spec("contactPerson", BillHeaderFilterSupport.Kind.TEXT));
        f.put("contactPhone", new BillHeaderFilterSupport.Spec("contactPhone", BillHeaderFilterSupport.Kind.TEXT));
        f.put("materialCode", new BillHeaderFilterSupport.Spec("materialCode", BillHeaderFilterSupport.Kind.TEXT));
        f.put("materialName", new BillHeaderFilterSupport.Spec("materialName", BillHeaderFilterSupport.Kind.TEXT));
        f.put("priority", new BillHeaderFilterSupport.Spec("priority", BillHeaderFilterSupport.Kind.TEXT));
        f.put("progress", new BillHeaderFilterSupport.Spec("progress", BillHeaderFilterSupport.Kind.TEXT));
        f.put("remark", new BillHeaderFilterSupport.Spec("remark", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（状态枚举 / 外键 ID）──
        f.put("status", new BillHeaderFilterSupport.Spec("status", BillHeaderFilterSupport.Kind.EQ_STRING));
        f.put("customerName", new BillHeaderFilterSupport.Spec("customerId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("engineerName", new BillHeaderFilterSupport.Spec("engineerId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("deptName", new BillHeaderFilterSupport.Spec("departmentId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 日期区间 ──
        f.put("demandDate", new BillHeaderFilterSupport.Spec("demandDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("planFinishDate", new BillHeaderFilterSupport.Spec("planFinishDate", BillHeaderFilterSupport.Kind.DATE));
        f.put("actualFinishDate", new BillHeaderFilterSupport.Spec("actualFinishDate", BillHeaderFilterSupport.Kind.DATE));
        // ── 数值区间 ──
        f.put("sampleQty", new BillHeaderFilterSupport.Spec("sampleQty", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("costAmount", new BillHeaderFilterSupport.Spec("costAmount", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("sampleOrder", SampleOrder.class, f);
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "actual_finish_date",
            "contact_person",
            "contact_phone",
            "cost_amount",
            "created_at",
            "created_by",
            "customer_id",
            "customer_name",
            "demand_date",
            "department_id",
            "engineer_id",
            "generated_bom_id",
            "is_bom_generated",
            "line_no",
            "material_code",
            "material_id",
            "material_name",
            "org_code",
            "plan_finish_date",
            "priority",
            "remark",
            "sample_id",
            "sample_name",
            "sample_no",
            "sample_progress",
            "sample_qty",
            "sample_stage",
            "sample_type",
            "source_bill_no",
            "specification",
            "status",
            "unit_id",
            "updated_at",
            "updated_by",
            "void_reason",
            "voided_at",
            "voided_by"
        );

    private final SampleOrderItemMapper itemMapper;
    private final MaterialMapper materialMapper;
    private final BomService bomService;
    private final MaterialRequisitionMapper materialRequisitionMapper;
    private final MaterialRequisitionItemMapper materialRequisitionItemMapper;
    private final ProductionReceiptMapper productionReceiptMapper;
    private final ProductionReceiptItemMapper productionReceiptItemMapper;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final CustomerService customerService;
    private final MaterialService materialService;
    /** 设置中心业务规则校验器（含税业务开关 / 默认计税方式 / 默认税率） */
    private final SysParamValidator sysParamValidator;

    /** 下游引用校验（统一状态机）：重置草稿/作废/删除前禁止存在已生效下游 */
    @Lazy
    @Autowired
    private BillRelationService billRelationService;

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 明细计税类型：1=含税价 2=不含税价（默认） 3=不计税 */
    private static final int TAX_TYPE_INCLUSIVE = 1;
    private static final int TAX_TYPE_EXCLUSIVE = 2;
    private static final int TAX_TYPE_NO_TAX = 3;

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "打样单"; }
    @Override protected String getApprovalBillType() { return "SAMPLE_ORDER"; }
    @Override protected String getBillNo(SampleOrder b) { return b.getSampleNo(); }
    @Override protected Object getId(SampleOrder b) { return b.getSampleId(); }
    @Override protected String getStatus(SampleOrder b) { return b.getStatus(); }
    @Override protected void setStatus(SampleOrder b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(SampleOrder b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(SampleOrder b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(SampleOrder b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(SampleOrder b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(SampleOrder b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(SampleOrder b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(SampleOrder b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(SampleOrder b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(SampleOrder b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(SampleOrder b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(SampleOrder b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(SampleOrder b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(SampleOrder b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(SampleOrder bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getSampleNo());
    }

    /**
     * 审核通过影响：原审核动作只做状态迁移（打样领料由开始打样触发、样品入库由完成打样触发、转BOM走手动下推），
     * 无库存/财务正向影响，故此处仅留审核日志；后续新增正向影响一律写在本钩子。
     */
    @Override
    public void onApproved(SampleOrder bill) {
        log.info("打样单审核: {} 审核人: {}", bill.getSampleNo(), bill.getApprovedBy());
    }

    /** 重置草稿影响：与 onApproved 严格镜像（当前无正向影响，无需冲回） */
    @Override
    public void onReverseApproved(SampleOrder bill) {
        log.info("打样单重置草稿回冲完成: {} 操作人: {}", bill.getSampleNo(), bill.getApprovedBy());
    }

    /** 作废影响：与重置草稿严格镜像 */
    @Override
    public void onVoided(SampleOrder bill) {
        onReverseApproved(bill);
    }

    /** 反作废影响（2026-11-09）：与 onVoided 严格镜像（当前无正向影响，仅留审核日志） */
    @Override
    public void onUnvoided(SampleOrder bill) {
        onApproved(bill);
    }


    /**
     * 由表头「计税方式」解析明细计税类型（口径对齐销售订单试点）。
     * 含税价=1、不含税价=2（默认）、不计税=3；兼容历史值 价内税→含税价、价外税→不含税价；
     * 设置中心「启用含税业务」总开关关闭时，一律按「不计税」。
     */
    private int resolveHeaderTaxType(SampleOrder sample) {
        if (!sysParamValidator.isTaxEnabled()) return TAX_TYPE_NO_TAX;
        String m = sample != null ? sample.getTaxCalcMethod() : null;
        if (m == null || m.isBlank()) m = sysParamValidator.getDefaultTaxMethod();
        if (m == null) return TAX_TYPE_EXCLUSIVE;
        String v = m.trim();
        if ("不计税".equals(v)) return TAX_TYPE_NO_TAX;
        if ("含税价".equals(v) || "价内税".equals(v)) return TAX_TYPE_INCLUSIVE;
        return TAX_TYPE_EXCLUSIVE;
    }

    private static BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    @Override
    @Transactional
    public void saveSampleWithItems(SampleOrder sample, List<SampleOrderItem> items) {
        boolean isNew = sample.getSampleId() == null;

        // 已生效不可直接修改（需重置草稿）
        if (!isNew) {
            SampleOrder existing = getById(sample.getSampleId());
            if (existing != null) {
                String status = existing.getStatus();
                if ("已生效".equals(status) || "打样中".equals(status) || "已完成".equals(status)
                        || "已确认".equals(status) || "已转BOM".equals(status)) {
                    throw new BusinessException("当前状态(" + status + ")不可直接修改，请先重置草稿或取消");
                }
                if ("已取消".equals(status)) {
                    throw new BusinessException("已取消的打样单不可修改");
                }
                // 已生成BOM后进度锁定：整单保存也不允许变更进度（防止绕过进度更新接口）
                if (existing.getIsBomGenerated() != null && existing.getIsBomGenerated() == 1
                        && !Objects.equals(existing.getProgress(), sample.getProgress())) {
                    throw new BusinessException("已经生成BOM表，请先删除BOM");
                }
            }
        }

        // 单号自动生成: SAM-yyyyMMdd-0001
        if (sample.getSampleNo() == null || sample.getSampleNo().isBlank()) {
            sample.setSampleNo(generateSampleNo());
        }

        // 默认值
        if (sample.getStatus() == null) sample.setStatus("草稿");
        // 新建草稿时同步 approval_status，避免该列留空（与全系统审批口径一致）
        if ("草稿".equals(sample.getStatus()) && sample.getApprovalStatus() == null) {
            sample.setApprovalStatus("草稿");
        }
        if (sample.getSampleType() == null) sample.setSampleType("新产品");
        if (sample.getSampleStage() == null) sample.setSampleStage("原样");
        if (sample.getPriority() == null) sample.setPriority("普通");
        if (sample.getSampleQty() == null) sample.setSampleQty(BigDecimal.ONE);
        if (sample.getCostAmount() == null) sample.setCostAmount(BigDecimal.ZERO);
        if (sample.getIsBomGenerated() == null) sample.setIsBomGenerated((short) 0);
        if (sample.getCreatedAt() == null) sample.setCreatedAt(LocalDateTime.now());
        if (sample.getUpdatedAt() == null) sample.setUpdatedAt(LocalDateTime.now());
        // createdBy/updatedBy 交由 AuditMetaObjectHandler 按当前登录人自动填充（无登录态时落 system）

        // 保存=草稿宽松：原"客户打样必须填写客户 / 打样数量必须大于0 / 交期校验 / 基本用量必须大于0"
        // 等业务校验不在保存时执行，统一挪到提交(submit)时校验

        // 保存宽松：丢弃未选择物料的空行（child_material_id 等列数据库 NOT NULL，无法落库）
        if (items != null) {
            List<SampleOrderItem> validItems = new ArrayList<>();
            for (SampleOrderItem it : items) {
                if (it != null && it.getChildMaterialId() != null) validItems.add(it);
            }
            items = validItems;
        }

        // 表头「计税方式」归一（总开关关闭→不计税；为空→取设置中心默认），并据此换算明细
        int headerTaxType = resolveHeaderTaxType(sample);
        // 重新汇总成本
        recalculateCost(sample, items, headerTaxType);

        if (isNew) {
            save(sample);
        } else {
            updateById(sample);
        }

        // 保存明细
        if (items != null && !items.isEmpty()) {
            // items 为 null 时不删除既有明细，避免未携带明细的更新把表体清空
            itemMapper.delete(new QueryWrapper<SampleOrderItem>().eq("sample_id", sample.getSampleId()));
            int lineNo = 1;
            for (SampleOrderItem item : items) {
                item.setItemId(null);
                item.setSampleId(sample.getSampleId());
                item.setLineNo(lineNo);
                calculateItemAmount(item, headerTaxType);
                if (item.getQtyType() == null) item.setQtyType("固定");
                if (item.getSupplyType() == null) item.setSupplyType("领料");
                if (item.getIsKeyPart() == null) item.setIsKeyPart((short) 0);
                if (item.getScrapRate() == null) item.setScrapRate(BigDecimal.ZERO);
                if (item.getCreatedAt() == null) item.setCreatedAt(LocalDateTime.now());
                if (item.getUpdatedAt() == null) item.setUpdatedAt(LocalDateTime.now());
                itemMapper.insert(item);
                lineNo += 1;
            }
        }

        log.info("打样单{}: {} 明细{}项 状态: {}",
                isNew ? "新增" : "编辑", sample.getSampleNo(),
                items != null ? items.size() : 0, sample.getStatus());
    }

    /**
     * 业务规则校验
     * - 客户打样必须填写客户
     * - 打样数量必须 > 0
     * - 需求交期 ≤ 计划完成日期
     */
    private void validateSample(SampleOrder sample, List<SampleOrderItem> items) {
        // 客户打样必须填写客户（样品类型为来料打样/改进产品且无客户时，判断为客户打样）
        if (sample.getCustomerId() == null) {
            // 仅内部研发允许无客户
            if (!"内部研发".equals(sample.getSampleType())) {
                throw new BusinessException("客户打样必须填写客户");
            }
        }
        // 打样数量必须 > 0
        if (sample.getSampleQty() == null || sample.getSampleQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("打样数量必须大于0");
        }
        // 需求交期 ≤ 计划完成日期
        if (sample.getDemandDate() != null && sample.getPlanFinishDate() != null) {
            if (sample.getDemandDate().isAfter(sample.getPlanFinishDate())) {
                throw new BusinessException("计划完成日期不能早于需求交期");
            }
        }
        // 子件物料数量必须 > 0
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                SampleOrderItem item = items.get(i);
                if (item.getBaseQty() == null || item.getBaseQty().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("第" + (i + 1) + "行基本用量必须大于0");
                }
            }
        }
    }

    /**
     * 表体金额与含税/不含税价换算（口径对齐销售订单试点）：
     * 表头「计税方式」决定「单价」是含税价还是不含税价 → 换算另一列（落库 price_with_tax / price_without_tax）；
     * 金额一律按「不含税」口径参与（金额 = 用量 × 不含税单价）；不计税时含税价=不含税价=单价。
     * 本页明细无「税率%」列，故取设置中心「税率」默认值。
     */
    private void calculateItemAmount(SampleOrderItem item, int taxType) {
        BigDecimal qty = nvl(item.getBaseQty());
        BigDecimal price = nvl(item.getUnitPrice());
        BigDecimal taxRate = nvl(sysParamValidator.getTaxRate());
        item.setBaseQty(qty);
        item.setUnitPrice(price);

        boolean noTax = taxType == TAX_TYPE_NO_TAX;
        boolean inclusive = taxType == TAX_TYPE_INCLUSIVE;
        BigDecimal factor = BigDecimal.ONE.add(taxRate.divide(HUNDRED, 6, RoundingMode.HALF_UP));

        BigDecimal priceWithTax;
        BigDecimal priceWithoutTax;
        if (inclusive) {
            priceWithTax = price;
            priceWithoutTax = factor.compareTo(BigDecimal.ZERO) != 0
                    ? price.divide(factor, 6, RoundingMode.HALF_UP) : price;
        } else if (noTax) {
            priceWithTax = price;
            priceWithoutTax = price;
        } else {
            priceWithoutTax = price;
            priceWithTax = price.multiply(factor).setScale(6, RoundingMode.HALF_UP);
        }
        item.setPriceWithTax(priceWithTax.setScale(6, RoundingMode.HALF_UP));
        item.setPriceWithoutTax(priceWithoutTax.setScale(6, RoundingMode.HALF_UP));

        // 折前金额一律按「不含税」口径：金额 = 用量 × 不含税单价
        BigDecimal amount = inclusive
                ? price.multiply(qty).divide(factor, SCALE, RoundingMode.HALF_UP)
                : price.multiply(qty).setScale(SCALE, RoundingMode.HALF_UP);
        item.setAmount(amount);
        // 价税合计 = 含税价 × 数量（无折扣，2026-11-23 补齐）
        item.setNetAmount(item.getPriceWithTax().multiply(qty).setScale(SCALE, RoundingMode.HALF_UP));
    }

    /** 汇总样品成本（表体金额合计） */
    private void recalculateCost(SampleOrder sample, List<SampleOrderItem> items, int taxType) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        if (items != null) {
            for (SampleOrderItem item : items) {
                calculateItemAmount(item, taxType);
                total = total.add(nvl(item.getAmount()));
                totalNet = totalNet.add(nvl(item.getNetAmount()));
            }
        }
        sample.setCostAmount(total.setScale(SCALE, RoundingMode.HALF_UP));
        sample.setNetAmount(totalNet.setScale(SCALE, RoundingMode.HALF_UP));
    }

    /** 旧签名（老调用方零影响）：委托新重载，不附带表头字段过滤 */
    @Override
    public PageResult<SampleOrder> pageQuery(int current, int size, String keyword, String status, String customSearch, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder) {
        return pageQuery(current, size, keyword, status, customSearch, scopeCtx, sortBy, sortOrder, null);
    }

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）：
     * 白名单 + 参数化，非法/空白/白名单外入参在支持类内部忽略，不阻断列表查询。
     */
    @Override
    public PageResult<SampleOrder> pageQuery(int current, int size, String keyword, String status, String customSearch, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, Map<String, String> headerFilters) {
        QueryWrapper<SampleOrder> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("sample_no", keyword)
                    .or().like("sample_name", keyword)
                    .or().like("customer_name", keyword)
                    .or().like("material_name", keyword));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        // 表头字段过滤（2026-10-14 筛选条件自定义）：f_* → 白名单翻译（非法/空白/白名单外一律忽略，不阻断列表查询）
        BillHeaderFilterSupport.apply(wrapper, "sampleOrder", headerFilters);
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "sample_order", customSearch, CustomFieldSearchHelper.idColumnOf("sample_order"));
        // 数据权限（2026-09-06 补齐，与 dashboard 一致）
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null);
        if (!SortHelper.applyToWrapper(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, SampleOrder.class);
        Page<SampleOrder> page = new Page<>(current, size);
        Page<SampleOrder> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SampleOrder getDetail(Long id) {
        SampleOrder sample = getById(id);
        if (sample == null) throw new BusinessException("打样单不存在");
        List<SampleOrderItem> items = itemMapper.selectList(
                new QueryWrapper<SampleOrderItem>().eq("sample_id", id).orderByAsc("line_no"));
        sample.setItems(items);
        return sample;
    }

    /** 提交审核：状态校验/严格校验/审批开关/状态迁移全部由统一状态机执行 */
    @Override
    @Transactional
    public void submit(Long id, String operator) {
        SampleOrder sample = getById(id);
        if (sample == null) throw new BusinessException("打样单不存在");
        String submitter = (operator != null && !operator.isBlank()) ? operator : BillApprovalAdapter.currentSubmitter();
        super.submitForApproval(sample, submitter);
    }

    /** 提交前的严格业务校验（统一状态机在提交/草稿直审时调用） */
    @Override
    protected void validateStrictSubmit(SampleOrder sample) {
        List<SampleOrderItem> items = itemMapper.selectList(
                new QueryWrapper<SampleOrderItem>().eq("sample_id", sample.getSampleId()));
        if (items == null || items.isEmpty()) {
            throw new BusinessException("没有明细，请至少添加一行明细后再提交");
        }

        // 提交=严格校验：草稿保存时放宽的"客户必填 / 打样数量>0 / 交期 / 基本用量>0"统一在此校验
        validateSample(sample, items);

        if (sample.getCustomerId() != null && customerService.getById(sample.getCustomerId()) == null) {
            throw new BusinessException("关联客户不存在(customerId=" + sample.getCustomerId() + ")，请重新选择客户");
        }
        if (sample.getMaterialId() != null) {
            Material m = materialService.getById(sample.getMaterialId());
            if (m == null) {
                throw new BusinessException("关联打样物料不存在(materialId=" + sample.getMaterialId() + ")，请重新选择物料");
            }
        }
    }

    /**
     * 审核：审批引擎防护 + 状态迁移全部由统一状态机执行，业务影响在 onApproved 钩子。
     * 必须覆盖 Object 签名：审批引擎回调（基类 onApprovalApproved）按 Object 签名调用，否则防绕过校验失效。
     */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask(getApprovalBillType(), (Long) id) != null) {
            throw new BusinessException("打样单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 重置草稿：保留"仅已生效可重置"守卫（打样中/已完成/已确认/已转BOM 均为执行态，不可回退），迁移与下游校验由基类执行 */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        SampleOrder sample = getById(id);
        if (sample == null) throw new BusinessException("打样单不存在");
        if (!"已生效".equals(sample.getStatus())) {
            throw new BusinessException("只有已生效状态可重置草稿，当前: " + sample.getStatus());
        }
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        update(new UpdateWrapper<SampleOrder>().eq("sample_id", id)
                .set("approved_by", null).set("approved_at", null));
    }

    @Override
    @Transactional
    public void startSample(Long id, String operator) {
        SampleOrder sample = getById(id);
        if (sample == null) throw new BusinessException("打样单不存在");
        if (!"已生效".equals(sample.getStatus())) {
            throw new BusinessException("只有已生效状态可开始打样，当前: " + sample.getStatus());
        }
        sample.setStatus("打样中");
        sample.setUpdatedBy(operator);
        sample.setUpdatedAt(LocalDateTime.now());
        updateById(sample);
        // 自动生成打样领料单（基于打样单子件清单）
        try {
            generateRequisitionForSample(sample, operator);
        } catch (Exception e) {
            log.warn("打样单 {} 自动生成领料单失败: {}", sample.getSampleNo(), e.getMessage());
            // 不阻断状态变更，仅记录日志
        }
        log.info("打样单开始打样: {} 操作人: {}", sample.getSampleNo(), operator);
    }

    /**
     * 打样开始时自动生成领料单
     * 基于打样单的样品BOM子件清单，生成领料单（草稿状态）
     */
    private void generateRequisitionForSample(SampleOrder sample, String operator) {
        List<SampleOrderItem> items = itemMapper.selectList(
                new QueryWrapper<SampleOrderItem>().eq("sample_id", sample.getSampleId()));
        if (items == null || items.isEmpty()) {
            log.info("打样单 {} 无子件清单, 跳过生成领料单", sample.getSampleNo());
            return;
        }

        // 检查是否已生成过领料单（防重复）
        Long existCount = materialRequisitionMapper.selectCount(
                new QueryWrapper<MaterialRequisition>()
                        .eq("source_bill_no", sample.getSampleNo()));
        if (existCount != null && existCount > 0) {
            log.info("打样单 {} 已生成过领料单, 跳过", sample.getSampleNo());
            return;
        }

        // 创建领料单表头
        MaterialRequisition req = new MaterialRequisition();
        req.setRequisitionNo("SAMPICK-" + sample.getSampleNo() + "-" + System.currentTimeMillis() % 10000);
        req.setBillType("领料");
        req.setRequisitionDate(LocalDate.now());
        req.setWorkOrderNo(sample.getSampleNo());
        req.setDepartmentId(sample.getDepartmentId());
        req.setEmployeeId(sample.getEngineerId());
        req.setSourceBillType("打样单");
        req.setSourceBillNo(sample.getSampleNo());
        req.setRemark("由打样单 " + sample.getSampleNo() + " 开始打样时自动生成");
        req.setStatus("草稿");
        req.setApprovalStatus("草稿");
        req.setCreatedBy(operator);
        req.setUpdatedBy(operator);

        // 计算总金额
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<MaterialRequisitionItem> reqItems = new ArrayList<>();
        int lineNo = 10;
        for (SampleOrderItem si : items) {
            if (si.getChildMaterialId() == null) continue;
            MaterialRequisitionItem reqItem = new MaterialRequisitionItem();
            reqItem.setLineNo(lineNo);
            reqItem.setMaterialId(si.getChildMaterialId());
            reqItem.setMaterialCode(si.getChildMaterialCode());
            reqItem.setMaterialName(si.getChildMaterialName());
            reqItem.setSpecification(si.getSpecification());
            reqItem.setUnitId(si.getUnitId());
            // 领料数量 = 基本用量 × 打样数量（按样品数量倍数领料）
            BigDecimal baseQty = si.getBaseQty() != null ? si.getBaseQty() : BigDecimal.ONE;
            BigDecimal sampleQty = sample.getSampleQty() != null ? sample.getSampleQty() : BigDecimal.ONE;
            reqItem.setQuantity(baseQty.multiply(sampleQty).setScale(4, RoundingMode.HALF_UP));
            reqItem.setIssuedQuantity(BigDecimal.ZERO);
            reqItem.setUnitPrice(si.getUnitPrice() != null ? si.getUnitPrice() : BigDecimal.ZERO);
            BigDecimal amount = reqItem.getQuantity().multiply(reqItem.getUnitPrice()).setScale(SCALE, RoundingMode.HALF_UP);
            reqItem.setAmount(amount);
            totalAmount = totalAmount.add(amount);
            reqItem.setSourceBillType("打样单");
            reqItem.setSourceBillNo(sample.getSampleNo());
            reqItem.setSourceLineNo(si.getLineNo());
            reqItem.setCreatedBy(operator);
            reqItem.setUpdatedBy(operator);
            reqItems.add(reqItem);
            lineNo += 10;
        }
        req.setTotalAmount(totalAmount);

        materialRequisitionMapper.insert(req);
        for (MaterialRequisitionItem item : reqItems) {
            item.setRequisitionId(req.getRequisitionId());
            materialRequisitionItemMapper.insert(item);
        }
        log.info("打样单 {} 自动生成领料单: {}, 共 {} 条明细", sample.getSampleNo(), req.getRequisitionNo(), reqItems.size());
    }

    @Override
    @Transactional
    public void completeSample(Long id, String operator) {
        SampleOrder sample = getById(id);
        if (sample == null) throw new BusinessException("打样单不存在");
        if (!"打样中".equals(sample.getStatus())) {
            throw new BusinessException("只有打样中状态可完成打样，当前: " + sample.getStatus());
        }
        sample.setStatus("已完成");
        sample.setActualFinishDate(LocalDate.now());
        sample.setUpdatedBy(operator);
        sample.setUpdatedAt(LocalDateTime.now());
        updateById(sample);
        // 自动生成样品入库单（将打样成品入库）
        try {
            generateReceiptForSample(sample, operator);
        } catch (Exception e) {
            log.warn("打样单 {} 自动生成入库单失败: {}", sample.getSampleNo(), e.getMessage());
        }
        log.info("打样单完成: {} 操作人: {}", sample.getSampleNo(), operator);
    }

    /**
     * 打样完成时自动生成样品入库单
     * 将打样成品按打样数量入库
     */
    private void generateReceiptForSample(SampleOrder sample, String operator) {
        if (sample.getMaterialId() == null) {
            log.info("打样单 {} 未关联成品物料, 跳过生成入库单", sample.getSampleNo());
            return;
        }

        // 检查是否已生成过入库单（防重复）
        Long existCount = productionReceiptMapper.selectCount(
                new QueryWrapper<ProductionReceipt>()
                        .eq("source_bill_no", sample.getSampleNo()));
        if (existCount != null && existCount > 0) {
            log.info("打样单 {} 已生成过入库单, 跳过", sample.getSampleNo());
            return;
        }

        // 创建入库单表头
        ProductionReceipt receipt = new ProductionReceipt();
        receipt.setReceiptNo("SAMIN-" + sample.getSampleNo() + "-" + System.currentTimeMillis() % 10000);
        receipt.setReceiptDate(LocalDate.now());
        receipt.setProductionOrderNo(sample.getSampleNo());
        receipt.setDepartmentId(sample.getDepartmentId());
        receipt.setTotalQuantity(sample.getSampleQty());
        receipt.setQualifiedQuantity(sample.getSampleQty());
        receipt.setRejectedQuantity(BigDecimal.ZERO);
        receipt.setSourceBillType("打样单");
        receipt.setSourceBillNo(sample.getSampleNo());
        receipt.setRemark("由打样单 " + sample.getSampleNo() + " 完成时自动生成");
        receipt.setStatus("草稿");
        receipt.setApprovalStatus("草稿");
        receipt.setCreatedBy(operator);
        receipt.setUpdatedBy(operator);

        // 创建入库明细（成品入库）
        ProductionReceiptItem item = new ProductionReceiptItem();
        item.setLineNo(10);
        item.setMaterialId(sample.getMaterialId());
        item.setMaterialCode(sample.getMaterialCode());
        item.setMaterialName(sample.getMaterialName());
        item.setSpecification(sample.getSpecification());
        item.setUnitId(sample.getUnitId());
        item.setQuantity(sample.getSampleQty());
        item.setQualifiedQuantity(sample.getSampleQty());
        item.setRejectedQuantity(BigDecimal.ZERO);
        item.setUnitPrice(BigDecimal.ZERO);
        item.setAmount(BigDecimal.ZERO);
        item.setCreatedBy(operator);
        item.setUpdatedBy(operator);

        List<ProductionReceiptItem> items = new ArrayList<>();
        items.add(item);
        receipt.setItems(items);

        productionReceiptMapper.insert(receipt);
        item.setReceiptId(receipt.getReceiptId());
        productionReceiptItemMapper.insert(item);

        log.info("打样单 {} 自动生成样品入库单: {}", sample.getSampleNo(), receipt.getReceiptNo());
    }

    @Override
    @Transactional
    public void confirmSample(Long id, String operator) {
        SampleOrder sample = getById(id);
        if (sample == null) throw new BusinessException("打样单不存在");
        if (!"已完成".equals(sample.getStatus())) {
            throw new BusinessException("只有已完成状态可确认样品，当前: " + sample.getStatus());
        }
        sample.setStatus("已确认");
        sample.setUpdatedBy(operator);
        sample.setUpdatedAt(LocalDateTime.now());
        updateById(sample);
        log.info("打样单确认样品合格: {} 操作人: {}", sample.getSampleNo(), operator);
    }

    @Override
    @Transactional
    public void cancel(Long id, String operator, String reason) {
        SampleOrder sample = getById(id);
        if (sample == null) throw new BusinessException("打样单不存在");
        String status = sample.getStatus();
        if ("已转BOM".equals(status)) {
            throw new BusinessException("已转BOM的打样单不可取消");
        }
        if ("已取消".equals(status)) {
            throw new BusinessException("打样单已是已取消状态");
        }
        sample.setStatus("已取消");
        sample.setApprovalStatus("已作废");
        sample.setUpdatedBy(operator);
        sample.setUpdatedAt(LocalDateTime.now());
        if (reason != null && !reason.isBlank()) {
            String existing = sample.getRemark();
            sample.setRemark((existing == null ? "" : existing + " | ") + "取消原因: " + reason);
        }
        updateById(sample);
        log.info("打样单取消: {} 操作人: {} 原因: {}", sample.getSampleNo(), operator, reason);
    }

    /**
     * 核心业务：打样转BOM（手动入口）
     * 业务规则：
     * 1. 只有"已确认"状态可转BOM
     * 2. 已转BOM的打样单不可重复转
     * 3. 若父项物料不存在则自动创建物料档案
     * 4. 以打样单表头物料为父项，表体子件清单为BOM子项
     * 5. BOM版本默认V1.0，状态默认草稿
     * 6. 回写打样单 is_bom_generated=1, generated_bom_id, status=已转BOM
     */
    @Override
    @Transactional
    public Bom convertToBom(Long id, String operator) {
        SampleOrder sample = getById(id);
        if (sample == null) throw new BusinessException("打样单不存在");

        // 1. 状态校验：只有"已确认"可转BOM
        if (!"已确认".equals(sample.getStatus())) {
            throw new BusinessException("只有已确认状态的打样单可转BOM，当前: " + sample.getStatus());
        }
        // 2. 防重复转BOM
        if (sample.getIsBomGenerated() != null && sample.getIsBomGenerated() == 1) {
            throw new BusinessException("该打样单已转BOM（BOM ID: " + sample.getGeneratedBomId() + "），不可重复转");
        }

        return doConvertToBom(sample, operator, false);
    }

    /**
     * 转BOM共用实现（手动入口与进度自动推送入口共用）
     *
     * @param autoMode true=进度完成自动推送（不打断原状态流转，单据状态保持不变，仅回写BOM关联标记）
     *                 false=手动转BOM（单据状态流转为已转BOM）
     */
    private Bom doConvertToBom(SampleOrder sample, String operator, boolean autoMode) {
        // 3. 确定父项物料：若打样单已指定物料则直接使用；否则自动创建物料档案
        Long parentMaterialId = sample.getMaterialId();
        String parentMaterialCode = sample.getMaterialCode();
        String parentMaterialName = sample.getMaterialName();

        if (parentMaterialId == null) {
            // 自动创建父项物料
            Material newMaterial = new Material();
            // 若无物料编码，用打样单号生成
            if (parentMaterialCode == null || parentMaterialCode.isBlank()) {
                parentMaterialCode = "SAMPLE-" + sample.getSampleNo();
            }
            if (parentMaterialName == null || parentMaterialName.isBlank()) {
                parentMaterialName = sample.getSampleName();
            }
            // 检查编码是否已存在
            Material existing = materialMapper.selectOne(
                    new QueryWrapper<Material>().eq("material_code", parentMaterialCode).last("LIMIT 1"));
            if (existing != null) {
                parentMaterialId = existing.getMaterialId();
                log.info("打样转BOM: 父项物料已存在, 复用 materialId={}", parentMaterialId);
            } else {
                newMaterial.setMaterialCode(parentMaterialCode);
                newMaterial.setMaterialName(parentMaterialName);
                newMaterial.setFullName(sample.getSampleName());
                newMaterial.setMaterialType("自制件");
                if (sample.getSpecification() != null) newMaterial.setModel(sample.getSpecification());
                if (sample.getUnitId() != null) {
                    newMaterial.setBaseUnitId(sample.getUnitId());
                    newMaterial.setStockUnitId(sample.getUnitId());
                    newMaterial.setProductionUnitId(sample.getUnitId());
                }
                newMaterial.setUnitConversion(new BigDecimal("1.000000"));
                newMaterial.setIsLotControl((short) 0);
                newMaterial.setIsSnControl((short) 0);
                newMaterial.setIsBinControl((short) 0);
                newMaterial.setIsQcRequired((short) 0);
                newMaterial.setIsExpiryControl((short) 0);
                materialMapper.insert(newMaterial);
                parentMaterialId = newMaterial.getMaterialId();
                log.info("打样转BOM: 自动创建父项物料 materialId={} code={}", parentMaterialId, parentMaterialCode);
            }
        } else {
            // 已有物料，校验存在性
            Material mat = materialMapper.selectById(parentMaterialId);
            if (mat == null) {
                throw new BusinessException("打样单关联的父项物料不存在, materialId=" + parentMaterialId);
            }
            if (parentMaterialCode == null) parentMaterialCode = mat.getMaterialCode();
            if (parentMaterialName == null) parentMaterialName = mat.getMaterialName();
        }

        // 4. 构造 BOM 对象
        Bom bom = new Bom();
        bom.setBomCode(bomService.generateBomCode());
        bom.setBomName(sample.getSampleName() + "-BOM");
        bom.setBomVersion("V1.0");
        bom.setMaterialId(parentMaterialId);
        bom.setMaterialCode(parentMaterialCode);
        bom.setMaterialName(parentMaterialName);
        if (sample.getUnitId() != null) bom.setUnitId(sample.getUnitId());
        bom.setQuantity(BigDecimal.ONE);
        bom.setBomType("标准BOM");
        bom.setIsPrimary((short) 1);
        bom.setIsEnabled((short) 1);
        bom.setEffectiveDate(LocalDate.now());
        bom.setStatus("草稿");
        bom.setRemark(autoMode
                ? "由打样单 " + sample.getSampleNo() + " 进度完成自动推送生成"
                : "由打样单 " + sample.getSampleNo() + " 转化生成");
        bom.setCreatedBy(operator);
        bom.setUpdatedBy(operator);

        // 5. 构造 BOM 子项（从打样单表体转化）
        List<SampleOrderItem> sampleItems = itemMapper.selectList(
                new QueryWrapper<SampleOrderItem>().eq("sample_id", sample.getSampleId()).orderByAsc("line_no"));
        List<BomItem> bomItems = new ArrayList<>();
        if (sampleItems != null && !sampleItems.isEmpty()) {
            int lineNo = 1;
            for (SampleOrderItem sItem : sampleItems) {
                BomItem bItem = new BomItem();
                bItem.setLineNo(lineNo);
                bItem.setMaterialId(sItem.getChildMaterialId());
                bItem.setMaterialCode(sItem.getChildMaterialCode());
                bItem.setMaterialName(sItem.getChildMaterialName());
                bItem.setSpecification(sItem.getSpecification());
                bItem.setUnitId(sItem.getUnitId());
                bItem.setQuantity(sItem.getBaseQty());
                bItem.setQtyType(sItem.getQtyType() != null ? sItem.getQtyType() : "固定");
                bItem.setScrapRate(sItem.getScrapRate() != null ? sItem.getScrapRate() : BigDecimal.ZERO);
                bItem.setIsKeyPart(sItem.getIsKeyPart() != null ? sItem.getIsKeyPart() : (short) 0);
                bItem.setSupplyType(sItem.getSupplyType() != null ? sItem.getSupplyType() : "领料");
                bItem.setRemark(sItem.getRemark());
                bItem.setCreatedBy(operator);
                bItem.setUpdatedBy(operator);
                bomItems.add(bItem);
                lineNo += 1;
            }
        }
        bom.setItems(bomItems);

        // 6. 调用 BOM 服务保存
        bomService.saveBomWithItems(bom, bomItems);

        // 7. 回写打样单：记录BOM关联；手动模式额外流转状态为已转BOM，自动模式保持原状态不打断
        UpdateWrapper<SampleOrder> uw = new UpdateWrapper<>();
        uw.eq("sample_id", sample.getSampleId())
                .set("is_bom_generated", 1)
                .set("generated_bom_id", bom.getBomId())
                .set("updated_by", operator)
                .set("updated_at", LocalDateTime.now());
        if (!autoMode) {
            uw.set("status", "已转BOM");
        }
        update(uw);

        log.info("打样单转BOM成功: sampleNo={} bomId={} bomCode={} 子件{}项 autoMode={}",
                sample.getSampleNo(), bom.getBomId(), bom.getBomCode(), bomItems.size(), autoMode);
        return bom;
    }

    @Override
    public String generateSampleNo() {
        String docType = "SAMPLE_ORDER";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "SAM-" + dateStr + "-";
            QueryWrapper<SampleOrder> wrapper = new QueryWrapper<>();
            wrapper.likeRight("sample_no", prefix).orderByDesc("sample_no").last("LIMIT 1");
            SampleOrder last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getSampleNo() != null) {
                try {
                    seq = Integer.parseInt(last.getSampleNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    /**
     * 更新打样进度
     * 业务规则：
     * 1. 已生成BOM的打样单进度锁定，提示先删除BOM
     * 2. 状态为已生效（已生效）且进度改为"已完成"时，自动推送生成一张草稿状态的BOM
     */
    @Override
    @Transactional
    public String updateProgress(Long id, String progress, String operator) {
        SampleOrder sample = getById(id);
        if (sample == null) {
            throw new BusinessException("打样单不存在");
        }
        // 已生成BOM：进度锁定，需先删除BOM（删除BOM会自动解锁）
        if (sample.getIsBomGenerated() != null && sample.getIsBomGenerated() == 1) {
            throw new BusinessException("已经生成BOM表，请先删除BOM");
        }
        UpdateWrapper<SampleOrder> wrapper = new UpdateWrapper<>();
        wrapper.eq("sample_id", id);
        wrapper.set("sample_progress", progress);
        wrapper.set("updated_by", operator);
        wrapper.set("updated_at", LocalDateTime.now());
        update(wrapper);
        log.info("打样单进度更新: sampleNo={}, progress={}, operator={}", sample.getSampleNo(), progress, operator);

        // 状态已生效（已生效）且进度=已完成：自动推送生成BOM草稿（失败则与进度更新一起回滚）
        if ("已完成".equals(progress) && "已生效".equals(sample.getStatus())) {
            Bom bom = doConvertToBom(sample, operator, true);
            return "进度已更新，已自动生成BOM草稿：" + bom.getBomCode();
        }
        return "进度已更新";
    }

    /** 驳回：状态校验/审批引擎防护由统一状态机执行 */
    @Override
    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
    }

    /** 作废：状态校验/下游校验/作废前状态记录由统一状态机执行，冲回在 onVoided 钩子 */
    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        super.voidBill(id, operator, reason);
    }

    /** 反作废：恢复作废前状态，不重复释放正向影响 */
    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }

    /** 结案：状态校验/结案字段（含结案人、时间、原因）由统一状态机执行 */
    @Override
    @Transactional
    public void closeOrder(Long id, String operator) {
        super.close(id, operator, "手动结案");
    }

    /** 反结案：状态校验/结案字段由统一状态机执行 */
    @Override
    @Transactional
    public void reopenOrder(Long id, String operator) {
        super.reopen(id, operator);
    }
}
