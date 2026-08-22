package com.example.demodrug.dao;

import com.example.demodrug.entity.InventoryCheck;
import com.example.demodrug.entity.InventoryCheckItem;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.List;

@Repository
public class InventoryCheckDao {

    @Resource
    private JdbcTemplate jdbcTemplate;

    public void create(InventoryCheck check) {
        String sql = "INSERT INTO inventory_check (check_no, title, status, created_by, create_time, update_time) VALUES (?, ?, 'OPEN', ?, NOW(), NOW())";
        jdbcTemplate.update(sql, check.getCheckNo(), check.getTitle(), check.getCreatedBy());
    }

    public List<InventoryCheck> findRecent(int limit) {
        String sql = "SELECT * FROM inventory_check ORDER BY create_time DESC LIMIT ?";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(InventoryCheck.class), limit);
    }

    public InventoryCheck findById(Long id) {
        String sql = "SELECT * FROM inventory_check WHERE id = ? LIMIT 1";
        List<InventoryCheck> list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(InventoryCheck.class), id);
        return list.isEmpty() ? null : list.get(0);
    }

    public void upsertItem(InventoryCheckItem item) {
        String sql = "INSERT INTO inventory_check_item (check_id, trace_code, code_type, drug_name, expected_status, actual_status, difference_type, scanned_by, scan_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW()) " +
                "ON DUPLICATE KEY UPDATE code_type = VALUES(code_type), drug_name = VALUES(drug_name), expected_status = VALUES(expected_status), " +
                "actual_status = VALUES(actual_status), difference_type = VALUES(difference_type), scanned_by = VALUES(scanned_by), scan_time = NOW()";
        jdbcTemplate.update(sql,
                item.getCheckId(),
                item.getTraceCode(),
                item.getCodeType(),
                item.getDrugName(),
                item.getExpectedStatus(),
                item.getActualStatus(),
                item.getDifferenceType(),
                item.getScannedBy());
    }

    public List<InventoryCheckItem> findItems(Long checkId) {
        String sql = "SELECT * FROM inventory_check_item WHERE check_id = ? ORDER BY scan_time DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(InventoryCheckItem.class), checkId);
    }

    public int complete(Long id, String operator) {
        String sql = "UPDATE inventory_check SET status = 'COMPLETED', completed_by = ?, complete_time = NOW(), update_time = NOW() WHERE id = ? AND status = 'OPEN'";
        return jdbcTemplate.update(sql, operator, id);
    }
}
