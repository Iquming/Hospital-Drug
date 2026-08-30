CREATE TABLE IF NOT EXISTS drug_application (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_system VARCHAR(60) NOT NULL,
  his_application_no VARCHAR(100) NOT NULL,
  revision_no INT NOT NULL DEFAULT 1,
  patient_id VARCHAR(100) NOT NULL,
  patient_name VARCHAR(100) NOT NULL,
  encounter_no VARCHAR(100) NULL,
  department_code VARCHAR(60) NULL,
  department_name VARCHAR(100) NULL,
  priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  status VARCHAR(40) NOT NULL DEFAULT 'RECEIVED',
  prescribed_at DATETIME NULL,
  received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  cancel_reason VARCHAR(300) NULL,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_drug_application_source_no (source_system, his_application_no),
  INDEX idx_drug_application_patient (patient_id),
  INDEX idx_drug_application_status (status),
  INDEX idx_drug_application_priority (priority),
  INDEX idx_drug_application_received (received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS his_drug_mapping (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_system VARCHAR(60) NOT NULL,
  his_drug_code VARCHAR(100) NOT NULL,
  local_catalog_id BIGINT NOT NULL,
  created_by VARCHAR(100) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_his_drug_mapping (source_system, his_drug_code),
  INDEX idx_his_mapping_catalog (local_catalog_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS drug_application_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  application_id BIGINT NOT NULL,
  his_item_no VARCHAR(100) NOT NULL,
  his_drug_code VARCHAR(100) NOT NULL,
  local_catalog_id BIGINT NULL,
  drug_name VARCHAR(120) NOT NULL,
  specification VARCHAR(120) NULL,
  requested_quantity INT NOT NULL,
  dispensed_quantity INT NOT NULL DEFAULT 0,
  returned_quantity INT NOT NULL DEFAULT 0,
  unit VARCHAR(20) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_application_item_no (application_id, his_item_no),
  INDEX idx_application_item_application (application_id),
  INDEX idx_application_item_catalog (local_catalog_id),
  INDEX idx_application_item_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS his_inbound_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(120) NOT NULL,
  application_id BIGINT NULL,
  event_type VARCHAR(40) NOT NULL,
  result_status VARCHAR(30) NOT NULL,
  response_json TEXT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_his_inbound_event (event_id),
  INDEX idx_his_inbound_application (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS his_callback_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(120) NOT NULL,
  application_id BIGINT NOT NULL,
  event_type VARCHAR(50) NOT NULL,
  application_status VARCHAR(40) NOT NULL,
  payload_json LONGTEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_retry_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_error VARCHAR(500) NULL,
  response_body TEXT NULL,
  operator_name VARCHAR(100) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  sent_time DATETIME NULL,
  UNIQUE KEY uk_his_callback_event (event_id),
  INDEX idx_his_callback_due (status, next_retry_time),
  INDEX idx_his_callback_application (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER $$

CREATE PROCEDURE add_his_column_if_missing(
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

CALL add_his_column_if_missing('drug_stock', 'catalog_id', 'BIGINT NULL')$$
CALL add_his_column_if_missing('dispense_record', 'application_id', 'BIGINT NULL')$$
CALL add_his_column_if_missing('dispense_record', 'application_item_id', 'BIGINT NULL')$$

DROP PROCEDURE add_his_column_if_missing$$

DELIMITER ;

UPDATE drug_stock stock
JOIN drug_catalog catalog
  ON catalog.drug_name COLLATE utf8mb4_unicode_ci = stock.drug_name COLLATE utf8mb4_unicode_ci
SET stock.catalog_id = catalog.id
WHERE stock.catalog_id IS NULL;
