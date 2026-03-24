package lab.learn.kafka.infra.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lab.learn.kafka.domain.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 도메인 이벤트를 Outbox 테이블에 저장하는 퍼블리셔.
 *
 * 도메인 로직과 동일한 트랜잭션 내에서 호출되어야 한다.
 * 실제 Kafka 발행은 OutboxEventRelay가 별도로 수행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * 도메인 이벤트를 Outbox 테이블에 저장한다.
     * 호출하는 서비스의 @Transactional 컨텍스트 내에서 실행된다.
     */
    public void save(DomainEvent event) {
        String payload = serialize(event);

        OutboxEvent outboxEvent = OutboxEvent.create(
                event.getEventId(),
                "Product",
                event.getAggregateId(),
                event.getEventType(),
                payload,
                event.getAggregateId()  // partitionKey = aggregateId → 동일 상품 이벤트는 같은 파티션
        );

        outboxRepository.save(outboxEvent);
        log.info("Outbox 이벤트 저장 완료 - eventId: {}, type: {}", event.getEventId(), event.getEventType());
    }

    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("이벤트 직렬화 실패", e);
        }
    }
}
