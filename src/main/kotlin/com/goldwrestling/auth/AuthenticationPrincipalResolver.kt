package com.goldwrestling.auth

import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.member.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * JWT의 `principalType`/`principalId` 클레임을 매 요청 DB의 현재 상태로 변환한다.
 *
 * D-033이 요구하는 "상태는 토큰 클레임이 아니라 DB 현재 상태 기준" 인가의 실현부다. 유효한 서명의
 * 토큰이 들어올 때마다(즉 거의 매 인증 요청마다) [JwtAuthenticationFilter]가 이 메서드를 호출한다 —
 * 이 조회 비용이 곧 강제 로그아웃 정확도(회원 상태 변경이 얼마나 빨리 반영되는가)의 대가다.
 *
 * **엔티티를 반환하지 않는다.** 조회 결과에서 필요한 스칼라 값만 뽑아 [AuthenticatedPrincipal] 값
 * 객체로 만든다(엔티티를 담지 않는 이유는 `AuthenticatedPrincipal`의 KDoc 참고).
 */
@Service
@Transactional(readOnly = true)
class AuthenticationPrincipalResolver(
    private val memberRepository: MemberRepository,
    private val adminRepository: AdminRepository,
) {
    /**
     * @return [principalType]/[principalId]에 해당하는 주체가 DB에 있으면 그 현재 상태를 담은
     *   [AuthenticatedPrincipal], 없으면 `null` (삭제되었거나 존재한 적 없는 주체 — T-02-15).
     */
    fun resolve(
        principalType: PrincipalType,
        principalId: Long,
    ): AuthenticatedPrincipal? =
        when (principalType) {
            PrincipalType.MEMBER -> {
                val member = memberRepository.findById(principalId).orElse(null)
                member?.let {
                    AuthenticatedPrincipal(
                        principalType = PrincipalType.MEMBER,
                        principalId = principalId,
                        memberStatus = it.status,
                        onboardingCompleted = it.isOnboardingCompleted(),
                    )
                }
            }

            PrincipalType.ADMIN -> {
                val admin = adminRepository.findById(principalId).orElse(null)
                admin?.let {
                    AuthenticatedPrincipal(
                        principalType = PrincipalType.ADMIN,
                        principalId = principalId,
                        memberStatus = null,
                        onboardingCompleted = true,
                    )
                }
            }
        }
}
