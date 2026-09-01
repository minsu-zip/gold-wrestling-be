# Roadmap: gold-wrestling-be

## Milestones

- ✅ **v1.0 M1~M6** — Phases 1-6 (shipped 2026-09-01) · [아카이브](milestones/v1.0-ROADMAP.md) · [MILESTONES.md](MILESTONES.md)
- 📋 **v1.1** — 미정 (`/gsd:new-milestone`으로 시작. 후보는 아래 Backlog와 STATE.md Deferred Items)

## Phases

<details>
<summary>✅ v1.0 M1~M6 (Phases 1-6) — SHIPPED 2026-09-01</summary>

- [x] Phase 1: 기반 (3/3 plans) — ProblemDetail·초기 스키마·openapi 파이프라인 (completed 2026-07-30)
- [x] Phase 2: 인증·회원 (15/15 plans) — 카카오 로그인·온보딩·JWT·승인·회원 관리 (completed 2026-08-03)
- [x] Phase 3: 이용권 (11/11 plans) — Pass 3종·PassTransaction 원장·가감·기간·취소 (completed 2026-08-04)
- [x] Phase 4: 시간표·예약 (15/15 plans) — 즉시 차감/복구·동시성 보장·관리자 운영·휴강 (completed 2026-08-08)
- [x] Phase 5: 배치 (16/16 plans) — 2주 미사용 차감·멱등·갭 클로저(청크 D) (completed 2026-08-16)
- [x] Phase 6: 운영 (11/11 plans) — 출석·저녁반 0.5회 차감·공지·알림·활동 피드 (completed 2026-08-19)

phase 상세(Goal·Success Criteria·플랜 목록·충족 근거)는 [아카이브](milestones/v1.0-ROADMAP.md) 참조.
마감 후 quick task로 v1 잔여 결정·계약 요청을 처리했다(PR #27, D-146~D-151).

</details>

## Backlog (v1.1 후보)

로드맵 확정 전 후보 목록이다. 정식 요구사항 정의는 `/gsd:new-milestone`에서 한다.

- **FE 계약 요청 잔여 3건** — BE-REQ-001(4xx/5xx ProblemDetail 스키마 선언), BE-REQ-002(`GET /api/admin/me`), BE-REQ-006(관리자 예약 단건 조회). 상세·FE 우회 현황은 STATE.md Deferred Items와 `../gold-wrestling-fe/.planning/BE-CHANGE-REQUESTS.md`
- **배포 파이프라인 정식화** — v1.0 범위 밖이었던 GitHub Actions → EC2 정비 (dev→main 배포 = 운영 반영)
- **cron 활성화 운영** — D-151 방침에 따른 배포 후 활성화는 로드맵 항목이 아니라 운영 절차(README·D-130)
- v2 후보(REQUIREMENTS 아카이브 §v2): KAKAO-01 자동수집, PROF-01 셀프 프로필 수정, CROSS-01 지점 간 연동, PAY-01 결제, PUSH-01 웹 푸시, WAIT-01 대기열, TALK-01 알림톡, SSE-01

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. 기반 | v1.0 | 3/3 | Complete | 2026-07-30 |
| 2. 인증·회원 | v1.0 | 15/15 | Complete | 2026-08-03 |
| 3. 이용권 | v1.0 | 11/11 | Complete | 2026-08-04 |
| 4. 시간표·예약 | v1.0 | 15/15 | Complete | 2026-08-08 |
| 5. 배치 | v1.0 | 16/16 | Complete | 2026-08-16 |
| 6. 운영 | v1.0 | 11/11 | Complete | 2026-08-19 |
