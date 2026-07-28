package com.goldwrestling.system

import com.goldwrestling.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 웹 계층 + 보안 필터체인 배선 확인. 도메인 로직이 아니므로 이 테스트는 뼈대 검증용이다.
 *
 * Boot 4 주의: `@AutoConfigureMockMvc` 패키지가 3.x 의
 * `boot.test.autoconfigure.web.servlet` → `boot.webmvc.test.autoconfigure` 로 이동했다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class HealthControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `헬스 엔드포인트는 인증 없이 접근 가능하고 기준 시간대를 반환한다`() {
        mockMvc
            .perform(get("/api/system/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.timeZone").value("Asia/Seoul"))
    }
}
