package com.hospital.pharmacy.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DrugApplication {
    private Long id;
    private String sourceSystem;
    private String hisApplicationNo;
    private Integer revisionNo;
    private String patientId;
    private String patientName;
    private String patientGender;
    private Integer patientAge;
    private String encounterNo;
    private String departmentCode;
    private String departmentName;
    private String priority;
    private String status;
    private String prescriberId;
    private String prescriberName;
    private String diagnosis;
    private String allergyInfo;
    private String reviewStatus;
    private String reviewComment;
    private String reviewedBy;
    private Boolean specialReviewRequired;
    private String cancelReason;
    private String callbackStatus;
    private List<DrugApplicationItem> items;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime prescribedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime receivedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime reviewedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
