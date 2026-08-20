package com.goldwrestling.pass.dto

import com.goldwrestling.pass.PassTransaction
import com.goldwrestling.pass.PassType
import com.goldwrestling.pass.TransactionReason
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * 회원 본인 차감/복구 이력 응답(PASS-06) 항목 하나.
 *
 * **`note`(관리자 자유 텍스트 사유)를 담지 않는다.** requirements §3.2가 요구하는 "무슨 사유로"는
 * 닫힌 코드인 [reason]이 답하고, `note`는 관리자가 남기는 내부 운영 메모다(D-043 "거절 사유 원문은
 * 회원에게 노출하지 않는다"의 연장). **이 규칙은 계획 단계에서 사용자가 확정했다(2026-08-03,
 * plan-phase AskUserQuestion — "회원에게 비노출" 선택, D-070).**
 */
@Schema(description = "회원 본인 차감/복구 이력 항목")
data class PassTransactionResponse(
    @field:Schema(description = "이력 ID") val transactionId: Long,
    @field:Schema(description = "이용권 ID") val passId: Long,
    @field:Schema(description = "이용권 종류") val passType: PassType,
    @field:Schema(description = "증감 수량(+차감/복구, 부호로 방향 구분)") val amount: BigDecimal,
    @field:Schema(description = "차감/복구 사유 코드") val reason: TransactionReason,
    @field:Schema(description = "발생 시각") val occurredAt: OffsetDateTime,
) {
    companion object {
        /**
         * **트랜잭션이 열려 있는 서비스 계층 안에서만 호출한다.** [PassTransaction.pass]가 `LAZY`
         * 연관이라 [passId]·[passType]을 얻으려 `transaction.pass.id`/`transaction.pass.type`에
         * 접근하는 순간, 트랜잭션 밖이면 `LazyInitializationException`이 난다(`PassResponse.from`과
         * 동일 함정).
         */
        fun from(transaction: PassTransaction): PassTransactionResponse =
            PassTransactionResponse(
                transactionId =
                    requireNotNull(transaction.id) { "저장되지 않은 PassTransaction은 응답으로 변환할 수 없습니다." },
                passId = requireNotNull(transaction.pass.id) { "저장되지 않은 Pass를 참조하는 PassTransaction은 응답으로 변환할 수 없습니다." },
                passType = transaction.pass.type,
                amount = transaction.amount,
                reason = transaction.reason,
                occurredAt = transaction.occurredAt,
            )
    }
}
