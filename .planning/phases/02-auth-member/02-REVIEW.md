---
phase: 02-auth-member
reviewed: 2026-08-02T23:57:10Z
depth: standard
files_reviewed: 94
files_reviewed_list:
  - docs/api/openapi.yaml
  - docs/conventions.md
  - docs/decisions.md
  - docs/error-codes.md
  - docs/glossary.md
  - src/main/kotlin/com/goldwrestling/admin/Admin.kt
  - src/main/kotlin/com/goldwrestling/admin/AdminRepository.kt
  - src/main/kotlin/com/goldwrestling/admin/AdminSeeder.kt
  - src/main/kotlin/com/goldwrestling/auth/AdminAuthController.kt
  - src/main/kotlin/com/goldwrestling/auth/AdminAuthService.kt
  - src/main/kotlin/com/goldwrestling/auth/AuthenticatedPrincipal.kt
  - src/main/kotlin/com/goldwrestling/auth/AuthenticationPrincipalResolver.kt
  - src/main/kotlin/com/goldwrestling/auth/JwtAuthenticationFilter.kt
  - src/main/kotlin/com/goldwrestling/auth/KakaoAuthController.kt
  - src/main/kotlin/com/goldwrestling/auth/KakaoAuthService.kt
  - src/main/kotlin/com/goldwrestling/auth/PrincipalType.kt
  - src/main/kotlin/com/goldwrestling/auth/RefreshToken.kt
  - src/main/kotlin/com/goldwrestling/auth/RefreshTokenInvalidException.kt
  - src/main/kotlin/com/goldwrestling/auth/RefreshTokenRepository.kt
  - src/main/kotlin/com/goldwrestling/auth/TokenController.kt
  - src/main/kotlin/com/goldwrestling/auth/TokenPair.kt
  - src/main/kotlin/com/goldwrestling/auth/TokenService.kt
  - src/main/kotlin/com/goldwrestling/auth/dto/AdminLoginRequest.kt
  - src/main/kotlin/com/goldwrestling/auth/dto/AdminLoginResponse.kt
  - src/main/kotlin/com/goldwrestling/auth/dto/KakaoLoginRequest.kt
  - src/main/kotlin/com/goldwrestling/auth/dto/KakaoLoginResponse.kt
  - src/main/kotlin/com/goldwrestling/auth/dto/TokenPairResponse.kt
  - src/main/kotlin/com/goldwrestling/auth/dto/TokenRequests.kt
  - src/main/kotlin/com/goldwrestling/auth/kakao/KakaoApiClient.kt
  - src/main/kotlin/com/goldwrestling/auth/kakao/KakaoExceptions.kt
  - src/main/kotlin/com/goldwrestling/auth/kakao/KakaoTokenResponse.kt
  - src/main/kotlin/com/goldwrestling/auth/kakao/KakaoUserResponse.kt
  - src/main/kotlin/com/goldwrestling/branch/BranchRepository.kt
  - src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt
  - src/main/kotlin/com/goldwrestling/common/error/ProblemDetailAccessDeniedHandler.kt
  - src/main/kotlin/com/goldwrestling/common/error/ProblemDetailAuthenticationEntryPoint.kt
  - src/main/kotlin/com/goldwrestling/common/error/ProblemDetailResponseWriter.kt
  - src/main/kotlin/com/goldwrestling/config/AdminSeedProperties.kt
  - src/main/kotlin/com/goldwrestling/config/ClockConfig.kt
  - src/main/kotlin/com/goldwrestling/config/JwtConfig.kt
  - src/main/kotlin/com/goldwrestling/config/JwtProperties.kt
  - src/main/kotlin/com/goldwrestling/config/KakaoProperties.kt
  - src/main/kotlin/com/goldwrestling/config/KakaoRestClientConfig.kt
  - src/main/kotlin/com/goldwrestling/config/PasswordEncoderConfig.kt
  - src/main/kotlin/com/goldwrestling/config/SecurityConfig.kt
  - src/main/kotlin/com/goldwrestling/member/AdminMemberController.kt
  - src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt
  - src/main/kotlin/com/goldwrestling/member/Member.kt
  - src/main/kotlin/com/goldwrestling/member/MemberExceptions.kt
  - src/main/kotlin/com/goldwrestling/member/MemberProfileController.kt
  - src/main/kotlin/com/goldwrestling/member/MemberProfileService.kt
  - src/main/kotlin/com/goldwrestling/member/MemberRegistrationService.kt
  - src/main/kotlin/com/goldwrestling/member/MemberRepository.kt
  - src/main/kotlin/com/goldwrestling/member/MemberSpecifications.kt
  - src/main/kotlin/com/goldwrestling/member/MemberStateGate.kt
  - src/main/kotlin/com/goldwrestling/member/PhoneNumberNormalizer.kt
  - src/main/kotlin/com/goldwrestling/member/dto/MemberDetailResponse.kt
  - src/main/kotlin/com/goldwrestling/member/dto/MemberSearchCondition.kt
  - src/main/kotlin/com/goldwrestling/member/dto/MemberSessionResponse.kt
  - src/main/kotlin/com/goldwrestling/member/dto/MemberSummaryResponse.kt
  - src/main/kotlin/com/goldwrestling/member/dto/MyProfileResponse.kt
  - src/main/kotlin/com/goldwrestling/member/dto/OnboardingRequest.kt
  - src/main/kotlin/com/goldwrestling/member/dto/PageResponse.kt
  - src/main/kotlin/com/goldwrestling/member/dto/RejectMemberRequest.kt
  - src/main/kotlin/com/goldwrestling/member/dto/UpdateMemberStatusRequest.kt
  - src/main/resources/application.yml
  - src/main/resources/db/migration/V3__add_auth_credentials_and_refresh_token.sql
  - src/test/kotlin/com/goldwrestling/admin/AdminSeederTest.kt
  - src/test/kotlin/com/goldwrestling/auth/AdminAuthControllerTest.kt
  - src/test/kotlin/com/goldwrestling/auth/AuthRepositoryIntegrationTest.kt
  - src/test/kotlin/com/goldwrestling/auth/JwtAuthenticationFilterTest.kt
  - src/test/kotlin/com/goldwrestling/auth/KakaoAuthControllerTest.kt
  - src/test/kotlin/com/goldwrestling/auth/RefreshTokenRotationTest.kt
  - src/test/kotlin/com/goldwrestling/auth/TokenControllerTest.kt
  - src/test/kotlin/com/goldwrestling/auth/kakao/KakaoApiClientTest.kt
  - src/test/kotlin/com/goldwrestling/common/error/ErrorCodeRegistryTest.kt
  - src/test/kotlin/com/goldwrestling/common/error/GlobalExceptionHandlerTest.kt
  - src/test/kotlin/com/goldwrestling/config/JwtConfigTest.kt
  - src/test/kotlin/com/goldwrestling/config/SecurityFilterChainTest.kt
  - src/test/kotlin/com/goldwrestling/db/FlywayMigrationIntegrationTest.kt
  - src/test/kotlin/com/goldwrestling/member/MemberApprovalTest.kt
  - src/test/kotlin/com/goldwrestling/member/MemberOnboardingStatusTest.kt
  - src/test/kotlin/com/goldwrestling/member/MemberProfileTest.kt
  - src/test/kotlin/com/goldwrestling/member/MemberSearchTest.kt
  - src/test/kotlin/com/goldwrestling/member/MemberSpecificationTest.kt
  - src/test/kotlin/com/goldwrestling/member/MemberStateGateTest.kt
  - src/test/kotlin/com/goldwrestling/member/MemberStatusChangeTest.kt
  - src/test/kotlin/com/goldwrestling/member/OnboardingValidationTest.kt
  - src/test/kotlin/com/goldwrestling/support/KakaoApiMockSupport.kt
  - src/test/kotlin/com/goldwrestling/support/MutableTestClock.kt
  - src/test/kotlin/com/goldwrestling/support/MutableTestClockTest.kt
  - src/test/kotlin/com/goldwrestling/support/TestClockConfiguration.kt
  - src/test/resources/kakao/token-response.json
  - src/test/resources/kakao/user-response.json
findings:
  critical: 1
  warning: 6
  info: 6
  total: 13
status: issues_found
---

# Phase 2: Code Review Report (인증·회원)

**Reviewed:** 2026-08-02T23:57:10Z
**Depth:** standard
**Files Reviewed:** 94
**Status:** issues_found

## Summary

Phase 2(카카오 로그인, 관리자 로그인, JWT 발급·회전, 회원 온보딩·승인·거절·상태 변경) 전체를 검토했다.
전반적으로 설계 결정(D-032~D-049)이 코드에 충실히 반영되어 있고, 보안 기본기(alg confusion 방어,
refresh 해시 저장, 계정 열거 방지 메시지 통일, 거절 사유 미노출, LIKE 와일드카드 이스케이프)와
테스트 커버리지(성공+실패 경로, 스키마 제약 검증)가 좋은 수준이다.

그러나 **동시성 경로에서 증명 가능한 결함 2건**을 찾았다:

1. **(Critical)** `MemberRegistrationService`의 동시 최초 로그인 경쟁 복구 코드가 PostgreSQL 트랜잭션
   의미론상 **절대 성공할 수 없는 경로**다 — 유니크 제약 위반 후 같은 트랜잭션에서 재조회하면
   "current transaction is aborted"로 실패하고, 설령 조회가 됐더라도 rollback-only 마킹 때문에 커밋에서
   `UnexpectedRollbackException`이 난다. 주석·KDoc이 주장하는 "정상 흐름 흡수"가 실제로는 500이 된다.
2. **(Warning)** refresh 토큰 회전의 폐기 처리가 조회-판단-더티체킹 방식(TOCTOU)이라, 같은 토큰을 동시에
   두 번 제시하면 둘 다 성공한다 — D-036의 재사용 감지(탈취 감지)가 정확히 그 위협 시나리오에서 무력화된다.
   D-021("조건부 갱신 우선")과 conventions §10.4(경쟁 경로 동시성 테스트 필수)를 이 경로에 적용하지 않았다.

그 외 관리자 로그인 타이밍 부채널, 상태 변경 API의 승인 규칙 우회, 검색어 `-` 입력 시 전체 매칭 등
Warning 5건과 Info 6건이 있다.

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: 동시 최초 로그인 경쟁 복구 코드가 PostgreSQL에서 동작 불가능 — 복구 대신 500

**File:** `src/main/kotlin/com/goldwrestling/member/MemberRegistrationService.kt:38-50`
**Issue:** `findOrCreateByKakaoId`는 `@Transactional` 메서드 **안에서** `saveAndFlush`의
`DataIntegrityViolationException`을 잡고 같은 트랜잭션으로 `findByKakaoId`를 재시도한다.
이 복구 경로는 두 가지 이유로 절대 성공할 수 없다:

1. **PostgreSQL은 제약 위반이 난 순간 트랜잭션을 abort한다.** 이후 같은 트랜잭션의 모든 SQL은
   `ERROR: current transaction is aborted, commands ignored until end of transaction block`으로 실패한다.
   즉 catch 블록의 `memberRepository.findByKakaoId(kakaoId)` 자체가 예외를 던진다.
2. 설령 DB가 허용하더라도, 예외가 리포지토리 프록시(`SimpleJpaRepository`의 `@Transactional`) 경계를
   통과하는 순간 Spring이 공유 트랜잭션을 **rollback-only로 마킹**하므로, 바깥 경계 커밋 시
   `UnexpectedRollbackException`으로 실패한다.

결과: T-02-22가 목표한 "중복 클릭·재시도 시 다른 요청이 만든 회원을 반환" 대신, 경쟁이 실제로 나면
사용자는 `INTERNAL_ERROR` 500을 받는다. KDoc(31-36행)의 설명은 실제 동작과 다르며, 이 경로를 검증하는
테스트(동시성 테스트, conventions §10.4)도 없어서 어긋남이 드러나지 않았다.

**Fix:** 재시도를 **트랜잭션 경계 밖**으로 옮긴다. `KakaoAuthService.login`(트랜잭션 없음)이 잡고
새 트랜잭션으로 다시 호출하게 하는 것이 가장 단순하다:

```kotlin
// MemberRegistrationService — 트랜잭션 안 catch 제거, 예외는 그대로 전파
@Transactional
fun findOrCreateByKakaoId(kakaoId: Long): MemberSessionResponse {
    memberRepository.findByKakaoId(kakaoId)?.let { return MemberSessionResponse.from(it) }
    return MemberSessionResponse.from(createPendingMember(kakaoId))
}

// KakaoAuthService.login — 트랜잭션 밖에서 1회 재시도 (두 번째 호출은 새 트랜잭션에서 기존 회원을 찾는다)
val session =
    try {
        memberRegistrationService.findOrCreateByKakaoId(kakaoId)
    } catch (e: DataIntegrityViolationException) {
        memberRegistrationService.findOrCreateByKakaoId(kakaoId)
    }
```

수정 후 `ExecutorService` + `CountDownLatch`로 같은 kakaoId 동시 등록 테스트를 추가해
"회원 1명 + 두 요청 모두 200"을 증명해야 한다(conventions §10.4).

## Warnings

### WR-01: refresh 회전의 폐기가 원자적이지 않음 — 동시 제시 시 재사용 감지 우회 (TOCTOU)

**File:** `src/main/kotlin/com/goldwrestling/auth/TokenService.kt:100-123`, `src/main/kotlin/com/goldwrestling/auth/RefreshToken.kt:48-52`
**Issue:** `rotate()`는 행을 읽고(`findByTokenHash`) → 메모리에서 `isRevoked()` 판단 → 더티체킹으로
`revokedAt`을 채운다. READ COMMITTED에서 같은 refresh 토큰이 동시에 두 번 제시되면 두 트랜잭션 모두
미폐기 상태를 읽고 둘 다 회전에 성공한다 — 토큰 하나에서 유효한 refresh 두 개가 파생되고,
D-036이 명시한 재사용 감지("이미 폐기된 토큰 재제시 → 전체 폐기")가 **정확히 탈취 시나리오**
(공격자와 피해자가 같은 토큰을 근접 시점에 사용)에서 발동하지 않는다. 이 프로젝트의 동시성 원칙
(D-021: 조건부 갱신 우선)과 conventions §10.4(경쟁 경로 동시성 테스트)가 이 경로에 적용되지 않았고,
`RefreshTokenRotationTest`도 순차 시나리오만 검증한다.
**Fix:** 폐기를 조건부 UPDATE로 바꾸고 갱신 행 수 0을 재사용 신호로 취급한다:

```kotlin
// RefreshTokenRepository
@Modifying(clearAutomatically = true)
@Query("update RefreshToken rt set rt.revokedAt = :now where rt.id = :id and rt.revokedAt is null")
fun revokeIfUsable(id: Long, now: OffsetDateTime): Int

// TokenService.rotate — existing.revoke(now) 대신
if (refreshTokenRepository.revokeIfUsable(existing.id!!, now) == 0) {
    revokeAllUsableFor(existing, now)   // 경쟁에서 진 쪽 = 재사용으로 간주
    throw RefreshTokenInvalidException()
}
```

동시 회전 테스트(스레드 2개, 성공 1건·401 1건·최종 미폐기 행 1개)를 함께 추가한다.

### WR-02: 관리자 로그인의 계정 열거 방지가 타이밍 부채널로 우회 가능

**File:** `src/main/kotlin/com/goldwrestling/auth/AdminAuthService.kt:38-42`
**Issue:** `admin == null || !passwordEncoder.matches(...)`는 단락 평가라, loginId가 존재하지 않으면
BCrypt 검증(수십~수백 ms)이 아예 실행되지 않는다. 응답 메시지는 통일했지만(T-02-24) 응답 **시간**이
"존재하는 loginId"와 "없는 loginId"를 구분해 준다 — KDoc이 주장하는 계정 열거 방지가 절반만 구현됐다.
관리자 계정이 소수라 실익이 크진 않지만, 명시적으로 세운 보안 통제가 뚫려 있는 상태다.
**Fix:** loginId 미존재 시에도 더미 해시에 대해 `matches`를 1회 수행해 시간을 균일화한다:

```kotlin
val admin = adminRepository.findByLoginId(loginId)
val matched = passwordEncoder.matches(rawPassword, admin?.passwordHash ?: DUMMY_BCRYPT_HASH)
if (admin == null || !matched) { ... throw InvalidCredentialsException() }
// DUMMY_BCRYPT_HASH = 기동 시 passwordEncoder.encode("dummy")로 만든 상수 (실값 하드코딩 아님)
```

### WR-03: `PATCH /status`로 온보딩 미완료 회원을 ACTIVE로 만들 수 있음 — 승인 규칙 우회

**File:** `src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt:114-131`
**Issue:** `approve()`는 "온보딩 완료 + PENDING"을 서버에서 강제하며, 그 근거로 "목록 필터는 화면 노출
범위일 뿐이라 API 직접 호출로 우회 가능하므로 정책은 항상 서버에서 강제한다"(T-02-37)를 명시했다.
그런데 같은 파일의 `changeStatus()`는 `newStatus=ACTIVE`에 아무 검사가 없어, **이름·전화번호가 없는
회원을 ACTIVE로 만드는 우회 경로**가 열려 있다. 관리자는 오프라인에서 회원을 이름·전화번호로
식별한다(D-025)는 전제가 깨진 ACTIVE 회원이 생길 수 있다. "상태 전이 제약 없음"이 의도된 결정이라 해도,
approve가 막는 조건을 같은 리소스의 다른 엔드포인트가 열어 주는 것은 T-02-37의 논리와 정면으로 모순된다.
**Fix:** `changeStatus`에서 `newStatus == ACTIVE && !member.isOnboardingCompleted()`이면
`MemberStateConflictException`을 던진다(그 외 전이는 현행대로 자유). 의도적으로 허용하는 것이라면
docs/decisions.md에 그 판단을 기록해 T-02-37과의 모순을 해소해야 한다.

### WR-04: 검색어가 하이픈만으로 이루어지면 전화번호가 있는 전 회원이 매칭됨

**File:** `src/main/kotlin/com/goldwrestling/member/MemberSpecifications.kt:31-52`
**Issue:** `keywordContains`는 와일드카드 이스케이프로 "검색어 하나로 전체 회원 매칭"을 막았다고
주장하지만(T-02-34), 검색어 `"-"`(또는 `"--"` 등)는 blank가 아니라 통과한 뒤
`PhoneNumberNormalizer.normalize("-")` → `""`가 되어 전화번호 조건이 `LIKE '%%'`가 된다 —
전화번호가 non-null인 **모든 회원**이 반환된다. `MemberSpecificationTest`는 `%`만 검증하고 이 경로가 없다.
**Fix:** 정규화 결과가 빈 문자열이면 전화번호 술어를 만들지 않는다:

```kotlin
val normalizedPhone = PhoneNumberNormalizer.normalize(trimmed)
// Specification 내부에서
val predicates = buildList {
    add(namePredicate)
    if (normalizedPhone.isNotEmpty()) add(phonePredicate)
}
criteriaBuilder.or(*predicates.toTypedArray())
```

하이픈만으로 된 검색어 테스트 케이스를 함께 추가한다.

### WR-05: 온보딩 완료 판정이 엔티티와 Specification에서 공백 문자열에 대해 어긋남

**File:** `src/main/kotlin/com/goldwrestling/member/MemberSpecifications.kt:71-87`, `src/main/kotlin/com/goldwrestling/member/Member.kt:54`
**Issue:** `Member.isOnboardingCompleted()`는 `isNullOrBlank()`(공백만 있는 문자열 = 미완료)를 쓰지만,
`MemberSpecifications.onboardingCompleted`는 `notEqual(..., "")`(빈 문자열만 미완료)를 쓴다.
`name = " "`인 행이 생기면 엔티티는 미완료, 승인 대기 목록 쿼리는 완료로 판정해 policies §5.1의
승인 목록이 어긋난다. KDoc은 "반드시 같은 판정 규칙"이라고 명시했지만 실제로는 다르고, 정합성을
지킨다는 `MemberSpecificationTest`의 픽스처(m6)는 `""`만 있고 `" "` 케이스가 없어 어긋남을 잡지 못한다.
현재 쓰기 경로(온보딩이 trim + `@NotBlank`)로는 공백 문자열이 들어가지 않지만, v2 프로필 수정(PROF-01)
등 새 쓰기 경로가 생기는 순간 잠복 버그가 현실화된다.
**Fix:** Specification에서 `criteriaBuilder.trim(root.get<String>("name"))`과 `""`를 비교하도록 바꿔
blank 의미론을 일치시키고, `name = " "` 픽스처를 정합성 테스트에 추가한다.

### WR-06: openapi.yaml의 회원 목록 쿼리 파라미터가 단일 `condition` 객체로 표현됨 — FE 생성 클라이언트 직렬화 위험

**File:** `docs/api/openapi.yaml:214-219`, `src/main/kotlin/com/goldwrestling/member/AdminMemberController.kt:40-42`
**Issue:** 서버는 `keyword`·`status`·`page`·`size`를 평평한 쿼리 파라미터로 바인딩하지만, 생성된 계약은
`name: condition, in: query, schema: $ref MemberSearchCondition` **객체 파라미터 1개**로 표현됐다.
OpenAPI의 기본 `style: form, explode: true` 해석으로는 등가지만, FE가 쓰는 생성기·클라이언트에 따라
객체 파라미터를 `deepObject`(`condition[keyword]=...`)나 JSON 문자열로 직렬화하는 구현이 흔해
(예: openapi-fetch의 객체 기본 직렬화), 그렇게 되면 서버가 파라미터를 전혀 바인딩하지 못한다.
openapi.yaml이 FE와의 유일한 계약(D-013, CLAUDE.md 규칙 4)인 프로젝트에서 해석이 갈리는 표현은 위험하다.
**Fix:** 컨트롤러 파라미터에 springdoc의 `@ParameterObject`를 붙여(`@ParameterObject @Valid condition:
MemberSearchCondition`) `keyword`/`status`/`onboardingCompleted`/`page`/`size`가 개별 쿼리 파라미터로
펼쳐지게 하고 `./gradlew generateApiDocs`로 재생성한다. FE 쪽 실제 직렬화 확인 후 문제가 없다고
판단되면 그 확인 결과를 기록한다.

## Info

### IN-01: `submitOnboarding`의 동시 재제출 방어 주석이 실제 보장을 과장함

**File:** `src/main/kotlin/com/goldwrestling/member/MemberProfileService.kt:46-51`
**Issue:** 주석은 "엔티티 기준 재검사가 두 번째 요청의 덮어쓰기를 막는다"고 하지만, 두 트랜잭션이 모두
커밋 전의 미완료 상태를 읽으면(READ COMMITTED) 둘 다 검사를 통과해 last-write-wins가 된다. 본인 데이터를
본인이 덮는 것이라 실질 피해는 없지만, 주석이 없는 보장을 주장하고 있어 후속 작업자가 오판할 수 있다.
**Fix:** 주석을 "순차 재제출은 막고, 완전 동시 제출은 last-write-wins로 수용한다(피해 없음)"로 정정하거나,
엄밀히 막으려면 `WHERE name IS NULL` 조건부 UPDATE로 바꾼다.

### IN-02: AdminSeeder는 두 인스턴스 동시 기동 시 한쪽이 기동 실패

**File:** `src/main/kotlin/com/goldwrestling/admin/AdminSeeder.kt:49-66`
**Issue:** `existsByLoginId` → `save` 사이 경쟁에서 `uq_admin_login_id`가 두 번째 INSERT를 거부하면
`ApplicationRunner` 예외로 그 인스턴스의 기동 자체가 실패한다. 현재 단일 인스턴스(EC2 1대) 배포라
실해는 없지만, KDoc이 "두 인스턴스가 동시에 기동해도 중복 생성 여지가 최소화된다"고 언급하는 만큼
스케일아웃 시점에 함정이 된다.
**Fix:** `save`를 `try-catch(DataIntegrityViolationException)`로 감싸 "다른 인스턴스가 먼저 생성"
로그만 남기고 정상 기동하게 한다.

### IN-03: Bearer 스킴 비교가 대소문자 구분

**File:** `src/main/kotlin/com/goldwrestling/auth/JwtAuthenticationFilter.kt:80-84`
**Issue:** RFC 9110/7235상 인증 스킴은 대소문자 무관이다. `header.startsWith("Bearer ")`는 `bearer x`를
거부한다. 자사 FE만 쓰는 API라 실해는 없지만 표준 클라이언트와의 상호운용 저하.
**Fix:** `header.regionMatches(0, "Bearer ", 0, 7, ignoreCase = true)` 방식으로 비교.

### IN-04: 로그인 실패 로그에 사용자 입력 loginId를 그대로 기록

**File:** `src/main/kotlin/com/goldwrestling/auth/AdminAuthService.kt:40`
**Issue:** `logger.warn("관리자 로그인 실패: loginId={}", loginId)` — loginId는 `@Size(max=50)` 외에
문자 제한이 없어 CR/LF를 넣은 로그 라인 위조(log forging)나 로그 오염이 가능하다.
**Fix:** 기록 전 개행 제거(`loginId.replace(Regex("[\\r\\n]"), "_")`) 또는 logback 인코더에서 개행 이스케이프.

### IN-05: 카카오 설정 미주입이 기동 시점이 아니라 첫 로그인의 오해석된 401로 드러남

**File:** `src/main/kotlin/com/goldwrestling/config/KakaoProperties.kt:17-23`
**Issue:** JWT 시크릿은 `JwtConfig`가 기동 시 검증해 즉시 실패하지만, `restApiKey`/`clientSecret`/
`redirectUri`는 빈 값이어도 그대로 기동한다. 미설정 상태의 첫 카카오 로그인은 카카오 4xx →
`KAKAO_AUTH_FAILED`(401)로 나가 "사용자 코드 문제"처럼 보인다 — 운영 설정 오류가 사용자 오류로 위장된다.
**Fix:** `KakaoRestClientConfig`(또는 `@PostConstruct`)에서 세 값이 blank면 JwtConfig와 같은 방식으로
기동을 막거나 최소한 명시적 경고 로그를 남긴다.

### IN-06: refresh 회전마다 만료가 14일로 재설정 — 사실상 무기한 세션

**File:** `src/main/kotlin/com/goldwrestling/auth/TokenService.kt:54`
**Issue:** 회전 시 새 refresh가 항상 `now + 14일`을 받으므로, 14일 안에 한 번이라도 접속하는 회원의
세션은 영구히 연장된다(sliding expiration). D-033은 절대 수명 상한을 정하지 않았으므로 위반은 아니지만,
"refresh 14일"이라는 문구가 주는 인상(최대 14일)과 실동작(마지막 사용 후 14일)이 다르다.
**Fix:** 의도된 동작이면 D-033에 "sliding, 절대 상한 없음"을 한 줄 추가. 상한을 원하면 최초 발급 시각을
회전 체인에 전파해 절대 만료를 함께 검사한다.

---

_Reviewed: 2026-08-02T23:57:10Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
