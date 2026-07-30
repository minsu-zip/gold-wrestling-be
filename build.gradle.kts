plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    // 코틀린 포맷터 (Prettier 역할). 스타일 규칙은 .editorconfig 가 단일 출처
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "com.goldwrestling"
version = "0.0.1-SNAPSHOT"
description = "골드레슬링 회원 관리·예약 시스템 백엔드"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Boot 4의 BOM이 관리하지 않는 의존성만 여기서 버전을 고정한다.
val springdocVersion = "3.0.3" // Spring Boot 4 대응 라인 (2.x는 Boot 3 전용)

dependencies {
    // --- Spring Boot 4: web 스타터 이름이 spring-boot-starter-web → -webmvc 로 변경됨 ---
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Boot 4는 Flyway도 전용 스타터로 분리됨
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Boot 4는 Jackson 3 사용 → groupId가 com.fasterxml.jackson 이 아니라 tools.jackson
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // API 문서 (docs/api/openapi.yaml 생성 주체)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    runtimeOnly("org.postgresql:postgresql")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // --- 테스트: Boot 4는 spring-boot-starter-test 대신 모듈별 -test 스타터로 분리됨 ---
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    // Testcontainers 2.x: 모듈 artifactId가 postgresql → testcontainers-postgresql 로 변경됨
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
        )
    }
}

ktlint {
    // 플러그인이 기본으로 물고 오는 버전 대신 명시적으로 고정한다 (포맷 결과가 버전마다 달라질 수 있다)
    version.set("1.8.0")
    // 스타일 규칙은 .editorconfig 에만 둔다 (에디터와 같은 규칙을 공유하기 위해)
    filter {
        exclude { it.file.path.contains("${layout.buildDirectory.get()}") }
    }
}

// JPA 엔티티는 final 이면 프록시를 만들 수 없다 → 해당 애노테이션이 붙은 클래스만 open 으로 컴파일
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // 도메인 규칙상 서버 기준 시간대는 Asia/Seoul 고정
    systemProperty("user.timezone", "Asia/Seoul")
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    systemProperty("user.timezone", "Asia/Seoul")
}

// ---------------------------------------------------------------------------
// openapi.yaml 재생성 파이프라인 (FOUND-03, docs/decisions.md D-029)
//
// springdoc은 런타임 introspection으로 스펙을 만든다 — 앱을 띄우지 않으면 스펙이 나오지 않는다.
// springdoc-openapi-gradle-plugin은 쓰지 않는다(D-029 — 최근 릴리스 없음 + Boot 4 Gradle 플러그인과
// BootRun_Decorated 캐스트 충돌·configuration cache 비호환 이슈 미해결). 대신 앱을 백그라운드로
// 기동 → /actuator/health 폴링으로 대기 → /v3/api-docs.yaml 다운로드 → 프로세스 정리를 커스텀
// Exec 태스크 체인으로 구현한다. 로컬 docker compose Postgres 기동이 전제다 (상세는 D-029).
// ---------------------------------------------------------------------------
val apiDocsPort = 8099 // bootRun(8080)과 충돌하지 않도록 별도 포트
val apiDocsDir = layout.buildDirectory.dir("apiDocs")
val apiDocsPidFile = layout.buildDirectory.file("apiDocs/app.pid")
val apiDocsLogFile = layout.buildDirectory.file("apiDocs/app.log")
val apiDocsJarFile =
    tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar").flatMap { it.archiveFile }

tasks.register<Exec>("startApiDocsApp") {
    group = "documentation"
    description = "openapi 재생성용 앱을 백그라운드로 기동한다 (포트 $apiDocsPort)"
    dependsOn("bootJar")
    doFirst {
        apiDocsDir.get().asFile.mkdirs()
    }
    commandLine(
        "bash", "-c",
        """
        # 이전 실행이 finalizer(stopApiDocsApp)를 못 돌리고 죽었으면 옛 jar 의 앱이 $apiDocsPort 를 점유한 채
        # 살아남는다. 그대로 기동하면 health 폴링이 잔존 앱에 성공해 "옛 스펙"을 조용히 내려받게 되므로,
        # (1) PID 파일에 남은 프로세스를 먼저 정리하고 (2) 그래도 응답하는 미지의 프로세스가 있으면 즉시 실패시킨다.
        if [ -f "${apiDocsPidFile.get().asFile}" ]; then
          OLD_PID=${'$'}(cat "${apiDocsPidFile.get().asFile}")
          kill "${'$'}OLD_PID" 2>/dev/null || true
          for i in {1..10}; do
            kill -0 "${'$'}OLD_PID" 2>/dev/null || break
            sleep 1
          done
          rm -f "${apiDocsPidFile.get().asFile}"
        fi
        if curl -sf "http://localhost:$apiDocsPort/actuator/health" > /dev/null; then
          echo "포트 $apiDocsPort 에서 이미 다른 프로세스가 응답 중입니다 — 그 프로세스를 종료한 뒤 다시 실행하세요." >&2
          echo "(이대로 진행하면 그 프로세스의 낡은 스펙이 docs/api/openapi.yaml 로 내려받힙니다)" >&2
          exit 1
        fi
        nohup java -Duser.timezone=Asia/Seoul -jar "${apiDocsJarFile.get().asFile}" \
          --server.port=$apiDocsPort > "${apiDocsLogFile.get().asFile}" 2>&1 &
        echo ${'$'}! > "${apiDocsPidFile.get().asFile}"
        """.trimIndent(),
    )
}

tasks.register<Exec>("waitApiDocsApp") {
    group = "documentation"
    description = "openapi 재생성용 앱의 /actuator/health 를 폴링해 기동 완료를 기다린다"
    dependsOn("startApiDocsApp")
    finalizedBy("stopApiDocsApp")
    commandLine(
        "bash", "-c",
        """
        # health 응답만 믿지 않는다 — startApiDocsApp 이 기록한 PID 가 살아 있는지 함께 확인해,
        # "우리가 띄운 프로세스는 죽고 다른 프로세스가 응답 중"인 상황을 걸러낸다.
        for i in {1..60}; do
          if ! kill -0 "${'$'}(cat "${apiDocsPidFile.get().asFile}")" 2>/dev/null; then
            echo "openapi 재생성용 앱 프로세스가 기동 중 종료됨(포트 충돌 등) — app.log 마지막 20줄:" >&2
            tail -n 20 "${apiDocsLogFile.get().asFile}" >&2
            exit 1
          fi
          curl -sf "http://localhost:$apiDocsPort/actuator/health" > /dev/null && exit 0
          sleep 1
        done
        echo "openapi 재생성용 앱이 60초 안에 기동하지 않음 — app.log 마지막 20줄:" >&2
        tail -n 20 "${apiDocsLogFile.get().asFile}" >&2
        exit 1
        """.trimIndent(),
    )
}

tasks.register<Exec>("downloadApiDocs") {
    group = "documentation"
    description = "실행 중인 앱에서 /v3/api-docs.yaml 을 내려받아 docs/api/openapi.yaml 을 갱신한다"
    dependsOn("waitApiDocsApp")
    finalizedBy("stopApiDocsApp")
    // docs/api/openapi.yaml 을 outputs 로 선언하지 않는다 — UP-TO-DATE 로 건너뛰면
    // "항상 최신 스펙"이라는 계약이 깨진다. 매번 실행되는 것이 의도된 동작이다.
    commandLine(
        "bash", "-c",
        "curl -sf \"http://localhost:$apiDocsPort/v3/api-docs.yaml\" -o docs/api/openapi.yaml",
    )
}

tasks.register<Exec>("stopApiDocsApp") {
    group = "documentation"
    description = "openapi 재생성용 백그라운드 앱을 종료하고 PID 파일을 정리한다 (멱등 — 여러 번 걸려도 안전)"
    isIgnoreExitValue = true
    commandLine(
        "bash", "-c",
        """
        if [ -f "${apiDocsPidFile.get().asFile}" ]; then
          kill ${'$'}(cat "${apiDocsPidFile.get().asFile}") 2>/dev/null || true
          rm -f "${apiDocsPidFile.get().asFile}"
        fi
        true
        """.trimIndent(),
    )
}

tasks.register("generateApiDocs") {
    group = "documentation"
    description =
        "docs/api/openapi.yaml 을 재생성한다. 로컬 docker compose Postgres 기동 필요. " +
            "앱 기동을 포함해 최대 1분가량 걸린다"
    dependsOn("downloadApiDocs")
    // 파이널라이저는 완료된(성공이든 실패든) 태스크 뒤에 실행되므로, 의존 체인의 세 지점
    // (waitApiDocsApp·downloadApiDocs·generateApiDocs)에 모두 걸어 어느 단계에서 실패해도
    // 프로세스가 정리되게 한다. stopApiDocsApp은 멱등해 여러 번 실행돼도 부작용이 없다.
    finalizedBy("stopApiDocsApp")
}
