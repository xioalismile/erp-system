-- =============================================================
-- 41_masterdata_remark.sql
-- 系统全面新增备注字段（基础资料 + 采集相关表）
-- 为下列表幂等添加 remark 列并加注释，重复执行安全
-- =============================================================

-- account 会计科目
DO $do$
BEGIN
  BEGIN
    ALTER TABLE account ADD COLUMN remark TEXT;
    COMMENT ON COLUMN account.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- currency 币种
DO $do$
BEGIN
  BEGIN
    ALTER TABLE currency ADD COLUMN remark TEXT;
    COMMENT ON COLUMN currency.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- customer_segment 客户分类
DO $do$
BEGIN
  BEGIN
    ALTER TABLE customer_segment ADD COLUMN remark TEXT;
    COMMENT ON COLUMN customer_segment.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- customer_tag_dict 客户标签字典
DO $do$
BEGIN
  BEGIN
    ALTER TABLE customer_tag_dict ADD COLUMN remark TEXT;
    COMMENT ON COLUMN customer_tag_dict.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- department 部门
DO $do$
BEGIN
  BEGIN
    ALTER TABLE department ADD COLUMN remark TEXT;
    COMMENT ON COLUMN department.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- employee 员工
DO $do$
BEGIN
  BEGIN
    ALTER TABLE employee ADD COLUMN remark TEXT;
    COMMENT ON COLUMN employee.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- location 货位
DO $do$
BEGIN
  BEGIN
    ALTER TABLE location ADD COLUMN remark TEXT;
    COMMENT ON COLUMN location.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- material 物料
DO $do$
BEGIN
  BEGIN
    ALTER TABLE material ADD COLUMN remark TEXT;
    COMMENT ON COLUMN material.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- material_category 物料分类
DO $do$
BEGIN
  BEGIN
    ALTER TABLE material_category ADD COLUMN remark TEXT;
    COMMENT ON COLUMN material_category.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- payment_term 付款条件
DO $do$
BEGIN
  BEGIN
    ALTER TABLE payment_term ADD COLUMN remark TEXT;
    COMMENT ON COLUMN payment_term.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- price_level 价格等级
DO $do$
BEGIN
  BEGIN
    ALTER TABLE price_level ADD COLUMN remark TEXT;
    COMMENT ON COLUMN price_level.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- settlement_mode 结算方式
DO $do$
BEGIN
  BEGIN
    ALTER TABLE settlement_mode ADD COLUMN remark TEXT;
    COMMENT ON COLUMN settlement_mode.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- supplier 供应商
DO $do$
BEGIN
  BEGIN
    ALTER TABLE supplier ADD COLUMN remark TEXT;
    COMMENT ON COLUMN supplier.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- unit_of_measure 计量单位
DO $do$
BEGIN
  BEGIN
    ALTER TABLE unit_of_measure ADD COLUMN remark TEXT;
    COMMENT ON COLUMN unit_of_measure.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- warehouse 仓库
DO $do$
BEGIN
  BEGIN
    ALTER TABLE warehouse ADD COLUMN remark TEXT;
    COMMENT ON COLUMN warehouse.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- workstation 工位
DO $do$
BEGIN
  BEGIN
    ALTER TABLE workstation ADD COLUMN remark TEXT;
    COMMENT ON COLUMN workstation.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- batch 批次
DO $do$
BEGIN
  BEGIN
    ALTER TABLE batch ADD COLUMN remark TEXT;
    COMMENT ON COLUMN batch.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- acquisition_task 采集任务
DO $do$
BEGIN
  BEGIN
    ALTER TABLE acquisition_task ADD COLUMN remark TEXT;
    COMMENT ON COLUMN acquisition_task.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- acquisition_result 采集结果
DO $do$
BEGIN
  BEGIN
    ALTER TABLE acquisition_result ADD COLUMN remark TEXT;
    COMMENT ON COLUMN acquisition_result.remark IS '备注';
  EXCEPTION WHEN duplicate_column THEN NULL;
  END;
END $do$;

-- =============================================================
-- 验证查询：确认以上表均已具备 remark 列
-- =============================================================
SELECT table_name
FROM information_schema.columns
WHERE column_name = 'remark'
  AND table_name IN (
    'account', 'currency', 'customer_segment', 'customer_tag_dict',
    'department', 'employee', 'location', 'material', 'material_category',
    'payment_term', 'price_level', 'settlement_mode', 'supplier',
    'unit_of_measure', 'warehouse', 'workstation', 'batch',
    'acquisition_task', 'acquisition_result'
  )
ORDER BY 1;
