-- 110_purchase_request_close_date.sql
-- 目的：修复「采购申请单关闭」接口一直报 500 的既有缺陷（2026-09-20 阶段6 全量验证时发现）。
-- 根因：PurchaseRequestServiceImpl.close() 的 UpdateWrapper 写 close_date 列，
--      而 purchase_request 表当前**没有该列** —— 建表脚本 db/12_purchase_modules_fix.sql:22
--      本就有这条 ALTER，但本环境未应用（代码侧全仓仅此一处写 close_date，故一直 500）。
-- 幂等：ADD COLUMN IF NOT EXISTS（用 information_schema 守卫，表不存在自动跳过）。
-- 同内容文件另存 deploy/sql/67_purchase_request_close_date.sql（守则04 编号体系）。

DO $$
BEGIN
    IF to_regclass('purchase_request') IS NULL THEN
        RAISE NOTICE '110: purchase_request 不存在，跳过';
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'purchase_request' AND column_name = 'close_date'
    ) THEN
        ALTER TABLE purchase_request ADD COLUMN close_date TIMESTAMP;
        RAISE NOTICE '110: purchase_request.close_date 已补齐（修复关闭接口 500）';
    ELSE
        RAISE NOTICE '110: purchase_request.close_date 已存在，无需处理';
    END IF;
END $$;
