package com.goldwrestling.attendance

import com.goldwrestling.admin.Admin
import com.goldwrestling.branch.Branch
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.pass.PassTransaction
import com.goldwrestling.schedule.ClassSchedule
import com.goldwrestling.schedule.ClassSession
import com.goldwrestling.schedule.ClassSessionStatus
import com.goldwrestling.schedule.ClassType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * `AttendanceRepositoryTest`·`NoticeRepositoryTest`(06-02)와 이후 06-03(배치 테스트)·06-06~06-08
 * (출석 서비스·컨트롤러·동시성)이 함께 쓰는 출석·수업 픽스처(`BatchFixtures` 관례 — object + 고정
 * 시각 상수 + 최소 생성 함수).
 *
 * [branch]는 단위테스트가 DB 시드에 의존하지 않고 지점을 구성할 때만 쓴다(`PassFixtures.branch()`와
 * 동일 관례) — Testcontainers 통합테스트에서 이 함수로 만든 엔티티를 그대로 저장하면 V2가 이미 심어
 * 둔 "송파점"과 이름이 겹쳐 `uq_branch_name` 위반이 난다. 통합테스트는 `BranchRepository.findByName`
 * 으로 시드된 지점을 재사용한다(`BatchFixtures` 사용처와 동일).
 */
object AttendanceFixtures {
    val FIXED_TIME: OffsetDateTime = OffsetDateTime.parse("2026-08-02T10:00:00+09:00")
    val FIXED_TODAY: LocalDate = LocalDate.of(2026, 8, 2)

    fun branch(name: String = "송파점"): Branch = Branch(name = name)

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

    fun classSchedule(
        branch: Branch,
        dayOfWeek: DayOfWeek,
        classType: ClassType,
        startTime: LocalTime,
        endTime: LocalTime,
        capacity: Int?,
    ): ClassSchedule =
        ClassSchedule(
            branch = branch,
            dayOfWeek = dayOfWeek,
            classType = classType,
            startTime = startTime,
            endTime = endTime,
            capacity = capacity,
            createdAt = FIXED_TIME,
        )

    /** [classSchedule]의 시각·정원을 복사해 [classDate]에 실체화한 `ClassSession`을 만든다(D-094). */
    fun classSession(
        classSchedule: ClassSchedule,
        classDate: LocalDate,
        reservedCount: Int = 0,
        status: ClassSessionStatus = ClassSessionStatus.SCHEDULED,
    ): ClassSession =
        ClassSession(
            classSchedule = classSchedule,
            classDate = classDate,
            classType = classSchedule.classType,
            startTime = classSchedule.startTime,
            endTime = classSchedule.endTime,
            capacity = classSchedule.capacity,
            reservedCount = reservedCount,
            status = status,
            createdAt = FIXED_TIME,
        )

    /** `passTransaction`은 예약제/1:1은 항상 `null`이고, 저녁반 0.5회 차감이 있을 때만 채운다(D-128). */
    fun attendance(
        classSession: ClassSession,
        member: Member,
        status: AttendanceStatus,
        checkedBy: Admin,
        checkedAt: OffsetDateTime = FIXED_TIME,
        passTransaction: PassTransaction? = null,
    ): Attendance =
        Attendance(
            member = member,
            classSession = classSession,
            status = status,
            passTransaction = passTransaction,
            checkedBy = checkedBy,
            checkedAt = checkedAt,
            createdAt = FIXED_TIME,
        )
}
