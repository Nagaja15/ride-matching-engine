package com.ridematching.engine.websocket;

import com.ridematching.engine.entity.Ride;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendRideOffer(Long driverId, Ride trip) {
        String destination = "/topic/driver/" + driverId;
        Map<String, Object> offer = Map.of(
                "type", "RIDE_OFFER",
                "tripId", trip.getId(),
                "riderId", trip.getRiderId(),
                "pickupCoordinates", trip.getPickupCoordinates() != null ? trip.getPickupCoordinates() : new double[]{0, 0},
                "dropoffCoordinates", trip.getDropoffCoordinates() != null ? trip.getDropoffCoordinates() : new double[]{0, 0},
                "surgeMultiplier", trip.getSurgeMultiplier() != null ? trip.getSurgeMultiplier() : "1.0",
                "timeoutSeconds", 30
        );
        messagingTemplate.convertAndSend(destination, offer);
        log.info("Sent ride offer to driver {} for trip {}", driverId, trip.getId());
    }

    public void sendTripUpdate(Long driverId, Ride trip) {
        String destination = "/topic/driver/" + driverId;
        Map<String, Object> update = Map.of(
                "type", "TRIP_UPDATE",
                "tripId", trip.getId(),
                "status", trip.getStatus(),
                "fare", trip.getFare() != null ? trip.getFare() : "pending"
        );
        messagingTemplate.convertAndSend(destination, update);
    }
}