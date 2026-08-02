package com.goldwrestling.member

import com.goldwrestling.auth.AuthenticatedPrincipal
import com.goldwrestling.member.dto.MyProfileResponse
import com.goldwrestling.member.dto.OnboardingRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 회원 본인 프로필 조회·온보딩 제출(AUTH-05, MEMBER-04).
 */
@Service
@Transactional(readOnly = true)
class MemberProfileService(
    private val memberRepository: MemberRepository,
    private val memberStateGate: MemberStateGate,
) {
    /**
     * 본인 프로필을 조회한다.
     *
     * **의도적으로 [MemberStateGate]를 호출하지 않는다.** 승인 대기(`PENDING`) 화면과 거절 안내 화면이
     * 이 응답에 의존하므로(policies §5, ROADMAP Phase 2 성공기준 5) 모든 회원 상태에서 접근 가능해야
     * 한다 — 상태 게이트를 걸면 정작 그 상태를 보여주려는 화면 자체가 막힌다.
     */
    fun getMyProfile(principal: AuthenticatedPrincipal): MyProfileResponse {
        val memberId = principal.requireMemberId()
        val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        return MyProfileResponse.from(member)
    }

    /**
     * 온보딩(실명·전화번호 등록)을 제출한다. **회원 상태를 바꾸지 않는다** — policies §5.1이
     * 온보딩을 별도 상태 전이가 아니라 프로필 입력 여부로만 판정하도록 못박았다. 승인(`PENDING` →
     * `ACTIVE`)은 관리자의 몫이다(02-10).
     */
    @Transactional
    fun submitOnboarding(
        principal: AuthenticatedPrincipal,
        request: OnboardingRequest,
    ): MyProfileResponse {
        memberStateGate.requireOnboardingAllowed(principal)

        val memberId = principal.requireMemberId()
        val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }

        // principal은 요청 시작 시점(JwtAuthenticationFilter)의 스냅샷이다. 같은 회원이 온보딩
        // 제출을 거의 동시에 두 번 보내면 두 요청 모두 principal 기준 게이트를 통과할 수 있다.
        // 여기서 엔티티(트랜잭션 안에서 다시 읽은 현재 값) 기준으로 한 번 더 검사해야 두 번째
        // 요청이 이미 채워진 값을 덮어쓰는 것을 막는다.
        if (member.isOnboardingCompleted()) {
            throw OnboardingAlreadyCompletedException()
        }

        member.name = request.name.trim()
        member.phoneNumber = PhoneNumberNormalizer.normalize(request.phoneNumber)
        // member.status는 건드리지 않는다 — 온보딩은 상태 전이가 아니다(policies §5.1).

        // 별도 save() 호출이 없다 — member는 이 트랜잭션에서 findById로 읽어온 영속 상태 엔티티라,
        // 위 필드 대입이 JPA 변경 감지(dirty checking)에 의해 트랜잭션 커밋 시점에 UPDATE로 flush된다.
        return MyProfileResponse.from(member)
    }
}
