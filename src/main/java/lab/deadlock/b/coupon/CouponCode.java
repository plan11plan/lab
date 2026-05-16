package lab.deadlock.b.coupon;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "b_coupon_code")
@Getter
@NoArgsConstructor
public class CouponCode {

    @Id
    @Column(length = 64)
    private String code;

    @Column(name = "claimed_by")
    private Long claimedBy;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    public CouponCode(String code) {
        this.code = code;
    }

    public void assign(Long userId) {
        this.claimedBy = userId;
        this.claimedAt = LocalDateTime.now();
    }
}
