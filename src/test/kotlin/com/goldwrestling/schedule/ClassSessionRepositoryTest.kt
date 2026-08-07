package com.goldwrestling.schedule

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * `ClassSessionRepository`의 get-or-create 네이티브 upsert·조건부 UPDATE 4종을 실제
 * PostgreSQL(Testcontainers)에서 증명하는 통합테스트. 애노테이션 조합은 `PassRepositoryTest`와
 * 동일하게 유지한다(conventions §10.1 — 컨텍스트 캐시 재사용).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class ClassSessionRepositoryTest {
    @Autowired
    private lateinit var classSessionRepository: ClassSessionRepository

    @Autowired
    private lateinit var classScheduleRepository: ClassScheduleRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var clock: Clock

    private var fixtureCounter = 0L

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @Test
    fun `insertIfAbsent를 같은 시간표-날짜로 두 번 호출하면 두 번째는 0을 반환하고 class_session 행 수는 1이다`() {
        val schedule = anySchedule()
        val classDate = nextClassDate()

        val first = insertSession(schedule, classDate)
        val second = insertSession(schedule, classDate)

        assertThat(first).isEqualTo(1)
        assertThat(second).isEqualTo(0)
        assertThat(classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, classDate)).isNotNull()
    }

    @Test
    fun `incrementReservedCountIfCapacityAvailable는 정원에 여유가 있으면 1을 반환하고 예약 인원이 1 늘어난다`() {
        val session = persistSession(capacity = 10, reservedCount = 3)

        val updated = classSessionRepository.incrementReservedCountIfCapacityAvailable(session.id!!)

        assertThat(updated).isEqualTo(1)
        val reloaded = classSessionRepository.findById(session.id!!).get()
        assertThat(reloaded.reservedCount).isEqualTo(4)
    }

    @Test
    fun `incrementReservedCountIfCapacityAvailable는 정원이 찼으면 0을 반환하고 예약 인원이 그대로다`() {
        val session = persistSession(capacity = 1, reservedCount = 1)

        val updated = classSessionRepository.incrementReservedCountIfCapacityAvailable(session.id!!)

        assertThat(updated).isEqualTo(0)
        val reloaded = classSessionRepository.findById(session.id!!).get()
        assertThat(reloaded.reservedCount).isEqualTo(1)
    }

    @Test
    fun `incrementReservedCountIfCapacityAvailable는 휴강 세션에 대해 0을 반환한다`() {
        val session = persistCanceledSession(capacity = 10, reservedCount = 0)

        val updated = classSessionRepository.incrementReservedCountIfCapacityAvailable(session.id!!)

        assertThat(updated).isEqualTo(0)
    }

    @Test
    fun `decrementReservedCount는 예약 인원이 0인 세션에 대해 0을 반환한다`() {
        val session = persistSession(capacity = 10, reservedCount = 0)

        val updated = classSessionRepository.decrementReservedCount(session.id!!)

        assertThat(updated).isEqualTo(0)
        val reloaded = classSessionRepository.findById(session.id!!).get()
        assertThat(reloaded.reservedCount).isEqualTo(0)
    }

    @Test
    fun `suspendIfScheduled는 이미 휴강인 세션에 대해 0을 반환한다`() {
        val session = persistCanceledSession(capacity = 10, reservedCount = 0)
        val admin = persistAdmin()

        val updated =
            classSessionRepository.suspendIfScheduled(
                session.id!!,
                "중복 휴강 시도",
                admin,
                OffsetDateTime.now(clock),
            )

        assertThat(updated).isEqualTo(0)
    }

    @Test
    fun `resumeIfCanceled는 정상 진행 중인 세션에 대해 0을 반환한다`() {
        val session = persistSession(capacity = 10, reservedCount = 0)

        val updated = classSessionRepository.resumeIfCanceled(session.id!!)

        assertThat(updated).isEqualTo(0)
    }

    private fun anySchedule(): ClassSchedule = classScheduleRepository.findAllByBranchId(songpaBranchId()).minByOrNull { it.id!! }!!

    private fun songpaBranchId(): Long = branchRepository.findByName("송파점")!!.id!!

    private fun nextClassDate(): LocalDate {
        fixtureCounter++
        return LocalDate.now(clock).plusDays(fixtureCounter)
    }

    private fun insertSession(
        schedule: ClassSchedule,
        classDate: LocalDate,
    ): Int =
        classSessionRepository.insertIfAbsent(
            schedule.id!!,
            classDate,
            schedule.classType.name,
            schedule.startTime,
            schedule.endTime,
            schedule.capacity,
        )

    private fun persistSession(
        capacity: Int?,
        reservedCount: Int,
    ): ClassSession {
        val schedule = anySchedule()
        return classSessionRepository.saveAndFlush(
            ClassSession(
                classSchedule = schedule,
                classDate = nextClassDate(),
                classType = schedule.classType,
                startTime = LocalTime.of(11, 0),
                endTime = LocalTime.of(12, 30),
                capacity = capacity,
                reservedCount = reservedCount,
                status = ClassSessionStatus.SCHEDULED,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun persistCanceledSession(
        capacity: Int?,
        reservedCount: Int,
    ): ClassSession {
        val schedule = anySchedule()
        val admin = persistAdmin()
        return classSessionRepository.saveAndFlush(
            ClassSession(
                classSchedule = schedule,
                classDate = nextClassDate(),
                classType = schedule.classType,
                startTime = LocalTime.of(11, 0),
                endTime = LocalTime.of(12, 30),
                capacity = capacity,
                reservedCount = reservedCount,
                status = ClassSessionStatus.CANCELED,
                canceledAt = OffsetDateTime.now(clock),
                cancelReason = "테스트 휴강",
                canceledByAdmin = admin,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun persistAdmin(): Admin {
        fixtureCounter++
        return adminRepository.saveAndFlush(
            Admin(
                name = "관리자",
                loginId = "admin-session-$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }
}
