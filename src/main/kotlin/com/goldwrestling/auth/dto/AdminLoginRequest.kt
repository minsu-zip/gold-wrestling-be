package com.goldwrestling.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "관리자 ID/PW 로그인 요청")
data class AdminLoginRequest(
    @field:NotBlank
    @field:Size(max = 50)
    @field:Schema(description = "관리자 로그인 ID", example = "YOUR_ADMIN_LOGIN_ID")
    val loginId: String,
    @field:NotBlank
    @field:Size(max = 100)
    @field:Schema(description = "비밀번호", example = "YOUR_ADMIN_PASSWORD")
    val password: String,
)
