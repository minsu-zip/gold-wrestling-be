package com.goldwrestling.reservation

import com.goldwrestling.admin.Admin
import com.goldwrestling.branch.Branch
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassType
import com.goldwrestling.schedule.ClassSchedule
import com.goldwrestling.schedule.ClassSession
import com.goldwrestling.schedule.ClassSessionStatus
import com.goldwrestling.schedule.ClassType
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * 예약 취소·변경 가능 여부 판정(policies §3, D-090) — 순수 Kotlin 단위테스트.
 * 스프링 컨텍스트 없이 `Reservation`의 판정 메서드 4종만 검증한다(add-domain-test §1).
 *
 * **당일 판정이 시각에 흔들리지 않음(Pitfall 3)**을 이 파일이 증명한다 — 판정 메서드가
 * `LocalDate`만 받으므로(시그니처에 `LocalDateTime`/`OffsetDateTime`/`Clock`이 없다) 하루 중
 * 어느 시각에 호출되든 같은 `today` 값이면 결과가 항상 같다. 이것이 구조적 증거다.
 */
class ReservationCancellationPolicyTest {
    // --- assertCancelableByMember: 당일 경계 3종(어제/오늘/내일) ---

    @Test
    fun `당일 예약은 회원이 취소할 수 없다`() {
        val reservation = reservation(classDate = TODAY)

        assertThatThrownBy { reservation.assertCancelableByMember(TODAY) }
            .isInstanceOf(SameDayModificationNotAllowedException::class.java)
    }

    @Test
    fun `지난 수업은 회원이 취소할 수 없다 — 이미 소비된 예약이라 당일보다 강하게 막는다`() {
        val reservation = reservation(classDate = TODAY.minusDays(1))

        assertThatThrownBy { reservation.assertCancelableByMember(TODAY) }
            .isInstanceOf(SameDayModificationNotAllowedException::class.java)
    }

    @Test
    fun `내일 수업은 회원이 취소할 수 있다`() {
        val reservation = reservation(classDate = TODAY.plusDays(1))

        assertThatCode { reservation.assertCancelableByMember(TODAY) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `이미 취소된 예약은 회원이 다시 취소할 수 없다`() {
        val reservation = reservation(classDate = TODAY.plusDays(1), status = ReservationStatus.CANCELED)

        assertThatThrownBy { reservation.assertCancelableByMember(TODAY) }
            .isInstanceOf(ReservationAlreadyCanceledException::class.java)
    }

    // --- assertCancelableByAdmin: 당일·과거 제약 없음 ---

    @Test
    fun `관리자는 당일 예약도 취소할 수 있다`() {
        val reservation = reservation(classDate = TODAY)

        assertThatCode { reservation.assertCancelableByAdmin() }
            .doesNotThrowAnyException()
    }

    @Test
    fun `관리자는 지난 수업도 취소할 수 있다`() {
        val reservation = reservation(classDate = TODAY.minusDays(1))

        assertThatCode { reservation.assertCancelableByAdmin() }
            .doesNotThrowAnyException()
    }

    @Test
    fun `이미 취소된 예약은 관리자도 다시 취소할 수 없다`() {
        val reservation = reservation(classDate = TODAY, status = ReservationStatus.CANCELED)

        assertThatThrownBy { reservation.assertCancelableByAdmin() }
            .isInstanceOf(ReservationAlreadyCanceledException::class.java)
    }

    // --- assertChangeableByMember: 당일 검사 선행 + 종류 일치 검사 ---

    @Test
    fun `내일 예약은 같은 종류로 회원이 변경할 수 있다`() {
        val reservation = reservation(classDate = TODAY.plusDays(1), classType = ClassType.SESSION)

        assertThatCode { reservation.assertChangeableByMember(TODAY, ClassType.SESSION) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `당일 예약은 변경으로도 취소를 우회할 수 없다`() {
        val reservation = reservation(classDate = TODAY, classType = ClassType.SESSION)

        assertThatThrownBy { reservation.assertChangeableByMember(TODAY, ClassType.SESSION) }
            .isInstanceOf(SameDayModificationNotAllowedException::class.java)
    }

    @Test
    fun `회원은 다른 종류의 수업으로 변경할 수 없다`() {
        val reservation = reservation(classDate = TODAY.plusDays(1), classType = ClassType.SESSION)

        assertThatThrownBy { reservation.assertChangeableByMember(TODAY, ClassType.LESSON) }
            .isInstanceOf(ReservationTypeMismatchException::class.java)
    }

    // --- assertChangeableByAdmin: 당일 제약 없음 + 종류 일치 검사 ---

    @Test
    fun `관리자는 당일 예약도 같은 종류로 변경할 수 있다`() {
        val reservation = reservation(classDate = TODAY, classType = ClassType.LESSON)

        assertThatCode { reservation.assertChangeableByAdmin(ClassType.LESSON) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `관리자도 다른 종류의 수업으로는 변경할 수 없다`() {
        val reservation = reservation(classDate = TODAY, classType = ClassType.LESSON)

        assertThatThrownBy { reservation.assertChangeableByAdmin(ClassType.SESSION) }
            .isInstanceOf(ReservationTypeMismatchException::class.java)
    }

    private fun reservation(
        classDate: LocalDate,
        classType: ClassType = ClassType.SESSION,
        status: ReservationStatus = ReservationStatus.ACTIVE,
    ): Reservation {
        val branch = Branch(name = "송파점")
        val admin =
            Admin(
                name = "관리자",
                loginId = "admin1",
                passwordHash = "hash",
                createdAt = FIXED_TIME,
            )
        val member =
            Member(
                branch = branch,
                name = "홍길동",
                phoneNumber = "01012345678",
                status = MemberStatus.ACTIVE,
                kakaoId = 1L,
                createdAt = FIXED_TIME,
            )
        val pass =
            Pass(
                member = member,
                branch = branch,
                registeredBy = admin,
                type = if (classType == ClassType.LESSON) PassType.LESSON_PASS else PassType.SESSION_PASS,
                status = PassStatus.ACTIVE,
                startDate = TODAY.minusYears(1),
                endDate = TODAY.plusYears(1),
                remainingCount = java.math.BigDecimal("1.0"),
                createdAt = FIXED_TIME,
            )
        val schedule =
            ClassSchedule(
                branch = branch,
                dayOfWeek = DayOfWeek.MONDAY,
                classType = classType,
                startTime = LocalTime.of(19, 0),
                endTime = LocalTime.of(20, 30),
                capacity = 10,
                createdAt = FIXED_TIME,
            )
        val session =
            ClassSession(
                classSchedule = schedule,
                classDate = classDate,
                classType = classType,
                startTime = LocalTime.of(19, 0),
                endTime = LocalTime.of(20, 30),
                capacity = 10,
                reservedCount = 1,
                status = ClassSessionStatus.SCHEDULED,
                createdAt = FIXED_TIME,
            )
        return Reservation(
            member = member,
            classSession = session,
            pass = pass,
            classType = classType,
            classDate = classDate,
            startTime = LocalTime.of(19, 0),
            status = status,
            reservedAt = FIXED_TIME,
            createdAt = FIXED_TIME,
        )
    }

    companion object {
        private val FIXED_TIME: OffsetDateTime = OffsetDateTime.parse("2026-08-01T00:00:00+09:00")
        private val TODAY: LocalDate = LocalDate.of(2026, 8, 1)
    }
}
