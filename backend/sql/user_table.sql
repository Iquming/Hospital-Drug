CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  role ENUM('ADMIN', 'PHARMACIST', 'NURSE') NOT NULL DEFAULT 'NURSE',
  department VARCHAR(100) NULL,
  status ENUM('ENABLED', 'DISABLED') NOT NULL DEFAULT 'ENABLED',
  last_login_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_sys_user_role (role),
  INDEX idx_sys_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_user (
  username,
  password_hash,
  display_name,
  role,
  department,
  status
) VALUES (
  'admin',
  '$2a$10$7unXL8U9hf8dvB9nnYkbTeUW1KAtSybYXCyK1VwtM40qSDpZnIKmW',
  '系统管理员',
  'ADMIN',
  '信息科',
  'ENABLED'
) ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  display_name = VALUES(display_name),
  role = VALUES(role),
  department = VALUES(department),
  status = VALUES(status),
  update_time = NOW();
