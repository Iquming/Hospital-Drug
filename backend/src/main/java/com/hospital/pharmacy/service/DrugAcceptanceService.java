package com.hospital.pharmacy.service;

import com.hospital.pharmacy.dao.DrugDao;
import com.hospital.pharmacy.constant.DispenseOperation;
import com.hospital.pharmacy.entity.DrugStock;
import com.hospital.pharmacy.exception.BusinessException;
import com.hospital.pharmacy.exception.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 对应论文 5.2.1 节：基于追溯码的药品入库验收服务类
 */
@Service
public class DrugAcceptanceService {

    @Resource
    private DrugDao drugDao;

    @Resource
    private DrugCatalogService drugCatalogService;

    @Resource
    private AuditLogService auditLogService;

    @Transactional(rollbackFor = Exception.class)
    public void scanAndAccept(DrugStock drug, String operatorId) {
        if (drug == null) {
            throw new IllegalArgumentException("药品信息不能为空");
        }
        if (!StringUtils.hasText(drug.getTraceCode())) {
            throw new IllegalArgumentException("追溯码不能为空");
        }
        if (!StringUtils.hasText(drug.getDrugName())) {
            throw new IllegalArgumentException("药品名称不能为空");
        }
        requireValidExpiry(drug.getExpireDate());

        String traceCode = drug.getTraceCode().trim();
        String drugName = drug.getDrugName().trim();
        String operator = StringUtils.hasText(operatorId) ? operatorId.trim() : "未知操作员";
        drug.setTraceCode(traceCode);
        drug.setDrugName(drugName);
        drugCatalogService.applyCatalogDefaults(drug);

        DrugStock existDrug = drugDao.getDrugByTraceCode(traceCode);
        if (existDrug != null) {
            throw new BusinessException(ErrorCode.TRACE_CODE_DUPLICATED, "入库拦截：该追溯码 [" + traceCode + "] 已在库，严禁重复建档");
        }

        drug.setQuantity(1);

        if (drug.getBatchNumber() != null && !drug.getBatchNumber().contains("入:")) {
            String batchWithOperator = drug.getBatchNumber().trim() + " (入:" + operator + ")";
            drug.setBatchNumber(batchWithOperator);
        }

        try {
            drugDao.saveDrug(drug);
        } catch (DuplicateKeyException e) {
            auditLogService.record(DispenseOperation.DRUG_INBOUND_DUPLICATE, "drug_stock", traceCode, null, drugName, "FAILED", "追溯码唯一索引拦截重复入库");
            throw new BusinessException(ErrorCode.TRACE_CODE_DUPLICATED, "入库拦截：该追溯码 [" + traceCode + "] 已存在");
        }
        drugDao.saveRecord(
                traceCode,
                drugName,
                DispenseOperation.INBOUND_SCAN,
                "操作人:" + operator
        );
        auditLogService.record(DispenseOperation.DRUG_INBOUND, "drug_stock", traceCode, null, drugName, "SUCCESS", DispenseOperation.INBOUND_SCAN_AUDIT);
    }

    private void requireValidExpiry(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("药品有效期不能为空");
        }
        try {
            String normalized = value.trim();
            LocalDate expiry = normalized.length() >= 10
                    ? LocalDate.parse(normalized.substring(0, 10))
                    : LocalDateTime.parse(normalized).toLocalDate();
            if (expiry.isBefore(LocalDate.now())) {
                throw new BusinessException(ErrorCode.STOCK_CONFLICT, "入库拦截：药品已超过有效期");
            }
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("药品有效期格式无效，应为 yyyy-MM-dd");
        }
    }
}
