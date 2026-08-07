package com.goldwrestling.notification

import org.springframework.data.jpa.repository.JpaRepository

/**
 * 이 phase는 저장만 한다 — 조회 계약(미확인 카운트, 폴링 API 등)은 Phase 6이 정의한다.
 */
interface NotificationRepository : JpaRepository<Notification, Long>
