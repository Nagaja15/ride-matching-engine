package com.ridematching.engine.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.geo.Point;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoRadiusCommandArgs;
import org.springframework.data.geo.GeoResults;
import org.springframework.stereotype.Service;

@Service
public class DriverLocationService {

    private static final String KEY = "drivers";

    @Autowired
    private StringRedisTemplate redisTemplate;

    // ✅ Save driver location
    public void saveDriverLocation(String driverId, double longitude, double latitude) {
        redisTemplate.opsForGeo()
                .add(KEY, new RedisGeoCommands.GeoLocation<>(driverId, new Point(longitude, latitude)));
    }

    // ✅ Find nearby drivers
    public GeoResults<RedisGeoCommands.GeoLocation<String>> findNearbyDrivers(
            double longitude, double latitude, double radiusKm) {

        GeoOperations<String, String> geoOps = redisTemplate.opsForGeo();

        Circle area = new Circle(
                new Point(longitude, latitude),
                new Distance(radiusKm, Metrics.KILOMETERS)
        );

        return geoOps.radius(KEY, area,
                GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance());
    }

    // ✅ Set driver status (AVAILABLE / BUSY)
    public void setDriverStatus(String driverId, String status) {
        redisTemplate.opsForValue().set("driver:" + driverId, status);
    }

    // ✅ Get driver status
    public String getDriverStatus(String driverId) {
        String status = redisTemplate.opsForValue().get("driver:" + driverId);
        return status != null ? status : "AVAILABLE";
    }

    // ✅ Find nearest AVAILABLE driver
    public String findNearestDriver(double longitude, double latitude, double radiusKm) {

        GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                findNearbyDrivers(longitude, latitude, radiusKm);

        if (results == null || results.getContent().isEmpty()) {
            return "No drivers available ❌";
        }

        // 🔥 Loop through drivers and pick AVAILABLE one
        for (var result : results.getContent()) {

            String driverId = result.getContent().getName();
            String status = getDriverStatus(driverId);

            if ("AVAILABLE".equals(status)) {

                // 👉 Mark driver BUSY
                setDriverStatus(driverId, "BUSY");

                return "Assigned Driver: " + driverId + " 🚗";
            }
        }

        return "No AVAILABLE drivers ❌";
    }
}