package com.goldwrestling.batch

/**
 * 배치 트리거 종류 (glossary.md "배치 트리거 종류", D-108·D-114).
 */
enum class BatchTrigger {
    /** 매일 새벽 cron으로 자동 실행 (D-114 — 04:00 Asia/Seoul) */
    SCHEDULED,

    /** 관리자가 수동 실행 API(`POST /api/admin/batch/inactivity-runs`)로 트리거 (D-114) */
    MANUAL,
}
