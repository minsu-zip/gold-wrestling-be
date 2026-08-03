package com.goldwrestling.pass

import com.goldwrestling.member.Member
import org.springframework.data.jpa.domain.Specification

/**
 * 회원 본인 이력 조회(`GET /api/members/me/pass-transactions`, PASS-06)의 동적 조건.
 *
 * **파일명이 `PassSpecifications`가 아니라 `PassTransactionSpecifications`인 이유**: 이 객체가 필터하는
 * 엔티티는 `Pass`가 아니라 `PassTransaction`이다(조인 경로 `PassTransaction.pass.member.id`).
 * `MemberSpecifications`와 같은 관례를 따른다 — 각 함수는 조건이 없으면 `null`을 반환하고,
 * 호출부가 `Specification.allOf(listOfNotNull(...))`로 합친다.
 */
object PassTransactionSpecifications {
    /**
     * 본인 스코프 조건 — IDOR 방어의 핵심이다(T-03-41·T-03-42). `pass.member.id = memberId` 경로로
     * 비교한다.
     *
     * **의도적으로 nullable 반환 타입이 아니다.** `hasPassId`처럼 조건이 없을 때 `null`을 반환하게
     * 만들면 호출부가 `listOfNotNull(...)`에서 이 조건을 실수로 빼먹을 수 있다 — 그 실수 하나가
     * 곧 다른 회원의 이력이 그대로 노출되는 IDOR이 된다. 그래서 이 함수는 항상 non-null
     * `Specification<PassTransaction>`을 반환해, 호출부가 "조건을 빼먹는" 선택지 자체를 갖지 못하게
     * 한다.
     */
    fun ownedByMember(memberId: Long): Specification<PassTransaction> =
        Specification { root, _, criteriaBuilder ->
            criteriaBuilder.equal(
                root.get<Pass>("pass").get<Member>("member").get<Long>("id"),
                memberId,
            )
        }

    /** `passId` 필터. `null`이면 조건 없음(`MemberSpecifications.hasStatus`와 동일 관례). */
    fun hasPassId(passId: Long?): Specification<PassTransaction>? {
        if (passId == null) return null
        return Specification { root, _, criteriaBuilder ->
            criteriaBuilder.equal(root.get<Pass>("pass").get<Long>("id"), passId)
        }
    }
}
