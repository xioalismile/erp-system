package com.erp.crm.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.crm.entity.Contact;
import com.erp.crm.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/crm/contact")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping("/page")
    public Result<PageResult<Contact>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Integer isPrimary,
            jakarta.servlet.http.HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(contactService.pageQuery(current, size, keyword, customerId, isPrimary, scopeCtx, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<Contact> detail(@PathVariable Long id) {
        return Result.success(contactService.getDetail(id));
    }

    @PostMapping
    public Result<Contact> create(@RequestBody Contact contact) {
        contactService.saveContact(contact);
        return Result.success(contact);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Contact contact) {
        contact.setContactId(id);
        contactService.saveContact(contact);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        contactService.removeById(id);
        return Result.success();
    }

    /** 按客户查询联系人列表 */
    @GetMapping("/by-customer/{customerId}")
    public Result<List<Contact>> listByCustomer(@PathVariable Long customerId) {
        return Result.success(contactService.listByCustomer(customerId));
    }

    /** 设为主联系人 */
    @PutMapping("/{id}/set-primary")
    public Result<Void> setPrimary(@PathVariable Long id) {
        contactService.setPrimary(id);
        return Result.success();
    }
}
