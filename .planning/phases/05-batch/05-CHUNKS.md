# Phase 5 청크 계획 (D-084 / deliver-phase-chunk)

Phase 5는 플랜 9개 = wave 9개(각 wave 플랜 1개, 전부 순차 의존)다.
`.claude/skills/deliver-phase-chunk/SKILL.md` 절차에 따라 **3개 청크**로 끊어 납품한다.

| 청크 | wave | 브랜치 | 내용 | 상태 |
|---|---|---|---|---|
| 5a | 1–4 | `feature/phase-05a-batch-foundation` | 문서 확정 · V9 스키마 · 판정 순수 계산기 · 벌크 조회 | PR #13 |
| 5b | 5–7 | `feature/phase-05b-batch-deduction` | 차감 서비스 · 러너 · 멱등/만료 실증 | 대기 |
| 5c | 8–9 | `feature/phase-05c-batch-trigger` | `@Scheduled` cron · 관리자 수동 실행 API · openapi 재생성 · phase 마감 | 대기 |

## 경계를 이렇게 잡은 이유

- **5a는 아무것도 차감하지 않는다** — 읽기·계산만 있어 잘못 머지돼도 회원 잔여가 변하지 않는다
- **5b는 차감(05-05·06)과 멱등성 실증(05-07)을 반드시 함께 낸다** — 나누면 *두 번 실행하면 두 번 깎이는지 아무도 확인하지 않은 차감 코드*가 dev에 남고, 5c가 그 전제를 밟는다
- **5c는 엔드포인트와 `openapi.yaml` 재생성을 함께 낸다** — FE 계약과 코드가 갈라진 상태를 dev에 남기지 않는다

## 다음 청크 실행 절차

이전 청크 PR이 **dev에 머지된 뒤**에 시작한다 (청크 병렬 금지 — 둘 다 같은 Flyway 버전을 만들면
커밋된 마이그레이션은 수정 불가라 새 버전을 또 추가해야만 풀린다).

```bash
git switch dev && git pull --ff-only
git symbolic-ref --short refs/remotes/origin/HEAD   # → origin/dev 여야 한다
git switch -c feature/phase-05b-batch-deduction
```

그다음 청크에 속한 wave를 **하나씩 순서대로** 실행한다:

```
/gsd-execute-phase 5 --wave 5
/gsd-execute-phase 5 --wave 6
/gsd-execute-phase 5 --wave 7
```

`--wave` 없이 `/gsd-execute-phase 5`를 치면 남은 플랜을 전부 실행해 청크 분할이 무너진다.

마지막 wave가 끝나면 승인을 기다리지 않고 `./gradlew ktlintFormat` → `./gradlew build` →
`create-pr` 스킬로 PR 생성(base: dev)까지 한다. **머지 버튼은 사용자가 누른다.**

## 청크 진행 중 유지할 것

- `.planning/REQUIREMENTS.md`의 BATCH-01~04 **Complete 전환은 05-09(청크 5c)가 소유**한다.
  중간 플랜에서 `requirements.mark-complete`를 실행하지 않는다 — 5a 실행 중 두 번 발생해 되돌렸다
  (`66d2a11`, 그리고 05-02 실행 중 자체 정정).
- phase 전체 검증(`05-VERIFICATION.md`)·완료 처리는 **마지막 청크(5c)가 끝난 뒤**에만 돌린다.
