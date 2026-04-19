package com.ridematching.engine.websocket;

import com.ridematching.engine.entity.Ride;
import com.ridematching.engine.entity.User;
import com.ridematching.engine.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    public void notifyRider(Long riderId, Ride trip) {
        String destination = "/topic/rider/" + riderId;
        messagingTemplate.convertAndSend(destination, buildPayload(trip));
        log.info("Notified rider {} — trip {} status: {}", riderId, trip.getId(), trip.getStatus());
    }

    public void notifyDriver(Long driverId, Ride trip) {
        String destination = "/topic/driver/" + driverId;
        messagingTemplate.convertAndSend(destination, buildPayload(trip));
        log.info("Notified driver {} — trip {} status: {}", driverId, trip.getId(), trip.getStatus());
    }

    private Map<String, Object> buildPayload(Ride trip) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tripId", trip.getId());
        payload.put("status", trip.getStatus());
        payload.put("driverId", trip.getDriverId());
        payload.put("riderId", trip.getRiderId());
        payload.put("fare", trip.getFare() != null ? trip.getFare() : "pending");
        payload.put("distanceKm", trip.getDistanceKm() != null ? trip.getDistanceKm() : "pending");
        payload.put("paymentStatus", trip.getPaymentStatus() != null ? trip.getPaymentStatus() : "PENDING");
        payload.put("surgeMultiplier", trip.getSurgeMultiplier() != null ? trip.getSurgeMultiplier() : "1.0");

        // Add driver info for rider notifications
        if (trip.getDriverId() != null) {
            Optional<User> driver = userRepository.findById(trip.getDriverId());
            driver.ifPresent(d -> {
                payload.put("driverName", d.getName() != null ? d.getName() : "Driver");
                payload.put("driverPhone", d.getPhone() != null ? d.getPhone() : "N/A");
                payload.put("driverVehicle", d.getVehicleType() != null ? d.getVehicleType() : "Car");
                payload.put("driverRating", d.getRating() != null ? d.getRating() : 5.0);
            });
        }

        return payload;
    }
}