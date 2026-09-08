# Phase 6: 운영 - Context

**Gathered:** 2026-08-18
**Status:** Ready for planning

<domain>
## Phase Boundary

관리자가 모든 수업(저녁반/예약제/1:1)의 타임별 출석을 체크하고(참고용 데이터), 저녁반 참여를
0.5회 수동 차감하며(`EVENING_HALF`), 공지사항을 운영하고, Phase 4가 생성해 둔 알림 레코드를
폴링 조회·확인 처리·활동 피드로 소비한다. 아울러 Phase 5에서 이월된 CR-03(기준일 후보 ①
마지막 출석일 부재)을 출석 데이터 배선으로 닫고, 꺼 둔 미사용 차감 cron의 운영 켜기 절차를
문서화한다.

- **ATTEND-01**: 모든 수업 타임별 출석 체크 — 차감과 무관한 참고 데이터 (policies §6)
- **ATTEND-02**: 저녁반 0.5회 수동 차감 (`EVENING_HALF`) — 잔여 0.5 이상일 때만
- **NOTICE-01/02**: 공지 등록·수정·삭제(관리자) + 목록·상세 열람(회원)
- **NOTIF-02**: 알림 목록 폴링(30초) + 미확인 카운트 + 확인 처리
- **NOTIF-03**: 활동 피드 — 알림과 동일 데이터의 다른 뷰
- **이월(CR-03)**: `InactivityBatchRunner`의 `lastAttendanceDate = null` 하드코딩을 실제 조회로
  교체해 기준일 5종 max를 완결하고, cron 활성 절차를 문서화 (D-116·D-119·D-121)

**이 phase가 만들지 않는 것:**
- 회원용 알림(수신자는 관리자로 고정 — D-097, Phase 4에서 확정)
- 알림 실시간 푸시(WebSocket/SSE) — 30초 폴링으로 충족
- 공지 첨부파일·고정(핀)·노출 예약 — MVP 밖
- cron 기본값 변경 — D-121(기본 꺼짐) 유지, 켜는 것은 운영 배포 절차의 일

</domain>

<decisions>
## Implementation Decisions

**논의 방식**: 사용자가 4개 영역 + 공지 재량안에 대한 입장을 선제 제시했고, 코드·정책 대조로
반대 근거가 없는 것은 그대로 확정, 빈칸 2건(차감 불가 시 처리·복구 사유 코드)은 보완 질문으로
확정했다. 아래 결정은 `docs/decisions.md` **D-127~D-131**로 기록 완료, 소급 규칙은
`docs/policies.md` §6에 반영 완료 — **downstream 에이전트는 policies §6 확정 문구를 원본으로 삼는다.**

### 1. 출석 체크 모델 (D-127)
- 출석 상태는 **레코드 부재 = 미체크**, 레코드는 `ATTENDED`/`ABSENT` 2값
- **예약제/1:1**: 예약자 명단을 프리로드해 출석/불참을 체크 (예약했지만 불참 = 차감 유지 + `ABSENT`)
- **저녁반**: 빈 명단에서 관리자가 회원 검색으로 추가, **추가됨 = 출석**(`ATTENDED`만, 불참 없음)
- **회원×세션 유니크 제약** — 출석 1건과 (저녁반의) 차감 1건을 DB 수준에서 함께 고정
- **소급 입력·수정 허용**. 단, 소급 출석이 이미 실행된 `INACTIVITY` 차감을 되돌리지 않는다 —
  기준일 반영은 다음 배치 실행부터. 코드도 이미 이 방향(`deficit.coerceAtLeast(0)`,
  `InactivityDueDateCalculator.kt:109`)이며, 정정이 필요하면 관리자가 `ADMIN_ADJUST`로 수동 복구
- 미사용 차감 기준일 후보 ①(마지막 출석일)은 **`ATTENDED`만 인정** (불참은 미사용)

### 2. 저녁반 0.5회 차감 (D-128)
- **저녁반 출석 추가와 0.5회 차감은 한 트랜잭션** — 서버가 판정한다:
  - 유효한 저녁반 회비(`EVENING_MEMBERSHIP`) 보유 시 **차감 없음** (회비 우선)
  - 없으면 `SESSION_PASS`에서 0.5 차감 — 차감 대상 선택은 **D-091(만료 임박순) 재사용**,
    유효기간 판정 기준일은 오늘이 아니라 **수업날** (소급 입력 시 그날 유효했는지로 판정)
  - 둘 다 불가(회비 없음 + `SESSION_PASS` 잔여 0.5 미만)면 **출석 추가 자체를 409로 거부** —
    관리자가 `ADMIN_ADJUST`로 충전 후 재시도
- 이중 차감은 회원×세션 유니크로 구조적 방지, 차감 이력은 출석 레코드에 연결(FK)
- **저녁반 출석 삭제 시 연결된 차감을 자동 복구** — 사유는 **`EVENING_HALF_REFUND` 신설**
  (예약 복구가 `CANCEL_REFUND`·`CLASS_CANCELED_REFUND`로 구분되는 선례와 일관.
  `pass_transaction.reason`은 VARCHAR(30)에 DB CHECK 없음 — enum 상수 추가만으로 확장 가능)

### 3. 알림 폴링·활동 피드 (D-129)
- 확인 처리는 **"모두 읽음"만** — 개별 읽음 없음. `Notification.isRead/readAt`(이미 `var`) 벌크 UPDATE
- **미확인 카운트는 알림 목록 응답에 포함** — 별도 카운트 엔드포인트 없음
- 활동 피드는 **알림과 동일 데이터(notification 테이블)의 다른 뷰** — 읽음 여부 무관 시간순,
  필터는 **기간 + 종류**, 페이지네이션은 기존 page/size(`PageResponse`) 형태 재사용

### 4. CR-03 마감·cron 재활성 (D-130)
- `InactivityBatchRunner.kt:157`의 `lastAttendanceDate = null`을 회원별 "마지막 `ATTENDED`
  수업일" 벌크 조회로 교체 — 기준일 5종 max 완결. calculator는 수정 불필요(이미 5종 수용)
- cron 기본값은 **D-121(기본 꺼짐) 유지** — 코드로 되돌리지 않는다
- **운영 켜기 절차를 문서화**: ① 출석 배선 배포 확인 → ② 운영에서 수동 실행 1회 검증 →
  ③ `BATCH_INACTIVITY_SCHEDULER_ENABLED=true` 환경변수 명시.
  절차에 **정책 시행일 하한(기본 2026-09-01, D-119)** 명시 — 시행일 전 수동 실행은 차감 0건이 정상

### 5. 공지사항 (D-131, 재량 확정)
- 관리자 CRUD + 회원 목록·상세. **제목+본문만** (첨부·고정 없음, MVP)
- 목록은 기존 page/size(`PageResponse`) 재사용, 삭제는 **hard delete**
  (이력 요구 없음 — `PassTransaction` 같은 원장 성격이 아님)

### Claude's Discretion
- Attendance/Notice 스키마 상세(컬럼·인덱스 설계), API 경로·DTO 형태 — 기존 관례
  (add-migration·add-endpoint 스킬, conventions.md) 안에서 플래너 재량
- 출석 체크 API의 배치 처리 형태(타임별 일괄 저장 vs 개별 토글) — FE 사용성 기준 재량
- 공지 정렬(최신순 기본) 등 세부 조회 규칙

### 문서 정합 (06-01 정합 플랜에서 처리할 것)
- glossary: `EVENING_HALF_REFUND`, 출석 상태(`ATTENDED`/`ABSENT`), 활동 피드 용어 추가
- policies §4.2: 저녁반 출석 추가 = 차감 원자 결합·회비 우선·거부 규칙 반영 (§6은 반영 완료)
- error-codes.md: 출석 관련 신규 에러 코드(차감 불가 409 등) 등록

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 도메인 정책 (스펙의 원본)
- `docs/policies.md` §6 — 출석 체크: 참고용 데이터 원칙 + 이번 논의로 추가된 명단 소스·소급 규칙
- `docs/policies.md` §4.2·§4.2a — 저녁반 0.5회 차감·관리자 수동 가감 규칙
- `docs/policies.md` §4.3 — 미사용 차감 기준일 5종 (출석일 후보가 이 phase에서 채워진다)
- `docs/requirements.md` — ATTEND/NOTICE/NOTIF 요구사항 원문
- `docs/glossary.md` — `Attendance`·`Notice` 용어 이미 정의됨. 신규 용어는 추가 후 사용 (Rule 3)

### 설계 결정
- `docs/decisions.md` D-127~D-131 — 이번 논의로 기록된 Phase 6 결정 5건
- `docs/decisions.md` D-091 — 차감 대상 이용권 선택(만료 임박순·수업날 기준) — 저녁반 차감이 재사용
- `docs/decisions.md` D-097 — 알림 수신자는 관리자 고정, Phase 4는 스키마+레코드 생성까지
- `docs/decisions.md` D-105·D-106 — 기준일 5종·상태 기반 멱등 (소급 출석 불가역의 근거)
- `docs/decisions.md` D-116·D-119·D-121 — cron 킬 스위치·시행일 하한·기본 꺼짐 (켜기 절차의 대상)

### 이월 근거
- `.planning/phases/05-batch/05-VERIFICATION.md` — CR-03 원문 (출석일 후보 부재)
- `.planning/ROADMAP.md` Phase 5 배포 조건 블록 — "cron은 꺼 둔 채 배포, Phase 6에서 켠다"

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `notification/Notification.kt` — 엔티티 완성됨. `isRead`/`readAt`만 `var`(확인 처리용으로 설계됨),
  비정규화 표시 필드(memberName·classType·classDate·startTime) 보유 — 피드·목록 조회 시 조인 불필요
- `notification/NotificationType.kt` — 6종 타입 (종류 필터의 값 집합)
- `pass/TransactionReason.EVENING_HALF` — 이미 선언됨. `EVENING_HALF_REFUND`만 추가
- `reservation/ReservationPassPolicy` — D-091 차감 대상 선택 로직 (저녁반 차감이 재사용)
- `reservation/ReservationLedgerSupport` — 차감/복구 실행부 공유 컴포넌트 (D-021 보장 집중점)
- `common/PageResponse` + Specification 패턴(`ReservationSpecifications` 선례) — 피드·공지 목록
- `ClassSession` get-or-create 서비스 — 저녁반 출석도 EVENING 세션에 매달 수 있다
  (V7 시드에 EVENING 월~금 19:00·21:00 존재, `ClassType.EVENING(reservable=false)`)

### Established Patterns
- 부분 유니크 인덱스로 동시성 보장 (V6 예약 3종, V10 RUNNING 선례) — 회원×세션 출석 유니크
- 조건부 UPDATE 차감 + 이력 원자 기록 (Phase 3~5 전반) — 저녁반 0.5 차감도 동일 경로
- `@EntityGraph`로 Specification 페이지 조회 N+1 방지 (04-12 선례) — 알림·피드 목록
- 기능별 패키지 (D-018) — `attendance`·`notice` 패키지 신설 예상, 의존 방향은
  reservation 선례처럼 attendance→schedule·attendance→pass 두 갈래

### Integration Points
- `batch/InactivityBatchRunner.kt:157` — `lastAttendanceDate = null` 하드코딩 지점 (CR-03 배선 대상)
- `batch/InactivityDueDateCalculator.kt` — 후보 ① 필드·5종 max 이미 구현됨, 수정 불필요
- Flyway 다음 버전 V11부터 (V10까지 커밋됨)
- `docs/api/openapi.yaml` 재생성 (`./gradlew generateApiDocs`) — API 추가 청크마다

</code_context>

<specifics>
## Specific Ideas

- 소급 출석 ↔ 배치 차감의 불가역 규칙은 사용자가 명시적으로 policies §6 명문화를 요구했다 —
  "소급 출석이 이미 실행된 INACTIVITY 차감을 되돌리지 않는다 (기준일 반영은 다음 배치부터)"
- 저녁반 출석 추가 화면은 "회원 검색 → 추가" 흐름 — 검색은 기존 관리자 회원 검색(이름·전화번호)
  자산 재사용 가능
- cron 켜기 절차는 운영자가 따라 할 수 있는 체크리스트 형태로 문서화

</specifics>

<deferred>
## Deferred Ideas

- **회원용 알림** — 수신자 확장은 요구사항에 없음 (D-097 유지). 필요해지면 새 phase
- **알림 실시간 푸시(WebSocket/SSE)** — 30초 폴링으로 충족, M7 이후 재검토
- **공지 첨부·고정(핀)** — MVP 밖, 운영 피드백 후 판단
- **오래된 알림 보존 기간·아카이빙** — append-only 테이블 증가 대응은 운영 데이터가 쌓인 뒤 판단

</deferred>

---

*Phase: 6-운영*
*Context gathered: 2026-08-18*
