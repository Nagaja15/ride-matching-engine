package com.ridematching.engine.rating;

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
public class RatingController {

    private final RatingService ratingService;

    @PostMapping("/{tripId}/rate")
    public ResponseEntity<Ride> rate(
            @PathVariable Long tripId,
            @RequestBody RatingRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Ride rated = ratingService.rateDriver(
                tripId,
                request.getRating(),
                userDetails.getUsername()
        );
        return ResponseEntity.ok(rated);
    }

    @Data
    static class RatingRequest {
        private Integer rating;
    }
}