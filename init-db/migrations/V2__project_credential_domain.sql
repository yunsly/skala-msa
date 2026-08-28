-- 레거시 강의 도메인을 프로젝트 기반 Credential 도메인으로 전환한다.
-- 운영 DB에 적용하기 전에 반드시 백업하고, 모든 애플리케이션을 중지한 상태에서 1회 실행한다.
-- 레거시 강의는 데이터 보존을 위해 각각 하나의 프로젝트와 SUBSCRIPTION_PLAN 자산으로 변환한다.

UPDATE users
SET role = CASE role
               WHEN 'INSTRUCTOR' THEN 'LEADER'
               WHEN 'STUDENT' THEN 'MEMBER'
               ELSE role
    END;

UPDATE users SET name = LEFT(name, 100);

ALTER TABLE users
    MODIFY name VARCHAR(100) NOT NULL,
    MODIFY role VARCHAR(20) NOT NULL DEFAULT 'MEMBER';

CREATE TABLE projects (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    name        VARCHAR(150)    NOT NULL,
    description TEXT            NULL,
    owner_id    BIGINT          NOT NULL,
    status      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(6)     NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NULL DEFAULT CURRENT_TIMESTAMP(6)
                                ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_projects_name (name),
    KEY idx_projects_owner_status (owner_id, status),
    CONSTRAINT fk_projects_owner
        FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO projects (id, name, description, owner_id, status, created_at, updated_at)
SELECT c.id,
       CONCAT(LEFT(c.title, 120), ' [legacy-', c.id, ']'),
       c.description,
       c.instructor_id,
       CASE WHEN c.status = 'ACTIVE' THEN 'ACTIVE' ELSE 'ARCHIVED' END,
       c.created_at,
       c.updated_at
FROM courses c;

ALTER TABLE courses
    ADD COLUMN project_id BIGINT NULL AFTER id,
    ADD COLUMN provider VARCHAR(100) NULL AFTER category,
    ADD COLUMN plan_name VARCHAR(100) NULL AFTER provider,
    ADD COLUMN expires_at DATETIME(6) NULL AFTER enrollment_count,
    ADD COLUMN renewal_at DATETIME(6) NULL AFTER expires_at,
    ADD COLUMN last_rotated_at DATETIME(6) NULL AFTER renewal_at,
    ADD COLUMN metadata LONGTEXT NULL AFTER status;

UPDATE courses
SET project_id = id,
    category = 'SUBSCRIPTION_PLAN',
    provider = 'LEGACY_MIGRATION',
    plan_name = LEFT(title, 100),
    renewal_at = NULL,
    metadata = NULL;

ALTER TABLE courses
    DROP COLUMN price,
    DROP COLUMN enrollment_count,
    MODIFY project_id BIGINT NOT NULL,
    MODIFY category VARCHAR(30) NOT NULL,
    MODIFY provider VARCHAR(100) NOT NULL,
    MODIFY status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD UNIQUE KEY uq_courses_project_title (project_id, title),
    ADD KEY idx_courses_project_category_status (project_id, category, status),
    ADD KEY idx_courses_expires_status (expires_at, status),
    ADD KEY idx_courses_renewal_status (renewal_at, status),
    ADD CONSTRAINT fk_courses_project
        FOREIGN KEY (project_id) REFERENCES projects(id);

ALTER TABLE enrollments
    ADD COLUMN project_id BIGINT NULL AFTER course_id,
    ADD COLUMN reason TEXT NULL AFTER project_id,
    ADD COLUMN last_accessed_at DATETIME(6) NULL AFTER status,
    ADD KEY idx_enrollments_user_status (user_id, status);

UPDATE enrollments SET project_id = course_id;

ALTER TABLE payments
    ADD COLUMN enrollment_id BIGINT NULL AFTER id,
    ADD COLUMN project_id BIGINT NULL AFTER course_id,
    ADD COLUMN approved_by BIGINT NULL AFTER project_id,
    ADD COLUMN decision_reason TEXT NULL AFTER transaction_id;

UPDATE payments SET project_id = course_id;

INSERT INTO enrollments (
    user_id,
    course_id,
    project_id,
    reason,
    status,
    created_at,
    updated_at
)
SELECT p.user_id,
       p.course_id,
       p.course_id,
       '레거시 승인 내역에서 생성',
       CASE
           WHEN p.status = 'COMPLETED' THEN 'ACTIVE'
           WHEN p.status = 'CANCELLED' THEN 'CANCELLED'
           ELSE 'PENDING'
       END,
       p.created_at,
       p.updated_at
FROM payments p
LEFT JOIN enrollments e
       ON e.user_id = p.user_id
      AND e.course_id = p.course_id
WHERE e.id IS NULL;

UPDATE payments p
JOIN enrollments e
  ON e.user_id = p.user_id
 AND e.course_id = p.course_id
SET p.enrollment_id = e.id,
    p.project_id = e.project_id;

-- 레거시 course_id FK 이름은 초기 DDL 기준이다.
ALTER TABLE payments DROP FOREIGN KEY payments_ibfk_2;
ALTER TABLE enrollments DROP FOREIGN KEY enrollments_ibfk_2;

-- 초기 DDL과 과거 Hibernate update가 생성한 레거시 유니크 인덱스를 제거한다.
ALTER TABLE enrollments DROP INDEX IF EXISTS uq_user_course;
ALTER TABLE enrollments DROP INDEX IF EXISTS UKg1muiskd02x66lpy6fqcj6b9q;

ALTER TABLE payments
    DROP COLUMN amount,
    DROP COLUMN course_id,
    MODIFY enrollment_id BIGINT NOT NULL,
    MODIFY project_id BIGINT NOT NULL,
    ADD KEY idx_payments_enrollment_created (enrollment_id, created_at),
    ADD KEY idx_payments_project_status (project_id, status),
    ADD CONSTRAINT fk_payments_enrollment
        FOREIGN KEY (enrollment_id) REFERENCES enrollments(id),
    ADD CONSTRAINT fk_payments_project
        FOREIGN KEY (project_id) REFERENCES projects(id),
    ADD CONSTRAINT fk_payments_approver
        FOREIGN KEY (approved_by) REFERENCES users(id);

ALTER TABLE enrollments
    DROP COLUMN course_id,
    MODIFY project_id BIGINT NOT NULL,
    ADD UNIQUE KEY uq_enrollments_user_project (user_id, project_id),
    ADD KEY idx_enrollments_project_status (project_id, status),
    ADD CONSTRAINT fk_enrollments_project
        FOREIGN KEY (project_id) REFERENCES projects(id);

CREATE TABLE credential_audit_logs (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    event_id    VARCHAR(36)     NOT NULL,
    project_id  BIGINT          NULL,
    course_id   BIGINT          NULL,
    user_id     BIGINT          NULL,
    action      VARCHAR(50)     NOT NULL,
    result      VARCHAR(20)     NOT NULL,
    source_ip   VARCHAR(45)     NULL,
    detail      TEXT            NULL,
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
