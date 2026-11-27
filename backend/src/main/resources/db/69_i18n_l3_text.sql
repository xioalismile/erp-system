-- 69_i18n_l3_text.sql
-- 多语言切换 L3：界面文案（text.<中文原文>）与字典值（dict.<中文值>）语言包种子
-- 用途：$tt(中文原文) 提示语/按钮/标题/placeholder/校验语；$dt(中文值) 状态/枚举/写死下拉显示值
-- 维护：设置中心 → 语言管理（module=text / dict），本文件是初始种子
-- 幂等：按 (i18n_key, lang) 先查后插，可重复执行；不修改任何业务表
-- 铁律：只翻显示层；dict.* 的真实值（后端枚举/中文）一律不变

-- ============ 1. 字典值：单据状态（全站唯一出口 utils/bill-status.js）============
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT * FROM (VALUES
        ('草稿',        'Draft'),
        ('已驳回',      'Rejected'),
        ('已取消',      'Cancelled'),
        ('待审核',      'Pending Approval'),
        ('待审批',      'Pending Approval'),
        ('审批中',      'In Approval'),
        ('已审核',      'Approved'),
        ('已生效',      'Approved'),   -- 2026-09-19 终态改名；上一行为历史别名，保留兼容
        ('已确认',      'Confirmed'),
        ('已下达',      'Issued'),
        ('执行中',      'In Progress'),
        ('已完工',      'Completed'),
        ('部分入库',    'Partially Received'),
        ('全部入库',    'Fully Received'),
        ('已关闭',      'Closed'),
        ('已作废',      'Voided'),
        ('作废',        'Voided'),
        ('已废弃',      'Deprecated'),
        ('已结案',      'Settled'),
        ('启用',        'Enabled'),
        ('停用',        'Disabled'),
        ('是',          'Yes'),
        ('否',          'No'),
        ('开',          'On'),
        ('关',          'Off'),
        ('全部',        'All'),
        ('正常',        'Normal'),
        ('逾期',        'Overdue'),
        ('异常',        'Abnormal')
    ) AS t(zh_text, en_text)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('dict.' || r.zh_text) AND lang = 'zh-CN') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('dict.' || r.zh_text, 'zh-CN', r.zh_text, 'dict', 'system', NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('dict.' || r.zh_text) AND lang = 'en') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('dict.' || r.zh_text, 'en', r.en_text, 'dict', 'system', NOW());
        END IF;
    END LOOP;
END $$;

-- ============ 2. 字典值：数据范围（设置中心→角色权限 写死下拉）============
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT * FROM (VALUES
        ('全部数据',        'All Data'),
        ('本部门及子部门',  'Department & Sub-departments'),
        ('本部门数据',      'Department Data'),
        ('仅本人数据',      'Own Data Only'),
        ('自定义',          'Custom'),
        ('未设置',          'Not Set'),
        ('未知',            'Unknown')
    ) AS t(zh_text, en_text)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('dict.' || r.zh_text) AND lang = 'zh-CN') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('dict.' || r.zh_text, 'zh-CN', r.zh_text, 'dict', 'system', NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('dict.' || r.zh_text) AND lang = 'en') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('dict.' || r.zh_text, 'en', r.en_text, 'dict', 'system', NOW());
        END IF;
    END LOOP;
END $$;

-- ============ 3. 字典值：内置角色名（设置中心→角色权限 写死映射）============
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT * FROM (VALUES
        ('超级管理员', 'Super Admin'),
        ('总经理',     'General Manager'),
        ('销售主管',   'Sales Manager'),
        ('销售员',     'Salesperson'),
        ('采购主管',   'Purchasing Manager'),
        ('采购员',     'Purchaser'),
        ('仓库主管',   'Warehouse Manager'),
        ('仓管员',     'Warehouse Keeper'),
        ('生产主管',   'Production Manager'),
        ('生产员',     'Production Staff'),
        ('财务主管',   'Finance Manager'),
        ('会计',       'Accountant'),
        ('查看者',     'Viewer'),
        ('管理员',     'Administrator'),
        ('普通',       'Normal')
    ) AS t(zh_text, en_text)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('dict.' || r.zh_text) AND lang = 'zh-CN') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('dict.' || r.zh_text, 'zh-CN', r.zh_text, 'dict', 'system', NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('dict.' || r.zh_text) AND lang = 'en') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('dict.' || r.zh_text, 'en', r.en_text, 'dict', 'system', NOW());
        END IF;
    END LOOP;
END $$;

-- ============ 4. 界面文案：公共构件层（CrudTable/BaseEdit/BillListPage/useBillActions 等）============
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT * FROM (VALUES
        ('请输入',            'Please enter'),
        ('请选择',            'Please select'),
        ('列设置',            'Column Settings'),
        ('固定列',            'Freeze Column'),
        ('取消固定',          'Unfreeze Column'),
        ('暂无数据',          'No data'),
        ('操作成功',          'Success'),
        ('操作失败',          'Operation failed'),
        ('保存成功',          'Saved successfully'),
        ('保存失败',          'Save failed'),
        ('删除成功',          'Deleted successfully'),
        ('删除失败',          'Delete failed'),
        ('删除',              'Delete'),
        ('编辑',              'Edit'),
        ('新增',              'Add'),
        ('查询',              'Search'),
        ('重置',              'Reset'),
        ('取消',              'Cancel'),
        ('确定',              'OK'),
        ('保存',              'Save'),
        ('导出',              'Export'),
        ('导入',              'Import'),
        ('提示',              'Notice'),
        ('警告',              'Warning'),
        ('导出失败',          'Export failed'),
        ('导入失败',          'Import failed'),
        ('数据加载失败',      'Failed to load data'),
        ('登录已过期，请重新登录', 'Session expired, please sign in again'),
        ('没有权限执行此操作', 'You do not have permission to perform this action'),
        ('操作失败，请重试',  'Operation failed, please retry'),
        ('该字段不能为空',    'This field is required'),
        ('未找到下推关系配置', 'Push-down relationship not configured'),
        ('请先保存源单据后再下推', 'Please save the source bill before pushing down'),
        ('当前状态不可审核',  'Cannot approve in the current status'),
        ('当前没有可审批的任务', 'No task available for approval'),
        ('请求的数据不存在',  'The requested data does not exist'),
        ('数据操作失败，请联系管理员（SQL错误）', 'Data operation failed, please contact the administrator (SQL error)'),
        ('页面资源加载失败，请按 Ctrl+F5 强制刷新浏览器', 'Failed to load page resources. Press Ctrl+F5 to force refresh.')
    ) AS t(zh_text, en_text)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('text.' || r.zh_text) AND lang = 'zh-CN') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('text.' || r.zh_text, 'zh-CN', r.zh_text, 'text', 'system', NOW());
        END IF;
        IF NOT EXISTS (SELECT 1 FROM sys_i18n WHERE i18n_key = ('text.' || r.zh_text) AND lang = 'en') THEN
            INSERT INTO sys_i18n (i18n_key, lang, text, module, updated_by, updated_at)
            VALUES ('text.' || r.zh_text, 'en', r.en_text, 'text', 'system', NOW());
        END IF;
    END LOOP;
END $$;
