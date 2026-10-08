#!/bin/bash
# 墨阅全量自测验证：双仓一致性 → 单元测试 → Cloud 全栈 E2E
#
# 用法：
#   bash verify-all.sh          # 完整流程（含拉起基础设施与微服务栈）
#   SKIP_BUILD=1 bash verify-all.sh   # 跳过 Maven 构建，仅跑已编译产物
#
# 退出码：0 = 全绿；非 0 = 存在失败项

set -u
cd "$(dirname "$0")/.."
ROOT=$(pwd)
FAILED=0
step() { echo; echo "════════ $1 ════════"; }
mark()  { if [ "$1" -eq 0 ]; then echo "  ✅ $2"; else echo "  ❌ $2"; FAILED=$((FAILED+1)); fi; }

step "0/5 基础设施"
if ! docker ps --format '{{.Names}}' | grep -qx moyue-mysql; then
  bash e2e/start-infra.sh > /tmp/verify-infra.log 2>&1
fi
docker ps --format '{{.Names}}\t{{.Status}}'
for n in moyue-mysql moyue-redis moyue-nacos; do
  docker ps --format '{{.Names}}' | grep -qx "$n"; mark $? "$n 运行中"
done

step "1/5 双仓逐字节一致性"
bash sync.sh > /tmp/verify-sync.log 2>&1
grep -q '差异文件：0' /tmp/verify-sync.log
mark $? "sync.sh 两仓共享源码一致（$(grep -oP '一致文件：\K\d+' /tmp/verify-sync.log) 文件 / 0 差异）"

step "2/5 单元测试（Maven）"
if [ "${SKIP_BUILD:-0}" != "1" ]; then
  ( cd moyue-cloud && mvn -Plocal-jdk20 -am -DskipTests install -q ) > /tmp/verify-build.log 2>&1
  mark $? "moyue-cloud 全量构建（10 模块）"
fi
( cd moyue-cloud && mvn -Plocal-jdk20 \
    -pl moyue-modules/moyue-content,moyue-modules/moyue-social,\
moyue-modules/moyue-risk,moyue-modules/moyue-ai test ) > /tmp/verify-test.log 2>&1
mark $? "单元测试（$(grep -oP 'Tests run: \K\d+(?=, Failures: 0, Errors: 0, Skipped: 0$)' /tmp/verify-test.log | paste -sd+ | bc) 例）"
grep -E 'Tests run:.*in com\.moyue' /tmp/verify-test.log | sed 's/.*Tests run/  · Tests run/' | head -10

step "3/5 前端类型检查与构建"
# 说明：Node 默认堆上限可能超过 cgroup 剩余内存（此时报 "Killed"，非代码错误）。
# 限堆 + 先构建后启服务，避免与 10 个 Java 进程叠加触发 OOM。
( cd moyue-web && NODE_OPTIONS=--max-old-space-size=2048 npm run build ) > /tmp/verify-web.log 2>&1
mark $? "vue-tsc --noEmit && vite build"

step "4/5 Cloud 全栈启动"
bash e2e/start-cloud.sh > /tmp/verify-start.log 2>&1
sleep 95
PORTS="8080:gateway 8086:system 8090:auth 8082:content 8083:social 8084:commerce 8085:search 8087:message 8089:risk 8097:ai"
for e in $PORTS; do
  p=${e%%:*}; n=${e##*:}
  if [ "$n" = "gateway" ]; then u="http://127.0.0.1:$p/api/v1/system/login"; else u="http://127.0.0.1:$p/"; fi
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$u")
  # 网关与服务根路径返回 404/405 均说明端口在监听（网关故意未暴露 actuator 根）
  if [ "$code" != "000" ]; then echo "  · $n($p) http=$code"; else echo "  ❌ $n($p) 未监听"; FAILED=$((FAILED+1)); fi
done

step "5/5 端到端冒烟（8 脚本）"
TOTAL_PASS=0
for s in cloud commerce search message risk ai content social; do
  out=$(timeout 300 python3 "e2e/${s}_e2e.py" 2>&1)
  rc=$?
  n=$(echo "$out" | grep -oP '通过 \K\d+(?= / 失败)' | tail -1)
  [ -z "$n" ] && n=$(echo "$out" | grep -oP 'PASS \K\d+(?= / FAIL)' | tail -1)
  [ -z "$n" ] && n=0
  TOTAL_PASS=$((TOTAL_PASS + n))
  echo "$out" | tail -3 | grep -E '通过|PASS|全部通过|FAIL' | sed 's/^/     /'
  mark $rc "e2e/${s}_e2e.py（$n 项断言）"
done
echo
echo "════════ 汇总 ════════"
echo "  端到端断言总数：$TOTAL_PASS"
if [ "$FAILED" -eq 0 ]; then
  echo "  ✅ 全量自测通过"
else
  echo "  ❌ 失败项：$FAILED"
fi
exit "$FAILED"
