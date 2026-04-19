package com.ridematching.engine.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RideEvent {
    private Long tripId;
    private Long riderId;
    private Long driverId;
    private String status;
    private double pickupLongitude;
    private double pickupLatitude;
    private double dropoffLongitude;
    private double dropoffLatitude;
    private String timestamp;
}