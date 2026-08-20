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

CALL add_column_if_missing('drug_stock', 'is_split_allowed', 'TINYINT(1) NOT NULL DEFAULT 0')$$
CALL add_column_if_missing('drug_stock', 'package_unit', 'VARCHAR(20) NOT NULL DEFAULT ''盒''')$$
CALL add_column_if_missing('drug_stock', 'min_unit', 'VARCHAR(20) NOT NULL DEFAULT ''盒''')$$
CALL add_column_if_missing('drug_stock', 'min_units_per_package', 'INT NOT NULL DEFAULT 1')$$
CALL add_column_if_missing('drug_stock', 'remaining_min_units', 'INT NOT NULL DEFAULT 1')$$
CALL add_column_if_missing('drug_stock', 'parent_trace_code', 'VARCHAR(100) NULL')$$
CALL add_column_if_missing('drug_stock', 'stock_type', 'ENUM(''WHOLE'', ''SPLIT_PARENT'', ''SPLIT_CHILD'') NOT NULL DEFAULT ''WHOLE''')$$

CALL add_column_if_missing('prescription', 'prescribed_units', 'INT NULL')$$
CALL add_column_if_missing('prescription', 'dispensed_units', 'INT NULL')$$
CALL add_column_if_missing('prescription', 'dispense_unit', 'VARCHAR(20) NULL')$$

CALL add_column_if_missing('dispense_record', 'parent_trace_code', 'VARCHAR(100) NULL')$$
CALL add_column_if_missing('dispense_record', 'child_trace_code', 'VARCHAR(120) NULL')$$
CALL add_column_if_missing('dispense_record', 'dispense_units', 'INT NULL')$$
CALL add_column_if_missing('dispense_record', 'dispense_unit', 'VARCHAR(20) NULL')$$
CALL add_column_if_missing('dispense_record', 'dispense_type', 'ENUM(''WHOLE_PACKAGE'', ''SPLIT_PACKAGE'') NOT NULL DEFAULT ''WHOLE_PACKAGE''')$$

DROP PROCEDURE add_column_if_missing$$

DELIMITER ;

UPDATE drug_stock
SET
  remaining_min_units = CASE
    WHEN quantity > 0 THEN GREATEST(quantity, 1) * GREATEST(min_units_per_package, 1)
    ELSE 0
  END,
  stock_type = CASE WHEN is_split_allowed = 1 THEN 'SPLIT_PARENT' ELSE 'WHOLE' END
WHERE remaining_min_units IS NULL OR remaining_min_units = 1;

CREATE TABLE IF NOT EXISTS drug_split_code (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_trace_code VARCHAR(100) NOT NULL,
  child_trace_code VARCHAR(120) NOT NULL UNIQUE,
  drug_name VARCHAR(100) NOT NULL,
  batch_number VARCHAR(100) NULL,
  min_unit VARCHAR(20) NOT NULL,
  split_units INT NOT NULL,
  remaining_units INT NOT NULL,
  status ENUM('AVAILABLE', 'DISPENSED', 'RETURNED', 'VOIDED') NOT NULL DEFAULT 'AVAILABLE',
  created_by VARCHAR(100) NULL,
  dispensed_to_patient_id VARCHAR(100) NULL,
  dispensed_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_split_parent_trace_code (parent_trace_code),
  INDEX idx_split_child_trace_code (child_trace_code),
  INDEX idx_split_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
