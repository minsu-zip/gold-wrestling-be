---
phase: 06
depth: standard
files_reviewed: 11
files_reviewed_list:
  - src/main/kotlin/com/goldwrestling/notification/NotificationRepository.kt
  - src/main/kotlin/com/goldwrestling/notification/NotificationQueryService.kt
  - src/main/kotlin/com/goldwrestling/notification/AdminNotificationController.kt
  - src/main/kotlin/com/goldwrestling/notification/NotificationSpecifications.kt
  - src/main/kotlin/com/goldwrestling/notification/dto/NotificationResponses.kt
  - src/main/kotlin/com/goldwrestling/notification/dto/ActivityFeedSearchCondition.kt
  - src/test/kotlin/com/goldwrestling/notification/AdminNotificationControllerTest.kt
  - src/test/kotlin/com/goldwrestling/notification/ActivityFeedTest.kt
  - README.md
  - docs/error-codes.md
  - docs/api/openapi.yaml
findings:
  critical: 0
  warning: 1
  info: 1
  total: 2
status: issues_found
---

# Phase 06: Code Review Report (청크 6c — 플랜 06-09·06-10·06-11)

**Reviewed:** 2026-08-19
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

관리자 알림 폴링(NOTIF-02)·활동 피드(NOTIF-03) API와 phase 마감 문서(cron 켜기 절차, 에러코드 정합)를
리뷰했다. 트랜잭션 경계(D-020), 벌크 UPDATE 후 재조회(stale count 방지, T-06-31), LAZY 연관 미접근
(N+1 없음), 엔티티 비노출(D-019), 관리자 전용 권한(D-040, 회원 토큰 403 테스트로 실증), Specification
null 반환 관례, README/`.env.example`/`ROADMAP.md` 정합은 모두 코드와 테스트로 확인했고 결함이 없었다.

한 가지 실질적인 결함을 발견했다 — `GET /api/admin/notifications`의 `page`/`size` 파라미터가 같은 PR
안에서 바로 옆에 추가된 `/api/admin/activity-feed`(`ActivityFeedSearchCondition`)와 달리 **어떤 상한·하한
검증도 없다.** 06-09 계획의 위협 모델(T-06-32)은 "페이지네이션 상한(size 기본 20)"을 DoS 완화책으로
명시했지만, 기본값만 있을 뿐 실제 강제는 없다.

## Warnings

### WR-01: `GET /api/admin/notifications`의 page/size에 검증이 없어 잘못된 값이 500으로 새거나 무제한 조회가 가능하다

**File:** `src/main/kotlin/com/goldwrestling/notification/AdminNotificationController.kt:47-51`

**Issue:**
```kotlin
fun list(
    @RequestParam(defaultValue = "false") unreadOnly: Boolean,
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "20") size: Int,
): NotificationListResponse = notificationQueryService.getNotifications(unreadOnly, page, size)
```
`page`·`size`는 원시 `@RequestParam Int`이고 `@Min`/`@Max` 등 어떤 `jakarta.validation` 제약도 없다.
`NotificationQueryService.getNotifications`는 이 값을 그대로 `PageRequest.of(page, size)`에 전달한다
(`NotificationQueryService.kt:42`). Spring Data의 `PageRequest.of(int, int)`는 `page < 0` 또는
`size < 1`이면 `IllegalArgumentException`을 던진다.

같은 파일·같은 커밋 안에서 바로 아래 추가된 `activityFeed` 엔드포인트(`AdminNotificationController.kt:67-69`)는
`ActivityFeedSearchCondition`에 `@field:Min(0)`(`page`)·`@field:Min(1) @field:Max(100)`(`size`)를 걸어
동일한 문제를 정확히 막아 두었다(`ActivityFeedSearchCondition.kt:26-32`, `ActivityFeedTest.kt`의
"size가 101이면 400 VALIDATION_FAILED다" 테스트로 실증됨). `list`만 이 안전장치 없이 만들어졌다.

**실패 시나리오:**
1. `GET /api/admin/notifications?size=0` (또는 `page=-1`) → `PageRequest.of`가
   `IllegalArgumentException`을 던지고, `@Valid`/`@Min` 제약이 없어 컨트롤러 진입 전에 걸러지지
   않는다. 이 예외는 `GlobalExceptionHandler.handleDomainException`이 아니라
   `handleUnexpectedException`(catch-all)로 떨어져 **HTTP 500 `INTERNAL_ERROR`**로 응답한다 —
   conventions.md §8("검증 실패 400")과 어긋나고, 스택트레이스가 서버 로그에 남는다(운영 모니터링
   관점에서 잡음이 된다).
2. `GET /api/admin/notifications?size=100000` → 상한이 없으므로 그대로 통과해 대량 조회가 가능하다.
   06-09 플랜의 STRIDE 표(T-06-32)는 "페이지네이션 상한(`size` 기본 20)"을 DoS 완화 근거로 명시했지만,
   실제로는 **기본값**만 있을 뿐 **상한**은 강제되지 않는다 — 위협 모델의 완화 주장이 이 엔드포인트에
   대해서는 사실과 다르다.

`AdminNotificationControllerTest`에는 이 경계를 검증하는 테스트가 없다(`ActivityFeedTest`의 대응 테스트와
비교하면 누락이 뚜렷하다).

**Fix:**
```kotlin
fun list(
    @RequestParam(defaultValue = "false") unreadOnly: Boolean,
    @RequestParam(defaultValue = "0") @Min(0) page: Int,
    @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
): NotificationListResponse = notificationQueryService.getNotifications(unreadOnly, page, size)
```
클래스에 `@Validated`를 붙이거나(메서드 파라미터 제약이 동작하려면 필요), `ActivityFeedSearchCondition`처럼
전용 조건 DTO(`NotificationListSearchCondition` 등)로 옮겨 일관성을 맞춘다. 회귀 테스트로
`size=0`·`size=101`·`page=-1` 400 케이스를 `ActivityFeedTest`와 같은 형태로 추가한다.

(참고: 같은 미검증 패턴이 `AdminNoticeController.getList`·`MemberNoticeController`에도 이미 존재해
이 chunk가 새로 만든 패턴은 아니다 — 다만 이 리뷰 범위 안에서는 바로 옆 엔드포인트가 올바른 패턴을
보여주고 있어 불일치가 명확하다.)

## Info

### IN-01: 권한 테스트 1건이 GET·POST 두 엔드포인트를 한 메서드에서 검증한다

**File:** `src/test/kotlin/com/goldwrestling/notification/AdminNotificationControllerTest.kt:228-242`

**Issue:** `` 회원 토큰으로 GET 호출 시 403과 ACCESS_DENIED를 반환한다 ``라는 이름의 테스트 하나가
`GET /api/admin/notifications`와 `POST /api/admin/notifications/read-all` 두 엔드포인트를 모두
호출·단언한다. conventions.md §10.2("한 테스트는 한 가지만 검증한다. 실패했을 때 무엇이 깨졌는지
이름만 보고 알 수 있어야 한다")와 어긋난다 — 첫 번째 `GET` 단언이 실패하면 두 번째 `POST` 단언은
아예 실행되지 않으므로, 두 엔드포인트 중 어느 쪽이 실제로 깨졌는지 테스트 이름·리포트만으로는 알 수
없다. 기능상 결함은 아니다(둘 다 정상 통과 중).

**Fix:** 두 개의 테스트로 분리한다 — `` 회원 토큰으로 GET 호출 시 403과 ACCESS_DENIED를 반환한다 ``,
`` 회원 토큰으로 POST read-all 호출 시 403과 ACCESS_DENIED를 반환한다 ``.

---
*Reviewed: 2026-08-19*
*Reviewer: Claude (gsd-code-reviewer)*
*Depth: standard*
