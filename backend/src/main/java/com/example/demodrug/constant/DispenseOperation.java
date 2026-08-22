package com.example.demodrug.constant;

public final class DispenseOperation {
    public static final String INBOUND_SCAN = "【扫码建档入库】";
    public static final String INBOUND_SCAN_AUDIT = "扫码建档入库";
    public static final String PRESCRIPTION_DISPENSE = "处方扫码发药";
    public static final String PHARMACY_OUTBOUND = "药房质控出库";
    public static final String RETURNED_BY_PATIENT = "【退药】患者退回";
    public static final String SPLIT_DISPENSE = "拆零扫码发药";
    public static final String SPLIT_RETURNED_BY_PATIENT = "【拆零退药】患者退回";

    public static final String DRUG_INBOUND = "DRUG_INBOUND";
    public static final String DRUG_INBOUND_DUPLICATE = "DRUG_INBOUND_DUPLICATE";

    private DispenseOperation() {
    }
}
