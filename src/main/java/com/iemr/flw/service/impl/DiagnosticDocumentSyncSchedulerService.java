package com.iemr.flw.service.impl;

import com.iemr.flw.service.DiagnosticDocumentSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DiagnosticDocumentSyncSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticDocumentSyncSchedulerService.class);

    @Autowired
    private DiagnosticDocumentSyncService diagnosticDocumentSyncService;

    @Scheduled(fixedDelayString = "${diagnostic.documents.sync.tick-ms:30000}")
    public void syncTick() {
        try {
            diagnosticDocumentSyncService.syncDueDocuments();
        } catch (Exception e) {
            logger.error("Unexpected error during diagnostic document sync tick: {}", e.getMessage(), e);
        }
    }
}
