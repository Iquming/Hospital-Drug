package com.example.demodrug.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DrugApplicationItem {
    private Long id;
    private Long applicationId;
    private String hisItemNo;
    private String hisDrugCode;
    private Long localCatalogId;
    private String localDrugName;
    private String drugName;
    private String specification;
    private Integer requestedQuantity;
    private Integer dispensedQuantity;
    private Integer returnedQuantity;
    private String unit;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
