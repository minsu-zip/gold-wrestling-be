-- V10: 배치 실행 직렬화 — batch_execution에 "실행 중(RUNNING)" 행을 표현할 수 있게 하고,
-- 그런 행이 동시에 2건이 될 수 없도록 DB가 막는다 (D-117, 05-REVIEW.md CR-01).
-- 이미 커밋된 V1~V9는 수정하지 않는다(conventions §9) — 이 파일로 두 가지를 새로 바꾼다.

-- (1) finished_at NOT NULL 완화 — RUNNING 행은 아직 끝나지 않았으므로 종료 시각이 없다.
-- 실행 시작 시점에 finished_at 없이 행을 먼저 넣고, 종료 시점에 같은 행을 확정한다.
-- 이 구조가 "배치 전체가 실패하면 이력이 한 줄도 안 남는다"는 결함(05-REVIEW.md WR-02)을
-- 별도 장치 없이 함께 닫는다 — 시작 기록이 먼저 남기 때문이다.
ALTER TABLE batch_execution ALTER COLUMN finished_at DROP NOT NULL;

-- (2) status = 'RUNNING' 부분 유니크 인덱스 (D-117).
-- 동시에 존재할 수 있는 "실행 중" 행이 최대 1건임을 DB가 물리적으로 보장한다 — 두 번째 실행의
-- INSERT가 유니크 위반으로 거부되므로, 애플리케이션 코드가 "지금 도는 배치가 있나"를 조회한 뒤
-- 판단하는(조회와 삽입 사이에 경쟁이 남는) 방식과 달리 경쟁 창이 아예 없다(D-021 "DB 제약 우선").
--
-- status는 VARCHAR(20)이고 값 CHECK가 없다 — 'RUNNING'·'FAILED' 상태값을 추가하는 데 DDL이
-- 더 필요하지 않다. 두 값은 BatchExecutionStatus enum에만 추가한다.
--
-- 확장 경로: 지금은 배치 종류가 미사용 차감 하나뿐이라 status 단독 인덱스로 충분하다. 두 번째
-- 배치가 생기면 batch_type 컬럼을 추가하고 (batch_type, status) 복합 부분 인덱스로 바꾼다 —
-- 그래야 서로 다른 배치가 서로를 막지 않는다.
--
-- 되돌리기 비용: V11에서 DROP INDEX uq_batch_execution_running 한 줄이면 원복된다. 제약이 걸리는
-- 대상이 원장(pass_transaction)이 아니라 실행 이력이라 데이터에 영구 흔적이 남지 않는다.
CREATE UNIQUE INDEX uq_batch_execution_running ON batch_execution (status) WHERE status = 'RUNNING';

-- (3) V9 (2)번 주석 정정 (05-10, 05-REVIEW.md CR-04).
-- V9은 member.returned_from_leave_at을 "ON_LEAVE → ACTIVE 전이에서만 기록한다"고 적었으나,
-- 지금은 ON_LEAVE에서 벗어나는 **모든 전이**(→ACTIVE / →INACTIVE / →PENDING)에서 기록한다.
-- ON_LEAVE→INACTIVE→ACTIVE 우회 복귀에서 기준일이 사라져 휴회 기간이 소급 차감되던 결함을
-- 막기 위한 확장이다(D-111 정정 항목). 커밋된 V9 파일은 체크섬 때문에 수정할 수 없으므로
-- 정정 사실을 여기에 남긴다.
