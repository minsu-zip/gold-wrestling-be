package com.goldwrestling.member

/**
 * 전화번호 형식 검증·저장 형식 통일(D-041).
 *
 * 온보딩 요청은 `010-1234-5678`·`01012345678` 두 형태를 모두 받지만, 저장은 항상 하이픈을 제거한
 * 숫자만으로 한다. 저장 형식이 섞이면 "전화번호 부분 일치 검색"(D-035)이 입력 형태에 따라 결과가
 * 달라지기 때문이다.
 *
 * [PHONE_NUMBER_PATTERN]은 `object`로 두어 상태가 없다는 것을 드러낸다. 이 상수는
 * `dto.OnboardingRequest`의 `@field:Pattern`과 **공유**한다 — 정규식이 두 곳에 복사되면 한쪽만
 * 고쳐지는 사고가 난다.
 */
object PhoneNumberNormalizer {
    /**
     * 010/011/016/017/018/019로 시작하는 국내 휴대폰 번호. 국번 3~4자리 + 뒷자리 4자리, 하이픈은
     * 있어도 없어도 통과한다. 지역번호(02 등)·자릿수 부족·공백 포함 입력은 거부한다.
     */
    const val PHONE_NUMBER_PATTERN = "^01[016789]-?\\d{3,4}-?\\d{4}$"

    /** 앞뒤 공백을 제거하고 하이픈을 모두 없앤 숫자만 남긴다. */
    fun normalize(raw: String): String = raw.trim().replace("-", "")
}
