# [KeyNexus] 핵심 기능 상세 둘러보기 (Feature Showcase)

> 본 문서는 업데이트된 요구사항 정의서(requirements_specification.md)의 기능 명세를 바탕으로, KeyNexus 솔루션이 제공하는 핵심 기능과 비즈니스 가치를 한눈에 파악할 수 있도록 5대 핵심 모듈별로 정리한 기능 안내서입니다.

---

## 1. 디지털 자산 & SaaS 통합 카탈로그 (course-service)

> 사내 분산되어 있던 DB 비밀번호, API Key, B2B SaaS 구독 플랜을 하나의 카탈로그로 통합 가시화합니다.

* **자산 유형별 카테고리 필터링 (FR-02-04)**
  * DB 계정(DB_CREDENTIAL), API 시크릿(API_KEY), SaaS 구독 서비스(SUBSCRIPTION_PLAN)별로 카탈로그를 한눈에 탐색하고 검색할 수 있습니다.
* **JPA ColumnTransformer DB 레벨 암호화 (FR-02-01, NFR-보안)**
  * MariaDB courses 테이블의 Secret 메타데이터는 JPA 레벨에서 AES 양방향 암호화 처리되어 저장되므로, DB 덤프 유출 시에도 안전합니다.
* **활성 참조 수(Seat) 자동 가시화 (FR-02-02, FR-02-05)**
  * 특정 자산을 몇 명의 사원(또는 몇 개의 프로젝트)이 실제로 점유/참조하고 있는지 enrollment_count 수치로 실시간 모니터링합니다.

---

## 2. 접근 권한 신청 및 4시간 임시 접근권 (enrollment-service)

> 무분별한 영구 권한 부여를 막고, 필요할 때만 4시간 동안 한시적으로 접근을 허용합니다.

* **원클릭 권한 신청 & 중복 방지 (FR-03-01)**
  * 사원은 필요한 자산 화면에서 접근 목적을 입력해 원클릭으로 신청하며, 이미 보유 중인 권한은 중복 신청이 자동 차단됩니다.
* **4시간 임시 접근권 (expires_at) 부여 (FR-03-03)**
  * 관리자가 승인하면 권한 상태가 ACTIVE로 변경되는 동시에, 승인 시점 기준 4시간만 유효한 만료 일시(expires_at = NOW + 4h)가 부여됩니다.
* **Secret 마스킹 해제 & 4시간 카운트다운 타이머 UI (FR-03-04, FEAT-FE-07)**
  * 마이페이지에서 사원이 [Secret 조회]를 클릭하면 마스킹이 해제되어 평문을 확인할 수 있으며, 화면에 4시간 카운트다운 타이머가 실시간 작동합니다.
* **시간 제약 만료 자동 회수 (FR-03-05)**
  * 4시간이 경과하면 시스템이 상태를 EXPIRED로 전이하고 Secret 조회를 다시 블라인드 처리하며 자산 참조 Seat 수(1 감소)를 자동 회수합니다.

---

## 3. 비동기 보안 승인 & 감사 이력 관리 (payment-service)

> 마이크로서비스 간 직접적인 결합 없이, Kafka 이벤트를 통한 빠른 비동기 승인과 엄격한 감사 로그를 제공합니다.

* **고유 감사 티켓(UUID) 발급 (FR-04-01, FR-04-03)**
  * 승인 처리 시 고유한 감사 티켓 UUID(transaction_id)가 생성되어, ISMS/SOC2 보안 감사에 대비한 위변조 불가 감사 이력을 기록합니다.
* **Kafka Event-Driven 멀티 컨슈머 연동 (FR-04-02, FR-02-05, FR-03-03)**
  * payment-service가 payment.completed 이벤트를 발행하면, enrollment-service(권한 승인)와 course-service(Seat 증가)가 독립된 Kafka Consumer로 각각 비동기 수신하여 처리하므로, 장애 격리 및 결합도 완벽 해제를 달성합니다.

---

## 4. AI 기반 유휴 라이선스 탐지 & Shadow IT 분석 (recommend-service)

> 사원들의 실제 서비스 이용 텔레메트리 데이터와 등록 자산 메타데이터를 분석해 방치된 SaaS 라이선스를 가려내고 중복 결제를 감지합니다.

* **실시간 접속 로그(last_accessed_at) 수집 (FR-03-04)**
  * 사원이 자산 상세 보기나 Secret 평문 조회를 수행할 때마다 last_accessed_at 필드가 비동기 갱신되어, AI 분석을 위한 실제 접속 데이터가 적재됩니다.
* **90일 미접속 Zombie Seat 탐지 (FR-05-01)**
  * Python FastAPI 기반 AI 분석기가 최근 90일 동안 접근 이력이 전혀 없는 비활성 계정을 자동으로 식별합니다.
* **부서별 중복 SaaS(Shadow IT) 탐지 (FR-05-02)**
  * 서로 다른 팀이 개별 등록/결제하여 쓰고 있는 동일 SaaS(예: Notion) 메타데이터를 텍스트 클러스터링하여 통합 할인 포인트를 제시합니다.
* **Downsizing 절감 예측치 리포트 생성 (FR-05-03)**
  * 유휴 계정 데이터를 기반으로 "Notion 5개 계정 회수 시 월 ₩300,000 절감 가능" 형태의 AI 절감 예측치와 가이딩 리포트를 동적 생성합니다.

---

## 5. AI Downsizing 절감 예측치 위젯 & 자가 치유 CRON

> AI가 예측한 절감 가능 금액을 전면에 시각화하고, 데이터 불일치를 자가 치유합니다.

* **AI Downsizing 절감 예측치 위젯 (FEAT-FE-08)**
  * 거버넌스 대시보드 최상단에 "현재 유휴 시트 회수 및 Downsizing 적용 시 월 ₩X,XXX,XXX 절감 가능" 형태의 AI 절감 예측 금액 위젯을 렌더링하여 관리자에게 직관적인 판단 기준을 제공합니다.
* **GitHub Actions 자정 동기화 스케줄러 (NFR-데이터무결성)**
  * 매일 자정 GitHub Actions CRON 스크립트가 실행되어 courses 활성 참조수와 enrollments 레코드 수 대조, 그리고 만료된 4시간 임시 권한을 자가 치유(Self-Healing) 형태로 자동 정돈합니다.

---

## 6. 기능 요약 흐름도

```text
KeyNexus Platform
 ├── 1. 자산 카탈로그 (JPA DB 암호화, Credential & SaaS 탭 분리)
 ├── 2. 임시 권한 (4시간 타이머 UI, last_accessed_at 갱신, 자동 EXPIRED 회수)
 ├── 3. 비동기 승인 (Kafka Multi-Consumer, 감사 티켓 UUID 발급)
 ├── 4. Zombie Seat & Shadow IT 탐지 (90일 미접속 계정 분석, 중복 결제 파싱)
 └── 5. 거버넌스 UX (AI Downsizing 절감 예측치 위젯, GitHub Actions Self-Healing)
```

KeyNexus는 이처럼 "보안 통제(4시간 임시 접근)"와 "AI 비용 절감(Zombie Seat & Downsizing 절감 예측)"이라는 두 축을 바탕으로 엔터프라이즈 환경에 완벽한 거버넌스를 제공합니다.
