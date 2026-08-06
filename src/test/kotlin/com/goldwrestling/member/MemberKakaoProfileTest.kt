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

    @Test
    fun `닉네임이 컬럼 길이를 넘으면 잘라내지 않고 null로 취급한다`() {
        // given — 카카오 정책(20자 내외)을 벗어난 이상 응답. 잘라 저장하면 "틀린 닉네임"이 남는다
        val member = member(nickname = "예전닉")

        // when
        member.applyKakaoProfile("가".repeat(Member.MAX_KAKAO_NICKNAME_LENGTH + 1), "https://k.kakaocdn.test/p.jpg")

        // then — 컬럼 제약 위반으로 로그인 트랜잭션이 깨지는 대신, 값 없음(정상 상태)으로 떨어진다
        assertThat(member.kakaoNickname).isNull()
        assertThat(member.kakaoProfileImageUrl).isEqualTo("https://k.kakaocdn.test/p.jpg")
    }

    @Test
    fun `프로필 이미지 URL이 컬럼 길이를 넘으면 잘라내지 않고 null로 취급한다`() {
        // given — URL은 잘라 저장하면 깨진 주소가 되어 FE가 깨진 이미지를 렌더링한다
        val member = member(profileImageUrl = "https://k.kakaocdn.test/old.jpg")
        val tooLongUrl = "https://k.kakaocdn.test/" + "a".repeat(Member.MAX_KAKAO_PROFILE_IMAGE_URL_LENGTH)

        // when
        member.applyKakaoProfile("골드레슬러", tooLongUrl)

        // then
        assertThat(member.kakaoNickname).isEqualTo("골드레슬러")
        assertThat(member.kakaoProfileImageUrl).isNull()
    }

    @Test
    fun `컬럼 길이와 정확히 같은 값은 그대로 저장된다`() {
        // given — 경계값: 길이 제한은 "초과"에만 걸려야 한다
        val member = member()
        val exactNickname = "가".repeat(Member.MAX_KAKAO_NICKNAME_LENGTH)

        // when
        member.applyKakaoProfile(exactNickname, null)

        // then
        assertThat(member.kakaoNickname).isEqualTo(exactNickname)
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
