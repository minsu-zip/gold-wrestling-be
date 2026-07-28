---
name: add-domain-test
description: "도메인 로직·DB·동시성 테스트를 작성할 때의 골격과 규약. 차감 정책, 예약 검증, 정원 경쟁 테스트가 필요하면 이 절차를 따른다."
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash
  - Grep
  - Glob
---

# 테스트 작성 절차

## 0. 이 변경에 테스트가 필요한가

**`docs/conventions.md` §10.0 의 "변경 유형별 표"가 유일한 기준이다.** 먼저 그 표를 확인한다.

- 프로덕션 코드를 추가·수정했으면 **같은 작업 안에서** 테스트를 함께 만든다 (CLAUDE.md 규칙 10)
- 면제 대상: `config/` 설정 클래스, 필드만 있는 DTO, yml/gradle/문서, 단순 위임 코드
- 면제로 판단했다면 **완료 보고에 그 이유를 한 줄로 밝힌다**
- 판단 기준: "이 코드가 잘못 동작하면 회원의 잔여 횟수·예약이 틀어지는가?" → 예면 테스트를 쓴다

## 어느 종류를 쓸지

| 검증 대상 | 종류 | 비용 |
|---|---|---|
| 차감량 계산, 예약 가능 판정, 기간 계산 | **단위테스트** (스프링 없음) | 밀리초 |
| 쿼리, 마이그레이션, 제약, 매핑 | **Testcontainers 통합테스트** | 컨텍스트 1회 + 컨테이너 |
| 정원 마지막 자리, 1:1 슬롯 경쟁 | **동시성 테스트** (통합) | 느림 — 꼭 필요한 곳만 |

**도메인 로직은 단위테스트가 필수다** (CLAUDE.md 기술 규칙). 스프링을 띄워야만 테스트되는 구조라면 로직이 서비스에 너무 얽힌 것 — 엔티티/도메인 함수로 분리한다.

## 1. 이름과 구조

```kotlin
class PassDeductionTest {
    @Test
    fun `잔여가 차감량보다 적으면 예약이 거부된다`() {
        // given
        val pass = sessionPass(remaining = "0.5")
        // when / then
        assertThatThrownBy { pass.deduct(ONE_SESSION, RESERVE) }
            .isInstanceOf(InsufficientPassCountException::class.java)
    }
}
```

- 메서드명은 백틱 한국어 서술문. **정책 문서의 문장을 그대로** 쓰면 추적이 쉽다 (policies.md §3 "잔여 0.5회로는 1회짜리 예약 불가")
- given/when/then 주석으로 구분
- 테스트 픽스처는 각 테스트 파일 하단이나 기능 패키지의 테스트 헬퍼에 함수로 (`fun sessionPass(remaining: String) = ...`)

## 2. 통합테스트

```kotlin
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class ReservationConcurrencyTest { ... }
```

- **애노테이션 조합을 기존 테스트와 동일하게 유지한다.** 조합이 다르면 스프링이 컨텍스트를 새로 띄워 전체 테스트가 느려진다
- 컨테이너는 `TestcontainersConfiguration`(`@ServiceConnection`)만 쓴다. 새 컨테이너 설정을 만들지 않는다
- 테스트 간 데이터 격리는 `@Transactional` 롤백으로. **단, 동시성 테스트에는 쓸 수 없다**(아래)

## 3. 시각 의존 테스트

- `OffsetDateTime.now()`를 직접 부르지 않는다 → `Clock`을 주입받고 테스트에서 고정한다
- 대상: 2주 미사용 차감(policies §4.3), 당일 취소 불가(§3), 유효기간 만료(§1), 주 단위 예약 오픈(§3)
- `Thread.sleep`으로 시간을 기다리지 않는다

## 4. 동시성 테스트 (policies §8 — 초과 예약 0건)

```kotlin
val threads = 20
val executor = Executors.newFixedThreadPool(threads)
val latch = CountDownLatch(threads)
val success = AtomicInteger()

repeat(threads) {
    executor.submit {
        try { reservationService.reserve(command); success.incrementAndGet() }
        catch (e: Exception) { /* 정원 초과·중복은 예상된 실패 */ }
        finally { latch.countDown() }
    }
}
latch.await()

assertThat(success.get()).isEqualTo(capacity)          // 정확히 정원만 성공
assertThat(reservationRepository.countBySession(id)).isEqualTo(capacity)   // DB에도 초과 없음
```

- 테스트 메서드에 **`@Transactional`을 붙이지 않는다** — 각 스레드가 별도 트랜잭션이어야 경쟁이 재현된다.
  대신 테스트 후 데이터를 직접 정리한다
- 성공 건수만 보지 말고 **DB 실제 행 수**도 검증한다
- 차감 이력(`PassTransaction`) 수도 성공 건수와 일치하는지 확인한다 (이력 누락은 조용한 버그)

## 5. 실행 (보고 전 필수)

```bash
./gradlew ktlintFormat   # 포맷 자동 수정 (build 가 포맷 위반 시 실패한다)
./gradlew build          # 포맷 검사 + 컴파일 + 테스트. Docker Desktop 실행 중이어야 함
```

테스트를 실행하지 않고 "통과한다"고 보고하지 않는다.
