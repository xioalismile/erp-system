-- 62_i18n.sql
-- 多语言切换 L1：sys_i18n 语言包表 + 设置中心「语言管理」菜单与授权 + 通用外壳种子(zh-CN/en)
-- 幂等：可重复执行，零副作用。
-- 铁律：只做显示层，不碰业务表/状态枚举真实值；缺词回退中文由前端保证。

-- ============ 1. sys_i18n 表 ============
CREATE TABLE IF NOT EXISTS sys_i18n (
    id          BIGSERIAL PRIMARY KEY,
    i18n_key    VARCHAR(512) NOT NULL,
    lang        VARCHAR(10)  NOT NULL,
    text        VARCHAR(500) NOT NULL DEFAULT '',
    module      VARCHAR(50)  NOT NULL DEFAULT 'common',
    updated_by  VARCHAR(50),
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_sys_i18n_key_lang') THEN
        ALTER TABLE sys_i18n ADD CONSTRAINT uk_sys_i18n_key_lang UNIQUE (i18n_key, lang);
    END IF;
END $$;

-- ============ 2. 设置中心「语言管理」菜单（parent=1131 设置中心）============
-- 幂等：已有该菜单时若类型大小写不符（历史遗留 'menu'）纠正为 'MENU'（与其它设置子项一致）
UPDATE sys_menu SET menu_type = 'MENU'
WHERE menu_code = 'system_i18n_manager' AND menu_type = 'menu';
INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, route_path, component_path, is_visible, is_cache, is_enabled, sort_order, permission_code, status, created_at, updated_at)
VALUES ('system_i18n_manager', '语言管理', 1131, 'MENU', '/system/settings?item=i18n-manager', NULL, 1, 0, 1, 23, 'system:i18n:manage', '启用', NOW(), NOW())
ON CONFLICT (menu_code) DO NOTHING;
-- 注：设置中心子项按既有的「只插 sys_menu、管理员走通配/角色路径权限」模式落地，不做 sys_role_permission 直插（本地该表无 permission_code 列）

-- ============ 3. L1 通用外壳种子（zh + en，幂等按 key+lang 判存在）============
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT * FROM (VALUES
        ('common.save',       'common', '保存',       'Save'),
        ('common.confirm',    'common', '确认',       'Confirm'),
        ('common.cancel',     'common', '取消',       'Cancel'),
        ('common.search',     'common', '查询',       'Search'),
        ('common.reset',      'common', '重置',       'Reset'),
        ('common.add',        'common', '新增',       'Add'),
        ('common.edit',       'common', '编辑',       'Edit'),
        ('common.delete',     'common', '删除',       'Delete'),
        ('common.export',     'common', '导出',       'Export'),
        ('common.import',     'common', '导入',       'Import'),
        ('common.operate',    'common', '操作',       'Action'),
        ('common.success',    'common', '操作成功',   'Success'),
        ('common.loading',    'common', '加载中...',  'Loading...'),
        ('common.nodata',     'common', '暂无数据',   'No data'),
        ('common.logout',     'common', '退出登录',   'Logout'),
        ('common.refresh',    'common', '刷新页面',   'Refresh'),
        ('common.workbench',  'common', '工作台',     'Dashboard'),
        ('common.settingsCenter', 'common', '设置中心', 'Settings Center'),
        ('common.switchTenant','common', '切换租户',  'Switch Tenant'),
        ('common.language',   'common', '语言',       'Language'),
        ('login.title',       'login',  '楷煜智能ERP', 'Kaiyu Smart ERP'),
        ('login.subtitle',    'login',  '欢迎登录',   'Welcome'),
        ('login.username',    'login',  '用户名',     'Username'),
        ('login.password',    'login',  '密码',       'Password'),
        ('login.remember',    'login',  '记住我',     'Remember me'),
        ('login.submit',      'login',  '登 录',      'Sign in'),
        ('login.forgot',      'login',  '忘记密码',   'Forgot password'),
        ('layout.tutorial',   'layout', '新手引导',   'Tutorial')
    ) AS t(i18n_key, module, zh_text, en_text)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = r.i18n_key AND lang = 'zh-CN') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES (r.i18n_key, 'zh-CN', r.zh_text, r.module, 'system', NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = r.i18n_key AND lang = 'en') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES (r.i18n_key, 'en', r.en_text, r.module, 'system', NOW());
        END IF;
    END LOOP;
END $$;

-- ============ 4. L1 外壳补充键（登录页/主框架新增引用）============
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT * FROM (VALUES
        ('common.home',          'common', '首页',   'Home'),
        ('common.user',          'common', '用户',   'User'),
        ('common.switchCompany', 'common', '切换公司', 'Switch Company'),
        ('layout.currentTenant', 'layout', '当前',   'Current'),
        ('layout.noMenu',        'layout', '当前账号暂无可访问的菜单', 'No accessible menu for this account'),
        ('layout.noMenuSub',     'layout', '请联系管理员在「设置中心 → 角色权限」中分配', 'Please ask the administrator to assign permissions in Settings → Role Permissions'),
        ('login.slogan',         'login',  '去掉一切不创造价值的环节，持续健康的盈利！', 'Remove every non-value-adding step, and keep profitable growth!'),
        ('login.rememberPwd',    'login',  '记住账号密码', 'Remember me'),
        ('login.phoneLogin',     'login',  '手机号验证',   'Mobile verification'),
        ('login.phoneDev',       'login',  '开发中，即将上线，敬请期待', 'Coming soon'),
        ('login.success',        'login',  '登录成功',     'Signed in'),
        ('login.successSelectTenant', 'login', '登录成功，请选择公司', 'Signed in, please select a company'),
        ('login.fail',           'login',  '登录失败，请检查账号密码', 'Sign-in failed, please check your account and password')
    ) AS t(i18n_key, module, zh_text, en_text)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = r.i18n_key AND lang = 'zh-CN') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES (r.i18n_key, 'zh-CN', r.zh_text, r.module, 'system', NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = r.i18n_key AND lang = 'en') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES (r.i18n_key, 'en', r.en_text, r.module, 'system', NOW());
        END IF;
    END LOOP;
END $$;

-- ============ 5. L1 菜单名种子（key = menu.<中文菜单名>，与 MainLayout getItemName/getGroupName 口径一致）============
-- 未列出的新增菜单自动回退中文，不影响使用；新增语言时在此追加对应译文即可。
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT * FROM (VALUES
        ('AI数据使用政策','AI Data Usage Policy'),
        ('AI用户额度','AI User Quota'),
        ('AI用量管理','AI Usage Management'),
        ('AI知识库管理','AI Knowledge Base'),
        ('AI租户额度','AI Tenant Quota'),
        ('AI租户自有Key','AI Tenant Own Key'),
        ('BOM','BOM'),
        ('CRM仪表盘','CRM Dashboard'),
        ('CRM预警设置','CRM Alert Settings'),
        ('MRP分析','MRP Analysis'),
        ('报表中心','Reports Center'),
        ('币种','Currency'),
        ('部门档案','Department'),
        ('财务管理','Finance'),
        ('财务看板','Finance Dashboard'),
        ('采购订单','Purchase Order'),
        ('采购发票','Purchase Invoice'),
        ('采购管理','Purchasing'),
        ('采购合同','Purchase Contract'),
        ('采购价格策略','Purchase Price Strategy'),
        ('采购入库单','Purchase Receipt'),
        ('采购申请单','Purchase Requisition'),
        ('采购退货单','Purchase Return'),
        ('采购预付款单','Purchase Prepayment'),
        ('采集结果审核','Acquisition Review'),
        ('菜单名称设置','Menu Name Settings'),
        ('仓储看板','Warehouse Dashboard'),
        ('仓库档案','Warehouse'),
        ('操作日志','Operation Log'),
        ('操作手册','User Manual'),
        ('车间排程看板','Workshop Scheduling Board'),
        ('成本核算','Cost Accounting'),
        ('成本核算配置','Cost Accounting Config'),
        ('打样单','Sample Order'),
        ('打样管理','Sampling'),
        ('打印设置','Print Settings'),
        ('费用分摊','Expense Allocation'),
        ('分析报表','Analysis Reports'),
        ('付款单','Payment Voucher'),
        ('付款条件','Payment Terms'),
        ('跟进超期预警','Follow-up Overdue Alert'),
        ('跟进记录','Follow-up Records'),
        ('工艺路线','Routing'),
        ('工作台','Dashboard'),
        ('公海池','Public Pool'),
        ('供应商档案','Supplier'),
        ('供应商对账单','Supplier Statement'),
        ('货位档案','Storage Location'),
        ('获客管理','Lead Acquisition'),
        ('获客任务','Acquisition Tasks'),
        ('基础信息设置','Basic Info Settings'),
        ('基础资料','Master Data'),
        ('即时库存','Stock on Hand'),
        ('计量单位','Unit of Measure'),
        ('价格等级','Price Level'),
        ('角色权限设置','Role Permissions'),
        ('结算方式','Settlement Method'),
        ('客户档案','Customer'),
        ('客户对账单','Customer Statement'),
        ('客户管理','CRM'),
        ('客户升级部署','Customer Upgrade & Deployment'),
        ('客户投诉','Customer Complaints'),
        ('库存调拨单','Stock Transfer'),
        ('库存管理','Inventory'),
        ('库存流水','Stock Ledger'),
        ('库存盘点单','Stock Take'),
        ('库存预警设置','Inventory Alert Settings'),
        ('联系人管理','Contacts'),
        ('满意度调查','Satisfaction Survey'),
        ('免费样品订单','Free Sample Order'),
        ('批次编号设置','Batch Number Settings'),
        ('批次库存','Batch Stock'),
        ('平台管理','Platform Admin'),
        ('其他出库单','Other Issue'),
        ('其他入库单','Other Receipt'),
        ('企查查登录','Qichacha Login'),
        ('商机管理','Opportunities'),
        ('设置中心','Settings Center'),
        ('审批设置','Approval Settings'),
        ('生产补料单','Production Material Supplement'),
        ('生产拆卸单','Production Disassembly'),
        ('生产工单','Production Work Order'),
        ('生产管理','Production'),
        ('生产领料单','Production Material Requisition'),
        ('生产入库单','Production Receipt'),
        ('生产退料单','Production Material Return'),
        ('生产组装单','Production Assembly'),
        ('收款单','Payment Receipt'),
        ('数据看板','Data Board'),
        ('数据维护','Data Maintenance'),
        ('提成率配置','Commission Rate Settings'),
        ('委外补料单','Subcontract Material Supplement'),
        ('委外发料单','Subcontract Material Issue'),
        ('委外管理','Subcontracting'),
        ('委外加工单','Subcontract Order'),
        ('委外入库单','Subcontract Receipt'),
        ('委外退料单','Subcontract Material Return'),
        ('我的私海','My Private Pool'),
        ('物料档案','Material'),
        ('物料分类','Material Category'),
        ('物流监控看板','Logistics Tracking Board'),
        ('物流配置','Logistics Config'),
        ('系统参数设置','System Parameters'),
        ('消息通知设置','Notification Settings'),
        ('销售报价单','Sales Quotation'),
        ('销售出库单','Sales Delivery'),
        ('销售订单','Sales Order'),
        ('销售发票','Sales Invoice'),
        ('销售管理','Sales'),
        ('销售合同','Sales Contract'),
        ('销售价格策略','Sales Price Strategy'),
        ('销售看板','Sales Dashboard'),
        ('销售漏斗','Sales Funnel'),
        ('销售目标','Sales Target'),
        ('销售退货','Sales Return'),
        ('销售线索','Leads'),
        ('销售预收款单','Sales Prepayment'),
        ('仪表盘','Dashboard'),
        ('银行账户','Bank Account'),
        ('应付账款汇总表','Accounts Payable Summary'),
        ('应收账款汇总表','Accounts Receivable Summary'),
        ('语言管理','Language Management'),
        ('员工档案','Employee'),
        ('员工权限补充','Employee Extra Permissions'),
        ('主题设置','Theme Settings'),
        ('自定义报表','Custom Reports'),
        ('自定义字段设置','Custom Field Settings'),
        ('自动编号设置','Auto Numbering Settings')
    ) AS t(zh_name, en_name)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('menu.' || r.zh_name) AND lang = 'zh-CN') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('menu.' || r.zh_name, 'zh-CN', r.zh_name, 'menu', 'system', NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('menu.' || r.zh_name) AND lang = 'en') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('menu.' || r.zh_name, 'en', r.en_name, 'menu', 'system', NOW());
        END IF;
    END LOOP;
END $$;
