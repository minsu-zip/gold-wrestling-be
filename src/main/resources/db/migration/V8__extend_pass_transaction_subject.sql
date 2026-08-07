-- V8: pass_transaction 회원 주체 확장 (D-030이 예고한 작업, Phase 4에서 실제로 필요해짐).
-- 이미 커밋된 V4는 수정하지 않는다(conventions §9) — admin_id를 nullable로 완화하고 member_id를 추가한다.
--
-- 왜 CHECK로 "정확히 하나"를 강제하는가: 이 컬럼은 "이 이용권의 소유자"가 아니라
-- "이 이력을 발생시킨 행위자"다. 소유자는 이미 pass.member_id로 추적되므로, 주체 컬럼이
-- 하는 일은 "누가 이 트랜잭션을 만들었는가"뿐이다. 회원 셀프 예약/취소/변경은 member_id,
-- 관리자 대리 취소/변경과 휴강 캐스케이드(CLASS_CANCELED_REFUND)는 admin_id — 둘 중 하나만
-- 채워지지 않으면(둘 다 NULL이거나 둘 다 NOT NULL) 감사 추적("누가 이 행위를 했는가")이
-- 불완전해지거나 모순되므로 DB가 저장 자체를 거부한다.
--
-- 기존 행은 전부 admin_id가 채워져 있고 member_id가 NULL이라 이 CHECK를 그대로 통과한다 —
-- 데이터 백필이 필요 없다.

ALTER TABLE pass_transaction ALTER COLUMN admin_id DROP NOT NULL;
ALTER TABLE pass_transaction ADD COLUMN member_id BIGINT REFERENCES member (id);

ALTER TABLE pass_transaction ADD CONSTRAINT ck_pass_transaction_subject CHECK (
    (admin_id IS NOT NULL AND member_id IS NULL) OR
    (admin_id IS NULL AND member_id IS NOT NULL)
);

CREATE INDEX idx_pass_transaction_member ON pass_transaction (member_id);
