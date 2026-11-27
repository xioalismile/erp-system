package com.erp.crm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.crm.entity.Contact;

import java.util.List;

public interface ContactService extends IService<Contact> {

    PageResult<Contact> pageQuery(int current, int size, String keyword,
                                   Long customerId, Integer isPrimary, com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx, String sortBy, String sortOrder);

    Contact getDetail(Long id);

    void saveContact(Contact contact);

    /** 查询某客户下的全部联系人 */
    List<Contact> listByCustomer(Long customerId);

    /** 设为主联系人（同客户下其他联系人自动取消主联系人标记） */
    void setPrimary(Long id);
}
