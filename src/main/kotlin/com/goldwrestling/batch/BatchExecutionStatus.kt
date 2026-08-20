package com.goldwrestling.batch

/**
 * 배치 실행 결과 (glossary.md "배치 실행 결과", D-108 보강·D-113·D-117).
 *
 * 실행은 [RUNNING]으로 시작해 나머지 세 값 중 하나로 확정된다 — 확정 없이 [RUNNING]으로 남는
 * 경로가 없어야 다음 실행이 막히지 않는다(05-REVIEW.md WR-02).
 */
enum class BatchExecutionStatus {
    /**
     * 실행 시작 시점에 삽입되는 행의 상태 — 아직 끝나지 않아 `finishedAt`이 null이다.
     * **이 상태의 행은 동시에 1건만 존재할 수 있다**(V10 `uq_batch_execution_running`, D-117) —
     * 이 유일성이 배치 실행의 직렬화 장치다.
     */
    RUNNING,

    /**
     * 경쟁 패배·대상 소진으로 인한 스킵은 정상 경로라 여기에 포함된다 — 스킵 건수는
     * `skippedCount`가 따로 센다.
     */
    SUCCESS,

    /** 회원 단위 처리 중 예외가 발생해 **일부** 회원이 처리되지 못함 — 나머지 회원의 차감은 반영됐다 */
    PARTIAL_FAILURE,

    /**
     * 실행 **전체**가 실패해 회원 처리를 끝내지 못함(대상 조회 실패, DB 연결 끊김, stale 정리 등).
     * [PARTIAL_FAILURE]와의 차이는 "일부만 실패"가 아니라 "루프를 완주하지 못했다"는 것이다 —
     * 이 값이 없으면 전체 실패가 이력에 한 줄도 남지 않는다(05-REVIEW.md WR-02).
     */
    FAILED,
}
