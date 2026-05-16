package lab.deadlock.b.product;

import lab.deadlock.b.support.ConcurrencyTestSupport;
import lab.deadlock.b.support.DeadlockStatusCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = lab.Application.class)
@ActiveProfiles("test")
class ProductDeadlockTest {

    @Autowired ProductRepository productRepository;
    @Autowired OrderService orderService;
    @Autowired PlatformTransactionManager txManager;
    @Autowired JdbcTemplate jdbc;

    TransactionTemplate tx;
    Long productId;

    @BeforeEach
    void setup() {
        tx = new TransactionTemplate(txManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        jdbc.execute("SET GLOBAL innodb_print_all_deadlocks = ON");
        Product saved = productRepository.save(new Product("popular", 100));
        productId = saved.getId();
    }

    @AfterEach
    void cleanup() {
        productRepository.deleteAll();
    }

    @Test
    void 안티패턴_PESSIMISTIC_READ_후_UPDATE_는_데드락을_일으킨다() throws Exception {
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        var failures = new ConcurrentLinkedQueue<Throwable>();

        Runnable txn = () -> {
            try {
                tx.executeWithoutResult(status -> {
                    Product p = productRepository.findByIdForShare(productId).orElseThrow();
                    awaitBarrier(barrier);                 // 양쪽 모두 S-lock 잡은 시점 동기화
                    p.decreaseStock(1);                    // dirty checking → UPDATE 로 격상
                });
            } catch (Throwable t) {
                failures.add(t);
            }
        };

        executor.submit(txn);
        executor.submit(txn);
        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        assertThat(failures).as("한쪽 트랜잭션이 데드락으로 희생").anyMatch(ConcurrencyTestSupport::isDeadlock);

        String status = DeadlockStatusCapture.fetchLatestDeadlock(jdbc);
        String latest = DeadlockStatusCapture.extractLatestDetectedDeadlock(status);
        Path out = Path.of(".claude/deadlock/blog/03-공유락-격상/관측-product.txt");
        DeadlockStatusCapture.saveToFile(out, latest);
        // 데드락 본문에는 b_product 가 등장해야 한다.
        assertThat(latest).contains("LATEST DETECTED DEADLOCK");
        assertThat(latest).contains("b_product");
    }

    @Test
    void 처방_원자적_UPDATE_는_데드락이_나지_않는다() throws Exception {
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        var failures = new ConcurrentLinkedQueue<Throwable>();

        Runnable txn = () -> {
            try {
                tx.executeWithoutResult(status -> {
                    awaitBarrier(barrier);
                    orderService.orderSafe(productId, 1);
                });
            } catch (Throwable t) {
                failures.add(t);
            }
        };

        executor.submit(txn);
        executor.submit(txn);
        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        assertThat(failures).noneMatch(ConcurrencyTestSupport::isDeadlock);
        Product p = productRepository.findById(productId).orElseThrow();
        assertThat(p.getStock()).isEqualTo(98); // 정확히 두 번 차감
    }

    private static void awaitBarrier(CyclicBarrier b) {
        try {
            b.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
