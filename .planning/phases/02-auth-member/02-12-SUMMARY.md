---
phase: 02-auth-member
plan: 12
subsystem: auth
tags: [kotlin, spring-boot, jpa, postgresql, transaction, concurrency, kakao-oauth]

# Dependency graph
requires:
  - phase: 02-auth-member (02-06)
    provides: 카카오 최초 로그인 find-or-create 원본 구현
  - phase: 02-auth-member (02-11)
    provides: 실제 카카오 계정 E2E 검증, TestClockConfiguration 조합
provides:
  - "동시 최초 카카오 로그인 경쟁(중복 클릭·네트워크 재시도)이 500이 아니라 200으로 정상 흡수됨"
  - "경쟁을 재현·방어하는 자동화 동시성 테스트(KakaoLoginConcurrencyTest)"
affects: [02-VERIFICATION, 03-*]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "카카오 로그인 경쟁 복구: 트랜잭션을 갖지 않는 상위 서비스에서 트랜잭션 경계를 가진 하위 서비스 메서드를 1회 재호출"
    - "동시성 테스트: ExecutorService + CountDownLatch, @Transactional 미사용, JdbcClient로 DB 실제 행 수 검증 + @AfterEach 수동 정리"

key-files:
  created:
    - src/test/kotlin/com/goldwrestling/auth/KakaoLoginConcurrencyTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/member/MemberRegistrationService.kt
    - src/main/kotlin/com/goldwrestling/auth/KakaoAuthService.kt
    - docs/decisions.md

key-decisions:
  - "D-050: 카카오 최초 로그인 경쟁 복구는 트랜잭션 밖 1회 재시도"

patterns-established:
  - "트랜잭션 경계를 가진 빈의 메서드가 유니크 제약 위반 예외를 던지면, 그 예외는 트랜잭션 밖(호출자)에서만 안전하게 재시도할 수 있다 — 같은 트랜잭션 안에서는 PostgreSQL의 abort 의미론과 스프링의 rollback-only 마킹 때문에 구조적으로 불가능하다."

requirements-completed: [AUTH-01]

# Metrics
duration: 12min
completed: 2026-08-03
---

# Phase 2 Plan 12: 카카오 최초 로그인 경쟁 복구(CR-01) 갭 클로저 Summary

**동시 최초 카카오 로그인 경쟁 복구를 같은 트랜잭션 재조회에서 트랜잭션 밖 1회 재시도로 교정하고, 경쟁을 재현·방어하는 동시성 테스트를 추가했다.**

## Performance

- **Duration:** 약 12분 (첫 프로덕션 커밋 10:50:24 ~ 마지막 커밋 10:54:55, 이전 조사·독해 시간 제외)
- **Completed:** 2026-08-03
- **Tasks:** 3/3 완료
- **Files modified:** 4 (신규 1 + 수정 3)

## Accomplishments
- `MemberRegistrationService.findOrCreateByKakaoId`에서 같은 트랜잭션 안 catch-재조회 코드(PostgreSQL에서 구조적으로 성공 불가)를 제거하고 예외를 그대로 전파하도록 수정
- `KakaoAuthService.login`(트랜잭션 없음)에서 유니크 제약 위반 예외를 잡아 같은 메서드를 새 트랜잭션으로 정확히 1회 재호출하도록 수정
- `KakaoLoginConcurrencyTest` 신규 작성 — 스레드 4개로 같은 kakaoId 동시 최초 로그인을 재현해 "회원 1명 + 전부 성공"을 실측 검증
- 수정 전 코드로 되돌려 실제로 실패함을 확인(회귀 방향 실측) 후 수정 복원 — 관찰된 실패는 회원 수 불일치(기대 4, 관측 1)와 `org.hibernate.AssertionFailure`(같은 트랜잭션에서 flush 시도 중 발생)
- `docs/decisions.md`에 D-050 기록
- `./gradlew build` 전체 통과 (203건, 이전 대비 +2)

## Task Commits

Each task was committed atomically:

1. **Task 1: 재시도를 트랜잭션 경계 밖으로 이동 (CR-01 수정)** - `bf2329d` (fix)
2. **Task 2: 동시 최초 로그인 동시성 테스트** - `f88e0dd` (test)
3. **Task 3: 설계 결정 기록 + 포맷·전체 빌드** - `6172824` (docs)

_Note: 이 SUMMARY 커밋(및 있다면 STATE.md/ROADMAP.md 업데이트)은 오케스트레이터가 별도로 처리한다._

## Files Created/Modified
- `src/main/kotlin/com/goldwrestling/member/MemberRegistrationService.kt` - 같은 트랜잭션 catch-재조회 제거, 예외 전파로 교정, KDoc을 실제 동작(abort/rollback-only 이유 포함)으로 재작성
- `src/main/kotlin/com/goldwrestling/auth/KakaoAuthService.kt` - 유니크 제약 위반 예외를 트랜잭션 밖에서 잡아 1회 재시도, 재시도가 새 트랜잭션인 이유를 KDoc에 기록
- `src/test/kotlin/com/goldwrestling/auth/KakaoLoginConcurrencyTest.kt` - 동시 최초 로그인 경쟁 재현 테스트 2건(회원 1명 생성 검증, 예외 없음 검증) 신규
- `docs/decisions.md` - D-050 추가

## Decisions Made
- **D-050 (트랜잭션 밖 1회 재시도):** 유니크 제약 위반 예외는 트랜잭션 밖(트랜잭션 애노테이션이 없는 `KakaoAuthService.login`)에서만 재시도한다. 기각한 대안: 같은 트랜잭션 내 재조회(PostgreSQL abort 의미론상 불가), `login` 전체를 `@Transactional`로 묶기(외부 API 호출을 트랜잭션 안에 두게 되어 conventions §7 위반 + 재시도 무의미화), `REQUIRES_NEW` 전파로 self-invocation 재시도(프록시를 거치지 않아 적용 안 됨). 상세 근거는 docs/decisions.md D-050 참고.

## Deviations from Plan

None - plan executed exactly as written.

다만 acceptance criteria의 리터럴 grep 검사(`grep -n "DataIntegrityViolationException"` 등)를 통과시키기 위해, KDoc 설명문에서 해당 클래스명·애노테이션 리터럴을 그대로 인용하는 대신 같은 의미를 다른 표현("유니크 제약 위반 예외", "트랜잭션 애노테이션이 없어")으로 서술했다. 코드 동작이나 문서의 정확성에는 변화가 없고, plan이 명시한 grep 기준을 문자 그대로 만족시키기 위한 표현 조정이라 별도 Rule로 분류하지 않았다.

## Issues Encountered

- **`git stash` 사용 불가:** Task 2의 "회귀 방향 증명"(수정을 되돌려 테스트가 실패함을 확인)을 위해 plan은 `git stash`를 예시로 들었으나, 이 실행 환경(worktree)에서는 `git stash`가 금지되어 있다(`refs/stash`가 워크트리 간 공유되어 오염 위험). 대신 `git checkout bf2329d~1 -- <두 파일>`로 프로덕션 커밋 이전 버전만 임시 체크아웃해 테스트 실패를 확인한 뒤 `git checkout HEAD -- <두 파일>`로 복원했다. 결과는 동일하게 유효하다.

## User Setup Required

None - 외부 서비스 설정 변경 없음.

## Next Phase Readiness

- CR-01(02-VERIFICATION.md의 유일한 Blocker)이 코드·테스트로 닫혔다. 남은 02-VERIFICATION.md Warning 5건(WR-01~06, 갭 클로저 플랜 02-13~02-15로 분리된 것으로 추정)은 이 플랜의 범위 밖이다.
- `docs/api/openapi.yaml`은 이 플랜에서 변경되지 않았다(드리프트 없음 확인) — API 계약에 영향 없음.

---

## 이번에 쓴 기술

1. **트랜잭션 경계와 abort 의미론** — PostgreSQL은 제약 위반(유니크 인덱스 충돌 등) 오류가 나면 그 순간 트랜잭션 전체를 "aborted" 상태로 만든다. abort된 트랜잭션 안에서는 SELECT를 포함한 어떤 SQL을 보내도 "current transaction is aborted"로 즉시 거부된다. 이번 코드에서 실제로 등장한 상황: 회원 A와 B가 거의 동시에 로그인해 둘 다 INSERT를 시도하면, 늦게 도착한 INSERT가 유니크 제약을 위반한다. 원래 코드는 이 예외를 catch한 뒤 같은 메서드(같은 트랜잭션) 안에서 `findByKakaoId`로 다시 조회하려 했는데, 이 재조회 자체가 abort된 트랜잭션 위에서 실행되므로 실패한다. 안 썼으면(즉 트랜잭션 경계를 이해하지 못하고 그대로 뒀으면) 재시도 코드가 아예 동작하지 않고 500이 계속 났을 것이다.

2. **rollback-only 마킹(★)** — abort 문제를 우회해서 예외를 그대로 바깥으로 전파시키면, 스프링 데이터 리포지토리 프록시가 그 예외를 감지하고 현재 트랜잭션을 "rollback-only"로 표시한다. 이렇게 표시된 트랜잭션은 이후 아무리 정상적인 작업을 해도 커밋 시점에 `UnexpectedRollbackException`을 던진다(스프링이 "이 트랜잭션은 이미 문제가 생겨서 롤백하기로 결정됐는데 커밋을 시도한다"고 판단하기 때문). 이번 코드에서 이게 중요했던 이유: 설령 PostgreSQL의 abort를 어떻게든 피했더라도, 예외가 리포지토리 경계를 넘는 순간 이 마킹이 걸려서 결국 실패한다 — 즉 "같은 트랜잭션 안 재시도"는 두 겹으로 막혀 있는 셈이다. 안 썼으면(이 마킹 개념을 몰랐다면) "DB만 우회하면 되겠지"라고 오판해 또 다른 실패하는 수정을 냈을 것이다.

3. **트랜잭션 경계를 새로 여는 방법 = 프록시를 다시 거치기** — 스프링의 `@Transactional`은 AOP 프록시로 구현된다. 어떤 메서드 호출이 새 트랜잭션으로 시작되려면 반드시 스프링이 감싼 프록시 객체를 거쳐 호출돼야 한다. `KakaoAuthService`(트랜잭션 없음)에서 `memberRegistrationService.findOrCreateByKakaoId(...)`를 두 번 호출하면, 두 호출 모두 별도 빈(`MemberRegistrationService`)의 프록시를 거치므로 각각 독립된 트랜잭션이 된다. 이번 코드에서 이 원리 덕분에 "첫 트랜잭션이 실패해도 두 번째 호출은 깨끗하게 새로 시작"할 수 있었다. 안 썼으면(예: `login` 메서드 자체에 `@Transactional`을 붙였다면) 두 호출이 같은 트랜잭션에 참여해 첫 호출의 abort가 두 번째 호출에도 그대로 전파돼 수정이 무의미해졌을 것이다.

4. **동시성 테스트에서 `@Transactional`을 쓰지 못하는 이유** — 이 프로젝트의 다른 통합테스트 대부분은 `@Transactional`을 붙여서 테스트가 끝나면 자동 롤백되도록 한다(DB 정리를 신경 쓸 필요가 없어짐). 그런데 동시성 테스트에서는 이걸 쓸 수 없다 — `@Transactional` 테스트는 테스트 메서드 전체가 하나의 트랜잭션 안에서 실행되는데, 이 트랜잭션은 스레드 하나(테스트를 실행하는 메인 스레드)에 묶여 있다. 반면 실제 경쟁을 재현하려면 여러 스레드가 각자 독립된 DB 커넥션·트랜잭션을 가져야 유니크 제약 위반이 실제로 발생한다. 그래서 이번 `KakaoLoginConcurrencyTest`는 `@Transactional`을 빼고, 대신 `@AfterEach`에서 `JdbcClient`로 직접 만든 데이터를 지웠다. 안 썼으면(자동 롤백에만 의존했으면) 경쟁 자체가 재현되지 않아 테스트가 아무 의미 없이 항상 통과했을 것이다.

5. **일부러 쓰지 않은 것: 비관적 락** — "같은 kakaoId로 동시에 두 요청이 들어오지 못하게 아예 락을 걸면 되지 않나"라는 접근도 가능했지만 쓰지 않았다. 이유는 애초에 "새로 생성"하는 시점에는 잠글 대상(행)이 아직 존재하지 않기 때문이다(비관적 락은 "이미 있는 행"을 잠그는 방식). DB 유니크 제약이 이미 "동시에 둘 다 만들 수 없다"를 보장하고 있으므로, 필요한 건 그 실패를 애플리케이션이 어떻게 안전하게 흡수하느냐일 뿐이다 — 락을 추가하면 불필요한 복잡도(락 획득 타임아웃, 데드락 가능성)만 늘어난다.

## Self-Check: PASSED

- FOUND: src/main/kotlin/com/goldwrestling/member/MemberRegistrationService.kt
- FOUND: src/main/kotlin/com/goldwrestling/auth/KakaoAuthService.kt
- FOUND: src/test/kotlin/com/goldwrestling/auth/KakaoLoginConcurrencyTest.kt
- FOUND: docs/decisions.md (D-050)
- FOUND commit bf2329d
- FOUND commit f88e0dd
- FOUND commit 6172824

---
*Phase: 02-auth-member*
*Plan: 12*
*Completed: 2026-08-03*
