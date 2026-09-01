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
| `CLASS_SCHEDULE_NOT_FOUND` | 404 | 요청한 정기 시간표 행이 없음, **또는 시간표 요일과 수업 날짜의 요일이 어긋난 조합**(D-146 — 존재하지 않는 (시간표, 날짜) 조합. 존재 여부를 흘리지 않으려 403이 아니라 404로 통일) | `ClassSessionService`(요일 검증 — 전 쓰기 경로 공통), `AdminScheduleService`, `MemberReservationService`, `AdminReservationService`, `AttendanceService` |
| `CLASS_SESSION_NOT_FOUND` | 404 | 요청한 날짜별 수업(ClassSession) 행이 없음 (휴강 해제 대상 조회, RESV-09) | `AdminScheduleService` |
| `CLASS_SESSION_CANCELED` | 409 | 휴강된 수업에 예약·변경 시도, 또는 이미 휴강인 수업에 재휴강 시도 (policies §7) | `ClassSession`(판정), `AdminScheduleService`(휴강 처리 CAS 경쟁 패배) |
| `CLASS_SESSION_NOT_CANCELED` | 409 | 휴강 상태가 아닌 수업에 휴강 해제 시도 | `ClassSession`(판정), `AdminScheduleService`(휴강 해제 CAS 경쟁 패배) |
| `CLASS_SESSION_NOT_RESERVABLE` | 409 | 예약 대상이 아닌 수업 종류(`EVENING`) 예약 시도 (D-093) | `ClassSession`, `ReservationPassPolicy` |
| `RESERVATION_NOT_FOUND` | 404 | 대상 예약 없음. 소유자가 아닌 예약 접근도 403이 아니라 이 코드(404)로 응답한다 — "그 id의 예약이 존재한다"는 사실 자체를 노출하지 않기 위해서다 | `MemberReservationService`, `AdminReservationService` |
| `RESERVATION_CAPACITY_EXCEEDED` | 409 | 정원 초과 (RESV-06) | `ReservationLedgerSupport` |
| `RESERVATION_WINDOW_CLOSED` | 409 | 예약 창 밖(다음 주 예약, 시작 시각 경과) (D-095) | `ReservationWindow` |
| `DUPLICATE_RESERVATION` | 409 | 같은 회원·같은 날짜·시각 중복 예약 (D-092) | `ReservationLedgerSupport` |
| `SAME_DAY_MODIFICATION_NOT_ALLOWED` | 409 | 당일 취소·변경 시도 (policies §3) | `Reservation` |
| `RESERVATION_ALREADY_CANCELED` | 409 | 이미 취소된 예약에 재취소·변경 시도, 또는 취소 CAS 경쟁 패배 | `Reservation`(판정), `MemberReservationService`·`AdminReservationService`(CAS 경쟁 패배) |
| `RESERVATION_TYPE_MISMATCH` | 400 | 변경 시 수업 종류가 다름 (`SESSION`↔`LESSON` 교차, D-090) | `Reservation` |
| `RESERVATION_STATE_CONFLICT` | 409 | 조건부 갱신 경쟁 패배 등 위 코드로 나뉘지 않는 예약 상태 충돌 — **예비 코드, 현재 실제로 던져지는 경로 없음**(모든 CAS 경쟁 실패가 더 구체적인 코드로 분류돼 있다) | — |
| `PASS_HAS_ACTIVE_RESERVATION` | 409 | 대상 이용권으로 잡힌 활성 예약이 있어 등록 취소 거부 (D-089) | `AdminPassService` |
| `ADMIN_BRANCH_NOT_ASSIGNED` | 403 | 요청한 `branchId`에 관리자가 소속되지 않음 (T-04-53) | `AdminScheduleService` |
| `INVALID_RESERVATION_SEARCH_RANGE` | 400 | 관리자 예약 검색 조건의 `from`이 `to`보다 뒤임 (RESV-07) | `AdminReservationService` |

## 배치 코드 (Phase 5)

| 코드 | HTTP 상태 | 의미 | 발생 지점 |
|---|---|---|---|
| `BATCH_ALREADY_RUNNING` | 409 | 이미 실행 중인 배치가 있어 새 실행을 거부 (CR-01, D-118). 관리자 더블클릭·재시도·cron 겹침은 예상된 상황이고 거부가 안전한 결과다 — 오류가 아니라 상태 충돌이다 | `BatchExecutionRecorder` |
| `BATCH_EXECUTION_NOT_FOUND` | 404 | 요청한 배치 실행 이력이 없음 (WR-05). 진행 상태 조회(`GET /api/admin/batch/inactivity-runs/{id}`)를 잘못된 id로 호출한 경우 — 응답 문구에 id를 담지 않는다(존재 여부 탐색 방지, conventions §8) | `AdminBatchService` |

## 운영 코드 (Phase 6)

| 코드 | HTTP 상태 | 의미 | 발생 지점 |
|---|---|---|---|
| `ATTENDANCE_NOT_FOUND` | 404 | 대상 출석 기록 없음(삭제·수정 대상) | `AttendanceService` |
| `ATTENDANCE_MEMBER_NOT_RESERVED` | 409 | 예약제/1:1 출석 체크 대상이 그 세션의 활성 예약자가 아님(D-127 "예약자 명단에 대해 체크") | `AttendanceService` |
| `ATTENDANCE_CLASS_TYPE_MISMATCH` | 409 | 저녁반 전용 API를 `SESSION`/`LESSON` 세션에 호출했거나, 예약자 체크 API를 `EVENING` 세션에 호출함 | `AttendanceService`(예약자 체크 API 오용), `EveningHalfDeductionPolicy.requireEveningSession`(저녁반 API 오용) |
| `DUPLICATE_ATTENDANCE` | 409 | 같은 회원·같은 세션에 출석 기록이 이미 존재(회원×세션 유니크 위반) | `AttendanceService` |
| `EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE` | 409 | 유효한 저녁반 회비도 없고 `SESSION_PASS` 잔여도 0.5 미만이라 저녁반 출석 추가를 거부(D-128·D-133) | `EveningHalfDeductionPolicy.selectCandidate`(차감 후보 자체가 없음), `AttendanceService`(후보는 있었으나 조건부 UPDATE 경쟁 패배) |
| `NOTICE_NOT_FOUND` | 404 | 대상 공지 없음 | `NoticeService` |

**폴백 규칙** — 위 표에 매핑되지 않은 예외는 상태값으로 코드를 추측하지 않고 다음으로 고정된다:
4xx → `MALFORMED_REQUEST`, 그 외 → `INTERNAL_ERROR`. (이때 HTTP 상태는 예외가 정한 값이 그대로 나가므로,
FE가 특정 코드로 구분해야 하는 에러가 생기면 이 표와 핸들러에 명시 매핑을 추가한다.)
