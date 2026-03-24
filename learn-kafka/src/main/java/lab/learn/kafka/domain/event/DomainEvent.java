package lab.learn.kafka.domain.event;

import java.time.LocalDateTime;

/**
 * 시스템 간 전파가 필요한 도메인 이벤트의 기본 인터페이스.
 * ApplicationEvent와 달리 Kafka를 통해 외부 시스템으로 발행된다.
 */
public interface DomainEvent {

    String getEventId();

    String getEventType();

    String getAggregateId();

    int getVersion();

    LocalDateTime getOccurredAt();
}
