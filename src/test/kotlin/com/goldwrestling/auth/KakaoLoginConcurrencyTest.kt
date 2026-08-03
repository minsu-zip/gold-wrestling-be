package com.goldwrestling.auth

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.auth.kakao.KakaoApiClient
import com.goldwrestling.auth.kakao.KakaoTokenResponse
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Clock
import java.time.OffsetDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * CR-01 회귀 방어 테스트(02-REVIEW.md, 02-VERIFICATION.md) — 같은 카카오 계정으로 거의 동시에
 * 최초 로그인을 시도하는 경쟁(중복 클릭·모바일 네트워크 재시도)을 재현한다.
 *
 * 애노테이션 조합은 [KakaoAuthControllerTest]와 동일하게 유지해 스프링 컨텍스트 캐시를 공유한다.
 * **이 클래스·메서드에는 트랜잭션 애노테이션을 붙이지 않는다** — 각 스레드가 별도 트랜잭션이어야
 * 유니크 제약 경쟁이 실제로 재현된다(add-domain-test §2·§4). 트랜잭션 경계에 기댄 자동 롤백을
 * 쓸 수 없으므로 각 테스트가 만든 데이터는 [cleanUp]에서 직접 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class KakaoLoginConcurrencyTest {
    @Autowired
    private lateinit var kakaoAuthService: KakaoAuthService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @MockitoBean
    private lateinit var kakaoApiClient: KakaoApiClient

    /**
     * 이 테스트가 만든 kakaoId를 여기에 모아 두고 [cleanUp]에서 일괄 삭제한다.
     * 트랜잭션 경계에 기댄 자동 롤백을 쓸 수 없어(경쟁 재현을 위해 스레드마다 별도 트랜잭션이 필요) 정리를
     * 빠뜨리면 다른 테스트(예: `KakaoAuthControllerTest`의 `memberRepository.count()` 전체 건수
     * 단언)를 오염시킨다.
     */
    private val usedKakaoIds = Collections.synchronizedList(mutableListOf<Long>())

    @AfterEach
    fun cleanUp() {
        if (usedKakaoIds.isEmpty()) return
        // 외래키 순서상 refresh_token을 먼저 지우고 member를 지운다.
        jdbcClient
            .sql("delete from refresh_token where member_id in (select id from member where kakao_id in (:ids))")
            .param("ids", usedKakaoIds)
            .update()
        jdbcClient
            .sql("delete from member where kakao_id in (:ids)")
            .param("ids", usedKakaoIds)
            .update()
        usedKakaoIds.clear()
    }

    @Test
    fun `같은 카카오 계정으로 동시에 최초 로그인해도 회원은 1명만 생성되고 모든 요청이 성공한다`() {
        val kakaoId = 7001L
        usedKakaoIds.add(kakaoId)
        stubKakaoLogin(kakaoId)

        val result = runConcurrentLogins(kakaoId)

        assertThat(result.successCount.get()).isEqualTo(THREADS)
        assertThat(result.failures).isEmpty()
        val member = memberRepository.findByKakaoId(kakaoId)
        assertThat(member).isNotNull
        val rowCount =
            jdbcClient
                .sql("select count(*) from member where kakao_id = :kakaoId")
                .param("kakaoId", kakaoId)
                .query(Long::class.java)
                .single()
        assertThat(rowCount).isEqualTo(1L)
        assertThat(result.memberIds.toSet()).containsExactly(member!!.id)
        // 회원이 정확히 한 번만 생성됐음을 createdAt으로도 재확인 — createPendingMember가 두 번 이상
        // 성공했다면 두 번째 saveAndFlush가 유니크 제약에 막히지만, 혹시 다른 시점에 갱신되는
        // 경로가 생기더라도 이 값이 흔들리면(고정 Clock이라 흔들리지 않아야 함) 잡아낼 수 있다.
        assertThat(member.createdAt).isEqualTo(OffsetDateTime.now(clock))
    }

    @Test
    fun `동시 최초 로그인에서 UnexpectedRollbackException이 발생하지 않는다`() {
        val kakaoId = 7002L
        usedKakaoIds.add(kakaoId)
        stubKakaoLogin(kakaoId)

        val result = runConcurrentLogins(kakaoId)

        assertThat(result.failures).isEmpty()
    }

    private fun stubKakaoLogin(kakaoId: Long) {
        given(kakaoApiClient.exchangeToken(anyString())).willReturn(dummyTokenResponse())
        given(kakaoApiClient.fetchKakaoId(anyString())).willReturn(kakaoId)
    }

    private fun dummyTokenResponse(): KakaoTokenResponse =
        KakaoTokenResponse(
            accessToken = "kakao-access-token-for-concurrency-test",
            tokenType = "bearer",
            expiresIn = 21599,
            refreshToken = null,
            scope = null,
        )

    /**
     * 스레드 4개로 동시 최초 로그인을 재현한다. 스레드 수를 4로 잡은 이유: 경쟁 중 스레드가 유니크
     * 인덱스 대기로 커넥션을 붙잡으므로, 기본 커넥션 풀 크기 안에서 안전하게 재현되는 최소 규모다.
     */
    private fun runConcurrentLogins(kakaoId: Long): ConcurrentLoginResult {
        val executor = Executors.newFixedThreadPool(THREADS)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(THREADS)
        val successCount = AtomicInteger(0)
        val memberIds = Collections.synchronizedList(mutableListOf<Long>())
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())

        repeat(THREADS) {
            executor.submit {
                try {
                    startLatch.await()
                    val response = kakaoAuthService.login("동시-로그인-코드")
                    successCount.incrementAndGet()
                    memberIds.add(response.member.memberId)
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

        return ConcurrentLoginResult(successCount, memberIds, failures)
    }

    private data class ConcurrentLoginResult(
        val successCount: AtomicInteger,
        val memberIds: MutableList<Long>,
        val failures: MutableList<Throwable>,
    )

    companion object {
        private const val THREADS = 4
    }
}
