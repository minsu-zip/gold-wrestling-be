package com.goldwrestling.admin

import com.goldwrestling.common.error.DomainException
import com.goldwrestling.common.error.ErrorCode

/**
 * 관리자가 소속되지 않은 지점(`branchId`)을 명시적으로 요청했을 때(T-04-53). 어느 지점에
 * 소속돼 있는지는 노출하지 않는다 — "그 지점이 존재한다"는 사실 자체를 흘리지 않기 위해서다
 * (conventions §8, `PassNotFoundException`과 동일한 사고).
 */
class AdminBranchNotAssignedException :
    DomainException(
        ErrorCode.ADMIN_BRANCH_NOT_ASSIGNED,
        "소속되지 않은 지점입니다.",
    )
