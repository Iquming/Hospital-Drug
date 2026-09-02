package com.hospital.pharmacy.service;

import com.hospital.pharmacy.constant.DispenseOperation;
import com.hospital.pharmacy.constant.DispenseType;
import com.hospital.pharmacy.constant.HisApplicationItemStatus;
import com.hospital.pharmacy.constant.HisApplicationStatus;
import com.hospital.pharmacy.constant.StockStatus;
import com.hospital.pharmacy.dao.DrugDao;
import com.hospital.pharmacy.dao.HisIntegrationDao;
import com.hospital.pharmacy.entity.DrugApplication;
import com.hospital.pharmacy.entity.DrugApplicationItem;
import com.hospital.pharmacy.entity.DrugSplitCode;
import com.hospital.pharmacy.entity.DrugStock;
import com.hospital.pharmacy.entity.DispenseRecord;
import com.hospital.pharmacy.exception.BusinessException;
import com.hospital.pharmacy.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;

@Service
public class ApplicationDispenseService {

    @Resource
    private HisIntegrationDao hisIntegrationDao;

    @Resource
    private DrugDao drugDao;

    @Resource
    private HisApplicationService hisApplicationService;

    @Resource
    private HisCallbackService hisCallbackService;

    @Resource
    private AuditLogService auditLogService;

    @Transactional(rollbackFor = Exception.class)
    public DrugApplication dispense(Long itemId, String traceCode, String operator) {
        DrugApplicationItem item = requireDispensableItem(itemId);
        DrugApplication application = requireApplication(item.getApplicationId());
        requireApprovedApplication(application);
        DispenseUnit scanned = resolveDispenseUnit(traceCode, item, false);

        if (scanned.splitCode() != null) {
            if (drugDao.markSplitDispensed(scanned.splitCode().getChildTraceCode(), application.getPatientId()) <= 0) {
                throw new BusinessException(ErrorCode.STOCK_CONFLICT, "拆零子码已发放或状态已变化");
            }
        } else if (drugDao.dispenseDrug(scanned.stock().getTraceCode()) <= 0) {
            throw new BusinessException(ErrorCode.STOCK_CONFLICT, "该追溯码已出库或库存状态异常");
        }

        if (hisIntegrationDao.addDispensedQuantity(itemId, scanned.quantity()) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "本次发药数量超过申请明细剩余数量");
        }

        drugDao.saveRecord(scanned.traceCode(), scanned.drugName(), application.getPatientName(),
                application.getPatientId(), scanned.parentTraceCode(), scanned.childTraceCode(),
                scanned.quantity(), scanned.unit(), scanned.dispenseType(), application.getId(), itemId);
        String status = hisApplicationService.refreshStatus(application.getId());
        hisCallbackService.enqueue(application.getId(), "DISPENSE_STATUS_CHANGED", operator);
        auditLogService.record("HIS_APPLICATION_DISPENSE", "drug_application_item", String.valueOf(itemId),
                item.getStatus(), status, "SUCCESS", scanned.traceCode() + " 操作人:" + operator);
        return hisApplicationService.detail(application.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public DrugApplication returnDrug(Long itemId, String traceCode, String operator) {
        DrugApplicationItem item = hisIntegrationDao.findItem(itemId);
        if (item == null) {
            throw new IllegalArgumentException("申请明细不存在");
        }
        if (!HisApplicationItemStatus.PARTIAL.equals(item.getStatus())
                && !HisApplicationItemStatus.DISPENSED.equals(item.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "该申请明细当前没有可退药数量");
        }
        DrugApplication application = requireApplication(item.getApplicationId());
        DispenseUnit scanned = resolveDispenseUnit(traceCode, item, true);
        DispenseRecord originalRecord = drugDao.findReturnableDispenseRecord(scanned.traceCode(),
                application.getId(), itemId, application.getPatientId());
        if (originalRecord == null) {
            throw new BusinessException(ErrorCode.PRESCRIPTION_MISMATCH,
                    "该追溯码不属于当前患者和申请明细，不能退药");
        }
        if (drugDao.claimReturnedUnits(originalRecord.getId(), scanned.quantity()) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "原发药流水的可退数量不足");
        }

        if (scanned.splitCode() != null) {
            if (drugDao.markSplitReturned(scanned.splitCode().getChildTraceCode(), application.getPatientId()) <= 0) {
                throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "拆零子码未发药、患者不一致或已经退回");
            }
        } else if (drugDao.restoreReturnedDrug(scanned.traceCode()) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "该追溯码未发药或已经退回");
        }

        if (hisIntegrationDao.addReturnedQuantity(itemId, scanned.quantity()) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "退药数量超过该明细已发数量");
        }

        String operation = scanned.splitCode() == null
                ? DispenseOperation.RETURNED_BY_PATIENT : DispenseOperation.SPLIT_RETURNED_BY_PATIENT;
        drugDao.saveReturnRecord(scanned.traceCode(), scanned.drugName(), operation + " " + application.getPatientName(),
                application.getPatientId(), scanned.parentTraceCode(), scanned.childTraceCode(),
                scanned.quantity(), scanned.unit(), scanned.dispenseType(), application.getId(), itemId,
                originalRecord.getId());
        String status = hisApplicationService.refreshStatus(application.getId());
        hisCallbackService.enqueue(application.getId(), "RETURN_STATUS_CHANGED", operator);
        auditLogService.record("HIS_APPLICATION_RETURN", "drug_application_item", String.valueOf(itemId),
                item.getStatus(), status, "SUCCESS", scanned.traceCode() + " 操作人:" + operator);
        return hisApplicationService.detail(application.getId());
    }

    private DrugApplicationItem requireDispensableItem(Long itemId) {
        DrugApplicationItem item = hisIntegrationDao.findItem(itemId);
        if (item == null) {
            throw new IllegalArgumentException("申请明细不存在");
        }
        if (item.getLocalCatalogId() == null || HisApplicationItemStatus.UNMAPPED.equals(item.getStatus())) {
            throw new BusinessException(ErrorCode.HIS_MAPPING_REQUIRED, "该HIS药品编码尚未映射本地药品档案");
        }
        if (!HisApplicationItemStatus.PENDING.equals(item.getStatus())
                && !HisApplicationItemStatus.PARTIAL.equals(item.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "该申请明细当前不可发药");
        }
        return item;
    }

    private DrugApplication requireApplication(Long applicationId) {
        DrugApplication application = hisIntegrationDao.findApplicationById(applicationId);
        if (application == null) {
            throw new IllegalArgumentException("HIS申请单不存在");
        }
        return application;
    }

    private void requireApprovedApplication(DrugApplication application) {
        if (!"APPROVED".equals(application.getReviewStatus())) {
            String message = Boolean.TRUE.equals(application.getSpecialReviewRequired())
                    ? "特殊管理药品尚未完成人工复核，不能发药"
                    : "处方尚未通过通用审核，不能发药";
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, message);
        }
        if (HisApplicationStatus.RETURN_REQUIRED.equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "HIS已申请撤销，请完成退药，不能继续发药");
        }
    }

    private DispenseUnit resolveDispenseUnit(String rawTraceCode, DrugApplicationItem item, boolean returning) {
        String traceCode = requireText(rawTraceCode, "追溯码不能为空");
        DrugSplitCode splitCode = drugDao.getSplitByChildTraceCode(traceCode);
        if (splitCode != null) {
            DrugStock parent = drugDao.getDrugByTraceCode(splitCode.getParentTraceCode());
            requireCatalogMatch(parent, item);
            requireUnitMatch(item.getUnit(), splitCode.getMinUnit());
            requireWithinExpiry(parent, returning);
            return new DispenseUnit(traceCode, splitCode.getDrugName(), splitCode.getSplitUnits(),
                    splitCode.getMinUnit(), DispenseType.SPLIT_PACKAGE, splitCode.getParentTraceCode(),
                    splitCode.getChildTraceCode(), parent, splitCode);
        }
        DrugStock stock = drugDao.getDrugByTraceCode(traceCode);
        if (stock == null) {
            throw new IllegalArgumentException("系统中不存在该追溯码");
        }
        requireCatalogMatch(stock, item);
        requireUnitMatch(item.getUnit(), safeText(stock.getPackageUnit(), "盒"));
        requireWithinExpiry(stock, returning);
        if (!returning && stock.getQuantity() != null && stock.getQuantity() != 1) {
            throw new BusinessException(ErrorCode.STOCK_CONFLICT, "整包装追溯码必须对应一个可核销包装");
        }
        return new DispenseUnit(traceCode, stock.getDrugName(), 1, safeText(stock.getPackageUnit(), "盒"),
                DispenseType.WHOLE_PACKAGE, traceCode, null, stock, null);
    }

    private void requireCatalogMatch(DrugStock stock, DrugApplicationItem item) {
        if (stock == null || stock.getCatalogId() == null) {
            throw new BusinessException(ErrorCode.HIS_MAPPING_REQUIRED, "该库存尚未关联本地药品档案");
        }
        if (!stock.getCatalogId().equals(item.getLocalCatalogId())) {
            throw new BusinessException(ErrorCode.PRESCRIPTION_MISMATCH,
                    "扫码药品与HIS申请明细映射的本地药品不一致");
        }
    }

    private void requireUnitMatch(String requestedUnit, String scannedUnit) {
        if (!safeText(requestedUnit, "").equals(safeText(scannedUnit, ""))) {
            throw new BusinessException(ErrorCode.PRESCRIPTION_MISMATCH,
                    "扫码包装单位与申请明细单位不一致，应发单位：" + requestedUnit);
        }
    }

    private void requireWithinExpiry(DrugStock stock, boolean returning) {
        if (!returning && !drugDao.isWithinExpiry(stock.getTraceCode())) {
            throw new BusinessException(ErrorCode.STOCK_CONFLICT, "药品有效期缺失或已过期，禁止发药");
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

    private record DispenseUnit(String traceCode, String drugName, int quantity, String unit,
                                String dispenseType, String parentTraceCode, String childTraceCode,
                                DrugStock stock, DrugSplitCode splitCode) {
    }
}
