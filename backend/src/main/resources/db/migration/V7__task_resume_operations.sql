-- 设计：specs/005-task-resume-and-live-progress/contracts.md；继续回执与原暂停历史隔离。
CREATE TABLE task_resume_operations (
 operation_id CHAR(36) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 task_id CHAR(36) COLLATE utf8mb4_bin NOT NULL,
 request_hash BINARY(32) NOT NULL, expected_control_version BIGINT NOT NULL,
 result_json JSON NOT NULL, created_at DATETIME(3) NOT NULL,
 FOREIGN KEY(task_id) REFERENCES export_tasks(task_id) ON DELETE RESTRICT,
 CONSTRAINT ck_resume_control CHECK (expected_control_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
