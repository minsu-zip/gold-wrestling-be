---
status: complete
date: 2026-08-31
decisions: [D-146, D-147, D-148, D-149, D-150, D-151]
migrations: [V12]
openapi_paths: 41
tests: 883 passed / 0 failed / 0 skipped
---

# v1 마감 quick task 요약

## 처리한 것

### 정책 결정 3건

| 결정 | 내용 | 산출물 |
|---|---|---|
| **D-146** (D-136 마감) | 보강 수업 **불허** 확정. 요일 검증을 `ClassSessionService.getOrCreate` 단일 초크포인트로 이동 | policies §2, `ClassSessionService`, 출석 테스트 픽스처 3파일 정렬, 신규 테스트 2건 |
| **D-147** (WR-06 마감) | policies §4.3 차감 예외에 `INACTIVE` 추가. 기준일 후보 ③ → "차감 제외 상태 이탈 시각"으로 확장 + 컬럼 리네임 | V12, policies §4.3·§5, glossary, `MemberStatus.DEDUCTION_EXCLUDED`, 신규 테스트 5건 |
| **D-151** | cron 기본값 `false` 유지, 운영 환경에서만 D-130 절차로 활성화 | decisions.md만 (코드 변경 없음) |

### BE-REQ 3건

| ID | 엔드포인트/스키마 | 결정 |
|---|---|---|
| **BE-REQ-005** | `AttendanceRosterEntryResponse.phoneNumber` (nullable) | D-148 |
| **BE-REQ-003** | `GET /api/admin/members/{memberId}/pass-transactions` — `note`·`passStatus` 포함, 취소 이력 포함 | D-149 |
| **BE-REQ-004** | `GET /api/members/me/reservations`에 `from`/`to` (수업 날짜, 양끝 포함) | D-150 |

### 백로그 이관

BE-REQ-001(4xx 스키마) · BE-REQ-002(`/api/admin/me`) · BE-REQ-006(예약 단건 조회) → `.planning/STATE.md` Deferred Items에 v1.1 백로그로 명시 이관.

## 품질 게이트

- `./gradlew build` (ktlintCheck + test **883건**) — **BUILD SUCCESSFUL, 0 failures / 0 errors / 0 skipped**
- `./gradlew generateApiDocs` — openapi.yaml 재생성 완료, **경로 40 → 41**
- FE `pnpm api:types` 재생성 검증 — 새 경로·스키마·필드가 정상 생성됨을 확인 후 FE 워킹트리는 원복(재생성 커밋은 FE 레포의 일)

## 커밋 상태

**미커밋 — 사용자 확인 대기.** CLAUDE.md 커밋 규칙(phase 실행 밖에서는 사용자 지시를 기다린다)에 따름.
