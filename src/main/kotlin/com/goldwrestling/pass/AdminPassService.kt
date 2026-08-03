package com.goldwrestling.pass

import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.member.MemberNotFoundException
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.pass.dto.PassResponse
import com.goldwrestling.pass.dto.RegisterPassRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 관리자 이용권 등록(PASS-01, PASS-02). 등록과 초기 부여 이력(`INITIAL_GRANT`)이 **같은
 * `@Transactional` 메서드** 안에서 일어나 "이력 없는 잔여 변경"이 원천적으로 불가능하다(CLAUDE.md 규칙 6).
 *
 * 회원 상태(`PENDING`/`ON_LEAVE`/`INACTIVE`)로 등록을 막지 않는다 — policies·requirements 어디에도
 * 그런 제한이 없고, D-034·D-044가 상태 전이에 관리자 재량을 남긴 선례를 따른다. 이 판단은 계획
 * 단계에서 사용자가 확정했다(2026-08-03, plan-phase AskUserQuestion — "제한 없음, 관리자 재량"
 * 선택, D-068).
 */
@Service
@Transactional(readOnly = true)
class AdminPassService(
    private val memberRepository: MemberRepository,
    private val adminRepository: AdminRepository,
    private val passRepository: PassRepository,
    private val passTransactionRepository: PassTransactionRepository,
    private val clock: Clock,
) {
    /**
     * 이용권을 등록한다. 처리 순서: 회원·관리자 조회 → 시작일 기본값 채우기(D-055) → `Pass.register`로
     * 타입별 규칙 검증·조립 → 저장 → 횟수제면 같은 트랜잭션에서 `INITIAL_GRANT` 이력 저장 → 응답 변환.
     *
     * `branch`는 등록 시점 회원의 소속 지점을 그대로 쓴다(D-067 "발급 지점 귀속").
     */
    @Transactional
    fun register(
        memberId: Long,
        request: RegisterPassRequest,
        adminId: Long,
    ): PassResponse {
        val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        val admin =
            adminRepository.findById(adminId).orElseThrow {
                IllegalStateException("이용권을 등록하려는 관리자(id=$adminId)를 찾을 수 없습니다.")
            }

        val today = LocalDate.now(clock)
        val now = OffsetDateTime.now(clock)
        val startDate = request.startDate ?: today

        val pass =
            Pass.register(
                member = member,
                branch = member.branch,
                type = request.type,
                startDate = startDate,
                term = request.term,
                initialCount = request.initialCount,
                registeredBy = admin,
                now = now,
            )
        passRepository.save(pass)

        if (request.type != PassType.EVENING_MEMBERSHIP) {
            val initialCount = requireNotNull(request.initialCount) { "횟수제 등록은 Pass.register가 initialCount 존재를 이미 강제했다." }
            passTransactionRepository.save(
                PassTransaction(
                    pass = pass,
                    amount = initialCount,
                    reason = TransactionReason.INITIAL_GRANT,
                    note = null,
                    admin = admin,
                    occurredAt = now,
                ),
            )
        }

        return PassResponse.from(pass, today)
    }
}
