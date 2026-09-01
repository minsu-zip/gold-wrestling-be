---
quick_id: 260828-g4o
slug: issue-12-suspend-cancel-race
status: complete
date: 2026-08-28
commits: 0
---

# 휴강 캐스케이드 × 이용권 등록취소 경합 (이슈 #12 / WR-02) — 실행 요약

**커밋 0건 — 사용자 확인 대기.** CLAUDE.md 커밋 규칙("phase 실행 밖에서는 사용자 지시가 있어야
커밋한다")에 따라 이 quick task는 파일 변경만 하고 `git add`/`git commit`/`git push`를 실행하지
않았다. `git status --short` 결과 워킹 트리 변경만 있고 새 커밋은 없다.

## 변경 파일

| 파일 | 종류 |
|---|---|
| `src/main/kotlin/com/goldwrestling/reservation/ReservationLedgerSupport.kt` | 프로덕션 — `restorePassAfterSuspension` 신설, `restorePassAfterCancellation`에서 `reason` 파라미터 제거 |
| `src/main/kotlin/com/goldwrestling/schedule/AdminScheduleService.kt` | 프로덕션 — `ReservationCancellationSnapshot.passStatus` 제거, ⑤ 루프가 `restorePassAfterSuspension` 호출 |
| `src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt` | 프로덕션 — `cancel()`에 상태 전환 직후 활성 예약 이중 검사 추가 |
| `src/test/kotlin/com/goldwrestling/schedule/ClassSessionSuspensionTest.kt` | 테스트 — 신규 테스트 1건 + `@MockitoSpyBean` KDoc 갱신 |
| `src/test/kotlin/com/goldwrestling/reservation/ReservationLedgerRestoreTest.kt` | 테스트 — 신규 파일, 단일 취소 경로 회귀 방어 1건 + 휴강 복구 0행 스킵(실제 행 잠금 대기) 1건 |
| `src/test/kotlin/com/goldwrestling/pass/PassCancelDoubleCheckTest.kt` | 테스트 — 신규 파일, 이중 검사 1건 |
| `docs/decisions.md` | 문서 — D-145 신설 |

`docs/api/openapi.yaml`은 변경하지 않았다 — API 응답 형태·에러코드가 그대로다(계획대로).

## Task별 결과

### Task 1 — 캐스케이드 복구 경로를 단일 취소 경로에서 분리하고 최신 상태로 재판정

`ReservationLedgerSupport`에 `restorePassAfterSuspension(passId, reservationId, canceledAt, admin)`을
신설했다. 동작: ① `passRepository.findById`로 **현재** 이용권 상태를 다시 읽어
`ReservationRefundPolicy.shouldRestore`를 재판정 → ② false면 아무것도 안 하고 반환 → ③
`adjustRemainingCount`가 0행이면 **예외 대신 WARN 로그** 후 반환(추적 정보로 `passId`·`reservationId`
포함) → ④ 0이 아니면 재조회한 영속 `Pass`로 `CLASS_CANCELED_REFUND` 이력 저장.

`restorePassAfterCancellation`은 `reason` 파라미터를 제거해 항상 `CANCEL_REFUND`만 쓰도록 굳혔다 —
비기본값(`CLASS_CANCELED_REFUND`)을 넘기던 유일한 호출부(휴강)가 새 메서드로 이동했으므로, 남겨두면
"여기로도 휴강을 부를 수 있다"는 오해가 남는다. 0행 → `IllegalStateException` 동작은 그대로다.

`AdminScheduleService`의 ⑤ 루프를 `restorePassAfterSuspension` 호출로 교체하고,
`ReservationCancellationSnapshot`에서 `passStatus` 필드를 제거했다(stale 값의 근원). 관련 KDoc(클래스
상단, `suspend()` 처리 순서 설명, 데이터 클래스 주석)을 "복구 판정은 스냅샷이 아니라 복구 직전 현재
상태로 한다"로 갱신했다. 미사용 `PassStatus` import를 제거했고, `TransactionReason`은 KDoc 링크로 계속
참조돼 남겨뒀다.

테스트: `ClassSessionSuspensionTest`에 `휴강 도중 대상 이용권이 등록취소되면 그 건만 복구를 건너뛰고
휴강은 성공한다`를 추가했다. `@MockitoSpyBean reservationRepository`로 `cancelByAdminIfActive` 첫
호출을 가로채, `REQUIRES_NEW` 트랜잭션으로 대상 이용권을 먼저 등록취소·커밋한 뒤 실제 취소를
반영하도록 해 t1~t4 경합을 결정적으로 재현했다.

**계획과의 편차(구현 중 발견, Rule 3 — 블로킹 이슈 해결):** 계획은 `invocation.callRealMethod()`로 실제
예약 취소를 위임하도록 지시했지만, `reservationRepository`는 Spring Data JPA가 인터페이스만으로 생성한
프록시(구현 클래스 없음)라 `@MockitoSpyBean`으로 스파이한 뒤 `callRealMethod()`를 호출하면
`MockitoException`("Cannot call abstract real method on java object")으로 실패했다. 대신
`cancelByAdminIfActive`와 동등한 조건부 UPDATE를 `JdbcClient` 원시 SQL로 반영해 반환 행 수(1)를 그대로
재현하도록 수정했다.

**오케스트레이터 검토에서 추가로 고친 것:** 원시 SQL 대체는 실제 메서드의
`@Modifying(clearAutomatically = true)` 효과(영속성 컨텍스트 비우기)를 빠뜨렸다. 그 결과 ④의 fetch join이
1차 캐시에 남긴 ACTIVE `Pass`를 재조회가 그대로 돌려줘, 이 테스트는 프로덕션과 다른 경로(0행 → WARN
스킵)를 타고 있었다(실행 로그에서 WARN 1건으로 확인). `willAnswer` 안에 `entityManager.clear()`를 넣어
프로덕션과 같은 경로 — 재조회가 CANCELED를 읽어 `shouldRestore = false`로 조기 반환 — 를 타도록
바로잡았다(재실행 후 WARN 0건 확인). 0행 → WARN 스킵 경로는 아래 `ReservationLedgerRestoreTest`의 두 번째
테스트가 **실제 행 잠금 대기**로 별도 고정한다.

새 파일 `ReservationLedgerRestoreTest.kt`에 `단일 취소 복구는 이용권이 등록취소돼 있으면
IllegalStateException을 던진다` 1건을 추가했다. `PassCancellationConcurrencyTest`와 동일한 애노테이션
조합(클래스 레벨 `@Transactional` 없음)을 썼고, `ReservationLedgerSupport`가 자체 트랜잭션을 열지
않는 헬퍼라 `TransactionTemplate`으로 감쌌다. CANCELED pass 픽스처는 `cancelIfNotCanceled` 실제
취소 경로로 만들었다(`ck_pass_cancellation` 제약). **계획과의 편차(Rule 3):** 이 픽스처 setup 호출
자체도 `@Modifying` 쿼리라 트랜잭션 없이 부르면 `TransactionRequiredException`이 나는 것을 실행 중
발견해, 이것도 `TransactionTemplate`으로 감쌌다(계획 문서는 이 setup 호출의 트랜잭션 필요성을
명시하지 않았다).

같은 파일에 오케스트레이터가 `휴강 복구는 재조회 시점엔 ACTIVE였어도 UPDATE가 0행이면 예외 없이 건너뛰고
잔여-이력을 남기지 않는다`를 추가했다. Mockito 없이 **실제 락 대기**로 결정적으로 재현한다: 메인 스레드가
등록취소 트랜잭션을 열어 `cancelIfNotCanceled`로 이용권 행을 잠근 채 커밋을 미루고, 별도 스레드의
`restorePassAfterSuspension`은 `findById`로 (아직 커밋 전이라) ACTIVE를 읽은 뒤 `adjustRemainingCount`
UPDATE에서 행 잠금 대기에 걸린다. 메인 스레드는 `pg_stat_activity`에서 `wait_event_type = 'Lock'`인
`update pass …` 세션이 보일 때까지 폴링한 뒤 커밋한다 → UPDATE가 깨어나 갱신된 행(CANCELED)으로
`WHERE`를 재평가해 0행 → WARN 스킵. 단언: 예외 없음, 이용권 CANCELED·잔여 1.0 불변, `PassTransaction` 0건.
실행 로그에 `휴강 복구 스킵 — passId=…: 등록취소와 동시 발생 — 정책상 복구하지 않음` WARN이 실제로 찍힌다.
첫 실행은 10초 타임아웃으로 실패했는데, PostgreSQL이 트랜잭션 안에서 `pg_stat_activity`를 처음 읽은
결과를 트랜잭션 끝까지 캐시하기 때문이었다 — 폴링마다 `pg_stat_clear_snapshot()`을 호출해 해결했다.

### Task 2 — 등록취소에 상태 전환 직후 활성 예약 이중 검사

`AdminPassService.cancel`에서 `cancelIfNotCanceled` 반환값 검사 직후, `zeroRemainingCount` 호출
이전에 `reservationRepository.existsByPassIdAndStatus(passId, ACTIVE)`를 한 번 더 확인해 `true`면
`PassHasActiveReservationException()`을 던지도록 추가했다. `@Transactional` 메서드 내에서 던지는
unchecked 예외라 `cancelIfNotCanceled`가 반영한 상태 전환도 함께 롤백된다. KDoc에 이중 검사의 이유와
`zeroRemainingCount`보다 앞에 둬야 하는 이유(뒤에 두면 잘못된 원인 `PassStateConflictException`으로
보고됨)를 남겼다.

새 파일 `PassCancelDoubleCheckTest.kt`에 `선행 검사 이후 활성 예약이 생기면 등록 취소가 거부되고
상태-이력이 변하지 않는다` 1건을 추가했다. `@MockitoSpyBean ReservationRepository`로
`existsByPassIdAndStatus`가 첫 호출(D-089 선행 검사)은 false, 두 번째 호출(이중 검사)은 true를
반환하도록 `willReturn(false, true).given(spy).method(실제 인자)` 형태로 스텁했다(매처 없이 실제
값 사용 — Kotlin non-null + Mockito 5 NPE 회피). 클래스 레벨 `@Transactional`은 붙이지 않았고
`@AfterEach`에서 `JdbcClient`로 직접 정리했다.

### Task 3 — 결정 기록 + 전체 회귀·포맷 게이트

`docs/decisions.md`에 D-145를 추가했다(직전 D-144 확인 후 다음 번호로 순연 없이 그대로 사용). 내용:
복구 재판정 + 0행 스킵, 메서드 분리 이유, 409 미채택 이유, 등록취소 이중 검사, 근거(이슈 #12/WR-02).

게이트 실행 결과:
- `./gradlew ktlintFormat` — 성공(추가 수정 없음, 이전 실행에서 이미 정리됨)
- `./gradlew build` — 성공(ktlintCheck 포함)
- `./gradlew cleanTest test` — **성공, 861 tests, 0 failures, 0 errors** (기존 857건 + 이번에 추가한
  4건). 오케스트레이터 검토 후 최종 재실행 기준.

`policies.md`·`requirements.md`·`glossary.md`는 계획대로 수정하지 않았다 — 기존 정책(D-059·D-089·
D-091)을 경합 상황에서도 지키게 만드는 변경이라 정책 자체가 바뀌지 않았다.

## 검증 체크리스트

- [x] `./gradlew cleanTest test` 전체 그린 (861건, 신규 4건 포함, 0 failures/errors)
- [x] `./gradlew build` (ktlintCheck 포함) 그린
- [x] `grep -n "passStatus" .../AdminScheduleService.kt` — 필드는 제거됐고, 남은 것은 "이제 이
      필드를 쓰지 않는다"고 설명하는 KDoc 주석 1건뿐(의도된 것)
- [x] `git status`에 커밋되지 않은 변경만 있고 새 커밋은 없음(확인: `git log -1`이 실행 시작 시점의
      HEAD `6d84701`과 동일)
- [x] `docs/api/openapi.yaml` 무변경

## 테스트를 쓰지 않기로 판단한 변경

없음 — `docs/decisions.md` 수정은 conventions §10.0의 면제 대상(문서)이라 별도 테스트가 필요 없다.
그 외 프로덕션 코드 변경(`ReservationLedgerSupport`·`AdminScheduleService`·`AdminPassService`)은
모두 테스트를 함께 작성했다.

## 이번에 쓴 기술

1. **READ COMMITTED에서 조건부 UPDATE의 잠금 대기 후 `WHERE` 재평가**
   ① PostgreSQL의 기본 트랜잭션 격리수준. 한 트랜잭션이 어떤 행을 UPDATE하려는데 다른 트랜잭션이
   같은 행을 이미 잠그고 있으면, 뒤에 온 UPDATE는 그 잠금이 풀릴 때까지 **기다렸다가**, 깨어난 뒤
   `WHERE` 조건을 **그 시점의 최신 값으로 다시** 평가한다.
   ② 이 코드에서 왜 필요했는가: 휴강 캐스케이드가 이용권을 복구하려는 순간, 다른 관리자의 등록취소
   UPDATE가 같은 이용권 행을 먼저 잠그고 있다가 커밋하면, 복구 UPDATE는 대기 후 깨어나 "이미
   CANCELED가 된" 최신 상태로 `WHERE status = ACTIVE`를 재평가해 0행을 반환한다. "복구 직전에 한 번
   더 조회했으니 안전하다"고 생각해도, 조회와 실제 UPDATE 문 실행 사이에 다시 그 창이 열린다 —
   그래서 재조회만으로는 부족하고 0행 자체를 최종 방어(스킵)로 다뤄야 한다.
   ③ 안 썼으면 뭐가 깨지는가: 0행을 여전히 예외로 처리했다면, 무관한 회원의 등록취소 타이밍이
   나빴다는 이유만으로 휴강 요청 전체가 500으로 실패하고 롤백된다(이 이슈의 원래 버그).

2. **스냅샷(stale read)과 복구 직전 현재 상태 재조회**
   ① 한 트랜잭션이 오래 걸리는 N건 반복 작업 도중 초반에 읽어 둔 값(스냅샷)이, 실제로 그 값을 쓰는
   시점에는 이미 낡아 있을 수 있는 문제.
   ② 이 코드에서 왜 필요했는가: `AdminScheduleService.suspend`는 ④단계에서 활성 예약 N건을 배치
   조회하며 각 이용권의 `passStatus`를 한 번에 떠 두고, ⑤단계 루프에서 그 값으로 복구 여부를
   판정했다 — 이 사이에 다른 관리자가 등록취소하면 스냅샷 값은 낡은 ACTIVE를 계속 들고 있는다.
   ③ 안 썼으면 뭐가 깨지는가: 낡은 스냅샷 그대로 복구를 시도해 `adjustRemainingCount`가 실제로는
   실패하는데 그걸 예외로 처리해 버리는 원래 버그가 재발한다.

3. **`REQUIRES_NEW` 전파로 중간 상태를 커밋해 경합을 결정적으로 재현하는 테스트 기법** ★
   ① 트랜잭션 전파(propagation) 옵션 중 하나. 지금 진행 중인 트랜잭션을 잠시 미뤄두고 완전히 새
   트랜잭션을 시작해 독립적으로 커밋·롤백한다.
   ② 이 코드에서 왜 필요했는가: 실제 스레드 두 개로 "정확히 이 타이밍에" 다른 관리자가 취소하게
   만드는 건 창이 마이크로초 단위라 매번 성공한다는 보장이 없다(플레이키 테스트). 대신 테스트
   코드 안에서 `TransactionTemplate(transactionManager).apply { propagationBehavior =
   PROPAGATION_REQUIRES_NEW }`로 별도 트랜잭션을 열어 그 안에서 이용권을 취소·커밋한 뒤, 원래
   진행 중이던 휴강 트랜잭션으로 돌아온다 — 순서가 항상 같으므로 결정적이다.
   ③ 안 썼으면 뭐가 깨지는가: 진짜 스레드로 경쟁을 재현하려 했다면 테스트가 가끔 통과하고 가끔
   실패하는(flaky) 테스트가 됐을 것이고, CI에서 원인 불명 실패로 시간을 낭비하게 된다.

4. **벌크 UPDATE의 `clearAutomatically`와 준영속(detached) 엔티티** ★
   ① JPA `@Modifying` 벌크 UPDATE는 영속성 컨텍스트(1차 캐시)를 거치지 않고 DB에 직접 SQL을
   보낸다. `clearAutomatically = true`를 켜면 그 실행 직후 영속성 컨텍스트를 통째로 비워, 이후
   조회가 갱신된 최신 값을 다시 읽게 만든다. 이 옵션이 켜진 순간 그 전까지 관리되던 엔티티는
   전부 준영속(detached) 상태가 된다.
   ② 이 코드에서 왜 필요했는가: `restorePassAfterSuspension`이 `adjustRemainingCount`(벌크 UPDATE,
   `clearAutomatically = true`)를 실행한 직후에는, 그 전에 읽었던 `Pass` 엔티티가 준영속이 돼
   `PassTransaction`에 그대로 넣으면 `TransientPropertyValueException` 류의 문제가 생긴다 —
   그래서 UPDATE 후 `findById`로 **다시** 조회한 영속 엔티티로 이력을 저장한다(기존
   `restorePassAfterCancellation`과 동일한 함정).
   ③ 안 썼으면 뭐가 깨지는가: 준영속 엔티티를 그대로 이력 저장에 쓰면 실행 시점에 예외가 나거나,
   JPA 캐스케이드 설정에 따라 예기치 않은 flush가 일어나 데이터가 꼬일 수 있다.

5. **실패 격리 — N건 중 1건 실패가 나머지 N-1건을 롤백시키지 않는 경계 설계**
   ① 하나의 트랜잭션 안에서 여러 건을 처리할 때, 일부 건의 "정상적으로 예상 가능한 실패"가 전체
   트랜잭션을 롤백시키지 않도록 그 실패를 예외가 아니라 정상 흐름(로그+스킵)으로 다루는 설계.
   ② 이 코드에서 왜 필요했는가: 휴강 캐스케이드는 한 트랜잭션 안에서 N건의 예약을 취소·복구한다.
   그중 1건이 무관한 다른 관리자의 등록취소와 경합해 복구를 못 했다고 해서, 나머지 N-1건까지
   전부 롤백시킬 이유가 없다 — 관리자가 "이 수업을 닫는다"고 내린 결정의 성패가 자신과 무관한
   회원의 이용권 상태에 좌우돼선 안 된다.
   ③ 안 썼으면 뭐가 깨지는가: 단일 취소 경로처럼 예외로 처리했다면(이 이슈의 원래 버그), 정상적인
   휴강 처리가 무관한 동시성 이벤트 하나 때문에 통째로 500 실패한다.

6. **`pg_stat_activity`로 다른 세션의 잠금 대기를 관측해 경합 순서를 고정하는 테스트 기법** ★
   ① PostgreSQL 시스템 뷰 `pg_stat_activity`는 현재 접속된 세션마다 실행 중인 SQL과 무엇을 기다리는지
   (`wait_event_type = 'Lock'`이면 잠금 대기)를 보여준다. 테스트가 이를 폴링하면 "상대 스레드가 지금
   정확히 UPDATE에서 막혀 있다"는 사실을 확인한 뒤 다음 단계(커밋)로 넘어갈 수 있다.
   ② 이 코드에서 왜 필요했는가: 0행 스킵 경로는 "재조회는 ACTIVE를 읽었지만 UPDATE는 0행"이어야
   닿는다. 이는 등록취소가 행을 잠근 뒤·커밋 전에 복구가 시작돼야 하는 순서인데, 스레드 두 개로는
   그 순서를 보장할 수 없다. 잠금 대기가 관측된 뒤에만 커밋하면 순서가 항상 같다 — 그리고 이건
   Mockito로 흉내 낸 게 아니라 READ COMMITTED의 `WHERE` 재평가가 실제로 일어나는 것을 본 것이다.
   ③ 안 썼으면 뭐가 깨지는가: `Thread.sleep`으로 타이밍을 맞추면 느린 CI에서 커밋이 먼저 일어나
   재조회가 CANCELED를 읽는 경로(a)로 빠져, 정작 고정하려던 경로(b)를 검증하지 못한 채 통과한다.
   함정 하나: 트랜잭션 안에서 `pg_stat_activity`를 읽으면 첫 결과가 트랜잭션 끝까지 캐시된다 —
   폴링마다 `pg_stat_clear_snapshot()`을 불러야 한다(첫 실행이 이 때문에 10초 타임아웃으로 실패했다).

7. **1차 캐시(영속성 컨텍스트)가 "재조회"를 가로채는 함정**
   ① `findById`는 영속성 컨텍스트에 같은 id의 엔티티가 이미 있으면 DB에 가지 않고 그것을 돌려준다.
   ② 이 코드에서 왜 필요했는가: 휴강 캐스케이드의 ④ fetch join이 `Pass`들을 컨텍스트에 올려 둔다.
   프로덕션에서는 ⑤의 `cancelByAdminIfActive`가 `clearAutomatically = true`로 컨텍스트를 비워 주기
   때문에 `restorePassAfterSuspension`의 `findById`가 DB의 현재 상태를 읽는다. 테스트에서 그 메서드를
   원시 SQL로 대체하자 비우기가 빠져 재조회가 stale ACTIVE를 돌려줬고, 테스트가 프로덕션과 다른
   경로를 타고 있었다 — `entityManager.clear()`를 넣어 바로잡았다.
   ③ 안 썼으면 뭐가 깨지는가: "복구 직전 현재 상태로 재판정한다"는 설계가 1차 캐시 때문에 무력해질
   수 있다. 벌크 UPDATE 뒤에 재조회하는 코드는 그 UPDATE가 컨텍스트를 비우는지 항상 확인해야 한다.

## 일부러 쓰지 않은 것

- **비관적 락(`SELECT ... FOR UPDATE`)**: 이용권 행에 락을 걸어 두 트랜잭션이 동시에 접근하지
  못하게 막는 대안도 있었지만 쓰지 않았다. 이 저장소는 D-021 관례대로 조건부 UPDATE(compare-and-
  swap)로 경쟁을 통제하는 것이 기본이고, 비관적 락은 락 보유 시간 동안 다른 트랜잭션을 대기시켜
  처리량을 떨어뜨린다. 이 시나리오는 "경쟁에서 진 쪽을 예외로 실패시켜야 하는 게 아니라 스킵하면
  되는" 상황이라 락으로 막을 필요조차 없었다 — 락은 "막아야 할 때" 쓰는 도구인데, 여기서는 "이미
  일어난 걸 정확히 감지해 스킵하면 되는" 문제였다.
- **낙관적 락(`@Version`)**: `Pass` 엔티티에 버전 컬럼을 추가해 갱신 충돌을 감지하는 대안도
  기각했다. 낙관적 락은 충돌 시 예외(`OptimisticLockException`)를 던지는 것이 기본 동작이라,
  "충돌해도 조용히 스킵해야 한다"는 이 시나리오의 요구와 맞지 않는다. 게다가 이 저장소 전체가
  이미 JPQL 벌크 UPDATE(조건부 갱신) 패턴으로 통일돼 있어, 이 한 곳만 낙관적 락으로 바꾸면 동시성
  제어 방식이 두 가지로 갈린다.
- **409로 거부**: 결정 2에서 다룬 대로, 재시도로 해결되지 않는 실패를 사용자에게 떠넘기는 결과라
  기각했다(`docs/decisions.md` D-145 참고).

## Self-Check: PASSED

- 변경/신규 파일 8개 전부 실재 확인(`FOUND`)
- `HEAD`가 실행 시작 시점 커밋(`6d84701b72c7ca1925ee75e44c08c4b17a237ced`)과 동일 — 새 커밋 0건 확인
- `restorePassAfterSuspension` 문자열이 `ReservationLedgerSupport.kt`에 4회 등장(정의 1 + KDoc 참조
  3) — 신설 메서드가 실제로 파일에 반영됨을 확인
