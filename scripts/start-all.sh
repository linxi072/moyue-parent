#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 墨阅小说网 · 单体一键启动（moyue-app）
# 单体化后整个后端为一个 Spring Boot 应用（moyue-app），直接监听 :8080，
# 所有 /api/v1/* 由本进程处理，不再有 Nacos 注册发现 / 网关路由。
# 用法（Git Bash / macOS / Linux）：
#   chmod +x scripts/*.sh
#   ./scripts/build.sh          # 先构建
#   ./scripts/start-all.sh      # 再启动
# ---------------------------------------------------------------------------

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$PROJECT_DIR/logs"
VERSION="1.0.0-SNAPSHOT"

mkdir -p "$LOG_DIR"

echo "============================================================"
echo "[start] 基础设施提示"
echo "[start] 项目硬性约束：禁用 Docker（见 docs/不可忽视条件.md），基础设施须原生部署"
echo "[start] 请确认已原生启动并就绪：mysql 3306 / redis 6379（es 9200 仅检索域可选）"
echo "============================================================"
echo "[start] 5 秒后启动单体应用（如需先起基础设施，请按 Ctrl+C 中止）"
sleep 5

JAR="$PROJECT_DIR/moyue-app/target/moyue-app-$VERSION.jar"
if [ ! -f "$JAR" ]; then
    echo "[start][ERROR] 未找到 $JAR"
    echo "[start][ERROR] 请先执行 ./scripts/build.sh 完成构建"
    exit 1
fi

nohup java -jar "$JAR" > "$LOG_DIR/moyue-app.log" 2>&1 &
echo "[start] moyue-app 已提交启动（PID $!），日志：$LOG_DIR/moyue-app.log"

echo "============================================================"
echo "[start] 单体应用已启动（后台 nohup 运行），监听 http://localhost:8080"
echo "[start] 日志目录：$LOG_DIR"
echo "[start] 查看日志：tail -f $LOG_DIR/moyue-app.log"
echo "[start] 提示 1：XXL-Job 执行器随进程启动（RPC 9099）；调度中心可选（默认 http://localhost:8088/xxl-job-admin）"
echo "[start] 提示 2：ES 未启动时限检索 / RAG 自动降级，不阻断主链路"
echo "[start] 下一步：./scripts/smoke-test.sh"
echo "============================================================"
