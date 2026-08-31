package com.hospital.pharmacy.service;

import com.hospital.pharmacy.constant.HisApplicationItemStatus;
import com.hospital.pharmacy.constant.HisApplicationStatus;
import com.hospital.pharmacy.dao.DrugCatalogDao;
import com.hospital.pharmacy.dao.HisIntegrationDao;
import com.hospital.pharmacy.dto.HisDtos;
import com.hospital.pharmacy.entity.DrugApplication;
import com.hospital.pharmacy.entity.DrugApplicationItem;
import com.hospital.pharmacy.entity.DrugCatalog;
import com.hospital.pharmacy.entity.HisDrugMapping;
import com.hospital.pharmacy.exception.BusinessException;
import com.hospital.pharmacy.exception.ErrorCode;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

@Service
public class HisApplicationService {

    @Resource
    private HisIntegrationDao hisIntegrationDao;

    @Resource
    private DrugCatalogDao drugCatalogDao;

    @Resource
    private HisCallbackService hisCallbackService;

    @Resource
    private AuditLogService auditLogService;

    @Resource
    private ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public HisDtos.ReceiveResponse receive(HisDtos.ApplicationRequest rawRequest) {
        HisDtos.ApplicationRequest request = normalize(rawRequest);
        String existingResponse = hisIntegrationDao.findInboundResponse(request.eventId());
        if (existingResponse != null) {
            try {
                HisDtos.ReceiveResponse response = objectMapper.readValue(existingResponse, HisDtos.ReceiveResponse.class);
                return new HisDtos.ReceiveResponse(response.eventId(), response.localApplicationId(),
                        response.applicationNo(), response.status(), true, response.warnings());
            } catch (JacksonException e) {
                throw new IllegalStateException("重复HIS事件响应读取失败", e);
            }
        }
        if (!hisIntegrationDao.reserveInboundEvent(request.eventId(), "APPLICATION_RECEIVED")) {
            existingResponse = hisIntegrationDao.findInboundResponse(request.eventId());
            if (existingResponse != null) {
                try {
                    HisDtos.ReceiveResponse response = objectMapper.readValue(existingResponse, HisDtos.ReceiveResponse.class);
                    return new HisDtos.ReceiveResponse(response.eventId(), response.localApplicationId(),
                            response.applicationNo(), response.status(), true, response.warnings());
                } catch (JacksonException e) {
                    throw new IllegalStateException("重复HIS事件响应读取失败", e);
                }
            }
            throw new BusinessException(ErrorCode.IDEMPOTENT_PROCESSING, "HIS事件正在处理中", request.eventId());
        }

        DrugApplication existing = hisIntegrationDao.findApplication(request.sourceSystem(), request.applicationNo());
        Long applicationId;
        if (existing == null) {
            applicationId = hisIntegrationDao.createApplication(request, HisApplicationStatus.RECEIVED);
        } else {
            applicationId = existing.getId();
            if (request.revision() <= existing.getRevisionNo()) {
                DrugApplication current = detail(applicationId);
                HisDtos.ReceiveResponse response = new HisDtos.ReceiveResponse(request.eventId(), applicationId,
                        request.applicationNo(), current.getStatus(), true, List.of("申请单已接收，本次事件未重复写入"));
                hisIntegrationDao.saveInboundEvent(request.eventId(), applicationId, "APPLICATION_RECEIVED",
                        "DUPLICATE", toJson(response));
                return response;
            }
            if (hisIntegrationDao.hasDispensedQuantity(applicationId)) {
                throw new BusinessException(ErrorCode.HIS_REVISION_CONFLICT,
                        "申请单已开始发药，不能用新修订覆盖，请先走撤销或退药流程", request.eventId());
            }
            hisIntegrationDao.updateApplication(applicationId, request);
            hisIntegrationDao.deleteApplicationItems(applicationId);
        }

        List<String> warnings = new ArrayList<>();
        for (HisDtos.ApplicationItemRequest item : request.items()) {
            Long catalogId = hisIntegrationDao.findMappedCatalogId(request.sourceSystem(), item.hisDrugCode());
            String itemStatus = catalogId == null ? HisApplicationItemStatus.UNMAPPED : HisApplicationItemStatus.PENDING;
            hisIntegrationDao.createApplicationItem(applicationId, item, catalogId, itemStatus);
            if (catalogId == null) {
                warnings.add(item.hisDrugCode() + " " + item.drugName() + " 尚未映射本地药品档案");
            }
        }
        String status = refreshStatus(applicationId);
        HisDtos.ReceiveResponse response = new HisDtos.ReceiveResponse(request.eventId(), applicationId,
                request.applicationNo(), status, false, warnings);
        hisIntegrationDao.saveInboundEvent(request.eventId(), applicationId, "APPLICATION_RECEIVED",
                "ACCEPTED", toJson(response));
        hisCallbackService.enqueue(applicationId, "APPLICATION_RECEIVED", "HIS接口");
        auditLogService.record("HIS_APPLICATION_RECEIVED", "drug_application", String.valueOf(applicationId),
                null, status, "SUCCESS", request.applicationNo());
        return response;
    }

    public List<DrugApplication> list(String status, String keyword, String priority) {
        return hisIntegrationDao.listApplications(status, keyword, priority);
    }

    public DrugApplication detail(Long applicationId) {
        DrugApplication application = hisIntegrationDao.findApplicationById(applicationId);
        if (application == null) {
            throw new IllegalArgumentException("HIS申请单不存在");
        }
        application.setItems(hisIntegrationDao.findItems(applicationId));
        return application;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancel(String sourceSystem, String applicationNo, HisDtos.CancelRequest request) {
        requireText(request == null ? null : request.eventId(), "eventId不能为空");
        String duplicate = hisIntegrationDao.findInboundResponse(request.eventId().trim());
        if (duplicate != null) {
            try {
                return objectMapper.readValue(duplicate, new TypeReference<>() { });
            } catch (JacksonException e) {
                throw new IllegalStateException("重复撤销事件响应读取失败", e);
            }
        }
        if (!hisIntegrationDao.reserveInboundEvent(request.eventId().trim(), "APPLICATION_CANCELLED")) {
            throw new BusinessException(ErrorCode.IDEMPOTENT_PROCESSING, "HIS撤销事件正在处理中", request.eventId());
        }
        DrugApplication application = hisIntegrationDao.findApplication(
                textOr(sourceSystem, "HIS"), requireText(applicationNo, "申请单号不能为空"));
        if (application == null) {
            throw new IllegalArgumentException("HIS申请单不存在");
        }
        if (request.revision() != null && !request.revision().equals(application.getRevisionNo())) {
            throw new BusinessException(ErrorCode.HIS_REVISION_CONFLICT,
                    "撤销版本与当前申请单版本不一致", request.eventId());
        }
        String responseStatus;
        String message;
        if (hisIntegrationDao.hasCurrentDispensedQuantity(application.getId())) {
            hisIntegrationDao.markReturnRequired(application.getId(), textOr(request.reason(), "HIS撤销"));
            responseStatus = HisApplicationStatus.RETURN_REQUIRED;
            message = "申请单已发生发药，请完成原路退药";
        } else if (hisIntegrationDao.hasDispensedQuantity(application.getId())) {
            hisIntegrationDao.updateApplicationStatus(application.getId(), HisApplicationStatus.RETURNED);
            responseStatus = HisApplicationStatus.RETURNED;
            message = "已发药品均已退回，申请单结束";
        } else {
            hisIntegrationDao.cancelApplication(application.getId(), textOr(request.reason(), "HIS撤销"));
            responseStatus = HisApplicationStatus.CANCELLED;
            message = "申请单已撤销";
        }
        hisCallbackService.enqueue(application.getId(), "APPLICATION_CANCELLED", "HIS接口");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("eventId", request.eventId());
        response.put("applicationNo", applicationNo);
        response.put("status", responseStatus);
        response.put("message", message);
        hisIntegrationDao.saveInboundEvent(request.eventId(), application.getId(), "APPLICATION_CANCELLED",
                "ACCEPTED", toJson(response));
        auditLogService.record("HIS_APPLICATION_CANCEL", "drug_application", String.valueOf(application.getId()),
                application.getStatus(), responseStatus, "SUCCESS", message);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public DrugApplication review(Long applicationId, HisDtos.ReviewRequest request, String reviewer) {
        DrugApplication application = detail(applicationId);
        String decision = requireText(request == null ? null : request.decision(), "审方结论不能为空").toUpperCase();
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            throw new IllegalArgumentException("审方结论只能是 APPROVED 或 REJECTED");
        }
        if (HisApplicationStatus.MAPPING_REQUIRED.equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.HIS_MAPPING_REQUIRED, "存在未匹配药品，不能完成审方");
        }
        String comment = textOr(request.comment(), "APPROVED".equals(decision) ? "处方审核通过" : "处方审核不通过");
        if (hisIntegrationDao.reviewApplication(applicationId, decision, comment, reviewer) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "当前申请单状态不允许审方");
        }
        String status = refreshStatus(applicationId);
        hisCallbackService.enqueue(applicationId, "PRESCRIPTION_REVIEWED", reviewer);
        auditLogService.record("PRESCRIPTION_REVIEW", "drug_application", String.valueOf(applicationId),
                application.getReviewStatus(), decision, "SUCCESS", comment + " 操作人:" + reviewer);
        return detail(applicationId);
    }

    public List<HisDrugMapping> mappings() {
        return hisIntegrationDao.listMappings();
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveMapping(HisDtos.MappingRequest request, String operator) {
        String sourceSystem = requireText(request == null ? null : request.sourceSystem(), "HIS来源不能为空");
        String hisDrugCode = requireText(request.hisDrugCode(), "HIS药品编码不能为空");
        if (request.localCatalogId() == null) {
            throw new IllegalArgumentException("请选择本地药品档案");
        }
        DrugCatalog catalog = drugCatalogDao.findById(request.localCatalogId());
        if (catalog == null) {
            throw new IllegalArgumentException("本地药品档案不存在或已停用");
        }
        List<Long> affected = hisIntegrationDao.findUnmappedApplicationIds(sourceSystem, hisDrugCode);
        hisIntegrationDao.saveMapping(sourceSystem, hisDrugCode, request.localCatalogId(), operator);
        for (Long applicationId : affected) {
            String before = hisIntegrationDao.findApplicationById(applicationId).getStatus();
            String after = refreshStatus(applicationId);
            if (!before.equals(after)) {
                hisCallbackService.enqueue(applicationId, "APPLICATION_MAPPING_COMPLETED", operator);
            }
        }
        auditLogService.record("HIS_DRUG_MAPPING", "his_drug_mapping", sourceSystem + ":" + hisDrugCode,
                null, String.valueOf(request.localCatalogId()), "SUCCESS", operator);
    }

    public String refreshStatus(Long applicationId) {
        DrugApplication application = hisIntegrationDao.findApplicationById(applicationId);
        if (application == null) {
            throw new IllegalArgumentException("HIS申请单不存在");
        }
        if (HisApplicationStatus.CANCELLED.equals(application.getStatus())) {
            return application.getStatus();
        }
        Map<String, Object> totals = hisIntegrationDao.applicationTotals(applicationId);
        int requested = number(totals.get("requested_quantity"));
        int dispensed = number(totals.get("dispensed_quantity"));
        int returned = number(totals.get("returned_quantity"));
        int unmapped = number(totals.get("unmapped_count"));
        String status;
        if (HisApplicationStatus.RETURN_REQUIRED.equals(application.getStatus()) && dispensed > 0) {
            status = HisApplicationStatus.RETURN_REQUIRED;
        } else if (dispensed == 0 && returned > 0) {
            status = HisApplicationStatus.RETURNED;
        } else if (unmapped > 0) {
            status = HisApplicationStatus.MAPPING_REQUIRED;
        } else if ("REJECTED".equals(application.getReviewStatus())) {
            status = HisApplicationStatus.REVIEW_REJECTED;
        } else if (!"APPROVED".equals(application.getReviewStatus())) {
            status = HisApplicationStatus.REVIEW_PENDING;
        } else if (requested > 0 && dispensed >= requested) {
            status = HisApplicationStatus.DISPENSED;
        } else if (dispensed > 0) {
            status = HisApplicationStatus.PARTIALLY_DISPENSED;
        } else {
            status = HisApplicationStatus.READY;
        }
        hisIntegrationDao.updateApplicationStatus(applicationId, status);
        return status;
    }

    private HisDtos.ApplicationRequest normalize(HisDtos.ApplicationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("HIS申请单不能为空");
        }
        String eventId = requireText(request.eventId(), "eventId不能为空");
        String source = textOr(request.sourceSystem(), "HIS");
        String applicationNo = requireText(request.applicationNo(), "申请单号不能为空");
        String patientId = requireText(request.patientId(), "患者编号不能为空");
        String patientName = requireText(request.patientName(), "患者姓名不能为空");
        String patientGender = requireText(request.patientGender(), "患者性别不能为空");
        if (request.patientAge() == null || request.patientAge() < 0 || request.patientAge() > 150) {
            throw new IllegalArgumentException("患者年龄必须在0至150岁之间");
        }
        int revision = request.revision() == null || request.revision() <= 0 ? 1 : request.revision();
        String priority = textOr(request.priority(), "NORMAL").toUpperCase();
        if (!"NORMAL".equals(priority) && !"URGENT".equals(priority)) {
            throw new IllegalArgumentException("优先级只能是 NORMAL 或 URGENT");
        }
        if (request.prescribedAt() == null) {
            throw new IllegalArgumentException("处方开立时间不能为空");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("申请单至少需要一条药品明细");
        }
        if (request.items().size() > 100) {
            throw new IllegalArgumentException("单张申请单药品明细不能超过100条");
        }
        List<HisDtos.ApplicationItemRequest> items = new ArrayList<>();
        Set<String> itemNumbers = new HashSet<>();
        for (HisDtos.ApplicationItemRequest item : request.items()) {
            if (item == null || item.quantity() == null || item.quantity() <= 0) {
                throw new IllegalArgumentException("药品明细数量必须大于0");
            }
            String itemNo = requireText(item.itemNo(), "明细编号不能为空");
            if (!itemNumbers.add(itemNo)) {
                throw new IllegalArgumentException("药品明细编号不能重复：" + itemNo);
            }
            items.add(new HisDtos.ApplicationItemRequest(
                    itemNo,
                    requireText(item.hisDrugCode(), "HIS药品编码不能为空"),
                    requireText(item.drugName(), "药品名称不能为空"),
                    item.specification(), item.quantity(), requireText(item.unit(), "发药单位不能为空"),
                    requireText(item.dosage(), "单次剂量不能为空"),
                    requireText(item.frequency(), "用药频次不能为空"),
                    requireText(item.administrationRoute(), "给药途径不能为空"), item.usageInstruction()));
        }
        return new HisDtos.ApplicationRequest(eventId, source, applicationNo, revision, patientId, patientName,
                patientGender, request.patientAge(),
                requireText(request.encounterNo(), "就诊号不能为空"),
                requireText(request.departmentCode(), "科室编码不能为空"),
                requireText(request.departmentName(), "科室名称不能为空"),
                priority, request.prescribedAt(),
                requireText(request.prescriberId(), "处方医师编号不能为空"),
                requireText(request.prescriberName(), "处方医师姓名不能为空"),
                requireText(request.diagnosis(), "临床诊断不能为空"),
                requireText(request.allergyInfo(), "过敏史信息不能为空"), items);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("HIS事件响应生成失败", e);
        }
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String textOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
