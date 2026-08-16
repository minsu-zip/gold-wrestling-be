package com.goldwrestling.batch

import com.goldwrestling.common.error.DomainException
import com.goldwrestling.common.error.ErrorCode

/**
 * 이미 실행 중인 배치가 있어 새 실행을 거부할 때(CR-01, D-117·D-118).
 *
 * **발생 조건:** `BatchExecutionRecorder.start`가 `RUNNING` 행을 넣을 때 V10의 부분 유니크 인덱스
 * `uq_batch_execution_running`을 위반한 경우다 — 즉 다른 실행이 아직 끝나지 않았다는 뜻이다.
 *
 * **이것은 정상 경로다.** 관리자의 더블클릭, 수동 실행 API 재시도, cron(04:00)과 수동 실행이 겹치는
 * 상황은 모두 예상된 상황이고, **거부가 곧 안전한 결과**다 — 두 실행을 나란히 돌리면 같은 주기가
 * 두 번 차감된다(조건부 UPDATE는 잔여 음수만 막고 주기 중복은 막지 않는다). 그래서 이 예외는 버그
 * 신호가 아니며, 서버 오류(5xx)가 아니라 상태 충돌(409)로 나간다.
 *
 * 메시지에 제약조건명·SQL·실행 id를 담지 않는다(conventions §8) — 사용자 대면 문구만 나간다.
 */
class BatchAlreadyRunningException :
    DomainException(
        ErrorCode.BATCH_ALREADY_RUNNING,
        "이미 실행 중인 배치가 있습니다. 실행이 끝난 뒤 다시 시도하세요.",
    )

/**
 * 요청한 배치 실행 이력이 없을 때(WR-05, `AdminBatchService.getExecution`).
 *
 * 관리자가 `Location`이 가리키는 진행 상태 조회(`GET /api/admin/batch/inactivity-runs/{id}`)를
 * 잘못된 id로 호출한 경우다.
 *
 * **[batchExecutionId]를 메시지에 보간하지 않는다** — 응답 문구로 "그 id의 실행이 존재하는가"를
 * 탐색할 수 있게 되는 것을 막기 위해서다(conventions §8, `PassNotFoundException`·
 * `MemberNotFoundException`과 동일한 선례). 파라미터를 받기만 하고 쓰지 않는 것도 그 선례와 같다 —
 * 호출부가 "무엇을 못 찾았는지"를 명시하게 두되 응답에는 싣지 않는다.
 *
 * **추적은 로그가 담당한다** — `AdminBatchService.getExecution`이 던지기 직전에 id를 남긴다.
 * 응답에 담지 않는 것과 아무 데도 남기지 않는 것은 다르다: 후자면 운영 중 "어떤 id가 404였나"를
 * 되짚을 수단이 없다(PR #16 리뷰 Info 2).
 */
@Suppress("UNUSED_PARAMETER")
class BatchExecutionNotFoundException(
    batchExecutionId: Long?,
) : DomainException(
        ErrorCode.BATCH_EXECUTION_NOT_FOUND,
        "배치 실행 이력을 찾을 수 없습니다.",
    )
