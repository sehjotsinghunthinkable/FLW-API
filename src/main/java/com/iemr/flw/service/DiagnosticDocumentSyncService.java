package com.iemr.flw.service;

public interface DiagnosticDocumentSyncService {

    /**
     * Runs one sync cycle: checks S3 connectivity, and if online, uploads a batch of
     * due (PENDING/FAILED, next-attempt-elapsed) documents. No-ops silently if offline.
     */
    void syncDueDocuments();
}
