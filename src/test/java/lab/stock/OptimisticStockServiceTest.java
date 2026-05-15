package lab.stock;

import static org.assertj.core.api.Assertions.assertThat;

import lab.support.ConcurrencyResult;
import lab.support.ConcurrencyTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OptimisticStockServiceTest {

    @Autowired StockRepository repo;
    @Autowired OptimisticStockService service;

    @BeforeEach
    void reset() {
        repo.deleteAll();
    }

    @Test
    void 낙관락은_재시도_없으면_일부_실패한다() throws Exception {
        Long id = repo.save(new StockModel(100L)).getId();

        ConcurrencyResult result = ConcurrencyTestSupport.runConcurrently(
            100, () -> service.decrease(id, 1L));

        // 일부 트랜잭션이 OptimisticLockException으로 롤백되어야 정상
        assertThat(result.fail()).isPositive();
        assertThat(result.success()).isLessThan(100);
        // 최종 재고 = 100 - success
        assertThat(repo.findById(id).orElseThrow().getStock())
            .isEqualTo(100L - result.success());
    }
}
