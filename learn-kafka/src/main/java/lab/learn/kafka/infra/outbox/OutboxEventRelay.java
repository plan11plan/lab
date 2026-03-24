package lab.learn.kafka.infra.outbox;

import lab.learn.kafka.infra.producer.KafkaEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox 테이블의 미발행 이벤트를 Kafka로 릴레이하는 스케줄러.
 *
 * 동작 방식:
 * 1. 주기적으로 outbox_event 테이블에서 published=false인 이벤트를 조회
 * 2. Kafka로 전송 (PartitionKey 기반 순서 보장)
 * 3. 전송 성공 시 published=true로 마킹
 *
 * 장애 시:
 * - Kafka 전송 실패 → 다음 스케줄에서 재시도 (at-least-once)
 * - Consumer 측에서 멱등 처리로 중복 보장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaEventProducer kafkaEventProducer;

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void relay() {
        List<OutboxEvent> unpublished = outboxRepository.findByPublishedFalseOrderByCreatedAtAsc();

        if (unpublished.isEmpty()) {
            return;
        }

        log.info("미발행 Outbox 이벤트 {} 건 발견", unpublished.size());

        for (OutboxEvent event : unpublished) {
            try {
                kafkaEventProducer.send(event.getPartitionKey(), event.getPayload());
                event.markPublished();
                log.info("Outbox → Kafka 전송 완료 - eventId: {}, partitionKey: {}",
                        event.getEventId(), event.getPartitionKey());
            } catch (Exception e) {
                log.error("Outbox → Kafka 전송 실패 - eventId: {}, 다음 스케줄에서 재시도",
                        event.getEventId(), e);
                break; // 순서 보장을 위해 실패 시 중단
            }
        }
    }
}
