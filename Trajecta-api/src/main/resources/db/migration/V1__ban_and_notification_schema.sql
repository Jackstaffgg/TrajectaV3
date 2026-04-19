-- Baseline migration for ban-related schema and notification type normalization.

CREATE TABLE IF NOT EXISTS user_punishment (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    punished_by BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expired_at TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_user_punishment_user
    ON user_punishment (user_id);

CREATE INDEX IF NOT EXISTS idx_user_punishment_expired
    ON user_punishment (expired_at);

CREATE INDEX IF NOT EXISTS idx_user_punishment_punished_by
    ON user_punishment (punished_by);

DO $$
DECLARE
    users_table regclass;
BEGIN
    users_table := COALESCE(to_regclass('users'), to_regclass('"user"'));

    IF users_table IS NOT NULL THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_user_punishment_user') THEN
            EXECUTE format(
                'ALTER TABLE user_punishment ADD CONSTRAINT fk_user_punishment_user FOREIGN KEY (user_id) REFERENCES %s(id) ON DELETE CASCADE',
                users_table
            );
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_user_punishment_punished_by') THEN
            EXECUTE format(
                'ALTER TABLE user_punishment ADD CONSTRAINT fk_user_punishment_punished_by FOREIGN KEY (punished_by) REFERENCES %s(id) ON DELETE RESTRICT',
                users_table
            );
        END IF;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'notifications'
          AND column_name = 'type'
          AND data_type NOT IN ('character varying', 'text')
    ) THEN
        ALTER TABLE notifications
            ALTER COLUMN type TYPE VARCHAR(32)
            USING (
                CASE type
                    WHEN 0 THEN 'TASK_COMPLETED'
                    WHEN 1 THEN 'SYSTEM_ALERT'
                    WHEN 2 THEN 'SYSTEM_NEWS'
                    WHEN 3 THEN 'TASK_FAILED'
                    ELSE 'SYSTEM_NEWS'
                END
            );
    END IF;
END $$;

