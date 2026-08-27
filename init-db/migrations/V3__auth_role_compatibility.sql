-- Apply this migration once to an existing MariaDB volume.
-- New installations execute init-db/02_auth_role_compatibility.sql automatically.

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
