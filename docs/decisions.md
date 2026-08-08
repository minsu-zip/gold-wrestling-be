# 의사결정 기록 (decisions.md)

> 설계·기술 결정을 할 때마다 아래 형식으로 추가한다. 포트폴리오·면접 대비 원천 자료.
> 형식: 번호. 제목 / 날짜 / 결정 / 이유 / 대안과 기각 사유

## D-001. 차감 시점: 예약 즉시 차감

- 2026-07 / **예약 성공 시 즉시 차감, 취소 시 즉시 복구**
- 이유: 회원이 보는 잔여 횟수가 항상 실제와 일치해야 함. 당일 취소 불가 정책 덕분에 노쇼 처리도 자동 해결
- 기각 대안: 수업 종료 후 배치 차감 — 잔여 횟수 표시가 실제와 어긋나 회원 혼란 유발

## D-002. 배치의 역할 분리

- 2026-07 / 예약 관련 차감 = 실시간 트랜잭션, 정책성 차감(2주 미사용, 유효기간 만료) = 매일 새벽 멱등 배치

## D-003. 레포 구조: 2개 멀티레포

- 2026-07 / `gold-wrestling-be`(docs, docker-compose 포함) + `gold-wrestling-fe`
- 이유: 1인 개발에서 4개 레포는 관리 오버헤드. docs는 BE 내 폴더로 충분 (openapi.yaml 생성 주체가 BE)
- 기각 대안: docs/infra 별도 레포(과함), 모노레포(레포별 독립 CI/CD 학습 목표와 상충)

## D-004. 프론트: React CSR (Vite)

- 2026-07 / 로그인 기반 서비스라 SEO 불필요. SSR(Next)은 인프라 복잡도만 추가 — 학습 초점(BE/인프라)과 상충

## D-005. JDK 21

- 2026-07 / 강의(김영한) 및 생태계 호환, 국내 실무 표준. JDK 25는 라이브러리 지원 성숙 후 업그레이드 경험 삼아 고려

## D-006. 인프라: AWS 통일

- 2026-07 / EC2(BE+DB, Docker) + S3/CloudFront(FE) + GitHub Actions. 프리티어 12개월 후 Lightsail/Oracle 재검토
- 기각 대안: Oracle Always Free(더 좋은 무료 사양이나 낯선 플랫폼 학습 비용), Cloudflare Pages(간편하나 AWS 학습 목표와 상충)

## D-007. 관리자 실시간 인지: 인앱 알림센터 + 주간 보드

- 2026-07 / 폴링(30초)으로 시작 → SSE 업그레이드. 푸시(FCM/PWA)는 v2
- 주간 보드는 라이브러리 대신 커스텀 그리드 (고정 슬롯 구조라 범용 캘린더가 과함)

## D-008. FE 패키지 매니저: pnpm

- 2026-07 / **pnpm 고정 (npm/yarn 금지)**
- 이유: 설치 속도, 디스크 효율(전역 저장+심링크), 엄격한 node_modules로 유령 의존성 차단 — 선언 안 한 패키지 import 시 즉시 에러가 나서 AI 작업 시 의존성 위생이 강제됨
- 기각 대안: npm(느림, 유령 의존성 허용), yarn classic(레거시), yarn berry(PnP 호환 마찰)
- 후속: GitHub Actions 워크플로우 작성 시(M7) pnpm/action-setup + setup-node cache: 'pnpm' 적용 필요

## D-009. FE TypeScript 버전: 6.0 고정 (7.0 보류)

- 2026-07 / **TypeScript 6.0.x 고정**. 7.0(Go 네이티브 포팅)은 생태계 성숙 후 재검토
- 이유: 7.0은 2026-07 출시로 아직 3주차. 우리가 쓰는 도구가 지원하지 않는다 —
  typescript-eslint는 peer가 `<6.1.0`, openapi-typescript는 `^5.x`.
  린팅(D-010)과 API 타입 생성(D-013)은 둘 다 이 프로젝트의 필수 경로라 여기서 막히면 안 된다
- 참고: openapi-typescript는 peer 범위만 낡았을 뿐 TS 6에서 생성이 정상 동작함을 확인.
  pnpm.peerDependencyRules로 경고만 억제했다
- 기각 대안: TS 7 선행 도입(툴체인 절반이 동작 불가), TS 5.9 유지(불필요하게 2세대 뒤처짐)

## D-010. FE 린터: ESLint + typescript-eslint

- 2026-07 / **ESLint 10 + typescript-eslint 8 (타입 정보 기반 규칙 활성화)**
- 이유: 예약·취소가 전부 비동기라 `no-floating-promises`(await 누락),
  `no-misused-promises` 같은 **타입 정보 없이는 잡을 수 없는** 규칙이 중요하다.
  실제로 전환 직후 `import.meta.env` 접근이 any로 새는 것과 E2E의 DOM 타입 누락을 잡아냈다
- 기각 대안: oxlint (현 Vite 공식 템플릿 기본값, 50~100배 빠름).
  타입 정보를 쓰지 않아 위 부류를 못 잡고, 린트가 몇 초 빨라지는 것보다
  런타임 버그 하나 막는 게 낫다고 판단
- 유의: typescript-eslint peer가 `<6.1.0`이라 TS 6.1/7 업그레이드 시
  typescript-eslint 지원을 기다려야 한다 (D-009와 함께 봐야 함)

## D-011. FE 폰트: Pretendard Variable, 동적 서브셋 self-host

- 2026-07 / **Pretendard Variable 단일 패밀리로 통일**, npm `pretendard` 패키지로 self-host
- 이유: 한글·영문을 한 패밀리로 통일. 가변 폰트라 `@font-face` 한 선언
  (`font-weight: 45 920`)으로 Thin~Black 전 굵기를 커버해 굵기별 파일 선언이 필요 없다.
  동적 서브셋 버전은 unicode-range로 92개 조각으로 나뉘어 실제 쓰인 글자 범위만 내려받는다
- 기각 대안:
  - Geist(shadcn 프리셋 기본) — 한글 글리프가 없어 한글이 시스템 폰트로 떨어진다
  - `@fontsource/pretendard` — 서브셋 없이 굵기당 전체 한글 폰트 1개라 4굵기면 수 MB
  - 전체 variable woff2 한 덩어리(2MB) — 첫 로딩에 전부 받는다
  - CDN 링크 — S3/CloudFront 배포와 외부 의존을 섞고 싶지 않다

## D-012. FE dev 서버 포트 5180 고정

- 2026-07 / **dev 5180 / preview 5181, `strictPort: true`**
- 이유: Vite 기본 5173은 다른 프로젝트와 충돌하기 쉽다. 기본 동작은 점유 시 조용히
  다음 포트로 옮겨가는데, 이때 Playwright가 `reuseExistingServer`로 5173의
  **엉뚱한 서버**를 붙잡아 E2E가 통째로 실패한다 (실제로 겪음).
  strictPort로 조용한 이동 대신 즉시 실패시킨다

## D-013. 생성된 API 타입은 커밋한다

- 2026-07 / `pnpm api:types`로 만든 `src/api/schema.d.ts`를 커밋 대상에 포함
- 이유: FE 빌드/CI가 BE 레포 체크아웃 없이 독립적으로 돌아야 한다 (멀티레포 구조, D-003)
- 기각 대안: gitignore 후 빌드 시 생성 — CI에서 BE 레포를 함께 체크아웃해야 해 결합도가 올라간다

## D-014. BE 프레임워크: Spring Boot 4.1.x 채택

- 2026-07 / **Spring Boot 4.1.0 (Spring Framework 7)**, JDK 21 유지
- 이유: Boot 3.5의 OSS 지원이 **2026-06-30 종료**되어(이후 보안 패치는 상용 구독) 신규·실서비스 프로젝트에 부적합.
  4.0은 2026-12-31 지원 종료라 5개월 뒤 재업그레이드가 필요하므로 **4.1**(2027-07-31까지 지원) 선택.
  Java baseline이 4.x에서도 17이라 JDK 21 결정(D-005)에 영향 없음
- 기각 대안: 3.5.16(보안 패치 종료), 4.0(단기간 내 재업그레이드 필요)
- 유의: 학습 자료(강의)가 3.x 기준이므로 설정 키·패키지·의존성 차이는 **4.x 공식 문서 기준으로 해소**한다.
  실제 확인된 차이 — 스타터명 `spring-boot-starter-web`→`-webmvc`, Flyway/모듈별 `-test` 스타터 분리,
  Jackson 3(`tools.jackson`), Testcontainers 2.x(`testcontainers-postgresql`),
  `@AutoConfigureMockMvc`→`org.springframework.boot.webmvc.test.autoconfigure`, springdoc은 3.x 라인
  (`spring.datasource`/`spring.jpa`/`spring.flyway`/`spring.jackson` 설정 키는 3.x와 동일함을 메타데이터로 확인)

## D-015. 로컬 시크릿 주입: application.yml이 .env를 직접 import

- 2026-07 / `spring.config.import: optional:file:.env[.properties]` 로 `.env`를 읽고, 배포는 동일 키를 OS 환경변수로 주입
- 이유: `.env` 하나를 docker-compose와 애플리케이션이 공유해 로컬 셋업이 `cp .env.example .env` 한 번으로 끝난다.
  `optional:`이라 `.env` 없는 배포 환경에서도 그대로 뜬다
- 기각 대안: dotenv 라이브러리 추가(의존성 증가), IDE 실행 구성에 환경변수 등록(팀·CI 재현 불가)
- 유의: `.env`는 properties 포맷으로 파싱된다 — 값에 `#`(주석), `\`(이스케이프) 사용 금지, 따옴표로 감싸지 말 것

## D-016. 횟수 표현: DECIMAL(4,1) + BigDecimal

- 2026-07 / DB는 `DECIMAL(4,1)`, 코드는 `BigDecimal`. 0.5를 그대로 0.5로 저장한다
- 이유: 0.5 단위가 정책 전반(저녁반 0.5회 차감, "잔여 0.5회로는 1회 예약 불가")에 그대로 노출된다.
  정수 스케일(0.5=1)은 저장은 단순하지만 표시·입력·검증·이력 모든 지점에 ×2 ÷2 변환이 붙어
  변환 누락이 곧 횟수 오류가 된다. 감사 대상 데이터라 DB 값을 눈으로 읽어 검증할 수 있어야 한다
- 기각 대안: 정수 스케일(변환 지점마다 버그 위험), `Double`/`Float`(부동소수 오차 — 잔여 0 판정이 깨진다)
- 유의: `BigDecimal`은 `equals`가 스케일까지 비교해 `0.5 != 0.50` 이다.
  **잔여 횟수 비교는 반드시 `compareTo`** 를 쓴다 (conventions.md에 규약으로 명시)

## D-017. 에러 응답: RFC 9457 ProblemDetail

- 2026-07 / Spring 내장 `ProblemDetail`(`application/problem+json`)을 전역 예외 핸들러에서 반환.
  도메인 에러는 `type`에 에러코드 URI, 부가 정보는 `properties`에 담는다
- 이유: FE가 `openapi.yaml`로 타입을 생성한다(D-013). 표준 스키마라 생성기가 그대로 처리한다.
  직접 만든 `ApiResponse<T>` 래퍼는 모든 성공 응답까지 한 겹 감싸서 FE가 매번 벗겨야 하고,
  스프링이 내부적으로 발생시키는 에러(400/404/405)와 우리 에러의 형태가 갈라진다
- 기각 대안: 커스텀 `ApiResponse<T>` 공통 래퍼(비표준·FE 부담), 예외별 즉석 응답(형태 불일치)

## D-018. 패키지 구조: 기능별 패키지

- 2026-07 / `com.goldwrestling.<기능>` 아래에 계층을 둔다 (`member`, `pass`, `reservation`, `schedule`, `notice`, `admin`)
- 이유: 예약이 이용권 차감·정원·휴강과 얽혀 있어 도메인 경계를 눈에 보이게 유지해야 한다.
  계층별 최상위 구조(`controller/`, `service/`)는 한 기능을 수정할 때 파일이 여러 폴더로 흩어진다
- 기각 대안: 계층별 패키지(전통적이지만 응집도 낮음), 완전한 헥사고날/멀티모듈(1인 MVP에 과함)

## D-019. DTO 경계: 엔티티를 컨트롤러 밖으로 내보내지 않는다

- 2026-07 / 요청·응답 전용 DTO를 기능 패키지의 `dto`에 두고, 엔티티는 서비스 계층 안에서만 다룬다
- 이유: `openapi.yaml`이 FE와의 유일한 계약이다. 엔티티를 그대로 반환하면 필드 추가가 곧 API 변경이 되고,
  지연 로딩 프로퍼티가 직렬화 시점에 터진다(open-in-view=false와 함께 보면 명확하다)
- 기각 대안: 엔티티 직접 반환(계약 오염), `Map<String, Any>` 반환(스펙 생성 불가)

## D-020. 트랜잭션 경계: 서비스 메서드 = 트랜잭션 단위

- 2026-07 / 서비스 클래스에 `@Transactional(readOnly = true)` 기본, 변경 메서드에만 `@Transactional` 오버라이드.
  컨트롤러·리포지토리에는 `@Transactional`을 붙이지 않는다
- 이유: 차감과 예약 생성은 한 트랜잭션에서 원자적으로 끝나야 한다(D-001 즉시 차감).
  경계가 컨트롤러로 올라가면 외부 호출·응답 직렬화가 트랜잭션 안에 들어와 커넥션을 오래 잡는다
- 기각 대안: 컨트롤러 트랜잭션(경계 과다 확장), 리포지토리 트랜잭션(여러 저장이 원자성을 잃음)

## D-021. 동시성: DB 제약 + 조건부 갱신 우선, 락은 필요한 곳에만

- 2026-07 / 1:1 레슨은 (session_id) 유니크 제약, 예약제 정원은 조건부 갱신/삽입으로 먼저 막고,
  그것으로 부족한 지점에만 비관적 락(`SELECT ... FOR UPDATE`)을 쓴다. 각 지점은 동시성 테스트로 검증
- 이유: 초과 예약 0건은 애플리케이션 조건문으로 보장할 수 없다(조회-판단-저장 사이에 다른 트랜잭션이 끼어든다).
  DB 제약은 코드 경로가 몇 개든 마지막 방어선이 된다
- 기각 대안: 낙관적 락 `@Version`(충돌 시 재시도 로직이 필요, 마지막 자리 경쟁에서 재시도 폭증),
  애플리케이션 레벨 동기화(다중 인스턴스에서 무효)
- 유의: 정원 방식은 예약 phase에서 실측 비교 후 확정하고 이 항목을 갱신한다

## D-022. FE 재사용 규약: 만들기 전에 찾고, 확장은 하위호환으로

- 2026-07 / **탐색 → 재사용 → 확장** 순서를 규약화. 확장 시 기존 시그니처·동작을 바꾸지 않는다
- 이유: AI가 페이즈마다 작업하면 같은 일을 하는 함수·컴포넌트가 이름만 다르게 계속 생긴다.
  더 위험한 건 기존 것을 "개선"하다 이미 쓰고 있는 화면을 조용히 깨뜨리는 것 —
  타입 체크가 못 잡는 변경(문자열 출력, 렌더 결과)일수록 그렇다
- 구체: 새 인자·prop은 optional + 기본값(기본값일 때 동작은 기존과 동일).
  동작 자체를 바꿔야 하면 새로 만든다. 정말 바꿔야 하면
  기존 동작을 테스트로 고정 → 호출부 전수 검색 → 한 커밋에서 함께 수정
- 컴포넌트 안에 헬퍼 함수를 두지 않는다 — 판정은 `features/*/rules.ts`,
  표시 포맷은 `features/*/format.ts`, 도메인 무관 유틸은 `src/lib/`.
  그래야 Vitest로 검증할 수 있다
- 승격은 **두 번째 사용처가 생겼을 때**. 미리 공용화하면 맞는 인터페이스를 알 수 없다
- 문서: `gold-wrestling-fe/.claude/skills/fe-architecture/rules/reuse.md`

## D-023. FE 검증 규약: 변경에는 검증이 따라온다 + Storybook 도입

- 2026-07 / **순수 함수 → Vitest, 컴포넌트·기능·페이지 → Storybook, 주요 플로우 → Playwright**를
  같은 커밋에 함께 넣는다
- 이유: 나중에 로직을 고치거나 확장할 때 무엇이 깨졌는지 판단할 근거가 필요하다.
  특히 정책 변경(policies.md)이 잦을 도메인이라 경계값 테스트가 회귀 방어선이 된다
- **Storybook 채택 (10.5.x)**: BE API가 아직 없고, 정원 마감·휴강·잔여 0.5회 같은 상태는
  실제 데이터로 재현하기 번거롭다. 스토리북이 그 상태들을 고정해두는 곳이 된다.
  `Default` 하나로 끝내지 않고 `Empty`/`Loading`/`Error` + 도메인 상태를 각각 만든다
- `@storybook/addon-vitest`를 함께 쓴다 — **스토리가 곧 렌더 회귀 테스트**가 되어
  `pnpm test`가 모든 스토리를 검증한다. 스토리 작성 비용이 테스트 비용을 겸한다
- 호환 확인: Storybook 10.5.5가 Vite 8 / React 19 / TS 6을 모두 지원 (peer 확인)
- 기각 대안: 스토리북 없이 E2E만 — 데이터 없는 상태·에러 상태를 재현하려면
  목 서버가 필요해 비용이 더 크다
- 설치 시점: 첫 컴포넌트 페이즈 (환경 세팅 단계에서는 미설치)
- 문서: `gold-wrestling-fe/.claude/skills/fe-architecture/rules/testing.md`

## D-024. BE 코틀린 포맷터: ktlint (스타일 `ktlint_official`)

- 2026-07 / **ktlint-gradle 14.2.0 + ktlint 1.8.0**. 스타일은 `.editorconfig` 의 `ktlint_code_style = ktlint_official`.
  `ktlintCheck` 는 플러그인 기본대로 `check` 에 묶여 `./gradlew build` 가 포맷 위반 시 실패한다
- 이유: phase 가 독립 컨텍스트에서 실행되므로 "스타일을 통일하라"를 문서에 적어도 지켜지지 않는다.
  코드가 5개 파일인 지금 넣어야 나중에 "전 파일 포맷 커밋"이 `git blame` 을 오염시키는 일을 피한다.
  FE 의 Prettier 자리 — 코드 품질이 아니라 모양만 담당한다 (D-010 의 ESLint 는 detekt 에 대응)
- 스타일 선택 근거: 실제 코드에 두 스타일을 돌려 비교 → `ktlint_official` 은 **변경 0줄**,
  `intellij_idea` 는 69줄 변경. 또 official 은 생성자·함수 파라미터를 한 줄씩 + 끝쉼표로 두어
  **파라미터 추가 시 diff 가 "1줄 추가"** 로 끝난다 (한 줄로 몰면 그 줄 전체가 수정으로 잡힌다)
- 기각 대안:
  - `intellij_idea` 스타일 — 기존 코드 69줄 즉시 재작성 + 끝쉼표 제거로 이후 diff 가 더러워진다
  - **detekt 는 지금 도입하지 않는다** — 코드 냄새(함수 길이·복잡도) 검사는 규칙 튜닝·오탐 억제 비용이 크고,
    FE 에서 ESLint 가 잡던 것 중 상당수(널 접근, 타입 누출, 미정의 참조)는 **코틀린 컴파일러가 이미 막는다**.
    코드가 쌓여 "이 서비스가 너무 커졌다"는 판단이 생기면 그때 재검토
  - spotless — 여러 언어를 한 번에 다루는 대신 코틀린 전용 규칙 제어가 ktlint 보다 간접적
- 유의: 스타일 규칙의 단일 출처는 `.editorconfig` 다. `build.gradle.kts` 에 규칙을 중복 정의하지 않는다.
  `ij_kotlin_allow_trailing_comma*` 를 켠 것은 IntelliJ 자동 포맷 결과를 ktlint 와 일치시키기 위한 것

## D-025. 회원 실명·전화번호: 최초 로그인 온보딩에서 직접 입력

- 2026-07 / **카카오 로그인은 인증 수단으로만 사용**하고, 최초 로그인 시 온보딩 화면에서
  실명·전화번호를 필수 입력받는다 (전화번호 형식 검증). 온보딩 미완료는 별도 상태가 아니라
  `PENDING` 상태에서 프로필 입력 여부로 판정하고, 승인 목록에는 온보딩 완료 회원만 노출한다
- 이유: 카카오 기본 동의항목에는 실명·전화번호가 없다 (비즈앱 전환 + 동의항목 심사 필요).
  관리자는 오프라인에서 회원을 이름·전화번호로 알고 있으므로 이 두 값이 있어야 승인·운영이 가능하다
- 기각 대안:
  - 관리자 사전 등록 후 매칭 — 등록 전에 로그인한 회원의 처리가 애매하고 관리자 수작업이 늘어난다
  - 비즈앱 심사 선행 — MVP 일정이 심사에 묶이고 통과가 불확실하다.
    심사 통과 후 자동 수집·온보딩 폼 자동 채움은 v2 후보로 유지 (KAKAO-01)

## D-026. 관리자 인증: ID/PW 로그인 + 회원과 동일한 JWT 체계

- 2026-07 / 관리자는 카카오 연동 없이 **ID/PW 로그인**, 토큰은 회원과 **동일한 JWT 발급 체계**를
  사용한다. 관리자 계정은 시드 데이터로 생성한다 (셀프 가입 없음)
- 이유: 관리자는 지점당 소수 고정 인원이라 OAuth 온보딩이 과하다.
  JWT 체계를 공유하면 시큐리티 필터체인·인가 코드가 하나로 유지된다
- 기각 대안: 개인 카카오 계정에 ADMIN 역할 부여(개인 계정과 지점 운영 계정이 결합되고
  회원 승인 흐름과 얽힘), 세션 기반 별도 인증(무상태 API 원칙·STATELESS 설정과 상충)

## D-027. 2주 미사용 차감 기준일: max(마지막 출석일, 마지막 예약 수업일)

- 2026-07 / 2주 미사용 자동 차감(policies §4.3)의 기준일을 **마지막 출석일과 마지막 예약의
  수업일 중 더 최근 날짜**로 정한다. 둘 다 없으면 등록일
- 이유: 출석은 관리자가 수동으로 남기는 참고 데이터라 체크 누락이 생길 수 있는데,
  출석일 단독 기준이면 누락이 곧 부당 차감으로 이어진다. 예약은 예약 시점에 이미 차감된
  '사용'이므로, 예약해 놓고 미사용으로 간주해 또 차감하면 이중 불이익이자 정책 모순이다
- 기각 대안: 마지막 출석일 단독 기준(체크 누락 = 부당 차감), 마지막 예약일(예약 행위일) 기준
  (수업일이 아닌 조작 시점 기준이 되어 "실제로 체육관에 온 날"과 어긋난다)

## D-028. 에러 응답에 커스텀 code 필드 + 에러코드 레지스트리

- 2026-07 / `ProblemDetail` 표준 필드에 문자열 enum `code`를 `properties`로 추가하고 **FE 분기는 `code`로만** 한다.
  `type` URI는 형식만 갖춘 값이며 분기 키가 아니다. 에러코드 목록은 `docs/error-codes.md`를 계약 문서로 삼고,
  코드를 추가하면 같은 PR에서 그 표를 갱신한다
- 이유: 내장 예외(400/404/405/415)와 도메인 예외의 응답 모양이 갈리면 FE가 에러마다 다른 분기를 짜게 되므로,
  `ResponseEntityExceptionHandler`를 상속해 `handleExceptionInternal` 한 지점에서 `code`를 주입해 진입점을 하나로 모았다
- 기각 대안: 자동 등록되는 `ProblemDetailsExceptionHandler`를 그대로 두고 도메인 예외만 별도 `@ExceptionHandler`로
  처리(응답에 `code` 유무가 섞임), `type` URI를 분기 키로 사용(FE가 URI 문자열을 파싱해야 함),
  `openapi.yaml`에 에러 스키마를 전 엔드포인트에 수동 주석(반복 비용이 큼)

## D-029. openapi.yaml 재생성: 커스텀 Gradle 태스크 체인 (springdoc gradle 플러그인 미사용)

- 2026-07 / `springdoc-openapi-gradle-plugin` 대신 커스텀 Gradle 태스크 체인(`generateApiDocs`)으로
  `docs/api/openapi.yaml`을 재생성한다. 로컬 `docker compose` Postgres 기동을 전제한다
- 이유: 플러그인 최신 1.9.0이 2024-06 이후 릴리스가 없고, 최신 Boot Gradle 플러그인과의
  `BootRun_Decorated` 캐스트 충돌·configuration cache 비호환 이슈가 미해결이라 Boot 4.1에서
  실패 위험이 크다. 대신 앱을 백그라운드로 기동(8099) → `/actuator/health` 폴링 →
  `/v3/api-docs.yaml` 다운로드 → 프로세스 정리를 `Exec` 태스크 5개(`dependsOn`/`finalizedBy`)로
  직접 구현해 동일 효과를 낸다
- 기각 대안: 플러그인 채택(위 이유로 위험), 수동 `bootRun` + `curl`(사람이 한 번만 빠뜨려도
  커밋된 계약과 실제 API가 조용히 어긋난다), `openapi.yaml` 수작업 편집(필연적 드리프트)
- 이연: CI에서 재생성 결과를 코드와 대조하는 검증은 배포 단계(M7)로 미룬다 — 이번 스코프는
  로컬 실행 보장까지다
- 트레이드오프: 재생성이 앱 기동을 포함해 최대 1분가량 걸린다 — 매번 실행되게 만들어 계약
  드리프트를 막는 대신 속도를 포기했다(계약 파일을 항상 최신으로 유지하는 정확성이 우선)

## D-030. 초기 스키마: 최소 스키마 원칙 (Phase 1)

- 2026-07 / Phase 1 스키마(V2)는 **확실한 정체성 컬럼만** 만든다. 카카오 식별자·관리자 로그인
  자격(ID/PW)·역할 컬럼 등 인증 관련 컬럼은 인증 설계가 확정되는 Phase 2에서 V3 이후 마이그레이션으로 추가한다
- 이유: 커밋된 마이그레이션은 수정 금지(conventions §9)라, 확정되지 않은 설계를 추측으로 굳히면
  잘못된 컬럼이 스키마 이력에 영구히 남는다. 컬럼 추가는 싸고 제거·변경은 비싸다
- 기각 대안: 인증 컬럼 선반영(추측 설계가 이력에 고정됨), 전부 nullable 로 미리 파두기(무의미한 널 컬럼이 계약을 흐림)
- 유의: `V2__create_branch_member_admin.sql` 주석의 "D-04"는 phase 1 계획 문서
  (`.planning/phases/01-foundation/01-CONTEXT.md`)의 로컬 ID로, 이 항목을 가리킨다
  (커밋된 마이그레이션은 수정 금지라 표기를 그대로 남겨 둔다)

## D-031. Branch 시드: Flyway 시드 마이그레이션으로 주입

- 2026-07 / 송파점 1건을 V2 마이그레이션의 `INSERT`로 주입한다. 지점 관리 API는 v1 스코프 밖이라
  마이그레이션이 유일한 데이터 주입 경로다. `id`는 명시하지 않는다(identity 시퀀스 어긋남 방지)
- 이유: Testcontainers가 빈 DB에서 마이그레이션을 전부 재생하므로 로컬·테스트·운영이 같은 시드를 보장받는다
- 기각 대안: 앱 기동 시 시드(`ApplicationRunner` — 환경·중복 실행 조건 분기가 필요),
  `data.sql`(Flyway와 주입 경로가 이원화되어 실행 순서 보장이 어긋남)
- 유의: `V2__create_branch_member_admin.sql` 주석의 "D-09"는 phase 1 계획 문서의 로컬 ID로, 이 항목을 가리킨다

## D-032. 카카오 OAuth: 인가 코드 방식, 토큰 교환은 BE가 수행

- 2026-08 / FE는 카카오 리다이렉트와 **인가 코드 전달만** 담당하고, BE가 인가 코드로 카카오 토큰
  교환·사용자 정보 조회를 수행한 뒤 **자체 JWT를 발급**한다. `client_secret`은 서버 환경변수에만
  존재한다 (`.env.example`에 키 이름 동기화)
- 이유: 시크릿이 브라우저에 노출되지 않고, 카카오 API 의존이 BE 한 곳에 모여 FE와의 계약
  (openapi.yaml)이 "인가 코드 주면 JWT 준다"로 단순해진다
- 기각 대안: FE가 SDK로 카카오 액세스 토큰을 받아 BE에 전달(BE가 토큰 진위를 별도 검증해야 하고
  시크릿 보호 이점이 사라짐), Spring Security OAuth2 Client 리다이렉트 위임(세션 기반 흐름이라
  STATELESS JWT 체계(D-026)와 상충)

## D-033. JWT 토큰 정책: access 30분 / refresh 14일, DB 저장 + 회전

- 2026-08 / access 토큰 30분, refresh 토큰 14일. refresh는 **DB에 저장**하고 **사용할 때마다
  회전(rotation)**한다. 로그아웃 = refresh 삭제. 회원 상태가 `ACTIVE`가 아니게 되면 refresh를
  무효화해 **강제 로그아웃이 가능**해야 한다
- 이유: refresh를 DB에 저장해야 강제 만료·회전·로그아웃이 가능하다. 무상태 refresh는 탈취 시
  14일간 차단 수단이 없다
- 노트: refresh 무효화만으로는 기발급 access가 최대 30분 유효한 창이 남는다. 따라서 상태 게이트
  인가(AUTH-04, `PENDING` 접근 제한 등)는 토큰 클레임이 아니라 **DB의 현재 상태 기준**으로 검사한다
- 기각 대안: 무상태 refresh(강제 만료 불가), 세션 기반(무상태 API 원칙과 상충),
  access 수명 단축으로 창 제거(갱신 트래픽 증가 대비 이득이 작고 DB 상태 검사가 더 정확)

## D-034. 가입 거절: 별도 상태 없이 INACTIVE 전환 + 거절 사유 기록

- 2026-08 / 거절 시 `REJECTED` 상태를 추가하지 않고 **`INACTIVE` 전환 + 거절 사유 기록**으로
  처리한다. 거절된 회원 재로그인 시 거절 안내 화면 대상으로 식별된다. 재신청은 관리자가 상태를
  `PENDING`으로 되돌리는 운영 방식, 승인 취소도 기존 상태 변경 기능으로 갈음한다 (policies §5.2)
- 이유: 회원 상태 4종(policies §5)을 유지해 모든 상태 분기 코드가 단순하게 남는다. 거절 사유
  기록이 있으면 `INACTIVE`의 다른 유래(탈퇴·장기 미이용)와 구분할 수 있다
- 기각 대안: `REJECTED` 상태 추가(상태 기계가 5종으로 늘어 전 코드의 분기 복잡화),
  거절 시 데이터 삭제(재로그인 시 신규 가입으로 오인되고 거절 이력 소실)

## D-035. 관리자 회원 목록 API: page/size + 통합 검색 + 상태 필터, 승인 대기 목록 재사용

- 2026-08 / 회원 목록은 **page/size 페이지네이션**, **검색어 하나로 이름·전화번호 부분 일치**,
  **상태 필터**를 제공한다. 승인 대기 목록(MEMBER-01)은 전용 API 없이 동일 API에
  `status=PENDING` + 온보딩 완료 필터 조합으로 재사용한다
- 이유: 화면별 전용 API를 늘리지 않고 하나로 운영한다. 온보딩 완료 필터가 있어야 승인 목록
  정책(policies §5.1 — 프로필 입력된 `PENDING`만 노출)이 지켜진다
- 기각 대안: 전체 반환 후 FE 필터(회원 수 증가 시 계약 변경 필요), 승인 대기 전용 API(목록 로직 중복)

## D-036. refresh 토큰 표현·저장: 랜덤 문자열 + SHA-256 해시 저장, 사용 시 회전

- 2026-08 / refresh 토큰은 JWT가 아니라 256비트 난수를 Base64URL로 인코딩한 문자열이고, DB에는 원문이 아니라 SHA-256 hex(64자)를 `token_hash`에 저장한다. 사용할 때마다 새 토큰을 발급하고 기존 행은 `revoked_at`을 채워 폐기한다. 이미 폐기된 토큰이 다시 제시되면 재사용으로 보고 해당 주체의 모든 refresh를 폐기한다. 회원당 여러 refresh 행을 허용해 멀티 디바이스 로그인을 지원한다.
- 이유: refresh는 DB 조회가 전제라 자기기술적 JWT일 이유가 없다. 원문 저장은 DB 유출이 곧 계정 탈취다. 비밀번호와 달리 이미 고엔트로피라 BCrypt 같은 느린 해시가 필요 없다.
- 기각 대안: refresh도 JWT(폐기·회전 추적을 위해 어차피 DB 행이 필요해 이점이 없음), 원문 저장(유출 즉시 탈취), 회원당 1개만 허용(모바일·PC 동시 로그인이 서로를 로그아웃시킴).

## D-037. refresh_token의 주체 참조: nullable FK 쌍 + CHECK 제약

- 2026-08 / `refresh_token`은 `member_id`·`admin_id` 두 FK를 모두 nullable로 두고 `CHECK ((member_id IS NULL) <> (admin_id IS NULL))`로 정확히 하나만 채워지도록 강제한다.
- 이유: Member/Admin이 별도 테이블이라 단일 FK로 표현할 수 없는데, `principal_type` 문자열 + `principal_id` 조합은 FK 무결성을 잃는다. 이 레포는 `admin_branch`에서도 FK를 명시해 왔다.
- 기각 대안: `principal_type`+`principal_id` 문자열 조합(참조 무결성 없음 — 삭제된 회원의 토큰 행이 남음), 테이블 2개로 분리(`member_refresh_token`/`admin_refresh_token` — 회전·폐기 로직이 통째로 두 벌이 됨).

## D-038. 관리자 시드: ApplicationRunner 멱등 시드 (Flyway INSERT 금지)

- 2026-08 / 관리자 계정은 `AdminSeeder`(`ApplicationRunner`)가 기동 시 `login_id` 존재 여부를 확인해 없을 때만 INSERT한다. 자격은 `ADMIN_SEED_LOGIN_ID`·`ADMIN_SEED_PASSWORD`·`ADMIN_SEED_NAME` 환경변수로 주입하고, 비어 있으면 시드를 건너뛰고 경고 로그만 남긴다(앱은 정상 기동).
- 이유: 비밀번호는 환경마다 달라야 하는 시크릿이다. Branch 시드(D-031)처럼 Flyway INSERT로 넣으면 플레이스홀더가 비어 있는 채로 한 번 적용되는 순간 깨진 해시가 그 환경 DB에 영구히 박히고, 커밋된 마이그레이션은 수정 금지라 새 UPDATE 마이그레이션이 필요해진다.
- 기각 대안: Flyway 시드 + `spring.flyway.placeholders`(위 이유), 관리자 가입 API(D-026이 셀프 가입 없음으로 확정).

## D-039. 감사 시각(created_at): JPA Auditing 미도입, 애플리케이션이 Clock으로 명시 세팅

- 2026-08 / `@EnableJpaAuditing`/`@CreatedDate`를 도입하지 않는다. `Member`·`Admin`·`RefreshToken`의 `createdAt`은 엔티티 생성 시 서비스가 주입받은 `Clock`으로 채운다. DB의 `DEFAULT now()`는 그대로 두어 방어선으로 남긴다. (Phase 1에서 이월된 결정 — D-030 유의 참조)
- 이유: 감사 시각이 필요한 필드가 아직 소수이고, `Clock` 주입은 이미 프로젝트 규약(conventions §5)이라 테스트에서 시각을 고정하기 쉽다. Auditing은 커스텀 `DateTimeProvider` 빈 배선이 추가로 필요하고 시각 고정 경로가 한 겹 늘어난다.
- 기각 대안: `@CreatedDate` + `dateTimeProviderRef`(배선 추가 대비 이득이 작음), DB DEFAULT만 쓰고 매핑하지 않기(회원 목록에 가입 신청 시각을 못 보여줌).

## D-040. 인가 구현: URL/역할은 authorizeHttpRequests, 상태 게이트는 MemberStateGate

- 2026-08 / 역할 구분(공개 / `ROLE_MEMBER` / `ROLE_ADMIN`)은 `SecurityConfig`의 `authorizeHttpRequests` URL 규칙으로만 표현한다. "회원 상태가 `ACTIVE`여야 한다" 같은 조건은 `MemberStateGate` 컴포넌트가 서비스 계층에서 DB 현재 상태로 검사하고 `DomainException`을 던진다. `@EnableMethodSecurity`/`@PreAuthorize`는 쓰지 않는다.
- 이유: 상태 규칙에는 엔드포인트별 예외가 있다(`GET /api/members/me`와 온보딩 제출은 `PENDING`도 접근 가능해야 한다). 이를 URL 규칙이나 전역 메서드 시큐리티로 표현하면 예외를 또 열어주는 규칙이 겹쳐 오히려 복잡해진다. 서비스 계층 검사는 도메인 예외 경로를 그대로 타서 `ProblemDetail` 형식이 자동으로 통일된다.
- 기각 대안: `@PreAuthorize`에 SpEL로 상태 조건 넣기(예외 엔드포인트마다 규칙 중복, 에러 응답이 시큐리티 경로로 빠져 `code` 주입이 갈라짐), 필터에서 상태까지 차단(거절 안내 화면 조회가 401로 막힘).
- 유의: 서비스 계층 검사는 "빠뜨릴 수 있는" 방식이다. 회원 대상 엔드포인트를 새로 만들 때마다 `MemberStateGate` 호출이 필요한지 확인한다.

## D-041. 전화번호: 하이픈 제거 후 숫자만 저장, UNIQUE 제약 없음

- 2026-08 / 온보딩 요청은 `010-1234-5678`·`01012345678` 두 형태를 모두 받고(형식 검증 정규식 `^01[016789]-?\d{3,4}-?\d{4}$`), 서버가 하이픈을 제거해 숫자만 `member.phone_number`에 저장한다. `phone_number`에 DB UNIQUE 제약은 걸지 않는다.
- 이유: 저장 형식이 섞이면 "전화번호 부분 일치 검색"(D-035)이 입력 형태에 따라 결과가 달라진다. UNIQUE는 policies §5.1이 요구하지 않고, 운영 규모가 작아 중복은 관리자가 승인 단계에서 걸러낼 수 있다.
- 기각 대안: 하이픈 포함 저장(검색 불안정), 입력을 숫자만으로 제한(FE·사용자 입력 관습과 어긋남), UNIQUE 제약(가족 공유폰 등 정당한 중복을 막고, 제거하려면 새 마이그레이션이 필요).
- 유의: 중복 가입 방지가 필요해지면 v2에서 UNIQUE 제약을 새 마이그레이션으로 추가한다 — 컬럼 추가보다 싼 변경이다.

## D-042. 온보딩 재제출 금지

- 2026-08 / 온보딩 제출 API는 회원 상태가 `PENDING`이면서 온보딩 미완료일 때만 허용한다. 이미 완료했으면 `ONBOARDING_ALREADY_COMPLETED`(409)로 거부한다.
- 이유: 이 API를 열어두면 회원이 온보딩 경로로 이름·전화번호를 자유롭게 바꿀 수 있어, "프로필 수정은 관리자만"(MVP, 셀프 수정은 v2 PROF-01)이라는 결정이 우회된다. 이름·전화번호는 관리자가 회원을 식별하는 기준(policies §5.1)이라 임의 변경은 운영 사고다.
- 기각 대안: 멱등하게 덮어쓰기 허용(PROF-01 우회), 완료 후에도 값이 같을 때만 허용(우회 여지는 남고 규칙만 복잡).

## D-043. 거절 사유 노출 범위: 회원에게는 rejected 불리언만

- 2026-08 / 회원 대상 응답(카카오 로그인 응답, `GET /api/members/me`)에는 `rejected: Boolean`만 담고 `rejectionReason` 원문은 담지 않는다. 사유 원문은 관리자 회원 상세 응답에서만 반환한다.
- 이유: policies §5.2가 요구하는 것은 "거절 안내 화면 대상으로 식별"까지다. 사유는 관리자가 내부 운영 메모로 쓸 수 있어(예: "타 회원 신고 이력") 그대로 노출하면 분쟁 소지가 된다.
- 기각 대안: 회원에게 사유 원문 노출(내부 메모 유출), 사유를 아예 기록하지 않음(D-034가 요구하는 `INACTIVE` 유래 구분이 불가능).

## D-044. 상태 변경 시 refresh 무효화 + PENDING 복귀 시 거절 사유 초기화

- 2026-08 / 관리자의 회원 상태 변경·거절로 **전환 후 상태가 `ACTIVE`가 아니게 되면** 해당 회원의 폐기되지 않은 refresh 토큰을 전부 폐기한다. 승인(`PENDING`→`ACTIVE`)에서는 폐기하지 않는다. 상태를 `PENDING`으로 되돌리는 재신청 처리(D-034)에서는 `rejection_reason`을 `NULL`로 초기화한다.
- 이유: D-033이 요구하는 강제 로그아웃의 실현부다. `PENDING` 복귀 시 사유가 남아 있으면 재신청 회원에게 거절 안내 화면이 계속 뜬다.
- 기각 대안: 모든 상태 변경에서 폐기(승인 직후 재로그인을 강요), refresh를 두고 access 만료만 기다림(최대 30분 창이 남음 — D-033이 명시적으로 기각).

## D-045. 비밀번호 해싱: DelegatingPasswordEncoder (기본 bcrypt)

- 2026-08 / `PasswordEncoderFactories.createDelegatingPasswordEncoder()`를 `PasswordEncoder` 빈으로 등록한다. 저장 값에는 `{bcrypt}` 접두가 붙으므로 `admin.password_hash`는 `VARCHAR(100)`으로 잡는다.
- 이유: 저장 값 자체가 알고리즘을 기술하므로, 나중에 알고리즘을 바꿔도 기존 해시를 그대로 검증할 수 있다. `spring-boot-starter-security`에 이미 포함돼 추가 비용이 0이다.
- 기각 대안: `BCryptPasswordEncoder` 직접 등록(알고리즘 교체 시 전 계정 재설정 필요), 직접 salt+hash 구현(적응형 work factor·타이밍 안전 비교를 직접 다뤄야 함).

## D-046. 카카오 연동 세부: state는 FE 책임, redirect_uri는 서버 환경변수 고정

- 2026-08 / CSRF 방지용 `state` 파라미터의 생성·검증은 FE가 담당한다. BE는 인가 코드만 받는다. `redirect_uri`는 요청 본문이 아니라 서버 환경변수(`KAKAO_REDIRECT_URI`)에서 읽어 카카오 토큰 교환에 사용한다.
- 이유: BE가 STATELESS(세션 없음)라 `state`를 저장해 둘 곳이 없다. `redirect_uri`를 요청에서 받으면 공격자가 자기 서버를 넣어 인가 코드를 유도할 수 있고, 카카오 콘솔 등록값과 서버 설정이 갈라진다.
- 기각 대안: BE가 state를 Redis 등에 저장해 검증(인프라 추가), redirect_uri를 요청 파라미터로 수용(오픈 리다이렉트 유사 위험).

## D-047. 신규 회원 지점 배정: 기본 지점 이름 설정으로 조회 배정

- 2026-08 / 카카오 최초 로그인으로 `Member`를 만들 때 `goldwrestling.default-branch-name`(기본값 `송파점`) 설정으로 `Branch`를 조회해 배정한다. 해당 지점이 없으면 로그인은 서버 오류로 실패한다.
- 이유: `member.branch_id`는 NOT NULL인데 회원 스스로 지점을 고르는 UI가 v1에 없다(MVP는 송파점 단일 — D-031). 지점명을 설정으로 빼두면 2호점이 생겨도 코드 수정 없이 환경별로 다르게 둘 수 있다.
- 기각 대안: 코드에 `송파점` 하드코딩(지점 추가 시 코드 수정), `branch.id = 1` 가정(identity 시퀀스에 의존하는 취약한 전제), 회원이 가입 시 지점 선택(v1 스코프 밖 — CROSS-01은 v2).

## D-048. 카카오 RestClient는 `RestClient.builder()` 직접 호출, `RestClient.Builder` 빈 주입 안 함

- 2026-08 / `KakaoRestClientConfig.kakaoRestClient()`는 스프링이 자동 구성한 `RestClient.Builder`를 생성자로 주입받지 않고 `RestClient.builder()`를 직접 호출해 만든다.
- 이유: 이 프로젝트 클래스패스에는 `RestClient.Builder` 자동 구성 빈을 등록하는 Boot 4.1의 `spring-boot-restclient`/`spring-boot-http-client` 모듈이 없다(`spring-boot-starter-webmvc`가 이를 끌어오지 않음 — `./gradlew dependencies --configuration compileClasspath`로 실제 확인). 주입을 시도하면 `NoSuchBeanDefinitionException`으로 앱 기동 자체가 실패한다(02-03 실행 중 재현·확인).
- 기각 대안: `spring-boot-starter-restclient`(또는 동등 모듈) 신규 추가(이번 phase가 명시한 "신규 패키지 1건" 예산을 넘어섬 — 필요성이 타임아웃 설정 하나뿐이라 과함), Boot의 `HttpClientSettings`/`ClientHttpRequestFactoryBuilder` API 사용(같은 이유로 모듈 부재). 대신 이미 `spring-web`에 있는 `SimpleClientHttpRequestFactory`의 `Duration` 기반 타임아웃 setter로 타임아웃(연결 3초/읽기 5초)을 구성한다.

## D-049. 테스트 Clock 교체: 같은 이름 `@Bean` 대체가 아니라 다른 이름 + `@Primary`

- 2026-08 / `TestClockConfiguration`이 프로덕션 `Clock` 빈(`ClockConfig.clock()`)을 테스트에서 대체하는 방식은, 빈 이름을 `clock`으로 맞춰 재정의하는 것이 아니라 **다른 이름(`testClock()`) + `@Primary`** 조합이다.
- 이유: `@SpringBootTest`가 컴포넌트 스캔으로 찾는 `ClockConfig.clock()`과 테스트에서 `@Import`로 등록한 같은 이름의 `@Bean`이 있으면, Spring Boot(`allow-bean-definition-overriding` 기본값 `false`)가 컨텍스트 로딩 시점에 `BeanDefinitionOverrideException`을 던진다는 것을 실행해 직접 재현·확인했다(교체가 아니라 예외). 다른 이름 + `@Primary`는 이름 충돌 없이 타입 기반 주입 지점 전부가 테스트 빈을 우선 선택하게 한다.
- 기각 대안: `spring.main.allow-bean-definition-overriding=true` 전역 설정(테스트 전체에서 의도치 않은 다른 빈 충돌도 조용히 통과시켜 버그를 숨길 위험), 매 통합테스트마다 `@TestPropertySource`로 이 플래그를 개별 지정(보일러플레이트가 늘고 까먹기 쉬움).

## D-050. 카카오 최초 로그인 경쟁 복구는 트랜잭션 밖 1회 재시도

- 2026-08 / `MemberRegistrationService.findOrCreateByKakaoId`는 유니크 제약 위반 예외를 잡지 않고 그대로 전파한다. 트랜잭션 애노테이션이 없는 `KakaoAuthService.login`이 이 예외를 잡아 같은 메서드를 새 트랜잭션으로 정확히 1회 재호출한다(02-REVIEW.md CR-01).
- 이유: PostgreSQL은 제약 위반 시 트랜잭션을 abort해 같은 트랜잭션 내 재조회가 불가능하고("current transaction is aborted"), 예외가 리포지토리 프록시 경계를 넘으면 스프링이 트랜잭션을 rollback-only로 마킹해 커밋이 `UnexpectedRollbackException`으로 실패한다 — 복구는 반드시 트랜잭션 경계 밖이어야 한다.
- 기각 대안: 같은 트랜잭션 내 catch 후 재조회(02-06의 원래 구현 — PostgreSQL에서 동작 불가), `login` 전체를 `@Transactional`로 묶기(conventions §7 위반 + 재시도가 같은 트랜잭션이 되어 무의미), `REQUIRES_NEW` 전파로 내부 재시도(self-invocation이라 프록시를 거치지 않아 적용되지 않고, 별도 빈을 새로 만들면 호출 체인만 늘어남).

## D-051. refresh 회전 폐기는 조건부 UPDATE + 실패 응답에서도 커밋

- 2026-08 / `TokenService.rotate`의 폐기 판단과 기록을 `RefreshTokenRepository.revokeIfUsable` 단일 조건부 UPDATE(`revokedAt is null`일 때만 갱신)로 원자화하고, 갱신 행 수 0을 재사용 신호로 취급한다. `rotate`는 `@Transactional(noRollbackFor = [RefreshTokenInvalidException::class])`로 재사용·만료 실패 응답을 주면서도 그 과정의 폐기를 커밋한다(02-REVIEW.md WR-01).
- 이유: 조회 → 메모리 판단(`isRevoked()`) → 더티체킹 폐기는 READ COMMITTED에서 같은 refresh 토큰이 동시에 두 번 제시되면 둘 다 미폐기 상태를 읽고 둘 다 회전에 성공시켜(TOCTOU), D-036의 재사용 감지가 정확히 탈취 시나리오에서 무력화된다. 또한 재사용 감지 후 예외(`RefreshTokenInvalidException`은 `RuntimeException` 상속)가 그대로 전파되면 스프링 기본 규칙에 걸려 감지 폐기 자체가 롤백돼 DB에 남지 않는다.
- 기각 대안: 비관적 락(`SELECT ... FOR UPDATE`)으로 행을 잠금(조건부 UPDATE로 충분한데 대기 비용만 늘어남), `SERIALIZABLE` 격리(전역 성능 비용), 예외를 체크 예외로 바꿔 롤백을 회피(코틀린에는 체크 예외 개념이 없고 이 프로젝트의 `ErrorCode`/`DomainException` 체계와도 어긋남).

## D-052. 상태 변경 API의 ACTIVE 전환도 온보딩 완료를 서버에서 강제

- 2026-08 / `changeStatus`는 `newStatus == ACTIVE`이면서 온보딩(실명·전화번호) 미완료인 회원에 대해 `MEMBER_STATE_CONFLICT`(409)로 거부한다. ACTIVE가 아닌 전이는 종전대로 제한 없음.
- 이유: `approve()`가 서버에서 강제하는 policies §5.1 규칙을 같은 리소스의 다른 엔드포인트가 우회시키면, 관리자가 회원을 이름·전화번호로 식별한다는 전제(D-025)가 깨진 ACTIVE 회원이 생긴다.
- 기각 대안: 우회를 허용하고 문서에만 명시(정책이 엔드포인트마다 갈라짐), 모든 전이에 전이표를 도입(policies §5.2가 "승인 취소는 상태 변경으로 갈음"이라 관리자 재량을 남겨 둔 취지와 충돌).

## D-053. 통합 검색어는 정규화 결과가 빈 문자열이면 전화번호 술어를 만들지 않는다

- 2026-08 / `keywordContains`는 `PhoneNumberNormalizer.normalize` 결과가 빈 문자열이면 전화번호 술어를 생성하지 않고 이름 술어만 사용한다. 온보딩 완료 판정 쿼리는 SQL `TRIM` 기준으로 엔티티의 `isNullOrBlank()`와 맞춘다(공백 문자 범위에서 일치).
- 이유: LIKE 와일드카드 이스케이프만으로는 `"-"` 같은 입력이 `LIKE '%%'`가 되는 경로를 막지 못해 전화번호가 있는 전 회원이 반환된다. 판정 규칙이 엔티티와 쿼리 두 곳에 존재하는 한 한쪽만 고쳐지면 승인 대기 목록이 어긋난다.
- 기각 대안: 검색어에서 하이픈을 제거한 뒤 blank 검사(이름에 하이픈이 든 검색을 못 하게 됨), 전화번호 컬럼에 함수 인덱스 도입(문제의 원인이 인덱스가 아님).

## D-054. 쿼리 조건 DTO는 `@ParameterObject`로 개별 파라미터로 펼쳐 기술한다

- 2026-08 / `@ModelAttribute`로 받는 조건 DTO 파라미터에는 항상 springdoc `@ParameterObject`를 함께 붙여, 생성되는 openapi.yaml이 필드별 개별 쿼리 파라미터가 되게 한다. 이후 페이즈의 목록·검색 엔드포인트도 이 규칙을 따른다.
- 이유: 객체 파라미터 표현은 OpenAPI 기본 해석으로는 등가지만 생성기에 따라 `deepObject`나 JSON 문자열로 직렬화되어 서버가 값을 전혀 바인딩하지 못한다. openapi.yaml이 FE와의 유일한 계약(D-013)인 이상 해석이 갈리는 표현은 계약으로 부적합하다.
- 기각 대안: 컨트롤러 시그니처를 개별 `@RequestParam` 5개로 풀기(검증 애노테이션과 기본값이 흩어지고 조건이 늘 때마다 시그니처가 길어짐), 생성된 yaml을 손으로 수정(D-029의 재생성이 되돌려 버려 드리프트의 원인이 됨), FE 쪽 직렬화 설정으로 회피(계약이 아니라 소비자 구현에 의존하게 됨).

## D-055. 이용권 등록은 시작일 지정(과거 허용)·0.5 단위 자유 입력·`INITIAL_GRANT` 이력을 기본으로 한다

- 2026-08 / 이용권 등록 시 시작일을 지정할 수 있다(기본값 오늘, 과거 날짜 허용). 횟수권 유효기간 1년과 저녁반 회비 만료일은 모두 **시작일 기준**으로 계산한다(policies §1 "등록일로부터 1년"을 시작일 기준으로 정정). 횟수권 초기 횟수는 0.5 단위 자유 입력이며, 초기 부여도 `PassTransaction`(+수량, 사유 `INITIAL_GRANT`)으로 남긴다.
- 이유: 결제는 전부 오프라인 수기 등록이라 실제 결제·이용 시작 시점과 시스템 등록 시점이 어긋날 수 있다. 초기 부여가 이력에 없으면 "잔여 = 이력 합계" 원장 검증이 등록 시점부터 깨진다(PASS-02의 "이력 없는 잔여 변경 불가"와 모순).
- 기각 대안: 등록일 고정(수기 등록 지연 시 유효기간이 회원에게 불리하게 어긋남), 정해진 상품 단위만 입력(10회/20회 등 — 체육관 운영상 자유 프로모션·보상 횟수가 흔해 제약이 실사용과 안 맞음), 초기 횟수를 이력 없이 컬럼 초기값으로만 설정(원장 불변식 예외 발생).

## D-056. 수동 가감은 0.5 단위·음수 잔여 금지·만료권 허용·기간제 제외

- 2026-08 / `ADMIN_ADJUST` 수동 가감은 0.5 단위로 하고, 결과 잔여가 음수가 되는 가감은 거부한다. 유효기간이 만료된 횟수권에도 가감할 수 있으며, 이를 위해 관리자가 횟수권 유효기간을 수정하는 기능(PASS-07)을 세트로 제공한다(만료 후 서비스 부여 대응). 기간제(`EVENING_MEMBERSHIP`)는 횟수 가감 대상이 아니다 — 기간 수정으로만 조정한다.
- 이유: 저녁반 0.5회 차감 보정 등 0.5 단위 조정이 실제 운영에 존재한다(policies §4.2). 음수 잔여는 "회원이 보는 잔여 = 실제 사용 가능 횟수" 핵심 가치와 양립할 수 없다. 만료권 가감만 허용하고 유효기간을 못 고치면 부여한 횟수를 쓸 수 없어 반쪽 기능이 된다.
- 기각 대안: 정수 단위만 허용(0.5회 보정 불가), 음수 허용 후 사후 정산(잔여 표시 신뢰 붕괴), 만료권 가감 금지(보상·서비스 부여 운영 케이스 대응 불가).

## D-057. 기간·유효기간 변경 이력은 전용 테이블 `PassPeriodChange`로 남긴다

- 2026-08 / 저녁반 회비 기간 수정(PASS-04)과 횟수권 유효기간 수정(PASS-07)의 이력은 전용 테이블 `pass_period_change`(이용권, 변경 전·후 시작/종료일, 사유, 주체 admin_id, 시각)로 남긴다. `PassTransaction`은 ±수량 원장 역할에 고정한다.
- 이유: phase 목표가 "모든 변경이 감사 가능한 이력"인데 기간 변경은 수량 구조에 담기지 않는다. 전용 테이블은 `PassTransaction`과 같은 패턴이라 일관되고, 변경 전값/후값/사유/주체/시각을 그대로 조회 API로 노출할 수 있다.
- 기각 대안: `PassTransaction`에 기간 필드를 덧붙여 겸용(수량 원장의 합계 검증에 이질 행이 섞임), Hibernate Envers 등 범용 감사(Boot 4/Hibernate 7 호환 검증 부담 + 사유·주체 같은 도메인 필드 커스터마이징 + 조회 API 가공 복잡).

## D-058. 회원 본인 이용권 조회는 만료·소진 포함, 이력은 이용권별 필터 + page/size

- 2026-08 / 회원 본인 이용권 조회(PASS-05)는 만료·소진된 이용권도 상태 구분과 함께 노출한다(취소된 이용권만 숨김 — D-059). 이력 조회(PASS-06)는 이용권별 필터와 page/size 페이지네이션(회원 목록 D-035와 동일 형태, `PageResponse` 재사용)으로 제공한다.
- 이유: 만료·소진 내역이 안 보이면 "내 횟수가 어디로 갔는지" 문의에 시스템이 답하지 못한다 — 이력 감사 가능성이라는 phase 목표의 회원 쪽 절반이다. 페이지네이션 형태는 기존 계약(D-035, D-054)과 통일해야 FE 타입 재사용이 된다.
- 기각 대안: 사용 가능한 이용권만 노출(문의 대응 불가), 이력 전체 나열(이력 누적 시 응답 폭증), 커서 페이지네이션(기존 D-035 page/size와 계약 형태가 갈라짐).

## D-059. 이용권 오등록 정정은 물리 삭제가 아닌 취소 상태 + `REGISTRATION_CANCELED` 상쇄 이력

- 2026-08 / 관리자가 이용권 등록을 취소할 수 있다(PASS-08). 물리 삭제가 아니라 취소 상태 전환이며, 횟수권은 잔여를 0으로 만드는 `PassTransaction`(−잔여, 사유 `REGISTRATION_CANCELED`)을 함께 남겨 "잔여 = 이력 합계" 불변식을 취소된 이용권에도 유지한다. 기간제는 수량 없이 상태 전환 + 이력만 남긴다. 취소된 이용권은 회원 화면에서 숨기고 관리자 화면에서는 구분 표시한다.
- 이유: 물리 삭제는 `PassTransaction`·`PassPeriodChange` 이력의 참조 대상을 없애 감사 추적이 끊긴다. 상쇄 이력이 없으면 원장 합계 검증에 "취소된 이용권 제외" 예외가 생겨 향후 배치(M5)·감사 로직이 복잡해진다.
- 기각 대안: 물리 삭제(이력 고아 발생), 수량 0 마커만 기록(원장 불변식에 예외 발생), `ADMIN_ADJUST`로 잔여만 0 처리(이용권 자체는 살아 있어 회원 화면에 계속 노출되고 취소 의도가 이력에 안 남음).

## D-060. `Pass`는 단일 엔티티 + `PassType` 판별 컬럼 (JPA `@Inheritance` 미사용)

- 2026-08 / `Pass`는 JPA `@Inheritance` 없이 **단일 엔티티 + `PassType` 판별 컬럼**으로 설계한다. 타입별 컬럼 규칙(횟수제만 `remaining_count` NOT NULL)은 DB CHECK 제약으로 강제한다. [사용자 확정]
- 이유: 이용권 3종이 공유하는 필드가 대부분이라 `@Inheritance` 계층의 조인·판별 컬럼 오버헤드 없이도 표현할 수 있고, CHECK 제약이 애플리케이션 버그와 무관하게 타입별 필수 컬럼을 DB 레벨에서 강제한다.
- 기각 대안: `JOINED`/`SINGLE_TABLE` 상속(계층 하나를 위해 매핑 복잡도가 늘어남 — 3종 모두 필드 대부분을 공유해 상속의 이점이 적다).

## D-061. `PassTransaction`은 `reason`(코드)과 `note`(자유 텍스트)를 분리한다

- 2026-08 / `PassTransaction`은 `reason`(`TransactionReason` enum)과 `note`(nullable 자유 텍스트)를 분리한다. `ADMIN_ADJUST`일 때만 `note` 필수를 서비스 계층에서 강제한다. [사용자 확정]
- 이유: `reason`은 FE 분기·집계에 쓰이는 닫힌 코드이고, `note`는 관리자가 남기는 임의 사유 설명이라 성격이 다르다. 하나로 합치면 집계·분기 쿼리가 문자열 매칭에 의존하게 된다.
- 기각 대안: `reason` 문자열 하나로 통합(FE 분기가 자유 텍스트 파싱에 의존하게 됨).

## D-062. 기간·유효기간 수정은 통합 엔드포인트 하나로 처리

- 2026-08 / 기간·유효기간 수정은 `PATCH /api/admin/passes/{passId}/period` 하나로 처리한다. 횟수권은 **종료일만** 수정 가능(시작일 고정), 저녁반은 시작·종료 모두 수정 가능하다. [사용자 확정]
- 이유: 두 종류 모두 결과가 `PassPeriodChange` 이력(D-057)이라는 점이 같고, 엔드포인트를 나누면 FE가 이용권 타입별로 다른 API를 호출해야 해 계약이 복잡해진다. 수정 가능 필드의 차이는 요청 바디 검증으로 표현한다.
- 기각 대안: 타입별 별도 엔드포인트(계약 중복, FE 분기 증가).

## D-063. `EveningMembershipTerm`(개월 수)은 저장하지 않는다

- 2026-08 / `EVENING_MEMBERSHIP`의 개월 수(1/3/6, `EveningMembershipTerm`)는 저장하지 않는다 — 등록 시 `end_date` 계산 입력으로만 쓴다.
- 이유: 이후 기간 수정이 날짜 직접 지정(D-057 `PassPeriodChange`)이라, 개월 수를 저장하면 기간 수정 후 실제 기간과 저장된 개월 수가 어긋나 표시가 거짓말이 된다.
- 기각 대안: 개월 수 컬럼 유지 + 수정 시 갱신(수정 경로마다 동기화를 잊지 않아야 하는 이중 관리 지점이 생김).

## D-064. 이용권 상태는 `PassStatus`(저장) / `PassDisplayStatus`(계산)로 분리한다

- 2026-08 / 이용권 상태는 `ACTIVE`/`CANCELED`(`PassStatus`)만 저장하고, 만료·소진은 조회 시점에 `PassDisplayStatus`(`USABLE`/`EXPIRED`/`EXHAUSTED`/`CANCELED`)로 계산한다.
- 이유: 만료·소진 여부를 저장하면 배치(M5)가 돌기 전까지 저장값과 실제가 갈라진다. 조회 시점 계산은 항상 최신 상태를 보장한다.
- 기각 대안: 만료·소진 상태를 컬럼으로 저장(배치 실행 전까지 표시가 거짓말이 됨).

## D-065. 등록 취소 시 잔여 0이면 상쇄 `PassTransaction`을 남기지 않는다

- 2026-08 / 이용권 등록 취소(D-059) 시 잔여가 이미 0이면 상쇄 `PassTransaction`(`REGISTRATION_CANCELED`)을 남기지 않는다.
- 이유: 수량 변화가 0인 행은 원장에서 의미가 없고, `pass_transaction`의 `amount <> 0` CHECK 제약과도 정합이 맞는다. "잔여 = 이력 합계" 불변식은 상쇄 행 없이도 그대로 유지된다.
- 기각 대안: 항상 상쇄 행 기록(`amount = 0` 행이 생겨 CHECK 제약과 충돌하거나 예외 케이스가 필요해짐).

## D-066. 유효기간·회비 기간의 경계 산정: 종료일 포함 + 정확히 1년/개월

- 2026-08 / `SESSION_PASS`/`LESSON_PASS`·`EVENING_MEMBERSHIP` 모두 **종료일을 포함**해 계산한다.
  `endDate = startDate.plusYears(1).minusDays(1)`(횟수권·레슨권, 1년) / `endDate = startDate.plusMonths(term).minusDays(1)`(저녁반, 1/3/6개월).
  유효 판정은 `!today.isAfter(endDate)` — `endDate` 당일까지 사용 가능하다. [사용자 확정, Task 1 결정]
- 이유: 이용 가능 일수가 정확히 기간 길이(1년/1·3·6개월)이고, "만료일"로 보이는 날짜가 "그날까지 사용 가능"과 일치해 회원 안내 문구가 단순해진다.
- 기각 대안: 종료일 미포함(`endDate = startDate.plusYears(1)`) — 계산식은 단순하지만 저장된 만료일 당일에 못 쓰는데 화면엔 그 날짜가 만료일로 보여 회원 문의를 유발하고, FE가 표시용으로 하루를 빼야 해 경계 처리가 두 곳으로 흩어진다.

## D-067. `pass` 테이블은 `branch_id`를 보유한다

- 2026-08 / `pass` 테이블은 `branch_id`를 보유한다. 값은 등록 시점 회원의 소속 지점으로 채운다(발급 지점 귀속).
- 이유: requirements §1의 "모든 핵심 엔티티에 `branch_id`" 확장 전제와 add-migration §2 규약을 따른다. MVP는 송파점 1개지만 지점 확장 시 컬럼 추가 없이 대응할 수 있다.
- 기각 대안: `branch_id` 생략 후 회원 경유로 지점 추적(지점 간 이관·교차 운영 시 발급 지점 이력을 잃음).

## D-068. 이용권 등록은 회원 상태로 제한하지 않는다

- 2026-08 / 관리자 이용권 등록(PASS-01·PASS-02)은 회원 상태(`PENDING`/`ON_LEAVE`/`INACTIVE`)로 막지 않는다 — 어느 상태의 회원에게도 등록할 수 있다. [사용자 확정, 2026-08-03 plan-phase AskUserQuestion — "제한 없음, 관리자 재량" 선택]
- 이유: policies·requirements 어디에도 등록 시점 회원 상태 제한이 없고, D-034·D-044가 상태 전이에 관리자 재량을 남긴 선례를 그대로 따른다. 예를 들어 온보딩 전이라도 오프라인 결제·상담이 먼저 이뤄질 수 있어, 이용권 등록을 회원 승인 이후로 미루면 실제 운영 순서와 맞지 않는다.
- 기각 대안: `ACTIVE` 회원만 등록 허용(오프라인 결제가 앞서는 실제 운영 순서와 불일치 — 승인 전 이용권 선등록을 막아 관리자 재량을 축소).
- 2026-08-04 phase 마감 human-verify에서 사용자 재확인 완료 — 이의 없음.

## D-069. 기간·유효기간 수정 시 전값·후값이 완전히 같으면 이력을 남기지 않는다

- 2026-08 / `AdminPassService.changePeriod`는 `Pass.changePeriod` 호출 후 시작일·종료일 중 하나라도 실제로 바뀌었을 때만 `PassPeriodChange`를 저장한다. 둘 다 그대로면(같은 값 재전송) 이력 행을 남기지 않는다.
- 이유: D-065("등록 취소 시 잔여 변화가 0이면 상쇄 이력을 남기지 않는다")와 같은 원칙 — 변화가 없는 행은 감사 이력에서 의미가 없고, 남기면 "몇 번 바뀌었는가"를 이력 건수로 셀 수 없게 된다.
- 기각 대안: 항상 이력 저장(변화 없는 재전송도 이력에 쌓여 실제 변경 횟수를 왜곡).

## D-070. 회원 본인 이력 조회 응답에서 관리자 메모(`note`)를 제외한다

- 2026-08 / 회원 본인 차감/복구 이력 조회(PASS-06, `GET /api/members/me/pass-transactions`)의 응답 `PassTransactionResponse`는 `PassTransaction.note`(관리자 자유 텍스트 사유)를 담지 않는다. `reason`(닫힌 코드)만 노출한다. **[사용자 확정, 2026-08-03 plan-phase AskUserQuestion — "회원에게 비노출" 선택]**
- 이유: requirements §3.2가 요구하는 "무슨 사유로"는 `reason` 코드로 충분히 답할 수 있고, `note`는 관리자가 남기는 임의 운영 메모(예: "이벤트 보상", 분쟁 소지가 있는 내부 코멘트)라 회원에게 그대로 노출하면 D-043("거절 사유 원문은 회원에게 노출하지 않는다")과 같은 문제가 생긴다.
- 기각 대안: `note`도 그대로 노출(내부 메모 유출 위험), `ADMIN_ADJUST`일 때만 조건부로 숨김(응답 스키마가 사유 코드에 따라 필드 유무가 갈려 FE 타입이 불안정해짐).
- 2026-08-04 phase 마감 human-verify에서 사용자 재확인 완료 — 이의 없음.

## D-071. 회원 본인 이용권·이력 조회는 회원 상태로 제한하지 않는다

- 2026-08 / `MemberPassService.getMyPasses`·`getMyTransactions`(PASS-05·PASS-06)는 `MemberStateGate.requireActive`를 호출하지 않는다 — `ON_LEAVE`(휴회)·`PENDING`·`INACTIVE` 회원도 본인 이용권과 이력을 조회할 수 있다. **[사용자 확정, 2026-08-04 phase 3 검증 AskUserQuestion — "휴회도 조회 허용" 선택]**
- 이유: policies §5가 휴회를 "정상적으로 로그인해 쓰는 상태"로 정의하므로 복귀 전 잔여 확인이 가능해야 하고, 등록이 회원 상태를 가리지 않으므로(D-068) 어떤 상태든 본인 이용권은 본인이 볼 수 있어야 한다. `MemberProfileService.getMyProfile`이 같은 이유로 게이트를 걸지 않은 선례를 따른다. 응답은 본인 스코프로만 한정되어 상태 제한 없이도 새어나갈 데이터가 없다.
- 기각 대안: `ACTIVE`만 허용(휴회 회원이 복귀 전 잔여를 확인할 수 없어 policies §5와 충돌 — 초기 구현이 이 형태였고 phase 3 검증에서 발견되어 수정됨).

## D-072. 이용권 취소·기간수정도 조건부 UPDATE로 상태 전환한다(D-021 확장)

- 2026-08 / `AdminPassService.cancel`·`changePeriod`는 더 이상 엔티티를 mutate한 뒤 dirty-checking flush로 반영하지 않는다. `Pass.resolveCancellationOffset`·`Pass.resolvePeriodChange`는 판정·계산만 하고 상태를 바꾸지 않으며, 실제 상태 전환은 `PassRepository.cancelIfNotCanceled`(상태<>CANCELED 조건)·`changePeriodIfUnchanged`(전값 compare-and-swap) 조건부 UPDATE가 한다. 반환 행 수 0을 각각 `PassAlreadyCanceledException`·`PassStateConflictException`(기존 코드 재사용, 신규 코드 없음)으로 변환한다.
- 이유: 두 관리자가 같은 이용권을 동시에 취소·기간수정하면 read→mutate→save 경로는 조회-판단-저장 사이에 다른 트랜잭션이 끼어들 수 있어(T-03-38·WF-03-01, 03-REVIEW.md WR-03·WR-04) 둘 다 200을 받고 나중 커밋이 취소 사유·기간 전값을 조용히 덮어쓸 수 있었다. 특히 취소의 상쇄 수량 0 분기(기간제·잔여 0 횟수권)는 종전 코드에서 조건부 경로를 아예 타지 않아 그 분기만 경쟁에 완전히 노출돼 있었다.
- 기각 대안: `@Version` 낙관적 락(이 프로젝트가 D-021에서 이미 기각 — 재시도 로직 필요, 경쟁 폭증 시나리오에서 재시도 폭증), 애플리케이션 레벨 동기화(다중 인스턴스 무효).

## D-073. 회원 본인 이력 조회에서 취소된 이용권의 이력을 제외한다

- 2026-08 / 본인 차감/복구 이력 조회(PASS-06)는 취소(`CANCELED`)된 이용권의 이력(INITIAL_GRANT·ADMIN_ADJUST·REGISTRATION_CANCELED 상쇄 포함)을 응답에서 제외한다. `PassTransactionSpecifications.passNotCanceled()`가 non-null 필수 조건으로 항상 결합된다. **[사용자 확정, 2026-08-04 — 리뷰 WR-05 분석 후 "숨김" 방향 채택]**
- 이유: 본인 이용권 목록(D-058)이 취소 이용권을 숨기므로, 이력만 노출되면 회원 화면에 "목록에 없는 passId"의 행이 나타나 혼란을 유발한다. D-059가 취소를 "오등록 정정(없었던 것처럼)"으로 정의한 취지와도 일치. 관리자 화면은 반대로 전부 보이므로 감사 가능성은 유지된다.
- 기각 대안: 이력도 그대로 노출(목록·이력의 노출 범위 불일치로 FE 조인 깨짐), reason 코드별 선별 노출(규칙이 복잡해지고 FE 분기 증가).

## D-074. 회원 데스크탑 내비는 상단 헤더, 관리자는 좌측 사이드바

- 2026-08 / FE 내비게이션 확정: 회원용은 모바일 하단 탭 + 데스크탑 상단 헤더, 관리자용은 모바일 하단 탭 + 데스크탑 좌측 사이드바. design-system 스킬 반응형 표의 "내비게이션" 행을 회원/관리자 2행으로 분리해 ROADMAP("상단·사이드")과의 표기 불일치를 해소했다. **[사용자 확정, 2026-08-04 FE discuss-phase 1]**
- 이유: 회원 화면은 메뉴가 적고(홈·내 예약·이용권·공지) 소비자 서비스 관례상 상단 헤더가 자연스럽다. 관리자 화면은 메뉴가 많고 매일 장시간 쓰는 운영 도구라 좌측 사이드바가 정보 밀도·확장성에 맞는다.
- 기각 대안: 회원도 좌측 사이드바(메뉴 4개에 사이드바는 과함, 콘텐츠 폭 손실), 양쪽 모두 상단 헤더(관리자 메뉴 확장 시 수용 불가).

## D-075. M1에서 로그인 라우트 분리와 가드 전체 동작까지 구현한다

- 2026-08 / M1(FE Phase 1)에서 회원 `/login`·관리자 `/admin/login` 라우트를 분리하고, 껍데기 로그인 페이지 + 라우트 가드의 전체 동작(미인증→로그인 리다이렉트, PENDING·온보딩 미완료→해당 안내 화면 라우팅)까지 구현한다. 실제 카카오/관리자 인증 연동은 M2·M3에서 채운다. **[사용자 확정, 2026-08-04 FE discuss-phase 1]**
- 이유: 가드는 이후 모든 페이즈가 올라타는 구조라 M1에서 확정해야 M2·M3가 화면만 얹을 수 있다. 회원 상태(PENDING·온보딩)는 서버 데이터이므로 가드 판정은 로그인 요약 쿼리(TanStack Query) 기준으로 하고 Zustand에 복제하지 않으며, M1에서는 MSW 목으로 동작을 검증한다.
- 기각 대안: 가드를 미인증 리다이렉트만 두고 상태 분기는 M2로 미룸(M2에서 라우팅 구조 재작업 발생), 로그인 라우트 통합(회원 카카오·관리자 ID/PW의 UI·플로우가 달라 분리가 자연스러움).

## D-076. 401 refresh 회전은 M1에서 실제 구현하고, 에러 메시지는 중앙 code→메시지 맵으로 관리한다

- 2026-08 / FE API 클라이언트의 401 처리(`POST /api/auth/refresh` 계약 기준 refresh 회전, 동시 요청 시 중복 refresh 방지 포함)를 M1에서 실제 구현한다. 에러 사용자 문구는 중앙 code→메시지 맵 하나로 관리하되 feature별 override가 가능한 구조로 한다. 계약상 refresh 토큰이 응답 본문(TokenPairResponse)으로 오므로 authStore가 accessToken과 함께 refreshToken도 보관하도록 확장한다. **[사용자 확정, 2026-08-04 FE discuss-phase 1]**
- 이유: refresh 계약이 이미 openapi.yaml에 있어 자리만 남기면 M2에서 클라이언트 미들웨어를 재작업하게 된다. 중복 refresh 방지는 동시 쿼리가 기본인 TanStack Query 환경에서 필수. 메시지 맵 중앙화는 error-codes.md가 유일한 에러 계약이라는 원칙과 일치하고, feature override는 같은 코드가 문맥별로 다른 안내(예: 예약 화면의 정원 마감)를 요구하는 경우를 수용한다.
- 기각 대안: M1은 자리만(M2 재작업), feature별 분산 메시지(코드 계약과 문구가 흩어져 일관성 붕괴).

## D-077. M1 공통 컴포넌트는 FOUND-01 필수 4종만 만든다

- 2026-08 / M1의 `components/common/`은 PageHeader, EmptyState, ErrorState, 확인 다이얼로그 4종만 만든다. ResponsiveDialog(Sheet↔Dialog 전환)·DataTable은 두 번째 사용처가 생기는 시점(M3 예상)에 만든다. **[사용자 확정, 2026-08-04 FE discuss-phase 1]**
- 이유: fe-architecture 스킬의 "처음부터 공용으로 만들지 않는다 — 두 번째 사용처가 생겼을 때 올린다" 원칙 준수. 실사용처 없이 선제작하면 실제 요구와 어긋난 API가 고정된다.
- 기각 대안: M1에서 전부 선제작(사용처 없는 추측 설계, M1 범위 비대화).

## D-078. M2 FE 신규 의존성 4건 — react-hook-form + zod 4 + @hookform/resolvers 5 + sonner

- 2026-08 / FE(M2)에서 아래 4개를 `pnpm add react-hook-form zod @hookform/resolvers sonner`로 추가한다. **[CLAUDE.md 규칙 "새 의존성은 근거와 함께 제안하고 decisions.md에 기록한 뒤 추가"에 따른 선기록, D-16·D-21 이행]**

| 패키지                | 버전(2026-08-06 확인) | 용도                                       | 근거                                                             |
| --------------------- | --------------------- | ------------------------------------------ | ---------------------------------------------------------------- |
| `react-hook-form`     | 7.84.0                | 온보딩 폼 상태·제출 (M2 유일한 폼)         | fe-architecture 스킬이 이미 표준 스택으로 확정. peer `react ^19` |
| `zod`                 | 4.4.3                 | 이름·전화번호 검증 스키마                  | "검증은 스키마 하나가 소유한다" 원칙. JSX `required` 금지        |
| `@hookform/resolvers` | 5.7.1                 | `zodResolver` 어댑터                       | peer `zod: ^3.25.0 \|\| ^4.0.0` — **zod 4 호환 조합 확인 완료**  |
| `sonner`              | 2.0.7                 | mutation 실패 토스트 (D-21 인프라 최초 도입) | peer가 `react`/`react-dom`뿐 — 추가 전이 의존성 유입 없음        |

- 이유: 온보딩(AUTH-03)이 이 프로젝트 최초의 실제 폼이고, 검증 실패 문구를 필드에 붙이려면 rhf의 `setError`가 필요하다. zod는 4.x가 현행 메이저이고 resolvers 5.x가 `^3.25.0 || ^4.0.0`을 peer로 선언해 조합이 성립한다(zod 3으로 내리면 최신 API를 포기하게 된다). 4건 모두 성숙·고다운로드·공식 리포지토리 보유이고 `postinstall` 스크립트가 없다(`pnpm.onlyBuiltDependencies`가 `esbuild`·`@tailwindcss/oxide`로 제한돼 있어 네이티브 빌드 스크립트도 실행되지 않는다).
- 기각 대안: 폼 라이브러리 없이 `useState`+수기 검증(검증 로직이 JSX에 흩어져 스킬 규약 위반), zod 3 고정(resolvers 5가 zod 4를 지원하므로 굳이 내릴 이유 없음), Formik(유지보수 정체·리렌더 비용).

## D-079. 토스트는 shadcn `sonner` 블록이 아니라 `pnpm add sonner` + 자작 `common/Toaster`로 도입한다

- 2026-08 / D-21(토스트 인프라 도입)의 구현 방식 확정. shadcn 레지스트리의 `sonner` **블록을 쓰지 않고** npm 패키지 `sonner`만 설치한 뒤 `src/components/common/Toaster.tsx`를 직접 만든다.
- 이유: `sonner` 블록은 `next-themes`를 의존성으로 끌고 들어오고 `useTheme()`로 테마를 읽는 create-app 전용 코드를 포함한다. 이 앱은 다크 모드 토글이 없고(토큰만 유지) 테마 라이브러리를 도입할 계획도 없어, 블록을 쓰면 쓰지 않는 전이 의존성 하나가 번들과 lockfile에 남는다. 토스트 규약(위치 `top-center`, `richColors` 금지, 기본 아이콘 비움, 문구는 중앙 에러 메시지 맵 경유)을 우리 쪽 래퍼가 소유하는 편이 규약 강제에도 유리하다.
- 기각 대안: `shadcn add sonner`(불필요한 `next-themes` 유입), 토스트 없이 인라인 에러만(mutation 실패는 화면 전환과 겹쳐 인라인 표시 자리가 없다 — 규약이 이미 "변경 실패는 toast"로 확정).

## D-080. `radix-nova` 프리셋에 `form` 블록이 없다 — 폼 스택을 `field` 기반으로 정정한다

- 2026-08 / `pnpm exec shadcn view form` 결과 `radix-nova` 프리셋에 `form` 블록이 존재하지 않음을 실측 확인(2026-08-06). fe-architecture 스킬의 "react-hook-form + zod + shadcn `Form`(`FormField`/`FormMessage`)" 문구를 **`field` 블록(`Field`/`FieldLabel`/`FieldError`) 기반**으로 정정한다. `react-hook-form` + `zod` + `@hookform/resolvers` 조합과 "검증은 zod 스키마 하나가 소유한다"는 규약은 그대로 유지한다.
- 이유: 없는 블록을 전제로 한 규약을 남겨두면 구현 시점에 매번 재발견하게 된다. `field`는 `label`·`separator`를 registryDependency로 갖는 정식 블록이고 라벨·설명·에러 슬롯을 모두 제공해 `Form`의 역할을 대체할 수 있다. rhf의 `handleSubmit`이 Promise를 반환하므로 `onSubmit={(e) => void form.handleSubmit(fn)(e)}` 형태로 감싸야 ESLint `no-misused-promises`(error)에 걸리지 않는다는 점도 함께 규약에 적는다.
- 기각 대안: 다른 shadcn 스타일 프리셋으로 전환해 `form`을 얻기(기존 생성물 전체가 스타일 불일치), `Form` 컴포넌트를 손으로 이식(생성물 규약 밖의 유지보수 부담).

## D-081. `@types/kakao-js-sdk`를 설치하지 않고 레포 내 최소 ambient 선언을 쓴다

- 2026-08 / 카카오 JS SDK 타입은 DefinitelyTyped 패키지(`@types/kakao-js-sdk`) 대신 `src/types/kakao.d.ts`에 **실제 사용하는 3개 멤버만**(`init`, `isInitialized`, `Auth.authorize`) 선언한다. `window.Kakao`는 `optional`로 둔다.
- 이유: `@types/kakao-js-sdk@1.39.5`는 SDK **v1.39** API 기준이라 v2에서 제거된 `Auth.login(success/fail)`·`createLoginButton`·`getAccessToken`과 오타 필드(`prompts`, v2는 `prompt`)까지 타입으로 열어준다. 우리는 v2.8.1을 쓰므로 타입이 통과한 호출이 런타임에서 `is not a function`으로 죽는다. 없는 API를 타입 단계에서 막는 편이 안전하다. 이 선언은 BE 계약 타입이 아니라 **외부 전역 스크립트**의 타입이므로 "수기 API 타입 작성 금지"(CLAUDE.md 규칙 2) 위반이 아니다 — 계약 타입은 여전히 `openapi-typescript` 생성물 `src/api/schema.d.ts`에서만 온다.
- 기각 대안: `@types/kakao-js-sdk` 설치(버전 스큐로 런타임 오류 유발), `window.Kakao`를 `any`로 캐스팅(ESLint `no-explicit-any` error + 오용 방지 효과 0).

## D-082. 카카오 JS SDK는 CDN 2.8.1 + SRI로 로드하고, `--kakao` 브랜드 토큰은 `/login` 버튼 1곳에만 쓴다

- 2026-08 / 카카오는 JS SDK의 npm 패키지를 제공하지 않으므로 공식 CDN 스크립트(`kakao_js_sdk/2.8.1/kakao.min.js`)를 `integrity` + `crossorigin="anonymous"`와 함께 로드한다. 함께 도입하는 브랜드 색 토큰 `--kakao: #fee500` / `--kakao-foreground: #191919`는 **`/login`의 `카카오로 로그인하기` 버튼 하나에만** 사용하고 다른 어떤 요소에도 쓰지 않는다. `.dark`에서도 같은 값을 유지한다(브랜드 색은 테마에 따라 바뀌지 않는다).
- 이유: 외부 오리진 스크립트는 `window.Kakao`로 앱과 같은 권한을 갖기 때문에 SRI 없이 로드하면 CDN 변조가 그대로 앱 권한이 된다. 카카오 공식 문서도 "버전과 integrity 값을 정확히 입력"을 요구한다. 색값은 카카오가 지정한 정확한 브랜드 값이라 oklch로 변환하지 않고 hex 그대로 둔다(변환하면 브랜드 규정 색이 아니게 된다). `#191919` on `#FEE500` 대비는 14.9:1로 본문 기준 4.5:1을 통과한다. 사용처를 1곳으로 묶는 이유는 노랑을 다른 곳에 흘리면 "액센트 남용"으로 위계가 무너지기 때문이다.
- 기각 대안: `kauth.kakao.com/oauth/authorize`를 직접 조립(authorize의 `client_id`가 REST API 키인데 FE에는 JS 키만 있고, 카카오톡 앱 간편로그인을 잃는다 — D-01이 SDK로 확정), SRI 없이 CDN 로드(공급망 변조 무방비), 카카오 심볼 로고 에셋 추가(lucide에 없고 임의 SVG·이모지는 금지 — 텍스트 라벨만 쓴다. 카카오 심사에서 로고가 요구되면 그때 예외를 기록하고 추가한다).

## D-083. 카카오 프로필(닉네임·사진)을 회원 프로필 응답에 추가

- 2026-08 / FE 요청(M2 수동 검증 중, 2026-08-06)을 BE에서 확정·구현했다. 카카오 로그인 시 `/v2/user/me`의 `kakao_account.profile`에서 닉네임·프로필 이미지 URL을 수집해 `member.kakao_nickname`·`member.kakao_profile_image_url`(둘 다 nullable, V5 마이그레이션)에 저장하고, `MyProfileResponse`에 `kakaoNickname`·`kakaoProfileImageUrl`을 nullable로 노출한다. **[BE 확정, 2026-08-06]**
- 확정 내용: ① **매 로그인마다 카카오가 준 값으로 갱신**한다(최초 가입 시점에만 채우면 기존 회원이 영원히 null로 남는다). ② 카카오가 값을 주지 않으면(동의항목 미추가·동의 거부·사후 철회) **저장값을 null로 덮어쓴다** — 회원이 철회했는데 우리 DB가 계속 보관하는 상태를 만들지 않는다. ③ 노출 범위는 `MyProfileResponse`(`GET /api/members/me`)뿐이고 관리자 응답(`MemberDetailResponse`·`MemberSummaryResponse`)·로그인 요약(`MemberLoginSummaryResponse`)에는 넣지 않는다. ④ 이미지 URL은 640px `profile_image_url`을 저장한다(110px `thumbnail_image_url`은 매핑만 해 두고 쓰지 않는다). ⑤ 카카오 개발자 콘솔 동의항목(`profile_nickname`·`profile_image`) 추가는 **운영자가 콘솔에서 직접 처리**하며, 동의항목이 없어도 코드는 정상 동작한다(두 값이 null일 뿐 로그인은 200으로 성공한다).
- 이유: 현재 `MyProfileResponse`는 실명·전화번호·상태만 담고 있어 FE가 표시할 수단이 없다(FE는 계약에 없는 API를 구현하지 않는다는 원칙). 계약 반영 후 FE는 `pnpm api:types` 재생성만으로 표시할 수 있다. 카카오 응답의 중간 노드(`kakao_account`·`profile`)를 전부 nullable로 매핑한 이유는, 동의항목이 없을 때 그 객체들이 응답에서 통째로 빠지기 때문이다 — non-null로 두면 역직렬화 실패로 로그인 전체가 막힌다.
- 참고: 체육관 운영 기준 신원은 온보딩 실명·전화번호가 정본이고 카카오 프로필은 표시용 보조 정보다. 프로필 값은 로그에 남기지 않는다(개인정보 성격, T-02-19 연장선).
- 추가 확정(PR #7 리뷰 Info): 카카오가 컬럼 길이(닉네임 100자·URL 500자)를 넘는 값을 주면 **잘라 저장하지 않고 `null`로 취급**한다. 대입은 성공하고 트랜잭션 커밋 시점의 UPDATE에서야 제약 위반으로 터지는데, 그 예외는 로그인 흐름 전체를 500으로 만든다 — 동의항목이 없어도 로그인이 죽지 않게 설계한 것과 같은 이유로, 예상 밖 응답을 "값 없음"이라는 이미 정상인 상태로 흡수한다. 길이 상수는 `Member`의 companion object 하나에서만 정의해 `@Column(length=)`와 검증이 갈라지지 않게 한다.
- 기각 대안: 최초 가입 시에만 저장(기존 회원 공백), 동의 철회 시 기존 값 유지(철회 의사와 어긋남), 관리자 응답에도 추가(요구 범위 밖으로 개인정보 노출면만 넓어짐), 길이 초과 시 `take(n)`으로 잘라 저장(닉네임은 틀린 이름이 남고 URL은 깨진 주소가 남아 FE가 깨진 이미지를 렌더링한다 — 둘 다 "값 없음"보다 나쁘다).

## D-084. phase는 청크 단위로 나눠 PR을 내고, 청크 경계에서는 커밋·푸시·PR을 자동으로 한다

- 2026-08 / phase 하나를 PR 하나로 내지 않는다. 플랜 여러 개를 리뷰 가능한 크기(PR당 3,000~7,000줄)의 **청크**로 묶어, 청크마다 `feature/phase-{N}{a|b|c}-{slug}` 브랜치를 `origin/dev`에서 따고 PR → dev 머지를 완주한 뒤 다음 청크로 간다. 청크 경계에서는 사용자 승인을 기다리지 않고 커밋·푸시·PR 생성까지 자동으로 하고 결과를 보고한다 — **CLAUDE.md "커밋·푸시는 명시적 요청 시에만" 규칙의 명시적 예외**다. 절차는 `.claude/skills/deliver-phase-chunk/SKILL.md`. **[사용자 확정, 2026-08-07 — Phase 4 착수 직전]**
- 이유: Phase 2가 +17,857줄, Phase 3이 +11,682줄 단일 PR이었고 그 크기는 리뷰봇(max-turns 60)도 사람도 실질적으로 읽지 못한다 — "봤다고 치고 머지"가 된다. 실제로 인라인 지적이 잡힌 유일한 PR은 +1,368줄짜리(#7)였다. Phase 4는 요구사항 13개로 둘보다 크다. 자동화 범위를 `feature/phase-*` 브랜치로 한정하고 **머지 버튼은 사용자가 누르는 것**으로 남겨 두면, 배포는 여전히 dev→main PR 시점에만 일어나므로 자동 PR이 위험을 만들지 않는다.
- 함께 정한 것: ① 불변식과 그 방어 코드(예: "예약 생성"과 "정원 초과 방지")는 **반드시 한 청크** — 나누면 불변식을 위반하는 코드가 dev에 남는다. ② 스키마와 그 스키마를 쓰는 첫 코드도 한 청크(커밋된 마이그레이션은 수정 불가). ③ 청크는 **순차 진행** — 병렬로 열면 두 브랜치가 같은 Flyway 버전을 만든다. ④ `.planning/config.json`의 `git.branching_strategy`를 `phase` → `none`으로 바꿔 브랜치 소유권을 이 절차로 옮겼다.
- 알려진 제약: GSD는 "청크"를 모른다. 끊을 수 있는 유일한 장치는 `/gsd-execute-phase {N} --wave {M}`(wave 하나만 실행 후 멈춤)이고 **플랜 단위 실행 명령은 없다.** 따라서 청크 경계는 항상 wave 경계와 일치해야 하며, wave 하나가 이미 너무 크면 execute를 시작하기 전에 plan 단계로 돌아가 의존 관계를 다시 잡아야 한다. `--wave` 없이 실행하면 남은 플랜을 전부 돌려 청크 분할이 무너진다.
- 함께 고친 것: `origin/HEAD`가 `origin/main`을 가리키고 있었다(main은 dev보다 235커밋 뒤처짐). GSD `execute-phase`가 브랜치를 `origin/HEAD`에서 따므로 그대로 두면 Phase 2·3 코드가 없는 브랜치에서 작업이 시작된다. `git remote set-head origin dev`로 교정했고, `--auto`를 돌리면 되돌아가므로 phase 시작 전 확인을 절차에 넣었다.
- 기각 대안: phase당 PR 1개 유지(리뷰가 형식적으로만 존재), 플랜당 PR 1개(Phase 4 기준 11~15개로 오버헤드가 리뷰 이득을 넘어섬), 청크 경계마다 사용자 승인 대기(승인 대기 자체가 병목이고, PR은 닫으면 되돌릴 수 있어 위험이 낮다), PR 자동 머지(리뷰봇 결과를 보지 않고 머지하게 되어 이 결정의 목적 자체를 무너뜨림).

## D-085. M3 관리자 IA: 승인 대기는 사이드바 메뉴+뱃지, 화면은 회원 목록 프리셋 필터, `/admin`은 회원 목록으로 리다이렉트

- 2026-08 / FE 관리자 화면(M3)의 정보 구조: 사이드바에 승인 대기를 **별도 메뉴 + 미처리 건수 뱃지**로 두되, 화면은 별도 구현 없이 회원 목록의 프리셋 필터(`status=PENDING` + `onboardingCompleted=true`)를 재사용한다. 뱃지 건수는 같은 검색 API를 `size=1`로 호출해 `totalElements`를 쓴다. `/admin` 인덱스는 M3에서 회원 목록으로 리다이렉트하고, 대시보드 콘텐츠는 M5의 주간 보드가 맡는다. **[사용자 확정, 2026-08-07 FE discuss-phase 3]**
- 이유: D-035가 "승인 대기 목록은 전용 API 없이 동일 API 필터 조합으로 재사용"을 명시해 API 설계가 이미 이 방향을 전제한다. 승인 대기를 메뉴로 분리하면 매일 쓰는 핵심 업무의 진입이 한 클릭이 되고, 화면을 재사용하면 목록 로직이 한 벌로 유지된다. M3에서 대시보드를 선제작하면 M5 주간 보드와 중복 설계가 된다.
- 기각 대안: 승인 대기 전용 화면 별도 구현(목록·검색 로직 중복), `/admin`에 임시 대시보드 콘텐츠(M5에서 갈아엎을 추측 설계), 뱃지용 전용 카운트 API 요청(D-035가 기각한 화면별 전용 API의 재생산).

## D-086. M3 관리자 목록: 데스크탑 페이지 번호 / 모바일 '더 보기', DataTable 공용화 (D-077 이행)

- 2026-08 / 관리자 회원 목록의 페이지네이션은 **데스크탑 테이블 = 페이지 번호, 모바일 리스트 = '더 보기'**로 한다. 둘 다 동일한 page/size API(D-035)를 소비하므로 계약 분기는 없다. DataTable은 D-077이 예정한 대로 이 페이즈에서 `components/common/` 공용 컴포넌트로 도입한다(회원 목록 + 회원별 이용권 목록으로 사용처 2곳 확보). **[사용자 확정, 2026-08-07 FE discuss-phase 3]**
- 이유: 관리자 테이블은 특정 페이지로 건너뛰는 조회(페이지 번호)가 관례이고, 모바일 리스트는 M2 이력 화면에서 확립한 '더 보기'(useInfiniteQuery) 패턴과 UX가 일관된다. design-system의 "목록 = 데스크탑 테이블 / 모바일 리스트 행" 전환 규약과 한 몸으로 움직인다.
- 기각 대안: 양쪽 모두 페이지 번호(모바일에서 페이지 내비 터치 타깃이 작고 M2 패턴과 어긋남), 양쪽 모두 무한 스크롤(관리자가 특정 페이지·건수를 확인하는 운영 조회에 부적합), DataTable 없이 화면별 테이블 중복(D-077의 "두 번째 사용처" 시점 도래를 무시).

## D-087. M3 회원 상세는 프로필+이용권+이력 통합 화면, 폼은 다이얼로그, 관리자 이력은 BE-REQ-003 계약 전제(연동 유예)

- 2026-08 / 관리자 회원 상세는 **프로필 + 이용권 목록 + 이력을 한 화면**에 배치한다. 이용권 등록/수동 가감/기간 수정/등록 취소 폼은 **다이얼로그**로 열고 M2 폼 스택(react-hook-form + zod + `field` 기반, D-078·D-080)을 재사용한다. 관리자용 이력 조회 API는 계약에 없으므로 "BE에 필요한 변경"(FE `.planning/BE-CHANGE-REQUESTS.md`의 **BE-REQ-003**)으로 확정한다: `GET /api/admin/members/{memberId}/pass-transactions` — passId 필터 + page/size(D-054 형태), 응답에 관리자 메모(`note`) 포함(D-070이 회원 응답에서 note를 제외한 것의 관리자 쪽 대응). M3에서는 이 계약을 전제로 이력 섹션을 **컴포넌트 + fixture 스토리로만** 고정하고, `api.ts` 호출·MSW 핸들러는 만들지 않으며 연동은 BE 반영 후 붙인다. **[사용자 확정, 2026-08-07 FE discuss-phase 3]**
- 이유: 관리자 업무(승인 확인 → 이용권 등록 → 잔여 보정)가 한 회원을 중심으로 이어지므로 화면을 쪼개면 이동만 늘어난다. 다이얼로그 폼은 목록 컨텍스트를 유지한 채 조작하게 해 준다. 이력에 note가 없으면 관리자가 자기가 남긴 조정 사유를 볼 수 없어 PASS-05(회원별 이력 조회)가 반쪽이 된다. FE가 계약 밖 엔드포인트를 코드·목 어디에도 만들지 않는 원칙(M1 게이트 불변식)은 유지한다.
- 기각 대안: 이용권 관리를 별도 페이지로 분리(업무 흐름 단절), 회원 본인용 `/members/me/pass-transactions` 형태를 관리자에서 유용(권한 스코프가 다르고 note 부재), FE가 MSW로 가짜 엔드포인트를 먼저 구현(계약 밖 API 0건 원칙 위반 — 우회 코드 영구화 위험).

## D-088. M3 관리자 액션 확인 정책: 승인은 즉시 실행, 거절·등록 취소·상태 변경은 확인 다이얼로그 + 사유/경고

- 2026-08 / **승인은 확인 다이얼로그 없이 즉시 실행**한다 — 상태 변경으로 되돌릴 수 있는 가역 액션이라 확인은 마찰만 된다(FE 규약 "확인 다이얼로그는 비가역 액션 전용"과 일치). **거절 = 확인 다이얼로그 + 사유 입력(필수)**, **등록 취소 = 확인 다이얼로그**, **상태 변경 = 확인 다이얼로그**로 하되, 강제 로그아웃 경고 문구는 D-044에 따라 **전환 후 상태가 ACTIVE가 아닐 때만** 표시한다(ACTIVE로 바꾸는 변경은 refresh 폐기가 없으므로 경고하지 않는다 — 거짓 경고 금지). 처리 결과는 mutation 성공 시 쿼리 무효화로 목록·뱃지에 즉시 반영한다. **[사용자 확정, 2026-08-07 FE discuss-phase 3]**
- 이유: 승인은 매일 반복하는 핵심 업무라 확인 한 번이 그대로 운영 마찰이 되고, 잘못 눌러도 상태 변경으로 복구된다. 반면 거절(사유가 회원 안내에 쓰임)·등록 취소(이용권 소멸)·상태 변경(회원 세션 강제 종료 가능)은 결과가 회원에게 즉시 영향을 주므로 확인과 결과 고지가 필요하다.
- 기각 대안: 승인에도 확인(가역 액션에 확인을 붙이는 규약 위반 + 반복 업무 마찰), 모든 상태 변경에 일괄 강제 로그아웃 경고(ACTIVE 전환에는 사실이 아닌 경고 — D-044와 불일치), 거절 사유를 선택 입력으로(policies §5.2·ADMN-02가 사유를 요구).

## D-089. 등록 취소 선행 조건: 활성 예약이 있으면 이용권 등록 취소를 거부한다

- 2026-08 / 대상 이용권으로 잡힌 활성 예약이 하나라도 있으면 등록 취소를 거부한다. 관리자가 대리 취소(복구 안 함)로 먼저 정리한 뒤 등록을 취소하는 2단계 절차로 처리하며, 자동 연쇄 취소는 만들지 않는다.
- 이유: 오등록 정정은 드문 운영 행위이고, 취소된 이용권의 잔여는 `REGISTRATION_CANCELED`로 0으로 상쇄되므로(D-059) 연쇄 복구가 그 상쇄와 곧바로 충돌한다. 명시적 2단계 절차가 자동 연쇄보다 안전하다.
- 기각 대안: 활성 예약 자동 연쇄 취소(D-059의 상쇄 의미와 충돌), 선행 검사 없이 등록만 취소(잔여가 0이 됐는데 예약은 남아 있는 불일치 상태 발생). [policies §1 반영 완료]

## D-090. 예약 취소는 물리 삭제가 아니라 상태 전환

- 2026-08 / 예약 취소는 예약 행을 지우지 않고 상태 전환(`ACTIVE` → `CANCELED`)으로 처리한다. 취소한 타임의 재예약을 허용하므로 전체 유니크 제약 대신 `status = 'ACTIVE'` 부분 유니크 인덱스로 중복을 막는다. 변경(재예약)은 전용 엔드포인트 1개 + 단일 트랜잭션(취소 UPDATE + 새 행 INSERT)으로 처리하고, 원장에 `CANCEL_REFUND`(+1)와 `RESERVE`(−1) 2건 모두 남긴다. 같은 수업 종류 안에서만 변경 가능하다.
- 이유: "누가 언제 어떤 수업을 취소했는지"가 남아야 감사 가능성(Core Value)이 성립한다. Phase 3의 이용권 등록 취소(D-059)와 동일한 사고다.
- 기각 대안: 물리 삭제(감사 이력 소실), 전체 유니크 제약(재예약을 막아 정책과 충돌). [policies §3 반영 완료]

## D-091. 차감 대상 이용권 선택: 만료 임박순 단일 이용권, 합산 금지

- 2026-08 / 같은 종류 이용권을 여러 장 보유하면 `end_date` 오름차순(동률 시 `id` 오름차순) 첫 장에서 차감한다. **합산 금지**(0.5회 두 장으로 1회 예약 불가) — 예약 1건 ↔ 이용권 1장이 항상 대응하고, 예약 행이 차감한 `pass_id`를 보유한다. 유효기간 판정 기준일은 예약일이 아니라 **수업날**이다. 복구는 원래 차감한 그 이용권으로 하되, 만료 상태여도 복구하고 등록 취소(`CANCELED`) 상태면 복구하지 않는다.
- 이유: 만료 임박순 차감은 잔여 소진을 자연스럽게 유도한다. 합산 금지는 policies §3 "잔여 0.5회로는 1회 예약 불가"를 이용권 한 장 단위로 정확히 적용한 결과다. 수업날 기준 판정은 만료 직전 다음 달 수업을 미리 잡아 유효기간을 사실상 연장하는 우회로를 막는다. 만료돼도 복구하는 이유는 "잔여 = 이력 합계" 원장 불변식(Core Value) 유지 때문이고, 등록 취소된 이용권을 복구하지 않는 이유는 D-059의 취소 의미(잔여 0 상쇄)가 깨지기 때문이다.
- 기각 대안: 여러 장 합산 차감(이용권 1장과 예약 1건의 대응이 깨져 복구 대상 추적이 모호해짐), 등록일 임박순 차감(만료가 임박한 이용권이 방치돼 회원이 손해를 본다).

## D-092. 중복 예약 금지: 같은 회원의 같은 날짜·시각 이중 예약 방지

- 2026-08 / 같은 회원이 같은 날짜·같은 시각에 두 건을 예약할 수 없다. 같은 날 다른 타임 복수 예약은 제한 없음. 1:1(`LESSON`) 한도는 "날짜 + 시각" 단위로 1명이며 같은 시각의 예약제 정원과 무관하다.
- 이유: 1:1과 예약제 수업이 같은 타임에 동시 진행되더라도(policies §2, 코치 2명 체제) 한 사람이 동시에 두 수업을 들을 수는 없다. 이 검사가 없으면 실수로 두 이용권에서 헛수로 차감된다.
- 기각 대안: 검사 없이 서비스단 안내만(동시 요청 경쟁에서 막지 못함 — D-021과 같은 이유로 애플리케이션 조건문만으로는 보장 불가).

## D-093. 정기 시간표는 Flyway 시드 고정, 정원은 시간표에 귀속

- 2026-08 / 정기 시간표(`ClassSchedule`)는 Flyway 시드로 고정하고 관리자 CRUD API를 만들지 않는다(MVP 단일 지점). 정원은 `ClassSchedule`에 둔다(요일+시각 단위, 예약제 10 / 1:1 1). 저녁반(`EVENING`)도 행으로 정의하되 `capacity`는 NULL이고 예약 대상이 아니다.
- 이유: MVP는 송파점 1개 지점이라 시간표가 거의 바뀌지 않는다. CRUD API를 만들면 "이미 예약이 있는 타임의 시간표를 바꾸면?"이라는 별도 문제가 즉시 따라붙어 phase 범위가 커진다.
- 기각 대안: 관리자 CRUD API 선제작(사용 빈도 대비 구현·검증 비용 과다), 정원을 `ClassSession`에 둠(날짜마다 관리할 값이 늘어 운영 부담 증가).

## D-094. `ClassSession`은 필요할 때 생성한다 (사전 배치 생성 안 함)

- 2026-08 / `ClassSession`은 사전 배치 생성이 아니라 첫 예약 또는 휴강 처리 시 **필요할 때 생성**한다. 경쟁은 `(class_schedule_id, class_date)` 유니크 제약 + `INSERT ... ON CONFLICT DO NOTHING`으로 흡수한다. 시작·종료 시각과 정원을 시간표에서 복사해 자기 컬럼으로 보유한다(과거 수업이 시간표 수정에 소급되지 않도록).
- 이유: 배치 인프라는 Phase 5 소관이라 이 phase에서 배치를 앞당겨 만들면 책임 경계가 흐려진다. 무엇보다 배치가 하루라도 안 돌면 예약 자체가 막히는 구조적 결함을 필요 시점 생성이 원천적으로 없앤다.
- 기각 대안: 주 단위 사전 생성 배치(배치 인프라 선반영 필요 + 배치 미실행 시 예약 불가), `ClassSession`이 시간표를 참조만 함(시간표 수정이 과거 수업까지 소급 변경).

## D-095. 예약 창: 해당 주 월요일 오픈, 수업 시작 전 마감, 조회는 2주치

- 2026-08 / 오픈은 해당 주 월요일 00:00(`Asia/Seoul`), 마감은 수업 시작 시각 전까지. 조회 범위는 이번 주 + 다음 주 2주치이며 다음 주는 조회만 가능하다. 주 시작은 월요일, 판정은 `Clock` 빈 기준.
- 이유: 오프라인 결제 기반이라 오픈런이 생길 이유가 없어 "월요일부터" 한 줄 안내로 충분하다. 마감을 시작 시각 전까지로 두는 이유는 이미 시작·종료된 수업에 예약이 붙어 횟수가 사라지는 사고를 막기 위해서다.
- 기각 대안: 별도 오픈 시각 지정(오프라인 결제 기반 서비스에 불필요한 복잡도), 조회 범위를 1주로 제한(회원이 다음 주 일정을 미리 확인할 수 없어 UX 저하).

## D-096. 회원 시간표 응답: 저녁반 함께 노출, 예약 인원 숫자만 제공

- 2026-08 / 회원 시간표 응답은 저녁반도 함께 노출하되 예약 대상이 아님을 표현하고, 셀에는 예약 인원 숫자만 내려준다. **예약자 명단은 회원에게 주지 않는다** — 회원 간 개인정보 노출. 명단은 관리자 보드에서만 본다.
- 이유: 저녁반을 시간표에서 숨기면 회원이 전체 운영 시간을 파악할 수 없다. 명단 비공개는 회원 개인정보(누가 어떤 수업을 듣는지) 보호가 목적이다.
- 기각 대안: 저녁반 시간표에서 완전히 제외(전체 시간표 파악 불가), 예약자 명단 전체 노출(회원 간 개인정보 노출).

## D-097. 관리자 알림: 수신자는 관리자뿐, 스키마+레코드 생성까지만 이 phase 범위

- 2026-08 / 알림 수신자는 관리자 하나뿐이다(회원용 알림 없음). 이벤트 4종(회원 예약/취소·변경/휴강 처리/관리자 대리 취소·변경)에 대해 `NotificationType` 6종을 기록하며, 이 phase는 스키마 + 레코드 생성까지만 구현한다(조회·확인·폴링은 Phase 6). 조회 시점 조인이 필요 없도록 회원명·수업 종류·수업 일시를 비정규화해 담는다. **휴강 알림은 세션당 1건 요약형**이라 `Notification`이 `reservation_id` 외에 `class_session_id`(nullable) 참조를 갖는다.
- 이유: 요구사항(NOTIF-01~03)에 회원 알림 조회 경로가 없고 MVP는 푸시 제외(requirements §5)다. 휴강 시 취소 건별로 N건씩 알림을 만들면 관리자 알림함이 폭증하고 Phase 6의 미확인 카운트를 왜곡하므로 세션당 1건 요약형으로 묶는다. 비정규화하는 이유는 나중에 회원명이 바뀌어도 "그때 그 알림"이 그대로 남아야 알림·활동 피드의 의미에 맞기 때문이다.
- 기각 대안: 휴강 취소 건별 알림 N건 생성(알림함 폭증, 미확인 카운트 왜곡), 조회 시점 조인으로 표시 정보 계산(회원명 변경 시 과거 알림 내용이 바뀌는 부작용).

## D-098. 동시성 검증 방법: JVM 동시성 통합테스트로 "초과 예약 0건" 증명, k6는 이번 phase 범위 밖

- 2026-08 / "초과 예약 0건" 증명은 JVM `ExecutorService`+`CountDownLatch` 동시성 통합테스트로 한다(conventions §10.4 · `add-domain-test` 스킬과 동일 방식). k6 부하테스트는 HTTP 레벨 부하·처리량 도구로 목적이 다르고 새 인프라를 요구해 이번 phase 범위에서 제외한다. **[사용자 확정, 2026-08-07]**
- 이유: 이 저장소에 k6 도입 전례가 없고, 기존 `add-domain-test` 스킬은 이미 JVM 동시성 통합테스트만 규정한다. 목적은 "정확히 정원 수만큼만 성공"을 증명하는 정확성 테스트이지 처리량 측정이 아니다.
- 기각 대안: k6 부하테스트 도입(스크립트·실행 환경 등 새 인프라가 필요해 phase 규모가 커짐, 목적이 정확성이 아닌 처리량 측정이라 부적합).

## D-099. FE `AdminLayout`은 `components/layout/`이 아니라 `app/`에 둔다

- 2026-08 / 관리자 보호 구역의 승인 대기 뱃지 쿼리(`pendingMemberCountQuery`)를 소유하는 레이아웃 라우트를 `src/app/AdminLayout.tsx`에 만든다. `AdminShell`은 계속 프레젠테이션 컴포넌트로 남고, 뱃지 건수·활성 판정은 `items` prop으로 주입받는다. **[FE M3 03-09 실행 시 확정]**
- 이유: `AdminLayout`은 라우트 조립물이지 재사용 셸이 아니다 — 라우트 트리 밖에서 쓸 일이 없어 `components/layout/`(재사용 셸)의 의미와 맞지 않는다. 셸이 직접 `useQuery`를 부르면 스토리북에서 셸을 데이터 없이 렌더할 수 없고, 부가 조회 실패가 내비 전체를 깨뜨린다.
- 기각 대안: `components/layout/AdminLayout.tsx`(재사용 셸이 아닌데 셸 폴더에 섞여 경계가 흐려짐), `AdminShell`이 직접 조회(스토리북에서 데이터 없이 렌더 불가 — Phase 1이 `items` 주입 구조를 만든 이유를 무효화).

## D-100. `ResponsiveDialog` 도입 확정 (D-077이 미룬 것을 M3에서 이행)

- 2026-08 / D-077이 "두 번째 사용처가 생기는 시점(M3 예상)"으로 미뤄둔 `ResponsiveDialog`(모바일 Sheet ↔ 데스크탑 Dialog 전환)를 M3에서 `components/common/`에 도입한다. **[FE M3 UI-SPEC 확정, 03-06 구현]**
- 이유: M3 관리자 화면에서 사유 입력 다이얼로그·이용권 등록 폼 등 실사용처가 복수로 확보됐다. D-077의 "두 번째 사용처가 생겼을 때 올린다" 조건이 충족됐다.
- 기각 대안: 화면마다 Dialog/Sheet 분기 중복(반응형 계약이 화면마다 갈라져 회귀 고정 불가), 데스크탑 Dialog만 사용(모바일에서 폼 다이얼로그의 입력 영역이 좁아 운영 도구로 쓰기 어렵다).

## D-101. 관리자 스케줄 보드의 `branchId` 해석: `admin_branch` 매핑 우선, 없으면 단일 지점 대체

- 2026-08 / `GET /api/admin/schedule/board`가 `branchId`를 명시받으면 `admin_branch` 매핑으로 그 관리자가 실제 소속인지 검증하고, 아니면 `ADMIN_BRANCH_NOT_ASSIGNED`(403)로 거부한다(T-04-53). `branchId`를 생략하면 관리자의 첫 지점을 쓰되, `admin_branch` 매핑이 비어 있으면(`AdminSeeder`가 v1에서 이 매핑을 만들지 않기로 한 기존 결정 — 지점이 하나뿐이라 매핑 없이도 지장이 없었다) 시스템에 지점이 정확히 하나일 때만 그 지점으로 대체한다.
- 이유: 04-11-PLAN이 "admin_branch 조회 경로가 있으면 쓰고, 없으면 branchId 파라미터 + 소속 검증"을 명시했는데, admin_branch가 스키마만 있고 실제로 채워진 적이 없어(v1 단일 지점 가정) 두 경로를 절충해야 했다. 명시적 `branchId`는 항상 매핑을 요구해 다지점 확장 시 타 지점 열람을 막고(T-04-53), 생략 시에는 기존 시드 관리자가 이 기능을 즉시 쓸 수 있도록 단일 지점 대체를 남겨 둔다.
- 기각 대안: `admin_branch` 매핑 없이 항상 첫 Branch로 대체(다지점 확장 시 인가 우회 경로가 됨), 매핑이 없으면 무조건 500/거부(기존 시드 관리자가 이 phase가 만든 기능을 전혀 쓸 수 없게 됨).

## D-102. 휴강 처리·해제도 지점 소속을 검증한다 (D-101을 변경 엔드포인트로 확장)

- 2026-08 / `POST /api/admin/class-sessions/suspension`과 `.../{id}/resumption`이 대상 수업의 소속 지점(`ClassSchedule.branch`)에 대해 호출 관리자의 소속을 검증하고, 아니면 `ADMIN_BRANCH_NOT_ASSIGNED`(403)로 거부한다. 판정 규칙은 D-101과 동일하다 — `admin_branch` 매핑이 있으면 매핑으로 판정하고, 매핑이 하나도 없으면 시스템에 지점이 정확히 하나일 때만 허용한다.
- 이유: D-101이 조회(GET 스케줄 보드)에만 적용돼, **읽기는 지점을 확인하는데 쓰기는 확인하지 않는** 비대칭이 있었다(PR #11 리뷰 Warning). v1은 지점이 하나뿐이라 실제로 뚫리는 경로는 아니지만, 지점이 늘어나는 순간 `classScheduleId`만 알면 타 지점 수업을 휴강시켜 예약 N건을 자동 취소하고 차감을 복구시킬 수 있다 — 조회보다 파급이 큰 쪽이 먼저 열려 있는 상태였다.
- 기각 대안: `resolveBranchId`를 그대로 재사용(지점이 이미 대상 수업으로 정해져 있어 "해석"이 필요 없고, `branchId` non-null 경로는 매핑을 강제해 매핑 없는 현재 관리자 전원이 휴강 기능을 못 쓰게 된다), 다지점 phase까지 미루고 이슈로만 남기기(엔드포인트가 늘어날수록 누락 지점이 늘고, 그때 한 번에 훑는 비용이 지금 한 줄 추가보다 크다).
- 범위 밖: `AdminReservationController`(RESV-07/08 예약 조회·대리 취소/변경)는 이번에도 검증하지 않는다 — 해당 컨트롤러 KDoc이 "다지점 운영이 시작되면 `resolveBranchId`와 같은 방식으로 확장한다"고 이미 밝혀 둔 의도적 스코프이고, 예약은 시간표와 달리 `branchId`를 직접 갖지 않아 회원→지점 경유 판정이 필요해 설계가 별건이다.
