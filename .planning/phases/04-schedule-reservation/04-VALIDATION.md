---
phase: 4
slug: schedule-reservation
status: ready
nyquist_compliant: true
wave_0_complete: true
created: 2026-08-07
updated: 2026-08-07
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Kotlin (Spring Boot 4.1.x `-test` 스타터) + Testcontainers 2.x (PostgreSQL) |
| **Config file** | `build.gradle.kts` (기존 인프라 재사용) |
| **Quick run command** | `./gradlew test --tests "*<변경 도메인>*"` |
| **Full suite command** | `./gradlew build` (ktlintFormat 후) |
| **Estimated runtime** | ~120 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "*<변경 도메인>*"`
- **After every plan wave:** Run `./gradlew build`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 04-01-T1 | 04-01 | 1 | SCHED-02, RESV-03, RESV-04, RESV-09 | T-04-03 | 설계 결정(D-089~D-098) 미기록으로 인한 임의 재해석 차단 | static (grep) | `grep -v '^#' docs/decisions.md \| grep -c "D-09"` | ✅ 기존(docs/decisions.md) | ⬜ pending |
| 04-01-T2 | 04-01 | 1 | RESV-01~RESV-09 공통 | T-04-01, T-04-02, T-04-04 | 예외 메시지에 식별자 미노출 · 타인 예약은 404 · error-codes.md ↔ ErrorCode 양방향 일치 | unit | `./gradlew test --tests "com.goldwrestling.common.error.ErrorCodeRegistryTest"` | ✅ 기존(확장) | ⬜ pending |
| 04-02-T1 | 04-02 | 2 | SCHED-01, SCHED-02, RESV-06 | T-04-05, T-04-06, T-04-07 | 정원 CHECK · 1:1/중복 예약 부분 유니크 인덱스 · 취소 메타 완전성 | integration (Testcontainers) | `./gradlew test --tests "*FlywayMigrationIntegrationTest*"` | ✅ 기존(확장) | ⬜ pending |
| 04-02-T2 | 04-02 | 2 | SCHED-01 | — | 송파점 시간표 52행 시드 정합 | integration (Testcontainers) | `./gradlew test --tests "com.goldwrestling.schedule.ClassScheduleSeedIntegrationTest"` | ⬜ 신규 | ⬜ pending |
| 04-02-T3 | 04-02 | 2 | RESV-06, NOTIF-01 | T-04-08, T-04-09 | `ck_pass_transaction_subject` — admin/member 주체 배타성 | integration (Testcontainers) | `./gradlew test --tests "*FlywayMigrationIntegrationTest*"` | ✅ 기존(확장) | ⬜ pending |
| 04-03-T1 | 04-03 | 3 | SCHED-01, SCHED-02 | T-04-10 | 정원·휴강 조건이 같은 WHERE에 있는 조건부 UPDATE (0행 반환 단언) | integration (Testcontainers) | `./gradlew test --tests "com.goldwrestling.schedule.ClassSessionRepositoryTest"` | ⬜ 신규 | ⬜ pending |
| 04-03-T2 | 04-03 | 3 | RESV-01, RESV-02, RESV-06 | T-04-11, T-04-12 | 판정 메서드 무부작용 · 소유자 조건 포함 조회만 노출 | integration (Testcontainers) | `./gradlew test --tests "com.goldwrestling.reservation.ReservationRepositoryTest"` | ⬜ 신규 | ⬜ pending |
| 04-03-T3 | 04-03 | 3 | NOTIF-01, RESV-06 | T-04-13 | `Notification` 이력 불변(표시·연결 필드 `val`) | integration (Testcontainers) | `./gradlew test --tests "com.goldwrestling.pass.*"` | ✅ 기존(확장) | ⬜ pending |
| 04-04-F1 | 04-04 | 4 | SCHED-02, RESV-04 | T-04-14, T-04-15, T-04-16, T-04-17 | 예약 오픈/마감 창 · 시작 정각 거부 · 조회 주 범위 제한 · 예외 메시지 무반사 | unit (TDD) | `./gradlew test --tests "com.goldwrestling.common.time.WeekRangeTest" --tests "com.goldwrestling.schedule.ReservationWindowTest"` | ⬜ 신규 | ⬜ pending |
| 04-05-T1 | 04-05 | 5 | SCHED-01 | T-04-22 | 동시 세션 생성 시 `uq_class_session` + `ON CONFLICT DO NOTHING`으로 행 1개 | concurrency (Testcontainers) | `./gradlew test --tests "com.goldwrestling.schedule.ClassSessionConcurrencyTest"` | ⬜ 신규 | ⬜ pending |
| 04-05-T2 | 04-05 | 5 | SCHED-01, SCHED-02 | T-04-18, T-04-19, T-04-20, T-04-21 | 회원 응답에 예약자 명단 없음(D-096) · 주 범위 제한 · 비활성 회원 403 · 배치 조회 | integration (MockMvc + Testcontainers) | `./gradlew test --tests "com.goldwrestling.schedule.MemberScheduleControllerTest"` | ⬜ 신규 | ⬜ pending |
| 04-05-T3 | 04-05 | 5 | SCHED-01, SCHED-02 | — | 청크 A 마감 — openapi.yaml 재생성 후 전체 그린 | build (full suite) | `./gradlew build` | ✅ 기존 | ⬜ pending |
| 04-06-F1 | 04-06 | 6 | RESV-01, RESV-02, RESV-03 | T-04-23, T-04-24, T-04-25, T-04-26 | 유효기간은 수업 날짜 기준 · 단일 이용권 잔여 기준 · 타 회원/취소 이용권 제외 | unit (TDD) | `./gradlew test --tests "com.goldwrestling.reservation.ReservationPassPolicyTest" --tests "com.goldwrestling.pass.PassDeductionCandidateTest"` | ⬜ 신규 | ⬜ pending |
| 04-07-T1 | 04-07 | 7 | NOTIF-01 | — | 관리자 알림 생성 경로 단일화 | integration (Testcontainers) | `./gradlew test --tests "com.goldwrestling.notification.NotificationServiceTest"` | ⬜ 신규 | ⬜ pending |
| 04-07-T2 | 04-07 | 7 | RESV-01, RESV-02 | T-04-27 ~ T-04-33 | 세션 id 미수신(서버 확보) · 요일 정합 · 조건부 UPDATE로만 정원 확보 · 차감↔이력 동일 트랜잭션 · 재시도 루프 없음 | integration (Testcontainers) | `./gradlew test --tests "com.goldwrestling.reservation.MemberReservationServiceTest"` | ⬜ 신규 | ⬜ pending |
| 04-08-T1 | 04-08 | 8 | RESV-01, RESV-02 | T-04-34, T-04-35 | 비활성 회원 403 `MEMBER_NOT_ACTIVE` · 관리자 토큰 403 | integration (MockMvc + Testcontainers) | `./gradlew test --tests "com.goldwrestling.reservation.MemberReservationControllerTest"` | ⬜ 신규 | ⬜ pending |
| 04-08-T2 | 04-08 | 8 | RESV-06 | T-04-36, T-04-37, T-04-38 | 초과 예약 0건 실증 · 이중 차감 방지 · 성공건수=예약행=이력=`reserved_count` 4자 일치 | concurrency (Testcontainers) | `./gradlew test --tests "com.goldwrestling.reservation.ReservationCapacityConcurrencyTest"` | ⬜ 신규 | ⬜ pending |
| 04-09-F1 | 04-09 | 9 | RESV-04 | T-04-40 ~ T-04-44 | 변경을 통한 당일 취소 우회 차단 · 날짜 기준 판정 고정 · 취소 이용권 복구 금지 · 복구 판정↔이력 결합 | unit (TDD) | `./gradlew test --tests "com.goldwrestling.reservation.ReservationCancellationPolicyTest" --tests "com.goldwrestling.reservation.ReservationRefundPolicyTest"` | ⬜ 신규 | ⬜ pending |
| 04-10-T1 | 04-10 | 10 | RESV-04, RESV-05, NOTIF-01 | T-04-47, T-04-48, T-04-49 | 취소+재예약 단일 트랜잭션(실패 시 기존 예약 ACTIVE 유지) · 복구 판정 하나로 잔여·이력 동시 처리 | integration (Testcontainers) | `./gradlew test --tests "com.goldwrestling.reservation.MemberReservationCancellationTest"` | ⬜ 신규 | ⬜ pending |
| 04-10-T2 | 04-10 | 10 | RESV-04, RESV-05 | T-04-45, T-04-46, T-04-50 | IDOR 방어(타인 예약 404) · 본인 목록 Specification · 비활성 회원 차단 | integration (MockMvc + Testcontainers) | `./gradlew test --tests "com.goldwrestling.reservation.MemberReservationControllerTest"` | ⬜ 신규(확장) | ⬜ pending |
| 04-10-T3 | 04-10 | 10 | RESV-04, RESV-05 | — | 청크 B 마감 — openapi.yaml 재생성 후 전체 그린 | build (full suite) | `./gradlew build` | ✅ 기존 | ⬜ pending |
| 04-11-T1 | 04-11 | 11 | SCHED-03 | T-04-52, T-04-54 | 명단 3필드 고정 · 취소 예약 명단·카운트 제외 · 배치 조회 2회 이하 · 주 정규화 | integration (Testcontainers) | `./gradlew test --tests "com.goldwrestling.schedule.AdminScheduleServiceTest"` | ⬜ 신규 | ⬜ pending |
| 04-11-T2 | 04-11 | 11 | SCHED-03 | T-04-51, T-04-53 | 회원 토큰 403 / 미인증 401 · 소속 지점 검증 | integration (MockMvc + Testcontainers) | `./gradlew test --tests "com.goldwrestling.schedule.AdminScheduleControllerTest"` | ⬜ 신규 | ⬜ pending |
| 04-12-T1 | 04-12 | 11 | RESV-07 | T-04-56, T-04-58, T-04-59 | LIKE 와일드카드 이스케이프 재사용 · fetch join으로 N+1 제거 · 전화번호 미포함 | integration (Testcontainers) | `./gradlew test --tests "com.goldwrestling.reservation.AdminReservationSearchTest"` | ⬜ 신규 | ⬜ pending |
| 04-12-T2 | 04-12 | 11 | RESV-07 | T-04-55, T-04-57 | 회원 토큰 403 · `size` 상한 `@Max(100)` 400 응답 | integration (MockMvc + Testcontainers) | `./gradlew test --tests "com.goldwrestling.reservation.AdminReservationSearchTest"` | ⬜ 신규(확장) | ⬜ pending |
| 04-13-T1 | 04-13 | 12 | RESV-08, NOTIF-01 | T-04-60, T-04-61, T-04-62, T-04-63 | 관리자 경로도 정원 검사 통과 · 이력 주체 `admin` 고정(`member == null`) · 취소 메타 완전성 | integration (Testcontainers) | `./gradlew test --tests "com.goldwrestling.reservation.AdminReservationCancellationTest"` | ⬜ 신규 | ⬜ pending |
| 04-13-T2 | 04-13 | 12 | RESV-08 | T-04-64 | 활성 예약 있는 이용권 등록 취소 거부(`PASS_HAS_ACTIVE_RESERVATION`), 상태·잔여 불변 | integration (Testcontainers) | `./gradlew test --tests "com.goldwrestling.pass.*"` | ✅ 기존(확장) | ⬜ pending |
| 04-14-T1 | 04-14 | 13 | RESV-09, NOTIF-01 | T-04-66, T-04-67, T-04-68, T-04-69 | 세션 선전환으로 끼어들기 예약 차단 · `CLASS_CANCELED_REFUND` 사유 · 세션당 알림 1건 | integration (Testcontainers) | `./gradlew test --tests "com.goldwrestling.schedule.ClassSessionSuspensionTest"` | ⬜ 신규 | ⬜ pending |
| 04-14-T2 | 04-14 | 13 | RESV-09 | T-04-70, T-04-71 | 휴강 해제 시 예약 미복원 고정 · 회원 토큰 403 | integration (MockMvc + Testcontainers) | `./gradlew test --tests "com.goldwrestling.schedule.ClassSessionSuspensionTest"` | ⬜ 신규(확장) | ⬜ pending |
| 04-15-T1 | 04-15 | 14 | 전체 13종 | — | 요구사항 커버리지 확인 + 전체 회귀 | build (clean full suite) | `./gradlew clean build` | ✅ 기존 | ⬜ pending |
| 04-15-T2 | 04-15 | 14 | 전체 13종 | T-04-72, T-04-75 | openapi.yaml ↔ 코드 계약 일치 · 설계 변경의 decisions.md 반영 | build (full suite) | `./gradlew build` | ✅ 기존 | ⬜ pending |
| 04-15-T3 | 04-15 | 14 | 전체 13종 | T-04-73, T-04-74 | 회원↔관리자 응답 차이(명단 노출 경계) · 이력 주체 배타성 육안 확인 | manual (checkpoint:human-verify) | `test -n "$GSD_CHECKPOINT_ANSWER" \|\| echo "사용자 확인 대기 — 자동 검증 대상 아님"` | — (확인 전용) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Sampling continuity:** 32개 태스크 중 31개가 실행형 `<automated>` 명령을 갖는다. 유일한 예외는
마지막 `04-15-T3`(휴먼 체크포인트)이고, 그 직전 `04-15-T1`·`04-15-T2`가 `./gradlew build`로
전체 회귀를 돌린다 — **자동 검증 없는 태스크가 3연속 나오는 구간이 없다.**

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements — Phase 1~3에서 Testcontainers 통합테스트·`ExecutorService` 동시성 테스트 인프라(`add-domain-test` 스킬) 구축 완료.*

미해결 `MISSING — Wave 0 must create ...` 참조 없음. 위 맵의 "⬜ 신규" 테스트 파일들은 전부
**해당 태스크가 프로덕션 코드와 같은 작업 안에서 직접 생성**하며(CLAUDE.md 규칙 10),
선행 스캐폴딩 태스크를 필요로 하지 않는다.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 회원 예약 → 관리자 보드 반영 → 대리 취소/휴강까지 **이어진 운영 흐름** | 전체 13종 (통합) | 자동 테스트는 각 경계를 개별 픽스처로 검증한다. 여러 API·역할·화면을 가로지르는 연속 흐름의 정합(잔여·명단·알림이 같은 조작에 대해 함께 맞는지)은 실제 실행 환경에서 한 번 이어서 봐야 회귀를 잡을 수 있다 | `04-15-PLAN.md` Task 3 `<how-to-verify>` A(회원 1~7) · B(관리자 8~14) · C(psql 15~16) 16개 항목 |

그 외 이 phase의 모든 동작은 자동 검증된다 — 도메인 판정은 단위 테스트, DB 제약·조회는
Testcontainers 통합 테스트, 인가·직렬화는 MockMvc 테스트, 초과 예약 0건은 동시성 테스트가 담당한다.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (미해결 MISSING 참조 없음)
- [x] No watch-mode flags (`--watch`·`--continuous` 사용 태스크 0건)
- [x] Feedback latency < 180s (태스크별 `--tests` 좁은 실행 ~30-60s, 전체 빌드 ~120s)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** ready — 15개 플랜 32개 태스크 전부 매핑 완료 (2026-08-07)
