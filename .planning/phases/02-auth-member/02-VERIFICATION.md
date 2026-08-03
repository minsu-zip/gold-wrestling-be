---
phase: 02-auth-member
verified: 2026-08-03T00:03:27Z
status: gaps_found
score: 4/5 must-haves verified (ROADMAP Success Criteria 기준)
overrides_applied: 0
gaps:
  - truth: "회원이 카카오 OAuth로 로그인하면 PENDING 상태로 가입되고 JWT access/refresh 토큰이 발급된다 (동시 최초 로그인 포함)"
    status: partial
    reason: "순차 로그인은 정상 동작하고 사람이 실제 카카오 계정으로 E2E 확인까지 마쳤다. 그러나 같은 카카오 계정이 거의 동시에 두 번 로그인을 시도하는 경쟁 상황(중복 클릭·네트워크 재시도)에서, MemberRegistrationService.findOrCreateByKakaoId의 복구 코드가 PostgreSQL 트랜잭션 의미론상 절대 성공할 수 없는 경로다. 유니크 제약 위반 시 트랜잭션이 abort되므로 catch 블록의 재조회 자체가 예외를 던지고, 리포지토리 프록시 경계를 넘는 순간 스프링이 트랜잭션을 rollback-only로 마킹해 커밋 시 UnexpectedRollbackException이 난다. 결과적으로 경쟁이 실제로 발생하면 사용자는 200 대신 500(INTERNAL_ERROR)을 받는다. 코드 KDoc(31-36행)과 02-06-PLAN.md의 must_have(\"같은 카카오 계정으로 다시 로그인하면 새 회원이 생기지 않고 기존 회원으로 로그인된다\")가 주장하는 동작과 실제 동작이 다르다."
    artifacts:
      - path: "src/main/kotlin/com/goldwrestling/member/MemberRegistrationService.kt"
        issue: "38-50행: @Transactional 메서드 안에서 DataIntegrityViolationException을 잡고 같은 트랜잭션으로 재조회 — PostgreSQL에서 구조적으로 성공 불가 (02-REVIEW.md CR-01, 재현 조건·수정안 포함)"
    missing:
      - "재시도를 트랜잭션 경계 밖(KakaoAuthService.login, @Transactional 없음)으로 옮기고 새 트랜잭션으로 재호출"
      - "ExecutorService + CountDownLatch로 같은 kakaoId 동시 등록 테스트 추가 — '회원 1명 + 두 요청 모두 200' 증명 (conventions.md §10.4)"
deferred: []
human_verification: []
---

# Phase 2: 인증·회원 Verification Report

**Phase Goal:** 회원이 카카오로 가입해 온보딩·관리자 승인을 거쳐 활성 상태가 되고, 관리자는 ID/PW로 로그인해 회원을 관리할 수 있다 — 카카오 로그인·JWT 인증·관리자 인증·온보딩·회원 관리(승인/거절/상태 변경/조회)가 실제 코드로 동작하고 정합성이 검증된 상태.
**Verified:** 2026-08-03T00:03:27Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## 검증 방법 요약

SUMMARY.md·VALIDATION.md의 서술을 그대로 신뢰하지 않고 다음을 직접 확인했다.

1. `./gradlew test --rerun`을 직접 실행 — 201개 테스트, 실패 0건 (캐시가 아니라 강제 재실행으로 재확인)
2. `git status --short docs/api/openapi.yaml` — 드리프트 없음(clean)
3. 11개 플랜 PLAN.md의 `must_haves` 전체와 ROADMAP.md Success Criteria 5개를 코드와 대조
4. 02-REVIEW.md(코드 리뷰, Critical 1 · Warning 6 · Info 6)의 각 finding을 실제 소스로 재현 확인 — Critical 1건(CR-01)이 리뷰 이후에도 수정되지 않은 채 남아 있음을 `MemberRegistrationService.kt` 원본으로 확인
5. 동시성 테스트 존재 여부를 `grep -rl "ExecutorService\|CountDownLatch\|Thread("` 로 전수 조사 — 0건
6. SecurityConfig의 `permitAll` 뼈대가 역할 기반 규칙으로 실제 교체되었는지 소스 확인
7. 인증 관련 anti-pattern(TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER, stub return) 스캔 — phase 2에서 변경된 104개 파일 전수

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria 기준)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | 회원이 카카오 OAuth로 로그인하면 `PENDING` 상태로 가입되고 JWT access/refresh 토큰이 발급되며, refresh로 access를 갱신할 수 있다 | ⚠️ 부분 충족 | 순차 흐름은 `KakaoAuthController`→`KakaoAuthService`→`MemberRegistrationService`→`TokenService`로 완전히 배선되어 있고, `KakaoAuthControllerTest`(10건)·`TokenControllerTest`·실제 카카오 계정 E2E(02-11 Task 3, 사람 확인)로 검증됨. **그러나** 동시 최초 로그인 경쟁 복구 경로는 PostgreSQL에서 구조적으로 동작 불가 — CR-01 참고, 아래 Gaps 참고 |
| 2 | 최초 로그인 회원은 온보딩 대상으로 식별되어 실명·전화번호를 필수 입력해야 하고(형식 검증 포함), 온보딩 미완료 상태로 재로그인해도 다시 온보딩 대상으로 식별된다 | ✓ VERIFIED | `MemberProfileService.submitOnboarding` + `OnboardingValidationTest`(전화번호 정규식 7건) + `MemberOnboardingStatusTest` + `MemberProfileTest`. `Member.isOnboardingCompleted()`가 이름·전화번호 존재 여부로 매 요청 판정(별도 상태 컬럼 없음, D-025/D-034) |
| 3 | 관리자 승인 목록에는 온보딩을 완료한 `PENDING` 회원만 노출되고, 관리자가 승인하면 `ACTIVE`로 전환되어 전체 기능을 쓸 수 있다 (거절도 가능) | ✓ VERIFIED (정책 우회 경로 1건은 WARNING으로 별도 기록) | `AdminMemberService.approve()`가 `PENDING` 상태 + `isOnboardingCompleted()` 서버측 강제 검사를 명시적으로 수행(70-75행). `reject()`는 `INACTIVE` 전환+사유 기록+`revokeAllForMember` 같은 트랜잭션. `MemberApprovalTest`로 검증. 단, `changeStatus()` API로 온보딩 미완료 회원을 직접 `ACTIVE`로 전환하는 우회 경로가 열려 있음(WR-03, 아래 Warning 참고) — 승인 목록에는 노출되지 않지만 상태 변경 엔드포인트로는 규칙이 강제되지 않는다 |
| 4 | 관리자는 ID/PW로 로그인해(카카오 연동 없음, 회원과 동일한 JWT 체계) 회원 목록·상세를 조회하고 이름·전화번호로 검색하며, 회원 상태(`ACTIVE`/`ON_LEAVE`/`INACTIVE`)를 변경할 수 있다 | ✓ VERIFIED | `AdminAuthController`/`AdminAuthService`(BCrypt, 02-07 실제 500 버그가 발견되어 커밋 `05241f5`로 수정됨 — 아래 참고), `AdminMemberService.search/getDetail/changeStatus`, `MemberSpecifications`(검색어+상태+온보딩완료 동적 조합). `MemberSearchTest`·`MemberStatusChangeTest`·`AdminAuthControllerTest`로 검증. 하이픈만 입력 시 검색 조건이 무력화되는 엣지케이스는 WR-04로 별도 기록(전체 기능 자체는 동작) |
| 5 | `PENDING`이거나 온보딩 미완료인 회원은 승인 대기 정보 외 기능에 접근할 수 없고, 회원은 본인 프로필(이름·전화번호)을 조회할 수 있다 | ✓ VERIFIED | `MemberStateGate.requireActive()`(DB 재조회 없이 매 요청 필터가 채운 `AuthenticatedPrincipal`로 판정, D-033) + `MemberProfileController`(GET `/api/members/me`는 PENDING도 허용) + `MemberStateGateTest`·`MemberProfileTest` |

**Score:** 4/5 truths 완전 충족, 1개는 부분 충족(핵심 경로는 동작하지만 동시성 경쟁 경로에 증명된 500 결함)

### 관리자 로그인 트랜잭션 버그 — 검증 중 발견·수정 확인

02-11 실제 카카오 E2E 검증 중 `POST /api/auth/admin/login`이 실서버(`bootRun`)에서 500으로 실패한 사실이 기록되어 있었다. 원인은 `AdminAuthService.login`이 클래스 기본 `@Transactional(readOnly = true)`를 오버라이드하지 않아 `TokenService.issueTokenPair`의 `refresh_token` INSERT가 읽기 전용 트랜잭션에서 거부된 것이었다. 커밋 `05241f5`(`fix(02-07)`)로 수정되어 현재 브랜치(`feature/phase-02-auth-member`)에 포함되어 있음을 `git log`로 직접 확인했다. 이 버그는 이미 해결되어 있어 별도 gap으로 잡지 않았다.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/com/goldwrestling/auth/kakao/KakaoApiClient.kt` | 카카오 토큰 교환+사용자 조회 | ✓ VERIFIED | RestClient 기반, 트랜잭션 밖 호출 |
| `src/main/kotlin/com/goldwrestling/auth/KakaoAuthController.kt` / `KakaoAuthService.kt` | `POST /api/auth/kakao/login` | ✓ VERIFIED | 31줄 컨트롤러, 서비스가 실제 로직 담당, wired |
| `src/main/kotlin/com/goldwrestling/member/MemberRegistrationService.kt` | kakaoId find-or-create | ⚠️ HOLLOW (경쟁 경로만) | 정상 경로는 동작, 경쟁 복구 경로는 PostgreSQL에서 동작 불가 (CR-01) |
| `src/main/kotlin/com/goldwrestling/auth/TokenService.kt` | 발급·회전·폐기(회원·관리자 공용) | ✓ VERIFIED | `issueTokenPair`/`rotate`/`revoke`/`revokeAllFor*` 전부 구현, `RefreshTokenRotationTest`로 순차 시나리오 검증 |
| `src/main/kotlin/com/goldwrestling/auth/JwtAuthenticationFilter.kt` + `SecurityConfig.kt` | Bearer 인증 + 역할 기반 인가 | ✓ VERIFIED | `permitAll` 뼈대가 `hasRole("ADMIN")`/`hasRole("MEMBER")`/`anyRequest().authenticated()`로 실제 교체됨(소스 직접 확인) |
| `src/main/kotlin/com/goldwrestling/auth/AdminAuthController.kt` / `AdminAuthService.kt` | ID/PW 로그인 | ✓ VERIFIED | BCrypt, 시드 연동, 트랜잭션 버그 수정 확인 |
| `src/main/kotlin/com/goldwrestling/member/MemberProfileController.kt` / `MemberProfileService.kt` | 온보딩+본인 프로필 | ✓ VERIFIED | 전화번호 정규화, 상태 게이트 연동 |
| `src/main/kotlin/com/goldwrestling/member/AdminMemberController.kt` / `AdminMemberService.kt` | 목록·검색·상세·승인·거절·상태변경 | ✓ VERIFIED (WR-03/WR-04 엣지케이스 별도 기록) | 6개 엔드포인트 전부 구현·wired |
| `src/main/resources/db/migration/V3__add_auth_credentials_and_refresh_token.sql` | 인증 스키마 | ✓ VERIFIED | `FlywayMigrationIntegrationTest`로 검증 |
| `docs/api/openapi.yaml` | 11개 신규 경로 반영 | ✓ VERIFIED | `git status`로 드리프트 없음 확인(재생성 후 diff 0) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `KakaoAuthService` | `TokenService.issueTokenPair` | 카카오 인증 성공 후 자체 토큰 발급 | ✓ WIRED | 소스 확인 |
| `MemberRegistrationService` | `BranchRepository` | 신규 회원 기본 지점 배정(D-047) | ✓ WIRED | `findByName` 확인 |
| `AdminMemberService.reject/changeStatus` | `TokenService.revokeAllForMember` | 상태 전이 시 강제 로그아웃 | ✓ WIRED | 같은 트랜잭션 참여, `MemberStatusChangeTest`로 검증 |
| `JwtAuthenticationFilter` | `AuthenticationPrincipalResolver` | 매 요청 DB 재조회로 SecurityContext 구성 | ✓ WIRED | D-033 "DB 현재 상태 기준" 구현 확인 |
| `MemberRegistrationService` (경쟁 복구) | `MemberRepository.findByKakaoId` (같은 트랜잭션) | `DataIntegrityViolationException` catch 후 재조회 | ✗ NOT_WIRED (구조적 결함) | PostgreSQL abort 트랜잭션 의미론상 재조회 자체가 실패 (CR-01) |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| 전체 테스트 스위트 green | `./gradlew test --rerun` | BUILD SUCCESSFUL, 201 tests / 0 failures (test-results XML 집계 직접 확인) | ✓ PASS |
| openapi.yaml 드리프트 없음 | `git status --short docs/api/openapi.yaml` | 출력 없음(clean) | ✓ PASS |
| anti-pattern(TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER) | `grep -n -E "TBD\|FIXME\|XXX\|TODO\|HACK\|PLACEHOLDER"` 전체 104개 변경 파일 | 매치 없음 | ✓ PASS |
| 동시성 테스트 존재 여부 | `grep -rl "ExecutorService\|CountDownLatch\|Thread("  src/test` | 매치 없음 (0건) | ✗ FAIL — CR-01 관련 경쟁 경로를 검증하는 테스트가 전무함을 재확인 |
| SecurityConfig permitAll 교체 여부 | 소스 직접 열람 | `/api/admin/**`→`hasRole("ADMIN")`, `/api/members/**`→`hasRole("MEMBER")`, 기본값 거부 | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|--------------|--------|----------|
| AUTH-01 | 02-02, 02-06 | 카카오 OAuth 가입·로그인 (PENDING) | ⚠️ SATISFIED (조건부) | 정상 흐름 검증됨. 동시 최초 로그인 경쟁 시 500 (CR-01) — 위 Gaps 참고 |
| AUTH-02 | 02-03, 02-04, 02-05 | JWT access/refresh 발급·갱신 | ✓ SATISFIED | `TokenControllerTest`, `RefreshTokenRotationTest`(순차), 실제 E2E |
| AUTH-03 | 02-02, 02-07 | 관리자 ID/PW 로그인 | ✓ SATISFIED | `AdminAuthControllerTest`, `AdminSeederTest`, 트랜잭션 버그 수정 확인(`05241f5`) |
| AUTH-04 | 02-05, 02-08, 02-10 | 역할·상태 기반 인가 | ✓ SATISFIED | `SecurityFilterChainTest`, `MemberStateGateTest`, `MemberStatusChangeTest` |
| AUTH-05 | 02-08 | 온보딩(실명·전화번호, 형식 검증) | ✓ SATISFIED | `OnboardingValidationTest`(7건), `MemberProfileTest` |
| AUTH-06 | 02-02, 02-08 | 온보딩 미완료 재로그인 재식별 | ✓ SATISFIED | `MemberOnboardingStatusTest`, `MemberProfileTest` |
| MEMBER-01 | 02-09, 02-10 | 승인/거절 (온보딩 완료 PENDING만 노출) | ⚠️ SATISFIED (엣지케이스 존재) | `MemberApprovalTest`. `approve()`는 서버측 강제 검사 확실. `changeStatus()`로 우회 가능(WR-03) — 별도 Warning |
| MEMBER-02 | 02-09 | 목록·상세 조회, 이름/전화 검색 | ⚠️ SATISFIED (엣지케이스 존재) | `MemberSearchTest`. 하이픈만 입력 시 전체 매칭(WR-04) — 별도 Warning |
| MEMBER-03 | 02-10 | 회원 상태 변경 (ACTIVE/ON_LEAVE/INACTIVE) | ✓ SATISFIED | `MemberStatusChangeTest`, 강제 로그아웃 연동 확인 |
| MEMBER-04 | 02-08 | 본인 프로필 조회 | ✓ SATISFIED | `MemberProfileTest` |

**Orphaned requirements:** 없음 — REQUIREMENTS.md의 Phase 2 매핑(AUTH-01~06, MEMBER-01~04) 10건 전부 어느 한 플랜의 `requirements` 필드에 등장한다.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `src/main/kotlin/com/goldwrestling/member/MemberRegistrationService.kt` | 38-50 | 트랜잭션 경계 오설계 — 절대 성공 불가한 예외 복구 코드(CR-01) | 🛑 Blocker | 동시 최초 로그인 시 500. KDoc이 주장하는 보장과 실제 동작 불일치 |
| `src/main/kotlin/com/goldwrestling/auth/TokenService.kt` | 100-123 | TOCTOU — 조회-판단-더티체킹 방식의 refresh 폐기(WR-01) | ⚠️ Warning | 동시 재사용 시 탈취 감지 무력화 가능 (재현 테스트는 리뷰에서 제안됨, 이번 검증에서는 코드 존재만 재확인) |
| `src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt` | 114-131 | `changeStatus`가 온보딩 미완료 회원의 ACTIVE 전환을 막지 않음(WR-03) | ⚠️ Warning | `approve()`가 강제하는 정책을 같은 리소스의 다른 엔드포인트가 우회 |
| `src/main/kotlin/com/goldwrestling/member/MemberSpecifications.kt` | 31-52 | 하이픈만 있는 검색어 → 전화번호 조건이 `LIKE '%%'`로 무력화(WR-04) | ⚠️ Warning | 검색 정확도 저하 (전체 회원 노출 위험은 관리자 전용 API라 낮음) |
| `src/main/kotlin/com/goldwrestling/auth/AdminAuthService.kt` | 38-42 | 관리자 로그인 타이밍 부채널(WR-02) | ℹ️ Info | 계정 열거 방지가 응답 시간으로 절반만 구현 |
| `src/main/kotlin/com/goldwrestling/member/MemberSpecifications.kt` / `Member.kt` | 71-87 / 54 | 온보딩 완료 판정 blank 의미론 불일치(WR-05) | ℹ️ Info | 현재 쓰기 경로로는 도달 불가, v2 프로필 수정 시 잠복 위험 |
| `docs/api/openapi.yaml` | 214-219 | 회원 목록 쿼리가 단일 `condition` 객체 파라미터로 표현(WR-06) | ⚠️ Warning | FE 생성 클라이언트의 직렬화 방식에 따라 쿼리 파라미터가 전혀 전달되지 않을 위험 |

TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER 문자열 자체는 phase 2가 변경한 104개 파일 전수에서 0건 — "결함 부채 마커"는 없지만, 코드 리뷰가 지적한 **동작이 증명 가능하게 틀린** Critical 1건이 남아 있다.

### Human Verification Required

없음. 02-11 Task 3에서 실제 카카오 계정으로 로그인→온보딩→관리자 승인→상태 변경→강제 로그아웃 전 구간을 사람이 이미 확인했고(02-VALIDATION.md), 그 과정에서 발견된 관리자 로그인 500 버그도 수정·커밋(`05241f5`)되어 반영되어 있다. 남은 문제(CR-01, WR-01~06)는 시각적 확인이 아니라 코드 수정+동시성 테스트가 필요한 항목이라 human_verification이 아니라 gap으로 분류했다.

### Gaps Summary

**핵심 결함 1건(Blocker):** `MemberRegistrationService.findOrCreateByKakaoId`의 동시 최초 로그인 경쟁 복구 코드가 PostgreSQL 트랜잭션 의미론상 절대 성공할 수 없다. 유니크 제약 위반 후 같은 트랜잭션에서 재조회하면 "current transaction is aborted"로 실패하거나, 설령 조회가 됐어도 rollback-only 마킹 때문에 커밋에서 `UnexpectedRollbackException`이 난다. 코드 KDoc과 02-06-PLAN.md의 must_have("같은 카카오 계정으로 다시 로그인하면 새 회원이 생기지 않고 기존 회원으로 로그인된다")가 주장하는 동작이 실제로는 500이다. 이 경로를 검증하는 동시성 테스트도 전무해(전체 코드베이스에 `ExecutorService`/`CountDownLatch` 0건) 지금까지 아무 자동화 검증도 이 결함을 잡지 못했다. 카카오 로그인 버튼 중복 클릭·모바일 네트워크 재시도처럼 실사용에서 충분히 발생 가능한 시나리오이므로 phase 목표("카카오 로그인이 실제 코드로 동작하고 정합성이 검증된 상태")를 완전히 충족했다고 보기 어렵다.

**Warning 5건**은 phase 목표를 완전히 막지는 않지만 정합성·보안 약속과 실제 동작이 어긋나는 지점이다(WR-01 refresh 재사용 감지 TOCTOU, WR-03 승인 규칙 우회, WR-04 검색 엣지케이스, WR-06 openapi 쿼리 파라미터 계약 위험, WR-02는 낮은 우선순위). 이들은 이번 검증에서 gap으로 구조화하지 않고 코드 리뷰(02-REVIEW.md)에 위임했으나, Phase 3 진입 전 처리 여부를 사용자가 판단해야 한다.

**이 리포트가 fix가 아니라 gap으로 남긴 이유:** CR-01은 이미 02-REVIEW.md에 구체적 재현·수정안까지 나와 있음에도 리뷰 이후 커밋(`22ecbcd`, `71cd3f2`)이 문서만 다루고 코드를 고치지 않았다. `/gsd:plan-phase --gaps`로 닫아야 할 실제 작업이 남아 있다.

---

_Verified: 2026-08-03T00:03:27Z_
_Verifier: Claude (gsd-verifier)_
