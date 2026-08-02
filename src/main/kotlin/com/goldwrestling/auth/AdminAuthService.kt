package com.goldwrestling.auth

import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.auth.dto.AdminLoginResponse
import com.goldwrestling.auth.dto.AdminSummary
import com.goldwrestling.auth.dto.TokenPairResponse
import com.goldwrestling.common.error.DomainException
import com.goldwrestling.common.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 관리자 ID/PW 로그인(AUTH-03, D-026).
 *
 * [login]은 클래스 기본(`readOnly = true`)을 쓰기 트랜잭션으로 **반드시 오버라이드**해야 한다 —
 * [TokenService.issueTokenPair]의 `@Transactional`은 전파 기본값(REQUIRED)이라 새 트랜잭션을 열지 않고
 * 바깥 트랜잭션에 합류하는데, 바깥이 read-only면 refresh 토큰 INSERT가
 * `cannot execute INSERT in a read-only transaction`으로 거부된다(02-11 수동 검증에서 실제 발생).
 */
@Service
@Transactional(readOnly = true)
class AdminAuthService(
    private val adminRepository: AdminRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenService: TokenService,
) {
    /**
     * loginId가 없는 경우와 비밀번호가 틀린 경우 **완전히 같은 예외(같은 메시지)** 를 던진다 —
     * 응답만으로 "어느 loginId가 실제로 존재하는지"를 유추할 수 있는 계정 열거 공격을 막는다(T-02-24).
     */
    @Transactional
    fun login(
        loginId: String,
        rawPassword: String,
    ): AdminLoginResponse {
        val admin = adminRepository.findByLoginId(loginId)
        if (admin == null || !passwordEncoder.matches(rawPassword, admin.passwordHash)) {
            logger.warn("관리자 로그인 실패: loginId={}", loginId)
            throw InvalidCredentialsException()
        }

        val adminId = admin.id!!
        val tokenPair = tokenService.issueTokenPair(PrincipalType.ADMIN, adminId)
        return AdminLoginResponse(
            tokens = TokenPairResponse.from(tokenPair),
            admin = AdminSummary(adminId = adminId, name = admin.name),
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AdminAuthService::class.java)
    }
}

/**
 * loginId 없음 / 비밀번호 불일치 두 경우를 구분하지 않고 던지는 예외(T-02-24).
 *
 * `auth/AuthExceptions.kt`(02-04 소유 파일)를 이 플랜이 수정하면 파일 소유권이 겹쳐 병렬 실행 중
 * 충돌 위험이 생긴다 — 그래서 이 예외는 이 파일 안에 둔다.
 */
class InvalidCredentialsException(
    message: String = "아이디 또는 비밀번호가 올바르지 않습니다.",
) : DomainException(ErrorCode.INVALID_CREDENTIALS, message)
