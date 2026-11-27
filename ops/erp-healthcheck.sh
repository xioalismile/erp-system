#!/bin/bash
# =====================================================
#  Smart ERP - 健康检查脚本
#  功能：检查所有服务运行状态、磁盘空间、内存使用
#  用法：./erp-healthcheck.sh
# =====================================================

# ===== 配置区 =====
COMPOSE_DIR="/opt/smart-erp"
DB_CONTAINER="erp-postgres"
REDIS_CONTAINER="erp-redis"
BACKEND_CONTAINER="erp-backend"
FRONTEND_CONTAINER="erp-frontend"
BACKEND_HEALTH_URL="http://localhost:8080/api/auth/test"
FRONTEND_URL="http://localhost"
DISK_THRESHOLD=85    # 磁盘使用率告警阈值(%)
MEM_THRESHOLD=80     # 内存使用率告警阈值(%)
# ==================

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

cd "$COMPOSE_DIR" 2>/dev/null

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  Smart ERP - 健康检查${NC}"
echo -e "${CYAN}  时间: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
echo -e "${CYAN}========================================${NC}"

HAS_ERROR=false

# ===== 1. 容器状态检查 =====
echo -e "\n${YELLOW}[1/5] 容器状态${NC}"
printf "  %-20s %-15s %-10s\n" "服务" "状态" "运行时间"
echo "  -----------------------------------------------"

for svc in "$DB_CONTAINER" "$REDIS_CONTAINER" "$BACKEND_CONTAINER" "$FRONTEND_CONTAINER"; do
    STATUS=$(docker inspect --format='{{.State.Status}}' "$svc" 2>/dev/null)
    if [ "$STATUS" = "running" ]; then
        STARTED=$(docker inspect --format='{{.State.StartedAt}}' "$svc" 2>/dev/null)
        UPTIME=$(docker inspect --format='{{.State.StartedAt}}' "$svc" 2>/dev/null | xargs -I{} date -d "{}" "+%Y-%m-%d %H:%M" 2>/dev/null || echo "未知")
        printf "  %-20s ${GREEN}%-15s${NC} %-10s\n" "$svc" "运行中 ✓" "$UPTIME"
    else
        printf "  %-20s ${RED}%-15s${NC} %-10s\n" "$svc" "未运行 ✗" "-"
        HAS_ERROR=true
    fi
done

# ===== 2. 数据库连接检查 =====
echo -e "\n${YELLOW}[2/5] 数据库连接${NC}"
DB_CHECK=$(docker exec "$DB_CONTAINER" psql -U erp_user -d erp_db -t -c "SELECT 1;" 2>/dev/null)
if echo "$DB_CHECK" | grep -q "1"; then
    TABLE_COUNT=$(docker exec "$DB_CONTAINER" psql -U erp_user -d erp_db -t -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public';" 2>/dev/null | xargs)
    DB_SIZE=$(docker exec "$DB_CONTAINER" psql -U erp_user -d erp_db -t -c "SELECT pg_size_pretty(pg_database_size('erp_db'));" 2>/dev/null | xargs)
    echo -e "  ${GREEN}连接正常 ✓${NC}  表数量: ${TABLE_COUNT}  数据库大小: ${DB_SIZE}"
else
    echo -e "  ${RED}连接失败 ✗${NC}"
    HAS_ERROR=true
fi

# ===== 3. Redis 连接检查 =====
echo -e "\n${YELLOW}[3/5] Redis 连接${NC}"
REDIS_CHECK=$(docker exec "$REDIS_CONTAINER" redis-cli ping 2>/dev/null)
if echo "$REDIS_CHECK" | grep -q "PONG"; then
    REDIS_MEM=$(docker exec "$REDIS_CONTAINER" redis-cli info memory 2>/dev/null | grep "used_memory_human" | cut -d: -f2 | tr -d '\r')
    echo -e "  ${GREEN}连接正常 ✓${NC}  内存使用: ${REDIS_MEM}"
else
    echo -e "  ${RED}连接失败 ✗${NC}"
    HAS_ERROR=true
fi

# ===== 4. HTTP 接口检查 =====
echo -e "\n${YELLOW}[4/5] HTTP 接口${NC}"

# 后端
BACKEND_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BACKEND_HEALTH_URL" 2>/dev/null)
if [ "$BACKEND_CODE" = "200" ]; then
    echo -e "  后端 API: ${GREEN}正常 ✓${NC} (HTTP ${BACKEND_CODE})"
else
    echo -e "  后端 API: ${RED}异常 ✗${NC} (HTTP ${BACKEND_CODE})"
    HAS_ERROR=true
fi

# 前端
FRONTEND_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$FRONTEND_URL" 2>/dev/null)
if [ "$FRONTEND_CODE" = "200" ] || [ "$FRONTEND_CODE" = "301" ] || [ "$FRONTEND_CODE" = "302" ]; then
    echo -e "  前端页面: ${GREEN}正常 ✓${NC} (HTTP ${FRONTEND_CODE})"
else
    echo -e "  前端页面: ${RED}异常 ✗${NC} (HTTP ${FRONTEND_CODE})"
    HAS_ERROR=true
fi

# ===== 5. 系统资源检查 =====
echo -e "\n${YELLOW}[5/5] 系统资源${NC}"

# 磁盘
DISK_USAGE=$(df / | tail -1 | awk '{print $5}' | tr -d '%')
if [ "$DISK_USAGE" -ge "$DISK_THRESHOLD" ]; then
    echo -e "  磁盘使用率: ${RED}${DISK_USAGE}% ✗${NC} (超过 ${DISK_THRESHOLD}% 告警)"
    HAS_ERROR=true
else
    echo -e "  磁盘使用率: ${GREEN}${DISK_USAGE}% ✓${NC}"
fi

# 内存
MEM_USAGE=$(free | awk '/Mem/{printf "%.0f", $3/$2*100}')
if [ "$MEM_USAGE" -ge "$MEM_THRESHOLD" ]; then
    echo -e "  内存使用率: ${RED}${MEM_USAGE}% ✗${NC} (超过 ${MEM_THRESHOLD}% 告警)"
    HAS_ERROR=true
else
    echo -e "  内存使用率: ${GREEN}${MEM_USAGE}% ✓${NC}"
fi

# Docker 磁盘占用
DOCKER_SIZE=$(docker system df --format '{{.Size}}' | head -1)
echo -e "  Docker 占用: ${CYAN}${DOCKER_SIZE}${NC}"

# ===== 汇总 =====
echo -e "\n${CYAN}========================================${NC}"
if [ "$HAS_ERROR" = false ]; then
    echo -e "${GREEN}  所有服务运行正常 ✓${NC}"
else
    echo -e "${RED}  存在异常，请检查上述标记 ✗ 的项目${NC}"
fi
echo -e "${CYAN}========================================${NC}"
