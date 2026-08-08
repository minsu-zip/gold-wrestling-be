---
status: passed
phase: 04-schedule-reservation
source: [04-VERIFICATION.md]
started: 2026-08-08T08:03:53Z
updated: 2026-08-08T09:30:00Z
---

## Current Test

[전 항목 완료 — 항목 2의 WR-02는 후속 이슈 #12로 이월 확정 (사용자 결정, 2026-08-08)]

## Tests

### 1. 카카오 로그인 경로로 발급된 회원 토큰으로 예약 흐름 재확인
expected: 카카오 인가 코드 교환으로 실제 발급된 회원 access token으로 04-15 Task 3의 A(회원 흐름) 1~7을 호출했을 때, `JWT_SECRET` 직접 서명 토큰과 동일한 동작(주간 시간표 조회, 예약 생성 201 + 잔여 즉시 차감, 중복 예약 409 `DUPLICATE_RESERVATION`, 본인 목록 조회, 변경 200 + 이력 2건, 당일 취소·변경 409 `SAME_DAY_MODIFICATION_NOT_ALLOWED`)이 나온다.
why_human: 04-15 Task 3 실측(16/16 PASS)에 쓴 회원 토큰은 카카오 인가 코드 교환 없이 `JWT_SECRET`으로 직접 서명해 발급했다. 카카오 로그인 자체는 Phase 2에서 검증됐지만, "카카오로 로그인한 회원이 Phase 4가 여는 예약 API를 호출"하는 조합은 아직 확인된 적이 없다. `JwtAuthenticationFilter`는 두 토큰을 동일하게 처리하므로 실패 가능성은 낮지만, 검증되지 않은 것을 검증된 것으로 기록하지 않기 위해 남긴다.
result: passed (2026-08-08 17:36 KST, 항목 1~6 재확인 / 항목 7은 재현 불가 — 아래 범위 참고)

사용자가 실제 로그인 세션에서 발급된 회원 access token(`sub=2`, `principalType=MEMBER`, 헤더에 `kid` 존재 — 앱의 `NimbusJwtEncoder`가 발급한 토큰임을 뜻한다. 직접 서명 토큰에는 `kid`가 없었다)을 제공했고, 그 토큰으로 아래를 재실행했다:

| # | 결과 | 근거 |
|---|------|------|
| 1 | PASS | `weekStart=2026-08-03`·`weekEnd=2026-08-09`, EVENING 10셀 `reservable=false`+`capacity=null`, SESSION 16셀 `capacity=10`, 응답에 타 회원 이름 필드 0건 |
| 2 | PASS | 다음 주(`2026-08-10`) 200 + `bookableWeek=false` + 52셀 중 bookable 0. 3주 뒤는 `409 RESERVATION_WINDOW_CLOSED` |
| 3 | PASS | 새 `SESSION_PASS`(passId=3) 등록 후 예약 201(`id=6`), 잔여 `10.0 → 9.0` 즉시 차감 |
| 4 | PASS | 같은 타임 재예약 `409 DUPLICATE_RESERVATION` |
| 5 | PASS | `totalElements=1` — 이전 확인에서 취소된 예약 5건은 나오지 않는다(취소 숨김) |
| 6 | PASS | 변경 200(새 `id=7`), `CANCEL_REFUND(+1.0)`·`RESERVE(-1.0)` 2건 모두 기록, 잔여 `9.0` 유지 |

**항목 7 미재실행 (범위 밖으로 종결):** 재확인 시각이 17:36이었고 당일 마지막 예약 가능 타임이 17:00 시작이라, 당일 예약을 새로 만들 수 없어 "당일 취소·변경 거부"를 재현할 수 없었다. 재실행하지 않은 채로 종결하는 근거: 토큰은 인증된 주체(`principalId`)만 결정하고, 당일 판정은 `Clock` + `classDate`로 이뤄지므로 토큰 출처와 무관하다. 항목 1~6이 "앱이 발급한 토큰이 동일한 서비스 경로를 그대로 통과한다"를 이미 보였고, 항목 7 자체는 04-15 Task 3에서 취소·변경 양쪽 모두 `409 SAME_DAY_MODIFICATION_NOT_ALLOWED`로 확인됐다.

### 2. WR-01·WR-02 리스크 수용 여부 결정
expected: 두 WARNING을 후속 과제로만 남길지, 04-14에 대한 gap-closure로 지금 수정할지 결정한다.
  - **WR-01** (`AdminScheduleService.kt:197-227`): 휴강 캐스케이드 중 회원이 동시에 자가 취소하면, 취소 실패분을 건너뛰는데도 알림·응답이 `snapshots.size`를 그대로 써서 `canceledReservationCount`가 실제 취소 건수보다 크게 나온다. 잔여·`PassTransaction` 이력은 정확하다. 관리자에게 틀린 숫자가 보이는 표시값 결함.
  - **WR-02** (`ReservationLedgerSupport.kt:213`): `shouldRestore(passStatus, ...)`가 스냅샷 시점의 `passStatus`를 쓰므로, 휴강 진행 중 대상 이용권이 다른 관리자에 의해 등록취소되면 조기 반환에 걸리지 않고 `adjustRemainingCount`(status=ACTIVE 조건부 UPDATE)가 0을 반환해 `IllegalStateException` → 500 + 휴강 전체 롤백. 도달에 4중 경쟁이 필요하고(등록취소 선행검사 통과 → 회원 예약 → 휴강 스냅샷 → 등록취소 커밋) 원자적으로 롤백되어 데이터는 깨지지 않는다.
why_human: 코드 리뷰(04-REVIEW.md)가 WARNING으로 분류하고 검증에서도 코드로 재확인했지만, "이 정도면 다음 phase로 넘어가도 되는가"는 리스크 허용 판단이라 자동 검증으로 결론 낼 수 없다. 특히 WR-02의 수정 방향(복구를 조용히 생략할 것인가)은 Core Value("잔여 = 실제 사용 가능 횟수")와 맞물려 있어 `docs/policies.md` 확인이 필요하다.
result: passed (2026-08-08 — WR-01 수정 완료, WR-02는 후속 이슈 #12로 이월 확정)

사용자 결정: **WR-01만 지금 수정하고 WR-02는 후속으로 남긴다.**

- **WR-01 — 해결됨.** 커밋 `a6fc932` `fix(04-14): 휴강 취소 건수를 실제 성공 건수로 집계 (WR-01)`.
  `AdminScheduleService.suspend`가 `snapshots.size` 대신 실제 취소 성공 건수(`canceledCount`)를 알림·응답에 쓴다.
  회귀 테스트 `ClassSessionSuspensionTest > 휴강 도중 회원이 먼저 취소한 건은 취소 건수에서 제외된다` 추가 —
  경합 창이 좁아 스레드로 재현할 수 없어 `@MockitoSpyBean`으로 `cancelByAdminIfActive`가 특정 예약 1건에만
  0을 반환하도록 주입한다. **수정을 되돌리면 이 테스트가 실패하는 것을 확인했다**(통과만 보고 회귀 테스트라
  단정하지 않기 위해). 전체 스위트 577 tests, 0 failures.
- **WR-02 — 이월 확정.** `ReservationLedgerSupport.restorePassAfterCancellation`이 스냅샷 `passStatus`로
  `shouldRestore`를 판정하는 문제. 수정 방향이 "라이브 상태 재조회" vs "조건부 UPDATE 0행이면 조용히 생략"
  둘로 갈리고, 후자는 Core Value와 맞물려 `docs/policies.md` 확인이 필요하다. 도달에 4중 경쟁이 필요하고
  원자적으로 롤백되어 데이터 정합성은 깨지지 않으므로 다음 phase로 넘긴다.
  **후속 이슈: [#12](https://github.com/minsu-zip/gold-wrestling-be/issues/12)** (2026-08-08, 재현 방법·
  수정 선택지 2안·완료 조건 기재). 이로써 human_verification 항목 2의 "결정" 자체는 완료됐다.

## Summary

total: 2
passed: 2
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps
