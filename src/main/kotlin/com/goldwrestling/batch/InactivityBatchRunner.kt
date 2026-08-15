package com.goldwrestling.batch

import com.goldwrestling.SEOUL_ZONE_ID
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.reservation.ReservationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * 2주 미사용 자동 차감(BATCH-01·02·04, policies §4.3)의 실행 1회를 오케스트레이션한다 — 벌크
 * 조회(05-04) → 순수 계산(05-03) → 회원별 차감(05-05)을 엮고 결과를 `batch_execution` 이력
 * 1건으로 남긴다.
 *
 * ### 실행 흐름은 시작 → 본문 → 확정 세 구간이다 (CR-01·WR-02)
 * [start]가 `RUNNING` 이력 1건을 **먼저** 넣고, [runStarted]가 본문을 돌린 뒤 같은 행을
 * `SUCCESS`/`PARTIAL_FAILURE`/`FAILED`로 확정한다. [run]은 둘을 이어 붙인 편의 진입점이다.
 * - **직렬화**: 실행 중 행의 유일성(V10 `uq_batch_execution_running`)이 곧 실행 직렬화 장치다
 *   (D-117). 두 실행이 겹치면 뒤에 온 쪽의 INSERT를 DB가 거부해 [BatchAlreadyRunningException]
 *   (409)이 되고 본문은 아예 돌지 않는다. 애플리케이션 락이 아니라 DB 제약이므로 인스턴스가
 *   늘어나도 같은 보장이 유지된다(D-021 "동시성은 DB 제약으로").
 * - **확정 보장**: 본문이 어떻게 끝나든(예외 포함) 행이 `RUNNING`으로 방치되지 않는다 —
 *   방치된 행 하나가 이후 모든 실행을 영구히 막기 때문이다(WR-02, T-05D-13-02).
 *
 * **트랜잭션 애노테이션을 붙이지 않는다**(05-01 결정 C, D-112). 트랜잭션은 [InactivityDeductionService]
 * (별도 빈)의 `deductOnce` **호출 1회 = 1개**이고, 부족분이 2회면 한 회원이 트랜잭션 2개를 쓴다
 * (회당 재선택 D-109의 결과). D-112가 "회원 1명 = 트랜잭션 1개"라고 부르는 것은 **실패 격리 단위**가
 * 회원이라는 뜻이다 — 한 회원의 예외는 아래 `try-catch`가 흡수하고 이미 커밋된 다른 회원의 차감은
 * 살아남는다. 여기에 트랜잭션 애노테이션을 붙이면 한 회원의 실패가 전원을 롤백하고, 같은 클래스
 * 내부 호출은 프록시를 우회해 경계가 생기지도 않는다. 시작·확정 기록만 [BatchExecutionRecorder]
 * (별도 빈)의 `REQUIRES_NEW` 트랜잭션에서 일어난다 — 그래야 `RUNNING` 행이 본문이 끝나기 전에
 * 커밋돼 다른 실행에게 보인다.
 *
 * **`batch_execution`을 "오늘 이미 실행했나" 판단에 쓰지 않는다**(D-106). 이 테이블은 이제 중복
 * 실행 거부에 쓰이지만, **부족분 계산의 근거는 여전히 원장(`PassTransaction`)의 `INACTIVITY`
 * 건수뿐이다.** 둘을 섞으면 상태 기반 캐치업(D-106)이 무너진다.
 *
 * 휴회 예외는 대상 회원 조회 필터(현재 `ON_LEAVE` 제외)와 기준일 후보 ③(복귀일 리셋) **두 곳으로만**
 * 구현된다 — 휴회 일수를 경과일에서 빼는 세 번째 메커니즘을 만들지 않는다(RESEARCH Pitfall 2).
 */
@Component
class InactivityBatchRunner(
    private val passRepository: PassRepository,
    private val reservationRepository: ReservationRepository,
    private val passTransactionRepository: PassTransactionRepository,
    private val memberRepository: MemberRepository,
    private val adminRepository: AdminRepository,
    private val inactivityDeductionService: InactivityDeductionService,
    private val recorder: BatchExecutionRecorder,
    private val clock: Clock,
) {
    /**
     * 배치 1회를 시작해 끝까지 돌리고 **확정된** 실행 이력을 반환한다 — 시그니처는 05-08까지와
     * 같아서 기존 호출부(cron·수동 실행 API·통합테스트)가 그대로 쓴다.
     *
     * 이미 실행 중이면 [BatchAlreadyRunningException]이 나고 **본문은 돌지 않으며 새 이력도 남지
     * 않는다.** 실행을 시작한 뒤 본문이 터지면 이력이 `FAILED`로 확정된 다음 원래 예외가 그대로
     * 전파된다.
     */
    fun run(
        trigger: BatchTrigger,
        triggeredByAdminId: Long?,
    ): BatchExecution {
        val started = start(trigger, triggeredByAdminId)
        return runStarted(requireNotNull(started.id) { "시작된 배치 실행 이력에 id가 없습니다." })
    }

    /**
     * 실행을 **시작만** 한다 — `RUNNING` 이력 1건을 즉시 커밋하고 그 엔티티를 반환한다.
     *
     * **관리자 해석이 [BatchExecutionRecorder.start]보다 먼저다 — 이 순서 자체가 계약이다.**
     * `MANUAL`인데 관리자 id가 없거나 존재하지 않으면 이력을 **한 줄도 남기지 않고** 거부해야
     * 하는데, 순서가 뒤집히면 잘못된 요청이 `RUNNING` 행을 만들었다가 확정하는 흔적을 남긴다
     * (`InactivityBatchFailureIsolationTest`가 이 계약을 고정한다).
     *
     * [BatchAlreadyRunningException]은 잡지 않고 그대로 전파한다 — 거부는 정상 경로이고, 그것을
     * 어떻게 표현할지는 호출부가 정한다(cron은 로그로 흡수, HTTP는 409).
     */
    fun start(
        trigger: BatchTrigger,
        triggeredByAdminId: Long?,
    ): BatchExecution {
        val resolvedAdminId = resolveTriggeredByAdminId(trigger, triggeredByAdminId)
        return recorder.start(trigger = trigger, triggeredByAdminId = resolvedAdminId)
    }

    /**
     * [start]가 만든 실행([executionId])의 본문을 돌리고 이력을 확정한 뒤 확정된 엔티티를 반환한다.
     *
     * 흐름: ① 대상 회원 벌크 조회 → ② 기준일 후보 4종 + `INACTIVITY` 이력 벌크 조회(비어 있으면
     * 건너뜀) → ③ 회원별로 [InactivityDueDateCalculator]로 기준일·부족분을 계산 →
     * ④ 부족분만큼 [InactivityDeductionService.deductOnce]를 반복 호출(대상 소진 시 그 회원은
     * 즉시 중단 — 남은 부족분은 다음 실행이 이어받는다) → ⑤ 회원 단위 예외를 흡수해 나머지 회원을
     * 계속 처리 → ⑥ 이력 확정.
     *
     * **집계 변수를 `try` 밖에 선언한다** — 본문이 중간에 터져도 그 시점까지의 진행 상황(몇 명을
     * 처리했고 몇 회를 깎았는지)이 `FAILED` 이력에 남아야 운영자가 복구 범위를 판단할 수 있다.
     */
    fun runStarted(executionId: Long): BatchExecution {
        val today = LocalDate.now(clock)

        var processedMemberCount = 0
        var deductedCount = 0
        var skippedCount = 0
        val failedMembers = mutableListOf<Pair<Long, String>>()

        try {
            val memberIds = passRepository.findMemberIdsWithDeductibleSessionPass(today)
            processedMemberCount = memberIds.size

            if (memberIds.isNotEmpty()) {
                val lastActiveReservationClassDates =
                    reservationRepository.findLastActiveReservationClassDates(memberIds).associate { it.getMemberId() to it.getDate() }
                val returnedFromLeaveDates =
                    memberRepository
                        .findReturnedFromLeaveTimestamps(memberIds)
                        .associate { it.getMemberId() to it.getTimestamp()?.toSeoulLocalDate() }
                val lastSessionPassRegistrationDates =
                    passRepository
                        .findLastDeductibleSessionPassRegistrationDates(memberIds, today)
                        .associate { it.getMemberId() to it.getTimestamp()?.toSeoulLocalDate() }
                val lastPositiveAdjustDates =
                    passTransactionRepository
                        .findLastPositiveAdjustTimestamps(memberIds)
                        .associate { it.getMemberId() to it.getTimestamp()?.toSeoulLocalDate() }
                val inactivityEventDatesByMember =
                    passTransactionRepository
                        .findInactivityEventTimestamps(memberIds)
                        .mapNotNull { row -> row.getTimestamp()?.let { row.getMemberId() to it.toSeoulLocalDate() } }
                        .groupBy({ it.first }, { it.second })

                for (memberId in memberIds) {
                    try {
                        val candidates =
                            InactivityDueDateCandidates(
                                // Phase 6이 Attendance를 도입하면 여기에 값을 채운다(D-105 후보 ①)
                                lastAttendanceDate = null,
                                lastActiveReservationClassDate = lastActiveReservationClassDates[memberId],
                                returnedFromLeaveDate = returnedFromLeaveDates[memberId],
                                lastSessionPassRegistrationDate = lastSessionPassRegistrationDates[memberId],
                                lastPositiveAdjustDate = lastPositiveAdjustDates[memberId],
                            )

                        val dueDate = InactivityDueDateCalculator.resolveDueDate(candidates) ?: continue
                        val shortfallCount =
                            InactivityDueDateCalculator.shortfall(
                                dueDate,
                                today,
                                inactivityEventDatesByMember[memberId].orEmpty(),
                            )

                        // 반복 횟수를 세는 인덱스를 쓰지 않는다 — 회차 번호는 어디에도 쓰이지 않고
                        // (회당 재선택 D-109이라 회차마다 대상이 달라진다), 소진 시 `break`가
                        // 필요해 `repeat`(람다 안에서 break 불가)도 쓸 수 없다.
                        var remainingShortfall = shortfallCount
                        while (remainingShortfall > 0) {
                            if (inactivityDeductionService.deductOnce(memberId)) {
                                deductedCount++
                            } else {
                                // 대상 소진 — 남은 부족분은 다음 실행이 상태 기반으로 이어받는다(D-106).
                                skippedCount++
                                break
                            }
                            remainingShortfall--
                        }
                    } catch (e: Exception) {
                        // 진단 정보(메시지·스택)는 로그로만 남긴다 — errorSummary는 관리자 수동 실행
                        // 응답(D-114 `BatchExecutionResponse`)으로 그대로 나가므로 예외 메시지를 담으면
                        // DB 제약조건명·SQL 조각이 API로 새어 나간다(conventions §8, D-017).
                        logger.error("미사용 차감 배치에서 회원 처리 실패 (memberId={})", memberId, e)
                        failedMembers += memberId to e.javaClass.simpleName
                    }
                }
            }

            val status = if (failedMembers.isEmpty()) BatchExecutionStatus.SUCCESS else BatchExecutionStatus.PARTIAL_FAILURE
            val errorSummary =
                failedMembers
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(separator = "; ") { (memberId, message) -> "memberId=$memberId: $message" }
                    ?.take(MAX_ERROR_SUMMARY_LENGTH)

            return recorder.finish(
                executionId = executionId,
                status = status,
                processedMemberCount = processedMemberCount,
                deductedCount = deductedCount,
                skippedCount = skippedCount,
                errorSummary = errorSummary,
            )
        } catch (e: Exception) {
            logger.error("미사용 차감 배치 실행이 전체 실패했습니다. (executionId={})", executionId, e)
            // 확정 기록이 또 실패해도 **원인 예외를 가리지 않는다** — 원래 예외를 던져야 호출부와
            // 로그가 진짜 원인을 본다. 확정에 실패해 남은 `RUNNING` 행은 stale 정리(기본 30분,
            // D-117)가 뒤늦게 회수한다.
            runCatching {
                recorder.finish(
                    executionId = executionId,
                    status = BatchExecutionStatus.FAILED,
                    processedMemberCount = processedMemberCount,
                    deductedCount = deductedCount,
                    skippedCount = skippedCount,
                    // 여기서도 예외 **종류**만 담는다(회원 단위 실패와 같은 이유).
                    errorSummary = e.javaClass.simpleName,
                )
            }.onFailure {
                logger.error("배치 실행 이력을 FAILED로 확정하지 못했습니다. (executionId={})", executionId, it)
            }
            throw e
        }
    }

    /**
     * `trigger = MANUAL`이면 **관리자 존재를 확인한 뒤 그 id를 반환**하고, `SCHEDULED`면 항상
     * null이다(`ck_batch_execution_trigger`).
     *
     * **존재 검증을 없애고 FK(`fk_batch_execution_admin`) 위반에 맡기지 않는다**(D-117) — 그러면
     * "관리자 없음"과 "이미 실행 중(`uq_batch_execution_running`)"이 둘 다
     * `DataIntegrityViolationException`이 되어, 실행 시작 경로가 두 원인을 구분해 409로 변환할 수
     * 없게 된다.
     *
     * **두 예외 모두 `AdminBatchController`의 HTTP 경로에서는 도달 불가하다**(05-08, PR #14 리뷰
     * Info 이월 확인):
     * - `requireNotNull(triggeredByAdminId)`: 호출부(`AdminBatchController.runInactivityBatch`)가
     *   넘기는 값은 [AuthenticatedPrincipal.requireAdminId]의 반환값인데, 이 함수의 시그니처가
     *   non-null `Long`이라 컴파일 타임에 null이 될 수 없다 — Kotlin 타입 시스템이 보장한다.
     * - 관리자 조회 실패 `IllegalStateException`: `JwtAuthenticationFilter.authenticate`가 매
     *   요청마다 `AuthenticationPrincipalResolver.resolve`로 관리자 존재 여부를 먼저 확인하고,
     *   없으면(삭제됨) `null`을 반환해 인증 자체가 실패한다(401, 컨트롤러 진입 전 차단). 게다가
     *   `AdminRepository`에는 관리자 삭제 기능이 아예 없다(`JpaRepository` 상속 메서드를 호출하는
     *   곳이 저장소 어디에도 없음) — 그래서 이 예외는 지금 이 저장소에서 관리자를 삭제할 방법이
     *   생기기 전까지는 이론적으로도 발생하지 않는다.
     *
     * 두 경로 모두 도메인 예외·`ErrorCode`를 새로 만들지 않는다. 이 두 예외는 여전히
     * `runner.run()`을 직접 호출하는 테스트(`InactivityBatchFailureIsolationTest`)의 계약 검증용으로
     * 남는다 — 프로그래밍 오류(잘못된 인자로 러너를 직접 호출)를 조기에 드러내는 방어 코드다.
     * (D-114의 "새 에러코드는 추가하지 않는다"는 중복 실행 거부에 한해 D-118이 철회했다 — 이
     * 두 경로에는 여전히 적용된다.)
     */
    private fun resolveTriggeredByAdminId(
        trigger: BatchTrigger,
        triggeredByAdminId: Long?,
    ): Long? =
        when (trigger) {
            BatchTrigger.MANUAL -> {
                val adminId =
                    requireNotNull(triggeredByAdminId) { "MANUAL 트리거는 triggeredByAdminId가 필요합니다." }
                adminRepository.findById(adminId).orElseThrow {
                    IllegalStateException("배치를 수동 실행한 관리자(id=$adminId)를 찾을 수 없습니다.")
                }
                adminId
            }

            BatchTrigger.SCHEDULED -> {
                null
            }
        }

    private fun OffsetDateTime.toSeoulLocalDate(): LocalDate = atZoneSameInstant(ZoneId.of(SEOUL_ZONE_ID)).toLocalDate()

    companion object {
        private const val MAX_ERROR_SUMMARY_LENGTH = 1000
        private val logger = LoggerFactory.getLogger(InactivityBatchRunner::class.java)
    }
}
