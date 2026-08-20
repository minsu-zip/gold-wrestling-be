package com.goldwrestling

import org.springframework.boot.fromApplication
import org.springframework.boot.with

/**
 * 로컬에서 docker-compose 없이 앱을 띄우고 싶을 때 사용하는 실행 진입점.
 * Testcontainers 가 일회용 PostgreSQL 을 올려서 붙여 준다.
 */
fun main(args: Array<String>) {
    fromApplication<GoldWrestlingApplication>().with(TestcontainersConfiguration::class).run(*args)
}
