#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 墨阅小说网 · 一键启动全部服务
# 顺序：基础设施提示 -> moyue-account（播种演示用户 id=1）-> moyue-gateway -> 其余业务服务
# 用法（Git Bash / macOS / Linux）：
#   chmod +x scripts/*.sh
#   ./scripts/build.sh          # 先构建
#   ./scripts/start-all.sh      # 再启动
# ---------------------------------------------------------------------------

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_DIR="$(cd "$PROJECT_DIR/.." && pwd)"
LOG_DIR="$PROJECT_DIR/logs"
VERSION="1.0.0-SNAPSHOT"

mkdir -p "$LOG_DIR"

echo "============================================================"
echo "[start] 基础设施提示"
echo "[start] 项目硬性约束：禁用 Docker（见 docs/不可忽视条件.md），基础设施须原生部署"
echo "[start] 请确认已原生启动并就绪：mysql 3306 / redis 6379 / nacos 8848"
echo "============================================================"
echo "[start] 5 秒后继续启动服务（如需先起基础设施，请按 Ctrl+C 中止）"
sleep 5

# 启动单个服务：jar 不存在则直接报错退出
start_service() {
    local module="$1"
    local jar="$PROJECT_DIR/$module/target/$module-$VERSION.jar"
    if [ ! -f "$jar" ]; then
        echo "[start][ERROR] 未找到 $jar"
        echo "[start][ERROR] 请先执行 ./scripts/build.sh 完成构建"
        exit 1
    fi
    nohup java -jar "$jar" > "$LOG_DIR/$module.log" 2>&1 &
    echo "[start] $module 已提交启动（PID $!），日志：$LOG_DIR/$module.log"
}

# 1) auth 最先启动：登录/注册/刷新（含演示账号播种，BCrypt 写入 user id=1）
start_service moyue-auth
echo "[start] 等待 auth 完成 Flyway 迁移与用户播种 ..."
sleep 15

# 2) 网关（依赖 Nacos 解析 lb:// 路由，放在业务服务之前）
start_service moyue-gateway
sleep 5

# 3) 其余业务服务（顺序不限，均向 Nacos 注册）
for module in moyue-account moyue-content moyue-social moyue-commerce moyue-search moyue-system moyue-message moyue-risk moyue-ai; do
    start_service "$module"
    sleep 3
done

echo "============================================================"
echo "[start] 全部服务已提交启动（后台 nohup 运行）"
echo "[start] 日志目录：$LOG_DIR"
echo "[start] 查看单个服务日志：tail -f $LOG_DIR/moyue-content.log"
echo "[start] 提示 1：moyue-system 依赖 XXL-Job 调度中心（默认 http://localhost:8088/xxl-job-admin）"
echo "[start]        未启动调度中心时日志会有连接报错，但不影响其余服务"
echo "[start] 提示 2：Nacos 控制台 http://localhost:8848/nacos，确认全部服务已注册后再跑冒烟"
echo "[start] 下一步：./scripts/smoke-test.sh"
echo "============================================================"
