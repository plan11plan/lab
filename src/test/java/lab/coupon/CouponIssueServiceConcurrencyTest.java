package lab.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import lab.support.ConcurrencyResult;
import lab.support.ConcurrencyTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CouponIssueServiceConcurrencyTest {

    @Autowired CouponRepository repo;
    @Autowired PessimisticCouponIssueService pessimistic;
    @Autowired OptimisticCouponIssueService optimistic;
    @Autowired AtomicUpdateCouponIssueService atomic;

    @BeforeEach
    void reset() {
        repo.deleteAll();
    }

    @Test
    void 비관락_remaining_30에_100명_발급_요청_시_30성공_70실패() throws Exception {
        Long id = repo.save(new CouponModel(30L)).getId();

        ConcurrencyResult result = ConcurrencyTestSupport.runConcurrently(
            100, () -> pessimistic.issue(id));

        assertThat(result.success()).isEqualTo(30);
        assertThat(result.fail()).isEqualTo(70);
        assertThat(repo.findById(id).orElseThrow().getRemaining()).isZero();
    }

    @Test
    void 낙관락_재시도_없으면_일부는_OptimisticLockException으로_실패() throws Exception {
        Long id = repo.save(new CouponModel(30L)).getId();

        ConcurrencyResult result = ConcurrencyTestSupport.runConcurrently(
            100, () -> optimistic.issue(id));

        assertThat(result.success()).isLessThanOrEqualTo(30);
        assertThat(repo.findById(id).orElseThrow().getRemaining())
            .isEqualTo(30L - result.success());
    }

    @Test
    void 단일_UPDATE_remaining_30에_100명_발급_요청_시_30성공_70실패() throws Exception {
        Long id = repo.save(new CouponModel(30L)).getId();

        ConcurrencyResult result = ConcurrencyTestSupport.runConcurrently(
            100, () -> atomic.issue(id));

        assertThat(result.success()).isEqualTo(30);
        assertThat(result.fail()).isEqualTo(70);
        assertThat(repo.findById(id).orElseThrow().getRemaining()).isZero();
    }
}
