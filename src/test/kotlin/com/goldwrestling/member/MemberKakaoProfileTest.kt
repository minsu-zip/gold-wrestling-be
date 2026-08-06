package com.goldwrestling.member

import com.goldwrestling.branch.Branch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

/**
 * [Member.applyKakaoProfile]의 반영 규칙(D-083)을 검증하는 순수 단위테스트다.
 * 스프링 컨텍스트·DB 없이 엔티티만으로 판정할 수 있는 로직이므로 통합테스트로 올리지 않는다
 * (conventions §10.1 — 도메인 로직은 단위테스트 필수).
 */
class MemberKakaoProfileTest {
    @Test
    fun `카카오 프로필이 없던 회원에게 닉네임과 이미지가 오면 두 값이 설정된다`() {
        // given
        val member = member()

        // when
        member.applyKakaoProfile("골드레슬러", "https://k.kakaocdn.test/profile.jpg")

        // then
        assertThat(member.kakaoNickname).isEqualTo("골드레슬러")
        assertThat(member.kakaoProfileImageUrl).isEqualTo("https://k.kakaocdn.test/profile.jpg")
    }

    @Test
    fun `이미 값이 있는 회원에게 null이 오면 두 값이 null로 덮어써진다`() {
        // given — 동의 철회·동의항목 미추가로 카카오가 값을 주지 않는 상황
        val member = member(nickname = "예전닉", profileImageUrl = "https://k.kakaocdn.test/old.jpg")

        // when
        member.applyKakaoProfile(null, null)

        // then
        assertThat(member.kakaoNickname).isNull()
        assertThat(member.kakaoProfileImageUrl).isNull()
    }

    @Test
    fun `이미 값이 있는 회원에게 다른 닉네임이 오면 새 값으로 갱신된다`() {
        // given
        val member = member(nickname = "예전닉", profileImageUrl = "https://k.kakaocdn.test/old.jpg")

        // when
        member.applyKakaoProfile("새닉네임", "https://k.kakaocdn.test/new.jpg")

        // then
        assertThat(member.kakaoNickname).isEqualTo("새닉네임")
        assertThat(member.kakaoProfileImageUrl).isEqualTo("https://k.kakaocdn.test/new.jpg")
    }

    @Test
    fun `닉네임이 빈 문자열이거나 공백뿐이면 null로 정규화된다`() {
        // given
        val member = member(nickname = "예전닉")

        // when
        member.applyKakaoProfile("   ", "https://k.kakaocdn.test/profile.jpg")

        // then
        assertThat(member.kakaoNickname).isNull()
        assertThat(member.kakaoProfileImageUrl).isEqualTo("https://k.kakaocdn.test/profile.jpg")
    }

    @Test
    fun `프로필 이미지 URL이 빈 문자열이면 null로 정규화된다`() {
        // given
        val member = member(profileImageUrl = "https://k.kakaocdn.test/old.jpg")

        // when
        member.applyKakaoProfile("골드레슬러", "")

        // then
        assertThat(member.kakaoNickname).isEqualTo("골드레슬러")
        assertThat(member.kakaoProfileImageUrl).isNull()
    }

    private fun member(
        nickname: String? = null,
        profileImageUrl: String? = null,
    ): Member =
        Member(
            branch = Branch(name = "송파점"),
            name = null,
            phoneNumber = null,
            status = MemberStatus.PENDING,
            kakaoId = 1L,
            rejectionReason = null,
            createdAt = OffsetDateTime.parse("2026-08-06T00:00:00+09:00"),
            kakaoNickname = nickname,
            kakaoProfileImageUrl = profileImageUrl,
        )
}
