package com.erp.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.crm.entity.Lead;
import com.erp.crm.mapper.LeadMapper;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.service.CustomerService;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadServiceImpl extends ServiceImpl<LeadMapper, Lead> implements LeadService {

    private final CustomerService customerService;
    private final SysSerialNoConfigService sysSerialNoConfigService;

    private static final Set<String> VALID_SOURCES = Set.of("线上广告", "电话营销", "客户介绍", "网络搜索", "展会", "企查查", "天眼查", "官网咨询", "其他");
    private static final Set<String> VALID_INTENTIONS = Set.of("A", "B", "C");

    @Override
    public PageResult<Lead> pageQuery(int current, int size, String keyword, String leadStatus,
                                      String intentionLevel, Long assignedSalesId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder) {
        QueryWrapper<Lead> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("lead_no", keyword)
                    .or().like("lead_name", keyword)
                    .or().like("company_name", keyword)
                    .or().like("contact_person", keyword));
        }
        if (leadStatus != null && !leadStatus.isBlank()) {
            wrapper.eq("lead_status", leadStatus);
        }
        if (intentionLevel != null && !intentionLevel.isBlank()) {
            wrapper.eq("intention_level", intentionLevel);
        }
        if (assignedSalesId != null) {
            wrapper.eq("assigned_sales_id", assignedSalesId);
        }
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己负责/创建的；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, "assigned_sales_id");
        Page<Lead> page = new Page<>(current, size);
        Page<Lead> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public Lead getDetail(Long id) {
        Lead lead = getById(id);
        if (lead == null) throw new BusinessException("线索不存在");
        return lead;
    }

    @Override
    @Transactional
    public void saveLead(Lead lead) {
        boolean isNew = lead.getLeadId() == null;

        // 输入验证
        if (lead.getLeadName() == null || lead.getLeadName().isBlank()) {
            throw new BusinessException("线索名称不能为空");
        }
        if (lead.getCompanyName() == null || lead.getCompanyName().isBlank()) {
            throw new BusinessException("公司名称不能为空");
        }
        if (lead.getSourceType() != null && !VALID_SOURCES.contains(lead.getSourceType())) {
            throw new BusinessException("无效的来源类型: " + lead.getSourceType());
        }
        if (lead.getIntentionLevel() != null && !VALID_INTENTIONS.contains(lead.getIntentionLevel())) {
            throw new BusinessException("无效的意向等级: " + lead.getIntentionLevel() + " (只允许 A/B/C)");
        }

        if (lead.getLeadNo() == null || lead.getLeadNo().isBlank()) {
            lead.setLeadNo(generateLeadNo());
        }
        if (lead.getLeadStatus() == null) lead.setLeadStatus("新线索");
        if (lead.getIntentionLevel() == null) lead.setIntentionLevel("C");
        if (lead.getLeadType() == null) lead.setLeadType("采购意向");
        if (lead.getFollowUpCount() == null) lead.setFollowUpCount(0);
        if (lead.getAssignedSalesId() != null && lead.getAssignedDate() == null) {
            lead.setAssignedDate(LocalDate.now());
        }

        if (isNew) {
            lead.setCreateDate(LocalDateTime.now());
            save(lead);
        } else {
            lead.setModifyDate(LocalDateTime.now());
            updateById(lead);
        }

        log.info("销售线索保存成功: {} 状态: {}", lead.getLeadNo(), lead.getLeadStatus());
    }

    @Override
    @Transactional
    public void updateLeadStatus(Long id, String status) {
        Lead lead = getById(id);
        if (lead == null) throw new BusinessException("线索不存在");
        if (status == null || status.isBlank()) {
            throw new BusinessException("线索状态不能为空");
        }
        if (!"新线索".equals(status) && !"跟进中".equals(status)
                && !"已转化".equals(status) && !"已放弃".equals(status)) {
            throw new BusinessException("无效的线索状态: " + status);
        }
        // 已放弃需要原因，请使用abandon接口
        if ("已放弃".equals(status)) {
            throw new BusinessException("放弃线索请使用abandon接口并填写原因");
        }
        // 已转化/已放弃为终态，不可逆
        if ("已转化".equals(lead.getLeadStatus()) || "已放弃".equals(lead.getLeadStatus())) {
            throw new BusinessException("当前状态不可变更: " + lead.getLeadStatus());
        }
        lead.setLeadStatus(status);
        if ("已转化".equals(status)) {
            lead.setConvertedDate(LocalDate.now());
        }
        lead.setModifyDate(LocalDateTime.now());
        updateById(lead);
        log.info("销售线索状态更新: {} → {}", lead.getLeadNo(), status);
    }

    @Override
    @Transactional
    public void assignSales(Long id, Long salesId) {
        Lead lead = getById(id);
        if (lead == null) throw new BusinessException("线索不存在");
        if (salesId == null) throw new BusinessException("销售员ID不能为空");
        lead.setAssignedSalesId(salesId);
        lead.setAssignedDate(LocalDate.now());
        lead.setModifyDate(LocalDateTime.now());
        updateById(lead);
        log.info("销售线索分配销售员: {} 销售员ID: {}", lead.getLeadNo(), salesId);
    }

    @Override
    @Transactional
    public void abandon(Long id, String reason) {
        Lead lead = getById(id);
        if (lead == null) throw new BusinessException("线索不存在");
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("放弃原因不能为空");
        }
        lead.setLeadStatus("已放弃");
        lead.setAbandonReason(reason);
        lead.setModifyDate(LocalDateTime.now());
        updateById(lead);
        log.info("销售线索放弃: {} 原因: {}", lead.getLeadNo(), reason);
    }

    @Override
    @Transactional
    public Long convertToCustomer(Long id) {
        return convertToCustomer(id, false);
    }

    @Override
    @Transactional
    public Long convertToCustomer(Long id, boolean toPool) {
        Lead lead = getById(id);
        if (lead == null) throw new BusinessException("线索不存在");
        if ("已转化".equals(lead.getLeadStatus())) {
            throw new BusinessException("线索已转化，不能重复转化");
        }

        // 创建新客户，复制全部相关字段（解决"11个字段但能用的只有2个"问题）
        Customer customer = new Customer();
        customer.setCustomerCode(generateCustomerCode());
        customer.setCustomerName(lead.getCompanyName() != null ? lead.getCompanyName() : lead.getLeadName());
        customer.setShortName(lead.getCompanyName());
        // 法人/联系人信息
        customer.setLegalPerson(lead.getContactPerson());
        customer.setContactPerson(lead.getContactPerson());
        customer.setContactPhone(lead.getContactPhone());
        customer.setEmail(lead.getContactEmail());
        // 税号 ← 采集的统一社会信用代码
        customer.setTaxNo(lead.getCreditCode());
        // 地址 ← 注册地址
        customer.setAddress(lead.getRegisteredAddress());
        // 行业/主营/规模
        customer.setIndustry(lead.getIndustry());
        customer.setMainBusiness(lead.getDemandDesc());
        customer.setCompanyScale(lead.getCompanyScale());
        // 来源
        customer.setSourceType(lead.getSourceType());
        customer.setSourceDetail(lead.getSourceUrl());
        // 意向/分配
        customer.setIntentionLevel(lead.getIntentionLevel());
        customer.setFollowUpCount(lead.getFollowUpCount() != null ? lead.getFollowUpCount() : 0);
        // 状态
        customer.setStatus("正常");
        customer.setIsBlacklist(0);

        if (toPool) {
            // 进入公海池：不分配销售员、归属用户为空
            customer.setPoolType(1);
            customer.setPoolEnterDate(LocalDateTime.now());
            customer.setOwnerUserId(null);
            customer.setAssignedSalesId(null);
            customer.setCrmStatus("公海客户");
            customer.setSalesStage("公海");
        } else {
            // 转化为正式客户：保留原分配销售员
            customer.setPoolType(0);
            customer.setAssignedSalesId(lead.getAssignedSalesId());
            customer.setCrmStatus("正式客户");
            customer.setSalesStage("成交");
        }

        // 扩展字段存入 extData JSON（area/establishDate/companyStatus/registeredCapital/contactAddress）
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> ext = new java.util.LinkedHashMap<>();
            if (lead.getArea() != null) ext.put("area", lead.getArea());
            if (lead.getEstablishDate() != null) ext.put("establishDate", lead.getEstablishDate());
            if (lead.getCompanyStatus() != null) ext.put("companyStatus", lead.getCompanyStatus());
            if (lead.getRegisteredCapital() != null) ext.put("registeredCapital", lead.getRegisteredCapital());
            if (lead.getContactAddress() != null) ext.put("contactAddress", lead.getContactAddress());
            if (lead.getCreditCode() != null) ext.put("creditCode", lead.getCreditCode());
            if (lead.getRegisteredAddress() != null) ext.put("registeredAddress", lead.getRegisteredAddress());
            if (!ext.isEmpty()) {
                customer.setExtData(mapper.writeValueAsString(ext));
            }
        } catch (Exception e) {
            log.warn("Lead扩展字段写入extData失败 leadId={}: {}", id, e.getMessage());
        }

        customerService.save(customer);

        // 更新线索: 设置转化客户ID、转化日期、状态
        lead.setConvertedCustomerId(customer.getCustomerId());
        lead.setConvertedDate(LocalDate.now());
        lead.setLeadStatus("已转化");
        lead.setModifyDate(LocalDateTime.now());
        updateById(lead);

        log.info("销售线索转化客户成功: 线索:{} → 客户ID:{} toPool={}", lead.getLeadNo(), customer.getCustomerId(), toPool);
        return customer.getCustomerId();
    }

    @Override
    public String generateLeadNo() {
        String docType = "LEAD";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "L-" + dateStr + "-";
            QueryWrapper<Lead> wrapper = new QueryWrapper<>();
            wrapper.likeRight("lead_no", prefix).orderByDesc("lead_no").last("LIMIT 1");
            Lead last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getLeadNo() != null) {
                try {
                    seq = Integer.parseInt(last.getLeadNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }

    /** 生成客户编号: C-yyyyMMdd-0001 */
    private String generateCustomerCode() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "C-" + dateStr + "-";
        QueryWrapper<Customer> wrapper = new QueryWrapper<>();
        wrapper.likeRight("customer_code", prefix).orderByDesc("customer_code").last("LIMIT 1");
        Customer last = customerService.getOne(wrapper);
        int seq = 1;
        if (last != null && last.getCustomerCode() != null) {
            try {
                seq = Integer.parseInt(last.getCustomerCode().substring(prefix.length())) + 1;
            } catch (Exception ignored) {
            }
        }
        return prefix + String.format("%04d", seq);
    }
}
