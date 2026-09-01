---
quick_id: 260820-cp1
status: complete
phase: quick/260820-cp1
plan: 01
subsystem: api/notice
tags: [validation, springdoc, openapi, notice, D-01]
requires: [conventions§8, D-054]
provides:
  - "NoticeSearchCondition(@Min/@Max page·size) — 공지 목록 2경로 공통 조건 객체"
  - "GET /api/admin/notices, GET /api/members/notices의 page/size 400 검증"
affects:
  - "docs/api/openapi.yaml (공지 2경로 쿼리 파라미터 제약)"
tech-stack:
  added: []
  patterns:
    - "@ParameterObject @ModelAttribute @Valid 조건 객체로 쿼리 파라미터 검증 (NotificationSearchCondition·ActivityFeedSearchCondition과 동일 관례)"
key-files:
  created:
    - src/main/kotlin/com/goldwrestling/notice/dto/NoticeSearchCondition.kt
  modified:
    - src/main/kotlin/com/goldwrestling/notice/AdminNoticeController.kt
    - src/main/kotlin/com/goldwrestling/notice/MemberNoticeController.kt
    - src/main/kotlin/com/goldwrestling/notice/NoticeService.kt
    - src/test/kotlin/com/goldwrestling/notice/NoticeServiceTest.kt
    - src/test/kotlin/com/goldwrestling/notice/AdminNoticeControllerTest.kt
    - src/test/kotlin/com/goldwrestling/notice/MemberNoticeControllerTest.kt
    - docs/api/openapi.yaml
decisions:
  - "다른 페이지네이션 API 전수 스캔 결과 공지 2곳만 예외였다 — 나머지는 손대지 않는다(플랜 '하지 않을 것')"
  - "page 상한은 추가하지 않는다 — 임의의 숫자를 계약에 박지 않고, 데이터가 커지면 커서 페이지네이션으로 다룬다(PR #20 2차 리뷰 판단 재확인)"
metrics:
  tasks: 3
  commits: 3
  completed: 2026-08-20
---

# Quick Task 260820-cp1: 공지 목록 API page/size 검증 추가(D-01) Summary

`GET /api/admin/notices`·`GET /api/members/notices`가 검증 없는 `@RequestParam Int`로 `page`/`size`를
받아 `size=0`·`page=-1`에서 500이 나던 결함을, `NotificationSearchCondition` 선례를 그대로 따른
`NoticeSearchCondition`(`@Min`/`@Max`)으로 고쳤다.

## 실행 결과

| Task | 내용 | 커밋 |
|------|------|------|
| 1 | `NoticeSearchCondition` 신설 + 관리자·회원 컨트롤러/서비스 배선 | `a8e2f26` |
| 2 | 회귀 테스트 6건(관리자 3 + 회원 3: size=0·page=-1·size=101) | `601ff4d` |
| 3 | 앱 기동 후 openapi.yaml 재생성 | `46cd949` |

푸시는 오케스트레이터가 담당한다(사용자 지시대로 여기서는 커밋까지만).

## 검증

- `./gradlew ktlintFormat` → 변경 없음(이미 규약 준수)
- `./gradlew cleanTest test` → **총 857건, 실패 0건** (notice 패키지만 21건: Admin 7 + Member 5 + Repository 2 + Service 7)
- `./gradlew build` → BUILD SUCCESSFUL
- `openapi.yaml` diff: 관리자·회원 공지 2경로(`/api/admin/notices`, `/api/members/notices`)의
  `page`(`minimum: 0`)·`size`(`minimum: 1`, `maximum: 100`)와 `description` 추가뿐 — 다른 경로는
  변경 없음(`git diff --stat` 1 file changed, 14 insertions만)

### 실제 앱 재확인 (관리자 경로)

`POST /api/auth/admin/login`(`.env`의 `ADMIN_SEED_LOGIN_ID`/`ADMIN_SEED_PASSWORD`)으로 토큰 발급 후
`GET /api/admin/notices`를 직접 호출했다.

| 요청 | 결과 |
|---|---|
| `?size=0` | **400** |
| `?page=-1` | **400** |
| `?size=100000` | **400** |
| `?size=20` | **200** |

**회원 경로(`/api/members/notices`)는 실제 앱으로 재확인하지 못했다** — 회원 로그인이 카카오
OAuth 흐름이라 시드 계정으로 토큰을 즉시 발급할 방법이 없다. 대신 `MemberNoticeControllerTest`의
신규 회귀 테스트 3건(size=0·page=-1·size=101 모두 400)이 통합테스트(Testcontainers, 실제 MockMvc
요청)로 이 경로를 검증한다 — 컨트롤러·서비스 배선이 관리자 경로와 완전히 동일한 `NoticeSearchCondition`을
공유하므로 회귀 위험은 낮다고 판단했다.

## 계획과 달라진 점

### 1. [Rule 3 - Blocking] `NoticeServiceTest`의 `getList` 호출부를 함께 고쳤다

- **Found during:** Task 1
- **Issue:** 플랜의 `files_modified`에는 없었지만, `NoticeService.getList(page, size)` →
  `getList(condition)` 시그니처 변경으로 `NoticeServiceTest`의 `service.getList(page = 0, size = 20)`
  호출이 컴파일되지 않았다.
- **Fix:** `NoticeServiceTest`에 `NoticeSearchCondition` import를 추가하고 호출부를
  `service.getList(NoticeSearchCondition(page = 0, size = 20))`로 바꿨다. 테스트 의도(최신순 페이지를
  `PageResponse`로 변환)는 그대로다.
- **Files modified:** `src/test/kotlin/com/goldwrestling/notice/NoticeServiceTest.kt`
- **Verification:** `./gradlew compileKotlin compileTestKotlin` 통과, `NoticeServiceTest` 7건 전부 그린
- **Committed in:** `a8e2f26` (Task 1 커밋)

---

**Total deviations:** 1 auto-fixed (Rule 3 — blocking)
**Impact on plan:** 시그니처 변경의 직접적 파급이라 범위를 벗어나지 않았다. 스코프 크리프 없음.

## 이번에 쓴 기술

1. **Bean Validation(JSR 380) 애노테이션(`@Min`/`@Max`) ★**
   - **이 코드에서 왜 필요했는가:** `page`/`size`가 순수 `Int`로 컨트롤러에 들어오면 Spring이 타입만
     확인하고 값의 범위는 검사하지 않는다. `@field:Min(0)`·`@field:Max(100)`을 DTO 필드에 붙이면
     `@Valid`가 걸린 시점에 Spring이 자동으로 이 규칙을 검사해 위반 시 예외를 던진다 — `if (size < 1)`
     같은 수동 분기를 컨트롤러·서비스 어디에도 쓰지 않아도 된다.
   - **안 썼으면 뭐가 깨지는가:** 계속 `PageRequest.of(page, 0)`처럼 잘못된 값이 그대로 JPA까지
     내려가 `IllegalArgumentException` → 포괄 예외 핸들러 → 500이 된다. 클라이언트 입력 실수가
     "서버 장애"로 보고돼 운영 알림이 울린다.

2. **`@ParameterObject @ModelAttribute` — 쿼리 파라미터를 객체로 바인딩 ★**
   - **이 코드에서 왜 필요했는가:** `@RequestParam Int page, @RequestParam Int size` 두 파라미터
     각각에는 `@Valid`를 붙일 수 없다(Bean Validation은 객체의 필드에 붙는 애노테이션 검사라서, 메서드
     파라미터 단독으로는 `@Validated` 클래스 레벨 방식이 따로 필요하고 에러 형식도 달라진다). 이미
     `NotificationSearchCondition`·`ActivityFeedSearchCondition`이 같은 방식(조건 객체 +
     `@ModelAttribute @Valid`)을 쓰고 있어 관례를 그대로 따랐다. `@ParameterObject`는 springdoc이
     이 객체의 필드를 "요청 바디"가 아니라 "쿼리 파라미터 목록"으로 문서화하게 하는 애노테이션이다.
   - **안 썼으면 뭐가 깨지는가:** `@RequestParam` 그대로 두면 검증 자체가 안 걸리고(위 항목), 억지로
     `@Validated` 방식을 쓰면 이 프로젝트의 다른 8개 목록 API와 검증 실패 시 에러 응답 모양이 달라져
     conventions §8("에러 응답은 RFC 9457 ProblemDetail 고정")의 일관성이 깨진다.

3. **springdoc-openapi 런타임 스캔으로 `openapi.yaml` 재생성**
   - **이 코드에서 왜 필요했는가:** `@field:Min`·`@field:Max`·`@field:Schema`를 코드에 추가해도,
     FE가 실제로 보는 계약 파일(`docs/api/openapi.yaml`)은 자동으로 갱신되지 않는다. springdoc은
     애플리케이션이 **떠 있는 상태**에서 컨트롤러·DTO를 리플렉션으로 스캔해 `/v3/api-docs.yaml`을
     즉석에서 만들어 낸다 — 정적 분석이 아니라 런타임 스캔이라, 이 파일을 갱신하려면 반드시
     `bootRun`으로 앱을 띄운 뒤 그 엔드포인트를 호출해야 한다.
   - **안 썼으면 뭐가 깨지는가:** FE가 여전히 옛 스펙(제약 없는 `page`/`size`)으로 타입을 생성해,
     백엔드는 400을 내는데 FE 타입은 "항상 성공"으로 가정한 채 어긋난다.

## 하지 않은 것 (의도적)

- `docs/decisions.md`에 별도 결정 기록을 추가하지 않았다 — 이 작업은 D-01(전수 스캔 결과)의
  실행일 뿐, 새로운 아키텍처 판단이 아니다. `page` 상한을 넣지 않기로 한 판단은 이미 PR #20
  2차 리뷰에서 기록되어 있어 중복 기록하지 않았다.

## Next Phase Readiness

공지 페이지네이션 API 2곳이 이 프로젝트의 다른 모든 목록 API와 동일한 검증 관례로 통일됐다.
후속 phase에서 새 목록 API를 추가할 때도 이 조건 객체 패턴(`@field:Min`/`@field:Max` +
`@ParameterObject @ModelAttribute @Valid`)을 그대로 따르면 된다.

---
*Quick Task: 260820-cp1-api-page-size*
*Completed: 2026-08-20*
