# Smart ERP - 运维操作手册

## 一、首次使用（已完成部署的系统）

### 1.1 上传运维脚本到服务器

将 `ops/` 目录上传到服务器 `/opt/smart-erp/` 下：

```bash
# 在服务器上创建目录
mkdir -p /opt/smart-erp/ops

# 在本地执行上传（替换服务器IP和路径）
scp -r ops/ root@你的服务器IP:/opt/smart-erp/
```

### 1.2 赋予脚本执行权限

```bash
cd /opt/smart-erp/ops
chmod +x *.sh
```

### 1.3 设置自动备份（一次性操作）

```bash
# 编辑定时任务
crontab -e

# 添加以下两行（每天凌晨2点备份，每天8点健康检查）：
0 2 * * * /opt/smart-erp/ops/erp-backup.sh >> /opt/backups/backup.log 2>&1
0 8 * * * /opt/smart-erp/ops/erp-healthcheck.sh >> /opt/backups/health.log 2>&1

# 保存退出
```

完成！以后系统每天自动备份，不需要手动干预。

---

## 二、日常更新操作（核心流程）

### 场景：你改了代码，要更新到客户服务器

#### 第 1 步：在本地构建新代码

```bash
# 在本地项目目录
cd frontend && npm run build    # 构建前端
cd backend && mvn clean package -DskipTests    # 构建后端
```

#### 第 2 步：上传新代码到服务器

```bash
# 方式一：SCP 上传（简单直接）
scp -r backend/target/smart-erp-1.0.0.jar root@服务器IP:/opt/smart-erp/backend/target/
scp -r frontend/dist/* root@服务器IP:/opt/smart-erp/frontend/dist/

# 方式二：用项目自带的 deploy-to-cloud.ps1（Windows 本地一键部署）
```

#### 第 3 步：在服务器上执行安全更新

```bash
cd /opt/smart-erp

# 更新全部（前端+后端）—— 推荐
./ops/erp-update.sh

# 或只更新前端（零停机）
./ops/erp-update.sh frontend

# 或只更新后端（约30秒停机）
./ops/erp-update.sh backend
```

脚本会自动完成：
1. ✅ 备份当前数据库
2. ✅ 记录当前版本号（用于回滚）
3. ✅ 构建新镜像
4. ✅ 重启服务
5. ✅ 健康检查（失败会提示你回滚）

#### 第 4 步：验证

```bash
# 看健康状态
./ops/erp-healthcheck.sh

# 或浏览器访问
# http://服务器IP  (Ctrl+F5 强制刷新)
```

**整个过程停机时间约 30~60 秒（仅后端重启期间）。**

---

## 三、出问题怎么回滚

### 3.1 代码回滚（30秒恢复）

```bash
cd /opt/smart-erp

# 交互式回滚
./ops/erp-rollback.sh

# 或直接回滚代码
./ops/erp-rollback.sh image
```

### 3.2 数据库回滚（从备份恢复）

```bash
cd /opt/smart-erp

# 从最新备份恢复
./ops/erp-rollback.sh db

# 从指定备份恢复
./ops/erp-rollback.sh db /opt/backups/db_20260724_020000.sql.gz
```

### 3.3 代码+数据库全部回滚

```bash
./ops/erp-rollback.sh both
```

---

## 四、脚本说明

### 4.1 脚本清单

```
ops/
├── erp-backup.sh        # 数据库自动备份（含文件备份、旧备份清理）
├── erp-update.sh        # 安全更新（备份→构建→重启→健康检查→自动回滚）
├── erp-rollback.sh      # 回滚工具（代码回滚 / 数据库恢复 / 两者都做）
├── erp-healthcheck.sh   # 健康检查（容器状态、数据库、Redis、HTTP、磁盘内存）
└── ops-guide.md         # 本文档
```

### 4.2 配置修改

所有脚本顶部的「配置区」都可以根据实际环境修改：

```bash
# 如果你的项目路径不是 /opt/smart-erp，修改所有脚本中的：
COMPOSE_DIR="/opt/smart-erp"

# 如果你的数据库容器名不同，修改：
DB_CONTAINER="erp-postgres"
DB_USER="erp_user"
DB_NAME="erp_db"
```

---

## 五、数据库迁移操作

当新版本涉及数据库结构变更（加表、加字段等）时：

### 5.1 执行迁移脚本

```bash
# 1. 先备份数据库（重要!）
./ops/erp-backup.sh

# 2. 上传 SQL 迁移脚本到服务器
scp migration_xxx.sql root@服务器IP:/tmp/

# 3. 执行迁移
docker exec -i erp-postgres psql -U erp_user -d erp_db < /tmp/migration_xxx.sql

# 4. 验证迁移结果
docker exec -it erp-postgres psql -U erp_user -d erp_db -c "\dt"  # 查看表列表

# 5. 然后执行正常更新流程
./ops/erp-update.sh
```

### 5.2 迁移注意事项

- **加字段**：用 `ALTER TABLE xxx ADD COLUMN new_col VARCHAR(255)`，不要设默认值（避免锁表）
- **加表**：`CREATE TABLE IF NOT EXISTS xxx`，安全可重复执行
- **大表操作**：选在业务低峰期（晚上或周末）执行
- **先测试**：迁移脚本先在测试环境跑一遍

---

## 六、常见问题

### Q1: 更新后用户说页面没变化

**原因：** 浏览器缓存了旧版本

**解决：** 让用户按 `Ctrl + Shift + R`（或 `Ctrl + F5`）强制刷新

---

### Q2: 更新后后端启动失败

```bash
# 查看启动日志
docker compose logs --tail 100 backend

# 常见原因：
# 1. 数据库连接失败 → 检查 postgres 容器是否正常
# 2. License 校验失败 → 检查 license.lic 文件
# 3. 端口被占用 → docker compose ps 看是否有残留容器

# 回滚到上一个版本
./ops/erp-rollback.sh image
```

---

### Q3: 磁盘空间不足

```bash
# 检查磁盘
df -h

# 清理 Docker 无用镜像
docker system prune -a

# 清理旧备份（保留最近3天）
find /opt/backups -name "*.gz" -mtime +3 -delete

# 查看日志文件大小
docker exec erp-backend du -sh /app/logs/
```

---

### Q4: 数据库备份文件太大

```bash
# 查看备份文件大小
ls -lh /opt/backups/

# 只保留最近3天的备份（修改 erp-backup.sh 中的 RETAIN_DAYS=3）
# 或手动清理
find /opt/backups -name "db_*.gz" -mtime +3 -delete
```

---

### Q5: 怎么查看当前运行的版本

```bash
# 查看版本历史
cat /opt/backups/version_history.log | tail -10

# 查看当前镜像信息
docker inspect --format='{{.Config.Image}} {{.Created}}' erp-backend
```

---

## 七、最佳实践

### 7.1 更新前检查清单

- [ ] 新代码在本地测试通过
- [ ] 数据库迁移脚本已在测试环境验证（如有）
- [ ] 选择业务低峰期更新（建议晚上或周末）
- [ ] 确认自动备份正常运行（`ls /opt/backups/`）
- [ ] 通知客户即将有短暂停机（如有后端更新）

### 7.2 更新后检查清单

- [ ] `./ops/erp-healthcheck.sh` 全部通过
- [ ] 浏览器访问核心功能（登录、查看订单、新增记录）
- [ ] 确认客户数据完整（查看客户列表、库存数据）

### 7.3 安全建议

- 定期检查备份文件是否能正常恢复（每月一次恢复演练）
- 保留至少 7 天的数据库备份
- 不要在服务器上存储明文密码（用 .env 文件 + 文件权限 600）
- 定期更新 Docker 和系统安全补丁
