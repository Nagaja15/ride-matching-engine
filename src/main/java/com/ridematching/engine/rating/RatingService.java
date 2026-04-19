package com.ridematching.engine.rating;

import com.ridematching.engine.entity.Ride;
import com.ridematching.engine.entity.User;
import com.ridematching.engine.repository.UserRepository;
import com.ridematching.engine.trip.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RatingService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    public Ride rateDriver(Long tripId, Integer rating, String riderEmail) {

        Ride trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found"));

        if (!"PAID".equals(trip.getStatus()) && !"COMPLETED".equals(trip.getStatus())) {
            throw new IllegalStateException("Can only rate after trip is completed or paid");
        }

        if (trip.getRatingByRider() != null) {
            throw new IllegalStateException("Trip already rated");
        }

        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        trip.setRatingByRider(rating);
        Ride saved = tripRepository.save(trip);

        // Recalculate driver's average rating
        User driver = userRepository.findById(trip.getDriverId())
                .orElseThrow(() -> new IllegalArgumentException("Driver not found"));

        List<Ride> ratedTrips = tripRepository.findByDriverIdOrderByCreatedAtDesc(trip.getDriverId())
                .stream()
                .filter(t -> t.getRatingByRider() != null)
                .toList();

        double avgRating = ratedTrips.stream()
                .mapToInt(Ride::getRatingByRider)
                .average()
                .orElse(rating);

        driver.setRating(BigDecimal.valueOf(avgRating).setScale(2, RoundingMode.HALF_UP));
        userRepository.save(driver);

        log.info("Driver {} rated {} stars — new avg: {}", driver.getEmail(), rating, driver.getRating());

        return saved;
    }
}