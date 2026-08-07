# Phase 4: 시간표·예약 - Pattern Map

**Mapped:** 2026-08-07
**Files analyzed:** 36 (신규 30 + 수정 6)
**Analogs found:** 33 / 36

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `schedule/ClassSchedule.kt` | model (entity) | CRUD (읽기 전용, 시드 고정) | `pass/Pass.kt` (companion factory·타입별 CHECK 관례) / `branch/Branch.kt` (단순 참조 엔티티) | role-match |
| `schedule/ClassScheduleRepository.kt` | model (repository) | CRUD | `pass/PassPeriodChangeRepository.kt` / `branch/BranchRepository.kt` | role-match |
| `schedule/ClassSession.kt` | model (entity) | event-driven (get-or-create) + CRUD | `pass/Pass.kt` (판정 메서드·취소 메타데이터 완전성 CHECK) | exact(구조) |
| `schedule/ClassSessionRepository.kt` | model (repository) | event-driven + CRUD | `pass/PassRepository.kt`(`adjustRemainingCount`) + `auth/RefreshTokenRepository.kt`(`revokeIfUsable`) — 조건부 UPDATE 관례. **네이티브 `ON CONFLICT DO NOTHING`은 이 저장소에 실사용 전례 없음** | role-match(부분 신규) |
| `schedule/ClassSessionStatus.kt` | model (enum) | — | `pass/PassStatus.kt` | exact |
| `schedule/ClassType.kt` | model (enum) | — | `pass/PassType.kt` | exact |
| `schedule/ScheduleExceptions.kt` | model (exception) | — | `pass/PassExceptions.kt` | exact |
| `schedule/ScheduleService.kt` | service | CRUD(조회) | `pass/MemberPassService.kt` | exact |
| `schedule/MemberScheduleController.kt` | controller | request-response | `pass/MemberPassController.kt` | exact |
| `schedule/AdminScheduleService.kt` | service | CRUD + event-driven(휴강 캐스케이드) | `pass/AdminPassService.kt`(`cancel` 메서드) | exact |
| `schedule/AdminScheduleController.kt` | controller | request-response | `pass/AdminPassController.kt` | exact |
| `schedule/dto/*` (WeeklyScheduleResponse 등) | model (DTO) | transform | `pass/dto/PassResponse.kt` | exact |
| `common/time/WeekRange.kt` | utility | transform | `member/PhoneNumberNormalizer.kt`(순수 무상태 유틸 object) | role-match |
| `reservation/Reservation.kt` | model (entity) | CRUD + event-driven | `pass/Pass.kt`(취소 판정 메서드) + `auth/RefreshToken.kt`(주체 nullable 쌍 CHECK) | exact |
| `reservation/ReservationStatus.kt` | model (enum) | — | `pass/PassStatus.kt` | exact |
| `reservation/ReservationRepository.kt` | model (repository) | event-driven(조건부 UPDATE) | `pass/PassRepository.kt`(`cancelIfNotCanceled`) | exact |
| `reservation/ReservationSpecifications.kt` | model (specification) | CRUD(검색) | `pass/PassTransactionSpecifications.kt` + `member/MemberSpecifications.kt` | exact |
| `reservation/ReservationExceptions.kt` | model (exception) | — | `pass/PassExceptions.kt` | exact |
| `reservation/MemberReservationService.kt` | service | CRUD + event-driven(차감/복구 트랜잭션) | `pass/AdminPassService.kt`(`register`·`cancel` — 조회→판정→조건부UPDATE→이력저장 순서) | exact |
| `reservation/MemberReservationController.kt` | controller | request-response | `pass/MemberPassController.kt` | exact |
| `reservation/AdminReservationService.kt` | service | CRUD + event-driven | `pass/AdminPassService.kt` + `member/AdminMemberService.kt`(`search` 페이지네이션) | exact |
| `reservation/AdminReservationController.kt` | controller | request-response | `member/AdminMemberController.kt`(`@ParameterObject` 필터+페이지네이션) | exact |
| `reservation/dto/*` (ReserveRequest, ReservationResponse, ChangeReservationRequest, ReservationSearchCondition 등) | model (DTO) | transform | `pass/dto/RegisterPassRequest.kt` + `pass/dto/PassResponse.kt` + `pass/dto/PassTransactionSearchCondition.kt` | exact |
| `notification/Notification.kt` | model (entity) | event-driven(append-only) | `pass/PassTransaction.kt` | exact |
| `notification/NotificationType.kt` | model (enum) | — | `pass/TransactionReason.kt` | exact |
| `notification/NotificationRepository.kt` | model (repository) | CRUD(저장 전용) | `pass/PassTransactionRepository.kt` | exact |
| `notification/NotificationService.kt` | service | event-driven(create만) | 없음(단독 서비스 전례 없음) — `AdminPassService`가 트랜잭션 안에서 `PassTransaction`을 인라인 저장하는 방식을 참고 | no-analog |
| `pass/AdminPassService.kt` (수정: `cancel`에 활성 예약 선행 검사 추가) | service (수정) | CRUD | 자기 자신(기존 `cancel` 메서드) | exact |
| `pass/PassRepository.kt` (수정: `findDeductionCandidates` 추가) | model (repository, 수정) | CRUD(조회) | 같은 파일의 기존 `@Query` 메서드 관례 | exact |
| `pass/PassTransaction.kt` (수정: `member` nullable 주체 추가) | model (entity, 수정) | — | `auth/RefreshToken.kt`(주체 nullable 쌍 + CHECK 관례) | exact |
| `db/migration/V6__create_schedule_reservation_notification.sql` | migration | batch | `db/migration/V4__create_pass_tables.sql` | exact |
| `db/migration/V7__extend_pass_transaction_subject.sql` | migration | batch | `db/migration/V5__add_member_kakao_profile.sql`(단순 `ALTER TABLE`) | exact |
| `common/error/ErrorCode.kt` (수정: 신규 코드 추가) | config (수정) | — | 자기 자신(기존 enum 항목 추가 관례) | exact |
| `reservation/ReservationCapacityConcurrencyTest.kt` | test(동시성) | event-driven | `pass/PassCancellationConcurrencyTest.kt` | exact |
| `schedule/ClassSessionConcurrencyTest.kt` | test(동시성) | event-driven | `pass/PassCancellationConcurrencyTest.kt` | exact |
| `reservation/PassDeductionCandidateTest.kt` | test(단위) | — | `pass/PassAdjustmentPolicyTest.kt` | exact |
| `*RepositoryTest.kt`(ClassSession·Reservation) | test(통합) | CRUD | `pass/PassRepositoryTest.kt` | exact |
| `*ControllerTest.kt`(Admin/Member Schedule·Reservation) | test(통합) | request-response | `pass/AdminPassControllerTest.kt` | exact |

## Pattern Assignments

### `schedule/ClassSchedule.kt` (model, CRUD 읽기 전용)

**Analog:** `src/main/kotlin/com/goldwrestling/pass/Pass.kt` (구조) + `src/main/kotlin/com/goldwrestling/branch/Branch.kt` (단순성)

**엔티티 골격** (Pass.kt lines 39-76 패턴 그대로 적용):
```kotlin
@Entity
@Table(name = "class_schedule")
class ClassSchedule(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    val branch: Branch,
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    val dayOfWeek: DayOfWeek,          // java.time.DayOfWeek 재사용 — 새 enum 만들지 않음 (RESEARCH)
    @Enumerated(EnumType.STRING)
    @Column(name = "class_type", nullable = false, length = 20)
    val classType: ClassType,
    @Column(name = "start_time", nullable = false)
    val startTime: LocalTime,
    @Column(name = "end_time", nullable = false)
    val endTime: LocalTime,
    @Column(nullable = true)
    val capacity: Int?,                // EVENING만 null — ck_class_schedule_capacity_by_type이 DB에서 강제
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
```
**핵심 원칙(Pass.kt KDoc 관례 그대로 적용):** 타입별 필수/금지 컬럼 규칙은 엔티티가 아니라 DB CHECK가 강제한다(`ck_pass_remaining_count_by_type`와 동일 사고 → `ck_class_schedule_capacity_by_type`). 이 phase는 시드 고정이라 companion factory(`Pass.register`류)는 만들 필요 없음 — Flyway INSERT가 그 역할을 대신한다.

---

### `schedule/ClassSession.kt` (model, get-or-create + 휴강 상태 전환)

**Analog:** `src/main/kotlin/com/goldwrestling/pass/Pass.kt`

**취소(휴강) 메타데이터 완전성 CHECK 관례** — `ck_pass_cancellation`(V4 lines 37-40)을 그대로 `ck_class_session_cancellation`에 적용(RESEARCH Code Examples 이미 확정):
```sql
CONSTRAINT ck_class_session_cancellation CHECK (
    (status = 'CANCELED' AND canceled_at IS NOT NULL AND cancel_reason IS NOT NULL AND canceled_by_admin_id IS NOT NULL) OR
    (status <> 'CANCELED' AND canceled_at IS NULL AND cancel_reason IS NULL AND canceled_by_admin_id IS NULL)
)
```

**판정만 하는 도메인 메서드, 반영은 조건부 UPDATE** (Pass.kt lines 167-184 `resolveCancellationOffset` 패턴):
```kotlin
// ClassSession.kt — 판정만, 대입 없음(D-072)
fun assertSuspendable() {
    if (status == ClassSessionStatus.CANCELED) throw ClassSessionAlreadyCanceledException()
}
```
실제 상태 전환은 `ClassSessionRepository`의 조건부 UPDATE가 담당한다 — Pass.kt KDoc의 "다음에 이 메서드를 읽는 사람이 여기에 대입문을 추가하지 않는다" 경고 문구를 그대로 유지할 것.

---

### `schedule/ClassSessionRepository.kt` (get-or-create + 정원 조건부 UPDATE)

**Analog:** `src/main/kotlin/com/goldwrestling/pass/PassRepository.kt` (조건부 UPDATE 관례) — **단, 네이티브 `INSERT ... ON CONFLICT`는 이 저장소 최초 도입**이므로 실사용 전례가 없다(RESEARCH Pitfall 1, Standard Stack 확인).

**조건부 UPDATE 골격** (`PassRepository.adjustRemainingCount`, lines 44-53 그대로 이식):
```kotlin
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query(
    "update Pass p set p.remainingCount = p.remainingCount + :amount " +
        "where p.id = :id and p.status = com.goldwrestling.pass.PassStatus.ACTIVE " +
        "and p.remainingCount + :amount >= 0",
)
fun adjustRemainingCount(@Param("id") id: Long, @Param("amount") amount: BigDecimal): Int
```
→ `ClassSessionRepository.incrementReservedCountIfCapacityAvailable`가 같은 형태로 `reserved_count + 1 <= capacity` 조건을 건다(RESEARCH Pattern 2, 이미 코드 완성됨 — 04-RESEARCH.md Architecture Patterns 참조).

**KDoc 필수 관례**: `PassRepository.adjustRemainingCount`의 KDoc(반환 0의 의미, `flushAutomatically`/`clearAutomatically` 이유, 호출 전 스칼라 값 미리 꺼내기 경고)을 새 메서드마다 동일 형식으로 반복 작성한다 — 이 프로젝트는 조건부 UPDATE마다 이 설명을 KDoc에 남기는 것이 확립된 관례다.

**get-or-create 네이티브 쿼리**(RESEARCH가 이미 코드 확정, 이 파일에서 최초 적용):
```kotlin
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query(
    value = """
        insert into class_session (class_schedule_id, class_date, class_type, start_time, end_time, capacity, status, created_at)
        values (:scheduleId, :classDate, :classType, :startTime, :endTime, :capacity, 'SCHEDULED', now())
        on conflict (class_schedule_id, class_date) do nothing
    """,
    nativeQuery = true,
)
fun insertIfAbsent(...): Int
```
반환값을 확인하지 않고 항상 재조회한다 — `RefreshTokenRepository.revokeIfUsable` KDoc의 "반환 0은 실패가 아니라 상태 정보" 원칙과 같은 사고.

---

### `reservation/Reservation.kt` (model, 예약 생성/취소/변경)

**Analog:** `src/main/kotlin/com/goldwrestling/pass/Pass.kt`(취소 판정) + `src/main/kotlin/com/goldwrestling/auth/RefreshToken.kt`(주체 nullable 쌍)

**취소 판정 메서드**(Pattern 5, RESEARCH가 이미 코드 확정 — Pass.kt의 D-072 관례를 그대로 적용):
```kotlin
// Reservation.kt — 판정만, 대입 없음
fun assertCancelableByMember(today: LocalDate) {
    if (status == ReservationStatus.CANCELED) throw ReservationAlreadyCanceledException()
    if (classDate == today) throw SameDayCancellationNotAllowedException()
}
```

**주체 nullable 쌍 표현** — `RefreshToken.kt`(lines 26-32, 61-66)의 `member: Member?` / `admin: Admin?` + `principalType()` 판별 메서드 패턴을 `canceledByMember`/`canceledByAdmin`에 적용:
```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "canceled_by_member_id")
var canceledByMember: Member? = null
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "canceled_by_admin_id")
var canceledByAdmin: Admin? = null
```
DB에서 "정확히 하나"는 `ck_reservation_cancellation` CHECK가 강제(RESEARCH Code Examples에 SQL 확정됨) — `RefreshToken`의 `ck_refresh_token_principal`과 동일한 사고.

---

### `reservation/ReservationRepository.kt` (compare-and-swap 취소)

**Analog:** `src/main/kotlin/com/goldwrestling/pass/PassRepository.kt` (`cancelIfNotCanceled`, lines 78-89)

```kotlin
// PassRepository.cancelIfNotCanceled 그대로 이식 — 대상 엔티티만 Reservation으로 교체
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query(
    "update Reservation r set r.status = com.goldwrestling.reservation.ReservationStatus.CANCELED, " +
        "r.canceledAt = :now, r.canceledByMemberId = :memberId, r.refunded = true " +
        "where r.id = :id and r.status = com.goldwrestling.reservation.ReservationStatus.ACTIVE",
)
fun cancelByMemberIfActive(@Param("id") id: Long, @Param("memberId") memberId: Long, @Param("now") now: OffsetDateTime): Int
```
관리자 대리 취소는 `refunded` 파라미터를 요청에서 받는 별도 오버로드(RESEARCH Pattern 5 후반부 확정).

**Pitfall 2 적용 지점**: 예약 생성 경로에서 `adjustRemainingCount` 반환 0 → 예외로 변환. 취소/복구 경로에서 반환 0(등록취소된 이용권) → **무시하고 계속 진행**. 두 호출부를 구분해 KDoc에 명시할 것(`pass/AdminPassService.adjust`의 "재조회 후 정확한 예외로 변환" 관례와, 취소 복구의 "무시" 예외를 나란히 문서화).

---

### `reservation/MemberReservationService.kt` (예약 생성/취소/변경 트랜잭션 조립)

**Analog:** `src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt` (`register`·`cancel` 메서드 전체 구조)

**클래스 골격**(AdminPassService.kt lines 27-36):
```kotlin
@Service
@Transactional(readOnly = true)
class MemberReservationService(
    private val classScheduleRepository: ClassScheduleRepository,
    private val classSessionRepository: ClassSessionRepository,
    private val reservationRepository: ReservationRepository,
    private val passRepository: PassRepository,
    private val passTransactionRepository: PassTransactionRepository,
    private val notificationService: NotificationService,
    private val memberStateGate: MemberStateGate,
    private val clock: Clock,
)
```

**"조회 → 판정 → 조건부 UPDATE → 재조회 → 이력 저장" 순서**(AdminPassService.cancel, lines 233-283 그대로 재사용할 뼈대):
1. `memberStateGate.requireActive(principal)` — 신규 진입점이므로 D-040 규칙에 따라 필요 여부 확인 필수(Don't Hand-Roll 표)
2. `ClassSessionRepository.insertIfAbsent` + 재조회 (get-or-create, Pitfall 1 회피)
3. 휴강 여부·예약 창(오픈/마감)·중복 예약 사전 판정(엔티티/도메인 메서드, 판정만)
4. 정원 조건부 UPDATE(`incrementReservedCountIfCapacityAvailable`) — 반환 0 → 예외 변환
5. 이용권 후보 조회(`PassRepository.findDeductionCandidates`) → 없으면 `InsufficientPassCountException`
6. `PassRepository.adjustRemainingCount` 차감 — 반환 0 → **재시도 없이 즉시 실패**(CONTEXT.md Claude's Discretion 확정)
7. `Reservation` INSERT
8. `PassTransaction` INSERT(`RESERVE`, member 주체 — Pitfall 4 "행위자 기준" 준수)
9. `NotificationService.create(...)` 호출

**Pitfall 4 준수**: 회원 셀프 예약/취소는 `PassTransaction.member` 주체, 관리자 대리 작업·휴강은 `admin` 주체 — `AdminPassService.adjust`(lines 110-113)의 "벌크 UPDATE 호출 전 스칼라 값 미리 꺼내기" 관례를 여기서도 지킨다(정원/잔여 조건부 UPDATE 직후 세션·이용권 엔티티가 준영속화됨).

---

### `reservation/AdminReservationController.kt` / 관리자 목록 조회

**Analog:** `src/main/kotlin/com/goldwrestling/member/AdminMemberController.kt`(lines 34-48) — `@ParameterObject @ModelAttribute @Valid condition` + `PageResponse` 반환 패턴 그대로.

```kotlin
@GetMapping
fun search(
    @ParameterObject @ModelAttribute @Valid condition: ReservationSearchCondition,
): PageResponse<ReservationResponse> = adminReservationService.search(condition)
```
서비스 쪽 Specification 조합은 `AdminMemberService.search`(lines 35-47) 그대로:
```kotlin
val specification =
    Specification.allOf<Reservation>(
        listOfNotNull(
            ReservationSpecifications.classDateBetween(condition.from, condition.to),
            ReservationSpecifications.hasClassType(condition.classType),
            ReservationSpecifications.memberKeywordContains(condition.keyword),
        ),
    )
val pageable = PageRequest.of(condition.page, condition.size, Sort.by(Sort.Direction.DESC, "reservedAt"))
```

---

### `reservation/ReservationSpecifications.kt` (IDOR 방어 포함)

**Analog:** `src/main/kotlin/com/goldwrestling/pass/PassTransactionSpecifications.kt`(lines 14-31, `ownedByMember`)

```kotlin
// non-null 반환 — 호출부가 이 조건을 빼먹을 수 없게 한다(IDOR 방어의 핵심, KDoc 그대로 인용)
fun ownedByMember(memberId: Long): Specification<Reservation> =
    Specification { root, _, criteriaBuilder ->
        criteriaBuilder.equal(root.get<Member>("member").get<Long>("id"), memberId)
    }
```
검색어(회원명) 조건은 `MemberSpecifications.keywordContains`(lines 36-62)의 LIKE 이스케이프·정규화 관례를 그대로 재사용 — 새 LIKE 이스케이프 로직을 재작성하지 않는다.

---

### `notification/Notification.kt` (append-only 이벤트 레코드)

**Analog:** `src/main/kotlin/com/goldwrestling/pass/PassTransaction.kt` (전체)

```kotlin
// PassTransaction과 동일하게 전 필드 val — append-only를 코드로 보장(setter 없음)
@Entity
@Table(name = "notification")
class Notification(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val type: NotificationType,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    val reservation: Reservation?,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_session_id")
    val classSession: ClassSession?,      // 휴강 세션당 1건 요약형 대응(D-093)
    @Column(name = "message", nullable = false, length = 500)
    val message: String,                  // 비정규화된 표시 정보(회원명·수업일시 등)
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: OffsetDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
```
`PassTransaction`의 "주체는 항상 admin"이던 제약과 달리 Notification은 수신자가 항상 관리자로 고정(D-093)이라 주체 컬럼 자체가 필요 없다 — 이 차이를 KDoc에 명시할 것.

---

### `notification/NotificationService.kt` (create만)

**No direct analog** — 이 프로젝트에 "생성 전용 서비스"(조회 없음) 전례가 없다. 가장 가까운 참고는 `AdminPassService`가 같은 `@Transactional` 메서드 안에서 `PassTransaction`을 인라인 저장하는 방식(lines 74-84, 129-138)이다. `NotificationService.create(...)`는 `MemberReservationService`/`AdminReservationService`/`AdminScheduleService`(휴강) 4개 호출부에서 같은 트랜잭션 안에 호출되는 헬퍼로 설계한다 — 별도 `@Transactional`을 붙이지 않고 호출부 트랜잭션에 편승한다(D-020 "서비스 메서드 = 트랜잭션 단위"의 연장, 단 이 메서드는 호출되는 쪽이므로 자체 경계를 열지 않는다).

---

### `pass/AdminPassService.kt` — 기존 코드 수정 지점 (D-085 선행 검사)

**Analog:** 자기 자신의 기존 `cancel` 메서드(lines 233-283)

`cancel` 메서드 도입부(조회 직후, 판정 이전)에 활성 예약 검사를 추가한다:
```kotlin
@Transactional
fun cancel(passId: Long, request: CancelPassRequest, adminId: Long): PassResponse {
    val pass = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }
    // D-085 신설: 대상 이용권으로 잡힌 활성 예약이 있으면 등록 취소를 거부한다.
    if (reservationRepository.existsByPassIdAndStatus(passId, ReservationStatus.ACTIVE)) {
        throw PassHasActiveReservationException()
    }
    val admin = adminRepository.findById(adminId).orElseThrow { ... }
    // ... 이하 기존 로직 그대로
```
**의존 방향 주의**(RESEARCH Integration Points): `pass` 패키지가 `reservation` 패키지를 참조하게 된다 — 기능별 패키지(D-018) 원칙상 방향이 역전되는 유일한 지점이므로, 새 예외 `PassHasActiveReservationException`은 `pass` 패키지에 두고 `reservationRepository.existsBy...`만 참조하는 최소 결합으로 유지한다.

---

### `pass/PassRepository.kt` — 기존 코드 수정 지점 (이용권 후보 조회)

**Analog:** 같은 파일의 기존 `@Query` 메서드 형태(lines 44-114 전체 관례)

```kotlin
@Query(
    "select p from Pass p where p.member.id = :memberId and p.type = :type " +
        "and p.status = com.goldwrestling.pass.PassStatus.ACTIVE " +
        "and p.endDate >= :classDate and p.remainingCount >= :requiredAmount " +
        "order by p.endDate asc, p.id asc",
)
fun findDeductionCandidates(
    @Param("memberId") memberId: Long,
    @Param("type") type: PassType,
    @Param("classDate") classDate: LocalDate,
    @Param("requiredAmount") requiredAmount: BigDecimal,
): List<Pass>
```
`Pass.displayStatus`/`isExpired`(D-066 종료일 포함 판정, lines 115-127)와 **같은 비교축**(`endDate >= classDate`)을 쓴다 — Don't Hand-Roll 표가 이미 경고한 "다른 비교식을 새로 만들면 경계값에서 어긋난다"를 지킨다.

---

### `pass/PassTransaction.kt` — 기존 코드 수정 지점 (회원 주체 확장)

**Analog:** `src/main/kotlin/com/goldwrestling/auth/RefreshToken.kt` (lines 26-32, `member: Member?` / `admin: Admin?` nullable 쌍 + `principalType()` 판별)

```kotlin
// admin을 nullable로 완화하고 member를 추가 — DB ck_pass_transaction_subject CHECK와 짝을 이룬다
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "admin_id")
val admin: Admin?,
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "member_id")
val member: Member?,
```
`RefreshToken.principalType()`(lines 61-66)과 같은 판별 메서드를 추가할지는 실제 호출부 필요성에 따라 결정(Claude's Discretion 범위) — 최소한 "주체 중 정확히 하나" 불변식은 DB CHECK가 최종 방어선임을 KDoc에 명시(Pass.kt·RefreshToken.kt 공통 관례).

---

### `db/migration/V6__create_schedule_reservation_notification.sql`

**Analog:** `src/main/resources/db/migration/V4__create_pass_tables.sql` (전체 구조)

- 서로게이트 PK `id BIGINT GENERATED BY DEFAULT AS IDENTITY`
- `TIMESTAMPTZ NOT NULL DEFAULT now()` for `created_at`
- 헤더 주석에 근거 결정 번호 나열 (`-- V6: ... D-086~D-094 결정에 근거한다`)
- 타입별 컬럼 규칙은 CHECK로 (`ck_pass_remaining_count_by_type` → `ck_class_schedule_capacity_by_type`)
- 취소 메타데이터 완전성은 CHECK로 (`ck_pass_cancellation` → `ck_class_session_cancellation`, `ck_reservation_cancellation`)
- 전체 DDL은 04-RESEARCH.md "Code Examples" 섹션에 이미 완성돼 있음 — 그대로 채택 가능

**주의**: 이미 커밋된 V4·V5는 절대 수정하지 않는다. 번호는 계획 단계에서 확정(V6 이후 미사용 확인 필요).

---

### `db/migration/V7__extend_pass_transaction_subject.sql`

**Analog:** `src/main/resources/db/migration/V5__add_member_kakao_profile.sql` (단순 `ALTER TABLE` 패턴)

```sql
ALTER TABLE pass_transaction ALTER COLUMN admin_id DROP NOT NULL;
ALTER TABLE pass_transaction ADD COLUMN member_id BIGINT REFERENCES member (id);
ALTER TABLE pass_transaction ADD CONSTRAINT ck_pass_transaction_subject CHECK (
    (admin_id IS NOT NULL AND member_id IS NULL) OR
    (admin_id IS NULL AND member_id IS NOT NULL)
);
```
V5의 "왜 nullable인가"를 설명하는 헤더 주석 관례(lines 1-11)를 그대로 따른다 — 이번엔 "왜 CHECK로 정확히 하나를 강제하는가"(D-030 예고, Pitfall 4)를 설명.

---

### `common/error/ErrorCode.kt` — 신규 에러코드 추가

**Analog:** 같은 파일의 기존 항목 추가 관례(lines 71-90, Pass 도메인 코드들)

```kotlin
/** 정원 초과로 예약 실패 (RESV-06) */
RESERVATION_CAPACITY_EXCEEDED(HttpStatus.CONFLICT),

/** 당일 취소/변경 시도 (policies §3) */
SAME_DAY_CANCELLATION_NOT_ALLOWED(HttpStatus.CONFLICT),

/** 예약 창(오픈 전/마감 후) 밖의 요청 */
RESERVATION_WINDOW_CLOSED(HttpStatus.CONFLICT),

/** 같은 회원의 같은 날짜·시각 중복 예약 (D-088) */
DUPLICATE_RESERVATION(HttpStatus.CONFLICT),

/** 휴강된 수업에 대한 예약 시도 */
CLASS_SESSION_CANCELED(HttpStatus.CONFLICT),

/** 대상 이용권으로 잡힌 활성 예약이 있어 등록 취소 거부 (D-085) */
PASS_HAS_ACTIVE_RESERVATION(HttpStatus.CONFLICT),
```
`docs/error-codes.md`를 같은 PR에서 갱신할 것(CLAUDE.md 규칙 4) — 이 표는 `ErrorCode.defaultStatus`와 항상 일치해야 한다.

## Shared Patterns

### 조건부 UPDATE(compare-and-swap)로 상태 전환 (D-021·D-072)
**Source:** `pass/PassRepository.kt` (`adjustRemainingCount`, `cancelIfNotCanceled`, `changePeriodIfUnchanged`) + `auth/RefreshTokenRepository.kt`(`revokeIfUsable`)
**Apply to:** `ClassSessionRepository`(정원 증감·세션 휴강), `ReservationRepository`(취소), `PassRepository`(예약 차감/복구 — 그대로 재사용)
```kotlin
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query("update <Entity> e set ... where e.id = :id and <상태 조건>")
fun <조건부이름>(...): Int
// 반환 0 = 경쟁 패배 또는 사전 판정 이후 상태 변경. 호출부가 재조회 후 정확한 예외로 변환.
```

### 도메인 메서드는 판정만, 반영은 조건부 UPDATE (D-072)
**Source:** `pass/Pass.kt` (`resolveCancellationOffset`, `resolvePeriodChange`, `validateAdjustment`)
**Apply to:** `ClassSession.assertSuspendable`, `Reservation.assertCancelableByMember`, `Reservation.assertChangeable`
엔티티 메서드에 `status =`/필드 대입문을 추가하지 않는다 — 이 원칙 위반이 Phase 3의 실제 회귀 버그(T-03-38) 원인이었다(RESEARCH Anti-Patterns).

### 페이지네이션 응답
**Source:** `member/dto/PageResponse.kt`
**Apply to:** `AdminReservationService.search`, (필요 시) `AdminScheduleService` 목록류 — 그대로 import, 새 DTO 만들지 않음.

### 관리자 목록 필터 — `@ParameterObject` + `Specification`
**Source:** `member/AdminMemberController.kt` + `member/MemberSpecifications.kt` + `pass/PassTransactionSpecifications.kt`
**Apply to:** `AdminReservationController`(`ReservationSearchCondition`), `AdminScheduleController`(필요 시)
개별 `@RequestParam`으로 펼치지 않는다(D-054) — `@ParameterObject @ModelAttribute @Valid condition` 고정.

### IDOR 방어 — non-null 소유권 Specification
**Source:** `pass/PassTransactionSpecifications.kt` (`ownedByMember`)
**Apply to:** `ReservationSpecifications.ownedByMember(memberId)` — 회원 본인 예약 목록(RESV-05)에서 반드시 non-null 필수 조건으로 결합.

### 회원 상태 게이트
**Source:** `member/MemberStateGate.kt`
**Apply to:** `MemberReservationController`의 예약 생성·취소·변경 엔드포인트 — `MemberStateGate.requireActive(principal)` 호출 필요 여부를 새 엔드포인트마다 확인(D-040 유의사항, 빠뜨려도 컴파일·테스트가 안 잡아준다).

### Clock 주입 — 시각 판정
**Source:** `config/ClockConfig.kt` + `pass/AdminPassService.kt`(`val today = LocalDate.now(clock)`, `val now = OffsetDateTime.now(clock)`)
**Apply to:** 모든 신규 서비스 — 예약 창 오픈/마감, 당일 판정(date), 마감 판정(datetime)을 **분리된 메서드**로 구현(RESEARCH Pitfall 3).

### 에러 응답 — DomainException + ErrorCode
**Source:** `common/error/DomainException.kt` + `common/error/ErrorCode.kt` + `pass/PassExceptions.kt`
**Apply to:** `schedule/ScheduleExceptions.kt`, `reservation/ReservationExceptions.kt` — 각 예외는 `DomainException`을 상속, 사용자 대면 메시지만 담고 내부 식별자를 보간하지 않는다(`PassNotFoundException`의 `@Suppress("UNUSED_PARAMETER")` 관례 참고).

### LAZY 연관 접근 — 벌크 UPDATE 직후 준영속 함정
**Source:** `pass/AdminPassService.kt`(`adjust`, `changePeriod`, `cancel` 전체) KDoc 경고
**Apply to:** 정원 조건부 UPDATE·잔여 차감 직후 `ClassSession`/`Pass` 엔티티의 LAZY 필드 접근 금지 — 조건부 UPDATE 호출 전에 필요한 스칼라 값을 지역 변수로 미리 꺼내두거나, 호출 후 재조회한다.

### 동시성 통합테스트 골격
**Source:** `pass/PassCancellationConcurrencyTest.kt` (전체)
**Apply to:** `reservation/ReservationCapacityConcurrencyTest.kt`(RESV-06, 이 phase 핵심 테스트), `schedule/ClassSessionConcurrencyTest.kt`(SCHED-02 get-or-create 경쟁)
```kotlin
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class XxxConcurrencyTest {
    // @Transactional 미부착 — 각 스레드가 별도 트랜잭션이어야 경쟁이 재현된다
    // ExecutorService + CountDownLatch(startLatch/doneLatch) + AtomicInteger(successCount)
    // + Collections.synchronizedList(실패 수집)
    // @AfterEach에서 JdbcClient로 직접 정리 (트랜잭션 롤백에 기대지 않음)
}
```

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `schedule/ClassSessionRepository.insertIfAbsent`(네이티브 `ON CONFLICT`) | repository method | event-driven | 이 저장소에 `nativeQuery = true` 실사용 전례가 0건(RESEARCH 확인). `PassRepository`의 조건부 UPDATE 관례를 뼈대로 삼되, SQL 자체는 04-RESEARCH.md Code Examples의 완성된 쿼리를 채택 |
| `notification/NotificationService.kt` | service | event-driven | "생성 전용, 조회 없음" 서비스 전례가 없음. `AdminPassService`의 인라인 이력 저장 방식을 참고해 헬퍼로 설계 |
| `common/time/WeekRange.kt` | utility | transform | 주 단위 날짜 계산 유틸 전례 없음(conventions §1이 패키지만 예고, 코드는 이 phase가 최초). `PhoneNumberNormalizer.kt`(무상태 순수 object 스타일)를 형식적 참고로만 사용 |

## Metadata

**Analog search scope:** `src/main/kotlin/com/goldwrestling/{pass,member,auth,branch,common,config}`, `src/main/resources/db/migration`, `src/test/kotlin/com/goldwrestling/{pass,auth,support}`
**Files scanned:** 32개 직접 조회(엔티티 6, 리포지토리 5, 서비스 4, 컨트롤러 4, DTO/Specification 6, 예외/에러 4, 마이그레이션 3, 테스트 6)
**Pattern extraction date:** 2026-08-07
