# 📋 [KeyNexus] 시스템 요구사항 정의서 (SRS)
## Software Requirements Specification: Enterprise Credential & Digital Asset Governance Platform

> **문서 버전**: v1.0.0  
> **작성 기준일**: 2026-08-26  
> **문서 책임자**: PM / 기획 파트  
> **대상 독자**: 백엔드 엔지니어, 프론트엔드 엔지니어, QA 엔지니어, 인프라 엔지니어  
> **문서 목적**: 본 문서는 KeyNexus 플랫폼 개발에 필요한 기능적/비기능적 요구사항, 데이터 모델, API 인터페이스 규격, UI/UX 흐름 및 트러블슈팅 기준을 정의한 **개발의 단일 진실 공급원(Single Source of Truth)**입니다.

---

# 📑 목차 (Table of Contents)

1. [시스템 개요 및 프로젝트 목표](#1-시스템-개요-및-프로젝트-목표)
2. [용어 정의 및 도메인 데이터 사전](#2-용어-정의-및-도메인-데이터-사전)
3. [시스템 아키텍처 및 네트워크 규격](#3-시스템-아키텍처-및-네트워크-규격)
4. [공통 인증 및 인가 규격 (Security Baseline)](#4-공통-인증-및-인가-규격-security-baseline)
5. [기능별 상세 요구사항 (Functional Requirements)](#5-기능별-상세-요구사항-functional-requirements)
   - 5.1 [FR-01] 계정 및 사용자 권한 관리 (`user-service`)
   - 5.2 [FR-02] 디지털 자산(Credential) 카탈로그 관리 (`course-service`)
   - 5.3 [FR-03] 접근 권한 신청 및 라이프사이클 관리 (`enrollment-service`)
   - 5.4 [FR-04] 보안 검증 및 비동기 승인 발급 (`payment-service`)
   - 5.5 [FR-05] AI 기반 자산 위험도 분석 및 거버넌스 (`recommend-service`)
6. [비기능 요구사항 (Non-Functional Requirements)](#6-비기능-요구사항-non-functional-requirements)
7. [데이터베이스 설계 및 스키마 명세](#7-데이터베이스-설계-및-스키마-명세)
8. [API 인터페이스 상세 규격서 (REST Contract)](#8-api-인터페이스-상세-규격서-rest-contract)
9. [이벤트 메시지 규격서 (Kafka Event Contract)](#9-이벤트-메시지-규격서-kafka-event-contract)
10. [화면 정의 및 프론트엔드 컴포넌트 설계](#10-화면-정의-및-프론트엔드-컴포넌트-설계)
11. [스프린트별 개발 범위 (Sprint Scope & DoD)](#11-스프린트별-개발-범위-sprint-scope--dod)
12. [개발자 FAQ 및 트러블슈팅 가이드](#12-개발자-faq-및-트러블슈팅-가이드)

---

# 1. 시스템 개요 및 프로젝트 목표

### 1.1 시스템 개요
**KeyNexus**는 기업 내 분산되어 관리되던 각종 클라우드 IAM Key, 데이터베이스 계정, 외부 결제/통신 API Secret 등의 디지털 인증 자산(Credential)을 프로젝트 단위로 가시화하고, 권한 요청-승인-만료-회수의 전 과정을 체계적으로 통제하는 **사내 Credential 거버넌스 솔루션**입니다.

### 1.2 핵심 프로젝트 목표
1. **가시성 확보 (Visibility)**: 사내 프로젝트에 연결된 모든 Credential의 소유자, 용도, 접근 권한자를 한 화면에서 조회.
2. **최소 권한 원칙 (Least Privilege)**: 모든 자산 접근은 '신청 ➔ 승인' 단계를 거치며, 미인가 사용자의 평문 Key 조회를 차단.
3. **위험도 기반 지능형 거버넌스 (AI Governance)**: 다중 프로젝트가 공유하는 고위험 자산 및 장기 미회수 자산을 AI가 탐지하여 우선순위 마이그레이션 권고.
4. **Agile & MSA 실증**: 기존 5개 마이크로서비스 및 인프라 구조의 수정을 최소화하고, 도메인 매핑을 통해 즉시 가동 가능한 엔터프라이즈 플랫폼 구현.

---

# 2. 용어 정의 및 도메인 데이터 사전

기존 온라인 강의 템플릿의 물리적 명칭을 비즈니스 도메인 용어로 1:1 대응하여 사용합니다. 백엔드 DB와 DTO는 기존 구조를 유지하되, 프론트엔드 UI 및 API 응답 해석 시 아래 사전을 준수합니다.

| 물리 엔티티/필드 (Template) | 도메인 용어 (KeyNexus) | 정의 및 비즈니스 의미 | 허용 값 / 형식 |
| :--- | :--- | :--- | :--- |
| **`users`** | **사내 계정 (Member)** | 시스템을 이용하는 사내 임직원 계정 | `STUDENT`(개발자), `INSTRUCTOR`(관리자) |
| **`courses`** | **디지털 자산 (Digital Asset)** | 관리 대상이 되는 Credential, API Key, DB 접속정보 | 엔티티 테이블 |
| `courses.title` | **자산명 (Asset Name)** | 디지털 자산의 식별 이름 | 문자열 (예: `AWS Prod IAM Key`) |
| `courses.category` | **자산 유형 (Asset Type)** | 자산의 기술 분류 | `SECURITY`(API Key), `CLOUD_IAM`, `DATABASE`, `BACKEND` |
| `courses.price` | **보안 위험 등급 (Risk Tier)** | 유출 시 비즈니스 영향도 점수/가치 | `100.00`(Low) ~ `500.00`(Critical) |
| `courses.instructor_id` | **자산 소유자 (Owner ID)** | 자산 생성 및 승인 권한을 가진 테크 리드/관리자 ID | `users.id` FK |
| `courses.enrollment_count`| **활성 참조 수 (Active Consumers)** | 현재 해당 자산을 승인받아 사용 중인 프로젝트/개발자 수 | 정수 (기본값 0) |
| **`enrollments`** | **접근 권한 요청 (Access Request)** | 개발자가 특정 자산에 대한 접근 권한을 요청한 내역 | 엔티티 테이블 |
| `enrollments.status` | **승인 상태 (Access Status)** | 자산 접근 권한의 진행 상태 | `PENDING`(승인 대기), `ACTIVE`(사용 가능), `CANCELLED`(회수) |
| **`payments`** | **보안 검증 및 승인 (Security Grant)**| 관리자의 접근 승인 및 감사 로그 발급 레코드 | 엔티티 테이블 |
| `payments.transaction_id` | **감사 티켓 ID (Audit UUID)** | 보안 감사 추적용 고유 트랜잭션 식별자 | UUID 문자열 |
| `payments.status` | **승인 처리 상태** | 승인 프로세스의 완료 여부 | `PENDING`, `COMPLETED`, `FAILED` |

---

# 3. 시스템 아키텍처 및 네트워크 규격

### 3.1 네트워크 토폴로지 및 포트 할당

```
                             [Client Browser]
                                     │ (HTTP / JSON)
                                     ▼
                   ┌───────────────────────────────────┐
                   │    Spring Cloud Gateway (:8080)   │
                   └─────────────────┬─────────────────┘
                                     │
         ┌───────────────┬───────────┼───────────────┬───────────────┐
         ▼               ▼           ▼               ▼               ▼
┌─────────────────┐┌──────────┐┌───────────┐┌─────────────────┐┌─────────────┐
│  user-service   ││  course  ││enrollment ││ payment-service ││ recommend-  │
│     (:8081)     ││ (:8082)  ││  (:8083)  ││     (:8084)     ││  service    │
│   (사내 계정)   ││(자산관리)││(권한신청) ││   (보안 승인)   ││   (:8085)   │
└────────┬────────┘└────┬─────┘└─────┬─────┘└────────┬────────┘└──────┬──────┘
         │              │            │               │                │
         └──────────────┼────────────┴───────────────┴────────────────┘
                        ▼
       ┌─────────────────────────────────┐      ┌─────────────────────────────┐
       │   MariaDB 11.2 (:3306) (DB)     │      │   Kafka Broker (:9092)      │
       │   - Single Instance Shared      │      │   - Topic: payment.completed│
       └─────────────────────────────────┘      │   - Topic: enroll.completed │
                                                └─────────────────────────────┘
```

* **서비스 탐색**: `eureka-server` (포트: `8761`)
* **인증 인프라**: `auth-server` (포트: `9000`)
* **단일 진입점**: `api-gateway` (포트: `8080`) — **프론트엔드는 오직 8080 포트만 호출**

---

# 4. 공통 인증 및 인가 규격 (Security Baseline)

### 4.1 인증 토큰 규격
* **방식**: OAuth2 / OpenID Connect 기반 JWT (JSON Web Token)
* **발급처**: `auth-server` (포트 `9000`) ➔ `POST /api/users/login` 응답으로 전달.
* **전송 규칙**: 프론트엔드는 모든 보호된 API 호출 시 헤더에 `Authorization: Bearer <토큰>` 첨부.

### 4.2 Gateway 헤더 전파 (Header Propagation)
API Gateway는 JWT 서명을 검증한 후, 복호화된 사용자 메타데이터를 하위 마이크로서비스로 HTTP 헤더를 통해 주입합니다.

| 주입 헤더명 | 내용 | 예시 값 | 하위 서비스 사용처 |
| :--- | :--- | :--- | :--- |
| **`X-User-Id`** | 사용자 고유 식별 번호 (Long) | `1` | 자산 등록자 식별, 내 신청 목록 필터링 |
| **`X-User-Email`** | 사용자 이메일 계정 | `developer@company.com` | 감사 로그 기록 |
| **`X-User-Role`** | 사용자 권한 그룹 | `ROLE_STUDENT`, `ROLE_INSTRUCTOR` | 인가(Authorization) 제어 |

---

# 5. 기능별 상세 요구사항 (Functional Requirements)

## 5.1 [FR-01] 계정 및 사용자 권한 관리 (`user-service`)

* **FR-01-01 [회원가입]**: 사내 직원은 이메일, 비밀번호, 이름, 역할을 입력하여 계정을 생성할 수 있다.
  - 관리자(Owner): `role = "INSTRUCTOR"`
  - 개발자(Consumer): `role = "STUDENT"`
* **FR-01-02 [로그인 및 토큰 발급]**: 이메일과 비밀번호로 로그인 시 JWT Access Token을 발급받는다.
* **FR-01-03 [내 정보 조회]**: 로그인된 사용자는 JWT 토큰을 기반으로 본인의 계정 정보 및 역할을 조회할 수 있다.

## 5.2 [FR-02] 디지털 자산(Credential) 카탈로그 관리 (`course-service`)

* **FR-02-01 [자산 등록]**: 관리자(`ROLE_INSTRUCTOR`) 권한을 가진 사용자만 신규 자산을 등록할 수 있다.
  - 필수 입력값: 자산명(`title`), 상세설명(`description`), 자산분류(`category`), 위험등급(`price`).
  - 자산 등록 시 `instructor_id`는 Gateway가 주입한 `X-User-Id`로 자동 바인딩된다.
* **FR-02-02 [자산 목록 조회]**: 모든 인증된 사용자는 등록된 디지털 자산 전체 목록을 조회할 수 있다.
* **FR-02-03 [자산 상세 조회]**: 자산 ID를 통해 해당 자산의 메타데이터 및 소유자 정보를 단건 조회할 수 있다.
* **FR-02-04 [카테고리별 필터링]**: 자산 유형(`category`)별로 자산 목록을 필터링 조회할 수 있다.
* **FR-02-05 [활성 참조수 자동 갱신]**: 자산 접근 승인이 완료될 때마다 해당 자산의 `enrollment_count`가 1씩 증가한다.

## 5.3 [FR-03] 접근 권한 신청 및 라이프사이클 관리 (`enrollment-service`)

* **FR-03-01 [접근 권한 신청]**: 개발자(`ROLE_STUDENT`)는 특정 자산 ID를 지정하여 사용 권한을 신청한다.
  - 신청 즉시 `status = 'PENDING'` 상태의 레코드가 생성된다.
  - 동일 사용자가 동일 자산에 대해 중복 신청하는 것을 방지한다 (Unique 제약).
* **FR-03-02 [내 신청 내역 조회]**: 개발자는 본인이 신청한 자산들의 목록과 현재 승인 상태(`PENDING` / `ACTIVE` / `CANCELLED`)를 조회할 수 있다.
* **FR-03-03 [비동기 상태 전이 수신]**: 승인 완료 Kafka 이벤트(`payment.completed`)를 수신하면, 해당 신청 건의 상태를 `ACTIVE`로 변경하고, `course-service`에 참조수 증가를 요청한다.

## 5.4 [FR-04] 보안 검증 및 비동기 승인 발급 (`payment-service`)

* **FR-04-01 [승인 처리 트리거]**: 내부 시스템 또는 관리자에 의해 승인 요청이 접수되면 고유한 감사 티켓(`transaction_id`: UUID)을 생성하고 상태를 `COMPLETED`로 변경한다.
* **FR-04-02 [승인 완료 이벤트 발행]**: 승인 처리가 완료되는 즉시 Kafka 브로커의 `payment.completed` 토픽으로 비동기 이벤트를 발행한다.
* **FR-04-03 [승인 내역 단건/사용자별 조회]**: 발급된 감사 티켓 및 승인 상세 내역을 조회할 수 있다.

## 5.5 [FR-05] AI 기반 자산 위험도 분석 및 거버넌스 (`recommend-service`)

* **FR-05-01 [사용자/프로젝트 기반 위험도 분석]**: 특정 사용자가 보유한 자산 이력과 전체 자산의 중요도/의존성을 분석하여 맞춤형 거버넌스 정보를 반환한다.
* **FR-05-02 [다중 의존 고위험 자산 우선 추천]**: 
  - 수식: $\text{Risk Score} = \text{Price (중요도)} \times \text{EnrollmentCount (참조 프로젝트 수)}$
  - 다수의 프로젝트에서 참조 중이며 중요도가 높은 Credential을 우선순위 상위 5개로 정렬하여 반환한다.
* **FR-05-03 [마이그레이션 권고 메시지 생성]**: 분석 결과에 따라 *"보안 취약 예방을 위해 Vault 기반 동적 시크릿으로 우선 전환이 필요한 자산 목록"* 메시지를 제공한다.

---

# 6. 비기능 요구사항 (Non-Functional Requirements)

| 항목 | 요구사항 기준 |
| :--- | :--- |
| **성능 (Performance)** | Gateway 경유 API 호출 응답 시간 p95 300ms 이내 (동기 REST 구간). |
| **보안 (Security)** | 1. 패스워드는 BCrypt 해시 알고리즘으로 단방향 암호화 저장.<br>2. 모든 마이크로서비스 간 통신은 API Gateway의 토큰 검증을 통과해야 함.<br>3. `ACTIVE` 상태가 아닌 사용자는 Secret 평문값에 접근 불가. |
| **장애 격리 (Fault Tolerance)** | Kafka 브로커 장애 또는 AI 서비스 다운 시에도 핵심 자산 조회 및 신청(Sprint 1 영역)은 정상 작동해야 함. |
| **확장성 (Scalability)** | 각 마이크로서비스는 Stateless 구조로 설계되어 Docker 컨테이너 단위의 수평 확장이 가능해야 함. |

---

# 7. 데이터베이스 설계 및 스키마 명세

단일 MariaDB 인스턴스 내에서 논리적으로 분리된 4대 핵심 테이블 구조입니다.

```sql
-- 1. 사내 계정 테이블
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255)    NOT NULL UNIQUE,
    password    VARCHAR(255)    NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    role        VARCHAR(20)     NOT NULL COMMENT 'STUDENT (개발자) | INSTRUCTOR (관리자)',
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 디지털 자산(Credential) 테이블
CREATE TABLE IF NOT EXISTS courses (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    title            VARCHAR(255)    NOT NULL COMMENT '자산명 (예: AWS IAM Key)',
    description      TEXT            COMMENT '자산 상세 및 사용 가이드',
    category         VARCHAR(50)     NOT NULL COMMENT 'SECURITY | CLOUD_IAM | DATABASE | BACKEND',
    price            DECIMAL(10,2)   NOT NULL COMMENT '보안 중요도 등급 (100.00 ~ 500.00)',
    instructor_id    BIGINT          NOT NULL COMMENT '자산 소유자 (users.id FK)',
    enrollment_count INT             NOT NULL DEFAULT 0 COMMENT '활성 참조 프로젝트/사용자 수',
    status           VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | INACTIVE',
    created_at       DATETIME(6),
    updated_at       DATETIME(6),
    PRIMARY KEY (id),
    FOREIGN KEY (instructor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 자산 접근 권한 신청 테이블
CREATE TABLE IF NOT EXISTS enrollments (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL COMMENT '신청 개발자 (users.id FK)',
    course_id   BIGINT      NOT NULL COMMENT '대상 자산 (courses.id FK)',
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING (승인대기) | ACTIVE (승인완료) | CANCELLED',
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_course (user_id, course_id),
    FOREIGN KEY (user_id)   REFERENCES users(id),
    FOREIGN KEY (course_id) REFERENCES courses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 보안 검증 및 승인 감사 테이블
CREATE TABLE IF NOT EXISTS payments (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL COMMENT '신청 개발자 (users.id FK)',
    course_id       BIGINT          NOT NULL COMMENT '대상 자산 (courses.id FK)',
    amount          DECIMAL(10,2)   NOT NULL COMMENT '보안 중요도 가중치',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | COMPLETED | FAILED',
    transaction_id  VARCHAR(255)    UNIQUE COMMENT '보안 감사 티켓 UUID',
    created_at      DATETIME(6),
    updated_at      DATETIME(6),
    PRIMARY KEY (id),
    FOREIGN KEY (user_id)   REFERENCES users(id),
    FOREIGN KEY (course_id) REFERENCES courses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

# 8. API 인터페이스 상세 규격서 (REST Contract)

모든 API 요청은 Gateway 베이스 URL `http://localhost:8080`을 통해 라우팅됩니다.

### 8.1 계정 / 인증 API (`user-service`)

#### [POST] `/api/users/login` — 로그인 및 토큰 발급
* **Request Header**: `Content-Type: application/json`
* **Request Body**:
```json
{
  "email": "developer@company.com",
  "password": "Password123!"
}
```
* **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "id": 1,
      "name": "홍길동",
      "email": "developer@company.com",
      "role": "STUDENT"
    }
  },
  "message": "로그인 성공"
}
```

---

### 8.2 자산 카탈로그 API (`course-service`)

#### [GET] `/api/courses` — 디지털 자산 전체 목록 조회
* **Request Header**: `Authorization: Bearer <토큰>`
* **Response (200 OK)**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "title": "AWS Production IAM Admin Key",
      "description": "클라우드 인프라 배포 및 EKS 클러스터 제어용 루트성 키",
      "category": "CLOUD_IAM",
      "price": 500.00,
      "instructorId": 2,
      "instructorName": "김보안 (Tech Lead)",
      "enrollmentCount": 4,
      "status": "ACTIVE",
      "createdAt": "2026-08-20T10:00:00"
    }
  ]
}
```

#### [POST] `/api/courses` — 신규 디지털 자산 등록 (관리자 전용)
* **Request Header**: `Authorization: Bearer <토큰>`, `X-User-Id: 2` (Gateway 자동 주입)
* **Request Body**:
```json
{
  "title": "Payment Gateway Secret Key",
  "description": "토스페이먼츠 상용 결제 승인 API 시크릿",
  "category": "SECURITY",
  "price": 400.00
}
```
* **Response (201 Created)**:
```json
{
  "success": true,
  "data": {
    "id": 2,
    "title": "Payment Gateway Secret Key",
    "category": "SECURITY",
    "price": 400.00,
    "instructorId": 2,
    "status": "ACTIVE"
  },
  "message": "자산이 성공적으로 등록되었습니다."
}
```

---

### 8.3 접근 권한 신청 API (`enrollment-service`)

#### [POST] `/api/enrollments` — 자산 접근 권한 신청
* **Request Header**: `Authorization: Bearer <토큰>`, `X-User-Id: 1`
* **Request Body**:
```json
{
  "courseId": 1
}
```
* **Response (201 Created)**:
```json
{
  "success": true,
  "data": {
    "id": 10,
    "userId": 1,
    "courseId": 1,
    "courseTitle": "AWS Production IAM Admin Key",
    "status": "PENDING",
    "createdAt": "2026-08-26T17:50:00"
  },
  "message": "자산 접근 권한 신청이 접수되었습니다. (승인 대기)"
}
```

#### [GET] `/api/enrollments/my` — 내 자산 권한 및 신청 목록 조회
* **Request Header**: `Authorization: Bearer <토큰>`, `X-User-Id: 1`
* **Response (200 OK)**:
```json
{
  "success": true,
  "data": [
    {
      "id": 10,
      "courseId": 1,
      "courseTitle": "AWS Production IAM Admin Key",
      "category": "CLOUD_IAM",
      "status": "ACTIVE",
      "createdAt": "2026-08-26T17:50:00"
    }
  ]
}
```

---

### 8.4 AI 위험도 분석 및 거버넌스 API (`recommend-service`)

#### [GET] `/api/recommend/{userId}` — 고위험 및 마이그레이션 우선순위 분석
* **Request Header**: `Authorization: Bearer <토큰>`
* **Response (200 OK)**:
```json
{
  "userId": 1,
  "recommendedCourses": [
    {
      "id": 1,
      "title": "AWS Production IAM Admin Key",
      "category": "CLOUD_IAM",
      "price": 500.00,
      "enrollmentCount": 8,
      "riskScore": 4000.00
    },
    {
      "id": 3,
      "title": "User Database Master Connection",
      "category": "DATABASE",
      "price": 450.00,
      "enrollmentCount": 6,
      "riskScore": 2700.00
    }
  ],
  "basedOnCategory": "CLOUD_IAM",
  "message": "⚠️ 8개 이상의 서비스에서 참조 중인 고위험 자산입니다. 즉시 Vault 동적 토큰으로 마이그레이션할 것을 권고합니다."
}
```

---

# 9. 이벤트 메시지 규격서 (Kafka Event Contract)

### 9.1 토픽: `payment.completed` (보안 승인 완료 이벤트)
* **발행자 (Producer)**: `payment-service`
* **구독자 (Consumer)**: `enrollment-service`
* **메시지 페이로드 스키마**:
```json
{
  "paymentId": 100,
  "userId": 1,
  "courseId": 1,
  "amount": 500.00,
  "transactionId": "sec-grant-uuid-88392",
  "status": "COMPLETED",
  "timestamp": "2026-08-26T17:55:00"
}
```
* **수신 시 처리 로직**: `enrollment-service`는 해당 `userId`와 `courseId`에 일치하는 신청 건의 상태를 `ACTIVE`로 업데이트하고, `courses`의 `enrollment_count`를 1 증가시킴.

---

# 10. 화면 정의 및 프론트엔드 컴포넌트 설계

### 10.1 라우팅 맵 (`src/router/index.js`)

| 라우트 경로 | 뷰 컴포넌트 | 화면 명칭 | 접근 권한 | 주요 기능 |
| :--- | :--- | :--- | :---: | :--- |
| `/` | `LandingView.vue` | 메인 랜딩 포털 | All | 솔루션 소개, 최근 등록 자산 요약 |
| `/login` | `LoginView.vue` | 사내 SSO 로그인 | All | 이메일/PW 입력 및 토큰 발급 |
| `/courses` | `CourseListView.vue` | 디지털 자산 카탈로그 | Auth | 전체 자산 검색, 카테고리 필터링, 카드 뷰 |
| `/courses/:id` | `CourseDetailView.vue`| 자산 상세 및 권한 신청| Auth | 자산 메타데이터, [권한 신청] 액션 |
| `/courses/create`| `CourseCreateView.vue`| 신규 자산 등록 | Admin | 자산명, 유형, 위험등급 입력 폼 |
| `/mypage` | `MyPageView.vue` | 내 자산함 & AI 대시보드 | Auth | 보유/대기 자산 현황, AI 위험도 리포트 |

### 10.2 컴포넌트별 UI 변환 가이드 (Label Dictionary)
프론트엔드 개발자는 기존 코드를 수정할 때 아래 사전표를 기준으로 텍스트 및 스타일을 치환합니다.

* `과목 / Course` ➔ **디지털 자산 (Credential Asset)**
* `강사 / Instructor` ➔ **자산 책임자 (Asset Owner)**
* `수강료 / Price` ➔ **보안 등급 (Impact Tier)** (표시: `Lv.1` ~ `Lv.5` 또는 `Critical/High/Medium`)
* `수강생 수 / Enrollment Count` ➔ **참조 프로젝트 (Consumers)**
* `수강 신청 / Enroll` ➔ **접근 권한 신청 (Request Access)**
* `추천 강의 / Recommend` ➔ **AI 보안 거버넌스 & 마이그레이션 권고**

---

# 11. 스프린트별 개발 범위 (Sprint Scope & DoD)

```
[Sprint 1: 핵심 자산 가시화 및 신청 MVP]
- 목표: 자산 등록 ➔ 목록/상세 조회 ➔ 접근 권한 신청 (PENDING) E2E 동작 완성
- 백엔드: user, course, enrollment 서비스 기동 및 Swagger API 동작 검증
- 프론트엔드: 랜딩, 로그인, 자산 카탈로그, 자산 등록 폼, 내 신청 목록 UI 구현
- DoD: 개발자가 로그인 후 원하는 자산을 찾아 [신청] 버튼을 눌렀을 때 DB에 PENDING으로 저장됨

[Sprint 2: 비동기 보안 승인 및 AI 거버넌스]
- 목표: Kafka 비동기 승인 자동화 (ACTIVE 전환) 및 AI 위험도 분석 대시보드 탑재
- 백엔드: payment-service 승인 처리 ➔ Kafka 이벤트 ➔ enrollment ACTIVE 처리 입증
- AI 파트: recommend-service (FastAPI) 위험 지수 산출 및 마이그레이션 권고 API 구현
- 프론트엔드: ACTIVE 상태 시 Secret 마스킹 해제 UI, AI 위험도 분석 위젯 연동
- DoD: 승인 트리거 후 비동기로 권한이 활성화되고 AI 추천 리포트가 화면에 정상 표시됨
```

---

# 12. 개발자 FAQ 및 트러블슈팅 가이드

### Q1. 프론트엔드에서 API 호출 시 CORS 에러가 발생합니다.
* **원인**: 개별 서비스 포트(`8081`, `8082` 등)로 직접 `fetch` 요청을 보냈기 때문입니다.
* **해결책**: 반드시 API Gateway 포트인 `http://localhost:8080/api/...`로 호출해야 합니다.

### Q2. 로그인 후 다른 API 호출 시 401 Unauthorized 에러가 납니다.
* **원인**: 요청 헤더에 Authorization Bearer 토큰이 누락되었거나 `sessionStorage`에 토큰이 저장되지 않았습니다.
* **해결책**: `src/api/index.js`의 axios 인터셉터에서 `sessionStorage.getItem('token')`을 꺼내 `headers['Authorization'] = 'Bearer ' + token`을 주입하는지 확인하십시오.

### Q3. 백엔드 서비스에서 사용자 ID를 어떻게 가져오나요?
* **해결책**: Controller 메서드 파라미터에 `@RequestHeader("X-User-Id") Long userId`를 선언하면 Gateway가 주입한 사용자 식별자를 즉시 사용할 수 있습니다.

### Q4. Kafka 이벤트가 수신되지 않습니다.
* **해결책**: `docker compose logs -f kafka`로 브로커 정상 기동 여부를 확인하고, `enrollment-service`의 Consumer 로그에 `@KafkaListener` 토픽명이 `payment.completed`로 정확히 일치하는지 확인하십시오.

---

> **[PM 서명]**  
> 본 요구사항 정의서는 KeyNexus 프로젝트의 공식 기준 문서이며, 모든 팀원은 본 문서에 정의된 API 규격과 상태 머신 규칙을 준수하여 개발을 진행합니다.
