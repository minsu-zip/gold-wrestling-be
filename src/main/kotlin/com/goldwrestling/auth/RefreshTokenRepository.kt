package com.goldwrestling.auth

import com.goldwrestling.admin.Admin
import com.goldwrestling.member.Member
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByTokenHash(tokenHash: String): RefreshToken?

    fun findAllByMemberAndRevokedAtIsNull(member: Member): List<RefreshToken>

    fun findAllByAdminAndRevokedAtIsNull(admin: Admin): List<RefreshToken>

    /**
     * 아직 폐기되지 않은 행일 때만 갱신하는 조건부 폐기 UPDATE (D-021 "조건부 갱신 우선").
     * 반환값은 갱신된 행 수 — **0이면 이 트랜잭션이 경쟁에서 졌다는 뜻**이다: 다른 트랜잭션이
     * 이 호출과 같은 순간(READ COMMITTED) 먼저 같은 행을 폐기했으므로, 지금 이 refresh 원문을
     * 제시한 쪽은 이미 폐기된 토큰의 재사용 시도로 취급해야 한다(D-036).
     *
     * `flushAutomatically = true`: 벌크 UPDATE는 영속성 컨텍스트를 거치지 않고 DB에 직접 SQL을
     * 보낸다. 같은 트랜잭션에서 이 호출 전에 더티 상태로 남아 있는 변경이 있다면, 그 변경이 먼저
     * flush돼야 이 UPDATE가 최신 상태 위에서 정확히 실행된다.
     * `clearAutomatically = true`: 벌크 UPDATE 이후 영속성 컨텍스트를 비운다 — 비우지 않으면
     * 이 트랜잭션이 이후 같은 행을 다시 조회할 때 1차 캐시에 남은 갱신 전 스냅샷(`revokedAt = null`)을
     * 돌려받아, DB에는 이미 반영된 폐기가 애플리케이션 눈에는 안 보이는 상태가 된다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken rt set rt.revokedAt = :now where rt.id = :id and rt.revokedAt is null")
    fun revokeIfUsable(
        @Param("id") id: Long,
        @Param("now") now: OffsetDateTime,
    ): Int

    /** [revokeIfUsable]과 같은 이유로 조건부 벌크 UPDATE — 재사용 감지 시 회원 주체의 미폐기 refresh를 전부 폐기한다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken rt set rt.revokedAt = :now where rt.member.id = :memberId and rt.revokedAt is null")
    fun revokeAllUsableByMemberId(
        @Param("memberId") memberId: Long,
        @Param("now") now: OffsetDateTime,
    ): Int

    /** [revokeIfUsable]과 같은 이유로 조건부 벌크 UPDATE — 재사용 감지 시 관리자 주체의 미폐기 refresh를 전부 폐기한다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken rt set rt.revokedAt = :now where rt.admin.id = :adminId and rt.revokedAt is null")
    fun revokeAllUsableByAdminId(
        @Param("adminId") adminId: Long,
        @Param("now") now: OffsetDateTime,
    ): Int
}
