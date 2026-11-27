package com.erp.basedata.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.BatchDeleteCheckDTO;
import com.erp.common.Result;
import com.erp.common.util.CustomFieldSearchHelper;
import com.erp.common.util.SortHelper;
import com.erp.common.bill.BillCascadeDeleteService;
import com.erp.config.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.Serializable;
import java.util.List;

/**
 * 基础资料通用CRUD基类
 * 子类只需注入Service即可拥有标准CRUD能力
 */
public abstract class BaseCrudController<T, ID extends Serializable> {

    protected abstract IService<T> getService();
    protected abstract String getCodeField();
    protected abstract String getIdField();
    /** 模糊搜索字段列表(用于关键字搜索) */
    protected abstract List<String> getSearchFields();

    /** 整单删除级联服务（删主表前清理明细，2026-09-19） */
    @Autowired
    private BillCascadeDeleteService billCascadeDeleteService;

    /**
     * 数据权限过滤钩子（2026-09-02 数据权限体系落地）：
     * page/list/listAll 查询构建后统一调用；默认不过滤。
     * 需要数据范围过滤的子类（如仓库/物料）覆写本方法，
     * 用 DataScopeResolver 注入 created_by 范围条件与白名单叠加。
     */
    protected void applyDataScopeFilter(QueryWrapper<T> qw) {
        // 默认无过滤，子类按需覆写
    }

    /**
     * 业务准入过滤钩子（2026-09-06 客户档案域隔离）：
     * page/list/listAll 查询构建后统一调用；默认无过滤。
     * 子类覆写可附加业务可见性条件（如客户档案只显示正式客户与存量 NULL 客户，
     * CRM 私海培育中的潜在/意向客户不进入客户档案）。
     */
    protected void applyExtraFilter(QueryWrapper<T> qw) {
        // 默认无过滤，子类按需覆写
    }

    /**
     * 写操作角色校验（安全审计 V-004）：VIEWER 为系统只读角色，禁止任何写操作。
     * ADMIN/业务角色（SALES/WAREHOUSE 等）不受影响，业务零回归。
     * 子类如需更严格权限可覆盖本方法。
     * 角色来源：AuthInterceptor 写入的 currentRoles 请求属性（sys_user_role 真实数据，不做用户名推断）。
     */
    protected Result<Void> checkWriteRole() {
        Object roles = currentRequest().getAttribute("currentRoles");
        if (roles instanceof List<?> roleList && roleList.stream()
                .anyMatch(r -> "VIEWER".equals(String.valueOf(r)))) {
            // 仅 VIEWER（无其他角色）时拒绝；同时持有 VIEWER+业务角色视为业务角色用户
            boolean onlyViewer = roleList.stream().allMatch(r -> "VIEWER".equals(String.valueOf(r)));
            if (onlyViewer) {
                return Result.error(403, "当前账号为只读角色，无操作权限");
            }
        }
        return null;
    }

    /** 从线程上下文取当前请求（不改子类可见的端点方法签名；子类数据权限过滤需要，改为 protected） */
    protected static jakarta.servlet.http.HttpServletRequest currentRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }

    @GetMapping("/page")
    public Result<Page<T>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String customSearch,
            // 数据看板穿透等场景的时间区间过滤（可选；子类覆写 applyCreatedRangeFilter 才生效）
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo) {
        return pageQuery(current, size, keyword, status, sortBy, sortOrder,
                customSearch, createdFrom, createdTo, null);
    }

    /**
     * 分页查询主体（page 端点与子类覆写共用）。
     * <p>子类若需要「父类 9 参数签名之外」的附加筛选（如物料档案的分类 categoryId），
     * 通过 extraFilter 注入，避免改动父类签名导致其它子类覆写签名失配、丢失关键字/排序/数据权限。</p>
     *
     * @param extraFilter 附加查询条件（可为 null，表示无附加条件）
     */
    protected Result<Page<T>> pageQuery(
            int current, int size, String keyword, String status,
            String sortBy, String sortOrder, String customSearch,
            String createdFrom, String createdTo,
            java.util.function.Consumer<QueryWrapper<T>> extraFilter) {
        Page<T> page = new Page<>(current, size);
        QueryWrapper<T> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            List<String> fields = getSearchFields();
            if (!fields.isEmpty()) {
                qw.and(w -> {
                    for (int i = 0; i < fields.size(); i++) {
                        if (i > 0) w.or();
                        w.like(fields.get(i), keyword);
                    }
                });
            }
        }
        if (status != null && !status.isBlank()) {
            qw.eq("status", status);
        }
        // 自定义字段搜索：customSearch 为 JSON {fieldKey: value}，按 sys_custom_field_value 表过滤
        // （设置中心-自定义字段 is_search=1 的字段；不依赖业务表 ext_data 列，所有实体通用）
        applyCustomSearch(qw, customSearch);
        // 排序：前端传 sortBy/sortOrder 时按白名单列排序，否则默认主键倒序
        if (!SortHelper.applyToWrapper(qw, sortBy, sortOrder, getSortableColumns())) {
            qw.orderByDesc(getIdField());
        }
        applyDataScopeFilter(qw);
        applyCreatedRangeFilter(qw, createdFrom, createdTo);
        applyExtraFilter(qw);
        if (extraFilter != null) {
            extraFilter.accept(qw);
        }
        return Result.success(getService().page(page, qw));
    }

    /**
     * 建档时间区间过滤（可选，默认不生效）。
     * 子类需按"创建时间"列覆写本方法；参数为空时不得添加任何条件（保证默认查询行为不变）。
     * 参数格式 YYYY-MM-DD，非法值忽略（不抛异常、不拼接进 SQL，全部走 QueryWrapper 参数绑定）。
     */
    protected void applyCreatedRangeFilter(QueryWrapper<T> qw, String createdFrom, String createdTo) {
        // 默认无过滤，子类按需覆写
    }

    /**
     * 自定义字段搜索条件（基于 sys_custom_field_value 值表，所有实体通用，不依赖 ext_data 列）。
     * 具体拼装逻辑见 {@link CustomFieldSearchHelper}（单据列表 Controller 也用同一份，避免两处实现漂移）。
     */
    private void applyCustomSearch(QueryWrapper<T> qw, String customSearch) {
        String entityType = getCustomFieldEntityType();
        if (entityType == null || entityType.isBlank()) return;
        CustomFieldSearchHelper.apply(qw, entityType, customSearch, getIdField());
    }

    /** 自定义字段实体类型（与 sys_custom_field.entity_type 一致，如 'customer'/'material'）；子类覆盖以启用自定义字段搜索 */
    protected String getCustomFieldEntityType() {
        return null;
    }

    @GetMapping
    public Result<List<T>> listAll(@RequestParam(required = false) String status) {
        QueryWrapper<T> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) qw.eq("status", status);
        qw.orderByAsc(getCodeField());
        applyDataScopeFilter(qw);
        applyExtraFilter(qw);
        return Result.success(getService().list(qw));
    }

    @GetMapping("/list")
    public Result<List<T>> list(@RequestParam(required = false) String status) {
        QueryWrapper<T> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) qw.eq("status", status);
        qw.orderByAsc(getCodeField());
        applyDataScopeFilter(qw);
        applyExtraFilter(qw);
        return Result.success(getService().list(qw));
    }

    @GetMapping("/{id}")
    public Result<T> get(@PathVariable ID id) {
        return Result.success(getService().getById(id));
    }

    /**
     * 数据库列名 → Java 属性名（customer_id → customerId；customerId 原样返回）。
     * getIdField()/getCodeField() 返回 DB 列名（供 QueryWrapper eq/orderBy 使用），
     * 而反射 getter 必须用 Java 属性名——此前直接拼接列名导致 getCustomer_id()
     * NoSuchMethodException，编辑保存(update) 恒返回 404"记录不存在或已被删除"。
     */
    private String toJavaProperty(String column) {
        if (column == null || column.isBlank() || column.indexOf('_') < 0) {
            return column;
        }
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : column.toCharArray()) {
            if (c == '_') {
                upper = true;
                continue;
            }
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return sb.toString();
    }

    @PostMapping
    public Result<Void> save(@RequestBody T entity) {
        // 编码唯一性校验
        if (getCodeField() != null) {
            try {
                String codeProp = toJavaProperty(getCodeField());
                Object codeVal = entity.getClass().getMethod("get" + capitalize(codeProp)).invoke(entity);
                // 编码留空 = 交由 Service 按编号规则自动生成（前端新增页不再预填编码），
                // 此时不能拿空串去查重：库里若有历史空编码行会被误判成"编码已存在"而拦死新增。
                if (codeVal != null && !String.valueOf(codeVal).isBlank()) {
                    long count = getService().count(new QueryWrapper<T>().eq(getCodeField(), codeVal));
                    if (count > 0) {
                        return Result.error(409, "编码已存在");
                    }
                }
            } catch (Exception e) {
                throw new BusinessException("保存失败：编码查重失败：" + e.getMessage());
            }
        }
        getService().save(entity);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody T entity) {
        // 编辑时排除自身的编码唯一性校验
        Object idVal = null;
        String codeProp = toJavaProperty(getCodeField());
        String idProp = toJavaProperty(getIdField());
        if (getCodeField() != null && getIdField() != null) {
            try {
                Object codeVal = entity.getClass().getMethod("get" + capitalize(codeProp)).invoke(entity);
                idVal = entity.getClass().getMethod("get" + capitalize(idProp)).invoke(entity);
                if (codeVal != null && idVal != null) {
                    long count = getService().count(new QueryWrapper<T>()
                            .eq(getCodeField(), codeVal).ne(getIdField(), idVal));
                    if (count > 0) {
                        return Result.error(409, "编码已存在");
                    }
                }
            } catch (Exception e) {
                throw new BusinessException("保存失败：编码查重失败：" + e.getMessage());
            }
        }
        // 写操作存在性校验（安全审计）：id 为空或目标记录不存在时返回 404，避免影响 0 行仍报成功
        if (idVal == null && getIdField() != null) {
            try {
                idVal = entity.getClass().getMethod("get" + capitalize(idProp)).invoke(entity);
            } catch (Exception ignored) {}
        }
        if (idVal == null) {
            return Result.error(404, "记录不存在或已被删除");
        }
        if (!getService().updateById(entity)) {
            return Result.error(404, "记录不存在或已被删除");
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Result<Void> delete(@PathVariable ID id) {
        // 【2026-09-19 整单删除】先清明细/从表，再删主表（用户硬要求：不允许留孤儿明细）
        billCascadeDeleteService.deleteChildrenByEntity(getService().getEntityClass(), id);
        // 写操作存在性校验（安全审计）：删除不存在的记录返回 404，避免"成功假象"
        if (!getService().removeById(id)) {
            return Result.error(404, "记录不存在");
        }
        return Result.success();
    }

    @PostMapping("/batch-delete")
    @Transactional
    public Result<Void> batchDelete(@RequestBody List<ID> ids) {
        // 写操作角色校验（2026-09-16 补）：与同基类 enable/disable 等写操作对齐，
        // 否则批量删除会成为绕过"只读角色禁止写"的口子
        Result<Void> denied = checkWriteRole();
        if (denied != null) return denied;
        if (ids == null || ids.isEmpty()) return Result.success();
        // 【2026-09-19 整单删除】批量删除前逐条清理明细/从表（用户硬要求：不允许留孤儿明细）
        for (ID id : ids) {
            billCascadeDeleteService.deleteChildrenByEntity(getService().getEntityClass(), id);
        }
        // 一次 DELETE ... WHERE id IN (...)；回读实际影响行数，与请求条数不一致时如实报错（不静默）
        int affected = getService().getBaseMapper().delete(new QueryWrapper<T>().in(getIdField(), ids));
        if (affected != ids.size()) {
            return Result.error(409, "已删除 " + affected + " 条，另有 " + (ids.size() - affected)
                    + " 条未能删除（可能已被其他操作改动），请刷新后重试");
        }
        return Result.success();
    }

    /**
     * 批量删除「预检」（2026-09-16 新增）：只看不删，返回"可删 / 不可删（含原因）"清单，
     * 供前端弹窗让用户自己决定是否继续，**不替用户做决定**。
     *
     * <p>默认实现＝存在性检查（纯删表的基础资料天然"全部可删"）。
     * 有业务校验的子类应覆写本方法，逐个跑自己的校验（范式见 CustomerController：引用校验），
     * 校验逻辑与单条删除接口保持同源，避免"单条删不掉、批量却删掉了"。</p>
     *
     * <p>注意：预检结果仅用于展示，执行阶段（{@link #batchDelete}）会重新校验。</p>
     */
    @PostMapping("/batch-delete-check")
    public Result<BatchDeleteCheckDTO> batchDeleteCheck(@RequestBody List<ID> ids) {
        Result<Void> denied = checkWriteRole();
        if (denied != null) return Result.error(denied.getCode(), denied.getMessage());
        BatchDeleteCheckDTO out = new BatchDeleteCheckDTO();
        if (ids == null || ids.isEmpty()) return Result.success(out);
        for (ID id : ids) {
            Long val = toLongId(id);
            if (val != null && getService().getById(id) != null) {
                out.getDeletable().add(val);
            } else {
                out.getBlocked().add(new BatchDeleteCheckDTO.Blocked(val, "记录不存在或已被删除"));
            }
        }
        return Result.success(out);
    }

    /** ID → Long（供预检结果回传前端使用；非数字主键返回 null） */
    protected Long toLongId(ID id) {
        if (id == null) return null;
        if (id instanceof Long l) return l;
        if (id instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(String.valueOf(id));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @PostMapping("/enable/{id}")
    public Result<Void> enable(@PathVariable ID id) {
        Result<Void> denied = checkWriteRole();
        if (denied != null) return denied;
        updateStatus(id, "启用");
        return Result.success();
    }

    @PostMapping("/disable/{id}")
    public Result<Void> disable(@PathVariable ID id) {
        Result<Void> denied = checkWriteRole();
        if (denied != null) return denied;
        updateStatus(id, "停用");
        return Result.success();
    }

    private void updateStatus(ID id, String status) {
        T entity = getService().getById(id);
        if (entity != null) {
            try {
                entity.getClass().getMethod("setStatus", String.class).invoke(entity, status);
                getService().updateById(entity);
            } catch (Exception e) {
                throw new BusinessException("更新状态失败：" + e.getMessage());
            }
        }
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }


    /** 可排序列白名单（数据库列名）。子类覆盖以启用列头排序；默认空=不支持排序 */
    protected Set<String> getSortableColumns() {
        return Set.of();
    }
}