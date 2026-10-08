#!/bin/bash
# 墨阅 E2E 基础设施拉起脚本
#
# 背景：沙箱休眠会回收容器（状态变 Exited 255/137），导致服务连不上 MySQL/Redis/Nacos。
# 本脚本做「幂等拉起 + 就绪等待」，可反复执行。
#
# 两个历史坑位（已在此脚本内固化修复）：
#   1. Nacos 2.x 客户端除 HTTP 8848 外，还需 gRPC 9848/9849，否则报
#      "Connection refused: /127.0.0.1:9848" 且重试 42 次后放弃注册。
#   2. Nacos 容器 JVM_XMS 必须 <= JVM_XMX，否则启动即退出：
#      "Initial heap size set to a larger value than the maximum heap size"。

set -u

ensure_container() {
  local name="$1"; shift
  if docker ps -a --format '{{.Names}}' | grep -qx "$name"; then
    if docker ps --format '{{.Names}}' | grep -qx "$name"; then
      echo "[infra] $name already running"
    else
      echo "[infra] starting existing $name"
      docker start "$name" >/dev/null
    fi
  else
    echo "[infra] creating $name"
    docker run -d --name "$name" "$@" >/dev/null
  fi
}

ensure_container moyue-mysql \
  -p 3306:3306 \
  --memory=900m \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=moyue \
  -e TZ=Asia/Shanghai \
  mysql:8.0 --default-time-zone=+08:00 \
  --innodb-buffer-pool-size=256M

ensure_container moyue-redis \
  -p 6379:6379 \
  --memory=192m \
  redis:7-alpine \
  --maxmemory 128mb --maxmemory-policy allkeys-lru

ensure_container moyue-nacos \
  -p 8848:8848 -p 9848:9848 -p 9849:9849 \
  --memory=1200m \
  -e MODE=standalone \
  -e JVM_XMS=256m -e JVM_XMX=384m -e JVM_XMN=128m \
  nacos/nacos-server:v2.3.2

wait_tcp() {
  local host="$1" port="$2" label="$3" tries="${4:-60}"
  for i in $(seq 1 "$tries"); do
    if python3 - "$host" "$port" >/dev/null 2>&1 <<'PY'
import socket,sys
s=socket.socket(); s.settimeout(1.5)
try:
    s.connect((sys.argv[1], int(sys.argv[2])))
except Exception:
    sys.exit(1)
finally:
    s.close()
PY
    then echo "[infra] $label ready (~$((i*3))s)"; return 0; fi
    sleep 3
  done
  echo "[infra] !! $label NOT ready after $((tries*3))s"; return 1
}

wait_tcp 127.0.0.1 3306 "mysql:3306" 60
wait_tcp 127.0.0.1 6379 "redis:6379" 30
wait_tcp 127.0.0.1 9848 "nacos-grpc:9848" 60

for i in $(seq 1 40); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 \
    http://127.0.0.1:8848/nacos/v1/console/health/readiness)
  [ "$code" = "200" ] && { echo "[infra] nacos console ready (${i}x5s)"; break; }
  sleep 5
done
[ "${code:-000}" = "200" ] || echo "[infra] !! nacos console not ready"

echo "[infra] done."
docker ps --format '{{.Names}}\t{{.Status}}'
