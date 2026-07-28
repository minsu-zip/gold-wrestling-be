package com.goldwrestling.db

import com.goldwrestling.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient

/**
 * Flyway 배선 검증: 마이그레이션이 실제 DB 에 적용되고 히스토리가 남는지 확인한다.
 * 스키마 변경을 Flyway 로만 한다는 규칙이 깨지면(ddl-auto 사용 등) 이 테스트가 먼저 무의미해진다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class FlywayMigrationIntegrationTest {
    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @Test
    fun `모든 마이그레이션이 성공 상태로 기록된다`() {
        val applied =
            jdbcClient
                .sql("SELECT version, success FROM flyway_schema_history ORDER BY installed_rank")
                .query { rs, _ -> rs.getString("version") to rs.getBoolean("success") }
                .list()

        assertThat(applied).isNotEmpty()
        assertThat(applied).allSatisfy { (_, success) -> assertThat(success).isTrue() }
        assertThat(applied.map { it.first }).contains("1")
    }

    @Test
    fun `DB 세션 시간대가 Asia_Seoul 이다`() {
        val timeZone = jdbcClient.sql("SHOW TIME ZONE").query(String::class.java).single()

        assertThat(timeZone).isEqualTo("Asia/Seoul")
    }
}
