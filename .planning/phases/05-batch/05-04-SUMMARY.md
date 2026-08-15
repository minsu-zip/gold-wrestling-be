---
phase: 05-batch
plan: 04
subsystem: database
tags: [jpa, kotlin, batch, postgresql, testcontainers, projection]

# Dependency graph
requires:
  - phase: 05-batch (05-02)
    provides: "V9 스키마(pass_transaction 시스템 주체·member.returnedFromLeaveAt·batch_execution) — 이 플랜의 벌크 조회가 읽는 실제 컬럼"
  - phase: 05-batch (05-03)
    provides: "InactivityDueDateCalculator — 05-06이 이 플랜의 조회 결과를 입력으로 받아 기준일·부족분을 계산"
provides:
  - "PassRepository — findMemberIdsWithDeductibleSessionPass(BATCH-02 예외 3종 필터)·findLastDeductibleSessionPassRegistrationDates·findDeductibleSessionPasses(회당 재선택용)"
  - "ReservationRepository.findLastActiveReservationClassDates — 기준일 후보 ②"
  - "PassTransactionRepository.findLastPositiveAdjustTimestamps(후보 ⑤)·findInactivityEventTimestamps(D-106 멱등 근거)"
  - "MemberRepository.findReturnedFromLeaveTimestamps — 기준일 후보 ③"
  - "common/projection/MemberDateProjection·MemberTimestampProjection — 이 저장소 최초의 인터페이스 스칼라 프로젝션(D-115)"
affects: [05-05, 05-06, 05-07]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "인터페이스 스칼라 프로젝션(common/projection) — JPQL `as` 별칭을 getter 이름과 맞춰 GROUP BY 결과를 타입 안전하게 매핑(D-115)"
    - "배치 벌크 조회는 소유 리포지토리에 둔다(batch 패키지에 두지 않음) — findDeductionCandidates가 pass에 있는 것과 동일 관례"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/common/projection/MemberDateProjection.kt
    - src/main/kotlin/com/goldwrestling/common/projection/MemberTimestampProjection.kt
    - src/test/kotlin/com/goldwrestling/batch/BatchFixtures.kt
    - src/test/kotlin/com/goldwrestling/batch/InactivityBatchQueryTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/pass/PassRepository.kt
    - src/main/kotlin/com/goldwrestling/reservation/ReservationRepository.kt
    - src/main/kotlin/com/goldwrestling/pass/PassTransactionRepository.kt
    - src/main/kotlin/com/goldwrestling/member/MemberRepository.kt
    - docs/decisions.md

key-decisions:
  - "D-115: 배치 벌크 조회는 Array<Any> 캐스팅 대신 인터페이스 스칼라 프로젝션으로 반환 — 타입 안전성을 이 저장소 관례로 고정"
  - "CANCELED Pass 픽스처는 상태를 직접 대입하지 않고 PassRepository.cancelIfNotCanceled 실제 취소 경로로 만든다 — ck_pass_cancellation(V4)이 취소 메타데이터 완전성을 강제하기 때문"
  - "findInactivityEventTimestamps KDoc의 'group by' 리터럴이 acceptance grep(정확히 1건)과 충돌해 표현을 '집계 없이'로 수정 — 05-03의 Clock 문자열 충돌과 동일 유형"

patterns-established:
  - "빈 memberIds 컬렉션으로 IN절 벌크 조회를 실행해도 예외 없이 빈 결과를 반환함을 실제 실행으로 확인(RESEARCH가 요구한 검증) — 호출부가 방어적으로 빈 목록을 걸러낼 필요 없음을 KDoc에 명시"

requirements-completed: [BATCH-01, BATCH-02]

# Metrics
duration: ~50min
completed: 2026-08-15
---

# Phase 5 Plan 4: 배치 전용 벌크 조회 쿼리 Summary

**배치가 회원 수백 명을 회원별 개별 쿼리 없이 판정할 수 있도록 IN절 벌크 조회 6개(대상 회원·차감 후보·기준일 후보 4종)를 인터페이스 스칼라 프로젝션으로 반환하고, BATCH-02 예외 3종(휴회·잔여 0·만료)을 대상 회원 조회 쿼리 한 곳의 필터로만 구현**

## Performance

- **Duration:** ~50분 (컨텍스트 로딩 포함)
- **Started:** 2026-08-15T12:30 (05-03 완료 직후)
- **Completed:** 2026-08-15T12:44
- **Tasks:** 2 완료 (각 TDD RED→GREEN 2커밋 + 문서 정합 1커밋)
- **Files modified:** 9 (신규 4, 수정 5)

## Accomplishments
- `PassRepository`에 배치 대상 회원 조회(`findMemberIdsWithDeductibleSessionPass`)를 추가하고, BATCH-02의 예외 3종(휴회 중·잔여 0·만료)을 이 한 쿼리의 필터로만 구현 — RESEARCH Pitfall 2가 경고한 "예외를 세 곳에 흩뿌리는" 함정을 피했다
- 기준일 후보 4종 중 데이터가 실제로 존재하는 4종(②예약 수업일·③복귀일·④SESSION_PASS 등록일·⑤양(+) 가감일)을 `Reservation`·`Member`·`PassTransaction`·`Pass` 각 소유 리포지토리에 IN절 벌크 조회로 추가 — 회원 300명 처리 시 기준일 조회 쿼리가 5개 이하(success_criteria 충족)
- `common/projection/MemberDateProjection`·`MemberTimestampProjection` 도입(D-115, 이 저장소 최초의 인터페이스 프로젝션) — context7로 Spring Data JPA 4.1.0의 `Tuple` 기반 프로젝션 매핑을 확인해 `Array<Any>` 캐스팅 없이 타입 안전하게 구현
- `findDeductibleSessionPasses`가 예약용 `findDeductionCandidates`와 달리 잔여 0.5인 장도 포함하도록 분리(D-109 부분 차감의 실현부), `findInactivityEventTimestamps`는 집계 없이 건별 반환해 D-106 멱등성의 근거 데이터를 그대로 노출
- `InactivityBatchQueryTest` 22케이스(BATCH-02 3종 독립 검증·`endDate` 경계 2종·빈 `memberIds` 실제 실행 검증 포함) 전부 통과, 기존 `PassRepositoryTest`·`ReservationRepositoryTest`·`PassDeductionCandidateTest` 등 전체 스위트(637 테스트) 회귀 없음

## Task Commits

Each task was committed atomically (TDD RED→GREEN):

1. **Task 1: 프로젝션 문법 검증 + 대상 회원·등록일·차감 후보 조회(PassRepository)**
   - RED: `5c16967` (test)
   - GREEN: `e3aa6c7` (feat)
   - 문서 정합(D-115): `79451f6` (docs)
2. **Task 2: 기준일 후보·이력 벌크 조회(Reservation·PassTransaction·Member)**
   - RED: `72066ee` (test)
   - GREEN: `23402d9` (feat)

**Plan metadata:** (다음 커밋에서 기록)

## Files Created/Modified
- `src/main/kotlin/com/goldwrestling/common/projection/MemberDateProjection.kt` - 회원 id↔날짜 스칼라 프로젝션(신규)
- `src/main/kotlin/com/goldwrestling/common/projection/MemberTimestampProjection.kt` - 회원 id↔타임스탬프 스칼라 프로젝션(신규)
- `src/main/kotlin/com/goldwrestling/pass/PassRepository.kt` - 배치 조회 3개 추가(대상 회원·등록일·차감 후보), 기존 메서드 무변경
- `src/main/kotlin/com/goldwrestling/reservation/ReservationRepository.kt` - `findLastActiveReservationClassDates` 추가
- `src/main/kotlin/com/goldwrestling/pass/PassTransactionRepository.kt` - `findLastPositiveAdjustTimestamps`·`findInactivityEventTimestamps` 추가
- `src/main/kotlin/com/goldwrestling/member/MemberRepository.kt` - `findReturnedFromLeaveTimestamps` 추가
- `src/test/kotlin/com/goldwrestling/batch/BatchFixtures.kt` - 배치 통합테스트 공유 픽스처(신규)
- `src/test/kotlin/com/goldwrestling/batch/InactivityBatchQueryTest.kt` - 벌크 조회 6종 22케이스(신규)
- `docs/decisions.md` - D-115(스칼라 프로젝션 도입 근거) 추가

## Decisions Made

- **프로젝션 문법 검증(계획 action (1) 이행)**: context7 MCP로 Spring Data JPA(`/spring-projects/spring-data-jpa`) 공식 문서를 조회해, 커스텀 `@Query` JPQL의 `as` 별칭이 인터페이스 프로젝션 getter 이름과 매칭되면 `SimpleJpaRepository`가 `Tuple` 기반 프록시로 결과를 매핑함을 확인했다(`CollectionAwareProjectionFactory`가 getter를 `PropertyDescriptor`로 introspect). Boot 3→4 사이에 이 메커니즘이 바뀌었다는 근거가 없고, 로컬 spring-data-jpa jar 버전(4.1.0)도 확인했다 — `List<Array<Any>>` 대안은 쓰지 않았다.
- **`ck_pass_cancellation`(V4) 위반 발견 즉시 수정(Rule 1)**: 취소된 `SESSION_PASS`만 가진 회원을 테스트하려고 `Pass(status = CANCELED)`를 직접 생성했더니 `canceled_at`·`cancel_reason`·`canceled_by_admin_id` 완전성 CHECK가 INSERT를 거부했다 — 실제 취소 경로(`cancelIfNotCanceled`)로 만들도록 테스트를 고쳐 해결했다.
- **`findInactivityEventTimestamps` KDoc의 grep 충돌 수정(Rule 1)**: acceptance criteria가 `grep -c "group by" == 1`로 "이 메서드는 집계하지 않는다"를 검증하는데, KDoc 설명 문장이 "group by 없이"라는 문구를 그대로 써서 매치 수가 2가 됐다 — 05-03의 "Clock" 문자열 충돌과 동일한 유형이라 표현만 "집계 없이"로 바꿔 해결했다(의미 변화 없음).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] CANCELED Pass 픽스처가 V4 CHECK를 위반**
- **Found during:** Task 1 GREEN 검증(테스트 최초 실행)
- **Issue:** `PassStatus.CANCELED`만 직접 대입한 테스트 픽스처가 `ck_pass_cancellation`(취소 메타데이터 완전성) 위반으로 `DataIntegrityViolationException`을 던졌다
- **Fix:** 두 테스트를 `passRepository.cancelIfNotCanceled(...)` 실제 취소 경로로 바꿔 취소 메타데이터가 함께 채워지게 했다
- **Files modified:** `src/test/kotlin/com/goldwrestling/batch/InactivityBatchQueryTest.kt`
- **Verification:** 재실행 시 두 테스트 모두 통과
- **Committed in:** `e3aa6c7` (Task 1 GREEN 커밋에 포함)

**2. [Rule 1 - Bug] `findInactivityEventTimestamps` KDoc 문자열이 자체 acceptance grep과 충돌**
- **Found during:** Task 2 acceptance criteria 검증(`grep -c "group by"` 실행 후)
- **Issue:** KDoc이 "group by 없이 행 하나하나를 반환한다"고 설명하면서 리터럴 "group by"를 포함해, 실제 `group by` 사용이 `findLastPositiveAdjustTimestamps` 1건뿐인데도 grep count가 2가 됐다
- **Fix:** 문장을 "집계 없이"로 바꿔 같은 의미를 유지하고 리터럴 문자열만 제거
- **Files modified:** `src/main/kotlin/com/goldwrestling/pass/PassTransactionRepository.kt`
- **Verification:** `grep -c "group by" ...` → 1, `./gradlew build` 재실행 그린
- **Committed in:** `23402d9` (Task 2 GREEN 커밋에 포함)

---

**Total deviations:** 2 auto-fixed (Rule 1 — 둘 다 버그 수정, 도메인 로직 변경 없음)
**Impact on plan:** 계획서의 벌크 조회 6개·필터·정렬·KDoc 내용은 그대로 옮겼고 추가·삭제 없음. 편차는 테스트 픽스처 수정과 KDoc 문구 수정뿐이다.

## Issues Encountered

None — Docker Desktop이 이미 기동돼 있어 Testcontainers 기반 통합테스트가 즉시 실행됐다.

## User Setup Required

None - 이 플랜은 리포지토리 메서드와 프로젝션 인터페이스만 추가했다. 환경변수·외부 서비스 설정이 필요 없다.

## Next Phase Readiness

- 05-05(배치 차감 실행부)가 `findDeductibleSessionPasses`를 차감 1회마다 재호출해 회당 재선택(D-109)을 구현할 수 있다 — 회당 재조회 계약이 KDoc에 명시돼 있다
- 05-06(`InactivityBatchRunner`)이 `findMemberIdsWithDeductibleSessionPass`로 대상 회원을 얻고, 4종 벌크 조회 결과 + `InactivityDueDateCalculator`(05-03)를 조합해 회원별 부족분을 계산할 수 있다 — 벌크 조회가 5개 이하(성공 기준 충족)
- 빈 `memberIds`로 4종 조회를 호출해도 예외 없이 빈 결과가 반환됨을 실제로 확인했다 — 05-06이 "대상 회원 0명" 엣지 케이스를 방어 코드 없이 그대로 통과시킬 수 있다
- REQUIREMENTS.md는 이 플랜에서 건드리지 않았다 — BATCH-01·02의 Complete 전환은 05-09가 일괄 처리한다(project_rules 지시)
- **청크 A 마지막 플랜** — 다음 단계로 `./gradlew ktlintFormat`·`./gradlew build` 그린 확인 후 `create-pr` 스킬로 dev 대상 PR을 생성한다(API 변경 없음, `generateApiDocs` 불필요)
- Blocker 없음

---
*Phase: 05-batch*
*Completed: 2026-08-15*

## Self-Check: PASSED

All created/modified files and task commits (5c16967, e3aa6c7, 79451f6, 72066ee, 23402d9) verified present.
