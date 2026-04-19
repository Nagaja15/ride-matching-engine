package com.ridematching.engine.location;

import com.ridematching.engine.entity.Ride;
import com.ridematching.engine.repository.UserRepository;
import com.ridematching.engine.trip.TripRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // ── Driver goes online ────────────────────────────────────────────────────
    @PostMapping("/api/driver/location")
    public ResponseEntity<String> updateLocation(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody LocationRequest request) {

        String driverEmail = userDetails.getUsername();
        locationService.updateDriverLocation(driverEmail, request.getLongitude(), request.getLatitude());

        userRepository.findByEmail(driverEmail).ifPresent(driver ->
                userRepository.updateDriverAvailability(driver.getId(), true));

        return ResponseEntity.ok("Location updated for driver: " + driverEmail);
    }

    // ── Driver streams live location during active trip ───────────────────────
    // Frontend calls this every 4 seconds when driver has ACCEPTED or IN_PROGRESS trip
    @PostMapping("/api/driver/location/stream")
    public ResponseEntity<String> streamLocation(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody LocationRequest request) {

        String driverEmail = userDetails.getUsername();

        // Update Redis GEO
        locationService.updateDriverLocation(driverEmail, request.getLongitude(), request.getLatitude());

        // Find active trip and push driver location to rider via WebSocket
        userRepository.findByEmail(driverEmail).ifPresent(driver -> {
            tripRepository.findByDriverIdOrderByCreatedAtDesc(driver.getId())
                .stream()
                .filter(t -> "ACCEPTED".equals(t.getStatus()) || "IN_PROGRESS".equals(t.getStatus()))
                .findFirst()
                .ifPresent(trip -> {
                    Map<String, Object> locationUpdate = Map.of(
                        "type", "DRIVER_LOCATION",
                        "tripId", trip.getId(),
                        "driverLat", request.getLatitude(),
                        "driverLon", request.getLongitude()
                    );
                    messagingTemplate.convertAndSend(
                        "/topic/rider/" + trip.getRiderId(),
                        locationUpdate
                    );
                });
        });

        return ResponseEntity.ok("Location streamed");
    }

    // ── Nearby drivers ────────────────────────────────────────────────────────
    @GetMapping("/api/trips/nearby")
    public ResponseEntity<List<String>> getNearbyDrivers(
            @RequestParam double longitude,
            @RequestParam double latitude,
            @RequestParam(defaultValue = "5.0") double radiusKm) {

        List<String> drivers = locationService.findNearbyDrivers(longitude, latitude, radiusKm);
        return ResponseEntity.ok(drivers);
    }

    // ── Online driver count ───────────────────────────────────────────────────
    @GetMapping("/api/trips/drivers/online-count")
    public ResponseEntity<Map<String, Object>> getOnlineDriverCount() {
        List<String> drivers = locationService.findNearbyDrivers(78.4867, 17.385, 50.0);
        return ResponseEntity.ok(Map.of(
                "count", drivers.size(),
                "available", drivers.size()
        ));
    }

    // ── Driver goes offline ───────────────────────────────────────────────────
    @DeleteMapping("/api/driver/location")
    public ResponseEntity<String> goOffline(
            @AuthenticationPrincipal UserDetails userDetails) {

        String driverEmail = userDetails.getUsername();
        locationService.removeDriver(driverEmail);

        userRepository.findByEmail(driverEmail).ifPresent(driver ->
                userRepository.updateDriverAvailability(driver.getId(), false));

        return ResponseEntity.ok("Driver offline: " + driverEmail);
    }

    @Data
    static class LocationRequest {
        private double longitude;
        private double latitude;
    }
}