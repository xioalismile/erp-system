-- 181 电子签名改为手写签名（幂等）
-- 背景：原签名弹窗要求输入登录密码；用户口径改为「只手写签名，不输密码」，签名图需永久留痕。
-- 说明：sign_image 存手写签名图（前端 canvas 导出的 PNG DataURL）；sign_method 记签名方式。
-- 存量数据：历史密码签名记录 sign_method 置「密码」，不做删除、不做覆盖。

ALTER TABLE signature_record ADD COLUMN IF NOT EXISTS sign_image TEXT;
ALTER TABLE signature_record ADD COLUMN IF NOT EXISTS sign_method VARCHAR(20);
UPDATE signature_record SET sign_method = '密码' WHERE sign_method IS NULL OR sign_method = '';
ALTER TABLE signature_record ALTER COLUMN sign_method SET DEFAULT '手写';

COMMENT ON COLUMN signature_record.sign_image IS '手写签名图（PNG DataURL，只增不改）';
COMMENT ON COLUMN signature_record.sign_method IS '签名方式：手写 / 密码';
