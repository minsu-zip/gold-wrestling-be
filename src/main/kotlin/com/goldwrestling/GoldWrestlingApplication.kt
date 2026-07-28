package com.goldwrestling

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import java.util.TimeZone

@SpringBootApplication
@ConfigurationPropertiesScan
class GoldWrestlingApplication

fun main(args: Array<String>) {
    // 배포 환경(컨테이너)의 기본 시간대가 UTC여도 서버 기준은 항상 Asia/Seoul.
    // 스프링 컨텍스트가 뜨기 전에 JVM 기본 시간대를 고정한다.
    TimeZone.setDefault(TimeZone.getTimeZone(SEOUL_ZONE_ID))
    runApplication<GoldWrestlingApplication>(*args)
}

const val SEOUL_ZONE_ID = "Asia/Seoul"
