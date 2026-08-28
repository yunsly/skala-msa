-- Prebuilt auth-server compatibility layer.
-- The domain database keeps ADMIN | LEADER | MEMBER as the source of truth,
-- while the legacy auth-server reads only STUDENT | INSTRUCTOR through this view.

CREATE DATABASE IF NOT EXISTS auth_compat_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE OR REPLACE SQL SECURITY INVOKER VIEW auth_compat_db.users AS
SELECT
    id,
    email,
    password,
    name,
    CAST(
        CASE role
            WHEN 'MEMBER' THEN 'STUDENT'
            WHEN 'LEADER' THEN 'INSTRUCTOR'
            WHEN 'ADMIN' THEN 'INSTRUCTOR'
            ELSE role
        END AS CHAR(20)
    ) AS role,
    created_at,
    updated_at
FROM lecture_db.users;

GRANT SELECT ON auth_compat_db.* TO 'manager'@'%';
FLUSH PRIVILEGES;
