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
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.OffsetDateTime

/**
 * 회원 공지 열람 2개 엔드포인트의 HTTP 계약을 검증한다(NOTICE-02). 애노테이션 조합은
 * `AdminNoticeControllerTest`와 맞춘다(conventions §10.1).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class MemberNoticeControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var noticeRepository: NoticeRepository

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
    fun `회원 토큰으로 공지 목록과 상세를 200으로 조회한다`() {
        val member = persistMember()
        val token = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken
        val notice = persistNotice(title = "공지 제목", content = "공지 본문")

        val list = getList(token)
        assertThat(noticeIdsOf(list.get("content"))).contains(notice.id)

        val detail = getDetail(token, notice.id!!)
        assertThat(detail.get("title").asText()).isEqualTo("공지 제목")
        assertThat(detail.get("content").asText()).isEqualTo("공지 본문")
    }

    @Test
    fun `없는 id로 상세를 조회하면 404와 NOTICE_NOT_FOUND다`() {
        val member = persistMember()
        val token = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken

        mockMvc
            .perform(get("$NOTICES_PATH/$MISSING_NOTICE_ID").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"))
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private fun getList(token: String): JsonNode {
        val raw =
            mockMvc
                .perform(get(NOTICES_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isOk)
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

    private fun noticeIdsOf(contentArray: JsonNode): List<Long> = (0 until contentArray.size()).map { contentArray[it].get("id").asLong() }

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

    private fun persistNotice(
        title: String,
        content: String,
    ): Notice {
        val admin = persistAdmin()
        val now = OffsetDateTime.now()
        val notice =
            noticeRepository.saveAndFlush(
                Notice(
                    title = title,
                    content = content,
                    createdByAdmin = admin,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        createdNoticeIds += notice.id!!
        return notice
    }

    companion object {
        const val KAKAO_ID_BASE = 9_740_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-notice-member-ctrl-"
        private const val NOTICES_PATH = "/api/members/notices"
        private const val MISSING_NOTICE_ID = 9_999_999L
    }
}
