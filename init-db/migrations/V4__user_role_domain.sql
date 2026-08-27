-- Existing database compatibility migration for user-service issue #4.
-- It can be applied before the full V2 domain migration; V2 repeats these
-- idempotent conversions when the remaining project schema is migrated later.

UPDATE users
SET role = CASE role
               WHEN 'INSTRUCTOR' THEN 'LEADER'
               WHEN 'STUDENT' THEN 'MEMBER'
               ELSE role
    END;

UPDATE users SET name = LEFT(name, 100);

ALTER TABLE users
    MODIFY name VARCHAR(100) NOT NULL,
    MODIFY role VARCHAR(20) NOT NULL DEFAULT 'MEMBER'
        COMMENT 'ADMIN | LEADER | MEMBER';
