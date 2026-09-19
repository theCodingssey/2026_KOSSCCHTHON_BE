#!/usr/bin/env bash
# Ice-Link 실행 스크립트 (서버용)
# 위치: /home/fbwoals/icelink/run.sh
# 사용: ./run.sh            → 포그라운드 실행 (Ctrl+C 로 종료)
#       ./run.sh --bg       → 백그라운드 실행 (nohup, 로그는 app.log)
#       ./run.sh --stop     → 백그라운드 프로세스 종료
set -euo pipefail

APP_DIR="/home/fbwoals/icelink"
ENV_FILE="/home/fbwoals/icelink.env"
JAR="$APP_DIR/Icelink.jar"
PID_FILE="$APP_DIR/app.pid"
LOG_FILE="$APP_DIR/app.log"

if [[ "${1:-}" == "--stop" ]]; then
  if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    kill "$(cat "$PID_FILE")"
    rm -f "$PID_FILE"
    echo "stopped"
  else
    echo "not running"
  fi
  exit 0
fi

[[ -f "$ENV_FILE" ]] || { echo "env file not found: $ENV_FILE"; exit 1; }
[[ -f "$JAR" ]]      || { echo "jar not found: $JAR"; exit 1; }

# env 파일의 모든 변수를 export 상태로 로드
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

# 필수 값 확인
for v in ICELINK_DB_PASSWORD ICELINK_AI_API_KEY; do
  [[ -n "${!v:-}" ]] || { echo "missing required env: $v"; exit 1; }
done

if [[ "${1:-}" == "--bg" ]]; then
  nohup java -jar "$JAR" > "$LOG_FILE" 2>&1 &
  echo $! > "$PID_FILE"
  echo "started pid=$(cat "$PID_FILE"), log=$LOG_FILE"
else
  exec java -jar "$JAR"
fi
