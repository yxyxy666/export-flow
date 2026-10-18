-- 统一删除：任务业务记录硬删除，独立技术墓碑用于迟到消息裁决。
ALTER TABLE export_tasks
  ADD COLUMN deletion_state VARCHAR(24) NOT NULL DEFAULT 'NONE',
  ADD COLUMN deletion_mode VARCHAR(24) NULL,
  ADD COLUMN deletion_operation_id CHAR(36) COLLATE utf8mb4_bin NULL,
  ADD COLUMN deletion_requested_at DATETIME(3) NULL,
  ADD COLUMN deletion_error_code VARCHAR(64) NULL,
  ADD CONSTRAINT ck_task_deletion_state CHECK (deletion_state IN ('NONE','REQUESTED','STOPPING','CLEANUP_PENDING','CLEANING','COMPLETED','FAILED_RETRYABLE')),
  ADD CONSTRAINT ck_task_deletion_mode CHECK (deletion_mode IS NULL OR deletion_mode IN ('ACTIVE_STOP','TERMINAL_CLEANUP'));

CREATE TABLE task_deletion_tombstones (
  task_id CHAR(36) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
  task_no_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL,
  operation_id CHAR(36) COLLATE utf8mb4_bin NOT NULL,
  deleted_at DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NULL,
  state VARCHAR(16) NOT NULL,
  CONSTRAINT ck_task_tombstone_state CHECK (state IN ('ACTIVE','EXPIRED')),
  INDEX ix_task_tombstone_operation(operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE task_deletion_operations (
  operation_id CHAR(36) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
  task_id CHAR(36) COLLATE utf8mb4_bin NOT NULL,
  request_hash BINARY(32) NOT NULL,
  expected_version BIGINT NOT NULL,
  mode VARCHAR(24) NOT NULL,
  prior_status VARCHAR(16) NOT NULL,
  state VARCHAR(24) NOT NULL,
  result_json JSON NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_task_deletion_operation_task(operation_id,task_id),
  INDEX ix_task_deletion_task(task_id),
  CONSTRAINT ck_task_deletion_operation_mode CHECK (mode IN ('ACTIVE_STOP','TERMINAL_CLEANUP')),
  CONSTRAINT ck_task_deletion_operation_state CHECK (state IN ('ACCEPTED','STOPPING','CLEANUP_PENDING','CLEANING','COMPLETED','FAILED_RETRYABLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE task_deletion_jobs (
  job_id CHAR(36) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
  task_id CHAR(36) COLLATE utf8mb4_bin NOT NULL,
  operation_id CHAR(36) COLLATE utf8mb4_bin NOT NULL,
  phase VARCHAR(24) NOT NULL,
  attempt_no INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL,
  lease_owner VARCHAR(128) NULL,
  lease_until DATETIME(3) NULL,
  last_error_code VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_task_deletion_job_operation(operation_id),
  INDEX ix_task_deletion_job_due(phase,next_attempt_at,lease_until),
  CONSTRAINT ck_task_deletion_job_phase CHECK (phase IN ('STOP_WAIT','DB_CLEANUP','FILE_CLEANUP','DONE','RETRYABLE_FAILURE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
