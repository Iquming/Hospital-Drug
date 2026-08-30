package com.hospital.pharmacy.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HisCallbackEvent {
    private Long id;
    private String eventId;
    private Long applicationId;
    private String hisApplicationNo;
    private String patientName;
    private String eventType;
    private String applicationStatus;
    private String payloadJson;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextRetryTime;
    private String lastError;
    private String responseBody;
    private String operatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime sentTime;
}
