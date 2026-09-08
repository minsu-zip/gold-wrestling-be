---
phase: 1
slug: foundation
status: approved
nyquist_compliant: true
wave_0_complete: false
wave_0_owner: "01-01 Task 2 (tdd RED 단계에서 GlobalExceptionHandlerTest.kt 신규 작성)"
created: 2026-07-30
updated: 2026-07-30
---

# Phase 1 — Validation Strategy

> 실행 중 피드백 샘플링을 위한 phase 단위 검증 계약.
> 출처: `01-RESEARCH.md` §Validation Architecture + 01-01/02/03 PLAN.md 의 `<verify><automated>` 커맨드.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (`kotlin-test-junit5`) + AssertJ + Testcontainers 2.x (`testcontainers-postgresql`, `spring-boot-testcontainers`) |
| **Config file** | 없음 — `build.gradle.kts` 의 `tasks.withType<Test> { }` 블록이 유일한 설정 (`-Duser.timezone=Asia/Seoul`) |
| **Quick run command** | `./gradlew test --tests "<변경 패키지>*"` (예: `--tests "com.goldwrestling.common.error.*"`) |
| **Full suite command** | `./gradlew build` (ktlintCheck + compileKotlin + 전체 테스트 + `ddl-auto=validate`) |
| **Estimated runtime** | quick(필터) ~40–60초 · full ~90–180초 — 둘 다 Testcontainers Postgres 컨테이너 기동 시간 포함. **실측값은 각 SUMMARY에 기록한다**(현재는 추정) |
| **Prerequisite** | Docker 데몬 실행 중. `01-03` Task 2만 추가로 `docker compose up -d`(로컬 Postgres) 필요 |

프레임워크 설치는 이미 끝나 있다 — 기존 `HealthControllerTest`, `FlywayMigrationIntegrationTest`가 동작하는 것이 증거다.
이 phase에서 테스트 프레임워크·의존성을 추가하지 않는다.

---

## Sampling Rate

- **태스크 완료 직후:** 해당 태스크의 `<automated>` 커맨드 (아래 맵의 "Automated Command" 열)
- **플랜(웨이브) 완료 직후:** `./gradlew build` — D-10에 따라 dev PR 전에 반드시 초록
- **`/gsd:verify-work` 전:** `./gradlew build` 통과 + `01-03` Task 2 파이프라인 실행 1회 성공
- **Max feedback latency:** **60초** (Testcontainers 컨테이너 기동을 포함한 통합테스트 기준)
  - 예외 1건: `01-03 Task 2`는 앱 콜드 스타트 + 헬스 폴링(최대 60회) 때문에 최대 ~60초, 실패 경로까지 포함하면 ~120초까지 걸린다.
    이 태스크는 **의도적으로 느린 검증**이며, 빠른 개발 루프에서는 `01-03 Task 1`의 `./gradlew help --task generateApiDocs`(설정 단계 확인, ~5초)로 대체한다.
    상세는 `01-03-PLAN.md` §피드백 지연 참조.
- **Watch 모드 금지:** `--continuous` / `-t` 플래그를 검증 커맨드에 쓰지 않는다 (종료 코드가 없어 게이트로 쓸 수 없다)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | FOUND-01 | T-01-04 | 에러코드↔HTTP 상태 대응이 코드(`ErrorCode.defaultStatus`)에 고정되어 문서와 갈라지지 않는다. 도메인 코드 선점 없음 | static + compile | `./gradlew compileKotlin` + `ErrorCode.kt`에 6개 코드 존재 grep (정확한 커맨드는 `01-01-PLAN.md` Task 1 `<verify><automated>` — grep BRE 이스케이프 때문에 여기 옮겨 적지 않는다) | ❌ W0 (이 태스크가 생성) | ⬜ pending |
| 01-01-02 | 01 | 1 | FOUND-01 | T-01-01 / T-01-02 / T-01-03 | 500 응답 본문에 예외 메시지·클래스명·스택트레이스가 없다. 내장 예외의 `detail`을 `ex.message`로 덮어쓰지 않는다. 테스트 전용 `/internal-test/*`가 `src/main`·`openapi.yaml`로 새지 않는다 | integration (tdd RED→GREEN) | `./gradlew test --tests "com.goldwrestling.common.error.GlobalExceptionHandlerTest"` | ❌ W0 (이 태스크의 RED 단계가 생성) | ⬜ pending |
| 01-01-03 | 01 | 1 | FOUND-01 | T-01-04 | 에러코드 레지스트리 운영 규칙(신규 코드 추가 시 같은 PR에서 `docs/error-codes.md` 갱신)이 D-028로 못박힌다 | static + full suite | `./gradlew ktlintFormat && ./gradlew build && grep -c '^## D-028\. 에러 응답' docs/decisions.md` | ✅ | ⬜ pending |
| 01-02-01 | 02 | 2 | FOUND-02 | T-01-06 / T-01-07 / T-01-08 / T-01-09 | 시드에 자격증명·개인정보 없음(`branch` 1건만). `admin`에 로그인 자격 컬럼 없음. `member.branch_id` NOT NULL FK로 지점 없는 회원 행 불가. `uq_admin_branch`로 중복 권한 부여 거부 | integration | `./gradlew test --tests "com.goldwrestling.db.FlywayMigrationIntegrationTest"` | ✅ (기존 파일, 케이스 3건 추가) | ⬜ pending |
| 01-02-02 | 02 | 2 | FOUND-02 | T-01-10 | 엔티티 노출 경로(Repository/Service/Controller/DTO)를 만들지 않아 엔티티가 API로 새지 않는다. `ddl-auto=validate`가 스키마↔매핑 불일치를 기동 시점에 거부한다 | integration (validate 게이트) | `./gradlew ktlintFormat && ./gradlew build` | ✅ (01-02-01의 통합테스트 재사용) | ⬜ pending |
| 01-03-01 | 03 | 3 | FOUND-03 | T-01-14 | 재생성 태스크가 UP-TO-DATE로 스킵되지 않아 "항상 최신 스펙" 계약이 유지된다. PID·로그가 `build/` 하위(gitignore) | config-stage (fast, ~5초) | `./gradlew tasks --group documentation \| grep -q generateApiDocs && ./gradlew help --task generateApiDocs` | ✅ | ⬜ pending |
| 01-03-02 | 03 | 3 | FOUND-03 | T-01-11 / T-01-12 / T-01-13 | 생성된 스펙에 `localhost`·`/internal-test/*` 없음(`servers: /` 유지). 실패 경로에서도 8099 점유 프로세스 잔존 없음. `app.log`에 `.env` 실값 노출 없음 | e2e (**slow, ~60초 · 실패 경로 포함 ~120초**) | `docker compose up -d && ./gradlew generateApiDocs && head -1 docs/api/openapi.yaml \| grep -q 'openapi: 3.1' && grep -A1 '^servers:' docs/api/openapi.yaml \| grep -q 'url: /' && ! grep -q 'localhost' docs/api/openapi.yaml && ! grep -q 'internal-test' docs/api/openapi.yaml` | ✅ | ⬜ pending |
| 01-03-03 | 03 | 3 | FOUND-03 | T-01-14 | 절차 문서(`add-endpoint` §6, conventions §6)가 옛 수동 절차를 지시하지 않는다 — 다음 phase 실행자가 재생성을 빠뜨리는 경로를 없앤다 | static + full suite | `grep -q 'generateApiDocs' .claude/skills/add-endpoint/SKILL.md && grep -q 'generateApiDocs' docs/conventions.md && grep -q '^## D-029\.' docs/decisions.md && ./gradlew ktlintFormat && ./gradlew build` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**샘플링 연속성:** 8개 태스크 전부 `<automated>` 보유 — 자동 검증 없는 태스크가 3연속 이상 이어지는 구간이 없다.

---

## Wave 0 Requirements

RESEARCH.md §Wave 0 Gaps의 두 항목에 대한 처리:

- [ ] `src/test/kotlin/com/goldwrestling/common/error/GlobalExceptionHandlerTest.kt` — FOUND-01 커버. **미존재.**
      별도 Wave 0 플랜을 만들지 않고 `01-01 Task 2`가 `tdd="true"`의 RED 단계에서 이 파일을 먼저 작성한다
      (테스트 → 실패 확인 → `GlobalExceptionHandler` 구현 순서). 즉 Wave 0 = 그 태스크의 첫 단계다.
- [x] **400/415/500/도메인 예외를 트리거할 엔드포인트 부재** — RESEARCH가 "플래너 결정 필요"로 남긴 항목.
      **결정: 프로덕션 `HealthController`를 건드리지 않고**, 테스트 파일 내부 중첩 `@TestConfiguration` + `@RestController`로
      `/internal-test/*` 엔드포인트를 등록하고 `@Import`로 명시 주입한다 (`01-01 Task 2` action 참조).
      이유: `HealthController`에 검증용 파라미터를 추가하면 그것이 `docs/api/openapi.yaml`(FE 계약)에 새겨진다.
- [x] 프레임워크 설치 — 불필요. 기존 `HealthControllerTest`·`FlywayMigrationIntegrationTest`가 배선 완료를 증명한다.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 재생성된 `docs/api/openapi.yaml` diff가 **의도한 변경만** 담고 있는지 | FOUND-03 | "의도한 변경"의 판단은 코드가 대신할 수 없다. 자동 검증은 `servers: /` 유지·`localhost` 0건·`internal-test` 0건까지만 커버한다 | `git diff docs/api/openapi.yaml` 를 읽고, 이 phase는 새 엔드포인트를 만들지 않았으므로 실질적 변경이 없어야 한다. 변경이 있으면 원인을 규명해 SUMMARY에 적는다 (`add-endpoint` SKILL §6 체크리스트) |
| `docs/error-codes.md` 표와 `ErrorCode.kt` enum 엔트리 목록 일치 | FOUND-01 | 두 파일의 "같은 6개"는 grep으로 존재 여부만 확인 가능하고, 초과/누락 없는 완전 일치는 사람이 1회 대조하는 편이 정확하다 | 두 파일을 나란히 열고 코드 이름·HTTP 상태를 1:1 대조. D-028이 정한 "코드 추가 시 같은 PR에서 표 갱신" 규칙이 이 대조를 반복 비용으로 만들지 않는다 |
| `build/apiDocs/app.log` 에 `.env` DB 비밀번호 실값 노출 여부 | FOUND-03 (T-01-13) | 자동화하면 커맨드·로그에 시크릿 패턴이 남을 수 있다. 사람이 확인하고 **"노출 있음/없음"만** 기록한다 | 로그를 열어 접속 정보 노출을 확인. SUMMARY에 실값을 절대 복사하지 않는다 |
| 실패 경로 재현 결과(DB 중단 시 좀비 프로세스 없음) | FOUND-03 (T-01-12) | 성공 경로 `<automated>`에 포함할 수 없다(의도적 실패를 요구). Task 2 action의 절차를 사람이 실행하고 결과를 기록한다 | `docker compose stop postgres` → `./gradlew generateApiDocs`(0이 아닌 종료 코드) → `lsof -i :8099` no match 확인 → `docker compose start postgres`. **`down -v` 금지**(로컬 데이터 삭제) |

---

## Validation Sign-Off

- [x] 모든 태스크가 `<automated>` verify를 갖거나 Wave 0 의존을 명시한다 (8/8)
- [x] 샘플링 연속성: 자동 검증 없는 태스크 3연속 구간 없음
- [x] Wave 0 의 MISSING 참조를 모두 다룬다 (`GlobalExceptionHandlerTest.kt` → 01-01 Task 2 RED 단계, 트리거 엔드포인트 → 테스트 전용 컨트롤러 결정)
- [x] watch 모드 플래그 없음 (`--continuous`/`-t` 미사용)
- [x] 피드백 지연 < 60초 — 예외 1건(`01-03 Task 2`, ~60–120초)은 위 §Sampling Rate에 명시하고 빠른 대체 커맨드를 지정함
- [x] `nyquist_compliant: true` frontmatter 설정
- [ ] `wave_0_complete` — 실행 중 `01-01 Task 2` RED 단계 완료 시 `true`로 갱신

**Approval:** approved 2026-07-30
