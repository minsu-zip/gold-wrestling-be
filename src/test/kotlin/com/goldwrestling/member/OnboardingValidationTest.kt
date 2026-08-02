package com.goldwrestling.member

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 전화번호 형식 검증·정규화(policies §5.1 "전화번호 형식 검증", D-041) — 순수 Kotlin 단위테스트.
 * 스프링 컨텍스트 없이 [PhoneNumberNormalizer]만 검증한다(add-domain-test §1).
 */
class OnboardingValidationTest {
    @Test
    fun `010-1234-5678을 01012345678로 정규화한다`() {
        assertThat(PhoneNumberNormalizer.normalize("010-1234-5678")).isEqualTo("01012345678")
    }

    @Test
    fun `01012345678은 그대로 유지된다`() {
        assertThat(PhoneNumberNormalizer.normalize("01012345678")).isEqualTo("01012345678")
    }

    @Test
    fun `011-123-4567을 0111234567로 정규화한다`() {
        assertThat(PhoneNumberNormalizer.normalize("011-123-4567")).isEqualTo("0111234567")
    }

    @Test
    fun `PHONE_NUMBER_PATTERN이 하이픈 포함 형식을 통과시킨다`() {
        assertThat(matches("010-1234-5678")).isTrue()
    }

    @Test
    fun `PHONE_NUMBER_PATTERN이 하이픈 없는 형식을 통과시킨다`() {
        assertThat(matches("01012345678")).isTrue()
    }

    @Test
    fun `PHONE_NUMBER_PATTERN이 3자리 국번을 통과시킨다`() {
        assertThat(matches("011-123-4567")).isTrue()
    }

    @Test
    fun `PHONE_NUMBER_PATTERN이 지역번호를 거부한다`() {
        assertThat(matches("02-1234-5678")).isFalse()
    }

    @Test
    fun `PHONE_NUMBER_PATTERN이 공백이 섞인 입력을 거부한다`() {
        assertThat(matches("0101234567 8")).isFalse()
    }

    @Test
    fun `PHONE_NUMBER_PATTERN이 자릿수가 부족한 입력을 거부한다`() {
        assertThat(matches("010-12-5678")).isFalse()
    }

    @Test
    fun `PHONE_NUMBER_PATTERN이 숫자가 아닌 입력을 거부한다`() {
        assertThat(matches("abcdefghijk")).isFalse()
    }

    @Test
    fun `PHONE_NUMBER_PATTERN이 빈 문자열을 거부한다`() {
        assertThat(matches("")).isFalse()
    }

    private fun matches(input: String): Boolean = Regex(PhoneNumberNormalizer.PHONE_NUMBER_PATTERN).matches(input)
}
