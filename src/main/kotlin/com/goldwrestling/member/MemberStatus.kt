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

    /** 비활성 — 탈퇴/장기 미이용 등. 2주 미사용 차감 정지(policies §4.3, D-147) */
    INACTIVE,
    ;

    companion object {
        /**
         * 2주 미사용 자동 차감(policies §4.3)에서 **제외되는 회원 상태**.
         *
         * 이 집합이 두 곳의 유일한 근거다 — ① 배치 대상 회원 조회 필터
         * (`PassRepository.findMemberIdsWithDeductibleSessionPass`)와 ② 기준일 후보 ③을 기록하는
         * 시점 판정(`AdminMemberService.changeStatus`). 두 곳이 같은 집합을 보므로 "제외 중에는
         * 부채가 쌓이지 않고, 제외를 벗어나면 유예가 새로 시작된다"는 원리가 한 벌로 유지된다
         * (D-105·D-147).
         *
         * **여기에 상태를 더하면 두 지점이 함께 따라온다** — 어느 한쪽만 고치면 "차감은 안 되는데
         * 유예도 리셋되지 않는" 또는 그 반대의 비대칭이 생긴다. 값을 바꾸기 전에 policies §4.3의
         * 예외 목록을 먼저 고친다(문서가 기준, CLAUDE.md 문서 우선순위).
         *
         * `INACTIVE`가 들어 있는 이유(WR-06): 탈퇴·장기 미이용 회원의 잔여가 계속 깎여 0이 되면
         * 환불 분쟁의 소지가 된다. 이용 의사가 없는 회원에게 "미사용 부채"를 물리는 것은 §4.3의
         * 취지(활동을 유도하는 유예)와도 어긋난다.
         */
        val DEDUCTION_EXCLUDED: Set<MemberStatus> = setOf(ON_LEAVE, INACTIVE)
    }
}
