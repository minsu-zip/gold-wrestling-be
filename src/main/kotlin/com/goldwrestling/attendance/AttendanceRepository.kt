package com.goldwrestling.attendance

import com.goldwrestling.common.projection.MemberDateProjection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AttendanceRepository : JpaRepository<Attendance, Long> {
    /** 특정 수업의 출석 명단 조회용(파생 쿼리) — 관리자 출석 체크 화면이 이 세션의 전체 기록을 본다. */
    fun findAllByClassSessionId(classSessionId: Long): List<Attendance>

    /**
     * 명단 응답 전용 조회(`AttendanceService.getRoster`) — `member`를 `join fetch`로 함께 로딩한다.
     * 명단 응답이 `member.name`을 담으므로 join fetch가 없으면 명단 크기만큼 추가 쿼리가 나간다
     * (`ReservationRepository.findAllByClassSessionIdInAndStatusWithMember` KDoc과 동일 논리).
     * [findAllByClassSessionId](06-02이 만든 파생 쿼리)는 그대로 두되, 명단 조회 경로는 이 메서드를 쓴다.
     */
    @Query(
        "select a from Attendance a join fetch a.member where a.classSession.id = :classSessionId",
    )
    fun findAllByClassSessionIdWithMember(
        @Param("classSessionId") classSessionId: Long,
    ): List<Attendance>

    /**
     * 특정 회원의 특정 세션 출석 기록을 조회한다 — 출석 체크(upsert, D-132) 판정에 쓴다. 존재하면
     * [Attendance.status] UPDATE, 없으면 신규 INSERT로 분기한다.
     */
    fun findByClassSessionIdAndMemberId(
        classSessionId: Long,
        memberId: Long,
    ): Attendance?

    /**
     * 존재 여부만 확인한다 — **이 검사는 사용자에게 친절한 메시지를 주기 위한 것이고 실제 방어선은
     * `uq_attendance_member_session`이다**(`ReservationRepository.existsByMemberIdAndClassDateAndStartTimeAndStatus`와
     * 동일 관례). 이 검사와 실제 INSERT 사이의 경쟁은 DB 유니크 제약이 최종적으로 막는다(D-021).
     */
    fun existsByClassSessionIdAndMemberId(
        classSessionId: Long,
        memberId: Long,
    ): Boolean

    /**
     * CR-03 기준일 후보 ①(D-105) 벌크 조회 — 회원별로 **출석(`ATTENDED`)만** 집계해 가장 최근
     * 수업일을 반환한다(policies §6 "출석(ATTENDED)만 기준으로 한다").
     *
     * **`status` 필터를 빼면 불참이 "마지막 참여일"로 잡혀 부당하게 유예가 갱신된다** — 2주 미사용
     * 차감(§4.3)의 기준일이 실제로는 아무도 참여하지 않은 날짜로 밀리는 회귀가 된다. [memberIds]가
     * 빈 컬렉션이면 빈 결과를 반환한다.
     */
    @Query(
        "select a.member.id as memberId, max(a.classSession.classDate) as date from Attendance a " +
            "where a.member.id in :memberIds and a.status = com.goldwrestling.attendance.AttendanceStatus.ATTENDED " +
            "group by a.member.id",
    )
    fun findLastAttendedClassDates(
        @Param("memberIds") memberIds: Collection<Long>,
    ): List<MemberDateProjection>
}
