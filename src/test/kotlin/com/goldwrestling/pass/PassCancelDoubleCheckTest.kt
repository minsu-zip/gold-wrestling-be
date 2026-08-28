package com.goldwrestling.pass

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.pass.dto.CancelPassRequest
import com.goldwrestling.reservation.ReservationRepository
import com.goldwrestling.reservation.ReservationStatus
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.willReturn
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * `AdminPassService.cancel`의 이중 검사(D-145, 이슈 #12/WR-02) 통합테스트. D-089 선행 검사는
 * 이용권 조회 직후 수행되지만, 그 검사~`cancelIfNotCanceled`(상태 전환) 사이에 창이 있다 — 그
 * 사이에 예약이 커밋되면 선행 검사만으로는 놓친다. 이 창에서 커밋된 활성 예약을 상태 전환
 * 직후(이용권 행 잠금을 확보한 뒤) 한 번 더 확인해 거부한다.
 *
 * 경합 창이 마이크로초 단위라 실제 스레드로는 결정적으로 재현할 수 없다 — `@MockitoSpyBean
 * ReservationRepository`로 `existsByPassIdAndStatus`가 **첫 호출(선행 검사)은 false, 두 번째
 * 호출(이중 검사)은 true**를 반환하게 해 그 창에서 예약이 커밋된 상황을 결정적으로 재현한다.
 * `willReturn(...).given(spy).method(실제 인자)` 형태를 쓴다 — 매처 없이 실제 값을 그대로 넘겨야
 * (`ClassSessionSuspensionTest`의 `anyArg()` 우회와 같은 이유, Kotlin non-null 파라미터 + Mockito 5
 * NPE 회피) 스텁 설정 중 스파이가 실제 메서드를 호출하지 않는다(`doReturn`과 동일 효과).
 *
 * 애노테이션 조합(`@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import(TestcontainersConfiguration,
 * TestClockConfiguration)` + `@MockitoSpyBean ReservationRepository`)을 `ClassSessionSuspensionTest`와
 * 동일하게 맞춰 컨텍스트 캐시를 공유한다(conventions §10.1). 클래스 레벨 `@Transactional`은 **붙이지
 * 않는다** — 붙이면 `cancel` 호출이 테스트 트랜잭션에 편승해 롤백이 테스트 종료까지 실제 반영되지
 * 않아 "상태가 ACTIVE 그대로"를 검증할 수 없다(`MemberReservationServiceTest` 선례). 대신
 * `@AfterEach`에서 `JdbcClient`로 직접 정리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class PassCancelDoubleCheckTest {
    @Autowired
    private lateinit var adminPassService: AdminPassService

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var passRepository: PassRepository

    @Autowired
    private lateinit var passTransactionRepository: PassTransactionRepository

    @MockitoSpyBean
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private var memberId: Long? = null
    private var adminId: Long? = null

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @AfterEach
    fun cleanUp() {
        jdbcClient
            .sql("delete from pass_transaction where pass_id in (select id from pass where member_id = :memberId)")
            .param("memberId", memberId)
            .update()
        jdbcClient.sql("delete from pass where member_id = :memberId").param("memberId", memberId).update()
        jdbcClient.sql("delete from member where id = :memberId").param("memberId", memberId).update()
        jdbcClient.sql("delete from admin where id = :adminId").param("adminId", adminId).update()
    }

    @Test
    fun `선행 검사 이후 활성 예약이 생기면 등록 취소가 거부되고 상태-이력이 변하지 않는다`() {
        val member = persistMember()
        memberId = member.id
        val admin = persistAdmin()
        adminId = admin.id
        val pass = persistPass(member, admin, remaining = "2.0")

        // 첫 호출(D-089 선행 검사)은 false — 통과. 두 번째 호출(이중 검사)은 true — 그 사이
        // 커밋된 활성 예약을 잡는다.
        willReturn(false, true)
            .given(reservationRepository)
            .existsByPassIdAndStatus(pass.id!!, ReservationStatus.ACTIVE)

        assertThatThrownBy {
            adminPassService.cancel(pass.id!!, CancelPassRequest("오등록 정정 시도"), admin.id!!)
        }.isInstanceOf(PassHasActiveReservationException::class.java)

        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.status).isEqualTo(PassStatus.ACTIVE)
        assertThat(refreshedPass.canceledAt).isNull()
        assertThat(refreshedPass.cancelReason).isNull()
        assertThat(refreshedPass.canceledBy).isNull()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("2.0"))

        val transactions =
            passTransactionRepository.findAll().filter {
                it.pass.id == pass.id && it.reason == TransactionReason.REGISTRATION_CANCELED
            }
        assertThat(transactions).isEmpty()
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun persistMember(): Member =
        memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "이중검사회원",
                phoneNumber = "01099970000",
                status = MemberStatus.ACTIVE,
                kakaoId = KAKAO_ID,
                createdAt = OffsetDateTime.now(clock),
            ),
        )

    private fun persistAdmin(): Admin =
        adminRepository.saveAndFlush(
            Admin(
                name = "관리자",
                loginId = "admin-pass-cancel-doublecheck",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )

    private fun persistPass(
        member: Member,
        admin: Admin,
        remaining: String,
    ): Pass =
        passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                type = PassType.SESSION_PASS,
                status = PassStatus.ACTIVE,
                startDate = LocalDate.now(clock),
                endDate = LocalDate.now(clock).plusYears(1).minusDays(1),
                remainingCount = BigDecimal(remaining),
                createdAt = OffsetDateTime.now(clock),
            ),
        )

    private companion object {
        const val KAKAO_ID = 91_000_003L
    }
}
