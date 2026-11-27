package com.erp.price.sale.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.common.bill.AbstractBillService;
import com.erp.common.bill.BillApprovalAdapter;
import com.erp.common.bill.BillRelationService;
import com.erp.common.bill.BillStatusEnum;
import com.erp.common.bill.BillStatusUtils;
import com.erp.config.BusinessException;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.mapper.CustomerMapper;
import com.erp.masterdata.mapper.MaterialMapper;
import com.erp.price.sale.entity.SalePriceStrategy;
import com.erp.price.sale.entity.SalePriceStrategyItem;
import com.erp.price.sale.mapper.SalePriceStrategyItemMapper;
import com.erp.price.sale.mapper.SalePriceStrategyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalePriceStrategyServiceImpl extends AbstractBillService<SalePriceStrategyMapper, SalePriceStrategy>
        implements SalePriceStrategyService {

    private final SalePriceStrategyItemMapper itemMapper;
    private final CustomerMapper customerMapper;
    private final MaterialMapper materialMapper;

    /** 统一数据权限解析器：按角色 data_scope（仅本人/本部门/全部数据）过滤，创建人列 created_by */
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 下游引用校验（统一状态机）：重置草稿/作废/删除前禁止存在已生效下游 */
    @Lazy
    @Autowired
    private BillRelationService billRelationService;

    // ================= 统一状态机基类钩子（AbstractBillService） =================
    @Override protected String getBillTypeLabel() { return "销售价格策略"; }
    @Override protected String getApprovalBillType() { return "SALE_PRICE_STRATEGY"; }
    @Override protected String getBillNo(SalePriceStrategy b) { return b.getStrategyCode(); }
    @Override protected Object getId(SalePriceStrategy b) { return b.getId(); }
    @Override protected String getStatus(SalePriceStrategy b) { return BillStatusUtils.toString(b.getStatus()); }
    @Override protected void setStatus(SalePriceStrategy b, String s) { b.setStatus(BillStatusUtils.toNumber(s)); }
    @Override protected Integer getCloseStatus(SalePriceStrategy b) { return b.getCloseStatus(); }
    @Override protected void setCloseStatus(SalePriceStrategy b, Integer v) { b.setCloseStatus(v); }
    @Override protected void setApprovedBy(SalePriceStrategy b, String v) { b.setApprovedBy(v); }
    @Override protected void setApprovedAt(SalePriceStrategy b, LocalDateTime v) { b.setApprovedAt(v); }
    @Override protected void setApprovalStatus(SalePriceStrategy b, String v) { b.setApprovalStatus(v); }
    @Override protected void setClosedBy(SalePriceStrategy b, String v) { b.setClosedBy(v); }
    @Override protected void setClosedAt(SalePriceStrategy b, LocalDateTime v) { b.setClosedAt(v); }
    @Override protected void setClosedReason(SalePriceStrategy b, String v) { b.setClosedReason(v); }
    @Override protected void setBillBeforeStatus(SalePriceStrategy b, Integer v) { b.setBillBeforeStatus(v); }
    @Override protected Integer getBillBeforeStatus(SalePriceStrategy b) { return b.getBillBeforeStatus(); }
    @Override protected void setVoidedBy(SalePriceStrategy b, String v) { b.setVoidedBy(v); }
    @Override protected void setVoidedAt(SalePriceStrategy b, LocalDateTime v) { b.setVoidedAt(v); }
    @Override protected void setVoidReason(SalePriceStrategy b, String v) { b.setVoidReason(v); }

    /** 下游引用校验：统一状态机在重置草稿/作废/删除前调用 */
    @Override
    public List<String> listApprovedDownstreamBills(SalePriceStrategy bill) {
        return billRelationService.listApprovedDownstream(getBillTypeLabel(), bill.getStrategyCode());
    }

    /** 审核通过影响：策略置「已生效」后即参与取价（取价只匹配已生效策略，见 matchStrategyPrice） */
    @Override
    public void onApproved(SalePriceStrategy strategy) {
        log.info("销售价格策略审核生效: {} 审核人: {}", strategy.getStrategyCode(), strategy.getApprovedBy());
    }

    @Override
    public PageResult<SalePriceStrategy> listPage(int current, int size, String keyword) {
        QueryWrapper<SalePriceStrategy> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            // 2026-10-10：策略名称已从界面去掉，列表统一按「策略编码」检索
            wrapper.and(w -> w.like("strategy_code", keyword));
        }
        // 数据权限（按角色 data_scope：仅本人/本部门/全部数据）—— 创建人列 created_by，total 随过滤变化
        dataScopeResolver.applyIfLimited(wrapper, null, "created_by");
        wrapper.orderByDesc("priority");
        Page<SalePriceStrategy> page = new Page<>(current, size);
        Page<SalePriceStrategy> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public SalePriceStrategy getById(Long id) {
        SalePriceStrategy strategy = baseMapper.selectById(id);
        if (strategy == null) {
            throw new BusinessException("销售价格策略不存在");
        }
        List<SalePriceStrategyItem> items = itemMapper.selectList(
                new QueryWrapper<SalePriceStrategyItem>().eq("strategy_id", id));
        strategy.setItems(items);
        return strategy;
    }

    @Override
    @Transactional
    public void save(SalePriceStrategy strategy, List<SalePriceStrategyItem> items) {
        validate(strategy, items, null);
        // 保存=草稿（统一状态机）：状态只由提交/审核链路推进，保存不接受前端传入的状态值
        strategy.setStatus(BillStatusEnum.DRAFT.getCode());
        strategy.setApprovalStatus(BillStatusEnum.DRAFT.getLabel());
        // 2026-10-10：优先级默认沿用「下一个可用号」，适用范围默认「按物料」，编码由系统自动生成
        if (strategy.getPriority() == null) {
            strategy.setPriority(nextPriority());
        }
        if (strategy.getTargetType() == null || strategy.getTargetType().isBlank()) {
            strategy.setTargetType("material");
        }
        if (strategy.getStrategyCode() == null || strategy.getStrategyCode().isBlank()) {
            strategy.setStrategyCode(generateStrategyCode());
        }
        // 策略名称已从界面去掉（列表列名保留兼容），统一回写为编码
        if (strategy.getStrategyName() == null || strategy.getStrategyName().isBlank()) {
            strategy.setStrategyName(strategy.getStrategyCode());
        }
        save(strategy);
        insertItems(strategy.getId(), strategy.getTargetType(), items);
        log.info("新增销售价格策略: {}", strategy.getStrategyCode());
    }

    @Override
    @Transactional
    public void update(SalePriceStrategy strategy, List<SalePriceStrategyItem> items) {
        SalePriceStrategy existing = baseMapper.selectById(strategy.getId());
        if (existing == null) {
            throw new BusinessException("销售价格策略不存在");
        }
        validate(strategy, items, strategy.getId());
        // 编辑不得改动主状态：状态只由统一状态机推进（前端传值一律忽略）
        strategy.setStatus(existing.getStatus());
        if (strategy.getTargetType() == null || strategy.getTargetType().isBlank()) {
            strategy.setTargetType(existing.getTargetType() == null ? "material" : existing.getTargetType());
        }
        if (strategy.getPriority() == null) {
            strategy.setPriority(existing.getPriority());
        }
        if (strategy.getStrategyCode() == null || strategy.getStrategyCode().isBlank()) {
            strategy.setStrategyCode(existing.getStrategyCode());
        }
        if (strategy.getStrategyName() == null || strategy.getStrategyName().isBlank()) {
            strategy.setStrategyName(strategy.getStrategyCode());
        }
        updateById(strategy);
        itemMapper.delete(new QueryWrapper<SalePriceStrategyItem>().eq("strategy_id", strategy.getId()));
        insertItems(strategy.getId(), strategy.getTargetType(), items);
        log.info("修改销售价格策略: {}", strategy.getStrategyCode());
    }

    /** 允许的适用范围：material 按物料 / category 按商品分类 / customer 按客户 */
    private static final java.util.Set<String> TARGET_TYPES = java.util.Set.of("material", "category", "customer");

    /** 保存前校验：适用范围合法 / 明细非空且每行都有对象与价格 / 优先级不重复 */
    private void validate(SalePriceStrategy strategy, List<SalePriceStrategyItem> items, Long excludeId) {
        String tt = strategy.getTargetType();
        if (tt != null && !tt.isBlank() && !TARGET_TYPES.contains(tt)) {
            throw new BusinessException("适用范围不合法：" + tt);
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException("价格明细不能为空，请至少添加一条");
        }
        for (int i = 0; i < items.size(); i++) {
            SalePriceStrategyItem it = items.get(i);
            if (it.getTargetId() == null) {
                throw new BusinessException("价格明细第" + (i + 1) + "行未选择适用对象");
            }
            if (it.getPriceValue() == null) {
                throw new BusinessException("价格明细第" + (i + 1) + "行价格值不能为空");
            }
        }
        Integer priority = strategy.getPriority();
        if (priority != null) {
            QueryWrapper<SalePriceStrategy> qw = new QueryWrapper<>();
            qw.eq("priority", priority);
            if (excludeId != null) {
                qw.ne("id", excludeId);
            }
            Long used = baseMapper.selectCount(qw);
            if (used != null && used > 0) {
                throw new BusinessException("优先级 " + priority + " 已被其它策略占用，请换一个数字");
            }
        }
    }

    /** 明细落库：统一写 target_type；按物料时 material_id 与 target_id 同源（保留旧列与引用检查） */
    private void insertItems(Long strategyId, String targetType, List<SalePriceStrategyItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        String tt = (targetType == null || targetType.isBlank()) ? "material" : targetType;
        for (SalePriceStrategyItem item : items) {
            item.setId(null);
            item.setStrategyId(strategyId);
            item.setTargetType(tt);
            item.setMaterialId("material".equals(tt) ? item.getTargetId() : null);
            itemMapper.insert(item);
        }
    }

    /** 下一个可用优先级（现有最大优先级 + 1，空表返回 1）：保证新增策略不与现有策略重复 */
    @Override
    public int nextPriority() {
        SalePriceStrategy last = baseMapper.selectOne(new QueryWrapper<SalePriceStrategy>()
                .select("priority").orderByDesc("priority").last("LIMIT 1"));
        return (last == null || last.getPriority() == null) ? 1 : last.getPriority() + 1;
    }

    /** 策略编码自动生成：SP + yyyyMMdd + 4 位流水（同一天内自增，确保不重复） */
    private String generateStrategyCode() {
        String prefix = "SP" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        for (int i = 1; i <= 9999; i++) {
            String code = prefix + String.format("%04d", i);
            Long cnt = baseMapper.selectCount(new QueryWrapper<SalePriceStrategy>().eq("strategy_code", code));
            if (cnt == null || cnt == 0) {
                return code;
            }
        }
        throw new BusinessException("策略编码生成失败，请稍后重试");
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SalePriceStrategy existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("销售价格策略不存在");
        }
        itemMapper.delete(new QueryWrapper<SalePriceStrategyItem>().eq("strategy_id", id));
        // 【2026-09-19 整单删除】先清明细/从表，再删主表（用户硬要求：不允许留孤儿明细）
        billCascadeDeleteService.deleteChildren("sale_price_strategy", id);
        removeById(id);
        log.info("删除销售价格策略: {} {}", existing.getStrategyCode(), existing.getStrategyName());
    }

    /** 启用/禁用：统一状态机口径 —— 启用=审核生效，禁用=重置草稿/驳回，不再直接改写状态列 */
    @Override
    @Transactional
    public void toggleStatus(Long id) {
        SalePriceStrategy strategy = baseMapper.selectById(id);
        if (strategy == null) {
            throw new BusinessException("销售价格策略不存在");
        }
        int code = BillStatusUtils.toNumber(strategy.getStatus());
        String operator = BillApprovalAdapter.currentSubmitter();
        if (code == BillStatusEnum.APPROVED.getCode()) {
            reverseApprove(id, operator);
        } else if (code == BillStatusEnum.PENDING.getCode()) {
            reject(id, operator, "禁用价格策略");
        } else if (BillStatusUtils.isVoided(strategy.getStatus())) {
            throw new BusinessException("销售价格策略已作废，不能启用或禁用");
        } else {
            approve((Object) id, operator);
        }
        log.info("切换销售价格策略状态: {} 原状态: {}", strategy.getStrategyCode(), BillStatusUtils.toString(code));
    }

    @Override
    public BigDecimal calculatePrice(Long customerId, Long materialId, BigDecimal quantity, String taxType) {
        BigDecimal matched = matchStrategyPrice(customerId, materialId, quantity, taxType);
        if (matched != null) {
            return matched;
        }
        Material material = materialMapper.selectById(materialId);
        if (material == null) {
            throw new BusinessException("商品不存在");
        }
        return material.getSalePrice();
    }

    @Override
    public BigDecimal tryMatchPrice(Long customerId, Long materialId, BigDecimal quantity, String taxType) {
        return matchStrategyPrice(customerId, materialId, quantity, taxType);
    }

    /**
     * 按策略匹配算价：命中返回价格，未命中返回 null（不抛异常，交给上层按取价优先级回落）。
     *
     * <p>2026-10-10 改造：适用范围改为"一处互斥单选"，命中条件由明细行的对象决定——
     * 按物料比 materialId、按商品分类比物料的分类、按客户比客户ID；主表只保留状态/生效日期/数量区间三个通用条件。
     */
    private BigDecimal matchStrategyPrice(Long customerId, Long materialId, BigDecimal quantity, String taxType) {
        if (materialId == null) {
            return null;
        }
        Material material = materialMapper.selectById(materialId);
        if (material == null) {
            return null;
        }

        LocalDate today = LocalDate.now();
        BigDecimal qty = quantity != null ? quantity : BigDecimal.ONE;

        QueryWrapper<SalePriceStrategy> wrapper = new QueryWrapper<>();
        // 取价只认「已生效」策略（统一状态机编码 2）：草稿/待审核/已作废一律不参与取价
        wrapper.eq("status", BillStatusEnum.APPROVED.getCode());
        wrapper.and(w -> w.isNull("start_date").or().le("start_date", today));
        wrapper.and(w -> w.isNull("end_date").or().ge("end_date", today));
        // 数量区间已下沉到明细行（每个对象自己的区间），主表区间仅作存量兜底 → 不在主表过滤
        wrapper.orderByDesc("priority");

        List<SalePriceStrategy> strategies = list(wrapper);
        if (strategies.isEmpty()) {
            return null;
        }

        strategies.sort(Comparator.comparing(SalePriceStrategy::getPriority).reversed());

        for (SalePriceStrategy strategy : strategies) {
            String targetType = (strategy.getTargetType() == null || strategy.getTargetType().isBlank())
                    ? "material" : strategy.getTargetType();
            Long targetId = switch (targetType) {
                case "category" -> material.getCategoryId();
                case "customer" -> customerId;
                default -> materialId;
            };
            if (targetId == null) {
                continue;
            }

            SalePriceStrategyItem item = findItem(strategy.getId(), targetType, targetId);
            BigDecimal finalPrice;
            if (item != null) {
                // 数量区间按「该对象的明细行」判断（为空=不限）
                if (!inRange(item.getMinQuantity(), item.getMaxQuantity(), qty)) {
                    continue;
                }
                finalPrice = calculateItemPrice(item, material);
            } else if (strategy.getPriceValue() != null && !hasAnyItem(strategy.getId())) {
                // 兼容改造前"只配了表头价、没有明细"的老策略（新界面已禁止无明细保存），区间走主表
                if (!inRange(strategy.getMinQuantity(), strategy.getMaxQuantity(), qty)) {
                    continue;
                }
                BigDecimal costPrice = material.getAvgCost() != null ? material.getAvgCost() : material.getPurchasePrice();
                finalPrice = calculateFinalPrice(strategy.getPriceType(), strategy.getPriceValue(), null, material.getSalePrice(), costPrice);
            } else {
                continue;
            }
            if (finalPrice == null) {
                continue;
            }

            if ("exclusive".equals(taxType) && material.getTaxRate() != null) {
                finalPrice = finalPrice.divide(BigDecimal.ONE.add(material.getTaxRate().divide(BigDecimal.valueOf(100))), 2, BigDecimal.ROUND_HALF_UP);
            }

            return finalPrice;
        }

        return null;
    }

    /** 按「对象类型 + 对象ID」取该策略下命中的明细行；按物料时兼容尚未回填 target_id 的旧行 */
    private SalePriceStrategyItem findItem(Long strategyId, String targetType, Long targetId) {
        if (targetId == null) {
            return null;
        }
        SalePriceStrategyItem item = itemMapper.selectOne(
                new QueryWrapper<SalePriceStrategyItem>()
                        .eq("strategy_id", strategyId)
                        .eq("target_type", targetType)
                        .eq("target_id", targetId)
                        .last("LIMIT 1"));
        if (item == null && "material".equals(targetType)) {
            item = itemMapper.selectOne(
                    new QueryWrapper<SalePriceStrategyItem>()
                            .eq("strategy_id", strategyId)
                            .eq("material_id", targetId)
                            .last("LIMIT 1"));
        }
        return item;
    }

    /** 数量是否落在明细行的区间内（区间为空 = 不限） */
    private boolean inRange(BigDecimal min, BigDecimal max, BigDecimal qty) {
        if (min != null && qty.compareTo(min) < 0) {
            return false;
        }
        if (max != null && qty.compareTo(max) > 0) {
            return false;
        }
        return true;
    }

    private boolean hasAnyItem(Long strategyId) {
        Long cnt = itemMapper.selectCount(
                new QueryWrapper<SalePriceStrategyItem>().eq("strategy_id", strategyId));
        return cnt != null && cnt > 0;
    }

    private BigDecimal calculateItemPrice(SalePriceStrategyItem item, Material material) {
        BigDecimal costPrice = item.getCostPrice();
        if (costPrice == null) {
            costPrice = material.getAvgCost() != null ? material.getAvgCost() : material.getPurchasePrice();
        }
        return calculateFinalPrice(item.getPriceType(), item.getPriceValue(), item.getGrossProfitRate(), material.getSalePrice(), costPrice);
    }

    private BigDecimal calculateFinalPrice(String priceType, BigDecimal priceValue, BigDecimal grossProfitRate, BigDecimal basePrice, BigDecimal costPrice) {
        if (priceType == null || priceType.isBlank()) {
            return basePrice;
        }
        BigDecimal bp = basePrice != null ? basePrice : BigDecimal.ZERO;
        BigDecimal cp = costPrice != null ? costPrice : BigDecimal.ZERO;
        BigDecimal pv = priceValue != null ? priceValue : BigDecimal.ZERO;
        return switch (priceType) {
            case "fixed", "固定价" -> pv;
            case "discount", "折扣率" -> bp.multiply(pv).divide(BigDecimal.valueOf(100), 4, BigDecimal.ROUND_HALF_UP);
            case "markup", "加价率" -> bp.add(bp.multiply(pv).divide(BigDecimal.valueOf(100), 4, BigDecimal.ROUND_HALF_UP));
            case "gross_profit", "毛利率" -> {
                BigDecimal rate = grossProfitRate != null ? grossProfitRate : pv;
                yield cp.add(cp.multiply(rate).divide(BigDecimal.valueOf(100), 4, BigDecimal.ROUND_HALF_UP));
            }
            default -> bp;
        };
    }

    /** 审核：审批引擎防绕过 + 状态迁移由统一状态机执行 */
    @Override
    @Transactional
    public void approve(Object id, String approver) {
        if (approvalRuntimeService.getRunningTask("SALE_PRICE_STRATEGY", (Long) id) != null) {
            throw new BusinessException("销售价格策略已提交审批流程，请由指定审批人在审批中心处理");
        }
        super.approve(id, approver != null && !approver.isBlank() ? approver : "系统(自动通过)");
    }

    @Transactional
    public void approve(Long id, String approver) {
        approve((Object) id, approver);
    }

    /** 提交前严格校验：适用范围 / 明细非空且有对象与价格 / 优先级唯一（与保存共用同一套校验） */
    @Override
    protected void validateStrictSubmit(SalePriceStrategy strategy) {
        List<SalePriceStrategyItem> items = itemMapper.selectList(
                new QueryWrapper<SalePriceStrategyItem>().eq("strategy_id", strategy.getId()));
        validate(strategy, items, strategy.getId());
    }

    /** 驳回：待审核 → 草稿（状态校验/审批引擎防绕过由统一状态机执行） */
    @Override
    @Transactional
    public void reject(Long id, String rejector, String reason) {
        super.reject(id, rejector, reason);
    }

    /** 重置草稿：已生效 → 草稿（状态校验/下游校验由统一状态机执行） */
    @Override
    @Transactional
    public void reverseApprove(Long id, String operator) {
        super.reverseApprove(id, operator);
        // MyBatis-Plus 忽略 null 更新：显式清空审核人/审核时间
        update(new UpdateWrapper<SalePriceStrategy>().eq("id", id)
                .set("approved_by", null).set("approved_at", null));
        log.info("重置草稿销售价格策略: {} 操作人: {}", id, operator);
    }

    /** 作废：状态校验/下游校验/作废前状态记录由统一状态机执行 */
    @Override
    @Transactional
    public void voidBill(Long id, String operator, String reason) {
        super.voidBill(id, operator, reason);
    }

    /** 反作废：恢复作废前状态，不重复释放业务影响 */
    @Override
    @Transactional
    public void unvoidBill(Long id, String operator) {
        super.unvoidBill(id, operator);
    }
}
