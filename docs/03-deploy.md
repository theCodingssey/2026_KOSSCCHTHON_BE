# Ice-Link 배포 가이드

> 대상 서버: `fbwoals@fbwoalszz.iptime.org` (Linux) · 실행 방식: `java -jar`
> 환경 변수 파일: `/home/fbwoals/icelink.env`

관련 파일: [`deploy/icelink.env.example`](../deploy/icelink.env.example) · [`deploy/run.sh`](../deploy/run.sh) · [`deploy/icelink.service`](../deploy/icelink.service)

---

## 1. 서버 디렉터리 구조

```
/home/fbwoals/
├── icelink.env            ← 환경 변수 (비밀값, chmod 600, 커밋 금지)
└── icelink/
    ├── Icelink.jar        ← 빌드 산출물 (scp로 올림)
    ├── run.sh             ← 실행 스크립트
    ├── app.log            ← --bg 실행 시 로그
    └── app.pid            ← --bg 실행 시 PID
```

## 2. 최초 1회 서버 설정

```bash
ssh fbwoals@fbwoalszz.iptime.org

# Java 25 확인 (없으면 설치 필요. build.gradle toolchain = 25)
java -version

# 디렉터리
mkdir -p /home/fbwoals/icelink
```

### 2.1 환경 변수 파일 만들기

```bash
cat > /home/fbwoals/icelink.env <<'EOF'
ICELINK_DB_HOST=localhost
ICELINK_DB_PORT=5432
ICELINK_DB_NAME=kosscchthon
ICELINK_DB_USER=fbwoals
ICELINK_DB_PASSWORD=fbwoals!1234
ICELINK_AI_API_KEY=sk-4HzzvT2ChDEyHjkKJPXbTND792STFPiKWGG36IVDMndW8cSj
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod
EOF

chmod 600 /home/fbwoals/icelink.env
```

규칙:
- `KEY=value` 한 줄에 하나. `=` 양옆에 공백 없음. 값에 공백·`!`·`$` 같은 특수문자가 있으면 **작은따옴표**로 감싼다. 예: `ICELINK_DB_PASSWORD='abc!1234'`
- `#` 으로 시작하는 줄은 주석
- `export` 를 붙이지 않는다 (systemd `EnvironmentFile` 은 `export` 를 인식하지 못함. `run.sh` 는 `set -a` 로 자동 export)
- `chmod 600` 필수. 다른 계정이 비밀번호를 읽지 못하게 한다

### 2.2 값이 잘 읽히는지 확인

```bash
set -a; source /home/fbwoals/icelink.env; set +a
echo "$ICELINK_DB_HOST / $ICELINK_DB_NAME / port=$SERVER_PORT"
echo "pw length: ${#ICELINK_DB_PASSWORD}"     # 비밀번호 자체는 출력하지 않고 길이만 확인
```

## 3. 빌드와 업로드 (로컬 Windows)

```powershell
cd D:\Code\2026_KOSSCCHTHON_BE\Icelink
.\gradlew.bat clean bootJar

scp build\libs\Icelink-0.0.1-SNAPSHOT.jar fbwoals@fbwoalszz.iptime.org:/home/fbwoals/icelink/Icelink.jar
scp ..\deploy\run.sh                        fbwoals@fbwoalszz.iptime.org:/home/fbwoals/icelink/run.sh
```

서버에서 실행 권한 부여 (최초 1회):
```bash
chmod +x /home/fbwoals/icelink/run.sh
```

## 4. 실행

### 4.1 `run.sh` 로 실행 (기본)

```bash
cd /home/fbwoals/icelink
./run.sh            # 포그라운드. 로그가 터미널에 바로 보임. 첫 실행 확인용
./run.sh --bg       # 백그라운드. SSH 끊어도 유지. 로그는 app.log
./run.sh --stop     # 백그라운드 종료
tail -f app.log
```

`run.sh` 는 env 파일을 읽어 export 한 뒤 `java -jar` 를 실행한다. `ICELINK_DB_PASSWORD`, `ICELINK_AI_API_KEY` 가 비어 있으면 시작하지 않고 어떤 값이 빠졌는지 출력한다.

### 4.2 스크립트 없이 직접 실행할 때

```bash
set -a; source /home/fbwoals/icelink.env; set +a
java -jar /home/fbwoals/icelink/Icelink.jar
```

### 4.3 systemd 로 상시 운영 (선택)

재부팅 자동 시작·장애 시 자동 재시작이 필요하면:
```bash
scp deploy/icelink.service fbwoals@fbwoalszz.iptime.org:/home/fbwoals/icelink/
ssh fbwoals@fbwoalszz.iptime.org
sudo cp /home/fbwoals/icelink/icelink.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now icelink
sudo systemctl status icelink
journalctl -u icelink -f
```
재배포: jar 덮어쓰기 후 `sudo systemctl restart icelink`. `run.sh --bg` 와 systemd 를 동시에 쓰면 포트가 충돌하므로 하나만 쓴다.

## 5. 동작 확인

```bash
curl -s http://localhost:8080/actuator/health        # {"status":"UP"} 이면 DB 연결까지 정상
curl -s http://localhost:8080/swagger-ui.html -o /dev/null -w "%{http_code}\n"
```

외부(폰)에서 접속하려면 iptime 공유기에서 `8080` 포트포워딩이 필요하다. DB용 `5432` 와는 별개.

## 6. 환경 변수 → 설정 매핑

| 환경 변수 | Spring 설정 | 기본값 (env 없을 때) |
|---|---|---|
| `ICELINK_DB_HOST` | `spring.datasource.url` 의 host | `fbwoalszz.iptime.org` |
| `ICELINK_DB_PORT` | `spring.datasource.url` 의 port | `5432` |
| `ICELINK_DB_NAME` | `spring.datasource.url` 의 db | `kosscchthon` |
| `ICELINK_DB_USER` | `spring.datasource.username` | `fbwoals` |
| `ICELINK_DB_PASSWORD` | `spring.datasource.password` | 없음 (필수) |
| `ICELINK_AI_API_KEY` | `icelink.ai.api-key` | 없음 (필수) |
| `SERVER_PORT` | `server.port` | `8080` |
| `SPRING_PROFILES_ACTIVE` | 활성 프로파일 | 없음 |

`SERVER_PORT`, `SPRING_PROFILES_ACTIVE` 는 Spring Boot 가 환경 변수 이름을 자동으로 설정 키에 매핑한다(relaxed binding). 나머지는 `application.properties` 에서 `${ICELINK_...}` 로 직접 참조한다.

## 7. 자주 나는 문제

| 증상 | 원인 / 조치 |
|---|---|
| `Could not resolve placeholder 'ICELINK_DB_PASSWORD'` | env 파일을 source 하지 않았거나 `run.sh` 를 안 씀. 4.1 또는 4.2 방식으로 실행 |
| `Connection refused` (DB) | `ICELINK_DB_HOST=localhost` 로 안 되면 `fbwoalszz.iptime.org` 로 변경. PostgreSQL `listen_addresses`, `pg_hba.conf` 확인 |
| `password authentication failed` | env 파일 값에 특수문자가 있으면 작은따옴표로 감쌌는지 확인 |
| `UnsupportedClassVersionError` | 서버 Java 가 25 미만. JDK 25 설치 또는 `build.gradle` toolchain 을 서버 버전으로 낮춤 |
| `Address already in use` | 이전 프로세스가 살아 있음. `./run.sh --stop` 또는 `systemctl stop icelink` |
| `run.sh: /usr/bin/env: 'bash\r'` | CRLF 로 올라감. `sed -i 's/\r$//' run.sh` 실행 (저장소 `.gitattributes` 로 재발 방지) |
