---
phase: 05-batch
verified: 2026-08-15T00:00:00Z
status: gaps_found
score: 1/4 must-haves verified
overrides_applied: 0
gaps:
  - truth: "BATCH-04: 같은 날 배치를 두 번 이상 실행해도 이중 차감이 발생하지 않는다(멱등)"
    status: failed
    reason: >
      순차 멱등(같은 트랜잭션 스레드가 순서대로 재실행)은 실증됐지만, 동시 실행(관리자 더블클릭,
      관리자 2명 동시 실행, 04:00 cron과 수동 실행이 겹치는 경우)에서는 이중 차감이 발생한다.
      `run()`은 ①원장을 읽어 부족분(shortfall)을 계산하고 ②그 수만큼 `deductOnce`를 반복 호출하는데
      ①·② 사이에 락이나 조건부 갱신이 없다. `adjustRemainingCount`의 조건부 UPDATE는
      `remainingCount + :amount >= 0`(음수 방지)만 검사할 뿐 "이 주기 차감이 이미 존재하는가"는
      보지 않는다. 같은 phase의 `InactivityDeductionConcurrencyTest`가 "잔여 2회를 세 스레드가 동시
      차감하면 정확히 두 번 성공한다"를 스스로 단언하고 있어, 두 개의 `run()`이 겹치면 총 차감이
      배가된다는 것이 이미 코드베이스에 실증돼 있다. `docs/decisions.md` D-108/D-114와
      `docs/api/openapi.yaml`의 "중복 실행해도 안전하다" 서술은 이 사실과 배치된다.
    artifacts:
      - path: "src/main/kotlin/com/goldwrestling/pass/PassRepository.kt"
        issue: "adjustRemainingCount 조건부 UPDATE가 음수만 막고 주기 중복 차감을 막지 못함(47행)"
      - path: "src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt"
        issue: "run() 전체를 직렬화하는 락·거부 장치가 없음(63-128행)"
      - path: "src/main/kotlin/com/goldwrestling/batch/AdminBatchController.kt"
        issue: "수동 실행 API가 동시 호출을 막지 않음(36-41행)"
    missing:
      - "run() 전체를 직렬화하는 장치(Postgres advisory lock 또는 회원+차감주기 부분 유니크 인덱스)"
      - "동시 run() 2개에서 총 차감이 1회임을 단언하는 동시성 테스트"
      - "openapi.yaml·D-108·D-114의 '중복 실행 안전' 서술 정정"
  - truth: "BATCH-01: SESSION_PASS가 기준일 기준 2주 미사용이면 1회 자동 차감되고, 이후 2주마다 반복 차감되며 이력이 INACTIVITY 사유로 남는다"
    status: failed
    reason: >
      2주 주기 반복 계산 자체(InactivityDueDateCalculator)는 순수 함수 단위테스트로 정확히 검증됐다.
      그러나 이 계산이 실제로 산출하는 결과가 정책과 어긋나는 두 경로가 남아 있다.
      (1) expectedDeductionCount에 상한이 없고 기준일 후보에 정책 시행일 하한이 없어, 배포 후 첫
      실행이 "배치가 존재하지 않았던 과거 전체 기간"까지 밀린 주기로 계산한다 — 오래 방치된
      SESSION_PASS를 한 번의 실행으로 0까지 소진시킬 수 있다. 이를 막을 kill switch(스케줄러
      on/off)도 없다.
      (2) lastAttendanceDate가 Phase 6 전까지 항상 null인 상태에서 cron이 이미 켜져 있어, 저녁반에만
      참여하는 SESSION_PASS 회원(EVENING_HALF로 관리자가 수동 차감)이 활동의 유일한 증거를 기준일
      후보로 인정받지 못해 2주마다 부당하게 추가 INACTIVITY 차감을 받는다(저녁반 0.5 + 미사용 1.0 =
      2주에 1.5회 차감). policies §6이 "마지막 출석일은 이 출석 기록을 기준으로 한다"고 명시하고
      CLAUDE.md 문서 우선순위상 policies.md가 코드를 이기므로, 이 상태로 cron이 도는 것은 정책 위반이다.
    artifacts:
      - path: "src/main/kotlin/com/goldwrestling/batch/InactivityDueDateCalculator.kt"
        issue: "expectedDeductionCount(53-60행)에 상한 없음, resolveDueDate에 정책 시행일 하한 없음"
      - path: "src/main/kotlin/com/goldwrestling/config/SchedulingConfig.kt"
        issue: "@EnableScheduling이 무조건 활성, on/off 프로퍼티 없음"
      - path: "src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt"
        issue: "lastAttendanceDate = null 하드코딩(95행) 상태로 스케줄러가 이미 활성화됨"
    missing:
      - "정책 시행일(policyEffectiveDate) 하한 + 회원별 1회 실행 최대 차감 수 상한"
      - "스케줄러 kill switch (@ConditionalOnProperty)"
      - "Phase 6 전까지 EVENING_HALF 이력을 임시 기준일 후보로 인정하거나, kill switch로 cron을 꺼 둔다는 명시적 결정"
  - truth: "BATCH-02: ON_LEAVE 기간, 잔여 0, 유효기간 만료된 이용권은 자동 차감 대상에서 제외된다"
    status: failed
    reason: >
      쿼리 필터(member.status <> ON_LEAVE, remainingCount > 0, endDate >= today)는 "현재 상태" 기준
      제외를 정확히 구현하고 통합테스트로 검증됐다. 그러나 policies §4.3의 취지("휴회 기간에는 부채가
      쌓이지 않는다")는 휴회 종료 후 그 기간이 소급 차감되지 않는 것까지 포함하며, 이는
      returnedFromLeaveAt(기준일 후보 ③)로 구현된다. AdminMemberService.changeStatus는
      `previousStatus == ON_LEAVE && newStatus == ACTIVE`일 때만 이 값을 기록해, 흔한 운영 경로인
      `ON_LEAVE→INACTIVE→ACTIVE`(장기 휴회자 비활성 처리 후 복귀)·`ON_LEAVE→PENDING→ACTIVE`(재신청)를
      거치면 기록되지 않는다. 이 경우 기준일이 휴회 전 등록일로 되돌아가 휴회 기간 전체가 소급
      차감 대상이 된다(6개월 휴회 복귀 시 최대 12회 몰아 차감).
    artifacts:
      - path: "src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt"
        issue: "changeStatus의 returnedFromLeaveAt 기록 조건이 ON_LEAVE→ACTIVE 직행만 커버(137-141행)"
    missing:
      - "'ON_LEAVE에서 벗어났을 때'(previousStatus==ON_LEAVE && newStatus!=ON_LEAVE) 기준으로 기록 조건 확장"
      - "ON_LEAVE→INACTIVE→ACTIVE 우회 경로에서 휴회 기간이 소급 차감되지 않음을 확인하는 테스트"
human_verification:
  - test: "WR-06: 탈퇴/장기미이용(INACTIVE) 회원의 SESSION_PASS도 2주 미사용 차감 대상에 계속 포함되는 현재 동작이 의도인지 확인"
    expected: "정책 문서(policies §4.3)에 INACTIVE가 예외로 명시돼 있지 않아 현재 구현('포함')이 문면상 틀리지는 않으나, 탈퇴 회원 잔여가 계속 깎여 0이 되는 결과가 환불 분쟁으로 이어질 수 있다 — 의도된 것인지 결정 필요"
    why_human: "정책 문서가 이 케이스를 의식하고 쓴 문장인지 확인된 바 없다(CLAUDE.md 규칙 8 — 두 가지로 해석되고 결과가 달라지면 추측하지 말고 확인받는다)"
---

# Phase 5: 배치 검증 보고서

**Phase 목표:** 이용권을 오래 쓰지 않은 회원이 정책대로 자동 차감되고, 유효기간이 지난 이용권은 사용 불가 처리되며, 배치가 며칠씩 중복 실행돼도 이중 차감이 없다.
**검증 시각:** 2026-08-15
**상태:** gaps_found
**재검증 여부:** 아니오 — 최초 검증

## 핵심 판정

Phase 5의 목표는 "2주 미사용 자동 차감이 **정확·멱등**하게 동작한다"이다. 이번 검증은 이 목표가
달성되지 않았다고 판정한다. 근거는 SUMMARY.md의 주장이 아니라 코드 자체와, 같은 phase가 스스로
작성한 동시성 테스트(`InactivityDeductionConcurrencyTest`)가 문서화하고 있는 사실이다.

- **BATCH-04(멱등)**: 순차 멱등은 실증됐다(로컬 실기동에서 순차 재호출 시 `deductedCount 0` 확인,
  `InactivityBatchIdempotencyTest` 7개 케이스 통과). 그러나 **동시 멱등은 성립하지 않는다.**
  `InactivityDeductionConcurrencyTest`의 테스트 이름 자체가 "잔여 2회를 세 스레드가 동시에 차감하면
  **정확히 두 번 성공**한다"이다 — 이는 `run()` 레벨에서 관리자가 실행 버튼을 두 번 누르거나
  cron과 수동 실행이 겹치면 실제로 이중 차감이 난다는 것을 코드베이스 스스로 증명한 것이다.
  요구사항 문구("같은 날 배치를 두 번 이상 실행해도 이중 차감이 발생하지 않는다")에는 "순차로만"이라는
  단서가 없다. `docs/decisions.md` D-108·D-114는 "상태 기반 설계가 중복 실행을 이미 안전하게 만든다"고
  명시적으로 판단했는데, 이 판단이 사실과 다르다는 것이 같은 PR의 테스트로 반증됐다. `passed`로
  판정할 근거가 없다.
- **BATCH-01(정확성)**: 2주 주기 반복 계산 자체는 순수 함수 단위테스트로 견고하게 고정돼 있다.
  그러나 (a) 상한·정책 시행일 하한이 없어 배포 첫 실행이 과거 전체 기간을 소급 차감할 수 있고
  kill switch도 없으며, (b) 출석일 후보가 Phase 6 전까지 null인 채로 cron이 이미 켜져 있어 저녁반
  전용 회원이 부당하게 이중 차감된다(policies §6과 상충, CLAUDE.md 문서 우선순위 위반). Core Value
  ("회원이 보는 잔여는 항상 실제 사용 가능 횟수와 일치한다")를 직접 위협하는 결함이라 `passed` 불가.
- **BATCH-02(예외 준수)**: 현재 상태 기준 필터(쿼리)는 정확하다. 하지만 휴회 예외의 완전성은
  "복귀 시각 기록"에도 의존하는데, `ON_LEAVE→INACTIVE→ACTIVE`처럼 흔한 관리자 운영 경로에서
  복귀 시각이 기록되지 않아 휴회 기간이 소급 차감된다. 정책의 명시적 예외("휴회 기간에는 부채가
  쌓이지 않는다")를 정면으로 위반하는 경로가 존재하므로 `passed` 불가.
- **BATCH-03(만료 처리)**: 신규 구현물 없이 D-064·Phase 4 예약 거부 경로로 충족한다는 설계(D-107)를
  `InactivityBatchExpiryVerificationTest` 6개 케이스(종료일 경계 2·예약 거부 2·배치 제외·기간 수정
  후 복귀)가 실제로 실증하고 있다. 코드리뷰에서도 이 부분은 Critical/Warning이 없다. **VERIFIED.**

## 목표 달성 여부 (Observable Truths)

| # | Truth (BATCH-ID) | 상태 | 근거 |
|---|---|---|---|
| 1 | BATCH-01: 2주 미사용 시 1회, 이후 2주마다 반복 차감(INACTIVITY 이력) | ✗ FAILED | 주기 계산 로직은 정확하나 상한·정책 시행일 하한 부재(CR-02) + 출석일 후보 미구현 상태에서 cron 활성화(CR-03)로 실제 산출 결과가 정책과 어긋난다 |
| 2 | BATCH-02: ON_LEAVE 기간·잔여 0·만료 이용권 차감 제외 | ✗ FAILED | 현재 상태 필터는 정확하나, `ON_LEAVE→INACTIVE→ACTIVE` 등 우회 경로에서 복귀 시각 미기록으로 휴회 기간이 소급 차감됨(CR-04) |
| 3 | BATCH-03: 만료(1년 경과) 이용권 예약 불가 + 차감 제외 | ✓ VERIFIED | `InactivityBatchExpiryVerificationTest` 6종이 D-064 조회시점 계산·Phase 4 거부 경로·배치 제외·기간 수정 후 복귀를 실증. Critical/Warning 없음 |
| 4 | BATCH-04: 같은 날 중복 실행돼도 이중 차감 0건(멱등) | ✗ FAILED | 순차 멱등만 성립. `InactivityDeductionConcurrencyTest`가 동시 실행 시 이중 차감을 스스로 증명(CR-01). openapi.yaml의 "중복 실행 안전" 서술이 사실과 다름 |

**점수:** 1/4 truths verified

## 필수 산출물 (Artifacts)

| Artifact | 기대 역할 | 상태 | 세부 |
|---|---|---|---|
| `src/main/kotlin/com/goldwrestling/batch/InactivityDueDateCalculator.kt` | 기준일 max·부족분 순수 계산 | ⚠️ 부분 결함 | 계산 자체는 테스트로 고정됐으나 상한·하한 부재(85행 전체) |
| `src/main/kotlin/com/goldwrestling/batch/InactivityDeductionService.kt` | 차감 1회 반영(만료 임박 한 장·부분 차감·시스템 주체 이력) | ✓ VERIFIED | `deductOnce` 존재, 통합테스트 통과. 단 상위 호출부(Runner)에서 동시성 방어 없음이 문제 |
| `src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt` | 배치 오케스트레이션 | ⚠️ 결함 | `run()` 존재·동작하나 직렬화 장치 없음(CR-01), lastAttendanceDate 하드코딩(CR-03) |
| `src/main/kotlin/com/goldwrestling/batch/InactivityBatchScheduler.kt` | cron 트리거 | ⚠️ 결함 | `@Scheduled(cron = "0 0 4 * * *")` 존재·동작하나 kill switch 없음(CR-02) |
| `src/main/kotlin/com/goldwrestling/batch/AdminBatchController.kt` | 관리자 수동 실행 API | ⚠️ 결함 | `/batch/inactivity-runs` 존재, 인가 확인(401/403 로컬 실기동 확인). 중복 요청 거부 없음(CR-01), 동기 호출로 타임아웃→재시도 유발(WR-05) |
| `src/main/kotlin/com/goldwrestling/pass/PassRepository.kt` (adjustRemainingCount) | 조건부 UPDATE로 원자적 차감 | ⚠️ 결함 | 음수 방지 조건만 있고 주기 중복 방지 조건 없음(CR-01 근본 원인) |
| `src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt` | 휴회 복귀 시각 기록 | ⚠️ 결함 | `ON_LEAVE→ACTIVE` 직행만 기록(CR-04) |
| `src/test/kotlin/com/goldwrestling/batch/InactivityBatchExpiryVerificationTest.kt` | BATCH-03 실증 | ✓ VERIFIED | 6개 테스트, 4축 모두 커버 |
| `docs/api/openapi.yaml` | FE 계약 갱신 | ⚠️ 결함 | `/api/admin/batch/inactivity-runs` 등록됨. 단 설명이 "중복 실행해도 안전하다"로 CR-01과 모순 |

## 핵심 연결(Key Link) 검증

| From | To | Via | 상태 | 세부 |
|---|---|---|---|---|
| `InactivityBatchScheduler` | `InactivityBatchRunner.run(SCHEDULED, null)` | cron이 러너 호출 | ✓ WIRED | 로컬 실기동으로 확인(앱 기동 후 스케줄 등록) |
| `AdminBatchController` | `InactivityBatchRunner.run(MANUAL, adminId)` | 수동 실행 API | ✓ WIRED | 로컬 실기동에서 `deductedCount 1` 확인, `batch_execution`에 `triggered_by_admin_id` 기록 |
| `InactivityBatchRunner` | `InactivityDeductionService.deductOnce` | 부족분만큼 반복 호출 | ✓ WIRED, ⚠️ 동시성 미보호 | 순차 호출은 정확, 두 `run()`이 겹치면 각각 반복 호출해 총합이 배가됨 |
| `InactivityDeductionService.deductOnce` | `PassRepository.adjustRemainingCount` | 조건부 UPDATE | ✓ WIRED, ⚠️ 조건 불충분 | 음수 방지만, 주기 중복 방지 없음 |
| `AdminMemberService.changeStatus` | `Member.returnedFromLeaveAt` | ON_LEAVE→ACTIVE 전이 시각 대입 | ✓ WIRED, ⚠️ 조건 범위 좁음 | 직행 전이만 커버, 우회 전이 누락 |

## 데이터 흐름 추적 (Level 4)

배치는 회원의 잔여 횟수를 직접 변경하는 도메인 로직이라 "화면 렌더링"이 아니라 "DB 상태 변경"이
최종 산출물이다. 로컬 실기동 검증에서 실제 DB 상태 변화(잔여 3.0→2.0, `pass_transaction` 1건 삽입,
재호출 시 불변)를 사용자가 직접 관찰했으므로 정상 경로(순차 단일 실행)의 데이터 흐름은 FLOWING으로
판정한다. 다만 위 truths에서 밝힌 대로, 이 흐름이 산출하는 **값 자체**가 특정 조건(동시 실행·소급
기간·저녁반 전용 회원·휴회 우회 경로)에서 정책과 다른 값을 만든다.

## 행동 스팟체크 / 프로브

- 별도 `scripts/*/tests/probe-*.sh`는 이 프로젝트에 없음 — SKIPPED.
- 대신 오케스트레이터가 이미 수행한 **로컬 실기동 검증(05-09 Task 3, 사용자 승인)** 결과를 근거
  로그로 채택했다: 앱 기동 → 수동 실행 API 호출 → 잔여·이력·재호출 불변·인가·ON_LEAVE 제외를
  실제 관찰. 이 결과는 위 truths 판정에 이미 반영했다(정상 경로는 동작한다는 근거로, 그러나
  이 검증은 동시 실행·소급 시나리오를 다루지 않았으므로 CR-01·CR-02를 반증하지 못한다).

## 요구사항 커버리지

| 요구사항 | 근거 플랜 | 설명 | 상태 | 근거 |
|---|---|---|---|---|
| BATCH-01 | 05-01,03,04,05,06,08,09 | 2주 미사용 1회+반복 차감, 기준일 5종 후보 max, 회원 단위 | ✗ BLOCKED | 계산 로직은 맞지만 상한·시행일 하한 부재 + 출석일 후보 미구현 상태에서 cron 활성화로 산출값이 정책과 어긋남 |
| BATCH-02 | 05-04,05,06,07 | ON_LEAVE·잔여0·만료 제외 | ✗ BLOCKED | 즉시 상태 필터는 정확, 휴회 우회 전이에서 소급 차감 위험 |
| BATCH-03 | 05-01,07 | 만료 이용권 사용 불가(구현물 없음, D-107) | ✓ SATISFIED | `InactivityBatchExpiryVerificationTest` 6종 |
| BATCH-04 | 05-02,03,06,07,08 | 배치 멱등 | ✗ BLOCKED | 순차만 멱등, 동시 실행은 이중 차감 |

REQUIREMENTS.md에는 BATCH-01~04 외 이 phase에 추가로 매핑된 ID가 없다 — orphaned requirement 없음.
단, REQUIREMENTS.md·ROADMAP.md 현재 `[x] Complete` 표기는 이번 검증 결과와 불일치한다 —
`/gsd:plan-phase --gaps`로 갭 클로저 플랜이 반영된 뒤에만 Complete로 되돌릴 것을 권고한다.

## 안티패턴 스캔

| 파일 | 위치 | 패턴 | 심각도 | 영향 |
|---|---|---|---|---|
| `docs/decisions.md` (D-108, D-114) | 전체 | 문서화된 설계 결정이 같은 PR의 테스트가 반증하는 사실과 모순 | 🛑 Blocker | "중복 실행이 안전하다"는 결정이 틀렸다는 것을 인지하지 못한 채 커밋됨 — 다음 작업자가 이 결정을 근거로 안전을 가정한다 |
| `docs/api/openapi.yaml:480` | inactivity-runs description | FE 계약에 사실과 다른 안전성 서술 | ⚠️ Warning | FE가 "재시도해도 안전"으로 오해해 재시도 로직을 구현할 수 있다 |
| `src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt:95` | lastAttendanceDate | 하드코딩 null (주석으로 Phase 6 위임 명시) | ⚠️ Warning | TBD/FIXME 마커는 아니지만 cron이 이미 켜진 상태에서 정책 미준수 상태로 운영됨(CR-03) |

`TBD`/`FIXME`/`XXX` 형식의 부채 마커는 이번 phase가 수정한 파일에서 발견되지 않았다 — debt-marker
게이트는 트리거되지 않는다. 위 3건은 코드 자체의 debt marker가 아니라 **문서·구현 불일치**와
**정책 미준수 상태의 실행**을 지적한 것이다.

## 사람 확인 필요

### 1. INACTIVE(탈퇴·거절) 회원의 지속적 차감이 의도인지

**확인할 것:** `PassRepository.findMemberIdsWithDeductibleSessionPass`가 `member.status <> ON_LEAVE`만
제외하고 `INACTIVE`는 대상에 남기는 현재 동작이 맞는지
**기대:** policies §4.3 예외 3종(휴회·잔여0·만료)에 `INACTIVE`가 없으므로 문면상 현재 구현이 틀리지는
않지만, 탈퇴 회원의 잔여가 배치로 계속 깎여 0이 되면 환불 분쟁 소지가 있다
**왜 사람인가:** CLAUDE.md 규칙 8 — 정책 문서가 이 케이스를 의식하고 쓴 문장인지 확인된 바 없고,
해석에 따라 결과(환불 대응)가 달라진다

## 갭 요약

Phase 5는 "정확·멱등"이라는 목표 문구의 두 축 모두에서 실패한다. BATCH-03만 근거가 충분하다.
근본 원인은 세 갈래로 나뉜다:

1. **동시성(BATCH-04, CR-01)** — `run()`을 직렬화하는 장치가 전혀 없어, "상태 기반 계산 = 자동으로
   멱등"이라는 설계 전제가 순차 실행에서만 성립한다. 같은 phase의 동시성 테스트가 이를 스스로
   증명하고 있어 재현 불확실성이 없다.
2. **소급 차감 무방비(BATCH-01, CR-02)** — 상한·정책 시행일 하한·kill switch가 모두 없어, 배포
   시점이 곧 첫 실행 시점이 되는 이 코드베이스에서 오래된 데이터를 가진 회원의 이용권이 배포
   직후 대량 소진될 수 있다.
3. **기준일 완전성(BATCH-01/02, CR-03·CR-04)** — 출석일 후보 부재 상태에서 cron이 이미 켜져
   저녁반 전용 회원이 부당 차감되고(CR-03), 휴회 우회 전이에서 복귀 시각이 기록되지 않아 휴회
   기간이 소급 차감된다(CR-04).

세 갈래 모두 Core Value("회원이 보는 잔여 횟수는 항상 실제 사용 가능 횟수와 일치한다")를 직접
위협하며, 코드리뷰(05-REVIEW.md)의 Critical 4건과 정확히 일치한다. `/gsd:plan-phase --gaps`로
갭 클로저 플랜을 만들어 CR-01~04를 닫기 전에는 프로덕션 배포(cron 활성화 상태로 dev→main merge)를
권장하지 않는다.

---

_검증: 2026-08-15_
_검증자: Claude (gsd-verifier)_
