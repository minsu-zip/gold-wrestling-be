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
