package lab.concurrency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 아쉬운 동시성 테스트 — 단순 for 반복문.
 * 동시 실행이 아니라 직렬 호출이므로 race condition이 절대 발생하지 않는다.
 * "테스트가 통과한다 = 코드가 안전하다"가 아님을 보여준다.
 */
class BadForLoopTest {

    @Test
    void unsafe_counter_passes_with_for_loop() {
        Counter counter = new Counter();
        int callCount = 30;

        for (int i = 0; i < callCount; i++) {
            counter.increment();
        }

        System.out.printf("[BadForLoop] expected=%d, actual=%d%n", callCount, counter.get());
        assertThat(counter.get()).isEqualTo(callCount);
    }
}
