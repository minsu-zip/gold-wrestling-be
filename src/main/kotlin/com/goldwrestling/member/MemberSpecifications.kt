package com.goldwrestling.member

import org.springframework.data.jpa.domain.Specification

/**
 * 관리자 회원 검색(`GET /api/admin/members`, D-035)의 동적 조건. 각 함수는 조건이 없으면 `null`을
 * 반환하고, 호출부가 `Specification.allOf(listOfNotNull(...))`(Spring Data JPA 4.x 정적 팩토리 —
 * `unrestricted()`를 항등원 삼아 `and`로 리듀스한다. 구버전의 `Specification.where(...)`은 이 라인에서
 * deprecated 되지 않았지만 `allOf`가 여러 조건을 한 번에 묶는 더 직접적인 표현이라 이걸 쓴다.
 * verify-boot4-api 절차로 spring-data-jpa 4.1.0 소스를 확인했다)로 합친다.
 */
object MemberSpecifications {
    /** LIKE 패턴에서 이스케이프 문자로 쓸 문자. `%`·`_`·이 문자 자체를 이스케이프하는 데 쓴다. */
    private const val LIKE_ESCAPE_CHAR = '\\'

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
     */
    fun keywordContains(keyword: String?): Specification<Member>? {
        if (keyword.isNullOrBlank()) return null
        val trimmed = keyword.trim()
        val escapedNameKeyword = escapeLikeWildcards(trimmed).lowercase()
        val escapedPhoneKeyword = escapeLikeWildcards(PhoneNumberNormalizer.normalize(trimmed))

        return Specification { root, _, criteriaBuilder ->
            val namePredicate =
                criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")),
                    "%$escapedNameKeyword%",
                    LIKE_ESCAPE_CHAR,
                )
            val phonePredicate =
                criteriaBuilder.like(
                    root.get("phoneNumber"),
                    "%$escapedPhoneKeyword%",
                    LIKE_ESCAPE_CHAR,
                )
            criteriaBuilder.or(namePredicate, phonePredicate)
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
     * **[Member.isOnboardingCompleted]와 반드시 같은 판정 규칙이어야 한다.** 이름·전화번호가 모두
     * `null`이 아니고 빈 문자열(`""`)도 아닐 때 완료로 본다. 이 규칙이 엔티티 메서드와 이 함수
     * 두 곳에 존재하므로, 한쪽만 고치면 **관리자 승인 대기 목록(status=PENDING +
     * onboardingCompleted=true, policies §5.1)이 실제 온보딩 완료 판정과 어긋난다.**
     * `MemberSpecificationTest`의 정합성 테스트가 이 어긋남을 잡는다.
     */
    fun onboardingCompleted(flag: Boolean?): Specification<Member>? {
        if (flag == null) return null
        return Specification { root, _, criteriaBuilder ->
            val nameFilled =
                criteriaBuilder.and(
                    criteriaBuilder.isNotNull(root.get<String>("name")),
                    criteriaBuilder.notEqual(root.get<String>("name"), ""),
                )
            val phoneFilled =
                criteriaBuilder.and(
                    criteriaBuilder.isNotNull(root.get<String>("phoneNumber")),
                    criteriaBuilder.notEqual(root.get<String>("phoneNumber"), ""),
                )
            val completed = criteriaBuilder.and(nameFilled, phoneFilled)
            if (flag) completed else criteriaBuilder.not(completed)
        }
    }

    /** `%`·`_`·이스케이프 문자 자체를 [LIKE_ESCAPE_CHAR]로 이스케이프해 LIKE 와일드카드로 해석되지 않게 한다. */
    private fun escapeLikeWildcards(value: String): String =
        value
            .replace("$LIKE_ESCAPE_CHAR", "$LIKE_ESCAPE_CHAR$LIKE_ESCAPE_CHAR")
            .replace("%", "$LIKE_ESCAPE_CHAR%")
            .replace("_", "${LIKE_ESCAPE_CHAR}_")
}
