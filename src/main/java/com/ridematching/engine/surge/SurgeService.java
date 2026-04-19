package com.ridematching.engine.surge;

import com.ridematching.engine.trip.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SurgeService {

    private final TripRepository tripRepository;

    // Returns surge multiplier based on active REQUESTED trips
    public BigDecimal calculateSurgeMultiplier() {
        List<?> requestedTrips = tripRepository.findByStatus("REQUESTED");
        int activeRequests = requestedTrips.size();

        BigDecimal multiplier;
        if (activeRequests >= 10) {
            multiplier = new BigDecimal("2.0");
        } else if (activeRequests >= 5) {
            multiplier = new BigDecimal("1.5");
        } else if (activeRequests >= 3) {
            multiplier = new BigDecimal("1.2");
        } else {
            multiplier = new BigDecimal("1.0");
        }

        log.info("Active requests: {} — surge multiplier: {}x", activeRequests, multiplier);
        return multiplier;
    }
}