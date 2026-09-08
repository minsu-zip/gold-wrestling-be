# Phase 2: 인증·회원 - Context

**Gathered:** 2026-08-02
**Status:** Ready for planning

<domain>
## Phase Boundary

회원이 카카오 OAuth로 가입해(`PENDING`) 온보딩(실명·전화번호 필수 입력)·관리자 승인을 거쳐
`ACTIVE`가 되고, 관리자는 ID/PW로 로그인해(회원과 동일한 JWT 체계) 회원을 관리한다.

- **AUTH-01~06**: 카카오 OAuth 로그인, JWT access/refresh 발급·갱신, 관리자 ID/PW 인증,
  역할·상태 기반 인가, 온보딩(형식 검증 포함), 온보딩 미완료 재로그인 식별
- **MEMBER-01~04**: 가입 승인/거절, 회원 목록·상세·검색, 상태 변경, 본인 프로필 조회
- `config/SecurityConfig.kt`의 전체 permitAll 뼈대를 실제 인가 규칙으로 교체한다 (ROADMAP Note)

이용권(Phase 3)·예약(Phase 4)은 스코프 밖. 프로필 셀프 수정은 v2(PROF-01).

</domain>

<decisions>
## Implementation Decisions

### 카카오 OAuth 연동 방식 (D-032)
- **인가 코드 방식.** FE는 카카오 리다이렉트와 인가 코드 전달만 담당한다.
- BE가 인가 코드로 카카오 토큰 교환 → 사용자 정보 조회 → 자체 JWT 발급까지 수행한다.
- `client_secret`은 서버 환경변수에만 존재한다 (`.env.example`에 키 이름 동기화 — 시크릿 규칙).

### JWT 토큰 정책 (D-033)
- **access 30분, refresh 14일.**
- refresh 토큰은 **DB 저장 + 사용 시마다 회전(rotation)**. 로그아웃 = refresh 삭제.
- 회원 상태가 `ACTIVE`가 아니게 되면 refresh를 무효화해 **강제 로그아웃이 가능**해야 한다.
- 상태 게이트 인가(AUTH-04)는 토큰 클레임이 아니라 **DB 현재 상태 기준**으로 검사한다 —
  refresh만 무효화하면 기발급 access가 최대 30분 살아 있는 창이 생기기 때문 (D-033 노트).

### 가입 거절·상태 전이 (D-034, policies §5.2)
- 별도 `REJECTED` 상태 없이 **`INACTIVE` 전환 + 거절 사유 기록**.
- 거절된 회원 재로그인 시 **거절 안내 화면 대상으로 식별**된다.
- 재신청 = 관리자가 상태를 `PENDING`으로 되돌리는 운영 방식 (회원 셀프 재신청 없음).
- 승인 취소 = 별도 기능 없이 기존 상태 변경(MEMBER-03)으로 갈음.

### 회원 목록·검색 API (D-035)
- **page/size 페이지네이션**, 검색어 하나로 **이름·전화번호 부분 일치**, **상태 필터** 제공.
- **승인 대기 목록도 동일 API 재사용** — `status=PENDING` + 온보딩 완료 필터 조합.
  (온보딩 완료 필터가 있어야 policies §5.1 "프로필 입력된 PENDING만 노출"이 지켜진다)

### Claude's Discretion
- 카카오 연동 세부: redirect_uri 관리(환경변수), state 파라미터 처리, 카카오 API 호출 구현
  (Boot 4 기준 — verify-boot4-api 스킬 절차 준수)
- 스키마 설계(V3+): member의 kakao_id·유니크 제약·거절 사유 컬럼, admin 로그인 자격(BCrypt),
  refresh_token 테이블 구성
- 회전 시 토큰 재사용 감지(reuse detection) 도입 여부, 멀티 디바이스 동시 로그인 허용 여부
  (권장: refresh 토큰을 회원당 여러 개 허용 — DB 저장 구조가 자연스럽게 지원)
- 관리자 시드 계정의 비밀번호 주입 방식 — 단 실값 커밋 금지, 환경변수 주입 원칙은 고정
- `created_at` 감사 시각 전략 — Phase 1에서 이월된 결정 (Clock 빈 기반, 이번 phase의 첫
  INSERT 경로에서 확정)
- 인가 규칙 구현 방식 (URL 기반 vs 메서드 시큐리티 조합)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 도메인·스펙 (CLAUDE.md 문서 우선순위 준수)
- `docs/policies.md` §5, §5.1, §5.2 — 회원 상태 4종, 가입·온보딩 판정, 승인·거절·상태 전이(이번 논의로 추가)
- `docs/requirements.md` — AUTH/MEMBER 요구사항 원문
- `docs/decisions.md` — D-025(온보딩 직접 입력), D-026(관리자 ID/PW+동일 JWT),
  D-032~D-035(이번 논의 결정), D-028(에러코드 레지스트리), D-020(트랜잭션), D-024(ktlint)
- `docs/glossary.md` — Member/Admin/MemberStatus 등 네이밍, 금지어
- `docs/conventions.md` — §1(패키지), §3(엔티티), §5(시간 타입), §8(에러), §9(Flyway), §10(테스트 표), §11(Boot 4)
- `docs/error-codes.md` — 에러코드 계약 문서. 새 인증 에러코드 추가 시 같은 PR에서 갱신

### 프로젝트 실행 상태
- `.planning/REQUIREMENTS.md` — AUTH-01~06, MEMBER-01~04 정의
- `.planning/ROADMAP.md` — Phase 2 성공 기준 5항목

### 프로젝트 스킬 (해당 작업 시 필수 절차)
- `.claude/skills/add-endpoint/SKILL.md` — API 추가·openapi.yaml 재생성 절차
- `.claude/skills/add-migration/SKILL.md` — V3+ 스키마 작업 절차
- `.claude/skills/add-domain-test/SKILL.md` — 테스트 골격
- `.claude/skills/verify-boot4-api/SKILL.md` — Spring Security OAuth·JWT 의존성 등 Boot 4 검증 절차

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `config/SecurityConfig.kt` — STATELESS·CORS·permitAll 뼈대. 이번 phase에서 JWT 필터 + 인가 규칙으로 교체
- `common/error/` (ErrorCode·DomainException·GlobalExceptionHandler) — 인증·인가 에러도 이 체계로 (401/403 응답도 ProblemDetail + code)
- `V2__create_branch_member_admin.sql` — member(name/phone nullable, status), admin(자격 없음).
  V3+에서 kakao_id·admin 자격·refresh_token 테이블 추가
- `generateApiDocs` 태스크(D-029) — API 추가 후 openapi.yaml 재생성
- Testcontainers 배선 + `FlywayMigrationIntegrationTest` — 새 마이그레이션 자동 검증

### Established Patterns
- Boot 4 의존성 세팅(webmvc, 모듈별 test 스타터, security 포함) — spring-boot-starter-security·security-test 이미 존재
- ktlint 1.8.0 · `.editorconfig` 단일 출처 — 작업 마지막 `ktlintFormat` → `build`

### Integration Points
- 새 패키지: `auth/`(로그인·토큰), `member/`(기존 — 관리·조회 확장) — 기능별 패키지 (D-018)
- Security 필터체인은 기존 `SecurityConfig` 교체 — 새 config 클래스 난립 금지
- 컨트롤러는 DTO만 (D-019), 서비스 `@Transactional(readOnly = true)` 기본 (D-020)

</code_context>

<specifics>
## Specific Ideas

- 사용자 입장 4개가 그대로 확정됨 (반대 근거 없음) — D-032~D-035로 `docs/decisions.md`에 기록
- 거절 전이 규칙은 `docs/policies.md` §5.2로 스펙화 (사용자 지시)
- 상태 게이트 인가의 DB 기준 검사는 Claude가 제안해 D-033에 노트로 포함 — "강제 로그아웃
  가능해야 한다"는 요구를 access 토큰 잔여 수명 창까지 막으려면 필요

</specifics>

<deferred>
## Deferred Ideas

- 카카오 자동 수집·온보딩 폼 자동 채움 — v2 (KAKAO-01, D-025 기존 결정)
- 회원 프로필 셀프 수정 — v2 (PROF-01, 기존 결정)

</deferred>

---

*Phase: 2-인증·회원*
*Context gathered: 2026-08-02*
