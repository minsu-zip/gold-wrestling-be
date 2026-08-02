package com.goldwrestling.admin

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.config.AdminSeedProperties
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * `AdminSeeder`의 멱등성·해싱·빈 환경변수 스킵을 검증하는 통합테스트(D-038).
 *
 * 이 클래스만 `@SpringBootTest(properties = [...])`로 `goldwrestling.admin-seed.*`를 테스트 전용
 * loginId로 덮어써 컨텍스트를 새로 띄운다 — 기본 컨텍스트(다른 테스트들이 공유)는 시드 환경변수가
 * 전부 비어 있어(.env 없음) 시드가 항상 스킵되므로, 실제 생성·멱등 동작을 보려면 이 재정의가 필요하다.
 * `AdminSeeder`는 이 컨텍스트가 기동하는 시점에 이미 한 번 `run()`이 실행됐을 수 있으므로,
 * 아래 테스트는 그 사실에 기대지 않고 **직접 두 번 호출**해 멱등성을 스스로 증명한다.
 */
@SpringBootTest(
    properties = [
        "goldwrestling.admin-seed.login-id=test-admin-seed-idempotency",
        "goldwrestling.admin-seed.password=test-seed-password-1234",
        "goldwrestling.admin-seed.name=테스트관리자",
    ],
)
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class AdminSeederTest {
    @Autowired
    private lateinit var adminSeeder: AdminSeeder

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `시드 환경변수가 채워져 있으면 관리자 1명이 생성되고 두 번 실행해도 수가 늘지 않는다`() {
        val loginId = "test-admin-seed-idempotency"
        val rawPassword = "test-seed-password-1234"

        adminSeeder.run(DefaultApplicationArguments())
        adminSeeder.run(DefaultApplicationArguments())

        val matches = adminRepository.findAll().filter { it.loginId == loginId }
        assertThat(matches).hasSize(1)

        val created = matches.single()
        assertThat(created.name).isEqualTo("테스트관리자")
        assertThat(created.passwordHash).startsWith("{bcrypt}")
        assertThat(created.passwordHash).isNotEqualTo(rawPassword)
        assertThat(passwordEncoder.matches(rawPassword, created.passwordHash)).isTrue()
    }

    @Test
    fun `loginId 또는 password가 비어 있으면 관리자가 생성되지 않고 예외도 나지 않는다`() {
        val blankSeeder =
            AdminSeeder(
                adminRepository = adminRepository,
                passwordEncoder = passwordEncoder,
                adminSeedProperties = AdminSeedProperties(loginId = "", password = "", name = ""),
                clock = clock,
            )
        val beforeCount = adminRepository.count()

        blankSeeder.run(DefaultApplicationArguments())

        assertThat(adminRepository.count()).isEqualTo(beforeCount)
    }
}
