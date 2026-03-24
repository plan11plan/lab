package lab.learn.kafka.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    private String id;

    private String name;

    private long price;

    @Version
    private int version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static Product create(String name, long price) {
        Product product = new Product();
        product.id = UUID.randomUUID().toString();
        product.name = name;
        product.price = price;
        product.createdAt = LocalDateTime.now();
        product.updatedAt = product.createdAt;
        return product;
    }

    public long changePrice(long newPrice) {
        long oldPrice = this.price;
        this.price = newPrice;
        this.updatedAt = LocalDateTime.now();
        return oldPrice;
    }
}
