package com.erp.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.crm.entity.FollowUp;
import com.erp.crm.entity.Lead;
import com.erp.crm.entity.Opportunity;
import com.erp.crm.mapper.FollowUpMapper;
import com.erp.crm.mapper.LeadMapper;
import com.erp.crm.mapper.OpportunityMapper;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.service.CustomerService;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpServiceImpl extends ServiceImpl<FollowUpMapper, FollowUp> implements FollowUpService {

    private final CustomerService customerService;
    private final LeadMapper leadMapper;
    private final OpportunityMapper opportunityMapper;
    private final SysSerialNoConfigService sysSerialNoConfigService;

    private static final Set<String> VALID_SOURCE_TYPES = Set.of("客户", "线索", "商机");

    @Override
    public PageResult<FollowUp> pageQuery(int current, int size, String keyword, String sourceType,
                                          Long sourceId, String followUpType, Integer importantFlag, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder) {
        QueryWrapper<FollowUp> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("follow_up_no", keyword)
                    .or().like("contact_person", keyword)
                    .or().like("source_name", keyword));
        }
        if (sourceType != null && !sourceType.isBlank()) {
            wrapper.eq("source_type", sourceType);
        }
        if (sourceId != null) {
            wrapper.eq("source_id", sourceId);
        }
        if (followUpType != null && !followUpType.isBlank()) {
            wrapper.eq("follow_up_type", followUpType);
        }
        if (importantFlag != null) {
            wrapper.eq("important_flag", importantFlag);
        }
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建的；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null);
        Page<FollowUp> page = new Page<>(current, size);
        Page<FollowUp> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public FollowUp getDetail(Long id) {
        FollowUp fu = getById(id);
        if (fu == null) throw new BusinessException("跟进记录不存在");
        return fu;
    }

    @Override
    @Transactional
    public void saveFollowUp(FollowUp fu) {
        // 输入验证
        if (fu.getContent() == null || fu.getContent().isBlank()) {
            throw new BusinessException("跟进内容不能为空");
        }
        if (fu.getSourceType() != null && !VALID_SOURCE_TYPES.contains(fu.getSourceType())) {
            throw new BusinessException("无效的来源类型: " + fu.getSourceType() + " (只允许 客户/线索/商机)");
        }

        if (fu.getFollowUpNo() == null || fu.getFollowUpNo().isBlank()) {
            fu.setFollowUpNo(generateFollowUpNo());
        }
        if (fu.getFollowUpDate() == null) {
            fu.setFollowUpDate(LocalDate.now());
        }
        if (fu.getImportantFlag() == null) {
            fu.setImportantFlag(0);
        }
        // 销售过程动作量化（2026-10-07）：未显式传入时按"通话 ≥300 秒 或 微信交流 ≥10 句"自动预判，人可手改
        if (fu.getIsDeepCommunication() == null) {
            int dur = fu.getCallDurationSeconds() == null ? 0 : fu.getCallDurationSeconds();
            int cnt = fu.getChatMessageCount() == null ? 0 : fu.getChatMessageCount();
            fu.setIsDeepCommunication((dur >= 300 || cnt >= 10) ? 1 : 0);
        }

        save(fu);

        // 文档规范: 保存跟进记录后, 更新来源对象的最后跟进日期与跟进次数
        updateSource(fu);

        log.info("跟进记录保存成功: {} 来源: {}-{}", fu.getFollowUpNo(), fu.getSourceType(), fu.getSourceName());
    }

    /** 根据来源类型更新客户/线索/商机的最后跟进日期与跟进次数 */
    private void updateSource(FollowUp fu) {
        String sourceType = fu.getSourceType();
        Long sourceId = fu.getSourceId();
        if (sourceType == null || sourceId == null) {
            return;
        }

        if ("客户".equals(sourceType)) {
            Customer customer = customerService.getById(sourceId);
            if (customer != null) {
                customer.setLastContactDate(fu.getFollowUpDate());
                int count = customer.getFollowUpCount() == null ? 0 : customer.getFollowUpCount();
                customer.setFollowUpCount(count + 1);
                customerService.updateById(customer);
            }
        } else if ("线索".equals(sourceType)) {
            Lead lead = leadMapper.selectById(sourceId);
            if (lead != null) {
                lead.setLastFollowUpDate(fu.getFollowUpDate());
                int count = lead.getFollowUpCount() == null ? 0 : lead.getFollowUpCount();
                lead.setFollowUpCount(count + 1);
                // 文档规范: 新线索首次跟进后状态变为跟进中
                if ("新线索".equals(lead.getLeadStatus())) {
                    lead.setLeadStatus("跟进中");
                }
                leadMapper.updateById(lead);
            }
        } else if ("商机".equals(sourceType)) {
            Opportunity opp = opportunityMapper.selectById(sourceId);
            if (opp != null) {
                opp.setLastFollowUpDate(fu.getFollowUpDate());
                int count = opp.getFollowUpCount() == null ? 0 : opp.getFollowUpCount();
                opp.setFollowUpCount(count + 1);
                opportunityMapper.updateById(opp);
            }
        }
    }

    @Override
    public String generateFollowUpNo() {
        String docType = "FOLLOW_UP";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "FU-" + dateStr + "-";
            QueryWrapper<FollowUp> wrapper = new QueryWrapper<>();
            wrapper.likeRight("follow_up_no", prefix).orderByDesc("follow_up_no").last("LIMIT 1");
            FollowUp last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getFollowUpNo() != null) {
                try {
                    seq = Integer.parseInt(last.getFollowUpNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }
}
