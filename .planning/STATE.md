---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Completed 04-15-PLAN.md — Phase 4(시간표·예약) 15/15 plans 완료
last_updated: "2026-08-08T07:45:12.652Z"
last_activity: 2026-08-08
progress:
  total_phases: 6
  completed_phases: 4
  total_plans: 44
  completed_plans: 44
  percent: 67
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-07-30)

**Core value:** 회원이 보는 잔여 횟수는 항상 실제 사용 가능 횟수와 일치한다 (즉시 차감/복구 + 전 이력 + 초과 예약 0건)
**Current focus:** Phase 04 — schedule-reservation (완료, 청크 C PR 대기)

## Current Position

Phase: 04 (schedule-reservation) — COMPLETE (15/15 plans)
Plan: 15 of 15
Status: Phase complete — dev PR 생성 대기 (청크 C `feature/phase-04c-admin-ops`)
Last activity: 2026-08-08 -- Phase 4 execution complete, Task 3 human-verify 16/16 PASS

Progress: [██████████] 100%

## Performance Metrics

**Velocity:**

- Total plans completed: 29
- Average duration: -
- Total execution time: 0h

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 3 | - | - |
| 02 | 15 | - | - |
| 3 | 11 | - | - |

**Recent Trend:**

- Last 5 plans: -
- Trend: -

*Updated after each plan completion*
| Phase 01 P01 | 45min | 3 tasks | 7 files |
| Phase 01-foundation P02 | 15min | 2 tasks | 6 files |
| Phase 01-foundation P03 | 25min | 3 tasks | 5 files |
| Phase 04 P01 | 25min | 2 tasks | 9 files |
| Phase 04 P02 | 30min | 3 tasks | 5 files |
| Phase 04 P03 | ~20min | 3 tasks | 18 files |
| Phase 04 P04 | 15min | 2 tasks | 4 files |
| Phase 04 P06 | ~20min | 2 tasks | 4 files |
| Phase 04 P07 | 25min | 2 tasks | 7 files |
| Phase 04 P09 | ~15min | 2 tasks | 4 files |
| Phase 04 P08 | 20min | 2 tasks | 3 files |
| Phase 04 P10 | 60min | 3 tasks | 10 files |
| Phase 04 P11 | 35min | 2 tasks | 16 files |
| Phase 04 P12 | 40min | 2 tasks | 9 files |
| Phase 04 P13 | 65min | 2 tasks | 9 files |
| Phase 04 P14 | 55min | 2 tasks | 10 files |
| Phase 04 P15 | ~30min | 2 tasks | 3 files |

## Accumulated Context

### Roadmap Evolution

- Phase 3 edited: edited fields: requirements(PASS-07·08 추가), success_criteria(1·3·4 보강, 6 신설 — D-055~D-059 반영)

### Decisions

Decisions are logged in PROJECT.md Key Decisions table (도메인·기술 결정 원본은 docs/decisions.md).
Recent decisions affecting current work:

- 로드맵 수립: 사용자 지정 M1~M6 마일스톤 구조(기반→인증·회원→이용권→시간표·예약→배치→운영)를 그대로 따름, 재구성하지 않음
- Phase 5(배치)의 "마지막 출석일" 기준은 Phase 6(출석) 데이터 도입 전까지 등록일 fallback으로 동작 — Attendance 스키마는 필요시 Phase 5에서 선반영 가능
- Phase 2에서 `SecurityConfig`의 현재 전체 permitAll 뼈대를 실제 인가 규칙으로 교체 예정
- [Phase 01]: ErrorCode enum이 defaultStatus를 직접 보유해 코드-HTTP상태 매핑을 코드 안에 고정 (D-028) — 문서(error-codes.md)와 코드가 갈라지는 것을 방지
- [Phase 01]: admin_branch는 서로게이트 PK(id) + UNIQUE(admin_id, branch_id)로 설계 — 복합 PK 대신 add-migration §2 'PK는 항상 id' 관례와 일관성 유지
- [Phase 01]: created_at은 Phase 1 엔티티에 매핑하지 않음 — 첫 INSERT 경로가 없어 Clock 빈 기반 감사 시각 전략은 Phase 2에서 결정
- [Phase 01-foundation]: D-029: springdoc gradle 플러그인 대신 커스텀 Exec 태스크 체인(generateApiDocs)으로 openapi.yaml 재생성 — 플러그인이 Boot 4 Gradle 플러그인과 캐스트 충돌/configuration cache 비호환 이슈 미해결 — 성공 경로 3.9초·실패 경로 62초(DB 다운, 좀비 프로세스 0건)를 로컬에서 실제로 검증
- [Phase 04]: docs/decisions.md 번호를 D-085~094 대신 D-089~098로 순연(FE M3 결정이 D-085~088을 이미 선점) — PLAN.md가 가정한 '마지막 결정 D-084'가 실행 시점에는 이미 D-088까지 진행돼 있었다. 내용·순서는 그대로 유지하고 번호만 순연했다.
- [Phase 04-02]: V6 4테이블(class_schedule/class_session/reservation/notification)을 한 마이그레이션에 담고, 부분 유니크 인덱스 3종·CHECK 5종으로 초과 예약 0건을 DB 수준에서 강제(D-021 확장)
- [Phase 04-02]: V8로 pass_transaction에 member_id를 추가하고 ck_pass_transaction_subject로 admin/member 중 정확히 하나만 주체가 되도록 강제 — 회원 셀프 예약/취소 이력 기록 경로 확보(D-030 이행)
- [Phase 04-03]: ClassSession.status/reservedCount는 기본값 없는 생성자 필수 파라미터로 선언 — 판정 전용(D-072) grep 검사를 확실히 통과시키고 Pass.kt 관례와 일관성 유지
- [Phase 04-03]: PassTransaction.member 추가 시 기본값을 주지 않고 모든 호출부(AdminPassService 3곳 + 테스트 1곳)에 member=null을 명시 — PLAN.md가 4곳을 모두 AdminPassService로 가정했으나 실제로는 테스트 파일 1곳 포함
- [Phase 04-04]: WeekRange는 값 객체라 data class로 정의 — 엔티티 data class 금지 규약(conventions §3)은 JPA 엔티티 전용, WeekRange는 테스트 equals 비교가 필요해 예외
- [Phase 04-04]: 조회범위(14일)는 WeekRange를 재사용하지 않고 ReservationWindow.ViewableRange로 분리 — glossary가 WeekRange를 7일 범위로 명시했기 때문
- [Phase 04-06]: ReservationPassPolicy를 reservation 패키지에 배치해 schedule이 pass를 참조하지 않도록 의존 방향을 reservation→schedule·reservation→pass 두 갈래로 유지(D-091)
- [Phase 04-06]: 리포지토리 @Query 메서드의 RED는 어서션 실패 대신 시그니처만 선언해 Spring Data 파생 쿼리 파싱 실패(PropertyReferenceException)로 컨텍스트 기동이 실패하는 것으로 확보 — 이 저장소 최초 사례
- [Phase 04-07]: MemberReservationServiceTest는 클래스 레벨 @Transactional을 배제 - reserve() 실패 시 실제 롤백된 DB 상태를 검증하려면 각 호출이 독립 트랜잭션이어야 한다 — 테스트가 @Transactional이면 참여 트랜잭션의 rollback-only 마킹이 테스트 종료 시점까지 실제 반영되지 않아 검증이 불가능하다
- [Phase 04-07]: 취소·변경 알림 문구는 PLAN.md가 예시로 지정한 예약/휴강 알림 톤을 따라 직접 작성 — 문구 자체는 도메인 규칙이 아니라 표시 문자열이라 Rule 4 대상이 아니다
- [Phase 04-09]: assertCancelableByMember는 당일·과거를 !classDate.isAfter(today) 한 조건으로 함께 거부한다 — 지난 수업을 당일보다 강하게 막는다는 취지를 유지하면서 판정 로직을 단일 부등식으로 단순화
- [Phase 04-08]: 409 코드 5종 구성에 CLASS_SESSION_CANCELED(휴강)를 추가 — behavior 목록(4종)만으로는 acceptance_criteria가 요구한 5종을 채울 수 없어 컨트롤러 action 섹션이 이미 언급한 다섯 번째 코드를 테스트에 반영
- [Phase 04-08]: classScheduleId 누락 시 기대 코드를 VALIDATION_FAILED→MALFORMED_REQUEST로 정정 — Kotlin non-null 생성자 파라미터라 Jackson 역직렬화가 @Valid보다 먼저 실패(AdminPassControllerTest 선례와 동일)
- [Phase 04-10]: 취소·변경 응답 재조회는 항상 findByIdAndMemberId — bare findById(Reservation)를 회원 경로 어디에서도 쓰지 않아 IDOR 방어가 조회 시그니처 수준에서 끝까지 일관된다
- [Phase 04-10]: LikePatternEscaper를 common으로 승격 — MemberSpecifications의 private LIKE 이스케이프 로직을 ReservationSpecifications.memberKeywordContains가 재구현 없이 재사용(PageResponse KDoc이 예고한 승격 트리거 충족)
- [Phase 04-11]: branchId 해석은 admin_branch 매핑 우선, 없으면(v1 미도입) 단일 지점으로 대체(D-101)
- [Phase 04-11]: ScheduleService.getWeeklySchedule을 순수 리팩터링해 ScheduleGridSkeleton(관리자용과 공유하는 그리드 조립 헬퍼)을 도입, 기존 테스트로 행위 불변 확인
- [Phase 04-12]: 관리자 예약 조회는 branchId 스코프를 받지 않는다 — RESV-07 결정과 AdminMemberController 원본 인터페이스를 따름(AdminBranch 매핑은 v1 미도입, D-101)
- [Phase 04-12]: ReservationRepository.findAll(Specification, Pageable)을 @EntityGraph로 재선언 — Specification 페이지 조회에서 ManyToOne LAZY 연관을 count 쿼리에 영향 없이 N+1 없이 로딩하는 이 저장소 최초 패턴
- [Phase 04-13]: 예약 생성/취소 복구 실행부를 회원·관리자 경로가 공유하도록 ReservationLedgerSupport 컴포넌트로 추출 — 차감/복구 경로가 두 서비스에 각자 복제되면 D-021(모든 잔여 변경이 이력을 남긴다) 보장이 흩어진다
- [Phase 04-13]: AdminPassService.cancel의 활성 예약 선행 검사는 이용권 조회 직후, 다른 판정보다 먼저 수행 — 조건부 UPDATE 이후에 두면 거부 사유가 경쟁 패배·잔여 충돌 등 다른 실패와 뒤섞인다(D-089)
- [Phase 04-14]: ReservationLedgerSupport.restoreAfterCancellation를 restorePassAfterCancellation(세션 정원 미반영 + reason 파라미터)로 감싸는 형태로 리팩터링 — 기존 회원/관리자 취소 호출부는 동작 불변(기본값 CANCEL_REFUND), 휴강 캐스케이드는 CLASS_CANCELED_REFUND로 호출해 원장에서 구분(T-04-67)
- [Phase 04-14]: AdminScheduleController의 클래스 레벨 @RequestMapping을 /api/admin/schedule에서 /api/admin으로 넓히고 메서드마다 하위 경로를 붙임 — 스케줄 보드와 휴강 처리가 서로 다른 리소스 계층이라 한 컨트롤러 파일 안에서 고정 경로 두 갈래를 표현하기 위한 최소 변경
- [Phase 04-14]: ClassSessionNotFoundException + ErrorCode.CLASS_SESSION_NOT_FOUND 신설 — 휴강 해제 대상 세션 id가 없는 경로를 plan이 명시하지 않았으나 404 처리 없이는 500이 노출되는 방어적이지 않은 API가 된다
- [Phase 04-15]: docs/decisions.md(D-089~101)·docs/glossary.md는 이미 실제 구현과 일치해 phase 마감 시점에 추가 수정 없음 — error-codes.md 발생 지점 열만 실제 throw 지점 기준으로 정정
- [Phase 04-15]: Task 3 회원 예약~관리자 운영 전체 흐름 검증(16항목)은 오케스트레이터가 실제 HTTP·psql로 판정하고 사용자가 승인하는 방식으로 수행 — 16/16 PASS, 로컬 검증 데이터는 사용자 지시로 보존

### Pending Todos

None yet.

### Blockers/Concerns

- REQUIREMENTS.md 문서 상단의 "v1 requirements: 36 total" 표기가 실제 v1 목록(FOUND~NOTIF, 42건)과 불일치했음. 로드맵 작성 시 실제 목록 42건 전부를 매핑하고 Coverage 섹션을 42로 정정함 — 원 문서(docs/)와의 스펙 차이가 아니라 REQUIREMENTS.md 자체의 집계 오류로 판단.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260806-und | D-083 카카오 프로필(닉네임·프로필 이미지) 수집·저장 및 MyProfileResponse 노출 | 2026-08-06 | 7f363a9 | [260806-und-d-083-myprofileresponse](./quick/260806-und-d-083-myprofileresponse/) |

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| *(none)* | | | |

## Session Continuity

Last session: 2026-08-08T07:44:51.549Z
Stopped at: Completed 04-15-PLAN.md — Phase 4(시간표·예약) 15/15 plans 완료, dev PR 생성 대기
Resume file: None
