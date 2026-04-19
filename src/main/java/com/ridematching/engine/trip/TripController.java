package com.ridematching.engine.trip;

import com.ridematching.engine.entity.Ride;
import com.ridematching.engine.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // In-memory chat store per trip
    private static final ConcurrentHashMap<Long, List<Map<String,Object>>> tripChats
            = new ConcurrentHashMap<>();

    @PostMapping("/api/rider/request")
    public ResponseEntity<Ride> requestRide(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody TripRequest request) {
        Ride trip = tripService.requestRide(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(trip);
    }

    @GetMapping("/api/rider/trips")
    public ResponseEntity<List<Ride>> getMyTrips(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tripService.getRiderTrips(userDetails.getUsername()));
    }

    @GetMapping("/api/rider/profile")
    public ResponseEntity<Map<String, Object>> getRiderProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        var user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "name", user.getName() != null ? user.getName() : "",
                "phone", user.getPhone() != null ? user.getPhone() : "",
                "rating", user.getRating() != null ? user.getRating() : 5.0
        ));
    }

    @GetMapping("/api/trips/{tripId}")
    public ResponseEntity<Ride> getTrip(@PathVariable Long tripId) {
        return ResponseEntity.ok(tripService.getTrip(tripId));
    }

    @GetMapping("/api/trips/{tripId}/driver-info")
    public ResponseEntity<Map<String, Object>> getDriverInfo(@PathVariable Long tripId) {
        Ride trip = tripService.getTrip(tripId);
        if (trip.getDriverId() == null) return ResponseEntity.ok(Map.of());
        var driver = userRepository.findById(trip.getDriverId())
                .orElseThrow(() -> new IllegalArgumentException("Driver not found"));
        return ResponseEntity.ok(Map.of(
                "name",        driver.getName() != null ? driver.getName() : "Driver",
                "phone",       driver.getPhone() != null ? driver.getPhone() : "Not available",
                "vehicleType", driver.getVehicleType() != null ? driver.getVehicleType() : "Car",
                "rating",      driver.getRating() != null ? driver.getRating() : 5.0,
                "email",       driver.getEmail()
        ));
    }

    // ── Chat endpoints ────────────────────────────────────────────────────────
    @PostMapping("/api/trips/{tripId}/message")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long tripId,
            @RequestBody Map<String, String> body) {

        var user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Map<String, Object> msg = new HashMap<>();
        msg.put("sender", user.getName() != null ? user.getName() : user.getEmail());
        msg.put("role",   user.getRole().name());
        msg.put("text",   body.getOrDefault("text", ""));
        msg.put("time",   LocalDateTime.now().toString());
        msg.put("type",   "CHAT_MESSAGE");
        msg.put("tripId", tripId);

        tripChats.computeIfAbsent(tripId, k -> new ArrayList<>()).add(msg);

        // Push to both rider and driver via WebSocket
        Ride trip = tripService.getTrip(tripId);
        messagingTemplate.convertAndSend("/topic/rider/"  + trip.getRiderId(),  msg);
        messagingTemplate.convertAndSend("/topic/driver/" + trip.getDriverId(), msg);

        return ResponseEntity.ok(msg);
    }

    @GetMapping("/api/trips/{tripId}/messages")
    public ResponseEntity<List<Map<String,Object>>> getMessages(@PathVariable Long tripId) {
        return ResponseEntity.ok(tripChats.getOrDefault(tripId, List.of()));
    }

    // ── Driver endpoints ──────────────────────────────────────────────────────
    @GetMapping("/api/driver/profile")
    public ResponseEntity<Map<String, Object>> getDriverProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        var user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Driver not found"));
        return ResponseEntity.ok(Map.of(
                "id",          user.getId(),
                "email",       user.getEmail(),
                "name",        user.getName() != null ? user.getName() : "",
                "vehicleType", user.getVehicleType() != null ? user.getVehicleType() : "Car",
                "phone",       user.getPhone() != null ? user.getPhone() : "",
                "rating",      user.getRating() != null ? user.getRating() : 5.0
        ));
    }

    @GetMapping("/api/driver/trips")
    public ResponseEntity<List<Ride>> getDriverTrips(
            @AuthenticationPrincipal UserDetails userDetails) {
        var user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Driver not found"));
        return ResponseEntity.ok(
                tripRepository.findByDriverIdOrderByCreatedAtDesc(user.getId()));
    }

    @PatchMapping("/api/driver/trips/{tripId}/accept")
    public ResponseEntity<Ride> acceptRide(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long tripId) {
        return ResponseEntity.ok(
                tripService.updateStatus(tripId, userDetails.getUsername(), "ACCEPTED"));
    }

    @PatchMapping("/api/driver/trips/{tripId}/start")
    public ResponseEntity<Ride> startRide(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long tripId) {
        return ResponseEntity.ok(
                tripService.updateStatus(tripId, userDetails.getUsername(), "IN_PROGRESS"));
    }

    @PatchMapping("/api/driver/trips/{tripId}/complete")
    public ResponseEntity<Ride> completeRide(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long tripId) {
        return ResponseEntity.ok(
                tripService.updateStatus(tripId, userDetails.getUsername(), "COMPLETED"));
    }

    @PatchMapping("/api/driver/trips/{tripId}/cancel")
    public ResponseEntity<Ride> cancelRide(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long tripId) {
        return ResponseEntity.ok(
                tripService.updateStatus(tripId, userDetails.getUsername(), "CANCELLED"));
    }
}