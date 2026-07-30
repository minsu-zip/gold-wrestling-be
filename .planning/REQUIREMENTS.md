# Requirements: gold-wrestling-be

**Defined:** 2026-07-30
**Core Value:** 회원이 보는 잔여 횟수는 항상 실제 사용 가능 횟수와 일치한다 (즉시 차감/복구 + 전 이력 + 초과 예약 0건)

> 기능 스펙 원본은 `docs/requirements.md`, 도메인 규칙은 `docs/policies.md` (충돌 시 그쪽이 이긴다).
> 이 문서는 그 스펙을 로드맵 추적 단위(REQ-ID)로 분해한 것이다. 마일스톤 구분은 사용자 지정 M1~M6.

## v1 Requirements

### 기반 (M1) — FOUND

- [x] **FOUND-01**: 모든 에러 응답이 RFC 9457 ProblemDetail(`application/problem+json`)로 반환된다 — 전역 예외 핸들러, 스프링 내장 에러(400/404/405) 포함 (D-017)
- [x] **FOUND-02**: Flyway 초기 스키마 — `Branch`, `Member`, `Admin`, `AdminBranch`(다대다 매핑) 테이블. 모든 핵심 엔티티에 `branch_id` 확장 전제 반영
- [x] **FOUND-03**: API 변경 시 `docs/api/openapi.yaml`을 재생성·커밋하는 파이프라인이 한 명령으로 동작한다 (springdoc 기반, FE 타입 생성의 원천)

### 인증·회원 (M2) — AUTH / MEMBER

- [ ] **AUTH-01**: 회원이 카카오 OAuth로 가입·로그인할 수 있다 (가입 직후 상태 `PENDING`) — 카카오는 인증 수단으로만 사용, 기본 제공 정보만 수신 (policies §5.1)
- [ ] **AUTH-02**: 로그인 시 JWT access/refresh 토큰이 발급되고, refresh로 access를 갱신할 수 있다
- [ ] **AUTH-03**: 관리자가 ID/PW로 로그인해 관리자 기능에 접근할 수 있다 — 카카오 연동 없음, 회원과 동일한 JWT 체계, 계정은 시드 데이터로 생성 (D-026)
- [ ] **AUTH-04**: 역할(MEMBER/ADMIN)·상태 기반 인가 — `PENDING` 회원은 승인 대기 정보 외 기능 접근 불가
- [ ] **AUTH-05**: 최초 카카오 로그인 후 온보딩으로 실명·전화번호를 등록할 수 있다 (필수, 전화번호 형식 검증) — 온보딩 완료 전에는 승인 대기 정보 외 기능 접근 불가 (policies §5.1)
- [ ] **AUTH-06**: 온보딩 미완료 회원이 재로그인하면 온보딩 대상으로 식별된다
- [ ] **MEMBER-01**: 관리자가 가입을 승인/거절할 수 있다 — 승인 목록에는 온보딩 완료된 `PENDING` 회원만 노출 (승인 시 `ACTIVE` 전환)
- [ ] **MEMBER-02**: 관리자가 회원 목록·상세를 조회하고 이름·전화번호로 검색할 수 있다
- [ ] **MEMBER-03**: 관리자가 회원 상태를 변경할 수 있다 (`ACTIVE`/`ON_LEAVE`/`INACTIVE`)
- [ ] **MEMBER-04**: 회원이 본인 프로필(이름·전화번호)을 조회할 수 있다 — 수정은 MVP에서 관리자만 (셀프 수정은 v2 PROF-01)

### 이용권 (M3) — PASS

- [ ] **PASS-01**: 관리자가 회원에게 이용권을 등록할 수 있다 — `EVENING_MEMBERSHIP`(1/3/6개월), `SESSION_PASS`/`LESSON_PASS`(횟수 지정, 유효기간 등록일+1년)
- [ ] **PASS-02**: 모든 차감/복구가 `PassTransaction` 이력으로 남는다 — (이용권, ±수량, 사유 코드, 주체, 시각). 이력 없는 잔여 변경 불가
- [ ] **PASS-03**: 관리자가 횟수를 수동 가감할 수 있다 — 사유 입력 필수(`ADMIN_ADJUST`)
- [ ] **PASS-04**: 관리자가 저녁반 회비 기간을 수정/연장할 수 있다
- [ ] **PASS-05**: 회원이 본인 이용권 목록·잔여 횟수·유효기간을 조회할 수 있다
- [ ] **PASS-06**: 회원이 본인 차감/복구 이력을 조회할 수 있다 (언제, 무슨 사유로 몇 회)

### 시간표·예약 (M4) — SCHED / RESV

- [ ] **SCHED-01**: 주간 반복 시간표(`ClassSchedule`)가 정의되고 조회된다 — 송파점 시간표 (policies §2, 90분 타임)
- [ ] **SCHED-02**: 특정 날짜의 수업(`ClassSession`)이 실체화되고, 해당 주 월요일에 그 주(월~일) 예약이 열린다 — 다음 주 예약 불가
- [ ] **SCHED-03**: 관리자가 주간 스케줄 보드 데이터를 조회할 수 있다 — 요일×타임 그리드: 수업 종류, 예약 n/정원, 1:1 여부, 셀별 예약자 명단
- [ ] **RESV-01**: 회원이 예약제 수업(SESSION)을 예약하면 `SESSION_PASS`에서 즉시 1회 차감된다 (`RESERVE`)
- [ ] **RESV-02**: 회원이 1:1 레슨(LESSON)을 예약하면 `LESSON_PASS`에서 즉시 1회 차감된다 — 타임당 1명
- [ ] **RESV-03**: 잔여 횟수가 차감량보다 적으면 예약이 거부된다 (잔여 0.5회로 1회 예약 불가)
- [ ] **RESV-04**: 회원이 예약을 취소(즉시 복구 `CANCEL_REFUND`)·변경(취소+재예약)할 수 있다 — 당일에는 둘 다 불가
- [ ] **RESV-05**: 회원이 본인 예약 목록을 조회할 수 있다
- [ ] **RESV-06**: 정원 마지막 자리·1:1 슬롯 동시 예약에서 초과 예약 0건 — DB 제약 + 조건부 갱신/락(D-021), 동시성 테스트 포함
- [ ] **RESV-07**: 관리자가 모든 회원의 예약을 조회할 수 있다
- [ ] **RESV-08**: 관리자가 예약을 대리 취소/변경할 수 있다 — 당일 포함 제약 없음, 취소 시 차감 복구 여부 선택(기본 복구)
- [ ] **RESV-09**: 관리자가 특정 날짜의 수업을 휴강 처리하면 예약 전부 자동 취소 + 차감 복구(`CLASS_CANCELED_REFUND`) + 알림 생성, 해당 타임은 예약 불가로 표시된다

### 배치 (M5) — BATCH

- [ ] **BATCH-01**: `SESSION_PASS` 2주 미사용 시 1회 자동 차감, 이후 2주마다 반복(`INACTIVITY`) — 기준일: 마지막 출석일과 마지막 예약의 수업일 중 더 최근 날짜, 둘 다 없으면 등록일 (D-027)
- [ ] **BATCH-02**: 차감 예외가 지켜진다 — `ON_LEAVE` 기간, 잔여 0, 유효기간 만료 이용권은 차감하지 않음
- [ ] **BATCH-03**: 유효기간(등록일+1년) 만료 이용권이 사용 불가 처리된다
- [ ] **BATCH-04**: 배치는 멱등하다 — 같은 날 중복 실행돼도 이중 차감 0건 (매일 새벽 실행)

### 운영 (M6) — ATTEND / NOTICE / NOTIF

- [ ] **ATTEND-01**: 관리자가 모든 수업(저녁반/예약제/1:1)의 타임별 출석을 체크할 수 있다 — 차감과 무관한 참고 데이터 (policies §6)
- [ ] **ATTEND-02**: 관리자가 횟수권 회원의 저녁반 참여를 0.5회 수동 차감할 수 있다 (`EVENING_HALF`) — 잔여 0.5 이상일 때만
- [ ] **NOTICE-01**: 관리자가 공지사항을 등록/수정/삭제할 수 있다
- [ ] **NOTICE-02**: 회원이 공지 목록·상세를 열람할 수 있다
- [ ] **NOTIF-01**: 예약 생성/변경/취소·휴강 시 관리자 알림이 생성된다 — Notification 스키마·레코드 생성은 Phase 4에서 구현 (조회·확인·폴링·피드는 Phase 6)
- [ ] **NOTIF-02**: 관리자가 알림 목록을 폴링(30초)으로 조회하고 확인 처리할 수 있다 — 미확인 카운트 제공
- [ ] **NOTIF-03**: 관리자가 최근 활동 피드(예약 이벤트 타임라인)를 조회할 수 있다 — 알림과 동일 데이터의 다른 뷰

## v2 Requirements

향후 후보. 현재 로드맵에 없음 (`docs/requirements.md` §6).

- **KAKAO-01**: 비즈앱 전환·동의항목 심사 통과 후 이름·전화번호를 카카오에서 자동 수집해 온보딩 폼을 자동 채움 (D-025)
- **PROF-01**: 회원이 본인 프로필(이름·전화번호)을 직접 수정 — MVP는 관리자만 수정 가능
- **CROSS-01**: 지점 간 연동 — 타 지점 횟수권 예약, 관리자 교차 권한 (스키마의 `branch_id`·`AdminBranch`가 준비 지점)
- **PAY-01**: 온라인 결제(PG)
- **PUSH-01**: 웹 푸시 알림 (PWA + FCM)
- **WAIT-01**: 정원 초과 대기(waitlist)
- **TALK-01**: 카카오 알림톡
- **SSE-01**: 알림 폴링 → SSE 업그레이드

## Out of Scope

| Feature | Reason |
|---------|--------|
| 프론트엔드 | 별도 레포 `gold-wrestling-fe` (D-003). BE는 openapi.yaml 계약만 제공 |
| 배포 파이프라인 (GitHub Actions → EC2) | 사용자 지정 — 이번 M1~M6 로드맵에서 제외, 별도 작업 |
| 회원 셀프 탈퇴/휴회 신청 | 스펙에 없음 — 상태 변경은 관리자만 (requirements.md §4.1) |
| 노쇼 페널티 | 즉시 차감 정책으로 자동 해결 — 출석 기록에 불참만 남김 (policies §3) |

## 완료 조건 공통 (모든 마일스톤)

- API 변경 시 `docs/api/openapi.yaml` 재생성·커밋 완료
- 도메인 로직 단위 테스트 + DB 로직 Testcontainers 통합 테스트 (conventions.md §10.0 표 기준)
- M4는 동시성 테스트(정원 경쟁·1:1 슬롯) 필수
- `./gradlew ktlintFormat` → `./gradlew build` 통과

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| FOUND-01 | Phase 1 | Complete |
| FOUND-02 | Phase 1 | Complete |
| FOUND-03 | Phase 1 | Complete |
| AUTH-01 | Phase 2 | Pending |
| AUTH-02 | Phase 2 | Pending |
| AUTH-03 | Phase 2 | Pending |
| AUTH-04 | Phase 2 | Pending |
| AUTH-05 | Phase 2 | Pending |
| AUTH-06 | Phase 2 | Pending |
| MEMBER-01 | Phase 2 | Pending |
| MEMBER-02 | Phase 2 | Pending |
| MEMBER-03 | Phase 2 | Pending |
| MEMBER-04 | Phase 2 | Pending |
| PASS-01 | Phase 3 | Pending |
| PASS-02 | Phase 3 | Pending |
| PASS-03 | Phase 3 | Pending |
| PASS-04 | Phase 3 | Pending |
| PASS-05 | Phase 3 | Pending |
| PASS-06 | Phase 3 | Pending |
| SCHED-01 | Phase 4 | Pending |
| SCHED-02 | Phase 4 | Pending |
| SCHED-03 | Phase 4 | Pending |
| RESV-01 | Phase 4 | Pending |
| RESV-02 | Phase 4 | Pending |
| RESV-03 | Phase 4 | Pending |
| RESV-04 | Phase 4 | Pending |
| RESV-05 | Phase 4 | Pending |
| RESV-06 | Phase 4 | Pending |
| RESV-07 | Phase 4 | Pending |
| RESV-08 | Phase 4 | Pending |
| RESV-09 | Phase 4 | Pending |
| NOTIF-01 | Phase 4 | Pending |
| BATCH-01 | Phase 5 | Pending |
| BATCH-02 | Phase 5 | Pending |
| BATCH-03 | Phase 5 | Pending |
| BATCH-04 | Phase 5 | Pending |
| ATTEND-01 | Phase 6 | Pending |
| ATTEND-02 | Phase 6 | Pending |
| NOTICE-01 | Phase 6 | Pending |
| NOTICE-02 | Phase 6 | Pending |
| NOTIF-02 | Phase 6 | Pending |
| NOTIF-03 | Phase 6 | Pending |

**Coverage:**
- v1 requirements: 42 total
- Mapped to phases: 42 ✓
- Unmapped: 0 ✓

> 참고: 최초 작성 시 이 섹션 상단에 있던 "36 total" 표기는 실제 위 v1 목록(FOUND~NOTIF, 42건)과 맞지 않는 집계 오류였다. 로드맵 작성 시 실제 목록 기준으로 42건 전부를 매핑해 정정했다.

---
*Requirements defined: 2026-07-30*
*Last updated: 2026-07-30 after roadmap creation (Phase 1~6 매핑)*
