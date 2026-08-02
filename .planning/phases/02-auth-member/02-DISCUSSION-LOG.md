# Phase 2: 인증·회원 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-02
**Phase:** 2-인증·회원
**Areas discussed:** 카카오 OAuth 연동 방식, JWT 토큰 정책, 가입 거절·상태 전이, 회원 목록·검색 API 형태

---

## 진행 방식

4개 회색 영역을 모두 선택한 뒤, 사용자가 각 영역의 기본 입장을 일괄 제시하고
"특별한 반대 근거 없으면 이대로 확정"을 지시했다. Claude 검토 결과 4개 모두 반대 근거
없음 — 그대로 확정. D-033에 한 가지 보완(상태 게이트 인가의 DB 기준 검사)을 덧붙였다.

## 카카오 OAuth 연동 방식

| Option | Description | Selected |
|--------|-------------|----------|
| 인가 코드 방식 (BE 토큰 교환) | FE는 리다이렉트·인가 코드 전달만, BE가 카카오 토큰 교환·사용자 조회 후 자체 JWT 발급 | ✓ |
| FE SDK 토큰 전달 | FE가 카카오 액세스 토큰을 받아 BE에 전달, BE는 사용자 조회만 | |
| Spring Security OAuth2 Client 위임 | 리다이렉트 세션 기반 — STATELESS 원칙과 상충 | |

**User's choice:** 인가 코드 방식. client_secret은 서버 환경변수에만 존재.
**Notes:** FE와의 API 계약(openapi.yaml)이 단순해지고 카카오 의존이 BE 한 곳에 모인다.

## JWT 토큰 정책

| Option | Description | Selected |
|--------|-------------|----------|
| DB 저장 + 회전 | access 30분 / refresh 14일, refresh DB 저장·사용 시 회전, 로그아웃 = 삭제 | ✓ |
| 무상태 refresh | 서버 저장 없음 — 강제 만료 불가 | |

**User's choice:** access 30분, refresh 14일, DB 저장 + 회전. 상태가 ACTIVE 아니게 되면
refresh 무효화로 강제 로그아웃 가능해야 함.
**Notes:** Claude 보완 — refresh 무효화만으로는 기발급 access가 최대 30분 유효한 창이
남으므로, 상태 게이트 인가는 DB 현재 상태 기준으로 검사한다 (D-033 노트로 기록).

## 가입 거절·상태 전이

| Option | Description | Selected |
|--------|-------------|----------|
| INACTIVE + 사유 기록 | 별도 상태 추가 없이 INACTIVE 전환, 거절 사유 기록, 재로그인 시 안내 대상 식별 | ✓ |
| REJECTED 상태 추가 | 상태 5종으로 확장 — 모든 상태 분기 코드 복잡화 | |
| 거절 시 데이터 삭제 | 거절 이력 소실, 재로그인 시 신규 가입으로 오인 | |

**User's choice:** INACTIVE 전환 + 거절 사유 기록. 재신청은 관리자가 PENDING으로 되돌리는
운영 방식. 승인 취소도 기존 상태 변경으로 갈음. policies.md에 전이 규칙 추가 지시.
**Notes:** policies.md §5.2 신설, D-034 기록.

## 회원 목록·검색 API 형태

| Option | Description | Selected |
|--------|-------------|----------|
| page/size + 통합 검색 + 상태 필터 | 검색어 하나로 이름·전화번호 부분 일치, 승인 대기 목록도 동일 API 재사용 | ✓ |
| 전체 반환 (페이지네이션 없음) | 소규모라 가능하나 회원 증가 시 계약 변경 필요 | |
| 승인 대기 전용 API 분리 | 목록 API 중복 | |

**User's choice:** page/size 페이지네이션, 통합 검색어(이름·전화번호 부분 일치), 상태 필터.
승인 대기 목록 재사용.
**Notes:** 승인 목록 정책(§5.1 — 온보딩 완료 PENDING만)을 지키기 위해 온보딩 완료 필터를
함께 둔다 (D-035).

## Claude's Discretion

- redirect_uri·state 처리, 카카오 API 호출 구현 세부
- V3+ 스키마 세부(kakao_id, admin 자격, refresh_token 테이블, 거절 사유 컬럼)
- 토큰 재사용 감지 여부, 멀티 디바이스 허용 여부 (권장: 허용)
- 관리자 시드 비밀번호 주입 방식 (실값 커밋 금지 원칙 고정)
- created_at 감사 시각 전략 (Phase 1 이월)
- 인가 구현 방식 (URL vs 메서드 시큐리티)

## Deferred Ideas

- 카카오 자동 수집(KAKAO-01), 프로필 셀프 수정(PROF-01) — 기존 v2 후보 유지, 신규 이연 없음
