# Requirements: gold-wrestling-be — v1.1 배포·운영

**Defined:** 2026-09-08
**Core Value:** 회원이 보는 잔여 횟수는 항상 실제 사용 가능 횟수와 일치한다 (즉시 차감/복구 + 전 이력 + 초과 예약 0건)
**Milestone Goal:** 송파점 실운영 시작 가능한 상태 — v1.0 백엔드를 EC2(`api.goldwrestling.com`)에 자동 배포하고, 백업·복구·부하 검증·cron 활성화까지 운영 절차를 갖춘다.

> 이 마일스톤은 기능 추가가 아니라 **운영 인프라·절차**다. 도메인 규칙(`docs/policies.md`)은 바뀌지 않는다.
> "사용자"는 대부분 **운영자(소유자)**이며, 최종 수혜자는 실제로 서비스를 쓰게 되는 송파점 회원·관리자다.
> v1.0 요구사항(44건)은 `milestones/v1.0-REQUIREMENTS.md`에 아카이브돼 있다.

## 사전 준비 완료 (전제값, 2026-09-08)

- 도메인 `goldwrestling.com` / BE `api.goldwrestling.com` (Route53 A → Elastic IP `15.164.17.113`), FE `app.goldwrestling.com`(FE 레포)
- EC2 t3.micro(서울, Ubuntu 24.04, RAM 1GB), 보안그룹 22(내 IP)/80/443, `ubuntu@` + `~/.ssh/goldwrestling-server.pem`. 서버 내부는 빈 상태
- 카카오 콘솔 운영 리다이렉트 URI `https://app.goldwrestling.com/login/callback` 등록 완료
- 레포 PRIVATE(→ GHCR 이미지 private), main = dev(v1.0 + CI, PR #29), `origin/HEAD` = `origin/dev`

## v1.1 Requirements

### 컨테이너화·서버 구성 — INFRA

- [x] **INFRA-01**: 운영자가 `docker build` 한 번으로 실행 가능한 앱 이미지를 만들 수 있다 — 멀티스테이지 Dockerfile(빌더 스테이지가 jar 생성, 런타임 스테이지는 JDK 21 기반 최소 이미지, 비루트 실행). 테스트는 CI가 담당하므로 이미지 빌드에서는 생략
- [x] **INFRA-02**: 같은 Dockerfile로 amd64+arm64 멀티 아키텍처 이미지(매니페스트 1개)를 만들 수 있다 — 추후 서버 이전(Graviton 등) 대비. 빌더 스테이지는 빌드 머신 네이티브 플랫폼에서 1회만 컴파일한다(QEMU 위 Gradle 컴파일 회피)
- [x] **INFRA-03**: 운영자가 운영용 compose 파일 하나로 app+postgres+Caddy 세 컨테이너를 올릴 수 있다 — postgres 데이터는 named volume, app은 postgres healthcheck 통과 후 기동, 전부 `restart: unless-stopped`. 로컬 개발용 `docker-compose.yml`은 그대로 둔다
- [x] **INFRA-04**: `https://api.goldwrestling.com`이 Caddy 자동 HTTPS(Let's Encrypt)로 서비스된다 — 80→443 리다이렉트, app 컨테이너로 리버스 프록시, 인증서 저장소 볼륨 영속(재시작해도 재발급 안 함)
- [x] **INFRA-05**: RAM 1GB 서버에서 세 컨테이너가 기동·부하 중 OOM kill 없이 동작한다 — JVM 힙 상한 명시, postgres 메모리 파라미터 조정, 컨테이너별 메모리 제한, 스왑 2GB 전제. 메모리 예산표를 문서에 남긴다
- [x] **INFRA-06**: 운영자가 빈 Ubuntu 서버에 멱등 초기 세팅 스크립트를 실행하면 배포 가능 상태가 된다 — Docker(compose 플러그인) 설치, 스왑 2GB, 타임존 Asia/Seoul, 배포 디렉토리·`.env` 자리 생성. **두 번 실행해도 결과가 같다**
- [x] **INFRA-07**: 운영 환경변수의 키 목록·의미·예시가 레포에 문서화된다(실값 없음) — 운영 필수값(`KAKAO_REDIRECT_URI=https://app.goldwrestling.com/login/callback`, `CORS_ALLOWED_ORIGINS=https://app.goldwrestling.com`, `ADMIN_SEED_*`, JWT 등) 포함. 운영 `.env`는 서버에만 존재하고 레포 밖이다

### 배포 파이프라인 — DEPLOY

- [ ] **DEPLOY-01**: main push 시 CI(`ktlintCheck build`)가 통과한 뒤에만 배포가 시작된다 — CI 실패 시 이미지 빌드·배포가 실행되지 않는다
- [ ] **DEPLOY-02**: 배포 워크플로가 멀티 아키텍처 이미지를 GHCR에 커밋 SHA 태그 + `latest` 태그로 푸시한다
- [ ] **DEPLOY-03**: 배포 워크플로가 SSH로 서버에 접속해 compose pull → up으로 새 이미지로 교체한다(재시작 허용). GHCR 이미지는 **public**이라 서버 pull 인증이 없다(Phase 7 결정, `07-CONTEXT.md` D-03) — 워크플로는 push 인증(`GITHUB_TOKEN` write:packages)만 다룬다
- [ ] **DEPLOY-04**: 배포 후 `https://api.goldwrestling.com/actuator/health`가 `UP`을 반환할 때까지 폴링하고, 제한 시간 내 실패하면 워크플로가 실패(빨간불)한다
- [ ] **DEPLOY-05**: 필요한 GitHub Actions Secrets/Variables 목록과 등록 절차가 README에 정리되어 운영자가 직접 등록할 수 있다 — SSH 키·호스트·사용자 등 키 이름만, 실값 없음
- [ ] **DEPLOY-06**: 최초 배포·롤백 절차가 문서화된다 — `ADMIN_SEED_*`는 최초 기동 1회만 유효(관리자 생성 후 `.env`에서 제거 권장), 초기 배포 체크리스트(DNS·`.env`·Flyway V1~V12 적용 확인), 롤백은 이전 SHA 태그로 재배포
- [ ] **DEPLOY-07**: 실제 main push 1회로 운영 서버 배포가 성공한다 — `api.goldwrestling.com`에서 health `UP`, Swagger 경로 정책대로 동작, 관리자 로그인 성공을 실제로 확인·기록

### 운영 안전장치 — OPS

- [ ] **OPS-01**: 매일 1회(Asia/Seoul 새벽) `pg_dump` 결과가 S3 버킷에 업로드되고, 보관 정책(예: 30일)이 적용된다 — 실패 시 종료 코드와 로그로 판별 가능
- [ ] **OPS-02**: S3 버킷·IAM 권한 생성 절차가 문서화되어 운영자가 콘솔에서 직접 만들 수 있다 — EC2 인스턴스 프로파일(IAM Role) 우선 검토(액세스 키를 서버에 두지 않음)
- [ ] **OPS-03**: 복구 절차가 문서화되고 리허설 1회를 수행·기록한다 — S3의 dump로 빈 DB에 복원 → 앱 기동 → 데이터 일치 확인
- [ ] **OPS-04**: 서버 이전 절차가 문서화된다 — 새 서버 초기 세팅(INFRA-06) → dump → restore → `.env` 복제 → DNS 전환 → 구서버 정지, 각 단계의 확인 지점 포함
- [ ] **OPS-05**: Actuator 노출 범위가 확정된다 — **Caddy 외부 차단(`/actuator/health`만 통과)은 Phase 7이 구현·기록한다**(`07-CONTEXT.md` D-20). 이 요구사항은 앱 내부 노출 범위(`management.endpoints.web.exposure`, `show-details: never` 유지) 확정 + 실서버에서 차단 동작 확인 + `docs/decisions.md` 기록을 맡는다
- [ ] **OPS-06**: 세 컨테이너 로그가 로테이션된다(docker logging driver `max-size`/`max-file`) — 디스크 무한 증가 방지

### 검증·활성화 — VERIFY

- [ ] **VERIFY-01**: k6 부하테스트 스크립트가 레포에 있다 — ① 정원 N 세션에 N+M 회원 동시 예약, ② 1:1 슬롯에 다수 회원 동시 예약. 실행 방법 문서 포함
- [ ] **VERIFY-02**: 결과 수치(성공/거부 건수, **초과 예약 0건**, p95 응답시간, 서버 메모리)를 `docs/metrics.md`에 기록하고 D-098(k6 범위 밖)을 해소하는 결정을 기록한다
- [ ] **VERIFY-03**: 미사용 차감 cron이 D-151/D-130 3단계 절차(출석 배선 확인 → 운영 수동 실행 1회 검증 → `BATCH_INACTIVITY_SCHEDULER_ENABLED=true`)로 활성화되고 수행 기록이 남는다 — D-119 시행일 2026-09-01 기준 첫 실행 결과 해석(0건이 정상일 수 있음) 포함
- [ ] **VERIFY-04**: CR-03(출석 유무로 차감 결과가 갈리는 회원) 운영 데이터 대조가 관찰 항목으로 등록된다 — 대조 SQL/절차·주기·판정 기준을 운영 문서에 기록

## Open Questions (phase discuss에서 확정)

| # | 질문 | 관련 | 초기 추천 |
|---|---|---|---|
| Q1 | ~~private GHCR pull 인증 — 단기 `docker login` vs 서버 PAT 보관~~ **해소(Phase 7 discuss, 2026-09-08)**: GHCR 이미지를 public으로 두어 pull 인증 자체가 없다. 코드 노출은 소유자가 감수 | DEPLOY-03 | — |
| Q2 | CI → 배포 연결 방식 — `workflow_run`(ci.yml 완료 후 트리거) vs 배포 워크플로가 빌드·테스트를 자체 job으로 포함 | DEPLOY-01 | plan-phase 리서치 후 결정 |
| Q3 | k6 대상 환경 — 런칭 전 운영 서버(실 스펙 수치, 이후 DB 초기화) vs 로컬 운영 compose 스택 | VERIFY-01 | 런칭 전 운영 서버 + 테스트 데이터 정리 |
| Q4 | S3 인증 — 인스턴스 프로파일(IAM Role) vs 액세스 키 | OPS-02 | 인스턴스 프로파일 |

## v1.2 Requirements (deferred)

### FE 계약 변경 요청 — 이관

- **BE-REQ-001**: `openapi.yaml`에 4xx/5xx `ProblemDetail` 응답 스키마 선언 (FE는 런타임 타입가드로 우회 중)
- **BE-REQ-002**: `GET /api/admin/me` 관리자 신원 조회 (FE `RequireAdmin`은 토큰 존재만 판정 — 인가는 BE가 강제하므로 보안 구멍은 아님)
- **BE-REQ-006**: `GET /api/admin/reservations/{reservationId}` 예약 단건 조회 (대리 변경 시 다른 주 이동 케이스)

### 운영 고도화

- **MON-01**: 모니터링 대시보드(메트릭·알림) — 운영 시작 후 필요가 드러나면
- **ZERO-01**: 무중단 배포

## Out of Scope

| Feature | Reason |
|---------|--------|
| FE 배포(`app.goldwrestling.com`) | FE 레포(`gold-wrestling-fe`) 범위. BE는 CORS·카카오 리다이렉트 URI로만 연결 |
| 모니터링 대시보드(Prometheus·Grafana·알림) | v1.1은 health 게이트 + 로그 로테이션까지. 체육관 1개 규모에서 대시보드 운영 비용이 이득보다 크다 |
| 무중단 배포(블루/그린·롤링) | 배포 시 수십 초 재시작 허용. 소규모 트래픽에서 복잡도가 이득보다 크다 |
| BE-REQ-001·002·006 | v1.2 이관 — FE 우회가 안전하게 동작 중이고, v1.1은 API 표면을 바꾸지 않는다 |
| 스테이징 환경 | 서버 1대. 로컬 운영 compose 스택으로 대체 |
| 이미지 취약점 스캔·SBOM | v1.1 범위 밖. 이후 CI 보강 후보 |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| INFRA-01 | Phase 7 | Complete |
| INFRA-02 | Phase 7 | Complete |
| INFRA-03 | Phase 7 | Complete |
| INFRA-04 | Phase 7 | Complete |
| INFRA-05 | Phase 7 | Complete |
| INFRA-06 | Phase 7 | Complete |
| INFRA-07 | Phase 7 | Complete |
| DEPLOY-01 | Phase 8 | Pending |
| DEPLOY-02 | Phase 8 | Pending |
| DEPLOY-03 | Phase 8 | Pending |
| DEPLOY-04 | Phase 8 | Pending |
| DEPLOY-05 | Phase 8 | Pending |
| DEPLOY-06 | Phase 8 | Pending |
| DEPLOY-07 | Phase 8 | Pending |
| OPS-01 | Phase 9 | Pending |
| OPS-02 | Phase 9 | Pending |
| OPS-03 | Phase 9 | Pending |
| OPS-04 | Phase 9 | Pending |
| OPS-05 | Phase 9 | Pending |
| OPS-06 | Phase 9 | Pending |
| VERIFY-01 | Phase 10 | Pending |
| VERIFY-02 | Phase 10 | Pending |
| VERIFY-03 | Phase 10 | Pending |
| VERIFY-04 | Phase 10 | Pending |

**Coverage:**
- v1.1 requirements: 24 total
- Mapped to phases: 24
- Unmapped: 0 ✓

---
*Requirements defined: 2026-09-08*
*Last updated: 2026-09-08 after initial definition*
