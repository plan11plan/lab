package lab.learn.kafka.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 상품 메트릭스 집계 테이블.
 * Consumer가 Kafka 이벤트를 소비하여 upsert 한다.
 */
@Entity
@Table(name = "product_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetrics {

    @Id
    private String productId;

    private String productName;

    private long currentPrice;

    private int totalPriceChanges;

    private int lastProcessedVersion;

    private LocalDateTime lastUpdatedAt;

    public static ProductMetrics initialize(String productId, String productName, long price, int version) {
        ProductMetrics metrics = new ProductMetrics();
        metrics.productId = productId;
        metrics.productName = productName;
        metrics.currentPrice = price;
        metrics.totalPriceChanges = 0;
        metrics.lastProcessedVersion = version;
        metrics.lastUpdatedAt = LocalDateTime.now();
        return metrics;
    }

    public boolean isNewerVersion(int eventVersion) {
        return eventVersion > this.lastProcessedVersion;
    }

    public void applyPriceChange(long newPrice, int eventVersion) {
        this.currentPrice = newPrice;
        this.totalPriceChanges++;
        this.lastProcessedVersion = eventVersion;
        this.lastUpdatedAt = LocalDateTime.now();
    }
}
