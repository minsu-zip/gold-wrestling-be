# Phase 5: 배치 - Context

**Gathered:** 2026-08-15
**Status:** Ready for planning

<domain>
## Phase Boundary

이용권을 오래 쓰지 않은 회원이 정책대로 자동 차감되고(`INACTIVITY`), 유효기간이 지난
이용권은 사용 불가로 동작하며, 배치가 며칠씩 밀리거나 같은 날 중복 실행돼도 이중 차감이
없다(멱등).

- **BATCH-01**: `SESSION_PASS` 2주 미사용 시 1회 자동 차감, 이후 2주마다 반복 (`INACTIVITY`)
- **BATCH-02**: 차감 예외 — `ON_LEAVE` 기간, 잔여 0, 유효기간 만료 이용권 제외
- **BATCH-03**: 유효기간(등록일+1년) 만료 이용권의 사용 불가 — **이번 논의로 "기존 메커니즘
  (D-064 조회 시점 계산 + Phase 4 수업날 기준 예약 거부)으로 이미 충족, 배치 구현물 없음"으로
  확정.** 이 phase에서는 충족을 실증하는 검증 테스트만 작성한다
- **BATCH-04**: 멱등 — 같은 날 중복 실행돼도 이중 차감 0건 (매일 새벽 실행 전제)

**이 phase가 만들지 않는 것:**
- 출석(`Attendance`) 스키마 — Phase 6 소관. 기준일 쿼리가 출석 테이블을 필요로 하지 않음을
  확인했으므로 선반영하지 않는다 (ROADMAP 노트의 "필요시 선반영"은 불필요로 판정).
  기준일의 "마지막 출석일" 후보는 Phase 6 전까지 자연히 부재로 동작한다
- `EXPIRED` 상태 영속화 배치 (D-107로 기각)
- 배치 이벤트에 대한 알림(`Notification`) 생성 — 요구사항에 없음 (deferred 참조)
- 외부 트리거(시스템 cron·EventBridge 등) — M7에서 재검토

</domain>

<decisions>
## Implementation Decisions

**논의 방식**: 사용자가 6개 의제(4 영역 + 추가 2건)에 대한 입장을 제시했고, 코드·정책 대조로
반대 근거가 없는 것은 그대로 확정, 충돌·빈칸 4건은 보완 질문으로 확정했다.
아래 결정은 `docs/decisions.md` **D-105~D-109**로 기록 완료, 1·A·B는 `docs/policies.md` §4.3에
반영 완료 — **downstream 에이전트는 policies §4.3 확정 문구를 원본으로 삼는다.**

### 1. 미사용 판정 기준일 (D-105, D-027 확장)
- **판정은 회원 단위**, 기준일 = 다음 5종 중 **가장 최근 날짜**:
  1. 마지막 출석일 (Phase 6 전까지 부재)
  2. 마지막 **취소되지 않은** 예약의 수업일 — 수업 종류(SESSION/LESSON) **무관**하게 인정
  3. `ON_LEAVE` → `ACTIVE` **복귀일** — 복귀하면 2주 유예가 새로 시작
  4. `SESSION_PASS` **등록일**(`pass.created_at`, **시작일 아님** — 과거 시작일 등록(D-055)이
     소급 차감으로 이어지지 않도록)
  5. 마지막 **양(+) 수동 가감일**(`ADMIN_ADJUST`) — 충전 시점부터 2주 유예가 새로 시작
- 원리: **"차감 제외 기간에는 부채가 쌓이지 않는다"** — 복귀일(휴회 제외 기간)과 +가감일
  (잔여 0 제외 기간)이 같은 원리로 시계를 리셋한다
- 복귀일은 휴회 기간 테이블 대신 **회원 상태 전환 시각 기록**(새 마이그레이션, 컬럼 설계는
  재량)으로 구현. 과거 복귀 이력은 데이터가 없으므로 후보에서 자연 제외 — 의도된 동작
- 수용한 트레이드오프: 휴회를 짧게 토글하면 시계가 리셋되는 관대함 — 휴회는 관리자만
  설정 가능하므로 악용 위험 낮음

### 2. 캐치업 = 멱등성 (D-106)
- 배치는 실행 횟수 기반이 아니라 **상태 기반**: "현재 존재해야 할 차감 수
  (`floor(기준일→오늘 경과일 / 14)`) − 기준일 이후 실제 `INACTIVITY` 이력 수 = 부족분"을
  계산해 부족분만 차감한다
- 같은 날 중복 실행·수동 실행·재기동 중복이 전부 자연 멱등이고, 배치가 며칠 중단돼도
  다음 실행이 밀린 주기를 몰아서 차감한다 (policies "4주 미사용 = 2회"와 정합)
- `INACTIVITY` 이력 수는 **건수**(이벤트 수)로 센다 — 부분 차감(0.5) 1건도 1회로 계산

### 3. 만료 처리 (D-107)
- **`EXPIRED` 영속화를 만들지 않는다** — 만료는 D-064 조회 시점 계산(`displayStatus`)이
  유일한 진실 원천으로 유지되고, Phase 4 예약 경로가 수업날 기준으로 이미 거부한다
- 영속화하면 진실 원천이 둘이 되고, 유효기간 수정으로 만료 해제(D-056) 시 상태 되돌리기가
  따라온다
- BATCH-03은 "만료 이용권 예약 불가 + `INACTIVITY` 대상 제외"를 **검증 테스트로 실증**해 충족

### 4. 실행·운영 (D-108)
- **앱 내 `@Scheduled`**(cron, `Asia/Seoul`, 매일 새벽) + **배치 실행 이력 테이블**
  (시각·처리 건수·결과) + **관리자 수동 실행 API 1개** (D-106 덕에 중복 실행 안전)
- **ShedLock 등 분산 락은 일부러 쓰지 않는다** — 단일 EC2 인스턴스 전제. 다중 인스턴스·외부
  트리거는 M7에서 재검토
- 실행 이력은 관측·복구 판단용이며 멱등성의 근거가 아니다 (멱등성은 D-106 상태 기반 계산이 담당)

### A. 판정 단위와 차감 대상 (D-109)
- **판정(기준일·부족분)은 회원 단위**, **차감 1회는 한 장에서**: 차감 가능한 `SESSION_PASS`
  (취소·만료 아님, 잔여 > 0) 중 **유효기간 만료가 가장 임박한 한 장** (D-091 선택 규칙 준용 —
  `end_date` 오름차순, 동률 `id` 오름차순)
- 부족분이 여러 회면 회당 대상을 재선택한다 (앞 장이 0이 되면 다음 장으로)

### B. 부분 차감 (D-109)
- **잔여가 차감량(1)보다 적으면 잔여만큼만 차감한다** (0.5 → 0) — "잔여 0 제외" 예외와
  연속되는 규칙
- **다른 장으로 부족분을 이월하지 않는다** — 합산 금지(D-091)와 일관. 한 차감 이벤트는
  한 장에서만 일어난다

### C. 계획 단계 보완 확정 (2026-08-15, 리서치 Open Questions에 대한 사용자 답변)
- **기준일 후보 ④의 다장 보유 범위**: **차감 가능한 장(취소·만료 아님, 잔여 > 0) 중 최신
  `created_at`** — 새 장 등록(충전)이 시계를 리셋하는 +가감일 원리와 일관 (D-105 보강,
  첫 플랜의 문서 정합 시 docs/decisions.md D-105에 반영)
- **경쟁 패배 스킵의 실행 이력 집계**: 스킵은 설계상 정상 동작(다음 실행이 상태 기반 보정)
  이므로 **결과는 SUCCESS로 두고 스킵 건수를 별도 컬럼으로 기록**한다 (D-108 보강)

### Claude's Discretion
- **`PassTransaction` 시스템 주체 표현** — V8 CHECK(`ck_pass_transaction_subject`)가
  admin/member 중 정확히 하나를 요구하는데 배치는 둘 다 아니다. 새 마이그레이션으로 CHECK
  완화·시스템 주체 표현을 설계한다 (**커밋된 V8 수정 금지 — 새 버전 추가**)
- 회원 상태 전환 시각의 컬럼 설계 (예: `member.status_changed_at` 단일 컬럼 vs 복귀 전용)
- 배치 트랜잭션 경계 (회원 단위 개별 트랜잭션 vs 전체 일괄 — 부분 실패 시 나머지 진행 여부)
- 실행 이력 테이블 스키마·네이밍 (**glossary 등록 후 사용** — 금지어 확인)
- `@Scheduled` cron 시각(새벽 몇 시), 수동 실행 API 경로·응답 DTO, 에러코드
- 차감 시 `adjustRemainingCount` 조건부 UPDATE가 0행이면(경쟁 패배) 해당 회원 스킵 —
  다음 실행이 상태 기반으로 자연 보정 (Phase 4의 "재시도 없이 실패" 재량과 같은 원리)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 도메인·스펙 (CLAUDE.md 문서 우선순위: policies > requirements > glossary·decisions·conventions)
- `docs/policies.md` **§4.3(2주 미사용 자동 차감 — 이번 논의로 확정 문구 반영 완료, 최종 기준)**,
  §4.1(즉시 차감·이력 원칙 — 배치 차감도 전 이력), §4.2a(수동 가감 — +가감일이 기준일 후보),
  §1(이용권 3종·유효기간·D-066 종료일 포함), §5(회원 상태 — `ON_LEAVE`)
- `docs/decisions.md` — **D-105~D-109(이번 논의 신설, 기록 완료)**, D-027(기준일 원본 —
  D-105로 확장), D-016(BigDecimal·compareTo), D-020(트랜잭션 경계), D-021(조건부 갱신 우선),
  D-055(과거 시작일 등록), D-056(만료 후 가감·기간 수정), D-064(표시 상태 계산),
  D-066(종료일 포함 판정), D-091(차감 대상 선택·합산 금지)
- `.planning/REQUIREMENTS.md` — BATCH-01~04 정의
- `.planning/ROADMAP.md` — Phase 5 성공 기준 4항목, Attendance 선반영 노트(이번 논의로 불채택)
- `docs/glossary.md` — `TransactionReason.INACTIVITY`, 금지어 목록 (새 개념 등록 시 필수 확인)
- `docs/conventions.md` — §3(엔티티), §5(시간 타입), §9(Flyway), **§10.0(변경유형별 테스트 표 —
  도메인 로직 단위테스트 + DB Testcontainers 필수)**, §11(Boot 4)
- `docs/error-codes.md` — 수동 실행 API 에러코드 추가 시 같은 PR에서 갱신

### 프로젝트 스킬 (해당 작업 시 필수 절차)
- `.claude/skills/add-migration/SKILL.md` — V9+ (상태 전환 시각, 실행 이력 테이블,
  `pass_transaction` 시스템 주체)
- `.claude/skills/add-endpoint/SKILL.md` — 관리자 수동 실행 API + `openapi.yaml` 재생성
- `.claude/skills/add-domain-test/SKILL.md` — 기준일 계산·부족분 계산 단위테스트,
  멱등성 Testcontainers 테스트
- `.claude/skills/verify-boot4-api/SKILL.md` — `@Scheduled`/`@EnableScheduling` Boot 4 확인
  (스케줄링 인프라 이 저장소 최초 도입)
- `.claude/skills/deliver-phase-chunk/SKILL.md` — D-084 청크 납품 절차
- `.claude/skills/create-pr/SKILL.md` — PR 본문 학습 노트

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`pass/PassRepository.adjustRemainingCount`** — 잔여 원자 가감 조건부 UPDATE
  (`ACTIVE` + 결과 음수 거부). 배치 차감이 그대로 재사용. 부분 차감은 호출 전
  `min(1, 잔여)`로 수량을 계산해 전달 — 경쟁으로 0행이면 스킵(재량 참조)
- **`pass/Pass.kt`** — `displayStatus(today)`·`isExpired`(D-064·D-066)를 만료 제외 판정에
  재사용 (기준: 배치 실행일). `start_date`와 `created_at`(등록일)이 별도 존재 — 기준일은
  **`created_at`**
- **`pass/PassTransaction` + `TransactionReason.INACTIVITY`** — 사유는 Phase 3에서 선언 완료.
  단, **V8 CHECK가 admin/member 주체 중 정확히 하나를 요구 → 시스템 주체 표현이 스키마
  선결 과제** (Claude 재량 항목)
- **`reservation/Reservation` + `ReservationStatus`(ACTIVE/CANCELED)** — "마지막 취소되지 않은
  예약의 수업일" 조회는 `status = ACTIVE` 필터 + `class_session.class_date` 조인.
  ROADMAP이 예고한 대로 Phase 4 예약 데이터를 기준일 조회에 쓴다
- **`member/MemberStatus`(ON_LEAVE)** — 상태는 있으나 **전환 시각 기록이 없음** → V9+
  마이그레이션 필요. 전환 지점은 `AdminMemberService`의 상태 변경 경로
- **`config/ClockConfig.kt`** — 기준일·경과일 판정 전부 `Clock` 주입 (테스트 시각 고정)
- **Testcontainers 배선 + `FlywayMigrationIntegrationTest`** — 새 마이그레이션 자동 검증
- **`generateApiDocs`** — 수동 실행 API 추가 후 openapi.yaml 재생성

### Established Patterns
- **판정·계산은 도메인 메서드, 반영은 조건부 UPDATE** (D-072 관례) — 기준일·부족분 계산을
  순수 함수로 분리하면 §10.0 단위테스트 대상이 명확해진다 (TDD 플랜 후보)
- **원장 불변식 "잔여 = 이력 합계"** — `PassLedgerInvariantTest` 선례. 배치 차감 후에도
  성립해야 하며 멱등성 테스트와 함께 실증
- 서비스 `@Transactional(readOnly = true)` 기본, 변경 메서드만 오버라이드 (D-020)
- 마이그레이션 관례: 서로게이트 PK `id`, `TIMESTAMPTZ`, 헤더 주석에 결정 번호

### Integration Points
- **스케줄링 인프라 최초 도입** — `@EnableScheduling` 설정 위치, Boot 4 확인 필수
- **새 패키지** 후보: `batch/` (기능별 패키지 D-018) — 판정 로직이 `pass`·`reservation`·
  `member`를 읽는 의존 방향 주의
- **수동 실행 API**는 `/api/admin/**` 아래 — SecurityConfig 기존 ADMIN 규칙이 그대로 적용,
  수정 불필요
- Phase 6이 출석(`Attendance`) 도입 시 기준일 후보 1(마지막 출석일)이 활성화된다 —
  기준일 계산이 후보 목록에 항목을 추가하기 쉬운 형태면 충분

</code_context>

<specifics>
## Specific Ideas

- 사용자가 6개 의제 전부에 대해 구체적 입장을 선제 제시했고 "반대 근거 없으면 확정" 방식을
  요청했다 — 상태 기반 부족분 설계(2번)를 멱등성의 구현 방식 그 자체로 규정한 것,
  수동 실행 API의 안전성을 그 설계에서 도출한 것(4번)은 사용자의 설계 관점
- "제외 기간에는 부채가 쌓이지 않는다"는 원리로 복귀일·+가감일 리셋을 한 규칙으로 묶었다
  (보완 질문에서 사용자 확정)
- BATCH-03을 "같은 사실의 원천을 두 개 만들지 않는다"는 이유로 구현 없이 검증으로 충족
  처리한 것은 사용자의 명시적 결정 — REQUIREMENTS·ROADMAP 문구 정정까지 지시

</specifics>

<deferred>
## Deferred Ideas

- **외부 트리거**(시스템 cron·EventBridge 등 앱 밖 스케줄러) — M7에서 재검토 (사용자 지정)
- **배치 이벤트 알림** (`INACTIVITY` 차감·만료를 관리자 알림으로) — 요구사항(NOTIF-01~03)에
  없음. 배치 실행 이력 테이블이 관측 수요를 우선 흡수. 필요가 확인되면 Phase 6 알림 체계에 추가
- **다중 인스턴스 대비 분산 락**(ShedLock) — 단일 EC2 전제가 깨질 때 재검토
- **휴회 토글로 인한 유예 리셋 관대함 보정** — 악용이 실제 관찰되면 휴회 기간 기록 방식 재논의

</deferred>

<open_items>
## 계획 단계에서 처리할 문서 정합 (Phase 3·4의 첫 플랜 관례)

이번 논의로 확정됐고 **이미 반영한 것**: `docs/decisions.md` D-105~D-109, `docs/policies.md` §4.3.

**첫 플랜에서 남은 정합을 처리한다:**
- `docs/glossary.md` — 배치 실행 이력 개념(엔티티명) 등록 후 사용, 금지어 확인
- `docs/error-codes.md` — 수동 실행 API 관련 신규 에러코드 등록 (필요 시)
- `.planning/REQUIREMENTS.md`·`.planning/ROADMAP.md` — BATCH-03을 "기존 메커니즘(D-064)으로
  충족, 검증 테스트로 실증"으로 문구 정정 (사용자 지시)
- `docs/requirements.md` — 배치 관련 문구가 D-105~D-109와 어긋나는 곳이 있으면 정정

</open_items>

---

*Phase: 5-배치*
*Context gathered: 2026-08-15*
