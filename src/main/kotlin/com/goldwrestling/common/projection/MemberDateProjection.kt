package com.goldwrestling.common.projection

import java.time.LocalDate

/**
 * 회원 id ↔ 날짜 쌍을 `GROUP BY`로 한 번에 가져오는 배치 전용 읽기 모델. 엔티티를 로딩하지 않는다.
 *
 * `pass`·`reservation`·`member` 세 패키지가 함께 쓰는 배치 벌크 조회 결과라 `common`에 둔다
 * (conventions §1 "두 기능 이상이 실제로 쓰기 전에는 넣지 않는다"). JPQL select 절에서
 * `p.member.id as memberId`, `max(...) as date`처럼 이 인터페이스의 getter 이름과 같은 별칭을
 * 붙여야 Spring Data JPA가 `Tuple` 결과를 이 프록시로 매핑한다.
 */
interface MemberDateProjection {
    fun getMemberId(): Long

    fun getDate(): LocalDate?
}
