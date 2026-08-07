package com.goldwrestling.schedule

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * `ClassSessionService.getOrCreate`의 get-or-create 경쟁 안전성을 검증한다(D-094, T-04-22).
 * 스레드 20개가 같은 `(schedule, classDate)`로 동시에 호출해도 예외 없이 20건 전부 성공하고,
 * `class_session` 행은 정확히 1개만 남아야 한다.
 *
 * 애노테이션 조합은 `PassCancellationConcurrencyTest`와 동일하게 유지한다(conventions §10.1).
 * **이 클래스·메서드에는 `@Transactional`을 붙이지 않는다** — 각 스레드가 별도 트랜잭션이어야
 * `insertIfAbsent`의 `ON CONFLICT DO NOTHING` 경쟁이 실제로 재현된다(add-domain-test §2·§4).
 * 트랜잭션 롤백에 기대지 않으므로 [cleanUp]에서 이 테스트가 만든 `class_session` 행을 직접 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class ClassSessionConcurrencyTest {
    @Autowired
    private lateinit var classSessionService: ClassSessionService

    @Autowired
    private lateinit var classScheduleRepository: ClassScheduleRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    /** 실제 예약·조회 흐름과 겹치지 않도록 먼 미래 날짜를 쓴다(2099년은 다른 테스트가 쓰지 않는다). */
    private val classDate: LocalDate = LocalDate.of(2099, 3, 2)

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @AfterEach
    fun cleanUp() {
        jdbcClient.sql("delete from class_session where class_date = :classDate").param("classDate", classDate).update()
    }

    @Test
    fun `스레드 20개가 동시에 같은 시간표-날짜로 getOrCreate를 호출하면 성공 20건, DB 행 1개다`() {
        val schedule = songpaSchedule()
        // classDate가 schedule.dayOfWeek와 실제로 같은 요일이어야 하는 도메인 제약은 없다 —
        // ClassSession.classDate는 자유 컬럼이라(D-094) 동시성 재현 목적의 임의 미래 날짜로도 충분하다.
        assertThat(classDate.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)

        val threadCount = 20
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val sessionIds = Collections.synchronizedList(mutableListOf<Long>())
        val returnedSessions = Collections.synchronizedList(mutableListOf<ClassSession>())

        repeat(threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    val session = classSessionService.getOrCreate(schedule, classDate)
                    sessionIds.add(session.id!!)
                    returnedSessions.add(session)
                    successCount.incrementAndGet()
                } catch (e: Throwable) {
                    failures.add(e)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val completed = doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()
        assertThat(completed).isTrue()

        assertThat(failures).isEmpty()
        assertThat(successCount.get()).isEqualTo(threadCount)
        // 스레드 20개가 반환받은 세션 id가 전부 같은 값이어야 한다 — 서로 다른 행을 각자 만든 게 아니다.
        assertThat(sessionIds.toSet()).hasSize(1)

        val rowCount =
            jdbcClient
                .sql("select count(*) from class_session where class_schedule_id = :scheduleId and class_date = :classDate")
                .param("scheduleId", schedule.id)
                .param("classDate", classDate)
                .query(Long::class.java)
                .single()
        assertThat(rowCount).isEqualTo(1L)

        // 반환된 세션은 시간표에서 복사한 값을 그대로 갖는다(D-094) — 20개 스레드가 모두 같은 값을 본다.
        val sample = returnedSessions.first()
        assertThat(sample.startTime).isEqualTo(schedule.startTime)
        assertThat(sample.endTime).isEqualTo(schedule.endTime)
        assertThat(sample.capacity).isEqualTo(schedule.capacity)
        assertThat(sample.classType).isEqualTo(schedule.classType)
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    /** V7 시드가 심어 둔 실제 정기 시간표 1행 — 월요일 저녁반(EVENING) 19:00. */
    private fun songpaSchedule(): ClassSchedule =
        classScheduleRepository
            .findAllByBranchId(songpaBranch().id!!)
            .first { it.dayOfWeek == DayOfWeek.MONDAY && it.classType == ClassType.EVENING && it.startTime.hour == 19 }
}
