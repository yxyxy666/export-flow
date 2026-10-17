-- 设计：specs/004-task-management/contracts/assembly-repair.md；禁止旧worker混跑。
ALTER TABLE export_tasks
 ADD COLUMN trusted_processed_count BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN execution_phase VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
 ADD COLUMN repair_state VARCHAR(16) NOT NULL DEFAULT 'NONE',
 ADD COLUMN repair_epoch BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN repair_attempt INT NOT NULL DEFAULT 0,
 ADD COLUMN repair_reason_code VARCHAR(64) NULL,
 ADD CONSTRAINT ck_trusted_count CHECK (trusted_processed_count BETWEEN 0 AND total_count),
 ADD CONSTRAINT ck_repair_budget CHECK (repair_attempt BETWEEN 0 AND 3 AND repair_epoch>=0),
 ADD CONSTRAINT ck_execution_phase CHECK (execution_phase IN ('NOT_STARTED','COLLECTING','CHECKING','REBUILDING','ASSEMBLING','COMPLETE')),
 ADD CONSTRAINT ck_repair_state CHECK (repair_state IN ('NONE','CHECKING','REBUILDING','PAUSING','PAUSED','FAILED'));
UPDATE export_tasks SET trusted_processed_count=IF(status='SUCCEEDED',total_count,0),
 execution_phase=CASE WHEN status IN ('SUCCEEDED','FAILED') THEN 'COMPLETE' WHEN status='RUNNING' THEN 'COLLECTING' ELSE 'NOT_STARTED' END;
ALTER TABLE export_checkpoints
 ADD COLUMN manifest_sealed BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN manifest_version BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN segment_count INT NOT NULL DEFAULT 0,
 ADD COLUMN manifest_row_count BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN manifest_digest CHAR(64) COLLATE utf8mb4_bin NULL;
-- MySQL对旧匿名UNIQUE使用首列名task_id命名；先建立外键需要的替代索引。
ALTER TABLE export_data_blocks ADD INDEX ix_blocks_generation(task_id,data_generation,sequence), DROP INDEX task_id;
CREATE TABLE export_data_segments (
 task_id CHAR(36) COLLATE utf8mb4_bin NOT NULL, data_generation BIGINT NOT NULL, sequence INT NOT NULL,
 previous_cursor_json JSON NULL, last_cursor_json JSON NOT NULL, row_count INT NOT NULL,
 configuration_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL, format_version INT NOT NULL,
 content_checksum CHAR(64) COLLATE utf8mb4_bin NOT NULL,
 active_block_id CHAR(36) COLLATE utf8mb4_bin NULL,
 selection_version BIGINT NOT NULL DEFAULT 0, verified_repair_epoch BIGINT NOT NULL DEFAULT 0,
 state VARCHAR(20) NOT NULL DEFAULT 'VERIFIED',
 PRIMARY KEY(task_id,data_generation,sequence),
 FOREIGN KEY(task_id) REFERENCES export_tasks(task_id) ON DELETE RESTRICT,
 CONSTRAINT ck_segment_count CHECK(row_count BETWEEN 1 AND 1000),
 CONSTRAINT ck_segment_state CHECK(state IN ('PENDING','VERIFIED','REBUILD_REQUIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
CREATE TABLE export_task_repairs (
 task_id CHAR(36) COLLATE utf8mb4_bin NOT NULL, repair_epoch BIGINT NOT NULL,
 attempt_no INT NOT NULL, checkpoint_version BIGINT NOT NULL, manifest_version BIGINT NOT NULL,
 reason_code VARCHAR(64) NOT NULL, state VARCHAR(16) NOT NULL,
 created_at DATETIME(3) NOT NULL, finished_at DATETIME(3) NULL,
 PRIMARY KEY(task_id,repair_epoch), UNIQUE(task_id,attempt_no),
 FOREIGN KEY(task_id) REFERENCES export_tasks(task_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
CREATE TABLE export_repair_segments (
 task_id CHAR(36) COLLATE utf8mb4_bin NOT NULL, repair_epoch BIGINT NOT NULL, sequence INT NOT NULL,
 old_block_id CHAR(36) COLLATE utf8mb4_bin NULL, replacement_block_id CHAR(36) COLLATE utf8mb4_bin NULL,
 expected_selection_version BIGINT NOT NULL, state VARCHAR(16) NOT NULL,
 PRIMARY KEY(task_id,repair_epoch,sequence),
 FOREIGN KEY(task_id,repair_epoch) REFERENCES export_task_repairs(task_id,repair_epoch) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
