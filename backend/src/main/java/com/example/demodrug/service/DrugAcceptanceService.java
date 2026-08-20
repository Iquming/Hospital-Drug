package com.example.demodrug.service;

import com.example.demodrug.dao.DrugDao;
import com.example.demodrug.entity.DrugStock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import jakarta.annotation.Resource;

/**
 * 对应论文 5.2.1 节：基于追溯码的药品入库验收服务类
 */
@Service
public class DrugAcceptanceService {

    @Resource
    private DrugDao drugDao;

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

        String traceCode = drug.getTraceCode().trim();
        String drugName = drug.getDrugName().trim();
        String operator = StringUtils.hasText(operatorId) ? operatorId.trim() : "未知操作员";
        drug.setTraceCode(traceCode);
        drug.setDrugName(drugName);

        DrugStock existDrug = drugDao.getDrugByTraceCode(traceCode);
        if (existDrug != null) {
            throw new IllegalArgumentException("入库拦截：该追溯码 [" + traceCode + "] 已在库，严禁重复建档");
        }

        drug.setQuantity(1);

        if (drug.getBatchNumber() != null && !drug.getBatchNumber().contains("入:")) {
            String batchWithOperator = drug.getBatchNumber().trim() + " (入:" + operator + ")";
            drug.setBatchNumber(batchWithOperator);
        }

        drugDao.saveDrug(drug);
        drugDao.saveRecord(
                traceCode,
                drugName,
                "【扫码建档入库】",
                "操作人:" + operator
        );
    }
}
