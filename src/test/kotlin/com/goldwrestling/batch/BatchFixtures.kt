package com.goldwrestling.batch

import com.goldwrestling.admin.Admin
import com.goldwrestling.branch.Branch
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassTransaction
import com.goldwrestling.pass.PassType
import com.goldwrestling.pass.TransactionReason
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 배치 벌크 조회 통합테스트(`InactivityBatchQueryTest` 등)가 공유하는 회원·이용권·이력 픽스처
 * (`PassFixtures` 관례 — object + 고정 시각 상수 + 최소 생성 함수). 지점·회원·이용권 등록 고유값
 * (kakaoId·loginId)은 호출부가 넘긴다 — 유니크 제약(`uq_member_kakao_id`·`uq_admin_login_id`)을
 * 이 픽스처 혼자 책임질 수 없어 각 테스트의 카운터가 소유한다(`PassDeductionCandidateTest` 관례).
 *
 * 고정 시각은 `TestClockConfiguration`의 기본값(`2026-08-02T10:00:00+09:00`)과 **같은 값**으로
 * 맞춰 뒀다 — 테스트가 clock을 이 시각으로 리셋하면 `LocalDate.now(clock)`이 항상 [FIXED_TODAY]가
 * 되어 `endDate == today` 같은 경계 케이스를 상대 날짜 계산 없이 그대로 쓸 수 있다.
 */
object BatchFixtures {
    val FIXED_TIME: OffsetDateTime = OffsetDateTime.parse("2026-08-02T10:00:00+09:00")
    val FIXED_TODAY: LocalDate = LocalDate.of(2026, 8, 2)

    fun admin(loginId: String): Admin =
        Admin(
            name = "관리자",
            loginId = loginId,
            passwordHash = "{noop}not-used-in-this-test",
            createdAt = FIXED_TIME,
        )

    fun member(
        branch: Branch,
        kakaoId: Long,
        status: MemberStatus = MemberStatus.ACTIVE,
    ): Member =
        Member(
            branch = branch,
            name = "회원$kakaoId",
            phoneNumber = "010000$kakaoId",
            status = status,
            kakaoId = kakaoId,
            createdAt = FIXED_TIME,
        )

    /** `SESSION_PASS` 생성 — 등록 시각·유효기간·잔여·상태를 배치 테스트가 개별 지정한다. */
    fun sessionPass(
        member: Member,
        branch: Branch,
        registeredBy: Admin,
        remainingCount: BigDecimal,
        endDate: LocalDate,
        createdAt: OffsetDateTime = FIXED_TIME,
        status: PassStatus = PassStatus.ACTIVE,
    ): Pass =
        Pass(
            member = member,
            branch = branch,
            registeredBy = registeredBy,
            type = PassType.SESSION_PASS,
            status = status,
            startDate = endDate.minusYears(1).plusDays(1),
            endDate = endDate,
            remainingCount = remainingCount,
            createdAt = createdAt,
        )

    /** [type]이 `SESSION_PASS`가 아닌 이용권(LESSON_PASS 미보유 회원 필터 검증 등)이 필요할 때 쓴다. */
    fun pass(
        member: Member,
        branch: Branch,
        registeredBy: Admin,
        type: PassType,
        remainingCount: BigDecimal?,
        endDate: LocalDate,
        createdAt: OffsetDateTime = FIXED_TIME,
        status: PassStatus = PassStatus.ACTIVE,
    ): Pass =
        Pass(
            member = member,
            branch = branch,
            registeredBy = registeredBy,
            type = type,
            status = status,
            startDate = endDate.minusYears(1).plusDays(1),
            endDate = endDate,
            remainingCount = remainingCount,
            createdAt = createdAt,
        )

    /** `PassTransaction` 생성 — 사유·수량·발생 시각을 배치 테스트가 개별 지정한다. 배치 이력은 주체가 둘 다 null이다(V9). */
    fun passTransaction(
        pass: Pass,
        amount: BigDecimal,
        reason: TransactionReason,
        occurredAt: OffsetDateTime = FIXED_TIME,
    ): PassTransaction =
        PassTransaction(
            pass = pass,
            amount = amount,
            reason = reason,
            note = null,
            admin = null,
            member = null,
            occurredAt = occurredAt,
        )
}
