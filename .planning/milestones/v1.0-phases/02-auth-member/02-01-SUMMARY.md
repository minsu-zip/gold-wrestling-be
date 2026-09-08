---
phase: 02-auth-member
plan: 01
subsystem: docs
tags: [glossary, error-codes, decisions, ktlint, kotlin]

# Dependency graph
requires:
  - phase: 01-foundation
    provides: ErrorCode enum, DomainException, GlobalExceptionHandler (ProblemDetail 계약)
provides:
  - "docs/glossary.md 인증·회원 신규 개념 12종 + 금지어 4종"
  - "ErrorCode enum 10종 확장 (17개 상수) + docs/error-codes.md 표 동기화"
  - "ErrorCodeRegistryTest — enum ↔ 문서 드리프트를 빌드에서 강제하는 순수 단위테스트"
  - "docs/decisions.md D-036~D-047 설계 결정 12건"
affects: [02-auth-member 이후 플랜 전체 (02-02~02-11) — 인증·회원 코드가 쓸 이름·에러코드·설계 결정의 근거]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "enum ↔ 문서 양방향 동기화를 순수 단위테스트(Spring 컨텍스트 없음)로 강제"

key-files:
  created:
    - src/test/kotlin/com/goldwrestling/common/error/ErrorCodeRegistryTest.kt
  modified:
    - docs/glossary.md
    - docs/decisions.md
    - docs/error-codes.md
    - src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt

key-decisions:
  - "D-036~D-047: refresh 토큰 저장·회전, FK 설계, 관리자 시드, 감사 시각, 인가 구현 방식, 전화번호 저장, 온보딩 재제출 금지, 거절 사유 노출 범위, 상태 변경 시 refresh 무효화, 비밀번호 해싱, 카카오 연동 세부, 신규 회원 지점 배정 — 전부 02-CONTEXT.md의 Claude's Discretion 범위에서 planner가 이미 확정한 결정을 문서화만 함"

patterns-established:
  - "인증·인가·회원 상태 에러코드는 ErrorCode enum + docs/error-codes.md 표 + ErrorCodeRegistryTest 3점 세트로 관리"

requirements-completed: [AUTH-02, AUTH-03, AUTH-04, MEMBER-01, MEMBER-03]

# Metrics
duration: ~15min
completed: 2026-08-02
---

# Phase 2 Plan 1: 인증·회원 네이밍·에러코드·결정 확정 Summary

**glossary.md 인증·회원 개념 12종 등재, ErrorCode enum 10종 확장(17개) + 표 동기화 테스트, decisions.md D-036~D-047 12건 기록 — 커밋 없이 파일만 준비**

## Performance

- **Duration:** 약 15분
- **Completed:** 2026-08-02
- **Tasks:** 3/3 완료
- **Files modified:** 4개 수정 + 1개 신규

## Accomplishments

- `docs/glossary.md`에 "## 인증·회원 (Phase 2)" 섹션 신설 — kakaoId, RefreshToken, PrincipalType, AuthenticatedPrincipal, loginId, passwordHash, rejectionReason, Onboarding, onboardingCompleted, MemberStateGate 등 12개 개념을 표로 등재하고, 금지어(Session/User/Account/Role 단독)를 추가
- `ErrorCode` enum을 7개(공통) → 17개(공통 7 + 인증·회원 10)로 확장. `docs/error-codes.md`에 "## 인증·회원 코드 (Phase 2)" 표를 폴백 규칙 문단 앞에 추가해 1:1 동기화
- `ErrorCodeRegistryTest`(순수 Kotlin 단위테스트, 스프링 컨텍스트 없음)를 신규 작성 — enum→문서, 문서→enum 양방향 검증. 임시 상수를 추가해 실제로 테스트가 실패함을 확인 후 되돌림(실험 완료)
- `docs/decisions.md`에 D-036~D-047 12건을 기존 형식으로 기록하고 D-048 플레이스홀더로 교체

## Task Commits

**커밋하지 않음.** 이 플랜의 `<commit_policy>`와 CLAUDE.md "커밋·푸시는 사용자가 명시적으로 요청했을 때만 실행한다" 규칙에 따라 파일 저장까지만 하고 `git add`/`git commit`을 실행하지 않았다. 아래 "커밋 대기 중인 변경 파일"을 사용자 확인 후 커밋을 지시해야 한다.

## Files Created/Modified (커밋 대기)

- `docs/glossary.md` — 인증·회원 신규 개념 12종 + 금지어 4종 추가 (수정, +19줄)
- `docs/error-codes.md` — "## 인증·회원 코드 (Phase 2)" 표 10행 추가 (수정, +15줄)
- `docs/decisions.md` — D-036~D-047 12건 추가, D-048 플레이스홀더로 교체 (수정, +76줄/-4줄)
- `src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt` — 인증·회원 에러코드 10개 상수 추가, 클래스 KDoc 문구 갱신 (수정, +37줄/-4줄)
- `src/test/kotlin/com/goldwrestling/common/error/ErrorCodeRegistryTest.kt` — enum↔문서 동기화 검증 단위테스트 (신규)

## Decisions Made

이 플랜 자체는 새 설계 결정을 만들지 않고, 02-CONTEXT.md에서 이미 확정된 D-036~D-047을 `docs/decisions.md`에 옮겨 적었다. 실행 중 추가로 내린 소소한 판단:

- `ErrorCodeRegistryTest`의 역방향 검사(문서→enum)는 "## 공통 코드"·"## 인증·회원 코드" 표의 **첫 번째 열만** 파싱하도록 구현 — "발생 지점" 열의 클래스명(`AdminAuthService` 등)도 백틱을 쓰므로 전체 텍스트 정규식 스캔은 오탐을 만든다. 태스크 지시(`<action>`)에 이미 명시된 요구사항을 그대로 구현한 것이라 별도 편차는 아니다.

## Deviations from Plan

None - plan executed exactly as written.

테스트 작성 면제 판단 불필요 — 이 플랜의 유일한 코드 변경(`ErrorCode.kt`)은 플랜이 명시한 `ErrorCodeRegistryTest.kt`로 이미 커버된다 (conventions.md §10.0 "전역 예외 핸들러 / 에러 응답 → 통합테스트로 상태코드·본문 형태 확인"에 준하는 계약 테스트).

## Issues Encountered

없음. `./gradlew ktlintFormat` → `./gradlew build`(Testcontainers 포함, Docker Desktop 기동 확인) 전부 통과. 임시 상수 실험(`TEMP_EXPERIMENT_CODE`)으로 드리프트 검출 동작을 확인한 뒤 정상 되돌림.

## User Setup Required

None - 외부 서비스 설정 불필요.

## Next Phase Readiness

- 이후 9개 플랜(02-02~02-11)이 카카오 로그인·JWT·관리자 인증·온보딩·회원 관리 코드를 작성할 때 쓸 이름(glossary)·에러코드(ErrorCode/error-codes.md)·설계 근거(decisions.md D-036~D-047)가 전부 준비됨
- **차단 요소: 이 플랜의 변경사항이 아직 커밋되지 않았다.** 오케스트레이터가 이 워크트리를 정리하기 전에 사용자가 변경 파일을 검토하고 커밋을 명시적으로 지시해야 한다 (아래 "커밋 정책 관련 알림" 참조)

---
*Phase: 02-auth-member*
*Completed: 2026-08-02*

## Self-Check: PASSED

- FOUND: docs/glossary.md (수정 확인, `## 인증·회원 (Phase 2)` 존재)
- FOUND: docs/error-codes.md (수정 확인, `## 인증·회원 코드 (Phase 2)` 존재)
- FOUND: docs/decisions.md (수정 확인, D-036~D-048 순번 완전)
- FOUND: src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt (17개 상수 확인)
- FOUND: src/test/kotlin/com/goldwrestling/common/error/ErrorCodeRegistryTest.kt (신규 파일, 테스트 2건 PASSED)
- `./gradlew build` BUILD SUCCESSFUL (Testcontainers 포함 전체 테스트 통과)
- 커밋 없음 — task_commit_protocol의 표준 커밋 단계는 이 플랜의 `<commit_policy>`(CLAUDE.md 커밋 규칙 우선 적용)에 따라 의도적으로 생략함
