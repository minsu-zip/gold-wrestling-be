-- V7: 송파점 정기 시간표 시드. D-093(시간표는 시드 고정, CRUD API 없음), policies §2에 근거한다.
-- branch_id는 리터럴 정수로 박지 않고 지점명으로 조회하는 INSERT...SELECT로 채운다 — V2 시드의 id가
-- 환경마다 다를 수 있고, 리터럴을 박으면 FK 위반으로 마이그레이션이 통째로 실패한다.
-- day_of_week 값은 java.time.DayOfWeek.name()과 정확히 같은 대문자 문자열이다(엔티티가
-- @Enumerated(EnumType.STRING)으로 읽는다). 모든 타임은 90분 — end_time = start_time + 90분.
-- LESSON은 EVENING∪SESSION이 열리는 모든 (요일, 시각)의 합집합이다(policies §2 "1:1은 주 26타임").

INSERT INTO class_schedule (branch_id, day_of_week, class_type, start_time, end_time, capacity)
SELECT
    b.id,
    x.day_of_week,
    x.class_type,
    x.start_time,
    x.start_time + INTERVAL '90 minutes',
    x.capacity
FROM branch b
CROSS JOIN (VALUES
    -- EVENING: 월~금 19:00·21:00, capacity NULL — 10행
    ('MONDAY', 'EVENING', TIME '19:00', NULL::int),
    ('MONDAY', 'EVENING', TIME '21:00', NULL::int),
    ('TUESDAY', 'EVENING', TIME '19:00', NULL::int),
    ('TUESDAY', 'EVENING', TIME '21:00', NULL::int),
    ('WEDNESDAY', 'EVENING', TIME '19:00', NULL::int),
    ('WEDNESDAY', 'EVENING', TIME '21:00', NULL::int),
    ('THURSDAY', 'EVENING', TIME '19:00', NULL::int),
    ('THURSDAY', 'EVENING', TIME '21:00', NULL::int),
    ('FRIDAY', 'EVENING', TIME '19:00', NULL::int),
    ('FRIDAY', 'EVENING', TIME '21:00', NULL::int),
    -- SESSION: 화·목·금 11:00·13:00, 토·일 09/11/13/15/17시, capacity 10 — 16행
    ('TUESDAY', 'SESSION', TIME '11:00', 10),
    ('TUESDAY', 'SESSION', TIME '13:00', 10),
    ('THURSDAY', 'SESSION', TIME '11:00', 10),
    ('THURSDAY', 'SESSION', TIME '13:00', 10),
    ('FRIDAY', 'SESSION', TIME '11:00', 10),
    ('FRIDAY', 'SESSION', TIME '13:00', 10),
    ('SATURDAY', 'SESSION', TIME '09:00', 10),
    ('SATURDAY', 'SESSION', TIME '11:00', 10),
    ('SATURDAY', 'SESSION', TIME '13:00', 10),
    ('SATURDAY', 'SESSION', TIME '15:00', 10),
    ('SATURDAY', 'SESSION', TIME '17:00', 10),
    ('SUNDAY', 'SESSION', TIME '09:00', 10),
    ('SUNDAY', 'SESSION', TIME '11:00', 10),
    ('SUNDAY', 'SESSION', TIME '13:00', 10),
    ('SUNDAY', 'SESSION', TIME '15:00', 10),
    ('SUNDAY', 'SESSION', TIME '17:00', 10),
    -- LESSON: 저녁반·예약제가 열리는 모든 타임(EVENING ∪ SESSION), capacity 1 — 26행
    ('MONDAY', 'LESSON', TIME '19:00', 1),
    ('MONDAY', 'LESSON', TIME '21:00', 1),
    ('TUESDAY', 'LESSON', TIME '11:00', 1),
    ('TUESDAY', 'LESSON', TIME '13:00', 1),
    ('TUESDAY', 'LESSON', TIME '19:00', 1),
    ('TUESDAY', 'LESSON', TIME '21:00', 1),
    ('WEDNESDAY', 'LESSON', TIME '19:00', 1),
    ('WEDNESDAY', 'LESSON', TIME '21:00', 1),
    ('THURSDAY', 'LESSON', TIME '11:00', 1),
    ('THURSDAY', 'LESSON', TIME '13:00', 1),
    ('THURSDAY', 'LESSON', TIME '19:00', 1),
    ('THURSDAY', 'LESSON', TIME '21:00', 1),
    ('FRIDAY', 'LESSON', TIME '11:00', 1),
    ('FRIDAY', 'LESSON', TIME '13:00', 1),
    ('FRIDAY', 'LESSON', TIME '19:00', 1),
    ('FRIDAY', 'LESSON', TIME '21:00', 1),
    ('SATURDAY', 'LESSON', TIME '09:00', 1),
    ('SATURDAY', 'LESSON', TIME '11:00', 1),
    ('SATURDAY', 'LESSON', TIME '13:00', 1),
    ('SATURDAY', 'LESSON', TIME '15:00', 1),
    ('SATURDAY', 'LESSON', TIME '17:00', 1),
    ('SUNDAY', 'LESSON', TIME '09:00', 1),
    ('SUNDAY', 'LESSON', TIME '11:00', 1),
    ('SUNDAY', 'LESSON', TIME '13:00', 1),
    ('SUNDAY', 'LESSON', TIME '15:00', 1),
    ('SUNDAY', 'LESSON', TIME '17:00', 1)
) AS x(day_of_week, class_type, start_time, capacity)
WHERE b.name = '송파점';
