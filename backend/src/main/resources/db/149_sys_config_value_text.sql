-- 149: sys_config.config_value 放宽为 TEXT（2026-10-10 小楷模型能力接入整合 · 验证中暴露）
-- 病根：AI 自定义服务商整体以 JSON 数组存 sys_config 单值列（键 ai.providers），API Key 为加密串
--       比原文更长，名称+地址+密文+模型列表合计必然超过 VARCHAR(255) → 保存服务商报「值太长了」。
--       本地库此前从未保存过服务商所以一直未暴露；8 服务器如有真实服务商同样受影响。
-- 修法：varchar(255) → TEXT 向上兼容，存量数据无损、重复执行无副作用（幂等）。
--       SchemaUpgradeEnsurer.ensureSysConfigValueText 已同步（主库 + 每个租户独立库启动自愈全覆盖）。
ALTER TABLE sys_config ALTER COLUMN config_value TYPE TEXT;
