# Phase 3: 이용권 - Pattern Map

**Mapped:** 2026-08-03
**Files analyzed:** 27 (신규)
**Analogs found:** 27 / 27 (전부 role-match 이상 — Phase 1·2에 정확한 구조적 선례가 존재)

## File Classification

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|-----------------|----------------|
| `pass/PassType.kt` | model (enum) | — | `member/MemberStatus.kt` | exact |
| `pass/PassStatus.kt` | model (enum) | — | `auth/PrincipalType.kt` | exact |
| `pass/TransactionReason.kt` | model (enum) | — | `member/MemberStatus.kt` | exact |
| `pass/Pass.kt` | model (entity) | CRUD | `member/Member.kt` | role-match |
| `pass/PassTransaction.kt` | model (entity, 원장) | event-driven(append-only) | `auth/RefreshToken.kt` | role-match (동일하게 "주체 nullable FK + 상태 컬럼" 구조) |
| `pass/PassPeriodChange.kt` | model (entity, 이력) | event-driven(append-only) | `auth/RefreshToken.kt` | role-match |
| `pass/PassRepository.kt` | model (repository) | CRUD + 조건부 UPDATE | `auth/RefreshTokenRepository.kt` (조건부 UPDATE) + `member/MemberRepository.kt` (Specification) | exact (조건부 UPDATE), exact (Specification) |
| `pass/PassTransactionRepository.kt` | model (repository) | CRUD + 집계 | `auth/RefreshTokenRepository.kt` | role-match |
| `pass/PassPeriodChangeRepository.kt` | model (repository) | CRUD | `member/MemberRepository.kt` | role-match |
| `pass/PassSpecifications.kt` | utility (query spec) | request-response | `member/MemberSpecifications.kt` | exact |
| `pass/PassExceptions.kt` | model (도메인 예외) | — | `member/MemberExceptions.kt` | exact |
| `pass/AdminPassController.kt` | controller | request-response | `member/AdminMemberController.kt` | exact |
| `pass/AdminPassService.kt` | service | CRUD | `member/AdminMemberService.kt` | exact |
| `pass/MemberPassController.kt` | controller | request-response | `member/MemberProfileController.kt` | exact |
| `pass/MemberPassService.kt` | service | request-response | `member/MemberProfileService.kt` | exact |
| `pass/dto/RegisterPassRequest.kt` | model (request DTO) | request-response | `member/dto/RejectMemberRequest.kt` | exact |
| `pass/dto/AdjustPassRequest.kt` | model (request DTO) | request-response | `member/dto/RejectMemberRequest.kt` | exact |
| `pass/dto/ChangePassPeriodRequest.kt` | model (request DTO) | request-response | `member/dto/UpdateMemberStatusRequest.kt` | exact |
| `pass/dto/CancelPassRequest.kt` | model (request DTO) | request-response | `member/dto/RejectMemberRequest.kt` | exact |
| `pass/dto/PassResponse.kt` | model (response DTO) | request-response | `member/dto/MemberDetailResponse.kt` | exact |
| `pass/dto/PassTransactionResponse.kt` | model (response DTO) | request-response | `member/dto/MemberSummaryResponse.kt` | exact |
| `pass/dto/PassTransactionSearchCondition.kt` | model (query DTO) | request-response | `member/dto/MemberSearchCondition.kt` | exact |
| `pass/PassAdjustmentPolicyTest.kt` | test (순수 단위) | — | `member/MemberOnboardingStatusTest.kt` | exact |
| `pass/PassRegistrationTest.kt` | test (순수 단위) | — | `member/MemberOnboardingStatusTest.kt` | exact |
| `pass/PassLedgerInvariantTest.kt` | test (통합, 불변식) | — | `auth/RefreshTokenRotationTest.kt` 계열 (미열람, 목록상 존재) + `member/MemberSearchTest.kt` 골격 | role-match |
| `pass/AdminPassControllerTest.kt` | test (통합, MockMvc) | — | `member/MemberSearchTest.kt` | exact |
| `pass/MemberPassControllerTest.kt` / `MemberPassTransactionControllerTest.kt` | test (통합, MockMvc) | — | `member/MemberSearchTest.kt` (페이지네이션 부분) | exact |
| `db/migration/V4__create_pass_tables.sql` | migration | batch(DDL) | `db/migration/V3__add_auth_credentials_and_refresh_token.sql` | exact |

## Pattern Assignments

### `pass/PassType.kt`, `pass/PassStatus.kt`, `pass/TransactionReason.kt` (model, enum)

**Analog:** `src/main/kotlin/com/goldwrestling/member/MemberStatus.kt` (전체, 6-18줄), `src/main/kotlin/com/goldwrestling/auth/PrincipalType.kt` (전체)

```kotlin
// member/MemberStatus.kt — KDoc에 정책 문서 §번호를 걸고, 각 상수에 한 줄 한글 주석
enum class MemberStatus {
    /** 승인대기 — 카카오 가입 직후. 승인 대기 화면만 노출 */
    PENDING,
    ...
}
```

- glossary.md에 이미 있는 이름(`PassType` EVENING_MEMBERSHIP/SESSION_PASS/LESSON_PASS, `TransactionReason` 8종)을 그대로 옮긴다.
- `PassStatus`는 신규 개념이므로 **glossary.md 추가가 선행**되어야 한다(CLAUDE.md 규칙 3, RESEARCH A항목과 무관하게 필수).
- 각 enum 상수마다 `/** 한 줄 설명(어느 정책 조항 근거) */` KDoc을 붙이는 관례를 유지한다.

---

### `pass/Pass.kt` (model, entity)

**Analog:** `src/main/kotlin/com/goldwrestling/member/Member.kt` (전체, 1-61줄)

**Imports/구조 패턴** (1-15줄):
```kotlin
@Entity
@Table(name = "member")
class Member(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    val branch: Branch,
    @Column(length = 50)
    var name: String?,
    ...
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
    ...
}
```

**핵심 규칙 (반드시 준수):**
- `data class` 금지, 생성자 파라미터 = DB 컬럼, `@Id`는 생성자 밖에 `val id: Long? = null`
- `@ManyToOne(fetch = FetchType.LAZY)` 명시 — `Pass.member`
- `@Enumerated(EnumType.STRING)` 필수 — `Pass.type`, `Pass.status`
- **순수 도메인 판정 메서드를 엔티티에 둔다** — `Member.isOnboardingCompleted()`/`isRejected()`처럼, `Pass.validateAdjustment(amount)`도 스프링 없이 단위테스트 가능해야 한다(RESEARCH Pattern 1).
- `createdAt`은 `Clock` 주입 서비스가 채운다 — JPA Auditing 미도입(D-039와 동일 원칙).

---

### `pass/PassTransaction.kt`, `pass/PassPeriodChange.kt` (model, 원장/이력 엔티티)

**Analog:** `src/main/kotlin/com/goldwrestling/auth/RefreshToken.kt` (전체, 1-79줄)

**핵심 패턴 — "주체 컬럼" 대신 "대상 Pass FK + admin_id NOT NULL"**:
```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "member_id")
val member: Member?,
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "admin_id")
val admin: Admin?,
```
`RefreshToken`은 "member 또는 admin 중 정확히 하나"를 표현하지만, **PassTransaction/PassPeriodChange는 이 패턴을 그대로 복사하지 않는다** — RESEARCH Pitfall 5가 명시한 대로 이번 phase의 사유 코드는 전부 관리자 주체이므로 `admin: Admin`(non-null), `pass: Pass`(non-null) 두 개의 `@ManyToOne(fetch = LAZY)` FK만 두면 충분하다. "정확히 하나만 채워짐" CHECK는 **이번 phase에 도입하지 않는다** — Phase 4가 `member_id` nullable 컬럼을 추가할 때 그 CHECK를 새로 붙인다.

**멱등/불변 이력 메서드가 필요 없는 이유:** `RefreshToken.revoke()`처럼 상태를 바꾸는 메서드가 있는 게 아니라, `PassTransaction`/`PassPeriodChange`는 **append-only**다 — 생성자 파라미터만 있고 setter가 없는 편이 이력 불변성을 코드로 보장한다(참고: `RefreshToken`도 `tokenHash`/`expiresAt`/`createdAt`은 `val`, `revokedAt`만 `var`).

---

### `pass/PassRepository.kt` — 조건부 UPDATE (D-021의 핵심 재사용 자산)

**Analog:** `src/main/kotlin/com/goldwrestling/auth/RefreshTokenRepository.kt` 31-37줄, `src/main/kotlin/com/goldwrestling/auth/TokenService.kt` 106-152줄(호출부·LAZY 함정 회피)

```kotlin
// RefreshTokenRepository.kt 31-36줄 — 그대로 본뜬다
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query("update RefreshToken rt set rt.revokedAt = :now where rt.id = :id and rt.revokedAt is null")
fun revokeIfUsable(
    @Param("id") id: Long,
    @Param("now") now: OffsetDateTime,
): Int
```

`Pass.adjustRemainingCount`는 이 구조를 그대로 옮긴다(RESEARCH Pattern 1 예시):
```kotlin
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query(
    "update Pass p set p.remainingCount = p.remainingCount + :amount " +
        "where p.id = :id and p.remainingCount + :amount >= 0",
)
fun adjustRemainingCount(
    @Param("id") id: Long,
    @Param("amount") amount: BigDecimal,
): Int
```

**호출부에서 반드시 지킬 함정 회피 (`TokenService.rotate` 113-120줄 그대로 재현):**
```kotlin
// 벌크 UPDATE(clearAutomatically = true)는 실행되는 즉시 영속성 컨텍스트를 비운다.
// 그 이후 existing은 준영속 상태가 되어 LAZY 연관에 접근하면 LazyInitializationException이 난다
// — 그 전에 필요한 값을 전부 지역 변수로 옮겨 둔다.
val id = existing.id!!
val principalType = existing.principalType()
...
```
`AdminPassService.adjust()`도 조건부 UPDATE 호출 **전에** `pass.member.id`, `pass.type` 등 이후 로직·응답 변환에 필요한 스칼라 값을 지역 변수로 꺼내둔다(RESEARCH Pitfall 4).

**반환값 0 처리 관례** (`TokenService.rotate` 140-148줄):
```kotlin
if (refreshTokenRepository.revokeIfUsable(id, now) == 0) {
    // 경쟁 패배 → 재조회 후 정확한 도메인 예외로 변환
    ...
}
```
`PassRepository.adjustRemainingCount(...) == 0`도 같은 방식으로 재조회해 `InsufficientPassCountException` 등 정확한 예외로 변환한다.

**Specification 조회 부분** — `PassRepository`도 `MemberRepository`처럼 `JpaSpecificationExecutor<Pass>`를 함께 상속한다:
```kotlin
// member/MemberRepository.kt 전체
interface MemberRepository :
    JpaRepository<Member, Long>,
    JpaSpecificationExecutor<Member> {
    fun findByKakaoId(kakaoId: Long): Member?
}
```

---

### `pass/PassSpecifications.kt`

**Analog:** `src/main/kotlin/com/goldwrestling/member/MemberSpecifications.kt` (전체, 1-111줄)

핵심 재사용 포인트:
- `Specification.allOf(listOfNotNull(...))` 정적 팩토리 (12-42줄, verify-boot4-api로 이미 검증됨 — Spring Data JPA 4.1.0)
- 조건이 없으면 `null` 반환하는 함수 시그니처 관례: `fun hasStatus(status: PassStatus?): Specification<Pass>?`
- `object PassSpecifications { ... }` — 싱글턴 object, 인스턴스화하지 않음

```kotlin
// MemberSpecifications.kt 64-70줄 — 상태 필터 패턴 그대로
fun hasStatus(status: MemberStatus?): Specification<Member>? {
    if (status == null) return null
    return Specification { root, _, criteriaBuilder ->
        criteriaBuilder.equal(root.get<MemberStatus>("status"), status)
    }
}
```
`PassSpecifications`는 `belongsToMember(memberId)`(회원 스코프, IDOR 방지 핵심), `hasType(type)`, `hasStatus(status)`, `hasPassId(passId)`(이력 필터 D-058) 함수로 이 패턴을 반복한다.

---

### `pass/PassExceptions.kt`

**Analog:** `src/main/kotlin/com/goldwrestling/member/MemberExceptions.kt` (전체, 1-49줄)

```kotlin
package com.goldwrestling.member

import com.goldwrestling.common.error.DomainException
import com.goldwrestling.common.error.ErrorCode

@Suppress("UNUSED_PARAMETER")
class MemberNotFoundException(
    memberId: Long?,
) : DomainException(
        ErrorCode.MEMBER_NOT_FOUND,
        "회원을 찾을 수 없습니다.",
    )

class MemberStateConflictException(
    message: String,
) : DomainException(ErrorCode.MEMBER_STATE_CONFLICT, message)
```

**적용 규칙:**
- 모든 예외가 `DomainException(errorCode, message)`를 상속하고, `message`는 **사용자 대면 문구**(내부 식별자·SQL 미포함)
- 여러 유사 실패 상황을 하나의 예외(+ 호출부가 넘기는 message)로 묶는 관례(`MemberStateConflictException` 참고) — `PassStateConflictException` 후보 (취소된 이용권 조작 등 여러 케이스 재사용)
- `PassNotFoundException`, `PassTypeNotAdjustableException`, `InvalidAdjustmentUnitException`, `InsufficientPassCountException`, `PassAlreadyCanceledException` 등은 각각 `ErrorCode` 신규 항목과 1:1 대응해야 한다 — 아래 Shared Patterns 참고.

---

### `pass/AdminPassController.kt` / `pass/MemberPassController.kt`

**Analog:** `src/main/kotlin/com/goldwrestling/member/AdminMemberController.kt` (전체) / `src/main/kotlin/com/goldwrestling/member/MemberProfileController.kt` (전체)

**공통 규칙 (컨트롤러는 얇게):**
```kotlin
// AdminMemberController.kt 22-30줄 — 이 프로젝트 컨트롤러의 표준 주석 형태
/**
 * `SecurityConfig`에서 `/api/admin` 하위 전체가 `hasRole("ADMIN")` 전용이므로,
 * **이 컨트롤러에는 별도 권한 애노테이션을 붙이지 않는다**(D-040).
 * 트랜잭션 애노테이션·`try-catch`를 붙이지 않는다 — 트랜잭션 경계는 서비스(D-020),
 * 에러 응답은 `GlobalExceptionHandler`가 담당한다(D-017).
 */
```

**`@ParameterObject` 쿼리 DTO 패턴** (AdminMemberController.kt 37-48줄, D-054 — 반드시 준수, 빠뜨리면 openapi.yaml 파라미터 바인딩이 깨짐 WR-06):
```kotlin
@GetMapping
fun search(
    @ParameterObject @ModelAttribute @Valid condition: MemberSearchCondition,
): PageResponse<MemberSummaryResponse> = adminMemberService.search(condition)
```
→ `MemberPassTransactionController`(또는 `MemberPassController`의 이력 조회 메서드)의 `PassTransactionSearchCondition` 바인딩에 그대로 적용.

**회원 본인 스코프 — memberId를 절대 경로/바디로 받지 않는다** (MemberProfileController.kt 20-27줄, IDOR 방지):
```kotlin
/**
 * **경로에 `memberId`를 받지 않는다.** 항상 `@AuthenticationPrincipal`로 주입된 토큰 주체 본인의
 * 정보만 다룬다 — 다른 회원의 프로필을 조회·수정하는 경로를 아예 만들지 않는 것이 IDOR을 막는 방법이다.
 */
@GetMapping("/me")
fun getMyProfile(
    @AuthenticationPrincipal principal: AuthenticatedPrincipal,
): MyProfileResponse = memberProfileService.getMyProfile(principal)
```
→ `GET /api/members/me/passes`, `GET /api/members/me/pass-transactions` 모두 이 형태.

---

### `pass/AdminPassService.kt`

**Analog:** `src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt` (전체, 1-142줄)

**클래스 헤더 표준:**
```kotlin
@Service
@Transactional(readOnly = true)
class AdminMemberService(
    private val memberRepository: MemberRepository,
    private val tokenService: TokenService,
) { ... }
```
→ `AdminPassService`도 `@Transactional(readOnly = true)` 기본, 변경 메서드(`register`/`adjust`/`changePeriod`/`cancel`)만 `@Transactional` 오버라이드(35줄 `search()`는 readOnly, 67줄 `approve()`는 `@Transactional`).

**"조회 → 도메인 규칙 검사(서버 강제) → 상태 변경 → DTO 변환(트랜잭션 안)" 순서** (AdminMemberService.kt 67-81줄):
```kotlin
@Transactional
fun approve(memberId: Long): MemberDetailResponse {
    val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
    if (member.status != MemberStatus.PENDING) {
        throw MemberStateConflictException("승인 대기 상태의 회원만 승인할 수 있습니다.")
    }
    if (!member.isOnboardingCompleted()) {
        throw MemberStateConflictException("회원이 이름·전화번호를 등록해야 승인할 수 있습니다.")
    }
    member.status = MemberStatus.ACTIVE
    ...
    return MemberDetailResponse.from(member)
}
```
→ `AdminPassService.adjust()`는: `pass` 조회 → `pass.validateAdjustment(amount)`(순수 판정, RESEARCH Pattern 1) → `passRepository.adjustRemainingCount(id, amount)`(조건부 UPDATE) → 반환 0이면 재조회 후 `InsufficientPassCountException` → 같은 트랜잭션에서 `PassTransaction(ADMIN_ADJUST)` 저장(CLAUDE.md 규칙 6 "이력 없는 변경 금지") → 재조회한 `Pass`로 `PassResponse.from(pass)` 변환.

**같은 트랜잭션에 여러 리포지토리 호출을 묶는 패턴** (AdminMemberService.kt 90-103줄, `reject()`):
```kotlin
@Transactional
fun reject(memberId: Long, reason: String): MemberDetailResponse {
    val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
    ...
    member.status = MemberStatus.INACTIVE
    member.rejectionReason = reason.trim()
    tokenService.revokeAllForMember(memberId)  // 같은 트랜잭션에 참여 (REQUIRED 전파)
    return MemberDetailResponse.from(member)
}
```
→ `cancel()`은 `pass.status = CANCELED` + (횟수제만) `passTransactionRepository.save(REGISTRATION_CANCELED 이력)`을 같은 트랜잭션에서 함께 처리.

---

### `pass/MemberPassService.kt`

**Analog:** `src/main/kotlin/com/goldwrestling/member/MemberProfileService.kt` (전체, 1-63줄)

```kotlin
@Service
@Transactional(readOnly = true)
class MemberProfileService(
    private val memberRepository: MemberRepository,
    private val memberStateGate: MemberStateGate,
) {
    fun getMyProfile(principal: AuthenticatedPrincipal): MyProfileResponse {
        val memberId = principal.requireMemberId()
        val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        return MyProfileResponse.from(member)
    }
}
```
→ `MemberPassService.getMyPasses(principal)`는 `principal.requireMemberId()`로만 회원 id를 얻고(경로 파라미터 절대 사용 금지), `PassSpecifications.belongsToMember(memberId)` + `hasStatus(CANCELED 제외 조건)`으로 조회한다 — D-058 "취소된 이용권만 숨김" 요구를 여기서 구현.

---

### 요청/응답 DTO 4종

**Analog (요청 DTO 형식검증만):** `src/main/kotlin/com/goldwrestling/member/dto/RejectMemberRequest.kt`(전체), `UpdateMemberStatusRequest.kt`(전체)

```kotlin
@Schema(description = "거절 사유. 관리자 전용 정보이며 회원에게 원문이 노출되지 않는다 (D-043)")
data class RejectMemberRequest(
    @field:NotBlank
    @field:Size(max = 500)
    @field:Schema(description = "거절 사유(관리자 전용, 500자 이내)", example = "...")
    val reason: String,
)
```
→ `AdjustPassRequest(amount: BigDecimal, reason: String)`는 `@field:NotBlank`(reason) + 금액 형식은 `@Digits`/`@DecimalMin` 등으로 형식만 검증하고, "0.5 단위인지"·"기간제 제외" 같은 **도메인 규칙 검증은 DTO에 넣지 않는다**(conventions §6, Pitfall 1) — `Pass.validateAdjustment()`가 담당.

**Analog (응답 DTO — companion object `from()` + 트랜잭션 안 변환):** `src/main/kotlin/com/goldwrestling/member/dto/MemberDetailResponse.kt`(전체)

```kotlin
data class MemberDetailResponse(
    @field:Schema(description = "회원 ID") val memberId: Long,
    ...
    @field:Schema(description = "소속 지점명") val branchName: String,
    ...
) {
    companion object {
        fun from(member: Member): MemberDetailResponse =
            MemberDetailResponse(
                memberId = requireNotNull(member.id) { "저장되지 않은 Member는 응답으로 변환할 수 없습니다." },
                ...
                branchName = member.branch.name,  // LAZY 연관 — 트랜잭션 안에서만 안전
            )
    }
}
```
→ `PassResponse.from(pass, today)`는 **상태(만료/소진) 계산을 여기서 한다**(RESEARCH "상태는 저장하지 않고 조회 시점 계산") — `today`를 파라미터로 받아 `Clock` 의존을 서비스에서 주입받게 한다(add-domain-test §3 "Clock을 주입받고 테스트에서 고정").

**Analog (페이지 조회 목록 항목 — 요약 필드만):** `src/main/kotlin/com/goldwrestling/member/dto/MemberSummaryResponse.kt`(전체) → `PassTransactionResponse`가 이 형태(목록용, 민감정보 최소화)를 따른다.

---

### `pass/dto/PassTransactionSearchCondition.kt`

**Analog:** `src/main/kotlin/com/goldwrestling/member/dto/MemberSearchCondition.kt`(전체)

```kotlin
data class MemberSearchCondition(
    val keyword: String? = null,
    val status: MemberStatus? = null,
    val onboardingCompleted: Boolean? = null,
    @field:Min(0) val page: Int = 0,
    @field:Min(1) @field:Max(100) val size: Int = 20,
)
```
→ `PassTransactionSearchCondition(passId: Long? = null, page: Int = 0, size: Int = 20)` — D-058 "이용권별 필터 + page/size", `PageResponse<T>` 재사용(아래 Shared Patterns).

---

### `db/migration/V4__create_pass_tables.sql`

**Analog:** `src/main/resources/db/migration/V3__add_auth_credentials_and_refresh_token.sql`(전체, 특히 33-51줄), `V2__create_branch_member_admin.sql`(전체)

**관례 (add-migration/SKILL.md와 정확히 일치):**
- 파일명: `V<다음번호>__<소문자_스네이크_설명>.sql`, 현재 최신은 V3 → **V4부터**(Pitfall 6, 병렬 작업 있으면 재확인)
- PK: `id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`
- FK 명시적 이름: `CONSTRAINT fk_refresh_token_member FOREIGN KEY (member_id) REFERENCES member (id)`
- enum 저장: `VARCHAR(20) NOT NULL`
- CHECK로 nullability 조합 강제 (V3 46줄 `ck_refresh_token_principal`이 "정확히 하나" 패턴의 원형이지만, **Pass 쪽은 이 패턴을 복사하지 않는다** — RESEARCH Pitfall 5. 대신 RESEARCH Architecture Patterns 3의 `ck_pass_remaining_count_by_type`처럼 **"타입값에 따른 컬럼 nullable 조합"** CHECK를 쓴다):
```sql
-- V3 46줄 패턴 (참고용 — Pass에는 이 형태로 쓰지 않음)
CONSTRAINT ck_refresh_token_principal CHECK ((member_id IS NULL) <> (admin_id IS NULL))

-- Pass 쪽 실제 적용 형태 (RESEARCH Architecture Patterns 3)
CONSTRAINT ck_pass_remaining_count_by_type CHECK (
    (type = 'EVENING_MEMBERSHIP' AND remaining_count IS NULL) OR
    (type IN ('SESSION_PASS', 'LESSON_PASS') AND remaining_count IS NOT NULL)
)
```
- 인덱스: 조회 패턴에 맞춰(V3 49-51줄처럼) `idx_pass_member`, `idx_pass_transaction_pass`(이력 필터 D-058이 이 인덱스를 탄다) 추가
- 마이그레이션 주석은 어떤 결정(D-번호)에 근거했는지 한글로 남기는 관례를 유지한다(V2 1-3줄, V3 1-4줄 참고)

---

### 테스트 3종 골격

**순수 단위테스트 — Analog:** `src/test/kotlin/com/goldwrestling/member/MemberOnboardingStatusTest.kt`(전체, 1-94줄)

```kotlin
class MemberOnboardingStatusTest {
    @Test
    fun `이름과 전화번호가 둘 다 있으면 온보딩 완료로 판정한다`() {
        val member = member(name = "홍길동", phoneNumber = "01012345678", status = MemberStatus.PENDING)
        assertThat(member.isOnboardingCompleted()).isTrue()
    }
    ...
    private fun member(...): Member = Member(...)
    companion object {
        private val FIXED_TIME: OffsetDateTime = OffsetDateTime.parse("2026-08-01T00:00:00+09:00")
    }
}
```
→ `PassAdjustmentPolicyTest`/`PassRegistrationTest`가 이 골격(백틱 한글 서술문 메서드명, 하단 픽스처 팩토리 함수, `FIXED_TIME` 상수) 그대로 재사용. **스프링 컨텍스트 기동 없음.**

**통합테스트(MockMvc + Testcontainers) — Analog:** `src/test/kotlin/com/goldwrestling/member/MemberSearchTest.kt`(전체, 1-348줄)

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class MemberSearchTest {
    @Autowired private lateinit var mockMvc: MockMvc
    ...
    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @Test
    fun `관리자 토큰으로 GET api-admin-members 를 호출하면 200과 페이지 응답이 온다`() {
        persistMember(...)
        val token = adminAccessToken(loginId = "admin-list-ok")
        mockMvc
            .perform(get("/api/admin/members").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
    }
    ...
    private fun adminAccessToken(loginId: String): String {
        val admin = persistAdmin(loginId)
        return tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
    }
    private fun memberAccessToken(member: Member): String = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken
}
```
`AdminPassControllerTest`/`MemberPassControllerTest`/`MemberPassTransactionControllerTest`가 이 애노테이션 조합(`@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)`, **정확히 동일하게 유지 — 다르면 스프링 컨텍스트가 새로 뜬다**), `adminAccessToken()`/`memberAccessToken()` 헬퍼, `persistMember()`류 픽스처 헬퍼, 인가(401/403)·페이지네이션(size/page/totalElements)·검증실패(400 VALIDATION_FAILED) 테스트 섹션 구성을 그대로 재사용.

**동시성 방지 필요 없음(이번 phase):** CONTEXT.md D-021 discretion에 따라 "관리자 단독 조작이라 경쟁 희박" — `add-domain-test/SKILL.md` §4 동시성 테스트 골격은 이번 phase에는 적용하지 않는다(Phase 4가 예약 경합에서 재사용).

---

## Shared Patterns

### 에러 응답 (RFC 9457 ProblemDetail, D-017)
**Source:** `src/main/kotlin/com/goldwrestling/common/error/DomainException.kt`(전체), `ErrorCode.kt`(전체), `GlobalExceptionHandler.kt`(전체)
**Apply to:** 모든 `pass/*Exceptions.kt`, 모든 컨트롤러/서비스

```kotlin
// DomainException.kt — 새 예외는 전부 이걸 상속하기만 하면 된다
abstract class DomainException(
    val errorCode: ErrorCode,
    message: String,
    val status: HttpStatus = errorCode.defaultStatus,
) : RuntimeException(message)
```
```kotlin
// ErrorCode.kt — 신규 코드 추가 위치(59-69줄 인증·회원 10개 다음)
/** 대상 회원 없음 */
MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND),
```
**신규로 추가해야 할 ErrorCode 후보** (CONTEXT.md Claude's Discretion, docs/error-codes.md 동시 갱신 필수):
`PASS_NOT_FOUND`(404), `INSUFFICIENT_PASS_COUNT`(409 또는 422 — 기존 코드베이스는 상태 충돌에 409 사용), `INVALID_ADJUSTMENT_UNIT`(400), `PASS_TYPE_NOT_ADJUSTABLE`(409), `PASS_ALREADY_CANCELED`(409). `GlobalExceptionHandler`는 **수정 불필요** — `DomainException` 상속만으로 자동 통합된다(37-49줄 `handleDomainException`).

### 페이지네이션 응답
**Source:** `src/main/kotlin/com/goldwrestling/member/dto/PageResponse.kt`(전체)
**Apply to:** `MemberPassTransactionController`(이력 조회, D-058)

```kotlin
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <E : Any, T> from(page: Page<E>, mapper: (E) -> T): PageResponse<T> = ...
    }
}
```
`member/dto`에서 그대로 import해 재사용한다(RESEARCH가 지적한 대로 "두 번째 사용처"가 됐으니 `common/dto`로 승격을 계획 단계에서 검토할 가치는 있으나, 이번 phase의 필수 작업은 아님).

### `@ParameterObject` 쿼리 DTO (D-054)
**Source:** `src/main/kotlin/com/goldwrestling/member/AdminMemberController.kt` 37-48줄
**Apply to:** `AdminPassController.search`(있다면), `MemberPassController`의 이력 조회 메서드

```kotlin
@GetMapping
fun search(
    @ParameterObject @ModelAttribute @Valid condition: MemberSearchCondition,
): PageResponse<MemberSummaryResponse> = adminMemberService.search(condition)
```
빠뜨리면 openapi.yaml이 객체 파라미터 1개로 생성되어 FE 바인딩이 깨진다(실제 재현된 버그, WR-06) — **이 phase의 모든 페이지네이션+필터 GET 엔드포인트에 예외 없이 적용**.

### 조건부 UPDATE 동시성 방어 (D-021)
**Source:** `src/main/kotlin/com/goldwrestling/auth/RefreshTokenRepository.kt` 31-52줄, 호출부 `src/main/kotlin/com/goldwrestling/auth/TokenService.kt` 106-152줄
**Apply to:** `PassRepository.adjustRemainingCount`(잔여 원자적 반영) — Phase 4가 그대로 재사용할 경로이므로 이번 phase부터 이 형태로 설계.

### 회원 본인 스코프 (IDOR 방지)
**Source:** `src/main/kotlin/com/goldwrestling/member/MemberProfileController.kt` 20-27줄, `src/main/kotlin/com/goldwrestling/auth/AuthenticatedPrincipal.kt` 30-35줄
**Apply to:** `MemberPassController`의 모든 메서드

```kotlin
fun requireMemberId(): Long {
    check(principalType == PrincipalType.MEMBER) { "회원 전용 경로에 관리자 주체가 들어왔습니다." }
    return principalId
}
```
경로/쿼리에 `memberId`를 받지 않고 항상 `principal.requireMemberId()`로만 얻는다.

### `Clock` 주입 (시각 고정 테스트)
**Source:** `MemberSearchTest.kt` 63-66줄(`MutableTestClock`), `TokenService.kt` 전역(모든 `OffsetDateTime.now(clock)` 호출)
**Apply to:** `Pass` 만료·유효기간 판정, `PassResponse.from(pass, clock)`, 등록 시 `startDate` 기본값 계산

---

## No Analog Found

없음 — 이번 phase의 27개 파일 전부 Phase 1·2에 구조적으로 대응하는 선례가 있다(신규 개념은 `PassStatus`뿐이며, 이는 기존 enum 관례를 그대로 따르면 됨).

## Metadata

**Analog search scope:** `src/main/kotlin/com/goldwrestling/{member,auth,admin,common,config}/**`, `src/test/kotlin/com/goldwrestling/**`, `src/main/resources/db/migration/**`
**Files scanned:** 약 45개 (member 21, auth 20, 마이그레이션 3, 공통 test 인프라 4)
**Pattern extraction date:** 2026-08-03
