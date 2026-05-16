package lab.deadlock.b.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DeadlockStatusCapture {

    private DeadlockStatusCapture() {}

    public static String fetchLatestDeadlock(JdbcTemplate jdbc) {
        try {
            return jdbc.execute((java.sql.Connection conn) -> {
                try (java.sql.Statement stmt = conn.createStatement();
                     java.sql.ResultSet rs = stmt.executeQuery("SHOW ENGINE INNODB STATUS")) {
                    if (rs.next()) {
                        int colCount = rs.getMetaData().getColumnCount();
                        return rs.getString(colCount);
                    }
                    return "(no rows)";
                }
            });
        } catch (Exception ex) {
            StringBuilder sb = new StringBuilder("(failed to fetch SHOW ENGINE INNODB STATUS: ");
            Throwable cur = ex;
            while (cur != null) {
                sb.append(cur.getClass().getSimpleName()).append(": ").append(cur.getMessage()).append(" -> ");
                cur = cur.getCause();
            }
            sb.append(")");
            return sb.toString();
        }
    }

    public static String extractLatestDetectedDeadlock(String fullStatus) {
        if (fullStatus == null) return "(null)";
        int begin = fullStatus.indexOf("LATEST DETECTED DEADLOCK");
        if (begin < 0) {
            return "(LATEST DETECTED DEADLOCK section not found)";
        }
        int end = fullStatus.indexOf("------------", begin + "LATEST DETECTED DEADLOCK".length());
        // 데드락 섹션은 다음 큰 구분선까지 이어진다.
        int sectionEnd = fullStatus.indexOf("\nTRANSACTIONS\n", begin);
        if (sectionEnd < 0) sectionEnd = fullStatus.length();
        return fullStatus.substring(begin, sectionEnd);
    }

    public static void saveToFile(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
