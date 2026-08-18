# Phase 6: 운영 - Pattern Map

**Mapped:** 2026-08-18
**Files analyzed:** 24 (신규 21 + 수정 3)
**Analogs found:** 24 / 24 (전부 exact 또는 role-match — 이 phase는 새 패턴을 발명하지 않는다, RESEARCH.md 결론)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `attendance/Attendance.kt` | model (entity) | CRUD | `reservation/Reservation.kt` | exact |
| `attendance/AttendanceStatus.kt` | model (enum) | n/a | `notification/NotificationType.kt` | role-match |
| `attendance/AttendanceRepository.kt` | model (repository) | CRUD + batch-projection | `pass/PassRepository.kt` | exact |
| `attendance/AttendanceService.kt` | service | CRUD (조건부 원자 갱신 + 원장) | `reservation/ReservationLedgerSupport.kt` + `pass/AdminPassService.kt` | exact |
| `attendance/EveningHalfDeductionPolicy.kt` | utility (순수 판정 object) | transform | `reservation/ReservationPassPolicy.kt` | exact |
| `attendance/AdminAttendanceController.kt` | controller | request-response | `schedule/AdminScheduleController.kt` | exact |
| `attendance/AttendanceExceptions.kt` | utility (도메인 예외) | n/a | `pass/PassExceptions.kt` + `reservation/ReservationExceptions.kt` | exact |
| `attendance/dto/CheckAttendanceRequest.kt` | dto | request-response | `pass/dto/AdjustPassRequest.kt` | role-match |
| `attendance/dto/ClassSessionAttendanceRosterResponse.kt`, `AttendanceResponse.kt` | dto | request-response | `schedule/dto/AdminWeeklyBoardResponse.kt`(명단형 응답, 미열람) / `pass/dto/PassResponse.kt` 관례 | role-match |
| `notice/Notice.kt` | model (entity) | CRUD | `reservation/Reservation.kt`(가변 필드) + `notification/Notification.kt`(append 표시 필드) | role-match |
| `notice/NoticeRepository.kt` | model (repository) | CRUD | `notification/NotificationRepository.kt` | role-match |
| `notice/AdminNoticeController.kt` | controller | request-response (CRUD) | `member/AdminMemberController.kt` | exact |
| `notice/MemberNoticeController.kt` | controller | request-response (열람) | `pass/MemberPassController.kt` | exact |
| `notice/NoticeService.kt` | service | CRUD | `pass/AdminPassService.kt` | exact |
| `notice/NoticeExceptions.kt` | utility (도메인 예외) | n/a | `reservation/ReservationExceptions.kt`(`*NotFoundException`) | exact |
| `notice/dto/CreateNoticeRequest.kt`, `UpdateNoticeRequest.kt` | dto | request-response | `pass/dto/AdjustPassRequest.kt` | role-match |
| `notice/dto/NoticeSummaryResponse.kt`, `NoticeDetailResponse.kt` | dto | request-response | `member/dto/PageResponse.kt` + `pass/dto/PassResponse.kt` | role-match |
| `notification/NotificationRepository.kt` (확장) | model (repository) | CRUD + bulk-update + Specification | `pass/PassRepository.kt`(`adjustRemainingCount`) | exact |
| `notification/AdminNotificationController.kt` | controller | request-response (폴링) | `schedule/AdminScheduleController.kt` | exact |
| `notification/NotificationQueryService.kt` | service | request-response (조회 + 벌크 커맨드) | `pass/AdminPassService.kt` | role-match |
| `notification/NotificationSpecifications.kt` | utility (Specification) | transform | `reservation/ReservationSpecifications.kt` | exact |
| `notification/dto/NotificationListResponse.kt`, `ActivityFeedItemResponse.kt` | dto | request-response | `member/dto/PageResponse.kt` | exact |
| `pass/TransactionReason.kt` (수정 — `EVENING_HALF_REFUND` 추가) | model (enum 상수 추가) | n/a | 자기 자신(기존 `EVENING_HALF` 옆에 추가) | exact |
| `batch/InactivityBatchRunner.kt` (수정 — `lastAttendanceDate` 배선) | service | batch | `reservation/ReservationRepository.findLastActiveReservationClassDates` 호출부 관례 | exact |
| `db/migration/V11__create_attendance_and_notice.sql` | migration | n/a | `V9__relax_pass_transaction_subject_and_add_batch_execution.sql` + `V6__create_schedule_reservation_notification.sql` | exact |

## Pattern Assignments

### `attendance/Attendance.kt` (model, CRUD)

**Analog:** `reservation/Reservation.kt`

**Imports pattern** (`Reservation.kt:1-21`):
```kotlin
package com.goldwrestling.reservation

import com.goldwrestling.admin.Admin
import com.goldwrestling.member.Member
import com.goldwrestling.pass.Pass
import com.goldwrestling.schedule.ClassSession
import com.goldwrestling.schedule.ClassType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
```

**Core entity pattern** (`Reservation.kt:43-83`) — FK 3종(`member`/`classSession`/`pass`) 전부 `LAZY` + `nullable = false`, PK는 별도 `@Id` 블록, `status`만 `var`(나머지는 이력 불변을 위해 `val`):
```kotlin
@Entity
@Table(name = "reservation")
class Reservation(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_session_id", nullable = false)
    val classSession: ClassSession,
    ...
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReservationStatus,
    ...
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
```
**Attendance 적용 시 차이점**: `pass_transaction_id`는 **nullable FK**(저녁반만 채움, 예약제/1:1은 항상 null) — `Notification.kt:42-47`의 `reservation`/`classSession` nullable `@ManyToOne` 관례를 그대로 참고한다. `status`(`ATTENDED`/`ABSENT`)는 소급 수정을 허용하므로(D-127) `Reservation.status`처럼 `var`로 둔다.

**Anti-pattern 경고 (RESEARCH Pitfall 2, 직접 인용):** 예약의 부분 유니크(`ux_reservation_session_member_active ... WHERE status = 'ACTIVE'`, `V6:100-101`)를 그대로 복사하지 않는다 — 출석은 **조건 없는 일반 `UNIQUE (class_session_id, member_id)`**가 맞다.

---

### `attendance/AttendanceStatus.kt` (model, enum)

**Analog:** `notification/NotificationType.kt` (단순 enum, KDoc으로 각 값의 의미를 설명하는 관례)
```kotlin
package com.goldwrestling.notification

enum class NotificationType {
    /** 회원이 예약을 생성했다 */
    RESERVATION_CREATED,
    ...
}
```
`AttendanceStatus`는 `ATTENDED`/`ABSENT` 2값 — 예약제/1:1은 둘 다, 저녁반은 `ATTENDED`만 쓴다는 것을 KDoc에 명시한다(D-127).

---

### `attendance/AttendanceRepository.kt` (model, CRUD + batch-projection)

**Analog:** `pass/PassRepository.kt`

**조건부 원자 갱신 관례** (`PassRepository.kt:45-54`) — `EveningHalfDeductionPolicy`가 사용할 `PassRepository.adjustRemainingCount`를 그대로 재사용한다(새 메서드 불필요). `AttendanceRepository` 자체에는 조건부 UPDATE가 필요 없다 — 출석 INSERT/상태 UPDATE는 유니크 제약이 방어선이다.

**벌크 프로젝션 쿼리 관례** (`PassRepository.kt:180-191`, `findLastDeductibleSessionPassRegistrationDates`) — CR-03 배선에 그대로 이식:
```kotlin
@Query(
    "select p.member.id as memberId, max(p.createdAt) as timestamp from Pass p " +
        "where p.member.id in :memberIds " +
        "and p.type = com.goldwrestling.pass.PassType.SESSION_PASS " +
        "and p.status = com.goldwrestling.pass.PassStatus.ACTIVE " +
        "and p.endDate >= :today and p.remainingCount > 0 " +
        "group by p.member.id",
)
fun findLastDeductibleSessionPassRegistrationDates(
    @Param("memberIds") memberIds: Collection<Long>,
    @Param("today") today: LocalDate,
): List<MemberTimestampProjection>
```
**AttendanceRepository에 추가할 메서드** — RESEARCH.md가 이미 완성된 코드를 제시했다(Pattern 5, `AttendanceRepository.kt` 신규):
```kotlin
@Query(
    "select a.member.id as memberId, max(a.classSession.classDate) as date from Attendance a " +
        "where a.member.id in :memberIds and a.status = com.goldwrestling.attendance.AttendanceStatus.ATTENDED " +
        "group by a.member.id",
)
fun findLastAttendedClassDates(@Param("memberIds") memberIds: Collection<Long>): List<MemberDateProjection>
```
`MemberDateProjection`/`MemberTimestampProjection`은 `common.projection`에 이미 있다(수정 불필요):
```kotlin
// common/projection/MemberDateProjection.kt:13-17
interface MemberDateProjection {
    fun getMemberId(): Long
    fun getDate(): LocalDate?
}
```

---

### `attendance/AttendanceService.kt` (service, CRUD — 조건부 원자 갱신 + 원장)

**Analog:** `reservation/ReservationLedgerSupport.kt` (구조) + `pass/AdminPassService.kt` (트랜잭션 애노테이션 관례)

**트랜잭션 애노테이션 관례** (`AdminPassService.kt:35-45`) — 클래스 레벨 `readOnly = true`, 쓰기 메서드만 오버라이드(D-020):
```kotlin
@Service
@Transactional(readOnly = true)
class AdminPassService(
    ...
) {
    @Transactional
    fun register(...): PassResponse { ... }
}
```

**핵심 흐름** (`ReservationLedgerSupport.kt:62-154`, `createReservation`) — "차감 후보 선정 → 조건부 UPDATE(0행이면 예외) → 재조회 → INSERT → 원장 저장"을 그대로 이식하되, D-128의 "회비 우선 판정" 한 단계를 앞에 끼운다:
```kotlin
val candidates =
    passRepository.findDeductionCandidates(
        memberId, passType, classDate, ReservationPassPolicy.DEDUCTION_AMOUNT,
    )
val candidate = ReservationPassPolicy.selectCandidate(candidates)
val passId = requireNotNull(candidate.id) { "차감 후보 조회는 항상 저장된 Pass만 반환합니다." }

if (passRepository.adjustRemainingCount(passId, ReservationPassPolicy.DEDUCTION_AMOUNT.negate()) == 0) {
    throw InsufficientPassCountException()
}

val refreshedPass = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }
// ... INSERT 후 PassTransaction 저장
```
**RESEARCH.md가 이미 저녁반 버전 구조를 제시함**(Pattern 2, `attendance/AttendanceService.kt`에 그대로 이식할 골격):
```kotlin
fun addEveningAttendance(memberId: Long, classSession: ClassSession, admin: Admin): Attendance {
    val classDate = classSession.classDate // 유효기간 판정은 수업날 기준(D-128) — 오늘 아님(Pitfall 1)

    val hasValidMembership =
        passRepository.existsActiveEveningMembership(memberId, classDate) // 신규 쿼리

    val passTransaction: PassTransaction? =
        if (hasValidMembership) {
            null // 차감 없음(D-128, 회비 우선)
        } else {
            val candidates = passRepository.findDeductionCandidates(memberId, PassType.SESSION_PASS, classDate, HALF_SESSION)
            val candidate = ReservationPassPolicy.selectCandidate(candidates) // 비어있으면 InsufficientPassCountException -> 409
            if (passRepository.adjustRemainingCount(candidate.id!!, HALF_SESSION.negate()) == 0) {
                throw InsufficientPassCountException()
            }
            // ... 재조회 + PassTransaction(EVENING_HALF) 저장
        }

    return attendanceRepository.save(Attendance(..., passTransaction = passTransaction, status = ATTENDED))
}
```
**신규 쿼리** (`existsActiveEveningMembership`) — `PassRepository.findDeductionCandidates` 관례(`PassRepository.kt:132-143`)를 그대로 따른다:
```kotlin
@Query(
    "select count(p) > 0 from Pass p where p.member.id = :memberId and p.type = " +
        "com.goldwrestling.pass.PassType.EVENING_MEMBERSHIP and p.status = com.goldwrestling.pass.PassStatus.ACTIVE " +
        "and p.startDate <= :classDate and p.endDate >= :classDate",
)
fun existsActiveEveningMembership(@Param("memberId") memberId: Long, @Param("classDate") classDate: LocalDate): Boolean
```

**복구 경로 분석** — `ReservationLedgerSupport.restorePassAfterCancellation`(`ReservationLedgerSupport.kt:204-234`)이 `reason` 파라미터를 받아 `CANCEL_REFUND`/`CLASS_CANCELED_REFUND`를 구분하는 관례를 그대로 재사용한다 — 저녁반 출석 삭제 시 `reason = TransactionReason.EVENING_HALF_REFUND`를 넘긴다:
```kotlin
fun restorePassAfterCancellation(
    passId: Long, passStatus: PassStatus, refundRequested: Boolean, canceledAt: OffsetDateTime,
    member: Member?, admin: Admin?,
    reason: TransactionReason = TransactionReason.CANCEL_REFUND,
) {
    if (!ReservationRefundPolicy.shouldRestore(passStatus, refundRequested)) return
    if (passRepository.adjustRemainingCount(passId, ReservationPassPolicy.DEDUCTION_AMOUNT) == 0) {
        throw IllegalStateException("복구 대상 이용권(id=$passId)이 판정 이후 상태가 바뀌어 복구를 반영하지 못했습니다.")
    }
    val refreshedPass = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }
    passTransactionRepository.save(PassTransaction(pass = refreshedPass, amount = ReservationPassPolicy.DEDUCTION_AMOUNT, reason = reason, ...))
}
```
저녁반은 차감량이 `1.0`이 아니라 `0.5`(`HALF_SESSION`)이므로 이 값을 파라미터화해야 한다 — `ReservationLedgerSupport`는 상수(`DEDUCTION_AMOUNT`)로 고정돼 있어 그대로 재사용은 불가하고, `EveningHalfDeductionPolicy`가 별도 상수를 갖는 구조가 맞다(자매 object, RESEARCH 명시).

**Pitfall 상기 (직접 인용):** 유효기간 판정 기준일은 **오늘이 아니라 수업날**(`classSession.classDate`)이다 — `LocalDate.now(clock)`을 `existsActiveEveningMembership`/`findDeductionCandidates`에 넘기면 소급 입력이 잘못 판정된다.

---

### `attendance/EveningHalfDeductionPolicy.kt` (utility, transform)

**Analog:** `reservation/ReservationPassPolicy.kt` (전문)
```kotlin
object ReservationPassPolicy {
    val DEDUCTION_AMOUNT: BigDecimal = BigDecimal("1.0")

    fun requiredPassType(classType: ClassType): PassType = when (classType) {
        ClassType.SESSION -> PassType.SESSION_PASS
        ClassType.LESSON -> PassType.LESSON_PASS
        ClassType.EVENING -> throw ClassSessionNotReservableException()
    }

    fun selectCandidate(candidates: List<Pass>): Pass =
        candidates.firstOrNull() ?: throw InsufficientPassCountException()
}
```
`EveningHalfDeductionPolicy`는 `HALF_SESSION = BigDecimal("0.5")` 상수를 갖고, `selectCandidate`는 `ReservationPassPolicy.selectCandidate`를 그대로 재사용(재구현 금지, Don't Hand-Roll 표)한다. **패키지 위치도 동일 이유로 결정**: `attendance` 패키지에 둬야 `schedule`이 `pass`를 참조하지 않는다(D-018, `ReservationPassPolicy.kt:14-16` KDoc과 동일 논리).

---

### `attendance/AdminAttendanceController.kt` (controller, request-response)

**Analog:** `schedule/AdminScheduleController.kt` (전문 구조)

**클래스 헤더 + 권한 관례** (`AdminScheduleController.kt:21-43`):
```kotlin
/**
 * `SecurityConfig`에서 `/api/admin` 하위 전체가 `hasRole("ADMIN")` 전용이므로, **이 컨트롤러에는
 * 별도 권한 애노테이션을 붙이지 않는다**(D-040).
 *
 * 트랜잭션 애노테이션·`try-catch`를 붙이지 않는다 — 트랜잭션 경계는 서비스(D-020), 에러 응답은
 * `GlobalExceptionHandler`가 담당한다(D-017).
 */
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
`AdminAttendanceController`는 이 그대로: 프리로드 명단 `GET`, 체크/수정 `POST`/`PATCH`, `@AuthenticationPrincipal`로 `adminId` 추출 → 서비스 위임 1줄. 컨트롤러 본문에 `try-catch`/`@Transactional`을 붙이지 않는다.

---

### `attendance/AttendanceExceptions.kt` (utility, 도메인 예외)

**Analog:** `pass/PassExceptions.kt` + `reservation/ReservationExceptions.kt`

**공통 골격** (`DomainException.kt` 전문):
```kotlin
abstract class DomainException(
    val errorCode: ErrorCode,
    message: String,
    val status: HttpStatus = errorCode.defaultStatus,
) : RuntimeException(message)
```

**404 조회 실패 관례** (`PassExceptions.kt:10-16`, id를 메시지에 보간하지 않는다):
```kotlin
@Suppress("UNUSED_PARAMETER")
class PassNotFoundException(
    passId: Long?,
) : DomainException(ErrorCode.PASS_NOT_FOUND, "이용권을 찾을 수 없습니다.")
```

**409 충돌/거부 관례** (`PassExceptions.kt:44-48`):
```kotlin
class InsufficientPassCountException :
    DomainException(ErrorCode.INSUFFICIENT_PASS_COUNT, "잔여 횟수가 부족합니다.")
```
저녁반 차감 불가(409)는 RESEARCH.md Open Question이 신규 코드(예: `EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE`)를 만드는 쪽을 권장한다 — `InsufficientPassCountException`을 그대로 던지지 말고, `AttendanceExceptions.kt`에 이 관례 그대로 신규 예외+`ErrorCode` 상수를 추가한다(플래너가 확정).

**`ErrorCode` 등록 관례** (`ErrorCode.kt:74-90` — enum 상수 + KDoc 1줄 + `defaultStatus`):
```kotlin
enum class ErrorCode(val defaultStatus: HttpStatus) {
    ...
    /** 가감 결과 잔여가 음수가 됨 */
    INSUFFICIENT_PASS_COUNT(HttpStatus.CONFLICT),
    ...
}
```
새 코드는 이 enum 끝부분(`BATCH_EXECUTION_NOT_FOUND` 다음)에 추가하고 `docs/error-codes.md`도 같은 PR에서 갱신한다.

---

### `notice/Notice.kt` (model, CRUD)

**Analog:** `reservation/Reservation.kt`(가변 필드 관례) + `notification/Notification.kt`(비-append 대비)

공지는 hard delete + 수정 가능(D-131)이라 `Notification`(append-only, 전부 `val`)보다 `Reservation`의 `var status`처럼 **수정 대상 필드(`title`/`content`)를 `var`**로 선언하는 편이 맞다. `updated_at`은 `var`로 두고 서비스가 수정 시 직접 갱신한다(`Clock` 주입, `@PreUpdate` 자동화는 이 저장소에 선례 없음 — 명시적 세팅 유지).

```kotlin
@Entity
@Table(name = "notice")
class Notice(
    @Column(nullable = false, length = 200)
    var title: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_admin_id", nullable = false)
    val createdByAdmin: Admin,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
```

---

### `notice/NoticeRepository.kt` (model, CRUD)

**Analog:** `notification/NotificationRepository.kt` (가장 단순한 `JpaRepository` 상속 1줄 관례)
```kotlin
interface NotificationRepository : JpaRepository<Notification, Long>
```
`NoticeRepository`는 최신순 페이지 조회 메서드 하나만 추가하면 된다(`Pageable` 기반, Specification 불필요 — CONTEXT.md가 "정렬만 재량"이라 필터 조합이 없다):
```kotlin
interface NoticeRepository : JpaRepository<Notice, Long> {
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Notice>
}
```

---

### `notice/AdminNoticeController.kt` (controller, CRUD)

**Analog:** `member/AdminMemberController.kt` (전문)
```kotlin
@RestController
@RequestMapping("/api/admin/members")
@Tag(name = "admin-member", description = "관리자 회원 관리")
class AdminMemberController(
    private val adminMemberService: AdminMemberService,
) {
    @GetMapping
    fun search(@ParameterObject @ModelAttribute @Valid condition: MemberSearchCondition): PageResponse<MemberSummaryResponse> =
        adminMemberService.search(condition)

    @GetMapping("/{memberId}")
    fun getDetail(@PathVariable memberId: Long): MemberDetailResponse = adminMemberService.getDetail(memberId)

    @PatchMapping("/{memberId}/status")
    fun changeStatus(@PathVariable memberId: Long, @Valid @RequestBody request: UpdateMemberStatusRequest): MemberDetailResponse =
        adminMemberService.changeStatus(memberId, request.status)
}
```
`AdminNoticeController`는 `RequestMapping("/api/admin/notices")` + `GET`(목록)/`GET/{id}`(상세)/`POST`(등록)/`PATCH/{id}`(수정)/`DELETE/{id}`(hard delete) 5개 메서드, 권한 애노테이션 없음(D-040), 트랜잭션·try-catch 없음(D-017/D-020) — 이 컨트롤러와 동일 골격.

---

### `notice/MemberNoticeController.kt` (controller, 열람)

**Analog:** `pass/MemberPassController.kt` (전문)
```kotlin
@RestController
@RequestMapping("/api/members/me")
@Tag(name = "member-pass", description = "회원 본인 이용권·이력 조회")
class MemberPassController(
    private val memberPassService: MemberPassService,
) {
    @GetMapping("/pass-transactions")
    fun getMyTransactions(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        @ParameterObject @ModelAttribute @Valid condition: PassTransactionSearchCondition,
    ): PageResponse<PassTransactionResponse> = memberPassService.getMyTransactions(principal, condition)
}
```
공지는 회원 소유 데이터가 아니라 `@AuthenticationPrincipal` 스코프가 필요 없다 — 다만 `/api/members/**` 하위 배치는 `ROLE_MEMBER` 인가를 그대로 상속하므로 경로만 `/api/members/notices`로 잡고 principal 파라미터는 생략 가능(공지 열람 자체엔 회원 식별이 필요 없음). 존재하지 않는 id 조회 시 404(존재 비노출 원칙, ASVS 섹션 참고)로 응답한다.

---

### `notice/NoticeService.kt` (service, CRUD)

**Analog:** `pass/AdminPassService.kt` — 클래스 레벨 `@Transactional(readOnly = true)` + 쓰기 메서드 오버라이드 관례를 그대로 따른다(패턴은 위 `AttendanceService` 섹션과 동일, 재인용 생략).

---

### `notice/dto/*.kt` (dto, request-response)

**Analog:** `pass/dto/AdjustPassRequest.kt`(요청) + `member/dto/PageResponse.kt`(목록 응답)
```kotlin
@Schema(description = "관리자 수동 가감 요청 — 사유(note) 필수")
data class AdjustPassRequest(
    @field:NotNull
    @field:Digits(integer = 3, fraction = 1)
    val amount: BigDecimal,
    @field:NotBlank
    @field:Size(max = 500)
    val note: String,
)
```
`CreateNoticeRequest`/`UpdateNoticeRequest`는 `@field:NotBlank @field:Size(max = 200) val title`, `@field:NotBlank val content` — **형식만 검증**한다(도메인 규칙 없음, conventions §6).

---

### `notification/NotificationRepository.kt` (확장 — CRUD + bulk-update + Specification)

**Analog:** `pass/PassRepository.kt` (`adjustRemainingCount`)

**벌크 UPDATE + flush/clear 관례** (`PassRepository.kt:45-54`):
```kotlin
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query(
    "update Pass p set p.remainingCount = p.remainingCount + :amount " +
        "where p.id = :id and p.status = com.goldwrestling.pass.PassStatus.ACTIVE " +
        "and p.remainingCount + :amount >= 0",
)
fun adjustRemainingCount(@Param("id") id: Long, @Param("amount") amount: BigDecimal): Int
```
**"모두 읽음" 적용** (RESEARCH Pattern 3, 완성 코드):
```kotlin
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query("update Notification n set n.isRead = true, n.readAt = :now where n.isRead = false")
fun markAllAsRead(@Param("now") now: OffsetDateTime): Int
```
호출부는 벌크 UPDATE **이전에 로드한** 엔티티를 그대로 응답에 쓰지 않는다 — 항상 UPDATE 이후 재조회(Pitfall 5).

`interface NotificationRepository : JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification>` — `PassRepository.kt:18-20`처럼 `JpaSpecificationExecutor`를 함께 상속해 활동 피드 필터 조합을 지원한다.

---

### `notification/AdminNotificationController.kt` (controller, 폴링)

**Analog:** `schedule/AdminScheduleController.kt` — 위 `AdminAttendanceController` 섹션과 동일 골격(권한 애노테이션 없음, `/api/admin/notifications` 하위 `GET`(목록+미확인카운트), `POST /read-all`).

---

### `notification/NotificationQueryService.kt` (service, 조회 + 벌크 커맨드)

**Analog:** `pass/AdminPassService.kt` (클래스 레벨 `readOnly = true`, `markAllAsRead` 호출 메서드만 `@Transactional` 오버라이드).

---

### `notification/NotificationSpecifications.kt` (utility, Specification)

**Analog:** `reservation/ReservationSpecifications.kt` (전문 — `classDateBetween`)
```kotlin
object ReservationSpecifications {
    fun classDateBetween(from: LocalDate?, to: LocalDate?): Specification<Reservation>? {
        if (from == null && to == null) return null
        return Specification { root, _, criteriaBuilder ->
            val predicates = mutableListOf<Predicate>()
            if (from != null) predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("classDate"), from))
            if (to != null) predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("classDate"), to))
            criteriaBuilder.and(*predicates.toTypedArray())
        }
    }

    fun hasClassType(classType: ClassType?): Specification<Reservation>? {
        if (classType == null) return null
        return Specification { root, _, criteriaBuilder ->
            criteriaBuilder.equal(root.get<ClassType>("classType"), classType)
        }
    }
}
```
**활동 피드 적용** (RESEARCH Pattern 4, 완성 코드 — `occurredBetween`/`hasType`) 그대로 이식:
```kotlin
object NotificationSpecifications {
    fun occurredBetween(from: OffsetDateTime?, to: OffsetDateTime?): Specification<Notification>? {
        if (from == null && to == null) return null
        return Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from))
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to))
            cb.and(*predicates.toTypedArray())
        }
    }

    fun hasType(type: NotificationType?): Specification<Notification>? {
        if (type == null) return null
        return Specification { root, _, cb -> cb.equal(root.get<NotificationType>("type"), type) }
    }
}
```
합성은 `member/AdminMemberController` 계열이 쓰는 `Specification.allOf(listOfNotNull(...))`(Spring Data JPA 4.x, `MemberSpecifications.kt:8-11` KDoc 참고) 관례를 따른다.

---

### `pass/TransactionReason.kt` (수정 — `EVENING_HALF_REFUND` 추가)

기존 파일에 `EVENING_HALF`(`TransactionReason.kt:21-22`)가 이미 선언돼 있다 — `CLASS_CANCELED_REFUND`(`TransactionReason.kt:27-28`)와 같은 KDoc 스타일로 한 줄만 추가한다:
```kotlin
/** 저녁반 0.5회 수동 차감 (Phase 6) */
EVENING_HALF,
...
/** 저녁반 출석 삭제로 인한 0.5회 복구 (Phase 6, EVENING_HALF의 반대) */
EVENING_HALF_REFUND,
```
컬럼은 `VARCHAR(30)`에 DB `CHECK` 없음(`PassTransaction.kt:44` `@Column(nullable = false, length = 30)`) — 마이그레이션 변경 불필요, enum 상수 추가만으로 저장 가능(CONTEXT.md 확인 완료).

---

### `batch/InactivityBatchRunner.kt` (수정 — CR-03 배선)

**변경 지점** (`InactivityBatchRunner.kt:152-162`, 현재 코드):
```kotlin
for (memberId in memberIds) {
    try {
        val candidates =
            InactivityDueDateCandidates(
                // Phase 6이 Attendance를 도입하면 여기에 값을 채운다(D-105 후보 ①)
                lastAttendanceDate = null,
                lastActiveReservationClassDate = lastActiveReservationClassDates[memberId],
                returnedFromLeaveDate = returnedFromLeaveDates[memberId],
                lastSessionPassRegistrationDate = lastSessionPassRegistrationDates[memberId],
                lastPositiveAdjustDate = lastPositiveAdjustDates[memberId],
            )
```
**적용할 패턴** — 같은 파일 132-141행의 벌크 조회 관례(`associate { it.getMemberId() to it.getDate() }`)를 그대로 반복해 `if (memberIds.isNotEmpty())` 블록 안에 한 줄 추가:
```kotlin
val lastAttendedDates =
    attendanceRepository.findLastAttendedClassDates(memberIds).associate { it.getMemberId() to it.getDate() }
```
그리고 `lastAttendanceDate = null`을 `lastAttendanceDate = lastAttendedDates[memberId]`로 교체한다. `attendanceRepository`를 생성자 주입에 추가해야 한다(`AttendanceRepository`, `attendance` 패키지 → `batch` 패키지 참조, 기존 `passRepository`/`reservationRepository` 주입과 동일 위치에 추가). **`InactivityDueDateCalculator`/`InactivityDueDateCandidates`는 수정하지 않는다** — 이미 5종 후보 시그니처를 갖추고 있다(Anti-Pattern, RESEARCH 명시).

---

### `db/migration/V11__create_attendance_and_notice.sql` (migration)

**Analog:** `V9__relax_pass_transaction_subject_and_add_batch_execution.sql`(신규 테이블 + CHECK + FK 관례) + `V6__create_schedule_reservation_notification.sql`(notification 테이블 — nullable FK·인덱스 배치 참고)

**신규 테이블 + FK + 인덱스 관례** (`V9` 헤더 주석 스타일 + `notification` 테이블, `V6:115-137`):
```sql
CREATE TABLE notification (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    type VARCHAR(40) NOT NULL,
    reservation_id BIGINT,
    class_session_id BIGINT,
    ...
    CONSTRAINT fk_notification_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (id),
    CONSTRAINT fk_notification_class_session FOREIGN KEY (class_session_id) REFERENCES class_session (id),
    CONSTRAINT ck_notification_target CHECK (reservation_id IS NOT NULL OR class_session_id IS NOT NULL)
);
CREATE INDEX idx_notification_unread ON notification (is_read, occurred_at DESC);  -- Phase 6 폴링 대비
```
**Attendance 테이블은 RESEARCH.md Code Examples 섹션에 골격이 이미 있다**(그대로 이식, SQL 값은 플래너가 확정):
```sql
CREATE TABLE attendance (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    class_session_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    pass_transaction_id BIGINT,
    checked_by_admin_id BIGINT NOT NULL,
    checked_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_attendance_class_session FOREIGN KEY (class_session_id) REFERENCES class_session (id),
    CONSTRAINT fk_attendance_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_attendance_pass_transaction FOREIGN KEY (pass_transaction_id) REFERENCES pass_transaction (id),
    CONSTRAINT fk_attendance_checked_by FOREIGN KEY (checked_by_admin_id) REFERENCES admin (id),
    CONSTRAINT uq_attendance_member_session UNIQUE (class_session_id, member_id)  -- 조건 없는 일반 유니크(Pitfall 2)
);
CREATE INDEX idx_attendance_member ON attendance (member_id, checked_at);  -- CR-03 벌크 조회 대비

CREATE TABLE notice (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    created_by_admin_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_notice_created_by FOREIGN KEY (created_by_admin_id) REFERENCES admin (id)
);
CREATE INDEX idx_notice_created_at ON notice (created_at DESC);
```
**추가 검토 사항(RESEARCH Pitfall 6, 필수 아님)**: 활동 피드가 `is_read` 조건 없이 `occurred_at` 역순 조회를 하므로 `idx_notification_unread (is_read, occurred_at DESC)`가 활용되지 않을 수 있다 — `idx_notification_occurred_at ON notification (occurred_at DESC)` 추가를 플래너가 이 마이그레이션에 포함할지 판단한다.

---

## Shared Patterns

### 조건부 원자 갱신 + 원장 원자 기록 (D-021, CLAUDE.md 규칙 6)
**Source:** `pass/PassRepository.kt:45-54`(`adjustRemainingCount`) + `reservation/ReservationLedgerSupport.kt:99-151`
**Apply to:** `AttendanceService`(저녁반 차감), `NotificationRepository`(모두 읽음)
모든 잔여 변경은 `@Modifying(flushAutomatically = true, clearAutomatically = true)` 조건부 `UPDATE ... WHERE`로 하고, 0행이면 도메인 예외로 변환한다. 실행 직후 준영속 엔티티의 LAZY 필드에 접근하지 않는다 — 항상 재조회 후 다음 단계를 진행한다.

### 관리자 컨트롤러 — 권한 애노테이션 없음 (D-040)
**Source:** `schedule/AdminScheduleController.kt:21-25`, `member/AdminMemberController.kt:22-26`
**Apply to:** `AdminAttendanceController`, `AdminNoticeController`, `AdminNotificationController`
`/api/admin/**`은 `SecurityConfig`가 `hasRole("ADMIN")`으로 전역 처리한다 — 컨트롤러에 `@PreAuthorize` 등을 추가하지 않는다. 컨트롤러는 트랜잭션·try-catch 없이 서비스 호출 1줄로 끝난다(D-017, D-020).

### 트랜잭션 경계 — 서비스 클래스 레벨 readOnly + 쓰기 메서드 오버라이드 (D-020)
**Source:** `pass/AdminPassService.kt:35-45, 52-57`
**Apply to:** `AttendanceService`, `NoticeService`, `NotificationQueryService`
```kotlin
@Service
@Transactional(readOnly = true)
class XxxService(...) {
    @Transactional
    fun writeMethod(...) { ... }
}
```

### 도메인 예외 → `ErrorCode` → `ProblemDetail` (D-017, D-028)
**Source:** `common/error/DomainException.kt`(전문) + `common/error/ErrorCode.kt:74-90` + `pass/PassExceptions.kt:44-48`
**Apply to:** `AttendanceExceptions.kt`, `NoticeExceptions.kt`
새 도메인 예외는 `DomainException`을 상속하고, id 등 식별자를 메시지에 보간하지 않는다(정보 노출 방지). 새 `ErrorCode` 상수는 enum 끝에 추가 + `defaultStatus` 지정 + `docs/error-codes.md` 동시 갱신.

### 페이지네이션 응답 (`PageResponse<T>`)
**Source:** `member/dto/PageResponse.kt`(전문)
**Apply to:** `NoticeSummaryResponse` 목록, `NotificationListResponse`, `ActivityFeedItemResponse` 목록
```kotlin
data class PageResponse<T>(
    val content: List<T>, val page: Int, val size: Int, val totalElements: Long, val totalPages: Int,
) {
    companion object {
        fun <E : Any, T> from(page: Page<E>, mapper: (E) -> T): PageResponse<T> = ...
    }
}
```
컨트롤러가 Spring Data `Page`를 그대로 반환하지 않는다.

### Specification 동적 조건 합성
**Source:** `reservation/ReservationSpecifications.kt`(전문), `member/MemberSpecifications.kt:8-11`
**Apply to:** `NotificationSpecifications`(활동 피드 기간·종류 필터)
조건 없는 필터는 `null`을 반환하고, 호출부가 `Specification.allOf(listOfNotNull(...))`로 합성한다(Spring Data JPA 4.x 정적 팩토리).

### `Clock` 빈 주입 (시각 고정)
**Source:** `reservation/ReservationLedgerSupport.kt:20, 48` (`private val clock: Clock`, `OffsetDateTime.now(clock)`)
**Apply to:** `AttendanceService`(소급 입력·저녁반 차감 시각), `NoticeService`(생성·수정 시각), `NotificationQueryService`(모두 읽음 시각)
`OffsetDateTime.now()`/`LocalDate.now()`를 직접 호출하지 않는다 — 소급 출석 테스트가 이 phase의 핵심 시나리오다(RESEARCH Don't Hand-Roll 표).

### 테스트 픽스처 (`object` + 고정 시각 상수 + 최소 생성 함수)
**Source:** `src/test/kotlin/com/goldwrestling/batch/BatchFixtures.kt:26-51`
**Apply to:** `attendance/AttendanceFixtures.kt`(신규, add-domain-test 스킬 §4 언급)
```kotlin
object BatchFixtures {
    val FIXED_TIME: OffsetDateTime = OffsetDateTime.parse("2026-08-02T10:00:00+09:00")
    val FIXED_TODAY: LocalDate = LocalDate.of(2026, 8, 2)

    fun member(branch: Branch, kakaoId: Long, status: MemberStatus = MemberStatus.ACTIVE): Member = ...
    fun sessionPass(member: Member, branch: Branch, registeredBy: Admin, remainingCount: BigDecimal, endDate: LocalDate, ...): Pass = ...
}
```

## No Analog Found

없음 — 이 phase의 24개 파일 전부 exact 또는 role-match 분석이 존재한다(RESEARCH.md 결론: "이 phase가 새로 발명해야 하는 메커니즘은 사실상 없다").

## Metadata

**Analog search scope:** `src/main/kotlin/com/goldwrestling/{reservation,pass,notification,schedule,member,batch,common}` 전체 + `src/main/resources/db/migration/V*.sql` + `src/test/kotlin/com/goldwrestling/batch/BatchFixtures.kt`
**Files scanned (직접 Read):** `Reservation.kt`, `ReservationLedgerSupport.kt`, `ReservationPassPolicy.kt`, `ReservationRepository.kt`(발췌), `ReservationSpecifications.kt`, `ReservationExceptions.kt`, `Pass.kt`(미열람, 참조만)·`PassRepository.kt`, `PassTransaction.kt`, `TransactionReason.kt`, `PassType.kt`, `PassExceptions.kt`, `AdminPassService.kt`(발췌), `MemberPassController.kt`, `AdjustPassRequest.kt`, `Notification.kt`, `NotificationType.kt`, `NotificationRepository.kt`, `NotificationService.kt`, `AdminScheduleController.kt`, `AdminMemberController.kt`, `MemberSpecifications.kt`, `InactivityBatchRunner.kt`, `PageResponse.kt`, `MemberDateProjection.kt`, `MemberTimestampProjection.kt`, `ErrorCode.kt`, `DomainException.kt`, `V6__create_schedule_reservation_notification.sql`(발췌), `V9__relax_pass_transaction_subject_and_add_batch_execution.sql`, `BatchFixtures.kt`(발췌)
**Pattern extraction date:** 2026-08-18
