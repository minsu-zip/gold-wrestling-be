package com.goldwrestling.pass.dto

import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassDisplayStatus
import com.goldwrestling.pass.PassType
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 이용권 응답 DTO. 등록(03-06)에서 확정되고 이후 가감·기간수정·취소·조회(03-07~03-10)가 그대로
 * 재사용한다.
 *
 * [cancelReason]은 관리자 전용 정보다(D-043의 연장) — 회원 본인 조회(03-10)는 취소된 이용권을
 * 응답에서 아예 제외하므로(D-058 "취소된 이용권만 숨김"), 회원이 이 필드를 받는 경로 자체가 없다.
 * 그래도 필드를 관리자·회원 응답으로 분리하지 않은 이유는, 회원 쪽에서는 애초에 이 값이 항상
 * null인 이용권만 노출되어 별도 DTO를 만들 실익이 없기 때문이다.
 */
@Schema(description = "이용권 응답")
data class PassResponse(
    @field:Schema(description = "이용권 ID") val passId: Long,
    @field:Schema(description = "회원 ID") val memberId: Long,
    @field:Schema(description = "이용권 종류") val type: PassType,
    @field:Schema(description = "표시 상태(취소 > 만료 > 소진 > 사용가능 우선순위로 계산, D-064)") val displayStatus: PassDisplayStatus,
    @field:Schema(description = "시작일") val startDate: LocalDate,
    @field:Schema(description = "종료일(포함) — 이 날짜까지 사용 가능(D-066)") val endDate: LocalDate,
    @field:Schema(description = "잔여 횟수 — 기간제(EVENING_MEMBERSHIP)는 null") val remainingCount: BigDecimal?,
    @field:Schema(description = "취소 시각 — 취소되지 않았으면 null") val canceledAt: OffsetDateTime?,
    @field:Schema(description = "취소 사유(관리자 전용) — 취소되지 않았으면 null") val cancelReason: String?,
    @field:Schema(description = "등록 시각") val registeredAt: OffsetDateTime,
) {
    companion object {
        /**
         * **트랜잭션이 열려 있는 서비스 계층 안에서만 호출한다.** [Pass.member]가 `LAZY` 연관이라
         * [memberId]를 얻으려 `pass.member.id`에 접근하는 순간, 트랜잭션 밖이면
         * `LazyInitializationException`이 난다(RESEARCH Pitfall 3).
         *
         * [displayStatus]는 [Pass.displayStatus]를 그대로 호출해 얻는다 — 취소·만료·소진·사용가능
         * 판정 규칙을 이 변환 계층에서 다시 쓰지 않는다.
         */
        fun from(
            pass: Pass,
            today: LocalDate,
        ): PassResponse =
            PassResponse(
                passId = requireNotNull(pass.id) { "저장되지 않은 Pass는 응답으로 변환할 수 없습니다." },
                memberId = requireNotNull(pass.member.id) { "저장되지 않은 Member를 참조하는 Pass는 응답으로 변환할 수 없습니다." },
                type = pass.type,
                displayStatus = pass.displayStatus(today),
                startDate = pass.startDate,
                endDate = pass.endDate,
                remainingCount = pass.remainingCount,
                canceledAt = pass.canceledAt,
                cancelReason = pass.cancelReason,
                registeredAt = pass.createdAt,
            )
    }
}
