package com.careconnect.patient.service;

public enum ChartScope {
    NONE,
    DEMOGRAPHICS,
    FULL;

    public boolean covers(ChartScope needed) {
        return ordinal() >= needed.ordinal();
    }
}
