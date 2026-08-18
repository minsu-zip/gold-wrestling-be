package com.goldwrestling.notice

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface NoticeRepository : JpaRepository<Notice, Long> {
    /**
     * 최신순 페이지 조회(NOTICE-01/02) — `createdAt`만으로 정렬하면 같은 시각(동률)에서 순서가
     * 불안정해 페이지 경계가 흔들릴 수 있어 `id`를 2차 정렬 키로 더해 결정적 순서를 보장한다.
     */
    fun findAllByOrderByCreatedAtDescIdDesc(pageable: Pageable): Page<Notice>
}
