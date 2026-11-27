package com.erp.customfield.service;

import com.erp.customfield.entity.CustomField;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface CustomFieldService {

    // ========== 字段定义管理 ==========

    /** 查询某实体的所有字段定义 */
    List<CustomField> listByEntity(String entityType);

    /** 查询某实体启用的字段定义（按 sort_order 排序） */
    List<CustomField> listEnabledByEntity(String entityType);

    /** 新增/修改字段定义 */
    CustomField saveField(CustomField field);

    /** 删除字段定义（同时删除值） */
    boolean deleteField(Long fieldId);

    /** 批量获取多实体的字段定义（按 entityType 分组） */
    Map<String, List<CustomField>> batchListByEntities(List<String> entityTypes);

    // ========== 字段值管理 ==========

    /** 获取单个实体实例的所有字段值（Map: fieldKey -> value） */
    Map<String, Object> getFieldValues(String entityType, Long entityId);

    /** 批量获取多个实体实例的字段值（Map: entityId -> (fieldKey -> value)） */
    Map<Long, Map<String, Object>> batchGetFieldValues(String entityType, List<Long> entityIds);

    /** 保存实体实例的字段值（upsert，同时同步 ext_data JSONB） */
    boolean saveFieldValues(String entityType, Long entityId, Map<String, Object> values);

    /** 删除实体实例的所有字段值（实体被删除时调用） */
    boolean deleteFieldValues(String entityType, Long entityId);

    // ========== 缓存 ==========

    /** 刷新缓存 */
    void refreshCache();

    // ========== 实体类型 / 表名映射（供自定义报表等按表名反查自定义字段） ==========

    /** 实体类型 → 业务表名（未开放自定义字段的实体返回 null） */
    String resolveTableName(String entityType);

    /** 业务表名 → 实体类型（未开放自定义字段的表返回 null） */
    String resolveEntityType(String tableName);

    /** 全部支持自定义字段的实体类型 */
    Set<String> allEntityTypes();
}
