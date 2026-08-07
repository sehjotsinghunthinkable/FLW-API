package com.iemr.flw.masterEnum;

public enum DiagnosticDocumentSyncStatus {
    PENDING,
    UPLOADED,
    FAILED;

    public static DiagnosticDocumentSyncStatus fromString(String value) {
        if (value == null) return PENDING;
        for (DiagnosticDocumentSyncStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return PENDING;
    }
}
