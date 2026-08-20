# Phase 3: 이용권 - Context

**Gathered:** 2026-08-03
**Status:** Ready for planning

<domain>
## Phase Boundary

관리자가 회원에게 이용권 3종(`EVENING_MEMBERSHIP` / `SESSION_PASS` / `LESSON_PASS`)을 등록·조정하고,
모든 변경이 감사 가능한 이력(`PassTransaction` + `PassPeriodChange`)으로 남으며,
회원이 본인 이용권 현황·이력을 조회한다.

- **PASS-01~06**: 등록, 이력, 수동 가감, 저녁반 기간 수정, 본인 이용권/이력 조회
- **PASS-07** (이번 논의로 추가): 횟수권 유효기간 수정
- **PASS-08** (이번 논의로 추가): 이용권 등록 취소 (오등록 정정)

예약 시 차감(`RESERVE`)·복구는 Phase 4, 배치 차감(`INACTIVITY`)·만료 처리는 Phase 5,
저녁반 0.5회 수동 차감(`EVENING_HALF`)은 Phase 6 스코프. 이 phase는 그들이 전제할
Pass·PassTransaction 데이터 모델과 원장 불변식("잔여 = 이력 합계")을 만든다.

</domain>

<decisions>
## Implementation Decisions

### 등록 규칙 (D-055)
- 등록 시 **시작일 지정 가능** (기본값 오늘, **과거 날짜 허용**) — 오프라인 수기 등록 대응
- 횟수권 유효기간 1년·저녁반 회비 만료일 모두 **시작일 기준** 계산 (policies §1 정정 완료)
- 횟수권 초기 횟수는 **0.5 단위 자유 입력** (정해진 상품 단위 없음)
- 초기 부여도 `PassTransaction` 이력으로 남긴다 — 사유 코드 **`INITIAL_GRANT`** 신설 (glossary 반영 완료)

### 수동 가감 정책 (D-056, policies §4.2a)
- `ADMIN_ADJUST` 가감 단위 **0.5**, 사유 입력 필수
- **결과 잔여가 음수가 되는 가감은 거부**
- **만료된 횟수권에도 가감 가능** — 횟수권 유효기간 수정(PASS-07)과 세트 (만료 후 서비스 부여 대응)
- 기간제(`EVENING_MEMBERSHIP`)는 횟수 가감 대상 제외 — 기간 수정으로만 조정

### 기간·유효기간 변경 이력 (D-057)
- 저녁반 기간 수정(PASS-04)은 **날짜 직접 지정** 방식 (개월 단위 연장 아님)
- 기간·유효기간 변경 이력은 **전용 테이블 `pass_period_change`**:
  이용권 / 변경 전·후 시작·종료일 / 사유 / 주체(admin_id) / 시각
- `PassTransaction`은 ±수량 원장 역할에 고정 — 기간 변경과 역할 분리
- Envers 등 범용 감사는 기각 (Boot 4 호환 검증 부담 + 도메인 필드 커스터마이징 복잡)

### 본인 조회 범위 (D-058)
- 이용권 조회(PASS-05): **만료·소진 포함** 노출 + 상태 구분 표시. 취소된 이용권만 숨김
- 이력 조회(PASS-06): **이용권별 필터 + page/size 페이지네이션** — 회원 목록(D-035)과
  동일 형태, `PageResponse` DTO 재사용, `@ParameterObject` 규칙(D-054) 준수

### 오등록 정정 (D-059)
- 이용권 **등록 취소** 기능 제공 — **물리 삭제 금지**, 취소 상태 전환
- 횟수권 취소 시 **잔여를 0으로 상쇄하는 `PassTransaction`(−잔여, `REGISTRATION_CANCELED`)** 을
  함께 남겨 "잔여 = 이력 합계" 불변식을 취소된 이용권에도 유지. 기간제는 상태 전환 + 이력만
- 취소된 이용권: 회원 화면 숨김, 관리자 화면 구분 표시

### Claude's Discretion
- Pass 3종의 테이블/엔티티 모델링 (단일 테이블 + 타입 구분 vs 상속 등) — 단, glossary 네이밍
  (`Pass` 상위 개념, `PassTransaction`, `PassPeriodChange`)과 D-016(DECIMAL(4,1) + BigDecimal)은 고정
- 이용권 상태(사용가능/만료/소진/취소)의 표현 방식 — 취소는 저장 필수, 만료·소진은 계산 가능.
  상태 enum 이름을 새로 지으면 glossary에 추가 후 사용
- 잔여 갱신의 동시성 처리 수준 — 이번 phase는 관리자 단독 조작이라 경쟁이 희박하나,
  Phase 4(예약 차감)가 재사용할 갱신 경로임을 감안해 설계 (D-021: DB 제약 + 조건부 갱신 우선)
- 신규 ErrorCode 구성 (잔여 부족, 음수 거부, 기간제 가감 시도, 취소된 이용권 조작 등) —
  docs/error-codes.md 동시 갱신
- 유효기간 수정(PASS-07)의 화면/API 형태 — 별도 엔드포인트 vs 기간 수정과 통합

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 도메인·스펙 (CLAUDE.md 문서 우선순위 준수)
- `docs/policies.md` §1(이용권 3종 — 이번 논의로 정정·보강), §4.1(즉시 차감·이력 원칙),
  §4.2a(수동 가감 — 이번 논의로 신설) — 최종 기준
- `docs/requirements.md` §3.2(본인 조회), §4.2(이용권 관리 — 이번 논의로 보강)
- `docs/decisions.md` — **D-055~D-059(이번 논의 결정)**, D-016(BigDecimal·compareTo),
  D-017(ProblemDetail), D-018(패키지), D-019(DTO만 노출), D-020(트랜잭션), D-021(동시성),
  D-028(에러코드 레지스트리), D-035(page/size), D-054(@ParameterObject)
- `docs/glossary.md` — Pass/PassTransaction/PassPeriodChange, TransactionReason 8종
  (INITIAL_GRANT·REGISTRATION_CANCELED 신설), 금지어(Ticket/Voucher/Coupon 등)
- `docs/conventions.md` — §1(패키지), §3(엔티티), §5(시간 타입), §8(에러), §9(Flyway), §10(테스트 표), §11(Boot 4)
- `docs/error-codes.md` — 신규 에러코드 추가 시 같은 PR에서 갱신

### 프로젝트 실행 상태
- `.planning/REQUIREMENTS.md` — PASS-01~08 정의 (07·08 이번 논의로 추가, coverage 44)
- `.planning/ROADMAP.md` — Phase 3 성공 기준 6항목 (PASS-01~08, D-055~D-059 반영 완료)

### 프로젝트 스킬 (해당 작업 시 필수 절차)
- `.claude/skills/add-migration/SKILL.md` — V4+ pass/pass_transaction/pass_period_change 스키마 절차
- `.claude/skills/add-endpoint/SKILL.md` — API 추가·openapi.yaml 재생성 절차
- `.claude/skills/add-domain-test/SKILL.md` — 차감 정책 단위테스트·DB 통합테스트 골격
- `.claude/skills/verify-boot4-api/SKILL.md` — 낯선 API·의존성 Boot 4 검증 절차

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `member/dto/PageResponse.kt` — 이력 조회 페이지네이션에 재사용 (D-035·D-058)
- `common/error/` (ErrorCode·DomainException·GlobalExceptionHandler) — 이용권 에러도 이 체계로
- `member/Member.kt`·`MemberRepository` — Pass의 소유자 FK 대상. `admin/Admin.kt` — 가감·기간수정 주체
- `AuthenticationPrincipalResolver`·`MemberStateGate` — 본인 조회 API의 인증·상태 게이트 재사용
- Testcontainers 배선 + `FlywayMigrationIntegrationTest` — V4+ 마이그레이션 자동 검증
- `generateApiDocs` 태스크(D-029) — API 추가 후 openapi.yaml 재생성

### Established Patterns
- V2·V3 마이그레이션 관례: 서로게이트 PK `id`, `TIMESTAMPTZ`, CHECK 제약으로 불변식 표현
  (`ck_refresh_token_principal` 참조 — pass_transaction의 주체 표현에 같은 패턴 후보)
- 검색·목록 API: `@ParameterObject` 조건 DTO + Specification (D-053·D-054)
- ktlint → build 순서, 서비스 `@Transactional(readOnly = true)` 기본 (D-020)

### Integration Points
- 새 패키지: `pass/` (기능별 패키지 D-018). 컨트롤러는 DTO만 (D-019)
- SecurityConfig 인가 규칙에 `/api/admin/**`·회원 본인 조회 경로 추가 (Phase 2의 default-deny 위에)
- Phase 4가 이 phase의 차감/복구 경로(잔여 갱신 + PassTransaction 기록)를 그대로 호출한다 —
  서비스 계층 분리 시 이 재사용을 염두

</code_context>

<specifics>
## Specific Ideas

- 사용자 입장 5개(등록 규칙/수동 가감/기간 수정/본인 조회/오등록 정정)가 반대 근거 없이
  그대로 확정 — D-055~D-059로 `docs/decisions.md`에 기록 완료
- 기간 변경 이력의 구체 방식(전용 테이블)과 취소 시 잔여 0 상쇄는 Claude 제안을 사용자가 채택
- policies §1 "등록일로부터 1년" → "시작일로부터 1년" 정정, §4.2a 신설, glossary 사유 코드
  2종·PassPeriodChange 추가, requirements(§4.2)·REQUIREMENTS.md(PASS-07·08) 반영 — 모두 이번 세션에서 완료

</specifics>

<deferred>
## Deferred Ideas

- 원장 정합 자동 검증(잔여 = 이력 합계 배치 검증) — Phase 5(배치)에서 멱등 설계와 함께 고려
- 취소된 이용권의 복원(취소 철회) 기능 — 필요성 확인 후 별도 논의 (현재는 재등록으로 갈음)

</deferred>

---

*Phase: 3-이용권*
*Context gathered: 2026-08-03*
