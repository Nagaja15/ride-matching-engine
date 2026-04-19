package com.ridematching.engine.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trips")
@Data
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rider_id")
    private Long riderId;

    @Column(name = "driver_id")
    private Long driverId;

    @JsonIgnore
    @Column(columnDefinition = "geometry(Point,4326)")
    private Point pickup;

    @JsonIgnore
    @Column(columnDefinition = "geometry(Point,4326)")
    private Point dropoff;

    @Transient
    public double[] getPickupCoordinates() {
        if (pickup == null) return null;
        return new double[]{pickup.getX(), pickup.getY()};
    }

    @Transient
    public double[] getDropoffCoordinates() {
        if (dropoff == null) return null;
        return new double[]{dropoff.getX(), dropoff.getY()};
    }

    private String status;

    @Column(precision = 10, scale = 2)
    private BigDecimal fare;

    @Column(name = "surge_multiplier", precision = 4, scale = 2)
    private BigDecimal surgeMultiplier;

    @Column(name = "distance_km", precision = 8, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "duration_min")
    private Integer durationMin;

    @Column(name = "payment_status")
    private String paymentStatus = "PENDING";

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "rating_by_rider")
    private Integer ratingByRider;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}