package com.goldwrestling.config

import com.goldwrestling.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * `SWAGGER_ENABLED=false`(운영 배포 설정)에서 `/v3/api-docs`·`/swagger-ui.html`이 실제로 404가
 * 되는지 증명하는 D-171의 유일한 관찰 지점이다.
 *
 * `SecurityConfig`의 Swagger permitAll 목록은 그대로 둔다 — `generateApiDocs`(D-029)가 로컬에서
 * 401 없이 스펙을 받아오기 위한 전제라 손대지 않는다. 대신 `springdoc.api-docs.enabled` ·
 * `springdoc.swagger-ui.enabled`를 꺼서 springdoc이 핸들러 자체를 등록하지 않게 하면, permitAll
 * 경로로 요청이 와도 매핑된 핸들러가 없어 404가 난다.
 *
 * `@SpringBootTest(properties = [...])`로 전역 springdoc 설정을 이 클래스에서만 덮어쓰므로 별도
 * `ApplicationContext`가 새로 뜬다(`JwtConfigTest`·`InactivityBatchPolicyLimitTest`와 동일 트레이드
 * 오프). 전역 설정을 직접 바꾸면 다른 모든 컨트롤러 테스트의 컨텍스트가 오염되므로, 컨텍스트 캐시
 * 비용을 감수하는 것이 맞다.
 */
@SpringBootTest(properties = ["springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"])
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class SwaggerDisabledTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `springdoc 비활성 시 v3 api-docs는 404를 반환한다`() {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound)
    }

    @Test
    fun `springdoc 비활성 시 swagger-ui html은 404를 반환한다`() {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound)
    }
}
