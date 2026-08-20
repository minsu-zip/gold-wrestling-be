---
phase: 02-auth-member
plan: 02
subsystem: database
tags: [flyway, jpa, postgresql, kotlin, refresh-token, kakao-auth]

# Dependency graph
requires:
  - phase: 02-auth-member (plan 01)
    provides: docs/glossary.md 인증·회원 네이밍, ErrorCode 확장, decisions.md D-036~D-047
provides:
  - "V3 마이그레이션 — member.kakao_id/rejection_reason, admin.login_id/password_hash, refresh_token 테이블(주체 배타 CHECK)"
  - "Member.isOnboardingCompleted()/isRejected() 판정 메서드 — policies §5.1·§5.2 고정"
  - "RefreshToken 엔티티 + PrincipalType enum — 이후 로그인·토큰 발급 플랜의 계약"
  - "MemberRepository(+JpaSpecificationExecutor)/AdminRepository/BranchRepository/RefreshTokenRepository"
affects: [02-auth-member 이후 플랜 (02-03~02-11) — 카카오 로그인, 관리자 로그인, 토큰 발급·회전, 온보딩, 승인 API가 이 스키마·엔티티·리포지토리 위에서 동작]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "다형적 참조(회원 또는 관리자)는 nullable FK 쌍 + DB CHECK 제약으로 표현 (principal_type 문자열 대신)"
    - "원문 시크릿(refresh 토큰)은 저장하지 않고 해시만 저장"

key-files:
  created:
    - src/main/resources/db/migration/V3__add_auth_credentials_and_refresh_token.sql
    - src/main/kotlin/com/goldwrestling/auth/PrincipalType.kt
    - src/main/kotlin/com/goldwrestling/auth/RefreshToken.kt
    - src/main/kotlin/com/goldwrestling/auth/RefreshTokenRepository.kt
    - src/main/kotlin/com/goldwrestling/member/MemberRepository.kt
    - src/main/kotlin/com/goldwrestling/admin/AdminRepository.kt
    - src/main/kotlin/com/goldwrestling/branch/BranchRepository.kt
    - src/test/kotlin/com/goldwrestling/member/MemberOnboardingStatusTest.kt
    - src/test/kotlin/com/goldwrestling/auth/AuthRepositoryIntegrationTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/member/Member.kt
    - src/main/kotlin/com/goldwrestling/admin/Admin.kt
    - src/test/kotlin/com/goldwrestling/db/FlywayMigrationIntegrationTest.kt

key-decisions:
  - "새 결정 없음 — 이 플랜은 02-01이 이미 기록한 D-036·D-037·D-039·D-041·D-045를 스키마·코드로 구현만 함"

patterns-established:
  - "JPA @Entity 클래스는 allOpen 플러그인으로 open 컴파일되므로, null 체크 후 프로퍼티 접근 시 로컬 val에 담아야 스마트캐스트가 동작한다 (RefreshToken.principalId())"

requirements-completed: [AUTH-01, AUTH-03, AUTH-06, MEMBER-01]

# Metrics
duration: ~35min
completed: 2026-08-02
---

# Phase 2 Plan 2: 인증 스키마 확정 + 엔티티·리포지토리 Summary

**V3 마이그레이션(kakao_id/login_id/refresh_token 주체 배타 CHECK) + Member/Admin 확장 + RefreshToken 신규 엔티티 + 리포지토리 4종, 통합·단위테스트 20건 전부 통과**

## Performance

- **Duration:** 약 35분
- **Completed:** 2026-08-02
- **Tasks:** 3/3 완료
- **Files modified:** 3개 수정 + 9개 신규

## Accomplishments

- `V3__add_auth_credentials_and_refresh_token.sql` 신규 — `member`에 `kakao_id`(UNIQUE)·`rejection_reason` 추가, `admin`에 `login_id`(UNIQUE)·`password_hash` 추가, `refresh_token` 신규 테이블(주체 배타 `ck_refresh_token_principal` CHECK, `token_hash` UNIQUE). V1·V2는 무변경(`git diff` 확인)
- `Member`·`Admin` 엔티티에 D-039가 정한 `Clock` 기반 `createdAt` 필드 추가, `Member.isOnboardingCompleted()`/`isRejected()` 판정 메서드로 policies §5.1·§5.2 규칙을 코드 한 곳에 고정
- `PrincipalType`(MEMBER/ADMIN) enum, `RefreshToken` 엔티티(원문 미저장·주체 배타 방어·회전 준비) 신규
- `MemberRepository`(+`JpaSpecificationExecutor`, 이후 검색 플랜을 위해 미리 상속)·`AdminRepository`·`BranchRepository`·`RefreshTokenRepository` 4종 신규
- `MemberOnboardingStatusTest`(단위 6건)·`AuthRepositoryIntegrationTest`(Testcontainers 7건)·`FlywayMigrationIntegrationTest` 확장(기존 5건 + 신규 3건, 기존 "로그인 컬럼 없음" 테스트는 "있음"으로 반전)까지 전부 통과 — `./gradlew build` BUILD SUCCESSFUL (37개 테스트 전체 통과)

## Task Commits

1. **Task 1: V3 마이그레이션 — 인증 컬럼 + refresh_token 테이블** - `b0aec91` (feat)
2. **Task 2: 엔티티 확장·신규 + 리포지토리 4종** - `8d5ae99` (feat)
3. **Task 3: 스키마·매핑·판정 로직 테스트 3종** - `472230e` (test)

**Plan metadata:** 이 커밋 이후 별도 docs 커밋으로 기록 예정 (final_commit 단계)

_참고: Task 3는 tdd="true"로 지정되어 있었으나, Task 1·2가 먼저 스키마·엔티티를 확정한 뒤 Task 3가 그 위에 검증 테스트를 얹는 순서라 RED(실패 테스트 우선)→GREEN 순서로 진행되지 않았다. 아래 "TDD Gate Compliance" 참조._

## Files Created/Modified

- `src/main/resources/db/migration/V3__add_auth_credentials_and_refresh_token.sql` — 인증 컬럼 + refresh_token 테이블 (신규)
- `src/main/kotlin/com/goldwrestling/member/Member.kt` — kakaoId·rejectionReason·createdAt 확장, 판정 메서드 2종 (수정)
- `src/main/kotlin/com/goldwrestling/member/MemberRepository.kt` — kakaoId 조회 + Specification 지원 (신규)
- `src/main/kotlin/com/goldwrestling/admin/Admin.kt` — loginId·passwordHash·createdAt 확장 (수정)
- `src/main/kotlin/com/goldwrestling/admin/AdminRepository.kt` — loginId 조회·존재 확인 (신규)
- `src/main/kotlin/com/goldwrestling/branch/BranchRepository.kt` — name 조회 (신규, D-047 지점 배정에서 사용 예정)
- `src/main/kotlin/com/goldwrestling/auth/PrincipalType.kt` — 토큰 주체 종류 enum (신규)
- `src/main/kotlin/com/goldwrestling/auth/RefreshToken.kt` — refresh 토큰 엔티티 (신규)
- `src/main/kotlin/com/goldwrestling/auth/RefreshTokenRepository.kt` — 해시 조회·주체별 미폐기 목록 (신규)
- `src/test/kotlin/com/goldwrestling/member/MemberOnboardingStatusTest.kt` — 온보딩·거절 판정 단위테스트 6건 (신규)
- `src/test/kotlin/com/goldwrestling/auth/AuthRepositoryIntegrationTest.kt` — 제약 위반 검증 통합테스트 7건 (신규)
- `src/test/kotlin/com/goldwrestling/db/FlywayMigrationIntegrationTest.kt` — V3 적용·제약 검증 테스트 3건 추가 + 1건 반전 (수정)

## Decisions Made

새 설계 결정 없음. 02-01이 이미 기록한 D-036(refresh 저장 방식)·D-037(nullable FK 쌍 + CHECK)·D-039(Clock 기반 감사 시각)·D-041(전화번호 UNIQUE 없음)·D-045(DelegatingPasswordEncoder)를 스키마·엔티티로 그대로 구현했다.

실행 중 내린 소소한 판단:

- `Member`/`Admin` 생성자에서 `kakaoId`/`loginId`처럼 기본값 없는 파라미터를 기본값 있는 `status` 뒤에 추가했다 — 플랜이 "기존 파라미터 순서를 유지하고 뒤에 붙인다"고 명시했고, Kotlin은 이 순서를 허용한다(호출부는 이미 named argument를 쓰고 있어 영향 없음). 컴파일로 확인.
- `RefreshToken.principalId()`에서 `member != null` 체크만으로는 스마트캐스트가 되지 않아(엔티티가 allOpen 플러그인으로 open 컴파일되기 때문) 로컬 `val`에 담아 우회했다 — 아래 "이번에 쓴 기술" 참조.

## Deviations from Plan

None - plan executed exactly as written. (마이너 컴파일 이슈 1건은 Rule 1로 즉시 수정 — 아래 "Issues Encountered" 참조)

## TDD Gate Compliance

Task 3는 `tdd="true"`로 지정됐지만, 이 플랜의 태스크 순서 자체가 "스키마·엔티티 확정(Task 1·2) → 검증 테스트(Task 3)"로 설계되어 있어 RED(실패 테스트)가 GREEN(구현) 이전에 오는 고전적 TDD 순서를 따르지 않았다. Task 3에서 작성한 테스트는 처음부터 통과했다(구현이 이미 존재하므로 fail-fast 규칙의 "테스트가 예상과 달리 통과하면 이미 구현이 존재하는 것" 케이스에 해당하며, 이는 플랜 설계상 의도된 것이지 실수가 아니다). CLAUDE.md가 GSD 기본 TDD 방침을 프로젝트 규칙(rule 10 + conventions §10.0)으로 덮어쓴다고 명시하므로, "프로덕션 코드와 테스트를 같은 작업 단위 안에서 함께 작성"하는 이 순서는 프로젝트 규칙에 부합한다. 다만 GSD의 test(RED)→feat(GREEN) 커밋 게이트 관점에서는 순서가 반대(feat→feat→test)임을 투명하게 기록한다.

## Issues Encountered

- Task 2 컴파일 중 `RefreshToken.principalId()`에서 "Smart cast to 'Member' is impossible, because 'member' is a property that has an open or custom getter" 컴파일 에러 발생. 원인: `build.gradle.kts`의 `allOpen` 플러그인이 `@Entity` 클래스를 open으로 컴파일해, `member: Member?` 프로퍼티도 open이 되어 null 체크만으로는 컴파일러가 안전을 보장하지 못함. 로컬 `val memberRef = member` / `val adminRef = admin`에 담아 스마트캐스트가 되게 수정(Rule 1 - 블로킹 컴파일 에러 즉시 수정, Task 2 커밋에 포함).
- 로컬 docker-compose Postgres(`gold-wrestling-postgres` 컨테이너)가 이미 떠 있는 상태를 확인했으나, 이 실행이 병렬 워크트리 세션(Phase 2의 다른 플랜이 동시에 실행 중일 수 있음)이라 `docker compose up -d && ./gradlew bootRun`으로 공유 로컬 DB에 실제 적용하는 검증(플랜 `<verification>` 2번)은 의도적으로 건너뛰었다 — 병렬 실행 중인 다른 워크트리 에이전트의 검증과 충돌할 위험이 있다. 대신 Testcontainers 통합테스트(격리된 컨테이너)가 V1→V3 전체 재생 + `ddl-auto=validate` 통과를 이미 증명한다(동일한 검증 목적을 격리된 환경에서 달성). 사용자가 이 워크트리 병합 후 로컬 DB에 실제 적용해 확인하는 것을 권장한다.

## User Setup Required

None - 외부 서비스 설정 불필요. 단, 로컬 개발 DB에 이미 회원/관리자 데이터를 수동으로 넣어둔 적이 있다면 V3의 `kakao_id`/`login_id` NOT NULL 컬럼 추가가 실패할 수 있다 — 그 경우 `docker compose down -v && docker compose up -d`로 초기화 필요(데이터 삭제, 확인 후 진행할 것).

## Next Phase Readiness

- 이후 플랜(02-03~02-11)이 카카오 로그인·관리자 로그인·토큰 발급/회전/폐기·온보딩·승인 API를 작성할 때 쓸 스키마(V3)·엔티티(Member/Admin/RefreshToken)·리포지토리 4종이 전부 준비됨
- `MemberRepository`가 `JpaSpecificationExecutor`를 이미 상속하고 있어 02-09(회원 검색)가 이 파일을 다시 건드리지 않아도 됨
- 커밋 정책: 이번 실행 세션은 오케스트레이터가 사용자 승인을 받아 태스크 단위 자동 커밋을 허용했다 — 위 3개 커밋 모두 완료 상태이며 push는 하지 않았다

---
*Phase: 02-auth-member*
*Completed: 2026-08-02*

## 이번에 쓴 기술

1. **DB CHECK 제약 vs 애플리케이션 검증의 역할 분담**
   - **왜 필요했는가:** refresh_token 한 행은 회원 또는 관리자 중 정확히 하나만 가리켜야 한다(D-037). 서비스 코드가 항상 하나만 채워서 저장하도록 짜더라도, 코드 경로가 늘어나면(배치, 관리자 도구, 향후 리팩터링) 실수로 둘 다 채우거나 둘 다 비운 채 저장하는 경로가 생길 수 있다.
   - **안 썼으면 뭐가 깨지는가:** 애플리케이션 코드만 믿으면, 실수로 잘못 저장된 행 하나가 "이 토큰은 회원 것인가 관리자 것인가"를 코드에서 잘못 판단하게 만들어 다른 사람의 세션으로 오인증되는 심각한 사고로 이어질 수 있다. `ck_refresh_token_principal` CHECK는 그런 행이 DB에 아예 존재할 수 없게 만든다 — 마지막 방어선.

2. **nullable FK 쌍으로 "둘 중 하나"(다형적 참조) 표현 ★**
   - **왜 필요했는가:** Member와 Admin이 별도 테이블인데, refresh_token은 이 둘 중 하나를 가리켜야 한다. 단일 FK 컬럼으로는 서로 다른 두 테이블을 가리킬 수 없다.
   - **안 썼으면 뭐가 깨지는가:** 대안인 `principal_type` 문자열 + `principal_id` 정수 조합을 쓰면, DB가 그 `principal_id`가 실제로 존재하는 회원/관리자인지 검증해줄 방법이 없다(FK가 아니므로). 회원이 탈퇴해도 그 회원을 가리키던 토큰 행은 고아 상태로 남고, 아무도 그걸 막아주지 않는다.

3. **`@ManyToOne(fetch = FetchType.LAZY)`를 반드시 명시하는 이유**
   - **왜 필요했는가:** `RefreshToken.member`·`RefreshToken.admin`은 토큰 검증 시 대부분 "이 토큰이 어느 회원/관리자 것인지"만 알면 되고, 그 회원의 다른 정보(이름, 이용권 등)까지 항상 즉시 읽어올 필요는 없다.
   - **안 썼으면 뭐가 깨지는가:** JPA 기본값은 EAGER라서, refresh_token 하나를 조회할 때마다 연결된 Member/Admin 전체를 즉시 함께 조회하는 쿼리가 나간다. 토큰 검증처럼 자주 실행되는 경로에서 불필요한 조인이 매번 따라붙어 느려진다.

4. **Flyway 체크섬과 "커밋된 마이그레이션 수정 금지"의 관계**
   - **왜 필요했는가:** V1·V2는 이미 Phase 1에서 적용·커밋된 파일이다. 이번 작업에서 인증 컬럼이 필요하다고 V2 파일을 직접 고치고 싶은 유혹이 있었다.
   - **안 썼으면 뭐가 깨지는가:** Flyway는 각 마이그레이션 파일의 내용을 체크섬으로 기록해 두고, 다음 기동 때 파일 내용이 그 체크섬과 다르면 "이미 적용된 마이그레이션이 변경됐다"고 보고 기동 자체를 막는다. 이미 그 파일을 적용해 둔 다른 환경(팀원 로컬, 운영 서버)이 있다면 그 환경들이 전부 기동 실패에 빠진다. 그래서 고칠 내용은 V3라는 새 파일로 추가했다.

5. **JPA 엔티티가 컴파일 타임에 `open` 클래스가 되는 이유와 스마트캐스트 제약 ★**
   - **왜 필요했는가:** Hibernate는 지연 로딩(위 3번)을 프록시 객체로 구현하는데, 프록시는 원본 클래스를 상속해서 만든다. Kotlin 클래스는 기본이 `final`이라 상속이 안 되므로, 이 프로젝트는 `allOpen` 플러그인으로 `@Entity` 클래스를 자동으로 `open`(상속 가능)으로 컴파일한다.
   - **안 썼으면 뭐가 깨지는가:** `allOpen`이 없으면 Hibernate가 지연 로딩 프록시를 만들지 못해 `fetch = LAZY`가 조용히 무시되거나 예외가 난다. 반대급부로, `RefreshToken.principalId()`에서 `if (member != null) member.id`처럼 쓰면 컴파일러가 "member는 open 프로퍼티라 이 사이에 서브클래스가 값을 바꿨을 수도 있다"고 보고 스마트캐스트를 거부한다. `val memberRef = member`처럼 로컬 변수(항상 final)에 담아서 우회했다.

**일부러 쓰지 않은 것:** `@EnableJpaAuditing`/`@CreatedDate` — `createdAt`을 자동으로 채워주는 표준 JPA 기능이지만, 이 프로젝트는 이미 `Clock` 빈 주입 규약이 있고(테스트 시각 고정이 쉬움), Auditing을 쓰려면 `DateTimeProvider` 빈을 추가로 배선해야 해서 오히려 시각 고정 경로가 하나 늘어난다(D-039).

## Self-Check: PASSED

- FOUND: src/main/resources/db/migration/V3__add_auth_credentials_and_refresh_token.sql
- FOUND: src/main/kotlin/com/goldwrestling/auth/PrincipalType.kt
- FOUND: src/main/kotlin/com/goldwrestling/auth/RefreshToken.kt
- FOUND: src/main/kotlin/com/goldwrestling/auth/RefreshTokenRepository.kt
- FOUND: src/main/kotlin/com/goldwrestling/member/MemberRepository.kt
- FOUND: src/main/kotlin/com/goldwrestling/admin/AdminRepository.kt
- FOUND: src/main/kotlin/com/goldwrestling/branch/BranchRepository.kt
- FOUND: src/test/kotlin/com/goldwrestling/member/MemberOnboardingStatusTest.kt
- FOUND: src/test/kotlin/com/goldwrestling/auth/AuthRepositoryIntegrationTest.kt
- 커밋 `b0aec91`, `8d5ae99`, `472230e` 전부 `git log --oneline --all`에서 확인됨
- `./gradlew build` BUILD SUCCESSFUL (37개 테스트 전체 통과, Testcontainers 포함)
