-- 工序转移单(process_transfer)补齐前端在用但主表缺失的扩展字段
-- 背景：ProcessTransferEdit.vue 表单使用了 materialCode/materialModel/workCenterName/
--       fromWarehouseId/toWarehouseId/attachmentUrls，但 process_transfer 主表无对应列，
--       导致这些值保存不落库、详情回显为空。
-- 说明：全部使用 ADD COLUMN IF NOT EXISTS 幂等写法，重复执行安全。

ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS material_code VARCHAR(50);
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS material_model VARCHAR(100);
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS work_center_name VARCHAR(100);
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS from_warehouse_id BIGINT;
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS to_warehouse_id BIGINT;
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS attachment_urls TEXT;

COMMENT ON COLUMN process_transfer.material_code IS '物料编码';
COMMENT ON COLUMN process_transfer.material_model IS '物料规格型号';
COMMENT ON COLUMN process_transfer.work_center_name IS '工作中心名称';
COMMENT ON COLUMN process_transfer.from_warehouse_id IS '转出仓库ID';
COMMENT ON COLUMN process_transfer.to_warehouse_id IS '接收仓库ID';
COMMENT ON COLUMN process_transfer.attachment_urls IS '附件地址(多个逗号分隔)';
