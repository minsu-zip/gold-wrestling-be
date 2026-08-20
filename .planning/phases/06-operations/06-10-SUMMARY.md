---
phase: 06-operations
plan: 10
subsystem: api
tags: [kotlin, spring-boot, jpa, specification, notification, openapi]

# Dependency graph
requires:
  - phase: 06-operations
    provides: "06-09(NotificationRepository가 JpaSpecificationExecutor를 이미 상속 — 리포지토리 재변경 없이 Specification만 추가하면 됨)"
provides:
  - "NotificationSpecifications — occurredBetween(기간)·hasType(종류), 조건 없으면 null(ReservationSpecifications 관례)"
  - "ActivityFeedSearchCondition — from/to/type/page/size, 범위 역전은 새 에러코드 없이 빈 목록으로 자연 처리"
  - "ActivityFeedItemResponse — isRead/readAt 없는 시간순 표시 전용 응답"
  - "NotificationQueryService.getActivityFeed — Specification.allOf 합성 + occurredAt/id desc 정렬, isRead 조건 없음"
  - "GET /api/admin/activity-feed — AdminNotificationController 클래스 매핑을 /api/admin으로 넓히고 기존 두 경로에 하위 경로 부여, 문자열은 06-09 값 그대로 유지"
  - "ActivityFeedTest 7건 — 무필터/기간/종류/AND조합/size상한/권한/읽음알림노출"
  - "docs/api/openapi.yaml — 피드 경로 1개 + 스키마 2종 추가, Phase 6 전체 10경로(공지4+출석3+알림2+피드1) 완비"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "한 컨트롤러가 여러 리소스 계층(알림 폴링 + 활동 피드)을 담을 때 클래스 매핑을 넓히고 메서드마다 하위 경로를 붙이는 관례(AdminScheduleController 선례)를 두 번째로 적용"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/notification/NotificationSpecifications.kt
    - src/main/kotlin/com/goldwrestling/notification/dto/ActivityFeedSearchCondition.kt
    - src/test/kotlin/com/goldwrestling/notification/ActivityFeedTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/notification/dto/NotificationResponses.kt
    - src/main/kotlin/com/goldwrestling/notification/NotificationQueryService.kt
    - src/main/kotlin/com/goldwrestling/notification/AdminNotificationController.kt
    - docs/api/openapi.yaml

key-decisions:
  - "활동 피드의 from > to 범위 역전은 새 에러코드를 만들지 않고 Specification AND 조합이 자연히 빈 목록을 반환하도록 둔다 — 예약 조회 전용인 INVALID_RESERVATION_SEARCH_RANGE를 재사용하지 않았고, 피드 조회는 파괴적 동작이 아니라 잘못된 범위를 넣어도 데이터가 잘못되지 않는다"
  - "AdminNotificationController 클래스 레벨 매핑을 /api/admin/notifications에서 /api/admin으로 넓히고 기존 두 메서드에 /notifications, /notifications/read-all 하위 경로를 붙였다 — 알림과 피드는 같은 테이블의 다른 뷰지만 FE 화면이 다르고, 피드를 알림 하위 경로에 두면 '알림의 부분 리소스'로 오해된다(AdminScheduleController 선례를 따름). 노출 경로 문자열은 06-09 값과 동일하게 유지해 FE 계약을 깨지 않았다"

patterns-established: []

requirements-completed: [NOTIF-03]

# Metrics
duration: ~20min
completed: 2026-08-19
---

# Phase 06 Plan 10: 활동 피드(기간·종류 필터) + openapi.yaml 재생성 Summary

**관리자 활동 피드 API(`GET /api/admin/activity-feed`)를 알림과 동일한 `notification` 테이블 위에 별도 저장 경로 없이 추가하고, Phase 6 전체 10개 경로를 담은 openapi.yaml을 재생성해 알림 청크를 마감**

## Performance

- **Duration:** ~20min
- **Started:** 2026-08-19T09:44:00+09:00 (06-09 완료 직후 기준 역산)
- **Completed:** 2026-08-19T09:51:03+09:00
- **Tasks:** 3
- **Files modified:** 7 (신규 3, 수정 4)

## Accomplishments
- `NotificationSpecifications`(기간 `occurredBetween`·종류 `hasType`)를 `ReservationSpecifications` 관례 그대로 추가 — 조건이 없으면 `null`을 반환해 호출부가 `Specification.allOf(listOfNotNull(...))`로 자유롭게 합성
- `ActivityFeedSearchCondition`(from/to/type/page/size)과 `ActivityFeedItemResponse`(isRead·readAt 없는 시간순 전용 응답)를 신설해 알림과 피드의 계약을 명확히 분리
- `NotificationQueryService.getActivityFeed`가 `isRead` 조건 없이 `occurredAt desc, id desc`로 정렬해 D-129("읽음 여부 무관 시간순")를 구현
- `AdminNotificationController`가 `AdminScheduleController` 관례(넓은 클래스 매핑 + 메서드별 하위 경로)를 따라 `/api/admin`으로 확장되고 `GET /api/admin/activity-feed`가 신설됨 — 06-09가 노출한 알림 경로 문자열은 그대로 보존돼 FE 계약이 깨지지 않음
- `ActivityFeedTest` 7건(무필터 최신순, 기간 필터, 종류 필터, 기간+종류 AND, size 101→400, 회원 토큰 403, 읽음 처리된 알림도 노출)이 모두 통과하고, 06-09의 `AdminNotificationControllerTest` 6건도 경로 변경 후 그대로 통과
- `docs/api/openapi.yaml` 재생성 — 피드 경로 1개 + `ActivityFeedItemResponse`·`PageResponseActivityFeedItemResponse` 스키마 추가, 쿼리 파라미터가 `from`/`to`/`type`/`page`/`size` 개별 항목으로 기술됨(`@ParameterObject` 정상 작동, D-054/WR-06 재발 없음), 공지·출석·알림 기존 경로 전부 보존, `servers: /` 유지

## Task Commits

Each task was committed atomically:

1. **Task 1: NotificationSpecifications + ActivityFeedSearchCondition + ActivityFeedItemResponse** - `55c37ca` (feat)
2. **Task 2: NotificationQueryService.getActivityFeed + GET /api/admin/activity-feed + ActivityFeedTest** - `f44aacc` (feat)
3. **Task 3: openapi.yaml 재생성** - `f1ed44e` (docs)

**Plan metadata:** (다음 커밋에서 STATE.md·ROADMAP.md와 함께 기록)

_Note: tdd="true"였지만 Task 1은 Specification·DTO·응답 계약 3개 파일이 각각 순수 변환 로직이라 단위테스트 없이 Task 2의 `ActivityFeedTest`(통합테스트)가 실제 동작을 실증한다 — 06-09가 같은 판단을 내린 선례와 동일하게 conventions §10.0 표(리포지토리·컨트롤러 신규 엔드포인트 = Testcontainers 통합테스트 필수)를 따랐다._

## Files Created/Modified
- `src/main/kotlin/com/goldwrestling/notification/NotificationSpecifications.kt` - 기간·종류 동적 필터(신규)
- `src/main/kotlin/com/goldwrestling/notification/dto/ActivityFeedSearchCondition.kt` - 피드 조회 조건 DTO(신규)
- `src/main/kotlin/com/goldwrestling/notification/dto/NotificationResponses.kt` - `ActivityFeedItemResponse` 추가
- `src/main/kotlin/com/goldwrestling/notification/NotificationQueryService.kt` - `getActivityFeed` 메서드 추가
- `src/main/kotlin/com/goldwrestling/notification/AdminNotificationController.kt` - 클래스 매핑 확장 + `/activity-feed` 엔드포인트 추가
- `src/test/kotlin/com/goldwrestling/notification/ActivityFeedTest.kt` - HTTP 계약 테스트 7건(신규)
- `docs/api/openapi.yaml` - 피드 경로 1개 + DTO 2개 재생성

## Decisions Made
- 활동 피드 범위 역전(from > to)은 에러 없이 빈 목록으로 자연 처리(위 key-decisions 참조)
- 컨트롤러 클래스 매핑을 `/api/admin`으로 넓힘(위 key-decisions 참조) — 경로 문자열 자체는 불변

## Deviations from Plan

None - 계획대로 실행됐다.

## Issues Encountered

None - 계획대로 실행됐다.

## User Setup Required

None - 외부 서비스 설정 불필요.

## Next Phase Readiness

- 활동 피드(NOTIF-03)가 완결됨 — 기간·종류 필터, 페이지네이션, 읽음 여부 무관 시간순이 모두 갖춰짐
- Phase 6(운영) 알림 청크(06-09·06-10)가 완료됨 — `./gradlew ktlintFormat && ./gradlew build`가 BUILD SUCCESSFUL로 끝남(전체 회귀 포함, 0 failures)
- `docs/api/openapi.yaml`이 Phase 6 전체 10개 경로(공지 4·출석 3·알림 2·피드 1)를 담은 상태로 갱신됨 — FE가 이 파일로 전체 운영 API 타입을 생성할 수 있음
- 다음 플랜(06-11, phase 마감 검증)으로 진행 가능. 블로커 없음

---

## 이번에 쓴 기술

1. **동적 조건 합성 — `Specification.allOf(listOfNotNull(...))` (Spring Data JPA 4.x)** — 여러 개의 선택적 검색 조건(기간·종류)을 하나의 SQL `WHERE`로 합치는 방법
   - **이 코드에서 왜 필요했는가**: 활동 피드는 "기간만", "종류만", "둘 다", "아무것도 없이" 네 가지 조합을 모두 지원해야 한다. 조합마다 별도 쿼리 메서드를 만들면 2×2=4개 메서드가 되고, 필터가 하나 늘 때마다 조합 수가 배로 늘어난다. `NotificationSpecifications.occurredBetween`·`hasType`이 조건이 없을 때 `null`을 반환하게 만들고, `listOfNotNull(...)`로 `null`을 걸러낸 뒤 `Specification.allOf`로 AND 결합하면 조합의 수만큼 코드를 늘리지 않고도 모든 조합을 한 메서드로 처리한다.
   - **안 썼으면 뭐가 깨지는가**: 필터 조합마다 파생 쿼리(`findByOccurredAtBetweenAndType`, `findByOccurredAtBetween`, `findByType`, `findAll`)를 전부 만들어야 하고, 새 필터(예: 회원 검색어)가 하나 추가될 때마다 기존 메서드 개수가 배로 불어나 유지보수가 불가능해진다.

2. **정렬 축을 `occurredAt`으로 고정 — `createdAt`이 아니다** — 데이터가 "언제 저장됐는가"와 "언제 실제로 벌어졌는가"를 구분하는 개념
   - **이 코드에서 왜 필요했는가**: 활동 피드는 관리자가 "무슨 일이 언제 일어났는지" 보는 타임라인이다. 만약 배치 작업이나 지연된 처리로 알림이 실제 이벤트 발생 시점보다 늦게 저장되면, `createdAt`(저장 시각) 기준 정렬은 실제 사건 순서와 어긋난 타임라인을 보여준다. `occurredAt`(이벤트 발생 시각)을 축으로 고정해 이 둘을 명확히 분리했다.
   - **안 썼으면 뭐가 깨지는가**: 저장 순서와 실제 사건 순서가 다를 경우(예: 여러 이벤트가 한 트랜잭션에 묶여 나중에 일괄 저장되는 경우) 관리자가 보는 타임라인 순서가 실제와 어긋나는 조용한 버그가 된다 — 겉으로는 정상 작동하는 것처럼 보이지만 감사·추적 목적에 맞지 않는다.

3. **같은 데이터의 "다른 뷰" — 별도 저장 경로 없이 조회 쿼리만 분기(D-129)**
   - **이 코드에서 왜 필요했는가**: 알림(미확인 카운트·30초 폴링용)과 활동 피드(전체 이력 타임라인)는 관리자 화면에서 서로 다른 UI지만, 담고 있는 데이터의 실체(`notification` 테이블의 행)는 완전히 같다. 피드 전용 테이블을 새로 만들면 같은 이벤트가 두 곳에 각각 쓰여야 하고, 한쪽 쓰기 로직이 바뀌었을 때 다른 쪽을 놓치면 두 화면이 서로 다른 사실을 보여주는 정합성 붕괴가 생긴다.
   - **안 썼으면 뭐가 깨지는가**: 예약 생성 이벤트가 알림 테이블에는 기록됐는데 피드 전용 테이블에는 기록이 누락되는 식의 이중 쓰기 실패가 언젠가 발생한다 — "알림함에는 있는데 피드에는 없다"는 회원 관점에서 설명 불가능한 불일치로 나타난다.

---
*Phase: 06-operations*
*Completed: 2026-08-19*

## Self-Check: PASSED

All created/modified files verified to exist on disk (`NotificationSpecifications.kt`,
`ActivityFeedSearchCondition.kt`, `NotificationResponses.kt`, `NotificationQueryService.kt`,
`AdminNotificationController.kt`, `ActivityFeedTest.kt`, `docs/api/openapi.yaml`, this SUMMARY.md);
all task commit hashes (`55c37ca`, `f44aacc`, `f1ed44e`) verified present in git log.
