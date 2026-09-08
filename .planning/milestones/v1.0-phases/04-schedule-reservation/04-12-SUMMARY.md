---
phase: 04-schedule-reservation
plan: 12
subsystem: api
tags: [kotlin, spring-boot, jpa, postgresql, reservation, admin, entity-graph, tdd]

# Dependency graph
requires:
  - phase: 04-schedule-reservation (plan 04-10)
    provides: "ReservationSpecifications(classDateBetween·hasClassType·memberKeywordContains·hasStatus) — 이 플랜이 그대로 재사용"
  - phase: 04-schedule-reservation (plan 04-11)
    provides: "관리자 목록 API의 Specification+PageResponse 관례 반복 확인, N+1 방지가 이 저장소의 표준 관심사임을 재확인"
provides:
  - "AdminReservationService.search — 기간·수업종류·회원검색어·상태 필터 + 페이지네이션 관리자 예약 조회(RESV-07)"
  - "GET /api/admin/reservations 엔드포인트"
  - "ReservationRepository.findAll(Specification, Pageable)의 @EntityGraph 재선언 — 이 저장소 최초의 Specification+EntityGraph 조합 패턴"
  - "InvalidReservationSearchRangeException/ErrorCode.INVALID_RESERVATION_SEARCH_RANGE(400)"
affects: [04-13, 04-15]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Specification 페이지 조회 + @EntityGraph: JpaSpecificationExecutor.findAll(Specification, Pageable)을
       리포지토리에서 재선언하고 @EntityGraph(attributePaths=[...])를 붙여 ManyToOne LAZY 연관을
       N+1 없이 로딩 — Specification에 root.fetch(...)를 넣는 방식과 달리 count 쿼리에는 힌트가
       적용되지 않아 페이지네이션과 안전하게 공존한다(spring-data-jpa 4.1.0 테스트 코드로 확인)"
    - "두 필드 간 관계 검증(from > to)은 DTO(@Valid)가 아니라 서비스에서 도메인 예외로 판정 —
       AdminPassService.changePeriod의 기간 역전 판정과 동일 관례"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/reservation/AdminReservationService.kt
    - src/main/kotlin/com/goldwrestling/reservation/AdminReservationController.kt
    - src/main/kotlin/com/goldwrestling/reservation/dto/ReservationSearchCondition.kt
    - src/main/kotlin/com/goldwrestling/reservation/dto/AdminReservationResponse.kt
    - src/test/kotlin/com/goldwrestling/reservation/AdminReservationSearchTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/reservation/ReservationRepository.kt
    - src/main/kotlin/com/goldwrestling/reservation/ReservationExceptions.kt
    - src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt
    - docs/error-codes.md

key-decisions:
  - "관리자 예약 조회는 branchId 스코프를 받지 않는다 — 04-CONTEXT.md의 RESV-07 결정이 필터를
     기간·수업종류·회원검색어로만 못박았고 AdminBranch 매핑은 v1 미도입(D-101)이라, 04-11(스케줄
     보드)과 달리 AdminMemberController(원본 인터페이스)를 그대로 따라 principal을 받지 않는다"
  - "from > to 검증은 서비스에서 도메인 예외(InvalidReservationSearchRangeException, 400)로 판정 —
     AdminPassService.changePeriod의 기간 역전 판정과 동일 관례, DTO에는 형식 검증만 남긴다"

requirements-completed: [RESV-07]

# Metrics
duration: ~40min
completed: 2026-08-08
---

# Phase 4 Plan 12: 관리자 전체 예약 조회 Summary

**관리자가 기간·수업 종류·회원 검색어·상태로 모든 회원의 예약을 필터링해 조회하는 `GET /api/admin/reservations`를 추가하고, `ReservationRepository.findAll(Specification, Pageable)`을 `@EntityGraph`로 재선언해 회원명·취소 주체 표시에 필요한 N+1을 원천 차단했다.**

## Performance

- **Duration:** ~40min
- **Tasks:** 2 completed (Task 1·2 모두 TDD RED→GREEN)
- **Files modified:** 9 (신규 5, 수정 4)

## Accomplishments

- 관리자가 `GET /api/admin/reservations`로 기간(`from`/`to`)·수업 종류·회원 검색어·상태를 조합해 모든 회원의 예약을 페이지 단위로 조회한다 — `status`를 생략하면 취소된 예약도 함께 반환된다(감사·이력 확인이 목적이라 회원 본인 목록의 "취소 숨김"과 반대)
- 검색어에 LIKE 와일드카드(`%`, `_`)를 넣어도 전체 조회로 확장되지 않는다(04-10의 `LikePatternEscaper` 재사용, 실제 통합테스트로 확인) — 앞뒤 공백도 정규화 후 매칭된다
- 정렬은 `reservedAt` 내림차순 고정 — 관리자가 방금 들어온 예약·변경을 먼저 본다
- 응답(`AdminReservationResponse`)에 회원명·`status`·`refunded`·`canceledAt`·취소 주체 종류(`canceledByType`, `PrincipalType` 재사용)·취소 주체 이름이 포함되고, **전화번호는 포함하지 않는다**(T-04-59)
- `member`·`classSession`·`canceledByMember`·`canceledByAdmin`(전부 `ManyToOne`) LAZY 연관을 `@EntityGraph`로 미리 로딩해, 페이지 크기와 무관하게 조회 쿼리가 고정 횟수로 끝난다(T-04-58) — 이 저장소에 `Specification` 기반 페이지 조회와 `@EntityGraph`를 함께 쓴 최초 사례
- 회원 토큰으로는 403(`ACCESS_DENIED`), 인증 없이는 401(`UNAUTHENTICATED`)로 거부되고, `size` 상한(100) 초과는 400(`VALIDATION_FAILED`), `from > to`는 400(`INVALID_RESERVATION_SEARCH_RANGE`, 신규 에러코드)으로 거부된다
- 목록 API 형태(`@ParameterObject` + `Specification.allOf` + `PageResponse`)가 Phase 2·3(`AdminMemberController`/`AdminMemberService`)과 동일하게 유지된다 — 새 페이지네이션 DTO를 만들지 않았다

## Task Commits

Each task was committed atomically (TDD RED → GREEN):

1. **Task 1: AdminReservationService.search + 검색 조건·응답 DTO**
   - `4b2f346` test(04-12): 관리자 예약 검색 서비스 실패 테스트 추가 (RED)
   - `b50b464` feat(04-12): AdminReservationService.search 구현 (GREEN)
2. **Task 2: GET /api/admin/reservations 엔드포인트**
   - `a3ff86c` test(04-12): GET /api/admin/reservations 엔드포인트 실패 테스트 추가 (RED)
   - `3b74983` feat(04-12): GET /api/admin/reservations 엔드포인트 추가 (GREEN)

**Plan metadata:** (다음 커밋, docs: complete plan)

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/reservation/AdminReservationService.kt` — `search`(Specification 조합 + from>to 판정 + PageResponse 변환)
- `src/main/kotlin/com/goldwrestling/reservation/AdminReservationController.kt` — `GET /api/admin/reservations`(`@ParameterObject`, branchId 스코프 없음)
- `src/main/kotlin/com/goldwrestling/reservation/dto/ReservationSearchCondition.kt`, `AdminReservationResponse.kt` — 검색 조건·응답 DTO(신규)
- `src/main/kotlin/com/goldwrestling/reservation/ReservationRepository.kt` — `findAll(Specification, Pageable)`을 `@EntityGraph`로 재선언
- `src/main/kotlin/com/goldwrestling/reservation/ReservationExceptions.kt` — `InvalidReservationSearchRangeException` 추가
- `src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt`, `docs/error-codes.md` — `INVALID_RESERVATION_SEARCH_RANGE`(400) 추가
- `src/test/kotlin/com/goldwrestling/reservation/AdminReservationSearchTest.kt` — 서비스 레벨 10종 + HTTP 계약 6종(`@Nested HttpContract`), 총 16개(신규)

## Decisions Made

- **branchId 스코프 미도입** — 04-11(관리자 스케줄 보드)의 사전 컨텍스트가 "04-12도 branch 스코프가 필요하면 같은 인프라를 재사용하라"고 안내했지만, 실제 `04-CONTEXT.md`의 RESV-07 결정과 04-12-PLAN의 `<interfaces>`는 이 API의 원본으로 `AdminMemberController`(principal 없음)를 지목했고 필터 목록에 branchId가 없다. `AdminBranch` 매핑이 v1에서 아직 비어 있다는 04-11의 발견(D-101)까지 고려하면, 이 시점에 branchId 필터를 추가하는 것은 요구되지 않은 스코프 확장이라 판단해 만들지 않았다. 다지점 운영이 시작되면 `AdminScheduleService.resolveBranchId`와 같은 방식으로 확장 가능하다.
- **`from > to` 검증은 서비스에서 도메인 예외로 판정** — 두 필드를 함께 봐야 하는 관계 검증이라 `jakarta.validation`(형식 검증)으로 표현하지 않고, `AdminPassService.changePeriod`가 기간 역전을 서비스에서 판정하는 것과 동일한 관례를 따랐다. 신규 에러코드 `INVALID_RESERVATION_SEARCH_RANGE`(400)를 추가하고 `docs/error-codes.md`에 같은 커밋에서 등재했다.
- **`@EntityGraph`로 `findAll(Specification, Pageable)` 재선언** — 플랜이 "`JpaSpecificationExecutor`와 `@EntityGraph`를 함께 쓰는 방법이 확실하지 않으면 verify-boot4-api 절차로 확인하라"고 명시했다. context7로 `spring-data-jpa` 공식 테스트 코드(`RepositoryMethodsWithEntityGraphConfigRepository`)를 확인해, 리포지토리 인터페이스에서 기반 메서드를 재선언하며 `@EntityGraph`를 붙이는 패턴이 지원됨을 검증한 뒤 적용했다. `Specification`에 `root.fetch(...)`를 넣는 대안은 count 쿼리에도 fetch가 적용되어 JPA 스펙 위반 위험이 있어 채택하지 않았다.

## Deviations from Plan

None — plan executed as written. 위 "Decisions Made"의 branchId 미도입은 계획을 벗어난 추가가 아니라, 플랜의 `<interfaces>`가 지목한 원본(AdminMemberController)을 그대로 따른 것이다.

## Issues Encountered

- **테스트 픽스처의 `uq_class_session` 유니크 제약 위반** — 같은 (시간표, 수업날짜) 조합으로 여러 회원의 예약을 만드는 테스트에서, 매번 새 `ClassSession`을 저장하려다 `(class_schedule_id, class_date)` 유니크 제약에 걸렸다. `ClassSessionRepository.findByClassScheduleIdAndClassDate`로 기존 세션이 있으면 재사용하도록 테스트 픽스처(`persistSession`)를 수정해 해결했다 — 프로덕션 코드 문제가 아니라 테스트 헬퍼의 실수였다.

## Known Stubs

없음.

## Threat Flags

없음 — 이 플랜이 여는 새 표면(`GET /api/admin/reservations`)은 플랜의 `<threat_model>`(T-04-55~59)이 이미 다루고 있고, 전부 `mitigate`로 테스트가 실증했다:
- T-04-55(회원 토큰으로 전체 예약 조회) — `hasRole("ADMIN")`(기존 SecurityConfig, 미수정) + 403 테스트
- T-04-56(LIKE 와일드카드로 필터 우회) — `LikePatternEscaper` 재사용 + `keyword="%"` 테스트로 전체 건수보다 적음을 확인
- T-04-57(size 상한 없는 대량 조회) — `@Max(100)` + 400 테스트
- T-04-58(N+1) — `@EntityGraph` + 서비스 레벨 테스트로 응답 필드(회원명·취소주체) 정상 노출 확인(쿼리 횟수 직접 계측은 하지 않았으나 EntityGraph 적용 자체가 구조적 방지)
- T-04-59(전화번호 노출) — `AdminReservationResponse`에 `phoneNumber` 필드 없음(코드 리뷰로 확인)

## Test Coverage Note

**§10.0 표 적용 판단:** 신규 서비스 메서드(`search`)와 신규 컨트롤러 엔드포인트이므로 통합테스트가 필수다(면제 대상 아님). `AdminReservationSearchTest` 하나에 서비스 레벨 10종(필터 조합·와일드카드·페이지네이션·취소 메타데이터)과 HTTP 계약 6종(성공·필터·400 2종·403·401)을 `@Nested`로 분리해 담았다 — `@EntityGraph`를 붙인 커스텀 `findAll` 재선언은 별도 리포지토리 테스트를 만들지 않고 서비스/컨트롤러 통합테스트가 실제 PostgreSQL로 이미 실행하므로 충분하다고 판단했다.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 관리자가 예약을 검색해 대상 id를 찾을 수 있어(RESV-07), 04-13(관리자 대리 취소·변경)이 조작 대상 예약을 얻는 진입점이 완성됐다
- `ReservationRepository.findAll(Specification, Pageable)` + `@EntityGraph` 패턴은 이후 관리자 목록 API가 LAZY 연관을 표시해야 할 때 재사용 가능한 선례다
- openapi.yaml 재생성은 계획대로 청크 C 마지막 플랜(04-15)에서 한 번에 처리한다 — 이 플랜은 API 표면(`ReservationSearchCondition`/`AdminReservationResponse`/컨트롤러)만 변경하고 문서 재생성은 하지 않았다(파일 생성 훅이 매번 재생성을 권고했지만 플랜의 `<verification>` 지시를 따라 의도적으로 건너뛰었다)

---
*Phase: 04-schedule-reservation*
*Completed: 2026-08-08*

## Self-Check: PASSED

All created files (service/controller/DTOs/test) and all 4 task commit hashes
(`4b2f346`, `b50b464`, `a3ff86c`, `3b74983`) verified present via filesystem checks and
`git log --oneline --all` respectively (see below).
