package com.goldwrestling.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * 카카오 API(`kauth.kakao.com`, `kapi.kakao.com`) 호출용 RestClient.
 *
 * baseUrl을 설정하지 않는다 — 토큰 교환과 사용자 조회가 서로 다른 호스트라, 호출부(`KakaoAuthService`)가
 * 매번 절대 URI를 지정한다.
 *
 * 연결 3초 / 읽기 5초 타임아웃을 건다. 카카오가 응답하지 않을 때 우리 요청 스레드가 무한 대기하면
 * 로그인 전체가 멈추기 때문이다(T-02-08). Boot 4.1의 `HttpClientSettings`/`ClientHttpRequestFactoryBuilder`
 * (spring-boot-http-client 모듈)는 이 프로젝트 클래스패스에 없어(신규 의존성 추가가 필요해 이번
 * phase의 "신규 패키지 1건" 범위를 벗어난다), 이미 `spring-web`에 있는
 * [SimpleClientHttpRequestFactory]의 `Duration` 기반 타임아웃 setter로 구성한다
 * (verify-boot4-api 절차로 실제 classpath jar를 확인함).
 *
 * 이 빈은 테스트에서 `MockRestServiceServer.bindTo(builder)`로 목킹되는 대상이다 — 실제 네트워크
 * 호출 없이 카카오 연동을 검증할 수 있다.
 */
@Configuration
class KakaoRestClientConfig {
    @Bean
    fun kakaoRestClient(builder: RestClient.Builder): RestClient {
        val requestFactory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(3))
                setReadTimeout(Duration.ofSeconds(5))
            }
        return builder.requestFactory(requestFactory).build()
    }
}
