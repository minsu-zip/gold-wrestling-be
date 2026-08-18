package com.goldwrestling.notice

import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.notice.dto.CreateNoticeRequest
import com.goldwrestling.notice.dto.UpdateNoticeRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

/**
 * [NoticeService]의 CRUD·트랜잭션·시각 관례를 협력자를 가짜 객체로 두고 검증한다(conventions
 * §10.1) — DB가 필요한 최신순 페이지·hard delete 자체는 이미 `NoticeRepositoryTest`(06-02)가
 * Testcontainers로 증명했고, 여기서는 서비스가 그 리포지토리를 올바른 인자로 부르는지·`Clock`으로
 * 시각을 채우는지·없는 id에서 [NoticeNotFoundException]을 던지는지를 본다.
 *
 * `noticeRepository.save`는 실제 JPA `IDENTITY` 전략처럼 인자로 받은 엔티티에 생성된 [Notice.id]를
 * 채워 그대로 반환하도록 스텁한다(`@GeneratedValue(IDENTITY)`가 `persist` 시점에 넘겨받은 인스턴스에
 * id를 직접 채워 넣는 실제 동작을 흉내낸다) — 그렇지 않으면 `NoticeDetailResponse.from`의
 * `requireNotNull(id)`가 가짜 저장소 위에서 실패해 응답 변환 자체를 검증할 수 없다.
 */
class NoticeServiceTest {
    private val noticeRepository = mock(NoticeRepository::class.java)
    private val adminRepository = mock(AdminRepository::class.java)
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-08-18T01:00:00Z"), ZoneOffset.UTC)
    private val service = NoticeService(noticeRepository, adminRepository, fixedClock)
    private val idSequence = AtomicLong(1)

    init {
        willAnswer { invocation ->
            val notice = invocation.getArgument<Notice>(0)
            assignId(notice, idSequence.getAndIncrement())
            notice
        }.given(noticeRepository).save(any(Notice::class.java))
    }

    @Test
    fun `공지를 등록하면 createdAt과 updatedAt이 Clock 기준 같은 시각으로 채워진다`() {
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin()))

        val response = service.create(ADMIN_ID, CreateNoticeRequest(title = "제목", content = "본문"))

        assertThat(response.title).isEqualTo("제목")
        assertThat(response.content).isEqualTo("본문")
        assertThat(response.createdAt).isEqualTo(OffsetDateTime.now(fixedClock))
        assertThat(response.updatedAt).isEqualTo(response.createdAt)
    }

    @Test
    fun `공지를 수정하면 title과 content가 바뀌고 updatedAt만 갱신되며 createdAt은 불변이다`() {
        val original = notice(id = NOTICE_ID, createdAt = OffsetDateTime.parse("2026-08-01T00:00:00Z"))
        given(noticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(original))

        val response = service.update(NOTICE_ID, UpdateNoticeRequest(title = "수정된 제목", content = "수정된 본문"))

        assertThat(response.title).isEqualTo("수정된 제목")
        assertThat(response.content).isEqualTo("수정된 본문")
        assertThat(response.updatedAt).isEqualTo(OffsetDateTime.now(fixedClock))
        assertThat(response.createdAt).isEqualTo(OffsetDateTime.parse("2026-08-01T00:00:00Z"))
    }

    @Test
    fun `공지를 삭제하면 리포지토리에서 그 행이 실제로 삭제된다`() {
        val target = notice(id = NOTICE_ID)
        given(noticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(target))

        service.delete(NOTICE_ID)

        verify(noticeRepository).delete(target)
    }

    @Test
    fun `없는 id로 상세를 조회하면 NoticeNotFoundException이다`() {
        given(noticeRepository.findById(MISSING_ID)).willReturn(Optional.empty())

        assertThatThrownBy { service.getDetail(MISSING_ID) }
            .isInstanceOf(NoticeNotFoundException::class.java)
    }

    @Test
    fun `없는 id로 수정하면 NoticeNotFoundException이다`() {
        given(noticeRepository.findById(MISSING_ID)).willReturn(Optional.empty())

        assertThatThrownBy {
            service.update(MISSING_ID, UpdateNoticeRequest(title = "제목", content = "본문"))
        }.isInstanceOf(NoticeNotFoundException::class.java)
    }

    @Test
    fun `없는 id로 삭제하면 NoticeNotFoundException이다`() {
        given(noticeRepository.findById(MISSING_ID)).willReturn(Optional.empty())

        assertThatThrownBy { service.delete(MISSING_ID) }
            .isInstanceOf(NoticeNotFoundException::class.java)
    }

    @Test
    fun `목록 조회는 최신순 페이지를 PageResponse로 변환한다`() {
        val page = PageImpl(listOf(notice(id = NOTICE_ID)))
        given(noticeRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, 20))).willReturn(page)

        val result = service.getList(page = 0, size = 20)

        assertThat(result.content).hasSize(1)
        assertThat(result.content.first().id).isEqualTo(NOTICE_ID)
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun admin(): Admin =
        Admin(
            name = "관리자",
            loginId = "admin-notice-service-test",
            passwordHash = "{noop}not-used-in-this-test",
            createdAt = OffsetDateTime.now(fixedClock),
        )

    private fun notice(
        id: Long,
        createdAt: OffsetDateTime = OffsetDateTime.now(fixedClock),
    ): Notice {
        val notice =
            Notice(
                title = "원래 제목",
                content = "원래 본문",
                createdByAdmin = admin(),
                createdAt = createdAt,
                updatedAt = createdAt,
            )
        assignId(notice, id)
        return notice
    }

    /**
     * 실제 JPA `IDENTITY` 전략이 `persist` 시점에 하는 일(생성된 id를 그 인스턴스에 직접 채움)을
     * 가짜 저장소 위에서 흉내낸다. `Notice.id`는 `val`(불변 식별자, conventions §3)이라 생성자
     * 밖에서 세팅할 방법이 리플렉션뿐이다 — 프로덕션 코드 경로에는 전혀 관여하지 않는다.
     */
    private fun assignId(
        notice: Notice,
        id: Long,
    ) {
        val idField = Notice::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(notice, id)
    }

    private companion object {
        const val ADMIN_ID = 1L
        const val NOTICE_ID = 10L
        const val MISSING_ID = 999L
    }
}
