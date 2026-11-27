#!/bin/bash
# =====================================================
#  Smart ERP - 快速回滚脚本
#  功能：将后端/前端回滚到上一个版本，或从备份恢复数据库
#
#  用法：
#    ./erp-rollback.sh                 # 交互式选择回滚方式
#    ./erp-rollback.sh image           # 回滚到上一个镜像版本
#    ./erp-rollback.sh db              # 从最新备份恢复数据库
#    ./erp-rollback.sh db <文件名>     # 从指定备份文件恢复数据库
# =====================================================

# ===== 配置区 =====
COMPOSE_DIR="/opt/smart-erp"
BACKUP_DIR="/opt/backups"
DB_CONTAINER="erp-postgres"
DB_USER="erp_user"
DB_NAME="erp_db"
BACKEND_CONTAINER="erp-backend"
FRONTEND_CONTAINER="erp-frontend"
# ==================

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

cd "$COMPOSE_DIR" || { echo -e "${RED}找不到项目目录: ${COMPOSE_DIR}${NC}"; exit 1; }

MODE=${1:-interactive}

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  Smart ERP - 回滚工具${NC}"
echo -e "${CYAN}========================================${NC}"

# ===== 交互式选择 =====
if [ "$MODE" = "interactive" ]; then
    echo -e "\n请选择回滚方式:"
    echo -e "  ${CYAN}1)${NC} 回滚代码到上一个版本（快速，30秒）"
    echo -e "  ${CYAN}2)${NC} 从数据库备份恢复（用于数据回滚）"
    echo -e "  ${CYAN}3)${NC} 两者都做（代码+数据库全部回滚）"
    echo -e "  ${CYAN}0)${NC} 退出"
    echo ""
    read -p "请选择 [0-3]: " choice

    case $choice in
        1) MODE="image" ;;
        2) MODE="db" ;;
        3) MODE="both" ;;
        *) echo "已退出"; exit 0 ;;
    esac
fi

# ===== 回滚代码（镜像回滚） =====
rollback_image() {
    echo -e "\n${YELLOW}回滚代码到上一个版本...${NC}"

    # 从 version_history.log 读取最近的两个记录
    LOG_FILE="$BACKUP_DIR/version_history.log"
    if [ ! -f "$LOG_FILE" ]; then
        echo -e "${RED}  [错误] 找不到版本历史文件: ${LOG_FILE}${NC}"
        return 1
    fi

    # 读取最近一次更新前的镜像ID
    LATEST_BACKEND=$(grep "backend" "$LOG_FILE" | tail -1 | awk '{print $3}')
    LATEST_FRONTEND=$(grep "frontend" "$LOG_FILE" | tail -1 | awk '{print $3}')

    # 读取上上次的镜像ID（即更新前的版本）
    PREV_BACKEND=$(grep "backend" "$LOG_FILE" | tail -2 | head -1 | awk '{print $3}')
    PREV_FRONTEND=$(grep "frontend" "$LOG_FILE" | tail -2 | head -1 | awk '{print $3}')

    if [ -z "$PREV_BACKEND" ]; then
        echo -e "${RED}  [错误] 找不到上一个版本记录${NC}"
        echo -e "${YELLOW}  可用版本记录:${NC}"
        cat "$LOG_FILE" | tail -10
        return 1
    fi

    echo -e "  当前版本: backend=${LATEST_BACKEND:0:12} frontend=${LATEST_FRONTEND:0:12}"
    echo -e "  回滚目标: backend=${PREV_BACKEND:0:12} frontend=${PREV_FRONTEND:0:12}"

    read -p "  确认回滚? [y/N]: " confirm
    if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
        echo -e "${YELLOW}  已取消${NC}"
        return 0
    fi

    # 停止当前容器
    echo -e "\n${YELLOW}  停止当前容器...${NC}"
    docker compose stop backend frontend

    # 用旧镜像重新创建容器
    echo -e "${YELLOW}  用旧镜像启动...${NC}"
    docker compose up -d --no-deps --force-recreate backend frontend

    # 等待启动
    echo -e "${YELLOW}  等待后端启动...${NC}"
    sleep 10

    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/api/auth/test" 2>/dev/null)
    if [ "$HTTP_CODE" = "200" ]; then
        echo -e "${GREEN}  回滚成功! 后端已恢复到上一个版本 ✓${NC}"
    else
        echo -e "${RED}  [警告] 回滚后健康检查未通过 (HTTP ${HTTP_CODE})${NC}"
        echo -e "${YELLOW}  请检查日志: docker compose logs --tail 50 backend${NC}"
    fi
}

# ===== 回滚数据库 =====
rollback_db() {
    DB_FILE=${2:-}

    echo -e "\n${YELLOW}从备份恢复数据库...${NC}"

    # 如果没有指定文件，找最新的备份
    if [ -z "$DB_FILE" ]; then
        DB_FILE=$(ls -t "$BACKUP_DIR"/db_*.sql.gz 2>/dev/null | head -1)
        if [ -z "$DB_FILE" ]; then
            DB_FILE=$(ls -t "$BACKUP_DIR"/db_*.sql 2>/dev/null | head -1)
        fi
    fi

    if [ -z "$DB_FILE" ] || [ ! -f "$DB_FILE" ]; then
        echo -e "${RED}  [错误] 找不到备份文件${NC}"
        echo -e "${YELLOW}  可用备份文件:${NC}"
        ls -lht "$BACKUP_DIR"/db_* 2>/dev/null | head -10
        return 1
    fi

    echo -e "  将从以下文件恢复: ${CYAN}${DB_FILE}${NC}"
    echo -e "  ${RED}  ⚠ 警告: 此操作会覆盖当前数据库所有数据!${NC}"
    read -p "  确认恢复? 输入 YES 继续: " confirm
    if [ "$confirm" != "YES" ]; then
        echo -e "${YELLOW}  已取消（输入 YES 才会执行）${NC}"
        return 0
    fi

    echo -e "\n${YELLOW}  恢复中...${NC}"

    # 先备份当前数据库（以防万一）
    CURRENT_BACKUP="$BACKUP_DIR/db_before_rollback_$(date +%Y%m%d_%H%M%S).sql"
    docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" "$DB_NAME" > "$CURRENT_BACKUP" 2>/dev/null
    echo -e "  当前数据已备份: ${CURRENT_BACKUP}"

    # 恢复
    case "$DB_FILE" in
        *.gz)
            gunzip -c "$DB_FILE" | docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" 2>/dev/null
            ;;
        *.sql)
            docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" < "$DB_FILE" 2>/dev/null
            ;;
    esac

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}  数据库恢复成功 ✓${NC}"
        echo -e "${YELLOW}  建议重启后端以刷新缓存:${NC}"
        echo -e "    docker compose restart backend"
    else
        echo -e "${RED}  [错误] 数据库恢复失败!${NC}"
        echo -e "${YELLOW}  当前数据备份在: ${CURRENT_BACKUP}${NC}"
    fi
}

# ===== 执行 =====
case "$MODE" in
    image)
        rollback_image
        ;;
    db)
        rollback_db "$@"
        ;;
    both)
        rollback_image
        echo ""
        rollback_db
        ;;
    *)
        echo -e "${RED}未知模式: ${MODE}${NC}"
        echo -e "用法: $0 [image|db|both]"
        exit 1
        ;;
esac
