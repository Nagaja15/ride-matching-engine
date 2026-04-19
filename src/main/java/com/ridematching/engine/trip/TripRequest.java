package com.ridematching.engine.trip;

import com.ridematching.engine.entity.User;
import lombok.Data;

@Data
public class TripRequest {
    private double pickupLatitude;
    private double pickupLongitude;
    private double dropoffLatitude;
    private double dropoffLongitude;
    private String vehicleType; // ← new: "Bike", "Auto", "Hatchback", "Sedan", "SUV","Scooty", "Any"
}