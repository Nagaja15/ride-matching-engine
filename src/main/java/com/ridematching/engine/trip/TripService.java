package com.ridematching.engine.trip;

import com.ridematching.engine.entity.Ride;
import com.ridematching.engine.entity.User;
import com.ridematching.engine.event.RideEvent;
import com.ridematching.engine.event.RideEventProducer;
import com.ridematching.engine.fare.FareService;
import com.ridematching.engine.location.LocationService;
import com.ridematching.engine.repository.UserRepository;
import com.ridematching.engine.surge.SurgeService;
import com.ridematching.engine.websocket.DriverNotificationService;
import com.ridematching.engine.websocket.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final LocationService locationService;
    private final RideEventProducer rideEventProducer;
    private final FareService fareService;
    private final NotificationService notificationService;
    private final SurgeService surgeService;
    private final DriverNotificationService driverNotificationService;

    private static final GeometryFactory geometryFactory =
            new GeometryFactory(new PrecisionModel(), 4326);

    public Ride requestRide(String riderEmail, TripRequest request) {

        User rider = userRepository.findByEmail(riderEmail)
                .orElseThrow(() -> new IllegalArgumentException("Rider not found"));

        // ── Expanding radius retry ────────────────────────────────────────────
        List<String> nearbyDrivers = List.of();
        double[] radii = {2.0, 5.0, 10.0, 20.0};
        double usedRadius = 2.0;

        for (double radius : radii) {
            nearbyDrivers = locationService.findNearbyDrivers(
                    request.getPickupLongitude(),
                    request.getPickupLatitude(),
                    radius
            );
            if (!nearbyDrivers.isEmpty()) {
                usedRadius = radius;
                break;
            }
            log.info("No drivers within {}km, expanding radius...", radius);
        }

        if (nearbyDrivers.isEmpty()) {
            throw new IllegalStateException("No drivers available nearby. Please try again.");
        }

        log.info("Found {} driver(s) within {}km for {}", nearbyDrivers.size(), usedRadius, riderEmail);

        // ── Vehicle type filter ───────────────────────────────────────────────
        String vehicleType = request.getVehicleType();
        List<String> filteredDrivers = nearbyDrivers;

        if (vehicleType != null && !vehicleType.isBlank() && !vehicleType.equals("Any")) {
            filteredDrivers = nearbyDrivers.stream()
                .filter(email -> userRepository.findByEmail(email)
                    .map(u -> vehicleType.equalsIgnoreCase(u.getVehicleType()))
                    .orElse(false))
                .collect(Collectors.toList());

            if (filteredDrivers.isEmpty()) {
                log.info("No {} drivers found, using any available driver", vehicleType);
                filteredDrivers = nearbyDrivers; // fallback
            }
        }

        String driverEmail = filteredDrivers.get(0);
        User driver = userRepository.findByEmail(driverEmail)
                .orElseThrow(() -> new IllegalArgumentException("Driver not found"));

        Point pickup = geometryFactory.createPoint(
                new Coordinate(request.getPickupLongitude(), request.getPickupLatitude()));
        Point dropoff = geometryFactory.createPoint(
                new Coordinate(request.getDropoffLongitude(), request.getDropoffLatitude()));

        BigDecimal surgeMultiplier = surgeService.calculateSurgeMultiplier();

        Ride trip = new Ride();
        trip.setRiderId(rider.getId());
        trip.setDriverId(driver.getId());
        trip.setPickup(pickup);
        trip.setDropoff(dropoff);
        trip.setStatus("REQUESTED");
        trip.setSurgeMultiplier(surgeMultiplier);
        trip.setCreatedAt(LocalDateTime.now());
        trip.setUpdatedAt(LocalDateTime.now());

        Ride savedTrip = tripRepository.save(trip);

        driverNotificationService.sendRideOffer(driver.getId(), savedTrip);
        publishEvent(savedTrip, request.getPickupLongitude(), request.getPickupLatitude(),
                request.getDropoffLongitude(), request.getDropoffLatitude());

        return savedTrip;
    }

    public Ride updateStatus(Long tripId, String driverEmail, String newStatus) {

        Ride trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found"));

        User driver = userRepository.findByEmail(driverEmail)
                .orElseThrow(() -> new IllegalArgumentException("Driver not found"));

        if (!trip.getDriverId().equals(driver.getId())) {
            throw new IllegalStateException("You are not assigned to this trip");
        }

        validateStatusTransition(trip.getStatus(), newStatus);

        if ("ACCEPTED".equals(newStatus)) {
            userRepository.updateDriverAvailability(driver.getId(), false);
        }
        if ("COMPLETED".equals(newStatus) || "CANCELLED".equals(newStatus)) {
            userRepository.updateDriverAvailability(driver.getId(), true);
        }

        trip.setStatus(newStatus);
        trip.setUpdatedAt(LocalDateTime.now());

        Ride updated = tripRepository.save(trip);

        if ("COMPLETED".equals(newStatus)) {
            updated = fareService.calculateAndSaveFare(updated);
        }

        notificationService.notifyRider(updated.getRiderId(), updated);
        driverNotificationService.sendTripUpdate(updated.getDriverId(), updated);

        RideEvent event = new RideEvent(
                updated.getId(), updated.getRiderId(), updated.getDriverId(), newStatus,
                updated.getPickupCoordinates() != null ? updated.getPickupCoordinates()[0] : 0,
                updated.getPickupCoordinates() != null ? updated.getPickupCoordinates()[1] : 0,
                updated.getDropoffCoordinates() != null ? updated.getDropoffCoordinates()[0] : 0,
                updated.getDropoffCoordinates() != null ? updated.getDropoffCoordinates()[1] : 0,
                LocalDateTime.now().toString()
        );
        rideEventProducer.publishRideRequested(event);

        if ("COMPLETED".equals(newStatus) || "CANCELLED".equals(newStatus)) {
            locationService.removeDriver(driverEmail);
        }

        log.info("Trip {} status: {} → {}", tripId, trip.getStatus(), newStatus);
        return updated;
    }

    private void validateStatusTransition(String current, String next) {
        boolean valid = switch (current) {
            case "REQUESTED"   -> "ACCEPTED".equals(next) || "CANCELLED".equals(next);
            case "ACCEPTED"    -> "IN_PROGRESS".equals(next) || "CANCELLED".equals(next);
            case "IN_PROGRESS" -> "COMPLETED".equals(next);
            default -> false;
        };
        if (!valid) throw new IllegalStateException(
                "Invalid status transition: " + current + " → " + next);
    }

    private void publishEvent(Ride trip, double pLon, double pLat, double dLon, double dLat) {
        RideEvent event = new RideEvent(
                trip.getId(), trip.getRiderId(), trip.getDriverId(), trip.getStatus(),
                pLon, pLat, dLon, dLat, LocalDateTime.now().toString());
        rideEventProducer.publishRideRequested(event);
    }

    public Ride getTrip(Long tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found"));
    }

    public List<Ride> getRiderTrips(String riderEmail) {
        User rider = userRepository.findByEmail(riderEmail)
                .orElseThrow(() -> new IllegalArgumentException("Rider not found"));
        return tripRepository.findByRiderIdOrderByCreatedAtDesc(rider.getId());
    }
}