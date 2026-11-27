package com.erp.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.crm.entity.Opportunity;
import com.erp.crm.mapper.OpportunityMapper;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.service.CustomerService;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.CustomFieldSearchHelper;
import com.erp.common.util.SortHelper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor

public class OpportunityServiceImpl extends ServiceImpl<OpportunityMapper, Opportunity> implements OpportunityService {

    @Autowired
    @Lazy
    private CustomerService customerService;

    private final SysSerialNoConfigService sysSerialNoConfigService;

    /** 阶段-概率映射 */
    private static final Map<String, Integer> STAGE_PROBABILITY = new HashMap<>();
    static {
        STAGE_PROBABILITY.put("初步接触", 10);
        STAGE_PROBABILITY.put("需求确认", 25);
        STAGE_PROBABILITY.put("方案报价", 45);
        STAGE_PROBABILITY.put("商务谈判", 70);
        STAGE_PROBABILITY.put("成交", 100);
        STAGE_PROBABILITY.put("已关闭", 0);
    }

    @Override
    public PageResult<Opportunity> pageQuery(int current, int size, String keyword, String salesStage,
                                              Long customerId, Long assignedSalesId, Integer isClosed, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder, String customSearch) {
        QueryWrapper<Opportunity> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("opportunity_no", keyword)
                    .or().like("opportunity_name", keyword)
                    .or().like("customer_name", keyword));
        }
        if (salesStage != null && !salesStage.isBlank()) {
            wrapper.eq("sales_stage", salesStage);
        }
        if (customerId != null) {
            wrapper.eq("customer_id", customerId);
        }
        if (assignedSalesId != null) {
            wrapper.eq("assigned_sales_id", assignedSalesId);
        }
        if (isClosed != null) {
            wrapper.eq("is_closed", isClosed);
        }
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己负责/创建的；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "assigned_sales_id");
        // 自定义字段搜索（设置中心勾「可搜索」的字段；为空/非法一律忽略，不阻断列表查询）
        CustomFieldSearchHelper.apply(wrapper, "opportunity", customSearch, CustomFieldSearchHelper.idColumnOf("opportunity"));
        Page<Opportunity> page = new Page<>(current, size);
        Page<Opportunity> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public Opportunity getDetail(Long id) {
        Opportunity opp = getById(id);
        if (opp == null) throw new BusinessException("商机不存在");
        return opp;
    }

    @Override
    @Transactional
    public void saveOpportunity(Opportunity opp) {
        // 输入验证
        if (opp.getOpportunityName() == null || opp.getOpportunityName().isBlank()) {
            throw new BusinessException("商机名称不能为空");
        }

        if (opp.getOpportunityNo() == null || opp.getOpportunityNo().isBlank()) {
            opp.setOpportunityNo(generateOpportunityNo());
        }

        if (opp.getSalesStage() == null || opp.getSalesStage().isBlank()) {
            opp.setSalesStage("初步接触");
        }
        if (opp.getIsClosed() == null) {
            opp.setIsClosed(0);
        }
        if (opp.getEstimatedAmount() == null) {
            opp.setEstimatedAmount(BigDecimal.ZERO);
        }
        if (opp.getFollowUpCount() == null) {
            opp.setFollowUpCount(0);
        }

        // 根据阶段设置概率（成交/已关闭阶段除外，由专门流程处理）
        if (opp.getProbability() == null) {
            Integer prob = STAGE_PROBABILITY.get(opp.getSalesStage());
            opp.setProbability(prob != null ? prob : 10);
        }

        save(opp);
        log.info("商机保存成功: {} 阶段: {}", opp.getOpportunityNo(), opp.getSalesStage());
    }

    @Override
    @Transactional
    public void updateSalesStage(Long id, String stage, String winReason, String loseReason) {
        if (stage == null || stage.isBlank()) {
            throw new BusinessException("销售阶段不能为空");
        }
        if (!STAGE_PROBABILITY.containsKey(stage)) {
            throw new BusinessException("无效的销售阶段: " + stage);
        }
        Opportunity opp = getById(id);
        if (opp == null) throw new BusinessException("商机不存在");

        // 已关闭的商机不允许变更阶段
        if (opp.getIsClosed() != null && opp.getIsClosed() == 1) {
            throw new BusinessException("商机已关闭，不允许变更阶段");
        }

        opp.setSalesStage(stage);
        opp.setStageUpdateDate(LocalDate.now());

        // 成交/已关闭阶段不自动覆盖概率，由专门流程处理
        if (!"成交".equals(stage) && !"已关闭".equals(stage)) {
            opp.setProbability(STAGE_PROBABILITY.get(stage));
        }

        if ("成交".equals(stage)) {
            opp.setProbability(STAGE_PROBABILITY.get(stage));
            opp.setActualCloseDate(LocalDate.now());
            if (winReason != null && !winReason.isBlank()) {
                opp.setWinReason(winReason);
            }
            // 商机成交 → 关联客户自动升级为"正式客户"（CRM正式客户流转到客户档案）
            upgradeCustomerToFormal(opp.getCustomerId());
        }

        if ("已关闭".equals(stage)) {
            opp.setIsClosed(1);
            opp.setCloseDate(LocalDate.now());
            opp.setProbability(0);
            if (loseReason != null && !loseReason.isBlank()) {
                opp.setLoseReason(loseReason);
            }
        }

        updateById(opp);
        log.info("商机阶段更新: {} → {}", opp.getOpportunityNo(), stage);
    }

    @Override
    @Transactional
    public void closeOpportunity(Long id, boolean won, String reason) {
        Opportunity opp = getById(id);
        if (opp == null) throw new BusinessException("商机不存在");
        if (opp.getIsClosed() != null && opp.getIsClosed() == 1) {
            throw new BusinessException("商机已关闭，不可重复关闭");
        }

        opp.setIsClosed(1);
        opp.setCloseDate(LocalDate.now());
        opp.setSalesStage("已关闭");
        opp.setProbability(0);

        if (won) {
            opp.setWinReason(reason);
            opp.setActualCloseDate(LocalDate.now());
            // 商机赢单成交 → 关联客户自动升级为"正式客户"
            upgradeCustomerToFormal(opp.getCustomerId());
        } else {
            opp.setLoseReason(reason);
        }

        updateById(opp);
        log.info("商机关闭: {} 赢单: {} 原因: {}", opp.getOpportunityNo(), won, reason);
    }

    @Override
    @Transactional
    public void markWonBySaleOrder(Long opportunityId, Long saleOrderId, String saleOrderNo, java.math.BigDecimal actualAmount) {
        if (opportunityId == null || saleOrderId == null) {
            log.warn("markWonBySaleOrder 参数为空: opportunityId={}, saleOrderId={}", opportunityId, saleOrderId);
            return;
        }
        Opportunity opp = getById(opportunityId);
        if (opp == null) {
            log.warn("商机不存在: opportunityId={}", opportunityId);
            return;
        }

        // 记录成交订单信息（无论商机当前处于什么阶段都更新）
        opp.setSaleOrderId(saleOrderId);
        opp.setSaleOrderNo(saleOrderNo);
        if (actualAmount != null) {
            opp.setActualAmount(actualAmount);
        }

        // 若商机尚未成交，推进到"成交"阶段
        boolean needUpgradeCustomer = false;
        if (!"成交".equals(opp.getSalesStage()) && (opp.getIsClosed() == null || opp.getIsClosed() == 0)) {
            opp.setSalesStage("成交");
            opp.setProbability(100);
            opp.setStageUpdateDate(LocalDate.now());
            opp.setActualCloseDate(LocalDate.now());
            needUpgradeCustomer = true;
        }
        updateById(opp);
        log.info("销售订单审核回写商机成交: opportunityId={}, saleOrderNo={}, amount={}",
                opportunityId, saleOrderNo, actualAmount);

        // 升级客户为正式客户（幂等）
        if (needUpgradeCustomer) {
            upgradeCustomerToFormal(opp.getCustomerId());
        }
    }

    /**
     * 商机成交时自动将关联客户升级为"正式客户"
     * 仅当客户当前不是"正式客户"时才更新，避免重复写库
     */
    private void upgradeCustomerToFormal(Long customerId) {
        if (customerId == null) return;
        Customer customer = customerService.getById(customerId);
        if (customer == null) {
            log.warn("商机成交升级客户失败: customerId={} 不存在", customerId);
            return;
        }
        if ("正式客户".equals(customer.getCrmStatus())) {
            log.info("客户 {} 已是正式客户，无需升级", customer.getCustomerName());
            return;
        }
        UpdateWrapper<Customer> uw = new UpdateWrapper<>();
        uw.eq("customer_id", customerId)
                .set("crm_status", "正式客户")
                .set("sales_stage", "成交")
                .set("last_order_date", LocalDate.now());
        customerService.update(uw);
        log.info("商机成交 → 客户升级为正式客户: customerId={}, customerName={}", customerId, customer.getCustomerName());
    }

    @Override
    public String generateOpportunityNo() {
        String docType = "OPPORTUNITY";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "OPP-" + dateStr + "-";
            QueryWrapper<Opportunity> wrapper = new QueryWrapper<>();
            wrapper.likeRight("opportunity_no", prefix).orderByDesc("opportunity_no").last("LIMIT 1");
            Opportunity last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getOpportunityNo() != null) {
                try {
                    seq = Integer.parseInt(last.getOpportunityNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }
}
