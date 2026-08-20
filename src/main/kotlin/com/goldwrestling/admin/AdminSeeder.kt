package com.goldwrestling.admin

import com.goldwrestling.config.AdminSeedProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

/**
 * 첫 관리자 계정을 기동 시 멱등하게 만드는 시드 (D-038).
 *
 * [com.goldwrestling.branch.Branch] 시드(D-031)와 **의도적으로 다른 방식**이다. Branch명 같은 데이터는
 * "모든 환경에 같은 값"이 들어가야 해 Flyway `INSERT`가 적합하지만, 관리자 비밀번호는 **환경마다
 * 달라야 하는 시크릿**이다. Flyway 플레이스홀더로 넣으면 값이 비어 있는 채 한 번 적용되는 순간 깨진
 * 해시(빈 문자열 또는 리터럴)가 그 환경 DB에 영구히 박힌다 — 이미 적용된 마이그레이션은 재실행되지
 * 않고 커밋된 마이그레이션 수정도 금지라 되돌리려면 새 마이그레이션이 필요해진다(RESEARCH Pitfall 2).
 * `ApplicationRunner`는 매 기동마다 실행돼 존재 여부를 다시 확인하므로 이 문제가 없다.
 *
 * `AdminBranch`(담당 지점 연결)는 이번 phase 스코프가 아니라 만들지 않는다 — 지점 범위 인가는 v2
 * 후보(CROSS-01)이고, v1은 지점이 송파점 하나뿐이라 관리자-지점 매핑 없이도 운영에 지장이 없다.
 *
 * 단일 쓰기 작업이라 서비스 계층 없이 클래스에 직접 `@Transactional`을 붙인다 — 조회·검증·저장이
 * 한 트랜잭션에서 원자적으로 끝나야 두 인스턴스가 동시에 기동해도(또는 재기동 사이에) 중복 생성
 * 여지가 최소화된다.
 */
@Component
@Transactional
class AdminSeeder(
    private val adminRepository: AdminRepository,
    private val passwordEncoder: PasswordEncoder,
    private val adminSeedProperties: AdminSeedProperties,
    private val clock: Clock,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val loginId = adminSeedProperties.loginId
        val password = adminSeedProperties.password

        if (loginId.isBlank() || password.isBlank()) {
            logger.warn(
                "관리자 시드 환경변수가 비어 있어 시드를 건너뜁니다 (ADMIN_SEED_LOGIN_ID / ADMIN_SEED_PASSWORD)",
            )
            return
        }

        if (adminRepository.existsByLoginId(loginId)) {
            logger.info("관리자 시드 계정이 이미 존재해 건너뜁니다: loginId={}", loginId)
            return
        }

        val seedName = adminSeedProperties.name.ifBlank { "관리자" }
        // PasswordEncoder.encode()의 반환 타입은 플랫폼상 nullable(@Nullable)이지만, 이 지점은 이미
        // 위에서 blank 입력을 걸러낸 뒤라 non-null 입력에는 non-null 결과가 보장된다(Contract).
        val encodedPassword =
            checkNotNull(passwordEncoder.encode(password)) { "비밀번호 해싱 결과가 비어 있습니다." }
        adminRepository.save(
            Admin(
                name = seedName,
                loginId = loginId,
                passwordHash = encodedPassword,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
        logger.info("관리자 시드 계정을 생성했습니다: loginId={}", loginId)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AdminSeeder::class.java)
    }
}
