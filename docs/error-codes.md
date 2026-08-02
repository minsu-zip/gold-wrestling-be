# 에러코드 레지스트리 (error-codes.md)

> 이 표가 **FE 분기의 유일한 계약**이다. FE는 `code` 필드 값으로만 에러를 분기하고, `type`/`title`/`detail`은 사람이 읽는 보조 정보로만 취급한다.
> 새 에러코드를 추가하면 **같은 PR에서 이 표에 행을 추가한다** (`src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt`의 enum과 항상 1:1로 맞춘다).

## 공통 코드 (Phase 1)

| 코드 | HTTP 상태 | 의미 | 발생 지점 |
|---|---|---|---|
| `VALIDATION_FAILED` | 400 | 요청 값 형식 검증 실패 (`@Valid`, 파라미터 제약) | `MethodArgumentNotValidException`, `HandlerMethodValidationException` |
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

**폴백 규칙** — 위 표에 매핑되지 않은 예외는 상태값으로 코드를 추측하지 않고 다음으로 고정된다:
4xx → `MALFORMED_REQUEST`, 그 외 → `INTERNAL_ERROR`. (이때 HTTP 상태는 예외가 정한 값이 그대로 나가므로,
FE가 특정 코드로 구분해야 하는 에러가 생기면 이 표와 핸들러에 명시 매핑을 추가한다.)

도메인 코드(예: `RESERVATION_FULL`, `INSUFFICIENT_PASS_COUNT`)는 해당 도메인이 생기는 이후 phase에서 이 표에 추가된다.
