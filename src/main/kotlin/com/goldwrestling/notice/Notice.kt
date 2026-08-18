package com.goldwrestling.notice

import com.goldwrestling.admin.Admin
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * 공지사항 (glossary.md "공지사항", D-131) — 제목·본문만 존재한다(첨부·고정 없음).
 *
 * 수정·삭제(hard delete)가 가능한 도메인이라 `Notification`(append-only, 전부 `val`)이 아니라
 * `Reservation`의 가변 필드 관례를 따른다 — [title]·[content]·[updatedAt]이 `var`다. `@PreUpdate`는
 * 쓰지 않는다 — 이 저장소에 선례가 없고, 수정 시각은 서비스가 `Clock` 빈으로 명시 세팅한다.
 *
 * 본문([content])에 삽입된 값의 이스케이프는 이 서버의 책임이 아니다 — BE는 원문을 저장·전달만
 * 하고 렌더링 시점 이스케이프는 FE 책임이다(T-06-05).
 */
@Entity
@Table(name = "notice")
class Notice(
    @Column(nullable = false, length = 200)
    var title: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_admin_id", nullable = false)
    val createdByAdmin: Admin,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
