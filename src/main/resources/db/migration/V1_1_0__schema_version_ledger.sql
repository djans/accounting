CREATE TABLE IF NOT EXISTS schema_version_history (
    singleton_id TINYINT NOT NULL,
    version      VARCHAR(32) NOT NULL,
    applied_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (singleton_id),
    CONSTRAINT chk_schema_version_history_singleton CHECK (singleton_id = 1)
);

INSERT INTO schema_version_history (singleton_id, version, applied_at)
SELECT 1, '1.0.0', CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (
    SELECT 1 FROM schema_version_history WHERE singleton_id = 1
);
