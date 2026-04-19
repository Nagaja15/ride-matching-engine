package com.ridematching.engine.payment;

import com.ridematching.engine.entity.Ride;
import com.ridematching.engine.trip.TripRepository;
import com.ridematching.engine.websocket.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final TripRepository tripRepository;
    private final NotificationService notificationService;

    @Transactional
    public Ride processPayment(Long tripId, String paymentMethod, String riderEmail) {

        Ride trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found"));

        if (!"COMPLETED".equals(trip.getStatus())) {
            throw new IllegalStateException("Payment only allowed for COMPLETED trips");
        }

        // ── Idempotency check — if already paid, return existing state ────────
        // This makes retries safe — same result, no double charge
        if ("PAID".equals(trip.getPaymentStatus())) {
            log.info("Trip {} already paid — idempotent return", tripId);
            return trip;
        }

        // Simulate payment gateway (1 second processing)
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Mock payment always succeeds
        trip.setPaymentStatus("PAID");
        trip.setPaymentMethod(paymentMethod);
        trip.setStatus("PAID");
        trip.setUpdatedAt(LocalDateTime.now());

        Ride saved = tripRepository.save(trip);

        notificationService.notifyRider(saved.getRiderId(), saved);

        log.info("Payment processed for trip {} via {} — ₹{}", tripId, paymentMethod, saved.getFare());

        return saved;
    }
}