---
phase: 03-pass
plan: 09
subsystem: api
tags: [kotlin, spring-boot, jpa, postgresql, testcontainers, pass, cancellation]

# Dependency graph
requires:
  - phase: 03-pass (03-05)
    provides: "Pass.cancel(reason, admin, now) — 상태 전환 + 상쇄 수량 산출"
  - phase: 03-pass (03-02)
    provides: "PassRepository.zeroRemainingCount — 조건부 UPDATE"
  - phase: 03-pass (03-06~03-08)
    provides: "AdminPassService/AdminPassController 골격, PassResponse.from"
provides:
  - "POST /api/admin/passes/{passId}/cancellation — 등록 취소 (물리 삭제 없이 상태 전환 + 상쇄 이력)"
  - "GET /api/admin/members/{memberId}/passes — 관리자 이용권 목록 (취소 포함, displayStatus로 구분)"
  - "PassLedgerInvariantTest에 취소 순환 2건 추가 — 등록→가감→가감→취소, 잔여0 취소 후에도 잔여=이력합계"
affects: [03-10 (회원 본인 이용권/이력 조회 — 취소 제외 대비), phase-04 (예약)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "취소: pass.cancel(엔티티, 상태·메타데이터만) → zeroRemainingCount(조건부 UPDATE, 잔여 반영) → 재조회 → PassTransaction 저장(같은 트랜잭션)"
    - "벌크 UPDATE(clearAutomatically) 이후 준영속 엔티티를 다시 만지지 않고 findById 재조회 후 사용"
    - "조회 전용 서비스 메서드는 클래스 기본 @Transactional(readOnly = true)를 그대로 쓰고 별도 애노테이션을 붙이지 않는다"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/pass/dto/CancelPassRequest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt
    - src/main/kotlin/com/goldwrestling/pass/AdminPassController.kt
    - src/test/kotlin/com/goldwrestling/pass/AdminPassControllerTest.kt
    - src/test/kotlin/com/goldwrestling/pass/PassLedgerInvariantTest.kt
    - docs/api/openapi.yaml

key-decisions:
  - "취소 상쇄 수량이 0(기간제 또는 잔여 0 횟수권)이면 zeroRemainingCount 호출도 PassTransaction 저장도 하지 않는다 (D-065, Pass.cancel의 반환값으로 분기)"
  - "관리자 목록 조회(getMemberPasses)는 취소된 이용권을 제외하지 않는다 — ROADMAP 성공기준 6 '관리자 화면 구분 표시'가 요구사항. 회원 본인 조회(03-10)는 반대로 제외한다"
  - "일회성 상태 전이(cancellation)는 단수형 POST, 목록 조회는 GET — 기존 /approval·/rejection·/passes 관례를 그대로 따름"

patterns-established: []

requirements-completed: [PASS-08, PASS-02]

# Metrics
duration: 35min
completed: 2026-08-03
---

# Phase 03 Plan 09: 등록 취소 + 관리자 이용권 목록 조회 Summary

**이용권 등록 취소(물리 삭제 없이 상태 전환 + REGISTRATION_CANCELED 상쇄 이력)와 관리자 이용권 목록 조회 API, 취소 순환까지 포함한 원장 불변식 테스트**

## Performance

- **Duration:** 약 35분
- **Tasks:** 3/3 완료
- **Files modified:** 6 (신규 1, 수정 5)

## Accomplishments

- `POST /api/admin/passes/{passId}/cancellation` — 횟수권/기간제 모두 물리 삭제 없이 `CANCELED` 상태로 전환. 횟수권은 `zeroRemainingCount` 조건부 UPDATE로 잔여를 0으로 상쇄하고 `REGISTRATION_CANCELED` 이력을 같은 트랜잭션에 남긴다. 잔여가 이미 0이거나 기간제면 이력을 남기지 않는다(D-065)
- `GET /api/admin/members/{memberId}/passes` — 회원의 전체 이용권을 취소 포함 최신 등록순으로 반환, `displayStatus`로 관리자가 구분해 볼 수 있다
- 취소된 이용권은 가감·기간수정·재취소 세 경로 모두에서 `PASS_ALREADY_CANCELED` 409로 거부됨을 통합테스트로 고정
- `PassLedgerInvariantTest`에 취소를 포함한 전체 순환(등록→가감→가감→취소) 및 "잔여 0인 이용권 취소" 케이스를 추가해 "잔여 = 이력 합계" 불변식이 취소 이후에도 유지됨을 실증

## Task Commits

1. **Task 1: CancelPassRequest DTO + AdminPassService.cancel/getMemberPasses** - `d553221` (feat)
2. **Task 2: 취소·관리자 목록 엔드포인트 + openapi 재생성** - `d9416b8` (feat)
3. **Task 3: 취소 통합테스트 + 원장 불변식 취소 순환 추가** - `f5f2d39` (test)

_Plan metadata commit: TBD (this SUMMARY + STATE/ROADMAP update, applied by orchestrator)_

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/pass/dto/CancelPassRequest.kt` - 취소 사유 요청 DTO (`reason`, `@NotBlank`, 500자 이내)
- `src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt` - `cancel`(상태 전환 + 조건부 상쇄 + 이력), `getMemberPasses`(취소 포함 목록 조회, 조회 전용)
- `src/main/kotlin/com/goldwrestling/pass/AdminPassController.kt` - `POST /passes/{passId}/cancellation`, `GET /members/{memberId}/passes`
- `src/test/kotlin/com/goldwrestling/pass/AdminPassControllerTest.kt` - 취소 10건 + 관리자 목록 3건 추가 (누적 44건)
- `src/test/kotlin/com/goldwrestling/pass/PassLedgerInvariantTest.kt` - 취소 순환 2건 추가, "03-09가 추가한다" 플레이스홀더 주석 제거 (누적 7건)
- `docs/api/openapi.yaml` - `generateApiDocs`로 재생성, 취소·관리자 목록 경로 및 `CancelPassRequest` 스키마 반영. `servers: /` 유지 확인

## Decisions Made

- 취소 처리 순서를 계획대로 정확히 지켰다: `pass.cancel`(엔티티, 상태·메타데이터만 대입) → 상쇄 수량이 0이 아닐 때만 `zeroRemainingCount`(조건부 UPDATE) → 반환 0이면 `PassStateConflictException` → `clearAutomatically`로 준영속이 된 엔티티 대신 `findById` 재조회 후 `PassTransaction` 저장. 이 순서를 지키지 않으면 `ck_pass_cancellation` CHECK 위반 또는 LAZY 접근 예외가 난다.
- `getMemberPasses`는 취소된 이용권을 결과에서 제외하지 않는다 — ROADMAP 성공기준 6("관리자 화면 구분 표시")과 D-059가 요구사항의 근거다. 회원 본인 조회(03-10 예정)는 이와 반대로 취소된 이용권을 제외한다는 점을 서비스 KDoc에 명시해 향후 플랜이 이 비대칭을 실수로 통일하지 않게 했다.
- 관리자 목록 API는 응답에 `Long` 형 `List<PassResponse>`를 그대로 반환한다 — 페이지네이션 없이 회원 1인의 전체 이용권 목록이라 `PageResponse` 래퍼가 불필요하다고 판단했다(회원 목록 검색 API와 달리 건수가 소수).

## Deviations from Plan

None - plan executed exactly as written.

계획에 없던 사소한 수정 1건: 관리자 목록 테스트에서 JSON `displayStatus` 문자열을 읽을 때 `JsonNode.asText()`가 Jackson 3(tools.jackson)에서 deprecated 경고를 내 `asString()`으로 교체했다(Rule 1 - 컴파일 경고 수정, 동작 변화 없음, Task 3 커밋에 포함).

## Issues Encountered

None. 사전 준비(Pass.cancel, zeroRemainingCount, PassAlreadyCanceledException, PassStateConflictException)가 03-02·03-05에서 이미 완비되어 있어 이번 플랜은 서비스·컨트롤러·테스트 배선에 집중했다.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 03-10(회원 본인 이용권/이력 조회)이 `findAllByMemberIdAndStatusNotOrderByStartDateDescIdDesc`(취소 제외)를 바로 쓸 수 있는 상태다. `getMemberPasses`의 KDoc에 "회원 조회는 반대로 제외"를 명시해 대비해 두었다.
- Phase 3(이용권)의 등록→가감→기간수정→취소 전 순환이 원장 불변식 테스트로 닫혔다. Phase 4(예약)가 잔여 차감 경로를 설계할 때 `adjustRemainingCount`/`zeroRemainingCount`와 동일한 조건부 UPDATE 패턴을 재사용할 수 있다.

---
*Phase: 03-pass*
*Completed: 2026-08-03*
