# Ice-Link Docker 배포 (GitHub Actions → Docker Hub → 서버 `docker run`)

> 대상 서버: `fbwoals@fbwoalszz.iptime.org` (Linux, 가정용 공유기 뒤) · 배포 디렉터리: `/home/fbwoals/icelink`
> DB: 서버 호스트의 PostgreSQL 을 그대로 사용 (컨테이너 아님) · compose 없이 `docker run` 만 사용
> 이 문서가 [`03-deploy.md`](./03-deploy.md)(`java -jar` 수동 배포)를 대체한다. 둘을 동시에 쓰면 8080 포트가 충돌한다.

관련 파일
- 백엔드: [`Icelink/Dockerfile`](../Icelink/Dockerfile) · [`deploy/deploy.sh`](../deploy/deploy.sh) · [`deploy/docker.env.example`](../deploy/docker.env.example) · [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml)
- 프론트(React 저장소): `Dockerfile` · `deploy/nginx.conf` · `deploy/00-log-format.conf` · `.github/workflows/deploy.yml`

---

## 1. 구조

```
                        인터넷
                          │ :80  (공유기 포트포워딩 80 → 서버 80)
                          ▼
   ┌─ docker: icelink-frontend (nginx) ─────────────────────────┐
   │   /            React dist (SPA 폴백)                        │
   │   /api/  ───►  host.docker.internal:8080  (버퍼링 off, SSE) │
   └──────────────────────────────────────────────────────────────┘
                          │
   ┌─ docker: icelink-backend (--network host) ─────────────────┐
   │   Spring Boot :8080  ──►  localhost:5432 PostgreSQL (호스트) │
   │   --env-file /home/fbwoals/icelink.env                       │
   └──────────────────────────────────────────────────────────────┘

   GitHub Actions (main 푸시)
     test/lint → docker build → Docker Hub push (latest, sha-xxxxxxx)
     → SSH (비밀번호, 공유기 포워딩 포트 → 서버 22) → docker pull → docker rm -f → docker run
```

- 프론트는 `VITE_API_BASE_URL=/api/v1` 로 빌드되어 같은 도메인의 nginx 를 통해 백엔드를 호출한다. CORS 없음, 8080 외부 노출 없음.
- 백엔드 컨테이너는 **호스트 네트워크**(`--network host`)로 떠서 기존 `icelink.env` 의 `ICELINK_DB_HOST=localhost` 가 그대로 동작한다. `pg_hba.conf`, `listen_addresses` 수정 불필요.
- 이미지는 Docker Hub 에 있고 서버는 pull 만 한다. 서버 `/home/fbwoals/icelink` 에는 `deploy.sh` 와 변수 파일 `.env` 만 둔다.
- 워크플로의 SSH 단계는 `deploy.sh` 와 **같은 `docker run` 옵션**을 인라인으로 실행한다. 옵션을 바꾸면 워크플로 2개와 `deploy.sh` 를 함께 고친다.

컨테이너 실행 옵션 (기준):

```bash
# backend
docker run -d --name icelink-backend --restart unless-stopped \
  --network host \
  --env-file /home/fbwoals/icelink.env -e SPRING_PROFILES_ACTIVE=prod \
  --log-opt max-size=20m --log-opt max-file=5 \
  <계정>/icelink-backend:latest

# frontend
docker run -d --name icelink-frontend --restart unless-stopped \
  -p 80:80 \
  --add-host host.docker.internal:host-gateway \
  --log-opt max-size=20m --log-opt max-file=5 \
  <계정>/icelink-frontend:latest
```

## 2. 서버 1회 설정

### 2.1 기존 java -jar 프로세스 정리
```bash
cd /home/fbwoals/icelink
./run.sh --stop                              # run.sh 로 띄웠다면
sudo systemctl disable --now icelink 2>/dev/null || true   # systemd 로 띄웠다면
ss -ltnp | grep -E ':8080|:80 ' || echo "ports free"
```

### 2.2 Docker 설치 (Ubuntu/Debian)
```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker fbwoals
newgrp docker                                # 또는 재로그인
docker --version
```

### 2.3 배포 디렉터리 파일
```bash
mkdir -p /home/fbwoals/icelink && cd /home/fbwoals/icelink
curl -fsSLO https://raw.githubusercontent.com/theCodingssey/2026_KOSSCCHTHON_BE/main/deploy/deploy.sh
curl -fsSL  https://raw.githubusercontent.com/theCodingssey/2026_KOSSCCHTHON_BE/main/deploy/docker.env.example -o .env
chmod +x deploy.sh
nano .env                                    # DOCKERHUB_USERNAME 확인, TAG=latest
```

`/home/fbwoals/icelink.env` (앱 비밀값) 는 기존 그대로 쓴다. `ICELINK_DB_HOST=localhost`, `SERVER_PORT=8080` 확인.

최종 디렉터리:
```
/home/fbwoals/
├── icelink.env              ← 앱 비밀값 (DB 비밀번호, AI 키). chmod 600
└── icelink/
    ├── deploy.sh            ← 수동 배포·롤백·상태 확인
    └── .env                 ← deploy.sh 변수 (DOCKERHUB_USERNAME, TAG)
```

### 2.4 SSH 접속 (비밀번호 인증)
Actions 러너가 `sshpass` 로 비밀번호를 전달해 `fbwoals` 계정으로 접속한다. 서버 `/etc/ssh/sshd_config` 에서 `PasswordAuthentication yes` 인지 확인한다 (기본값 yes).

SSH 포트가 인터넷에 열리므로 최소한 다음은 지킨다.
- 계정 비밀번호를 길고 추측 불가능하게 (GitHub Secret 에 같은 값)
- 외부 포트는 22 대신 다른 번호로 포워딩 (2.5절)
- 가능하면 `sudo apt install fail2ban` 으로 무차별 대입을 차단

나중에 키 인증으로 바꾸려면 `ssh-keygen -t ed25519` 로 만든 공개키를 서버 `~/.ssh/authorized_keys` 에 넣고, 워크플로의 `sshpass -e ssh` 를 `ssh -i` 로 바꾸면 된다.

### 2.5 공유기 포트포워딩 (iptime)
| 외부 포트 | 내부 IP:포트 | 용도 |
|---|---|---|
| 80 | 서버:80 | 웹 + API (nginx) |
| 2222 (임의) | 서버:22 | GitHub Actions SSH 배포 |

SSH 는 22 를 그대로 열지 말고 다른 외부 포트로 포워딩한다. 그 값이 `SSH_PORT` 시크릿이다. 기존 8080 포워딩은 닫는다.

### 2.6 Docker Hub
1. hub.docker.com 계정명이 `DOCKERHUB_USERNAME`
2. Account Settings → Personal access tokens → **Read & Write** 토큰 생성 → `DOCKERHUB_TOKEN`
3. 저장소 `icelink-backend`, `icelink-frontend` 는 첫 push 때 자동 생성된다 (기본 public). private 로 바꾸면 서버에서 `docker login` 1회 필요.

## 3. GitHub Secrets

두 저장소(백엔드, React) 모두 Settings → Secrets and variables → Actions → New repository secret 에 **같은 값**을 등록한다.

| Secret | 값 | 비고 |
|---|---|---|
| `DOCKERHUB_USERNAME` | Docker Hub 계정명 | 이미지 이름 prefix |
| `DOCKERHUB_TOKEN` | Docker Hub Access Token | Read & Write |
| `SSH_HOST` | `fbwoalszz.iptime.org` | |
| `SSH_PORT` | `2222` | 2.5 에서 정한 외부 포트 |
| `SSH_USER` | `fbwoals` | |
| `SSH_PASSWORD` | `fbwoals` 계정 비밀번호 | 러너에서 `sshpass` 로 전달. 특수문자 포함 그대로 입력 |

서버 쪽 파일과의 관계:

| 어디 | 파일 | 내용 |
|---|---|---|
| 서버 | `/home/fbwoals/icelink.env` | 앱 비밀값 (`ICELINK_DB_*`, `ICELINK_AI_API_KEY`, `SERVER_PORT`) — 기존 그대로 |
| 서버 | `/home/fbwoals/icelink/.env` | `deploy.sh` 변수 (`DOCKERHUB_USERNAME`, `TAG`) |
| GitHub | Secrets 6개 | 이미지 푸시 + SSH 접속용. 앱 비밀값은 GitHub 에 두지 않는다 |

## 4. 첫 배포

1. 백엔드 저장소 `main` 에 푸시 (또는 Actions 탭 → Deploy backend → Run workflow). 순서: Gradle test → 이미지 빌드·푸시 → SSH 로 `docker pull` → `docker rm -f icelink-backend` → `docker run` → 헬스체크.
2. React 저장소 `main` 에 푸시 (또는 Deploy frontend 수동 실행). 순서: tsc·lint → 이미지 빌드·푸시 → SSH 로 pull → 컨테이너 교체 → `/healthz` 확인.
3. 확인
   ```bash
   # 서버
   /home/fbwoals/icelink/deploy.sh status
   docker logs -f icelink-backend
   curl -s http://localhost:8080/actuator/health          # {"status":"UP"}
   curl -s http://localhost/healthz                        # ok
   curl -s http://localhost/api/v1/rooms/XXXXXX -i | head -1   # 404 problem+json 이면 프록시 정상
   ```
   외부: `http://fbwoalszz.iptime.org/` 접속 → 이름 입력 화면.

Actions 없이 서버에서 직접 올리려면 (이미지가 Docker Hub 에 있어야 함):
```bash
cd /home/fbwoals/icelink && ./deploy.sh all
```

## 5. 운영

| 작업 | 명령 (서버, `/home/fbwoals/icelink`) |
|---|---|
| 상태 | `./deploy.sh status` |
| 로그 | `./deploy.sh logs backend` / `./deploy.sh logs frontend` |
| 재시작 | `docker restart icelink-backend` |
| 수동 갱신 | `./deploy.sh backend` / `./deploy.sh frontend` / `./deploy.sh all` |
| 롤백 | Actions 로그 또는 Docker Hub 에서 이전 `sha-xxxxxxx` 확인 → `./deploy.sh backend sha-xxxxxxx` |
| 중지 | `./deploy.sh stop` (DB 는 호스트에 있으므로 영향 없음) |
| 정리 | `docker image prune -f` (배포마다 자동 실행) |

컨테이너는 `--restart unless-stopped` 라 서버 재부팅 후 Docker 데몬이 올라오면 자동으로 다시 뜬다. 두 워크플로는 `concurrency` 그룹으로 같은 서비스의 동시 배포를 막고, 백엔드 워크플로는 `Icelink/**` 가 바뀔 때만 돈다.

## 6. HTTPS (선택, 권장)

80 이 열려 있으면 iptime DDNS 도메인으로 Let's Encrypt 인증서를 받을 수 있다. 가장 간단한 방법은 호스트에 Caddy 를 두고 80/443 을 받아 `icelink-frontend` 로 넘기는 것이다 (frontend 를 `-p 127.0.0.1:8081:80` 으로 바꾸고 Caddy 가 `reverse_proxy 127.0.0.1:8081`). HTTPS 가 되면
- 브라우저 마이크 권한(음성 인식)이 localhost 밖에서도 동작한다
- HTTP/2 다중화로 브라우저의 호스트당 6연결 제한(SSE 탭 여러 개)이 사라진다

## 7. 자주 나는 문제

| 증상 | 원인 / 조치 |
|---|---|
| Actions `deploy` 에서 `Connection timed out` | 공유기 포트포워딩(외부 `SSH_PORT` → 서버 22) 누락, 또는 `SSH_HOST` 오타 |
| `Permission denied, please try again` | `SSH_PASSWORD` 오타, 또는 서버 `sshd_config` 의 `PasswordAuthentication no`. `SSH_USER` 가 `fbwoals` 인지 확인 |
| `docker: permission denied` | `usermod -aG docker fbwoals` 후 재로그인 안 함 |
| 백엔드 `Connection refused` (DB) | `icelink.env` 의 `ICELINK_DB_HOST=localhost` 확인. 백엔드는 host 네트워크라 localhost 가 호스트다 |
| 웹은 뜨는데 `/api/` 502 | 백엔드 컨테이너가 안 떠 있음 (`docker logs icelink-backend`). 또는 8080 을 옛 java 프로세스가 점유 (`./run.sh --stop`) |
| `/actuator/health` 프록시가 400 | nginx upstream 이름에 밑줄이 들어가 Host 헤더로 전달됨. 현재 설정은 하이픈 사용 |
| SSE 가 연결은 되는데 이벤트가 안 옴 | nginx 설정에서 `proxy_buffering off` 가 빠졌는지, 이미지가 최신인지 확인 |
| `port is already allocated` (80) | 호스트에 nginx/apache 가 이미 80 을 쓰고 있음. 중지하거나 `-p` 포트 변경 |
| 이미지 pull 이 `denied` | Docker Hub 저장소가 private. 서버에서 `docker login` 1회 |
