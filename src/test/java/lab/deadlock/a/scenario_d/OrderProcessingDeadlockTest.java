package lab.deadlock.a.scenario_d;

import static lab.deadlock.a.scenario_d.ConcurrencyTestSupport.awaitBarrier;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class OrderProcessingDeadlockTest extends OrderProcessingDeadlockTestBase {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderProcessingService processingService;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void initState() {
        transactionTemplate = new TransactionTemplate(txManager);
        transactionTemplate.setIsolationLevel(
                TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // 매 테스트마다 깨끗한 (orders, inventory) 한 쌍을 준비한다.
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM inventory");
        orderRepository.save(new Order(1L, 10L, "CREATED"));
        inventoryRepository.save(new Inventory(10L, 100));
    }

    /**
     * 안티패턴 — 주문(processOrder)은 Order → Inventory 순으로,
     * 환불(processRefund)은 Inventory → Order 순으로 잠근다.
     * 두 워크플로가 같은 (orderId=1, productId=10) 쌍에 동시에 들어오면
     * 락 획득 순서가 엇갈리면서 서로 다른 두 테이블 사이에 cycle이 닫혀야 한다.
     */
    @Test
    void 주문과_환불이_반대_순서로_두_테이블을_잠그면_데드락이_발생한다() throws Exception {
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        var failures = new ConcurrentLinkedQueue<Throwable>();

        // T1 — 주문 처리: Order(1) 락 → barrier → Inventory(10) 락
        executor.submit(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    orderRepository.findByIdForUpdate(1L);      // T1 step 1: Order row 락
                    awaitBarrier(barrier);                       // T2 step 1 완료 대기
                    inventoryRepository.findByIdForUpdate(10L); // T1 step 2: Inventory row 락 → 대기
                });
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        // T2 — 환불 처리: Inventory(10) 락 → barrier → Order(1) 락
        executor.submit(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    inventoryRepository.findByIdForUpdate(10L); // T2 step 1: Inventory row 락
                    awaitBarrier(barrier);
                    orderRepository.findByIdForUpdate(1L);      // T2 step 2: Order row 락 → cycle
                });
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(finished).as("두 스레드는 데드락 감지 후 정상 종료해야 한다").isTrue();

        // SHOW ENGINE INNODB STATUS / 컨테이너 로그에서 마지막 감지된 데드락을 캡처한다.
        captureInnodbStatus();

        assertThat(failures)
                .as("최소 한 스레드는 DeadlockLoserDataAccessException 또는 SQLState 40001로 실패해야 한다")
                .anyMatch(ConcurrencyTestSupport::isDeadlock);
    }

    /**
     * 처방 — processOrderSafe / processRefundSafe는 항상 Inventory → Order 순서로 잠근다.
     * 두 워크플로가 같은 락 순서 컨벤션을 따르므로 동시 실행에도 데드락이 발생하지 않아야 한다.
     */
    @RepeatedTest(10)
    void 두_워크플로가_같은_순서로_두_테이블을_잠그면_데드락이_사라진다() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var failures = new ConcurrentLinkedQueue<Throwable>();

        executor.submit(() -> {
            try {
                processingService.processOrderSafe(1L, 10L, 1);
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        executor.submit(() -> {
            try {
                processingService.processRefundSafe(1L, 10L, 1);
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(finished).as("두 스레드는 짧은 시간 내에 종료해야 한다").isTrue();

        assertThat(failures)
                .as("같은 순서 컨벤션 처방 적용 시 데드락이 발생해선 안 된다")
                .noneMatch(ConcurrencyTestSupport::isDeadlock);
    }

    private void captureInnodbStatus() {
        // PROCESS 권한이 없는 일반 사용자(`lab`)로는 SHOW ENGINE INNODB STATUS를 직접 호출하기 어렵다.
        // innodb-print-all-deadlocks=ON 옵션이 컨테이너 stdout에도 데드락 상세를 찍으므로,
        // 두 경로를 차례로 시도해 캡처한다.
        String captured = tryShowEngineViaContainer();
        if (captured == null) {
            captured = tryReadDeadlockFromLogs();
        }
        if (captured == null) {
            return;
        }
        try {
            Path target = Path.of(System.getProperty("user.dir"),
                    "docs", "시나리오-d-관측.txt");
            Files.createDirectories(target.getParent());
            Files.writeString(target, captured);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private String tryShowEngineViaContainer() {
        try {
            var result = OrderProcessingDeadlockTestBase.MYSQL.execInContainer(
                    "mysql",
                    "-h", "127.0.0.1",
                    "-u", "lab",
                    "-plab",
                    "-e", "SHOW ENGINE INNODB STATUS\\G");
            if (result.getExitCode() != 0) {
                return null;
            }
            String out = result.getStdout();
            return sliceLatestDeadlock(out);
        } catch (Exception ex) {
            return null;
        }
    }

    private String tryReadDeadlockFromLogs() {
        String logs = OrderProcessingDeadlockTestBase.MYSQL.getLogs();
        if (logs == null) {
            return null;
        }
        int start = logs.indexOf("LATEST DETECTED DEADLOCK");
        if (start >= 0) {
            return sliceLatestDeadlock(logs.substring(start));
        }
        for (String marker : new String[]{
                "Transactions deadlock detected",
                "TRANSACTIONS DEADLOCK DETECTED",
                "*** (1) TRANSACTION",
                "*** (1) WAITING FOR THIS LOCK TO BE GRANTED"}) {
            int idx = logs.indexOf(marker);
            if (idx >= 0) {
                int lineStart = logs.lastIndexOf('\n', idx) + 1;
                int end = Math.min(logs.length(), idx + 8000);
                return logs.substring(lineStart, end);
            }
        }
        int lastTxn = logs.lastIndexOf("TRANSACTION ");
        if (lastTxn > 0) {
            int blockStart = logs.lastIndexOf("TRANSACTION ", lastTxn - 1);
            if (blockStart < 0) {
                blockStart = lastTxn;
            }
            int lineStart = logs.lastIndexOf('\n', blockStart) + 1;
            return logs.substring(lineStart);
        }
        return null;
    }

    private String sliceLatestDeadlock(String status) {
        int start = status.indexOf("LATEST DETECTED DEADLOCK");
        if (start < 0) {
            int end = Math.min(status.length(), 8000);
            return status.substring(0, end);
        }
        int end = status.indexOf("TRANSACTIONS", start + "LATEST DETECTED DEADLOCK".length());
        if (end < 0) {
            end = status.indexOf("FILE I/O", start);
        }
        if (end < 0) {
            end = Math.min(status.length(), start + 8000);
        }
        return status.substring(start, end);
    }
}
