package com.goldwrestling.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * springdoc 이 생성하는 스펙의 메타데이터.
 * 생성 결과(`docs/api/openapi.yaml`)가 FE·BE 간 유일한 API 계약이다.
 */
@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            // 기본값은 요청 URL(예: http://localhost:8080)이 그대로 박혀 생성 환경마다 diff 가 생긴다.
            // 커밋되는 계약 파일이므로 환경 비의존적인 상대 경로로 고정한다.
            .servers(listOf(Server().url("/")))
            .info(
                Info()
                    .title("골드레슬링 API")
                    .description("골드레슬링 체육관 회원 관리·예약 시스템 백엔드 API")
                    .version("v0.0.1"),
            )
}
