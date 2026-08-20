---
quick_id: 260820-cp1
slug: api-page-size
date: 2026-08-20
type: quick
source: .planning/phases/06-operations/06-VERIFICATION.md (D-01)
files_modified:
  - src/main/kotlin/com/goldwrestling/notice/dto/NoticeSearchCondition.kt
  - src/main/kotlin/com/goldwrestling/notice/AdminNoticeController.kt
  - src/main/kotlin/com/goldwrestling/notice/MemberNoticeController.kt
  - src/main/kotlin/com/goldwrestling/notice/NoticeService.kt
  - src/test/kotlin/com/goldwrestling/notice/AdminNoticeControllerTest.kt
  - src/test/kotlin/com/goldwrestling/notice/MemberNoticeControllerTest.kt
  - docs/api/openapi.yaml
---

# 공지 목록 API의 page·size 검증 추가 (D-01)

## 문제

`GET /api/admin/notices`·`GET /api/members/notices`가 `page`/`size`를 검증 없이
`@RequestParam Int`로 받아 그대로 `PageRequest.of`에 넘긴다. 그 결과:

| 요청 | 현재 | 기대 |
|---|---|---|
| `?size=0` | **500** `INTERNAL_ERROR` | 400 `VALIDATION_FAILED` |
| `?page=-1` | **500** `INTERNAL_ERROR` | 400 `VALIDATION_FAILED` |
| `?size=100000` | **200** (전량 조회) | 400 `VALIDATION_FAILED` |

실제 앱으로 재현 확인함(관리자·회원 양쪽 500).

`PageRequest.of`가 던지는 `IllegalArgumentException`이 `GlobalExceptionHandler`의 포괄
핸들러에 걸려 500이 된다. **잘못된 입력은 4xx여야 한다**(conventions §8) — 500은 "서버 잘못"이라
클라이언트가 재시도하고 운영 알림이 울린다.

## 왜 지금 고치는가

`AdminNotificationController`에서 **완전히 같은 결함**이 PR #20 리뷰(WR-01)로 발견돼 이미
수정됐다. 그런데 같은 phase에서 **먼저 만들어진** 공지 컨트롤러(청크 6a, PR #18)에는 반영되지
않은 채 남았다 — 리뷰가 "그 PR의 diff"만 보기 때문에 생기는 누락이다.

전수 스캔 결과 **공지 2곳이 유일한 예외**다. 나머지 페이지네이션 API는 전부 조건 객체 +
`@Min`/`@Max`를 쓴다(`MemberSearchCondition`, `ReservationSearchCondition`,
`MyReservationSearchCondition`, `PassTransactionSearchCondition`, `ActivityFeedSearchCondition`,
`NotificationSearchCondition`). `AdminBatchService`는 `limit.coerceIn(1, MAX_LIMIT)`로 방어까지 한다.

## Tasks

### Task 1 — `NoticeSearchCondition` 신설 + 컨트롤러·서비스 배선

`src/main/kotlin/com/goldwrestling/notice/dto/NoticeSearchCondition.kt`를 만든다.
**`NotificationSearchCondition`을 선례로 그대로 따른다** (같은 관례 유지가 이 작업의 목적이다):

- `@field:Min(0)` on `page` (기본 0)
- `@field:Min(1) @field:Max(100)` on `size` (기본 20)
- `@field:Schema`로 설명·기본값 명시
- KDoc에 왜 조건 객체로 묶는지(검증 없는 `Int`가 500을 만든다) 남긴다

두 컨트롤러의 `getList`를 `@ParameterObject @ModelAttribute @Valid condition: NoticeSearchCondition`
으로 바꾸고, `NoticeService.getList(page, size)` 시그니처를 `getList(condition)`으로 바꾼다.
사용하지 않게 된 `RequestParam` import를 지운다.

관리자·회원 컨트롤러가 같은 조건 객체를 공유한다 — 두 목록은 정렬·페이지 계약이 동일하다.

### Task 2 — 회귀 테스트

`AdminNoticeControllerTest`·`MemberNoticeControllerTest` 각각에 3건씩 추가한다
(`AdminNotificationControllerTest`의 동명 테스트가 선례):

- `size가 0이면 500이 아니라 400 VALIDATION_FAILED다`
- `page가 음수면 500이 아니라 400 VALIDATION_FAILED다`
- `size가 101이면 400 VALIDATION_FAILED다`

정상 경로(`size=20` 등)가 여전히 200인지 확인하는 기존 테스트가 깨지지 않아야 한다.

### Task 3 — openapi.yaml 재생성

API 표면(쿼리 파라미터 제약)이 바뀌므로 재생성한다(CLAUDE.md 규칙 4).
`docker compose up -d` → `./gradlew bootRun` → `curl -s localhost:8080/v3/api-docs.yaml -o docs/api/openapi.yaml`.
**diff에 공지 2개 경로의 `minimum`/`maximum`·description 추가만 있어야 한다** — 다른 경로가
함께 바뀌면 멈추고 원인을 확인한다.

## 검증 기준

- [ ] `./gradlew ktlintFormat` → `./gradlew cleanTest test` → `./gradlew build` 그린
- [ ] 신규 회귀 테스트 6건 통과
- [ ] `openapi.yaml` diff가 공지 2경로의 제약 추가로 한정
- [ ] 실제 앱으로 재확인: 관리자·회원 공지 목록에서 `size=0`·`page=-1`·`size=100000`이 **400**,
      `size=20`이 **200**

## 하지 않을 것

- 다른 목록 API 손대기 — 전수 스캔 결과 이미 전부 검증돼 있다
- `page` 상한 추가 — 깊은 OFFSET은 페이지 번호가 아니라 테이블 크기의 문제고, 임의의 상한을
  계약에 박으면 근거 없는 숫자가 남는다. 데이터가 커지면 커서 페이지네이션으로 다룬다
  (PR #20 2차 리뷰에서 같은 판단을 이미 기록함)
