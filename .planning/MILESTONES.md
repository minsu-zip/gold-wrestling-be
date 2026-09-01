# Milestones: gold-wrestling-be

## v1.0 — 골드레슬링 회원·예약 시스템 BE (M1~M6)

**Shipped:** 2026-09-01 (dev 머지 기준 — dev→main 배포는 별도 운영 절차)
**Phases:** 1-6 · **Plans:** 71 (+ quick task 4건) · **Requirements:** 44/44
**Timeline:** 2026-07-27 → 2026-09-01 (36일) · **Commits:** 548
**Scale:** 프로덕션 12,113 LOC + 테스트 27,178 LOC (Kotlin) · Flyway V1~V12 · openapi 41경로
**Quality:** 테스트 883건 0 failures · 전 phase VERIFICATION passed · 실기동 human-verify 승인

**Delivered:**

1. **인증·회원** — 카카오 OAuth(인증 수단 한정) + JWT 회전·재사용 감지, 온보딩(실명·전화번호)과 관리자 승인/거절, 역할 기반 default-deny 인가
2. **이용권 원장** — 3종 등록·수동 가감·기간 수정·등록 취소 전부가 append-only `PassTransaction`/`PassPeriodChange` 이력으로 남고, "잔여 = 이력 합계" 불변식을 실제 PostgreSQL로 실증
3. **예약** — 즉시 차감/복구, 정원·1:1 슬롯 동시 경쟁에서 초과 예약 0건(DB 부분 유니크 + 조건부 UPDATE), 관리자 대리 취소/변경·휴강 캐스케이드
4. **미사용 차감 배치** — 기준일 5종 max, 상태 기반 부족분 계산 = 멱등·캐치업 한 메커니즘, `RUNNING` 부분 유니크로 동시 실행 차단, 시행일 하한·실행 상한으로 소급 차감 봉쇄
5. **운영** — 출석 체크(예약제 upsert / 저녁반 0.5회 차감 한 트랜잭션), 공지 CRUD, 알림 폴링 + 활동 피드
6. **v1 마감** — 이월 정책 결정 3건(D-146 보강 불허·D-147 INACTIVE 차감 예외·D-151 cron 방침) + FE 계약 요청 3건(D-148~D-150) 처리 (PR #27)

**Known deferred items at close: 6 (see STATE.md Deferred Items)**
— v1.1 백로그 3건(BE-REQ-001·002·006) + 운영 결정·관찰 3건(cron 활성화 / CR-03 운영 대조 / WR-02 Info).
audit-open의 uat_gaps 1건은 status passed·미해결 0건인 문서가 카운트된 스캐너 동작으로 확인, 인정 처리.

**Archives:** [v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md) · [v1.0-REQUIREMENTS.md](milestones/v1.0-REQUIREMENTS.md)
