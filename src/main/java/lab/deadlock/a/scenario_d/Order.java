package lab.deadlock.a.scenario_d;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor
public class Order {

    @Id
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 32)
    private String status;

    public Order(Long id, Long productId, String status) {
        this.id = id;
        this.productId = productId;
        this.status = status;
    }

    public void markPaid() {
        this.status = "PAID";
    }

    public void markRefunded() {
        this.status = "REFUNDED";
    }
}
