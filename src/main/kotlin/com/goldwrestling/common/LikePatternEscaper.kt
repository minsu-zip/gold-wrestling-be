package com.goldwrestling.common

/**
 * SQL LIKE 패턴에서 와일드카드(`%`·`_`)를 이스케이프하는 공용 유틸.
 *
 * 원래 `member.MemberSpecifications`에만 있었으나, `reservation.ReservationSpecifications`의
 * 회원 검색어 조건(`memberKeywordContains`, 04-10)이 같은 로직을 필요로 하면서 **두 번째 기능
 * 패키지가 실제로 쓰게 되어** `common`으로 승격했다(conventions §1 "두 기능 이상이 실제로 쓰기
 * 전에는 넣지 않는다"의 반대 경우 — `member.dto.PageResponse` KDoc이 예고한 바로 그 트리거).
 */
object LikePatternEscaper {
    /** LIKE 이스케이프 문자 — 호출부가 `criteriaBuilder.like(..., escapeChar)`에 그대로 넘긴다. */
    const val ESCAPE_CHAR: Char = '\\'

    /** `%`·`_`·이스케이프 문자 자체를 이스케이프해 LIKE 와일드카드로 해석되지 않게 한다. */
    fun escapeWildcards(value: String): String =
        value
            .replace("$ESCAPE_CHAR", "$ESCAPE_CHAR$ESCAPE_CHAR")
            .replace("%", "$ESCAPE_CHAR%")
            .replace("_", "${ESCAPE_CHAR}_")
}
