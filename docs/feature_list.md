# [KeyNexus] 핵심 기능 상세 둘러보기 (Feature Showcase)

> 본 문서는 업데이트된 요구사항 정의서(`requirements_specification.md` v2.1.0)의 기능 명세를 바탕으로, KeyNexus 솔루션이 제공하는 핵심 기능과 비즈니스 가치를 한눈에 파악할 수 있도록 5대 핵심 모듈별로 정리한 기능 안내서입니다.

---

## 1. 사내 프로젝트 & 디지털 자산 통합 카탈로그 (course-service + projects)

> 사내 프로젝트별로 분산되어 있던 DB 비밀번호, API Key, B2B SaaS 구독 플랜을 최상위 프로젝트(`projects`) 단위로 통합 가시화합니다.

* **사내 프로젝트별 자산 격리 및 가시성 확보 (FR-02-06)**
  * 프로젝트(`projects`)를 최상위 영역으로 생성하고, 프로젝트에 연결된 디지털 자산과 구독 플랜을 한눈에 파악합니다.
* **자산 유형별 카테고리 필터링 (FR-02-04)**
  * DB 계정(`DB_CREDENTIAL`), API 시크릿(`API_KEY`), SaaS 구독 서비스(`SUBSCRIPTION_PLAN`)별로 카탈로그를 탐색하고 검색할 수 있습니다.
* **JPA ColumnTransformer DB 레벨 암호화 (FR-02-01, NFR-보안)**
  * MariaDB `courses` 테이블의 Secret 메타데이터는 JPA 레벨에서 AES 양방향 암호화 처리되어 저장되므로, DB 덤프 유출 시에도 안전합니다.
* **활성 참조 수(Seat) 자동 가시화 (FR-02-02, FR-02-05)**
  * 특정 자산을 몇 명의 사원이 실제로 점유/참조하고 있는지 `enrollment_count` 수치로 실시간 모니터링합니다.

---

## 2. 접근 권한 신청 및 승인 기반 접근권 (enrollment-service)

> 필요시 원클릭으로 승인을 요청하며, 보안 검증 완료 시 안전하게 자산에 접근할 수 있습니다.

* **원클릭 권한 신청 & 중복 방지 (FR-03-01)**
  * 사원은 필요한 자산 화면에서 접근 목적(`reason`)을 입력해 원클릭으로 신청하며, 중복 신청은 자동 차단됩니다.
* **승인 기반 접근권 활성화 (FR-03-03)**
  * 관리자가 승인하면 권한 상태가 `ACTIVE`로 변경되어 안전하게 자산 정보를 이용할 수 있습니다.
* **Secret 마스킹 해제 및 실시간 접속 로그 갱신 (FR-03-04, FEAT-FE-07)**
  * 마이페이지에서 사원이 [Secret 조회]를 클릭하면 마스킹이 해제되어 평문을 확인할 수 있으며, 감사 및 거버넌스 분석을 위해 `last_accessed_at` 접속 로그가 비동기로 적재됩니다.
* **명시적 권한 회수 (FR-03-05)**
  * 관리자의 권한 회수 처리 또는 사원의 취소 요청 시 권한 상태가 `CANCELLED`로 전이되고 자산 참조 Seat 수(1 감소)가 차감됩니다.

---

## 3. 비동기 보안 승인 & 감사 이력 관리 (payment-service)

> 마이크로서비스 간 직접적인 결합 없이, Kafka 이벤트를 통한 빠른 비동기 승인과 엄격한 감사 이력을 제공합니다.

* **고유 감사 티켓(UUID) 발급 및 승인 (FR-04-01, FR-04-03)**
  * 승인 처리 시 고유한 감사 티켓 UUID(`transaction_id`), 승인자(`approved_by`), 사유(`decision_reason`)가 기록되어 ISMS/SOC2 보안 감사에 대비합니다.
* **Kafka Event-Driven 멀티 컨슈머 연동 (FR-04-02, FR-02-05, FR-03-03)**
  * `payment-service`가 `payment.completed` 이벤트를 발행하면, `enrollment-service`(권한 승인)와 `course-service`(Seat 증가)가 독립된 Kafka Consumer로 수신하여 장애 격리 및 결합도 해제를 달성합니다.
* **감사 로그(Audit Log) 추적 (FR-04-04)**
  * Key 조회, 회전, 폐기 등의 행위를 `credential_audit_logs`에 위변조 없이 기록합니다.

---

## 4. FastAPI 규칙 기반(Rule-based) Credential 위험도 분석 & 만료/회전 알림 (recommend-service)

> 백엔드 규칙 엔진이 프로젝트 자산 메타데이터와 감사 통계를 분석하여 객관적인 위험 점수와 만료/회전 권고 알림을 산출합니다.

* **객관적 규칙 기반 위험 점수 및 등급 계산 (FR-05-01)**
  * API Key 만료 임박(7일 전 +40, 30일 전 +20), 마지막 회전 주기 경과(180일 초과 +25, 90일 초과 +15), 활성 접근자 수(5명 이상 +15), 최근 접근 거절 이력(3회 이상 +20) 등을 합산해 0~100점 점수 및 `LOW / MEDIUM / HIGH / CRITICAL` 등급을 산출합니다.
* **API Key 만료/회전 및 구독 Plan 갱신 알림 권고 (FR-05-02)**
  * 만료 임박 API Key, 회전 필요 Key, 갱신 임박 구독 Plan을 자동으로 가려내고 관리자가 수행할 우선순위 조치 가이드 리포트를 제시합니다.
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
 ├── 1. 프로젝트 & 자산 카탈로그 (projects 최상위 관리, JPA DB 암호화, Credential & SaaS 탭 분리)
 ├── 2. 접근 권한 (승인 기반 ACTIVE 접근권, last_accessed_at 갱신, 명시적 REVOKE 회수)
 ├── 3. 비동기 승인 & 감사 (Kafka Multi-Consumer, 감사 티켓 UUID, credential_audit_logs)
 ├── 4. 규칙 기반 위험도 & 만료 알림 (만료/회전/갱신 주기 기반 0~100 위험점수 및 CRITICAL/HIGH 등급 산출)
 └── 5. 거버넌스 UX (위험도/만료 알림 대시보드 위젯, GitHub Actions Self-Healing)
```

KeyNexus는 이처럼 "보안 통제(프로젝트 단위 자산 관리 & 승인 기반 접근)"와 "규칙 기반 거버넌스(FastAPI 만료/회전 위험도 분석)"라는 두 축을 바탕으로 엔터프라이즈 환경에 완벽한 거버넌스를 제공합니다.
