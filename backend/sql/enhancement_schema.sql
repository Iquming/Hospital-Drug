CREATE TABLE IF NOT EXISTS audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator_id BIGINT NULL,
  operator_name VARCHAR(100) NULL,
  operator_role VARCHAR(30) NULL,
  action VARCHAR(80) NOT NULL,
  target_type VARCHAR(80) NULL,
  target_id VARCHAR(120) NULL,
  request_path VARCHAR(200) NULL,
  client_ip VARCHAR(80) NULL,
  before_state TEXT NULL,
  after_state TEXT NULL,
  result VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
  message VARCHAR(500) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_audit_action (action),
  INDEX idx_audit_operator (operator_id),
  INDEX idx_audit_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inventory_check (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  check_no VARCHAR(60) NOT NULL UNIQUE,
  title VARCHAR(120) NOT NULL,
  status ENUM('OPEN', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'OPEN',
  created_by VARCHAR(100) NULL,
  completed_by VARCHAR(100) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  complete_time DATETIME NULL,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_inventory_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inventory_check_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  check_id BIGINT NOT NULL,
  trace_code VARCHAR(120) NOT NULL,
  code_type ENUM('PARENT', 'CHILD', 'UNKNOWN') NOT NULL DEFAULT 'UNKNOWN',
  drug_name VARCHAR(100) NULL,
  expected_status VARCHAR(80) NULL,
  actual_status VARCHAR(80) NOT NULL,
  difference_type ENUM('MATCH', 'SURPLUS', 'LOSS', 'ABNORMAL') NOT NULL DEFAULT 'MATCH',
  scanned_by VARCHAR(100) NULL,
  scan_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_inventory_trace (check_id, trace_code),
  INDEX idx_inventory_item_check (check_id),
  INDEX idx_inventory_item_diff (difference_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS drug_catalog (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  drug_name VARCHAR(100) NOT NULL UNIQUE,
  specification VARCHAR(100) NULL,
  dosage_form VARCHAR(60) NULL,
  manufacturer VARCHAR(120) NULL,
  is_split_allowed TINYINT(1) NOT NULL DEFAULT 0,
  package_unit VARCHAR(20) NOT NULL DEFAULT '盒',
  min_unit VARCHAR(20) NOT NULL DEFAULT '盒',
  min_units_per_package INT NOT NULL DEFAULT 1,
  low_stock_threshold INT NOT NULL DEFAULT 50,
  status ENUM('ENABLED', 'DISABLED') NOT NULL DEFAULT 'ENABLED',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_catalog_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
