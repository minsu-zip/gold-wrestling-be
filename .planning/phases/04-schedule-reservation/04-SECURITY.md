---
phase: 04
slug: schedule-reservation
status: verified
threats_open: 0
asvs_level: 1
created: 2026-08-08
---

# Phase 04 — Security

> 시간표·예약 phase의 보안 계약: 위협 레지스터, 수용된 위험, 감사 이력.
> 레지스터는 15개 PLAN의 `<threat_model>` 블록에서 그대로 가져왔다 (플랜 작성 시점에 저작 — 사후 역산이 아니다).

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| 회원 클라이언트 → 예약 API | 인증된 회원이 `classScheduleId`·`classDate`·`weekStart`·`reservationId`를 임의 값으로 보낼 수 있다 | 예약 요청 파라미터 (신뢰 불가) |
| 회원 클라이언트 → 시간표 조회 API | 임의 `weekStart`로 과거·미래 예약 현황을 탐색할 수 있는 경로 | 주간 시간표 셀 (정원·예약 수, 명단 제외) |
| 관리자 클라이언트 → 관리자 API | 전체 회원의 예약·이름이 나가는 유일한 경로, `branchId`로 타 지점 열람 시도 가능 | 회원 개인정보(이름), 전 지점 예약 데이터 |
| 애플리케이션 → DB | 모든 예약 쓰기가 넘는 경계 — 경쟁 조건이 원장 불변식을 깰 수 있는 유일한 지점 | 예약·세션 정원·이용권 잔여·`PassTransaction` 이력 |
| 서비스 → 다중 테이블 쓰기 | 예약 1건이 5개 테이블을 갱신한다. 일부만 남으면 "잔여 = 실제 사용 가능 횟수"가 깨진다 | 원장 정합성 |
| 서버 → 클라이언트 (에러 응답) | 실패 사유가 타인 예약의 존재 여부·내부 구조를 유추하게 할 수 있다 | 예외 메시지·HTTP 상태 코드 |
| 검색어 파라미터 → JPA Criteria LIKE | 사용자 입력이 쿼리 조건으로 직접 들어가는 지점 | 관리자 예약 검색 키워드 |
| 문서(계약) → FE | `openapi.yaml`이 코드와 어긋나면 FE가 잘못된 타입으로 통신한다 | API 스키마 |

---

## Threat Register

76개 위협 전부 CLOSED. `mitigate` 72건은 구현 코드·마이그레이션·테스트에서 실증했고, `accept` 4건은
근거의 사실 여부를 별도로 확인했다 (근거를 그대로 믿지 않았다).

### 04-01 ~ 04-05 (기반·엔티티·창 판정·시간표 조회)

| Threat ID | Category | Component | Disposition | Mitigation (검증된 위치) | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-04-01 | Information Disclosure | 예약 예외 메시지 | mitigate | `ReservationExceptions.kt:13-18` — 고정 한국어 문구, id 미보간 (`@Suppress("UNUSED_PARAMETER")`) | closed |
| T-04-02 | Information Disclosure | 타인 예약 접근 응답 코드 | mitigate | `docs/error-codes.md:54` 명시 + `MemberReservationService.kt:104-108` 404 반환 (403 미사용) | closed |
| T-04-03 | Repudiation | 설계 결정 미기록 | mitigate | `docs/decisions.md:676-735` D-089~D-098 (플랜의 D-085~094에서 번호 재조정, 04-01-SUMMARY에 공개) | closed |
| T-04-04 | Tampering | error-codes.md ↔ ErrorCode 불일치 | mitigate | `ErrorCodeRegistryTest.kt:44` `targetSectionPrefixes`에 `"## 시간표·예약 코드"` 추가 — 빌드에서 양방향 강제 | closed |
| T-04-05 | Tampering | 정원 초과 삽입 | mitigate | `V6:54-56` `ck_class_session_reserved_count` + `V6:103-104` `ux_reservation_lesson_slot_active` | closed |
| T-04-06 | Tampering | 같은 회원 동시각 중복 예약 | mitigate | `V6:106-107` `ux_reservation_member_timeslot_active` 부분 유니크 인덱스 | closed |
| T-04-07 | Repudiation | 주체 없는 CANCELED 예약 | mitigate | `V6:90-96` `ck_reservation_cancellation` — 주체 정확히 1개 강제 | closed |
| T-04-08 | Repudiation | `PassTransaction` 주체 누락/중복 | mitigate | `V8:17-20` `ck_pass_transaction_subject` | closed |
| T-04-09 | Information Disclosure | `notification` 비정규화 회원명 | **accept** | `V6:115-135` 확인 — 전화번호·카카오 id 컬럼 없음, 수신자 컬럼 자체가 없다(관리자 전용 설계) | closed |
| T-04-10 | Tampering | 조건부 UPDATE WHERE 절 누락 | mitigate | `ClassSessionRepository.kt:71-79` — 정원·휴강 조건이 같은 WHERE에 | closed |
| T-04-11 | Tampering | 판정 메서드에 대입문 혼입 | mitigate | `ClassSession.kt:75,84`·`Reservation.kt:100,109` — 비교만 존재, 대입문 grep 0건 | closed |
| T-04-12 | Information Disclosure | 소유자 조건 없는 `findById` 사용 | mitigate | `ReservationRepository.kt:122-131` KDoc + 회원 경로 3곳 전부 `findByIdAndMemberId` | closed |
| T-04-13 | Repudiation | `Notification` 사후 수정 | mitigate | `Notification.kt:38-66` — 표시·연결 필드 전부 `val`, `isRead`/`readAt`만 `var` | closed |
| T-04-14 | Tampering | 임의 `classDate`로 타 주 예약 | mitigate | `ReservationWindow.kt:45-56` + `ReservationWindowTest.kt:66,81` (다음 주·지난 주 거부) | closed |
| T-04-15 | Tampering | 이미 시작된 수업 예약 | mitigate | `ReservationWindow.kt:53` `isAfter(now)` 엄격 비교 + `ReservationWindowTest.kt:56` (시작 정각 거부) | closed |
| T-04-16 | Information Disclosure | 예외 메시지에 요청 값 반사 | mitigate | `ReservationWindow.kt:51,54,67` — 정적 한국어 문구만 | closed |
| T-04-17 | Tampering | 임의 `weekStart` 탐색 | mitigate | `ReservationWindow.kt:62-69` `assertViewable` (월요일 + 2주 범위) | closed |
| T-04-18 | Information Disclosure | 시간표 셀에 예약자 명단 | mitigate | `ScheduleCellResponse.kt:17-29` — 명단·회원명 필드 없음 (D-096) | closed |
| T-04-19 | Information Disclosure | 조회 API의 창 검증 누락 | mitigate | `ScheduleService.kt:60` `assertViewable` 호출 | closed |
| T-04-20 | Elevation of Privilege | 비활성 회원 시간표 조회 | mitigate | `MemberScheduleController.kt:40` `memberStateGate.requireActive` (D-040) | closed |
| T-04-21 | Denial of Service | 셀별 개별 쿼리(182 쿼리) | mitigate | `ScheduleService.kt:67-79` — 배치 조회 각 1회, 루프 밖 | closed |
| T-04-22 | Tampering | 동시 세션 생성 중복 행 | mitigate | `ClassSessionRepository.kt:38-47` `ON CONFLICT DO NOTHING` + `ClassSessionConcurrencyTest.kt:74` (스레드 20 → 행 1) | closed |

### 04-06 ~ 04-10 (이용권 선택·예약 생성·동시성·취소/변경)

| Threat ID | Category | Component | Disposition | Mitigation (검증된 위치) | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-04-23 | Tampering | 만료 임박 이용권으로 미래 선점 | mitigate | `PassRepository.kt:134` `endDate >= :classDate` + `PassDeductionCandidateTest.kt:106` | closed |
| T-04-24 | Tampering | 여러 이용권 잔여 합산 우회 | mitigate | `PassRepository.kt:134` 단일 행 기준 + `PassDeductionCandidateTest.kt:75` (0.5 두 장 → 0건) | closed |
| T-04-25 | Elevation of Privilege | 타 회원 이용권 차감 | mitigate | `PassRepository.kt:132` `p.member.id = :memberId` + 테스트 `:169` | closed |
| T-04-26 | Tampering | 등록 취소 이용권 재사용 | mitigate | `PassRepository.kt:133` `status = ACTIVE` + 테스트 `:118` | closed |
| T-04-27 | Tampering | 임의 세션 id로 타 지점 예약 | mitigate | `ReserveRequest.kt:18-24` 세션 id 미수신 + `MemberReservationService.kt:70` 지점 일치 검사 | closed |
| T-04-28 | Tampering | 요일 불일치 조합 | mitigate | `MemberReservationService.kt:70` `dayOfWeek` 일치 검사 | closed |
| T-04-29 | Tampering | 정원 사전검사↔커밋 레이스 | mitigate | `ClassSessionRepository.kt:77-79` 조건부 UPDATE + `V6:100-107` 부분 유니크 인덱스 2차 방어 | closed |
| T-04-30 | Repudiation | 이력 없는 차감 | mitigate | `ReservationLedgerSupport.kt:109-151` 동일 트랜잭션 + 실패 케이스 불변 단언 | closed |
| T-04-31 | Repudiation | 회원 셀프 예약을 관리자 주체로 기록 | mitigate | `ReservationLedgerSupport.kt:147-148` + `V8:17-20` CHECK | closed |
| T-04-32 | Information Disclosure | 응답 DTO에 타 회원 정보 | mitigate | `ReservationResponse.kt:17-26` — 회원 필드 없음 | closed |
| T-04-33 | Denial of Service | 0행 시 무한 재시도 | mitigate | `ReservationLedgerSupport.kt:109-111` 즉시 예외, 재시도 루프 grep 0건 | closed |
| T-04-34 | Elevation of Privilege | 비활성 회원 예약 | mitigate | `MemberReservationController.kt:61,77,93,106` + `MemberReservationControllerTest.kt:262` | closed |
| T-04-35 | Elevation of Privilege | 관리자 토큰으로 회원 API 호출 | mitigate | `SecurityConfig.kt:72-73` `hasRole("MEMBER")` + 403 테스트 `:292` | closed |
| T-04-36 | Tampering | 레이스 악용 초과 예약 | mitigate | `ReservationCapacityConcurrencyTest.kt:142-269` 시나리오 A(20→10)·B(10→1) | closed |
| T-04-37 | Tampering | 동시 요청 이용권 이중 차감 | mitigate | 시나리오 C `:274-329` — 10요청 중 1성공, 잔여 정확히 1회 감소 | closed |
| T-04-38 | Repudiation | 예약↔이력 불일치 | mitigate | 3개 시나리오 전부 4자 일치 단언 (`:197-201,266-268,323-325`) | closed |
| T-04-39 | Information Disclosure | 409가 정원 현황 유추 | **accept** | 근거 확인 — `ScheduleCellResponse.kt:23-24`가 `capacity`/`reservedCount`를 이미 공개하므로 신규 노출 없음 | closed |
| T-04-40 | Tampering | 변경을 통한 당일 취소 우회 | mitigate | `Reservation.kt:119-125` — `assertChangeableByMember`가 `assertCancelableByMember` 선행 호출 | closed |
| T-04-41 | Tampering | 시각 기반 판정 혼입 | mitigate | `Reservation.kt:99,108,119,131` — 시그니처에 `LocalDate`만, `Clock`/`LocalDateTime` 없음 | closed |
| T-04-42 | Tampering | 등록 취소 이용권에 복구 | mitigate | `ReservationRefundPolicy.kt:37-39` `CANCELED → false` (D-059) + 테스트 `:222` | closed |
| T-04-43 | Tampering | 종류 바꾼 변경으로 타 종류 차감 | mitigate | `Reservation.kt:124` `ReservationTypeMismatchException` (D-090) | closed |
| T-04-44 | Repudiation | 복구 없는데 이력만 남음 | mitigate | `ReservationLedgerSupport.kt:213-221` — `shouldRestore`를 호출 **전에** 판정, 반환값 사후 해석 없음 | closed |
| T-04-45 | Tampering / Info Disclosure | 타인 `reservationId` (IDOR) | mitigate | `ReservationRepository.kt:124-128` + `MemberReservationControllerTest.kt:345-374` 404 단언 | closed |
| T-04-46 | Information Disclosure | 목록에 타 회원 예약 | mitigate | `ReservationSpecifications.kt:26-29` non-null + 두 회원 픽스처 테스트 `:377-406` | closed |
| T-04-47 | Tampering | 변경 경로 당일 우회 | mitigate | `Reservation.kt:123` — T-04-40과 동일 경로 | closed |
| T-04-48 | Tampering | 변경 실패 시 기존 예약 손실 | mitigate | `MemberReservationService.kt:157-215` 단일 `@Transactional` + 실패 4종 테스트가 ACTIVE 유지 단언 | closed |
| T-04-49 | Repudiation | 복구/이력 불일치 | mitigate | `ReservationLedgerSupport.kt:213-233` — 한 조건이 잔여 조정과 이력 저장을 함께 감쌈 | closed |
| T-04-50 | Elevation of Privilege | 비활성 회원 취소·변경·조회 | mitigate | 4개 엔드포인트 전부 `requireActive` + 테스트 `:431` | closed |

### 04-11 ~ 04-15 (관리자 조회·대리 조작·휴강·마감)

| Threat ID | Category | Component | Disposition | Mitigation (검증된 위치) | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-04-51 | Information Disclosure | 회원 토큰으로 명단 조회 | mitigate | `SecurityConfig.kt:66-68` `hasRole("ADMIN")` + `AdminScheduleControllerTest.kt:211` | closed |
| T-04-52 | Information Disclosure | 명단에 전화번호·이용권 정보 | mitigate | `BoardReservationResponse.kt:12-16` — 3필드 고정 | closed |
| T-04-53 | Elevation of Privilege | 미소속 지점 보드 조회 | mitigate | `AdminBranchRepository.kt:19-22` + `AdminScheduleService.kt:118-134` + 403 테스트 `:230` | closed |
| T-04-54 | Denial of Service | 셀별 개별 쿼리 | mitigate | `AdminScheduleService.kt:76-88` — 배치 3회, `member` fetch join, 루프 내 조회 0 | closed |
| T-04-55 | Information Disclosure | 회원 토큰으로 전체 예약 조회 | mitigate | `hasRole("ADMIN")` + `AdminReservationSearchTest.kt:326` | closed |
| T-04-56 | Tampering | LIKE 와일드카드 필터 우회 | mitigate | `ReservationSpecifications.kt:70,83` — 공용 `LikePatternEscaper` 재사용(재구현 아님) + `keyword="%"` 테스트 `:181` | closed |
| T-04-57 | Denial of Service | `size` 상한 없음 | mitigate | `ReservationSearchCondition.kt:31-32` `@Max(100)` + 400 테스트 `:298` | closed |
| T-04-58 | Denial of Service | 응답 매핑 N+1 | mitigate | `ReservationRepository.kt:35-36` `@EntityGraph` 4개 연관 | closed |
| T-04-59 | Information Disclosure | 목록에 전화번호 | mitigate | `AdminReservationResponse.kt:20-36` — `phoneNumber` 없음 | closed |
| T-04-60 | Elevation of Privilege | 회원 토큰으로 대리 취소 | mitigate | `hasRole("ADMIN")` + `AdminReservationCancellationTest.kt:459,516` | closed |
| T-04-61 | Elevation of Privilege | 관리자 경로 정원 우회 | mitigate | `ReservationLedgerSupport.kt:95-97` — `admin` 여부와 무관하게 정원 UPDATE 통과, 우회 분기 없음 + 테스트 `:407-431` | closed |
| T-04-62 | Repudiation | 대리 조작을 회원 주체로 기록 | mitigate | `AdminReservationService.kt:112-120,176-184` + `member == null` 단언 `:199,349` | closed |
| T-04-63 | Repudiation | 주체·복구 여부 없는 취소 | mitigate | `V6:90-95` CHECK + `cancelByAdminIfActive`가 3필드 동시 기록 | closed |
| T-04-64 | Tampering | 활성 예약 있는 이용권 등록취소 | mitigate | `AdminPassService.kt:259-262` 선행 검사 → `PASS_HAS_ACTIVE_RESERVATION` + 테스트 `:96` | closed |
| T-04-65 | Information Disclosure | 대리 취소 실패 응답의 존재 노출 | **accept** | 근거 확인 — `AdminReservationService.search`에 소유자 필터가 없어 관리자는 이미 전 예약 조회 가능 | closed |
| T-04-66 | Tampering | 휴강 중 끼어든 예약이 남음 | mitigate | `AdminScheduleService.kt:178-186` — 세션 CANCELED 전환(③)이 예약 조회(④)보다 **먼저**, 이후 예약은 `status=SCHEDULED` 조건이 거부 | closed |
| T-04-67 | Repudiation | 휴강 복구를 회원 취소와 혼동 | mitigate | `AdminScheduleService.kt:209-217` `CLASS_CANCELED_REFUND` + reason 단언 테스트 | closed |
| T-04-68 | Repudiation | 주체·사유 없는 휴강 세션 | mitigate | `V6:59-62` `ck_class_session_cancellation` + `ClassSessionRepository.kt:118-119` 3필드 동시 기록 | closed |
| T-04-69 | Denial of Service | 휴강 알림 N건 폭증 | mitigate | `AdminScheduleService.kt:231` — 루프(`:202-218`) 밖 1회 호출 (D-097) | closed |
| T-04-70 | Tampering | 휴강 해제 시 예약 자동 복원 | mitigate | `AdminScheduleService.kt:250-268` — 예약 INSERT/UPDATE 없음 + "복원되지 않음" 테스트로 고정 | closed |
| T-04-71 | Elevation of Privilege | 회원 토큰으로 휴강 처리 | mitigate | `hasRole("ADMIN")` + 403 테스트 | closed |
| T-04-72 | Tampering | `openapi.yaml` ↔ 코드 불일치 | mitigate | `docs/api/openapi.yaml` — Phase 4 신규 경로 12개 전부 존재 확인 | closed |
| T-04-73 | Information Disclosure | 회원 응답에 명단이 새는 회귀 | mitigate | `04-15-SUMMARY.md` 항목 1·8 PASS + `04-HUMAN-UAT.md` 항목 1이 실제 카카오 발급 토큰으로 재확인 | closed |
| T-04-74 | Repudiation | 이력 주체 오기록 회귀 | mitigate | `04-15-SUMMARY.md` 항목 16 — `pass_transaction` 12행 psql 직접 확인, 배타성 위반 0건 | closed |
| T-04-75 | Repudiation | 미기록 설계 변경 | mitigate | `04-15-SUMMARY.md` — "D-089~101·glossary는 구현과 일치해 변경 없음" 명시 기록 | closed |
| T-04-SC | Tampering | 외부 패키지 설치 | **accept** | 근거 확인 — `git log -- build.gradle.kts` 최신 커밋이 Phase 02(`8a89e4c`), Phase 04 커밋 0건 | closed |

*Status: open · closed*
*Disposition: mitigate (구현 필요) · accept (문서화된 위험) · transfer (제3자)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-04-01 | T-04-09 | `notification`이 회원명을 비정규화 보관하지만 수신자가 관리자뿐이고(D-097) 관리자는 이미 회원 목록 전체 열람 권한이 있다. 전화번호·카카오 id는 담지 않는다 (DDL로 확인) | 04-02-PLAN 승인 | 2026-08-08 |
| R-04-02 | T-04-39 | 정원 초과 409 응답이 "그 수업이 찼다"를 알려주지만, 같은 정보를 시간표 조회 API가 `capacity`/`reservedCount`로 이미 공개한다. 개인 식별 정보가 아니다 | 04-08-PLAN 승인 | 2026-08-08 |
| R-04-03 | T-04-65 | 대리 취소 실패 응답이 타 회원 예약 존재를 노출하지만, 관리자는 `GET /api/admin/reservations`로 이미 전 예약을 조회할 수 있어 추가 노출이 없다 | 04-13-PLAN 승인 | 2026-08-08 |
| R-04-04 | T-04-SC | 이 phase는 신규 외부 패키지를 설치하지 않는다. 설치 태스크가 생기면 slopcheck + 차단형 휴먼 체크포인트를 먼저 넣는다 | 전 PLAN 공통 | 2026-08-08 |
| R-04-05 | T-04-44 / T-04-49 (인접) | **WR-02 이월** — `ReservationLedgerSupport.kt:213` `shouldRestore`가 스냅샷 시점 `passStatus`를 쓴다. 휴강 진행 중 대상 이용권이 다른 관리자에 의해 등록취소되면 조건부 UPDATE가 0행 → `IllegalStateException` → 500 + 휴강 전체 롤백. 도달에 4중 경쟁이 필요하고 원자적 롤백이라 데이터 정합성은 깨지지 않는다. 수정 방향("라이브 상태 재조회" vs "복구 조용히 생략")이 Core Value와 맞물려 `docs/policies.md` 확인이 필요해 다음 phase로 넘긴다. 등록된 위협 T-04-44/T-04-49의 완화책(판정 선행 + 단일 조건 묶기)은 코드에 존재함을 확인했다 — WR-02는 원장 불일치가 아니라 가용성 결함이다 | 사용자 결정 (`04-HUMAN-UAT.md` 항목 2, 2026-08-08) | 2026-08-08 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-08 | 76 | 76 | 0 | gsd-security-auditor ×3 (그룹 A: T-04-01~22, B: T-04-23~50, C: T-04-51~75+SC) |

**감사 방식:** 각 감사관은 플랜의 완화 계획을 그대로 믿지 않고 구현 코드·마이그레이션 DDL·테스트 단언을
직접 읽어 file:line으로 인용했다. `accept` 4건은 "수용해도 되는가"가 아니라 **수용 근거가 사실인가**를
독립 검증했다 (예: T-04-SC는 `git log -- build.gradle.kts`로, T-04-39는 시간표 DTO 필드로).
그룹 C 감사관은 인용한 테스트 6종을 실제로 재실행해 `BUILD SUCCESSFUL`을 확인했다.

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-08-08
