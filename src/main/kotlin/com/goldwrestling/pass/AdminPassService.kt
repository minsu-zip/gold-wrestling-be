package com.goldwrestling.pass

import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.member.MemberNotFoundException
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.pass.dto.AdjustPassRequest
import com.goldwrestling.pass.dto.ChangePassPeriodRequest
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
    private val passPeriodChangeRepository: PassPeriodChangeRepository,
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

    /**
     * 관리자 수동 가감(`ADMIN_ADJUST`, policies §4.2a, D-056). 사전 판정(엔티티)과 원자적 반영(DB)의
     * 역할이 다르며 둘 다 필요하다(D-021) — [Pass.validateAdjustment]는 사용자에게 정확한 실패
     * 사유를 주기 위한 사전 판정일 뿐 방어선이 아니고, 실제 반영과 경쟁 통제는
     * [PassRepository.adjustRemainingCount]의 조건부 UPDATE가 한다.
     *
     * 처리 순서: 조회 → 사전 판정 → 조건부 UPDATE → (반환 0이면 재조회 후 정확한 예외로 변환) →
     * 재조회한 영속 `Pass`로 `ADMIN_ADJUST` 이력 저장(같은 트랜잭션, CLAUDE.md 규칙 6) → 응답 변환.
     */
    @Transactional
    fun adjust(
        passId: Long,
        request: AdjustPassRequest,
        adminId: Long,
    ): PassResponse {
        val pass = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }
        pass.validateAdjustment(request.amount)

        // 벌크 UPDATE(clearAutomatically = true) 호출 전에 이후 필요한 스칼라 값을 지역 변수로
        // 옮긴다 — 실행 직후 pass가 준영속 상태가 되어 LAZY 연관에 접근하면
        // LazyInitializationException이 난다(RESEARCH Pitfall 4, TokenService.rotate와 동일 함정).
        val admin =
            adminRepository.findById(adminId).orElseThrow {
                IllegalStateException("이용권을 가감하려는 관리자(id=$adminId)를 찾을 수 없습니다.")
            }
        val today = LocalDate.now(clock)
        val now = OffsetDateTime.now(clock)

        val updated = passRepository.adjustRemainingCount(passId, request.amount)
        val refreshedPass =
            if (updated == 0) {
                // 갱신 행 수 0 = 경쟁에서 졌거나(동시 가감) 사전 판정 이후 상태(취소·잔여)가 바뀐 것.
                // 재조회 후 판정을 다시 돌려 정확한 도메인 예외로 변환한다.
                val reloaded = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }
                reloaded.validateAdjustment(request.amount)
                throw PassStateConflictException("이용권 잔여가 방금 변경되어 가감을 반영하지 못했습니다. 다시 시도해 주세요.")
            } else {
                passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }
            }

        passTransactionRepository.save(
            PassTransaction(
                pass = refreshedPass,
                amount = request.amount,
                reason = TransactionReason.ADMIN_ADJUST,
                note = request.note.trim(),
                admin = admin,
                occurredAt = now,
            ),
        )

        return PassResponse.from(refreshedPass, today)
    }

    /**
     * 기간·유효기간 통합 수정(PASS-04·PASS-07, D-057, D-062). 저녁반 기간·횟수권 유효기간 수정이
     * 이 하나의 메서드로 처리된다 — 타입별 차이는 `Pass.changePeriod`가 서버에서 강제한다.
     *
     * 처리 순서: 조회 → **`changePeriod` 호출 전 전값 보관**(호출 후에는 되찾을 방법이 없다) →
     * `pass.changePeriod` 호출(규칙 위반은 여기서 예외) → 전값·후값이 하나라도 달라졌으면 같은
     * 트랜잭션에서 `PassPeriodChange` 이력 저장(D-069 — 변화가 없으면 이력을 남기지 않는다) →
     * 응답 변환. `pass`는 영속 상태라 변경 감지로 flush되므로 별도 `passRepository.save`를 넣지
     * 않는다.
     */
    @Transactional
    fun changePeriod(
        passId: Long,
        request: ChangePassPeriodRequest,
        adminId: Long,
    ): PassResponse {
        val pass = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }
        val admin =
            adminRepository.findById(adminId).orElseThrow {
                IllegalStateException("이용권 기간을 수정하려는 관리자(id=$adminId)를 찾을 수 없습니다.")
            }
        val today = LocalDate.now(clock)
        val now = OffsetDateTime.now(clock)

        val previousStartDate = pass.startDate
        val previousEndDate = pass.endDate

        pass.changePeriod(request.newStartDate, request.newEndDate)

        if (previousStartDate != pass.startDate || previousEndDate != pass.endDate) {
            passPeriodChangeRepository.save(
                PassPeriodChange(
                    pass = pass,
                    previousStartDate = previousStartDate,
                    previousEndDate = previousEndDate,
                    newStartDate = pass.startDate,
                    newEndDate = pass.endDate,
                    reason = request.reason.trim(),
                    admin = admin,
                    occurredAt = now,
                ),
            )
        }

        return PassResponse.from(pass, today)
    }
}
