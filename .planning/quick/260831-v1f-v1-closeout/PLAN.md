---
task: v1 마감 — 정책 결정 3건 + BE-REQ 3건 처리, 잔여 3건 v1.1 백로그 이관
created: 2026-08-31
mode: quick
---

# v1 마감 quick task

## 배경

v1(M1~M6) 로드맵은 REQUIREMENTS.md Traceability 44/44 Complete로 마감 상태이나,
표 밖에 사용자 판단 대기 항목이 남아 있고 FE의 계약 변경 요청 6건이 전부 미해소였다.
이 작업은 그중 **결정이 내려진 것만** 처리한다.

## 확정된 결정 (2026-08-31 사용자)

1. **D-136** — 보강 수업 **불허**. 요일이 어긋난 `(시간표, 날짜)` 조합의 세션 생성을
   예약 경로와 동일한 검증으로 차단. 검증 위치는 `ClassSessionService.getOrCreate`
   **단일 초크포인트**. 조회 경로(`getRoster`)는 변경하지 않는다(계약 동작 불변).
2. **WR-06** — policies §4.3 차감 예외에 `INACTIVE` 추가. 기준일 후보 ③을
   "차감 제외 상태(`ON_LEAVE`|`INACTIVE`)에서 벗어난 시각"으로 확장하고,
   컬럼을 `returned_from_leave_at` → `deduction_exclusion_exited_at`으로 **리네임**(V12).
3. **cron** — 기본값 `false` 유지. "운영 환경에서만 배포 후 D-130 절차로 활성화" 방침을
   decisions.md에 기록.

## 작업 단위

| # | 항목 | 산출물 |
|---|---|---|
| 1 | D-136 요일 검증 | `ClassSessionService`, policies §2, 테스트 |
| 2 | WR-06 INACTIVE 차감 예외 | V12 마이그레이션, `Member`/`MemberRepository`/`AdminMemberService`/`PassRepository`/배치 후보, policies §4.3, glossary, 테스트 |
| 3 | cron 방침 기록 | decisions.md (코드 변경 없음) |
| 4 | BE-REQ-005 | `AttendanceRosterEntryResponse.phoneNumber` |
| 5 | BE-REQ-003 | `GET /api/admin/members/{memberId}/pass-transactions` (note 포함) |
| 6 | BE-REQ-004 | `GET /api/members/me/reservations` from/to |
| 7 | 마감 | openapi.yaml 재생성, decisions.md 3건, STATE.md 백로그 이관, ktlint→build |

## 범위 밖 (v1.1 백로그로 이관)

- BE-REQ-001 (4xx/5xx ProblemDetail 스키마 선언)
- BE-REQ-002 (`GET /api/admin/me`)
- BE-REQ-006 (`GET /api/admin/reservations/{reservationId}`)
