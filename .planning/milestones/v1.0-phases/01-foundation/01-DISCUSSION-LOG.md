# Phase 1: 기반 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-07-30
**Phase:** 1-기반
**Areas discussed:** openapi.yaml 재생성 방식, Member/Admin 스키마 범위, 에러코드·type 설계, Branch 시드·브랜치/PR 운영

---

## 진행 방식

사용자가 4개 회색지대 모두에 대해 기본 입장을 일괄 제시하고 "특별한 반대 근거가 없으면 확정"을 요청. Claude가 각 입장을 검증한 뒤 1건(openapi 플러그인)에서 반대 근거를 발견해 재질문, 나머지 3건은 그대로 확정.

## openapi.yaml 재생성 방식

**사용자 초기 입장:** springdoc-openapi-gradle-plugin의 `generateOpenApiDocs` 실행 후 결과를 `docs/api/openapi.yaml`로 복사하는 gradle 태스크 하나. 로컬 docker-compose Postgres 전제. CI 스펙 검증은 M7.

**Claude 검증 결과 (반대 근거):** 플러그인 최신 1.9.0이 2024-06 이후 릴리스 없음(Boot 4 이전). 미해결 이슈 #169(BootRun 캐스트 오류, 2025-10), #166(configuration cache 비호환), #157(placeholder 해석 실패).

| Option | Description | Selected |
|--------|-------------|----------|
| 커스텀 태스크로 바로 (추천) | 플러그인이 하는 일(백그라운드 기동+HTTP 다운로드)을 직접 gradle 태스크로 구현. 미유지보수 플러그인 의존 제거 | ✓ |
| 플러그인 시도 → 실패 시 폴백 | plan 단계에서 1.9.0 실제 검증 후 실패 시 전환 | |
| 그래도 플러그인 고정 | 실패 시 재논의 | |

**User's choice:** 커스텀 태스크로 바로
**Notes:** "한 명령" 계약·로컬 Postgres 전제·M7 CI 검증 유예는 초기 입장 그대로 유지.

## Member/Admin 스키마 범위

**User's choice (일괄 제시, 반대 근거 없음 → 확정):** 최소 스키마. Phase 1은 확실한 정체성 컬럼만, 인증 관련 컬럼은 Phase 2에서 V+1 마이그레이션으로 추가.

## 에러코드·type 설계

**User's choice (일괄 제시, 반대 근거 없음 → 확정):** ProblemDetail 표준 필드 + 커스텀 `code` 필드(문자열 enum, 예: RESERVATION_FULL). FE 분기는 code로만. type URI는 형식만 갖춘 단순 값. 에러코드 레지스트리를 docs/ 문서로 계약 관리.

## Branch 시드·브랜치/PR 운영

**User's choice (일괄 제시, 반대 근거 없음 → 확정):** Branch 시드는 Flyway 시드 마이그레이션으로 송파점 1건 삽입 (지점 관리 API 스코프 밖). 작업 브랜치는 plan 단위 `feat/p1-xxx` → dev PR, 리뷰는 Claude Code 코드리뷰로 대체.
(최초 커맨드 인자에서는 phase 단위 브랜치였으나 논의 중 plan 단위로 구체화됨 — 최신 지시가 우선)

## Claude's Discretion

- 테이블 세부 컬럼·인덱스·제약 설계 (D-05 제약 내)
- 커스텀 gradle 태스크 내부 구현 (기동 대기·종료 처리)
- JPA 엔티티를 이번 phase에서 함께 만들지 여부

## Deferred Ideas

- CI에서의 openapi 스펙 검증 — M7(배포)에서 고려
- 지점 관리 API — v1 스코프 밖
