package lab.like;

import static org.assertj.core.api.Assertions.assertThat;

import lab.support.ConcurrencyResult;
import lab.support.ConcurrencyTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LikeCountServiceConcurrencyTest {

    @Autowired LikeCountRepository repo;
    @Autowired PessimisticLikeCountService pessimistic;
    @Autowired OptimisticLikeCountService optimistic;
    @Autowired AtomicIncrementLikeCountService atomic;

    @BeforeEach
    void reset() {
        repo.deleteAll();
    }

    @Test
    void 비관락_100명이_좋아요하면_count_100() throws Exception {
        Long productId = repo.save(new LikeCountModel(1L)).getProductId();

        ConcurrencyResult result = ConcurrencyTestSupport.runConcurrently(
            100, () -> pessimistic.increment(productId));

        assertThat(result.success()).isEqualTo(100);
        assertThat(repo.findByProductId(productId).orElseThrow().getCount()).isEqualTo(100L);
    }

    @Test
    void 낙관락_재시도_없으면_일부_실패한다() throws Exception {
        Long productId = repo.save(new LikeCountModel(2L)).getProductId();

        ConcurrencyResult result = ConcurrencyTestSupport.runConcurrently(
            100, () -> optimistic.increment(productId));

        assertThat(result.fail()).isPositive();
        assertThat(repo.findByProductId(productId).orElseThrow().getCount())
            .isEqualTo((long) result.success());
    }

    @Test
    void 단일_UPDATE_100명이_좋아요하면_count_100() throws Exception {
        Long productId = repo.save(new LikeCountModel(3L)).getProductId();

        ConcurrencyResult result = ConcurrencyTestSupport.runConcurrently(
            100, () -> atomic.increment(productId));

        assertThat(result.success()).isEqualTo(100);
        assertThat(repo.findByProductId(productId).orElseThrow().getCount()).isEqualTo(100L);
    }
}
