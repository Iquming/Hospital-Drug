-- 门诊处方审方、原路退药、HIS 防重放与回传恢复升级
-- 执行前请备份数据库。本脚本在 his_integration_schema.sql 之后执行。

DELIMITER $$

CREATE PROCEDURE add_professional_column_if_missing(
  IN table_name_value VARCHAR(64),
  IN column_name_value VARCHAR(64),
  IN column_definition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
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

CALL add_professional_column_if_missing('drug_application', 'prescriber_id', 'VARCHAR(100) NULL')$$
CALL add_professional_column_if_missing('drug_application', 'patient_gender', 'VARCHAR(20) NULL')$$
CALL add_professional_column_if_missing('drug_application', 'patient_age', 'INT NULL')$$
CALL add_professional_column_if_missing('drug_application', 'prescriber_name', 'VARCHAR(100) NULL')$$
CALL add_professional_column_if_missing('drug_application', 'diagnosis', 'VARCHAR(500) NULL')$$
CALL add_professional_column_if_missing('drug_application', 'allergy_info', 'VARCHAR(500) NULL')$$
CALL add_professional_column_if_missing('drug_application', 'review_status', 'VARCHAR(20) NOT NULL DEFAULT ''PENDING''')$$
CALL add_professional_column_if_missing('drug_application', 'review_comment', 'VARCHAR(1000) NULL')$$
CALL add_professional_column_if_missing('drug_application', 'reviewed_by', 'VARCHAR(100) NULL')$$
CALL add_professional_column_if_missing('drug_application', 'reviewed_at', 'DATETIME NULL')$$

CALL add_professional_column_if_missing('drug_application_item', 'dosage', 'VARCHAR(100) NULL')$$
CALL add_professional_column_if_missing('drug_application_item', 'frequency', 'VARCHAR(100) NULL')$$
CALL add_professional_column_if_missing('drug_application_item', 'administration_route', 'VARCHAR(100) NULL')$$
CALL add_professional_column_if_missing('drug_application_item', 'usage_instruction', 'VARCHAR(500) NULL')$$

CALL add_professional_column_if_missing('dispense_record', 'returned_units', 'INT NOT NULL DEFAULT 0')$$
CALL add_professional_column_if_missing('dispense_record', 'operation_type', 'VARCHAR(20) NOT NULL DEFAULT ''DISPENSE''')$$
CALL add_professional_column_if_missing('dispense_record', 'related_record_id', 'BIGINT NULL')$$

DROP PROCEDURE add_professional_column_if_missing$$

DELIMITER ;

CREATE TABLE IF NOT EXISTS his_request_nonce (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  nonce_value VARCHAR(120) NOT NULL,
  request_time DATETIME NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_his_request_nonce (nonce_value),
  INDEX idx_his_nonce_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS revoked_token (
  token_hash CHAR(64) PRIMARY KEY,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_revoked_token_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS auth_login_guard (
  guard_key VARCHAR(220) PRIMARY KEY,
  failure_count INT NOT NULL DEFAULT 0,
  first_failure_time DATETIME NULL,
  locked_until DATETIME NULL,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS drug_code_sequence (
  biz_date CHAR(8) PRIMARY KEY,
  current_value BIGINT NOT NULL DEFAULT 0,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

UPDATE drug_application
SET review_status = 'APPROVED', reviewed_by = '历史数据迁移', reviewed_at = COALESCE(update_time, NOW())
WHERE status IN ('PARTIALLY_DISPENSED', 'DISPENSED', 'RETURN_REQUIRED', 'RETURNED')
  AND review_status = 'PENDING';

UPDATE drug_application
SET status = 'REVIEW_PENDING'
WHERE status IN ('RECEIVED', 'READY') AND review_status = 'PENDING';

UPDATE dispense_record
SET operation_type = 'RETURN'
WHERE drug_name LIKE '%退药%' OR patient_name LIKE '%退药%';
