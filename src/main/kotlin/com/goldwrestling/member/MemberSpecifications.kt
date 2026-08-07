package com.goldwrestling.member

import com.goldwrestling.common.LikePatternEscaper
import org.springframework.data.jpa.domain.Specification

/**
 * 관리자 회원 검색(`GET /api/admin/members`, D-035)의 동적 조건. 각 함수는 조건이 없으면 `null`을
 * 반환하고, 호출부가 `Specification.allOf(listOfNotNull(...))`(Spring Data JPA 4.x 정적 팩토리 —
 * `unrestricted()`를 항등원 삼아 `and`로 리듀스한다. 구버전의 `Specification.where(...)`은 이 라인에서
 * deprecated 되지 않았지만 `allOf`가 여러 조건을 한 번에 묶는 더 직접적인 표현이라 이걸 쓴다.
 * verify-boot4-api 절차로 spring-data-jpa 4.1.0 소스를 확인했다)로 합친다.
 */
object MemberSpecifications {
    /**
     * 이름·전화번호 부분 일치 검색(D-035 "검색어 하나로 이름·전화번호 부분 일치").
     *
     * - blank 검색어는 조건 없음(`null`)으로 취급한다.
     * - 이름 조건은 대소문자를 구분하지 않는다(영문 이름 대비) — `lower(name) like lower(keyword)`.
     * - 전화번호 조건은 검색어에서 하이픈을 제거한 값([PhoneNumberNormalizer.normalize])으로 매칭한다.
     *   **저장 형식이 하이픈 없는 숫자이므로(D-041), 검색어도 같은 형식으로 정규화해야 "010-1234"
     *   같은 입력이 매칭된다.**
     * - `name`·`phoneNumber`가 `null`인 회원은 SQL `LIKE`에서 자동으로 매칭되지 않는다(`NULL`과의
     *   비교는 항상 UNKNOWN으로 평가돼 `WHERE`에서 걸러진다) — 그래서 별도 null 방어 코드를 두지
     *   않는다. 다음에 이 함수를 보는 사람이 "null이면 터지는 거 아닌가" 하고 방어 코드를 추가하지
     *   않도록 이 사실을 여기 남겨 둔다.
     * - 검색어에 `%`·`_`(SQL LIKE 와일드카드)가 포함되면 [escapeLikeWildcards]로 이스케이프한다 —
     *   그렇지 않으면 검색어 하나로 의도치 않게 전체 회원이 매칭되는 논리적 우회가 된다(T-02-34).
     * - **하이픈만으로 된 검색어(`"-"`, `"--"` 등)는 blank가 아니라 이 함수를 통과하지만,
     *   [PhoneNumberNormalizer.normalize]를 거치면 빈 문자열이 된다. 빈 문자열로 전화번호 술어를
     *   만들면 `LIKE '%%'`가 되어 전화번호가 `null`이 아닌 전 회원이 매칭된다 — 이스케이프만으로는
     *   막히지 않는 두 번째 우회 경로다(T-02-14-02). 그래서 정규화 결과가 빈 문자열이면 전화번호
     *   술어 자체를 만들지 않는다.**
     */
    fun keywordContains(keyword: String?): Specification<Member>? {
        if (keyword.isNullOrBlank()) return null
        val trimmed = keyword.trim()
        val escapedNameKeyword = LikePatternEscaper.escapeWildcards(trimmed).lowercase()
        val normalizedPhone = PhoneNumberNormalizer.normalize(trimmed)

        return Specification { root, _, criteriaBuilder ->
            val namePredicate =
                criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")),
                    "%$escapedNameKeyword%",
                    LikePatternEscaper.ESCAPE_CHAR,
                )
            val predicates = mutableListOf(namePredicate)
            if (normalizedPhone.isNotEmpty()) {
                val escapedPhoneKeyword = LikePatternEscaper.escapeWildcards(normalizedPhone)
                predicates.add(
                    criteriaBuilder.like(
                        root.get("phoneNumber"),
                        "%$escapedPhoneKeyword%",
                        LikePatternEscaper.ESCAPE_CHAR,
                    ),
                )
            }
            criteriaBuilder.or(*predicates.toTypedArray())
        }
    }

    /** 상태 필터. `null`이면 조건 없음. */
    fun hasStatus(status: MemberStatus?): Specification<Member>? {
        if (status == null) return null
        return Specification { root, _, criteriaBuilder ->
            criteriaBuilder.equal(root.get<MemberStatus>("status"), status)
        }
    }

    /**
     * 온보딩 완료 여부 필터. `null`이면 조건 없음.
     *
     * **[Member.isOnboardingCompleted]와 같은 범위에서 같은 판정 규칙을 유지하려 한다.** 이름·전화번호가
     * 모두 `null`이 아니고 SQL `TRIM` 기준으로 빈 문자열이 아닐 때 완료로 본다. 이 규칙이 엔티티
     * 메서드와 이 함수 두 곳에 존재하므로, 한쪽만 고치면 **관리자 승인 대기 목록(status=PENDING +
     * onboardingCompleted=true, policies §5.1)이 실제 온보딩 완료 판정과 어긋난다.**
     * `MemberSpecificationTest`의 정합성 테스트가 이 어긋남을 잡는다.
     *
     * **완전히 같은 보장은 아니다.** SQL `TRIM`은 기본적으로 **공백 문자(space)만** 제거하지만
     * 코틀린 `isBlank()`는 탭·개행 등 유니코드 공백 문자를 전부 공백으로 취급한다. 이 프로젝트의
     * 현재 쓰기 경로(온보딩 제출은 trim + `@NotBlank`로 검증됨)로는 탭·개행이 저장될 수 없어
     * 도달 가능한 범위(공백 문자만 있는 문자열)에서는 두 판정이 일치한다 — 그 범위를 넘는 보장을
     * 여기서 주장하지 않는다(WR-05: 과장된 보장 문구 자체가 이 결함의 원인이었다).
     */
    fun onboardingCompleted(flag: Boolean?): Specification<Member>? {
        if (flag == null) return null
        return Specification { root, _, criteriaBuilder ->
            val nameFilled =
                criteriaBuilder.and(
                    criteriaBuilder.isNotNull(root.get<String>("name")),
                    criteriaBuilder.notEqual(criteriaBuilder.trim(root.get<String>("name")), ""),
                )
            val phoneFilled =
                criteriaBuilder.and(
                    criteriaBuilder.isNotNull(root.get<String>("phoneNumber")),
                    criteriaBuilder.notEqual(criteriaBuilder.trim(root.get<String>("phoneNumber")), ""),
                )
            val completed = criteriaBuilder.and(nameFilled, phoneFilled)
            if (flag) completed else criteriaBuilder.not(completed)
        }
    }
}
