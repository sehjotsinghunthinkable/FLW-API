package com.iemr.flw.service.impl;

import com.iemr.flw.domain.identity.RMNCHBeneficiaryDetailsRmnch;
import com.iemr.flw.domain.identity.RMNCHHouseHoldDetails;
import com.iemr.flw.domain.identity.RMNCHMBeneficiarycontact;
import com.iemr.flw.domain.identity.RMNCHMBeneficiarydetail;
import com.iemr.flw.domain.identity.RMNCHMBeneficiarymapping;
import com.iemr.flw.dto.MapplsReverseGeocodeResponse;
import com.iemr.flw.repo.identity.BeneficiaryRepo;
import com.iemr.flw.repo.identity.HouseHoldRepo;
import com.iemr.flw.service.NikshayEnrollmentExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class NikshayEnrollmentExportServiceImpl implements NikshayEnrollmentExportService {

    private static final int CANDIDATE_BATCH_SIZE = 20;

    private static final String CSV_HEADER = "beneficiaryId,typeOfCaseFinding,caste,firstName,middleLastName,age,gender," +
            "primaryPhone,address,state,district,tu,healthFacility,village,Pincode,area," +
            "maritalStatus,occupation,socioeconomicStatus,symptoms,hivStatus";

    @Value("${mappls.reverse-geocode.base-url}")
    private String mapplsBaseUrl;

    @Value("${mappls.reverse-geocode.access-token}")
    private String mapplsAccessToken;

    @Autowired
    private HouseHoldRepo houseHoldRepo;

    @Autowired
    private BeneficiaryRepo beneficiaryRepo;

    @Autowired
    @Qualifier("mapplsRestTemplate")
    private RestTemplate mapplsRestTemplate;

    @Override
//    public byte[] exportSampleEnrollmentCsv() throws Exception {
    public MapplsReverseGeocodeResponse.Result exportSampleEnrollmentCsv(Double lat, Double lng) throws Exception {
//        ResolvedBeneficiary resolved = resolveAnyTestBeneficiary();
        MapplsReverseGeocodeResponse.Result geo = reverseGeocode(lat, lng);
        return geo;
//        String[] row = buildRow(resolved, geo);
//        return writeCsv(row);
    }

    private ResolvedBeneficiary resolveAnyTestBeneficiary() throws Exception {
        List<RMNCHHouseHoldDetails> candidates = houseHoldRepo.findCandidatesWithGps(PageRequest.of(0, CANDIDATE_BATCH_SIZE));
        for (RMNCHHouseHoldDetails household : candidates) {
            if (household.getHouseoldId() == null) continue;

            List<RMNCHBeneficiaryDetailsRmnch> rmnchList = beneficiaryRepo.findByHouseoldId(household.getHouseoldId());
            RMNCHBeneficiaryDetailsRmnch rmnch = rmnchList.stream()
                    .filter(b -> b.getBenRegId() != null)
                    .findFirst().orElse(null);
            if (rmnch == null) continue;

            RMNCHMBeneficiarydetail detail = beneficiaryRepo.getDetailByBenRegID(BigInteger.valueOf(rmnch.getBenRegId()));
            if (detail == null) continue;

            List<RMNCHMBeneficiarymapping> mappings = beneficiaryRepo.findByBenRegIdFromMapping(BigInteger.valueOf(rmnch.getBenRegId()));
            if (mappings.isEmpty() || mappings.get(0).getBenContactsId() == null) continue;

            RMNCHMBeneficiarycontact contact = beneficiaryRepo.getContactById(mappings.get(0).getBenContactsId());
            if (contact == null) continue;

            return new ResolvedBeneficiary(household, rmnch, detail, contact);
        }
        throw new Exception("No household with GPS coordinates and a fully-linked beneficiary was found");
    }

    private MapplsReverseGeocodeResponse.Result reverseGeocode(Double lat, Double lng) throws Exception {
        String url = UriComponentsBuilder.fromHttpUrl(mapplsBaseUrl)
                .queryParam("lat", lat)
                .queryParam("lng", lng)
                .queryParam("region", "IND")
                .queryParam("lang", "en")
                .queryParam("access_token", mapplsAccessToken)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<MapplsReverseGeocodeResponse> response = mapplsRestTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), MapplsReverseGeocodeResponse.class);

        List<MapplsReverseGeocodeResponse.Result> results = response.getBody() != null ? response.getBody().getResults() : null;
        if (results == null || results.isEmpty()) {
            throw new Exception("Mappls reverse-geocode returned no results for lat=" + lat + ", lng=" + lng);
        }
        return results.get(0);
    }

    private String[] buildRow(ResolvedBeneficiary resolved, MapplsReverseGeocodeResponse.Result geo) {
        RMNCHMBeneficiarydetail detail = resolved.detail;

        String middleLastName = String.join(" ", nullToEmpty(detail.getMiddleName()), nullToEmpty(detail.getLastName())).trim();
        String primaryPhone = firstNonBlank(resolved.contact.getPreferredPhoneNum(), resolved.contact.getPhoneNum1());

        return new String[] {
                resolved.rmnch.getBenficieryid() != null ? String.valueOf(resolved.rmnch.getBenficieryid()) : "",
                "Active",
                "Other",
                nullToEmpty(detail.getFirstName()),
                middleLastName,
                resolved.rmnch.getAge() != null ? String.valueOf(resolved.rmnch.getAge()) : "",
                normalizeGender(detail.getGender()),
                nullToEmpty(primaryPhone),
                buildAddressLine(geo),
                nullToEmpty(geo.getState()),
                nullToEmpty(geo.getDistrict()),
                nullToEmpty(geo.getSubDistrict()),
                "Unknown",
                resolveVillage(geo),
                nullToEmpty(geo.getPincode()),
                resolveArea(geo),
                defaultIfBlank(detail.getMaritalstatus(), "Unknown"),
                defaultIfBlank(detail.getOccupation(), "Unknown"),
                defaultIfBlank(detail.getEconomicStatus(), "Unknown"),
                "Unknown",
                "Unknown"
        };
    }

    private String buildAddressLine(MapplsReverseGeocodeResponse.Result r) {
        List<String> parts = new ArrayList<>();
        for (String value : List.of(nullToEmpty(r.getHouseNumber()), nullToEmpty(r.getHouseName()),
                nullToEmpty(r.getStreet()), nullToEmpty(r.getSubSubLocality()))) {
            if (!value.isBlank()) parts.add(value);
        }
        return String.join(", ", parts);
    }

    private String resolveVillage(MapplsReverseGeocodeResponse.Result r) {
        for (String value : List.of(nullToEmpty(r.getVillage()), nullToEmpty(r.getLocality()),
                nullToEmpty(r.getSubLocality()), nullToEmpty(r.getCity()))) {
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private String resolveArea(MapplsReverseGeocodeResponse.Result r) {
        boolean urban = !nullToEmpty(r.getCity()).isBlank() || !nullToEmpty(r.getLocality()).isBlank();
        return urban ? "Urban" : "Rural";
    }

    private String normalizeGender(String gender) {
        if (gender == null) return "";
        switch (gender.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "M": return "Male";
            case "F": return "Female";
            case "O":
            case "T": return "Other";
            default: return gender;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private String defaultIfBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private byte[] writeCsv(String[] row) {
        StringBuilder sb = new StringBuilder();
        sb.append(CSV_HEADER).append("\n");
        for (int i = 0; i < row.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(csvEscape(row[i]));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static class ResolvedBeneficiary {
        final RMNCHHouseHoldDetails household;
        final RMNCHBeneficiaryDetailsRmnch rmnch;
        final RMNCHMBeneficiarydetail detail;
        final RMNCHMBeneficiarycontact contact;

        ResolvedBeneficiary(RMNCHHouseHoldDetails household, RMNCHBeneficiaryDetailsRmnch rmnch,
                RMNCHMBeneficiarydetail detail, RMNCHMBeneficiarycontact contact) {
            this.household = household;
            this.rmnch = rmnch;
            this.detail = detail;
            this.contact = contact;
        }
    }
}
