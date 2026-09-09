---
phase: 07-container-server-setup
plan: 01
subsystem: infra
tags: [spring-boot, hikari, tomcat, springdoc, env-config, decisions-log]

# Dependency graph
requires:
  - phase: 06-operations
    provides: v1.0 완결된 백엔드(운영 배포 대상 코드베이스)
provides:
  - "운영 배포가 참조할 env 키 계약: DB_HIKARI_MAX_POOL_SIZE, SERVER_TOMCAT_THREADS_MAX, SWAGGER_ENABLED, DOMAIN, ACME_EMAIL, JAVA_TOOL_OPTIONS, APP_IMAGE"
  - "SWAGGER_ENABLED=false 시 /v3/api-docs·/swagger-ui.html 404 회귀 테스트"
  - "docs/decisions.md D-169~D-175 (운영 프로필 미신설·풀/스레드 축소·Swagger 비활성 방식·런타임 베이스 이미지·JVM 힙 시작값·Caddy actuator 차단·GHCR public)"
affects: [07-02, 07-03, 07-04, 07-05, 07-06, 08-deploy-pipeline]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "env 플레이스홀더 간접 바인딩: 점(.) 표기 Spring 키(springdoc.*)는 평평한(flat) env 이름으로 감싸 env_file 제약을 우회"
    - "${ENV_KEY:안전한기본값} 패턴을 기존 application.yml 관례 그대로 확장"

key-files:
  created:
    - src/test/kotlin/com/goldwrestling/config/SwaggerDisabledTest.kt
  modified:
    - src/main/resources/application.yml
    - .env.example
    - docs/decisions.md

key-decisions:
  - "D-169: 운영 전용 스프링 프로필을 만들지 않고 운영 값을 전부 env 키로 표현한다"
  - "D-170: Hikari maximum-pool-size 5, Tomcat threads.max 50으로 축소"
  - "D-171: 운영 Swagger 차단은 springdoc.*.enabled=false로 하고 SecurityConfig는 손대지 않는다"
  - "D-172: 런타임 베이스 이미지는 eclipse-temurin:21-jre-noble로 OS 계열까지 명시 고정"
  - "D-173: 앱 JVM 힙은 JAVA_TOOL_OPTIONS의 -XX:MaxRAMPercentage=60으로 시작하고 실측으로 확정"
  - "D-174: /actuator/* 외부 차단은 Caddy가 담당하고 /actuator/health만 통과시킨다"
  - "D-175: GHCR 이미지 패키지는 public으로 두어 서버 pull 인증을 없앤다"

patterns-established:
  - "운영 전용 env 섹션은 .env.example 맨 끝에 '# --- 운영 배포 (Phase N, decisions.md D-xxx~D-yyy) ---' 헤더로 그룹핑하고 실값 없이 키만 남긴다"

requirements-completed: [INFRA-07]

# Metrics
duration: ~45min
completed: 2026-09-08
---

# Phase 7 Plan 01: 운영 배포 애플리케이션 설정 표면 Summary

**Hikari/Tomcat/springdoc 3종 env 플레이스홀더로 운영 배포 설정 표면을 열고(D-08·D-10 실현), `.env.example`에 운영 키 7종을 실값 없이 문서화하고, `SwaggerDisabledTest`로 운영 Swagger 차단(404)을 회귀 고정하고, `docs/decisions.md`에 Phase 7 결정 D-169~D-175를 기록했다.**

## Performance

- **Duration:** 약 45분
- **Completed:** 2026-09-08T13:58:19Z
- **Tasks:** 3/3 완료
- **Files modified:** 4 (신설 1, 수정 3)

## Accomplishments

- `application.yml`에 `DB_HIKARI_MAX_POOL_SIZE`(기본 5)·`SERVER_TOMCAT_THREADS_MAX`(기본 50)·`SWAGGER_ENABLED`(기본 true, api-docs·swagger-ui 양쪽 동일 키) 3종 env 플레이스홀더 추가. 기존 `${ENV:기본값}` 관례·한국어 주석 스타일 그대로 확장했고 `SecurityConfig.kt`·프로필 파일은 무변경.
- `.env.example`에 운영 배포 섹션(`DOMAIN`·`ACME_EMAIL`·`SWAGGER_ENABLED`·`DB_HIKARI_MAX_POOL_SIZE`·`SERVER_TOMCAT_THREADS_MAX`·`JAVA_TOOL_OPTIONS`·`APP_IMAGE`) 신설. 실값 0건(`DOMAIN`·`ACME_EMAIL`·`JAVA_TOOL_OPTIONS`·`APP_IMAGE`는 키만).
- `SwaggerDisabledTest.kt` 신설 — `@SpringBootTest(properties=[...])`로 springdoc을 끈 상태에서 `/v3/api-docs`·`/swagger-ui.html` GET이 404임을 증명. `SecurityFilterChainTest.kt`의 `mockMvc.perform(...).andExpect(...)` 체이닝 스타일을 그대로 따랐고 Kotlin MockMvc DSL은 쓰지 않았다.
- `docs/decisions.md`에 D-169~D-175 7건 append(순수 append, 기존 D-001~D-168 무변경).

## Task Commits

Each task was committed atomically:

1. **Task 1: application.yml에 Hikari·Tomcat·springdoc env 플레이스홀더 추가** - `198a4bf` (feat)
2. **Task 2: .env.example 운영 키 섹션 + SwaggerDisabledTest 신설** - `9e69a63` (feat)
3. **Task 3: docs/decisions.md에 Phase 7 결정 7건 기록** - `4fbabc9` (docs)

_TDD 아님 — 이 플랜은 설정 확장 + 회귀 테스트 성격이라 GREEN 단독 커밋(RED 단계 없이 바로 통과 테스트 작성)._

## Files Created/Modified

- `src/main/resources/application.yml` - Hikari 풀·Tomcat 스레드·springdoc 활성 여부 env 플레이스홀더 3종 추가
- `.env.example` - 운영 배포 키 7종 섹션 신설(실값 없음)
- `src/test/kotlin/com/goldwrestling/config/SwaggerDisabledTest.kt` - springdoc 비활성 시 404 회귀 테스트 2건
- `docs/decisions.md` - D-169~D-175 7건 append

## Decisions Made

- **번호 편차:** 오케스트레이터 사전 확인·실행 시점 재확인 모두 마지막 번호 `D-168`로 일치했다(플랜이 예상한 D-158 대비 FE 세션이 D-159~D-168을 이미 선점한 상태). 계획대로 D-169부터 연속 7건 부여했고 별도 편차 없음.
- D-169~D-175 결정 내용은 위 frontmatter `key-decisions` 참조. 근거는 `docs/decisions.md` 해당 항목 본문.

## Deviations from Plan

None - 계획대로 실행됨. `docs/decisions.md` 번호 시작점(D-168)이 플랜 작성 시점 가정(D-158)과 달랐으나, 이는 플랜 자체가 이미 "실행 시점에 재확인하라"고 명시한 정상 시나리오였고 오케스트레이터 사전 체크와도 일치해 편차로 기록하지 않는다.

## Issues Encountered

None.

## 테스트 미작성 파일과 그 이유 (§10.0 면제)

- `src/main/resources/application.yml` — `application.yml`은 conventions §10.0 명시 면제 대상(설정 파일). 동작 검증은 `SwaggerDisabledTest`(springdoc 플래그)와 `./gradlew compileKotlin`(Hikari/Tomcat 키 파싱)으로 갈음.
- `.env.example` — 문서/예시 파일, §10.0 면제.
- `docs/decisions.md` — 문서, §10.0 면제. `ktlintCheck`가 마크다운은 검사하지 않으므로 별도 포맷 게이트는 없음.

## 이번에 쓴 기술

1. **컨테이너 인지 커넥션 풀·스레드 풀 사이징 (Hikari `maximum-pool-size`, Tomcat `threads.max`)**
   - **왜 필요했는가:** 지금 앱은 로컬/CI에서만 돌아 기본값(풀 10, 스레드 200)이 문제된 적이 없다. 하지만 다음 플랜이 배포할 운영 서버는 RAM 1GB짜리 t3.micro다. 커넥션 풀 하나·스레드 하나마다 메모리(스레드는 스택 메모리, 커넥션은 postgres 쪽 세션)를 점유하므로, 기본값 그대로면 실제 필요량(배치가 한 흐름에서 커넥션 2개 쓰는 정도)보다 훨씬 많은 자원을 미리 예약해 버린다.
   - **안 썼으면 뭐가 깨지는가:** 앱 컨테이너 메모리 한도(550M) 안에서 Tomcat 스레드 200개가 각자 스택을 잡고 있으면, 실제 요청 처리에 쓸 힙 여유가 줄어들어 트래픽이 몰리지 않아도 OOM kill 위험이 커진다.

2. **`★ env 간접 바인딩` — 점(.) 표기 Spring 키를 평평한(flat) 환경변수 이름으로 우회하는 패턴**
   - **왜 필요했는가:** `docker compose`의 `env_file`(`.env`)은 변수 이름에 점(`.`)이 들어가면 파싱 에러를 낸다. 그런데 끄고 싶은 설정은 `springdoc.api-docs.enabled`처럼 Spring 프로퍼티 키라 점이 필수다. 그래서 `application.yml`에서만 점 표기 키를 쓰고, 그 값의 실제 출처는 점 없는 `SWAGGER_ENABLED`라는 별도 이름으로 만들어 `${SWAGGER_ENABLED:true}`로 연결했다.
   - **안 썼으면 뭐가 깨지는가:** `.env`에 `springdoc.api-docs.enabled=false`를 직접 쓰면 컨테이너 기동 자체가 "unexpected character '.' in variable name" 에러로 실패한다 — 운영 서버가 아예 안 뜬다.

3. **`@SpringBootTest(properties = [...])`로 특정 테스트 클래스만 전역 설정을 덮어쓰기**
   - **왜 필요했는가:** "Swagger를 끄면 404가 난다"는 동작은 `application.yml`의 기본값(true)을 바꾸지 않고는 다른 방법으로 검증하기 어렵다. 그렇다고 전역 기본값을 false로 바꾸면 다른 모든 컨트롤러 테스트에 영향을 준다. `@SpringBootTest(properties=[...])`는 이 테스트 클래스에서만 프로퍼티를 덮어써 별도의 `ApplicationContext`를 새로 띄운다.
   - **안 썼으면 뭐가 깨지는가:** 전역 설정으로 껐다면 `SecurityFilterChainTest`의 "`/v3/api-docs`는 200이다" 같은 기존 테스트가 깨졌을 것이다. 반대로 이 트레이드오프를 감수하지 않고 검증을 생략했다면, `SWAGGER_ENABLED=false`가 실제로 404를 내는지 아무도 증명하지 못한 채 운영에 배포하게 된다.
   - **일부러 안 쓴 것:** RESEARCH.md가 제시한 Kotlin MockMvc DSL(`mockMvc.get("...") { }`)은 쓰지 않았다 — 이 레포에 사용 사례가 0건이라 기존 `SecurityFilterChainTest`와 스타일이 갈라지는 것을 피했다(`MockMvcRequestBuilders`/`MockMvcResultMatchers` 정적 임포트 + `perform().andExpect()` 체이닝 유지).

4. **결정 로그(`docs/decisions.md`)의 append-only 3단 포맷 — 번호를 실행 시점에 재확인**
   - **왜 필요했는가:** 이 파일은 BE·FE가 D-번호를 공유하는 단일 로그다. 플랜을 짤 때 본 마지막 번호(D-158)와 실제 실행 시점 번호(D-168)가 10건이나 벌어져 있었다 — 그 사이 FE 세션이 먼저 번호를 썼기 때문이다. 그래서 번호를 하드코딩하지 않고 `grep -oE '^## D-[0-9]+' docs/decisions.md | tail -1`로 실제 마지막 번호를 매번 다시 읽었다.
   - **안 썼으면 뭐가 깨지는가:** 플랜이 가정한 D-159부터 그대로 썼다면 FE가 이미 쓴 D-159~D-168과 번호가 정확히 충돌해, `docs/decisions.md`에 같은 번호를 가진 서로 다른 결정 두 개가 생긴다 — 이후 어느 플랜이나 코드 주석이 그 번호를 인용해도 어느 쪽을 가리키는지 알 수 없게 된다.

## User Setup Required

None - 이 플랜은 외부 서비스 설정이 필요 없다. `.env.example`에 새로 추가된 키(`DOMAIN`·`ACME_EMAIL`·`JAVA_TOOL_OPTIONS`·`APP_IMAGE`)는 값을 채우지 않고 다음 플랜(compose·Caddyfile·수동 배포)에서 실제로 쓰인다.

## Next Phase Readiness

- 이 플랜이 확정한 env 키 계약(`DB_HIKARI_MAX_POOL_SIZE`·`SERVER_TOMCAT_THREADS_MAX`·`SWAGGER_ENABLED`·`DOMAIN`·`ACME_EMAIL`·`JAVA_TOOL_OPTIONS`·`APP_IMAGE`)을 07-02(compose)·07-03 이후 Caddyfile·수동 배포 플랜이 그대로 참조하면 된다.
- D-169~D-175는 확정 기록됐으므로, 뒤 플랜에서 같은 결정을 다시 논의할 필요는 없다.
- 블로커 없음.

---
*Phase: 07-container-server-setup*
*Completed: 2026-09-08*

## Self-Check: PASSED

- 생성/수정 파일 5건 전부 존재 확인 (`application.yml`, `.env.example`, `SwaggerDisabledTest.kt`, `docs/decisions.md`, 이 SUMMARY 파일)
- 커밋 4건(`198a4bf`, `9e69a63`, `4fbabc9`, `e078216`) 전부 `git log`에서 확인
