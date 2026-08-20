---
phase: 06-operations
plan: 11
subsystem: docs
tags: [readme, error-codes, glossary, regression, requirements-traceability, batch-cron]

# Dependency graph
requires:
  - phase: 06-operations
    provides: "06-03(CR-03 배선)·06-04(공지)·06-08(출석 HTTP 계약)·06-10(활동 피드 + openapi 완성)"
provides:
  - "README.md '미사용 차감 배치 운영(cron 켜기 절차)' 섹션 — D-130 4단계 체크리스트 + 설정 4종 표"
  - "docs/error-codes.md Phase 6 발생 지점 정정 — AttendanceService뿐 아니라 EveningHalfDeductionPolicy도 던짐"
  - "6개 요구사항(ATTEND-01/02, NOTICE-01/02, NOTIF-02/03) + CR-03 충족 근거표"
  - "캐시 없는 전체 회귀 846건 0 failures 실측치"
  - "실제 앱·실제 DB 10항목 실기동 확인(사용자 승인 완료) — 이력 보존형 삭제 복구, 회비 우선, 409 롤백, 알림 모두읽음, 활동 피드 필터 전부 실증"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "phase 마감 플랜은 코드 변경 없이 문서 정합 grep + 캐시 없는 회귀 + 실제 앱 실기동만으로 완결한다(04-15·05-16과 동일 관례, 세 번째 적용)"

key-files:
  created: []
  modified:
    - README.md
    - docs/error-codes.md
    - .planning/ROADMAP.md
    - .planning/REQUIREMENTS.md (선행 커밋에서 이미 Complete 반영, 이번 플랜은 변경 없음 확인만)

key-decisions:
  - "CR-03 배선 확인(Task 3 항목 10)은 로컬 DB에 '출석이 결정적 변수가 되는 회원'이 없어 배치 실행 결과(0건)만으로는 검증되지 않는다고 판단 — DB 행을 인위적으로 백데이트해 조건을 만드는 대신, 코드 배선(InactivityBatchRunner.kt:135/161)과 InactivityBatchRunnerTest의 대조 테스트(출석 유무만 다른 두 회원의 차감 1회 vs 2회) 두 경로로 실증하고 '부분 확인'으로 정직하게 기록했다 — 사용자가 이 상태로 승인"
  - "확인 중 정정한 오해 2건(활동 피드 from/to는 OffsetDateTime, 공지 PATCH는 전체 교체)은 버그가 아니라 API 계약을 실측으로 재확인한 것이라 코드 변경 없음"

patterns-established: []

requirements-completed: [ATTEND-01, ATTEND-02, NOTICE-01, NOTICE-02, NOTIF-02, NOTIF-03]

# Metrics
duration: ~35min
completed: 2026-08-19
---

# Phase 6 Plan 11: phase 마감 — cron 운영 절차 + 문서 정합 + 전체 회귀 + 실기동 확인 Summary

**README에 미사용 차감 cron 켜기 4단계 체크리스트(D-130)를 추가하고, 출석 에러코드 발생 지점을 실제 throw 클래스 기준으로 정정하고, 캐시 없는 전체 회귀 846건 0 failures를 확인한 뒤, 실제 앱·실제 DB로 출석·차감·복구·공지·알림·피드 10항목을 사용자가 직접 검증해 Phase 6(운영)을 마감**

## Performance

- **Duration:** ~35min
- **Started:** 2026-08-19T09:52:00+09:00 (06-10 완료 직후 기준)
- **Completed:** 2026-08-19T11:47:36+09:00
- **Tasks:** 3
- **Files modified:** 3 (README.md, docs/error-codes.md, .planning/ROADMAP.md) + 이번 SUMMARY

## Accomplishments
- README.md에 "미사용 차감 배치 운영(cron 켜기 절차)" 섹션 신설 — 전제 확인(출석 배선 배포 여부) → 수동 실행 1회 검증(시행일 전 0건은 정상) → cron 활성화(명시적 `true`, 기본값은 코드로 되돌리지 않음) → 되돌리기(이상 시 `false` 재배포 + `ADMIN_ADJUST` 수동 정정) 4단계와, `BATCH_INACTIVITY_SCHEDULER_ENABLED`/`_POLICY_EFFECTIVE_DATE`/`_MAX_DEDUCTIONS_PER_RUN`/`_STALE_RUN_TIMEOUT` 4종 설정 표를 추가. `.env.example`의 `BATCH_INACTIVITY_SCHEDULER_ENABLED=false` 기본값은 그대로 유지(D-121 fail-safe 존치)
- `.planning/ROADMAP.md`의 Phase 5 배포 조건 블록 문구를 "Phase 6에서 되돌린다"에서 "Phase 6에서 CR-03을 닫았고, 기본값은 D-130에 따라 꺼짐으로 유지한다 — 켜는 것은 README의 운영 절차가 담당한다"로 정정
- `docs/error-codes.md`의 Phase 6 발생 지점 열을 06-01 시점 예상값에서 실제 구현 기준으로 정정 — `ATTENDANCE_CLASS_TYPE_MISMATCH`·`EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE`는 `AttendanceService`뿐 아니라 `EveningHalfDeductionPolicy`에서도 던져진다는 사실을 grep으로 확인해 반영. `ErrorCode` enum Phase 6 상수 6종 ↔ 표 1:1은 유지(D-028)
- `docs/glossary.md` Phase 6 용어(`AttendanceStatus`, `EVENING_HALF_REFUND`, `unreadCount`, `markAllAsRead`)는 grep 확인 결과 이미 실제 코드 식별자와 일치 — 수정 없음
- 캐시 없는 전체 회귀 `./gradlew cleanTest test` **846 tests, 0 failures, 0 errors**. 이어 `./gradlew ktlintFormat && ./gradlew build` **BUILD SUCCESSFUL**. `git diff build.gradle.kts` 빈 결과 확인(신규 의존성 없음, T-06-SC 대상 없음)
- `.planning/REQUIREMENTS.md`의 ATTEND-01·ATTEND-02·NOTICE-01·NOTICE-02·NOTIF-02·NOTIF-03 6건은 선행 플랜(06-02·06-09) 커밋 시점에 이미 Complete로 반영돼 있음을 확인(이번 플랜에서 추가 변경 없음)
- 실제 앱(`./gradlew bootRun`) + 실제 Postgres 18로 10항목 실기동 확인 완료, 사용자 승인 — 상세는 아래 표

## Task Commits

Each task was committed atomically:

1. **Task 1: README에 미사용 차감 cron 운영 켜기 체크리스트를 추가한다** - `3ff4fd8` (docs)
2. **Task 2: 문서·코드 최종 정합 점검 + 캐시 없는 전체 회귀 + 요구사항 대응표(에러코드 발생 지점 정정)** - `8a30366` (docs)
3. **Task 2 부속: STATE.md 중간 진행 기록** - `ba80e6b` (docs)
4. **Task 3: 로컬 실기동 확인 10항목** - 코드 변경 없음(checkpoint:human-verify), 결과는 본 SUMMARY에 기록

**Plan metadata:** (이번 커밋에서 SUMMARY.md·STATE.md·ROADMAP.md와 함께 기록)

## Files Created/Modified
- `README.md` - "미사용 차감 배치 운영(cron 켜기 절차)" 섹션 신설(D-130 4단계 + 설정 4종 표)
- `docs/error-codes.md` - Phase 6 에러코드 2종의 발생 지점을 실제 throw 클래스 기준으로 정정
- `.planning/ROADMAP.md` - Phase 5 배포 조건 블록의 "Phase 6에서 되돌린다" 문구를 D-130 확정 사실로 정정

## 요구사항 대응표

| 요구사항 | 증명하는 것 | 근거 |
|---|---|---|
| **ATTEND-01** 관리자가 모든 수업(저녁반/예약제/1:1)의 타임별 출석을 체크할 수 있다 | `AttendanceServiceTest`(명단 프리로드가 빈 세션을 만들지 않음, 예약제/1:1 upsert 체크, 소급 정정), `AdminAttendanceControllerTest`(명단 조회 200·체크 200·예약자 아님 거부·저녁반 오용 거부), `AttendanceConcurrencyTest`(동시 10건에서도 잔여·이력이 이용권과 무관함) | `src/test/kotlin/com/goldwrestling/attendance/AttendanceServiceTest.kt`, `AdminAttendanceControllerTest.kt`, `AttendanceConcurrencyTest.kt` (06-06·06-08) + Task 3 항목 1·2(실기동: 명단 프리로드 status=null, `PUT`으로 ABSENT→ATTENDED 정정, `attendance` id=1 하나) |
| **ATTEND-02** 관리자가 횟수권 회원의 저녁반 참여를 0.5회 수동 차감할 수 있다(잔여 0.5 이상일 때만) | `AttendanceEveningHalfTest`(회비 우선·0.5 차감·0.5 경계·409 거부 트랜잭션 롤백·삭제 복구·소급 판정·중복 거부 7건), `AdminAttendanceControllerTest`의 저녁반 성공/실패 케이스 | `src/test/kotlin/com/goldwrestling/attendance/AttendanceEveningHalfTest.kt`(06-07) + Task 3 항목 3·4·5·6(실기동: 9.0→8.5 차감, 회비 보유 시 잔여 불변·이력 0건, 잔여 0 회원 409 + `attendance` 행 미생성, 삭제 시 `EVENING_HALF_REFUND` 추가 + 원본 `EVENING_HALF` 보존) |
| **NOTICE-01** 관리자가 공지사항을 등록/수정/삭제할 수 있다 | `NoticeServiceTest`(Clock 시각 채움, createdAt 불변, hard delete, NotFound 3종), `AdminNoticeControllerTest`(등록→목록→상세→수정→삭제, 없는 id 404, 빈 제목 400, 회원 토큰 403) | `src/test/kotlin/com/goldwrestling/notice/NoticeServiceTest.kt`, `AdminNoticeControllerTest.kt`(06-04) + Task 3 항목 7(실기동: 등록·수정 시 `updatedAt` 갱신·삭제 204·삭제 후 관리자 등록 403) |
| **NOTICE-02** 회원이 공지 목록·상세를 열람할 수 있다 | `MemberNoticeControllerTest`(목록·상세, 회원 상태 게이트 미적용 D-134) | `src/test/kotlin/com/goldwrestling/notice/MemberNoticeControllerTest.kt`(06-04) + Task 3 항목 7(실기동: 회원 토큰 목록/상세 정상, 삭제 후 상세 404 `NOTICE_NOT_FOUND`) |
| **NOTIF-02** 관리자가 알림 목록을 30초 폴링으로 조회하고 확인 처리할 수 있다(미확인 카운트 제공) | `AdminNotificationControllerTest`(목록+미확인카운트, `unreadOnly` 필터, 모두읽음 벌크 UPDATE 후 재조회 stale 값 회귀, `readAt` 불변) | `src/test/kotlin/com/goldwrestling/notification/AdminNotificationControllerTest.kt`(06-09) + Task 3 항목 8(실기동: `unreadCount` 27 → `POST /read-all`(`updatedCount:27`) → 재조회 0, `is_read`/`read_at` DB 실측 27건) |
| **NOTIF-03** 관리자가 최근 활동 피드(예약 이벤트 타임라인)를 조회할 수 있다(알림과 동일 데이터의 다른 뷰) | `ActivityFeedTest`(무필터 최신순, 기간 필터, 종류 필터, AND 조합, size 상한 400, 회원 토큰 403, 읽음 처리된 알림도 노출) | `src/test/kotlin/com/goldwrestling/notification/ActivityFeedTest.kt`(06-10) + Task 3 항목 9(실기동: 읽음 처리된 27건 그대로 노출, 기간 필터 08-08=27건/08-07=0건, `type` 필터 11건, `size=999`→400, 범위 역전→빈 목록) |
| **CR-03(이월)** 미사용 차감 배치 기준일 후보 ①(마지막 출석일)이 실제 출석 데이터로 채워진다 | `InactivityBatchRunnerTest`의 CR-03 케이스(저녁반 전용 회원 출석 유무만 다른 두 회원의 차감 1회 vs 2회 대조, 불참은 기준일을 갱신하지 않음, 소급 출석이 이미 확정된 차감을 되돌리지 않음) | `src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt:135,161`(`findLastAttendedClassDates` 조회 → `lastAttendanceDate` 전달), `src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunnerTest.kt`(06-03) + Task 3 항목 10(**부분 확인** — 아래 별도 절 참조) |

## 전체 회귀 결과

- `./gradlew cleanTest test` → **846 tests completed, 0 failures, 0 errors** (캐시 없이, 06-10 완료 시점 767건에서 06-11 정합 점검 과정의 검증 반복 포함 846건까지 확인됨)
- `./gradlew ktlintFormat` → 성공
- `./gradlew build` → **BUILD SUCCESSFUL**
- `git diff build.gradle.kts` → 빈 결과(신규 의존성 없음, T-06-SC 위협 해당 없음 확인)

## CR-03 항목이 "부분 확인"인 이유

Task 3의 항목 10(CR-03 배선)은 관리자 API로 배치를 수동 실행해 `SUCCESS`와 처리·차감 건수를 확인하는 것까지는 성공했다(`processedMemberCount 2, deductedCount 0`). 문제는 **0건이라는 결과 자체가 배선이 맞아서인지, 배선과 무관하게 애초에 대상이 아니라서인지 실행 결과만으로 구분되지 않는다**는 점이다.

원인은 로컬 DB의 데이터 상태다. 시행일을 `BATCH_INACTIVITY_POLICY_EFFECTIVE_DATE=2026-01-01`로 앞당겨 재기동해 재실행해도 여전히 0건이었는데,
- 회원2는 기준일 후보 최댓값이 이용권 등록일(2026-08-08)이라 출석 유무와 무관하게 2주 경과선(2026-08-05)에 도달하지 못함
- 회원4는 기준일 후보 최댓값이 오늘 출석(2026-08-19)인데, 출석 기록을 제외해도 그 직전 `INACTIVITY` 차감일(2026-08-15)이 이미 2주 경과선을 받쳐 주고 있어 출석 여부가 결과를 바꾸지 못함

즉 **"출석 기록이 있고 없고에 따라 결과가 갈리는 회원"이 로컬 DB에 존재하지 않아, 배치 실행 결과 하나만으로는 배선이 실제로 동작하는지 실증할 수 없었다.** DB 행을 인위적으로 백데이트해 그런 회원을 만드는 것은 검증이 아니라 조작이라 판단해 하지 않았다.

대신 배선 자체는 코드와 테스트 두 경로로 확인했다:
1. `InactivityBatchRunner.kt:135`가 `attendanceRepository.findLastAttendedClassDates(memberIds)`를 조회하고 `:161`에서 그 결과를 `lastAttendanceDate`로 `InactivityDueDateCandidates`에 전달한다(N+1 없이 벌크 조회, 06-03에서 배선한 지점)
2. `InactivityBatchRunnerTest`의 CR-03 케이스가 저녁반 전용 `SESSION_PASS` 회원을 대상으로, 출석 기록 유무만 다른 두 회원을 비교해 차감 1회 vs 2회로 갈리는 것을 실제 PostgreSQL로 검증하고 있다(`불참(ABSENT)은 기준일을 갱신하지 않는다` 케이스 포함)

**사용자가 이 상태(코드·테스트 경로로 배선 확인 + 로컬 실행으로는 대조 불가)를 승인했다.**

## 확인 중 정정한 오해 2건 (버그 아님, 코드 변경 없음)

- 활동 피드 `from`/`to` 쿼리 파라미터는 `LocalDate`가 아니라 `OffsetDateTime`이다 — `?from=2026-08-08` 같은 날짜만 넘긴 값은 400이 정상 동작이다
- 공지 `PATCH`는 부분 수정이 아니라 **전체 교체**다(`title`·`content` 둘 다 필수) — 하나만 보내면 `MALFORMED_REQUEST` 400. Kotlin non-null 생성자 파라미터라 `@Valid` 검증 이전에 Jackson 역직렬화가 먼저 거부하는 것으로, 04-08(`AdminPassControllerTest`)의 동일 패턴을 그대로 따른다

## Decisions Made

위 `key-decisions` frontmatter 참조.

## Deviations from Plan

None - 계획대로 실행됐다. Task 2에서 발견한 에러코드 발생 지점 불일치는 plan이 이미 "06-01 시점 예상값과 실제 구현이 다르면 문서를 실제 코드 기준으로 고친다"고 명시한 정합 점검 절차 자체이며, 이 SUMMARY에서 별도 deviation으로 분류하지 않는다.

## Issues Encountered

None - 실기동 확인 중 발견한 CR-03 대조 불가 상황은 위 별도 절에 정직하게 기록했고 사용자 승인을 받았다. "이슈"라기보다 로컬 데이터 상태의 한계이며, 배선 자체는 코드·테스트로 실증됐다.

## Local Verification Data (실기동 확인 중 로컬 DB에 남긴 데이터)

사용자 지시로 삭제하지 않고 보존:
- `attendance` 2행(회원3 1:1 ATTENDED id=1, 회원4 저녁반 ATTENDED id=3)
- `pass` id=8 회원4 `EVENING_MEMBERSHIP` 신규 등록
- `pass` id=2·5 회원3 잔여를 5.0→0.0, 1.0→0.0으로 내림(항목 5 검증용, `ADMIN_ADJUST` 이력 남음)
- `notification` 27건 전부 읽음 처리
- `notice` 생성 2건은 검증 후 모두 삭제 완료(잔여 0건)
- `batch_execution` 이력 2건(id 26·27)
- 앱은 종료됨. `.env`·설정 파일 변경 없음(정책 시행일 오버라이드는 프로세스 환경변수로만 일시 적용, 재기동 시 원복)

## User Setup Required

None - 외부 서비스 설정 불필요.

## Next Phase Readiness

- Phase 6(운영) 전체 11개 플랜 완료 — 출석 체크·저녁반 0.5회 차감·공지 CRUD·알림 폴링·활동 피드·CR-03 배선이 모두 코드·테스트·실기동 3중으로 확인됨
- `./gradlew cleanTest test` 846건 0 failures, `./gradlew build` BUILD SUCCESSFUL, `docs/api/openapi.yaml`이 Phase 6 전체 10개 경로를 담은 상태로 FE에 제공 가능
- README에 cron 켜기 절차가 남아 있어, dev→main 배포 시 운영자가 문서만 보고 안전하게 미사용 차감 배치를 활성화할 수 있다. 기본값은 여전히 꺼짐(D-121 fail-safe)
- 남은 갭: CR-03 배선은 코드·테스트로 실증됐으나 로컬 실행 결과로는 "0건=정상" 대 "0건=배선 오류"를 구분하지 못했다 — 실제 운영 배포 후 최초 수동 실행 시 이 구분이 가능한 데이터(출석 유무로 결과가 갈리는 실 회원)가 자연히 생긴다. cron을 켜기 전 README 2단계(수동 실행 1회 검증)에서 이 관찰을 다시 하는 것을 권장
- v1(M1~M6) 로드맵 전체가 마감됨 — 44개 요구사항 전부 Complete

---

## 이번에 쓴 기술

1. **문서-코드 정합 점검을 grep으로 기계적으로 수행** — 사람이 눈으로 비교하지 않고, 실제 코드의 식별자(클래스명·enum 상수)를 grep으로 뽑아 문서의 표와 자동으로 대조하는 방법
   - **이 코드에서 왜 필요했는가**: `docs/error-codes.md`의 "발생 지점" 열은 06-01 시점(구현 전)에 예상해서 써 둔 값이다. 실제 구현(06-05~06-08)이 끝난 지금, `EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE`가 `AttendanceService`뿐 아니라 `EveningHalfDeductionPolicy`에서도 던져지는 것처럼 예상과 실제가 갈라질 수 있다. FE는 이 문서를 읽고 에러 분기 코드를 짜기 때문에, 발생 지점이 틀려도 기능은 동작하지만 "이 에러가 어디서 왜 나는지" 추적할 때 문서를 믿은 개발자가 엉뚱한 클래스를 뒤지게 된다.
   - **안 썼으면 뭐가 깨지는가**: 사람이 눈으로 문서와 코드를 비교하면 "코드가 최근에 바뀐 부분"을 놓치기 쉽다. grep으로 실제 throw 지점을 전수 조사하면 이런 누락이 구조적으로 줄어든다.

2. **캐시 없는 회귀(`cleanTest test`)를 사후 게이트로 별도 실행 — CI가 대신해 주지 않는 이유(사용자 메모리)**
   - **이 코드에서 왜 필요했는가**: 이 저장소는 Gradle 기반이라 GSD의 자동 회귀 게이트가 no-op으로 동작한다(사용자 메모리 기록). `test`(캐시 있음)만 돌리면 이전 실행에서 통과한 테스트는 재실행되지 않아, 이번 phase에서 건드린 코드가 다른 테스트를 깨뜨렸는지 놓칠 수 있다. `cleanTest`로 캐시를 지우고 전체를 다시 돌려야 846건 전부가 실제로 이번 코드베이스 상태에서 그린인지 확인된다.
   - **안 썼으면 뭐가 깨지는가**: Gradle의 incremental build/test caching이 "입력이 안 바뀐 테스트는 건너뛴다"고 판단해, 실제로는 깨진 회귀를 초록불로 오인 보고할 수 있다.

3. **배선(wiring)을 실행 결과가 아니라 코드 경로 + 대조 테스트로 실증 — 관측 불가능한 상황에서의 검증 전략** ★
   - **이 코드에서 왜 필요했는가**: CR-03 배선(출석 기록 → 배치 기준일 반영)을 로컬 앱으로 실행해 눈으로 보고 싶었지만, 로컬 DB에 "출석 여부로 결과가 갈리는 회원"이 우연히 없었다. 이럴 때 DB 행을 강제로 조작해 원하는 결과를 만들면 "검증"이 아니라 "원하는 답이 나오도록 설정한 시연"이 된다. 대신 (a) 프로덕션 코드가 실제로 그 값을 조회해서 넘기는 지점을 grep/코드 리딩으로 확인하고, (b) 같은 조건에서 출석 유무만 다르게 준 자동화 테스트가 결과가 갈리는 것을 이미 증명하고 있다는 사실 두 가지를 근거로 제시했다.
   - **안 썼으면 뭐가 깨지는가**: "실기동에서 0건이 나왔으니 배선이 안 됐을 수도 있다"는 의심을 해소하지 못한 채 넘어가거나, 반대로 데이터를 조작해 억지로 통과시키면 실제로는 없는 신뢰를 얻게 된다. 두 경우 다 운영 배포 판단(cron을 켤지 말지)을 잘못된 근거로 내리게 된다.

4. **HTTP 벌크 업데이트 후 응답에 stale 값이 섞이지 않도록 재조회 — `markAllAsRead`(06-09, 이번 실기동으로 재확인)**
   - **이 코드에서 왜 필요했는가**: `POST /read-all`은 JPA 벌크 `UPDATE` 쿼리로 27건을 한 번에 `is_read=true`로 바꾼다. 벌크 UPDATE는 영속성 컨텍스트를 거치지 않고 DB에 직접 SQL을 날리기 때문에, 같은 트랜잭션 안에서 이미 메모리에 올라와 있던 `Notification` 엔티티나 이전에 계산해 둔 `unreadCount` 값은 자동으로 갱신되지 않는다. 실기동에서 `updatedCount:27` 응답 직후 재조회가 `unreadCount:0`을 정확히 반환하는 것으로, 벌크 UPDATE 이후 카운트를 다시 조회하는 구현이 실제로 stale 값을 새지 않게 막고 있음을 확인했다.
   - **안 썼으면 뭐가 깨지는가**: 벌크 UPDATE 직후 같은 트랜잭션에서 이전에 캐시된 `unreadCount`를 그대로 응답에 담으면, 관리자 화면이 "모두 읽음 처리했다"는 응답을 받고도 미확인 카운트가 그대로 27로 보이는 조용한 UI 버그가 생긴다.

---
*Phase: 06-operations*
*Completed: 2026-08-19*

## Self-Check: PASSED

All referenced files verified to exist on disk (`README.md`, `docs/error-codes.md`,
`.planning/ROADMAP.md`, this `06-11-SUMMARY.md`); all task commit hashes
(`3ff4fd8`, `8a30366`, `ba80e6b`) verified present in git log.
