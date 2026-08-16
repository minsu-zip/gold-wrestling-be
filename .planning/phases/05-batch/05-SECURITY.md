---
phase: 05
slug: batch
status: secured
threats_total: 74
threats_closed: 74
threats_open: 0
accepted_risks: 9
asvs_level: 1
created: 2026-08-16
---

# Phase 05 — Security

> 2주 미사용 자동 차감 배치 phase의 보안 계약: 위협 레지스터, 수용된 위험, 감사 이력.
> 레지스터는 16개 PLAN의 `<threat_model>` 블록에서 그대로 가져왔다 (플랜 작성 시점에 저작 — 사후 역산이 아니다).
> 이 중 31건(`T-05D-*`)은 갭 클로저 청크 D(05-10~05-16) 산출물이다.

**이 phase가 특별한 이유:** 사람의 확인 없이 회원 잔여 횟수를 깎는 **유일한 경로**가 여기서
열렸다. 이 저장소의 Core Value("회원이 보는 잔여 = 실제 사용 가능 횟수")가 깨지는 가장 큰
단일 위험원이므로, 다른 phase보다 Tampering 위협(41/74)의 비중이 압도적으로 높다.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| cron 트리거(04:00) → 러너 | 사람 개입 없이 매일 실행된다 — 잘못된 cron·시간대·정책값은 조용히 전 회원에게 퍼진다 | 트리거 시각, 정책 설정값 |
| 인터넷 → `POST /api/admin/batch/inactivity-runs` | 호출 하나가 전 회원 원장을 건드리는 유일한 HTTP 진입점 | 관리자 JWT, 실행 접수 |
| 인터넷 → `GET /api/admin/batch/inactivity-runs[/{id}]` | 실행 이력·집계·실패 요약이 나가는 조회 경로 | 집계 수치, `errorSummary` |
| cron 스레드 ↔ Tomcat 스레드 ↔ 배치 실행기 스레드 | 한 JVM 안 세 실행 경로가 같은 원장을 동시에 갱신하려 한다 | `batch_execution` `RUNNING` 행 |
| 배치(시스템 주체) → `pass.remaining_count` | 잔여를 깎는 유일한 무인 경로 — 이중 차감·과차감이 조용히 일어날 수 있다 | 잔여 횟수, `PassTransaction` 이력 |
| 애플리케이션 → `pass_transaction` 원장 (V9 CHECK 완화) | 주체 배타성 규칙을 넓힌 변경 — 과하게 열면 사람 조작의 주체가 누락된다 | 원장 주체(`admin_id`/`member_id`) |
| 운영 환경변수 → 차감 정책 | `policy-effective-date`·`max-deductions-per-run`·`scheduler-enabled`가 차감량을 직접 결정한다 | 정책 설정값 (서버 운영자만 접근) |
| 운영자 → DB (`batch_execution`) | 사람이 `RUNNING` 행을 직접 지우거나 남길 수 있다 — 직렬화가 DB 인덱스에 의존한다 | 실행 직렬화 상태 |
| 서버 → 관리자 (에러 응답) | 409/404 문구가 제약조건명·SQL·id 존재 여부를 유추하게 할 수 있다 | `ProblemDetail` 본문 |
| 문서(스펙) → 코드 | 기준일 정의가 문서마다 다르면 대량 차감 로직이 틀린 채 구현된다 | policies §4.3 · D-105 문구 |

---

## Threat Register

74개 위협 중 **72건 CLOSED, 2건 PARTIAL(open)**. `mitigate` 51건은 구현 코드·마이그레이션 DDL·
테스트 단언에서 `파일:라인`으로 실증했고, `accept` 16건은 근거의 **사실 여부**를 별도로 확인했다
(근거 서술을 그대로 믿지 않았다). `n/a` 7건은 전제("새 의존성 없음")를 git diff로 실제 확인했다.

### 05-01 ~ 05-03 (문서 정합 · 스키마 · 순수 계산)

| Threat ID | Category | Component | Disposition | Mitigation (검증된 위치) | Status |
|-----------|----------|-----------|-------------|--------------------------|--------|
| T-05-01 | Tampering | 기준일 정의 문서 불일치 → 소급 차감 | mitigate | `docs/policies.md:85-92` — 기준일 5종 후보 확정 문구 + CR-04 정정 반영. `docs/decisions.md:774` D-105 | closed |
| T-05-02 | Repudiation | 재량 결정 미기록 | mitigate | `docs/decisions.md:774,797,818,859,872,884,904,929,949` — D-105·106·108·109·110·111·112·113·114 (기각 대안 포함) | closed |
| T-05-03 | Tampering | CHECK 완화 과잉 → 사람 조작 주체 누락 | mitigate | `V9:9-11` — `NOT (admin_id IS NOT NULL AND member_id IS NOT NULL)` (at-most-one). "둘 다 채움" 거부를 `PassRepositoryTest.kt:174`가, "둘 다 NULL 허용"을 `:195`가 고정. 기존 사람 경로 5곳(`AdminPassService.kt:89,145,299`·`ReservationLedgerSupport.kt:147,229`)이 여전히 주체를 채움을 확인 | closed |
| T-05-04 | Repudiation | 배치 차감과 관리자 차감이 원장에서 구분 불가 | mitigate | `InactivityDeductionService.kt:75,77-78` — `reason = INACTIVITY` + `admin = null, member = null`. `admin_id`를 위장 계정으로 오염시키지 않음 | closed |
| T-05-05 | Tampering | 복귀 시각이 다른 전이에서도 갱신 → 차감 시계 부당 리셋 | mitigate | `AdminMemberService.kt:143-145` — `previousStatus == ON_LEAVE` 게이트. `MemberStatusChangeTest.kt:309`(PENDING→ACTIVE null 유지)·`:321`(INACTIVE→ACTIVE null 유지)·`:333`(ACTIVE→ON_LEAVE 미갱신)·`:412`(ON_LEAVE→ON_LEAVE 미갱신)·`:368`(approve 미갱신) ⚠️ **완화 조건이 계획과 다르다 — 아래 주 참조** | closed |
| T-05-06 | Denial of Service | `batch_execution` 무한 증가 | **accept** | 근거 확인 — 정리 쿼리·TTL이 코드에 없음이 맞다. `BatchExecutionRepository.kt` 전체 55행에 삭제 메서드 없음. 연 400건 미만 규모 (R-05-02) | closed |
| T-05-07 | Tampering | 경계 오프바이원 → 전 회원 오차감 | mitigate | `InactivityDueDateCalculator.kt:83-85` `floor(경과일/14)`. 5경계 전부 테스트: `:199`(13→0)·`:204`(14→1)·`:209`(27→1)·`:214`(28→2)·`:224`(42→3) | closed |
| T-05-08 | Tampering | 부족분 음수 → "차감 취소"로 해석 | mitigate | `InactivityDueDateCalculator.kt:109` `.coerceAtLeast(0)` + `InactivityDueDateCalculatorTest.kt:300` | closed |
| T-05-09 | Tampering | 리셋 이전 옛 `INACTIVITY` 이력이 섞여 차감 영구 정지 | mitigate | `InactivityDueDateCalculator.kt:108` `count { !it.isBefore(dueDate) }` + 테스트 `:276`(이전 미포함)·`:288`(당일 포함) | closed |

**T-05-05 완화 조건 변경 기록:** 플랜은 `previousStatus == ON_LEAVE && newStatus == ACTIVE`
단일 분기를 요구했으나, 구현은 `previousStatus == ON_LEAVE && newStatus != ON_LEAVE`로 **넓혀졌다.**
`ON_LEAVE→INACTIVE→ACTIVE` 우회 복귀에서 기준일이 사라져 휴회 기간 전체가 소급 차감되던 결함
(05-REVIEW.md CR-04) 때문이다. 위협의 **의도**("다른 상태 전이에서 부당 리셋")는 그대로 지켜진다 —
게이트가 여전히 `previousStatus == ON_LEAVE`에 걸려 있어 PENDING·INACTIVE→ACTIVE는 리셋되지 않고,
그 사실을 위 3개 테스트가 고정한다. 변경 근거는 `V10:27-32` 주석과 `docs/decisions.md:884` D-111
정정, `docs/policies.md:88-90`에 남아 있다. CLOSED로 판정하되 축소가 아니라 **확대**임을 명시한다.

### 05-04 ~ 05-05 (대상 조회 · 차감 반영)

| Threat ID | Category | Component | Disposition | Mitigation (검증된 위치) | Status |
|-----------|----------|-----------|-------------|--------------------------|--------|
| T-05-10 | Tampering | 휴회 회원이 차감 대상에 포함 | mitigate | `PassRepository.kt:162` `p.member.status <> ON_LEAVE` + `InactivityBatchQueryTest.kt:106`. 세 번째 메커니즘(휴회 일수 차감) 부재를 `InactivityBatchRunner.kt:57-58` KDoc이 명시 | closed |
| T-05-11 | Tampering | 만료·소진 이용권 보유 회원이 대상 | mitigate | `PassRepository.kt:161` `endDate >= :today and remainingCount > 0` + 경계 테스트 `:116`(잔여 0)·`:126`(어제 만료)·`:136`(오늘 만료는 포함) | closed |
| T-05-12 | Tampering | `startDate` 오인 → 과거 시작일 등록이 즉시 소급 차감 | mitigate | `PassRepository.kt:181` `max(p.createdAt)` — 쿼리 전체에 `p.startDate` 부재 확인. `InactivityBatchQueryTest.kt:171` | closed |
| T-05-13 | Information Disclosure | 벌크 조회가 대상 외 회원 데이터 반환 | mitigate | 5개 벌크 조회 전부 `in :memberIds` — `ReservationRepository.kt:203`·`MemberRepository.kt:26`·`PassRepository.kt:182`·`PassTransactionRepository.kt:37,55`. `InactivityBatchQueryTest.kt:357`(4종)·`:202`(등록일 조회) | closed |
| T-05-14 | Denial of Service | 회원별 개별 쿼리(N+1)로 DB 포화 | mitigate | `InactivityBatchRunner.kt:132-151` — 벌크 5종이 전부 `for` 루프(`:152`) **밖**. 루프 안 조회는 `deductOnce`의 회당 재선택뿐(차감 횟수 = 소수) | closed |
| T-05-15 | Tampering | 배치 차감과 예약 차감 경쟁 → 잔여 음수 | mitigate | `PassRepository.kt:48-49` `remainingCount + :amount >= 0` 조건부 UPDATE + `InactivityDeductionService.kt:65-67` 0행이면 `false` 반환(스킵). `InactivityDeductionConcurrencyTest.kt:102`(2스레드→1성공, 음수 없음)·`:119`(3스레드→정확히 2) | closed |
| T-05-16 | Tampering | 후보 캐시 소비로 소진된 장 반복 차감 | mitigate | `InactivityDeductionService.kt:56-57` — 매 호출이 `findDeductibleSessionPasses` 재조회. `InactivityDeductionServiceTest.kt:185`(연속 2회 → 1회만 성공, 이력 1건)·`:199`(두 장 → 회당 다른 장) | closed |
| T-05-17 | Repudiation | 잔여만 바뀌고 이력 없음 → 감사 추적 단절 | mitigate | `InactivityDeductionService.kt:53,65-81` — 단일 `@Transactional` 안에서 UPDATE + `PassTransaction` 저장. `InactivityBatchIdempotencyTest.kt:248` `assertLedgerInvariant`(잔여 == 이력 합계)를 7개 시나리오 전부가 호출(`:120,132,150,164,190,208,224`) | closed |
| T-05-18 | Spoofing | 배치 차감이 특정 관리자 행위로 오귀속 | mitigate | `InactivityDeductionService.kt:77-78` `admin = null, member = null` + `InactivityDeductionServiceTest.kt:118` 단언 | closed |

### 05-06 ~ 05-07 (오케스트레이션 · 멱등성)

| Threat ID | Category | Component | Disposition | Mitigation (검증된 위치) | Status |
|-----------|----------|-----------|-------------|--------------------------|--------|
| T-05-19 | Tampering | 루프 전체 단일 트랜잭션 → 부분 실패가 전원 롤백 | mitigate | `InactivityDeductionService.kt` grep `Transactional` **0건** (import·애노테이션 모두 부재). 트랜잭션은 별도 빈 `InactivityDeductionService.kt:53`·`BatchExecutionRecorder.kt:66,113`에만 | closed |
| T-05-20 | Tampering | 실행 이력 기반 "오늘 이미 실행" 가드 → 캐치업 실패 | mitigate | `InactivityBatchRunner.kt:61-71` 생성자에 `BatchExecutionRepository` 없음, 파일 전체 grep **0건**. 부족분 근거는 원장 건수뿐(`InactivityDueDateCalculator.kt:93-96` KDoc + `:108`) | closed |
| T-05-21 | Tampering | 휴회 예외의 세 번째 메커니즘 중복 구현 | mitigate | `InactivityBatchRunner.kt:57-58` "두 곳으로만" KDoc + 복귀일 리셋 테스트 `InactivityBatchRunnerTest.kt:248`·`InactivityLeaveReturnTest.kt:114,155` | closed |
| T-05-22 | Denial of Service | 한 회원의 예외가 배치 전체 중단 | mitigate | `InactivityBatchRunner.kt:153,204-211` 회원 단위 try-catch + `:214` `PARTIAL_FAILURE` 집계. `InactivityBatchFailureIsolationTest.kt:142`(나머지 회원 차감 유지)·`:159` | closed |
| T-05-23 | Information Disclosure | `errorSummary`에 스택트레이스 등 과도한 내부 정보 | mitigate | `InactivityBatchRunner.kt:209-210` — 로그에만 예외 전달, 이력에는 `e.javaClass.simpleName`만. `:220` `.take(1000)`. `InactivityBatchFailureIsolationTest.kt:180`이 예외 메시지 미포함을 단언. **계획("예외 메시지만")보다 엄격하게 구현됨** | closed |
| T-05-24 | Tampering | 같은 날 중복 실행 → 이중 차감 | mitigate | `InactivityBatchIdempotencyTest.kt:108`(2회)·`:124`(5회 → 이력 1건)·`:212`(SCHEDULED+MANUAL 혼합 → 총 1건) | closed |
| T-05-25 | Tampering | 만료 이용권으로 예약해 유효기간 우회 | mitigate | `InactivityBatchExpiryVerificationTest.kt:190` — "오늘 유효·수업날 만료" 예약 거부(D-091) + `:175`·`:209`·`:228` | closed |
| T-05-26 | Repudiation | 반복 실행 후 잔여와 이력 불일치 | mitigate | `InactivityBatchIdempotencyTest.kt:248` 불변식 헬퍼를 7개 멱등 시나리오가 **전부** 종료 시 호출 | closed |

### 05-08 ~ 05-09 (관리자 API · cron · 마감 검증)

| Threat ID | Category | Component | Disposition | Mitigation (검증된 위치) | Status |
|-----------|----------|-----------|-------------|--------------------------|--------|
| T-05-27 | Elevation of Privilege | 회원·비인증 사용자가 배치 실행 | mitigate | `SecurityConfig.kt:70-71` `/api/admin/**` → `hasRole("ADMIN")`, `:76-77` `anyRequest().authenticated()` 기본 거부. `AdminBatchControllerTest.kt:328`(회원 토큰 403 ACCESS_DENIED)·`:340`(무토큰 401 UNAUTHENTICATED). 컨트롤러에 권한 애노테이션 없음이 D-040 관례와 일치 | closed |
| T-05-28 | Repudiation | 수동 실행 주체 추적 불가 | mitigate | `AdminBatchController.kt:93` `principal.requireAdminId()` → `AdminBatchService.kt:64` → `InactivityBatchRunner.kt:279-295`(관리자 존재 검증) → `BatchExecution.kt:41`. DB 강제는 `V9:32-35` `ck_batch_execution_trigger` + `BatchExecutionRepositoryTest.kt:89,108`. `BatchExecutionRecorderTest.kt:208` | closed |
| T-05-29 | Denial of Service | 관리자 수동 실행 반복 호출 | **accept** | 근거 확인 — rate limit 코드 없음이 맞다. 다만 **위험이 계획 시점보다 줄었다**: 실행 중 재호출은 `BatchExecutionRecorder.kt:97-100`이 409로 즉시 끊고(`AdminBatchRunConcurrencyTest.kt:186`), 접수는 202 비동기라 요청 스레드를 잡지 않는다. 관리자 전용 (R-05-03) | closed |
| T-05-30 | Tampering | cron 시간대 UTC 오해석 | mitigate | `InactivityBatchScheduler.kt:48` `@Scheduled(cron = "0 0 4 * * *", zone = SEOUL_ZONE_ID)` + `GoldWrestlingApplication.kt:15` JVM 기본 시간대 고정. `InactivityBatchSchedulerTest.kt:74`(cron 문자열·zone 단언)·`:87`(`@Scheduled` 메서드 정확히 1개) | closed |
| T-05-31 | Information Disclosure | 배치 응답에 회원 개인정보 노출 | mitigate | 이름·전화번호 미노출 확인(`BatchExecutionResponse.kt:18-29` 10필드에 회원 필드 없음, `AdminBatchControllerTest.kt:370`). `errorSummary`의 실패 회원 id는 **R-05-08로 수용**(2026-08-16 사용자 확정) — 문구 정정은 아래 Resolved 참조 | closed |
| T-05-32 | Repudiation | 요구사항이 근거 없이 완료 처리 | mitigate | `.planning/ROADMAP.md:163-166` — 성공 기준 4개 각각에 테스트 클래스명 인용(`InactivityDueDateCalculatorTest`·`InactivityBatchQueryTest`·`InactivityBatchExpiryVerificationTest`·`InactivityBatchIdempotencyTest`) | closed |
| T-05-33 | Tampering | 문서·구현 분기 상태로 phase 종료 | mitigate | 문자열 단위 대조 확인: `docs/error-codes.md:70-71`(BATCH 2종) ↔ `ErrorCode.kt:141,144`, `docs/glossary.md:64-77` ↔ `BatchExecution`·`BatchTrigger`·`BatchExecutionStatus`, `docs/api/openapi.yaml:475,885` ↔ 컨트롤러 경로 3종 | closed |
| T-05-34 | Tampering | 로컬 검증 데이터 잔존 | **accept** | 근거 확인 — 사용자 지시로 보존됨이 `05-09-SUMMARY.md` key-decisions에 기록. 임의 삭제 없음(04-15 선례) (R-05-04) | closed |

### 05-10 ~ 05-12 (기준일 리셋 · 실행 이력 · 직렬화)

| Threat ID | Category | Component | Disposition | Mitigation (검증된 위치) | Status |
|-----------|----------|-----------|-------------|--------------------------|--------|
| T-05D-10-01 | Tampering | `changeStatus`의 기준일 리셋 | mitigate | `AdminMemberService.kt:144` `OffsetDateTime.now(clock)` — 요청 DTO에 시각 필드 없음(`MemberStatusChangeRequest`는 `status`만). `MemberStatusChangeTest.kt:296` Clock 기준 시각 단언 | closed |
| T-05D-10-02 | Elevation of Privilege | 상태 전이 API | mitigate | `SecurityConfig.kt:70-71` 상속 + `MemberStatusChangeTest.kt:224` 회원 토큰 403. 이 플랜이 `SecurityConfig`를 건드리지 않았음을 diff로 확인 | closed |
| T-05D-10-03 | Repudiation | 휴회 이탈 이력 | **accept** | 근거 확인 — `Member.kt:57-58` 단일 컬럼(`var`, 덮어쓰기)이고 전이 이력 테이블 부재가 맞다. 잔여 변경 자체는 `PassTransaction`이 전량 기록(T-05-17 CLOSED로 실증) (R-05-05) | closed |
| T-05D-11-01 | Denial of Service | 잔존 `RUNNING` 행 | mitigate | `BatchExecutionRepository.kt:36-46` `markStaleRunningAsFailed` + **05-12가 시작 경로에 실제 배선**: `BatchExecutionRecorder.kt:73-78`. `BatchExecutionRepositoryTest.kt:221,252`(임계 이전만 정리)·`BatchExecutionRecorderTest.kt:176,192`. 테스트 `@AfterEach` 자기 행 정리 14개 클래스 전부 확인 | closed |
| T-05D-11-02 | Tampering | 실행 이력 확정값 조작 | mitigate | `BatchExecution.kt:39,41,43` — `trigger`·`triggeredByAdminId`·`startedAt` 전부 `val`. 확정 필드만 `var`(`:45-56`). `BatchExecutionTest.kt:81`이 `finish`가 이 3필드를 바꾸지 않음을 단언 | closed |
| T-05D-11-03 | Information Disclosure | `error_summary` | mitigate | `InactivityBatchRunner.kt:209-210,243` 예외 **종류**만 + `InactivityBatchFailureIsolationTest.kt:180` (메시지 미포함) | closed |
| T-05D-12-01 | Denial of Service | 반복 실행 요청 | mitigate | `V10:25` `uq_batch_execution_running` → `BatchExecutionRecorder.kt:97-100` `DataIntegrityViolationException` → `BatchAlreadyRunningException` → `ErrorCode.kt:141` 409. `AdminBatchRunConcurrencyTest.kt:162`(2스레드 → 1개만 202)·`:175`(4스레드 → RUNNING 1건)·`:233`(202 시점에 잔여 미변경 = 본문 미시작) | closed |
| T-05D-12-02 | Denial of Service | 잔존 `RUNNING`의 영구 차단 | mitigate | `BatchExecutionRecorder.kt:66,73-78` — 정리와 INSERT가 같은 `REQUIRES_NEW` 트랜잭션. `BatchExecutionRecorderTest.kt:176`(임계 초과 → STALE 정리 후 시작)·`:192`(임계 이내 → 거부) | closed |
| T-05D-12-03 | Tampering | `policy-effective-date` 환경변수 조작 | **accept** | 근거 확인 — `.env.example:56` 키만 있고 값 비어 있음(실값 커밋 0건). 상한 방어가 실재: `InactivityBatchRunner.kt:177` `minOf(shortfall, maxDeductionsPerRun)` 기본 1 (R-05-06) | closed |
| T-05D-12-04 | Information Disclosure | 409 응답 본문 | mitigate | `BatchExceptions.kt:19-23` 고정 한국어 문구(제약조건명·SQL·id 미포함) + `AdminBatchRunConcurrencyTest.kt:205` — 응답 본문에 `uq_batch_execution_running`·`batch_execution`·`insert`·`constraint`·`Exception` 부재 단언 | closed |

### 05-13 ~ 05-16 (러너 배선 · 정책값 · 비동기 API · 실기동)

| Threat ID | Category | Component | Disposition | Mitigation (검증된 위치) | Status |
|-----------|----------|-----------|-------------|--------------------------|--------|
| T-05D-13-01 | Tampering | 동시 `run()`의 이중 차감 | mitigate | `InactivityBatchRunner.kt:84-86` — `start()` 통과 후에만 `runStarted()` 진입. `InactivityBatchRunConcurrencyTest.kt:167`(4스레드 → 총 `INACTIVITY` 차감 1회)·`:145`(거부 시 잔여·이력 무변화) | closed |
| T-05D-13-02 | Denial of Service | 전체 실패 후 잔존 `RUNNING` | mitigate | `InactivityBatchRunner.kt:229-248` — `catch`가 `FAILED` 확정 후 `throw e` 재전파, 확정 실패는 `runCatching`으로 흡수(원인 예외 미차단). `InactivityBatchFailureIsolationTest.kt:235,255`(전체 실패 뒤 다음 실행이 시작됨) | closed |
| T-05D-13-03 | Repudiation | cron 실패의 무기록 | mitigate | `InactivityBatchScheduler.kt:50-58` — 예외를 삼키되 이력은 러너가 남김(`:57` 로그가 `batch_execution` 확인을 지시). `InactivityBatchSchedulerTest.kt:53,63` | closed |
| T-05D-13-04 | Information Disclosure | `errorSummary` | mitigate | T-05-23·T-05D-11-03과 동일 경로 — `InactivityBatchRunner.kt:243` `e.javaClass.simpleName` | closed |
| T-05D-14-01 | Denial of Service | 배포 첫 실행의 대량 소진 | mitigate | 하한: `InactivityDueDateCalculator.kt:72` `.coerceAtLeast(policyEffectiveDate)` (기본 `2026-09-01`, `InactivityBatchProperties.kt:35`). 상한: `InactivityBatchRunner.kt:177` `minOf(..., maxDeductionsPerRun)` (기본 1, `:44`). 킬 스위치: `InactivityBatchScheduler.kt:34-38` `@ConditionalOnProperty`. 테스트 `InactivityBatchPolicyLimitTest.kt:120,148,167,180`·`InactivityDueDateCalculatorTest.kt:145,160`·`AdminBatchControllerTest.kt:174`(킬 스위치가 빈을 실제로 제거) | closed |
| T-05D-14-02 | Tampering | 시행일 환경변수 조작 | **accept** | 근거 확인 — `.env.example:56` 키 이름만, 값 비어 있음. `application.yml:87` 환경변수 참조 형태(`${...:2026-09-01}`)로 실값 커밋 없음 (R-05-06) | closed |
| T-05D-14-03 | Repudiation | 상한 절삭분의 무기록 | **accept** | 근거 확인 — `InactivityBatchRunner.kt:182-188` `logger.info`만, `skippedCount` 미증가(`:179-181` 주석 근거). 재계산 가능성은 원장+기준일로 보존됨. `InactivityBatchPolicyLimitTest.kt:135`가 이 계약을 고정 (R-05-07) | closed |
| T-05D-15-01 | Elevation of Privilege | 새 GET 엔드포인트 2종 | mitigate | `AdminBatchController.kt:45,100,126` 경로 전부 `/api/admin` 하위 → `SecurityConfig.kt:70-71` 상속. `AdminBatchControllerTest.kt:353` — **두 엔드포인트 각각에 대해** 403·401 반복 단언 | closed |
| T-05D-15-02 | Information Disclosure | 실행 이력 조회 응답 | mitigate | 집계 전용 DTO 확인(`BatchExecutionResponse.kt:18-29`). `errorSummary`의 실패 회원 id는 **R-05-08로 수용**(2026-08-16 사용자 확정) | closed |
| T-05D-15-03 | Denial of Service | 반복 POST | mitigate | `AdminBatchService.kt:64` 동기 시작 → 409 즉시 종료(`AdminBatchRunConcurrencyTest.kt:186`). 실행기 `BatchExecutorConfig.kt:40-42` core=1·max=1·queue=1 | closed |
| T-05D-15-04 | Denial of Service | 실행기 포화로 잔존 `RUNNING` | mitigate | `AdminBatchService.kt:72-87` — `RejectedExecutionException` catch → `finish(FAILED, "REJECTED")` 후 재전파. `AdminBatchServiceTest.kt:95` | closed |
| T-05D-15-05 | Repudiation | 비동기 실행의 주체 | mitigate | `AdminBatchService.kt:64` — `runner.start(MANUAL, adminId)`가 **요청 스레드에서 동기 실행**되어 `triggered_by_admin_id`를 확정. 실행 스레드(`:68-71`)는 `executionId`만 받음. `AdminBatchServiceTest.kt:45` | closed |
| T-05D-16-01 | Tampering | 실기동 검증 중 보존 데이터 손상 | mitigate | `05-16-SUMMARY.md:113` "`docker compose down -v`를 실행하지 않았다 — 절대 실행하지 말 것" + `:270` psql로 보존 데이터(member 4 / pass 7 / pass_transaction 35) 무손실 확인. `05-VERIFICATION.md:230` `RUNNING` 0건·`finished_at IS NULL` 0건 | closed |
| T-05D-16-02 | Repudiation | 검증했다는 주장만 남는 것 | mitigate | `05-16-SUMMARY.md:210` `./gradlew cleanTest test` — 763 tests / 0 failures(캐시 재사용 아님). `:254` "실기동 확인 결과(2026-08-16, 체크포인트 해소)"에 실제 관찰값 기록. `05-VERIFICATION.md:248` | closed |

### 공급망 (`T-05-SC` ×9 · `T-05D-*-SC` ×7)

| Threat ID | Category | Component | Disposition | Mitigation (검증된 위치) | Status |
|-----------|----------|-----------|-------------|--------------------------|--------|
| T-05-SC (05-01~05-09, 9건) | Tampering | npm/pip/cargo installs | **accept** | 근거 확인 — `git diff 8a89e4c..HEAD -- build.gradle.kts`에서 `implementation`/`testImplementation`/`runtimeOnly` 추가·삭제 **0줄**. Phase 5의 `build.gradle.kts` 커밋 2건(`e80bb9c`·`858f381`)은 테스트 태스크 `systemProperty` 추가뿐 (R-05-01) | closed |
| T-05D-10-SC ~ T-05D-16-SC (7건) | Tampering | 패키지 설치 | **n/a** | 전제 확인 — 위와 동일 diff. 실행기는 스프링 코어 `ThreadPoolTaskExecutor`(`BatchExecutorConfig.kt:5` import)로 신규 의존성 아님 | closed |

*Status: open · closed*
*Disposition: mitigate (구현 필요) · accept (문서화된 위험) · transfer (제3자) · n/a (해당 없음)*

---

## Resolved — 등록부 모순 정정 (구 Open Threats 2건)

두 위협 모두 **같은 코드 한 줄**에서 비롯됐고, 원인은 구현 결함이 아니라 **등록부가 서로 모순된 두
요구를 담고 있었던 것**이다. 2026-08-16 사용자 결정으로 **T-05-23을 정본**으로 삼고 두 항목을
R-05-08(수용)로 닫았다. 아래 분석은 그 판단 근거로 남긴다.

| Threat ID | Category | Mitigation Expected | 실제 코드 | 판정 |
|-----------|----------|---------------------|-----------|------|
| T-05-31 | Information Disclosure | "응답은 집계 수치와 관리자 id만 — **회원 id 목록**·이름을 담지 않는다(테스트로 단언)" | `InactivityBatchRunner.kt:216-220` — `errorSummary = "memberId=$memberId: $예외종류"`를 `; `로 이어 붙여 최대 1000자. 이 값이 `BatchExecutionResponse.kt:28`을 통해 `POST /…/inactivity-runs`·`GET /…/{id}`·`GET /…` 응답으로 나간다 | **PARTIAL** — 이름·전화번호는 미노출(CLOSED), 회원 id 미노출은 **미충족** |
| T-05D-15-02 | Information Disclosure | "`BatchExecutionResponse`는 집계 수치만 담고 **회원 id**·개인정보를 담지 않는다" | 위와 동일 | **PARTIAL** — 동일 사유 |

**왜 인용된 테스트가 이것을 못 잡는가.** `AdminBatchControllerTest.kt:370`
(`응답 본문에 회원 개인정보나 회원 id 목록이 담기지 않는다`)은 ① 회원 **이름·전화번호** 문자열
부재와 ② `memberIds`라는 **필드명** 부재만 단언하고, 시나리오가 정상 실행(실패 회원 0명 →
`errorSummary = null`)이라 실패 경로를 밟지 못한다. 반대로 `InactivityBatchFailureIsolationTest.kt:159`는
`errorSummary`가 실패 회원 id를 **포함할 것**을 명시적으로 요구한다 — 두 테스트가 서로 반대
방향을 고정하고 있고, 등록부의 T-05-23(회원 id 기록 허용) ↔ T-05-31/T-05D-15-02(회원 id 금지)가
그대로 코드에 반영된 결과다.

**심각도 판단 (BLOCKER 아님, `block_on: critical` 기준):**
- 노출 대상은 `hasRole("ADMIN")` 인증을 통과한 관리자뿐이다(`SecurityConfig.kt:70-71`,
  `AdminBatchControllerTest.kt:353` 3개 엔드포인트 전부 401/403 단언).
- 관리자는 이미 `GET /api/admin/members`로 전 회원 id·이름·전화번호를 열람할 수 있으므로
  **신규 권한 경계 통과가 없다.** Phase 4의 R-04-02·R-04-03과 같은 성격이다.
- 노출되는 것은 내부 PK 하나이며 예외 **메시지**는 여전히 차단된다(`:180` 테스트).

**결정 (2026-08-16, 사용자 확정): 1번안 — 수용으로 확정.**

`T-05-23`("회원 id + 예외 메시지만 1000자 이내로 기록")을 **정본**으로 삼고,
`T-05-31`·`T-05D-15-02`의 "회원 id 미포함" 문구를 **오기로 정정**한다. 근거:

- 관리자는 이미 `GET /api/admin/members`로 전 회원을 열람하므로 **새로 넘는 권한 경계가 없다.**
- 실패 회원 id가 없으면 `PARTIAL_FAILURE` 이력만으로 **어느 회원을 복구해야 하는지 특정할 수 없다.**
  이 저장소의 Core Value("회원이 보는 잔여 = 실제 사용 가능 횟수")를 되돌리는 작업이 불가능해진다.
- 차단해야 할 것(예외 **메시지**·SQL·제약조건명)은 계속 차단된다.

**계약을 고정하는 테스트는 이미 존재한다** — `InactivityBatchFailureIsolationTest`가
회원 id 포함(`:159`)·예외 **종류**만 포함·SQL 제약조건 메시지 배제(`:188`, `:251`)를 함께 단언한다.
`BatchExecutionResponse.from`은 `errorSummary`를 그대로 통과시키므로 HTTP 계층에 별도 실패 경로
테스트를 만들지 않았다 — 러너 레벨에서 계약이 고정돼 있고, 실패를 강제로 주입하는 HTTP 테스트는
같은 사실을 더 취약한 방식으로 다시 증명할 뿐이다.

다만 `AdminBatchControllerTest.kt:370`의 이름이 실제 보장보다 넓다는 점은 남는다 — 이 테스트는
"이름·전화번호 문자열과 `memberIds` 필드명이 없다"만 단언하며, `errorSummary`의 회원 id는 **의도적으로
허용된다**. 다음에 이 테스트를 읽는 사람이 오해하지 않도록 이 문단을 근거로 남긴다.

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-05-01 | T-05-SC ×9 / T-05D-*-SC ×7 | 이 phase는 신규 외부 패키지를 설치하지 않는다. `git diff 8a89e4c..HEAD -- build.gradle.kts`로 의존성 추가 0줄 확인 — 변경은 테스트 태스크 `systemProperty` 2줄뿐 | 전 PLAN 공통 | 2026-08-16 |
| R-05-02 | T-05-06 | `batch_execution`에 정리 정책이 없다(삭제 메서드 부재를 코드로 확인). 하루 1~수 건(연 400건 미만) 규모라 보관 비용이 무의미하다. 필요가 관찰되면 도입 | 05-02-PLAN 승인 | 2026-08-16 |
| R-05-03 | T-05-29 | 관리자 수동 실행에 rate limit이 없다. 관리자 전용 경로이고, 실행 중 재호출은 409로 즉시 끊기며(`uq_batch_execution_running`) 접수는 202 비동기라 요청 스레드를 붙잡지 않는다 — 계획 시점보다 위험이 **줄었다** | 05-08-PLAN 승인 | 2026-08-16 |
| R-05-04 | T-05-34 | 로컬 검증 데이터(member 4·5, pass 6·7, pass_transaction, batch_execution)를 사용자 지시로 보존한다. AI가 임의 삭제하지 않는다(04-15 선례) | 사용자 결정 (05-09-SUMMARY) | 2026-08-15 |
| R-05-05 | T-05D-10-03 | `member.returned_from_leave_at`은 **마지막 이탈 시각만** 남기고 전이 이력 테이블을 만들지 않는다(D-111 기각안). 잔여 변경 자체는 `PassTransaction`이 전량 기록하므로 원장 감사 추적은 유지된다 | 05-10-PLAN 승인 | 2026-08-16 |
| R-05-06 | T-05D-12-03 / T-05D-14-02 | `BATCH_INACTIVITY_POLICY_EFFECTIVE_DATE`를 과거로 바꾸면 소급 차감 범위가 늘어난다. 배포 환경변수는 서버 운영자만 바꿀 수 있고, 1회 실행 상한(기본 1)이 피해 **속도**를 하루 1회로 묶는다. `.env.example:56`에는 키 이름만 두고 실값은 커밋하지 않는다 | 05-12/05-14-PLAN 승인 | 2026-08-16 |
| R-05-07 | T-05D-14-03 | 1회 실행 상한으로 잘린 부족분을 이력이 아니라 로그로만 남긴다 — `skippedCount`(대상 소진·경쟁 패배 전용, D-113)를 오염시키지 않기 위해서다. 원장과 기준일로 언제든 재계산 가능해 정보가 사라지지 않는다 | 05-14-PLAN 승인 | 2026-08-16 |
| R-05-08 | T-05-31 / T-05D-15-02 | `errorSummary`에 실패 회원 id가 담겨 관리자 응답으로 나간다. 관리자는 이미 전 회원 목록을 열람할 수 있어 **신규 권한 경계 통과가 없고**, 이 값 없이는 `PARTIAL_FAILURE` 이력으로 복구 대상을 특정할 수 없다. 예외 메시지·SQL은 계속 차단된다. 등록부의 T-05-23을 정본으로 삼고 T-05-31/T-05D-15-02 문구를 오기로 정정한다 | **사용자 확정** | 2026-08-16 |
| R-05-09 | 등록부 밖 (기록 목적) | ~~cron 킬 스위치가 **fail-open**이다~~ **해소(2026-08-17, D-121)** — `application.yml` 기본값을 `false`로, `@ConditionalOnProperty`의 `matchIfMissing`을 `false`로 뒤집어 **설정을 빠뜨리면 꺼지는 쪽으로 실패**하게 했다(fail-safe). 이제 자동 실행은 `BATCH_INACTIVITY_SCHEDULER_ENABLED=true`를 명시해야만 켜진다. 감사 시점(2026-08-16)에는 `.env.example`만 `false`였고 기본값 2곳이 `true`였다 | 사용자 확정 | 2026-08-17 |

---

## Known Residual Risks (등록부에 없으나 이번 phase가 만든 상태 — 기록 목적)

이 세 항목은 **새로 발견한 결함이 아니라** 리뷰·검증에서 이미 확인되어 범위 밖으로 남은 상태다.
"닫혔다"고 오해되지 않도록 정확히 기록한다.

| ID | 출처 | 상태 | 내용 |
|----|------|------|------|
| RES-05-01 | 05-REVIEW.md **WR-01** | **열림, 범위 밖** | `V9:9-11`이 `ck_pass_transaction_subject`를 "정확히 하나"에서 "최대 하나"로 완화해, **사람 조작 이력의 주체 누락을 DB가 더 이상 막지 못한다.** 배치 행(둘 다 NULL)을 표현하려면 불가피한 완화였고(D-110), "둘 다 채움"은 여전히 거부된다(`PassRepositoryTest.kt:174`). 현재는 사람 경로 5곳이 모두 주체를 채우는 것을 코드로 확인했지만(`AdminPassService.kt:89,145,299`·`ReservationLedgerSupport.kt:147,229`), 앞으로 주체를 빠뜨린 코드가 들어와도 **DB가 잡아주지 않는다** — 방어선이 CHECK에서 코드리뷰·테스트로 내려왔다. 감사 추적("누가 이 차감을 했는가")이 약해진 지점이다. 등록부 T-05-03의 완화책(at-most-one으로만 완화)은 계획대로 구현됐으므로 T-05-03 자체는 CLOSED다 |
| RES-05-02 | 05-REVIEW.md **CR-03** | **열림, Phase 6 이월** | 기준일 후보 ①(마지막 출석일)이 `InactivityBatchRunner.kt:158`에 `null` 하드코딩이다(Phase 6이 `Attendance`를 도입할 때까지). 저녁반 전용 `SESSION_PASS` 회원은 실제로 출석해도 후보가 없어 2주마다 부당 차감된다. 대응은 `BATCH_INACTIVITY_SCHEDULER_ENABLED=false`로 cron을 꺼 둔 채 운영 배포하는 것(D-116·D-119). 이번 브랜치에서 `.env.example:54`를 `false`로 바꿨으나 `application.yml` 기본값은 여전히 `true`다 → R-05-09 |
| RES-05-03 | 05-REVIEW.md **WR-06** | **열림, 사용자 판단 대기** | `INACTIVE`(탈퇴) 회원의 `SESSION_PASS`도 차감 대상에 남는다 — `PassRepository.kt:162`가 제외하는 것은 `ON_LEAVE`뿐이다. policies §4.3의 예외 3종(휴회·잔여 0·만료)에 `INACTIVE`가 없어 문면상 구현이 틀린 것은 아니나, 탈퇴 회원 잔여가 계속 깎여 0이 되면 환불 분쟁 소지가 있다. `05-VERIFICATION.md` human_verification 1번 |

---

## Process Observations (WARNING — 차단 사유 아님)

| ID | 내용 |
|----|------|
| P-05-01 | **`## Threat Flags` 섹션이 16개 SUMMARY 중 5개에만 있다** (05-06·07·13·14·15). 나머지 11개(05-01~05·08~12·16)에는 `threat` 문자열 자체가 없다. 실행자가 구현 중 발견한 신규 공격 표면을 자기 신고하는 창구가 그 플랜들에서는 비어 있었다는 뜻이다 — 이번 감사는 등록부 + 코드 직접 검증으로 보완했으나, 다음 phase에서는 SUMMARY 템플릿에 이 섹션을 강제하는 편이 낫다. **미매핑 신규 표면(unregistered_flag)은 발견되지 않았다**: 이 phase가 연 신규 엔드포인트 3종은 전부 T-05-27·T-05D-15-01에 등록돼 있고, 신규 스키마 변경 2건(V9·V10)도 T-05-03·T-05D-12-01에 등록돼 있다 |
| P-05-02 | **`Member.kt:30` KDoc이 구현과 모순된다** — "`returnedFromLeaveAt`은 `ON_LEAVE` → `ACTIVE` 전이에서만 갱신한다"고 적혀 있으나 실제는 `AdminMemberService.kt:143`이 `ON_LEAVE` → **모든 상태**에서 갱신한다(CR-04 대응, D-111 정정). `V10:27-32`가 V9 주석은 정정했지만 엔티티 KDoc은 갱신되지 않았다. 보안상 직접 위험은 없으나, 이 KDoc을 정본으로 읽은 개발자가 서비스 코드를 "고치면" CR-04(휴회 기간 소급 차감)가 되살아난다 — 문서 드리프트가 결함을 재유입시키는 전형적 경로다 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-16 | 74 | 72 | 2 | gsd-security-auditor (Phase 05 batch, 단일 감사관) |

**감사 방식:** 플랜·SUMMARY의 "구현했다"는 서술을 근거로 삼지 않고, 구현 코드·마이그레이션 DDL·
테스트 단언을 직접 읽어 `파일:라인`으로 인용했다. 특히 다음을 독립 검증했다:
- `accept` 16건은 "수용해도 되는가"가 아니라 **수용 근거가 사실인가**를 확인했다
  (예: T-05-06은 `BatchExecutionRepository` 전체를 읽어 삭제 메서드 부재를, T-05-SC는
  `git diff 8a89e4c..HEAD -- build.gradle.kts`로 의존성 변경 0줄을, T-05D-12-03은
  `.env.example`의 실값 부재를 확인).
- `n/a` 7건은 전제("새 의존성 없음")를 같은 diff로 실제 확인했다.
- 인가(T-05-27·T-05D-15-01)는 단일 grep으로 끝내지 않고 **3개 엔드포인트 전부**에 401/403
  단언이 있는지 확인했다(`AdminBatchControllerTest.kt:328,340,353`).
- T-05-31/T-05D-15-02는 인용된 테스트를 실제로 읽어 **그 테스트가 무엇을 단언하지 않는지**까지
  확인해 PARTIAL로 뒤집었다 — 코드 구조가 "안전해 보인다"에서 멈추지 않았다.

**구현 파일은 수정하지 않았다.** 이 감사의 산출물은 이 문서 하나다.

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer / n/a)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` — 충족 (T-05-31·T-05D-15-02를 R-05-08로 수용, 2026-08-16 사용자 확정)
- [x] `status: secured` — 충족 (2026-08-16)
- [x] 등록부 밖 잔여 위험 3건(RES-05-01~03) 상태 기록
- [x] 프로세스 관찰 2건(P-05-01~02) 기록

**해소 (2026-08-16):** R-05-08이 사용자 확정으로 승인돼 두 위협이 accepted risk로 닫혔다.
`threats_open: 0`.

**Next (Phase 5 밖):**
1. ~~dev→main 배포 시 환경변수를 반드시 넣는다~~ **해소(2026-08-17, D-121)** — 기본값을 뒤집어
   fail-safe로 만들었다. 이제 환경변수를 넣지 **않으면** cron이 돌지 않는다. Phase 6에서 켤 때
   `BATCH_INACTIVITY_SCHEDULER_ENABLED=true`를 명시한다.
2. **RES-05-03(WR-06)** — `INACTIVE`(탈퇴) 회원의 `SESSION_PASS`가 계속 차감 대상인지 사용자 결정 필요.
3. **Phase 6** — RES-05-02(출석일 후보) 해소 후 cron을 켠다. 그때 `Member.kt`·`application.yml`
   기본값도 함께 재검토한다.
