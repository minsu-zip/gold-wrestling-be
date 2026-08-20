package com.goldwrestling.pass

import com.goldwrestling.auth.AuthenticatedPrincipal
import com.goldwrestling.member.dto.PageResponse
import com.goldwrestling.pass.dto.PassResponse
import com.goldwrestling.pass.dto.PassTransactionResponse
import com.goldwrestling.pass.dto.PassTransactionSearchCondition
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/**
 * 회원 본인 이용권 조회(PASS-05)·본인 차감/복구 이력 조회(PASS-06).
 *
 * **경로에 `memberId`를 받지 않는다.** 두 메서드 모두 [AuthenticatedPrincipal.requireMemberId]로만
 * 스코프를 얻는다 — `MemberProfileService`와 동일한 형태다(T-03-41). 조회 전용이라 변경 메서드가
 * 없으므로 `@Transactional` 오버라이드가 없다.
 *
 * **의도적으로 `MemberStateGate`를 호출하지 않는다(D-071).** 휴회(`ON_LEAVE`)는 policies §5가
 * "정상적으로 로그인해 쓰는 상태"로 정의하므로 복귀 전 잔여 확인이 가능해야 하고, 등록 자체가
 * 회원 상태를 가리지 않으므로(D-068) 어떤 상태든 본인 이용권은 본인이 볼 수 있어야 한다 —
 * `MemberProfileService.getMyProfile`과 같은 판단이다. 응답은 본인 스코프로만 한정되어
 * 상태 제한 없이도 새어나갈 데이터가 없다.
 */
@Service
@Transactional(readOnly = true)
class MemberPassService(
    private val passRepository: PassRepository,
    private val passTransactionRepository: PassTransactionRepository,
    private val clock: Clock,
) {
    /**
     * 본인 이용권 목록을 조회한다(PASS-05, D-058).
     *
     * **취소(`PassStatus.CANCELED`)만 제외하고 만료·소진은 제외하지 않는다** — 만료·소진 이용권도
     * "내 횟수가 어디로 갔는지" 문의에 답하려면 `displayStatus`와 함께 그대로 노출돼야 한다(D-058).
     * 대비: 관리자 목록(`AdminPassService.getMemberPasses`)은 반대로 취소도 포함해 관리자 화면에서
     * 구분 표시한다 — 회원 화면과 관리자 화면의 노출 범위가 의도적으로 다르다.
     *
     * `PassResponse.from(pass, today)` 변환은 [Pass.member]가 `LAZY` 연관이라 이 트랜잭션(readOnly)
     * 안에서 끝나야 한다.
     */
    fun getMyPasses(principal: AuthenticatedPrincipal): List<PassResponse> {
        val memberId = principal.requireMemberId()
        val today = LocalDate.now(clock)
        return passRepository
            .findAllByMemberIdAndStatusNotOrderByStartDateDescIdDesc(memberId, PassStatus.CANCELED)
            .map { PassResponse.from(it, today) }
    }

    /**
     * 본인 차감/복구 이력을 이용권별 필터 + page/size로 조회한다(PASS-06, D-058).
     *
     * **`condition.passId`를 소유권 검사 없이 그대로 써도 안전한 이유**: [PassTransactionSpecifications.ownedByMember]가
     * non-nullable 필수 조건이라 `Specification.allOf`에서 항상 AND로 결합된다(T-03-42) — 타인의
     * `passId`를 넘겨도 "본인 소유 AND 그 passId" 조건이 되어 결과가 그냥 비어 있을 뿐, 다른 회원의
     * 이력이 새어나갈 경로가 없다.
     *
     * 정렬은 `occurredAt` 내림차순 고정 — 최근 이력이 먼저 보여야 회원이 최근 변동을 바로 확인한다.
     */
    fun getMyTransactions(
        principal: AuthenticatedPrincipal,
        condition: PassTransactionSearchCondition,
    ): PageResponse<PassTransactionResponse> {
        val memberId = principal.requireMemberId()

        val specification =
            Specification.allOf<PassTransaction>(
                listOfNotNull(
                    PassTransactionSpecifications.ownedByMember(memberId),
                    // 취소된 이용권의 이력은 본인 이용권 목록(D-058)과 노출 범위를 맞춰 숨긴다 (D-073).
                    PassTransactionSpecifications.passNotCanceled(),
                    PassTransactionSpecifications.hasPassId(condition.passId),
                ),
            )
        val pageable = PageRequest.of(condition.page, condition.size, Sort.by(Sort.Direction.DESC, "occurredAt"))
        val page = passTransactionRepository.findAll(specification, pageable)
        return PageResponse.from(page) { PassTransactionResponse.from(it) }
    }
}
