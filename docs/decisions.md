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
- **확장**: Phase 5에서 기준일 후보를 5종으로 확장하고 회원 단위 판정을 명시 (D-105)

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

## D-103. FE `ui/dialog.tsx`·`ui/sheet.tsx`의 sr-only 닫기 라벨을 한국어로 고친다

- 2026-08 / shadcn 생성물의 `<span className="sr-only">Close</span>`를 `닫기`로 바꾼다. 구조·동작·클래스는 손대지 않고 문자열 한 줄만 바꾼다. 생성물이라 재생성하면 덮어써지므로 이 결정이 "다시 적용해야 한다"는 근거로 남고, 각 줄 위에 `D-103` 주석을 남겨 재생성 후 diff에서 눈에 띄게 한다. **[FE M3 후속 수정, 03-REVIEW IN-06]**
- 이유: "UI 텍스트는 한국어" 규약(CLAUDE.md / design-system) 위반이고, M3의 다이얼로그 7개가 전부 이 문자열을 상속한다 — 스크린 리더 사용자가 전부 한국어인 플로우 중간에 "Close" 하나만 영어로 듣는다. 래퍼(`ResponsiveDialog`)는 `DialogContent` 바깥이라 이 span에 닿을 수 없다(03-06-SUMMARY가 그래서 미뤘다).
- 기각 대안: `showCloseButton={false}`로 X 버튼 제거(ESC와 본문의 `닫기` 버튼이 남지만 마우스 사용자의 관례적 탈출구가 사라진다), 그대로 두기(접근성 계약 위반이 남고 다이얼로그가 늘 때마다 복제된다).

## D-104. FE `Button variant="destructive"`를 solid fill로 바꾼다

- 2026-08 / `ui/button.tsx`의 `destructive` variant를 `bg-destructive/10 text-destructive` 연한 틴트에서 `bg-destructive text-white hover:bg-destructive/90` solid fill로 바꾼다(`focus-visible:*` destructive 링은 유지, solid와 모순되는 `dark:bg-destructive/20 dark:hover:bg-destructive/30`은 제거). `--destructive-foreground` 토큰이 없어 전경색은 `text-white`를 쓴다. 적용 대상은 `Button variant="destructive"`뿐이고 `DropdownMenuItem variant="destructive"`(이용권 `등록 취소` 메뉴 항목)는 생성물의 별도 처방이라 손대지 않는다. **[FE M3 후속 수정, 03-UI-REVIEW Top-3]**
- 이유: 되돌릴 수 없는 제출 버튼(`가입 거절`, `등록 취소`)이 본문에서 "되돌릴 수 없습니다"라고 말하면서 시각적으로는 연한 칩으로 읽혀 카피와 신호가 어긋난다. design-system SKILL.md의 색 표가 이미 "파괴적 액션 → `bg-destructive`"라고 적고 있어, 이 변경은 새 규약이 아니라 코드와 문서의 불일치 해소다. destructive는 파괴적 액션에만 쓰이므로 "화면당 primary 1개" 규약과도 충돌하지 않는다.
- 기각 대안: 호출부(`ReasonDialog`)에서 className 보정(비가역 액션이 늘 때마다 반복되고 계약이 화면마다 갈라진다), border만 추가(대비 개선 폭이 작아 카피와 시각 신호의 어긋남이 그대로 남는다).

## D-105. 미사용 차감 기준일을 5종 후보의 max로 확장, 판정은 회원 단위 (D-027 확장)

- 2026-08 / 2주 미사용 차감(policies §4.3)의 기준일을 **회원 단위**로,
  max(① 마지막 출석일, ② 마지막 **취소되지 않은** 예약의 수업일 — 수업 종류 무관,
  ③ `ON_LEAVE`에서 **벗어난 시각**(대상 상태 무관, CR-04·D-111 정정), ④ `SESSION_PASS` **등록일**(`created_at`, 시작일 아님),
  ⑤ 마지막 **양(+) `ADMIN_ADJUST` 일자**)로 정한다. 복귀일은 휴회 기간 테이블 대신
  회원 상태 전환 시각 기록(새 마이그레이션)으로 구현하며, 과거 전환 이력은 데이터가 없어
  후보에서 자연 제외된다.
- 이유: 복귀일·+가감일 후보는 **"차감 제외 기간(휴회·잔여 0)에는 부채가 쌓이지 않는다"** 의
  구현이다 — 없으면 복귀·충전 직후 배치가 밀린 주기를 몰아서 소급 몰수한다. 등록일(≥시작일)
  fallback은 과거 시작일 등록(D-055) 직후 즉시 차감을 구조적으로 막는다. 취소 예약 제외는
  D-027의 근거("예약은 이미 차감된 '사용'")와 일치한다 — 취소는 복구(환불)되어 사용이 아니다.
- 기각 대안: 휴회 기간 테이블(상태 전환 시각 기록으로 충분, 과잉 설계), 시작일 fallback(소급
  차감), 취소 예약 포함(환불받은 예약이 차감을 막는 비대칭), 충전 후 소급 차감 허용(관리자가
  서비스로 준 횟수가 곧바로 깎임), SESSION 예약만 활동 인정(실제로 체육관에 나오는 회원을
  차감하는 결과 — 취지 위배).
- 보강(2026-08-15, 사용자 확정): 기준일 후보 ④(`SESSION_PASS` 등록일)를 다장 보유 회원에게
  적용하는 범위는 **차감 가능한 장**(취소·만료 아님, 잔여 > 0) 중 최신 `created_at`이다 —
  새 장 등록(충전)이 시계를 리셋하는 +가감일 원리와 일관하며, 이미 못 쓰는 옛 이용권의 등록일이
  기준일을 부당하게 최신으로 만드는 것을 막는다.
- 수용한 트레이드오프: 짧은 휴회 토글로도 유예가 리셋되는 관대함 — 휴회는 관리자만 설정하므로
  악용 위험이 낮다. 악용이 관찰되면 재논의.

## D-106. 미사용 차감 배치는 상태 기반 부족분 채우기 — 캐치업이 곧 멱등성

- 2026-08 / 배치는 실행 횟수가 아니라 상태를 본다: 회원별로 **"현재 존재해야 할 차감 수
  (`floor(기준일→오늘 경과일 / 14)`) − 기준일 이후 실제 `INACTIVITY` 이력 건수 = 부족분"** 을
  계산해 부족분만 차감한다. 이력 건수는 이벤트 수로 센다(부분 차감 0.5도 1회).
- 이유: 같은 날 중복 실행·관리자 수동 실행·재기동 중복이 전부 자연 멱등이고, 배치가 며칠
  중단돼도 다음 실행이 밀린 주기를 몰아서 차감한다(policies "4주 미사용 = 2회"와 정합).
  멱등성과 캐치업이 별도 장치 없이 한 메커니즘으로 해결된다.
- 기각 대안: 실행 이력 기반 "오늘 이미 실행했나" 가드(캐치업이 안 되고, 이력 유실 시 이중
  차감), 이용권별 차감 예정일 컬럼 관리(원장과 별도의 상태가 생겨 진실 원천 이원화).

## D-107. 유효기간 만료의 EXPIRED 영속화를 만들지 않는다 — BATCH-03은 D-064로 충족

- 2026-08 / 만료는 D-064의 조회 시점 계산(`displayStatus`)을 유일한 진실 원천으로 유지한다.
  Phase 4 예약 경로가 수업날 기준으로 만료 이용권을 이미 거부하고, 배치는 만료 이용권을
  `INACTIVITY` 대상에서 제외만 한다. BATCH-03(만료 사용 불가 처리)은 새 구현물 없이
  "만료 이용권 예약 불가 + 차감 제외"를 검증 테스트로 실증해 충족 처리한다.
- 이유: 같은 사실(만료 여부)의 원천을 두 개 만들지 않는다. 영속 상태를 두면 유효기간 수정으로
  만료를 해제(D-056)할 때 상태 되돌리기가 따라오고, 배치 실행 전까지 상태가 어긋나는 창이 생긴다.
- 기각 대안: EXPIRED 상태 전환 배치(진실 원천 이원화 + 해제 경로 복잡화).

## D-108. 배치 실행은 앱 내 @Scheduled + 실행 이력 테이블 + 관리자 수동 실행 API

- 2026-08 / 매일 새벽 `@Scheduled`(cron, `Asia/Seoul`)로 실행하고, 실행 이력 테이블
  (시각·처리 건수·결과)을 남기며, 관리자 수동 실행 API 1개를 연다 — D-106의 상태 기반 설계
  덕에 수동 실행·중복 실행이 안전하다. 실행 이력은 관측·복구 판단용이며 멱등성의 근거가 아니다.
- 이유: 단일 EC2 인스턴스 전제라 ShedLock 등 분산 락은 **일부러 쓰지 않는다** — 필요 없는
  인프라를 미리 들이지 않는다. 배치가 안 돌았는지는 실행 이력으로 확인하고, 복구는 수동 실행
  API로 한다(캐치업이 자동 보정).
- 기각 대안: 외부 트리거(시스템 cron·EventBridge — 새 인프라, M7에서 재검토), Spring Batch
  프레임워크(청크·재시작 기능이 이 규모에 과잉), ShedLock(다중 인스턴스가 아닌데 의존성 추가).
- 보강(2026-08-15, 사용자 확정): 경쟁 패배로 스킵된 회원이 있어도 실행 결과는 `SUCCESS`이며
  스킵 건수를 별도 컬럼(`skipped_count`)에 기록한다 — 스킵은 다음 실행이 상태 기반으로
  보정하는 정상 동작이라 실패로 집계하지 않는다.
- **정정(2026-08-15, 05-REVIEW.md CR-01)**: 위 "중복 실행이 안전하다"는 **순차 재실행에만**
  참이다. 동시 실행에는 성립하지 않는다 — 두 실행이 원장을 동시에 읽으면 각자 "부족분 1"로
  판단하고 둘 다 차감에 성공한다. `PassRepository.adjustRemainingCount`의 조건부 UPDATE는
  `remainingCount + :amount >= 0`(잔여 음수 방지)만 검사하고 "같은 주기에 이미 차감됐는가"는
  검사하지 않기 때문이다. **"단일 EC2라 분산 락이 필요 없다"는 위 이유도 불충분하다** — 이
  경쟁은 인스턴스 *간*이 아니라 한 JVM 안에서 스케줄러 스레드와 Tomcat 스레드(수동 실행)가
  겹칠 때 발생하므로 인스턴스가 하나여도 일어난다. 갭 클로저에서 차감 원자성 보장 수단을
  정하고 이 결정을 다시 쓴다.
- **해소(2026-08-16, 05-11~05-13)**: 위 정정이 지적한 동시 실행 이중 차감은 닫혔다.
  실행 1회가 `batch_execution`에 `RUNNING` 행을 먼저 넣고 종료 시 확정하며, 그 행의 유일성을
  V10 부분 유니크 인덱스(`uq_batch_execution_running`, D-117)가 보장한다 — 겹친 두 번째 실행은
  INSERT 단계에서 거부돼 409 `BATCH_ALREADY_RUNNING`(D-118)이 되고 **본문이 아예 돌지 않는다.**
  실증 근거는 `InactivityBatchRunConcurrencyTest`, `AdminBatchRunConcurrencyTest`다:
  전자는 네 스레드가 동시에 `run()`을 호출해도 `INACTIVITY` 이력이 정확히 1건이고 실행
  이력들의 `deductedCount` 합도 1임을, 후자는 동시 POST에서 202가 정확히 1건·나머지는
  409임을 HTTP 계층에서 실제 PostgreSQL로 각각 단언한다.
  경쟁에서 진 쪽이 **거부되지 않고 뒤늦게 시작하는 경우에도** 총 차감은 1회다 — 그때는 원장에
  이미 이력이 있어 부족분 0이 계산되기 때문이다(D-106). 두 경로 모두 같은 불변식으로 수렴한다.
- **"단일 EC2라 분산 락이 필요 없다"는 결론은 유지하되 근거가 바뀌었다(2026-08-16).**
  더 이상 "인스턴스가 하나여서"가 아니라 **"DB 제약이 인스턴스 수와 무관하게 막아서"**다.
  그래서 다중 인스턴스로 확장해도 같은 보장이 유지되며 ShedLock 도입 필요성은 생기지 않는다.
  다만 이 설계에는 알려진 약점이 하나 있다 — 앱이 급종료하면 `RUNNING` 행이 남아 배치를 영구히
  막는다. 임계 시간(기본 30분)을 넘긴 행을 `FAILED("STALE")`로 회수해 대응한다(D-117·D-118).
- **전체 실패도 이력에 남는다(2026-08-16, WR-02)**: 위 "배치가 안 돌았는지는 실행 이력으로
  확인한다"는 예전에는 반쪽만 참이었다 — 이력을 종료 시점에 한 번 저장했으므로 배치가 도중에
  터지면 이력이 한 줄도 남지 않아 "안 돌았다"와 "돌다가 터졌다"가 구분되지 않았다. 이제 시작
  시점에 행이 먼저 생기고 실패 경로도 `FAILED`로 확정되므로 두 상황이 구분된다.

## D-109. 미사용 판정은 회원 단위, 차감은 만료 임박 한 장에서 부분 차감 허용

- 2026-08 / 판정(기준일·부족분)은 회원 단위로 하고, 차감 1회는 그 회원의 차감 가능한
  `SESSION_PASS`(취소·만료 아님, 잔여 > 0) 중 **유효기간 만료가 가장 임박한 한 장**에서 한다
  (D-091 선택 규칙 준용). **잔여가 차감량(1)보다 적으면 잔여만큼만 차감한다**(0.5 → 0).
  부족분을 다른 장으로 이월하지 않으며, 부족분이 여러 회면 회당 대상을 재선택한다.
- 이유: 미사용은 회원의 활동(출석·예약)으로 판정되는 개념이라 이용권별 판정은 여러 장 보유
  회원을 장 수만큼 중복 차감한다. 부분 차감은 "잔여 0 제외" 예외와 연속되는 규칙이고
  (0.5 남은 회원도 미사용이면 0으로), 이월 금지는 합산 금지(D-091)·"한 이벤트 한 장" 대응과
  일관된다.
- 기각 대안: 이용권 단위 판정(다장 보유 시 중복 차감), 부족분 타 장 이월(합산 금지 위배),
  잔여 부족 시 차감 스킵(잔여 0.5가 영구히 안 깎여 "잔여 0 제외"와 부정합).

## D-110. `pass_transaction` 시스템 주체는 CHECK를 "정확히 하나"에서 "최대 하나"로 완화해 표현한다

- 2026-08 / V9에서 `ck_pass_transaction_subject`를 DROP하고
  `CHECK (NOT (admin_id IS NOT NULL AND member_id IS NOT NULL))`로 재정의한다. 배치(`INACTIVITY`)
  이력은 `admin_id`·`member_id`가 **둘 다 NULL**이며 이것이 "시스템 주체"의 표현이다. 기존 행은
  전부 한쪽만 채워져 있어 백필이 필요 없다 (커밋된 V8은 수정하지 않고 새 버전으로 완화한다).
- 이유: 배치가 새 주체 종류를 추가할 때 기존 두 컬럼의 "둘 다 없음" 상태로 시스템 주체를
  표현하면 스키마 변경을 최소화하면서 감사 추적("누가 이 행위를 했는가")을 확장할 수 있다.
- 기각 대안: `actor_type` 판별 컬럼 추가(기존 두 컬럼의 의미가 그대로인데 판별 컬럼이 생겨
  주체 표현이 이원화된다), 시스템 관리자 계정을 만들어 `admin_id`에 대입(`admin`의 의미가
  "이 행위를 한 실제 관리자"에서 오염되고 감사 추적에서 배치와 사람이 구분되지 않는다).

## D-111. 휴회 복귀 시각은 `member.returned_from_leave_at` 단일 목적 컬럼으로 기록한다

> **2026-08-31 이후 주의**: 이 컬럼은 D-147(WR-06)에서 `deduction_exclusion_exited_at`으로
> 리네임됐고(V12), 의미도 "휴회 이탈"에서 "차감 제외 상태(`ON_LEAVE`·`INACTIVE`) 이탈"로
> 확장됐다. 아래 본문은 리네임 전 시점의 기록이다.

- 2026-08 / `ON_LEAVE` → `ACTIVE` 전이에서만 갱신하고 다른 전이(승인 `PENDING`→`ACTIVE`, 재활성화
  `INACTIVE`→`ACTIVE`, 휴회 시작)에서는 손대지 않는다. `Reservation.canceledAt`과 같은 "그 사건이
  일어난 시각만" 기록하는 관례를 따른다.
- 이유: D-105 기준일 후보 ③(복귀일)이 정확히 이 전이만을 가리켜야 하므로, 범용 컬럼보다
  단일 목적 컬럼이 판정 로직을 단순하게 유지한다.
- 기각 대안: 범용 `status_changed_at`(마지막 전환 시각만 남고 이전 상태를 알 수 없어 "휴회에서
  복귀한 것인지" 판정 불가), 휴회 기간 테이블(D-105가 이미 기각).
- **정정(2026-08-16, CR-04, 05-REVIEW.md 근거):** 위 "`ON_LEAVE`→`ACTIVE` 전이에서만 갱신"은
  틀렸다. 실제 운영에서는 `ON_LEAVE→INACTIVE→ACTIVE`·`ON_LEAVE→PENDING→ACTIVE`처럼 `ACTIVE`를
  거치지 않고 우회하는 경로가 있는데, 그 경로에서는 기록이 누락돼 기준일이 휴회 시작 이전으로
  되돌아가 휴회 기간 전체가 소급 차감됐다(6개월 휴회 복귀 시 최대 12회). `AdminMemberService.changeStatus`의
  기록 조건을 `previousStatus == ON_LEAVE && newStatus == ACTIVE`에서
  `previousStatus == ON_LEAVE && newStatus != ON_LEAVE`로 확장했다 — "`ACTIVE`로 복귀했을 때"가
  아니라 "`ON_LEAVE`에서 벗어났을 때"가 정확한 의미다. 단일 목적 컬럼이라는 이 결정의 본체(범용
  `status_changed_at`을 쓰지 않는다)는 그대로 유지된다 — 바뀐 것은 "언제 채우는가"의 조건뿐이다.
  **커밋된 V9 마이그레이션의 "`ON_LEAVE → ACTIVE` 전이에서만 기록한다" 주석은 수정하지 않는다**
  (Flyway 체크섬 불일치로 기동이 막힌다) — 정정 사실은 V10 마이그레이션 헤더 주석에 남긴다.

## D-112. 미사용 차감 배치의 트랜잭션 경계는 "회원 1명 = 트랜잭션 1개"다

- 2026-08 / 루프를 도는 `InactivityBatchRunner`에는 `@Transactional`을 붙이지 않고, 회원 1명분
  차감 반영(`InactivityDeductionService.deductOnce`)만 **별도 스프링 빈**의 `@Transactional`
  메서드로 둔다. 회원 단위 예외는 루프가 `try-catch`로 흡수해 실행 이력에 집계하고 나머지 회원은
  계속 처리한다.
- 이유: D-020의 취지는 "차감과 이력이 원자적"이지 "배치 전체가 원자적"이 아니다. 회원 단위
  트랜잭션이라야 한 회원의 실패가 다른 회원 처리를 막지 않는다.
- 표현 보강(2026-08-15): 제목의 "회원 1명 = 트랜잭션 1개"는 **실패 격리 단위**를 가리킨다.
  실제 트랜잭션 개수는 `deductOnce` **호출 1회당 1개**이고, 회당 재선택(D-109) 때문에 부족분이
  2회인 회원은 트랜잭션을 2개 쓴다. 두 문장은 충돌하지 않는다 — 격리 단위가 회원이고, 원자성
  단위가 차감 1회다.
- 기각 대안: 배치 전체를 한 트랜잭션으로(150번째 회원의 실패가 앞선 149명의 차감까지
  롤백시키고, 같은 클래스 내부 호출은 프록시를 우회해 트랜잭션 경계가 생기지도 않는다).
- **보강(2026-08-16, 05-12·05-13)**: 실행 이력이 "시작 시 삽입 → 종료 시 확정"으로 바뀐 뒤에도
  `InactivityBatchRunner`에는 **여전히 `@Transactional`이 없다.** 시작·확정 기록만 별도 빈
  `BatchExecutionRecorder`의 `Propagation.REQUIRES_NEW` 트랜잭션에서 일어난다.
  이 분리가 없으면 두 가지가 동시에 깨진다.
  - 러너에 트랜잭션을 붙이면 한 회원의 실패가 전원을 롤백해 위의 실패 격리가 사라진다.
  - `REQUIRES_NEW`가 아니면(호출부 트랜잭션에 참여하면) `RUNNING` 행이 배치가 끝날 때까지
    커밋되지 않아 다른 실행에게 보이지 않는다 — `uq_batch_execution_running`이 발동하지 못해
    D-117의 실행 직렬화가 아예 성립하지 않는다.
  기록자를 러너 안의 메서드로 두고 애노테이션만 붙이는 것도 안 된다 — 같은 클래스 내부 호출은
  스프링 프록시를 거치지 않아 애노테이션이 **조용히 무시된다**(self-invocation).

## D-113. 배치 실행 이력 `batch_execution` 스키마와 스킵 집계

- 2026-08 / 컬럼: `trigger_type`(SCHEDULED/MANUAL), `triggered_by_admin_id`(MANUAL일 때만
  non-null, CHECK로 강제), `started_at`/`finished_at`, `processed_member_count`,
  `deducted_count`, `skipped_count`, `status`(SUCCESS/PARTIAL_FAILURE), `error_summary`.
  경쟁 패배·대상 소진으로 인한 스킵은 정상 경로라 `SUCCESS`로 두고 `skipped_count`에만 센다.
- 이유: 실행 이력은 관측·복구 판단용이며 멱등성 판단에는 절대 쓰지 않는다(D-106·D-108).
  상태 기반 설계가 예정한 정상 동작을 실패로 표시하면 운영자가 매일 오탐을 본다.
- 기각 대안: 스킵을 `PARTIAL_FAILURE`로 집계(운영 오탐 유발), 회원별 처리 상세 저장(원장이
  이미 이력을 갖고 있어 이중 기록).
- **갱신(2026-08-16, V10 / D-117)**: 실행 이력이 "종료 시 1건 INSERT"에서 "시작 시 INSERT →
  종료 시 확정"으로 바뀌면서 스키마가 세 곳 달라졌다.
  - `status`에 `RUNNING`(실행 중)과 `FAILED`(실행 전체 실패)가 추가됐다. `status`는 값 CHECK가
    없는 `VARCHAR(20)`이라 DDL 변경 없이 enum에만 추가하면 된다.
  - `finished_at`이 nullable이 됐다 — `RUNNING` 행은 아직 끝나지 않았다.
  - `triggered_by_admin_id`를 엔티티에서 `Admin` 연관(`@ManyToOne(LAZY)`)이 아니라 **스칼라
    필드**로 매핑한다(05-REVIEW.md WR-04). 실행 이력을 트랜잭션 밖에서 응답 DTO로 변환하는
    경로에서 `LazyInitializationException`이 날 여지를 없앤다. FK와 CHECK는 DB에 그대로 남아
    무결성은 계속 DB가 보장한다.

## D-114. cron은 매일 04:00 `Asia/Seoul`, 수동 실행은 `POST /api/admin/batch/inactivity-runs` 1개

- 2026-08 / `@Scheduled(cron = "0 0 4 * * *", zone = SEOUL_ZONE_ID)`. 새벽 4시는 예약·차감
  트래픽이 사실상 없는 시간대다. 수동 실행 API는 요청 본문 없이 `BatchExecutionResponse`를
  반환하고, 인가는 기존 `/api/admin/**` → `hasRole("ADMIN")` 규칙을 그대로 상속한다(D-040) —
  SecurityConfig를 수정하지 않는다. 새 에러코드는 추가하지 않는다.
- 이유: D-106 상태 기반 설계가 "중복 실행" 같은 실패 경로를 부족분 0으로 흡수하므로 거부할
  요청이 없다.
- 기각 대안: 실행 파라미터(기준일 override 등)를 받는 API(테스트 목적의 소급 차감 경로가 열려
  원장이 오염된다).
- **정정(2026-08-15, 05-REVIEW.md CR-01)**: 위 이유의 "중복 실행을 부족분 0으로 흡수한다"는
  **순차** 재호출에만 참이다 — 동시 호출은 흡수되지 않고 이중 차감이 된다(상세는 D-108 정정).
  `@Operation` description도 이에 맞춰 순차/동시를 구분하도록 고쳤고 `openapi.yaml`을 재생성했다.
- **정정(2026-08-16, D-118)**: 위 결정의 **"새 에러코드는 추가하지 않는다"는 철회한다.** 그 문장의
  근거("거부할 요청이 없다")가 바로 앞 정정으로 무너졌기 때문이다 — 동시 호출은 거부해야 한다.
  `ErrorCode.BATCH_ALREADY_RUNNING`(409)이 추가됐고 근거는 D-118에 있다. 수동 실행 API의 상태코드·
  응답 형태도 202 Accepted + 실행 상태 조회로 바뀐다(05-GAP-CONTEXT §2.4, 05-15).
- **갱신(2026-08-16, 05-REVIEW.md WR-05·IN-03)**: 수동 실행 API가 **엔드포인트 1개 → 3개**가 됐다.
  - `POST /api/admin/batch/inactivity-runs` — 실행을 **접수만** 하고 **202 Accepted** + `Location`
    (단건 조회 경로)을 즉시 반환한다. `RUNNING` 행 INSERT(직렬화 진입)만 동기로 하고 본문은 전용
    실행기에 넘긴다. 이미 실행 중이면 **409** `BATCH_ALREADY_RUNNING`이다.
  - `GET /api/admin/batch/inactivity-runs/{batchExecutionId}` — 진행 상태·결과 폴링.
    없으면 404 `BATCH_EXECUTION_NOT_FOUND`(신규 에러코드).
  - `GET /api/admin/batch/inactivity-runs?limit=` — 최근 실행 목록(기본 20, 1..100 보정).
    D-108의 "배치가 안 돌았는지는 실행 이력으로 확인한다"를 실제로 가능하게 만든다.
- 이유: 기존 동기 호출은 배치가 끝날 때까지 요청 스레드를 붙잡아, 프록시·로드밸런서 타임아웃
  (흔히 60초)으로 관리자가 응답을 못 받고 재시도하게 만들었다 — **CR-01(동시 실행 이중 차감)의 가장
  현실적인 트리거**였다(WR-05). 가드(D-117·D-118)가 있어도 그 경로를 계속 밟으면 운영이 불편하고
  가드가 매번 발동한다. 이 변경은 그 경로 자체를 없앤다. `201`이 아니라 `202`인 이유는 이 호출이
  만드는 것이 도메인 리소스가 아니라 **아직 끝나지 않은 작업**이기 때문이다.
- 실행기: `BatchExecutorConfig`의 **전용 단일 스레드 빈**(`inactivityBatchExecutor`, 큐 1)이다.
  스프링 기본 실행기에 암묵적으로 얹지 않는다 — 배치가 요청 처리용 풀을 잠식하면 무관한 API 응답이
  함께 느려지고, 어느 풀에서 도는지 로그로 추적할 수 없다. 스레드 1개로 충분한 이유는 어차피
  `uq_batch_execution_running`이 동시 실행을 막기 때문이다. 부트의 `applicationTaskExecutor`가
  `@ConditionalOnMissingBean(Executor)` 조건이라 `defaultCandidate = false`로 등록해 기본 실행기를
  밀어내지 않게 했고, 그 사실을 `AdminBatchControllerTest`가 단언한다.
- 문서: `openapi.yaml`에 남아 있던 **잘못된 안전성 서술**(동시 실행이 위험하다는 경고와 "응답을
  못 받아도 재호출하지 말라"는 안내)은 이 변경으로 **거짓이 되어 전면 재작성**했다 — 이제 겹치면
  409로 거부되고, 애초에 응답을 기다릴 일이 없다(FE가 이 파일로 타입과 동작을 정한다).
  IN-03(POST가 200을 반환하고 실패 응답이 명세에 없던 문제)도 `@ApiResponses`로 202·401·403·404·409를
  명시하면서 함께 정리됐다.
- 실증: `AdminBatchRunConcurrencyTest`가 동시 POST에서 **202 정확히 1건 / 나머지 409**를 실제
  PostgreSQL로 단언한다. 결정론은 실행기 빈을 무동작 모의로 대체해 확보한다 — 실제 실행기를 쓰면
  첫 실행이 두 번째 요청보다 먼저 끝나 둘 다 202가 될 수 있다.

## D-115. 배치 벌크 조회는 인터페이스 스칼라 프로젝션(`common/projection`)으로 반환한다

- 2026-08 / 회원 id ↔ 날짜/타임스탬프 쌍을 `GROUP BY`로 가져오는 배치 조회는 `Array<Any>` 캐스팅
  대신 `MemberDateProjection`/`MemberTimestampProjection`(`common/projection`) 인터페이스
  프로젝션을 반환한다. JPQL `as memberId`/`as date`/`as timestamp` 별칭이 getter 이름과 일치하면
  Spring Data JPA(4.1.0, context7로 Tuple 기반 매핑 확인)가 프록시로 매핑한다.
- 이유: `pass`·`reservation`·`member` 세 패키지가 함께 쓰는 첫 스칼라 프로젝션이라 타입 안전한
  형태를 이 저장소의 관례로 고정해 둔다 — 다음 phase가 형태를 다시 고르지 않게 한다.
- 기각 대안: `List<Array<Any>>` + 호출부 캐스팅(RESEARCH 초안) — 인덱스 오타가 컴파일 타임에
  잡히지 않는다.


## D-116. 미사용 차감 cron은 프로퍼티로 끌 수 있다 (`goldwrestling.batch.inactivity-scheduler-enabled`)

- 2026-08-15 / `InactivityBatchScheduler`에 `@ConditionalOnProperty`를 붙여
  `BATCH_INACTIVITY_SCHEDULER_ENABLED`로 빈 등록 자체를 막을 수 있게 한다.
  **[2026-08-17 정정 — D-121] 최초 결정의 "기본은 켜 둔다"(`matchIfMissing = true`)를 철회한다.**
  기본값을 `false`로 뒤집어 설정을 빠뜨리면 꺼지는 쪽으로 실패하게 했다(fail-safe). 근거는 D-121.
  **관리자 수동 실행 API는 이 값과 무관하게 계속 동작한다** — 끄는 것은 "자동 실행"뿐이다.
- 이유 ①(운영): 이 배치는 사람 개입 없이 회원 잔여를 깎는 유일한 경로다. 잘못 돌 때 코드 배포
  없이 즉시 멈출 수단이 없으면 매일 04:00마다 피해가 누적된다. 특히 최초 배포는 소급 차감
  위험(05-REVIEW.md CR-02)이 있어 꺼 둔 채 올리고 데이터를 확인한 뒤 켜는 순서가 안전하다.
- 이유 ②(테스트): 게이트가 없으면 모든 `@SpringBootTest`가 `@EnableScheduling`이 살아 있는
  컨텍스트를 띄워, CI가 04:00 `Asia/Seoul`을 걸치면 실제 배치가 Testcontainers DB의 잔여를 깎아
  재현되지 않는 실패를 만든다. `build.gradle.kts`의 테스트 태스크가 이 값을 `false`로 고정한다.
- 기각 대안: `src/test/resources/application.yml`로 끄기(main의 `application.yml`을 클래스패스에서
  통째로 가려 나머지 설정까지 날린다), `@Profile("!test")`(테스트가 `test` 프로파일을 쓰지 않아
  전 테스트 클래스에 `@ActiveProfiles` 추가가 필요), 스케줄러 메서드 안에서 플래그 검사(빈은
  여전히 등록돼 "트리거만 한다"는 계약이 깨지고 조건문이 스케줄러로 들어온다).
- 이 프로퍼티는 **CR-01(동시 이중 차감)의 해결책이 아니다** — 자동 실행을 끄면 진입점이 하나로
  줄어 위험이 낮아질 뿐, 관리자가 동시에 두 번 호출하는 경로는 그대로 남는다.

## D-117. 배치 실행 직렬화는 `batch_execution` `RUNNING` 행 + 부분 유니크 인덱스로 한다

- 2026-08-16 / 실행 시작 시점에 `status = 'RUNNING'`, `finished_at = NULL`인 행을 먼저 INSERT하고
  종료 시점에 같은 행을 `SUCCESS`/`PARTIAL_FAILURE`/`FAILED`로 확정한다. V10의
  `CREATE UNIQUE INDEX uq_batch_execution_running ON batch_execution (status) WHERE status = 'RUNNING'`
  가 **동시에 존재할 수 있는 실행 중 행은 최대 1건**임을 DB 수준에서 보장하고, 두 번째 실행의
  INSERT는 유니크 위반으로 거부된다(호출부는 이를 409로 변환한다).
- 이유 ①: D-021 "동시성은 DB 제약으로 막는다"와 같은 방식이면서, 제약이 걸리는 대상이
  **원장(`pass_transaction`)이 아니라 실행 이력**이라 되돌리기 비용이 V11의 `DROP INDEX` 한 줄이다.
  원장에 컬럼을 새로 만드는 방식과 달리 데이터에 영구 흔적이 남지 않는다.
- 이유 ②: "배치 전체 실패 시 이력이 한 줄도 안 남는다"(05-REVIEW.md WR-02)를 별도 설계 없이
  흡수한다 — 시작 기록이 먼저 남기 때문이다. `FAILED` 상태값도 이 구조에서 자연히 필요해진다.
- 이유 ③: 조회 후 판정(“지금 도는 배치가 있나?”)은 조회와 INSERT 사이에 경쟁 창이 남지만,
  유니크 인덱스는 그 창 자체가 없다. 애플리케이션 코드로는 같은 보장을 얻을 수 없다.
- 기각 대안:
  - *PostgreSQL advisory lock*: 마이그레이션이 0건이지만 락 상태를 운영에서 관측할 수 없고,
    세션 스코프 락은 커넥션 하나를 배치 내내 붙잡는 수명 관리를 직접 해야 한다.
  - *`pass_transaction` 주기 유니크 인덱스*: 키가 `(회원, 주기)`여야 하는데 원장의 `member_id`는
    소유자가 아니라 **조작 주체** 컬럼이고 `INACTIVITY`는 그 값이 NULL이다. 소유 회원 컬럼과 주기
    키 컬럼을 원장에 신설해야 하며, 인덱스를 지워도 그 컬럼은 영구히 남는다.
  - *회원 행 비관적 락*: 여기서 일어나는 것은 lost update가 아니라 **조건 재평가 없는 중복 실행**이라
    락을 걸어도 두 트랜잭션이 줄을 서서 둘 다 차감한다.
- 알려진 약점: 앱이 죽으면 `RUNNING` 행이 남아 배치가 영구 차단된다. `started_at`이 임계 시간
  (기본 30분)을 넘긴 `RUNNING` 행은 죽은 것으로 보고 `FAILED`(`error_summary = "STALE"`)로 정리한
  뒤 새 행을 넣는다 — 정리 쿼리(`BatchExecutionRepository.markStaleRunningAsFailed`)는 05-11이
  만들고, 실행 시작 경로에 배선하는 것은 05-12가 맡는다. 정리와 INSERT 사이 경쟁에서 진 쪽은
  409를 받는데 그것이 안전한 결과다.
- 확장 경로: 배치 종류가 하나뿐이라 `status` 단독 인덱스로 충분하다. 두 번째 배치가 생기면
  `batch_type` 컬럼 + `(batch_type, status)` 복합 부분 인덱스로 바꾼다.

## D-118. 배치 중복 실행은 409 `BATCH_ALREADY_RUNNING`으로 거부한다

- 2026-08-16 / 새 `ErrorCode.BATCH_ALREADY_RUNNING`(409 CONFLICT)과
  `BatchAlreadyRunningException`(`batch` 패키지)을 추가하고, 응답은 기존 규약대로 RFC 9457
  `ProblemDetail` + `code` 필드로 나간다(D-017·D-028). 던지는 지점은
  `BatchExecutionRecorder.start` 하나이며, V10 `uq_batch_execution_running` 위반
  (`DataIntegrityViolationException`)을 이 예외로 변환한다(D-117).
- 이유: D-114는 "새 에러코드는 추가하지 않는다"고 했고 그 근거는 "D-106 상태 기반 설계가 중복
  실행을 부족분 0으로 흡수하므로 거부할 요청이 없다"였다. **그 전제가 CR-01로 깨졌다** — 흡수는
  *순차* 재호출에만 성립하고, 동시 호출은 두 실행이 같은 부족분을 함께 읽어 이중 차감이 된다.
  거부할 요청이 생겼으므로 거부를 표현할 코드가 필요하다. 409를 고른 것은 conventions §8의 상태
  매핑("정원·중복·잔여부족 같은 상태 충돌 409")과 일치하며, 이 거부는 서버 오류가 아니라 **정상
  경로**다(관리자 더블클릭·타임아웃 후 재시도·cron 겹침은 예상된 상황이고 거부가 안전한 결과다).
- 기각 대안:
  - *거부 없이 재시도 안내만 문서화*: 관리자가 실제로 재시도하면 이중 차감이 그대로 난다 —
    안내는 동시성 방어가 아니다.
  - *중복 요청에도 202만 반환하고 조용히 스킵*: 관리자가 "실행됐다"고 오해한다. 실행 이력에도
    새 행이 없어 나중에 무슨 일이 있었는지 추적할 수 없다.
- 응답 본문에는 제약조건명(`uq_batch_execution_running`)·SQL을 담지 않는다(conventions §8).
  HTTP 계약(409 + `ProblemDetail` 본문 형태)의 검증은 컨트롤러가 바뀌는 05-15가 담당한다.

## D-119. 미사용 차감에 정책 시행일 하한과 1회 실행 상한을 둔다

- 2026-08-16 / 2주 미사용 차감(policies §4.3)에 두 가지 제한을 넣는다. 둘 다
  `InactivityBatchProperties`(prefix `goldwrestling.batch.inactivity`, D-118)의 설정값이라
  재배포 없이 환경변수로 되돌릴 수 있다.
  - **정책 시행일 하한** `policy-effective-date`, 기본 `2026-09-01`
    (`BATCH_INACTIVITY_POLICY_EFFECTIVE_DATE`) — 기준일 후보 5종(D-105)의 max가 이 날짜보다
    이르면 시행일을 기준일로 본다(`InactivityDueDateCalculator.resolveDueDate`의
    `coerceAtLeast`). **후보가 전부 null이면 여전히 null이다** — 판정 대상 자체가 아니므로
    시행일로 대체하지 않는다.
  - **1회 실행 상한** `max-deductions-per-run`, 기본 `1`
    (`BATCH_INACTIVITY_MAX_DEDUCTIONS_PER_RUN`) — `InactivityBatchRunner`의 회원 루프에서
    `minOf(부족분, 상한)`으로 자른다. 잘린 부족분은 `skippedCount`에 세지 않고 로그로만 남긴다
    (그 필드는 대상 소진·경쟁 패배 전용 D-113이고, 상한 적용은 사고가 아니라 정상 예정 동작이다).
- 이유 ①: **D-106의 상태 기반 캐치업은 "배치가 이미 돌고 있었다"는 전제 위의 설계다.** 그래서
  "배치가 며칠 죽었던 기간"과 "배치가 아예 없었던 기간"을 구분하지 못한다. 하한이 없으면 배포 후
  첫 실행이 과거 전체를 밀린 주기로 계산해, 200일 전 등록되고 한 번도 쓰이지 않은 `SESSION_PASS`가
  14회 부족분으로 잡혀 한 실행에 잔여가 0이 된다(05-REVIEW.md CR-02, BATCH-01 실패 판정).
  시행일은 그 전제를 코드에 명시하는 유일한 장치다.
- 이유 ②: 상한은 사고 피해의 **속도**를 묶는다. 매일 04:00 배치에서 정상 부족분은 0 또는 1이고
  2 이상은 배치가 2주 넘게 죽었거나 계산이 틀린 상황이다. 하루 1회로 묶으면 계산이 틀려도 피해가
  하루 1회씩만 누적돼 관리자가 킬 스위치(D-116)를 켤 시간이 생긴다. 밀린 주기는 사라지지 않고
  다음 실행들이 이어받으므로 **캐치업의 총량은 그대로다** — 상한은 속도만 늦춘다.
- 시행일을 `InactivityDueDateCalculator`에 주입하지 않고 **파라미터로 넘긴다** — 이 object는 순수
  계산이라 Spring·DB·시각 빈에 의존하지 않는다(conventions §5). 설정을 직접 읽으면 이 계산을
  검증하는 데 스프링 컨텍스트가 필요해지고 "같은 입력이면 같은 출력"이 깨진다.
- 기각 대안:
  - *상한 3회*: 첫 실행 피해가 여전히 크다(잔여 3회짜리가 한 번에 0이 된다). 정상 부족분이 0 또는
    1인 이상 3은 사고를 막지도, 정상 동작을 돕지도 않는다.
  - *배치 도입일 자동 감지(`batch_execution` 최초 행)*: 실행 이력을 지우거나 테이블을 옮기는 순간
    소급 차감이 되살아난다. 게다가 D-106은 "실행 이력을 부족분 계산의 근거로 쓰지 않는다"를 명시적
    원칙으로 두고 있어(멱등의 근거는 원장뿐), 그 원칙과 정면으로 충돌한다.
  - *하한 없이 킬 스위치(D-116)만*: cron을 끈 채 배포하면 당장은 안전하지만, 켜는 순간 같은 사고가
    난다. 킬 스위치는 사고를 **멈추는** 수단이지 **막는** 수단이 아니다.
- **테스트 전역 기본값을 과거로 고정한다.** `build.gradle.kts`의 `tasks.withType<Test>`가
  `goldwrestling.batch.inactivity.policy-effective-date=2000-01-01`을 준다. 기본값(`2026-09-01`)이
  배치 테스트의 고정 시각(`BatchFixtures.FIXED_TODAY` = 2026-08-02)보다 **미래**라, 고정하지 않으면
  모든 배치 테스트의 기준일이 시행일로 끌어올려져 기대 차감 수가 0이 되고 멱등·캐치업 테스트가
  "아무것도 차감하지 않음"을 검증하는 빈 껍데기가 되면서도 초록불로 통과한다. 시행일·상한 자체를
  검증하는 테스트만 `@SpringBootTest(properties = ...)`로 클래스마다 덮어쓴다
  (`InactivityBatchPolicyLimitTest`, `InactivityBatchDeductionLimitOverrideTest`).
- **부작용 하나: 상한 `1` 아래에서 "대상 소진 스킵"은 경쟁 없이는 도달할 수 없다.** 회원당
  `deductOnce` 호출이 최대 1회인데, 그 첫 호출은 대상 회원 벌크 조회
  (`findMemberIdsWithDeductibleSessionPass`)와 필터가 같아 단일 스레드에서는 항상 성공한다.
  그 분기는 죽지 않았고(상한을 올리거나 경쟁이 나면 즉시 살아난다) 계약도
  `InactivityBatchDeductionLimitOverrideTest`가 계속 지킨다.
- **CR-03(출석일 후보 부재)은 이번 범위 밖이다.** 기준일 후보 ①(마지막 출석일)은 Phase 6이
  `Attendance`를 도입하기 전까지 항상 null이라, 저녁반만 다니는 회원은 출석해도 기준일이 갱신되지
  않아 부당 차감될 수 있다(ROADMAP Phase 5 Note의 설계 결정). 시행일 하한은 이 문제를 **줄이지만
  없애지 않는다** — 시행일 이후 2주가 지나면 같은 문제가 다시 생긴다. 그래서 운영 배포는
  **`BATCH_INACTIVITY_SCHEDULER_ENABLED=false`로 cron을 꺼 둔 채** 올리고, 출석 후보가 생기는
  Phase 6에서 켠다(D-116). 수동 실행 API는 이 값과 무관하게 동작하므로 그때까지 실행하지 않는다.

## D-120. 배치 `errorSummary`에 실패 회원 id를 남긴다 — 예외 메시지는 계속 차단한다

- 2026-08-16 / 부분 실패(`PARTIAL_FAILURE`) 이력의 `error_summary`에 `memberId={id}: {예외종류}`
  형태로 실패 회원 id를 남긴다. 이 값은 관리자 배치 API 3종 응답으로 그대로 나간다.
  예외 **메시지**·SQL·제약조건명은 여전히 담지 않는다(conventions §8, D-017).
- 이유: 이 값이 없으면 `PARTIAL_FAILURE` 이력만으로 **어느 회원의 잔여를 복구해야 하는지 특정할 수
  없다** — Core Value("회원이 보는 잔여 = 실제 사용 가능 횟수")를 되돌리는 작업 자체가 불가능해진다.
  노출 대상은 `hasRole("ADMIN")` 통과자뿐이고 관리자는 이미 `GET /api/admin/members`로 전 회원을
  열람하므로 **새로 넘는 권한 경계가 없다**(Phase 4의 R-04-02·R-04-03과 같은 성격).
- 배경: 05-SECURITY.md 감사에서 위협 등록부가 서로 모순됨이 드러났다 — T-05-23은 "회원 id + 예외
  메시지만 기록"을, T-05-31·T-05D-15-02는 "회원 id 미포함"을 요구했다. **T-05-23을 정본**으로 삼고
  나머지 둘의 문구를 오기로 정정했다(R-05-08 수용, 2026-08-16 사용자 확정).
- 기각 대안: 회원 id를 로그에만 남기기 — 신뢰 경계가 동일(둘 다 관리자·운영자)해 보안 이득이
  거의 없으면서 복구 시 서버 로그 접근을 강요한다.
- 계약 고정: `InactivityBatchFailureIsolationTest`가 회원 id 포함(`:159`)·예외 종류만 포함·
  SQL 제약조건 메시지 배제(`:188`, `:251`)를 함께 단언한다.

## D-121. cron 킬 스위치의 기본값을 꺼짐으로 뒤집는다 (fail-safe) — D-116 정정

- 2026-08-17 / `application.yml`의 `inactivity-scheduler-enabled` 기본값을 `true` → **`false`**로,
  `@ConditionalOnProperty`의 `matchIfMissing`을 `true` → **`false`**로 바꾼다. 이제 자동 실행은
  `BATCH_INACTIVITY_SCHEDULER_ENABLED=true`를 **명시해야만** 켜진다.
- 이유: D-116은 "잘못 돌 때 끌 수 있다"는 **사후 수단**을 만들었지만, 켜고 끄는 판단이 배포자의
  기억에 달려 있었다 — 환경변수를 빠뜨리면 켜진 채로 뜬다(fail-open). 05-SECURITY.md R-05-09가
  이 상태를 지적했다. CR-03(기준일 후보 ① 부재)이 Phase 6까지 열려 있는 동안 cron이 돌면
  저녁반 전용 `SESSION_PASS` 회원의 잔여가 2주마다 부당하게 깎인다. **잊어서 안 도는 것은 아무 일도
  일어나지 않지만, 잊어서 도는 것은 회원 횟수가 사라진다** — 두 실패의 비용이 비대칭이므로
  기본값은 싼 쪽으로 둔다.
- 대가: Phase 6에서 켤 때 서버 환경변수를 명시해야 한다. 그 시점에 "이제 켜도 되는가"(출석 기록이
  실제로 쌓이는가)를 확인하고 켜는 것이 옳은 순서이므로 대가라기보다 절차의 일부다.
- 테스트 영향 없음: `build.gradle.kts`가 이미 테스트 전역에서 이 값을 `false`로 고정하고 있고,
  스케줄러 빈이 **등록되지 않음**을 단언하는 테스트만 존재한다(`AdminBatchControllerTest`).
- 되돌리기: Phase 6에서 출석 기록이 채워지면 기본값을 재검토한다. 그때도 "기본은 꺼짐, 명시해야 켜짐"을
  유지할지(운영 안전) 되돌릴지(편의)를 함께 판단한다.

## D-122. FE 회원 예약 진입 구조: 예약 탭이 시간표다 — M2 홈 시간표 결정(02-CONTEXT D-14) 정정

- 2026-08-17 / 회원 예약 탭 `/reservations`를 **주간 시간표 + 내 예약 목록**을 담는 한 화면으로 만든다.
  예약 실행(셀 → 다이얼로그 → 확정)은 **시간표 한 곳에서만** 일어나고, 내 예약 목록은 조회·취소·
  변경의 진입점이다. 변경은 시간표를 "변경 모드"로 열어 새 타임을 고르는 흐름이다. 홈(`/`)은
  M2가 만든 잔여 요약을 유지하고 **시간표 바로가기만 추가**한다 — M2 결정(홈 하단에 시간표 추가,
  02-CONTEXT D-14)은 이 결정으로 정정한다. **[FE M4 discuss 확정]**
- 이유: 주간 그리드 + 주 이동 + 변경 모드까지 얹으면 홈이 무거워지고, 예약 실행 지점이 두 곳이 되면
  버튼 상태 6종(가능/마감/휴강/잔여 부족/다음 주/당일)의 회귀 고정 지점도 두 배가 된다. 탭 이름
  "예약"과 화면 내용(시간표에서 예약)이 일치하는 것도 이 배치다.
- 기각 대안: 홈 하단 시간표(M2 D-14 문언 — 변경 모드 진입 시 홈으로 이동하는 어색한 동선),
  별도 `/schedule` 라우트(회원 탭 4개 체계 밖의 화면이 늘어 진입점이 분산됨).

## D-123. FE 예약 버튼 판정 경계: `bookable`(세션 속성) + FE rules(회원 결합), 최종 판정은 API 에러

- 2026-08-17 / 예약 버튼 상태는 **서버 `bookable`**(세션 속성: 예약 대상 종류·휴강·창 —
  `ScheduleService` 실측으로 정원·본인 예약·잔여는 미포함 확인)과 **FE `features/reservation/rules.ts`
  판정**(정원 마감 `reservedCount >= capacity`, 이미 예약함 `myReservationId`, 잔여 부족 = 내 이용권
  결합)을 조합해 **표시용**으로만 결정하고, **최종 판정은 예약 API의 에러 코드**다. 사유가 겹치면
  **이미 예약함 > 휴강 > 창 밖 > 정원 마감 > 잔여 부족** 순으로 하나만 표시한다. 잔여 부족 판정의
  유효기간 기준일은 오늘이 아니라 **수업날**이다(D-091). 예약 도메인 에러 코드 전체(error-codes.md
  기준)를 FE 중앙 code→메시지 맵에 등록한다. **[FE M4 discuss 확정]**
- 이유: `bookable`은 회원 무관 세션 속성만 담으므로(구현 확인) 회원 결합 판정(잔여·본인 예약)은
  FE가 자기 데이터(내 이용권·`myReservationId`)로 할 수밖에 없다. 그러나 표시는 힌트일 뿐 진실이
  아니다 — 정원·중복·창은 요청 시점에 바뀔 수 있으므로 최종 판정을 API 에러 코드에 두어야
  "화면 숫자 = 서버 진실" 코어 밸류가 유지된다.
- 기각 대안: FE가 창·휴강까지 전부 재판정(서버 판정 로직 중복 — 시각 경계에서 어긋남),
  `bookable` 하나만 신뢰(잔여 부족·이미 예약함 상태를 표시할 수 없어 RESV-05 위반).

## D-124. FE 시간표 타임 슬롯 병합: 같은 시각 셀을 한 행으로, 슬롯 다이얼로그가 이용권별 액션 제시

- 2026-08-17 / 같은 시각의 셀들(EVENING/SESSION/LESSON — 계약이 이 순서로 정렬 보장, D-096)을
  시간표에서 **하나의 타임 슬롯 행으로 병합** 표시하고, 슬롯 상세 다이얼로그에서 보유 이용권에 따라
  가능한 액션(**수업 예약 / 1:1 예약**)을 함께 제시한다. 저녁반은 슬롯에 노출하되 예약 대상 아님을
  표시한다(D-093). 1:1 슬롯 점유 여부는 기존 계약의 LESSON 셀 `capacity(=1)`/`reservedCount`로
  판정한다 — **BE 계약 변경 불필요(확인 완료)**. **[FE M4 discuss 확정]**
- 이유: 1:1은 전 타임에서 저녁반·예약제와 동시 진행되므로(policies §2) 셀을 종류별로 나열하면
  같은 시각이 2~3행으로 갈라져 주간 그리드가 읽히지 않는다. 슬롯 병합 + 다이얼로그 분기가
  "30초 안에 예약" 목표에 맞다.
- 기각 대안: 종류별 셀 개별 나열(같은 시각 중복 행으로 시간표 가독성 훼손), 1:1 전용 별도
  화면(진입점 분산 + 같은 타임 동시 진행이라는 운영 실체와 불일치).

## D-125. FE 예약 확정·변경·당일 플로우: 차감 명시 다이얼로그, 변경 실패 안내, 사유 상시 노출

- 2026-08-17 / 셀 클릭 → 상세 다이얼로그에 **"잔여 N회 중 1회 차감"을 명시**하고 확정한다.
  당일 예약 확정 시에는 "당일 예약은 취소할 수 없습니다" 경고를 함께 보여준다(당일 예약은 성립
  즉시 비가역 — 취소·변경 불가이므로 확인 다이얼로그 규약 "비가역 전용"과 정합). 변경 실패
  (새 타임 만석 등) 시 **"기존 예약은 유지됩니다" 안내 필수**(D-090이 보장하는 동작을 문구로 전달).
  당일 건의 취소·변경 버튼은 비활성 + **사유를 상시 텍스트로 노출**한다(툴팁·`title` 속성 금지 —
  기존 FE 규약 재확인). **[FE M4 discuss 확정]**
- 이유: 예약은 잔여 차감이라는 금전적 결과가 즉시 따르고 당일 건은 비가역이므로, 차감량·비가역성을
  확정 전에 명시해야 "정확한 상태를 즉시 본다"는 코어 밸류에 맞다. 변경 실패 안내가 없으면 회원은
  기존 예약까지 사라졌다고 오해한다.
- 기각 대안: 셀 클릭 즉시 예약(차감 명시 없이 실행 — 오터치가 곧 차감), 사유 툴팁 표시(모바일에서
  접근 불가 + 기존 규약 위반).

## D-126. FE 잔여 반영은 invalidate 전용 — optimistic update 금지, 정원 경쟁 패배는 토스트+재조회

- 2026-08-17 / 예약·취소·변경 mutation 성공 후 시간표·내 예약·이용권 쿼리를 **`invalidateQueries`로만**
  갱신한다. **optimistic update는 금지한다** — 잔여 횟수는 서버가 유일한 진실이다. 정원 경쟁 패배
  (`RESERVATION_CAPACITY_EXCEEDED` 수신) 시 "방금 정원이 찼습니다. 다른 시간을 선택해주세요."
  토스트 + 시간표 즉시 재조회(invalidate)로 처리한다. **[FE M4 discuss 확정]**
- 이유: 코어 밸류가 "화면의 숫자는 항상 서버 진실과 일치"다. 잔여는 차감 대상 이용권 선택(D-091
  만료 임박순)·유효기간 판정(수업날 기준) 등 서버만 아는 규칙의 결과라 FE 선반영은 추측이 된다.
  fe-architecture 스킬이 예약을 optimistic 후보로 언급하지만 허용 규정일 뿐이며, 이 결정으로
  M4에서는 금지로 확정한다.
- 기각 대안: optimistic + 롤백(잔여 숫자가 잠시라도 서버와 어긋나는 상태를 만들며, 롤백 실패 경로가
  코어 밸류를 직접 위협), 정원 패배 시 다이얼로그 유지(이미 무효한 화면 위에서 재시도 유도).

## D-127. 출석 모델: 레코드 부재=미체크, 소급 출석은 기존 INACTIVITY 차감을 되돌리지 않는다

- 2026-08-18 / 출석은 회원×세션 레코드로 저장하며 **레코드 부재 = 미체크**, 레코드 상태는
  `ATTENDED`/`ABSENT` 2값이다. 예약제/1:1은 예약자 명단을 프리로드해 체크하고, **저녁반은 관리자가
  회원 검색으로 추가하며 추가됨 = 출석**(`ATTENDED`만, 불참 없음). **회원×세션 유니크 제약**으로
  중복 출석을 DB 수준에서 막는다. 소급 입력·수정을 허용하되, **소급 출석이 이미 실행된
  `INACTIVITY` 차감을 되돌리지 않는다**(기준일 반영은 다음 배치부터, policies §6). 미사용 차감
  기준일 후보 ①은 `ATTENDED`만 인정한다. **[Phase 6 discuss 확정]**
- 이유: 배치 부족분 계산이 이미 `coerceAtLeast(0)`으로 음수를 자르므로(D-106) 소급 환불은 구조적으로
  불가능하고, 이를 정책으로도 못 박아야 "배치는 절대 환불하지 않는다"는 단방향 원장이 유지된다.
  정정이 필요하면 관리자 `ADMIN_ADJUST` 수동 복구 한 경로만 쓴다.
- 기각 대안: 소급 출석 시 자동 환불(배치가 양방향이 되어 멱등 검증이 복잡해지고 원장 해석이 흐려짐),
  불참을 저녁반에도 허용(저녁반은 예약 명단이 없어 "안 온 사람"을 특정할 모집단 자체가 없음).

## D-128. 저녁반 출석 추가와 0.5회 차감은 한 트랜잭션 — 회비 우선, 불가 시 거부, 삭제 시 자동 복구

- 2026-08-18 / 저녁반 출석 추가 시 서버가 차감을 판정한다: **수업날 기준** 유효한
  `EVENING_MEMBERSHIP` 보유 시 차감 없음(회비 우선), 없으면 `SESSION_PASS`에서 0.5 차감
  (`EVENING_HALF`, 대상 선택은 D-091 만료 임박순 재사용). **둘 다 불가(회비 없음 + 잔여 0.5 미만)면
  출석 추가 자체를 409로 거부**한다. 차감 이력은 출석 레코드에 연결하고, **출석 삭제 시 연결 차감을
  `EVENING_HALF_REFUND`(신설) 사유로 자동 복구**한다. 이중 차감은 회원×세션 유니크(D-127)로
  구조적으로 막는다. **[Phase 6 discuss 확정]**
- 이유: 출석과 차감이 별도 API면 관리자가 한쪽만 수행하는 반쪽 상태가 생긴다. 회비 없는 참여를
  조용히 기록하면 "화면 숫자 = 서버 진실" 밖의 무료 참여가 누적되므로 거부가 안전하다. 복구 사유를
  신설하는 것은 예약 복구가 `CANCEL_REFUND`·`CLASS_CANCELED_REFUND`로 구분되는 선례와 일관 —
  원장에서 사유만으로 차감·복구가 구분된다.
- 기각 대안: 출석만 기록+차감 별도(반쪽 상태), `EVENING_HALF` +0.5 상쇄(저녁반 참여 횟수 집계가
  차감·복구 혼합으로 오염), `ADMIN_ADJUST` 복구(자동 복구가 수동 가감으로 기록돼 의미 왜곡).

## D-129. 알림 확인은 "모두 읽음" 전용, 미확인 카운트는 목록 응답 포함 — 활동 피드는 동일 데이터의 다른 뷰

- 2026-08-18 / NOTIF-02 확인 처리는 **"모두 읽음" 벌크 처리만** 제공한다(개별 읽음 없음).
  미확인 카운트는 별도 엔드포인트 없이 **알림 목록 응답에 포함**한다. NOTIF-03 활동 피드는
  notification 테이블 **동일 데이터의 다른 뷰**로, 읽음 여부와 무관하게 시간순 + **기간·종류 필터** +
  기존 page/size(`PageResponse`) 페이지네이션으로 조회한다. **[Phase 6 discuss 확정]**
- 이유: 관리자 1~2명이 30초 폴링으로 소비하는 알림이라 개별 읽음의 상태 관리 비용이 효익을
  넘는다. 카운트를 목록 응답에 실으면 폴링 1회가 API 1호출로 끝난다. 피드를 같은 테이블의 다른
  쿼리로 두면 이벤트 저장 경로가 하나라 두 뷰가 어긋날 수 없다.
- 기각 대안: 개별 읽음(FE·BE 모두 상태 관리 추가, 요구사항에 근거 없음), 카운트 별도 엔드포인트
  (폴링마다 2호출), 피드 전용 테이블(같은 이벤트를 두 번 쓰는 이중 기록 — 정합 리스크).

## D-130. CR-03 배선으로 기준일 5종을 완결하고, cron은 기본 꺼짐 유지 + 운영 켜기 절차를 문서화한다

- 2026-08-18 / `InactivityBatchRunner`의 `lastAttendanceDate = null` 하드코딩을 회원별
  "마지막 `ATTENDED` 수업일" 벌크 조회로 교체해 기준일 5종 max(D-105)를 완결한다(CR-03 마감).
  cron 킬 스위치 기본값은 **D-121(기본 꺼짐)을 유지**하고, 운영 켜기 절차를 문서화한다:
  ① 출석 배선 배포 확인 → ② 운영 환경 수동 실행 1회 검증 → ③ `BATCH_INACTIVITY_SCHEDULER_ENABLED=true`
  명시. 절차에 **정책 시행일 하한(D-119, 기본 2026-09-01)을 명시**한다 — 시행일 전 수동 실행은
  차감 0건이 정상이므로 검증 결과를 오독하지 않게 한다. **[Phase 6 discuss 확정]**
- 이유: calculator·candidates는 이미 5종을 수용하고 있어 배선은 조회 교체 한 지점이다. 기본값을
  코드로 되돌리면 D-121의 fail-safe(설정을 빠뜨리면 꺼짐)가 사라진다 — 켜는 판단은 배포 절차의
  일이지 코드 기본값의 일이 아니다.
- 기각 대안: 기본값 복원(`matchIfMissing=true` — D-121이 철회한 사고 경로 재개방), 절차 없이
  환경변수만 켜기(저녁반 전용 회원 부당 차감을 운영에서 최초 발견하게 됨).

## D-131. 공지사항은 제목+본문 MVP — 관리자 CRUD + 회원 열람, hard delete

- 2026-08-18 / 공지(`Notice`)는 **제목+본문만** 담는다(첨부·고정(핀)·노출 예약 없음). 관리자
  등록·수정·삭제 + 회원 목록·상세 열람, 목록은 기존 page/size(`PageResponse`) 재사용. 삭제는
  **hard delete**다. **[Phase 6 discuss 확정, 세부는 재량 위임]**
- 이유: NOTICE-01·02가 요구하는 것은 게시판 최소 기능이다. 공지는 원장(`PassTransaction`) 같은
  감사 대상이 아니라 삭제 이력 요구가 없고, soft delete는 모든 조회에 필터 조건을 강제하는
  비용만 남긴다.
- 기각 대안: soft delete(이력 요구 없는 도메인에 조회 복잡도만 추가), 고정·첨부 선반영
  (운영 피드백 전 추측 기능 — deferred로 기록).

## D-132. 출석 체크 API는 타임별 일괄 저장이 아니라 회원 건별 upsert로 한다

- 2026-08-18 / 출석 체크는 회원 1명 단위 upsert API로 처리한다(타임별 일괄 저장 없음)
- 이유: 저녁반 추가는 차감 트랜잭션을 유발해 회원마다 성공/409가 갈린다. 일괄 저장이면 한 명의
  잔여 부족이 다른 회원의 출석까지 롤백시키거나, 부분 성공을 응답으로 표현하는 별도 계약이
  필요해진다
- 기각 대안: 타임별 일괄 저장(부분 실패 계약 필요), 저녁반만 건별·예약제만 일괄(같은 화면에 두
  계약 공존)

## D-133. 저녁반 차감 불가는 신규 코드 `EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE`(409)로 응답한다

- 2026-08-18 / 저녁반 출석 추가 시 회비도 없고 `SESSION_PASS` 잔여도 0.5 미만이면
  `EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE`(409)를 응답한다. `INSUFFICIENT_PASS_COUNT` 재사용은
  기각한다
- 이유: 이 실패는 "잔여가 음수가 된다"가 아니라 "회비도 없고 잔여도 0.5 미만"이라는 복합
  사유이고, FE 안내 문구가 예약 실패("다른 시간을 고르세요")와 완전히 다르다(관리자에게 "수동
  가감으로 충전 후 재시도"를 안내해야 한다). 06-CONTEXT.md 문서 정합 항목도 신규 코드 등록을
  지시했다
- 기각 대안: `INSUFFICIENT_PASS_COUNT` 재사용(FE가 맥락 구분 불가)

## D-134. 공지 열람에는 회원 상태 게이트를 걸지 않는다(D-071 연장)

- 2026-08-18 / 회원의 공지 목록·상세 조회 API는 `MemberStateGate`(`requireActive`)를 호출하지 않는다
- 이유: 공지는 개인 데이터가 아니라 지점 전체 공지이고, `MemberPassService`가 이미 같은 이유로
  `MemberStateGate`를 호출하지 않는다(D-071). `requireActive`를 걸면 휴회(`ON_LEAVE`) 회원이
  운영 공지를 못 보게 되는데 이는 policies §5의 취지(휴회는 차감 정지이지 정보 차단이 아니다)와
  어긋난다
- 기각 대안: `requireActive` 적용(휴회 회원 차단), 새 게이트 메서드 신설(상태 게이트 축이 두
  갈래로 갈라짐)

## D-135. 활동 피드용 `idx_notification_occurred_at (occurred_at DESC)`를 V11에 함께 만든다

- 2026-08-18 / 활동 피드(NOTIF-03)의 시간순 조회를 위해 `idx_notification_occurred_at
  (occurred_at DESC)` 인덱스를 V11(attendance/notice 마이그레이션)에 함께 추가한다
- 이유: 기존 `idx_notification_unread`는 선두 컬럼이 `is_read`라 읽음 여부 무관 시간순 조회에
  온전히 쓰이지 못한다. 인덱스 하나는 값싼 대비책이고, 커밋된 마이그레이션은 수정할 수 없으므로
  attendance/notice와 같은 V11에 함께 넣는 것이 유일하게 저렴한 시점이다
- 기각 대안: 미도입(운영 데이터가 쌓인 뒤 별도 마이그레이션 필요), `(type, occurred_at DESC)`
  복합(종류 필터가 선택적이라 단일 컬럼 인덱스가 더 넓게 쓰인다)

## D-136. 저녁반 출석은 회원-시간표 지점 일치를 검증한다 — 요일 일치 검증은 정책 확정까지 보류(미해결)

- 2026-08-18 / 저녁반 출석 추가(`AttendanceService.addEveningAttendance`)에 회원 소속 지점과
  시간표 지점의 일치 검증을 넣는다. 불일치 시 예약 경로와 같은 `ClassScheduleNotFoundException`
  (404)으로 거부한다. **요일 일치 검증(`schedule.dayOfWeek == classDate.dayOfWeek`)은 넣지 않는다**
- 이유: 지점 검증은 `MemberReservationService.reserve`가 이미 강제하는 불변식인데, 저녁반 출석은
  예약을 거치지 않아 이 검사가 적용되지 않는 유일한 원장 경로였다 — 타 지점 회원의 이용권에서
  0.5회가 빠질 수 있었다. 반면 요일 검증은 "열리지 않은 요일에 출석을 기록할 수 있어야 하는가"
  (보강 수업·임시 수업 처리)라는 **운영 정책 질문**이라 코드가 먼저 정할 사안이 아니다
- **미해결 항목**: 요일이 어긋난 (시간표, 날짜) 조합으로도 `class_session`이 생성된다.
  소유자에게 보강 수업 허용 여부를 확인한 뒤 별도 처리한다 — 허용하지 않기로 하면 예약 경로와
  같은 한 조건으로 묶고 `AttendanceEveningHalfTest`의 날짜 픽스처를 요일 정렬로 바꿔야 한다
- 기각 대안: 403 응답(타 지점 시간표의 존재 여부가 노출된다), `check`에도 같은 조회 추가(활성
  예약자만 통과하므로 예약 생성 시점 검사로 이미 보장된다 — 쿼리만 늘어난다)

## D-137. FE 관리자 주간 보드: 회원 시간표와 같은 슬롯 병합·`lg` 경계, 셀 패널은 우측 Sheet + 종류별 섹션, 명단은 출석 명단 API 단일 소스

- 2026-08-26 / 관리자 주간 보드(`/admin`, `GET /api/admin/schedule/board`)는 회원 시간표(D-124)와
  같은 규칙으로 **같은 시각의 저녁반/수업/1:1 셀을 한 슬롯 행으로 병합**하고, 그리드↔일 단위
  리스트 전환 경계도 회원 시간표와 같은 **`lg`(1024px)**를 쓴다. 슬롯 클릭 패널은 `ResponsiveDialog`를
  **optional prop(`desktopVariant`, 기본값 `'dialog'`)으로 확장**해 데스크탑은 **우측 Sheet**, 모바일은
  하단 풀시트로 열고, 패널 안은 **수업 종류별 섹션**(수업/1:1/저녁반)으로 나눈다. 각 섹션의 명단은
  **`GET /api/admin/attendances?classScheduleId&classDate` 단일 소스**로 채운다 — 예약자 프리로드와
  출석 상태(`status`·`attendanceId`·`reservationId`·`deducted`)가 한 응답에 있어 보드 응답의
  `reservations`와 결합할 필요가 없다. 주 이동은 URL `?week=`이며 관리자 보드는 조회 주 범위 제한이
  없다(계약). **[Phase 5 discuss 확정]**
- 이유: 병합·경계를 회원 시간표와 맞추면 `timeRows()`·`toTimeSlots()`(features/reservation/rules)를
  그대로 재사용하고 관리자·회원이 같은 격자를 본다. 우측 Sheet는 보드를 가리지 않아 명단 처리 중에도
  주간 현황을 참조할 수 있다 — 중앙 Dialog로는 보드가 가려진다. 출석 명단 API에는 `reservationId`가
  이미 있어(저녁반만 null) 대리 취소·변경의 대상 id까지 한 번에 얻는다 — BE 계약 변경 요청 없음.
  ResponsiveDialog 확장은 CLAUDE.md 재사용 규약(새 prop은 optional + 기본값, 기본 동작 불변)을 따른다.
- 기각 대안: 종류별로 셀을 분리(같은 시각 열이 3배로 늘어 640~1023px에서 정보 손실), 보드 응답의
  `reservations` + 출석 API 이중 소스(두 응답 사이 상태 어긋남), 중앙 Dialog 패널(보드 가림),
  새 패널 컴포넌트 신설(ResponsiveDialog의 "마크업 한 벌·포커스 트랩 하나" 원칙을 다시 구현).

## D-138. FE 출석 체크: 예약제/1:1 토글은 확인 없이 즉시 upsert, 저녁반 삭제만 확인, 차감 결과 뱃지, 409는 토스트+수동 가감 이동

- 2026-08-26 / 예약제/1:1 명단 행의 출석/불참 토글은 **확인 다이얼로그 없이 즉시** `PUT /api/admin/attendances`
  (회원 건별 upsert, D-132)를 호출하고, 미체크 복귀(`DELETE`)도 즉시 실행한다. **저녁반 출석 삭제만
  확인 다이얼로그**를 둔다 — 삭제가 0.5회 `EVENING_HALF_REFUND` 복구를 유발하기 때문(D-128).
  저녁반 행에는 차감 결과 뱃지를 표시한다: `deducted=true` → "0.5회 차감", `false` → "회비". 저녁반
  추가는 패널 안 회원 검색(`GET /api/admin/members?keyword=`)으로 하고, `EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE`
  (409, D-133)은 **토스트 + 회원 상세(`/admin/members/:memberId`, 수동 가감) 이동 액션**으로 안내한다.
  D-136 미해결(요일 검증)은 BE 백로그로 추적만 하고 FE는 진행한다. 성공 후 반영은 D-126과 같이
  `invalidateQueries` 전용(optimistic 금지). **[Phase 5 discuss 확정]**
- 이유: 출석 토글은 되돌릴 수 있는 액션이라 확인은 마찰이다(M3 D-085 "확인은 비가역 전용" 입장의
  일관 적용). 저녁반 삭제는 원장에 복구 트랜잭션이 남는 부수효과가 있어 예외로 둔다. 차감/회비 구분을
  행에 노출해야 "화면 숫자 = 서버 진실"이 성립한다 — 관리자가 회비 회원과 횟수권 회원을 눈으로 구분할
  다른 수단이 없다. 409 안내에 이동 액션을 붙이는 이유는 계약이 명시한 복구 절차("수동 가감으로 충전
  후 재시도")를 클릭 한 번으로 잇기 위해서다.
- 기각 대안: 타임별 일괄 저장 버튼(계약에 없음 — D-132가 기각), 모든 출석 변경에 확인(마찰),
  차감 여부 비노출(회비/횟수권 구분 불가), 409를 문구만으로 안내(관리자가 회원을 다시 검색해야 함).

## D-139. FE 대리 취소·변경·휴강: 취소는 복구 토글 확인, 변경은 보드 변경 모드, 휴강 사전 안내·해제 문구, 예약 검색 화면 포함

- 2026-08-26 / **대리 취소**는 확인 다이얼로그에 **복구 여부 토글(기본 복구, `refund` 기본값 true)**을
  넣는다. **대리 변경**은 패널의 명단 행에서 진입해 보드가 **변경 모드**로 전환되고 새 슬롯을 고르는
  방식(회원 D-125와 같은 구조, URL 상태)으로 한다 — 당일·과거 날짜도 계약상 허용된다. **휴강 처리**는
  셀(수업 종류) 단위 액션으로 `ReasonDialog`(사유 필수)를 재사용하며, 확인 문구에 보드 셀의
  `reservedCount`로 **"예약 N건이 취소되고 차감이 복구됩니다"**를 사전 안내하고 성공 토스트에는
  응답의 `canceledReservationCount`를 쓴다. **휴강 해제**(`POST .../resumption`)도 포함하되 확인 문구에
  **"취소된 예약은 자동 복원되지 않습니다"**를 필수로 넣는다. 보드 명단에는 활성 예약만 보이므로
  취소 포함 조회를 위해 **예약 검색 화면(`/admin/reservations`, `GET /api/admin/reservations`)을
  포함**한다 — `DataTable` + 기간·수업 종류·회원 검색어·상태 필터(URL searchParams), M3 D-085 페이지네이션
  패턴. FE REQUIREMENTS에서는 OPS-02의 확장으로 기록한다(BE requirements §4.3 "모든 회원의 예약 조회").
  **[Phase 5 discuss 확정]**
- 이유: 취소·휴강은 비가역이라 확인이 맞고, 복구 여부는 취소와 같은 순간에 정해야 하므로 같은
  다이얼로그에 둔다. 변경 모드 재사용은 슬롯 선택 UI를 두 벌 만들지 않기 위해서다. 휴강 사전 안내는
  관리자가 결과 규모를 실행 전에 알아야 하고, 해제 문구는 계약 설명("복원하지 않는다")을 관리자가
  오해하지 않게 하기 위해서다. 휴강이 셀 단위인 것은 계약(`SuspendClassSessionRequest`가
  classScheduleId+classDate)이 정한다 — 병합 슬롯에서는 섹션마다 실행된다. 검색 화면 없이는
  "취소된 예약 누가·언제·복구 여부"를 볼 곳이 없어 대리 처리의 사후 확인이 불가능하다.
- 기각 대안: 변경용 별도 슬롯 선택기(UI 이중화), 휴강 해제 미포함(계약이 제공하고 실수 휴강의
  복구 경로가 없어짐), 예약 검색 화면 미포함(취소 이력 확인 불가).

## D-140. FE 알림·피드·공지 배치: 종 팝오버 + `/admin/activity` 통합, 폴링 1쿼리 공유, 공지 폼 다이얼로그, 회원 탭 5번째 + 승인 대기 링크

- 2026-08-26 / 관리자 헤더의 **종 아이콘 팝오버**가 최근 알림(`GET /api/admin/notifications`,
  size 소량)과 **"모두 읽음" 버튼**(`POST .../read-all`)을 담고, 전체 목록은 **`/admin/activity`
  활동 피드 페이지**(`GET /api/admin/activity-feed`, 기간·종류 필터)로 통합한다 — 두 API는 같은
  데이터의 다른 뷰다(D-129). **폴링은 `AdminLayout`이 소유하는 알림 쿼리 하나**(30초 `refetchInterval`,
  관리자 구역 마운트 중에만)이며, 뱃지는 응답의 `unreadCount`, 팝오버는 같은 쿼리 캐시를 읽는다 —
  카운트 전용 API가 없으므로(D-129) 30초당 호출은 1회다. **공지 폼**은 `ResponsiveDialog`(제목 +
  본문 textarea, 200/10000자 zod), 삭제는 확인 다이얼로그(hard delete, D-131), 본문은 plain text
  `whitespace-pre-wrap`으로 렌더한다(마크다운·HTML 없음 — 이스케이프는 React 기본). **회원 진입**은
  하단 탭 5번째 "공지"(`/notices`) + **승인 대기 화면(`/pending`)에도 공지 링크**를 둔다. 이를 위해
  FE `features/auth/rules.ts`의 회원 분기에 **상태 무관 경로 예외(`/notices*`)**를 추가해 PENDING
  회원이 공지 라우트를 통과하게 한다(D-134의 FE 대응). **[Phase 5 discuss 확정]**
- 이유: 알림과 피드를 한 페이지로 통합하면 D-129가 보장한 "저장 경로 하나"가 화면에서도 한 곳이 된다.
  폴링 쿼리를 레이아웃이 소유하는 것은 M3 승인 대기 뱃지와 같은 구조(RESEARCH §Pattern 6)다.
  "모두 읽음"을 버튼으로 두는 이유는 팝오버를 스쳐 열기만 해도 읽음 처리되면 미확인 뱃지가 의미를
  잃기 때문이다. 공지가 제목+본문뿐(D-131)이라 페이지 폼은 과하다. 승인 대기 회원에게 공지를 여는
  것은 D-134("휴회는 정보 차단이 아니다")의 연장이며, 현재 `RequireAuth`가 PENDING을 `/pending`으로
  강제 이동시키므로 예외 목록 없이는 성립하지 않는다.
- 기각 대안: 알림 전용 페이지 + 피드 페이지 분리(같은 데이터를 두 화면에서 보여 혼란), 팝오버 열면
  자동 읽음(뱃지 무의미), 뱃지용 폴링과 팝오버용 조회를 별도 쿼리로(30초당 2회), 공지 마크다운
  렌더(새 의존성 + 계약이 원문 텍스트라 명시), PENDING 회원에게 공지 비노출(D-134 취지 위반).

## D-141. FE M6 접근성 게이트 이원화: 공통 컴포넌트·레이아웃 셸 스토리만 axe `'error'`, 페이지는 `'todo'` + 심각도 분류

- 2026-08-28 / Storybook a11y(`parameters.a11y.test`)를 **`src/components/common/` 8개 + `src/components/layout/`
  셸 3개(AdminShell·AuthShell·MemberShell) 스토리 meta에서 `'error'`**로 승격해 axe 위반이 `pnpm test:stories`
  실패가 되게 한다. 페이지·feature 스토리는 전역 `'todo'`(보고만)를 유지하되 **위반 리포트를 심각도로 분류**한다 —
  키보드 도달 불가·폼 라벨 누락·이름 없는 인터랙티브 요소·다이얼로그 접근성 이름 누락은 M6에서 수정, 경미한
  대비(메타 텍스트 AA 미달 등)는 백로그. 수동 점검은 **axe가 못 잡는 포커스 순서·포커스 트랩·닫힘 후 복귀만**,
  핵심 플로우(회원 로그인→예약→취소, 관리자 로그인→보드→명단 패널→출석) 한정. **[Phase 6 discuss 확정]**
- 이유: 공통 컴포넌트·셸은 모든 화면이 통과하는 경로라 여기서 막으면 페이지 위반의 대부분이 원천에서 사라진다.
  페이지 498개 스토리를 한 번에 `'error'`로 올리면 대비 같은 경미 위반으로 게이트가 막혀 출시선이 밀린다 —
  분류해 "지금 고칠 것"과 "미룰 것"을 가른다. 수동 점검을 핵심 플로우로 한정하는 이유는 axe로 못 잡는 영역만
  사람이 보는 게 비용 대비 효과가 크기 때문이다.
- 기각 대안: 전 스토리 `'error'`(경미 위반으로 게이트 마비), 전부 `'todo'` 유지(게이트 없음 — M6 목표 미달),
  `components/common`만 승격하고 셸 제외(내비게이션이 접근성 핵심 경로인데 강제에서 빠짐).

## D-142. FE M6 반응형 증빙: Playwright 360/768/1280 순회 — 스크린샷 아티팩트 + 구조 단언, 픽셀 기준선 없음, 불일치 방치 금지

- 2026-08-28 / 반응형 전수 점검(QA-01)의 증빙은 **Playwright 전용 스펙**이 핵심 화면 × **360 / 768 / 1280**을
  `setViewportSize`로 순회하며 (a) `page.screenshot()` 아티팩트를 리포트에 첨부하고 (b) **구조 단언**으로 판정한다 —
  design-system §반응형 표 4행(내비 하단 탭↔헤더/사이드바 640, 목록 카드형 행↔`<table>` 640, 상세 편집·명단
  패널 하단 Sheet↔Dialog/우측 Sheet 640, 주간 시간표 2종 리스트↔`<table>` 1024). **`toHaveScreenshot` 픽셀
  기준선은 쓰지 않는다.** 768 태블릿 구간(내비는 데스크탑, 시간표는 리스트)은 명시 대상. **표와 어긋난 화면은
  화면 수정이 기본, 화면이 옳으면 표를 고치고 여기 기록** — 불일치를 남긴 채 페이즈를 닫지 않는다. **[Phase 6 discuss 확정]**
- 이유: 픽셀 기준선은 macOS(로컬)와 Linux(CI)의 폰트 렌더링 차이로 플랫폼별 스냅샷을 이중 관리해야 하고
  D-143의 CI 게이트를 흔든다. 표와의 대조는 "어느 컴포넌트가 보이는가"라는 구조 사실이므로 구조 단언이 정확히
  그것을 검증하고, 스크린샷은 사람이 보는 증빙으로 남기면 된다. 360은 Storybook(390)·iPhone 14(390)보다
  좁은 실기기 하한(M5 리서치 항목과 동일)이다.
- 기각 대안: `toHaveScreenshot` 픽셀 회귀(플랫폼별 기준선·폰트 플레이키), Storybook Mobile 스토리 전수만으로
  증빙(뷰포트 390 하나·768 구간 미검증), 수동 체크리스트만(재현 불가·회귀 못 잡음), 새 Playwright 프로젝트
  추가(스펙 전체가 3배로 늘어남 — 전용 스펙 안 순회가 싸다).

## D-143. FE 테스트 CI는 M6, 배포 CI는 M7: lint/typecheck/unit/storybook + E2E 별도 job, main 필수 status check, E2E는 MSW 유지, 계약 밖 검출은 MSW `'error'`(Vitest·Storybook)

- 2026-08-28 / `.github/workflows/`에 **테스트 CI 워크플로**를 M6에 추가한다 — PR + `dev`/`main` push 트리거,
  **Job A** `lint`·`format:check`·`typecheck`·`test:unit`·`test:stories`, **Job B(별도)** `test:e2e` 두 프로젝트
  (chromium + webkit, MSW 모드). `pnpm/action-setup` + `setup-node cache: 'pnpm'`(D-006 후속 메모 이행).
  두 job을 **`main` 브랜치 보호의 required status check로 등록**(레포 설정 — 사용자가 직접). **E2E는 MSW 유지**,
  실 BE 스모크와 **배포 워크플로(S3+CloudFront)는 M7** — D-006의 "GitHub Actions 워크플로우 작성 시(M7)"는
  배포 CI를 가리키는 것으로 좁힌다. 계약 밖 호출 검출은 **MSW `onUnhandledRequest: 'error'`** — Vitest는
  이미 적용, **Storybook 워커는 `addonMsw(setup)`으로 기본 핸들러 + `'error'` 주입**(preview의 죽은
  `parameters.msw` 설정 해소), **E2E 워커는 `'bypass'` 유지**(`page.route` 스텁이 MSW에는 미처리 요청).
  실행되지 않은 경로는 잡지 못하므로 성공 기준 4 증빙은 M5의 api 호출 경로 ↔ openapi.yaml 대조 표를
  페이즈 말에 1회 재실행한다. **[Phase 6 discuss 확정]**
- 이유: "출시선"은 로컬에서 초록인 것이 아니라 머지가 막히는 것이다 — status check 없이는 게이트가 아니다.
  E2E를 CI에 넣지 않으면 QA-02가 규약으로만 남는다. 카카오 로그인은 실 환경 자동화가 불가능하므로 MSW가
  구조적으로 옳고, 실 BE 스모크는 배포 환경이 생기는 M7에 붙이는 게 맞다. MSW `'error'`는 "계약 밖 엔드포인트
  0건(코드에도 목에도)" 불변식과 결합될 때 계약 밖 호출을 즉시 드러내는 가장 싼 장치다.
- 기각 대안: CI 전체를 M7로(M6 동안 게이트 부재), E2E 로컬 전용(게이트 아님), 실 BE 스모크 M6 포함(배포 환경
  없음·카카오 자동화 불가), E2E 워커도 `'error'`(page.route 스텁 전부 파괴), 계약 밖 검출을 grep 스크립트로만
  (실행 시점 검출 없음 — 대조 표는 보조 증빙으로만 유지).

## D-144. FE M6 이월 포함 기준 "게이트를 흔드는 것 + 사용자에게 보이는 것": 플레이키 스토리·관리자 착지 `/admin`·preview.tsx·IN-02/03/05 포함, 변경 모드 다른 주는 BE-REQ-006, searchParams 승격은 백로그

- 2026-08-28 / M3~M5 이월 항목 중 **포함:** (a) `ReservationsPage.stories > ScheduleForbidden` 콜드런 플레이키
  수정(CI 게이트 필수), (b) **관리자 로그인 착지 `/admin/members` → `/admin`(보드)** + E2E 기대 갱신(D-137의
  "`/admin` = 보드" 후속), (c) `.storybook/preview.tsx` MSW setup 교체(D-143 게이트 관련), (d) 05-REVIEW
  **IN-02·IN-03·IN-05를 한 태스크로**(E2E 스텁 픽스처 — IN-03 계약 위반 형태, IN-02 뱃지≠서버 진실, IN-05는
  같은 파일). **BE 요청으로 전환:** 대리 변경 모드에서 다른 주로 이동 시 대상 종류를 몰라 `TYPE_MISMATCH`로
  오분류되는 문제는 FE에 `UNKNOWN_TARGET` 사유를 추가하지 않고 "BE에 필요한 변경"(FE
  `.planning/BE-CHANGE-REQUESTS.md`의 **BE-REQ-006** — `GET /api/admin/reservations/{reservationId}`)으로
  요청, 현 동작 유지. **백로그 명시 이관:** `lib/searchParams.ts` 승격. **제외(이미 해소):**
  Vitest 브라우저 포트 충돌(`VITEST_BROWSER_PORT`), 04-REVIEW Warning(전부 fixed). **[Phase 6 discuss 확정]**
- 이유: 마감 페이즈는 "남은 것 전부"가 아니라 출시선 기준으로 고른다. 플레이키는 CI를 붉게 만들고, 착지 화면·
  뱃지 숫자는 관리자가 매일 보는 것이며, 계약 위반 픽스처는 "계약 밖 0건" 불변식의 테스트 쪽 구멍이다.
  변경 모드 사유 오분류는 근본 원인이 단건 조회 계약 부재라 FE 우회(`UNKNOWN_TARGET`)는 문구만 바꾸고 문제를
  영구화한다 — BE-CHANGE-REQUESTS의 목적("우회 코드가 영구화되지 않게")대로 요청으로 남긴다. searchParams
  승격은 내부 구조 정리라 두 기준 어느 쪽도 아니다.
- 기각 대안: 이월 전부 포함(마감 페이즈 비대화), `UNKNOWN_TARGET` FE 사유 추가(우회 영구화), IN-05 제외
  (같은 파일을 두 번 여는 비용이 더 큼), 착지 변경 보류(M5 D-01과 어긋난 채 출시).

## D-145. 휴강 캐스케이드 복구는 현재 상태 재판정 + 0행 스킵, 등록취소는 상태 전환 직후 이중 검사

- 2026-08-28 / 휴강 처리가 활성 예약을 배치 취소하며 복구를 반영할 때, 낡은(stale) 스냅샷이 아니라
  **복구 직전 이용권의 현재 상태**로 복구 여부를 재판정한다. 그럼에도 조건부 UPDATE가 0행이면(READ
  COMMITTED에서 상대 트랜잭션 커밋 후 `WHERE`가 재평가된 결과) **예외 대신 WARN 로그 후 스킵**한다 —
  해당 이용권의 잔여·이력은 건드리지 않는다. 이 캐스케이드 전용 복구는 `restorePassAfterSuspension`
  으로 별도 메서드에 담고, 단일 취소 경로(`restorePassAfterCancellation`)의 `IllegalStateException`
  의미는 그대로 둔다 — `cascade: Boolean` 플래그나 `Boolean` 반환으로 두 의미를 가르지 않는다
  (`ReservationRefundPolicy` KDoc이 금지한 "호출부의 즉흥적 반환값 분기"를 피하기 위해서다). 등록
  취소(`AdminPassService.cancel`)는 `cancelIfNotCanceled` 직후(이용권 행 잠금을 확보한 뒤)
  `existsByPassIdAndStatus`를 한 번 더 확인해, D-089 선행 검사~상태 전환 사이에 커밋된 활성 예약을
  잡아 `PassHasActiveReservationException`으로 거부·롤백한다.
- **409를 고르지 않은 이유**: 휴강 성패가 무관한 다른 회원의 이용권 상태에 좌우되면 안 되고, 관리자에게
  재시도 말고 할 수 있는 일이 없다(자기가 고칠 수 있는 입력 오류가 아니다) — 재시도 사이에 또 다른
  이용권이 취소되면 같은 실패가 반복될 뿐이다.
- 이유: 휴강 캐스케이드는 N건을 한 트랜잭션에서 순회하는 동안 무관한 다른 관리자의 등록취소가 끼어들
  수 있어, 스냅샷을 뜬 시점의 이용권 상태가 복구를 반영하는 시점엔 낡은 값이 될 수 있다(이슈
  #12/WR-02). 직전 재조회만으로는 창을 닫지 못한다 — 조건부 UPDATE 자체가 잠금 대기 후 `WHERE`를
  재평가하기 때문에, 재조회 시점엔 ACTIVE로 보여도 UPDATE 실행 순간 0행이 나올 수 있다. 그래서
  0행을 최종 방어(스킵)로 다룬다. 등록취소의 이중 검사는 D-089 선행 검사가 방어선이 아니라 정확한
  실패 사유를 주기 위한 사전 판정일 뿐이라는 기존 원칙(D-072 계열)을 그대로 확장한 것이다.
- 기각 대안: 캐스케이드 복구에 `cascade` 플래그 추가(같은 0행에 "예외"·"스킵" 두 의미가 섞여 호출부
  오용 시 조용히 복구가 사라짐), `Boolean` 반환 + 호출부 분기(`ReservationRefundPolicy` KDoc이 이미
  금지), 휴강 실패를 409로 반환(재시도로 해결 불가능한 실패를 사용자에게 떠넘김). 근거: 이슈
  #12(WR-02).

## D-146. 정기 시간표 외 임시 세션을 금지하고, 요일 검증을 `ClassSessionService.getOrCreate` 한 곳으로 내린다 (D-136 마감)

- 2026-08-31 / **보강 수업을 v1에서 허용하지 않는다.** 날짜별 수업(`ClassSession`)은 `시간표의 요일
  == 그 날짜의 요일`인 조합으로만 만들어지며, 검증은 **세션 실체화의 단일 초크포인트인
  `ClassSessionService.getOrCreate`** 안에 둔다(불일치 시 `ClassScheduleNotFoundException`, 404).
  예약·대리변경·휴강 세 경로는 종전대로 호출 전에도 같은 검사를 하지만, 검증의 근거는 이제 이
  한 곳이다. 조회 경로(`AttendanceService.getRoster`)는 세션을 만들지 않으므로 **그대로 둔다** —
  요일이 어긋난 조합은 지금처럼 빈 명단 200을 받고, 계약 동작이 바뀌지 않는다. policies §2에
  "정기 시간표 외 임시 세션은 v1에서 허용하지 않는다"를 명시했다. **[사용자 확정, 2026-08-31]**
- 이유: D-136이 미해결로 남긴 항목이다. 지점 검증은 저녁반 출석에 들어갔지만 요일 검증이 없어
  `AttendanceService.check`·`addEveningAttendance` 두 경로가 "열리지 않은 요일"에도 세션을 만들 수
  있었고, 저녁반 경로는 거기서 0.5회를 차감한다. 호출부마다 같은 `if`를 반복하는 방식은 새 쓰기
  경로가 생길 때마다 빠뜨릴 수 있어(실제로 출석 두 경로가 그렇게 빠졌다) 초크포인트로 내렸다.
  "보강을 허용할 것인가"는 운영 정책 질문이라 D-136이 코드로 먼저 정하지 않고 남겨 둔 것이고,
  이번에 소유자가 불허로 확정했다 — 필요해지면 시간표를 먼저 추가해 처리한다.
- 영향: `AttendanceEveningHalfTest`·`AttendanceServiceTest`·`AdminAttendanceControllerTest`의
  `nextClassDate()`가 연속된 날짜를 만들어 대부분 요일이 어긋나 있었다. 시간표 요일에 맞춰
  **1주씩** 증가하도록 정렬해 날짜 유일성(`uq_class_session`)을 유지했다.
- 기각 대안: 호출부 두 곳(`check`·`addEveningAttendance`)에만 `if` 추가(같은 누락이 재발할 구조를
  그대로 둠), 조회 경로도 404로 막기(세션을 만들지 않아 위험이 없는데 계약 동작만 바뀜 — FE가
  잘못된 조합을 호출하면 빈 화면 대신 에러가 뜬다), 보강 수업 허용(임시 세션의 정원·차감·알림 규칙이
  전부 미정 — v1 범위 밖).

## D-147. 미사용 차감 예외에 `INACTIVE`를 추가하고, 기준일 후보 ③을 "차감 제외 상태 이탈 시각"으로 확장한다 (WR-06 마감)

- 2026-08-31 / policies §4.3의 차감 예외를 3종(휴회·잔여 0·만료)에서 **4종**으로 늘려 회원 상태
  `INACTIVE`를 포함한다. 제외 상태 집합은 `MemberStatus.DEDUCTION_EXCLUDED`(= `ON_LEAVE`,
  `INACTIVE`) **하나가 근거**이며, 두 지점이 이 집합을 본다 — ① 배치 대상 조회 필터
  (`PassRepository.findMemberIdsWithDeductibleSessionPass`), ② 기준일 후보 ③ 기록 시점 판정
  (`AdminMemberService.changeStatus`). 후보 ③은 "휴회에서 벗어난 시각"에서 **"차감 제외 상태에서
  벗어난 시각"**이 되고, 제외 집합 **안에서의 이동**(`ON_LEAVE→INACTIVE`)은 기록하지 않는다.
  의미가 바뀌었으므로 컬럼도 `member.returned_from_leave_at` → `deduction_exclusion_exited_at`으로
  **리네임한다(V12)**. **[사용자 확정, 2026-08-31]**
- 이유: WR-06이 Phase 5부터 판단 없이 이월돼 온 항목이다. 탈퇴·장기 미이용 회원의 `SESSION_PASS`
  잔여가 2주마다 계속 깎여 0이 되면 나중에 환불 분쟁이 된다("시스템이 다 썼다고 한다"). 이용 의사가
  없는 회원에게 미사용 부채를 물리는 것은 §4.3의 취지(활동을 유도하는 유예)와도 어긋난다.
  후보 ③을 새 컬럼이 아니라 기존 메커니즘의 확장으로 구현한 것은 D-105가 "제외 기간에는 부채가
  쌓이지 않는다"를 **하나의 메커니즘**으로 표현했기 때문이다 — 컬럼·쿼리·후보를 2벌로 늘리면 그
  원리가 두 곳으로 갈라진다. `ON_LEAVE→INACTIVE`에서 기록을 멈추는 것은 CR-04의 보장을 약화시키지
  않는다: 기준일이 오히려 **더 늦어져**(실제 이탈 시점) 회원에게 불리해질 여지가 없다.
- 리네임을 택한 이유: `returnedFromLeaveAt`이라는 이름이 INACTIVE 이탈까지 기록하면 다음 사람이
  "휴회 전용"으로 오독해 INACTIVE 경로를 다시 빠뜨린다. Postgres `RENAME COLUMN`은 카탈로그만
  고쳐 테이블 재작성·긴 잠금이 없고, 기존 값은 새 의미의 부분집합이라 데이터 마이그레이션이 없다.
- 기각 대안: 컬럼명 유지 + 의미만 확장(이름과 실제가 어긋난 채 v1 마감 — 오독 위험), `INACTIVE`
  전용 컬럼 신설(같은 원리가 컬럼 2개·쿼리 2개·후보 2개로 갈라져 D-105의 "하나의 메커니즘"이 깨짐),
  제외 일수를 경과일에서 빼는 세 번째 메커니즘(RESEARCH Pitfall 2가 이미 기각).

## D-148. 출석 명단 항목에 `phoneNumber`를 싣는다 (BE-REQ-005)

- 2026-08-31 / `AttendanceRosterEntryResponse`에 nullable `phoneNumber`를 추가한다. 값은 명단
  조회가 **이미 fetch join으로 가져오는 회원**에서 꺼내므로 추가 쿼리가 없다. 온보딩 전 회원은
  `Member.phoneNumber`가 null이라 타입도 nullable로 맞춘다 — 표시의 필수 요소인 `memberName`은
  종전대로 non-null을 강제한다.
- 이유: 회원 식별의 기준이 "이름 + 전화번호"인데(policies §5.1) 명단이 이름만 실어 **동명이인을
  구분할 수 없었다.** 저녁반 출석 삭제는 0.5회 복구를 유발하므로(D-128) 잘못 고르면 엉뚱한 회원의
  잔여가 바뀐다 — Core Value("화면 숫자 = 서버 진실")를 직접 위협하는 유일한 계약 공백이었다.
- 기각 대안: FE가 회원별 `GET /api/admin/members/{memberId}`로 보강(명단 20명 × 추가 요청 = N+1,
  보드 응답성이 깨진다 — FE가 이미 거부하고 BE-REQ-005로 올린 방식), 이름에 전화번호 뒷자리를
  붙여 한 문자열로 반환(표시 규칙을 서버가 정해 버려 FE가 다른 조합으로 못 쓴다).

## D-149. 관리자용 회원 이력 조회를 신설하고, 응답 DTO를 회원용과 분리한다 (BE-REQ-003)

- 2026-08-31 / `GET /api/admin/members/{memberId}/pass-transactions`를 추가한다. 조건 DTO는
  회원 경로와 `PassTransactionSearchCondition`을 **공유**(필터·페이지네이션 모양이 같다)하되,
  **응답 DTO는 `AdminPassTransactionResponse`로 분리**한다. 관리자 응답만 `note`(D-070이 회원에게
  감춘 관리자 메모)와 `passStatus`를 담고, `passNotCanceled()` 조건을 붙이지 않아 **취소된 이용권의
  이력까지 포함**한다(D-073의 반대). 없는 회원은 빈 페이지가 아니라 404다(`getMemberPasses` 관례).
- 이유: PASS-05가 요구한 "관리자가 회원별 이력을 조회"에 대응하는 엔드포인트가 계약에 없어 FE가
  이력 섹션을 fixture 스토리로만 고정해 두고 있었다. 응답을 하나의 DTO로 합치고 `note`를 호출부에
  따라 채우거나 비우면 FE는 "이 필드가 언제 오는지"를 계약이 아니라 관례로 알아야 한다 — D-070이
  조건부 필드를 기각한 이유와 같아서 스키마를 갈랐다. 취소 이력을 포함하는 것은 D-059가 관리자
  화면에 감사 가능성을 남긴 것과 일관된다.
- 기각 대안: 회원용 `PassTransactionResponse`에 nullable `note` 추가(조건부 필드 — FE 타입 불안정),
  회원 엔드포인트에 `memberId` 쿼리 파라미터 추가(회원 경로의 IDOR 방어가 "본인 스코프 고정"이라는
  단순한 불변식이었는데 조건부가 된다), 조건 DTO도 분리(필드가 완전히 같아 중복만 남는다).

## D-150. `GET /api/members/me/reservations`에 수업 날짜 기간 필터를 추가한다 (BE-REQ-004)

- 2026-08-31 / `MyReservationSearchCondition`에 `from`/`to`(수업 날짜 기준, **양끝 포함**)를 더하고
  `ReservationSpecifications.classDateBetween`(관리자 조회가 쓰던 조건)을 그대로 재사용한다.
  둘 다 생략하면 조건이 붙지 않아 **기존 호출의 동작이 그대로다**(하위 호환).
- 이유: 이 목록은 활성 예약만 반환하는데 지나간 예약도 계속 `ACTIVE`로 남는다 — 노쇼도 출석도
  상태를 바꾸지 않고(policies §3) 지난 예약을 종료 처리하는 배치도 없다. 정렬이 `classDate ASC`라
  **지난 예약이 앞을 채우고 다가오는 예약이 뒤 페이지로 밀린다.** 주 3회 이용 회원 기준 약 8개월
  뒤 100건을 넘어, page/size만으로는 "내 다음 수업"을 보여줄 수 없다(FE는 마지막 페이지부터 역으로
  긁는 우회로 버티고 있었다).
- 기각 대안: `upcoming=true` 불리언(오늘 기준이 서버에 박혀 "지난 예약 보기"를 만들 수 없다),
  지난 예약을 종료 상태로 바꾸는 배치 신설(원장에 영향이 없는 상태 전이를 위해 배치·멱등성·복구를
  새로 설계해야 하고, 예약 상태의 의미(D-090 "취소는 취소 상태 전환")가 흐려진다),
  기본 정렬을 내림차순으로 변경(계약 파괴 — FE 목록 순서가 뒤집힌다).

## D-151. 미사용 차감 cron은 기본 꺼짐을 유지하고, 운영 환경에서만 배포 후 D-130 절차로 활성화한다

- 2026-08-31 / `BATCH_INACTIVITY_SCHEDULER_ENABLED` 기본값 `false`를 **유지한다**
  (`application.yml`·`.env.example` 모두). CR-03(기준일 후보 ① 배선)이 Phase 6에서 닫혀 이제 켤 수
  있는 상태가 됐지만, **켜는 것은 코드 기본값이 아니라 배포 절차의 일**이다: 운영 환경에 한해
  D-130의 3단계(① 출석 배선 배포 확인 → ② 운영 환경 수동 실행 1회 검증 → ③ 환경변수 명시)를 거쳐
  활성화한다. 로컬·CI·스테이징은 계속 꺼 둔다. **[사용자 확정, 2026-08-31]**
- 이유: D-121의 fail-safe("설정을 빠뜨리면 꺼짐")를 유지하는 것이 핵심이다. 기본값을 `true`로
  되돌리면 환경변수를 잊은 배포에서 cron이 켜진 채 뜨고, 첫 실행이 곧바로 원장을 바꾼다 —
  이 배치는 잔여를 깎기만 하고 되돌리지 않는 단방향이라(D-127) 사고의 복구 비용이 비대칭이다.
  ②의 수동 실행 1회 검증에서는 **정책 시행일 하한(D-119, 기본 `2026-09-01`)** 때문에 시행일 전에는
  차감 0건이 정상이라는 점을 함께 읽어야 한다 — 0건을 "배선이 안 됐다"로 오독하지 않기 위해서다.
- 기각 대안: 기본값을 `true`로 복원(D-121이 철회한 사고 경로 재개방), 배포와 동시에 자동 활성화
  (수동 실행 1회 검증 단계가 사라져 저녁반 전용 회원 부당 차감을 운영에서 최초로 발견하게 된다),
  스테이징에서도 켜기(스테이징 데이터로는 ②의 대조가 성립하지 않는다 — 06-VERIFICATION 참조).

## D-152. 테스트 CI는 단일 job `CI / build`로, FE와 같은 트리거 설계(PR+push, paths-ignore 없음)를 쓴다

- 2026-09-01 / `.github/workflows/ci.yml` 신설 — `pull_request`(dev·main) + `push`(dev·main)에서
  `./gradlew ktlintCheck build`를 단일 job(`build`)으로 돌린다. Testcontainers 통합 테스트는
  ubuntu-latest 러너에 기본 탑재된 Docker를 그대로 쓰고, 테스트 전제값은 build.gradle.kts의
  `systemProperty`가 주입하므로 서비스 컨테이너·`.env`·시크릿이 CI에 필요 없다.
  main 브랜치 보호 required status check 등록 절차는 README "CI · 브랜치 보호" 섹션이 정본.
- 이유: 레포 public 전환으로 main 보호가 다시 강제된다 — required check가 될 워크플로는
  경로 필터로 스킵되면 "Expected — waiting for status"로 머지가 영영 막히므로 paths-ignore를
  걸지 않는다(FE D-08과 동일 설계). push 트리거는 dev 직접 커밋이 검사를 빠져나가는 구멍을 막는다.
- 기각 대안: job을 lint·test로 분리(러너 2대 비용 대비 이득 없음 — `build`가 이미 ktlintCheck 포함,
  실패 원인도 로그로 구분 가능), Postgres 서비스 컨테이너(Testcontainers가 자급하므로 이중 설정),
  `generateApiDocs` CI 검증(로컬 Postgres 전제 태스크라 별도 설계 필요 — 필요해지면 그때 결정).

## D-153. 모바일 정본 폭은 360이고, 코드 반영은 Phase 8에서 한다 (FE v1.1 Phase 7)

- 2026-09-07 / FE v1.1 감사의 정본 뷰포트를 **360 / 768 / 1280**으로 확정한다. 레포 내 불일치는
  세 곳이 아니라 **다섯 곳**이다 — `e2e/responsive.spec.ts:54`=360, `.storybook/preview.tsx:63`=**390**,
  `.planning/PROJECT.md:27`=360, `playwright.config.ts`의 `mobile-safari`=`devices['iPhone 14']`=**390×664**,
  `.claude/skills/design-system/rules/patterns.md:526`=**390**. **Phase 7은 캡처를 360으로 찍고
  "정본=360 · 불일치 5곳"을 리포트에 기록만 한다.** `preview.tsx`와 `patterns.md`의 실제 변경은
  RULE-05 경로(감사 결과 우선 + 이 로그 기록)를 밟아 **Phase 8**에서 처리한다.
  `playwright.config.ts`의 iPhone 14(390)는 **기기 재현용 의도된 예외**로 유지하며 v1.1 내내 무수정이다.
  **[사용자 확정, 2026-09-07]**
- 이유: `.storybook/preview.tsx:63` 변경은 문서 수정이 아니라 **CI 게이트를 건드리는 코드 변경**이다.
  실측 결과 Vitest 브라우저 러너에서 `globals.viewport`가 실제로 적용된다(글로벌 없음=1200px,
  `mobile`=390px, `desktop`=1280px). `globals: { viewport: { value: 'mobile' } }`를 쓰는 스토리 파일이
  **63개**라 390→360은 그 63개 파일의 Mobile 스토리를 전부 360px에서 재렌더시키고, `pnpm test:stories`는
  CI 게이트다. Phase 7의 경계는 "감사 산출물만, 화면·코드 수정 0건"이므로 이 변경은 Phase 7 몫이 아니다.
  `patterns.md:526`이 390을 `lg`(1024) 예외의 **근거 문장**("E2E 두 프로젝트가 경계 양쪽에 깨끗이
  떨어진다")으로 인용하고 있어 함께 갱신해야 하는 것도 규칙 페이즈에서 다룰 일이다.
- 기각 대안: Phase 7에서 전부 반영(63파일 재렌더 리스크를 감사 인프라 페이즈가 떠안고, 스토리 실패 시
  "고쳐서 통과" 유혹이 감사 결과 자체를 오염시킨다), 캡처를 360·390 4뷰포트로 돌려 데이터로 정본 결정
  (장수 33%·실행 시간 증가에 AUDIT-01의 "3뷰포트" 문구까지 갱신 대상이 되는데, 정본은 이미 세 곳이
  360으로 다수결), Storybook을 그대로 두고 캡처만 390(정본이 소수 쪽으로 끌려간다).

## D-154. 감사 캡처 PNG는 전량 미커밋을 유지하고, Phase 11 대조 기준선은 touch-targets.json이 담당한다

- 2026-09-07 / `.planning/ui-reviews/.gitignore`의 현행 규칙(`*.png`·`*.webp` 등 전량 무시)을
  **수정 없이 유지한다.** 커밋 대상은 `REPORT.md`와 `touch-targets.json` 두 개다. Phase 11 VERI-01의
  "Phase 7 캡처와 대조"는 **이미지 대조가 아니라 `touch-targets.json`의 요소별 실측 px·판정
  before/after 대조**로 수행한다. 승인 검토용 이미지(대표 화면 + 위반 지점)는 REPORT.md PR의
  본문·코멘트에 첨부해 리뷰어가 레포 밖에서 볼 수 있게 한다. **[사용자 확정, 2026-09-07]**
- 이유: "PNG를 gitignore한다"는 신규 결정이 아니라 이미 시행 중인 규칙이다 —
  `git ls-files .planning/ui-reviews`는 `.gitignore` 1건만 반환하고, 기존 `03-20260809-002308/`의
  PNG 20장(892K)도 커밋된 적이 없다. 따라서 "대표 컷 소수 커밋"이 오히려 negation(`!`)으로 현행
  규칙을 뚫는 변경이며, 바이너리가 레포에 쌓이기 시작하면 되돌리기 어렵다. 대신 이미지가 없으면
  VERI-01의 대조 기준선이 사라지므로, 기준선의 정본을 **기계 판독 가능한 수치**로 옮긴다 —
  "런칭 전 필수 항목을 항목별로 해소 확인"(VERI-01)에는 픽셀보다 실측 px이 정확하다.
  PR 첨부는 승인 게이트의 근거 대조용이고 실기기 확인과는 별개 경로다.
- 기각 대안: 대표 컷 소수 예외 커밋(현행 규칙 변경 + "몇 장이 대표인가"라는 새 판단 부담),
  전량 커밋(D-04가 픽셀 스냅샷을 기각한 이유와 같은 부채 — 바이너리 기준선은 흔들리고 무거워진다),
  레포 밖 보관소(경로가 사람에게 종속돼 6개월 뒤 VERI-01이 수행 불가해질 위험).

## D-155. 감사 캡처 하네스는 두 갈래다 — MSW 워커 순회 + 서비스워커 차단 스텁

- 2026-09-07 / 캡처 파이프라인은 단일 순회가 아니다. **(A) MSW 워커 순회**가 기본이고
  (`webServer.env`의 `VITE_ENABLE_MSW=true`가 띄운 워커 + `src/mocks/handlers.ts` 기본 핸들러),
  **(B) `test.use({ serviceWorkers: 'block' })` + `page.route` 전면 스텁**을
  `/onboarding`·`/pending`·`/rejected`·`/inactive` 4화면 전용으로 둔다. **[사용자 확정, 2026-09-07]**
- 이유: `src/mocks/fixtures/member.ts:19`의 기본 `status: 'ACTIVE'` 때문에 `RequireAuth`의
  `resolveMemberDestination`이 저 네 라우트를 리다이렉트한다. 그리고 `e2e/auth-guard.spec.ts:29-41`이
  실측으로 못박아 둔 사실 — **`page.route`만으로는 뚫리지 않는다.** MSW가 응답을 만들면 네트워크로
  나가지 않아 Playwright가 가로챌 기회가 없고, 실측상 기본 활성 회원 응답이 이긴다. 워커 등록 자체를
  막아야 Playwright가 `/api/members/me`를 직접 응답할 수 있다. 대상 4화면은 전부 `AuthShell` 정적
  화면이라 스텁 표면이 좁다.
- 기각 대안: 목 핸들러에 상태 전환 스위치 신설(`src/mocks/**` 수정 = 498 스토리·108 E2E 공유 파일
  변경이라 Phase 7의 "코드 수정 0건" 경계를 깬다), 저 4화면을 감사 대상에서 제외(온보딩은 MEMB-04의
  명시적 해소 대상이라 감사에서 빠지면 Phase 9가 근거 없이 작업하게 된다).

## D-156. 감사 캡처의 결정론은 page.clock 단독으로 달성한다 — MSW 픽스처 수정 0건

- 2026-09-07 / 캡처 재현성은 **`page.clock.install({ time })`을 `goto` 전에 호출**하는 것만으로
  확보한다. `src/mocks/fixtures/**`에 고정 기준일 주입 수단을 만들지 않는다(수정 0건).
  **고정 기준 시각과 그 선정 사유는 `REPORT.md` 머리에 적는다** — 시각 선택이 화면 내용을 바꾸므로
  기준 시각 자체가 판정의 일부다. **[사용자 확정, 2026-09-07]**
- 이유: 최초 판단("픽스처가 서비스 워커 안에서 실행되므로 메인 스레드만 고정하는 `page.clock`으로는
  부족하다")은 **틀렸다.** `public/mockServiceWorker.js`(346줄)는 전달자일 뿐이다 —
  `client.postMessage({type:'REQUEST'})`(259행)로 클라이언트에 넘기고 `MOCK_RESPONSE`(270행)를
  기다린다. 핸들러와 픽스처는 **페이지 메인 스레드**에서 실행되므로 `page.clock`이 만든 가짜 `Date`를
  그대로 읽는다. 실증: `page.clock.install({ time: '2026-03-11T01:00:00Z' })` 후 `/reservations`를 열면
  시간표 헤더가 `3.9 ~ 3.15`, 요일 셀이 `월 9 … 일 15`로 렌더된다 —
  `startOfWeek(toLocalDate())`(`src/mocks/fixtures/board.ts:80,270,298` 등)가 가짜 시계를 따라갔다.
  픽스처는 498 스토리·108 E2E가 공유하므로, 불필요한 수정은 그 자체가 회귀 표면이다.
- 기각 대안: 픽스처에 `VITE_AUDIT_FIXED_DATE` 류의 주입 경로 신설(불필요할 뿐 아니라 공유 파일에
  분기를 심는다 — 기본값 불변으로 설계해도 읽는 사람이 매번 확인해야 하는 부채), 캡처를 특정 요일에만
  실행(사람 절차에 의존해 "두 번 실행해도 같은 화면"(AUDIT-01)이 성립하지 않는다).

## D-157. 터치 타깃 계층 판정은 data-slot/data-size 기반 중앙 매핑 파일이 소유한다

- 2026-09-07 / 실측은 **휴리스틱 전수 스캔**(인터랙티브 role/태그 전부 + `boundingBox()`)이고,
  계층 판정(52/40/36/24)은 **중앙 매핑 파일 한 곳**(셀렉터 → 계층)이 소유한다. 화면 순회 코드에
  임계값을 흩지 않는다. 매핑 키는 shadcn이 **이미 DOM에 내보내는** `[data-slot]`·`[data-size]`를 쓴다
  (`src/components/ui/button.tsx:63-65`) — **코드에 `data-` 속성을 추가하지 않는다.**
  미매핑 요소는 조용히 통과시키지 않고 **"미분류"로 리포트에 노출**한다. 스캔 제외 대상은
  "생성물"이 아니라 **생성물이 만드는 비가시·보조 요소**(Radix dismiss 버튼, `aria-hidden`, `sr-only`,
  portal 잔여)와 두 벌 마운트의 숨은 쪽이다. **[사용자 확정, 2026-09-07]**
- 이유: 임계값이 화면마다 흩어지면 관리자 표 밀도(제품 핵심 가치)를 지키는 예외가 어디에 몇 개
  있는지 알 수 없게 되고, Phase 8 RULE-03 규격표를 이 파일에서 유도할 수 없다. `data-` 속성 추가가
  불필요한 것은 실측으로 확인됐다 — shadcn `Button`이 `data-slot="button"`·`data-variant`·`data-size`를
  이미 내보내고, 실측 치수는 `xs` h-6=24 / `sm` h-7=28 / `default` h-8=32 / `lg` h-9=36 / `icon` size-8=32다.
  "생성물 제외"를 문자 그대로 적용할 수 없는 이유는 **주 CTA 자체가 shadcn `Button`**이기 때문이다 —
  제외하면 감사 대상이 남지 않는다. 미분류를 노출하는 이유는 그 목록이 매핑 파일의 구멍을 드러내는
  유일한 신호이기 때문이다. 이 매핑 파일이 정하는 것은 "어느 셀렉터가 어느 계층인가"뿐이고,
  수치 자체(52/40/36/24)는 이미 확정된 값이다.
- 기각 대안: 화면별 수동 목록(20라우트 × 오버레이 5종을 사람이 유지 — 누락이 곧 감사 구멍),
  코드에 `data-touch-tier` 류 속성 추가(Phase 7의 "코드 수정 0건" 위반이자, 감사 도구를 위해
  프로덕션 마크업을 바꾸는 것은 순서가 거꾸로다), 미매핑을 기본 계층으로 자동 배정(오탐·누락이
  구분되지 않아 리포트 신뢰도가 무너진다).

## D-158. Phase 7 승인 게이트는 "실기기 확인 → 리포트 반영 → PR 머지" 순서다

- 2026-09-07 / AUDIT-04 승인 게이트의 절차를 확정한다. ① 캡처·실측·리포트 초안을 `feature/*`
  브랜치에 올린다 → ② 사용자가 자기 폰으로 현재 상태를 보고 "런칭 전 필수" 판정이 체감과 맞는지
  확인한다 → ③ 지적을 리포트에 반영한다 → ④ 사용자가 `dev`로 PR을 리뷰·머지한다.
  **그 머지 커밋이 Phase 8~10 수정 범위의 정본이다.** 448px Sheet `AttendanceStateGroup` 배치 결론
  (치수 교체 vs 레이아웃 재설계)도 이 시점에 확정된다. 커밋·푸시·PR 생성은 사용자가 지시할 때만
  수행한다. **[사용자 확정, 2026-09-07]**
- 이유: 로드맵 Phase 7의 verification checkpoint("실기기 확인 대기")와 AUDIT-04 승인 게이트가
  서로 다른 두 지점이 되면 "무엇이 승인본인가"가 흐려진다. 실기기 확인을 PR 머지보다 **앞에** 두는
  이유는, 리포트의 심각도 판정이 체감과 어긋날 경우 그 정정이 승인본에 들어가야 하기 때문이다 —
  머지 후에 고치면 정본이 두 번 바뀐다. 머지 커밋을 고정점으로 삼으면 Phase 8~10이 "어느 리포트를
  따르는가"를 커밋 해시로 지목할 수 있다.
- 기각 대안: PR을 먼저 올리고 리뷰 중 실기기 확인(지적 이력이 스레드에 남는 이점은 있으나 PR이
  열린 채 왕복이 길어지고, 승인 시점이 흐려진다), PR 없이 대화로 승인(가장 빠르지만 "머지 커밋 =
  정본"이라는 고정점이 사라져 Phase 8~10이 참조할 대상이 없어진다).

## D-159. FE Phase 8의 코드 경계는 "렌더 결과 불변"이고, 모바일 정본 폭 360 갱신 대상은 15곳이 아니라 실측 19곳이다

- 2026-09-08 / FE ROADMAP Phase 8의 Scope guard "문서만 바꾼다 — 코드 변경 0건"을
  **"`src/` 화면 코드 0건. 도구·설정·스킬 문서는 규칙 반영에 한해 허용. 색·radius 토큰 변경 0건"**으로
  정정한다 (D-153-3이 이긴다). 경계는 **렌더 결과 불변**이다 — 렌더된 UI가 바뀌는 변경(JSX 구조·`className`·
  토큰)만 0건이고, 주석·스토리 export 이름·스토리 doc·감사 도구 주석은 Phase 8에서 갱신한다.
  유일한 렌더 영향 변경은 `.storybook/preview.tsx`의 모바일 뷰포트 폭 390 → 360이며 이는 CI 게이트 상수다.
  390 → 360 갱신 대상은 계획(08-CONTEXT D-03)의 15곳이 아니라 **실측 19곳 / 16파일**이다 — 실행 중 grep으로
  4곳이 더 나왔다: `audit/schema.ts:20` · `audit/schema.test.ts:114` · `src/app/App.tsx:22` ·
  `src/features/notification/components/ActivityFeedList.stories.tsx:137`. 일부만 360이면 그 자체가 새 불일치라
  같은 범위에서 처리했다. `playwright.config.ts`의 `devices['iPhone 14']`(390)는 정본 폭 재현이 아니라
  실기기 재현이 목적이라 의도된 예외로 무수정이고(D-153-4), `audit/schema.test.ts:115`의 390은 거절 단언
  자체라 무수정이다. 360 재렌더에서 `pnpm test:stories` 75파일 504테스트 전수 통과(실패 0건).
  **[사용자 확정, 2026-09-08]**
- 이유: "문서만 바꾼다"를 문자 그대로 지키면 `src/` 주석의 "390px에서 78px/셀" 같은 틀린 수치가 남고,
  Phase 9·10이 그 주석을 근거로 읽는 사고가 난다. 렌더 결과를 경계로 두면 승인본 `71bb0e0`의 캡처·실측과
  대조 가능성은 유지하면서 근거 수치만 정합할 수 있다. 15곳과 19곳의 차이를 기록하는 이유는 07-REPORT의
  "6곳"이 *뷰포트 정본 정의*만 센 값이고 근거 인용처가 별도로 있다는 사실이 두 번 확인됐기 때문이다 —
  다음에 같은 종류의 전수 갱신을 할 때 표가 아니라 grep을 정본으로 삼아야 한다.
- 기각 대안: Scope guard를 문자 그대로 유지(틀린 수치 주석이 다음 페이즈의 입력이 된다), 15곳만 갱신하고
  4곳은 후속 처리(일부만 360인 상태가 새 불일치다), `playwright.config.ts`까지 360으로 교체(실기기 재현
  목적을 잃고 mobile-safari 프로젝트가 존재하지 않는 기기를 흉내 낸다).

## D-160. 터치 타깃 계층은 `nav`를 신설한 5개에서 동결하고, `nav`는 이름이 아니라 위치로 정의한다

- 2026-09-08 / RULE-03 규격표(`rules/touch-targets.md`)의 계층은 `primary-cta`(52) · `form-control`(40) ·
  `row-action`(36) · `dense-table`(24)에 **`nav`(44 · 인접 간격 요구 없음)**를 더한 5개이고, 여기서 **동결**한다.
  `nav`는 **셸의 하단 탭 바 또는 사이드바 내비 안에 있는 셀**로, 요소의 이름·라벨이 아니라 놓인 위치로
  판정한다. 이 배정으로 admin 6화면 하단 탭 마지막 셀 `#로그아웃하기`의 `gap 0` 위반 6건이 계층 오분류로
  닫히고, `pending`·`rejected`·`inactive`·`me`의 본문 `로그아웃하기` 버튼 12건(h 32)은 `row-action` fail로
  그대로 남는다. 여섯 번째 계층이 필요해 보이면 RULE-05 경로를 지난다. **[사용자 확정, 2026-09-08]**
- 이유: 승인본 산출물의 미분류 250건 중 201건이 내비 링크였고, 이것을 기존 4계층에 억지로 넣으면 하단 탭
  셀(55·56)이 `row-action`의 간격 8 요구에 걸려 국내 관례(카카오·토스·당근의 `gap 0` 탭 바)와 충돌한다.
  이름으로 정의하면 안 되는 이유는 `#로그아웃하기` 하나가 하단 탭·사이드바·본문 세 자리에 동시에 있기
  때문이다 — 이름 면제는 실제로 누르기 어려운 32px 본문 버튼 12건을 잘못 통과시킨다. 계층을 동결하는
  이유는 미분류를 메우려고 계층을 계속 늘리면 규격표가 판정 도구가 아니라 변명 목록이 되기 때문이다.
- 기각 대안: 이름 기반 정의(본문 버튼 12건 오면제), 계층 추가로 미분류를 계속 메우기(규격표 신뢰도 붕괴),
  `nav`에도 간격 8 요구(탭 바 셀 사이 틈이 그대로 오탭 구간이 된다).

## D-161. Phase 8이 새로 부여한 계층으로 생긴 위반은 심각도 "권장"에서 시작하고, 필수 승격은 RULE-05 경로로만 한다

- 2026-09-08 / 미분류 250건을 4그룹(내비 링크 201 → `nav` · 요일 선택기 28 → `form-control` · 알림 팝오버
  트리거 18 → `form-control` · `내 이용권 보기` 3 → `row-action`)으로 전부 배정한 결과 새로 fail이 되는 것은
  **알림 팝오버 트리거 18건(h 32 < 40) 하나뿐**이고, 이 18건은 **권장**으로 편입한다. 산술: 권장 누계
  68 + 18 = **86**, 같은 규격표의 `nav` 배정이 원래 권장 안에 있던 `gap 0` 6건을 닫으므로 **잔여 80**.
  누계와 잔여를 둘 다 기록한다. **승인본 "런칭 전 필수" 59건은 불변**이다. **[사용자 확정, 2026-09-08]**
- 이유: 규격표를 메우는 행위는 판정 가능성을 만드는 일이지 새 요구를 만드는 일이 아니다 — 배정이 승인본의
  작업량을 늘리면 Phase 9·10의 수정 범위가 승인 없이 커진다. 누계와 잔여를 모두 적는 이유는 어느 쪽을
  세는지에 따라 Phase 9·10 진척률이 6건 어긋나기 때문이다.
- 기각 대안: 신규 위반을 필수로 편입(승인본 정본이 조용히 늘어난다), 미분류를 배정하지 않고 남김(판정
  불가 상태가 규격표의 구멍으로 남는다), 알림 트리거 18건을 `size="icon"` 예외로 면제(32px 아이콘 버튼이
  실제로 누르기 어렵다는 사실을 감춘다).

## D-162. `form-control` 계층에 인접 간격 기준 8을 신설한다

- 2026-09-08 / `tierSpecs['form-control']`은 높이 40만 있고 `minGap`이 없어, 448px Sheet 반사실 시뮬레이션이
  간격 기준을 `row-action`(8)에서 빌려 왔다(`e2e/audit/sheet-448.audit.ts:135-141` 주석). RULE-03이
  `form-control`의 인접 간격을 **8**로 확정한다. Phase 10 ADMIN-03의 목표는 `AttendanceStateGroup`의
  **높이 40 + 인접 간격 8**로 완성된다 (현재 실측 h 28 · `gap-1` 4). **[사용자 확정, 2026-09-08]**
- 이유: 빌려 쓰는 기준은 기준이 아니다. 값이 8인 근거는 셋이다 — 이 시스템에 존재하는 유일한 간격 값이라
  같은 행 안에서 두 기준이 충돌하지 않고, Tailwind 기본 스케일 `2` = 8px라 4px 그리드를 깨지 않으며,
  448px 반사실에서 `gap` 4 → 8의 8px 증가에도 가장 좁은 360에서 여유 폭 156.56px · 줄바꿈 0건 ·
  가로 오버플로 0건이 확인됐다 (07-REPORT §근거 2 · `sheet448.verdict: "dimension-swap"`).
- 기각 대안: 간격 기준 없이 높이만(현재도 gap 4로 붙어 있어 연속 오탭이 남는다), 간격 12(스케일 안이지만
  다른 계층과 값이 갈라져 같은 행에서 기준이 둘이 된다), 폼 컨트롤 간격을 화면별로 정함(규격표의 존재
  이유가 사라진다).

## D-163. 하단 고정 CTA 제외 판정선은 화면 레벨 CTA에만 적용하고, `ResponsiveDialog` Sheet 푸터는 RULE-02(오버레이 자기 소유) 소관이다

- 2026-09-08 / RULE-01 제외 판정선은 **"주 CTA 활성화에 텍스트 입력 완료가 선행되는 폼 제출형 화면은 하단
  고정 CTA를 쓰지 않는다"**이다. 적용 결과 `AuthShell` 공유 화면 중 `/login`(입력 0)만 적용이고
  `/onboarding`(입력 2) · `/admin/login`(입력 2)은 제외다. 검색창 같은 보조 입력은 제외 사유가 아니다 —
  레포의 `type="search"` 3곳(`MemberFilterBar` · `ReservationFilterBar` · `EveningMemberSearch`) 어느 것도
  이 단서로 제외되는 화면을 만들지 않는다(실측 0건). **이 판정선은 화면 레벨 CTA에만 적용된다** —
  `ResponsiveDialog`가 만드는 Sheet·Dialog 푸터는 대상이 아니며 RULE-02 소관이다. 포커스 시점 동적 처리
  (`visualViewport` 구독)는 하지 않는다. **[사용자 확정, 2026-09-08]**
- 이유: iOS 가상 키보드는 레이아웃 뷰포트를 줄이지 않아 고정 CTA가 키보드 밑에 남고(P2), `dvh`도 키보드를
  반영하지 않으며, 데스크탑 devtools와 Playwright WebKit에는 키보드가 없어 전 스위트가 초록인 채로 실기기에서만
  깨진다. 오버레이를 경계 밖으로 두는 이유는 관리자 폼 Sheet 3종(`overlay-register-pass` ·
  `overlay-notice-form` · `overlay-member-status`)이 전부 "입력 + 저장" 구조라 판정선을 오버레이까지 읽으면
  Phase 10 ADMIN-01(Sheet 푸터 sticky + safe-area)이 통째로 "규칙상 불가"로 닫히기 때문이다.
- 기각 대안: 판정선을 오버레이까지 적용(ADMIN-01 봉쇄), 포커스 시점 동적 처리(KBD-01 백로그와 한 묶음이고
  화면마다 각자 구현하면 되돌릴 수 없다), 검색창 보유 화면까지 제외(실측상 해당 화면이 없고 규칙만 넓어진다).

## D-164. safe-area 소유자는 화면당 1곳이고 우선순위는 셸 하단 탭 > CTA 바이며, 오버레이는 자기 소유다

- 2026-09-08 / `env(safe-area-inset-bottom)`을 계상하는 요소는 **화면당 1곳**이고 최하단 고정 요소가 갖는다.
  우선순위: ① 셸 하단 탭 바(`sm:hidden`) → ② 화면 레벨 CTA 바(하단 탭이 없는 `AuthShell` 화면에서만) →
  ③ Sheet·Dialog 오버레이는 셸 위에 뜨므로 **자기 소유**. 현재 소유자는 `MemberShell.tsx:128` ·
  `AdminShell.tsx:169` 두 곳뿐이고 둘 다 하단 탭이라 정합한다. `tokens.md` §임의값 예외의
  `pb-[env(safe-area-inset-bottom)]` 허용 범위는 "모바일 하단 탭 바에만"에서 "화면당 1곳인 최하단 고정
  요소"로 정합시키되 허용 개수는 그대로다. **`viewport-fit=cover`는 전제로만 기록한다** — `index.html:6`에
  현재 없어서 두 셸의 계상이 iOS에서 전부 0으로 계산되는 무효 상태이며, 추가는 Phase 9 MEMB-03 소관이다
  (렌더 결과가 바뀌는 변경이라 D-159 경계 밖). **[사용자 확정, 2026-09-08]**
- 이유: 하단 탭과 CTA 바가 각각 계상하면 iOS 실기기에서 하단이 34px 더 비어 보이고, 반대로 CTA가 탭 바를
  대체하는 화면에서 빼먹으면 버튼이 홈 인디케이터에 물린다. 이 버그는 **자동 검증이 원리적으로 불가**하다 —
  Playwright 디바이스 에뮬레이션은 safe area를 시뮬레이션하지 않아 `env()`가 항상 0이고, mobile-safari E2E·
  감사 스크린샷·스토리북 전수가 버그 유무와 무관하게 같은 그림을 낸다(P3 · 07-REPORT §검사 범위와 한계 #1).
  그래서 이 규칙은 검사로 거르는 것이 아니라 읽고 피하는 것이며, 잡히는 경로는 실기기 체크리스트 ①·②뿐이다.
- 기각 대안: 각 고정 요소가 각자 계상(이중 계상), `calc()`로 CTA 바가 탭 높이까지 합산(새 임의값 + 탭이
  사라지는 `sm` 이상에서 값이 틀어진다), Phase 8에서 `viewport-fit=cover`를 넣기(전 화면 여백이 바뀌는
  렌더 변경이라 before/after 캡처 대조 없이 넣을 수 없다).

## D-165. 영향표 판단 보류 2건을 규약으로 닫는다 — 관리자 셸은 `banner` 랜드마크를 두지 않고, 같은 일을 하는 액션은 문구를 같게 두고 `variant` 강도로 구분한다. `e2e/responsive.spec.ts` 단언은 수정하지 않는다

- 2026-09-08 / 승인본 §단언 영향표가 *"Phase 8 RULE-04가 정할 때까지 손대지 않는다"*로 남긴 2건을
  `rules/screen-templates.md` §영향표 판단 보류 2건에서 규약으로 확정한다. ① `responsive.spec.ts:398` —
  **관리자 셸은 `banner` 랜드마크를 두지 않는다. 사이드바가 `navigation` 1개이고 그것이 전부다.**
  (`AdminShell.tsx`에는 `<header>`가 없고 단언 `banner 0`은 현재 구현의 정확한 기술이다.)
  ② `responsive.spec.ts:692` — **같은 일을 하는 액션은 문구를 같게 두고 `variant` 강도로 구분한다. E2E는
  `.first()`로 헤더 액션을 지목한다.** (`AdminNoticeListPage.tsx:198,241` 주석이 이미 기록한 설계 결정이고,
  헤더 primary와 빈 상태 `outline`은 동시에 렌더되지 않아 D-19의 동시 노출 금지와 충돌하지 않는다.)
  재분류: 판단 보류 2 → 0, 계약 46 → **48** / 관측 27 / 양의 증거 11. `e2e/` 코드 변경 0건.
  **[사용자 확정, 2026-09-08]**
- 이유: 둘 다 "고칠 위반"이 아니라 "문서가 정하지 않은 규약"이다. `<header>`를 도입해 필수로 편입하면
  랜드마크 신규 추가라는 구조 변경 + 계약 단언 뒤집기가 되어 승인본 59건 밖으로 나가고, 액션 문구를 갈라
  strict mode를 피하면 이미 기록된 UI-SPEC §9-1 결정을 뒤집는다. 단언을 손대지 않는 이유는 "단언 완화로
  통과"가 어느 페이즈에서도 금지이기 때문이다(P9).
- 기각 대안: `AdminShell`에 `<header>` 신규 추가(구조 변경 + 필수 59 이탈), 빈 상태 액션 문구를 "첫 공지
  등록하기"로 분리(같은 일을 하는 버튼의 문구가 갈라져 사용자에게 두 액션으로 보인다), E2E 단언을 완화해
  판단 보류를 없앰(P9 위반).

## D-166. 실기기 체크리스트는 6항목으로 고정하고, 확인 페이즈 배정(① P9 · ② P10 · ③ P9·10 · ④ P11 · ⑤ P10 · ⑥ P9·11)을 확정한다

- 2026-09-08 / `rules/screen-templates.md` §실기기 체크리스트의 6항목 — ① 하단 탭 safe-area 실동작(공통 A)
  ② 하단 Sheet 푸터가 홈 인디케이터에 물리는가(공통 B) ③ iOS 가상 키보드가 CTA를 가리는가(온보딩·공지 폼)
  ④ iOS 26 하단 브라우저 컨트롤 겹침·실기기 폰트 렌더링 ⑤ 명단 패널 출석 3상태 연속 조작 체감(#46~48)
  ⑥ 한 손 조작 시 CTA 도달성·오탭 — 을 사용자가 자기 폰에서 실행 가능한 항목인지 확인했다.
  결과: **①~⑥ 전부 승인, 추가·제외 0건**, 확인 페이즈 배정도 그대로 확정. ④가 Phase 11인 이유는 브라우저
  크롬 겹침·폰트 렌더링이 특정 화면 수정의 결과가 아니라 전 화면 공통이라 최종 검증에서 한 번에 보는 것이
  맞기 때문이다. 접속 수단은 Phase 7에서 실패한 LAN IP 직결(secure context가 아니라 MSW 서비스 워커
  미등록)이 아니라 **HTTPS 터널 + `/admin/login` 목 우회, 또는 실 BE**다. 항목을 늘리거나 줄이려면 RULE-05
  경로를 지난다. Phase 11 VERI-03이 이 표를 항목별 통과/미통과로 채운다. **[사용자 확정, 2026-09-08]**
- 이유: 게이트마다 볼 것이 달라지면 "폰으로 보고 승인"으로 되돌아가고, 승인은 났는데 다음 페이즈에서 같은
  문제가 다시 나온다(P12). ⑥을 고른 사유는 07-REPORT §검사 범위와 한계가 명시한 항목 중 유일하게 미사용이고
  원리적으로 에뮬레이션 불가(터치 정확도·손가락 가림·한 손 도달 범위는 좌표로 표현되지 않는다)이며, 하단
  CTA 52px 재배치의 효과(엄지 존 도달)를 직접 검증하는 유일한 항목이기 때문이다.
- 기각 대안: P20(라벨 중복)·P23(짧은 콘텐츠에서 CTA가 화면 중간)을 체크리스트에 포함(스토리북 `Empty`
  스토리와 E2E가 이미 잡으므로 실기기 전용이 아니다), ④를 Phase 9·10 양쪽 게이트에서 반복(같은 확인을
  두 번 하고 결과 표시가 두 곳으로 갈라진다), 항목 수를 페이즈별로 가변(고정 목록의 존재 이유가 사라진다).

## D-167. 감사 결과가 기존 design-system 규칙과 충돌하면 감사 결과가 이기고, 사유를 이 파일에 D-번호로 기록한 뒤 반영한다 — 승인본 필수 59건을 바꾸는 유일한 통로이며, PR 검토용 캡처 커밋은 예외로 허용한다

- 2026-09-08 / RULE-05를 `design-system/SKILL.md` §감사 결과와 충돌할 때에 규칙으로 둔다. (a) 승인된
  Phase 7 감사 리포트(승인본 커밋 `71bb0e0` · `07-REPORT.md`)의 판정이 기존 design-system 문장과 충돌하면
  감사 결과를 따르고 문서를 고친다. (b) 바꾸는 이유는 항상 이 파일에 D-번호로 기록한다. (c) 승인본
  "런칭 전 필수" 59건을 바꾸는 유일한 통로가 이 규칙이다 — 심각도 승격·강등·신규 추가 전부. (d) **캡처
  커밋 예외:** *"PR 검토용 소수 컷은 `docs/pr-assets/<phase>-<slug>/`에 커밋 허용. 전수 캡처는
  `.planning/ui-reviews/`에 두고 `.planning/ui-reviews/.gitignore`의 `*.png` 무시를 유지한다."*
  실측: Phase 7 검토용 5장이 `docs/pr-assets/phase7-audit-report/`에 커밋 `8ee2cc5`로 들어가 있고, 루트
  `.gitignore`에는 `ui-reviews` 항목이 없으며 무시는 `.planning/ui-reviews/.gitignore:2`의 `*.png`가 담당한다.
  이 5장은 되돌리지 않는다. 07-REPORT §승인의 "승인본 커밋: 사용자 커밋 후 기입" 자리에는 `71bb0e0`을
  기입했고 리포트의 나머지 본문은 한 줄도 바꾸지 않았다. **[사용자 확정, 2026-09-08]**
- 이유: 문서만 고치고 기록을 건너뛰면 다음 사람이 같은 판단을 다시 하고, 리포트를 조용히 고치면 Phase 8~10이
  따르는 정본이 두 번 바뀐다. 캡처 예외를 "phase 디렉토리"가 아니라 실제 위치(`docs/pr-assets/`)로 적는
  이유는 원안 문구가 실측과 달랐기 때문이다 — 규칙이 존재하지 않는 경로를 가리키면 지켜지지 않는다.
  전수 캡처를 계속 무시하는 이유는 D-154(감사 캡처 PNG 전량 미커밋, 대조 기준선은 `07-touch-targets.json`)가
  이미 정한 바이고, PR 검토용 소수 컷만 리뷰 편의를 위해 허용하는 것이다.
- 기각 대안: 리포트를 직접 수정(정본이 조용히 바뀐다), 캡처 5장을 되돌리고 예외 없이 전량 무시(이미 머지된
  PR #43 이력을 고쳐 써야 하고 리뷰 편의를 잃는다), 예외 위치를 `.planning/phases/<phase>/`로 둠(원안 문구 —
  실제 커밋 위치와 달라 규칙이 처음부터 틀린 경로를 가리킨다).

## D-168. `audit/targets.ts` `TIER_SPECS`에 RULE-03 델타(`nav` 계층 · `form-control` `minGap: 8`)를 반영하는 일은 Phase 9·10의 화면 수정이 끝난 뒤 재감사 시점으로 미룬다

- 2026-09-08 / Phase 8은 규격표(`rules/touch-targets.md`)를 문서 정본으로 확정하는 데서 멈추고,
  `audit/targets.ts`는 **변경 0건**으로 둔다. 재감사 시점에 반영할 델타는 두 줄뿐이다 — `nav` 계층 추가
  (`minHeight: 44` · `minGap` 없음) · `form-control`에 `minGap: 8` 추가. 미분류 250건의 배정 결과와
  권장 누계 86 · 잔여 80의 산술은 문서에 실측으로 적어 두었으므로, 코드 반영 시 그 값이 재현되어야 한다.
  **[사용자 확정, 2026-09-08]**
- 이유: 지금 코드를 고쳐 재감사하면 산출물 `07-touch-targets.json`(승인본 `71bb0e0`)과 판정 기준이 달라져
  대조가 불가능해지고, Phase 11의 before/after 기준선이 사라진다 — 필수 59건 불변식의 기준선 자체가 흔들린다.
  Phase 9·10의 화면 수정 후 재감사 시점에 한 번에 반영하면 "규격표 변경에 의한 차이"와 "화면 수정에 의한
  차이"를 한 실행에서 나란히 볼 수 있다. D-157이 정한 "임계값은 매핑 파일 한 곳이 소유한다"는 원칙은 그대로다 —
  소유 위치가 바뀌는 것이 아니라 반영 시점만 뒤로 간다.
- 기각 대안: Phase 8에서 즉시 반영 + 재감사(승인본 산출물과 기준이 갈라져 Phase 11 대조 불가), 문서와 코드를
  영구히 분리(D-157 위반 — 임계값의 출처가 둘이 된다), `nav`만 먼저 반영하고 `minGap`은 나중에(델타가 두 번에
  걸쳐 들어가 재감사 결과의 원인 귀속이 어려워진다).

## D-169. 운영 전용 스프링 프로필을 만들지 않고 운영 값을 전부 env 키로 표현한다

- 2026-09-08 / Phase 7(컨테이너화·서버 구성)이 운영 배포용 설정을 준비하면서 `application-prod.yml` 같은
  별도 프로필을 신설하지 않고, 기존 `.env`/OS 환경변수 동일 키 방식(D-011 계열)을 그대로 확장했다. Hikari
  풀 크기·Tomcat 스레드 수·Swagger 토글을 전부 `${ENV_KEY:기본값}` 플레이스홀더로 `application.yml`에
  추가하고, 운영 서버 `.env`에서만 값을 덮어쓴다.
- 이유: 프로필을 만들면 "어느 프로필에서 어떤 값이 뜨는가"가 코드(프로필 파일)와 문서(`.env.example`) 두
  곳에 흩어진다. env 키 하나로 통일하면 로컬·운영이 같은 코드 경로를 타고, 값의 차이만 `.env`가 진다.
- 기각 대안: `application-prod.yml` 신설.

## D-170. Hikari `maximum-pool-size` 5, Tomcat `threads.max` 50으로 축소한다

- 2026-09-08 / `src/main/resources/application.yml`에 `DB_HIKARI_MAX_POOL_SIZE`(기본 5)·
  `SERVER_TOMCAT_THREADS_MAX`(기본 50) 플레이스홀더를 추가했다. RAM 1GB(t3.micro)·단일 지점 트래픽이
  전제이고, 배치의 `REQUIRES_NEW`(`BatchExecutionRecorder`)가 한 흐름에서 커넥션 2개를 쓰는 것이 최대
  소비 패턴이라 5로 충분하다. 스레드 200개(Tomcat 기본값)는 스택 메모리가 힙 밖에서 550M 컨테이너 한도를
  압박한다. 둘 다 env 플레이스홀더라 재배포 없이 값을 올릴 수 있다.
- 이유: 1GB 서버에서 기본값(풀 10 / 스레드 200)을 그대로 두면 커넥션·스레드가 실제 트래픽 대비 과잉
  할당돼 컨테이너 메모리 예산(app 550M)을 잠식한다.
- 기각 대안: 기본값 유지(풀 10 / 스레드 200).

## D-171. 운영 Swagger 차단은 `springdoc.*.enabled=false`로 하고 `SecurityConfig`는 손대지 않는다

- 2026-09-08 / `springdoc.api-docs.enabled`·`springdoc.swagger-ui.enabled`를 `${SWAGGER_ENABLED:true}`
  플레이스홀더로 바인딩하고, 운영 `.env`에서 `SWAGGER_ENABLED=false`로 덮어쓴다.
  `SwaggerDisabledTest`(`src/test/kotlin/com/goldwrestling/config/`)가 이 상태에서 `/v3/api-docs`·
  `/swagger-ui.html`이 404임을 회귀 테스트로 고정한다.
- 이유: `SecurityConfig`의 Swagger permitAll 목록은 `generateApiDocs`(D-029)가 로컬에서 401 없이
  스펙을 받아오기 위한 전제라 손대면 안 된다. springdoc을 비활성화하면 핸들러 자체가 등록되지 않아
  permitAll 경로여도 매핑된 핸들러가 없어 404가 난다. `env_file`이 점(.) 포함 키를 거부하므로
  `springdoc.*`을 env 이름으로 직접 쓰지 않고 평평한 `SWAGGER_ENABLED` 키로 간접 바인딩한다.
- 기각 대안: `SecurityConfig`의 permitAll 목록에서 Swagger 경로 제거(= `generateApiDocs` 파괴).

## D-172. 런타임 베이스 이미지는 `eclipse-temurin:21-jre-noble`로 OS 계열까지 명시 고정한다

- 2026-09-08 / INFRA-01의 "JDK 21 기반 최소 이미지"를 JRE로 충족하는 것으로 해석했다. 접미사 없는
  `21-jre` 태그는 현재 noble이 아니라 resolute로 매핑돼 있음을 `docker-library/official-images`
  정의로 직접 확인했다 — 짧은 태그는 배급사가 시간이 지나며 최신 Ubuntu LTS로 조용히 재매핑하므로
  프로덕션 Dockerfile에는 쓰지 않는다.
- 이유: 태그 없는 짧은 이름은 재현성을 보장하지 않는다. OS 계열명을 명시하면 재빌드 시점마다 베이스
  이미지가 조용히 바뀌는 사고를 막는다.
- 기각 대안: Alpine·distroless(musl 호환성·디버깅 도구 부재가 1인 운영 환경에서 손해가 더 큼).

## D-173. 앱 JVM 힙은 `JAVA_TOOL_OPTIONS`의 `-XX:MaxRAMPercentage=60`으로 시작하고 실측으로 확정한다

- 2026-09-08 / 550M 컨테이너 메모리 한도에서 힙 ≈330M, 비힙(메타스페이스·스레드 스택·코드 캐시·GC
  구조체) 여유 ≈220M을 확보한다. 초안이던 70%는 여유가 165M뿐이라 Boot+Hibernate의 일반적인 비힙
  사용량(150~250M) 상단에서 컨테이너 OOM kill 경계에 걸린다. 이미지가 아니라 compose의
  `JAVA_TOOL_OPTIONS`로 주입해 재빌드 없이 조정 가능하게 한다. 실서버 수동 배포에서 `docker stats`로
  RSS를 실측해 최종 비율을 확정한다(Phase 7 후속 플랜).
- 이유: 컨테이너 메모리 인지 힙 사이징(`-XX:+UseContainerSupport` + `-XX:MaxRAMPercentage`)은 limit이
  바뀔 때마다 재빌드 없이 힙도 비례 조정된다.
- 기각 대안: 고정 `-Xmx`(컨테이너 limit 변경 때마다 재계산·재빌드 필요), 70%(비힙 여유 부족).

## D-174. `/actuator/*` 외부 차단은 Caddy가 담당하고 `/actuator/health`만 통과시킨다

- 2026-09-08 / 앱 내부 노출 범위(`management.endpoints.web.exposure.include: health,info`)는 이미
  좁혀져 있으나, Caddyfile에서 `/actuator/health`만 통과시키고 `/actuator/info`를 포함한 나머지는
  전부 404로 차단한다.
- 이유: 엣지(Caddy)에서 한 겹 더 막으면 앱 설정 실수가 곧바로 외부 노출로 이어지지 않는 2중 방어가
  된다. 앱 내부 노출 범위(`show-details: never` 등)의 최종 확정은 Phase 9(OPS-05)가 잇는다.
- 기각 대안: 앱 설정만으로 차단(엣지 방어선 없이 단일 지점 실패에 노출됨).

## D-175. GHCR 이미지 패키지는 public으로 두어 서버 pull 인증을 없앤다

- 2026-09-08 / 레포(`gold-wrestling-be`)는 private을 유지하되, GHCR에 push하는 컨테이너 이미지
  패키지(`ghcr.io/minsu-zip/gold-wrestling-be`)는 public으로 둔다. 서버는 별도 인증 없이
  `docker compose pull`로 이미지를 받는다.
- 이유: 서버에 장기 PAT(write/read:packages)를 두지 않는 것이 유출 표면을 줄이는 실질적 이득이다.
  이미지 안에는 시크릿이 없다(전량 env 주입, D-169). 바이트코드 디컴파일로 도메인 로직·마이그레이션·
  API 표면이 노출되는 것은 소유자가 감수하기로 확정했다.
- 기각 대안: private 패키지 + 서버 상주 PAT.

## D-176. 레이어 추출 전에 jar를 `application.jar`로 이름 고정한다 — ENTRYPOINT 불일치의 근본 수정은 Dockerfile이 맡는다

- 2026-09-08 / Boot `tools extract --layers`는 application 레이어 안의 jar 파일명을 **입력 jar 이름 그대로** 남긴다.
  07-02가 `build/libs/*.jar`에 바로 extract를 돌려 레이어 안 파일이 `gold-wrestling-be-0.0.1-SNAPSHOT.jar`가 됐고,
  `ENTRYPOINT ["java","-jar","application.jar"]`가 기동 시 `Unable to access jarfile`로 실패했다(07-03 로컬 실기동에서 발견).
  공식 Boot 4.1 파셜 Dockerfile과 같이 빌더 스테이지에서 `cp build/libs/*.jar application.jar` 후 extract하는 형태로 고쳤다.
- 이유: 실행 명령(ENTRYPOINT)은 이미지의 책임이다. compose가 `find`로 jar를 탐색하는 우회는 Phase 8 배포 워크플로·
  `docker run` 단독 실행·공식 패턴 모두와 어긋나고, 이미지만 보고는 실행 방법을 알 수 없게 만든다. `cp`는 jar가 둘 이상이면
  실패하므로 조용히 하나를 고르는 일도 없다.
- 기각 대안: ① compose `entrypoint`/`command`의 `find` 우회(07-03이 임시 적용했다가 제거) — 실행 계약이 이미지 밖으로 샌다.
  ② `build.gradle.kts`의 `bootJar { archiveFileName = "application.jar" }` — 동작하지만 CI 아티팩트·로컬 `build/libs` 이름까지
  바뀌고 D-17("07-02는 build.gradle.kts 무변경")과 어긋난다. 이름 고정은 이미지 빌드 단계에서만 필요하므로 Dockerfile에 둔다.

## D-177. Caddy에 넘기는 `DOMAIN`·`ACME_EMAIL`은 compose의 `${VAR:?메시지}`로 `up` 시점에 필수 검사한다

- 2026-09-09 / 운영 compose의 caddy 서비스에 `DOMAIN: ${DOMAIN:?...}`, `ACME_EMAIL: ${ACME_EMAIL:?...}`를 둔다.
  Caddyfile의 `{$DOMAIN:localhost}` 콜론 기본값은 변수가 "아예 없을 때"만 적용되고, `.env`에 `DOMAIN=`처럼
  빈 문자열로 설정되면 적용되지 않아 빈 사이트 블록 파싱 오류가 난다. `email {$ACME_EMAIL}`은 기본값조차 없어
  빈 값이면 `wrong argument count`로 실패한다(둘 다 `caddy validate`로 실측). 두 경우 모두 Caddy가 재시작 루프에 빠진다.
- 이유: compose의 `${VAR:?}`는 unset과 빈 문자열을 모두 잡아 컨테이너가 뜨기 전에 사람이 읽을 메시지로 멈춘다.
  Caddyfile에는 조건문이 없어 파일 안에서 막을 방법이 없고, 가짜 이메일 기본값을 두면 Let's Encrypt 만료 알림이
  아무에게도 가지 않는 상태로 조용히 배포된다.
- 기각 대안: ① `{$ACME_EMAIL:admin@example.invalid}` 기본값 — 빈 문자열은 여전히 통과 못 하고 알림 유실 위험.
  ② 로컬 오버라이드에서만 값 주입(07-03 초안) — 운영 `.env`가 비어 있는 경로가 검증되지 않은 채 남는다.

## D-180. 카카오 로그인 버튼을 공식 디자인 가이드에 정합시킨다 — 브랜드 계약값 정정 (D-082 부분 supersede)

- 2026-09-09 / FE PR #53. 라벨 `카카오 로그인`(완성형) · 배경 `#FEE500` 유지 · 라벨 색 `#191919` → `rgba(0, 0, 0, 0.85)`(`#000000` 85%, 합성 ≈`#262200` 12.5:1) ·
  radius `rounded-[12px]` · `h-13`. 심볼은 공식 묶음에 단독 에셋이 없어 생략(FE `09-02-SUMMARY`, 심사 지적 가능성 인지). 폰트는 범위 밖
- 이유: v2 비즈앱 심사의 버튼 가이드 준수 대비. D-082의 "정확한 브랜드 값"이 전경색에는 부정확했다 — 팔레트 변경이 아니라 계약값 정정.
  D-082의 SDK SRI 결정은 유지되고 색·라벨·radius·심볼만 supersede 한다
- 기각 대안: 버튼 전체 PNG 삽입(반응형·disabled·스피너 불가) · 심볼 크롭/SVG 재작성(형태·비율 보증 상실) — 세부는 FE `tokens.md` §카카오

## D-181. design-system 브랜드 예외 2건 — 버튼 라벨 규칙 · `rounded-[12px]` 임의값

- 2026-09-09 / FE PR #53. (a) 외부 브랜드 가이드가 라벨을 지정하면 "동사+명사" 대신 그 문구를 쓴다(`카카오 로그인`, 다른 소셜 로그인도 동일).
  (b) `rounded-[12px]`를 `tokens.md` §임의값 예외 표에 등재 — 허용 범위 `/login` 카카오 버튼 1곳, `rounded-xl`(14px) 금지와 충돌 없음
- 이유: 규칙 문서만 보는 사람이 위반으로 오인하지 않게 예외를 먼저 기록한다("규약에 없는 결정은 문서에 추가한 뒤 사용")
- 계획된 (c) lucide-only 심볼 예외는 심볼 생략(D-180)으로 발생하지 않는다 — 3건이 아니라 2건

## D-185. 하단 고정 CTA 바·대화상자 푸터·하단 탭 바의 버튼 위 구분선(`border-t`)을 두지 않는다

- 2026-09-09 / FE PR #53 리뷰(사용자: "푸터 버튼 위 밑줄 전부 제거"). `ScreenActionBar` · `ResponsiveDialog` Sheet/Dialog 푸터 · `ConfirmDialog`(생성물
  `AlertDialogFooter` 기본 `bg-muted/50 border-t`) · 회원/관리자 하단 탭 `<nav>` · 관리자 사이드바 로그아웃 위 선. 생성물은 무수정, 소비처에서 `border-t-0 bg-transparent` 병합
- 이유: 콘텐츠가 짧은 화면에서 선이 본문과 CTA를 두 구획으로 갈라 보이게 한다. "구획은 border로"는 목록·섹션 규칙이고 하단 액션 바는 배경색으로 이미 구획된다.
  바 높이 77 → 76이라 `scroll-padding-bottom`(D-16 짝)과 스토리 단언을 같이 맞췄다
- 기각 대안: 그림자로 대체 — 정적 요소 shadow 금지와 충돌

## D-186. 주 액션 색을 무채색 다크에서 브랜드 골드 + 다크 텍스트로 바꾼다

- 2026-09-09 / FE `--primary: oklch(0.8 0.16 85)`(sRGB `#edb417 #0a0a0a 10.50`) · `--primary-foreground: oklch(0.145 0 0)`(sRGB ``), `.dark` 동일. 호출부 코드 변경 0건(생성물 `Button` 기본 변형).
  골드 램프는 `--primary`(0.80 배경) > `--brand`(0.65 얇은 인디케이터) > `--brand-strong`(0.55 텍스트) — 얇은 선·텍스트는 자기 색으로 3:1/4.5:1을 맞춰야 해서 합치지 않는다
- 이유: 사용자 판단 — 골드레슬링 브랜드와 카카오 노랑 옆에서 무채색 다크 버튼이 따로 논다. 기존 "골드는 대비가 안 나온다"는 **흰 텍스트 전제**였고,
  다크 텍스트면 `` on `#edb417 #0a0a0a 10.50` = :1(AAA — FE `src/lib/contrast.test.ts`가 oklch→sRGB 변환 포함 10~11 범위로 고정). 배경 자체의 흰 배경 대비 1.88:1은 텍스트 라벨이 있어 WCAG 1.4.11 대상이 아니다(D-180과 같은 논리). 새 금지: `text-primary`(`variant="link"`)
- 기각 대안: ① 카카오 `#FEE500` 그대로 — 카카오 버튼과 구별 불가 ② 기존 `--brand` #c47d04 — 앰버/갈색으로 읽힘 ③ 골드 + 흰 텍스트 — 1.88:1 불가.
  번호: D-178·D-179·D-182~D-184는 FE 09-08 예약이라 비워 뒀다
