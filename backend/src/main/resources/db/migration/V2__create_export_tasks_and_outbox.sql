-- 规格 data-model.md；TC-217/218验证唯一约束及四表事务。
CREATE TABLE export_tasks (
 task_id CHAR(36) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 task_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL UNIQUE,
 scope_type VARCHAR(16) NOT NULL, filter_json JSON NULL, fields_json JSON NOT NULL,
 total_count BIGINT NOT NULL, processed_count BIGINT NOT NULL DEFAULT 0,
 status VARCHAR(16) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(0) NOT NULL, finished_at DATETIME(0) NULL,
 error_code VARCHAR(64) NULL, error_summary VARCHAR(256) NULL,
 file_name VARCHAR(256) NULL, file_size BIGINT NULL, result_artifact_id CHAR(36) COLLATE utf8mb4_bin NULL,
 INDEX ix_tasks_time(created_at,task_id),
 CONSTRAINT ck_tasks_count CHECK (processed_count >= 0 AND processed_count <= total_count AND total_count BETWEEN 1 AND 200000),
 CONSTRAINT ck_tasks_status CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED')),
 CONSTRAINT ck_tasks_scope CHECK (scope_type IN ('SELECTED','FILTERED')),
 CONSTRAINT ck_tasks_terminal CHECK ((status IN ('PENDING','RUNNING') AND finished_at IS NULL) OR (status IN ('SUCCEEDED','FAILED') AND finished_at IS NOT NULL)),
 CONSTRAINT ck_tasks_success CHECK (status <> 'SUCCEEDED' OR (processed_count = total_count AND result_artifact_id IS NOT NULL AND file_name IS NOT NULL AND file_size > 0)),
 CONSTRAINT ck_tasks_failure CHECK (status <> 'FAILED' OR (error_code IS NOT NULL AND error_summary IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
CREATE TABLE export_task_orders (
 task_id CHAR(36) COLLATE utf8mb4_bin NOT NULL, order_id BIGINT NOT NULL,
 PRIMARY KEY(task_id,order_id),
 FOREIGN KEY(task_id) REFERENCES export_tasks(task_id) ON DELETE RESTRICT,
 FOREIGN KEY(order_id) REFERENCES exportflow.orders(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
CREATE TABLE export_idempotency (
 key_hash BINARY(32) NOT NULL PRIMARY KEY, canonical_version INT NOT NULL,
 request_hash BINARY(32) NOT NULL, task_id CHAR(36) COLLATE utf8mb4_bin NOT NULL UNIQUE,
 FOREIGN KEY(task_id) REFERENCES export_tasks(task_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
CREATE TABLE command_outbox (
 event_id CHAR(36) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 task_id CHAR(36) COLLATE utf8mb4_bin NOT NULL,
 type VARCHAR(32) NOT NULL, schema_version INT NOT NULL DEFAULT 1,
 status VARCHAR(16) NOT NULL, accepted_at DATETIME(3) NOT NULL,
 dispatch_deadline DATETIME(3) NOT NULL, next_attempt_at DATETIME(3) NOT NULL,
 attempt_no INT NOT NULL DEFAULT 0, lease_owner VARCHAR(128) NULL,
 lease_token CHAR(36) COLLATE utf8mb4_bin NULL, lease_until DATETIME(3) NULL,
 sent_at DATETIME(3) NULL, closed_at DATETIME(3) NULL,
 close_reason VARCHAR(64) NULL, last_error_code VARCHAR(64) NULL,
 UNIQUE(task_id,type), INDEX ix_outbox_due(status,next_attempt_at,dispatch_deadline),
 FOREIGN KEY(task_id) REFERENCES export_tasks(task_id) ON DELETE RESTRICT,
 CONSTRAINT ck_outbox_status CHECK (status IN ('READY','LEASED','SENT','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
CREATE TABLE dispatch_attempts (
 event_id CHAR(36) COLLATE utf8mb4_bin NOT NULL, attempt_no INT NOT NULL,
 lease_token CHAR(36) COLLATE utf8mb4_bin NOT NULL,
 started_at DATETIME(3) NOT NULL, crash_retry_at DATETIME(3) NOT NULL,
 finished_at DATETIME(3) NULL, outcome VARCHAR(32) NULL, safe_error VARCHAR(64) NULL,
 PRIMARY KEY(event_id,attempt_no),
 FOREIGN KEY(event_id) REFERENCES command_outbox(event_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
