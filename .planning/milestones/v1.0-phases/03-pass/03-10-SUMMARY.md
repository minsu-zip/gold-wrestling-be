---
phase: 03-pass
plan: 10
subsystem: api
tags: [kotlin, spring-boot, jpa, specification, pagination, pass, member-scope]

# Dependency graph
requires:
  - phase: 03-pass (03-02)
    provides: "PassTransaction 엔티티·리포지토리(JpaSpecificationExecutor)"
  - phase: 03-pass (03-06)
    provides: "PassResponse.from, AuthenticatedPrincipal.requireMemberId 패턴"
  - phase: 03-pass (03-09)
    provides: "취소 상태 전환 — 회원 조회에서 취소 제외 대비"
provides:
  - "GET /api/members/me/passes — 본인 이용권 조회 (만료·소진 포함 displayStatus 구분, 취소 제외, D-058)"
  - "GET /api/members/me/pass-transactions — 본인 차감/복구 이력 조회 (passId 필터 + page/size 페이지네이션)"
  - "PassTransactionSpecifications.ownedByMember — 본인 스코프를 쿼리 조건 수준에서 강제 (IDOR 방어)"
affects: [03-11 (phase 마감), phase-04 (예약 — 회원 이용권 조회 재사용)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "본인 스코프는 경로 파라미터 없이 principal.requireMemberId()로만 — 타인 passId를 필터로 넘겨도 ownedByMember AND 조건으로 결과가 빈 페이지 (IDOR 방어 2중)"
    - "ownedByMember는 non-null 반환으로 강제 — 호출부가 조건을 빼먹을 수 없는 타입 설계"
    - "JsonNode(tools.jackson) 멤버 map(Function)이 Iterable.map과 충돌 — .toList() 선삽입으로 타입추론 해소"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/pass/PassTransactionSpecifications.kt
    - src/main/kotlin/com/goldwrestling/pass/dto/PassTransactionResponse.kt
    - src/main/kotlin/com/goldwrestling/pass/dto/PassTransactionSearchCondition.kt
    - src/main/kotlin/com/goldwrestling/pass/MemberPassService.kt
    - src/main/kotlin/com/goldwrestling/pass/MemberPassController.kt
    - src/test/kotlin/com/goldwrestling/pass/MemberPassControllerTest.kt
    - src/test/kotlin/com/goldwrestling/pass/MemberPassTransactionControllerTest.kt
  modified:
    - docs/api/openapi.yaml
    - docs/decisions.md

key-decisions:
  - "D-070: 관리자 가감 메모(note)는 회원 이력 응답에서 제외 — reason 코드·수량·시각만 노출 (계획 단계 사용자 확정, D-043 연장)"
  - "회원 본인 이용권 조회는 취소된 이용권을 쿼리 조건에서 제외 (D-058·D-059) — 관리자 목록(03-09)과 반대"
  - "이력 조회는 Specification 조합(ownedByMember AND hasPassId)으로 구현 — MemberSpecifications 관례 재사용"

patterns-established:
  - "회원 self-scope 컨트롤러: /api/members/me/* + requireMemberId — MemberProfileController 관례를 pass 도메인으로 확장"

requirements-completed: [PASS-05, PASS-06]

# Metrics
duration: 약 60분 (API 오류·스톨 중단 2회 포함)
completed: 2026-08-03
---

# Phase 03 Plan 10: 회원 본인 이용권·이력 조회 Summary

**회원이 본인 이용권(만료·소진 포함, 취소 제외)과 차감/복구 이력(필터+페이지네이션)을 조회하는 API 2종 — 본인 스코프를 쿼리 조건 수준에서 강제**

## Task Commits

| Task | Commit | 내용 |
|------|--------|------|
| 1 | 7ef6198 | 이력 조회용 Specification + 응답·조건 DTO (note 제외, D-070 기록) |
| 2 | 45ffd89 | MemberPassController 엔드포인트 2종 + openapi.yaml 재생성 |
| 3 | 63db667 | 통합테스트 2종 (노출 범위·인가·페이지네이션·IDOR 방어) |

## Verification

- `./gradlew test --tests "com.goldwrestling.pass.*"` 그린 (MemberPassControllerTest 및 MemberPassTransactionControllerTest 포함)
- 타인 passId를 필터로 전달 시 `totalElements=0` — ownedByMember AND 조건이 쿼리 수준에서 차단함을 통합테스트로 실증
- PassTransactionResponse에 note 필드 없음 (D-070)
- openapi.yaml에 `/api/members/me/passes`·`/api/members/me/pass-transactions` 경로 반영

## Deviations

- 실행 중 API 연결 오류·스트림 스톨로 2회 중단 — 작업 유실 없이 기존 워크트리에서 이어서 완료. 코드·계획 편차 없음.

## Self-Check: PASSED

- [x] 3개 태스크 커밋 존재 (7ef6198, 45ffd89, 63db667)
- [x] pass 패키지 테스트 전부 그린
- [x] STATE.md/ROADMAP.md 무변경 (오케스트레이터 소관)
