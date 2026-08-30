package com.hospital.pharmacy.entity;

import lombok.Data;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
public class DispenseRecord {
    private Long id;
    private String patientId;   //  患者ID
    private String patientName;
    private String traceCode;
    private String drugName;
    private String parentTraceCode;
    private String childTraceCode;
    private Integer dispenseUnits;
    private String dispenseUnit;
    private String dispenseType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime dispenseTime;
}
