# 把供应商签发的 license.lic 文件放到此目录
# docker-compose.yml 会把整个 license 目录挂载到 backend 容器的 /app/license/
#
# 部署步骤：
# 1. 供应商用 LicenseGenerator 生成 license.lic
# 2. 把 license.lic 上传到此目录（与 docker-compose.yml 同级的 license/）
# 3. 在项目根目录创建 .env 文件，填入供应商提供的 ERP_LICENSE_PUBLIC_KEY
# 4. docker compose up -d --build
