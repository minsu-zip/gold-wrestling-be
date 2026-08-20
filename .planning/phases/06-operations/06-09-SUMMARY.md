---
phase: 06-operations
plan: 09
subsystem: api
tags: [kotlin, spring-boot, jpa, notification, polling, testcontainers, openapi]

# Dependency graph
requires:
  - phase: 06-operations
    provides: "06-08(출석 청크 마감, openapi.yaml 최신 상태) — 알림·피드 wave를 출석 청크 뒤에 배치한 이유(D-084 청크 경계)"
provides:
  - "NotificationRepository 확장 — findAllByOrderByOccurredAtDescIdDesc·findAllByIsReadFalseOrderByOccurredAtDescIdDesc·countByIsReadFalse·markAllAsRead(벌크 UPDATE) + JpaSpecificationExecutor 상속(06-10 활동 피드 대비)"
  - "NotificationQueryService — getNotifications(unreadOnly/page/size), markAllAsRead(벌크 UPDATE 이후 재조회로 stale count 방지)"
  - "AdminNotificationController — GET /api/admin/notifications(목록+unreadCount), POST /api/admin/notifications/read-all"
  - "AdminNotificationControllerTest 6건 — 목록/필터/모두읽음 회귀/readAt 보존/비정규화 필드/권한"
  - "docs/api/openapi.yaml — 알림 경로 2개 + DTO 3종(NotificationResponse·NotificationListResponse·MarkAllReadResponse) 추가"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "벌크 UPDATE(@Modifying flushAutomatically/clearAutomatically) 직후 반드시 재조회 — PassRepository.adjustRemainingCount 관례를 조회 전용 카운트(countByIsReadFalse)에도 동일하게 적용"
    - "알림 응답 DTO는 Notification의 LAZY 연관(reservation/classSession)을 건드리지 않고 이벤트 시점 비정규화 필드만으로 조립한다"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/notification/dto/NotificationResponses.kt
    - src/main/kotlin/com/goldwrestling/notification/NotificationQueryService.kt
    - src/main/kotlin/com/goldwrestling/notification/AdminNotificationController.kt
    - src/test/kotlin/com/goldwrestling/notification/AdminNotificationControllerTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/notification/NotificationRepository.kt
    - docs/api/openapi.yaml

key-decisions:
  - "Notification.memberName은 Member로의 FK가 아니라 비정규화 문자열이라, 테스트 알림 픽스처는 실제 Member 엔티티 없이 문자열만으로 만든다 — 회원 토큰 발급이 필요한 권한 테스트만 예외적으로 실제 Member를 생성한다"
  - "Phase 4의 NotificationService(생성 전용)는 무변경 — git diff로 확인. 조회 책임은 신규 NotificationQueryService로 완전히 분리했다"

patterns-established:
  - "조회 전용 서비스(NotificationQueryService)와 생성 전용 서비스(NotificationService)를 같은 패키지 안에서 파일로 분리 — 서로 다른 phase가 만든 책임이 서로의 회귀 테스트를 건드리지 않는다"

requirements-completed: [NOTIF-02]

# Metrics
duration: ~30min
completed: 2026-08-19
---

# Phase 06 Plan 09: 관리자 알림 폴링(목록+미확인 카운트) + 모두읽음 Summary

**관리자 알림 폴링 API 2종(GET 목록+unreadCount, POST 모두읽음)을 만들어 FE가 30초 폴링 1회 호출로 알림함을 유지하게 하고, 벌크 UPDATE 직후 stale 카운트가 새지 않음을 실제 커밋으로 실증**

## Performance

- **Duration:** ~30min
- **Started:** 2026-08-19T09:00:00+09:00 (파일 조사 시작 기준 역산)
- **Completed:** 2026-08-19T09:43:30+09:00
- **Tasks:** 3
- **Files modified:** 6 (신규 4, 수정 2)

## Accomplishments
- `NotificationRepository`가 `JpaSpecificationExecutor`를 상속하고, 폴링 조회 2종(전체/미확인) + `countByIsReadFalse` + `markAllAsRead` 벌크 UPDATE(`where isRead = false`로 기존 `readAt` 보존)를 추가
- `NotificationQueryService.getNotifications`가 목록과 `unreadCount`를 한 응답에 담아 D-129("폴링 1회로 끝난다")를 실현. `markAllAsRead`는 벌크 UPDATE **이후** `countByIsReadFalse`를 재조회해 응답에 stale 값이 새지 않게 함(T-06-31)
- `AdminNotificationController` — `GET /api/admin/notifications`(목록+미확인카운트), `POST /api/admin/notifications/read-all`(모두읽음). `/api/admin/**` 전역 `hasRole("ADMIN")`에 의존해 별도 권한·트랜잭션·try-catch 없음(D-040·D-020·D-017)
- `AdminNotificationControllerTest` 6건 — 목록 카운트(3건/미확인2), `unreadOnly=true` 필터(2건), 모두읽음 응답+직후 재조회 `unreadCount=0`(벌크 UPDATE stale 값 회귀 테스트), 이미 읽은 알림의 `readAt` 불변, 비정규화 표시 필드(memberName·classDate·classType) 노출, 회원 토큰 403 `ACCESS_DENIED`
- `docs/api/openapi.yaml` 재생성 — 알림 목록·모두읽음 경로 2개 + `NotificationResponse`·`NotificationListResponse`·`MarkAllReadResponse` 3개 스키마 추가, 기존 경로 전부 보존

## Task Commits

Each task was committed atomically:

1. **Task 1: NotificationRepository 확장(폴링 조회·미확인 카운트·모두읽음) + 응답 DTO** - `47adc19` (feat)
2. **Task 2: NotificationQueryService + AdminNotificationController** - `ee05e25` (feat)
3. **Task 3: AdminNotificationControllerTest — 목록·필터·카운트·모두읽음·권한** - `0aa7fca` (test)

**Plan metadata:** (다음 커밋에서 STATE.md·ROADMAP.md와 함께 기록)

_Note: tdd="true"였지만 세 태스크 모두 리포지토리·서비스·컨트롤러 계층으로 명확히 분리돼 있어 RED/GREEN을 하나의 사이클로 강제하지 않고 conventions §10.0 표(리포지토리 커스텀 쿼리·컨트롤러 신규 엔드포인트 = Testcontainers 통합테스트 필수)를 그대로 따랐다 — Task 3의 통합테스트가 Task 1·2의 리포지토리 쿼리·서비스 로직을 실제 PostgreSQL로 실증한다._

## Files Created/Modified
- `src/main/kotlin/com/goldwrestling/notification/NotificationRepository.kt` - `JpaSpecificationExecutor` 상속 + 폴링 조회 2종 + `countByIsReadFalse` + `markAllAsRead` 벌크 UPDATE
- `src/main/kotlin/com/goldwrestling/notification/dto/NotificationResponses.kt` - `NotificationResponse`·`NotificationListResponse`·`MarkAllReadResponse`(신규)
- `src/main/kotlin/com/goldwrestling/notification/NotificationQueryService.kt` - 조회 전용 서비스(신규)
- `src/main/kotlin/com/goldwrestling/notification/AdminNotificationController.kt` - 알림 API 2종(신규)
- `src/test/kotlin/com/goldwrestling/notification/AdminNotificationControllerTest.kt` - HTTP 계약 테스트 6건(신규)
- `docs/api/openapi.yaml` - 알림 경로 2개 + DTO 3개 재생성

## Decisions Made
- 알림 픽스처는 실제 `Member` 엔티티 없이 문자열(`memberName`)만으로 구성(위 key-decisions 참조) — `Notification.memberName`이 FK가 아니라 비정규화 필드이기 때문
- `NotificationService`(Phase 4, 생성 전용)는 그대로 두고 조회 책임을 `NotificationQueryService`로 완전히 분리(위 patterns-established 참조)

## Deviations from Plan

None - 계획대로 실행됐다.

## Issues Encountered

None - 계획대로 실행됐다.

## User Setup Required

None - 외부 서비스 설정 불필요.

## Next Phase Readiness

- 알림 폴링(NOTIF-02)이 완결됨 — 목록·미확인 카운트·모두읽음·권한·stale 값 회귀 방지가 모두 갖춰짐
- `./gradlew ktlintFormat && ./gradlew build`가 BUILD SUCCESSFUL로 끝남(전체 회귀 포함, 0 failures)
- FE는 `docs/api/openapi.yaml`로 알림 API 타입을 생성할 수 있음
- `NotificationRepository`가 `JpaSpecificationExecutor`를 이미 상속하고 있어, 다음 플랜(06-10 활동 피드)이 `NotificationSpecifications`(기간·종류 필터)만 추가하면 됨 — 리포지토리 재변경 불필요
- 블로커 없음. 다음 플랜(06-10, 활동 피드)으로 진행 가능

---

## 이번에 쓴 기술

1. **벌크 UPDATE(JPQL `@Modifying`) + `flushAutomatically`/`clearAutomatically`** — 여러 행을 한 번에 갱신하는 SQL을 영속성 컨텍스트(1차 캐시)를 우회해 직접 DB에 보내는 방식
   - **이 코드에서 왜 필요했는가**: "모두 읽음"은 미확인 알림이 몇백 건이든 한 번의 `UPDATE ... WHERE is_read = false`로 처리해야 한다. 엔티티를 하나씩 조회해서 `isRead = true`로 바꾸고 저장하면 알림이 많을수록 쿼리가 알림 개수만큼 늘어난다. `flushAutomatically`는 이 UPDATE를 실행하기 전에 대기 중인 다른 변경사항을 먼저 반영해 최신 상태 위에서 정확히 실행되게 하고, `clearAutomatically`는 실행 직후 1차 캐시를 비워서 "방금 실행한 벌크 UPDATE 이전 상태로 캐시된 낡은 값"을 다시 읽는 사고를 막는다.
   - **안 썼으면 뭐가 깨지는가**: `clearAutomatically` 없이 같은 트랜잭션에서 알림을 다시 조회하면, JPA가 DB에 다시 쿼리를 보내지 않고 1차 캐시에 남아 있는 갱신 전(`isRead = false`) 엔티티를 그대로 돌려줄 수 있다 — "모두 읽음을 눌렀는데 미확인 카운트가 그대로다"라는 눈에 보이는 버그가 된다.

2. **★ Repository 파생 쿼리 이름 규칙(Spring Data JPA method-name query)** — `findAllByOrderByOccurredAtDescIdDesc`처럼 메서드 이름 자체가 SQL의 `ORDER BY`·`WHERE`를 표현하는 Spring Data의 관례
   - **이 코드에서 왜 필요했는가**: "전체 최신순"과 "미확인만 최신순"은 조건 하나(`isRead = false`)만 다른 단순 조회다. `@Query`로 JPQL을 직접 쓸 수도 있지만, 이 정도로 단순한 조건은 메서드 이름만으로 Spring Data가 쿼리를 자동 생성해준다 — `PassRepository`의 관례를 그대로 따랐다.
   - **안 썼으면 뭐가 깨지는가**: 크게 깨지지는 않지만, 단순 조회마다 JPQL 문자열을 손으로 쓰면 오타·타입 불일치가 컴파일 타임이 아니라 런타임(앱 기동 시점)에야 드러난다. 이름 규칙 쿼리는 앱 기동 시점에 메서드 시그니처를 파싱해 검증하므로 실수를 더 빨리 잡는다.

3. **컨트롤러/서비스 책임 분리 — 같은 엔티티를 다루는 두 서비스가 서로 다른 phase 소유** — `NotificationService`(생성 전용, Phase 4)와 `NotificationQueryService`(조회 전용, 이번 phase)를 별도 클래스로 유지
   - **이 코드에서 왜 필요했는가**: `Notification`은 예약·휴강 이벤트가 트리거하는 "쓰기"(Phase 4)와 관리자 폴링이 쓰는 "읽기"(Phase 6)가 성격이 완전히 다르다. 한 클래스에 몰아넣으면 이번 phase의 변경이 Phase 4가 이미 검증한 알림 생성 로직의 회귀 위험을 만든다.
   - **안 썼으면 뭐가 깨지는가**: `NotificationService`를 수정했다면 `git diff src/main/kotlin/com/goldwrestling/notification/NotificationService.kt`가 비어 있지 않게 되고, Task 2의 acceptance criteria가 실패했을 것이다 — 이는 "생성 경로는 건드리지 않는다"는 이 plan의 명시적 계약이었다.

---
*Phase: 06-operations*
*Completed: 2026-08-19*

## Self-Check: PASSED

All created/modified files verified to exist on disk (`NotificationResponses.kt`, `NotificationQueryService.kt`,
`AdminNotificationController.kt`, `AdminNotificationControllerTest.kt`, `NotificationRepository.kt`, this
SUMMARY.md); all task commit hashes (`47adc19`, `ee05e25`, `0aa7fca`) verified present in git log.
