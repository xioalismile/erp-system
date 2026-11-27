-- 16_deploy_log_nullable.sql  BUILD(打包)日志无服务器，server_id 放宽为可空（幂等）
ALTER TABLE deploy_log ALTER COLUMN server_id DROP NOT NULL;
