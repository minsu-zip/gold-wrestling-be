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
 * **트랜잭션 애노테이션을 붙이지 않는다**(05-01 결정 C, D-112). 트랜잭션은 [InactivityDeductionService]
 * (별도 빈)의 `deductOnce` **호출 1회 = 1개**이고, 부족분이 2회면 한 회원이 트랜잭션 2개를 쓴다
 * (회당 재선택 D-109의 결과). D-112가 "회원 1명 = 트랜잭션 1개"라고 부르는 것은 **실패 격리 단위**가
 * 회원이라는 뜻이다 — 한 회원의 예외는 아래 `try-catch`가 흡수하고 이미 커밋된 다른 회원의 차감은
 * 살아남는다. 여기에 트랜잭션 애노테이션을 붙이면 한 회원의 실패가 전원을 롤백하고, 같은 클래스
 * 내부 호출은 프록시를 우회해 경계가 생기지도 않는다.
 *
 * **`batch_execution`을 "오늘 이미 실행했나" 판단에 쓰지 않는다**(D-106). 멱등성의 근거는 원장의
 * `INACTIVITY` 건수다 — 실행 이력은 관측·복구 판단용이다.
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
    private val batchExecutionRepository: BatchExecutionRepository,
    private val clock: Clock,
) {
    /**
     * 배치 1회를 실행하고 저장된 실행 이력을 반환한다(수동 실행 API가 그대로 응답에 쓴다).
     *
     * 흐름: ① 대상 회원 벌크 조회 → ② 기준일 후보 4종 + `INACTIVITY` 이력 벌크 조회(비어 있으면
     * 건너뜀) → ③ 회원별로 [InactivityDueDateCalculator]로 기준일·부족분을 계산 →
     * ④ 부족분만큼 [InactivityDeductionService.deductOnce]를 반복 호출(대상 소진 시 그 회원은
     * 즉시 중단 — 남은 부족분은 다음 실행이 이어받는다) → ⑤ 회원 단위 예외를 흡수해 나머지 회원을
     * 계속 처리 → ⑥ `BatchExecution` 1건 저장.
     */
    fun run(
        trigger: BatchTrigger,
        triggeredByAdminId: Long?,
    ): BatchExecution {
        val startedAt = OffsetDateTime.now(clock)
        val today = LocalDate.now(clock)
        val resolvedAdminId = resolveTriggeredByAdminId(trigger, triggeredByAdminId)

        val memberIds = passRepository.findMemberIdsWithDeductibleSessionPass(today)

        var deductedCount = 0
        var skippedCount = 0
        val failedMembers = mutableListOf<Pair<Long, String>>()

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
                    if (shortfallCount == 0) continue

                    for (attempt in 1..shortfallCount) {
                        if (inactivityDeductionService.deductOnce(memberId)) {
                            deductedCount++
                        } else {
                            // 대상 소진 — 남은 부족분은 다음 실행이 상태 기반으로 이어받는다(D-106).
                            skippedCount++
                            break
                        }
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

        val finishedAt = OffsetDateTime.now(clock)

        return batchExecutionRepository.save(
            BatchExecution(
                trigger = trigger,
                triggeredByAdminId = resolvedAdminId,
                startedAt = startedAt,
                finishedAt = finishedAt,
                processedMemberCount = memberIds.size,
                deductedCount = deductedCount,
                skippedCount = skippedCount,
                status = status,
                errorSummary = errorSummary,
            ),
        )
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
     * 두 경로 모두 도메인 예외·`ErrorCode`를 새로 만들지 않는다(D-114 "새 에러코드는 추가하지
     * 않는다"). 이 두 예외는 여전히 `runner.run()`을 직접 호출하는 테스트
     * (`InactivityBatchFailureIsolationTest`)의 계약 검증용으로 남는다 — 프로그래밍 오류(잘못된
     * 인자로 러너를 직접 호출)를 조기에 드러내는 방어 코드다.
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
