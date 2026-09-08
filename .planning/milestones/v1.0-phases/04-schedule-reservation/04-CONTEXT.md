# Phase 4: 시간표·예약 - Context

**Gathered:** 2026-08-07
**Status:** Ready for planning

<domain>
## Phase Boundary

회원이 주간 시간표를 보고 예약제 수업(`SESSION`)·1:1 레슨(`LESSON`)을 예약·취소·변경하면
이용권이 즉시 차감/복구되고, 동시 경쟁에서도 정원을 초과하지 않으며, 관리자가 시간표와
예약 전반을 운영한다.

- **SCHED-01~03**: 정기 시간표 정의·조회, 날짜별 수업 실체화·주간 오픈, 관리자 주간 스케줄 보드
- **RESV-01~09**: 예약 생성(즉시 차감)·잔여 부족 거부·취소/변경(즉시 복구)·본인 목록·동시성 보장·
  관리자 예약 조회·대리 취소/변경·휴강
- **NOTIF-01**: `Notification` 스키마 + 알림 레코드 생성

**이 phase가 만들지 않는 것:**
- 알림 조회·확인 처리·폴링 API·활동 피드 (NOTIF-02/03 — Phase 6)
- 출석 체크(`Attendance`) — Phase 6. 저녁반 0.5회 수동 차감(`EVENING_HALF`)도 Phase 6
- 2주 미사용 자동 차감(`INACTIVITY`)·유효기간 만료 배치 — Phase 5
- 공지사항 — Phase 6

</domain>

<decisions>
## Implementation Decisions

### 예약 이력 모델 (D-090 후보)
- **취소는 물리 삭제가 아니라 상태 전환**한다 — 예약 행을 남기고 취소 상태·시각·주체·경위를
  기록한다. Phase 3의 이용권 등록 취소(D-059)와 같은 방식이고, "누가 언제 어떤 수업을
  취소했는지"가 남아야 감사 가능성이 성립한다
- **취소한 타임의 재예약을 허용**한다 — 따라서 `(member, class_session)` 전체 유니크 제약은
  쓸 수 없다. **활성 예약만 대상으로 하는 부분 유니크 인덱스**로 중복을 막는다
- 회원 본인 예약 목록(RESV-05)에는 **취소된 예약을 숨긴다**. 취소 내역은 Phase 3이 이미 만든
  `GET /api/members/me/pass-transactions`에 `CANCEL_REFUND`로 남아 회원이 확인할 수 있다
- `docs/policies.md` §3의 "취소 = 예약 삭제" 문구는 **이번 논의에서 정정 완료**

### 차감 대상 이용권 선택 (D-091 후보)
- 회원이 같은 종류 이용권을 여러 장 보유하면 **유효기간 만료가 가장 임박한 장에서 차감**한다
  (`end_date` 오름차순, 동률이면 `id` 오름차순으로 결정적 순서 보장)
- **여러 장을 합산해 한 건을 예약할 수 없다** — 0.5회짜리 두 장으로 1회짜리 예약은 거부한다.
  policies §3의 "잔여 0.5회로는 1회짜리 예약 불가"를 **이용권 한 장 단위**로 적용한다.
  결과적으로 **예약 1건 ↔ 이용권 1장**이 항상 대응하고, 예약 행이 차감한 `pass_id`를 보유한다
- **유효기간 판정 기준일은 예약일이 아니라 수업날**이다 — 만료 직전에 다음 달 수업을 전부 잡아
  유효기간을 사실상 연장하는 우회로를 막는다. `Pass.isExpired`가 이미 종료일 포함 판정(D-066)이므로
  `today` 자리에 수업 날짜를 넣으면 된다
- 예약 가능 이용권은 종류가 고정된다 — `SESSION` 예약은 `SESSION_PASS`만, `LESSON` 예약은
  `LESSON_PASS`만. 교차 사용 없음

### 취소 시 복구 대상 (D-091 후보)
- 복구는 **원래 차감한 그 이용권**으로 되돌린다 (예약 행이 `pass_id`를 갖고 있으므로 추적 가능)
- 그 이용권이 **유효기간 만료 상태여도 복구한다** — 빌려간 것을 돌려놓는 것이고, "잔여 = 이력 합계"
  원장 불변식이 유지돼야 한다. 만료된 이용권에 가감이 가능하다는 policies §4.2a와도 일관된다
- 그 이용권이 **등록 취소(`CANCELED`)됐으면 복구하지 않는다** — 이미 `REGISTRATION_CANCELED`로
  잔여를 0으로 상쇄했으므로 복구하면 D-059의 취소 의미가 깨진다. 예약만 취소 상태로 전환한다
  - **주의**: 이 경로는 아래 "등록 취소 선행 조건"으로 정상 운영에서는 발생하지 않아야 한다.
    그래도 방어적으로 처리한다(휴강 일괄 취소 등 다른 경로가 있으므로)

### 등록 취소(PASS-08)와 예약의 관계 (D-089, policies §1 반영 완료)
- 대상 이용권으로 잡힌 **활성 예약이 하나라도 있으면 등록 취소를 거부**한다
- 관리자가 예약을 먼저 정리(대리 취소, **복구 안 함** 선택)한 뒤 등록을 취소한다
- **자동 연쇄 취소는 만들지 않는다** — 오등록 정정은 드문 운영 행위이고, 연쇄 복구가 곧바로
  `REGISTRATION_CANCELED` 상쇄와 충돌한다. 명시적 2단계 절차가 안전하다
- Phase 3의 `AdminPassService` 취소 경로에 이 선행 검사를 추가해야 한다 (기존 코드 수정 지점)

### 예약 변경 (D-090 후보)
- **전용 엔드포인트 1개**로 열고 **하나의 트랜잭션**에서 처리한다. FE가 취소 API + 예약 API를
  두 번 부르게 하면 그 사이에 자리를 뺏겼을 때 회원이 양쪽 다 잃는다
- 새 타임 예약이 실패하면(정원 초과·잔여 부족 등) **기존 예약을 그대로 유지**하고 실패 응답을 준다
- 이력은 `CANCEL_REFUND`(+1)와 `RESERVE`(−1) **2건 모두 남긴다** — 차감 경로가 항상 동일해
  코드가 단순하고, 변경 내역이 원장에 그대로 보인다
- **같은 수업 종류 안에서만** 변경할 수 있다 (`SESSION`↔`SESSION`, `LESSON`↔`LESSON`).
  종류가 바뀌면 차감하는 이용권 종류까지 바뀌어 사실상 다른 예약이다
- 회원 변경은 policies §3의 당일 제약을 그대로 받는다 (당일 예약의 변경 불가)

### 중복 예약 규칙 (D-092 후보)
- **같은 회원이 같은 날짜·같은 시각에 두 건을 예약할 수 없다** — 1:1과 예약제가 같은 타임에
  동시 진행되더라도(policies §2, 코치 2명) 한 사람이 동시에 두 수업을 들을 수는 없다.
  이 검사가 없으면 실수로 두 이용권에서 헛수로 차감된다
- **같은 날 서로 다른 타임을 여러 개 예약하는 것은 제한 없음** — 잔여 횟수와 정원이 이미 제약이다
- **1:1(`LESSON`) 한도는 "날짜 + 시각" 단위로 1명**이다. 같은 시각에 예약제 수업이 별도로
  10명을 받는 것과 무관하다 (1:1과 그룹수업은 같이 진행된다 — 사용자 확인)

### 시간표 정의 (D-093 후보)
- 정기 시간표(`ClassSchedule`)는 **Flyway 마이그레이션 시드로 고정**한다. 관리자 CRUD API는
  이번 phase에서 만들지 않는다 — 만들면 "이미 예약이 있는 타임의 시간표를 바꾸면?"이 따라오고
  MVP는 단일 지점이라 시간표가 거의 바뀌지 않는다
- **정원(capacity)은 정기 시간표에** 둔다 (요일+시각 단위). policies §3의 "지점/수업별 설정 가능"을
  충족하면서 날짜별로 관리할 값이 늘지 않는다. 예약제 기본 10, 1:1은 1
- **저녁반(`EVENING`)도 `ClassSchedule` 행으로 정의한다** — 예약 대상은 아니지만 시간표 조회에
  노출되어야 하고, Phase 6의 출석 체크가 저녁반도 대상으로 삼는다

**시드에 들어갈 타임 (policies §2 확정, 사용자 확인 완료):**

| 요일 | `EVENING` | `SESSION` | `LESSON` |
|---|---|---|---|
| 월·수 | 19:00, 21:00 | — | 19:00, 21:00 |
| 화·목·금 | 19:00, 21:00 | 11:00, 13:00 | 11:00, 13:00, 19:00, 21:00 |
| 토·일 | — | 09:00, 11:00, 13:00, 15:00, 17:00 | 09:00, 11:00, 13:00, 15:00, 17:00 |

모든 타임은 90분. **1:1은 주 26타임 전부**(저녁반·예약제가 열리는 모든 타임)에 예약 가능하다.

### 날짜별 수업(ClassSession) 실체화 (D-094 후보)
- **필요할 때 생성한다** — 그 수업에 첫 예약이 들어오거나 관리자가 휴강 처리할 때 행을 만든다.
  예약이 없는 수업은 행이 없고, 시간표에서 계산해 "0/10"으로 표시한다
- 근거 두 가지: ① 주 단위 사전 생성은 배치 스케줄러를 요구하는데 배치 인프라는 Phase 5 소관이고,
  ② 배치가 하루라도 안 돌면 **예약 자체가 막힌다**. 필요할 때 생성하는 방식은 그 고장이 없다
- `(class_schedule_id, class_date)` **유니크 제약**을 걸어 동시에 두 요청이 같은 세션을 만들려 할 때
  하나만 성공하게 한다 (경쟁에서 진 쪽은 재조회)
- **`ClassSession`은 시작·종료 시각을 정기 시간표에서 복사해 자기 컬럼으로 보유한다.**
  지금은 항상 시간표와 같은 값이지만, 나중에 "공휴일에 그날만 시간 옮기기"를 붙일 때
  마이그레이션 없이 그 값만 바꾸면 된다. 시간표를 참조만 하면 시간표 수정이 과거 수업까지
  소급 변경해 지난 기록이 틀어진다

### 예약 창(오픈·마감·조회 범위) (D-095 후보)
- **오픈**: 해당 주 **월요일 00:00 (`Asia/Seoul`)** 부터 그 주(월~일) 전체가 열린다.
  오픈 시각을 따로 두지 않는다 — 오프라인 결제 기반이라 오픈런이 생길 이유가 없고,
  회원에게 안내할 것이 "월요일부터" 한 줄로 끝난다
- **마감**: 수업 **시작 시각 전까지** 예약 가능 (policies §3). 오늘 13:00 수업은 12:59까지.
  이미 시작·종료된 수업에 예약이 붙어 횟수가 사라지는 사고를 막는다
- **조회 범위**: **이번 주 + 다음 주 2주치**만 내려준다. 다음 주는 조회만 가능하고 예약 불가
  (ROADMAP 성공기준 1). FE 달력은 이 2주 범위 안에서 동작한다
- 주의 시작은 **월요일** (CLAUDE.md 기술 규칙). 모든 시각 판정은 주입된 `Clock` 빈 기준

### 회원 시간표 응답 (D-096 후보)
- 저녁반도 **함께 노출**하되 예약 대상이 아님을 응답에 표현한다 (FE가 예약 버튼을 감춘다)
- 셀에는 **예약 인원 숫자만**(예: `3/10`) 내려준다. **예약자 명단은 회원에게 주지 않는다** —
  회원 간 개인정보 노출이고, 명단은 관리자 스케줄 보드에서만 본다

### 관리자 예약 조회·대리 조작 (RESV-07·08)
- **예약 조회 필터**: 기간(날짜 범위) · 수업 종류 · 회원 검색어 + `page`/`size` 페이지네이션.
  Phase 2·3의 목록 API와 동일 형태 — `PageResponse` DTO 재사용, `@ParameterObject` 규칙(D-054) 준수
- **대리 취소의 "복구 안 함" 옵션은 유지한다.** 대표 사용처가 실재한다 — 회원이 당일 불참을
  연락해온 경우. 회원은 당일 취소가 불가하므로 관리자가 대신 취소하되, 정책상 차감은 유지해야 한다
  (policies §3 노쇼 규칙과 같은 결론). 기본값은 복구
- **관리자의 정원 무시(오버부킹)는 만들지 않는다.** "초과 예약 0건"이 이 시스템의 Core Value인데
  거기에 백도어를 두지 않는다. 정원 10이 실수요 대비 여유가 있어 실익도 없다
- 관리자는 당일 포함 제약 없이 취소·변경할 수 있다 (policies §3)

### 휴강 (RESV-09)
- 휴강 처리 시: 해당 수업의 활성 예약 **전부 자동 취소 + 차감 복구(`CLASS_CANCELED_REFUND`) +
  알림 생성**, 그 타임은 예약 불가로 표시 (policies §7)
- **휴강 철회는 "휴강 해제"까지만 지원한다** — 그 타임이 다시 예약 가능해지고, 취소된 예약은
  회원이 직접 다시 잡는다. **예약을 자동 복원하지 않는다**: 복원 시점에 그 사이 횟수를 다른 데
  써버린 회원은 잔여가 음수가 되고, 이는 Core Value("잔여 = 실제 사용 가능 횟수")를 정면으로 깬다

### 알림(Notification) — NOTIF-01 범위 (D-097 후보)
- 알림 레코드를 만드는 이벤트 **4종**: ① 회원 예약 ② 회원 취소·변경 ③ 휴강 처리
  ④ 관리자 대리 취소·변경
- **수신자는 관리자다** (glossary: "관리자 인앱 알림"). **회원용 알림은 만들지 않는다** —
  요구사항(NOTIF-01~03)에 회원 알림 조회 경로가 어느 phase에도 없고, MVP는 푸시 제외(requirements §5).
  휴강 통지는 관리자가 공지사항(Phase 6)이나 오프라인으로 한다
- 이 phase는 **스키마 + 레코드 생성까지만**. 조회·확인 처리·미확인 카운트·폴링 API는 Phase 6
- **Phase 6이 그대로 조회할 스키마**이므로, 알림 내용은 조회 시점 조인 없이 표시 가능하도록
  이벤트 발생 시점의 표시 정보(회원명·수업 일시·수업 종류 등)를 **비정규화해 담는 것을 권장**한다 —
  회원명이 나중에 바뀌어도 "그때 그 알림"이 그대로 남는 것이 알림·활동 피드의 의미에 맞다
- **휴강 알림은 세션당 1건 요약형**이다 (사용자 확정, 2026-08-07 플랜 단계) — 정원 10명 수업을
  휴강하면 취소 예약 건별 10건이 아니라 "○○ 수업 08/10 11:00 휴강 — N건 자동 취소" 1건을 만든다.
  건별 N건은 관리자 알림함을 폭증시키고 Phase 6의 미확인 카운트를 왜곡한다. 이를 표현하기 위해
  `Notification`은 `reservation_id` 단일 FK 외에 **`class_session_id`(nullable) 참조**를 갖는다

### 동시성 검증 방법 (D-098 후보, 사용자 확정 2026-08-07)
- **"초과 예약 0건" 증명은 JVM `ExecutorService`+`CountDownLatch` 동시성 통합테스트로 한다** —
  기존 규약(conventions §10.4, `add-domain-test` 스킬)과 동일한 방식이며, 이것으로 policies §8을
  충족한 것으로 본다. 서비스 계층에서 "정확히 정원 수만큼만 성공"을 증명하는 정확성 테스트가 목적
- **k6 부하테스트는 이번 phase 범위에서 제외**한다 — k6는 HTTP 레벨 부하·처리량 도구로 목적이
  다르고, 스크립트·실행 환경 등 새 인프라가 필요해 phase 규모를 키운다
- `docs/policies.md` §8의 "k6 부하테스트로 검증한다" 문구는 **동시성 통합테스트 기준으로 정정**한다
  (아래 open_items의 문서 정합에 포함)

### Claude's Discretion
논의 중 사용자가 명시적으로 위임했거나, 구현 세부라 묻지 않은 것들:

- **동시성 보장 구현 방식** — D-021(DB 제약 + 조건부 갱신 우선, 부족한 곳만 비관적 락)을 따른다.
  정원은 세션 행의 예약 카운트에 대한 **조건부 UPDATE**(`reserved_count + 1 <= capacity`), 1:1은
  **부분 유니크 인덱스**가 1차 방어선. `PassRepository.adjustRemainingCount`(잔여 음수 거부 조건부
  UPDATE)를 그대로 재사용한다. 낙관적 락(`@Version`)은 쓰지 않는다 — 실패를 재시도로 흡수해야 해
  코드가 복잡해지고, 이미 조건부 UPDATE로 같은 보장을 얻는다
- **`PassTransaction`에 회원 주체 컬럼 추가** — 현재 `admin_id NOT NULL`이라 회원이 주체인
  `RESERVE`/`CANCEL_REFUND`를 기록할 수 없다. 새 마이그레이션으로 `member_id`(nullable)를 붙이고
  기존 `admin_id`를 nullable로 완화 + "주체 중 정확히 하나" CHECK를 검토한다 (ROADMAP D-030 노트가
  예고한 작업). **커밋된 V4는 절대 수정하지 않는다 — 새 버전을 추가한다**
- **예약↔이용권 연결 방식** — 예약 행이 차감한 `pass_id`를 보유(복구 대상 추적용)
- 새 enum·에러코드 구성, 패키지 구조(`schedule`/`reservation`/`notification` 분리 여부),
  DTO 형태, 인덱스 설계
- 세션 미생성 타임의 "0/10" 계산을 조회 쿼리에서 어떻게 합칠지
- **차감 대상 이용권이 선택 직후 경쟁에서 소진되면(조건부 UPDATE 0행) 재시도 없이 즉시 실패**시키고
  회원이 재요청하게 한다 — 같은 회원이 자기 이용권을 동시에 두 곳에서 쓰는 경우뿐이라 빈도가
  극히 낮고, 재시도 로직의 복잡성 대비 실익이 없다 (RESEARCH.md Open Question #3 재량 처리)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 도메인·스펙 (CLAUDE.md 문서 우선순위 준수: policies > requirements > glossary·decisions·conventions)
- `docs/policies.md` §1(등록 취소 선행 조건 — **이번 논의로 신설**), §2(시간표 — 시드 원본),
  §3(예약 규칙 — **이번 논의로 취소=상태전환·변경 원자성 정정**), §4.1(즉시 차감·이력 원칙),
  §7(휴강), §8(동시성 요구) — **최종 기준**
- `docs/requirements.md` §3.3(회원 예약), §4.3(관리자 예약 관리), §4.4(수업 운영·휴강),
  §4.5(관리자 대시보드 — 주간 스케줄 보드·알림센터), §5(비기능: 초과 예약 0건, 감사 가능성)
- `docs/glossary.md` — `ClassSchedule`/`ClassSession`/`Reservation`/`Notification`,
  `ClassType`(EVENING/SESSION/LESSON), `TransactionReason` 8종 중 이 phase가 쓰는
  `RESERVE`/`CANCEL_REFUND`/`CLASS_CANCELED_REFUND`, **금지어**(Ticket/Voucher/Booking/Session 단독)
- `docs/decisions.md` — D-016(BigDecimal·compareTo), D-017(ProblemDetail), D-018(기능별 패키지),
  D-019(엔티티 노출 금지), D-020(트랜잭션 경계), D-021(동시성 — DB 제약+조건부 갱신 우선),
  D-028(에러코드 레지스트리), D-030(PassTransaction 주체 확장 예고), D-035(page/size),
  D-054(@ParameterObject), D-059(이용권 등록 취소), D-064(표시 상태 계산), D-066(종료일 포함),
  D-072(조건부 UPDATE로 상태 전환), D-084(청크 단위 납품)
- `docs/conventions.md` — §1(패키지), §3(엔티티), §5(시간 타입: `LocalDate`/`LocalTime`/`OffsetDateTime`),
  §8(에러), §9(Flyway), **§10.0(변경유형별 테스트 표 — 이 프로젝트가 GSD 기본 TDD 방침을 덮어씀)**, §11(Boot 4)
- `docs/error-codes.md` — 신규 에러코드(정원 초과, 당일 취소 불가, 예약 창 밖, 중복 예약,
  잔여 부족, 휴강된 수업 등) 추가 시 같은 PR에서 갱신

### 프로젝트 실행 상태
- `.planning/REQUIREMENTS.md` — SCHED-01~03, RESV-01~09, NOTIF-01 정의
- `.planning/ROADMAP.md` — Phase 4 성공 기준 5항목, Phase 5·6와의 경계 노트
- `.planning/phases/03-pass/03-CONTEXT.md` — Pass 도메인 결정(D-055~D-059)의 근거

### 프로젝트 스킬 (해당 작업 시 필수 절차)
- `.claude/skills/deliver-phase-chunk/SKILL.md` — **D-084: phase를 리뷰 가능한 청크로 나눠
  브랜치·커밋·PR까지. Phase 4는 규모가 크므로 청크 분할이 필수다**
- `.claude/skills/add-migration/SKILL.md` — V6+ 시간표·예약·알림 스키마 + `pass_transaction` 주체 확장
- `.claude/skills/add-endpoint/SKILL.md` — API 추가·`openapi.yaml` 재생성 절차
- `.claude/skills/add-domain-test/SKILL.md` — 예약 검증·차감 정책 단위테스트, **동시성 테스트 골격**
- `.claude/skills/verify-boot4-api/SKILL.md` — 낯선 API·의존성 Boot 4 검증 절차
- `.claude/skills/create-pr/SKILL.md` — PR 본문에 백엔드 학습 노트 포함

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`pass/PassRepository.adjustRemainingCount`** — 잔여를 원자적으로 가감하는 조건부 UPDATE.
  `status = ACTIVE`와 `remainingCount + :amount >= 0`을 DB가 검사하고, 반환 행 수 0이면 경쟁에서
  졌거나 상태가 바뀐 것. **Phase 4 예약 차감/복구가 그대로 재사용하도록 만들어진 경로다**(KDoc 명시).
  호출 전 필요한 스칼라 값을 미리 꺼내둘 것 — `clearAutomatically`로 엔티티가 준영속이 된다
- **`pass/Pass.kt`** — `displayStatus(today)`·`isExpired`(종료일 포함, D-066)를 예약 가능 이용권
  판정에 재사용. 단, `today` 자리에 **수업 날짜**를 넣는다
- **`pass/PassTransaction.kt`** — append-only 원장(전 필드 `val`). `admin_id NOT NULL`이라
  회원 주체 기록을 위해 스키마 확장 필요
- **`member/dto/PageResponse.kt`** — 관리자 예약 목록·회원 예약 목록 페이지네이션에 재사용
- **`common/error/`** (`ErrorCode`·`DomainException`·`GlobalExceptionHandler`) — 예약 에러도 이 체계
- **`member/MemberStateGate.kt`** — 예약 API는 `ACTIVE` 회원만 허용해야 하므로 여기서 검사
  (SecurityConfig는 역할만 본다 — D-040)
- **`auth/AuthenticationPrincipalResolver`** — 본인 예약 API의 주체 해석
- **`member/MemberSpecifications.kt` + `pass/PassTransactionSpecifications.kt`** — 관리자 예약 검색
  필터를 같은 Specification 패턴으로 구성
- **`config/ClockConfig.kt`** — 예약 창·당일 판정·마감 판정이 전부 `Clock` 주입 기준. 테스트 시각 고정
- **Testcontainers 배선 + `FlywayMigrationIntegrationTest`** — 새 마이그레이션 자동 검증
- **`generateApiDocs` 태스크**(D-029) — API 추가 후 `openapi.yaml` 재생성

### Established Patterns
- **조건부 UPDATE(compare-and-swap)로 상태 전환**(D-072) — `cancelIfNotCanceled`,
  `changePeriodIfUnchanged`, `RefreshTokenRepository.revokeIfUsable`가 같은 관례.
  "도메인 메서드는 판정·계산만, 실제 반영은 조건부 UPDATE가" 라는 역할 분리가 KDoc에 명문화돼 있다 —
  예약 정원 차지도 같은 형태로 간다
- **DB CHECK 제약으로 불변식 표현** — `ck_pass_remaining_count_by_type`, `ck_pass_cancellation`,
  `ck_pass_transaction_amount_nonzero`. 예약·세션 스키마도 같은 방식
- 마이그레이션 관례: 서로게이트 PK `id`, `TIMESTAMPTZ`, 헤더 주석에 근거 결정 번호 나열
- 서비스 `@Transactional(readOnly = true)` 기본, 변경 메서드만 오버라이드 (D-020)
- ktlint → build 순서

### Integration Points
- **새 패키지**: `schedule/`(ClassSchedule·ClassSession), `reservation/`(Reservation),
  `notification/`(Notification) — 기능별 패키지(D-018). 컨트롤러는 DTO만(D-019)
- **`SecurityConfig`는 손댈 필요 없다** — `/api/admin/**`(ADMIN)·`/api/members/**`(MEMBER) 규칙이
  이미 있고 `anyRequest().authenticated()`가 default-deny다. 새 엔드포인트를 이 경로 아래 두면 된다
- **Phase 3 코드 수정 지점**: `pass/AdminPassService`의 등록 취소 경로에 **활성 예약 선행 검사**를
  추가해야 한다 (D-089). `reservation` 패키지를 참조하게 되므로 의존 방향 주의
- **`pass_transaction` 스키마 확장** — 회원 주체 컬럼 추가 (V6+ 새 마이그레이션)
- Phase 5(배치)가 이 phase의 "마지막 예약의 수업일"을 2주 미사용 기준일 조회에 쓴다 —
  예약 조회 경로를 그때 재사용할 수 있게 설계
- Phase 6(운영)이 `ClassSession`을 출석 체크 대상으로, `Notification`을 조회 대상으로 쓴다

</code_context>

<specifics>
## Specific Ideas

- 사용자가 "달력 화면에 시간표대로 수업이 뜨고, 칸을 눌러 예약하거나 몇 명 찼는지 본다"로
  화면 모델을 명확히 제시했다. `ClassSession` 실체화 시점은 그 화면과 무관한 내부 결정임을
  확인한 뒤 Claude 재량으로 위임했다
- 공휴일 시간 조정(예: 저녁반 19/21시 → 17시 한 타임)은 **저녁반이 예약 대상이 아니라서
  예약 데이터에 영향이 없다**는 점을 확인하고, 이번 phase는 휴강+공지로 대응하기로 했다.
  대신 `ClassSession`에 시각을 복사해 두어 나중에 "그날만 시간 옮기기"를 마이그레이션 없이
  붙일 수 있게 여지를 확보했다
- 1:1과 그룹수업이 같은 시각에 동시 진행되는 것은 그대로 허용하되, **한 회원이** 같은 시각에
  둘 다 예약하는 것만 막는다는 점을 사용자가 명시적으로 구분해 확인했다
- 대리 취소의 "복구 안 함"은 추상적 옵션이 아니라 **당일 불참 연락** 이라는 구체적 사용처가 있다
- 관리자 오버부킹 백도어를 두지 않는 이유를 사용자가 Core Value로 직접 근거를 댔다

</specifics>

<deferred>
## Deferred Ideas

- **날짜별 시간 조정**("그날만 수업 시각 옮기기") — 이번엔 만들지 않고 휴강+공지로 대응.
  `ClassSession`이 시각을 자기 컬럼으로 갖게 해 스키마 변경 없이 나중에 열 수 있다
- **일회성 임시 수업 추가**(정기 시간표에 없는 타임을 그날만 개설) — 예약·정원·휴강 경로가
  정기/임시 두 갈래가 돼 phase가 커진다. 필요성이 실제로 확인되면 별도 phase
- **관리자 시간표 CRUD API** — MVP는 시드 고정. 시간표가 자주 바뀌는 운영이 되면 별도 phase
  ("이미 예약이 있는 타임의 시간표 변경" 문제를 그때 함께 다룬다)
- **정원 초과 대기(waitlist)** — requirements §6에서 이미 MVP 제외
- **회원용 알림** — 요구사항에 조회 경로가 없다. 웹 푸시(PWA+FCM)와 함께 v2 후보
- **휴강 철회 시 예약 자동 복원** — 잔여 음수 위험 때문에 기각. 필요해지면 "복원 가능한 것만
  복원하고 나머지는 안내" 같은 설계를 별도로 논의
- **관리자 오버부킹** — Core Value 위배로 기각. 재논의 시 별도 결정 필요

</deferred>

<open_items>
## 계획 단계에서 처리할 문서 정합 (Phase 3의 03-01-PLAN.md 관례)

이번 논의로 확정됐지만 아직 `docs/`에 반영되지 않은 것들 — **첫 플랜에서 함께 처리한다**:

- `docs/decisions.md` — D-089(등록 취소 선행 조건, policies는 반영 완료)부터
  D-090~D-098(예약 이력 모델, 차감 대상 선택, 중복 예약, 시간표 정의, 세션 실체화,
  예약 창, 회원 시간표 응답, 알림 범위·휴강 알림 세션당 1건, 동시성 검증 방법) 기록.
  **번호는 D-084 다음부터**
- `docs/glossary.md` — 새 개념 등록 후 사용: 예약 상태(`ReservationStatus`), 휴강 표현,
  알림 종류(`NotificationType` 등), 취소 주체 구분. **금지어 확인 필수**(`Booking` 금지,
  `Session` 단독 금지 — `ClassSession`만)
- `docs/error-codes.md` — 신규 에러코드 등록
- `docs/policies.md` — §2 시간표에 1:1 예약 가능 타임이 "저녁반·예약제가 열리는 모든 타임"임을
  명시(현재 "운영 중인 모든 타임슬롯"은 해석 여지가 있다), §3에 차감 대상 이용권 선택 규칙·
  유효기간 수업날 기준 판정·중복 예약 금지·예약 창 경계 추가, **§8의 "k6 부하테스트로 검증"
  문구를 "동시성 통합테스트(ExecutorService)로 검증"으로 정정** (D-098, 사용자 확정)

</open_items>

---

*Phase: 4-시간표·예약*
*Context gathered: 2026-08-07*
