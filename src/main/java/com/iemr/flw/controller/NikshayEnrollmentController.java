package com.iemr.flw.controller;

import com.iemr.flw.dto.MapplsReverseGeocodeResponse;
import com.iemr.flw.dto.ReverseGeocodeRequest;
import com.iemr.flw.service.NikshayEnrollmentExportService;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/nikshay/enrollment")
public class NikshayEnrollmentController {

    private static final Logger logger = LoggerFactory.getLogger(NikshayEnrollmentController.class);

    @Autowired
    private NikshayEnrollmentExportService nikshayEnrollmentExportService;

    @PostMapping("/export-sample")
    @Operation(summary = "Test/prototype export: picks any beneficiary whose household has GPS coordinates, "
            + "reverse-geocodes them via Mappls, and returns a Nikshay-enrollment-template-shaped CSV.")
//    public ResponseEntity<byte[]> exportSample() {
    public ResponseEntity<MapplsReverseGeocodeResponse.Result> exportSample(@RequestBody ReverseGeocodeRequest request) {

        try {
//            byte[] csv = nikshayEnrollmentExportService.exportSampleEnrollmentCsv();
            MapplsReverseGeocodeResponse.Result csv = nikshayEnrollmentExportService.exportSampleEnrollmentCsv(
                    request.getLat(), request.getLng());
            return ResponseEntity.accepted().body(csv);
//            return ResponseEntity.ok()
//                    .contentType(MediaType.parseMediaType("text/csv"))
//                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=nikshay-enrollment.csv")
//                    .body(csv);
        } catch (Exception e) {
            logger.error("Nikshay sample export failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
