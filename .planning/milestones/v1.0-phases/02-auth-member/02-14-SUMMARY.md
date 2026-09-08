---
phase: 02-auth-member
plan: 14
subsystem: member
tags: [kotlin, spring-boot, jpa, criteria-api, member-search, admin]

# Dependency graph
requires:
  - phase: 02-auth-member (02-09/02-10)
    provides: MemberSpecifications(검색·필터), AdminMemberService(승인·거절·상태변경) 원본 구현
provides:
  - "검색어가 하이픈만으로 이루어져도 전화번호가 있는 전 회원이 반환되지 않는다(WR-04)"
  - "이름이 공백만인 회원이 엔티티 판정과 승인 대기 목록 쿼리 양쪽에서 동일하게 온보딩 미완료로 취급된다(WR-05)"
  - "상태 변경 API(PATCH /status)로도 온보딩 미완료 회원을 ACTIVE로 만들 수 없다(WR-03)"
affects: [02-VERIFICATION]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "JPA Criteria Specification에서 술어를 mutableListOf에 조건부로 담아 criteriaBuilder.or(*predicates.toTypedArray())로 가변 개수 OR 조합"
    - "criteriaBuilder.trim()으로 SQL TRIM 컬럼 비교 — 코틀린 isBlank()와의 의미 차이를 KDoc에 명시하고 도달 가능한 범위로 보장을 좁힘"
    - "같은 정책 검사(온보딩 완료)를 강제하는 두 엔드포인트(approve/changeStatus)에서 화면 필터가 아닌 서비스 계층 검사로 중복 적용 — 정책은 항상 서버에서 강제한다(T-02-37)"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/goldwrestling/member/MemberSpecifications.kt
    - src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt
    - src/test/kotlin/com/goldwrestling/member/MemberSpecificationTest.kt
    - src/test/kotlin/com/goldwrestling/member/MemberStatusChangeTest.kt
    - docs/decisions.md

key-decisions:
  - "D-052: 상태 변경 API의 ACTIVE 전환도 온보딩 완료를 서버에서 강제"
  - "D-053: 통합 검색어는 정규화 결과가 빈 문자열이면 전화번호 술어를 만들지 않는다"

patterns-established:
  - "정책이 여러 엔드포인트에 걸쳐 있으면(승인 vs 상태 변경) 한쪽의 서버측 강제 근거(T-02-37)를 다른 쪽에도 반복 적용해야 우회 경로가 남지 않는다."
  - "판정 규칙이 엔티티 메서드와 Specification 두 곳에 중복 존재하면, 정합성 테스트(같은 조건의 스펙 결과와 엔티티 필터 결과 비교)로 어긋남을 지속적으로 검증한다."

requirements-completed: [MEMBER-01, MEMBER-02, MEMBER-03]

# Metrics
duration: 약 10분(사전 조사·독해 제외, 첫 프로덕션 커밋~마지막 커밋 기준 약 3분 26초 + 회귀 증명 실측·복원 시간)
completed: 2026-08-03
---

# Phase 2 Plan 14: 검색·온보딩 판정 정합화 + 상태 변경 API 우회 차단 (WR-03·WR-04·WR-05) 갭 클로저 Summary

**02-REVIEW.md의 Warning 3건(WR-03·WR-04·WR-05)을 닫았다 — 검색어 "-"의 전체 매칭 우회, 공백 이름의 온보딩 판정 불일치, 상태 변경 API의 승인 규칙 우회를 각각 재현 테스트와 함께 수정했다.**

## Performance

- **Duration:** 첫 프로덕션 커밋(11:16:30) ~ 마지막 커밋(11:19:56) 약 3분 26초. 회귀 방향 증명 2건의 실측·복원 시간 포함. 사전 조사·독해 시간 제외.
- **Completed:** 2026-08-03
- **Tasks:** 3/3 완료
- **Files modified:** 5 (모두 기존 파일 수정, 신규 파일 없음)

## Accomplishments

- `MemberSpecifications.keywordContains`가 `PhoneNumberNormalizer.normalize` 결과가 빈 문자열일 때 전화번호 술어를 아예 만들지 않도록 수정 — 검색어 `"-"`·`"--"`가 `LIKE '%%'`로 전화번호 non-null 전 회원을 반환하던 우회 경로(WR-04) 차단
- `MemberSpecifications.onboardingCompleted`의 name·phoneNumber `notEqual` 비교에 `criteriaBuilder.trim`을 적용해, `Member.isOnboardingCompleted()`의 `isNullOrBlank()` 판정과 공백 문자 범위에서 일치시킴(WR-05) — KDoc의 "반드시 같은 판정 규칙" 과장 문구도 실제 보장 수준(SQL TRIM은 공백 문자만 제거, 유니코드 공백 전체 아님)으로 정정
- `AdminMemberService.changeStatus`에서 `newStatus == ACTIVE && !member.isOnboardingCompleted()`를 검사해 `MemberStateConflictException`(409)을 던지도록 추가 — `approve()`가 강제하는 policies §5.1 규칙(T-02-37 "정책은 항상 서버에서 강제한다")을 같은 리소스의 다른 엔드포인트가 우회하지 못하게 함(WR-03). ACTIVE가 아닌 전이는 종전대로 자유
- `MemberSpecificationTest`에 공백 이름 픽스처(m7) 추가 + 기존 단언 2곳(hasStatus PENDING, onboardingCompleted false) 갱신 + 신규 테스트 2건(하이픈 전용 검색어, 공백 이름 온보딩 미완료)
- `MemberStatusChangeTest`에 신규 테스트 3건(온보딩 미완료 ACTIVE 전환 409+상태 유지, 온보딩 완료 ACTIVE 전환 200, 온보딩 미완료의 비-ACTIVE 전이 3종 허용)
- 회귀 방향 증명 2건 실측 후 수정 복원 (아래 상세)
- `docs/decisions.md`에 D-052·D-053 기록
- `./gradlew ktlintFormat && ./gradlew build` 전체 통과 (210건, 02-13 대비 +5)

## Task Commits

Each task was committed atomically:

1. **Task 1: 검색·온보딩 판정 술어 교정 (WR-04, WR-05)** - `5b8f5d8` (fix)
2. **Task 2: 상태 변경 API의 승인 규칙 우회 차단 (WR-03)** - `403c948` (fix)
3. **Task 3: 결정 기록 + 포맷·전체 빌드** - `00978ad` (docs)

_Note: 이 SUMMARY 커밋(및 있다면 STATE.md/ROADMAP.md 업데이트)은 오케스트레이터가 별도로 처리한다._

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/member/MemberSpecifications.kt` - `keywordContains`를 조건부 술어 리스트(`mutableListOf` + `criteriaBuilder.or(*predicates.toTypedArray())`)로 재작성, `onboardingCompleted`에 `criteriaBuilder.trim` 적용, 두 KDoc의 보장 문구를 실제 동작에 맞게 정정
- `src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt` - `changeStatus`에 ACTIVE 전환 온보딩 완료 검사 추가, KDoc에 "ACTIVE 전환만 유일한 예외" 근거 명시
- `src/test/kotlin/com/goldwrestling/member/MemberSpecificationTest.kt` - m7(공백 이름) 픽스처 신규, 기존 단언 2곳 갱신, 신규 테스트 2건
- `src/test/kotlin/com/goldwrestling/member/MemberStatusChangeTest.kt` - `persistMember` 헬퍼에 `name`/`phoneNumber` 선택 파라미터 추가(기본값은 기존 온보딩 완료 픽스처와 동일해 기존 테스트 영향 없음), 신규 테스트 3건
- `docs/decisions.md` - D-052·D-053 추가

## Decisions Made

- **D-052 (상태 변경 API의 ACTIVE 전환도 온보딩 완료를 서버에서 강제):** `changeStatus`는 `newStatus == ACTIVE`이면서 온보딩 미완료인 회원을 409로 거부한다. ACTIVE가 아닌 전이는 제한 없음. 기각 대안: 우회 허용 후 문서화만(정책이 엔드포인트마다 갈라짐), 모든 전이에 전이표 도입(policies §5.2의 관리자 재량 취지와 충돌). 상세 근거는 `docs/decisions.md` D-052 참고.
- **D-053 (통합 검색어는 정규화 결과가 빈 문자열이면 전화번호 술어를 만들지 않는다):** `keywordContains`가 빈 정규화 결과에서 전화번호 술어를 생략하고, `onboardingCompleted`는 SQL TRIM 기준으로 엔티티 판정과 맞춘다. 기각 대안: 검색어에서 하이픈 제거 후 blank 검사(정상 이름 검색을 훼손), 전화번호 컬럼 함수 인덱스(원인이 인덱스가 아님). 상세 근거는 `docs/decisions.md` D-053 참고.

## 회귀 방향 증명 실측 결과

**Task 1 (WR-04·WR-05):** `MemberSpecifications.kt`의 프로덕션 수정만 되돌리고(`MemberSpecificationTest`는 그대로 둔 채) `./gradlew test --tests MemberSpecificationTest --rerun` 실행 → `BUILD FAILED`, 신규 테스트 2건이 정확히 실패했다:
- `공백만 있는 이름은 온보딩 미완료로 취급된다()` FAILED
- `검색어가 하이픈뿐이면 전화번호 조건이 만들어지지 않아 전 회원이 매칭되지 않는다()` FAILED

이후 Edit으로 수정을 원복하고 `./gradlew test --tests MemberSpecificationTest --tests MemberSearchTest --rerun`으로 `BUILD SUCCESSFUL` 재확인 후 커밋했다.

**Task 2 (WR-03):** `AdminMemberService.changeStatus`의 신규 검사(3줄)만 제거하고 `./gradlew test --tests MemberStatusChangeTest --rerun` 실행 → `BUILD FAILED`, `이름·전화번호가 없는 회원을 ACTIVE로 바꾸면 409와 MEMBER_STATE_CONFLICT이고 상태는 그대로 PENDING이다()`가 `status().isConflict` 단언(파일 262행)에서 `AssertionError`로 실패했다 — 실제 응답이 200(관측대로)이었음을 확인. 이후 Edit으로 검사를 원복하고 `./gradlew test --tests MemberStatusChangeTest --tests MemberApprovalTest --rerun`으로 `BUILD SUCCESSFUL` 재확인 후 커밋했다.

두 증명 모두 `git stash`를 쓰지 않고 Edit 도구로 임시 되돌리기 → 실패 실측 → Edit으로 재적용하는 방식을 썼다(02-13 SUMMARY와 동일 관행).

## Deviations from Plan

**1. [면제 아님 — 문서화만] acceptance criteria의 `isOnboardingCompleted` grep 카운트가 예상(2)과 다르게 3으로 나옴.**
- **발견 시점:** Task 2 acceptance criteria 검증 중
- **원인:** 계획서는 "approve + changeStatus = 2건"을 예상했지만, `approve()`의 KDoc(`[Member.isOnboardingCompleted]를 서비스에서 직접 검사하는 이유는...`, 02-10에서 이미 존재하던 문구)이 코드 리터럴이 아닌 KDoc 인용으로 이미 1건을 차지하고 있었다 — 이 플랜이 만든 변화가 아니라 사전 존재 텍스트다.
- **판단:** 그레프 리터럴은 acceptance criteria의 보조 확인 수단일 뿐 기능 요구사항이 아니고, KDoc 인용을 굳이 다른 표현으로 바꾸면 오히려 가독성이 떨어진다고 판단해 코드는 그대로 두고 여기 문서화만 한다. 실제 정책 검증은 회귀 방향 증명(위)과 `MemberApprovalTest`/`MemberStatusChangeTest` 전체 통과로 이미 확인됐다.
- **영향받는 파일:** 없음 (수정하지 않음)

그 외에는 계획서대로 실행했다.

## Issues Encountered

None.

## User Setup Required

None - 외부 서비스 설정 변경 없음.

## Next Phase Readiness

- 02-REVIEW.md WR-03·WR-04·WR-05가 코드·테스트로 닫혔다.
- 남은 02-REVIEW.md Warning은 WR-02(관리자 로그인 타이밍 부채널)·WR-06(openapi.yaml 쿼리 파라미터 표현)이며, 이 플랜의 범위 밖이다(후속 갭 클로저 플랜 02-15 등으로 분리된 것으로 추정).
- `docs/api/openapi.yaml`은 이 플랜에서 변경되지 않았다(`git status --short docs/api/openapi.yaml` 출력 없음 확인) — 새 응답 코드(`MEMBER_STATE_CONFLICT`)는 이미 기존 계약에 존재해 API 계약에 영향 없음.
- `docs/error-codes.md`와 `ErrorCode.kt`도 이 플랜에서 변경되지 않음(기존 `MEMBER_STATE_CONFLICT`/409 재사용) — `git status --short`로 확인.

---

## 이번에 쓴 기술

1. **JPA Criteria API의 동적 술어 조합** — `Specification<T>` 람다는 `CriteriaBuilder`로 SQL `WHERE` 절을 코드로 조립한다. 이번 코드에서 실제 등장한 상황: 기존 `keywordContains`는 이름·전화번호 술어 2개를 **항상** 만들어 `criteriaBuilder.or(namePredicate, phonePredicate)`로 고정 결합했는데, 전화번호 정규화 결과가 빈 문자열이면 그 술어가 `LIKE '%%'`(모든 non-null 값과 매칭)가 되어 버렸다. 해결은 술어를 고정 2개가 아니라 `mutableListOf`에 조건부로 담고 `criteriaBuilder.or(*predicates.toTypedArray())`로 **가변 개수** OR을 만드는 것 — 전화번호 정규화 결과가 비었으면 그 술어 자체를 리스트에 넣지 않아 `WHERE`에서 아예 등장하지 않게 된다. 안 썼으면(고정 2-술어 구조를 유지했으면) "빈 조건이면 술어를 만들지 않는다"는 원칙을 지키려면 `namePredicate`만 반환하는 별도 분기를 코드 두 벌로 짜야 했을 것이다 — 조건이 하나 더 늘면(예: 이메일 검색 추가) 분기가 지수적으로 늘어난다.

2. **SQL `TRIM`과 코틀린 `isBlank()`의 의미 차이(★)** — 코틀린의 `String.isBlank()`는 유니코드 공백 문자(스페이스·탭·개행 등 `Character.isWhitespace` 기준) 전체를 공백으로 취급한다. 반면 PostgreSQL의 `TRIM(column)`(JPA `criteriaBuilder.trim()`이 생성하는 SQL)은 기본적으로 **스페이스 문자 하나만** 제거한다 — 탭이 들어간 `"\t"`는 `TRIM` 후에도 빈 문자열이 안 된다. 이번 코드에서 이게 왜 문제였는가: `MemberSpecifications.onboardingCompleted`가 엔티티의 `isNullOrBlank()`와 "반드시 같은 판정 규칙"이라고 KDoc에서 과장했는데, 사실은 SQL 계층과 애플리케이션 계층이 쓰는 도구 자체가 다른 정의를 가진 함수였다. 고친 방법은 보장 범위를 있는 그대로 좁히는 것 — "이 프로젝트의 현재 쓰기 경로(온보딩은 trim + `@NotBlank`로 검증)로는 탭·개행이 저장될 방법이 없으므로, 도달 가능한 스페이스 문자 케이스 안에서는 일치한다"고 명시했다. 안 썼으면(과장된 보장을 그대로 뒀으면) 나중에 새 쓰기 경로(예: v2 프로필 수정 PROF-01)가 trim 검증 없이 탭이 섞인 이름을 저장하면, 엔티티는 온보딩 미완료로 보는데 승인 대기 목록 쿼리는 완료로 보는 조용한 불일치가 재발했을 것이다.

3. **"정책은 서버에서 강제한다"가 화면 필터로 대체될 수 없는 이유** — 이 프로젝트는 이미 T-02-37로 이 원칙을 세워 뒀다: "관리자 목록 화면의 `status=PENDING&onboardingCompleted=true` 필터는 화면에 무엇을 보여줄지 정할 뿐, API를 직접 호출(Postman, curl 등)하는 관리자를 막지 못한다." 이번 결함(WR-03)이 실제로 증명한 것: `approve()`는 이 원칙에 따라 서비스 계층에서 온보딩 완료를 검사했지만, **같은 리소스의 다른 액션인 `changeStatus()`**는 검사가 없었다 — 화면에서는 "승인" 버튼만 노출해 우회를 못 느끼지만, `PATCH /status`로 직접 `ACTIVE`를 보내면 그대로 통과했다. 안 썼으면(이 검사를 changeStatus에 반복 적용하지 않았으면) "정책은 서버가 강제한다"는 이 프로젝트의 원칙이 **엔드포인트 하나만** 지키고 나머지는 안 지키는 상태로 남아, 관리자가 실수로(혹은 FE 버그로) `PATCH /status`를 잘못 호출하는 순간 이름·전화번호 없는 ACTIVE 회원이 실제로 생겼을 것이다(회귀 방향 증명에서 실측한 그대로).

4. **JPA `Specification`의 `null` 반환과 `Specification.allOf(listOfNotNull(...))` 조합** — 이번 코드에서 직접 등장한 것은 아니지만(기존 패턴을 그대로 따름), `keywordContains`·`onboardingCompleted`가 조건이 없을 때 `null`을 반환하는 이유를 다시 짚을 필요가 있었다: 호출부(`AdminMemberService.search`)가 `listOfNotNull(...)`로 `null`을 걸러내고 `Specification.allOf`로 리듀스하기 때문에, 각 함수는 "이 조건을 WHERE에 넣을지 말지"를 `null` 여부로만 표현하면 된다. 이번 수정에서 `keywordContains`의 전화번호 술어를 조건부로 만든 것도 같은 원리의 축소판이다 — 함수 전체를 `null`로 만드는 것과, 함수 **안의 개별 술어**를 조건부로 만드는 것은 같은 "필요 없는 조건은 아예 만들지 않는다"는 설계 원칙이 서로 다른 레벨(함수/술어)에 적용된 것이다.

## Self-Check: PASSED

- FOUND: src/main/kotlin/com/goldwrestling/member/MemberSpecifications.kt
- FOUND: src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt
- FOUND: src/test/kotlin/com/goldwrestling/member/MemberSpecificationTest.kt
- FOUND: src/test/kotlin/com/goldwrestling/member/MemberStatusChangeTest.kt
- FOUND: docs/decisions.md (D-052, D-053)
- FOUND commit 5b8f5d8
- FOUND commit 403c948
- FOUND commit 00978ad

---
*Phase: 02-auth-member*
*Plan: 14*
*Completed: 2026-08-03*
