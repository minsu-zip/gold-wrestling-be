---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: 배포·운영
status: executing
stopped_at: Completed 07-05-PLAN.md (wave 3 done — 07-06 wave 4 remains)
last_updated: "2026-09-09T01:23:36.000Z"
last_activity: 2026-09-09
progress:
  total_phases: 4
  completed_phases: 0
  total_plans: 6
  completed_plans: 5
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-08)

**Core value:** 회원이 보는 잔여 횟수는 항상 실제 사용 가능 횟수와 일치한다 (즉시 차감/복구 + 전 이력 + 초과 예약 0건)
**Current focus:** Phase 07 — container-server-setup

## Current Position

Phase: 07 (container-server-setup) — EXECUTING
Plan: 5 of 6
Status: Ready to execute
Last activity: 2026-09-09

## Performance Metrics

**Velocity:**

- Total plans completed: 40
- Average duration: -
- Total execution time: 0h

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 3 | - | - |
| 02 | 15 | - | - |
| 3 | 11 | - | - |
| 06 | 11 | - | - |

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
| Phase 05-batch P01 | 15min | 3 tasks | 5 files |
| Phase 05-batch P02 | 35min | 3 tasks | 11 files |
| Phase 05-batch P03 | 25min | 2 tasks | 2 files |
| Phase 05-batch P04 | 50min | 2 tasks | 9 files |
| Phase 05-batch P05 | 55min | 1 tasks | 3 files |
| Phase 05-batch P06 | 35min | 1 tasks | 2 files |
| Phase 05-batch P07 | ~40min | 2 tasks | 2 files |
| Phase 05-batch P15 | 75min | 3 tasks | 12 files |
| Phase 05-batch P08 | 45min | 3 tasks | 7 files |
| Phase 05-batch P10 | 25min | 2 tasks | 6 files |
| Phase 05-batch P11 | 30min | 3 tasks | 12 files |
| Phase 5 P12 | 15min | 3 tasks | 10 files |
| Phase 5 P13 | 25min | 3 tasks | 5 files |
| Phase 05-batch P14 | 30min | 2 tasks | 11 files |
| Phase 06-operations P01 | 15min | 2 tasks | 6 files |
| Phase 06-operations P02 | 35min | 3 tasks | 9 files |
| Phase 06-operations P03 | ~30min | 2 tasks | 2 files |
| Phase 06-operations P04 | ~50min | 3 tasks | 10 files |
| Phase 06-operations P05 | ~25min | 3 tasks | 5 files |
| Phase 06-operations P06 | 15min | 3 tasks | 6 files |
| Phase 06-operations P07 | ~30min | 3 tasks | 2 files |
| Phase 06-operations P08 | 25min | 3 tasks | 4 files |
| Phase 06-operations P09 | ~30min | 3 tasks | 6 files |
| Phase 06-operations P10 | ~20min | 3 tasks | 7 files |
| Phase 06-operations P11 | ~35min | 3 tasks | 3 files |
| Phase 07-container-server-setup P01 | 45min | 3 tasks | 4 files |
| Phase 07 P02 | 45min | 3 tasks | 3 files |
| Phase 07 P03 | 60min | 3 tasks | 3 files |
| Phase 07 P04 | 40min | 3 tasks | 3 files |
| Phase 07 P05 | 25min | 3 tasks | 1 files |

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
- [Phase 05-01]: D-110~D-114: 배치 CHECK 완화·복귀 시각 컬럼·트랜잭션 경계·실행 이력 스키마·cron/API 경로 확정 (05-01)
- [Phase 05-01]: docs/policies.md §4.3, docs/decisions.md D-105·D-108 보강 문구는 discuss-phase에서 작성됐으나 미커밋 상태였던 것을 05-01에서 함께 커밋
- [Phase 05-02]: PassRepositoryTest의 '둘 다 비어 있으면 실패' 테스트를 '시스템 주체로 성공'으로 교체 — V9이 그 행동 자체를 바꿨으므로 옛 단언을 남겨두면 틀린 것을 검증하는 통과 테스트가 될 위험이 있었다
- [Phase 05-02]: AdminMemberService 생성자에 Clock 추가는 별도 이관 작업 없이 안전 — 저장소 전체에서 수동 인스턴스화 호출부가 없어 Spring DI가 자동으로 새 파라미터를 채운다
- [Phase 05-02]: 재복귀 테스트에서 같은 트랜잭션 내 findById 1차 캐시로 엔티티 참조가 재사용되는 버그를 발견해 스냅샷 값 비교로 수정(Rule 1)
- [Phase 05-batch]: ktlintFormat 결과를 별도 style 커밋으로 분리 — 동작 변경과 자동 포맷 정렬을 한 커밋에 섞지 않는다
- [Phase 05-batch]: InactivityDueDateCalculator KDoc의 'Clock' 문자열이 acceptance grep과 충돌해 '시각 주입 빈'으로 표현 변경
- [Phase 05-batch]: D-115: 배치 벌크 조회는 Array<Any> 캐스팅 대신 인터페이스 스칼라 프로젝션(common/projection)으로 반환 — 타입 안전성을 이 저장소 관례로 고정
- [Phase 05-batch]: CANCELED Pass 픽스처는 상태를 직접 대입하지 않고 PassRepository.cancelIfNotCanceled 실제 취소 경로로 만든다 — ck_pass_cancellation(V4)이 취소 메타데이터 완전성을 강제
- [Phase 05-05]: 잔여 0인 SESSION_PASS 테스트 픽스처는 INITIAL_GRANT 이력을 생성하지 않는다 — ck_pass_transaction_amount_nonzero(V4)가 금액 0인 이력을 거부한다
- [Phase 05-05]: 취소된 SESSION_PASS 픽스처는 cancelIfNotCanceled 대신 취소 메타데이터를 채운 Pass를 직접 saveAndFlush한다 — 커스텀 @Modifying 쿼리는 명시적 @Transactional 없이 호출하면 기본 readOnly 트랜잭션이 붙어 flush가 실패한다
- [Phase 05-05]: Mockito 5는 Kotlin non-null 인터페이스 파라미터에 any()/anyLong() 매처를 쓰면 NPE를 던진다 — 매처 없이 실제 값을 그대로 인자로 넘겨 우회한다
- [Phase 05-06]: repeat+return@repeat 대신 for+break로 부족분 루프 구현 — return@repeat은 continue 의미라 대상 소진 후에도 반복이 계속돼 'skippedCount 1건만 증가하고 중단'이라는 명시된 behavior를 만족하지 못한다
- [Phase 05-06]: InactivityBatchRunner KDoc의 '@Transactional' 리터럴이 acceptance grep(0건 기대)과 충돌해 '트랜잭션 애노테이션'으로 표현 변경 — 05-03·05-04의 동일 유형 충돌과 같은 해결
- [Phase 05-07]: ADMIN_ADJUST 픽스처는 배치 차감(조건부 UPDATE) 이후 낡은 메모리 참조가 아니라 DB 재조회 값 위에 가감한다 — 재조회 없이 가감하면 이미 반영된 배치 차감분이 되살아난다
- [Phase 05-07]: 만료 검증 테스트 @AfterEach 정리 순서에 pass_period_change 삭제를 pass 삭제보다 앞에 추가 — AdminPassService.changePeriod가 남기는 이력(D-057)이 FK로 남아 있으면 pass 삭제가 실패한다
- [Phase 05-batch]: resolveTriggeredBy 두 예외는 HTTP 경로에서 도달 불가 — requireNotNull 분기는 requireAdminId()의 non-null Long 반환으로 컴파일 타임 차단, 관리자 조회 실패 분기는 JwtAuthenticationFilter가 매 요청 관리자 존재를 먼저 검증해 401로 차단(관리자 삭제 기능 자체가 없음). 새 에러코드는 추가하지 않는다(D-114와 일치)
- [Phase 05-batch]: AdminBatchControllerTest는 클래스 레벨 @Transactional을 쓰지 않는다 — 연속 2회 호출이 서로 다른 물리 트랜잭션으로 커밋돼야 D-106 멱등성을 HTTP 레벨에서 실증할 수 있다. 컨트롤러 테스트 clock 리셋은 Instant.now()를 쓴다 — NimbusJwtDecoder가 시스템 시각으로 exp를 검증해 과거 고정 clock이면 발급 토큰이 즉시 401 처리된다
- [Phase 05-10]: CR-04: returnedFromLeaveAt 기록 조건을 previousStatus==ON_LEAVE && newStatus!=ON_LEAVE로 확장 — ON_LEAVE→INACTIVE→ACTIVE 우회 복귀 경로의 소급 차감을 막는다
- [Phase 05-10]: docs/policies.md §4.3·glossary.md·decisions.md D-105/D-111을 CR-04 수정에 맞춰 같은 작업 안에서 정정 — 커밋된 V9 마이그레이션 주석은 그대로 두고 정정 사실은 V10 헤더로 미룬다
- [Phase 05-11]: D-117: 배치 실행 직렬화는 batch_execution RUNNING 행 + status='RUNNING' 부분 유니크 인덱스(V10)로 한다 — advisory lock·원장 주기 유니크 인덱스·회원 행 비관적 락 기각
- [Phase 05-11]: BatchExecution의 triggeredBy(@ManyToOne LAZY)를 triggeredByAdminId 스칼라로 교체 — 엔티티에 LAZY 연관이 0개가 되어 트랜잭션 밖 응답 변환이 조건 없이 안전해진다(WR-04)
- [Phase 05-11]: BatchExecutionRepositoryTest는 @AfterEach 삭제 대신 클래스 레벨 @Transactional 롤백에 정리를 맡긴다 — 유니크 위반 단언 직후 PostgreSQL 트랜잭션이 abort 상태라 @AfterEach의 DELETE가 그 테스트를 실패시킨다
- [Phase 5]: D-118: 배치 중복 실행은 409 BATCH_ALREADY_RUNNING으로 거부한다 — D-114의 '새 에러코드는 추가하지 않는다'를 철회 — 근거였던 '거부할 요청이 없다'는 전제가 CR-01(동시 호출 이중 차감)로 깨졌다
- [Phase 5]: BatchExecutionRecorder는 별도 스프링 빈 + REQUIRES_NEW로 시작·종료를 기록한다 — 러너에 @Transactional을 붙이면 실패 격리(D-112)가 깨지고, 같은 클래스 내부 호출은 프록시를 우회해 트랜잭션 경계가 생기지 않는다. REQUIRED로 두면 RUNNING 행이 호출부 종료까지 커밋되지 않아 직렬화가 성립하지 않는다
- [Phase 5]: D-108 해소: RUNNING 부분 유니크 인덱스 + 409 거부로 배치 동시 실행이 안전해졌다 — 분산 락 미도입 근거를 '단일 인스턴스'에서 'DB 제약이 인스턴스 수와 무관하게 막는다'로 교체
- [Phase 5]: D-112 보강: 러너에는 @Transactional을 붙이지 않고 실행 이력의 시작·확정만 BatchExecutionRecorder의 REQUIRES_NEW에서 처리한다
- [Phase 5]: D-119: 미사용 차감에 정책 시행일 하한(기본 2026-09-01)과 1회 실행 상한(기본 1)을 둔다 — 둘 다 설정값이라 재배포 없이 되돌릴 수 있다
- [Phase 5]: 테스트 전역 시행일을 2000-01-01로 고정한다 — 고정하지 않으면 배치 테스트가 '차감 0'을 검증하는 빈 껍데기가 되면서 초록불로 통과한다
- [Phase 05-16]: 전체 회귀(./gradlew cleanTest test 763건, 0 failures) + ./gradlew build 모두 캐시 없이 그린. "동시 실행은 아직 안전하지 않다"·"응답이 오지 않아도 재호출하지 않는다"·"차감 원자성 보장은 갭 클로저에서 정한다"·"ON_LEAVE→ACTIVE 복귀일" 서술이 src/·docs/ 전체에서 0건임을 grep으로 확인
- [Phase 05-16]: REQUIREMENTS.md BATCH-01·02·04를 Complete로 전환 — BATCH-01은 CR-03(출석일 후보 부재, Phase 6 범위)이 열려 있는 동안 운영 배포는 cron을 꺼 둔 채 한다는 조건을 함께 명시. 사용자 로컬 실기동 확인(Task 2)은 아직 미완료이므로 05-16 플랜 자체는 완료 처리하지 않음
- [Phase 06-01]: D-132~D-135: 출석 건별 upsert, EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE(409) 신설, 공지 열람 게이트 미적용(D-071 연장), 활동 피드 인덱스 V11 예고 — Phase 6 계약(용어·정책·에러코드)을 코드보다 먼저 docs/에 확정
- [Phase 06-02]: AttendanceRepositoryTest·NoticeRepositoryTest는 InactivityBatchRunnerTest와 동일한 애노테이션 조합(@Transactional 미사용 + @AfterEach 직접 정리)을 쓴다 — 06-06~06-08이 재사용할 컨텍스트를 통일하고, 유니크 위반 단언 직후 abort된 트랜잭션에서 @AfterEach DELETE가 실패하는 문제를 피한다
- [Phase 06-02]: AttendanceFixtures.branch()는 통합테스트에서 쓰지 않는다 — uq_branch_name UNIQUE + V2의 '송파점' 시드와 충돌하므로 순수 단위테스트 전용으로 남기고, 통합테스트는 BranchRepository.findByName으로 시드된 지점을 재사용한다
- [Phase 06-03]: InactivityDueDateCalculator·InactivityDueDateCandidates는 계획대로 무변경 — Phase 5가 이미 5종 후보 시그니처를 갖춰 뒀으므로 배선 한 지점(lastAttendanceDate = null -> attendanceRepository.findLastAttendedClassDates 벌크 조회)만 바꿔 CR-03을 닫았다
- [Phase 06-03]: 회귀 테스트는 저녁반(EVENING) 전용 SESSION_PASS 회원 시나리오를 기본값으로 재현하고(CR-03 실제 사고 시나리오), 1회 실행 상한(maxDeductionsPerRun=1)이 단일 실행 비교를 무의미하게 만들어 repeat(3) 반복 실행으로 캐치업 차이를 검증한다
- [Phase 06-04]: NoticeExceptions.kt를 NoticeNotFoundException.kt로 개명 — ktlint standard:filename 규칙 위반(단일 클래스 파일은 클래스명과 일치)이라 plan 파일명에서 편차
- [Phase 06-04]: MemberNoticeController는 회원 상태 게이트를 거치지 않는다(D-134) — 공지는 회원 소유 데이터가 아니고 휴회 회원의 열람을 막을 이유가 없다
- [Phase 06-05]: EveningHalfDeductionPolicy.selectCandidate는 ReservationPassPolicy.selectCandidate를 재사용하지 않는다 — 예외 타입이 D-133 요구 코드(EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE)와 달라진다
- [Phase 06-05]: resolveDeduction은 회비로 커버되는 경우 Pass? 중 null을 반환한다 — 별도 sealed 결과 타입 없이 호출부가 null 분기만으로 처리
- [Phase 06-05]: existsActiveEveningMembership은 findDeductionCandidates와 같은 D-066 종료일 포함 비교축(endDate >= :classDate)을 재사용해 종료일 당일 경계가 쿼리마다 갈라지지 않게 했다
- [Phase 06-06]: Attendance.checkedBy/checkedAt을 val→var로 변경 — 06-02는 이력이라 불변으로 설계했으나, 소급 정정 시 마지막 확인 관리자·시각 갱신 요구(D-127)를 만족하려면 status와 동일하게 가변이어야 한다
- [Phase 06-06]: AttendanceService는 이용권 원장 리포지토리를 생성자에 주입하지 않는다 — policies §6 차감과 무관한 참고용 데이터를 구조적으로 강제(T-06-17, grep acceptance로 고정)
- [Phase 06-operations]: 저녁반 출석 삭제는 attendance 행을 먼저 삭제·flush한 뒤 조건부 UPDATE로 잔여를 복구한다 — FK 참조가 남아 있으면 이력 해석이 모호해지는 것을 방지
- [Phase 06-operations]: ReservationLedgerSupport를 재사용하지 않고 흐름만 이식 — 복구 금액이 정책 상수(1.0)로 고정돼 있어 0.5 복구에 그대로 쓸 수 없다
- [Phase 06-08]: 저녁반 회원 검색 전용 API는 만들지 않는다 — FE는 기존 GET /api/admin/members?keyword=로 memberId를 얻어 POST /evening에 넘긴다(06-RESEARCH Open Question 2 확정)
- [Phase 06-08]: 출석 컨트롤러 경로는 /api/admin/attendances 단일 계층으로 고정 — 스케줄 보드처럼 여러 리소스 계층이 섞이지 않는 단일 리소스는 넓은 클래스 매핑이 필요 없다
- [Phase 06-09]: Notification.memberName은 Member FK가 아니라 비정규화 문자열이라, 알림 픽스처는 실제 Member 없이 문자열만으로 만든다 — 회원 토큰 발급이 필요한 권한 테스트만 예외적으로 실제 Member를 생성한다
- [Phase 06-09]: Phase 4의 NotificationService(생성 전용)는 무변경 — 조회 책임은 신규 NotificationQueryService로 완전히 분리했다
- [Phase 06-10]: 활동 피드 범위 역전(from > to)은 새 에러코드를 만들지 않고 Specification AND 조합이 자연히 빈 목록을 반환하도록 둔다 — 예약 조회 전용인 INVALID_RESERVATION_SEARCH_RANGE를 재사용하지 않았다
- [Phase 06-10]: AdminNotificationController 클래스 매핑을 /api/admin/notifications에서 /api/admin으로 넓히고 기존 두 메서드에 /notifications, /notifications/read-all 하위 경로를 붙였다 — 알림과 피드는 같은 테이블의 다른 뷰지만 FE 화면이 달라 하위 경로로 두면 오해된다(AdminScheduleController 선례). 노출 경로 문자열은 06-09 값과 동일하게 유지해 FE 계약을 깨지 않았다
- [Phase 06-11]: CR-03 배선 확인은 로컬 DB에 출석 유무로 결과가 갈리는 회원이 없어 InactivityBatchRunner.kt의 코드 경로 + InactivityBatchRunnerTest 대조 테스트로 실증하고 실기동은 '부분 확인'으로 기록, 사용자가 이 상태로 승인
- [Phase 07-01]: D-169~D-175: 운영 프로필 미신설, Hikari/Tomcat 축소, springdoc enabled 토글로 Swagger 운영 비활성(SecurityConfig 불변), 런타임 베이스 이미지 noble 고정, JVM MaxRAMPercentage=60 시작값, Caddy actuator 외부 차단, GHCR public 이미지
- [Phase 07]: 가정 A1(빌더가 amd64+arm64 두 플랫폼에서 1회만 컴파일한다)을 최초 빌드와 --no-cache-filter=builder 강제 재캐시 두 방식으로 실측 검증 — TRUE 확인 (docs/metrics.md §2)
- [Phase 07]: 레이어드 이미지의 이득은 전체 크기(fat jar와 동일 563MB)가 아니라 코드 변경 시 재전송 바이트에서 발생함을 실측으로 확정 (72.2MB → 606kB, 약 119배, docs/metrics.md §1)
- [Phase 07]: 운영 compose container_name을 gw-prod-*로 분리해 로컬 dev docker-compose.yml과 이름 충돌 없이 동시 검증 가능하게 함
- [Phase 07]: compose.local.yml에서 local_certs 전역 옵션 주입을 포기 — Caddy가 localhost를 비공인 도메인으로 자동 인식해 내부 CA 인증서를 스스로 발급함을 실측 확인
- [Phase 07]: Dockerfile ENTRYPOINT(java -jar application.jar)가 실제 산출물 파일명과 불일치하는 버그 발견 — compose entrypoint/command로 우회, Dockerfile 자체 수정은 후속 필요
- [Phase 07-04]: docs/operations.md의 env 키 표는 .env.example 29개 키 전수를 grep 루프로 대조해 1:1 동기화를 강제한다
- [Phase 07-04]: DOMAIN·ACME_EMAIL은 REQUIREMENTS.md INFRA-07이 명시한 KAKAO_REDIRECT_URI/CORS_ALLOWED_ORIGINS와 달리 실도메인이 아니므로 <your-domain>/<your-email> 플레이스홀더로 남긴다

### Pending Todos

None yet.

### Blockers/Concerns

- REQUIREMENTS.md 문서 상단의 "v1 requirements: 36 total" 표기가 실제 v1 목록(FOUND~NOTIF, 42건)과 불일치했음. 로드맵 작성 시 실제 목록 42건 전부를 매핑하고 Coverage 섹션을 42로 정정함 — 원 문서(docs/)와의 스펙 차이가 아니라 REQUIREMENTS.md 자체의 집계 오류로 판단.
- ~~STATE.md의 'Plan: X of 9' 표시값 드리프트~~ **해소(2026-08-15)** — 원인은 청크 실행 시 오케스트레이터가 매번 호출하는 `state.begin-phase`가 Plan 카운터를 1로 리셋하는 것. `advance-plan`은 상대 증분만 하므로 리셋된 값에서 다시 세어 어긋났다. 실제 완료 수(5/9)로 수동 정정했고, 청크 단위 실행(D-084)에서는 wave 시작마다 `begin-phase`가 재호출되므로 다음 청크에서도 같은 드리프트가 재발할 수 있다 — 표시 전용 필드이며 SUMMARY 존재 여부가 실제 진행의 근거다
- ~~05-16-PLAN.md Task 2(로컬 실기동 확인) 사용자 승인 대기~~ **해소(2026-08-16)** — 오케스트레이터가 실제 앱·실제 DB로 실행해 결과를 사용자에게 제시했다: 202+Location, Location 폴링 SUCCESS, 동시 POST 10건×2회 → 매번 202 1건/409 9건(ProblemDetail `BATCH_ALREADY_RUNNING`), 종료 후 `RUNNING` 0건, 총 20건 요청 후에도 `pass_transaction` 35건·`INACTIVITY` 1건 불변(실 DB 이중 차감 0건), `limit` 0/-1/101 → 400. 보존 데이터 무손실
- ~~Dockerfile ENTRYPOINT(java -jar application.jar)가 실제 bootJar 산출물 파일명과 불일치~~ **해소(2026-09-08, D-176)** — 07-03의 compose `find` 우회를 제거하고 Dockerfile 빌더 스테이지에서 jar를 `application.jar`로 복사한 뒤 extract하도록 고쳤다(공식 Boot 4.1 형태). 재빌드 이미지로 로컬 3컨테이너 재기동 검증 완료. 07-05 GHCR push는 이 수정본 기준

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260806-und | D-083 카카오 프로필(닉네임·프로필 이미지) 수집·저장 및 MyProfileResponse 노출 | 2026-08-06 | 7f363a9 | [260806-und-d-083-myprofileresponse](./quick/260806-und-d-083-myprofileresponse/) |
| 260820-cp1 | D-01 공지 목록 2경로(admin·member) page/size 검증 추가 — 500→400, NoticeSearchCondition 신설 | 2026-08-20 | 46cd949 | [260820-cp1-api-page-size](./quick/260820-cp1-api-page-size/) |
| 260828-g4o | 이슈 #12(WR-02) 휴강 캐스케이드 × 등록취소 경합 시 500 → 현재 상태 재판정 + 0행 스킵, 등록취소 이중 검사 (D-145) | 2026-08-28 | PR #26 (dev 머지) | [260828-g4o-issue-12-suspend-cancel-race](./quick/260828-g4o-issue-12-suspend-cancel-race/) |
| 260831-v1f | v1 마감: 정책 결정 3건(D-146 요일검증 불허·D-147 INACTIVE 차감예외·D-151 cron 방침) + BE-REQ 3건(005 phoneNumber·003 관리자 이력조회·004 기간필터), BE-REQ-001/002/006 v1.1 백로그 이관 | 2026-08-31 | PR #27 (dev 머지 2026-09-01) | [260831-v1f-v1-closeout](./quick/260831-v1f-v1-closeout/) |
| 260901-tkv | CI 워크플로(ci.yml) 신설 + main 보호 required check 등록 절차 README 문서화 (D-152) | 2026-09-01 | 9cdd42e | [260901-tkv-ci-main-required-check](./quick/260901-tkv-ci-main-required-check/) |

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

### v1.1 백로그 — FE 계약 변경 요청 3건 (2026-08-31 v1 마감 시 명시 이관)

출처: `../gold-wrestling-fe/.planning/BE-CHANGE-REQUESTS.md`. 같은 문서의 6건 중 **3건은 v1에서
처리**했고(BE-REQ-003 → D-149, BE-REQ-004 → D-150, BE-REQ-005 → D-148), 아래 3건은 **FE 우회가
안전하게 동작 중이라** v1 마감을 막지 않는다고 판단해 이관한다. 해소 시 FE가 되돌릴 코드는
BE-CHANGE-REQUESTS.md의 "해소되면 할 일" 열에 항목별로 적혀 있다.

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| 계약(FE 요청) | **BE-REQ-001** — `openapi.yaml`에 4xx/5xx `ProblemDetail` 응답 스키마 선언. 현재 전 operation이 `200`만 선언해 FE가 `code`에 타입으로 도달할 수 없다(값은 오지만 타입이 없다) | v1.1 백로그 — FE는 `src/api/errors.ts` 런타임 타입가드로 우회 중 | 2026-08-31 (v1 마감) |
| 계약(FE 요청) | **BE-REQ-002** — `GET /api/admin/me`(관리자 신원 조회). 새로고침 후 세션에서 관리자 신원을 재확인할 수단이 없다 | v1.1 백로그 — FE `RequireAdmin`은 토큰 존재만 판정(UX 라우팅이며 인가가 아니다 — 실제 인가는 BE `hasRole("ADMIN")`가 강제하므로 보안 구멍은 아니다) | 2026-08-31 (v1 마감) |
| 계약(FE 요청) | **BE-REQ-006** — `GET /api/admin/reservations/{reservationId}`(예약 단건 조회). 대리 변경 모드에서 다른 주로 이동하면 대상 `classType`을 알 수 없어 차단 사유 문구가 실제 원인과 다르게 표시된다 | v1.1 백로그 — 같은 주 안의 변경(대다수 사용)에는 영향 없어 FE가 현 동작 유지 | 2026-08-31 (v1 마감) |

### 운영 결정 대기

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| 운영 | 미사용 차감 cron 활성화 — CR-03이 Phase 6에서 닫혀 켤 수 있는 상태가 됐다. 기본값은 `false` 유지(D-151) | 운영 배포 후 D-130 3단계 절차로 활성화 판단 | 2026-08-31 (v1 마감) |
| 관측 | CR-03 배선의 운영 데이터 대조 — 로컬 DB에 "출석 유무로 결과가 갈리는 회원"이 없어 자동화 테스트로만 실증됨(`06-VERIFICATION.md` §2) | 배포 후 관찰 항목 (phase 완료 조건 아님) | 2026-08-20 |
| 보안(Info) | WR-02 — 관리자 로그인 타이밍 부채널(`02-VERIFICATION.md`) | 사용자가 인지하고 의도적으로 범위 제외 | 2026-08-08 |

## Session Continuity

Last session: 2026-09-09T00:48:55.398Z
Stopped at: Completed 07-04-PLAN.md
Resume file: None
