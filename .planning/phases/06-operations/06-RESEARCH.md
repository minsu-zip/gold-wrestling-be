# Phase 6: 운영 - Research

**Researched:** 2026-08-18
**Domain:** Kotlin + Spring Boot 4.1.x(Spring Framework 7) + JPA + PostgreSQL 백엔드 — 출석 체크, 저녁반 수동 차감, 공지 CRUD, 알림 폴링/활동 피드, 배치 기준일 배선
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**논의 방식**: 사용자가 4개 영역 + 공지 재량안에 대한 입장을 선제 제시했고, 코드·정책 대조로
반대 근거가 없는 것은 그대로 확정, 빈칸 2건(차감 불가 시 처리·복구 사유 코드)은 보완 질문으로
확정했다. 아래 결정은 `docs/decisions.md` **D-127~D-131**로 기록 완료, 소급 규칙은
`docs/policies.md` §6에 반영 완료 — **downstream 에이전트는 policies §6 확정 문구를 원본으로 삼는다.**

**1. 출석 체크 모델 (D-127)**
- 출석 상태는 **레코드 부재 = 미체크**, 레코드는 `ATTENDED`/`ABSENT` 2값
- **예약제/1:1**: 예약자 명단을 프리로드해 출석/불참을 체크 (예약했지만 불참 = 차감 유지 + `ABSENT`)
- **저녁반**: 빈 명단에서 관리자가 회원 검색으로 추가, **추가됨 = 출석**(`ATTENDED`만, 불참 없음)
- **회원×세션 유니크 제약** — 출석 1건과 (저녁반의) 차감 1건을 DB 수준에서 함께 고정
- **소급 입력·수정 허용**. 단, 소급 출석이 이미 실행된 `INACTIVITY` 차감을 되돌리지 않는다 —
  기준일 반영은 다음 배치 실행부터. 코드도 이미 이 방향(`deficit.coerceAtLeast(0)`,
  `InactivityDueDateCalculator.kt:109`)이며, 정정이 필요하면 관리자가 `ADMIN_ADJUST`로 수동 복구
- 미사용 차감 기준일 후보 ①(마지막 출석일)은 **`ATTENDED`만 인정** (불참은 미사용)

**2. 저녁반 0.5회 차감 (D-128)**
- **저녁반 출석 추가와 0.5회 차감은 한 트랜잭션** — 서버가 판정한다:
  - 유효한 저녁반 회비(`EVENING_MEMBERSHIP`) 보유 시 **차감 없음** (회비 우선)
  - 없으면 `SESSION_PASS`에서 0.5 차감 — 차감 대상 선택은 **D-091(만료 임박순) 재사용**,
    유효기간 판정 기준일은 오늘이 아니라 **수업날** (소급 입력 시 그날 유효했는지로 판정)
  - 둘 다 불가(회비 없음 + `SESSION_PASS` 잔여 0.5 미만)면 **출석 추가 자체를 409로 거부** —
    관리자가 `ADMIN_ADJUST`로 충전 후 재시도
- 이중 차감은 회원×세션 유니크로 구조적 방지, 차감 이력은 출석 레코드에 연결(FK)
- **저녁반 출석 삭제 시 연결된 차감을 자동 복구** — 사유는 **`EVENING_HALF_REFUND` 신설**
  (예약 복구가 `CANCEL_REFUND`·`CLASS_CANCELED_REFUND`로 구분되는 선례와 일관.
  `pass_transaction.reason`은 VARCHAR(30)에 DB CHECK 없음 — enum 상수 추가만으로 확장 가능)

**3. 알림 폴링·활동 피드 (D-129)**
- 확인 처리는 **"모두 읽음"만** — 개별 읽음 없음. `Notification.isRead/readAt`(이미 `var`) 벌크 UPDATE
- **미확인 카운트는 알림 목록 응답에 포함** — 별도 카운트 엔드포인트 없음
- 활동 피드는 **알림과 동일 데이터(notification 테이블)의 다른 뷰** — 읽음 여부 무관 시간순,
  필터는 **기간 + 종류**, 페이지네이션은 기존 page/size(`PageResponse`) 형태 재사용

**4. CR-03 마감·cron 재활성 (D-130)**
- `InactivityBatchRunner.kt:157`의 `lastAttendanceDate = null`을 회원별 "마지막 `ATTENDED`
  수업일" 벌크 조회로 교체 — 기준일 5종 max 완결. calculator는 수정 불필요(이미 5종 수용)
- cron 기본값은 **D-121(기본 꺼짐) 유지** — 코드로 되돌리지 않는다
- **운영 켜기 절차를 문서화**: ① 출석 배선 배포 확인 → ② 운영에서 수동 실행 1회 검증 →
  ③ `BATCH_INACTIVITY_SCHEDULER_ENABLED=true` 환경변수 명시.
  절차에 **정책 시행일 하한(기본 2026-09-01, D-119)** 명시 — 시행일 전 수동 실행은 차감 0건이 정상

**5. 공지사항 (D-131, 재량 확정)**
- 관리자 CRUD + 회원 목록·상세. **제목+본문만** (첨부·고정 없음, MVP)
- 목록은 기존 page/size(`PageResponse`) 재사용, 삭제는 **hard delete**
  (이력 요구 없음 — `PassTransaction` 같은 원장 성격이 아님)

**이 phase가 만들지 않는 것:**
- 회원용 알림(수신자는 관리자로 고정 — D-097, Phase 4에서 확정)
- 알림 실시간 푸시(WebSocket/SSE) — 30초 폴링으로 충족
- 공지 첨부파일·고정(핀)·노출 예약 — MVP 밖
- cron 기본값 변경 — D-121(기본 꺼짐) 유지, 켜는 것은 운영 배포 절차의 일

### Claude's Discretion
- Attendance/Notice 스키마 상세(컬럼·인덱스 설계), API 경로·DTO 형태 — 기존 관례
  (add-migration·add-endpoint 스킬, conventions.md) 안에서 플래너 재량
- 출석 체크 API의 배치 처리 형태(타임별 일괄 저장 vs 개별 토글) — FE 사용성 기준 재량
- 공지 정렬(최신순 기본) 등 세부 조회 규칙

### Deferred Ideas (OUT OF SCOPE)
- **회원용 알림** — 수신자 확장은 요구사항에 없음 (D-097 유지). 필요해지면 새 phase
- **알림 실시간 푸시(WebSocket/SSE)** — 30초 폴링으로 충족, M7 이후 재검토
- **공지 첨부·고정(핀)** — MVP 밖, 운영 피드백 후 판단
- **오래된 알림 보존 기간·아카이빙** — append-only 테이블 증가 대응은 운영 데이터가 쌓인 뒤 판단

### 문서 정합 (06-01 정합 플랜에서 처리할 것 — CONTEXT.md 원문)
- glossary: `EVENING_HALF_REFUND`, 출석 상태(`ATTENDED`/`ABSENT`), 활동 피드 용어 추가
- policies §4.2: 저녁반 출석 추가 = 차감 원자 결합·회비 우선·거부 규칙 반영 (§6은 반영 완료)
- error-codes.md: 출석 관련 신규 에러 코드(차감 불가 409 등) 등록
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ATTEND-01 | 관리자가 모든 수업(저녁반/예약제/1:1)의 타임별 출석을 체크할 수 있다 — 참고용 데이터 | `Attendance` 엔티티 설계(회원×세션 유니크, `ATTENDED`/`ABSENT`), 예약제/1:1 프리로드는 `ReservationRepository.findAllByClassSessionIdAndStatusWithPass` 계열 재사용, 저녁반은 `MemberSpecifications.keywordContains` 재사용 회원 검색 → 추가 |
| ATTEND-02 | 관리자가 `SESSION_PASS` 보유 회원의 저녁반 참여를 0.5회 수동 차감할 수 있다(`EVENING_HALF`) | D-128 판정 트랜잭션 설계, `ReservationPassPolicy`/`PassRepository.findDeductionCandidates`(만료 임박순, D-091) 재사용, `EVENING_MEMBERSHIP` 유효 여부 조회 신규 쿼리 필요, `PassRepository.adjustRemainingCount` 조건부 UPDATE 재사용 |
| NOTICE-01 | 관리자가 공지사항을 등록/수정/삭제할 수 있다 | 신규 `notice` 패키지, hard delete(D-131), `add-endpoint`/`add-migration` 표준 절차 |
| NOTICE-02 | 회원이 공지 목록·상세를 열람할 수 있다 | `PageResponse` 재사용 목록 조회, 상세는 단건 GET |
| NOTIF-02 | 관리자가 알림 목록을 30초 폴링으로 조회하고 확인 처리(모두 읽음)할 수 있다 — 미확인 카운트 포함 | 기존 `Notification` 엔티티·`idx_notification_unread` 인덱스 재사용, 벌크 UPDATE 패턴은 `PassRepository.adjustRemainingCount` KDoc의 flush/clear 관례 재사용 |
| NOTIF-03 | 관리자가 최근 활동 피드(예약 이벤트 타임라인)를 조회할 수 있다 | 같은 `notification` 테이블의 다른 쿼리 뷰(D-129), `PageResponse` + 기간·종류 필터(Specification 패턴) |
| (이월 CR-03) | `InactivityBatchRunner`의 `lastAttendanceDate = null`을 실제 조회로 교체 | `Attendance` 저장 후 `findMemberIdsWithDeductibleSessionPass`/`findLastActiveReservationClassDates`와 동일한 `MemberDateProjection` 벌크 조회 패턴 재사용 — `ATTENDED`만 필터 |
</phase_requirements>

## Summary

Phase 6은 **새 외부 라이브러리를 도입하지 않는다.** Boot 4.1.0(Spring Framework 7) + JPA + PostgreSQL 18 +
springdoc 3.0.3 스택 그대로, Phase 3~5가 이미 확립한 "조건부 UPDATE + 원장 원자 기록"·"Specification
동적 조건"·"벌크 프로젝션 조회" 패턴을 두 신규 기능 패키지(`attendance`, `notice`)와 기존
`notification` 패키지의 조회 계층에 그대로 적용하는 phase다. `conventions.md` §1의 패키지 레이아웃
표에는 이미 `attendance/`·`notice/`가 명시돼 있어(Phase 3 시점부터 이 phase를 전제로 설계됨),
새로운 아키텍처 결정이 필요하지 않다.

가장 위험한 지점은 ATTEND-02(저녁반 0.5회 차감)다 — "출석 추가"라는 하나의 API 호출이 서버 내부에서
**회비 우선 판정 → 없으면 차감 대상 선택(D-091 재사용) → 조건부 차감 → 출석 저장 → 실패 시 409**의
분기를 전부 한 트랜잭션에서 처리해야 한다. `ReservationLedgerSupport.createReservation`이 정확히
같은 모양(정원 조건부 UPDATE → 차감 후보 선정 → 조건부 차감 → INSERT → 원장 기록)의 선례이므로,
이 phase는 새 패턴을 발명하지 않고 그 구조를 `EVENING_MEMBERSHIP` 유효성 판정 한 단계만 앞에 끼워
재사용하면 된다. 두 번째 위험 지점은 CR-03 배선이다 — `InactivityDueDateCalculator`는 이미 5종
후보를 받는 시그니처를 갖추고 있으므로(Phase 5가 의도적으로 미리 설계) 이 phase는 `null` 하드코딩
한 줄을 벌크 조회로 바꾸는 것 이상을 건드릴 필요가 없다. 여기서 후보 값의 필터 조건(`ATTENDED`만
인정, 소급 출석이 과거 `INACTIVITY` 이력을 되돌리지 않음)을 잘못 짜면 D-106의 멱등성 불변식이
깨진다는 점이 유일한 함정이다.

**Primary recommendation:** `ReservationLedgerSupport`·`InactivityBatchRunner`가 확립한 "조건부
UPDATE + 원장 원자 기록 + 실패 시 도메인 예외" 패턴을 그대로 `attendance` 패키지에 이식하고,
`notification`·`notice`·`Specification` 계열은 `ReservationSpecifications`/`PassTransactionSpecifications`의
필터 조합 관례를 그대로 따른다. 새 아키텍처를 설계하지 말 것.

## Architectural Responsibility Map

이 프로젝트는 BE 단일 계층(Kotlin/Spring Boot 모놀리스, FE는 별도 레포)이라 "Browser/CDN" 등의 다중
계층 구분이 없다. 계층은 **API/Backend**(컨트롤러-서비스-리포지토리)와 **Database/Storage**(PostgreSQL,
제약·인덱스) 둘뿐이다.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| 출석 체크(ATTEND-01, 명단 프리로드·체크·소급 수정) | API/Backend | Database/Storage | 판정 로직(예약제 vs 저녁반 분기)은 서비스가 담당, 회원×세션 유니크는 DB 제약이 최종 방어선(D-021 원칙) |
| 저녁반 0.5회 차감(ATTEND-02, 회비 우선·차감·거부·복구) | API/Backend | Database/Storage | 판정 순서(회비→SESSION_PASS→거부)는 서비스, 이중 차감 방지는 DB 유니크 제약 + 조건부 UPDATE |
| 공지 CRUD·열람(NOTICE-01/02) | API/Backend | Database/Storage | 단순 CRUD, DB는 저장·조회만 |
| 알림 폴링·확인(NOTIF-02) | API/Backend | Database/Storage | 벌크 "모두 읽음" UPDATE + 미확인 카운트 조회, `idx_notification_unread` 인덱스가 조회 성능 담당 |
| 활동 피드(NOTIF-03) | API/Backend | Database/Storage | 같은 테이블의 다른 Specification 쿼리, 인덱스 재사용(추가 인덱스 필요 여부는 Pitfall 참고) |
| CR-03 기준일 배선(배치) | API/Backend | Database/Storage | 순수 계산은 이미 완성(`InactivityDueDateCalculator`), 이 phase는 벌크 조회 한 지점만 배선 |

## Standard Stack

### Core

이 phase는 새 의존성을 추가하지 않는다. 기존 `build.gradle.kts`에서 검증된 버전을 그대로 쓴다.

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.1.0 | 애플리케이션 프레임워크 | 프로젝트 고정 스택(D-005/D-014) `[VERIFIED: build.gradle.kts:5]` |
| Kotlin | 2.3.21 | 언어 | 프로젝트 고정 `[VERIFIED: build.gradle.kts:2]` |
| springdoc-openapi-starter-webmvc-ui | 3.0.3 | OpenAPI 스펙 생성(FE 계약) | Boot 4 대응 라인(2.x는 Boot 3 전용, D-014) `[VERIFIED: build.gradle.kts:26,47]` |
| PostgreSQL(런타임 드라이버) | org.postgresql:postgresql(관리형 버전) | 운영 DB | 프로젝트 고정 스택 `[VERIFIED: build.gradle.kts:49]` |
| Testcontainers | 2.x 계열(`testcontainers-postgresql` 아티팩트) | 통합테스트 DB | Boot 4/Testcontainers 2.x 모듈명 변경 반영됨(D-014 주석) `[VERIFIED: build.gradle.kts:61-64]` |

### Supporting

새 서포팅 라이브러리 불필요 — 기존 재사용 컴포넌트로 충분하다.

| Component | Package | Purpose | When to Use |
|-----------|---------|---------|-------------|
| `PageResponse<T>` | `member.dto` | 페이지네이션 응답 계약 | 공지 목록, 알림 목록, 활동 피드 전부 재사용 |
| `Specification` 동적 조건(`ReservationSpecifications`/`PassTransactionSpecifications` 관례) | 각 기능 패키지 | 필터 조합 | 활동 피드(기간+종류), 공지 목록(정렬) |
| `LikePatternEscaper`/`PhoneNumberNormalizer` | `common`/`member` | 회원 검색 이스케이프·정규화 | 저녁반 출석 추가 화면의 "회원 검색" |
| `MemberDateProjection`/`MemberTimestampProjection` | `common.projection` | 벌크 회원별 날짜/시각 조회 | CR-03의 "마지막 ATTENDED 수업일" 벌크 조회 |
| `Clock` 빈 | `config` | 시각 고정 주입 | 출석 소급 입력, 저녁반 차감 시각 기록 |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| notification 테이블 재사용(D-129) | 활동 피드 전용 별도 테이블 | 기각(D-129) — 이벤트 저장 경로가 둘로 갈라지면 알림·피드가 어긋날 수 있음. 데이터 볼륨이 소규모(단일 지점, 관리자 1~2명)라 별도 최적화 테이블의 이득이 없음 |
| 개별 읽음 처리 | 알림별 `isRead` 개별 토글 | 기각(D-129) — 요구사항에 근거 없고 상태 관리 비용만 증가 |
| soft delete 공지 | `deleted_at` 컬럼 | 기각(D-131) — 원장 성격이 아니라 이력 요구 없음, 조회 필터 비용만 추가 |

**Installation:** 불필요 — `build.gradle.kts` 변경 없음.

**Version verification:** `build.gradle.kts`를 직접 읽어 확인함(`npm view`/`pip` 류 레지스트리 조회 불필요 — Gradle 프로젝트이며 신규 의존성 추가가 없음).

## Package Legitimacy Audit

> 이 phase는 **신규 외부 패키지를 설치하지 않는다.** 기존 `build.gradle.kts` 의존성만 재사용하므로
> slopcheck·레지스트리 검증 게이트는 해당 없음(N/A) — 새 `implementation`/`testImplementation` 라인이
> 플랜에 추가된다면 그 시점에 이 게이트를 다시 수행해야 한다.

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| (해당 없음 — 신규 의존성 없음) | — | — | — | — | — | N/A |

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
[관리자 클라이언트]                         [회원 클라이언트]
      |                                          |
      | 1. GET /api/admin/class-sessions/{id}/attendance   (프리로드 명단)
      | 2. POST/PATCH .../attendance                        (체크/수정)
      v                                          |
+---------------------------+                    | GET /api/notices, /{id}
|  AdminAttendanceController |                    v
+---------------------------+           +--------------------+
      |                                 |  NoticeController   |
      v                                 +--------------------+
+---------------------------+                    |
|  AdminAttendanceService    |                    v
|  (판정: EVENING_MEMBERSHIP |          +--------------------+
|   유효? -> 없으면 D-091    |          |  NoticeService      |
|   차감 후보 선정 -> 조건부  |          +--------------------+
|   차감 -> Attendance INSERT|                    |
|   -> 실패시 409 거부)      |                    v
+---------------------------+          +--------------------+
      |          |                     |  NoticeRepository    |
      v          v                     +--------------------+
+-----------+ +----------------+
| Attendance | | PassRepository |
| Repository | | .adjustRemain- |
| (uq 회원×  | |  ingCount(조건 |
|  세션)     | |  부 UPDATE)    |
+-----------+ +----------------+
      |               |
      v               v
   attendance      pass_transaction
     테이블         (EVENING_HALF /
                    EVENING_HALF_REFUND)

[관리자 클라이언트] --30초 폴링--> GET /api/admin/notifications?unreadOnly=...
                                    -> NotificationController(신규)
                                    -> NotificationQueryService(신규)
                                    -> NotificationRepository(기존 엔티티, 신규 조회 메서드)
                                    -> notification 테이블 (기존, Phase 4가 write, Phase 6이 read)

[관리자 클라이언트] --확인 처리--> POST /api/admin/notifications/read-all
                                   -> 벌크 UPDATE isRead=true, readAt=now (조건부, flush/clear 관례)

[관리자 클라이언트] --활동 피드--> GET /api/admin/activity-feed?from=&to=&type=
                                   -> 같은 notification 테이블, Specification(기간+종류)+PageResponse

[cron 04:00 Asia/Seoul, 기본 꺼짐] -> InactivityBatchScheduler -> InactivityBatchRunner
     -> AttendanceRepository.findLastAttendedClassDates(memberIds)  <- CR-03 배선 지점
     -> InactivityDueDateCandidates(lastAttendanceDate = 실제값, ...)
     -> InactivityDueDateCalculator.resolveDueDate(...)  (수정 불필요, 이미 5종 수용)
```

### Recommended Project Structure

`conventions.md` §1의 패키지 레이아웃 표에 이미 `attendance/`·`notice/`가 명시돼 있다(Phase 3 시점부터
전제됨) — 새로 설계할 필요 없이 그대로 따른다.

```
src/main/kotlin/com/goldwrestling/
├── attendance/
│   ├── Attendance.kt                 # 엔티티 (member, classSession FK, status, passTransaction FK nullable)
│   ├── AttendanceStatus.kt           # ATTENDED / ABSENT
│   ├── AttendanceRepository.kt       # 회원×세션 유니크 upsert/조회, CR-03용 벌크 프로젝션
│   ├── AttendanceService.kt          # 예약제/1:1 체크, 저녁반 추가+차감 판정(EveningHalfDeductionPolicy 위임)
│   ├── EveningHalfDeductionPolicy.kt # D-128 판정 순수 로직 (ReservationPassPolicy와 자매 object)
│   ├── AdminAttendanceController.kt
│   ├── AttendanceExceptions.kt
│   └── dto/
│       ├── ClassSessionAttendanceRosterResponse.kt
│       ├── CheckAttendanceRequest.kt
│       └── AttendanceResponse.kt
├── notice/
│   ├── Notice.kt
│   ├── NoticeRepository.kt
│   ├── AdminNoticeController.kt      # CRUD
│   ├── MemberNoticeController.kt     # 목록·상세
│   ├── NoticeService.kt
│   ├── NoticeExceptions.kt
│   └── dto/
│       ├── CreateNoticeRequest.kt / UpdateNoticeRequest.kt
│       └── NoticeSummaryResponse.kt / NoticeDetailResponse.kt
└── notification/                     # 기존 패키지, 조회 계층만 확장
    ├── Notification.kt               # 변경 없음(Phase 4 완성)
    ├── NotificationType.kt           # 변경 없음
    ├── NotificationRepository.kt     # 신규: 폴링/피드/미확인카운트 쿼리 추가
    ├── AdminNotificationController.kt  # 신규
    ├── NotificationQueryService.kt     # 신규 (readonly 조회 + 모두읽음 커맨드)
    ├── NotificationSpecifications.kt   # 신규 (기간·종류 필터, 피드 전용)
    └── dto/
        ├── NotificationListResponse.kt   # items + unreadCount
        └── ActivityFeedItemResponse.kt
```

### Pattern 1: 예약제/1:1 출석 명단 프리로드는 기존 조인-페치 쿼리를 재사용한다

**What:** `AttendanceService`가 세션의 활성 예약자 명단을 N+1 없이 한 번에 가져와 출석 체크
후보로 뿌린다.
**When to use:** ATTEND-01의 예약제/1:1 프리로드 API.
**Example:**
```kotlin
// Source: 이 저장소 ReservationRepository.kt(기존 코드) — join fetch 관례 그대로 재사용
// 새 쿼리를 짤 필요 없이 기존 메서드를 그대로 호출하거나 동일 관례로 하나만 추가한다.
val activeReservations =
    reservationRepository.findAllByClassSessionIdAndStatusWithPass(sessionId, ReservationStatus.ACTIVE)
// 여기서 member까지 join fetch가 필요하면 findAllByClassSessionIdInAndStatusWithMember의
// 단일-id 버전을 하나 추가한다(N+1 방지 관례를 그대로 따른다).
```

### Pattern 2: 저녁반 출석 추가 = 회비 우선 판정 + 조건부 차감 + 원장 기록을 한 트랜잭션에

**What:** `ReservationLedgerSupport.createReservation`과 동일한 모양의 흐름 — "판정 → 조건부
UPDATE(0이면 예외) → INSERT → 원장 기록"을 그대로 이식하되, 앞에 "회비 우선 여부" 판정 한 단계를
추가한다.
**When to use:** ATTEND-02(D-128) 저녁반 출석 추가 API.
**Example:**
```kotlin
// Source: 이 저장소 ReservationLedgerSupport.createReservation(기존 코드)의 구조를 그대로 이식
// (실제 API가 아니라 이 phase가 따라야 할 "구조" 예시)
fun addEveningAttendance(memberId: Long, classSession: ClassSession, admin: Admin): Attendance {
    val classDate = classSession.classDate // 유효기간 판정은 수업날 기준(D-128)

    // ① 회비 우선 판정 — 수업날 기준 유효한 EVENING_MEMBERSHIP이 있는가
    val hasValidMembership =
        passRepository.existsActiveEveningMembership(memberId, classDate) // 신규 쿼리, PassRepository.findDeductionCandidates와 동일 관례

    val passTransaction: PassTransaction? =
        if (hasValidMembership) {
            null // 차감 없음(D-128)
        } else {
            // ② SESSION_PASS 후보 선정 — D-091(만료 임박순) 재사용, requiredAmount = HALF_SESSION
            val candidates = passRepository.findDeductionCandidates(memberId, PassType.SESSION_PASS, classDate, HALF_SESSION)
            val candidate = ReservationPassPolicy.selectCandidate(candidates) // 비어있으면 InsufficientPassCountException -> 409 매핑

            // ③ 조건부 차감 — adjustRemainingCount 재사용, 0행이면 경쟁 패배/상태 변경
            if (passRepository.adjustRemainingCount(candidate.id!!, HALF_SESSION.negate()) == 0) {
                throw InsufficientPassCountException()
            }
            // ... 재조회 + PassTransaction(EVENING_HALF) 저장
        }

    // ④ Attendance INSERT — 회원×세션 유니크가 이중 출석/이중 차감을 DB에서 막는다
    return attendanceRepository.save(Attendance(..., passTransaction = passTransaction, status = ATTENDED))
}
```
**둘 다 불가하면 출석 추가 자체를 거부한다(D-128)** — `candidates`가 비어 있으면
`ReservationPassPolicy.selectCandidate`가 이미 `InsufficientPassCountException`을 던지므로, 이
예외를 그대로 409로 매핑하면 별도 새 예외 타입 없이 요구사항을 충족한다(단, 에러코드 표에
저녁반 맥락임을 구분할 별도 코드가 필요한지는 Open Questions 참고).

### Pattern 3: "모두 읽음"은 조건부 벌크 UPDATE + 재사용 가능한 KDoc 관례를 따른다

**What:** `PassRepository.adjustRemainingCount`가 확립한 `@Modifying(flushAutomatically = true,
clearAutomatically = true)` 관례를 그대로 쓴다.
**When to use:** NOTIF-02 "모두 읽음" 처리.
**Example:**
```kotlin
// Source: 이 저장소 PassRepository.adjustRemainingCount의 flush/clear 관례를 그대로 적용
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query("update Notification n set n.isRead = true, n.readAt = :now where n.isRead = false")
fun markAllAsRead(@Param("now") now: OffsetDateTime): Int
```
호출부는 이 벌크 UPDATE 이전에 로드한 `Notification` 엔티티의 `isRead`/`readAt`을 그대로 읽지
않는다 — 항상 벌크 UPDATE 이후 재조회한 응답을 반환한다(`PassRepository` KDoc의
`LazyInitializationException` 경고와 동일 원리, 여기선 스칼라 필드라 실제 영향은 적지만 관례를
일관되게 유지한다).

### Pattern 4: 활동 피드는 같은 테이블의 다른 Specification 조합

**What:** `notification` 테이블에 `ReservationSpecifications`와 동일 관례의 `Specification` 필터를
추가한다 — 기간(`occurredAt between`), 종류(`type = :type`).
**When to use:** NOTIF-03.
**Example:**
```kotlin
// Source: 이 저장소 ReservationSpecifications.classDateBetween 관례를 그대로 적용
object NotificationSpecifications {
    fun occurredBetween(from: OffsetDateTime?, to: OffsetDateTime?): Specification<Notification>? {
        if (from == null && to == null) return null
        return Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from))
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to))
            cb.and(*predicates.toTypedArray())
        }
    }

    fun hasType(type: NotificationType?): Specification<Notification>? {
        if (type == null) return null
        return Specification { root, _, cb -> cb.equal(root.get<NotificationType>("type"), type) }
    }
}
```

### Pattern 5: CR-03 배선은 기존 벌크 프로젝션 관례를 그대로 재사용

**What:** `PassRepository.findLastDeductibleSessionPassRegistrationDates`/
`ReservationRepository.findLastActiveReservationClassDates`와 정확히 같은 모양의 쿼리를
`AttendanceRepository`에 하나 추가한다.
**When to use:** `InactivityBatchRunner.kt:157`의 `lastAttendanceDate = null` 대체.
**Example:**
```kotlin
// Source: 이 저장소 ReservationRepository.findLastActiveReservationClassDates 관례를 그대로 적용
@Query(
    "select a.member.id as memberId, max(a.classSession.classDate) as date from Attendance a " +
        "where a.member.id in :memberIds and a.status = com.goldwrestling.attendance.AttendanceStatus.ATTENDED " +
        "group by a.member.id",
)
fun findLastAttendedClassDates(@Param("memberIds") memberIds: Collection<Long>): List<MemberDateProjection>
```
`InactivityBatchRunner.runStarted`에서 한 줄만 바뀐다:
```kotlin
val lastAttendedDates = attendanceRepository.findLastAttendedClassDates(memberIds).associate { it.getMemberId() to it.getDate() }
// ...
lastAttendanceDate = lastAttendedDates[memberId],
```

### Anti-Patterns to Avoid

- **출석 테이블에 잔여 차감량을 직접 저장하지 않는다** — 차감은 항상 `PassTransaction` 원장을
  거친다(CLAUDE.md 규칙 6). `Attendance`는 `pass_transaction_id`(nullable FK)로 "이 출석이 어떤
  차감을 유발했는가"만 추적한다.
- **예약제/1:1 출석에 회원×세션 파셜 유니크(예약의 `WHERE status = 'ACTIVE'` 관례)를 그대로
  복붙하지 않는다** — 예약은 "취소되면 다시 예약 가능"해야 해서 파셜 유니크가 맞지만, 출석은
  레코드가 있으면 이미 체크된 것이고 상태(`ATTENDED`/`ABSENT`)만 바뀌는 게 맞다(D-127 "레코드
  부재=미체크"). 여기서는 **조건 없는 일반 유니크** `UNIQUE (class_session_id, member_id)`가
  맞다 — 예약 선례를 무비판적으로 따라가면 잘못된 제약이 된다.
- **회원용 알림 수신자 컬럼을 추가하지 않는다** — D-097이 명시적으로 확정한 제약. `Notification`
  엔티티에 손대지 않는다(Phase 4가 완성, 이 phase는 조회 계층만 추가).
- **공지에 soft delete 컬럼을 추가하지 않는다** — D-131이 hard delete로 확정.
- **`InactivityDueDateCalculator`/`InactivityDueDateCandidates`를 수정하지 않는다** — 이미 5종
  후보를 받는 시그니처이므로 CR-03은 호출부(`InactivityBatchRunner`) 배선 한 지점만 바꾼다.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 페이지네이션 응답 형태 | 새 `Page` 래퍼 | `member.dto.PageResponse<T>` | 이미 두 기능(공지, 피드)이 쓰게 되므로 `common`으로 승격을 고려하되, 최소한 새로 만들지 않는다 |
| 차감 대상 이용권 선택(만료 임박순) | 저녁반 전용 새 정렬 로직 | `ReservationPassPolicy.selectCandidate` + `PassRepository.findDeductionCandidates` | D-091이 이미 "만료 임박순, 합산 금지"를 확정. `requiredAmount`만 `HALF_SESSION`으로 바꿔 재사용 |
| 회원 검색(이름·전화번호) | 저녁반 출석 추가 화면 전용 새 검색 | `MemberSpecifications.keywordContains` + `LikePatternEscaper`/`PhoneNumberNormalizer` | 기존 관리자 회원 검색과 완전히 동일한 요구(CONTEXT.md "기존 관리자 회원 검색 자산 재사용 가능") |
| 조건부 원자 갱신(잔여 차감·상태 전이) | 애플리케이션 레벨 락 또는 `SELECT ... FOR UPDATE` | `@Modifying` 조건부 `UPDATE ... WHERE` (D-021) | 이 프로젝트 전체가 이 패턴 하나로 통일돼 있다. 새 락 메커니즘을 도입하면 D-021 원칙이 두 갈래로 갈라진다 |
| 벌크 회원별 날짜 조회 | 세션당 개별 조회(N+1) | `MemberDateProjection`/`MemberTimestampProjection` + `GROUP BY` 벌크 쿼리 | 배치가 회원 수백 명을 순회하는데 세션별 개별 조회면 N+1이 배치 실행 시간을 직접 늘린다 |
| 시각 처리 | `OffsetDateTime.now()` 직접 호출 | 주입받은 `Clock` 빈 | 소급 출석·시각 고정 테스트가 이 phase의 핵심 시나리오(policies §6 "소급 입력·수정 허용") |

**Key insight:** 이 phase가 새로 발명해야 하는 메커니즘은 사실상 없다 — Phase 3~5가 "조건부
UPDATE + 원장 원자 기록", "Specification 동적 조건", "벌크 프로젝션 배치 조회" 세 패턴을 이미
충분히 성숙시켜 뒀고, 이 phase의 6개 요구사항 전부가 이 세 패턴의 재조합으로 해결된다. 새 패턴을
도입하려는 유혹이 든다면(예: 저녁반 차감에 별도 락 메커니즘, 활동 피드 전용 캐시 등) 그것은
과설계 신호다.

## Common Pitfalls

### Pitfall 1: 저녁반 0.5회 차감의 유효기간 판정 기준일을 오늘로 잘못 쓴다
**What goes wrong:** `findDeductionCandidates`의 `classDate` 파라미터에 `LocalDate.now(clock)`을
넘기면, 소급 입력(예: 2주 전 저녁반 출석을 오늘 등록)에서 그날 유효했던 `SESSION_PASS`가 지금은
만료돼 후보에서 빠지거나, 반대로 지금은 유효하지만 그날은 미등록이었던 이용권이 잘못 잡힌다.
**Why it happens:** `ReservationPassPolicy`/`PassRepository.findDeductionCandidates`는 원래
"예약일이 아니라 수업날" 기준으로 설계됐는데(D-091), 저녁반 출석은 "체크한 날"과 "수업날"이
소급 입력 시 다를 수 있다는 점이 예약(항상 당일~1주 내 체결)보다 더 두드러진다.
**How to avoid:** `Attendance.classSession.classDate`(=수업날)를 항상 파라미터로 넘긴다. 오늘
날짜는 어디에도 쓰지 않는다(D-128 "유효기간 판정 기준일은 오늘이 아니라 수업날").
**Warning signs:** 소급 출석 통합테스트에서 "그날은 유효했는데 지금 기준으로 거부됨" 또는 반대
케이스가 나오면 이 버그다.

### Pitfall 2: 예약제/1:1 출석의 회원×세션 유니크를 예약처럼 파셜 유니크로 만든다
**What goes wrong:** `WHERE status = 'ATTENDED'`처럼 조건부 유니크를 걸면, 같은 회원을 `ABSENT`로
체크했다가 `ATTENDED`로 정정할 때 유니크 위반이 아니라 새 행이 INSERT되면서 회원×세션 1건 원칙이
깨진다.
**Why it happens:** 이 저장소의 동시성 방어 선례가 전부 예약의 파셜 유니크(V6 `ux_reservation_*`,
"취소되면 재예약 가능")라 그 패턴을 무비판적으로 복사하기 쉽다.
**How to avoid:** 출석은 **조건 없는 일반 `UNIQUE (class_session_id, member_id)`**를 쓴다. 상태
전환(`ABSENT`→`ATTENDED`)은 새 행이 아니라 기존 행의 `status` UPDATE로 처리한다(D-127 "소급
수정 허용"이 이 UPDATE 경로를 요구한다).
**Warning signs:** 마이그레이션 리뷰에서 `Attendance` 유니크 제약에 `WHERE` 절이 있으면 재검토.

### Pitfall 3: 소급 출석이 이미 확정된 `INACTIVITY` 차감을 환불하려는 로직을 추가한다
**What goes wrong:** "출석 기록을 나중에 추가했으니 그 회원의 부당 차감을 자동으로 되돌려주자"는
직관적인 요구처럼 보이지만, `InactivityDueDateCalculator.shortfall`은 `coerceAtLeast(0)`으로
음수를 이미 차단하고 있고, 배치를 양방향으로 만들면 D-106 멱등성 검증이 기하급수적으로 복잡해진다.
**Why it happens:** "출석 데이터가 늦게 들어왔으니 정정해야 한다"는 자연스러운 사용자 기대와,
"배치는 절대 환불하지 않는 단방향 원장이어야 한다"는 D-127 정책이 충돌하는 지점이라 구현자가
임의로 "더 정확해 보이는" 쪽을 선택하기 쉽다.
**How to avoid:** policies §6 문구를 그대로 코드 주석·PR 설명에 인용한다 — "소급 출석이 이미
실행된 `INACTIVITY` 차감을 되돌리지 않는다. 기준일 반영은 다음 배치 실행부터다." 정정이
필요하면 오직 관리자 `ADMIN_ADJUST` 수동 경로 하나만 존재해야 한다.
**Warning signs:** `Attendance` 저장 로직이 `PassTransactionRepository`나 `INACTIVITY` 이력을
조회·수정하는 코드 경로를 갖고 있으면 즉시 재검토.

### Pitfall 4: CR-03 벌크 조회에서 `ABSENT`를 포함해 기준일을 계산한다
**What goes wrong:** `findLastAttendedClassDates` 쿼리에 `status` 필터를 빼먹으면 불참 기록도
"마지막 참여일"로 잡혀, 실제로는 안 왔는데 유예 기간이 갱신된다.
**Why it happens:** SQL을 짤 때 "가장 최근 출석 레코드"와 "가장 최근 **참석** 레코드"를 혼동하기
쉽다 — 이 phase의 출석 모델 자체가 두 개념을 명확히 구분한다는 걸 놓치면 발생한다.
**How to avoid:** 쿼리에 `and a.status = AttendanceStatus.ATTENDED` 조건을 명시한다(policies §6
"이 출석 기록 중 출석(ATTENDED)만 기준으로 한다").
**Warning signs:** 단위테스트에서 "불참만 있는 회원은 기준일 후보 ①이 여전히 null"을 검증하지
않으면 이 버그를 못 잡는다 — 반드시 이 케이스를 테스트로 남긴다.

### Pitfall 5: 알림 벌크 UPDATE 직후 준영속 엔티티의 LAZY 필드에 접근한다
**What goes wrong:** `markAllAsRead` 실행 직후, 이전에 로드해 둔 `Notification` 리스트를 그대로
응답 변환에 쓰면 `clearAutomatically = true`로 준영속화된 엔티티의 `isRead` 값이 벌크 UPDATE 이전
스냅샷일 수 있다(스칼라 필드라 LazyInitializationException은 안 나지만 값이 stale할 수 있다).
**Why it happens:** `PassRepository.adjustRemainingCount` KDoc이 경고하는 패턴과 동일한 함정 —
벌크 UPDATE는 영속성 컨텍스트를 우회한다.
**How to avoid:** 벌크 UPDATE 이후 항상 재조회(또는 갱신된 개수만 응답에 반영하고 목록은 별도
재조회 쿼리로 채운다)한다.
**Warning signs:** 통합테스트에서 "모두 읽음 처리 직후 목록 재조회 시 전부 isRead=true"를
검증하지 않으면 이 버그를 놓친다.

### Pitfall 6: 활동 피드 조회에 필요한 인덱스가 없어 전체 스캔한다
**What goes wrong:** `idx_notification_unread (is_read, occurred_at DESC)`는 **폴링(미확인만
조회)** 최적화용으로 Phase 4가 미리 만들어 둔 인덱스라, `is_read` 조건 없이 전체 이력을
`occurred_at` 역순으로 조회하는 활동 피드 쿼리(NOTIF-03)에는 이 인덱스가 활용되지 않을 수 있다.
**Why it happens:** "인덱스가 이미 있으니 새로 만들 필요 없겠지"라고 낙관하기 쉽지만, 이
인덱스의 선두 컬럼이 `is_read`라 `is_read` 조건이 없는 쿼리는 인덱스를 온전히 활용하지 못한다.
**How to avoid:** 단일 지점 소규모 운영(관리자 1~2명, 알림 건수가 수천~수만 건 수준일 것으로
예상)이라 즉시 문제가 되진 않겠지만, 새 마이그레이션에서 `idx_notification_occurred_at ON
notification (occurred_at DESC)`(또는 `type` 필터가 잦으면 `(type, occurred_at DESC)`) 추가를
플래너가 고려하도록 남긴다. 필수는 아니지만 값싼 대비책이다.
**Warning signs:** 운영 데이터가 쌓인 뒤 활동 피드 조회가 느려지면 이 인덱스 부재를 먼저 의심한다.

## Code Examples

### 신규 마이그레이션 골격(V11) — Attendance + Notice + pass_transaction reason 확장

```sql
-- Source: 이 저장소 add-migration 스킬 규약 + V4~V10 선례를 그대로 적용(실제 SQL은 플래너가 확정)
-- V11__create_attendance_and_notice.sql

CREATE TABLE attendance (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    class_session_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,              -- ATTENDED / ABSENT
    pass_transaction_id BIGINT,               -- 저녁반 0.5회 차감 연결(nullable — 예약제/1:1은 항상 null)
    checked_by_admin_id BIGINT NOT NULL,
    checked_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_attendance_class_session FOREIGN KEY (class_session_id) REFERENCES class_session (id),
    CONSTRAINT fk_attendance_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_attendance_pass_transaction FOREIGN KEY (pass_transaction_id) REFERENCES pass_transaction (id),
    CONSTRAINT fk_attendance_checked_by FOREIGN KEY (checked_by_admin_id) REFERENCES admin (id),
    -- 조건 없는 일반 유니크 — Pitfall 2 참고. 예약의 파셜 유니크 관례를 그대로 복사하지 않는다.
    CONSTRAINT uq_attendance_member_session UNIQUE (class_session_id, member_id)
);
CREATE INDEX idx_attendance_member ON attendance (member_id, checked_at);  -- CR-03 벌크 조회 대비

CREATE TABLE notice (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    created_by_admin_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_notice_created_by FOREIGN KEY (created_by_admin_id) REFERENCES admin (id)
);
CREATE INDEX idx_notice_created_at ON notice (created_at DESC);  -- 목록 최신순 기본 정렬
```

`pass_transaction.reason`은 `VARCHAR(30)`에 DB `CHECK` 제약이 없으므로(CONTEXT.md 확인 완료),
`TransactionReason` enum에 `EVENING_HALF_REFUND` 상수만 추가하면 마이그레이션 변경 없이 저장
가능하다.

### EVENING_MEMBERSHIP 유효성 조회 신규 쿼리(회비 우선 판정)

```kotlin
// Source: 이 저장소 PassRepository.findDeductionCandidates 관례를 그대로 적용(신규 메서드)
@Query(
    "select count(p) > 0 from Pass p where p.member.id = :memberId and p.type = " +
        "com.goldwrestling.pass.PassType.EVENING_MEMBERSHIP and p.status = com.goldwrestling.pass.PassStatus.ACTIVE " +
        "and p.startDate <= :classDate and p.endDate >= :classDate",
)
fun existsActiveEveningMembership(
    @Param("memberId") memberId: Long,
    @Param("classDate") classDate: LocalDate,
): Boolean
```

## State of the Art

이 phase에 해당하는 "구식→신식 전환" 항목은 없다 — 사내 코드베이스가 이미 최신 관례로
일관돼 있고, 이 phase는 그 관례를 확장할 뿐 대체하지 않는다.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | 활동 피드용 추가 인덱스(`occurred_at DESC` 단독)는 이 phase에서 필수는 아니고 값싼 대비책 수준이다 | Common Pitfalls #6 | 운영 데이터가 예상보다 빨리 쌓이면(다지점 확장 등) 조회 성능 저하가 더 일찍 나타날 수 있음 — 플래너가 필수로 승격할지 판단 필요 |
| A2 | 저녁반 차감 거부(회비 없음+SESSION_PASS 부족)를 기존 `InsufficientPassCountException`(409, `INSUFFICIENT_PASS_COUNT`)으로 재사용해도 FE가 예약 부족과 저녁반 부족을 구분할 필요가 없다 | Pattern 2, Code Examples | CONTEXT.md의 "문서 정합" 항목이 "출석 관련 신규 에러 코드(차감 불가 409 등) 등록"을 명시하므로, 실제로는 저녁반 전용 새 에러코드(예: `EVENING_HALF_DEDUCTION_UNAVAILABLE`)가 필요할 가능성이 높다 — Open Questions 참고 |

## Open Questions (RESOLVED)

1. **저녁반 차감 불가(409) 응답에 기존 `INSUFFICIENT_PASS_COUNT`를 재사용할지, 새 에러코드를
   만들지**
   - RESOLVED: 신규 에러코드 `EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE`(409)로 확정 —
     플래너 재량 결정 D-133, 06-01-PLAN.md에서 `error-codes.md` 등록 포함.
   - What we know: CONTEXT.md의 "문서 정합" 섹션이 "error-codes.md: 출석 관련 신규 에러 코드
     (차감 불가 409 등) 등록"을 명시적으로 남겨 뒀다 — 신규 코드가 필요하다는 신호로 읽힌다.
   - What's unclear: 새 코드가 필요한 이유가 "FE가 저녁반 맥락과 예약 맥락을 다르게 안내해야
     해서"인지, 아니면 단순히 "회비 없음 + 잔여 부족"이라는 복합 사유를 `INSUFFICIENT_PASS_COUNT`
     하나로는 설명이 부족해서인지 CONTEXT.md에 구체적 근거가 없다.
   - Recommendation: 플래너가 이 phase 착수 시점에 신규 코드(예:
     `EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE`, 409)를 만들고 `error-codes.md`에 등록하는 쪽을
     기본으로 잡는다 — CONTEXT.md가 이미 "신규 코드 등록"을 확정 항목처럼 적어 뒀으므로 재사용
     쪽으로 판단을 뒤집으려면 사용자 확인이 한 번 더 필요하다.

2. **저녁반 출석 "회원 검색으로 추가" API의 응답 형태 — 회원 검색과 출석 추가가 한 API인지 별도
   2단계인지**
   - RESOLVED: 기존 `GET /api/admin/members?keyword=` 재사용 + "추가"만 신규 API로 하는
     2단계 흐름으로 확정 — 06-08-PLAN.md에 명시(신규 검색 API 없음).
   - What we know: CONTEXT.md Specific Ideas가 "회원 검색 → 추가 흐름, 검색은 기존 관리자 회원
     검색(이름·전화번호) 자산 재사용 가능"이라고만 적어 뒀고, Claude's Discretion에 "배치 처리
     형태(타임별 일괄 저장 vs 개별 토글)"가 재량으로 명시돼 있다.
   - What's unclear: "회원 검색"은 기존 `GET /api/admin/members?keyword=`를 그대로 FE가 별도
     호출하는 것으로 충분한지(신규 API 불필요), 아니면 저녁반 출석 화면 전용 축약 검색 API가
     필요한지.
   - Recommendation: 기존 `AdminMemberController.search`를 그대로 재사용하고(신규 API 불필요),
     "추가"만 새 API(`POST .../attendance` with `memberId`)로 만든다 — Don't Hand-Roll 원칙과
     일관되고 CONTEXT.md의 "재사용 가능" 문구와도 맞는다. 플래너 재량으로 확정.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker(Testcontainers 실행) | 통합테스트, `docker compose up -d` | ✓ | 28.5.1 | — |
| JDK 21 | 빌드·실행 | ✓ | OpenJDK 21.0.12 (Zulu) | — |
| Gradle Wrapper | 빌드 | ✓ | Gradle 9.6.1 / Kotlin 2.3.21 | — |
| PostgreSQL(로컬 docker-compose) | 로컬 실행 | ✓(Docker 데몬 기동 확인됨) | 기존 `docker-compose.yml` 고정 버전 재사용 | — |

**Missing dependencies with no fallback:** 없음
**Missing dependencies with fallback:** 없음 — 이 phase는 새 외부 의존성을 요구하지 않는다.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Kotlin(`kotlin-test-junit5`) + AssertJ, Testcontainers(PostgreSQL, `testcontainers-postgresql`) `[VERIFIED: build.gradle.kts]` |
| Config file | `build.gradle.kts`(테스트 의존성 선언), 별도 설정 파일 없음 — Gradle 표준 소스셋 |
| Quick run command | `./gradlew test --tests "com.goldwrestling.attendance.*"` (해당 패키지 범위, notice/notification도 동일 패턴) |
| Full suite command | `./gradlew ktlintFormat && ./gradlew build` (ktlint + compile + 전체 테스트) — 사용자 메모리("GSD 회귀 게이트 no-op") 기준 phase 마감 시 `./gradlew cleanTest test` 직접 실행 |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ATTEND-01 | 예약제/1:1 명단 프리로드 후 출석/불참 체크, 레코드 부재=미체크 | integration(Testcontainers) | `./gradlew test --tests AdminAttendanceControllerTest` | ❌ Wave 0 |
| ATTEND-01 | 저녁반은 회원 추가=출석만 존재(불참 없음) | unit + integration | `./gradlew test --tests AttendanceServiceTest` | ❌ Wave 0 |
| ATTEND-01 | 회원×세션 유니크 제약(중복 출석 방지) | integration(Testcontainers) | `./gradlew test --tests AttendanceRepositoryTest` | ❌ Wave 0 |
| ATTEND-01 | 소급 출석 입력·수정이 기존 INACTIVITY 이력을 건드리지 않는다 | integration | `./gradlew test --tests AttendanceRetroactiveTest` | ❌ Wave 0 |
| ATTEND-02 | 회비(EVENING_MEMBERSHIP) 보유 시 차감 없이 출석만 기록 | unit + integration | `./gradlew test --tests EveningHalfDeductionPolicyTest`, `AttendanceServiceTest` | ❌ Wave 0 |
| ATTEND-02 | 회비 없고 SESSION_PASS 잔여 0.5 이상이면 0.5 차감(D-091 만료 임박순) | integration(Testcontainers) | `./gradlew test --tests AttendanceServiceTest` | ❌ Wave 0 |
| ATTEND-02 | 회비 없고 잔여 0.5 미만이면 출석 추가 자체 409 거부 | integration | `./gradlew test --tests AttendanceServiceTest` | ❌ Wave 0 |
| ATTEND-02 | 출석 삭제 시 연결 차감이 EVENING_HALF_REFUND로 자동 복구 | integration | `./gradlew test --tests AttendanceServiceTest` | ❌ Wave 0 |
| ATTEND-02 | 동시에 같은 회원·세션에 두 번 출석+차감 시도해도 이중 차감 0건 | 동시성(통합) | `./gradlew test --tests AttendanceConcurrencyTest` | ❌ Wave 0 |
| NOTICE-01 | 관리자 공지 등록/수정/삭제(hard delete) | integration | `./gradlew test --tests AdminNoticeControllerTest` | ❌ Wave 0 |
| NOTICE-02 | 회원 공지 목록·상세 열람 | integration | `./gradlew test --tests MemberNoticeControllerTest` | ❌ Wave 0 |
| NOTIF-02 | 알림 목록 조회 시 미확인 카운트 포함 | integration | `./gradlew test --tests AdminNotificationControllerTest` | ❌ Wave 0 |
| NOTIF-02 | 모두 읽음 처리 후 재조회 시 전부 isRead=true | integration | `./gradlew test --tests AdminNotificationControllerTest` | ❌ Wave 0 |
| NOTIF-03 | 활동 피드가 기간·종류 필터로 조회되고 읽음 여부 무관 | integration | `./gradlew test --tests ActivityFeedTest` | ❌ Wave 0 |
| (CR-03) | 5종 후보 중 ATTENDED만 반영, ABSENT 제외 | unit + integration | `./gradlew test --tests InactivityDueDateCalculatorTest`(기존 확장), `AttendanceRepositoryTest` | ❌ Wave 0(기존 파일 확장 + 신규) |
| (CR-03) | 소급 출석이 이미 실행된 INACTIVITY 차감을 되돌리지 않는다(멱등) | integration | `./gradlew test --tests InactivityBatchRunnerTest`(기존 확장) | ❌ Wave 0(기존 파일 확장) |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "com.goldwrestling.attendance.*"` (또는 해당 청크의
  기능 패키지 범위)
- **Per wave merge:** `./gradlew ktlintFormat && ./gradlew build` (전체 스위트)
- **Phase gate:** 전체 스위트 green 상태로 `/gsd:verify-work` 진입, 이후 `./gradlew cleanTest test`
  로 캐시 없이 재확인(사용자 메모리 "GSD 회귀 게이트 no-op" 대응)

### Wave 0 Gaps

- [ ] `src/test/kotlin/com/goldwrestling/attendance/AttendanceRepositoryTest.kt` — 유니크 제약,
      CR-03 벌크 조회(`findLastAttendedClassDates`) 통합테스트
- [ ] `src/test/kotlin/com/goldwrestling/attendance/EveningHalfDeductionPolicyTest.kt` — 회비
      우선·차감 대상 선택 순수 판정 단위테스트
- [ ] `src/test/kotlin/com/goldwrestling/attendance/AttendanceServiceTest.kt` — 저녁반 추가+차감
      트랜잭션, 삭제+복구, 예약제/1:1 체크 통합테스트
- [ ] `src/test/kotlin/com/goldwrestling/attendance/AdminAttendanceControllerTest.kt` — 엔드포인트
      성공/실패 경로
- [ ] `src/test/kotlin/com/goldwrestling/attendance/AttendanceConcurrencyTest.kt` — 동시 출석+차감
      경쟁(`add-domain-test` 스킬 §4 패턴)
- [ ] `src/test/kotlin/com/goldwrestling/attendance/AttendanceFixtures.kt` — `BatchFixtures.kt`와
      동일 관례의 테스트 픽스처 헬퍼
- [ ] `src/test/kotlin/com/goldwrestling/notice/*Test.kt` — CRUD 통합테스트(관리자·회원 컨트롤러)
- [ ] `src/test/kotlin/com/goldwrestling/notification/AdminNotificationControllerTest.kt`,
      `ActivityFeedTest.kt` — 폴링/모두읽음/피드 통합테스트
- [ ] `src/test/kotlin/com/goldwrestling/batch/InactivityDueDateCalculatorTest.kt`(기존 파일 확장)
      — ATTENDED만 후보 인정 케이스 추가
- [ ] `src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunnerTest.kt`(기존 파일 확장) —
      실제 `Attendance` 데이터를 준비해 기준일 후보 ①이 채워짐을 검증
- [ ] 프레임워크 설치: 불필요 — 기존 JUnit5/AssertJ/Testcontainers 배선 그대로 재사용

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | 새 인증 경로 없음 — 기존 JWT 체계 재사용(D-040) |
| V3 Session Management | no | 해당 없음(STATELESS, 세션 개념 없음 — glossary 금지어) |
| V4 Access Control | yes | 관리자 전용 API(출석 체크, 저녁반 차감, 공지 CRUD, 알림/피드 조회)는 `/api/admin/**` → `hasRole("ADMIN")` 기존 규칙 상속(D-040). 회원 전용 API(공지 열람)는 `/api/members/**` 기존 규칙. 별도 인가 코드 불필요, `AdminScheduleController`/`AdminMemberController`와 동일 패턴을 그대로 따른다 |
| V5 Input Validation | yes | `CheckAttendanceRequest`(memberId/status), `CreateNoticeRequest`(title/content 길이) 등에 `jakarta.validation` 형식 검증. 도메인 검증(잔여 부족, 회원×세션 중복)은 서비스·DB 제약이 담당(D-019 연장) |
| V6 Cryptography | no | 해당 없음 |

### Known Threat Patterns for Kotlin/Spring Boot 4 + JPA + PostgreSQL

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| 회원이 본인 소속이 아닌 지점/세션의 출석 데이터를 조회·조작(IDOR) | Tampering/Information Disclosure | 출석 API는 전부 관리자 전용(`/api/admin/**`)이라 회원 principal은 컨트롤러 진입 자체가 차단된다 — `RESERVATION_NOT_FOUND`(404, 존재 여부 비노출) 관례를 공지 상세 조회(회원 열람)에도 적용해, 존재하지 않는 공지 id 조회 시 403이 아니라 404로 응답한다 |
| 저녁반 0.5회 차감을 회원이 직접 호출해 자가 차감/자가 복구를 유발 | Elevation of Privilege | 저녁반 출석·차감 API는 관리자 전용 — 회원이 직접 호출할 경로가 URL 인가 규칙 단계에서 차단됨. `PassTransaction.admin`이 항상 채워지고 `member`는 null(관리자 주체 관례, `PassTransaction.kt` KDoc) |
| 활동 피드/알림 API를 통한 관리자 정보 과다 노출(다른 회원 정보가 비정규화 필드로 이미 포함) | Information Disclosure | `Notification`의 비정규화 필드(회원명·수업정보)는 설계상 의도된 관리자 전용 노출(D-097) — 회원 principal에게는 이 API 자체가 노출되지 않으므로 추가 마스킹 불필요 |
| 소급 출석 입력 남용으로 배치 차감을 조작(관리자 계정 탈취 시) | Tampering | 관리자 인증은 기존 JWT 체계로 보호되며, 이 phase가 추가하는 새 공격 표면이 아니다. 다만 소급 출석이 `INACTIVITY` 차감에 미치는 영향이 "다음 배치부터"로 제한돼(D-127) 즉시 악용 시 피해 범위가 제한된다 |
| 공지 XSS(본문에 스크립트 삽입 후 회원 화면에서 실행) | Tampering | BE는 저장·응답만 담당하고 이스케이프/새니타이징은 FE 렌더링 책임(별도 레포) — BE 응답은 순수 텍스트/마크다운 원문을 그대로 전달하는 것이 이 프로젝트의 계약(D-019 DTO 원칙과 일관, `docs/policies.md`에 별도 서식 요구 없음). 플래너는 FE와의 계약에 "content는 이스케이프되지 않은 원문"임을 명시해야 한다 |

## Sources

### Primary (HIGH confidence)

- 이 저장소 코드 직접 조사(전부 `Read`/`Bash grep`으로 확인): `Notification.kt`, `NotificationType.kt`,
  `NotificationService.kt`, `NotificationRepository.kt`, `TransactionReason.kt`, `PassTransaction.kt`,
  `PassRepository.kt`, `Pass.kt`, `PassType.kt`, `ReservationPassPolicy.kt`, `ReservationLedgerSupport.kt`,
  `ReservationSpecifications.kt`, `Reservation.kt`, `ReservationRepository.kt`, `ClassSession.kt`,
  `ClassType.kt`, `ClassSessionStatus.kt`, `ClassSessionService.kt`, `InactivityBatchRunner.kt`,
  `InactivityDueDateCalculator.kt`, `InactivityBatchProperties.kt`, `InactivityBatchScheduler.kt`,
  `MemberDateProjection.kt`, `MemberTimestampProjection.kt`, `PageResponse.kt`, `AdminScheduleController.kt`,
  `AdminMemberController.kt`, `MemberSpecifications.kt`, `ErrorCode.kt`, `build.gradle.kts`,
  `V1~V10` 마이그레이션 전체(특히 `V6__create_schedule_reservation_notification.sql`의 notification
  테이블·`idx_notification_unread` 인덱스)
- `docs/policies.md` §4.1·§4.2·§4.2a·§4.3·§6 — 저녁반 차감, 출석 체크, 미사용 차감 기준일 원문
- `docs/decisions.md` D-016·D-017·D-018·D-019·D-020·D-021·D-091·D-097·D-105·D-106·D-116·D-119·D-121·D-127·D-128·D-129·D-130·D-131 — 전체 원문 확인
- `docs/conventions.md` §1(패키지 레이아웃, `attendance`/`notice` 명시 확인)·§3·§4·§5·§6·§7·§8·§9·§10·§10.0
- `docs/error-codes.md` — 전체 코드 레지스트리, 폴백 규칙
- `.claude/skills/add-endpoint/SKILL.md`, `.claude/skills/add-migration/SKILL.md`,
  `.claude/skills/add-domain-test/SKILL.md` — 전체 절차
- `.planning/phases/06-operations/06-CONTEXT.md` — Phase 6 확정 결정 전문
- `.planning/REQUIREMENTS.md` — ATTEND/NOTICE/NOTIF 요구사항 원문, Phase 1~5 완료 이력
- `.planning/phases/05-batch/05-RESEARCH.md` — 선행 phase의 Validation Architecture/Security Domain
  섹션 형식 일관성 확인용 참조

### Secondary (MEDIUM confidence)

없음 — 이 phase는 신규 외부 지식(라이브러리 API, 서드파티 문서)을 요구하지 않아 WebSearch/Context7을
사용하지 않았다. 모든 결론은 사내 코드베이스·문서 직접 조사로 뒷받침된다.

### Tertiary (LOW confidence)

없음.

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — 신규 의존성 없음, 기존 `build.gradle.kts` 버전을 직접 읽어 확인
- Architecture: HIGH — Phase 3~5가 확립한 패턴(조건부 UPDATE, Specification, 벌크 프로젝션)을 그대로
  재사용하는 phase이며, 재사용 대상 코드를 전부 직접 읽고 확인함
- Pitfalls: HIGH — 6개 중 5개는 이 저장소의 기존 KDoc·정책 문서가 명시적으로 경고하는 함정을
  구체화한 것이고, 나머지 1개(인덱스)는 저위험 권장사항으로 명확히 구분해 표기함
- Security: MEDIUM — ASVS 매핑은 기존 phase(05-RESEARCH.md) 형식을 따랐으나, 이 phase 고유의
  위협(공지 XSS, 활동 피드 정보 노출)은 이번에 새로 도출한 것이라 팀 리뷰로 재확인 권장

**Research date:** 2026-08-18
**Valid until:** 이 phase 계획·실행 기간 내 유효(사내 코드베이스 기준 조사라 외부 라이브러리
변동성에 영향받지 않음 — 단, 이 phase 착수 전 `docs/decisions.md`에 D-131 이후 새 결정이 추가됐다면
재확인 필요)
