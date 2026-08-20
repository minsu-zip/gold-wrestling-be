package com.goldwrestling.notice

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.OffsetDateTime

/**
 * `NoticeRepository.findAllByOrderByCreatedAtDescIdDesc`(최신순 페이지)와 hard delete(D-131)를
 * 실제 PostgreSQL(Testcontainers)에서 증명하는 통합테스트. 애노테이션 조합은 `AttendanceRepositoryTest`와
 * 동일하게 맞춘다(conventions §10.1).
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class NoticeRepositoryTest {
    @Autowired
    private lateinit var noticeRepository: NoticeRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private var fixtureCounter = 0L
    private val createdNoticeIds = mutableListOf<Long>()

    @AfterEach
    fun cleanUp() {
        if (createdNoticeIds.isNotEmpty()) {
            jdbcClient
                .sql("delete from notice where id in (:ids)")
                .param("ids", createdNoticeIds)
                .update()
            createdNoticeIds.clear()
        }
        jdbcClient
            .sql("delete from admin where login_id like :prefix")
            .param("prefix", "$ADMIN_LOGIN_PREFIX%")
            .update()
    }

    @Test
    fun `공지 목록은 최신순으로 조회된다`() {
        val admin = persistAdmin()
        persistNotice(admin, title = "1번째 공지", createdAt = FIXED_TIME)
        persistNotice(admin, title = "2번째 공지", createdAt = FIXED_TIME.plusDays(1))
        val latest = persistNotice(admin, title = "3번째 공지", createdAt = FIXED_TIME.plusDays(2))

        val page = noticeRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, 10))

        assertThat(page.content).hasSizeGreaterThanOrEqualTo(3)
        assertThat(page.content.first().id).isEqualTo(latest.id)
    }

    @Test
    fun `삭제한 공지는 조회되지 않는다`() {
        val admin = persistAdmin()
        val notice = persistNotice(admin, title = "삭제될 공지", createdAt = FIXED_TIME)

        noticeRepository.delete(notice)
        createdNoticeIds.remove(notice.id)

        assertThat(noticeRepository.findById(notice.id!!)).isEmpty()
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun persistAdmin(): Admin {
        fixtureCounter++
        return adminRepository.saveAndFlush(
            Admin(
                name = "관리자",
                loginId = "$ADMIN_LOGIN_PREFIX$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = FIXED_TIME,
            ),
        )
    }

    private fun persistNotice(
        admin: Admin,
        title: String,
        createdAt: OffsetDateTime,
    ): Notice {
        val notice =
            noticeRepository.saveAndFlush(
                Notice(
                    title = title,
                    content = "본문",
                    createdByAdmin = admin,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                ),
            )
        createdNoticeIds += notice.id!!
        return notice
    }

    companion object {
        const val ADMIN_LOGIN_PREFIX = "admin-notice-repository-"
        val FIXED_TIME: OffsetDateTime = OffsetDateTime.parse("2026-08-02T10:00:00+09:00")
    }
}
