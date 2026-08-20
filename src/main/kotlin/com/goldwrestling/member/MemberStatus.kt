package com.goldwrestling.member

/**
 * 회원 상태 (policies.md §5).
 */
enum class MemberStatus {
    /** 승인대기 — 카카오 가입 직후. 승인 대기 화면만 노출 */
    PENDING,

    /** 활성 — 정상 이용 */
    ACTIVE,

    /** 휴회 — 관리자가 설정. 2주 미사용 차감 정지, 저녁반은 기간 연장으로 별도 보상 */
    ON_LEAVE,

    /** 비활성 — 탈퇴/장기 미이용 등 */
    INACTIVE,
}
