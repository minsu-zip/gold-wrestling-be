# syntax=docker/dockerfile:1
# 표기: D-1xx = docs/decisions.md 전역 결정 / 07-CONTEXT D-xx = .planning/phases/07-container-server-setup/07-CONTEXT.md 로컬 결정(실행 맥락, 스펙 아님)
# 위 지시문이 있어야 --mount=type=cache 캐시 마운트 구문이 안전하게 파싱된다 (가정 A4, 07-RESEARCH.md).

# --- 빌더 스테이지 ---
# $BUILDPLATFORM(빌드를 실행하는 호스트의 아키텍처, 예: linux/arm64 on Apple Silicon Mac)으로 고정한다.
# JVM 바이트코드는 아키텍처 독립적이므로 --platform=$TARGETPLATFORM(amd64/arm64 각각)으로 빌드할 필요가 없다.
# 빌더를 호스트 아키텍처 하나로 고정하면 QEMU 에뮬레이션 없이 네이티브로 1회만 컴파일하고,
# 그 결과 jar를 두 런타임 스테이지가 그대로 나눠 COPY해 쓴다 (INFRA-02, 07-CONTEXT D-16).
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-noble AS builder
WORKDIR /builder
COPY . .
# 테스트는 CI(ci.yml)가 담당하고 이미지 빌드에서는 돌리지 않는다 (INFRA-01 명시).
# bootJar의 태스크 그래프에는 원래 test가 없어(`./gradlew bootJar --dry-run`으로 확인: compileKotlin → bootJar 뿐)
# -x test는 지금 아무것도 건너뛰지 않는 no-op이다. 그래도 남겨 두는 이유는 "이미지 빌드에서 테스트를 돌리지 않는다"는
# 의도를 명시하고, 이후 build.gradle.kts가 bootJar를 check/test에 의존시키도록 바뀌어도 이 가드가 그대로 막게 하려는 것이다.
# --mount=type=cache로 Gradle 의존성 캐시를 빌드 간에 재사용해 반복 빌드 속도를 높인다.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar -x test --no-daemon

# Boot 4.1은 layertools jarmode가 tools jarmode로 통합됐다 (07-CONTEXT D-13, verify-boot4-api로 확인된 공식 명령).
# bootJar가 layers.idx를 기본 포함하므로 build.gradle.kts에 layered {} 블록 없이도 추출이 된다 (가정 A5).
#
# 추출 전에 jar를 application.jar로 이름을 고정한다 (공식 Boot 4.1 파셜 Dockerfile과 동일한 형태, D-176).
# extract는 application 레이어 안의 jar 파일명을 "입력 jar 이름 그대로" 유지하므로, 원본 이름
# (gold-wrestling-be-0.0.1-SNAPSHOT.jar)으로 추출하면 아래 ENTRYPOINT의 application.jar가 존재하지 않는다.
# bootJar만 실행했으므로 build/libs/에는 jar가 하나뿐이다 — 둘 이상이면 cp가 실패해 빌드가 멈춘다(조용히 하나를 고르지 않는다).
RUN cp build/libs/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# --- 런타임 스테이지 ---
# eclipse-temurin:21-jre(접미사 없음)는 현재 noble이 아니라 resolute로 롤링돼 있다 (Pitfall 1).
# D-172가 noble 계열을 못박았으므로 반드시 -noble 접미사를 명시한다.
FROM eclipse-temurin:21-jre-noble
WORKDIR /application

# 베이스 이미지는 비루트 사용자를 기본 제공하지 않는다 (Pitfall 2) — 직접 만들어 USER로 전환한다.
# --no-create-home: 애플리케이션 실행 전용 시스템 계정이라 홈 디렉토리가 필요 없다.
RUN groupadd --system app && useradd --system --gid app --no-create-home app

# 레이어 COPY 순서 고정: dependencies(가장 안 바뀜) → spring-boot-loader → snapshot-dependencies → application(가장 자주 바뀜).
# 코드만 바뀐 재배포는 application 레이어(수 MB)만 새로 전송하면 되고, 나머지 레이어는 캐시를 그대로 쓴다 (07-CONTEXT D-14).
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

USER app
EXPOSE 8080
# --launcher가 extract 기본 옵션에 포함돼 JarLauncher가 이미 들어있다 — java -jar로 바로 실행 가능.
ENTRYPOINT ["java", "-jar", "application.jar"]

# CDS/AppCDS 관련 클래스 아카이브 생성·훈련 실행 단계는 이 이미지에 넣지 않는다 — v1.2 후보로 미뤘다 (07-CONTEXT D-15).
# HEALTHCHECK 지시어도 넣지 않는다 — 운영 헬스체크는 compose(07-03)가 소유해 간격 조정 시 재빌드가 필요 없게 한다.
