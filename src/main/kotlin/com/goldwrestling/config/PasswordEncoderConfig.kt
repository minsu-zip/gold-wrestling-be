package com.goldwrestling.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * 관리자 비밀번호 해싱용 [PasswordEncoder] 빈 (D-045).
 *
 * [PasswordEncoderFactories.createDelegatingPasswordEncoder]가 만드는 인코더는 저장 값 앞에
 * `{bcrypt}` 처럼 알고리즘을 기술하는 접두를 붙인다. 그래서 나중에 인코딩 알고리즘을 바꿔도
 * 이미 저장된 옛 해시를 그대로 검증(matches)할 수 있다 — 접두로 어느 알고리즘으로 만들어졌는지
 * 알 수 있기 때문이다. `{bcrypt}` 접두(8자) + BCrypt 해시(60자)를 담기 위해
 * `admin.password_hash`는 이미 `VARCHAR(100)`으로 잡혀 있다([com.goldwrestling.admin.Admin]).
 */
@Configuration
class PasswordEncoderConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
}
