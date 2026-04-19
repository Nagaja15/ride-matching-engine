package com.ridematching.engine.fare;

import com.ridematching.engine.entity.Ride;
import com.ridematching.engine.surge.SurgeService;
import com.ridematching.engine.trip.TripRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class FareService {

    private static final BigDecimal BASE_FARE = new BigDecimal("20.00");
    private static final BigDecimal PER_KM_RATE = new BigDecimal("12.00");

    private final EntityManager entityManager;
    private final TripRepository tripRepository;
    private final SurgeService surgeService;

    public Ride calculateAndSaveFare(Ride trip) {

        Double distanceMeters = (Double) entityManager.createNativeQuery(
                "SELECT ST_Distance(" +
                "ST_Transform(CAST(pickup AS geometry), 3857), " +
                "ST_Transform(CAST(dropoff AS geometry), 3857)" +
                ") FROM trips WHERE id = :tripId")
                .setParameter("tripId", trip.getId())
                .getSingleResult();

        if (distanceMeters == null) {
            log.warn("Could not calculate distance for trip {}", trip.getId());
            return trip;
        }

        BigDecimal distanceKm = BigDecimal.valueOf(distanceMeters / 1000.0)
                .setScale(2, RoundingMode.HALF_UP);

        // Use surge multiplier from SurgeService
        BigDecimal surgeMultiplier = surgeService.calculateSurgeMultiplier();

        BigDecimal fare = BASE_FARE
                .add(distanceKm.multiply(PER_KM_RATE))
                .multiply(surgeMultiplier)
                .setScale(2, RoundingMode.HALF_UP);

        log.info("Trip {} — distance: {}km, surge: {}x, fare: ₹{}",
                trip.getId(), distanceKm, surgeMultiplier, fare);

        trip.setDistanceKm(distanceKm);
        trip.setFare(fare);
        trip.setSurgeMultiplier(surgeMultiplier);

        return tripRepository.save(trip);
    }
}