package com.goldwrestling.system

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 배선 확인용 시스템 엔드포인트. 도메인 API 가 아니므로 응답 형태는 계약 대상이 아니다.
 * (운영 상태 점검은 `/actuator/health` 를 사용한다)
 */
@RestController
@RequestMapping("/api/system")
@Tag(name = "system", description = "서버 상태·환경 확인")
class HealthController {
    @GetMapping("/health")
    @Operation(summary = "서버 기동 및 기준 시간대 확인")
    fun health(): HealthResponse =
        HealthResponse(
            status = "UP",
            serverTime = ZonedDateTime.now(),
            timeZone = ZoneId.systemDefault().id,
        )
}

data class HealthResponse(
    val status: String,
    val serverTime: ZonedDateTime,
    val timeZone: String,
)
