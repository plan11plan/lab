package lab.deadlock.b.coupon;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    /**
     * 안티패턴 — UPSERT.
     */
    @Transactional
    public void claim(String code, Long userId) {
        couponRepository.claim(code, userId);
    }

    /**
     * 처방 — 명시적 X-lock 으로 조회한 뒤 미할당 상태일 때만 갱신.
     */
    @Transactional
    public boolean claimSafe(String code, Long userId) {
        CouponCode coupon = couponRepository.findByCodeForUpdate(code)
            .orElseThrow(() -> new IllegalArgumentException("unknown code: " + code));
        if (coupon.getClaimedBy() != null) {
            return false;
        }
        coupon.assign(userId);
        return true;
    }
}
