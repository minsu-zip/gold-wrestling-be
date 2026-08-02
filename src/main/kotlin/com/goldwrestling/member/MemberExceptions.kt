package com.goldwrestling.member

import com.goldwrestling.common.error.DomainException
import com.goldwrestling.common.error.ErrorCode

/**
 * 대상 회원이 없을 때. **[memberId]를 메시지에 보간하지 않는다** — 존재 여부를 응답 문구로 탐색할 수
 * 있게 되는 것을 막기 위해서다(conventions §8 "내부 정보를 응답에 넣지 않는다"의 연장).
 */
@Suppress("UNUSED_PARAMETER")
class MemberNotFoundException(
    memberId: Long?,
) : DomainException(
        ErrorCode.MEMBER_NOT_FOUND,
        "회원을 찾을 수 없습니다.",
    )

/**
 * `MemberStateGate.requireActive`가 던진다 — 회원 상태가 `ACTIVE`가 아닌 상태에서 상태 게이트로
 * 보호되는 기능에 접근했을 때(policies §5, D-040).
 */
class MemberNotActiveException :
    DomainException(
        ErrorCode.MEMBER_NOT_ACTIVE,
        "승인된 회원만 이용할 수 있습니다.",
    )

/**
 * 이미 온보딩을 완료한 회원이 온보딩 API를 다시 호출했을 때(D-042 온보딩 재제출 금지).
 * 메시지는 "프로필 수정은 관리자 문의"라는 D-042의 정책을 사용자 문구로 그대로 옮긴다.
 */
class OnboardingAlreadyCompletedException :
    DomainException(
        ErrorCode.ONBOARDING_ALREADY_COMPLETED,
        "이미 정보를 등록했습니다. 변경이 필요하면 관리자에게 문의해 주세요.",
    )

/**
 * 회원 상태가 요청된 동작을 허용하지 않는 그 밖의 충돌 상황. 호출부가 상황에 맞는 사용자 대면
 * 문구를 [message]로 넘긴다(예: "현재 상태에서는 정보를 등록할 수 없습니다.").
 */
class MemberStateConflictException(
    message: String,
) : DomainException(ErrorCode.MEMBER_STATE_CONFLICT, message)
