---
phase: 2
slug: auth-member
status: draft
nyquist_compliant: false
wave_0_complete: false
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

> Task ID는 플랜 생성 후 확정. 요구사항→테스트 매핑은 RESEARCH.md Validation Architecture 기준.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | TBD | TBD | AUTH-01 | TBD | 카카오 로그인 최초 가입 시 `PENDING` 생성 | 통합(카카오 목킹) | `./gradlew test --tests "*KakaoAuthController*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | AUTH-02 | TBD | access/refresh 발급·refresh 갱신 | 통합 | `./gradlew test --tests "*TokenController*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | AUTH-03 | TBD | 관리자 ID/PW 로그인 (BCrypt) | 통합 | `./gradlew test --tests "*AdminAuthController*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | AUTH-04 | TBD | `PENDING` 회원 접근 제한 (DB 상태 기준) | 통합 | `./gradlew test --tests "*SecurityFilterChain*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | AUTH-05 | TBD | 온보딩 형식 검증(전화번호 정규식) | 단위 | `./gradlew test --tests "*OnboardingValidation*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | AUTH-06 | TBD | 온보딩 미완료 재로그인 재식별 | 단위+통합 | `./gradlew test --tests "*OnboardingStatus*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | MEMBER-01 | TBD | 승인 목록 = 온보딩 완료 `PENDING`만 | 통합(Testcontainers) | `./gradlew test --tests "*MemberSpecification*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | MEMBER-02 | TBD | 이름·전화번호 부분 일치 검색 | 통합 | `./gradlew test --tests "*MemberSearch*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | MEMBER-03 | TBD | 상태 변경 시 refresh 무효화(강제 로그아웃) | 통합 | `./gradlew test --tests "*MemberStatusChange*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | MEMBER-04 | TBD | 본인 프로필 조회 | 통합 | `./gradlew test --tests "*MemberProfile*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | (D-033 회전) | TBD | refresh 회전 시 이전 토큰 재사용 감지 | 단위+통합 | `./gradlew test --tests "*RefreshTokenRotation*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | (D-017 회귀) | TBD | 만료/위조 토큰 → `application/problem+json` 401 | 통합 | `./gradlew test --tests "*JwtAuthenticationFilter*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/com/goldwrestling/auth/` — 신규 테스트 디렉터리 생성
- [ ] `src/test/resources/kakao/` — 카카오 API 응답 픽스처(JSON) 고정
- [ ] 고정 `Clock` 테스트 구성 (`@TestConfiguration` 기반) — Phase 1에 선례 없는 첫 시각 의존 로직
- [ ] `MockRestServiceServer` 기반 카카오 목킹 공용 헬퍼

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 실제 카카오 개발자 콘솔 연동(redirect_uri·client_secret 실값) | AUTH-01 | 실 카카오 계정·콘솔 설정은 자동화 불가 (테스트는 목킹으로 대체) | 로컬 `.env`에 실값 설정 후 브라우저로 카카오 로그인 플로우 1회 수행 |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
