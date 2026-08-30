package com.hospital.pharmacy.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DrugCatalog {
    private Long id;
    private String drugName;
    private String specification;
    private String dosageForm;
    private String manufacturer;
    private Boolean isSplitAllowed;
    private String packageUnit;
    private String minUnit;
    private Integer minUnitsPerPackage;
    private Integer lowStockThreshold;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
