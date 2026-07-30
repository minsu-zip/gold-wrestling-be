package com.goldwrestling.common.error

import com.goldwrestling.TestcontainersConfiguration
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * FOUND-01: 스프링 내장 예외(404/405/400/415)와 도메인 예외가 전부 같은 모양
 * (`application/problem+json` + `code`)으로 응답하는지 증명하는 통합테스트.
 *
 * 400/415/500/도메인 예외를 트리거할 실제 프로덕션 엔드포인트가 이 phase엔 없다. `HealthController`에
 * 검증용 파라미터를 추가하면 그게 그대로 `docs/api/openapi.yaml`(FE 계약)에 새겨지므로, 여기서만 쓰는
 * 테스트 전용 컨트롤러([TestErrorController])를 이 테스트 클래스의 중첩 클래스로 두고, [Config]
 * (`@TestConfiguration`)의 `@Bean` 메서드로만 빈으로 등록한다 — 컴포넌트 스캔에 걸리는 최상위
 * `@Component` 클래스가 아니라 `@Import`로 명시 주입해야만 컨텍스트에 들어오므로 다른 통합테스트
 * 컨텍스트를 오염시키지 않는다(실제로 `HealthControllerTest`·`FlywayMigrationIntegrationTest`가 이
 * 컨트롤러 없이도 그대로 통과함을 회귀로 확인했다).
 *
 * 애노테이션 조합은 `HealthControllerTest`와 동일하게 `@SpringBootTest` + `@AutoConfigureMockMvc` +
 * `@Import(...)`를 쓴다(컨텍스트 캐시 재사용 — conventions §10.1). 다만 `@Import` 인자가 기존 두 테스트와
 * 달라(테스트 전용 컨트롤러 추가) 스프링 컨텍스트가 하나 더 뜬다 — FOUND-01의 모든 케이스를 이 한 클래스에
 * 모아 추가 컨텍스트를 1개로 묶는 트레이드오프를 택했다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, GlobalExceptionHandlerTest.Config::class)
class GlobalExceptionHandlerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `매핑되지 않은 경로는 404 problem+json 으로 응답하고 code 는 RESOURCE_NOT_FOUND 다`() {
        mockMvc
            .perform(get("/api/nonexistent"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `허용되지 않은 HTTP 메서드는 405 problem+json 으로 응답하고 code 는 METHOD_NOT_ALLOWED 다`() {
        mockMvc
            .perform(post("/api/system/health"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
    }

    @Test
    fun `요청 값 검증 실패는 400 problem+json 으로 응답하고 code 는 VALIDATION_FAILED 다`() {
        mockMvc
            .perform(
                post("/internal-test/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":""}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `본문 파싱 실패는 400 problem+json 으로 응답하고 code 는 MALFORMED_REQUEST 다`() {
        mockMvc
            .perform(
                post("/internal-test/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"""),
            ).andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
    }

    @Test
    fun `지원하지 않는 Content-Type 요청은 415 problem+json 으로 응답하고 code 는 UNSUPPORTED_MEDIA_TYPE 다`() {
        mockMvc
            .perform(
                post("/internal-test/validate")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("plain text"),
            ).andExpect(status().isUnsupportedMediaType)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
    }

    @Test
    fun `예상하지 못한 예외는 500 problem+json 으로 응답하고 code 는 INTERNAL_ERROR 이며 본문에 내부 정보가 없다`() {
        val result =
            mockMvc
                .perform(post("/internal-test/boom"))
                .andExpect(status().isInternalServerError)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn()

        val body = result.response.contentAsString
        assertThat(body).doesNotContain("internal-leak-marker")
        assertThat(body).doesNotContain("IllegalStateException")
        assertThat(body).doesNotContain("at com.goldwrestling")
    }

    @Test
    fun `도메인 예외는 그 예외의 errorCode 가 code 로 message 가 detail 로 응답된다`() {
        mockMvc
            .perform(post("/internal-test/domain-error"))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.detail").value("테스트 전용 도메인 예외입니다."))
    }

    @Test
    fun `모든 에러 응답 본문에는 trace 와 exception 필드가 없다`() {
        mockMvc
            .perform(post("/internal-test/boom"))
            .andExpect(jsonPath("$.trace").doesNotExist())
            .andExpect(jsonPath("$.exception").doesNotExist())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        fun testErrorController(): TestErrorController = TestErrorController()
    }

    @RestController
    @RequestMapping("/internal-test")
    class TestErrorController {
        @PostMapping("/validate", consumes = [MediaType.APPLICATION_JSON_VALUE])
        fun validate(
            @Valid @RequestBody request: SampleRequest,
        ): ResponseEntity<Void> = ResponseEntity.ok().build()

        @PostMapping("/boom")
        fun boom(): Nothing = throw IllegalStateException("internal-leak-marker")

        @PostMapping("/domain-error")
        fun domainError(): Nothing = throw SampleDomainException()
    }

    class SampleRequest(
        @field:NotBlank
        val name: String,
    )

    private class SampleDomainException :
        DomainException(
            errorCode = ErrorCode.VALIDATION_FAILED,
            message = "테스트 전용 도메인 예외입니다.",
        )
}
