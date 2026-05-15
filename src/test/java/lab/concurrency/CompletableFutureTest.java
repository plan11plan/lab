package lab.concurrency;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 패턴 3 — CompletableFuture.
 * runAsync로 던지자마자 실행, allOf().join()으로 완료 동기화.
 * 코드는 가장 간결하지만 시작 정렬은 따로 보장하지 않는다.
 */
class CompletableFutureTest {

    @Test
    void unsafe_counter_with_completable_future() {
        Counter counter = new Counter();
        int threadCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(32);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(CompletableFuture.runAsync(counter::increment, executor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        int lost = threadCount - counter.get();
        System.out.printf("[CompletableF.]  expected=%d, actual=%d, lost=%d%n",
                threadCount, counter.get(), lost);
    }
}
