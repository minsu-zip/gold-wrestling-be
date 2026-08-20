# Phase 3: 이용권 - Research

**Researched:** 2026-08-03
**Domain:** JPA 도메인 모델링(다형 엔티티) + 원장(ledger) 불변식 + Kotlin TDD (Spring Boot 4.1 / Hibernate)
**Confidence:** HIGH (기존 코드베이스 패턴 재사용 비중이 커서 대부분 실측 가능) / 일부 모델링 결정은 MEDIUM(신규 설계, 아래 Assumptions Log 참조)

## Summary

Phase 3은 이 프로젝트에서 **처음으로 "다형적(polymorphic) 도메인"과 "원장(ledger) 불변식"을 함께 다루는 phase**다. 저녁반 회비(기간제)와 예약제 횟수권/1:1 레슨권(횟수제)이라는 서로 다른 두 가지 이용권 형태를 하나의 상위 개념(`Pass`)으로 묶어야 하고, 모든 수량 변경은 `PassTransaction` 이력과 정확히 합이 맞아야 한다("잔여 = 이력 합계"). 기존 Phase 1·2 코드베이스는 지금까지 상속·다형성을 쓴 적이 없고(모든 엔티티가 flat), `refresh_token`에서 이미 "주체 중 정확히 하나만 채워진다"는 CHECK 제약 패턴(D-037)과 "조건부 UPDATE로 경쟁을 막는" 패턴(D-021, `RefreshTokenRepository.revokeIfUsable`)을 실전 검증해 둔 상태다 — 이 두 패턴이 Phase 3 설계의 가장 강력한 재사용 자산이다.

조사 결과 권장 설계는: **JPA `@Inheritance` 없이 단일 엔티티 `Pass`(테이블 `pass`) + `PassType` 판별 컬럼**을 쓰고, 기간제/횟수제 공통으로 `startDate`/`endDate`(유효 구간)를 두고 횟수제에만 `remainingCount`를 추가하는 구조다. Hibernate 공식 문서도 SINGLE_TABLE 상속이 "서브클래스가 추가 속성이 적을 때" 적합하다고 명시하는데, 이 조건은 두 방식(진짜 상속 vs 판별 컬럼) 모두 충족한다 — 이 프로젝트는 지금까지 프레임워크 기능을 필요 이상으로 쓰지 않는 쪽을 일관되게 선택해 왔다(D-024 detekt 보류, D-029 플러그인 대신 커스텀 태스크, D-057 Envers 기각). 실제 이유(엔티티 상속을 이 프로젝트에서 처음 도입하는 리스크, `PassTransaction`/`PassPeriodChange`가 어차피 하나의 물리 테이블을 FK로 참조하므로 상속의 이점이 "타입 안전성" 정도로 작음)를 근거로 판별 컬럼 방식을 권장한다.

차감·수동 가감 정책의 TDD 설계는 **2계층 분리**를 권장한다: (1) `Pass` 엔티티 메서드가 순수 Kotlin으로 "이 가감이 허용되는가"를 판정(스프링 없이 단위테스트, `MemberOnboardingStatusTest` 패턴 그대로 재사용), (2) 실제 반영은 리포지토리의 조건부 `UPDATE ... WHERE remaining_count + :amount >= 0` 벌크 쿼리로 원자적으로 수행(Testcontainers 통합테스트, `RefreshTokenRepository.revokeIfUsable` 패턴 그대로 재사용). CONTEXT.md가 명시한 대로 이번 phase는 관리자 단독 조작이라 경쟁이 희박하지만, Phase 4(예약 차감)가 이 경로를 그대로 재사용하므로 처음부터 DB 조건부 갱신으로 설계해야 한다.

**Primary recommendation:** `pass` 단일 테이블(판별 컬럼 `type`) + `pass_transaction`(±수량 원장, admin_id NOT NULL) + `pass_period_change`(기간 변경 이력) 3테이블로 V4 마이그레이션을 작성하고, 도메인 규칙은 `Pass` 엔티티 메서드로 내려 순수 단위테스트로, 실제 반영은 조건부 UPDATE 리포지토리 메서드로 Testcontainers 통합테스트한다.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| 이용권 등록/조정 도메인 규칙(0.5 단위, 음수 거부, 기간제 제외 등) | API/Backend (엔티티 메서드) | — | 정책이 서버에서만 강제되어야 함(D-052 선례와 동일 원칙) — DTO·FE는 형식 검증만 |
| 잔여 횟수 원자적 반영(동시성 방어) | Database/Storage (조건부 UPDATE + CHECK 제약) | API/Backend (리포지토리 호출) | 조회-판단-저장 사이 경쟁은 애플리케이션 조건문으로 막을 수 없음(D-021) |
| `PassTransaction`/`PassPeriodChange` 이력 기록 | API/Backend (서비스, 같은 트랜잭션) | Database/Storage (FK+CHECK로 무결성 방어) | "이력 없는 잔여 변경 불가"는 트랜잭션 원자성으로만 보장 가능 |
| 본인 이용권/이력 조회(PASS-05·06) | API/Backend (Specification 조회) | — | `MemberStateGate`·`AuthenticatedPrincipal` 기반 본인 스코프, FE는 렌더링만 |
| 상태(만료/소진) 표시 | API/Backend (응답 DTO 변환 시점 계산) | — | DB에는 `ACTIVE`/`CANCELED`만 저장, 만료·소진은 저장하지 않고 조회 시점 계산(저장 상태와 실제가 갈라지는 배치 지연 문제 원천 차단) |
| 관리자/회원 인가 구분 | API/Backend (`SecurityConfig` URL 규칙, 변경 불필요) | — | `/api/admin/**`·`/api/members/**` 규칙이 이미 존재(D-040) — 새 경로도 이 프리픽스 안에 들어가면 별도 설정 불필요 |

## Standard Stack

### Core

이번 phase는 **신규 외부 의존성을 추가하지 않는다.** 기존 스택(Spring Boot 4.1.0, Spring Data JPA, Bean Validation, springdoc 3.0.3, Kotlin 2.3.21)만으로 구현 가능하다.

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Data JPA | Boot 4.1.0 BOM 관리 | `Pass`/`PassTransaction`/`PassPeriodChange` 엔티티·리포지토리 | 이미 `member`/`auth`에서 검증된 스택 |
| `JpaSpecificationExecutor` | 위와 동일 | PASS-05/06 조회 조건 조합(회원 스코프, 상태·이용권종류 필터) | `MemberSpecifications` 패턴 그대로 재사용 가능 [VERIFIED: 코드베이스 실측] |
| Bean Validation (`jakarta.validation`) | Boot 4.1.0 BOM 관리 | 등록/가감/기간수정 요청 DTO 형식 검증 | 이미 `MemberSearchCondition` 등에서 사용 중 |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| 단일 `Pass` 엔티티 + 판별 컬럼 | JPA `@Inheritance(strategy = SINGLE_TABLE)` + `EveningMembership`/`SessionPass`/`LessonPass` 서브클래스 | Kotlin 타입 안전성은 얻지만, 이 프로젝트에 상속 매핑 선례가 전혀 없어(모든 기존 엔티티가 flat) 첫 도입 리스크를 진다. 물리 테이블은 어차피 하나라 `PassTransaction`/`PassPeriodChange`의 FK 대상이 달라지지 않음 — 이점 대비 새 프레임워크 표면이 넓어짐 [ASSUMED — 이 프로젝트의 "필요 이상으로 프레임워크 기능을 쓰지 않는다"는 일관된 성향(D-024·D-029·D-057)에 근거한 판단, 사용자 확인 권장] |
| 조건부 `UPDATE ... WHERE` (D-021 우선순위) | 비관적 락(`SELECT ... FOR UPDATE`) | 이번 phase는 관리자 단독 조작이라 필요 없음. Phase 4에서 정원 경쟁처럼 조건부 갱신만으로 부족한 지점이 드러나면 그때 부분 도입(D-021 원칙) |
| 신규 `PassLedgerService`(공용 차감/기록 서비스) | 각 유스케이스(`register`/`adjust`/`cancel`)에 로직 중복 | CONTEXT.md가 "Phase 4가 이 경로를 그대로 호출"한다고 명시 — 공용 서비스로 뽑아야 재사용 가능. 단일 컨트롤러/서비스에 다 넣으면 Phase 4가 또 재작성하게 됨 |

**Installation:** 해당 없음 (신규 의존성 없음)

**Version verification:** 해당 없음 — build.gradle.kts의 기존 BOM 관리 버전을 그대로 사용. Boot 4.1.0 BOM이 관리하는 Hibernate ORM 버전은 build.gradle.kts에 버전을 명시하지 않는다(기존 관례, conventions §11 "Boot BOM이 관리하는 것은 버전을 적지 않는다").

## Package Legitimacy Audit

**해당 없음 — 이번 phase는 신규 외부 패키지를 설치하지 않는다.** 기존 `build.gradle.kts`의 의존성만 사용하므로 slopcheck/레지스트리 검증 대상이 없다.

## Architecture Patterns

### System Architecture Diagram

```
[관리자 클라이언트]                         [회원 클라이언트]
      |                                            |
      | POST /api/admin/members/{id}/passes        | GET /api/members/me/passes
      | PATCH /api/admin/passes/{id}/adjustment     | GET /api/members/me/pass-transactions
      | PATCH /api/admin/passes/{id}/period         |
      | POST  /api/admin/passes/{id}/cancellation   |
      v                                            v
+---------------------------+          +---------------------------+
| AdminPassController        |          | MemberPassController      |
| (hasRole ADMIN, D-040)     |          | (hasRole MEMBER, D-040)   |
+-------------+---------------+          +-------------+---------------+
              |  DTO only (D-019)                      |  principal.requireMemberId()로 본인 스코프 강제
              v                                         v
+----------------------------------------------------------------+
| AdminPassService / MemberPassQueryService                       |
| @Transactional(readOnly=true) 기본, 변경 메서드만 오버라이드(D-020)|
|  - register(): Pass 생성 + INITIAL_GRANT PassTransaction 기록    |
|  - adjust():  Pass.validateAdjustment() 순수판정 → 조건부 UPDATE |
|  - changePeriod(): 전/후 값 계산 → PassPeriodChange 기록          |
|  - cancel(): status=CANCELED + (횟수제만) REGISTRATION_CANCELED  |
+----------------+------------------------+------------------------+
                 |                        |
                 v                        v
      +-------------------+     +---------------------------+
      | PassRepository     |     | PassTransactionRepository |
      | (조건부 UPDATE:     |     | PassPeriodChangeRepository|
      |  D-021, 반환행수로  |     +---------------------------+
      |  경쟁 감지)         |
      +---------+----------+
                v
      +-------------------------------------------------+
      | PostgreSQL                                        |
      | pass (type/status/start_date/end_date/remaining)  |
      | pass_transaction (±amount, reason, admin_id, at)   |
      | pass_period_change (전/후 시작·종료일, admin_id, at)|
      | CHECK 제약: type<->remaining_count nullability     |
      +-------------------------------------------------+
```

### Recommended Project Structure

```
src/main/kotlin/com/goldwrestling/pass/
├── Pass.kt                      # 엔티티 — 단일 테이블, PassType 판별 컬럼
├── PassType.kt                  # enum: EVENING_MEMBERSHIP / SESSION_PASS / LESSON_PASS (glossary 기존)
├── PassStatus.kt                # enum: ACTIVE / CANCELED (저장값만, 신규 — glossary 추가 필요)
├── PassTransaction.kt           # 엔티티 — ±수량 원장 (glossary 기존)
├── TransactionReason.kt         # enum: RESERVE/CANCEL_REFUND/ADMIN_ADJUST/EVENING_HALF/
│                                 #       INACTIVITY/CLASS_CANCELED_REFUND/INITIAL_GRANT/
│                                 #       REGISTRATION_CANCELED (glossary 기존 8종, 이번 phase는 3종만 사용)
├── PassPeriodChange.kt          # 엔티티 — 기간 변경 이력 (glossary 기존)
├── PassRepository.kt            # JpaRepository + JpaSpecificationExecutor + 조건부 UPDATE 커스텀 쿼리
├── PassTransactionRepository.kt
├── PassPeriodChangeRepository.kt
├── PassSpecifications.kt        # MemberSpecifications 패턴 재사용(member 스코프, 상태·타입 필터)
├── PassExceptions.kt            # DomainException 상속 (PassNotFoundException 등)
├── AdminPassController.kt       # /api/admin/members/{id}/passes, /api/admin/passes/{id}/...
├── AdminPassService.kt
├── MemberPassController.kt      # /api/members/me/passes, /api/members/me/pass-transactions
├── MemberPassService.kt
└── dto/
    ├── RegisterPassRequest.kt
    ├── AdjustPassRequest.kt
    ├── ChangePassPeriodRequest.kt
    ├── CancelPassRequest.kt
    ├── PassResponse.kt          # displayStatus 계산 포함 (companion object from(pass, today))
    ├── PassTransactionResponse.kt
    └── PassTransactionSearchCondition.kt  # @ParameterObject, passId?/page/size
```

### Pattern 1: 순수 도메인 정책 + 조건부 UPDATE 분리 (TDD의 핵심)

**What:** 가감이 "허용되는가"의 판정은 엔티티의 순수 Kotlin 메서드로, "실제 반영"은 리포지토리의 조건부 벌크 UPDATE로 분리한다.
**When to use:** 잔여 횟수를 바꾸는 모든 경로(`ADMIN_ADJUST`, 향후 Phase 4의 `RESERVE`/`CANCEL_REFUND`)
**Example (설계 스케치 — 실제 필드/이름은 계획 단계에서 확정):**
```kotlin
// Pass.kt — 스프링 없이 단위테스트 가능한 순수 판정 (add-domain-test §1)
class Pass(/* ... */) {
    // ...
    /** 정책 판정만 한다 — 실제 DB 반영은 하지 않는다. policies §4.2a 문장을 그대로 테스트 이름에 옮긴다. */
    fun validateAdjustment(amount: BigDecimal) {
        check(type != PassType.EVENING_MEMBERSHIP) { }
            .let { if (type == PassType.EVENING_MEMBERSHIP) throw PassTypeNotAdjustableException() }
        if (amount.remainder(HALF_SESSION).compareTo(BigDecimal.ZERO) != 0) throw InvalidAdjustmentUnitException()
        if (remainingCount!!.add(amount) < BigDecimal.ZERO) throw InsufficientPassCountException()
    }
}
```
```kotlin
// PassRepository.kt — RefreshTokenRepository.revokeIfUsable 패턴 그대로 재사용 (D-021)
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query("update Pass p set p.remainingCount = p.remainingCount + :amount where p.id = :id and p.remainingCount + :amount >= 0")
fun adjustRemainingCount(@Param("id") id: Long, @Param("amount") amount: BigDecimal): Int
// 반환 0 = 경쟁에서 졌거나(동시 가감) 애플리케이션 사전판정 이후 상태가 바뀜 → 서비스가 재조회 후 정확한 예외로 변환
```
**Source:** `com.goldwrestling.auth.RefreshTokenRepository.revokeIfUsable` (동일 프로젝트 실제 코드, 조건부 벌크 UPDATE + `flushAutomatically`/`clearAutomatically` 패턴)

### Pattern 2: `PassPeriodChange`를 통한 기간·유효기간 변경 통합 API

**What:** 저녁반 기간 수정(PASS-04)과 횟수권 유효기간 수정(PASS-07)을 하나의 엔드포인트로 통합한다.
**When to use:** `PATCH /api/admin/passes/{passId}/period` — `Pass`가 타입 무관하게 `startDate`/`endDate` 필드를 공유하므로(아래 Pattern 3), 기간제든 횟수제든 같은 요청/응답 모양으로 처리 가능
**Example:**
```kotlin
// 요청 DTO는 타입을 몰라도 된다 — 서버가 Pass.type을 보고 PassPeriodChange에 기록할 뿐
data class ChangePassPeriodRequest(
    @field:NotNull val newEndDate: LocalDate,
    val newStartDate: LocalDate? = null,   // 보통 시작일은 유지, 필요 시에만 변경
    @field:NotBlank val reason: String,
)
```
**Rationale:** D-057이 이미 "이용권 / 변경 전·후 시작·종료일"로 `PassPeriodChange` 컬럼을 지정했다 — 컬럼이 타입 무관 공통 구조이므로 API도 통합하는 것이 자연스럽다. (Claude's Discretion 항목의 권장 해소안 — 최종 확정은 discuss/planner)

### Pattern 3: `Pass` 스키마 — 기간제/횟수제 공통 `start_date`/`end_date` + 횟수제 전용 `remaining_count`

**What:** `EVENING_MEMBERSHIP`은 `end_date`를 회비 만료일로, `SESSION_PASS`/`LESSON_PASS`는 `end_date`를 횟수권 유효기간(`validUntil`)으로 동일하게 사용한다. `remaining_count`는 횟수제에만 채워진다.
**When to use:** 전체 스키마 설계의 기본 축
**Example (마이그레이션 스케치):**
```sql
CREATE TABLE pass (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES member (id),
    type VARCHAR(20) NOT NULL,               -- EVENING_MEMBERSHIP / SESSION_PASS / LESSON_PASS
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE / CANCELED 만 저장 (만료·소진은 계산)
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,                  -- 회비 만료일 또는 횟수권 유효기간(시작일+1년, D-055)
    remaining_count DECIMAL(4,1),            -- 횟수제만 NOT NULL, 기간제는 NULL
    canceled_at TIMESTAMPTZ,                 -- D-059 "관리자 화면 구분 표시"용 취소 메타데이터
    cancel_reason VARCHAR(500),
    canceled_by_admin_id BIGINT REFERENCES admin (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_pass_remaining_count_by_type CHECK (
        (type = 'EVENING_MEMBERSHIP' AND remaining_count IS NULL) OR
        (type IN ('SESSION_PASS', 'LESSON_PASS') AND remaining_count IS NOT NULL)
    )
);
```
**Source:** Hibernate ORM 공식 문서(`SINGLE_TABLE`은 "서브클래스 속성이 적을 때 적합, NOT NULL은 CHECK로 강제") + 이 프로젝트의 `ck_refresh_token_principal` 선례 [VERIFIED: context7 `/hibernate/hibernate-orm` "Object/relational mapping > Mapping entity inheritance hierarchies"]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 페이지네이션 응답 형태 | 새 `PassPageResponse` | `member/dto/PageResponse<T>` 재사용 | D-035·D-058이 이미 "회원 목록과 동일 형태" 요구, 두 번째 사용처가 생겼으니 conventions §1 "두 기능 이상이 쓰면 공용화" 조건 충족 — `common/dto`로 승격 검토 가치 있음(계획 단계 판단) |
| 조건 DTO의 쿼리 파라미터 표현 | 커스텀 바인딩 로직 | `@ParameterObject` + `@ModelAttribute @Valid`(D-054) | 이걸 빠뜨리면 openapi.yaml이 객체 1개 파라미터로 생성되어 FE 생성기가 바인딩 자체를 못 함(실제 재현된 버그, WR-06) |
| 잔여 횟수 동시성 방어 | 애플리케이션 레벨 synchronized/캐시 | DB 조건부 UPDATE(`WHERE remaining_count + :amount >= 0`) | D-021 — 다중 인스턴스에서 애플리케이션 동기화는 무효, DB만이 마지막 방어선 |
| 에러 응답 포맷 | 커스텀 예외 핸들러 | 기존 `DomainException`/`GlobalExceptionHandler` 상속 | 새 예외 클래스만 추가하면 `ProblemDetail` 변환·`code` 주입이 자동으로 통일됨(D-028) |
| BigDecimal 0.5 단위 검증 | 문자열 파싱/수동 곱셈 비교 | `amount.remainder(BigDecimal("0.5")).compareTo(BigDecimal.ZERO) == 0` | `DECIMAL(4,1)` 컬럼 자체는 0.1 단위까지 허용 — "0.5 단위"는 별도 도메인 규칙(아래 Pitfall 참고) |

**Key insight:** 이 phase의 위험은 "새 라이브러리가 필요한 것"이 아니라 "기존에 검증된 패턴(조건부 UPDATE, CHECK 제약, DTO 변환 companion object)을 새 도메인에 정확히 재적용하는 것"이다. 새로 발명할 필요가 없다 — Phase 2 코드를 그대로 본떠야 한다.

## Common Pitfalls

### Pitfall 1: `DECIMAL(4,1)` 컬럼이 "0.5 단위"를 자동으로 강제하지 않는다

**What goes wrong:** `remaining_count DECIMAL(4,1)`은 소수점 1자리(0.1 단위)까지 저장 가능하다. 관리자가 실수로 `0.3`을 입력해도 컬럼 제약은 통과한다.
**Why it happens:** D-016의 `DECIMAL(4,1)`은 "0.5 단위가 존재한다"는 요구를 만족시키기 위한 최소 정밀도이지, "오직 0.5의 배수만 허용"이라는 별도 규칙(D-056 "가감 단위는 0.5")을 컬럼 스케일이 대신 강제해주지 않는다.
**How to avoid:** 가감 입력값에 대해 `amount.remainder(HALF_SESSION).compareTo(BigDecimal.ZERO) == 0` 같은 명시적 도메인 검증을 추가한다. (단, `INITIAL_GRANT`의 초기 횟수는 D-055가 "0.5 단위 자유 입력"이라고만 했지 "0.5의 배수만"이라고 하지 않았다는 점을 정확히 구분 — 초기 등록값은 0.5 배수 강제 규칙이 적용되는지 CONTEXT.md에 명시가 없다. **Assumptions Log 참고, 계획 전 확인 필요.**)
**Warning signs:** 테스트가 `0.5`/`1.0` 같은 "깔끔한" 값만 검증하고 `0.3`/`0.7` 같은 위반 케이스를 빠뜨리면 이 규칙이 실제로 강제되는지 알 수 없다.

### Pitfall 2: `BigDecimal(Double)` 생성자 사용

**What goes wrong:** `BigDecimal(0.5)`는 부동소수 오차 때문에 `0.5000000000000000277...` 같은 값이 된다.
**Why it happens:** Double → BigDecimal 변환은 이진 부동소수의 근사값을 그대로 가져온다.
**How to avoid:** 반드시 문자열 생성자 `BigDecimal("0.5")` 사용(conventions §4에 이미 명시된 규칙, Phase 3에서 실제로 처음 적용됨).
**Warning signs:** `compareTo(BigDecimal.ZERO) == 0` 판정이 예상과 다르게 나오면 이 문제를 의심한다.

### Pitfall 3: LAZY `member` 연관을 트랜잭션 밖에서 접근

**What goes wrong:** `PassResponse.from(pass)`를 컨트롤러(트랜잭션 밖)에서 호출하면 `pass.member.name`(있다면) 접근 시 `LazyInitializationException`.
**Why it happens:** `@ManyToOne(fetch = LAZY)`가 기본 규칙(conventions §3)이고, `open-in-view=false` 전제.
**How to avoid:** DTO 변환은 반드시 서비스 메서드(`@Transactional` 범위) 안에서 끝낸다 — `AdminMemberService.getDetail`이 이미 이 패턴을 보여줌.
**Warning signs:** 컨트롤러에서 `service.xxx().let { PassResponse.from(it) }` 처럼 서비스 밖에서 변환하는 코드가 보이면 즉시 의심.

### Pitfall 4: 조건부 UPDATE 이후 준영속 엔티티의 LAZY 필드 접근

**What goes wrong:** `@Modifying(clearAutomatically = true)` 쿼리 실행 직후 영속성 컨텍스트가 비워지므로, 그 전에 들고 있던 엔티티 참조가 준영속 상태가 되어 LAZY 연관 접근 시 예외가 난다.
**Why it happens:** `TokenService.rotate`에서 이미 겪은 문제(주석에 실제 재현 기록 있음) — 벌크 UPDATE 전에 필요한 값(예: `member.id`, `type`)을 로컬 변수로 미리 꺼내둬야 한다.
**How to avoid:** 조건부 UPDATE를 호출하기 **전에** 이후 로직에 필요한 모든 스칼라 값을 지역 변수에 담아둔다.
**Warning signs:** 벌크 UPDATE 호출 다음 줄에서 엔티티의 연관 필드를 만지는 코드.

### Pitfall 5: `PassTransaction`/`PassPeriodChange`의 "주체(admin_id)"를 미리 다형화하려는 유혹

**What goes wrong:** Phase 4가 `RESERVE`/`CANCEL_REFUND`(회원 자신이 주체)를 추가할 것을 예상해 지금 `member_id` nullable 컬럼과 "정확히 하나만 채워짐" CHECK(`ck_refresh_token_principal`과 동일 패턴)를 미리 넣고 싶어질 수 있다.
**Why it happens:** CONTEXT.md가 "Phase 4가 이 경로를 그대로 호출한다"고 명시해 재사용을 의식하게 만든다.
**How to avoid:** D-030(최소 스키마 원칙 — "확정되지 않은 설계를 추측으로 굳히지 않는다")을 그대로 적용한다. Phase 3의 사유 코드(`INITIAL_GRANT`/`ADMIN_ADJUST`/`REGISTRATION_CANCELED`)는 **전부 관리자 주체**이므로 `admin_id BIGINT NOT NULL`로 충분하다. 게다가 Phase 5(`INACTIVITY`)는 배치(사람 주체 없음)라 "정확히 하나"가 아니라 "둘 다 null 가능"이 되어야 해 `ck_refresh_token_principal`을 그대로 복사하면 오히려 나중에 깨진다. Phase 4에서 실제로 필요해지면 새 마이그레이션으로 `member_id` nullable 컬럼 + CHECK 완화를 추가한다(컬럼 추가는 싸다, D-030).
**Warning signs:** 이번 phase 마이그레이션에 `member_id` 컬럼이나 "정확히 하나" CHECK가 등장하면 스코프 과잉.

### Pitfall 6: 마이그레이션 번호 충돌

**What goes wrong:** 현재 최신 마이그레이션은 `V3__add_auth_credentials_and_refresh_token.sql`이다. Phase 3은 **V4**부터 시작해야 한다 — 다른 병렬 작업이 먼저 V4를 썼다면 번호를 확인하고 순차로 이어간다.
**Warning signs:** `./gradlew build` 시 Flyway 체크섬/순번 오류.

## Code Examples

### `PassStatus`/`PassType` — 신규 glossary 개념 (사용 전 glossary.md 추가 필요)

```kotlin
// PassType은 glossary.md에 이미 존재 (EVENING_MEMBERSHIP/SESSION_PASS/LESSON_PASS)
enum class PassType { EVENING_MEMBERSHIP, SESSION_PASS, LESSON_PASS }

// PassStatus는 이번 phase 신규 — glossary.md에 추가 후 사용 (CLAUDE.md 규칙 3)
// 저장값은 2종만: 취소 여부만 저장이 필요(만료·소진은 계산 가능, CONTEXT.md discretion)
enum class PassStatus { ACTIVE, CANCELED }
```

### 순수 도메인 단위테스트 (스프링 없음) — `MemberOnboardingStatusTest` 패턴 재사용

```kotlin
// 대상 파일: src/test/kotlin/com/goldwrestling/pass/PassAdjustmentPolicyTest.kt
class PassAdjustmentPolicyTest {
    @Test
    fun `기간제 이용권은 수동 가감 대상이 아니다`() {
        val pass = eveningMembership()
        assertThatThrownBy { pass.validateAdjustment(BigDecimal("0.5")) }
            .isInstanceOf(PassTypeNotAdjustableException::class.java)
    }

    @Test
    fun `가감 결과가 음수가 되면 거부된다`() {
        val pass = sessionPass(remaining = "0.5")
        assertThatThrownBy { pass.validateAdjustment(BigDecimal("-1.0")) }
            .isInstanceOf(InsufficientPassCountException::class.java)
    }

    @Test
    fun `0.5 단위가 아니면 거부된다`() {
        val pass = sessionPass(remaining = "2.0")
        assertThatThrownBy { pass.validateAdjustment(BigDecimal("0.3")) }
            .isInstanceOf(InvalidAdjustmentUnitException::class.java)
    }

    @Test
    fun `만료된 횟수권도 가감할 수 있다`() {
        // D-056: 유효기간 만료 여부는 가감 가능 여부와 무관 — 별도 검증 없음을 증명
        val pass = sessionPass(remaining = "1.0", endDate = LocalDate.of(2020, 1, 1))
        pass.validateAdjustment(BigDecimal("0.5"))  // 예외 없이 통과해야 함
    }
}
```

### "잔여 = 이력 합계" 불변식 통합테스트 (Testcontainers)

```kotlin
// 여러 연산(등록, 가감, 취소) 후 원장 합계 검증 — Deferred된 배치 자동검증(Phase 5)과 별개로
// 이번 phase 안에서 최소 1개는 명시적으로 이 불변식을 증명해야 한다.
@Test
fun `등록-가감-취소를 거쳐도 잔여는 이력 합계와 항상 같다`() {
    val pass = registerSessionPass(initial = "3.0")
    adminPassService.adjust(pass.id!!, BigDecimal("-0.5"), "저녁반 참여 보정", adminId)
    adminPassService.adjust(pass.id!!, BigDecimal("1.0"), "이벤트 보상", adminId)

    val sumOfHistory = passTransactionRepository.sumAmountByPassId(pass.id!!)
    val current = passRepository.findById(pass.id!!).get()
    assertThat(current.remainingCount).isEqualByComparingTo(sumOfHistory)  // compareTo 기반, D-016
}
```

## State of the Art

이 phase에는 "구식 vs 신식" 대비가 크게 해당하지 않는다(신규 도메인). 참고할 변화 지점은:

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| Hibernate Envers 같은 범용 감사 프레임워크로 변경 이력 처리 | 전용 이력 테이블(`PassTransaction`/`PassPeriodChange`)을 도메인 스키마로 직접 설계 | 이 프로젝트 결정(D-057, 2026-08) | Envers는 Boot 4/Hibernate 7 호환 검증 비용 + 도메인 필드 커스터마이징 복잡도 때문에 기각됨 — Phase 3부터 "전용 이력 테이블" 패턴이 이 프로젝트의 표준이 됨(향후 phase도 이 패턴 재사용 예상) |

**참고 없음 항목:** Spring Data JPA `Specification.allOf(...)` 정적 팩토리(구버전 `Specification.where(...)` 대체)는 이미 Phase 2에서 verify-boot4-api 절차로 확인 완료(`MemberSpecifications` 주석 참고) — Phase 3의 `PassSpecifications`도 동일하게 `allOf`를 쓴다.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|----------------|
| A1 | JPA `@Inheritance` 없이 단일 엔티티 + 판별 컬럼(`PassType`)으로 모델링하는 것이 이 프로젝트에 더 적합하다 | Standard Stack > Alternatives Considered, Architecture Patterns 3 | discuss-phase/계획 단계에서 사용자가 실제 클래스 계층(상속) 모델을 선호하면 엔티티·마이그레이션·리포지토리 설계 전체를 다시 작성해야 함. CONTEXT.md가 이 결정을 명시적으로 Claude's Discretion으로 열어뒀으므로 계획 전에 이 근거를 사용자에게 한 번 더 확인하는 것을 권장 |
| A2 | 초기 등록 횟수(`INITIAL_GRANT`)는 "0.5 단위 자유 입력"이지만 "0.5의 배수만" 강제되는지는 D-055 문장만으로는 불명확 — 본 리서치는 등록 시에도 0.5 배수 검증을 적용한다고 가정 | Pitfall 1 | 실제로는 등록 시 임의 소수점(예: 2.3회) 입력을 허용해야 한다면 검증 로직을 등록/가감 경로에서 다르게 둬야 함 — 재작업 필요 |
| A3 | `EVENING_MEMBERSHIP` 기간 계산은 `endDate = startDate.plusMonths(n)`(같은 날짜, exclusive 경계 없음), `SESSION_PASS`/`LESSON_PASS` 유효기간은 `endDate = startDate.plusYears(1)`로 가정. "유효함"의 판정은 `!endDate.isBefore(today)`(종료일 당일까지 포함)로 가정 | Architecture Patterns 3, Common Pitfalls (간접) | 정책 문서·CONTEXT.md가 "1년"/"1·3·6개월"이라고만 명시하고 경계일 포함 여부를 명시하지 않음 — 반대로 가정하면 회원이 마지막 날 사용 가능 여부가 실제와 하루 어긋남(회원 신뢰 이슈로 이어질 수 있음) |
| A4 | 취소(D-059)의 "이력"은 `pass` 테이블에 직접 둔 `canceled_at`/`cancel_reason`/`canceled_by_admin_id` 컬럼으로 satisfy하고, `PassTransaction`/`PassPeriodChange`와는 별개로 취급한다 (기간제는 이 3컬럼이 유일한 취소 이력, 횟수제는 이 3컬럼 + `REGISTRATION_CANCELED` PassTransaction 둘 다) | Architecture Patterns 3 | D-057이 "PassTransaction은 ±수량 원장 역할에 고정"이라고 명시했으므로 기간제 취소를 PassTransaction에 억지로 넣는 것은 이 결정과 충돌한다고 판단했으나, 사용자가 "모든 취소는 하나의 통합 이력 테이블에 남아야 한다"고 의도했다면 재설계 필요 |
| A5 | 관리자용 `GET /api/admin/passes/{passId}/transactions`(가감 이력 조회) 엔드포인트는 PASS-01~08 어디에도 명시적으로 요구되지 않아 이번 phase 범위에서 제외했다 | Architecture Patterns (API 설계) | 관리자가 회원 문의 대응 시 이 조회가 실제로 필요하다면 범위 누락 — PASS-02가 "이력이 남는다"까지만 요구하고 "관리자가 조회할 수 있다"는 요구하지 않음을 근거로 제외 |

## Open Questions (RESOLVED — 2026-08-03 plan-phase에서 사용자 확정)

> 3건 모두 계획 단계 AskUserQuestion으로 해소되어 D-060/D-061/D-062로 03-01 플랜에 기록된다.

1. **`Pass` 엔티티 모델링: 단일 테이블 vs JPA 상속** — **RESOLVED: 단일 엔티티 + `PassType` 판별 컬럼 확정 (D-060)**
   - What we know: CONTEXT.md가 이 결정을 Claude's Discretion으로 명시적으로 열어뒀고, glossary는 `Pass`를 "3종의 공통 부모"로 서술한다(도메인 개념 서술이지 구현 지시는 아님).
   - What's unclear: "공통 부모"라는 glossary 표현이 실제 Kotlin 클래스 계층을 기대하는 뉘앙스인지, 순수 도메인 개념 서술인지.
   - Recommendation: 본 리서치는 단일 엔티티(판별 컬럼)를 권장하지만(A1), 계획 단계에서 이 근거를 사용자에게 1줄로 재확인하는 것을 권장한다.

2. **`ADMIN_ADJUST` 사유 텍스트(`reason`)를 `PassTransaction`에 저장할 위치** — **RESOLVED: `reason`(enum)·`note`(자유 텍스트) 분리, `ADMIN_ADJUST`만 note 필수 확정 (D-061)**
   - What we know: D-056 "사유 입력 필수". `PassTransaction`의 `reason` 컬럼명이 이미 사유 **코드**(enum, `TransactionReason`)로 쓰이고 있어(glossary "사유 코드"), 자유 텍스트 사유와 컬럼명이 겹친다.
   - What's unclear: 자유 텍스트 사유를 `reason_note`/`memo` 등 별도 컬럼에 둘지, `TransactionReason`과 통합할지.
   - Recommendation: `reason`(enum, `TransactionReason`)과 `note`(nullable VARCHAR, 자유 텍스트)를 분리하는 것을 권장 — `ADMIN_ADJUST`일 때만 `note` NOT NULL을 서비스 레벨에서 강제.

3. **`PassPeriodChange`에서 저녁반(기간제)과 횟수권(유효기간)의 "시작일" 의미가 다른가** — **RESOLVED: 횟수권은 종료일(`newEndDate`)만 수정 가능, 시작일 고정 확정 (D-062)**
   - What we know: 저녁반 기간 수정은 회비 적용 구간(시작~종료) 자체를 바꾸는 것이고, 횟수권 유효기간 수정은 통상 "종료일(유효기간)"만 바뀌고 시작일은 그대로일 가능성이 높다.
   - What's unclear: 횟수권의 시작일(등록일)도 수정 대상에 포함되는지 policies.md/D-056/D-057이 명시하지 않는다.
   - Recommendation: API를 `newStartDate`(옵션)+`newEndDate`(필수)로 설계해 두 케이스 모두 수용 가능하게(Pattern 2 참고), 실제 사용은 계획 단계에서 확정.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| Docker / docker-compose (Postgres) | Testcontainers 통합테스트, `generateApiDocs` | 로컬 환경 의존 — 이 세션에서 직접 확인 불가 | — | 없음 (M3 완료 조건이 통합테스트 필수라 Docker 없이는 검증 불가) |
| PostgreSQL 18 | 런타임/테스트 | Testcontainers가 컨테이너로 제공 | 18 (docker-compose 설정 기준, 이 세션에서 버전 파일 미확인) | — |

기존 Phase 1·2가 이미 이 의존성들로 정상 동작함을 코드베이스로 확인했으므로(FlywayMigrationIntegrationTest 등), 실질적 리스크는 낮다.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ (`spring-boot-starter-webmvc-test`/`-data-jpa-test`) + Testcontainers 2.x(`testcontainers-postgresql`) [VERIFIED: build.gradle.kts] |
| Config file | 없음 — `TestcontainersConfiguration.kt`(`@ServiceConnection`) 기존 배선 재사용 |
| Quick run command | `./gradlew test --tests "com.goldwrestling.pass.*"` |
| Full suite command | `./gradlew build` (ktlintCheck + compileKotlin + test 전부 포함) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|---------------------|--------------|
| PASS-01 | 등록 시 타입별 시작일/유효기간 계산, `INITIAL_GRANT` 이력 생성 | unit + integration | `./gradlew test --tests "com.goldwrestling.pass.PassRegistrationTest"` | ❌ Wave 0 |
| PASS-02 | 모든 차감/복구가 `PassTransaction`으로 남고 이력 없는 변경 불가 | integration (불변식 검증) | `./gradlew test --tests "com.goldwrestling.pass.PassLedgerInvariantTest"` | ❌ Wave 0 |
| PASS-03 | 수동 가감 0.5단위·음수거부·기간제 제외 | unit + integration | `./gradlew test --tests "com.goldwrestling.pass.PassAdjustmentPolicyTest"` / `AdminPassControllerTest` | ❌ Wave 0 |
| PASS-04 | 저녁반 기간 수정 + `PassPeriodChange` 기록 | unit(기간 계산) + integration | `./gradlew test --tests "com.goldwrestling.pass.PassPeriodChangeTest"` | ❌ Wave 0 |
| PASS-05 | 본인 이용권 조회(만료·소진 포함, 취소 제외) | integration | `./gradlew test --tests "com.goldwrestling.pass.MemberPassControllerTest"` | ❌ Wave 0 |
| PASS-06 | 본인 이력 조회(이용권별 필터 + page/size) | integration | `./gradlew test --tests "com.goldwrestling.pass.MemberPassTransactionControllerTest"` | ❌ Wave 0 |
| PASS-07 | 횟수권 유효기간 수정 | unit + integration | PASS-04와 동일 테스트 클래스(통합 엔드포인트, Pattern 2) | ❌ Wave 0 |
| PASS-08 | 등록 취소 + `REGISTRATION_CANCELED` 상쇄 이력, 화면별 노출 차등 | unit(상쇄 계산) + integration | `./gradlew test --tests "com.goldwrestling.pass.PassCancellationTest"` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "com.goldwrestling.pass.*"`
- **Per wave merge:** `./gradlew build`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `pass/PassAdjustmentPolicyTest.kt` — 순수 단위테스트, PASS-03
- [ ] `pass/PassRegistrationTest.kt` — 순수 단위테스트(기간·유효기간 계산), PASS-01
- [ ] `pass/PassPeriodChangeTest.kt` — 단위+통합, PASS-04/07
- [ ] `pass/PassCancellationTest.kt` — 단위+통합, PASS-08
- [ ] `pass/PassLedgerInvariantTest.kt` — 통합, "잔여 = 이력 합계" 증명, PASS-02
- [ ] `pass/AdminPassControllerTest.kt` — 통합(인가·성공·대표실패), PASS-01/03/04/07/08
- [ ] `pass/MemberPassControllerTest.kt` — 통합, PASS-05
- [ ] `pass/MemberPassTransactionControllerTest.kt` — 통합(페이지네이션·필터), PASS-06
- 프레임워크 신규 설치: 없음 — 기존 JUnit5/AssertJ/MockMvc/Testcontainers로 충분

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-------------------|
| V2 Authentication | no | 이 phase는 기존 JWT 인증(Phase 2) 재사용, 신규 인증 로직 없음 |
| V3 Session Management | no | STATELESS, 해당 없음 |
| V4 Access Control | yes | `SecurityConfig`의 `/api/admin/**`(hasRole ADMIN)/`/api/members/**`(hasRole MEMBER) 재사용(D-040) — 신규 URL 규칙 불필요. **회원 자기 스코프는 반드시 `AuthenticatedPrincipal.requireMemberId()`에서 얻고, 요청 바디/경로의 memberId를 신뢰하지 않는다**(IDOR 방지) |
| V5 Input Validation | yes | Bean Validation(형식) + 엔티티 메서드(도메인 규칙: 0.5단위, 음수거부, 기간제 제외) 이중 검증. `EVENING_MEMBERSHIP` 개월 수는 자유 정수가 아니라 닫힌 집합(1/3/6)이므로 enum(`EveningMembershipTerm` 등, glossary 추가 필요)으로 표현해 임의값 입력 자체를 차단하는 것을 권장 |
| V6 Cryptography | no | 신규 암호화 대상 없음 |

### Known Threat Patterns for 이 스택

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|-----------------------|
| 다른 회원의 이용권/이력을 memberId 파라미터 조작으로 열람(IDOR) | Information Disclosure | 회원 자기 조회 엔드포인트는 경로/쿼리에 memberId를 받지 않고 `AuthenticatedPrincipal`에서만 얻는다 — `/api/members/me/passes`처럼 "me" 고정 경로 사용 |
| 동시 요청으로 잔여가 음수로 내려가는 비즈니스 로직 우회(TOCTOU) | Tampering | DB 조건부 UPDATE(`WHERE remaining_count + :amount >= 0`)로 원자적 방어(D-021) — Phase 3 자체는 저위험이나 Phase 4가 재사용하므로 지금부터 이 패턴 적용 |
| 관리자 API를 이용한 취소된 이용권 재조작(취소 후 가감 시도) | Tampering | 서비스 레벨에서 `pass.status == CANCELED`이면 가감/기간수정 자체를 거부하는 가드 필요(정책 문서에 명시적 문장은 없지만 D-059의 "취소" 의미상 당연히 요구됨 — Assumptions/Open Questions에 없지만 계획 시 반드시 포함) |

## Sources

### Primary (HIGH confidence)
- 이 저장소 실제 코드 — `src/main/kotlin/com/goldwrestling/**`, `src/test/kotlin/com/goldwrestling/**`, `src/main/resources/db/migration/**` (Phase 1·2 산출물, 전량 Read 도구로 실측)
- `docs/policies.md` §1·§4.1·§4.2·§4.2a — 이용권·차감 도메인 규칙 최종 기준
- `docs/decisions.md` D-016·D-017·D-018·D-019·D-020·D-021·D-030·D-037·D-054·D-055~D-059
- `docs/glossary.md` — Pass/PassTransaction/PassPeriodChange/TransactionReason 네이밍
- `docs/conventions.md` §1~§11 — 패키지·엔티티·시간·에러·테스트·Boot4 규약
- `docs/error-codes.md` — 기존 에러코드 레지스트리 형식
- `.claude/skills/add-migration/SKILL.md`, `add-endpoint/SKILL.md`, `add-domain-test/SKILL.md`, `verify-boot4-api/SKILL.md`
- context7 `/hibernate/hibernate-orm` — SINGLE_TABLE 상속 전략, 판별 컬럼, CHECK 제약을 통한 서브클래스 NOT NULL 강제 문서 확인

### Secondary (MEDIUM confidence)
- (해당 없음 — 이번 리서치는 전량 1차 소스(코드베이스·프로젝트 문서·context7)로 수행됨)

### Tertiary (LOW confidence)
- (해당 없음)

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — 신규 의존성 없음, 기존 검증된 스택만 재사용
- Architecture(모델링 방식): MEDIUM — 단일 테이블 vs 상속은 이 프로젝트 최초 결정이라 사용자 확인 권장(A1)
- Pitfalls: HIGH — 전부 이 코드베이스에서 실제로 겪은 문제(RefreshToken/Member) 또는 D-016/D-021의 직접적 연장
- 테스트 전략(TDD): HIGH — `MemberOnboardingStatusTest`/`RefreshTokenRepository` 패턴이 이미 이 코드베이스에 정확히 대응하는 선례로 존재

**Research date:** 2026-08-03
**Valid until:** 이 phase의 계획·실행이 끝날 때까지 유효 (외부 라이브러리 버전 의존이 없어 만료 리스크 낮음 — 30일 기준 적용 시 2026-09-02)
