package lab.concurrency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 패턴 1 — ExecutorService + 단일 CountDownLatch.
 * start.await()로 일제 출발, done.await()로 완료 동기화.
 * 시작 정렬 정밀도는 보통 (스레드 풀 큐잉으로 일부 늦게 도착할 수 있음).
 */
class SingleLatchTest {

    @Test
    void unsafe_counter_with_single_latch() throws InterruptedException {
        Counter counter = new Counter();
        int threadCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    counter.increment();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        int lost = threadCount - counter.get();
        System.out.printf("[SingleLatch]    expected=%d, actual=%d, lost=%d%n",
                threadCount, counter.get(), lost);
    }
}
