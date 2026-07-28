# CLAUDE.md — gold-wrestling-be

> 이 파일을 `gold-wrestling-be/` 레포 루트에 `CLAUDE.md`로 저장한다.

## 프로젝트

골드레슬링 체육관 회원 관리·예약 시스템의 **백엔드**. Kotlin + Spring Boot 4.1.x + JPA + PostgreSQL.

## 문서 우선순위 (충돌 시 이 순서로 이긴다)

`docs/policies.md` > `docs/requirements.md` > `docs/glossary.md` · `docs/decisions.md` · `docs/conventions.md` > `.planning/**` > 코드

- `.planning/`(GSD 산출물)은 **실행 상태이지 스펙이 아니다.** 플랜이 policies.md와 어긋나면 policies.md가 이긴다.
- 스펙을 바꿔야 한다면 `.planning/`이 아니라 `docs/`를 고치고, 그 사실을 사용자에게 알린다.

## 반드시 지킬 것

1. **작업 시작 전 `docs/requirements.md`, `docs/policies.md`, `docs/glossary.md`를 읽는다.**
   도메인 규칙은 policies.md가 최종 기준이다. 코드와 문서가 다르면 코드가 틀린 것.
2. **코드를 쓰기 전 `docs/conventions.md`를 읽는다.** 패키지 구조·엔티티·트랜잭션·테스트 규약이 거기 있다.
   기존 코드가 규약과 다르면 규약이 맞고 코드가 틀린 것.
3. **네이밍은 `docs/glossary.md`만 따른다.** 새 개념은 glossary에 추가 후 사용. Ticket/Voucher/Booking 등 금지.
4. **API 응답 형태를 임의로 정하지 말 것.** springdoc으로 `docs/api/openapi.yaml`을 생성·갱신하고,
   API 변경 시 반드시 openapi.yaml을 다시 생성해 커밋한다. (FE가 이 파일로 타입을 생성한다)
   에러 응답은 RFC 9457 `ProblemDetail` 고정 (D-017). 커스텀 공통 래퍼 금지.
5. 설계 결정을 하면 `docs/decisions.md`에 3~4줄 기록한다.
6. 모든 차감/복구는 `PassTransaction` 이력을 남긴다. 이력 없는 잔여 횟수 변경 금지.
7. 시간대는 `Asia/Seoul` 명시. 주(week)의 시작은 월요일.
8. **요구사항이 모호하면 구현하지 말고 질문한다.** 소유자는 백엔드가 처음이라 요청이 덜 구체적일 수 있다.
   두 가지 이상으로 해석되고 해석에 따라 결과가 달라지면, 추측해서 진행하지 말고
   선택지와 추천안을 제시해 확인받는다. 조용히 정한 결정이 가장 비싼 실수다.
9. **모르는 API를 추측하지 않는다.** Boot 4 관련은 ① context7 MCP로 해당 버전 문서 확인
   → ② 버전은 maven-metadata.xml로 실제 조회 → ③ `./gradlew compileKotlin`으로 검증. 이 순서를 건너뛰지 않는다.
10. **프로덕션 코드를 추가·수정하면 같은 작업 안에서 테스트를 함께 작성한다.**
    무엇을 함께 써야 하는지는 `docs/conventions.md` §10.0 의 변경유형별 표가 기준이다.
    같은 표에 **면제 목록**(config 클래스, 필드만 있는 DTO, yml/gradle/문서)이 있으니 억지로 만들지는 않는다.
    테스트를 쓰지 않기로 판단했다면 **완료 보고에 그 이유를 한 줄로 밝힌다.**
    "나중에 추가"는 하지 않는다 — 다음 phase가 그 코드를 이미 전제로 삼아 버린다.

## 기술 규칙

- Kotlin + **Spring Boot 4.1.x (Spring Framework 7) 기준**, Gradle Kotlin DSL, JDK 21
- 의존성은 반드시 **Boot 4 호환 버전**을 선택한다. (예: springdoc-openapi는 3.x — 2.x는 Boot 3 전용)
- **Boot 3 기준 예제 코드를 그대로 복붙하지 말 것.** 4.x에서 바뀐 설정 키/패키지/API인지 확인 후 적용한다.
  (실제로 바뀐 것들: 스타터명 `spring-boot-starter-web`→`-webmvc`, Flyway·모듈별 `-test` 스타터 분리,
  Jackson 3 `tools.jackson`, Testcontainers 2.x `testcontainers-postgresql`,
  `@AutoConfigureMockMvc` 패키지 이동 → `org.springframework.boot.webmvc.test.autoconfigure`)
- DB 스키마 변경은 반드시 Flyway 마이그레이션으로 (ddl-auto 금지, validate만).
  **이미 커밋된 마이그레이션 파일은 절대 수정하지 않는다** — 새 버전을 추가해 고친다
- 코드 포맷은 **ktlint** 가 기준. 작업 마지막에 `./gradlew ktlintFormat` → `./gradlew build` 순서로 돌린다.
  스타일 규칙은 `.editorconfig` 에만 둔다 (D-024). import 목록 사이에 주석 금지 (자동 정렬 불가)
- 테스트: 도메인 로직(차감 정책, 예약 검증) 단위테스트 필수, DB 관련은 Testcontainers 통합테스트
- 동시성이 걸린 코드(예약 정원, 1:1 슬롯)는 DB 제약/락으로 보장하고 동시성 테스트를 함께 작성
- 예외는 전역 핸들러에서 일관된 에러 응답 형식으로 변환

## 코드 규약 요약 (상세는 `docs/conventions.md`)

- 패키지는 **기능별**(`member`, `pass`, `reservation`, `schedule` …) 안에 계층 (D-018)
- 엔티티: `data class` 금지 / `@ManyToOne`은 `fetch = LAZY` 명시 / `@Enumerated(EnumType.STRING)` 필수
- 횟수는 `BigDecimal`(DB `DECIMAL(4,1)`). **비교는 `compareTo`** — `equals`는 `0.5 != 0.50` (D-016)
- 컨트롤러는 DTO만 주고받는다. **엔티티를 API로 노출 금지** (D-019)
- 서비스 클래스에 `@Transactional(readOnly = true)` 기본, 변경 메서드만 오버라이드.
  컨트롤러·리포지토리에 `@Transactional` 금지 (D-020)
- 현재 시각은 `Clock` 빈을 주입받아 쓴다 (테스트에서 시각 고정 필요)
- 시간 타입: 날짜 `LocalDate` / 시각 `LocalTime` / 타임스탬프 `OffsetDateTime`(`timestamptz`).
  `LocalDateTime` + `timestamp` 조합 금지

## 개발 프로세스 (GSD)

- 개발은 GSD로 진행한다. **각 phase는 `/gsd-discuss-phase`로 시작**한다 —
  요구사항이 덜 구체적인 상태에서 바로 플랜을 만들면 AI가 빈칸을 임의로 채운다
- 플랜·실행 산출물은 `.planning/`에 쌓이지만, 스펙의 근거는 항상 `docs/`다 (위 문서 우선순위 참조)
- 반복 작업은 `.claude/skills/`의 절차를 따른다 (엔드포인트 추가, 마이그레이션, 테스트, Boot 4 확인)
- **GSD의 기본 TDD 방침을 이 프로젝트 규칙이 덮어쓴다.** GSD는 "단순 CRUD·glue code는 TDD 생략,
  테스트는 필요하면(if applicable) 추가"로 되어 있지만, 이 프로젝트에서는 **규칙 10과
  conventions.md §10.0 표가 기준**이다. 잔여 횟수·예약에 영향을 주는 코드는 CRUD로 보이더라도 테스트를 쓴다
- 이용권 차감·예약 검증처럼 입력→출력이 명확한 도메인 로직 phase는 **TDD 플랜으로 요청**한다
  (`/gsd-plan-phase` 시 TDD 여부를 명시). 배선·설정 phase는 표준 플랜 + 사후 테스트로 충분하다

## 학습 모드 (중요)

이 프로젝트는 소유자의 백엔드 학습을 겸한다. 소유자는 **백엔드 개발이 처음**이다.
- 복잡한 결정은 대안 비교를 함께 제시할 것
- 새로 등장한 개념은 "왜 이게 필요한가"를 1~2문장으로 덧붙일 것 (용어만 던지지 말 것)

### 작업 완료 보고에 `이번에 쓴 기술` 섹션을 반드시 포함한다

코드를 수정·개발한 뒤 결과를 보고할 때, **무엇을 했는지**와 함께 **거기에 어떤 백엔드 기법이 쓰였는지**를 설명한다.

형식 — 항목마다 이 3가지를 채운다:

1. **이름** (한 줄 정의)
2. **이 코드에서 왜 필요했는가** — 우리 도메인의 구체적 상황으로 설명
3. **안 썼으면 뭐가 깨지는가** — 실패 시나리오 한 줄

지킬 것:
- **이번 작업에 실제로 등장한 것만.** 일반적인 백엔드 강의 요약을 붙이지 않는다
- 용어를 던지고 끝내지 않는다.
  ✗ "비관적 락을 사용했습니다"
  ✓ "두 회원이 마지막 한 자리를 동시에 예약하면 둘 다 '자리 있음'을 읽고 둘 다 저장한다.
     먼저 읽은 트랜잭션이 그 행을 잠가 두면 뒤에 온 쪽은 기다렸다가 갱신된 값을 보게 된다 — 이게 비관적 락이다"
- **처음 등장한 개념은 `★`로 표시**한다 (나중에 복습 대상 식별용)
- 이 프로젝트에서 **일부러 쓰지 않은 것**과 그 이유도 짚어 준다 (예: 낙관적 락을 왜 안 골랐는지)
- 틀린 이해를 유발하는 비유는 쓰지 않는다. 정확한 설명이 조금 길어지는 편이 낫다
- 설명은 3~6항목 정도로 압축한다. 전부 나열하지 말고 **이번 작업의 핵심만**

자주 다룰 주제: 트랜잭션 경계·전파·격리수준, 락(비관/낙관)과 DB 제약, JPA 연관관계·페치 전략·N+1,
영속성 컨텍스트, 인덱스, 멱등성, 스프링 빈·DI·필터체인, 직렬화, 커넥션 풀, 마이그레이션

## 시크릿 규칙 (절대 준수)

- API 키, 시크릿, 비밀번호, 토큰 등 실제 값은 코드·설정파일·문서·커밋에 절대 포함 금지
- 로컬은 .env, 배포는 GitHub Actions Secrets와 서버 환경변수로만 주입
- 예시 코드 작성 시에도 실값처럼 보이는 문자열 금지 — 반드시 환경변수 참조 또는 명백한 플레이스홀더(YOUR_KEY_HERE) 사용
- 새 환경변수 추가 시 .env.example에 키 이름을 함께 추가

## 커밋·브랜치 규칙

- 브랜치: dev에서 작업하고 dev → main PR 머지 시 배포. main 직접 커밋 금지
- 커밋은 작업 단위로 잘게, Conventional Commits 형식 사용 (feat:, fix:, refactor:, docs:, test:, chore:)
- 커밋 메시지 본문은 한국어 가능, 제목은 형식 준수
- 하나의 커밋에 서로 다른 목적의 변경을 섞지 말 것

## 로컬 실행

- `docker-compose up -d` (Postgres) 후 부트 실행
- 브랜치: dev에서 작업, main 머지 시 GitHub Actions 배포 (EC2)
