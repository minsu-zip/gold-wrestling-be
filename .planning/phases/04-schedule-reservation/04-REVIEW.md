---
phase: 04-schedule-reservation
reviewed: 2026-08-08T07:57:27Z
depth: standard
files_reviewed: 91
files_reviewed_list:
  - docs/api/openapi.yaml
  - docs/decisions.md
  - docs/error-codes.md
  - docs/glossary.md
  - docs/policies.md
  - src/main/kotlin/com/goldwrestling/admin/AdminBranchNotAssignedException.kt
  - src/main/kotlin/com/goldwrestling/admin/AdminBranchRepository.kt
  - src/main/kotlin/com/goldwrestling/common/LikePatternEscaper.kt
  - src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt
  - src/main/kotlin/com/goldwrestling/common/time/WeekRange.kt
  - src/main/kotlin/com/goldwrestling/member/MemberSpecifications.kt
  - src/main/kotlin/com/goldwrestling/notification/Notification.kt
  - src/main/kotlin/com/goldwrestling/notification/NotificationRepository.kt
  - src/main/kotlin/com/goldwrestling/notification/NotificationService.kt
  - src/main/kotlin/com/goldwrestling/notification/NotificationType.kt
  - src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt
  - src/main/kotlin/com/goldwrestling/pass/PassExceptions.kt
  - src/main/kotlin/com/goldwrestling/pass/PassRepository.kt
  - src/main/kotlin/com/goldwrestling/pass/PassTransaction.kt
  - src/main/kotlin/com/goldwrestling/reservation/AdminReservationController.kt
  - src/main/kotlin/com/goldwrestling/reservation/AdminReservationService.kt
  - src/main/kotlin/com/goldwrestling/reservation/MemberReservationController.kt
  - src/main/kotlin/com/goldwrestling/reservation/MemberReservationService.kt
  - src/main/kotlin/com/goldwrestling/reservation/Reservation.kt
  - src/main/kotlin/com/goldwrestling/reservation/ReservationExceptions.kt
  - src/main/kotlin/com/goldwrestling/reservation/ReservationLedgerSupport.kt
  - src/main/kotlin/com/goldwrestling/reservation/ReservationPassPolicy.kt
  - src/main/kotlin/com/goldwrestling/reservation/ReservationRefundPolicy.kt
  - src/main/kotlin/com/goldwrestling/reservation/ReservationRepository.kt
  - src/main/kotlin/com/goldwrestling/reservation/ReservationSpecifications.kt
  - src/main/kotlin/com/goldwrestling/reservation/ReservationStatus.kt
  - src/main/kotlin/com/goldwrestling/reservation/dto/AdminCancelReservationRequest.kt
  - src/main/kotlin/com/goldwrestling/reservation/dto/AdminChangeReservationRequest.kt
  - src/main/kotlin/com/goldwrestling/reservation/dto/AdminReservationResponse.kt
  - src/main/kotlin/com/goldwrestling/reservation/dto/ChangeReservationRequest.kt
  - src/main/kotlin/com/goldwrestling/reservation/dto/MyReservationSearchCondition.kt
  - src/main/kotlin/com/goldwrestling/reservation/dto/ReservationResponse.kt
  - src/main/kotlin/com/goldwrestling/reservation/dto/ReservationSearchCondition.kt
  - src/main/kotlin/com/goldwrestling/reservation/dto/ReserveRequest.kt
  - src/main/kotlin/com/goldwrestling/schedule/AdminScheduleController.kt
  - src/main/kotlin/com/goldwrestling/schedule/AdminScheduleService.kt
  - src/main/kotlin/com/goldwrestling/schedule/ClassSchedule.kt
  - src/main/kotlin/com/goldwrestling/schedule/ClassScheduleRepository.kt
  - src/main/kotlin/com/goldwrestling/schedule/ClassSession.kt
  - src/main/kotlin/com/goldwrestling/schedule/ClassSessionRepository.kt
  - src/main/kotlin/com/goldwrestling/schedule/ClassSessionService.kt
  - src/main/kotlin/com/goldwrestling/schedule/ClassSessionStatus.kt
  - src/main/kotlin/com/goldwrestling/schedule/ClassType.kt
  - src/main/kotlin/com/goldwrestling/schedule/MemberScheduleController.kt
  - src/main/kotlin/com/goldwrestling/schedule/ReservationWindow.kt
  - src/main/kotlin/com/goldwrestling/schedule/ScheduleExceptions.kt
  - src/main/kotlin/com/goldwrestling/schedule/ScheduleGridSkeleton.kt
  - src/main/kotlin/com/goldwrestling/schedule/ScheduleService.kt
  - src/main/kotlin/com/goldwrestling/schedule/dto/AdminBoardCellResponse.kt
  - src/main/kotlin/com/goldwrestling/schedule/dto/AdminBoardDayResponse.kt
  - src/main/kotlin/com/goldwrestling/schedule/dto/AdminWeeklyBoardResponse.kt
  - src/main/kotlin/com/goldwrestling/schedule/dto/BoardReservationResponse.kt
  - src/main/kotlin/com/goldwrestling/schedule/dto/ClassSessionResponse.kt
  - src/main/kotlin/com/goldwrestling/schedule/dto/DayScheduleResponse.kt
  - src/main/kotlin/com/goldwrestling/schedule/dto/ScheduleCellResponse.kt
  - src/main/kotlin/com/goldwrestling/schedule/dto/SuspendClassSessionRequest.kt
  - src/main/kotlin/com/goldwrestling/schedule/dto/WeeklyScheduleResponse.kt
  - src/main/resources/db/migration/V6__create_schedule_reservation_notification.sql
  - src/main/resources/db/migration/V7__seed_class_schedule.sql
  - src/main/resources/db/migration/V8__extend_pass_transaction_subject.sql
  - src/test/kotlin/com/goldwrestling/auth/KakaoAuthControllerTest.kt
  - src/test/kotlin/com/goldwrestling/common/error/ErrorCodeRegistryTest.kt
  - src/test/kotlin/com/goldwrestling/common/time/WeekRangeTest.kt
  - src/test/kotlin/com/goldwrestling/notification/NotificationServiceTest.kt
  - src/test/kotlin/com/goldwrestling/pass/MemberPassTransactionControllerTest.kt
  - src/test/kotlin/com/goldwrestling/pass/PassCancellationWithReservationTest.kt
  - src/test/kotlin/com/goldwrestling/pass/PassDeductionCandidateTest.kt
  - src/test/kotlin/com/goldwrestling/pass/PassRepositoryTest.kt
  - src/test/kotlin/com/goldwrestling/reservation/AdminReservationCancellationTest.kt
  - src/test/kotlin/com/goldwrestling/reservation/AdminReservationSearchTest.kt
  - src/test/kotlin/com/goldwrestling/reservation/MemberReservationCancellationTest.kt
  - src/test/kotlin/com/goldwrestling/reservation/MemberReservationControllerTest.kt
  - src/test/kotlin/com/goldwrestling/reservation/MemberReservationServiceTest.kt
  - src/test/kotlin/com/goldwrestling/reservation/ReservationCancellationPolicyTest.kt
  - src/test/kotlin/com/goldwrestling/reservation/ReservationCapacityConcurrencyTest.kt
  - src/test/kotlin/com/goldwrestling/reservation/ReservationPassPolicyTest.kt
  - src/test/kotlin/com/goldwrestling/reservation/ReservationRefundPolicyTest.kt
  - src/test/kotlin/com/goldwrestling/reservation/ReservationRepositoryTest.kt
  - src/test/kotlin/com/goldwrestling/schedule/AdminScheduleControllerTest.kt
  - src/test/kotlin/com/goldwrestling/schedule/AdminScheduleServiceTest.kt
  - src/test/kotlin/com/goldwrestling/schedule/ClassScheduleSeedIntegrationTest.kt
  - src/test/kotlin/com/goldwrestling/schedule/ClassSessionConcurrencyTest.kt
  - src/test/kotlin/com/goldwrestling/schedule/ClassSessionRepositoryTest.kt
  - src/test/kotlin/com/goldwrestling/schedule/ClassSessionSuspensionTest.kt
  - src/test/kotlin/com/goldwrestling/schedule/MemberScheduleControllerTest.kt
  - src/test/kotlin/com/goldwrestling/schedule/ReservationWindowTest.kt
findings:
  critical: 0
  warning: 2
  info: 1
  total: 3
status: issues_found
---

# Phase 04: Code Review Report

**Reviewed:** 2026-08-08T07:57:27Z
**Depth:** standard
**Files Reviewed:** 91
**Status:** issues_found

## Summary

Phase 4 (시간표·예약) 구현을 리뷰했다. 예약 생성/취소/변경, 정원·1:1 슬롯 동시성, 이용권 차감/복구
원장, 휴강 캐스케이드, 회원/관리자 시간표·보드 조회, 알림 생성까지 전 경로를 프로덕션 코드 60개
파일과 테스트 27개 파일 기준으로 직접 추적했다.

핵심 결론: **Core Value(잔여 = 실제 사용 가능 횟수)를 깨는 결함은 발견하지 못했다.** 구체적으로 확인한 것:

- 정원·1:1 슬롯 동시성은 조건부 UPDATE(`incrementReservedCountIfCapacityAvailable`,
  `PassRepository.adjustRemainingCount`) + DB 부분 유니크 인덱스 3종(V6)의 이중 방어로 되어 있고,
  `ReservationCapacityConcurrencyTest`의 3개 시나리오(정원 20명 경쟁 → 정확히 10건, 1:1 10명 경쟁 →
  정확히 1건, 동일 회원 10중 요청 → 정확히 1건)가 "성공 건수 = 활성 예약 행 수 = RESERVE 이력
  건수 = reservedCount" 4자 일치를 실증한다.
- 차감/복구 경로(`ReservationLedgerSupport`)는 회원 셀프·관리자 대리·휴강 캐스케이드가 전부
  공유하는 단일 지점이라 이력 누락 경로가 보이지 않는다. `PassTransaction.admin`/`member`
  배타성도 모든 호출부에서 정확히 하나만 채워지는 것을 확인했다(V8 `ck_pass_transaction_subject`가
  최종 방어선).
- 휴강 캐스케이드(`AdminScheduleService.suspend`)는 세션 상태 전환·N건 취소·이력 저장·알림 생성이
  전부 하나의 `@Transactional` 안에서 일어나 부분 커밋 가능성이 없다(all-or-nothing).
- 회원 대상 응답(`ReservationResponse`, `ScheduleCellResponse`, `WeeklyScheduleResponse`)에는
  타 회원 정보가 전혀 없고, 명단이 필요한 `BoardReservationResponse`는 관리자 전용 엔드포인트에서만
  쓰인다(D-096 준수).
- 주간 그리드 조회(회원 시간표·관리자 보드)는 셀 수와 무관하게 고정 쿼리 3회로 끝나며 N+1이 없다.

찾은 결함은 전부 **관리자 휴강 처리(`AdminScheduleService.suspend`)의 극단적인 동시성 경계 상황**에
관한 것으로, 데이터 정합성(잔여·이력)에는 영향이 없고 가용성/정확성 측면의 WARNING 2건이다.

## Warnings

### WR-01: 휴강 처리 중 회원이 동시에 자가 취소하면 `canceledReservationCount`가 실제보다 과대 집계될 수 있다

**File:** `src/main/kotlin/com/goldwrestling/schedule/AdminScheduleService.kt:184-227`
**Issue:**
`suspend()`는 ④에서 활성 예약을 배치 조회해 `snapshots`(크기 N)를 만들고, ⑤ 루프에서 예약별로
`cancelByAdminIfActive`를 호출한다. 이 CAS가 0을 반환하면(그 사이 회원이 스스로 취소해 이미
`CANCELED`가 된 경우) `return@forEach`로 건너뛰지만, 마지막 알림·응답 생성에 쓰는
`canceledReservationCount`는 여전히 `snapshots.size`(스킵된 건 포함)를 그대로 쓴다.

```kotlin
snapshots.forEach { snapshot ->
    if (reservationRepository.cancelByAdminIfActive(snapshot.reservationId, admin, true, now) == 0) {
        return@forEach   // 스킵되지만 카운트에서는 빠지지 않는다
    }
    ...
}
...
notificationService.createClassSessionSuspended(refreshedSession, snapshots.size)
return ClassSessionResponse.from(refreshedSession, canceledReservationCount = snapshots.size)
```

`reservedCount`(리셋)·잔여 이용권·`PassTransaction` 이력은 정확하다 — 영향받는 것은 **응답의
`canceledReservationCount`와 알림 메시지에 박히는 건수 문구**뿐이다. 확률은 낮지만(회원 자가 취소와
관리자 휴강 처리가 같은 예약을 정확히 이 좁은 창에서 경합해야 함), 재현 가능한 카운트 오차이고
관리자에게 잘못된 정보("N건이 자동 취소되었습니다")를 남긴다.

**Fix:** 스킵된 건을 별도로 카운트해 실제 취소 성공 건수만 보고한다.
```kotlin
var actuallyCanceled = 0
snapshots.forEach { snapshot ->
    if (reservationRepository.cancelByAdminIfActive(snapshot.reservationId, admin, true, now) == 0) {
        return@forEach
    }
    actuallyCanceled++
    reservationLedgerSupport.restorePassAfterCancellation(...)
}
...
notificationService.createClassSessionSuspended(refreshedSession, actuallyCanceled)
return ClassSessionResponse.from(refreshedSession, canceledReservationCount = actuallyCanceled)
```

### WR-02: 휴강 캐스케이드 도중 대상 이용권이 동시에 등록취소되면 전체 휴강 처리가 500으로 실패한다

**File:** `src/main/kotlin/com/goldwrestling/schedule/AdminScheduleService.kt:184-212`,
`src/main/kotlin/com/goldwrestling/reservation/ReservationLedgerSupport.kt:204-234`
**Issue:**
`suspend()`의 ④에서 뽑은 `snapshot.passStatus`는 배치 조회 시점 스냅샷이다. `AdminPassService.cancel`의
활성 예약 선행 검사(D-089, `existsByPassIdAndStatus`)는 그 시점에 아직 `CANCELED`로 전환되지
않은 이 예약을 "활성"으로 보고 통과시킬 수 있다 — 즉 다른 관리자 트랜잭션이 정확히 이 좁은 창에서
같은 이용권을 등록취소로 확정할 수 있다. 그 뒤 `suspend()`의 루프가 그 예약을 처리하며
`restorePassAfterCancellation(passStatus = snapshot.passStatus /* 여전히 ACTIVE로 기록된 stale 값 */)`를
호출하면, `ReservationRefundPolicy.shouldRestore`는 stale 값을 보고 "복구해야 한다"로 판정하지만
실제 DB의 `adjustRemainingCount`는 `status = ACTIVE` 조건에 걸려 0을 반환한다. 이 경로는
`IllegalStateException`을 던지도록 명시적으로 구현되어 있다(`restorePassAfterCancellation` KDoc상
의도된 "있어선 안 되는 상태"):

```kotlin
if (passRepository.adjustRemainingCount(passId, ReservationPassPolicy.DEDUCTION_AMOUNT) == 0) {
    throw IllegalStateException(
        "복구 대상 이용권(id=$passId)이 판정 이후 상태가 바뀌어 복구를 반영하지 못했습니다.",
    )
}
```

이 예외는 어디서도 잡히지 않으므로 전역 핸들러의 포괄 `Exception` 분기(`INTERNAL_ERROR`, 500)로
빠지고, `suspend()` 전체 트랜잭션이 롤백된다. 트랜잭션이 원자적이라 **데이터가 깨지지는 않는다**
(이미 처리된 N-1건도 함께 롤백되어 부분 커밋은 없다) — 그러나 관리자가 정원 10명짜리 휴강 처리를
시도할 때마다 이 경합 창이 열려 있고, 처리 대상 예약 수가 많을수록(정원이 큰 수업일수록) 부딪힐
확률이 커진다. 정상적인 휴강 요청이 무관한 동시 작업(다른 관리자의 이용권 등록취소) 때문에 완전히
실패하고 재시도를 요구받는 것은 가용성 관점에서 취약하다.

**Fix:** 두 가지 선택지 중 하나를 권장한다.
1. (최소 변경) `restorePassAfterCancellation` 호출 직전에 해당 `passId`의 현재 상태를 재조회해
   `shouldRestore` 판정을 최신 값으로 다시 하거나, `adjustRemainingCount` 반환 0을 "정책상 복구
   안 함"으로 처리(로그만 남기고 건너뜀)하도록 캐스케이드 경로 전용 분기를 추가한다 — 단일 취소
   경로(`restoreAfterCancellation`)의 "있어선 안 되는 상태" 가정과 캐스케이드 경로의 "여러 건을
   순회하며 다른 트랜잭션과 부딪힐 확률이 구조적으로 더 높다"는 특성을 분리해서 다룬다.
2. (근본 해결) `AdminPassService.cancel`의 D-089 선행 검사와 상태 전환 사이의 경합 창을 좁힌다 —
   예를 들어 등록취소 조건부 UPDATE(`cancelIfNotCanceled`) 직후 다시 한번
   `existsByPassIdAndStatus`를 확인해 그 사이 활성 예약이 생겼다면 롤백하는 이중 검사를 추가한다.

## Info

### IN-01: `ClassSession.assertReservable()`의 `EVENING` 분기가 현재 호출 경로상 도달 불가능하다

**File:** `src/main/kotlin/com/goldwrestling/schedule/ClassSession.kt:74-77`,
`src/main/kotlin/com/goldwrestling/reservation/ReservationLedgerSupport.kt:77-80`
**Issue:** `assertReservable()`의 유일한 호출부인 `ReservationLedgerSupport.createReservation`은
`session.assertReservable()`을 부르기 **전에** 이미 `ReservationPassPolicy.requiredPassType(scheduleClassType)`를
호출해 `EVENING`이면 `ClassSessionNotReservableException`을 던진다. 따라서
`assertReservable()`의 `!classType.reservable` 분기는 현재 코드베이스에서 실행될 수 없는
방어 코드다(버그는 아니다 — 두 번째 방어선으로 남겨둔 의도로 보이나, 죽은 경로라는 사실이 문서화돼
있지 않다).
**Fix:** 의도적 이중 방어라면 KDoc에 "현재 유일한 호출부에서는 이 분기에 도달하지 않지만, 향후
`assertReservable`을 직접 호출하는 새 경로가 생길 것에 대비한 방어선"이라고 명시한다. 그렇지 않다면
`requiredPassType` 판정과 중복이므로 정리를 고려한다.

---

_Reviewed: 2026-08-08T07:57:27Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
