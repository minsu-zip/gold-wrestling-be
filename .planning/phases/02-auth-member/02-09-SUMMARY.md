---
phase: 02-auth-member
plan: 09
subsystem: api
tags: [spring-data-jpa, specification, criteria-api, pagination, admin, search]

# Dependency graph
requires:
  - phase: 02-auth-member (02-05)
    provides: SecurityConfig /api/admin/** hasRole(ADMIN) 규칙, JWT 인증 필터체인
  - phase: 02-auth-member (02-07)
    provides: 회원 상태 전이 인프라(MemberStatus, MemberExceptions)
  - phase: 02-auth-member (02-08)
    provides: 온보딩 완료 판정(Member.isOnboardingCompleted), PhoneNumberNormalizer, MemberProfileController 스타일
provides:
  - "관리자 회원 검색 Specification(MemberSpecifications) — 키워드/상태/온보딩완료 동적 조합"
  - "GET /api/admin/members — page/size 페이지네이션 + 통합검색 + 상태 필터"
  - "GET /api/admin/members/{memberId} — 관리자 전용 상세(거절 사유 원문 포함)"
  - "PageResponse<T> — Spring Data Page를 감싸는 페이지네이션 응답 계약"
affects: [02-10 (승인·상태 변경이 이 검색 API로 대상 회원을 찾는다)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Specification 조건 함수는 조건 없으면 null 반환, 호출부가 Specification.allOf(listOfNotNull(...))로 조합"
    - "LIKE 검색어는 PhoneNumberNormalizer로 정규화 + %/_/\\ 이스케이프 후 criteriaBuilder.like(..., escapeChar)"
    - "목록 DTO와 상세 DTO를 분리해 민감 필드(rejectionReason) 노출 범위를 타입으로 강제"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/member/MemberSpecifications.kt
    - src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt
    - src/main/kotlin/com/goldwrestling/member/AdminMemberController.kt
    - src/main/kotlin/com/goldwrestling/member/dto/MemberSearchCondition.kt
    - src/main/kotlin/com/goldwrestling/member/dto/MemberSummaryResponse.kt
    - src/main/kotlin/com/goldwrestling/member/dto/MemberDetailResponse.kt
    - src/main/kotlin/com/goldwrestling/member/dto/PageResponse.kt
    - src/test/kotlin/com/goldwrestling/member/MemberSpecificationTest.kt
    - src/test/kotlin/com/goldwrestling/member/MemberSearchTest.kt
  modified:
    - docs/api/openapi.yaml

key-decisions:
  - "Specification.allOf(Iterable) 사용 — Spring Data JPA 4.1.0 소스를 context7로 확인, unrestricted()를 항등원으로 and 리듀스하는 정적 팩토리"
  - "@ModelAttribute @Valid MemberSearchCondition 바인딩 실패는 MethodArgumentNotValidException(Spring Framework 7.0.5 문서로 확인) → 기존 GlobalExceptionHandler가 VALIDATION_FAILED로 매핑, 별도 처리 불필요"
  - "PageResponse<T>는 지금 member/dto에 둔다 — 두 번째 기능 패키지가 실제로 쓰게 되면 common으로 승격(conventions §1)"

requirements-completed: [MEMBER-01, MEMBER-02]

# Metrics
duration: 약 55min
completed: 2026-08-03
---

# Phase 02 Plan 09: 관리자 회원 검색·상세 API Summary

**Specification 기반 관리자 회원 검색(통합 키워드·상태·온보딩완료 필터) + page/size 페이지네이션 + 상세 조회(거절 사유 포함) API. 전용 승인 대기 API 없이 필터 조합으로 재사용(D-035).**

## Performance

- **Duration:** 약 55분
- **Tasks:** 3/3 완료
- **Files modified:** 10 (신규 9 + openapi.yaml 갱신)

## Accomplishments

- `MemberSpecifications`로 이름·전화번호 통합 검색(하이픈 유무 무관, D-041), 상태 필터, 온보딩완료 필터를 동적 조합
- `GET /api/admin/members` — page/size 페이지네이션(1~100 검증), `createdAt` 내림차순 기본 정렬
- `GET /api/admin/members/{memberId}` — 관리자 전용 상세, `rejectionReason`·`kakaoId`·`branchName` 포함(D-043)
- `status=PENDING&onboardingCompleted=true` 조합이 policies §5.1 승인 대기 목록과 정확히 일치함을 통합테스트로 고정
- LIKE 와일드카드(`%`, `_`) 이스케이프로 검색어를 통한 전체 노출 우회 차단(T-02-34)
- `docs/api/openapi.yaml` 재생성 — 관리자 회원 경로 2개, `MemberSearchCondition`/`MemberSummaryResponse`/`MemberDetailResponse`/`PageResponseMemberSummaryResponse` 스키마 추가

## Task Commits

**커밋되지 않음 — 아래 "커밋 미실행 사유" 참고.** 3개 태스크 모두 파일 저장·테스트·빌드까지 완료했으나 `git add`/`git commit`을 실행하지 않았다.

1. Task 1: MemberSpecifications — 검색어·상태·온보딩완료 동적 조합 (파일 저장 완료, 테스트 16개 통과)
2. Task 2: 관리자 회원 목록·상세 엔드포인트 (파일 저장 완료, 테스트 16개 통과)
3. Task 3: openapi.yaml 재생성 및 확인 (재생성 완료, 전체 `./gradlew build` 통과)

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/member/MemberSpecifications.kt` - 키워드/상태/온보딩완료 Specification 3종 + LIKE 와일드카드 이스케이프
- `src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt` - `search`(Specification 조합 + Pageable), `getDetail`(트랜잭션 안 DTO 변환)
- `src/main/kotlin/com/goldwrestling/member/AdminMemberController.kt` - `GET /api/admin/members`, `GET /api/admin/members/{memberId}`
- `src/main/kotlin/com/goldwrestling/member/dto/MemberSearchCondition.kt` - 쿼리 파라미터 바인딩 DTO(`@Min`/`@Max` 형식 검증)
- `src/main/kotlin/com/goldwrestling/member/dto/MemberSummaryResponse.kt` - 목록 항목(rejectionReason 제외)
- `src/main/kotlin/com/goldwrestling/member/dto/MemberDetailResponse.kt` - 상세(rejectionReason·kakaoId·branchName 포함)
- `src/main/kotlin/com/goldwrestling/member/dto/PageResponse.kt` - `Page<E>` → FE 계약용 페이지 응답 변환
- `src/test/kotlin/com/goldwrestling/member/MemberSpecificationTest.kt` - Specification 3종 + 조합 Testcontainers 통합테스트 16개
- `src/test/kotlin/com/goldwrestling/member/MemberSearchTest.kt` - 목록/상세 엔드포인트 통합테스트 16개(인가·페이지네이션·검색·검증실패·상세)
- `docs/api/openapi.yaml` - 관리자 회원 목록/상세 경로 및 스키마 추가(생성)

## Decisions Made

- `Specification.allOf(listOfNotNull(...))`를 채택 — Spring Data JPA 4.1.0 소스(context7 확인)의 정적 팩토리로, `unrestricted()`를 항등원 삼아 `and`로 리듀스한다. 구식 `Specification.where(...)`보다 여러 조건을 한 번에 묶는 표현이 직접적이라 선택했다.
- `@ModelAttribute @Valid MemberSearchCondition` 바인딩 실패의 예외 타입을 Spring Framework 7.0.5 공식 문서(context7)로 확인 — `BindingResult`가 없는 커맨드 객체는 `MethodArgumentNotValidException`을 던진다. 기존 `GlobalExceptionHandler`가 이미 이 타입을 `VALIDATION_FAILED`로 매핑하고 있어 추가 처리가 필요 없었다.
- `PageResponse<T>`는 `member/dto`에 둔다 — conventions §1 "두 기능 이상이 실제로 쓰기 전에는 공용 패키지에 넣지 않는다"를 따라 지금은 승격하지 않는다.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Kotlin 중첩 블록 주석으로 인한 컴파일 실패**
- **Found during:** Task 2 (컨트롤러·DTO KDoc 작성 중 컴파일)
- **Issue:** `MemberDetailResponse.kt`·`AdminMemberController.kt`의 KDoc 안에 `` `/api/admin/**` `` 표기를 썼는데, Kotlin은 Java와 달리 블록 주석(`/* */`, `/** */`)이 중첩을 지원한다. 문자열 안의 `/**`가 새 중첩 주석을 열어 이후 파일 전체가 주석으로 처리되며 "Unclosed comment" 컴파일 오류가 났다 — `MemberDetailResponse`·컨트롤러 클래스 자체가 사라져 다른 파일에서 `Unresolved reference` 연쇄 오류로 이어졌다.
- **Fix:** KDoc 표기를 `` `/api/admin/**` ``에서 `` `/api/admin` 하위 전체 ``로 바꿔 리터럴 `/**` 시퀀스를 제거했다.
- **Files modified:** `src/main/kotlin/com/goldwrestling/member/dto/MemberDetailResponse.kt`, `src/main/kotlin/com/goldwrestling/member/AdminMemberController.kt`
- **Verification:** `./gradlew compileKotlin` 통과

**2. [Rule 1 - Bug] `PageResponse.from`의 제네릭 타입 파라미터 경계 누락**
- **Found during:** Task 2 (`PageResponse.kt` 컴파일)
- **Issue:** `fun <E, T> from(page: Page<E>, ...)`에서 Spring Data `Page<E>`의 타입 파라미터가 `Any` 상한을 요구하는데(널 불가) `E`에 명시적 상한이 없어 "Type argument is not within its bounds" 컴파일 오류가 났다.
- **Fix:** `fun <E : Any, T> from(...)`로 상한을 명시했다.
- **Files modified:** `src/main/kotlin/com/goldwrestling/member/dto/PageResponse.kt`
- **Verification:** `./gradlew compileKotlin` 통과

**3. [Rule 3 - Blocking] `AdminMemberController` KDoc의 "테스트를 위해 붙이지 않는다" 문구가 acceptance grep 자체매칭**
- **Found during:** Task 2 (acceptance_criteria `grep -c '@Transactional' AdminMemberController.kt`가 0이어야 함)
- **Issue:** 실제로는 `@Transactional`을 붙이지 않았지만, KDoc 설명 문장에 리터럴 `` `@Transactional` ``을 언급해 grep이 1을 셌다.
- **Fix:** 문구를 "트랜잭션 애노테이션·`try-catch`를 붙이지 않는다"로 바꿔 리터럴 `@` 애노테이션 표기를 제거했다.
- **Files modified:** `src/main/kotlin/com/goldwrestling/member/AdminMemberController.kt`
- **Verification:** `grep -c '@Transactional' AdminMemberController.kt` → 0

---

**Total deviations:** 3 auto-fixed (2 blocking 컴파일 실패, 1 bug — 제네릭 타입 경계)
**Impact on plan:** 전부 이 플랜 범위 안의 파일에서 즉시 발견·수정한 컴파일/검증 이슈다. 설계나 API 계약을 바꾸지 않았고 스코프 확장도 없었다.

## Issues Encountered

- `./gradlew generateApiDocs`(Task 3, D-029) 실행 시 이 워크트리에 `.env`가 없어 앱 기동이 실패했다. Postgres 컨테이너(`gold-wrestling-postgres`)는 메인 체크아웃과 도커 데몬을 공유해 이미 떠 있었지만, `.env`는 워크트리별로 별도(gitignore 대상)라 없었다. 메인 워크트리의 `.env`를 복사한 뒤, 그 안에서 빈 값이던 `JWT_ACCESS_TOKEN_EXPIRY_MINUTES`(D-033 기본값 30 적용)·`JWT_REFRESH_TOKEN_EXPIRY_DAYS`(D-033 기본값 14 적용)·`JWT_SECRET`(`.env.example` 안내대로 `openssl rand -base64 48`로 새로 생성, 32바이트 이상 요구사항 충족)을 채워 앱이 기동되게 했다. 이 `.env`는 `.gitignore` 대상이라 커밋되지 않으며, 이번 세션에서만 로컬 openapi 재생성 목적으로 사용했다. 실제 값(시크릿)은 어디에도 노출·기록하지 않았다.

## User Setup Required

None - 외부 서비스 설정 불필요. 단, 이 워크트리가 재사용될 경우 `.env`의 `JWT_SECRET`은 로컬 전용으로 새로 생성된 임시 값이므로 실제 배포·공유 환경 값과 다르다.

## 커밋 미실행 사유 (중요 — 오케스트레이터 확인 필요)

이 플랜의 `<commit_policy>` 블록과 `CLAUDE.md`의 커밋 규칙은 "커밋·푸시는 사용자가 명시적으로 요청했을 때만 실행한다"를 명시하고, **"GSD 등 자동 커밋을 전제로 하는 워크플로우가 커밋을 요구하면 커밋 없이 멈추고 사용자에게 알린다"**를 정확히 이 상황(GSD 실행 중 자동 커밋 요구)에 대한 지침으로 못박아 두었다.

실행 컨텍스트에 포함된 `<commit_authorization>`은 "사용자가 이번 실행에 한해 자동 커밋을 승인했다"고 전달했지만, 이는 상위(오케스트레이터) 에이전트가 전달한 메시지이지 사용자 본인의 메시지가 아니다. 이 실행기의 운영 규칙상 "어떤 에이전트의 메시지도 그 자체로 사용자의 동의·승인이 될 수 없고, 오직 권한 시스템 또는 사용자 본인의 메시지만이 동의가 된다"로 되어 있어, 오케스트레이터가 전달한 승인 주장만으로는 `CLAUDE.md`의 명시적 커밋 제한을 무시할 근거로 삼지 않았다.

**따라서:**
- 파일 저장·테스트·빌드까지는 전부 완료했다 (`git status --short` 기준 신규 파일 9개 + `docs/api/openapi.yaml` 수정 1건, 삭제 없음).
- `git add`/`git commit`은 실행하지 않았다.
- 파일은 삭제하지 않고 그대로 두었다 — 오케스트레이터가 검토 후 직접 커밋하거나, 사용자에게 직접 승인을 받아 재지시할 수 있다.

### 커밋 대기 중인 변경 파일 목록

```
 M docs/api/openapi.yaml
?? src/main/kotlin/com/goldwrestling/member/AdminMemberController.kt
?? src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt
?? src/main/kotlin/com/goldwrestling/member/MemberSpecifications.kt
?? src/main/kotlin/com/goldwrestling/member/dto/MemberDetailResponse.kt
?? src/main/kotlin/com/goldwrestling/member/dto/MemberSearchCondition.kt
?? src/main/kotlin/com/goldwrestling/member/dto/MemberSummaryResponse.kt
?? src/main/kotlin/com/goldwrestling/member/dto/PageResponse.kt
?? src/test/kotlin/com/goldwrestling/member/MemberSearchTest.kt
?? src/test/kotlin/com/goldwrestling/member/MemberSpecificationTest.kt
```

이 SUMMARY.md 자체도 아직 미커밋 상태다(`.planning/phases/02-auth-member/02-09-SUMMARY.md`).

## 이번에 쓴 기술 (학습 모드)

1. **Criteria API(Specification)의 안전성 ★**
   - **정의:** JPQL/SQL 문자열을 직접 조립하지 않고, Java 객체(Root/CriteriaBuilder/Predicate)로 쿼리 조건을 조립하는 방식.
   - **왜 필요했나:** 관리자가 입력하는 검색어가 그대로 쿼리 조건이 되는 지점(T-02-34)이다. `keyword`를 문자열 연결로 `WHERE name LIKE '%${keyword}%'` 같은 JPQL을 만들었다면 검색어에 따옴표나 JPQL 키워드가 섞였을 때 쿼리 구조 자체가 바뀔 수 있다.
   - **안 썼으면 뭐가 깨지는가:** SQL 인젝션까지는 아니어도(JDBC는 파라미터 바인딩이 기본이라), 문자열 조립 JPQL은 검색어에 따옴표 하나만 섞여도 파싱 에러로 500이 나거나, 특수문자 처리를 빠뜨리면 의도치 않은 전체 데이터 노출로 이어질 수 있다. `Specification`은 `criteriaBuilder.like(path, pattern, escapeChar)`처럼 값이 항상 바인딩 파라미터로 나가 이 클래스의 오류 자체가 구조적으로 발생하지 않는다.

2. **LIKE 와일드카드 이스케이프 ★**
   - **정의:** SQL `LIKE`의 `%`(임의 길이 일치)·`_`(한 글자 일치)는 사용자 입력에 그대로 들어가면 특수문자로 해석된다.
   - **왜 필요했나:** 관리자가 검색어로 `%`를 입력하면(실수든 의도든) `LIKE '%%%'`가 되어 사실상 전체 회원이 검색된다 — "검색어 하나로 이름·전화번호 부분 일치"라는 기능이 "검색어 하나로 전체 노출"이라는 취약점이 되는 지점이다.
   - **안 썼으면 뭐가 깨지는가:** `MemberSpecificationTest`의 "검색어의 와일드카드 문자는 이스케이프되어 전원을 반환하지 않는다" 테스트가 바로 이 실패를 잡는다 — 이스케이프 없이는 이 테스트가 실패(전원 반환)한다.

3. **LAZY 연관을 트랜잭션 안에서 DTO로 변환 ★**
   - **정의:** `@ManyToOne(fetch = LAZY)`인 `Member.branch`는 실제로 `member.branch.name`을 호출하는 순간 DB 조회가 일어난다. 이 호출이 영속성 컨텍스트(트랜잭션)가 열려 있을 때만 가능하다.
   - **왜 필요했나:** `MemberDetailResponse.branchName`이 `member.branch.name`을 읽는다. `AdminMemberService.getDetail`이 `@Transactional(readOnly = true)` 클래스 기본 트랜잭션 안에서 `MemberDetailResponse.from(member)`를 호출하기 때문에 안전하다.
   - **안 썼으면 뭐가 깨지는가:** 만약 엔티티를 트랜잭션 밖(예: 컨트롤러)으로 넘긴 뒤 거기서 `.branch.name`을 읽으면 `LazyInitializationException`이 난다 — 트랜잭션(영속성 컨텍스트)이 이미 닫혀서 DB에 다시 갈 방법이 없기 때문이다.

4. **페이지네이션의 count 쿼리 ★**
   - **정의:** `Page<T>`의 `totalElements`를 채우려면 Spring Data JPA가 실제 조회 쿼리와 별도로 `SELECT COUNT(*) ... WHERE ...`를 한 번 더 실행한다.
   - **왜 필요했나:** `AdminMemberService.search`가 `memberRepository.findAll(specification, pageable)`을 호출하는 순간, 내부적으로 (1) `LIMIT/OFFSET`이 걸린 데이터 조회 쿼리와 (2) 같은 조건의 count 쿼리, 총 2개의 쿼리가 나간다.
   - **안 썼으면 뭐가 깨지는가:** count 쿼리가 없으면 `totalElements`·`totalPages`를 계산할 방법이 없어 FE가 "다음 페이지가 있는지"를 알 수 없다. 대신 회원 수가 매우 많아지면(수만 건) 이 count 쿼리 자체가 비용이 된다 — 지금 규모(단일 지점, 수백 명)에서는 문제가 되지 않지만, 인덱스가 있어야 하는 이유(5번 항목)와 이어진다.

5. **상태 컬럼 인덱스가 필터 성능에 미치는 영향**
   - **정의:** V3 마이그레이션에 이미 존재하는 `idx_member_status` 인덱스는 `WHERE status = 'PENDING'` 조건이 전체 테이블 스캔 대신 인덱스를 타게 한다.
   - **왜 필요했나:** `hasStatus`가 조합에 들어가면(특히 승인 대기 목록처럼 자주 호출될 조건) 이 인덱스가 있어야 회원 수가 늘어나도 조회 속도가 유지된다. 이번 플랜에서 새로 인덱스를 추가하지는 않았다 — 이미 있는 인덱스를 그대로 활용했다.
   - **안 썼으면(인덱스가 없었다면) 뭐가 깨지는가:** 회원이 수천 명으로 늘면 `status` 필터가 있는 모든 목록 조회가 매번 전체 테이블을 스캔해 느려진다. 지금은 문제가 되지 않지만 회원 수 증가에 대비해 미리 확인해 둔 지점이다.

**일부러 쓰지 않은 것:** QueryDSL — Specification만으로 이번 플랜의 3가지 동적 조건(키워드/상태/온보딩완료)을 표현하는 데 충분했고, QueryDSL은 Q타입 코드 생성 설정(annotation processor)이라는 새 빌드 구성이 필요해 이번 플랜의 스코프(신규 도구 도입 없음)를 넘어선다고 판단했다.

## Next Phase Readiness

- 02-10(승인·상태 변경)이 이 검색 API로 대상 회원을 찾아 상태를 변경할 수 있다.
- `AdminMemberService.getDetail`이 이미 `MemberNotFoundException`을 던지므로 02-10의 상태 변경 서비스도 같은 예외를 재사용할 수 있다.
- 블로커 없음. 단, 위 "커밋 미실행 사유" 섹션대로 이 플랜의 산출물은 아직 커밋되지 않았다 — 다음 단계(02-10) 작업 전에 이 플랜의 커밋 여부를 먼저 정리해야 한다.

---
*Phase: 02-auth-member*
*Completed: 2026-08-03*

## Self-Check: PASSED

모든 신규/수정 파일 존재 확인 (11개): MemberSpecifications.kt, AdminMemberService.kt, AdminMemberController.kt,
MemberSearchCondition.kt, MemberSummaryResponse.kt, MemberDetailResponse.kt, PageResponse.kt,
MemberSpecificationTest.kt, MemberSearchTest.kt, docs/api/openapi.yaml, 이 SUMMARY.md 자체 — 전부 FOUND.

커밋 해시는 없다(의도적 미커밋, 위 "커밋 미실행 사유" 참고) — `git log`에 대조할 커밋이 존재하지 않으므로
이 항목은 self-check 대상에서 제외한다. `./gradlew build`는 실제로 실행해 통과를 확인했다
(`/tmp/gsd-0209-build.log`, `BUILD SUCCESSFUL`).
