---
phase: 06-operations
verified: 2026-08-19T23:47:02Z
status: passed
score: 5/5 success criteria verified (+ CR-03 이월 항목 verified)
overrides_applied: 0
---

# Phase 6: 운영 Verification Report

**Phase Goal:** 관리자가 모든 수업의 출석을 체크하고 공지사항을 운영하며, 예약 관련 이벤트를
알림·활동 피드로 실시간에 가깝게 확인할 수 있다.
**Verified:** 2026-08-19T23:47:02Z
**Status:** passed (2026-08-20 갱신 — 최초 판정 `human_needed`)
**재검증 여부:** 아니오 — 최초 검증 (이전 VERIFICATION.md 없음)

이 리포트는 SUMMARY.md의 주장을 그대로 옮기지 않고, dev 브랜치(HEAD `2dc5030`, PR #18/#19/#20
머지 완료 상태)의 실제 코드·테스트·문서를 직접 열어 대조한 결과다. 코드 근거는 모두 파일 경로와
심볼명으로 명시한다.

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria 5개)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | 관리자는 저녁반/예약제/1:1 모든 수업의 타임별 출석을 체크할 수 있고, 이 기록은 차감에 영향을 주지 않는 참고용 데이터로 남는다 | ✓ VERIFIED | `AttendanceService.getRoster`(명단 프리로드)·`check`(예약제/1:1 upsert, `passTransaction = null` 고정)가 `src/main/kotlin/com/goldwrestling/attendance/AttendanceService.kt`에 실존. 예약제/1:1 경로는 `pass`/`PassTransaction`을 전혀 참조하지 않음(코드 직접 확인). `AttendanceServiceTest`(7건)·`AdminAttendanceControllerTest`(9건)로 명단·체크·소급 정정·예약자 아님 거부·저녁반 오용 거부를 검증 |
| 2 | 관리자는 `SESSION_PASS` 보유 회원의 저녁반 참여를 확인 후 0.5회 수동 차감(`EVENING_HALF`)할 수 있다 — 잔여가 0.5 이상일 때만 가능하다 | ✓ VERIFIED | `AttendanceService.addEveningAttendance`가 회비 우선 판정(`existsActiveEveningMembership`, 수업날 기준) → 없으면 `findDeductionCandidates`+`EveningHalfDeductionPolicy.selectCandidate`(만료 임박순)로 0.5 조건부 차감(`adjustRemainingCount`, 0행이면 `EveningAttendanceDeductionUnavailableException` 409) → `TransactionReason.EVENING_HALF` 원장 기록을 한 트랜잭션(`@Transactional`)에서 실행. `AttendanceEveningHalfTest` 8건(회비 우선·0.5 차감·0.5 경계·409 거부·삭제 복구·소급 판정·중복 거부·지점 불일치)과 `AttendanceConcurrencyTest`(10 스레드 동시 요청, 출석 1건·이력 1건만 반영)로 실증 |
| 3 | 관리자는 공지사항을 등록·수정·삭제할 수 있고, 회원은 공지 목록·상세를 열람할 수 있다 | ✓ VERIFIED | `AdminNoticeController`(`/api/admin/notices` CRUD 5종, hard delete)·`MemberNoticeController`(`/api/members/notices` 목록·상세)가 `NoticeService`에 위임. `NoticeServiceTest` 7건 + `AdminNoticeControllerTest` 4건 + `MemberNoticeControllerTest` 2건. 다만 아래 "발견된 결함" 참조 — page/size 검증 누락 |
| 4 | 관리자는 30초 폴링으로 알림 목록과 미확인 카운트를 조회하고 확인 처리할 수 있다 | ✓ VERIFIED | `AdminNotificationController.list`(`NotificationSearchCondition`, `@Valid`)가 `NotificationQueryService.getNotifications`에 위임 — 응답에 `unreadCount` 포함(`countByIsReadFalse`). `markAllAsRead`는 `NotificationRepository.markAllAsRead`(벌크 UPDATE, `where isRead=false`로 기존 `readAt` 보존) 실행 후 **재조회**해 stale count를 방지(코드 KDoc + `AdminNotificationControllerTest` 11건으로 확인). PR #20 리뷰로 발견된 page/size 미검증(WR-01)은 커밋 `635e33c`로 수정 확인(아래 "리뷰 결함 수정 확인" 참조) |
| 5 | 관리자는 최근 예약 이벤트 타임라인(활동 피드, 알림과 동일 데이터의 다른 뷰)을 조회할 수 있다 | ✓ VERIFIED | `AdminNotificationController.activityFeed` → `NotificationQueryService.getActivityFeed`가 `NotificationSpecifications.occurredBetween`+`hasType`을 `Specification.allOf(listOfNotNull(...))`로 합성, `isRead` 조건 없음(코드에서 확인 — object에 `isRead` 조건 자체가 없음). 같은 `NotificationRepository`(`JpaSpecificationExecutor`)를 읽어 별도 저장 경로가 없음. `ActivityFeedTest` 7건(무필터·기간·종류·조합·size 상한 400·회원 403·읽음 처리된 알림도 노출) |

**Score:** 5/5 truths verified

### CR-03 (Phase 5 이월 항목)

| 항목 | Status | Evidence |
|---|--------|----------|
| `InactivityBatchRunner`의 기준일 후보 ①(마지막 출석일)이 하드코딩(`null`)에서 실제 조회로 교체됐다 | ✓ VERIFIED | `InactivityBatchRunner.kt:135`가 `attendanceRepository.findLastAttendedClassDates(memberIds)`를 호출하고 `:161`에서 `lastAttendanceDate`로 `InactivityDueDateCandidates`에 전달(코드 직접 확인, `null` 하드코딩 없음). `AttendanceRepository.findLastAttendedClassDates`는 `status = ATTENDED` 필터가 걸린 벌크 프로젝션 쿼리(`AttendanceRepository.kt`)로 ABSENT를 배제 |
| 배선이 실제 실행 결과로 실증됐다 | ✓ VERIFIED (자동화 테스트 경로) / ⚠ 부분 (실제 앱 수동 실행 경로) | `InactivityBatchRunnerTest`의 `마지막 출석일은 출석(ATTENDED) 기록만 기준으로 한다`(줄 286)가 실제 PostgreSQL(Testcontainers)로 같은 조건에서 출석 유무만 다른 두 회원의 차감 횟수가 1회 vs 2회로 갈리는 것을 직접 단언 — **이는 SUMMARY가 "부분 확인"이라 부른 것보다 강한 증거다.** `불참(ABSENT) 기록은 기준일을 갱신하지 않는다`(줄 313)·`소급 출석은 이미 실행된 INACTIVITY 차감을 되돌리지 않는다`(줄 337)도 함께 존재하고 셋 다 이번 `cleanTest` 재실행(851건, 0 failures)에 포함돼 그린이다. 06-11-SUMMARY.md가 "부분 확인"으로 기록한 것은 **로컬 실제 앱·실제 DB 수동 실행**(자동화 테스트가 아닌 사람이 직접 만든 로컬 데이터)에서 대조 가능한 회원이 없었다는 뜻이고, 이는 사용자가 명시적으로 승인한 상태다 — 자동화 테스트 경로의 배선 증명 자체는 부분이 아니라 완전하다고 판단한다 |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/com/goldwrestling/attendance/Attendance.kt` | 출석 엔티티(D-127) | ✓ VERIFIED | LAZY 연관 3종, `EnumType.STRING`, `status`/`checkedBy`/`checkedAt`만 `var`(소급 수정), `passTransaction` FK 연결 |
| `src/main/kotlin/com/goldwrestling/attendance/AttendanceRepository.kt` | 명단·체크·CR-03 벌크 조회 | ✓ VERIFIED | `findLastAttendedClassDates`(ATTENDED 필터), `findAllByClassSessionIdWithMember`(join fetch), `existsByClassSessionIdAndMemberId` 전부 실존 |
| `src/main/kotlin/com/goldwrestling/attendance/AttendanceService.kt` | 명단·체크·저녁반 차감·삭제 4개 유스케이스 | ✓ VERIFIED | `getRoster`/`check`/`addEveningAttendance`/`delete` 전부 구현, 트랜잭션 경계·조건부 UPDATE·재조회 패턴 준수(D-020, D-021) |
| `src/main/kotlin/com/goldwrestling/attendance/AdminAttendanceController.kt` | 출석 관리자 API 4종 | ✓ VERIFIED | `GET /api/admin/attendances`(명단), `PUT`(체크), `POST /evening`(저녁반 추가), `DELETE /{id}`(삭제) 4종 전부 존재, `principal.requireAdminId()` 위임 |
| `src/main/kotlin/com/goldwrestling/attendance/EveningHalfDeductionPolicy.kt` | 저녁반 0.5회 차감 순수 판정 | ✓ VERIFIED | `HALF_SESSION = BigDecimal("0.5")`, `requireEveningSession`, `selectCandidate`, `resolveDeduction` 전부 존재. `EveningHalfDeductionPolicyTest` 6건 |
| `src/main/kotlin/com/goldwrestling/notice/*.kt` (Notice·NoticeService·2 컨트롤러) | 공지 CRUD + 회원 열람 | ✓ VERIFIED | 위 truths #3 참조 |
| `src/main/kotlin/com/goldwrestling/notification/NotificationQueryService.kt` | 알림 목록·모두읽음·피드 | ✓ VERIFIED | 위 truths #4·#5 참조 |
| `src/main/kotlin/com/goldwrestling/notification/NotificationSpecifications.kt` | 활동 피드 기간·종류 필터 | ✓ VERIFIED | `occurredBetween`·`hasType` 존재, `isRead` 조건 없음 확인 |
| `src/main/resources/db/migration/V11__create_attendance_and_notice.sql` | attendance·notice 테이블 + 활동 피드 인덱스 | ✓ VERIFIED | `uq_attendance_member_session`(조건 없는 일반 UNIQUE), `idx_attendance_member`, `idx_notice_created_at`, `idx_notification_occurred_at` 전부 존재. V1~V10 수정 없음(새 버전 추가만) |
| `docs/api/openapi.yaml` | 출석·공지·알림·피드 10개 경로 | ✓ VERIFIED | grep으로 `/api/admin/attendances`, `/api/admin/attendances/evening`, `/api/admin/attendances/{attendanceId}`, `/api/admin/notices`, `/api/admin/notices/{noticeId}`, `/api/members/notices`, `/api/members/notices/{noticeId}`, `/api/admin/notifications`, `/api/admin/notifications/read-all`, `/api/admin/activity-feed` 전부 확인 |
| `src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt` + `docs/error-codes.md` | 출석·공지 에러코드 6종 1:1 | ✓ VERIFIED | `ATTENDANCE_NOT_FOUND`, `ATTENDANCE_MEMBER_NOT_RESERVED`, `ATTENDANCE_CLASS_TYPE_MISMATCH`, `DUPLICATE_ATTENDANCE`, `EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE`, `NOTICE_NOT_FOUND` 6종 모두 enum·표 양쪽에 존재 |
| `src/main/kotlin/com/goldwrestling/pass/TransactionReason.kt` | `EVENING_HALF_REFUND` | ✓ VERIFIED | `EVENING_HALF`(기존)·`EVENING_HALF_REFUND`(신설) 둘 다 존재 |
| `README.md` | cron 켜기 체크리스트(D-130) | ✓ VERIFIED | "미사용 차감 배치 운영 (cron 켜기 절차)" 섹션에 4단계 + 설정 4종 표, `BATCH_INACTIVITY_SCHEDULER_ENABLED` 포함. `.env.example` 기본값 `false` 유지 확인 |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `AttendanceService.addEveningAttendance` | `pass.PassRepository.adjustRemainingCount` | 조건부 원자 UPDATE | ✓ WIRED | 0행이면 `EveningAttendanceDeductionUnavailableException` 던짐, 코드 직접 확인 |
| `AttendanceService.addEveningAttendance` | `pass.PassTransactionRepository` | `EVENING_HALF` 원장 기록 | ✓ WIRED | `deductedPassId?.let { ... passTransactionRepository.save(...) }` |
| `AttendanceService.delete` | `pass.PassRepository.adjustRemainingCount` + `PassTransactionRepository` | 복구 + `EVENING_HALF_REFUND` | ✓ WIRED | 삭제 선행 → 조건부 복구 UPDATE(0행이면 `IllegalStateException`으로 트랜잭션 롤백, 삭제까지 되돌아감) → 복구 이력 저장 |
| `InactivityBatchRunner` | `attendance.AttendanceRepository.findLastAttendedClassDates` | 생성자 주입 + 벌크 조회 1회 | ✓ WIRED | `:135` 호출, `:161` 전달. `InactivityBatchRunnerTest`로 결과 차이 실증 |
| `AdminNotificationController.list` | `NotificationQueryService.getNotifications` | `NotificationSearchCondition`(`@Valid`, `@Min/@Max`) | ✓ WIRED | 커밋 `635e33c`로 검증 추가 확인(아래 참조) |
| `NotificationQueryService.markAllAsRead` | `NotificationRepository.markAllAsRead` | 벌크 UPDATE 후 재조회 | ✓ WIRED | `countByIsReadFalse()`를 UPDATE 이후 다시 호출(코드 확인) |
| `NotificationQueryService.getActivityFeed` | `NotificationSpecifications` | `Specification.allOf(listOfNotNull(...))` | ✓ WIRED | 코드 확인 |
| `AdminNoticeController`/`MemberNoticeController` | `NoticeRepository.findAllByOrderByCreatedAtDescIdDesc` | 최신순 페이지 조회 | ✓ WIRED | 단, 아래 "발견된 결함" 참조 |

**Wiring:** 8/8 key links verified (그중 1개는 아래 결함과 함께 기록)

## PR 리뷰 결함 수정 확인 (verification_notes 항목 3)

| 결함 | 리뷰 발견 | 수정 커밋 | dev HEAD 포함 여부 | 확인 방법 |
|------|-----------|-----------|---------------------|-----------|
| `GET /api/admin/notifications`의 `page`/`size` 무검증 → `size=0`/`page=-1`이 500 | PR #20 WR-01 | `635e33c` | ✓ `git merge-base --is-ancestor 635e33c HEAD` 성공 | 현재 `AdminNotificationController.list`가 `NotificationSearchCondition`(`@Valid @ModelAttribute`)을 받고, 코드에 `@field:Min`/`@field:Max` 존재(`NotificationSearchCondition.kt`) 확인. 회귀 테스트 3건(size=0/page=-1/size=101)이 `AdminNotificationControllerTest`에 존재 |
| `isRead`가 openapi에는 `read`로 나가 실제 응답과 계약 파손 | PR #20 리뷰 Warning | `1fda4d6` | ✓ `git merge-base --is-ancestor 1fda4d6 HEAD` 성공 | `docs/api/openapi.yaml`에서 알림 응답 스키마가 `isRead`로 나가는지 확인 필요 — grep으로 `isRead` 존재, `read:` 단독 필드 없음 확인 |

두 결함 모두 dev HEAD(`2dc5030`)에 포함돼 있고, 각 커밋의 diff가 실제로 검증 로직·JSON 필드명을 고치는 프로덕션 코드 변경임을 커밋 diff로 직접 확인했다(claim이 아니라 `git show`로 확인).

## 발견된 결함 (검증 중 신규 발견, SUMMARY에 언급 없음)

### D-01: `AdminNoticeController`·`MemberNoticeController`의 `page`/`size`에 상하한 검증이 없다

**File:** `src/main/kotlin/com/goldwrestling/notice/AdminNoticeController.kt:44-48`,
`src/main/kotlin/com/goldwrestling/notice/MemberNoticeController.kt:29-33`

**내용:** 두 컨트롤러 모두 `@RequestParam(defaultValue = "0") page: Int`, `@RequestParam(defaultValue = "20") size: Int`를
원시 파라미터로 받고 `@Min`/`@Max` 등 검증이 전혀 없다(grep으로 `@Min`/`@Max`/`NoticeSearchCondition` 부재 확인).
`NoticeService.getList`가 이 값을 그대로 `PageRequest.of(page, size)`에 넘긴다. `GlobalExceptionHandler`에
`IllegalArgumentException` 전용 핸들러가 없어(확인함 — `@ExceptionHandler(DomainException::class)`와
`@ExceptionHandler(Exception::class)` catch-all 둘뿐) `PageRequest.of`가 던지는 `IllegalArgumentException`이
catch-all로 떨어져 **500 INTERNAL_ERROR**가 된다. `size=100000` 같은 상한 없는 대량 조회도 그대로 통과한다.

**근거:** 이것은 추측이 아니다 — **같은 종류의 결함이 `AdminNotificationController.list`에서 실제로 발견돼
PR #20에서 `NotificationSearchCondition` + `@field:Min/@Max`로 수정된 선례가 있다**(위 표 참조). Notice는
이 phase(06-04, PR #18)에서 새로 만들어진 코드인데 같은 패턴의 검증이 빠진 채로 남았고, PR #18 리뷰(critical
0/warning 0)에서도 발견되지 않았다.

**영향:** 정상적인 관리자·회원 사용(기본 page/size, 또는 FE가 만드는 값)에는 영향이 없다 — 목록·상세·CRUD의
정상 경로는 동작한다(테스트로 확인됨). 잘못된 쿼리 파라미터(음수 page, 0 이하 size, 매우 큰 size)가 들어오는
경우에만 노출되며, 이는 conventions.md §8("검증 실패는 400")과 어긋나는 500 응답 + 운영 로그 잡음이다.

**심각도:** ⚠️ Warning — 성공 기준(SC3, NOTICE-01/02) 자체는 충족되므로 phase goal을 막지 않지만, 같은 PR
세트 안에서 이미 발견·수정된 결함 클래스가 형제 컨트롤러에 남아 있어 후속 조치가 필요하다.

**This looks like an oversight, not an intentional deviation.** Fix에 override를 쓰기보다 다음 quick task 또는
후속 phase에서 `NoticeSearchCondition`(또는 `NotificationSearchCondition`과 동일한 관례)으로 정리할 것을
권장한다.

## Requirements Coverage

| Requirement | Description | Status | Evidence |
|---|---|---|---|
| ATTEND-01 | 관리자가 모든 수업(저녁반/예약제/1:1)의 타임별 출석을 체크할 수 있다(참고용 데이터) | ✓ SATISFIED | Truth #1, CR-03 항목 |
| ATTEND-02 | 관리자가 횟수권 회원의 저녁반 참여를 0.5회 수동 차감할 수 있다(잔여 0.5 이상일 때만) | ✓ SATISFIED | Truth #2 |
| NOTICE-01 | 관리자가 공지사항을 등록/수정/삭제할 수 있다 | ✓ SATISFIED | Truth #3 (D-01 결함은 edge case, 핵심 CRUD는 동작) |
| NOTICE-02 | 회원이 공지 목록·상세를 열람할 수 있다 | ✓ SATISFIED | Truth #3 |
| NOTIF-02 | 관리자가 알림 목록을 폴링(30초)으로 조회·확인 처리할 수 있다(미확인 카운트 제공) | ✓ SATISFIED | Truth #4, PR #20 결함 수정 확인 |
| NOTIF-03 | 관리자가 최근 활동 피드를 조회할 수 있다(알림과 동일 데이터의 다른 뷰) | ✓ SATISFIED | Truth #5 |

**Coverage:** 6/6 requirements satisfied. `.planning/REQUIREMENTS.md`의 6개 항목이 모두 `[x]`/`Complete`로
표시돼 있고(라인 65-71, 144-149), 이번 검증 결과와 일치한다. Orphaned 요구사항 없음.

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `notice/AdminNoticeController.kt` | 44-48 | `page`/`size` 원시 파라미터, 검증 없음 | ⚠️ Warning | `size=0`/`page=-1` 등에서 500 (D-01) |
| `notice/MemberNoticeController.kt` | 29-33 | 동일 | ⚠️ Warning | 동일 |

TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER 등 부채 마커는 이 phase가 수정한 모든 파일(11개 PLAN의
`files_modified` 전수)에서 검색했으나 **0건**.

**Anti-patterns:** 2 found (0 blockers, 2 warnings)

## 회귀 테스트 (직접 재실행)

- `./gradlew cleanTest test` (캐시 완전 무효화 후 재실행, 이번 검증에서 직접 실행) →
  `build/test-results/test/*.xml` 집계 결과 **851 tests, 0 skipped, 0 failures, 0 errors**.
  SUMMARY가 주장한 846건보다 5건 많다 — 06-11 완료 이후 05-16(Phase 5 마감, 사용자 확인 대기 중이던
  건)이 이 사이 완료되며 늘어난 것으로 보이나, 이 차이가 Phase 6 자체의 문제는 아니다(Phase 6이
  건드린 파일 범위의 테스트는 위 표에서 파일 단위로 개별 확인함).
- Gradle 종료 코드 0, `BUILD SUCCESSFUL`류 실패 메시지 없음.

## Human Verification Required

### 1. Phase 6 실기동 확인 10항목 — 이미 완료·승인됨 (재확인 불필요, 기록 목적)

**Test:** README·SUMMARY에 근거만 옮기지 않기 위해, 이 항목이 실제로 "사람이 검증하고 승인했다"는
근거를 확인했다 — `06-11-SUMMARY.md`의 `key-decisions` frontmatter와 본문에 10개 항목별 실기동
결과(출석 명단, 저녁반 9.0→8.5 차감, 회비 우선, 0.5 미만 409, 삭제 복구, 공지 CRUD, 알림 모두읽음
`unreadCount 27→0`, 활동 피드 필터)가 표로 남아 있고 "사용자가 이 상태로 승인"이라고 명시돼 있다.
**Expected:** 사용자가 실제 앱·실제 DB로 10항목을 확인하고 승인한 기록이 남아 있어야 한다.
**Why human:** 이미 완료된 인간 검증이라 이 검증에서 재수행하지 않았지만, 검증 문서 형식상 사람의
승인이 필요했던 항목이었다는 점과 그 근거 위치를 명시하기 위해 이 섹션에 남긴다. **이 항목이
`status: human_needed`를 유발하지는 않는다** — 이미 자체 완결된 게이트다.

### 2. CR-03 배선의 "실제 앱 수동 실행 대조" — 로컬 데이터 한계로 미완, 운영 배포 후 재확인 권장

**Test:** 실제 운영(또는 스테이징) 배포 후, cron을 켜기 전 README 2단계(수동 실행 1회 검증)를
수행할 때 "출석 유무로 결과가 갈리는 실제 회원"이 존재하는 데이터셋에서 배치를 1회 더 실행해,
자동화 테스트가 이미 증명한 배선(1회 vs 2회 차감)이 실제 운영 데이터에서도 같은 방향으로 나타나는지
확인한다.
**Expected:** 출석 기록이 있는 회원의 차감 횟수가 출석 기록이 없는(또는 불참만 있는) 동일 조건
회원보다 적게 나와야 한다.
**Why human:** 로컬 DB에는 이 대조가 가능한 데이터가 없어(06-11-SUMMARY.md에 원인 설명) 로컬
환경에서는 자동 재현이 불가능하다 — 실제 운영 데이터가 쌓인 뒤에만 관찰 가능한 항목이다. 이미
사용자가 이 상태를 승인했으므로 이 항목이 이번 phase의 완료를 막지는 않지만, 배포 후 관찰 항목으로
공식 기록해 둔다.

### 3. D-01 결함(공지 page/size 무검증) 수정 여부 판단

**Test:** `curl "GET /api/admin/notices?size=0"` 또는 `page=-1`을 실제 배포 환경(또는 로컬)에서
호출해 500 INTERNAL_ERROR가 재현되는지 확인하고, 이를 이번 phase 범위에서 즉시 고칠지 다음 quick
task로 미룰지 결정한다.
**Why human:** 코드 분석으로 결함의 존재와 영향 범위는 확인했으나(정상 경로는 안전), 수정 우선순위는
제품 판단(관리자 콘솔이 잘못된 쿼리 파라미터를 보낼 가능성이 실제로 있는지)이 필요하다.

**✅ 해소 (2026-08-20)** — 사용자 판단: 즉시 수정. quick task
`.planning/quick/260820-cp1-api-page-size/`로 진행해 PR #22로 dev에 머지됐다.
`NoticeSearchCondition`(`@Min(0)` / `@Min(1) @Max(100)`)을 신설해 두 컨트롤러가 공유하게 했고,
회귀 테스트 6건과 openapi 재생성을 함께 넣었다. 실제 앱으로 전후를 확인했다:

| 요청 | 수정 전 | 수정 후 |
|---|---|---|
| `?size=0` | 500 (관리자·회원 모두) | **400 VALIDATION_FAILED** |
| `?page=-1` | 500 (관리자·회원 모두) | **400 VALIDATION_FAILED** |
| `?size=100000` | 200 (전량 조회) | **400 VALIDATION_FAILED** |
| `?size=20` | 200 | 200 (변화 없음) |

전수 스캔 결과 공지 2곳이 유일한 예외였고(다른 6개 목록 API는 이미 조건 객체 + `@Min`/`@Max`),
이 수정으로 프로젝트 전체가 같은 관례가 됐다. 전체 회귀 857건 0 failures.

## Gaps Summary

**Blocking gap 없음.** 5개 Success Criteria(ATTEND-01/02, NOTICE-01/02, NOTIF-02/03에 대응)와 CR-03
이월 항목 전부가 코드·자동화 테스트로 검증됐고, 851건 회귀(직접 재실행)가 0 failures다. PR 리뷰로
발견된 실제 결함 2건(page/size 검증, isRead 계약 파손)도 dev HEAD에 수정이 반영돼 있음을 커밋
diff로 직접 확인했다.

다만 검증 중 **새로 발견한 결함 1건**(D-01, Notice 컨트롤러 page/size 무검증)이 있다 — SUMMARY에
언급되지 않았고 PR #18 리뷰에서도 발견되지 않은 warning급 결함이다. Phase goal 자체(공지 CRUD·열람이
가능하다)는 이 결함과 무관하게 달성됐으므로 `gaps_found`로 분류하지 않았지만, 사용자 판단을 위해
`status: human_needed`로 표시하고 위 Human Verification 섹션 3에 결정 사항으로 남긴다.

최초 판정이 `human_needed`였던 이유는 D-01 처리 방향 결정이 사람의 판단을 필요로 했기 때문이다.
Human Verification 섹션의 1번(실기동 10항목)은 이미 완료·승인된 항목이고, 2번(CR-03 운영 데이터
재확인)은 문서 자체가 "이번 phase의 완료를 막지는 않는다"고 명시한 배포 후 관찰 항목이다. 즉
완료를 실제로 막고 있던 것은 3번 하나였다.

**2026-08-20 `passed`로 갱신** — 3번이 PR #22 머지로 해소됐다(위 Human Verification 3 참조).
남은 2번은 배포 후 관찰 항목으로 계속 열려 있으며, 이 phase의 완료 조건이 아니다.
자동화 검사는 최초 검증 시점부터 전부 통과 상태였다.

---

## Verification Metadata

**Verification approach:** Goal-backward, ROADMAP Success Criteria 5개 + PLAN.md frontmatter
must_haves(11개 플랜) 병합 기준
**Must-haves source:** ROADMAP.md Phase 6 success_criteria(우선) + 11개 PLAN.md frontmatter must_haves
**코드 직접 확인:** Attendance/AttendanceService/AttendanceRepository/AdminAttendanceController/
EveningHalfDeductionPolicy/AttendanceExceptions, Notice(3파일)+2 컨트롤러, Notification 5파일,
InactivityBatchRunner, V11 마이그레이션, ErrorCode.kt, TransactionReason.kt, README.md,
docs/glossary.md, docs/decisions.md(D-127~135), docs/error-codes.md, docs/api/openapi.yaml,
GlobalExceptionHandler.kt — 전부 Read로 열어 대조함
**자동화 검사:** `./gradlew cleanTest test` 직접 재실행(851 tests, 0 failures, 0 errors), 커밋
635e33c·1fda4d6가 dev HEAD의 ancestor인지 `git merge-base --is-ancestor`로 직접 확인, PR #18/#19/#20
GitHub 리뷰 코멘트 `gh pr view`로 직접 조회
**신규 발견 결함:** 1건(D-01, warning) — SUMMARY·기존 REVIEW.md 어디에도 언급되지 않음
**Human checks required:** 3 (①이미 완료된 게이트 기록용, ②CR-03 운영 배포 후 관찰 권장, ③D-01 처리
방향 결정)

---
*Verified: 2026-08-19T23:47:02Z*
*Verifier: Claude (gsd-verifier)*
