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
| 기간 변경 이력             | `PassPeriodChange`                         | 저녁반 기간·횟수권 유효기간 변경의 전값/후값/사유/주체/시각 (D-057) |
| 공지사항                   | `Notice`                                   |                                                                |
| 알림                       | `Notification`                             | 관리자 인앱 알림                                               |

## 이용권 (Phase 3)

| 한국어                 | 코드 네이밍               | 설명                                                                                          |
| ---------------------- | -------------------------- | ----------------------------------------------------------------------------------------------- |
| 이용권 저장 상태       | `PassStatus`                | `ACTIVE` / `CANCELED` — DB에 저장되는 값은 이 2종뿐                                             |
| 이용권 표시 상태       | `PassDisplayStatus`         | `USABLE` / `EXPIRED` / `EXHAUSTED` / `CANCELED` — 저장하지 않고 조회 시점에 계산 (D-064)        |
| 저녁반 회비 기간 단위  | `EveningMembershipTerm`     | `ONE_MONTH` / `THREE_MONTHS` / `SIX_MONTHS` — 등록 요청의 닫힌 집합, 저장하지 않음 (D-063)       |
| 가감 사유 메모         | `note`                       | DB `pass_transaction.note` — 사유 **코드** `reason`(`TransactionReason`)과 분리된 자유 텍스트 (D-061) |
| 이용권 등록 주체       | `registeredBy`               | DB `pass.registered_by_admin_id` — 이용권을 등록한 관리자                                       |

## 수업 종류 (ClassType)

| 한국어           | 코드      |
| ---------------- | --------- |
| 저녁반(단체수업) | `EVENING` |
| 예약제 수업      | `SESSION` |
| 1:1 레슨         | `LESSON`  |

## 시간표·예약 (Phase 4)

| 한국어                     | 코드 네이밍                                | 설명                                                                                              |
| -------------------------- | ------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| 정기 시간표                | `ClassSchedule`                             | 요일+시각+수업종류+정원으로 정의되는 주간 반복 시간표. Flyway 시드로 고정                          |
| 날짜별 수업                | `ClassSession`                              | `ClassSchedule`이 특정 날짜에 실체화된 것                                                          |
| 날짜별 수업 상태           | `ClassSessionStatus`                        | `SCHEDULED` / `CANCELED`(휴강)                                                                     |
| 예약                       | `Reservation`                               | 회원 ↔ `ClassSession`                                                                              |
| 예약 상태                  | `ReservationStatus`                         | `ACTIVE` / `CANCELED`                                                                              |
| 복구 여부                  | `refunded`                                  | DB `reservation.refunded` — 관리자 대리 취소의 "복구 안 함" 선택 결과                              |
| 취소 주체(회원)            | `canceledByMember`                          | 예약을 취소한 회원 (`canceledByAdmin`과 둘 중 정확히 하나만 채워진다)                              |
| 취소 주체(관리자)          | `canceledByAdmin`                           | 예약을 대리 취소한 관리자 (`canceledByMember`와 둘 중 정확히 하나만 채워진다)                      |
| 예약 인원                  | `reservedCount`                             | `ClassSession`의 현재 활성 예약 수                                                                 |
| 정원                       | `capacity`                                  | `ClassSchedule`/`ClassSession`의 최대 예약 가능 인원 (`EVENING`은 null)                            |
| 관리자 알림                | `Notification`                              | 관리자 인앱 알림 레코드 (append-only)                                                              |
| 알림 종류                  | `NotificationType`                          | `RESERVATION_CREATED` / `RESERVATION_CANCELED_BY_MEMBER` / `RESERVATION_CHANGED_BY_MEMBER` / `RESERVATION_CANCELED_BY_ADMIN` / `RESERVATION_CHANGED_BY_ADMIN` / `CLASS_SESSION_SUSPENDED` |
| 주 범위                    | `WeekRange`                                 | 월요일 시작, 월~일 7일 범위 계산 유틸 (`common/time`)                                              |
| 휴강 처리                  | `suspend`                                   | 특정 날짜의 수업을 휴강 상태로 전환하는 동작                                                       |
| 휴강 해제                  | `resume`                                    | 휴강된 수업을 다시 예약 가능 상태로 되돌리는 동작 (취소된 예약은 자동 복원하지 않는다)               |

## 회원 상태 (MemberStatus)

`PENDING`(승인대기) / `ACTIVE`(활성) / `ON_LEAVE`(휴회) / `INACTIVE`(비활성)

## 인증·회원 (Phase 2)

| 한국어                 | 코드 네이밍                   | 설명                                                                                          |
| ---------------------- | ------------------------------ | ----------------------------------------------------------------------------------------------- |
| 카카오 사용자 식별자   | `kakaoId`                      | 카카오가 발급하는 회원 고유 번호 (DB `kakao_id`). 회원 1명당 1개, 중복 가입 방지의 기준         |
| 리프레시 토큰(개념)    | `RefreshToken`                 | access 토큰 재발급용 자격증명 (DB `refresh_token`). DB에 해시로 저장                            |
| 액세스 토큰(값)        | `accessToken`                  | 클라이언트가 매 요청에 실어 보내는 단기 토큰 값                                                 |
| 리프레시 토큰(값)      | `refreshToken`                 | access 재발급에 쓰는 장기 토큰 값                                                                |
| 토큰 회전              | `rotation`                     | refresh를 쓸 때마다 새 refresh를 발급하고 기존 것을 폐기하는 방식                                |
| 토큰 주체 종류         | `PrincipalType` (`MEMBER` / `ADMIN`) | Member/Admin이 별도 테이블이라 토큰이 어느 쪽을 가리키는지 구분한다                        |
| 인증 주체              | `AuthenticatedPrincipal`       | 요청을 수행하는 회원/관리자를 나타내는 값 객체 (엔티티가 아니다)                                |
| 관리자 로그인 ID       | `loginId`                      | 관리자 ID/PW 로그인의 아이디 (DB `login_id`)                                                     |
| 비밀번호 해시          | `passwordHash`                 | 관리자 비밀번호의 해시값 (DB `password_hash`). 평문 비밀번호는 어디에도 저장하지 않는다          |
| 거절 사유              | `rejectionReason`              | 가입 거절 시 기록 (DB `rejection_reason`, D-034)                                                 |
| 온보딩                 | `Onboarding`                   | 최초 로그인 회원이 실명·전화번호를 입력하는 절차 (policies §5.1)                                |
| 온보딩 완료 여부       | `onboardingCompleted`          | 별도 상태 컬럼이 아니라 `name`·`phoneNumber` 입력 여부로 판정한다 (D-025)                       |
| 회원 상태 게이트       | `MemberStateGate`              | 엔드포인트가 요구하는 회원 상태를 DB 현재 값 기준으로 검사하는 컴포넌트 (D-033 노트)             |
| 로그인 회원 요약       | `MemberLoginSummaryResponse`   | 카카오 로그인 응답에 담기는 최소 요약 — FE가 온보딩/승인대기/거절 화면을 분기한다 (금지어 `Session`을 피한 이름) |
| 카카오 닉네임          | `kakaoNickname`                | 카카오 프로필 닉네임 (DB `kakao_nickname`). 표시용 보조 정보 — 운영 기준 신원은 온보딩 실명이다. 매 로그인마다 카카오 값으로 갱신되며 동의가 없으면 null (D-083) |
| 카카오 프로필 이미지   | `kakaoProfileImageUrl`         | 카카오 프로필 사진 URL (DB `kakao_profile_image_url`). 640px `profile_image_url`을 저장한다. 동의가 없으면 null (D-083) |

## 차감 사유 (TransactionReason)

| 코드                    | 의미                               |
| ----------------------- | ---------------------------------- |
| `RESERVE`               | 예약 성공 시 차감                  |
| `CANCEL_REFUND`         | 예약 취소로 복구                   |
| `ADMIN_ADJUST`          | 관리자 수동 가감                   |
| `EVENING_HALF`          | 횟수권 회원 저녁반 참여 0.5회 차감 |
| `INACTIVITY`            | 2주 미사용 자동 차감               |
| `CLASS_CANCELED_REFUND` | 휴강으로 인한 복구                 |
| `INITIAL_GRANT`         | 이용권 등록 시 초기 횟수 부여 (D-055) |
| `REGISTRATION_CANCELED` | 등록 취소(오등록 정정) 시 잔여를 0으로 상쇄 (D-059) |

## 기타 규칙

- 금지어: 위 개념에 대해 `Ticket`, `Voucher`, `Coupon`, `Booking`, `Course` 등 다른 단어 사용 금지
- 금지어(인증 영역): `Session`(우리 체계는 STATELESS라 세션 개념이 없다 — `ClassSession`과도 혼동됨), `User`(→ `Member`), `Account`(→ `Member`/`Admin`), `Role` 단독 사용(→ `PrincipalType`)
- 횟수는 0.5 단위가 존재하므로 정수형 금지 — **`DECIMAL(4,1)` + `BigDecimal` 로 확정** (D-016).
  비교는 `equals`가 아니라 `compareTo` 를 쓴다 (`0.5 != 0.50`)
- 날짜/시간은 서버·DB 모두 `Asia/Seoul` 기준으로 명시적 처리
