package com.hospital.pharmacy.service;

import com.hospital.pharmacy.dao.DrugDao;
import com.hospital.pharmacy.constant.DispenseOperation;
import com.hospital.pharmacy.constant.DispenseType;
import com.hospital.pharmacy.constant.PrescriptionStatus;
import com.hospital.pharmacy.constant.SplitCodeStatus;
import com.hospital.pharmacy.constant.StockStatus;
import com.hospital.pharmacy.entity.DrugSplitCode;
import com.hospital.pharmacy.entity.DrugStock;
import com.hospital.pharmacy.entity.Prescription;
import com.hospital.pharmacy.exception.BusinessException;
import com.hospital.pharmacy.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import jakarta.annotation.Resource;

/**
 * 对应论文 5.2.2 节：处方闭环调剂与发药核心服务类
 */
@Service
public class DrugDispenseService {

    @Resource
    private DrugDao drugDao;

    @Resource
    private AuditLogService auditLogService;

    @Transactional(rollbackFor = Exception.class)
    public void executeDispense(String traceCode, String patientId, String prescriptionIdStr, String operatorLabel) {
        String normalizedTraceCode = requireText(traceCode, "追溯码不能为空");
        String normalizedPatientId = StringUtils.hasText(patientId) ? patientId.trim() : "";
        String operator = StringUtils.hasText(operatorLabel) ? operatorLabel.trim() : "未知操作员";
        Long prescriptionId = parsePrescriptionId(prescriptionIdStr);

        DrugSplitCode splitCode = drugDao.getSplitByChildTraceCode(normalizedTraceCode);
        if (splitCode != null) {
            executeSplitDispense(splitCode, normalizedPatientId, prescriptionId, operator);
            return;
        }

        DrugStock drug = drugDao.getDrugByTraceCode(normalizedTraceCode);
        if (drug == null) {
            throw new IllegalArgumentException("无效追溯码，系统中无此实物档案");
        }

        String dispenseUnit = safeText(drug.getPackageUnit(), "盒");
        if (prescriptionId != null) {
            validatePrescription(prescriptionId, drug.getDrugName(), 1, dispenseUnit, normalizedPatientId);
        }

        int rows = drugDao.dispenseDrug(normalizedTraceCode);
        if (rows <= 0) {
            auditLogService.record("DRUG_DISPENSE_CONFLICT", "drug_stock", normalizedTraceCode, null, StockStatus.DISPENSED, "FAILED", "整包装发药并发冲突或状态不允许");
            throw new BusinessException(ErrorCode.STOCK_CONFLICT, "该单品已被核销、锁定或库存状态异常");
        }

        String operationType = prescriptionId != null ? DispenseOperation.PRESCRIPTION_DISPENSE : DispenseOperation.PHARMACY_OUTBOUND;

        drugDao.saveRecord(normalizedTraceCode, drug.getDrugName(), operationType, normalizedPatientId + " 操作人:" + operator,
                normalizedTraceCode, null, 1, dispenseUnit, DispenseType.WHOLE_PACKAGE);

        if (prescriptionId != null) {
            int updated = drugDao.completePrescription(prescriptionId, normalizedTraceCode, 1, dispenseUnit);
            if (updated <= 0) {
                throw new IllegalStateException("处方不存在或状态不是" + PrescriptionStatus.PENDING);
            }
        }
        auditLogService.record("DRUG_DISPENSE", "drug_stock", normalizedTraceCode, StockStatus.IN_STOCK, "OUT_STOCK", "SUCCESS", operationType);
    }

    @Transactional(rollbackFor = Exception.class)
    public void executeReturn(String prescriptionIdStr, String traceCode, String patientId, String drugName, String operatorLabel) {
        Long prescriptionId = parseRequiredPrescriptionId(prescriptionIdStr);
        String normalizedTraceCode = requireText(traceCode, "追溯码不能为空");
        String normalizedDrugName = requireText(drugName, "药品名称不能为空");
        String normalizedPatientId = StringUtils.hasText(patientId) ? patientId.trim() : "";
        String operator = StringUtils.hasText(operatorLabel) ? operatorLabel.trim() : "未知操作员";

        DrugSplitCode splitCode = drugDao.getSplitByChildTraceCode(normalizedTraceCode);
        if (splitCode != null) {
            executeSplitReturn(splitCode, prescriptionId, normalizedPatientId, operator);
            return;
        }

        int restored = drugDao.restoreReturnedDrug(normalizedTraceCode);
        if (restored <= 0) {
            auditLogService.record("DRUG_RETURN_CONFLICT", "drug_stock", normalizedTraceCode, null, StockStatus.IN_STOCK, "FAILED", "整包装退药状态不允许");
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "退药失败：该单品未出库、被锁定或库存状态异常");
        }

        int prescriptionUpdated = drugDao.markPrescriptionReturned(prescriptionId);
        if (prescriptionUpdated <= 0) {
            throw new IllegalStateException("退药失败：处方不存在或状态不是" + PrescriptionStatus.DISPENSED);
        }

        drugDao.saveRecord(normalizedTraceCode, normalizedDrugName, DispenseOperation.RETURNED_BY_PATIENT, normalizedPatientId + " 操作人:" + operator,
                normalizedTraceCode, null, 1, "盒", DispenseType.WHOLE_PACKAGE);
        auditLogService.record("DRUG_RETURN", "drug_stock", normalizedTraceCode, "OUT_STOCK", StockStatus.IN_STOCK, "SUCCESS", "整包装退药");
    }

    private void executeSplitDispense(DrugSplitCode splitCode, String patientId, Long prescriptionId, String operator) {
        if (!SplitCodeStatus.AVAILABLE.equals(splitCode.getStatus())) {
            throw new IllegalStateException("拆零子码状态不是可发药");
        }
        if (prescriptionId != null) {
            validatePrescription(prescriptionId, splitCode.getDrugName(), splitCode.getSplitUnits(), splitCode.getMinUnit(), patientId);
        }
        int updated = drugDao.markSplitDispensed(splitCode.getChildTraceCode(), patientId);
        if (updated <= 0) {
            auditLogService.record("SPLIT_DISPENSE_CONFLICT", "drug_split_code", splitCode.getChildTraceCode(), splitCode.getStatus(), SplitCodeStatus.DISPENSED, "FAILED", "拆零子码重复发药或状态变化");
            throw new BusinessException(ErrorCode.STOCK_CONFLICT, "拆零子码已被处理，请刷新后重试");
        }

        drugDao.saveRecord(splitCode.getChildTraceCode(), splitCode.getDrugName(), DispenseOperation.SPLIT_DISPENSE,
                patientId + " 操作人:" + operator,
                splitCode.getParentTraceCode(), splitCode.getChildTraceCode(),
                splitCode.getSplitUnits(), splitCode.getMinUnit(), DispenseType.SPLIT_PACKAGE);

        if (prescriptionId != null) {
            int prescriptionUpdated = drugDao.completePrescription(
                    prescriptionId,
                    splitCode.getChildTraceCode(),
                    splitCode.getSplitUnits(),
                    splitCode.getMinUnit()
            );
            if (prescriptionUpdated <= 0) {
                throw new IllegalStateException("处方不存在或状态不是" + PrescriptionStatus.PENDING);
            }
        }
        auditLogService.record("SPLIT_DISPENSE", "drug_split_code", splitCode.getChildTraceCode(),
                SplitCodeStatus.AVAILABLE, SplitCodeStatus.DISPENSED, "SUCCESS", DispenseOperation.SPLIT_DISPENSE);
    }

    private void executeSplitReturn(DrugSplitCode splitCode, Long prescriptionId, String patientId, String operator) {
        if (!SplitCodeStatus.DISPENSED.equals(splitCode.getStatus())) {
            throw new IllegalStateException("拆零子码未发药或已处理，不能退药");
        }
        int returned = drugDao.markSplitReturned(splitCode.getChildTraceCode());
        if (returned <= 0) {
            auditLogService.record("SPLIT_RETURN_CONFLICT", "drug_split_code", splitCode.getChildTraceCode(), splitCode.getStatus(), SplitCodeStatus.RETURNED, "FAILED", "拆零子码重复退药或状态变化");
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "拆零退药失败：子码状态已变化");
        }
        drugDao.restoreParentMinUnits(splitCode.getParentTraceCode(), splitCode.getSplitUnits());

        int prescriptionUpdated = drugDao.markPrescriptionReturned(prescriptionId);
        if (prescriptionUpdated <= 0) {
            throw new IllegalStateException("退药失败：处方不存在或状态不是" + PrescriptionStatus.DISPENSED);
        }

        drugDao.saveRecord(splitCode.getChildTraceCode(), splitCode.getDrugName(), DispenseOperation.SPLIT_RETURNED_BY_PATIENT,
                patientId + " 操作人:" + operator,
                splitCode.getParentTraceCode(), splitCode.getChildTraceCode(),
                splitCode.getSplitUnits(), splitCode.getMinUnit(), DispenseType.SPLIT_PACKAGE);
        auditLogService.record("SPLIT_RETURN", "drug_split_code", splitCode.getChildTraceCode(),
                SplitCodeStatus.DISPENSED, SplitCodeStatus.RETURNED, "SUCCESS", "拆零退药");
    }

    private void validatePrescription(Long prescriptionId, String drugName, Integer dispenseUnits, String dispenseUnit, String patientId) {
        Prescription prescription = drugDao.findPrescriptionById(prescriptionId);
        if (prescription == null) {
            throw new IllegalArgumentException("处方不存在");
        }
        if (!PrescriptionStatus.PENDING.equals(prescription.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "处方状态不是" + PrescriptionStatus.PENDING);
        }
        if (StringUtils.hasText(patientId) && StringUtils.hasText(prescription.getPatientId())
                && !patientId.trim().equals(prescription.getPatientId().trim())
                && !patientId.contains(prescription.getPatientId().trim())) {
            throw new BusinessException(ErrorCode.PRESCRIPTION_MISMATCH, "患者编号与处方不一致");
        }
        if (StringUtils.hasText(prescription.getDrugName()) && !prescription.getDrugName().trim().equals(drugName)) {
            throw new BusinessException(ErrorCode.PRESCRIPTION_MISMATCH, "药品名称与处方不一致");
        }
        if (prescription.getPrescribedUnits() != null && prescription.getPrescribedUnits() > 0
                && !prescription.getPrescribedUnits().equals(dispenseUnits)) {
            throw new BusinessException(ErrorCode.PRESCRIPTION_MISMATCH, "发放数量与处方数量不一致，应发 "
                    + prescription.getPrescribedUnits() + safeText(prescription.getDispenseUnit(), dispenseUnit));
        }
        if (StringUtils.hasText(prescription.getDispenseUnit())
                && StringUtils.hasText(dispenseUnit)
                && !prescription.getDispenseUnit().trim().equals(dispenseUnit.trim())) {
            throw new BusinessException(ErrorCode.PRESCRIPTION_MISMATCH, "发放单位与处方单位不一致");
        }
    }

    private Long parsePrescriptionId(String prescriptionIdStr) {
        if (!StringUtils.hasText(prescriptionIdStr)) {
            return null;
        }
        return parseRequiredPrescriptionId(prescriptionIdStr);
    }

    private Long parseRequiredPrescriptionId(String prescriptionIdStr) {
        String value = requireText(prescriptionIdStr, "处方编号不能为空");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("处方编号格式不正确");
        }
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
