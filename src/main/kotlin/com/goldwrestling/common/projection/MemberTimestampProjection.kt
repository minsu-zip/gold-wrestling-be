package com.goldwrestling.common.projection

import java.time.OffsetDateTime

/**
 * 회원 id ↔ 타임스탬프 쌍을 `GROUP BY`로 한 번에 가져오는 배치 전용 읽기 모델. 엔티티를 로딩하지 않는다.
 *
 * [MemberDateProjection]과 자매 인터페이스 — 날짜(`LocalDate`) 후보와 타임스탬프(`OffsetDateTime`)
 * 후보를 같은 형태로 반환한다. `pass`·`reservation`·`member` 세 패키지가 공유해 `common`에 둔다
 * (conventions §1). JPQL select 절에서 `... as memberId`, `... as timestamp` 별칭이 이 인터페이스의
 * getter 이름과 일치해야 한다.
 */
interface MemberTimestampProjection {
    fun getMemberId(): Long

    fun getTimestamp(): OffsetDateTime?
}
