package com.erp.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.crm.entity.Contact;
import com.erp.crm.mapper.ContactMapper;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.service.CustomerService;
import com.erp.serialno.service.SysSerialNoConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.util.SortHelper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor

public class ContactServiceImpl extends ServiceImpl<ContactMapper, Contact> implements ContactService {

    private final CustomerService customerService;
    private final SysSerialNoConfigService sysSerialNoConfigService;

    @Override
    public PageResult<Contact> pageQuery(int current, int size, String keyword,
                                          Long customerId, Integer isPrimary, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder) {
        QueryWrapper<Contact> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("contact_no", keyword)
                    .or().like("contact_name", keyword)
                    .or().like("mobile", keyword)
                    .or().like("phone", keyword)
                    .or().like("email", keyword)
                    .or().like("customer_name", keyword));
        }
        if (customerId != null) {
            wrapper.eq("customer_id", customerId);
        }
        if (isPrimary != null) {
            wrapper.eq("is_primary", isPrimary);
        }
        // 数据权限（2026-09-02 配置驱动）：仅本人=自己创建的；本部门=部门成员的；全部=不过滤
        if (scopeCtx != null) scopeCtx.applyTo(wrapper, null);
        Page<Contact> page = new Page<>(current, size);
        Page<Contact> result = page(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public Contact getDetail(Long id) {
        Contact contact = getById(id);
        if (contact == null) throw new BusinessException("联系人不存在");
        return contact;
    }

    @Override
    @Transactional
    public void saveContact(Contact contact) {
        boolean isNew = contact.getContactId() == null;

        if (contact.getContactName() == null || contact.getContactName().isBlank()) {
            throw new BusinessException("联系人姓名不能为空");
        }
        if (contact.getCustomerId() == null) {
            throw new BusinessException("所属客户不能为空");
        }
        // 反查客户名称冗余字段
        Customer customer = customerService.getById(contact.getCustomerId());
        if (customer == null) throw new BusinessException("客户不存在: " + contact.getCustomerId());
        contact.setCustomerName(customer.getCustomerName());

        if (contact.getContactNo() == null || contact.getContactNo().isBlank()) {
            contact.setContactNo(generateContactNo());
        }
        if (contact.getIsPrimary() == null) contact.setIsPrimary(0);

        // 设为主联系人时，同客户下其他联系人取消主联系人标记
        if (Integer.valueOf(1).equals(contact.getIsPrimary())) {
            update(null, new UpdateWrapper<Contact>()
                    .eq("customer_id", contact.getCustomerId())
                    .set("is_primary", 0));
        }

        if (isNew) {
            contact.setCreateDate(LocalDateTime.now());
            save(contact);
        } else {
            contact.setModifyDate(LocalDateTime.now());
            updateById(contact);
        }
        log.info("CRM联系人保存成功: {} 客户: {}", contact.getContactNo(), contact.getCustomerName());
    }

    @Override
    public List<Contact> listByCustomer(Long customerId) {
        QueryWrapper<Contact> wrapper = new QueryWrapper<>();
        wrapper.eq("customer_id", customerId)
                .orderByDesc("is_primary")
                .orderByDesc("create_date");
        return list(wrapper);
    }

    @Override
    @Transactional
    public void setPrimary(Long id) {
        Contact contact = getById(id);
        if (contact == null) throw new BusinessException("联系人不存在");
        // 同客户下其他联系人取消主联系人
        update(null, new UpdateWrapper<Contact>()
                .eq("customer_id", contact.getCustomerId())
                .set("is_primary", 0));
        // 当前联系人设为主联系人
        contact.setIsPrimary(1);
        contact.setModifyDate(LocalDateTime.now());
        updateById(contact);
        log.info("联系人设为主联系人: {}", contact.getContactNo());
    }

    /** 编号格式: CT-yyyyMMdd-0001 */
    private String generateContactNo() {
        String docType = "CONTACT";
        return sysSerialNoConfigService.generateOrDefault(docType, () -> {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String prefix = "CT-" + dateStr + "-";
            QueryWrapper<Contact> wrapper = new QueryWrapper<>();
            wrapper.likeRight("contact_no", prefix).orderByDesc("contact_no").last("LIMIT 1");
            Contact last = getOne(wrapper);
            int seq = 1;
            if (last != null && last.getContactNo() != null) {
                try {
                    seq = Integer.parseInt(last.getContactNo().substring(prefix.length())) + 1;
                } catch (Exception ignored) {
                }
            }
            return prefix + String.format("%04d", seq);
        });
    }
}
