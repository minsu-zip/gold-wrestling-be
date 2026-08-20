package com.goldwrestling.auth

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime

/**
 * `POST /api/auth/admin/login`의 성공/실패 계약과, 발급된 토큰이 실제로 `ROLE_ADMIN`으로 인식되는지
 * 검증하는 통합테스트(AUTH-03, D-026). 애노테이션 조합은 `KakaoAuthControllerTest`·
 * `RefreshTokenRotationTest`와 동일하게 `@Import(TestcontainersConfiguration::class,
 * TestClockConfiguration::class)`를 쓴다(conventions §10.1 — 컨텍스트 캐시 재사용).
 *
 * "ROLE_ADMIN 인식" 검증은 아직 실제 관리자 전용 엔드포인트가 없어, 존재하지 않는 경로
 * (`/api/admin/__probe`)에 발급된 토큰으로 요청했을 때 **403이 아닌 404**가 오는지로 대신한다 —
 * 인가는 통과했고 매핑된 핸들러가 없어서 나는 404이기 때문이다(토큰 없이 같은 경로면 401이어야 한다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class AdminAuthControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var clock: Clock

    private val rawPassword = "test-admin-password-1234"

    /**
     * `MutableTestClock`은 싱글턴 빈이라 이전 테스트가 밀어 둔 시각이 이어진다. `JwtDecoder`의 만료
     * 검증은 실제 시스템 시각과 비교하므로(RefreshTokenRotationTest 참고), 매 테스트 시작 시 실제
     * 현재 시각으로 되돌린다.
     */
    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @Test
    fun `올바른 loginId와 비밀번호로 로그인하면 200과 토큰 쌍·관리자 요약이 온다`() {
        persistAdmin(loginId = "admin-login-ok", name = "송파점 관리자")

        mockMvc
            .perform(loginRequest("admin-login-ok", rawPassword))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tokens.accessToken").exists())
            .andExpect(jsonPath("$.tokens.refreshToken").exists())
            .andExpect(jsonPath("$.admin.adminId").exists())
            .andExpect(jsonPath("$.admin.name").value("송파점 관리자"))
            .andExpect(jsonPath("$.admin.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.admin.loginId").doesNotExist())
    }

    /**
     * 읽기 전용 트랜잭션 회귀 테스트 — 02-11 수동 검증에서 발견된 500 버그의 재현 조건.
     *
     * 이 클래스의 다른 테스트는 클래스 레벨 `@Transactional`(테스트 트랜잭션) 안에서 돌고, 그 바깥
     * 트랜잭션은 **쓰기 가능**이라 서비스의 `readOnly = true`가 합류해도 INSERT가 통과해 버린다 —
     * 즉 운영에서만 터지는 read-only 위반을 테스트가 가려 버린다. 이 테스트만 `NOT_SUPPORTED`로
     * 테스트 트랜잭션을 끄고, 서비스가 스스로 여는 트랜잭션 경계 그대로 로그인을 실행해
     * refresh 토큰 INSERT까지 실제로 커밋되는지 확인한다. 커밋이 실제로 남으므로 finally에서 직접 지운다.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `테스트 트랜잭션 없이 로그인해도 refresh 토큰 INSERT가 성공한다 (read-only 회귀)`() {
        val admin = persistAdmin(loginId = "admin-readonly-regression", name = "관리자")
        try {
            mockMvc
                .perform(loginRequest("admin-readonly-regression", rawPassword))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.tokens.refreshToken").exists())

            assertThat(refreshTokenRepository.findAllByAdminAndRevokedAtIsNull(admin)).isNotEmpty()
        } finally {
            refreshTokenRepository.deleteAll(refreshTokenRepository.findAllByAdminAndRevokedAtIsNull(admin))
            adminRepository.delete(admin)
        }
    }

    @Test
    fun `발급된 access 토큰으로 관리자 전용 경로에 접근하면 403이 아니라 404다 (역할은 인식됐다)`() {
        persistAdmin(loginId = "admin-role-check", name = "관리자")
        val accessToken = login("admin-role-check", rawPassword)

        mockMvc
            .perform(get("/api/admin/__probe").header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `토큰 없이 관리자 전용 경로에 접근하면 401이다`() {
        mockMvc
            .perform(get("/api/admin/__probe"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `발급된 access 토큰으로 회원 전용 경로에 접근하면 403이다`() {
        persistAdmin(loginId = "admin-cross-role", name = "관리자")
        val accessToken = login("admin-cross-role", rawPassword)

        mockMvc
            .perform(get("/api/members/__probe").header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `존재하지 않는 loginId와 틀린 비밀번호의 응답이 완전히 같다`() {
        persistAdmin(loginId = "admin-wrong-password", name = "관리자")

        val notFoundResult =
            mockMvc
                .perform(loginRequest("존재하지-않는-admin", rawPassword))
                .andExpect(status().isUnauthorized)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn()
                .response.contentAsString

        val wrongPasswordResult =
            mockMvc
                .perform(loginRequest("admin-wrong-password", "틀린비밀번호"))
                .andExpect(status().isUnauthorized)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn()
                .response.contentAsString

        val notFoundDetail = extractField(notFoundResult, "detail")
        val wrongPasswordDetail = extractField(wrongPasswordResult, "detail")
        assertThat(notFoundDetail).isEqualTo(wrongPasswordDetail)
    }

    @Test
    fun `loginId 또는 password가 비어 있으면 400과 VALIDATION_FAILED를 반환한다`() {
        mockMvc
            .perform(loginRequest("", rawPassword))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))

        mockMvc
            .perform(loginRequest("admin-blank-password", ""))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `발급된 refresh 토큰으로 POST api-auth-refresh 하면 새 토큰 쌍이 온다`() {
        persistAdmin(loginId = "admin-refresh-check", name = "관리자")
        val loginBody =
            mockMvc
                .perform(loginRequest("admin-refresh-check", rawPassword))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        val refreshToken = extractField(loginBody, "refreshToken")

        mockMvc
            .perform(
                post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"refreshToken":"$refreshToken"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
    }

    private fun login(
        loginId: String,
        password: String,
    ): String {
        val body =
            mockMvc
                .perform(loginRequest(loginId, password))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        return extractField(body, "accessToken")
    }

    private fun persistAdmin(
        loginId: String,
        name: String,
    ): Admin =
        adminRepository.saveAndFlush(
            Admin(
                name = name,
                loginId = loginId,
                passwordHash = passwordEncoder.encode(rawPassword)!!,
                createdAt = OffsetDateTime.now(clock),
            ),
        )

    private fun loginRequest(
        loginId: String,
        password: String,
    ) = post("/api/auth/admin/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"loginId":"$loginId","password":"$password"}""")

    private fun extractField(
        responseBody: String,
        field: String,
    ): String =
        Regex(""""$field":"([^"]*)"""")
            .find(responseBody)
            ?.groupValues
            ?.get(1)
            ?: error("$field 를 응답에서 찾을 수 없습니다: $responseBody")
}
