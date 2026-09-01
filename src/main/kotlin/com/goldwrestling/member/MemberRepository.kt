package com.goldwrestling.member

import com.goldwrestling.common.projection.MemberTimestampProjection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * `JpaSpecificationExecutor`를 함께 상속해 둔다 — 이후 회원 검색(이름·전화번호 부분 일치 +
 * 상태 필터, D-035)이 이 인터페이스를 다시 건드리지 않고 바로 쓸 수 있게 하기 위해서다.
 */
interface MemberRepository :
    JpaRepository<Member, Long>,
    JpaSpecificationExecutor<Member> {
    fun findByKakaoId(kakaoId: Long): Member?

    /**
     * 기준일 후보 ③(D-105) 벌크 조회 — `deductionExclusionExitedAt`이 있는 회원만 1행씩 반환한다.
     * 과거 상태 이력을 저장하는 테이블 없이 이 단일 목적 컬럼(D-111·D-147)만으로 "차감 제외 상태를
     * 벗어난 시각" 후보를 얻는다 — 값이 없는 회원(제외 상태였던 적 없음)은 자연히 결과에서 빠진다.
     * [memberIds]가 빈 컬렉션이면 빈 결과를 반환한다.
     */
    @Query(
        "select m.id as memberId, m.deductionExclusionExitedAt as timestamp from Member m " +
            "where m.id in :memberIds and m.deductionExclusionExitedAt is not null",
    )
    fun findDeductionExclusionExitTimestamps(
        @Param("memberIds") memberIds: Collection<Long>,
    ): List<MemberTimestampProjection>
}
