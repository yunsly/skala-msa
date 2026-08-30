# enrollment-service / payment-service API 명세서

> 대상 코드베이스: `skala-msa-develop` (KeyNexus Credential 거버넌스)
> 비교 기준(베이스라인): `msa-lecture` (온라인 강의 수강신청·결제 템플릿)
> 작성일: 2026-08-30

---

## 0. 한 줄 요약 — 무엇이 바뀌었나

`msa-lecture`는 **"수강생이 강의를 수강신청하고 99,000원을 결제하면 수강이 활성화"** 되는 흐름이었다.
`skala-msa-develop`은 물리 테이블명(`enrollments`, `payments`)만 유지한 채 도메인을 완전히 갈아엎어
**"사원(MEMBER)이 사내 프로젝트 접근 권한을 신청 → 프로젝트 리더(LEADER)가 승인/거절/회수 → 승인 시 프로젝트 내 모든 Credential 자산(Secret) 열람 가능"** 하는 **접근 통제 + 감사(Audit) 워크플로우**로 재정의되었다.

| 개념 | msa-lecture | skala-msa-develop |
|---|---|---|
| enrollment 대상 | `courseId` (강의) | `projectId` (사내 프로젝트) + `reason` (신청 사유) |
| payment 의미 | PG 결제 (금액 `amount`) | 리더의 **접근 승인 티켓** (금액 개념 삭제) |
| 승인 처리 | `POST /internal/request` 시 **자동 즉시 COMPLETED** | `POST /internal/request` 는 **PENDING 티켓만 생성**, 사람이 `approve`/`reject` |
| 상태 전이 트리거 | payment.completed 1개 이벤트 | payment.completed / payment.rejected / payment.revoked 3개 이벤트 |
| 사용자 역할 | STUDENT / INSTRUCTOR | MEMBER / LEADER / ADMIN |
| 신규 테이블 | – | `projects`, `credential_audit_logs` |
| payment-service 인증 | `permitAll` (인증 없음) | **OAuth2 JWT 리소스 서버 인증 활성화** |

---

## 1. 공통 규약

### 1.1 게이트웨이 & 인증

- 모든 외부 요청은 API Gateway(`:8080`)를 경유한다. 게이트웨이가 JWT를 검증하고 하위 서비스로 다음 헤더를 주입한다.
  - `X-User-Id`: JWT `sub`(사용자 PK). enrollment 신청자·조회 기준·승인자 식별에 사용.
  - `Authorization: Bearer <JWT>`: payment-service가 course-service(`/api/courses/projects`) 호출 시 그대로 포워딩.
- 서비스 → 서비스 내부 호출은 auth-server의 **client_credentials** 토큰(`scope=service.read`)을 Bearer로 사용한다.
- 직접 포트(enrollment `:8083`, payment `:8084`)는 개발용.

### 1.2 서비스별 Security 설정 (중요 변경점)

| 서비스 | msa-lecture | skala-msa-develop |
|---|---|---|
| enrollment-service | `anyRequest().permitAll()` | `anyRequest().permitAll()` **(변경 없음 — 게이트웨이 신뢰 모델)** |
| payment-service | `anyRequest().permitAll()` | `oauth2ResourceServer().jwt()` + 아래 규칙 |

payment-service 인가 규칙 (`SecurityConfig`):
| 경로 | 요구 권한 |
|---|---|
| `/api-docs/**`, `/swagger-ui/**`, `/actuator/health`, `/actuator/info` | permitAll |
| `GET /api/payments/internal/audit-logs/credentials/*/denied-count` | 인증된 사용자 (`authenticated`) |
| `/api/payments/internal/**` | `SCOPE_service.read` (서비스 토큰) |
| 그 외 전부 | `authenticated` (사용자 JWT) |

### 1.3 공통 응답 래퍼

대부분의 사용자용 엔드포인트는 아래 래퍼로 감싼다. (internal 엔드포인트 일부는 래퍼 없이 raw 반환 — 각 항목에 표기)

```json
{ "success": true, "message": "성공", "data": { ... } }
```

에러 시:
```json
{ "success": false, "message": "<에러 메시지>", "data": null }
```

### 1.4 공통 에러 코드

| 상태 | enrollment-service | payment-service |
|---|---|---|
| 400 | `IllegalArgumentException`(도메인 규칙 위반), 검증 실패, 필수 헤더 누락 | `IllegalArgumentException`, 검증 실패, `ConstraintViolationException`, 잘못된 JSON |
| 401 | – | JWT 없음/무효 (인증 필요한 경로) |
| 403 | – | `AccessDeniedException` (리더/ADMIN 권한 부족), `SCOPE_service.read` 없음 |
| 503 | `RuntimeException` (하위 서비스 연결 실패) | – |
| 500 | 그 외 | 그 외 |

---

## 2. 변경 요약 표

### 2.1 enrollment-service 엔드포인트

| 메서드 & 경로 | 상태 | 비고 |
|---|---|---|
| `POST /api/enrollments` | 수정 | `courseId`→`projectId`, `reason` 추가, 재신청(reapply) 로직 추가, 결제금액 제거 |
| `GET /api/enrollments/my` | 유지 | 응답 DTO 필드 변경 (course 요약 제거, project/상태 필드 추가) |
| `GET /api/enrollments/my-projects` | 🆕 신규 (핵심) | ACTIVE/PENDING/CANCELLED 3분류 조회 |
| `GET /api/enrollments/user/{userId}` | 유지 | 응답 DTO 필드 변경 |
| `GET /api/enrollments/internal/history/{userId}` | 수정 | `activeCourseIds`→`activeProjectIds` |
| `GET /api/enrollments/internal/{enrollmentId}` | 🆕 신규 | payment-service가 신청 사유 조회용 |
| `GET /api/enrollments/internal/projects/{projectId}/active-count` | 🆕 신규 | course-service Seat(`enrollment_count`) 계산용 |
| `PATCH /api/enrollments/internal/{userId}/{projectId}/access` | 🆕 신규 | Secret 평문 조회 직후 `last_accessed_at` 갱신 |

### 2.2 payment-service 엔드포인트

| 메서드 & 경로 | 상태 | 비고 |
|---|---|---|
| `POST /api/payments/internal/request` | 수정 (핵심) | `{userId,courseId,amount}`→`{enrollmentId,userId,projectId}`, **자동 완료 제거 → PENDING 티켓만 생성** |
| `GET /api/payments/pending` | 🆕 신규 (핵심) | 리더 본인 프로젝트의 승인 대기 목록 |
| `GET /api/payments/active` | 🆕 신규 | 활성(COMPLETED) 부여 목록 (LEADER는 본인 프로젝트 / ADMIN은 전체) |
| `POST /api/payments/{id}/approve` | 🆕 신규 (핵심) | 리더 승인 → 감사 티켓 UUID 발급 → `payment.completed` 발행 |
| `POST /api/payments/{id}/reject` | 🆕 신규 (핵심) | 리더 거절 → `payment.rejected` 발행 |
| `POST /api/payments/{id}/revoke` | 🆕 신규 | 권한 회수 → `payment.revoked` 발행 + 감사 로그 적재 |
| `GET /api/payments/{id}` | 유지 | 응답 필드 변경 (amount 제거, enrollmentId/projectId/approvedBy/decisionReason 추가) |
| `GET /api/payments/user/{userId}` | 유지 | 응답 필드 변경 |
| `POST /api/payments/internal/audit-logs` | 🆕 신규 | 감사 이벤트 적재 (course-service의 Secret 조회 거절 등) |
| `GET /api/payments/audit-logs/projects/{projectId}` | 🆕 신규 | 프로젝트 감사 이력 조회 |
| `GET /api/payments/internal/audit-logs/credentials/{credentialId}/denied-count` | 🆕 신규 | recommend-service 위험도 분석용 DENIED 횟수 집계 |

> `msa-lecture`의 `PaymentDto.PaymentRequest`(외부 클라이언트용 `{courseId, amount}`)는 실제 노출 엔드포인트가 없던 미사용 DTO였고, skala에서는 완전히 제거됨.

---

## 3. enrollment-service API 상세

Base URL(gateway): `http://<gateway>/` · 직접: `http://enrollment-service:8083`
포트 `8083` · DB `lecture_db` · Kafka consumer group `enrollment-service`

---

### 3.1 `POST /api/enrollments` — 프로젝트 접근 권한 신청 【수정 · 핵심】

프로젝트 단위 접근 권한을 신청한다. 이미 신청/활성 상태면 차단, 이전에 회수(CANCELLED)됐던 건이면 재신청 처리한다.

| 항목 | 내용 |
|---|---|
| 인증 | 사용자 JWT (게이트웨이가 `X-User-Id` 주입) |
| 헤더 | `X-User-Id: <Long>` (필수) |

요청 본문:
```json
{ "projectId": 1, "reason": "결제 MSA 프로젝트 개발 참여" }
```
| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `projectId` | Long | ✅ (`@NotNull`) | 접근할 대상 프로젝트(`projects.id`) |
| `reason` | String | ❌ | 신청 사유 (`enrollments.reason`, TEXT) |

처리 흐름:
1. `CourseServiceClient.existsProject(projectId)` — course-service `GET /api/courses/internal/projects/{projectId}/exists` (서비스 토큰) 호출로 프로젝트 존재 확인. 없으면 `400`.
2. `(user_id, project_id)` 기존 enrollment 조회
   - 없음 → `EnrollmentWriteService.createPendingEnrollment` (신규 트랜잭션, `REQUIRES_NEW`) → `PENDING` 로 INSERT
   - 있음 & 상태 ≠ `CANCELLED` → `400` "이미 접근 신청했거나 활성화된 프로젝트입니다."
   - 있음 & 상태 = `CANCELLED` → `reapply(reason)` → 상태 `PENDING` 으로 복귀 (UPDATE)
3. `PaymentServiceClient.requestApproval(enrollmentId, userId, projectId)` — payment-service `POST /api/payments/internal/request` (서비스 토큰) 호출 → PENDING 승인 티켓 생성

성공 응답 `201 Created`:
```json
{
  "success": true,
  "message": "성공",
  "data": {
    "id": 10,
    "userId": 5,
    "projectId": 1,
    "reason": "결제 MSA 프로젝트 개발 참여",
    "status": "PENDING",
    "lastAccessedAt": null,
    "createdAt": "2026-08-30T10:00:00",
    "updatedAt": "2026-08-30T10:00:00"
  }
}
```

DB 영향: `enrollments` 1행 INSERT(신규) 또는 UPDATE(재신청) · `payments` 1행 INSERT(PENDING)

에러:
| 상태 | 상황 |
|---|---|
| 400 | `projectId` 누락, 존재하지 않는 프로젝트, 중복 신청, `X-User-Id` 헤더 누락 |
| 503 | course-service / payment-service 연결 실패 |

베이스라인 대비 차이: `msa-lecture`는 `{courseId}`만 받고 `existsByUserIdAndCourseId` 로 무조건 중복 차단, `BigDecimal 99000` 고정 결제 요청. 재신청 개념 없음.

---

### 3.2 `GET /api/enrollments/my` — 내 신청/멤버십 전체 목록 【유지 · 응답 변경】

| 항목 | 내용 |
|---|---|
| 인증 | 사용자 JWT · 헤더 `X-User-Id` (필수) |

`user_id` 기준 모든 enrollment(상태 불문)를 반환. `msa-lecture`와 달리 course-service를 호출해 강의 요약을 붙이지 않는다(단순 목록).

응답 `200`:
```json
{
  "success": true, "message": "성공",
  "data": [
    { "id": 10, "userId": 5, "projectId": 1, "reason": "...", "status": "ACTIVE",
      "lastAccessedAt": "2026-08-30T11:00:00", "createdAt": "...", "updatedAt": "..." }
  ]
}
```

---

### 3.3 `GET /api/enrollments/my-projects` — 내 프로젝트 (상태별 분리) 【신규 · 핵심】 (FR-03-02)

프론트 "My Projects" 화면용. 조회 기준은 항상 `X-User-Id` (타인 신청 내역 노출 불가).

| 항목 | 내용 |
|---|---|
| 인증 | 사용자 JWT · 헤더 `X-User-Id` (필수, 없으면 `400`) |

응답 `200`:
```json
{
  "success": true, "message": "성공",
  "data": {
    "userId": 5,
    "activeProjects":   [ { "id": 10, "userId": 5, "projectId": 1, "status": "ACTIVE",  ... } ],
    "pendingProjects":  [ { "id": 11, "userId": 5, "projectId": 2, "status": "PENDING", ... } ],
    "cancelledProjects":[ { "id": 12, "userId": 5, "projectId": 3, "status": "CANCELLED", ... } ]
  }
}
```
- `activeProjects`: 승인 완료(멤버십 유효) — 이 프로젝트들의 자산/Secret 열람 가능
- `pendingProjects`: 리더 승인 대기
- `cancelledProjects`: 거절되었거나 회수된 건 (재신청 가능)

DB 영향: 없음 (읽기)

---

### 3.4 `GET /api/enrollments/user/{userId}` — 특정 사용자 신청 목록 【유지 · 응답 변경】

- 인증: 사용자 JWT (경로 파라미터로 대상 지정)
- Path: `userId: Long`

`3.2`와 동일한 리스트 형태 응답. 내부적으로 `getEnrollmentsByUser(userId)` 재사용.

---

### 3.5 `GET /api/enrollments/internal/history/{userId}` — 활성 프로젝트 ID 목록 【수정】

recommend-service / course-service 가 사용자의 활성 멤버십을 조회.

| 항목 | 내용 |
|---|---|
| 인증 | 서비스 토큰 (Bearer client_credentials) |
| Path | `userId: Long` |
| 응답 래퍼 | ❌ raw JSON |

응답 `200`:
```json
{ "userId": 5, "activeProjectIds": [1, 4, 7] }
```
베이스라인 차이: 필드명 `activeCourseIds` → `activeProjectIds` (상태 `ACTIVE` 필터는 동일).

---

### 3.6 `GET /api/enrollments/internal/{enrollmentId}` — 승인 티켓 보강용 단건 조회 【신규】

payment-service `GET /api/payments/pending` 가 신청 사유(`reason`)를 붙이기 위해 호출.

| 항목 | 내용 |
|---|---|
| 인증 | 서비스 토큰 (best-effort — 실패해도 승인 목록은 반환됨) |
| Path | `enrollmentId: Long` |
| 응답 래퍼 | ❌ raw `EnrollmentResponse` |

응답 `200`:
```json
{ "id": 10, "userId": 5, "projectId": 1, "reason": "결제 모듈 개발 참여",
  "status": "PENDING", "lastAccessedAt": null, "createdAt": "...", "updatedAt": "..." }
```
에러: `400` — 존재하지 않는 enrollmentId

---

### 3.7 `GET /api/enrollments/internal/projects/{projectId}/active-count` — 프로젝트 활성 멤버 수 【신규】

course-service가 자산의 `enrollment_count`(Seat) 를 동기화할 때 사용.

| 항목 | 내용 |
|---|---|
| 인증 | 서비스 토큰 |
| Path | `projectId: Long` |
| 응답 래퍼 | ❌ raw |

응답 `200`: `3`  (숫자 그대로 — `countByProjectIdAndStatus(projectId, ACTIVE)`)

---

### 3.8 `PATCH /api/enrollments/internal/{userId}/{projectId}/access` — 최근 접근 시각 갱신 【신규】 (FR-03-04)

course-service `GET /api/courses/{id}/secret` 이 Secret 평문 조회를 **허용한 직후** 호출. 감사/거버넌스용 `last_accessed_at` 기록.

| 항목 | 내용 |
|---|---|
| 인증 | 서비스 토큰 |
| Path | `userId: Long`, `projectId: Long` |
| 본문 | 없음 |

처리: `(user_id, project_id)` enrollment 가 있으면 `last_accessed_at = now()` UPDATE. 없으면(리더/ADMIN 등 enrollment 없이 접근 가능한 경우) **조용히 무시**.

응답 `204 No Content` (본문 없음)

DB 영향: `enrollments.last_accessed_at` UPDATE (해당 행 존재 시)

---

## 4. payment-service API 상세

포트 `8084` · DB `lecture_db` · Kafka producer 토픽 `payment.completed` / `payment.rejected` / `payment.revoked`

승인 티켓(`payments`) 상태 머신:
```
                approve()                          revoke()
  PENDING  ───────────────►  COMPLETED  ──────────────────►  CANCELLED
     │                       (감사 티켓 UUID 발급)
     │  reject()
     └────────────►  FAILED
```

---

### 4.1 `POST /api/payments/internal/request` — 승인 대기 티켓 생성 【수정 · 핵심】

enrollment-service가 접근 신청 직후 호출. **결제/PG/자동승인 없음** — `PENDING` 티켓만 만든다.

| 항목 | 내용 |
|---|---|
| 인증 | 서비스 토큰 — `SCOPE_service.read` (없으면 `403`) |

요청 본문:
```json
{ "enrollmentId": 10, "userId": 5, "projectId": 1 }
```
| 필드 | 타입 | 필수 |
|---|---|---|
| `enrollmentId` | Long | ✅ `@NotNull` |
| `userId` | Long | ✅ `@NotNull` |
| `projectId` | Long | ✅ `@NotNull` |

응답 `200`:
```json
{ "success": true, "message": "성공", "data": { "paymentId": 100, "status": "PENDING" } }
```

DB 영향: `payments` 1행 INSERT — `status=PENDING`, `enrollment_id/user_id/project_id` 세팅, `transaction_id=NULL`, `approved_by=NULL`

베이스라인 차이: `msa-lecture`는 `{userId,courseId,amount}` 를 받아 즉시 `complete(UUID)` → `status=COMPLETED` 로 저장하고 `payment.completed` 를 바로 발행했다. skala는 이 자동 완료 로직을 **전부 제거**하고 사람 승인 단계로 분리.

---

### 4.2 `GET /api/payments/pending` — 리더 승인 대기 목록 【신규 · 핵심】 (FR-04-01)

로그인한 리더가 **소유한 프로젝트**로 들어온 `PENDING` 승인 티켓 목록.

| 항목 | 내용 |
|---|---|
| 인증 | 사용자 JWT (`authenticated`) |
| 헤더 | `X-User-Id: <Long>` (승인자), `Authorization: Bearer <JWT>` (course-service 포워딩용, optional이지만 없으면 소유 프로젝트 0건) |

처리:
1. `ApprovalContextClient.getOwnedProjects(approverId, authHeader)` — course-service `GET /api/courses/projects` 호출 → `ownerId == approverId` 인 프로젝트만 필터
2. 소유 프로젝트가 없으면 빈 배열
3. `payments` 에서 `status=PENDING AND project_id IN (소유 프로젝트들)` 을 `created_at ASC` 로 조회
4. 각 건에 `projectName`(course-service), `reason`(enrollment-service `GET /internal/{enrollmentId}`) 을 best-effort 보강. `userName` 은 현재 항상 `null` (user-service가 ADMIN만 타 사용자 조회 허용 + payment에 서비스 토큰 인프라 없음 → 프론트 fallback).

응답 `200`:
```json
{
  "success": true, "message": "성공",
  "data": [
    { "id": 100, "enrollmentId": 10, "projectId": 1, "projectName": "결제 MSA",
      "userId": 5, "userName": null, "reason": "결제 모듈 개발 참여",
      "createdAt": "2026-08-30T10:00:00" }
  ]
}
```

DB 영향: 없음

---

### 4.3 `GET /api/payments/active` — 활성 부여 목록 【신규】

현재 유효한(멤버십 `COMPLETED`) 접근 부여 목록. 자산 회수 대상 파악용.

| 항목 | 내용 |
|---|---|
| 인증 | 사용자 JWT · 헤더 `X-User-Id`, `Authorization` (둘 다 필수) |
| 권한 | `LEADER` (본인 소유 프로젝트만) · `ADMIN` (전체 프로젝트) · 그 외 `403` |

처리: `ApprovalContextClient.getUserRole(approverId)` — user-service `GET /api/users/internal/{id}` 로 역할 조회 후 분기.
- ADMIN: `payments WHERE status=COMPLETED ORDER BY updated_at DESC` 전체
- LEADER: 소유 프로젝트로 필터
- else: `AccessDeniedException` → `403`

응답 `200`:
```json
{
  "success": true, "message": "성공",
  "data": [
    { "id": 100, "enrollmentId": 10, "projectId": 1, "projectName": "결제 MSA",
      "userId": 5, "userName": null,
      "transactionId": "9f1c...uuid", "approvedAt": "2026-08-30T10:05:00" }
  ]
}
```

---

### 4.4 `POST /api/payments/{id}/approve` — 접근 승인 【신규 · 핵심】 (FR-04-01, FR-04-02)

| 항목 | 내용 |
|---|---|
| 인증 | 사용자 JWT · 헤더 `X-User-Id: <Long>` (승인자) |
| Path | `id: Long` (payment/티켓 ID) |
| 본문 | (optional) `{ "decisionReason": "리뷰 완료, 승인" }` |

> ⚠️ 현재 구현은 승인자가 해당 프로젝트의 리더인지 **컨트롤러/서비스에서 직접 검증하지 않는다** (revoke만 검증). 티켓이 `PENDING` 이 아니면 `400`.

처리:
1. 티켓 조회 → `PENDING` 아니면 `400` "PENDING 승인 티켓만 처리할 수 있습니다."
2. `payment.approve(approverId, UUID, reason)` → `status=COMPLETED`, `approved_by=approverId`, `transaction_id=<UUID>`, `decision_reason=reason`
3. Kafka `payment.completed` 발행 (아래 6.1)

응답 `200`:
```json
{
  "success": true, "message": "성공",
  "data": {
    "paymentId": 100, "enrollmentId": 10, "userId": 5, "projectId": 1,
    "approvedBy": 2, "status": "COMPLETED",
    "transactionId": "9f1c2d...uuid", "decisionReason": "리뷰 완료, 승인",
    "createdAt": "...", "updatedAt": "..."
  }
}
```

DB 영향: `payments` 해당 행 UPDATE (`status`, `approved_by`, `transaction_id`, `decision_reason`, `updated_at`)
→ (비동기) `enrollments.status` `PENDING→ACTIVE`, `courses.enrollment_count` +1

---

### 4.5 `POST /api/payments/{id}/reject` — 접근 거절 【신규 · 핵심】

| 항목 | 내용 |
|---|---|
| 인증 | 사용자 JWT · 헤더 `X-User-Id` |
| Path | `id: Long` |
| 본문 | (optional) `{ "decisionReason": "권한 범위 초과" }` |

처리: `PENDING` 검증 → `payment.reject(approverId, reason)` → `status=FAILED`, `approved_by`, `decision_reason` 세팅 → Kafka `payment.rejected` 발행.

응답 `200`: `data.status = "FAILED"` (구조는 4.4와 동일, `transactionId`는 `null`)

DB 영향: `payments` 행 UPDATE (`status=FAILED`, `approved_by`, `decision_reason`)
→ (비동기) `enrollments.status` `PENDING→CANCELLED`

---

### 4.6 `POST /api/payments/{id}/revoke` — 접근 권한 회수 【신규】 (FR-03-05)

이미 승인된(`COMPLETED`) 접근을 회수한다.

| 항목 | 내용 |
|---|---|
| 인증 | 사용자 JWT · 헤더 `X-User-Id: <Long>`, `Authorization: Bearer <JWT>` (필수) |
| Path | `id: Long` |
| 본문 | `{ "decisionReason": "프로젝트 종료" }` — **필수** (없거나 공백이면 `400`) |

처리:
1. 티켓 조회 (없으면 `400`)
2. **권한 검증**: `getUserRole(approverId)` 가 `ADMIN` 이거나, `LEADER` 이면서 해당 `project_id` 를 소유해야 함. 아니면 `403` "프로젝트 리더 또는 ADMIN만 접근 권한을 회수할 수 있습니다."
3. `payment.revoke(approverId, reason)` → `COMPLETED` 아니면 `400`, 맞으면 `status=CANCELLED`
4. Kafka `payment.revoked` 발행
5. `CredentialAuditLogService.createAuditLog(...)` — `action=PROJECT_ACCESS_REVOKED`, `result=SUCCESS`, `detail="프로젝트 접근 권한 회수: <reason>"` 로 감사 로그 1행 적재

응답 `200`: `data.status = "CANCELLED"`

DB 영향: `payments` 행 UPDATE (`status=CANCELLED`, `approved_by`, `decision_reason`) · `credential_audit_logs` 1행 INSERT
→ (비동기) `enrollments.status` `ACTIVE→CANCELLED`, `courses.enrollment_count` -1

---

### 4.7 `GET /api/payments/{id}` — 승인 티켓 단건 조회 【유지 · 응답 변경】

- 인증: 사용자 JWT
- Path: `id: Long`

응답 `200`: `data` = 4.4의 `PaymentResponse` 구조. 없으면 `400`.

베이스라인 차이: `amount` 제거, `enrollmentId`/`projectId`/`approvedBy`/`decisionReason` 추가. `msa-lecture`의 `createdAt`만 있던 것에서 `updatedAt` 추가.

---

### 4.8 `GET /api/payments/user/{userId}` — 사용자 승인 내역 【유지 · 응답 변경】

- 인증: 사용자 JWT
- Path: `userId: Long`

응답 `200`: `data` = `PaymentResponse[]` (해당 사용자 `user_id` 기준 전체)

---

### 4.9 `POST /api/payments/internal/audit-logs` — 감사 이벤트 적재 【신규】 (FR-04-04)

course-service 등이 Credential 관련 행위(특히 **조회 거절**)를 기록.

| 항목 | 내용 |
|---|---|
| 인증 | 서비스 토큰 — `SCOPE_service.read` (사용자 JWT만 있으면 `403`) |

요청 본문:
```json
{
  "projectId": 1,
  "courseId": 10,
  "userId": 20,
  "action": "CREDENTIAL_VIEWED",
  "result": "DENIED",
  "sourceIp": "10.0.0.5",
  "detail": "프로젝트 접근 권한이 없습니다."
}
```
| 필드 | 타입 | 필수 | 비고 |
|---|---|---|---|
| `projectId` | Long | ❌ | |
| `courseId` | Long | ❌ | 자산(Credential) ID |
| `userId` | Long | ❌ | 행위자 |
| `action` | enum | ✅ `@NotNull` | 아래 목록 |
| `result` | enum | ✅ `@NotNull` | `SUCCESS` / `FAILURE` / `DENIED` |
| `sourceIp` | String(≤45) | ❌ | |
| `detail` | String(≤2000) | ❌ | **Secret 평문은 절대 넣지 않음** |

`action` enum: `API_KEY_CREATED`, `API_KEY_VIEWED`, `API_KEY_UPDATED`, `API_KEY_ROTATED`, `API_KEY_REVOKED`, `CREDENTIAL_VIEWED`, `SUBSCRIPTION_CREATED`, `SUBSCRIPTION_UPDATED`, `PROJECT_ACCESS_REQUESTED`, `PROJECT_ACCESS_ACTIVATED`, `PROJECT_ACCESS_CANCELLED`, `PROJECT_ACCESS_REAPPLIED`, `PROJECT_ACCESS_APPROVED`, `PROJECT_ACCESS_REJECTED`, `PROJECT_ACCESS_REVOKED`

응답 `201 Created`:
```json
{
  "success": true, "message": "성공",
  "data": {
    "id": 1, "eventId": "b1a2...uuid", "projectId": 1, "courseId": 10, "userId": 20,
    "action": "CREDENTIAL_VIEWED", "result": "DENIED",
    "sourceIp": "10.0.0.5", "detail": "프로젝트 접근 권한이 없습니다.",
    "createdAt": "2026-08-30T10:10:00"
  }
}
```

DB 영향: `credential_audit_logs` 1행 INSERT (`event_id` 서버 생성 UUID)

에러: `400` — `action`/`result` 누락, `detail`/`sourceIp` 길이 초과 · `403` — 서비스 스코프 없음

---

### 4.10 `GET /api/payments/audit-logs/projects/{projectId}` — 프로젝트 감사 이력 【신규】

- 인증: 사용자 JWT (`authenticated`)
- Path: `projectId: Long` (`@Positive` — 0/음수면 `400`)

응답 `200`: `data` = `AuditLogResponse[]`, `created_at DESC` 정렬. (4.9 응답의 `data` 형태와 동일)

---

### 4.11 `GET /api/payments/internal/audit-logs/credentials/{credentialId}/denied-count` — DENIED 횟수 집계 【신규】 (FR-05-01)

recommend-service 규칙 엔진이 "최근 접근 거절 이력 3회 이상 → 위험 +20" 계산에 사용.

| 항목 | 내용 |
|---|---|
| 인증 | 사용자 **또는** 서비스 JWT (`authenticated` — 미인증 시 `401`) |
| Path | `credentialId: Long` (`@Positive`) = `courses.id` (자산 ID) |
| Query | `days` (기본 `30`, `@Min(1) @Max(365)` — 범위 밖이면 `400`) |

처리: `countByCourseIdAndResultAndCreatedAtGreaterThanEqual(credentialId, DENIED, now-days)` (UTC 기준)

응답 `200`:
```json
{
  "success": true, "message": "성공",
  "data": {
    "credentialId": 10, "periodDays": 30,
    "deniedAccessCount": 3, "calculatedAt": "2026-08-30T05:00:00"
  }
}
```

---

## 5. 서비스 간 호출 관계 (skala)

```
[Frontend] ──JWT──► [API Gateway] ──X-User-Id──►
     ├─► enrollment-service  POST /api/enrollments
     │        ├─(svc token)─► course-service  GET /api/courses/internal/projects/{id}/exists
     │        └─(svc token)─► payment-service POST /api/payments/internal/request  ⇒ payments(PENDING)
     │
     ├─► payment-service  GET /api/payments/pending  (리더)
     │        ├─(fwd JWT)──► course-service  GET /api/courses/projects        (소유 프로젝트)
     │        └────────────► enrollment-service GET /api/enrollments/internal/{enrollmentId} (사유)
     │
     └─► payment-service  POST /api/payments/{id}/approve  (리더)
              └─► Kafka: payment.completed
                    ├─► enrollment-service Consumer  ⇒ enrollments PENDING→ACTIVE ⇒ Kafka: enrollment.completed
                    └─► course-service Consumer       ⇒ courses.enrollment_count +1

[Frontend] ──► course-service  GET /api/courses/{id}/secret  (승인 멤버)
     ├─ 허용 ─► enrollment-service PATCH /api/enrollments/internal/{userId}/{projectId}/access ⇒ last_accessed_at
     └─ 거절 ─► payment-service   POST /api/payments/internal/audit-logs (result=DENIED)      ⇒ credential_audit_logs

[recommend-service] ──► payment-service GET /api/payments/internal/audit-logs/credentials/{id}/denied-count
                    ──► enrollment-service GET /api/enrollments/internal/history/{userId}
```

---

## 6. Kafka 이벤트 명세

모든 이벤트는 `JsonSerializer` (`spring.json.add.type.headers=false`), key = `String.valueOf(projectId)`.
enrollment-service consumer는 타입 헤더 없이 `Map<String,Object>` 로 역직렬화하여 처리(누락 필드 시 로그 후 skip; `payment.revoked` 는 예외를 재던져 재처리 유도).

### 6.1 `payment.completed` — 승인 완료

| Producer | payment-service (`approve`) |
| Consumer | enrollment-service, course-service |

```json
{
  "eventId": "uuid", "paymentId": 100, "enrollmentId": 10,
  "userId": 5, "projectId": 1, "approvedBy": 2,
  "transactionId": "uuid", "status": "COMPLETED",
  "occurredAt": "2026-08-30T10:05:00Z"
}
```
enrollment-service 수신 시: `activateEnrollment(enrollmentId, userId, projectId)` — `PENDING` 인 건만 `ACTIVE` 로, 이미 `ACTIVE` 면 멱등 통과, 그 외 상태면 예외. 성공 시 `enrollment.completed` 발행.

> 베이스라인 차이: `msa-lecture` 이벤트는 `{paymentId, userId, courseId, status}` 4필드, key = `userId`. enrollment-service는 `(userId, courseId)` 로 enrollment를 찾아 활성화하고 course-service `enrollment-count` 를 직접 REST 호출했다. skala는 `enrollmentId` 기반 + course-service가 자체 consumer로 처리.

### 6.2 `payment.rejected` — 승인 거절 【신규】

| Producer | payment-service (`reject`) · Consumer | enrollment-service |

```json
{
  "eventId": "uuid", "paymentId": 100, "enrollmentId": 10,
  "userId": 5, "projectId": 1, "rejectedBy": 2,
  "reason": "권한 범위 초과", "status": "FAILED",
  "occurredAt": "..."
}
```
수신 시: `cancelEnrollment(...)` — `PENDING` → `CANCELLED` (재신청 가능 상태로). 이미 `CANCELLED` 면 멱등 통과.

### 6.3 `payment.revoked` — 권한 회수 【신규】

| Producer | payment-service (`revoke`) · Consumer | enrollment-service |

```json
{
  "eventId": "uuid", "paymentId": 100, "enrollmentId": 10,
  "userId": 5, "projectId": 1, "revokedBy": 2,
  "reason": "프로젝트 종료", "status": "CANCELLED",
  "occurredAt": "..."
}
```
수신 시: `revokeEnrollment(...)` — `ACTIVE` → `CANCELLED`. 실패 시 예외를 재던짐(재처리).

### 6.4 `enrollment.completed` — 멤버십 활성화 완료 【필드 변경】

| Producer | enrollment-service · Consumer | recommend-service (등) |

```json
{ "enrollmentId": 10, "userId": 5, "projectId": 1, "status": "ACTIVE" }
```
베이스라인 차이: `courseId` → `projectId`, `status` 필드 추가.

---

## 7. DB 스키마 변경

원본 DDL: `init-db/01_init.sql` (신규 초기화용) · 전환 마이그레이션: `init-db/migrations/V2__project_credential_domain.sql`
JPA `ddl-auto: validate` (두 서비스 모두) — 스키마는 SQL로 관리.

### 7.1 `enrollments` 테이블

| 컬럼 | msa-lecture | skala-msa-develop | 변경 |
|---|---|---|---|
| `id` | BIGINT PK AI | 동일 | – |
| `user_id` | BIGINT NOT NULL, FK→users | 동일 (FK `fk_enrollments_user`) | – |
| `course_id` | BIGINT NOT NULL, FK→courses | **삭제** | ❌ 제거 |
| `project_id` | – | BIGINT NOT NULL, FK→projects | 🆕 추가 |
| `reason` | – | TEXT NULL | 🆕 추가 |
| `status` | VARCHAR(20) `PENDING/ACTIVE/CANCELLED` | 동일 (의미: 승인대기/멤버십유효/회수) | 의미 변경 |
| `last_accessed_at` | – | DATETIME(6) NULL | 🆕 추가 |
| `created_at` / `updated_at` | DATETIME(6) | 동일 (`updated_at` ON UPDATE CURRENT_TIMESTAMP) | – |
| UNIQUE | `uq_user_course (user_id, course_id)` | `uq_enrollments_user_project (user_id, project_id)` | 변경 |
| INDEX | – | `idx_enrollments_project_status`, `idx_enrollments_user_status` | 🆕 추가 |

### 7.2 `payments` 테이블

| 컬럼 | msa-lecture | skala-msa-develop | 변경 |
|---|---|---|---|
| `id` | BIGINT PK AI | 동일 | – |
| `enrollment_id` | – | BIGINT NOT NULL, FK→enrollments | 🆕 추가 |
| `user_id` | BIGINT NOT NULL FK | 동일 | – |
| `course_id` | BIGINT NOT NULL FK | **삭제** | ❌ 제거 |
| `project_id` | – | BIGINT NOT NULL, FK→projects | 🆕 추가 |
| `approved_by` | – | BIGINT NULL, FK→users | 🆕 추가 (승인/거절/회수 처리자) |
| `amount` | DECIMAL(10,2) NOT NULL | **삭제** | ❌ 제거 |
| `status` | `PENDING/COMPLETED/FAILED/CANCELLED` | 동일 (의미: 대기/승인/거절/회수) | 의미 변경 |
| `transaction_id` | VARCHAR(255) UNIQUE (PG 거래 ID) | 동일, 의미 = **감사 티켓 UUID** (승인 시에만 발급) | 의미 변경 |
| `decision_reason` | – | TEXT NULL | 🆕 추가 |
| `created_at` / `updated_at` | DATETIME(6) | 동일 | – |
| INDEX | – | `idx_payments_enrollment_created`, `idx_payments_project_status` | 🆕 추가 |

### 7.3 `credential_audit_logs` 테이블 【신규】

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | BIGINT PK AI | |
| `event_id` | VARCHAR(36) NOT NULL, UNIQUE `uq_audit_event_id` | 멱등키(UUID) |
| `project_id` | BIGINT NULL, FK→projects | |
| `course_id` | BIGINT NULL, FK→courses | 자산 ID |
| `user_id` | BIGINT NULL, FK→users | 행위자 |
| `action` | VARCHAR(50) NOT NULL | enum (4.9 참조) |
| `result` | VARCHAR(20) NOT NULL | `SUCCESS/FAILURE/DENIED` |
| `source_ip` | VARCHAR(45) NULL | |
| `detail` | TEXT NULL | Secret 값 제외 |
| `created_at` | DATETIME(6) NOT NULL | |
| INDEX | `idx_audit_project_created`, `idx_audit_course_created`, `idx_audit_user_created` | |

### 7.4 부수 변경 (참고)

- **`projects` 테이블 신규**: `id, name(UNIQUE), description, owner_id(FK→users, =리더), status(ACTIVE/ARCHIVED/CLOSED), created_at, updated_at`
- **`users.role`**: `STUDENT|INSTRUCTOR` → `ADMIN|LEADER|MEMBER` (기본값 `MEMBER`), `email` UNIQUE 제약 추가
- **`courses`**: `price`, `enrollment_count` 관련 재편, `project_id`(FK), `provider`, `plan_name`, `expires_at`, `renewal_at`, `last_rotated_at`, `metadata`(암호화 Secret payload) 추가, `category` = `API_KEY|SUBSCRIPTION_PLAN`

---

## 8. DB 상태 변경 캡쳐

### 8.1 실행 환경 관련 (읽어주세요)

이 PC에서 **실제 스택을 띄워 DB 스크린샷을 찍는 것은 불가**했습니다. 확인 결과:

- `docker` / `docker compose` / `podman` 미설치 (PATH·Docker Desktop 모두 없음)
- WSL2 Ubuntu 존재하나 **손상 상태** (`ext4.vhdx` 경로 없음, 네트워킹 초기화 실패)
- MariaDB / Kafka / gradle / maven 미설치 (JDK 21만 있음)
- `docker-compose.yml`의 이미지가 **`linux/arm64`** 전용인데 이 PC는 Windows x64 → 플랫폼 불일치
- `auth-server`, `api-gateway` 는 소스가 없는 **프리빌트 이미지**(`msa-lecture/*:1.0`)라 리포지토리만으로 빌드 불가

→ 실제 캡쳐를 원하시면: (a) Docker Desktop이 설치되고 배포용 x64 이미지 tar(`msa-lecture-images-*.tar`)가 있는 환경, 또는 (b) MariaDB만 로컬로 띄우고 두 서비스를 `./gradlew bootRun` 으로 기동 + Kafka는 임베디드/생략, 중 하나가 필요합니다. 알려주시면 스크립트를 만들어 드리겠습니다.

아래는 코드 로직 기준으로 재구성한 **행(row) 단위 상태 전이표**입니다.

### 8.2 시나리오 A — 정상 신청 → 승인 → 자산 접근 → 회수

전제: `users`에 MEMBER(id=5), LEADER(id=2). `projects`에 id=1 (owner_id=2). `courses`에 id=10 (project_id=1).

| 단계 | API | `enrollments` (id=10) | `payments` (id=100) | `credential_audit_logs` | `courses.enrollment_count` (id=10) |
|---|---|---|---|---|---|
| 0. 초기 | – | (없음) | (없음) | (없음) | 0 |
| 1. 신청 | `POST /api/enrollments` `{projectId:1, reason:"..."}` (X-User-Id:5) | **INSERT** `user_id=5, project_id=1, reason="...", status=PENDING, last_accessed_at=NULL` | **INSERT** `enrollment_id=10, user_id=5, project_id=1, status=PENDING, transaction_id=NULL, approved_by=NULL` | – | 0 |
| 2. 리더 조회 | `GET /api/payments/pending` (X-User-Id:2) | 변화 없음 | 변화 없음 | – | 0 |
| 3. 승인 | `POST /api/payments/100/approve` (X-User-Id:2) `{decisionReason:"승인"}` | (동기적으로는 변화 없음) | **UPDATE** `status=COMPLETED, approved_by=2, transaction_id=<UUID>, decision_reason="승인", updated_at=now` | – | 0 |
| 3-a. Kafka `payment.completed` → enrollment consumer | (비동기) | **UPDATE** `status=PENDING→ACTIVE, updated_at=now` | – | – | 0 |
| 3-b. Kafka `payment.completed` → course consumer | (비동기) | – | – | – | **0 → 1** |
| 3-c. enrollment → Kafka `enrollment.completed` | (비동기) | – | – | – | 1 |
| 4. Secret 조회 (허용) | `GET /api/courses/10/secret` → `PATCH /api/enrollments/internal/5/1/access` | **UPDATE** `last_accessed_at=now` | – | (course-service가 SUCCESS 로그를 남기는 경우 INSERT) | 1 |
| 5. 회수 | `POST /api/payments/100/revoke` (X-User-Id:2) `{decisionReason:"프로젝트 종료"}` | (동기적으로는 변화 없음) | **UPDATE** `status=COMPLETED→CANCELLED, approved_by=2, decision_reason="프로젝트 종료"` | **INSERT** `project_id=1, user_id=2, action=PROJECT_ACCESS_REVOKED, result=SUCCESS, detail="프로젝트 접근 권한 회수: 프로젝트 종료", event_id=<UUID>` | 1 |
| 5-a. Kafka `payment.revoked` → enrollment consumer | (비동기) | **UPDATE** `status=ACTIVE→CANCELLED` | – | – | – |
| 5-b. Kafka `payment.revoked` → course consumer | (비동기) | – | – | – | **1 → 0** |

### 8.3 시나리오 B — 거절 → 재신청

| 단계 | API | `enrollments` (id=10) | `payments` |
|---|---|---|---|
| 1. 신청 | `POST /api/enrollments` | INSERT `status=PENDING` | INSERT id=100 `status=PENDING` |
| 2. 거절 | `POST /api/payments/100/reject` `{decisionReason:"범위 초과"}` | – | **UPDATE** id=100 `status=FAILED, approved_by=2, decision_reason="범위 초과"` |
| 2-a. Kafka `payment.rejected` → enrollment | (비동기) | **UPDATE** `status=PENDING→CANCELLED` | – |
| 3. 재신청 | `POST /api/enrollments` `{projectId:1, reason:"재검토 요청"}` | **UPDATE** (같은 행 재사용) `status=CANCELLED→PENDING, reason="재검토 요청", updated_at=now` | **INSERT** id=101 `enrollment_id=10, status=PENDING` (신규 티켓) |

> 재신청은 `(user_id, project_id)` UNIQUE 제약 때문에 **기존 enrollment 행을 UPDATE** 하고, payment 티켓은 **새로 INSERT** 된다. 따라서 하나의 enrollment에 여러 payment 이력이 쌓일 수 있다 (`idx_payments_enrollment_created` 로 조회).

### 8.4 시나리오 C — Secret 조회 거절 (권한 없음)

| 단계 | API | `credential_audit_logs` |
|---|---|---|
| 1. 미승인 사용자가 Secret 조회 | `GET /api/courses/10/secret` (승인 안 된 MEMBER) | course-service가 `POST /api/payments/internal/audit-logs` 호출 → **INSERT** `project_id=1, course_id=10, user_id=<요청자>, action=CREDENTIAL_VIEWED, result=DENIED, detail="프로젝트 자산 조회 권한이 없습니다.", event_id=<UUID>` |
| 2. recommend-service 위험 분석 | `GET /api/payments/internal/audit-logs/credentials/10/denied-count?days=30` | 조회만 — `deniedAccessCount` 집계 |

---

## 9. "핵심 API" 우선순위 (검토·데모용)

| 순위 | 서비스 | API | 이유 |
|---|---|---|---|
| 1 | enrollment | `POST /api/enrollments` | 전체 워크플로우의 시작점, 도메인 전환의 상징 (course→project+reason) |
| 2 | payment | `POST /api/payments/{id}/approve` | 신규 사람-승인 단계 + 감사 티켓 UUID + Kafka 팬아웃의 트리거 |
| 3 | payment | `GET /api/payments/pending` | 리더 UX의 중심, course/enrollment/user 3개 서비스 조합 |
| 4 | payment | `POST /api/payments/{id}/reject` | 거절 → 재신청 루프 |
| 5 | enrollment | `GET /api/enrollments/my-projects` | MEMBER UX의 중심 (상태별 분리) |
| 6 | payment | `POST /api/payments/{id}/revoke` | 회수 + 감사 로그, 유일하게 권한 검증이 구현된 엔드포인트 |
| 7 | payment | `POST /api/payments/internal/audit-logs` + `.../denied-count` | 감사·거버넌스·위험분석 파이프라인 |
| 8 | enrollment | `PATCH /internal/{userId}/{projectId}/access` | Secret 평문 조회 감사(FR-03-04) |

---

## 10. 알려진 이슈 / 확인 필요 사항

1. **`approve` / `reject` 에 리더 권한 검증 없음** — `revoke` 만 `requireRevocationAuthority` 로 프로젝트 소유/ADMIN 검증. `approve`/`reject`는 `X-User-Id` 만 있으면 아무나 호출 가능. 의도된 것인지 확인 필요.
2. **enrollment-service `permitAll` 유지** — 게이트웨이를 반드시 경유한다는 전제. 직접 포트(8083) 노출 시 `X-User-Id` 위조로 타인 신청 가능.
3. **`denied-count` 의 `credentialId` = `courses.id`** — 파라미터/필드명은 `credentialId` 지만 실제 매핑은 `course_id` (레거시 컬럼명). repository 메서드도 `countByCourseId...`.
4. **`GET /api/payments/pending` 의 `userName` 항상 null** — 코드 주석에 명시된 알려진 제약 (user-service 조회 권한 + payment 서비스 토큰 부재).
5. **`payments`–`enrollments` 관계**: DDL `V2` 주석엔 `enrollment_id ... UNIQUE` 언급이 있으나, `01_init.sql` 실제 DDL과 엔티티에는 UNIQUE 없음 → 재신청 시 enrollment 1 : payment N. 스펙 문서(§8.3)는 후자 기준으로 작성함.
