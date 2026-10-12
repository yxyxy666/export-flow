-- 规格 provided-data.md；只读适配既有订单，不创建订单表或写入数据。
CREATE ALGORITHM=MERGE SQL SECURITY INVOKER VIEW orders AS
SELECT id, order_no, customer_name,
       COALESCE(customer_phone, '') AS customer_phone,
       COALESCE(order_status, status) AS status,
       sales_channel AS channel, total_amount AS amount,
       currency, COALESCE(shipping_province, '') AS region,
       CAST(created_at AS DATETIME) AS created_at
FROM exportflow.orders;
