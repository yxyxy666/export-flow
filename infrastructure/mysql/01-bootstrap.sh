#!/usr/bin/env bash
# 仅在本项目MySQL空数据卷首次启动时执行；不修改任何外部数据库。
(
set -euo pipefail

for bootstrap_secret in "$EXPORT_FLOW_DB_PASSWORD" "$EXPORT_FLOW_MIGRATOR_PASSWORD"; do
  if [[ ! "$bootstrap_secret" =~ ^[0-9a-f]{64}$ ]]; then
    echo '应用和迁移密码须由make infra生成（64位小写十六进制）。' >&2
    exit 1
  fi
done

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot <<SQL
CREATE DATABASE export_flow CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci;
CREATE DATABASE exportflow CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci;

-- 与示例项目V1和V6后的来源契约一致；空表可支持本应用的只读视图与外键。
CREATE TABLE exportflow.orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(64) NOT NULL,
  customer_name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  total_amount DECIMAL(18,2) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  order_status VARCHAR(32) NULL,
  sales_channel VARCHAR(32) NULL,
  customer_phone VARCHAR(32) NULL,
  currency VARCHAR(8) NULL,
  shipping_province VARCHAR(64) NULL,
  CONSTRAINT uk_orders_order_no UNIQUE (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE USER 'export_flow_app'@'%' IDENTIFIED BY '$EXPORT_FLOW_DB_PASSWORD';
CREATE USER 'export_flow_migrator'@'%' IDENTIFIED BY '$EXPORT_FLOW_MIGRATOR_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON export_flow.* TO 'export_flow_app'@'%';
GRANT SELECT ON exportflow.orders TO 'export_flow_app'@'%';
GRANT ALL PRIVILEGES ON export_flow.* TO 'export_flow_migrator'@'%';
GRANT SELECT, REFERENCES ON exportflow.orders TO 'export_flow_migrator'@'%';
SQL
)
