package com.goldwrestling.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 허용할 프론트엔드 오리진. `.env` 의 `CORS_ALLOWED_ORIGINS`(쉼표 구분)로 주입된다.
 */
@ConfigurationProperties(prefix = "goldwrestling.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
)
