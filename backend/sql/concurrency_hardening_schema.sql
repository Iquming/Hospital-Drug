DELIMITER $$

CREATE PROCEDURE add_column_if_missing(
  IN table_name_value VARCHAR(64),
  IN column_name_value VARCHAR(64),
  IN column_definition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = table_name_value
      AND column_name = column_name_value
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE ', table_name_value, ' ADD COLUMN ', column_name_value, ' ', column_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$

CALL add_column_if_missing('drug_stock', 'status', 'ENUM(''IN_STOCK'', ''DISPENSED'', ''LOCKED'', ''DAMAGED'', ''VOIDED'') NOT NULL DEFAULT ''IN_STOCK''')$$
CALL add_column_if_missing('drug_stock', 'version', 'BIGINT NOT NULL DEFAULT 0')$$
CALL add_column_if_missing('drug_split_code', 'version', 'BIGINT NOT NULL DEFAULT 0')$$

DROP PROCEDURE add_column_if_missing$$

DELIMITER ;

UPDATE drug_stock
SET status = CASE
  WHEN quantity > 0 THEN 'IN_STOCK'
  ELSE 'DISPENSED'
END
WHERE status IS NULL OR status = '';

CREATE TABLE IF NOT EXISTS idempotent_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id VARCHAR(160) NOT NULL UNIQUE,
  action VARCHAR(80) NOT NULL,
  target_id VARCHAR(160) NULL,
  request_hash VARCHAR(128) NOT NULL,
  status ENUM('PROCESSING', 'SUCCESS', 'FAILED') NOT NULL DEFAULT 'PROCESSING',
  response_body TEXT NULL,
  operator_name VARCHAR(100) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_idempotent_action_target (action, target_id),
  INDEX idx_idempotent_status (status),
  INDEX idx_idempotent_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS drug_code_sequence (
  biz_date CHAR(8) PRIMARY KEY COMMENT '业务日期，格式 yyyyMMdd',
  current_value BIGINT NOT NULL DEFAULT 0 COMMENT '当天已分配的最大流水号',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 上线前必须先检查该诊断表。
-- 如果表中存在记录，说明 drug_stock 中已有重复 trace_code，需要先清洗重复数据；
-- 否则 uk_drug_stock_trace_code 会跳过创建，Java 层只能友好处理数据库唯一索引已生效后的并发冲突。
DROP TABLE IF EXISTS drug_stock_duplicate_trace_diagnostic;
CREATE TABLE IF NOT EXISTS drug_stock_duplicate_trace_diagnostic AS
SELECT trace_code, COUNT(*) AS duplicate_count, GROUP_CONCAT(id ORDER BY id) AS row_ids
FROM drug_stock
WHERE trace_code IS NOT NULL AND trace_code <> ''
GROUP BY trace_code
HAVING COUNT(*) > 1;

SET @duplicate_count = (
  SELECT COUNT(*) FROM drug_stock_duplicate_trace_diagnostic
);

SET @ddl = IF(
  @duplicate_count = 0
  AND NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'drug_stock'
      AND index_name = 'uk_drug_stock_trace_code'
  ),
  'ALTER TABLE drug_stock ADD UNIQUE KEY uk_drug_stock_trace_code (trace_code)',
  'SELECT ''skip unique index: duplicate trace_code rows exist or index already exists'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP TABLE IF EXISTS drug_stock_duplicate_drug_code_diagnostic;
CREATE TABLE IF NOT EXISTS drug_stock_duplicate_drug_code_diagnostic AS
SELECT drug_code, COUNT(*) AS duplicate_count, GROUP_CONCAT(id ORDER BY id) AS row_ids
FROM drug_stock
WHERE drug_code IS NOT NULL AND drug_code <> ''
GROUP BY drug_code
HAVING COUNT(*) > 1;

SET @drug_code_duplicate_count = (
  SELECT COUNT(*) FROM drug_stock_duplicate_drug_code_diagnostic
);

SET @ddl = IF(
  @drug_code_duplicate_count = 0
  AND NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'drug_stock'
      AND index_name = 'uk_drug_stock_drug_code'
  ),
  'ALTER TABLE drug_stock ADD UNIQUE KEY uk_drug_stock_drug_code (drug_code)',
  'SELECT ''skip unique index: duplicate drug_code rows exist or index already exists'' AS message'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
