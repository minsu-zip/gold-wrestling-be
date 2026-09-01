package com.goldwrestling.pass.dto

import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassTransaction
import com.goldwrestling.pass.PassType
import com.goldwrestling.pass.TransactionReason
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * 관리자용 회원 차감/복구 이력 응답(`GET /api/admin/members/{memberId}/pass-transactions`,
 * PASS-05, BE-REQ-003, D-149) 항목 하나.
 *
 * **회원용 [PassTransactionResponse]와 의도적으로 다른 DTO다.** 같은 테이블을 보지만 노출 범위가
 * 반대이기 때문이다 — 하나의 DTO에 `note`를 nullable로 얹고 호출부에 따라 채우거나 비우면, FE는
 * "이 필드가 언제 오는지"를 계약이 아니라 관례로 알아야 한다(D-070이 조건부 필드를 기각한 이유와
 * 같다). 두 스키마로 갈라 두면 각 응답이 자기 필드를 항상 채운다.
 *
 * 회원용과 다른 점 두 가지:
 * 1. **[note]를 담는다** — 관리자가 남긴 자유 텍스트 사유다. 회원 응답은 D-070으로 이 필드를
 *    제외하지만, 그 근거("내부 운영 메모를 회원에게 노출하지 않는다")는 관리자에게 적용되지
 *    않는다. 관리자는 오히려 자신이 남긴 조정 메모를 다시 볼 수 있어야 한다.
 * 2. **[passStatus]를 담는다** — 회원 응답은 취소된 이용권의 이력을 아예 숨기지만(D-073),
 *    관리자 화면은 감사 가능성을 위해 취소분까지 전부 본다(D-059 "관리자 화면에서는 구분 표시").
 *    그래서 각 행이 어느 상태의 이용권에서 나왔는지 구분할 수단이 필요하다.
 */
@Schema(description = "관리자용 회원 차감/복구 이력 항목 — 관리자 메모(note)와 이용권 상태를 포함한다")
data class AdminPassTransactionResponse(
    @field:Schema(description = "이력 ID") val transactionId: Long,
    @field:Schema(description = "이용권 ID") val passId: Long,
    @field:Schema(description = "이용권 종류") val passType: PassType,
    @field:Schema(description = "이용권 상태 — 취소(CANCELED)된 이용권의 이력도 포함된다(D-059 감사 가능성)")
    val passStatus: PassStatus,
    @field:Schema(description = "증감 수량(+차감/복구, 부호로 방향 구분)") val amount: BigDecimal,
    @field:Schema(description = "차감/복구 사유 코드") val reason: TransactionReason,
    @field:Schema(description = "관리자 메모 — 수동 가감(ADMIN_ADJUST) 등에서 관리자가 남긴 사유. 없으면 null")
    val note: String?,
    @field:Schema(description = "발생 시각") val occurredAt: OffsetDateTime,
) {
    companion object {
        /**
         * **트랜잭션이 열려 있는 서비스 계층 안에서만 호출한다.** [PassTransaction.pass]가 `LAZY`
         * 연관이라 [passId]·[passType]·[passStatus]를 얻으려 `transaction.pass.*`에 접근하는 순간,
         * 트랜잭션 밖이면 `LazyInitializationException`이 난다([PassTransactionResponse.from]과
         * 동일 함정).
         */
        fun from(transaction: PassTransaction): AdminPassTransactionResponse =
            AdminPassTransactionResponse(
                transactionId =
                    requireNotNull(transaction.id) { "저장되지 않은 PassTransaction은 응답으로 변환할 수 없습니다." },
                passId =
                    requireNotNull(transaction.pass.id) { "저장되지 않은 Pass를 참조하는 PassTransaction은 응답으로 변환할 수 없습니다." },
                passType = transaction.pass.type,
                passStatus = transaction.pass.status,
                amount = transaction.amount,
                reason = transaction.reason,
                note = transaction.note,
                occurredAt = transaction.occurredAt,
            )
    }
}
