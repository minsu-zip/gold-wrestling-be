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
 * 경로가 복수형 `inactivity-runs`인 이유: 이 호출이 `batch_execution` 이력 1건을 남기므로 실행
 * 컬렉션에 대한 POST로 표현한다.
 *
 * **그런데도 `201 Created`가 아니라 `200 OK`다** — 이 저장소에서 `201`은 클라이언트가 이후에
 * 다시 다룰 도메인 리소스를 만들 때만 쓴다(이용권 등록·예약 생성 2곳뿐. 나머지 POST 15개는 200).
 * 배치 실행 이력은 조회 엔드포인트가 없어 `Location`으로 가리킬 곳이 없고, 클라이언트가 원하는
 * 것은 새 리소스의 주소가 아니라 **이번 실행의 결과 수치**다. 이력 조회 API가 생기면 그때
 * `201` + `Location`으로 바꾸는 것을 재검토한다.
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
            "**순차** 재실행은 안전하다 — 상태 기반 부족분 계산(D-106)이 이미 존재하는 차감을 " +
                "다시 만들지 않으므로, 응답을 받은 뒤 다시 호출하면 deductedCount가 0이 된다. " +
                "배치가 며칠 중단됐다면 이 호출 한 번이 밀린 주기를 몰아서 보정한다. " +
                "**동시 실행은 아직 안전하지 않다** — 이 호출이 끝나기 전에 또 호출하거나 새벽 " +
                "04:00 cron과 겹치면 같은 주기가 이중 차감될 수 있다(05-REVIEW.md CR-01). " +
                "차감 원자성을 보장하는 조건부 UPDATE가 잔여 음수만 막고 주기 중복은 막지 " +
                "않기 때문이다. 이전 응답을 받기 전에는 다시 호출하지 않는다.",
    )
    fun runInactivityBatch(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
    ): BatchExecutionResponse =
        BatchExecutionResponse.from(
            runner.run(trigger = BatchTrigger.MANUAL, triggeredByAdminId = principal.requireAdminId()),
        )
}
