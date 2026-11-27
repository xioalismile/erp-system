package com.erp.sale.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.BillStatusUtils;
import com.erp.common.query.ListQuerySupport;
import com.erp.common.util.CustomFieldSearchHelper;
import com.erp.common.util.SortHelper;
import com.erp.config.BusinessException;
import com.erp.config.SysParamValidator;
import com.erp.customfield.service.CustomFieldService;
import com.erp.inventory.entity.OtherIssue;
import com.erp.inventory.entity.OtherIssueItem;
import com.erp.inventory.entity.OtherReceipt;
import com.erp.inventory.entity.OtherReceiptItem;
import com.erp.inventory.service.OtherIssueService;
import com.erp.inventory.service.OtherReceiptService;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.MaterialService;
import com.erp.sale.entity.*;
import com.erp.sale.mapper.*;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 售后维修实现。
 *
 * 库存口径：全部通过「其他入库单/其他出库单」落地
 *  - 收件入库：维修仓，批次=维修单号，单价 0（代管件）
 *  - 配件领用：备件仓，按实际出库成本
 *  - 返还客户：维修仓，数量 ≤ 已收件 − 已返还
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaleRepairServiceImpl extends AbstractBillService<SaleRepairMapper, SaleRepair>
        implements SaleRepairService {

    private static final String ST_DRAFT = "草稿";
    private static final String ST_ACCEPTED = "已受理";
    private static final String ST_REPAIRING = "维修中";
    private static final String ST_DONE = "维修完成";
    private static final String ST_RETURNED = "已返还";

    private static final Set<String> SORTABLE = Set.of(
            "repair_no", "repair_date", "customer_id", "warranty_status", "status",
            "approval_status", "total_fee", "approved_at", "created_at", "created_by");

    private final SaleRepairItemMapper itemMapper;
    private final SaleRepairPartMapper partMapper;
    private final MaterialService materialService;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final SysParamValidator sysParamValidator;
    private final JdbcTemplate jdbcTemplate;
    // 自定义字段值（表头 sale_repair / 维修明细 sale_repair_item / 配件 sale_repair_part）与数据版本记录
    private final CustomFieldService customFieldService;
    private final SaleBillVersionRecorder saleBillVersionRecorder;

    @Lazy @Autowired private OtherReceiptService otherReceiptService;
    @Lazy @Autowired private OtherIssueService otherIssueService;
    @Lazy @Autowired private SaleExchangeService saleExchangeService;
    @Lazy @Autowired private SaleReturnService saleReturnService;
    @Lazy @Autowired private com.erp.finance.service.FinanceService financeService;

    // ==================== 基础 ====================

    @Override protected String getBillTypeLabel() { return "售后维修单"; }
    @Override protected String getApprovalBillType() { return "SALE_REPAIR"; }
    @Override protected String getBillNo(SaleRepair b) { return b.getRepairNo(); }
    @Override protected Object getId(SaleRepair b) { return b.getRepairId(); }
    @Override protected String getStatus(SaleRepair b) { return b.getStatus(); }
    @Override protected void setStatus(SaleRepair b, String s) { b.setStatus(s); }
    @Override protected Integer getCloseStatus(SaleRepair b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(SaleRepair b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(SaleRepair b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(SaleRepair b, LocalDateTime t) { b.setApprovedAt(t); }
    @Override protected void setApprovalStatus(SaleRepair b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(SaleRepair b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(SaleRepair b, LocalDateTime t) { b.setClosedAt(t); }
    @Override protected void setClosedReason(SaleRepair b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(SaleRepair b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(SaleRepair b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(SaleRepair b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(SaleRepair b, LocalDateTime t) { b.setVoidedAt(t); }
    @Override protected void setVoidReason(SaleRepair b, String v) { b.setVoidReason(v); }

    // ==================== 查询 ====================

    @Override
    public PageResult<SaleRepair> pageQuery(int current, int size, String keyword, String status,
                                            String startDate, String endDate, String customerId,
                                            com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx,
                                            String sortBy, String sortOrder, String customSearch) {
        QueryWrapper<SaleRepair> w = new QueryWrapper<>();
        if (!ListQuerySupport.applySort(w, sortBy, sortOrder, SORTABLE)) {
            SortHelper.orderByNewest(w, SaleRepair.class);
        }
        if (status != null && !status.isBlank()) w.eq("status", status);
        ListQuerySupport.applyKeywordGroup(w, keyword, "repair_no", "source_bill_no", "customer_name", "repair_result");
        ListQuerySupport.applyDateRange(w, "repair_date", startDate, endDate);
        ListQuerySupport.applyId(w, "customer_id", customerId);
        if (scopeCtx != null) scopeCtx.applyTo(w, "created_by");
        CustomFieldSearchHelper.apply(w, "sale_repair", customSearch, "repair_id");
        Page<SaleRepair> page = page(new Page<>(current, size), w);
        return PageResult.of(page.getRecords(), page.getTotal(), current, size);
    }

    @Override
    public SaleRepair getDetail(Long id) {
        SaleRepair bill = getById(id);
        if (bill == null) throw new BusinessException("售后维修单不存在");
        bill.setItems(itemMapper.selectList(new QueryWrapper<SaleRepairItem>()
                .eq("repair_id", id).orderByAsc("line_no")));
        bill.setParts(partMapper.selectList(new QueryWrapper<SaleRepairPart>()
                .eq("repair_id", id).orderByAsc("line_no")));
        fillCustomFieldValues(bill);
        return bill;
    }

    /** 详情回填自定义字段值：表头 + 维修明细行 + 配件行（旧库/未配置字段时返回空 Map，不报错） */
    private void fillCustomFieldValues(SaleRepair bill) {
        if (bill == null || bill.getRepairId() == null) return;
        try {
            bill.setCustomFieldValues(customFieldService.getFieldValues("sale_repair", bill.getRepairId()));
            if (bill.getItems() != null) {
                for (SaleRepairItem it : bill.getItems()) {
                    if (it != null && it.getRepairItemId() != null) {
                        it.setCustomFieldValues(customFieldService.getFieldValues(
                                "sale_repair_item", it.getRepairItemId()));
                    }
                }
            }
            if (bill.getParts() != null) {
                for (SaleRepairPart p : bill.getParts()) {
                    if (p != null && p.getPartId() != null) {
                        p.setCustomFieldValues(customFieldService.getFieldValues(
                                "sale_repair_part", p.getPartId()));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("回填售后维修单自定义字段失败 id={}: {}", bill.getRepairId(), e.getMessage());
        }
    }

    // ==================== 保存 ====================

    @Override
    @Transactional
    public SaleRepair saveRepairWithItems(SaleRepair bill, List<SaleRepairItem> items, List<SaleRepairPart> parts) {
        boolean isNew = bill.getRepairId() == null;
        // 可全量编辑：草稿 / 已驳回；已受理 / 维修中 只允许维护配件与备注（维修产品与收件数量已发生业务，不允许重写）
        boolean editable = true;
        boolean partsOnly = false;
        SaleRepair oldSnapshot = null;
        if (!isNew) {
            oldSnapshot = getById(bill.getRepairId());
            SaleRepair db = oldSnapshot;
            if (db == null) throw new BusinessException("售后维修单不存在");
            String st = db.getStatus() == null ? "" : db.getStatus();
            editable = ST_DRAFT.equals(st) || "已驳回".equals(st);
            partsOnly = ST_ACCEPTED.equals(st) || ST_REPAIRING.equals(st);
            if (!editable && !partsOnly) {
                throw new BusinessException("当前状态（" + st + "）不允许修改，请先驳回或重置草稿");
            }
            BillStatusUtils.assertDirectModifiable("售后维修单", db.getRepairNo(), db.getStatus(), db.getCloseStatus());
        }
        if (bill.getRepairNo() == null || bill.getRepairNo().isBlank()) bill.setRepairNo(generateRepairNo());
        QueryWrapper<SaleRepair> dup = new QueryWrapper<SaleRepair>().eq("repair_no", bill.getRepairNo());
        if (!isNew) dup.ne("repair_id", bill.getRepairId());
        sysParamValidator.validateBillNoDuplicate(bill.getRepairNo(), "售后维修单", count(dup) > 0);

        if (bill.getStatus() == null || bill.getStatus().isBlank()) bill.setStatus(ST_DRAFT);
        if (bill.getApprovalStatus() == null || bill.getApprovalStatus().isBlank()) bill.setApprovalStatus(ST_DRAFT);
        if (bill.getRepairDate() == null) bill.setRepairDate(LocalDate.now());
        if (bill.getServiceType() == null) bill.setServiceType("寄修");
        if (bill.getCloseStatus() == null) bill.setCloseStatus(0);
        // 前端空串统一落 null：避免 transferred_bill_no='' 被误判为"已转出"
        if (bill.getTransferredBillNo() != null && bill.getTransferredBillNo().isBlank()) bill.setTransferredBillNo(null);
        if (bill.getTransferredBillType() != null && bill.getTransferredBillType().isBlank()) bill.setTransferredBillType(null);
        // 维修仓/备件仓未选时取设置中心默认参数（启动初始化会创建并写入）
        if (bill.getWarehouseId() == null) bill.setWarehouseId(configWarehouseId("sale.repair.warehouse.id"));
        if (bill.getPartsWarehouseId() == null) bill.setPartsWarehouseId(configWarehouseId("sale.repair.parts.warehouse.id"));

        if (!editable) {
            // 已受理/维修中：只更新备注 + 配件，维修产品与费用以库中为准
            bill.setRepairFee(null);
            bill.setPartsFee(null);
            bill.setTotalFee(null);
            UpdateWrapper<SaleRepair> uw = new UpdateWrapper<>();
            uw.eq("repair_id", bill.getRepairId())
                    .set("remark", bill.getRemark())
                    .set("fault_description", bill.getFaultDescription())
                    .set("updated_by", bill.getUpdatedBy())
                    .set("updated_at", LocalDateTime.now());
            update(uw);
            replaceParts(bill.getRepairId(), bill.getPartsWarehouseId(), parts);
            // 配件维护同样要落自定义字段与版本记录（表头自定义字段不随费用字段被清空）
            saveHeaderCustomFieldValues(bill);
            SaleRepair after = getDetail(bill.getRepairId());
            recordVersion("UPDATE", bill, oldSnapshot, after);
            return after;
        }

        List<SaleRepairItem> validItems = new ArrayList<>();
        if (items != null) for (SaleRepairItem it : items) {
            if (it != null && it.getMaterialId() != null && nvl(it.getQuantity()).signum() > 0) validItems.add(it);
        }
        if (validItems.isEmpty()) throw new BusinessException("请至少添加一行维修产品");

        applyWarranty(bill, validItems);
        validateFee(bill);

        // 汇总
        BigDecimal qty = BigDecimal.ZERO;
        for (SaleRepairItem it : validItems) qty = qty.add(nvl(it.getQuantity()));
        bill.setTotalQuantity(qty);
        bill.setTotalFee(nvl(bill.getRepairFee()).add(nvl(bill.getPartsFee())));

        if (isNew) save(bill); else updateById(bill);
        // 自定义字段：表头先落库（主表已有 id），维修明细/配件行插入拿到新 id 后再逐行保存
        saveHeaderCustomFieldValues(bill);

        // 明细先删后插：旧行 id 的自定义字段值一并清理，避免孤儿值
        clearItemCustomFieldValues(bill.getRepairId());
        itemMapper.delete(new QueryWrapper<SaleRepairItem>().eq("repair_id", bill.getRepairId()));
        int line = 1;
        for (SaleRepairItem it : validItems) {
            it.setRepairItemId(null);
            it.setRepairId(bill.getRepairId());
            it.setLineNo(line++);
            if (it.getWarrantyStatus() == null) it.setWarrantyStatus(bill.getWarrantyStatus());
            fillMaterial(it);
            itemMapper.insert(it);
            saveItemCustomFieldValues(it);
        }
        replaceParts(bill.getRepairId(), bill.getPartsWarehouseId(), parts);
        SaleRepair after = getDetail(bill.getRepairId());
        // 数据版本记录（独立事务，失败只告警）
        recordVersion(isNew ? "INSERT" : "UPDATE", bill, isNew ? null : oldSnapshot, after);
        return after;
    }

    /** 保存表头自定义字段值（值为空 Map 时不处理，避免误清空；显式传 null 值表示清空该字段） */
    private void saveHeaderCustomFieldValues(SaleRepair bill) {
        if (bill == null || bill.getRepairId() == null) return;
        Map<String, Object> values = bill.getCustomFieldValues();
        if (values == null || values.isEmpty()) return;
        try {
            customFieldService.saveFieldValues("sale_repair", bill.getRepairId(), values);
        } catch (Exception e) {
            log.warn("保存售后维修单表头自定义字段失败 id={}: {}", bill.getRepairId(), e.getMessage());
        }
    }

    /** 保存维修明细行自定义字段值（明细必须先 insert 拿到主键） */
    private void saveItemCustomFieldValues(SaleRepairItem it) {
        if (it == null || it.getRepairItemId() == null) return;
        Map<String, Object> values = it.getCustomFieldValues();
        if (values == null || values.isEmpty()) return;
        try {
            customFieldService.saveFieldValues("sale_repair_item", it.getRepairItemId(), values);
        } catch (Exception e) {
            log.warn("保存售后维修单明细自定义字段失败 id={}: {}", it.getRepairItemId(), e.getMessage());
        }
    }

    /** 保存配件行自定义字段值（配件必须先 insert 拿到主键） */
    private void savePartCustomFieldValues(SaleRepairPart p) {
        if (p == null || p.getPartId() == null) return;
        Map<String, Object> values = p.getCustomFieldValues();
        if (values == null || values.isEmpty()) return;
        try {
            customFieldService.saveFieldValues("sale_repair_part", p.getPartId(), values);
        } catch (Exception e) {
            log.warn("保存售后维修单配件自定义字段失败 id={}: {}", p.getPartId(), e.getMessage());
        }
    }

    /** 清理本单所有旧维修明细行的自定义字段值（先删后插前调用） */
    private void clearItemCustomFieldValues(Long repairId) {
        if (repairId == null) return;
        try {
            List<SaleRepairItem> olds = itemMapper.selectList(new QueryWrapper<SaleRepairItem>()
                    .select("repair_item_id").eq("repair_id", repairId));
            for (SaleRepairItem old : olds) {
                if (old != null && old.getRepairItemId() != null) {
                    customFieldService.deleteFieldValues("sale_repair_item", old.getRepairItemId());
                }
            }
        } catch (Exception e) {
            log.warn("清理售后维修单明细自定义字段失败 repairId={}: {}", repairId, e.getMessage());
        }
    }

    /** 清理本单所有旧配件行的自定义字段值（配件先删后插前调用） */
    private void clearPartCustomFieldValues(Long repairId) {
        if (repairId == null) return;
        try {
            List<SaleRepairPart> olds = partMapper.selectList(new QueryWrapper<SaleRepairPart>()
                    .select("part_id").eq("repair_id", repairId));
            for (SaleRepairPart old : olds) {
                if (old != null && old.getPartId() != null) {
                    customFieldService.deleteFieldValues("sale_repair_part", old.getPartId());
                }
            }
        } catch (Exception e) {
            log.warn("清理售后维修单配件自定义字段失败 repairId={}: {}", repairId, e.getMessage());
        }
    }

    /** 数据版本记录：独立事务，失败只告警、不影响单据保存 */
    private void recordVersion(String op, SaleRepair bill, Object before, Object after) {
        if (bill == null || bill.getRepairId() == null) return;
        try {
            String operator = bill.getUpdatedBy() != null && !bill.getUpdatedBy().isBlank()
                    ? bill.getUpdatedBy() : bill.getCreatedBy();
            String remark = "INSERT".equals(op) ? "新增" : ("UPDATE".equals(op) ? "修改" : "删除");
            saleBillVersionRecorder.record("sale_repair", bill.getRepairId(), bill.getRepairNo(),
                    "sale", op, before, after, operator, remark);
        } catch (Exception e) {
            log.warn("售后维修单数据版本记录失败 id={} op={}: {}", bill.getRepairId(), op, e.getMessage());
        }
    }

    /** 配件先删后插：只在 parts 非空时执行，避免请求未带配件时清空既有清单 */
    private void replaceParts(Long repairId, Long defaultWarehouseId, List<SaleRepairPart> parts) {
        if (parts == null) return;
        // 旧配件行 id 的自定义字段值一并清理，避免孤儿值
        clearPartCustomFieldValues(repairId);
        partMapper.delete(new QueryWrapper<SaleRepairPart>().eq("repair_id", repairId));
        int pl = 1;
        for (SaleRepairPart p : parts) {
            if (p == null || p.getMaterialId() == null || nvl(p.getQuantity()).signum() <= 0) continue;
            p.setPartId(null);
            p.setRepairId(repairId);
            p.setLineNo(pl++);
            if (p.getWarehouseId() == null) p.setWarehouseId(defaultWarehouseId);
            fillPartMaterial(p);
            if (p.getAmount() == null) p.setAmount(nvl(p.getQuantity()).multiply(nvl(p.getAvgCost())));
            partMapper.insert(p);
            savePartCustomFieldValues(p);
        }
    }

    private void validateFee(SaleRepair bill) {
        if ("保内".equals(bill.getWarrantyStatus())) {
            if (nvl(bill.getRepairFee()).signum() > 0 || nvl(bill.getPartsFee()).signum() > 0) {
                throw new BusinessException("保内维修不允许收取维修费/配件费");
            }
        }
        if (nvl(bill.getRepairFee()).signum() < 0 || nvl(bill.getPartsFee()).signum() < 0) {
            throw new BusinessException("维修费与配件费不能为负数");
        }
    }

    private void fillMaterial(SaleRepairItem it) {
        Material m = materialService.getById(it.getMaterialId());
        if (m == null) throw new BusinessException("维修产品不存在: " + it.getMaterialId());
        it.setMaterialCode(m.getMaterialCode());
        it.setMaterialName(m.getMaterialName());
        it.setSpecification(m.getModel());
        if (it.getUnitId() == null) it.setUnitId(m.getBaseUnitId());
        if (it.getUnit() == null) it.setUnit(defaultUnitName(m.getBaseUnitId()));
        if (it.getReceivedQuantity() == null) it.setReceivedQuantity(BigDecimal.ZERO);
        if (it.getReturnedQuantity() == null) it.setReturnedQuantity(BigDecimal.ZERO);
    }

    private void fillPartMaterial(SaleRepairPart p) {
        Material m = materialService.getById(p.getMaterialId());
        if (m == null) throw new BusinessException("配件不存在: " + p.getMaterialId());
        p.setMaterialCode(m.getMaterialCode());
        p.setMaterialName(m.getMaterialName());
        p.setSpecification(m.getModel());
        if (p.getUnitId() == null) p.setUnitId(m.getBaseUnitId());
        if (p.getUnit() == null) p.setUnit(defaultUnitName(m.getBaseUnitId()));
        if (p.getIssuedQuantity() == null) p.setIssuedQuantity(BigDecimal.ZERO);
    }

    private String defaultUnitName(Long unitId) {
        if (unitId == null) return null;
        try {
            return jdbcTemplate.queryForObject("SELECT unit_name FROM unit_of_measure WHERE unit_id = ?", String.class, unitId);
        } catch (Exception e) {
            return null;
        }
    }

    /** 读取设置中心保存的仓库参数；查不到或非数字返回 null */
    private Long configWarehouseId(String configKey) {
        try {
            String v = jdbcTemplate.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key = ?", String.class, configKey);
            if (v == null || v.isBlank()) return null;
            return Long.valueOf(v.trim());
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 保修判定 ====================

    @Override
    public void applyWarranty(SaleRepair bill, List<SaleRepairItem> items) {
        LocalDate baseDate = null;
        String srcType = bill.getSourceBillType() == null ? "" : bill.getSourceBillType();
        try {
            if (srcType.contains("销售出库") && bill.getSourceId() != null) {
                baseDate = jdbcTemplate.queryForObject(
                        "SELECT delivery_date FROM sale_delivery WHERE delivery_id = ?", LocalDate.class, bill.getSourceId());
            } else if (srcType.contains("销售订单") && bill.getSourceId() != null) {
                baseDate = jdbcTemplate.queryForObject(
                        "SELECT MIN(d.delivery_date) FROM sale_delivery d WHERE d.status = '已生效' "
                                + "AND d.source_bill_no = (SELECT order_no FROM sale_order WHERE order_id = ?)",
                        LocalDate.class, bill.getSourceId());
            }
        } catch (Exception ignore) {
            // 取不到出库日期时按报修日期兜底
        }
        if (baseDate == null) baseDate = bill.getRepairDate() != null ? bill.getRepairDate() : LocalDate.now();

        int months = 12;
        if (items != null && !items.isEmpty() && items.get(0).getMaterialId() != null) {
            Material m = materialService.getById(items.get(0).getMaterialId());
            if (m != null && m.getWarrantyMonths() != null && m.getWarrantyMonths() > 0) months = m.getWarrantyMonths();
        }
        bill.setWarrantyMonths(months);
        LocalDate expire = baseDate.plusMonths(months);
        String computed = LocalDate.now().isAfter(expire) ? "保外" : "保内";

        String manual = bill.getWarrantyStatus();
        if (manual != null && !manual.isBlank() && !"待判定".equals(manual) && !manual.equals(computed)) {
            if ("保内".equals(manual) && "保外".equals(computed)
                    && (bill.getRemark() == null || bill.getRemark().isBlank())) {
                throw new BusinessException("将保外改判为保内必须填写备注说明原因");
            }
            bill.setWarrantyBasis("原出库日期 " + baseDate + "，保修 " + months + " 个月（到期 " + expire
                    + "），系统判定：" + computed + "，人工改判为：" + manual + "；原因：" + (bill.getRemark() == null ? "-" : bill.getRemark()));
        } else {
            bill.setWarrantyStatus(computed);
            bill.setWarrantyBasis("原出库日期 " + baseDate + "，保修 " + months + " 个月（到期 " + expire + "），判定：" + computed);
        }
        if (items != null) for (SaleRepairItem it : items) it.setWarrantyStatus(bill.getWarrantyStatus());
    }

    // ==================== 状态流转 ====================

    @Override
    protected void validateStrictSubmit(SaleRepair bill) {
        List<SaleRepairItem> items = itemMapper.selectList(
                new QueryWrapper<SaleRepairItem>().eq("repair_id", bill.getRepairId()));
        if (items.isEmpty()) throw new BusinessException("售后维修单没有维修产品，请先添加后再提交");
        if (bill.getCustomerId() == null) throw new BusinessException("请选择客户");
    }

    @Override
    @Transactional
    public void submitForApproval(Long id) {
        SaleRepair bill = getById(id);
        if (bill == null) throw new BusinessException("售后维修单不存在");
        validateStrictSubmit(bill);
        super.submitForApprovalById(id);
        // 免审批提交即生效：业务状态推进到「已受理」；开启审批时保持「待审核」
        SaleRepair after = getById(id);
        if (after != null && "已生效".equals(after.getStatus())) {
            updateStatus(id, ST_ACCEPTED);
        }
    }

    @Override
    @Transactional
    public void approve(Long id, String approver) {
        SaleRepair bill = getById(id);
        if (bill == null) throw new BusinessException("售后维修单不存在");
        validateStrictSubmit(bill);
        super.approve(id, approver);
        SaleRepair after = getById(id);
        if (after != null && "已生效".equals(after.getStatus())) updateStatus(id, ST_ACCEPTED);
    }

    @Override public void reject(Long id, String rejector, String reason) { super.reject(id, rejector, reason); }

    @Override
    @Transactional
    public void startRepair(Long id, String operator) {
        SaleRepair bill = getById(id);
        if (bill == null) throw new BusinessException("售后维修单不存在");
        if (!ST_ACCEPTED.equals(bill.getStatus())) {
            throw new BusinessException("只有已受理的维修单才能开始维修，当前状态：" + bill.getStatus());
        }
        updateStatus(id, ST_REPAIRING);
    }

    @Override
    @Transactional
    public void completeRepair(Long id, String operator, String result) {
        SaleRepair bill = getById(id);
        if (bill == null) throw new BusinessException("售后维修单不存在");
        if (!ST_REPAIRING.equals(bill.getStatus())) {
            throw new BusinessException("只有维修中的单据才能完成，当前状态：" + bill.getStatus());
        }
        if (result == null || result.isBlank()) throw new BusinessException("请选择维修结果");
        if (!List.of("已修复", "无法修复", "客户放弃").contains(result)) {
            throw new BusinessException("维修结果不合法：" + result);
        }
        if ("保内".equals(bill.getWarrantyStatus())) {
            bill.setRepairFee(BigDecimal.ZERO);
            bill.setPartsFee(BigDecimal.ZERO);
        }
        bill.setTotalFee(nvl(bill.getRepairFee()).add(nvl(bill.getPartsFee())));
        bill.setRepairResult(result);
        bill.setStatus(ST_DONE);
        bill.setUpdatedBy(operator);
        updateById(bill);
        createReceiptIfNeeded(bill);
    }

    /**
     * 保外收费：维修完成时自动生成一张「收款单草稿」（待收款）。
     * 幂等：已有有效收款单则跳过；原收款单已作废时允许重新生成；财务侧失败只告警，不阻断维修完成。
     */
    private void createReceiptIfNeeded(SaleRepair bill) {
        if (!"保外".equals(bill.getWarrantyStatus())) return;
        if (nvl(bill.getTotalFee()).signum() <= 0) return;
        if (bill.getReceiptId() != null) {
            // 已回写的收款单若仍有效则不再生成；被作废则允许重新生成
            com.erp.finance.entity.PaymentReceipt old = financeService.getReceiptById(bill.getReceiptId());
            if (old != null && !"已作废".equals(old.getStatus())) return;
        }
        try {
            com.erp.finance.entity.PaymentReceipt receipt =
                    financeService.createReceiptDraftFromSource("售后维修单", bill.getRepairId());
            if (receipt != null) {
                UpdateWrapper<SaleRepair> uw = new UpdateWrapper<>();
                uw.eq("repair_id", bill.getRepairId())
                        .set("receipt_id", receipt.getReceiptId())
                        .set("receipt_no", receipt.getReceiptNo());
                update(uw);
                bill.setReceiptId(receipt.getReceiptId());
                bill.setReceiptNo(receipt.getReceiptNo());
                log.info("售后维修单生成收款单草稿: {} -> {}", bill.getRepairNo(), receipt.getReceiptNo());
            }
        } catch (Exception e) {
            log.warn("售后维修单生成收款单草稿失败（不阻断维修完成）: {} err={}", bill.getRepairNo(), e.getMessage());
        }
    }

    private void updateStatus(Long id, String status) {
        UpdateWrapper<SaleRepair> uw = new UpdateWrapper<>();
        uw.eq("repair_id", id).set("status", status).set("updated_at", LocalDateTime.now());
        update(uw);
    }

    private SaleRepair mustStatus(SaleRepair bill, String... allowed) {
        for (String s : allowed) if (s.equals(bill.getStatus())) return bill;
        throw new BusinessException("当前状态（" + bill.getStatus() + "）不允许该操作，需为：" + String.join("/", allowed));
    }

    // ==================== 库存动作 ====================

    @Override
    @Transactional
    public void receiveIn(Long id, String operator) {
        SaleRepair bill = mustStatus(getDetail(id), ST_ACCEPTED, ST_REPAIRING);
        if (bill.getWarehouseId() == null) bill.setWarehouseId(configWarehouseId("sale.repair.warehouse.id"));
        if (bill.getWarehouseId() == null) throw new BusinessException("请先选择维修仓");
        if (bill.getReceivedInboundId() != null) throw new BusinessException("已生成收件入库单（" + bill.getReceivedInboundNo() + "），请勿重复生成");

        OtherReceipt r = new OtherReceipt();
        r.setReceiptDate(LocalDate.now());
        r.setReceiptType("维修入库");
        r.setWarehouseId(bill.getWarehouseId());
        r.setSourceBillType("售后维修单");
        r.setSourceBillNo(bill.getRepairNo());
        r.setRemark("售后维修收件：" + bill.getRepairNo());
        r.setStatus(ST_DRAFT);
        r.setApprovalStatus(ST_DRAFT);
        r.setCloseStatus(0);
        r.setCreatedBy(operator);
        r.setUpdatedBy(operator);

        List<OtherReceiptItem> ris = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (SaleRepairItem it : bill.getItems()) {
            OtherReceiptItem ri = new OtherReceiptItem();
            ri.setMaterialId(it.getMaterialId());
            ri.setMaterialCode(it.getMaterialCode());
            ri.setMaterialName(it.getMaterialName());
            ri.setSpecification(it.getSpecification());
            ri.setUnitId(it.getUnitId());
            ri.setQuantity(nvl(it.getQuantity()));
            ri.setUnitPrice(BigDecimal.ZERO);
            ri.setAmount(BigDecimal.ZERO);
            ri.setStockId(bill.getWarehouseId());
            ri.setBatchNo(it.getSerialNo() != null && !it.getSerialNo().isBlank() ? it.getSerialNo() : bill.getRepairNo());
            ris.add(ri);
            total = total.add(nvl(it.getQuantity()));
        }
        otherReceiptService.saveReceiptWithItems(r, ris);

        bill.setReceivedInboundId(r.getReceiptId());
        bill.setReceivedInboundNo(r.getReceiptNo());
        bill.setReceiveDate(LocalDate.now());
        bill.setReceivedQuantity(total);
        updateById(bill);
        for (SaleRepairItem it : bill.getItems()) {
            it.setReceivedQuantity(nvl(it.getQuantity()));
            itemMapper.updateById(it);
        }
    }

    @Override
    @Transactional
    public void issueParts(Long id, String operator) {
        SaleRepair bill = mustStatus(getDetail(id), ST_REPAIRING);
        if (bill.getParts() == null || bill.getParts().isEmpty()) throw new BusinessException("没有可领用的配件");

        List<SaleRepairPart> todo = new ArrayList<>();
        for (SaleRepairPart p : bill.getParts()) {
            BigDecimal remain = nvl(p.getQuantity()).subtract(nvl(p.getIssuedQuantity()));
            if (remain.signum() > 0) todo.add(p);
        }
        if (todo.isEmpty()) throw new BusinessException("配件已全部领用");

        Long wh = bill.getPartsWarehouseId() != null ? bill.getPartsWarehouseId()
                : configWarehouseId("sale.repair.parts.warehouse.id");
        for (SaleRepairPart p : todo) if (p.getWarehouseId() != null) { wh = p.getWarehouseId(); break; }
        if (wh == null) throw new BusinessException("请先选择备件仓");

        OtherIssue oi = new OtherIssue();
        oi.setIssueDate(LocalDate.now());
        oi.setIssueType("维修领料");
        oi.setWarehouseId(wh);
        oi.setSourceBillType("售后维修单");
        oi.setSourceBillNo(bill.getRepairNo());
        oi.setRemark("售后维修配件领用：" + bill.getRepairNo());
        oi.setStatus(ST_DRAFT);
        oi.setApprovalStatus(ST_DRAFT);
        oi.setCloseStatus(0);
        oi.setCreatedBy(operator);
        oi.setUpdatedBy(operator);

        List<OtherIssueItem> ois = new ArrayList<>();
        BigDecimal partsFee = BigDecimal.ZERO;
        for (SaleRepairPart p : todo) {
            BigDecimal remain = nvl(p.getQuantity()).subtract(nvl(p.getIssuedQuantity()));
            OtherIssueItem oii = new OtherIssueItem();
            oii.setMaterialId(p.getMaterialId());
            oii.setMaterialCode(p.getMaterialCode());
            oii.setMaterialName(p.getMaterialName());
            oii.setSpecification(p.getSpecification());
            oii.setUnitId(p.getUnitId());
            oii.setQuantity(remain);
            oii.setUnitPrice(nvl(p.getAvgCost()));
            oii.setAmount(remain.multiply(nvl(p.getAvgCost())));
            oii.setStockId(p.getWarehouseId() != null ? p.getWarehouseId() : wh);
            oii.setBatchNo(p.getBatchNo());
            ois.add(oii);
            partsFee = partsFee.add(remain.multiply(nvl(p.getAvgCost())));
        }
        otherIssueService.saveIssueWithItems(oi, ois);

        for (SaleRepairPart p : todo) {
            p.setIssuedQuantity(nvl(p.getQuantity()));
            p.setIssueId(oi.getIssueId());
            p.setIssueNo(oi.getIssueNo());
            partMapper.updateById(p);
        }
        if (partsFee.signum() > 0) {
            bill.setPartsFee(partsFee);
            bill.setTotalFee(nvl(bill.getRepairFee()).add(partsFee));
            updateById(bill);
        }
    }

    @Override
    @Transactional
    public void returnToCustomer(Long id, String operator) {
        SaleRepair bill = mustStatus(getDetail(id), ST_DONE);
        if (bill.getWarehouseId() == null) bill.setWarehouseId(configWarehouseId("sale.repair.warehouse.id"));
        if (bill.getWarehouseId() == null) throw new BusinessException("请先选择维修仓");
        if (bill.getReceivedQuantity() == null || bill.getReceivedQuantity().signum() <= 0) {
            throw new BusinessException("还没有收件入库，无法返还");
        }
        if (bill.getReturnedIssueId() != null) throw new BusinessException("已生成返还出库单（" + bill.getReturnedIssueNo() + "），请勿重复生成");

        List<OtherIssueItem> ois = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (SaleRepairItem it : bill.getItems()) {
            BigDecimal remain = nvl(it.getReceivedQuantity()).subtract(nvl(it.getReturnedQuantity()));
            if (remain.signum() <= 0) continue;
            OtherIssueItem oii = new OtherIssueItem();
            oii.setMaterialId(it.getMaterialId());
            oii.setMaterialCode(it.getMaterialCode());
            oii.setMaterialName(it.getMaterialName());
            oii.setSpecification(it.getSpecification());
            oii.setUnitId(it.getUnitId());
            oii.setQuantity(remain);
            oii.setUnitPrice(BigDecimal.ZERO);
            oii.setAmount(BigDecimal.ZERO);
            oii.setStockId(bill.getWarehouseId());
            oii.setBatchNo(it.getSerialNo() != null && !it.getSerialNo().isBlank() ? it.getSerialNo() : bill.getRepairNo());
            ois.add(oii);
            total = total.add(remain);
        }
        if (ois.isEmpty()) throw new BusinessException("没有可返还的维修件");

        OtherIssue oi = new OtherIssue();
        oi.setIssueDate(LocalDate.now());
        oi.setIssueType("维修返还");
        oi.setWarehouseId(bill.getWarehouseId());
        oi.setSourceBillType("售后维修单");
        oi.setSourceBillNo(bill.getRepairNo());
        oi.setRemark("售后维修返还：" + bill.getRepairNo());
        oi.setStatus(ST_DRAFT);
        oi.setApprovalStatus(ST_DRAFT);
        oi.setCloseStatus(0);
        oi.setCreatedBy(operator);
        oi.setUpdatedBy(operator);
        otherIssueService.saveIssueWithItems(oi, ois);

        bill.setReturnedIssueId(oi.getIssueId());
        bill.setReturnedIssueNo(oi.getIssueNo());
        bill.setReturnDate(LocalDate.now());
        bill.setReturnedQuantity(total);
        bill.setStatus(ST_RETURNED);
        updateById(bill);
        for (SaleRepairItem it : bill.getItems()) {
            it.setReturnedQuantity(nvl(it.getReceivedQuantity()));
            itemMapper.updateById(it);
        }
    }

    // ==================== 转换新 / 转退货 ====================

    @Override
    @Transactional
    public SaleRepair transferToExchange(Long id, List<Map<String, Object>> lines, String operator) {
        SaleRepair bill = mustTransferable(getDetail(id));
        if (!"销售订单".equals(bill.getSourceBillType()) || bill.getSourceId() == null) {
            throw new BusinessException("只有来源为销售订单的维修单才能转换新（当前来源：" + bill.getSourceBillType() + "）");
        }
        if (lines == null || lines.isEmpty()) throw new BusinessException("请为每行维修产品选择替换货品");

        List<Map<String, Object>> exgLines = new ArrayList<>();
        for (Map<String, Object> ln : lines) {
            Long repairItemId = toLong(ln.get("repairItemId"));
            Long subId = toLong(ln.get("substituteMaterialId"));
            if (repairItemId == null || subId == null) throw new BusinessException("转换新参数不完整");
            SaleRepairItem item = bill.getItems().stream()
                    .filter(x -> repairItemId.equals(x.getRepairItemId())).findFirst()
                    .orElseThrow(() -> new BusinessException("维修明细不存在：" + repairItemId));
            Long orderItemId = jdbcTemplate.queryForObject(
                    "SELECT order_item_id FROM sale_order_item WHERE order_id = ? AND material_id = ? "
                            + "ORDER BY line_no LIMIT 1", Long.class, bill.getSourceId(), item.getMaterialId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderItemId", orderItemId);
            m.put("quantity", nvl(item.getQuantity()));
            m.put("substituteMaterialId", subId);
            m.put("unitPrice", BigDecimal.ZERO);
            exgLines.add(m);
        }
        SaleExchange exg = saleExchangeService.createFromOrder(bill.getSourceId(), exgLines, operator);
        bill.setTransferredBillType("销售换货单");
        bill.setTransferredBillNo(exg.getExchangeNo());
        bill.setTransferredBillId(exg.getExchangeId());
        bill.setStatus(ST_DONE);
        updateById(bill);
        return bill;
    }

    @Override
    @Transactional
    public SaleRepair transferToReturn(Long id, String operator) {
        SaleRepair bill = mustTransferable(getDetail(id));
        SaleReturn ret = new SaleReturn();
        ret.setReturnDate(LocalDate.now());
        ret.setCustomerId(bill.getCustomerId());
        ret.setCustomerName(bill.getCustomerName());
        ret.setWarehouseId(bill.getWarehouseId());
        ret.setExchangeRate(BigDecimal.ONE);
        ret.setSourceBillType("售后维修单");
        ret.setSourceBillNo(bill.getRepairNo());
        ret.setRemark("售后退货，维修单 " + bill.getRepairNo() + "；维修结果：" + bill.getRepairResult());
        ret.setStatus(ST_DRAFT);
        ret.setApprovalStatus(ST_DRAFT);
        ret.setCloseStatus(0);
        ret.setCreatedBy(operator);
        ret.setUpdatedBy(operator);
        ret.setReturnNo(saleReturnService.generateReturnNo());

        List<SaleReturnItem> ris = new ArrayList<>();
        for (SaleRepairItem it : bill.getItems()) {
            SaleReturnItem ri = new SaleReturnItem();
            ri.setMaterialId(it.getMaterialId());
            ri.setMaterialCode(it.getMaterialCode());
            ri.setMaterialName(it.getMaterialName());
            ri.setSpecification(it.getSpecification());
            ri.setUnitId(it.getUnitId());
            ri.setUnit(it.getUnit());
            ri.setQuantity(nvl(it.getReceivedQuantity()).signum() > 0 ? it.getReceivedQuantity() : it.getQuantity());
            ri.setUnitPrice(BigDecimal.ZERO);
            ri.setTaxRate(new BigDecimal("13"));
            ri.setSourceBillType("售后维修单");
            ri.setSourceBillNo(bill.getRepairNo());
            ris.add(ri);
        }
        saleReturnService.saveReturnWithItems(ret, ris);
        bill.setTransferredBillType("销售退货单");
        bill.setTransferredBillNo(ret.getReturnNo());
        bill.setTransferredBillId(ret.getReturnId());
        bill.setStatus(ST_DONE);
        updateById(bill);
        return bill;
    }

    private SaleRepair mustTransferable(SaleRepair bill) {
        if (!ST_DONE.equals(bill.getStatus())) throw new BusinessException("只有维修完成的单据才能转换新/转退货");
        if (hasText(bill.getTransferredBillNo())) throw new BusinessException("已转出：" + bill.getTransferredBillNo());
        if ("已修复".equals(bill.getRepairResult())) throw new BusinessException("维修结果已修复，无需转换新/退货");
        return bill;
    }

    // ==================== 回退与删除 ====================

    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        SaleRepair bill = getById(id);
        if (bill == null) throw new BusinessException("单据不存在");
        if (hasText(bill.getTransferredBillNo())) throw new BusinessException("已转出单据（" + bill.getTransferredBillNo() + "），不能重置草稿");
        checkNoEffectiveStockDoc(bill);
        super.reverseApprove(id, operator);
        // 生成的库存单据已随重置删除：把收件/返还/领用痕迹与维修进度一并归零，避免下次生效沿用旧值
        UpdateWrapper<SaleRepair> uw = new UpdateWrapper<>();
        uw.eq("repair_id", id)
                .set("received_inbound_id", null).set("received_inbound_no", null)
                .set("returned_issue_id", null).set("returned_issue_no", null)
                .set("receive_date", null).set("return_date", null)
                .set("received_quantity", BigDecimal.ZERO).set("returned_quantity", BigDecimal.ZERO)
                .set("repair_result", null)
                .set("receipt_id", null).set("receipt_no", null)
                .set("updated_at", LocalDateTime.now());
        update(uw);
        jdbcTemplate.update("UPDATE sale_repair_item SET received_quantity = 0, returned_quantity = 0 WHERE repair_id = ?", id);
        jdbcTemplate.update("UPDATE sale_repair_part SET issued_quantity = 0, issue_id = NULL, issue_no = NULL WHERE repair_id = ?", id);
    }

    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        SaleRepair bill = getById(id);
        if (bill == null) throw new BusinessException("单据不存在");
        if (hasText(bill.getTransferredBillNo())) throw new BusinessException("已转出单据（" + bill.getTransferredBillNo() + "），不能作废");
        checkNoEffectiveStockDoc(bill);
        super.voidBill(id, operator, reason);
    }

    @Override
    @Transactional
    public void deleteBill(Long id) {
        SaleRepair bill = getById(id);
        if (bill == null) return;
        if (hasText(bill.getTransferredBillNo())) throw new BusinessException("已转出单据（" + bill.getTransferredBillNo() + "），不能删除");
        checkNoEffectiveStockDoc(bill);
        // 删除前取整单快照（含明细/配件与自定义字段值），供数据版本记录
        SaleRepair snapshot = getDetail(id);
        clearItemCustomFieldValues(id);
        clearPartCustomFieldValues(id);
        try {
            customFieldService.deleteFieldValues("sale_repair", id);
        } catch (Exception e) {
            log.warn("清理售后维修单表头自定义字段失败 id={}: {}", id, e.getMessage());
        }
        itemMapper.delete(new QueryWrapper<SaleRepairItem>().eq("repair_id", id));
        partMapper.delete(new QueryWrapper<SaleRepairPart>().eq("repair_id", id));
        super.deleteBill(id);
        recordVersion("DELETE", snapshot, snapshot, null);
    }

    /** 生成的库存单据必须未生效；草稿直接删除，避免悬挂 */
    private void checkNoEffectiveStockDoc(SaleRepair bill) {
        if (bill.getReceivedInboundId() != null) {
            OtherReceipt r = otherReceiptService.getById(bill.getReceivedInboundId());
            if (r != null) {
                if ("已生效".equals(r.getStatus())) {
                    throw new BusinessException("收件入库单 " + r.getReceiptNo() + " 已生效，请先作废或重置草稿");
                }
                otherReceiptService.deleteBill(r.getReceiptId());
            }
        }
        if (bill.getReturnedIssueId() != null) {
            OtherIssue o = otherIssueService.getById(bill.getReturnedIssueId());
            if (o != null) {
                if ("已生效".equals(o.getStatus())) {
                    throw new BusinessException("返还出库单 " + o.getIssueNo() + " 已生效，请先作废或重置草稿");
                }
                otherIssueService.deleteBill(o.getIssueId());
            }
        }
        // 保外维修自动生成的收款单：草稿直接删，已生效必须先生效单据侧处理
        if (bill.getReceiptId() != null) {
            com.erp.finance.entity.PaymentReceipt r = financeService.getReceiptById(bill.getReceiptId());
            if (r != null) {
                String st = r.getStatus();
                if ("草稿".equals(st)) {
                    financeService.deleteReceipt(r.getReceiptId());
                } else if (!"已作废".equals(st)) {
                    throw new BusinessException("收款单 " + r.getReceiptNo() + " 已生效，请先作废或重置草稿");
                }
            }
        }
        List<SaleRepairPart> parts = partMapper.selectList(new QueryWrapper<SaleRepairPart>()
                .eq("repair_id", bill.getRepairId()).isNotNull("issue_id"));
        for (SaleRepairPart p : parts) {
            OtherIssue o = otherIssueService.getById(p.getIssueId());
            if (o == null) continue;
            if ("已生效".equals(o.getStatus())) {
                throw new BusinessException("配件领用出库单 " + o.getIssueNo() + " 已生效，请先作废或重置草稿");
            }
            otherIssueService.deleteBill(o.getIssueId());
        }
    }

    @Override
    public String generateRepairNo() {
        return sysSerialNoConfigService.generateOrDefault("SALE_REPAIR", () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "SVRV-" + dateStr + "-";
            QueryWrapper<SaleRepair> w = new QueryWrapper<>();
            w.likeRight("repair_no", prefix).orderByDesc("repair_no").last("LIMIT 1");
            SaleRepair last = getOne(w);
            int seq = 1;
            if (last != null && last.getRepairNo() != null) {
                try { seq = Integer.parseInt(last.getRepairNo().substring(prefix.length())) + 1; }
                catch (Exception ignored) { }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    // ==================== 工具 ====================

    private static BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static boolean hasText(String s) { return s != null && !s.isBlank(); }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.valueOf(String.valueOf(v)); } catch (Exception e) { return null; }
    }
}
