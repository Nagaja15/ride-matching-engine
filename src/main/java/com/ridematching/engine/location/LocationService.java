package com.ridematching.engine.location;

import com.ridematching.engine.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private static final String DRIVER_GEO_KEY = "driver:locations";

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    public void updateDriverLocation(String driverId, double longitude, double latitude) {
        redisTemplate.opsForGeo().add(
                DRIVER_GEO_KEY,
                new Point(longitude, latitude),
                driverId
        );
        log.info("Updated location for driver: {} at ({}, {})", driverId, latitude, longitude);
    }

    public List<String> findNearbyDrivers(double longitude, double latitude, double radiusKm) {
        GeoOperations<String, String> geoOps = redisTemplate.opsForGeo();

        Circle circle = new Circle(
                new Point(longitude, latitude),
                new Distance(radiusKm, Metrics.KILOMETERS)
        );

        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands
                .GeoRadiusCommandArgs
                .newGeoRadiusArgs()
                .includeDistance()
                .sortAscending()
                .limit(20);

        GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                geoOps.radius(DRIVER_GEO_KEY, circle, args);

        if (results == null) return List.of();

        List<String> allNearby = results.getContent()
                .stream()
                .map(r -> r.getContent().getName())
                .collect(Collectors.toList());

        log.info("Found {} drivers within {}km of ({}, {}): {}",
                allNearby.size(), radiusKm, latitude, longitude, allNearby);

        // ── Fix: treat NULL isAvailable as available (new users) ─────────────
        return allNearby.stream()
                .filter(email -> userRepository.findByEmail(email)
                        .map(user -> !Boolean.FALSE.equals(user.getIsAvailable()))
                        .orElse(false))
                .collect(Collectors.toList());
    }

    public void removeDriver(String driverId) {
        redisTemplate.opsForGeo().remove(DRIVER_GEO_KEY, driverId);
        log.info("Removed driver from Redis: {}", driverId);
    }
}