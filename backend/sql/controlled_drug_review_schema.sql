-- 特殊管制药品人工复核升级
-- 执行前请备份数据库。本脚本在 professional_pharmacy_hardening.sql 之后执行。

DELIMITER $$

CREATE PROCEDURE add_controlled_review_column_if_missing(
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

CALL add_controlled_review_column_if_missing(
  'drug_catalog',
  'control_category',
  'VARCHAR(30) NOT NULL DEFAULT ''GENERAL'''
)$$

DROP PROCEDURE add_controlled_review_column_if_missing$$

DELIMITER ;

UPDATE drug_catalog
SET control_category = 'GENERAL'
WHERE control_category IS NULL OR TRIM(control_category) = '';

-- 已完成映射且全部为普通药的历史待审申请，不再要求药房重复执行特殊药品人工复核。
UPDATE drug_application application
SET application.review_status = 'APPROVED',
    application.review_comment = '普通药品处方已完成通用审方，无需特殊药品人工复核',
    application.reviewed_by = '系统规则',
    application.reviewed_at = NOW(),
    application.status = 'READY',
    application.update_time = NOW()
WHERE application.status = 'REVIEW_PENDING'
  AND application.review_status = 'PENDING'
  AND NOT EXISTS (
    SELECT 1 FROM drug_application_item item
    WHERE item.application_id = application.id
      AND item.local_catalog_id IS NULL
  )
  AND NOT EXISTS (
    SELECT 1
    FROM drug_application_item item
    JOIN drug_catalog catalog ON catalog.id = item.local_catalog_id
    WHERE item.application_id = application.id
      AND catalog.control_category <> 'GENERAL'
  );
