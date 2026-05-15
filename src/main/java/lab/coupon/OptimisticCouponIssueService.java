package lab.coupon;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OptimisticCouponIssueService {

    private final CouponRepository repo;

    @Transactional
    public void issue(Long id) {
        CouponModel c = repo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("coupon not found: " + id));
        c.issueOne();
    }
}
