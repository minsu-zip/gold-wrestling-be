package com.goldwrestling.auth

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/**
 * `/api/auth/refresh`·`/api/auth/logout`의 계약(problem+json 실패 응답 포함)을 검증하는
 * 통합테스트. 애노테이션 조합은 `HealthControllerTest`와 동일하게 `@SpringBootTest` +
 * `@AutoConfigureMockMvc` + `@Import(TestcontainersConfiguration::class)`를 쓴다(컨텍스트 캐시 재사용).
 * `@Transactional`은 테스트 프레임워크가 트랜잭션 경계만 감쌀 뿐 스프링 컨텍스트 시그니처에는
 * 영향을 주지 않는다 — MockMvc 요청이 같은 스레드에서 실행되므로 테스트 후 롤백으로 격리한다.
 *
 * 아직 로그인 엔드포인트가 없어(02-06·02-07에서 추가 예정), 유효한 refresh 토큰이 필요할 때는
 * `TokenService.issueTokenPair(...)`를 직접 호출해 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@Transactional
class TokenControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Test
    fun `유효한 refreshToken으로 갱신하면 200과 새 토큰 쌍이 반환되고 refreshToken이 회전된다`() {
        val pair = issueValidTokenPair(kakaoId = 2001L)

        mockMvc
            .perform(
                post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshRequestBody(pair.refreshToken)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").value(not(pair.refreshToken)))
    }

    @Test
    fun `같은 refreshToken을 두 번 보내면 두 번째는 401과 REFRESH_TOKEN_INVALID를 반환한다`() {
        val pair = issueValidTokenPair(kakaoId = 2002L)
        val body = refreshRequestBody(pair.refreshToken)

        mockMvc
            .perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)

        mockMvc
            .perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"))
    }

    @Test
    fun `refreshToken 필드가 비어 있으면 400과 VALIDATION_FAILED를 반환한다`() {
        mockMvc
            .perform(
                post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshRequestBody("")),
            ).andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `요청 본문이 JSON이 아니면 400과 MALFORMED_REQUEST를 반환한다`() {
        mockMvc
            .perform(
                post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("이건 JSON이 아닙니다"),
            ).andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
    }

    @Test
    fun `유효한 refreshToken으로 로그아웃하면 204이고 그 토큰으로 refresh하면 401이다`() {
        val pair = issueValidTokenPair(kakaoId = 2003L)

        mockMvc
            .perform(
                post("/api/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshRequestBody(pair.refreshToken)),
            ).andExpect(status().isNoContent)

        mockMvc
            .perform(
                post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshRequestBody(pair.refreshToken)),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `존재하지 않는 refreshToken으로 로그아웃해도 204다 (멱등)`() {
        mockMvc
            .perform(
                post("/api/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshRequestBody("존재하지-않는-refresh-토큰-값")),
            ).andExpect(status().isNoContent)
    }

    private fun issueValidTokenPair(kakaoId: Long): TokenPair {
        val branch = branchRepository.findByName("송파점")!!
        val member =
            memberRepository.saveAndFlush(
                Member(
                    branch = branch,
                    name = null,
                    phoneNumber = null,
                    kakaoId = kakaoId,
                    createdAt = OffsetDateTime.now(),
                ),
            )
        return tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)
    }

    private fun refreshRequestBody(refreshToken: String): String = """{"refreshToken":"$refreshToken"}"""
}
