-- 设计：specs/004-task-management/data-model.md；验证：TaskManagementMigrationTest。
ALTER TABLE export_tasks
 DROP CHECK ck_tasks_status, DROP CHECK ck_tasks_terminal,
 ADD COLUMN control_version BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN pause_requested_at DATETIME(3) NULL,
 ADD COLUMN paused_at DATETIME(3) NULL,
 ADD CONSTRAINT ck_tasks_status CHECK (status IN ('PENDING','RUNNING','PAUSING','PAUSED','SUCCEEDED','FAILED')),
 ADD CONSTRAINT ck_tasks_terminal CHECK ((status IN ('PENDING','RUNNING','PAUSING','PAUSED') AND finished_at IS NULL) OR (status IN ('SUCCEEDED','FAILED') AND finished_at IS NOT NULL)),
 ADD CONSTRAINT ck_task_control CHECK (control_version >= 0),
 ADD INDEX ix_tasks_status_time(status,created_at,task_id);
ALTER TABLE task_execution ADD COLUMN control_version BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN pause_ack_version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE task_pause_operations (
 operation_id CHAR(36) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 task_id CHAR(36) COLLATE utf8mb4_bin NOT NULL,
 request_hash BINARY(32) NOT NULL, expected_control_version BIGINT NOT NULL,
 result_json JSON NOT NULL, created_at DATETIME(3) NOT NULL,
 FOREIGN KEY(task_id) REFERENCES export_tasks(task_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
CREATE TABLE task_activity (
 id INT NOT NULL PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_activity_singleton CHECK (id=1 AND version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
INSERT INTO task_activity(id,version) VALUES(1,0);
CREATE TABLE task_progress_outbox (
 task_id CHAR(36) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 pending_version BIGINT NOT NULL, activity_version BIGINT NOT NULL,
 published_version BIGINT NOT NULL DEFAULT -1,
 lease_owner VARCHAR(128) NULL, lease_token CHAR(36) COLLATE utf8mb4_bin NULL,
 lease_until DATETIME(3) NULL, next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY(task_id) REFERENCES export_tasks(task_id) ON DELETE RESTRICT,
 CONSTRAINT ck_progress_versions CHECK (pending_version>=0 AND published_version>=-1 AND published_version<=pending_version),
 INDEX ix_progress_due(next_attempt_at,lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
INSERT INTO task_progress_outbox(task_id,pending_version,activity_version)
 SELECT task_id,version,0 FROM export_tasks;
