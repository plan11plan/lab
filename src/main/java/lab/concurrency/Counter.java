package lab.concurrency;

/**
 * race가 발생할 수 있는 단순 카운터.
 * increment는 read → modify → write 세 단계로 분해되며, 락이 없어 두 스레드가
 * 같은 값을 읽고 같은 값을 쓰면 한 번의 증가가 묻혀 사라진다 (lost update).
 */
public class Counter {

    private int count = 0;

    public void increment() {
        int snapshot = count;   // R
        Thread.yield();         // R과 W 사이에 다른 스레드가 끼어들 수 있도록 양보
        count = snapshot + 1;   // W
    }

    public int get() {
        return count;
    }
}
