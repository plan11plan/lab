package lab.support;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ConcurrencyTestSupport {

    private ConcurrencyTestSupport() {}

    public static ConcurrencyResult runConcurrently(int n, Runnable task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(n);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        for (int i = 0; i < n; i++) {
            pool.submit(() -> {
                try {
                    task.run();
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            boolean finished = latch.await(30, TimeUnit.SECONDS);
            if (!finished) {
                throw new IllegalStateException(
                    "Concurrent tasks did not finish within 30s (completed="
                        + (n - latch.getCount()) + "/" + n + ")");
            }
            return new ConcurrencyResult(success.get(), fail.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
