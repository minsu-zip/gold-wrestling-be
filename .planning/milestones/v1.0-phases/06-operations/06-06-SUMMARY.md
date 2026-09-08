---
phase: 06-operations
plan: 06
subsystem: api
tags: [kotlin, spring-boot, jpa, attendance, testcontainers]

# Dependency graph
requires:
  - phase: 06-operations
    provides: "06-02(Attendance 엔티티·AttendanceRepository 기본 4종·AttendanceExceptions·AttendanceFixtures), 06-05(EveningHalfDeductionPolicy)"
provides:
  - "AttendanceService.getRoster — 시간표·날짜별 출석 명단 조회(세션 미생성 조회, 예약제/1:1 활성 예약자 전원 + 미체크 병합, 저녁반 출석 레코드만)"
  - "AttendanceService.check — 예약제/1:1 출석/불참 upsert(소급 정정, 비예약자·저녁반 오용 거부)"
  - "AttendanceRepository.findAllByClassSessionIdWithMember — 명단 N+1 방지 join fetch"
  - "CheckAttendanceRequest·AddEveningAttendanceRequest·AttendanceResponse·AttendanceRosterEntryResponse·ClassSessionAttendanceRosterResponse DTO"
affects: ["06-07(저녁반 0.5회 차감 경로가 이 AttendanceService에 addEveningAttendance를 추가)", "06-08(AdminAttendanceController가 getRoster·check를 배선)"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "명단 조회는 join fetch 전용 메서드(findAllByClassSessionIdWithMember)로 N+1을 막고, 조회 경로는 ClassSessionService.findExisting만 써서 세션을 만들지 않는다(get-or-create는 쓰기 경로 전용)"
    - "예약제/1:1 명단은 활성 예약자를 기준 리스트로 하고 출석 레코드를 memberId 맵으로 병합 — 미체크 회원은 자연히 status=null이 된다"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/attendance/AttendanceService.kt
    - src/main/kotlin/com/goldwrestling/attendance/dto/AttendanceRequests.kt
    - src/main/kotlin/com/goldwrestling/attendance/dto/AttendanceResponses.kt
    - src/test/kotlin/com/goldwrestling/attendance/AttendanceServiceTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/attendance/AttendanceRepository.kt
    - src/main/kotlin/com/goldwrestling/attendance/Attendance.kt

key-decisions:
  - "Attendance.checkedBy/checkedAt을 val에서 var로 변경 — 06-02는 '이력이라 불변'으로 설계했으나, 이 플랜이 명시한 소급 정정 시 '마지막으로 확인한 관리자·시각' 갱신 요구를 만족하려면 status와 같은 이유(D-127)로 가변이어야 한다"
  - "AttendanceService는 이용권 원장 리포지토리를 생성자에 주입하지 않는다 — policies §6 '차감과 무관한 참고용 데이터'를 구조적으로 강제(grep acceptance로 고정, T-06-17)"

patterns-established:
  - "출석 명단 조회의 get-or-create 회피 관례: 조회 화면을 여는 것만으로 빈 세션이 쌓이지 않도록 findExisting을 쓰고, 체크(쓰기) 경로만 getOrCreate를 쓴다"

requirements-completed: [ATTEND-01]

# Metrics
duration: ~15min
completed: 2026-08-18
---

# Phase 06 Plan 06: 출석 체크 예약제/1:1 경로 Summary

**예약자 명단 프리로드(N+1 없는 join fetch) + 회원 건별 출석/불참 upsert(소급 정정) — 차감을 전혀 건드리지 않는 순수 참고 데이터 경로**

## Performance

- **Duration:** ~15min
- **Started:** 2026-08-18T21:53:00+09:00 (첫 커밋 기준 역산)
- **Completed:** 2026-08-18T21:59:00+09:00
- **Tasks:** 3
- **Files modified:** 6 (신규 4, 수정 2)

## Accomplishments
- 관리자가 특정 시간표·날짜의 예약자 명단을 출석 상태와 함께 한 번에 조회하는 `getRoster` — 세션이 없으면 조회만으로 세션을 만들지 않고 빈 명단을 반환
- 예약제/1:1 회원 건별 출석/불참 체크를 upsert로 처리하는 `check` — 활성 예약자가 아니면 거부, 저녁반 세션에는 사용 불가, 재체크는 같은 행의 상태만 정정(D-127 소급 수정)
- 명단 조회 N+1 방지용 `findAllByClassSessionIdWithMember`(join fetch) 추가
- 출석 기록이 이용권 잔여·차감 이력을 전혀 건드리지 않음을 실제 PostgreSQL 통합테스트로 증명(policies §6 직접 검증)

## Task Commits

Each task was committed atomically:

1. **Task 1: 명단·단건 DTO와 AttendanceRepository 명단 조회(join fetch)를 추가한다** - `2cf4a76` (feat)
2. **Task 2: AttendanceService.getRoster + check(upsert) — 예약자 검증과 소급 수정** - `7f2a79a` (feat)
3. **Task 3: AttendanceServiceTest — 명단·미체크·소급 수정·예약자 아님·저녁반 거부** - `a8fa1e0` (test)

**Plan metadata:** (다음 커밋에서 STATE.md·ROADMAP.md와 함께 기록)

## Files Created/Modified
- `src/main/kotlin/com/goldwrestling/attendance/AttendanceRepository.kt` - `findAllByClassSessionIdWithMember`(join fetch member) 추가
- `src/main/kotlin/com/goldwrestling/attendance/dto/AttendanceRequests.kt` - `CheckAttendanceRequest`(예약제/1:1 체크 요청)·`AddEveningAttendanceRequest`(06-07 전용, 함께 선언) 신설
- `src/main/kotlin/com/goldwrestling/attendance/dto/AttendanceResponses.kt` - `AttendanceResponse`·`AttendanceRosterEntryResponse`·`ClassSessionAttendanceRosterResponse` 신설
- `src/main/kotlin/com/goldwrestling/attendance/AttendanceService.kt` - `getRoster`(명단 프리로드)·`check`(출석 upsert), 이용권 원장 미주입
- `src/main/kotlin/com/goldwrestling/attendance/Attendance.kt` - `checkedBy`/`checkedAt`을 `var`로 변경(소급 정정 시 최신 확인자·시각 갱신)
- `src/test/kotlin/com/goldwrestling/attendance/AttendanceServiceTest.kt` - 명단·미체크·소급 수정·비예약자·저녁반 거부·차감 무관 불변식 7개 통합테스트

## Decisions Made
- `Attendance.checkedBy`/`checkedAt`을 `val`→`var`로 변경(자세한 배경은 아래 편차 1번)
- `AttendanceService`는 `check()` 내에서 `memberRepository.findById`로 회원 엔티티를 명시적으로 조회한다 — 활성 예약자 검증(리스트 순회)과 엔티티 획득(신규 Attendance 구성용)의 책임을 분리해, 조인 결과의 부수 활용에 의존하지 않는다

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Attendance.checkedBy/checkedAt을 val에서 var로 변경**
- **Found during:** Task 2 (AttendanceService.check 구현)
- **Issue:** 이 플랜의 명시적 behavior("checkedBy/checkedAt이 매 호출마다 갱신된다 — 마지막으로 확인한 관리자·시각")를 만족하려면 두 필드가 가변이어야 하는데, 06-02가 만든 `Attendance` 엔티티는 "누가 언제 체크했는지의 이력이라 val로 고정한다"는 KDoc과 함께 두 필드를 불변으로 선언해 뒀다. Task 2의 `<files>`에는 `AttendanceService.kt`만 명시돼 있었고 `Attendance.kt`는 빠져 있었다 — 계획 수립 시점에 이 불일치가 반영되지 않은 것으로 보인다.
- **Fix:** `checkedBy`·`checkedAt`을 `var`로 변경하고, 클래스 KDoc을 "status·checkedBy·checkedAt이 var다(D-127 소급 수정 허용) — 마지막으로 확인한 관리자·시각을 나타낸다"로 정정. `member`·`classSession`·`passTransaction`·`createdAt`은 여전히 `val`(연결·생성 이력)로 유지해 불변성 원칙을 부분적으로 보존했다.
- **Files modified:** `src/main/kotlin/com/goldwrestling/attendance/Attendance.kt`
- **Verification:** `AttendanceServiceTest.'불참으로 체크했다가 출석으로 정정할 수 있다'`가 재체크 후 행 수 1·status ATTENDED를 실제 DB로 확인
- **Committed in:** `7f2a79a` (Task 2 커밋)

---

**Total deviations:** 1 auto-fixed (Rule 1 - 버그 수정, 계획 파일 목록 누락으로 인한 엔티티 불일치)
**Impact on plan:** 이 플랜이 명시한 behavior를 실제로 만족시키기 위한 필수 수정. 범위 확장 없음 — 같은 파일의 관련 필드 2개만 조정했다.

## Issues Encountered
None - 그 외에는 계획대로 실행됐다.

## User Setup Required

None - 외부 서비스 설정 불필요.

## Next Phase Readiness

- `AttendanceService`가 06-07(저녁반 0.5회 차감 경로)이 `addEveningAttendance`를 추가할 확장 지점으로 준비됐다 — 생성자에 이용권 원장 리포지토리가 아직 없으므로 06-07이 `passRepository`·`passTransactionRepository`를 새로 주입해야 한다(계획에 이미 명시됨)
- `CheckAttendanceRequest`·`AddEveningAttendanceRequest`·`ClassSessionAttendanceRosterResponse` 등 DTO가 06-08(`AdminAttendanceController`)이 그대로 쓸 수 있는 형태로 준비됐다 — 06-08 이전에는 이 DTO들을 노출하는 컨트롤러가 없으므로 `docs/api/openapi.yaml` 재생성은 하지 않았다(실제 API 표면 변경 없음, 06-08에서 함께 처리)
- 블로커 없음

---

## 이번에 쓴 기술

1. **`join fetch`(JPQL 조인 페치)** — 연관 엔티티를 한 쿼리로 함께 로딩하는 JPQL 문법
   - **이 코드에서 왜 필요했는가**: `getRoster`가 명단 각 항목에 `member.name`을 담아야 하는데, `Attendance.member`는 `LAZY`(지연 로딩)다. 명단을 순회하며 `attendance.member.name`을 읽으면 그 순간마다 DB에 `SELECT`가 한 번씩 더 나간다 — 정원 10명 수업이면 명단 조회 1번이 조회 11번(본 쿼리 1 + N)이 된다.
   - **안 썼으면 뭐가 깨지는가**: 기능은 동작하지만, 수업 하나 명단을 열 때마다 회원 수만큼 쿼리가 늘어나는 N+1 문제가 생긴다. 관리자가 스케줄 보드에서 여러 셀을 훑어보면 이게 누적돼 화면이 눈에 띄게 느려진다.

2. **★ 프리로드 후 애플리케이션 레벨 병합(join in memory)** — DB 조인 대신, 두 쿼리 결과를 애플리케이션 코드에서 맵으로 합치는 방식
   - **이 코드에서 왜 필요했는가**: 예약제/1:1 명단은 "활성 예약자 전원"이 기준이고 "출석 레코드"는 있을 수도 없을 수도 있다(미체크). 이건 예약과 출석을 SQL JOIN(특히 LEFT JOIN)으로 한 번에 가져올 수도 있지만, 이 저장소는 이미 존재하는 두 개의 독립된 join fetch 쿼리(`ReservationRepository`·`AttendanceRepository`)를 재사용하는 관례를 따른다 — 예약자 리스트를 기준으로, 출석 레코드를 `memberId`로 색인한 맵에서 찾아 있으면 채우고 없으면 `null`(미체크)로 둔다.
   - **안 썼으면 뭐가 깨지는가**: 새 커스텀 LEFT JOIN 쿼리를 하나 더 만들어야 했다면 "명단은 예약자 리포지토리 조회 + 출석 리포지토리 조회를 조합한다"는 이 저장소의 기존 패턴(관리자 스케줄 보드도 동일)에서 벗어나 유지보수 지점이 하나 늘어난다.

3. **Kotlin `var`/`val`과 JPA 엔티티 가변성 설계** — 필드를 재대입 가능하게 할지(`var`) 불변으로 고정할지(`val`)의 선택
   - **이 코드에서 왜 필요했는가**: "소급 수정을 허용하되 누가 무엇을 바꿨는지는 추적한다"는 이 프로젝트의 반복되는 요구(D-127)를 표현하려면, "정정 가능한 최신 상태"(status·checkedBy·checkedAt)와 "이 기록이 무엇에 대한 것인지"(member·classSession·createdAt)를 필드 단위로 구분해야 한다. 전자는 `var`, 후자는 `val`로 두는 게 곧 그 구분을 코드로 강제하는 것이다.
   - **안 썼으면 뭐가 깨지는가**: 전부 `val`로 두면(06-02 원래 설계) 재체크 시 "누가 마지막으로 확인했는지"를 갱신할 방법이 없어 정정 이력이 실제와 어긋난다. 반대로 전부 `var`로 두면 `member`·`classSession` 같은 연결 관계까지 실수로 재대입할 수 있는 문을 열어 버그 위험이 커진다.

4. **트랜잭션 경계와 지연 로딩 프록시(`LazyInitializationException`) 회피** — `@Transactional` 메서드 안에서만 `LAZY` 연관에 접근하는 관례
   - **이 코드에서 왜 필요했는가**: `AttendanceResponse.from`이 `attendance.member.name`·`attendance.classSession.classDate` 등 `LAZY` 필드를 읽는다. 이 변환은 서비스 메서드(트랜잭션이 열려 있는 곳) 안에서만 호출돼야 한다 — 트랜잭션이 끝난 뒤(예: 컨트롤러)에서 호출하면 프록시가 이미 닫힌 세션을 참조해 예외가 난다.
   - **안 썼으면 뭐가 깨지는가**: 컨트롤러 레이어에서 응답 변환을 시도하면 런타임에 `LazyInitializationException`이 나서 500 에러가 응답된다 — 06-08(컨트롤러)이 이 서비스를 호출할 때 반드시 지켜야 할 경계다.

---
*Phase: 06-operations*
*Completed: 2026-08-18*

## Self-Check: PASSED

All created files verified to exist on disk; all task commit hashes (`2cf4a76`, `7f2a79a`, `a8fa1e0`) verified present in git log.
