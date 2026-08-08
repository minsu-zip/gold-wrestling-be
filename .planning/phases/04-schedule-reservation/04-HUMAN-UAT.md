---
status: partial
phase: 04-schedule-reservation
source: [04-VERIFICATION.md]
started: 2026-08-08T08:03:53Z
updated: 2026-08-08T08:03:53Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. 카카오 로그인 경로로 발급된 회원 토큰으로 예약 흐름 재확인
expected: 카카오 인가 코드 교환으로 실제 발급된 회원 access token으로 04-15 Task 3의 A(회원 흐름) 1~7을 호출했을 때, `JWT_SECRET` 직접 서명 토큰과 동일한 동작(주간 시간표 조회, 예약 생성 201 + 잔여 즉시 차감, 중복 예약 409 `DUPLICATE_RESERVATION`, 본인 목록 조회, 변경 200 + 이력 2건, 당일 취소·변경 409 `SAME_DAY_MODIFICATION_NOT_ALLOWED`)이 나온다.
why_human: 04-15 Task 3 실측(16/16 PASS)에 쓴 회원 토큰은 카카오 인가 코드 교환 없이 `JWT_SECRET`으로 직접 서명해 발급했다. 카카오 로그인 자체는 Phase 2에서 검증됐지만, "카카오로 로그인한 회원이 Phase 4가 여는 예약 API를 호출"하는 조합은 아직 확인된 적이 없다. `JwtAuthenticationFilter`는 두 토큰을 동일하게 처리하므로 실패 가능성은 낮지만, 검증되지 않은 것을 검증된 것으로 기록하지 않기 위해 남긴다.
result: [pending]

### 2. WR-01·WR-02 리스크 수용 여부 결정
expected: 두 WARNING을 후속 과제로만 남길지, 04-14에 대한 gap-closure로 지금 수정할지 결정한다.
  - **WR-01** (`AdminScheduleService.kt:197-227`): 휴강 캐스케이드 중 회원이 동시에 자가 취소하면, 취소 실패분을 건너뛰는데도 알림·응답이 `snapshots.size`를 그대로 써서 `canceledReservationCount`가 실제 취소 건수보다 크게 나온다. 잔여·`PassTransaction` 이력은 정확하다. 관리자에게 틀린 숫자가 보이는 표시값 결함.
  - **WR-02** (`ReservationLedgerSupport.kt:213`): `shouldRestore(passStatus, ...)`가 스냅샷 시점의 `passStatus`를 쓰므로, 휴강 진행 중 대상 이용권이 다른 관리자에 의해 등록취소되면 조기 반환에 걸리지 않고 `adjustRemainingCount`(status=ACTIVE 조건부 UPDATE)가 0을 반환해 `IllegalStateException` → 500 + 휴강 전체 롤백. 도달에 4중 경쟁이 필요하고(등록취소 선행검사 통과 → 회원 예약 → 휴강 스냅샷 → 등록취소 커밋) 원자적으로 롤백되어 데이터는 깨지지 않는다.
why_human: 코드 리뷰(04-REVIEW.md)가 WARNING으로 분류하고 검증에서도 코드로 재확인했지만, "이 정도면 다음 phase로 넘어가도 되는가"는 리스크 허용 판단이라 자동 검증으로 결론 낼 수 없다. 특히 WR-02의 수정 방향(복구를 조용히 생략할 것인가)은 Core Value("잔여 = 실제 사용 가능 횟수")와 맞물려 있어 `docs/policies.md` 확인이 필요하다.
result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps
