package lab.coupon;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AtomicUpdateCouponIssueService {

    private final CouponRepository repo;

    @Transactional
    public void issue(Long id) {
        int updated = repo.issueOneAtomic(id);
        if (updated == 0) {
            throw new IllegalStateException("coupon sold out or not found: " + id);
        }
    }
}
