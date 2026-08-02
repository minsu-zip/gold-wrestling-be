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
        assertThat(applied.map { it.first }).contains("1", "2")
    }

    @Test
    fun `DB 세션 시간대가 Asia_Seoul 이다`() {
        val timeZone = jdbcClient.sql("SHOW TIME ZONE").query(String::class.java).single()

        assertThat(timeZone).isEqualTo("Asia/Seoul")
    }

    @Test
    fun `송파점 지점이 시드로 한 건 존재한다`() {
        val count =
            jdbcClient
                .sql("SELECT count(*) FROM branch WHERE name = '송파점'")
                .query(Long::class.java)
                .single()

        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `member 테이블은 branch_id NOT NULL FK 를 갖는다`() {
        val isNullable =
            jdbcClient
                .sql(
                    """
                    SELECT is_nullable FROM information_schema.columns
                    WHERE table_name = 'member' AND column_name = 'branch_id'
                    """.trimIndent(),
                ).query(String::class.java)
                .single()
        assertThat(isNullable).isEqualTo("NO")

        val fkCount =
            jdbcClient
                .sql(
                    """
                    SELECT count(*) FROM information_schema.table_constraints
                    WHERE constraint_name = 'fk_member_branch' AND constraint_type = 'FOREIGN KEY'
                    """.trimIndent(),
                ).query(Long::class.java)
                .single()
        assertThat(fkCount).isEqualTo(1)
    }

    @Test
    fun `admin 테이블에는 아직 로그인 자격 컬럼이 없다`() {
        val columnNames =
            jdbcClient
                .sql("SELECT column_name FROM information_schema.columns WHERE table_name = 'admin'")
                .query(String::class.java)
                .list()

        assertThat(columnNames).doesNotContain("password", "login_id", "password_hash")
    }
}
