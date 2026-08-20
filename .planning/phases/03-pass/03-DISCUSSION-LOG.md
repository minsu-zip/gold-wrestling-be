# Phase 3: 이용권 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-03
**Phase:** 3-이용권
**Areas discussed:** 등록 규칙, 수동 가감 정책, 기간 수정 이력, 본인 조회 범위, 오등록 정정(사용자 추가)

---

## 진행 방식

Claude가 4개 gray area(등록 규칙 / 수동 가감 정책 / 기간 수정 이력 / 본인 조회 범위)를 제시.
사용자가 4개 모두 선택하고 다섯 번째 주제 "이용권 오등록 정정(등록 취소)"을 추가하면서,
5개 영역 전부에 대한 기본 입장을 일괄 제시 — "특별한 반대 근거 없으면 이대로 확정" 지시.
Claude 검토 결과 기존 정책·제약(D-016, 이력 원칙 등)과 충돌 없음 → 그대로 확정 (D-055~D-059).

사용자 입장 요약:
1. **등록 규칙**: 시작일 과거 지정 가능(기본 오늘), 유효기간·회비 만료일은 시작일 기준,
   횟수 0.5 단위 자유 입력, 초기 부여도 이력(`INITIAL_GRANT` 신설) → D-055
2. **수동 가감**: 0.5 단위, 결과 음수 거부, 만료권 가감 허용 + 횟수권 유효기간 수정과 세트,
   기간제 제외 → D-056 (횟수권 유효기간 수정은 신규 요구사항 PASS-07로 공식화)
3. **기간 수정**: 날짜 직접 지정. 이력은 PassTransaction과 별도 방식 — 구체 방식은 Claude 제안 위임 → D-057
4. **본인 조회**: 만료·소진 포함 + 상태 구분, 이력은 이용권별 필터 + page/size → D-058
5. **오등록 정정**: 취소 상태 전환 + `REGISTRATION_CANCELED` 이력, 물리 삭제 금지,
   회원 화면 숨김·관리자 구분 표시 → D-059 (신규 요구사항 PASS-08로 공식화)

---

## 기간·유효기간 변경 이력 방식 (Claude 제안 → 사용자 선택)

| Option | Description | Selected |
|--------|-------------|----------|
| 전용 이력 테이블 (추천) | `pass_period_change` 신설: 이용권/변경 전·후 날짜/사유/주체/시각. PassTransaction과 같은 패턴, 조회 API 노출 용이 | ✓ |
| 범용 감사 로그 (Envers 등) | 엔티티 변경 자동 기록. Boot 4 호환 검증 필요, 사유·주체 커스터마이징·조회 가공 복잡 | |

**User's choice:** 전용 이력 테이블
**Notes:** 저녁반 기간 수정(PASS-04)과 횟수권 유효기간 수정(PASS-07)을 한 테이블로 커버.

## 등록 취소 시 원장 처리 (Claude 제안 → 사용자 선택)

| Option | Description | Selected |
|--------|-------------|----------|
| 잔여를 0으로 상쇄 (추천) | 취소 시 −잔여 PassTransaction(`REGISTRATION_CANCELED`)을 남겨 "잔여 = 이력 합계" 불변식을 취소된 이용권에도 유지 | ✓ |
| 수량 0 마커만 기록 | 취소 사실만 ±0 이력으로. 구현 단순하나 원장 불변식에 예외 발생 | |

**User's choice:** 잔여를 0으로 상쇄
**Notes:** 기간제(EVENING_MEMBERSHIP)는 수량 없이 상태 전환 + 이력만.

---

## Claude's Discretion

- Pass 3종 테이블/엔티티 모델링 (glossary 네이밍·D-016 내에서)
- 이용권 상태 표현 (취소는 저장 필수, 만료·소진은 계산 가능)
- 잔여 갱신 동시성 처리 수준 (Phase 4 재사용 감안, D-021)
- 신규 ErrorCode 구성 + error-codes.md 갱신
- 유효기간 수정 API의 형태 (별도 vs 기간 수정 통합)

## Deferred Ideas

- 원장 정합 자동 검증 배치 — Phase 5에서 고려
- 취소 철회(복원) 기능 — 필요성 확인 후 별도 논의
