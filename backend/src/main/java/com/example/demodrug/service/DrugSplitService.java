package com.example.demodrug.service;

import com.example.demodrug.dao.DrugDao;
import com.example.demodrug.constant.SplitCodeStatus;
import com.example.demodrug.entity.DrugSplitCode;
import com.example.demodrug.entity.DrugStock;
import com.example.demodrug.exception.BusinessException;
import com.example.demodrug.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class DrugSplitService {

    @Resource
    private DrugDao drugDao;

    @Resource
    private AuditLogService auditLogService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createSplitCode(String parentTraceCode, Integer splitUnits, String operatorLabel) {
        String parentCode = requireText(parentTraceCode, "母包装追溯码不能为空");
        int units = requirePositive(splitUnits, "拆零数量必须大于 0");
        String operator = StringUtils.hasText(operatorLabel) ? operatorLabel.trim() : "未知操作员";

        DrugStock parent = drugDao.getDrugByTraceCode(parentCode);
        if (parent == null) {
            throw new IllegalArgumentException("母包装追溯码不存在");
        }
        if (!Boolean.TRUE.equals(parent.getIsSplitAllowed())) {
            throw new IllegalArgumentException("该药品未启用拆零管理");
        }
        int remaining = safeUnits(parent.getRemainingMinUnits());
        if (remaining < units) {
            throw new IllegalStateException("母包装剩余最小单位不足");
        }

        String childCode = generateChildCode(parentCode);
        int reserved = drugDao.reserveParentMinUnits(parentCode, units);
        if (reserved <= 0) {
            auditLogService.record("SPLIT_PARENT_CONFLICT", "drug_stock", parentCode, null, String.valueOf(units), "FAILED", "拆零母包装并发冲突或状态不允许");
            throw new BusinessException(ErrorCode.STOCK_CONFLICT, "母包装库存已变化、被锁定或剩余最小单位不足，请刷新后重试");
        }

        DrugSplitCode splitCode = new DrugSplitCode();
        splitCode.setParentTraceCode(parentCode);
        splitCode.setChildTraceCode(childCode);
        splitCode.setDrugName(parent.getDrugName());
        splitCode.setBatchNumber(parent.getBatchNumber());
        splitCode.setMinUnit(safeText(parent.getMinUnit(), "片"));
        splitCode.setSplitUnits(units);
        splitCode.setRemainingUnits(units);
        splitCode.setCreatedBy(operator);
        drugDao.saveSplitCode(splitCode);
        auditLogService.record("SPLIT_CREATE", "drug_split_code", childCode, parentCode,
                units + splitCode.getMinUnit(), "SUCCESS", "拆零建码");

        Map<String, Object> result = new HashMap<>();
        result.put("childTraceCode", childCode);
        result.put("parentTraceCode", parentCode);
        result.put("splitUnits", units);
        result.put("remainingParentUnits", remaining - units);
        result.put("minUnit", splitCode.getMinUnit());
        result.put("drugName", parent.getDrugName());
        result.put("status", SplitCodeStatus.AVAILABLE);
        return result;
    }

    public Map<String, Object> label(String childTraceCode) {
        String code = requireText(childTraceCode, "子码不能为空");
        DrugSplitCode splitCode = drugDao.getSplitByChildTraceCode(code);
        if (splitCode == null) {
            throw new IllegalArgumentException("拆零子码不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("printType", "SPLIT_LABEL");
        result.put("childTraceCode", splitCode.getChildTraceCode());
        result.put("parentTraceCode", splitCode.getParentTraceCode());
        result.put("drugName", splitCode.getDrugName());
        result.put("batchNumber", splitCode.getBatchNumber());
        result.put("minUnit", splitCode.getMinUnit());
        result.put("splitUnits", splitCode.getSplitUnits());
        result.put("status", splitCode.getStatus());
        result.put("labelText", splitCode.getDrugName() + " " + splitCode.getSplitUnits() + splitCode.getMinUnit() + " 子码:" + splitCode.getChildTraceCode());
        return result;
    }

    private String generateChildCode(String parentTraceCode) {
        int next = drugDao.countSplitChildren(parentTraceCode) + 1;
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String childCode;
        do {
            childCode = parentTraceCode + "-S" + date + "-" + String.format("%03d", next++);
        } while (drugDao.getSplitByChildTraceCode(childCode) != null);
        return childCode;
    }

    private int safeUnits(Integer value) {
        return value == null ? 0 : value;
    }

    private int requirePositive(Integer value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
