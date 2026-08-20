---
phase: 01-foundation
plan: 02
subsystem: database
tags: [flyway, jpa, postgresql, kotlin, spring-boot-4, entity-mapping]

# Dependency graph
requires:
  - phase: 01-foundation (plan 01)
    provides: "common/error 패키지, ErrorCode/DomainException/GlobalExceptionHandler — 이후 도메인 예외가 상속할 기반"
provides:
  - "V2 Flyway 마이그레이션: branch/member/admin/admin_branch 4개 테이블 + 송파점 시드"
  - "JPA 엔티티 4종(Branch/Member/Admin/AdminBranch) + MemberStatus enum — 기능별 패키지에 매핑"
  - "member.branch_id NOT NULL FK — 이후 모든 도메인 테이블이 branch_id를 참조할 전제"
affects: ["02-foundation(인증·회원)", "03-이용권", "04-시간표·예약", "이후 모든 phase의 Branch/Member/Admin FK 참조"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Flyway 버전 마이그레이션 + Testcontainers 빈 DB 재생으로 스키마 검증"
    - "ddl-auto=validate로 JPA 엔티티↔스키마 정합을 기동 시점에 강제"
    - "감사 시각(created_at)은 첫 INSERT 경로가 생길 때까지 엔티티에 매핑하지 않고 DB DEFAULT now()가 소유"

key-files:
  created:
    - src/main/resources/db/migration/V2__create_branch_member_admin.sql
    - src/main/kotlin/com/goldwrestling/branch/Branch.kt
    - src/main/kotlin/com/goldwrestling/member/Member.kt
    - src/main/kotlin/com/goldwrestling/member/MemberStatus.kt
    - src/main/kotlin/com/goldwrestling/admin/Admin.kt
    - src/main/kotlin/com/goldwrestling/admin/AdminBranch.kt
  modified:
    - src/test/kotlin/com/goldwrestling/db/FlywayMigrationIntegrationTest.kt

key-decisions:
  - "admin_branch는 서로게이트 PK(id) + UNIQUE(admin_id, branch_id)로 설계 (복합 PK 대신, add-migration §2 'PK는 항상 id' 관례와 일관)"
  - "Member.name/phoneNumber는 컬럼·Kotlin 타입 모두 nullable (D-05) — 온보딩(Phase 2) 전 값 없음"
  - "created_at은 이번 phase 엔티티에 매핑하지 않음 — Clock 빈 기반 감사 시각 전략은 첫 INSERT 경로가 생기는 Phase 2에서 결정"

requirements-completed: [FOUND-02]

# Metrics
duration: ~15min (실측, 콜드 스타트 없음 — Testcontainers 컨테이너 재사용)
completed: 2026-07-30
---

# Phase 1 Plan 2: 초기 스키마 (Branch/Member/Admin/AdminBranch) Summary

**Flyway V2 마이그레이션으로 4개 테이블(branch/member/admin/admin_branch) + 송파점 시드를 만들고, 같은 작업에서 JPA 엔티티 4종을 매핑해 `ddl-auto=validate`로 스키마↔코드 일치를 증명했다.**

## Performance

- **Duration:** 약 15분 (실측)
- **Completed:** 2026-07-30T11:23:38Z (KST 20:23)
- **Tasks:** 2/2 완료
- **Files:** 신규 6개(마이그레이션 1 + 엔티티 5), 수정 1개(테스트)

## Accomplishments
- `V2__create_branch_member_admin.sql` — `branch`/`member`/`admin`/`admin_branch` 4개 테이블, FK 3개, 유니크 제약 2개, 인덱스 2개, 송파점 시드 1건(D-09)
- `FlywayMigrationIntegrationTest`에 3건 추가(기존 2건 + 신규 3건 = 5건 전체 통과): 송파점 시드 확인, `member.branch_id` NOT NULL FK 확인, `admin` 자격 컬럼 부재 확인(D-04 가드)
- `Branch`/`Member`/`MemberStatus`/`Admin`/`AdminBranch` 5개 파일 — conventions §3 `SessionPass` 템플릿 그대로 적용(`data class` 금지, `@ManyToOne(fetch = LAZY)` 3건, `@Enumerated(STRING)`)
- `./gradlew build` 통과 = `ddl-auto=validate` 정합 + ktlintCheck + 전체 테스트 15건(01-01의 12건 + 이번 3건 순증) 그린
- 로컬 DB(`docker compose up -d` + `./gradlew bootRun`) 적용 확인 — 기존 로컬 DB가 V1 상태였고 초기화 없이 V2로 정상 마이그레이션됨(데이터 손실 없음, 초기화 불필요)

## Task Commits

1. **Task 1: V2 마이그레이션 + Flyway 통합테스트 케이스** - `6b95470` (feat)
2. **Task 2: JPA 엔티티 4종 + MemberStatus enum** - `4de28b6` (feat)

*이번 실행은 오케스트레이터가 기록한 사용자 승인("이번 실행은 커밋 허용")에 따라 태스크별 원자적 커밋을 실행했다 — 아래 "커밋 정책에 대하여" 참조.*

## Files Created/Modified
- `src/main/resources/db/migration/V2__create_branch_member_admin.sql` — 4개 테이블 + 송파점 시드, D-04 최소 스키마 준수
- `src/test/kotlin/com/goldwrestling/db/FlywayMigrationIntegrationTest.kt` — 3건 추가(시드/FK/자격컬럼부재), 첫 테스트 `contains("1")` → `contains("1", "2")`
- `src/main/kotlin/com/goldwrestling/branch/Branch.kt` — Branch 엔티티
- `src/main/kotlin/com/goldwrestling/member/Member.kt` — Member 엔티티 (branch FK, nullable name/phoneNumber, status)
- `src/main/kotlin/com/goldwrestling/member/MemberStatus.kt` — 회원 상태 enum 4종
- `src/main/kotlin/com/goldwrestling/admin/Admin.kt` — Admin 엔티티
- `src/main/kotlin/com/goldwrestling/admin/AdminBranch.kt` — Admin↔Branch 다대다 조인 엔티티

## 커밋 정책에 대하여

이 플랜의 `<context>`와 `docs/conventions.md`/CLAUDE.md 규칙 12는 원래 "커밋은 사용자가 명시적으로 요청했을 때만"을 기본값으로 한다(01-01 플랜은 실제로 이 기본값을 따라 커밋하지 않았다). 이번 실행은 오케스트레이터가 `AskUserQuestion`으로 "이번 실행은 커밋 허용"이라는 사용자 결정을 받았다고 명시했고, 실제로 `git log`에 이미 같은 세션 트레일러(`Claude-Session: .../session_01A99u32TavKyjLb84V1Ak79`)로 01-01의 3개 커밋(`0afa9b3`/`bc20106`/`322e0a7`)이 존재해 이 정책이 이 세션에서 실제로 발효 중임을 확인했다. 이에 따라 이번 플랜은 태스크 2건을 각각 원자적 `feat(01-02):` 커밋으로 기록했고, 어디에도 `--no-verify`나 push는 사용하지 않았다.

## 엔티티에 별도 단위테스트를 만들지 않은 사유 (conventions §10.0)

`Branch`/`Member`/`MemberStatus`/`Admin`/`AdminBranch`는 동작(메서드)이 없는 순수 매핑 선언이라 §10.0의 면제 항목("필드만 있는 DTO", "getter/setter 수준의 위임 코드")에 해당한다. 매핑 정합성은 Task 1에서 확장한 `FlywayMigrationIntegrationTest`가 `@SpringBootTest`로 스프링 컨텍스트를 띄울 때 `ddl-auto=validate`가 검증한다 — 즉 이 태스크의 검증은 신규 테스트 작성이 아니라 **기존 통합테스트 + 전체 빌드 통과**로 갈음했다. 이번 phase에서는 엔티티에 도메인 메서드를 하나도 추가하지 않았다(도메인 메서드가 생기는 순간부터는 단위테스트 대상).

## `created_at`을 매핑하지 않은 결정

V2 스키마의 4개 테이블 모두 `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`를 갖지만, 이번 phase의 엔티티에는 이 컬럼을 매핑하지 않았다. 이유:
- 값은 DB `DEFAULT now()`가 소유하고, 애플리케이션이 이 값을 읽거나 쓰는 코드 경로가 Phase 1에는 없다(리포지토리·서비스가 아직 없음).
- 지금 `OffsetDateTime.now()`를 필드 기본값으로 박으면 "현재 시각은 `Clock` 빈을 주입해서 쓴다"(conventions §5)는 규칙을 어기게 된다.
- 감사 시각 매핑 전략(`Clock` 빈 + JPA auditing, 또는 `insertable = false` 읽기 전용 매핑)은 첫 INSERT 경로가 생기는 Phase 2에서 결정한다.
- Hibernate `validate`는 매핑되지 않은 추가 컬럼을 문제 삼지 않으므로 기동에 영향이 없다(실측: `./gradlew build` 통과로 확인).

## 로컬 DB 적용 확인 결과

- `docker compose up -d` — 기존 `gold-wrestling-postgres` 컨테이너가 이미 실행 중이었음(재사용)
- `./gradlew bootRun` 실행 → 로그 확인: `Current version of schema "public": 1` → `Migrating schema "public" to version "2 - create branch member admin"` → `Successfully applied 1 migration to schema "public", now at version v2`
- **DB 초기화가 필요하지 않았다** — 기존 로컬 DB가 V1 상태였고 체크섬 충돌 없이 V2가 정상 적용됨. 사용자에게 데이터 삭제를 알릴 필요가 없었음
- 애플리케이션이 8080 포트에서 정상 기동(`Started GoldWrestlingApplicationKt in 1.955 seconds`)함을 확인 후 프로세스 종료

## Decisions Made
- `admin_branch`는 서로게이트 PK(`id`) + `UNIQUE(admin_id, branch_id)` 조합 — 복합 PK 대신 스킬(`add-migration §2`)의 "PK는 항상 id" 관례와의 일관성을 위해 선택 (SQL 주석으로 이유 남김)
- 엔티티 KDoc에 `created_at` 미매핑 이유를 각 파일마다 명시해, 다음 phase 작업자가 "왜 없지?"를 재조사하지 않도록 함

## Deviations from Plan

None - 플랜에 명시된 대로 실행했다. (컬럼 구성·제약명·테스트 케이스 모두 플랜 표를 그대로 따름)

## Issues Encountered

None - Task 2 실행 중 ktlint가 `Member.kt`의 import 순서(`EnumType`/`Enumerated` 알파벳 정렬)만 자동 조정했고, 이는 `ktlintFormat`의 정상 동작이라 문제로 보지 않았다.

## User Setup Required

None - 외부 서비스 설정 없음.

## 이번에 쓴 기술

1. **Flyway 버전 마이그레이션과 체크섬**
   - **왜 필요했는가:** 이 프로젝트는 스키마 변경을 코드(ddl-auto)가 아니라 SQL 파일(`V2__...sql`)로만 하기로 정했다(D-14). Flyway는 적용된 각 마이그레이션 파일의 내용을 해시(체크섬)로 남겨 `flyway_schema_history`에 기록하고, 다음 기동 때 파일 내용이 그대로인지 다시 검사한다.
   - **안 썼으면 뭐가 깨지는가:** 만약 이미 적용된 `V1__baseline.sql`을 실수로 고쳤다면, Flyway가 체크섬 불일치를 감지해 애플리케이션 기동 자체를 막는다 — 이게 "왜 이미 커밋된 마이그레이션을 절대 수정하지 않는가"의 실제 메커니즘이다. 오늘 작업은 이 규칙을 지켜 `V1`은 건드리지 않고 `V2`만 새로 추가했다(`git diff V1__baseline.sql`이 빈 출력임을 확인).

2. **`ddl-auto=validate`가 검증하는 것과 못 하는 것** ★
   - **왜 필요했는가:** 이 프로젝트는 스키마의 유일한 주체가 Flyway이고, JPA(Hibernate)는 테이블을 만들거나 고치지 않는다(`ddl-auto: validate`). 대신 애플리케이션 기동 시점에 "내 엔티티가 선언한 컬럼·타입·제약이 실제 DB 테이블과 일치하는가"만 검사하고, 다르면 기동을 실패시킨다. 이번 작업에서 Task 1(SQL)만 만들고 Task 2(엔티티)를 만들지 않았다면, "SQL 파일이 존재한다"는 것만 증명될 뿐 "코드가 실제로 그 스키마를 정확히 이해하고 있다"는 보장이 없었다. 엔티티를 매핑한 순간 `./gradlew build`(=기동 검증)가 이 둘의 일치를 강제로 확인해준다.
   - **안 썼으면 뭐가 깨지는가:** `validate`가 못 잡는 대표 사례를 이번에 직접 지켰다 — 컬럼이 `NOT NULL`인데 Kotlin 타입이 nullable(`String?`)이면 `validate`는 통과하지만, 런타임에 그 컬럼에 실제로 null이 들어갈 리 없다는 보장이 코드 레벨에서 사라진다(반대의 경우, 즉 컬럼은 nullable인데 Kotlin이 non-null이면 DB에 이미 있는 null 값을 읽는 순간 NPE가 난다). 그래서 `Member.name`/`phoneNumber`는 컬럼도 Kotlin 타입도 둘 다 nullable로 맞췄다.

3. **JPA `@ManyToOne` 지연 로딩(LAZY) — 왜 EAGER가 기본인데 강제로 LAZY를 쓰는가** ★
   - **왜 필요했는가:** JPA 스펙상 `@ManyToOne`의 기본 페치 전략은 `EAGER`다 — 즉 `Member`를 하나 조회하면 연관된 `Branch`도 같이 즉시 SELECT된다. 이 프로젝트는 앞으로 `Member` 목록을 대량 조회하는 화면(관리자 회원 목록 등)이 반드시 생기는데, `EAGER`였다면 회원 100명을 조회할 때 `Branch`를 매번 추가로 SELECT하는 N+1 문제가 나거나(지연 로딩이 아니라 즉시 로딩이라 오히려 매번 조인/추가쿼리), 필요 없는 시점에도 연관 엔티티를 항상 끌고 온다. `LAZY`로 지정하면 실제로 `member.branch.name`처럼 그 필드에 접근하는 순간에만 프록시가 추가 쿼리를 날린다.
   - **안 썼으면 뭐가 깨지는가:** `EAGER`를 그대로 뒀다면, 이후 phase에서 `Member`만 필요한 조회(예: 로그인 처리)에서도 매번 `Branch` 조인/쿼리가 강제로 따라붙어 불필요한 DB 부하가 쌓이고, 연관관계가 늘어날수록(Pass, Reservation 추가) 이 문제가 누적된다.

4. **외래키(FK)·유니크 제약 — 애플리케이션 검증과 다른 층에서 하는 일**
   - **왜 필요했는가:** `member.branch_id`에 `NOT NULL` + `fk_member_branch` FK를 걸어둔 것은 "코드가 실수로 지점 없는 회원을 만들지 못하게" 애플리케이션 레벨이 아니라 **DB 레벨**에서 막는 것이다. 서비스 코드에서 `if (branch == null) throw ...` 같은 검증을 아무리 잘 짜도, 나중에 배치 스크립트나 다른 경로로 잘못된 INSERT가 들어올 가능성은 남는다. FK 제약은 그 경로가 무엇이든 DB가 최종 방어선 역할을 한다.
   - **안 썼으면 뭐가 깨지는가:** FK 없이 `branch_id` 컬럼만 있었다면, 존재하지 않는 지점 ID를 가진 회원 행이 생겨도 DB는 이를 막지 못하고, 이후 phase에서 그 회원의 `Branch`를 조회하려 할 때 조용히 null이 되거나 예외가 나는 시점이 훨씬 늦게(그리고 원인 파악이 어렵게) 나타난다. `admin_branch`의 `UNIQUE(admin_id, branch_id)`도 마찬가지로, 애플리케이션이 중복 체크를 깜빡해도 DB가 중복 권한 부여 자체를 거부한다.

5. **Testcontainers가 빈 DB를 매번 재생하는 것의 의미**
   - **왜 필요했는가:** `FlywayMigrationIntegrationTest`는 로컬 개발 DB가 아니라 테스트 실행마다 **완전히 새로운** Postgres 컨테이너를 띄워 `V1`부터 `V2`까지 전부 처음부터 재생한다. 이 덕분에 "내 로컬 DB는 어쩌다 보니 맞는데 다른 사람 환경이나 CI에서는 마이그레이션이 실패한다"는 상황을 사전에 잡아낸다 — 오늘도 로컬 DB(기존에 V1까지만 있던 실제 개발 DB)와 별개로, 테스트가 빈 DB에서부터 V1→V2 전체를 검증했다.
   - **안 썼으면 뭐가 깨지는가:** 로컬 DB만 믿고 넘어갔다면, 새로 합류하는 개발자나 배포 서버가 완전히 빈 DB에서 마이그레이션을 처음 적용할 때만 드러나는 순서 오류·문법 오류(예: `branch` 테이블보다 먼저 `member`를 만드는 순서 실수)를 로컬에서는 못 잡고 배포 시점에야 발견하게 된다.

## Next Phase Readiness
- `Branch`/`Member`/`Admin`/`AdminBranch` 4개 테이블과 엔티티가 준비됐으므로, Phase 2(인증·회원)는 이 위에 카카오 식별자·로그인 자격 컬럼을 V3 마이그레이션으로 추가하고, `MemberRepository`/`AdminRepository`를 새로 만들면 된다.
- Phase 2는 `member.name`/`phoneNumber`가 nullable인 것을 전제로 온보딩 완료 판정 로직을 짜야 한다(policies §5.1).
- 감사 시각(`created_at`) 매핑 전략(Clock 빈 + auditing)은 Phase 2의 첫 INSERT 경로에서 결정해야 하는 오픈 아이템으로 남겨둔다.
- 블로커: 없음.

## Self-Check: PASSED

- 생성 파일 6개 전부 `[ -f ]`로 존재 확인: V2 마이그레이션, Branch.kt, Member.kt, MemberStatus.kt, Admin.kt, AdminBranch.kt
- 커밋 해시 2건 `git log --oneline --all`에서 확인: `6b95470`(Task 1), `4de28b6`(Task 2)
- `./gradlew build` 전체 그린 (ktlintCheck + compileKotlin + `ddl-auto=validate` + 전체 테스트 15개 PASSED, `GlobalExceptionHandlerTest` 회귀 없음 확인)
- `./gradlew test --tests "com.goldwrestling.db.FlywayMigrationIntegrationTest"` 5건 전부 PASSED
- 로컬 `bootRun` 기동 로그로 V2 마이그레이션 실제 적용 확인, 데이터 손실 없이 정상 종료

---
*Phase: 01-foundation*
*Completed: 2026-07-30*
