package com.goldwrestling.member.dto

import com.goldwrestling.member.PhoneNumberNormalizer
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * 온보딩 제출 요청(policies §5.1) — 실명·전화번호 형식만 검증한다. **도메인 규칙(온보딩 허용 여부,
 * 상태 전이)은 이 DTO의 일이 아니다** — `MemberStateGate`가 서비스 계층에서 검사한다(conventions §6).
 *
 * [phoneNumber]의 `@Pattern`은 [PhoneNumberNormalizer.PHONE_NUMBER_PATTERN] 상수를 그대로 참조한다.
 * 정규식을 여기 다시 적으면 한쪽만 고쳐지는 사고가 난다 — Kotlin 애노테이션 인자는 컴파일 타임 상수여야
 * 하므로 `PhoneNumberNormalizer`의 `PHONE_NUMBER_PATTERN`이 `const val`로 선언되어 있어야 이 참조가 된다.
 */
@Schema(description = "온보딩 제출 요청 — 실명·전화번호(최초 1회만 허용, D-042)")
data class OnboardingRequest(
    @field:NotBlank
    @field:Size(max = 50)
    @field:Schema(description = "실명", example = "홍길동")
    val name: String,
    @field:NotBlank
    @field:Pattern(regexp = PhoneNumberNormalizer.PHONE_NUMBER_PATTERN, message = "휴대폰 번호 형식이 올바르지 않습니다.")
    @field:Schema(description = "휴대폰 번호(하이픈 포함/미포함 모두 허용)", example = "010-1234-5678")
    val phoneNumber: String,
)
