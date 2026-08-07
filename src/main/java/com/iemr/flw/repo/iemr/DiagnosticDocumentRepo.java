package com.iemr.flw.repo.iemr;

import com.iemr.flw.domain.iemr.DiagnosticDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiagnosticDocumentRepo extends JpaRepository<DiagnosticDocument, Long> {

    Optional<DiagnosticDocument> findByDiagnosticOrderIdAndDocumentTypeAndDeletedFalse(Long diagnosticOrderId, String documentType);

    @Transactional
    @Modifying
    @Query("UPDATE DiagnosticDocument d SET d.vanSerialNo = d.id WHERE d.id = :id")
    void updateVanSerialNo(@Param("id") Long id);

    @Query("SELECT d FROM DiagnosticDocument d WHERE d.syncStatus IN ('PENDING', 'FAILED') " +
            "AND d.deleted = false AND (d.nextAttemptAt IS NULL OR d.nextAttemptAt <= :now) " +
            "ORDER BY d.id ASC")
    List<DiagnosticDocument> findDueForSync(@Param("now") Timestamp now, Pageable pageable);
}
