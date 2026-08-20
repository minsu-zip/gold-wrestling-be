package com.goldwrestling.schedule.dto

import com.goldwrestling.schedule.ClassSession
import com.goldwrestling.schedule.ClassSessionStatus
import com.goldwrestling.schedule.ClassType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * 날짜별 수업(`ClassSession`) 응답(RESV-09) — 휴강 처리·해제 결과를 표현한다.
 *
 * [canceledReservationCount]는 휴강 처리 응답에서만 채워진다(`suspend`가 자동 취소한 예약 건수) —
 * 휴강 해제 응답(`resume`)은 이 값을 계산하지 않으므로 `null`이다(휴강 해제는 예약을 복원하지
 * 않는다, CONTEXT.md 락인).
 */
@Schema(description = "날짜별 수업(ClassSession) 응답 — 휴강 처리·해제 결과")
data class ClassSessionResponse(
    @field:Schema(description = "날짜별 수업(ClassSession) ID") val id: Long,
    @field:Schema(description = "정기 시간표(ClassSchedule) ID") val classScheduleId: Long,
    @field:Schema(description = "수업 날짜") val classDate: LocalDate,
    @field:Schema(description = "수업 종류") val classType: ClassType,
    @field:Schema(description = "시작 시각") val startTime: LocalTime,
    @field:Schema(description = "종료 시각") val endTime: LocalTime,
    @field:Schema(description = "정원 — EVENING은 null") val capacity: Int?,
    @field:Schema(description = "현재 예약 인원") val reservedCount: Int,
    @field:Schema(description = "세션 상태") val status: ClassSessionStatus,
    @field:Schema(description = "휴강 사유 — 휴강 상태가 아니면 null") val cancelReason: String?,
    @field:Schema(description = "휴강 처리 시각 — 휴강 상태가 아니면 null") val canceledAt: OffsetDateTime?,
    @field:Schema(description = "휴강 처리로 자동 취소된 예약 건수 — 휴강 해제 응답에서는 null")
    val canceledReservationCount: Int?,
) {
    companion object {
        /**
         * **트랜잭션이 열려 있는 서비스 계층 안에서만 호출한다.** [ClassSession.classSchedule]이
         * `LAZY` 연관이라 트랜잭션 밖에서 `.id`가 아닌 다른 필드에 접근하면 `LazyInitializationException`이
         * 난다 — `.id` 자체는 프록시가 이미 알고 있는 값이라 초기화를 유발하지 않는다
         * (`AdminReservationResponse.from`과 동일 관례).
         */
        fun from(
            session: ClassSession,
            canceledReservationCount: Int? = null,
        ): ClassSessionResponse =
            ClassSessionResponse(
                id = requireNotNull(session.id) { "저장되지 않은 ClassSession은 응답으로 변환할 수 없습니다." },
                classScheduleId =
                    requireNotNull(session.classSchedule.id) {
                        "저장되지 않은 ClassSchedule을 참조하는 ClassSession입니다."
                    },
                classDate = session.classDate,
                classType = session.classType,
                startTime = session.startTime,
                endTime = session.endTime,
                capacity = session.capacity,
                reservedCount = session.reservedCount,
                status = session.status,
                cancelReason = session.cancelReason,
                canceledAt = session.canceledAt,
                canceledReservationCount = canceledReservationCount,
            )
    }
}
