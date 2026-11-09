package com.depwatch.project.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 业务代码只调 append()，必须在调用方的事务内执行（MANDATORY），
 * 保证"业务数据 + 事件"要么一起提交，要么一起回滚。
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
