# Phase 5: 배치 - Pattern Map

**Mapped:** 2026-08-15
**Files analyzed:** 22 (신규 15, 기존 수정 7)
**Analogs found:** 20 / 22 (스케줄러 트리거 컴포넌트 1건, 배치 오케스트레이션 루프 1건은 저장소 최초 도입이라 근사 analog만 존재)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `batch/InactivityBatchScheduler.kt` | provider(scheduler trigger) | event-driven(cron) | — (저장소 최초 `@Scheduled`) | no-analog (RESEARCH Code Example로 대체) |
| `batch/InactivityBatchRunner.kt` | service(오케스트레이션, `@Transactional` 없음) | batch | `reservation/ReservationLedgerSupport.kt` | role-match |
| `batch/InactivityDueDateCalculator.kt` | utility(순수 도메인 계산) | transform | `pass/Pass.kt` (`displayStatus`/`isExpired`) | exact |
| `batch/InactivityDeductionService.kt` | service(`@Transactional` 단위) | CRUD(조건부 UPDATE) | `reservation/ReservationLedgerSupport.kt` (`createReservation`) | exact |
| `batch/BatchExecution.kt` | model(entity) | event-driven(이력 기록) | `pass/PassTransaction.kt` | role-match |
| `batch/BatchExecutionStatus.kt` | model(enum) | — | `pass/TransactionReason.kt` | exact |
| `batch/BatchExecutionRepository.kt` | repository | CRUD | `pass/PassTransactionRepository.kt` | exact |
| `batch/AdminBatchController.kt` | controller | request-response | `schedule/AdminScheduleController.kt` | exact |
| `batch/dto/BatchExecutionResponse.kt` | dto | — | `pass/dto/PassResponse.kt` | exact |
| `batch/BatchExceptions.kt` | utility(도메인 예외) | — | `pass/PassExceptions.kt` | exact |
| `config/SchedulingConfig.kt` | config | event-driven | `config/ClockConfig.kt` | role-match |
| `member/Member.kt` (수정 — `returnedFromLeaveAt` 추가) | model | CRUD | `reservation/Reservation.kt` (`canceledAt` 단일 목적 컬럼) | role-match |
| `member/AdminMemberService.kt` (수정 — `changeStatus`) | service | CRUD | 자기 자신(기존 `changeStatus` 메서드) | exact |
| `reservation/ReservationRepository.kt` (수정 — 벌크 기준일 조회 추가) | repository | batch(IN절 벌크) | 자기 자신(`findAllByClassSessionIdIn...WithMember`) | exact |
| `pass/PassRepository.kt` (수정 — SESSION_PASS 등록일 벌크 조회 추가) | repository | batch(IN절 벌크) | `reservation/ReservationRepository.kt` (IN절 벌크 패턴) | role-match |
| `pass/PassTransactionRepository.kt` (수정 — ADMIN_ADJUST/INACTIVITY 벌크 조회 추가) | repository | batch(IN절 벌크) | `reservation/ReservationRepository.kt` (IN절 벌크 패턴) | role-match |
| `common/error/ErrorCode.kt` (수정 — 배치 관련 코드 추가) | config(enum) | — | 자기 자신(기존 도메인 코드 추가 관례) | exact |
| `db/migration/V9__*.sql` | migration | batch | `db/migration/V8__extend_pass_transaction_subject.sql` (CHECK 완화) + `V6__create_schedule_reservation_notification.sql` (신규 테이블) | exact |
| `test/batch/InactivityDueDateCalculatorTest.kt` | test(unit) | transform | `test/reservation/ReservationPassPolicyTest.kt` | exact |
| `test/batch/InactivityDeductionServiceTest.kt` | test(integration) | CRUD | `test/pass/PassLedgerInvariantTest.kt` | exact |
| `test/batch/InactivityBatchRunnerTest.kt` / `InactivityBatchIdempotencyTest.kt` / `InactivityBatchExpiryVerificationTest.kt` | test(integration) | batch | `test/pass/PassLedgerInvariantTest.kt` | exact |
| `test/batch/BatchFixtures.kt` | test(fixture helper) | — | `test/pass/PassFixtures.kt` | exact |

## Pattern Assignments

### `batch/InactivityBatchScheduler.kt` (provider/scheduler trigger, event-driven)

**Analog:** 없음(저장소 최초 `@Scheduled` 도입) — RESEARCH.md Pattern 1 Code Example을 원본으로 쓴다(Context7 실측 확인 완료).

**핵심 원칙**: 스케줄러 메서드는 트리거만 한다. 조건문·쿼리·트랜잭션을 넣지 않는다.

```kotlin
// Source: 05-RESEARCH.md Pattern 1 (Context7 spring-framework reference로 zone 속성 확인)
@Configuration
@EnableScheduling
class SchedulingConfig

@Component
class InactivityBatchScheduler(
    private val runner: InactivityBatchRunner,
) {
    @Scheduled(cron = "0 0 4 * * *", zone = SEOUL_ZONE_ID)
    fun runDaily() {
        runner.run(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)
    }
}
```

`SEOUL_ZONE_ID` 상수는 `GoldWrestlingApplication.kt:19`에 이미 선언돼 있다 — 새로 만들지 않고 import한다.

---

### `batch/InactivityBatchRunner.kt` (service, batch orchestration, `@Transactional` 없음)

**Analog:** `src/main/kotlin/com/goldwrestling/reservation/ReservationLedgerSupport.kt`

이 파일 자체는 예약 도메인이지만 **구조 원칙이 그대로 적용된다**: 여러 스텝을 조합하는 컴포넌트는
`@Transactional`을 붙이지 않고, 상태 변경이 필요한 부분만 별도 빈(`InactivityDeductionService`)의
`@Transactional` 메서드로 위임한다(Pitfall 3 — self-invocation + 전체 롤백 회피).

**구조 패턴** (`ReservationLedgerSupport.kt:40-49`):
```kotlin
@Component
class ReservationLedgerSupport(
    private val memberRepository: MemberRepository,
    private val classSessionRepository: ClassSessionRepository,
    private val classSessionService: ClassSessionService,
    private val reservationRepository: ReservationRepository,
    private val passRepository: PassRepository,
    private val passTransactionRepository: PassTransactionRepository,
    private val clock: Clock,
) {
    // @Transactional 없음 — 호출부(서비스)가 이미 연 트랜잭션 안에서 실행되는 헬퍼
```

`InactivityBatchRunner`는 반대로 **호출부가 트랜잭션을 열지 않는** 최상위 루프다(RESEARCH 다이어그램
참조) — 회원별로 `InactivityDeductionService.deductOnce(memberId)`(별도 빈)를 호출하고
`try-catch`로 회원 단위 예외를 흡수해 `BatchExecution` 집계에 반영한다. RESEARCH.md "부족분 계산 후
회당 재선택 차감 루프" 코드 예제를 그대로 골격으로 쓴다:

```kotlin
// Source: 05-RESEARCH.md "부족분 계산 후 회당 재선택 차감 루프"
repeat(shortfall) {
    val deducted = inactivityDeductionService.deductOnce(memberId)
    if (!deducted) return@repeat // 대상 소진 — 남은 부족분은 다음 실행이 이어받음(캐치업)
}
```

---

### `batch/InactivityDueDateCalculator.kt` (utility, 순수 도메인 계산)

**Analog:** `src/main/kotlin/com/goldwrestling/pass/Pass.kt`

`Pass.kt`의 도메인 판정 메서드들(`displayStatus`, `resolvePeriodChange`, `resolveCancellationOffset`)이
따르는 원칙을 그대로 따른다 — **판정만 하고 반영은 하지 않는다**(D-072), Spring/DB 접근 없이 파라미터만
받는다.

**만료 판정 패턴** (`Pass.kt:123-127`, 재사용 대상 — D-066 비교축):
```kotlin
/**
 * 유효기간 만료 판정 (D-066): `endDate`는 종료일 포함이라 `!today.isAfter(endDate)`가 유효 —
 * 그 반대인 `today.isAfter(endDate)`가 만료다. Phase 5 만료 배치가 이 식을 재사용한다.
 */
private fun isExpired(today: LocalDate): Boolean = today.isAfter(endDate)
```

**계산 자체는 RESEARCH.md Pattern 3 코드를 그대로 쓴다** (`companion object`, Clock 불필요 — `today`를
파라미터로 받는다):
```kotlin
// Source: 05-RESEARCH.md Pattern 3 (policies §4.3 + D-106을 코드화)
object InactivityDueDateCalculator {
    private const val GRACE_PERIOD_DAYS = 14L

    fun expectedDeductionCount(dueDate: LocalDate, today: LocalDate): Int {
        val elapsedDays = ChronoUnit.DAYS.between(dueDate, today)
        if (elapsedDays < GRACE_PERIOD_DAYS) return 0
        return (elapsedDays / GRACE_PERIOD_DAYS).toInt()
    }

    fun shortfall(
        dueDate: LocalDate,
        today: LocalDate,
        inactivityEventDatesOnOrAfterDueDate: Int,
    ): Int = (expectedDeductionCount(dueDate, today) - inactivityEventDatesOnOrAfterDueDate).coerceAtLeast(0)
}
```

`object`(companion 아님, 최상위 object) 스타일은 `ReservationPassPolicy`/`ReservationRefundPolicy`
(순수 정책 object) 관례와도 일치한다 — `pass`/`reservation` 패키지 모두 이 스타일을 쓴다.

---

### `batch/InactivityDeductionService.kt` (service, `@Transactional` 단위, CRUD 조건부 UPDATE)

**Analog:** `src/main/kotlin/com/goldwrestling/reservation/ReservationLedgerSupport.kt` (`createReservation` 메서드, 라인 62-154)

D-021 조건부 UPDATE → 재조회 → `PassTransaction` 저장 흐름을 그대로 재사용한다. 특히 "조건부 UPDATE
직후 준영속화된 엔티티를 그대로 쓰지 않고 재조회한다"는 관례(`ReservationLedgerSupport.kt:58-60`,
`109-118`)가 핵심.

```kotlin
// Source: ReservationLedgerSupport.createReservation (lines 99-151), 조건부 차감 → 재조회 → 이력 저장
val candidates = passRepository.findDeductionCandidates(memberId, passType, classDate, DEDUCTION_AMOUNT)
val candidate = ReservationPassPolicy.selectCandidate(candidates)
val passId = requireNotNull(candidate.id) { "차감 후보 조회는 항상 저장된 Pass만 반환합니다." }

if (passRepository.adjustRemainingCount(passId, DEDUCTION_AMOUNT.negate()) == 0) {
    throw InsufficientPassCountException()
}

val refreshedPass = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }
val nowOffset = OffsetDateTime.now(clock)

passTransactionRepository.save(
    PassTransaction(
        pass = refreshedPass,
        amount = DEDUCTION_AMOUNT.negate(),
        reason = TransactionReason.RESERVE,
        note = null,
        admin = admin,
        member = if (admin == null) refreshedMember else null,
        occurredAt = nowOffset,
    ),
)
```

배치용 변형은 RESEARCH.md "부족분 계산 후 회당 재선택 차감 루프" 코드 예제를 그대로 쓴다 — 회당
`findActiveUsableSessionPasses`(신규, `PassRepository.findDeductionCandidates`와 동형이나 `classDate`
대신 `today`+`type=SESSION_PASS` 고정) 재조회, `min(1, remaining)` 부분 차감, 0행이면 스킵.
`PassTransaction`의 주체 표현은 **`admin = null, member = null`**(시스템 주체) — V9 완화된 CHECK가
허용한다(Pitfall 5).

**재사용할 기존 컴포넌트** (변경 불필요):
- `PassRepository.adjustRemainingCount` (`PassRepository.kt:44-53`) — 그대로 호출
- `PassRepository.findDeductionCandidates` 정렬 규칙(`endDate asc, id asc`, `PassRepository.kt:131-142`) — 신규 벌크/단건 조회 모두 이 정렬을 재사용

---

### `batch/BatchExecution.kt` (model/entity)

**Analog:** `src/main/kotlin/com/goldwrestling/pass/PassTransaction.kt`

append-only 이력 엔티티의 관례 — 전 필드 `val`, setter 없음, `@Id`는 `GenerationType.IDENTITY`.

```kotlin
// Source: PassTransaction.kt:34-59 — append-only 엔티티 골격 그대로 재사용
@Entity
@Table(name = "pass_transaction")
class PassTransaction(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pass_id", nullable = false)
    val pass: Pass,
    ...
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
```

`BatchExecution`은 `triggerType`(enum, STRING)·`triggeredByAdminId`(nullable FK, `Admin`)·
`startedAt`/`finishedAt`(`OffsetDateTime`)·`processedMemberCount`/`deductedCount`(`Int`)·
`status`(enum)·`errorSummary`(nullable) 필드로 구성한다(RESEARCH "배치 실행 이력 테이블" 코드 예제
참조). **저장은 실행 종료 시점(또는 catch 블록) 1건만** — 회원별 상세를 담지 않는다(D-108).

---

### `batch/BatchExecutionStatus.kt` (model/enum)

**Analog:** `src/main/kotlin/com/goldwrestling/pass/TransactionReason.kt`

닫힌 enum + 각 상수에 KDoc 한 줄 관례:

```kotlin
// Source: TransactionReason.kt:11-35 — 상수마다 사유 KDoc을 붙이는 관례
enum class TransactionReason {
    /** 예약 성공 시 차감 (Phase 4) */
    RESERVE,
    ...
}
```

`BatchExecutionStatus`는 `SUCCESS`/`PARTIAL_FAILURE` 2종(RESEARCH 권고, Open Question 2 참조 —
경쟁 패배로 인한 스킵은 정상 경로이므로 `SUCCESS`로 집계하고 스킵 건수만 별도 필드로 남기는 편을
계획 단계에서 확정).

---

### `batch/BatchExecutionRepository.kt` (repository)

**Analog:** `src/main/kotlin/com/goldwrestling/pass/PassTransactionRepository.kt`

```kotlin
// Source: PassTransactionRepository.kt:13-25
interface PassTransactionRepository :
    JpaRepository<PassTransaction, Long>,
    JpaSpecificationExecutor<PassTransaction> {
    @Query("select coalesce(sum(t.amount), 0) from PassTransaction t where t.pass.id = :passId")
    fun sumAmountByPassId(@Param("passId") passId: Long): BigDecimal
}
```

`BatchExecutionRepository`는 단순 `JpaRepository<BatchExecution, Long>`이면 충분 — 관리자 조회 API가
필터·페이지네이션을 요구하면 그때 `JpaSpecificationExecutor`를 추가한다(이번 phase 범위에는 없음).

---

### `batch/AdminBatchController.kt` (controller, request-response)

**Analog:** `src/main/kotlin/com/goldwrestling/schedule/AdminScheduleController.kt`

`/api/admin/**` 전체가 `SecurityConfig`에서 `hasRole("ADMIN")`으로 이미 보호되므로 **컨트롤러에
별도 권한 애노테이션을 붙이지 않는다**(D-040) — 트랜잭션 애노테이션·try-catch도 붙이지 않는다
(트랜잭션은 서비스, 에러 응답은 `GlobalExceptionHandler`).

```kotlin
// Source: AdminScheduleController.kt:38-68 — 트리거형 POST 엔드포인트 패턴
@RestController
@RequestMapping("/api/admin")
@Tag(name = "admin-schedule", description = "...")
class AdminScheduleController(
    private val adminScheduleService: AdminScheduleService,
) {
    @PostMapping("/class-sessions/suspension")
    @Operation(summary = "...", description = "...")
    fun suspend(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        @Valid @RequestBody request: SuspendClassSessionRequest,
    ): ClassSessionResponse = adminScheduleService.suspend(principal.requireAdminId(), request)
}
```

`AdminBatchController`는 `POST /api/admin/batch/inactivity-runs` 1개 엔드포인트 — 요청 본문 없이
`principal.requireAdminId()`만 받아 `InactivityBatchRunner.run(trigger = MANUAL, triggeredByAdminId = adminId)`를
호출하고 `BatchExecutionResponse`를 반환한다.

---

### `batch/dto/BatchExecutionResponse.kt` (dto)

**Analog:** `src/main/kotlin/com/goldwrestling/pass/dto/PassResponse.kt`

`companion object { fun from(entity, ...): Response }` 변환 관례 — 트랜잭션 안에서만 호출한다는
주석을 남긴다(LAZY 연관 접근 시점 문제, `PassResponse.kt:34-41`).

```kotlin
// Source: PassResponse.kt:20-58
@Schema(description = "이용권 응답")
data class PassResponse(
    @field:Schema(description = "...") val passId: Long,
    ...
) {
    companion object {
        fun from(pass: Pass, today: LocalDate): PassResponse = PassResponse(...)
    }
}
```

---

### `batch/BatchExceptions.kt` (도메인 예외)

**Analog:** `src/main/kotlin/com/goldwrestling/pass/PassExceptions.kt`

```kotlin
// Source: PassExceptions.kt:41-48 — DomainException 서브클래스 패턴
class InsufficientPassCountException :
    DomainException(
        ErrorCode.INSUFFICIENT_PASS_COUNT,
        "잔여 횟수가 부족합니다.",
    )
```

이 phase의 관리자 수동 실행 API는 도메인 검증이 거의 없다(D-106 상태 기반 설계 자체가 흡수 —
RESEARCH Security Domain V5 참조)는 점에 유의 — 새 예외는 필요 최소한만 추가한다.

---

### `config/SchedulingConfig.kt` (config)

**Analog:** `src/main/kotlin/com/goldwrestling/config/ClockConfig.kt`

```kotlin
// Source: ClockConfig.kt:13-17 — 이 저장소의 단일 책임 @Configuration 관례
@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of(SEOUL_ZONE_ID))
}
```

`SchedulingConfig`는 `@Bean` 없이 `@Configuration @EnableScheduling` 애노테이션만 있는 빈 클래스다
(RESEARCH Pattern 1 코드 예제 그대로).

---

### `member/Member.kt` (수정 — `returnedFromLeaveAt` 컬럼 추가)

**Analog(컬럼 설계 원칙):** `src/main/kotlin/com/goldwrestling/reservation/Reservation.kt`의 `canceledAt`
— "그 사건이 일어난 시각만" 기록하는 단일 목적 컬럼 패턴(범용 `status_changed_at` 대신).

```kotlin
// Source: 05-RESEARCH.md "회원 상태 전환 시각 컬럼" 코드 예제
@Column(name = "returned_from_leave_at")
var returnedFromLeaveAt: OffsetDateTime? = null
```

기존 `Member.kt`(라인 1-106)의 nullable 필드 선언 관례(`var rejectionReason: String? = null` 등,
라인 46)를 그대로 따른다.

---

### `member/AdminMemberService.kt` (수정 — `changeStatus`)

**Analog:** 자기 자신의 기존 `changeStatus` 메서드 (`AdminMemberService.kt:121-141`)

```kotlin
// Source: AdminMemberService.kt:121-141 — 수정 대상 메서드 그대로
@Transactional
fun changeStatus(
    memberId: Long,
    newStatus: MemberStatus,
): MemberDetailResponse {
    val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
    if (newStatus == MemberStatus.ACTIVE && !member.isOnboardingCompleted()) {
        throw MemberStateConflictException("...")
    }
    member.status = newStatus
    ...
    return MemberDetailResponse.from(member)
}
```

`member.status == MemberStatus.ON_LEAVE && newStatus == MemberStatus.ACTIVE` 분기에서
`member.returnedFromLeaveAt = OffsetDateTime.now(clock)`를 `member.status = newStatus` 대입 **직후**에
추가한다(RESEARCH Code Example) — `AdminMemberService`는 현재 `clock` 빈을 주입받지 않으므로 생성자에
추가해야 한다(`ReservationLedgerSupport`처럼 `private val clock: Clock` 파라미터 추가).

---

### 벌크 조회 3건 — `ReservationRepository`/`PassRepository`/`PassTransactionRepository` 수정

**Analog:** `src/main/kotlin/com/goldwrestling/reservation/ReservationRepository.kt`의 IN절 벌크 조회 관례

```kotlin
// Source: ReservationRepository.kt:109-116 — 이 저장소의 IN절 벌크 조회 + join fetch 관례
@Query(
    "select r from Reservation r join fetch r.member " +
        "where r.classSession.id in :classSessionIds and r.status = :status",
)
fun findAllByClassSessionIdInAndStatusWithMember(
    @Param("classSessionIds") classSessionIds: Collection<Long>,
    @Param("status") status: ReservationStatus,
): List<Reservation>
```

배치의 5종 기준일 후보 벌크 조회는 **`GROUP BY` + 스칼라 프로젝션**이 필요한데, 이 저장소에 아직
선례가 없다(`Array<Any>` 캐스팅 또는 인터페이스 프로젝션) — RESEARCH.md Pattern 2가 초안 쿼리 3개를
제시했지만, **인터페이스 프로젝션 문법은 계획 단계에서 `verify-boot4-api` 절차로 재확인해야 한다**
(RESEARCH가 명시적으로 미검증 표시).

```kotlin
// Source: 05-RESEARCH.md Pattern 2 — 초안(계획 단계에서 프로젝션 문법 재검증 필요)
@Query(
    "select r.member.id, max(r.classDate) from Reservation r " +
        "where r.member.id in :memberIds and r.status = com.goldwrestling.reservation.ReservationStatus.ACTIVE " +
        "group by r.member.id",
)
fun findLastActiveReservationDates(@Param("memberIds") memberIds: Collection<Long>): List<Array<Any>>
```

3개 벌크 쿼리는 각자의 소유 패키지에 둔다(`reservation`/`pass`/`pass`) — `batch` 패키지가 아니라
소유 리포지토리에 추가하는 것이 기존 관례(`findDeductionCandidates`도 `pass` 패키지 소유)와 일치한다.

---

### `common/error/ErrorCode.kt` (수정 — 배치 관련 코드 추가 시)

**Analog:** 자기 자신의 기존 추가 관례 (`ErrorCode.kt:71-138`)

```kotlin
// Source: ErrorCode.kt:134-138 — 최근 phase가 새 도메인 코드를 추가한 패턴
/** 요청한 branchId에 관리자가 소속되지 않음 (T-04-53, 관리자 스케줄 보드 최초 도입) */
ADMIN_BRANCH_NOT_ASSIGNED(HttpStatus.FORBIDDEN),

/** 관리자 예약 검색 조건의 from이 to보다 뒤임 (RESV-07) */
INVALID_RESERVATION_SEARCH_RANGE(HttpStatus.BAD_REQUEST),
```

RESEARCH Security Domain 분석대로 이 phase는 관리자 API의 검증 범위가 좁아(D-106이 흡수) 새 코드가
필요 없을 수도 있다 — 필요해지면 `docs/error-codes.md`도 같은 PR에서 갱신(규칙 4).

---

### `db/migration/V9__*.sql` (migration)

**Analog 1(CHECK 완화):** `src/main/resources/db/migration/V8__extend_pass_transaction_subject.sql`

```sql
-- Source: V8__extend_pass_transaction_subject.sql:14-22 — CHECK 재정의 관례
ALTER TABLE pass_transaction ALTER COLUMN admin_id DROP NOT NULL;
ALTER TABLE pass_transaction ADD COLUMN member_id BIGINT REFERENCES member (id);

ALTER TABLE pass_transaction ADD CONSTRAINT ck_pass_transaction_subject CHECK (
    (admin_id IS NOT NULL AND member_id IS NULL) OR
    (admin_id IS NULL AND member_id IS NOT NULL)
);
```

V9는 이 CHECK를 DROP 후 "최대 하나"로 재추가한다(Pitfall 5, RESEARCH "배치 실행 이력 테이블"
코드 예제):
```sql
ALTER TABLE pass_transaction DROP CONSTRAINT ck_pass_transaction_subject;
ALTER TABLE pass_transaction ADD CONSTRAINT ck_pass_transaction_subject CHECK (
    NOT (admin_id IS NOT NULL AND member_id IS NOT NULL)
);
```

**Analog 2(신규 테이블 생성):** `src/main/resources/db/migration/V6__create_schedule_reservation_notification.sql`

```sql
-- Source: V6__create_schedule_reservation_notification.sql:17-34 — 서로게이트 PK, TIMESTAMPTZ,
-- 헤더 주석에 결정 번호 인용, 타입별 CHECK 제약 관례
CREATE TABLE class_schedule (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    ...
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_class_schedule_branch FOREIGN KEY (branch_id) REFERENCES branch (id),
    ...
);
```

`batch_execution` 테이블·`member.returned_from_leave_at` 컬럼 추가 SQL은 RESEARCH.md "배치 실행 이력
테이블 — 마이그레이션 스케치" 절 전문을 초안으로 쓴다. **커밋된 V4·V8은 수정 금지 — V9로만 추가**
(conventions §9).

---

### 테스트 4종 — `test/batch/InactivityDeductionServiceTest.kt` / `InactivityBatchRunnerTest.kt` / `InactivityBatchIdempotencyTest.kt` / `InactivityBatchExpiryVerificationTest.kt` (Testcontainers 통합)

**Analog:** `src/test/kotlin/com/goldwrestling/pass/PassLedgerInvariantTest.kt`

```kotlin
// Source: PassLedgerInvariantTest.kt:41-72 — SpringBootTest + Testcontainers + 고정 Clock 배선
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class PassLedgerInvariantTest {
    @Autowired private lateinit var adminPassService: AdminPassService
    @Autowired private lateinit var passRepository: PassRepository
    @Autowired private lateinit var passTransactionRepository: PassTransactionRepository
    @Autowired private lateinit var clock: Clock

    private var fixtureCounter = 30000L

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    private fun assertLedgerInvariant(passId: Long, expectedRemaining: BigDecimal) {
        val pass = passRepository.findById(passId).get()
        val sumOfHistory = passTransactionRepository.sumAmountByPassId(passId)
        assertThat(pass.remainingCount).isEqualByComparingTo(expectedRemaining)
        assertThat(pass.remainingCount).isEqualByComparingTo(sumOfHistory)
    }
}
```

**멱등성 테스트(`InactivityBatchIdempotencyTest`)의 핵심 관례**: `(clock as MutableTestClock).setTo(...)`로
시각을 이동시켜가며 배치를 여러 번 호출하고, 매번 `PassLedgerInvariantTest`와 동일한 "잔여 = 이력 합계"
불변식을 검증한다 — 그 위에 "같은 날 2회 실행 → 차감 건수 동일", "N일 경과 후 실행 → 밀린 회차만큼
차감"을 추가로 검증한다.

`isEqualByComparingTo`(NOT `isEqualTo`) — D-016, `BigDecimal` 비교는 항상 `compareTo` 기반.

---

### `test/batch/InactivityDueDateCalculatorTest.kt` (unit, Spring 컨텍스트 없음)

**Analog:** `src/test/kotlin/com/goldwrestling/reservation/ReservationPassPolicyTest.kt`

```kotlin
// Source: ReservationPassPolicyTest.kt:1-24 — 순수 Kotlin 단위테스트, Spring 컨텍스트 없음
class ReservationPassPolicyTest {
    @Test
    fun `후보가 비어 있으면 잔여 부족 예외를 던진다`() {
        assertThatThrownBy { ReservationPassPolicy.selectCandidate(emptyList()) }
            .isInstanceOf(InsufficientPassCountException::class.java)
    }
}
```

`InactivityDueDateCalculatorTest`는 `LocalDate` 두 개(`dueDate`, `today`)와 `Int`(이력 건수)만 주고받는
순수 함수 테스트라 픽스처(회원·이용권 엔티티)조차 필요 없다 — `ReservationPassPolicyTest`보다도
가벼운 구조가 된다.

---

### `test/batch/BatchFixtures.kt` (test fixture helper)

**Analog:** `src/test/kotlin/com/goldwrestling/pass/PassFixtures.kt`

```kotlin
// Source: PassFixtures.kt:16-38 — 고정 시각 + 최소 픽스처 object 관례
object PassFixtures {
    val FIXED_TIME: OffsetDateTime = OffsetDateTime.parse("2026-08-01T00:00:00+09:00")
    val FIXED_TODAY: LocalDate = LocalDate.of(2026, 8, 1)

    fun branch(): Branch = Branch(name = "송파점")
    fun member(): Member = Member(branch = branch(), ..., createdAt = FIXED_TIME)
    fun admin(): Admin = Admin(name = "관리자", loginId = "admin1", passwordHash = "hash", createdAt = FIXED_TIME)
}
```

`BatchFixtures`는 여기에 "5종 기준일 후보를 만드는" 헬퍼(예: `memberWithLastActiveReservation`,
`memberWithReturnedFromLeave`)를 추가한다 — Testcontainers 통합테스트(`InactivityBatchRunnerTest` 등)가
공유한다.

## Shared Patterns

### 조건부 UPDATE(D-021) + 재조회 + 이력 저장
**Source:** `pass/PassRepository.kt` (`adjustRemainingCount`, 라인 44-53) + `reservation/ReservationLedgerSupport.kt` (라인 99-151)
**Apply to:** `InactivityDeductionService.deductOnce`
```kotlin
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query(
    "update Pass p set p.remainingCount = p.remainingCount + :amount " +
        "where p.id = :id and p.status = com.goldwrestling.pass.PassStatus.ACTIVE " +
        "and p.remainingCount + :amount >= 0",
)
fun adjustRemainingCount(@Param("id") id: Long, @Param("amount") amount: BigDecimal): Int
```
반환 0 = 경쟁 패배 또는 사전 판정 이후 상태 변화 — 배치는 이 경우 해당 회원을 스킵하고 다음 실행이
자연 보정한다(Claude's Discretion 항목, D-106과 정합).

### 트랜잭션 경계 = 회원 1명 (D-020 + Pitfall 3)
**Source:** `reservation/ReservationLedgerSupport.kt` 헤더 KDoc (라인 30-33) — "`@Transactional`은
붙이지 않는다 — 항상 호출부가 이미 열어 둔 트랜잭션 안에서 실행되는 헬퍼"
**Apply to:** `InactivityBatchRunner`(트랜잭션 없음) vs `InactivityDeductionService`(회원 1명당
`@Transactional` 1개, 별도 스프링 빈)

### `Clock` 빈 주입, `OffsetDateTime.now()` 직접 호출 금지
**Source:** `config/ClockConfig.kt` (전체)
**Apply to:** `InactivityDueDateCalculator`(파라미터로 `today` 수신, Clock 자체는 불필요) +
`InactivityBatchRunner`/`InactivityDeductionService`(`Clock` 주입해 `LocalDate.now(clock)`/
`OffsetDateTime.now(clock)`)

### RFC 9457 ProblemDetail + `DomainException` 서브클래스
**Source:** `common/error/DomainException.kt`, `pass/PassExceptions.kt`
**Apply to:** `batch/BatchExceptions.kt`(필요 최소한만 추가 — D-106 설계가 대부분의 실패 경로를
"부족분 0"으로 흡수한다)

### `/api/admin/**` 인가는 URL 규칙만 — 컨트롤러에 권한 애노테이션 없음
**Source:** `schedule/AdminScheduleController.kt` 헤더 KDoc (라인 21-24, D-040)
**Apply to:** `AdminBatchController`

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `batch/InactivityBatchScheduler.kt` | provider(scheduler) | event-driven(cron) | 이 저장소 최초 `@Scheduled` 도입 — RESEARCH.md Pattern 1(Context7 실측)을 원본으로 쓴다 |
| `batch/InactivityBatchRunner.kt`의 "회원 벌크 조회 → 인메모리 조합" 단계 | service | batch | 벌크 IN절 쿼리는 `ReservationRepository`에 선례가 있으나(role-match로 처리), 5종 후보를 인메모리에서 `max()` 조합하는 로직 자체는 이 저장소에 선례가 없다 — RESEARCH Pattern 2·Pattern 3이 유일한 참조 |

## Metadata

**Analog search scope:** `src/main/kotlin/com/goldwrestling/{pass,reservation,member,schedule,admin,config,common/error}`, `src/main/resources/db/migration`, `src/test/kotlin/com/goldwrestling/{pass,reservation,db}`
**Files scanned:** 24개 직접 Read(패키지 디렉토리 목록 포함 시 40+개 파일명 확인)
**Pattern extraction date:** 2026-08-15
