# gold-wrestling-be

골드레슬링 체육관 회원 관리·예약 시스템 **백엔드** — Kotlin + Spring Boot 4.1.x + JPA + PostgreSQL

## 기술 스택

| 항목 | 버전 | 비고 |
|---|---|---|
| JDK | 21 (LTS) | Gradle toolchain으로 고정 (decisions.md D-005) |
| Kotlin | 2.3.21 | Boot 4.1.0 BOM 정렬 버전 |
| Spring Boot | 4.1.0 | Spring Framework 7 / Hibernate 7.4 (D-014) |
| Gradle | 9.6.1 | wrapper 커밋됨 — `./gradlew` 사용 |
| PostgreSQL | 18.4 | docker-compose / Testcontainers 동일 버전 |
| Flyway | Boot BOM 관리 | 스키마 변경의 유일한 주체 (`ddl-auto=validate`) |
| springdoc-openapi | 3.0.3 | Boot 4 대응 라인 (2.x는 Boot 3 전용) |
| ktlint | 1.8.0 (플러그인 14.2.0) | 포맷 기준. 규칙은 `.editorconfig` (D-024) |

## 로컬 실행

1. 환경변수 설정: `.env.example`을 복사해 `.env`를 작성한다.

   ```bash
   cp .env.example .env
   ```

   `DB_PASSWORD`, `JWT_SECRET`, 카카오 키 등 실제 값은 `.env`에만 넣는다.
   `.env`는 `.gitignore`로 제외되며 절대 커밋하지 않는다.
   이 파일은 docker-compose(Postgres 컨테이너 생성)와 애플리케이션(`spring.config.import`)이 함께 읽는다.

2. PostgreSQL 기동

   ```bash
   docker compose up -d
   ```

3. 애플리케이션 실행

   ```bash
   ./gradlew bootRun
   ```

   | 확인 | URL |
   |---|---|
   | 배선 확인 | http://localhost:8080/api/system/health |
   | 운영 상태 | http://localhost:8080/actuator/health |
   | Swagger UI | http://localhost:8080/swagger-ui.html |

   DB 없이 앱만 띄우려면 `src/test/.../TestGoldWrestlingApplication.kt`의 `main`을 실행한다
   (Testcontainers가 일회용 PostgreSQL을 올려 붙여 준다).

## 테스트 · 코드 포맷

```bash
./gradlew ktlintFormat   # 코드 포맷 자동 수정 (커밋 전에 실행)
./gradlew test           # Docker Desktop 실행 필요 (Testcontainers)
./gradlew build          # 포맷 검사 + 컴파일 + 테스트 + jar
```

포맷 규칙은 `.editorconfig` 가 단일 출처다 (ktlint `ktlint_official` 스타일, D-024).
`build` 는 포맷 위반이 있으면 실패하므로, 실패하면 `ktlintFormat` 을 돌리고 다시 빌드한다.
IntelliJ / VS Code 도 같은 `.editorconfig` 를 읽으므로 에디터 포맷과 ktlint 결과가 어긋나지 않는다.

## API 문서 생성 (FE 계약)

`docs/api/openapi.yaml`이 FE·BE 간 유일한 API 계약이다. **API를 변경하면 반드시 재생성해 커밋한다.**

```bash
./gradlew bootRun                                          # 앱 기동 후
curl -s http://localhost:8080/v3/api-docs.yaml -o docs/api/openapi.yaml
```

## 문서

- 기획·정책: [`docs/requirements.md`](docs/requirements.md), [`docs/policies.md`](docs/policies.md)
- 용어: [`docs/glossary.md`](docs/glossary.md) · 결정 기록: [`docs/decisions.md`](docs/decisions.md)
- API 계약: [`docs/api/openapi.yaml`](docs/api/openapi.yaml)
