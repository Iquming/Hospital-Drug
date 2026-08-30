package com.hospital.pharmacy.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryCheckItem {
    private Long id;
    private Long checkId;
    private String traceCode;
    private String codeType;
    private String drugName;
    private String expectedStatus;
    private String actualStatus;
    private String differenceType;
    private String scannedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime scanTime;
}
