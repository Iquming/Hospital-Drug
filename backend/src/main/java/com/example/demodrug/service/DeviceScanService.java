package com.example.demodrug.service;

import com.example.demodrug.dao.DrugDao;
import com.example.demodrug.constant.SplitCodeStatus;
import com.example.demodrug.constant.StockStatus;
import com.example.demodrug.entity.DrugSplitCode;
import com.example.demodrug.entity.DrugStock;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DeviceScanService {

    private static final Set<String> SCENES = Set.of("INBOUND", "DISPENSE", "RETURN", "QC");

    @Resource
    private DrugDao drugDao;

    public Map<String, Object> verify(String scene, String traceCode, String expectedDrugName) {
        String normalizedScene = normalizeScene(scene);
        String normalizedTraceCode = requireText(traceCode, "追溯码不能为空");
        String normalizedExpectedDrugName = StringUtils.hasText(expectedDrugName) ? expectedDrugName.trim() : "";

        DrugSplitCode splitCode = drugDao.getSplitByChildTraceCode(normalizedTraceCode);
        DrugStock drug = splitCode == null
                ? drugDao.getDrugByTraceCode(normalizedTraceCode)
                : drugDao.getDrugByTraceCode(splitCode.getParentTraceCode());
        return switch (normalizedScene) {
            case "INBOUND" -> verifyInbound(normalizedScene, normalizedTraceCode, drug, splitCode);
            case "DISPENSE" -> verifyDispense(normalizedScene, normalizedTraceCode, normalizedExpectedDrugName, drug, splitCode);
            case "RETURN" -> verifyReturn(normalizedScene, normalizedTraceCode, drug, splitCode);
            case "QC" -> verifyQc(normalizedScene, normalizedTraceCode, drug, splitCode);
            default -> throw new IllegalArgumentException("扫描场景不合法");
        };
    }

    private Map<String, Object> verifyInbound(String scene, String traceCode, DrugStock drug, DrugSplitCode splitCode) {
        if (drug == null && splitCode == null) {
            return response(false, scene, traceCode, null, "追溯码未建档，可入库", "ALLOW");
        }
        return decorate(response(true, scene, traceCode, drug, "该追溯码已在库，禁止重复建档", "BLOCK"), drug, splitCode);
    }

    private Map<String, Object> verifyDispense(String scene, String traceCode, String expectedDrugName, DrugStock drug, DrugSplitCode splitCode) {
        if (splitCode != null) {
            return verifySplitDispense(scene, traceCode, expectedDrugName, drug, splitCode);
        }
        if (drug == null) {
            return response(false, scene, traceCode, null, "无效追溯码，系统中无此实物档案", "BLOCK");
        }
        if (!StockStatus.IN_STOCK.equals(safeText(drug.getStatus(), StockStatus.IN_STOCK)) || safeQuantity(drug) <= 0) {
            return decorate(response(true, scene, traceCode, drug, "该单品已出库或库存状态异常", "BLOCK"), drug, null);
        }
        if (StringUtils.hasText(expectedDrugName) && !expectedDrugName.equals(drug.getDrugName())) {
            return decorate(response(true, scene, traceCode, drug, "药名不一致，禁止发药", "BLOCK"), drug, null);
        }
        return decorate(response(true, scene, traceCode, drug, "核对通过，可执行发药", "ALLOW"), drug, null);
    }

    private Map<String, Object> verifyReturn(String scene, String traceCode, DrugStock drug, DrugSplitCode splitCode) {
        if (splitCode != null) {
            if (!SplitCodeStatus.DISPENSED.equals(splitCode.getStatus())) {
                return decorate(response(true, scene, traceCode, drug, "拆零子码未发药或已处理，不能退药", "BLOCK"), drug, splitCode);
            }
            return decorate(response(true, scene, traceCode, drug, "拆零子码核对通过，可执行退药", "ALLOW"), drug, splitCode);
        }
        if (drug == null) {
            return response(false, scene, traceCode, null, "无效追溯码，系统中无此实物档案", "BLOCK");
        }
        if (!StockStatus.DISPENSED.equals(safeText(drug.getStatus(), ""))) {
            return decorate(response(true, scene, traceCode, drug, "该单品未出库，不符合退药条件", "BLOCK"), drug, null);
        }
        return decorate(response(true, scene, traceCode, drug, "核对通过，可执行退药", "ALLOW"), drug, null);
    }

    private Map<String, Object> verifyQc(String scene, String traceCode, DrugStock drug, DrugSplitCode splitCode) {
        if (drug == null) {
            return response(false, scene, traceCode, null, "无效追溯码，系统中无此实物档案", "BLOCK");
        }
        Map<String, Object> result = response(true, scene, traceCode, drug, "已找到药品档案，请按质控流程复核", "REVIEW");
        result.put("qualityStatus", qualityStatus(drug));
        return decorate(result, drug, splitCode);
    }

    private Map<String, Object> verifySplitDispense(String scene, String traceCode, String expectedDrugName, DrugStock parent, DrugSplitCode splitCode) {
        if (!SplitCodeStatus.AVAILABLE.equals(splitCode.getStatus())) {
            return decorate(response(true, scene, traceCode, parent, "拆零子码状态不是可发药", "BLOCK"), parent, splitCode);
        }
        if (StringUtils.hasText(expectedDrugName) && !expectedDrugName.equals(splitCode.getDrugName())) {
            return decorate(response(true, scene, traceCode, parent, "药名不一致，禁止发药", "BLOCK"), parent, splitCode);
        }
        return decorate(response(true, scene, traceCode, parent, "拆零子码核对通过，可执行发药", "ALLOW"), parent, splitCode);
    }

    private Map<String, Object> response(boolean matched,
                                        String scene,
                                        String traceCode,
                                        DrugStock drug,
                                        String message,
                                        String suggestion) {
        Map<String, Object> result = new HashMap<>();
        result.put("matched", matched);
        result.put("scene", scene);
        result.put("traceCode", traceCode);
        result.put("drug", drug);
        result.put("message", message);
        result.put("suggestion", suggestion);
        return result;
    }

    private Map<String, Object> decorate(Map<String, Object> result, DrugStock drug, DrugSplitCode splitCode) {
        if (splitCode != null) {
            result.put("codeType", "CHILD");
            result.put("parentTraceCode", splitCode.getParentTraceCode());
            result.put("childTraceCode", splitCode.getChildTraceCode());
            result.put("availableUnits", splitCode.getRemainingUnits());
            result.put("splitUnits", splitCode.getSplitUnits());
            result.put("minUnit", splitCode.getMinUnit());
            result.put("splitStatus", splitCode.getStatus());
            return result;
        }
        result.put("codeType", "PARENT");
        if (drug != null) {
            result.put("parentTraceCode", drug.getTraceCode());
            result.put("childTraceCode", null);
            result.put("availableUnits", drug.getRemainingMinUnits());
            result.put("minUnit", safeText(drug.getMinUnit(), safeText(drug.getPackageUnit(), "盒")));
            result.put("splitStatus", drug.getStockType());
        }
        return result;
    }

    private Map<String, Object> qualityStatus(DrugStock drug) {
        Map<String, Object> status = new HashMap<>();
        Integer daysLeft = daysLeft(drug.getExpireDate());
        status.put("quantity", safeQuantity(drug));
        status.put("stockStatus", drug.getStatus());
        status.put("version", drug.getVersion());
        status.put("expired", daysLeft != null && daysLeft < 0);
        status.put("nearExpiry", daysLeft != null && daysLeft >= 0 && daysLeft <= 90);
        status.put("daysLeft", daysLeft);
        status.put("expireDate", drug.getExpireDate());
        status.put("batchNumber", drug.getBatchNumber());
        return status;
    }

    private Integer daysLeft(String expireDate) {
        if (!StringUtils.hasText(expireDate)) {
            return null;
        }
        try {
            String value = expireDate.trim();
            LocalDate date = LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
            return (int) ChronoUnit.DAYS.between(LocalDate.now(), date);
        } catch (Exception e) {
            return null;
        }
    }

    private int safeQuantity(DrugStock drug) {
        return drug.getQuantity() == null ? 0 : drug.getQuantity();
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String normalizeScene(String scene) {
        String normalizedScene = requireText(scene, "扫描场景不能为空").toUpperCase(Locale.ROOT);
        if (!SCENES.contains(normalizedScene)) {
            throw new IllegalArgumentException("扫描场景不合法");
        }
        return normalizedScene;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
