---
quick_id: 260806-und
phase: quick/260806-und
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - src/main/resources/db/migration/V5__add_member_kakao_profile.sql
  - src/main/kotlin/com/goldwrestling/member/Member.kt
  - src/main/kotlin/com/goldwrestling/member/MemberRegistrationService.kt
  - src/main/kotlin/com/goldwrestling/member/dto/MyProfileResponse.kt
  - src/main/kotlin/com/goldwrestling/auth/kakao/KakaoUserResponse.kt
  - src/main/kotlin/com/goldwrestling/auth/kakao/KakaoUserProfile.kt
  - src/main/kotlin/com/goldwrestling/auth/kakao/KakaoApiClient.kt
  - src/main/kotlin/com/goldwrestling/auth/KakaoAuthService.kt
  - src/test/kotlin/com/goldwrestling/member/MemberKakaoProfileTest.kt
  - src/test/kotlin/com/goldwrestling/auth/kakao/KakaoApiClientTest.kt
  - src/test/kotlin/com/goldwrestling/auth/KakaoAuthControllerTest.kt
  - src/test/kotlin/com/goldwrestling/auth/KakaoLoginConcurrencyTest.kt
  - src/test/kotlin/com/goldwrestling/member/MemberProfileTest.kt
  - src/test/kotlin/com/goldwrestling/member/MemberApprovalTest.kt
  - src/test/resources/kakao/user-response.json
  - docs/decisions.md
  - docs/glossary.md
  - docs/api/openapi.yaml
autonomous: true
requirements: [D-083]

must_haves:
  truths:
    - "카카오가 닉네임·프로필 이미지를 주면 member 행에 저장되고 GET /api/members/me 응답에 나온다"
    - "카카오가 kakao_account 또는 profile을 아예 주지 않아도 로그인이 예외 없이 성공한다"
    - "이미 값이 저장된 회원이 프로필 없이 재로그인하면 저장값이 null로 덮어써진다"
    - "이미 값이 저장된 회원이 바뀐 닉네임으로 재로그인하면 새 값으로 갱신된다"
    - "동시 최초 로그인 재시도 경로(DataIntegrityViolationException 1회 재시도)가 그대로 동작한다"
    - "기존 로그인·프로필·승인 테스트가 전부 통과한다(회귀 없음)"
  artifacts:
    - path: "src/main/resources/db/migration/V5__add_member_kakao_profile.sql"
      provides: "member.kakao_nickname, member.kakao_profile_image_url 컬럼(둘 다 NULL 허용)"
      contains: "ALTER TABLE member"
    - path: "src/main/kotlin/com/goldwrestling/auth/kakao/KakaoUserProfile.kt"
      provides: "kakaoId·nickname·profileImageUrl를 평탄화해 담는 클라이언트 반환 타입"
    - path: "src/test/kotlin/com/goldwrestling/member/MemberKakaoProfileTest.kt"
      provides: "null 덮어쓰기·값 갱신 규칙 단위테스트"
    - path: "docs/api/openapi.yaml"
      provides: "MyProfileResponse에 kakaoNickname·kakaoProfileImageUrl이 포함된 FE 계약"
      contains: "kakaoProfileImageUrl"
  key_links:
    - from: "src/main/kotlin/com/goldwrestling/auth/KakaoAuthService.kt"
      to: "MemberRegistrationService.findOrCreateByKakaoId"
      via: "카카오 프로필 값을 인자로 전달(트랜잭션 밖 → 트랜잭션 안)"
      pattern: "findOrCreateByKakaoId\\("
    - from: "src/main/kotlin/com/goldwrestling/member/MemberRegistrationService.kt"
      to: "Member.applyKakaoProfile"
      via: "같은 트랜잭션 안에서 dirty checking으로 반영"
      pattern: "applyKakaoProfile"
    - from: "src/main/kotlin/com/goldwrestling/member/dto/MyProfileResponse.kt"
      to: "Member.kakaoNickname / Member.kakaoProfileImageUrl"
      via: "from(member) 팩토리"
      pattern: "kakaoProfileImageUrl"
---

<objective>
`docs/decisions.md` D-083을 BE에서 확정·구현한다. 카카오 로그인 시 `/v2/user/me` 응답의
`kakao_account.profile`에서 닉네임·프로필 이미지 URL을 수집해 `member`에 저장하고,
`GET /api/members/me`(`MyProfileResponse`)에 두 필드를 nullable로 노출한다.

Purpose: FE가 "내 정보" 화면에 카카오 닉네임·프로필 사진을 표시할 수 있게 계약을 연다.
체육관 운영 기준 신원은 여전히 온보딩 실명·전화번호이고, 카카오 프로필은 **표시용 보조 정보**다.

Output: Flyway V5 마이그레이션, `Member` 필드 2개 + 반영 메서드, 카카오 응답 매핑 확장,
로그인 흐름 배선, `MyProfileResponse` 필드 2개, 단위·통합 테스트, 재생성된 `docs/api/openapi.yaml`,
확정 상태로 갱신된 `docs/decisions.md` D-083 + `docs/glossary.md` 용어 2행.
</objective>

<context>
@CLAUDE.md
@.planning/quick/260806-und-d-083-myprofileresponse/260806-und-CONTEXT.md
@docs/conventions.md
@docs/glossary.md
@docs/decisions.md
@.claude/skills/add-migration/SKILL.md
@.claude/skills/add-endpoint/SKILL.md
@.claude/skills/add-domain-test/SKILL.md
@src/main/kotlin/com/goldwrestling/auth/kakao/KakaoUserResponse.kt
@src/main/kotlin/com/goldwrestling/auth/kakao/KakaoApiClient.kt
@src/main/kotlin/com/goldwrestling/auth/KakaoAuthService.kt
@src/main/kotlin/com/goldwrestling/member/MemberRegistrationService.kt
@src/main/kotlin/com/goldwrestling/member/Member.kt
@src/main/kotlin/com/goldwrestling/member/dto/MyProfileResponse.kt

<interfaces>
<!-- 실행자가 바로 쓸 현재 계약. 코드베이스를 다시 탐색하지 말 것. -->

현재 (변경 전):
- `KakaoApiClient.fetchKakaoId(kakaoAccessToken: String): Long`
- `KakaoUserResponse(id: Long, connectedAt: String? = null)` — `@JsonNaming(SnakeCaseStrategy)`, Jackson 3(`tools.jackson`)
- `MemberRegistrationService.findOrCreateByKakaoId(kakaoId: Long): MemberLoginSummaryResponse` — `@Transactional`
- `MemberRegistrationService.createPendingMember(kakaoId: Long): Member` — private
- `Member(branch, name, phoneNumber, status, kakaoId, rejectionReason, createdAt)` — `class`(data class 아님), `id: Long? = null`
- `MyProfileResponse(memberId, name, phoneNumber, status, onboardingCompleted, rejected)` + `from(member)`

이 작업 후 (목표 계약):
- `KakaoApiClient.fetchUserProfile(kakaoAccessToken: String): KakaoUserProfile`
- `data class KakaoUserProfile(val kakaoId: Long, val nickname: String?, val profileImageUrl: String?)`
- `MemberRegistrationService.findOrCreateByKakaoId(kakaoId: Long, kakaoNickname: String?, kakaoProfileImageUrl: String?): MemberLoginSummaryResponse`
- `Member.applyKakaoProfile(nickname: String?, profileImageUrl: String?)`
- `MyProfileResponse(..., kakaoNickname: String?, kakaoProfileImageUrl: String?)`

기존 테스트 중 `kakaoApiClient.fetchKakaoId`를 목킹하는 지점(전부 갱신 대상):
- `src/test/kotlin/com/goldwrestling/auth/KakaoAuthControllerTest.kt` (4곳)
- `src/test/kotlin/com/goldwrestling/auth/KakaoLoginConcurrencyTest.kt` (1곳)
- `src/test/kotlin/com/goldwrestling/member/MemberProfileTest.kt` (1곳)
- `src/test/kotlin/com/goldwrestling/member/MemberApprovalTest.kt` (1곳)
- `src/test/kotlin/com/goldwrestling/auth/kakao/KakaoApiClientTest.kt` (3곳, 실호출)

통합테스트 애노테이션 조합(컨텍스트 캐시 공유 — 절대 바꾸지 말 것):
`@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)`
</interfaces>
</context>

<constraints>
- CONTEXT.md 결정은 LOCKED. 대안을 다시 제시하지 않는다.
- **푸시 금지.** 커밋은 하되 `git push`는 어떤 경우에도 실행하지 않는다.
- 커밋된 마이그레이션(V1~V4)은 수정하지 않는다. 새 파일 V5만 추가한다.
- 관리자 응답(`MemberDetailResponse`·`MemberSummaryResponse`)·`MemberLoginSummaryResponse`에는 필드를 추가하지 않는다(FE 요청 범위는 `/me`뿐).
- 카카오 개발자 콘솔 동의항목 추가는 범위 밖 — 값이 안 와도 코드가 정상 동작해야 한다.
- 닉네임·프로필 이미지 URL을 로그에 남기지 않는다(개인정보 성격, T-02-19 연장선).
- 낯선 Boot 4 API·설정 키가 필요하면 추측하지 말고 `verify-boot4-api` 스킬 절차를 따른다.
</constraints>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: V5 마이그레이션 + Member 엔티티 필드·반영 메서드 (+단위테스트)</name>
  <files>
    src/main/resources/db/migration/V5__add_member_kakao_profile.sql,
    src/main/kotlin/com/goldwrestling/member/Member.kt,
    src/test/kotlin/com/goldwrestling/member/MemberKakaoProfileTest.kt
  </files>
  <behavior>
    `MemberKakaoProfileTest` (순수 Kotlin 단위테스트, 스프링 컨텍스트 없음, 메서드명은 백틱 한국어 — conventions §10.1·§10.2):
    - 카카오 프로필이 없던 회원에게 닉네임·이미지가 오면 두 값이 설정된다
    - 이미 값이 있는 회원에게 null이 오면 두 값이 null로 덮어써진다 (동의 철회 반영)
    - 이미 값이 있는 회원에게 다른 닉네임이 오면 새 값으로 갱신된다
    - 닉네임이 빈 문자열·공백만이면 null로 정규화된다
    - 프로필 이미지 URL이 빈 문자열이면 null로 정규화된다
  </behavior>
  <action>
    D-083 확정 사항 중 저장 계층을 만든다.

    (1) `V5__add_member_kakao_profile.sql` 신규 작성. `ALTER TABLE member`로 `kakao_nickname VARCHAR(100)`,
    `kakao_profile_image_url VARCHAR(500)` 두 컬럼을 추가한다. 둘 다 NULL 허용(NOT NULL 금지) —
    동의항목 미추가·동의 거부·사후 철회 시 값이 없는 것이 정상 상태다. 파일 상단에 주석으로 D-083 근거와
    "표시용 보조 정보이며 운영 기준 신원은 온보딩 실명·전화번호"임을 남긴다. 길이 선택 근거(닉네임은 카카오
    정책상 20자 내외라 100은 충분한 여유, 이미지 URL은 `k.kakaocdn.net` 기준 100자 내외라 500은 여유)도
    주석 한 줄로 남긴다. 인덱스·제약은 추가하지 않는다(조회 조건으로 쓰이지 않는다).

    (2) `Member.kt`에 생성자 파라미터 2개를 추가한다: `@Column(name = "kakao_nickname", length = 100) var kakaoNickname: String? = null`,
    `@Column(name = "kakao_profile_image_url", length = 500) var kakaoProfileImageUrl: String? = null`.
    기본값 `null`을 주어 기존 생성 호출부(테스트 다수)가 깨지지 않게 한다. `var`인 이유는 매 로그인마다 갱신되기 때문이다.
    컬럼 nullable과 Kotlin nullable을 반드시 일치시킨다(conventions §3).

    (3) `Member`에 `fun applyKakaoProfile(nickname: String?, profileImageUrl: String?)`를 추가한다.
    카카오가 준 값을 그대로 반영하되 빈 문자열·공백만 문자열은 `null`로 정규화한다(`takeIf { it.isNotBlank() }`).
    별도의 "값이 바뀌었는지" 비교 분기를 넣지 않는다 — JPA 변경 감지는 스냅샷과 같은 값을 대입하면 UPDATE를
    내지 않으므로 단순 대입만으로 CONTEXT의 "실제로 바뀐 경우에만 UPDATE" 요구가 충족된다. KDoc에 이 이유와
    "동의 철회 시 null로 덮어쓰는 것이 의도된 동작(D-083)"임을 명시한다.

    (4) `MemberKakaoProfileTest`를 `<behavior>`의 5개 케이스로 작성한다. `Member` 인스턴스를 직접 생성해
    검증한다(스프링·DB 불필요). `Branch`가 필요하면 최소 인스턴스를 만들어 쓴다.
  </action>
  <verify>
    <automated>./gradlew ktlintFormat &amp;&amp; ./gradlew test --tests "com.goldwrestling.member.MemberKakaoProfileTest" --tests "com.goldwrestling.db.FlywayMigrationIntegrationTest"</automated>
    <automated>grep -v '^--' src/main/resources/db/migration/V5__add_member_kakao_profile.sql | grep -c "NOT NULL"</automated>
    <!-- 위 grep은 0이어야 한다: 두 컬럼 모두 NULL 허용 -->
  </verify>
  <done>
    V5 파일이 존재하고 Testcontainers가 V1~V5를 성공 재생하며 `ddl-auto=validate`가 Member 매핑을 통과한다.
    `MemberKakaoProfileTest` 5개 케이스가 통과한다. 기존 `Member(...)` 호출부는 하나도 수정하지 않았다.
    커밋: `feat(member): 카카오 프로필 컬럼 추가와 반영 메서드 (D-083)` — 마이그레이션+엔티티+테스트를 한 커밋에.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: 카카오 /v2/user/me 프로필 매핑 + 클라이언트 반환 타입 확장 (+단위테스트, 호출부 컴파일 유지)</name>
  <files>
    src/main/kotlin/com/goldwrestling/auth/kakao/KakaoUserResponse.kt,
    src/main/kotlin/com/goldwrestling/auth/kakao/KakaoUserProfile.kt,
    src/main/kotlin/com/goldwrestling/auth/kakao/KakaoApiClient.kt,
    src/main/kotlin/com/goldwrestling/auth/KakaoAuthService.kt,
    src/test/kotlin/com/goldwrestling/auth/kakao/KakaoApiClientTest.kt,
    src/test/resources/kakao/user-response.json,
    src/test/kotlin/com/goldwrestling/auth/KakaoAuthControllerTest.kt,
    src/test/kotlin/com/goldwrestling/auth/KakaoLoginConcurrencyTest.kt,
    src/test/kotlin/com/goldwrestling/member/MemberProfileTest.kt,
    src/test/kotlin/com/goldwrestling/member/MemberApprovalTest.kt
  </files>
  <behavior>
    `KakaoApiClientTest`에 추가할 케이스 (기존 `MockRestServiceServer` 방식 유지):
    - `kakao_account.profile`이 있으면 닉네임과 프로필 이미지 URL을 읽는다
    - `kakao_account` 자체가 응답에 없으면(동의항목 미추가) 예외 없이 닉네임·이미지가 null이다
    - `kakao_account`는 있지만 `profile`이 없으면 닉네임·이미지가 null이다
    - `profile`은 있지만 `nickname` 키가 없으면(부분 동의) 닉네임만 null이고 이미지는 읽힌다
    - 닉네임이 빈 문자열이면 null로 정규화된다
    - 기존 케이스(숫자 id 읽기, Bearer 헤더, 4xx/5xx/연결실패 번역)는 그대로 통과한다
  </behavior>
  <action>
    카카오 응답에서 프로필을 꺼내는 계층을 만든다. **이 task가 끝난 시점에 `./gradlew build`가 그린이어야 한다** —
    그래서 시그니처가 바뀌는 호출부·기존 목킹까지 이 task 안에서 함께 고친다.

    (1) `KakaoUserResponse.kt`에 중첩 타입을 추가한다: `kakaoAccount: KakaoAccount? = null`,
    중첩 `data class KakaoAccount(val profile: Profile? = null)`,
    중첩 `data class Profile(val nickname: String? = null, val profileImageUrl: String? = null, val thumbnailImageUrl: String? = null)`.
    **모든 중간 노드가 nullable**이어야 한다 — 동의항목이 없으면 `kakao_account`가 통째로 응답에서 빠진다.
    `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)`는 상속되지 않으므로 **중첩 클래스에도 각각 붙인다**
    (Jackson 3 = `tools.jackson.*` 패키지. 기존 임포트를 그대로 따른다).
    기존 KDoc의 "닉네임·프로필 사진 등은 매핑하지 않는다" 문장을 D-083 확정 내용으로 **교체**한다:
    실명·전화번호는 여전히 온보딩 입력이 정본이고(D-025) 카카오에서 가져오지 않는다는 원칙은 유지하되,
    닉네임·프로필 이미지는 표시용 보조 정보로 D-083에 따라 매핑한다고 적는다. 문서와 코드가 어긋난 채 남기지 않는다.

    (2) `KakaoUserProfile.kt` 신규: `data class KakaoUserProfile(val kakaoId: Long, val nickname: String?, val profileImageUrl: String?)`.
    중첩 nullable 체인을 서비스 계층까지 끌고 가지 않기 위해 클라이언트가 평탄화해 반환하는 타입이다(KDoc에 이 이유를 적는다).

    (3) `KakaoApiClient.fetchKakaoId`를 `fetchUserProfile(kakaoAccessToken: String): KakaoUserProfile`로 교체한다.
    `user.kakaoAccount?.profile?.nickname`, `?.profileImageUrl`을 읽고 빈 문자열·공백은 `null`로 정규화한다.
    이미지 URL은 `profileImageUrl`(640px)을 쓴다 — FE 요청이 "프로필 사진"이므로 큰 쪽을 기본으로 한다.
    `thumbnailImageUrl`은 매핑만 해 두고 사용하지 않는다(향후 필요 시 전환 가능). KDoc에 이 선택 이유를 적는다.
    **닉네임·URL 값을 로그에 남기지 않는다** — 기존 로깅 정책(상태 코드만)을 그대로 유지한다.

    (4) `KakaoAuthService.login`에서 `val kakaoUser = kakaoApiClient.fetchUserProfile(kakaoToken.accessToken)`로 바꾸고,
    이 task에서는 `memberRegistrationService.findOrCreateByKakaoId(kakaoUser.kakaoId)`로 **kakaoId만 넘겨 컴파일을 유지**한다.
    프로필 값 전달은 Task 3에서 배선한다. 기존 재시도 구조·KDoc은 건드리지 않는다.

    (5) `src/test/resources/kakao/user-response.json` 픽스처에 `kakao_account.profile`(nickname·profile_image_url·thumbnail_image_url)을
    추가한다. `KakaoApiMockSupport.expectUserInfo`의 `"1234567890"` 치환 로직이 깨지지 않도록 id 값은 그대로 둔다.

    (6) `KakaoApiClientTest`: 기존 `fetchKakaoId` 호출 3곳을 `fetchUserProfile`로 바꾸고(`.kakaoId`로 단언),
    `<behavior>`의 신규 케이스를 추가한다. 각 케이스는 인라인 JSON 응답 문자열로 구성한다(기존 `USER_RESPONSE_BODY` 패턴).

    (7) 기존 통합테스트 4개 파일의 목킹을 기계적으로 교체한다:
    `given(kakaoApiClient.fetchKakaoId(anyString())).willReturn(x)` →
    `given(kakaoApiClient.fetchUserProfile(anyString())).willReturn(KakaoUserProfile(x, null, null))`.
    (`KakaoAuthControllerTest` 4곳, `KakaoLoginConcurrencyTest` 1곳, `MemberProfileTest` 1곳, `MemberApprovalTest` 1곳)
    이 task에서는 값이 null인 스텁만 쓴다 — 프로필이 실제로 저장되는지 검증은 Task 3에서 추가한다.
    동시성 테스트의 스레드·정리 구조는 손대지 않는다.
  </action>
  <verify>
    <automated>./gradlew ktlintFormat &amp;&amp; ./gradlew build</automated>
    <automated>grep -rn "fetchKakaoId" src/ | grep -v '^\s*$' | wc -l</automated>
    <!-- 위 grep 결과는 0이어야 한다: 구 메서드가 남아 있지 않다 -->
  </verify>
  <done>
    `./gradlew build`가 그린이다(기존 로그인·프로필·승인·동시성 테스트 전부 통과 = 회귀 없음).
    `KakaoApiClientTest`가 `kakao_account` 없음 / `profile` 없음 / 부분 동의 / 빈 문자열 케이스에서 예외 없이 null을 돌려준다.
    `fetchKakaoId`는 코드베이스에 남아 있지 않다.
    커밋: `feat(auth): 카카오 /v2/user/me 프로필 매핑과 클라이언트 반환 타입 확장 (D-083)`
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: 로그인 흐름 배선(저장·갱신) + MyProfileResponse 노출 (+통합테스트)</name>
  <files>
    src/main/kotlin/com/goldwrestling/auth/KakaoAuthService.kt,
    src/main/kotlin/com/goldwrestling/member/MemberRegistrationService.kt,
    src/main/kotlin/com/goldwrestling/member/dto/MyProfileResponse.kt,
    src/test/kotlin/com/goldwrestling/auth/KakaoAuthControllerTest.kt,
    src/test/kotlin/com/goldwrestling/member/MemberProfileTest.kt
  </files>
  <behavior>
    `KakaoAuthControllerTest` 신규 케이스(기존 애노테이션 조합·헬퍼 재사용):
    - 카카오가 프로필을 주면 최초 로그인으로 생성된 member 행에 닉네임·이미지 URL이 저장된다
    - 카카오가 프로필을 주지 않으면(스텁이 null) 로그인은 200이고 두 컬럼은 null이다
    - 이미 닉네임·이미지가 저장된 회원이 프로필 없이 재로그인하면 저장값이 null로 덮어써진다
    - 이미 저장된 회원이 바뀐 닉네임으로 재로그인하면 새 값으로 갱신된다
    (DB 실제 값 확인은 `memberRepository.flush()` 후 `jdbcClient`로 조회한다 — `@Transactional` 테스트는
    커밋되지 않아 명시적 flush 없이는 UPDATE가 나가지 않는다. `MemberProfileTest`의 기존 주석 참고)

    `MemberProfileTest` 신규 케이스:
    - 카카오 프로필이 저장된 회원이 `GET /api/members/me`를 호출하면 `kakaoNickname`·`kakaoProfileImageUrl`이 응답에 나온다
    - 카카오 프로필이 없는 회원은 두 필드가 응답에 없다(null) — `jsonPath(...).doesNotExist()`
  </behavior>
  <action>
    수집한 프로필을 저장하고 `/me`로 노출하는 배선을 완성한다.

    (1) `MemberRegistrationService.findOrCreateByKakaoId` 시그니처를
    `(kakaoId: Long, kakaoNickname: String?, kakaoProfileImageUrl: String?)`로 확장한다.
    **`KakaoUserProfile`을 인자 타입으로 쓰지 않는다** — `member` 패키지가 `auth.kakao` 패키지에 의존하게 되는 것을 피한다(conventions §1).
    기존 회원을 찾은 경로에서는 반환 전에 `member.applyKakaoProfile(kakaoNickname, kakaoProfileImageUrl)`을 호출한다.
    이 메서드는 이미 `@Transactional`이므로 dirty checking으로 반영된다 — 별도 `save()`/`flush()`를 호출하지 않는다.
    `createPendingMember`에도 두 값을 전달해 최초 생성 시점에 함께 채운다(정규화는 `applyKakaoProfile`과 동일하게
    적용되도록 생성 후 `applyKakaoProfile`을 호출하거나 같은 정규화를 거치게 한다 — 빈 문자열이 DB에 들어가면 안 된다).
    KDoc에 "카카오 프로필은 매 로그인마다 카카오가 준 값 그대로 반영하며, 값이 없으면 null로 덮어쓴다(D-083)"를 추가한다.

    (2) `KakaoAuthService.login`에서 `findOrCreateByKakaoId`를 **named arguments**로 호출한다
    (`kakaoNickname`/`kakaoProfileImageUrl` 둘 다 `String?`이라 위치 인자는 조용히 뒤바뀔 수 있다).
    재시도 경로(`catch (e: DataIntegrityViolationException)`)에도 **같은 프로필 값**이 전달되어야 한다 —
    두 호출이 같은 인자를 쓰도록 private 헬퍼로 묶는다. 헬퍼는 `memberRegistrationService`(다른 빈)를 호출하므로
    self-invocation 문제가 없다. 이 클래스에는 여전히 트랜잭션 애노테이션을 붙이지 않는다(클래스 KDoc·conventions §7).

    (3) `MyProfileResponse`에 `kakaoNickname: String?`, `kakaoProfileImageUrl: String?`를 추가하고 `from(member)`에서 채운다.
    `@field:Schema(description = ...)`에 "카카오 동의항목에 동의하지 않았거나 철회했으면 null" 을 명시한다(FE가 이 문구를 보고 분기한다).
    `MemberLoginSummaryResponse`·`MemberDetailResponse`·`MemberSummaryResponse`에는 **추가하지 않는다**.

    (4) `<behavior>`의 통합테스트 케이스를 추가한다. 프로필 값이 있는 스텁이 필요하면 기존 `stubKakaoLogin` 헬퍼에
    nullable 기본값 파라미터(`nickname: String? = null, profileImageUrl: String? = null`)를 더해 확장한다 —
    기존 호출부가 그대로 컴파일되게 한다.
  </action>
  <verify>
    <automated>./gradlew ktlintFormat &amp;&amp; ./gradlew build</automated>
    <automated>grep -c "kakaoProfileImageUrl" src/main/kotlin/com/goldwrestling/member/dto/MyProfileResponse.kt</automated>
  </verify>
  <done>
    최초 로그인·재로그인 모두에서 프로필이 DB에 반영되고, 프로필이 없으면 null로 덮어써진다.
    `GET /api/members/me` 응답에 두 필드가 나온다. `./gradlew build` 그린 = 기존 테스트 회귀 없음.
    커밋: `feat(member): 내 프로필 응답에 카카오 닉네임·프로필 이미지 노출 (D-083)`
  </done>
</task>

<task type="auto">
  <name>Task 4: openapi.yaml 재생성 + docs(D-083 확정·glossary) 갱신</name>
  <files>
    docs/api/openapi.yaml,
    docs/decisions.md,
    docs/glossary.md
  </files>
  <action>
    FE 계약과 문서를 코드와 일치시킨다.

    (1) `docs/glossary.md`의 "인증·회원 (Phase 2)" 표에 2행을 추가한다(CLAUDE.md 규칙 3 — 새 개념은 glossary 먼저):
    `카카오 닉네임 | kakaoNickname | 카카오 프로필 닉네임 (DB kakao_nickname). 표시용 보조 정보 — 운영 기준 신원은 온보딩 실명이다. 매 로그인마다 카카오 값으로 갱신되며 동의가 없으면 null (D-083)`
    `카카오 프로필 이미지 | kakaoProfileImageUrl | 카카오 프로필 사진 URL (DB kakao_profile_image_url). 640px profile_image_url을 저장한다. 동의가 없으면 null (D-083)`
    표의 기존 열 정렬 스타일을 그대로 따른다.

    (2) `docs/decisions.md` D-083(현재 634행 부근)을 BE 확정 상태로 갱신한다:
    - 제목의 `[FE 요청 — BE 미확정]` 표기를 제거해 `## D-083. 카카오 프로필(닉네임·사진)을 회원 프로필 응답에 추가` 형태로 만든다.
    - 본문의 `**[FE 요청, 2026-08-06 — BE 검토·확정 대기]**` 문구를 확정 표기로 교체하고, 확정된 내용을 요약해 덧붙인다:
      매 로그인마다 갱신 / 값이 없으면 null로 덮어쓴다(동의 철회 반영) / `member.kakao_nickname`·`kakao_profile_image_url`(둘 다 nullable, V5) /
      `MyProfileResponse`에만 노출하고 관리자 응답·로그인 요약에는 넣지 않는다 / 이미지 URL은 640px `profile_image_url` /
      카카오 개발자 콘솔 동의항목(`profile_nickname`·`profile_image`) 추가는 **운영자가 콘솔에서 처리**하며,
      동의항목이 없어도 코드는 정상 동작한다(값이 null).
    - 기존 다른 결정 항목의 서술 형식(3~4줄, `- 이유:` / `- 참고:`)을 그대로 따른다.

    (3) openapi.yaml을 **재생성**한다(손으로 편집 금지, add-endpoint §6):
    `docker compose up -d` → `./gradlew generateApiDocs`.
    `git diff docs/api/openapi.yaml`로 **MyProfileResponse에 두 필드가 추가된 것 외의 변경이 없는지**,
    그리고 `servers:`가 `/`로 유지되는지 확인한다.
    docker를 쓸 수 없어 재생성이 불가능하면 **openapi.yaml을 임의로 손편집하지 말고 여기서 멈춰 사용자에게 알린다**
    (FE가 이 파일로 타입을 생성하므로 손편집본과 실제 응답이 어긋나면 FE 빌드가 조용히 잘못된 타입을 만든다).

    (4) 마지막 품질 게이트: `./gradlew ktlintFormat` → `./gradlew build`.
    커밋: `docs(auth): D-083 카카오 프로필 수집·노출 BE 확정 + openapi 재생성`.
    **`git push`는 실행하지 않는다.** 커밋까지만 하고 사용자에게 푸시 여부를 맡긴다.
  </action>
  <verify>
    <automated>grep -c "BE 미확정" docs/decisions.md</automated>
    <!-- 0이어야 한다 -->
    <automated>grep -c "kakaoProfileImageUrl" docs/api/openapi.yaml</automated>
    <!-- 1 이상이어야 한다 -->
    <automated>grep -c "kakaoNickname" docs/glossary.md</automated>
    <automated>git log --oneline -4 &amp;&amp; git status --porcelain</automated>
  </verify>
  <done>
    `docs/decisions.md` D-083이 확정 상태이고 미확정 표기가 남아 있지 않다.
    `docs/glossary.md`에 용어 2행이 있다. `docs/api/openapi.yaml`이 재생성되어 두 필드를 포함하며
    diff에 의도치 않은 변경이 없다. `./gradlew build` 그린. 커밋 완료, **푸시 안 함**, 워킹트리 클린.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| 카카오 `/v2/user/me` → BE | 외부 시스템이 통제하는 문자열(닉네임·URL)이 우리 DB로 들어온다 |
| BE → FE(`/api/members/me`) | 저장한 외부 문자열을 다시 클라이언트로 내보낸다 |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-083-01 | Information Disclosure | `KakaoApiClient` 로깅 | mitigate | 닉네임·프로필 URL을 로그에 남기지 않는다(기존 정책: 상태 코드만). Task 2 action 명시 |
| T-083-02 | Denial of Service | 로그인 경로 | mitigate | 응답의 모든 중간 노드를 nullable로 매핑해 필드 누락 시 역직렬화 실패로 로그인이 막히지 않게 한다. Task 2 behavior 4개 케이스로 검증 |
| T-083-03 | Tampering | `member.kakao_profile_image_url` | accept | URL은 카카오가 준 값을 그대로 저장·전달한다. 렌더링 측 XSS 방지는 FE의 책임 범위이고, BE는 값을 실행 컨텍스트에 넣지 않는다(JSON 문자열로만 전달) |
| T-083-04 | Information Disclosure | `MyProfileResponse` | mitigate | 본인 토큰 주체의 프로필만 반환하는 기존 `/me` 인가 경로를 그대로 사용한다. 관리자·타인 조회 응답에는 추가하지 않는다 |
| T-083-SC | Tampering | 패키지 설치 | n/a | 이번 작업은 새 의존성을 추가하지 않는다 |
</threat_model>

<verification>
- `./gradlew build` 그린 (Testcontainers가 V1~V5 재생 + `ddl-auto=validate` 매핑 검증 포함)
- CONTEXT `<specifics>` 필수 케이스 6종이 전부 테스트로 존재한다:
  프로필 저장·노출 / `kakao_account` 없음 / `profile` 없음 / null 덮어쓰기 / 닉네임 갱신 / 기존 테스트 회귀 없음
- `grep -rn "fetchKakaoId" src/` 결과 0건
- `grep -c "BE 미확정" docs/decisions.md` 결과 0
- `docs/api/openapi.yaml` diff가 `MyProfileResponse` 두 필드 추가에 한정된다
- 커밋 4건 존재, `git push` 미실행
</verification>

<success_criteria>
1. 카카오가 프로필을 주면 `member`에 저장되고 `GET /api/members/me`가 `kakaoNickname`·`kakaoProfileImageUrl`을 반환한다
2. 카카오가 `kakao_account`·`profile`을 주지 않아도 로그인이 200으로 성공하고 두 값이 null이다
3. 재로그인 시 카카오 값 그대로 갱신되며, 값이 없어지면 null로 덮어써진다
4. 동시 최초 로그인 재시도 경로가 같은 프로필 값으로 동작하고 `KakaoLoginConcurrencyTest`가 통과한다
5. `docs/api/openapi.yaml`·`docs/decisions.md`·`docs/glossary.md`가 코드와 일치한다
6. `./gradlew build` 그린, 커밋 완료, **푸시하지 않음**
</success_criteria>

<output>
완료 보고에 CLAUDE.md의 `이번에 쓴 기술` 섹션을 포함한다. 이번 작업에서 다룰 만한 소재:
JPA 변경 감지(dirty checking)와 "같은 값 대입 시 UPDATE가 나가지 않는" 동작, 트랜잭션 경계 밖의 외부 API 호출과
인자 전달로 경계를 넘기는 이유, Jackson 중첩 nullable 매핑과 외부 계약의 부분 응답 방어,
Flyway `ALTER TABLE` + `ddl-auto=validate`가 잡아 주는 것과 못 잡는 것(Kotlin nullability 불일치).

보고 마지막에 **카카오 개발자 콘솔에서 `profile_nickname`·`profile_image` 동의항목을 추가해야 값이 실제로 채워진다**는
점과, 그 전까지는 두 필드가 정상적으로 null이라는 점을 한 줄로 상기시킨다.
</output>
