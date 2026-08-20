package com.goldwrestling.reservation.dto

import com.goldwrestling.reservation.Reservation
import com.goldwrestling.reservation.ReservationStatus
import com.goldwrestling.schedule.ClassType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * 예약 응답 DTO(RESV-01·02). 엔티티를 그대로 노출하지 않는다(D-019).
 *
 * **다른 회원 정보를 포함하지 않는다**(T-04-32) — 본인 예약 응답이라 불필요하다.
 */
@Schema(description = "예약 응답")
data class ReservationResponse(
    @field:Schema(description = "예약 ID") val id: Long,
    @field:Schema(description = "날짜별 수업(ClassSession) ID") val classSessionId: Long,
    @field:Schema(description = "수업 종류") val classType: ClassType,
    @field:Schema(description = "수업 날짜") val classDate: LocalDate,
    @field:Schema(description = "시작 시각") val startTime: LocalTime,
    @field:Schema(description = "종료 시각") val endTime: LocalTime,
    @field:Schema(description = "예약 상태") val status: ReservationStatus,
    @field:Schema(description = "예약(생성) 시각") val reservedAt: OffsetDateTime,
    @field:Schema(description = "차감에 쓰인 이용권 ID") val passId: Long,
) {
    companion object {
        /**
         * **트랜잭션이 열려 있는 서비스 계층 안에서만 호출한다.** [Reservation.classSession]·
         * [Reservation.pass]가 `LAZY` 연관이라 `.id`에 접근하는 순간 트랜잭션 밖이면
         * `LazyInitializationException`이 난다(`PassResponse.from`과 동일한 경고).
         *
         * [endTime]은 `Reservation`이 직접 보유하지 않는 값이다 — 비정규화 컬럼은 부분 유니크
         * 인덱스 근거로 필요한 classType·classDate·startTime뿐이라(D-021), 호출부가 세션에서
         * 읽은 값을 넘긴다.
         */
        fun from(
            reservation: Reservation,
            endTime: LocalTime,
        ): ReservationResponse =
            ReservationResponse(
                id = requireNotNull(reservation.id) { "저장되지 않은 Reservation은 응답으로 변환할 수 없습니다." },
                classSessionId =
                    requireNotNull(reservation.classSession.id) {
                        "저장되지 않은 ClassSession을 참조하는 Reservation입니다."
                    },
                classType = reservation.classType,
                classDate = reservation.classDate,
                startTime = reservation.startTime,
                endTime = endTime,
                status = reservation.status,
                reservedAt = reservation.reservedAt,
                passId = requireNotNull(reservation.pass.id) { "저장되지 않은 Pass를 참조하는 Reservation입니다." },
            )
    }
}
