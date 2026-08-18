package com.goldwrestling.notice

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.auth.PrincipalType
import com.goldwrestling.auth.TokenService
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.MemberStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.OffsetDateTime

/**
 * 관리자 공지 CRUD 5개 엔드포인트의 HTTP 계약을 검증한다(NOTICE-01). 애노테이션 조합은
 * `AdminBatchControllerTest`와 맞춘다(conventions §10.1) — `TestClockConfiguration`은 쓰지
 * 않는다(clock 고정이 필요한 정책 판정이 없다, `Clock` 실제 시각 그대로 사용).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class AdminNoticeControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private var fixtureCounter = 0L
    private val createdNoticeIds = mutableListOf<Long>()

    @AfterEach
    fun cleanUp() {
        if (createdNoticeIds.isNotEmpty()) {
            jdbcClient
                .sql("delete from notice where id in (:ids)")
                .param("ids", createdNoticeIds)
                .update()
            createdNoticeIds.clear()
        }
        // refresh_token이 member/admin을 FK로 참조한다 — 토큰 발급이 만든 행을 먼저 지운다
        // (AdminBatchControllerTest와 동일 관례).
        jdbcClient
            .sql("delete from refresh_token where member_id in (select id from member where kakao_id >= :base)")
            .param("base", KAKAO_ID_BASE)
            .update()
        jdbcClient
            .sql(
                "delete from refresh_token where admin_id in " +
                    "(select id from admin where login_id like :prefix)",
            ).param("prefix", "$ADMIN_LOGIN_PREFIX%")
            .update()
        jdbcClient
            .sql("delete from member where kakao_id >= :base")
            .param("base", KAKAO_ID_BASE)
            .update()
        jdbcClient
            .sql("delete from admin where login_id like :prefix")
            .param("prefix", "$ADMIN_LOGIN_PREFIX%")
            .update()
    }

    @Test
    fun `등록한 공지를 목록과 상세에서 조회하고 수정한 뒤 삭제하면 이후 조회가 404다`() {
        val token = adminToken()

        val created = create(token, title = "8월 휴관 안내", content = "8월 15일은 휴관합니다.")
        val noticeId = created.get("id").asLong()

        val list = getList(token)
        assertThat(noticeIdsOf(list.get("content"))).contains(noticeId)

        val detail = getDetail(token, noticeId)
        assertThat(detail.get("title").asText()).isEqualTo("8월 휴관 안내")
        assertThat(detail.get("content").asText()).isEqualTo("8월 15일은 휴관합니다.")

        val updated = update(token, noticeId, title = "8월 휴관 안내(수정)", content = "8월 15~16일은 휴관합니다.")
        assertThat(updated.get("title").asText()).isEqualTo("8월 휴관 안내(수정)")
        assertThat(
            OffsetDateTime.parse(updated.get("updatedAt").asText()),
        ).isAfterOrEqualTo(OffsetDateTime.parse(updated.get("createdAt").asText()))

        mockMvc
            .perform(delete("$NOTICES_PATH/$noticeId").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isNoContent)
        createdNoticeIds.remove(noticeId)

        mockMvc
            .perform(get("$NOTICES_PATH/$noticeId").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"))
    }

    @Test
    fun `없는 id를 수정하거나 삭제하면 404와 NOTICE_NOT_FOUND다`() {
        val token = adminToken()

        mockMvc
            .perform(
                patch("$NOTICES_PATH/$MISSING_NOTICE_ID")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("title" to "제목", "content" to "본문"))),
            ).andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"))

        mockMvc
            .perform(delete("$NOTICES_PATH/$MISSING_NOTICE_ID").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"))
    }

    @Test
    fun `제목이 빈 문자열이면 400과 VALIDATION_FAILED다`() {
        val token = adminToken()

        mockMvc
            .perform(
                post(NOTICES_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("title" to "", "content" to "본문"))),
            ).andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `회원 토큰으로 공지 등록을 호출하면 403과 ACCESS_DENIED다`() {
        val member = persistMember()
        val memberToken = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken

        mockMvc
            .perform(
                post(NOTICES_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $memberToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("title" to "제목", "content" to "본문"))),
            ).andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private fun create(
        token: String,
        title: String,
        content: String,
    ): JsonNode {
        val raw =
            mockMvc
                .perform(
                    post(NOTICES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("title" to title, "content" to content))),
                ).andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        val node = objectMapper.readTree(raw)
        createdNoticeIds += node.get("id").asLong()
        return node
    }

    private fun update(
        token: String,
        noticeId: Long,
        title: String,
        content: String,
    ): JsonNode {
        val raw =
            mockMvc
                .perform(
                    patch("$NOTICES_PATH/$noticeId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("title" to title, "content" to content))),
                ).andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        return objectMapper.readTree(raw)
    }

    private fun getDetail(
        token: String,
        noticeId: Long,
    ): JsonNode {
        val raw =
            mockMvc
                .perform(get("$NOTICES_PATH/$noticeId").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        return objectMapper.readTree(raw)
    }

    private fun getList(token: String): JsonNode {
        val raw =
            mockMvc
                .perform(get(NOTICES_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        return objectMapper.readTree(raw)
    }

    private fun adminToken(): String {
        val admin = persistAdmin()
        return tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
    }

    private fun persistAdmin(): Admin {
        fixtureCounter++
        return adminRepository.saveAndFlush(
            Admin(
                name = "관리자",
                loginId = "$ADMIN_LOGIN_PREFIX$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(),
            ),
        )
    }

    private fun persistMember(): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(
            Member(
                branch = branchRepository.findByName("송파점")!!,
                name = "회원$fixtureCounter",
                phoneNumber = "010000${KAKAO_ID_BASE + fixtureCounter}",
                status = MemberStatus.ACTIVE,
                kakaoId = KAKAO_ID_BASE + fixtureCounter,
                createdAt = OffsetDateTime.now(),
            ),
        )
    }

    private fun noticeIdsOf(contentArray: JsonNode): List<Long> = (0 until contentArray.size()).map { contentArray[it].get("id").asLong() }

    companion object {
        const val KAKAO_ID_BASE = 9_730_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-notice-ctrl-"
        private const val NOTICES_PATH = "/api/admin/notices"
        private const val MISSING_NOTICE_ID = 9_999_999L
    }
}
