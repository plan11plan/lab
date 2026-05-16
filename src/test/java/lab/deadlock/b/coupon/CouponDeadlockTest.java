package lab.deadlock.b.coupon;

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
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = lab.Application.class)
@ActiveProfiles("test")
class CouponDeadlockTest {

    @Autowired CouponRepository couponRepository;
    @Autowired CouponService couponService;
    @Autowired PlatformTransactionManager txManager;
    @Autowired JdbcTemplate jdbc;

    TransactionTemplate tx;

    @BeforeEach
    void setup() {
        tx = new TransactionTemplate(txManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        jdbc.execute("SET GLOBAL innodb_print_all_deadlocks = ON");
    }

    @AfterEach
    void cleanup() {
        couponRepository.deleteAll();
    }

    /**
     * 세 트랜잭션 패턴:
     * - T1: 같은 code 로 UPSERT 후 잠시 보유 → rollback
     * - T2, T3: T1 이 INSERT 한 code 에 같은 UPSERT 시도 →
     *           양쪽 다 검사 단계에서 S-lock 을 잡은 뒤
     *           T1 rollback 후 INSERT/X-lock 격상 시도 → 데드락
     */
    @Test
    void 안티패턴_UPSERT_동시_claim_은_데드락을_일으킨다() throws Exception {
        int attempts = 12;
        boolean deadlockObserved = false;
        String capturedDeadlockSection = null;

        for (int round = 0; round < attempts && !deadlockObserved; round++) {
            String code = "WELCOME-" + UUID.randomUUID();
            couponRepository.deleteAll();

            var t1Inserted = new CountDownLatch(1);
            var contendersStarted = new CountDownLatch(2);
            var failures = new ConcurrentLinkedQueue<Throwable>();
            var executor = Executors.newFixedThreadPool(3);

            Runnable t1 = () -> {
                try {
                    tx.executeWithoutResult(status -> {
                        couponService.claim(code, 99L);
                        t1Inserted.countDown();
                        try {
                            contendersStarted.await(5, TimeUnit.SECONDS);
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        status.setRollbackOnly();
                    });
                } catch (Throwable t) {
                    failures.add(t);
                }
            };
            Runnable contender = () -> {
                try {
                    t1Inserted.await(5, TimeUnit.SECONDS);
                    contendersStarted.countDown();
                    tx.executeWithoutResult(status -> couponService.claim(code, 1L));
                } catch (Throwable t) {
                    failures.add(t);
                }
            };

            executor.submit(t1);
            executor.submit(contender);
            executor.submit(contender);
            executor.shutdown();
            executor.awaitTermination(20, TimeUnit.SECONDS);

            if (failures.stream().anyMatch(ConcurrencyTestSupport::isDeadlock)) {
                String status = DeadlockStatusCapture.fetchLatestDeadlock(jdbc);
                String latest = DeadlockStatusCapture.extractLatestDetectedDeadlock(status);
                if (latest.contains("LATEST DETECTED DEADLOCK") && latest.contains("b_coupon_code")) {
                    deadlockObserved = true;
                    capturedDeadlockSection = latest;
                }
            }
        }

        assertThat(deadlockObserved).as("UPSERT 3-트랜잭션 동시 호출은 데드락을 일으킨다").isTrue();
        DeadlockStatusCapture.saveToFile(
            Path.of(".claude/deadlock/blog/03-공유락-격상/관측-coupon.txt"),
            capturedDeadlockSection);
    }

    @Test
    void 처방_명시적_X_lock_은_데드락_없이_한_명만_받는다() throws Exception {
        String code = "WELCOME-SAFE-" + UUID.randomUUID();
        couponRepository.save(new CouponCode(code));

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var done  = new CountDownLatch(2);
        var executor = Executors.newFixedThreadPool(2);
        var results = new ConcurrentLinkedQueue<Boolean>();
        var failures = new ConcurrentLinkedQueue<Throwable>();

        Runnable t1 = () -> {
            ready.countDown();
            try {
                start.await();
                tx.execute(status -> {
                    results.add(couponService.claimSafe(code, 1L));
                    return null;
                });
            } catch (Throwable t) {
                failures.add(t);
            } finally {
                done.countDown();
            }
        };
        Runnable t2 = () -> {
            ready.countDown();
            try {
                start.await();
                tx.execute(status -> {
                    results.add(couponService.claimSafe(code, 2L));
                    return null;
                });
            } catch (Throwable t) {
                failures.add(t);
            } finally {
                done.countDown();
            }
        };

        executor.submit(t1);
        executor.submit(t2);
        ready.await();
        start.countDown();
        done.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(failures).noneMatch(ConcurrencyTestSupport::isDeadlock);
        // 정확히 한 명만 성공
        long successCount = results.stream().filter(Boolean::booleanValue).count();
        assertThat(successCount).isEqualTo(1);
    }
}
