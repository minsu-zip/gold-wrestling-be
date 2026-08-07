package com.goldwrestling.schedule

import com.goldwrestling.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient

/**
 * V7 시드(송파점 정기 시간표 52행)가 policies.md §2·CONTEXT.md "시드에 들어갈 타임" 표대로
 * DB에 존재하는지 실제 PostgreSQL(Testcontainers)에서 검증한다. `ClassScheduleRepository`가
 * 아직 없으므로(04-03이 만든다) `JdbcClient`로 직접 조회한다.
 * 애노테이션 조합은 `FlywayMigrationIntegrationTest`와 동일하게 유지한다(컨텍스트 캐시 재사용).
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class ClassScheduleSeedIntegrationTest {
    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @Test
    fun `class_schedule 전체 행 수는 52다`() {
        val count = countByType(null)

        assertThat(count).isEqualTo(52)
    }

    @Test
    fun `EVENING 시간표는 10행이고 전부 capacity가 NULL이다`() {
        val count = countByType("EVENING")
        assertThat(count).isEqualTo(10)

        val nonNullCapacityCount =
            jdbcClient
                .sql("SELECT count(*) FROM class_schedule WHERE class_type = 'EVENING' AND capacity IS NOT NULL")
                .query(Long::class.java)
                .single()
        assertThat(nonNullCapacityCount).isEqualTo(0)
    }

    @Test
    fun `SESSION 시간표는 16행이고 전부 capacity가 10이다`() {
        val count = countByType("SESSION")
        assertThat(count).isEqualTo(16)

        val wrongCapacityCount =
            jdbcClient
                .sql("SELECT count(*) FROM class_schedule WHERE class_type = 'SESSION' AND capacity <> 10")
                .query(Long::class.java)
                .single()
        assertThat(wrongCapacityCount).isEqualTo(0)
    }

    @Test
    fun `LESSON 시간표는 26행이고 전부 capacity가 1이다`() {
        val count = countByType("LESSON")
        assertThat(count).isEqualTo(26)

        val wrongCapacityCount =
            jdbcClient
                .sql("SELECT count(*) FROM class_schedule WHERE class_type = 'LESSON' AND capacity <> 1")
                .query(Long::class.java)
                .single()
        assertThat(wrongCapacityCount).isEqualTo(0)
    }

    @Test
    fun `모든 시간표 행의 end_time 은 start_time 보다 정확히 90분 뒤다`() {
        val mismatchCount =
            jdbcClient
                .sql(
                    """
                    SELECT count(*) FROM class_schedule
                    WHERE end_time <> start_time + INTERVAL '90 minutes'
                    """.trimIndent(),
                ).query(Long::class.java)
                .single()

        assertThat(mismatchCount).isEqualTo(0)
    }

    @Test
    fun `모든 시간표 행의 branch_id 는 송파점 id 다`() {
        val mismatchCount =
            jdbcClient
                .sql(
                    """
                    SELECT count(*) FROM class_schedule cs
                    WHERE cs.branch_id <> (SELECT id FROM branch WHERE name = '송파점')
                    """.trimIndent(),
                ).query(Long::class.java)
                .single()

        assertThat(mismatchCount).isEqualTo(0)
    }

    @Test
    fun `LESSON의 요일-시각 집합은 EVENING과 SESSION의 요일-시각 집합의 합집합과 같다`() {
        val lessonSlots = daySlots("LESSON")
        val evAndSessionSlots = daySlots("EVENING") + daySlots("SESSION")

        assertThat(lessonSlots).containsExactlyInAnyOrderElementsOf(evAndSessionSlots)
        // 총 개수만 비교하면 다른 (요일,시각) 26개가 들어가도 통과하므로, 집합 동일성까지 함께 본다.
        assertThat(evAndSessionSlots).doesNotHaveDuplicates()
    }

    private fun countByType(classType: String?): Long =
        if (classType == null) {
            jdbcClient.sql("SELECT count(*) FROM class_schedule").query(Long::class.java).single()
        } else {
            jdbcClient
                .sql("SELECT count(*) FROM class_schedule WHERE class_type = :classType")
                .param("classType", classType)
                .query(Long::class.java)
                .single()
        }

    private fun daySlots(classType: String): List<Pair<String, String>> =
        jdbcClient
            .sql("SELECT day_of_week, start_time FROM class_schedule WHERE class_type = :classType")
            .param("classType", classType)
            .query { rs, _ -> rs.getString("day_of_week") to rs.getString("start_time") }
            .list()
}
