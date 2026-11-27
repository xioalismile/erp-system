package com.erp.customfield.controller;

import com.erp.common.Result;
import com.erp.config.service.SysConfigService;
import com.erp.customfield.entity.CustomField;
import com.erp.customfield.service.CustomFieldService;
import com.erp.masterdata.service.MaterialDedupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 自定义字段系统
 * - 字段定义管理（增删改查元数据）
 * - 字段值管理（按实体实例存取值）
 */
@Slf4j
@RestController
@RequestMapping("/api/custom-field")
@RequiredArgsConstructor
public class CustomFieldController {

    private final CustomFieldService customFieldService;
    private final SysConfigService sysConfigService;
    /** 货品查重规则（设置中心「货品查重字段」）：物料自定义字段值保存时校验 */
    private final MaterialDedupService materialDedupService;

    /** 「自定义字段下拉显示」定制功能总开关（sys_config 键，设置中心「系统参数 → 界面显示」可配） */
    private static final String CFG_DROP_DISPLAY = "ui.custom_field.drop_display.enabled";
    /** 默认开启：保证未配置该键的存量部署升级后行为与改动前完全一致 */
    private static final boolean CFG_DROP_DISPLAY_DEFAULT = true;

    // ========== 字段定义管理 ==========

    /** 查询某实体的所有字段定义 */
    @GetMapping("/definition/{entityType}")
    public Result<List<CustomField>> listDefinition(@PathVariable String entityType) {
        return Result.success(customFieldService.listByEntity(entityType));
    }

    /** 查询某实体启用的字段定义（前端表单渲染用） */
    @GetMapping("/definition/{entityType}/enabled")
    public Result<List<CustomField>> listEnabledDefinition(@PathVariable String entityType) {
        return Result.success(customFieldService.listEnabledByEntity(entityType));
    }

    /** 批量查询多实体的启用字段定义 */
    @PostMapping("/definition/batch")
    public Result<Map<String, List<CustomField>>> batchDefinition(@RequestBody List<String> entityTypes) {
        return Result.success(customFieldService.batchListByEntities(entityTypes));
    }

    /** 新增/修改字段定义 */
    @PostMapping("/definition")
    public Result<CustomField> saveDefinition(@RequestBody CustomField field) {
        return Result.success(customFieldService.saveField(field));
    }

    /** 删除字段定义 */
    @DeleteMapping("/definition/{fieldId}")
    public Result<Void> deleteDefinition(@PathVariable Long fieldId) {
        boolean ok = customFieldService.deleteField(fieldId);
        return ok ? Result.success() : Result.error("字段不存在");
    }

    // ========== 定制功能开关 ==========

    /**
     * 「自定义字段下拉显示」定制功能总开关是否开启（sys_config 键 ui.custom_field.drop_display.enabled，
     * 设置中心「系统参数 → 界面显示」可配）。
     *
     * <p>关闭时前端不再展示该定制功能的任何入口与内容：自定义字段配置页的「下拉显示」开关/属性标签、
     * 销售订单明细物料下拉里追加的自定义字段段。</p>
     *
     * <p>配置缺失或读取异常一律按默认值 {@code true} 处理——绝不因配置问题让功能消失或报错
     * （未配置该键的存量部署行为与改动前完全一致）。</p>
     */
    @GetMapping("/drop-display/enabled")
    public Result<Boolean> dropDisplayEnabled() {
        try {
            return Result.success(sysConfigService.getBooleanValue(CFG_DROP_DISPLAY, CFG_DROP_DISPLAY_DEFAULT));
        } catch (Exception e) {
            log.warn("读取定制开关 {} 失败，按默认开启处理: {}", CFG_DROP_DISPLAY, e.getMessage());
            return Result.success(CFG_DROP_DISPLAY_DEFAULT);
        }
    }

    // ========== 字段值管理 ==========

    /** 获取单个实体实例的所有字段值 */
    @GetMapping("/value/{entityType}/{entityId}")
    public Result<Map<String, Object>> getValues(@PathVariable String entityType, @PathVariable Long entityId) {
        return Result.success(customFieldService.getFieldValues(entityType, entityId));
    }

    /** 批量获取多个实体实例的字段值（列表显示用） */
    @GetMapping("/batch-value/{entityType}")
    public Result<Map<Long, Map<String, Object>>> batchGetValues(
            @PathVariable String entityType,
            @RequestParam List<Long> ids) {
        return Result.success(customFieldService.batchGetFieldValues(entityType, ids));
    }

    /** 保存实体实例的字段值 */
    @PostMapping("/value/{entityType}/{entityId}")
    public Result<Void> saveValues(@PathVariable String entityType,
                                   @PathVariable Long entityId,
                                   @RequestBody Map<String, Object> values) {
        boolean ok = customFieldService.saveFieldValues(entityType, entityId, values);
        return ok ? Result.success() : Result.error("保存失败");
    }

    /** 删除实体实例的所有字段值（实体被删除时调用） */
    @DeleteMapping("/value/{entityType}/{entityId}")
    public Result<Void> deleteValues(@PathVariable String entityType, @PathVariable Long entityId) {
        customFieldService.deleteFieldValues(entityType, entityId);
        return Result.success();
    }

    // ========== 缓存管理 ==========

    @PostMapping("/refresh")
    public Result<Void> refreshCache() {
        customFieldService.refreshCache();
        return Result.success();
    }
}
