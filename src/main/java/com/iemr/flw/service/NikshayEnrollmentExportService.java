package com.iemr.flw.service;

import com.iemr.flw.dto.MapplsReverseGeocodeResponse;

public interface NikshayEnrollmentExportService {

    /**
     * Test/prototype export: picks any beneficiary whose household has GPS coordinates,
     * reverse-geocodes them via Mappls, and returns a Nikshay-enrollment-template-shaped CSV.
     */
    MapplsReverseGeocodeResponse.Result exportSampleEnrollmentCsv(Double lat, Double lng) throws Exception;
}
