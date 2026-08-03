# Roadmap: gold-wrestling-be

## Overview

기반(에러 포맷·초기 스키마·API 계약 파이프라인) → 인증·회원(카카오 로그인·온보딩·승인) →
이용권(Pass 3종·이력) → 시간표·예약(즉시 차감/복구·동시성 보장) → 배치(미사용 차감·만료) →
운영(출석·공지·알림)의 순서로 쌓는다. 각 단계는 다음 단계가 전제하는 데이터·규칙을 만든다 —
예약은 이용권 잔여를 전제하고, 이용권은 회원(승인된 ACTIVE 상태)을 전제한다.
이 순서는 사용자가 지정한 M1~M6 마일스톤 구조를 그대로 따른다 (재구성하지 않음).

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (마감 후 `/gsd:phase insert`로 생성)

- [x] **Phase 1: 기반** - 공통 에러 포맷(ProblemDetail), 초기 스키마(Branch/Member/Admin/AdminBranch), openapi.yaml 재생성 파이프라인 (completed 2026-07-30)
- [x] **Phase 2: 인증·회원** - 카카오 로그인, 온보딩, JWT, 관리자 ID/PW 인증, 가입 승인, 회원 관리 (본 작업 완료 2026-08-02 / 검증 갭 클로저 진행 중 — 02-12~02-15) (completed 2026-08-03)
- [ ] **Phase 3: 이용권** - Pass 3종 등록, PassTransaction 이력, 수동 가감·기간 수정, 본인 조회
- [ ] **Phase 4: 시간표·예약** - ClassSchedule/ClassSession, 예약 생성·취소·변경 + 즉시 차감/복구, 동시성 보장, 관리자 예약 관리·휴강, Notification 스키마·알림 레코드 생성
- [ ] **Phase 5: 배치** - 2주 미사용 차감, 유효기간 만료 처리, 멱등 실행
- [ ] **Phase 6: 운영** - 출석 체크, 공지사항, 관리자 알림·활동 피드

## Phase Details

### Phase 1: 기반
**Goal**: 에러 응답 형식, 초기 도메인 스키마, API 계약 재생성 파이프라인이 갖춰져 이후 모든 phase가 그 위에 안전하게 쌓일 수 있다.
**Depends on**: Nothing (첫 phase)
**Requirements**: FOUND-01, FOUND-02, FOUND-03
**Success Criteria** (what must be TRUE):
  1. 존재하지 않는 리소스·잘못된 요청 등 모든 에러 응답이 `application/problem+json`(RFC 9457 ProblemDetail) 형식으로 반환된다 — 스프링 내장 에러(400/404/405)를 포함해 예외 없이
  2. `Branch`, `Member`, `Admin`, `AdminBranch`(다대다) 테이블이 Flyway 마이그레이션으로 생성되어 있고, 이후 도메인 확장을 전제로 핵심 테이블에 `branch_id`를 둘 수 있는 구조다
  3. 한 번의 명령으로 `docs/api/openapi.yaml`이 재생성되어 커밋 가능한 상태가 된다 (springdoc 기반)
**Plans**: 3 plans (D-10에 따라 순차 실행 — 플랜 단위 브랜치 → dev PR → 머지 → 다음 플랜)
- [x] 01-01-PLAN.md — 전역 ProblemDetail 에러 응답 + ErrorCode 레지스트리 (FOUND-01)
- [x] 01-02-PLAN.md — V2 초기 스키마(Branch/Member/Admin/AdminBranch) + JPA 엔티티 (FOUND-02)
- [x] 01-03-PLAN.md — openapi.yaml 재생성 파이프라인 `./gradlew generateApiDocs` (FOUND-03)

### Phase 2: 인증·회원
**Goal**: 회원이 카카오로 가입해 온보딩·관리자 승인을 거쳐 활성 상태가 되고, 관리자는 ID/PW로 로그인해 회원을 관리할 수 있다.
**Depends on**: Phase 1
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06, MEMBER-01, MEMBER-02, MEMBER-03, MEMBER-04
**Success Criteria** (what must be TRUE):
  1. 회원이 카카오 OAuth로 로그인하면 `PENDING` 상태로 가입되고 JWT access/refresh 토큰이 발급되며, refresh로 access를 갱신할 수 있다
  2. 최초 로그인 회원은 온보딩 화면 대상으로 식별되어 실명·전화번호를 필수 입력해야 하고(형식 검증 포함), 온보딩을 완료하지 않고 재로그인해도 다시 온보딩 대상으로 식별된다
  3. 관리자 승인 목록에는 온보딩(실명·전화번호 입력)을 완료한 `PENDING` 회원만 노출되고, 관리자가 승인하면 `ACTIVE`로 전환되어 전체 기능을 쓸 수 있다 (거절도 가능)
  4. 관리자는 ID/PW로 로그인해(카카오 연동 없음, 회원과 동일한 JWT 체계) 회원 목록·상세를 조회하고 이름·전화번호로 검색하며, 회원 상태(`ACTIVE`/`ON_LEAVE`/`INACTIVE`)를 변경할 수 있다
  5. `PENDING`이거나 온보딩 미완료인 회원은 승인 대기 정보 외 기능에 접근할 수 없고, 회원은 본인 프로필(이름·전화번호)을 조회할 수 있다
**Plans**: 15 plans (본 작업 11 + 갭 클로저 4. D-10에 따라 순차 실행 — 플랜 단위 브랜치 → dev PR → 머지 → 다음 플랜. Wave 1의 3개만 병렬 가능)
- [x] 02-01-PLAN.md — 용어·에러코드·설계 결정 문서 정합 + ErrorCode 확장 (AUTH-02/03/04, MEMBER-01/03)
- [x] 02-02-PLAN.md — V3 인증 스키마 + 엔티티·리포지토리 (AUTH-01/03/06, MEMBER-01)
- [x] 02-03-PLAN.md — JWT·카카오 설정, 의존성 1건, 테스트 인프라(Wave 0) (AUTH-01/02)
- [x] 02-04-PLAN.md — TokenService(발급·회전·재사용 감지) + 토큰 갱신·로그아웃 API (AUTH-02)
- [x] 02-05-PLAN.md — JWT 인증 필터 + 역할 기반 인가 + 401/403 ProblemDetail (AUTH-02/04)
- [x] 02-06-PLAN.md — 카카오 인가코드 로그인 + PENDING 회원 생성 (AUTH-01/02/06)
- [x] 02-07-PLAN.md — 관리자 ID/PW 로그인 + 멱등 시드 (AUTH-02/03)
- [x] 02-08-PLAN.md — 온보딩 + 본인 프로필 + 상태 게이트 (AUTH-04/05/06, MEMBER-04)
- [x] 02-09-PLAN.md — 관리자 회원 목록·검색·상세 (MEMBER-01/02)
- [x] 02-10-PLAN.md — 가입 승인·거절 + 상태 변경 + 강제 로그아웃 (MEMBER-01/03)
- [x] 02-11-PLAN.md — phase 마감: 전체 검증·문서 정합 + 실제 카카오 E2E 수동 확인 (전 요구사항)
- [x] 02-12-PLAN.md — [갭] CR-01: 동시 최초 로그인 경쟁 복구를 트랜잭션 밖으로 + 동시성 테스트 (AUTH-01)
- [x] 02-13-PLAN.md — [갭] WR-01: refresh 회전 폐기 원자화(조건부 UPDATE) + 재사용 감지 폐기 커밋 (AUTH-02)
- [x] 02-14-PLAN.md — [갭] WR-03/04/05: 상태변경 ACTIVE 우회 차단 + 검색어·온보딩 판정 정합 (MEMBER-01/02/03)
- [x] 02-15-PLAN.md — [갭] WR-06: 회원 목록 쿼리 파라미터를 개별 파라미터로 기술(@ParameterObject) (MEMBER-02)

**Note**: `src/main/kotlin/com/goldwrestling/config/SecurityConfig.kt`의 현재 전체 `permitAll` 뼈대는 이 phase에서 실제 인가 규칙으로 교체된다 (02-05).

**Note (갭 클로저)**: 02-VERIFICATION.md가 Blocker 1건(CR-01)을 남겨 Phase 2를 `gaps_found`로 판정했다. 02-12~02-15는 그 Blocker와 02-REVIEW.md의 Warning 5건을 닫는 플랜이다. WR-02(관리자 로그인 타이밍 부채널)와 Info 6건(IN-01~IN-06)은 이번 갭 클로저 범위 밖으로 두었다 — 사용자 판단 사항.

### Phase 3: 이용권
**Goal**: 관리자가 회원에게 이용권을 등록·조정하고, 모든 변경이 감사 가능한 이력으로 남으며, 회원이 본인 이용권 현황을 확인할 수 있다.
**Depends on**: Phase 2 (이용권은 승인된 회원을 전제)
**Requirements**: PASS-01, PASS-02, PASS-03, PASS-04, PASS-05, PASS-06, PASS-07, PASS-08
**Success Criteria** (what must be TRUE):
  1. 관리자가 회원에게 저녁반 회비(1/3/6개월)·예약제 횟수권·1:1 레슨권을 시작일 지정(기본 오늘, 과거 허용)으로 등록할 수 있고, 횟수권의 유효기간은 시작일로부터 1년으로 자동 설정되며, 초기 횟수 부여도 `INITIAL_GRANT` 이력으로 남는다 (D-055)
  2. 관리자가 사유를 입력해 잔여 횟수를 수동 가감하면(`ADMIN_ADJUST`) 즉시 반영되고, 모든 차감/복구가 `PassTransaction`(이용권/±수량/사유/주체/시각) 이력으로 남는다 — 이력 없는 잔여 변경은 불가능하다
  3. 관리자가 저녁반 회비 기간과 횟수권 유효기간을 수정할 수 있고, 모든 기간 변경이 `PassPeriodChange`(전값/후값/사유/주체/시각) 이력으로 남는다 (D-056·D-057)
  4. 회원이 본인이 보유한 모든 이용권의 잔여 횟수·유효기간을 조회할 수 있다 — 만료·소진 이용권 포함(상태 구분), 취소된 이용권 제외 (D-058)
  5. 회원이 본인의 차감/복구 이력(시각·사유·수량)을 조회할 수 있다
  6. 관리자가 이용권 등록을 취소하면 취소 상태 전환 + `REGISTRATION_CANCELED` 상쇄 이력이 남고, 취소된 이용권은 회원 조회에서 숨겨지고 관리자 화면에서는 구분 표시된다 (D-059)
**Plans**: 11 plans (D-10에 따라 순차 실행 — 플랜 단위 브랜치 → dev PR → 머지 → 다음 플랜. Wave 5의 03-05·03-06만 병렬 가능)
- [x] 03-01-PLAN.md — 용어·에러코드·설계 결정 문서 정합 + PassExceptions (PASS-01/03/07/08, 경계 산정 체크포인트)
- [x] 03-02-PLAN.md — V4 이용권 스키마 + enum·엔티티·리포지토리(조건부 UPDATE) (PASS-01/02/03/04/07/08)
- [x] 03-03-PLAN.md — [TDD] 수동 가감 정책 `Pass.validateAdjustment` (PASS-03)
- [ ] 03-04-PLAN.md — [TDD] 등록 유효기간 계산 + 표시 상태 계산 (PASS-01/05)
- [ ] 03-05-PLAN.md — [TDD] 기간 변경 판정 + 취소 상쇄 산출 (PASS-04/07/08)
- [ ] 03-06-PLAN.md — 관리자 이용권 등록 API + INITIAL_GRANT 이력 (PASS-01/02)
- [ ] 03-07-PLAN.md — 관리자 수동 가감 API + "잔여 = 이력 합계" 불변식 테스트 (PASS-02/03)
- [ ] 03-08-PLAN.md — 기간·유효기간 수정 통합 API + PassPeriodChange 이력 (PASS-04/07)
- [ ] 03-09-PLAN.md — 등록 취소 API + 관리자 이용권 목록(취소 구분) (PASS-08/02)
- [ ] 03-10-PLAN.md — 회원 본인 이용권·이력 조회 (PASS-05/06)
- [ ] 03-11-PLAN.md — phase 마감: 전체 검증·문서 정합 + 관리자 흐름 수동 확인 (PASS-01~08)

**Note (API 표면)**: 이 phase가 여는 엔드포인트는 7개다 — `POST /api/admin/members/{memberId}/passes`,
`GET /api/admin/members/{memberId}/passes`, `POST /api/admin/passes/{passId}/adjustments`,
`PATCH /api/admin/passes/{passId}/period`, `POST /api/admin/passes/{passId}/cancellation`,
`GET /api/members/me/passes`, `GET /api/members/me/pass-transactions`.
관리자 이용권 목록 조회는 요구사항 목록(PASS-01~08)에는 없지만 **성공기준 6의 "관리자 화면에서 구분 표시"**를
FE가 구현할 수 있게 하는 데이터 경로라 03-09에 포함했다.

**Note (Phase 4·5·6와의 경계)**: `TransactionReason` 8종을 모두 선언하되 이 phase가 실제로 쓰는 것은
`INITIAL_GRANT`·`ADMIN_ADJUST`·`REGISTRATION_CANCELED` 3종이다. `RESERVE`/`CANCEL_REFUND`/`CLASS_CANCELED_REFUND`는
Phase 4, `INACTIVITY`는 Phase 5, `EVENING_HALF`는 Phase 6이 쓴다. `PassTransaction`의 주체 컬럼은
`admin_id NOT NULL` 하나만 둔다 — Phase 4가 회원 주체를 추가할 때 새 마이그레이션으로 nullable 컬럼을 붙인다(D-030).

### Phase 4: 시간표·예약
**Goal**: 회원이 주간 시간표를 보고 예약제 수업·1:1 레슨을 예약·취소·변경하면 이용권이 즉시 차감/복구되고, 동시 경쟁 상황에서도 정원을 초과하지 않으며, 관리자가 시간표와 예약 전반을 운영한다.
**Depends on**: Phase 3 (예약은 이용권 잔여를 전제)
**Requirements**: SCHED-01, SCHED-02, SCHED-03, RESV-01, RESV-02, RESV-03, RESV-04, RESV-05, RESV-06, RESV-07, RESV-08, RESV-09, NOTIF-01
**Success Criteria** (what must be TRUE):
  1. 회원은 해당 주(월요일에 오픈되는 월~일, 다음 주는 조회만 가능하고 예약 불가)의 시간표에서 예약제 수업(SESSION)을 예약하면 `SESSION_PASS`에서 즉시 1회 차감되고, 잔여가 부족하면(예: 0.5회로 1회짜리 예약) 예약이 거부된다
  2. 회원은 1:1 레슨(LESSON)을 타임당 1명 한도로 예약하면 `LESSON_PASS`에서 즉시 1회 차감된다
  3. 회원은 당일이 아닌 예약을 취소(즉시 복구)하거나 변경(취소+재예약)할 수 있고, 당일에는 취소·변경 모두 거부된다. 본인 예약 목록을 조회할 수 있다
  4. 정원 마지막 자리·1:1 슬롯에 여러 명이 동시에 예약을 요청하면, 정확히 정원 수만큼만 성공하고 나머지는 정원 초과 에러를 받는다(초과 예약 0건) — 성공 건수·DB 예약 행 수·`PassTransaction` 이력 건수가 서로 일치한다
  5. 관리자는 주간 스케줄 보드(요일×타임 그리드: 수업 종류·예약 n/정원·1:1 여부·셀별 예약자 명단)와 모든 회원의 예약을 조회하고, 당일 포함 제약 없이 예약을 대리 취소(복구 여부 선택, 기본 복구)·변경할 수 있으며, 특정 날짜 수업을 휴강 처리하면 해당 예약이 전부 자동 취소+차감 복구(`CLASS_CANCELED_REFUND`)+알림 생성되고 그 타임은 예약 불가로 표시된다
**Plans**: TBD — 규모가 커서 여러 플랜으로 분할 필요 (예: 시간표/세션 실체화 → 예약 생성/취소/변경+차감·복구 → 동시성 보장+동시성 테스트 → 관리자 스케줄 보드·대리 취소/변경·휴강)

**Note (Phase 6와의 경계)**: Notification은 이 phase에서 **스키마 마이그레이션과 알림 레코드 생성(NOTIF-01)까지만** 구현한다 — 예약 생성/변경/취소·휴강 이벤트가 모두 이 phase에서 발생하기 때문. 알림 조회·확인 처리·폴링 API·활동 피드(NOTIF-02, NOTIF-03)는 Phase 6에서 구현한다.

### Phase 5: 배치
**Goal**: 이용권을 오래 쓰지 않은 회원이 정책대로 자동 차감되고, 유효기간이 지난 이용권은 사용 불가 처리되며, 배치가 며칠씩 중복 실행돼도 이중 차감이 없다.
**Depends on**: Phase 3 (Pass), Phase 4 (예약/차감 흐름과의 정합성)
**Requirements**: BATCH-01, BATCH-02, BATCH-03, BATCH-04
**Success Criteria** (what must be TRUE):
  1. `SESSION_PASS`가 기준일(마지막 출석일과 마지막 예약의 수업일 중 더 최근 날짜, 둘 다 없으면 등록일 — D-027) 기준 2주 동안 미사용이면 1회 자동 차감되고, 이후 2주마다 반복 차감되며 이력이 `INACTIVITY` 사유로 남는다
  2. `ON_LEAVE` 기간, 잔여 0, 유효기간 만료된 이용권은 자동 차감 대상에서 제외된다
  3. 등록일로부터 1년이 지난 이용권은 사용 불가로 처리되어 더 이상 예약에 쓸 수 없다
  4. 같은 날 배치를 두 번 이상 실행해도 이중 차감이 발생하지 않는다(멱등) — 매일 새벽 실행을 전제로 검증된다
**Plans**: TBD

**Note**: 기준일(policies §4.3, D-027)은 **마지막 출석일과 마지막 예약의 수업일 중 더 최근 날짜**다. 출석(`Attendance`)은 Phase 6 소관이라 이 phase 시점에는 출석 이력이 없으므로, 기준일은 **마지막 예약의 수업일**(Phase 4 데이터) 또는 **등록일**(fallback)로 동작하며 이는 의도된 동작이다. 기준일 조회 쿼리가 `Attendance` 테이블을 필요로 하면 이 phase에서 해당 스키마를 먼저 마이그레이션할 수 있다 (Phase 6에서 재사용).

### Phase 6: 운영
**Goal**: 관리자가 모든 수업의 출석을 체크하고 공지사항을 운영하며, 예약 관련 이벤트를 알림·활동 피드로 실시간에 가깝게 확인할 수 있다.
**Depends on**: Phase 4 (수업/예약 데이터 + Notification 스키마·알림 레코드), Phase 5 (Attendance 스키마가 먼저 만들어졌다면 재사용)
**Requirements**: ATTEND-01, ATTEND-02, NOTICE-01, NOTICE-02, NOTIF-02, NOTIF-03
**Success Criteria** (what must be TRUE):
  1. 관리자는 저녁반/예약제/1:1 모든 수업의 타임별 출석을 체크할 수 있고, 이 기록은 차감에 영향을 주지 않는 참고용 데이터로 남는다 (예약했지만 불참해도 차감은 유지)
  2. 관리자는 `SESSION_PASS` 보유 회원의 저녁반 참여를 확인 후 0.5회 수동 차감(`EVENING_HALF`)할 수 있다 — 잔여가 0.5 이상일 때만 가능하다
  3. 관리자는 공지사항을 등록·수정·삭제할 수 있고, 회원은 공지 목록·상세를 열람할 수 있다
  4. 관리자는 30초 폴링으로 알림 목록과 미확인 카운트를 조회하고 확인 처리할 수 있다 — 알림 레코드 자체는 Phase 4에서 생성된다 (NOTIF-01, 예약 생성/변경/취소·휴강 이벤트)
  5. 관리자는 최근 예약 이벤트 타임라인(활동 피드, 알림과 동일 데이터의 다른 뷰)을 조회할 수 있다
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. 기반 | 3/3 | Complete   | 2026-07-30 |
| 2. 인증·회원 | 15/15 | Complete   | 2026-08-03 |
| 3. 이용권 | 3/11 | In Progress|  |
| 4. 시간표·예약 | 0/TBD | Not started | - |
| 5. 배치 | 0/TBD | Not started | - |
| 6. 운영 | 0/TBD | Not started | - |
</content>
