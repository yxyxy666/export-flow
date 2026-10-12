-- 规格 data-model.md / ADR-0008；TC-224验证数据库执行权。
CREATE TABLE task_execution (
 task_id CHAR(36) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 owner_id VARCHAR(128) NOT NULL, fence BIGINT NOT NULL,
 lease_until DATETIME(3) NOT NULL, heartbeat_at DATETIME(3) NOT NULL,
 progress_version BIGINT NOT NULL DEFAULT 0, attempt_started_at DATETIME(3) NOT NULL,
 attempt_deadline DATETIME(3) NOT NULL, recovery_count INT NOT NULL DEFAULT 0,
 next_recovery_at DATETIME(3) NOT NULL,
 FOREIGN KEY(task_id) REFERENCES export_tasks(task_id) ON DELETE RESTRICT,
 CONSTRAINT ck_execution_budget CHECK (fence > 0 AND recovery_count BETWEEN 0 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
