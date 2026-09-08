---
phase: 2
slug: auth-member
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-08-02
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit5 (Kotlin) + MockMvc(`spring-boot-starter-webmvc-test`) + Testcontainers(`testcontainers-postgresql`) — Phase 1 구성 그대로 |
| **Config file** | `build.gradle.kts` (`tasks.withType<Test>`), `src/test/kotlin/.../TestcontainersConfiguration.kt` |
| **Quick run command** | `./gradlew test --tests "com.goldwrestling.auth.*"` (신규 패키지만) |
| **Full suite command** | `./gradlew build` (ktlintFormat 이후 — conventions §11.5) |
| **Estimated runtime** | 통합테스트 포함 ~90초 (Testcontainers 기동 포함) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "*<변경 클래스>*"` (해당 태스크가 건드린 클래스만)
- **After every plan wave:** Run `./gradlew build` (ktlintFormat 포함 전체)
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~120초

---

## Per-Task Verification Map

> 요구사항→테스트 매핑은 실제 실행 결과 기준으로 확정됨 (02-11 Task 2, 2026-08-03).

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 02-06 T2 | 02-06 | 4 | AUTH-01 | T-02-22 | 카카오 로그인 최초 가입 시 `PENDING` 생성 | 통합(카카오 목킹) | `./gradlew test --tests "*KakaoAuthControllerTest*"` | `src/test/kotlin/com/goldwrestling/auth/KakaoAuthControllerTest.kt` | ✅ green |
| 02-04 T1·T2 | 02-04 | 2 | AUTH-02 | T-02-10 | access/refresh 발급·refresh 갱신 | 통합 | `./gradlew test --tests "*TokenControllerTest*"` | `src/test/kotlin/com/goldwrestling/auth/TokenControllerTest.kt` | ✅ green |
| 02-07 T1·T2 | 02-07 | 5 | AUTH-03 | T-02-25 | 관리자 ID/PW 로그인 (BCrypt) | 통합 | `./gradlew test --tests "*AdminAuthControllerTest*" --tests "*AdminSeederTest*"` | `src/test/kotlin/com/goldwrestling/auth/AdminAuthControllerTest.kt`, `src/test/kotlin/com/goldwrestling/admin/AdminSeederTest.kt` | ✅ green |
| 02-05 T3, 02-08 T1, 02-10 T2 | 02-05/02-08/02-10 | 3/6/8 | AUTH-04 | T-02-14 | `PENDING`/상태 변경 회원 접근 제한 (DB 상태 기준 재조회) | 통합 | `./gradlew test --tests "*SecurityFilterChainTest*" --tests "*MemberStateGateTest*" --tests "*MemberStatusChangeTest*"` | `src/test/kotlin/com/goldwrestling/config/SecurityFilterChainTest.kt`, `src/test/kotlin/com/goldwrestling/member/MemberStateGateTest.kt`, `src/test/kotlin/com/goldwrestling/member/MemberStatusChangeTest.kt` | ✅ green |
| 02-08 T1·T2 | 02-08 | 6 | AUTH-05 | T-02-30 | 온보딩 형식 검증(전화번호 정규식) | 단위 | `./gradlew test --tests "*OnboardingValidationTest*" --tests "*MemberProfileTest*"` | `src/test/kotlin/com/goldwrestling/member/OnboardingValidationTest.kt`, `src/test/kotlin/com/goldwrestling/member/MemberProfileTest.kt` | ✅ green |
| 02-02 T3, 02-08 T2 | 02-02/02-08 | 1/6 | AUTH-06 | — (정합성 요구사항, STRIDE 위협 항목 없음) | 온보딩 미완료 재로그인 재식별 | 단위+통합 | `./gradlew test --tests "*MemberOnboardingStatusTest*" --tests "*MemberProfileTest*"` | `src/test/kotlin/com/goldwrestling/member/MemberOnboardingStatusTest.kt`, `src/test/kotlin/com/goldwrestling/member/MemberProfileTest.kt` | ✅ green |
| 02-09 T1, 02-10 T1 | 02-09/02-10 | 7/8 | MEMBER-01 | T-02-37 | 승인 목록 = 온보딩 완료 `PENDING`만 | 통합(Testcontainers) | `./gradlew test --tests "*MemberSpecificationTest*" --tests "*MemberApprovalTest*"` | `src/test/kotlin/com/goldwrestling/member/MemberSpecificationTest.kt`, `src/test/kotlin/com/goldwrestling/member/MemberApprovalTest.kt` | ✅ green |
| 02-09 T2 | 02-09 | 7 | MEMBER-02 | T-02-34 | 이름·전화번호 부분 일치 검색 | 통합 | `./gradlew test --tests "*MemberSearchTest*"` | `src/test/kotlin/com/goldwrestling/member/MemberSearchTest.kt` | ✅ green |
| 02-10 T2 | 02-10 | 8 | MEMBER-03 | T-02-38 | 상태 변경 시 refresh 무효화(강제 로그아웃) | 통합 | `./gradlew test --tests "*MemberStatusChangeTest*"` | `src/test/kotlin/com/goldwrestling/member/MemberStatusChangeTest.kt` | ✅ green |
| 02-08 T2 | 02-08 | 6 | MEMBER-04 | T-02-29 | 본인 프로필 조회 | 통합 | `./gradlew test --tests "*MemberProfileTest*"` | `src/test/kotlin/com/goldwrestling/member/MemberProfileTest.kt` | ✅ green |
| 02-04 T1 | 02-04 | 2 | (D-033 회전) | T-02-10 | refresh 회전 시 이전 토큰 재사용 감지 | 단위+통합 | `./gradlew test --tests "*RefreshTokenRotationTest*"` | `src/test/kotlin/com/goldwrestling/auth/RefreshTokenRotationTest.kt` | ✅ green |
| 02-05 T3 | 02-05 | 3 | (D-017 회귀) | T-02-16 | 만료/위조 토큰 → `application/problem+json` 401 | 통합 | `./gradlew test --tests "*JwtAuthenticationFilterTest*"` | `src/test/kotlin/com/goldwrestling/auth/JwtAuthenticationFilterTest.kt` | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**확정 근거:** `./gradlew ktlintFormat && ./gradlew build`(2026-08-03, 이 워크트리) — 200개 테스트 전체 통과(실패 0). 위 표의 각 `Automated Command`는 개별 실행으로도 재확인했다(전체 스위트에 포함된 결과와 함께, 3개 이상 표본 직접 재실행: `TokenControllerTest`·`MemberStatusChangeTest`·`RefreshTokenRotationTest` 전부 green). `AdminSeederTest`는 02-07 SUMMARY에 따라 시드 값이 채워진 별도 `@SpringBootTest(properties=[...])` 컨텍스트로 검증한다.

---

## Wave 0 Requirements

- [x] `src/test/kotlin/com/goldwrestling/support/` — 시각 의존·카카오 목킹 공용 테스트 인프라 디렉터리 생성 (02-03) — `MutableTestClock.kt`, `MutableTestClockTest.kt`, `TestClockConfiguration.kt`, `KakaoApiMockSupport.kt` 4개 파일 확인
- [x] `src/test/resources/kakao/` — 카카오 API 응답 픽스처(JSON) 고정 (02-03) — `token-response.json`, `user-response.json` 2개 파일 확인
- [x] 고정 `Clock` 테스트 구성 (`@TestConfiguration` 기반) — Phase 1에 선례 없는 첫 시각 의존 로직 (02-03) — `TestClockConfiguration.kt:24`의 `@TestConfiguration(proxyBeanMethods = false)` + `testClock`/`@Primary` 빈(D-049) 확인
- [x] `MockRestServiceServer` 기반 카카오 목킹 공용 헬퍼 (02-03) — `KakaoApiMockSupport.kt` 확인, `KakaoApiClientTest`(11건)·`KakaoAuthControllerTest`(10건)가 재사용

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions | Result |
|----------|-------------|------------|-------------------|--------|
| 실제 카카오 개발자 콘솔 연동(redirect_uri·client_secret 실값) | AUTH-01 | 실 카카오 계정·콘솔 설정은 자동화 불가 (테스트는 목킹으로 대체) | 로컬 `.env`에 실값 설정 후 브라우저로 카카오 로그인 플로우 1회 수행 | **완료 — 2026-08-03.** 실제 카카오 계정으로 로그인→온보딩→관리자 승인→강제 로그아웃 전 구간을 사람이 확인했다(02-11 Task 3). ①카카오 로그인 200, `memberId=1`, `PENDING`, 토큰 발급 ②온보딩 200, `onboardingCompleted=true`, `01012345678` 정규화 확인 ③관리자 로그인 — **최초 500 발생, 실제 버그 발견**(아래 참고), 수정 후 200 ④승인 대기 목록 200, 해당 회원 포함 ⑤승인 200, `ACTIVE` ⑥본인 조회 200, `ACTIVE`/`rejected=false` ⑦`ON_LEAVE` 전환 후 refresh 시도 401 + `application/problem+json` + `REFRESH_TOKEN_INVALID`. 카카오 콘솔 설정(redirect_uri·client_secret)이 실제로 유효함을 이 단계에서 최초로 증명했다 |

**수동 검증 중 발견·수정된 버그(1건):** 관리자 ID/PW 로그인(`POST /api/auth/admin/login`)이 실제 서버에서 500으로 실패했다. 원인: `AdminAuthService.login`이 클래스 기본 `@Transactional(readOnly = true)`를 오버라이드하지 않아, 그 안에서 호출하는 `TokenService.issueTokenPair`(전파 REQUIRED)가 같은 읽기 전용 트랜잭션에 합류했고, `refresh_token` INSERT가 "cannot execute INSERT in a read-only transaction"으로 DB에서 거부되었다. 기존 통합테스트(`AdminAuthControllerTest`)는 테스트 트랜잭션 자체가 쓰기 가능이라 이 문제를 가려 통과했었다 — 실제 `bootRun`으로 띄운 서버에서만 드러난, 목킹·테스트 트랜잭션으로는 잡을 수 없었던 종류의 결함이다. 메인 워크트리(`feature/phase-02-auth-member`)에서 `login`에 `@Transactional` 오버라이드를 추가하고 `NOT_SUPPORTED` 회귀 테스트를 더해 수정, 전체 빌드·테스트 통과를 확인한 뒤 커밋 `05241f5`(`fix(02-07)`)로 반영했다. 이 워크트리는 문서 산출물만 다루므로 코드는 재수정하지 않는다.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies — Per-Task Verification Map 전 행 실제 테스트 파일·명령으로 확정 (위)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify — Phase 2의 모든 태스크가 `./gradlew test`로 검증됨 (02-01~02-10 SUMMARY 확인)
- [x] Wave 0 covers all MISSING references — 위 Wave 0 Requirements 4건 전부 실제 파일로 충족 확인
- [x] No watch-mode flags — 이 phase는 watch 모드를 쓰지 않음
- [x] Feedback latency < 120s — `./gradlew build`(Testcontainers 포함) 실측 25초, 목표(120초) 이내
- [x] `nyquist_compliant` set to `true` in frontmatter — 위 frontmatter 반영

**자동화 검증 (Task 1·2):** 완료 — 2026-08-03, `./gradlew ktlintFormat && ./gradlew build` 200개 테스트 전체 통과, `openapi.yaml` 드리프트 없음, ErrorCode↔문서·glossary↔코드 정합 확인.

**수동 검증 (Task 3, 실제 카카오 연동):** 완료 — 2026-08-03. 실제 카카오 계정으로 로그인→온보딩→관리자 승인→강제 로그아웃 전 구간을 사람이 확인했다(위 Manual-Only Verifications 표 참고). 이 과정에서 목킹 테스트로는 발견할 수 없었던 관리자 로그인 트랜잭션 버그 1건을 실제로 잡아냈고, 메인 트리에 수정·커밋(`05241f5`)했다.

**Approval:** Task 1·2·3 전부 승인 — 2026-08-03. Phase 2(auth-member) 검증 계약이 실제 결과로 확정되었다.
