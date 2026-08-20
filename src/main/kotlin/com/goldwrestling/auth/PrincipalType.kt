package com.goldwrestling.auth

/**
 * 인증 주체 종류. Member/Admin이 별도 테이블이라 토큰·인가 처리에서 어느 쪽을 가리키는지
 * 구분하는 용도다 (D-040 인가 설계).
 */
enum class PrincipalType {
    /** 회원 (카카오 로그인) */
    MEMBER,

    /** 관리자 (ID/PW 로그인) */
    ADMIN,
}
