-- 130 付款条件改造（2026-09-28，幂等，可反复执行）
--
-- 背景：付款条件的维护入口从"基础资料页面"改为**单据编辑页/档案页的下拉本身**
--       （输入新名称回车即建、落库；下拉里可停用；停用后历史单据仍显示原值）。
--       同时把"应收/应付到期日"从硬编码改为按账期天数计算，数据源优先级：
--       ① 单据上的付款条件的账期天数 → ② 客户/供应商档案的账期天数 → ③ 都没有则沿用原行为。

-- ① 客户/供应商档案新增「账期天数」
ALTER TABLE customer ADD COLUMN IF NOT EXISTS credit_days INTEGER;
ALTER TABLE supplier ADD COLUMN IF NOT EXISTS credit_days INTEGER;

COMMENT ON COLUMN customer.credit_days IS '账期天数（应收到期日 = 基准日 + 本值；单据上另有付款条件时以付款条件为准）';
COMMENT ON COLUMN supplier.credit_days IS '账期天数（应付到期日 = 基准日 + 本值；单据上另有付款条件时以付款条件为准）';

-- ② 基础资料「付款条件」菜单改为隐藏（**保留菜单行与权限码**，便于以后恢复；
--    同时保留其作为"新增/停用付款条件"按钮权限码的来源）
UPDATE sys_menu
   SET is_visible = 0
 WHERE menu_code = 'basedata_payment-term'
   AND is_visible <> 0;

-- ③ 结果自检（输出到部署日志，便于回读核对）
DO $$
DECLARE menu_visible INT;
BEGIN
    SELECT COUNT(*) INTO menu_visible
      FROM sys_menu
     WHERE menu_code = 'basedata_payment-term' AND is_visible = 1;
    RAISE NOTICE '[130] basedata_payment-term still visible (expect 0) = %', menu_visible;
END $$;
