---
quick_id: 260806-und
phase: quick/260806-und
plan: 01
subsystem: auth/member
tags: [kakao-login, profile, flyway, openapi, D-083]
requires: [D-025, D-047, conventions§1, conventions§3, conventions§7]
provides:
  - "member.kakao_nickname / member.kakao_profile_image_url (V5, 둘 다 nullable)"
  - "Member.applyKakaoProfile(nickname, profileImageUrl)"
  - "KakaoApiClient.fetchUserProfile(accessToken): KakaoUserProfile"
  - "MyProfileResponse.kakaoNickname / kakaoProfileImageUrl (FE 계약)"
affects:
  - "KakaoAuthService.login (카카오 조회 → 프로필 인자 전달)"
  - "MemberRegistrationService.findOrCreateByKakaoId (시그니처 확장)"
tech-stack:
  added: []
  patterns:
    - "Jackson 3(tools.jackson) 중첩 nullable 매핑 + @JsonNaming 중첩 클래스별 명시"
    - "JPA dirty checking으로 재로그인 시 프로필 갱신 (save/flush 호출 없음)"
key-files:
  created:
    - src/main/resources/db/migration/V5__add_member_kakao_profile.sql
    - src/main/kotlin/com/goldwrestling/auth/kakao/KakaoUserProfile.kt
    - src/test/kotlin/com/goldwrestling/member/MemberKakaoProfileTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/member/Member.kt
    - src/main/kotlin/com/goldwrestling/member/MemberRegistrationService.kt
    - src/main/kotlin/com/goldwrestling/member/dto/MyProfileResponse.kt
    - src/main/kotlin/com/goldwrestling/auth/kakao/KakaoUserResponse.kt
    - src/main/kotlin/com/goldwrestling/auth/kakao/KakaoApiClient.kt
    - src/main/kotlin/com/goldwrestling/auth/KakaoAuthService.kt
    - src/test/kotlin/com/goldwrestling/auth/kakao/KakaoApiClientTest.kt
    - src/test/kotlin/com/goldwrestling/auth/KakaoAuthControllerTest.kt
    - src/test/kotlin/com/goldwrestling/auth/KakaoLoginConcurrencyTest.kt
    - src/test/kotlin/com/goldwrestling/member/MemberProfileTest.kt
    - src/test/kotlin/com/goldwrestling/member/MemberApprovalTest.kt
    - src/test/resources/kakao/user-response.json
    - docs/decisions.md
    - docs/glossary.md
    - docs/api/openapi.yaml
decisions:
  - "D-083 BE 확정: 매 로그인 갱신 / 값 없으면 null 덮어쓰기 / MyProfileResponse에만 노출 / 640px profile_image_url"
  - "findOrCreateByKakaoId는 KakaoUserProfile이 아니라 String? 두 개를 받는다 — member 패키지가 auth.kakao에 의존하지 않게 (conventions §1)"
  - "createPendingMember도 생성자 인자 대신 applyKakaoProfile을 거친다 — 빈 문자열 정규화 경로를 한 곳으로"
metrics:
  tasks: 4
  commits: 5
  completed: 2026-08-06
---

# Quick Task 260806-und: D-083 카카오 프로필 수집·노출 Summary

카카오 로그인 시 `/v2/user/me`의 `kakao_account.profile`에서 닉네임·프로필 이미지 URL을 수집해
`member`에 저장하고 `GET /api/members/me` 응답에 nullable로 노출한다. 동의항목이 없어도 로그인은
정상 동작하며(값만 null), 재로그인마다 카카오 값으로 갱신·철회 시 null로 덮어쓴다.

## 실행 결과

| Task | 내용 | 커밋 |
|------|------|------|
| 1 | V5 마이그레이션 + `Member` 필드 2개 + `applyKakaoProfile` + 단위테스트 5케이스 | `86751b2` |
| 2 | `KakaoUserResponse` 중첩 매핑 + `KakaoUserProfile` + `fetchUserProfile` + 목킹 4파일 갱신 | `602063d` |
| 3 | 로그인 흐름 배선(저장·갱신) + `MyProfileResponse` 노출 + 통합테스트 6케이스 | `33d8fd7` |
| — | (선행 정리) 워킹트리에 있던 미커밋 FE 결정 D-074~D-083 기록 | `11bcc2a` |
| 4 | D-083 BE 확정 + glossary 2행 + openapi 재생성 | `7f363a9` |

**푸시하지 않았다.** 5개 커밋 모두 로컬 `dev` 브랜치에만 존재한다.

## 검증

- `./gradlew build` 그린 (Testcontainers가 V1~V5 재생 + 부팅 시 스키마 검증 포함)
- `grep -rn "fetchKakaoId" src/` → 0건 (구 메서드 잔존 없음)
- `grep -c "BE 미확정" docs/decisions.md` → 0
- `grep -v '^--' V5...sql | grep -c "NOT NULL"` → 0 (두 컬럼 모두 NULL 허용)
- `git diff docs/api/openapi.yaml` → `MyProfileResponse`의 두 필드 추가만, `servers: - url: /` 유지
- TDD 게이트: Task 1·2·3 모두 구현 전 테스트를 먼저 작성해 **RED(컴파일 실패 / 4케이스 FAILED)를 실측한 뒤** 구현했다.
  커밋은 프로젝트 스킬(add-migration §5 "마이그레이션+엔티티+테스트를 같은 커밋에")과 플랜 지시에 따라 task 단위 1커밋으로 묶었다.

### CONTEXT 필수 케이스 6종 커버리지

| 필수 케이스 | 테스트 |
|---|---|
| 프로필 저장·`/me` 노출 | `KakaoAuthControllerTest`(최초 저장) + `MemberProfileTest`(응답 노출) |
| `kakao_account` 없음 | `KakaoApiClientTest` `kakao_account 자체가 응답에 없으면...` |
| `profile` 없음 | `KakaoApiClientTest` `kakao_account 는 있지만 profile 이 없으면...` |
| null 덮어쓰기 | `MemberKakaoProfileTest`(단위) + `KakaoAuthControllerTest`(DB 실값) |
| 닉네임 갱신 | `MemberKakaoProfileTest`(단위) + `KakaoAuthControllerTest`(DB 실값) |
| 기존 테스트 회귀 없음 | `./gradlew build` 전체 그린 (동시성 테스트 포함) |

추가로 커버한 것: 부분 동의(`nickname` 키만 없음), 빈 문자열·공백 정규화.

## 계획과 달라진 점

### 1. `docs/decisions.md` 커밋을 2개로 분리 (Rule 3 — 블로킹 이슈 회피)

작업 시작 시점에 `docs/decisions.md`에 **미커밋 상태의 FE 결정 D-074~D-083이 이미 들어 있었다.**
플랜은 Task 4에서 이 파일을 커밋하라고 지시하지만, 그대로 커밋하면 "D-083 BE 확정"과 무관한 FE 결정
9건이 같은 커밋에 섞인다(CLAUDE.md 커밋 규칙 "하나의 커밋에 서로 다른 목적의 변경을 섞지 말 것" 위반).

- 조치: 기존 상태를 먼저 `docs: FE 결정 D-074~D-083 기록`(`11bcc2a`)으로 커밋한 뒤,
  D-083 확정 편집만 `7f363a9`에 담았다.
- `git stash`·부분 스테이징은 쓰지 않았다.

### 2. `.planning/STATE.md`를 갱신하지 않았다

quick task는 계획된 phase(현재 Phase 4 대기)와 별개라, `state advance-plan`을 돌리면 Phase 4 진행
위치가 잘못 전진한다. ROADMAP.md도 지시대로 건드리지 않았다.

그 외 자동 수정(Rule 1·2) 사항은 없다. 플랜대로 실행됐다.

## Known Stubs

없음. 두 필드는 실제 카카오 응답에서 채워지며, 값이 null인 것은 스텁이 아니라 **동의 없음이라는 정상 상태**다.

## 남은 운영 작업 (코드 밖)

**카카오 개발자 콘솔에서 `profile_nickname`·`profile_image` 동의항목을 추가해야 값이 실제로 채워진다.**
그 전까지 두 필드는 항상 `null`로 응답되며, 이는 오류가 아니라 설계된 정상 동작이다(D-083).

## Threat Flags

없음. 플랜 `<threat_model>`의 T-083-01(로그 미기록)·T-083-02(중간 노드 nullable)·T-083-04(`/me` 인가 경로 재사용)를
구현에 반영했고, 새로운 보안 표면(엔드포인트·인증 경로·파일 접근)은 추가되지 않았다.

---

## 이번에 쓴 기술 (사용자 보고용 재료)

### 1. JPA 변경 감지(dirty checking) — 같은 값을 넣으면 UPDATE가 아예 안 나간다 ★

- **무엇:** 영속성 컨텍스트는 엔티티를 조회할 때 필드 값의 스냅샷을 떠 둔다. 트랜잭션이 끝날 때
  현재 값과 스냅샷을 비교해 **달라진 것만** UPDATE 문으로 만든다. `save()`를 부르지 않아도 반영된다.
- **왜 필요했나:** "매 로그인마다 갱신하되, 실제로 바뀐 경우에만 UPDATE"라는 요구가 있었다.
  `applyKakaoProfile`에서 "값이 바뀌었나?" 비교 분기를 직접 짤 수도 있었지만, 그건 JPA가 이미
  하는 일을 손으로 다시 하는 것이다. 그래서 조건 없이 대입만 한다 — 닉네임이 그대로면 스냅샷과
  같으니 UPDATE 자체가 생성되지 않는다.
- **안 썼으면:** 로그인할 때마다 무의미한 `UPDATE member SET ...`이 나가 DB에 쓰기 부하와 행 잠금이
  쌓인다. 반대로 dirty checking을 믿지 못해 `saveAndFlush`를 넣었다면 매번 강제로 쓰기가 발생한다.
- **테스트에서 걸리는 함정:** 통합테스트는 `@Transactional`이라 커밋되지 않으므로, UPDATE가 실제
  DB로 나가는 시점이 없다. 그래서 `memberRepository.flush()`를 명시적으로 부른 뒤 `jdbcClient`로
  raw SQL 조회를 해야 "정말 DB에 반영됐는가"를 검증할 수 있다.

### 2. 트랜잭션 경계 밖의 외부 API 호출 — 값을 "인자로" 경계 너머에 넘긴다

- **무엇:** `KakaoAuthService`는 트랜잭션을 열지 않고, DB 쓰기는 `MemberRegistrationService`의 짧은
  트랜잭션 안에서만 일어난다. 카카오에서 받아 온 프로필 값은 **메서드 인자로** 그 경계를 넘긴다.
- **왜 필요했나:** 카카오 호출은 최대 5초까지 걸릴 수 있다. 이걸 트랜잭션 안에서 하면 그동안 DB
  커넥션 하나를 붙잡고 있게 된다. 커넥션 풀이 작은 이 프로젝트에서는 동시 로그인 몇 건만으로 풀이
  마른다. 그래서 "카카오 조회(트랜잭션 밖) → 값을 들고 트랜잭션 안으로" 구조를 그대로 유지했다.
- **안 썼으면:** 카카오가 느려지는 순간 로그인뿐 아니라 **예약·이용권 등 서비스 전체**가 커넥션
  부족으로 멈춘다. 외부 장애가 우리 장애로 번지는 전형적인 경로다.
- **함께 지킨 것:** 동시 최초 로그인 재시도 경로(`DataIntegrityViolationException` 후 1회 재시도)가
  **같은 프로필 값**으로 호출되도록 두 호출을 private 헬퍼 하나로 묶었다. 인자를 두 군데 적어 두면
  나중에 한쪽만 고쳐지는 사고가 난다.

### 3. named argument로 "같은 타입 인자 뒤바뀜"을 막기 ★

- **무엇:** `findOrCreateByKakaoId(kakaoId, kakaoNickname, kakaoProfileImageUrl)`에서 뒤 두 인자는
  둘 다 `String?`이다. 위치로 넘기면 순서를 바꿔 써도 컴파일러가 잡지 못한다.
- **왜 필요했나:** 닉네임 자리에 URL이 들어가도 타입은 맞으므로 테스트가 그 조합을 검사하지 않으면
  운영에서야 "내 닉네임이 https://..."로 보인다. Kotlin의 named argument는 이 실수를 호출부에서
  차단한다.
- **안 썼으면:** 조용히 잘못된 값이 DB에 저장되고, 컬럼 길이(100 vs 500) 때문에 어느 날 URL이
  닉네임 컬럼 길이를 넘겨 INSERT가 실패하는 식으로 뒤늦게 드러난다.

### 4. 외부 JSON 계약의 부분 응답 방어 — 중간 노드 전부 nullable ★

- **무엇:** 카카오 응답을 `KakaoUserResponse → KakaoAccount? → Profile?`로 매핑하되 **중간 객체를
  모두 nullable**로 뒀다. Jackson 3(`tools.jackson`)의 `@JsonNaming(SnakeCaseStrategy)`는 상속되지
  않아 중첩 클래스마다 따로 붙였다(`profile_image_url` → `profileImageUrl`).
- **왜 필요했나:** 카카오 개발자 콘솔에 동의항목이 없으면 `kakao_account` 키 자체가 응답에서 빠진다.
  non-null로 선언하면 그 순간 **역직렬화가 실패**하고, 실패 지점이 로그인 흐름 한복판이라 로그인
  전체가 막힌다. 우리가 아직 동의항목을 추가하지 않은 상태에서 배포해야 하므로 필수 조건이었다.
- **안 썼으면:** 동의항목을 추가하기 전까지 모든 회원의 로그인이 500으로 죽는다. 이건 가용성 문제라
  플랜의 위협 목록에도 `T-083-02 Denial of Service`로 잡혀 있었다.
- **평탄화한 이유:** `user.kakaoAccount?.profile?.nickname` 같은 체인을 서비스 계층까지 들고 가면
  카카오가 응답 구조를 바꿀 때 영향 범위가 서비스까지 번진다. `KakaoUserProfile`이라는 평탄한
  타입으로 한 번 접어서 외부 계약의 모양을 `auth.kakao` 패키지 안에서 끝냈다.

### 5. Flyway `ALTER TABLE` + 부팅 시 스키마 검증이 잡아 주는 것과 못 잡는 것

- **무엇:** 스키마 변경은 V5 마이그레이션으로만 하고, 부팅 시 Hibernate의 `validate` 모드가 엔티티
  매핑과 실제 스키마를 대조한다. 컬럼명 오타·타입 불일치·컬럼 누락은 기동 실패로 즉시 드러난다.
- **못 잡는 것:** **컬럼의 NULL 허용 여부와 Kotlin 타입의 nullable 불일치는 검증되지 않는다.**
  DB가 `NOT NULL`인데 Kotlin이 `String?`이어도 기동은 성공하고, 런타임에 null을 넣는 순간 제약 위반으로
  터진다. 그래서 conventions §3이 "둘을 손으로 맞춰라"를 명시하고 있고, 이번에도 양쪽 모두 nullable로
  맞췄다.
- **안 썼으면(마이그레이션 없이 엔티티만 추가했다면):** 검증이 "컬럼 없음"으로 기동을 막는다 —
  이게 정상 동작이다. 반대로 Hibernate가 스키마를 자동 생성·변경하도록 두면 운영 DB와 마이그레이션
  이력이 갈라지고, 어느 쪽이 진짜 스키마인지 알 수 없게 된다(그래서 이 프로젝트는 검증 모드로 고정).

### 6. 일부러 쓰지 않은 것

- **낙관적 락(`@Version`):** 프로필 갱신은 "카카오가 준 마지막 값이 이긴다"가 정의된 동작이라 동시
  갱신 충돌을 감지할 필요가 없다. 버전 컬럼을 넣으면 동시 로그인 시 `OptimisticLockException`으로
  로그인이 실패하는데, 그건 이 요구에서 손해다.
- **별도 서비스·별도 트랜잭션:** 프로필 저장을 위해 `updateKakaoProfile()` 같은 메서드를 따로 만들면
  로그인 한 번에 트랜잭션이 2개가 된다. find-or-create와 같은 트랜잭션 안에서 끝내는 편이
  "회원이 생성됐는데 프로필만 안 들어간" 중간 상태를 원천적으로 없앤다.
- **닉네임·URL 로깅:** 디버깅에 편하지만 개인정보다. 이 클라이언트의 기존 정책(실패 사실과 HTTP
  상태만 기록)을 그대로 지켰다.

## Self-Check: PASSED

- 생성 파일 4종 존재 확인 (V5 마이그레이션, `KakaoUserProfile.kt`, `MemberKakaoProfileTest.kt`, `openapi.yaml`)
- 커밋 5건 존재 확인 (`86751b2`, `602063d`, `33d8fd7`, `11bcc2a`, `7f363a9`)
- `./gradlew build` 그린, 워킹트리에 `.planning/` 외 미커밋 변경 없음
