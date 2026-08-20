package com.example.demodrug.service;

import com.example.demodrug.dao.DrugDao;
import com.example.demodrug.entity.DrugStock;
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

    @Transactional(rollbackFor = Exception.class)
    public void executeDispense(String traceCode, String patientId, String prescriptionIdStr) {
        String normalizedTraceCode = requireText(traceCode, "追溯码不能为空");
        String normalizedPatientId = StringUtils.hasText(patientId) ? patientId.trim() : "";
        Long prescriptionId = parsePrescriptionId(prescriptionIdStr);

        DrugStock drug = drugDao.getDrugByTraceCode(normalizedTraceCode);
        if (drug == null) {
            throw new IllegalArgumentException("无效追溯码，系统中无此实物档案");
        }

        int rows = drugDao.dispenseDrug(normalizedTraceCode);
        if (rows <= 0) {
            throw new IllegalStateException("该单品已被核销或库存状态异常");
        }

        String operationType = prescriptionId != null ? "处方扫码发药" : "药房扫码出库";

        drugDao.saveRecord(normalizedTraceCode, drug.getDrugName(), operationType, normalizedPatientId);

        if (prescriptionId != null) {
            int updated = drugDao.completePrescription(prescriptionId, normalizedTraceCode);
            if (updated <= 0) {
                throw new IllegalStateException("处方不存在或状态不是待发药");
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void executeReturn(String prescriptionIdStr, String traceCode, String patientId, String drugName) {
        Long prescriptionId = parseRequiredPrescriptionId(prescriptionIdStr);
        String normalizedTraceCode = requireText(traceCode, "追溯码不能为空");
        String normalizedDrugName = requireText(drugName, "药品名称不能为空");
        String normalizedPatientId = StringUtils.hasText(patientId) ? patientId.trim() : "";

        int restored = drugDao.restoreReturnedDrug(normalizedTraceCode);
        if (restored <= 0) {
            throw new IllegalStateException("退药失败：该单品未出库或库存状态异常");
        }

        int prescriptionUpdated = drugDao.markPrescriptionReturned(prescriptionId);
        if (prescriptionUpdated <= 0) {
            throw new IllegalStateException("退药失败：处方不存在或状态不是已发药");
        }

        drugDao.saveRecord(normalizedTraceCode, normalizedDrugName, "【退药】患者退回", normalizedPatientId);
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
}
