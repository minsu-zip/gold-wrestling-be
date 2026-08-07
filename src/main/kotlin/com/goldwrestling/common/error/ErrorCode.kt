package com.goldwrestling.common.error

import org.springframework.http.HttpStatus

/**
 * 이 프로젝트의 모든 에러 응답에 붙는 `code` 필드 값이다 (D-028).
 *
 * **FE 분기는 이 값(enum `name`)으로만 한다.** `ProblemDetail.type` URI는 형식만 갖춘 값이며 분기 키가 아니다.
 *
 * 공통 7개는 스프링 내장 예외(404/405/400/406/415)와 예상하지 못한 서버 오류(500)에 대응하고,
 * 인증·회원 10개(Phase 2)는 인증·인가·회원 상태 실패에 대응한다. 도메인 코드(예: `RESERVATION_FULL`,
 * `INSUFFICIENT_PASS_COUNT`)는 해당 도메인이 생기는 이후 phase에서 이 enum에 추가하고, 같은 PR에서
 * `docs/error-codes.md` 표도 함께 갱신한다.
 *
 * [defaultStatus]로 코드↔HTTP 상태 대응을 코드 안에 고정해, 문서(`docs/error-codes.md`)와 코드가 갈라지는 것을 막는다.
 */
enum class ErrorCode(
    val defaultStatus: HttpStatus,
) {
    /** 요청 값 형식 검증 실패 (`@Valid`, 파라미터 제약) */
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),

    /** 본문 파싱 실패, 타입 불일치, 필수 파라미터 누락 */
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),

    /** 매핑되지 않은 경로 또는 대상 리소스 없음 */
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** 해당 경로가 지원하지 않는 HTTP 메서드 */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    /** 지원하지 않는 Content-Type */
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    /** Accept 헤더가 요구하는 미디어 타입으로 응답을 만들 수 없음 */
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE),

    /** 예상하지 못한 서버 오류 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    /** 인증이 필요하거나 access 토큰이 없음/만료/위조 */
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),

    /** 인증은 됐으나 역할(ROLE_MEMBER/ROLE_ADMIN)이 부족 */
    ACCESS_DENIED(HttpStatus.FORBIDDEN),

    /** 관리자 loginId/비밀번호 불일치 (어느 쪽이 틀렸는지 구분해 노출하지 않는다) */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),

    /** refresh 토큰이 없음/만료/폐기됨/재사용 감지됨 */
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED),

    /** 카카오가 인가 코드 교환 또는 사용자 조회를 거부함 */
    KAKAO_AUTH_FAILED(HttpStatus.UNAUTHORIZED),

    /** 카카오 API가 응답하지 않거나 5xx를 반환 */
    KAKAO_UNAVAILABLE(HttpStatus.BAD_GATEWAY),

    /** 대상 회원 없음 */
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** 상태 게이트 위반. 회원 상태가 요구 조건(`ACTIVE`)이 아님 */
    MEMBER_NOT_ACTIVE(HttpStatus.FORBIDDEN),

    /** 이미 온보딩을 마친 회원의 온보딩 재제출 (프로필 수정은 v2 PROF-01) */
    ONBOARDING_ALREADY_COMPLETED(HttpStatus.CONFLICT),

    /** 승인 대상이 아니거나 허용되지 않는 상태 전이 */
    MEMBER_STATE_CONFLICT(HttpStatus.CONFLICT),

    /** 대상 이용권 없음 */
    PASS_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** 가감 수량이 0.5 단위가 아니거나 0 (policies §4.2a) */
    INVALID_ADJUSTMENT_UNIT(HttpStatus.BAD_REQUEST),

    /** 가감 결과 잔여가 음수가 됨 */
    INSUFFICIENT_PASS_COUNT(HttpStatus.CONFLICT),

    /** 기간제(`EVENING_MEMBERSHIP`)에 횟수 가감 시도 */
    PASS_TYPE_NOT_ADJUSTABLE(HttpStatus.CONFLICT),

    /** 이미 취소된 이용권을 가감·기간수정·재취소하려 함 */
    PASS_ALREADY_CANCELED(HttpStatus.CONFLICT),

    /** 종료일이 시작일보다 앞서거나, 횟수권 시작일 수정 시도 (D-062) */
    INVALID_PASS_PERIOD(HttpStatus.BAD_REQUEST),

    /** 조건부 갱신 경쟁 패배 등 위 코드로 나뉘지 않는 이용권 상태 충돌 */
    PASS_STATE_CONFLICT(HttpStatus.CONFLICT),

    /** 요청한 정기 시간표 행이 없음 */
    CLASS_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** 휴강된 수업에 예약·변경 시도 (policies §7) */
    CLASS_SESSION_CANCELED(HttpStatus.CONFLICT),

    /** 휴강 상태가 아닌 수업에 휴강 해제 시도 */
    CLASS_SESSION_NOT_CANCELED(HttpStatus.CONFLICT),

    /** 예약 대상이 아닌 수업 종류(`EVENING`) 예약 시도 (D-093) */
    CLASS_SESSION_NOT_RESERVABLE(HttpStatus.CONFLICT),

    /** 대상 예약 없음(타인 예약 조회 포함 — 존재 여부를 흘리지 않는다) */
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** 정원 초과 (RESV-06) */
    RESERVATION_CAPACITY_EXCEEDED(HttpStatus.CONFLICT),

    /** 예약 창 밖(다음 주 예약, 시작 시각 경과) (D-095) */
    RESERVATION_WINDOW_CLOSED(HttpStatus.CONFLICT),

    /** 같은 회원·같은 날짜·시각 중복 예약 (D-092) */
    DUPLICATE_RESERVATION(HttpStatus.CONFLICT),

    /** 당일 취소·변경 시도 (policies §3). 취소와 변경이 동일 규칙이라 코드를 하나로 둔다 — FE 분기가 나뉘지 않는다 */
    SAME_DAY_MODIFICATION_NOT_ALLOWED(HttpStatus.CONFLICT),

    /** 이미 취소된 예약에 재취소·변경 시도 */
    RESERVATION_ALREADY_CANCELED(HttpStatus.CONFLICT),

    /** 변경 시 수업 종류가 다름 (`SESSION`↔`LESSON` 교차, D-090) */
    RESERVATION_TYPE_MISMATCH(HttpStatus.BAD_REQUEST),

    /** 조건부 갱신 경쟁 패배 등 위 코드로 나뉘지 않는 예약 상태 충돌 */
    RESERVATION_STATE_CONFLICT(HttpStatus.CONFLICT),

    /** 활성 예약이 있어 등록 취소 거부 (D-089) */
    PASS_HAS_ACTIVE_RESERVATION(HttpStatus.CONFLICT),

    /** 요청한 branchId에 관리자가 소속되지 않음 (T-04-53, 관리자 스케줄 보드 최초 도입) */
    ADMIN_BRANCH_NOT_ASSIGNED(HttpStatus.FORBIDDEN),
}
