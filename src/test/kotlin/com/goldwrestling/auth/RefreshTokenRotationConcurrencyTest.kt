package com.goldwrestling.auth

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * WR-01(02-REVIEW.md) 회귀 방어 — refresh 회전의 폐기가 조건부 UPDATE로 원자화됐는지(TOCTOU 제거),
 * 재사용 감지 시의 대량 폐기가 실패 응답 경로에서도 실제 DB에 커밋되는지(`noRollbackFor`)를 검증한다.
 *
 * 애노테이션 조합은 [RefreshTokenRotationTest]와 동일하게 유지해 스프링 컨텍스트 캐시를 공유한다
 * (conventions §10.1). **이 클래스·메서드에는 트랜잭션 애노테이션을 붙이지 않는다** — 각 스레드가
 * 별도 트랜잭션이어야 조건부 UPDATE 경쟁이 실제로 재현되고(add-domain-test §2·§4), 테스트 트랜잭션의
 * 자동 롤백에 기대지 않고 커밋 후 DB 상태를 직접 조회해야 `noRollbackFor` 누락 회귀를 잡을 수 있다.
 * 트랜잭션 경계에 기댄 자동 정리를 쓸 수 없으므로 [cleanUp]에서 이 테스트가 만든 kakaoId 대역의
 * 행을 직접 지운다 — 빠뜨리면 `RefreshTokenRotationTest` 등 전체 건수를 단언하는 테스트를 오염시킨다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class RefreshTokenRotationConcurrencyTest {
    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @BeforeEach
    fun resetClock() {
        // RefreshTokenRotationTest와 같은 이유 — JwtDecoder 기본 검증기가 실제 시스템 시각과
        // 토큰의 iat/exp를 비교하므로, 이전 테스트가 밀어 둔 시각을 실제 벽시계로 되돌린다.
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @AfterEach
    fun cleanUp() {
        // 외래키 순서상 refresh_token을 먼저 지우고 member를 지운다.
        jdbcClient
            .sql("delete from refresh_token where member_id in (select id from member where kakao_id in (:ids))")
            .param("ids", KAKAO_IDS)
            .update()
        jdbcClient
            .sql("delete from member where kakao_id in (:ids)")
            .param("ids", KAKAO_IDS)
            .update()
    }

    @Test
    fun `같은 refresh 토큰을 동시에 제시하면 한 번만 회전에 성공한다`() {
        val member = memberRepository.saveAndFlush(newMember(kakaoId = KAKAO_ID_ROTATION))
        val original = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)

        val executor = Executors.newFixedThreadPool(THREADS)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(THREADS)
        val successCount = AtomicInteger(0)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())

        repeat(THREADS) {
            executor.submit {
                try {
                    startLatch.await()
                    tokenService.rotate(original.refreshToken)
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

        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failures).hasSize(THREADS - 1)
        failures.forEach { assertThat(it).isInstanceOf(RefreshTokenInvalidException::class.java) }

        val originalRevokedCount =
            jdbcClient
                .sql("select count(*) from refresh_token where token_hash = :hash and revoked_at is not null")
                .param("hash", sha256(original.refreshToken))
                .query(Long::class.java)
                .single()
        assertThat(originalRevokedCount).isEqualTo(1L)

        // 하나의 refresh에서 두 개 이상의 유효 토큰이 파생되지 않았음의 증명: 원본 1행 + 승자가
        // 발급한 새 토큰 1행 = 총 2행. **미폐기 행 수는 단언하지 않는다** — 패자의 대량 폐기
        // (revokeAllUsableFor)가 승자의 신규 발급 커밋보다 먼저 실행될 수도 있어, 승자의 새 토큰이
        // 곧바로 함께 폐기되는 경우와 살아남는 경우가 둘 다 가능하다(0 또는 1로 갈리는 비결정성).
        // 이 테스트가 증명하는 것은 "총 행 수가 2를 넘지 않는다" — 즉 회전 하나에서 유효 토큰이
        // 두 개 이상 파생되지 않는다는 사실이다.
        val totalRows =
            jdbcClient
                .sql("select count(*) from refresh_token where member_id = :memberId")
                .param("memberId", member.id)
                .query(Long::class.java)
                .single()
        assertThat(totalRows).isEqualTo(2L)
    }

    @Test
    fun `폐기된 refresh 재사용이 감지되면 그 주체의 미폐기 refresh 폐기가 DB에 남는다`() {
        // 순차 시나리오 — 경쟁이 아니라 noRollbackFor 누락 회귀를 잡는 것이 목적이다.
        // 이 테스트가 noRollbackFor 회귀를 잡는 유일한 방어선이다: noRollbackFor를 빼면 아래
        // 두 번째 rotate 호출 안의 revokeAllUsableFor 호출 결과가 RefreshTokenInvalidException과
        // 함께 롤백되어 DB에는 반영되지 않는다 — 이 테스트만이 커밋 후 별도 조회로 그 차이를 잡는다.
        val member = memberRepository.saveAndFlush(newMember(kakaoId = KAKAO_ID_REUSE))
        val first = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)
        val second = tokenService.rotate(first.refreshToken)

        assertThatThrownBy { tokenService.rotate(first.refreshToken) }
            .isInstanceOf(RefreshTokenInvalidException::class.java)

        // JPA 1차 캐시가 아니라 별도 JdbcClient 조회로 커밋된 DB 상태를 직접 확인한다 — 같은
        // 영속성 컨텍스트에서 조회하면 캐시된 메모리 상태를 보게 되어 롤백 여부를 구분하지 못한다.
        val secondRevokedCount =
            jdbcClient
                .sql("select count(*) from refresh_token where token_hash = :hash and revoked_at is not null")
                .param("hash", sha256(second.refreshToken))
                .query(Long::class.java)
                .single()
        assertThat(secondRevokedCount).isEqualTo(1L)
    }

    private fun sha256(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun newMember(kakaoId: Long): Member =
        Member(
            branch = songpaBranch(),
            name = null,
            phoneNumber = null,
            kakaoId = kakaoId,
            createdAt = OffsetDateTime.now(clock),
        )

    companion object {
        private const val THREADS = 4
        private const val KAKAO_ID_ROTATION = 7101L
        private const val KAKAO_ID_REUSE = 7102L
        private val KAKAO_IDS = listOf(KAKAO_ID_ROTATION, KAKAO_ID_REUSE)
    }
}
