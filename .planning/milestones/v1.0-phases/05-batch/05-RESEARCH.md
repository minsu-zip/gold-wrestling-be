# Phase 5: 배치 - Research

**Researched:** 2026-08-15
**Domain:** Spring Boot 4.1 `@Scheduled` 배치(2주 미사용 자동 차감) + 상태 기반 멱등 계산 + 기존 이용권/예약/회원 도메인과의 통합
**Confidence:** HIGH (Boot 4 스케줄링 API·기존 코드 정밀 조사) / MEDIUM (배치 트랜잭션 경계·테이블 스키마 설계 — 이 저장소 최초 사례라 실측 없이 관례 기반 권고)

## Summary

이 phase는 이 저장소에 **스케줄링 인프라를 처음 도입**한다. Spring Boot 4.1.0(Spring Framework 7.0.8)의
`@Scheduled`/`@EnableScheduling` API는 Boot 3와 달라진 것이 없다(context7로 실제 확인) — 학습 부담은
API 자체가 아니라 "판정은 회원 단위, 반영은 이용권 단위"(D-109)를 기존 조건부 UPDATE 관례(D-021) 위에서
어떻게 조립하느냐에 있다.

핵심은 D-106의 **상태 기반 부족분 계산**이다: 배치 로직 자체를 "오늘 차감할지 말지"가 아니라
`floor(경과일/14) − 이후 INACTIVITY 이력 건수 = 부족분`을 구하는 **순수 계산**으로 만들면, 스케줄러
트리거·수동 실행 API·재기동 중복이 전부 이 계산의 부수효과로 자연스럽게 멱등해진다. 이 계산에 필요한
"회원 단위 기준일"은 5종 후보(마지막 출석일[Phase 6 전까지 부재]·마지막 취소되지 않은 예약 수업일·
휴회 복귀일·SESSION_PASS 등록일·마지막 양(+) 수동 가감일)의 max이고, 각 후보는 회원 ID 목록을 넘겨
**벌크 조회**해야 한다 — 이 phase가 처리할 회원이 수백 명 규모이므로 회원별 개별 쿼리는 N+1이 된다.

기존 코드 조사 결과 두 가지 **스키마 선결 과제**가 확인됐다: (1) `pass_transaction`의 V8 CHECK
(`ck_pass_transaction_subject`)가 admin/member 중 정확히 하나를 요구하는데 배치는 시스템 주체라 둘
다 아니다 — CHECK 완화가 필요하다. (2) `member` 테이블에 상태 전환 시각 컬럼이 없다 — 휴회 복귀일
후보를 얻으려면 `AdminMemberService.changeStatus`의 ON_LEAVE→ACTIVE 분기에서 시각을 기록하는 새 컬럼이
필요하다. 두 가지 모두 V9 마이그레이션으로 해결 가능하고, 커밋된 V4·V8은 수정하지 않는다.

BATCH-03(만료 사용 불가)은 **구현물이 없다** — D-107 확정대로 D-064(`Pass.displayStatus`)의 조회 시점
계산과 Phase 4 예약 거부 경로가 이미 충족하므로, 이 phase는 검증 테스트만 작성한다.

**Primary recommendation:** `@EnableScheduling`을 여는 설정 클래스 하나 + 순수 계산(기준일·부족분)을
담은 도메인 서비스 + 회원별 차감 반영을 별도 `@Transactional` 메서드로 분리한 실행 서비스 + 스케줄러는
"트리거만" 하는 얇은 컴포넌트, 이렇게 4단으로 나눠 설계한다. 배치 대상 회원 조회부터 기준일 후보,
INACTIVITY 이력 카운트까지 전부 `IN (:memberIds)` 벌크 쿼리로 맵을 만들고, 인메모리에서 회원별로 조합한다.

## Architectural Responsibility Map

이 저장소는 단일 백엔드 모놀리스(Kotlin/Spring)라 FE/CDN 계층이 없다. 대신 **레이어(스케줄러 트리거 /
도메인 계산 / DB 반영 / 관리자 API)** 축으로 매핑한다.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| 배치 실행 트리거(매일 새벽) | 스케줄러(`@Scheduled`) | 관리자 API(수동 실행) | 자동 트리거는 cron, 사람이 개입하는 경로는 API — 둘 다 같은 실행 로직을 호출해야 중복 구현이 안 생긴다 |
| 회원 단위 기준일 판정(5종 max) | 도메인 서비스(순수 계산) | 리포지토리(벌크 조회) | 판정은 스프링 컨텍스트 없이 단위테스트 가능해야 한다(§10.0) — 계산과 조회를 분리 |
| 부족분 계산(D-106 상태 기반) | 도메인 서비스(순수 계산) | — | 오늘 날짜·기준일·기존 이력 건수만 입력받는 순수 함수 — Clock 주입 없이도 테스트 가능(파라미터로 today를 받음) |
| 차감 반영(이용권 1장, 부분 차감) | 리포지토리(조건부 UPDATE) | 도메인 서비스(대상 선택) | D-021 관례 — `PassRepository.adjustRemainingCount` 그대로 재사용 |
| `PassTransaction(INACTIVITY)` 이력 기록 | 실행 서비스(`@Transactional`) | — | D-020: 서비스 메서드 = 트랜잭션 단위, 회원 1명 = 트랜잭션 1개 권고(Pitfall 4 참조) |
| 배치 실행 이력(관측용) | 실행 서비스 + DB(새 테이블) | — | 멱등성의 근거가 아니라 순수 관측·복구 판단용(D-108) — 계산 로직과 결합하지 않는다 |
| 만료 처리(BATCH-03) | 기존 도메인 메서드(`Pass.displayStatus`) | 예약 서비스(수업날 기준 거부) | **새 구현 없음** — D-107. 이 phase는 검증 테스트만 추가 |
| 관리자 수동 실행 API | 컨트롤러(`/api/admin/**`) | 실행 서비스 | 기존 `SecurityConfig`의 ADMIN 규칙 재사용, 수정 불필요 |

## User Constraints (from CONTEXT.md)

<user_constraints>

### Locked Decisions

**1. 미사용 판정 기준일 (D-105, D-027 확장)**
- 판정은 회원 단위, 기준일 = 다음 5종 중 가장 최근 날짜:
  1. 마지막 출석일 (Phase 6 전까지 부재)
  2. 마지막 취소되지 않은 예약의 수업일 — 수업 종류(SESSION/LESSON) 무관하게 인정
  3. `ON_LEAVE` → `ACTIVE` 복귀일 — 복귀하면 2주 유예가 새로 시작
  4. `SESSION_PASS` 등록일(`pass.created_at`, 시작일 아님 — 과거 시작일 등록(D-055)이 소급 차감으로 이어지지 않도록)
  5. 마지막 양(+) 수동 가감일(`ADMIN_ADJUST`) — 충전 시점부터 2주 유예가 새로 시작
- 원리: "차감 제외 기간에는 부채가 쌓이지 않는다" — 복귀일(휴회 제외 기간)과 +가감일(잔여 0 제외 기간)이 같은 원리로 시계를 리셋한다
- 복귀일은 휴회 기간 테이블 대신 회원 상태 전환 시각 기록(새 마이그레이션, 컬럼 설계는 재량)으로 구현. 과거 복귀 이력은 데이터가 없으므로 후보에서 자연 제외 — 의도된 동작
- 수용한 트레이드오프: 휴회를 짧게 토글하면 시계가 리셋되는 관대함 — 휴회는 관리자만 설정 가능하므로 악용 위험 낮음

**2. 캐치업 = 멱등성 (D-106)**
- 배치는 실행 횟수 기반이 아니라 상태 기반: "현재 존재해야 할 차감 수(`floor(기준일→오늘 경과일 / 14)`) − 기준일 이후 실제 `INACTIVITY` 이력 수 = 부족분"을 계산해 부족분만 차감한다
- 같은 날 중복 실행·수동 실행·재기동 중복이 전부 자연 멱등이고, 배치가 며칠 중단돼도 다음 실행이 밀린 주기를 몰아서 차감한다 (policies "4주 미사용 = 2회"와 정합)
- `INACTIVITY` 이력 수는 건수(이벤트 수)로 센다 — 부분 차감(0.5) 1건도 1회로 계산

**3. 만료 처리 (D-107)**
- `EXPIRED` 영속화를 만들지 않는다 — 만료는 D-064 조회 시점 계산(`displayStatus`)이 유일한 진실 원천으로 유지되고, Phase 4 예약 경로가 수업날 기준으로 이미 거부한다
- 영속화하면 진실 원천이 둘이 되고, 유효기간 수정으로 만료 해제(D-056) 시 상태 되돌리기가 따라온다
- BATCH-03은 "만료 이용권 예약 불가 + `INACTIVITY` 대상 제외"를 검증 테스트로 실증해 충족한다

**4. 실행·운영 (D-108)**
- 앱 내 `@Scheduled`(cron, `Asia/Seoul`, 매일 새벽) + 배치 실행 이력 테이블(시각·처리 건수·결과) + 관리자 수동 실행 API 1개 (D-106 덕에 중복 실행 안전)
- ShedLock 등 분산 락은 일부러 쓰지 않는다 — 단일 EC2 인스턴스 전제. 다중 인스턴스·외부 트리거는 M7에서 재검토
- 실행 이력은 관측·복구 판단용이며 멱등성의 근거가 아니다 (멱등성은 D-106 상태 기반 계산이 담당)

**A. 판정 단위와 차감 대상 (D-109)**
- 판정(기준일·부족분)은 회원 단위, 차감 1회는 한 장에서: 차감 가능한 `SESSION_PASS`(취소·만료 아님, 잔여 > 0) 중 유효기간 만료가 가장 임박한 한 장 (D-091 선택 규칙 준용 — `end_date` 오름차순, 동률 `id` 오름차순)
- 부족분이 여러 회면 회당 대상을 재선택한다 (앞 장이 0이 되면 다음 장으로)

**B. 부분 차감 (D-109)**
- 잔여가 차감량(1)보다 적으면 잔여만큼만 차감한다 (0.5 → 0) — "잔여 0 제외" 예외와 연속되는 규칙
- 다른 장으로 부족분을 이월하지 않는다 — 합산 금지(D-091)와 일관. 한 차감 이벤트는 한 장에서만 일어난다

### Claude's Discretion

- `PassTransaction` 시스템 주체 표현 — V8 CHECK(`ck_pass_transaction_subject`)가 admin/member 중 정확히 하나를 요구하는데 배치는 둘 다 아니다. 새 마이그레이션으로 CHECK 완화·시스템 주체 표현을 설계한다 (커밋된 V8 수정 금지 — 새 버전 추가)
- 회원 상태 전환 시각의 컬럼 설계 (예: `member.status_changed_at` 단일 컬럼 vs 복귀 전용)
- 배치 트랜잭션 경계 (회원 단위 개별 트랜잭션 vs 전체 일괄 — 부분 실패 시 나머지 진행 여부)
- 실행 이력 테이블 스키마·네이밍 (glossary 등록 후 사용 — 금지어 확인)
- `@Scheduled` cron 시각(새벽 몇 시), 수동 실행 API 경로·응답 DTO, 에러코드
- 차감 시 `adjustRemainingCount` 조건부 UPDATE가 0행이면(경쟁 패배) 해당 회원 스킵 — 다음 실행이 상태 기반으로 자연 보정 (Phase 4의 "재시도 없이 실패" 재량과 같은 원리)

### Deferred Ideas (OUT OF SCOPE)

- 외부 트리거(시스템 cron·EventBridge 등 앱 밖 스케줄러) — M7에서 재검토
- 배치 이벤트 알림(`INACTIVITY` 차감·만료를 관리자 알림으로) — 요구사항(NOTIF-01~03)에 없음. 배치 실행 이력 테이블이 관측 수요를 우선 흡수. 필요가 확인되면 Phase 6 알림 체계에 추가
- 다중 인스턴스 대비 분산 락(ShedLock) — 단일 EC2 전제가 깨질 때 재검토
- 휴회 토글로 인한 유예 리셋 관대함 보정 — 악용이 실제 관찰되면 휴회 기간 기록 방식 재논의

</user_constraints>

## Phase Requirements

<phase_requirements>

| ID | Description | Research Support |
|----|-------------|------------------|
| BATCH-01 | `SESSION_PASS` 2주 미사용 시 1회 자동 차감, 이후 2주마다 반복(`INACTIVITY`) | Pattern 2(기준일 벌크 조회)·Pattern 3(D-106 부족분 계산)·Code Example 1·2 |
| BATCH-02 | 차감 예외 — `ON_LEAVE` 기간, 잔여 0, 유효기간 만료 이용권 제외 | Pattern 2의 후보 회원 필터 쿼리, Pitfall 2(ON_LEAVE 현재 상태만 검사) |
| BATCH-03 | 유효기간(등록일+1년) 만료 이용권의 사용 불가 | D-107 확정대로 **구현 없음** — 기존 `Pass.displayStatus`(D-064)·`PassRepository.findDeductionCandidates`(D-091, `endDate >= classDate`)가 이미 충족. Validation Architecture의 검증 테스트 매핑 참조 |
| BATCH-04 | 배치는 멱등하다 — 같은 날 중복 실행돼도 이중 차감 0건 | Pattern 3(D-106 상태 기반 계산)·Common Pitfall 1(캐치업 재선택) |

</phase_requirements>

## Standard Stack

### Core

이 phase는 **새 외부 의존성을 추가하지 않는다.** 스케줄링은 Spring Framework에 내장된 기능이고, 이미
`build.gradle.kts`에 있는 `spring-boot-starter-webmvc`(Boot 4 BOM이 관리)로 충분하다.

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Framework `@Scheduled`/`@EnableScheduling` | 7.0.8 (Boot 4.1.0 BOM 관리) | cron 기반 배치 트리거 | 이 프로젝트에 이미 포함된 `spring-context` 모듈 기능, 추가 설치 불필요. [VERIFIED: Boot 4.1.0 BOM `spring-boot-dependencies-4.1.0.pom`의 `spring-framework.version=7.0.8`, `maven-metadata.xml`로 실제 조회] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| — | — | — | 이번 phase는 지원 라이브러리도 추가하지 않는다 (ShedLock은 D-108이 명시적으로 기각) |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| 앱 내 `@Scheduled` | Spring Batch 프레임워크 | 청크·재시작·Job 메타데이터 테이블 등 이 규모(하루 1회, 회원 수백 명)에 과잉 — D-108 기각 대안 |
| 앱 내 `@Scheduled` | 외부 트리거(cron·EventBridge → 관리자 API 호출) | 새 인프라 필요, M7로 이연(D-108) |
| 상태 기반 부족분 계산 | ShedLock 분산 락 | 단일 인스턴스라 락으로 막을 동시 실행 시나리오가 없다 — D-108 기각 대안, 다중 인스턴스 전환 시 재검토 |

**Installation:** 없음 — 기존 `build.gradle.kts` 의존성으로 충분하다.

**Version verification:**
```bash
curl -s https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.0/spring-boot-dependencies-4.1.0.pom | grep spring-framework.version
# → <spring-framework.version>7.0.8</spring-framework.version>  (2026-08-15 실제 조회)
curl -s https://repo1.maven.org/maven2/org/springframework/spring-context/maven-metadata.xml | grep -o '<release>[^<]*'
# → <release>7.0.8  (독립 배포 버전도 동일선상, 2026-08-15 조회)
```

## Package Legitimacy Audit

**해당 없음 — 이 phase는 신규 외부 패키지를 추가하지 않는다.** `@Scheduled`/`@EnableScheduling`은
이미 클래스패스에 있는 `org.springframework:spring-context`(Boot BOM 관리) 소속이라 `slopcheck`/레지스트리
검증 대상이 아니다. 새 의존성이 필요해지면(예: 향후 알림 연동) 그 시점에 별도 감사를 수행한다.

## Architecture Patterns

### System Architecture Diagram

```
[매일 새벽 cron]                    [관리자 수동 실행 API]
      │                                     │
      ▼                                     ▼
InactivityBatchScheduler          AdminBatchController
(@Scheduled, 트리거만)              (POST /api/admin/batch/inactivity-runs)
      │                                     │
      └───────────────┬─────────────────────┘
                       ▼
         InactivityBatchRunner (@Transactional 없음 — 회원별 루프만)
                       │
         ① 대상 회원 조회 (SESSION_PASS ACTIVE·미만료·잔여>0 보유 & 현재 ON_LEAVE 아님)
                       │
         ② 5종 기준일 후보 벌크 조회 (memberIds IN절, 5개 쿼리)
                       │      - 마지막 취소되지 않은 예약 수업일 MAX (Reservation)
                       │      - 휴회→활성 복귀일 (Member.returnedFromLeaveAt)
                       │      - SESSION_PASS 등록일 MAX (Pass.createdAt)
                       │      - 마지막 양(+) ADMIN_ADJUST일 MAX (PassTransaction)
                       │      - (마지막 출석일 — Phase 6 전까지 자연 부재)
                       │
         ③ 회원별 순수 계산 (InactivityDueDateCalculator, 스프링 없는 단위테스트 대상)
                       │      기준일 = max(후보들)
                       │      부족분 = floor((오늘-기준일)/14) - 기준일 이후 INACTIVITY 건수
                       │
                       ▼ (부족분 > 0인 회원만)
         InactivityDeductionService.deductOnce(memberId)  ← 회원 1명 = @Transactional 1개
                       │
         ④ 차감 후보 재조회 (endDate asc, id asc, remainingCount>0) — 회당 재선택(D-109)
         ⑤ min(1, remaining) 만큼 adjustRemainingCount 조건부 UPDATE
         ⑥ 0행이면 스킵(경쟁 패배 — 다음 실행이 보정), 아니면 PassTransaction(INACTIVITY) 저장
                       │
                       ▼
         BatchExecution 이력 1건 저장 (처리 대상 수·차감 건수·SUCCESS/PARTIAL_FAILURE, 트리거 종류)
```

### Recommended Project Structure

```
src/main/kotlin/com/goldwrestling/batch/
├── InactivityBatchScheduler.kt        # @Scheduled 트리거 전용 — 로직 없음, InactivityBatchRunner만 호출
├── InactivityBatchRunner.kt           # 회원별 루프, @Transactional 없음(Pitfall 3 참조)
├── InactivityDueDateCalculator.kt     # 기준일 max·부족분 계산 순수 함수 (companion object, Clock 불필요 — today 파라미터)
├── InactivityDeductionService.kt      # 회원 1명 차감 반영 — @Transactional 단위 (D-020)
├── BatchExecution.kt                  # 실행 이력 엔티티
├── BatchExecutionStatus.kt            # SUCCESS / PARTIAL_FAILURE 등 enum (glossary 등록 후)
├── BatchExecutionRepository.kt
├── AdminBatchController.kt            # 관리자 수동 실행 API
└── dto/
    └── BatchExecutionResponse.kt
```

- **`batch` 패키지가 `pass`·`reservation`·`member`를 참조하는 단방향 의존**(D-018 기능별 패키지) — 반대
  방향 의존(예: `pass`가 `batch`를 참조)이 생기면 설계가 잘못된 것이다.
- `PassRepository`·`ReservationRepository`에 배치 전용 벌크 조회 메서드를 추가하는 것은 자연스럽다
  (기존 `findDeductionCandidates`도 `pass` 패키지에 있다) — `batch` 패키지가 아니라 소유 패키지에 둔다.

### Pattern 1: 스케줄러는 트리거만, 로직은 서비스로 분리

**What:** `@Scheduled` 메서드 자체에는 조건문·트랜잭션·쿼리를 넣지 않고, 별도 서비스 메서드 1개를
호출만 한다.
**When to use:** 이 저장소 스케줄링 도입 시 항상 — 스케줄러 컴포넌트는 스프링 컨텍스트 없이 테스트할
수 없으므로(트리거 자체가 프레임워크 기능), 테스트 가능한 로직은 전부 평범한 `@Service`로 내린다.
**Example:**
```kotlin
// Source: Context7 spring-framework reference "Enable Scheduling and Async Annotations" +
// 이 프로젝트 conventions §1 config/ 배치 규약 적용
@Configuration
@EnableScheduling
class SchedulingConfig

@Component
class InactivityBatchScheduler(
    private val runner: InactivityBatchRunner,
) {
    // cron 6필드: 초 분 시 일 월 요일. zone은 SEOUL_ZONE_ID 상수 재사용(GoldWrestlingApplication.kt).
    @Scheduled(cron = "0 0 4 * * *", zone = SEOUL_ZONE_ID)
    fun runDaily() {
        runner.run(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)
    }
}
```
`zone` 속성은 `@Scheduled`가 직접 지원한다(Context7로 확인) — cron 표현식을 서버 JVM 기본 시간대와
별개로 `Asia/Seoul`로 고정할 수 있다. `GoldWrestlingApplication.kt`가 이미 JVM 기본 시간대를
`Asia/Seoul`로 맞추므로(`TimeZone.setDefault`) `zone` 속성은 방어적 이중 명시지만, 명시적으로 쓰는 것이
conventions §5("시간대는 Asia/Seoul 명시")와 일치한다.

### Pattern 2: 회원별 기준일 후보는 전부 `IN (:memberIds)` 벌크 조회로

**What:** 5종 후보 각각을 "대상 회원 전체"에 대해 한 번의 그룹 쿼리로 가져와 `Map<Long, LocalDate>`로
만들고, 회원별 조합은 인메모리에서 한다.
**When to use:** 배치가 처리할 회원이 한 자리 수가 아닌 모든 배치 조회에 적용 — 이 저장소의 기존
N+1 회피 관례(`ReservationRepository.findAllByClassSessionIdInAndStatusWithMember` 등)와 같은 원리다.
**Example:**
```kotlin
// Source: 이 저장소 관례(ReservationRepository의 IN절 벌크 조회 패턴)를 배치 문맥에 적용

// 1) 마지막 취소되지 않은 예약 수업일 — 회원별 MAX
@Query(
    "select r.member.id, max(r.classDate) from Reservation r " +
        "where r.member.id in :memberIds and r.status = com.goldwrestling.reservation.ReservationStatus.ACTIVE " +
        "group by r.member.id",
)
fun findLastActiveReservationDates(
    @Param("memberIds") memberIds: Collection<Long>,
): List<Array<Any>>  // [memberId: Long, maxClassDate: LocalDate]

// 2) SESSION_PASS 등록일 — 회원의 "현재 유효한(만료 안 된)" 횟수권 중 MAX(등록일)
//    [RESOLVED] 여러 장 보유 시 범위는 "차감 가능한 장(취소·만료 아님, 잔여 > 0) 중 최신 created_at"
//    으로 확정 — CONTEXT §C(2026-08-15), D-105 보강. 아래 초안 쿼리에 잔여 > 0 조건을 추가해 구현할 것
@Query(
    "select p.member.id, max(p.createdAt) from Pass p " +
        "where p.member.id in :memberIds and p.type = com.goldwrestling.pass.PassType.SESSION_PASS " +
        "and p.status = com.goldwrestling.pass.PassStatus.ACTIVE and p.endDate >= :today " +
        "group by p.member.id",
)
fun findLastSessionPassRegistrationDates(
    @Param("memberIds") memberIds: Collection<Long>,
    @Param("today") today: LocalDate,
): List<Array<Any>>

// 3) 마지막 양(+) ADMIN_ADJUST일 — pass_transaction을 pass 경유로 회원에 연결
@Query(
    "select pt.pass.member.id, max(pt.occurredAt) from PassTransaction pt " +
        "where pt.pass.member.id in :memberIds and pt.reason = com.goldwrestling.pass.TransactionReason.ADMIN_ADJUST " +
        "and pt.amount > 0 group by pt.pass.member.id",
)
fun findLastPositiveAdjustDates(
    @Param("memberIds") memberIds: Collection<Long>,
): List<Array<Any>>
```
`Array<Any>` 반환은 이 저장소에 선례가 없는 패턴이다 — 기존 관례(`PassRepository.findDeductionCandidates`
등)는 전부 엔티티 리스트를 반환한다. Spring Data JPA는 `interface` 프로젝션(`MemberDateProjection { fun
getMemberId(): Long; fun getMaxDate(): LocalDate? }`)도 지원하므로, 계획 단계에서 `Array<Any>` 캐스팅
대신 프로젝션 인터페이스를 쓰는 편이 타입 안전하다 — 이 저장소 최초 프로젝션 사용이라 **계획 시
`verify-boot4-api` 절차로 Spring Data JPA 4.1.0의 인터페이스 프로젝션 문법을 재확인**할 것.

### Pattern 3: D-106 상태 기반 부족분 계산은 순수 함수로 분리

**What:** "기준일·오늘·기존 INACTIVITY 이력 발생일 리스트"만 입력받아 "부족분(Int)"을 반환하는
함수 — Spring, Clock, DB 접근이 전혀 없다.
**When to use:** 캐치업·멱등성이 이 함수 하나의 정확성에 전부 의존하므로, `add-domain-test` 스킬의
단위테스트 필수 규칙(§10.0 "엔티티 메서드/도메인 규칙")이 정확히 이 함수를 겨냥한다.
**Example:**
```kotlin
// Source: policies §4.3 + D-106 문구를 그대로 코드화
object InactivityDueDateCalculator {
    private const val GRACE_PERIOD_DAYS = 14L

    /** 기준일부터 오늘까지 "존재해야 할" INACTIVITY 차감 횟수. */
    fun expectedDeductionCount(dueDate: LocalDate, today: LocalDate): Int {
        val elapsedDays = ChronoUnit.DAYS.between(dueDate, today)
        if (elapsedDays < GRACE_PERIOD_DAYS) return 0
        return (elapsedDays / GRACE_PERIOD_DAYS).toInt()
    }

    /** 부족분 = 존재해야 할 횟수 - 기준일 이후 실제 발생한 INACTIVITY 건수(이벤트 수, 부분차감도 1건). */
    fun shortfall(
        dueDate: LocalDate,
        today: LocalDate,
        inactivityEventDatesOnOrAfterDueDate: Int,
    ): Int = (expectedDeductionCount(dueDate, today) - inactivityEventDatesOnOrAfterDueDate).coerceAtLeast(0)
}
```
`coerceAtLeast(0)`은 방어적 하한이다 — 정상 흐름에서는 이력 건수가 기대 횟수를 넘어설 수 없지만
(매 실행이 부족분만 채우므로), 수동 데이터 조작·마이그레이션 오류 같은 비정상 상황에서 음수 부족분이
"차감을 취소"하는 의미로 오해되지 않게 막는다.

### Pattern 4: 회원 1명 = 트랜잭션 1개 (부분 실패 격리)

**What:** 배치 루프를 도는 상위 메서드는 `@Transactional`을 붙이지 않고, 회원별 차감 반영 메서드만
`@Transactional`을 붙인 **별도 스프링 빈**의 메서드로 호출한다.
**When to use:** 이 phase의 배치 실행 전체 — 이유는 Pitfall 3 참조.

### Pattern 5: 배치 실행 이력은 계산 결과의 "부산물"로만 저장

**What:** `BatchExecution` 저장은 전체 실행이 끝난 뒤(또는 예외 발생 시 catch 블록에서) 요약 정보
1건만 쓴다 — 회원별 처리 상세를 담지 않는다(멱등성 근거가 아니므로 D-108).
**When to use:** 실행 이력 테이블 저장 시점 전부.

### Anti-Patterns to Avoid

- **배치가 "오늘 이미 실행했는가"를 확인하고 스킵하는 가드**: D-106이 명시적으로 기각한 대안이다 —
  이력 유실 시 이중 차감이 나고, 배치가 며칠 밀렸을 때 캐치업이 안 된다. 실행 이력 테이블을 멱등성
  판단에 절대 쓰지 않는다.
- **이용권별로 INACTIVITY 판정을 반복**: D-109가 명시적으로 기각 — 판정은 회원 단위다. 여러 장 보유
  회원을 장 수만큼 중복 차감하면 정책 위반이다.
- **부족분을 한 이용권에서 몰아서 차감(합산)**: D-091·D-109가 금지 — 회당 1장, 회당 재선택.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| cron 기반 매일 실행 | 자체 `Thread.sleep` 루프·타이머 | Spring `@Scheduled(cron=...)` | 이미 클래스패스에 있고, `zone` 속성으로 Asia/Seoul을 명시 지원한다(Context7 확인) |
| 회원별 병렬·순차 처리 | 자체 스레드 풀 관리 | 순차 루프(트랜잭션 1개=회원 1명) | 단일 인스턴스·회원 수백 명 규모에서 병렬화 이득보다 부분 실패 진단 복잡도가 더 크다. 필요해지면 `spring.task.scheduling.pool` 조정으로 대응(Context7 확인 — 기본 풀 크기 1) |
| 이용권 차감 원자성 | 애플리케이션 레벨 체크 후 저장 | `PassRepository.adjustRemainingCount`(기존 조건부 UPDATE) | D-021 — 이미 검증된 경로, Phase 4가 예약 차감에 그대로 재사용한 선례가 있다 |
| 만료 판정 | 배치 전용 만료 계산 로직 | `Pass.displayStatus(today)`/`isExpired` | D-064·D-107 — 진실 원천을 하나로 유지 |

**Key insight:** 이 phase에서 진짜로 새로 짜야 하는 코드는 "기준일 계산"과 "부족분 계산" 두 순수
함수, 그리고 이를 위한 벌크 조회 5~6개뿐이다. 차감 반영·트랜잭션 경계·조건부 갱신은 전부 Phase 3~4가
만든 경로의 재사용이다.

## Common Pitfalls

### Pitfall 1: 캐치업 루프에서 차감 대상을 한 번만 선택

**What goes wrong:** 부족분이 2 이상인 회원에서 "차감 대상 조회 → 2회 차감"을 한 번의 후보 조회로
처리하면, 첫 번째 차감으로 그 이용권 잔여가 0이 됐는데(부분 차감) 두 번째 차감도 같은(이미 소진된)
이용권을 대상으로 시도해 조건부 UPDATE가 계속 0행을 반환하거나, 다음으로 만료 임박한 장을 놓친다.
**Why it happens:** D-109 "회당 대상을 재선택한다"를 "한 번 조회한 후보 리스트에서 순서대로 소비"로
잘못 구현하면, 조회 시점 잔여 스냅샷이 첫 차감 이후 실제 DB 상태와 어긋난다.
**How to avoid:** 부족분만큼 루프를 돌면서 **매 회차마다** `findActiveUsableSessionPasses(memberId,
today)`를 다시 호출한다(이미 Phase 4가 이 관례를 세웠다 — `ReservationLedgerSupport`가 조건부 UPDATE
후 항상 재조회한다, RESEARCH Pitfall 4).
**Warning signs:** 부족분 2 이상인 테스트 케이스에서만 실패하고 1회 차감 테스트는 통과한다면 이 함정이다.

### Pitfall 2: ON_LEAVE 예외를 "현재 상태"로만 판정하고 과거 휴회 기간을 무시하는 것은 의도된 동작

**What goes wrong:** "휴회 기간에는 차감하지 않는다"는 문장을 "과거 휴회했던 기간의 경과일을
부족분 계산에서 빼야 한다"로 오해하면, 과거 휴회 이력을 저장하는 테이블(D-105가 명시적으로 기각한
"휴회 기간 테이블")을 다시 만들게 된다.
**Why it happens:** D-105는 "휴회 복귀일이 기준일을 리셋"하는 것으로 이 문제를 이미 해결했다 —
복귀일 자체가 그 시점 이전의 모든 경과일(휴회 기간 포함)을 부족분 계산에서 제외시킨다. 별도로
휴회 기간을 빼는 로직을 추가하면 이중 차감 방지가 두 곳(리셋 기준일 + 기간 차감)으로 흩어진다.
**How to avoid:** "예외(차감하지 않음)"는 **오직 두 곳**에서 구현한다 — ① 대상 회원 조회 쿼리에서
`member.status <> ON_LEAVE`로 걸러 현재 휴회 중인 회원 전체를 스킵, ② 기준일 후보 3번(복귀일)이
과거 휴회 기간을 시계 리셋으로 흡수. 세 번째 메커니즘을 만들지 않는다.
**Warning signs:** "휴회 일수를 빼서 계산"하는 코드나 컬럼이 등장하면 이 함정이다.

### Pitfall 3: 배치 루프 상위 메서드에 `@Transactional`을 붙이는 것 (self-invocation + 전체 롤백)

**What goes wrong:** 회원 300명을 처리하는 루프 전체를 하나의 `@Transactional` 메서드로 감싸면, (1)
같은 클래스 안에서 `this.deductOnce(memberId)`를 호출하는 self-invocation은 프록시를 우회해 새
트랜잭션 경계가 생기지 않고(스프링 AOP의 알려진 제약), (2) 회원 150번째에서 예외가 나면 이미 처리한
149명의 차감까지 전부 롤백된다 — "부분 실패 시 나머지는 그대로 반영"이 요구되는 배치 문맥과 정반대다.
**Why it happens:** D-020("서비스 메서드 = 트랜잭션 단위")을 "배치 서비스 전체 = 트랜잭션 하나"로
오독하면 이 문제가 생긴다. D-020의 취지는 "차감과 이력 기록이 한 트랜잭션에서 원자적으로 끝난다"이지
"배치 전체가 원자적이어야 한다"가 아니다.
**How to avoid:** 상위 루프(`InactivityBatchRunner`)는 `@Transactional`을 붙이지 않고, 회원별 차감
반영(`InactivityDeductionService.deductOnce`)만 **별도 스프링 빈**의 `@Transactional` 메서드로
분리한다 — 이러면 자연스럽게 서로 다른 빈 간 호출이 되어 self-invocation 문제가 없고, 회원 1명의
예외가 그 회원의 트랜잭션만 롤백한다. 상위 루프는 `try-catch`로 회원별 예외를 흡수하고 실행 이력에
집계한다.
**Warning signs:** 배치 서비스 클래스 전체에 `@Transactional(readOnly = true)` 클래스 레벨 애노테이션이
붙어 있고 회원 루프가 같은 클래스 안의 `private`/`internal` 메서드를 호출한다면 이 함정이다.

### Pitfall 4: `pass.created_at`을 시작일(`start_date`)로 착각

**What goes wrong:** D-055로 과거 시작일 등록이 가능해졌으므로, 기준일 후보 4번에 `pass.startDate`를
쓰면 "2개월 전 시작일로 오늘 등록한 이용권"이 등록 즉시 소급 차감 대상이 된다(경과일이 이미
14일을 넘음).
**Why it happens:** `Pass.kt`에 `startDate`와 `createdAt` 두 컬럼이 모두 있고, 도메인 감각상
"시작일"이 더 자연스러워 보인다.
**How to avoid:** 기준일 후보는 반드시 `pass.createdAt`(D-105가 명시)을 쓴다. `Pass.kt` KDoc
27번째 줄이 이미 이 구분("`start_date`와 `created_at`(등록일)이 별도 존재 — 기준일은 `created_at`")을
명시해 뒀다.
**Warning signs:** 테스트에서 "과거 시작일로 오늘 등록한 SESSION_PASS가 즉시 INACTIVITY 대상이
아니다"를 검증하지 않으면 이 함정을 놓친다.

### Pitfall 5: V8 CHECK를 완화하지 않고 `admin`/`member` 중 하나를 억지로 채움

**What goes wrong:** 배치 주체를 표현할 새 컬럼/완화 없이 "시스템 관리자 계정을 만들어 admin으로
채운다" 같은 우회를 하면, `PassTransaction.admin`의 의미("이 행위를 한 실제 관리자")가 오염되고
감사 추적에서 배치 차감과 사람의 관리자 조작이 구분되지 않는다.
**Why it happens:** CHECK 제약을 건드리지 않고 기존 nullable 쌍에 값을 끼워 맞추는 것이 마이그레이션
없이 끝낼 수 있어 보여 유혹적이다.
**How to avoid:** V9로 `ck_pass_transaction_subject`를 완화한다. 가장 단순한 완화는 "정확히 하나"
(exactly-one)에서 "**최대 하나**"(at-most-one)로 바꾸는 것이다 —
`CHECK (NOT (admin_id IS NOT NULL AND member_id IS NOT NULL))`. 이러면 기존 행(둘 중 하나만 채움)은
그대로 통과하고, 배치 행(`admin_id`·`member_id` 둘 다 NULL)도 허용된다. 대안으로 `actor_type`
판별 컬럼(`MEMBER`/`ADMIN`/`SYSTEM`)을 추가하는 설계도 있으나, 기존 컬럼 의미(V8 KDoc "이 이력을
발생시킨 행위자")를 그대로 유지하면서 최소 변경으로 끝내려면 at-most-one 완화가 더 단순하다 —
**계획 단계에서 두 설계를 비교해 확정**한다(Claude's Discretion 항목).
**Warning signs:** 마이그레이션에서 CHECK를 건드리지 않고 `PassTransaction` 생성 코드에서
`admin = systemAdminAccount`처럼 특정 계정을 대입하면 이 함정이다.

## Code Examples

### 회원 상태 전환 시각 컬럼 — 복귀 전용 단일 목적 컬럼 권고

```kotlin
// Source: 이 저장소 관례 — Reservation.canceledAt처럼 "그 사건이 일어난 시각만" 기록하는
// 단일 목적 컬럼 패턴을 따른다. 범용 status_changed_at 단일 컬럼은 "이전 상태가 ON_LEAVE였는가"를
// 사후에 알 수 없어(마지막 전환 시각만 남고 이전 상태 정보가 없음) 배치 판정에 쓸 수 없다 —
// PENDING→ACTIVE(승인)·INACTIVE→ACTIVE(재활성화) 전이도 같은 컬럼을 덮어써 ON_LEAVE 복귀와
// 구분되지 않기 때문이다. 대신 "ON_LEAVE→ACTIVE 전이 시에만" 갱신되는 전용 컬럼을 쓴다.

@Column(name = "returned_from_leave_at")
var returnedFromLeaveAt: OffsetDateTime? = null

// AdminMemberService.changeStatus 안, member.status = newStatus 대입 직후:
if (member.status == MemberStatus.ON_LEAVE && newStatus == MemberStatus.ACTIVE) {
    member.returnedFromLeaveAt = OffsetDateTime.now(clock)
}
```
이 설계의 트레이드오프: "복귀"만 기록하고 다른 전환(승인·재활성화·휴회 시작)은 기록하지 않는다 —
D-105가 요구하는 후보는 정확히 이 한 가지뿐이므로 범위를 넓히지 않는다. 향후 다른 phase가 "모든
상태 전환 이력"이 필요해지면 별도 이력 테이블(`PassPeriodChange`와 같은 전용 테이블 패턴, D-057)을
그때 추가한다 — 지금 범용 컬럼을 만들어 두는 것은 과잉 설계다.

### 배치 실행 이력 테이블 — 마이그레이션 스케치

```sql
-- V9: pass_transaction 시스템 주체 허용 + member 복귀 시각 + 배치 실행 이력 (D-105~108)
-- 이미 커밋된 V4·V8은 수정하지 않는다.

ALTER TABLE pass_transaction DROP CONSTRAINT ck_pass_transaction_subject;
ALTER TABLE pass_transaction ADD CONSTRAINT ck_pass_transaction_subject CHECK (
    NOT (admin_id IS NOT NULL AND member_id IS NOT NULL)
);
-- 완화 이유: 배치(INACTIVITY)는 admin도 member도 아닌 시스템 주체다 — 기존 "정확히 하나"에서
-- "최대 하나"로 넓혀 배치 행(둘 다 NULL)을 허용한다. 기존 행은 전부 하나만 채워져 있어 그대로 통과.

ALTER TABLE member ADD COLUMN returned_from_leave_at TIMESTAMPTZ;
-- ON_LEAVE → ACTIVE 전이 시에만 기록(다른 상태 전이는 갱신하지 않는다, D-105 기준일 후보 ③ 전용).

CREATE TABLE batch_execution (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    trigger_type VARCHAR(20) NOT NULL,          -- SCHEDULED / MANUAL
    triggered_by_admin_id BIGINT REFERENCES admin (id),  -- MANUAL일 때만 non-null
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    processed_member_count INT NOT NULL,
    deducted_count INT NOT NULL,
    status VARCHAR(20) NOT NULL,                -- SUCCESS / PARTIAL_FAILURE
    error_summary VARCHAR(1000)
);

CREATE INDEX idx_batch_execution_started_at ON batch_execution (started_at);
```
`triggered_by_admin_id`가 nullable인 이유는 `RefreshToken`(D-037)·`PassTransaction`(V8)과 같은
"nullable FK + 값 있으면 그 뜻" 관례를 그대로 따른 것이다 — SCHEDULED 실행은 NULL, MANUAL 실행만
관리자를 기록한다. **CHECK로 강제할지(trigger_type=MANUAL ↔ triggered_by_admin_id NOT NULL)는
계획 단계에서 결정** — 이 저장소의 다른 "행위자 있음/없음" 쌍(V8, refresh_token)은 모두 CHECK로
강제하는 관례이므로 일관성을 위해 강제하는 쪽을 권고한다.

### 부족분 계산 후 회당 재선택 차감 루프

```kotlin
// Source: ReservationLedgerSupport.createReservation의 "조건부 UPDATE 후 재조회" 관례(D-091)를
// 배치의 "회당 재선택"(D-109)에 적용

@Transactional
fun deductOnce(memberId: Long): Boolean {
    val today = LocalDate.now(clock)
    val candidates = passRepository.findActiveUsableSessionPasses(memberId, today) // endDate>=today, remaining>0, order by endDate asc,id asc
    val candidate = candidates.firstOrNull() ?: return false // 잔여 0/만료 등으로 대상 소진 — 스킵

    val deductAmount = minOf(ONE_SESSION, candidate.remainingCount!!)
    val updated = passRepository.adjustRemainingCount(candidate.id!!, deductAmount.negate())
    if (updated == 0) return false // 경쟁 패배 — 다음 실행이 상태 기반으로 자연 보정(discretion 항목)

    val refreshedPass = passRepository.findById(candidate.id!!).orElseThrow { PassNotFoundException(candidate.id!!) }
    passTransactionRepository.save(
        PassTransaction(
            pass = refreshedPass,
            amount = deductAmount.negate(),
            reason = TransactionReason.INACTIVITY,
            note = null,
            admin = null,
            member = null, // 시스템 주체 — V9 완화된 CHECK가 허용
            occurredAt = OffsetDateTime.now(clock),
        ),
    )
    return true
}

// 상위 루프(별도 빈, @Transactional 없음):
repeat(shortfall) {
    val deducted = inactivityDeductionService.deductOnce(memberId)
    if (!deducted) return@repeat // 대상 소진 — 남은 부족분은 다음 실행이 이어받음(캐치업)
}
```

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | 회원이 SESSION_PASS를 여러 장 보유할 때, 기준일 후보 ④(등록일)는 "현재 유효(미만료·ACTIVE)한 SESSION_PASS 중 가장 최근 등록일"의 MAX다 — D-105 원문이 "회원 단위 판정"과 "가장 최근 날짜" 원칙을 명시하지만 다장 보유 시 어느 등록일인지 문자 그대로 특정하지 않아 이 해석을 추론으로 채웠다 | Pattern 2 Code Example, D-105 인용부 | 잘못 해석하면(예: 만료된 장까지 포함해 MAX 계산) 이미 못 쓰는 옛 이용권 등록일이 기준일을 부당하게 최신으로 만들어 미사용 차감이 부당하게 늦춰지거나 생략될 수 있다. **계획 단계에서 사용자에게 재확인 권고** |
| A2 | `pass_transaction.ck_pass_transaction_subject` 완화 방식은 "정확히 하나 → 최대 하나"(admin/member 둘 다 NULL 허용)로 한다 — CONTEXT.md는 "CHECK 완화·시스템 주체 표현을 설계한다"고만 하고 구체적 SQL을 지정하지 않았다 | Pitfall 5, Code Example 2 | `actor_type` 판별 컬럼 방식을 원했다면 다시 설계해야 한다 — 둘 다 커밋 전 결정이므로 리스크는 낮지만 계획 단계에서 명시적으로 선택지를 제시해야 한다 |
| A3 | `member.returnedFromLeaveAt`은 ON_LEAVE→ACTIVE 전이에서만 갱신되는 단일 목적 컬럼으로 설계한다 — CONTEXT.md는 "컬럼 설계는 재량"이라고만 명시 | Code Example 1 | 범용 `status_changed_at`을 원했다면 "이전 상태가 ON_LEAVE였는가"를 판정할 수 없어 별도 이전 상태 컬럼이 추가로 필요해진다 — 재설계 비용이 있지만 A1·A2보다 영향 범위가 좁다 |
| A4 | 배치 실행 이력 테이블명은 `batch_execution`/엔티티명 `BatchExecution`으로 가정했다 — glossary.md에 아직 등록되지 않은 신규 개념이라 실제 확정은 계획 단계에서 glossary 등록 절차를 거쳐야 한다 | Recommended Project Structure, Code Example 2 | 금지어 목록(Ticket/Voucher/Coupon/Booking/Course, Session/User/Account/Role)과 충돌하지 않는 것은 확인했으나 최종 확정은 아니다 |
| A5 | `@Scheduled` cron 표현식 필드가 6필드(초 분 시 일 월 요일)라는 것은 Spring 표준 cron 문법으로 훈련 지식에 의존했다 — 이번 세션에서 Context7 예제(`"0 0 12 * * ?"`)로 형식은 확인했으나 6필드 개수 자체를 별도로 검증하지 않았다 | Pattern 1 Code Example | 리스크 낮음 — 컴파일이 아니라 런타임 파싱 오류로 즉시 드러나고, `verify-boot4-api` 5단계(컴파일 검증)로는 못 잡지만 앱 기동 시 `IllegalArgumentException`으로 바로 드러난다 |

## Open Questions (RESOLVED)

> 두 질문 모두 계획 단계에서 사용자 답변으로 해소됨 — 근거: `05-CONTEXT.md` §C "계획 단계 보완 확정
> (2026-08-15, 리서치 Open Questions에 대한 사용자 답변)". 각 항목의 **Resolution**이 최종 기준이다.

1. **다장 보유 회원의 기준일 후보 ④(등록일) 계산 범위** — **(RESOLVED)**
   - What we know: D-105는 "SESSION_PASS 등록일(created_at)"을 후보로 명시하고, 판정은 회원 단위다.
   - What's unclear: 회원이 SESSION_PASS를 2장 이상 보유(예: 기존 카드 소진 전 새 카드 추가 등록)할 때
     "등록일" 후보가 가장 최근 등록한 장의 `created_at`인지, 아니면 현재 차감 대상이 될 장(만료 임박
     한 장, D-109)의 `created_at`인지 CONTEXT.md 문구만으로는 확정할 수 없다.
   - Recommendation: 계획 단계에서 discuss-phase 형태로 짧게 재확인하거나(가장 안전), Assumption A1
     (미만료 SESSION_PASS 중 MAX)로 진행하되 테스트에 이 케이스를 명시적으로 남겨 회귀를 잡는다.
   - **Resolution (CONTEXT §C, 2026-08-15 — D-105 보강):** **차감 가능한 장(취소·만료 아님, 잔여 > 0)
     중 최신 `created_at`**을 후보 ④로 쓴다. 새 장 등록(충전)이 시계를 리셋하는 +가감일 원리와 일관.
     Assumption A1(미만료 SESSION_PASS 중 MAX)에 "잔여 > 0"·"취소 아님" 조건이 더해진 형태이므로,
     위 Pattern 2의 `findLastSessionPassRegistrationDates` 초안(`status = ACTIVE and endDate >= :today`)에
     잔여 조건을 추가해 구현한다. 반영 위치: 05-01 Task 2(docs/decisions.md D-105 보강),
     05-04 Task 1(쿼리), 05-03(기준일 테스트).

2. **배치 실행 이력의 실패 단위 표시** — **(RESOLVED)**
   - What we know: D-108은 "실행 이력(시각·처리 건수·결과)"만 요구하고, Pitfall 3은 회원별 트랜잭션
     분리를 요구한다.
   - What's unclear: 회원 300명 중 5명이 경쟁 패배(조건부 UPDATE 0행)로 스킵됐을 때 이것이
     `PARTIAL_FAILURE`인지 `SUCCESS`(경쟁 패배는 정상적인 "다음 실행이 보정" 경로이므로)인지 CONTEXT.md가
     명시하지 않는다.
   - Recommendation: "경쟁 패배로 인한 스킵"은 예외적 실패가 아니라 D-106 설계가 예정한 정상 동작이므로
     `SUCCESS`로 집계하고 스킵 건수만 별도 필드로 남기는 편을 권고(계획 단계 확정 필요).
   - **Resolution (CONTEXT §C, 2026-08-15 — D-108 보강):** 권고안 채택. 경쟁 패배 스킵은 설계가 예정한
     정상 동작(다음 실행이 상태 기반으로 보정)이므로 **결과는 `SUCCESS`로 두고 스킵 건수를
     `batch_execution`의 별도 컬럼으로 기록**한다. `PARTIAL_FAILURE`는 회원 단위 예외(try-catch로 잡힌
     오류)에만 쓴다. 반영 위치: 05-02 Task 1·2(스키마·엔티티), 05-06 Task 1(집계·결과 판정),
     05-01 Task 2(docs/decisions.md D-108 보강).

## Environment Availability

이 phase는 기존 PostgreSQL(Testcontainers 배선 완료)·Spring 컨텍스트 외에 새 외부 의존성이 없다.
스케줄링은 애플리케이션 프로세스 내부 기능이라 별도 인프라(외부 cron, 메시지 큐 등)가 필요 없다 —
아래 표는 "이미 있는 것의 재확인"이다.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL | 배치 대상 조회·차감 반영 | ✓ (docker-compose, Testcontainers) | 18.4-alpine(테스트), 운영은 PostgreSQL 18 | — |
| Spring Framework `spring-context`(스케줄링 모듈) | `@Scheduled`/`@EnableScheduling` | ✓ (Boot 4.1.0 BOM, 이미 classpath) | 7.0.8 | — |
| 단일 EC2 인스턴스 전제(D-108) | 분산 락 불필요 판단의 근거 | ✓ (배포 인프라 기존 확정, D-006) | — | 다중 인스턴스 전환 시 ShedLock 재검토(M7) |

**Missing dependencies with no fallback:** 없음.
**Missing dependencies with fallback:** 없음.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Kotlin (`kotlin-test-junit5`) + AssertJ, Testcontainers(PostgreSQL 18.4-alpine) |
| Config file | `build.gradle.kts`(테스트 의존성), 별도 `pytest.ini` 류 설정 없음 — Gradle 표준 소스셋 |
| Quick run command | `./gradlew test --tests "com.goldwrestling.batch.*"` |
| Full suite command | `./gradlew build` (ktlint + compile + 전체 테스트) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BATCH-01 | 기준일로부터 2주 경과 시 1회 차감, 이후 2주마다 반복 | unit | `./gradlew test --tests InactivityDueDateCalculatorTest` | ❌ Wave 0 |
| BATCH-01 | 5종 후보 중 가장 최근 날짜가 기준일로 채택된다 (출석 부재는 Phase 6 전까지 자연 제외) | unit | `./gradlew test --tests InactivityDueDateCalculatorTest` | ❌ Wave 0 |
| BATCH-01 | 차감은 만료 임박순 한 장, 잔여 부족 시 부분 차감(D-109) | integration(Testcontainers) | `./gradlew test --tests InactivityDeductionServiceTest` | ❌ Wave 0 |
| BATCH-02 | ON_LEAVE 회원은 차감 대상에서 제외 | integration | `./gradlew test --tests InactivityBatchRunnerTest` | ❌ Wave 0 |
| BATCH-02 | 잔여 0인 SESSION_PASS는 차감 대상에서 제외 | unit + integration | `./gradlew test --tests InactivityDeductionServiceTest` | ❌ Wave 0 |
| BATCH-02 | 유효기간 만료 SESSION_PASS는 차감 대상에서 제외 | integration | `./gradlew test --tests InactivityDeductionServiceTest` | ❌ Wave 0 |
| BATCH-03 | 만료 이용권은 `displayStatus`가 `EXPIRED`이고 예약 시 거부된다 (기존 메커니즘 검증) | integration | `./gradlew test --tests InactivityBatchExpiryVerificationTest` | ❌ Wave 0 (신규 검증 전용 테스트, 새 프로덕션 코드 없음) |
| BATCH-04 | 같은 날 배치를 2회 연속 실행해도 차감 건수·잔여가 동일 (멱등성) | integration(Testcontainers) | `./gradlew test --tests InactivityBatchIdempotencyTest` | ❌ Wave 0 |
| BATCH-04 | 배치가 며칠 밀린 뒤 실행하면 밀린 주기만큼 몰아서 차감(캐치업) | unit + integration | `./gradlew test --tests InactivityDueDateCalculatorTest`, `InactivityBatchIdempotencyTest` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "com.goldwrestling.batch.*"` (해당 청크 범위)
- **Per wave merge:** `./gradlew build` (ktlintFormat 확인 포함 전체 스위트)
- **Phase gate:** 전체 스위트 green 상태로 `/gsd:verify-work` 진입

### Wave 0 Gaps

- [ ] `src/test/kotlin/com/goldwrestling/batch/InactivityDueDateCalculatorTest.kt` — 순수 계산 단위테스트(기준일 max, 부족분, 캐치업)
- [ ] `src/test/kotlin/com/goldwrestling/batch/InactivityDeductionServiceTest.kt` — 회원 1명 차감 반영 통합테스트(D-109 부분차감·재선택 포함)
- [ ] `src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunnerTest.kt` — 대상 회원 필터링(ON_LEAVE 제외 등) 통합테스트
- [ ] `src/test/kotlin/com/goldwrestling/batch/InactivityBatchIdempotencyTest.kt` — 멱등성·캐치업 통합테스트(§10.0 "배치" 행이 요구하는 멱등성 테스트)
- [ ] `src/test/kotlin/com/goldwrestling/batch/InactivityBatchExpiryVerificationTest.kt` — BATCH-03 실증(새 프로덕션 코드 없이 기존 `Pass.displayStatus`·예약 거부 경로만 검증)
- [ ] `src/test/kotlin/com/goldwrestling/batch/BatchFixtures.kt` — `PassFixtures.kt`(pass 패키지)와 동일 관례의 테스트 픽스처 헬퍼
- [ ] 프레임워크 설치: 불필요 — 기존 JUnit5/AssertJ/Testcontainers 배선을 그대로 재사용

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | 이 phase는 새 인증 경로를 추가하지 않는다 (기존 JWT 체계 재사용) |
| V3 Session Management | no | 해당 없음 (STATELESS, 이 프로젝트는 세션 개념 자체가 없다 — glossary 금지어) |
| V4 Access Control | yes | 관리자 수동 실행 API는 기존 `SecurityConfig`의 `/api/admin/**` → `hasRole("ADMIN")` 규칙을 그대로 상속한다(D-040) — 새 인가 규칙 불필요, `AdminScheduleController`와 동일 패턴 |
| V5 Input Validation | yes | 수동 실행 API는 요청 본문이 없거나(단순 트리거) 최소 파라미터만 받으므로 `jakarta.validation` 형식 검증 범위가 작다 — 도메인 검증(중복 실행 방지 등)은 D-106 상태 기반 설계 자체가 흡수하므로 별도 검증 로직 불필요 |
| V6 Cryptography | no | 해당 없음 |

### Known Threat Patterns for {stack}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| 배치 API 남용(관리자가 수동 실행을 반복 호출해 부하 유발) | Denial of Service | D-106 상태 기반 설계 자체가 완화한다 — 반복 호출해도 부족분이 0이면 실질적으로 조회만 하고 끝나는 저비용 호출이 된다. 별도 rate limit은 이번 phase 범위 밖(관리자 전용 API, 회원 노출 없음) |
| 시스템 주체 위장(회원이 자신의 예약을 INACTIVITY 사유로 위장해 이력 조작) | Spoofing/Tampering | `PassTransaction` 생성은 서버 내부 배치 로직에서만 발생하고 API로 `reason` 값을 직접 받는 엔드포인트가 없다 — 클라이언트가 사유 코드를 지정할 수 있는 경로 자체가 없으므로 구조적으로 차단됨 |
| 관리자 수동 실행 API의 감사 추적 누락 | Repudiation | `batch_execution.triggered_by_admin_id`가 수동 실행 시 관리자를 기록한다(Code Example 2) — 누가 언제 수동 실행했는지 추적 가능 |

## Sources

### Primary (HIGH confidence)

- Context7 `/websites/spring_io_spring-framework_reference` — "@Scheduled" cron/zone 속성, `@EnableScheduling` 설정 예제 (2026-08-15 조회)
- Context7 `/spring-projects/spring-boot` (v4.1.0 브랜치 문서) — `spring.task.scheduling.pool.size` 기본값 1, `ThreadPoolTaskSchedulerBuilder` 자동설정, virtual threads 스케줄링 영향 (2026-08-15 조회)
- `https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.0/spring-boot-dependencies-4.1.0.pom` — `spring-framework.version=7.0.8` 실제 조회
- `https://repo1.maven.org/maven2/org/springframework/spring-context/maven-metadata.xml` — `<release>7.0.8` 실제 조회
- 이 저장소 코드 직접 조사: `Pass.kt`, `PassRepository.kt`, `PassTransaction.kt`, `V8__extend_pass_transaction_subject.sql`, `Member.kt`, `MemberStatus.kt`, `AdminMemberService.kt`, `ClockConfig.kt`, `TransactionReason.kt`, `Reservation.kt`, `ReservationRepository.kt`, `ReservationLedgerSupport.kt`, `AdminScheduleController.kt`, `ErrorCode.kt`, `GoldWrestlingApplication.kt`, `build.gradle.kts`, `PassLedgerInvariantTest.kt`, `TestClockConfiguration.kt`, `TestcontainersConfiguration.kt`
- `docs/policies.md` §4.3, `docs/decisions.md` D-027·D-091·D-105~D-109·D-016·D-020·D-021·D-055·D-056·D-064·D-066, `docs/conventions.md` §1·§3·§5·§9·§10·§11, `docs/glossary.md`, `.planning/phases/05-batch/05-CONTEXT.md`, `.planning/REQUIREMENTS.md`

### Secondary (MEDIUM confidence)

- 없음 — 이번 phase는 WebSearch를 쓰지 않고 Context7·공식 Maven 레지스트리·이 저장소 코드로 전부 검증했다.

### Tertiary (LOW confidence)

- 없음.

## Project Constraints (from CLAUDE.md)

- 문서 우선순위: `docs/policies.md` > `docs/requirements.md` > `docs/glossary.md`·`docs/decisions.md`·`docs/conventions.md` > `.planning/**` > 코드. `.planning/`은 실행 상태이지 스펙이 아니다 — 이 RESEARCH.md의 모든 도메인 규칙은 policies §4.3과 decisions.md D-105~109를 원본으로 삼았다.
- 새 개념(배치 실행 이력 엔티티명 등)은 `docs/glossary.md`에 먼저 등록 후 사용 (규칙 3, 금지어: Ticket/Voucher/Coupon/Booking/Course).
- API 응답 형태는 springdoc으로 `docs/api/openapi.yaml` 생성·갱신, 에러는 RFC 9457 `ProblemDetail` 고정(D-017) — 관리자 수동 실행 API도 예외 없음.
- 설계 결정은 `docs/decisions.md`에 3~4줄 기록 (V9 CHECK 완화·복귀 시각 컬럼·트랜잭션 경계 등 계획 단계 확정 항목 전부 대상).
- 모든 차감/복구는 `PassTransaction` 이력을 남긴다 — 이력 없는 잔여 변경 금지 (규칙 6).
- 시간대는 `Asia/Seoul` 명시, 주 시작은 월요일 (규칙 7) — `@Scheduled(zone = SEOUL_ZONE_ID)`로 반영.
- 모르는 Boot 4 API는 추측 금지 — ① context7 MCP ② maven-metadata.xml 실제 조회 ③ `./gradlew compileKotlin` 검증 순서 (규칙 9) — 이번 리서치에서 스케줄링 API·Spring Framework 버전을 이 순서로 실제 확인했다. **Pattern 2의 인터페이스 프로젝션 문법은 계획 단계에서 이 절차를 다시 밟아야 한다** (이번 리서치는 개념만 확인, 실제 문법은 미검증).
- 프로덕션 코드 추가·수정 시 같은 작업 안에서 테스트 함께 작성 (규칙 10, conventions §10.0 표 — "배치" 행이 단위테스트+멱등성 테스트를 명시적으로 요구).
- `ddl-auto`는 `validate` 고정, 커밋된 마이그레이션(V1~V8) 수정 금지 — 새 버전(V9)으로 추가.
- ktlint가 유일한 포맷 기준 — 작업 마지막에 `ktlintFormat` → `build` 순서.
- 이 phase는 GSD `/gsd:discuss-phase`로 이미 시작됐고(CONTEXT.md 존재), 도메인 로직(차감 정책) 비중이 커 TDD 플랜(`--tdd`) 후보에 해당한다 — 계획 단계에서 판단.
- phase는 청크 단위로 나눠 PR을 낸다(D-084) — 청크 경계 판단은 `.claude/skills/deliver-phase-chunk/SKILL.md` 절차를 따른다.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Context7 공식 문서 + maven-metadata.xml 실측으로 Boot 4.1.0/Spring Framework 7.0.8의 `@Scheduled`/`@EnableScheduling` API가 Boot 3와 동일함을 확인. 새 의존성이 없어 버전 충돌 리스크도 없다.
- Architecture: MEDIUM — 5종 벌크 조회·순수 계산 분리·트랜잭션 경계 설계는 이 저장소의 기존 관례(D-021 조건부 갱신, D-091 재선택, ReservationRepository IN절 패턴)를 그대로 연역한 것이라 근거는 탄탄하지만, **스케줄링 인프라 자체가 이 저장소 최초 도입**이라 실측(컴파일·테스트 실행) 검증은 계획·실행 단계에서 처음 이뤄진다.
- Pitfalls: HIGH — Pitfall 1·2·4는 CONTEXT.md·decisions.md의 명시적 근거(D-105~109)에서 직접 도출했고, Pitfall 3(self-invocation)은 Spring AOP의 잘 알려진 제약이며 이 저장소가 아직 배치를 다뤄본 적이 없어 처음 마주치는 함정이다. Pitfall 5(CHECK 완화)는 V8 마이그레이션 파일을 직접 읽고 도출했다.

**Research date:** 2026-08-15
**Valid until:** 2026-09-14 (30일 — Boot 4.1.x/Spring Framework 7.0.x는 안정 릴리스 라인이라 단기간 API 변경 가능성 낮음. 단, 이 저장소의 스키마·엔티티 상태는 phase 진행에 따라 바뀌므로 계획 단계 착수가 크게 지연되면 Pass.kt·Member.kt 등 재확인 권고)
