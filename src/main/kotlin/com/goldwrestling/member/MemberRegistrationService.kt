package com.goldwrestling.member

import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.dto.MemberSessionResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
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
     * **동시 최초 로그인 경쟁 처리:** 같은 카카오 계정이 아주 짧은 간격으로 두 번 로그인을 시도하면(예:
     * 중복 클릭, 네트워크 재시도), 두 요청이 모두 `findByKakaoId`에서 "없음"을 보고 둘 다 INSERT를
     * 시도할 수 있다. 이 상태에서 DB 유니크 제약(`uq_member_kakao_id`)이 두 번째 INSERT를 거부하면
     * [DataIntegrityViolationException]으로 나타난다 — 애플리케이션은 이 실패를 오류가 아니라 "다른
     * 요청이 먼저 만들었다"는 정상 흐름으로 흡수하고, 다시 조회해 그 결과를 반환한다. 조회-판단-저장
     * 사이의 경쟁은 애플리케이션 조건문만으로 막을 수 없고, DB 유니크 제약이 마지막 방어선이다(T-02-22).
     */
    @Transactional
    fun findOrCreateByKakaoId(kakaoId: Long): MemberSessionResponse {
        memberRepository.findByKakaoId(kakaoId)?.let { return MemberSessionResponse.from(it) }

        return try {
            MemberSessionResponse.from(createPendingMember(kakaoId))
        } catch (e: DataIntegrityViolationException) {
            val existing =
                memberRepository.findByKakaoId(kakaoId)
                    ?: throw e
            MemberSessionResponse.from(existing)
        }
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
