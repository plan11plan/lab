package lab.learn.kafka.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductPriceChangedEvent implements DomainEvent {

    private String eventId;
    private String productId;
    private long oldPrice;
    private long newPrice;
    private int version;
    private LocalDateTime occurredAt;

    public static ProductPriceChangedEvent of(String productId, long oldPrice, long newPrice, int version) {
        return new ProductPriceChangedEvent(
                UUID.randomUUID().toString(),
                productId,
                oldPrice,
                newPrice,
                version,
                LocalDateTime.now()
        );
    }

    @Override
    public String getEventType() {
        return "PRODUCT_PRICE_CHANGED";
    }

    @Override
    public String getAggregateId() {
        return productId;
    }
}
