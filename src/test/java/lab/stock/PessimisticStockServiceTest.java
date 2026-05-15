package lab.stock;

import static org.assertj.core.api.Assertions.assertThat;

import lab.support.ConcurrencyResult;
import lab.support.ConcurrencyTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PessimisticStockServiceTest {

    @Autowired StockRepository repo;
    @Autowired PessimisticStockService service;

    @BeforeEach
    void reset() {
        repo.deleteAll();
    }

    @Test
    void 비관락_100명이_1개씩_차감하면_최종_재고_0() throws Exception {
        Long id = repo.save(new StockModel(100L)).getId();

        ConcurrencyResult result = ConcurrencyTestSupport.runConcurrently(
            100, () -> service.decrease(id, 1L));

        assertThat(result.success()).isEqualTo(100);
        assertThat(result.fail()).isZero();
        assertThat(repo.findById(id).orElseThrow().getStock()).isZero();
    }
}
