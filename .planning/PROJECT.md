# gold-wrestling-be

## What This Is

레슬링 체육관 "골드레슬링" 송파점의 회원 관리·수업 예약 시스템 **백엔드**.
회원은 카카오 로그인으로 가입해 관리자 승인 후 이용권(저녁반 회비 / 예약제 횟수권 / 1:1 레슨권)으로
수업을 예약하고, 관리자는 회원·이용권·예약·출석·공지·알림을 한 곳에서 운영한다.
Kotlin + Spring Boot 4.1.x + JPA + PostgreSQL, FE(React)와는 `docs/api/openapi.yaml` 계약으로만 통신한다.

## Core Value

**회원이 보는 잔여 횟수는 항상 실제 사용 가능 횟수와 일치한다.**
즉시 차감/복구 + 전 이력 기록(PassTransaction) + 초과 예약 0건 — 이 정합성이 무너지면 나머지 전부가 무의미하다.

## Current State (v1.0 shipped 2026-09-01)

v1.0(M1~M6, Phase 1~6)이 dev에 머지 완료됐다 — 요구사항 44/44, 테스트 883건 0 failures,
프로덕션 12,113 LOC + 테스트 27,178 LOC(Kotlin), Flyway V1~V12, openapi 41경로.
상세는 `.planning/MILESTONES.md`와 `.planning/milestones/v1.0-*.md` 아카이브 참조.

main은 2026-09-08 PR #29로 dev와 동일해졌다(v1.0 + CI 워크플로). **그러나 운영 서버에는 아직
아무것도 배포되지 않았다** — Dockerfile·배포 워크플로가 없고, EC2는 SSH 접속만 확인된 빈 상태다.
v1.0 마감 때 적어 둔 "요일 정합 SQL 1회"(D-146)는 운영 DB가 빈 상태에서 시작하므로 해당 없음으로
정리한다. cron은 꺼진 채 배포하고 활성화는 README·D-130 절차를 따른다(D-151).

## Current Milestone: v1.1 배포·운영

**Goal:** 송파점 실운영 시작 가능한 상태 — v1.0 백엔드를 EC2(`api.goldwrestling.com`)에 자동
배포하고, 백업·복구·부하 검증·cron 활성화까지 운영 절차를 갖춘다.

**Target features (Phase 7~10 — v1.0이 Phase 6에서 끝났으므로 번호를 이어간다):**
- **컨테이너화·서버 구성** — 멀티스테이지 Dockerfile(JDK 21, amd64+arm64 — 추후 서버 이전 대비),
  운영 compose(app+postgres+Caddy), Caddyfile(자동 HTTPS), RAM 1GB 전제 JVM 힙 상한 + 스왑 2GB,
  멱등 서버 초기 세팅 스크립트(Docker 설치·스왑·타임존 Asia/Seoul·배포 디렉토리). 운영 `.env`는 서버에만 둔다
- **배포 파이프라인** — main push → CI 통과 → 이미지 빌드·GHCR 푸시 → SSH로 compose pull·up →
  Actuator health 확인(실패 시 워크플로 실패). Actions Secrets 목록 안내, `ADMIN_SEED_*`는 최초 기동 1회만 유효함을 운영 절차에 명시
- **운영 안전장치** — pg_dump 일 1회 → S3 백업(버킷 생성 안내) + 복구 절차 문서·리허설 1회 +
  서버 이전 절차 문서(dump→restore→DNS 전환), Actuator 노출 범위 확정, 컨테이너 로그 로테이션
- **검증·활성화** — k6 부하테스트(정원 경쟁·1:1 슬롯 동시 요청, 초과 예약 0건 수치를 `docs/metrics.md`에
  기록, D-098 해소), D-151/D-130 절차로 미사용 차감 cron 활성화(D-119 시행일 2026-09-01 유의),
  CR-03 운영 데이터 대조를 관찰 항목으로 등록

**사전 준비 완료(이 값들을 전제로 계획한다, 2026-09-08 기준):**
- 도메인 `goldwrestling.com` / BE `api.goldwrestling.com` (Route53 A 레코드 → Elastic IP 연결 완료),
  FE는 `app.goldwrestling.com`(FE 레포 담당)
- 서버 EC2 t3.micro(서울, Ubuntu 24.04, RAM 1GB), Elastic IP `15.164.17.113`, 보안그룹 22(내 IP)/80/443,
  접속 `ubuntu@` + `~/.ssh/goldwrestling-server.pem`. 내부는 빈 상태(SSH 접속만 확인)
- 카카오 콘솔에 운영 리다이렉트 URI `https://app.goldwrestling.com/login/callback` 등록 완료
- 레포는 **PRIVATE**(GHCR 이미지도 private → 서버 pull 인증 방식은 Phase 8에서 결정),
  `origin/HEAD`는 `origin/dev`로 교정된 상태

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
- ✓ **M1 기반** (Phase 1 완료, 2026-07-30): RFC 9457 ProblemDetail 전역 예외 핸들러(ErrorCode·DomainException·에러코드 레지스트리, D-028), 초기 스키마 V2(Branch/Member/Admin/AdminBranch + 송파점 시드), `generateApiDocs` 한 명령 openapi.yaml 재생성 파이프라인(D-029)
- ✓ **M2 인증·회원** (Phase 2 완료, 2026-08-03): 카카오 OAuth 로그인(인증 수단만, D-032), 온보딩(실명·전화번호), JWT access/refresh + 회전·재사용 감지(D-033·D-036), 가입 승인/거절·회원 상태 변경, 관리자 ID/PW 인증(D-026), SecurityConfig 역할 기반 인가(default-deny). 실제 카카오 계정 E2E 사람 검증 + 갭 클로저 4건(02-12~15)까지 통과 — 검증 5/5
- ✓ **M3 이용권** (Phase 3 완료, 2026-08-04): V4 스키마(pass/pass_transaction/pass_period_change + CHECK 5종), 단일 Pass 엔티티+판별 컬럼(D-060), 등록 3종·수동 가감(0.5단위·음수 거부, D-056)·기간 수정 통합(D-062)·등록 취소 상쇄(D-059)·관리자 목록·본인 이용권/이력 조회(D-058·D-070·D-071). 원장 불변식("잔여 = 이력 합계")을 PassLedgerInvariantTest가 실제 PostgreSQL로 실증. TDD 3플랜(RED→GREEN→REFACTOR), human-verify 승인 — 검증 6/6
- ✓ **M4 시간표·예약** (Phase 4 완료, 2026-08-08): V6~V8 스키마, 세션 get-or-create(D-094), 예약 생성·취소·변경 즉시 차감/복구, 정원·1:1 동시성(초과 예약 0건 실증), 관리자 보드·대리 취소/변경·휴강 캐스케이드, Notification 레코드 생성(NOTIF-01) — 검증 5/5, 실카카오 UAT passed
- ✓ **M5 배치** (Phase 5 완료, 2026-08-16): 2주 미사용 차감 — 기준일 5종 max(D-105), 상태 기반 멱등·캐치업(D-106), RUNNING 유니크 동시 실행 차단(D-117), 시행일 하한·실행 상한(D-119), 202 비동기 수동 실행. 최초 gaps_found(1/4) → 갭 클로저 청크 D로 재검증 passed 4/4
- ✓ **M6 운영** (Phase 6 완료, 2026-08-19): 출석 체크(레코드 부재=미체크 D-127, 건별 upsert D-132), 저녁반 0.5회 차감 한 트랜잭션(회비 우선·409 거부·삭제 복구, D-128), CR-03 배선(기준일 5종 완결, D-130), 공지 CRUD(D-131), 알림 폴링·모두읽음·활동 피드(D-129) — 검증 passed, 실기동 10항목 승인
- ✓ **v1 마감** (quick 260831-v1f, 2026-09-01, PR #27): 보강 수업 불허·요일 검증 초크포인트(D-146), INACTIVE 차감 예외 + 기준일 후보 ③ 확장·V12 리네임(D-147), FE 계약 요청 3건(명단 전화번호 D-148 / 관리자 이력 조회 D-149 / 예약 기간 필터 D-150), cron 방침(D-151)

### Active

<!-- v1.1 배포·운영. 상세 REQ-ID는 .planning/REQUIREMENTS.md -->

- 컨테이너화·서버 구성 (INFRA) — Dockerfile·운영 compose·Caddy·JVM/스왑·서버 초기 세팅 스크립트
- 배포 파이프라인 (DEPLOY) — main push → CI → GHCR → SSH compose → health 게이트, Secrets 안내
- 운영 안전장치 (OPS) — S3 백업·복구 리허설·서버 이전 절차·Actuator 범위·로그 로테이션
- 검증·활성화 (VERIFY) — k6 초과 예약 0건 실증(`docs/metrics.md`)·cron 활성화·CR-03 관찰 등록

### Out of Scope

- 프론트엔드 — 별도 레포 `gold-wrestling-fe` 담당 (D-003 멀티레포). **FE 배포(app.goldwrestling.com)도 FE 레포 범위**
- 모니터링 대시보드(Grafana·Prometheus 등) — v1.1은 health 게이트 + 로그 로테이션까지. 운영 시작 후 필요가 드러나면 다음 마일스톤
- 무중단 배포 — 배포 시 재시작(수십 초 다운) 허용. 체육관 1개·소규모 트래픽에서 블루/그린의 복잡도가 이득보다 크다
- BE-REQ-001(ProblemDetail 응답 스키마 선언)·BE-REQ-002(`GET /api/admin/me`)·BE-REQ-006(관리자 예약 단건 조회) — **v1.2로 이관.** FE 우회가 안전하게 동작 중이고, v1.1은 운영 시작을 최우선으로 한다
- 지점 간 연동 (교차 예약·교차 관리자 권한) — MVP는 송파점 1개. 단 `branch_id`·`AdminBranch` 매핑으로 확장 여지는 설계에 반영
- 온라인 결제(PG) — 전 결제 오프라인, 관리자 수기 등록
- 웹 푸시 알림 (PWA + FCM) — v2 후보. MVP 알림은 폴링 30초
- 정원 초과 대기(waitlist), 카카오 알림톡 — v2 후보
- SSE 알림 — 폴링으로 시작, 이후 업그레이드 (requirements.md §4.5)

## Context

- **코드베이스**: v1.0 기준 프로덕션 12,113 LOC + 테스트 27,178 LOC(Kotlin), 기능별 패키지 9개(member/pass/reservation/schedule/attendance/notice/notification/batch/auth), Flyway V1~V12, 테스트 883건
- **미결(운영)**: 운영 서버 미배포(main은 최신이나 Dockerfile·배포 워크플로 부재 — v1.1 대상), cron 기본 꺼짐(D-151), FE 계약 요청 잔여 3건(BE-REQ-001·002·006)은 v1.2로 이관
- **운영 인프라 제약(v1.1)**: t3.micro RAM 1GB 위에 app+postgres+Caddy 세 컨테이너 — JVM 힙 상한과 스왑 2GB 없이는 OOM으로 기동 자체가 실패할 수 있다. 무중단 배포는 하지 않는다(재시작 허용)
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
| 로드맵은 사용자 지정 M1~M6 마일스톤 구조를 따른다 | 기반→인증→이용권→예약→배치→운영 순으로 의존성이 자연스럽게 쌓인다 (예약은 이용권을, 이용권은 회원을 전제) | ✓ Good — 순서 재조정 0회로 v1.0 완주 |
| 배포 파이프라인은 이번 로드맵에서 제외 | M1~M6에 미포함, 사용자 지정 범위 | ✓ Revisit 해소 — v1.1(Phase 7~10)이 배포·운영 전체를 담는다 |
| 코드베이스 매핑·도메인 리서치 생략 | 뼈대 12파일 + docs/가 이미 스펙·기술결정 SSOT. 리서치가 확정 결정과 모순될 위험이 이득보다 크다 | ✓ Good — docs/ 우선순위 규칙이 6개 phase 내내 유효 |
| v1.1은 사용자 지정 4-phase 구조(컨테이너화→파이프라인→안전장치→검증·활성화)를 따르고 마일스톤 리서치는 생략 | 사전 준비 값(도메인·서버·카카오 URI)이 확정돼 있고 로드맵이 구체적이다. phase별 리서치는 plan-phase에서 어차피 돈다(`workflow.research=true`) | — (2026-09-08 시작) |
| BE-REQ-001·002·006은 v1.2로 이관 | FE 우회가 안전하게 동작 중. v1.1은 운영 시작을 최우선으로 하며 API 표면 변경을 섞지 않는다 | — |

마일스톤 기간 중 도메인·기술 결정 127건(D-025~D-151)은 전부 `docs/decisions.md`에 있다 —
phase별 핵심 요약은 `.planning/milestones/v1.0-ROADMAP.md` Milestone Summary 참조.

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
*Last updated: 2026-09-08 — v1.1 배포·운영 마일스톤 시작*
