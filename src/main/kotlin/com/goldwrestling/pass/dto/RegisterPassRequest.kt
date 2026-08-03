package com.goldwrestling.pass.dto

import com.goldwrestling.pass.EveningMembershipTerm
import com.goldwrestling.pass.PassType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 이용권 등록 요청(`POST /api/admin/members/{memberId}/passes`, PASS-01·PASS-02).
 *
 * **형식만 검증한다**(conventions §6) — "0.5의 배수", "양수", "타입별 필수·금지 조합" 같은 도메인
 * 규칙은 여기 넣지 않는다. `Pass.register`(companion object 팩토리)가 이미 그 전부를 강제하므로,
 * DTO에 같은 규칙을 중복해서 넣으면 두 곳이 어긋날 때 어느 쪽이 맞는지 알 수 없게 된다.
 * [initialCount]의 `@field:Digits`만 예외 — 이건 DB `DECIMAL(4,1)` 컬럼 자체의 형식 한계라
 * 도메인 규칙이 아니라 "숫자를 이 컬럼에 담을 수 있는 형식인가"의 문제다.
 */
@Schema(description = "이용권 등록 요청 — 저녁반 회비 / 예약제 횟수권 / 1:1 레슨권 공통")
data class RegisterPassRequest(
    @field:NotNull
    @field:Schema(description = "이용권 종류", example = "SESSION_PASS")
    val type: PassType,
    @field:Schema(
        description = "시작일. 지정하지 않으면 오늘로 채워진다(D-055). 과거 날짜도 그대로 등록된다 — 별도 검증 없음",
        example = "2026-08-01",
    )
    val startDate: LocalDate? = null,
    @field:Schema(
        description = "저녁반 회비 기간(1/3/6개월). EVENING_MEMBERSHIP에만 필요 — 횟수권·레슨권에 지정하면 거부된다",
        example = "ONE_MONTH",
    )
    val term: EveningMembershipTerm? = null,
    @field:Digits(integer = 3, fraction = 1)
    @field:Schema(
        description =
            "횟수권·레슨권의 초기 횟수(0.5 단위). SESSION_PASS·LESSON_PASS에만 필요 — " +
                "EVENING_MEMBERSHIP에 지정하면 거부된다",
        example = "10.0",
    )
    val initialCount: BigDecimal? = null,
)
