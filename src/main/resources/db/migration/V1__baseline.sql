-- V1: 베이스라인.
-- 스키마의 유일한 주체는 Flyway 이며(ddl-auto=validate), 실제 테이블은 도메인 설계 확정 후 V2 부터 추가한다.
-- 이 마이그레이션은 Flyway 배선(flyway_schema_history 생성)만 확정하는 no-op 이다.
SELECT 1;
