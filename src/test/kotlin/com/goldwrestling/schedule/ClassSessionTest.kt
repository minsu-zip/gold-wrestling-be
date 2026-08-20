package com.goldwrestling.schedule

import com.goldwrestling.branch.Branch
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * 수업 세션의 판정 전용 도메인 메서드 3종(`assertReservable`·`assertSuspendable`·`assertResumable`)
 * 단위테스트 — 스프링 컨텍스트 없이 순수 Kotlin으로 검증한다(conventions §10.0 "예약 가능 판정 →
 * 단위테스트 필수", add-domain-test §1). `PassCancellationTest`·`PassAdjustmentPolicyTest`와 같은 관례다.
 *
 * **이 테스트가 지키는 것은 판정 결과만이 아니다** — 세 메서드가 *상태를 바꾸지 않는다*는 D-072
 * 불변식도 함께 고정한다. 판정 메서드에 `status =` 같은 대입문이 들어가면 조건부 UPDATE로 막아 둔
 * 경쟁이 우회로로 되살아나고, 그게 Phase 3의 실제 회귀 버그(T-03-38) 원인이었다. 04-07(예약 생성)·
 * 04-14(휴강)가 이 메서드들을 호출하기 전에 여기서 잠가 둔다.
 */
class ClassSessionTest {
    @Test
    fun `예약 대상 수업이 정상 상태면 assertReservable이 통과한다`() {
        val session = session(classType = ClassType.SESSION, status = ClassSessionStatus.SCHEDULED)

        assertThatCode { session.assertReservable() }.doesNotThrowAnyException()
    }

    @Test
    fun `휴강된 수업에 assertReservable을 호출하면 ClassSessionCanceledException을 던진다`() {
        val session = session(classType = ClassType.SESSION, status = ClassSessionStatus.CANCELED)

        assertThatThrownBy { session.assertReservable() }
            .isInstanceOf(ClassSessionCanceledException::class.java)
    }

    @Test
    fun `저녁반에 assertReservable을 호출하면 ClassSessionNotReservableException을 던진다`() {
        val session = session(classType = ClassType.EVENING, status = ClassSessionStatus.SCHEDULED)

        assertThatThrownBy { session.assertReservable() }
            .isInstanceOf(ClassSessionNotReservableException::class.java)
    }

    @Test
    fun `휴강이면서 저녁반이면 휴강 사유가 먼저 판정된다`() {
        // 두 조건이 겹칠 때 어떤 예외가 나가는지를 고정한다 — 회원에게는 "예약 대상이 아님"보다
        // "휴강됨"이 더 구체적인 정보이고, 순서가 뒤집히면 FE의 에러 분기가 조용히 바뀐다.
        val session = session(classType = ClassType.EVENING, status = ClassSessionStatus.CANCELED)

        assertThatThrownBy { session.assertReservable() }
            .isInstanceOf(ClassSessionCanceledException::class.java)
    }

    @Test
    fun `1대1 레슨도 예약 대상이라 assertReservable이 통과한다`() {
        val session = session(classType = ClassType.LESSON, status = ClassSessionStatus.SCHEDULED)

        assertThatCode { session.assertReservable() }.doesNotThrowAnyException()
    }

    @Test
    fun `정상 수업은 assertSuspendable이 통과하고 이미 휴강이면 예외를 던진다`() {
        assertThatCode { session(status = ClassSessionStatus.SCHEDULED).assertSuspendable() }
            .doesNotThrowAnyException()

        assertThatThrownBy { session(status = ClassSessionStatus.CANCELED).assertSuspendable() }
            .isInstanceOf(ClassSessionCanceledException::class.java)
    }

    @Test
    fun `휴강된 수업은 assertResumable이 통과하고 정상 수업이면 예외를 던진다`() {
        assertThatCode { session(status = ClassSessionStatus.CANCELED).assertResumable() }
            .doesNotThrowAnyException()

        assertThatThrownBy { session(status = ClassSessionStatus.SCHEDULED).assertResumable() }
            .isInstanceOf(ClassSessionNotCanceledException::class.java)
    }

    @Test
    fun `판정 메서드는 세션의 상태와 예약 인원을 바꾸지 않는다`() {
        // D-072 불변식 — 판정은 판정만 한다. 실제 전환은 ClassSessionRepository의 조건부 UPDATE가 한다.
        val scheduled = session(status = ClassSessionStatus.SCHEDULED, reservedCount = 3)
        runCatching { scheduled.assertReservable() }
        runCatching { scheduled.assertSuspendable() }
        runCatching { scheduled.assertResumable() }

        assertThat(scheduled.status).isEqualTo(ClassSessionStatus.SCHEDULED)
        assertThat(scheduled.reservedCount).isEqualTo(3)
        assertThat(scheduled.canceledAt).isNull()
        assertThat(scheduled.cancelReason).isNull()
        assertThat(scheduled.canceledByAdmin).isNull()

        val canceled = session(status = ClassSessionStatus.CANCELED, reservedCount = 5)
        runCatching { canceled.assertReservable() }
        runCatching { canceled.assertSuspendable() }
        runCatching { canceled.assertResumable() }

        assertThat(canceled.status).isEqualTo(ClassSessionStatus.CANCELED)
        assertThat(canceled.reservedCount).isEqualTo(5)
    }

    /**
     * DB에 저장하지 않는 순수 인스턴스 — 판정 메서드는 연관 엔티티를 건드리지 않으므로
     * `classSchedule`은 저장되지 않은 최소 객체로 충분하다.
     */
    private fun session(
        classType: ClassType = ClassType.SESSION,
        status: ClassSessionStatus = ClassSessionStatus.SCHEDULED,
        reservedCount: Int = 0,
    ): ClassSession =
        ClassSession(
            classSchedule = schedule(classType),
            classDate = LocalDate.of(2026, 8, 10),
            classType = classType,
            startTime = LocalTime.of(11, 0),
            endTime = LocalTime.of(12, 30),
            capacity = 10,
            reservedCount = reservedCount,
            status = status,
            createdAt = OffsetDateTime.now(),
        )

    private fun schedule(classType: ClassType): ClassSchedule =
        ClassSchedule(
            branch = Branch(name = "송파점"),
            dayOfWeek = DayOfWeek.MONDAY,
            classType = classType,
            startTime = LocalTime.of(11, 0),
            endTime = LocalTime.of(12, 30),
            capacity = 10,
            createdAt = OffsetDateTime.now(),
        )
}
