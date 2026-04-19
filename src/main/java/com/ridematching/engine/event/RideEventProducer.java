package com.ridematching.engine.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RideEventProducer {

    private static final String TOPIC = "ride.requested";

    private final KafkaTemplate<String, RideEvent> kafkaTemplate;

    public void publishRideRequested(RideEvent event) {
        kafkaTemplate.send(TOPIC, String.valueOf(event.getTripId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ride event for tripId={}: {}",
                                event.getTripId(), ex.getMessage());
                    } else {
                        log.info("Published ride.requested event for tripId={} to partition={}",
                                event.getTripId(),
                                result.getRecordMetadata().partition());
                    }
                });
    }
}