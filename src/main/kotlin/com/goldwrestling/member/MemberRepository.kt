package com.goldwrestling.member

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/**
 * `JpaSpecificationExecutor`를 함께 상속해 둔다 — 이후 회원 검색(이름·전화번호 부분 일치 +
 * 상태 필터, D-035)이 이 인터페이스를 다시 건드리지 않고 바로 쓸 수 있게 하기 위해서다.
 */
interface MemberRepository :
    JpaRepository<Member, Long>,
    JpaSpecificationExecutor<Member> {
    fun findByKakaoId(kakaoId: Long): Member?
}
