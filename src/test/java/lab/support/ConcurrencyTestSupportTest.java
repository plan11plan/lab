package lab.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConcurrencyTestSupportTest {

    @Test
    void runConcurrently_는_성공_실패_카운트를_정확히_집계한다() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        ConcurrencyResult result = ConcurrencyTestSupport.runConcurrently(100, () -> {
            if (counter.incrementAndGet() % 3 == 0) {
                throw new RuntimeException("simulated");
            }
        });

        assertThat(result.total()).isEqualTo(100);
        assertThat(result.fail()).isEqualTo(33); // 3,6,...,99 = 33개
        assertThat(result.success()).isEqualTo(67);
    }
}
