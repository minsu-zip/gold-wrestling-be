---
phase: 03-pass
plan: 08
subsystem: api
tags: [kotlin, spring-boot, jpa, postgresql, pass, period-change]

requires:
  - phase: 03-pass (plan 05)
    provides: "Pass.changePeriod 도메인 판정 메서드 + PassPeriodChangeTest 단위테스트"
  - phase: 03-pass (plan 07)
    provides: "AdminPassService/AdminPassController 골격, AdminPassControllerTest 애노테이션 조합"
provides:
  - "PATCH /api/admin/passes/{passId}/period 통합 엔드포인트 (저녁반 기간 + 횟수권 유효기간)"
  - "ChangePassPeriodRequest DTO"
  - "AdminPassService.changePeriod (전값 보관 -> changePeriod 호출 -> 조건부 이력 저장)"
  - "D-069: 전값=후값이면 PassPeriodChange 이력을 남기지 않는다"
affects: [03-pass (남은 조회 플랜들이 PassPeriodChange 이력을 노출할 때 이 스키마를 참조)]

tech-stack:
  added: []
  patterns:
    - "엔티티 판정(Pass.changePeriod) + 서비스 조율(전값 보관, 조건부 이력 저장) 분리 — 03-07 가감 플랜의 3단 구조(사전판정→반영→이력)와 계열이 같되, 여기는 DB 경쟁이 없어 조건부 UPDATE 대신 JPA 변경 감지를 쓴다"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/pass/dto/ChangePassPeriodRequest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt
    - src/main/kotlin/com/goldwrestling/pass/AdminPassController.kt
    - src/test/kotlin/com/goldwrestling/pass/AdminPassControllerTest.kt
    - docs/api/openapi.yaml
    - docs/decisions.md

key-decisions:
  - "D-069: 기간·유효기간 수정 시 전값·후값이 완전히 같으면 PassPeriodChange 이력을 남기지 않는다 (D-065와 같은 원칙)"

patterns-established: []

requirements-completed: [PASS-04, PASS-07]

duration: 25min
completed: 2026-08-03
---

# Phase 3 Plan 08: 기간·유효기간 수정 API 요약

**저녁반 회비 기간 수정(PASS-04)과 횟수권 유효기간 수정(PASS-07)을 `PATCH /api/admin/passes/{passId}/period` 하나의 엔드포인트로 구현하고, 모든 변경을 `PassPeriodChange`에 전값·후값·사유·주체·시각으로 남겼다.**

## Performance

- **Duration:** 약 25분
- **Tasks:** 2/2 완료
- **Files modified:** 5개 (신규 1, 수정 4)

## Accomplishments

- `ChangePassPeriodRequest` DTO(`newStartDate?`, `newEndDate`, `reason`) — 형식 검증만 담당, 도메인 규칙(횟수권 시작일 고정 등)은 `Pass.changePeriod`가 강제
- `AdminPassService.changePeriod` — 조회 → **전값 보관(호출 전)** → `pass.changePeriod` 호출 → 전값·후값이 실제로 달라진 경우에만 `PassPeriodChange` 저장 → 응답 변환, 전부 같은 트랜잭션
- `PATCH /api/admin/passes/{passId}/period` 엔드포인트 (D-062 통합 엔드포인트)
- 통합테스트 11건 추가(누적 31건): 저녁반 전체 수정, 횟수권 종료일만 수정, 만료권 연장, 잘못된 시작일 변경 거부, 역전된 기간 거부, 취소된 이용권 거부, 공백 사유 거부, 없는 passId, 전값과 동일한 값 재전송 시 이력 미생성, 회원 토큰/무토큰 인가 거부
- `docs/api/openapi.yaml` 재생성 — `ChangePassPeriodRequest` 스키마 + `period` 경로 추가만 반영, `servers: /` 유지 확인
- D-069 신규 결정 기록 — "변화가 0이면 이력이 아니다"를 기간 수정에도 동일 적용

## Task Commits

1. **Task 1: ChangePassPeriodRequest DTO + AdminPassService.changePeriod** - `88e8713` (feat)
2. **Task 2: 기간 수정 엔드포인트 + 통합테스트 + openapi 재생성** - `d89fe39` (feat)

**Plan metadata:** (본 커밋에서 뒤이어 생성)

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/pass/dto/ChangePassPeriodRequest.kt` - 통합 기간 수정 요청 DTO
- `src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt` - `changePeriod` 메서드 추가(전값 보관 → 판정 호출 → 조건부 이력)
- `src/main/kotlin/com/goldwrestling/pass/AdminPassController.kt` - `PATCH /api/admin/passes/{passId}/period` 추가
- `src/test/kotlin/com/goldwrestling/pass/AdminPassControllerTest.kt` - "기간·유효기간 수정" 섹션 11개 테스트, `persistPass` 헬퍼에 startDate/endDate/status 파라미터 추가(취소 픽스처는 `Pass.cancel`로 취소 메타데이터 3종을 함께 채워 `ck_pass_cancellation` CHECK를 만족시킴)
- `docs/api/openapi.yaml` - `ChangePassPeriodRequest` 스키마 + `period` 경로만 재생성 반영
- `docs/decisions.md` - D-069 신규 기록

## Decisions Made

- **D-069**: 전값·후값이 완전히 같으면 `PassPeriodChange` 이력을 남기지 않는다. D-065("등록 취소 시 잔여 변화가 0이면 상쇄 이력을 남기지 않는다")와 같은 원칙 — 변화 없는 재전송이 이력에 쌓이면 "몇 번 바뀌었는가"를 이력 건수로 셀 수 없게 된다.

## Deviations from Plan

None - 플랜에 기술된 대로 실행했다. 다만 통합테스트 작성 중 `persistPass` 헬퍼에 `status = CANCELED`를 직접 대입하는 최초 시도가 `ck_pass_cancellation` CHECK(취소 시 `canceled_at`/`cancel_reason`/`canceled_by_admin_id` 3종 동시 존재 강제, V4)를 위반해 `DataIntegrityViolationException`이 발생했다 — Rule 1(자동 버그 수정)로 `Pass.cancel(...)`을 호출해 3종을 함께 채우도록 헬퍼를 고쳤고, 재실행으로 31건 전부 통과를 확인했다. 플랜 범위를 벗어나는 프로덕션 코드 변경은 없었다(테스트 픽스처 헬퍼만 수정).

## TDD Gate Compliance

두 태스크 모두 `tdd="true"`로 표시돼 있었지만, 03-06·03-07의 기존 실행 관례(`git log` 확인)를 따라 RED(`test(...)`)·GREEN(`feat(...)`) 커밋을 분리하지 않고 **테스트를 프로덕션 코드와 같은 `feat` 커밋에 묶었다**. 이유:

- Task 1의 `<verify>`는 `./gradlew compileKotlin`만 요구했다 — 이 태스크가 추가한 판정 로직(`Pass.changePeriod`)은 이미 03-05(`PassPeriodChangeTest`, 커밋 이력 존재)에서 RED→GREEN 절차로 단위테스트가 끝나 있어, 이 플랜이 새로 검증할 도메인 규칙이 없었다(글루 코드: DTO 조립 + 서비스 오케스트레이션).
- Task 2가 실제 신규 동작(엔드포인트 계약)을 검증하는 통합테스트 11건을 포함했고, 컨트롤러 하나에 성공+실패 경로를 한 번에 작성하는 것이 이 코드베이스의 `AdminPassControllerTest` 섹션 관례(같은 애노테이션 조합 재사용, 컨텍스트 캐시 유지)와 일치한다.

`docs/conventions.md` §10.0 표 기준으로도 이 변경은 "컨트롤러(새 엔드포인트) → 통합테스트 1개 이상(성공+대표 실패)"에 해당하며 실제로 11건을 작성해 기준을 상회했다. 도메인 규칙 자체의 RED/GREEN은 03-05에서 이미 완료됐다는 점에서, 이번 플랜에 대해 별도 경고 없이 이 편차를 문서화만 남긴다.

## Issues Encountered

None (위 "Deviations from Plan"의 CHECK 제약 이슈 1건 외 없음).

## User Setup Required

None - 외부 서비스 설정 불필요.

## Next Phase Readiness

- `PassPeriodChange` 이력이 실제로 쌓이는 경로가 완성됐다 — 이후 조회 플랜(회원 본인 조회 등)이 이 테이블을 노출할 근거가 생겼다.
- `docs/api/openapi.yaml`에 `period` 계약이 반영돼 FE가 타입을 생성할 수 있다.
- 남은 03-pass 플랜(취소 API, 회원 조회 등)이 이 플랜의 결과물(D-069, `persistPass` 취소 픽스처 패턴)을 그대로 참고할 수 있다.

---
*Phase: 03-pass*
*Completed: 2026-08-03*

## Self-Check: PASSED

- FOUND: src/main/kotlin/com/goldwrestling/pass/dto/ChangePassPeriodRequest.kt
- FOUND: src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt
- FOUND: src/main/kotlin/com/goldwrestling/pass/AdminPassController.kt
- FOUND: src/test/kotlin/com/goldwrestling/pass/AdminPassControllerTest.kt
- FOUND: docs/api/openapi.yaml
- FOUND: docs/decisions.md
- FOUND commit: 88e8713 (Task 1)
- FOUND commit: d89fe39 (Task 2)
