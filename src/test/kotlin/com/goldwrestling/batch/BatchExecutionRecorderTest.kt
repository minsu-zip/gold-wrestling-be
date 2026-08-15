package com.goldwrestling.batch

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * [BatchExecutionRecorder]의 **트랜잭션 경계**를 실제 PostgreSQL(Testcontainers)에서 검증하는
 * 통합테스트. 애노테이션 조합은 다른 배치 통합테스트와 동일하게 유지한다(conventions §10.1 —
 * 스프링 컨텍스트 캐시 공유).
 *
 * **클래스·메서드에 `@Transactional`을 붙이지 않는다.** 이 클래스가 증명해야 하는 것이 바로
 * "시작 기록이 호출부와 **별개 트랜잭션으로 즉시 커밋된다**"이기 때문이다 — 테스트를 트랜잭션으로
 * 감싸면 커밋 여부를 관찰할 수 없고, 롤백 테스트(`REQUIRES_NEW` 증명)는 아예 성립하지 않는다
 * (add-domain-test §4의 동시성 테스트와 같은 이유).
 *
 * 그래서 데이터 정리를 [cleanUp]이 직접 한다. **이 클래스가 만든 `RUNNING` 행을 하나라도 남기면
 * 같은 컨테이너를 쓰는 다른 테스트 클래스의 배치 실행이 전부 409(`uq_batch_execution_running`)로
 * 막혀, 원인과 무관한 곳에서 실패가 터진다**(T-05D-11-01). 정리 범위는 [baselineExecutionId]
 * 이후에 생긴 행으로 한정한다 — 다른 테스트 클래스가 남긴 이력을 지우지 않기 위해서다.
 *
 * `MANUAL` 트리거 테스트는 `ck_batch_execution_trigger`(MANUAL이면 관리자 id 필수) 때문에 실제
 * `Admin` 행이 필요하다. 로그인 아이디 접두어는 이 클래스 전용([ADMIN_LOGIN_PREFIX])으로 두어
 * `uq_admin_login_id`가 다른 클래스와 충돌하지 않게 한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class BatchExecutionRecorderTest {
    @Autowired
    private lateinit var batchExecutionRecorder: BatchExecutionRecorder

    @Autowired
    private lateinit var batchExecutionRepository: BatchExecutionRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private var baselineExecutionId: Long = 0

    @BeforeEach
    fun resetClockAndBaseline() {
        (clock as MutableTestClock).setTo(BatchFixtures.FIXED_TIME.toInstant())
        baselineExecutionId =
            jdbcClient
                .sql("select coalesce(max(id), 0) from batch_execution")
                .query(Long::class.java)
                .single()
    }

    @AfterEach
    fun cleanUp() {
        jdbcClient
            .sql("delete from batch_execution where id > :baseline")
            .param("baseline", baselineExecutionId)
            .update()
        jdbcClient
            .sql("delete from admin where login_id like :prefix")
            .param("prefix", "$ADMIN_LOGIN_PREFIX%")
            .update()
    }

    @Test
    fun `start는 실행 중 이력 1건을 즉시 커밋하고 그 엔티티를 반환한다`() {
        val started = batchExecutionRecorder.start(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)

        assertThat(started.id).isNotNull()
        assertThat(started.status).isEqualTo(BatchExecutionStatus.RUNNING)
        assertThat(started.finishedAt).isNull()
        assertThat(started.startedAt).isCloseTo(OffsetDateTime.now(clock), within(1, ChronoUnit.SECONDS))
        // 트랜잭션을 열지 않은 이 테스트가 바로 읽어도 보인다 = 이미 커밋됐다.
        assertThat(statusOf(started.id!!)).isEqualTo("RUNNING")
        assertThat(createdRowCount()).isEqualTo(1)
    }

    @Test
    fun `start가 만든 실행 중 이력은 호출부 트랜잭션이 롤백돼도 살아남는다`() {
        val template = TransactionTemplate(transactionManager)

        val startedId =
            template.execute { outer ->
                val started = batchExecutionRecorder.start(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)
                outer.setRollbackOnly()
                started.id!!
            }!!

        // 호출부 트랜잭션에 참여(REQUIRED)했다면 롤백과 함께 사라졌을 행이다.
        assertThat(statusOf(startedId)).isEqualTo("RUNNING")
        assertThat(createdRowCount()).isEqualTo(1)
    }

    @Test
    fun `이미 실행 중인 배치가 있으면 두 번째 start는 거부되고 새 행이 만들어지지 않는다`() {
        batchExecutionRecorder.start(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)

        assertThatThrownBy {
            batchExecutionRecorder.start(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)
        }.isInstanceOf(BatchAlreadyRunningException::class.java)

        assertThat(createdRowCount()).isEqualTo(1)
    }

    @Test
    fun `finish로 확정한 뒤에는 같은 start가 다시 성공한다`() {
        val first = batchExecutionRecorder.start(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)
        batchExecutionRecorder.finish(
            executionId = first.id!!,
            status = BatchExecutionStatus.SUCCESS,
            processedMemberCount = 1,
            deductedCount = 0,
            skippedCount = 0,
            errorSummary = null,
        )

        val second = batchExecutionRecorder.start(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)

        assertThat(second.id).isNotNull()
        assertThat(statusOf(first.id!!)).isEqualTo("SUCCESS")
        assertThat(statusOf(second.id!!)).isEqualTo("RUNNING")
        assertThat(createdRowCount()).isEqualTo(2)
    }

    @Test
    fun `finish는 종료 시각과 집계 3종·상태·오류 요약을 확정하고 실행 중 행을 남기지 않는다`() {
        val started = batchExecutionRecorder.start(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)

        val finished =
            batchExecutionRecorder.finish(
                executionId = started.id!!,
                status = BatchExecutionStatus.PARTIAL_FAILURE,
                processedMemberCount = 7,
                deductedCount = 3,
                skippedCount = 2,
                errorSummary = "memberId=1: IllegalStateException",
            )

        assertThat(finished.status).isEqualTo(BatchExecutionStatus.PARTIAL_FAILURE)
        assertThat(finished.finishedAt).isCloseTo(OffsetDateTime.now(clock), within(1, ChronoUnit.SECONDS))

        val reloaded = batchExecutionRepository.findById(started.id!!).get()
        assertThat(reloaded.status).isEqualTo(BatchExecutionStatus.PARTIAL_FAILURE)
        assertThat(reloaded.finishedAt).isNotNull()
        assertThat(reloaded.processedMemberCount).isEqualTo(7)
        assertThat(reloaded.deductedCount).isEqualTo(3)
        assertThat(reloaded.skippedCount).isEqualTo(2)
        assertThat(reloaded.errorSummary).isEqualTo("memberId=1: IllegalStateException")
        assertThat(runningRowCount()).isZero()
    }

    @Test
    fun `임계 시간을 넘긴 실행 중 행은 STALE로 정리되고 새 실행이 시작된다`() {
        val stale = batchExecutionRecorder.start(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)

        // 앱이 죽어 확정되지 못한 상황 재현 — 기본 임계(30분)를 넘긴다.
        (clock as MutableTestClock).advance(Duration.ofMinutes(31))
        val fresh = batchExecutionRecorder.start(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)

        val reloadedStale = batchExecutionRepository.findById(stale.id!!).get()
        assertThat(reloadedStale.status).isEqualTo(BatchExecutionStatus.FAILED)
        assertThat(reloadedStale.errorSummary).isEqualTo("STALE")
        assertThat(reloadedStale.finishedAt).isCloseTo(OffsetDateTime.now(clock), within(1, ChronoUnit.SECONDS))
        assertThat(statusOf(fresh.id!!)).isEqualTo("RUNNING")
        assertThat(createdRowCount()).isEqualTo(2)
    }

    @Test
    fun `임계 시간 이내의 실행 중 행은 정리되지 않아 start가 거부된다`() {
        val running = batchExecutionRecorder.start(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)

        (clock as MutableTestClock).advance(Duration.ofMinutes(29))

        assertThatThrownBy {
            batchExecutionRecorder.start(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)
        }.isInstanceOf(BatchAlreadyRunningException::class.java)

        // 정상 실행 중인 배치를 죽었다고 오판해 끊지 않는다.
        assertThat(statusOf(running.id!!)).isEqualTo("RUNNING")
        assertThat(batchExecutionRepository.findById(running.id!!).get().errorSummary).isNull()
        assertThat(createdRowCount()).isEqualTo(1)
    }

    @Test
    fun `MANUAL 트리거로 시작하면 실행을 지시한 관리자 id가 함께 기록된다`() {
        val admin = adminRepository.saveAndFlush(BatchFixtures.admin(loginId = "${ADMIN_LOGIN_PREFIX}1"))

        val started = batchExecutionRecorder.start(trigger = BatchTrigger.MANUAL, triggeredByAdminId = admin.id)

        assertThat(started.trigger).isEqualTo(BatchTrigger.MANUAL)
        assertThat(batchExecutionRepository.findById(started.id!!).get().triggeredByAdminId).isEqualTo(admin.id)
        assertThat(statusOf(started.id!!)).isEqualTo("RUNNING")
    }

    /** 이 테스트가 만든 행만 센다 — 다른 테스트 클래스가 남긴 이력과 섞이지 않게 한다. */
    private fun createdRowCount(): Int =
        jdbcClient
            .sql("select count(*) from batch_execution where id > :baseline")
            .param("baseline", baselineExecutionId)
            .query(Int::class.java)
            .single()

    private fun runningRowCount(): Int =
        jdbcClient
            .sql("select count(*) from batch_execution where id > :baseline and status = 'RUNNING'")
            .param("baseline", baselineExecutionId)
            .query(Int::class.java)
            .single()

    /** JPA 1차 캐시를 거치지 않고 DB에 실제로 커밋된 상태값을 읽는다. */
    private fun statusOf(executionId: Long): String? =
        jdbcClient
            .sql("select status from batch_execution where id = :id")
            .param("id", executionId)
            .query(String::class.java)
            .optional()
            .orElse(null)

    companion object {
        const val ADMIN_LOGIN_PREFIX = "admin-batch-recorder-"
    }
}
