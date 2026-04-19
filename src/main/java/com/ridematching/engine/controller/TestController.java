package com.ridematching.engine.controller;

import com.ridematching.engine.service.DriverLocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class TestController {

    @Autowired
    private DriverLocationService driverLocationService;

    @GetMapping("/test")
    public String test() {
        return "Ride Matching Engine is running 🚀";
    }

    // ✅ Save driver location
    @GetMapping("/driver/location")
    public String saveLocation(
            @RequestParam String driverId,
            @RequestParam double lat,
            @RequestParam double lon) {

        driverLocationService.saveDriverLocation(driverId, lon, lat);
        return "Driver location saved ✅";
    }

    // ✅ Find nearby drivers
    @GetMapping("/drivers/nearby")
    public Object getNearbyDrivers(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam double radius) {

        return driverLocationService.findNearbyDrivers(lon, lat, radius);
    }

    // ✅ Assign nearest AVAILABLE driver
    @GetMapping("/ride/assign")
    public String assignRide(
            @RequestParam double lat,
            @RequestParam double lon) {

        return driverLocationService.findNearestDriver(lon, lat, 5);
    }

    // 🔥 NEW: Set driver status (AVAILABLE / BUSY)
    @GetMapping("/driver/status")
    public String setStatus(
            @RequestParam String driverId,
            @RequestParam String status) {

        driverLocationService.setDriverStatus(driverId, status);
        return "Status updated ✅";
    }
}