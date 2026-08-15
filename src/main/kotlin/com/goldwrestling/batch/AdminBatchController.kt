package com.goldwrestling.batch

import com.goldwrestling.auth.AuthenticatedPrincipal
import com.goldwrestling.batch.dto.BatchExecutionResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 2주 미사용 자동 차감 배치의 관리자 수동 실행 API(BATCH-01·04, D-108·D-114). `SecurityConfig`에서
 * `/api/admin` 하위 전체가 `hasRole("ADMIN")` 전용이므로, **이 컨트롤러에는 별도 권한 애노테이션을
 * 붙이지 않는다**(D-040) — 역할 구분은 URL 인가 규칙이 담당한다.
 *
 * 경로가 복수형 `inactivity-runs`인 이유: 이 호출이 "배치 실행"이라는 리소스 1건을 새로 만드는
 * 것이라 REST 관례상 컬렉션에 대한 POST(리소스 생성)로 표현한다.
 *
 * 트랜잭션 애노테이션·`try-catch`를 붙이지 않는다 — 트랜잭션 경계는 [InactivityBatchRunner]와
 * 그 협력자(D-020·D-112), 에러 응답은 `GlobalExceptionHandler`가 담당한다(D-017).
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "admin-batch", description = "관리자 배치 수동 실행")
class AdminBatchController(
    private val runner: InactivityBatchRunner,
) {
    @PostMapping("/batch/inactivity-runs")
    @Operation(
        summary = "2주 미사용 자동 차감 배치를 즉시 실행한다",
        description =
            "중복 실행해도 안전하다 — 상태 기반 부족분 계산(D-106)이 이미 존재하는 차감을 다시 " +
                "만들지 않는다. 배치가 며칠 중단됐다면 이 호출 한 번이 밀린 주기를 몰아서 보정한다.",
    )
    fun runInactivityBatch(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
    ): BatchExecutionResponse =
        BatchExecutionResponse.from(
            runner.run(trigger = BatchTrigger.MANUAL, triggeredByAdminId = principal.requireAdminId()),
        )
}
