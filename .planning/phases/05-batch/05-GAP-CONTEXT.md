# Phase 5 갭 클로저 — 확정된 설계 결정 (2026-08-16)

이 문서는 `/gsd:plan-phase 5 --gaps` 실행 전에 **사용자에게 직접 확인받아 확정한** 설계 판단이다.
플래너는 아래 결정을 **재검토 대상이 아니라 입력**으로 취급한다 — 다른 방식을 제안하지 말고 이대로 계획한다.

근거 문서: `05-VERIFICATION.md`(gaps_found, 1/4) · `05-REVIEW.md`(Critical 4 / Warning 9) ·
`.planning/ROADMAP.md` Phase 5 "갭 클로저 범위에 반드시 포함할 것" 블록.

작업 브랜치는 이미 생성돼 있다: `feature/phase-05d-gap-closure` (dev에서 분기, 05c 브랜치는 정리 완료).

---

## 1. 범위

### 닫는다

| ID | 내용 | 근거 |
|---|---|---|
| **CR-01** | 동시 실행 이중 차감 — `run()`을 직렬화하는 장치가 없다 | BATCH-04 실패 |
| **CR-02** | 캐치업 상한·정책 시행일 하한 부재 (킬 스위치는 D-116으로 확보됨) | BATCH-01 실패 |
| **CR-04** | `ON_LEAVE→INACTIVE→ACTIVE` 우회 시 `returned_from_leave_at` 미기록 | BATCH-02 실패 |
| **WR-05** | 수동 실행 API가 동기 호출 → 타임아웃 → 재시도 → CR-01 재현 | CR-01의 현실적 트리거 |
| **WR-02** | 배치 전체 실패 시 이력이 한 줄도 안 남고 `FAILED` 상태값도 없다 | CR-01 설계의 부산물로 함께 닫힌다 (§2.1) |
| **WR-04** | `BatchExecutionResponse.from`의 LAZY 프록시 계약 모순 | WR-05의 조회 API가 이 결함을 **실제 버그로 만든다** (§2.4) |

WR-02·WR-04는 "덤으로 끼워 넣는 것"이 아니다. §2.1의 `RUNNING` 행 설계는 실행 **시작 시점**에
이력을 먼저 쓰므로 WR-02가 구조적으로 닫히고, §2.4의 조회 API는 DB에서 다시 읽은
`BatchExecution`을 변환하므로 WR-04를 고치지 않으면 `LazyInitializationException`이 난다.

### 닫지 않는다

- **CR-03**(출석일 후보 부재로 저녁반 전용 회원 부당 차감) — ROADMAP Phase 5 Note에 "기준일 후보 ①은
  Phase 6까지 자연히 부재로 동작한다(의도된 동작)"로 문서화된 설계 결정이다. 이번 범위 밖.
  다만 D-116 킬 스위치가 있으므로 **운영 배포 시 cron을 꺼 둔 채 올린다**는 사실을 문서에 남긴다.
- WR-01·WR-03·WR-06~09, IN-01·02·04·05 — 후속 판단 사항. IN-03(POST 상태코드·응답 명세)만
  §2.4에서 API 형태가 바뀌므로 자연히 함께 정리된다.

---

## 2. 확정 결정

### 2.1 CR-01 — `batch_execution` `RUNNING` 행 + 부분 유니크 인덱스로 실행 전체를 직렬화

**선택안:** 실행 레코드 유니크. (기각: advisory lock, 원장 주기 유니크 인덱스, 회원 행 락+재검증)

**왜 이것인가**
- D-021 "DB 제약 우선" 원칙과 일치하면서, 제약이 걸리는 대상이 **원장(`pass_transaction`)이 아니라
  실행 이력(`batch_execution`)**이라 되돌리기 비용이 낮다 — V11에서 인덱스 `DROP` 한 줄이면 원복된다.
- WR-05(§2.4)와 WR-02를 별도 설계 없이 흡수한다.

**왜 다른 안이 아닌가 (플래너가 되묻지 말 것)**
- *비관적 락(Pass 행 `FOR UPDATE`)*: 여기서 일어나는 것은 lost update가 아니라 **조건 재평가 없는
  중복 실행**이다. 락을 걸어도 두 트랜잭션은 줄을 서서 둘 다 차감한다. 게다가 판정은 회원 단위인데
  락은 Pass 단위라, 회원이 `SESSION_PASS` 두 장을 가지면 서로 다른 장을 잡아 락이 겹치지도 않는다.
- *`pass_transaction` 주기 유니크 인덱스*: 유니크 키가 `(pass_id, reason, 주기)`면 성립하지 않는다 —
  차감 대상은 "만료 임박한 한 장"이라 같은 주기가 회차에 따라 다른 Pass에 꽂힌다. 키는 `(회원, 주기)`
  여야 하는데 `pass_transaction.member_id`는 **소유자가 아니라 조작 주체** 컬럼이고 `INACTIVITY`는
  그 값이 NULL이다. 소유 회원 컬럼 + 주기 키 컬럼을 원장에 신설해야 하고, 그 컬럼은 인덱스를 지워도
  영구히 남는다.
- *advisory lock*: 마이그레이션 0건이지만 락 상태를 운영에서 관측할 수 없고, 세션 스코프 락은
  커넥션 1개를 배치 내내 붙잡는 수명 관리를 직접 해야 한다.

**구현 형태**

Flyway **V10**(신규 — 커밋된 V1~V9는 절대 수정하지 않는다):
- `batch_execution.finished_at`의 `NOT NULL` 완화 (`RUNNING` 행은 아직 끝나지 않았다)
- `CREATE UNIQUE INDEX ... ON batch_execution (status) WHERE status = 'RUNNING'`
  — 동시에 존재할 수 있는 실행 중 행이 최대 1건임을 DB가 물리적으로 보장한다
- `status`는 `VARCHAR(20)`이고 값 CHECK가 없으므로 `RUNNING`·`FAILED` 추가에 DDL이 더 필요 없다
  (`BatchExecutionStatus` enum에만 추가). 이 사실을 마이그레이션 주석에 남긴다.
- 배치 종류가 하나뿐이라 `status` 단독 인덱스로 충분하다. 두 번째 배치가 생기면
  `batch_type` 컬럼 + 복합 부분 인덱스로 확장한다 — 이 확장 경로를 주석에 남긴다.

실행 흐름 (`InactivityBatchRunner`):
1. **시작** — `RUNNING` 행 INSERT. 유니크 위반(`DataIntegrityViolationException`)이면
   `BatchAlreadyRunningException`으로 변환한다.
2. **본문** — 기존 루프 그대로 (트랜잭션 없음, 회원 단위 실패 격리 D-112 유지).
3. **종료** — 같은 행을 `SUCCESS` / `PARTIAL_FAILURE` / `FAILED`로 UPDATE + `finished_at`·집계 기록.
   `try/catch`로 감싸 **어떤 종료 경로에서도 행이 `RUNNING`으로 남지 않게** 한다 (WR-02).

시작·종료 UPDATE는 러너 본문과 별개의 트랜잭션이어야 한다 — 러너에 `@Transactional`을 붙이면 안
되고(D-112), 같은 클래스 내부 호출은 프록시를 우회하므로 **별도 스프링 빈**으로 분리한다
(`InactivityDeductionService`가 이미 같은 이유로 분리돼 있다 — 05-01 결정 C).

**stale `RUNNING` 처리 (이 설계의 유일한 약점)**
앱이 죽으면 `RUNNING` 행이 남아 배치가 영구 차단된다. `started_at`이 임계 시간을 넘긴 `RUNNING` 행은
죽은 것으로 보고 `FAILED`(`errorSummary = "STALE"`)로 정리한 뒤 새 행을 넣는다.
임계 시간은 프로퍼티로 두고 **기본 30분**. 정리와 INSERT 사이에 경쟁이 나면 진 쪽이 409를 받는데,
그것이 안전한 결과다.

**에러 코드:** `BatchAlreadyRunningException` → **409 CONFLICT** + RFC 9457 `ProblemDetail`(D-017).
D-114의 "새 에러코드는 추가하지 않는다"는 **재검토 대상**이다 — "거부할 요청이 없다"는 전제가
이 결함으로 깨졌다. 새 `ErrorCode`를 추가하고 D-114에 정정 기록을 남긴다.

### 2.2 CR-02 — 캐치업 상한 = 회원당 1회 / 정책 시행일 = 2026-09-01

**상한: 배치 1회 실행에서 회원 1명당 최대 1회.**
매일 04:00에 도는 배치에서 정상 부족분은 0 또는 1이다. 2 이상은 배치가 2주 넘게 죽었거나 계산이
틀린 상황이므로, 하루 1회로 묶으면 사고가 나도 피해가 하루 1회씩만 누적돼 관리자가 킬 스위치를 켤
시간이 생긴다. 밀린 주기는 다음 날들이 이어받는다(D-106의 상태 기반 캐치업이 그대로 작동한다).

**정책 시행일: `2026-09-01`, `application.yml` 기본값 + 환경변수 오버라이드.**
이 날짜 이전의 미사용은 차감 부채로 치지 않는다 — "배치가 아예 없었던 기간"과 "배치가 며칠 죽었던
기간"을 코드가 구분하게 만드는 유일한 장치다. 기본값이 있어야 로컬·테스트가 설정 없이 돈다.

**구현 형태**
- `@ConfigurationProperties`로 `goldwrestling.batch.inactivity` 아래 묶는다:
  `policy-effective-date`(`LocalDate`, 기본 `2026-09-01`),
  `max-deductions-per-run`(`Int`, 기본 `1`),
  `stale-run-timeout`(`Duration`, 기본 `30m`).
  **주의:** D-116의 `goldwrestling.batch.inactivity-scheduler-enabled`는 `@ConditionalOnProperty`가
  원시 프로퍼티 이름으로 읽으므로 이 클래스로 옮기지 않는다 — 키를 바꾸면 D-116이 깨진다.
- `InactivityDueDateCalculator.resolveDueDate(candidates, policyEffectiveDate)` —
  후보 max가 시행일보다 이르면 시행일로 끌어올린다. 후보가 전부 null이면 여전히 null(판정 대상 아님).
  순수 계산이므로 시행일도 파라미터로 받는다(conventions §5, Spring 의존 금지).
- 상한은 러너 루프에서 `minOf(shortfall, maxDeductionsPerRun)`으로 적용한다.
- `.env.example`에 새 키를 추가한다(CLAUDE.md 시크릿 규칙).

### 2.3 CR-04 — "`ON_LEAVE`에서 벗어났을 때" 기록으로 조건 확장

`AdminMemberService.changeStatus`(현재 137~141행)의 조건을
`previousStatus == ON_LEAVE && newStatus == ACTIVE` → `previousStatus == ON_LEAVE && newStatus != ON_LEAVE`
로 바꾼다. 휴회가 끝난 시각이 곧 유예가 다시 시작되는 시점이므로 의미도 더 정확하다.
`ON_LEAVE→INACTIVE` 시점에 값이 채워지므로 이후 `INACTIVE→ACTIVE`에서도 기준일이 살아 있다.

D-111과 `docs/glossary.md`의 "`ON_LEAVE` → `ACTIVE` 전이 시각" 서술을 함께 고친다.
V9 마이그레이션 주석도 사실과 달라졌으나 **커밋된 마이그레이션은 수정하지 않는다** — V10 주석에
정정 사실을 남긴다.

### 2.4 WR-05 — 409 거부 + 202 비동기 + 실행 상태 조회

**세 가지를 모두 한다.**

- `POST /api/admin/batch/inactivity-runs`
  - `RUNNING` 행 INSERT는 **동기로** 수행한다 — 그래야 중복 요청이 즉시 409를 받는다.
  - 성공하면 배치 본문은 백그라운드로 넘기고 **202 Accepted** + `batchExecutionId`·`status=RUNNING`을
    즉시 반환한다. Tomcat 스레드를 배치 내내 붙잡지 않으므로 프록시·LB 타임아웃이 사라진다.
  - 비동기 실행은 전용 실행기(스레드 1개면 충분 — 어차피 유니크 인덱스가 동시 실행을 막는다)를
    명시적으로 등록해 쓴다. 스프링 기본 실행기에 암묵적으로 얹지 않는다.
- `GET /api/admin/batch/inactivity-runs/{id}` — 폴링용 단건 조회.
- `GET /api/admin/batch/inactivity-runs` — 최근 실행 목록. D-108이 "배치가 안 돌았는지는 실행
  이력으로 확인한다"고 했는데 지금은 확인할 수단이 없다(WR-02의 운영자 질문).

**WR-04는 여기서 필수 수정이 된다.** 조회 API는 DB에서 다시 읽은 `BatchExecution`을 변환하므로
`triggeredBy`가 진짜 LAZY 프록시다. 지금의 `BatchExecutionResponse.from`을 그대로 쓰면 컨트롤러에서
`LazyInitializationException`이 난다. `BatchExecution`에 `triggeredByAdminId` 스칼라를 두어 연관
접근 자체를 없애는 쪽을 우선 검토하고, 그 KDoc의 잘못된 안전성 서술도 함께 고친다.

**OpenAPI:** 엔드포인트가 1개 → 3개가 되고 상태코드도 바뀐다. `docs/api/openapi.yaml`의
"**동시 실행은 아직 안전하지 않다**"·"응답이 오지 않아도 재호출하지 않는다" 서술은 이번 수정으로
사실이 아니게 되므로 **반드시 다시 쓴다**. FE가 이 파일로 타입을 만든다.

---

## 3. 반드시 지킬 제약

- **테스트:** 동시성 수정이므로 `conventions.md` §10.4에 따라 **동시성 테스트 필수**.
  최소 두 가지를 단언한다 — ① `run()` 두 개를 동시에 호출하면 총 차감이 1회이고 한쪽은
  `BatchAlreadyRunningException`을 받는다, ② 수동 실행 API를 동시에 두 번 호출하면 정확히 하나만
  202를 받고 나머지는 409다. 기존 `InactivityDeductionConcurrencyTest`가 "세 스레드 동시 차감 →
  정확히 두 번 성공"을 정답으로 고정해 두었으니 **그 테스트가 여전히 맞는 계약인지 다시 판단**한다
  (deductOnce 레벨의 계약은 그대로일 수 있으나, 러너 레벨의 계약은 바뀐다).
- **검증 명령:** GSD 회귀 게이트는 이 Gradle 레포에서 no-op이다. 반드시
  `./gradlew cleanTest test`를 직접 돌린다 — `cleanTest` 없이 `test`만 돌리면 Gradle이
  `UP-TO-DATE`로 캐시 결과를 재사용해 "돌렸다"는 착각을 준다.
- **마무리:** `./gradlew ktlintFormat` → `./gradlew build` 순서.
- **OpenAPI 재생성:** `docker compose up -d` 후 `./gradlew generateApiDocs`로
  `docs/api/openapi.yaml`을 재생성해 **같은 커밋에 포함**한다.
- **Flyway:** V10부터. 커밋된 V1~V9 수정 금지.
- **로컬 DB 보존:** Phase 5 검증 데이터가 살아 있다 (member 4·5, pass 6·7, pass_transaction 38,
  batch_execution 1~3). 임의로 지우지 않는다. V10은 이 데이터 위에서 돌아야 하므로,
  기존 `batch_execution` 3건이 `finished_at` non-null·`status` 종료값이라 부분 유니크 인덱스와
  충돌하지 않음을 확인한다.
- **문서:** `docs/decisions.md`에 이번 결정을 기록하고 **D-108·D-114의 "중복 실행 안전" 서술을
  정정**한다. 정책 시행일 하한과 1회 실행 상한은 도메인 규칙이므로 `docs/policies.md` §4.3에
  명시하고, 새 용어는 `docs/glossary.md`에 추가한다 (CLAUDE.md 문서 우선순위: 스펙은 `.planning/`이
  아니라 `docs/`에 쓴다).
- **납품:** 단일 청크 `feature/phase-05d-gap-closure` → dev 대상 PR 1개.
  `.claude/skills/deliver-phase-chunk/SKILL.md` 절차를 따른다. PR 머지는 하지 않는다.
  플랜이 6개를 크게 넘거나 diff가 리뷰 불가능한 크기가 되면 그때 청크를 나눈다(D-084).

## 4. 완료 판정

`05-VERIFICATION.md`의 실패한 truth 3개가 모두 뒤집혀야 한다.

| Truth | 지금 | 닫는 근거 |
|---|---|---|
| BATCH-01 | ✗ | §2.2 시행일 하한 + 상한 1회 (CR-03은 범위 밖 — 킬 스위치로 대응) |
| BATCH-02 | ✗ | §2.3 휴회 이탈 전이 전체 기록 |
| BATCH-04 | ✗ | §2.1 `RUNNING` 유니크 + §2.4 409 거부, 동시성 테스트로 실증 |
| BATCH-03 | ✓ | 이미 통과 — 건드리지 않는다 |
