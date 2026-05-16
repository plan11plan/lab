package lab.deadlock.a.scenario_c;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;

import java.sql.SQLException;

public final class ConcurrencyTestSupport {

    private ConcurrencyTestSupport() {}

    public static boolean isDeadlock(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof DeadlockLoserDataAccessException) return true;
            if (cur instanceof SQLException sql) {
                if ("40001".equals(sql.getSQLState())) return true;
                if (sql.getErrorCode() == 1213) return true;
            }
            String msg = cur.getMessage();
            if (msg != null && msg.contains("Deadlock found")) return true;
            cur = cur.getCause();
        }
        return false;
    }

    public static boolean isUniqueViolation(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof DataIntegrityViolationException) return true;
            if (cur instanceof SQLException sql && sql.getErrorCode() == 1062) return true;
            String msg = cur.getMessage();
            if (msg != null && msg.contains("Duplicate entry")) return true;
            cur = cur.getCause();
        }
        return false;
    }
}
