package com.goldwrestling.batch

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

/**
 * 배치 실행 이력 조회·정리 쿼리(D-113·D-117).
 *
 * 실행 직렬화 자체는 이 인터페이스가 아니라 **DB 인덱스**(V10 `uq_batch_execution_running`)가
 * 보장한다 — 여기의 [findFirstByStatus]는 "지금 무엇이 돌고 있는지"를 **관측**하는 용도이지,
 * 조회 결과로 실행 여부를 판정하는 용도가 아니다. 조회 후 판정하면 조회와 INSERT 사이에 경쟁
 * 창이 남는다(D-021 "DB 제약 우선").
 */
interface BatchExecutionRepository : JpaRepository<BatchExecution, Long> {
    /** 실행 중인 이력 관측용 — `RUNNING` 행은 최대 1건이므로 `First`로 충분하다(D-117). */
    fun findFirstByStatus(status: BatchExecutionStatus): BatchExecution?

    /**
     * 앱이 비정상 종료해 `RUNNING`인 채로 남은 행을 죽은 것으로 보고 `FAILED`로 정리한다
     * (T-05D-11-01 — 이 설계의 알려진 약점). 정리하지 않으면 `RUNNING` 행 하나가 이후 모든 배치
     * 실행을 영구히 막는다.
     *
     * 대상은 `status = RUNNING`이면서 [threshold]보다 **이르게** 시작된 행뿐이다 — 지금 정상적으로
     * 돌고 있는 실행을 죽었다고 오판해 끊지 않기 위해서다.
     *
     * 반환값은 갱신된 행 수(0 또는 1)다. `@Modifying` 옵션 두 개의 이유는
     * `PassRepository.adjustRemainingCount`와 같다 — 벌크 UPDATE는 영속성 컨텍스트를 거치지 않고
     * DB에 직접 SQL을 보내므로, 이전 더티 상태를 먼저 반영해야 최신 값 위에서 실행되고
     * (`flushAutomatically`), 실행 후 컨텍스트를 비워야 1차 캐시의 갱신 전 스냅샷을 다시 읽는
     * 사고를 막는다(`clearAutomatically`).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update BatchExecution e set e.status = com.goldwrestling.batch.BatchExecutionStatus.FAILED, " +
            "e.finishedAt = :finishedAt, e.errorSummary = :errorSummary " +
            "where e.status = com.goldwrestling.batch.BatchExecutionStatus.RUNNING and e.startedAt < :threshold",
    )
    fun markStaleRunningAsFailed(
        @Param("threshold") threshold: OffsetDateTime,
        @Param("finishedAt") finishedAt: OffsetDateTime,
        @Param("errorSummary") errorSummary: String,
    ): Int

    /**
     * 관리자 실행 이력 목록(최근 실행순) — D-108이 "배치가 안 돌았는지는 실행 이력으로 확인한다"고
     * 했으나 지금은 확인할 수단이 없다(05-REVIEW.md WR-02). 05-15의 조회 API가 쓴다.
     *
     * `Pageable`로 건수를 제한한다 — 이력은 매일 쌓이므로 전체 조회 메서드를 두지 않는다.
     */
    fun findAllByOrderByStartedAtDesc(pageable: Pageable): List<BatchExecution>
}
