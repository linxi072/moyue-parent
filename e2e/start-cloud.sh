#!/bin/bash
# 关键：清除沙箱/agent 注入的 SERVER__PORT（=server.port）环境变量，
# 否则所有服务都会被强制绑到 49386（被 codebuddy prewarm 进程占用），导致启动冲突。
unset SERVER__PORT SERVER_PORT
# 停止：按端口取 PID，逐个 kill 并等待端口真正释放
for p in 8080 8082 8083 8084 8085 8086 8087 8089 8090 8097; do
  pid=$(ss -ltnp 2>/dev/null | grep ":$p " | grep -oP 'pid=\K[0-9]+' | head -1)
  if [ -n "$pid" ]; then
    kill "$pid" 2>/dev/null
    echo "stopped $p (pid=$pid)"
  fi
done
for i in $(seq 1 30); do
  busy=0
  for p in 8080 8082 8083 8084 8085 8086 8087 8089 8090 8097; do
    ss -ltnp 2>/dev/null | grep -q ":$p " && busy=1
  done
  [ "$busy" -eq 0 ] && { echo "all ports free after ${i}s"; break; }
  sleep 1
done

cd /tmp/cloud-run
J=/opt/jdk21/bin/java
# 沙箱 cgroup 内存上限仅 8GB，10 个服务默认堆会把 Nacos/MySQL 挤到 OOM（exit 137）。
# 统一限制单服务堆 320M，足够跑 E2E 链路又不触发内核 OOM killer。
decl() { setsid nohup $J -Xms128m -Xmx320m -XX:MaxMetaspaceSize=128m \
  -XX:+UseSerialGC -XX:TieredStopAtLevel=1 \
  -jar "$1" > "/tmp/cloud-run/$2.log" 2>&1 & echo "started $2 pid=$!"; }
decl /workspace/moyue-cloud/moyue-modules/moyue-system/target/moyue-system-1.0.0-SNAPSHOT.jar system
sleep 2
decl /workspace/moyue-cloud/moyue-auth/target/moyue-auth-1.0.0-SNAPSHOT.jar auth
sleep 2
decl /workspace/moyue-cloud/moyue-modules/moyue-content/target/moyue-content-1.0.0-SNAPSHOT.jar content
sleep 2
decl /workspace/moyue-cloud/moyue-modules/moyue-social/target/moyue-social-1.0.0-SNAPSHOT.jar social
sleep 2
decl /workspace/moyue-cloud/moyue-modules/moyue-commerce/target/moyue-commerce-1.0.0-SNAPSHOT.jar commerce
sleep 2
decl /workspace/moyue-cloud/moyue-modules/moyue-search/target/moyue-search-1.0.0-SNAPSHOT.jar search
sleep 2
decl /workspace/moyue-cloud/moyue-modules/moyue-message/target/moyue-message-1.0.0-SNAPSHOT.jar message
sleep 2
decl /workspace/moyue-cloud/moyue-modules/moyue-risk/target/moyue-risk-1.0.0-SNAPSHOT.jar risk
sleep 2
decl /workspace/moyue-cloud/moyue-modules/moyue-ai/target/moyue-ai-1.0.0-SNAPSHOT.jar ai
sleep 6
decl /workspace/moyue-cloud/moyue-gateway/target/moyue-gateway-1.0.0-SNAPSHOT.jar gateway
