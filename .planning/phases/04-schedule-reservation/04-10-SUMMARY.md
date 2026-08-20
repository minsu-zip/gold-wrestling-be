---
phase: 04-schedule-reservation
plan: 10
subsystem: api
tags: [kotlin, spring-boot, jpa, postgresql, reservation, idor, tdd]

# Dependency graph
requires:
  - phase: 04-schedule-reservation (plan 04-07·04-08)
    provides: "MemberReservationService.reserve 예약 생성 트랜잭션 조립 + POST /api/members/me/reservations"
  - phase: 04-schedule-reservation (plan 04-09)
    provides: "Reservation.assertCancelableByMember/assertChangeableByMember, ReservationRefundPolicy.shouldRestore"
provides:
  - "MemberReservationService.cancel/change/findMyReservations — 취소·변경·본인 목록"
  - "POST /api/members/me/reservations/{id}/cancellation, /change, GET /reservations 엔드포인트"
  - "ReservationSpecifications (ownedByMember 등) — 04-12 관리자 조회가 재사용할 IDOR 방어 조건 모음"
  - "LikePatternEscaper(common) — 두 번째 기능 패키지가 LIKE 이스케이프를 재사용하는 시점에 승격"
  - "청크 B(wave 6~10) 전체 — openapi.yaml 재생성 완료"
affects: [04-11, 04-12]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "차감/복구 경로 단일화: reserve의 스텝 ③~⑪을 private createReservationInternal로, 취소 반영
       스텝을 private performMemberCancellation으로 뽑아 reserve/cancel/change 세 메서드가 공유"
    - "회원 경로 재조회는 항상 findByIdAndMemberId만 사용 — bare findById(Reservation)를 쓰지 않아
       IDOR 방어가 조회 시그니처 수준에서 구조적으로 유지됨"
    - "IDOR 방어 Specification 관례 확장: PassTransactionSpecifications.ownedByMember → 이번 플랜의
       ReservationSpecifications.ownedByMember도 동일하게 non-null 강제 반환"
    - "LIKE 이스케이프 유틸(common/LikePatternEscaper) — 두 기능 패키지가 실제로 쓰게 된 시점에
       member 패키지 전용 private 함수를 common으로 승격(PageResponse KDoc이 예고한 패턴)"
    - "컨트롤러 통합테스트에서 한 테스트에 회원 2명 이상 필요하면 두 토큰 모두 클록 이동 전에
       발급해야 한다(tokenFor/moveClockToThisWeekMonday로 분리) — 안 그러면 두 번째 토큰의
       issuedAt이 합성 날짜(비즈니스 시계) 기준이 되어 401이 난다"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/reservation/ReservationSpecifications.kt
    - src/main/kotlin/com/goldwrestling/reservation/dto/ChangeReservationRequest.kt
    - src/main/kotlin/com/goldwrestling/reservation/dto/MyReservationSearchCondition.kt
    - src/main/kotlin/com/goldwrestling/common/LikePatternEscaper.kt
    - src/test/kotlin/com/goldwrestling/reservation/MemberReservationCancellationTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/reservation/MemberReservationService.kt
    - src/main/kotlin/com/goldwrestling/reservation/MemberReservationController.kt
    - src/main/kotlin/com/goldwrestling/member/MemberSpecifications.kt
    - src/test/kotlin/com/goldwrestling/reservation/MemberReservationControllerTest.kt
    - docs/api/openapi.yaml

key-decisions:
  - "취소/변경의 응답 재조회는 항상 findByIdAndMemberId — bare findById(Reservation)를 회원 경로
     어디에서도 쓰지 않아 acceptance criteria(‘findById(를 회원 경로에서 쓰지 않는다’)를 문자 그대로
     지킨다. member(Member)는 clearAutomatically 이후에도 안전하게 재사용하려고 memberRepository로
     직접(entity, not lazy proxy) 조회한다(AdminPassService.adjust의 admin 관례)"
  - "LikePatternEscaper를 common으로 승격 — 04-10 계획의 '재구현하지 않고 재사용' 지시를 문자 그대로
     지키려면 MemberSpecifications의 private 이스케이프 함수를 재사용 가능한 곳으로 옮겨야 했다.
     PageResponse KDoc이 이미 이 트리거(두 번째 기능 패키지가 실제로 쓰는 시점)를 예고해 뒀다"

patterns-established:
  - "TDD 컨트롤러 테스트에서 다회원 시나리오는 tokenFor(클록 고정 없이 발급) + moveClockToThisWeekMonday
     (클록만 이동)로 토큰 발급과 클록 이동을 분리 — MutableTestClock 싱글턴 특성상 순서를 지키지
     않으면 두 번째 회원 토큰이 즉시 401이 된다"

requirements-completed: [RESV-04, RESV-05, NOTIF-01]

duration: ~60min
completed: 2026-08-08
---

# Phase 4 Plan 10: 예약 취소·변경 + 본인 목록 조회 (청크 B 마감) Summary

**회원 예약 취소·변경을 단일 트랜잭션으로 완성하고(D-090), IDOR을 조회 시그니처 수준(`findByIdAndMemberId`)에서 구조적으로 차단하는 `GET /api/members/me/reservations`를 추가한 뒤, 청크 B(wave 6~10)의 openapi.yaml을 재생성해 예약 도메인 전체를 리뷰 가능한 PR로 마감했다.**

## Performance

- **Duration:** ~60min
- **Tasks:** 3 completed (Task 1·2는 TDD RED→GREEN, Task 3는 표준)
- **Files modified:** 10 (신규 5, 수정 5)

## Accomplishments

- 회원이 예약을 취소하면 즉시 잔여가 복구되고(`CANCEL_REFUND` 이력), 등록 취소(`CANCELED`)된 이용권으로 잡힌 예약을 취소할 때는 예약만 취소되고 잔여는 복구되지 않는 방어 분기(D-091 Pitfall 2)가 실제 PostgreSQL 통합테스트로 고정됐다
- 회원이 예약을 변경하면 "취소 + 재예약"이 하나의 트랜잭션으로 처리되고, 새 타임이 실패하면(정원 초과·창 마감·종류 불일치·당일) 기존 예약이 그대로 ACTIVE로 남는다 — 4가지 실패 경로 모두 테스트로 확인
- `reserve`/`cancel`/`change` 세 메서드가 `createReservationInternal`/`performMemberCancellation` 두 private 헬퍼를 공유해 차감/복구 경로가 하나로 유지된다
- 본인 예약 목록(`GET /api/members/me/reservations`)이 활성 예약만, 본인 것만(같은 세션에 타 회원이 예약해도 안 섞임) `classDate`·`startTime` 오름차순으로 반환한다
- 타인 예약 id로 취소·변경을 시도하면 403이 아니라 404(`RESERVATION_NOT_FOUND`)로 응답해 예약 존재 여부를 노출하지 않는다(T-04-45)
- 청크 B(wave 6~10 — 시간표 실체화·예약 생성·동시성 3종·정책 판정·취소/변경/목록)가 `openapi.yaml` 재생성으로 API 계약과 동기화됐다

## Task Commits

Each task was committed atomically (TDD RED → GREEN):

1. **Task 1: 예약 취소·변경 서비스 (단일 트랜잭션)**
   - `b2fa882` test(04-10): 예약 취소·변경 실패 테스트 추가 (RED)
   - `38d03e4` feat(04-10): 예약 취소·변경 서비스 추가 (단일 트랜잭션) (GREEN)
2. **Task 2: 회원 취소·변경·본인 목록 엔드포인트 + IDOR 방어 Specification**
   - `05d45b0` test(04-10): 취소·변경·본인 목록 엔드포인트 실패 테스트 추가 (RED)
   - `c684631` feat(04-10): 회원 취소·변경·본인 목록 엔드포인트 + IDOR 방어 Specification 추가 (GREEN)
3. **Task 3: openapi.yaml 재생성 + 청크 B 마감 점검**
   - `025a9b9` docs(04-10): openapi.yaml 재생성 — 청크 B(wave 6~10) 예약 API 계약 반영

**Plan metadata:** (다음 커밋, docs: complete plan)

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/reservation/MemberReservationService.kt` — `cancel`/`change`/`findMyReservations` 추가, `reserve`를 `createReservationInternal` 공유 헬퍼로 리팩터링
- `src/main/kotlin/com/goldwrestling/reservation/MemberReservationController.kt` — `POST .../cancellation`·`POST .../change`·`GET /reservations` 3개 엔드포인트, 모두 `memberStateGate.requireActive` 우선 호출
- `src/main/kotlin/com/goldwrestling/reservation/ReservationSpecifications.kt` — `ownedByMember`(non-null 강제)·`hasStatus`·`classDateBetween`·`hasClassType`·`memberKeywordContains`(04-12 재사용 대비)
- `src/main/kotlin/com/goldwrestling/reservation/dto/ChangeReservationRequest.kt`, `MyReservationSearchCondition.kt` — 신규 요청/조회 DTO
- `src/main/kotlin/com/goldwrestling/common/LikePatternEscaper.kt` — LIKE 이스케이프 공용 유틸(신규, `member`→`common` 승격)
- `src/main/kotlin/com/goldwrestling/member/MemberSpecifications.kt` — `escapeLikeWildcards` private 함수를 `LikePatternEscaper` 위임으로 교체(행위 변경 없음, 기존 `MemberSpecificationTest` 18개 전부 통과 확인)
- `src/test/kotlin/com/goldwrestling/reservation/MemberReservationCancellationTest.kt` — 취소 6종·변경 5종, 총 11개 통합테스트(신규)
- `src/test/kotlin/com/goldwrestling/reservation/MemberReservationControllerTest.kt` — 취소·변경·목록 8종 추가(기존 10 + 8 = 18개)
- `docs/api/openapi.yaml` — 청크 B 전체 API 계약 재생성

## Decisions Made

- **취소·변경 응답 재조회는 항상 `findByIdAndMemberId`** — 벌크 UPDATE(`clearAutomatically`) 이후 알림 생성·응답 변환에 쓸 영속 `Reservation`이 필요할 때, bare `findById`(Reservation)를 쓰지 않고 소유권 조건이 포함된 `findByIdAndMemberId`로 재조회한다. 이렇게 하면 acceptance criteria("`findById(`를 회원 경로에서 쓰지 않는다")를 리팩터링 이후에도 문자 그대로 지킬 수 있고, IDOR 방어가 조회 시그니처 수준에서 처음부터 끝까지 일관된다. `member`(Member)는 `reservation.member`(LAZY 프록시)를 그대로 재사용하지 않고 `memberRepository.findById(memberId)`로 직접 조회했다 — `AdminPassService.adjust`가 `admin`을 벌크 UPDATE 전에 미리 로드해 이후에도 재사용하는 관례와 동일하게, `clearAutomatically` 이후에도 안전하게 FK 참조로 쓸 수 있는 완전히 로드된 엔티티를 확보하기 위해서다.
- **`LikePatternEscaper`를 `common`으로 승격** — 플랜의 `ReservationSpecifications.memberKeywordContains` action 지시("`MemberSpecifications.keywordContains`의 이스케이프·정규화 로직을 재구현하지 않고 재사용한다")를 문자 그대로 지키려면, `MemberSpecifications`에 `private`으로 있던 `escapeLikeWildcards`를 어디선가 공유해야 했다. `docs/conventions.md` §1("두 기능 이상이 실제로 쓰기 전에는 넣지 않는다")과 `PageResponse` KDoc이 이미 예고한 승격 조건(두 번째 기능 패키지가 실제로 쓰는 시점)이 이번에 충족돼 `common/LikePatternEscaper.kt`로 옮겼다. `MemberSpecifications.keywordContains`의 공개 시그니처·행위는 바꾸지 않았고 기존 `MemberSpecificationTest`(18개)가 전부 그대로 통과한다.
- **`reserve` 리팩터링(스텝 ③~⑪ → `createReservationInternal`)은 순수 리팩터링** — 플랜이 명시적으로 요구한 작업("③④는 reserve/cancel과 같은 private 헬퍼를 공유하도록 리팩터링한다")이며, 기존 `MemberReservationServiceTest`(10개, 04-07/04-08 산출물)가 전부 그대로 통과해 행위 변경이 없음을 확인했다.

## Deviations from Plan

None — plan executed as written. `LikePatternEscaper` 승격은 plan의 `ReservationSpecifications` action 지시가 요구한 "재구현 금지"를 구현하는 과정에서 나온 자연스러운 확장이며 `MemberSpecifications.kt`가 `files_modified`에 명시되지 않았지만 이 지시를 충족하기 위한 최소 변경이었다(Rule 2 성격 — 요구된 재사용을 실제로 가능하게 만드는 인프라).

## Issues Encountered

- **컨트롤러 통합테스트 다회원 시나리오에서 401** — "같은 세션에 두 회원이 예약" 테스트와 "타인 예약 id로 취소·변경" 테스트에서, 기존 `tokenAtThisWeekMonday(member)` 헬퍼를 두 번째 회원에게도 그대로 호출했더니 두 번째 토큰이 401로 거부됐다. 원인: `MutableTestClock`이 첫 번째 호출에서 이미 "이번 주 월요일"(합성 연도, 예: 2092년)로 이동해 있는 상태에서 두 번째 `tokenService.issueTokenPair`를 호출하면 그 토큰의 `issuedAt`이 실제 시스템 시각이 아니라 합성 날짜가 되고, `JwtDecoder`는 실제 시스템 시각으로 검증하므로 즉시 만료/미래 토큰으로 거부된다. `tokenFor(member)`(클록 이동 없이 토큰만 발급)와 `moveClockToThisWeekMonday()`(클록만 이동)로 분리해, 여러 회원이 필요한 테스트는 모든 토큰을 클록 이동 **전에** 발급하도록 정정했다. 기존 단일 회원 테스트는 `tokenAtThisWeekMonday`가 내부적으로 같은 순서(토큰 → 클록 이동)를 유지해 영향이 없다.

## Known Stubs

없음.

## Threat Flags

없음 — 이 플랜이 여는 새 표면(`POST .../cancellation`, `POST .../change`, `GET /reservations`)은 플랜의 `<threat_model>`(T-04-45~50)이 이미 다루고 있고, 전부 `mitigate`로 테스트가 실증했다:
- T-04-45(IDOR) — `findByIdAndMemberId`만 사용, 타인 예약 id는 404
- T-04-46(목록 노출) — `ownedByMember` non-null 강제 + 두 회원 픽스처 테스트
- T-04-47(변경을 통한 당일 우회) — `assertChangeableByMember`가 `assertCancelableByMember`를 먼저 호출
- T-04-48(변경 실패 시 기존 예약 손실) — 단일 트랜잭션 + 4종 실패 테스트가 기존 예약 ACTIVE 유지를 단언
- T-04-49(이력 없는 복구/누락) — `shouldRestore` 판정 하나로 `adjustRemainingCount`+`PassTransaction` 저장을 함께 감쌈
- T-04-50(비활성 회원 접근) — 세 엔드포인트 모두 `memberStateGate.requireActive`

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 회원 예약 생성·취소·변경·본인 목록 조회 API 전체(RESV-01~05)가 동작하고, 청크 B(wave 6~10)의 `openapi.yaml`이 코드와 동기화되어 dev PR을 낼 준비가 됐다
- `ReservationSpecifications`의 `classDateBetween`·`hasClassType`·`memberKeywordContains`는 이번 플랜에서 아직 쓰이지 않는다 — 04-12(관리자 예약 조회)가 그대로 재사용할 수 있게 미리 만들어 뒀다
- 04-11(관리자 대리 취소/변경)이 `performMemberCancellation`과 유사한 관리자 버전 헬퍼를 만들 때 이 플랜의 "취소 반영 공통 경로" 관례를 참고할 수 있다

---
*Phase: 04-schedule-reservation*
*Completed: 2026-08-08*

## Self-Check: PASSED

All created files and commit hashes verified present via `git log --oneline --all` and filesystem checks (see below).
