package lab.learn.kafka.infra.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer 설정.
 *
 * 핵심 설정:
 * - acks=all: 모든 ISR(In-Sync Replica)이 메시지를 수신해야 성공 응답
 * - enable.idempotence=true: 프로듀서 재시도 시 중복 메시지 방지
 * - max.in.flight.requests.per.connection=5: 멱등성과 함께 순서 보장 (Kafka 1.0+)
 *
 * Outbox 패턴에서 이미 JSON 문자열로 직렬화된 페이로드를 전송하므로
 * key/value 모두 StringSerializer를 사용한다.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // acks=all: 리더 + 모든 ISR 복제본이 메시지를 기록해야 ACK
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        // 멱등성 활성화: 동일 메시지가 재시도로 인해 중복 전송되는 것을 방지
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // 멱등성 프로듀서에서 최대 5개까지 in-flight 요청 허용 (순서 보장됨)
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // 재시도 설정
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
