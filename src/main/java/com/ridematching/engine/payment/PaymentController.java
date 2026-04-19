package com.ridematching.engine.payment;

import com.ridematching.engine.entity.Ride;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rider/trips")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/{tripId}/pay")
    public ResponseEntity<Ride> pay(
            @PathVariable Long tripId,
            @RequestBody PaymentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Ride paid = paymentService.processPayment(
                tripId,
                request.getPaymentMethod(),
                userDetails.getUsername()
        );
        return ResponseEntity.ok(paid);
    }

    @Data
    static class PaymentRequest {
        private String paymentMethod;
    }
}