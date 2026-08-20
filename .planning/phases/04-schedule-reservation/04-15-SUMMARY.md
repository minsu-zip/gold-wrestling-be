---
phase: 04-schedule-reservation
plan: 15
subsystem: api
tags: [spring-boot-4, jpa, postgresql, openapi, concurrency-test, ktlint]

# Dependency graph
requires:
  - phase: 04-schedule-reservation (04-01~04-14)
    provides: ClassSchedule/ClassSession/Reservation/Notification 스키마·서비스·API 12개 엔드포인트
provides:
  - "Phase 4 요구사항 13종(SCHED-01~03, RESV-01~09, NOTIF-01) 전부 실행 가능한 테스트로 뒷받침됨을 확인"
  - "docs/api/openapi.yaml에 Phase 4가 연 12개 엔드포인트 전부 반영(청크 C 6개 신규 경로 포함)"
  - "docs/error-codes.md 발생 지점 열을 실제 구현과 대조해 정정"
  - "ROADMAP.md Phase 4 절 완료 갱신(15/15, 성공기준 충족 근거)"
  - "실제 HTTP·psql로 실증한 회원 예약→관리자 운영 전체 경로 검증 결과(16/16 PASS)"
affects: [05-batch, 06-operations]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "phase 마감 검증: ./gradlew clean build + 동시성 테스트 2종 각 2회 --rerun 연속 통과로 flaky 여부 재확인"
    - "요구사항→테스트 대응표를 RESEARCH.md의 예측 테스트명이 아니라 실제 존재하는 테스트 파일로 재검증"

key-files:
  created: []
  modified:
    - docs/api/openapi.yaml
    - docs/error-codes.md
    - .planning/ROADMAP.md

key-decisions:
  - "docs/decisions.md(D-089~101)·docs/glossary.md(NotificationType 6종)는 실제 구현과 이미 일치해 변경 없음"
  - "error-codes.md 발생 지점 열을 실제 throw 지점 기준으로 정정하고, RESERVATION_STATE_CONFLICT를 현재 도달 경로 없는 예비 코드로 명시"
  - "Task 3 검증은 사람의 육안 확인이 아니라 오케스트레이터가 실제 로컬 서버에 HTTP 요청을 보내고 psql로 DB를 직접 조회해 판정한 뒤 사용자가 승인하는 방식으로 수행됨"

patterns-established: []

requirements-completed: [SCHED-01, SCHED-02, SCHED-03, RESV-01, RESV-02, RESV-03, RESV-04, RESV-05, RESV-06, RESV-07, RESV-08, RESV-09, NOTIF-01]

# Metrics
duration: ~4h30m (Task 1·2 실행 자체는 약 30분, 나머지는 Task 3 사람 확인 대기)
completed: 2026-08-08
---

# Phase 4 Plan 15: phase 마감 — 전체 검증·문서 정합·openapi 재생성·전체 흐름 실증 Summary

**Phase 4(시간표·예약) 13개 요구사항 전부를 테스트 대응표로 뒷받침하고, openapi.yaml에 12개 엔드포인트를 최종 반영했으며, 회원 예약→관리자 운영 전체 경로를 실제 HTTP·DB 조회로 16/16 PASS 실증**

## Performance

- **Duration:** 약 4시간 30분 (Task 1·2 실행 자체는 약 30분, 대부분은 Task 3 사람 확인 대기 시간)
- **Started:** 2026-08-08T03:10:00Z (KST 12:10)
- **Completed:** 2026-08-08T07:42:00Z (KST 16:42)
- **Tasks:** 3 (Task 1·2 자동 실행 + Task 3 checkpoint:human-verify)
- **Files modified:** 3 (openapi.yaml, error-codes.md, ROADMAP.md) — Task 3는 코드/문서 변경 없음(16/16 PASS, 수정 커밋 불필요)

## Accomplishments

- `./gradlew clean build` 전체 스위트 그린, `ReservationCapacityConcurrencyTest`·`ClassSessionConcurrencyTest`를 각각 `--rerun`으로 2회 연속 실행해 flaky가 아님을 재확인
- 요구사항 13종(SCHED-01~03, RESV-01~09, NOTIF-01) → 실제 존재하는 테스트 파일 대응표를 작성해 전부 커버리지 확인 (대응 없는 항목 0건)
- `docs/api/openapi.yaml`을 `generateApiDocs`로 최종 재생성해 Phase 4가 연 12개 엔드포인트 전부(청크 C의 관리자 스케줄 보드·전체 예약 조회·대리 취소/변경·휴강 처리/해제 6개 포함) 반영
- `docs/error-codes.md`의 "발생 지점" 열을 실제 코드와 수동 대조해 6개 항목 정정, "Phase 4 진행 중에만 유효" 임시 안내 문단 삭제
- 회원 예약 생성→변경→취소, 관리자 스케줄 보드 노출→대리 취소→휴강 캐스케이드→이력 주체 기록까지 이어지는 전체 경로를 실제 로컬 서버 HTTP 요청과 psql 직접 조회로 16개 항목 전부 실증(16/16 PASS)

## Task Commits

Each task was committed atomically:

1. **Task 1: 전체 검증 + 요구사항 13종 커버리지 확인** - `fe3ba4d` (docs)
2. **Task 2: openapi.yaml 최종 재생성 + docs 정합 재확인** - `c120c86` (docs)
3. **Task 3: 회원 예약 흐름 · 관리자 운영 흐름 수동 확인** - 커밋 없음 (검증 전용 태스크, 16/16 PASS로 수정 불필요. 로컬 DB에 남긴 검증 데이터는 사용자 지시로 보존)

**Plan metadata:** (본 커밋 — docs: complete 04-15 plan)

## Files Created/Modified

- `docs/api/openapi.yaml` - Phase 4 청크 C 엔드포인트 6개(admin-schedule·admin-reservation) 추가 반영, 12개 경로 전부 확인
- `docs/error-codes.md` - "시간표·예약 코드" 표의 발생 지점 열을 실제 throw 지점으로 정정, 임시 안내 문단 삭제
- `.planning/ROADMAP.md` - Phase 4 절 완료 표시(15/15 plans), 성공기준 5항목에 충족 근거(플랜·테스트) 추가, Progress 표 갱신

## Decisions Made

- `docs/decisions.md`(D-089~101)와 `docs/glossary.md`(`NotificationType` 6종)는 04-01~04-14 실행 결과와 이미 정확히 일치해 이 플랜에서 추가 수정이 필요 없었다 — SUMMARY 확인 결과 "변경 없음"
- `error-codes.md` 발생 지점 열은 RESEARCH.md가 예측한 서비스 계층 이름(예: `MemberReservationService`)이 아니라 실제 `throw` 지점(엔티티 판정 메서드·전용 리포지토리/서포트 컴포넌트)을 기준으로 정정했다 — 예: `RESERVATION_CAPACITY_EXCEEDED`/`DUPLICATE_RESERVATION`은 `ReservationLedgerSupport`, `RESERVATION_WINDOW_CLOSED`는 `ReservationWindow`, `CLASS_SESSION_NOT_RESERVABLE`은 `ClassSession`·`ReservationPassPolicy`
- `RESERVATION_STATE_CONFLICT`는 코드 전체를 grep한 결과 현재 실제로 던져지는 경로가 없는 예비 코드임을 확인했다 — 모든 CAS(compare-and-swap) 경쟁 실패가 이미 더 구체적인 코드(`RESERVATION_ALREADY_CANCELED` 등)로 분류돼 있기 때문이다. 동작에는 영향이 없으나 문서가 "AdminReservationService가 던진다"고 잘못 기술하고 있어 정정하고 "예비 코드, 현재 실제로 던져지는 경로 없음"으로 명시했다
- Task 3 검증은 **사람이 직접 육안으로 확인한 것이 아니라, 오케스트레이터가 실제 로컬 서버(8080)에 실제 HTTP 요청을 보내고 psql로 DB를 직접 조회해 판정한 뒤, 그 결과를 사용자가 검토·승인**하는 방식으로 수행됐다. 이 사실을 그대로 기록한다 — "사람이 직접 확인했다"는 표현은 사실과 다르다
- 회원 토큰은 카카오 로그인 경로를 거치지 않았다 — 카카오 인가 코드를 실제로 발급받을 수 없어 `JWT_SECRET`으로 `sub`/`principalType` 클레임을 직접 HS256 서명해 발급했다. 카카오 발급 경로 자체(인가 코드 교환, 카카오 API 호출)는 이번 확인 범위 밖이며, 그 경로는 Phase 2에서 이미 검증됐다
- 항목 12(휴강 시 세션당 알림 1건) 검증을 위해 테스트 회원(`member id=3`, "검증용회원B")을 로컬 DB에 직접 추가했다 — 기존 `ACTIVE` 회원이 1명(id=2)뿐이라 같은 세션에 예약 2건을 동시에 만들 수 없었고, 예약이 1건이면 "알림이 정확히 1건"이라는 조건이 자명해져 "세션당 1건 요약"(D-097)이라는 의도를 실제로 검증하지 못하기 때문이다
- 사용자가 로컬 검증 데이터를 **삭제하지 않고 남기기로** 선택했다 — 로컬 DB에 `member id=3`, `pass` 2건, `pass_transaction` 12행, `reservation` 5행, `class_session` 4행, `notification` 6행이 남아 있다(운영 환경에는 영향 없음)

## Deviations from Plan

None - plan executed exactly as written. Task 1·2는 계획된 절차(전체 빌드·동시성 재확인·요구사항 대응표·ROADMAP 갱신, openapi 재생성·문서 정합 재확인)를 그대로 실행했고 발견된 편차가 없었다(decisions.md·glossary.md는 이미 정합 상태였음을 확인한 것이지 수정한 것이 아니다). Task 3는 16개 확인 항목이 전부 통과해 계획이 예비해 둔 "문제 발견 시 수정 커밋" 분기가 발동하지 않았다.

## Issues Encountered

None — 발견된 유일한 문서 부정합(`error-codes.md` 발생 지점 열)은 Task 2의 명시적 검증 항목이었고 그 자리에서 정정했다(별도 이슈로 분류하지 않음).

## Task 3: 회원 예약 흐름·관리자 운영 흐름 검증 결과 (16/16 PASS)

실행 환경: 로컬(`docker compose` Postgres, V1~V8 마이그레이션 적용, 앱 8080), 2026-08-08(토) 16:33~16:36 KST.
실행 주체: 오케스트레이터가 실제 HTTP 요청·psql 조회로 판정, 사용자가 결과를 검토·승인.
테스트 데이터: 회원 A = id 2(박민수, 기존), 회원 B = id 3(검증용회원B, 이 확인을 위해 신규 추가).

### A. 회원 흐름

| # | 확인 내용 | 결과 | 근거 |
|---|-----------|------|------|
| 1 | 주간 시간표 그리드 / 저녁반 `reservable=false`·`capacity=null` / 예약제 `0/10` | PASS | `weekStart=2026-08-03`·`weekEnd=2026-08-09`, EVENING 10셀 전부 `reservable=false`+`capacity=null`, SESSION 16셀 전부 `capacity=10`·`reservedCount=0`, LESSON 26셀 `capacity=1`. 과거 요일 `bookable=0`, 오늘(토) 2, 내일(일) 10 — 마감 판정 동작 확인 |
| 2 | 다음 주 조회는 되지만 전 셀 `bookable=false` | PASS | `weekStart=2026-08-10` 200, `bookableWeek=false`, 52셀 중 `bookable=true` 0개. 부수 확인: 3주 뒤(`2026-08-24`)는 `409 RESERVATION_WINDOW_CLOSED` — 2주 조회 범위(D-095) 경계까지 실증 |
| 3 | 예약 201 + 잔여 즉시 1 차감 | PASS | `SESSION_PASS` 10.0 등록 → 예약 201(`id=1`) → 잔여 `10.0 → 9.0` |
| 4 | 같은 타임 재예약 409 `DUPLICATE_RESERVATION` | PASS | `409 DUPLICATE_RESERVATION` "같은 시간에 이미 예약이 있습니다." |
| 5 | 내 예약 목록에 1건만 | PASS | `totalElements=1`, `id=1` ACTIVE만 |
| 6 | 변경 200 + `CANCEL_REFUND`·`RESERVE` 2건 모두 기록 | PASS | 변경 200(새 `id=2`), `pass-transactions`에 `CANCEL_REFUND(+1.0)`·`RESERVE(-1.0)` 둘 다 존재(총 4건: `INITIAL_GRANT`, `RESERVE`, `CANCEL_REFUND`, `RESERVE`) |
| 7 | 당일 예약 즉시 취소 시 409 `SAME_DAY_MODIFICATION_NOT_ALLOWED` | PASS | 취소·변경 양쪽 모두 `409 SAME_DAY_MODIFICATION_NOT_ALLOWED` — 부수 확인: 당일 **변경** 시도도 취소와 동일하게 거부됨을 실증 |

### B. 관리자 흐름

| # | 확인 내용 | 결과 | 근거 |
|---|-----------|------|------|
| 8 | 관리자 보드 명단에 회원 이름 노출 / 회원 응답에는 없음 | PASS | 보드 셀에 `{"reservationId":2,"memberId":2,"memberName":"박민수"}`. 항목 1 회원 응답 원문에 `name`/`memberName` 문자열 0건 — T-04-73(정보 노출) 경계 확인 |
| 9 | keyword 검색 / 취소된 예약도 포함 | PASS | `?keyword=박민수` 200, `totalElements=3`. 취소분(`id=1`, `status=CANCELED`, `canceledByType=MEMBER`, `canceledByName=박민수`) 포함 |
| 10 | 대리 취소 `refund=false` 시 잔여 그대로 | PASS | 취소 200(`refunded=false`, `canceledByType=ADMIN`), 잔여 `8.0 → 8.0` 유지. 부수 확인: 대응 `pass_transaction` 행이 **없음** — 잔여가 안 변했으니 이력도 없는 것이 정합(CLAUDE.md 규칙 6의 역방향 확인) |
| 11 | 활성 예약 있는 이용권 등록 취소 409 → 정리 후 성공 | PASS | 활성 예약 있을 때 `409 PASS_HAS_ACTIVE_RESERVATION`(D-089). 예약 전부 정리 후 재시도 200, `displayStatus=CANCELED`·잔여 `0.0`·`REGISTRATION_CANCELED(-9.0)` 이력 기록 |
| 12 | 휴강 시 예약 전부 취소·잔여 전부 복구 / 알림 정확히 1건 | PASS | 예약 2건(회원 A `id=2`, 회원 B `id=4`)이 붙은 세션 휴강 → 응답 `canceledReservationCount=2`·`status=CANCELED`. 두 예약 모두 `CANCELED`+`refunded=t`+`canceled_by_admin_id=1`. 잔여 A `8.0→9.0`·B `4.0→5.0`. `notification`에 `CLASS_SESSION_SUSPENDED` **정확히 1건**(예약 2건인데 알림 1건 = 세션당 1건 요약, D-097 실증) |
| 13 | 휴강 타임 재예약 409 `CLASS_SESSION_CANCELED` | PASS | `409 CLASS_SESSION_CANCELED` "휴강된 수업입니다." |
| 14 | 휴강 해제 후 재예약 가능 / 취소분은 복원 안 됨 | PASS | 해제 200(`status=SCHEDULED`), 재예약 201(새 `id=5`). 취소됐던 `id=2`·`id=4`는 `CANCELED` 그대로, 회원 B 잔여 `5.0` 유지(재차감 없음). 부수 확인: 휴강 아닌 세션을 재해제 시도하면 `409 CLASS_SESSION_NOT_CANCELED` |

### C. 데이터 확인 (psql)

| # | 확인 내용 | 결과 | 근거 |
|---|-----------|------|------|
| 15 | `reason`별 집계가 조작 횟수와 일치 | PASS | `INITIAL_GRANT` 2건(+15.0) / `RESERVE` 5건(-5.0) / `CANCEL_REFUND` 2건(+2.0) / `CLASS_CANCELED_REFUND` 2건(+2.0) / `REGISTRATION_CANCELED` 1건(-9.0). 실제 조작(이용권 2개 등록, 예약 5회, 회원 변경 1회 + 관리자 대리 취소 1회, 휴강 취소 2건, 등록 취소 1회)과 전부 일치 |
| 16 | 회원 셀프는 `member_id`, 관리자 대리·휴강은 `admin_id` / 둘 다 채워진 행 없음 | PASS | 12행 전수 확인. 둘 다 채워진 행 **0건**, 둘 다 빈 행 **0건**. 회원 셀프 `RESERVE`·`CANCEL_REFUND`는 `member_id`, 관리자 `INITIAL_GRANT`·대리 `CANCEL_REFUND`·`CLASS_CANCELED_REFUND`·`REGISTRATION_CANCELED`는 `admin_id`. 휴강 복구가 회원 소유 이용권인데도 `admin_id`인 것은 **행위자 기준**이라 정합(T-04-74) |

### 종합

- 16/16 PASS. 실패 항목 0건 → 수정 커밋 없음
- 회원 예약 → 관리자 보드 노출 → 관리자 대리 정리 → 휴강 캐스케이드 → 이력 주체 기록까지 경로 전체가 실제 HTTP·DB로 확인됨
- Core Value("회원이 보는 잔여 횟수는 항상 실제 사용 가능 횟수와 일치") 검증: 잔여 변경 6회 전부 대응 `pass_transaction` 행 존재, 이력 없는 잔여 변경 0건, 잔여가 안 변한 조작(항목 10)에는 이력도 없음

### 로컬 검증 데이터 (보존)

사용자 지시로 삭제하지 않고 로컬 DB에 남겨 두었다 — `member id=3`, `pass` 2건, `pass_transaction` 12행, `reservation` 5행, `class_session` 4행, `notification` 6행. 운영 환경에는 영향 없음.

## Known Stubs

None — 이 플랜은 검증·문서 정합 작업만 수행했고 새 코드를 작성하지 않았다.

## Threat Flags

None — 이 플랜은 새 네트워크 엔드포인트·인증 경로·파일 접근 패턴·스키마 변경을 도입하지 않았다(검증·문서 정합 전용).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 4(시간표·예약)가 15/15 플랜으로 완료됐다 — 요구사항 13종 전부 테스트로 뒷받침되고, `openapi.yaml`이 실제 구현과 일치하며, 회원→관리자 전체 경로가 실제 환경에서 실증됐다
- 다음 단계: `.claude/skills/deliver-phase-chunk/SKILL.md` 절차에 따라 청크 C(`feature/phase-04c-admin-ops`)의 dev PR을 생성한다 — 이 PR이 Phase 4의 마지막(3/3) 청크다
- Phase 5(배치)·Phase 6(운영)는 이 phase가 만든 `Reservation`·`ClassSession`·`Notification` 스키마를 전제로 진행 가능하다 (Phase 5의 "마지막 예약 수업일" 기준일 계산에 `Reservation` 데이터를 바로 재사용할 수 있다)

---
*Phase: 04-schedule-reservation*
*Completed: 2026-08-08*
