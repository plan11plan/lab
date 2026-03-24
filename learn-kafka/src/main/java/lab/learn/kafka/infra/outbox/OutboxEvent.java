package lab.learn.kafka.infra.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Transactional Outbox 패턴의 핵심 엔티티.
 *
 * 도메인 로직과 같은 트랜잭션 내에서 이 테이블에 이벤트를 저장하고,
 * 별도 스케줄러(OutboxEventRelay)가 미발행 이벤트를 Kafka로 전송한다.
 *
 * 이 패턴의 장점:
 * - 도메인 상태 변경과 이벤트 발행의 원자성 보장
 * - Kafka 장애 시에도 이벤트 유실 없음
 * - 최종 일관성(Eventual Consistency) 달성
 */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    private String eventId;

    private String aggregateType;

    private String aggregateId;

    private String eventType;

    @Column(columnDefinition = "CLOB")
    private String payload;

    /** Kafka 파티션 키 — 동일 키는 동일 파티션으로 전송되어 순서 보장 */
    private String partitionKey;

    private boolean published;

    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    public static OutboxEvent create(String eventId, String aggregateType, String aggregateId,
                                     String eventType, String payload, String partitionKey) {
        OutboxEvent event = new OutboxEvent();
        event.eventId = eventId;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.payload = payload;
        event.partitionKey = partitionKey;
        event.published = false;
        event.createdAt = LocalDateTime.now();
        return event;
    }

    public void markPublished() {
        this.published = true;
        this.publishedAt = LocalDateTime.now();
    }
}
