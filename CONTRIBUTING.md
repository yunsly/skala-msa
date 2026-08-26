# 협업 가이드

4인 팀, 단기 프로젝트 기준으로 정한 최소한의 협업 규칙입니다. 서비스별로 나눠 병렬 작업하기 때문에, 아래 규칙만 지키면 머지 충돌과 이력 정리 비용을 크게 줄일 수 있습니다.

## 시작하기 (클론 후 최초 1회)

커밋 메시지 컨벤션을 자동으로 검사하는 훅을 활성화해주세요.

```bash
git config core.hooksPath .githooks
```

이 설정을 하지 않으면 커밋 메시지 형식이 틀려도 로컬에서 걸러지지 않습니다 (팀원 각자 저장소마다 1회 실행 필요).

## 브랜치 전략

간소화한 Git Flow를 사용합니다.

- `main`: 언제든 배포 가능한 상태만 유지합니다. `develop`에서만 머지합니다. 직접 push 금지.
- `develop`: 통합 브랜치. 모든 작업 브랜치는 여기로 PR을 보냅니다. 직접 push 금지.
- 작업 브랜치: `서비스명/타입-설명` 형식으로 만듭니다.

**서비스명**: `eureka-server`, `user-service`, `course-service`, `enrollment-service`, `payment-service`, `recommend-service`, `vue-frontend`, 여러 서비스에 걸치거나 공통 설정(docker-compose, init-db 등)이면 `infra`

**타입**: `feat`, `fix`, `refactor`, `docs`, `chore`, `test`

예시:
```
payment-service/feat-refund-api
vue-frontend/fix-router-bug
infra/chore-docker-compose-update
```

## 커밋 메시지 컨벤션

`prefix: 한글 설명` 형식을 사용합니다. (`git config core.hooksPath .githooks` 설정 시 자동으로 검사됩니다.)

| prefix | 의미 |
|---|---|
| `add` | 신규 파일/기능 추가 |
| `update` | 기존 기능 수정/개선 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변경 없는 구조 개선 |
| `docs` | 문서 변경 |
| `chore` | 빌드/설정/의존성 등 기타 |
| `test` | 테스트 코드 추가/수정 |

예시:
```
fix: 결제 취소 시 중복 요청 오류 수정
add: 강의 검색 API 추가
```

## PR 규칙

- `main`, `develop`은 PR을 통해서만 병합합니다 (직접 push 금지, GitHub 브랜치 보호 규칙으로 강제).
- **승인(approve)은 필수가 아닙니다** — 작성자 판단으로 머지 가능합니다.
- 대신 PR을 열 때 **팀원 전원을 리뷰어로 태그**해주세요. 승인 여부와 관계없이 즉시 알림이 가서, 다들 어떤 변경이 들어오는지 놓치지 않을 수 있습니다.
- PR 템플릿(변경 서비스 / 요약 / 테스트 방법)을 채워주세요. 알림 받은 팀원이 빠르게 파악할 수 있습니다.
- 머지 방식은 **Squash merge**를 사용합니다. 작업 브랜치에 임시 커밋이 많아도 `develop` 이력은 PR 단위로 깔끔하게 남습니다.
- 머지 후 브랜치는 자동 삭제됩니다.
- 알림을 놓치지 않으려면 저장소 우측 상단 **Watch → All Activity**로 설정해두는 걸 권장합니다.

## GitHub 저장소 설정 (관리자 1회 설정)

Settings → Branches → Branch protection rules에서 `main`, `develop` 각각에 대해:

- "Require a pull request before merging" ON
- "Require approvals" 는 사용하지 않음 (0 또는 옵션 OFF)
- "Automatically delete head branches" ON (Settings → General)

## 그 외

- 필요 이상으로 세분화하지 않습니다 (release/hotfix 브랜치, CODEOWNERS 등은 단기 프로젝트 규모상 생략).
- 실행/배포 방법은 [readme.md](./readme.md)를 참고하세요.
