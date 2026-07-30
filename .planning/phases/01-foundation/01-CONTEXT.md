# Phase 1: 기반 - Context

**Gathered:** 2026-07-30
**Status:** Ready for planning

<domain>
## Phase Boundary

이후 모든 phase가 딛고 설 3가지 기반을 만든다:
1. **FOUND-01** — RFC 9457 ProblemDetail 전역 에러 응답. 스프링 내장 에러(400/404/405) 포함, 예외 없이 `application/problem+json`
2. **FOUND-02** — Flyway 초기 스키마: `Branch`, `Member`, `Admin`, `AdminBranch`(다대다). 핵심 테이블에 `branch_id` 확장 전제
3. **FOUND-03** — 한 명령으로 `docs/api/openapi.yaml` 재생성·커밋 가능한 파이프라인 (springdoc 기반)

기존 스켈레톤(HealthController, SecurityConfig permitAll, OpenApiConfig, V1 no-op baseline, Testcontainers 배선) 위에 쌓는다. 인증·회원 기능 자체는 Phase 2 스코프.

</domain>

<decisions>
## Implementation Decisions

### openapi.yaml 재생성 파이프라인 (FOUND-03)
- **D-01:** springdoc-openapi-gradle-plugin은 **사용하지 않는다** — 최신 1.9.0이 2024-06 이후 릴리스 없음, Boot 4 이전 시대. 미해결 이슈 #169(`BootRun_Decorated cannot be cast to BootRun` — 최신 Boot Gradle 플러그인과 캐스트 충돌), #166(configuration cache 비호환), #157(placeholder 해석 실패)로 Boot 4.1 환경에서 실패 위험이 높다.
- **D-02:** 대신 **커스텀 gradle 태스크**로 동일 동작 구현: 앱을 백그라운드로 기동 → `/v3/api-docs.yaml`을 HTTP로 다운로드 → `docs/api/openapi.yaml`로 저장. 이것들을 하나의 태스크로 묶어 "한 명령" 계약 충족.
- **D-03:** 로컬 docker-compose Postgres 기동을 전제로 한다. CI에서의 스펙 검증(스펙-코드 불일치 감지)은 배포 단계(M7)에서 고려 — 이번 스코프 아님.

### 초기 스키마 범위 (FOUND-02)
- **D-04:** **최소 스키마.** Phase 1은 확실한 정체성 컬럼만 정의한다. 인증 관련 컬럼(카카오 식별자, 관리자 로그인 자격 등)은 Phase 2에서 V+1 마이그레이션으로 추가한다.
- **D-05:** Member의 이름·전화번호는 온보딩(Phase 2)에서 입력되므로 nullable — 온보딩 완료 판정이 "프로필 입력 여부"이기 때문 (policies §5.1). 정확한 컬럼 구성은 플래너 재량이되, glossary.md 네이밍·conventions.md 타입 규칙(snake_case, `timestamptz`, `@Enumerated(STRING)` 대응 varchar)을 따른다.

### 에러 응답 설계 (FOUND-01)
- **D-06:** ProblemDetail 표준 필드 + **커스텀 `code` 필드**(문자열 enum, 예: `RESERVATION_FULL`). **FE 분기는 code로만 한다.** type URI는 형식만 갖춘 단순 값으로 (분기 키 아님).
- **D-07:** **에러코드 레지스트리를 `docs/`에 문서로 만들어 계약으로 관리한다** (예: `docs/error-codes.md`). 새 에러코드 추가 시 이 문서를 함께 갱신한다.
- **D-08:** 이 결정은 D-017(ProblemDetail 고정)의 구체화이므로 실행 시 `docs/decisions.md`에 새 D 번호로 기록한다.

### Branch 시드 · 브랜치/PR 운영
- **D-09:** Branch 시드는 **Flyway 시드 마이그레이션**으로 송파점 1건 삽입. 지점 관리 API는 스코프 밖. (Testcontainers가 마이그레이션을 재생하므로 테스트에서도 동일 시드가 보장된다)
- **D-10 (프로세스, 전 phase 공통):** 작업 브랜치는 **plan 단위** `feat/p1-xxx` 형식으로 dev에서 분기 → 완료·검증 후 **dev로 PR 생성** → 리뷰는 **Claude Code 코드리뷰**(/code-review)로 대체 → dev 머지 → 다음 작업. phase 완료 후 다음 phase도 같은 방식. main 직접 커밋 금지(기존 규칙 유지).

### Claude's Discretion
- Member/Admin/Branch/AdminBranch의 세부 컬럼 구성(D-05 제약 내에서), 인덱스·제약 설계
- 커스텀 gradle 태스크의 내부 구현 방식(기동 대기·종료 처리 등) — 단 "한 명령" 계약과 D-03 전제는 고정
- JPA 엔티티를 이번 phase에서 함께 만들지 여부 (ddl-auto=validate 정합만 지키면 됨)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 도메인·스펙 (우선순위 순 — CLAUDE.md 문서 우선순위 준수)
- `docs/policies.md` §5, §5.1 — 회원 상태 4종, 온보딩(프로필 입력 여부로 판정) → Member 스키마의 근거
- `docs/requirements.md` — FOUND-01/02/03 원문
- `docs/glossary.md` — Branch/Member/Admin/AdminBranch 네이밍, MemberStatus enum, 금지어
- `docs/decisions.md` — D-014(Boot 4), D-016(BigDecimal), D-017(ProblemDetail), D-018(패키지), D-024(ktlint)
- `docs/conventions.md` §1(패키지), §3(엔티티), §5(시간 타입), §8(에러 처리), §9(Flyway), §10(테스트 표), §11(Boot 4 주의)

### 프로젝트 실행 상태
- `.planning/REQUIREMENTS.md` — FOUND-01~03 정의와 traceability
- `.planning/ROADMAP.md` — Phase 1 성공 기준 3항목

### 프로젝트 스킬 (해당 작업 시 필수 절차)
- `.claude/skills/add-migration/SKILL.md` — 스키마 작업 절차
- `.claude/skills/add-endpoint/SKILL.md` — openapi.yaml 재생성 포함 API 절차
- `.claude/skills/add-domain-test/SKILL.md` — 테스트 골격
- `.claude/skills/verify-boot4-api/SKILL.md` — 낯선 API·의존성 검증 절차

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `config/OpenApiConfig.kt` — springdoc 3.0.3 메타데이터·서버 URL 상대경로 고정 완료. 커스텀 태스크는 이 설정 그대로 사용
- `config/SecurityConfig.kt` — permitAll 뼈대. Phase 1에서는 그대로 두고 Phase 2에서 교체 (roadmap Note)
- `src/test/.../TestcontainersConfiguration.kt` + `FlywayMigrationIntegrationTest` — 마이그레이션 검증 배선 완료. 새 마이그레이션은 `./gradlew test`로 자동 검증됨
- `V1__baseline.sql` — no-op baseline. 새 스키마는 V2부터

### Established Patterns
- Boot 4 의존성 세팅 완료 (webmvc·모듈별 test 스타터·tools.jackson·testcontainers-postgresql) — Boot 3 예제 이식 금지
- ktlint 1.8.0 고정, `.editorconfig` 단일 출처 — 작업 마지막 `ktlintFormat` → `build`

### Integration Points
- 전역 예외 핸들러는 `common/error/` 패키지에 (conventions §1, §8)
- 새 마이그레이션은 `src/main/resources/db/migration/V2__*.sql`부터 순번대로
- 생성 파이프라인 출력은 기존 `docs/api/openapi.yaml`을 덮어쓴다

</code_context>

<specifics>
## Specific Ideas

- 에러코드는 `RESERVATION_FULL` 같은 대문자 스네이크 문자열 enum — FE가 이 값으로만 분기한다
- "한 명령"은 gradle 태스크 하나로 — 결과가 곧 커밋 가능한 `docs/api/openapi.yaml`

</specifics>

<deferred>
## Deferred Ideas

- **CI에서의 openapi 스펙 검증** (생성 결과와 커밋본 불일치 감지) — 배포 단계(M7)에서 고려
- **지점 관리 API** (Branch CRUD) — v1 스코프 밖, 필요 시 이후 마일스톤

</deferred>

---

*Phase: 1-기반*
*Context gathered: 2026-07-30*
