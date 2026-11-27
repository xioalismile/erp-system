package com.erp.ai.dto;

import com.baomidou.mybatisplus.extension.service.IService;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class EntityCrudConfig {
    private String intentPrefix;
    private Class<?> entityClass;
    private Class<? extends IService> serviceClass;
    private String serviceBeanName;
    private String primaryKeyField;
    private String primaryKeyColumn;
    private List<String> searchFields;
    private List<String> displayFields;
    private String nameField;
    private String codeField;
    private boolean isMasterData;
    private String aiFilterColumn;
    private Object aiFilterValue;
    private String aiFilterOperator;

    /**
     * 允许 AI 按条件筛选的字段白名单（单表真实字段名，如 status/orderDate/customerId）。
     * 空 = 不开放条件筛选（AI 只能按关键词模糊搜）。
     * 2026-09-18 新增：让"按状态/日期区间/客户查单"这类**列表页搜索条件**能在对话里用。
     */
    private List<String> filterFields;

    /**
     * 该实体的业务默认排序（数据库列名，逐列升序）。
     * 空 = 沿用全系统"最新在前"（created_at desc）。
     * 2026-09-29 新增：库存这类**非时序**数据按 created_at 排序没有意义（历史数据 created_at 多为空，
     * 表现为"顺序看不懂"），改为按物料编码排序，稳定且可读。
     */
    private List<String> orderByColumns;

    /**
     * 字段中文名映射（字段名 → 中文标签，2026-11-22 表达层机制重构新增）。
     * 由 AI 查询配置自动注册机制生成（实体 @ExportField 标签 + 补全键 + 派生列），
     * 随查询结果以 "labels" 下发；前端列头优先取此映射，缺失时兜底走本地字典。
     */
    private Map<String, String> labels;
}
