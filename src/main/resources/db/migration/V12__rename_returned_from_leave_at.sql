-- V12: 미사용 차감 기준일 후보 ③의 의미 확장에 맞춰 컬럼명을 정정한다 (D-147, WR-06).
-- 이미 커밋된 V1~V11은 수정하지 않는다(conventions §9) — 새 버전으로 고친다.
--
-- 배경: policies §4.3의 차감 예외가 종전 3종(휴회·잔여 0·만료)에서 INACTIVE를 더한 4종이 됐다.
-- 그 결과 기준일 후보 ③은 더 이상 "휴회에서 벗어난 시각"이 아니라 **"차감 제외 상태
-- (ON_LEAVE | INACTIVE)에서 벗어난 시각"**이다. 값의 의미가 바뀌었는데 이름이 `returned_from_leave_at`
-- 으로 남으면 다음 사람이 "휴회 전용"으로 오독해 INACTIVE 경로를 다시 빠뜨린다.
--
-- 데이터 보존: RENAME이므로 기존 행의 값은 그대로 남는다. 종전 규칙에서 이 컬럼에 값이 있는 회원은
-- "ON_LEAVE에서 벗어난 시각"을 갖고 있는데, 그것은 새 의미(차감 제외 상태에서 벗어난 시각)의
-- 부분집합이라 해석이 어긋나지 않는다 — 마이그레이션할 값이 없다.
--
-- PostgreSQL의 ALTER TABLE ... RENAME COLUMN은 카탈로그만 고치므로 테이블 재작성·긴 잠금이 없다.

ALTER TABLE member
    RENAME COLUMN returned_from_leave_at TO deduction_exclusion_exited_at;

COMMENT ON COLUMN member.deduction_exclusion_exited_at IS
    '차감 제외 상태(ON_LEAVE|INACTIVE)에서 벗어난 시각 — 2주 미사용 차감 기준일 후보 ③ (D-105/D-147)';
