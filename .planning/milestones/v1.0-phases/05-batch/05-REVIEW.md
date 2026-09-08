---
phase: 05-batch
reviewed: 2026-08-15T00:00:00Z
depth: standard
files_reviewed: 37
files_reviewed_list:
  - src/main/kotlin/com/goldwrestling/batch/AdminBatchController.kt
  - src/main/kotlin/com/goldwrestling/batch/BatchExecution.kt
  - src/main/kotlin/com/goldwrestling/batch/BatchExecutionRepository.kt
  - src/main/kotlin/com/goldwrestling/batch/BatchExecutionStatus.kt
  - src/main/kotlin/com/goldwrestling/batch/BatchTrigger.kt
  - src/main/kotlin/com/goldwrestling/batch/dto/BatchExecutionResponse.kt
  - src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt
  - src/main/kotlin/com/goldwrestling/batch/InactivityBatchScheduler.kt
  - src/main/kotlin/com/goldwrestling/batch/InactivityDeductionService.kt
  - src/main/kotlin/com/goldwrestling/batch/InactivityDueDateCalculator.kt
  - src/main/kotlin/com/goldwrestling/common/projection/MemberDateProjection.kt
  - src/main/kotlin/com/goldwrestling/common/projection/MemberTimestampProjection.kt
  - src/main/kotlin/com/goldwrestling/config/SchedulingConfig.kt
  - src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt
  - src/main/kotlin/com/goldwrestling/member/Member.kt
  - src/main/kotlin/com/goldwrestling/member/MemberRepository.kt
  - src/main/kotlin/com/goldwrestling/pass/PassRepository.kt
  - src/main/kotlin/com/goldwrestling/pass/PassTransaction.kt
  - src/main/kotlin/com/goldwrestling/pass/PassTransactionRepository.kt
  - src/main/kotlin/com/goldwrestling/reservation/ReservationRepository.kt
  - src/main/resources/db/migration/V9__relax_pass_transaction_subject_and_add_batch_execution.sql
  - src/test/kotlin/com/goldwrestling/batch/AdminBatchControllerTest.kt
  - src/test/kotlin/com/goldwrestling/batch/BatchExecutionRepositoryTest.kt
  - src/test/kotlin/com/goldwrestling/batch/BatchFixtures.kt
  - src/test/kotlin/com/goldwrestling/batch/InactivityBatchExpiryVerificationTest.kt
  - src/test/kotlin/com/goldwrestling/batch/InactivityBatchFailureIsolationTest.kt
  - src/test/kotlin/com/goldwrestling/batch/InactivityBatchIdempotencyTest.kt
  - src/test/kotlin/com/goldwrestling/batch/InactivityBatchQueryTest.kt
  - src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunnerTest.kt
  - src/test/kotlin/com/goldwrestling/batch/InactivityDeductionConcurrencyTest.kt
  - src/test/kotlin/com/goldwrestling/batch/InactivityDeductionRaceTest.kt
  - src/test/kotlin/com/goldwrestling/batch/InactivityDeductionServiceTest.kt
  - src/test/kotlin/com/goldwrestling/batch/InactivityDueDateCalculatorTest.kt
  - src/test/kotlin/com/goldwrestling/member/MemberStatusChangeTest.kt
  - src/test/kotlin/com/goldwrestling/pass/PassRepositoryTest.kt
  - docs/api/openapi.yaml
  - docs/decisions.md
findings:
  critical: 4
  warning: 9
  info: 5
  total: 18
status: issues_found
---

# Phase 5: Code Review Report

**Reviewed:** 2026-08-15
**Depth:** standard
**Files Reviewed:** 37
**Status:** issues_found

## Summary

Phase 5(2주 미사용 자동 차감 배치)의 프로덕션 코드 21개 + 테스트 13개 + 마이그레이션 1개를 검토했다.
단일 실행 경로의 멱등·캐치업 설계(D-106)는 견고하고 테스트 커버리지도 두껍다. 그러나 **회원의 잔여
횟수를 실제로 파괴할 수 있는 결함 4건**이 남아 있다.

핵심 문제는 "상태 기반 부족분 계산 = 멱등"이라는 전제가 **순차 실행에서만 참**이라는 점이다.
`shortfall` 계산과 `deductOnce` 실행 사이에 원자성이 없고, 실행을 직렬화하는 장치(락·중복 실행 거부)가
어디에도 없다. 관리자가 수동 실행 버튼을 두 번 누르거나 04:00 cron과 겹치면 이중 차감이 난다 —
`InactivityDeductionConcurrencyTest`가 "세 스레드 동시 호출 → 정확히 2회 성공"을 스스로 증명하고 있어,
조건부 UPDATE는 음수만 막을 뿐 이중 차감을 막지 못한다는 것이 이미 코드베이스 안에 문서화돼 있다.

두 번째 축은 **배포 시점 리스크**다. 스케줄러에 kill switch가 없고 소급 차감에 cutoff가 없어,
이 코드가 배포되는 순간 첫 실행이 "이용권 등록일 이후 전체 미사용 기간"을 한 번에 몰아 차감한다.
게다가 policies §4.3의 기준일 후보 ①(마지막 출석일)이 Phase 6까지 영구 null이라, 저녁반만 다니는
`SESSION_PASS` 회원은 `EVENING_HALF` 0.5 차감을 받으면서 동시에 2주마다 `INACTIVITY` 1.0을 더 맞는다.
CLAUDE.md 문서 우선순위상 policies.md가 코드를 이긴다 — 기준일 후보가 미완인 채로 cron을 켜는 것은
정책 위반이다.

청크 귀속: CR-01·CR-02(일부)·WR-02·WR-04·WR-05·IN-03은 현재 브랜치(05-08/05-09)에서 새로 열린
경로(스케줄러·수동 실행 API)에 직접 걸린다. CR-03·CR-04·WR-01·WR-03·WR-06·WR-07~09는 이미 dev에
머지된 05-01~05-07 산출물이므로 후속 PR 대상 판단이 필요하다.

## Critical Issues

### CR-01: 배치 동시 실행 시 이중 차감 — 실행을 직렬화하는 장치가 없다

**File:** `src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt:63-128`,
`src/main/kotlin/com/goldwrestling/batch/AdminBatchController.kt:36-41`,
`src/main/kotlin/com/goldwrestling/batch/InactivityDeductionService.kt:53-84`
**청크:** 05-06(러너) + 05-08(수동 실행 API, 현재 PR)

**Issue:**
`run()`은 ① `findInactivityEventTimestamps`로 원장을 읽어 부족분을 계산하고 ② 부족분만큼
`deductOnce`를 반복 호출한다. ①과 ② 사이에 어떤 락도, 조건부 갱신도 없다. `deductOnce`의 조건부
UPDATE(`remainingCount + :amount >= 0`)는 **잔여가 음수가 되는 것만** 막고, "이 주기의 차감이 이미
존재하는가"는 전혀 보지 않는다.

이것은 추측이 아니다 — 같은 phase의 `InactivityDeductionConcurrencyTest.kt:119-132`가
`잔여 2회를 세 스레드가 동시에 차감하면 정확히 두 번만 성공한다`로 **동시 호출이 두 번 다 성공함**을
단언한다. 즉 두 개의 `run()`이 겹치면 각각 shortfall=1을 읽고 각각 차감해 총 2회가 나간다.

재현 경로 (전부 현실적):
1. 관리자가 `POST /api/admin/batch/inactivity-runs` 버튼을 더블클릭 (응답이 느리면 반드시 일어난다 → WR-05)
2. 관리자 2명이 동시에 수동 실행
3. 04:00 cron 실행 중 관리자가 수동 실행 (cron은 스케줄러 스레드, 수동은 Tomcat 스레드라 서로 다른 스레드다)

D-108이 "단일 EC2라 ShedLock 불필요"라고 한 것은 **인스턴스 간** 중복만 다룬 것이고, 한 인스턴스
안의 cron 스레드 ↔ HTTP 스레드 경쟁은 다루지 않았다. OpenAPI 설명("중복 실행해도 안전하다")이
사실이 아닌 상태로 FE/관리자에게 노출돼 있다.

영향: Core Value("회원이 보는 잔여 = 실제 사용 가능 횟수") 직접 파괴. 이력은 2건 남으므로 정합성
불변식(잔여 == 이력 합계)은 유지되지만, 차감된 횟수 자체가 정책보다 많다. 되돌리려면 관리자가
수동 가감으로 보정해야 한다.

**Fix:**
실행 전체를 DB 수준에서 직렬화한다. Postgres advisory lock이 새 테이블·의존성 없이 가장 싸다.

```kotlin
// BatchExecutionRepository.kt (또는 전용 LockRepository)
@Query(value = "select pg_try_advisory_xact_lock(:key)", nativeQuery = true)
fun tryLock(@Param("key") key: Long): Boolean

// InactivityBatchRunner.kt — run() 전체를 감싸는 얇은 진입 메서드를 별도 빈에 둔다
@Transactional
fun tryAcquireRunLock(): Boolean = lockRepository.tryLock(INACTIVITY_BATCH_LOCK_KEY)
```

락 획득 실패 시 선택지:
- 수동 실행 API: `409 CONFLICT` + 도메인 예외(`BatchAlreadyRunningException`)로 거부하고
  `ProblemDetail`로 응답 (D-114의 "새 에러코드 추가 안 함"은 재검토 대상 — 거부할 요청이 없다는
  전제가 이 결함으로 깨졌다)
- cron: 로그만 남기고 스킵

대안(더 강한 보장): `pass_transaction`에 "회원 + 차감 주기 인덱스" 부분 유니크 인덱스를 두어
같은 주기의 `INACTIVITY`가 물리적으로 2건 들어갈 수 없게 만든다 — D-021의 "DB 제약 우선" 원칙과
가장 일치한다. 어느 쪽을 고르든 **동시 `run()` 2개에서 총 차감이 1회임을 단언하는 동시성 테스트**를
함께 추가한다(conventions §10.4).

---

### CR-02: 최초 실행이 이용권 등록일까지 소급해 몰아 차감 — cutoff도 kill switch도 없다

**File:** `src/main/kotlin/com/goldwrestling/batch/InactivityDueDateCalculator.kt:53-60`,
`src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt:111-119`,
`src/main/kotlin/com/goldwrestling/batch/InactivityBatchScheduler.kt:23-26`,
`src/main/kotlin/com/goldwrestling/config/SchedulingConfig.kt:13-15`
**청크:** 05-03(계산) + 05-08(스케줄러, 현재 PR)

**Issue:**
`expectedDeductionCount`는 `floor(기준일→오늘 경과일 / 14)`를 **상한 없이** 반환하고, 기준일 후보에는
하한(정책 시행일·배치 도입일)이 없다. 배치가 존재하지 않았던 과거 기간도 전부 "밀린 주기"로 계산된다.

구체 시나리오: 관리자가 200일 전에 `SESSION_PASS`(잔여 10회, 유효기간 1년)를 등록했고 회원이 예약을
한 번도 하지 않았다면 — 배포 후 첫 실행에서 `expected = floor(200/14) = 14`, 실제 `INACTIVITY` 이력
0건 → shortfall 14 → `deductOnce`가 잔여 10회를 **전부 소진**할 때까지 반복한다. 한 번의 배치 실행이
회원의 이용권을 0으로 만든다.

D-106의 "배치가 며칠 밀리면 다음 실행이 몰아서 차감한다"는 **배치가 이미 돌고 있었다는 전제** 위의
설계다. "배치가 아예 없었던 기간"과 "배치가 며칠 죽었던 기간"을 코드가 구분하지 못한다.

이 실행을 막을 수단도 없다: `@EnableScheduling` + `@Scheduled`가 무조건 활성이고
`application.yml`에 on/off 프로퍼티가 없다. 배포 다음날 04:00에 자동으로 실행된다.
(부수적으로, 모든 `@SpringBootTest` 컨텍스트에서도 스케줄러가 켜져 있어 CI가 04:00 KST를 걸치면
테스트 DB에 배치가 난입할 수 있다.)

영향: 데이터 손실(회원 잔여 횟수 대량 소멸). 되돌리려면 회원별 수동 가감이 필요하고, 이미 나간
`INACTIVITY` 이력을 지울 수 없으므로(append-only) 상쇄 이력이 원장을 어지럽힌다.

**Fix:**
두 가지를 함께 넣는다.

1. 정책 시행일(effective date) 하한 — 기준일이 시행일보다 이르면 시행일로 끌어올린다.

```kotlin
// InactivityDueDateCalculator
fun resolveDueDate(candidates: InactivityDueDateCandidates, policyEffectiveDate: LocalDate): LocalDate? =
    listOfNotNull(/* 5종 후보 */).maxOrNull()?.coerceAtLeast(policyEffectiveDate)
```

`policyEffectiveDate`는 `@ConfigurationProperties`로 주입하고 `.env.example`에 키를 추가한다.
이 값이 있어야 "배치 도입 이전의 미사용은 부채가 아니다"가 코드로 표현된다.

2. 스케줄러 kill switch — 운영 중 사고가 났을 때 재배포 없이 끌 수 있어야 한다.

```kotlin
@Component
@ConditionalOnProperty(name = ["gold-wrestling.batch.inactivity.enabled"], havingValue = "true")
class InactivityBatchScheduler(...)
```

D-107·D-108에 이 결정을 기록하고, "회원별 1회 실행 최대 차감 수" 상한(예: 3회)도 함께 검토한다.

---

### CR-03: policies §4.3 기준일 후보 ①(출석일)이 미구현인 채로 cron이 켜졌다 — 저녁반 회원 부당 차감

**File:** `src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt:94-95`,
`src/main/kotlin/com/goldwrestling/batch/InactivityDueDateCalculator.kt:18-24`
**청크:** 05-06 (dev 머지됨) — 단, cron 활성화는 05-08(현재 PR)

**Issue:**
`lastAttendanceDate = null`이 하드코딩돼 있고 "Phase 6이 채운다"고 주석돼 있다. 그런데 policies §4.3은
기준일 후보 5종 중 ①을 **마지막 출석일**로 명시하고, §6은 "2주 미사용 차감의 기준일 중 마지막 출석일은
이 출석 기록을 기준으로 한다"고 못박는다. CLAUDE.md 문서 우선순위상 policies.md > 코드다.

실제 피해 경로 — policies §4.2의 저녁반 0.5회 참여 회원:
`SESSION_PASS` 보유 회원이 저녁반에만 나온다. 관리자가 현장에서 0.5회를 수동 차감하면
`PassTransaction(reason = EVENING_HALF, amount = -0.5)`가 남는다. 그런데 이 회원의 기준일 후보는:
- ① 출석일 → null (Phase 6 전)
- ② 예약 수업일 → 없음 (저녁반은 예약 불필요)
- ③ 복귀일 → 없음
- ④ 등록일 → 3개월 전
- ⑤ 양(+) `ADMIN_ADJUST` → 없음 (`EVENING_HALF`는 음수이고 사유 코드도 다르다)

→ 기준일이 3개월 전으로 잡혀 **매일 체육관에 나오는 회원이 2주마다 1.0회씩 추가 차감**된다.
저녁반 참여 0.5 + 미사용 1.0 = 2주에 1.5회. 정책 취지(D-105 "실제로 체육관에 나오는 회원을 차감하는
결과 — 취지 위배"로 명시 기각한 것)와 정면으로 어긋난다.

**Fix:**
Phase 6(Attendance) 전까지 셋 중 하나를 반드시 적용한다.

1. (권장) `EVENING_HALF` 이력을 기준일 후보로 임시 인정 — 저녁반 참여의 유일한 현존 증거다.

```kotlin
// PassTransactionRepository
@Query(
    "select pt.pass.member.id as memberId, max(pt.occurredAt) as timestamp from PassTransaction pt " +
        "where pt.pass.member.id in :memberIds " +
        "and pt.reason = com.goldwrestling.pass.TransactionReason.EVENING_HALF " +
        "group by pt.pass.member.id",
)
fun findLastEveningHalfTimestamps(@Param("memberIds") memberIds: Collection<Long>): List<MemberTimestampProjection>
```

2. CR-02의 kill switch로 cron을 꺼 두고 Phase 6에서 켠다.
3. Phase 6을 이 배치보다 먼저 완료한다.

어느 쪽이든 policies.md §4.3에 "①은 Phase 6까지 X로 대체한다"를 명시하고 decisions.md에 기록한다
(CLAUDE.md 규칙: 스펙을 바꿔야 하면 `.planning/`이 아니라 `docs/`를 고친다).

---

### CR-04: `ON_LEAVE → INACTIVE → ACTIVE` 경로에서 복귀 시각이 기록되지 않아 휴회 기간이 소급 차감된다

**File:** `src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt:137-141`
**청크:** 05-02 (dev 머지됨)

**Issue:**
```kotlin
val previousStatus = member.status
member.status = newStatus
if (previousStatus == MemberStatus.ON_LEAVE && newStatus == MemberStatus.ACTIVE) {
    member.returnedFromLeaveAt = OffsetDateTime.now(clock)
}
```

`ON_LEAVE`에서 **직접 `ACTIVE`로** 갈 때만 기록한다. 그런데 상태 전이는 policies §5.2에 따라
자유롭고(이 메서드의 KDoc이 직접 그렇게 서술한다), 관리자가 다음 경로를 밟으면 값이 비어 있다:

- `ON_LEAVE → INACTIVE → ACTIVE` (장기 휴회자를 비활성 처리했다가 되살리는 흔한 운영)
- `ON_LEAVE → PENDING → ACTIVE` (재신청 처리 경로, D-034)

이 경우 기준일 후보 ③이 null이라 기준일이 **휴회 전 등록일**로 되돌아간다. 6개월 휴회한 회원이
복귀하면 첫 배치에서 `floor(180/14) = 12회`가 한 번에 나간다 — policies §4.3의 예외
"회원 상태가 `휴회(ON_LEAVE)`인 기간은 차감하지 않는다"를 정면으로 위반한다.

`InactivityBatchRunner`의 KDoc이 "휴회 예외는 조회 필터 + 복귀일 리셋 **두 곳으로만** 구현된다"고
선언했는데, 그 두 번째 축이 특정 전이에서만 동작한다. `MemberStatusChangeTest`도
`ON_LEAVE→ACTIVE` 직행만 검증하고 우회 경로는 테스트가 없다.

**Fix:**
"`ACTIVE`로 복귀했을 때"가 아니라 "**`ON_LEAVE`에서 벗어났을 때**" 기록한다. 휴회가 끝난 시각이
곧 유예가 다시 시작되는 시점이므로 의미도 더 정확하다.

```kotlin
val previousStatus = member.status
member.status = newStatus
if (previousStatus == MemberStatus.ON_LEAVE && newStatus != MemberStatus.ON_LEAVE) {
    member.returnedFromLeaveAt = OffsetDateTime.now(clock)
}
```

`ON_LEAVE → INACTIVE` 시점에 값이 채워지므로 이후 `INACTIVE → ACTIVE`에서도 기준일이 살아 있다.
D-111·glossary("`ON_LEAVE` → `ACTIVE` 전이 시각")를 함께 갱신하고, 다음 테스트를 추가한다:
`ON_LEAVE에서 INACTIVE를 거쳐 ACTIVE로 돌아와도 휴회 기간이 소급 차감되지 않는다`.

## Warnings

### WR-01: V9의 CHECK 완화로 "사람 조작 이력의 주체 누락"을 DB가 더 이상 막지 못한다

**File:** `src/main/resources/db/migration/V9__relax_pass_transaction_subject_and_add_batch_execution.sql:4-11`
**청크:** 05-02 (dev 머지됨)

**Issue:**
V8의 `ck_pass_transaction_subject`("정확히 하나")를 `NOT (admin_id IS NOT NULL AND member_id IS NOT NULL)`
("최대 하나")로 완화했다. 이제 `RESERVE`·`ADMIN_ADJUST`·`CANCEL_REFUND` 같은 **사람이 일으킨 이력도
주체 없이 저장된다.** 예약 서비스가 `member` 세팅을 빠뜨리는 버그가 생겨도 DB가 잡아주지 못하고,
"누가 이 차감을 했는가"라는 감사 추적이 조용히 비게 된다 — 이 프로젝트가 핵심 가치로 내건
감사 가능성의 방어선 하나가 사라진 것이다.

파일 6-7행 주석 `"둘 다 채움"은 여전히 거부한다(T-05-03, 사람 조작 이력의 주체 누락 방지)`도
사실과 다르다. "둘 다 채움" 거부는 주체 **중복**을 막을 뿐, 주체 **누락**은 막지 않는다.
`PassRepositoryTest`의 기존 테스트도 "둘 다 비면 실패"에서 "성공"으로 뒤집혀, 회귀 방어가 그냥 없어졌다.

**Fix:**
사유 코드로 조건을 좁혀 시스템 주체를 `INACTIVITY`에만 허용한다 (커밋된 V9은 수정하지 말고 V10 추가).

```sql
-- V10__tighten_pass_transaction_subject.sql
ALTER TABLE pass_transaction DROP CONSTRAINT ck_pass_transaction_subject;
ALTER TABLE pass_transaction ADD CONSTRAINT ck_pass_transaction_subject CHECK (
    NOT (admin_id IS NOT NULL AND member_id IS NOT NULL)
    AND (reason = 'INACTIVITY' OR admin_id IS NOT NULL OR member_id IS NOT NULL)
);
```

`PassRepositoryTest`에 `RESERVE 이력은 주체가 둘 다 비면 저장이 실패한다`를 다시 추가한다.
D-110에 "시스템 주체는 배치 사유에 한정한다"를 보강 기록한다.

---

### WR-02: 배치 실행 자체가 실패하면 이력이 한 줄도 남지 않고, 실패를 표현할 상태값도 없다

**File:** `src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt:55-152`,
`src/main/kotlin/com/goldwrestling/batch/InactivityBatchScheduler.kt:24-26`,
`src/main/kotlin/com/goldwrestling/batch/BatchExecutionStatus.kt:6-15`
**청크:** 05-06 + 05-08(현재 PR)

**Issue:**
`batchExecutionRepository.save(...)`가 메서드 **마지막 줄**에 있다. 벌크 조회(63·70-88행) 중 하나가
실패하거나 `resolveTriggeredBy`가 던지면 실행 이력이 저장되지 않는다. `BatchExecutionStatus`에는
`SUCCESS`/`PARTIAL_FAILURE`만 있어 "전체 실패"를 기록할 값 자체가 없다.

`InactivityBatchScheduler.runDaily()`에도 try-catch가 없어, cron이 터지면 스프링 기본 에러 핸들러의
로그 한 줄만 남는다. D-108이 "배치가 안 돌았는지는 실행 이력으로 확인한다"고 했지만, **가장 확인이
필요한 상황(실패)에서 이력이 없다** — 운영자는 "실행 안 됨"과 "실행했는데 터짐"을 구분할 수 없다.
`InactivityBatchFailureIsolationTest`도 "이력을 남기지 않고 거부한다"를 오히려 기대 동작으로 고정해 뒀다.

**Fix:**
`BatchExecutionStatus`에 `FAILED`를 추가(마이그레이션 불필요 — `VARCHAR(20)` 컬럼이다)하고,
`run()`을 try/catch/finally로 감싸 어떤 종료 경로에서도 이력 1건을 남긴다.

```kotlin
return try {
    // 기존 본문
    saveExecution(status, errorSummary, ...)
} catch (e: Exception) {
    logger.error("미사용 차감 배치 전체 실패", e)
    saveExecution(BatchExecutionStatus.FAILED, e.javaClass.simpleName, ...)
    throw e   // 수동 실행 API는 500으로 응답, cron은 스케줄러가 로깅
}
```

`InactivityBatchScheduler.runDaily()`에도 try-catch를 두어 예외가 스케줄러 밖으로 나가지 않게 한다.

---

### WR-03: 기준일 후보 ⑤가 이용권 종류·상태를 가리지 않아 후보 ④와 비대칭이다

**File:** `src/main/kotlin/com/goldwrestling/pass/PassTransactionRepository.kt:35-43`
**청크:** 05-04 (dev 머지됨)

**Issue:**
`findLastPositiveAdjustTimestamps`는 회원의 **모든 이용권**에 걸린 양(+) `ADMIN_ADJUST`를 본다 —
`LESSON_PASS`, 이미 취소된 `SESSION_PASS`, 만료된 `SESSION_PASS`가 전부 포함된다.

반면 후보 ④(`findLastDeductibleSessionPassRegistrationDates`)는 D-105 보강(2026-08-15 사용자 확정)에
따라 "차감 가능한 장"으로 명시적으로 좁혀 놨다. 같은 원리("이미 못 쓰는 옛 이용권이 기준일을 부당하게
최신으로 만드는 것을 막는다")를 후보 ⑤에는 적용하지 않았다.

결과: 관리자가 취소된 이용권이나 `LESSON_PASS`에 서비스 횟수를 얹어 주면 — `SESSION_PASS`와 무관한
행위인데도 — `SESSION_PASS`의 미사용 시계가 리셋돼 차감이 무기한 유예된다. 반대 방향(부당 차감)은
아니지만 정책 판정이 조용히 어긋난다. 이 경계를 검증하는 테스트도 없다(`InactivityBatchQueryTest`는
음수 가감·다른 사유 코드만 다룬다).

**Fix:**
후보 ④와 같은 범위로 좁힌다.

```kotlin
@Query(
    "select pt.pass.member.id as memberId, max(pt.occurredAt) as timestamp from PassTransaction pt " +
        "where pt.pass.member.id in :memberIds " +
        "and pt.pass.type = com.goldwrestling.pass.PassType.SESSION_PASS " +
        "and pt.pass.status = com.goldwrestling.pass.PassStatus.ACTIVE " +
        "and pt.reason = com.goldwrestling.pass.TransactionReason.ADMIN_ADJUST and pt.amount > 0 " +
        "group by pt.pass.member.id",
)
```

`InactivityBatchQueryTest`에 `LESSON_PASS·취소된 장의 양(+) 가감은 후보에 포함되지 않는다`를 추가한다.
범위를 좁히지 않기로 한다면 그 판단을 D-105에 명시한다 — 지금은 어느 쪽인지 문서에 없다.

---

### WR-04: `BatchExecutionResponse.from`의 계약과 실제 호출부가 모순된다

**File:** `src/main/kotlin/com/goldwrestling/batch/dto/BatchExecutionResponse.kt:33`,
`src/main/kotlin/com/goldwrestling/batch/AdminBatchController.kt:38-41`
**청크:** 05-08 (현재 PR)

**Issue:**
DTO의 KDoc은 `**트랜잭션이 열려 있는 서비스 계층 안에서만 호출한다**`라고 못박는다. 그런데 유일한
호출부인 `AdminBatchController`는 트랜잭션 밖이다 — `InactivityBatchRunner`에는 의도적으로
`@Transactional`이 없다(D-112).

지금은 우연히 동작한다: `triggeredBy`가 `adminRepository.findById()`로 실제 로딩된 구체 엔티티라
프록시가 아니기 때문이다. 하지만 다음 phase가 `batch_execution` 조회 API를 만들어 DB에서 다시 읽은
`BatchExecution`에 `from()`을 쓰면, 그때의 `triggeredBy`는 진짜 LAZY 프록시이고 컨트롤러에서
`LazyInitializationException`이 난다. 문서가 지키지 못할 계약을 선언해 두면 다음 작업자는 그 문서를
근거로 잘못된 안전성을 가정한다.

**Fix:**
KDoc을 실제 보장으로 고쳐 쓴다.

```
* 이 변환은 [BatchExecution.triggeredBy]가 **구체 엔티티일 때만** 트랜잭션 밖에서 안전하다
* (`InactivityBatchRunner`가 `findById`로 로딩해 넣은 경우). DB에서 다시 조회한 `BatchExecution`을
* 변환할 때는 `triggeredBy`가 LAZY 프록시이므로 트랜잭션 안에서 호출하거나
* `@EntityGraph`로 미리 로딩해야 한다.
```

또는 `BatchExecution`에 `triggeredByAdminId` 스칼라 필드를 두어 연관 접근 자체를 없앤다.

---

### WR-05: 수동 실행이 동기 HTTP 요청 — 타임아웃 → 재시도 → CR-01 유발

**File:** `src/main/kotlin/com/goldwrestling/batch/AdminBatchController.kt:36-41`
**청크:** 05-08 (현재 PR)

**Issue:**
`runInactivityBatch`는 배치 전체가 끝날 때까지 Tomcat 스레드를 붙잡는다. 회원 수 × 부족분만큼
`deductOnce` 트랜잭션이 순차 실행되므로 소요 시간이 데이터에 비례해 늘어난다(캐치업이 밀린 상황에서는
회원 1명이 트랜잭션 수십 개를 쓴다 — CR-02 참조).

EC2 앞단 nginx/ALB 기본 타임아웃(60s)을 넘으면 관리자는 504를 받지만 **배치는 서버에서 계속 돈다.**
"실패한 줄 알고 다시 누른다" → CR-01의 이중 차감이 그대로 발생한다. 두 결함이 서로를 증폭한다.

**Fix:**
CR-01의 락을 먼저 넣는 것이 필수 조건이다. 그 위에서:
- 단기: OpenAPI `description`에 "수 분이 걸릴 수 있으며 응답이 없어도 재요청하지 말 것"을 명시하고,
  FE 버튼을 요청 중 비활성화한다
- 중기: `202 Accepted` + `batchExecutionId` 즉시 반환 후 비동기 실행, 조회 API로 결과 폴링
  (D-114 재논의 필요 — "실행 이력 조회 API 없음"과 세트로 결정한다)

---

### WR-06: `INACTIVE`(탈퇴·거절) 회원도 차감 대상에 남는다 — 정책에 없는 판단

**File:** `src/main/kotlin/com/goldwrestling/pass/PassRepository.kt:157-167`
**청크:** 05-04 (dev 머지됨)

**Issue:**
`findMemberIdsWithDeductibleSessionPass`는 `member.status <> ON_LEAVE`만 제외한다. `INACTIVE`
(policies §5: 탈퇴/장기 미이용, §5.2: 가입 거절) 회원은 대상에 포함되므로, 탈퇴한 회원의 남은
`SESSION_PASS` 잔여가 2주마다 계속 깎여 결국 0이 된다.

policies §4.3의 예외 3종에 `INACTIVE`가 없으니 "정책대로"라고 볼 수도 있지만, 정책이 이 경우를
의식하고 쓴 문장인지 확인된 바 없다. 환불 분쟁("탈퇴 신청 시점엔 5회 남아 있었는데 왜 0인가")로
직결되는 지점이라 조용히 정할 문제가 아니다 — CLAUDE.md 규칙 8("두 가지로 해석되고 결과가 달라지면
추측하지 말고 확인받는다")에 해당한다.

**Fix:**
사용자에게 확인하고 결과를 policies §4.3에 명시한다. 제외하기로 하면 한 줄 수정이다.

```kotlin
"and p.member.status not in (com.goldwrestling.member.MemberStatus.ON_LEAVE, " +
    "com.goldwrestling.member.MemberStatus.INACTIVE, com.goldwrestling.member.MemberStatus.PENDING) "
```

(`PENDING`도 같은 질문 대상이다 — 승인 전 회원에게 이용권이 붙는 경로가 있는지 함께 확인한다.)

---

### WR-07: 배치 테스트 3개 클래스가 같은 `KAKAO_ID_BASE`를 쓰고, 정리 쿼리가 `>=`로 남의 데이터까지 지운다

**File:** `src/test/kotlin/com/goldwrestling/batch/AdminBatchControllerTest.kt:259`,
`src/test/kotlin/com/goldwrestling/batch/InactivityBatchExpiryVerificationTest.kt:314`,
`src/test/kotlin/com/goldwrestling/batch/InactivityDeductionConcurrencyTest.kt:226`,
`src/test/kotlin/com/goldwrestling/batch/InactivityBatchIdempotencyTest.kt:304`,
`src/test/kotlin/com/goldwrestling/batch/InactivityBatchFailureIsolationTest.kt:241`
**청크:** 05-05~05-08

**Issue:**
`KAKAO_ID_BASE = 9_720_000_000L`이 3개 클래스에, `9_710_000_000L`이 2개 클래스에 중복 선언돼 있다
(`9_700_000_000L`은 `ClassSessionSuspensionTest`와도 겹친다). 각 클래스의 카운터가 `base + 1`부터
시작하므로 **같은 `kakao_id` 값을 서로 만든다** — `uq_member_kakao_id` 충돌 대기 상태다.

더 나쁜 것은 정리 쿼리가 범위가 아니라 하한만 쓴다는 점이다:
`delete from member where kakao_id >= :base`. `InactivityBatchRunnerTest`(base 9_700_000_000)의
`@AfterEach`는 **9.7e9 이상의 모든 배치 테스트 회원**을 지운다. 지금은 클래스가 순차 실행되고 각자
정리해서 우연히 통과할 뿐, Gradle 병렬 테스트를 켜거나 실행 순서가 바뀌면 즉시 깨진다.

**Fix:**
클래스마다 고유 base를 배정하고 정리 쿼리를 범위로 좁힌다.

```kotlin
const val KAKAO_ID_BASE = 9_730_000_000L
const val KAKAO_ID_MAX = KAKAO_ID_BASE + 10_000L
```
```sql
delete from member where kakao_id >= :base and kakao_id < :max
```

base 상수를 테스트 지원 파일 한 곳(`BatchFixtures` 등)에 모아 중복 배정을 컴파일 단계에서 보이게 한다.

---

### WR-08: `InactivityBatchQueryTest`가 전역 쿼리 결과에 `containsExactly`를 걸고 낮은 `kakaoId`를 쓴다

**File:** `src/test/kotlin/com/goldwrestling/batch/InactivityBatchQueryTest.kt:100-102, 392-395`
**청크:** 05-04 (dev 머지됨)

**Issue:**
`findMemberIdsWithDeductibleSessionPass(today)`는 **회원 범위 제한이 없는 전역 쿼리**인데,
102행이 `assertThat(ids).containsExactly(member1.id, member2.id)`로 "DB 전체에 이 둘뿐"을 단언한다.
다른 테스트 클래스가 정리에 실패해 행을 남기거나, 시드 데이터가 추가되면 이 테스트가 원인과 무관하게
깨진다. 같은 파일의 다른 테스트들이 `contains`/`doesNotContain`을 쓰는 것과도 일관되지 않다.

또한 `persistMember`가 `kakaoId = fixtureCounter`(1, 2, 3 …)를 쓴다 — 다른 테스트가 쓰는 9.x e9
대역과 달리 낮은 값이라, 향후 시드·픽스처가 낮은 `kakao_id`를 쓰면 유니크 충돌한다.

**Fix:**
`containsExactly` → `contains` + `doesNotContain`(대조군 회원 명시)으로 바꾸고,
`kakaoId`에 이 클래스 전용 base를 붙인다.

```kotlin
assertThat(ids).contains(member1.id, member2.id)
assertThat(ids).doesNotContain(excludedMember.id)
```

---

### WR-09: `shortfall`의 기준일 당일 경계 — 직전 주기의 차감 이력이 새 주기에 계산돼 1회 유예된다

**File:** `src/main/kotlin/com/goldwrestling/batch/InactivityDueDateCalculator.kt:76-84`
**청크:** 05-03 (dev 머지됨)

**Issue:**
`inactivityEventDates.count { !it.isBefore(dueDate) }` — 기준일 **당일** 이력을 포함한다.

cron이 04:00에 도는 설계(D-114)라 다음 순서가 흔하다:
1. D일 04:00 — 이전 주기 만료로 `INACTIVITY` 1건 발생 (날짜 = D)
2. D일 10:00 — 관리자가 양(+) 가감 / 회원이 휴회 복귀 → 새 기준일 = D

D+14일: `expected = 1`, `actual = D일 이력 1건`(직전 주기의 차감인데 `!isBefore(D)`라 포함) →
shortfall 0. 새 주기의 첫 차감이 한 사이클 밀린다.

방향이 회원에게 유리해 CR은 아니지만 계산이 정책과 어긋난다. `이력 날짜가 기준일 당일이면 센다`
테스트가 이 동작을 정답으로 고정해 놓아, 이대로면 영구히 남는다. 날짜(`LocalDate`)로만 비교하는
설계 자체가 원인이다 — 원장에는 `OffsetDateTime`이 있는데 정밀도를 버렸다.

**Fix:**
타임스탬프 단위로 비교해 경계를 정확히 한다. 기준일 후보 중 `classDate`만 `LocalDate`이므로,
그 후보는 해당 날짜의 시작(`atStartOfDay`)으로 승격해 맞춘다.

```kotlin
fun shortfall(dueAt: OffsetDateTime, now: OffsetDateTime, inactivityEventTimes: List<OffsetDateTime>): Int {
    val expected = expectedDeductionCount(dueAt, now)
    val actual = inactivityEventTimes.count { it.isAfter(dueAt) }   // 기준일 시각 이후만
    return (expected - actual).coerceAtLeast(0)
}
```

간단한 대안: 현행 유지 + 이 유예를 의도로 D-106에 명문화하고 KDoc·테스트 이름에 "직전 주기 이력이
경계에 걸리면 1회 유예된다"를 적는다. 어느 쪽이든 **선택했다는 사실이 문서에 남아야** 한다.

## Info

### IN-01: 사용하지 않는 루프 변수 `attempt`

**File:** `src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt:111`
**Issue:** `for (attempt in 1..shortfallCount)`에서 `attempt`를 쓰지 않는다.
**Fix:** `repeat(shortfallCount) { ... }` — 다만 `break`가 필요하므로 `for (i in 0 until shortfallCount)`
대신 `while` + 카운터가 더 읽기 좋다. 최소 수정은 `for (unused in 1..shortfallCount)` 대신
루프 변수를 `_`로 두는 것.

---

### IN-02: `skippedCount`는 "건수"가 아니라 "스킵된 회원 수"다

**File:** `src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt:116-117`,
`src/main/kotlin/com/goldwrestling/batch/dto/BatchExecutionResponse.kt:28`,
`src/main/resources/db/migration/V9__...sql:28`
**Issue:** `deductOnce`가 false를 반환하면 `skippedCount++` 후 즉시 `break`하므로 회원당 최대 1이다.
부족분 3인데 1회만 차감된 경우 실제로 못 채운 건수는 2인데 기록은 1이다. DTO 설명("스킵된 건수")과
실제 의미가 다르다.
**Fix:** 필드명을 `skippedMemberCount`로 바꾸거나, `skippedCount += (shortfallCount - attempt + 1)`로
남은 부족분을 세도록 통일한다. 어느 쪽이든 DTO `@Schema` 설명과 V9 주석을 같이 고친다.

---

### IN-03: 리소스 생성 POST가 200을 반환하고 OpenAPI에 실패 응답이 없다

**File:** `src/main/kotlin/com/goldwrestling/batch/AdminBatchController.kt:29-41`,
`docs/api/openapi.yaml` (`/api/admin/batch/inactivity-runs`)
**Issue:** 컨트롤러 KDoc이 "배치 실행이라는 리소스 1건을 새로 만드는 POST"라고 설명하는데 201이 아니라
200이다. OpenAPI에는 200만 있고 테스트가 검증하는 401/403이 빠져 있다(파일 전체 관례이긴 하다).
**Fix:** `@ResponseStatus(HttpStatus.CREATED)` + `Location` 헤더를 검토하고,
`@ApiResponses`로 401/403(+CR-01 도입 시 409)을 명시한 뒤 `./gradlew generateApiDocs`로 재생성한다.

---

### IN-04: `in :memberIds`가 무제한 확장된다

**File:** `src/main/kotlin/com/goldwrestling/pass/PassRepository.kt:180-191`,
`src/main/kotlin/com/goldwrestling/pass/PassTransactionRepository.kt:35-61`,
`src/main/kotlin/com/goldwrestling/member/MemberRepository.kt:24-30`,
`src/main/kotlin/com/goldwrestling/reservation/ReservationRepository.kt:201-208`
**Issue:** 대상 회원 전체를 한 번에 `IN`에 넣는다. 회원 수가 PostgreSQL 바인드 파라미터 한도(65535)를
넘으면 쿼리가 실패한다. 단일 지점 규모에서는 당장 문제가 아니지만 상한이 코드에 표현돼 있지 않다.
**Fix:** `memberIds.chunked(1000)`으로 나눠 조회하고 결과를 병합한다. 지금은 KDoc에 "회원 수가
수천 명을 넘으면 청크 분할이 필요하다"를 남기는 것으로도 충분하다.

---

### IN-05: 배치 내부 무결성 오류에 HTTP 404용 도메인 예외를 재사용한다

**File:** `src/main/kotlin/com/goldwrestling/batch/InactivityDeductionService.kt:69`
**Issue:** 조건부 UPDATE 성공 직후 재조회가 실패하는 것은 "요청한 리소스가 없다"(404)가 아니라
데이터 무결성 이상이다. `PassNotFoundException`을 쓰면 러너의 `errorSummary`에
`PassNotFoundException`이 찍혀 운영자가 원인을 오해한다.
**Fix:** `IllegalStateException("차감 직후 Pass(id=$passId) 재조회 실패")`로 바꾼다. 이 경로는
`InactivityBatchRunner`의 회원 단위 try-catch가 흡수하므로 HTTP 매핑이 필요 없다.

---

_Reviewed: 2026-08-15_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
