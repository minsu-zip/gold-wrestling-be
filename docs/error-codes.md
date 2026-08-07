# 에러코드 레지스트리 (error-codes.md)

> 이 표가 **FE 분기의 유일한 계약**이다. FE는 `code` 필드 값으로만 에러를 분기하고, `type`/`title`/`detail`은 사람이 읽는 보조 정보로만 취급한다.
> 새 에러코드를 추가하면 **같은 PR에서 이 표에 행을 추가한다** (`src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt`의 enum과 항상 1:1로 맞춘다).

## 공통 코드 (Phase 1)

| 코드 | HTTP 상태 | 의미 | 발생 지점 |
|---|---|---|---|
| `VALIDATION_FAILED` | 400 | 요청 값 형식 검증 실패 (`@Valid`, 파라미터 제약, 타입 조건부 필수) | `MethodArgumentNotValidException`, `HandlerMethodValidationException`, `MissingInitialCountException`(`Pass.register` — 횟수권 초기 횟수 누락, `@Valid`로 표현 불가한 조건부 필수) |
| `MALFORMED_REQUEST` | 400 | 본문 파싱 실패, 타입 불일치, 필수 파라미터·헤더 누락 | `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, `ServletRequestBindingException`(필수 헤더 누락 등) |
| `RESOURCE_NOT_FOUND` | 404 | 매핑되지 않은 경로 또는 대상 리소스 없음 | `NoResourceFoundException`, `NoHandlerFoundException` |
| `METHOD_NOT_ALLOWED` | 405 | 해당 경로가 지원하지 않는 HTTP 메서드 | `HttpRequestMethodNotSupportedException` |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 지원하지 않는 Content-Type | `HttpMediaTypeNotSupportedException` |
| `NOT_ACCEPTABLE` | 406 | Accept 헤더가 요구하는 미디어 타입으로 응답을 만들 수 없음 | `HttpMediaTypeNotAcceptableException` |
| `INTERNAL_ERROR` | 500 | 예상하지 못한 서버 오류 (응답 본문에 원인 노출 없음, 서버 로그에만 기록) | 포괄 `Exception` 핸들러 |

## 인증·회원 코드 (Phase 2)

| 코드 | HTTP 상태 | 의미 | 발생 지점 |
|---|---|---|---|
| `UNAUTHENTICATED` | 401 | 인증이 필요하거나 access 토큰이 없음/만료/위조 | `ProblemDetailAuthenticationEntryPoint` |
| `ACCESS_DENIED` | 403 | 인증은 됐으나 역할(ROLE_MEMBER/ROLE_ADMIN)이 부족 | `ProblemDetailAccessDeniedHandler` |
| `INVALID_CREDENTIALS` | 401 | 관리자 loginId/비밀번호 불일치 (어느 쪽이 틀렸는지 구분해 노출하지 않는다) | `AdminAuthService` |
| `REFRESH_TOKEN_INVALID` | 401 | refresh 토큰이 없음/만료/폐기됨/재사용 감지됨 | `TokenService` |
| `KAKAO_AUTH_FAILED` | 401 | 카카오가 인가 코드 교환 또는 사용자 조회를 거부함 | `KakaoApiClient` |
| `KAKAO_UNAVAILABLE` | 502 | 카카오 API가 응답하지 않거나 5xx를 반환 | `KakaoApiClient` |
| `MEMBER_NOT_FOUND` | 404 | 대상 회원 없음 | `AdminMemberService` |
| `MEMBER_NOT_ACTIVE` | 403 | 상태 게이트 위반. 회원 상태가 요구 조건(`ACTIVE`)이 아님 | `MemberStateGate` |
| `ONBOARDING_ALREADY_COMPLETED` | 409 | 이미 온보딩을 마친 회원의 온보딩 재제출 (프로필 수정은 v2 PROF-01) | `MemberProfileService` |
| `MEMBER_STATE_CONFLICT` | 409 | 승인 대상이 아니거나 허용되지 않는 상태 전이 | `AdminMemberService` |

## 이용권 코드 (Phase 3)

| 코드 | HTTP 상태 | 의미 | 발생 지점 |
|---|---|---|---|
| `PASS_NOT_FOUND` | 404 | 대상 이용권 없음 | `AdminPassService` |
| `INVALID_ADJUSTMENT_UNIT` | 400 | 가감 수량이 0.5 단위가 아니거나 0 (policies §4.2a) | `AdminPassService` |
| `INSUFFICIENT_PASS_COUNT` | 409 | 가감 결과 잔여가 음수가 됨 | `Pass` |
| `PASS_TYPE_NOT_ADJUSTABLE` | 409 | 기간제(`EVENING_MEMBERSHIP`)에 횟수 가감 시도 | `Pass` |
| `PASS_ALREADY_CANCELED` | 409 | 이미 취소된 이용권을 가감·기간수정·재취소하려 함 | `Pass` |
| `INVALID_PASS_PERIOD` | 400 | 종료일이 시작일보다 앞서거나, 횟수권 시작일 수정 시도 (D-062) | `AdminPassService` |
| `PASS_STATE_CONFLICT` | 409 | 조건부 갱신 경쟁 패배 등 위 코드로 나뉘지 않는 이용권 상태 충돌 | `AdminPassService` |

## 시간표·예약 코드 (Phase 4)

| 코드 | HTTP 상태 | 의미 | 발생 지점 |
|---|---|---|---|
| `CLASS_SCHEDULE_NOT_FOUND` | 404 | 요청한 정기 시간표 행이 없음 | `AdminScheduleService` |
| `CLASS_SESSION_CANCELED` | 409 | 휴강된 수업에 예약·변경 시도 (policies §7) | `ClassSession` |
| `CLASS_SESSION_NOT_CANCELED` | 409 | 휴강 상태가 아닌 수업에 휴강 해제 시도 | `ClassSession` |
| `CLASS_SESSION_NOT_RESERVABLE` | 409 | 예약 대상이 아닌 수업 종류(`EVENING`) 예약 시도 (D-093) | `MemberReservationService` |
| `RESERVATION_NOT_FOUND` | 404 | 대상 예약 없음. 소유자가 아닌 예약 접근도 403이 아니라 이 코드(404)로 응답한다 — "그 id의 예약이 존재한다"는 사실 자체를 노출하지 않기 위해서다 | `MemberReservationService`, `AdminReservationService` |
| `RESERVATION_CAPACITY_EXCEEDED` | 409 | 정원 초과 (RESV-06) | `Reservation` |
| `RESERVATION_WINDOW_CLOSED` | 409 | 예약 창 밖(다음 주 예약, 시작 시각 경과) (D-095) | `MemberReservationService` |
| `DUPLICATE_RESERVATION` | 409 | 같은 회원·같은 날짜·시각 중복 예약 (D-092) | `MemberReservationService` |
| `SAME_DAY_MODIFICATION_NOT_ALLOWED` | 409 | 당일 취소·변경 시도 (policies §3) | `Reservation` |
| `RESERVATION_ALREADY_CANCELED` | 409 | 이미 취소된 예약에 재취소·변경 시도 | `Reservation` |
| `RESERVATION_TYPE_MISMATCH` | 400 | 변경 시 수업 종류가 다름 (`SESSION`↔`LESSON` 교차, D-090) | `MemberReservationService` |
| `RESERVATION_STATE_CONFLICT` | 409 | 조건부 갱신 경쟁 패배 등 위 코드로 나뉘지 않는 예약 상태 충돌 | `AdminReservationService` |
| `PASS_HAS_ACTIVE_RESERVATION` | 409 | 대상 이용권으로 잡힌 활성 예약이 있어 등록 취소 거부 (D-089) | `AdminPassService` |

> **연결 상태 (Phase 4 진행 중에만 유효한 안내 — phase 완료 시 이 문단을 삭제한다)**
> 위 표의 "발생 지점"은 **그 코드를 던지도록 계획된 위치**이며, 전부가 이미 연결된 것은 아니다.
> 청크 A(04-01~04-05) 시점에 실제로 던져지는 코드는 `CLASS_SESSION_CANCELED`·`CLASS_SESSION_NOT_CANCELED`·
> `CLASS_SESSION_NOT_RESERVABLE`·`RESERVATION_WINDOW_CLOSED` 4개뿐이고, 나머지 9개는 예외 클래스만 선언된
> 상태다. 예약 API(04-07·04-08·04-10)와 관리자 운영(04-11~04-14)에서 연결된다.
> 특히 `PASS_HAS_ACTIVE_RESERVATION`(D-089)은 **04-13**이 `AdminPassService.cancel`에
> `ReservationRepository.existsByPassIdAndStatus` 선행 검사를 붙일 때 연결된다 — 지금 이 문서만 보고
> "D-089는 이미 구현됐다"고 판단하면 안 된다.

**폴백 규칙** — 위 표에 매핑되지 않은 예외는 상태값으로 코드를 추측하지 않고 다음으로 고정된다:
4xx → `MALFORMED_REQUEST`, 그 외 → `INTERNAL_ERROR`. (이때 HTTP 상태는 예외가 정한 값이 그대로 나가므로,
FE가 특정 코드로 구분해야 하는 에러가 생기면 이 표와 핸들러에 명시 매핑을 추가한다.)
