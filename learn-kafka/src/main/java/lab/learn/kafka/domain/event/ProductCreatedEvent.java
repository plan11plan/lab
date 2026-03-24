package lab.learn.kafka.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreatedEvent implements DomainEvent {

    private String eventId;
    private String productId;
    private String name;
    private long price;
    private int version;
    private LocalDateTime occurredAt;

    public static ProductCreatedEvent of(String productId, String name, long price) {
        return new ProductCreatedEvent(
                UUID.randomUUID().toString(),
                productId,
                name,
                price,
                1,
                LocalDateTime.now()
        );
    }

    @Override
    public String getEventType() {
        return "PRODUCT_CREATED";
    }

    @Override
    public String getAggregateId() {
        return productId;
    }
}
