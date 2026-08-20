package com.goldwrestling

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/**
 * 애플리케이션 컨텍스트가 실제 PostgreSQL 위에서 정상적으로 뜨는지 확인한다.
 * (Flyway 마이그레이션 + Hibernate 매핑 검증이 이 시점에 함께 수행된다)
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class GoldWrestlingApplicationTests {
    @Test
    fun contextLoads() {
    }
}
