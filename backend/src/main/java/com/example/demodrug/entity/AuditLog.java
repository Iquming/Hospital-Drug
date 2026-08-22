package com.example.demodrug.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLog {
    private Long id;
    private Long operatorId;
    private String operatorName;
    private String operatorRole;
    private String action;
    private String targetType;
    private String targetId;
    private String requestPath;
    private String clientIp;
    private String beforeState;
    private String afterState;
    private String result;
    private String message;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
