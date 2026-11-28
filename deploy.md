# 智能ERP系统 - 部署操作手册

## 环境要求
- 服务器：Linux Ubuntu 22.04（阿里云ECS或其他云服务器）
- 最低配置：2核CPU / 4G内存 / 40G磁盘
- 已安装：Docker 24+ 和 Docker Compose 2.20+

## ★ 重要：License 授权部署（新增）

本系统采用 **License 离线授权** 控制使用期限，部署前必须先完成 License 配置。

### L1. 供应商签发 License（供应商内部操作，不交付客户）

```bash
# 1. 生成 RSA 密钥对（仅一次，永不交付客户）
docker run --rm -v $(pwd):/work -w /work maven:3.9-eclipse-temurin-17 \
  bash -c "mvn -q compile -DskipTests && java -cp target/classes com.erp.license.generator.LicenseKeyGenerator"
# 输出私钥和公钥，分别保存到 private_key_base64.txt 和 public_key_base64.txt

# 2. 为客户生成 License 文件
docker run --rm -v $(pwd):/work -w /work maven:3.9-eclipse-temurin-17 \
  java -cp target/classes com.erp.license.generator.LicenseGenerator \
  --customer "客户A公司" \
  --project "智能ERP" \
  --mac "客户服务器MAC地址(可选)" \
  --expire 2027-12-31 \
  --maxUsers 50 \
  --features "sale,purchase,inventory,production,finance" \
  --privateKeyFile private_key_base64.txt \
  --output license.lic
# 生成 license.lic 文件，交付给客户
# 同时把 public_key_base64.txt 内容告知客户（作为环境变量 ERP_LICENSE_PUBLIC_KEY）
```

### L2. 客户部署 License（客户服务器操作）

```bash
# 1. 上传 license.lic 到项目根目录的 license/ 文件夹下
mkdir -p license
# 把供应商给的 license.lic 上传到 license/ 目录

# 2. 创建 .env 配置文件
cp .env.example .env
vi .env

# .env 文件内容（关键配置项）：
# ERP_LICENSE_PUBLIC_KEY=<供应商提供的公钥Base64>
# ERP_JWT_SECRET=<openssl rand -base64 32 生成的随机字符串>

# 3. 启动系统（详见下一节）
docker compose up -d --build

# 4. 验证 License 是否生效
curl http://localhost:8080/api/license/status
# 返回 {"valid":true,"customer":"客户A",...} 表示校验通过
# 返回 {"valid":false,"failureReason":"..."} 表示校验失败，需排查
```

### L3. License 续期 / 更换客户

License 文件通过 docker-compose 挂载（不打入镜像），续期只需替换文件并重启：

```bash
# 1. 把新的 license.lic 覆盖到 license/ 目录
cp 新license.lic license/license.lic

# 2. 重启 backend 容器（重新加载 License）
docker compose restart backend

# 3. 验证
curl http://localhost:8080/api/license/status
```

### L4. License 失效行为

| 失败场景 | 系统行为 |
|---------|---------|
| license.lic 文件缺失 | Spring Boot 启动失败，容器退出（restart: always 会持续重试，需修复 License 后 `docker compose up -d`） |
| 签名校验失败（被篡改） | 启动失败 + 日志报"License 签名校验失败" |
| 已过期 | 启动失败 + 日志报"License 已过期" |
| MAC 不匹配 | 启动失败 + 日志报"MAC 地址不在授权范围" |
| 运行中 24h 心跳校验失败 | 日志报错，所有 /api/** 请求返回 403（容器仍运行，用户可见提示） |
| License 文件运行中被替换 | 24h 心跳校验检测到，返回 403 |

排查 License 失败：
```bash
# 查看 backend 启动日志
docker compose logs --tail 50 backend | grep -A 5 License
# 常见错误：
#   "License 文件不存在" → 检查 license/license.lic 是否上传
#   "RSA 公钥未配置" → 检查 .env 的 ERP_LICENSE_PUBLIC_KEY
#   "License 签名校验失败" → license.lic 被篡改或与公钥不匹配
#   "License 已过期" → 需联系供应商续期
```

## 一、服务器环境准备

### 1.1 安装Docker（如未安装）
```bash
# 更新apt包索引
sudo apt update

# 安装Docker
curl -fsSL https://get.docker.com | sudo sh

# 将当前用户加入docker组(免sudo)
sudo usermod -aG docker $USER

# 重新登录使组权限生效
exit
# 重新SSH登录

# 验证安装
docker --version
docker compose version
```

### 1.2 安装Docker Compose（如未安装）
```bash
# Docker 24+已内置compose插件,验证:
docker compose version
# 若版本<2.20,手动安装:
sudo apt install docker-compose-plugin
```

## 二、部署项目

### 2.1 上传项目代码
```bash
# 方式一: 从 GitHub 克隆
cd /opt
git clone https://github.com/xioalismile/erp-system.git smart-erp
cd smart-erp

# 方式二: 上传项目压缩包
# 在本地打包: 将整个项目目录打成zip上传到服务器/opt/
cd /opt
unzip smart-erp.zip
cd smart-erp
```

### 2.2 配置 License 和环境变量（**部署前必做**）
```bash
# 1. 配置 License 文件（参考 L2 章节）
mkdir -p license
# 上传 license.lic 到 license/ 目录

# 2. 配置环境变量
cp .env.example .env
vi .env
# 填入：
#   ERP_LICENSE_PUBLIC_KEY=<供应商提供的公钥>
#   ERP_JWT_SECRET=<openssl rand -base64 32>
```

### 2.3 一键构建并启动
```bash
# 在项目根目录执行(包含docker-compose.yml的目录)
docker compose up -d --build
```

此命令会自动完成：
1. 拉取 PostgreSQL 15 / Redis 7 / Nginx / Maven / Node 镜像
2. Maven 编译后端为 JAR 包
3. npm 构建前端静态文件
4. 启动4个容器：postgres / redis / backend / frontend
5. 首次启动时自动执行 `full_schema.sql` 初始化102张表
6. **backend 启动时校验 License，失败则容器退出**

**首次构建约需10-15分钟**（依赖下载+编译），后续重建只需1-2分钟。

### 2.4 查看启动状态
```bash
# 查看容器状态(应全部为Up/healthy)
docker compose ps

# 预期输出:
# NAME            STATUS                   PORTS
# erp-postgres    Up (healthy)             0.0.0.0:5432->5432/tcp
# erp-redis       Up (healthy)             0.0.0.0:6379->6379/tcp
# erp-backend     Up                       0.0.0.0:8080->8080/tcp
# erp-frontend    Up                       0.0.0.0:80->80/tcp
```

### 2.5 验证部署
```bash
# 1. 验证前端页面
curl http://localhost
# 应返回HTML内容(包含<div id="app">)

# 2. 验证后端API
curl http://localhost/api/auth/test
# 或直接访问: http://服务器IP/api/

# 3. 浏览器访问
# 前端: http://服务器IP
# 后端: http://服务器IP:8080
```

## 三、日常运维操作

### 3.1 查看日志
```bash
# 查看所有服务日志
docker compose logs -f

# 只看后端日志(最常用)
docker compose logs -f backend

# 只看后端最近100行
docker compose logs --tail 100 backend

# 查看后端应用日志文件(容器内)
docker exec erp-backend cat /app/logs/erp.log | tail -200
```

### 3.2 重启服务
```bash
# 重启单个服务
docker compose restart backend
docker compose restart frontend

# 重启所有服务
docker compose restart
```

### 3.3 更新代码后重新部署
```bash
# 1. 拉取最新代码
git pull origin main

# 2. 重新构建并启动(只重建变更的服务)
docker compose up -d --build

# 如果只改了后端代码:
docker compose up -d --build backend

# 如果只改了前端代码:
docker compose up -d --build frontend
```

### 3.4 停止和启动
```bash
# 停止所有服务(不删除容器)
docker compose stop

# 启动所有服务
docker compose start

# 停止并删除容器(数据卷保留)
docker compose down

# 停止并删除容器+数据卷(慎用!会丢失数据库数据)
docker compose down -v
```

## 四、数据库运维

### 4.1 备份数据库
```bash
# 手动备份
docker exec erp-postgres pg_dump -U erp_user erp_db > backup_$(date +%Y%m%d).sql

# 恢复备份
docker exec -i erp-postgres psql -U erp_user erp_db < backup_20260630.sql
```

### 4.2 设置定时备份
```bash
# 创建备份脚本
cat > /opt/erp-backup.sh << 'EOF'
#!/bin/bash
BACKUP_DIR=/opt/backups
mkdir -p $BACKUP_DIR
docker exec erp-postgres pg_dump -U erp_user erp_db > $BACKUP_DIR/erp_$(date +%Y%m%d_%H%M%S).sql
# 保留最近7天备份
find $BACKUP_DIR -name "erp_*.sql" -mtime +7 -delete
EOF

chmod +x /opt/erp-backup.sh

# 添加定时任务(每天凌晨2点备份)
crontab -e
# 添加以下行:
0 2 * * * /opt/erp-backup.sh
```

### 4.3 进入数据库
```bash
# 进入PostgreSQL命令行
docker exec -it erp-postgres psql -U erp_user -d erp_db

# 查看所有表
\dt

# 退出
\q
```

## 五、防火墙配置

```bash
# 开放80端口(前端HTTP)
sudo ufw allow 80/tcp

# 开放443端口(如后续配置HTTPS)
sudo ufw allow 443/tcp

# 开放22端口(SSH)
sudo ufw allow 22/tcp

# 注意: 5432(PostgreSQL)和6379(Redis)和8080(后端)不建议对外开放
# 如需调试,临时开放后及时关闭:
# sudo ufw allow 8080/tcp  # 临时开放
# sudo ufw delete allow 8080/tcp  # 关闭

# 查看防火墙状态
sudo ufw status
```

## 六、配置HTTPS（可选）

```bash
# 1. 安装certbot
sudo apt install certbot

# 2. 先停止前端(释放80端口)
docker compose stop frontend

# 3. 申请SSL证书(替换your-domain.com为实际域名)
sudo certbot certonly --standalone -d your-domain.com

# 4. 修改frontend/nginx.conf添加HTTPS(参考配置):
#    将nginx.conf替换为带SSL的版本,监听443端口

# 5. 重新启动
docker compose up -d frontend
```

## 七、常见问题排查

### Q1: 后端启动失败，数据库连接超时
```bash
# 检查PostgreSQL是否就绪
docker compose ps postgres
# 确保状态为 Up (healthy)

# 手动测试数据库连接
docker exec erp-backend ping -c 2 postgres
docker exec erp-backend nc -zv postgres 5432

# 查看后端启动日志
docker compose logs --tail 50 backend
```

### Q2: 前端页面空白，API返回502
```bash
# 检查后端是否正常运行
docker compose ps backend
curl http://localhost:8080

# 检查Nginx代理配置
docker exec erp-frontend cat /etc/nginx/conf.d/default.conf

# 检查后端到数据库的连接
docker compose logs backend | grep -i error
```

### Q3: 构建失败 Maven 下载依赖超时
```bash
# 在backend/pom.xml中添加阿里云Maven镜像
# 或在Dockerfile中配置Maven settings.xml
# 重新构建:
docker compose build --no-cache backend
```

### Q4: 前端构建失败 npm install 超时
```bash
# Dockerfile已配置npmmirror镜像源
# 如仍超时,尝试:
docker compose build --no-cache frontend
```

### Q5: 磁盘空间不足
```bash
# 清理Docker无用镜像和容器
docker system prune -a

# 查看Docker磁盘占用
docker system df

# 查看日志文件大小
docker exec erp-backend du -sh /app/logs/
```

## 八、服务架构

```
用户浏览器 → :80 (Nginx) → /api/ → :808