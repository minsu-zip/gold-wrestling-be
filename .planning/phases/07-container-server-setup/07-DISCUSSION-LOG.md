# Phase 7: 컨테이너화·서버 구성 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-08
**Phase:** 7-컨테이너화·서버 구성
**Areas discussed:** 검증 범위·실서버 첫 기동, 메모리 예산·운영 설정 주입, 런타임 이미지·jar 실행 형태, 레포 배치·서버 디렉토리·Caddy 라우팅

**진행 방식:** 소유자가 네 영역 전부에 입장을 선제 제시("반대 근거 없으면 확정") → 항목별로 사실관계(레포 가시성, Boot 4 jarmode, `.env`/compose 키 제약, 메모리 산식)를 대조 → 충돌·위험 지점 3개만 되물어 확정.

---

## 검증 범위·실서버 첫 기동

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 7에서 EC2 수동 1회 배포 | 성공 기준 3·4는 실서버 전용. GHCR 수동 push → 서버 pull로 Phase 8 경로 선행 | ✓ (소유자 안) |
| 로컬 시뮬레이션까지, 실측은 Phase 8 | 메모리 제한 compose로 로컬 검증만 | |

**GHCR 가시성 (되물음)**

| Option | Description | Selected |
|--------|-------------|----------|
| public 유지 (사용자 안) | 서버 pull 인증 제거, Phase 8 Q1 해소. 바이트코드 노출 감수 | ✓ |
| private + 서버 1회 docker login | read:packages PAT를 서버에 장기 보관 | |
| private, Phase 8에서 단기 토큰 자동화 | Phase 7은 docker save/scp/load로 전달 | |

**User's choice:** 수동 배포 수행, GHCR public.
**Notes:** 레포는 PRIVATE임을 `gh repo view`로 확인한 뒤 "이미지 public = 코드 노출"을 명시적으로 알리고 확정받음. 시크릿은 전부 env라 이미지 안에 없다.

---

## 메모리 예산·운영 설정 주입

| Option | Description | Selected |
|--------|-------------|----------|
| limit app 550M / pg 300M / caddy 50M + 스왑 2G | 소유자 안 | ✓ |
| Hikari 5 / Tomcat 50 | 소유자 안, decisions에 근거 기록 | ✓ |
| prod 프로필 신설 없음, env 유지 | 소유자 안 (D-011 정합) | ✓ |
| Swagger 운영 비활성 | 소유자 안 — 명시 플레이스홀더 키로 구현(점 든 키는 compose env_file 거부) | ✓ |

**힙 비율 (되물음)**

| Option | Description | Selected |
|--------|-------------|----------|
| 60%로 시작, 실측 후 조정 (추천) | 힙 ~330M, docker stats 실측값을 예산표에 기록 | ✓ |
| 70% 유지 | 힙 ~385M, 힙 밖 165M — OOM kill 경계 | |

**User's choice:** 60% 시작.
**Notes:** 70% 기각 근거는 Boot+Hibernate 힙 밖 상주 150~250M 대비 여유 부족.

---

## 런타임 이미지·jar 실행 형태

| Option | Description | Selected |
|--------|-------------|----------|
| temurin 21 JRE (Ubuntu 계열) | 소유자 안. INFRA-01 "JDK 21 기반"은 JRE로 충족 해석 | ✓ |
| Alpine / distroless | 기각 (musl 호환·디버깅 편의) | |
| Boot 레이어 추출 이미지 | 소유자 안("layertools") → Boot 4는 `-Djarmode=tools extract --layers`로 정정, 플랜에서 verify-boot4-api | ✓ |
| fat jar 그대로 | 비교군으로만 빌드해 절감 수치 산출 | |
| CDS | 스킵 → v1.2 후보 | |

**User's choice:** 위와 같음. 절감 수치는 `docs/metrics.md`(Phase 7이 최초 생성)에 기록.

---

## 레포 배치·서버 디렉토리·Caddy 라우팅

| Option | Description | Selected |
|--------|-------------|----------|
| Dockerfile 루트 + deploy/ | 소유자 안 | ✓ |
| 서버 /opt/gold-wrestling/{compose,Caddyfile,.env,backups}, git 없음 | 소유자 안 → Phase 8은 scp 전달 전제 | ✓ |
| Caddyfile 도메인 `{$DOMAIN}` | 소유자 안 | ✓ |
| /actuator 외부 차단을 Phase 7에서 | 소유자 안 → Phase 9 OPS-05는 내부 노출 범위로 축소 | ✓ |

**문서 정정 (되물음)**

| Option | Description | Selected |
|--------|-------------|----------|
| 같이 정정 (추천) | REQUIREMENTS Q1·DEPLOY-03·OPS-05, ROADMAP Phase 8/9 메모를 같은 커밋에서 정정 | ✓ |
| CONTEXT.md에만 기록 | 정정은 나중에 | |

---

## Claude's Discretion

- 이미지 이름·`APP_IMAGE` 태그 교체 방식, postgres 포트 미노출·메모리 파라미터, 운영 문서 위치(`docs/operations.md` 추천), `.env.example` 신규 키, 서버 세팅 스크립트의 Docker 설치·실행·멱등성 패턴, 로컬 사전 검증 오버라이드 형태, Swagger 404 테스트 1건, 청크 경계

## Deferred Ideas

- CDS/AppCDS 기동 단축 (v1.2)
- Phase 8 Q1 해소(GHCR public) — Phase 8은 push 인증만
- 로그 로테이션은 Phase 9 그대로
