package com.hospital.pharmacy.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class HisDtos {
    private HisDtos() {
    }

    public record ApplicationRequest(
            String eventId,
            String sourceSystem,
            String applicationNo,
            Integer revision,
            String patientId,
            String patientName,
            String encounterNo,
            String departmentCode,
            String departmentName,
            String priority,
            LocalDateTime prescribedAt,
            List<ApplicationItemRequest> items
    ) {
    }

    public record ApplicationItemRequest(
            String itemNo,
            String hisDrugCode,
            String drugName,
            String specification,
            Integer quantity,
            String unit
    ) {
    }

    public record ReceiveResponse(
            String eventId,
            Long localApplicationId,
            String applicationNo,
            String status,
            boolean duplicate,
            List<String> warnings
    ) {
    }

    public record CancelRequest(String eventId, Integer revision, String reason) {
    }

    public record DispenseRequest(String requestId, String traceCode) {
    }

    public record ReturnRequest(String requestId, String traceCode) {
    }

    public record MappingRequest(String sourceSystem, String hisDrugCode, Long localCatalogId) {
    }
}
