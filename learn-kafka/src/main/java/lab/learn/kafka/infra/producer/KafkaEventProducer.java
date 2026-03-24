package lab.learn.kafka.infra.producer;

import lab.learn.kafka.infra.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka 이벤트 프로듀서.
 *
 * PartitionKey 기반 이벤트 순서 보장:
 * - 동일한 partitionKey(= productId)를 가진 메시지는 항상 같은 파티션에 전송
 * - 같은 파티션 내에서는 메시지 순서가 보장됨
 * - 따라서 동일 상품에 대한 이벤트 순서가 보장됨
 *
 * 예: productId="abc" → 항상 파티션 2로 전송 → 생성 → 가격변경1 → 가격변경2 순서 보장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Kafka 토픽에 메시지를 전송한다.
     *
     * @param partitionKey 파티션 키 (동일 키 → 동일 파티션 → 순서 보장)
     * @param payload      이벤트 페이로드 (JSON 문자열)
     */
    public void send(String partitionKey, String payload) {
        kafkaTemplate.send(KafkaTopicConfig.PRODUCT_EVENTS_TOPIC, partitionKey, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka 전송 실패 - key: {}, error: {}", partitionKey, ex.getMessage());
                    } else {
                        log.info("Kafka 전송 성공 - topic: {}, partition: {}, offset: {}, key: {}",
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset(),
                                partitionKey);
                    }
                });
    }
}
