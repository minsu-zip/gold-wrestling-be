package com.goldwrestling.batch

/**
 * 배치 실행 결과 (glossary.md "배치 실행 결과", D-108 보강·D-113).
 */
enum class BatchExecutionStatus {
    /**
     * 경쟁 패배·대상 소진으로 인한 스킵은 정상 경로라 여기에 포함된다 — 스킵 건수는
     * `skippedCount`가 따로 센다.
     */
    SUCCESS,

    /** 회원 단위 처리 중 예외가 발생해 일부가 처리되지 못함 */
    PARTIAL_FAILURE,
}
