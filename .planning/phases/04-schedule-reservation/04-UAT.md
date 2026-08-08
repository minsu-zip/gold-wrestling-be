---
status: complete
phase: 04-schedule-reservation
source: [04-01-SUMMARY.md, 04-02-SUMMARY.md, 04-03-SUMMARY.md, 04-04-SUMMARY.md, 04-05-SUMMARY.md, 04-06-SUMMARY.md, 04-07-SUMMARY.md, 04-08-SUMMARY.md, 04-09-SUMMARY.md, 04-10-SUMMARY.md, 04-11-SUMMARY.md, 04-12-SUMMARY.md, 04-13-SUMMARY.md, 04-14-SUMMARY.md, 04-15-SUMMARY.md]
started: 2026-08-08T09:09:03Z
updated: 2026-08-08T09:30:00Z
verified_by: Claude (전 항목 실서버 HTTP + psql 실측)
---

## Current Test

[testing complete — 14/14 pass, 별건 결함 1건(cosmetic)·관찰 1건(minor)은 Gaps 참조]

## Tests

### 1. 콜드 스타트 스모크 테스트
expected: 앱·DB를 볼륨까지 지우고 새로 올려도 Flyway V1~V8이 순서대로 적용되고 부팅에 성공한다. V7 시드가 `class_schedule` 52행(EVENING 10·SESSION 16·LESSON 26)을 송파점에 넣고, 예약이 하나도 없는 상태에서 `GET /api/members/me/schedule`이 200으로 그리드를 반환한다.
result: pass
verified_by: Claude (실측, 2026-08-08 18:14 KST)
method: |
  기존 로컬 DB를 보존하기 위해 `docker compose down -v` 대신 **일회용 Postgres 컨테이너**(포트 55432,
  빈 볼륨)를 띄우고 앱을 포트 18080으로 부팅했다. 검증 내용(빈 스키마 → Flyway 전량 적용 → 시드 → 부팅
  → 조회)은 동일하다. 확인 후 앱·컨테이너 모두 제거했다.
evidence: |
  - 빈 스키마 확인: `\dt` → "Did not find any tables"
  - Flyway 8건 순차 적용, `success = t` 전부: V1 baseline · V2 create branch member admin ·
    V3 add auth credentials and refresh token · V4 create pass tables · V5 add member kakao profile ·
    V6 create schedule reservation notification · V7 seed class schedule · V8 extend pass transaction subject
  - `class_schedule` 52행 / 송파점: EVENING 10(capacity NULL) · SESSION 16(capacity 10) · LESSON 26(capacity 1)
  - 부팅 성공 2.906초, 로그에 ERROR·Exception 0건. `AdminSeeder`가 시드 관리자 생성
  - primary query: 관리자 로그인 200 → `GET /api/admin/schedule/board?weekStart=2026-08-03` **200**,
    응답 `days` 아래 셀 52개
note: |
  회원 토큰 경로 대신 관리자 보드로 primary query를 확인했다 — 콜드 DB에는 회원이 존재하지 않는다
  (회원은 카카오 로그인으로만 생성된다). 두 경로 모두 `ScheduleGridSkeleton`으로 그리드를 조립하므로
  시드→그리드 조립 경로는 동일하게 검증된다. 회원 경로 조회는 테스트 2에서 별도로 확인한다.

### 2. 회원 주간 시간표 조회
expected: 회원 토큰으로 `GET /api/members/me/schedule?weekStart=<이번 주 월요일>` 호출 시 200. 응답에 `weekStart`(월)·`weekEnd`(일)와 요일×타임 셀이 들어 있다. 저녁반 셀은 노출되지만 `reservable=false`·`capacity=null`, 예약제 셀은 `capacity=10`. 예약이 없는 타임도 세션 없이 "0/정원"으로 나온다. 본인 예약이 있는 셀은 `myReservationId`로 표시된다. 응답 어디에도 다른 회원의 이름·전화번호가 없다. `weekStart`가 월요일이 아니면 409 `RESERVATION_WINDOW_CLOSED`.
result: pass
verified_by: Claude (실측, 2026-08-08 18:16 KST)
evidence: |
  - `weekStart=2026-08-03` → 200, `weekStart/weekEnd = 2026-08-03 ~ 2026-08-09`, 7일 × 52셀
  - 타입별: EVENING 10(`reservable=false`, `capacity=null`) · SESSION 16(`reservable=true`, `capacity=10`)
    · LESSON 26(`reservable=true`, `capacity=1`)
  - 셀 필드 11종: `bookable`·`capacity`·`classScheduleId`·`classSessionId`·`classType`·`endTime`
    ·`myReservationId`·`reservable`·`reservedCount`·`startTime`·`suspended`
  - 개인정보 노출 검사: 응답 전문에서 `박민수`·`검증용회원B`·`phoneNumber`·`memberName` **0건**
  - 본인 예약 표시: 08-09 13:00 셀만 `myReservationId=7`, 나머지 `null`
  - 세션 행이 없는 타임도 `classSessionId=null`·`reservedCount=0`으로 정상 표시(D-094)
expectation_corrected: |
  초안에 "`weekStart`가 월요일이 아니면 400"이라고 썼으나 실제는 **409 `RESERVATION_WINDOW_CLOSED`**
  ("조회할 수 없는 주입니다")다. 코드를 확인한 결과 이것이 의도된 설계 — `ReservationWindow.assertViewable`
  (`ReservationWindow.kt:63`)이 "월요일 아님"과 "조회 범위 밖"을 **하나의 예외로 묶어** 임의의 과거·미래 주
  탐색을 막는다(T-04-17, KDoc에 명시). 구현이 자기 스펙과 일치하므로 결함이 아니라 초안 기대값의 오류로 판단해
  기대값을 정정했다. 다만 FE가 소비하는 계약이므로 아래 관찰 사항에 남긴다.

### 3. 예약 창 경계
expected: 다음 주 월요일을 `weekStart`로 조회하면 200이지만 모든 셀의 `bookable=false`(예약은 이번 주만 가능). 조회 범위를 넘는 주(예: 3주 뒤)를 요청하면 409 `RESERVATION_WINDOW_CLOSED`. 이미 시작된 타임은 `bookable=false`.
result: pass
verified_by: Claude (실측, 2026-08-08 18:16 KST — 오늘은 토요일 2026-08-08)
evidence: |
  | weekStart | 결과 |
  |---|---|
  | 2026-07-27 (지난 주) | 409 `RESERVATION_WINDOW_CLOSED` |
  | 2026-08-03 (이번 주) | 200 · 52셀 중 bookable **10** |
  | 2026-08-10 (다음 주) | 200 · 52셀 중 bookable **0** |
  | 2026-08-17 (2주 뒤) | 409 `RESERVATION_WINDOW_CLOSED` |
  | 2026-08-24 (3주 뒤) | 409 `RESERVATION_WINDOW_CLOSED` |

  "이미 시작된 타임" 경계가 특히 깨끗하게 나왔다 — 현재 18:16, 오늘(08-08 토) 마지막 타임이 17:00이라
  오늘 10셀 전부 `bookable=false`(단 `reservable=true` — 두 필드가 구분됨), 내일(08-09 일) 10셀만
  `bookable=true`. 즉 bookable=10의 내역이 "이번 주 남은 미래 타임"과 정확히 일치한다.

### 4. 예약 생성 + 즉시 차감
expected: 유효한 `SESSION_PASS`를 가진 회원이 `POST /api/members/me/reservations`로 이번 주 예약제 타임을 예약하면 201 + 예약 id 반환. 즉시 이용권 잔여가 1 줄고, `PassTransaction`에 `RESERVE`(-1.0) 이력이 회원 주체로 1건 남는다. 해당 세션의 `reservedCount`가 1 증가한다. LESSON 타임도 `LESSON_PASS`로 동일하게 동작한다.
result: pass
verified_by: Claude (실측, 2026-08-08 18:17 KST)
evidence: |
  **SESSION** — `POST` `{classScheduleId:25, classDate:2026-08-09}` → **201**
  `{"id":8,"classSessionId":12,"classType":"SESSION","startTime":"15:00:00","status":"ACTIVE","passId":3}`
  - `pass.id=3` 잔여 `9.0 → 8.0`
  - `pass_transaction` id=17 `RESERVE` `-1.0`, **`member_id=2` / `admin_id=NULL`** (회원 주체, V8 `ck_pass_transaction_subject` 준수)
  - `class_session.id=12` `reserved_count` `0 → 1`

  **LESSON** — 관리자 API로 `LESSON_PASS`(2회) 발급 후 `{classScheduleId:52, classDate:2026-08-09}` → **201**
  `{"id":9,"classSessionId":14,"classType":"LESSON","startTime":"17:00:00","passId":4}`
  - `pass.id=4` 잔여 `2.0 → 1.0`, `pass_transaction` id=19 `RESERVE` `-1.0` `member_id=2`
  - `class_session.id=14` `reserved_count=1`, `capacity=1` (1:1 슬롯 정원 도달)
observation: |
  LESSON 15:00을 먼저 시도했을 때 이미 같은 시각에 SESSION 예약(id=8)이 있어 409 `DUPLICATE_RESERVATION`이 났다.
  중복 금지가 **수업 종류와 무관하게 (회원, 날짜, 시각)** 기준(`ux_reservation_member_timeslot_active`)임이
  의도치 않게 실증됐다 — 같은 시간에 두 수업을 잡을 수 없다는 정책이 맞게 동작한다.

### 5. 예약 실패 5종이 서로 다른 에러코드로 구분된다
expected: 실패 사유가 각각 다른 `code`로 내려온다 — 중복 예약, 정원 초과, 잔여 부족, 예약 창 밖(주 단위/시작 이후), 휴강. 저녁반 타임 예약 시도는 거부된다. **실패한 요청은 잔여 횟수와 `reservedCount`를 전혀 바꾸지 않는다.**
result: pass
verified_by: Claude (실측, 2026-08-08 18:18~18:19 KST)
evidence: |
  | # | 시나리오 | 응답 |
  |---|---|---|
  | ① | 본인이 08-09 13:00 재예약 | 409 `DUPLICATE_RESERVATION` — "같은 시간에 이미 예약이 있습니다." |
  | ② | 회원B가 정원 찬 LESSON 17:00(cap=1) 예약 | 409 `RESERVATION_CAPACITY_EXCEEDED` — "정원이 초과되었습니다." |
  | ③ | 회원B(LESSON권 없음)가 LESSON 13:00 예약 | 409 `INSUFFICIENT_PASS_COUNT` — "잔여 횟수가 부족합니다." |
  | ④ | 다음 주 월요일(08-10) LESSON 예약 | 409 `RESERVATION_WINDOW_CLOSED` — "예약 가능한 주(이번 주)가 아닙니다." |
  | ⑤ | 이미 시작된 오늘(08-08) 타임 예약 | 409 `RESERVATION_WINDOW_CLOSED` — "이미 시작된 수업은 예약할 수 없습니다." |
  | ⑥ | 휴강 처리된 08-09 09:00 예약 | 409 `CLASS_SESSION_CANCELED` — "휴강된 수업입니다." |

  ④·⑤가 같은 코드지만 `detail` 문구로 원인이 구분되고, 어느 쪽도 날짜·시각 값을 노출하지 않는다(T-04-16).
  ③은 "LESSON 예약에 SESSION_PASS를 끌어쓰지 않는다"(교차 사용 금지, policies §3)를 함께 증명한다.

  **무변화 확인** — 위 6건을 모두 실행한 뒤 상태가 시작 시점과 완전히 동일:
  `pass1=0.0 pass2=5.0 pass3=8.0 pass4=1.0` / `session1=0 3=0 4=0 11=1 12=1 14=1`
not_reproducible: |
  **저녁반(EVENING) 예약 거부는 실서버로 재현할 수 없었다 — 결함이 아니라 환경 제약이다.**
  `reserve`의 검사 순서가 ③예약 창 → ④이용권 종류 판정이라, 저녁반 거부에 도달하려면 저녁반 타임이
  **이번 주의 미래**여야 한다. 그런데 저녁반은 시드상 월~금에만 있고(10행) 오늘이 토요일 18:19라
  이번 주 월~금은 전부 과거다. 따라서 항상 `RESERVATION_WINDOW_CLOSED`가 먼저 나온다(실측 확인).
  이 경로는 `MemberReservationServiceTest.kt:183` `저녁반 타임은 예약할 수 없고 세션도 생성되지 않는다`가
  실제 PostgreSQL로 커버한다. 평일에 재실행하면 HTTP로도 확인 가능하다.

### 6. 본인 예약 목록
expected: `GET /api/members/me/reservations`가 본인의 **활성 예약만** 반환한다 — 취소한 예약은 목록에 나오지 않고, 같은 세션에 다른 회원이 예약해도 섞이지 않는다. 정렬은 `classDate` → `startTime` 오름차순.
result: pass
verified_by: Claude (실측, 2026-08-08 18:20 KST)
evidence: |
  격리 검증을 위해 회원B(id=3)가 회원A와 **같은 세션**(08-09 13:00, `classSessionId=11`)에 예약(id=10)했다.

  - 회원A(2) 목록: **3건** — id=7 SESSION 13:00 · id=8 SESSION 15:00 · id=9 LESSON 17:00, 전부 ACTIVE
    → `classDate`·`startTime` 오름차순 정렬 확인
  - 회원B(3) 목록: **1건** — id=10 SESSION 13:00 (회원A의 id=7과 같은 세션인데 섞이지 않음)
  - DB 실제: 회원2 = ACTIVE 3 + CANCELED 5, 회원3 = ACTIVE 1 + CANCELED 1
    → **취소 6건이 양쪽 목록 어디에도 나오지 않는다**(취소 숨김)
  - 응답 형태는 Phase 2·3과 동일한 `PageResponse`(`content`/`page`/`size`/`totalElements`/`totalPages`)

### 7. 예약 취소 + 잔여 복구
expected: 미래 수업 예약을 취소하면 200, 즉시 잔여가 원래대로 복구되고 `PassTransaction`에 `CANCEL_REFUND`(+1.0) 이력이 남는다. 세션 `reservedCount`도 1 줄어든다. 이미 취소한 예약을 다시 취소하면 거부된다. 등록 취소(`CANCELED`)된 이용권으로 잡힌 예약을 취소하면 **예약만 취소되고 잔여는 복구되지 않는다.**
result: pass
verified_by: Claude (실측, 2026-08-08 18:20 KST)
evidence: |
  예약 id=8(08-09 15:00 SESSION, pass 3) 취소 → **200**, `status=CANCELED`
  - `pass.id=3` 잔여 `8.0 → 9.0`
  - `pass_transaction` id=21 `CANCEL_REFUND` `+1.0`, `member_id=2` / `admin_id=NULL`
  - `class_session.id=12` `reserved_count` `1 → 0`
  - 같은 예약 재취소 → **409 `RESERVATION_ALREADY_CANCELED`** "이미 취소된 예약입니다."
not_reproducible: |
  **"등록 취소된 이용권으로 잡힌 예약 취소 시 잔여 미복구"는 API로 도달할 수 없다 — 그리고 그게 정상이다.**
  이 상태를 만들려면 활성 예약이 있는 이용권을 등록 취소해야 하는데, D-089 선행검사가 이를 막는다:
  활성 예약 id=9가 걸린 pass 4의 등록 취소를 시도 → **409 `PASS_HAS_ACTIVE_RESERVATION`**
  "활성 예약이 있어 등록을 취소할 수 없습니다." (실측)
  즉 이 분기는 D-089를 우회하는 경로(과거 데이터·직접 DB 조작)에 대비한 **방어 분기**이고,
  `MemberReservationCancellationTest.kt:223` `등록 취소된 이용권으로 잡힌 예약을 취소하면 잔여가
  그대로이고 CANCEL_REFUND 이력이 생기지 않는다`가 실제 PostgreSQL로 커버한다.

### 8. 예약 변경 + 당일 취소·변경 거부
expected: 예약을 다른 타임으로 변경하면 200 + 새 예약 id, 이력에 `CANCEL_REFUND`(+1.0)와 `RESERVE`(-1.0) 2건이 남고 **잔여 합계는 변하지 않는다.** 새 타임이 실패하면(정원 초과·창 마감·수업 종류 불일치) 기존 예약이 그대로 ACTIVE로 남는다. 당일 수업의 취소·변경은 둘 다 409 `SAME_DAY_MODIFICATION_NOT_ALLOWED`로 거부된다(변경을 통한 당일취소 우회도 막힌다).
result: pass
verified_by: Claude (실측, 2026-08-08 18:21~18:22 KST)
evidence: |
  **정상 변경** — 예약 id=7(08-09 13:00) → 08-09 15:00 → **200**, 새 예약 id=11
  - 구 예약 id=7 `status=CANCELED`, 새 예약 id=11 `ACTIVE`
  - 이력 2건: id=22 `CANCEL_REFUND` `+1.0` → id=23 `RESERVE` `-1.0`
  - **잔여 `9.0` 그대로** (합계 불변 — 변경은 잔여를 소모하지 않는다)
  - `reserved_count`: 세션11 `1`(회원B 것만 남음) / 세션12 `1`(새 예약)

  **실패 3종 — 모두 기존 예약이 ACTIVE 유지**
  | 시나리오 | 응답 |
  |---|---|
  | 종류 불일치 (SESSION→LESSON) | 400 `RESERVATION_TYPE_MISMATCH` — "같은 종류의 수업으로만 변경할 수 있습니다." |
  | 예약 창 밖 (다음 주 화 08-11) | 409 `RESERVATION_WINDOW_CLOSED` — "예약 가능한 주(이번 주)가 아닙니다." |
  | 정원 초과 (LESSON 17:00, cap=1 이미 참) | 409 `RESERVATION_CAPACITY_EXCEEDED` — "정원이 초과되었습니다." |

  실패 3건 후: `res11=ACTIVE`, `res12=ACTIVE`, 잔여 `pass3=9.0 pass5=1.0` — 모두 무변화(원자성 확인).

  **당일 취소·변경 거부** — 오늘(08-08) 타임은 전부 시작 시각이 지나 회원이 직접 예약을 만들 수 없다.
  그래서 **관리자 대리 변경(예약 창 제약 없음)**으로 예약 id=11을 오늘 17:00 타임으로 옮겨(→ 새 id=13)
  당일 예약을 실제로 만든 뒤 회원 경로로 시도했다:
  - 회원 취소 → **409 `SAME_DAY_MODIFICATION_NOT_ALLOWED`** "당일에는 취소·변경할 수 없습니다."
  - 회원 변경(= 변경을 통한 당일취소 우회) → **동일하게 409 `SAME_DAY_MODIFICATION_NOT_ALLOWED`**
  - 거부 후 `res13=ACTIVE`, `pass3=9.0` 무변화
resolves: |
  이 항목은 `04-HUMAN-UAT.md` 테스트 1의 **"항목 7 미재실행 (범위 밖으로 종결)"**을 실제로 해소한다.
  그때는 당일 예약을 만들 수 없어 재현을 포기했는데, 관리자 대리 변경 경로로 당일 예약을 생성해
  회원 경로의 당일 거부를 HTTP로 직접 확인했다.

### 9. 관리자 스케줄 보드
expected: 관리자 토큰으로 `GET /api/admin/schedule/board` 호출 시 요일×타임 그리드가 나오고, 예약이 있는 셀은 예약자 명단(`reservationId`/`memberId`/`memberName` 3필드만)과 `reservedCount`가 함께 내려온다. 취소된 예약은 명단·카운트 어디에도 없다. 휴강 타임은 `suspended=true` + `cancelReason`. 관리자는 지난 주·다다음 주도 조회되고, `weekStart`가 월요일이 아니면 거부 대신 그 주 월요일로 정규화된다. 명단에 전화번호·회원 상태·이용권 정보는 없다.
result: pass
verified_by: Claude (실측, 2026-08-08 18:23~18:24 KST)
evidence: |
  - 52셀, 셀 필드 11종(회원용과 달리 `reservations`·`cancelReason` 포함, `myReservationId`·`bookable` 없음)
  - 명단 필드는 정확히 **`reservationId`/`memberId`/`memberName` 3개뿐**
    (예: `{'reservationId': 13, 'memberId': 2, 'memberName': '박민수'}`)
  - **취소된 예약 id(7·8·11)는 응답 전문에 0건** — 명단·`reservedCount` 어디에도 반영되지 않음
  - 세션 미실체화 셀 45개 전부 `classSessionId=null`·`reservedCount=0`·`reservations=[]`
  - 개인정보 검사: `phoneNumber`·`memberStatus`·`passId`·`remainingCount` **0건**
  - 주 범위 제한 없음 — `2026-06-01`(과거) · `2026-08-17` · `2026-12-28`(연말, `weekEnd=2027-01-03`) 모두 200
  - 정규화 — `weekStart=2026-08-05`(수) → 거부 없이 `weekStart=2026-08-03`·`weekEnd=2026-08-09` 반환
    (회원용은 같은 입력에 409 — 두 화면의 정책이 의도대로 다르다)
  - 휴강 셀 — 08-09 09:00 휴강 후 `suspended=True`, `cancelReason='보드 휴강 표시 확인'`

### 10. 관리자 전체 예약 조회
expected: `GET /api/admin/reservations`가 기간(`from`/`to`)·수업 종류·회원 검색어·상태 조합으로 전 회원 예약을 페이지로 반환한다. `status`를 생략하면 **취소된 예약도 함께** 나온다(감사용). 정렬은 `reservedAt` 내림차순. 검색어에 `%`·`_`를 넣어도 전체 조회로 확장되지 않고, 앞뒤 공백은 정규화된다. 응답에 회원명·`status`·`refunded`·`canceledAt`·`canceledByType`·취소 주체 이름은 있고 **전화번호는 없다.** `size` 100 초과는 400 `VALIDATION_FAILED`, `from > to`는 400 `INVALID_RESERVATION_SEARCH_RANGE`.
result: pass
verified_by: Claude (실측, 2026-08-08 18:24~18:25 KST)
evidence: |
  | 조건 | 결과 |
  |---|---|
  | 기본(status 생략) | 200 · **13건** (ACTIVE 4 + CANCELED 9) — 취소 포함 확인 |
  | `status=ACTIVE` | 200 · 4건 |
  | `classType=LESSON` | 200 · 2건 (전부 LESSON) |
  | `keyword=박민수` | 200 · 10건 (박민수만) |
  | `keyword=검증` (부분일치) | 200 · 3건 (검증용회원B만) |
  | `keyword='  박민수  '` (앞뒤 공백) | 200 · **10건 — 공백 없는 경우와 동일** |
  | `keyword=%` | 200 · **0건** (와일드카드 확장 안 됨) |
  | `keyword=_` | 200 · **0건** (와일드카드 확장 안 됨) |
  | `size=101` | 400 `VALIDATION_FAILED` |
  | `from=08-31&to=08-01` | 400 `INVALID_RESERVATION_SEARCH_RANGE` — "조회 시작일(from)은 종료일(to)보다 늦을 수 없습니다." |

  - `reservedAt` 내림차순 정렬 프로그램 검증 통과
  - 응답 필드 15종에 `refunded`·`canceledByType`·`canceledByName` 포함, **`phoneNumber` 출현 0건**
  - 취소 주체가 실제로 구분돼 기록됨: id=11 `ADMIN/관리자`, id=8 `MEMBER/박민수`, id=3 `refunded=False`

### 11. 관리자 대리 취소·변경 + 이용권 등록 취소 선행검사
expected: 관리자가 **당일 수업 예약도** 대리 취소할 수 있다(회원과 달리 당일 제약 없음). `refund`를 생략하면 복구되고, `false`면 복구 없이 취소만 된다. 이력의 주체가 관리자로 기록된다(`PassTransaction.member == null`, `admin` 채워짐). 대리 변경도 예약 창 제약 없이 되지만 정원 조건부 갱신은 그대로 거쳐 오버부킹이 생기지 않는다. 활성 예약이 남은 이용권을 등록 취소하려 하면 409 `PASS_HAS_ACTIVE_RESERVATION`, 대리 취소로 정리한 뒤 다시 시도하면 성공한다.
result: pass
verified_by: Claude (실측, 2026-08-08 18:22~18:26 KST)
evidence: |
  **① 당일 예약 대리 취소** — 회원이 방금 409로 거부당한 바로 그 예약(id=13, 오늘 08-08 17:00)을
  관리자가 취소 → **200**, `status=CANCELED`, `refunded=true`, `canceledByType=ADMIN`
  - 잔여 `9.0 → 10.0`, 이력 id=30 `CANCEL_REFUND` `+1.0` **`member_id=NULL` / `admin_id=1`**
  - 같은 예약의 생성 이력(id=29 `RESERVE`)도 관리자 대리 변경이라 `admin_id=1`로 기록됨
    → **주체 구분이 회원/관리자 경로별로 정확히 갈린다** (V8 `ck_pass_transaction_subject`)

  **② `refund=false`** — 새 예약 id=14 생성(잔여 10.0→9.0) 후 `{"refund":false}`로 취소
  → `status=CANCELED`, **`refunded=False`**, 잔여 **`9.0` 그대로**, `CANCEL_REFUND` 이력 건수 변동 없음

  **③ 오버부킹 백도어 없음** — 관리자 대리 변경으로 정원 1인 LESSON 17:00(이미 점유)에 밀어넣기 시도
  → **409 `RESERVATION_CAPACITY_EXCEEDED`** (관리자라고 정원 조건부 UPDATE를 건너뛰지 않는다)

  **④ 등록 취소 2단계 절차 (D-089)**
  1. 활성 예약 id=9가 걸린 pass 4 등록취소 → **409 `PASS_HAS_ACTIVE_RESERVATION`**
  2. 관리자가 예약 9를 대리 취소 → 200 (pass4 잔여 1.0 → 2.0 복구)
  3. 다시 등록취소 → **200**, `displayStatus=CANCELED`, DB `status=CANCELED`
     (잔여 2.0은 `REGISTRATION_CANCELED` `-2.0` 이력과 함께 0.0으로 회수됨)

### 12. 휴강 처리·해제 캐스케이드
expected: `POST /api/admin/class-sessions/suspension`으로 휴강하면 그 타임의 활성 예약이 **전부** 취소되고 잔여가 복구되며, 이력은 회원 취소와 구분되는 `CLASS_CANCELED_REFUND`로 남는다. `reservedCount`는 0으로 초기화되고, 알림은 세션당 정확히 1건만 생성된다. 응답의 `canceledReservationCount`가 실제 취소된 건수와 일치한다. 예약 세션이 아직 없는 타임(시간표+날짜)도 휴강할 수 있다. `POST /api/admin/class-sessions/{id}/resumption`으로 해제하면 취소 시각·사유·주체만 되돌아가고 **취소됐던 예약은 복원되지 않는다.** 휴강 중에는 새 예약이 거부된다.
result: pass
verified_by: Claude (실측, 2026-08-08 18:26 KST)
evidence: |
  준비 — 08-09 13:00 SESSION(session 11)에 **서로 다른 회원 2명**(회원A pass3 / 회원B pass2)이 예약,
  `reserved_count=2`, 사전 알림 24건.

  **휴강 처리** → 200
  `{"status":"CANCELED","cancelReason":"강사 부상으로 휴강","reservedCount":0,"canceledReservationCount":2}`
  - 활성 예약 2건 모두 `CANCELED` + `refunded=t` + `canceled_by_admin_id=1`
    (이전에 회원이 스스로 취소했던 id=7은 `canceled_by_member_id=2` 그대로 — 주체가 덮어써지지 않음)
  - 잔여 복구: pass2 `4.0 → 5.0`, pass3 `8.0 → 9.0`
  - 이력 **`CLASS_CANCELED_REFUND`** `+1.0` 2건 — 회원 취소의 `CANCEL_REFUND`와 **구분되어** 남음(T-04-67)
  - `reserved_count` `2 → 0`, 세션 `status=CANCELED`
  - 알림 **24건 → 25건 = 정확히 1건 증가** — 2명이 취소됐는데 세션당 1건 요약형(D-097) 확인
    `CLASS_SESSION_SUSPENDED` / `reservation_id=NULL` / `class_session_id=11`
  - **`canceledReservationCount=2` = 실제 취소 건수 2** → WR-01 수정(커밋 `a6fc932`)이 실서버에서도 정확
  - 휴강 중 새 예약 → **409 `CLASS_SESSION_CANCELED`**

  **휴강 해제** → 200
  - `status=SCHEDULED`, `cancelReason=NULL`, `canceledAt=NULL`, `canceled_by_admin_id=NULL` (메타데이터 3종만 되돌림)
  - **취소됐던 예약 3건 모두 `CANCELED` 그대로 — 복원되지 않음** (CONTEXT.md 락인, T-04-70)
  - 잔여도 복구분 유지(pass2=5.0, pass3=9.0) — 되돌리지 않는다
  - `reserved_count=0` 유지

  **세션 미실체화 타임 휴강 (D-094)** — 08-09 LESSON 09:00(세션 행 0건)에 휴강 요청
  → 200, 세션 id=32가 `CANCELED` 상태로 **새로 생성**됨, `canceledReservationCount=0`
finding: |
  **[cosmetic] 알림 문구에 '수업'이 중복된다** — 아래 Gaps 참조.
  `CLASS_SESSION_SUSPENDED` 알림 본문이 "8/9 13:00 **예약제 수업 수업이** 휴강 처리되어…"로 저장된다.

### 13. 권한 경계
expected: 회원 토큰으로 관리자 API(`/api/admin/**`)를 호출하면 403 `ACCESS_DENIED`, 토큰 없이 호출하면 401 `UNAUTHENTICATED`. 다른 회원의 예약 id로 취소·변경을 시도하면 403이 아니라 **404 `RESERVATION_NOT_FOUND`**(예약 존재 여부를 노출하지 않음). `PENDING`/`ON_LEAVE`/`INACTIVE` 회원은 예약 API 호출 시 차단된다.
result: pass
verified_by: Claude (실측, 2026-08-08 18:27 KST)
evidence: |
  **회원 토큰 → 관리자 API**: 스케줄 보드 · 전체 예약 조회 · 휴강 처리 **3개 모두 403 `ACCESS_DENIED`**
  **토큰 없음**: 관리자 보드 · 회원 시간표 · 예약 생성 **3개 모두 401 `UNAUTHENTICATED`**

  **IDOR 방어** — 세 요청이 **완전히 같은 응답**을 낸다:
  | 요청 | 응답 |
  |---|---|
  | 회원A가 회원B의 예약 id=12 취소 | 404 `RESERVATION_NOT_FOUND` |
  | 회원A가 회원B의 예약 id=12 변경 | 404 `RESERVATION_NOT_FOUND` |
  | 존재하지 않는 예약 id=99999 취소 | 404 `RESERVATION_NOT_FOUND` |
  → 응답만 보고 "그 예약이 존재하는지"를 구분할 수 없다(T-04-45). 403이었다면 존재가 드러났을 것.

  **비활성 회원 차단** — 회원B를 `ON_LEAVE`로 변경 후 3개 엔드포인트 호출
  (시간표 조회 · 예약 목록 · 예약 생성) → **전부 403 `MEMBER_NOT_ACTIVE`** "승인된 회원만 이용할 수 있습니다."
  확인 후 `ACTIVE`로 원복했다(D-040).

### 14. 초과 예약 0건 (동시성)
expected: `./gradlew test --tests "*ReservationCapacityConcurrencyTest" --rerun` 및 `--tests "*ClassSessionConcurrencyTest" --rerun`이 연속 2회 모두 통과한다. 정원 10명 수업에 20명 동시 예약 → 성공 정확히 10건, 1:1 슬롯에 10명 동시 → 1건, 같은 회원의 같은 타임 10건 동시 → 1건. 매 시나리오에서 "성공 건수 = 활성 예약 행 수 = `RESERVE` 이력 건수 = 세션 `reservedCount`" 4자가 일치한다.
result: pass
verified_by: Claude (실측, 2026-08-08 18:28 KST)
evidence: |
  **2회 연속 `--rerun` 전부 PASSED** (flaky 아님):
  - `정원 10명 수업에 20명이 동시에 예약하면 정확히 10건만 성공하고 실패한 회원의 잔여는 그대로 1.0이다`
  - `같은 회원이 같은 타임에 동시에 10건을 요청하면 1건만 성공하고 잔여가 정확히 1회만 차감된다`
  - `1대1 레슨 슬롯에 10명이 동시에 예약하면 정확히 1건만 성공한다`
  - `스레드 20개가 동시에 같은 시간표-날짜로 getOrCreate를 호출하면 성공 20건, DB 행 1개다`

  **추가 — 실서버 DB 전량 원장 대조 (Core Value 직접 검증)**
  이번 UAT에서 예약 15건·취소 12건·휴강 4회·변경 4회를 실제로 돌린 뒤 전체 정합성을 SQL로 재계산:
  | 검사 | 결과 |
  |---|---|
  | 세션 8개의 `reserved_count` vs 실제 ACTIVE 예약 수 | **8/8 OK** (불일치 0) |
  | 이용권 5개의 `remaining_count` vs `PassTransaction` 합계 | **5/5 OK** (이력 없는 잔여 변경 0건) |
  | 정원(`capacity`) 초과 예약이 있는 세션 | **0건** |
  → "회원이 보는 잔여 = 실제 사용 가능 횟수"가 실사용 시나리오를 거친 뒤에도 유지된다.

## Summary

total: 14
passed: 14
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

<!-- 14개 테스트는 모두 통과했다. 아래는 테스트를 돌리는 과정에서 발견한 별건 결함·관찰이며,
     어느 것도 테스트 실패로 이어지지 않았다(기대값에 포함되지 않았던 항목). -->

- truth: "알림 문구가 회원에게 자연스러운 한국어로 보인다"
  status: defect_found
  reason: "Claude 실측: `SESSION` 수업 알림 4종 중 3종에서 '수업'이 중복 출력된다. `classTypeLabel(SESSION)`이 glossary 표대로 '예약제 수업'을 반환하는데(NotificationService.kt:167) 메시지 템플릿이 뒤에 다시 ' 수업을/수업이/수업으로'를 붙인다."
  severity: cosmetic
  test: 12
  root_cause: "NotificationService.kt 의 메시지 템플릿 3개가 라벨에 이미 '수업'이 들어있는 경우를 고려하지 않는다."
  artifacts:
    - path: "src/main/kotlin/com/goldwrestling/notification/NotificationService.kt"
      line: 41
      issue: "\"${classTypeLabel(...)} 수업을 예약했습니다.\" → SESSION일 때 '예약제 수업 수업을 예약했습니다.'"
    - path: "src/main/kotlin/com/goldwrestling/notification/NotificationService.kt"
      line: 88
      issue: "\"${classTypeLabel(...)} 수업으로 예약을 변경했습니다.\" → '예약제 수업 수업으로 …'"
    - path: "src/main/kotlin/com/goldwrestling/notification/NotificationService.kt"
      line: 112
      issue: "\"${classTypeLabel(...)} 수업이 휴강 처리되어 …\" → '예약제 수업 수업이 …'"
  evidence: |
    DB에 실제로 저장된 문구(중복 발생):
      "박민수님이 8/9 15:00 예약제 수업 수업을 예약했습니다."
      "박민수님이 8/9 13:00 예약제 수업 수업으로 예약을 변경했습니다."
      "8/9 13:00 예약제 수업 수업이 휴강 처리되어 예약 2건이 자동 취소되었습니다."
    같은 템플릿이라도 다른 종류는 정상:
      "검증용회원B님이 8/9 11:00 1:1 레슨 수업을 예약했습니다."   ← OK
      "8/9 09:00 1:1 레슨 수업이 휴강 처리되어 …"                ← OK
    취소 알림만 '수업'을 덧붙이지 않아 정상:
      "박민수님이 8/9 15:00 예약제 수업 예약을 취소했습니다."      ← OK
  impact: |
    데이터 정합성·권한·금액에는 영향이 없다. 다만 Phase 6에서 이 알림을 화면에 그대로 노출하면
    회원·관리자에게 어색한 문구가 보인다. `notification.message`는 생성 시점에 비정규화 저장되므로
    **템플릿을 고쳐도 이미 쌓인 행은 그대로 남는다** — 고칠 거라면 Phase 6가 이 데이터를 소비하기 전이 낫다.
  missing:
    - "메시지 템플릿에서 중복 '수업' 제거 (예: `${classTypeLabel(...)}을 예약했습니다.`) 또는 라벨과 조사를 조합하는 헬퍼 도입"
    - "NotificationServiceTest에 SESSION 종류 문구를 문자열로 단언하는 케이스 추가 — 현재 테스트는 라벨 포함 여부만 보고 중복을 잡지 못한다"

- truth: "400 검증 실패 응답의 detail이 무엇이 잘못됐는지 알려준다"
  status: observation
  reason: "Claude 실측: `size=101`·`classType=NOPE` 등 bean validation 실패가 `code=VALIDATION_FAILED`·`status=400`으로는 정확히 내려오지만, `detail`이 500 계열 fallback과 **같은 문구** '예상하지 못한 오류가 발생했습니다.'다."
  severity: minor
  test: 10
  root_cause: "GlobalExceptionHandler.handleExceptionInternal(:88-90) — 스프링 내장 핸들러가 body를 만들지 않은 경우 상태코드와 무관하게 고정 fallback 문구를 쓴다."
  artifacts:
    - path: "src/main/kotlin/com/goldwrestling/common/error/GlobalExceptionHandler.kt"
      line: 90
      issue: "`ProblemDetail.forStatusAndDetail(statusCode, \"예상하지 못한 오류가 발생했습니다.\")` 가 400에도 그대로 쓰인다"
  scope_note: |
    **Phase 4가 만든 것이 아니라 Phase 2의 전역 핸들러 동작이다.** Phase 4 고유 검증 실패
    (`INVALID_RESERVATION_SEARCH_RANGE`)는 "조회 시작일(from)은 종료일(to)보다 늦을 수 없습니다."처럼
    정확한 문구가 나온다 — 도메인 예외 경로는 문제없고, 스프링 내장 검증 경로만 해당한다.
  impact: |
    FE는 `code`로 분기할 수 있으니 기능적 영향은 없다. 다만 `detail`을 그대로 토스트에 띄우는 구현이면
    입력 실수를 "서버 오류"로 보여준다. conventions §8("내부 정보를 응답에 담지 않는다")을 지키면서도
    "요청 값이 올바르지 않습니다." 정도의 400 전용 문구는 줄 수 있다.
  missing:
    - "handleExceptionInternal에서 4xx/5xx를 구분해 fallback 문구를 분기 (Phase 4 범위 밖 — 별도 판단 필요)"

## 관찰 사항 (결함 아님)

- **`weekStart` 비월요일 처리가 회원/관리자에서 다르다** — 회원은 409 `RESERVATION_WINDOW_CLOSED`로 거부,
  관리자는 그 주 월요일로 정규화. 각각의 스펙(T-04-17 / 04-11)대로이고 의도된 차이지만,
  FE가 두 화면에서 같은 날짜 위젯을 쓴다면 동작이 갈리므로 계약으로 인지하고 있어야 한다.
- **중복 예약 금지는 수업 종류를 가리지 않는다** — 같은 시각에 SESSION과 LESSON을 동시에 잡을 수 없다
  (`ux_reservation_member_timeslot_active`). 정책상 맞는 동작이지만, FE 에러 문구가
  "같은 시간에 이미 예약이 있습니다."라 회원이 "다른 수업인데 왜?"로 느낄 수 있다.
- **저녁반 예약 거부는 예약 창 검사 뒤에 온다** — 저녁반 타임이 예약 창 밖이면 항상
  `RESERVATION_WINDOW_CLOSED`가 먼저 나온다. 검사 순서상 정상이나, 테스트 5에서 이 때문에
  저녁반 전용 에러코드를 HTTP로 재현할 수 없었다(단위·통합테스트로는 커버됨).
