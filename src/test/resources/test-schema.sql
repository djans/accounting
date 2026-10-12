ALTER TABLE app_users ADD COLUMN IF NOT EXISTS short_id VARCHAR(7);
CREATE UNIQUE INDEX IF NOT EXISTS uk_app_users_short_id ON app_users (short_id);

CREATE TABLE IF NOT EXISTS user_company_memberships (
    user_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'AUDITOR',
    PRIMARY KEY (user_id, company_id)
);
