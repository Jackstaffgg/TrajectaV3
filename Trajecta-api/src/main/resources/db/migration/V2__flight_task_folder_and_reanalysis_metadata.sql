
ALTER TABLE IF EXISTS flight_task
    ADD COLUMN IF NOT EXISTS folder_name VARCHAR(120);

ALTER TABLE IF EXISTS flight_task
    ADD COLUMN IF NOT EXISTS analysis_params_json VARCHAR(4000);

ALTER TABLE IF EXISTS flight_task
    ADD COLUMN IF NOT EXISTS source_task_id BIGINT;

DO $$
BEGIN
    IF to_regclass('flight_task') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM pg_constraint
           WHERE conname = 'fk_flight_task_source_task'
       )
    THEN
        ALTER TABLE flight_task
            ADD CONSTRAINT fk_flight_task_source_task
            FOREIGN KEY (source_task_id)
            REFERENCES flight_task(id)
            ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_flight_task_source_task_id
    ON flight_task (source_task_id);

CREATE INDEX IF NOT EXISTS idx_flight_task_folder_name
    ON flight_task (folder_name);

