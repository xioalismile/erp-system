package com.erp.basedata.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.Result;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/page")
    public Result<Page<T>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
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
        qw.orderByDesc(getIdField());
        return Result.success(getService().page(page, qw));
    }

    @GetMapping("/list")
    public Result<List<T>> list(@RequestParam(required = false) String status) {
        QueryWrapper<T> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) qw.eq("status", status);
        qw.orderByAsc(getCodeField());
        return Result.success(getService().list(qw));
    }

    @GetMapping("/{id}")
    public Result<T> get(@PathVariable ID id) {
        return Result.success(getService().getById(id));
    }

    @PostMapping
    public Result<Void> save(@RequestBody T entity) {
        // 编码唯一性校验
        if (getCodeField() != null) {
            try {
                Object codeVal = entity.getClass().getMethod("get" + capitalize(getCodeField())).invoke(entity);
                if (codeVal != null) {
                    long count = getService().count(new QueryWrapper<T>().eq(getCodeField(), codeVal));
                    if (count > 0) {
                        return Result.error(409, "编码已存在");
                    }
                }
            } catch (Exception ignored) {}
        }
        getService().save(entity);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody T entity) {
        // 编辑时排除自身的编码唯一性校验
        if (getCodeField() != null && getIdField() != null) {
            try {
                Object codeVal = entity.getClass().getMethod("get" + capitalize(getCodeField())).invoke(entity);
                Object idVal = entity.getClass().getMethod("get" + capitalize(getIdField())).invoke(entity);
                if (codeVal != null && idVal != null) {
                    long count = getService().count(new QueryWrapper<T>()
                            .eq(getCodeField(), codeVal).ne(getIdField(), idVal));
                    if (count > 0) {
                        return Result.error(409, "编码已存在");
                    }
                }
            } catch (Exception ignored) {}
        }
        getService().updateById(entity);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable ID id) {
        getService().removeById(id);
        return Result.success();
    }

    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody List<ID> ids) {
        getService().removeBatchByIds(ids);
        return Result.success();
    }

    @PostMapping("/enable/{id}")
    public Result<Void> enable(@PathVariable ID id) {
        updateStatus(id, "启用");
        return Result.success();
    }

    @PostMapping("/disable/{id}")
    public Result<Void> disable(@PathVariable ID id) {
        updateStatus(id, "停用");
        return Result.success();
    }

    private void updateStatus(ID id, String status) {
        T entity = getService().getById(id);
        if (entity != null) {
            try {
                entity.getClass().getMethod("setStatus", String.class).invoke(entity, status);
                getService().updateById(entity);
            } catch (Exception ignored) {}
        }
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
