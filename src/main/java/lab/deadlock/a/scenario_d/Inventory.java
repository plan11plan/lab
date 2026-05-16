package lab.deadlock.a.scenario_d;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventory")
@Getter
@NoArgsConstructor
public class Inventory {

    @Id
    private Long productId;

    @Column(nullable = false)
    private Integer stock;

    public Inventory(Long productId, Integer stock) {
        this.productId = productId;
        this.stock = stock;
    }

    public void decrease(int amount) {
        if (stock < amount) {
            throw new IllegalStateException("insufficient stock: productId=" + productId);
        }
        this.stock = this.stock - amount;
    }

    public void restore(int amount) {
        this.stock = this.stock + amount;
    }
}
