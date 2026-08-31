package com.hospital.pharmacy.dao;

import com.hospital.pharmacy.constant.PrescriptionStatus;
import com.hospital.pharmacy.constant.SplitCodeStatus;
import com.hospital.pharmacy.constant.StockStatus;
import com.hospital.pharmacy.constant.StockType;
import com.hospital.pharmacy.entity.DispenseRecord;
import com.hospital.pharmacy.entity.DrugSplitCode;
import com.hospital.pharmacy.entity.DrugStock;
import com.hospital.pharmacy.entity.Prescription;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import jakarta.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DrugDao {

    @Resource
    private JdbcTemplate jdbcTemplate;

    // 1. 保存/入库
    public void saveDrug(DrugStock drug) {
        String autoDrugCode = nextDrugCode();
        boolean splitAllowed = Boolean.TRUE.equals(drug.getIsSplitAllowed());
        int minUnitsPerPackage = safePositive(drug.getMinUnitsPerPackage(), 1);
        int remainingMinUnits = splitAllowed ? minUnitsPerPackage : safePositive(drug.getQuantity(), 1);
        String packageUnit = safeText(drug.getPackageUnit(), "盒");
        String minUnit = safeText(drug.getMinUnit(), splitAllowed ? "片" : packageUnit);
        String stockType = splitAllowed ? StockType.SPLIT_PARENT : StockType.WHOLE;
        String sql = "INSERT INTO drug_stock (catalog_id, drug_name, drug_code, trace_code, batch_number, quantity, expire_date, create_time, " +
                "is_split_allowed, package_unit, min_unit, min_units_per_package, remaining_min_units, stock_type, status, version) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), ?, ?, ?, ?, ?, ?, ?, 0)";
        String finalExpireDate = drug.getExpireDate();
        if (finalExpireDate != null && finalExpireDate.trim().isEmpty()) {
            finalExpireDate = null;
        }
        jdbcTemplate.update(sql,
                drug.getCatalogId(),
                drug.getDrugName(),
                autoDrugCode,
                drug.getTraceCode(),
                drug.getBatchNumber(),
                drug.getQuantity(),
                finalExpireDate,
                splitAllowed ? 1 : 0,
                packageUnit,
                minUnit,
                minUnitsPerPackage,
                remainingMinUnits,
                stockType,
                StockStatus.IN_STOCK);
    }

    // 2. 查询所有库存
    public List<DrugStock> findAll() {
        String sql = "SELECT * FROM drug_stock ORDER BY id DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(DrugStock.class));
    }

    // 3. 单品核销/减库存
    public int dispenseDrug(String traceCode) {
        String sql = "UPDATE drug_stock SET status = ?, quantity = 0, remaining_min_units = 0, version = version + 1, update_time = NOW() " +
                "WHERE trace_code = ? " +
                "AND status = ? " +
                "AND quantity = 1 " +
                "AND expire_date IS NOT NULL AND expire_date >= CURDATE() " +
                "AND (COALESCE(stock_type, ?) <> ? " +
                "OR COALESCE(remaining_min_units, 0) >= COALESCE(min_units_per_package, 1))";
        return jdbcTemplate.update(sql, StockStatus.DISPENSED, traceCode, StockStatus.IN_STOCK, StockType.WHOLE, StockType.SPLIT_PARENT);
    }

    // 4. 查单个药 (根据追溯码)
    public DrugStock getDrugByTraceCode(String traceCode) {
        try {
            String sql = "SELECT * FROM drug_stock WHERE trace_code = ? LIMIT 1";
            return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(DrugStock.class), traceCode);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    // 5. 插入流水记录
    public void saveRecord(String traceCode, String drugName, String patientName, String patientId) {
        String sql = "INSERT INTO dispense_record (trace_code, drug_name, patient_name, patient_id, dispense_time) VALUES (?, ?, ?, ?, NOW())";
        jdbcTemplate.update(sql, traceCode, drugName, patientName, patientId);
    }

    public void saveRecord(String traceCode,
                           String drugName,
                           String patientName,
                           String patientId,
                           String parentTraceCode,
                           String childTraceCode,
                           Integer dispenseUnits,
                           String dispenseUnit,
                           String dispenseType) {
        saveRecord(traceCode, drugName, patientName, patientId, parentTraceCode, childTraceCode,
                dispenseUnits, dispenseUnit, dispenseType, null, null);
    }

    public void saveRecord(String traceCode,
                           String drugName,
                           String patientName,
                           String patientId,
                           String parentTraceCode,
                           String childTraceCode,
                           Integer dispenseUnits,
                           String dispenseUnit,
                           String dispenseType,
                           Long applicationId,
                           Long applicationItemId) {
        String sql = "INSERT INTO dispense_record (trace_code, drug_name, patient_name, patient_id, dispense_time, " +
                "parent_trace_code, child_trace_code, dispense_units, dispense_unit, dispense_type, application_id, application_item_id) " +
                "VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, traceCode, drugName, patientName, patientId, parentTraceCode, childTraceCode,
                dispenseUnits, dispenseUnit, dispenseType, applicationId, applicationItemId);
    }

    public DispenseRecord findReturnableDispenseRecord(String traceCode, Long applicationId,
                                                        Long applicationItemId, String patientId) {
        String sql = "SELECT * FROM dispense_record WHERE trace_code = ? AND application_id = ? " +
                "AND application_item_id = ? AND patient_id = ? AND COALESCE(operation_type, 'DISPENSE') = 'DISPENSE' " +
                "AND COALESCE(dispense_units, 0) > COALESCE(returned_units, 0) ORDER BY id DESC LIMIT 1";
        List<DispenseRecord> rows = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(DispenseRecord.class),
                traceCode, applicationId, applicationItemId, patientId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int claimReturnedUnits(Long recordId, int units) {
        return jdbcTemplate.update("UPDATE dispense_record SET returned_units = returned_units + ? " +
                        "WHERE id = ? AND dispense_units - returned_units >= ?",
                units, recordId, units);
    }

    public void saveReturnRecord(String traceCode, String drugName, String patientName, String patientId,
                                 String parentTraceCode, String childTraceCode, Integer units, String unit,
                                 String dispenseType, Long applicationId, Long applicationItemId, Long relatedRecordId) {
        String sql = "INSERT INTO dispense_record (trace_code, drug_name, patient_name, patient_id, dispense_time, " +
                "parent_trace_code, child_trace_code, dispense_units, dispense_unit, dispense_type, application_id, " +
                "application_item_id, operation_type, related_record_id) VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, ?, ?, ?, ?, 'RETURN', ?)";
        jdbcTemplate.update(sql, traceCode, drugName, patientName, patientId, parentTraceCode, childTraceCode,
                units, unit, dispenseType, applicationId, applicationItemId, relatedRecordId);
    }

    // 6. 获取所有记录
    public List<DispenseRecord> getAllRecords() {
        String sql = "SELECT * FROM dispense_record ORDER BY dispense_time DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(DispenseRecord.class));
    }

    public List<DispenseRecord> getRecentRecords(int limit) {
        String sql = "SELECT * FROM dispense_record ORDER BY dispense_time DESC LIMIT ?";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(DispenseRecord.class), limit);
    }

    // 7. 搜索库存
    public List<DrugStock> findByTraceCode(String code) {
        String sql = "SELECT * FROM drug_stock WHERE trace_code = ?";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(DrugStock.class), code);
    }

    // 8. 查处方 (筛选状态：待发药/已发药)
    public List<Prescription> findPrescriptionsByPatient(String patientId, String status) {
        String sql = "SELECT * FROM prescription WHERE patient_id = ? AND status = ?";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(Prescription.class), patientId, status);
    }

    public Prescription findPrescriptionById(Long prescriptionId) {
        try {
            String sql = "SELECT * FROM prescription WHERE id = ? LIMIT 1";
            return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(Prescription.class), prescriptionId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    // 9. 核销处方 (发药时调用)
    public int completePrescription(Long prescriptionId, String traceCode) {
        String sql = "UPDATE prescription SET status = ?, trace_code_dispensed = ? WHERE id = ? AND status = ?";
        return jdbcTemplate.update(sql, PrescriptionStatus.DISPENSED, traceCode, prescriptionId, PrescriptionStatus.PENDING);
    }

    public int completePrescription(Long prescriptionId, String traceCode, Integer dispensedUnits, String dispenseUnit) {
        String sql = "UPDATE prescription SET status = ?, trace_code_dispensed = ?, dispensed_units = ?, dispense_unit = ? " +
                "WHERE id = ? AND status = ?";
        return jdbcTemplate.update(sql, PrescriptionStatus.DISPENSED, traceCode, dispensedUnits, dispenseUnit, prescriptionId, PrescriptionStatus.PENDING);
    }

    // 10. 处理单品退药
    public int restoreReturnedDrug(String traceCode) {
        String sql = "UPDATE drug_stock SET status = ?, quantity = 1, remaining_min_units = COALESCE(min_units_per_package, 1), " +
                "version = version + 1, update_time = NOW() " +
                "WHERE trace_code = ? AND status = ?";
        return jdbcTemplate.update(sql, StockStatus.LOCKED, traceCode, StockStatus.DISPENSED);
    }

    public boolean isWithinExpiry(String traceCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM drug_stock WHERE trace_code = ? " +
                        "AND expire_date IS NOT NULL AND expire_date >= CURDATE()",
                Integer.class, traceCode);
        return count != null && count > 0;
    }

    public int markPrescriptionReturned(Long prescriptionId) {
        String sql = "UPDATE prescription SET status = ? WHERE id = ? AND status = ?";
        return jdbcTemplate.update(sql, PrescriptionStatus.RETURNED, prescriptionId, PrescriptionStatus.DISPENSED);
    }

    public DrugSplitCode getSplitByChildTraceCode(String childTraceCode) {
        try {
            String sql = "SELECT * FROM drug_split_code WHERE child_trace_code = ? LIMIT 1";
            return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(DrugSplitCode.class), childTraceCode);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public int countSplitChildren(String parentTraceCode) {
        return queryInt("SELECT COUNT(*) FROM drug_split_code WHERE parent_trace_code = ?", parentTraceCode);
    }

    public int reserveParentMinUnits(String parentTraceCode, int splitUnits) {
        String sql = "UPDATE drug_stock SET remaining_min_units = remaining_min_units - ?, version = version + 1, update_time = NOW() " +
                "WHERE trace_code = ? AND status = ? AND is_split_allowed = 1 AND remaining_min_units >= ?";
        return jdbcTemplate.update(sql, splitUnits, parentTraceCode, StockStatus.IN_STOCK, splitUnits);
    }

    public int restoreParentMinUnits(String parentTraceCode, int splitUnits) {
        String sql = "UPDATE drug_stock SET status = ?, remaining_min_units = remaining_min_units + ?, quantity = 1, " +
                "version = version + 1, update_time = NOW() " +
                "WHERE trace_code = ? AND status IN (?, ?)";
        return jdbcTemplate.update(sql, StockStatus.IN_STOCK, splitUnits, parentTraceCode, StockStatus.IN_STOCK, StockStatus.DISPENSED);
    }

    public void saveSplitCode(DrugSplitCode splitCode) {
        String sql = "INSERT INTO drug_split_code (parent_trace_code, child_trace_code, drug_name, batch_number, min_unit, " +
                "split_units, remaining_units, status, version, created_by, create_time, update_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, NOW(), NOW())";
        jdbcTemplate.update(sql,
                splitCode.getParentTraceCode(),
                splitCode.getChildTraceCode(),
                splitCode.getDrugName(),
                splitCode.getBatchNumber(),
                splitCode.getMinUnit(),
                splitCode.getSplitUnits(),
                splitCode.getRemainingUnits(),
                SplitCodeStatus.AVAILABLE,
                splitCode.getCreatedBy());
    }

    public int markSplitDispensed(String childTraceCode, String patientId) {
        String sql = "UPDATE drug_split_code c JOIN drug_stock s ON s.trace_code = c.parent_trace_code " +
                "SET c.status = ?, c.remaining_units = 0, c.version = c.version + 1, " +
                "c.dispensed_to_patient_id = ?, c.dispensed_time = NOW(), c.update_time = NOW() " +
                "WHERE c.child_trace_code = ? AND c.status = ? AND s.status = ? " +
                "AND s.expire_date IS NOT NULL AND s.expire_date >= CURDATE()";
        return jdbcTemplate.update(sql, SplitCodeStatus.DISPENSED, patientId, childTraceCode,
                SplitCodeStatus.AVAILABLE, StockStatus.IN_STOCK);
    }

    public int markSplitReturned(String childTraceCode, String patientId) {
        String sql = "UPDATE drug_split_code SET status = ?, remaining_units = split_units, version = version + 1, update_time = NOW() " +
                "WHERE child_trace_code = ? AND status = ? AND dispensed_to_patient_id = ?";
        return jdbcTemplate.update(sql, SplitCodeStatus.RETURNED, childTraceCode, SplitCodeStatus.DISPENSED, patientId);
    }

    // ✅ 新增：查询近效期药品（默认90天内到期且库存>0，按效期升序）
    public List<DrugStock> findNearExpiry(int days) {
        String sql = "SELECT * FROM drug_stock " +
                "WHERE expire_date IS NOT NULL " +
                "AND expire_date <= DATE_ADD(NOW(), INTERVAL ? DAY) " +
                "AND expire_date >= NOW() " +
                "AND quantity > 0 " +
                "ORDER BY expire_date ASC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(DrugStock.class), days);
    }

    public List<DrugStock> findFifoCandidates(String drugName, int limit) {
        String sql = "SELECT * FROM drug_stock " +
                "WHERE drug_name = ? AND quantity > 0 AND status = ? " +
                "AND expire_date IS NOT NULL AND expire_date >= CURDATE() " +
                "ORDER BY expire_date IS NULL ASC, expire_date ASC, create_time ASC " +
                "LIMIT ?";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(DrugStock.class), drugName, StockStatus.IN_STOCK, limit);
    }

    public List<DrugSplitCode> findAvailableSplitCodes(int limit) {
        String sql = "SELECT * FROM drug_split_code WHERE status = ? ORDER BY create_time ASC LIMIT ?";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(DrugSplitCode.class), SplitCodeStatus.AVAILABLE, limit);
    }

    public Map<String, Object> getDashboardSummary(int lowThreshold, int expiryDays) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalStock", queryInt("SELECT COALESCE(SUM(quantity), 0) FROM drug_stock"));
        summary.put("skuCount", queryInt("SELECT COUNT(*) FROM drug_stock"));
        summary.put("inStockCount", queryInt("SELECT COUNT(*) FROM drug_stock WHERE quantity > 0"));
        summary.put("outStockCount", queryInt("SELECT COUNT(*) FROM drug_stock WHERE quantity <= 0"));
        summary.put("lowStockCount", queryInt(
                "SELECT COUNT(*) FROM drug_stock WHERE quantity > 0 AND quantity < ?",
                lowThreshold));
        summary.put("nearExpiryCount", queryInt(
                "SELECT COUNT(*) FROM drug_stock " +
                        "WHERE expire_date IS NOT NULL " +
                        "AND expire_date <= DATE_ADD(NOW(), INTERVAL ? DAY) " +
                        "AND expire_date >= NOW() " +
                        "AND quantity > 0",
                expiryDays));
        summary.put("recordCount", queryInt("SELECT COUNT(*) FROM dispense_record"));
        summary.put("lastUpdated", LocalDateTime.now().toString());
        return summary;
    }

    public Map<String, Object> getStockStatus(int lowThreshold, int expiryDays) {
        Map<String, Object> status = new HashMap<>();
        status.put("normal", queryInt(
                "SELECT COUNT(*) FROM drug_stock " +
                        "WHERE quantity > 0 " +
                        "AND quantity >= ? " +
                        "AND (expire_date IS NULL OR expire_date > DATE_ADD(NOW(), INTERVAL ? DAY))",
                lowThreshold, expiryDays));
        status.put("lowStock", queryInt(
                "SELECT COUNT(*) FROM drug_stock WHERE quantity > 0 AND quantity < ?",
                lowThreshold));
        status.put("nearExpiry", queryInt(
                "SELECT COUNT(*) FROM drug_stock " +
                        "WHERE expire_date IS NOT NULL " +
                        "AND expire_date <= DATE_ADD(NOW(), INTERVAL ? DAY) " +
                        "AND expire_date >= NOW() " +
                        "AND quantity > 0",
                expiryDays));
        status.put("outStock", queryInt("SELECT COUNT(*) FROM drug_stock WHERE quantity <= 0"));
        return status;
    }

    public int countStockRows() {
        return queryInt("SELECT COUNT(*) FROM drug_stock");
    }

    private int queryInt(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    public String nextDrugCode() {
        String bizDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String upsertSql = "INSERT INTO drug_code_sequence (biz_date, current_value) " +
                "VALUES (?, LAST_INSERT_ID(1)) " +
                "ON DUPLICATE KEY UPDATE current_value = LAST_INSERT_ID(current_value + 1), update_time = NOW()";

        Long sequence = jdbcTemplate.execute((ConnectionCallback<Long>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(upsertSql)) {
                statement.setString(1, bizDate);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT LAST_INSERT_ID()");
                 ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : null;
            }
        });
        if (sequence == null || sequence <= 0) {
            throw new IllegalStateException("药品流水号生成失败");
        }
        return "DRUG-" + bizDate + "-" + String.format("%05d", sequence);
    }

    private int safePositive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String safeText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

}
