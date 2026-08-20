package com.example.demodrug.dao;

import com.example.demodrug.entity.DispenseRecord;
import com.example.demodrug.entity.DrugSplitCode;
import com.example.demodrug.entity.DrugStock;
import com.example.demodrug.entity.Prescription;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class DrugDao {

    @Resource
    private JdbcTemplate jdbcTemplate;

    // 1. 保存/入库
    public void saveDrug(DrugStock drug) {
        String autoDrugCode = "DRUG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        boolean splitAllowed = Boolean.TRUE.equals(drug.getIsSplitAllowed());
        int minUnitsPerPackage = safePositive(drug.getMinUnitsPerPackage(), 1);
        int remainingMinUnits = splitAllowed ? minUnitsPerPackage : safePositive(drug.getQuantity(), 1);
        String packageUnit = safeText(drug.getPackageUnit(), "盒");
        String minUnit = safeText(drug.getMinUnit(), splitAllowed ? "片" : packageUnit);
        String stockType = splitAllowed ? "SPLIT_PARENT" : "WHOLE";
        String sql = "INSERT INTO drug_stock (drug_name, drug_code, trace_code, batch_number, quantity, expire_date, create_time, " +
                "is_split_allowed, package_unit, min_unit, min_units_per_package, remaining_min_units, stock_type) " +
                "VALUES (?, ?, ?, ?, ?, ?, NOW(), ?, ?, ?, ?, ?, ?)";
        String finalExpireDate = drug.getExpireDate();
        if (finalExpireDate != null && finalExpireDate.trim().isEmpty()) {
            finalExpireDate = null;
        }
        jdbcTemplate.update(sql,
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
                stockType);
    }

    // 2. 查询所有库存
    public List<DrugStock> findAll() {
        String sql = "SELECT * FROM drug_stock ORDER BY id DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(DrugStock.class));
    }

    // 3. 单品核销/减库存
    public int dispenseDrug(String traceCode) {
        String sql = "UPDATE drug_stock SET quantity = 0, remaining_min_units = 0, update_time = NOW() " +
                "WHERE trace_code = ? " +
                "AND quantity = 1 " +
                "AND (COALESCE(stock_type, 'WHOLE') <> 'SPLIT_PARENT' " +
                "OR COALESCE(remaining_min_units, 0) >= COALESCE(min_units_per_package, 1))";
        return jdbcTemplate.update(sql, traceCode);
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
        String sql = "INSERT INTO dispense_record (trace_code, drug_name, patient_name, patient_id, dispense_time, " +
                "parent_trace_code, child_trace_code, dispense_units, dispense_unit, dispense_type) " +
                "VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, traceCode, drugName, patientName, patientId, parentTraceCode, childTraceCode,
                dispenseUnits, dispenseUnit, dispenseType);
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

    // 9. 核销处方 (发药时调用)
    public int completePrescription(Long prescriptionId, String traceCode) {
        String sql = "UPDATE prescription SET status = '已发药', trace_code_dispensed = ? WHERE id = ? AND status = '待发药'";
        return jdbcTemplate.update(sql, traceCode, prescriptionId);
    }

    public int completePrescription(Long prescriptionId, String traceCode, Integer dispensedUnits, String dispenseUnit) {
        String sql = "UPDATE prescription SET status = '已发药', trace_code_dispensed = ?, dispensed_units = ?, dispense_unit = ? " +
                "WHERE id = ? AND status = '待发药'";
        return jdbcTemplate.update(sql, traceCode, dispensedUnits, dispenseUnit, prescriptionId);
    }

    // 10. 处理单品退药
    public int restoreReturnedDrug(String traceCode) {
        String sql = "UPDATE drug_stock SET quantity = 1, remaining_min_units = COALESCE(min_units_per_package, 1), update_time = NOW() " +
                "WHERE trace_code = ? AND quantity = 0";
        return jdbcTemplate.update(sql, traceCode);
    }

    public int markPrescriptionReturned(Long prescriptionId) {
        String sql = "UPDATE prescription SET status = '已退药' WHERE id = ? AND status = '已发药'";
        return jdbcTemplate.update(sql, prescriptionId);
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
        String sql = "UPDATE drug_stock SET remaining_min_units = remaining_min_units - ?, update_time = NOW() " +
                "WHERE trace_code = ? AND is_split_allowed = 1 AND remaining_min_units >= ?";
        return jdbcTemplate.update(sql, splitUnits, parentTraceCode, splitUnits);
    }

    public int restoreParentMinUnits(String parentTraceCode, int splitUnits) {
        String sql = "UPDATE drug_stock SET remaining_min_units = remaining_min_units + ?, quantity = 1, update_time = NOW() " +
                "WHERE trace_code = ?";
        return jdbcTemplate.update(sql, splitUnits, parentTraceCode);
    }

    public void saveSplitCode(DrugSplitCode splitCode) {
        String sql = "INSERT INTO drug_split_code (parent_trace_code, child_trace_code, drug_name, batch_number, min_unit, " +
                "split_units, remaining_units, status, created_by, create_time, update_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'AVAILABLE', ?, NOW(), NOW())";
        jdbcTemplate.update(sql,
                splitCode.getParentTraceCode(),
                splitCode.getChildTraceCode(),
                splitCode.getDrugName(),
                splitCode.getBatchNumber(),
                splitCode.getMinUnit(),
                splitCode.getSplitUnits(),
                splitCode.getRemainingUnits(),
                splitCode.getCreatedBy());
    }

    public int markSplitDispensed(String childTraceCode, String patientId) {
        String sql = "UPDATE drug_split_code SET status = 'DISPENSED', remaining_units = 0, " +
                "dispensed_to_patient_id = ?, dispensed_time = NOW(), update_time = NOW() " +
                "WHERE child_trace_code = ? AND status = 'AVAILABLE'";
        return jdbcTemplate.update(sql, patientId, childTraceCode);
    }

    public int markSplitReturned(String childTraceCode) {
        String sql = "UPDATE drug_split_code SET status = 'RETURNED', remaining_units = split_units, update_time = NOW() " +
                "WHERE child_trace_code = ? AND status = 'DISPENSED'";
        return jdbcTemplate.update(sql, childTraceCode);
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

    private int safePositive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String safeText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

}
