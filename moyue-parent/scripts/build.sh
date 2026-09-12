#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 墨阅小说网 · 一键构建脚本
# 作用：在 moyue-parent 根目录执行 mvn clean package -DskipTests
# 用法（Git Bash / macOS / Linux）：
#   chmod +x scripts/*.sh
#   ./scripts/build.sh
# ---------------------------------------------------------------------------

set -e

# 定位到脚本所在目录，再回退一级即为 moyue-parent 工程根目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_DIR"
echo "[build] 工程目录：$PROJECT_DIR"

mvn clean package -DskipTests

echo "[build] 构建完成，各模块可执行 jar 位于 <module>/target/ 目录"
