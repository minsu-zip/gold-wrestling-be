package com.goldwrestling.reservation

import com.goldwrestling.member.dto.PageResponse
import com.goldwrestling.reservation.dto.AdminReservationResponse
import com.goldwrestling.reservation.dto.ReservationSearchCondition
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 관리자 전체 예약 조회(RESV-07). 04-10의 [ReservationSpecifications]를 재사용해
 * `AdminMemberService.search`(회원 검색)와 동일한 형태(`Specification.allOf` + `PageResponse`)로
 * 조립한다 — 목록 API 계약을 Phase 2·3과 일관되게 유지한다.
 *
 * 대리 취소·변경(04-13)의 대상 예약을 찾는 진입점이라 취소된 예약도 기본으로 노출한다
 * ([ReservationSearchCondition.status]를 생략하면 조건 없음).
 */
@Service
@Transactional(readOnly = true)
class AdminReservationService(
    private val reservationRepository: ReservationRepository,
) {
    /**
     * 조건을 조합해 예약을 페이지 단위로 조회한다. 정렬은 `reservedAt` 내림차순 고정(최근 예약이 위) —
     * `AdminMemberService.search`가 `createdAt` 내림차순을 쓰는 것과 같은 이유로, 관리자가 방금
     * 들어온 예약·변경을 먼저 보게 한다.
     *
     * `from > to`는 형식 검증(`@Valid`)으로 표현할 수 없는 두 필드 간 관계라 여기서 판정한다
     * (conventions §6 — 도메인 규칙은 서비스에서, `AdminPassService.changePeriod`의 기간 역전
     * 판정과 동일 관례).
     */
    fun search(condition: ReservationSearchCondition): PageResponse<AdminReservationResponse> {
        if (condition.from != null && condition.to != null && condition.from.isAfter(condition.to)) {
            throw InvalidReservationSearchRangeException()
        }
        val specification =
            Specification.allOf<Reservation>(
                listOfNotNull(
                    ReservationSpecifications.classDateBetween(condition.from, condition.to),
                    ReservationSpecifications.hasClassType(condition.classType),
                    ReservationSpecifications.memberKeywordContains(condition.keyword),
                    condition.status?.let { ReservationSpecifications.hasStatus(it) },
                ),
            )
        val pageable = PageRequest.of(condition.page, condition.size, Sort.by(Sort.Direction.DESC, "reservedAt"))
        val page = reservationRepository.findAll(specification, pageable)
        return PageResponse.from(page) { AdminReservationResponse.from(it) }
    }
}
