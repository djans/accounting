ALTER TABLE user_company_memberships ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'AUDITOR';
UPDATE user_company_memberships
SET role = COALESCE((
    SELECT CASE WHEN app_users.role = 'VIEWER' THEN 'AUDITOR' ELSE app_users.role END
    FROM app_users
    WHERE app_users.id = user_company_memberships.user_id
), 'AUDITOR');
ALTER TABLE app_users ADD COLUMN short_id VARCHAR(7);
CREATE UNIQUE INDEX uk_app_users_short_id ON app_users (short_id);
