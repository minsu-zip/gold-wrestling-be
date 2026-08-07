package com.goldwrestling.schedule

import com.goldwrestling.admin.Admin
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * `ClassSession`의 get-or-create(네이티브 upsert)와 상태 전환(조건부 UPDATE) 4종을 담는다.
 * 조건부 UPDATE 관례는 `PassRepository.adjustRemainingCount`를 그대로 이식한다(D-021).
 */
interface ClassSessionRepository : JpaRepository<ClassSession, Long> {
    fun findByClassScheduleIdAndClassDate(
        scheduleId: Long,
        classDate: LocalDate,
    ): ClassSession?

    /** 주간 보드·2주 조회(D-095) — 날짜 범위로 이미 생성된 세션을 한 번에 가져온다. */
    fun findAllByClassDateBetween(
        from: LocalDate,
        to: LocalDate,
    ): List<ClassSession>

    /**
     * "필요할 때 생성"(D-094)의 get-or-create를 경쟁 안전하게 구현한다 — **이 저장소 최초의
     * `nativeQuery` 사용**이다. `save()` 후 `DataIntegrityViolationException`을 catch하는 방식은
     * Hibernate가 flush 시점 제약 위반 이후 영속성 컨텍스트를 신뢰할 수 없는 상태로 만들 수 있어
     * (04-RESEARCH.md Pitfall 1) 예외가 애초에 나지 않는 upsert를 쓴다.
     *
     * 반환 0은 실패가 아니라 **"이미 존재한다"는 상태 정보**다 — 호출부는 반환값을 보지 않고
     * 항상 [findByClassScheduleIdAndClassDate]로 재조회한다(`(class_schedule_id, class_date)` 유니크
     * 제약 `uq_class_session`, V6, 이 upsert의 최종 방어선).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            insert into class_session
                (class_schedule_id, class_date, class_type, start_time, end_time, capacity, status, created_at)
            values (:scheduleId, :classDate, :classType, :startTime, :endTime, :capacity, 'SCHEDULED', now())
            on conflict (class_schedule_id, class_date) do nothing
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("scheduleId") scheduleId: Long,
        @Param("classDate") classDate: LocalDate,
        @Param("classType") classType: String,
        @Param("startTime") startTime: LocalTime,
        @Param("endTime") endTime: LocalTime,
        @Param("capacity") capacity: Int?,
    ): Int

    /**
     * 예약 인원을 조건부로 1 증가시킨다(RESV-06, D-021 — `PassRepository.adjustRemainingCount`와
     * 동일 관례). `status = SCHEDULED` 조건으로 휴강 세션에 대한 증가를 막고,
     * `reservedCount + 1 <= capacity` 조건으로 정원 초과 증가를 DB가 거부한다.
     *
     * 반환값은 갱신된 행 수 — **0이면 정원이 찼거나 그 사이 휴강 상태로 바뀐 것**이다. 호출부는
     * `ReservationCapacityExceededException`으로 변환한다. `flushAutomatically`/`clearAutomatically`가
     * 왜 필요한지는 `PassRepository.adjustRemainingCount`와 같다 — 벌크 UPDATE는 영속성 컨텍스트를
     * 거치지 않고 DB에 직접 SQL을 보내므로, 이전 더티 상태를 먼저 flush해야 최신 상태 위에서 정확히
     * 실행되고(`flushAutomatically`), 실행 후 컨텍스트를 비워야 1차 캐시의 갱신 전 스냅샷을 다시
     * 읽는 사고를 막는다(`clearAutomatically`). 호출부는 이 쿼리 실행 전에 이후 로직에 필요한
     * 스칼라 값을 지역 변수로 미리 꺼내둬야 한다 — 실행 직후 준영속 상태가 된 엔티티의 LAZY
     * 연관에 접근하면 `LazyInitializationException`이 난다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update ClassSession s set s.reservedCount = s.reservedCount + 1 " +
            "where s.id = :id and s.status = com.goldwrestling.schedule.ClassSessionStatus.SCHEDULED " +
            "and s.capacity is not null and s.reservedCount + 1 <= s.capacity",
    )
    fun incrementReservedCountIfCapacityAvailable(
        @Param("id") id: Long,
    ): Int

    /**
     * 예약 인원을 조건부로 1 감소시킨다(취소·변경 시 정원 복구). `reservedCount > 0` 조건으로
     * 음수로 내려가는 갱신을 DB가 거부한다. 반환값·flush/clear 이유는
     * [incrementReservedCountIfCapacityAvailable]와 같다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update ClassSession s set s.reservedCount = s.reservedCount - 1 " +
            "where s.id = :id and s.reservedCount > 0",
    )
    fun decrementReservedCount(
        @Param("id") id: Long,
    ): Int

    /**
     * 휴강 처리를 조건부로 반영한다(policies §7, RESV-09). `status = SCHEDULED` 조건으로 이미
     * 휴강인 세션의 재처리·경쟁 중복 반영을 DB에서 막는다(D-021). **0이면 이미 휴강 처리된
     * 것**이다 — 호출부는 [ClassSessionCanceledException]으로 변환한다. 반환값·flush/clear 이유는
     * [incrementReservedCountIfCapacityAvailable]와 같다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update ClassSession s set s.status = com.goldwrestling.schedule.ClassSessionStatus.CANCELED, " +
            "s.canceledAt = :canceledAt, s.cancelReason = :reason, s.canceledByAdmin = :canceledBy " +
            "where s.id = :id and s.status = com.goldwrestling.schedule.ClassSessionStatus.SCHEDULED",
    )
    fun suspendIfScheduled(
        @Param("id") id: Long,
        @Param("reason") reason: String,
        @Param("canceledBy") canceledBy: Admin,
        @Param("canceledAt") canceledAt: OffsetDateTime,
    ): Int

    /**
     * 휴강 해제를 조건부로 반영한다 — `ck_class_session_cancellation`(V6)이 취소 메타데이터
     * 3개(`canceled_at`/`cancel_reason`/`canceled_by_admin_id`)의 완전성을 강제하므로 상태 전환 시
     * 반드시 3개를 함께 되돌린다. `status = CANCELED` 조건으로 휴강 상태가 아닌 세션의 재처리를
     * 막는다. **0이면 이미 휴강 상태가 아닌 것**이다 — 호출부는 [ClassSessionNotCanceledException]으로
     * 변환한다. 반환값·flush/clear 이유는 [incrementReservedCountIfCapacityAvailable]와 같다.
     *
     * (D-094 "휴강 철회는 예약을 자동 복원하지 않는다") — 취소된 예약 복원은 이 메서드의 책임이 아니다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update ClassSession s set s.status = com.goldwrestling.schedule.ClassSessionStatus.SCHEDULED, " +
            "s.canceledAt = null, s.cancelReason = null, s.canceledByAdmin = null " +
            "where s.id = :id and s.status = com.goldwrestling.schedule.ClassSessionStatus.CANCELED",
    )
    fun resumeIfCanceled(
        @Param("id") id: Long,
    ): Int
}
