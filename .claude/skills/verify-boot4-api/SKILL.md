---
name: verify-boot4-api
description: "Spring Boot 4 / Kotlin API·의존성 버전을 추측하지 않고 확인하는 절차. 새 의존성 추가, 낯선 애노테이션·설정 키 사용, 임포트 오류, 예제 코드 이식 전에 사용한다."
allowed-tools:
  - Read
  - Bash
  - Grep
  - Glob
  - WebFetch
  - mcp__context7__resolve-library-id
  - mcp__context7__query-docs
---

# Boot 4 API·버전 확인 절차

이 프로젝트는 **Spring Boot 4.1.0 / Spring Framework 7 / Kotlin 2.3.21** 이다.
학습 자료·블로그·모델의 기본 지식은 대부분 **Boot 3 기준**이라 그대로 쓰면 컴파일되지 않거나 조용히 다르게 동작한다.

## 언제 이 절차를 쓰는가

- 새 의존성을 추가할 때
- 처음 쓰는 애노테이션·설정 키·클래스를 넣을 때
- 예제 코드를 이식할 때
- `Unresolved reference` / `Unsatisfied dependency` 오류가 났을 때

## 1. 문서 확인 (context7)

```
mcp__context7__resolve-library-id → "spring boot"
mcp__context7__query-docs        → 버전을 명시해서 질문
```

- 질문에 **버전을 반드시 포함**한다: "Spring Boot 4.1에서 X를 설정하는 키", "Spring Framework 7의 Y API"
- 답이 3.x 기준으로 보이면 그대로 믿지 말고 2단계로 검증한다

## 2. 실제 좌표·버전 확인 (추측 금지)

```bash
# 최신 안정 버전
curl -s https://repo1.maven.org/maven2/<group을 /로>/<artifact>/maven-metadata.xml | grep -o '<release>[^<]*'

# 그 버전 artifact가 실제로 존재하는지
curl -s -o /dev/null -w "%{http_code}\n" https://repo1.maven.org/maven2/<경로>/<artifact>-<버전>.pom

# Boot BOM이 관리하는 버전 (관리 대상이면 build.gradle.kts에 버전을 적지 않는다)
curl -s https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.0/spring-boot-dependencies-4.1.0.pom | grep -i "<라이브러리>.version"
```

## 3. 클래스가 어느 패키지·모듈에 있는지 확인

Boot 4는 자동설정이 모듈별로 쪼개졌다. 임포트 경로가 3.x와 다른 경우가 많다.

```bash
# 로컬 Gradle 캐시에서 클래스 위치 찾기
find ~/.gradle/caches/modules-2/files-2.1 -name "*.jar" | grep -i spring-boot | while read j; do
  unzip -l "$j" 2>/dev/null | grep -q "<클래스명>.class" && echo "$j"
done
```

## 4. 설정 키 존재 확인

```bash
# 해당 모듈 jar의 configuration-metadata 에서 키 확인
unzip -p <모듈>.jar META-INF/spring-configuration-metadata.json | grep '"name" : "spring\.'
```

## 5. 컴파일로 최종 검증

```bash
./gradlew compileKotlin compileTestKotlin
```

**이 단계를 통과하지 않은 코드를 "완료"로 보고하지 않는다.**

## 이미 확인된 Boot 3 → 4 차이

| 항목 | Boot 3 | Boot 4 (이 프로젝트) |
|---|---|---|
| Web 스타터 | `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| 테스트 스타터 | `spring-boot-starter-test` | 모듈별 `-test` (`...-webmvc-test` 등) |
| Flyway | web에 포함 | `spring-boot-starter-flyway` |
| Jackson | `com.fasterxml.jackson` | Jackson 3 — `tools.jackson` |
| Testcontainers | `org.testcontainers:postgresql` | `org.testcontainers:testcontainers-postgresql` |
| MockMvc 자동설정 | `boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| springdoc | 2.x | 3.x |

설정 키(`spring.datasource` / `spring.jpa` / `spring.flyway` / `spring.jackson`)는 3.x와 동일하다.

**새로 발견한 차이는 이 표와 `docs/conventions.md` §11에 추가한다.**
