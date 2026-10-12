-- 规格 data-model.md / excel-design.md；TC-235/236验证引用及清理互斥。
CREATE TABLE export_checkpoints (
 task_id CHAR(36) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 data_generation BIGINT NOT NULL, checkpoint_version BIGINT NOT NULL DEFAULT 0,
 verified_prefix_count BIGINT NOT NULL DEFAULT 0,
 cursor_created_at DATETIME(0) NULL, cursor_order_id BIGINT NULL,
 processed_high_water BIGINT NOT NULL DEFAULT 0,
 configuration_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL, format_version INT NOT NULL,
 FOREIGN KEY(task_id) REFERENCES export_tasks(task_id) ON DELETE RESTRICT,
 CONSTRAINT ck_checkpoint_count CHECK (verified_prefix_count >= 0 AND processed_high_water >= verified_prefix_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
CREATE TABLE export_data_blocks (
 block_id CHAR(36) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 task_id CHAR(36) COLLATE utf8mb4_bin NOT NULL, data_generation BIGINT NOT NULL,
 sequence INT NOT NULL, previous_cursor_json JSON NULL, last_cursor_json JSON NOT NULL,
 row_count INT NOT NULL, byte_size BIGINT NOT NULL,
 checksum CHAR(64) COLLATE utf8mb4_bin NOT NULL,
 storage_ref VARCHAR(512) COLLATE utf8mb4_bin NOT NULL UNIQUE,
 format_version INT NOT NULL, configuration_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL,
 created_by_owner VARCHAR(128) NOT NULL, created_by_fence BIGINT NOT NULL,
 UNIQUE(task_id,data_generation,sequence),
 FOREIGN KEY(task_id) REFERENCES export_tasks(task_id) ON DELETE RESTRICT,
 CONSTRAINT ck_block_budget CHECK (row_count BETWEEN 1 AND 1000 AND byte_size BETWEEN 1 AND 16777216)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
CREATE TABLE export_artifacts (
 artifact_id CHAR(36) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 task_id CHAR(36) COLLATE utf8mb4_bin NOT NULL, data_generation BIGINT NOT NULL,
 configuration_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL,
 storage_ref VARCHAR(512) COLLATE utf8mb4_bin NOT NULL UNIQUE,
 row_count BIGINT NOT NULL, byte_size BIGINT NOT NULL,
 checksum CHAR(64) COLLATE utf8mb4_bin NOT NULL, file_name VARCHAR(256) NOT NULL,
 state VARCHAR(16) NOT NULL, created_by_owner VARCHAR(128) NOT NULL, created_by_fence BIGINT NOT NULL,
 ready_at DATETIME(3) NOT NULL, adopted_by_fence BIGINT NULL,
 cleanup_token CHAR(36) COLLATE utf8mb4_bin NULL,
 FOREIGN KEY(task_id) REFERENCES export_tasks(task_id) ON DELETE RESTRICT,
 CONSTRAINT ck_artifact_state CHECK (state IN ('READY','ADOPTED','CLEANING','CLEANED')),
 CONSTRAINT ck_artifact_budget CHECK (row_count BETWEEN 1 AND 200000 AND byte_size > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
ALTER TABLE export_tasks ADD CONSTRAINT fk_task_result FOREIGN KEY(result_artifact_id) REFERENCES export_artifacts(artifact_id) ON DELETE RESTRICT;
CREATE TABLE export_cleanup_candidates (
 candidate_id CHAR(36) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 storage_ref VARCHAR(512) COLLATE utf8mb4_bin NOT NULL UNIQUE,
 task_id CHAR(36) COLLATE utf8mb4_bin NOT NULL,
 owner_id VARCHAR(128) NULL, fence BIGINT NULL, kind VARCHAR(16) NOT NULL,
 observed_at DATETIME(3) NOT NULL, state VARCHAR(16) NOT NULL,
 cleanup_token CHAR(36) COLLATE utf8mb4_bin NULL,
 FOREIGN KEY(task_id) REFERENCES export_tasks(task_id) ON DELETE RESTRICT,
 INDEX ix_cleanup_due(state,observed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
