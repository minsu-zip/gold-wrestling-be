---
phase: 06-operations
plan: 07
subsystem: api
tags: [kotlin, spring-boot, jpa, attendance, pass, testcontainers]

# Dependency graph
requires:
  - phase: 06-operations
    provides: "06-05(EveningHalfDeductionPolicy·PassRepository.existsActiveEveningMembership), 06-06(AttendanceService.getRoster/check, AttendanceRepository.existsByClassSessionIdAndMemberId)"
provides:
  - "AttendanceService.addEveningAttendance — 회비 우선 판정 → 조건부 0.5회 차감 → 출석 INSERT → EVENING_HALF 원장 기록을 한 트랜잭션으로 완결"
  - "AttendanceService.delete — passTransaction 연결 시 0.5회 복구 + EVENING_HALF_REFUND 이력, 없으면 행만 삭제"
  - "AttendanceEveningHalfTest — 회비 우선·차감·409 거부·삭제 복구·소급 판정·중복 거부 통합테스트 7건"
affects: ["06-08(AdminAttendanceController가 addEveningAttendance·delete를 배선하고 openapi.yaml을 갱신한다)"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ReservationLedgerSupport.createReservation의 '판정 → 조건부 UPDATE(0행이면 예외) → 재조회 → INSERT → 원장 기록' 구조를 attendance 패키지로 이식 — 새 락 메커니즘·차감 경로를 발명하지 않는다"
    - "잔여 복구 금액이 정책 상수(ReservationPassPolicy.DEDUCTION_AMOUNT=1.0)와 다르면(0.5) ReservationLedgerSupport를 재사용하지 않고 같은 흐름만 이식한다 — 상수 재사용보다 흐름 재사용이 확장에 안전하다"

key-files:
  created:
    - src/test/kotlin/com/goldwrestling/attendance/AttendanceEveningHalfTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/attendance/AttendanceService.kt

key-decisions:
  - "delete()는 attendance 행을 먼저 삭제·flush한 뒤 조건부 UPDATE로 복구한다 — attendance.pass_transaction_id FK가 남아 있으면 이력 해석이 모호해진다는 계획의 순서를 그대로 따름"
  - "addEveningAttendance는 회비 보유 시(deductedPassId=null)에도 재조회 로직을 동일하게 타지만, 그 경로에서는 조건부 UPDATE가 없어 컨텍스트 clear가 일어나지 않는다 — 재조회를 무조건 실행해도 안전하고, 두 분기(회비 有/無)를 하나의 코드 경로로 합쳐 분기 복잡도를 줄였다"

patterns-established:
  - "저녁반 0.5회 차감·복구가 이 phase에서 잔여 횟수를 바꾸는 유일한 경로임을 AttendanceService 클래스 KDoc과 생성자 주입(passRepository·passTransactionRepository)으로 명시"

requirements-completed: [ATTEND-02]

# Metrics
duration: ~30min
completed: 2026-08-18
---

# Phase 06 Plan 07: 저녁반 0.5회 차감 실행부 Summary

**저녁반 출석 추가에서 회비 우선 판정 → 조건부 SESSION_PASS 0.5회 차감 → EVENING_HALF 원장 기록을 한 트랜잭션으로 묶고, 삭제 시 EVENING_HALF_REFUND로 복구하는 실행부(ATTEND-02)**

## Performance

- **Duration:** ~30min
- **Started:** 2026-08-18T21:41:00+09:00 (파일 조사 시작 기준 역산)
- **Completed:** 2026-08-18T22:10:00+09:00
- **Tasks:** 3
- **Files modified:** 2 (신규 1, 수정 1)

## Accomplishments
- `addEveningAttendance` — 수업날(session.classDate) 기준으로 `EVENING_MEMBERSHIP` 보유 여부를 먼저 판정하고, 없을 때만 만료 임박순 `SESSION_PASS`에서 0.5회를 조건부 UPDATE로 차감. 경쟁 패배(0행)는 `EveningAttendanceDeductionUnavailableException`, 이중 출석은 `DuplicateAttendanceException`(사전 검사 + `DataIntegrityViolationException` 변환 이중 방어)으로 처리
- `delete` — `passTransaction`이 연결된 저녁반 출석만 0.5회를 복구하고 `EVENING_HALF_REFUND` 이력을 추가(원 `EVENING_HALF` 이력은 append-only로 보존). 예약제/1:1·회비 보유 저녁반 출석은 행만 삭제
- `ReservationLedgerSupport.createReservation`이 확립한 "판정 → 조건부 UPDATE(0행이면 예외) → 재조회 → INSERT → 원장 기록" 구조를 그대로 이식 — 새 락 메커니즘·차감 경로를 발명하지 않음
- `AttendanceEveningHalfTest` 통합테스트 7건으로 회비 우선·0.5 차감·0.5 경계·409 거부(트랜잭션 전체 롤백 포함)·삭제 복구·소급 판정·중복 거부를 실제 PostgreSQL로 증명

## Task Commits

Each task was committed atomically:

1. **Task 1: addEveningAttendance — 회비 우선 판정 → 조건부 0.5 차감 → 출석 INSERT → EVENING_HALF 원장** - `e13712c` (feat)
2. **Task 2: delete — 저녁반 출석 삭제 시 0.5회 복구 + EVENING_HALF_REFUND 이력** - `4b97e2b` (feat)
3. **Task 3: AttendanceEveningHalfTest — 회비 우선·0.5 차감·409 거부·삭제 복구·소급 판정** - `4c4fdd4` (test)

**Plan metadata:** (다음 커밋에서 STATE.md·ROADMAP.md와 함께 기록)

## Files Created/Modified
- `src/main/kotlin/com/goldwrestling/attendance/AttendanceService.kt` - `addEveningAttendance`(회비 우선 판정·조건부 차감·원장 기록)·`delete`(연결 이력 있으면 복구·원장 기록, 없으면 삭제만) 추가. 생성자에 `passRepository`·`passTransactionRepository` 주입
- `src/test/kotlin/com/goldwrestling/attendance/AttendanceEveningHalfTest.kt` - 회비 우선·0.5 차감(만료 임박순)·잔여 0.5 경계·409 거부·삭제 복구(이력 2건 확인)·수업날 기준 소급 판정·중복 출석 거부 7개 통합테스트

## Decisions Made
- `delete()`의 삭제→flush→조건부 UPDATE 순서(위 key-decisions 참조)
- 회비 보유 분기에서도 재조회 로직을 동일 경로로 통과시켜 분기 복잡도를 줄임(위 key-decisions 참조)

## Deviations from Plan

None - 계획대로 실행됐다. 다만 검증 스크립트 자체의 함정 하나를 실행 중 발견해 조정했다(아래 Issues Encountered).

## Issues Encountered

**Task 1 verify 스크립트의 리터럴 매칭 함정**: 플랜의 자동 검증이 `! grep -q "LocalDate.now(clock)"`로 소급 판정 버그(오늘 기준 오판정)를 막는데, 구현 KDoc 주석에 그 정확한 문자열을 예시로 인용해 두었더니 같은 grep이 주석까지 걸려 검증이 실패했다. 실제 코드에는 그 호출이 없었으므로(버그 아님) KDoc 문구를 "clock으로 구한 오늘 날짜"로 바꿔 리터럴 일치를 피했다 — 동작 변경 없음, 문서 표현만 조정.

**저녁반 출석 추가 실패 시 세션도 함께 롤백됨**: Task 3의 "회비도 없고 잔여가 0.5 미만" 테스트를 처음 작성할 때 `getOrCreate`가 만든 `ClassSession`이 예외 발생 후에도 남아 있을 것으로 가정하고 그 id를 재조회해 정리하려 했다. 그러나 `addEveningAttendance` 전체가 `@Transactional`이라 예외 시 세션 INSERT까지 함께 롤백된다 — 테스트를 "세션도 만들어지지 않는다"는 확인으로 수정했다(실제 동작이 정책과 더 부합함을 재확인).

## User Setup Required

None - 외부 서비스 설정 불필요.

## Next Phase Readiness

- `AttendanceService.addEveningAttendance`·`delete`가 06-08(`AdminAttendanceController`)이 그대로 배선할 수 있는 형태로 준비됐다 — 06-08에서 `docs/api/openapi.yaml`을 함께 갱신해야 한다(이 플랜은 서비스 계층만 다뤄 API 표면 변경이 없다)
- `./gradlew test --tests "com.goldwrestling.attendance.*"` 0 failures, `./gradlew ktlintFormat && ./gradlew build` BUILD SUCCESSFUL로 확인
- 블로커 없음

---

## 이번에 쓴 기술

1. **조건부 UPDATE(compare-and-swap)를 통한 원자적 차감/복구** — `WHERE` 절에 판정 조건을 넣어 "조회 후 갱신" 대신 "조건이 참일 때만 갱신"으로 만드는 방식
   - **이 코드에서 왜 필요했는가**: 관리자 두 명이 거의 동시에 같은 회원의 저녁반 출석을 추가하려 하면, 둘 다 "잔여 0.5 있음"을 읽고 둘 다 차감을 시도할 수 있다. `adjustRemainingCount`는 `remainingCount + :amount >= 0`을 UPDATE의 `WHERE`에 직접 넣어, DB가 그 조건을 만족하는 행에만 갱신을 적용하고 아니면 0행을 반환한다 — 애플리케이션이 먼저 읽고 나중에 쓰는 두 단계 사이의 경쟁 창을 없앤다.
   - **안 썼으면 뭐가 깨지는가**: "조회 → 애플리케이션에서 뺄셈 → 저장" 방식이면 두 트랜잭션이 같은 잔여값을 읽고 각자 0.5씩 뺀 결과를 저장해, 실제로는 1.0이 빠져야 하는데 0.5만 반영되거나(마지막 쓰기가 이김) 잔여가 음수로 내려가는 이중 차감이 발생한다.

2. **★ 벌크 UPDATE 이후의 영속성 컨텍스트 clear와 재조회** — `@Modifying(clearAutomatically = true)`로 벌크 UPDATE 직후 1차 캐시를 비우고, 이후 로직에 쓸 엔티티를 다시 조회하는 관례
   - **이 코드에서 왜 필요했는가**: `adjustRemainingCount`는 JPQL 벌크 UPDATE라 영속성 컨텍스트(1차 캐시)를 거치지 않고 DB에 직접 SQL을 보낸다. 그대로 두면 이전에 읽어 둔 `Pass` 엔티티가 여전히 갱신 전 잔여값을 들고 있는 "낡은 스냅샷"이 된다. `clearAutomatically = true`는 컨텍스트를 비워 이 낡은 스냅샷을 못 쓰게 만들고, 그래서 `addEveningAttendance`는 차감 직후 `Pass`·`Member`·`ClassSession`·`Admin`을 전부 다시 조회해 `PassTransaction`·`Attendance`를 조립한다.
   - **안 썼으면 뭐가 깨지는가**: clear를 안 하면 이후 코드가 갱신 전 잔여값을 가진 준영속 엔티티를 실수로 다시 저장해 방금 반영한 차감을 덮어쓸 위험이 있고, clear는 했는데 재조회를 빼먹으면 준영속 엔티티의 지연 로딩 필드에 접근하는 순간 `LazyInitializationException`이 난다.

3. **append-only 원장에서의 상쇄 기록(compensating entry)** — 잘못되거나 되돌려야 할 이력을 삭제·수정하지 않고, 반대 방향의 새 행을 추가해 표현하는 방식
   - **이 코드에서 왜 필요했는가**: 저녁반 출석을 삭제하면 잔여를 되돌려야 하는데, `PassTransaction`은 "전 필드 `val`"로 설계된 append-only 원장이다(CLAUDE.md 규칙 6 "이력 없는 잔여 변경 금지"). 그래서 `delete()`는 원래 `EVENING_HALF`(-0.5) 행을 지우는 대신 `EVENING_HALF_REFUND`(+0.5) 행을 새로 추가한다 — 두 행을 합산하면 순 변화가 0이 되면서도, "차감이 있었다가 취소됐다"는 사실 자체는 원장에서 사라지지 않는다.
   - **안 썼으면 뭐가 깨지는가**: 원래 이력을 지우면 "언제 차감됐다가 언제 취소됐는지"를 감사(audit)할 방법이 없어지고, 회원이 "왜 내 잔여가 한 번 줄었다가 늘었는지" 문의했을 때 관리자가 근거를 보여줄 수 없다.

4. **Kotlin `let`을 이용한 nullable 분기와 원자적 대입** — `deductedPassId?.let { ... }`로 "null이면 건너뛰고 non-null이면 그 값으로 블록을 실행해 결과를 받는다"는 표현
   - **이 코드에서 왜 필요했는가**: 차감 여부(회비 유무)에 따라 `PassTransaction`을 만들지 말지가 갈리는데, 이를 `if/else`로 쓰면 두 분기에서 각각 `var passTransaction`을 선언·재대입해야 해서 실수로 초기화를 빼먹거나 재대입 시점이 어긋날 위험이 생긴다. `deductedPassId?.let { passId -> ... }`는 "차감이 있었을 때만 저장하고, 그 저장 결과(혹은 null)를 그대로 `passTransaction` 값으로 확정"하는 한 표현식이라 `val`로 불변 선언할 수 있다.
   - **안 썼으면 뭐가 깨지는가**: `var` + 명령형 if/else로 짜면 코드가 늘어날수록 "이 변수가 이 지점에서 이미 채워졌는지"를 매번 눈으로 추적해야 해서, 나중에 로직을 수정하다 재대입을 빠뜨리는 버그가 나기 쉽다.

---
*Phase: 06-operations*
*Completed: 2026-08-18*

## Self-Check: PASSED

All created/modified files verified to exist on disk (`AttendanceService.kt`, `AttendanceEveningHalfTest.kt`, this SUMMARY.md); all task commit hashes (`e13712c`, `4b97e2b`, `4c4fdd4`, `47bc3b1`) verified present in git log.
