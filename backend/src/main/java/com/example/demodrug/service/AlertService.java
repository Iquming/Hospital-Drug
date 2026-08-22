package com.example.demodrug.service;

import com.example.demodrug.dao.DrugDao;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Service
public class AlertService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private DrugDao drugDao;

    public Map<String, Object> enhancedAlerts() {
        Map<String, Object> result = new HashMap<>();
        result.put("lowStock", jdbcTemplate.queryForList(
                "SELECT s.drug_name, s.trace_code, s.quantity, COALESCE(c.low_stock_threshold, 50) AS threshold " +
                        "FROM drug_stock s LEFT JOIN drug_catalog c ON CONVERT(s.drug_name USING utf8mb4) COLLATE utf8mb4_unicode_ci = c.drug_name AND c.status = 'ENABLED' " +
                        "WHERE s.quantity > 0 AND s.quantity < COALESCE(c.low_stock_threshold, 50) " +
                        "ORDER BY s.quantity ASC LIMIT 100"));
        result.put("expired", jdbcTemplate.queryForList(
                "SELECT drug_name, trace_code, batch_number, expire_date, quantity FROM drug_stock " +
                        "WHERE expire_date IS NOT NULL AND expire_date < NOW() AND quantity > 0 " +
                        "ORDER BY expire_date ASC LIMIT 100"));
        result.put("availableSplitCodes", drugDao.findAvailableSplitCodes(100));
        result.put("longIdleStock", jdbcTemplate.queryForList(
                "SELECT drug_name, trace_code, batch_number, create_time, quantity FROM drug_stock " +
                        "WHERE quantity > 0 AND create_time < DATE_SUB(NOW(), INTERVAL 180 DAY) " +
                        "ORDER BY create_time ASC LIMIT 100"));
        return result;
    }
}
