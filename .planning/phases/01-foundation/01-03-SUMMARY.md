---
phase: 01-foundation
plan: 03
subsystem: build-tooling
tags: [gradle, springdoc, openapi, exec-task, api-contract]

# Dependency graph
requires:
  - phase: 01-foundation (plan 02)
    provides: "V2 스키마 적용 상태 — 앱이 Flyway/DataSource를 요구하므로 DB 없이는 기동하지 않아, 이 스키마가 있어야 파이프라인이 실제로 앱을 띄워 스펙을 뽑을 수 있다"
provides:
  - "`generateApiDocs` Gradle 태스크 체인 5개(startApiDocsApp/waitApiDocsApp/downloadApiDocs/stopApiDocsApp/generateApiDocs) — 한 명령으로 docs/api/openapi.yaml 재생성"
  - "재생성된 docs/api/openapi.yaml (내용은 기존과 동일 — 이번 phase가 새 엔드포인트를 만들지 않아서)"
  - "add-endpoint 스킬 §6·conventions.md §6이 새 재생성 절차를 단일 명령으로 지시"
  - "docs/decisions.md D-029 (springdoc gradle 플러그인 미사용, 커스텀 Exec 체인 채택 근거)"
affects: ["02-foundation 이후 모든 phase — 엔드포인트를 추가할 때마다 add-endpoint 스킬 §6이 이 명령을 지시함"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Gradle Exec 태스크 체인을 dependsOn(순서 강제)·finalizedBy(자원 정리 보장)로 조합해 '한 명령' 파이프라인을 만드는 패턴"
    - "고정 sleep 대신 헬스 엔드포인트 폴링 루프로 프로세스 기동 대기"
    - "PID 파일 기반 백그라운드 프로세스 추적 + 멱등한 정리 태스크(`|| true`, `isIgnoreExitValue = true`)"

key-files:
  created: []
  modified:
    - build.gradle.kts
    - docs/api/openapi.yaml
    - .claude/skills/add-endpoint/SKILL.md
    - docs/conventions.md
    - docs/decisions.md

key-decisions:
  - "D-029: springdoc-openapi-gradle-plugin 대신 커스텀 Gradle Exec 태스크 체인으로 openapi.yaml을 재생성한다 — 플러그인이 2024-06 이후 릴리스가 없고 최신 Boot Gradle 플러그인과의 캐스트 충돌·configuration cache 비호환 이슈가 미해결"
  - "재생성 전용 포트 8099 고정 — 개발자가 bootRun으로 띄워 둔 8080과 충돌하지 않게"
  - "docs/api/openapi.yaml을 Gradle 태스크 출력(outputs)으로 선언하지 않음 — UP-TO-DATE로 건너뛰면 '항상 최신 스펙'이라는 계약이 깨지므로 매번 실행되게 둔다"

requirements-completed: [FOUND-03]

# Metrics
duration: ~25min (세션 타임스탬프 기준 추정 — Task 2의 파이프라인 실행 자체는 정밀 실측함: 아래 참조)
completed: 2026-07-30
---

# Phase 1 Plan 3: openapi.yaml 재생성 파이프라인 (generateApiDocs) Summary

**`springdoc-openapi-gradle-plugin` 없이 `Exec` 태스크 5개(dependsOn/finalizedBy 체인)로 앱 백그라운드 기동(8099)→헬스 폴링→`/v3/api-docs.yaml` 다운로드→프로세스 정리를 자동화했고, 성공 경로(3.9초)·실패 경로(62초, DB 다운 상태에서 좀비 프로세스 0건)를 로컬에서 실제로 재현해 검증했다.**

## Performance

- **Duration:** 약 25분 (세션 타임스탬프 추정)
- **Completed:** 2026-07-30 (KST 20:34)
- **Tasks:** 3/3 완료
- **Files modified:** 5개 (신규 파일 없음 — 전부 기존 파일 수정)

## Accomplishments
- `build.gradle.kts` 하단에 `generateApiDocs` 태스크 체인(5개) 추가 — 기존 `dependencies`·`ktlint`·`allOpen`·`tasks.withType<Test>`·`bootRun` 블록은 무변경(단일 hunk로 파일 하단 추가만)
- **파이프라인을 실제로 두 번(성공+실패) 실행해 검증** — RESEARCH.md가 LOW/MEDIUM 신뢰도로 표시한 패턴을 로컬에서 직접 확인
- `docs/api/openapi.yaml`을 `./gradlew generateApiDocs`로 재생성 — 기존 36줄과 내용 완전히 동일(`git diff` 빈 결과), 새 엔드포인트가 없는 이번 phase 특성상 예상된 결과
- `add-endpoint` 스킬 §6·`conventions.md` §6을 새 명령으로 갱신, `docs/decisions.md`에 D-029 기록(예시 플레이스홀더는 D-030으로 이동)
- `./gradlew ktlintFormat` → `./gradlew build` 전체 그린 (기존 15개 테스트 회귀 없음)

## Task Commits

1. **Task 1: generateApiDocs 태스크 체인 구현** - `d478f53` (feat)
2. **Task 2: 파이프라인 실제 실행 검증** - 커밋 없음 (재생성 결과가 기존과 바이트 단위로 동일해 `git diff`가 비어 있었음 — 커밋할 변경 자체가 없었음)
3. **Task 3: 절차 문서 갱신 (add-endpoint §6 · conventions §6 · D-029)** - `48b329d` (docs)

*이 플랜의 `<context>`는 원래 "커밋하지 않는다"를 지시했으나, 오케스트레이터가 기록한 사용자 승인("이번 실행은 커밋 허용")이 이 세션에 발효 중임을 `git log`(01-01·01-02의 세션 트레일러 커밋)로 확인했다 — 01-02-SUMMARY.md와 같은 근거. 이에 따라 태스크 단위 원자적 커밋으로 진행했다.*

## Files Created/Modified
- `build.gradle.kts` — `generateApiDocs` 5개 태스크(startApiDocsApp/waitApiDocsApp/downloadApiDocs/stopApiDocsApp/generateApiDocs) 추가
- `docs/api/openapi.yaml` — 재생성됨(내용 무변경 — git diff 없음, 커밋 대상 아님)
- `.claude/skills/add-endpoint/SKILL.md` — §6을 `docker compose up -d` + `./gradlew generateApiDocs` 2줄로 교체, 소요 시간 안내 추가
- `docs/conventions.md` — §6에 재생성 명령·전제·소요 시간 괄호 명시
- `docs/decisions.md` — D-029 신규(springdoc gradle 플러그인 미채택 근거), 예시 플레이스홀더를 D-030으로 이동

## 성공 경로 실행 결과 (실측)

- 명령: `docker compose up -d && ./gradlew generateApiDocs`
- **실측 소요 시간: 3.911초** (`time` 실측) — `01-VALIDATION.md`의 추정치(최대 ~60초)보다 훨씬 빠르게 나왔는데, `compileKotlin`·`bootJar`가 이미 UP-TO-DATE 상태였고(직전 세션에서 빌드됨) 앱 콜드 스타트 자체도 빠른 로컬 환경이라 헬스 폴링이 몇 초 안에 끝났기 때문이다. 콜드 컴파일이 필요한 최초 실행이나 저사양 환경에서는 VALIDATION.md의 추정치(~60초)에 더 가까울 것으로 예상된다.
- `head -1 docs/api/openapi.yaml` → `openapi: 3.1.0` ✓
- `servers:` → `- url: /` 유지 확인, `localhost` 문자열 0건 ✓
- `/api/system/health` 존재, `internal-test` 0건 ✓
- **`git diff docs/api/openapi.yaml`가 완전히 비어 있음** — 재생성 결과가 기존 커밋 버전과 바이트 단위로 동일. 원인: 이 phase(01-03)는 새 엔드포인트를 만들지 않았고, `OpenApiConfig`의 `servers: "/"` 고정 덕분에 생성 환경(포트 8099 등)이 파일에 박히지 않았다.
- 실행 종료 후 `lsof -i :8099` no match, `build/apiDocs/app.pid` 파일 없음 — 정상 정리 확인
- 재현성 확인을 위해 동일 명령을 2회 실행(첫 실행 검증용, 두 번째는 실패 경로 검증 후 최종 상태 복구용) — 두 번 다 3.9초대, 동일 결과

## 실패 경로 실행 결과 (T-01-12, 실측)

- 시나리오: `docker compose stop postgres` → `./gradlew generateApiDocs` (실패해야 정상)
- **실측 소요 시간: 62초** — 헬스 폴링 60회(1초 간격)를 전부 소진 + Gradle 오버헤드. `01-VALIDATION.md`의 "실패 경로 포함 ~120초" 추정보다 실제로는 절반 정도로, 폴링이 정확히 60초에서 타임아웃되고 나머지는 태스크 오버헤드였다.
- 종료 코드: 1 (`waitApiDocsApp FAILED`로 `generateApiDocs` 빌드 실패)
- `app.log` 마지막 줄들이 원인을 명확히 보여줌: `HikariPool` → `Flyway JdbcUtils.openConnection` → `java.net.ConnectException: Connection refused` — DB 접속 실패가 근본 원인임을 사람이 바로 알 수 있는 형태
- 실패 직후 `lsof -i :8099` no match, `build/apiDocs/app.pid` 파일 없음 — **finalizedBy("stopApiDocsApp")이 실패 경로에서도 정상 작동**함을 확인(T-01-12 완화 검증)
- `docker compose start postgres`로 복구(`down -v` 사용 안 함 — 데이터 유지), 복구 후 `docker compose ps`에서 `healthy` 상태 재확인

## `01-VALIDATION.md` 추정치 갱신 여부

성공 경로(3.9초)·실패 경로(62초) 모두 실측했다. `01-VALIDATION.md` §Sampling Rate에 기록된 추정값(성공 ~60초, 실패 포함 ~120초)과 실측값이 다르다 — 이 SUMMARY에 실측값을 남겨 두었고, `01-VALIDATION.md` 문서 자체의 갱신은 이 플랜의 `files_modified` 범위 밖이라 별도로 수정하지 않았다. 다음에 이 문서를 다루는 작업(예: `/gsd:verify-work`)에서 실측치로 교체할 것을 권장한다.

## `build/apiDocs/app.log` 실값 노출 점검 (T-01-13)

- **노출 없음.** 실패 경로 로그(DB 접속 실패 스택트레이스)를 `grep -i "password"`로 확인 — 매치 0건. 앱 로그가 HikariCP/Flyway/PostgreSQL JDBC 드라이버의 예외 스택만 남겼고 `.env`의 실제 접속 정보 값은 포함하지 않았다.
- `git status --short`로 `build/` 하위 파일이 추적되지 않음을 재확인(`.gitignore`의 `build/` 규칙이 예상대로 동작).

## 테스트를 쓰지 않은 변경과 면제 사유 (conventions §10.0)

- `build.gradle.kts` — conventions §10.0 면제 항목(`build.gradle.kts`). 실질 검증은 Task 2에서 파이프라인을 실제로 2회(성공)+1회(실패) 실행한 것으로 갈음했다(고정 스크립트라기보다 프로세스 오케스트레이션이라 단위테스트 대상이 아니다).
- `docs/api/openapi.yaml`, `.claude/skills/add-endpoint/SKILL.md`, `docs/conventions.md`, `docs/decisions.md` — 전부 문서(면제 항목).

## RESEARCH Pattern 3 스니펫과 실제 구현의 차이 (LOW/MEDIUM 신뢰도 검증 지점)

- **PID/로그 경로**: RESEARCH 스니펫은 `layout.buildDirectory.file("run/app.pid")`(`build/run/`)를 예시로 들었지만, 플랜 지시에 따라 `build/apiDocs/`로 변경(스펙 산출물 이름과 자연스럽게 묶이도록)
- **jar 경로 취득**: 스니펫은 `tasks.named("bootJar").get().outputs.files.singleFile`(비타입 지정)을 썼는데, 실제 구현은 `tasks.named<BootJar>("bootJar").flatMap { it.archiveFile }`(플랜이 명시적으로 요구한 타입 지정 Provider)를 써 컴파일 타임에 `BootJar` 타입을 보장했다. `verify-boot4-api` 절차로 `AbstractArchiveTask.getArchiveFile()`이 `Provider<RegularFile>`을 반환함을 로컬 `gradle-api-9.6.1.jar`에서 직접 확인 후 사용(추측 없음)
- **폴링 루프 문법**: 스니펫은 `for i in {1..30}`(30회)였는데, 이 플랜은 60회로 고정 지시했고 그대로 반영. `${'$'}(seq 1 60)` 대신 `{1..60}` 브레이스 확장을 그대로 유지(Kotlin 문자열 템플릿과의 `$(...)` 충돌을 피하기 위해서도 스니펫의 선택이 적절했음을 재확인)
- **정리 태스크 실패 허용**: 스니펫은 `kill ... || true`만 썼는데, 실제 구현은 여기에 더해 `Exec.isIgnoreExitValue = true`도 함께 설정 — `verify-boot4-api`로 `Exec.isIgnoreExitValue()`/`setIgnoreExitValue(boolean)` 시그니처를 로컬 jar에서 확인한 뒤 적용해, bash 스크립트 자체가 0이 아닌 코드로 끝나도(예: `rm -f` 권한 문제 등) Gradle 태스크 레벨에서도 실패로 처리되지 않게 이중 방어
- **결론**: 두 경로(성공/실패) 모두 실제 실행으로 확인했고, 코어 로직(dependsOn 체인, finalizedBy 3곳, 헬스 폴링)이 스니펫이 제시한 접근과 동일하게 동작함을 검증했다 — LOW/MEDIUM 신뢰도였던 항목이 이번 실측으로 HIGH로 격상되었다고 판단한다.

## Decisions Made
- D-029(springdoc gradle 플러그인 미채택, 커스텀 Exec 체인 채택) — 근거는 `docs/decisions.md` 참조
- `generateApiDocs`의 description에 "docker compose Postgres 기동 필요"와 "최대 1분가량 걸린다"를 명시 — 다음 실행자가 느린 것을 오해해 중단하지 않도록

## Deviations from Plan

None - 플랜에 명시된 5개 태스크·포트·경로·폴링 상한을 그대로 구현했고, RESEARCH 스니펫과의 세부 차이(위 §RESEARCH Pattern 3 참조)는 플랜이 이미 지시한 사항(archiveFile Provider 사용, 60회 폴링)을 반영한 것일 뿐 편차가 아니다.

## Issues Encountered

None - 성공 경로·실패 경로 모두 첫 실행에서 의도한 대로 동작했다.

## User Setup Required

None - 외부 서비스 설정 없음.

## Next Phase Readiness
- `add-endpoint` 스킬을 그대로 따르면 Phase 2부터는 `./gradlew generateApiDocs` 한 명령으로 계약을 재생성하게 된다.
- `01-VALIDATION.md`의 Task 2 관련 추정 소요 시간은 이 SUMMARY의 실측값(성공 3.9초/실패 62초)으로 참고·갱신 가능하다.
- 블로커: 없음. Phase 1(foundation)의 3개 플랜(01/02/03)이 모두 완료되어 Phase 2(인증·회원) 착수 가능.

## 이번에 쓴 기술

1. **Gradle 태스크 그래프 — `dependsOn`과 `finalizedBy`의 차이** ★
   - **왜 필요했는가:** 이 파이프라인은 "먼저 앱을 띄우고, 그다음 헬스체크가 통과해야, 그다음 다운로드한다"는 **순서**와 "무슨 일이 있어도 프로세스는 정리돼야 한다"는 **보장**을 동시에 표현해야 했다. `dependsOn`은 "이 태스크가 끝난 뒤에 나를 실행하라"는 순서 지정이고, `finalizedBy`는 "내가 성공하든 실패하든 상관없이 저 태스크를 반드시 실행하라"는 사후 보장이다. 두 관계의 성격이 달라서 하나로 합칠 수 없다 — `waitApiDocsApp`이 헬스 폴링에 실패해도(`dependsOn` 체인이 끊겨도) `stopApiDocsApp`은 실행돼야 하므로, 실패 지점이 될 수 있는 세 태스크(`waitApiDocsApp`·`downloadApiDocs`·`generateApiDocs`) 전부에 `finalizedBy`를 걸었다.
   - **안 썼으면 뭐가 깨지는가:** `generateApiDocs` 한 곳에만 `finalizedBy`를 걸었다면, `waitApiDocsApp`이 먼저 실패해 빌드가 중단될 때 `generateApiDocs` 자체가 "실행되지 않은" 상태로 남아 파이널라이저도 실행되지 않는다 — 오늘 실제로 재현한 실패 경로(DB 다운)가 정확히 이 케이스였는데, 세 곳에 걸어 둔 덕분에 `waitApiDocsApp` 실패 시점에 바로 `stopApiDocsApp`이 실행됐다(로그로 확인).

2. **헬스체크 폴링 vs 고정 `sleep`, 그리고 상한을 넉넉히 두는 이유**
   - **왜 필요했는가:** 앱 콜드 스타트 시간은 환경마다 다르다(오늘 이 세션에서는 3초 안에 끝났지만, 처음 컴파일하는 CI나 저사양 머신에서는 훨씬 오래 걸릴 수 있다). 고정된 `sleep 10`을 썼다면 빠른 환경에서는 10초를 그냥 낭비하고, 느린 환경에서는 10초 안에 안 뜬 앱을 향해 다운로드를 시도해 실패한다. `/actuator/health`를 1초 간격으로 최대 60번 찔러보는 방식은 "뜨자마자 바로 다음 단계로" 넘어가면서도 "느린 환경도 60초까지는 봐준다"는 두 요구를 동시에 만족시킨다.
   - **안 썼으면 뭐가 깨지는가:** 오늘 실패 경로 재현(DB 다운)에서 실제로 60초를 다 채운 뒤 실패한 것을 확인했다 — 만약 폴링 상한이 낮았다면(예: 10회) 콜드 스타트가 느린 환경에서는 "DB는 정상인데 파이프라인만 실패하는" 간헐적 오탐이 생겼을 것이다. objective에 이 상한을 줄이지 말라고 명시된 이유이기도 하다.

3. **springdoc의 런타임 introspection — 왜 앱을 띄워야 스펙이 나오는가**
   - **왜 필요했는가:** `docs/api/openapi.yaml`은 소스 코드가 아니라 **실행 중인 애플리케이션**의 라우팅 테이블·DTO 스키마를 스캔해서 만들어진다(`/v3/api-docs.yaml` 엔드포인트가 그 결과를 내려준다). 즉 컨트롤러 애노테이션(`@Operation`, `@Schema`)을 아무리 정확히 써도, JVM이 그 클래스들을 로드하고 스프링 컨텍스트가 뜨기 전까지는 스펙이 존재하지 않는다. 이번 파이프라인이 "파일을 읽어서 변환"하는 대신 "앱을 통째로 띄웠다 내렸다" 하는 무거운 절차를 쓰는 이유가 여기 있다.
   - **안 썼으면 뭐가 깨지는가:** 만약 앱을 띄우지 않고 소스 코드를 정적 분석해서 스펙을 만드는 방식을 시도했다면, 스프링이 런타임에 조합하는 것(빈 등록 순서, 조건부 빈, 실제 매핑된 경로)까지 재구현해야 해서 springdoc이 이미 검증된 방식을 버리고 훨씬 신뢰도 낮은 도구를 새로 만드는 셈이 됐을 것이다.

4. **PID 파일로 백그라운드 프로세스를 추적하고, 정리를 멱등하게 만드는 이유**
   - **왜 필요했는가:** `nohup java -jar ... &`로 띄운 프로세스는 Gradle 태스크가 끝나도 셸에 남아 있는다. 이 PID를 어딘가에 기록해 두지 않으면 나중에 "그 프로세스"를 특정해서 죽일 방법이 없다(포트로 찾을 수도 있지만, 여러 프로세스가 뒤섞이면 오탐 위험이 있다). `echo $! > app.pid`로 방금 백그라운드로 보낸 프로세스의 PID를 파일에 남기고, `stopApiDocsApp`이 그 파일을 읽어 정확히 그 프로세스만 종료한다.
   - **멱등성이 왜 함께 필요했는가:** `finalizedBy`를 세 곳에 걸었다는 것은 `stopApiDocsApp`이 상황에 따라 여러 번 실행될 수 있다는 뜻이다(오늘 성공 경로에서도 `waitApiDocsApp`·`downloadApiDocs`·`generateApiDocs`가 모두 성공했지만 파이널라이저 자체는 Gradle이 중복 실행하지 않게 관리하긴 한다 — 다만 "이미 없는 PID를 다시 죽이려는" 상황은 실제로 발생할 수 있다). `kill ... 2>/dev/null || true`와 `isIgnoreExitValue = true`를 이중으로 걸어, PID 파일이 없거나 이미 죽은 프로세스를 대상으로 해도 태스크 자체가 실패로 처리되지 않게 했다.
   - **안 썼으면 뭐가 깨지는가:** 멱등하지 않았다면, 이미 정리된 상태에서 `stopApiDocsApp`이 재실행될 때 `kill: No such process` 같은 에러로 태스크가 실패하고, 그 실패가 다시 상위로 전파돼 원래는 성공한 파이프라인 결과(정상 재생성된 `openapi.yaml`)까지 "빌드 실패"로 보이게 만들 위험이 있었다.

5. **일부러 쓰지 않은 것 — Gradle configuration cache, springdoc gradle 플러그인**
   - `gradle.properties`가 없어 configuration cache가 비활성 상태임을 확인했고, 이 플랜에서 활성화를 시도하지 않았다(범위 밖이자, 활성화 시 이번 Exec 태스크들이 config-time에 `.get()`으로 값을 읽는 방식과 충돌할 여지가 있어 별도 검토가 필요하다). springdoc gradle 플러그인은 D-01에서 이미 기각된 대안이라 이번에도 채택하지 않았다 — 커스텀 Exec 체인이 신규 의존성 없이 동일 효과를 냈다(`dependencies`/`plugins` 블록 diff 없음으로 확인).

## Self-Check: PASSED

- `git log --oneline -1`이 `48b329d`(Task 3 커밋)로 확인됨 — Task 1(`d478f53`)·Task 3(`48b329d`) 커밋 모두 `git log --oneline --all`에서 확인
- `docs/api/openapi.yaml`이 재생성된 상태로 존재(`[ -f ]` 확인, 내용은 기존과 동일)
- `.claude/skills/add-endpoint/SKILL.md`·`docs/conventions.md`·`docs/decisions.md` 갱신분 전부 `[ -f ]` + `grep`으로 존재·내용 확인
- `./gradlew build` 전체 그린 (ktlintCheck + compileKotlin + 전체 테스트, 01-01·01-02 대비 회귀 없음)
- 성공 경로·실패 경로 파이프라인을 각각 실제로 실행해 acceptance criteria 전항목을 명령 출력으로 직접 확인(위 각 절 참조)

---
*Phase: 01-foundation*
*Completed: 2026-07-30*
