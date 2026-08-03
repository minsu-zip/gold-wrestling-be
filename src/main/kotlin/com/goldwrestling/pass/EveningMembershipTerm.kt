package com.goldwrestling.pass

/**
 * 저녁반 회비 기간 단위 (glossary.md "저녁반 회비 기간 단위"). 등록 요청의 닫힌 집합이며,
 * **DB에 저장하지 않는다**(D-063) — 등록 시 `end_date` 계산 입력으로만 쓰인다. 이후 기간 수정은
 * 날짜 직접 지정(`PassPeriodChange`, D-057)이라 개월 수를 저장하면 수정 후 실제 기간과 어긋난다.
 */
enum class EveningMembershipTerm(
    val months: Long,
) {
    ONE_MONTH(1L),
    THREE_MONTHS(3L),
    SIX_MONTHS(6L),
}
