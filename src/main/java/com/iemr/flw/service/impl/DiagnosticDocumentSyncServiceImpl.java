package com.iemr.flw.service.impl;

import com.iemr.flw.domain.iemr.BenFlowStatus;
import com.iemr.flw.domain.iemr.DiagnosticDocument;
import com.iemr.flw.masterEnum.DiagnosticDocumentSyncStatus;
import com.iemr.flw.repo.iemr.BenFlowStatusRepo;
import com.iemr.flw.repo.iemr.DiagnosticDocumentRepo;
import com.iemr.flw.service.DiagnosticDocumentSyncService;
import com.iemr.flw.service.S3StorageService;
import com.iemr.flw.utils.CryptoUtil;
import com.iemr.flw.utils.RetryBackoffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class DiagnosticDocumentSyncServiceImpl implements DiagnosticDocumentSyncService {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticDocumentSyncServiceImpl.class);
    private static final int MAX_BACKOFF_MINUTES = 60;

    @Value("${diagnostic.documents.storage-root}")
    private String storageRoot;

    @Value("${diagnostic.documents.sync.batch-size:20}")
    private int batchSize;

    @Value("${diagnostic.documents.sync.environment}")
    private String environmentToken;

    @Value("${diagnostic.documents.sync.s3.bucket}")
    private String bucketName;

    @Autowired
    private DiagnosticDocumentRepo diagnosticDocumentRepo;

    @Autowired
    private BenFlowStatusRepo benFlowStatusRepo;

    @Autowired
    private CryptoUtil cryptoUtil;

    @Autowired
    private S3StorageService s3StorageService;

    @Override
    public void syncDueDocuments() {
        if (!s3StorageService.isBucketReachable(bucketName)) {
            logger.debug("S3 unreachable — skipping sync tick");
            return;
        }

        List<DiagnosticDocument> due = diagnosticDocumentRepo
                .findDueForSync(new Timestamp(System.currentTimeMillis()), PageRequest.of(0, batchSize));
        if (due.isEmpty()) return;

        logger.info("Diagnostic document sync tick: {} document(s) due", due.size());
        for (DiagnosticDocument document : due) {
            syncSingle(document);
        }
    }

    private void syncSingle(DiagnosticDocument document) {
        try {
            Integer villageId = resolveVillageId(document.getBeneficiaryId());
            if (villageId == null) {
                logger.warn("Skipping sync for documentId={}: village could not be resolved for beneficiaryId={}",
                        document.getId(), document.getBeneficiaryId());
                return;
            }

            byte[] originalBytes = decryptAndVerify(document);
            String key = buildDocumentKey(document, villageId);

            s3StorageService.uploadObject(bucketName, key, originalBytes, document.getContentType());

            document.setSyncStatus(DiagnosticDocumentSyncStatus.UPLOADED.name());
            document.setS3ObjectKey(key);
            document.setSyncedDate(new Timestamp(System.currentTimeMillis()));
            document.setNextAttemptAt(null);
            diagnosticDocumentRepo.save(document);

            logger.info("Diagnostic document synced to S3: documentId={}, key={}", document.getId(), key);
        } catch (Exception e) {
            logger.error("Sync failed for documentId={}: {}", document.getId(), e.getMessage());
            int nextRetryCount = (document.getSyncRetryCount() == null ? 0 : document.getSyncRetryCount()) + 1;
            document.setSyncStatus(DiagnosticDocumentSyncStatus.FAILED.name());
            document.setSyncRetryCount(nextRetryCount);
            document.setNextAttemptAt(RetryBackoffUtil.computeNextAttempt(nextRetryCount, MAX_BACKOFF_MINUTES));
            diagnosticDocumentRepo.save(document);
        }
    }

    private byte[] decryptAndVerify(DiagnosticDocument document) throws Exception {
        Path filePath = Paths.get(storageRoot, document.getStoredPath());
        String encryptedPayload = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
        String base64Original = cryptoUtil.decrypt(encryptedPayload);
        if (base64Original == null) {
            throw new Exception("Failed to decrypt document id=" + document.getId());
        }
        byte[] originalBytes = Base64.getDecoder().decode(base64Original);
        String recomputedHash = sha256Hex(originalBytes);
        if (!recomputedHash.equals(document.getSha256Hash())) {
            throw new Exception("Integrity check failed for document id=" + document.getId());
        }
        return originalBytes;
    }

    private Integer resolveVillageId(Long beneficiaryId) {
        List<BenFlowStatus> statuses = benFlowStatusRepo.findByBeneficiaryID(beneficiaryId);
        return statuses.isEmpty() ? null : statuses.get(0).getVillageID();
    }

    private String buildDocumentKey(DiagnosticDocument document, Integer villageId) {
        return S3StorageService.buildObjectKey(
                environmentToken,
                String.valueOf(villageId),
                String.valueOf(document.getBeneficiaryId()),
                document.getOrderType(),
                String.valueOf(document.getDiagnosticOrderId()),
                document.getDocumentType(),
                document.getSha256Hash() + "." + resolveExtension(document));
    }

    private String resolveExtension(DiagnosticDocument document) {
        String originalName = document.getOriginalFileName();
        if (originalName != null && originalName.contains(".")) {
            return originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }
        String contentType = document.getContentType();
        if (contentType == null) return "bin";
        switch (contentType.toLowerCase(Locale.ROOT)) {
            case "application/pdf":
                return "pdf";
            case "image/jpeg":
                return "jpg";
            case "image/png":
                return "png";
            default:
                return "bin";
        }
    }

    private static String sha256Hex(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format(Locale.ROOT, "%02x", b));
        }
        return hex.toString();
    }
}
