#!/bin/bash
# =====================================================
#  Smart ERP - 数据库自动备份脚本
#  功能：备份 PostgreSQL 数据库 + 上传文件，自动清理旧备份
#  用法：手动执行 或 添加到 crontab 定时执行
# =====================================================

# ===== 配置区（根据实际环境修改） =====
COMPOSE_DIR="/opt/smart-erp"                    # docker-compose.yml 所在目录
BACKUP_DIR="/opt/backups"                        # 备份文件存放目录
DB_CONTAINER="erp-postgres"                      # PostgreSQL 容器名
DB_USER="erp_user"                               # 数据库用户名
DB_NAME="erp_db"                                 # 数据库名
RETAIN_DAYS=7                                    # 备份保留天数
# ======================================

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
TODAY=$(date +%Y%m%d)

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Smart ERP - 自动备份${NC}"
echo -e "${GREEN}  时间: ${TIMESTAMP}${NC}"
echo -e "${GREEN}========================================${NC}"

# 创建备份目录
mkdir -p "$BACKUP_DIR"

# ===== 1. 备份数据库 =====
echo -e "\n${YELLOW}[1/3] 备份数据库...${NC}"
DB_FILE="$BACKUP_DIR/db_${TODAY}.sql"

# 检查容器是否运行
if ! docker ps --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$"; then
    echo -e "${RED}  [错误] PostgreSQL 容器 ${DB_CONTAINER} 未运行!${NC}"
    echo -e "${YELLOW}  请检查: cd ${COMPOSE_DIR} && docker compose ps${NC}"
    exit 1
fi

# 执行备份
docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" "$DB_NAME" > "$DB_FILE" 2>/dev/null

if [ $? -eq 0 ] && [ -s "$DB_FILE" ]; then
    FILE_SIZE=$(du -h "$DB_FILE" | cut -f1)
    echo -e "${GREEN}  数据库备份成功: ${DB_FILE} (${FILE_SIZE})${NC}"
else
    echo -e "${RED}  [错误] 数据库备份失败!${NC}"
    rm -f "$DB_FILE"
    exit 1
fi

# 压缩备份文件
gzip -f "$DB_FILE"
echo -e "${GREEN}  已压缩: ${DB_FILE}.gz${NC}"

# ===== 2. 备份上传的文件（如果 volume 存在） =====
echo -e "\n${YELLOW}[2/3] 备份上传文件...${NC}"
FILES_TAR="$BACKUP_DIR/files_${TODAY}.tar.gz"

# 检查是否有文件存储 volume
if docker volume inspect smart-erp_erp-files >/dev/null 2>&1 || docker volume inspect erp-files >/dev/null 2>&1; then
    # 用 docker run 临时容器打包 volume
    docker run --rm -v smart-erp_erp-files:/data -v "$BACKUP_DIR":/backup alpine \
        tar czf "/backup/files_${TODAY}.tar.gz" -C /data . 2>/dev/null || \
    docker run --rm -v erp-files:/data -v "$BACKUP_DIR":/backup alpine \
        tar czf "/backup/files_${TODAY}.tar.gz" -C /data . 2>/dev/null

    if [ -f "$FILES_TAR" ]; then
        FILE_SIZE=$(du -h "$FILES_TAR" | cut -f1)
        echo -e "${GREEN}  文件备份成功: ${FILES_TAR} (${FILE_SIZE})${NC}"
    else
        echo -e "${YELLOW}  [警告] 文件备份失败，跳过（不影响数据库备份）${NC}"
    fi
else
    echo -e "${YELLOW}  未找到文件存储 volume，跳过文件备份${NC}"
fi

# ===== 3. 清理旧备份 =====
echo -e "\n${YELLOW}[3/3] 清理 ${RETAIN_DAYS} 天前的旧备份...${NC}"
DELETED_COUNT=$(find "$BACKUP_DIR" -name "*.gz" -mtime +${RETAIN_DAYS} -delete -print | wc -l)
echo -e "${GREEN}  已清理 ${DELETED_COUNT} 个旧备份文件${NC}"

# ===== 汇总 =====
echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}  备份完成!${NC}"
echo -e "${GREEN}  备份目录: ${BACKUP_DIR}${NC}"
echo -e "${GREEN}  当前备份文件:${NC}"
ls -lh "$BACKUP_DIR"/*"${TODAY}"* 2>/dev/null | awk '{print "    " $NF " (" $5 ")"}'
echo -e "${GREEN}========================================${NC}"
