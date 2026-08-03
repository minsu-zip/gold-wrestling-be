package com.goldwrestling.pass

import com.goldwrestling.admin.Admin
import com.goldwrestling.branch.Branch
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberStatus
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * `Pass` 단위테스트 3종(`PassAdjustmentPolicyTest`·`PassRegistrationTest`·`PassDisplayStatusTest`)이
 * 동일하게 쓰는 회원·지점·관리자 픽스처와 고정 시각. 타입별 `Pass` 조립(직접 생성자 vs `Pass.register`)은
 * 파일마다 형태가 달라 각 테스트 파일이 계속 담당한다(conventions §1 — 세 파일 모두가 실제로 쓰는
 * 부분만 추출).
 */
object PassFixtures {
    val FIXED_TIME: OffsetDateTime = OffsetDateTime.parse("2026-08-01T00:00:00+09:00")
    val FIXED_TODAY: LocalDate = LocalDate.of(2026, 8, 1)

    fun branch(): Branch = Branch(name = "송파점")

    fun member(): Member =
        Member(
            branch = branch(),
            name = "홍길동",
            phoneNumber = "01012345678",
            status = MemberStatus.ACTIVE,
            kakaoId = 1L,
            createdAt = FIXED_TIME,
        )

    fun admin(): Admin =
        Admin(
            name = "관리자",
            loginId = "admin1",
            passwordHash = "hash",
            createdAt = FIXED_TIME,
        )
}
