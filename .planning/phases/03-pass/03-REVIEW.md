---
phase: 03-pass
reviewed: 2026-08-03T23:44:37Z
depth: standard
files_reviewed: 42
files_reviewed_list:
  - src/main/kotlin/com/goldwrestling/pass/Pass.kt
  - src/main/kotlin/com/goldwrestling/pass/PassTransaction.kt
  - src/main/kotlin/com/goldwrestling/pass/PassPeriodChange.kt
  - src/main/kotlin/com/goldwrestling/pass/PassRepository.kt
  - src/main/kotlin/com/goldwrestling/pass/PassTransactionRepository.kt
  - src/main/kotlin/com/goldwrestling/pass/PassPeriodChangeRepository.kt
  - src/main/kotlin/com/goldwrestling/pass/PassTransactionSpecifications.kt
  - src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt
  - src/main/kotlin/com/goldwrestling/pass/AdminPassController.kt
  - src/main/kotlin/com/goldwrestling/pass/MemberPassService.kt
  - src/main/kotlin/com/goldwrestling/pass/MemberPassController.kt
  - src/main/kotlin/com/goldwrestling/pass/PassExceptions.kt
  - src/main/kotlin/com/goldwrestling/pass/PassType.kt
  - src/main/kotlin/com/goldwrestling/pass/PassStatus.kt
  - src/main/kotlin/com/goldwrestling/pass/PassDisplayStatus.kt
  - src/main/kotlin/com/goldwrestling/pass/TransactionReason.kt
  - src/main/kotlin/com/goldwrestling/pass/EveningMembershipTerm.kt
  - src/main/kotlin/com/goldwrestling/pass/dto/RegisterPassRequest.kt
  - src/main/kotlin/com/goldwrestling/pass/dto/PassResponse.kt
  - src/main/kotlin/com/goldwrestling/pass/dto/AdjustPassRequest.kt
  - src/main/kotlin/com/goldwrestling/pass/dto/ChangePassPeriodRequest.kt
  - src/main/kotlin/com/goldwrestling/pass/dto/CancelPassRequest.kt
  - src/main/kotlin/com/goldwrestling/pass/dto/PassTransactionResponse.kt
  - src/main/kotlin/com/goldwrestling/pass/dto/PassTransactionSearchCondition.kt
  - src/main/kotlin/com/goldwrestling/auth/AuthenticatedPrincipal.kt
  - src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt
  - src/main/resources/db/migration/V4__create_pass_tables.sql
  - src/test/kotlin/com/goldwrestling/pass/PassAdjustmentPolicyTest.kt
  - src/test/kotlin/com/goldwrestling/pass/PassRegistrationTest.kt
  - src/test/kotlin/com/goldwrestling/pass/PassDisplayStatusTest.kt
  - src/test/kotlin/com/goldwrestling/pass/PassPeriodChangeTest.kt
  - src/test/kotlin/com/goldwrestling/pass/PassCancellationTest.kt
  - src/test/kotlin/com/goldwrestling/pass/PassLedgerInvariantTest.kt
  - src/test/kotlin/com/goldwrestling/pass/PassRepositoryTest.kt
  - src/test/kotlin/com/goldwrestling/pass/AdminPassControllerTest.kt
  - src/test/kotlin/com/goldwrestling/pass/MemberPassControllerTest.kt
  - src/test/kotlin/com/goldwrestling/pass/MemberPassTransactionControllerTest.kt
  - src/test/kotlin/com/goldwrestling/pass/PassFixtures.kt
  - src/test/kotlin/com/goldwrestling/auth/AuthenticatedPrincipalTest.kt
  - src/test/kotlin/com/goldwrestling/common/error/ErrorCodeRegistryTest.kt
  - docs/api/openapi.yaml
  - docs/error-codes.md
findings:
  critical: 0
  warning: 5
  info: 2
  total: 7
status: issues_found
---

# Phase 03-pass: Code Review Report

**Reviewed:** 2026-08-03T23:44:37Z
**Depth:** standard
**Files Reviewed:** 42
**Status:** issues_found

## Summary

Phase 3(이용권)의 등록·수동 가감·기간 수정·취소·조회 경로를 `docs/policies.md`·`docs/decisions.md`(D-055~D-070)·`docs/conventions.md`를 기준으로 검토했다. "잔여 = `PassTransaction` 이력 합계" 핵심 불변식은 `Pass.register`/`AdminPassService.register`/`adjust`/`cancel` 전 경로에서 이력 없는 잔여 변경 지점을 찾지 못했고, `PassLedgerInvariantTest`가 이를 통합테스트로 실제 PostgreSQL에서 증명한다. `adjustRemainingCount`/`zeroRemainingCount`의 조건부 UPDATE(D-021)도 의도한 대로 동작하며, DB CHECK 제약(`ck_pass_remaining_count_by_type`, `ck_pass_cancellation`, `ck_pass_transaction_amount_nonzero`)이 애플리케이션 우회 시에도 방어선 역할을 한다. IDOR 방어(`PassTransactionSpecifications.ownedByMember`, `note` 비노출 D-070)도 코드·테스트 양쪽에서 확인된다.

다만 아래 경계 케이스에서 Critical 수준은 아니지만 놓치기 쉬운 구멍을 5건 발견했다 — 두 건은 "3중 방어"로 문서화된 동시성 보호가 실제로는 일부 경로(취소 시 잔여 0/기간제, 기간 수정)에 적용되지 않는 지점이고, 한 건은 회원이 자신의 이용권 잔여를 확인할 수 없게 막는 회원 상태 게이트 범위 문제, 나머지는 예외 의미 불일치와 회원 이력 노출 범위 불일치다. Critical(BLOCKER)은 없다 — 발견된 문제들은 모두 저빈도 관리자 동시 조작이거나 UX/일관성 문제이며, 핵심 원장 불변식이나 인가 경계를 직접 깨지는 않는다.

## Warnings

### WR-01: `MemberPassService`가 `ON_LEAVE` 회원의 본인 이용권·이력 조회를 막는다

**File:** `src/main/kotlin/com/goldwrestling/pass/MemberPassService.kt:44,66`
**Issue:** `getMyPasses`/`getMyTransactions`가 둘 다 `memberStateGate.requireActive(principal)`을 먼저 호출한다. 이 게이트는 `principal.memberStatus != MemberStatus.ACTIVE`이면 무조건 403 `MEMBER_NOT_ACTIVE`를 던진다 — `PENDING`뿐 아니라 `ON_LEAVE`(휴회) 회원도 막힌다. `ON_LEAVE`는 policies §5에서 "정상적으로 로그인해 쓰는 상태(2주 미사용 차감만 정지)"로 정의되어 있고, 휴회 중인 회원이 복귀 전에 자신의 잔여 횟수·유효기간을 확인하려는 시나리오는 자연스럽다. 반면 같은 Phase 2의 `MemberProfileService.getMyProfile`은 "PENDING/거절 안내 화면이 이 응답에 의존한다"는 이유로 **의도적으로 이 게이트를 걸지 않는다** — Phase 3에서 새로 도입된 이 제한이 policies·requirements 어디에도 근거가 없다. 테스트도 `PENDING` 케이스만 있고 `ON_LEAVE`는 다루지 않아(`MemberPassControllerTest`, `MemberPassTransactionControllerTest`) 이 동작이 의도된 것인지 회귀인지 테스트로도 확인할 수 없다.
**Fix:** `ON_LEAVE` 회원도 조회를 허용하도록 게이트 조건을 완화하거나(`requireActive` 대신 "PENDING/INACTIVE만 차단"하는 별도 게이트), 의도적 제한이라면 그 근거를 `docs/decisions.md`에 남기고 `ON_LEAVE` 케이스에 대한 테스트를 추가한다.
```kotlin
// MemberStateGate에 회원 상태 조회처럼 "PENDING/INACTIVE만 차단"하는 별도 메서드를 추가하거나
// requireActive 자체를 재검토한다 — 최소한 이 선택이 의도적이라는 근거가 코드에 남아야 한다.
```

### WR-02: `Pass.register`가 초기 횟수 누락(`null`)에 대해 오해를 부르는 예외를 던진다

**File:** `src/main/kotlin/com/goldwrestling/pass/Pass.kt:234`
**Issue:** `SESSION_PASS`/`LESSON_PASS` 등록 시 `initialCount`가 `null`이면 `val count = initialCount ?: throw InvalidAdjustmentUnitException()`로 "필수값 누락"을 "0.5 단위여야 하며 0이 될 수 없습니다"라는 메시지의 `InvalidAdjustmentUnitException`으로 표현한다. 실제 원인(초기 횟수 미입력)과 에러 메시지(단위 위반)가 일치하지 않아 FE·관리자가 원인을 오인할 수 있다. `PassRegistrationTest`에도 `initialCount = null` 케이스에 대한 검증이 없어(0/음수/0.3 케이스만 있음) 이 문구 불일치가 테스트로 드러나지 않는다.
**Fix:** "필수값 누락"과 "형식 위반"을 구분하는 별도 예외(또는 동일 `ErrorCode`라도 메시지를 분기)로 처리하고, `initialCount = null` 케이스의 단위테스트를 추가한다.
```kotlin
val count = initialCount ?: throw InvalidPassPeriodException("횟수권·레슨권은 초기 횟수를 지정해야 합니다.")
validateInitialCount(count)
```

### WR-03: 상쇄 수량이 0인 취소 경로(`EVENING_MEMBERSHIP`, 잔여 0 횟수권)에 동시성 보호가 없다

**File:** `src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt:217-221`, `src/main/kotlin/com/goldwrestling/pass/Pass.kt:173-186`
**Issue:** `AdminPassService.cancel()`은 `offset == 0`이면(D-065 — 기간제이거나 잔여가 이미 0인 횟수권) `zeroRemainingCount`를 호출하지 않고 바로 반환한다. 이 경로에는 `adjustRemainingCount`/`zeroRemainingCount` 같은 조건부 UPDATE도, `@Version` 낙관적 락도 없다 — `pass.cancel()`이 채운 `status`/`canceledAt`/`cancelReason`/`canceledBy`는 평범한 Hibernate dirty-check UPDATE로 커밋 시점에 flush된다. 두 관리자가 같은 `EVENING_MEMBERSHIP`(또는 잔여 0인 `SESSION_PASS`) 이용권을 거의 동시에 취소 요청하면, 두 트랜잭션 모두 `requireNotCanceled()` 검사를 통과하고(둘 다 취소 전 스냅샷을 읽음) 둘 다 200을 받는다 — 나중에 커밋하는 트랜잭션의 `cancelReason`/`canceledBy`가 먼저 커밋한 트랜잭션의 값을 조용히 덮어쓰고, `PASS_ALREADY_CANCELED`로 거부되어야 할 두 번째 요청이 오히려 성공 응답을 받는다. `offset != 0`인 경로(가장 흔한 케이스)는 `zeroRemainingCount`의 조건부 UPDATE + 실패 시 롤백 덕분에 이 문제가 없다 — 03-05-SUMMARY.md가 말하는 "3중 방어"가 이 분기에는 적용되지 않는다.
**Fix:** `Pass`에 `@Version` 컬럼을 추가하거나, `offset == 0` 분기에서도 `status`를 조건부 UPDATE(`where status = 'ACTIVE'`)로 반영해 동시 취소 경쟁에서 한쪽이 `PASS_ALREADY_CANCELED`를 받도록 한다.

### WR-04: 기간·유효기간 수정(`changePeriod`)에 낙관적 락·조건부 UPDATE가 전혀 없다

**File:** `src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt:153-188`, `src/main/kotlin/com/goldwrestling/pass/Pass.kt:145-161`
**Issue:** `changePeriod`는 `pass.startDate`/`pass.endDate`를 평범한 `var` 대입으로 바꾸고 Hibernate dirty-check flush에 의존한다. `Pass` 엔티티에 `@Version`이 없고, 이 갱신에는 `adjustRemainingCount`/`zeroRemainingCount`류의 조건부 UPDATE도 없다. 두 관리자가 같은 이용권의 기간을 동시에 수정하면(예: A는 유효기간을 6개월 연장, B는 3개월만 연장) 나중에 커밋한 트랜잭션이 먼저 커밋한 트랜잭션의 값을 완전히 덮어쓴다 — `PassPeriodChange` 이력에는 두 건이 각각 남지만(전값이 같은 스냅샷 기준이라 실제로는 A의 변경을 반영하지 못한 이력이 남을 수 있음), 최종 `pass` 상태는 나중에 커밋한 쪽만 반영되어 조용히 유실(lost update)된다. `PassRepositoryTest`/`AdminPassControllerTest` 어디에도 동시 수정 시나리오 테스트가 없다.
**Fix:** `Pass`에 `@Version`을 추가해 JPA 낙관적 락으로 두 번째 커밋이 `OptimisticLockException`(→ `PASS_STATE_CONFLICT` 매핑)을 받게 하거나, `changePeriod`도 전값을 조건으로 하는 조건부 UPDATE로 전환한다.

### WR-05: 회원 본인 이력 조회가 취소된 이용권의 거래 내역까지 그대로 노출한다

**File:** `src/main/kotlin/com/goldwrestling/pass/MemberPassService.kt:62-79`
**Issue:** `getMyPasses`는 `PassStatus.CANCELED`인 이용권을 D-058/D-059에 따라 목록에서 제외하지만, `getMyTransactions`는 `passId` 필터만 소유권으로 걸 뿐(`PassTransactionSpecifications.ownedByMember` + `hasPassId`) 대상 `Pass`의 상태는 전혀 검사하지 않는다. 결과적으로 회원이 `passId`로 취소된 이용권을 필터링하면(또는 필터 없이 전체 조회해도) `REGISTRATION_CANCELED` 사유의 거래 내역(금액, `passType`, 발생 시각)이 그대로 응답에 포함된다 — `note`(관리자 취소 사유)는 D-070에 따라 숨겨지지만, "이 이용권이 관리자에 의해 취소되었다"는 사실 자체와 상쇄 금액은 그대로 노출된다. `getMyPasses`가 취소된 이용권을 "회원 화면에서 숨긴다"고 명시한 D-059의 취지와 어긋날 수 있는 노출 범위 불일치이며, 이 시나리오에 대한 테스트가 없다.
**Fix:** 의도된 동작인지 확인이 필요하다 — 만약 숨겨야 한다면 `getMyTransactions`에도 `Pass.status <> CANCELED` 조건(또는 `PassTransactionSpecifications`에 상태 조건 추가)을 걸고, 의도된 노출이라면 그 근거를 `docs/decisions.md`에 남기고 테스트로 고정한다.

## Info

### IN-01: `AdminPassService.adjust`/`cancel`에서 detach된 `admin` 참조가 벌크 UPDATE 이후 재사용된다

**File:** `src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt:110-137, 210-241`
**Issue:** `adjustRemainingCount`/`zeroRemainingCount`는 `clearAutomatically = true`라 실행 직후 영속성 컨텍스트 전체가 비워지며, 이전에 조회한 `admin`(`Admin` 엔티티)도 함께 준영속(detached) 상태가 된다. 이후 `PassTransaction(admin = admin, ...)`으로 새 엔티티를 저장할 때 detached 참조를 그대로 쓴다. `admin`은 LAZY 연관을 건드리지 않고 식별자만 필요하므로 지금은 동작하지만(`RefreshTokenRepository` 계열과 동일 관례), Hibernate 버전·설정에 따라 detached 엔티티 참조가 예외를 던질 수 있는 영역이라 다음에 이 패턴을 건드리는 사람이 "왜 admin은 재조회하지 않는가"를 오해하지 않도록 KDoc에 한 줄 근거를 남겨두면 좋다.
**Fix:** 필요하면 `admin.id`만 별도 변수로 뽑아 `adminRepository.getReferenceById(adminId)`로 재획득하거나, 현재 방식이 의도적임을 밝히는 주석을 `admin` 조회 지점에 추가한다.

### IN-02: 취소·가감 사유 문자열이 이중으로 `trim()`된다

**File:** `src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt:217, 237`
**Issue:** `pass.cancel(request.reason, admin, now)` 내부에서 이미 `reason.trim()`을 저장하는데(`Pass.kt:182`), 바로 다음 줄에서 `note = request.reason.trim()`으로 원본 `request.reason`을 다시 `trim()`한다. 기능상 문제는 없지만(같은 결과), 같은 값을 두 번 계산하는 중복이라 다음 변경(예: trim 규칙 변경) 시 두 곳을 함께 고쳐야 하는 함정이 된다.
**Fix:** `pass.cancel()`이 반환하는 trim된 `cancelReason` 값을 재사용하거나, 최소한 두 지점의 trim 로직이 항상 같아야 한다는 점을 주석으로 남긴다.

---

_Reviewed: 2026-08-03T23:44:37Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
