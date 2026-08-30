package com.example.demodrug.entity;

import java.util.Date;

public class DrugStock {

    private Long id;
    private Long catalogId;
    private String drugName;
    private String drugCode;
    private String traceCode;
    private String batchNumber;
    private Integer quantity;
    private String locationCode;
    private String unit;
    private Boolean isSplitAllowed;
    private String packageUnit;
    private String minUnit;
    private Integer minUnitsPerPackage;
    private Integer remainingMinUnits;
    private String parentTraceCode;
    private String stockType;
    private String status;
    private Long version;
    private String requestId;

    // ✅ 改动：将类型从 Date 改为 String，方便前端直接传 "2028-01-01" 格式
    // 原来如果是 Date 类型，前端传来的字符串需要额外转换，改为 String 更简单
    private String expireDate;

    private Date createTime;
    private Date updateTime;

    // ---------- Getter / Setter ----------
    public String getLocationCode() {
        return locationCode;
    }
    public void setLocationCode(String locationCode) {
        this.locationCode = locationCode;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCatalogId() { return catalogId; }
    public void setCatalogId(Long catalogId) { this.catalogId = catalogId; }

    public String getDrugName() { return drugName; }
    public void setDrugName(String drugName) { this.drugName = drugName; }

    public String getDrugCode() { return drugCode; }
    public void setDrugCode(String drugCode) { this.drugCode = drugCode; }

    public String getTraceCode() { return traceCode; }
    public void setTraceCode(String traceCode) { this.traceCode = traceCode; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Boolean getIsSplitAllowed() { return isSplitAllowed; }
    public void setIsSplitAllowed(Boolean isSplitAllowed) { this.isSplitAllowed = isSplitAllowed; }

    public String getPackageUnit() { return packageUnit; }
    public void setPackageUnit(String packageUnit) { this.packageUnit = packageUnit; }

    public String getMinUnit() { return minUnit; }
    public void setMinUnit(String minUnit) { this.minUnit = minUnit; }

    public Integer getMinUnitsPerPackage() { return minUnitsPerPackage; }
    public void setMinUnitsPerPackage(Integer minUnitsPerPackage) { this.minUnitsPerPackage = minUnitsPerPackage; }

    public Integer getRemainingMinUnits() { return remainingMinUnits; }
    public void setRemainingMinUnits(Integer remainingMinUnits) { this.remainingMinUnits = remainingMinUnits; }

    public String getParentTraceCode() { return parentTraceCode; }
    public void setParentTraceCode(String parentTraceCode) { this.parentTraceCode = parentTraceCode; }

    public String getStockType() { return stockType; }
    public void setStockType(String stockType) { this.stockType = stockType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    // ✅ expireDate 使用 String 类型，直接与数据库 DATETIME 字段映射
    public String getExpireDate() { return expireDate; }
    public void setExpireDate(String expireDate) { this.expireDate = expireDate; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
