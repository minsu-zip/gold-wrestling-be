# Roadmap: gold-wrestling-be

## Milestones

- ✅ **v1.0 M1~M6** — Phases 1-6 (shipped 2026-09-01) · [아카이브](milestones/v1.0-ROADMAP.md) · [MILESTONES.md](MILESTONES.md)
- 🚧 **v1.1 배포·운영** — Phases 7-10 (시작 2026-09-08) · 요구사항 24건 · [REQUIREMENTS.md](REQUIREMENTS.md)

## Phases

<details>
<summary>✅ v1.0 M1~M6 (Phases 1-6) — SHIPPED 2026-09-01</summary>

- [x] Phase 1: 기반 (3/3 plans) — ProblemDetail·초기 스키마·openapi 파이프라인 (completed 2026-07-30)
- [x] Phase 2: 인증·회원 (15/15 plans) — 카카오 로그인·온보딩·JWT·승인·회원 관리 (completed 2026-08-03)
- [x] Phase 3: 이용권 (11/11 plans) — Pass 3종·PassTransaction 원장·가감·기간·취소 (completed 2026-08-04)
- [x] Phase 4: 시간표·예약 (15/15 plans) — 즉시 차감/복구·동시성 보장·관리자 운영·휴강 (completed 2026-08-08)
- [x] Phase 5: 배치 (16/16 plans) — 2주 미사용 차감·멱등·갭 클로저(청크 D) (completed 2026-08-16)
- [x] Phase 6: 운영 (11/11 plans) — 출석·저녁반 0.5회 차감·공지·알림·활동 피드 (completed 2026-08-19)

phase 상세(Goal·Success Criteria·플랜 목록·충족 근거)는 [아카이브](milestones/v1.0-ROADMAP.md) 참조.
마감 후 quick task로 v1 잔여 결정·계약 요청을 처리했다(PR #27, D-146~D-151).

</details>

- [ ] **Phase 7: 컨테이너화·서버 구성** - 멀티스테이지 Dockerfile·운영 compose·Caddy 자동 HTTPS·RAM 1GB 메모리 예산·멱등 서버 초기 세팅
- [ ] **Phase 8: 배포 파이프라인** - main push → CI 통과 → GHCR 푸시 → SSH compose 배포 → health 게이트
- [ ] **Phase 9: 운영 안전장치** - S3 백업·복구 리허설·서버 이전 절차·Actuator 노출 범위·로그 로테이션
- [ ] **Phase 10: 검증·활성화** - k6 부하테스트로 초과 예약 0건 실증·미사용 차감 cron 활성화·CR-03 관찰 등록

## Phase Details

### Phase 7: 컨테이너화·서버 구성

**Goal**: 운영자가 v1.0 백엔드를 이미지로 빌드하고, RAM 1GB EC2 서버에 app+postgres+Caddy 세 컨테이너를 안전하게 올릴 수 있는 상태를 만든다.
**Depends on**: Nothing (v1.1 첫 phase, v1.0 코드베이스가 전제)
**Requirements**: INFRA-01, INFRA-02, INFRA-03, INFRA-04, INFRA-05, INFRA-06, INFRA-07
**Success Criteria** (what must be TRUE):
  1. 운영자가 `docker build` 한 번으로 멀티스테이지(빌더가 jar 생성, 런타임은 JDK 21 최소 이미지·비루트 실행) Dockerfile을 amd64+arm64 매니페스트 1개로 빌드할 수 있다 — 빌더 스테이지는 네이티브 플랫폼에서 1회만 컴파일한다
  2. 운영용 compose 파일 하나로 app+postgres+Caddy 세 컨테이너를 올리면 postgres named volume이 유지되고, app은 postgres healthcheck 통과 후 기동하며, 전부 `restart: unless-stopped`다 (로컬 개발용 `docker-compose.yml`은 그대로 둔다)
  3. `https://api.goldwrestling.com`이 Caddy 자동 HTTPS(Let's Encrypt)로 서비스되고 80→443 리다이렉트가 동작하며, 컨테이너를 재시작해도 인증서 저장소 볼륨이 영속되어 재발급이 일어나지 않는다
  4. RAM 1GB 서버에서 세 컨테이너가 기동·부하 중 OOM kill 없이 동작한다 — JVM 힙 상한·컨테이너별 메모리 제한·스왑 2GB 전제가 문서의 메모리 예산표로 뒷받침된다
  5. 운영자가 빈 Ubuntu 서버에 멱등 초기 세팅 스크립트(Docker+compose 플러그인 설치, 스왑 2GB, 타임존 Asia/Seoul, 배포 디렉토리·`.env` 자리 생성)를 두 번 실행해도 두 번째 실행이 실패하지 않고 같은 결과를 내며, 운영 환경변수(`KAKAO_REDIRECT_URI`, `CORS_ALLOWED_ORIGINS`, `ADMIN_SEED_*`, JWT 등) 키 목록·의미·예시가 실값 없이 레포에 문서화된다
**Plans**: 6 plans (4 waves · 청크 7a=wave 1~2, 7b=wave 3~4)
- [x] 07-01-PLAN.md — 앱 운영 설정 확장(Hikari 5·Tomcat 50·Swagger 토글) + SwaggerDisabledTest + decisions D-169~D-175 [wave 1]
- [x] 07-02-PLAN.md — 멀티스테이지 Dockerfile·.dockerignore + 멀티아키 빌드 실측 + fat jar 대비 절감 수치(docs/metrics.md) [wave 1]
- [ ] 07-03-PLAN.md — deploy/compose.prod.yml·Caddyfile·로컬 오버라이드 + 로컬 3컨테이너 기동 검증 [wave 2]
- [ ] 07-04-PLAN.md — deploy/server-setup.sh(멱등) + docs/operations.md(env 키표·메모리 예산표·수동 배포 runbook) [wave 3]
- [ ] 07-05-PLAN.md — GHCR 멀티아키 이미지 게시 + 패키지 public 전환·무인증 pull 확인 (human-action) [wave 3]
- [ ] 07-06-PLAN.md — 실 EC2 수동 배포: 멱등성 2회 실행·실도메인 HTTPS·인증서 영속·1GB 메모리 실측 (human-verify) [wave 4]

### Phase 8: 배포 파이프라인

**Goal**: main push 한 번으로 CI 통과 → 이미지 빌드·GHCR 푸시 → 서버 배포 → health 확인까지 자동으로 이어지고, 실패 시 워크플로가 명확히 빨간불이 된다.
**Depends on**: Phase 7 (배포 대상 이미지 정의·compose·서버 세팅 스크립트가 존재해야 배포 워크플로가 그 위에서 동작한다)
**Requirements**: DEPLOY-01, DEPLOY-02, DEPLOY-03, DEPLOY-04, DEPLOY-05, DEPLOY-06, DEPLOY-07
**Open Questions to settle** (discuss-phase에서 확정):
  - ~~Q1 — private GHCR pull 인증~~ **해소(Phase 7, 2026-09-08)**: GHCR 이미지 public → 서버 pull 인증 없음. 워크플로는 push 인증(`GITHUB_TOKEN` write:packages)만. Phase 7 전제 추가: 서버에 git이 없으므로 compose·Caddyfile 변경분은 워크플로가 scp로 전달한다(`07-CONTEXT.md` D-18)
  - Q2 — CI→배포 연결 방식: `workflow_run`(ci.yml 완료 후 트리거) vs 배포 워크플로가 빌드·테스트를 자체 job으로 포함 (초기 추천: plan-phase 리서치 후 결정)
**Touches decisions**: D-152 (CI 워크플로 ktlintCheck build — 이 게이트를 배포 트리거 조건으로 재사용), D-038 (관리자 시드 — `ADMIN_SEED_*` 최초 1회성 규칙을 배포 절차에 반영)
**Success Criteria** (what must be TRUE):
  1. main push 시 CI(`ktlintCheck build`)가 통과한 뒤에만 이미지 빌드·배포가 시작되고, CI가 실패하면 배포 단계가 아예 실행되지 않는다
  2. 배포 워크플로가 멀티 아키텍처 이미지를 GHCR에 커밋 SHA 태그 + `latest` 태그로 푸시한다
  3. 배포 워크플로가 SSH로 서버에 접속해 compose pull → up으로 새 이미지로 교체하고, private GHCR pull 인증에서 서버에 장기 토큰을 남기지 않는 방식을 쓴다
  4. 배포 후 `https://api.goldwrestling.com/actuator/health`가 `UP`을 반환할 때까지 폴링하고, 제한 시간 내 실패하면 워크플로가 실패(빨간불)한다
  5. README에 필요한 GitHub Actions Secrets/Variables 목록·등록 절차와 최초 배포·롤백 절차(`ADMIN_SEED_*` 1회성 포함)가 정리되어 있고, 실제 main push 1회로 `api.goldwrestling.com`에서 health `UP`·Swagger 경로 정책·관리자 로그인 성공이 실측·기록된다
**Plans**: TBD

### Phase 9: 운영 안전장치

**Goal**: 운영 DB가 매일 자동 백업되고, 복구·서버 이전 절차가 실제로 검증되어 있으며, Actuator 노출 범위와 로그 증가가 통제된다.
**Depends on**: Phase 8 (백업·복구 리허설과 Actuator 노출 확정은 실제로 기동 중인 운영 서버를 전제로 한다)
**Requirements**: OPS-01, OPS-02, OPS-03, OPS-04, OPS-05, OPS-06
**Open Questions to settle** (discuss-phase에서 확정):
  - Q4 — S3 인증: 인스턴스 프로파일(IAM Role) vs 액세스 키 (초기 추천: 인스턴스 프로파일, 액세스 키를 서버에 두지 않음)
**Touches decisions**: application.yml Actuator **내부** 노출 설정(`management.endpoints.web.exposure`, `show-details: never` 유지) — Caddy 외부 차단(`/actuator/health`만 통과)은 Phase 7이 구현·기록했다(`07-CONTEXT.md` D-20). OPS-05는 내부 범위 확정 + 실서버 차단 확인 + docs/decisions.md 기록
**Success Criteria** (what must be TRUE):
  1. 매일 1회(Asia/Seoul 새벽) `pg_dump` 결과가 S3 버킷에 업로드되고 보관 정책(예: 30일)이 적용되며, 실패 시 종료 코드와 로그로 판별할 수 있다
  2. S3 버킷·IAM 권한(인스턴스 프로파일 우선) 생성 절차가 문서화되어 운영자가 AWS 콘솔에서 직접 만들 수 있다
  3. 복구 절차 문서를 따라 S3의 dump로 빈 DB에 복원 → 앱 기동 → 데이터 일치 확인까지 리허설 1회를 실제로 수행하고 결과를 기록한다
  4. 서버 이전 절차(신규 서버 초기 세팅 → dump → restore → `.env` 복제 → DNS 전환 → 구서버 정지, 각 단계 확인 지점 포함)가 문서화된다
  5. Phase 7이 넣은 Caddy 차단이 실서버에서 확인되고(`/actuator/health`만 응답, 나머지 actuator endpoint 차단), 앱 내부 노출 범위 결정이 `docs/decisions.md`에 기록되고, 세 컨테이너 로그가 `max-size`/`max-file` 설정으로 로테이션되어 디스크가 무한 증가하지 않는다
**Plans**: TBD

### Phase 10: 검증·활성화

**Goal**: 실제 운영 조건에서 초과 예약 0건이 수치로 실증되고, 미사용 차감 cron이 안전 절차를 거쳐 켜지며, CR-03 운영 대조가 관찰 체계로 등록되어 실운영 시작 조건이 충족된다.
**Depends on**: Phase 8 (k6 부하테스트 대상이 되는 배포된 서버가 있어야 한다), Phase 9 (부하테스트 전 백업·복구 절차가 준비돼 있어야 테스트 중 데이터 손상 위험에 대비할 수 있다)
**Requirements**: VERIFY-01, VERIFY-02, VERIFY-03, VERIFY-04
**Open Questions to settle** (discuss-phase에서 확정):
  - Q3 — k6 대상 환경: 런칭 전 운영 서버(실 스펙 수치, 이후 DB 초기화) vs 로컬 운영 compose 스택 (초기 추천: 런칭 전 운영 서버 + 테스트 데이터 정리)
**Touches decisions**: D-098 (k6 부하테스트를 범위 밖으로 미뤘던 결정 — 이 phase에서 해소·대체), D-119/D-130/D-151 (미사용 차감 cron 시행일 하한·활성화 3단계 절차·기본 꺼짐 방침을 실제로 실행), CR-03 (출석 유무로 차감 결과가 갈리는 회원 — 운영 데이터 대조를 관찰 항목으로 등록)
**Success Criteria** (what must be TRUE):
  1. k6 부하테스트 스크립트(① 정원 N 세션에 N+M 회원 동시 예약, ② 1:1 슬롯에 다수 회원 동시 예약)가 레포에 있고 실행 방법이 문서화된다
  2. k6 실행 결과(성공/거부 건수, **초과 예약 0건**, p95 응답시간, 서버 메모리)가 `docs/metrics.md`에 기록되고, D-098(k6 범위 밖 결정)을 해소하는 새 결정이 남는다
  3. 미사용 차감 cron이 D-151/D-130 3단계 절차(출석 배선 확인 → 운영 수동 실행 1회 검증 → `BATCH_INACTIVITY_SCHEDULER_ENABLED=true`)로 활성화되고, D-119 시행일(2026-09-01) 기준 첫 실행 결과 해석(0건이 정상일 수 있음 포함)과 함께 수행 기록이 남는다
  4. CR-03(출석 유무로 차감 결과가 갈리는 회원) 운영 데이터 대조 SQL/절차·주기·판정 기준이 운영 문서에 관찰 항목으로 등록된다
**Plans**: TBD

## Backlog (v1.2 이후)

- **FE 계약 요청 잔여 3건 (v1.2)** — BE-REQ-001(4xx/5xx ProblemDetail 스키마 선언), BE-REQ-002(`GET /api/admin/me`), BE-REQ-006(관리자 예약 단건 조회). 상세·FE 우회 현황은 STATE.md Deferred Items와 `../gold-wrestling-fe/.planning/BE-CHANGE-REQUESTS.md`
- v2 후보(REQUIREMENTS 아카이브 §v2): KAKAO-01 자동수집, PROF-01 셀프 프로필 수정, CROSS-01 지점 간 연동, PAY-01 결제, PUSH-01 웹 푸시, WAIT-01 대기열, TALK-01 알림톡, SSE-01
- 운영 고도화(v1.1 REQUIREMENTS.md §v1.2 Requirements): MON-01 모니터링 대시보드, ZERO-01 무중단 배포

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. 기반 | v1.0 | 3/3 | Complete | 2026-07-30 |
| 2. 인증·회원 | v1.0 | 15/15 | Complete | 2026-08-03 |
| 3. 이용권 | v1.0 | 11/11 | Complete | 2026-08-04 |
| 4. 시간표·예약 | v1.0 | 15/15 | Complete | 2026-08-08 |
| 5. 배치 | v1.0 | 16/16 | Complete | 2026-08-16 |
| 6. 운영 | v1.0 | 11/11 | Complete | 2026-08-19 |
| 7. 컨테이너화·서버 구성 | v1.1 | 2/6 | In Progress|  |
| 8. 배포 파이프라인 | v1.1 | 0/? | Not started | - |
| 9. 운영 안전장치 | v1.1 | 0/? | Not started | - |
| 10. 검증·활성화 | v1.1 | 0/? | Not started | - |
