package com.erp.permission.service;

import com.erp.customfield.entity.CustomField;
import com.erp.customfield.service.CustomFieldService;
import com.erp.permission.dto.FieldMetaDTO;
import com.erp.report.custom.core.EntityMetadataRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 字段权限元数据服务（动态元数据）。
 *
 * 页面标识 page_key = 单据表名（如 sale_order），与自定义字段 entity_type、
 * EntityMetadataRegistry 数据源 key 同一口径。
 * 字段来源：
 *  1. EntityMetadataRegistry 表头字段（运行时反射实体生成，field=camelCase）
 *  2. 自定义字段（fieldKey / fieldLabel / fieldType）
 * registry 中未注册的 page_key 返回空列表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldMetaService {

    private final EntityMetadataRegistry registry;
    private final CustomFieldService customFieldService;

    /** 元数据缓存（registry 构建后内容稳定；自定义字段变更走自定义字段模块自身的缓存失效） */
    private final Map<String, List<FieldMetaDTO>> cache = new ConcurrentHashMap<>();

    /**
     * 页面级"系统写死必填"字段：这些字段在业务页面上是静态必填（用户无法取消），
     * 因此字段权限配置页必须把它们的「必填」锁定为开（🔒 不可取消），
     * 否则会出现"配置页说能关、页面照样必填"的口径分裂（2026-09-15 物料档案修复）。
     * 维护约定：与对应页面模板里的静态必填保持一致——
     * material ↔ frontend/src/views/basedata/MaterialEdit.vue 的 STATIC_REQUIRED。
     */
    private static final Map<String, Set<String>> PAGE_LOCKED_REQUIRED = Map.of(
            "material", Set.of("materialType", "baseUnitId", "status")
    );

    /**
     * 页面级"解锁"字段：实体上标了 @ExportField(required=true)（系统默认必填），
     * 但在该业务页面上是**由系统生成、允许留空**的字段，不应锁定为必填。
     * material.materialCode：编码为空时后端会自动生成（MaterialServiceImpl.save 会按分类规则/流水号补齐），
     * 因此它既不该在配置页显示为锁定必填，页面也不该强制用户填写。
     */
    private static final Map<String, Set<String>> PAGE_UNLOCKED_REQUIRED = Map.of(
            "material", Set.of("materialCode")
    );

    /**
     * 页面级"无输入口字段"：这些字段在业务页面上**没有任何可录入控件**
     * （审计字段 / 仅导入导出用的 ID 列 / 系统派生的展示字段），
     * 因此字段权限配置页不应列出它们，也不应参与"角色必填"解析与导入必填校验
     * —— 否则用户配了"必填"却无处可填，只会产生"配了不生效"的困惑（2026-09-15 用户反馈）。
     *
     * <p>维护约定：与对应页面模板里的控件绑定保持一致——
     * material ↔ frontend/src/views/basedata/MaterialEdit.vue 的 PAGE_FIELD_LABELS
     * （该清单有 = 页面有输入口；不在其中即视为无输入口）。</p>
     */
    private static final Map<String, Set<String>> PAGE_FIELDS_WITHOUT_INPUT = Map.of(
            "material", Set.of(
                    "materialId",           // 主键 ID，页面无控件
                    "supplyType",           // 供应类型，页面无控件
                    "defaultSupplierId",    // 默认供应商 ID，页面无控件
                    "defaultSupplierName",  // 默认供应商名称，页面无控件（仅导出/导入用）
                    "stockUnitId",          // 库存单位 ID，页面无控件（仅导入用）
                    "productionUnitId",     // 生产单位 ID，页面无控件（仅导入用）
                    "unitConversion",       // 单位换算率，页面无控件
                    "mainImage",            // 主图 URL：由 ImageUploader 写入、非用户直填，后端还会按 imageUrls 自动兜底
                    "createdAt",            // 审计字段
                    "updatedAt",            // 审计字段
                    "createdBy",            // 审计字段
                    "updatedBy"             // 审计字段
            )
    );

    /** 计算某页某字段的 defaultRequired（实体默认必填 叠加 页面级锁定/解锁） */
    private int resolveDefaultRequired(String pageKey, String fieldKey, boolean entityDefault) {
        if (fieldKey == null || fieldKey.isBlank()) {
            return entityDefault ? 1 : 0;
        }
        if (PAGE_LOCKED_REQUIRED.getOrDefault(pageKey, Collections.emptySet()).contains(fieldKey)) {
            return 1;
        }
        if (PAGE_UNLOCKED_REQUIRED.getOrDefault(pageKey, Collections.emptySet()).contains(fieldKey)) {
            return 0;
        }
        return entityDefault ? 1 : 0;
    }

    public List<FieldMetaDTO> getFieldsByPage(String pageKey) {
        if (pageKey == null || pageKey.isBlank()) return Collections.emptyList();
        return cache.computeIfAbsent(pageKey.trim(), this::buildFields);
    }

    public Set<String> getAvailablePageKeys() {
        // registry 中全部数据源均可用（按需扩展为白名单）
        return registry.listDataSources().stream()
                .map(m -> String.valueOf(m.get("key")))
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    private List<FieldMetaDTO> buildFields(String pageKey) {
        List<FieldMetaDTO> fields = new ArrayList<>();
        // 1) 表头字段（registry 元数据，camelCase）
        EntityMetadataRegistry.DataSourceMeta meta = registry.getDataSource(pageKey);
        if (meta != null) {
            int sort = 1;
            // 页面无输入口的字段（审计字段/仅导入用 ID 列等）不列出、也不参与必填解析
            Set<String> noInputKeys = PAGE_FIELDS_WITHOUT_INPUT.getOrDefault(pageKey, Collections.emptySet());
            for (EntityMetadataRegistry.FieldMeta fm : meta.fields.values()) {
                if (fm.field != null && noInputKeys.contains(fm.field)) {
                    continue;
                }
                FieldMetaDTO dto = new FieldMetaDTO();
                dto.setFieldKey(fm.field);
                dto.setFieldName(fm.label != null ? fm.label : fm.field);
                dto.setFieldType(fm.type);
                dto.setDefaultVisible(1);
                dto.setDefaultEditable(1);
                // 系统默认必填：取实体 @ExportField(required=true)，即单据最初的必填项，前端不能关闭；
                // 再叠加"页面级锁定必填 / 解锁"（2026-09-15，见 PAGE_LOCKED_REQUIRED 说明）
                dto.setDefaultRequired(resolveDefaultRequired(pageKey, fm.field, fm.defaultRequired));
                dto.setSortOrder(sort++);
                fields.add(dto);
            }
        }
        // 2) 自定义字段（表头级，追加在标准字段之后）
        try {
            for (CustomField cf : customFieldService.listEnabledByEntity(pageKey)) {
                FieldMetaDTO dto = new FieldMetaDTO();
                dto.setFieldKey(cf.getFieldKey());
                dto.setFieldName(cf.getFieldLabel() != null ? cf.getFieldLabel() : cf.getFieldKey());
                dto.setFieldType(cf.getFieldType() != null ? cf.getFieldType() : "text");
                dto.setDefaultVisible(1);
                dto.setDefaultEditable(1);
                // 自定义字段的"必填"：勾选后用户不可取消（规则与系统默认必填一致）
                dto.setDefaultRequired(cf.getIsRequired() != null && cf.getIsRequired() == 1 ? 1 : 0);
                dto.setSortOrder(9000 + (cf.getSortOrder() != null ? cf.getSortOrder() : 0));
                fields.add(dto);
            }
        } catch (Exception e) {
            log.warn("加载自定义字段元数据失败: pageKey={}, err={}", pageKey, e.getMessage());
        }
        return fields;
    }
}
