# Phase 4: 시간표·예약 - Research

**Researched:** 2026-08-07
**Domain:** Kotlin + Spring Boot 4.1 + JPA(Hibernate 7.4) + PostgreSQL 18 — 동시성이 걸린 예약 도메인 (정원 제어, 즉시 차감/복구, 감사 이력)
**Confidence:** HIGH (기존 코드 관례·D-021/D-072 선례에 강하게 근거) / 알림 스키마 세부는 MEDIUM (요구사항이 스키마 grain을 명시하지 않음)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### 예약 이력 모델 (D-090 후보)
- **취소는 물리 삭제가 아니라 상태 전환**한다 — 예약 행을 남기고 취소 상태·시각·주체·경위를
  기록한다. Phase 3의 이용권 등록 취소(D-059)와 같은 방식이고, "누가 언제 어떤 수업을
  취소했는지"가 남아야 감사 가능성이 성립한다
- **취소한 타임의 재예약을 허용**한다 — 따라서 `(member, class_session)` 전체 유니크 제약은
  쓸 수 없다. **활성 예약만 대상으로 하는 부분 유니크 인덱스**로 중복을 막는다
- 회원 본인 예약 목록(RESV-05)에는 **취소된 예약을 숨긴다**. 취소 내역은 Phase 3이 이미 만든
  `GET /api/members/me/pass-transactions`에 `CANCEL_REFUND`로 남아 회원이 확인할 수 있다
- `docs/policies.md` §3의 "취소 = 예약 삭제" 문구는 **이번 논의에서 정정 완료**

#### 차감 대상 이용권 선택 (D-091 후보)
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

#### 취소 시 복구 대상 (D-091 후보)
- 복구는 **원래 차감한 그 이용권**으로 되돌린다 (예약 행이 `pass_id`를 갖고 있으므로 추적 가능)
- 그 이용권이 **유효기간 만료 상태여도 복구한다** — 빌려간 것을 돌려놓는 것이고, "잔여 = 이력 합계"
  원장 불변식이 유지돼야 한다. 만료된 이용권에 가감이 가능하다는 policies §4.2a와도 일관된다
- 그 이용권이 **등록 취소(`CANCELED`)됐으면 복구하지 않는다** — 이미 `REGISTRATION_CANCELED`로
  잔여를 0으로 상쇄했으므로 복구하면 D-059의 취소 의미가 깨진다. 예약만 취소 상태로 전환한다
  - **주의**: 이 경로는 아래 "등록 취소 선행 조건"으로 정상 운영에서는 발생하지 않아야 한다.
    그래도 방어적으로 처리한다(휴강 일괄 취소 등 다른 경로가 있으므로)

#### 등록 취소(PASS-08)와 예약의 관계 (D-089, policies §1 반영 완료)
- 대상 이용권으로 잡힌 **활성 예약이 하나라도 있으면 등록 취소를 거부**한다
- 관리자가 예약을 먼저 정리(대리 취소, **복구 안 함** 선택)한 뒤 등록을 취소한다
- **자동 연쇄 취소는 만들지 않는다** — 오등록 정정은 드문 운영 행위이고, 연쇄 복구가 곧바로
  `REGISTRATION_CANCELED` 상쇄와 충돌한다. 명시적 2단계 절차가 안전하다
- Phase 3의 `AdminPassService` 취소 경로에 이 선행 검사를 추가해야 한다 (기존 코드 수정 지점)

#### 예약 변경 (D-090 후보)
- **전용 엔드포인트 1개**로 열고 **하나의 트랜잭션**에서 처리한다. FE가 취소 API + 예약 API를
  두 번 부르게 하면 그 사이에 자리를 뺏겼을 때 회원이 양쪽 다 잃는다
- 새 타임 예약이 실패하면(정원 초과·잔여 부족 등) **기존 예약을 그대로 유지**하고 실패 응답을 준다
- 이력은 `CANCEL_REFUND`(+1)와 `RESERVE`(−1) **2건 모두 남긴다** — 차감 경로가 항상 동일해
  코드가 단순하고, 변경 내역이 원장에 그대로 보인다
- **같은 수업 종류 안에서만** 변경할 수 있다 (`SESSION`↔`SESSION`, `LESSON`↔`LESSON`).
  종류가 바뀌면 차감하는 이용권 종류까지 바뀌어 사실상 다른 예약이다
- 회원 변경은 policies §3의 당일 제약을 그대로 받는다 (당일 예약의 변경 불가)

#### 중복 예약 규칙 (D-092 후보)
- **같은 회원이 같은 날짜·같은 시각에 두 건을 예약할 수 없다** — 1:1과 예약제가 같은 타임에
  동시 진행되더라도(policies §2, 코치 2명) 한 사람이 동시에 두 수업을 들을 수는 없다.
  이 검사가 없으면 실수로 두 이용권에서 헛수로 차감된다
- **같은 날 서로 다른 타임을 여러 개 예약하는 것은 제한 없음** — 잔여 횟수와 정원이 이미 제약이다
- **1:1(`LESSON`) 한도는 "날짜 + 시각" 단위로 1명**이다. 같은 시각에 예약제 수업이 별도로
  10명을 받는 것과 무관하다 (1:1과 그룹수업은 같이 진행된다 — 사용자 확인)

#### 시간표 정의 (D-093 후보)
- 정기 시간표(`ClassSchedule`)는 **Flyway 마이그레이션 시드로 고정**한다. 관리자 CRUD API는
  이번 phase에서 만들지 않는다
- **정원(capacity)은 정기 시간표에** 둔다 (요일+시각 단위). 예약제 기본 10, 1:1은 1
- **저녁반(`EVENING`)도 `ClassSchedule` 행으로 정의한다** — 예약 대상은 아니지만 시간표 조회에
  노출되어야 하고, Phase 6의 출석 체크가 저녁반도 대상으로 삼는다

**시드에 들어갈 타임 (policies §2 확정, 사용자 확인 완료):**

| 요일 | `EVENING` | `SESSION` | `LESSON` |
|---|---|---|---|
| 월·수 | 19:00, 21:00 | — | 19:00, 21:00 |
| 화·목·금 | 19:00, 21:00 | 11:00, 13:00 | 11:00, 13:00, 19:00, 21:00 |
| 토·일 | — | 09:00, 11:00, 13:00, 15:00, 17:00 | 09:00, 11:00, 13:00, 15:00, 17:00 |

모든 타임은 90분. **1:1은 주 26타임 전부**(저녁반·예약제가 열리는 모든 타임)에 예약 가능하다.

#### 날짜별 수업(ClassSession) 실체화 (D-094 후보)
- **필요할 때 생성한다** — 첫 예약 또는 관리자 휴강 처리 시 행을 만든다. 예약 없는 수업은
  행이 없고 시간표에서 계산해 "0/10"으로 표시한다
- `(class_schedule_id, class_date)` **유니크 제약**으로 동시 생성 경쟁에서 하나만 성공
- **`ClassSession`은 시작·종료 시각을 정기 시간표에서 복사해 자기 컬럼으로 보유**한다 —
  나중에 "그날만 시간 옮기기"를 마이그레이션 없이 붙이기 위함. 시간표를 참조만 하면 시간표
  수정이 과거 수업까지 소급 변경한다

#### 예약 창(오픈·마감·조회 범위) (D-095 후보)
- **오픈**: 해당 주 **월요일 00:00 (`Asia/Seoul`)** 부터 그 주(월~일) 전체
- **마감**: 수업 **시작 시각 전까지**
- **조회 범위**: **이번 주 + 다음 주 2주치**만. 다음 주는 조회만 가능
- 주의 시작은 **월요일**. 모든 시각 판정은 주입된 `Clock` 빈 기준

#### 회원 시간표 응답 (D-096 후보)
- 저녁반도 **함께 노출**하되 예약 대상이 아님을 응답에 표현
- 셀에는 **예약 인원 숫자만**(예: `3/10`). **예약자 명단은 회원에게 주지 않는다**

#### 관리자 예약 조회·대리 조작 (RESV-07·08)
- **예약 조회 필터**: 기간(날짜 범위) · 수업 종류 · 회원 검색어 + `page`/`size`.
  `PageResponse` DTO 재사용, `@ParameterObject`(D-054) 준수
- **대리 취소의 "복구 안 함" 옵션은 유지한다** — 당일 불참 연락이 대표 사용처. 기본값은 복구
- **관리자의 정원 무시(오버부킹)는 만들지 않는다** — Core Value("초과 예약 0건")에 백도어 없음
- 관리자는 당일 포함 제약 없이 취소·변경할 수 있다

#### 휴강 (RESV-09)
- 휴강 처리 시: 해당 수업의 활성 예약 **전부 자동 취소 + 차감 복구(`CLASS_CANCELED_REFUND`) +
  알림 생성**, 그 타임은 예약 불가로 표시
- **휴강 철회는 "휴강 해제"까지만 지원한다** — 예약 자동 복원은 하지 않는다(잔여 음수 위험)

#### 알림(Notification) — NOTIF-01 범위 (D-097 후보)
- 알림 레코드를 만드는 이벤트 **4종**: ① 회원 예약 ② 회원 취소·변경 ③ 휴강 처리
  ④ 관리자 대리 취소·변경
- **수신자는 관리자다.** 회원용 알림은 만들지 않는다
- 이 phase는 **스키마 + 레코드 생성까지만**. 조회·확인 처리·미확인 카운트·폴링 API는 Phase 6
- 알림 내용은 조회 시점 조인 없이 표시 가능하도록 이벤트 발생 시점의 표시 정보(회원명·수업
  일시·수업 종류 등)를 **비정규화해 담는 것을 권장**

### Claude's Discretion
- **동시성 보장 구현 방식** — D-021(DB 제약 + 조건부 갱신 우선, 부족한 곳만 비관적 락)을 따른다.
  정원은 세션 행의 예약 카운트에 대한 **조건부 UPDATE**(`reserved_count + 1 <= capacity`), 1:1은
  **부분 유니크 인덱스**가 1차 방어선. `PassRepository.adjustRemainingCount`를 그대로 재사용한다.
  낙관적 락(`@Version`)은 쓰지 않는다
- **`PassTransaction`에 회원 주체 컬럼 추가** — `admin_id NOT NULL`을 완화하고 `member_id`
  (nullable)를 추가 + "주체 중 정확히 하나" CHECK를 검토한다. **커밋된 V4는 절대 수정하지 않는다**
- **예약↔이용권 연결 방식** — 예약 행이 차감한 `pass_id`를 보유(복구 대상 추적용)
- 새 enum·에러코드 구성, 패키지 구조(`schedule`/`reservation`/`notification` 분리 여부),
  DTO 형태, 인덱스 설계
- 세션 미생성 타임의 "0/10" 계산을 조회 쿼리에서 어떻게 합칠지

### Deferred Ideas (OUT OF SCOPE)
- **날짜별 시간 조정**("그날만 수업 시각 옮기기") — 스키마 여지만 확보, 이번엔 구현 안 함
- **일회성 임시 수업 추가**(정기 시간표에 없는 타임을 그날만 개설)
- **관리자 시간표 CRUD API** — MVP는 시드 고정
- **정원 초과 대기(waitlist)** — v2
- **회원용 알림** — v2 후보 (웹 푸시와 함께)
- **휴강 철회 시 예약 자동 복원** — Core Value 위배로 기각
- **관리자 오버부킹** — Core Value 위배로 기각
- **출석 체크(`Attendance`)·저녁반 0.5회 수동 차감(`EVENING_HALF`)** — Phase 6
- **2주 미사용 자동 차감(`INACTIVITY`)·유효기간 만료 배치** — Phase 5
- **공지사항** — Phase 6
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SCHED-01 | 주간 반복 시간표(`ClassSchedule`) 정의·조회 | Architecture Patterns "시간표 시드"·Code Examples "ClassSchedule DDL" — `ck_pass_remaining_count_by_type`식 타입별 CHECK 재사용 권장 |
| SCHED-02 | 날짜별 수업(`ClassSession`) 실체화, 해당 주 월요일 오픈 | Code Examples "get-or-create 세션(ON CONFLICT DO NOTHING)", Common Pitfalls "유니크 위반 catch의 함정" |
| SCHED-03 | 관리자 주간 스케줄 보드(요일×타임 그리드) | Architecture Patterns "조회 쿼리 설계"(세션 미생성 타임 0/10 계산), Don't Hand-Roll "PageResponse·Specification 재사용" |
| RESV-01 | SESSION 예약 시 `SESSION_PASS` 즉시 1회 차감 | Code Examples "예약 생성 트랜잭션 순서", Don't Hand-Roll "`adjustRemainingCount` 재사용" |
| RESV-02 | LESSON 예약 시 `LESSON_PASS` 즉시 1회 차감, 타임당 1명 | Code Examples "부분 유니크 인덱스", Common Pitfalls "정원=1 전용 방어선" |
| RESV-03 | 잔여 부족 시 예약 거부(0.5회로 1회 예약 불가) | Code Examples "이용권 후보 선정 쿼리" — `remainingCount >= 1.0` 단일 이용권 기준 |
| RESV-04 | 취소(즉시 복구)·변경(취소+재예약), 당일 불가 | Architecture Patterns "취소/변경 트랜잭션", Common Pitfalls "당일 판정은 LocalDate 비교" |
| RESV-05 | 본인 예약 목록 조회 | Don't Hand-Roll "Specification 패턴 재사용", 취소 예약 숨김(Locked Decisions) |
| RESV-06 | 정원·1:1 슬롯 동시성, 초과 예약 0건 | Architecture Patterns "정원 동시성 제어", Code Examples "조건부 UPDATE", Validation Architecture "동시성 테스트" |
| RESV-07 | 관리자 전체 예약 조회 | Don't Hand-Roll "PageResponse·`@ParameterObject`(D-054)" |
| RESV-08 | 관리자 대리 취소/변경(복구 여부 선택) | Architecture Patterns "취소 주체·복구 플래그 스키마" |
| RESV-09 | 휴강 처리(일괄 취소+복구+알림) | Architecture Patterns "휴강 캐스케이드", Common Pitfalls "`adjustRemainingCount` 0행 반환의 이중 의미" |
| NOTIF-01 | 예약/취소/변경/휴강 시 관리자 알림 레코드 생성 | Architecture Patterns "Notification 스키마 권장안", Open Questions "이벤트 단위(휴강 시 건별 vs 세션당 1건)" |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- 문서 우선순위: `docs/policies.md` > `docs/requirements.md` > `docs/glossary.md`·`docs/decisions.md`·`docs/conventions.md` > `.planning/**` > 코드. `.planning/`은 실행 상태이지 스펙이 아니다
- 코드 작성 전 `docs/conventions.md` 필독 — 패키지 구조·엔티티·트랜잭션·테스트 규약
- 네이밍은 `docs/glossary.md`만 사용. **금지어**: `Ticket`/`Voucher`/`Coupon`/`Booking`/`Course`, 인증 영역의 `Session` 단독(→ `ClassSession`과 혼동), `User`/`Account`/`Role` 단독
- API 응답은 springdoc `openapi.yaml` 재생성. 에러는 RFC 9457 `ProblemDetail` 고정(D-017), 커스텀 래퍼 금지
- 모든 차감/복구는 `PassTransaction` 이력 필수. 이력 없는 잔여 변경 금지
- 시간대 `Asia/Seoul` 명시, 주 시작은 월요일, 현재 시각은 `Clock` 빈으로만
- 모르는 Boot 4 API는 추측 금지 — context7 → maven-metadata.xml → `./gradlew compileKotlin` 순서
- 프로덕션 코드 추가·수정 시 같은 작업에서 테스트 동반 (`docs/conventions.md` §10.0 표 기준, GSD 기본 TDD 방침을 이 프로젝트 규칙이 덮어씀)
- 이 phase는 **청크 단위 납품(D-084)** 대상 — `.claude/skills/deliver-phase-chunk/SKILL.md`의 wave 경계 제약을 플랜 단계에서 반영해야 한다: "예약 생성"과 "정원 초과 방지(동시성)"는 반드시 같은 청크, 마이그레이션은 그 스키마를 쓰는 첫 코드와 같은 청크
- 커밋·브랜치: `dev`에서 작업, `feature/phase-04{a|b|c...}-{slug}` 브랜치가 청크 단위. phase 실행 중 청크 경계에서는 커밋·PR이 자동(D-084 예외) — 머지 버튼은 항상 사용자
- ktlint → build 순서로 마무리, 스타일은 `.editorconfig`만

## Summary

Phase 4는 이 프로젝트에서 가장 동시성이 무거운 phase다. 예약 생성이 "정원 조건부 UPDATE →
이용권 조건부 UPDATE(차감) → 예약 행 INSERT → PassTransaction INSERT → Notification INSERT"를
**하나의 트랜잭션**에서 원자적으로 묶어야 하고, 그 각 단계는 Phase 3이 이미 검증한 관례
(`adjustRemainingCount` 조건부 UPDATE, `cancelIfNotCanceled` 스타일 compare-and-swap)를 그대로
재사용하면 된다 — **이 phase에 새 외부 라이브러리는 필요 없다.** 새로 만드는 것은 3개
기능 패키지(`schedule`/`reservation`/`notification`)의 엔티티·리포지토리·서비스와, `pass_transaction`에
회원 주체 컬럼을 더하는 확장 마이그레이션이다.

기술적으로 가장 까다로운 지점은 두 가지다. 첫째, "필요할 때 생성"하는 `ClassSession`의
get-or-create 경쟁 처리 — 유니크 제약 위반을 애플리케이션에서 catch하는 방식은 Hibernate가
flush 시점 제약 위반 이후 영속성 컨텍스트를 신뢰할 수 없는 상태로 만들 위험이 있어(Pitfall 1),
`INSERT ... ON CONFLICT DO NOTHING` 네이티브 쿼리 + 별도 SELECT 조합을 권장한다. 둘째, 정원·1:1
슬롯·중복 예약 3종의 동시성 방어를 어떤 조합(조건부 UPDATE vs 부분 유니크 인덱스)으로 나눌지인데,
컨텍스트가 이미 "정원은 조건부 UPDATE, 1:1은 부분 유니크 인덱스가 1차 방어선"으로 확정했으므로
이 연구는 그 구현 방법(어떤 컬럼을 `Reservation`에 비정규화해야 부분 유니크 인덱스가 성립하는지)에
집중했다.

policies.md §8은 "k6 부하테스트로 검증"을 요구하지만, 이 저장소의 어떤 스킬·컨벤션·이전 phase도
k6를 도입한 적이 없다 — 기존 `add-domain-test` 스킬은 JVM `ExecutorService`+`CountDownLatch`
통합테스트만 규정한다. 이 간극은 Open Questions에 명시했다.

**Primary recommendation:** 새 패키지 3개(`schedule`/`reservation`/`notification`)를 만들고,
Phase 3의 조건부 UPDATE 관례(`PassRepository.adjustRemainingCount`, `cancelIfNotCanceled`)를
그대로 재사용해 예약 차감/복구를 구현한다. `ClassSession` get-or-create는 네이티브
`INSERT ... ON CONFLICT DO NOTHING`으로, 세 가지 동시성 불변식(정원·1:1·중복예약)은 `Reservation`에
`class_date`·`start_time`·`class_type`을 비정규화해 부분 유니크 인덱스 3개로 방어한다.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| 시간표 정의·조회 (SCHED-01) | API/Backend | Database/Storage | 정적 시드 데이터, 서비스가 읽기만 — 계산·가공 없음 |
| 세션 실체화 (SCHED-02) | Database/Storage | API/Backend | 동시성 방어의 핵심이 DB 유니크 제약이고, 서비스는 그 결과를 조립만 함 |
| 관리자 스케줄 보드 (SCHED-03) | API/Backend | Database/Storage | 여러 테이블 조인·집계(세션+예약+정원)가 서비스 계층 책임, 저장은 그대로 |
| 예약 생성/취소/변경 (RESV-01~05) | API/Backend | Database/Storage | 트랜잭션 조립(차감+예약+이력)은 서비스, 원자성 보장은 DB 조건부 UPDATE가 최종 방어선 |
| 정원·1:1 동시성 (RESV-06) | Database/Storage | — | "초과 예약 0건"은 애플리케이션 조건문으로 보장 불가(D-021) — DB 제약이 유일한 진실 |
| 관리자 조회·대리 조작 (RESV-07·08) | API/Backend | Database/Storage | 필터·페이지네이션은 서비스, 권한 검사는 Security 계층(기존 SecurityConfig 재사용) |
| 휴강 캐스케이드 (RESV-09) | API/Backend | Database/Storage | 일괄 취소·복구·알림 생성의 조립은 서비스, 각 단계의 원자성은 DB 조건부 갱신 |
| 알림 레코드 생성 (NOTIF-01) | API/Backend | Database/Storage | 이벤트 발생 시점 비정규화 스냅샷을 만드는 것은 서비스 책임, 저장은 append-only |
| FE 시간표/예약 UI | Browser / Client | — | 이 phase의 BE 범위 밖 — `openapi.yaml` 계약만 제공 |

## Standard Stack

### Core

이 phase는 **새 외부 의존성이 필요 없다.** 기존 스택(Spring Boot 4.1.0 / Hibernate ORM
7.4.1.Final / PostgreSQL 18 / Kotlin 2.3.21)만으로 요구사항을 전부 구현할 수 있다.
[VERIFIED: `build.gradle.kts`, `docker-compose.yml`, maven-metadata.xml 직접 조회]

| 구성 요소 | 버전 | 근거 |
|---|---|---|
| Spring Boot | 4.1.0 | `build.gradle.kts` 플러그인 선언 |
| Hibernate ORM | 7.4.1.Final | `spring-boot-dependencies-4.1.0.pom`의 `hibernate.version` 직접 조회 [VERIFIED: Maven Central] |
| PostgreSQL | 18.4-alpine (Docker 이미지) | `docker-compose.yml` |
| Kotlin | 2.3.21 | `build.gradle.kts` |
| Testcontainers | 2.x (`testcontainers-postgresql`) | 기존 `TestcontainersConfiguration` 재사용 |

### Supporting

이번 phase가 새로 도입하는 것은 라이브러리가 아니라 **네이티브 SQL 쿼리 패턴**(`@Query(nativeQuery = true)`)이다
— Spring Data JPA가 기본 제공하는 기능이라 별도 의존성이 필요 없다. 이 저장소에 `nativeQuery`
사용 전례는 아직 없다(grep 결과 0건) — Phase 4가 처음 도입하는 패턴임을 플랜에 명시할 것.

### Alternatives Considered

| 대상 | 대안 | Tradeoff |
|------|------|----------|
| `ClassSession` get-or-create | `save()` 후 `DataIntegrityViolationException` catch | Hibernate flush 예외 이후 영속성 컨텍스트가 오염될 위험(Pitfall 1) — 네이티브 `ON CONFLICT DO NOTHING`이 예외 자체를 없애 더 안전 |
| 정원 방어 | `SELECT ... FOR UPDATE` 비관적 락 | D-021이 이미 "부족한 곳에만" 락을 쓰기로 결정. 조건부 UPDATE 하나로 충분한 정원 카운터에 락까지 걸면 처리량만 떨어진다 |
| 정원 방어 | `@Version` 낙관적 락 | D-021이 이미 기각(재시도 로직 필요, 마지막 자리 경쟁에서 재시도 폭증) |
| k6 부하테스트(policies §8) | JVM `ExecutorService`+`CountDownLatch` 통합테스트만 | policies.md가 명시했지만 이 저장소에 k6 도입 전례가 없다 — Open Questions 참조 |

**Installation:** 해당 없음 (신규 의존성 없음)

## Package Legitimacy Audit

**해당 없음 — 이 phase는 신규 외부 패키지를 설치하지 않는다.** 기존 `build.gradle.kts` 의존성만
사용하며, 새로 쓰는 기능(네이티브 쿼리, 부분 유니크 인덱스)은 이미 설치된 Spring Data
JPA·PostgreSQL 드라이버의 표준 기능이다. slopcheck·레지스트리 검증 절차는 건너뛴다.

## Architecture Patterns

### System Architecture Diagram

```
[회원 클라이언트]                         [관리자 클라이언트]
       │ GET 시간표(2주)                         │ GET 스케줄보드 / 예약 목록
       │ POST 예약/취소/변경                      │ POST 대리취소/변경/휴강
       ▼                                          ▼
┌───────────────────────────────────────────────────────────────────┐
│                    Spring MVC Controller 계층                       │
│  MemberReservationController / AdminReservationController /        │
│  ScheduleController / AdminScheduleController                      │
│  (MemberStateGate.requireActive 통과 필요 — 회원 경로만)             │
└───────────────────────────────────────────────────────────────────┘
       │                                          │
       ▼                                          ▼
┌───────────────────────────────────────────────────────────────────┐
│                  Service 계층 (@Transactional 경계)                  │
│                                                                       │
│  ReservationService.reserve(command)                                │
│    1. ClassSession get-or-create (네이티브 INSERT ON CONFLICT)       │
│    2. 휴강 여부 검사 (session.status == CANCELED → 거부)              │
│    3. 예약 창 검사 (오픈 주 내부 + 시작 시각 전) — Clock 기준          │
│    4. 중복 예약 검사 (같은 회원·같은 날짜+시각 활성 예약 존재?)         │
│    5. 정원 조건부 UPDATE (reserved_count + 1 <= capacity)            │
│    6. 이용권 후보 선정 (만료 임박순, remainingCount >= 1)             │
│    7. 이용권 조건부 UPDATE 차감 (PassRepository.adjustRemainingCount) │
│    8. Reservation INSERT (status=ACTIVE, pass_id 보유)                │
│    9. PassTransaction INSERT (RESERVE, member 주체)                   │
│   10. Notification INSERT (회원 예약 이벤트)                          │
│       ── 4~10 중 하나라도 실패하면 트랜잭션 전체 롤백(2~5단계 포함) ──   │
│                                                                       │
│  ReservationService.cancel / .change / AdminReservationService.*    │
│  ScheduleService.getWeeklySchedule / AdminScheduleService.*         │
│  ClassSessionService.suspend(휴강) — 활성 예약 일괄 조회 후           │
│    각 예약에 cancel 경로 재사용(N+1 대신 배치 조회)                    │
└───────────────────────────────────────────────────────────────────┘
       │                                          │
       ▼                                          ▼
┌───────────────────────────────────────────────────────────────────┐
│                     Repository 계층 (조건부 UPDATE)                  │
│  ClassSessionRepository.insertIfAbsent / incrementReservedCount /   │
│  decrementReservedCount                                             │
│  ReservationRepository.cancelIfActive(compare-and-swap)             │
│  PassRepository.adjustRemainingCount (Phase 3 재사용, 변경 없음)      │
└───────────────────────────────────────────────────────────────────┘
       │
       ▼
┌───────────────────────────────────────────────────────────────────┐
│  PostgreSQL 18 — 최종 방어선                                         │
│  class_session: UNIQUE(class_schedule_id, class_date),              │
│    CHECK(reserved_count <= capacity)                                │
│  reservation: 부분 UNIQUE(class_session_id, member_id) WHERE ACTIVE, │
│    부분 UNIQUE(class_session_id) WHERE ACTIVE AND class_type=LESSON, │
│    부분 UNIQUE(member_id, class_date, start_time) WHERE ACTIVE       │
│  pass_transaction: CHECK(admin_id·member_id 중 정확히 하나 NOT NULL)  │
└───────────────────────────────────────────────────────────────────┘
```

### Recommended Project Structure

```
src/main/kotlin/com/goldwrestling/
├── schedule/
│   ├── ClassSchedule.kt              # 정기 시간표 엔티티 (Flyway 시드로만 채워짐)
│   ├── ClassScheduleRepository.kt
│   ├── ClassSession.kt               # 날짜별 실체화 엔티티
│   ├── ClassSessionRepository.kt     # insertIfAbsent(네이티브) / 카운터 조건부 UPDATE
│   ├── DayOfWeek 등은 java.time.DayOfWeek 재사용 — 새 enum 만들지 않음
│   ├── ScheduleService.kt            # 회원용 2주 조회 (@Transactional(readOnly=true))
│   ├── AdminScheduleController.kt    # 스케줄 보드(SCHED-03), 휴강 처리(RESV-09)
│   ├── AdminScheduleService.kt
│   ├── MemberScheduleController.kt
│   └── dto/
├── reservation/
│   ├── Reservation.kt
│   ├── ReservationRepository.kt      # cancelIfActive 등 조건부 UPDATE
│   ├── ReservationStatus.kt          # ACTIVE / CANCELED
│   ├── MemberReservationController.kt   # RESV-01~05
│   ├── MemberReservationService.kt
│   ├── AdminReservationController.kt    # RESV-07·08
│   ├── AdminReservationService.kt
│   └── dto/
├── notification/
│   ├── Notification.kt
│   ├── NotificationType.kt
│   ├── NotificationRepository.kt
│   └── NotificationService.kt        # create(...)만 — 조회는 Phase 6
└── common/
    └── time/
        └── WeekRange.kt              # 월요일 기준 주 계산 — conventions.md §1이 이미 예고한
                                       # 패키지지만 아직 코드가 없다. 이 phase가 최초 구현체
```

### Pattern 1: `ClassSession` get-or-create — 네이티브 `ON CONFLICT DO NOTHING`

**What:** "필요할 때 생성"(D-094)이 요구하는 경쟁 안전 get-or-create를, JPA 엔티티 저장 후
제약 위반을 catch하는 방식이 아니라 네이티브 upsert로 구현한다.

**When to use:** 예약 생성·변경·휴강 처리 진입점에서 `ClassSession`이 아직 없을 수 있는 모든 경로.

**Example:**
```kotlin
// ClassSessionRepository.kt — Source: PostgreSQL INSERT...ON CONFLICT 표준 문법
// (Hibernate 6.5+가 HQL 레벨 ON CONFLICT 절을 지원하지만, 이 경우처럼 "리터럴 값 1건 삽입"에는
//  HQL의 bulk insert-select 문법이 자연스럽지 않다 — 네이티브 SQL이 더 직접적이다)
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query(
    value = """
        insert into class_session
            (class_schedule_id, class_date, class_type, start_time, end_time, capacity, status, created_at)
        values (:scheduleId, :classDate, :classType, :startTime, :endTime, :capacity, 'SCHEDULED', now())
        on conflict (class_schedule_id, class_date) do nothing
    """,
    nativeQuery = true,
)
fun insertIfAbsent(
    @Param("scheduleId") scheduleId: Long,
    @Param("classDate") classDate: LocalDate,
    @Param("classType") classType: String,
    @Param("startTime") startTime: LocalTime,
    @Param("endTime") endTime: LocalTime,
    @Param("capacity") capacity: Int?,
): Int

fun findByClassScheduleIdAndClassDate(scheduleId: Long, classDate: LocalDate): ClassSession?

// 서비스에서: insertIfAbsent 반환값(0 또는 1)을 확인하지 않고 항상 findBy...로 재조회한다.
// 반환 0은 "내가 만들지 않았다"는 뜻일 뿐 실패가 아니다 — 이미 존재하는 행을 그대로 쓰면 된다.
fun getOrCreateSession(schedule: ClassSchedule, date: LocalDate): ClassSession {
    classSessionRepository.insertIfAbsent(
        schedule.id!!, date, schedule.classType.name,
        schedule.startTime, schedule.endTime, schedule.capacity,
    )
    return classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, date)
        ?: error("insertIfAbsent 직후 재조회 실패 — 있을 수 없는 상태")
}
```

### Pattern 2: 정원 조건부 UPDATE (D-021 확장, `PassRepository.adjustRemainingCount`와 동일 관례)

**What:** `reserved_count`를 원자적으로 증가시키되 정원을 넘으면 DB가 갱신을 거부한다.

**Example:**
```kotlin
// ClassSessionRepository.kt — Source: 기존 PassRepository.adjustRemainingCount(KDoc 그대로 인용)
// "호출부는 이 쿼리 실행 전에 이후 로직에 필요한 스칼라 값을 지역 변수로 미리 꺼내둬야 한다 —
//  실행 직후 준영속 상태가 된 엔티티의 LAZY 연관에 접근하면 LazyInitializationException이 난다"
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query(
    "update ClassSession s set s.reservedCount = s.reservedCount + 1 " +
        "where s.id = :id and s.status = com.goldwrestling.schedule.ClassSessionStatus.SCHEDULED " +
        "and s.capacity is not null and s.reservedCount + 1 <= s.capacity",
)
fun incrementReservedCountIfCapacityAvailable(@Param("id") id: Long): Int

@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query(
    "update ClassSession s set s.reservedCount = s.reservedCount - 1 " +
        "where s.id = :id and s.reservedCount > 0",
)
fun decrementReservedCount(@Param("id") id: Long): Int
```
반환값 0은 "정원 초과로 경쟁에서 졌다"는 뜻 — `ReservationCapacityExceededException`으로 변환한다.

### Pattern 3: 부분 유니크 인덱스 3종 — 중복 방지의 실제 방어선

**What:** 세 가지 서로 다른 불변식을 세 개의 부분(partial) 유니크 인덱스로 표현한다. Postgres
부분 인덱스의 `WHERE` 절과 인덱스 대상 컬럼은 **반드시 같은 테이블 안에서** 나와야 하므로,
`Reservation`이 `class_session`의 값을 일부 복사(비정규화)해야 한다 — `ClassSession`이
`ClassSchedule`의 시각을 복사하는 것과 같은 이유(D-094)다.

**Example:**
```sql
-- reservation 테이블에 class_date, start_time, class_type 을 예약 시점에 복사해 둔다(비정규화).
-- 세 인덱스 모두 status = 'ACTIVE' 부분 인덱스라 취소된 예약은 자유롭게 다시 예약할 수 있다.

-- ① 같은 세션에 같은 회원이 중복 예약하지 못한다 (재예약은 취소 후에만)
CREATE UNIQUE INDEX ux_reservation_session_member_active
    ON reservation (class_session_id, member_id) WHERE status = 'ACTIVE';

-- ② 1:1(LESSON) 슬롯은 세션당 활성 예약이 최대 1건 — 정원 조건부 UPDATE와 별개의 1차 방어선
CREATE UNIQUE INDEX ux_reservation_lesson_slot_active
    ON reservation (class_session_id) WHERE status = 'ACTIVE' AND class_type = 'LESSON';

-- ③ 같은 회원이 같은 날짜·같은 시각에 두 건(SESSION+LESSON 등) 예약 못함
CREATE UNIQUE INDEX ux_reservation_member_timeslot_active
    ON reservation (member_id, class_date, start_time) WHERE status = 'ACTIVE';
```
서비스 계층에서도 사전 검사(예: 미리 조회해서 사용자에게 정확한 에러 메시지 제공)를 하지만,
**실제 방어는 이 인덱스들이 한다** — 사전 검사와 커밋 사이에 다른 트랜잭션이 끼어들 수 있으므로
(D-021의 "애플리케이션 조건문으로 초과 예약 0건을 보장할 수 없다"는 근거가 여기도 동일하게 적용).
인덱스 위반 시 `DataIntegrityViolationException`을 잡아 도메인 예외로 변환한다 — 이때는
**INSERT 실패이므로 트랜잭션이 여기서 끝나도 안전**하다(정원 조건부 UPDATE는 이미 롤백 대상에
포함돼 있으므로 별도 보정이 필요 없다).

### Pattern 4: 이용권 선택 — 만료 임박순 단일 이용권 (D-091)

**What:** 여러 장 보유 시 종료일 오름차순으로 첫 번째 "잔여가 충분한 한 장"을 고른다. 합산 금지.

**Example:**
```kotlin
// PassRepository.kt에 추가 — Source: 기존 findAllByMemberIdAndStatusNot...와 동일 관례
fun findFirstByMemberIdAndTypeAndStatusAndEndDateGreaterThanEqualAndRemainingCountGreaterThanEqual(
    memberId: Long,
    type: PassType,
    status: PassStatus,
    classDate: LocalDate,       // "유효기간 판정 기준일은 수업날" — Pass.isExpired와 같은 비교축
    requiredAmount: BigDecimal, // 항상 ONE_SESSION("1.0") — 이번 phase 요구량은 고정
): Pass?
// Spring Data 파생 쿼리는 정렬을 이름에 담을 수 없으므로, 아래처럼 @Query + ORDER BY로 명시한다.
```
```kotlin
@Query(
    "select p from Pass p where p.member.id = :memberId and p.type = :type " +
        "and p.status = com.goldwrestling.pass.PassStatus.ACTIVE " +
        "and p.endDate >= :classDate and p.remainingCount >= :requiredAmount " +
        "order by p.endDate asc, p.id asc",
)
fun findDeductionCandidates(
    @Param("memberId") memberId: Long,
    @Param("type") type: PassType,
    @Param("classDate") classDate: LocalDate,
    @Param("requiredAmount") requiredAmount: BigDecimal,
): List<Pass>
// 서비스: candidates.firstOrNull() ?: throw InsufficientPassCountException()
// 그 뒤 adjustRemainingCount(candidate.id, -ONE_SESSION) 호출 — 0행 반환 시(경쟁 패배) 재조회 후
// InsufficientPassCountException 또는 재시도 여부는 Open Questions 참조.
```

### Pattern 5: 취소 — compare-and-swap + "취소해도 되는지"는 판정, 반영은 UPDATE (D-072 확장)

**What:** `Pass.resolveCancellationOffset`/`PassRepository.cancelIfNotCanceled`와 완전히 같은
역할 분리를 `Reservation`에도 적용한다 — 엔티티 메서드는 판정만, 실제 상태 전환은 조건부 UPDATE.

**Example:**
```kotlin
// Reservation.kt — 판정만, 대입 없음 (D-072 KDoc 관례 그대로)
fun assertCancelableByMember(today: LocalDate) {
    if (status == ReservationStatus.CANCELED) throw ReservationAlreadyCanceledException()
    if (classDate == today) throw SameDayCancellationNotAllowedException()
}

// ReservationRepository.kt
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query(
    "update Reservation r set r.status = com.goldwrestling.reservation.ReservationStatus.CANCELED, " +
        "r.canceledAt = :now, r.canceledByMemberId = :memberId, r.refunded = true " +
        "where r.id = :id and r.status = com.goldwrestling.reservation.ReservationStatus.ACTIVE",
)
fun cancelByMemberIfActive(
    @Param("id") id: Long,
    @Param("memberId") memberId: Long,
    @Param("now") now: OffsetDateTime,
): Int
```
관리자 대리 취소는 `refunded` 파라미터를 요청에서 받아 그대로 전달하는 별도 오버로드로 둔다
(기본값 `true` — "기본: 복구"). `refunded = false`면 `adjustRemainingCount` 호출 자체를 생략한다.

### Anti-Patterns to Avoid

- **엔티티를 read→mutate→save로 취소/차감 처리** — D-072가 이미 Phase 3에서 겪은 경쟁 버그를
  근거로 금지한 패턴이다. 이 phase의 모든 상태 전환(예약 생성의 정원 증가, 취소, 휴강 캐스케이드)에
  같은 원칙을 적용한다
- **`ClassSession` 사전 생성 배치** — D-094이 "배치 인프라는 Phase 5 소관, 배치 미실행 시 예약
  자체가 막히는 결함"을 이유로 명시적으로 기각했다. 필요 시점 생성만 구현한다
- **`DataIntegrityViolationException`을 잡아 재조회하는 get-or-create** — Pitfall 1 참조. 네이티브
  `ON CONFLICT DO NOTHING`을 쓴다
- **여러 이용권 잔여를 합산해 하나의 예약을 처리** — D-091이 명시적으로 금지("예약 1건 ↔
  이용권 1장"). 후보 쿼리가 단일 이용권 기준으로 `remainingCount >= requiredAmount`를 검사하는
  이유가 이것이다

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|--------------|-----|
| 이용권 잔여 차감/복구 | 새 조건부 UPDATE 로직 | `PassRepository.adjustRemainingCount` (그대로, 파라미터만 다르게 호출) | KDoc이 "Phase 4의 예약 차감이 그대로 재사용하도록 만들어진 경로"라고 명시. 재구현하면 D-021 보장이 두 곳으로 흩어진다 |
| 이용권 표시 상태·만료 판정 | 새 만료 계산 함수 | `Pass.displayStatus(today)` / 내부 `isExpired` 로직을 참고해 `classDate` 기준으로 같은 비교축(`endDate >= classDate`) 사용 | D-066 종료일 포함 규칙이 이미 검증됨 — 다른 비교식을 새로 만들면 경계값에서 어긋난다 |
| 페이지네이션 응답 | 새 `PageResponse` 유사 DTO | `member/dto/PageResponse.kt` (그대로 import) | 이미 "두 번째 기능 패키지가 쓰면 common으로 승격" 예고된 재사용 대상 |
| 관리자 목록 필터 | 개별 `@RequestParam` 여러 개 | `@ModelAttribute` + `@ParameterObject`(D-054), `MemberSpecifications`/`PassTransactionSpecifications`와 같은 `Specification` 조합 패턴 | D-054가 이미 "개별 파라미터로 펼치는 방식"의 실패를 근거로 기각. 새 검색 DTO도 같은 사고를 반복하면 openapi.yaml 계약이 깨진다 |
| 회원 상태 검사 | 서비스마다 `if (status != ACTIVE)` 산개 | `MemberStateGate.requireActive(principal)` | D-040이 이미 "새 엔드포인트마다 게이트 호출 필요 여부를 확인"하라고 명시. 빠뜨리면 컴파일도 테스트도 안 잡아준다 |
| 현재 시각·"오늘" 판정 | `OffsetDateTime.now()`/`LocalDate.now()` 직접 호출 | 주입된 `Clock` 빈(`OffsetDateTime.now(clock)`, `LocalDate.now(clock)`) | 당일 취소 불가·예약 창 오픈 판정 모두 테스트에서 시각을 고정해야 검증 가능하다. 직접 호출하면 그 테스트 자체가 불가능해진다 |
| 인증 주체 해석 | 새 principal 파싱 | `AuthenticatedPrincipal.requireMemberId()`/`requireAdminId()` | 이미 회원/관리자 구분과 프로그래밍 오류 감지(`check`)가 구현돼 있다 |

**Key insight:** 이 phase의 "새로 만드는 코드"는 사실 대부분 **Phase 3이 검증한 관례의 새로운
적용**이다. `PassRepository.adjustRemainingCount`가 이미 "Phase 4가 그대로 재사용하도록" 설계된
경로라는 KDoc 자체가 이 판단의 근거다 — 새 차감/복구 메커니즘을 만드는 순간 D-021의 보장이
두 벌로 나뉘어 유지보수 비용이 배가된다.

## Common Pitfalls

### Pitfall 1: 유니크 제약 위반 catch 방식의 get-or-create는 트랜잭션을 오염시킬 수 있다
**What goes wrong:** `classSessionRepository.save(newSession)`을 시도하고
`DataIntegrityViolationException`을 catch해 재조회하는 방식은, 같은 `@Transactional` 메서드 안에서
계속 진행하려 할 때 이후 DB 작업이 실패하거나 예기치 않게 동작할 수 있다.
**Why it happens:** JPA 구현체(Hibernate)는 flush 시점에 제약 위반이 나면 그 트랜잭션을 더 이상
정상 상태로 보장하지 않는 경우가 있다 — `IDENTITY` 전략은 INSERT를 즉시(지연 없이) 실행하므로
`save()` 호출 시점에 바로 flush가 일어나 예외가 그 위치에서 던져진다.
**How to avoid:** 네이티브 `INSERT ... ON CONFLICT DO NOTHING`을 써서 애초에 예외가 나지 않게 한다
(Pattern 1). 이 쿼리는 충돌해도 0행 영향으로 **정상 종료**하므로 트랜잭션이 오염될 여지가 없다.
**Warning signs:** get-or-create 직후의 다음 쿼리가 `PersistenceException`이나 세션이 닫힌 것 같은
불가해한 오류로 실패한다면 이 패턴을 의심한다.

### Pitfall 2: `adjustRemainingCount`가 0행을 반환하는 두 가지 다른 의미를 혼동하기 쉽다
**What goes wrong:** 취소 시 이용권을 복구하려는데 그 이용권이 `REGISTRATION_CANCELED`(등록
취소)로 이미 `CANCELED` 상태라면, `adjustRemainingCount`의 `WHERE status = 'ACTIVE'` 조건에 걸려
0행이 갱신된다. 예약 생성 시 이 0행은 "경쟁에서 졌다"(예외로 변환해야 함)는 뜻이지만, **취소
복구 시의 0행은 "복구하지 않는 게 정책"**(D-091 "등록 취소된 이용권은 복구하지 않는다")이라 예외를
던지면 안 된다.
**Why it happens:** 같은 리포지토리 메서드를 두 가지 다른 의미론으로 재사용하기 때문. 호출부가
문맥을 구분하지 않으면 정상적인 취소가 500/409 에러로 실패하거나, 반대로 실패해야 할 케이스가
조용히 통과한다.
**How to avoid:** 취소/복구 경로에서는 `adjustRemainingCount` 반환값 0을 **무시하고 계속 진행**한다
(단, Reservation.status는 정상적으로 CANCELED로 전환한다 — "예약만 취소 상태로 전환, 이용권은
복구하지 않는다"가 D-091의 정확한 문구). 예약 생성 경로에서는 0을 예외로 변환한다. 이 두 호출부를
테스트로 각각 검증한다 (등록 취소된 이용권으로 예약된 건을 방어적으로 취소하는 케이스 — 컨텍스트가
"휴강 일괄 취소 등 다른 경로가 있다"고 명시한 그 케이스다).
**Warning signs:** 취소 통합테스트에서 "등록 취소된 이용권" 픽스처가 없다면 이 분기가 커버되지
않은 것이다.

### Pitfall 3: "당일" 판정에 시각(time)을 섞으면 안 된다 — 날짜(date) 비교여야 한다
**What goes wrong:** "당일에는 취소·변경 불가"를 `now().isBefore(classStartDateTime)`처럼 시각까지
포함해 비교하면, 예를 들어 저녁 수업 예약을 그날 아침에 취소하려는 회원이 "아직 시작 전이니
가능"으로 잘못 통과되거나, 반대로 마감(오픈 시각 이전) 판정과 당일 판정 로직이 뒤섞여 버그가 난다.
**Why it happens:** 마감 규칙("시작 시각 전까지 예약 가능")과 당일 취소 규칙("당일은 무조건
불가")은 서로 다른 축이다 — 하나는 시각(time) 비교, 하나는 날짜(date) 비교다. 이 둘을 하나의
`OffsetDateTime` 비교식으로 합치려는 유혹이 실수의 원인이다.
**How to avoid:** 당일 판정은 `reservation.classDate == LocalDate.now(clock)` (date 동일성)만
본다. 마감 판정은 `LocalDateTime.of(classDate, startTime) > OffsetDateTime.now(clock)`처럼 시각까지
포함한 별도 비교로 분리한다. 두 판정을 서로 다른 메서드/테스트로 나눈다.
**Warning signs:** "당일 취소 불가" 테스트가 시각을 특정 시간(예: 09:00)으로 고정해 두고 그
값에서만 통과한다면, 실제로는 시각 비교 로직이 섞여 있을 가능성이 크다 — `Clock`을 하루 중
여러 시각(00:01, 23:59 등)으로 바꿔가며 테스트해 날짜 경계만 보는지 검증한다.

### Pitfall 4: `PassTransaction`의 "주체"는 이용권 소유자가 아니라 액션을 수행한 사람이다
**What goes wrong:** 관리자가 회원을 대신해 예약을 취소(대리 취소, RESV-08)할 때, 이 이력의
주체를 "이용권 소유자인 회원"으로 잘못 채우면 감사 추적("누가 이 취소를 실행했는가")이 틀어진다.
**Why it happens:** `PassTransaction`이 지금까지(Phase 3)는 항상 관리자 주체였고, Phase 4에서
"회원도 주체가 될 수 있다"는 확장이 처음 생긴다 — 확장 컬럼을 추가하면서 "이 트랜잭션이 누구
소유의 이용권을 다뤘는가"(→ `pass.member`로 이미 추적됨)와 "누가 이 트랜잭션을 발생시켰는가"(→
새 주체 컬럼)를 혼동하기 쉽다.
**How to avoid:** 규칙을 명확히 코드 주석으로 남긴다 — 회원 셀프 예약/취소/변경은 `member_id`
주체, 관리자 대리 취소/변경과 휴강 처리(`CLASS_CANCELED_REFUND`)는 `admin_id` 주체. **행위자
기준**이지 소유자 기준이 아니다.
**Warning signs:** 관리자 대리 취소 통합테스트에서 생성된 `PassTransaction.member`가 non-null이면
(회원 주체로 기록됐다면) 이 실수다.

### Pitfall 5: 부분 유니크 인덱스는 INSERT 실패로만 나타난다 — UPDATE 경로에는 적용되지 않는다
**What goes wrong:** 예약 "변경"(취소+재예약)을 하나의 UPDATE로 처리하려 하면(기존 행의 status만
유지하고 session_id를 바꾸는 식) 부분 유니크 인덱스가 방어하지 못하는 경로가 생긴다 —
컨텍스트가 이미 "변경 = 새 INSERT + 기존 행 취소"로 결정했으므로(D-090), 새 예약은 항상 새 행의
INSERT로 만들어야 인덱스가 정상 작동한다.
**Why it happens:** "기존 예약 행을 재사용해서 필드만 바꾸면 이력이 하나로 합쳐져 더 깔끔해
보인다"는 유혹이 있지만, 그러면 취소 이력(무엇을 취소했는지)과 재예약 이력이 한 행에 섞여
D-090이 요구하는 "CANCEL_REFUND(+1)와 RESERVE(−1) 2건 모두 남긴다"는 원장 요구사항과도 충돌한다.
**How to avoid:** 변경은 항상 (1) 기존 예약에 취소 조건부 UPDATE, (2) 새 예약 INSERT 두 단계로
구현한다. 둘 다 같은 `@Transactional` 메서드 안에서 실행해 원자성을 보장한다.
**Warning signs:** `Reservation` 엔티티에 "변경 이력"을 표현하는 self-reference 컬럼(예:
`replaced_reservation_id`)을 추가하려는 설계가 나온다면, 애초에 UPDATE로 처리하려는 신호일 수
있다 — 요구된 것은 별도 두 행 + 원장 2건이다.

## Code Examples

### `ClassSchedule`·`ClassSession` DDL — 타입별 컬럼 규칙은 DB CHECK로 (V4 `ck_pass_remaining_count_by_type`와 동일 관례)

```sql
-- V6__create_schedule_reservation_notification.sql (번호는 계획 단계에서 확정)
CREATE TABLE class_schedule (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,          -- java.time.DayOfWeek name(), EnumType.STRING
    class_type VARCHAR(20) NOT NULL,           -- EVENING / SESSION / LESSON
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    capacity INT,                              -- SESSION/LESSON만 NOT NULL, EVENING은 NULL
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_class_schedule_branch FOREIGN KEY (branch_id) REFERENCES branch (id),
    CONSTRAINT uq_class_schedule UNIQUE (branch_id, day_of_week, class_type, start_time),
    CONSTRAINT ck_class_schedule_period CHECK (end_time > start_time),
    -- Pass의 ck_pass_remaining_count_by_type(V4)과 동일한 사고: 타입별 필수 컬럼을 DB가 강제
    CONSTRAINT ck_class_schedule_capacity_by_type CHECK (
        (class_type = 'EVENING' AND capacity IS NULL) OR
        (class_type IN ('SESSION', 'LESSON') AND capacity IS NOT NULL AND capacity > 0)
    )
);

CREATE TABLE class_session (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    class_schedule_id BIGINT NOT NULL,
    class_date DATE NOT NULL,
    class_type VARCHAR(20) NOT NULL,           -- 조회 편의를 위한 복사(항상 schedule과 동일)
    start_time TIME NOT NULL,                  -- D-094: 시간표에서 복사, 자기 컬럼으로 보유
    end_time TIME NOT NULL,
    capacity INT,                              -- 복사 — 정원 조건부 UPDATE가 조인 없이 단일 테이블에서 끝나게 함
    reserved_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',  -- SCHEDULED / CANCELED(휴강)
    canceled_at TIMESTAMPTZ,
    cancel_reason VARCHAR(500),
    canceled_by_admin_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_class_session_schedule FOREIGN KEY (class_schedule_id) REFERENCES class_schedule (id),
    CONSTRAINT fk_class_session_canceled_by FOREIGN KEY (canceled_by_admin_id) REFERENCES admin (id),
    CONSTRAINT uq_class_session UNIQUE (class_schedule_id, class_date),
    CONSTRAINT ck_class_session_reserved_count CHECK (
        reserved_count >= 0 AND (capacity IS NULL OR reserved_count <= capacity)
    ),
    -- pass의 ck_pass_cancellation(V4)과 동일한 완전성 검사 관례
    CONSTRAINT ck_class_session_cancellation CHECK (
        (status = 'CANCELED' AND canceled_at IS NOT NULL AND cancel_reason IS NOT NULL AND canceled_by_admin_id IS NOT NULL) OR
        (status <> 'CANCELED' AND canceled_at IS NULL AND cancel_reason IS NULL AND canceled_by_admin_id IS NULL)
    )
);

CREATE INDEX idx_class_session_date ON class_session (class_date);  -- 주간 보드·2주 조회
```

### `reservation` DDL — 비정규화 컬럼 + 부분 유니크 인덱스 3종

```sql
CREATE TABLE reservation (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    member_id BIGINT NOT NULL,
    class_session_id BIGINT NOT NULL,
    pass_id BIGINT NOT NULL,                   -- 복구 대상 추적(D-091)
    class_type VARCHAR(20) NOT NULL,            -- 비정규화: 부분 인덱스 ②용
    class_date DATE NOT NULL,                   -- 비정규화: 부분 인덱스 ③용, 당일 판정용
    start_time TIME NOT NULL,                   -- 비정규화: 부분 인덱스 ③용
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    reserved_at TIMESTAMPTZ NOT NULL,
    canceled_at TIMESTAMPTZ,
    canceled_by_member_id BIGINT,
    canceled_by_admin_id BIGINT,
    refunded BOOLEAN,                            -- CANCELED일 때만 값 존재(대리 취소 "복구 안 함" 대응)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_reservation_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_reservation_session FOREIGN KEY (class_session_id) REFERENCES class_session (id),
    CONSTRAINT fk_reservation_pass FOREIGN KEY (pass_id) REFERENCES pass (id),
    CONSTRAINT fk_reservation_canceled_by_member FOREIGN KEY (canceled_by_member_id) REFERENCES member (id),
    CONSTRAINT fk_reservation_canceled_by_admin FOREIGN KEY (canceled_by_admin_id) REFERENCES admin (id),
    CONSTRAINT ck_reservation_cancellation CHECK (
        (status = 'CANCELED' AND canceled_at IS NOT NULL AND refunded IS NOT NULL
            AND ((canceled_by_member_id IS NOT NULL AND canceled_by_admin_id IS NULL)
              OR (canceled_by_member_id IS NULL AND canceled_by_admin_id IS NOT NULL))) OR
        (status <> 'CANCELED' AND canceled_at IS NULL AND refunded IS NULL
            AND canceled_by_member_id IS NULL AND canceled_by_admin_id IS NULL)
    )
);

CREATE UNIQUE INDEX ux_reservation_session_member_active
    ON reservation (class_session_id, member_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX ux_reservation_lesson_slot_active
    ON reservation (class_session_id) WHERE status = 'ACTIVE' AND class_type = 'LESSON';
CREATE UNIQUE INDEX ux_reservation_member_timeslot_active
    ON reservation (member_id, class_date, start_time) WHERE status = 'ACTIVE';
CREATE INDEX idx_reservation_member_active ON reservation (member_id, status);  -- 본인 목록(RESV-05)
CREATE INDEX idx_reservation_session ON reservation (class_session_id);         -- 스케줄보드 셀 명단
```

### `pass_transaction` 주체 확장 — V4 수정 없이 새 마이그레이션으로

```sql
-- 이미 커밋된 V4는 절대 수정하지 않는다 (conventions §9) — 새 버전으로 완화·확장한다
ALTER TABLE pass_transaction ALTER COLUMN admin_id DROP NOT NULL;
ALTER TABLE pass_transaction ADD COLUMN member_id BIGINT REFERENCES member (id);

-- V4 주석이 예고한 "주체 중 정확히 하나" CHECK — 이번 phase에서 실제로 필요해졌다
ALTER TABLE pass_transaction ADD CONSTRAINT ck_pass_transaction_subject CHECK (
    (admin_id IS NOT NULL AND member_id IS NULL) OR
    (admin_id IS NULL AND member_id IS NOT NULL)
);
```
기존 8건의 `TransactionReason`(`INITIAL_GRANT`, `ADMIN_ADJUST`, `REGISTRATION_CANCELED` 등)은
전부 관리자 주체라 이 변경으로 영향받지 않는다(기존 행은 `admin_id`가 이미 채워져 있고
`member_id`는 NULL). Kotlin 엔티티도 `admin: Admin?`(nullable)로 완화하고 `member: Member?`를
추가한다.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| `pass_transaction.admin_id NOT NULL` (Phase 3) | `admin_id`/`member_id` 중 정확히 하나 (Phase 4) | 이번 phase | 회원 셀프 예약/취소가 원장에 정확한 행위자로 기록됨. Phase 3 완료 시점 V4 KDoc이 이미 "이번 phase의 사유 코드는 전부 관리자 주체"라고 이 확장을 예고했다 |
| D-021 "정원 방식은 예약 phase에서 실측 비교 후 확정" (미결) | 조건부 UPDATE(정원) + 부분 유니크 인덱스(1:1) 조합으로 확정 | 이번 연구 + CONTEXT.md discuss-phase | discuss-phase에서 이미 이 조합으로 결정됐으므로, 별도 벤치마크 비교 없이 D-021 "유의" 문구를 이 결정으로 갱신하면 된다(플랜 단계 `docs/decisions.md` 갱신 항목) |

**해당 없음:** 이 phase가 다루는 영역(Postgres 조건부 UPDATE, 부분 유니크 인덱스)은 수년간
안정적인 표준 기법이라 "deprecated된 옛 방식"이 따로 없다.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|----------------|
| A1 | Hibernate가 flush 시점 제약 위반 예외 이후 같은 트랜잭션 내 후속 작업을 신뢰할 수 없게 만들 수 있다(Pitfall 1의 근거) — 이 프로젝트에서 직접 재현 검증은 하지 않았고 일반적인 JPA/Hibernate 동작 지식과 WebSearch 결과에 근거했다 | Common Pitfalls Pitfall 1, Code Examples Pattern 1 | 틀렸다면 `save()` + catch 방식도 안전할 수 있어 네이티브 쿼리 도입이 불필요한 복잡도가 된다 — 다만 `ON CONFLICT DO NOTHING` 자체는 이 우려와 무관하게도 더 명확한 의도 표현이라 손해는 크지 않다 |
| A2 | `Reservation.class_type`/`class_date`/`start_time` 비정규화가 부분 유니크 인덱스 3종에 필요하다는 스키마 설계는 이 연구의 권장안이며, CONTEXT.md가 명시적으로 락인한 컬럼 목록은 아니다(락인된 것은 "1:1은 부분 유니크 인덱스가 1차 방어선"이라는 방향뿐) | Code Examples "reservation DDL", Architecture Patterns Pattern 3 | 다른 방식(예: `class_session` 조인 뷰 + 애플리케이션 사전 검사만)을 택해도 기능은 동작하지만, Postgres 부분 인덱스 제약상 조인 기반 방어는 불가능해 방어 강도가 약해진다 |
| A3 | Notification 스키마(관리자 알림)에 `is_read`/`read_at`을 이번 phase에서 미리 추가하는 것을 권장했다 — NOTIF-01 자체는 이 컬럼을 쓰지 않지만 Phase 6(NOTIF-02)이 "그대로 조회할 스키마"를 요구하므로 선반영을 제안한 것이며 사용자 확정 사항은 아니다 | Open Questions, phase_requirements NOTIF-01 | 틀렸다면(Phase 6이 다른 컬럼 설계를 원하면) 이 컬럼들이 미사용 상태로 남거나 Phase 6에서 마이그레이션으로 조정해야 한다 — 둘 다 비용은 낮다 |

## Open Questions

1. **policies.md §8의 "k6 부하테스트"를 이 phase에서 실제로 도입해야 하는가?**
   - What we know: `docs/policies.md`(최우선 문서) §8이 "DB 유니크 제약 + 락(또는 조건부 insert)으로
     보장하고, k6 부하테스트로 검증한다"고 명시한다. `docs/requirements.md` 완료조건은 "M4는
     동시성 테스트(정원 경쟁·1:1 슬롯) 필수"라고만 하고 k6를 언급하지 않는다.
     `docs/conventions.md` §10.4·`.claude/skills/add-domain-test`는 JVM `ExecutorService`+
     `CountDownLatch` 통합테스트만 규정한다. 이 저장소에 k6 스크립트·CI 워크플로우·k6 관련 문서는
     전혀 없다(grep 결과 policies.md 1건뿐).
   - What's unclear: k6는 HTTP 레벨 부하·처리량 검증 도구이고, ExecutorService 테스트는 서비스
     계층에서 "정확히 정원만큼만 성공"을 증명하는 정확성(correctness) 테스트다 — 목적이 다르다.
     k6 도입은 새 인프라(스크립트, 로컬/CI 실행 환경, 대상 서버 기동)가 필요해 phase 범위·기간에
     영향을 준다.
   - Recommendation: 플랜 단계에서 사용자에게 확인한다 — (a) ExecutorService 기반 통합테스트로
     "초과 예약 0건"을 증명하는 것으로 §8을 충족한 것으로 볼지, (b) k6를 이번 phase에 새 인프라로
     도입할지, (c) k6 도입을 별도 후속 작업(문서화·인프라 phase)으로 미룰지. 어느 쪽이든
     `docs/policies.md` 문구 자체를 정정하거나 그대로 둘지도 함께 결정해야 한다.

2. **휴강 처리 시 알림(Notification)의 단위 — 세션 1건당 1개인가, 취소된 예약 건별로 N개인가?**
   - What we know: CONTEXT.md는 "휴강 처리" 이벤트를 4종 중 하나로 묶어서만 언급하고, 세션 하나가
     동시에 여러 예약을 취소시키는 이 케이스의 알림 grain을 명시하지 않았다.
   - What's unclear: 정원 10명짜리 SESSION이 휴강되면 예약 10건이 한꺼번에 취소된다. 이를 알림
     10건으로 만들면 관리자 알림함이 순식간에 넘치고(Phase 6 NOTIF-02 미확인 카운트도 왜곡된다),
     1건("○○ 수업 08/10 11:00 휴강 — N건 자동 취소")으로 만들면 `Notification.reservation_id`
     단일 FK로는 표현이 안 돼 `class_session_id`(nullable) 같은 별도 참조가 필요하다.
   - Recommendation: 세션당 1건(요약형)을 권장한다 — 관리자 알림 UX상 자연스럽고, Phase 6이
     "그대로 조회할 스키마"를 요구하므로 폭증하는 스키마보다 요약형이 더 안전하다. 단, 이는
     추천일 뿐이며 discuss-phase나 플랜 단계에서 확정이 필요하다.

3. **선택된 이용권이 예약 순간 경쟁에서 소진되면(조건부 UPDATE 0행) 다음 후보로 재시도하는가, 즉시 실패시키는가?**
   - What we know: Pattern 4의 후보 쿼리는 여러 장 중 만료 임박 1건을 고르지만, 그 사이 다른
     트랜잭션이 같은 이용권을 먼저 써버릴 수 있다(동시성). `adjustRemainingCount`는 이 경우 0행을
     반환한다.
   - What's unclear: 같은 회원이 같은 이용권을 동시에 두 곳에서 쓰는 상황은 실제로는 매우 드물다
     (본인이 자기 자신과 경쟁하는 경우 — 다른 회원이 남의 이용권을 쓰는 경우는 없다). 재시도 로직을
     만들 가치가 있는지 불확실하다.
   - Recommendation: 재시도 없이 즉시 `InsufficientPassCountException`(또는 별도
     `PassStateConflictException`류)으로 실패시키고, 회원이 재요청하게 한다 — 발생 빈도가 낮고
     구현 단순성이 이득이 더 크다. 플랜에서 이 판단을 명시적으로 기록만 하면 된다(별도 확인 불필요,
     Claude's Discretion 범위).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|--------------|-----------|---------|----------|
| Docker | Testcontainers 통합테스트, 로컬 Postgres | ✓ | 28.5.1 | — |
| PostgreSQL(Docker 이미지) | 전체 스키마 | ✓ | 18.4-alpine (`docker-compose.yml`) | — |
| JDK | 컴파일·테스트·bootRun | ✓ | 21.0.12 (Zulu) | — |
| Gradle | 빌드 | ✓ | 9.6.1 (wrapper) | — |

**Missing dependencies with no fallback:** 없음
**Missing dependencies with fallback:** 없음 — 이 phase는 로컬 환경에 이미 존재하는 도구만으로
전부 실행 가능하다.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5(`kotlin-test-junit5`) + AssertJ + Testcontainers 2.x(`testcontainers-postgresql`) |
| Config file | `build.gradle.kts`의 `tasks.withType<Test>` 블록 (Asia/Seoul 시스템 프로퍼티 고정 포함) |
| Quick run command | `./gradlew test --tests "com.goldwrestling.reservation.*"` (패키지 단위) |
| Full suite command | `./gradlew build` (ktlint + compile + 전체 테스트) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|---------------------|--------------|
| SCHED-01 | 시간표 시드가 정확히 로드된다(요일×타임×정원) | Testcontainers 통합 | `./gradlew test --tests "*ClassScheduleSeedIntegrationTest*"` | ❌ Wave 0 |
| SCHED-02 | 세션 get-or-create가 동시 요청에서 정확히 1행만 생성한다 | 동시성(Testcontainers) | `./gradlew test --tests "*ClassSessionConcurrencyTest*"` | ❌ Wave 0 |
| SCHED-03 | 스케줄 보드가 미생성 세션을 0/capacity로 계산해 응답한다 | 단위 + 통합 | `./gradlew test --tests "*AdminScheduleControllerTest*"` | ❌ Wave 0 |
| RESV-01 | SESSION 예약 성공 시 `SESSION_PASS`가 정확히 1회 차감된다 | 단위(도메인) + 통합 | `./gradlew test --tests "*ReservationServiceTest*"` | ❌ Wave 0 |
| RESV-02 | LESSON 예약은 타임당 1명 초과 시 거부된다(단일 스레드) | 단위(도메인) | `./gradlew test --tests "*ReservationServiceTest*"` | ❌ Wave 0 |
| RESV-03 | 잔여 0.5회로 1회 예약이 거부된다 | 단위(도메인) | `./gradlew test --tests "*PassDeductionCandidateTest*"` | ❌ Wave 0 |
| RESV-04 | 당일 취소·변경이 거부되고, 그 외에는 즉시 복구된다 | 단위 + 통합(`Clock` 고정) | `./gradlew test --tests "*ReservationCancellationTest*"` | ❌ Wave 0 |
| RESV-05 | 본인 목록이 취소된 예약을 제외한다 | 통합 | `./gradlew test --tests "*MemberReservationControllerTest*"` | ❌ Wave 0 |
| RESV-06 | 정원 마지막 자리·1:1 슬롯 동시 요청에서 성공 건수 = 정원, DB 행 수·이력 건수 일치 | **동시성 필수**(`ExecutorService`+`CountDownLatch`) | `./gradlew test --tests "*ReservationCapacityConcurrencyTest*"` | ❌ Wave 0 |
| RESV-07 | 관리자 전체 예약 조회가 필터·페이지네이션대로 동작한다 | 통합 | `./gradlew test --tests "*AdminReservationControllerTest*"` | ❌ Wave 0 |
| RESV-08 | 대리 취소 "복구 안 함" 선택 시 이용권 잔여가 그대로다 | 단위 + 통합 | `./gradlew test --tests "*AdminReservationServiceTest*"` | ❌ Wave 0 |
| RESV-09 | 휴강 처리 시 활성 예약 전부 취소+복구+알림 생성, 세션이 예약 불가로 표시 | 통합 | `./gradlew test --tests "*ClassSessionSuspensionTest*"` | ❌ Wave 0 |
| NOTIF-01 | 4종 이벤트 각각 알림 레코드가 정확한 비정규화 필드로 생성된다 | 단위 + 통합 | `./gradlew test --tests "*NotificationServiceTest*"` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** 해당 태스크가 건드린 패키지의 `./gradlew test --tests "com.goldwrestling.<package>.*"`
- **Per wave merge:** `./gradlew build` (전체 스위트)
- **Phase gate:** 전체 스위트 그린 + `/gsd:verify-work` 전에 동시성 테스트(RESV-06) 별도 재확인
  (동시성 테스트는 타이밍에 민감해 CI 환경에 따라 flaky할 수 있으므로 2회 연속 통과 권장)

### Wave 0 Gaps
- [ ] `schedule/ClassScheduleSeedIntegrationTest.kt` — SCHED-01 시드 검증
- [ ] `schedule/ClassSessionConcurrencyTest.kt` — SCHED-02 get-or-create 경쟁
- [ ] `reservation/ReservationCapacityConcurrencyTest.kt` — RESV-06, 이 phase의 핵심 테스트.
      `PassCancellationConcurrencyTest.kt`(위 코드에서 확인한 기존 패턴)를 골격으로 재사용:
      `@SpringBootTest` + `@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)`,
      `@Transactional` 미부착, `AfterEach`에서 `JdbcClient`로 직접 정리
- [ ] `reservation/PassDeductionCandidateTest.kt` — 만료 임박순 선택·합산 금지 단위테스트
- [ ] `notification/NotificationServiceTest.kt` — 4종 이벤트 생성 검증
- [ ] 프레임워크 설치: 불필요 — 기존 Testcontainers·JUnit5 인프라 그대로 사용

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-------------------|
| V4 Access Control | yes | 기존 `SecurityConfig`(`/api/members/**` → `ROLE_MEMBER`, `/api/admin/**` → `ROLE_ADMIN`) 재사용 + 본인 소유 자원만 접근 가능한 IDOR 방어(아래 참조) — 새 규칙 추가 불필요, 새 컨트롤러를 기존 경로 규칙 하위에 두기만 하면 됨 |
| V5 Input Validation | yes | 요청 DTO는 `jakarta.validation`으로 형식만(`@NotNull`, `@Future` 등), 도메인 규칙(정원·당일·잔여)은 서비스/엔티티에서 — 기존 관례(conventions §6) |
| V1 Architecture(불변식 방어) | yes | "초과 예약 0건"이라는 비즈니스 불변식을 DB 제약이 최종 방어선으로 보장 — D-021의 연장. ASVS 표준 카테고리는 아니지만 이 프로젝트 Core Value의 핵심이라 별도 명시 |
| V2 Authentication | no | 이 phase는 새 인증 경로를 추가하지 않는다(Phase 2가 이미 구현) |
| V6 Cryptography | no | 이 phase는 암호화 대상 데이터를 다루지 않는다 |

### Known Threat Patterns for 이 스택

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|-----------------------|
| IDOR — 다른 회원의 예약을 조회·취소 | Tampering / Information Disclosure | `PassTransactionSpecifications.ownedByMember`와 동일한 관례로 `ReservationSpecifications.ownedByMember(memberId)`를 **non-null 반환**으로 만들어, 호출부가 이 조건을 빼먹을 수 없게 한다(기존 KDoc "IDOR 방어의 핵심"과 동일 이유) |
| 정원 조건부 UPDATE 우회(레이스 악용으로 초과 예약) | Tampering | 부분 유니크 인덱스 + 조건부 UPDATE 이중 방어(Pattern 2·3). 애플리케이션 사전 검사만으로는 방어 불가(D-021) |
| 관리자 오버부킹 백도어 요청(운영상 편의로 정원 무시 기능 추가 압력) | Elevation of Privilege(정책 우회) | CONTEXT.md가 이미 "만들지 않는다"고 명시 — 플랜에 이 기능이 등장하면 컨텍스트 위반으로 거부 |
| 취소·차감 이력 위조(감사 추적 무결성) | Repudiation | `PassTransaction`/`Reservation` 모두 append-only(수정 없이 상태 전환만), 주체 컬럼 CHECK로 "누가"가 항상 기록됨 |

## Sources

### Primary (HIGH confidence)
- 로컬 코드베이스 직접 조회 — `PassRepository.kt`, `Pass.kt`, `PassTransaction.kt`,
  `RefreshTokenRepository.kt`, `PassCancellationConcurrencyTest.kt`, `V4__create_pass_tables.sql`,
  `build.gradle.kts`, `docker-compose.yml`, `MemberStateGate.kt`, `AuthenticatedPrincipal.kt`,
  `ClockConfig.kt`, `PassTransactionSpecifications.kt`, `PageResponse.kt`
- `docs/policies.md`, `docs/requirements.md`, `docs/glossary.md`, `docs/decisions.md`(D-016~D-084 발췌),
  `docs/conventions.md` — 프로젝트 스펙 원본, 직접 전문 조회
- `.claude/skills/add-migration/SKILL.md`, `add-domain-test/SKILL.md`, `add-endpoint/SKILL.md`,
  `verify-boot4-api/SKILL.md`, `deliver-phase-chunk/SKILL.md` — 절차 규약 원본
- `.planning/phases/04-schedule-reservation/04-CONTEXT.md`, `.planning/REQUIREMENTS.md`,
  `.planning/STATE.md` — 이번 phase 스코프·요구사항
- Maven Central `spring-boot-dependencies-4.1.0.pom` 직접 조회 — Hibernate ORM 7.4.1.Final,
  PostgreSQL JDBC 42.7.11 버전 확인 [VERIFIED: Maven Central]

### Secondary (MEDIUM confidence)
- WebSearch "PostgreSQL ON CONFLICT DO NOTHING get-or-create pattern JPA Hibernate" — Hibernate
  6.5+의 HQL `ON CONFLICT` 지원과, `ON CONFLICT DO NOTHING`이 예외 없이 0행 갱신으로 조용히
  종료된다는 동작 확인(Baeldung·Vlad Mihalcea 글 인용) — 프로젝트 코드에 아직 이 패턴의 실사용
  전례가 없어 실제 적용 시 통합테스트로 재검증 필요

### Tertiary (LOW confidence)
- Hibernate flush 시점 예외 이후 영속성 컨텍스트 신뢰성 저하 주장(Pitfall 1의 일반적 JPA 지식) —
  이 프로젝트 스택(Hibernate 7.4.1, Boot 4.1)에서 직접 재현 검증하지 않음, Assumptions Log A1 참조

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — 신규 의존성 없음, 기존 버전은 Maven Central 직접 조회로 검증
- Architecture(동시성 패턴): HIGH — Phase 3의 검증된 관례(D-021·D-072)를 그대로 확장하는 것이라
  새로 발명한 것이 거의 없음
- Architecture(get-or-create 패턴): MEDIUM — 이 프로젝트에 실사용 전례가 없는 새 패턴(네이티브
  쿼리 최초 도입), 통합테스트로 재검증 필요
- Pitfalls: HIGH(동시성·당일판정·주체 혼동) / MEDIUM(Pitfall 1, Assumptions A1)
- Notification 스키마: MEDIUM — CONTEXT.md가 grain(세션당 1건 vs 예약당 N건)을 명시하지 않아
  Open Questions로 넘김
- k6 요구사항 갭: 확인 필요 — Open Questions #1

**Research date:** 2026-08-07
**Valid until:** 60일 (안정된 표준 SQL/JPA 기법 위주라 라이브러리 드리프트 리스크가 낮음. 단, Boot 4.1.x
패치 버전이 올라가면 `verify-boot4-api` 스킬 절차로 재확인)
