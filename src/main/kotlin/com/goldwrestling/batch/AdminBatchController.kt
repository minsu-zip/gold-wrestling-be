package com.goldwrestling.batch

import com.goldwrestling.auth.AuthenticatedPrincipal
import com.goldwrestling.batch.dto.BatchExecutionResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * 2주 미사용 자동 차감 배치의 관리자 API(BATCH-01·04, D-108·D-114·D-117) — 실행 접수 1개 + 실행
 * 이력 조회 2개. `SecurityConfig`에서 `/api/admin` 하위 전체가 `hasRole("ADMIN")` 전용이므로,
 * **이 컨트롤러에는 별도 권한 애노테이션을 붙이지 않는다**(D-040) — 역할 구분은 URL 인가 규칙이
 * 담당한다.
 *
 * 경로가 복수형 `inactivity-runs`인 이유: 이 호출이 `batch_execution` 이력 1건을 남기므로 실행
 * 컬렉션에 대한 POST로 표현한다.
 *
 * **`201 Created`가 아니라 `202 Accepted`다.** 이 저장소에서 `201`은 클라이언트가 이후에 다시 다룰
 * 도메인 리소스를 만들 때 쓰는데(이용권 등록·예약 생성), 이 호출이 만드는 것은 리소스가 아니라
 * **아직 끝나지 않은 작업**이다. 서버는 "접수했다"만 말하고 결과는 나중에 생긴다 — 그것을 표현하는
 * 상태코드가 202다. 진행 상태를 볼 주소는 `Location` 헤더가 가리킨다(아래 단건 조회).
 *
 * 트랜잭션 애노테이션·`try-catch`를 붙이지 않는다 — 트랜잭션 경계는 [AdminBatchService]와
 * 그 협력자(D-020·D-112), 에러 응답은 `GlobalExceptionHandler`가 담당한다(D-017).
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "admin-batch", description = "관리자 배치 수동 실행·실행 이력 조회")
class AdminBatchController(
    private val adminBatchService: AdminBatchService,
) {
    // 실제 응답 상태는 ResponseEntity.accepted()가 결정한다(Spring MVC가 ResponseEntity 상태를
    // 항상 우선한다) — 이 애노테이션은 springdoc이 openapi.yaml에 202를 기술하게 하는 문서화 전용
    // 힌트다(AdminPassController.register의 201과 같은 이유).
    @PostMapping("/batch/inactivity-runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "2주 미사용 자동 차감 배치 실행을 접수한다 (비동기, 202)",
        description =
            "이 호출은 배치를 **시작만** 하고 즉시 202를 반환한다 — 응답 본문의 status는 RUNNING이고 " +
                "finishedAt은 null이다. 진행 상태와 최종 결과는 Location 헤더가 가리키는 단건 조회" +
                "(GET /api/admin/batch/inactivity-runs/{batchExecutionId})로 폴링한다. " +
                "**실행 중에 다시 호출하면 409(BATCH_ALREADY_RUNNING)로 거부되므로 재시도가 안전하다** — " +
                "동시에 실행 중일 수 있는 배치는 DB 제약으로 항상 최대 1건이다(D-117). " +
                "**순차 재실행도 안전하다** — 상태 기반 부족분 계산(D-106)이 이미 존재하는 차감을 다시 " +
                "만들지 않으므로 앞 실행이 끝난 뒤 다시 호출하면 그 실행의 deductedCount는 0이 된다. " +
                "배치가 며칠 중단됐다면 밀린 주기를 이어받되 **1회 실행당 회원 1명에게서 최대 1회**만 " +
                "차감한다(D-119) — 나머지는 다음 실행들이 이어받는다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "202",
            description = "실행 접수됨 (status = RUNNING, finishedAt = null)",
            headers = [
                Header(
                    name = "Location",
                    description = "이 실행의 진행 상태 조회 경로",
                    schema = Schema(type = "string"),
                ),
            ],
            content = [Content(schema = Schema(implementation = BatchExecutionResponse::class))],
        ),
        ApiResponse(responseCode = "401", description = "인증되지 않음 (UNAUTHENTICATED)", content = [Content()]),
        ApiResponse(responseCode = "403", description = "관리자 권한이 아님 (ACCESS_DENIED)", content = [Content()]),
        ApiResponse(
            responseCode = "409",
            description = "이미 실행 중인 배치가 있어 거부됨 (BATCH_ALREADY_RUNNING)",
            content = [Content()],
        ),
    )
    fun runInactivityBatch(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
    ): ResponseEntity<BatchExecutionResponse> {
        val response = BatchExecutionResponse.from(adminBatchService.launchInactivityRun(principal.requireAdminId()))
        return ResponseEntity
            .accepted()
            .location(URI.create("/api/admin/batch/inactivity-runs/${response.batchExecutionId}"))
            .body(response)
    }

    @GetMapping("/batch/inactivity-runs/{batchExecutionId}")
    @Operation(
        summary = "배치 실행 1건의 진행 상태·결과 조회",
        description =
            "202 응답의 Location이 가리키는 폴링 대상이다. status가 RUNNING이면 아직 실행 중이며 " +
                "집계 수치는 확정 전 값(0)이고 finishedAt은 null이다. 종료되면 SUCCESS · " +
                "PARTIAL_FAILURE(일부 회원 실패) · FAILED(실행 전체 실패) 중 하나가 된다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [Content(schema = Schema(implementation = BatchExecutionResponse::class))],
        ),
        ApiResponse(responseCode = "401", description = "인증되지 않음 (UNAUTHENTICATED)", content = [Content()]),
        ApiResponse(responseCode = "403", description = "관리자 권한이 아님 (ACCESS_DENIED)", content = [Content()]),
        ApiResponse(
            responseCode = "404",
            description = "그 id의 실행 이력이 없음 (BATCH_EXECUTION_NOT_FOUND)",
            content = [Content()],
        ),
    )
    fun getInactivityRun(
        @PathVariable batchExecutionId: Long,
    ): BatchExecutionResponse = BatchExecutionResponse.from(adminBatchService.getExecution(batchExecutionId))

    @GetMapping("/batch/inactivity-runs")
    @Operation(
        summary = "최근 배치 실행 이력 목록 (시작 시각 내림차순)",
        description =
            "\"어젯밤 배치가 돌았는가\"를 실행 이력으로 확인하는 창구다(D-108). 결과가 비어 있거나 " +
                "가장 최근 실행이 어제가 아니면 cron이 멈춘 것이다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "400", description = "limit이 1..100 범위를 벗어남 (VALIDATION_FAILED)", content = [Content()]),
        ApiResponse(responseCode = "401", description = "인증되지 않음 (UNAUTHENTICATED)", content = [Content()]),
        ApiResponse(responseCode = "403", description = "관리자 권한이 아님 (ACCESS_DENIED)", content = [Content()]),
    )
    fun listInactivityRuns(
        @Parameter(description = "조회 건수(1..100). 범위를 벗어나면 400이다")
        @RequestParam(defaultValue = "20")
        @Min(1)
        @Max(AdminBatchService.MAX_LIMIT.toLong())
        limit: Int,
    ): List<BatchExecutionResponse> = adminBatchService.listRecentExecutions(limit).map(BatchExecutionResponse::from)
}
