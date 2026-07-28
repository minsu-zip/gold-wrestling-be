package com.goldwrestling

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * DB 통합테스트용 PostgreSQL 컨테이너.
 *
 * `@ServiceConnection` 이 컨테이너의 접속 정보를 DataSource 로 자동 연결하므로
 * 테스트에서 `spring.datasource.*` 를 따로 지정하지 않는다.
 * 이미지 태그는 운영(docker-compose)과 동일한 메이저 버전으로 고정한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
}
