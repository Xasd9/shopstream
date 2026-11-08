package com.example.shopstream.outbox;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepository.findTop100ByProcessedAtIsNullOrderByCreatedAtAsc();

        if (events.isEmpty()) {
            return;
        }

        System.out.println("📤 Найдено " + events.size() + " событий для отправки в Kafka...");

        for (OutboxEvent event : events) {
            kafkaTemplate.send(event.getEventType(), event.getPayload());

            event.setProcessedAt(LocalDateTime.now());
            outboxRepository.save(event);
        }

    }
}
