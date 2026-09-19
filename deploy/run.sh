#!/usr/bin/env bash
# Ice-Link 실행 스크립트 (서버용)
# 위치: /home/fbwoals/icelink/run.sh
# 사용: ./run.sh            → 포그라운드 실행 (Ctrl+C 로 종료)
#       ./run.sh --bg       → 백그라운드 실행 (nohup, 로그는 app.log)
#       ./run.sh --stop     → 실행 중인 서버 종료 (app.pid 가 틀려도 프로세스 이름으로 찾음)
#       ./run.sh --restart  → 종료 후 백그라운드 재시작 (jar 교체 뒤 이걸 쓰면 된다)
#       ./run.sh --status   → 실행 여부와 PID
set -euo pipefail

APP_DIR="/home/fbwoals/icelink"
ENV_FILE="/home/fbwoals/icelink.env"
JAR="$APP_DIR/Icelink.jar"
PID_FILE="$APP_DIR/app.pid"
LOG_FILE="$APP_DIR/app.log"

# 실행 중인 Icelink 프로세스 PID 들 (pid 파일 + 프로세스 이름 검색 합집합)
find_pids() {
  local pids=""
  if [[ -f "$PID_FILE" ]]; then
    local p
    p=$(cat "$PID_FILE" 2>/dev/null || true)
    if [[ -n "$p" ]] && kill -0 "$p" 2>/dev/null; then
      pids="$p"
    fi
  fi
  local found
  found=$(pgrep -f "java .*-jar .*Icelink\.jar" || true)
  echo "$pids $found" | tr ' ' '\n' | grep -E '^[0-9]+$' | sort -u | tr '\n' ' '
}

stop_server() {
  local pids
  pids=$(find_pids)
  if [[ -z "${pids// /}" ]]; then
    echo "not running"
    rm -f "$PID_FILE"
    return 0
  fi
  echo "stopping pid(s): $pids"
  # shellcheck disable=SC2086
  kill $pids 2>/dev/null || true
  for _ in $(seq 1 15); do
    sleep 1
    pids=$(find_pids)
    [[ -z "${pids// /}" ]] && break
  done
  if [[ -n "${pids// /}" ]]; then
    echo "still alive, sending SIGKILL: $pids"
    # shellcheck disable=SC2086
    kill -9 $pids 2>/dev/null || true
    sleep 1
  fi
  rm -f "$PID_FILE"
  echo "stopped"
}

status_server() {
  local pids
  pids=$(find_pids)
  if [[ -z "${pids// /}" ]]; then
    echo "not running"
    return 1
  fi
  echo "running pid(s): $pids"
  return 0
}

load_env() {
  [[ -f "$ENV_FILE" ]] || { echo "env file not found: $ENV_FILE"; exit 1; }
  [[ -f "$JAR" ]]      || { echo "jar not found: $JAR"; exit 1; }
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
  for v in ICELINK_DB_PASSWORD ICELINK_AI_API_KEY; do
    [[ -n "${!v:-}" ]] || { echo "missing required env: $v"; exit 1; }
  done
}

start_bg() {
  local pids
  pids=$(find_pids)
  if [[ -n "${pids// /}" ]]; then
    echo "already running (pid: $pids). use --restart or --stop first."
    exit 1
  fi
  load_env
  nohup java -jar "$JAR" > "$LOG_FILE" 2>&1 &
  echo $! > "$PID_FILE"
  echo "started pid=$(cat "$PID_FILE"), log=$LOG_FILE"
  # 기동 실패(포트 충돌 등)를 바로 알 수 있게 잠깐 확인
  sleep 4
  if ! kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "process exited early. last log lines:"
    tail -n 30 "$LOG_FILE"
    rm -f "$PID_FILE"
    exit 1
  fi
}

case "${1:-}" in
  --stop)    stop_server ;;
  --status)  status_server ;;
  --restart) stop_server; start_bg ;;
  --bg)      start_bg ;;
  "")        load_env; exec java -jar "$JAR" ;;
  *)         echo "usage: $0 [--bg|--stop|--restart|--status]"; exit 2 ;;
esac
