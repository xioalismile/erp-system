#!/bin/bash
# =====================================================
#  Smart ERP - 安全更新脚本
#  功能：备份数据库 → 标记旧版本 → 构建新版本 → 健康检查 → 失败自动回滚
#
#  用法：
#    ./erp-update.sh              # 更新全部（前端+后端）
#    ./erp-update.sh frontend     # 只更新前端
#    ./erp-update.sh backend      # 只更新后端
#    ./erp-update.sh --no-backup  # 跳过备份（不推荐）
#
#  前提：将新代码上传到服务器后执行此脚本
# =====================================================

# ===== 配置区 =====
COMPOSE_DIR="/opt/smart-erp"
BACKUP_DIR="/opt/backups"
DB_CONTAINER="erp-postgres"
DB_USER="erp_user"
DB_NAME="erp_db"
BACKEND_CONTAINER="erp-backend"
FRONTEND_CONTAINER="erp-frontend"
HEALTH_CHECK_URL="http://localhost:8080/api/auth/test"
HEALTH_TIMEOUT=60    # 健康检查超时秒数
# ==================

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
TARGET=${1:-all}
SKIP_BACKUP=false

if [ "$1" = "--no-backup" ]; then
    SKIP_BACKUP=true
    TARGET="all"
fi
if [ "$2" = "--no-backup" ]; then
    SKIP_BACKUP=true
fi

cd "$COMPOSE_DIR" || { echo -e "${RED}找不到项目目录: ${COMPOSE_DIR}${NC}"; exit 1; }

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  Smart ERP - 安全更新${NC}"
echo -e "${CYAN}  时间: ${TIMESTAMP}${NC}"
echo -e "${CYAN}  目标: ${TARGET}${NC}"
echo -e "${CYAN}========================================${NC}"

# ===== Step 1: 备份数据库 =====
if [ "$SKIP_BACKUP" = false ]; then
    echo -e "\n${YELLOW}[1/5] 备份数据库...${NC}"
    mkdir -p "$BACKUP_DIR"
    DB_FILE="$BACKUP_DIR/db_${TIMESTAMP}.sql"

    docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" "$DB_NAME" > "$DB_FILE" 2>/dev/null

    if [ $? -eq 0 ] && [ -s "$DB_FILE" ]; then
        gzip -f "$DB_FILE"
        echo -e "${GREEN}  备份成功: ${DB_FILE}.gz${NC}"
    else
        echo -e "${RED}  [错误] 数据库备份失败! 更新中止。${NC}"
        echo -e "${YELLOW}  请检查 PostgreSQL 容器是否正常: docker compose ps${NC}"
        exit 1
    fi
else
    echo -e "\n${YELLOW}[1/5] 跳过备份（--no-backup）${NC}"
fi

# ===== Step 2: 标记当前版本（用于回滚） =====
echo -e "\n${YELLOW}[2/5] 标记当前版本...${NC}"

# 记录当前镜像 ID
BACKEND_IMAGE_BEFORE=$(docker inspect --format='{{.Image}}' "$BACKEND_CONTAINER" 2>/dev/null)
FRONTEND_IMAGE_BEFORE=$(docker inspect --format='{{.Image}}' "$FRONTEND_CONTAINER" 2>/dev/null)

echo "$TIMESTAMP backend $BACKEND_IMAGE_BEFORE" >> "$BACKUP_DIR/version_history.log"
echo "$TIMESTAMP frontend $FRONTEND_IMAGE_BEFORE" >> "$BACKUP_DIR/version_history.log"

echo -e "${GREEN}  已记录当前版本到 version_history.log${NC}"

# ===== Step 3: 构建新镜像 =====
echo -e "\n${YELLOW}[3/5] 构建新镜像...${NC}"

case "$TARGET" in
    frontend)
        docker compose build --no-cache frontend
        ;;
    backend)
        docker compose build --no-cache backend
        ;;
    all|"")
        docker compose build --no-cache
        ;;
    *)
        echo -e "${RED}  未知目标: ${TARGET}（可选: frontend / backend / all）${NC}"
        exit 1
        ;;
esac

if [ $? -ne 0 ]; then
    echo -e "${RED}  [错误] 构建失败! 更新中止。${NC}"
    echo -e "${YELLOW}  代码未变更，系统仍正常运行旧版本。${NC}"
    exit 1
fi
echo -e "${GREEN}  构建成功${NC}"

# ===== Step 4: 重启服务 =====
echo -e "\n${YELLOW}[4/5] 重启服务...${NC}"

case "$TARGET" in
    frontend)
        docker compose up -d frontend
        ;;
    backend)
        docker compose up -d backend
        ;;
    all|"")
        docker compose up -d backend frontend
        ;;
esac

echo -e "${GREEN}  服务已启动，等待健康检查...${NC}"

# ===== Step 5: 健康检查（失败自动回滚） =====
echo -e "\n${YELLOW}[5/5] 健康检查（最多等待 ${HEALTH_TIMEOUT} 秒）...${NC}"

# 如果只更新前端，跳过后端健康检查
if [ "$TARGET" = "frontend" ]; then
    sleep 3
    if curl -s -o /dev/null -w "%{http_code}" http://localhost | grep -q "200\|301\|302"; then
        echo -e "${GREEN}  前端健康检查通过 ✓${NC}"
        echo -e "\n${GREEN}========================================${NC}"
        echo -e "${GREEN}  更新成功!${NC}"
        echo -e "${GREEN}========================================${NC}"
        exit 0
    else
        echo -e "${RED}  前端健康检查失败!${NC}"
        echo -e "${YELLOW}  正在回滚前端...${NC}"
        docker compose stop frontend
        # 回滚：用旧镜像启动
        if [ -n "$FRONTEND_IMAGE_BEFORE" ]; then
            docker run -d --name "$FRONTEND_CONTAINER" \
                -p 80:80 \
                --network smart-erp_default \
                "$FRONTEND_IMAGE_BEFORE" 2>/dev/null || \
            docker compose up -d frontend
        fi
        echo -e "${RED}  前端已回滚到旧版本${NC}"
        exit 1
    fi
fi

# 后端健康检查
WAIT=0
while [ $WAIT -lt $HEALTH_TIMEOUT ]; do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$HEALTH_CHECK_URL" 2>/dev/null)
    if [ "$HTTP_CODE" = "200" ]; then
        echo -e "${GREEN}  后端健康检查通过 ✓ (HTTP ${HTTP_CODE})${NC}"
        echo -e "\n${GREEN}========================================${NC}"
        echo -e "${GREEN}  更新成功!${NC}"
        echo -e "${GREEN}  访问地址: http://$(hostname -I | awk '{print $1}')${NC}"
        echo -e "${GREEN}  如需回滚: ./erp-rollback.sh${NC}"
        echo -e "${GREEN}========================================${NC}"
        exit 0
    fi
    sleep 2
    WAIT=$((WAIT + 2))
    printf "\r  等待中... %ds / %ds" "$WAIT" "$HEALTH_TIMEOUT"
done

echo ""
echo -e "${RED}  [错误] 健康检查超时! 后端未在 ${HEALTH_TIMEOUT} 秒内响应${NC}"
echo -e "${YELLOW}  查看日志: docker compose logs --tail 50 backend${NC}"
echo -e "${YELLOW}  手动回滚: ./erp-rollback.sh${NC}"
exit 1
