package com.erp.produce.assemble.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillStatusUtils;
import com.erp.config.BusinessException;
import com.erp.serialno.service.SysSerialNoConfigService;
import com.erp.basedata.entity.Warehouse;
import com.erp.inventory.service.InventoryService;
import com.erp.inventory.service.WarehouseService;
import com.erp.masterdata.entity.Material;
import com.erp.basedata.service.CurrencyService;
import com.erp.masterdata.service.MaterialService;
import com.erp.config.service.SysConfigService;
import com.erp.produce.assemble.entity.ProduceAssemble;
import com.erp.produce.assemble.entity.ProduceAssembleDetail;
import com.erp.produce.assemble.entity.ProduceAssembleBom;
import com.erp.produce.assemble.mapper.ProduceAssembleMapper;
import com.erp.produce.assemble.mapper.ProduceAssembleDetailMapper;
import com.erp.produce.assemble.mapper.ProduceAssembleBomMapper;
import com.erp.produce.assemble.service.ProduceAssembleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.query.BillHeaderFilterSupport;
import com.erp.common.query.ListQuerySupport;
import com.erp.common.util.CustomFieldSearchHelper;
import com.erp.common.util.SortHelper;
import com.erp.common.util.TaxAmountUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 生产组装单Service实现类
 * 
 * 组装单业务逻辑：
 * - 成品入库：成品仓库增加库存
 * - BOM原料出库：原料仓库扣减库存
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class ProduceAssembleServiceImpl extends AbstractBillService<ProduceAssembleMapper, ProduceAssemble> implements ProduceAssembleService {

    static {
        // 列表页「表头字段过滤」白名单（2026-10-14 筛选条件自定义全面推广）。
        // 键 = 前端筛选条件定义键（与生产组装单明细报表引擎筛选键同键，汇总/明细两种模式一套参数）；
        // 值 = 实体属性名（registerProps 由 MyBatis-Plus 元数据解析成真实列名，防手写列名笔误）。
        // 仓库/币种：列是外键，按名称筛的下拉传的是主键 ID → Spec.column 写外键「属性名」。
        // 状态不入白名单：std 状态条件值走页面 search 专用入参（中英文案→数字码由 pageQuery 内转换），
        // 白名单若收 f_status 会拿中文与 integer 列等值比较直接 SQL 报错。
        // 单据日期 dateline 是 timestamp 且不登记：主日期走 std search 通道（后端已按「次日0点」口径处理），
        // 白名单 DATE 形态的 le(当天) 会截断到当天 0 点漏掉当天数据，不硬挂。
        java.util.Map<String, BillHeaderFilterSupport.Spec> f = new java.util.LinkedHashMap<>();
        // ── 文本模糊 ──
        f.put("serialNo", new BillHeaderFilterSupport.Spec("serialNo", BillHeaderFilterSupport.Kind.TEXT));
        f.put("dept", new BillHeaderFilterSupport.Spec("dept", BillHeaderFilterSupport.Kind.TEXT));
        f.put("taxCalcMethod", new BillHeaderFilterSupport.Spec("taxCalcMethod", BillHeaderFilterSupport.Kind.TEXT));
        f.put("sourceType", new BillHeaderFilterSupport.Spec("sourceType", BillHeaderFilterSupport.Kind.TEXT));
        f.put("salesmanStr", new BillHeaderFilterSupport.Spec("salesmanStr", BillHeaderFilterSupport.Kind.TEXT));
        f.put("approvalStatus", new BillHeaderFilterSupport.Spec("approvalStatus", BillHeaderFilterSupport.Kind.TEXT));
        f.put("notes", new BillHeaderFilterSupport.Spec("notes", BillHeaderFilterSupport.Kind.TEXT));
        // ── 精确（外键 ID）──
        f.put("whName", new BillHeaderFilterSupport.Spec("whId", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("whNameBom", new BillHeaderFilterSupport.Spec("whIdBom", BillHeaderFilterSupport.Kind.EQ_LONG));
        f.put("currencyCode", new BillHeaderFilterSupport.Spec("currencyId", BillHeaderFilterSupport.Kind.EQ_LONG));
        // ── 数值区间 ──
        f.put("amount", new BillHeaderFilterSupport.Spec("amount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("baseAmount", new BillHeaderFilterSupport.Spec("baseAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxAmount", new BillHeaderFilterSupport.Spec("taxAmount", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("taxRate", new BillHeaderFilterSupport.Spec("taxRate", BillHeaderFilterSupport.Kind.NUMBER));
        f.put("currencyRate", new BillHeaderFilterSupport.Spec("currencyRate", BillHeaderFilterSupport.Kind.NUMBER));
        BillHeaderFilterSupport.registerProps("produceAssemble", ProduceAssemble.class, f);
    }

    /** 列头排序白名单（数据库列名，防注入） */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "amount",
            "assemble_id",
            "base_amount",
            "created_at",
            "creator",
            "ctime",
            "currency_id",
            "currency_rate",
            "dateline",
            "dept",
            "etime",
            "files",
            "flow_id",
            "flow_operator",
            "flow_status",
            "flow_status_name",
            "id",
            "images",
            "is_tax",
            "modifier",
            "notes",
            "print_num",
            "produce_assemble",
            "reset_to_draft",
            "salesman",
            "salesman_str",
            "serial_no",
            "sort_order",
            "source_id",
            "source_type",
            "status",
            "tax_amount",
            "tax_rate",
            "tax_type",
            "utime",
            "wh_id",
            "wh_id_bom",
            "wh_name",
            "wh_name_bom"
        );

    private final ProduceAssembleDetailMapper detailMapper;
    private final ProduceAssembleBomMapper bomMapper;
    private final InventoryService inventoryService;
    private final SysSerialNoConfigService sysSerialNoConfigService;
    private final MaterialService materialService;
    private final WarehouseService warehouseService;
    private final CurrencyService currencyService;
    private final SysConfigService sysConfigService;

    /** 下游引用校验（重置草稿/作废/删除前禁止存在已生效下游） */
    @Autowired
    private BillRelationService billRelationService;

    /** 统一数据权限解析件：按角色 sys_role.data_scope（仅本人/本部门/全部）过滤 */
    @Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;
    private static final int SCALE_4 = 4;

    /** 明细计税类型：1=含税价 2=不含税价（默认） 3=不计税（2026-10-09 计税口径铺开，与销售订单试点同口径） */
    private static final int TAX_TYPE_INCLUSIVE = 1;
    private static final int TAX_TYPE_EXCLUSIVE = 2;
    private static final int TAX_TYPE_NO_TAX = 3;

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "生产组装单"; }
    @Override protected String getApprovalBillType() { return "PRODUCE_ASSEMBLE"; }
    @Override protected String getBillNo(ProduceAssemble b) { return b.getSerialNo(); }
    @Override protected Object getId(ProduceAssemble b) { return b.getId(); }
    @Override protected String getStatus(ProduceAssemble b) { return BillStatusUtils.toString(b.getStatus()); }
    @Override protected void setStatus(ProduceAssemble b, String s) { b.setStatus(BillStatusUtils.toNumber(s)); }
    @Override protected Integer getCloseStatus(ProduceAssemble b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(ProduceAssemble b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(ProduceAssemble b, String v) { b.setFlowOperator(v); }
    @Override protected void setApprovedAt(ProduceAssemble b, LocalDateTime v) { b.setEtime(v); }
    @Override protected void setApprovalStatus(ProduceAssemble b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(ProduceAssemble b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(ProduceAssemble b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(ProduceAssemble b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(ProduceAssemble b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(ProduceAssemble b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(ProduceAssemble b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(ProduceAssemble b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(ProduceAssemble b, String v) { b.setVoidReason(v); }

    @Override
    public List<String> listApprovedDownstreamBills(ProduceAssemble bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getSerialNo());
    }

    /** 提交审核前严格校验（原 submit 内的校验整体搬入） */
    @Override
    protected void validateStrictSubmit(ProduceAssemble assemble) {
        Long id = assemble.getId();
        List<ProduceAssembleDetail> details = detailMapper.selectList(
                new QueryWrapper<ProduceAssembleDetail>().eq("assemble_id", id));
        List<ProduceAssembleBom> boms = bomMapper.selectList(
                new QueryWrapper<ProduceAssembleBom>().eq("assemble_id", id));
        if (details == null || details.isEmpty()) {
            throw new BusinessException("没有成品明细，请至少添加一个成品后再提交");
        }
        if (boms == null || boms.isEmpty()) {
            throw new BusinessException("没有BOM原料明细，请至少添加一行原料后再提交");
        }
        if (assemble.getCurrencyId() != null && currencyService.getById(assemble.getCurrencyId()) == null) {
            throw new BusinessException("关联币种不存在(currencyId=" + assemble.getCurrencyId() + ")，请重新选择币种");
        }
        for (int i = 0; i < details.size(); i++) {
            ProduceAssembleDetail d = details.get(i);
            int lineNo = i + 1;
            if (d.getProductId() == null) {
                throw new BusinessException("成品明细第" + lineNo + "行产品ID不能为空");
            }
            Material p = materialService.getById(d.getProductId());
            if (p == null) {
                throw new BusinessException("成品明细第" + lineNo + "行产品不存在(productId=" + d.getProductId() + ")，请重新选择");
            }
            if (d.getWhId() == null) {
                throw new BusinessException("成品明细第" + lineNo + "行仓库ID不能为空");
            }
            if (warehouseService.getById(d.getWhId()) == null) {
                throw new BusinessException("成品明细第" + lineNo + "行仓库不存在(whId=" + d.getWhId() + ")，请重新选择仓库");
            }
            // 多单位：成品数量按「基本单位数量」（辅单位录入时行数量≠库存口径）
            BigDecimal qty = d.effectiveBaseQuantity() != null
                    ? d.effectiveBaseQuantity()
                    : (d.getQuantity() != null ? d.getQuantity() : BigDecimal.ZERO);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("成品明细第" + lineNo + "行数量必须大于0");
            }
        }
        boolean negativeAllowed = sysConfigService.getBooleanValue("inventory.negative.allowed", false);
        for (int i = 0; i < boms.size(); i++) {
            ProduceAssembleBom b = boms.get(i);
            int lineNo = i + 1;
            if (b.getProductId() == null) {
                throw new BusinessException("BOM原料第" + lineNo + "行产品ID不能为空");
            }
            Material m = materialService.getById(b.getProductId());
            if (m == null) {
                throw new BusinessException("BOM原料第" + lineNo + "行物料不存在(productId=" + b.getProductId() + ")，请重新选择物料");
            }
            if (b.getWhId() == null) {
                throw new BusinessException("BOM原料第" + lineNo + "行仓库ID不能为空");
            }
            Warehouse wh = warehouseService.getById(b.getWhId());
            if (wh == null) {
                throw new BusinessException("BOM原料第" + lineNo + "行仓库不存在(whId=" + b.getWhId() + ")，请重新选择仓库");
            }
            // 多单位：BOM原料出库按「基本单位数量」校验库存（辅单位录入时行数量≠库存口径）
            BigDecimal qty = b.effectiveBaseQuantity() != null
                    ? b.effectiveBaseQuantity()
                    : (b.getQuantity() != null ? b.getQuantity() : BigDecimal.ZERO);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("BOM原料第" + lineNo + "行数量必须大于0");
            }
            if (!negativeAllowed) {
                List<com.erp.inventory.entity.Inventory> invList = inventoryService.getByMaterialAndStock(b.getProductId(), b.getWhId());
                BigDecimal available = BigDecimal.ZERO;
                for (com.erp.inventory.entity.Inventory inv : invList) {
                    if (inv != null && inv.getAvailableQuantity() != null) {
                        available = available.add(inv.getAvailableQuantity());
                    }
                }
                if (available.compareTo(qty) < 0) {
                    String matLabel = (m.getMaterialCode() != null ? m.getMaterialCode() : "")
                            + " " + (m.getMaterialName() != null ? m.getMaterialName() : "");
                    String whName = wh.getStockName() != null ? wh.getStockName() : "ID=" + b.getWhId();
                    throw new BusinessException("BOM原料第" + lineNo + "行物料[" + matLabel.trim() + "]库存不足，仓库["
                            + whName + "]可用 " + available + "，需出库 " + qty);
                }
            }
        }
    }

    /** 审核生效影响：成品入库 + BOM原料出库 */
    @Override
    public void onApproved(ProduceAssemble assemble) {
        String operator = assemble.getFlowOperator();
        List<ProduceAssembleDetail> details = detailMapper.selectList(
                new QueryWrapper<ProduceAssembleDetail>().eq("assemble_id", assemble.getId()));
        for (ProduceAssembleDetail detail : details) {
            if (detail.getProductId() != null && detail.getWhId() != null) {
                inventoryService.addStock(detail.getWhId(), detail.getStorageId(), detail.getProductId(), null,
                        detail.getBasicQuantity() != null ? detail.getBasicQuantity() : detail.getQuantity(),
                        detail.getPrice(), assemble.getSerialNo(), "ASSEMBLE_IN", "produce_assemble",
                        assemble.getSerialNo(), operator);
            }
        }
        List<ProduceAssembleBom> boms = bomMapper.selectList(
                new QueryWrapper<ProduceAssembleBom>().eq("assemble_id", assemble.getId()));
        for (ProduceAssembleBom bom : boms) {
            if (bom.getProductId() != null && bom.getWhId() != null) {
                inventoryService.deductStock(bom.getWhId(), bom.getStorageId(), bom.getProductId(), null,
                        bom.getBasicQuantity() != null ? bom.getBasicQuantity() : bom.getQuantity(),
                        assemble.getSerialNo(), "ASSEMBLE_BOM_OUT", "produce_assemble",
                        assemble.getSerialNo(), operator);
            }
        }
        log.info("组装单审批通过(库存已释放): {} 审批人: {}", assemble.getSerialNo(), operator);
    }

    /** 重置草稿冲回：撤销成品入库 + 撤销原料出库（与 onApproved 严格镜像） */
    @Override
    public void onReverseApproved(ProduceAssemble assemble) {
        reverseInventory(assemble);
    }

    /** 作废冲回：按 onApproved 的反向口径撤销库存影响 */
    @Override
    public void onVoided(ProduceAssemble assemble) {
        reverseInventory(assemble);
    }

    /** 反作废恢复：恢复为已生效时，重新释放库存影响 */
    @Override
    public void onUnvoided(ProduceAssemble assemble) {
        onApproved(assemble);
    }

    private void reverseInventory(ProduceAssemble assemble) {
        String operator = assemble.getFlowOperator();
        List<ProduceAssembleDetail> details = detailMapper.selectList(
                new QueryWrapper<ProduceAssembleDetail>().eq("assemble_id", assemble.getId()));
        for (ProduceAssembleDetail detail : details) {
            if (detail.getProductId() != null && detail.getWhId() != null) {
                inventoryService.deductStock(detail.getWhId(), detail.getStorageId(), detail.getProductId(), null,
                        detail.getBasicQuantity() != null ? detail.getBasicQuantity() : detail.getQuantity(),
                        assemble.getSerialNo(), "ASSEMBLE_IN_CANCEL", "produce_assemble",
                        assemble.getSerialNo(), operator);
            }
        }
        List<ProduceAssembleBom> boms = bomMapper.selectList(
                new QueryWrapper<ProduceAssembleBom>().eq("assemble_id", assemble.getId()));
        for (ProduceAssembleBom bom : boms) {
            if (bom.getProductId() != null && bom.getWhId() != null) {
                inventoryService.addStock(bom.getWhId(), bom.getStorageId(), bom.getProductId(), null,
                        bom.getBasicQuantity() != null ? bom.getBasicQuantity() : bom.getQuantity(),
                        bom.getPrice(), assemble.getSerialNo(), "ASSEMBLE_BOM_OUT_CANCEL", "produce_assemble",
                        assemble.getSerialNo(), operator);
            }
        }
        log.info("组装单库存已冲回: {} 操作人: {}", assemble.getSerialNo(), operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveWithDetails(ProduceAssemble assemble) {
        boolean isNew = assemble.getId() == null;

        // 生成单据编号
        if (assemble.getSerialNo() == null || assemble.getSerialNo().isBlank()) {
            assemble.setSerialNo(generateSerialNo());
        }

        // 初始化默认值
        if (assemble.getStatus() == null) assemble.setStatus(0);
        if (assemble.getIsTax() == null) assemble.setIsTax(0);
        if (assemble.getTaxType() == null) assemble.setTaxType(0);
        if (assemble.getCurrencyRate() == null) assemble.setCurrencyRate(BigDecimal.ONE);
        if (assemble.getAmount() == null) assemble.setAmount(BigDecimal.ZERO);
        if (assemble.getBaseAmount() == null) assemble.setBaseAmount(BigDecimal.ZERO);
        if (assemble.getTaxAmount() == null) assemble.setTaxAmount(BigDecimal.ZERO);
        if (assemble.getPrintNum() == null) assemble.setPrintNum(0);
        if (assemble.getDateline() == null) assemble.setDateline(LocalDateTime.now());

        // 明细（保存=草稿宽松：原"成品明细不能为空"校验已移到提交时执行）
        List<ProduceAssembleDetail> details = assemble.getProduceAssembleDetail();
        List<ProduceAssembleBom> boms = assemble.getProduceAssembleBom();

        // 保存宽松：丢弃未选择物料的空行（product_id/product_code/product_name 数据库为 NOT NULL，无法落库）
        List<ProduceAssembleDetail> validDetails = new ArrayList<>();
        if (details != null) {
            for (ProduceAssembleDetail d : details) {
                if (d != null && d.getProductId() != null) validDetails.add(d);
            }
        }
        details = validDetails;
        List<ProduceAssembleBom> validBoms = new ArrayList<>();
        if (boms != null) {
            for (ProduceAssembleBom b : boms) {
                if (b != null && b.getProductId() != null) validBoms.add(b);
            }
        }
        boms = validBoms;

        // 重新计算金额（表头「计税方式」归一后逐行换算，2026-10-09）
        int headerTaxType = resolveHeaderTaxType(assemble.getTaxCalcMethod(), assemble.getIsTax(), assemble.getTaxType());
        recalculateAssemble(assemble, details, boms, headerTaxType);

        // 保存主表
        if (isNew) {
            // 新建草稿时同步 approval_status，避免该列留空（与全系统审批口径一致）
            if (assemble.getStatus() == 0 && assemble.getApprovalStatus() == null) assemble.setApprovalStatus("草稿");
            save(assemble);
        } else {
            // 删除旧明细
            detailMapper.delete(new QueryWrapper<ProduceAssembleDetail>().eq("assemble_id", assemble.getId()));
            bomMapper.delete(new QueryWrapper<ProduceAssembleBom>().eq("assemble_id", assemble.getId()));
            updateById(assemble);
        }

        // 保存明细
        int lineNo = 1;
        for (ProduceAssembleDetail detail : details) {
            detail.setId(null);
            detail.setAssembleId(assemble.getId());
            detail.setSortOrder(lineNo);
            calculateDetailAmount(detail, headerTaxType);
            detailMapper.insert(detail);
            lineNo++;
        }

        // 保存BOM明细
        lineNo = 1;
        if (boms != null && !boms.isEmpty()) {
            for (ProduceAssembleBom bom : boms) {
                bom.setId(null);
                bom.setAssembleId(assemble.getId());
                bom.setSortOrder(lineNo);
                calculateBomAmount(bom, headerTaxType);
                bomMapper.insert(bom);
                lineNo++;
            }
        }

        log.info("组装单保存成功: {} 状态: {} 金额: {}", assemble.getSerialNo(), assemble.getStatus(), assemble.getAmount());
    }

    /**
     * 表头「计税方式」归一：含税价=1、不含税价=2（默认）、不计税=3；兼容历史值 价内税→含税价、价外税→不含税价。
     * 新口径（taxCalcMethod）为空时回落老口径 is_tax/tax_type（未启用含税=不计税；tax_type=1=含税价），保证升级前后行为一致。
     */
    private int resolveHeaderTaxType(String taxCalcMethod, Integer isTax, Integer taxType) {
        if (taxCalcMethod != null && !taxCalcMethod.isBlank()) {
            String v = taxCalcMethod.trim();
            if ("不计税".equals(v)) return TAX_TYPE_NO_TAX;
            if ("含税价".equals(v) || "价内税".equals(v)) return TAX_TYPE_INCLUSIVE;
            return TAX_TYPE_EXCLUSIVE;
        }
        if (isTax != null && isTax == 0) return TAX_TYPE_NO_TAX;
        if (taxType != null && taxType == 1) return TAX_TYPE_INCLUSIVE;
        return TAX_TYPE_EXCLUSIVE;
    }

    /** 按表头「计税方式」把「单价」换算成 含税价 / 不含税价（返回值 [含税价, 不含税价]） */
    private BigDecimal[] convertPriceByTaxType(BigDecimal unitPrice, BigDecimal taxRate, int taxType) {
        BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        BigDecimal rate = taxRate != null ? taxRate : BigDecimal.ZERO;
        BigDecimal factor = BigDecimal.ONE.add(rate.divide(HUNDRED, 6, RoundingMode.HALF_UP));
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

    /**
     * 计算明细金额（含税价 / 不含税价两列 + 金额 + 税额）
     */
    private void calculateDetailAmount(ProduceAssembleDetail detail, int taxType) {
        BigDecimal price = detail.getPrice() != null ? detail.getPrice() : BigDecimal.ZERO;
        BigDecimal quantity = detail.getQuantity() != null ? detail.getQuantity() : BigDecimal.ZERO;
        BigDecimal taxRate = detail.getTaxRate() != null ? detail.getTaxRate() : BigDecimal.ZERO;

        // 保存=草稿宽松：数量/单价为负数等业务校验不在保存时执行，统一在提交时校验

        detail.setPrice(price);
        detail.setQuantity(quantity);
        detail.setTaxRate(taxRate);

        boolean noTax = taxType == TAX_TYPE_NO_TAX;
        boolean inclusive = taxType == TAX_TYPE_INCLUSIVE;
        BigDecimal taxFactor = BigDecimal.ONE.add(taxRate.divide(HUNDRED, 6, RoundingMode.HALF_UP));

        BigDecimal[] prices = convertPriceByTaxType(price, taxRate, taxType);
        detail.setPriceWithTax(prices[0]);
        detail.setPriceWithoutTax(prices[1]);

        // 折前金额一律按「不含税」口径
        BigDecimal amount = inclusive
                ? price.multiply(quantity).divide(taxFactor, SCALE, RoundingMode.HALF_UP)
                : price.multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP);
        detail.setAmount(amount);

        // 税额：不计税恒 0；含税价按「含税合计 − 不含税金额」倒算（无折扣，统一口径见 TaxAmountUtils）
        BigDecimal taxAmount = TaxAmountUtils.calcTaxAmount(
                amount, price, quantity, taxRate, BigDecimal.ZERO, inclusive, noTax);
        detail.setTaxAmount(taxAmount);
        // 价税合计 = 金额 + 税额（2026-11-23 补齐）
        detail.setNetAmount(amount.add(taxAmount).setScale(SCALE, RoundingMode.HALF_UP));
    }

    /**
     * 计算BOM明细金额（含税价 / 不含税价两列 + 金额 + 税额）
     */
    private void calculateBomAmount(ProduceAssembleBom bom, int taxType) {
        BigDecimal price = bom.getPrice() != null ? bom.getPrice() : BigDecimal.ZERO;
        BigDecimal quantity = bom.getQuantity() != null ? bom.getQuantity() : BigDecimal.ZERO;
        BigDecimal taxRate = bom.getTaxRate() != null ? bom.getTaxRate() : BigDecimal.ZERO;

        // 保存=草稿宽松：数量/单价为负数等业务校验不在保存时执行，统一在提交时校验

        bom.setPrice(price);
        bom.setQuantity(quantity);
        bom.setTaxRate(taxRate);

        boolean noTax = taxType == TAX_TYPE_NO_TAX;
        boolean inclusive = taxType == TAX_TYPE_INCLUSIVE;
        BigDecimal taxFactor = BigDecimal.ONE.add(taxRate.divide(HUNDRED, 6, RoundingMode.HALF_UP));

        BigDecimal[] prices = convertPriceByTaxType(price, taxRate, taxType);
        bom.setPriceWithTax(prices[0]);
        bom.setPriceWithoutTax(prices[1]);

        BigDecimal amount = inclusive
                ? price.multiply(quantity).divide(taxFactor, SCALE, RoundingMode.HALF_UP)
                : price.multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP);
        bom.setAmount(amount);

        // 税额：不计税恒 0；含税价按「含税合计 − 不含税金额」倒算（无折扣，统一口径见 TaxAmountUtils）
        BigDecimal taxAmount = TaxAmountUtils.calcTaxAmount(
                amount, price, quantity, taxRate, BigDecimal.ZERO, inclusive, noTax);
        bom.setTaxAmount(taxAmount);
        // 价税合计 = 金额 + 税额（2026-11-23 补齐）
        bom.setNetAmount(amount.add(taxAmount).setScale(SCALE, RoundingMode.HALF_UP));
    }

    /**
     * 重新计算组装单金额
     */
    private void recalculateAssemble(ProduceAssemble assemble, List<ProduceAssembleDetail> details,
                                     List<ProduceAssembleBom> boms, int taxType) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        if (details != null) {
            for (ProduceAssembleDetail detail : details) {
                calculateDetailAmount(detail, taxType);
                totalAmount = totalAmount.add(detail.getAmount());
                totalTax = totalTax.add(detail.getTaxAmount());
                totalNet = totalNet.add(detail.getNetAmount());
            }
        }

        if (boms != null) {
            for (ProduceAssembleBom bom : boms) {
                calculateBomAmount(bom, taxType);
                // BOM金额不计入总金额，仅用于成本核算
            }
        }

        assemble.setAmount(totalAmount.setScale(SCALE, RoundingMode.HALF_UP));
        assemble.setTaxAmount(totalTax.setScale(SCALE, RoundingMode.HALF_UP));
        assemble.setNetAmount(totalNet.setScale(SCALE, RoundingMode.HALF_UP));

        // 本位币换算
        BigDecimal rate = assemble.getCurrencyRate() != null ? assemble.getCurrencyRate() : BigDecimal.ONE;
        assemble.setBaseAmount(assemble.getAmount().multiply(rate).setScale(SCALE, RoundingMode.HALF_UP));
    }

    @Override
    public PageResult<ProduceAssemble> pageQuery(int current, int size, String keyword, String status,
                                                 String sortBy, String sortOrder,
                                                 String serialNo, String productName, String datelineStart, String datelineEnd, String customSearch,
                                                 String startDate, String endDate) {
        // 老签名委托新重载（headerFilters 空 = 行为与改造前完全一致）
        return pageQuery(current, size, keyword, status, sortBy, sortOrder,
                serialNo, productName, datelineStart, datelineEnd, customSearch, startDate, endDate,
                java.util.Collections.emptyMap());
    }

    /** 前端状态入参 → 数字状态码（兼容中文文案与数字串；无法识别返回 null = 不过滤，与状态机取值逐字一致） */
    private Integer statusCodeOf(String text) {
        if (text == null || text.isBlank()) return null;
        return switch (text.trim()) {
            case "草稿" -> 0;
            case "待审核" -> 1;
            case "已生效" -> 2;
            case "已作废", "作废" -> 7;
            default -> {
                try {
                    yield Integer.valueOf(text.trim());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
        };
    }

    /**
     * 在既有查询之上叠加「表头字段过滤」（2026-10-14 列表页筛选条件自定义全面推广）：
     * 白名单 + 参数化，非法/空白/白名单外入参在支持类内部忽略，不阻断列表查询。
     */
    @Override
    public PageResult<ProduceAssemble> pageQuery(int current, int size, String keyword, String status,
                                                 String sortBy, String sortOrder,
                                                 String serialNo, String productName, String datelineStart, String datelineEnd, String customSearch,
                                                 String startDate, String endDate, java.util.Map<String, String> headerFilters) {
        QueryWrapper<ProduceAssemble> wrapper = new QueryWrapper<>();
        // 排序：统一件 applySort（内部即 SortHelper 列名白名单），未传/非法时保留原有默认排序
        if (!ListQuerySupport.applySort(wrapper, sortBy, sortOrder, SORTABLE_COLUMNS)) SortHelper.orderByNewest(wrapper, ProduceAssemble.class);

        // 关键词：统一入参 keyword 与旧入参 serial_no 同义（都匹配单据编号），一律走 applyKeywordGroup 强制成组
        // （平铺 or() 会让命中第一列的行绕过后续状态筛选与数据权限）
        ListQuerySupport.applyKeywordGroup(wrapper, keyword, "serial_no");
        ListQuerySupport.applyKeywordGroup(wrapper, serialNo, "serial_no");
        // 成品名称：主表无该列，先用参数绑定(QueryWrapper.like 自动走 #{})查明细表拿到匹配的主单id列表，
        // 再 in；匹配为空时直接返回空页，避免退化成"返回全部"。
        if (productName != null && !productName.isBlank()) {
            List<Long> matchedIds = detailMapper.selectObjs(
                    new QueryWrapper<ProduceAssembleDetail>()
                            .select("DISTINCT assemble_id")
                            .like("product_name", productName)
            ).stream()
                    .filter(java.util.Objects::nonNull)
                    .map(o -> ((Number) o).longValue())
                    .collect(java.util.stream.Collectors.toList());
            if (matchedIds.isEmpty()) {
                return PageResult.of(java.util.Collections.emptyList(), 0, current, size);
            }
            wrapper.in("id", matchedIds);
        }
        // 单据日期区间：统一入参 startDate/endDate 优先，旧入参 dateline_start/dateline_end 兼容保留
        // dateline 是 LocalDateTime —— 起始端点走统一件 applyDateRange（>= 当天0点）；
        // 结束端点必须用「< 次日0点」：统一件的 le(当天) 会退化成"<= 当天0点"，把结束当天 00:00 之后的数据全部漏掉
        String dlStart = (startDate != null && !startDate.isBlank()) ? startDate : datelineStart;
        String dlEnd = (endDate != null && !endDate.isBlank()) ? endDate : datelineEnd;
        // 旧入参可能是 datetime 串，先按前10位归一化成 yyyy-MM-dd 再交给统一件（保证旧链接不失效）
        LocalDate dlStartDay = parseDateArg(dlStart);
        if (dlStartDay != null) ListQuerySupport.applyDateRange(wrapper, "dateline", dlStartDay.toString(), null);
        LocalDate dlEndDay = parseDateArg(dlEnd);
        if (dlEndDay != null) wrapper.lt("dateline", dlEndDay.plusDays(1).atStartOfDay());
        // 状态：中文文案/数字串 → 数字码等值过滤（2026-10-14 起入参收字符串，兼容旧数字串）
        Integer statusCode = statusCodeOf(status);
        if (statusCode != null) {
            wrapper.eq("status", statusCode);
        }
        // 表头字段过滤：f_* → 白名单翻译
        BillHeaderFilterSupport.apply(wrapper, "produceAssemble", headerFilters);
        // 数据权限（统一机制）：按角色 data_scope（仅本人/本部门/全部）过滤；无 Web 请求/全部数据时不加条件
        dataScopeResolver.applyIfLimited(wrapper, null, "creator");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "produce_assemble", customSearch, CustomFieldSearchHelper.idColumnOf("produce_assemble"));

        Page<ProduceAssemble> page = new Page<>(current, size);
        Page<ProduceAssemble> result = page(page, wrapper);

        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    /** 解析日期参数(取前10位 yyyy-MM-dd)，解析失败返回 null 以跳过该条件 */
    private LocalDate parseDateArg(String dateArg) {
        try {
            String d = dateArg.trim();
            if (d.length() > 10) d = d.substring(0, 10);
            return LocalDate.parse(d);
        } catch (Exception e) {
            log.warn("组装单日期查询参数解析失败，已忽略该条件: {}", dateArg);
            return null;
        }
    }

    @Override
    public ProduceAssemble getDetail(Long id) {
        ProduceAssemble assemble = getById(id);
        if (assemble == null) {
            throw new BusinessException("组装单不存在");
        }
        
        // 查询明细
        List<ProduceAssembleDetail> details = detailMapper.selectList(
            new QueryWrapper<ProduceAssembleDetail>().eq("assemble_id", id).orderByAsc("sort_order"));
        assemble.setProduceAssembleDetail(details);
        
        // 查询BOM明细
        List<ProduceAssembleBom> boms = bomMapper.selectList(
            new QueryWrapper<ProduceAssembleBom>().eq("assemble_id", id).orderByAsc("sort_order"));
        assemble.setProduceAssembleBom(boms);
        
        return assemble;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        // 提交校验 + 审批开关 + 免审批直通 + 创建审批待办，全部由统一状态机执行
        submitForApprovalById(id, com.erp.common.bill.BillApprovalAdapter.currentSubmitter());
    }

    /** 审核: 草稿/待审核 → 已生效（含「审批在途禁止绕过」防护，库存影响由 onApproved 释放） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("PRODUCE_ASSEMBLE", (Long) id) != null) {
            throw new BusinessException("生产组装单已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        ProduceAssemble assemble = getById(id);
        if (assemble == null) {
            throw new BusinessException("组装单不存在");
        }
        if (reason != null && !reason.isBlank()) {
            assemble.setNotes((assemble.getNotes() != null ? assemble.getNotes() + " | " : "") + "驳回原因: " + reason);
            updateById(assemble);
        }
        super.reject(id, null, reason);
    }

    /**
     * 撤销提交（待审核 → 草稿）：基类无「撤回」动作，保留本单据专用分支；
     * 状态仍通过钩子口径写入（Integer 状态码由 BillStatusUtils 统一换算）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id) {
        ProduceAssemble assemble = getById(id);
        if (assemble == null) {
            throw new BusinessException("组装单不存在");
        }
        if (!BillStatusUtils.isPending(assemble.getStatus())) {
            throw new BusinessException("只有待审批状态可撤销，当前状态: " + getStatus(assemble));
        }
        setStatus(assemble, "草稿");
        setApprovalStatus(assemble, "草稿");
        assemble.setFlowStatus("revoked");
        updateById(assemble);
        log.info("组装单撤销: {}", assemble.getSerialNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String reason) {
        // 「取消」与「作废」同义：统一走作废状态机
        voidBill(id, null, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetToDraft(Long id) {
        reverseApprove(id, null);
    }

    @Override
    public String generateSerialNo() {
        String docType = "PRODUCE_ASSEMBLE";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "AS-" + dateStr + "-";
            QueryWrapper<ProduceAssemble> wrapper = new QueryWrapper<>();
            wrapper.likeRight("serial_no", prefix).orderByDesc("serial_no").last("LIMIT 1");
            ProduceAssemble last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getSerialNo() != null) {
                try {
                    seq = Integer.parseInt(last.getSerialNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    /** 重置草稿: 已生效 → 草稿（状态/下游校验由基类执行，库存冲回由 onReverseApproved 执行） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverseApprove(Long id, String operator) {
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人（flow_operator）/生效时间（etime）
        UpdateWrapper<ProduceAssemble> uw = new UpdateWrapper<>();
        uw.eq("id", id).set("flow_operator", null).set("etime", null);
        update(uw);
    }

    /** 作废: 本单据守卫保留「已生效不可直接作废（须先重置草稿）」，其余交统一状态机 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidBill(Long id, String operator, String reason) {
        ProduceAssemble assemble = getById(id);
        if (assemble == null) {
            throw new BusinessException("组装单不存在");
        }
        if (BillStatusUtils.isApprovedClass(assemble.getStatus())) {
            throw new BusinessException("已生效的单据不能作废，请先重置草稿");
        }
        if (BillStatusUtils.isVoided(assemble.getStatus())) {
            throw new BusinessException("单据已作废");
        }
        if (reason != null && !reason.isBlank()) {
            assemble.setNotes((assemble.getNotes() != null ? assemble.getNotes() + " | " : "") + "作废原因: " + reason);
            updateById(assemble);
        }
        super.voidBill(id, operator, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }

    /**
     * 获取状态名称（保留给列表/提示文案使用）
     */
    private String getStatusName(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "草稿";
            case 1: return "待审批";
            case 7: return "已作废";
            case 2: return "已生效";
            default: return "未知";
        }
    }

    /** 结案: 已生效 → close_status=1 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeOrder(Long id, String operator) {
        super.close(id, operator, "手动结案");
    }

    /** 反结案: close_status=0 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reopenOrder(Long id, String operator) {
        super.reopen(id, operator);
    }

    /** 删除组装单(仅草稿族可删且无下游已生效单据, 连同明细/ BOM行一起删除) */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBill(Long id) {
        super.deleteBill(id);
    }

    /** 复制单据: 读原单+明细生成新草稿, 返回新单(含新id) */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProduceAssemble copyBill(Long id) {
        ProduceAssemble source = getDetail(id);
        String sourceSerialNo = source.getSerialNo();
        // 清空主键与单号，重置为草稿态
        source.setId(null);
        source.setSerialNo(null);
        source.setStatus(0);
        source.setApprovalStatus("草稿");
        source.setCloseStatus(0);
        source.setClosedBy(null);
        source.setClosedAt(null);
        source.setNotes("复制自: " + sourceSerialNo);
        // 清空明细主键，重挂到新主单
        if (source.getProduceAssembleDetail() != null) {
            source.getProduceAssembleDetail().forEach(d -> d.setId(null));
        }
        if (source.getProduceAssembleBom() != null) {
            source.getProduceAssembleBom().forEach(b -> b.setId(null));
        }
        saveWithDetails(source);
        log.info("组装单复制: 新单 {} 来源单: {}", source.getSerialNo(), sourceSerialNo);
        return source;
    }
}