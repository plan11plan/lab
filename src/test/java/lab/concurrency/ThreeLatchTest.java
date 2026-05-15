package lab.concurrency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 패턴 2 — 3-Latch 패턴 (ready / start / done).
 * ready: 모든 스레드가 출발선에 도착했는지 확인
 * start: 진짜 일제 출발
 * done: 완료 동기화
 * 시작 정렬 정밀도가 가장 높다.
 */
class ThreeLatchTest {

    @Test
    void unsafe_counter_with_three_latch() throws InterruptedException {
        Counter counter = new Counter();
        int threadCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
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

        ready.await();
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        int lost = threadCount - counter.get();
        System.out.printf("[ThreeLatch]     expected=%d, actual=%d, lost=%d%n",
                threadCount, counter.get(), lost);
    }
}
