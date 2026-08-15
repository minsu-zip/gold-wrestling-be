package com.goldwrestling.batch

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * `BatchExecution`의 저장·조회와 `ck_batch_execution_trigger` CHECK 제약을 실제 PostgreSQL
 * (Testcontainers)에서 증명하는 통합테스트. 애노테이션 조합은 `PassRepositoryTest`와 동일하게
 * 유지한다(conventions §10.1 — 컨텍스트 캐시 재사용).
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class BatchExecutionRepositoryTest {
    @Autowired
    private lateinit var batchExecutionRepository: BatchExecutionRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var clock: Clock

    private var fixtureCounter = 9000L

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @Test
    fun `save 후 findById로 읽으면 트리거 종류·시각·건수 3종·상태가 그대로 돌아온다`() {
        val started = OffsetDateTime.now(clock)
        val finished = started.plusMinutes(1)

        val saved =
            batchExecutionRepository.save(
                BatchExecution(
                    trigger = BatchTrigger.SCHEDULED,
                    triggeredByAdminId = null,
                    startedAt = started,
                    finishedAt = finished,
                    processedMemberCount = 10,
                    deductedCount = 3,
                    skippedCount = 1,
                    status = BatchExecutionStatus.SUCCESS,
                    errorSummary = null,
                ),
            )

        val reloaded = batchExecutionRepository.findById(saved.id!!).get()
        assertThat(reloaded.trigger).isEqualTo(BatchTrigger.SCHEDULED)
        assertThat(reloaded.startedAt).isCloseTo(started, within(1, ChronoUnit.SECONDS))
        assertThat(reloaded.finishedAt).isCloseTo(finished, within(1, ChronoUnit.SECONDS))
        assertThat(reloaded.processedMemberCount).isEqualTo(10)
        assertThat(reloaded.deductedCount).isEqualTo(3)
        assertThat(reloaded.skippedCount).isEqualTo(1)
        assertThat(reloaded.status).isEqualTo(BatchExecutionStatus.SUCCESS)
    }

    @Test
    fun `trigger가 MANUAL인데 triggeredBy가 없으면 ck_batch_execution_trigger 위반으로 저장이 실패한다`() {
        assertThatThrownBy {
            batchExecutionRepository.saveAndFlush(
                BatchExecution(
                    trigger = BatchTrigger.MANUAL,
                    triggeredByAdminId = null,
                    startedAt = OffsetDateTime.now(clock),
                    finishedAt = OffsetDateTime.now(clock),
                    processedMemberCount = 0,
                    deductedCount = 0,
                    skippedCount = 0,
                    status = BatchExecutionStatus.SUCCESS,
                    errorSummary = null,
                ),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `trigger가 SCHEDULED인데 triggeredBy가 채워져 있으면 저장이 실패한다`() {
        val admin = persistAdmin()

        assertThatThrownBy {
            batchExecutionRepository.saveAndFlush(
                BatchExecution(
                    trigger = BatchTrigger.SCHEDULED,
                    triggeredByAdminId = admin.id,
                    startedAt = OffsetDateTime.now(clock),
                    finishedAt = OffsetDateTime.now(clock),
                    processedMemberCount = 0,
                    deductedCount = 0,
                    skippedCount = 0,
                    status = BatchExecutionStatus.SUCCESS,
                    errorSummary = null,
                ),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `trigger가 SCHEDULED, triggeredBy가 null, status가 SUCCESS인 조합은 저장에 성공한다`() {
        val saved =
            batchExecutionRepository.saveAndFlush(
                BatchExecution(
                    trigger = BatchTrigger.SCHEDULED,
                    triggeredByAdminId = null,
                    startedAt = OffsetDateTime.now(clock),
                    finishedAt = OffsetDateTime.now(clock),
                    processedMemberCount = 5,
                    deductedCount = 2,
                    skippedCount = 0,
                    status = BatchExecutionStatus.SUCCESS,
                    errorSummary = null,
                ),
            )

        assertThat(saved.id).isNotNull()
    }

    @Test
    fun `errorSummary가 null인 정상 실행도 저장에 성공한다`() {
        val saved =
            batchExecutionRepository.saveAndFlush(
                BatchExecution(
                    trigger = BatchTrigger.SCHEDULED,
                    triggeredByAdminId = null,
                    startedAt = OffsetDateTime.now(clock),
                    finishedAt = OffsetDateTime.now(clock),
                    processedMemberCount = 0,
                    deductedCount = 0,
                    skippedCount = 0,
                    status = BatchExecutionStatus.SUCCESS,
                    errorSummary = null,
                ),
            )

        assertThat(saved.id).isNotNull()
        assertThat(batchExecutionRepository.findById(saved.id!!).get().errorSummary).isNull()
    }

    private fun persistAdmin(): Admin {
        fixtureCounter++
        return adminRepository.saveAndFlush(
            Admin(
                name = "관리자",
                loginId = "admin-batch-$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }
}
