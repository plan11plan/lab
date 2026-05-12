package lab.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long quantity;

    public Stock(Long quantity) {
        this.quantity = quantity;
    }

    public void decrease(long amount) {
        if (this.quantity < amount) {
            throw new IllegalStateException("재고 부족: 현재=" + this.quantity + ", 차감=" + amount);
        }
        this.quantity -= amount;
    }
}
