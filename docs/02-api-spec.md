# Ice-Link API 설계

> Base URL: `https://{host}/api/v1`
> Content-Type: `application/json; charset=utf-8` (파일 업로드 없음. 음성은 프론트에서 텍스트로 변환해 JSON으로 전송)
> 시간: ISO-8601 UTC · ID: 숫자(int64) · 열거형: 대문자 스네이크

관련 문서: [01-requirements.md](./01-requirements.md)

---

## 0. 공통 규약

### 0.1 인증 헤더

모든 인증은 단일 헤더 하나로 한다.

```
X-User-Key: 3a7f...c9e1        (64자 소문자 hex)
```

- 유저 키는 `POST /users` 에 이름을 보내면 **서버가** 256-bit 난수(64자 hex)로 생성해 돌려준다. 클라이언트는 이 값을 기기에 저장하고 이후 요청에 그대로 보낸다. 서버는 이 값을 `users.user_key` PK로 저장한다.
- 헤더가 없으면 `401 USER_KEY_REQUIRED`, 형식이 틀리면 `400 USER_KEY_INVALID_FORMAT`, 등록되지 않은 키면 `401 USER_NOT_FOUND`.
- **역할은 방 단위로 DB에서 판정**한다.

| 표기 | 허용 조건 | 실패 |
|---|---|---|
| `없음` | 헤더 불필요 | |
| `유저` | 등록된 유저 키 | 401 |
| `호스트` | `rooms.host_user_id == userKey` | 403 `FORBIDDEN` |
| `참가자` | `participants(room_id, user_id)` 존재, `LEFT` 아님 | 403 `FORBIDDEN` |
| `팀원` | 해당 팀의 `team_members`에 내 participant 존재 | 403 `FORBIDDEN` |
| `팀원\|호스트` | 팀원 또는 그 방의 호스트 | 403 `FORBIDDEN` |

- 인증 성공 시 서버는 `users.last_seen_at` 을 갱신한다 (1분 스로틀).

### 0.2 에러 응답 — RFC 9457 Problem Details

```json
{
  "type": "https://icelink.app/errors/room-not-waiting",
  "title": "Room is not accepting participants",
  "status": 409,
  "detail": "방이 이미 팀 빌딩을 시작했습니다.",
  "instance": "/api/v1/rooms/K7M3PQ/participants",
  "code": "ROOM_NOT_WAITING",
  "timestamp": "2026-09-19T10:00:00Z"
}
```

| HTTP | code | 상황 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 필드 검증 실패. `errors: [{field, message}]` 추가 |
| 400 | `USER_KEY_INVALID_FORMAT` | `X-User-Key` 값이 `^[0-9a-f]{64}$` 불일치 |
| 401 | `USER_KEY_REQUIRED` / `USER_NOT_FOUND` | 헤더 누락 / 미등록 키 |
| 403 | `FORBIDDEN` | 호스트/참가자/팀원 아님 |
| 404 | `ROOM_NOT_FOUND` / `TEAM_NOT_FOUND` / `QUESTION_NOT_FOUND` / `PARTICIPATION_NOT_FOUND` | |
| 409 | `USER_KEY_CONFLICT` | 난수 키 PK 충돌(사실상 발생하지 않음). 같은 이름으로 재시도 |
| 409 | `ROOM_NOT_WAITING` | WAITING 아닌 방에 입장/설문 |
| 409 | `NICKNAME_DUPLICATED` | 닉네임 자동 변경 후보(2~99)까지 모두 사용 중 (일반 중복은 서버가 자동 변경) |
| 409 | `HOST_CANNOT_JOIN` | 주최자가 자기 방에 참가 시도 |
| 409 | `ALREADY_IN_ANOTHER_ROOM` | 진행 중인 다른 방에 참가 중. `detail`에 그 방 코드 |
| 409 | `ROOM_FULL` | 인원 상한 |
| 409 | `SURVEY_INCOMPLETE` | 설문 미완료 상태에서 배정 조회 등 |
| 409 | `NOT_ENOUGH_PARTICIPANTS` | 팀 빌딩 최소 인원(2명) 미달 |
| 409 | `INVALID_STATE_TRANSITION` | 허용되지 않은 상태 전이. `detail`에 현재 상태 명시 |
| 409 | `QUESTION_LIMIT_EXCEEDED` | 세션당 AI 질문 상한 |
| 429 | `RATE_LIMITED` | |
| 409 | `ANSWER_ALREADY_SUBMITTED` | 같은 질문에 다른 팀원이 먼저 답변을 제출함 |
| 409 | `TEAM_NAME_DUPLICATED` | 같은 방의 다른 팀이 이미 쓰는 팀명 |
| 503 | `AI_PROVIDER_UNAVAILABLE` | LLM 장애 (폴백도 실패한 경우) |

### 0.3 목록 응답
방·팀 규모가 작으므로 페이지네이션 없이 배열 전체 반환.

### 0.4 열거형

```
RoomStatus        WAITING | TEAM_BUILDING | IN_PROGRESS | FINISHED
ParticipantStatus JOINED | SURVEY_DONE | ASSIGNED | LATE | LEFT
TeamStatus        NOT_STARTED | NAMING | QUESTIONING | FINISHED     (FINISHED 는 방 종료로만 전이)
QuestionType      INTRO | AI_GENERATED | FALLBACK
QuestionStatus    ANSWERING | PROCESSING | DONE | SKIPPED | FAILED
InterestCategory  MOVIE | GAME | FOOD | TRAVEL | SPORTS
ExtroversionLevel INTROVERT | BALANCED | EXTROVERT   (6~13 / 14~22 / 23~30, 표시용)
```

---

## 1. 엔드포인트 요약

### 1.1 메타 / 유저
| Method | Path | 인증 | 설명 |
|---|---|---|---|
| GET | `/health` | 없음 | 헬스체크 |
| POST | `/users` | 없음 | 유저 등록 (이름 → 서버가 난수 키 발급) |
| GET | `/users/me` | 유저 | 내 정보 + 현재 진행 중인 방 (앱 기동 시 화면 복원) |
| PATCH | `/users/me` | 유저 | 이름 변경 |
| GET | `/users/me/rooms` | 유저 | 내가 주최/참가한 방 목록 [C] |

### 1.2 방 (주최자)
| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | `/rooms` | 유저 | 방 생성 → 코드 발급, 생성자가 호스트 |
| GET | `/rooms/{code}` | 없음 | 방 공개 정보 (입장 전 확인용) |
| GET | `/host/rooms/{code}` | 호스트 | 방 상세 + 참가자 현황 + 팀 목록 |
| PATCH | `/host/rooms/{code}` | 호스트 | 방 설정 수정 (WAITING만) |
| POST | `/host/rooms/{code}/team-building` | 호스트 | 참가 마감 + 팀 빌딩 실행 |
| GET | `/host/rooms/{code}/participants` | 호스트 | 참가자 목록 |
| DELETE | `/host/rooms/{code}/participants/{participantId}` | 호스트 | 참가자 강퇴 (WAITING만) |
| GET | `/host/rooms/{code}/teams` | 호스트 | 팀 목록 (팀명, 상태, 인원, 진행 질문 수) |
| PATCH | `/host/rooms/{code}/teams/{teamId}/members` | 호스트 | 참가자 팀 이동 / LATE 배치 [C] |
| PUT | `/host/rooms/{code}/final-questions` | 호스트 | 마무리 질문 목록 수정 (종료 전까지 언제든) |
| POST | `/host/rooms/{code}/finish` | 호스트 | 아이스브레이킹 종료 → 전 팀 종료 + 마무리 질문 목록 전파 |
| GET | `/host/rooms/{code}/events` | 호스트 | SSE 스트림 (방 전체 이벤트) |

### 1.3 참가자
| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | `/rooms/{code}/participants` | 유저 | 방 참가 (멱등, 이미 참가 시 200) |
| GET | `/rooms/{code}/me` | 참가자 | 이 방에서의 내 상태 (참가·설문·팀 한번에) |
| DELETE | `/rooms/{code}/me` | 참가자 | 방 나가기 (WAITING만) |
| PUT | `/rooms/{code}/me/survey` | 참가자 | 설문 제출 (성격 + 관심사, 부분 제출 허용) |
| GET | `/rooms/{code}/me/survey` | 참가자 | 내 설문 응답 조회 |
| GET | `/rooms/{code}/me/team` | 참가자 | 배정된 팀 (미배정 시 404) |
| GET | `/rooms/{code}/me/events` | 참가자 | SSE 스트림 (방 + 내 팀 이벤트) |

### 1.4 팀 세션
| Method | Path | 인증 | 설명 |
|---|---|---|---|
| GET | `/teams/{teamId}` | 팀원\|호스트 | 팀 상세 (멤버, 상태, 현재 질문, 질문 이력) |
| POST | `/teams/{teamId}/start` | 팀원 | "모두 모였어요" → 세션 시작, Q1 생성 |
| PUT | `/teams/{teamId}/name` | 팀원 | 팀명 수정 (기본값 `"N팀"` 덮어쓰기) |
| GET | `/teams/{teamId}/questions` | 팀원\|호스트 | 질문 이력 전체 |
| GET | `/teams/{teamId}/questions/current` | 팀원\|호스트 | 현재 질문 |
| POST | `/teams/{teamId}/questions/next` | 팀원 | 다음 질문 요청 (NAMING→Q2 진입, 또는 건너뛰기) |
| POST | `/teams/{teamId}/questions/{questionId}/answer` | 팀원 | 대화 텍스트 제출 (프론트 STT 결과) → 비동기 처리 시작 |
| POST | `/teams/{teamId}/questions/{questionId}/retry` | 팀원 | FAILED 재처리 |
| GET | `/teams/{teamId}/questions/{questionId}` | 팀원\|호스트 | 질문 + 답변 처리 결과 (폴링용) |
| GET | `/teams/{teamId}/summary` | 팀원|호스트 | 종료 요약 (마무리 질문, 팀명, 질문 수, 키워드) |
| GET | `/teams/{teamId}/events` | 팀원|호스트 | SSE 스트림 (팀 이벤트만) |

> 팀 쪽에는 종료 API가 없다. 세션 종료는 주최자의 `POST /host/rooms/{code}/finish` 로만 일어난다.|호스트 | 종료 요약 (마무리 질문, 팀명, 질문 수, 키워드) |

> 팀 쪽에는 종료 API가 없다. 세션 종료는 주최자의 `POST /host/rooms/{code}/finish` 로만 일어난다.
| GET | `/teams/{teamId}/events` | 팀원\|호스트 | SSE 스트림 (팀 이벤트만) |

---

## 2. 엔드포인트 상세

### 2.0 유저

#### 유저 등록 절차 (Flutter 관점)

```
1. name = 입력값                            // 서버가 trim, 1~12자 검증
2. POST /users { name }
   - 201 → 응답의 userKey 를 flutter_secure_storage 에 저장, 완료
   - 409 USER_KEY_CONFLICT → "잠시 후 다시 시도" 안내 (같은 이름으로 재시도)
3. 이후 모든 요청: X-User-Key: {userKey}
```

클라이언트는 키를 계산하지 않는다. 키 생성 규칙은 서버 내부 사항이다.

---

#### `POST /users` — 유저 등록

**Request**
```json
{ "name": "민수" }
```
| 필드 | 제약 |
|---|---|
| name | 필수, trim 후 1~12자 |

**서버 동작**
1. `SecureRandom` 32바이트 → 소문자 hex 64자 키 생성 (이름과 무관)
2. `users` 에 저장. PK 충돌이면 `409` (확률적으로 발생하지 않음)

**201**
```json
{
  "userKey": "3a7f0d2e9b6c4f18a5d7e2c1b0f9a8d7c6e5f4a3b2c1d0e9f8a7b6c5d4e3f2a1",
  "name": "민수",
  "createdAt": "2026-09-19T10:00:00Z"
}
```
**409** `USER_KEY_CONFLICT` — 저장 시 PK 충돌. 같은 이름으로 재시도하면 된다
**400** `VALIDATION_ERROR` — 이름 누락/길이 초과

---

#### `GET /users/me` — 내 정보 + 진행 중인 방
앱 기동 시 첫 호출. 이 응답 하나로 첫 화면을 결정한다.

**200**
```json
{
  "userKey": "3a7f...f2a1",
  "name": "민수",
  "createdAt": "...",
  "activeRoom": {
    "role": "PARTICIPANT",
    "roomId": 12,
    "code": "K7M3PQ",
    "title": "KOSSCCHTHON 팀빌딩",
    "status": "IN_PROGRESS",
    "participantId": 101,
    "participantStatus": "ASSIGNED",
    "teamId": 501
  }
}
```
- `activeRoom`: 내가 주최 또는 참가 중이며 `FINISHED`가 아닌 방 중 가장 최근 것. 없으면 `null`.
- `role`: `HOST` | `PARTICIPANT`. `HOST`면 `participantId`, `participantStatus`, `teamId`는 `null`.
- 프론트 라우팅: `activeRoom == null → 홈(방 만들기 / 코드 입력)` / `role=HOST → 주최자 대시보드` / `role=PARTICIPANT → GET /rooms/{code}/me 로 세부 복원`

**401** `USER_NOT_FOUND` — 로컬 키가 서버에 없음(서버 초기화 등). 프론트는 저장 키를 지우고 이름 입력 화면으로

---

#### `PATCH /users/me` — 이름 변경

**Request**
```json
{ "name": "민수짱" }
```
**200** — `GET /users/me` 의 상단 필드. 진행 중 방의 닉네임은 바뀌지 않는다.

---

#### `GET /users/me/rooms` — 내 방 목록 [C]

**200**
```json
[
  { "role": "HOST", "roomId": 12, "code": "K7M3PQ", "title": "...", "status": "FINISHED", "createdAt": "...", "finishedAt": "..." },
  { "role": "PARTICIPANT", "roomId": 9, "code": "XY2Z8W", "title": "...", "status": "FINISHED", "teamNo": 2, "teamName": "감자전사", "createdAt": "...", "finishedAt": "..." }
]
```
최신순, 최대 50개.

---

### 2.1 메타

#### `GET /health`
**200** `{ "status": "UP" }`

성격 질문 6개의 문항 텍스트와 관심사 카테고리 5개의 표시명은 **프론트에 하드코딩**한다. 서버는 문항 번호(1~6)·점수(1~5)·카테고리 코드(`InterestCategory` 열거형)만 검증한다. 별도 메타 API는 두지 않는다.

---

### 2.2 방

#### `POST /rooms` — 방 생성
인증: `유저`. 요청한 유저가 `host_user_id`가 된다.

**Request**
```json
{
  "title": "KOSSCCHTHON 팀빌딩",
  "situation": "대학생 해커톤 참가자 30명, 서로 처음 봄. 개발자/디자이너 혼합.",
  "teamSize": 4,
  "finalQuestions": [
    "오늘 해커톤에서 우리 팀이 꼭 이루고 싶은 목표 한 가지는?",
    "서로에게 해주고 싶은 응원 한마디!"
  ]
}
```
| 필드 | 타입 | 제약 |
|---|---|---|
| title | string | 필수, 1~50자 |
| situation | string | 필수, 1~500자 |
| teamSize | int | 필수, 2~10 |
| finalQuestions | string[] | 선택(기본 `[]`), 0~5개, 각 1~200자. 종료 시 모든 참가자 화면에 표시 |

**201**
```json
{
  "roomId": 12,
  "code": "K7M3PQ",
  "host": { "userKey": "3a7f...f2a1", "name": "민수" },
  "inviteUrl": "https://icelink.app/join/K7M3PQ",
  "deepLink": "icelink://join?code=K7M3PQ",
  "title": "KOSSCCHTHON 팀빌딩",
  "situation": "...",
  "teamSize": 4,
  "finalQuestions": ["...", "..."],
  "status": "WAITING",
  "createdAt": "2026-09-19T10:00:00Z",
  "expiresAt": "2026-09-20T10:00:00Z"
}
```
**409** `ALREADY_IN_ANOTHER_ROOM` — 진행 중인 방을 이미 주최 중 (한 유저는 진행 중 방 1개만 주최)

---

#### `GET /rooms/{code}` — 방 공개 정보
입장 화면에서 코드 유효성 확인용. 인증 없음.

**200**
```json
{
  "code": "K7M3PQ",
  "title": "KOSSCCHTHON 팀빌딩",
  "status": "WAITING",
  "teamSize": 4,
  "participantCount": 17,
  "joinable": true
}
```
**404** `ROOM_NOT_FOUND`

---

#### `GET /host/rooms/{code}` — 방 상세 (주최자)

**200**
```json
{
  "roomId": 12,
  "code": "K7M3PQ",
  "title": "...",
  "situation": "...",
  "teamSize": 4,
  "finalQuestions": ["...", "..."],
  "status": "IN_PROGRESS",
  "inviteUrl": "https://icelink.app/join/K7M3PQ",
  "deepLink": "icelink://join?code=K7M3PQ",
  "counts": {
    "joined": 3,
    "surveyDone": 0,
    "assigned": 17,
    "late": 1,
    "total": 21
  },
  "participants": [
    { "participantId": 101, "nickname": "민수", "status": "ASSIGNED", "teamNo": 1, "joinedAt": "..." }
  ],
  "teams": [
    {
      "teamId": 501, "teamNo": 1, "name": "감자전사", "status": "QUESTIONING",
      "memberCount": 4, "category": "GAME", "mixed": false, "extroversionAvg": 18.5, "questionCount": 3
    }
  ],
  "createdAt": "...", "expiresAt": "...", "finishedAt": null
}
```

---

#### `PATCH /host/rooms/{code}` — 방 설정 수정
WAITING 상태에서만. 보낸 필드만 변경.

**Request**
```json
{ "teamSize": 5 }
```
마무리 질문 목록은 이 API가 아니라 아래 `PUT /host/rooms/{code}/final-questions` 로 수정한다 (상태 제한이 다름).
**200** — `POST /rooms` 응답과 동일 구조
**409** `INVALID_STATE_TRANSITION`

---

#### `POST /host/rooms/{code}/team-building` — 팀 빌딩 실행
참가를 마감하고 알고리즘을 실행한다. 동기 처리(100명 기준 1초 이내).

**Request** (선택)
```json
{ "includeIncompleteSurvey": false }
```
- `false`(기본): `SURVEY_DONE`만 배정, `JOINED`는 `LATE`로 전환
- `true`: 미완료자도 배정. 카테고리 미선택자는 잔여 인원으로, 성격 미응답자는 외향 점수 18(중앙값)로 취급

**200**
```json
{
  "roomStatus": "IN_PROGRESS",
  "teamCount": 5,
  "assignedCount": 17,
  "lateCount": 1,
  "categoryGroups": [
    { "category": "GAME", "participantCount": 8, "teamCount": 2 },
    { "category": "FOOD", "participantCount": 7, "teamCount": 2 },
    { "category": "TRAVEL", "participantCount": 2, "teamCount": 0, "leftover": true }
  ],
  "teams": [
    {
      "teamId": 501, "teamNo": 1, "name": "1팀", "category": "GAME", "mixed": true,
      "extroversionAvg": 18.5,
      "members": [
        { "participantId": 101, "nickname": "민수", "extroversionScore": 24, "interestCategory": "GAME" },
        { "participantId": 105, "nickname": "서연", "extroversionScore": 13, "interestCategory": "TRAVEL" }
      ]
    }
  ]
}
```
**409** `NOT_ENOUGH_PARTICIPANTS` (설문 완료 2명 미만) / `INVALID_STATE_TRANSITION`

SSE: 방 전체에 `TEAM_BUILDING_COMPLETED` 발행.

---

#### `GET /host/rooms/{code}/participants`

**200**
```json
[
  { "participantId": 101, "nickname": "민수", "status": "ASSIGNED", "teamId": 501, "teamNo": 1,
    "extroversionScore": 24, "interestCategory": "GAME", "joinedAt": "..." }
]
```

#### `DELETE /host/rooms/{code}/participants/{participantId}` — 강퇴
**204**. WAITING 상태만. 참가자 상태 `LEFT`, 이후 이 방의 참가자 API는 `403`. 재입장 허용(기존 행을 `JOINED`로 재활성화, 설문 초기화). SSE `PARTICIPANT_LEFT`.

---

#### `GET /host/rooms/{code}/teams`

**200**
```json
[
  {
    "teamId": 501, "teamNo": 1, "name": "감자전사", "status": "QUESTIONING",
    "category": "GAME", "mixed": false, "extroversionAvg": 18.5, "questionCount": 3,
    "currentQuestion": { "questionId": 9001, "orderNo": 3, "type": "AI_GENERATED", "status": "PROCESSING", "content": "..." },
    "members": [ { "participantId": 101, "nickname": "민수" } ],
    "startedAt": "...", "finishedAt": null
  }
]
```

#### `PATCH /host/rooms/{code}/teams/{teamId}/members` — 팀 이동 / LATE 배치 [C]
**Request**
```json
{ "add": [104], "remove": [102] }
```
**200** 변경된 팀 상세. 세션이 `NOT_STARTED`인 팀만 대상.

---

#### `PUT /host/rooms/{code}/final-questions` — 마무리 질문 목록 수정
방이 `FINISHED`가 아니면 언제든 가능. 진행 중 떠오른 질문을 추가하는 용도. 목록 전체를 덮어쓴다.

**Request**
```json
{ "finalQuestions": ["오늘 해커톤에서 우리 팀이 꼭 이루고 싶은 목표는?", "서로에게 응원 한마디!"] }
```
| 제약 | 0~5개, 각 trim 후 1~200자 |

**200**
```json
{ "finalQuestions": ["...", "..."], "updatedAt": "..." }
```
**409** `INVALID_STATE_TRANSITION` — 이미 종료된 방
SSE(방): `ROOM_UPDATED` (참가자 화면은 이 시점엔 표시하지 않고, 종료 시 받는 값으로 표시)

---

#### `POST /host/rooms/{code}/finish` — 아이스브레이킹 종료
주최자 화면의 "아이스브레이킹 종료" 버튼. 한 번에 다음을 수행한다. 멱등.

1. 방 `→ FINISHED`, 모든 팀 `→ FINISHED` (상태 무관, `NOT_STARTED` 팀 포함)
2. 각 팀의 `PROCESSING` 중인 답변은 처리를 끝내되 다음 질문은 생성하지 않음
3. 모든 참가자에게 SSE `ROOM_FINISHED` 발행. payload에 **마무리 질문 목록** 포함 → 참가자 화면이 마무리 질문 화면으로 전환

**200**
```json
{
  "roomStatus": "FINISHED",
  "finishedAt": "...",
  "finishedTeamCount": 5,
  "finalQuestions": ["오늘 해커톤에서 우리 팀이 꼭 이루고 싶은 목표는?", "서로에게 응원 한마디!"]
}
```
`WAITING`/`TEAM_BUILDING` 상태에서도 호출 가능하다(행사 취소). 이 경우 팀이 없으므로 참가자는 요약 없이 종료 화면만 본다.

---

### 2.3 참가자

#### `POST /rooms/{code}/participants` — 방 참가
인증: `유저`. 멱등: 같은 유저가 이미 이 방에 참가 중이면 `200`으로 기존 정보를 돌려준다.

**Request**
```json
{ "nickname": "민수" }
```
| 필드 | 제약 |
|---|---|
| nickname | 선택, 1~12자, trim. 생략 시 `users.name`. 방 안에 같은 닉네임(대소문자 무시)이 있으면 서버가 `민수2`처럼 자동 변경 → 응답 `nickname` 확인 |

**201** (신규) / **200** (이미 참가 중)
```json
{
  "participantId": 101,
  "nickname": "민수",
  "status": "JOINED",
  "room": { "roomId": 12, "code": "K7M3PQ", "title": "...", "status": "WAITING", "teamSize": 4 }
}
```
**409** `ROOM_NOT_WAITING` / `ROOM_FULL` / `HOST_CANNOT_JOIN` / `ALREADY_IN_ANOTHER_ROOM`
**409** `NICKNAME_DUPLICATED` — 자동 변경 후보(`민수2`~`민수99`)까지 모두 사용 중인 경우에만
SSE: `PARTICIPANT_JOINED`.

---

#### `GET /rooms/{code}/me` — 이 방에서의 내 상태
`GET /users/me` 의 `activeRoom.code` 로 진입한 뒤 세부 화면을 복원한다.

**200**
```json
{
  "participantId": 101,
  "userKey": "3a7f...f2a1",
  "nickname": "민수",
  "status": "ASSIGNED",
  "room": { "roomId": 12, "code": "K7M3PQ", "title": "...", "status": "IN_PROGRESS", "teamSize": 4, "participantCount": 21,
            "finalQuestions": null },
  "survey": { "personalityDone": true, "categoryDone": true, "extroversionScore": 24, "interestCategory": "GAME" },
  "team": {
    "teamId": 501, "teamNo": 1, "name": "감자전사", "status": "QUESTIONING",
    "category": "GAME",
    "members": [ { "participantId": 101, "nickname": "민수", "isMe": true } ]
  }
}
```
- `team`은 미배정 시 `null`.
- `room.finalQuestions`는 방이 `FINISHED`일 때만 채워지고 그 전에는 `null` (참가자에게 미리 노출하지 않음).
- 프론트 라우팅: `room.status=WAITING && !survey.*Done → 설문` / `WAITING && done → 대기` / `IN_PROGRESS && team → 팀 화면 (team.status 따라 세부)` / `FINISHED → 마무리 질문 + 요약 화면`

**403** `FORBIDDEN` — 이 방의 참가자가 아님 (나갔거나 미참가)

#### `DELETE /rooms/{code}/me` — 방 나가기
**204**. WAITING만. 상태 `LEFT`. SSE `PARTICIPANT_LEFT`.

---

#### `PUT /rooms/{code}/me/survey` — 설문 제출
성격 점수와 카테고리를 함께 보내거나 하나만 보낼 수 있다. 보낸 블록은 전체 덮어쓰기. 문항 텍스트는 프론트가 갖고 있고 서버는 번호와 점수만 받는다.

**Request**
```json
{
  "personality": [
    { "no": 1, "score": 4 }, { "no": 2, "score": 5 }, { "no": 3, "score": 3 },
    { "no": 4, "score": 4 }, { "no": 5, "score": 5 }, { "no": 6, "score": 3 }
  ],
  "interestCategory": "GAME"
}
```
| 필드 | 검증 |
|---|---|
| personality | 보낼 경우 `no` 1~6 정확히 6개, 중복 없음. `score` 1~5 정수 (5 = 매우 높음, 1 = 매우 낮음) |
| interestCategory | 보낼 경우 `MOVIE | GAME | FOOD | TRAVEL | SPORTS` 중 1개 |
| 상태 | 방 `WAITING`, 참가자 `JOINED`/`SURVEY_DONE` |

**200**
```json
{
  "status": "SURVEY_DONE",
  "personalityDone": true,
  "categoryDone": true,
  "extroversionScore": 24,
  "extroversionLevel": "EXTROVERT",
  "interestCategory": "GAME"
}
```
- `extroversionScore` = 6문항 점수 합 (6~30). `extroversionLevel`: `INTROVERT`(6~13) / `BALANCED`(14~22) / `EXTROVERT`(23~30). 표시용.
- `status`는 둘 다 완료 시 `SURVEY_DONE`, 아니면 `JOINED`. 완료 전이 시 SSE `PARTICIPANT_SURVEY_DONE`.

#### `GET /rooms/{code}/me/survey`
**200** — 위 Request 구조 + `personalityDone`, `categoryDone`, `extroversionScore`, `extroversionLevel`.

---

#### `GET /rooms/{code}/me/team` — 배정된 팀

**200** — `GET /teams/{teamId}` 와 동일 응답
**404** `TEAM_NOT_FOUND` (미배정) — 프론트는 대기 화면 유지

---

### 2.4 팀 세션

#### `GET /teams/{teamId}` — 팀 상세

**200**
```json
{
  "teamId": 501,
  "teamNo": 1,
  "name": "감자전사",
  "status": "QUESTIONING",
  "category": "GAME",
  "mixed": false,
  "questionCount": 3,
  "questionLimit": 15,
  "members": [
    { "participantId": 101, "nickname": "민수", "isMe": true },
    { "participantId": 102, "nickname": "지현", "isMe": false }
  ],
  "currentQuestion": {
    "questionId": 9001,
    "orderNo": 3,
    "type": "AI_GENERATED",
    "content": "각자 최근에 가장 몰입했던 게임이나 음식 이야기를 하나씩 해볼까요?",
    "status": "ANSWERING",
    "answer": null,
    "createdAt": "..."
  },
  "keywords": ["롤", "떡볶이", "밤샘"],
  "startedAt": "...",
  "finishedAt": null
}
```

---

#### `POST /teams/{teamId}/start` — 세션 시작 ("모두 모였어요")
`NOT_STARTED → NAMING`. Q1(INTRO) 생성. 이미 시작된 경우 200으로 현재 상태 반환(멱등).

**200**
```json
{
  "status": "NAMING",
  "currentQuestion": {
    "questionId": 8999, "orderNo": 1, "type": "INTRO",
    "content": "돌아가며 간단히 자기소개를 하고, '1팀' 대신 우리 팀만의 이름을 정해보세요!",
    "status": "ANSWERING"
  }
}
```
SSE(팀): `TEAM_STARTED`. SSE(방): `TEAM_STATUS_CHANGED`.

---

#### `PUT /teams/{teamId}/name` — 팀명 수정
팀명은 팀 빌딩 시 `"{teamNo}팀"`으로 자동 생성되어 있다. 이 API는 그 값을 덮어쓴다. 별도의 "팀명 등록" 단계는 없다.

**Request**
```json
{ "name": "감자전사" }
```
| 필드 | 제약 |
|---|---|
| name | trim 후 1~20자. 방 내 다른 팀과 중복 불가(대소문자·공백 무시). 빈 문자열 또는 `null`이면 기본값 `"{teamNo}팀"`으로 복원 |
| 상태 | 팀 `FINISHED` 아닐 것. `NOT_STARTED`(세션 시작 전)에도 허용 |

**200**
```json
{
  "teamId": 501,
  "teamNo": 1,
  "name": "감자전사",
  "isDefaultName": false,
  "updatedBy": { "participantId": 101, "nickname": "민수" }
}
```
`isDefaultName`은 현재 이름이 `"{teamNo}팀"` 과 같은지 여부. 프론트가 "아직 팀명을 정하지 않았어요" 안내에 사용.

**409** `TEAM_NAME_DUPLICATED` / `INVALID_STATE_TRANSITION`
SSE(팀+방): `TEAM_NAME_CHANGED`.

---

#### `GET /teams/{teamId}/questions` — 질문 이력

**200**
```json
[
  {
    "questionId": 8999, "orderNo": 1, "type": "INTRO", "content": "...", "status": "DONE", "answer": null, "createdAt": "..."
  },
  {
    "questionId": 9000, "orderNo": 2, "type": "AI_GENERATED", "content": "...", "status": "DONE",
    "answer": {
      "answerText": "저는 최근에 롤을 다시 시작해서...",
      "submittedBy": { "participantId": 101, "nickname": "민수" },
      "speechDurationSec": 74,
      "keywords": ["롤", "떡볶이", "밤샘"],
      "submittedAt": "...",
      "processedAt": "..."
    },
    "createdAt": "..."
  },
  { "questionId": 9001, "orderNo": 3, "type": "AI_GENERATED", "content": "...", "status": "ANSWERING", "answer": null, "createdAt": "..." }
]
```

#### `GET /teams/{teamId}/questions/current`
**200** — 위 배열의 마지막 원소 구조. 세션 미시작 시 **404** `QUESTION_NOT_FOUND`.

---

#### `POST /teams/{teamId}/questions/next` — 다음 질문 요청
두 가지 경우에 사용:
1. `NAMING` 상태에서 자기소개가 끝나 Q2로 진입 (팀명을 기본값 `"N팀"`에서 바꾸지 않았어도 허용, 팀명은 이후에도 수정 가능)
2. `QUESTIONING` 상태에서 현재 질문을 **건너뛰기** (현재 질문 `SKIPPED`)

AI 생성은 비동기. 즉시 `202`를 반환하고 생성 완료 시 SSE `QUESTION_CREATED`.

**Request** (선택)
```json
{ "reason": "SKIP" }
```

**202**
```json
{
  "status": "QUESTIONING",
  "generating": true,
  "skippedQuestionId": 9001,
  "questionCount": 3
}
```
**409** `QUESTION_LIMIT_EXCEEDED` / `INVALID_STATE_TRANSITION` (현재 질문이 `PROCESSING` 중이면 거절)

> 프론트: 202 수신 후 "질문 생성 중..." 표시 → SSE `QUESTION_CREATED` 또는 `GET /questions/current` 폴링.

---

#### `POST /teams/{teamId}/questions/{questionId}/answer` — 대화 텍스트 제출
프론트가 기기 STT로 변환한 대화 텍스트를 보낸다. 서버는 음성 파일을 받지 않는다.

**Request**
```json
{
  "answerText": "저는 최근에 롤을 다시 시작했어요 아 저는 밤새면서 떡볶이 시켜 먹은 게 기억나요 저는 게임보다 축구 보는 걸 더 좋아해요",
  "speechDurationSec": 74
}
```
| 필드 | 제약 |
|---|---|
| answerText | 필수, trim 후 1~3000자. 팀원 한 명의 기기로 녹음한 텍스트라 화자 구분이 없다. 프론트가 이름을 붙이지 않고 그대로 보낸다 |
| speechDurationSec | 선택, 0~600. 통계용 |

동작: `team_answers` 저장 → 질문 `ANSWERING → PROCESSING` → `202` 즉시 반환 → 비동기로 LLM 1회 호출(요약 + 키워드 + 다음 질문) → 질문 `DONE` → 다음 질문 INSERT → SSE `ANSWER_PROCESSED`, `QUESTION_CREATED`.

**202**
```json
{
  "questionId": 9001,
  "status": "PROCESSING",
  "estimatedSeconds": 5
}
```
**409** `INVALID_STATE_TRANSITION` — 질문이 `ANSWERING`이 아님 / 현재 질문이 아님
**409** `ANSWER_ALREADY_SUBMITTED` — 다른 팀원이 먼저 제출함 (프론트는 처리 중 화면으로 전환)
**409** `QUESTION_LIMIT_EXCEEDED` — 이 답변은 저장되지만 다음 질문은 생성하지 않음 (응답은 `202`, 본문에 `"nextQuestionGenerated": false`)

> 프론트 STT 권장: Flutter `speech_to_text` 패키지, `localeId: "ko_KR"`, `listenMode: dictation`, 부분 결과를 화면에 실시간 표시하고 "답변 완료" 버튼에서 최종 텍스트 전송.

---

#### `POST /teams/{teamId}/questions/{questionId}/retry` — 실패 재처리
`FAILED → PROCESSING`. 저장된 `answerText`로 LLM 재호출. 재시도도 실패하면 질문을 `DONE`으로 두고 카테고리 폴백 질문(`FALLBACK`)을 다음 질문으로 생성해 흐름을 이어간다.
**202** — 위와 동일. 답변이 없으면 **409**.

---

#### `GET /teams/{teamId}/questions/{questionId}` — 질문 단건 (폴링)
**200** — 질문 이력 원소 구조. `status`가 `DONE`이면 `answer.keywords` 채워짐. `FAILED`면 `failureReason` 추가:
```json
{ "questionId": 9001, "status": "FAILED", "failureReason": "LLM_TIMEOUT", "retryable": true, "...": "..." }
```
`failureReason`: `LLM_TIMEOUT` | `LLM_ERROR` | `LLM_INVALID_RESPONSE`

---

#### 팀 세션 종료에 대해
팀 쪽에는 종료·마무리 API가 없다. 주최자가 `POST /host/rooms/{code}/finish` 를 호출하면 팀이 `FINISHED`로 전이하고, 팀원은 SSE `ROOM_FINISHED` 로 마무리 질문 목록을 받아 화면을 전환한다. AI 질문 상한(15개)에 도달한 팀은 마지막 질문의 답변까지는 정상 처리(요약·키워드 저장)되지만 다음 질문은 생성되지 않고, `POST /questions/next` 는 `409 QUESTION_LIMIT_EXCEEDED` 를 반환한다. 화면에는 "주최자의 종료를 기다려 주세요"를 표시한다.

---

#### `GET /teams/{teamId}/summary` — 종료 요약
방이 `FINISHED`가 아니면 `409 INVALID_STATE_TRANSITION`.

**200**
```json
{
  "teamId": 501,
  "teamNo": 1,
  "name": "감자전사",
  "members": [ { "participantId": 101, "nickname": "민수" } ],
  "category": "GAME",
  "finalQuestions": ["오늘 해커톤에서 우리 팀이 꼭 이루고 싶은 목표는?", "서로에게 응원 한마디!"],
  "questionCount": 5,
  "answeredCount": 4,
  "durationSec": 1260,
  "keywords": ["롤", "떡볶이", "밤샘", "부산", "카페"],
  "highlights": [
    { "question": "각자 최근에 가장 몰입했던...", "keywords": ["롤", "떡볶이", "밤샘"] }
  ],
  "startedAt": "...", "finishedAt": "..."
}
```

---

## 3. 실시간 이벤트 (SSE)

### 3.1 연결

| 스트림 | Path | 인증 | 수신 범위 |
|---|---|---|---|
| 주최자 | `GET /host/rooms/{code}/events` | 호스트 | 방 전체 + 모든 팀 이벤트 |
| 참가자 | `GET /rooms/{code}/me/events` | 참가자 | 방 이벤트 + 자기 팀 이벤트 |
| 팀 | `GET /teams/{teamId}/events` | 팀원|호스트 | 해당 팀 이벤트 + `ROOM_FINISHED` (팀 화면이 종료를 알 수 있도록) |\|호스트 | 해당 팀 이벤트만 |

- `Accept: text/event-stream`, 유저 키는 `X-User-Key` 헤더로. (Flutter SSE 패키지가 헤더를 지원하지 않는 경우 `?userKey=` 쿼리도 허용. 이 경우 접근 로그에 쿼리스트링을 남기지 않는다)
- 서버는 15초마다 `: keep-alive` 코멘트 전송. 연결 타임아웃 30분 → 클라이언트 자동 재연결.
- 재연결 시 `Last-Event-ID` 헤더에 마지막 `id`를 보내면 그 이후 이벤트를 `room_events`에서 재전송.
- 접속 직후 서버가 `CONNECTED` 이벤트를 한 번 보낸다 (id 없음). data: `{scope, roomId, teamId, participantId, lastEventId, replayFrom}` — `lastEventId` 는 이 방의 현재 seq 라서 클라이언트가 재연결 기준점으로 저장해 두면 된다.
- 브로드캐스트는 이벤트를 만든 트랜잭션이 **커밋된 뒤**에 나간다. 이벤트를 받고 바로 REST 로 조회하면 반영된 상태를 본다.
- 참가자 스트림의 팀 이벤트 필터는 이벤트마다 현재 배정 팀을 확인하므로, 대기 화면에서 연결해 둔 스트림이 팀 빌딩 후에도 그대로 팀 이벤트를 받는다 (재연결 불필요).

### 3.2 이벤트 형식
```
id: 4821
event: QUESTION_CREATED
data: {"roomId":12,"teamId":501,"occurredAt":"2026-09-19T10:05:00Z","payload":{...}}

```

### 3.3 이벤트 타입

| event | 범위 | payload | 트리거 |
|---|---|---|---|
| `PARTICIPANT_JOINED` | 방 | `{participantId, nickname, participantCount}` | 참가 |
| `PARTICIPANT_LEFT` | 방 | `{participantId, participantCount}` | 나가기/강퇴 |
| `PARTICIPANT_SURVEY_DONE` | 방 | `{participantId, surveyDoneCount, participantCount}` | 설문 완료 |
| `ROOM_UPDATED` | 방 | `{title, teamSize, finalQuestionCount}` | 방 설정·마무리 질문 수정 (질문 본문은 종료 전 참가자에게 보내지 않음) |
| `TEAM_BUILDING_STARTED` | 방 | `{}` | 팀 빌딩 시작 |
| `TEAM_BUILDING_COMPLETED` | 방 | `{teamCount, myTeam: {teamId, teamNo, name, category, members[{participantId, nickname, isMe}]} | null}` | 팀 빌딩 완료. 주최자 스트림엔 `{teamCount, assignedCount, lateCount, assignments[]}`, 참가자 스트림엔 `myTeam` 채움 |
| `ROOM_FINISHED` | 방 | `{finishedAt, finalQuestions: string[], myTeam: {teamId, name, keywords: string[]} | null}` | 주최자 종료. 참가자 화면은 이 이벤트로 마무리 질문 화면 전환. `myTeam` 은 참가자 스트림에서만 |
| `TEAM_STARTED` | 팀 | `{teamId, currentQuestion}` | 모두 모였어요 |
| `TEAM_NAME_CHANGED` | 팀+방 | `{teamId, teamNo, name, isDefaultName, updatedBy}` | 팀명 수정 (기본값 복원 포함) |
| `TEAM_STATUS_CHANGED` | 방 | `{teamId, teamNo, status, questionCount}` | 팀 상태 전이 (주최자 모니터링) |
| `QUESTION_GENERATING` | 팀 | `{teamId, orderNo}` | AI 생성 시작 |
| `QUESTION_CREATED` | 팀 | `{teamId, question: {questionId, orderNo, type, content}}` | 새 질문 도착 |
| `ANSWER_PROCESSING` | 팀 | `{teamId, questionId, submittedBy}` | 텍스트 제출 접수 (다른 팀원 화면도 "처리 중"으로 전환) |
| `ANSWER_PROCESSED` | 팀 | `{teamId, questionId, keywords[]}` | 키워드 추출 완료 |
| `ANSWER_FAILED` | 팀 | `{teamId, questionId, failureReason, retryable}` | 처리 실패 |
| `TEAM_FINISHED` | 방 | `{teamId, teamNo, finishedAt}` | 방 종료에 따른 팀 종료 (주최자 모니터링용. 팀원은 `ROOM_FINISHED` 만 처리) |

### 3.4 폴링 폴백
SSE 연결 불가 시 프론트는 화면별로 아래를 3초 간격 호출:

| 화면 | 폴링 엔드포인트 |
|---|---|
| 참가자 대기 | `GET /rooms/{code}/me` (`room.participantCount`, `team` 등장 여부) |
| 팀 화면 | `GET /teams/{teamId}` (`status`, `currentQuestion.status`, `name`) |
| 주최자 | `GET /host/rooms/{code}` |

---

## 4. AI 질문 생성 — 두 가지 프롬프트

질문 생성은 시점에 따라 프롬프트가 둘로 나뉜다. 둘 다 실패하면 카테고리별 기본 질문 풀(`FALLBACK`)로 대체해 흐름을 끊지 않는다.

| | ① 카테고리 기반 첫 질문 | ② 대화 기반 꼬리 주제 |
|---|---|---|
| 호출 시점 | `POST /teams/{id}/questions/next` (NAMING → QUESTIONING 진입) | `POST /teams/{id}/questions/{qid}/answer` 처리 중 |
| 입력 | 상황 설명, 팀 카테고리, 팀원 수 | 상황 설명, 팀 카테고리, 팀원 수, 직전 질문, 대화 텍스트(화자 구분 없음), 누적 키워드, 이 팀의 이전 질문들 |
| 출력 | 대화 주제 질문 1개 (평문) | `{"keywords": [...], "nextQuestion": "..."}` (한 줄 JSON). nextQuestion 은 첫 질문과 같은 형식의 팀 전체 주제 |
| 동기/비동기 | 비동기 (202 후 SSE) | 비동기 (202 후 SSE) |

중복 방지 범위는 **팀 안**이다. 같은 방의 다른 팀이 비슷한 첫 질문을 받는 것은 허용한다.

### 4.1 프롬프트 ① — 카테고리 기반 첫 질문

```
당신은 팀 아이스브레이킹을 돕는 AI 진행자입니다.

[상황]
방의 주제: {situation}
팀의 관심사: {category}
팀원 수: {memberCount}명

[지시사항]
1. 위 정보를 바탕으로 팀을 알아가는 대화 질문 1개를 생성하세요.
2. 질문은 {category} 카테고리와 직접 관련이 있어야 합니다.
3. 질문은 자연스럽고 답하기 쉬워야 하며, 팀원 전원이 돌아가며 답할 수 있는 개방형이어야 합니다.
4. 한국어 존댓말로 1~2문장, 200자 이내로 작성하세요.
5. 정치·종교·외모·연봉·연애 여부·개인 신상을 묻는 질문은 금지합니다.
6. 질문만 반환하고, 설명·번호·따옴표·추가 텍스트는 없어야 합니다.

질문을 생성하세요:
```

응답 처리: 앞뒤 공백·따옴표 제거 → 1~200자 검증 → `type = AI_GENERATED`. 실패 시 `FallbackQuestionPool.pick(category)`.

### 4.2 프롬프트 ② — 대화 기반 꼬리 주제 (+ 키워드)

전제: 팀원 **한 명의 기기**로 질문을 보고 녹음하므로, 대화 텍스트는 여러 사람의 말이 섞인 **화자 구분 없는** 텍스트다.
따라서 개인을 지목하는 질문("OO님은 어떠세요?")은 만들 수 없고, 대화 키워드를 소재로 **팀 전체가 함께 이야기할 하나의 주제**를 첫 질문과 같은 형식으로 낸다.

```
당신은 팀 아이스브레이킹을 돕는 AI 진행자입니다.

[상황]
방의 주제: {situation}
팀의 관심사: {category}
팀원 수: {memberCount}명

[직전 대화]
이전 질문: {currentQuestion}
팀의 대화 내용 (팀원 한 명의 기기로 녹음한 음성 인식 텍스트. 여러 사람의 말이 섞여 있고 누가 말했는지는 알 수 없음): {currentAnswerText}

[누적 정보]
지금까지 나온 키워드: {accumulatedKeywords}
이전에 이미 던진 질문들: {previousQuestions}

[지시사항]
1. 팀의 대화 내용에서 핵심 키워드 3~7개를 짧은 명사구로 뽑으세요.
2. 그 키워드 중 1개 이상을 소재로, 팀원 모두가 함께 이야기할 수 있는 하나의 대화 주제 질문을 1개 생성하세요. 이전 질문과 같은 형식의 개방형 질문이어야 합니다.
3. 누가 무슨 말을 했는지는 알 수 없습니다. 특정 사람을 지목하거나 이름을 부르지 말고, "OO님은 어떠세요?" 같은 개인별 질문을 만들지 마세요. 항상 팀 전체에게 묻는 형태로 작성하세요.
4. 질문은 반드시 {category} 카테고리와 연관되어야 합니다. 대화가 다른 주제로 흘렀으면 그 주제와 카테고리를 연결하세요.
5. 이전에 던진 질문들과 소재·형식이 중복되지 않도록 하세요.
6. 한국어 존댓말로 1~2문장, 200자 이내.
7. 정치·종교·외모·연봉·연애 여부·개인 신상을 묻는 질문은 금지합니다.
8. 출력은 공백·줄바꿈 없는 한 줄 JSON 객체 {"keywords":[...],"nextQuestion":"..."} 만 허용합니다. 다른 텍스트는 금지.
```

응답 처리: `choices[0].message.content` 에서 첫 `{`~마지막 `}` 를 잘라 JSON 파싱 → `keywords` 는 답변에 저장, `nextQuestion` 은 1~200자 검증 후 새 질문(`AI_GENERATED`). 파싱 실패 → `LLM_INVALID_RESPONSE`.
답변 텍스트가 10자 미만이면 프롬프트 ②를 건너뛰고 프롬프트 ①로 일반 질문을 만든다 (Q-16).

### 4.3 비동기 처리 파이프라인 (답변 → 꼬리질문)

```
POST /answer (202)
  └─ [동기, 트랜잭션] team_answers INSERT, team_questions.status = PROCESSING (낙관적 락으로 중복 제출 차단)
  └─ SSE ANSWER_PROCESSING
  └─ @Async 작업 (Spring @Async + ThreadPoolTaskExecutor, 단일 인스턴스 기준)
       1. shouldGenerateNext = room.status == IN_PROGRESS && team.status == QUESTIONING && questionCount < limit
          (비동기 처리 중 주최자가 종료했을 수 있으므로 여기서 room 상태를 다시 조회)
       2. 프롬프트 ② 1회 호출 ──▶ { keywords[3..7], nextQuestion }
       3. team_answers UPDATE (keywords, processed_at), question DONE          → SSE ANSWER_PROCESSED
       4. shouldGenerateNext 이면
            └─ nextQuestion 유효(1~200자, 팀 내 이전 질문과 중복 아님) → type = AI_GENERATED
            └─ 아니면 FallbackQuestionPool.pick(team.category, 이 팀에서 이미 쓴 질문 제외) → type = FALLBACK
            └─ team_questions INSERT (ANSWERING), team.question_count++         → SSE QUESTION_CREATED
  └─ LLM 예외/타임아웃(15초) → question FAILED, SSE ANSWER_FAILED  (retry 엔드포인트로 재시도)
```

`generation_context`(jsonb)에는 프롬프트에 넣은 값과 `usage.total_tokens`를 저장해 디버깅·크레딧 추적에 쓴다.

---

## 4.4 AI 게이트웨이 연동

| 항목 | 값 |
|---|---|
| Base URL | `https://ai.cs.kookmin.ac.kr/v1` |
| 엔드포인트 | `POST /chat/completions` (OpenAI Chat Completions 호환) |
| 인증 | `Authorization: Bearer {ICELINK_AI_API_KEY}` |
| 모델 | `claude-opus-5` (발급된 키가 접근 가능한 유일한 모델. `claude-haiku-4-5` 는 403) |
| 타임아웃 | 연결 3초 / 읽기 15초 |
| 재시도 | 5xx·타임아웃 시 1회 (지수 백오프 1초). 그 외 즉시 FAILED |

**요청 예시**
```json
{
  "model": "claude-opus-5",
  "temperature": 0.8,
  "max_tokens": 700,
  "response_format": { "type": "json_object" },
  "messages": [
    { "role": "system", "content": "<4절 프롬프트 핵심 규칙 + 출력 JSON 스키마>" },
    { "role": "user",   "content": "<4절 LLM 입력 컨텍스트 JSON 직렬화>" }
  ]
}
```
`response_format`을 게이트웨이가 지원하지 않으면 시스템 프롬프트의 "JSON만 출력" 지시에 의존하고, 응답 본문에서 첫 `{`~마지막 `}`를 잘라 파싱한다.

**응답 파싱**
```
choices[0].message.content  →  프롬프트 ①: trim 후 질문 문자열 / 프롬프트 ②: JSON 파싱 → FollowUpResult{keywords[], nextQuestion}
```
파싱 실패 시 `failureReason = LLM_INVALID_RESPONSE`. `usage.total_tokens`는 `generation_context`에 함께 저장해 크레딧 소모를 추적한다.

**설정 (`application.properties`)**
```properties
icelink.ai.base-url=https://ai.cs.kookmin.ac.kr/v1
icelink.ai.model=claude-opus-5
icelink.ai.api-key=${ICELINK_AI_API_KEY}
icelink.ai.connect-timeout=3s
icelink.ai.read-timeout=15s
icelink.ai.max-tokens=700
```
키 값은 환경 변수 `ICELINK_AI_API_KEY`로만 주입한다. 로컬 개발은 `application-local.properties`(gitignore 대상) 또는 IDE 실행 구성의 환경 변수를 사용한다. **키를 문서·코드·커밋에 넣지 않는다.**

**구현**
- Spring `RestClient` 기반 `KookminGatewayAiClient implements ConversationAiClient`
- 요청/응답 DTO는 OpenAI 형식 최소 필드만 (`model, messages, temperature, max_tokens` / `choices[].message.content, usage`)
- `@ConfigurationProperties(prefix = "icelink.ai")` 로 설정 바인딩, 키 누락 시 기동 실패(`@NotBlank`)

---

## 5. 팀 빌딩 알고리즘 명세

입력: 참가자 목록 `[{id, extroversion(6~30), category}]`, `teamSize = s`
목표: **같은 카테고리끼리** 팀을 만들고, **각 팀의 외향 점수 평균이 서로 비슷**하게.

```
minTeam = max(2, ceil(s / 2))                 // 이보다 작은 카테고리 그룹은 팀을 만들지 않음
groups  = participants.groupBy(category)
leftover = []
teams = []

for (category, members) in groups (카테고리 열거형 순서):
    n = members.size
    if n < minTeam:
        leftover += members                    // 6절 잔여 처리
        continue
    k = max(1, round(n / s))                   // 팀 수. n=11,s=4 → 3팀(4/4/3), n=6,s=4 → 2팀(3/3), n=5,s=4 → 1팀(5)
    teams += buildBalanced(members, k, category)

if teams.isEmpty():                            // 모든 그룹이 minTeam 미달 (T-07)
    k = max(1, round(total / s))
    teams = buildBalanced(allParticipants, k, category = 최다 카테고리)
    leftover = []

for p in leftover (외향 점수 내림차순):          // 잔여 인원 배치 (T-06)
    candidates = teams.filter(t -> t.size == min(teams.size))       // 인원 최소 팀들
    target = candidates.minBy(t -> |avg(t + p) - globalAvg|)        // 넣었을 때 전체 평균에 가장 가까워지는 팀
    target.add(p); target.mixed = true

teamNo 를 1부터 순차 부여 (카테고리 순 → 팀 순), name = "{teamNo}팀" 으로 초기화
```

**`buildBalanced(members, k, category)` — 외향 평균 균등 배치**

1. **정렬**: 외향 점수 내림차순, 동점은 `participantId` 오름차순 (결정적 결과)
2. **스네이크 배분**: 정렬된 순서로 팀 `1→k`, 다음 줄은 `k→1`, 다시 `1→k` … 로 한 명씩 배치.
   상위 점수와 하위 점수가 각 팀에 교대로 들어가 평균이 자연스럽게 근접한다. 인원 차이는 최대 1명.
3. **개선(swap refinement)**: 최대 20회 반복.
   - 평균이 가장 높은 팀 `H`와 가장 낮은 팀 `L`을 고른다.
   - `H`의 멤버 `a`, `L`의 멤버 `b` 중 교환 시 `|avg(H') - avg(L')|` 가 가장 작아지는 쌍을 찾는다.
   - 그 교환이 현재 `|avg(H) - avg(L)|` 보다 실제로 줄이면 교환, 아니면 종료.
4. 각 팀 `extroversionAvg` 를 소수 1자리로 저장.

**예시** — GAME 그룹 8명, `s=4`, 점수 `[28, 26, 22, 20, 17, 15, 12, 9]` → `k=2`
- 스네이크: 팀1 ← 28, 팀2 ← 26, 팀2 ← 22, 팀1 ← 20, 팀1 ← 17, 팀2 ← 15, 팀2 ← 12, 팀1 ← 9
- 팀1 = {28, 20, 17, 9} 평균 18.5 / 팀2 = {26, 22, 15, 12} 평균 18.75 → 차이 0.25, 개선 단계에서 더 줄일 수 없으면 종료

**복잡도**: 정렬 `O(n log n)` + 스네이크 `O(n)` + 개선 `O(20 · s²)`. n=100 기준 1ms 미만.

**검증 지표(로그·응답)**: 카테고리별 팀 평균의 표준편차. 시연 시 1.5 이하를 목표.

---

## 6. 구현 메모 (Spring Boot 4.1.1)

- 패키지: `user`, `room`, `participant`, `survey`, `team`, `question`, `realtime`, `ai`, `common` (도메인별 수직 슬라이스)
- 인증: `UserKeyInterceptor` 하나가 `X-User-Key`(또는 SSE용 `?userKey=`) 형식 검증 → `users` 조회 → `last_seen_at` 갱신 → 요청 속성에 `User` 저장. `@CurrentUser User user` 커스텀 `ArgumentResolver`로 주입
- 권한: 호스트/참가자/팀원 판정은 각 서비스 메서드 진입부에서 `RoomAccessChecker.requireHost(room, user)` / `requireParticipant(room, user)` / `requireTeamMemberOrHost(team, user)` 호출. 인터셉터에서 하지 않는 이유는 경로마다 방·팀 로딩이 필요해서 서비스 계층 조회와 중복되기 때문
- 유저 키 생성(U-02): `SecureRandom.nextBytes(32)` + `HexFormat.of().withLowerCase()`. 형식 검증(U-04)은 `^[0-9a-f]{64}$`
- Rate limit: `/users/**` 는 `Bucket4j` 기반 IP당 분당 30회 필터 (또는 해커톤 범위에선 생략)
- SSE: `SseEmitter` + 방/팀 단위 `ConcurrentHashMap<Long, List<SseEmitter>>` 레지스트리. `room_events` INSERT 후 발행 (재전송 근거)
- 상태 전이: `TeamStatus` enum 내부에 `canTransitionTo()` 정의, 서비스에서 `@Version` 낙관적 락으로 중복 전이 차단
- AI 추상화: `QuestionAiClient` 인터페이스에 메서드 둘 — `generateFirstQuestion(ctx) → String` (프롬프트 ①), `generateFollowUp(ctx) → FollowUpResult{keywords, nextQuestion}` (프롬프트 ②). 구현체는 4.4절의 `KookminGatewayAiClient`. 테스트용 `StubAiClient`는 `@Profile("test")`. `FallbackQuestionPool`은 카테고리별 질문 10개씩 `resources/fallback-questions.yml`에 두고 항상 등록. STT 구현 없음 (프론트 담당)
- 비동기: `@EnableAsync` + 전용 `ThreadPoolTaskExecutor(core 4, max 8, queue 100)`. 비동기 메서드는 별도 `@Transactional` 경계에서 엔티티를 다시 조회 (detached 엔티티 넘기지 않음)
- 데이터 접근: Spring Data JPA. 엔티티는 `user`(PK `String userKey`), `room`, `participant`, `team`, `teamQuestion`, `teamAnswer`, `roomEvent`. 연관은 `@ManyToOne(fetch = LAZY)`만 쓰고 컬렉션 매핑은 피한다 (N+1 방지, 목록은 Repository 쿼리로). `keywords` 배열은 Hibernate 7 `@JdbcTypeCode(SqlTypes.ARRAY)` 또는 `String` 조인 컬럼 중 하나로 통일. `interest_category`/`category`는 `@Enumerated(EnumType.STRING)`
- 팀 빌딩: `TeamBuilder` 는 순수 함수(입력 리스트 → 팀 리스트)로 분리해 JPA 없이 단위 테스트. 5절 예시를 테스트 케이스로 고정
- 스키마: Flyway 미사용(백엔드 1인 개발). `spring.jpa.hibernate.ddl-auto=update` 로 엔티티 기준 자동 생성·변경. 컬럼 이름 변경/삭제는 `update`가 처리하지 못하므로 그런 경우 DB에서 직접 `ALTER`/`DROP` 한다. 시연 직전에는 `validate`로 바꿔 엔티티와 테이블이 어긋나지 않았는지 한 번 확인
- DB 접속: `application.properties`는 호스트·포트·DB명·계정을 환경 변수 기본값으로 갖고, 비밀번호는 `ICELINK_DB_PASSWORD` 로만 받는다. 로컬은 `application-local.properties`에 넣고 `--spring.profiles.active=local` 로 실행
- 추가 의존성: `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`
- OpenAPI: 컨트롤러에 `@Tag`, DTO에 `@Schema` 만 붙이고 이 문서와 어긋나면 이 문서를 우선 갱신
