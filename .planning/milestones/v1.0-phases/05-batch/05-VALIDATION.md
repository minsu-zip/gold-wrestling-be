---
phase: 5
slug: batch
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-08-15
updated: 2026-08-15
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Kotlin (Spring Boot 4.1.x test starters) + Testcontainers 2.x (PostgreSQL) |
| **Config file** | `build.gradle.kts` (기존 배선 재사용 — Wave 0 설치 불필요) |
| **Quick run command** | `./gradlew test --tests "com.goldwrestling.batch.*"` (태스크별 명령은 아래 검증 맵의 Automated Command 열) |
| **Full suite command** | `./gradlew ktlintFormat && ./gradlew build` |
| **Estimated runtime** | ~120 seconds (Testcontainers 기동 포함) |

---

## Sampling Rate

- **After every task commit:** Run quick run command (해당 태스크의 테스트 클래스)
- **After every plan wave:** Run `./gradlew build`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

TDD 플랜(05-03, 05-05)은 태스크가 아니라 **feature 게이트(RED→GREEN 사이클)** 단위로 행을 구성한다.

| Task ID | Plan | Wave | Requirement | Threat Ref | Test Type | Automated Command | Status |
|---------|------|------|-------------|------------|-----------|-------------------|--------|
| 05-01-T1 | 05-01 | 1 | BATCH-01 | — | 문서 정합(grep) | `grep -c "BatchExecution\|BatchTrigger\|BatchExecutionStatus\|returnedFromLeaveAt\|shortfall" docs/glossary.md` | ⬜ pending |
| 05-01-T2 | 05-01 | 1 | BATCH-01, BATCH-04 | T-05-02 | 문서 정합(grep) | `grep -c "^## D-" docs/decisions.md` | ⬜ pending |
| 05-01-T3 | 05-01 | 1 | BATCH-03 | T-05-01 | 문서 정합(grep) | `grep -n "D-107" .planning/REQUIREMENTS.md .planning/ROADMAP.md` | ⬜ pending |
| 05-02-T1 | 05-02 | 2 | BATCH-01, BATCH-04 | T-05-03, T-05-04 | 통합(Testcontainers) | `./gradlew test --tests "com.goldwrestling.db.FlywayMigrationIntegrationTest" --tests "com.goldwrestling.pass.PassRepositoryTest"` | ⬜ pending |
| 05-02-T2 | 05-02 | 2 | BATCH-04 | T-05-04, T-05-06 | 통합(Testcontainers) | `./gradlew test --tests "com.goldwrestling.batch.BatchExecutionRepositoryTest"` | ⬜ pending |
| 05-02-T3 | 05-02 | 2 | BATCH-01 | T-05-05 | 통합(Testcontainers) | `./gradlew test --tests "com.goldwrestling.member.MemberStatusChangeTest" --tests "com.goldwrestling.member.MemberApprovalTest"` | ⬜ pending |
| 05-03-G1 | 05-03 (tdd) | 3 | BATCH-01 | T-05-09 | 단위(스프링 없음) | `./gradlew test --tests "com.goldwrestling.batch.InactivityDueDateCalculatorTest"` | ⬜ pending |
| 05-03-G2 | 05-03 (tdd) | 3 | BATCH-04 | T-05-07, T-05-08 | 단위(스프링 없음) | `./gradlew test --tests "com.goldwrestling.batch.InactivityDueDateCalculatorTest"` | ⬜ pending |
| 05-04-T1 | 05-04 | 4 | BATCH-01, BATCH-02 | T-05-10, T-05-11, T-05-12 | 통합(Testcontainers) | `./gradlew test --tests "com.goldwrestling.batch.InactivityBatchQueryTest"` | ⬜ pending |
| 05-04-T2 | 05-04 | 4 | BATCH-01 | T-05-13, T-05-14 | 통합(Testcontainers) | `./gradlew test --tests "com.goldwrestling.batch.InactivityBatchQueryTest"` | ⬜ pending |
| 05-05-G1 | 05-05 (tdd) | 5 | BATCH-01, BATCH-02 | T-05-16, T-05-17, T-05-18 | 통합(Testcontainers) | `./gradlew test --tests "com.goldwrestling.batch.InactivityDeductionServiceTest"` | ⬜ pending |
| 05-05-G2 | 05-05 (tdd) | 5 | BATCH-02 | T-05-15 | 단위(Mockito) | `./gradlew test --tests "com.goldwrestling.batch.InactivityDeductionRaceTest"` | ⬜ pending |
| 05-06-T1 | 05-06 | 6 | BATCH-01, BATCH-02, BATCH-04 | T-05-19, T-05-20, T-05-21, T-05-22, T-05-23 | 통합(Testcontainers) | `./gradlew test --tests "com.goldwrestling.batch.InactivityBatchRunnerTest"` | ⬜ pending |
| 05-07-T1 | 05-07 | 7 | BATCH-04 | T-05-24, T-05-26 | 통합(멱등·캐치업) | `./gradlew test --tests "com.goldwrestling.batch.InactivityBatchIdempotencyTest"` | ⬜ pending |
| 05-07-T2 | 05-07 | 7 | BATCH-03 | T-05-25 | 통합(만료 실증) | `./gradlew test --tests "com.goldwrestling.batch.InactivityBatchExpiryVerificationTest"` | ⬜ pending |
| 05-08-T1 | 05-08 | 8 | BATCH-01 | T-05-30 | 컴파일 + 정적(grep) | `./gradlew compileKotlin && grep -c "@Scheduled(cron = \"0 0 4 * * *\", zone = SEOUL_ZONE_ID)" src/main/kotlin/com/goldwrestling/batch/InactivityBatchScheduler.kt` | ⬜ pending |
| 05-08-T2 | 05-08 | 8 | BATCH-04 | T-05-27, T-05-28, T-05-31 | 통합(MockMvc + 인증) | `./gradlew test --tests "com.goldwrestling.batch.AdminBatchControllerTest"` | ⬜ pending |
| 05-08-T3 | 05-08 | 8 | BATCH-04 | — | 계약 정합(grep) | `grep -c "inactivity-runs" docs/api/openapi.yaml` | ⬜ pending |
| 05-09-T1 | 05-09 | 9 | BATCH-01, BATCH-02, BATCH-03, BATCH-04 | T-05-32 | 전체 스위트 | `./gradlew build` | ⬜ pending |
| 05-09-T2 | 05-09 | 9 | BATCH-01, BATCH-04 | T-05-33 | 문서 정합(grep) | `grep -c "batch_execution\|inactivity-runs" docs/decisions.md` | ⬜ pending |
| 05-09-T3 | 05-09 | 9 | BATCH-01, BATCH-02, BATCH-04 | T-05-34 | **수동(checkpoint)** — 아래 Manual-Only 표 참조. 자동 커버는 05-09-T1 | (없음 — `<human-check>`) | ⬜ pending |

### 갭 클로저 (wave 10~16, 청크 D) — 2026-08-16 추가

플랜 체커가 지적한 대로 위 표는 wave 1~9(본 작업, 05-01~05-09)까지만 담고 있었다. 아래 21개
태스크가 갭 클로저(05-10~05-16)의 검증 맵이다. 두 동시성 테스트(`InactivityBatchRunConcurrencyTest`,
`AdminBatchRunConcurrencyTest`)는 각각 러너 레벨(`run()` 4개 동시 호출)과 HTTP 레벨(동시 POST 2건)에서
BATCH-04("배치는 멱등하다 — 같은 날 중복 실행돼도 이중 차감 0건")를 실증한다.

| Task ID | Plan | Wave | Requirement | Threat Ref | Test Type | Automated Command | Status |
|---------|------|------|-------------|------------|-----------|-------------------|--------|
| 05-10-T1 | 05-10 | 10 | BATCH-02 | T-05D-10-01 | 통합(Testcontainers) | `./gradlew cleanTest test --tests 'com.goldwrestling.member.MemberStatusChangeTest'` | ✅ green |
| 05-10-T2 | 05-10 | 10 | BATCH-02 | T-05D-10-02, T-05D-10-03 | 통합(Testcontainers) — 휴회 우회 복귀 소급 차감 부재 실증 | `./gradlew cleanTest test --tests 'com.goldwrestling.batch.InactivityLeaveReturnTest'` | ✅ green |
| 05-11-T1 | 05-11 | 11 | BATCH-04 | T-05D-11-01 | 마이그레이션 정적 검증 | `test -f src/main/resources/db/migration/V10__allow_running_batch_execution.sql && grep -c "WHERE status = 'RUNNING'" ...` | ✅ green |
| 05-11-T2 | 05-11 | 11 | BATCH-04 | T-05D-11-02 | 단위 + 통합(Testcontainers) | `./gradlew ktlintFormat && ./gradlew cleanTest test --tests 'com.goldwrestling.batch.InactivityBatchRunnerTest'` | ✅ green |
| 05-11-T3 | 05-11 | 11 | BATCH-04 | T-05D-11-01, T-05D-11-03 | 통합(Testcontainers) — RUNNING 유일성·stale 정리·목록 조회 | `./gradlew ktlintFormat && ./gradlew cleanTest test --tests 'com.goldwrestling.batch.BatchExecutionRepositoryTest'` | ✅ green |
| 05-12-T1 | 05-12 | 12 | BATCH-04 | T-05D-12-01 | 컴파일(설정 클래스, conventions §10.0 면제) | `./gradlew ktlintFormat && ./gradlew compileKotlin` | ✅ green |
| 05-12-T2 | 05-12 | 12 | BATCH-04 | T-05D-12-03 | 컴파일(예외·에러코드 선언) — HTTP 409 계약 검증은 05-15가 담당 | `./gradlew ktlintFormat && ./gradlew compileKotlin` | ✅ green |
| 05-12-T3 | 05-12 | 12 | BATCH-04 | T-05D-12-02, T-05D-12-04 | 통합(Testcontainers) — REQUIRES_NEW 커밋 동작 실증 | `./gradlew ktlintFormat && ./gradlew cleanTest test --tests 'com.goldwrestling.batch.BatchExecutionRecorderTest'` | ✅ green |
| 05-13-T1 | 05-13 | 13 | BATCH-04 | T-05D-13-01, T-05D-13-02 | 통합(Testcontainers) — 러너 3분할, 전체 실패 FAILED 확정 | `./gradlew ktlintFormat && ./gradlew cleanTest test --tests 'com.goldwrestling.batch.InactivityBatchFailureIsolationTest' --tests 'com.goldwrestling.batch.InactivityBatchRunnerTest' --tests 'com.goldwrestling.batch.InactivityBatchIdempotencyTest'` | ✅ green |
| 05-13-T2 | 05-13 | 13 | BATCH-04 | T-05D-13-03 | 단위(스프링 없음) — cron 거부·실패 흡수 | `./gradlew ktlintFormat && ./gradlew cleanTest test --tests 'com.goldwrestling.batch.InactivityBatchSchedulerTest'` | ✅ green |
| 05-13-T3 | 05-13 | 13 | **BATCH-04** | T-05D-13-04 | **동시성(Testcontainers) — `run()` 4개 동시 호출 → 총 `INACTIVITY` 차감 1회** | `./gradlew ktlintFormat && ./gradlew cleanTest test --tests 'com.goldwrestling.batch.InactivityBatchRunConcurrencyTest'` | ✅ green (반복 3회) |
| 05-14-T1 | 05-14 | 14 | BATCH-01 | T-05D-14-01 | 단위(스프링 없음, 순수 함수) — 정책 시행일 하한 | `./gradlew ktlintFormat && ./gradlew cleanTest test --tests 'com.goldwrestling.batch.InactivityDueDateCalculatorTest'` | ✅ green |
| 05-14-T2 | 05-14 | 14 | BATCH-01 | T-05D-14-02, T-05D-14-03 | 통합(Testcontainers) — 1회 실행 상한·시행일 경계·캐치업 이어받기 | `./gradlew ktlintFormat && ./gradlew cleanTest test --tests 'com.goldwrestling.batch.InactivityBatchPolicyLimitTest' --tests 'com.goldwrestling.batch.InactivityBatchIdempotencyTest'` | ✅ green |
| 05-15-T1 | 05-15 | 15 | BATCH-01, BATCH-04 | T-05D-15-01 | 컴파일(전용 실행기·접수 서비스 배선) | `./gradlew ktlintFormat && ./gradlew compileKotlin compileTestKotlin` | ✅ green |
| 05-15-T2 | 05-15 | 15 | BATCH-01, BATCH-04 | T-05D-15-01, T-05D-15-02 | 통합(MockMvc + 인증) — 202·Location·조회 2종 HTTP 계약 | `./gradlew ktlintFormat && ./gradlew cleanTest test --tests 'com.goldwrestling.batch.AdminBatchControllerTest'` | ✅ green |
| 05-15-T3 | 05-15 | 15 | **BATCH-04** | T-05D-15-03, T-05D-15-04, T-05D-15-05 | **동시성(MockMvc + Testcontainers) — 동시 POST 2건 → 202 정확히 1건, 나머지 409** | `./gradlew ktlintFormat && ./gradlew cleanTest test --tests 'com.goldwrestling.batch.AdminBatchRunConcurrencyTest'` | ✅ green (반복 3회) |
| 05-16-T1 | 05-16 | 16 | BATCH-01, BATCH-02, BATCH-03, BATCH-04 | T-05D-16-02 | 전체 스위트(캐시 없음) + 문서 정합(grep) | `./gradlew ktlintFormat && ./gradlew cleanTest test && ./gradlew build` | ✅ green (763 tests, 0 failures) |
| 05-16-T2 | 05-16 | 16 | BATCH-01, BATCH-04 | T-05D-16-01 | **수동(checkpoint)** — 로컬 실기동 202 접수·조회·잔여 변화 관찰 | `docker compose exec -T postgres psql ... "select count(*) from batch_execution where status='RUNNING'"` | ⬜ pending (사용자 확인 대기) |
| 05-16-T3 | 05-16 | 16 | BATCH-01, BATCH-02, BATCH-04 | — | 문서 정합(grep) — ROADMAP·REQUIREMENTS·VALIDATION·PATTERNS 갱신 | `grep -c '05-16-PLAN.md' .planning/ROADMAP.md && grep -c 'Gaps found' .planning/REQUIREMENTS.md` | ✅ green |

**BATCH-04 실증 근거 요약:** 러너 레벨 동시성(`InactivityBatchRunConcurrencyTest`, 05-13)과 HTTP
레벨 동시성(`AdminBatchRunConcurrencyTest`, 05-15) 두 층 모두에서 "동시 실행 → 총 차감 1회 또는
정확히 하나만 202"가 실제 PostgreSQL로 반복 검증됐다(각 3회 반복, 플레이키 아님). 순차 재실행
멱등은 05-07의 `InactivityBatchIdempotencyTest`가 이미 커버한다(변경 없음).

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Threat 커버리지 주석:** `T-05-06`(batch_execution 무한 증가), `T-05-29`(수동 실행 반복 호출),
`T-05-34`(로컬 검증 데이터 잔존), `T-05-SC`(패키지 설치 — 이번 phase 해당 없음)는 플랜에서
`accept` 처분이라 자동 검증 대상이 아니다. `mitigate` 처분 threat는 위 표에서 모두 최소 1개
자동 명령에 매핑돼 있다.

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements — **Wave 0 신규 작업 없음**.
(Testcontainers 배선·`FlywayMigrationIntegrationTest`·`PassLedgerInvariantTest`·`TestClockConfiguration`
선례 재사용, 신규 프레임워크 설치 없음. 검증 맵에 `MISSING —` 항목 0건.)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `@Scheduled` cron이 실제 새벽 시각에 발화 | BATCH-01 | 실시간 스케줄 발화는 테스트에서 대기 불가 — 로직은 서비스 분리로 단위/통합 검증하고 트리거만 수동 확인 | 로컬 기동 후 cron 표현식을 1분 뒤로 임시 변경해 로그 확인 (또는 수동 실행 API로 대체 확인) |
| 로컬 실기동 배치 실행 — 차감·이력·멱등 육안 확인 (05-09 Task 3) | BATCH-01, BATCH-02, BATCH-04 | 실제 DB·실제 관리자 토큰으로 도는 end-to-end 경로는 자동 스위트가 대체하지 않는다 (로직 자체는 05-05~05-07이 자동 커버) | 05-09-PLAN.md Task 3의 10단계 절차 수행 후 "승인" 응답. 자동 커버는 05-09-T1 `./gradlew build` |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies — 21행 중 20행 자동 명령 보유, 05-09-T3만 checkpoint(`<human-check>`)이며 Manual-Only에 등재
- [x] Sampling continuity: no 3 consecutive tasks without automated verify — 자동 검증 없는 태스크는 phase 최종 checkpoint 1건뿐
- [x] Wave 0 covers all MISSING references — `MISSING —` 참조 0건(기존 인프라로 충족)
- [x] No watch-mode flags — 전 명령이 1회 실행(`--continuous`/`--watch` 없음)
- [x] Feedback latency < 180s — 최장 명령은 Testcontainers 통합테스트 ~120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending (사용자 승인 대기 — 플래너 자체 검증 완료 2026-08-15)
