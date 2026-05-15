package lab.like;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "like_counts",
       uniqueConstraints = @UniqueConstraint(name = "uk_like_counts_product", columnNames = "product_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LikeCountModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "count", nullable = false)
    private Long count;

    @Version
    private Long version;

    public LikeCountModel(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId required");
        }
        this.productId = productId;
        this.count = 0L;
    }

    public void increment() {
        this.count++;
    }
}
