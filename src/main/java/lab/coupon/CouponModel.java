package lab.coupon;

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
@Table(name = "coupons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "remaining", nullable = false)
    private Long remaining;

    @Version
    private Long version;

    public CouponModel(Long remaining) {
        if (remaining == null || remaining < 0L) {
            throw new IllegalArgumentException("remaining must be >= 0");
        }
        this.remaining = remaining;
    }

    public void issueOne() {
        if (this.remaining <= 0L) {
            throw new IllegalStateException("coupon sold out");
        }
        this.remaining -= 1L;
    }
}
