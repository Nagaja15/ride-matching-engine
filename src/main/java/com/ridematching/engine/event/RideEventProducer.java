package com.ridematching.engine.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RideEventProducer {

    private static final String TOPIC = "ride.requested";

    @Autowired(required = false)
    private KafkaTemplate<String, RideEvent> kafkaTemplate;

    public void publishRideRequested(RideEvent event) {
        if (kafkaTemplate == null) {
            log.info("Kafka not available — skipping event publish for trip {}", event.getTripId());
            return;
        }
        try {
            kafkaTemplate.send(TOPIC, String.valueOf(event.getTripId()), event);
            log.info("Published ride event for trip {}", event.getTripId());
        } catch (Exception e) {
            log.error("Failed to publish Kafka event: {}", e.getMessage());
        }
    }
}