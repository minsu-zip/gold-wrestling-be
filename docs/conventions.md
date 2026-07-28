# 코드 규약 (conventions.md)

> 이 프로젝트에서 코드를 쓰거나 고칠 때의 구현 규약. **결정의 "왜"는 `decisions.md`에 있다.**
> 도메인 규칙은 `policies.md`, 이름은 `glossary.md`가 최종 기준이다.
> 여기 적힌 것과 기존 코드가 다르면 **기존 코드가 틀린 것**으로 보고 고친다.

## 1. 패키지 레이아웃 (D-018)

기능별 패키지 안에 계층을 둔다.

```
com.goldwrestling
├── config/                  # 횡단 설정 (Security, OpenApi, Jpa, Web)
├── common/                  # 여러 기능이 공유하는 것만. 애매하면 기능 패키지에 둔다
│   ├── error/               # 전역 예외 핸들러, 도메인 예외 기반 클래스, 에러코드
│   └── time/                # Asia/Seoul 기준 시간 유틸, 주(週) 계산
├── member/
│   ├── MemberController.kt
│   ├── MemberService.kt
│   ├── MemberRepository.kt
│   ├── Member.kt            # 엔티티
│   └── dto/                 # 요청·응답 DTO
├── pass/                    # Pass, PassTransaction, 차감·복구
├── reservation/             # Reservation, 예약 검증
├── schedule/                # ClassSchedule, ClassSession, 휴강
├── attendance/              # Attendance
├── notice/                  # Notice
└── notification/            # Notification
```

- 새 기능 패키지를 만들기 전에 **`glossary.md`에 그 개념이 있는지 확인**한다. 없으면 glossary에 먼저 추가한다.
- `common/`은 쓰레기통이 되기 쉽다. 두 기능 이상이 실제로 쓰기 전에는 넣지 않는다.
- 파일 하나에 클래스 하나가 기본. DTO는 관련된 것끼리 한 파일에 모아도 된다.

## 2. 네이밍

- 클래스·필드·API 경로·DB 컬럼 모두 `glossary.md`의 코드 네이밍을 그대로 쓴다. **금지어: `Ticket`, `Voucher`, `Coupon`, `Booking`, `Course`**
- DB 테이블·컬럼은 `snake_case` (예: `pass_transaction`, `remaining_count`)
- API 경로는 `kebab-case` 복수형 (예: `/api/session-passes`, `/api/reservations`)
- DTO: `<동작><대상>Request` / `<대상>Response` (예: `CreateReservationRequest`, `ReservationResponse`)
- 테스트 메서드명은 백틱 한국어 서술문 (예: `` fun `잔여 횟수가 부족하면 예약이 거부된다`() ``)

## 3. 엔티티 (Kotlin + JPA)

Kotlin에서 JPA를 쓸 때 실수하기 쉬운 지점들이다. 전부 지킨다.

```kotlin
@Entity
@Table(name = "session_pass")
class SessionPass(                          // data class 금지
    @ManyToOne(fetch = FetchType.LAZY)      // 기본값이 EAGER라 반드시 명시
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @Column(name = "remaining_count", nullable = false, precision = 4, scale = 1)
    var remainingCount: BigDecimal,         // 변경되는 필드만 var

    @Enumerated(EnumType.STRING)            // ORDINAL 금지 (enum 순서 바뀌면 데이터 깨짐)
    @Column(nullable = false, length = 20)
    var status: PassStatus,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null                    // 저장 전에는 null
}
```

- **`data class` 금지** — 자동 생성되는 `equals`/`hashCode`/`copy`가 지연 로딩 프록시·양방향 연관과 충돌한다
- **`@ManyToOne`/`@OneToOne`은 항상 `fetch = LAZY`** 를 명시한다 (JPA 기본값이 EAGER)
- **`@Enumerated(EnumType.STRING)`** 을 항상 붙인다
- 컬렉션은 `val list: MutableList<X> = mutableListOf()` 로 두고 교체하지 않는다 (Hibernate가 참조를 추적한다)
- 양방향 연관은 **정말 양쪽에서 탐색해야 할 때만** 만든다. 기본은 단방향 `@ManyToOne`
- 엔티티 동일성 비교는 `id`로 한다. `equals` 오버라이드가 필요하면 `id`만 사용
- `nullable = false`인 컬럼은 Kotlin 타입도 non-null로 맞춘다 (불일치가 `ddl-auto=validate`로 잡히지 않는 종류의 버그를 만든다)

## 4. 횟수와 BigDecimal (D-016)

```kotlin
// 잔여 확인 — 반드시 compareTo
if (pass.remainingCount < amount) throw InsufficientPassCountException(...)   // Kotlin은 <, > 가 compareTo로 컴파일됨

// 같은지 비교 — equals 쓰지 말 것 (0.5 != 0.50)
if (pass.remainingCount.compareTo(BigDecimal.ZERO) == 0) { ... }
// 또는
if (pass.remainingCount.signum() == 0) { ... }
```

- 상수는 `common`에 모아 둔다: `ONE_SESSION = BigDecimal("1.0")`, `HALF_SESSION = BigDecimal("0.5")`
- `BigDecimal(0.5)` (Double 생성자) 금지 → 반드시 `BigDecimal("0.5")` (문자열 생성자)
- **모든 잔여 횟수 변경은 `PassTransaction` 이력을 함께 남긴다.** 이력 없는 변경은 리뷰에서 반드시 거부한다 (CLAUDE.md 규칙 5)

## 5. 시간 처리

- 서버·DB 기준 시간대는 `Asia/Seoul` 고정 (JVM 기본 시간대를 `main`에서 설정, `.env`의 `TZ`)
- 타입 선택:
  | 용도 | 타입 | DB |
  |---|---|---|
  | 수업 날짜 | `LocalDate` | `date` |
  | 수업 시작·종료 시각 | `LocalTime` | `time` |
  | 생성·수정·차감 시각 | `OffsetDateTime` | `timestamptz` |
  | 유효기간 만료일 | `LocalDate` | `date` |
- **`LocalDateTime` + `timestamp`(without tz) 조합은 쓰지 않는다** — 시간대 정보가 사라져 배포 환경에서 조용히 어긋난다
- 현재 시각은 직접 `OffsetDateTime.now()`를 호출하지 않고 **`Clock` 빈을 주입받아** 쓴다 (테스트에서 시각을 고정해야 한다: 2주 미사용 차감, 당일 취소 불가)
- **주(週)의 시작은 월요일.** 주 범위 계산은 `common/time`의 공용 함수를 쓰고 각자 계산하지 않는다

## 6. DTO와 API (D-019)

- 컨트롤러는 DTO만 주고받는다. 엔티티를 반환하거나 파라미터로 받지 않는다
- 요청 DTO에 `jakarta.validation` 애노테이션으로 형식 검증(`@NotNull`, `@Positive`)을 걸고, **도메인 규칙 검증은 서비스·엔티티에서** 한다 (정원, 잔여 횟수, 당일 취소 불가는 DTO의 일이 아니다)
- 응답 DTO는 엔티티 → DTO 변환 함수를 DTO 쪽 `companion object`에 둔다 (`ReservationResponse.from(reservation)`)
- **API를 추가·변경하면 `docs/api/openapi.yaml`을 재생성해 같은 커밋에 포함한다** (CLAUDE.md 규칙 3)
- `@Operation`/`@Schema`로 요약을 붙인다. FE가 이 스펙만 보고 작업한다

## 7. 서비스와 트랜잭션 (D-020)

```kotlin
@Service
@Transactional(readOnly = true)              // 클래스 기본
class ReservationService(
    private val reservationRepository: ReservationRepository,
) {
    @Transactional                            // 변경 메서드만 오버라이드
    fun reserve(command: ReserveCommand): Reservation { ... }
}
```

- 컨트롤러·리포지토리에 `@Transactional`을 붙이지 않는다
- 한 트랜잭션 안에서 **차감과 예약 생성이 함께** 끝나야 한다 (D-001)
- 트랜잭션 안에서 외부 API 호출·긴 대기를 하지 않는다
- 비즈니스 규칙은 가능한 한 엔티티 메서드로 내린다 (`pass.deduct(amount, reason)`). 서비스는 조립·트랜잭션·조회 담당

## 8. 에러 처리 (D-017)

- 도메인 예외는 `common/error`의 기반 클래스를 상속하고, 에러코드를 갖는다
- 전역 `@RestControllerAdvice` 하나에서 `ProblemDetail`로 변환한다. 컨트롤러에서 `try-catch`로 응답을 만들지 않는다
- 에러 응답에 내부 정보(스택트레이스, SQL, 예외 클래스명)를 넣지 않는다
- HTTP 상태: 검증 실패 400 / 인증 401 / 권한 403 / 없음 404 / 정원·중복·잔여부족 같은 상태 충돌 409

## 9. Flyway

- 스키마 변경은 **오직** `src/main/resources/db/migration/V<번호>__<설명>.sql` 로 한다. `ddl-auto`는 `validate` 고정
- **이미 커밋된 마이그레이션 파일은 절대 수정하지 않는다.** 잘못됐으면 새 버전을 추가해 고친다 (체크섬 불일치로 기동이 막힌다)
- 파일명은 `V2__create_member.sql` 처럼 소문자 스네이크로. 번호는 건너뛰지 않는다
- 제약·인덱스도 마이그레이션에 명시한다. 특히 **동시성 방어용 유니크 제약**(D-021)
- 마이그레이션 작성 후 `./gradlew test`로 실제 적용을 확인한다 (Testcontainers가 빈 DB에서 전부 재생한다)

## 10. 테스트

### 10.0 변경 유형별로 무엇을 함께 작성하는가

프로덕션 코드를 **추가·수정하면 같은 작업 안에서** 아래 표에 해당하는 테스트를 함께 만든다.
"나중에 추가"는 하지 않는다 — 다음 phase가 그 코드를 이미 전제로 삼아 버린다.

| 바꾼 것 | 함께 작성할 테스트 | 왜 |
|---|---|---|
| 엔티티 메서드 / 도메인 규칙 (차감, 예약 가능 판정, 기간 계산) | **단위테스트 필수** | 정책이 곧 제품이다. policies.md 문장 단위로 검증 |
| 서비스 메서드 (분기·검증·트랜잭션이 있는 것) | 단위테스트 (협력자는 가짜 객체) + 필요하면 통합 | 조건 분기가 규칙의 실현부 |
| 리포지토리 커스텀 쿼리 (`@Query`, 조건부 갱신) | **Testcontainers 통합테스트 필수** | 쿼리는 실행해 보지 않으면 맞는지 알 수 없다 |
| 컨트롤러 (새 엔드포인트, 경로·상태코드 변경) | 통합테스트 1개 이상 (성공 + 대표 실패) | FE 계약의 실제 동작 확인 |
| Flyway 마이그레이션 | `./gradlew test` 통과로 갈음 (엔티티 매핑 validate가 검증) | 별도 테스트를 새로 만들 필요 없음 |
| 정원·1:1 슬롯 등 경쟁이 있는 경로 | **동시성 테스트 필수** (§10.4) | 초과 예약 0건은 단일 스레드 테스트로 증명 불가 |
| 배치 (2주 미사용 차감 등) | 단위테스트 + **멱등성 테스트**(두 번 실행해도 결과 동일) | policies §4.3이 멱등을 요구 |
| 전역 예외 핸들러 / 에러 응답 | 통합테스트로 상태코드·본문 형태 확인 | 에러 형태도 FE 계약이다 |

**테스트를 만들지 않아도 되는 것** (이걸 위해 억지로 테스트를 쓰지 않는다):

- `config/` 의 설정 클래스, `@ConfigurationProperties` 데이터 클래스
- DTO 자체 (필드 나열). 단 **DTO 변환 로직에 계산·분기가 있으면** 단위테스트 대상
- `application.yml`, `build.gradle.kts`, docker-compose, 문서
- getter/setter 수준의 위임 코드

**판단 기준**: "이 코드가 잘못 동작하면 회원의 잔여 횟수·예약이 틀어지는가?" → 예면 테스트를 쓴다.
애매하면 테스트를 쓰는 쪽으로 기운다.

### 10.1 종류 선택

- **도메인 로직(차감 정책, 예약 검증)은 단위테스트 필수** — 스프링 컨텍스트 없이 순수 Kotlin으로
- **DB가 필요한 것은 Testcontainers 통합테스트** — `@SpringBootTest` + `@Import(TestcontainersConfiguration::class)`
- 통합테스트는 **애노테이션 조합을 통일**한다. 조합이 다르면 스프링이 컨텍스트를 새로 띄워 테스트가 급격히 느려진다

### 10.2 이름과 범위

- 메서드명은 백틱 한국어 서술문. **policies.md 문장을 그대로** 쓰면 정책↔테스트 추적이 된다
- 한 테스트는 한 가지만 검증한다. 실패했을 때 무엇이 깨졌는지 이름만 보고 알 수 있어야 한다
- 성공 경로만 쓰지 않는다. **대표 실패 경로**(잔여 부족, 정원 초과, 당일 취소)를 함께 쓴다

### 10.3 시각 의존

- 시각에 의존하는 테스트는 `Clock`을 고정해 쓴다. `Thread.sleep`으로 시간을 기다리지 않는다
- 대상: 2주 미사용 차감, 당일 취소 불가, 유효기간 만료, 주 단위 예약 오픈

### 10.4 동시성

- `ExecutorService` + `CountDownLatch`로 동시 요청을 만들고, **성공 건수가 정원과 정확히 같은지** 검증한다 (policies §8: 초과 예약 0건)
- 성공 건수만 보지 말고 **DB 실제 행 수**와 **차감 이력 건수**도 함께 확인한다
- 테스트 메서드에 `@Transactional`을 붙이지 않는다 (각 스레드가 별도 트랜잭션이어야 경쟁이 재현된다)

### 10.5 보고

- 테스트가 통과했다고 보고하기 전에 **실제로 실행**한다 (`./gradlew test`)
- 테스트를 쓰지 않기로 판단한 변경이 있으면, 완료 보고에 **그 이유를 한 줄로 밝힌다** (§10.0의 면제 항목인지)

## 11. Spring Boot 4 주의 (D-014)

학습 자료·기존 예제는 대부분 Boot 3 기준이다. **복붙 전에 확인한다.**

| 항목 | Boot 3 | **Boot 4 (이 프로젝트)** |
|---|---|---|
| Web 스타터 | `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| 테스트 스타터 | `spring-boot-starter-test` 하나 | 모듈별 `-test` (`spring-boot-starter-webmvc-test` 등) |
| Flyway | web 스타터에 딸려옴 | `spring-boot-starter-flyway` 별도 |
| Jackson | `com.fasterxml.jackson` | **Jackson 3 — `tools.jackson`** |
| Testcontainers | `org.testcontainers:postgresql` | `org.testcontainers:testcontainers-postgresql` (2.x) |
| MockMvc 자동설정 | `boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| springdoc | 2.x | **3.x** |

- `spring.datasource` / `spring.jpa` / `spring.flyway` / `spring.jackson` **설정 키는 3.x와 동일**하다
- **모르는 API는 추측하지 말고 확인한다**: ① context7 MCP로 해당 버전 문서 조회 → ② 버전은 `https://repo1.maven.org/maven2/<경로>/maven-metadata.xml` 로 실제 확인 → ③ `./gradlew compileKotlin`으로 검증
- 의존성을 추가할 때 **Boot 4 호환 라인인지** 먼저 확인한다. Boot BOM이 관리하는 것은 버전을 적지 않는다

## 11.5 코드 포맷 (ktlint)

포맷은 사람이 맞추지 않는다. **ktlint 가 유일한 기준**이고 스타일 규칙은 `.editorconfig` 에만 있다 (D-024).

```bash
./gradlew ktlintFormat   # 자동 수정 — 커밋 전에 이것만 돌리면 된다
./gradlew ktlintCheck    # 검사만 (build/check 에 이미 포함돼 있음)
```

- `./gradlew build` 는 포맷 위반이 있으면 **실패한다.** 실패하면 `ktlintFormat` 을 돌리고 다시 빌드한다
- 스타일에 대해 논쟁하거나 `build.gradle.kts` 에 규칙을 추가하지 않는다. 바꿔야 하면 `.editorconfig` 를 고치고 D 기록을 남긴다
- import 목록 **사이에 주석을 넣지 않는다** — 자동 정렬이 불가능해져 유일하게 수동으로 고쳐야 하는 위반이 된다.
  설명은 클래스 KDoc 에 쓴다
- 코드를 생성·수정한 작업의 마지막에 `ktlintFormat` → `build` 순서로 돌린다

## 12. 커밋

- Conventional Commits, 작업 단위로 잘게. 목적이 다른 변경을 한 커밋에 섞지 않는다
- API 변경 커밋에는 재생성한 `openapi.yaml`이 포함되어야 한다
- 설계 결정을 했으면 `decisions.md` 기록이 같은 PR에 있어야 한다
