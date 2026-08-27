# 🛡️ [KeyNexus] 사내 프로젝트별 Credential 거버넌스 플랫폼
## Agile & MSA 기반 서비스 전환 및 스프린트 1·2 역할 분담/개발 설계서 (v3.0.0)

---

## 1. 비즈니스 정의 및 이해관계자 Pain Point 분석

### 1-1. 배경 및 문제 정의 (Pain Points)
현대 기업의 소프트웨어 개발 환경에서는 수많은 프로젝트가 생성되고 종료되며, 그 과정에서 클라우드 키(AWS IAM, GCP Service Account), DB 접속 계정, 결제/외부 API Secret이 무분별하게 발급되고 방치됩니다.

```text
[현재 상황의 악순환]
개발자 : "API Key 어디 있죠? 누구한테 요청해요?" ──▶ 슬랙/노션에 비밀번호 평문 공유 (보안 사고 위험)
PM/TL  : "프로젝트 끝났는데 어떤 키를 회수해야 하죠?" ──▶ 알 수 없어 방치 (불필요 과금 발생)
보안팀 : "누가 발급했고 언제 만료되는지 파악 불가" ──▶ 전사 감사(Audit) 시 증적 누락
기  업 : "퇴사자/프로젝트 종료 시 자산 추적 단절" ──▶ 기업의 디지털 지적 자산 유실
```

### 1-2. 우리 솔루션이 제공하는 핵심 가치 (Core Value)
1. **프로젝트 단위 거버넌스 (Project-Level Governance)**: 최상위 프로젝트(`projects`)를 단위로 개발자(`MEMBER`)가 접근 권한을 요청하고, 프로젝트 리더(`LEADER`)가 승인하면 소속 프로젝트 내 모든 자산 이용 가능. (ADMIN은 사전 승인 없이 전사 접근 가능)
2. **역할 기반 인가 (Role-Based Access Control)**: `ADMIN`(전사 관리자), `LEADER`(프로젝트 책임자/승인자), `MEMBER`(프로젝트 신청/자산 이용자)로 역할을 정비하여 명확한 권한 통제 제공.
3. **규칙 기반 위험도 및 만료 알림 (Rule-based Risk & Expiration Governance)**: 객관적인 규칙 기반으로 API Key 만료/회전 임박 및 구독 Plan 갱신 주기를 감지하고 알림 리포트 제공.

---

## 2. 도메인 매핑표 (기존 MSA 템플릿 ➔ Credential 솔루션)

| 기존 템플릿 개념 | 기존 시스템 요소 | Credential 거버넌스 솔루션 매핑 | 비즈니스 및 UI 의미 |
| :--- | :--- | :--- | :--- |
| **신규 (Root Boundary)** | `projects` 테이블 / Service | **사내 프로젝트 (Project Boundary)** | 자산 및 거버넌스 권한 격리의 최상위 영역 |
| **강사 (Instructor)** | `Role: LEADER` | **프로젝트 리더 / Tech Lead** | 프로젝트 자산을 등록하고 멤버의 프로젝트 접근 요청을 승인하는 책임자 |
| **수강생 (Student)** | `Role: MEMBER` | **프로젝트 팀원 / 개발자** | 프로젝트 접근 권한을 신청하고, 승인 후 프로젝트 자산을 활용하는 사원 |
| **관리자 (Admin)** | `Role: ADMIN` | **전사 보안 관리자** | 모든 프로젝트 및 자산에 자유롭게 접근하고 전사 거버넌스를 통제하는 관리자 |
| **과목 (Course)** | `courses` 테이블 / Service | **디지털 자산 (Credential Asset)** | AWS IAM, DB 접속정보, API Key 및 SaaS 구독 Plan |
| **수강생 수 (EnrollmentCount)** | `courses.enrollment_count`| **프로젝트 활성 멤버 수 (Active Members)**| 현재 프로젝트 접근 승인을 받아 자산을 이용 중인 활성 사원 수 |
| **수강신청 (Enrollment)** | `enrollments` 테이블 / Service | **프로젝트 접근 권한 신청 (Project Request)** | 개발자가 특정 프로젝트 접근을 신청 (`PENDING` ➔ 리더 승인 시 `ACTIVE`) |
| **결제 (Payment)** | `payments` 테이블 / Service | **프로젝트 승인 및 감사 (Project Grant)** | 프로젝트 리더 승인 및 감사 티켓 UUID 발행 ➔ `COMPLETED` 시 멤버십 활성화 |
| **추천 서비스 (Recommend)**| `recommend-service` (FastAPI)| **규칙 기반 위험도 및 만료 알림 엔진** | API Key 만료/회전 주기 및 구독 갱신 위험 점수(0~100) 계산 및 알림 |

---

## 3. 스프린트(Sprint) 분할 및 단계별 로드맵

```text
┌─────────────────────────────────────────────────────────────────────────────────┐
│ [Sprint 1] 핵심 가치 검증 (MVP)                                                 │
│  "이 기능이 없으면 서비스로서 가치를 줄 수 없는가?"                                │
│  ▶ 계정 인증 ➔ 프로젝트 생성 ➔ 자산 등록 ➔ 프로젝트 접근 신청 (PENDING)           │
│  ▶ 백엔드 3개 서비스(User, Project/Asset, Request) + 프론트엔드 기본 UI 연결        │
└────────────────────────────────────────┬────────────────────────────────────────┘
                                         │ 피드백 수집 및 안정화
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│ [Sprint 2] 비동기 프로젝트 승인 연동 & 규칙 기반 거버넌스 알림 확장               │
│  "있으면 업무 효율이 극대화되지만, 없어도 기본 동작은 가능한 기능"                 │
│  ▶ 비동기 Kafka 이벤트 기반 프로젝트 승인 (Payment ➔ Request ACTIVE 멤버십 부여)  │
│  ▶ 내가 속한 프로젝트(My Projects) 뷰어 및 프로젝트 자산 Secret 평문 조회        │
│  ▶ FastAPI 기반 규칙 기반 위험도 점수 계산 & 만료/회전 알림 대시보드 위젯        │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 3-1. 스프린트 1 (Sprint 1) : 핵심 MVP
* **목표**: 프로젝트 생성 ➔ 자산 등록 ➔ 프로젝트 접근 신청 E2E 기본 흐름 완성.
* **세부 구현 범위**:
  1. **인증/계정 (`User Service`)**: ADMIN, LEADER, MEMBER 역할 로그인 및 JWT 발급.
  2. **프로젝트 & 자산 카탈로그 (`Course Service`)**: 프로젝트 생성 (`POST /api/courses/projects`), 자산 등록 (`POST /api/courses`), 자산 목록 조회.
  3. **프로젝트 접근 신청 (`Enrollment Service`)**: 프로젝트 접근 신청 (`POST /api/enrollments`), 내 신청 현황 조회.
  4. **프론트엔드 UI**: 프로젝트 카탈로그 뷰, 접근 신청 모달, 내 프로젝트 신청함 연동.

### 3-2. 스프린트 2 (Sprint 2) : 비동기 승인 연동 & 규칙 기반 거버넌스 확장
* **목표**: Kafka 비동기 프로젝트 승인 및 FastAPI 기반 규칙 기반 위험도/만료 알림 엔진 연동.
* **세부 구현 범위**:
  1. **프로젝트 보안 승인 (`Payment Service`)**: 프로젝트 리더 승인/거절 (`POST /api/payments/{id}/approve`), Kafka 이벤트 `payment.completed` 발행.
  2. **프로젝트 멤버십 & 자산 활용 (`Enrollment Service`)**: Kafka 수신 시 `ACTIVE` 멤버십 활성화, "내가 속한 프로젝트(`My Projects`)" 뷰어 구현, 프로젝트 내 자산 Secret 평문 조회 및 `last_accessed_at` 갱신.
  3. **규칙 기반 위험도 엔진 (`Recommend Service - FastAPI`)**: 0~100점 위험 점수 및 `CRITICAL/HIGH` 등급 계산, 알림 리포트 반환 (`POST /api/recommend/projects/{projectId}/analyze`).
  4. **프론트엔드 UI**: 프로젝트 리더 전용 승인 대기열(Approval Queue), My Projects 자산 뷰어, 위험도 대시보드 위젯 연동.

---

## 4. MSA 아키텍처 및 서비스 간 인터페이스 설계

### 4-1. 서비스 아키텍처 다이어그램

```mermaid
flowchart TB
    Client(["👤 Web Client (Vue.js)"])
    
    subgraph Gateway["API Gateway Layer (:8080)"]
        GW["Spring Cloud Gateway\n- JWT Validation\n- Header Propagation (X-User-Id, Role)"]
    end

    subgraph Discovery["Service Discovery (:8761)"]
        EU["Eureka Server"]
    end

    subgraph CoreServices["Microservices Ecosystem"]
        US["User Service (:8081)\n- 사내 계정 / ADMIN/LEADER/MEMBER Role"]
        AS["Project & Asset Service (:8082)\n(Course Service)\n- 프로젝트/자산 등록/목록/조회"]
        RS["Access Request Service (:8083)\n(Enrollment Service)\n- 프로젝트 접근 신청/멤버십"]
        AP["Approval & Audit Service (:8084)\n(Payment Service)\n- 프로젝트 리더 승인/감사 티켓"]
        AI["Rule-based Risk Engine (:8085)\n(FastAPI Recommend Service)\n- 규칙 기반 위험도 & 만료/회전 알림"]
    end

    subgraph MessageBus["Async Message Bus (:9092)"]
        KF["Kafka Broker (KRaft)\n- payment.completed\n- enroll.completed"]
    end

    subgraph Storage["MariaDB (:3306)"]
        DB[("Single Instance Shared DB\n- projects, users, courses\n- enrollments, payments, credential_audit_logs")]
    end

    Client -->|REST API 요청| GW
    GW -->|/users/**| US
    GW -->|/courses/**| AS
    GW -->|/enrollments/**| RS
    GW -->|/payments/**| AP
    GW -->|/recommend/**| AI

    US & AS & RS & AP & AI -.->|Heartbeat| EU
    GW -.->|Lookup| EU

    RS -->|동기 REST: 승인 검증| AP
    AP -->|1. 비동기 이벤트 발행: payment.completed| KF
    KF -->|2. 승인 수신 -> 프로젝트 ACTIVE 멤버십 부여| RS
    KF -->|3. 승인 수신 -> 프로젝트 멤버 Seat 1 증가| AS

    US --- DB
    AS --- DB
    RS --- DB
    AP --- DB
    AI -.-|Read Only 메타데이터 조회| DB
```

### 4-2. 핵심 API 엔드포인트 명세서

| 서비스 도메인 | Method | 엔드포인트 (Gateway 경로) | 헤더 / 파라미터 | 기능 설명 | 스프린트 |
| :--- | :---: | :--- | :--- | :--- | :---: |
| **Auth / User** | `POST` | `/api/users/login` | Body: `{email, password}` | 로그인 및 JWT 토큰 발급 | Sprint 1 |
| **Project** | `POST` | `/api/courses/projects` | `X-User-Id` (LEADER / ADMIN) | 신규 사내 프로젝트 생성 | Sprint 1 |
| **Project** | `GET` | `/api/courses/projects` | `Authorization: Bearer <토큰>` | 전사 프로젝트 목록 조회 | Sprint 1 |
| **Asset (Course)** | `GET` | `/api/courses?projectId=1` | `Authorization: Bearer <토큰>` | 승인된 멤버/ADMIN의 프로젝트 자산 목록 | Sprint 1 |
| **Asset (Course)** | `POST` | `/api/courses` | `X-User-Id` (LEADER / ADMIN) | 특정 프로젝트 내 신규 자산 등록 | Sprint 1 |
| **Access (Enroll)** | `POST` | `/api/enrollments` | Body: `{"projectId": 1, "reason": "..."}` | 프로젝트 접근 권한 신청 | Sprint 1 |
| **Access (Enroll)** | `GET` | `/api/enrollments/my-projects` | `Authorization: Bearer <토큰>` | 내 승인된 프로젝트 및 신청 대기 목록 | Sprint 2 |
| **Access (Enroll)** | `GET` | `/api/courses/{id}/secret` | `Authorization: Bearer <토큰>` | 소속 프로젝트 자산 Secret 평문 조회 | Sprint 2 |
| **Approval (Pay)** | `POST` | `/api/payments/{id}/approve`| Body: `{decisionReason}` | 프로젝트 리더 승인 및 Kafka 이벤트 | Sprint 2 |
| **Rule Risk Engine**| `POST` | `/api/recommend/projects/{projectId}/analyze` | Path: `{projectId}` | 규칙 기반 위험도 및 만료 알림 반환 | Sprint 2 |

---

## 5. 팀원별 Task 분담 및 WBS

### 5-1. 백엔드 팀 Task
* **Sprint 1**:
  - `projects`, `courses`, `users`, `enrollments`, `payments`, `credential_audit_logs` DDL 및 JPA 암호화 작성.
  - 프로젝트 및 자산 CRUD REST API 구현.
* **Sprint 2**:
  - Kafka 브로커를 통한 `payment.completed` ➔ 프로젝트 멤버십 `ACTIVE` 비동기 상태 전환 구현.
  - FastAPI 기반 `recommend-service` 규칙 기반 Risk Engine 구현.

### 5-2. 프론트엔드 팀 Task
* **Sprint 1**:
  - 프로젝트 카탈로그 뷰, 프로젝트 접근 신청 모달 구현.
* **Sprint 2**:
  - 프로젝트 리더 전용 승인 대기열(Approval Queue) 뷰어 구현.
  - 내가 속한 프로젝트(My Projects) 자산 탐색 및 Secret 평문 뷰어 구현.
  - 프로젝트 위험도 및 만료 알림 대시보드 위젯 구현.
