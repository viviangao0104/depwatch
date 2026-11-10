package com.depwatch.project.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
  * Business code only calls append(); it must run inside the caller's transaction (MANDATORY)
  * so that "business data + event" commit together or roll back together.
 */
@Service
public class OutboxService {

    private final OutboxRepository repo;
    private final ObjectMapper mapper;

    public OutboxService(OutboxRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String aggregateType, UUID aggregateId, String topic, String partitionKey, Object event) {
        try {
            String payload = mapper.writeValueAsString(event);
            repo.save(new OutboxEvent(aggregateType, aggregateId, event.getClass().getName(), topic, partitionKey, payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize outbox event", e);
        }
    }
}
