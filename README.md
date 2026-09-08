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

## 미사용 차감 배치 운영 (cron 켜기 절차)

2주 미사용 시 자동 차감(`docs/policies.md` §4.3)의 cron은 배포 시 **기본적으로 꺼져 있다**
(D-121 fail-safe — 설정을 빠뜨리면 "돌지 않는" 쪽으로 안전하게 실패한다). 아래 절차를 **순서대로**
따라야 안전하게 켤 수 있다.

1. **전제 확인** — 출석 배선(Phase 6, `InactivityBatchRunner`가 `AttendanceRepository`로 마지막
   출석일을 실제 조회하는 배선)이 배포됐는지 확인한다. 이 배선 없이 cron을 켜면 저녁반에만 나오는
   `SESSION_PASS` 회원이 2주마다 1.0회씩 부당 차감된다(CR-03의 실제 피해).
2. **수동 실행 1회 검증** — `POST /api/admin/batch/inactivity-runs`(202 접수) → 응답 `Location`
   헤더의 실행 상태 조회 URL을 폴링해 `SUCCESS`와 처리·차감 건수를 확인한다.
   **정책 시행일(`BATCH_INACTIVITY_POLICY_EFFECTIVE_DATE`, 기본 `2026-09-01`) 이전에는 차감 0건이
   정상**이다 — 0건을 "동작 안 함"으로 오독하지 않는다(D-119).
3. **cron 활성화** — 배포 환경 변수에 `BATCH_INACTIVITY_SCHEDULER_ENABLED=true`를 **명시적으로**
   설정한다. 기본값은 꺼짐이며 코드로 되돌리지 않는다(D-121 fail-safe — 설정을 빠뜨리면 꺼진 채로
   남는다). 관리자 수동 실행 API는 이 값과 무관하게 항상 동작한다.
4. **되돌리기** — 이상 징후(예상보다 많은 `INACTIVITY` 이력)가 보이면 같은 변수를 `false`로 바꿔
   재배포하면 즉시 멈춘다. 이미 발생한 차감은 관리자 수동 가감(`ADMIN_ADJUST`)으로 정정한다 —
   배치는 환불하지 않는다(D-127).

| 환경변수 | 기본값 | 의미 |
|---|---|---|
| `BATCH_INACTIVITY_SCHEDULER_ENABLED` | `false` | cron 자동 실행 킬 스위치. `true`를 명시해야만 스케줄러 빈이 등록된다(D-121). 관리자 수동 실행 API는 이 값과 무관하게 항상 동작한다 |
| `BATCH_INACTIVITY_POLICY_EFFECTIVE_DATE` | `2026-09-01` | 정책 시행일 하한. 이 날짜 이전의 미사용은 차감 부채로 치지 않는다(D-119) |
| `BATCH_INACTIVITY_MAX_DEDUCTIONS_PER_RUN` | `1` | 배치 1회 실행에서 회원 1명당 최대 차감 횟수. 밀린 주기는 다음 실행이 이어받는다(D-119) |
| `BATCH_INACTIVITY_STALE_RUN_TIMEOUT` | `30m` | 이 시간을 넘긴 `RUNNING` 실행 이력은 죽은 것으로 보고 정리한다(D-117) |

## 테스트 · 코드 포맷

```bash
./gradlew ktlintFormat   # 코드 포맷 자동 수정 (커밋 전에 실행)
./gradlew test           # Docker Desktop 실행 필요 (Testcontainers)
./gradlew build          # 포맷 검사 + 컴파일 + 테스트 + jar
```

포맷 규칙은 `.editorconfig` 가 단일 출처다 (ktlint `ktlint_official` 스타일, D-024).
`build` 는 포맷 위반이 있으면 실패하므로, 실패하면 `ktlintFormat` 을 돌리고 다시 빌드한다.
IntelliJ / VS Code 도 같은 `.editorconfig` 를 읽으므로 에디터 포맷과 ktlint 결과가 어긋나지 않는다.

## CI · 브랜치 보호 (운영)

PR(dev·main 대상)과 dev/main push마다 [.github/workflows/ci.yml](.github/workflows/ci.yml)이
`./gradlew ktlintCheck build`(포맷 검사 + 컴파일 + Testcontainers 통합 테스트 포함 전체 테스트)를 돌린다.
러너의 Docker를 그대로 쓰므로 별도 DB 서비스·`.env` 설정이 없고, 트리거 설계(PR+push,
paths-ignore 없음)는 FE 레포 ci.yml과 정합한다 — required check 대상 워크플로가 경로 필터로
스킵되면 체크가 보고되지 않아 문서 전용 PR이 "Expected — waiting for status"에서 머지 불가로 멈춘다.

### main 브랜치 보호 required status check 등록 절차

레포 public 전환으로 브랜치 보호가 다시 강제된다. 아래는 레포 설정이라 코드가 아니며,
**사용자가 GitHub 설정에서 직접 수행한다.**

1. 이 워크플로(`ci.yml`)를 dev에 머지한다.
2. PR을 하나 열어 워크플로가 **최소 1회 실행**되게 한다.
   required status check는 그 이름의 체크가 한 번이라도 보고된 뒤에야
   브랜치 보호 설정 UI의 선택지로 뜬다. 이 순서를 건너뛰면 "선택지에 없다"에서 막힌다.
3. 그 PR의 Checks 탭에 표시되는 문자열을 그대로 복사한다.
   예상 형태는 `CI / build`이지만, **실제 표시 문자열이 정본이다.**
4. GitHub → Settings → Branches → main 보호 규칙에서
   *Require status checks to pass before merging*을 켜고 복사한 이름을 등록한다.
   dev 브랜치 보호는 재량이며, 하지 않으면 백로그로 남긴다.

**경고:** `ci.yml`의 job 이름(`build`)을 바꾸거나 매트릭스를 도입해 체크 이름이 갈라지면,
등록해 둔 이름은 영원히 "Expected — waiting for status"에서 멈추고 머지가 막힌다.
이름을 바꿔야 한다면 브랜치 보호 규칙의 등록 항목을 같은 시점에 함께 고친다.

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
