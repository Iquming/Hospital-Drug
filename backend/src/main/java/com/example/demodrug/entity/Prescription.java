package com.example.demodrug.entity;

public class Prescription {

    private Long id;                  // 处方流水号主键
    private String patientId;         // 患者编号 (如 P001)
    private String patientName;       // 患者姓名
    private String drugName;          // 处方开具的药品名称
    private String status;            // 处方状态：待发药 / 已发药 / 已退药
    private String traceCodeDispensed;// 实际扫码发出的追溯码 (发药时回写)
    private String createTime;        // 处方开立时间

    // --- 下面是 Getter 和 Setter 方法 ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getDrugName() {
        return drugName;
    }

    public void setDrugName(String drugName) {
        this.drugName = drugName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTraceCodeDispensed() {
        return traceCodeDispensed;
    }

    public void setTraceCodeDispensed(String traceCodeDispensed) {
        this.traceCodeDispensed = traceCodeDispensed;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }
}