---
phase: 02
slug: auth-member
status: verified
threats_open: 0
asvs_level: 1
created: 2026-08-03
---

# Phase 02 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.
> 2026-08-03 감사: 플랜 15개의 `<threat_model>` 레지스터(고유 위협 59건)를 구현 코드와 대조 검증.
> 문서·의도 진술이 아니라 코드 grep 매치·테스트 파일 존재를 증거로 채택했다.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| FE ↔ BE | React FE가 REST API 호출 (openapi.yaml 계약) | 인가 코드, JWT, 회원 개인정보(실명·전화번호) |
| BE ↔ 카카오 | 토큰 교환·사용자 조회 (server-to-server) | client_secret, 인가 코드, kakaoId |
| BE ↔ PostgreSQL | JPA/Flyway | 비밀번호 해시, refresh 토큰 해시, 회원 상태 |
| 운영자 ↔ BE | 관리자 ID/PW 로그인 → ROLE_ADMIN API | 관리자 자격증명, 회원 관리 조작 |

---

## Threat Register

전 59건 **closed**. 근거 요약 (상세 증거는 2026-08-03 감사 보고 — 파일:라인 단위):

| Threat ID | Category | Component | Disposition | Mitigation (핵심 증거) | Status |
|-----------|----------|-----------|-------------|------------------------|--------|
| T-02-01 | Info Disclosure | 로그인 에러 응답 | mitigate | `INVALID_CREDENTIALS` 단일 코드 (ErrorCode.kt) | closed |
| T-02-02 | Tampering | 에러코드 계약 | mitigate | `ErrorCodeRegistryTest` enum↔문서 양방향 동기화 | closed |
| T-02-03 | Spoofing | 회원 가입 | mitigate | `uq_member_kakao_id` UNIQUE (V3) | closed |
| T-02-04 | Info Disclosure | refresh 저장 | mitigate | SHA-256 해시만 저장, 원문 컬럼 없음 | closed |
| T-02-05 | Elevation of Priv. | refresh 주체 | mitigate | `ck_refresh_token_principal` CHECK (V3) | closed |
| T-02-06 | Spoofing | JWT 서명 | mitigate | HS256 고정 + `alg:none` 거부 테스트 (JwtConfigTest) | closed |
| T-02-07 | Info Disclosure | JWT 시크릿 | mitigate | env 주입, 에러 메시지에 값 미포함 | closed |
| T-02-08 | DoS | 카카오 호출 | mitigate | connect 3s / read 5s 타임아웃 | closed |
| T-02-09 | Spoofing | 약한 시크릿 | mitigate | 32바이트 미만 기동 차단 (JwtConfig) | closed |
| T-02-SC | Tampering | 의존성 | mitigate | oauth2-jose 1건, BOM 관리 버전 | closed |
| T-02-10 | Spoofing | refresh 재사용 | mitigate | 재사용 감지 → 주체 전체 폐기 (TokenService.rotate) | closed |
| T-02-11 | Info Disclosure | refresh 실패 사유 | mitigate | 단일 메시지, 사유는 서버 로그만 | closed |
| T-02-12 | Spoofing | refresh 엔트로피 | mitigate | SecureRandom 32바이트 | closed |
| T-02-13 | Elevation of Priv. | access claim | mitigate | status claim 미포함 (매 요청 DB 판정) | closed |
| T-02-14 | Elevation of Priv. | 상태 스냅샷 | mitigate | AuthenticationPrincipalResolver 매 요청 DB 재조회 | closed |
| T-02-15 | Spoofing | 무효 토큰 | mitigate | null principal → 미인증 통과 → 뒤에서 401 | closed |
| T-02-16 | Info Disclosure | 401/403 응답 | mitigate | 고정 문구 ProblemDetail, 토큰 값 로그 없음 | closed |
| T-02-17 | Elevation of Priv. | 인가 기본값 | mitigate | `anyRequest().authenticated()` (default-deny) | closed |
| T-02-18 | DoS | 문서 경로 | mitigate | swagger/api-docs permitAll 명시 | closed |
| T-02-19 | Info Disclosure | 카카오 로그 | mitigate | 상태코드만 로그, 시크릿·코드·토큰 미포함 | closed |
| T-02-20 | Tampering | redirect_uri | mitigate | 서버 설정값만 사용, 요청 파라미터 미수용 (D-046) | closed |
| T-02-21 | Info Disclosure | 거절 사유 | mitigate | 회원 응답은 rejected Boolean만 (D-043) | closed |
| T-02-22 | Spoofing | 동시 가입 | mitigate | UNIQUE + 트랜잭션 밖 1회 재시도 (02-12에서 교정) | closed |
| T-02-23 | DoS | 카카오 장애 | transfer | 타임아웃 + KAKAO_UNAVAILABLE 즉시 실패 | closed |
| T-02-24 | Info Disclosure | 계정 열거(응답) | mitigate | loginId 미존재/불일치 동일 예외·메시지 | closed |
| T-02-25 | Info Disclosure | 비밀번호 저장 | mitigate | DelegatingPasswordEncoder(bcrypt) | closed |
| T-02-26 | Info Disclosure | 자격증명 로그 | mitigate | 로그에 loginId만, 비밀번호 미기록 | closed |
| T-02-27 | Tampering | 시드 자격증명 | mitigate | env 주입 + ApplicationRunner 멱등 시드 | closed |
| T-02-28 | Spoofing | 로그인 브루트포스 | accept | v1 스코프 밖 (관리자 소수 고정) — Accepted Risks 참조 | closed |
| T-02-29 | Elevation of Priv. | IDOR | mitigate | /me 경로에 PathVariable 없음, principal만 사용 | closed |
| T-02-30 | Tampering | 온보딩 상태 | mitigate | MemberStateGate + 서비스 이중 검사 | closed |
| T-02-31 | Elevation of Priv. | 상태 게이트 | mitigate | D-040 상태 게이트 규칙 명시 | closed |
| T-02-32 | Info Disclosure | 본인 프로필 | mitigate | rejected Boolean만 노출 | closed |
| T-02-33 | Elevation of Priv. | 관리자 경로 | mitigate | `/api/admin/**` hasRole(ADMIN) + 401/403 테스트 | closed |
| T-02-34 | Tampering | 검색 인젝션 | mitigate | Specification 파라미터 바인딩 + LIKE 이스케이프 | closed |
| T-02-35 | DoS | 페이지 크기 | mitigate | size @Min(1) @Max(100) | closed |
| T-02-36 | Info Disclosure | 목록 응답 | mitigate | 목록에 rejectionReason 미포함 | closed |
| T-02-37 | Elevation of Priv. | 승인 검증 | mitigate | approve()가 온보딩 완료 서버측 강제 | closed |
| T-02-38 | Elevation of Priv. | 상태 변경 후 접근 | mitigate | refresh 전량 폐기 + 매 요청 DB 재조회 + 403 테스트 | closed |
| T-02-39 | Repudiation | 거절 이력 | mitigate | INACTIVE + rejectionReason 보존 (삭제 없음) | closed |
| T-02-40 | Tampering | 폐기 원자성 | mitigate | 같은 트랜잭션 내 revokeAllForMember | closed |
| T-02-41 | Spoofing | 콘솔 설정 검증 | mitigate | 실제 카카오 계정 E2E 수동 검증 (02-11) | closed |
| T-02-42 | Info Disclosure | 시크릿 커밋 | mitigate | .gitignore .env, git ls-files 확인 | closed |
| T-02-43 | Elevation of Priv. | 테스트 라우트 누출 | mitigate | openapi.yaml에 __probe/__gated 0건 | closed |
| T-02-44 | Info Disclosure | 계정 열거(타이밍) | mitigate | loginId 미존재 시에도 더미 해시로 bcrypt 1회 실행 (WR-02, 커밋 304a3cc) | closed |
| T-02-12-01 | DoS | 재시도 폭주 | mitigate | 재시도 정확히 1회 제한 | closed |
| T-02-12-02 | Tampering | 경쟁 무결성 | mitigate | UNIQUE + KakaoLoginConcurrencyTest | closed |
| T-02-12-SC | Tampering | 의존성 | accept | build.gradle.kts 무변경 확인 | closed |
| T-02-13-01 | Spoofing | 동시 회전 | mitigate | 조건부 UPDATE(revokeIfUsable) + 갱신 행 수 판정 | closed |
| T-02-13-02 | Repudiation | 폐기 지속성 | mitigate | noRollbackFor로 실패 응답에서도 폐기 커밋 | closed |
| T-02-13-03 | Elevation of Priv. | 회전 경쟁 검증 | mitigate | RefreshTokenRotationConcurrencyTest | closed |
| T-02-13-04 | Info Disclosure | 실패 사유 | accept | 단일 코드 유지 (T-02-11과 동일) | closed |
| T-02-13-SC | Tampering | 의존성 | accept | build.gradle.kts 무변경 확인 | closed |
| T-02-14-01 | Elevation of Priv. | ACTIVE 우회 | mitigate | changeStatus도 온보딩 완료 강제 | closed |
| T-02-14-02 | Info Disclosure | 검색 전체매칭 | mitigate | 빈 정규화 검색어 차단 | closed |
| T-02-14-03 | Tampering | LIKE 이스케이프 | accept | 기존 escapeLikeWildcards 유지 확인 | closed |
| T-02-14-SC | Tampering | 의존성 | accept | build.gradle.kts 무변경 확인 | closed |
| T-02-15-01 | Info Disclosure | 계약 정합 | mitigate | @ParameterObject 개별 파라미터화 | closed |
| T-02-15-02 | Tampering | 재생성 파이프라인 | mitigate | D-029 gradle 태스크 체인 | closed |
| T-02-15-SC | Tampering | 의존성 | accept | springdoc 3.0.3 기존 포함 확인 | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-02-01 | T-02-28 | 관리자 로그인 rate limiting 미구현 — v1은 관리자 계정 소수 고정, 실패 로그(loginId)로 사후 탐지 가능. 스케일 시 재검토 | 소유자 (02-07 플랜 승인) | 2026-08-03 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-03 | 59 | 58 | 1 (타이밍 부채널 — 미등록) | gsd-security-auditor |
| 2026-08-03 | 59 | 59 | 0 (T-02-44로 등록·수정 후 재판정) | orchestrator + 소유자 승인 |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter
