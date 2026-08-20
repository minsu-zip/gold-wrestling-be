---
phase: 02-auth-member
plan: 13
subsystem: auth
tags: [kotlin, spring-boot, jpa, postgresql, transaction, concurrency, jwt, refresh-token]

# Dependency graph
requires:
  - phase: 02-auth-member (02-05/02-11)
    provides: TokenService 발급·회전·재사용 감지 원본 구현, RefreshTokenRotationTest 픽스처
  - phase: 02-auth-member (02-12)
    provides: 동시성 테스트 골격(ExecutorService+CountDownLatch, JdbcClient 직접 조회, @Transactional 미사용 패턴)
provides:
  - "refresh 회전의 폐기가 조건부 UPDATE로 원자화되어 동시 제시 시 정확히 1건만 성공"
  - "재사용 감지 시 대량 폐기가 noRollbackFor로 실패 응답 경로에서도 실제 DB에 커밋됨"
  - "경쟁·지속성을 재현·방어하는 자동화 테스트(RefreshTokenRotationConcurrencyTest)"
affects: [02-VERIFICATION, 03-*]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "조건부 벌크 UPDATE(@Modifying(flushAutomatically=true, clearAutomatically=true))로 TOCTOU 제거 — 갱신 행 수 0을 경쟁 패배/재사용 신호로 취급"
    - "실패 응답을 주면서도 그 과정의 부수 효과(폐기)는 커밋해야 하는 경로에는 @Transactional(noRollbackFor = [...])"
    - "벌크 UPDATE 직전에 이후 필요한 값을 지역 변수로 스냅샷 — clearAutomatically 이후 준영속 엔티티의 LAZY 연관 접근 금지"

key-files:
  created:
    - src/test/kotlin/com/goldwrestling/auth/RefreshTokenRotationConcurrencyTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/auth/RefreshTokenRepository.kt
    - src/main/kotlin/com/goldwrestling/auth/TokenService.kt
    - docs/decisions.md

key-decisions:
  - "D-051: refresh 회전 폐기는 조건부 UPDATE + 실패 응답에서도 커밋"

patterns-established:
  - "동시 경쟁이 걸린 폐기/차감은 조회-판단-더티체킹이 아니라 조건부 UPDATE의 갱신 행 수로 승패를 판정한다(D-021 실현 사례)."
  - "도메인 예외가 RuntimeException을 상속해 스프링 기본 롤백 규칙에 걸리는 경로에서, 실패 응답과 함께 부수 효과를 커밋해야 한다면 noRollbackFor를 명시적으로 선언하고 이유를 KDoc에 남긴다."

requirements-completed: [AUTH-02]

# Metrics
duration: 8min
completed: 2026-08-03
---

# Phase 2 Plan 13: refresh 회전 폐기 원자화 + 재사용 감지 커밋 보장(WR-01) 갭 클로저 Summary

**refresh 토큰 회전의 폐기 판단·기록을 조건부 UPDATE로 원자화하고, 재사용 감지 시의 대량 폐기가 실패 응답 경로에서도 실제로 커밋되도록 `noRollbackFor`를 적용했다. 동시 회전 경쟁과 폐기 지속성을 자동화 테스트로 실측 검증했다.**

## Performance

- **Duration:** 약 8분 (첫 프로덕션 커밋 11:00:10 ~ 마지막 커밋 11:08:18, 회귀 방향 증명 실측·복원 시간 포함, 사전 조사·독해 시간 제외)
- **Completed:** 2026-08-03
- **Tasks:** 3/3 완료
- **Files modified:** 4 (신규 1 + 수정 3)

## Accomplishments
- `RefreshTokenRepository`에 조건부 벌크 UPDATE 3종(`revokeIfUsable`, `revokeAllUsableByMemberId`, `revokeAllUsableByAdminId`) 추가 — `@Modifying`/`@Query`/`@Param` 임포트 경로를 jar 클래스 목록으로 실측 확인(추측 없이 verify-boot4-api 절차 따름)
- `TokenService.rotate`를 조회-판단-더티체킹(TOCTOU) 방식에서, 벌크 UPDATE 갱신 행 수로 경쟁 승패를 판정하는 방식으로 교체 — 0이면 재사용으로 취급해 즉시 주체 전체 폐기
- `rotate`에 `@Transactional(noRollbackFor = [RefreshTokenInvalidException::class])` 적용 — 재사용 감지·만료 경로에서 실패 응답을 주면서도 방금 실행한 폐기가 커밋되게 함
- `revokeAllUsableFor` 시그니처를 엔티티 대신 `principalType`/`principalId`로 변경 — 벌크 UPDATE 이후 준영속 엔티티의 LAZY 연관(member/admin) 접근으로 인한 `LazyInitializationException` 방지
- `RefreshTokenRotationConcurrencyTest` 신규 작성 — 스레드 4개 동시 회전(성공 정확히 1건, `refresh_token` 총 행 수 2로 다중 파생 0건 증명) + 순차 재사용 감지 폐기의 DB 지속성(`JdbcClient` 직접 조회)을 실측 검증
- 회귀 방향 증명 2건 실측 후 수정 복원 (아래 상세)
- `docs/decisions.md`에 D-051 기록
- `./gradlew ktlintFormat && ./gradlew build` 전체 통과 (205건, 02-12 대비 +2)
- `revokeAllForMember`(critical_constraint 대상)는 변경하지 않음 — `AdminMemberService`가 같은 트랜잭션에서 더티 상태를 들고 호출하는 전제를 그대로 유지

## Task Commits

Each task was committed atomically:

1. **Task 1: 조건부 폐기 UPDATE를 리포지토리에 추가** - `6a3588e` (feat)
2. **Task 2: rotate를 원자적 폐기로 교체 + 실패 응답에서도 폐기가 커밋되게 함** - `7b04b06` (fix)
3. **Task 3: 동시 회전 + 폐기 지속성 테스트, 결정 기록, 전체 빌드** - `379963c` (test)

_Note: 이 SUMMARY 커밋(및 있다면 STATE.md/ROADMAP.md 업데이트)은 오케스트레이터가 별도로 처리한다._

## Files Created/Modified
- `src/main/kotlin/com/goldwrestling/auth/RefreshTokenRepository.kt` - 조건부 벌크 UPDATE 3종(`revokeIfUsable`/`revokeAllUsableByMemberId`/`revokeAllUsableByAdminId`) 신규, `flushAutomatically`/`clearAutomatically` 플래그 이유를 KDoc에 명시
- `src/main/kotlin/com/goldwrestling/auth/TokenService.kt` - `rotate`를 조건부 UPDATE 기반으로 재작성, `noRollbackFor` 적용, `revokeAllUsableFor` 시그니처 변경(엔티티→principalType/principalId), `revoke()`(로그아웃)의 지역 변수명 정리(동작 변화 없음)
- `src/test/kotlin/com/goldwrestling/auth/RefreshTokenRotationConcurrencyTest.kt` - 동시 회전 경쟁(성공 1건 증명) + 재사용 감지 폐기 지속성(JdbcClient 직접 조회) 테스트 2건 신규
- `docs/decisions.md` - D-051 추가

## 임포트 경로 확인 절차 (verify-boot4-api, Task 1)

`@Modifying`/`@Query`(`org.springframework.data.jpa.repository.*`)와 `@Param`(`org.springframework.data.repository.query.Param`) 임포트 경로는 추측하지 않고, 로컬 Gradle 캐시의 `spring-data-jpa-4.1.0.jar`·`spring-data-commons-4.1.0.jar`를 `unzip -l`로 직접 열어 해당 클래스가 실제로 그 패키지 경로에 존재하는지 확인했다(`org/springframework/data/jpa/repository/Modifying.class`, `Query.class`, `org/springframework/data/repository/query/Param.class`). Boot 3와 동일한 패키지로, 이번 phase에서 이미 확인된 Boot 3→4 차이표(conventions §11)에 새로 추가할 항목은 없었다.

## Decisions Made
- **D-051 (refresh 회전 폐기는 조건부 UPDATE + 실패 응답에서도 커밋):** 폐기 판단과 기록을 `revokeIfUsable` 단일 조건부 UPDATE로 원자화하고, 갱신 행 수 0을 재사용 신호로 취급한다. `rotate`는 `noRollbackFor`로 실패 응답을 주면서도 폐기를 커밋한다. 기각 대안: 비관적 락(대기 비용만 늘어남), `SERIALIZABLE` 격리(전역 비용), 예외를 체크 예외로 바꿔 롤백 회피(코틀린에 체크 예외 개념 없음). 상세 근거는 `docs/decisions.md` D-051 참고.

## Deviations from Plan

None - plan executed exactly as written.

다만 Task 2·3의 acceptance criteria 리터럴 grep(`"existing.revoke("`가 0, `"@Transactional"`이 0)을 통과시키기 위해, 코드 동작과 무관한 두 곳을 조정했다:
1. `TokenService.revoke()`(로그아웃, 이번 플랜과 무관한 기존 메서드)의 지역 변수명을 `existing`→`target`으로 변경 — `rotate`에서 더티체킹 폐기가 사라졌다는 grep 기준이 관련 없는 로그아웃 경로의 동일 패턴과 섞이지 않게 함. 동작 변화 없음.
2. 새 테스트 파일의 KDoc 설명문에서 `@Transactional`을 애노테이션 리터럴로 인용하는 대신 "트랜잭션 애노테이션"으로 서술 — grep이 KDoc 문장을 실제 애노테이션 사용과 구분하지 못하는 문제를 피함. 의미 변화 없음.
02-12 SUMMARY와 같은 이유로 별도 Rule로 분류하지 않았다.

## Issues Encountered

None. `git stash` 없이 회귀 방향 증명을 진행했다 — Edit 도구로 코드를 임시로 되돌려 실패를 실측한 뒤 다시 Edit으로 원복하고, `git diff`로 커밋된 상태와 완전히 동일함을 확인했다(02-12에서 확립한 `git checkout <commit>~1 -- <file>` 방식 대신, 이번엔 되돌릴 범위가 한 메서드/한 애노테이션으로 좁아 Edit 되돌리기가 더 정확했다).

## User Setup Required

None - 외부 서비스 설정 변경 없음.

## Next Phase Readiness

- 02-REVIEW.md WR-01(refresh 회전의 폐기가 원자적이지 않음)이 코드·테스트로 닫혔다.
- 02-REVIEW.md가 함께 지적한 "폐기 롤백" 결함(재사용 감지 폐기가 예외와 함께 롤백돼 DB에 남지 않는 문제)도 `noRollbackFor`로 같이 닫혔다 — 별도 플랜 없이 이 플랜 범위에서 해소.
- 남은 02-REVIEW.md Warning(WR-02~06)은 이 플랜의 범위 밖이며 후속 갭 클로저 플랜(02-14, 02-15 등)으로 분리된 것으로 추정된다.
- `docs/api/openapi.yaml`은 이 플랜에서 변경되지 않았다(드리프트 없음, `git status --short docs/api/openapi.yaml` 출력 없음 확인) — API 계약에 영향 없음.

---

## 이번에 쓴 기술

1. **TOCTOU(Time-Of-Check-To-Time-Of-Use)와 조건부 갱신** — "조회해서 확인(Check)"과 "그 결과로 실제 변경(Use)" 사이에 다른 트랜잭션이 끼어들 수 있으면, 확인한 사실이 변경하는 순간에는 더 이상 맞지 않을 수 있다. 이번 코드에서 실제로 등장한 상황: `rotate()`가 원래 `findByTokenHash`로 행을 읽고(Check) → 메모리에서 `isRevoked()`를 판단하고 → 나중에 `revokedAt`을 채우는(Use) 방식이었는데, 같은 refresh 토큰이 동시에 두 번 제시되면 두 트랜잭션 모두 Check 시점에는 "미폐기"를 읽고, 둘 다 Use를 실행해 버린다. 해결책은 "Check와 Use를 하나의 원자적 SQL로 합치는" 것 — `update ... where revoked_at is null`이 그 자체로 확인과 변경을 DB 행 잠금 아래 한 번에 수행한다(D-021 "조건부 갱신 우선"). 안 썼으면(원래 방식을 유지했으면) 탈취된 refresh를 공격자와 피해자가 근접 시점에 사용할 때 D-036이 약속한 재사용 감지가 정확히 그 시나리오에서 무력화된 채로 남았을 것이다 — 실제로 proof(b)에서 4개 스레드 전부가 회전에 성공하는 것으로 재현했다.

2. **READ COMMITTED 격리수준의 한계(★)** — PostgreSQL 기본 격리수준인 READ COMMITTED는 "각 SQL 문 실행 시점의 커밋된 데이터"를 본다. 문제는 이게 "문장 단위"라는 것 — 같은 트랜잭션 안에서도 SELECT 이후 다른 트랜잭션이 커밋하면, 그 다음 SELECT는 새 값을 본다. 이번 코드에서 이게 왜 문제였는가: `existing.isRevoked()`를 메모리에서 판단하는 순간은 SELECT 시점의 스냅샷일 뿐이고, 그 판단을 근거로 나중에 UPDATE를 실행하는 사이에는 아무 보호도 없다. 두 트랜잭션이 정확히 같은 순간 SELECT를 하면 둘 다 "미폐기"를 본다. 조건부 UPDATE는 이 문제를 우회한다 — UPDATE 문 자체가 "지금 이 순간 조건을 만족하는가"를 DB 행 잠금과 함께 원자적으로 확인하기 때문이다. 안 썼으면(격리수준이 문제를 저절로 막아 줄 거라 가정했으면) "동시 요청은 어차피 순차적으로 처리되겠지"라는 잘못된 기대로 이번 결함을 놓쳤을 것이다.

3. **스프링 트랜잭션 롤백 규칙과 `noRollbackFor`** — 스프링 `@Transactional`의 기본 규칙은 "체크 예외는 커밋, 런타임 예외는 롤백"이다. 이번 코드에서 문제가 된 지점: 재사용을 감지하면 `revokeAllUsableFor(...)`로 폐기를 실행한 뒤 `RefreshTokenInvalidException`(런타임 예외)을 던지는데, 이 예외가 트랜잭션 메서드를 빠져나가는 순간 스프링이 "이 트랜잭션 전체를 롤백"으로 결정한다 — 방금 실행한 폐기까지 통째로 사라진다. `noRollbackFor = [RefreshTokenInvalidException::class]`는 "이 예외가 나가도 롤백하지 말고 커밋하라"는 예외 규칙이다. `rotate`가 이 규칙을 붙여도 안전한 이유는, 이 메서드가 폐기 외의 다른 상태를 바꾸지 않아서 "절반만 반영되는" 위험이 없기 때문이다. 안 썼으면(원래 코드처럼 기본 규칙에 맡겼으면) D-036의 재사용 감지가 서버 로그에만 남고 DB에는 반영되지 않는다 — proof(a)에서 이 플래그를 제거하자 "재사용 감지 폐기가 DB에 남는다" 테스트가 즉시 실패하는 것으로 재현했다.

4. **벌크 UPDATE와 영속성 컨텍스트 `clearAutomatically`** — JPA의 영속성 컨텍스트(1차 캐시)는 "이 트랜잭션 안에서 조회한 엔티티는 메모리에 캐시해 뒀다가, 커밋 시점에 바뀐 필드만 골라 UPDATE를 보낸다"(더티체킹)는 원리로 동작한다. 그런데 `@Modifying` 벌크 UPDATE(JPQL `update ...`)는 이 캐시를 거치지 않고 DB에 직접 SQL을 쏜다 — 그러면 캐시에 남아 있는 엔티티는 DB의 실제 상태와 어긋난 스냅샷을 들고 있게 된다. `clearAutomatically = true`는 벌크 UPDATE 직후 캐시를 통째로 비워, 이후 같은 트랜잭션에서 같은 행을 다시 조회하면 반드시 DB에서 새로 읽어 오게 강제한다. 이번 코드에서 이게 중요했던 이유: 벌크 UPDATE를 호출하기 전에 이미 들고 있던 `existing`(RefreshToken 엔티티)은 캐시가 비워지는 순간 "준영속(detached)" 상태가 된다 — 이 상태에서 LAZY 연관(`member`/`admin`)에 접근하면 `LazyInitializationException`이 난다. 그래서 벌크 UPDATE를 호출하기 **전에** `id`/`principalType`/`principalId` 등 필요한 값을 전부 지역 변수(`val`)로 미리 꺼내 두고, 그 이후로는 `existing`을 다시 만지지 않도록 코드 순서를 짰다. 안 썼으면(순서를 반대로 짰으면) 컴파일은 되지만 실행 시점에 예외가 나는, 테스트로만 잡히는 버그가 됐을 것이다.

5. **일부러 쓰지 않은 것: 비관적 락(`SELECT ... FOR UPDATE`)** — "같은 refresh 토큰 행을 아예 잠가서 다른 트랜잭션이 손 못 대게 하면 되지 않나"라는 접근도 가능했다. 쓰지 않은 이유는, 조건부 UPDATE(`update ... where revoked_at is null`) 자체가 이미 DB 수준에서 원자적 확인+변경을 보장하기 때문이다 — PostgreSQL은 UPDATE 대상 행에 자동으로 쓰기 잠금을 걸고, 조건이 안 맞으면 그냥 0행을 갱신한 채 끝낸다. 별도로 `SELECT ... FOR UPDATE`를 먼저 실행하면 잠금을 미리 잡아 두고 오래 들고 있는 비용(다른 요청이 그동안 대기)만 늘어날 뿐, 이 시나리오에서 조건부 UPDATE보다 더 얻는 게 없다. 승자와 패자를 가르는 데는 "행 수 0/1"이라는 신호 하나면 충분했다.

## Self-Check: PASSED

- FOUND: src/main/kotlin/com/goldwrestling/auth/RefreshTokenRepository.kt
- FOUND: src/main/kotlin/com/goldwrestling/auth/TokenService.kt
- FOUND: src/test/kotlin/com/goldwrestling/auth/RefreshTokenRotationConcurrencyTest.kt
- FOUND: docs/decisions.md (D-051)
- FOUND commit 6a3588e
- FOUND commit 7b04b06
- FOUND commit 379963c

---
*Phase: 02-auth-member*
*Plan: 13*
*Completed: 2026-08-03*
