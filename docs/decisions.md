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
