---
phase: 6
slug: operations
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-08-18
---

# Phase 6 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Kotlin(`kotlin-test-junit5`) + AssertJ + Testcontainers 2.x (`testcontainers-postgresql`) |
| **Config file** | `build.gradle.kts` |
| **Quick run command** | `./gradlew test --tests "com.goldwrestling.attendance.*"` (해당 청크 패키지 범위, notice/notification 동일 패턴) |
| **Full suite command** | `./gradlew ktlintFormat && ./gradlew build` (phase 마감 시 `./gradlew cleanTest test` 직접 실행 — GSD 회귀 게이트 no-op 대응) |
| **Estimated runtime** | ~180 seconds (Testcontainers 포함) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "<변경 대상 패키지>.*"`
- **After every plan wave:** Run `./gradlew ktlintFormat && ./gradlew build`
- **Before `/gsd:verify-work`:** Full suite must be green + `./gradlew cleanTest test`로 캐시 없이 재확인
- **Max feedback latency:** 300 seconds

---

## Per-Task Verification Map

(출처: 06-RESEARCH.md "Phase Requirements → Test Map" — 06-01~06-11 PLAN.md의 `<automated>` 블록이 실제 게이트)

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 06-02-* | 02 | 1 | ATTEND-01 | plan `<threat_model>` | 회원×세션 일반 UNIQUE 제약(중복 출석 방지) | integration | `./gradlew test --tests AttendanceRepositoryTest` | ❌ W0 | ⬜ pending |
| 06-03-* | 03 | 2 | ATTEND-01 | plan `<threat_model>` | CR-03: ATTENDED만 후보 반영, 소급 출석이 INACTIVITY 이력 불변 | unit + integration | `./gradlew test --tests InactivityDueDateCalculatorTest`, `InactivityBatchRunnerTest` | ❌ W0(기존 확장) | ⬜ pending |
| 06-04-* | 04 | 2 | NOTICE-01, NOTICE-02 | plan `<threat_model>` | 관리자 CRUD·회원 열람 분리, ProblemDetail 에러 | integration | `./gradlew test --tests AdminNoticeControllerTest`, `MemberNoticeControllerTest` | ❌ W0 | ⬜ pending |
| 06-05-* | 05 | 3 | ATTEND-01, ATTEND-02 | plan `<threat_model>` | 회비 우선 판정·잔여 0.5 미만 거부(순수 판정) | unit (TDD) | `./gradlew test --tests EveningHalfDeductionPolicyTest` | ❌ W0 | ⬜ pending |
| 06-06-* | 06 | 4 | ATTEND-01 | plan `<threat_model>` | 예약제/1:1 출석 upsert, 차감 무관(레코드 부재=미체크) | integration | `./gradlew test --tests AttendanceServiceTest` | ❌ W0 | ⬜ pending |
| 06-07-* | 07 | 5 | ATTEND-02 | plan `<threat_model>` | 0.5 차감 원장 기록(EVENING_HALF) + 삭제 시 EVENING_HALF_REFUND 복구 | integration | `./gradlew test --tests AttendanceServiceTest` | ❌ W0 | ⬜ pending |
| 06-08-* | 08 | 6 | ATTEND-01, ATTEND-02 | plan `<threat_model>` | 동시 출석+차감 경쟁에도 이중 차감 0건, 409 ProblemDetail | integration + 동시성 | `./gradlew test --tests AdminAttendanceControllerTest`, `AttendanceConcurrencyTest` | ❌ W0 | ⬜ pending |
| 06-09-* | 09 | 7 | NOTIF-02 | plan `<threat_model>` | 미확인 카운트 정확, 모두 읽음 후 전부 isRead=true | integration | `./gradlew test --tests AdminNotificationControllerTest` | ❌ W0 | ⬜ pending |
| 06-10-* | 10 | 8 | NOTIF-03 | plan `<threat_model>` | 활동 피드 기간·종류 필터, 읽음 여부 무관 | integration | `./gradlew test --tests ActivityFeedTest` | ❌ W0 | ⬜ pending |
| 06-11-* | 11 | 9 | 전체 | plan `<threat_model>` | 전체 회귀 green + 로컬 실기동 확인 | full suite | `./gradlew cleanTest test` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*
*06-01(문서·에러코드)은 conventions.md §10.0 면제 대상(문서/enum 상수) — ErrorCode 등록은 06-08 HTTP 계약 테스트가 커버.*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/com/goldwrestling/attendance/AttendanceRepositoryTest.kt` — 유니크 제약, CR-03 벌크 조회
- [ ] `src/test/kotlin/com/goldwrestling/attendance/EveningHalfDeductionPolicyTest.kt` — 회비 우선·차감 판정 단위테스트
- [ ] `src/test/kotlin/com/goldwrestling/attendance/AttendanceServiceTest.kt` — 추가+차감, 삭제+복구, 체크 통합테스트
- [ ] `src/test/kotlin/com/goldwrestling/attendance/AdminAttendanceControllerTest.kt` — 성공/실패 경로
- [ ] `src/test/kotlin/com/goldwrestling/attendance/AttendanceConcurrencyTest.kt` — 동시 차감 경쟁
- [ ] `src/test/kotlin/com/goldwrestling/attendance/AttendanceFixtures.kt` — 픽스처 헬퍼
- [ ] `src/test/kotlin/com/goldwrestling/notice/*Test.kt` — 공지 CRUD·열람 통합테스트
- [ ] `src/test/kotlin/com/goldwrestling/notification/AdminNotificationControllerTest.kt`, `ActivityFeedTest.kt`
- [ ] `src/test/kotlin/com/goldwrestling/batch/InactivityDueDateCalculatorTest.kt` (기존 확장)
- [ ] `src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunnerTest.kt` (기존 확장)
- 프레임워크 설치: 불필요 — 기존 JUnit5/AssertJ/Testcontainers 배선 재사용

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 로컬 실기동 스모크 (06-11) | 전체 | 실제 부트 기동·docker-compose 환경은 CI 자동화 범위 밖 | `docker-compose up -d` → 부트 실행 → 출석/공지/알림 엔드포인트 수동 호출 확인 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies (plan-checker 8a 통과)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (8d 통과)
- [x] Wave 0 covers all MISSING references (8c 통과)
- [x] No watch-mode flags (8b 통과)
- [x] Feedback latency < 300s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-08-18 (plan-checker: 블로커 0건 — PLAN.md 태스크 레벨 automated verify 전수 통과)
