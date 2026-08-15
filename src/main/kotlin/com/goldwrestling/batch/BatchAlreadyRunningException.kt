package com.goldwrestling.batch

import com.goldwrestling.common.error.DomainException
import com.goldwrestling.common.error.ErrorCode

/**
 * 이미 실행 중인 배치가 있어 새 실행을 거부할 때(CR-01, D-117·D-118).
 *
 * **발생 조건:** `BatchExecutionRecorder.start`가 `RUNNING` 행을 넣을 때 V10의 부분 유니크 인덱스
 * `uq_batch_execution_running`을 위반한 경우다 — 즉 다른 실행이 아직 끝나지 않았다는 뜻이다.
 *
 * **이것은 정상 경로다.** 관리자의 더블클릭, 수동 실행 API의 타임아웃 후 재시도(WR-05), cron(04:00)과
 * 수동 실행이 겹치는 상황은 모두 예상된 상황이고, **거부가 곧 안전한 결과**다 — 두 실행을 나란히
 * 돌리면 같은 주기가 두 번 차감된다(조건부 UPDATE는 잔여 음수만 막고 주기 중복은 막지 않는다).
 * 그래서 이 예외는 버그 신호가 아니며, 서버 오류(5xx)가 아니라 상태 충돌(409)로 나간다.
 *
 * 메시지에 제약조건명·SQL·실행 id를 담지 않는다(conventions §8) — 사용자 대면 문구만 나간다.
 *
 * **파일명 주의:** 다른 패키지의 도메인 예외는 `PassExceptions.kt`처럼 한 파일에 모아 두지만,
 * 배치 예외는 현재 이 한 개뿐이라 ktlint `standard:filename`(단일 클래스 파일은 클래스명을 따른다)이
 * `BatchExceptions.kt`라는 이름을 거부한다. 두 번째 배치 예외가 생기면 그때 두 클래스를
 * `BatchExceptions.kt`로 합치면 된다 — 그 시점에는 규칙이 적용되지 않는다.
 */
class BatchAlreadyRunningException :
    DomainException(
        ErrorCode.BATCH_ALREADY_RUNNING,
        "이미 실행 중인 배치가 있습니다. 실행이 끝난 뒤 다시 시도하세요.",
    )
