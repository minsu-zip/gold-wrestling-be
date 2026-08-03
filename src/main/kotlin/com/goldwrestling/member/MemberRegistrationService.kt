package com.goldwrestling.member

import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.dto.MemberSessionResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

/**
 * 카카오 `kakaoId` 기준 회원 find-or-create를 전담하는 별도 빈이다.
 *
 * **왜 [com.goldwrestling.auth.KakaoAuthService]에 이 로직을 두지 않고 별도 빈으로 분리했는가:**
 * `KakaoAuthService`가 자기 자신의 트랜잭션 메서드를 호출하면(self-invocation) 스프링 프록시를
 * 거치지 않아 트랜잭션이 전혀 시작되지 않는다(스프링 AOP의 잘 알려진 한계). 카카오 API 호출(트랜잭션 밖,
 * conventions §7)과 DB 쓰기(트랜잭션 안)를 분리하려면 처음부터 서로 다른 빈이어야 한다.
 */
@Service
@Transactional(readOnly = true)
class MemberRegistrationService(
    private val memberRepository: MemberRepository,
    private val branchRepository: BranchRepository,
    private val clock: Clock,
    @Value("\${goldwrestling.default-branch-name}") private val defaultBranchName: String,
) {
    /**
     * [kakaoId]로 기존 회원을 찾고, 없으면 `PENDING` 상태로 새로 만든다(D-047 기본 지점 배정).
     *
     * **동시 최초 로그인 경쟁 처리는 이 메서드의 책임이 아니다.** 같은 카카오 계정이 아주 짧은 간격으로
     * 두 번 로그인을 시도하면(예: 중복 클릭, 네트워크 재시도), 두 요청이 모두 `findByKakaoId`에서
     * "없음"을 보고 둘 다 INSERT를 시도할 수 있다. 이 상태에서 DB 유니크 제약(`uq_member_kakao_id`)이
     * 두 번째 INSERT를 거부하면 유니크 제약 위반 예외(스프링 데이터 접근 예외 계층)가 발생하는데,
     * 이 메서드는 그 예외를 **잡지 않고 그대로 전파**한다 — 이 트랜잭션이 롤백되며 끝나야 한다.
     *
     * 같은 트랜잭션 안에서 잡아 재조회하는 방식이 왜 불가능한가(02-REVIEW.md CR-01): PostgreSQL은
     * 제약 위반이 난 순간 트랜잭션을 abort하므로, catch 블록에서 다시 `findByKakaoId`를 호출해도
     * "current transaction is aborted, commands ignored until end of transaction block"으로 실패한다.
     * 설령 DB가 이를 허용하더라도, 예외가 리포지토리 프록시(`SimpleJpaRepository`) 경계를 통과하는
     * 순간 스프링이 이 트랜잭션을 rollback-only로 마킹하므로 바깥 경계 커밋 시점에
     * `UnexpectedRollbackException`이 난다. 즉 같은 트랜잭션 안에서는 복구가 구조적으로 불가능하다.
     *
     * 복구(재시도)는 트랜잭션을 갖지 않는 [com.goldwrestling.auth.KakaoAuthService.login]의 책임이다 —
     * 이 메서드를 새 트랜잭션으로 다시 호출하면 그 시점에는 경쟁 상대의 INSERT가 이미 커밋되어 있어
     * `findByKakaoId` 조회만으로 끝난다.
     */
    @Transactional
    fun findOrCreateByKakaoId(kakaoId: Long): MemberSessionResponse {
        memberRepository.findByKakaoId(kakaoId)?.let { return MemberSessionResponse.from(it) }

        return MemberSessionResponse.from(createPendingMember(kakaoId))
    }

    private fun createPendingMember(kakaoId: Long): Member {
        val branch =
            branchRepository.findByName(defaultBranchName)
                ?: throw IllegalStateException(
                    "기본 지점(goldwrestling.default-branch-name=\"$defaultBranchName\")을 찾을 수 없습니다. " +
                        "Branch 시드 데이터를 확인하세요.",
                )
        val member =
            Member(
                branch = branch,
                name = null,
                phoneNumber = null,
                status = MemberStatus.PENDING,
                kakaoId = kakaoId,
                rejectionReason = null,
                createdAt = OffsetDateTime.now(clock),
            )
        return memberRepository.saveAndFlush(member)
    }
}
