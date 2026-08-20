package com.goldwrestling.reservation.dto

import com.goldwrestling.auth.PrincipalType
import com.goldwrestling.reservation.Reservation
import com.goldwrestling.reservation.ReservationStatus
import com.goldwrestling.schedule.ClassType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * 관리자 전체 예약 조회 응답(RESV-07). 회원 본인용 [ReservationResponse]와 달리 회원 정보와
 * 취소 메타데이터(누가·언제·복구 여부)를 포함한다 — 관리자가 대리 취소·변경(04-13)의 대상을
 * 찾는 화면이기 때문이다.
 *
 * **전화번호는 넣지 않는다**(T-04-59) — 예약 목록의 목적이 아니고, 필요하면 회원 상세 API로 간다.
 */
@Schema(description = "관리자 예약 응답 — 취소 메타데이터 포함")
data class AdminReservationResponse(
    @field:Schema(description = "예약 ID") val id: Long,
    @field:Schema(description = "회원 ID") val memberId: Long,
    @field:Schema(description = "회원 이름") val memberName: String,
    @field:Schema(description = "날짜별 수업(ClassSession) ID") val classSessionId: Long,
    @field:Schema(description = "수업 종류") val classType: ClassType,
    @field:Schema(description = "수업 날짜") val classDate: LocalDate,
    @field:Schema(description = "시작 시각") val startTime: LocalTime,
    @field:Schema(description = "종료 시각") val endTime: LocalTime,
    @field:Schema(description = "예약 상태") val status: ReservationStatus,
    @field:Schema(description = "예약(생성) 시각") val reservedAt: OffsetDateTime,
    @field:Schema(description = "취소 시각 — 취소되지 않았으면 null") val canceledAt: OffsetDateTime?,
    @field:Schema(description = "복구 여부 — 취소되지 않았으면 null") val refunded: Boolean?,
    @field:Schema(description = "취소 주체 종류 — 취소되지 않았으면 null") val canceledByType: PrincipalType?,
    @field:Schema(description = "취소 주체 이름 — 취소되지 않았으면 null") val canceledByName: String?,
    @field:Schema(description = "차감에 쓰인 이용권 ID") val passId: Long,
) {
    companion object {
        /**
         * **트랜잭션이 열려 있는 서비스 계층 안에서만 호출한다.** [Reservation.member]·
         * [Reservation.classSession]·[Reservation.canceledByMember]·[Reservation.canceledByAdmin]이
         * `LAZY` 연관이라 트랜잭션 밖에서 접근하면 `LazyInitializationException`이 난다 —
         * `AdminReservationService.search`가 `ReservationRepository.findAll`의 `@EntityGraph`로
         * 이 연관들을 미리 로딩해 두므로 N+1 없이 안전하다(T-04-58).
         *
         * [memberName]은 예약이 존재하려면 회원이 `ACTIVE`(온보딩 완료 전제)여야 하므로 항상 값이
         * 있어야 한다(`AdminScheduleService`의 `requireNotNull` 관례와 동일) — 위반 시 데이터
         * 정합성 오류로 즉시 드러나게 한다.
         */
        fun from(reservation: Reservation): AdminReservationResponse {
            val (canceledByType, canceledByName) =
                when {
                    reservation.canceledByMember != null -> {
                        val member = reservation.canceledByMember!!
                        PrincipalType.MEMBER to
                            requireNotNull(member.name) { "이름이 없는 회원(id=${member.id})이 취소 주체일 수 없습니다." }
                    }

                    reservation.canceledByAdmin != null -> {
                        val admin = reservation.canceledByAdmin!!
                        PrincipalType.ADMIN to admin.name
                    }

                    else -> {
                        null to null
                    }
                }

            return AdminReservationResponse(
                id = requireNotNull(reservation.id) { "저장되지 않은 Reservation은 응답으로 변환할 수 없습니다." },
                memberId = requireNotNull(reservation.member.id) { "저장되지 않은 Member를 참조하는 Reservation입니다." },
                memberName =
                    requireNotNull(reservation.member.name) {
                        "이름이 없는 회원(id=${reservation.member.id})의 예약은 응답으로 변환할 수 없습니다."
                    },
                classSessionId =
                    requireNotNull(reservation.classSession.id) {
                        "저장되지 않은 ClassSession을 참조하는 Reservation입니다."
                    },
                classType = reservation.classType,
                classDate = reservation.classDate,
                startTime = reservation.startTime,
                endTime = reservation.classSession.endTime,
                status = reservation.status,
                reservedAt = reservation.reservedAt,
                canceledAt = reservation.canceledAt,
                refunded = reservation.refunded,
                canceledByType = canceledByType,
                canceledByName = canceledByName,
                passId = requireNotNull(reservation.pass.id) { "저장되지 않은 Pass를 참조하는 Reservation입니다." },
            )
        }
    }
}
