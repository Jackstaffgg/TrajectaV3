-- Adds folder linkage for tasks via task_folder hierarchy.

ALTER TABLE IF EXISTS flight_task
    ADD COLUMN IF NOT EXISTS folder_id BIGINT;

DO $$
BEGIN
    IF to_regclass('flight_task') IS NOT NULL
       AND to_regclass('task_folder') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint
           WHERE conname = 'fk_flight_task_folder'
       )
    THEN
        ALTER TABLE flight_task
            ADD CONSTRAINT fk_flight_task_folder
            FOREIGN KEY (folder_id)
            REFERENCES task_folder(id)
            ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_flight_task_folder_id
    ON flight_task (folder_id);

