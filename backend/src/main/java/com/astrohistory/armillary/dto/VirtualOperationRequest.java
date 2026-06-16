package com.astrohistory.armillary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualOperationRequest {

    private UUID instrumentId;

    private double equatorialAxisAngleDeg;
    private double declinationAxisAngleDeg;
    private double azimuthAxisAngleDeg;
    private double altitudeAxisAngleDeg;

    private double rotationalSpeedRpm;
    private double loadKg;
    private double temperatureC;
    private String lubricantType;

    private boolean simulateWear;
    private int simulateHours;
}
