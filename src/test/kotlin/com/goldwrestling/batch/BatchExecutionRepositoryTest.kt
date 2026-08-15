package com.goldwrestling.batch

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.batch.dto.BatchExecutionResponse
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
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * `BatchExecution`의 저장·조회, `ck_batch_execution_trigger` CHECK 제약, 그리고 **실행 중 행은
 * 최대 1건**을 강제하는 V10 부분 유니크 인덱스(`uq_batch_execution_running`, D-117)를 실제
 * PostgreSQL(Testcontainers)에서 증명하는 통합테스트. 애노테이션 조합은 `PassRepositoryTest`와
 * 동일하게 유지한다(conventions §10.1 — 컨텍스트 캐시 재사용).
 *
 * **이 클래스가 만든 `RUNNING` 행은 절대 남기지 않는다.** 하나라도 남으면 같은 컨테이너를 쓰는
 * 다른 테스트 클래스의 배치 실행이 전부 유니크 위반으로 막혀, 원인과 무관한 곳에서 실패가
 * 터진다(T-05D-11-01). 클래스 레벨 트랜잭션 롤백이 그 보장이다 — 각 테스트가 만든 행은 테스트
 * 종료 시 전부 사라진다. 그래서 여기에 별도 `@AfterEach` 삭제를 두지 않는다: 유니크 위반을
 * 단언하는 테스트는 PostgreSQL 트랜잭션이 abort 상태라 이후 어떤 SQL도 실행할 수 없어,
 * `@AfterEach`의 DELETE가 오히려 그 테스트를 실패시킨다.
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
    fun `trigger가 MANUAL인데 triggeredByAdminId가 없으면 ck_batch_execution_trigger 위반으로 저장이 실패한다`() {
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
    fun `trigger가 SCHEDULED인데 triggeredByAdminId가 채워져 있으면 저장이 실패한다`() {
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
    fun `trigger가 SCHEDULED, triggeredByAdminId가 null, status가 SUCCESS인 조합은 저장에 성공한다`() {
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

    // ── V10 uq_batch_execution_running: 실행 중 행은 최대 1건 (D-117) ──────────

    @Test
    fun `실행 중 이력은 finishedAt 없이 RUNNING 상태로 저장된다`() {
        val saved = batchExecutionRepository.saveAndFlush(startedExecution())

        val reloaded = batchExecutionRepository.findById(saved.id!!).get()
        assertThat(reloaded.status).isEqualTo(BatchExecutionStatus.RUNNING)
        assertThat(reloaded.finishedAt).isNull()
        assertThat(reloaded.deductedCount).isZero()
    }

    @Test
    fun `이미 RUNNING 행이 있으면 두 번째 RUNNING 행 저장은 유니크 위반으로 실패한다`() {
        batchExecutionRepository.saveAndFlush(startedExecution())

        assertThatThrownBy {
            batchExecutionRepository.saveAndFlush(startedExecution())
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `앞선 실행을 SUCCESS로 확정하면 새 RUNNING 행을 저장할 수 있다`() {
        val first = batchExecutionRepository.saveAndFlush(startedExecution())
        first.finish(
            status = BatchExecutionStatus.SUCCESS,
            finishedAt = OffsetDateTime.now(clock),
            processedMemberCount = 3,
            deductedCount = 1,
            skippedCount = 0,
            errorSummary = null,
        )
        batchExecutionRepository.saveAndFlush(first)

        val second = batchExecutionRepository.saveAndFlush(startedExecution())

        assertThat(second.id).isNotNull()
        assertThat(batchExecutionRepository.findById(first.id!!).get().status).isEqualTo(BatchExecutionStatus.SUCCESS)
    }

    @Test
    fun `findFirstByStatus는 실행 중인 이력을 찾아 반환한다`() {
        val running = batchExecutionRepository.saveAndFlush(startedExecution())

        val found = batchExecutionRepository.findFirstByStatus(BatchExecutionStatus.RUNNING)

        assertThat(found?.id).isEqualTo(running.id)
    }

    // ── stale RUNNING 정리 (T-05D-11-01) ──────────────────────────────────────

    @Test
    fun `markStaleRunningAsFailed는 임계 시각보다 이른 RUNNING 행만 FAILED로 바꾸고 갱신 행 수를 반환한다`() {
        val now = OffsetDateTime.now(clock)
        val stale = batchExecutionRepository.saveAndFlush(startedExecution(startedAt = now.minusHours(2)))
        val finishedLongAgo =
            batchExecutionRepository.saveAndFlush(
                BatchExecution(
                    trigger = BatchTrigger.SCHEDULED,
                    triggeredByAdminId = null,
                    startedAt = now.minusDays(3),
                    finishedAt = now.minusDays(3).plusMinutes(1),
                    status = BatchExecutionStatus.SUCCESS,
                ),
            )

        val updated =
            batchExecutionRepository.markStaleRunningAsFailed(
                threshold = now.minusMinutes(30),
                finishedAt = now,
                errorSummary = "STALE",
            )

        assertThat(updated).isEqualTo(1)
        val reloadedStale = batchExecutionRepository.findById(stale.id!!).get()
        assertThat(reloadedStale.status).isEqualTo(BatchExecutionStatus.FAILED)
        assertThat(reloadedStale.finishedAt).isCloseTo(now, within(1, ChronoUnit.SECONDS))
        assertThat(reloadedStale.errorSummary).isEqualTo("STALE")
        // 이미 끝난 행은 건드리지 않는다 — 정리 대상은 "실행 중인 채로 방치된" 행뿐이다.
        assertThat(batchExecutionRepository.findById(finishedLongAgo.id!!).get().status).isEqualTo(BatchExecutionStatus.SUCCESS)
    }

    @Test
    fun `markStaleRunningAsFailed는 임계 시각 이후에 시작된 RUNNING 행을 건드리지 않는다`() {
        val now = OffsetDateTime.now(clock)
        val fresh = batchExecutionRepository.saveAndFlush(startedExecution(startedAt = now.minusMinutes(5)))

        val updated =
            batchExecutionRepository.markStaleRunningAsFailed(
                threshold = now.minusMinutes(30),
                finishedAt = now,
                errorSummary = "STALE",
            )

        assertThat(updated).isZero()
        assertThat(batchExecutionRepository.findById(fresh.id!!).get().status).isEqualTo(BatchExecutionStatus.RUNNING)
    }

    // ── 목록 조회 (05-15 관리자 조회 API가 쓴다) ───────────────────────────────

    @Test
    fun `findAllByOrderByStartedAtDesc는 최근 실행부터 페이지 크기만큼 반환한다`() {
        // 같은 컨테이너를 쓰는 다른 테스트 클래스의 잔여 행과 섞이지 않도록 시작 시각을 먼 미래로
        // 둔다 — 내림차순 정렬에서 이 클래스의 행이 항상 앞에 온다.
        val base = OffsetDateTime.now(clock).plusYears(900)
        val oldest = batchExecutionRepository.saveAndFlush(finishedExecution(startedAt = base))
        val middle = batchExecutionRepository.saveAndFlush(finishedExecution(startedAt = base.plusMinutes(1)))
        val newest = batchExecutionRepository.saveAndFlush(finishedExecution(startedAt = base.plusMinutes(2)))

        val page = batchExecutionRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, 2))

        assertThat(page.map { it.id }).containsExactly(newest.id, middle.id)
        assertThat(page.map { it.id }).doesNotContain(oldest.id)
    }

    // ── 응답 DTO 변환 (WR-04) ─────────────────────────────────────────────────

    @Test
    fun `DB에서 다시 읽은 실행 중 이력도 응답 DTO로 변환되고 finishedAt은 null이다`() {
        val admin = persistAdmin()
        val saved =
            batchExecutionRepository.saveAndFlush(
                BatchExecution(
                    trigger = BatchTrigger.MANUAL,
                    triggeredByAdminId = admin.id,
                    startedAt = OffsetDateTime.now(clock),
                ),
            )

        val response = BatchExecutionResponse.from(batchExecutionRepository.findById(saved.id!!).get())

        assertThat(response.status).isEqualTo(BatchExecutionStatus.RUNNING)
        assertThat(response.finishedAt).isNull()
        assertThat(response.triggeredByAdminId).isEqualTo(admin.id)
    }

    private fun startedExecution(startedAt: OffsetDateTime = OffsetDateTime.now(clock)): BatchExecution =
        BatchExecution(
            trigger = BatchTrigger.SCHEDULED,
            triggeredByAdminId = null,
            startedAt = startedAt,
        )

    private fun finishedExecution(startedAt: OffsetDateTime): BatchExecution =
        BatchExecution(
            trigger = BatchTrigger.SCHEDULED,
            triggeredByAdminId = null,
            startedAt = startedAt,
            finishedAt = startedAt.plusSeconds(30),
            status = BatchExecutionStatus.SUCCESS,
        )

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
