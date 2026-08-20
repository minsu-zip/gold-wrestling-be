package com.goldwrestling.member

import com.goldwrestling.auth.AuthenticatedPrincipal
import com.goldwrestling.auth.PrincipalType
import org.springframework.stereotype.Component

/**
 * 회원 상태 조건 인가의 실현부(D-040). `SecurityConfig`의 `authorizeHttpRequests`는
 * 역할(`ROLE_MEMBER`/`ROLE_ADMIN`)만 가르고, "회원 상태가 `ACTIVE`여야 한다" 같은 조건은
 * URL 규칙으로 표현하지 않는다 — `GET /api/members/me`와 온보딩 제출처럼 `PENDING` 상태도
 * 접근 가능해야 하는 엔드포인트가 있어, URL 패턴으로 예외를 또 열어주면 규칙이 더 복잡해지기
 * 때문이다.
 *
 * **이 컴포넌트는 DB를 조회하지 않는다.** `AuthenticatedPrincipal`이 이미 `JwtAuthenticationFilter`
 * 안에서 `AuthenticationPrincipalResolver`가 매 요청 DB로부터 채운 값을 들고 있다(D-033 "DB 현재
 * 상태 기준"). 여기서 다시 조회하면 같은 요청 안에서 같은 회원을 두 번 읽는 중복 비용만 늘어난다.
 *
 * **운영 규칙(D-040 유의)**: 회원 대상 엔드포인트를 새로 만들 때마다 이 게이트 호출이 필요한지
 * 확인한다. 서비스 계층 검사는 "빠뜨릴 수 있는" 방식이라, 새 엔드포인트를 추가하면서 게이트를
 * 잊으면 컴파일도 테스트도 그 자체로는 잡아주지 않는다.
 */
@Component
class MemberStateGate {
    /**
     * 회원 상태가 `ACTIVE`인지 검사한다. 관리자 principal이 들어오면 [IllegalStateException]을
     * 던진다 — `SecurityConfig`의 URL 인가가 이미 `/api/members` 하위 전체 경로를 `ROLE_MEMBER`로
     * 막았어야 하는 경로이므로, 여기 도달했다면 URL 규칙과 서비스 코드가 어긋난 프로그래밍 오류다.
     */
    fun requireActive(principal: AuthenticatedPrincipal) {
        check(principal.principalType == PrincipalType.MEMBER) {
            "회원 상태 게이트는 회원 전용이지만 관리자 principal이 들어왔습니다."
        }
        if (principal.memberStatus != MemberStatus.ACTIVE) {
            throw MemberNotActiveException()
        }
    }

    /**
     * 온보딩 제출이 허용되는 상태인지 검사한다.
     *
     * **검사 순서가 중요하다** — 온보딩 완료 여부를 상태 확인보다 먼저 검사한다. 그래야 이미
     * `ACTIVE`로 승인된(즉 온보딩도 이미 끝난) 회원이 재제출을 시도했을 때 "현재 상태에서는
     * 등록할 수 없다"는 뭉뚱그린 안내 대신 "이미 등록했다"는 더 정확한 안내를 받는다.
     */
    fun requireOnboardingAllowed(principal: AuthenticatedPrincipal) {
        check(principal.principalType == PrincipalType.MEMBER) {
            "온보딩 상태 게이트는 회원 전용이지만 관리자 principal이 들어왔습니다."
        }
        if (principal.onboardingCompleted) {
            throw OnboardingAlreadyCompletedException()
        }
        if (principal.memberStatus != MemberStatus.PENDING) {
            throw MemberStateConflictException("현재 상태에서는 정보를 등록할 수 없습니다.")
        }
    }
}
