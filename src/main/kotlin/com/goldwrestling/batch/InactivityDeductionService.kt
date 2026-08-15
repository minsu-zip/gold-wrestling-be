package com.goldwrestling.batch

import com.goldwrestling.pass.PassNotFoundException
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassTransaction
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.TransactionReason
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 미사용 차감 1회를 실제로 반영하는 트랜잭션 단위 서비스(BATCH-01, D-109, policies §4.3) —
 * 배치가 회원의 잔여 횟수를 바꾸는 유일한 지점이다. Core Value("잔여 = 실제 사용 가능 횟수")가
 * 여기서 깨지면 나머지가 무의미하다.
 *
 * **회당 재선택이 [deductOnce]의 계약이다** — 호출부(05-06 `InactivityBatchRunner`)는 부족분만큼
 * 이 메서드를 반복 호출하고, 매 호출이 [PassRepository.findDeductibleSessionPasses]로 후보를
 * 다시 조회한다(D-109, RESEARCH Pitfall 1). 후보 리스트를 호출부가 캐시해 여러 회차에 걸쳐
 * 재사용하면 앞선 호출로 잔여가 0이 된 장을 다시 고르는 사고가 난다.
 *
 * **부족분을 다른 장으로 이월하지 않는다** — 한 차감 이벤트는 만료가 가장 임박한 한 장에서만
 * 일어난다(합산 금지 D-091과 일관). 잔여가 차감량(1회)보다 적으면 잔여만큼만 차감한다
 * (0.5 → 0, policies §4.3 "잔여만큼만 차감").
 *
 * 반환 `false`는 실패가 아니라 정상 경로다 — 차감 대상 소진, 조건부 UPDATE 경쟁 패배 둘 다
 * 다음 배치 실행이 상태 기반으로 자연 보정한다(D-106).
 *
 * 트랜잭션 애노테이션이 [deductOnce]에만 있는 이유: 배치 루프 전체를 하나의 트랜잭션으로 감싸면
 * 한 회원의 실패가 앞서 처리된 회원 전원을 함께 롤백시키고, 같은 클래스 내부에서 이 메서드를
 * 호출하면 프록시를 우회해 트랜잭션 경계가 아예 생기지 않는다(self-invocation) — 그래서 별도
 * 스프링 빈으로 분리했다(05-01 결정 C).
 */
@Service
@Transactional(readOnly = true)
class InactivityDeductionService(
    private val passRepository: PassRepository,
    private val passTransactionRepository: PassTransactionRepository,
    private val clock: Clock,
) {
    /**
     * 회원 [memberId]의 미사용 차감을 1회 반영한다.
     *
     * 흐름: 오늘 날짜 기준 차감 가능한 `SESSION_PASS` 재조회 → 만료 임박순 첫 장 선택 →
     * `min(1회, 잔여)`로 차감량 산출 → 조건부 UPDATE(D-021) → 재조회 → `INACTIVITY` 이력 저장.
     *
     * 반환 `true`면 차감했다는 뜻이고, `false`면 차감 가능한 장이 없거나(대상 소진) 조건부
     * UPDATE가 0행이었다(경쟁 패배)는 뜻이다 — 어느 쪽도 예외를 던지지 않는다.
     */
    @Transactional
    fun deductOnce(memberId: Long): Boolean {
        val today = LocalDate.now(clock)
        val candidate =
            passRepository.findDeductibleSessionPasses(memberId, today).firstOrNull()
                ?: return false

        val remaining =
            requireNotNull(candidate.remainingCount) { "차감 후보 SESSION_PASS의 remainingCount는 null일 수 없습니다." }
        val deductAmount = minOf(ONE_SESSION, remaining)
        val passId = requireNotNull(candidate.id) { "차감 후보 조회는 항상 저장된 Pass만 반환합니다." }

        if (passRepository.adjustRemainingCount(passId, deductAmount.negate()) == 0) {
            return false
        }

        val refreshedPass = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }

        passTransactionRepository.save(
            PassTransaction(
                pass = refreshedPass,
                amount = deductAmount.negate(),
                reason = TransactionReason.INACTIVITY,
                note = null,
                admin = null,
                member = null,
                occurredAt = OffsetDateTime.now(clock),
            ),
        )

        return true
    }

    companion object {
        private val ONE_SESSION = BigDecimal("1.0")
    }
}
