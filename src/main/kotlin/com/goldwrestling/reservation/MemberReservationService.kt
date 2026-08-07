package com.goldwrestling.reservation

import com.goldwrestling.reservation.dto.ReservationResponse
import com.goldwrestling.reservation.dto.ReserveRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 회원 예약 생성(RESV-01·02) — 이 phase에서 가장 중요한 트랜잭션 조립.
 *
 * TODO(04-07 GREEN): `<action>`의 ①~⑫ 순서대로 구현한다. 지금은 RED 확보를 위한 스텁이다.
 */
@Service
@Transactional(readOnly = true)
class MemberReservationService {
    @Transactional
    fun reserve(
        memberId: Long,
        request: ReserveRequest,
    ): ReservationResponse = TODO("04-07 GREEN에서 구현")
}
