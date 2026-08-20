package com.example.demodrug.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DrugSplitCode {
    private Long id;
    private String parentTraceCode;
    private String childTraceCode;
    private String drugName;
    private String batchNumber;
    private String minUnit;
    private Integer splitUnits;
    private Integer remainingUnits;
    private String status;
    private String createdBy;
    private String dispensedToPatientId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime dispensedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
