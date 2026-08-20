# Quick Task 260806-und: D-083 카카오 프로필(닉네임·프로필 이미지) 수집·저장 및 MyProfileResponse 노출 - Context

**Gathered:** 2026-08-06
**Status:** Ready for planning

<domain>
## Task Boundary

`docs/decisions.md` D-083(현재 "[FE 요청 — BE 미확정]" 상태, working tree에 미커밋 상태로 추가되어 있음)을 BE에서 확정·구현한다.

범위:
1. 카카오 로그인 시 `/v2/user/me` 응답의 `kakao_account.profile`에서 닉네임·프로필 이미지 URL을 수집해 `member`에 저장 (둘 다 nullable — 동의 거부 가능)
2. `MyProfileResponse`에 `kakaoNickname`·`kakaoProfileImageUrl` 필드 추가 (nullable)
3. Flyway 마이그레이션으로 `member` 컬럼 2개 추가 (V5)
4. `./gradlew generateApiDocs`로 `docs/api/openapi.yaml` 재생성
5. `docs/decisions.md` D-083의 "[FE 요청 — BE 미확정]" 표기를 BE 확정으로 갱신
6. 테스트 필수 (기존 로그인·프로필 테스트 회귀 없어야 함)

범위 밖:
- 카카오 개발자 콘솔 동의항목(`profile_nickname`·`profile_image`) 추가 — **사용자가 직접 콘솔에서 처리한다.** 따라서 코드는 동의항목이 아직 없어 값이 안 와도 정상 동작해야 한다.
- 관리자 화면 응답(`MemberDetailResponse`·`MemberSummaryResponse`)에는 추가하지 않는다 (FE 요청 범위가 `/me`뿐)
- FE 작업 (별도 레포)

</domain>

<decisions>
## Implementation Decisions

### 갱신 시점 — 매 로그인마다 갱신 [사용자 확정]
- `KakaoAuthService.login()` 흐름에서 카카오 사용자 조회 결과의 닉네임·프로필 이미지를 매 로그인마다 `member`에 반영한다.
- 최초 가입 시점에만 채우는 방식은 기각 — 기존 회원이 영원히 null로 남아 FE에 표시할 값이 없어진다.
- 값이 실제로 바뀐 경우에만 UPDATE가 나가도록 JPA 변경 감지(dirty checking)에 맡긴다. 매 로그인마다 무조건 UPDATE를 강제하지 않는다.

### 동의 거부/철회 시 — null로 덮어쓴다 [사용자 확정]
- 카카오가 값을 주지 않으면(동의항목 미추가, 동의 거부, 사후 철회) 저장값을 `null`로 덮어쓴다. 카카오가 준 값을 그대로 반영하는 것이 원칙.
- 기존 값 유지 방식은 기각 — 사용자가 동의를 철회했는데 우리 DB가 계속 보관하는 상태가 된다.

### 트랜잭션 경계 — 기존 구조를 깨지 않는다
- `KakaoAuthService`는 트랜잭션을 열지 않는다(클래스 KDoc·conventions §7 "트랜잭션 안에서 외부 API 호출 금지"). 카카오 API 호출은 트랜잭션 밖, DB 쓰기는 `MemberRegistrationService`의 짧은 트랜잭션 안에서 처리하는 현재 구조를 유지한다.
- 즉 카카오 프로필 값은 `MemberRegistrationService`의 트랜잭션 메서드에 **인자로 전달**해서 find-or-create와 같은 트랜잭션 안에서 반영한다. 별도 서비스 호출을 추가해 트랜잭션을 2개로 쪼개지 않는다.
- `DataIntegrityViolationException` 재시도 경로(동시 최초 로그인 경쟁)도 그대로 유지되어야 한다 — 재시도 호출에도 같은 프로필 값이 전달되어야 한다.

### KakaoApiClient 반환 타입 확장
- 현재 `fetchKakaoId(accessToken): Long`은 id만 반환한다. 닉네임·이미지를 함께 넘기려면 반환 타입을 확장해야 한다.
- `KakaoUserResponse`에 `kakaoAccount.profile.nickname` / `profileImageUrl` 매핑을 추가하되, **모든 중간 노드가 nullable**이어야 한다 (`kakaoAccount`도, `profile`도 동의항목이 없으면 통째로 응답에 없다).
- 기존 `KakaoUserResponse` KDoc이 "닉네임·프로필 사진은 매핑하지 않는다"고 명시하고 있으므로, 그 KDoc을 D-083 확정에 맞게 갱신한다 (문서와 코드가 어긋난 채 남지 않게).

### 컬럼·DTO
- Flyway V5 마이그레이션으로 `member`에 `kakao_nickname`, `kakao_profile_image_url` 컬럼 추가. 둘 다 NULL 허용. 기존 커밋된 마이그레이션은 절대 수정하지 않는다.
- 엔티티 필드도 Kotlin nullable로 선언해 컬럼 nullable과 일치시킨다 (conventions §3).
- `MyProfileResponse`에 두 필드를 추가하고 `@field:Schema` 설명에 "동의하지 않았으면 null"을 명시한다.

### Claude's Discretion
- 컬럼 길이 (닉네임·URL 각각 적절한 VARCHAR 길이 선택)
- 프로필 이미지 URL을 카카오의 `profile_image_url`(640px)로 쓸지 `thumbnail_image_url`(110px)로 쓸지 — FE가 "프로필 사진"이라 했으므로 큰 쪽이 기본
- 테스트 배치·케이스 구성 (단, 아래 필수 케이스는 반드시 포함)

</decisions>

<specifics>
## Specific Ideas

필수 테스트 케이스:
- 카카오가 프로필을 주는 경우 → member에 저장되고 `/api/members/me` 응답에 나온다
- 카카오가 `kakao_account` 자체를 안 주는 경우(동의항목 미추가) → 예외 없이 로그인 성공, 두 필드 모두 null
- `kakao_account`는 있지만 `profile`이 없는 경우 → 마찬가지로 null
- 이미 값이 저장된 회원이 프로필 없이 재로그인 → 저장값이 null로 덮어써진다
- 이미 값이 저장된 회원이 바뀐 닉네임으로 재로그인 → 새 값으로 갱신된다
- 기존 로그인·프로필 테스트 전부 통과 (회귀 없음)

품질 게이트: `./gradlew ktlintFormat` → `./gradlew build` → (docker compose Postgres 기동 후) `./gradlew generateApiDocs`

</specifics>

<canonical_refs>
## Canonical References

- `docs/decisions.md` D-083 (이번 작업으로 확정 상태 전환), D-016/D-017/D-019/D-020/D-025/D-029/D-039/D-043/D-046/D-047
- `docs/conventions.md` §3(엔티티 nullable 일치), §7(트랜잭션 안 외부 API 호출 금지), §10.0(변경유형별 테스트 표)
- `docs/policies.md` §5.1(온보딩), `docs/glossary.md`(네이밍)
- `.claude/skills/add-migration/SKILL.md`, `.claude/skills/add-endpoint/SKILL.md`, `.claude/skills/add-domain-test/SKILL.md`
- 카카오 로그인 문서: `/v2/user/me` 응답의 `kakao_account.profile.nickname` / `.profile_image_url` / `.thumbnail_image_url`, 그리고 `profile_nickname_needs_agreement` 플래그

</canonical_refs>
