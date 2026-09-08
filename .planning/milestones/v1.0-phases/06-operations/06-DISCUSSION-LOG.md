# Phase 6: 운영 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-18
**Phase:** 6-운영
**Areas discussed:** 출석 체크 모델, 저녁반 0.5회 차감, 알림 폴링·활동 피드, CR-03 마감·cron 재활성 (+공지사항 재량안)

---

## 진행 방식

사용자가 4개 영역 전체 선택 후 각 영역의 입장을 선제 제시 — "반대 근거 없으면 확정" 방식.
Claude가 항목별로 코드·정책 대조 검증을 수행했고, 전 항목 반대 근거 없음으로 확정.
빈칸 2건만 AskUserQuestion으로 보완 확정했다.

### 검증에서 확인된 사실 (확정의 근거)
- `deficit.coerceAtLeast(0)` (`InactivityDueDateCalculator.kt:109`) — 소급 출석 불가역이 이미 코드로 방어됨
- `ClassType.EVENING` + V7 시드(월~금 19:00·21:00) 존재 — 저녁반 출석을 EVENING 세션에 매달 수 있음
- `pass_transaction.reason` VARCHAR(30), DB CHECK 없음 — enum 상수 추가만으로 사유 확장 가능
- `InactivityDueDateCandidates.lastAttendanceDate` 필드·5종 max 이미 구현 — CR-03 배선은
  `InactivityBatchRunner.kt:157`의 null 하드코딩 교체만 필요
- `Notification.isRead/readAt`만 `var` — 확인 처리용으로 이미 설계됨 (D-097)

---

## 출석 체크 모델

**User's choice (선제 제시):** 예약제/1:1은 예약자 명단 프리로드 + 출석/불참/미체크 3상태.
저녁반은 빈 명단에서 회원 검색으로 추가하며 추가됨=출석(불참 없음). 소급 입력·수정 허용하되
소급 출석이 기존 INACTIVITY 차감을 되돌리지 않음(다음 배치부터 반영) — policies §6 명시.
**Notes:** Claude가 3상태의 구현 해석을 "레코드 부재=미체크, 레코드는 ATTENDED/ABSENT 2값,
기준일 후보 ①은 ATTENDED만 인정"으로 제안·확정.

## 저녁반 0.5회 차감

**User's choice (선제 제시):** 출석 추가와 한 트랜잭션. 서버 판정: 유효 저녁반 회비 보유 시
차감 없음(회비 우선), 없으면 SESSION_PASS 0.5 차감(D-091 재사용). 회원×세션 유니크로 이중 차감
구조적 방지, 차감은 출석 레코드 연결, 출석 삭제 시 자동 복구.
**Notes:** Claude가 회비 보유 판정 기준일을 D-091 선례대로 수업날로 확정.

### 보완 질문 1 — 차감 불가 회원의 출석 추가

| Option | Description | Selected |
|--------|-------------|----------|
| 출석 추가 거부 (추천) | 409 에러 — ADMIN_ADJUST 충전 후 재시도. 회비 없는 참여가 조용히 기록되는 것을 구조적으로 차단 | ✓ |
| 출석만 기록 | 차감 없이 출석 레코드만 + "차감 없음" 표시 — 참고용 데이터 원칙 우선 | |

### 보완 질문 2 — 출석 삭제 복구 이력의 사유 코드

| Option | Description | Selected |
|--------|-------------|----------|
| EVENING_HALF_REFUND 신설 (추천) | CANCEL_REFUND·CLASS_CANCELED_REFUND 선례와 일관, 원장에서 사유만으로 구분 | ✓ |
| EVENING_HALF +0.5 상쇄 | enum 변경 없지만 원장 집계 해석이 흐려짐 | |
| ADMIN_ADJUST로 복구 | 자동 복구가 "수동 가감"으로 기록돼 의미 왜곡 | |

## 알림 폴링·활동 피드

**User's choice (선제 제시):** 확인은 "모두 읽음"만(개별 읽음 없음), 미확인 카운트는 목록 응답
포함. 활동 피드는 알림 동일 데이터의 다른 뷰, 필터 기간+종류, page/size 기존 형태.
**Notes:** NOTIF-02 문언("확인 처리할 수 있다")과 충돌 없음 확인 후 그대로 확정.

## CR-03 마감·cron 재활성

**User's choice (선제 제시):** CR-03 배선으로 기준일 5종 max 완결. cron 기본값 D-121(꺼짐) 유지,
운영 켜기 절차 문서화: 배선 확인 → 운영 수동 실행 1회 검증 → 환경변수 명시.
**Notes:** Claude가 절차에 정책 시행일 하한(2026-09-01, D-119) 명시를 추가 — 시행일 전
수동 실행은 차감 0건이 정상이라 검증 오독 방지.

## Claude's Discretion

- Attendance/Notice 스키마 상세·API 경로·DTO 형태 (기존 관례 내)
- 출석 체크 API의 일괄 저장 vs 개별 토글
- 공지 정렬 등 세부 조회 규칙
- 공지사항 전체 (사용자 재량 위임 + 방향 지정: 관리자 CRUD + 회원 목록·상세, 제목+본문만,
  page/size 재사용 — Claude가 hard delete로 보완 확정)

## Deferred Ideas

- 회원용 알림 (D-097 유지)
- 알림 실시간 푸시 (M7 이후)
- 공지 첨부·고정
- 알림 보존 기간·아카이빙
