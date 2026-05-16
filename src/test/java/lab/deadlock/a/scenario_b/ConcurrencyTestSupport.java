package lab.deadlock.a.scenario_b;

import java.sql.SQLException;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.dao.DeadlockLoserDataAccessException;

public final class ConcurrencyTestSupport {

    private ConcurrencyTestSupport() {
    }

    /**
     * MySQL ERROR 1213은 Spring에서 DeadlockLoserDataAccessException으로 매핑되거나,
     * 변환되지 않은 채 JDBC 표준 SQLState 40001로 노출된다. 양쪽 모두 확인한다.
     */
    public static boolean isDeadlock(Throwable e) {
        while (e != null) {
            if (e instanceof DeadlockLoserDataAccessException) {
                return true;
            }
            if (e instanceof SQLException sql && "40001".equals(sql.getSQLState())) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }

    public static void awaitBarrier(CyclicBarrier b) {
        try {
            b.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException | BrokenBarrierException | TimeoutException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
