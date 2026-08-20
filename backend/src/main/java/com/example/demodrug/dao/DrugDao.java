package com.example.demodrug.dao;

import com.example.demodrug.entity.DispenseRecord;
import com.example.demodrug.entity.DrugStock;
import com.example.demodrug.entity.Prescription;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.UUID;

@Repository
public class DrugDao {

    @Resource
    private JdbcTemplate jdbcTemplate;

    // 1. 保存/入库
    public void saveDrug(DrugStock drug) {
        String autoDrugCode = "DRUG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String sql = "INSERT INTO drug_stock (drug_name, drug_code, trace_code, batch_number, quantity, expire_date, create_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, NOW())";
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
                finalExpireDate);
    }

    // 2. 查询所有库存
    public List<DrugStock> findAll() {
        String sql = "SELECT * FROM drug_stock ORDER BY id DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(DrugStock.class));
    }

    // 3. 单品核销/减库存
    public int dispenseDrug(String traceCode) {
        String sql = "UPDATE drug_stock SET quantity = 0, update_time = NOW() WHERE trace_code = ? AND quantity = 1";
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

    // 6. 获取所有记录
    public List<DispenseRecord> getAllRecords() {
        String sql = "SELECT * FROM dispense_record ORDER BY dispense_time DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(DispenseRecord.class));
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

    // 10. 处理单品退药
    public int restoreReturnedDrug(String traceCode) {
        String sql = "UPDATE drug_stock SET quantity = 1, update_time = NOW() WHERE trace_code = ? AND quantity = 0";
        return jdbcTemplate.update(sql, traceCode);
    }

    public int markPrescriptionReturned(Long prescriptionId) {
        String sql = "UPDATE prescription SET status = '已退药' WHERE id = ? AND status = '已发药'";
        return jdbcTemplate.update(sql, prescriptionId);
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

}
