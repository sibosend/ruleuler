#!/bin/bash
# 本地开发启动脚本：构建所有模块并后台运行 server + client
# 用法: ./start.sh [env] [--server-port PORT] [--client-port PORT]
#   ./start.sh dev
#   ./start.sh dev --server-port 18009 --client-port 18001
set -e

clear

# ─── 解析参数 ─────────────────────────────────────────────────
ENV="dev"
SERVER_PORT=16009
CLIENT_PORT=16001

while [[ $# -gt 0 ]]; do
  case "$1" in
    --server-port) SERVER_PORT="$2"; shift 2 ;;
    --client-port) CLIENT_PORT="$2"; shift 2 ;;
    *) ENV="$1"; shift ;;
  esac
done

ROOT_DIR="$(pwd)"
LOG_PATH="$ROOT_DIR/logs"
mkdir -p "$LOG_PATH"

export JAVA_HOME="${JAVA_HOME:-$(jenv javahome 2>/dev/null || echo $JAVA_HOME)}"

echo "=== 构建 [profile=$ENV] ==="

echo "[1/4] 构建 ruleuler-console-js..."
(cd ruleuler-console-js && pnpm install --frozen-lockfile -s && pnpm run start)

echo "[2/4] 构建 ruleuler-admin..."
(cd ruleuler-admin && pnpm install --frozen-lockfile -s && pnpm run build)

echo "[3/4] 构建 ruleuler-server..."
mvn clean package -pl ruleuler-server -am -DskipTests -P$ENV -q
cp ruleuler-server/target/ruleuler-server-*.jar dist/server/app.jar
rm -rf dist/server/lib && cp -r ruleuler-server/target/lib dist/server/

echo "[4/4] 构建 ruleuler-client..."
mvn clean package -pl ruleuler-client -am -DskipTests -P$ENV -q
cp ruleuler-client/target/ruleuler-client-*.jar dist/client/app.jar
rm -rf dist/client/lib && cp -r ruleuler-client/target/lib dist/client/

echo ""
echo "=== 启动服务 (server:$SERVER_PORT, client:$CLIENT_PORT) ==="

# 清理旧进程
for PORT in $SERVER_PORT $CLIENT_PORT; do
  PID=$(lsof -ti:$PORT 2>/dev/null || true)
  if [ -n "$PID" ]; then
    echo "  关闭端口 $PORT 占用进程: $PID"
    kill $PID 2>/dev/null || true
    # 等待最多 5 秒让进程优雅退出
    for i in $(seq 1 5); do
      kill -0 $PID 2>/dev/null || break
      sleep 1
    done
    # 仍未退出则强杀
    kill -9 $PID 2>/dev/null || true
  fi
done

# Ctrl+C 时停止所有子进程
trap 'echo ""; echo "停止服务..."; kill $SERVER_PID $CLIENT_PID 2>/dev/null; wait; exit 0' INT TERM

java -Dlogging.file.path="$LOG_PATH" \
     -Dspring.profiles.active="$ENV" \
     -Dserver.port="$SERVER_PORT" \
     -Dadmin.staticPath="$ROOT_DIR/ruleuler-admin/dist" \
     -Dloader.path=dist/server/lib/ \
     -jar dist/server/app.jar >> "$LOG_PATH/server.log" 2>&1 &
SERVER_PID=$!
echo "server  PID=$SERVER_PID  port=$SERVER_PORT  → logs/server.log"

java -Dlogging.file.path="$LOG_PATH" \
     -Dspring.profiles.active="$ENV" \
     -Dserver.port="$CLIENT_PORT" \
     -Durule.resporityServerUrl="http://localhost:$SERVER_PORT" \
     -Dloader.path=dist/client/lib/ \
     -jar dist/client/app.jar >> "$LOG_PATH/client.log" 2>&1 &
CLIENT_PID=$!
echo "client  PID=$CLIENT_PID  port=$CLIENT_PORT  → logs/client.log"

echo ""

# 等待端口就绪
wait_port() {
  local name=$1 pid=$2 port=$3 timeout=10 elapsed=0
  printf "  等待 %-8s ..." "$name"
  while [ $elapsed -lt $timeout ]; do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo " ✗ 进程已退出"
      return 1
    fi
    if lsof -iTCP:"$port" -sTCP:LISTEN -P -n 2>/dev/null | grep -q "$port"; then
      echo " ✓ 就绪 (${elapsed}s)"
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  echo " ✗ 超时(${timeout}s)"
  return 1
}

SERVER_OK=0; CLIENT_OK=0
wait_port "server" $SERVER_PID $SERVER_PORT && SERVER_OK=1
wait_port "client" $CLIENT_PID $CLIENT_PORT && CLIENT_OK=1

echo ""
if [ $SERVER_OK -eq 1 ] && [ $CLIENT_OK -eq 1 ]; then
  echo "=== 全部就绪 ==="
else
  echo "=== 部分服务启动失败 ==="
fi
echo "  管理后台: http://localhost:$SERVER_PORT/admin/"
echo "  客户端:   http://localhost:$CLIENT_PORT"
echo "  停止: kill $SERVER_PID $CLIENT_PID"
echo "  日志: tail -f logs/server.log logs/client.log"
echo ""

# 任意进程退出时，杀掉另一个并退出
while kill -0 $SERVER_PID 2>/dev/null && kill -0 $CLIENT_PID 2>/dev/null; do
  sleep 2
done
echo "某个进程已退出，正在停止所有服务..."
kill $SERVER_PID $CLIENT_PID 2>/dev/null
wait $SERVER_PID $CLIENT_PID 2>/dev/null
echo "已停止。"
