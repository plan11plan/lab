package lab.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "stocks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock", nullable = false)
    private Long stock;

    @Version
    private Long version;

    public StockModel(Long stock) {
        if (stock == null || stock < 0L) {
            throw new IllegalArgumentException("stock must be >= 0");
        }
        this.stock = stock;
    }

    public void decrease(long quantity) {
        if (quantity < 1L) {
            throw new IllegalArgumentException("quantity must be >= 1");
        }
        if (this.stock < quantity) {
            throw new IllegalStateException("stock not enough");
        }
        this.stock -= quantity;
    }
}
