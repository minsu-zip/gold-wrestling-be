---
phase: 3
slug: pass
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-08-03
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ (`spring-boot-starter-webmvc-test`/`-data-jpa-test`) + Testcontainers 2.x (`testcontainers-postgresql`) |
| **Config file** | 없음 — `TestcontainersConfiguration.kt`(`@ServiceConnection`) 기존 배선 재사용 |
| **Quick run command** | `./gradlew test --tests "com.goldwrestling.pass.*"` |
| **Full suite command** | `./gradlew build` (ktlintCheck + compileKotlin + test 전부 포함) |
| **Estimated runtime** | quick ~60s (Testcontainers 기동 포함) / full ~수 분 |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.goldwrestling.pass.*"`
- **After every plan wave:** Run `./gradlew build`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 03-04/T1, 03-06/T3 | 03-04, 03-06 | 4, 5 | PASS-01 | T-03-23 | 등록 시 타입별 시작일/유효기간 계산 + `INITIAL_GRANT` 이력 | unit + integration | `./gradlew test --tests "com.goldwrestling.pass.PassRegistrationTest" --tests "com.goldwrestling.pass.AdminPassControllerTest"` | ✅ | ✅ green |
| 03-02/T3, 03-07/T3 | 03-02, 03-07 | 2, 6 | PASS-02 | T-03-27 | 모든 차감/복구가 `PassTransaction`으로 남고 이력 없는 변경 불가 (잔여 = 이력 합계) | integration | `./gradlew test --tests "com.goldwrestling.pass.PassLedgerInvariantTest" --tests "com.goldwrestling.pass.PassRepositoryTest"` | ✅ | ✅ green |
| 03-03/T1, 03-07/T2 | 03-03, 03-07 | 3, 6 | PASS-03 | T-03-26 | 수동 가감 0.5단위·음수 거부·기간제 제외 | unit + integration | `./gradlew test --tests "com.goldwrestling.pass.PassAdjustmentPolicyTest" --tests "com.goldwrestling.pass.AdminPassControllerTest"` | ✅ | ✅ green |
| 03-05/T1, 03-08/T2 | 03-05, 03-08 | 5, 7 | PASS-04 | T-03-33 | 저녁반 기간 수정 + `PassPeriodChange` 기록 | unit + integration | `./gradlew test --tests "com.goldwrestling.pass.PassPeriodChangeTest" --tests "com.goldwrestling.pass.AdminPassControllerTest"` | ✅ | ✅ green |
| 03-10/T3 | 03-10 | 9 | PASS-05 | T-03-41 | 본인 이용권 조회(만료·소진 포함, 취소 제외) | integration | `./gradlew test --tests "com.goldwrestling.pass.MemberPassControllerTest" --tests "com.goldwrestling.pass.PassDisplayStatusTest"` | ✅ | ✅ green |
| 03-10/T3 | 03-10 | 9 | PASS-06 | T-03-42 | 본인 이력 조회(이용권별 필터 + 페이지네이션) | integration | `./gradlew test --tests "com.goldwrestling.pass.MemberPassTransactionControllerTest"` | ✅ | ✅ green |
| 03-05/T1, 03-08/T2 | 03-05, 03-08 | 5, 7 | PASS-07 | T-03-32 | 횟수권 유효기간 수정 (PASS-04와 통합 엔드포인트) | unit + integration | `./gradlew test --tests "com.goldwrestling.pass.PassPeriodChangeTest" --tests "com.goldwrestling.pass.AdminPassControllerTest"` | ✅ | ✅ green |
| 03-05/T1, 03-09/T3 | 03-05, 03-09 | 5, 8 | PASS-08 | T-03-37 | 등록 취소 + `REGISTRATION_CANCELED` 상쇄 이력, 화면별 노출 차등 | unit + integration | `./gradlew test --tests "com.goldwrestling.pass.PassCancellationTest" --tests "com.goldwrestling.pass.AdminPassControllerTest"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*
*참고: 각 요구사항은 단위테스트(RED 플랜)와 통합테스트(엔드포인트 플랜) 양쪽에서 증명된다. Plan/Wave/Task ID는 두 기여 플랜을 모두 표기했다. Threat Ref는 03-11-PLAN.md가 지정한 PASS-02·03·05·06(T-03-27·T-03-26·T-03-41·T-03-42) 외 나머지는 각 플랜 STRIDE 표에서 해당 요구사항과 가장 직접 연관된 항목을 선정했다.*

---

## Wave 0 Requirements

- [x] `pass/PassAdjustmentPolicyTest.kt` — 순수 단위테스트, PASS-03 (TDD 대상) — 03-03/T1에서 존재 확인
- [x] `pass/PassRegistrationTest.kt` — 순수 단위테스트(기간·유효기간 계산), PASS-01 — 03-04/T1에서 존재 확인
- [x] `pass/PassPeriodChangeTest.kt` — 단위+통합, PASS-04/07 — 03-05/T1에서 존재 확인
- [x] `pass/PassCancellationTest.kt` — 단위+통합, PASS-08 — 03-05/T1에서 존재 확인
- [x] `pass/PassLedgerInvariantTest.kt` — 통합, "잔여 = 이력 합계" 증명, PASS-02 (TDD 대상) — 03-07/T3에서 존재 확인
- [x] `pass/AdminPassControllerTest.kt` — 통합(인가·성공·대표실패), PASS-01/03/04/07/08 — 03-06/T3에서 존재 확인
- [x] `pass/MemberPassControllerTest.kt` — 통합, PASS-05 — 03-10/T3에서 존재 확인
- [x] `pass/MemberPassTransactionControllerTest.kt` — 통합(페이지네이션·필터), PASS-06 — 03-10/T3에서 존재 확인
- 프레임워크 신규 설치: 없음 — 기존 JUnit5/AssertJ/MockMvc/Testcontainers로 충분 (계획대로)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| openapi.yaml 재생성 확인 | PASS-01~08 | 생성 태스크(generateApiDocs) 실행 결과물 diff 확인 | `./gradlew generateApiDocs` 후 `git diff docs/api/openapi.yaml` — 03-11/Task 1에서 실행, diff 없음(0건) 확인 완료 |
| 관리자 흐름 6단계 수동 확인 + D-068·D-070 재확인 | PASS-01~08 | 자동 테스트가 잡지 못하는 FE 계약 적합성·설계 결정 재확인 | 03-11/Task 3 checkpoint:human-verify — 사용자가 실제 요청으로 등록→가감→기간수정→취소→관리자 목록→본인 조회를 완주 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** 03-11 실행 완료 시점(Task 1·2 자동 검증 green) 기준 승인. Task 3(사람 확인)은 별도 체크포인트 응답으로 최종 확정.
