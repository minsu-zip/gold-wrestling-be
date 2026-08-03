---
phase: 3
slug: pass
status: draft
nyquist_compliant: false
wave_0_complete: false
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

> Task ID는 플랜 작성 후 채워진다. 요구사항 단위 매핑은 아래와 같다.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | TBD | TBD | PASS-01 | TBD | 등록 시 타입별 시작일/유효기간 계산 + `INITIAL_GRANT` 이력 | unit + integration | `./gradlew test --tests "com.goldwrestling.pass.PassRegistrationTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | PASS-02 | TBD | 모든 차감/복구가 `PassTransaction`으로 남고 이력 없는 변경 불가 (잔여 = 이력 합계) | integration | `./gradlew test --tests "com.goldwrestling.pass.PassLedgerInvariantTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | PASS-03 | TBD | 수동 가감 0.5단위·음수 거부·기간제 제외 | unit + integration | `./gradlew test --tests "com.goldwrestling.pass.PassAdjustmentPolicyTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | PASS-04 | TBD | 저녁반 기간 수정 + `PassPeriodChange` 기록 | unit + integration | `./gradlew test --tests "com.goldwrestling.pass.PassPeriodChangeTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | PASS-05 | TBD | 본인 이용권 조회(만료·소진 포함, 취소 제외) | integration | `./gradlew test --tests "com.goldwrestling.pass.MemberPassControllerTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | PASS-06 | TBD | 본인 이력 조회(이용권별 필터 + 페이지네이션) | integration | `./gradlew test --tests "com.goldwrestling.pass.MemberPassTransactionControllerTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | PASS-07 | TBD | 횟수권 유효기간 수정 (PASS-04와 통합 엔드포인트) | unit + integration | `./gradlew test --tests "com.goldwrestling.pass.PassPeriodChangeTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | PASS-08 | TBD | 등록 취소 + `REGISTRATION_CANCELED` 상쇄 이력, 화면별 노출 차등 | unit + integration | `./gradlew test --tests "com.goldwrestling.pass.PassCancellationTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `pass/PassAdjustmentPolicyTest.kt` — 순수 단위테스트, PASS-03 (TDD 대상)
- [ ] `pass/PassRegistrationTest.kt` — 순수 단위테스트(기간·유효기간 계산), PASS-01
- [ ] `pass/PassPeriodChangeTest.kt` — 단위+통합, PASS-04/07
- [ ] `pass/PassCancellationTest.kt` — 단위+통합, PASS-08
- [ ] `pass/PassLedgerInvariantTest.kt` — 통합, "잔여 = 이력 합계" 증명, PASS-02 (TDD 대상)
- [ ] `pass/AdminPassControllerTest.kt` — 통합(인가·성공·대표실패), PASS-01/03/04/07/08
- [ ] `pass/MemberPassControllerTest.kt` — 통합, PASS-05
- [ ] `pass/MemberPassTransactionControllerTest.kt` — 통합(페이지네이션·필터), PASS-06
- 프레임워크 신규 설치: 없음 — 기존 JUnit5/AssertJ/MockMvc/Testcontainers로 충분

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| openapi.yaml 재생성 확인 | PASS-01~08 | 생성 태스크(generateApiDocs) 실행 결과물 diff 확인 | `./gradlew generateApiDocs` 후 `git diff docs/api/openapi.yaml` |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
