package com.goldwrestling.member

import com.goldwrestling.member.dto.MemberDetailResponse
import com.goldwrestling.member.dto.MemberSearchCondition
import com.goldwrestling.member.dto.MemberSummaryResponse
import com.goldwrestling.member.dto.PageResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 관리자 회원 검색·상세 조회(MEMBER-01의 목록 부분, MEMBER-02, D-035).
 *
 * 전용 "승인 대기 목록" API를 따로 두지 않는다 — [search]가 `status`·`onboardingCompleted` 조건
 * 조합으로 그 역할을 겸한다. `status=PENDING` + `onboardingCompleted=true` 조합이 정확히
 * policies §5.1("프로필이 입력된 PENDING 회원만 노출")과 같다.
 */
@Service
@Transactional(readOnly = true)
class AdminMemberService(
    private val memberRepository: MemberRepository,
) {
    /**
     * 검색어·상태·온보딩완료 조건을 조합해 회원을 페이지 단위로 조회한다. 정렬은 `createdAt`
     * 내림차순 고정(최근 가입자가 위) — 관리자가 새로 들어온 승인 대기자를 먼저 보게 하기 위해서다.
     *
     * `PageResponse.from(page) { MemberSummaryResponse.from(it) }` 변환을 이 메서드(트랜잭션 안)에서
     * 끝내야 한다 — `MemberSummaryResponse`는 LAZY 연관을 쓰지 않지만, 관례를 일관되게 유지하고
     * 트랜잭션 경계 밖에서 엔티티가 새어나가지 않게 한다(D-020).
     */
    fun search(condition: MemberSearchCondition): PageResponse<MemberSummaryResponse> {
        val specification =
            Specification.allOf<Member>(
                listOfNotNull(
                    MemberSpecifications.keywordContains(condition.keyword),
                    MemberSpecifications.hasStatus(condition.status),
                    MemberSpecifications.onboardingCompleted(condition.onboardingCompleted),
                ),
            )
        val pageable = PageRequest.of(condition.page, condition.size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val page = memberRepository.findAll(specification, pageable)
        return PageResponse.from(page) { MemberSummaryResponse.from(it) }
    }

    /**
     * 회원 상세를 조회한다. `MemberDetailResponse.from(member)` 변환이 트랜잭션 안에서 일어나야
     * `member.branch.name`(LAZY) 접근이 안전하다.
     */
    fun getDetail(memberId: Long): MemberDetailResponse {
        val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        return MemberDetailResponse.from(member)
    }
}
