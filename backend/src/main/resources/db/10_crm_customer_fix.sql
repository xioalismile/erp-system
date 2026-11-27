-- ===========================================================================
-- CRM模块 - 客户档案CRM扩展字段 (PostgreSQL)
-- 文档：第七部分 7.1 客户档案（CRM扩展字段）
-- 说明：在基础资料 customer 表基础上，新增24个CRM扩展字段，支持客户全生命周期管理
--   档案状态：潜在客户 / 意向客户 / 正式客户 / 休眠客户 / 已流失
--   字段范围：序号 20-43 (序号 1-19 为基础字段，已在 customer 表中存在)
-- ===========================================================================

-- 客户来源 (线上推广/线下展会/客户转介绍/市场活动/其他)
ALTER TABLE customer ADD COLUMN IF NOT EXISTS source_type VARCHAR(30);
COMMENT ON COLUMN customer.source_type IS '客户来源: 线上推广/线下展会/客户转介绍/市场活动/其他';

-- 来源详情
ALTER TABLE customer ADD COLUMN IF NOT EXISTS source_detail VARCHAR(200);
COMMENT ON COLUMN customer.source_detail IS '来源详情: 具体来源描述';

-- 所属行业 (制造业/零售业/服务业/IT/金融/医疗/教育/其他)
ALTER TABLE customer ADD COLUMN IF NOT EXISTS industry VARCHAR(50);
COMMENT ON COLUMN customer.industry IS '所属行业: 制造业/零售业/服务业/IT/金融/医疗/教育/其他';

-- 公司规模 (10人以下/10-50人/50-100人/100-500人/500人以上)
ALTER TABLE customer ADD COLUMN IF NOT EXISTS company_scale VARCHAR(30);
COMMENT ON COLUMN customer.company_scale IS '公司规模: 10人以下/10-50人/50-100人/100-500人/500人以上';

-- 年营业额
ALTER TABLE customer ADD COLUMN IF NOT EXISTS annual_revenue NUMERIC(18,2) DEFAULT 0.00;
COMMENT ON COLUMN customer.annual_revenue IS '年营业额: 客户的年度营业收入';

-- 微信
ALTER TABLE customer ADD COLUMN IF NOT EXISTS wechat VARCHAR(50);
COMMENT ON COLUMN customer.wechat IS '客户微信账号';

-- 公司网站
ALTER TABLE customer ADD COLUMN IF NOT EXISTS website VARCHAR(200);
COMMENT ON COLUMN customer.website IS '客户公司官网地址';

-- CRM状态 (潜在客户/意向客户/正式客户/休眠客户/已流失)
ALTER TABLE customer ADD COLUMN IF NOT EXISTS crm_status VARCHAR(20) NOT NULL DEFAULT '潜在客户';
COMMENT ON COLUMN customer.crm_status IS 'CRM状态: 潜在客户/意向客户/正式客户/休眠客户/已流失';

-- 最后联系日期
ALTER TABLE customer ADD COLUMN IF NOT EXISTS last_contact_date DATE;
COMMENT ON COLUMN customer.last_contact_date IS '最后联系日期: 最后一次联系客户的日期';

-- 下次联系日期
ALTER TABLE customer ADD COLUMN IF NOT EXISTS next_contact_date DATE;
COMMENT ON COLUMN customer.next_contact_date IS '下次联系日期: 计划下次联系的日期';

-- 重要程度 (VIP/重要/普通/不重要)
ALTER TABLE customer ADD COLUMN IF NOT EXISTS important_level VARCHAR(20) DEFAULT '普通';
COMMENT ON COLUMN customer.important_level IS '重要程度: VIP/重要/普通/不重要';

-- 潜在价值
ALTER TABLE customer ADD COLUMN IF NOT EXISTS potential_value NUMERIC(18,2) DEFAULT 0.00;
COMMENT ON COLUMN customer.potential_value IS '潜在价值: 客户潜在订单价值预估';

-- 满意度评分 (1-5分)
ALTER TABLE customer ADD COLUMN IF NOT EXISTS satisfaction_score INT DEFAULT 0;
COMMENT ON COLUMN customer.satisfaction_score IS '满意度评分: 1-5分，来自满意度调查';

-- 累计退款金额
ALTER TABLE customer ADD COLUMN IF NOT EXISTS refund_amount NUMERIC(18,2) DEFAULT 0.00;
COMMENT ON COLUMN customer.refund_amount IS '累计退款金额: 客户累计退款总金额';

-- 投诉次数
ALTER TABLE customer ADD COLUMN IF NOT EXISTS complaint_count INT DEFAULT 0;
COMMENT ON COLUMN customer.complaint_count IS '投诉次数: 客户累计投诉次数';

-- 商机数量 (系统自动统计)
ALTER TABLE customer ADD COLUMN IF NOT EXISTS business_opportunity_count INT DEFAULT 0;
COMMENT ON COLUMN customer.business_opportunity_count IS '商机数量: 关联的商机总数，系统自动统计';

-- 订单数量 (系统自动统计)
ALTER TABLE customer ADD COLUMN IF NOT EXISTS order_count INT DEFAULT 0;
COMMENT ON COLUMN customer.order_count IS '订单数量: 关联的销售订单总数，系统自动统计';

-- 累计订单金额 (系统自动统计)
ALTER TABLE customer ADD COLUMN IF NOT EXISTS total_order_amount NUMERIC(18,2) DEFAULT 0.00;
COMMENT ON COLUMN customer.total_order_amount IS '累计订单金额: 客户累计订单总金额，系统自动统计';

-- 客户标签 (多标签，用逗号分隔)
ALTER TABLE customer ADD COLUMN IF NOT EXISTS tag VARCHAR(500);
COMMENT ON COLUMN customer.tag IS '客户标签: 多标签，用逗号分隔';

-- 销售阶段 (初步接触/需求确认/方案报价/商务谈判/成交/已流失)
ALTER TABLE customer ADD COLUMN IF NOT EXISTS sales_stage VARCHAR(30);
COMMENT ON COLUMN customer.sales_stage IS '销售阶段: 初步接触/需求确认/方案报价/商务谈判/成交/已流失';

-- 意向等级 (A/B/C/D)
ALTER TABLE customer ADD COLUMN IF NOT EXISTS intention_level VARCHAR(10);
COMMENT ON COLUMN customer.intention_level IS '意向等级: A/B/C/D';

-- 分配销售员
ALTER TABLE customer ADD COLUMN IF NOT EXISTS assigned_sales_id BIGINT;
COMMENT ON COLUMN customer.assigned_sales_id IS '分配销售员: 负责跟进此客户的销售员';
ALTER TABLE customer ADD CONSTRAINT fk_customer_assigned_sales FOREIGN KEY (assigned_sales_id)
    REFERENCES employee(employee_id) ON DELETE SET NULL;

-- 跟进次数
ALTER TABLE customer ADD COLUMN IF NOT EXISTS follow_up_count INT DEFAULT 0;
COMMENT ON COLUMN customer.follow_up_count IS '跟进次数: 累计跟进记录次数';

-- 是否黑名单 (0-否, 1-是)
ALTER TABLE customer ADD COLUMN IF NOT EXISTS is_blacklist SMALLINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN customer.is_blacklist IS '是否黑名单: 0-否，1-是';

-- ===========================================================================
-- 为 CRM 状态/销售阶段/意向等级 创建索引 (便于按客户全生命周期筛选)
-- ===========================================================================
CREATE INDEX IF NOT EXISTS idx_customer_crm_status ON customer(crm_status);
CREATE INDEX IF NOT EXISTS idx_customer_sales_stage ON customer(sales_stage);
CREATE INDEX IF NOT EXISTS idx_customer_intention_level ON customer(intention_level);
CREATE INDEX IF NOT EXISTS idx_customer_assigned_sales ON customer(assigned_sales_id);
CREATE INDEX IF NOT EXISTS idx_customer_is_blacklist ON customer(is_blacklist);
CREATE INDEX IF NOT EXISTS idx_customer_next_contact ON customer(next_contact_date);
