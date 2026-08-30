package com.example.demodrug.service;

import com.example.demodrug.dao.DrugCatalogDao;
import com.example.demodrug.entity.DrugCatalog;
import com.example.demodrug.entity.DrugStock;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.List;

@Service
public class DrugCatalogService {

    @Resource
    private DrugCatalogDao drugCatalogDao;

    @Resource
    private AuditLogService auditLogService;

    public List<DrugCatalog> list() {
        return drugCatalogDao.findAll();
    }

    public void create(DrugCatalog catalog) {
        validate(catalog);
        drugCatalogDao.create(catalog);
        auditLogService.record("DRUG_CATALOG_CREATE", "drug_catalog", catalog.getDrugName(), null, catalog.toString(), "SUCCESS", "新增药品档案");
    }

    public void update(Long id, DrugCatalog catalog) {
        validate(catalog);
        if (drugCatalogDao.update(id, catalog) <= 0) {
            throw new IllegalArgumentException("药品档案不存在");
        }
        auditLogService.record("DRUG_CATALOG_UPDATE", "drug_catalog", String.valueOf(id), null, catalog.toString(), "SUCCESS", "更新药品档案");
    }

    public void disable(Long id) {
        if (drugCatalogDao.disable(id) <= 0) {
            throw new IllegalArgumentException("药品档案不存在");
        }
        auditLogService.record("DRUG_CATALOG_DISABLE", "drug_catalog", String.valueOf(id), null, null, "SUCCESS", "停用药品档案");
    }

    public void applyCatalogDefaults(DrugStock drug) {
        if (drug == null || !StringUtils.hasText(drug.getDrugName())) {
            return;
        }
        DrugCatalog catalog = drugCatalogDao.findByName(drug.getDrugName().trim());
        if (catalog == null) {
            return;
        }
        drug.setCatalogId(catalog.getId());
        drug.setIsSplitAllowed(catalog.getIsSplitAllowed());
        drug.setPackageUnit(catalog.getPackageUnit());
        drug.setMinUnit(catalog.getMinUnit());
        drug.setMinUnitsPerPackage(catalog.getMinUnitsPerPackage());
    }

    private void validate(DrugCatalog catalog) {
        if (catalog == null || !StringUtils.hasText(catalog.getDrugName())) {
            throw new IllegalArgumentException("药品名称不能为空");
        }
        catalog.setDrugName(catalog.getDrugName().trim());
        if (!StringUtils.hasText(catalog.getStatus())) {
            catalog.setStatus("ENABLED");
        }
        if (catalog.getMinUnitsPerPackage() == null || catalog.getMinUnitsPerPackage() <= 0) {
            catalog.setMinUnitsPerPackage(1);
        }
        if (catalog.getLowStockThreshold() == null || catalog.getLowStockThreshold() <= 0) {
            catalog.setLowStockThreshold(50);
        }
    }
}
