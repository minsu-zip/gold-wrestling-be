package com.goldwrestling.member

import com.goldwrestling.auth.TokenService
import com.goldwrestling.member.dto.MemberDetailResponse
import com.goldwrestling.member.dto.MemberSearchCondition
import com.goldwrestling.member.dto.MemberSummaryResponse
import com.goldwrestling.member.dto.PageResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

/**
 * 관리자 회원 검색·상세 조회·승인·거절·상태 변경(MEMBER-01, MEMBER-02, MEMBER-03, D-035, D-044).
 *
 * 전용 "승인 대기 목록" API를 따로 두지 않는다 — [search]가 `status`·`onboardingCompleted` 조건
 * 조합으로 그 역할을 겸한다. `status=PENDING` + `onboardingCompleted=true` 조합이 정확히
 * policies §5.1("프로필이 입력된 PENDING 회원만 노출")과 같다.
 */
@Service
@Transactional(readOnly = true)
class AdminMemberService(
    private val memberRepository: MemberRepository,
    private val tokenService: TokenService,
    private val clock: Clock,
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

    /**
     * 가입 승인(MEMBER-01, policies §5.1). 온보딩을 마친 `PENDING` 회원만 승인할 수 있다 —
     * [Member.isOnboardingCompleted]를 서비스에서 직접 검사하는 이유는, 02-09의 관리자 목록
     * 필터(`status=PENDING&onboardingCompleted=true`)는 화면 노출 범위일 뿐이라 API를 직접
     * 호출하면 우회할 수 있기 때문이다 — 정책은 항상 서버에서 강제한다(T-02-37).
     *
     * **승인은 refresh 토큰을 폐기하지 않는다.** 승인은 권한이 늘어나는 전이라 재로그인을 강요할
     * 이유가 없다(D-044는 "ACTIVE가 아니게 되는" 전이만 강제 로그아웃 대상으로 삼는다).
     */
    @Transactional
    fun approve(memberId: Long): MemberDetailResponse {
        val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        if (member.status != MemberStatus.PENDING) {
            throw MemberStateConflictException("승인 대기 상태의 회원만 승인할 수 있습니다.")
        }
        if (!member.isOnboardingCompleted()) {
            throw MemberStateConflictException("회원이 이름·전화번호를 등록해야 승인할 수 있습니다.")
        }
        member.status = MemberStatus.ACTIVE
        // 이전에 거절되었다가 재신청(PENDING 복귀)을 거쳐 다시 승인되는 경로도 있을 수 있다 —
        // 이 시점에 남은 거절 사유를 지우지 않으면 isRejected() 판정이 흐려진다(D-044 취지).
        member.rejectionReason = null
        return MemberDetailResponse.from(member)
    }

    /**
     * 가입 거절(MEMBER-01, D-034). 데이터를 지우지 않고 `INACTIVE` 전환 + 사유 기록으로 처리해
     * 이력을 남긴다(T-02-39, 탈퇴·장기 미이용 유래의 `INACTIVE`와는 사유 유무로 구분된다).
     *
     * [tokenService]의 `revokeAllForMember`도 기본 전파(REQUIRED)라 이 메서드와 같은 트랜잭션에
     * 참여한다 — 상태 변경과 토큰 폐기가 함께 커밋되거나 함께 롤백된다(T-02-40).
     */
    @Transactional
    fun reject(
        memberId: Long,
        reason: String,
    ): MemberDetailResponse {
        val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        if (member.status != MemberStatus.PENDING) {
            throw MemberStateConflictException("승인 대기 상태의 회원만 거절할 수 있습니다.")
        }
        member.status = MemberStatus.INACTIVE
        member.rejectionReason = reason.trim()
        tokenService.revokeAllForMember(memberId)
        return MemberDetailResponse.from(member)
    }

    /**
     * 회원 상태 변경(MEMBER-03, D-044). 상태 전이는 원칙적으로 자유롭게 둔다 — policies §5.2가
     * "승인 취소는 별도 기능 없이 상태 변경으로 갈음한다"고 해, 어느 상태에서든 4종 중 하나로
     * 자유롭게 바꿀 수 있게 두고 관리자 재량을 막지 않는다.
     *
     * **단, `ACTIVE` 전환만 유일한 예외다(02-REVIEW.md WR-03).** [approve]가 서버측에서 강제하는
     * "온보딩 완료" 규칙(policies §5.1)은 목록 필터가 아니라 서비스 계층의 검사다 — 화면 노출 범위는
     * API 직접 호출로 우회 가능하므로 정책은 항상 서버에서 강제한다(T-02-37). 같은 리소스의 다른
     * 엔드포인트인 이 메서드가 그 규칙을 열어 두면 [approve]의 강제가 무의미해지므로, `newStatus`가
     * `ACTIVE`일 때만 같은 검사를 반복한다. `ON_LEAVE`·`INACTIVE`·`PENDING` 복귀 등 ACTIVE가 아닌
     * 전이는 종전대로 제약이 없다.
     *
     * [MemberStatus.PENDING]으로 되돌리면 거절 사유를 지운다 — D-034의 재신청 처리(관리자가 상태를
     * PENDING으로 되돌리는 것)가 이 메서드로만 가능하다. `UpdateMemberStatusRequest`가 요구사항
     * 문구(ACTIVE/ON_LEAVE/INACTIVE 3종)를 넘어 PENDING까지 허용하는 이유이기도 하다.
     *
     * **차감 제외 상태에서 벗어난 시각은 "제외 집합을 실제로 빠져나올 때" 기록된다**(D-147, WR-06).
     * 이 값은 2주 미사용 차감의 기준일 후보 ③이며(D-105), 제외가 끝난 시각이 곧 2주 유예가 다시
     * 시작되는 시점이다. 제외 집합은 policies §4.3의 차감 예외 상태, 즉
     * [MemberStatus.ON_LEAVE]·[MemberStatus.INACTIVE] 두 개다.
     *
     * 조건을 "`ACTIVE`로 전이할 때"가 아니라 "제외 집합을 벗어날 때"로 잡는 이유는
     * `ON_LEAVE → INACTIVE → ACTIVE`처럼 `ACTIVE`를 거치지 않고 우회하는 실제 운영 경로가 있기
     * 때문이다(CR-04, D-111 정정). 반대로 **제외 집합 안에서의 이동(`ON_LEAVE→INACTIVE`)은 기록하지
     * 않는다** — 그 시점에는 아직 차감이 재개되지 않으므로 유예를 다시 시작할 이유가 없고, 기록해
     * 두면 `INACTIVE`로 오래 머문 회원이 복귀했을 때 기준일이 그 오래전 시각으로 남아 복귀 즉시
     * 소급 차감된다(WR-06이 지적한 바로 그 문제). 종전 규칙(`ON_LEAVE`만 제외 상태)에서는
     * `ON_LEAVE→INACTIVE`가 곧 제외 집합 이탈이었으므로 이 변경은 규칙의 확장이지 방향 전환이 아니다.
     */
    @Transactional
    fun changeStatus(
        memberId: Long,
        newStatus: MemberStatus,
    ): MemberDetailResponse {
        val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        if (newStatus == MemberStatus.ACTIVE && !member.isOnboardingCompleted()) {
            throw MemberStateConflictException("회원이 이름·전화번호를 등록해야 활성 상태로 바꿀 수 있습니다.")
        }
        val previousStatus = member.status
        member.status = newStatus
        if (previousStatus in MemberStatus.DEDUCTION_EXCLUDED && newStatus !in MemberStatus.DEDUCTION_EXCLUDED) {
            member.deductionExclusionExitedAt = OffsetDateTime.now(clock)
        }
        if (newStatus == MemberStatus.PENDING) {
            member.rejectionReason = null
        }
        // "ACTIVE에서 벗어날 때"가 아니라 "전환 후 상태가 ACTIVE가 아니면"으로 판정한다 —
        // PENDING→INACTIVE처럼 원래도 ACTIVE가 아니었던 전이에서도 세션을 끊는 편이 안전하고
        // 규칙이 단순하다(D-044).
        if (newStatus != MemberStatus.ACTIVE) {
            tokenService.revokeAllForMember(memberId)
        }
        return MemberDetailResponse.from(member)
    }
}
