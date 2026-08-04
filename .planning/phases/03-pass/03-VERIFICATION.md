---
phase: 03-pass
verified: 2026-08-04T09:00:00Z
status: passed
score: 6/6 roadmap 성공기준 VERIFIED
overrides_applied: 0
gaps: []
human_verification: []
resolution:
  - item: "WR-01 — ON_LEAVE 회원 본인 조회 403"
    decided: "2026-08-04 사용자 결정(AskUserQuestion): 휴회도 조회 허용 — 상태 게이트 제거"
    applied: "MemberPassService에서 requireActive 호출 제거(D-071 기록), PENDING 403 테스트를 전 상태 200 허용으로 교체 + ON_LEAVE 케이스 추가, 전체 빌드·테스트 그린 재확인. 커밋: fix(03) 참조"
---

# Phase 3: 이용권 Verification Report

**Phase Goal:** 관리자가 회원에게 이용권을 등록·조정하고, 모든 변경이 감사 가능한 이력으로 남으며, 회원이 본인 이용권 현황을 확인할 수 있다.
**Verified:** 2026-08-04T09:00:00Z
**Status:** passed (human_needed 1건이 사용자 결정으로 해소됨 — frontmatter `resolution` 참조)
**Re-verification:** No — initial verification + WR-01 결정 반영

## Goal Achievement

이 검증은 SUMMARY.md의 서술을 신뢰하지 않고, 11개 플랜이 만든 42개 파일을 직접 읽고, 실제 마이그레이션·엔티티·서비스·컨트롤러·openapi.yaml·테스트 결과를 대조해 ROADMAP.md Phase 3 성공기준 6개(SC1~SC6)를 하나씩 재구성했다. `./gradlew test --tests "com.goldwrestling.pass.*"`를 직접 재실행해 10개 테스트 클래스(단위 41 + 통합 다수, 총 pass 패키지 51건+) 전부 `failures="0" errors="0"`임을 XML 결과로 직접 확인했다(orchestrator가 보고한 그린 상태를 재현).

### Observable Truths (ROADMAP Success Criteria 기준)

| # | Truth (ROADMAP SC) | Status | Evidence |
|---|---|---|---|
| 1 | 관리자가 저녁반(1/3/6개월)·횟수권·레슨권을 시작일 지정(기본 오늘, 과거 허용)으로 등록하고, 횟수권 유효기간은 시작일+1년, 초기 횟수가 `INITIAL_GRANT` 이력으로 남는다 (D-055) | ✓ VERIFIED | `Pass.Companion.register`(`Pass.kt:206-251`)가 타입별 `when`으로 term/initialCount 상호 배타 검증 + `startDate.plusYears(1).minusDays(1)`/`plusMonths(term.months).minusDays(1)` 계산(D-066). `AdminPassService.register`(`AdminPassService.kt:44-87`)가 `request.startDate ?: today`로 기본값 처리하고, 횟수제일 때만 같은 `@Transactional` 메서드에서 `TransactionReason.INITIAL_GRANT` `PassTransaction` 저장. `PassRegistrationTest`(11케이스) + `AdminPassControllerTest` 등록 섹션(과거 날짜 허용·기본값 포함) 통합테스트로 실증 |
| 2 | 관리자가 사유 입력 후 잔여를 수동 가감(`ADMIN_ADJUST`)하면 즉시 반영되고, 모든 차감/복구가 `PassTransaction`(이용권/±수량/사유/주체/시각) 이력으로 남으며 이력 없는 변경이 불가능하다 | ✓ VERIFIED | `AdminPassService.adjust`(`AdminPassService.kt:98-141`)가 사전 판정(`Pass.validateAdjustment`) → `PassRepository.adjustRemainingCount` 조건부 UPDATE(`WHERE status=ACTIVE AND remainingCount+:amount>=0`) → 같은 트랜잭션에서 `PassTransaction` 저장. `PassLedgerInvariantTest`(등록→연속가감→정확히0소진→거부→취소, 7건)가 실제 PostgreSQL에서 "잔여 = `sumAmountByPassId`" 불변식을 직접 증명. `ck_pass_transaction_amount_nonzero` CHECK로 0 수량 이력도 DB에서 차단 |
| 3 | 관리자가 저녁반 기간·횟수권 유효기간을 수정할 수 있고, 모든 변경이 `PassPeriodChange`(전값/후값/사유/주체/시각)로 남는다 (D-056·D-057) | ✓ VERIFIED | `PATCH /api/admin/passes/{passId}/period` 단일 엔드포인트(D-062)가 `Pass.changePeriod`(횟수권은 종료일만, 저녁반은 시작·종료 모두)로 판정 후 `AdminPassService.changePeriod`가 호출 전 전값을 지역변수로 보관, 전/후값이 다를 때만 `PassPeriodChange` 저장(D-069). V4 `pass_period_change` 테이블에 전값·후값·사유·admin_id·occurred_at 전 컬럼 존재. `PassPeriodChangeTest`(7) + `AdminPassControllerTest` 기간수정 섹션(11건, 이력 미생성 케이스 포함)으로 실증 |
| 4 | 회원이 본인 보유 이용권의 잔여·유효기간을 조회할 수 있다 — 만료·소진 포함(상태 구분), 취소 제외 (D-058) | ⚠ VERIFIED (ACTIVE 회원 기준) — WARNING 부기 | `MemberPassService.getMyPasses`가 `findAllByMemberIdAndStatusNotOrderByStartDateDescIdDesc(memberId, CANCELED)`로 취소만 제외, `PassResponse.from`이 `displayStatus(today)`로 만료/소진 구분. `MemberPassControllerTest`로 실증. **단, `getMyPasses`/`getMyTransactions` 진입부의 `memberStateGate.requireActive(principal)`이 `ACTIVE`가 아니면(즉 `ON_LEAVE`도) 403을 던진다** — 03-REVIEW.md WR-01과 동일 지점을 코드에서 직접 재확인(`MemberPassService.kt:44,66`, `MemberStateGate.kt:29-36`). policies §5는 `ON_LEAVE`를 "정상 로그인·사용 상태(2주 미사용 차감만 정지)"로 정의하고 있어 이 제한의 의도가 문서로 뒷받침되지 않는다 → **아래 human_verification 항목 참고** |
| 5 | 회원이 본인 차감/복구 이력(시각·사유·수량)을 조회할 수 있다 | ✓ VERIFIED (동일 게이트 이슈는 #4와 공유) | `GET /api/members/me/pass-transactions`가 `PassTransactionSpecifications.ownedByMember` + `hasPassId`로 IDOR 방어, `page`/`size`가 `@ParameterObject`로 개별 쿼리 파라미터 노출(openapi.yaml 직접 확인, D-054 준수). `note`는 `PassTransactionResponse`에서 필드 자체가 없어 응답에 포함되지 않음(D-070). `MemberPassTransactionControllerTest`로 실증 |
| 6 | 관리자가 등록을 취소하면 취소 상태 전환 + `REGISTRATION_CANCELED` 상쇄 이력이 남고, 회원 조회에서 숨겨지고 관리자 화면에서는 구분 표시된다 (D-059) | ✓ VERIFIED | `AdminPassService.cancel`이 `pass.cancel()`(상태·메타데이터 전환, 상쇄수량 산출) → 0이 아니면 `zeroRemainingCount` 조건부 UPDATE → 재조회 후 `REGISTRATION_CANCELED` 이력 저장. `ck_pass_cancellation` CHECK로 취소 메타데이터(canceled_at/cancel_reason/canceled_by) 완전성 DB 강제. `getMemberPasses`(관리자, 취소 포함)와 `getMyPasses`(회원, 취소 제외)의 비대칭이 코드·KDoc에 명시적으로 문서화됨. `PassLedgerInvariantTest` 취소 순환 2건(잔여>0 취소, 잔여=0 취소) 통과 |

**Score:** 6/6 성공기준이 핵심 경로(등록/가감/기간수정/취소/관리자조회/회원조회)에서 코드로 검증됨. SC4는 `ACTIVE` 상태 회원에 한해 완전히 검증되고, `ON_LEAVE` 상태에 대해서는 코드 동작이 확인되었으나 의도 여부가 문서로 뒷받침되지 않아 인간 판단이 필요하다(아래 참고).

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `src/main/resources/db/migration/V4__create_pass_tables.sql` | pass/pass_transaction/pass_period_change 3테이블 + CHECK 5종 | ✓ VERIFIED | 파일 직접 열람 — `ck_pass_remaining_count_by_type`/`ck_pass_period`/`ck_pass_cancellation`/`ck_pass_transaction_amount_nonzero`/`ck_pass_period_change_period` 5개 CHECK 전부 존재. V1~V3 미변경 확인(`git log`에 수정 커밋 없음) |
| `src/main/kotlin/com/goldwrestling/pass/Pass.kt` | 단일 엔티티 + 5개 도메인 판정 메서드 | ✓ VERIFIED | `validateAdjustment`/`displayStatus`/`changePeriod`/`cancel`/`register` 전부 존재, `data class` 아님, `@Enumerated(EnumType.STRING)` 2회(type/status) |
| `src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt`, `AdminPassController.kt` | 관리자 4개 엔드포인트(등록/가감/기간수정/취소) + 목록조회 | ✓ VERIFIED | 5개 메서드 전부 구현, `principal.requireAdminId()`로만 주체 확정(요청 바디 위조 방지) |
| `src/main/kotlin/com/goldwrestling/pass/MemberPassService.kt`, `MemberPassController.kt` | 회원 본인 조회 2개 엔드포인트 | ✓ VERIFIED (WARNING: ON_LEAVE 차단, 아래 참고) | `/api/members/me/passes`, `/api/members/me/pass-transactions` 존재, IDOR 방어 확인 |
| `docs/api/openapi.yaml` | 7개 경로 전부 반영 | ✓ VERIFIED | `grep`으로 7개 경로 전부 확인(`/admin/members/{memberId}/passes` GET/POST, `/admin/passes/{passId}/adjustments`, `/period`, `/cancellation`, `/members/me/passes`, `/members/me/pass-transactions`). `pass-transactions`가 `passId`/`page`/`size` 개별 쿼리 파라미터로 펼쳐짐(D-054) 직접 확인 |
| `docs/error-codes.md` + `ErrorCode.kt` + `PassExceptions.kt` | 이용권 실패 7종 1:1 | ✓ VERIFIED | `ErrorCode.kt`에 7개 상수 전부 존재(`PASS_NOT_FOUND` 등), `ErrorCodeRegistryTest`가 문서↔enum 양방향 강제 |
| `docs/glossary.md`, `docs/decisions.md` (D-055~D-070) | 신규 개념·설계결정 등재 | ✓ VERIFIED | glossary "이용권(Phase 3)" 표에 5개 신규 개념 전부 등재, decisions.md에 `## D-055`~`## D-070` 16개 항목 전부 존재 |
| `src/test/kotlin/com/goldwrestling/pass/*` (11개 테스트 파일) | 단위+통합 테스트 | ✓ VERIFIED | `./gradlew test --tests "com.goldwrestling.pass.*"` 재실행 — 10개 test-results XML 전부 `failures="0" errors="0"` |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `PassExceptions.kt` | `ErrorCode.kt` | `DomainException(ErrorCode.XXX, ...)` | ✓ WIRED | 7개 클래스가 각각 다른 ErrorCode 생성자 호출 |
| `AdminPassService.register/adjust/cancel` | `PassTransaction` 저장 | 같은 `@Transactional` 메서드 내 저장 | ✓ WIRED | 세 메서드 모두 잔여 변경과 이력 저장이 한 트랜잭션 — "이력 없는 잔여 변경" 코드 경로 없음(REVIEW.md도 동일 결론) |
| `PassRepository.adjustRemainingCount`/`zeroRemainingCount` | DB 조건부 UPDATE | JPQL `WHERE ... AND ... >= 0` / `WHERE remainingCount = :expected` | ✓ WIRED | `PassRepositoryTest` 7건이 경계값(정확히 0, 음수 거부, 경쟁 패배)을 실제 PostgreSQL로 증명 |
| `MemberPassController` | `MemberPassService` | `@AuthenticationPrincipal` → `requireMemberId()` | ✓ WIRED | 경로에 `memberId` 없음 — IDOR 원천 차단 |
| `03-VALIDATION.md` | `src/test/kotlin/com/goldwrestling/pass/` | 요구사항별 automated command | ✓ WIRED | `TBD` 0건, PASS-01~08 8개 행 모두 실제 테스트 클래스 지목 |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| PASS-01 | 03-04, 03-06 | 이용권 등록 (시작일 지정, 유효기간 자동 계산) | ✓ SATISFIED | `Pass.register` + `AdminPassService.register` + `PassRegistrationTest`/`AdminPassControllerTest` |
| PASS-02 | 03-02, 03-06, 03-07, 03-09 | 모든 차감/복구가 이력으로 남음 | ✓ SATISFIED | `PassLedgerInvariantTest` 7건이 원장 불변식을 직접 증명 |
| PASS-03 | 03-03, 03-07 | 관리자 수동 가감(사유 필수) | ✓ SATISFIED | `Pass.validateAdjustment`(9 단위테스트) + `AdminPassControllerTest` 가감 섹션 |
| PASS-04 | 03-05, 03-08 | 저녁반 기간 수정 | ✓ SATISFIED | `PassPeriodChangeTest` + `AdminPassControllerTest` 기간수정 섹션 |
| PASS-05 | 03-04, 03-10 | 회원 본인 이용권 조회 | ✓ SATISFIED (ACTIVE 한정, WARNING 부기) | `MemberPassControllerTest` — 단 `ON_LEAVE` 케이스 테스트 없음(WR-01) |
| PASS-06 | 03-10 | 회원 본인 이력 조회 | ✓ SATISFIED (ACTIVE 한정, WARNING 부기) | `MemberPassTransactionControllerTest` — 동일 게이트 이슈 |
| PASS-07 | 03-05, 03-08 | 횟수권 유효기간 수정 | ✓ SATISFIED | PASS-04와 동일 통합 엔드포인트(D-062), 종료일만 수정 가능 검증 포함 |
| PASS-08 | 03-05, 03-09 | 등록 취소 | ✓ SATISFIED | `Pass.cancel` + `AdminPassService.cancel` + `PassCancellationTest` + 취소 순환 원장 테스트 |

ORPHANED 요구사항: 없음. REQUIREMENTS.md의 PASS-01~08 8건 모두 03-01~03-11 플랜의 `requirements:` frontmatter에 매핑되어 있고 누락된 항목이 없다.

### Anti-Patterns Found

`src/main/kotlin/com/goldwrestling/pass/` 전체와 신규 DTO에서 `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER`/"not yet implemented" 계열 문자열을 grep했으나 **0건**. 디버트 마커 게이트 저촉 없음.

### 코드리뷰 Warning 5건 재분류 (03-REVIEW.md)

리뷰의 각 Warning을 코드로 직접 재확인하고, 이번 phase의 goal 달성을 실제로 막는지 판단했다.

| ID | 재확인 결과 | 분류 | 근거 |
|---|---|---|---|
| WR-01 | `MemberPassService.kt:44,66`에서 코드로 직접 확인 — `ON_LEAVE` 회원이 본인 조회 403 | **WARNING → human_verification 항목으로 승격** | policies §5가 `ON_LEAVE`를 "정상 사용 상태"로 정의하는데 반해 근거 문서 없이 신규 제한이 생겼다. Core Value(원장 불변식)나 IDOR 경계를 깨지 않지만, SC4("회원이 본인 이용권 현황을 확인할 수 있다")의 적용 범위를 사실상 좁힌다 — 이 phase의 goal 문장을 문자 그대로 읽으면 갭에 해당할 수 있어 인간 판단으로 승격 |
| WR-02 | `Pass.kt:234` 확인 — `initialCount=null`이 `InvalidAdjustmentUnitException`(단위 위반 메시지)으로 오분류 | **INFO(비차단)** | 사용자 경험 저하(오해를 부르는 메시지)이나 요청은 정상 거부되고 등록은 실패 처리됨 — goal 달성 자체를 막지 않음. 후속 정리 권장 |
| WR-03 | `AdminPassService.kt:217-221` 확인 — 상쇄수량 0인 취소 경로에 조건부 UPDATE/낙관적 락 없음 | **INFO(비차단)** | 관리자 단독 저빈도 조작에서만 발생하는 동시성 경합. ROADMAP Phase 3 성공기준·요구사항 어디에도 동시성 보장이 명시돼 있지 않다(그 요구는 Phase 4가 명시적으로 짊어짐, D-021). 발생해도 최종 상태(취소됨)는 맞고 사유/주체 필드만 마지막 커밋 기준으로 남는 수준 |
| WR-04 | `AdminPassService.kt:153-188` 확인 — `changePeriod`에 `@Version`/조건부 UPDATE 없음 | **INFO(비차단)** | 동일 사유(WR-03) — 관리자 저빈도 조작, phase 요구사항에 동시성 명시 없음 |
| WR-05 | `MemberPassService.kt:62-79` 확인 — 회원 이력 조회가 취소된 이용권의 거래 내역을 필터링하지 않음 | **INFO(비차단)** | `note`(관리자 메모)는 D-070으로 이미 비노출. 노출되는 것은 "취소되었다는 사실 + 상쇄 금액"뿐이며 이는 회원 자신의 이력 데이터다(제3자 정보 아님) — IDOR·Core Value 위반 아님. D-058/D-059 취지("회원 화면에서 숨김")와 정확히 일치하는지는 정책 해석의 문제로, 이번 판정에서는 goal을 막는 수준이 아니라고 본다 |

WR-01만 human_verification으로 승격한 이유: 나머지 4건은 "실패했을 때도 시스템이 안전한 상태를 유지"하거나 "이번 phase의 명시적 요구사항 범위 밖"인 반면, WR-01은 **정상적으로 로그인해 사용 중인 회원 집단(ON_LEAVE)이 SC4가 약속하는 기능 자체에 접근하지 못하는** 유일한 경우이기 때문이다.

### Human Verification Required

#### 1. ON_LEAVE 회원의 본인 이용권/이력 조회 허용 여부

**Test:** `ON_LEAVE` 상태 회원 토큰으로 `GET /api/members/me/passes`, `GET /api/members/me/pass-transactions` 호출
**Expected:** 현재는 403 `MEMBER_NOT_ACTIVE` — 이것이 의도된 정책인지 확인 필요
**Why human:** `MemberStateGate.requireActive`가 `ACTIVE`만 통과시키도록 구현되어 있고, 이 제한이 PASS-05/06 요구사항이나 policies.md 어디에도 명시되지 않았다. Phase 2의 동일 계열 API(`MemberProfileService.getMyProfile`)는 "PENDING 회원의 승인 대기 안내 화면이 이 응답에 의존한다"는 이유로 의도적으로 게이트를 걸지 않은 선례가 있어, 이 phase의 제한이 새로운 의도적 결정인지 이식 과정의 누락인지 코드만으로는 판별할 수 없다. 두 경로(허용/유지) 모두 코드 변경 여지가 있는 제품 판단이다.

---

## Gaps Summary

FAILED로 분류된 must-have는 없다 — ROADMAP 성공기준 6개, PLAN 8개 파일의 must_haves, PASS-01~08 요구사항이 모두 코드·테스트로 확인됐다. 유일한 미해결 항목은 `ON_LEAVE` 회원의 조회 접근권(WR-01)이며, 이는 "잘못 구현됨"이 아니라 "의도가 문서화되지 않은 채 구현된 제한"이라 자동 검증으로는 옳고 그름을 판정할 수 없다 — Escalation Gate로 인간 결정을 요청한다. 결정 후:
- 유지가 맞다면 `docs/decisions.md`에 근거를 1줄 추가하고 `ON_LEAVE` 케이스 테스트를 추가하는 것으로 충분(코드 변경 불요)
- 완화가 맞다면 `MemberStateGate`에 "PENDING/INACTIVE만 차단" 게이트를 추가하는 소규모 갭 클로저 플랜이 필요

나머지 코드리뷰 Warning 4건(WR-02~05)은 이번 phase 요구사항 범위 밖이거나 실패 시에도 안전한 상태를 유지하는 저위험 사안으로 판단해 정보성으로만 기록했다 — phase 진행을 막지 않는다.

---

_Verified: 2026-08-04T09:00:00Z_
_Verifier: Claude (gsd-verifier)_
