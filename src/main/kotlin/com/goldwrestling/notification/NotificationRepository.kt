package com.goldwrestling.notification

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

/**
 * 관리자 알림 폴링·확인 처리 조회 계약(NOTIF-02, D-129).
 *
 * `JpaSpecificationExecutor`를 함께 상속해 둔다 — 06-10의 활동 피드(기간·종류 필터)가 이
 * 인터페이스를 다시 건드리지 않고 바로 쓸 수 있게 하기 위해서다(`PassRepository` 관례).
 */
interface NotificationRepository :
    JpaRepository<Notification, Long>,
    JpaSpecificationExecutor<Notification> {
    /** 전체 알림을 최신순(발생 시각 desc, 동률이면 id desc)으로 페이지 조회한다. */
    fun findAllByOrderByOccurredAtDescIdDesc(pageable: Pageable): Page<Notification>

    /** 미확인 알림만 최신순으로 페이지 조회한다 — "미확인만 보기" 필터의 실현부. */
    fun findAllByIsReadFalseOrderByOccurredAtDescIdDesc(pageable: Pageable): Page<Notification>

    /** 미확인 건수 — 목록 응답에 실려 별도 카운트 엔드포인트를 없앤다(D-129). */
    fun countByIsReadFalse(): Long

    /**
     * 미확인 알림을 전부 읽음 처리한다("모두 읽음", D-129 — 개별 읽음은 만들지 않는다).
     *
     * `where n.isRead = false` 조건이 두 가지를 함께 보장한다: ① 이미 읽은 알림의
     * [Notification.readAt]을 덮어쓰지 않는다 — 원래 확인 시각이 나중의 "모두 읽음" 호출 시각으로
     * 갱신되면 "언제 봤는지" 추적(T-06-33)이 깨진다. ② 갱신 대상이 없으면(전부 이미 읽음) 0을
     * 반환해 실질적인 no-op이 된다.
     *
     * **이 벌크 UPDATE는 영속성 컨텍스트를 우회한다** — 호출 전에 로드한 `Notification`을 그대로
     * 응답에 쓰지 말고 항상 재조회한다(`PassRepository.adjustRemainingCount` KDoc과 동일 경고).
     * `flushAutomatically`로 대기 중인 변경을 먼저 반영한 뒤 실행하고, `clearAutomatically`로
     * 실행 후 1차 캐시의 갱신 전 스냅샷을 지운다 — 지우지 않으면 같은 트랜잭션에서 재조회해도
     * 캐시된 stale 엔티티를 그대로 돌려받는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.isRead = true, n.readAt = :now where n.isRead = false")
    fun markAllAsRead(
        @Param("now") now: OffsetDateTime,
    ): Int
}
