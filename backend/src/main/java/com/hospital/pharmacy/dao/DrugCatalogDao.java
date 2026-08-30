package com.hospital.pharmacy.dao;

import com.hospital.pharmacy.entity.DrugCatalog;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.List;

@Repository
public class DrugCatalogDao {

    @Resource
    private JdbcTemplate jdbcTemplate;

    public List<DrugCatalog> findAll() {
        String sql = "SELECT * FROM drug_catalog ORDER BY status ASC, drug_name ASC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(DrugCatalog.class));
    }

    public DrugCatalog findByName(String drugName) {
        try {
            String sql = "SELECT * FROM drug_catalog WHERE drug_name = ? AND status = 'ENABLED' LIMIT 1";
            return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(DrugCatalog.class), drugName);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public DrugCatalog findById(Long id) {
        try {
            String sql = "SELECT * FROM drug_catalog WHERE id = ? AND status = 'ENABLED' LIMIT 1";
            return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(DrugCatalog.class), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void create(DrugCatalog catalog) {
        String sql = "INSERT INTO drug_catalog (drug_name, specification, dosage_form, manufacturer, is_split_allowed, " +
                "package_unit, min_unit, min_units_per_package, low_stock_threshold, status, create_time, update_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
        jdbcTemplate.update(sql,
                catalog.getDrugName(),
                catalog.getSpecification(),
                catalog.getDosageForm(),
                catalog.getManufacturer(),
                Boolean.TRUE.equals(catalog.getIsSplitAllowed()) ? 1 : 0,
                safe(catalog.getPackageUnit(), "盒"),
                safe(catalog.getMinUnit(), "盒"),
                positive(catalog.getMinUnitsPerPackage(), 1),
                positive(catalog.getLowStockThreshold(), 50),
                safe(catalog.getStatus(), "ENABLED"));
    }

    public int update(Long id, DrugCatalog catalog) {
        String sql = "UPDATE drug_catalog SET drug_name = ?, specification = ?, dosage_form = ?, manufacturer = ?, " +
                "is_split_allowed = ?, package_unit = ?, min_unit = ?, min_units_per_package = ?, " +
                "low_stock_threshold = ?, status = ?, update_time = NOW() WHERE id = ?";
        return jdbcTemplate.update(sql,
                catalog.getDrugName(),
                catalog.getSpecification(),
                catalog.getDosageForm(),
                catalog.getManufacturer(),
                Boolean.TRUE.equals(catalog.getIsSplitAllowed()) ? 1 : 0,
                safe(catalog.getPackageUnit(), "盒"),
                safe(catalog.getMinUnit(), "盒"),
                positive(catalog.getMinUnitsPerPackage(), 1),
                positive(catalog.getLowStockThreshold(), 50),
                safe(catalog.getStatus(), "ENABLED"),
                id);
    }

    public int disable(Long id) {
        return jdbcTemplate.update("UPDATE drug_catalog SET status = 'DISABLED', update_time = NOW() WHERE id = ?", id);
    }

    private int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
