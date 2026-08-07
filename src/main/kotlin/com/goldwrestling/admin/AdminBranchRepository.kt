package com.goldwrestling.admin

import org.springframework.data.jpa.repository.JpaRepository

/**
 * `AdminBranch`(Admin↔Branch 다대다 매핑) 조회 전용 저장소 — 관리자 스케줄 보드(04-11)가
 * `branchId` 인가 검증(T-04-53)에 처음 쓴다.
 *
 * **주의**: `AdminSeeder`는 시드 관리자에게 이 매핑을 만들지 않는다(v1은 지점이 하나뿐이라
 * 매핑 없이도 운영에 지장이 없다는 Phase 1 결정, `AdminSeeder` KDoc 참고). 그래서 이 저장소를
 * 호출하는 쪽은 매핑이 비어 있는 경우(모든 시드/기존 관리자)를 정상 상태로 다뤄야 한다 —
 * `AdminScheduleService.resolveBranchId`가 그 대체 경로를 갖고 있다.
 */
interface AdminBranchRepository : JpaRepository<AdminBranch, Long> {
    /** `branchId` 생략 시 관리자의 기본 지점으로 쓴다 — MVP는 지점이 하나뿐이라 "첫" 지점이 곧 유일한 지점이다. */
    fun findFirstByAdminId(adminId: Long): AdminBranch?

    /** 관리자가 명시적으로 요청한 [branchId]에 실제로 소속돼 있는지 검증한다(T-04-53). */
    fun existsByAdminIdAndBranchId(
        adminId: Long,
        branchId: Long,
    ): Boolean
}
