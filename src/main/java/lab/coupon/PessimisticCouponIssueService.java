package lab.coupon;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PessimisticCouponIssueService {

    private final CouponRepository repo;

    @Transactional
    public void issue(Long id) {
        CouponModel c = repo.findByIdForUpdate(id)
            .orElseThrow(() -> new IllegalArgumentException("coupon not found: " + id));
        c.issueOne();
    }
}
