-- KeyNexus Credential 거버넌스 초기 DDL
-- 이 파일을 스키마 원본으로 사용한다.
-- 기존 lecture_db 볼륨에는 자동 재적용되지 않으므로 도메인 전환 시 새 DB로 초기화해야 한다.

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255)    NOT NULL,
    password    VARCHAR(255)    NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    role        VARCHAR(20)     NOT NULL DEFAULT 'MEMBER'
                                COMMENT 'ADMIN | LEADER | MEMBER',
    created_at  DATETIME(6)     NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NULL DEFAULT CURRENT_TIMESTAMP(6)
                                ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS projects (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    name        VARCHAR(150)    NOT NULL,
    description TEXT            NULL,
    owner_id    BIGINT          NOT NULL COMMENT '프로젝트 리더 users.id',
    status      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                                COMMENT 'ACTIVE | ARCHIVED | CLOSED',
    created_at  DATETIME(6)     NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NULL DEFAULT CURRENT_TIMESTAMP(6)
                                ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_projects_name (name),
    KEY idx_projects_owner_status (owner_id, status),
    CONSTRAINT fk_projects_owner
        FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS courses (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    project_id      BIGINT          NOT NULL COMMENT '소속 projects.id',
    title           VARCHAR(255)    NOT NULL COMMENT 'Credential 표시명',
    description     TEXT            NULL COMMENT '자산 용도 및 사용처',
    category        VARCHAR(30)     NOT NULL COMMENT 'API_KEY | SUBSCRIPTION_PLAN',
    provider        VARCHAR(100)    NOT NULL COMMENT 'GitHub, OpenAI 등 제공자',
    plan_name       VARCHAR(100)    NULL COMMENT '구독 Plan 이름',
    instructor_id   BIGINT          NOT NULL COMMENT '자산 관리자 users.id',
    expires_at      DATETIME(6)     NULL COMMENT 'API Key 만료일',
    renewal_at      DATETIME(6)     NULL COMMENT '구독 Plan 갱신일',
    last_rotated_at DATETIME(6)     NULL COMMENT '마지막 API Key 회전일',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                                    COMMENT 'ACTIVE | INACTIVE | EXPIRED | REVOKED',
    metadata        LONGTEXT        NULL COMMENT '애플리케이션에서 암호화한 Secret payload',
    created_at      DATETIME(6)     NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NULL DEFAULT CURRENT_TIMESTAMP(6)
                                    ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_courses_project_title (project_id, title),
    KEY idx_courses_project_category_status (project_id, category, status),
    KEY idx_courses_expires_status (expires_at, status),
    KEY idx_courses_renewal_status (renewal_at, status),
    CONSTRAINT fk_courses_project
        FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_courses_manager
        FOREIGN KEY (instructor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS enrollments (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    user_id          BIGINT          NOT NULL COMMENT '접근 신청자 users.id',
    project_id       BIGINT          NOT NULL COMMENT '대상 projects.id',
    reason           TEXT            NULL COMMENT '접근 신청 사유',
    status           VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                     COMMENT 'PENDING | ACTIVE | CANCELLED',
    last_accessed_at DATETIME(6)     NULL COMMENT '최근 프로젝트 자산 접근 시각',
    created_at       DATETIME(6)     NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)     NULL DEFAULT CURRENT_TIMESTAMP(6)
                                     ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_enrollments_user_project (user_id, project_id),
    KEY idx_enrollments_project_status (project_id, status),
    KEY idx_enrollments_user_status (user_id, status),
    CONSTRAINT fk_enrollments_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_enrollments_project
        FOREIGN KEY (project_id) REFERENCES projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payments (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    enrollment_id   BIGINT          NOT NULL COMMENT '원본 enrollments.id',
    user_id         BIGINT          NOT NULL COMMENT '접근 신청자 users.id',
    project_id      BIGINT          NOT NULL COMMENT '대상 projects.id',
    approved_by     BIGINT          NULL COMMENT '승인 또는 거절 처리자 users.id',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                    COMMENT 'PENDING | COMPLETED | FAILED | CANCELLED',
    transaction_id  VARCHAR(255)    NULL COMMENT '감사 티켓 UUID',
    decision_reason TEXT            NULL COMMENT '승인, 거절 또는 회수 사유',
    created_at      DATETIME(6)     NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NULL DEFAULT CURRENT_TIMESTAMP(6)
                                    ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_payments_transaction_id (transaction_id),
    KEY idx_payments_enrollment_created (enrollment_id, created_at),
    KEY idx_payments_project_status (project_id, status),
    CONSTRAINT fk_payments_enrollment
        FOREIGN KEY (enrollment_id) REFERENCES enrollments(id),
    CONSTRAINT fk_payments_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_payments_project
        FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_payments_approver
        FOREIGN KEY (approved_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS credential_audit_logs (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    event_id    VARCHAR(36)     NOT NULL COMMENT '감사 이벤트 UUID',
    project_id  BIGINT          NULL COMMENT '관련 projects.id',
    course_id   BIGINT          NULL COMMENT '관련 courses.id',
    user_id     BIGINT          NULL COMMENT '행위자 users.id',
    action      VARCHAR(50)     NOT NULL,
    result      VARCHAR(20)     NOT NULL COMMENT 'SUCCESS | FAILURE | DENIED',
    source_ip   VARCHAR(45)     NULL,
    detail      TEXT            NULL COMMENT 'Secret 값을 제외한 상세 설명',
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_audit_event_id (event_id),
    KEY idx_audit_project_created (project_id, created_at),
    KEY idx_audit_course_created (course_id, created_at),
    KEY idx_audit_user_created (user_id, created_at),
    CONSTRAINT fk_audit_project
        FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_audit_course
        FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT fk_audit_user
        FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
