package com.iemr.flw.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MapplsReverseGeocodeResponse {

    private Integer responseCode;
    private List<Result> results;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String houseNumber;
        private String houseName;
        private String street;
        private String subSubLocality;
        private String subLocality;
        private String locality;
        private String village;
        private String district;
        private String subDistrict;
        private String city;
        private String state;
        private String pincode;
        private String formatted_address;
    }
}
