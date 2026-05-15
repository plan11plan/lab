package lab.concurrency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 폐회로 검증 — 락 추가 후 같은 3-Latch 테스트가 통과하는지 확인.
 * Counter가 SafeCounter로 바뀌었을 뿐, 테스트 구조는 ThreeLatchTest와 동일.
 *
 * 락 없음 → race 재현
 * 락 추가 → race 사라짐
 * 두 결과를 모두 거쳐야 동시성 테스트의 유효성이 입증된다.
 */
class SafeCounterClosedLoopTest {

    @Test
    void safe_counter_with_three_latch_passes() throws InterruptedException {
        SafeCounter counter = new SafeCounter();
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

        System.out.printf("[SafeCounter]    expected=%d, actual=%d%n",
                threadCount, counter.get());
        assertThat(counter.get()).isEqualTo(threadCount);
    }
}
