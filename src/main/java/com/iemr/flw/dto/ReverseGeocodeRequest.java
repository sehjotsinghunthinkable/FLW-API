package com.iemr.flw.dto;

import lombok.Data;

@Data
public class ReverseGeocodeRequest {
    private Double lat;
    private Double lng;
}