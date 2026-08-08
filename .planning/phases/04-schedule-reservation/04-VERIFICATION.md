---
phase: 04-schedule-reservation
verified: 2026-08-08T08:01:57Z
status: human_needed
score: 5/5 (Success Criteria) — 13/13 requirements test-backed — 2 non-blocking WARNING follow-ups open
overrides_applied: 0
human_verification:
  - test: "카카오 로그인 경로를 실제로 거쳐 발급된 회원 토큰으로 04-15 Task 3의 16개 항목 중 A(회원 흐름) 1~7을 재확인한다"
    expected: "JWT_SECRET 직접 서명 토큰과 동일한 동작(예약 생성·차감·취소·변경 거부)이 실제 카카오 로그인 세션에서도 나온다"
    why_human: "04-15-SUMMARY.md가 명시적으로 밝힌 검증 범위 밖 — 회원 토큰은 카카오 인가 코드 교환 없이 JWT_SECRET으로 직접 서명해 발급됐다. 카카오 발급 경로 자체는 Phase 2에서 검증됐다고 되어 있으나, Phase 4가 실제로 여는 예약 API들을 카카오 로그인 세션으로 호출한 적은 없다"
  - test: "WR-01(휴강 처리 중 회원 자가 취소 경합 시 canceledReservationCount 과대 집계)·WR-02(휴강 캐스케이드 중 이용권 등록취소 경합 시 500)를 실제로 재현해 보고, 다음 phase로 넘어가기 전에 수정할지 결정한다"
    expected: "두 WARNING이 실제 운영에서 발생 빈도가 낮고 데이터 정합성에 영향이 없다는 코드 리뷰 판단에 동의하면 후속 이슈로만 남기고, 아니면 04-14 플랜에 대한 gap-closure를 요청한다"
    why_human: "코드 리뷰(04-REVIEW.md)가 이미 근거를 갖고 WARNING으로 분류했고 이 검증에서도 코드로 재확인했다 — 그러나 '이 정도면 다음 phase로 넘어가도 되는가'는 리스크 허용 판단이라 자동 검증으로 결론 낼 수 없다"
---

# Phase 4: 시간표·예약 Verification Report

**Phase Goal:** 회원이 주간 시간표를 보고 예약제 수업·1:1 레슨을 예약·취소·변경하면 이용권이 즉시 차감/복구되고, 동시 경쟁 상황에서도 정원을 초과하지 않으며, 관리자가 시간표와 예약 전반을 운영한다.
**Verified:** 2026-08-08T08:01:57Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria, 5항목)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | 주(월~일) 시간표에서 SESSION 예약 → `SESSION_PASS` 즉시 1회 차감, 잔여 부족 시 거부. 다음 주는 조회만 가능 | ✓ VERIFIED | `ReservationLedgerSupport.createReservation`이 `passRepository.adjustRemainingCount(passId, amount.negate())`(조건부 UPDATE, `remainingCount + amount >= 0` WHERE절)로 즉시 차감하고 0 반환 시 `InsufficientPassCountException`. `ReservationWindow`(`ReservationWindowTest`)·`WeekRange`(`WeekRangeTest`)가 오픈/마감·조회범위 판정. 04-15 실측(항목 1~2): SESSION 16셀 `capacity=10`·`reservedCount=0`, 다음 주(`weekStart=2026-08-10`) 조회는 200이지만 52셀 전부 `bookable=false` |
| 2 | LESSON은 타임당 1명 한도, `LESSON_PASS` 즉시 1회 차감 | ✓ VERIFIED | DB 부분 유니크 인덱스 `ux_reservation_lesson_slot_active`(V6, `WHERE status='ACTIVE' AND class_type='LESSON'`)가 1차 방어선, `incrementReservedCountIfCapacityAvailable`(조건부 UPDATE)이 2차 방어선. `ReservationCapacityConcurrencyTest`의 "1대1 레슨 슬롯에 10명이 동시에 예약하면 정확히 1건만 성공한다" 시나리오가 `successCount==1` 단언 |
| 3 | 당일 아닌 예약은 취소(즉시 복구)·변경(취소+재예약) 가능, 당일은 취소·변경 모두 거부. 본인 예약 목록 조회 | ✓ VERIFIED | `ReservationCancellationPolicyTest`(TDD)가 당일 거부 판정, `MemberReservationCancellationTest`가 즉시 복구·취소+재예약 단일 트랜잭션을 검증. IDOR 방어는 `ReservationRepository.findByIdAndMemberId` 사용 확인(코드). 04-15 실측(항목 6~7): 당일 취소·변경 양쪽 모두 `409 SAME_DAY_MODIFICATION_NOT_ALLOWED`, 변경 시 `CANCEL_REFUND`+`RESERVE` 이력 둘 다 기록 |
| 4 | 정원 마지막 자리·1:1 슬롯 동시 요청 → 정확히 정원 수만큼 성공, 초과 예약 0건. 성공 건수·DB 예약 행 수·`PassTransaction` 이력 건수 일치 | ✓ VERIFIED | 코드 레벨: DB 부분 유니크 인덱스 3종(V6) + 조건부 UPDATE(`incrementReservedCountIfCapacityAvailable`, `adjustRemainingCount`)의 이중 방어. `ReservationCapacityConcurrencyTest` 3개 시나리오(정원 20명 경쟁→정확히 10건, 1:1 10명 경쟁→정확히 1건, 동일 회원 10중 요청→정확히 1건)가 "성공=예약 행=RESERVE 이력=reservedCount" 4자 일치를 단언. 오케스트레이터가 이미 2회 연속 재실행으로 flaky 아님을 확인 |
| 5 | 관리자 주간 스케줄 보드(셀별 예약자 명단)·전체 예약 조회·당일 포함 대리 취소(복구 선택)·변경·휴강 캐스케이드(전부 취소+`CLASS_CANCELED_REFUND`+알림+예약 불가 표시) | ✓ VERIFIED (정상 경로) — WARNING 2건 별도 관리 | `AdminScheduleService.getWeeklyBoard`가 `BoardReservationResponse`(reservationId·memberId·memberName 3필드 고정)로 명단 반환, `AdminReservationService.search`가 필터+페이지네이션, `cancelByAdmin`이 `request.refund` 값 그대로 저장(복구 선택), `suspend()`가 세션 선전환→N건 일괄 취소→`CLASS_CANCELED_REFUND` 이력→세션당 1건 알림을 단일 `@Transactional` 안에서 처리. 04-15 실측(항목 8~14) 16/16 PASS. **단, `AdminScheduleService.suspend()`의 좁은 동시성 경계에서 WR-01(응답 건수 과대 집계)·WR-02(경합 시 500 롤백)이 코드 확인됨 — 아래 "Warnings" 절 참조** |

**Score:** 5/5 truths verified (WR-01/WR-02는 Success Criteria 5의 정상 경로 달성을 막지 않음 — 아래 판정 근거 참조)

### Required Artifacts (표본 — 도메인 로직 산출물)

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `ReservationLedgerSupport.kt` | 회원 셀프·관리자 대리·휴강 캐스케이드가 공유하는 단일 차감/복구/이력 지점 | ✓ VERIFIED | `createReservation`(차감+RESERVE 이력)·`restoreAfterCancellation`(단건 복구, 정원+잔여+이력)·`restorePassAfterCancellation`(잔여+이력만, 휴강 캐스케이드용)이 `MemberReservationService`·`AdminReservationService`·`AdminScheduleService` 3곳에서 공통 호출됨을 코드로 확인. 이력 없는 잔여 변경 경로 없음 |
| `V6__create_schedule_reservation_notification.sql` | 부분 유니크 인덱스 3종 + CHECK 제약 | ✓ VERIFIED | `ux_reservation_session_member_active`·`ux_reservation_lesson_slot_active`·`ux_reservation_member_timeslot_active` 3종 확인. `ck_class_session_reserved_count`(정원 초과 방지), `ck_reservation_cancellation`(취소 메타 완전성+주체 배타성) 존재 |
| `V8__extend_pass_transaction_subject.sql` | `admin_id`/`member_id` 배타성 CHECK | ✓ VERIFIED | `ck_pass_transaction_subject CHECK ((admin_id IS NOT NULL AND member_id IS NULL) OR (admin_id IS NULL AND member_id IS NOT NULL))` 확인 |
| `ScheduleCellResponse.kt` / `ReservationResponse.kt` | 회원 대상 응답에 타 회원 정보 없음 | ✓ VERIFIED | 두 DTO 모두 필드에 회원명/명단 없음. `BoardReservationResponse`(관리자 전용, 3필드 고정)만 명단 포함 |
| `PassRepository.adjustRemainingCount` | 애플리케이션 검사가 아니라 DB 조건부 UPDATE로 잔여 부족 방어 | ✓ VERIFIED | `WHERE p.status = ACTIVE AND p.remainingCount + :amount >= 0` — 0 반환 시 예외 |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `MemberReservationService.reserve/change/cancel` | `ReservationLedgerSupport` | 직접 호출 | WIRED | 예약 생성·복구가 공유 컴포넌트를 거침 |
| `AdminReservationService.cancelByAdmin/changeByAdmin` | `ReservationLedgerSupport` | 직접 호출 | WIRED | 관리자 대리 경로도 같은 차감/복구 지점 사용, 정원 우회 백도어 없음(코드 확인: `changeByAdmin`도 `incrementReservedCountIfCapacityAvailable` 경유) |
| `AdminScheduleService.suspend` | `ReservationLedgerSupport.restorePassAfterCancellation` | 루프 내 호출, `reason=CLASS_CANCELED_REFUND` | WIRED | 휴강 복구가 회원/관리자 취소(`CANCEL_REFUND`)와 이력 사유로 구분됨 |
| `MemberReservationController` | `ReservationRepository.findByIdAndMemberId` | IDOR 방어 | WIRED | 코드 확인 — 타인 예약 접근 시 404 |
| `ReservationCapacityConcurrencyTest` | `ClassSessionRepository.incrementReservedCountIfCapacityAvailable` + V6 부분 유니크 인덱스 | 동시성 테스트 | WIRED | 코드·테스트 단언 모두 확인 |

### Requirements Coverage (13/13)

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| SCHED-01 | 04-02, 04-03, 04-05 | 주간 반복 시간표 정의·조회 | ✓ SATISFIED | `class_schedule` 시드 52행(V7), `MemberScheduleControllerTest` |
| SCHED-02 | 04-01, 04-02, 04-04, 04-05 | 세션 실체화·해당 주만 예약 오픈 | ✓ SATISFIED | `ReservationWindowTest`, `WeekRangeTest`, `ClassSessionConcurrencyTest`(get-or-create 경쟁) |
| SCHED-03 | 04-11 | 관리자 주간 스케줄 보드 | ✓ SATISFIED | `AdminScheduleServiceTest`, `AdminScheduleControllerTest` |
| RESV-01 | 04-06, 04-07, 04-08 | SESSION 예약 시 `SESSION_PASS` 즉시 차감 | ✓ SATISFIED | `MemberReservationServiceTest`, `PassDeductionCandidateTest` |
| RESV-02 | 04-06, 04-07, 04-08 | LESSON 타임당 1명, `LESSON_PASS` 즉시 차감 | ✓ SATISFIED | `MemberReservationServiceTest`, `ReservationCapacityConcurrencyTest` |
| RESV-03 | 04-06 | 잔여 부족 시 거부 | ✓ SATISFIED | `ReservationPassPolicyTest`, `PassDeductionCandidateTest` |
| RESV-04 | 04-04, 04-09, 04-10 | 취소(즉시 복구)/변경(취소+재예약), 당일 거부 | ✓ SATISFIED | `ReservationCancellationPolicyTest`, `MemberReservationCancellationTest` |
| RESV-05 | 04-10 | 본인 예약 목록 조회 | ✓ SATISFIED | `MemberReservationControllerTest` |
| RESV-06 | 04-02, 04-03, 04-08 | 초과 예약 0건, DB 제약+동시성 테스트 | ✓ SATISFIED | V6 부분 유니크 인덱스 3종, `ReservationCapacityConcurrencyTest` |
| RESV-07 | 04-12 | 관리자 전체 예약 조회 | ✓ SATISFIED | `AdminReservationSearchTest` |
| RESV-08 | 04-13 | 관리자 대리 취소/변경 | ✓ SATISFIED | `AdminReservationCancellationTest` |
| RESV-09 | 04-14 | 휴강 처리 캐스케이드 | ✓ SATISFIED (WR-01/WR-02 예외 경로 WARNING, 정상 경로는 테스트로 뒷받침) | `ClassSessionSuspensionTest` |
| NOTIF-01 | 04-02, 04-07, 04-10, 04-13, 04-14 | 예약/휴강 이벤트 시 관리자 알림 생성 | ✓ SATISFIED | `NotificationServiceTest` |

ORPHANED 요구사항 없음 — REQUIREMENTS.md의 Phase 4 매핑 13종 전부가 플랜 `requirements` 필드에 등장한다.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `AdminScheduleService.kt` | 184-227 (`suspend`) | `canceledReservationCount`가 실제 취소 성공 건수가 아니라 조회 시점 스냅샷 크기(`snapshots.size`)를 그대로 사용 — 회원 자가 취소와 경합하는 좁은 창에서 과대 집계 가능 (WR-01, 코드 리뷰 04-REVIEW.md와 동일 지점 재확인) | ⚠️ Warning | 응답/알림 문구만 부정확. `reservedCount`·잔여·`PassTransaction` 이력은 영향 없음 |
| `AdminScheduleService.kt` (184-212) / `ReservationLedgerSupport.kt` (204-234) | — | `restorePassAfterCancellation`이 stale `passStatus` 스냅샷을 근거로 복구를 시도하다 `adjustRemainingCount`가 0을 반환하면 잡히지 않는 `IllegalStateException`을 던져 전역 핸들러가 500(`INTERNAL_ERROR`)으로 변환, 전체 휴강 트랜잭션 롤백 (WR-02, 04-REVIEW.md와 동일 지점 재확인) | ⚠️ Warning | 트랜잭션 원자성 덕에 데이터 손상은 없음(부분 커밋 없음) — 가용성 문제(재시도 필요)이며 정원이 큰 수업일수록 경합 창이 넓어짐 |
| `ClassSession.kt:74-77` | `assertReservable()`의 `EVENING` 분기 | 현재 유일한 호출부(`ReservationLedgerSupport.createReservation`)가 그 앞에서 `requiredPassType`으로 이미 걸러 도달 불가능한 방어 코드 (IN-01, 정보성) | ℹ️ Info | 동작에 영향 없음, 문서화만 필요 |

TBD/FIXME/XXX 등 미해결 debt marker는 phase 4가 수정한 91개 파일 중 발견되지 않음(코드 리뷰 91개 파일 스캔 결과와 일치).

### Behavioral Spot-Checks / Probe Execution

오케스트레이터가 이미 다음을 수행함(재실행하지 않고 근거로 채택):
- `./gradlew clean build` 그린(576 tests, 0 failures)
- 이전 phase 회귀 357 tests 실패 0건
- 동시성 테스트 2종 각 2회 연속 통과(flaky 아님)
- 실제 로컬 서버 HTTP 요청 + psql 조회 16/16 PASS (04-15-SUMMARY.md Task 3)

본 검증에서 추가로 코드 레벨 직접 확인(정적 검증, 서버 미기동): `PassRepository.adjustRemainingCount`·`ClassSessionRepository.incrementReservedCountIfCapacityAvailable/decrementReservedCount/suspendIfScheduled/resumeIfCanceled`·`ReservationRepository.cancelByMemberIfActive/cancelByAdminIfActive`가 전부 조건부 UPDATE(WHERE절에 상태·수량 조건)로 구현돼 있음을 소스에서 직접 읽어 확인 — 애플리케이션 레벨 검사만으로 끝나는 잔여/정원 변경 경로 없음.

### Human Verification Required

#### 1. 카카오 로그인 경로를 통한 실제 회원 토큰으로 재확인

**Test:** 카카오 인가 코드 교환을 실제로 거쳐 발급된 회원 액세스 토큰으로 04-15 Task 3 A항목(회원 예약 흐름 1~7)을 다시 실행
**Expected:** JWT_SECRET 직접 서명 토큰으로 확인한 것과 동일한 동작
**Why human:** 04-15-SUMMARY.md가 스스로 명시한 검증 범위 제외 사항 — 실제 회원 토큰 발급 경로(카카오)를 이번 phase의 예약 API 호출에 쓴 적이 없다. Phase 2에서 카카오 로그인 자체는 검증됐지만, "카카오로 로그인한 회원이 실제로 이 phase가 만든 예약 API를 호출"하는 조합은 확인되지 않았다

#### 2. WR-01·WR-02 리스크 수용 여부 결정

**Test:** 코드 리뷰가 지목한 두 WARNING(휴강 캐스케이드의 좁은 동시성 경계 문제)을 재현하거나, 발생 빈도·영향(데이터 손상 없음, 가용성/응답 정확도 문제)을 검토
**Expected:** "다음 phase로 진행 + 후속 이슈로 트래킹" 또는 "04-14 플랜에 gap-closure 요청" 중 하나를 결정
**Why human:** 자동 검증은 문제의 존재와 영향 범위(데이터 정합성 무영향, 좁은 경합 창)까지는 확인할 수 있지만 "이 정도 리스크면 milestone을 진행해도 되는가"는 사람의 리스크 허용 판단이 필요하다

## Gaps Summary

Blocking gap 없음. Success Criteria 5항목 전부 코드·테스트·실측으로 뒷받침되고, 요구사항 13종 전부 대응하는 실제 테스트 파일이 존재한다. Core Value("잔여 = 실제 사용 가능 횟수")를 깨는 이력 없는 잔여 변경 경로는 코드에서 발견되지 않았다(모든 차감/복구가 `ReservationLedgerSupport`·`AdminPassService`를 경유하고 DB 조건부 UPDATE로 보호됨).

두 건의 WARNING(WR-01 응답 건수 과대 집계, WR-02 좁은 동시성 경계에서 휴강 처리 500)은 코드 리뷰와 본 검증 양쪽에서 동일하게 확인됐다. 둘 다 (a) 정상 경로(경쟁 없음)에서는 발생하지 않고 04-14 자동 테스트·04-15 실측 16/16이 이를 뒷받침하며, (b) 발생하더라도 트랜잭션 원자성 덕에 데이터가 깨지지 않는다(전부 롤백 또는 응답 문구만 부정확). 이 두 특성 때문에 Success Criteria 5("휴강 캐스케이드가 전부 취소+복구+알림+예약 불가 표시")의 정상 동작 자체는 달성됐다고 판단해 BLOCKER로 분류하지 않았다. 다만 근본 수정 여부는 사람이 결정할 사안이라 human_verification 항목 2로 에스컬레이션한다.

---

_Verified: 2026-08-08T08:01:57Z_
_Verifier: Claude (gsd-verifier)_
