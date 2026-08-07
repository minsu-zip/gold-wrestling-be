package com.goldwrestling.pass

import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.member.MemberNotFoundException
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.pass.dto.AdjustPassRequest
import com.goldwrestling.pass.dto.CancelPassRequest
import com.goldwrestling.pass.dto.ChangePassPeriodRequest
import com.goldwrestling.pass.dto.PassResponse
import com.goldwrestling.pass.dto.RegisterPassRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
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
                    member = null,
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
                member = null,
                occurredAt = now,
            ),
        )

        return PassResponse.from(refreshedPass, today)
    }

    /**
     * 기간·유효기간 통합 수정(PASS-04·PASS-07, D-057, D-062). 저녁반 기간·횟수권 유효기간 수정이
     * 이 하나의 메서드로 처리된다 — 타입별 차이는 `Pass.resolvePeriodChange`가 서버에서 강제한다.
     *
     * 두 관리자의 동시 기간 수정(WF-03-01)을 D-021 관례(조건부 갱신)로 막는다 — 사전 판정
     * (`Pass.resolvePeriodChange`)은 정확한 실패 사유를 주기 위한 것일 뿐 방어선이 아니고, 실제
     * 반영과 경쟁 통제는 `PassRepository.changePeriodIfUnchanged`의 compare-and-swap 조건부
     * UPDATE가 한다(D-072). 처리 순서: 조회 → 전값 보관 → 사전 판정으로 적용될 (시작일, 종료일)
     * 계산 → 변화 없음(D-069)이면 DB를 건드리지 않고 반환 → 조건부 UPDATE → 반환 0이면 재조회해
     * 취소됐는지/경쟁에서 졌는지 구분해 정확한 예외로 변환 → 재조회한 영속 `Pass`로
     * `PassPeriodChange` 이력 저장(같은 트랜잭션) → 응답 변환.
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

        val (newStartDate, newEndDate) = pass.resolvePeriodChange(request.newStartDate, request.newEndDate)

        if (newStartDate == previousStartDate && newEndDate == previousEndDate) {
            // 변화 없음 재전송(D-069) — DB를 건드리지 않고 그대로 반환한다.
            return PassResponse.from(pass, today)
        }

        val updated =
            passRepository.changePeriodIfUnchanged(
                id = passId,
                newStartDate = newStartDate,
                newEndDate = newEndDate,
                expectedStartDate = previousStartDate,
                expectedEndDate = previousEndDate,
            )
        if (updated == 0) {
            // 반환 0의 원인은 둘 중 하나 — 그 사이 취소됐거나(경쟁), 다른 트랜잭션이 기간을
            // 먼저 바꿨다(동시 수정). 재조회해서 원인을 구분한다.
            val reloaded = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }
            if (reloaded.status == PassStatus.CANCELED) {
                throw PassAlreadyCanceledException()
            }
            throw PassStateConflictException("이용권 기간이 방금 변경되어 반영하지 못했습니다. 다시 시도해 주세요.")
        }

        // 벌크 UPDATE(clearAutomatically = true) 직후 pass는 준영속 상태다 — 재조회한 영속
        // 엔티티로 이력을 저장한다(RESEARCH Pitfall 4, TokenService.rotate와 동일 함정).
        val refreshed = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }
        passPeriodChangeRepository.save(
            PassPeriodChange(
                pass = refreshed,
                previousStartDate = previousStartDate,
                previousEndDate = previousEndDate,
                newStartDate = newStartDate,
                newEndDate = newEndDate,
                reason = request.reason.trim(),
                admin = admin,
                occurredAt = now,
            ),
        )

        return PassResponse.from(refreshed, today)
    }

    /**
     * 등록 취소(PASS-08, D-059, D-065). 물리 삭제를 하지 않고 상태 전환 + 상쇄 이력으로 처리해
     * "잔여 = 이력 합계" 불변식을 취소된 이용권에도 유지한다.
     *
     * 두 관리자의 동시 취소(T-03-38)를 D-021 관례(조건부 갱신)로 막는다 — 사전 판정
     * (`Pass.resolveCancellationOffset`)은 정확한 실패 사유를 주기 위한 것일 뿐 방어선이 아니고,
     * 실제 상태 전환과 경쟁 통제는 `PassRepository.cancelIfNotCanceled`의 조건부 UPDATE가
     * 한다(D-072) — 상쇄 수량이 0인 분기(기간제·잔여 0 횟수권)도 예외 없이 이 조건부 UPDATE를
     * 거친다.
     *
     * 처리 순서: 조회 → 사전 판정으로 상쇄 수량 산출(잔여는 아직 안 바뀜) →
     * `cancelIfNotCanceled`로 상태·취소 메타데이터를 조건부 반영(반환 0이면 경쟁 패배 →
     * [PassAlreadyCanceledException]) → 상쇄 수량이 0이 아니면 `zeroRemainingCount`로 잔여를
     * 조건부로 0으로 만듦(반환 0이면 그 사이 잔여가 바뀐 것이므로 [PassStateConflictException],
     * 트랜잭션 롤백으로 상태 전환도 함께 되돌아간다) → 재조회한 영속 `Pass`로
     * `REGISTRATION_CANCELED` 상쇄 이력을 저장한다(같은 트랜잭션, CLAUDE.md 규칙 6).
     */
    @Transactional
    fun cancel(
        passId: Long,
        request: CancelPassRequest,
        adminId: Long,
    ): PassResponse {
        val pass = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }
        val admin =
            adminRepository.findById(adminId).orElseThrow {
                IllegalStateException("이용권을 취소하려는 관리자(id=$adminId)를 찾을 수 없습니다.")
            }
        val today = LocalDate.now(clock)
        val now = OffsetDateTime.now(clock)
        val reason = request.reason.trim()

        val offset = pass.resolveCancellationOffset()

        val statusUpdated = passRepository.cancelIfNotCanceled(passId, reason, admin, now)
        if (statusUpdated == 0) {
            // 사전 판정 이후 다른 트랜잭션이 먼저 취소를 확정한 것 — 경쟁 패배.
            throw PassAlreadyCanceledException()
        }

        if (offset.compareTo(BigDecimal.ZERO) != 0) {
            val expectedRemaining = offset.negate()
            val updated = passRepository.zeroRemainingCount(passId, expectedRemaining)
            if (updated == 0) {
                throw PassStateConflictException("이용권 잔여가 방금 변경되어 취소를 완료하지 못했습니다. 다시 시도해 주세요.")
            }
        }

        // 조건부 UPDATE(clearAutomatically = true) 이후 pass는 준영속 상태다 — 재조회한 영속
        // 엔티티로 응답을 만들고, 상쇄 이력이 있다면 그 엔티티로 저장한다(RESEARCH Pitfall 4,
        // TokenService.rotate와 동일 함정).
        val refreshed = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }

        if (offset.compareTo(BigDecimal.ZERO) != 0) {
            passTransactionRepository.save(
                PassTransaction(
                    pass = refreshed,
                    amount = offset,
                    reason = TransactionReason.REGISTRATION_CANCELED,
                    note = reason,
                    admin = admin,
                    member = null,
                    occurredAt = now,
                ),
            )
        }

        return PassResponse.from(refreshed, today)
    }

    /**
     * 관리자 이용권 목록 조회 (ROADMAP 성공기준 6 — 관리자 화면 구분 표시). 조회 전용이라 클래스
     * 기본 `@Transactional(readOnly = true)`를 그대로 쓰고 별도 애노테이션을 붙이지 않는다(D-020).
     *
     * 회원 존재를 먼저 확인해 "없는 회원 = 404"와 "이용권 없는 회원 = 빈 배열"을 구분한다.
     * **취소된 이용권을 제외하지 않는다** — 관리자 화면은 구분 표시가 요구사항이라
     * `displayStatus`로 구분해 그대로 노출한다. 반대로 회원 본인 조회(03-10)는 취소된 이용권을
     * 결과에서 제외한다(D-058) — 취소 사유 같은 관리자 전용 정보가 회원에게 도달하지 않게 하기
     * 위해서다.
     */
    fun getMemberPasses(memberId: Long): List<PassResponse> {
        memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        val today = LocalDate.now(clock)
        return passRepository
            .findAllByMemberIdOrderByStartDateDescIdDesc(memberId)
            .map { PassResponse.from(it, today) }
    }
}
