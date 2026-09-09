# 운영 문서 (operations.md)

> 이 문서는 운영 환경변수·메모리 예산·서버 초기 세팅·수동 배포 절차의 정본이다.
> 값의 "왜"는 `docs/decisions.md` D-169~D-177에 있다 — 여기서는 값 자체와 절차만 다룬다.
> 운영 `.env`는 서버(`/opt/gold-wrestling/.env`)에만 존재하고 이 레포 밖이다. 이 문서 어디에도
> 실제 시크릿 값·도메인·이메일은 쓰지 않는다 — 키 이름과 생성 방법, 플레이스홀더만 남긴다.

## 1. 운영 환경변수 (INFRA-07)

`.env.example`의 모든 키를 아래 표에 싣는다. "출처·주의" 열에 **운영에서 로컬과 값이 달라야 하는 키**를
표시했다. 실제 시크릿 값(`JWT_SECRET`·`DB_PASSWORD`·`KAKAO_*`)은 여기 쓰지 않고 생성 방법만 남긴다.

| 키 | 의미 | 예시/기본값 | 출처·주의 |
|---|---|---|---|
| `DB_HOST` | Postgres 접속 호스트 | 로컬 `localhost` / 운영 `postgres`(compose 서비스명) | **운영에서 값이 다르다** — compose 내부 네트워크의 서비스 이름을 쓴다 |
| `DB_PORT` | Postgres 접속 포트 | `5432` | 운영 compose는 postgres 호스트 포트를 노출하지 않는다(내부 네트워크만) |
| `DB_NAME` | DB 이름 | `gold_wrestling` | — |
| `DB_USERNAME` | DB 사용자 | `gold` | — |
| `DB_PASSWORD` | DB 비밀번호 | 생성: `openssl rand -base64 32` | 실값 절대 커밋 금지. 서버 `.env`에만 존재 |
| `SERVER_PORT` | 앱 리스닝 포트 | `8080` | 운영 compose는 앱 호스트 포트를 노출하지 않는다(Caddy만 진입점) |
| `TZ` | 서버 기준 시간대 | `Asia/Seoul` | 고정값, 변경 금지(CLAUDE.md 시간대 규칙) |
| `JWT_SECRET` | JWT 서명 시크릿(HS256) | 생성: `openssl rand -base64 48` | 최소 32바이트, 실값 절대 커밋 금지 |
| `JWT_ACCESS_TOKEN_EXPIRY_MINUTES` | 액세스 토큰 만료(분) | 배포된 기본값 그대로 | — |
| `JWT_REFRESH_TOKEN_EXPIRY_DAYS` | 리프레시 토큰 만료(일) | 배포된 기본값 그대로 | — |
| `KAKAO_REST_API_KEY` | 카카오 REST API 키 | 카카오 개발자 콘솔에서 발급 | 실값 절대 커밋 금지 |
| `KAKAO_CLIENT_SECRET` | 카카오 클라이언트 시크릿 | 카카오 개발자 콘솔에서 발급 | 실값 절대 커밋 금지 |
| `KAKAO_REDIRECT_URI` | 카카오 로그인 콜백 URI | `https://app.goldwrestling.com/login/callback` | **운영에서 값이 다르다** — REQUIREMENTS.md INFRA-07 명시값. 카카오 콘솔에 등록 완료(PROJECT.md) |
| `CORS_ALLOWED_ORIGINS` | 허용 프론트엔드 오리진(쉼표 구분) | `https://app.goldwrestling.com` | **운영에서 값이 다르다** — REQUIREMENTS.md INFRA-07 명시값. 로컬은 `http://localhost:5180,http://localhost:5181`(D-012) |
| `ADMIN_SEED_LOGIN_ID` | 관리자 시드 로그인 ID | 최초 배포 1회만 채움 | D-038 — 채운 상태로 기동하면 없을 때만 멱등 생성, 이후 비운다(§4 참조) |
| `ADMIN_SEED_PASSWORD` | 관리자 시드 비밀번호 | 최초 배포 1회만 채움 | D-038 — 생성 확인 후 서버 `.env`에서 비운다 |
| `ADMIN_SEED_NAME` | 관리자 시드 이름 | 최초 배포 1회만 채움 | D-038 |
| `BATCH_INACTIVITY_SCHEDULER_ENABLED` | 2주 미사용 차감 cron 킬 스위치 | **기본 `false` 유지** | **기본값 꺼짐이 의도다.** 켜는 판단은 배포 후 D-130 3단계 절차(README §미사용 차감 배치 운영)로 한다(D-116·D-121·D-151) |
| `BATCH_INACTIVITY_POLICY_EFFECTIVE_DATE` | 배치 정책 시행일 하한 | 비우면 기본 `2026-09-01` | D-119 |
| `BATCH_INACTIVITY_MAX_DEDUCTIONS_PER_RUN` | 배치 1회 실행당 회원 1인 최대 차감 횟수 | 비우면 기본 `1` | D-119 |
| `BATCH_INACTIVITY_STALE_RUN_TIMEOUT` | 죽은 실행(RUNNING) 정리 기준 시간 | 비우면 기본 `30m` | D-117 |
| `DEFAULT_BRANCH_NAME` | 신규 회원 자동 배정 지점명 | `송파점` | D-047, MVP는 단일 지점 |
| `DOMAIN` | Caddy가 자동 HTTPS를 발급할 도메인 | `<your-domain>`(예: API 도메인) | **운영에서 값이 다르다.** 비어 있으면 운영 compose가 `up` 자체를 거부한다(D-177) — 로컬 검증은 `deploy/.env`에 `localhost` |
| `ACME_EMAIL` | Let's Encrypt 인증서 만료 알림 수신 이메일 | `<your-email>` | **운영에서 값이 다르다.** 비어 있으면 운영 compose가 `up` 자체를 거부한다(D-177) — 로컬 검증은 `dev@example.invalid` |
| `SWAGGER_ENABLED` | Swagger UI·api-docs 노출 여부 | 로컬 기본 `true` / **운영 `false`** | D-171 — 운영에서 끄면 `springdoc`이 핸들러를 등록하지 않아 404, `SecurityConfig`는 무변경 |
| `DB_HIKARI_MAX_POOL_SIZE` | Hikari 커넥션 풀 최대 크기 | `5` | D-170 — 1GB 서버·배치 `REQUIRES_NEW`가 최대 커넥션 2개 쓰는 패턴 기준 |
| `SERVER_TOMCAT_THREADS_MAX` | Tomcat 최대 스레드 수 | `50` | D-170 — 스레드 하나마다 스택 메모리가 550M 힙 예산을 잠식 |
| `JAVA_TOOL_OPTIONS` | 운영 compose가 JVM 메모리 플래그를 주입하는 통로 | 비우면 compose 내장 기본값(§2 참조) 사용 | D-173 — 재빌드 없이 힙 비율 조정 가능. 실측 후 재조정 시 이 키에 전체 플래그를 채운다 |
| `APP_IMAGE` | 운영 compose가 pull할 이미지 태그 | 비우면 `:latest` | Phase 8 SHA 태그 배포 대비(D-02) |

## 2. 메모리 예산 (INFRA-05)

전제: t3.micro RAM 1GB(공칭 1024MB, 실제 가용 약 950MB) + **스왑 2GB**(`deploy/server-setup.sh`가 생성).
스왑이 켜져 있지 않으면 아래 limit 합계가 여유 없이 물리 RAM을 거의 다 채워 OOM 위험이 커진다.

| 대상 | limit | 실측 RSS | 여유 | 비고 |
|---|---|---|---|---|
| app | 550M | (미측정 — 07-06 실서버 배포에서 기입) | (미측정 — 07-06 실서버 배포에서 기입) | JVM `MaxRAMPercentage=60` → 힙 ≈330M / 비힙(메타스페이스·스레드·코드캐시) ≈220M. 로컬 참고값(07-03 실측) RSS 약 365MiB |
| postgres | 300M | (미측정 — 07-06 실서버 배포에서 기입) | (미측정 — 07-06 실서버 배포에서 기입) | `shared_buffers=64MB` 등(아래 상세). 로컬 참고값(07-03 실측) RSS 약 34MiB |
| caddy | 50M | (미측정 — 07-06 실서버 배포에서 기입) | (미측정 — 07-06 실서버 배포에서 기입) | 장시간 구동 시 메모리 증가 가능성 관찰 대상(아래 주의 참조). 로컬 참고값(07-03 실측) RSS 약 19MiB |

세 컨테이너 limit 합계는 550M + 300M + 50M = **900M**이다. t3.micro 공칭 RAM(1024MB)에서 이를 빼면
약 **124M**이 OS·dockerd·sshd 몫으로 남는다 — 실제 가용 RAM이 약 950MB로 더 타이트하다는 관측
(`deploy/compose.prod.yml` 주석)까지 고려하면 이 여유가 넉넉하지 않다는 뜻이고, 그래서 스왑 2GB가
전제 조건이다(선택이 아니다).

**JVM 배분과 재조정 기준(D-07/D-173):** `-XX:MaxRAMPercentage=60`은 시작값이다. 힙 330M·비힙 여유
220M이라는 계산은 Boot+Hibernate 애플리케이션의 일반적인 비힙 사용량(150~250M) 범위 안에서 나온
추정이지 확정 수치가 아니다. 07-06 실서버 배포에서 `docker stats`로 RSS를 실측해 이 비율을 최종
확정한다 — 필요하면 `JAVA_TOOL_OPTIONS`만 바꿔 재배포 없이 조정한다(이미지 재빌드 불필요).

**Postgres 파라미터과 근거:** 300M 예산 안에서 아래로 시작한다(`deploy/compose.prod.yml`).

```
shared_buffers=64MB
work_mem=4MB
maintenance_work_mem=32MB
effective_cache_size=192MB
max_connections=20
```

`max_connections=20`의 근거는 Hikari `maximum-pool-size=5`(D-170) + 배치 `REQUIRES_NEW`가 한 흐름에서
최대 커넥션 2개를 쓰는 패턴 + 운영/점검용 여유분이다. 이 값들은 실제 부하테스트(Phase 10, VERIFY-01/02)
결과로 재조정될 수 있는 출발값이다.

**Caddy 50M에 대한 주의(신뢰도 LOW — 확정된 사실 아님, 관찰 항목):** 커뮤니티 포럼에 "Caddy 컨테이너가
장시간 구동 시 메모리가 시작값보다 크게 증가했다"는 단일 보고가 있다(원인 미확정). 배포 직후뿐 아니라
**몇 시간 뒤에도 `docker stats`를 한 번 더 확인**한다. `restart: unless-stopped`가 OOM kill 이후 자동
재기동을 보장하므로 최악의 경우도 짧은 다운타임으로 끝나지만, 반복 재시작이 관측되면 50M 자체를
재조정 대상으로 삼거나 `caddy:2-alpine` 전환을 검토한다.

## 3. 서버 초기 세팅 (INFRA-06)

빈 Ubuntu 24.04 EC2를 배포 가능한 상태로 만드는 절차다. 스크립트 본문은 `deploy/server-setup.sh`.

**실행:**

```bash
ssh ubuntu@<host> 'bash -s' < deploy/server-setup.sh
```

**스크립트가 하는 일 5가지:**

1. Docker Engine + compose 플러그인 설치(공식 `download.docker.com` apt 저장소, 이미 있으면 스킵)
2. 스왑 2GB 생성 + `/etc/fstab` 등록(재부팅 후에도 유지)
3. 타임존 `Asia/Seoul` 설정
4. `ubuntu` 사용자를 `docker` 그룹에 가입
5. `/opt/gold-wrestling/backups` 생성 + 소유권 `ubuntu:ubuntu` + `.env` 자리(없을 때만 빈 파일 생성)

**재로그인이 필요한 이유:** 리눅스의 그룹 멤버십은 로그인(세션 시작) 시점에 결정된다. 스크립트가
방금 `ubuntu`를 `docker` 그룹에 추가해도 **지금 열려 있는 SSH 세션에는 반영되지 않는다** — `exit` 후
다시 접속해야 `sudo` 없이 `docker`·`docker compose` 명령을 쓸 수 있다.

**서버 디렉토리 구조** — `/opt/gold-wrestling/`에는 아래 4가지만 존재한다. **서버에서 버전관리 명령을
쓰지 않는다(D-18)**:

```
/opt/gold-wrestling/
├── compose.prod.yml   # scp로 전달
├── Caddyfile           # scp로 전달
├── .env                 # 서버에서 직접 작성(스크립트가 빈 파일만 만듦)
└── backups/             # Phase 9(S3 백업) 자리 — 이 phase는 내용을 채우지 않는다
```

**파일 전달 방법:** `compose.prod.yml`·`Caddyfile`은 `scp`로 전달한다(§4 참조). Phase 8이 이 경로를
배포 워크플로로 자동화한다.

**두 번 실행해도 안전하다:** 각 블록이 "이미 되어 있으면 스킵"(`command -v docker`, `swapon --show`,
`/etc/fstab` grep, `.env` 존재 확인)으로 재실행을 안전하게 만든다. 실제 2회 실행 결과 비교는 07-06이
스크립트 종료 요약(`docker --version`·`swapon --show`·`timedatectl`·`ls -la /opt/gold-wrestling` 출력)을
두 번 실행해 비교하는 방식으로 실증한다.

## 4. 수동 배포 절차 (D-04)

07-06이 아래 순서를 **그대로 따라 실행**한다. 각 단계는 실행 가능한 명령 + 기대 결과로 적었고, 끝마다
"실패하면"을 붙였다 — 실패하면 다음 단계로 넘어가지 않는다. 도메인·이메일은 실값을 쓰지 않고
`<your-domain>`·`<your-email>` 플레이스홀더로 표기한다.

1. **사전 확인** — DNS A 레코드가 서버 Elastic IP를 가리키는지, 보안그룹 인바운드 80/443이 열려
   있는지 확인한다.
   *실패하면:* Let's Encrypt HTTP-01 챌린지는 80이 막혀 있으면 인증서 발급 자체가 실패한다 — 다음
   단계로 넘어가지 않는다.

2. **서버 초기 세팅** — `ssh ubuntu@<host> 'bash -s' < deploy/server-setup.sh` 실행 후 종료 요약에서
   `docker`·`swapon --show`·`Time zone: Asia/Seoul`·`/opt/gold-wrestling` 4가지가 모두 보이는지 확인한다.
   완료 후 **재로그인**한다(§3 참조).
   *실패하면:* 스크립트 중간에 `apt-get`이 실패하면(네트워크·저장소 문제) 재실행해도 안전하다(멱등) —
   원인을 해결한 뒤 다시 실행한다.

3. **파일 전달** — `scp deploy/compose.prod.yml deploy/Caddyfile ubuntu@<host>:/opt/gold-wrestling/`
   (서버에 git이 없다 — D-18).
   *실패하면:* `scp` 권한 오류는 대개 `.pem` 키 경로·`ubuntu` 소유권 문제다 — 2단계의 `chown`이
   끝났는지 재확인한다.

4. **서버 `.env` 작성** — §1 표를 보고 `/opt/gold-wrestling/.env`를 서버에서 직접 채운다. 운영 전용
   값(`DOMAIN`, `SWAGGER_ENABLED=false`, `DB_HOST=postgres`, `KAKAO_REDIRECT_URI`,
   `CORS_ALLOWED_ORIGINS`)을 명시적으로 넣는다. `chmod 600 /opt/gold-wrestling/.env` 권장(소유자 외
   읽기 차단).
   *실패하면:* 필수 키(`DOMAIN`·`ACME_EMAIL`) 누락은 6단계의 `up`이 즉시 거부한다(D-177) — 이 자체가
   "빠뜨린 키가 있다"는 신호다.

5. **관리자 시드(최초 1회)** — `ADMIN_SEED_LOGIN_ID`·`ADMIN_SEED_PASSWORD`·`ADMIN_SEED_NAME`을 채운
   채로 1회 기동 → 그 계정으로 로그인 성공 확인 → **`.env`에서 세 값을 다시 비운다**. D-038이 멱등
   생성이라 두 번째 기동에서 시드 로직 자체는 아무 일도 하지 않지만, 목적은 관리자 비밀번호를 서버
   파일에 상주시키지 않는 것이다 — 비운 뒤에도 재기동은 안전하다.
   *실패하면:* 로그인 실패 시 세 값을 비우지 말고 오탈자부터 확인한다 — 값을 비운 뒤 재기동하면
   D-038 멱등 생성이 이미 만든 계정이 있어도 다시 만들지 않는다(비밀번호 재발급 불가, 삭제 후
   재생성 필요).

6. **이미지 pull + 기동** — `cd /opt/gold-wrestling && docker compose -f compose.prod.yml pull &&
   docker compose -f compose.prod.yml up -d`. GHCR 이미지가 public이라 `docker login`이 필요 없다
   (D-03/D-175).
   *실패하면:* `up`이 `DOMAIN 필수` 메시지와 함께 즉시 종료되면 4단계로 돌아가 `.env`를 다시 확인한다.

7. **Flyway 적용 확인** — `docker compose -f compose.prod.yml logs app | grep -i flyway`로 `V1`부터
   최신 버전(`V12`)까지 `Successfully applied`가 순서대로 보이는지 확인한다.
   *실패하면:* 마이그레이션 실패 로그가 보이면 앱이 기동을 거부한 상태다 — DB 접속 정보(`DB_*`)부터
   재확인한다.

8. **health·HTTPS 확인** —
   - `curl -I http://<domain>` → `30x`(80→443 리다이렉트)
   - `curl -s https://<domain>/actuator/health` → `{"status":"UP"}`
   - `curl -o /dev/null -w '%{http_code}' https://<domain>/actuator/info` → `404`(Caddy 차단, D-20)
   *실패하면:* `curl: (60) SSL certificate problem`은 대개 인증서 발급이 아직 끝나지 않은 것이다 —
   `docker compose logs caddy`에서 `certificate obtained`를 기다린 뒤 재시도한다.

9. **인증서 영속 확인** — `docker compose -f compose.prod.yml restart caddy` 후 Caddy 로그에
   `obtaining`·`certificate obtained` 같은 **새 발급 로그가 없어야** 한다(named volume `caddy_data`가
   기존 인증서를 보존).
   *실패하면:* 재시작마다 새로 발급되면 `caddy_data` 볼륨이 실제로 마운트됐는지
   (`docker volume ls`·`docker inspect gw-prod-caddy`) 확인한다.

10. **메모리 실측** — `docker stats --no-stream`으로 세 컨테이너 RSS를 §2 예산표의 "실측 RSS"·"여유"
    열에 기입한다. `dmesg | grep -i oom`에 아무 것도 나오지 않아야 한다. **몇 시간 뒤 Caddy를 한 번 더
    확인**한다(§2 Caddy 주의 참조).
    *실패하면:* `dmesg`에 OOM kill 로그가 있으면 해당 컨테이너의 limit을 §2 표 기준으로 재검토한다.

11. **롤백** — 이전 이미지로 되돌리려면 `.env`의 `APP_IMAGE`를 이전 태그로 바꾼 뒤
    `docker compose -f compose.prod.yml up -d`. **Phase 8이 커밋 SHA 태그를 붙이기 전인 현재는
    `latest` 태그뿐이라, 정확한 이전 버전으로 되돌릴 방법이 아직 없다** — 이 한계는 Phase 8이
    해소한다.
    *실패하면:* 되돌릴 이전 태그가 없으면 GHCR 웹 콘솔에서 사용 가능한 다이제스트를 확인해
    `APP_IMAGE=ghcr.io/minsu-zip/gold-wrestling-be@sha256:<다이제스트>` 형태로 직접 지정한다.
