# [KeyNexus] 핵심 기능 상세 둘러보기 (Feature Showcase)

> 본 문서는 업데이트된 요구사항 정의서(`requirements_specification.md` v3.0.0)의 기능 명세를 바탕으로, KeyNexus 솔루션이 제공하는 핵심 기능과 비즈니스 가치를 한눈에 파악할 수 있도록 5대 핵심 모듈별로 정리한 기능 안내서입니다.

---

## 1. 사내 프로젝트 & 디지털 자산 통합 카탈로그 (course-service + projects)

> 사내 프로젝트별로 분산되어 있던 DB 비밀번호, API Key, B2B SaaS 구독 플랜을 최상위 프로젝트(`projects`) 단위로 통합 가시화합니다.

* **사내 프로젝트별 자산 격리 및 가시성 확보 (FR-02-06)**
  * 프로젝트(`projects`)를 최상위 영역으로 생성하고, 프로젝트 리더(`LEADER`)가 프로젝트에 연결된 디지털 자산과 구독 플랜을 등록 및 통제합니다.
* **자산 유형별 카테고리 필터링 (FR-02-04)**
  * DB 계정(`DB_CREDENTIAL`), API 시크릿(`API_KEY`), SaaS 구독 서비스(`SUBSCRIPTION_PLAN`)별로 카탈로그를 탐색하고 검색할 수 있습니다.
* **JPA ColumnTransformer DB 레벨 암호화 (FR-02-01, NFR-보안)**
  * MariaDB `courses` 테이블의 Secret 메타데이터는 JPA 레벨에서 AES 양방향 암호화 처리되어 저장되므로, DB 덤프 유출 시에도 안전합니다.
* **활성 참조 수(Seat) 자동 가시화 (FR-02-02, FR-02-05)**
  * 프로젝트 접근 승인을 받은 활성 개발자 수를 `enrollment_count` 수치로 실시간 모니터링합니다.

---

## 2. 프로젝트 접근 권한 신청 및 멤버십 관리 (enrollment-service)

> 개발자(`MEMBER`)는 필요한 프로젝트 단위로 접근 권한을 신청하며, 승인 완료 시 해당 프로젝트 내 모든 관리 자산에 접근할 수 있습니다.

* **프로젝트 단위 접근 신청 & 중복 방지 (FR-03-01)**
  * 사원(`MEMBER`)은 원하는 프로젝트 카탈로그에서 접근 목적(`reason`)을 입력해 원클릭으로 신청하며, 이미 승인된 프로젝트는 중복 신청이 자동 차단됩니다.
* **내가 속한 프로젝트 (My Projects) 뷰어 (FR-03-02)**
  * 개발자는 승인 완료된 프로젝트(`ACTIVE`) 목록을 한눈에 확인하고, 소속 프로젝트 내 모든 Credential 자산을 탐색할 수 있습니다.
* **Secret 마스킹 해제 및 실시간 접속 로그 갱신 (FR-03-04, FEAT-FE-07)**
  * 승인된 프로젝트 내 자산에서 [Secret 조회]를 클릭하면 마스킹이 해제되어 평문을 확인할 수 있으며, 감사 및 거버넌스 분석을 위해 `last_accessed_at` 접속 로그가 비동기로 적재됩니다.
* **프로젝트 멤버십 명시적 회수 (FR-03-05)**
  * 프로젝트 리더의 권한 회수 처리 또는 사원의 프로젝트 탈퇴 요청 시 멤버십 상태가 `CANCELLED`로 전이되고 프로젝트 Seat 수(1 감소)가 차감됩니다.

---

## 3. 프로젝트 비동기 승인 & 감사 이력 관리 (payment-service)

> 프로젝트 리더(`LEADER`)가 멤버의 프로젝트 접근 요청을 승인하며, Kafka 이벤트를 통해 비동기로 접근 권한이 활성화됩니다.

* **프로젝트 리더의 보안 승인 & 감사 티켓(UUID) 발급 (FR-04-01, FR-04-03)**
  * 해당 프로젝트의 리더(`approved_by`)가 승인 처리 시 고유한 감사 티켓 UUID(`transaction_id`)와 승인사유(`decision_reason`)가 기록되어 보안 감사에 대비합니다.
* **Kafka Event-Driven 멀티 컨슈머 연동 (FR-04-02, FR-02-05, FR-03-03)**
  * `payment-service`가 `payment.completed` 이벤트를 발행하면, `enrollment-service`(프로젝트 멤버십 ACTIVE 전환)와 `course-service`(프로젝트 멤버 Seat 증가)가 독립된 Kafka Consumer로 수신하여 처리합니다.
* **감사 로그(Audit Log) 추적 (FR-04-04)**
  * 프로젝트 승인/거절, Key 조회, 회전, 폐기 등의 행위를 `credential_audit_logs`에 위변조 없이 기록합니다.

---

## 4. FastAPI 규칙 기반(Rule-based) Credential 위험도 분석 & 만료/회전 알림 (recommend-service)

> 백엔드 규칙 엔진이 프로젝트 자산 메타데이터와 감사 통계를 분석하여 객관적인 위험 점수와 만료/회전 권고 알림을 산출합니다.

* **객관적 규칙 기반 위험 점수 및 등급 계산 (FR-05-01)**
  * API Key 만료 임박(7일 전 +40, 30일 전 +20), 마지막 회전 주기 경과(180일 초과 +25, 90일 초과 +15), 활성 프로젝트 멤버 수(5명 이상 +15), 최근 접근 거절 이력(3회 이상 +20) 등을 합산해 0~100점 점수 및 `LOW / MEDIUM / HIGH / CRITICAL` 등급을 산출합니다.
* **API Key 만료/회전 및 구독 Plan 갱신 알림 권고 (FR-05-02)**
  * 만료 임박 API Key, 회전 필요 Key, 갱신 임박 구독 Plan을 자동으로 가려내고 프로젝트 리더 및 관리자가 수행할 우선순위 조치 가이드 리포트를 제시합니다.
* **FastAPI 기반 위험도 분석 REST API (FR-05-03)**
  * Python FastAPI 기반 추천 서비스가 `/api/recommend/projects/{projectId}/analyze` 엔드포인트를 통해 즉각적인 위험 분석 JSON 결과를 응답합니다.

---

## 5. 프로젝트 위험도 & 만료 알림 위젯 & 자가 치유 CRON

> 위험도와 만료 알림을 전면에 시각화하고, 데이터 불일치를 자가 치유합니다.

* **프로젝트 위험도 및 만료 알림 대시보드 위젯 (FEAT-FE-08)**
  * 대시보드 상단에 "즉시 조치가 필요한 CRITICAL 위험 등급 Credential N개 존재" 형태의 알림 위젯을 렌더링하여 관리자에게 직관적인 의사결정을 제공합니다.
* **GitHub Actions 자정 동기화 스케줄러 (NFR-데이터무결성)**
  * 매일 자정 GitHub Actions CRON 스크립트가 실행되어 `courses` 활성 참조수와 `enrollments` 레코드 수 간의 데이터 정합성을 자가 치유(Self-Healing) 형태로 자동 동기화합니다.

---

## 6. 기능 요약 흐름도

```text
KeyNexus Platform
 ├── 1. 프로젝트 & 자산 카탈로그 (projects 최상위 관리, ADMIN 전사 접근, LEADER 자산 등록)
 ├── 2. 프로젝트 접근 신청 (MEMBER 프로젝트 신청 ➔ My Projects 뷰어 ➔ 소속 프로젝트 자산 활용)
 ├── 3. 프로젝트 승인 & 감사 (LEADER 보안 승인, Kafka Multi-Consumer, 감사 티켓 UUID)
 ├── 4. 규칙 기반 위험도 & 만료 알림 (만료/회전/갱신 주기 기반 0~100 위험점수 및 CRITICAL/HIGH 등급 산출)
 └── 5. 거버넌스 UX (위험도/만료 알림 대시보드 위젯, GitHub Actions Self-Healing)
```

KeyNexus는 이처럼 "프로젝트 단위 승인 통제"와 "규칙 기반 거버넌스 알림"이라는 두 축을 바탕으로 엔터프라이즈 환경에 완벽한 거버넌스를 제공합니다.
