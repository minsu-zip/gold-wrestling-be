---
phase: 02-auth-member
verified: 2026-08-03T02:32:46Z
status: passed
score: 5/5 must-haves verified (ROADMAP Success Criteria 기준)
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 4/5
  gaps_closed:
    - "회원이 카카오 OAuth로 로그인하면 PENDING 상태로 가입되고 JWT access/refresh 토큰이 발급된다 (동시 최초 로그인 포함) — CR-01, 02-12-PLAN.md로 닫힘"
  gaps_remaining: []
  regressions: []
deferred: []
human_verification: []
---

# Phase 2: 인증·회원 Verification Report (재검증 — 갭 클로저 이후)

**Phase Goal:** 회원이 카카오로 가입해 온보딩·관리자 승인을 거쳐 활성 상태가 되고, 관리자는 ID/PW로 로그인해 회원을 관리할 수 있다 — 카카오 로그인·JWT 인증·관리자 인증·온보딩·회원 관리(승인/거절/상태 변경/조회)가 실제 코드로 동작하고 정합성이 검증된 상태.
**Verified:** 2026-08-03T02:32:46Z
**Status:** passed
**Re-verification:** Yes — 이전 검증(2026-08-03T00:03:27Z, `gaps_found` 4/5)에서 발견된 Blocker(CR-01)가 02-12-PLAN.md로 닫힌 뒤 재검증

## 검증 방법 요약

이전 VERIFICATION.md·02-12~02-15 SUMMARY.md의 서술을 그대로 신뢰하지 않고 다음을 직접 확인했다.

1. 이전 VERIFICATION.md의 유일한 Blocker(CR-01)와 관련 소스(`MemberRegistrationService.kt`, `KakaoAuthService.kt`)를 원본으로 재독해 — catch 위치가 실제로 트랜잭션 경계 밖(`KakaoAuthService.login`, `@Transactional` 없음)으로 옮겨졌는지 확인
2. `KakaoLoginConcurrencyTest.kt`를 직접 읽고 `./gradlew test --rerun` 실행 결과 XML(`build/test-results/test/TEST-com.goldwrestling.auth.KakaoLoginConcurrencyTest.xml`)에서 실제로 `duplicate key value violates unique constraint "uq_member_kakao_id"`(SQLState 23505) 경쟁이 재현된 뒤 두 테스트 모두 통과(0 failures)했음을 raw 로그로 확인 — 재현 없이 통과만 하는 가짜 테스트가 아님을 검증
3. WR-01(`RefreshTokenRepository.kt`/`TokenService.kt`), WR-03/04/05(`MemberSpecifications.kt`/`AdminMemberService.kt`), WR-06(`AdminMemberController.kt`/`docs/api/openapi.yaml`) 각각의 수정 내용을 SUMMARY.md 주장과 실제 소스 코드를 라인 단위로 대조
4. `docker compose ps`로 Postgres 컨테이너 기동 확인 후 `./gradlew test --rerun`(캐시 무시 강제 재실행)을 직접 실행 — `build/test-results/test/*.xml`을 `tests=`/`failures=`/`errors=` 속성으로 집계해 210 tests / 0 failures / 0 errors 실측 확인 (02-15 SUMMARY의 "210건" 주장과 일치)
5. `./gradlew ktlintCheck` 실행 — BUILD SUCCESSFUL(포맷 위반 없음)
6. `git status --short docs/api/openapi.yaml` — 드리프트 없음(clean)
7. `docs/api/openapi.yaml`의 `/api/admin/members` GET `parameters` 블록을 직접 열람 — `condition` 객체 1개가 아니라 `keyword`/`status`/`onboardingCompleted`/`page`/`size` 5개 독립 파라미터로 기술됨을 확인
8. 02-12~02-15 PLAN.md의 `must_haves`(truths/artifacts/key_links) 전체를 코드와 대조
9. `docs/decisions.md`에서 D-050~D-054 존재 확인
10. 인증·회원 관련 anti-pattern(TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER) 스캔 — 02-12~02-15가 변경한 13개 파일 전수, 매치 없음
11. 02-REVIEW.md 원 결함 목록과 대조해 WR-02(관리자 로그인 타이밍 부채널)·Info 6건이 이번 갭 클로저 범위에서 의도적으로 제외되었음을 ROADMAP.md의 "Note (갭 클로저)" 문구로 재확인 — `AdminAuthService.kt`를 직접 열람해 WR-02가 실제로 미수정 상태임을 확인(의도된 범위 제외이지 누락이 아님)

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria 기준)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | 회원이 카카오 OAuth로 로그인하면 `PENDING` 상태로 가입되고 JWT access/refresh 토큰이 발급되며, refresh로 access를 갱신할 수 있다 (동시 최초 로그인 경쟁 포함) | ✓ VERIFIED | 순차 흐름은 이전 검증에서 이미 확인됨. 동시 경쟁 복구는 `MemberRegistrationService.findOrCreateByKakaoId`(38-50행, 트랜잭션 catch 제거·예외 전파)+`KakaoAuthService.login`(41-57행, 트랜잭션 밖 1회 재시도)로 재작성됨. `KakaoLoginConcurrencyTest`가 스레드 4개로 실제 유니크 제약 위반(`uq_member_kakao_id`, SQLState 23505)을 재현한 뒤 "회원 1명 + 4개 요청 전부 성공 + UnexpectedRollbackException 없음"을 실측 통과 |
| 2 | 최초 로그인 회원은 온보딩 대상으로 식별되어 실명·전화번호를 필수 입력해야 하고(형식 검증 포함), 온보딩 미완료 상태로 재로그인해도 다시 온보딩 대상으로 식별된다 | ✓ VERIFIED | 이전 검증과 변경 없음 — `MemberProfileService.submitOnboarding` + `OnboardingValidationTest`(7건) + `MemberOnboardingStatusTest`, `Member.isOnboardingCompleted()` 매 요청 판정 |
| 3 | 관리자 승인 목록에는 온보딩을 완료한 `PENDING` 회원만 노출되고, 관리자가 승인하면 `ACTIVE`로 전환되어 전체 기능을 쓸 수 있다 (거절도 가능) | ✓ VERIFIED | `approve()`의 서버측 강제(이전 검증에서 이미 확인)에 더해, 이전 검증이 지적한 `changeStatus()` 우회 경로(WR-03)가 `AdminMemberService.changeStatus`(121-141행) `newStatus == ACTIVE && !member.isOnboardingCompleted()` 검사로 닫힘 — `MemberStatusChangeTest` 신규 3건(409+상태유지, 200, 비-ACTIVE 전이 허용)으로 검증. 회귀 방향 증명(검사 제거 후 테스트 실패 실측)도 02-14 SUMMARY에 기록되어 있고 코드로 재확인됨 |
| 4 | 관리자는 ID/PW로 로그인해(카카오 연동 없음, 회원과 동일한 JWT 체계) 회원 목록·상세를 조회하고 이름·전화번호로 검색하며, 회원 상태(`ACTIVE`/`ON_LEAVE`/`INACTIVE`)를 변경할 수 있다 | ✓ VERIFIED | `AdminAuthController`/`AdminAuthService`(이전 검증에서 트랜잭션 버그 수정 확인됨, 변경 없음). 이전 검증이 지적한 검색 엣지케이스(WR-04, 하이픈만 입력 시 전체 매칭)는 `MemberSpecifications.keywordContains`가 `PhoneNumberNormalizer.normalize` 결과가 빈 문자열이면 전화번호 술어 자체를 만들지 않도록 수정(36-62행)되어 닫힘 — `MemberSpecificationTest`의 하이픈 전용 검색어 테스트로 검증 |
| 5 | `PENDING`이거나 온보딩 미완료인 회원은 승인 대기 정보 외 기능에 접근할 수 없고, 회원은 본인 프로필(이름·전화번호)을 조회할 수 있다 | ✓ VERIFIED | 이전 검증과 변경 없음 — `MemberStateGate.requireActive()` + `MemberProfileController` |

**Score:** 5/5 truths 완전 충족 (이전 검증의 유일한 Blocker였던 truth 1의 동시성 경쟁 경로가 닫힘)

### 갭 클로저 상세 — 이전 Blocker/Warning 대조

| ID | 내용 | 이전 상태 | 재검증 상태 | 증거 |
|----|------|-----------|--------------|------|
| CR-01 | 동시 최초 로그인 경쟁 복구 코드가 PostgreSQL에서 동작 불가 | 🛑 Blocker | ✓ CLOSED | `MemberRegistrationService.kt` 38-50행(트랜잭션 안 catch 제거), `KakaoAuthService.kt` 41-57행(트랜잭션 밖 1회 재시도), `KakaoLoginConcurrencyTest.kt` 신규(경쟁 실측 재현+통과), D-050 |
| WR-01 | refresh 회전 폐기가 TOCTOU — 동시 제시 시 재사용 감지 무력화 | ⚠️ Warning | ✓ CLOSED | `RefreshTokenRepository.kt`의 조건부 벌크 UPDATE 3종(`revokeIfUsable`/`revokeAllUsableByMemberId`/`revokeAllUsableByAdminId`), `TokenService.rotate`가 갱신 행 수로 경쟁 승패 판정+`noRollbackFor`로 실패 응답에서도 폐기 커밋, `RefreshTokenRotationConcurrencyTest.kt` 신규, D-051 |
| WR-02 | 관리자 로그인 타이밍 부채널 | ℹ️ Info(낮은 우선순위) | ⏸ 의도적 범위 제외 | `AdminAuthService.kt` 확인 결과 미수정 상태 그대로 — ROADMAP.md "Note (갭 클로저)"에 "WR-02와 Info 6건은 이번 갭 클로저 범위 밖 — 사용자 판단 사항"으로 명시적으로 기록됨. 새 갭이 아니라 사용자가 이미 알고 보류한 항목 |
| WR-03 | `changeStatus`가 온보딩 미완료 회원의 ACTIVE 전환을 막지 않음 | ⚠️ Warning | ✓ CLOSED | `AdminMemberService.changeStatus` 121-141행에 ACTIVE 전환 시 온보딩 완료 검사 추가, `MemberStatusChangeTest` 신규 3건, D-052 |
| WR-04 | 하이픈만 있는 검색어 → 전화번호 조건이 `LIKE '%%'`로 무력화 | ⚠️ Warning | ✓ CLOSED | `MemberSpecifications.keywordContains` 조건부 술어 리스트로 재작성, `MemberSpecificationTest` 신규 케이스, D-053 |
| WR-05 | 온보딩 완료 판정 blank 의미론 불일치(엔티티 vs Specification) | ℹ️ Info | ✓ CLOSED (WR-04와 같은 플랜에서 함께 처리) | `MemberSpecifications.onboardingCompleted`에 `criteriaBuilder.trim` 적용, `MemberSpecificationTest` 공백 이름(m7) 픽스처 신규, D-053 |
| WR-06 | openapi.yaml 회원 목록 쿼리가 단일 `condition` 객체 파라미터로 표현 | ⚠️ Warning | ✓ CLOSED | `AdminMemberController.kt`에 `@ParameterObject` 추가, `docs/api/openapi.yaml` 재생성으로 `keyword`/`status`/`onboardingCompleted`/`page`/`size` 5개 독립 파라미터로 확인, D-054 |
| IN-01, IN-02, IN-03, IN-04, IN-05, IN-06 | 그 외 Info 6건 | ℹ️ Info | ⏸ 의도적 범위 제외 | ROADMAP.md에 명시적으로 이번 갭 클로저 범위 밖으로 기록됨 |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/com/goldwrestling/member/MemberRegistrationService.kt` | 예외를 전파하는 find-or-create (트랜잭션 경계 = 이 메서드만) | ✓ VERIFIED | `catch (e: DataIntegrityViolationException)` 없음(코드 확인) — 예외가 그대로 전파되도록 재작성됨 |
| `src/main/kotlin/com/goldwrestling/auth/KakaoAuthService.kt` | 트랜잭션 밖 1회 재시도 | ✓ VERIFIED | 41-57행에 `try { ... } catch (e: DataIntegrityViolationException) { ...재호출... }` 존재, 클래스에 `@Transactional` 없음 |
| `src/test/kotlin/com/goldwrestling/auth/KakaoLoginConcurrencyTest.kt` | 동시 최초 로그인 경쟁 재현 테스트 | ✓ VERIFIED | `ExecutorService`+`CountDownLatch` 사용, `@Transactional` 미사용, 테스트 2건 모두 실측 통과(경쟁 재현 로그 확인) |
| `src/main/kotlin/com/goldwrestling/auth/RefreshTokenRepository.kt` | 조건부 벌크 UPDATE | ✓ VERIFIED | `revokeIfUsable`/`revokeAllUsableByMemberId`/`revokeAllUsableByAdminId` 3종, `@Modifying(flushAutomatically=true, clearAutomatically=true)` |
| `src/main/kotlin/com/goldwrestling/auth/TokenService.kt` | 원자적 폐기 + noRollbackFor | ✓ VERIFIED | `rotate`가 `revokeIfUsable` 반환값(갱신 행 수)으로 경쟁 승패 판정, `@Transactional(noRollbackFor = [RefreshTokenInvalidException::class])` |
| `src/test/kotlin/com/goldwrestling/auth/RefreshTokenRotationConcurrencyTest.kt` | 동시 회전 경쟁 + 폐기 지속성 테스트 | ✓ VERIFIED | 테스트 결과 XML에서 2건 모두 통과 확인 |
| `src/main/kotlin/com/goldwrestling/member/MemberSpecifications.kt` | 조건부 전화번호 술어 + trim 기반 온보딩 판정 | ✓ VERIFIED | `predicates.add(...)` 조건부(`normalizedPhone.isNotEmpty()`), `criteriaBuilder.trim` 적용 |
| `src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt` | changeStatus ACTIVE 전환 서버측 강제 | ✓ VERIFIED | `newStatus == MemberStatus.ACTIVE && !member.isOnboardingCompleted()` → `MemberStateConflictException` |
| `src/main/kotlin/com/goldwrestling/member/AdminMemberController.kt` | `@ParameterObject` | ✓ VERIFIED | `search` 파라미터에 `@ParameterObject @ModelAttribute @Valid` 확인 |
| `docs/api/openapi.yaml` | 개별 쿼리 파라미터 5개 | ✓ VERIFIED | `condition` 객체 파라미터 없음, `keyword`/`status`/`onboardingCompleted`/`page`/`size` 5개 확인. `git status --short` clean |
| `docs/decisions.md` | D-050~D-054 | ✓ VERIFIED | 5건 모두 존재(424~452행) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `KakaoAuthService.login` | `MemberRegistrationService.findOrCreateByKakaoId` | `DataIntegrityViolationException` catch 후 재호출(프록시 경유 = 새 트랜잭션) | ✓ WIRED | 소스 확인 + `KakaoLoginConcurrencyTest` 실측 재현·통과 |
| `TokenService.rotate` | `RefreshTokenRepository.revokeIfUsable` | 반환값(갱신 행 수)으로 경쟁 승패 판정 | ✓ WIRED | 소스 확인 + `RefreshTokenRotationConcurrencyTest` 실측 통과 |
| `AdminMemberService.changeStatus` | `Member.isOnboardingCompleted` | ACTIVE 전환 전 서버측 정책 검사 | ✓ WIRED | 소스 확인 + `MemberStatusChangeTest` 409 케이스 통과 |
| `MemberSpecifications.onboardingCompleted` | `Member.isOnboardingCompleted` | 동일 판정 규칙(trim 기준) | ✓ WIRED | `criteriaBuilder.trim` 적용 확인 + `MemberSpecificationTest` 정합성 테스트 통과 |
| `AdminMemberController.search` | `docs/api/openapi.yaml` | `./gradlew generateApiDocs` 재생성 | ✓ WIRED | `@ParameterObject` 적용 후 재생성된 5개 개별 파라미터 확인, `git status` clean |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| 전체 테스트 스위트 green (강제 재실행) | `./gradlew test --rerun` | BUILD SUCCESSFUL, 210 tests / 0 failures / 0 errors (test-results XML 직접 집계) | ✓ PASS |
| 동시 최초 로그인 경쟁이 실제로 재현되고 흡수되는가 | `KakaoLoginConcurrencyTest` 결과 XML raw 로그 | `duplicate key value violates unique constraint "uq_member_kakao_id"`(SQLState 23505) 경쟁 발생 로그 확인 + 테스트 2건 통과(failures=0) | ✓ PASS |
| ktlint 포맷 준수 | `./gradlew ktlintCheck` | BUILD SUCCESSFUL | ✓ PASS |
| openapi.yaml 드리프트 없음 | `git status --short docs/api/openapi.yaml` | 출력 없음(clean) | ✓ PASS |
| anti-pattern(TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER) | 갭 클로저 13개 변경 파일 전수 grep | 매치 없음 | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|--------------|--------|----------|
| AUTH-01 | 02-02, 02-06, 02-12 | 카카오 OAuth 가입·로그인 (PENDING, 동시 최초 로그인 포함) | ✓ SATISFIED | 정상 흐름 + 동시 경쟁 복구 모두 검증됨. CR-01 닫힘 |
| AUTH-02 | 02-03, 02-04, 02-05, 02-13 | JWT access/refresh 발급·갱신 (동시 회전 포함) | ✓ SATISFIED | `TokenControllerTest`, `RefreshTokenRotationTest`(순차), `RefreshTokenRotationConcurrencyTest`(동시) |
| AUTH-03 | 02-02, 02-07 | 관리자 ID/PW 로그인 | ✓ SATISFIED | 이전 검증에서 확인, 변경 없음 |
| AUTH-04 | 02-05, 02-08, 02-10 | 역할·상태 기반 인가 | ✓ SATISFIED | 이전 검증에서 확인, 변경 없음 |
| AUTH-05 | 02-08 | 온보딩(실명·전화번호, 형식 검증) | ✓ SATISFIED | 이전 검증에서 확인, 변경 없음 |
| AUTH-06 | 02-02, 02-08 | 온보딩 미완료 재로그인 재식별 | ✓ SATISFIED | 이전 검증에서 확인, 변경 없음 |
| MEMBER-01 | 02-09, 02-10, 02-14 | 승인/거절 (온보딩 완료 PENDING만 노출, ACTIVE 전환 서버측 강제) | ✓ SATISFIED | `MemberApprovalTest` + `changeStatus` 우회 차단(WR-03) 신규 테스트 3건으로 정책 우회 경로 닫힘 |
| MEMBER-02 | 02-09, 02-14, 02-15 | 목록·상세 조회, 이름/전화 검색, openapi 계약 정합 | ✓ SATISFIED | `MemberSearchTest` + 하이픈 전용 검색어 방어(WR-04) + openapi 개별 파라미터화(WR-06) |
| MEMBER-03 | 02-10, 02-14 | 회원 상태 변경 (ACTIVE/ON_LEAVE/INACTIVE, ACTIVE 전환 정책 강제) | ✓ SATISFIED | `MemberStatusChangeTest`(기존 + 신규 3건) |
| MEMBER-04 | 02-08 | 본인 프로필 조회 | ✓ SATISFIED | 이전 검증에서 확인, 변경 없음 |

**Orphaned requirements:** 없음 — REQUIREMENTS.md의 Phase 2 매핑(AUTH-01~06, MEMBER-01~04) 10건 전부 어느 한 플랜의 `requirements` 필드에 등장한다.

### Anti-Patterns Found

없음. 02-12~02-15가 수정·생성한 13개 파일 전수(`MemberRegistrationService.kt`, `KakaoAuthService.kt`, `KakaoLoginConcurrencyTest.kt`, `RefreshTokenRepository.kt`, `TokenService.kt`, `RefreshTokenRotationConcurrencyTest.kt`, `MemberSpecifications.kt`, `AdminMemberService.kt`, `MemberSpecificationTest.kt`, `MemberStatusChangeTest.kt`, `AdminMemberController.kt`, `docs/api/openapi.yaml`, `docs/decisions.md`)에서 TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER 문자열 매치 0건.

이전 검증의 유일한 Blocker(CR-01, "동작이 증명 가능하게 틀린" 트랜잭션 경계 오설계)가 코드·테스트로 닫혔으므로 남은 anti-pattern 없음.

### Human Verification Required

없음. 갭 클로저 플랜(02-12~02-15)은 이전 검증이 사람이 이미 확인한 실제 카카오 E2E 흐름(02-11 Task 3, 02-VALIDATION.md)에 영향을 주는 UI·사용자 흐름 변경이 아니라, 서버 내부 트랜잭션 경계·동시성 제어·검색 조건·API 문서 계약을 수정한 것이라 시각적·수동 확인이 새로 필요하지 않다. 이전 검증에서 이미 확인된 E2E 항목(카카오 로그인→온보딩→관리자 승인→상태 변경→강제 로그아웃)은 이번 재검증에서 재수행하지 않았다 — UI·흐름 자체는 변경되지 않았고, 각 갭 클로저 플랜이 회귀 방향 증명(수정 되돌린 뒤 실패 실측 → 복원)을 자체적으로 수행해 실측 검증했기 때문이다.

### Gaps Summary

없음. 이전 검증(2026-08-03T00:03:27Z)의 유일한 Blocker(CR-01: 동시 최초 로그인 경쟁 복구가 PostgreSQL에서 구조적으로 동작 불가)가 02-12-PLAN.md에서 트랜잭션 경계 재설계 + 동시성 테스트로 닫혔고, 실제 소스 코드·실제 테스트 실행 결과(경쟁 재현 로그 포함)로 재확인했다. 함께 지적됐던 Warning 5건(WR-01, WR-03, WR-04, WR-05, WR-06)도 02-13~02-15에서 모두 닫혔고 각각 코드·테스트로 검증했다.

남은 항목(WR-02 관리자 로그인 타이밍 부채널, Info 6건)은 ROADMAP.md에 "이번 갭 클로저 범위 밖 — 사용자 판단 사항"으로 명시적으로 기록되어 있는 낮은 우선순위 항목이며, phase 목표("카카오 로그인·JWT 인증·관리자 인증·온보딩·회원 관리가 실제 코드로 동작하고 정합성이 검증된 상태") 달성을 막지 않는다. 이들은 새로운 gap이 아니라 사용자가 이미 인지하고 보류한 항목이므로 이번 재검증에서 gap으로 재구조화하지 않는다.

Phase 2는 ROADMAP.md의 5개 Success Criteria를 모두 충족하고, 관련 REQUIREMENTS.md 10건(AUTH-01~06, MEMBER-01~04)이 모두 SATISFIED다. Phase 3 진입 가능.

---

_Verified: 2026-08-03T02:32:46Z_
_Verifier: Claude (gsd-verifier)_
