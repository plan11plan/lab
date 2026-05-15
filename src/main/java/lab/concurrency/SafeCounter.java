package lab.concurrency;

/**
 * Counter와 동일한 로직에 synchronized만 추가한 버전.
 * read → yield → write 전체가 하나의 임계영역으로 묶이므로, 어떤 스레드가
 * increment를 수행하는 동안 다른 스레드는 대기한다 → lost update 발생 불가.
 */
public class SafeCounter {

    private int count = 0;

    public synchronized void increment() {
        int snapshot = count;
        Thread.yield();
        count = snapshot + 1;
    }

    public synchronized int get() {
        return count;
    }
}
