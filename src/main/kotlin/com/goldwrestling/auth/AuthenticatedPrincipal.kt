package com.goldwrestling.auth

import com.goldwrestling.member.MemberStatus

/**
 * 인증된 요청의 주체를 나타내는 값 객체. **엔티티(`Member`/`Admin`)를 담지 않는다.**
 *
 * `JwtAuthenticationFilter`는 영속성 컨텍스트가 열려 있음을 보장할 수 없는 필터 레벨에서 동작한다.
 * `Member.branch`처럼 `LAZY`로 매핑된 연관 필드를 가진 엔티티를 그대로 principal에 담으면, 이후
 * 컨트롤러·서비스에서 그 필드에 접근하는 순간 `LazyInitializationException`이 난다
 * (RESEARCH.md Pitfall 4). 그래서 이 값 객체는 스칼라 값만 들고 다닌다 — 서비스에서 실제 엔티티가
 * 필요하면 [principalId]로 리포지토리를 다시 조회한다. 어차피 D-033 때문에 회원 상태는 매 요청
 * DB에서 재조회해야 하므로, 이 재조회가 새로 생기는 비용도 아니다.
 */
data class AuthenticatedPrincipal(
    val principalType: PrincipalType,
    val principalId: Long,
    /** ADMIN이면 항상 null — 관리자는 회원 상태 게이트 대상이 아니다. */
    val memberStatus: MemberStatus?,
    /** ADMIN이면 항상 true 고정 — 온보딩 개념은 회원에게만 있다(D-025). */
    val onboardingCompleted: Boolean,
) {
    /**
     * 회원 전용 서비스 로직에서 회원 id가 필요할 때 쓴다.
     *
     * 이 경로는 `SecurityConfig`의 URL 인가 규칙(`hasRole("MEMBER")`)이 이미 관리자 접근을 막고
     * 있으므로, 여기서 던지는 예외는 사용자 대면 오류가 아니라 "인가 규칙과 서비스 코드가 어긋났다"는
     * 프로그래밍 오류 신호다.
     */
    fun requireMemberId(): Long {
        check(principalType == PrincipalType.MEMBER) {
            "회원 전용 경로에 관리자 주체가 들어왔습니다."
        }
        return principalId
    }

    /**
     * 관리자 전용 서비스 로직에서 관리자 id가 필요할 때 쓴다. [requireMemberId]와 대칭이다.
     *
     * 이 경로도 `SecurityConfig`의 URL 인가 규칙(`hasRole("ADMIN")`)이 이미 회원 접근을 막고
     * 있으므로, 여기서 던지는 예외는 사용자 대면 오류가 아니라 "인가 규칙과 서비스 코드가
     * 어긋났다"는 프로그래밍 오류 신호다.
     */
    fun requireAdminId(): Long {
        check(principalType == PrincipalType.ADMIN) {
            "관리자 전용 경로에 회원 주체가 들어왔습니다."
        }
        return principalId
    }
}
