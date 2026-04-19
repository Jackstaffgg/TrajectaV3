-- Adds hierarchical task folders with support for empty folders and nested structure.

CREATE TABLE IF NOT EXISTS task_folder (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    parent_id BIGINT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

DO $$
DECLARE
    users_table regclass;
BEGIN
    users_table := COALESCE(to_regclass('users'), to_regclass('"user"'));

    IF users_table IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_task_folder_user')
    THEN
        EXECUTE format(
            'ALTER TABLE task_folder ADD CONSTRAINT fk_task_folder_user FOREIGN KEY (user_id) REFERENCES %s(id) ON DELETE CASCADE',
            users_table
        );
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_task_folder_parent') THEN
        ALTER TABLE task_folder
            ADD CONSTRAINT fk_task_folder_parent
            FOREIGN KEY (parent_id)
            REFERENCES task_folder(id)
            ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_task_folder_user_id ON task_folder (user_id);
CREATE INDEX IF NOT EXISTS idx_task_folder_parent_id ON task_folder (parent_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_task_folder_user_parent_name ON task_folder (user_id, parent_id, name);

