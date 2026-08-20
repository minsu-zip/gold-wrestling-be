---
name: add-endpoint
description: "REST API 엔드포인트를 추가·변경할 때의 절차. openapi.yaml 재생성까지 포함. API 관련 작업이면 항상 이 절차를 따른다."
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash
  - Grep
  - Glob
---

# 엔드포인트 추가·변경 절차

## 0. 시작 전 확인

- [ ] `docs/glossary.md` — 다루는 개념의 코드 네이밍 확인. 없는 개념이면 **glossary에 먼저 추가**
- [ ] `docs/policies.md` — 이 엔드포인트가 강제해야 하는 도메인 규칙 확인 (정원, 당일 취소 불가, 잔여 부족 등)
- [ ] `docs/conventions.md` §6 — DTO·API 규약

## 1. 경로·메서드

- `/api/<kebab-case-복수형>` (예: `/api/reservations`, `/api/session-passes`)
- 관리자 전용은 `/api/admin/...`
- 상태 변경은 POST/PATCH/DELETE. 조회는 GET (GET에서 상태를 바꾸지 않는다)

## 2. DTO

- 기능 패키지의 `dto/`에 요청·응답 DTO를 만든다. **엔티티를 노출하지 않는다** (D-019)
- 이름: `<동작><대상>Request` / `<대상>Response`
- 요청 DTO에 `jakarta.validation` 으로 **형식** 검증만 (`@NotNull`, `@Positive`, `@Size`)
- **도메인 규칙 검증은 DTO가 아니라 서비스·엔티티에서** (정원·잔여 횟수·기간은 형식 문제가 아니다)
- 응답 변환은 `companion object`의 `from(entity)` 로

## 3. 컨트롤러

- `@RestController` + 생성자 주입. `@Transactional` 붙이지 않는다
- `@Operation(summary = ...)`, 필요하면 `@Schema(description = ...)` — FE가 이 스펙만 보고 작업한다
- 인증 주체가 필요하면 인증 phase에서 만든 방식을 따른다 (직접 새 방식을 만들지 않는다)
- `try-catch`로 에러 응답을 만들지 않는다 → 도메인 예외를 던지고 전역 핸들러가 `ProblemDetail`로 변환 (D-017)

## 4. 서비스

- 클래스에 `@Transactional(readOnly = true)`, 변경 메서드에만 `@Transactional` (D-020)
- 비즈니스 규칙은 가능하면 엔티티 메서드로 내린다 (`pass.deduct(...)`)
- **잔여 횟수를 바꾸면 반드시 `PassTransaction` 이력을 같은 트랜잭션에서 남긴다** (CLAUDE.md 규칙 6)

## 5. 테스트 (같은 작업 안에서 — 나중으로 미루지 않는다)

`docs/conventions.md` §10.0 표가 기준이다. 이 작업에서는 최소한:

- [ ] 도메인 규칙 **단위테스트** (스프링 없이) — 잔여 부족·정원 초과·당일 취소 같은 판정
- [ ] 엔드포인트 **통합테스트** — 성공 경로 + 대표 실패 경로(4xx) 각 1개 이상.
      기존 통합테스트와 **같은 애노테이션 조합**을 쓴다 (컨텍스트 캐시 재사용)
- [ ] 커스텀 쿼리를 추가했다면 그 쿼리에 대한 통합테스트
- [ ] 정원·1:1 슬롯처럼 경쟁이 있으면 **동시성 테스트** (`add-domain-test` 스킬 §4)
- [ ] `./gradlew ktlintFormat` 후 `./gradlew build` 실제 실행 — 포맷·컴파일·테스트 한 번에 확인

DTO 필드만 추가한 변경이면 테스트가 필요 없다 — 대신 **완료 보고에 그 이유를 한 줄로 밝힌다**.

## 6. openapi.yaml 재생성 (건너뛰지 말 것)

```bash
docker compose up -d
./gradlew generateApiDocs
```

앱을 따로 띄우거나 포트를 신경 쓸 필요가 없다(태스크가 8099에서 기동·정리한다).
이 명령은 앱 기동을 포함하므로 최대 1분가량 걸린다 — 정상이다.

- [ ] `git diff docs/api/openapi.yaml` 로 의도한 변경만 들어갔는지 확인
- [ ] `servers:`가 `/` 로 유지되는지 확인 (환경 정보가 박히면 안 된다)

## 7. 커밋

- `feat: ...` 커밋에 **코드 + 테스트 + openapi.yaml 을 함께** 담는다
- API 형태를 새로 정한 것이 있으면 `docs/decisions.md`에 기록
