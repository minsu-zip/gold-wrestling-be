# 용어집 (glossary.md)

> FE/BE 코드, DB, API, 문서 전체에서 아래 네이밍만 사용한다.
> AI 에이전트는 새 개념에 이름을 지을 때 반드시 이 문서에 추가한 뒤 사용한다.

## 핵심 엔티티

| 한국어                     | 코드 네이밍                                | 설명                                                           |
| -------------------------- | ------------------------------------------ | -------------------------------------------------------------- |
| 지점                       | `Branch`                                   | 골드레슬링 지점 (MVP: 송파점 1개)                              |
| 회원                       | `Member`                                   | 카카오 로그인 사용자                                           |
| 관리자                     | `Admin`                                    | 지점 담당자. `AdminBranch`(다대다 매핑)로 담당 지점 연결       |
| 이용권                     | `Pass`                                     | 상위 개념. 아래 3종의 공통 부모                                |
| 저녁반 회비                | `EveningMembership` / `EVENING_MEMBERSHIP` | 기간제                                                         |
| 예약제 횟수권              | `SessionPass` / `SESSION_PASS`             | 횟수제                                                         |
| 1:1 레슨권                 | `LessonPass` / `LESSON_PASS`               | 횟수제                                                         |
| 수업(정기 시간표)          | `ClassSchedule`                            | 요일+시각+종류로 정의되는 주간 반복 시간표                     |
| 타임슬롯(특정 날짜의 수업) | `ClassSession`                             | ClassSchedule이 특정 날짜에 실체화된 것. 예약/출석/휴강의 대상 |
| 예약                       | `Reservation`                              | 회원 ↔ ClassSession                                            |
| 출석                       | `Attendance`                               | ClassSession별 참여 기록 (참고용)                              |
| 차감/복구 이력             | `PassTransaction`                          | ±수량, 사유, 주체, 시각                                        |
| 공지사항                   | `Notice`                                   |                                                                |
| 알림                       | `Notification`                             | 관리자 인앱 알림                                               |

## 수업 종류 (ClassType)

| 한국어           | 코드      |
| ---------------- | --------- |
| 저녁반(단체수업) | `EVENING` |
| 예약제 수업      | `SESSION` |
| 1:1 레슨         | `LESSON`  |

## 회원 상태 (MemberStatus)

`PENDING`(승인대기) / `ACTIVE`(활성) / `ON_LEAVE`(휴회) / `INACTIVE`(비활성)

## 차감 사유 (TransactionReason)

| 코드                    | 의미                               |
| ----------------------- | ---------------------------------- |
| `RESERVE`               | 예약 성공 시 차감                  |
| `CANCEL_REFUND`         | 예약 취소로 복구                   |
| `ADMIN_ADJUST`          | 관리자 수동 가감                   |
| `EVENING_HALF`          | 횟수권 회원 저녁반 참여 0.5회 차감 |
| `INACTIVITY`            | 2주 미사용 자동 차감               |
| `CLASS_CANCELED_REFUND` | 휴강으로 인한 복구                 |

## 기타 규칙

- 금지어: 위 개념에 대해 `Ticket`, `Voucher`, `Coupon`, `Booking`, `Course` 등 다른 단어 사용 금지
- 횟수는 0.5 단위가 존재하므로 정수형 금지 — **`DECIMAL(4,1)` + `BigDecimal` 로 확정** (D-016).
  비교는 `equals`가 아니라 `compareTo` 를 쓴다 (`0.5 != 0.50`)
- 날짜/시간은 서버·DB 모두 `Asia/Seoul` 기준으로 명시적 처리
