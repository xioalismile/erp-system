package com.erp.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.crm.entity.Complaint;
import com.erp.crm.mapper.ComplaintMapper;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.service.CustomerService;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintServiceImpl extends ServiceImpl<ComplaintMapper, Complaint> implements ComplaintService {

    private final CustomerService customerService;
    private final SysSerialNoConfigService sysSerialNoConfigService;

    private static final Set<String> VALID_COMPLAINT_TYPES = Set.of("产品质量", "服务态度", "售后问题", "物流问题", "价格问题", "其他");
    private static final Set<String> VALID_SEVERITY_LEVELS = Set.of("低", "中", "高");

    @Override
    public PageResult<Complaint> pageQuery(int current, int size, String keyword, String handleStatus,
                                            String complaintType, String severityLevel, Long customerId, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder) {
        QueryWrapper<Complaint> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("complaint_no", keyword)
                    .or().like("customer_name", keyword)
                    .or().like("contact_person", keyword));
        }
        if (handleStatus != null && !handleStatus.isBlank()) {
            wrapper.eq("handle_status", handleStatus);
        }
        if (complaintType != null && !complaintType.isBlank()) {
            wrapper.eq("complaint_type", complaintType);
        }
        if (severityLevel != null && !severityLevel.isBlank()) {
            wrapper.eq("severity_level", severityLevel);
        }
        if (customerId != null) {
            wrapper.eq("customer_id", customerId);
        }
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建的；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null);
        Page<Complaint> page = new Page<>(current, size);
        Page<Complaint> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public Complaint getDetail(Long id) {
        Complaint complaint = getById(id);
        if (complaint == null) throw new BusinessException("投诉单不存在");
        return complaint;
    }

    @Override
    @Transactional
    public void saveComplaint(Complaint complaint) {
        boolean isNew = complaint.getComplaintId() == null;

        // 输入验证
        if (complaint.getComplaintContent() == null || complaint.getComplaintContent().isBlank()) {
            throw new BusinessException("投诉内容不能为空");
        }
        if (complaint.getComplaintType() != null && !VALID_COMPLAINT_TYPES.contains(complaint.getComplaintType())) {
            throw new BusinessException("无效的投诉类型: " + complaint.getComplaintType());
        }
        if (complaint.getSeverityLevel() != null && !VALID_SEVERITY_LEVELS.contains(complaint.getSeverityLevel())) {
            throw new BusinessException("无效的严重程度: " + complaint.getSeverityLevel() + " (只允许 低/中/高)");
        }

        if (complaint.getComplaintNo() == null || complaint.getComplaintNo().isBlank()) {
            complaint.setComplaintNo(generateComplaintNo());
        }
        if (complaint.getHandleStatus() == null) complaint.setHandleStatus("新建");
        if (complaint.getSeverityLevel() == null) complaint.setSeverityLevel("中");
        if (complaint.getCompensationAmount() == null) complaint.setCompensationAmount(BigDecimal.ZERO);
        if (complaint.getSatisfactionScore() == null) complaint.setSatisfactionScore(0);
        if (complaint.getComplaintDate() == null) complaint.setComplaintDate(LocalDate.now());
        if (complaint.getAssignedDate() == null) complaint.setAssignedDate(LocalDate.now());

        if (isNew) {
            save(complaint);
        } else {
            updateById(complaint);
        }

        log.info("客户投诉保存成功: {} 状态: {}", complaint.getComplaintNo(), complaint.getHandleStatus());
    }

    @Override
    @Transactional
    public void assignHandler(Long id, Long handlerId) {
        Complaint complaint = getById(id);
        if (complaint == null) throw new BusinessException("投诉单不存在");
        if (handlerId == null) throw new BusinessException("处理人ID不能为空");
        if (!"新建".equals(complaint.getHandleStatus())) {
            throw new BusinessException("只有新建状态的投诉单可分配处理人, 当前状态: " + complaint.getHandleStatus());
        }
        complaint.setAssignedHandlerId(handlerId);
        complaint.setAssignedDate(LocalDate.now());
        complaint.setHandleStatus("处理中");
        updateById(complaint);
        log.info("客户投诉分配处理人: {} 处理人ID: {}", complaint.getComplaintNo(), handlerId);
    }

    @Override
    @Transactional
    public void resolve(Long id, String handleResult) {
        Complaint complaint = getById(id);
        if (complaint == null) throw new BusinessException("投诉单不存在");
        if (!"处理中".equals(complaint.getHandleStatus())) {
            throw new BusinessException("只有处理中状态可解决，当前状态: " + complaint.getHandleStatus());
        }
        complaint.setHandleResult(handleResult);
        complaint.setHandleDate(LocalDate.now());
        complaint.setHandleStatus("已解决");
        updateById(complaint);
        log.info("客户投诉解决: {}", complaint.getComplaintNo());
    }

    @Override
    @Transactional
    public void close(Long id, Integer satisfactionScore) {
        Complaint complaint = getById(id);
        if (complaint == null) throw new BusinessException("投诉单不存在");
        if (!"已解决".equals(complaint.getHandleStatus())) {
            throw new BusinessException("只有已解决状态可关闭，当前状态: " + complaint.getHandleStatus());
        }
        if (satisfactionScore != null && (satisfactionScore < 0 || satisfactionScore > 5)) {
            throw new BusinessException("满意度评分必须在 0-5 之间");
        }
        complaint.setHandleStatus("已关闭");
        complaint.setCloseDate(LocalDate.now());
        if (satisfactionScore != null) {
            complaint.setSatisfactionScore(satisfactionScore);
        }
        updateById(complaint);

        // 关闭后更新客户投诉次数与满意度评分
        if (complaint.getCustomerId() != null) {
            Customer customer = customerService.getById(complaint.getCustomerId());
            if (customer != null) {
                int cnt = customer.getComplaintCount() != null ? customer.getComplaintCount() : 0;
                customer.setComplaintCount(cnt + 1);
                if (satisfactionScore != null) {
                    customer.setSatisfactionScore(satisfactionScore);
                }
                customerService.updateById(customer);
            }
        }

        log.info("客户投诉关闭: {} 满意度: {}", complaint.getComplaintNo(), satisfactionScore);
    }

    @Override
    public String generateComplaintNo() {
        String docType = "COMPLAINT";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "C-" + dateStr + "-";
            QueryWrapper<Complaint> wrapper = new QueryWrapper<>();
            wrapper.likeRight("complaint_no", prefix).orderByDesc("complaint_no").last("LIMIT 1");
            Complaint last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getComplaintNo() != null) {
                try {
                    seq = Integer.parseInt(last.getComplaintNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }
}
