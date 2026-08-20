package com.goldwrestling.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 첫 관리자 계정을 만드는 데 쓰이는 시드 자격 (D-038).
 *
 * `.env` 키 매핑:
 * - `loginId` ← `ADMIN_SEED_LOGIN_ID`
 * - `password` ← `ADMIN_SEED_PASSWORD` (평문으로 주입되고 서버가 해시해 저장한다)
 * - `name` ← `ADMIN_SEED_NAME`
 *
 * **이 값들, 특히 [password]는 시크릿이다 — 로그·예외 메시지에 담지 않는다.**
 * 셋 다 비워 두면 [com.goldwrestling.admin.AdminSeeder]가 시드를 건너뛴다(앱 기동은 실패하지 않는다).
 */
@ConfigurationProperties(prefix = "goldwrestling.admin-seed")
data class AdminSeedProperties(
    val loginId: String = "",
    val password: String = "",
    val name: String = "",
)
