package lab.deadlock.c;

import org.springframework.dao.DeadlockLoserDataAccessException;

import java.sql.SQLException;

public final class ConcurrencyTestSupport {

    private ConcurrencyTestSupport() {
    }

    public static boolean isDeadlock(Throwable e) {
        while (e != null) {
            if (e instanceof DeadlockLoserDataAccessException) {
                return true;
            }
            if (e instanceof SQLException sql && "40001".equals(sql.getSQLState())) {
                return true;
            }
            String msg = e.getMessage();
            if (msg != null && msg.contains("Deadlock found")) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }
}
