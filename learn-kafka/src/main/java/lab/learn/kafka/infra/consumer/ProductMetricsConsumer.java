package lab.learn.kafka.infra.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lab.learn.kafka.domain.model.ProductMetrics;
import lab.learn.kafka.domain.repository.ProductMetricsRepository;
import lab.learn.kafka.infra.config.KafkaTopicConfig;
import lab.learn.kafka.infra.idempotency.IdempotencyChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 상품 이벤트 Kafka Consumer — Metrics 집계 처리.
 *
 * 핵심 구현 사항:
 * 1. Manual Ack: 처리 완료 후에만 오프셋 커밋 (메시지 유실 방지)
 * 2. 멱등 처리: event_handled 테이블로 중복 이벤트 무시
 * 3. 최신 이벤트만 반영: version 기준으로 오래된 이벤트 스킵
 * 4. product_metrics upsert: 메트릭스 테이블에 집계 결과 반영
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductMetricsConsumer {

    private static final String HANDLER_NAME = "ProductMetricsConsumer";

    private final ProductMetricsRepository metricsRepository;
    private final IdempotencyChecker idempotencyChecker;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @KafkaListener(topics = KafkaTopicConfig.PRODUCT_EVENTS_TOPIC)
    @Transactional
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        log.info("Kafka 메시지 수신 - partition: {}, offset: {}, key: {}",
                record.partition(), record.offset(), record.key());

        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String eventId = payload.get("eventId").asText();
            String eventType = payload.get("eventType").asText();

            // 1. 멱등 처리: 이미 처리된 이벤트인지 확인
            if (idempotencyChecker.isAlreadyHandled(eventId)) {
                acknowledgment.acknowledge();
                return;
            }

            // 2. 이벤트 타입별 처리
            switch (eventType) {
                case "PRODUCT_CREATED" -> handleProductCreated(payload);
                case "PRODUCT_PRICE_CHANGED" -> handleProductPriceChanged(payload);
                default -> log.warn("알 수 없는 이벤트 타입: {}", eventType);
            }

            // 3. 멱등 처리 완료 기록
            idempotencyChecker.markAsHandled(eventId, HANDLER_NAME);

            // 4. Manual Ack — 모든 처리가 성공한 후에만 오프셋 커밋
            acknowledgment.acknowledge();
            log.info("이벤트 처리 + ACK 완료 - eventId: {}, type: {}", eventId, eventType);

        } catch (Exception e) {
            log.error("이벤트 처리 실패 - offset: {}, ACK 미전송 (재처리 대상)",
                    record.offset(), e);
            // ACK를 보내지 않으면 Kafka가 동일 메시지를 다시 전달 (at-least-once)
        }
    }

    private void handleProductCreated(JsonNode payload) {
        String productId = payload.get("productId").asText();
        String name = payload.get("name").asText();
        long price = payload.get("price").asLong();
        int version = payload.get("version").asInt();

        // Upsert: 이미 존재하면 무시 (멱등)
        Optional<ProductMetrics> existing = metricsRepository.findById(productId);
        if (existing.isPresent()) {
            log.info("이미 존재하는 상품 메트릭스 - productId: {} (스킵)", productId);
            return;
        }

        ProductMetrics metrics = ProductMetrics.initialize(productId, name, price, version);
        metricsRepository.save(metrics);
        log.info("상품 메트릭스 초기화 - productId: {}, name: {}, price: {}", productId, name, price);
    }

    private void handleProductPriceChanged(JsonNode payload) {
        String productId = payload.get("productId").asText();
        long newPrice = payload.get("newPrice").asLong();
        int version = payload.get("version").asInt();

        Optional<ProductMetrics> optMetrics = metricsRepository.findById(productId);
        if (optMetrics.isEmpty()) {
            log.warn("메트릭스가 존재하지 않는 상품 - productId: {} (스킵)", productId);
            return;
        }

        ProductMetrics metrics = optMetrics.get();

        // version 기준 최신 이벤트만 반영: 오래된 이벤트는 스킵
        if (!metrics.isNewerVersion(version)) {
            log.info("오래된 이벤트 스킵 - productId: {}, eventVersion: {}, lastProcessed: {}",
                    productId, version, metrics.getLastProcessedVersion());
            return;
        }

        metrics.applyPriceChange(newPrice, version);
        metricsRepository.save(metrics);
        log.info("메트릭스 업데이트 - productId: {}, newPrice: {}, totalChanges: {}, version: {}",
                productId, newPrice, metrics.getTotalPriceChanges(), version);
    }
}
