package lab.stock;

import static org.assertj.core.api.Assertions.assertThat;

import lab.support.ConcurrencyResult;
import lab.support.ConcurrencyTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AtomicUpdateStockServiceTest {

    @Autowired StockRepository repo;
    @Autowired AtomicUpdateStockService service;

    @BeforeEach
    void reset() {
        repo.deleteAll();
    }

    @Test
    void 단일_UPDATE_100명이_1개씩_차감하면_최종_재고_0() throws Exception {
        Long id = repo.save(new StockModel(100L)).getId();

        ConcurrencyResult result = ConcurrencyTestSupport.runConcurrently(
            100, () -> service.decrease(id, 1L));

        assertThat(result.success()).isEqualTo(100);
        assertThat(result.fail()).isZero();
        assertThat(repo.findById(id).orElseThrow().getStock()).isZero();
    }

    @Test
    void 단일_UPDATE_재고가_부족하면_초과분은_모두_실패() throws Exception {
        Long id = repo.save(new StockModel(50L)).getId();

        ConcurrencyResult result = ConcurrencyTestSupport.runConcurrently(
            100, () -> service.decrease(id, 1L));

        assertThat(result.success()).isEqualTo(50);
        assertThat(result.fail()).isEqualTo(50);
        assertThat(repo.findById(id).orElseThrow().getStock()).isZero();
    }
}
