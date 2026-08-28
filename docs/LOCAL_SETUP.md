# 로컬 실행 가이드 (다른 PC 셋업)

`git pull` 만으로는 동작하지 않는다. 소스 외에 **DB 스키마·데모 데이터·도커 이미지·`.env`** 를 각자 환경에서 맞춰야 한다.

---

## 0. 선행: infra 브랜치

이 브랜치는 `infra/feat-user-auth-compatibility` 를 전제로 한다 (배포된 auth-server / user-service
이미지가 그 버전). develop 에 아직 안 들어간 부분은 이 PR 이 다음으로 보충한다:

- `init-db/02_auth_role_compatibility.sql` — `auth_compat_db` VIEW (auth-server 가 레거시
  Role 로 사용자를 읽는 호환 계층)
- `docker-compose.override.yml` — auth-server 를 `auth_compat_db` 로, **user-service 의
  JWT issuer 를 `http://localhost:8080` 로** 지정 (안 맞추면 `/api/users/me` 500 → 로그인 직후 튕김)

user-service **소스** 정합(`AuthenticatedUserResolver` 등)은 infra PR 이 develop 에 병합돼야
완결된다. 그때 `docker-compose.override.yml` 은 삭제한다.

---

## 1. `.env` 생성 (2개 — 둘 다 .gitignore 대상이라 클론에 없음)

```bash
cp .env.example .env                       # 루트: CREDENTIAL_ENCRYPTION_KEY
cp vue-frontend/.env.example vue-frontend/.env   # 프론트: OAuth 클라이언트/redirect
```
- 루트 `.env` 의 `CREDENTIAL_ENCRYPTION_KEY` 는 데모 고정 키다. **그대로 두면** 데모 데이터의
  "Secret 표시" 가 복호화된다. (실제 배포 시에는 `openssl rand -base64 32` 로 교체)
- `vue-frontend/.env` 가 없으면 로그인 시 OAuth `client_id` 가 `undefined` 로 나가 토큰 교환이 실패한다.

---

## 2. 인프라 컨테이너 기동

```bash
docker compose up -d mariadb kafka eureka-server auth-server api-gateway
```

- `docker-compose.yml` + `docker-compose.override.yml` 이 자동 병합된다
  (override 가 auth-server 를 `auth_compat_db` 로 지정).
- **신규 볼륨이면** `init-db/*.sql` 이 `01_init` → `02_auth_role_compatibility` → `03_seed_demo`
  순으로 자동 실행되어 스키마 + 데모 데이터가 채워진다. → **3번 건너뛰기 가능.**

---

## 3. 기존 볼륨인 경우 (스키마/데이터 수동)

이미 `lecturedb` 볼륨이 있으면 `init-db` 스크립트는 재실행되지 않는다.

**옵션 A — 볼륨 초기화 (권장, 데이터 날아감):**
```bash
docker compose down
docker volume rm msa-lecture_mariadb_data
docker compose up -d mariadb           # init-db 자동 실행
```

**옵션 B — 수동 적용:**
```bash
# 레거시(강의) 스키마면 먼저 도메인 전환
docker exec -i lecturedb mariadb -umanager -pSqlDba-1 lecture_db < init-db/migrations/V2__project_credential_domain.sql
# auth 호환 VIEW
docker exec -i lecturedb mariadb -umanager -pSqlDba-1 < init-db/02_auth_role_compatibility.sql
# 데모 데이터
docker exec -i lecturedb mariadb -umanager -pSqlDba-1 lecture_db < init-db/03_seed_demo.sql
```

---

## 4. 마이크로서비스 이미지 빌드 + 기동

`docker-compose.yml` 은 프리빌트 이미지(`image:`)를 참조하므로 소스 변경분이 반영되려면 재빌드가 필요하다.

```bash
./scripts/build-images.sh          # course/enrollment/payment/recommend 전부
docker compose up -d               # 나머지 서비스까지 기동
```

- JVM 서비스는 **호스트에서 `bootJar`** 한 뒤 `docker/Dockerfile.jvm-runtime` 으로 패키징한다
  (컨테이너 안 Gradle 빌드가 Maven Central 429 에 걸리는 것을 피함 — JDK 21 필요).
- `recommend-service` 는 Python 이라 자체 Dockerfile 로 빌드된다.

---

## 5. 프론트엔드

```bash
cd vue-frontend
npm install
npm run dev            # http://localhost:3000
```

---

## 6. 로그인 계정 (데모, 전부 `password123`)

| 이메일 | 역할 | 용도 |
|---|---|---|
| `fe_LEADER@company.com` | LEADER | 프로젝트 5개 소유 · 승인 큐 · 위험도 대시보드 |
| `nina.lead@company.com` | LEADER | 프로젝트 2개 소유 · 교차 승인 |
| `fe_MEMBER@company.com` / `dan.dev@` / `erin.dev@` / `frank.dev@` / `grace.dev@` | MEMBER | 접근 신청 · 내 프로젝트(참여/대기/회수됨) |

레거시 시드 계정(`student@lecture.com`, `instructor@lecture.com`, `example@naver.com`)은
비밀번호 불명이며 데모에 필요 없다.

---

## 요약 (신규 볼륨 기준)

```bash
cp .env.example .env
./scripts/build-images.sh
docker compose up -d
( cd vue-frontend && npm install && npm run dev )
```
