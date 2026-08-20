package com.goldwrestling.reservation

import com.goldwrestling.common.LikePatternEscaper
import com.goldwrestling.member.Member
import com.goldwrestling.member.PhoneNumberNormalizer
import com.goldwrestling.schedule.ClassType
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate

/**
 * 회원 본인 예약 목록(`GET /api/members/me/reservations`, 04-10)·관리자 예약 조회(RESV-07,
 * 04-12가 재사용 예정)의 동적 조건. `PassTransactionSpecifications`와 동일 관례를 따른다.
 */
object ReservationSpecifications {
    /**
     * 본인 스코프 조건 — IDOR 방어의 핵심이다(T-04-46, `PassTransactionSpecifications.ownedByMember`와
     * 동일 관례).
     *
     * **의도적으로 non-null 반환 타입이다.** `hasClassType`처럼 조건이 없을 때 `null`을 반환하게
     * 만들면, 호출부가 `listOfNotNull(...)`에서 이 조건을 실수로 빼먹을 수 있다 — 그 실수 하나가
     * 곧 다른 회원의 예약이 그대로 노출되는 IDOR이 된다. 그래서 이 함수는 항상 non-null
     * `Specification<Reservation>`을 반환해, 호출부가 "조건을 빼먹는" 선택지 자체를 갖지 못하게
     * 한다.
     */
    fun ownedByMember(memberId: Long): Specification<Reservation> =
        Specification { root, _, criteriaBuilder ->
            criteriaBuilder.equal(root.get<Member>("member").get<Long>("id"), memberId)
        }

    /** 예약 상태 필터 — 본인 목록 조회는 항상 `ACTIVE`를 고정으로 넘긴다(D-090, 취소된 예약 제외). */
    fun hasStatus(status: ReservationStatus): Specification<Reservation> =
        Specification { root, _, criteriaBuilder ->
            criteriaBuilder.equal(root.get<ReservationStatus>("status"), status)
        }

    /**
     * 수업 날짜 범위 필터(04-12 관리자 조회가 재사용) — nullable. [from]·[to] 둘 다 없으면 조건
     * 없음, 하나만 있으면 그 경계만 적용한다.
     */
    fun classDateBetween(
        from: LocalDate?,
        to: LocalDate?,
    ): Specification<Reservation>? {
        if (from == null && to == null) return null
        return Specification { root, _, criteriaBuilder ->
            val predicates = mutableListOf<Predicate>()
            if (from != null) predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("classDate"), from))
            if (to != null) predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("classDate"), to))
            criteriaBuilder.and(*predicates.toTypedArray())
        }
    }

    /** 수업 종류 필터(04-12 관리자 조회가 재사용). `null`이면 조건 없음. */
    fun hasClassType(classType: ClassType?): Specification<Reservation>? {
        if (classType == null) return null
        return Specification { root, _, criteriaBuilder ->
            criteriaBuilder.equal(root.get<ClassType>("classType"), classType)
        }
    }

    /**
     * 회원 검색어 필터(04-12 관리자 조회가 재사용) — `Reservation.member` 조인 경로로
     * `MemberSpecifications.keywordContains`와 같은 이스케이프([LikePatternEscaper])·정규화
     * ([PhoneNumberNormalizer]) 로직을 재사용한다(재구현하지 않는다). `null`/blank는 조건 없음.
     */
    fun memberKeywordContains(keyword: String?): Specification<Reservation>? {
        if (keyword.isNullOrBlank()) return null
        val trimmed = keyword.trim()
        val escapedNameKeyword = LikePatternEscaper.escapeWildcards(trimmed).lowercase()
        val normalizedPhone = PhoneNumberNormalizer.normalize(trimmed)

        return Specification { root, _, criteriaBuilder ->
            val memberPath = root.get<Member>("member")
            val namePredicate =
                criteriaBuilder.like(
                    criteriaBuilder.lower(memberPath.get("name")),
                    "%$escapedNameKeyword%",
                    LikePatternEscaper.ESCAPE_CHAR,
                )
            val predicates = mutableListOf(namePredicate)
            if (normalizedPhone.isNotEmpty()) {
                val escapedPhoneKeyword = LikePatternEscaper.escapeWildcards(normalizedPhone)
                predicates.add(
                    criteriaBuilder.like(
                        memberPath.get("phoneNumber"),
                        "%$escapedPhoneKeyword%",
                        LikePatternEscaper.ESCAPE_CHAR,
                    ),
                )
            }
            criteriaBuilder.or(*predicates.toTypedArray())
        }
    }
}
