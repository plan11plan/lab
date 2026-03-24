package lab.learn.kafka.infra.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String PRODUCT_EVENTS_TOPIC = "product-events";

    @Bean
    public NewTopic productEventsTopic() {
        return TopicBuilder.name(PRODUCT_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
