# gold-wrestling-be

## What This Is

레슬링 체육관 "골드레슬링" 송파점의 회원 관리·수업 예약 시스템 **백엔드**.
회원은 카카오 로그인으로 가입해 관리자 승인 후 이용권(저녁반 회비 / 예약제 횟수권 / 1:1 레슨권)으로
수업을 예약하고, 관리자는 회원·이용권·예약·출석·공지·알림을 한 곳에서 운영한다.
Kotlin + Spring Boot 4.1.x + JPA + PostgreSQL, FE(React)와는 `docs/api/openapi.yaml` 계약으로만 통신한다.

## Core Value

**회원이 보는 잔여 횟수는 항상 실제 사용 가능 횟수와 일치한다.**
즉시 차감/복구 + 전 이력 기록(PassTransaction) + 초과 예약 0건 — 이 정합성이 무너지면 나머지 전부가 무의미하다.

## 스펙의 단일 진실 공급원 (SSOT)

이 문서는 프로젝트 컨텍스트 요약이다. **기능 스펙과 도메인 규칙의 원본은 `docs/`에 있다:**

- `docs/policies.md` — 차감·예약·상태 규칙의 최종 기준 (충돌 시 최우선)
- `docs/requirements.md` — 기능 요구사항 SSOT
- `docs/glossary.md` — 네이밍 (Pass/Reservation/ClassSession 등. Ticket/Voucher/Booking 금지)
- `docs/decisions.md` — 기술 결정 D-001~D-024 (Boot 4.1, 즉시 차감, DECIMAL(4,1), ProblemDetail, 기능별 패키지 …)
- `docs/conventions.md` — 코드·테스트 규약

`.planning/`이 `docs/`와 어긋나면 `docs/`가 이긴다. 스펙 변경은 `docs/`를 고친다.

## Requirements

### Validated

<!-- 뼈대(Spring Initializr + 초기 세팅)로 이미 존재하는 것 -->

- ✓ 애플리케이션 기동 + 헬스체크 엔드포인트 (`/api/system/health`, actuator) — 기존
- ✓ Flyway 마이그레이션 파이프라인 (V1 baseline, ddl-auto=validate) — 기존
- ✓ springdoc 세팅 + Swagger UI (`openapi.yaml` 최초 생성) — 기존
- ✓ Security 뼈대 (STATELESS, CORS, 전체 permitAll — 인증 phase에서 교체 예정) — 기존
- ✓ Testcontainers 통합테스트 골격 + ktlint 빌드 게이트 — 기존

### Active

<!-- 이번 로드맵(M1~M6). 상세는 REQUIREMENTS.md -->

- [ ] **M1 기반**: 공통 에러 포맷(RFC 9457 ProblemDetail 전역 핸들러), 초기 스키마(Branch/Member/Admin), openapi.yaml 재생성 파이프라인 정비
- [ ] **M2 인증·회원**: 카카오 OAuth 로그인, JWT(access/refresh), 가입 승인 플로우, 회원 상태 4단계, 관리자 인증
- [ ] **M3 이용권**: Pass 3종 등록, PassTransaction 이력, 관리자 수동 가감·기간 수정, 본인 이용권/이력 조회
- [ ] **M4 시간표·예약**: ClassSchedule/ClassSession, 예약 생성·취소·변경 + 즉시 차감/복구, 정원·1:1 슬롯 동시성 보장(+동시성 테스트), 관리자 대리 취소/변경, 휴강 처리
- [ ] **M5 배치**: 2주 미사용 차감(휴회 정지 포함), 유효기간 만료 — 멱등 설계
- [ ] **M6 운영**: 출석 체크, 공지사항 CRUD, 관리자 알림(폴링 API) + 활동 피드

### Out of Scope

- 프론트엔드 — 별도 레포 `gold-wrestling-fe` 담당 (D-003 멀티레포)
- 배포 파이프라인 (GitHub Actions → EC2) — 이번 로드맵 M1~M6에 미포함, 별도 작업으로 진행
- 지점 간 연동 (교차 예약·교차 관리자 권한) — MVP는 송파점 1개. 단 `branch_id`·`AdminBranch` 매핑으로 확장 여지는 설계에 반영
- 온라인 결제(PG) — 전 결제 오프라인, 관리자 수기 등록
- 웹 푸시 알림 (PWA + FCM) — v2 후보. MVP 알림은 폴링 30초
- 정원 초과 대기(waitlist), 카카오 알림톡 — v2 후보
- SSE 알림 — 폴링으로 시작, 이후 업그레이드 (requirements.md §4.5)

## Context

- **기존 코드**: Spring Initializr 뼈대 + 초기 세팅 12파일 (Validated 참조). 도메인 코드는 아직 없다
- **학습 겸용**: 소유자는 백엔드가 처음. 복잡한 결정은 대안 비교 제시, 완료 보고에 "이번에 쓴 기술" 섹션 필수 (CLAUDE.md 학습 모드)
- **API 계약**: springdoc이 생성하는 `docs/api/openapi.yaml`이 FE·BE 간 유일한 진실. API 변경 시 재생성·커밋이 각 마일스톤 완료 조건
- **테스트 방침**: 도메인 로직 = 단위 테스트, DB 로직 = Testcontainers 통합 테스트, M4 동시성 = 동시성 테스트 필수 (conventions.md §10.0 표 기준)
- **기존 프로세스 장치**: `.claude/skills/`(add-endpoint, add-migration, add-domain-test, verify-boot4-api) + hooks(ddl-auto 금지, 적용된 마이그레이션 수정 금지, 테스트 커버리지, openapi 재생성 리마인드)

## Constraints

- **Tech stack**: Kotlin + Spring Boot 4.1.x (Spring Framework 7) + JPA + PostgreSQL 18, JDK 21, Gradle Kotlin DSL — D-005/D-014. Boot 3 예제 이식 금지, 의존성은 Boot 4 호환 버전만
- **스키마**: 변경은 Flyway 마이그레이션만 (ddl-auto 금지). 커밋된 마이그레이션 수정 금지 — 새 버전 추가
- **에러 응답**: RFC 9457 ProblemDetail 고정, 커스텀 공통 래퍼 금지 — D-017
- **횟수 표현**: `DECIMAL(4,1)` + `BigDecimal`, 비교는 `compareTo` — D-016
- **동시성**: DB 제약 + 조건부 갱신 우선, 부족한 곳만 비관적 락 — D-021. 초과 예약 0건
- **트랜잭션**: 서비스 메서드 = 트랜잭션 단위 — D-020
- **시간대**: `Asia/Seoul` 명시, 주 시작은 월요일. `Clock` 빈 주입
- **시크릿**: 실값 커밋 절대 금지. `.env`(로컬) / 환경변수(배포), `.env.example`에 키 이름 동기화
- **브랜치**: dev 작업 → dev→main PR. main 직접 커밋 금지

## Key Decisions

기술 결정의 원본은 `docs/decisions.md`(D-001~D-024)다. 이 로드맵 수립 시점에 추가된 결정:

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 로드맵은 사용자 지정 M1~M6 마일스톤 구조를 따른다 | 기반→인증→이용권→예약→배치→운영 순으로 의존성이 자연스럽게 쌓인다 (예약은 이용권을, 이용권은 회원을 전제) | — Pending |
| 배포 파이프라인은 이번 로드맵에서 제외 | M1~M6에 미포함, 사용자 지정 범위 | — Pending |
| 코드베이스 매핑·도메인 리서치 생략 | 뼈대 12파일 + docs/가 이미 스펙·기술결정 SSOT. 리서치가 확정 결정과 모순될 위험이 이득보다 크다 | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions — **단, 도메인·기술 결정의 원본 기록은 `docs/decisions.md`에 남긴다**
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-07-30 after initialization*
