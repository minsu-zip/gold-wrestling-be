---
phase: 3
slug: 03-pass
status: verified
threats_open: 0
asvs_level: 1
created: 2026-08-04
---

# Phase 3 — Security (이용권)

> Per-phase security contract: threat register, accepted risks, and audit trail.
> Register 출처: `.planning/phases/03-pass/03-01-PLAN.md` ~ `03-11-PLAN.md`의 `<threat_model>` 블록 (register_authored_at_plan_time: true). 총 61건(T-03-01~50, T-03-SC × 11개 플랜).

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|----------------|
| 관리자 요청 → 도메인 판정 (`/api/admin/**`) | 관리자가 임의 수량·날짜·사유를 보낼 수 있는 지점. 형식 검증(DTO)만으로는 도메인 규칙을 막을 수 없다 | 가감 수량, 기간, 취소 사유 |
| 회원 클라이언트 → `/api/members/me/**` | 인증된 회원이 자신의 이용권·이력만 볼 수 있어야 하는 경계 | 조회 파라미터(`passId`, `page`, `size`) |
| 애플리케이션 → DB | 잔여 횟수·상태 변경이 이 경계를 넘는다. 동시 요청은 애플리케이션 조건문으로 막을 수 없다(D-021) | 잔여 횟수 UPDATE |
| 저장 데이터 → 회원 응답 | 관리자 전용 정보(취소 사유·가감 메모)가 새어나갈 수 있는 지점 | `cancelReason`, `note` |
| 코드 → FE 계약(openapi.yaml) | 재생성을 빠뜨리면 FE가 존재하지 않는 형태로 타입을 만든다 | API 스키마 |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-03-01 | Information Disclosure | `PassExceptions.kt` 메시지 | mitigate | 7개 예외 전부 한국어 사용자 대면 문구만 사용, `passId`·SQL·필드명 미포함 | closed |
| T-03-02 | Repudiation | 설계 결정 미기록 | mitigate | `docs/decisions.md` D-060~D-067 기록 확인 | closed |
| T-03-03 | Tampering | error-codes.md ↔ ErrorCode 불일치 | mitigate | `ErrorCodeRegistryTest.targetSectionPrefixes`에 `"## 이용권 코드"` 포함, 7종 1:1 확인 | closed |
| T-03-04 | Tampering | `PassRepository.adjustRemainingCount` | mitigate | JPQL `where ... + :amount >= 0` 확인 | closed |
| T-03-05 | Tampering | 취소된 이용권 재조작 | mitigate | 동일 JPQL에 `p.status = ACTIVE` 조건 확인 | closed |
| T-03-06 | Tampering | 타입별 컬럼 규칙 우회 | mitigate | `ck_pass_remaining_count_by_type` CHECK (V4) 확인 | closed |
| T-03-07 | Repudiation | 취소 메타데이터 누락 | mitigate | `ck_pass_cancellation` CHECK (V4) 확인 | closed |
| T-03-08 | Tampering | 의미 없는 0 수량 이력 | mitigate | `ck_pass_transaction_amount_nonzero` CHECK (V4) 확인 | closed |
| T-03-09 | Tampering | `validateAdjustment` 수량 검증 | mitigate | `Pass.validateAdjustment` 0.5 배수·0 금지·음수 결과 금지 구현 확인 (`Pass.kt:87-108`) | closed |
| T-03-10 | Tampering | 취소된 이용권 가감 | mitigate | `requireNotCanceled()`가 검사 1로 최우선 실행 + T-03-05 DB 조건 이중 확인 | closed |
| T-03-11 | Elevation of Privilege | 기간제 이용권 횟수 조작 | mitigate | `EVENING_MEMBERSHIP` 시 `PassTypeNotAdjustableException` + CHECK 이중 확인 | closed |
| T-03-12 | Tampering | `register` 초기 횟수 | mitigate | `Pass.register` → `validateInitialCount` 확인 (`Pass.kt:253-260`) | closed |
| T-03-13 | Tampering | 타입-필드 조합 우회 | mitigate | `Pass.register`의 `when(type)` 분기가 기간제 `initialCount`/횟수제 `term` 조합을 거부 확인 | closed |
| T-03-14 | Information Disclosure | 만료·소진 상태 오표시 | mitigate | `displayStatus`가 저장하지 않고 매 조회 계산, 우선순위 테스트 7건 확인(`PassDisplayStatusTest.kt`) | closed |
| T-03-15 | Tampering | 시각 조작 의존 | accept | `today`를 호출부(`Clock`)가 넘기는 구조 확인. 서버 시각 신뢰는 인프라 범위 — Accepted Risks Log 등재 | closed |
| T-03-16 | Tampering | `changePeriod` 시작일 우회 | mitigate | 횟수권 `resolvedStart != startDate` 거부 확인 (`Pass.kt:145-161`) | closed |
| T-03-17 | Tampering | 역전된 기간 저장 | mitigate | 엔티티 판정 + `ck_pass_period`·`ck_pass_period_change_period` CHECK 확인 | closed |
| T-03-18 | Tampering | 취소된 이용권 재조작 | mitigate | `changePeriod`·`cancel` 진입부 `requireNotCanceled()` 확인 | closed |
| T-03-19 | Repudiation | 취소 주체·사유 누락 | mitigate | `cancel()`이 3필드 동시 대입 + `ck_pass_cancellation` CHECK 확인 | closed |
| T-03-20 | Tampering | 상쇄 이력 없는 잔여 0 처리 | mitigate | `Pass.cancel`이 상쇄 수량 산출, `AdminPassService.cancel`이 0이 아닐 때만 이력 저장 확인 | closed |
| T-03-21 | Elevation of Privilege | `POST /api/admin/members/{id}/passes` | mitigate | `SecurityConfig`의 `/api/admin/**` → `hasRole("ADMIN")` 확인 + 401/403 테스트(`AdminPassControllerTest`) 확인 | closed |
| T-03-22 | Spoofing | 이력 주체 위조 | mitigate | 4개 컨트롤러 메서드 전부 `principal.requireAdminId()`만 사용, 바디에 `adminId` 필드 없음 확인 | closed |
| T-03-23 | Repudiation | 이력 없는 이용권 생성 | mitigate | `register`가 `Pass` 저장 + `INITIAL_GRANT` 저장을 같은 `@Transactional` 메서드 안에서 수행 확인 | closed |
| T-03-24 | Tampering | 비정상 초기 횟수 | mitigate | T-03-12과 동일 근거 + CHECK 이중 확인 | closed |
| T-03-25 | Information Disclosure | 다른 회원 정보 노출 | accept | 관리자 전용 경로라 스코프 제한 없음이 정상 — Accepted Risks Log 등재 | closed |
| T-03-26 | Tampering | 동시 가감으로 잔여 음수(TOCTOU) | mitigate | `adjustRemainingCount`의 `where ... >= 0` 원자적 UPDATE + 반환 0 시 재조회·예외 변환 확인 (`AdminPassService.adjust`) | closed |
| T-03-27 | Repudiation | 이력 없는 잔여 변경 | mitigate | 잔여 갱신과 `PassTransaction` 저장이 같은 트랜잭션, `PassLedgerInvariantTest` 7건 확인 | closed |
| T-03-28 | Spoofing | 가감 주체 위조 | mitigate | `principal.requireAdminId()` 확인 | closed |
| T-03-29 | Tampering | 취소된 이용권 가감 | mitigate | `validateAdjustment` 취소 검사 + `status = ACTIVE` DB 조건 이중 확인 | closed |
| T-03-30 | Information Disclosure | 실패 응답의 내부 정보 | mitigate | `GlobalExceptionHandler`가 `ex.message`(도메인 예외 고정 문구)만 사용, 스택트레이스 미노출 확인 | closed |
| T-03-31 | Tampering | 횟수권 시작일 변경 우회 | mitigate | 서버(`Pass.changePeriod`)가 거부, FE 의존 없음 확인 | closed |
| T-03-32 | Tampering | 역전된 기간 저장 | mitigate | 엔티티 판정 + CHECK 2종 확인 | closed |
| T-03-33 | Repudiation | 변경 이력 누락·전값 유실 | mitigate | `AdminPassService.changePeriod`가 `pass.changePeriod` 호출 전 전값을 지역 변수로 보관 후 이력 저장 확인 (`AdminPassService.kt:167-185`) | closed |
| T-03-34 | Spoofing | 변경 주체 위조 | mitigate | `principal.requireAdminId()` 확인 | closed |
| T-03-35 | Tampering | 취소된 이용권 기간 수정 | mitigate | `changePeriod` 진입부 `requireNotCanceled()` 확인 | closed |
| T-03-36 | Tampering | 취소 후 재조작(가감·기간수정·재취소) | mitigate | 3개 서비스 메서드 진입부 취소 검사 + DB `status=ACTIVE` 조건 확인 | closed |
| T-03-37 | Repudiation | 물리 삭제로 인한 추적 단절 | mitigate | `pass` 패키지 전체에 `deleteById`/`.delete(` 호출 0건 확인(grep) | closed |
| T-03-38 | Tampering | 취소 중 잔여 경쟁 | mitigate | **수정 완료 (2026-08-04, 커밋 `aec19c7`·`e53c007`)** — 취소가 `PassRepository.cancelIfNotCanceled`(`status <> CANCELED` 조건부 UPDATE, PassRepository.kt:84)로 전환되어 상쇄 수량 0 분기 포함 전 경로가 원자적 상태 전환을 탄다. 경쟁 패배 측은 affected rows 0 → `PASS_ALREADY_CANCELED`(409). `Pass.cancel`은 판정·계산 전용 `resolveCancellationOffset`으로 축소되어 이중 쓰기 경로 제거. `PassCancellationConcurrencyTest`가 동시 취소 2요청에서 정확히 1건 성공·1건 409·상쇄 이력 중복 없음을 실 DB(Testcontainers)로 실증 | closed |
| T-03-39 | Information Disclosure | 취소 사유 회원 노출 | mitigate | 회원 조회가 `CANCELED` 상태를 쿼리에서 제외 확인 (`findAllByMemberIdAndStatusNotOrderByStartDateDescIdDesc`), `cancelReason`은 취소 안 된 이용권에서 항상 null | closed |
| T-03-40 | Elevation of Privilege | 관리자 목록 API 무단 접근 | mitigate | `/api/admin/**` prefix + 403/401 테스트 확인 | closed |
| T-03-41 | Information Disclosure | IDOR — 타인 이용권/이력 조회 | mitigate | `MemberPassController`/`Service` 전부 `principal.requireMemberId()`만 사용, 경로에 `memberId` 없음 확인 | closed |
| T-03-42 | Information Disclosure | `passId` 파라미터로 타인 이력 열람 | mitigate | `PassTransactionSpecifications.ownedByMember`가 non-nullable 반환, `Specification.allOf`로 항상 AND 결합 확인 | closed |
| T-03-43 | Information Disclosure | 관리자 메모·취소 사유 노출 | mitigate | `PassTransactionResponse`에 `note` 필드 없음(D-070) + 취소 이용권 쿼리 제외로 `cancelReason` 미도달 확인 | closed |
| T-03-44 | Elevation of Privilege | 비활성 회원의 데이터 접근 | **변경됨: mitigate → 실제 완화책은 스코프 강제** | **PLAN 원문의 `MemberStateGate.requireActive` 게이트는 실행 후 human-verify에서 D-071로 의도적으로 제거됨(03-REVIEW.md WR-01 대응)**. 실제 완화책은 `principal.requireMemberId()` + 응답이 본인 스코프로만 한정되는 것 — `MemberPassService`에 D-071 근거 KDoc 확인, `docs/decisions.md` D-071에 "2026-08-04 phase 마감 human-verify에서 사용자 재확인 완료" 기록 확인 | closed |
| T-03-45 | Denial of Service | 과대 페이지 크기 요청 | mitigate | `PassTransactionSearchCondition`에 `@Min(1) @Max(100)` 확인 | closed |
| T-03-46 | Tampering | FE 파라미터 바인딩 깨짐으로 인한 무필터 조회 | mitigate | `@ParameterObject @ModelAttribute @Valid` 확인, openapi.yaml에 `passId`/`page`/`size` 개별 파라미터로 반영 확인 | closed |
| T-03-47 | Tampering | openapi.yaml과 실제 API 불일치 | mitigate | `git status --short docs/api/openapi.yaml` clean, 03-11-SUMMARY의 `generateApiDocs` 재실행 후 `git diff --exit-code` 0 기록 확인 | closed |
| T-03-48 | Repudiation | 결정과 구현의 괴리 | mitigate | 03-11-SUMMARY Task 1에서 D-060~D-070을 구현과 대조해 전부 일치 확인한 기록 확인 | closed |
| T-03-49 | Information Disclosure | 미검토 응답 필드 노출 | mitigate | 03-11 Task 3 체크포인트에서 사람이 openapi 스키마 검토 후 승인(approved) 기록 확인 | closed |
| T-03-50 | Elevation of Privilege | 인가 규칙 회귀 | mitigate | `AdminPassControllerTest`·`MemberPassControllerTest`·`MemberPassTransactionControllerTest`에 401/403 테스트 각각 존재, `./gradlew build`에 포함되어 회귀 시 실패 확인 | closed |
| T-03-SC (×11, 03-01~03-11) | Tampering | npm/pip/cargo installs | accept | 각 플랜이 "신규 외부 패키지 설치 없음"을 선언. `build.gradle.kts` 최종 수정 커밋(`54acc8c`, Phase 2)이 Phase 3 기간(03-01~03-11) 동안 변경되지 않음을 `git log`로 확인 — Accepted Risks Log 등재 | closed |
| T-03-51 | Tampering | 기간 수정 동시 경합 (WF-03-01 역등재) | mitigate | **수정 완료 (2026-08-04, 커밋 `aec19c7`·`e53c007`)** — 기간 수정이 `PassRepository.changePeriodIfUnchanged`(전값 compare-and-swap: `startDate = :expectedStart AND endDate = :expectedEnd AND status <> CANCELED` 조건부 UPDATE, PassRepository.kt:108)로 전환. 경쟁 패배 측은 재조회 후 `PASS_ALREADY_CANCELED` 또는 `PASS_STATE_CONFLICT`(409). `PassPeriodChange` 이력의 전값이 항상 실제 DB 전값과 일치하도록 보장. `PassPeriodChangeConcurrencyTest`가 동시 수정 2요청에서 정확히 1건 성공·1건 409·이력 1건만 생성됨을 실 DB로 실증. 코드 리뷰 WR-04에서 발견되어 threat register에 역등재됨 | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

**Threats Open: 0/62 — 전건 closed (T-03-38 수정 완료, WF-03-01은 T-03-51로 역등재 후 수정 완료)**

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|--------------|------|
| AR-03-01 | T-03-15 | `today`를 호출부가 `Clock` 빈으로 주입하는 구조라 엔티티 자체는 시스템 시각을 직접 참조하지 않는다. 서버 OS 시각 자체의 무결성(NTP 조작 등)은 인프라 계층 책임이며 애플리케이션 코드로 방어할 수 없다 | 03-04-PLAN.md (계획 단계 disposition) | 2026-08-03 |
| AR-03-02 | T-03-25 | `/api/admin/**`는 관리자 전용 경로로 `hasRole("ADMIN")`이 이미 강제되므로, 관리자가 임의 회원의 이용권을 조회·관리할 수 있는 것은 설계된 정상 동작이다. 회원 셀프서비스 경로(03-10)는 별도로 본인 스코프를 강제한다 | 03-06-PLAN.md (계획 단계 disposition) | 2026-08-03 |
| AR-03-03 | T-03-SC (03-01~03-11, 11건) | Phase 3 전 기간 동안 `build.gradle.kts`에 신규 외부 의존성이 추가되지 않았다(마지막 수정 `54acc8c`는 Phase 2). RESEARCH.md의 Package Legitimacy Audit이 "해당 없음"으로 매 플랜에서 재확인됨 | 03-01~03-11-PLAN.md (계획 단계 disposition), 감사 시 `git log -- build.gradle.kts`로 실측 확인 | 2026-08-04 |

*Accepted risks do not resurface in future audit runs.*

---

## Open Findings (요약)

### T-03-38 — 취소 중 잔여 경쟁 (RESOLVED 2026-08-04 — 아래는 감사 시점 기록)

- **범위**: `AdminPassService.cancel()`에서 상쇄 수량이 0인 분기(기간제 `EVENING_MEMBERSHIP` 취소, 또는 잔여가 이미 0인 횟수권 취소)에는 `adjustRemainingCount`/`zeroRemainingCount` 같은 조건부 UPDATE도, `@Version` 낙관적 락도 없다. 잔여가 0이 아닌 분기(가장 흔한 케이스)는 정상적으로 방어된다.
- **영향**: 두 관리자가 같은 이용권을 거의 동시에 취소하면 두 번째 요청이 `PASS_ALREADY_CANCELED`(409)를 받아야 하는데, 실제로는 200을 받고 `cancelReason`/`canceledBy`가 먼저 커밋된 값을 조용히 덮어쓴다. **핵심 불변식("잔여 = 이력 합계")은 깨지지 않는다** — 이 분기에서는 잔여 자체가 변하지 않기 때문이다. 영향 범위는 취소 감사 메타데이터의 정확성과 에러 응답의 정확성으로 한정된다.
- **재현 조건**: 관리자 2명 이상, 같은 이용권에 대한 거의 동시 취소 요청 — 저빈도 관리자 전용 조작.
- **독립 근거**: `.planning/phases/03-pass/03-REVIEW.md` WR-03이 동일 지점을 코드 리뷰에서 이미 지적했고, `03-VERIFICATION.md`가 "INFO(비차단)"으로 분류했다. 그러나 이 분류는 STRIDE 위협 레지스터의 T-03-38 disposition을 "mitigate"에서 "accept"로 공식 변경하거나 Accepted Risks Log에 등재한 적이 없다 — 검증 문서의 severity 판단과 위협 레지스터의 disposition은 별개 트랙이다.
- **권고**: (a) `Pass`에 `@Version`을 추가하거나 `offset == 0` 분기도 `status = 'ACTIVE'` 조건부 UPDATE로 전환해 실제로 fix하거나, (b) 의도적으로 낮은 우선순위로 미룬다면 이 표의 Accepted Risks Log에 T-03-38을 정식 등재하고 disposition을 `accept`로 변경한다. 둘 중 하나 전까지는 OPEN으로 유지한다.
- **차단 여부 판단(참고, 최종 결정은 오케스트레이터/사용자)**: ASVS Level 1·`block_on: high` 기준으로 볼 때, 이 결함은 (1) 핵심 원장 불변식을 깨지 않고 (2) 회원 대면 기능이 아닌 관리자 저빈도 조작에 한정되며 (3) 최종 상태(취소됨)는 어쨌든 맞다는 점에서 **High가 아닌 Medium**으로 평가한다. 다만 위협 레지스터 상 공식적으로 아직 CLOSED도 ACCEPT도 아니므로 감사 결과는 OPEN으로 보고한다.

### 부가 발견 — 위협 레지스터에 매핑되지 않은 신규 공격 표면 (Unregistered Flag)

- **WF-03-01 (WR-04 유래)**: `AdminPassService.changePeriod()`도 `Pass`에 `@Version`이 없고 조건부 UPDATE도 쓰지 않아, 두 관리자가 같은 이용권의 기간을 동시에 수정하면 나중에 커밋한 쪽이 앞선 변경을 조용히 덮어쓰는 lost update가 가능하다(`PassPeriodChange` 이력에는 두 건이 남지만 최종 `pass.startDate`/`endDate`는 한쪽만 반영). **11개 PLAN.md의 threat_model 어디에도 이 위협을 다루는 threat ID가 없다** — T-03-31~35(03-08)는 입력 검증·이력 완전성·인가만 다루고 동시 수정 경합은 다루지 않는다. `03-REVIEW.md` WR-04가 코드 리뷰에서 발견해 INFO로 분류했으나 위협 레지스터에 역등재되지 않았다.
  - **권고**: Phase 4 진입 전 또는 후속 patch phase에서 T-03-38과 함께 `@Version` 도입 여부를 결정하고, 결정 결과를 새 threat ID로 이 레지스터에 추가한다.
  - **처리 결과 (2026-08-04)**: 사용자 결정("지금 수정")에 따라 `@Version` 대신 D-021 관례(조건부 UPDATE)로 T-03-38과 함께 즉시 수정 — `changePeriodIfUnchanged` compare-and-swap 도입, **T-03-51로 레지스터에 역등재 완료(closed)**. 결정 기록: docs/decisions.md D-072.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|----------------|--------|------|--------|
| 2026-08-04 | 61 | 60 | 1 | gsd-security-auditor |
| 2026-08-04 (재감사) | 62 | 62 | 0 | orchestrator — T-03-38 수정 검증(`aec19c7`·`e53c007`, 동시성 테스트 그린) + T-03-51 역등재·수정 검증 |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer) — 62/62 (transfer 0건, 이 phase는 3rd-party 위임 대상 없음)
- [x] Accepted risks documented in Accepted Risks Log — AR-03-01, AR-03-02, AR-03-03
- [x] `threats_open: 0` confirmed — T-03-38·T-03-51 수정 완료(커밋 `aec19c7`·`e53c007`), 동시성 테스트 2종 그린
- [x] `status: verified` set in frontmatter

**Approval:** approved 2026-08-04 — 사용자 결정("지금 수정") 반영, 조건부 UPDATE 전환 + 동시성 테스트로 실증 완료
