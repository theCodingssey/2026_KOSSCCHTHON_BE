#!/usr/bin/env bash
# Ice-Link 서버 배포 스크립트 (docker run 만 사용, compose 없음)
# 위치: /home/fbwoals/icelink/deploy.sh
#
#   ./deploy.sh backend  [tag]   백엔드 이미지 pull → 컨테이너 교체
#   ./deploy.sh frontend [tag]   프론트(nginx) 이미지 pull → 컨테이너 교체
#   ./deploy.sh all      [tag]   둘 다
#   ./deploy.sh status           컨테이너 상태
#   ./deploy.sh logs <backend|frontend>
#   ./deploy.sh stop             둘 다 중지·삭제 (이미지는 남음)
#
# 변수는 /home/fbwoals/icelink/.env 에서 읽는다 (docker.env.example 참고):
#   DOCKERHUB_USERNAME  이미지 prefix. 이미지 이름: <계정>/icelink-backend, <계정>/icelink-frontend
#   TAG                 기본 태그 (보통 latest). 인자로 tag 를 주면 그 값이 우선 → 롤백: ./deploy.sh backend sha-1a2b3c4
# 앱 비밀값(DB 비밀번호, AI 키)은 /home/fbwoals/icelink.env 를 백엔드 컨테이너에 --env-file 로 넘긴다.
#
# GitHub Actions 워크플로는 이 스크립트와 같은 docker 명령을 인라인으로 실행한다. 옵션을 바꾸면 양쪽을 같이 맞춘다.
set -euo pipefail

APP_DIR="/home/fbwoals/icelink"
APP_ENV_FILE="/home/fbwoals/icelink.env"
VARS_FILE="$APP_DIR/.env"

if [[ -f "$VARS_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$VARS_FILE"
  set +a
fi
: "${DOCKERHUB_USERNAME:?DOCKERHUB_USERNAME 이 없습니다 ($VARS_FILE)}"
TAG="${2:-${TAG:-latest}}"

BE_IMAGE="$DOCKERHUB_USERNAME/icelink-backend:$TAG"
FE_IMAGE="$DOCKERHUB_USERNAME/icelink-frontend:$TAG"
LOG_OPTS=(--log-opt max-size=20m --log-opt max-file=5)

deploy_backend() {
  [[ -f "$APP_ENV_FILE" ]] || { echo "env file not found: $APP_ENV_FILE"; exit 1; }
  echo "==> backend: $BE_IMAGE"
  docker pull "$BE_IMAGE"
  docker rm -f icelink-backend >/dev/null 2>&1 || true
  # 호스트 네트워크: 호스트 PostgreSQL(localhost:5432) 을 그대로 쓰고 8080 을 호스트에 직접 바인딩
  docker run -d --name icelink-backend --restart unless-stopped \
    --network host \
    --env-file "$APP_ENV_FILE" -e SPRING_PROFILES_ACTIVE=prod \
    "${LOG_OPTS[@]}" "$BE_IMAGE" >/dev/null
  echo "    waiting for health..."
  for _ in $(seq 1 30); do
    if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
      echo "    backend UP"; return 0
    fi
    sleep 2
  done
  echo "    backend did not become healthy in 60s. last log lines:"; docker logs --tail 40 icelink-backend; exit 1
}

deploy_frontend() {
  echo "==> frontend: $FE_IMAGE"
  docker pull "$FE_IMAGE"
  docker rm -f icelink-frontend >/dev/null 2>&1 || true
  # nginx(bridge) 가 호스트의 8080 으로 프록시할 수 있게 host.docker.internal 을 등록
  docker run -d --name icelink-frontend --restart unless-stopped \
    -p 80:80 \
    --add-host host.docker.internal:host-gateway \
    "${LOG_OPTS[@]}" "$FE_IMAGE" >/dev/null
  sleep 2
  curl -fsS http://localhost/healthz >/dev/null && echo "    frontend UP"
}

status() {
  docker ps -a --filter name=icelink- --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
}

case "${1:-}" in
  backend)  deploy_backend; docker image prune -f >/dev/null; status ;;
  frontend) deploy_frontend; docker image prune -f >/dev/null; status ;;
  all)      deploy_backend; deploy_frontend; docker image prune -f >/dev/null; status ;;
  status)   status ;;
  logs)     docker logs -f "icelink-${2:?backend|frontend}" ;;
  stop)     docker rm -f icelink-frontend icelink-backend 2>/dev/null || true; status ;;
  *)        echo "usage: $0 <backend|frontend|all> [tag] | status | logs <backend|frontend> | stop"; exit 2 ;;
esac
