---
phase: 01-foundation
status: secured
verified: 2026-07-30
threats_total: 16
threats_closed: 16
threats_open: 0
asvs_level: default
audited_commit: 3796bb7
---

# SECURITY.md — Phase 1 (Foundation) 위협 검증 결과

이 문서는 `gsd-security-auditor`가 `.planning/phases/01-foundation/01-01-PLAN.md`,
`01-02-PLAN.md`, `01-03-PLAN.md`의 `<threat_model>` 블록에 선언된 위협 완화책이
실제 구현 코드에 존재하는지 검증한 결과다. 문서·의도가 아니라 코드/설정/테스트의
실측 증거(grep, 파일 내용)로만 CLOSED/OPEN을 판정했다.

- **검증 일자:** 2026-07-30
- **검증 대상 커밋:** `3796bb7` (phase 1 완료 지점)
- **ASVS 레벨:** default (미지정)
- **차단 정책(block_on):** default

## 요약

| 상태 | 개수 |
|---|---|
| CLOSED | 16 (T-01-01 ~ T-01-15 + T-01-SC) |
| OPEN | 0 |
| 미등록 신규 공격면(unregistered_flag) | 0 |

`T-01-SC`(공급망 — 신규 의존성 없음)는 3개 플랜 모두에 동일 disposition으로 등록되어 있어
한 항목으로 통합 검증했다(01-01/01-02/01-03 각각의 `build.gradle.kts` diff를 모두 확인).

## 위협별 검증 상세

### T-01-01 — Information Disclosure — GlobalExceptionHandler 포괄 Exception 핸들러 (mitigate)
- **기대:** 500 응답 `detail`은 고정 문구만, `ex.message`/스택트레이스/클래스명 미포함, 원인은 `logger.error`로만 기록. 테스트가 `internal-leak-marker`/`IllegalStateException`/`at com.goldwrestling` 부재를 단언.
- **증거:**
  - `src/main/kotlin/com/goldwrestling/common/error/GlobalExceptionHandler.kt:52-61` — `handleUnexpectedException`이 `logger.error("예상하지 못한 예외가 발생했습니다.", ex)`로만 원인을 로그에 남기고, 응답 `detail`은 고정 문자열 `"서버 오류가 발생했습니다."`만 사용. `ex.message`를 응답에 쓰지 않음.
  - `src/test/kotlin/com/goldwrestling/common/error/GlobalExceptionHandlerTest.kt:106-120` — 500 케이스 테스트가 `assertThat(body).doesNotContain("internal-leak-marker")`, `.doesNotContain("IllegalStateException")`, `.doesNotContain("at com.goldwrestling")` 3건을 단언.
- **판정:** CLOSED

### T-01-02 — Information Disclosure — 내장 예외의 ProblemDetail.detail (400/404/405/415) (mitigate)
- **기대:** 스프링 기본 `detail` 유지, `ex.message`로 덮어쓰지 않음. `server.error.include-*` 기본값(`never`) 유지.
- **증거:**
  - `GlobalExceptionHandler.kt` 전체에서 `ex.message`가 쓰이는 곳은 `handleDomainException`(L43) 한 곳뿐이며, 이는 도메인 예외(사용자 대면 문구가 명시적으로 설계됨, `DomainException.kt:9`)에 한정된다. 내장 예외 경로(`handleExceptionInternal`, L67-79)는 body가 이미 `ProblemDetail`이면 그대로 쓰고 `code`만 추가할 뿐 `detail`을 건드리지 않음.
  - `src/main/resources/application.yml:52-54` — `include-message: never`, `include-stacktrace: never`, `include-binding-errors: never` 그대로 유지(기존 값 미변경, 01-01-SUMMARY.md acceptance criteria에서도 diff 없음 확인).
- **판정:** CLOSED

### T-01-03 — Information Disclosure — 테스트 전용 컨트롤러 (mitigate)
- **기대:** `/internal-test/*`는 `src/test`에만 존재, `@TestConfiguration` + 명시적 `@Import`로만 등록. `src/main`과 `openapi.yaml`에 문자열 부재.
- **증거:**
  - `src/test/kotlin/com/goldwrestling/common/error/GlobalExceptionHandlerTest.kt:140-159` — `TestErrorController`는 `GlobalExceptionHandlerTest`의 중첩 클래스, `Config`(`@TestConfiguration`)의 `@Bean` 메서드로만 등록, `@Import(TestcontainersConfiguration::class, GlobalExceptionHandlerTest.Config::class)`(L46)로 명시 주입.
  - `grep -rn "internal-test" src/main/kotlin` → **매치 없음**.
  - `grep -n "internal-test" docs/api/openapi.yaml` → **매치 없음**.
- **판정:** CLOSED

### T-01-04 — Tampering — 에러코드 계약 드리프트 (mitigate)
- **기대:** `ErrorCode` enum이 HTTP 상태 보유 + `docs/error-codes.md` 동기화 규칙 문서화(D-028).
- **증거:**
  - `src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt:16-36` — `enum class ErrorCode(val defaultStatus: HttpStatus)`로 코드↔상태 대응이 코드에 고정.
  - `docs/error-codes.md:4` — "새 에러코드를 추가하면 같은 PR에서 이 표에 행을 추가한다"는 동기화 규칙 명시, 6개 코드가 `ErrorCode.kt`와 1:1로 일치.
  - `docs/decisions.md:249-258` — D-028에 이 규칙이 결정으로 기록됨.
- **판정:** CLOSED

### T-01-05 — Spoofing — 에러 응답 경로 열거 (accept)
- **수용 근거:** Phase 1은 인증 없음, 노출 리소스는 헬스 엔드포인트뿐. Phase 2에서 404/403 정책 재검토 예정.
- **재검증(수용 근거 유효성):**
  - `src/main/kotlin/com/goldwrestling/config/SecurityConfig.kt:32` — `authorizeHttpRequests { it.anyRequest().permitAll() }` — Phase 1 스코프에서 인가 로직이 도입되지 않았음을 확인, 수용 근거가 여전히 유효함.
- **수용 로그 등재:** 본 SECURITY.md `## 수용된 위험(Accepted Risks)` 절 참조.
- **판정:** CLOSED (accepted)

### T-01-06 — Information Disclosure — V2 시드 데이터 (mitigate)
- **기대:** 시드는 `branch` 1건(`'송파점'`)만, `admin`/`member` INSERT 없음.
- **증거:**
  - `src/main/resources/db/migration/V2__create_branch_member_admin.sql:14` — `INSERT INTO branch (name) VALUES ('송파점');` 1건만 존재.
  - `grep -n "INSERT INTO" V2__create_branch_member_admin.sql` → `branch` INSERT 1건만 출력, `member`/`admin` INSERT 없음.
  - `FlywayMigrationIntegrationTest.kt:42-50` — "송파점 지점이 시드로 한 건 존재한다" 테스트로 회귀 방지.
- **판정:** CLOSED

### T-01-07 — Tampering — admin 테이블 자격증명 컬럼 조기 추가 (mitigate)
- **기대:** `password`/`login_id`/`kakao`/`role` 문자열이 V2에 없음 + '자격 컬럼 부재' 통합테스트.
- **증거:**
  - `grep -v '^--' V2__create_branch_member_admin.sql | grep -ciE 'password|login_id|kakao|role '` → **0**.
  - `FlywayMigrationIntegrationTest.kt:77-86` — "admin 테이블에는 아직 로그인 자격 컬럼이 없다" 테스트가 `information_schema.columns`에서 `password`/`login_id`/`password_hash` 부재를 단언.
- **판정:** CLOSED

### T-01-08 — Repudiation — Admin↔Branch 중복 매핑 (mitigate)
- **기대:** `uq_admin_branch UNIQUE(admin_id, branch_id)` 제약.
- **증거:** `V2__create_branch_member_admin.sql:45` — `CONSTRAINT uq_admin_branch UNIQUE (admin_id, branch_id)`.
- **판정:** CLOSED

### T-01-09 — Tampering — 참조 무결성 없는 회원 (mitigate)
- **기대:** `member.branch_id NOT NULL` + `fk_member_branch`, 통합테스트가 단언.
- **증거:**
  - `V2__create_branch_member_admin.sql:18,24` — `branch_id BIGINT NOT NULL`, `CONSTRAINT fk_member_branch FOREIGN KEY (branch_id) REFERENCES branch (id)`.
  - `FlywayMigrationIntegrationTest.kt:52-75` — `is_nullable = 'NO'` 및 FK 존재(count=1) 단언.
- **판정:** CLOSED

### T-01-10 — Information Disclosure — 엔티티 API 직접 노출 (accept)
- **수용 근거:** 이 phase에 컨트롤러/서비스/리포지토리 없음. Phase 2가 DTO로 감쌈(D-019).
- **재검증:** `find src/main/kotlin/com/goldwrestling/{branch,member,admin} -type f` → `Branch.kt`/`Member.kt`/`MemberStatus.kt`/`Admin.kt`/`AdminBranch.kt` 5개 매핑 파일만 존재, `*Controller.kt`/`*Service.kt`/`*Repository.kt`/`dto/` 없음.
- **판정:** CLOSED (accepted)

### T-01-11 — Information Disclosure — 생성된 openapi.yaml (mitigate)
- **기대:** `servers` `/` 고정, `localhost` 0건, `/internal-test/*` 경로 부재.
- **증거:**
  - `docs/api/openapi.yaml:6-7` — `servers:\n- url: /`.
  - `grep -c "localhost" docs/api/openapi.yaml` → 0 (실측, 01-03-SUMMARY.md에도 기록).
  - `grep -n "internal-test" docs/api/openapi.yaml` → 매치 없음.
- **판정:** CLOSED

### T-01-12 — Denial of Service — 8099 백그라운드 프로세스 잔존 (mitigate)
- **기대:** `finalizedBy(stopApiDocsApp)` 3개 태스크, 멱등 종료. 실패 경로 실측으로 잔존 0 확인.
- **증거:**
  - `build.gradle.kts` — `grep -c 'finalizedBy("stopApiDocsApp")'` → 3 (`waitApiDocsApp`, `downloadApiDocs`, `generateApiDocs`에 각각 걸림).
  - `stopApiDocsApp` 태스크(`isIgnoreExitValue = true`, `kill ... || true`) — 멱등 구현.
  - `01-03-SUMMARY.md` "실패 경로 실행 결과" 절 — DB 중단 상태에서 `./gradlew generateApiDocs` 실패(exit 1, 62초) 후 `lsof -i :8099` no match, `app.pid` 파일 없음을 실측 확인.
- **판정:** CLOSED

### T-01-13 — Information Disclosure — build/apiDocs/app.log DB 접속 정보 (mitigate)
- **기대:** 로그/PID를 `build/` 하위(gitignore)에만 둠.
- **증거:**
  - `build.gradle.kts` — `apiDocsDir = layout.buildDirectory.dir("apiDocs")`, `apiDocsPidFile`/`apiDocsLogFile`도 같은 하위 경로.
  - `.gitignore:19` — `build/` 무시.
  - `01-03-SUMMARY.md` "app.log 실값 노출 점검" 절 — `grep -i "password"` 매치 0건 확인, `git status --short`로 `build/` 하위 파일 미추적 재확인.
- **판정:** CLOSED

### T-01-14 — Tampering — 스펙-코드 드리프트 (mitigate)
- **기대:** `add-endpoint` 스킬 §6 + conventions §6 단일 명령 지시, 태스크 항상 실행(UP-TO-DATE 없음).
- **증거:**
  - `.claude/skills/add-endpoint/SKILL.md:65` — `./gradlew generateApiDocs` 지시, `grep -c 'bootRun &'` → 0(옛 수동 절차 제거 확인).
  - `docs/conventions.md:112` — §6에 `generateApiDocs` 명령·전제·소요시간 명시.
  - `build.gradle.kts`의 `downloadApiDocs` 주석 — "`docs/api/openapi.yaml`을 outputs로 선언하지 않는다 — UP-TO-DATE로 건너뛰면 계약이 깨진다"로 매번 실행됨을 코드 수준에서 보장.
- **판정:** CLOSED

### T-01-15 — Elevation of Privilege — docker compose down -v 볼륨 삭제 (mitigate)
- **기대:** 실패 경로 검증은 stop/start만 사용하도록 문서화.
- **증거:**
  - `.planning/phases/01-foundation/01-03-PLAN.md` Task 2 action — "**`docker compose down -v`를 쓰지 않는다**" 명시, `stop`/`start` 절차 지시.
  - `01-03-SUMMARY.md` "실패 경로 실행 결과" — 실제로 `docker compose stop postgres` → ... → `docker compose start postgres`로 복구했음을 기록(`down -v` 미사용).
- **판정:** CLOSED

### T-01-SC — Tampering — 공급망(신규 의존성) (accept, 01-01/01-02/01-03 공통)
- **수용 근거:** 각 플랜 모두 신규 외부 의존성/플러그인 0건.
- **재검증:** `git diff a7a0392 HEAD -- build.gradle.kts` — 추가된 hunk는 파일 하단의 `generateApiDocs` 태스크 체인(주석·`tasks.register` 블록)뿐이며 `dependencies{}`/`plugins{}` 블록에는 변경 없음(diff에 해당 블록 hunk 부재).
- **판정:** CLOSED (accepted)

## 수용된 위험(Accepted Risks) 로그

| Threat ID | 위험 | 수용 사유 | 재검토 시점 |
|---|---|---|---|
| T-01-05 | 에러 응답을 통한 경로/리소스 존재 여부 열거(Spoofing) | Phase 1은 인증·인가가 없고(`SecurityConfig` `permitAll`) 노출 리소스가 헬스 엔드포인트뿐이라 열거로 얻을 정보가 없음 | Phase 2(인증) 도입 시 404/403 구분 정책 수립 필요 |
| T-01-10 | JPA 엔티티가 향후 API로 직접 노출될 잠재 위험 | 이 phase는 컨트롤러/서비스/리포지토리를 만들지 않아 노출 경로 자체가 없음(D-019는 Phase 2의 DTO 래핑을 전제) | Phase 2에서 컨트롤러 도입 시 엔티티가 DTO 없이 반환되지 않는지 확인 |
| T-01-SC | 신규 의존성 도입에 따른 공급망 리스크 | 01-01/01-02/01-03 세 플랜 모두 `build.gradle.kts`의 `dependencies`/`plugins` 블록을 변경하지 않음(diff로 확인) | 다음에 신규 의존성을 추가하는 phase에서 `verify-boot4-api` §2(좌표·버전 실측) 재적용 |

## 미등록 신규 공격면(Unregistered Flags)

`01-01-SUMMARY.md`, `01-02-SUMMARY.md`, `01-03-SUMMARY.md`에 `## Threat Flags` 섹션이 존재하지 않으며,
"threat"/"security"/"보안"/"취약" 키워드로 재검색해도 매치가 없다. 세 SUMMARY 모두에서 실행자가
플랜 범위를 벗어난 새 공격면을 만들지 않았다고 보고 — 이번 감사에서 추가로 발견된 미등록 공격면 없음.

## 검증 범위 밖 참고 사항 (판정에 영향 없음)

- `docs/decisions.md` D-028/D-029가 정확히 기록되어 있고(§T-01-04, §T-01-14 증거), 예시 플레이스홀더가 D-030으로 밀려 있음을 확인.
- Phase 1의 세 플랜(01-01/01-02/01-03)은 커밋 정책상 처음엔 "커밋 금지"로 지시됐으나, 오케스트레이터가 기록한 사용자 승인에 따라 01-02/01-03부터 태스크별 커밋이 실제로 발생했다(`git log` 확인). 이 사실은 위협 모델 판정에 영향을 주지 않는다(커밋 여부는 STRIDE 위협 항목이 아님).
