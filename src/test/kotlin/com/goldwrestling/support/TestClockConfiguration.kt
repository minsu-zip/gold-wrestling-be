package com.goldwrestling.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock

/**
 * 프로덕션 `Clock` 빈(`ClockConfig`)을 [MutableTestClock]으로 대체하는 테스트 설정.
 *
 * **빈 이름을 `clock`으로 맞추지 않는다(계획 초안과 다른 점).** 실행해 확인한 결과, `@SpringBootTest`가
 * 컴포넌트 스캔으로 찾는 프로덕션 `ClockConfig.clock()`과 이 클래스를 `@Import`로 등록한 같은 이름의
 * `@Bean`이 있으면 Spring Boot(`allow-bean-definition-overriding` 기본값 `false`)가 컨텍스트 로딩 시점에
 * `BeanDefinitionOverrideException`을 던진다(교체가 아니라 예외였음 — `ClockOverrideScratchTest`로
 * 직접 재현·확인). 대신 **다른 이름의 `@Bean` + `@Primary`** 조합을 쓴다 — 이름이 달라 등록 충돌이
 * 없고, 타입(`Clock`)으로 주입받는 모든 지점(생성자 주입, `@Autowired`)이 `@Primary`가 붙은 이 빈을
 * 우선 선택한다.
 *
 * **중요**: 이 구성을 `@Import`하는 통합테스트는 애노테이션 조합이 Phase 1 테스트(`TestcontainersConfiguration`만
 * import)와 달라져 스프링 컨텍스트가 하나 더 뜬다. Phase 2 이후 시각 의존 통합테스트는 전부 동일하게
 * `@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)` 조합을 써서 컨텍스트가
 * 둘 이상으로 늘어나지 않게 한다(conventions §10.1).
 */
@TestConfiguration(proxyBeanMethods = false)
class TestClockConfiguration {
    @Bean
    @Primary
    fun testClock(): Clock = MutableTestClock.ofSeoul("2026-08-02T10:00:00+09:00")
}
