package lab.deadlock.a.scenario_c;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = lab.Application.class)
@Testcontainers
public abstract class UserHandleDeadlockTestBase {

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
        .withDatabaseName("lab")
        .withUsername("lab")
        .withPassword("lab")
        // RR + 모든 데드락 출력 + 짧은 lock wait (A(c)는 인덱스 검사 순서가 매번 달라
        // cycle 닫힘이 흔들릴 수 있으므로 timeout 을 줄여 재시도 회전을 빠르게 한다)
        .withCommand(
            "--character-set-server=utf8mb4",
            "--collation-server=utf8mb4_unicode_ci",
            "--transaction-isolation=REPEATABLE-READ",
            "--innodb-print-all-deadlocks=ON",
            "--innodb-lock-wait-timeout=2"
        );

    static {
        MYSQL.start();
        grantProcessPrivilege();
    }

    private static void grantProcessPrivilege() {
        String[] passwordsToTry = { MYSQL.getPassword(), "test", "" };
        for (String pwd : passwordsToTry) {
            try {
                var result = MYSQL.execInContainer(
                    "mysql",
                    "-uroot",
                    pwd.isEmpty() ? "--password=" : ("-p" + pwd),
                    "-e",
                    "GRANT PROCESS ON *.* TO '" + MYSQL.getUsername() + "'@'%'; FLUSH PRIVILEGES;"
                );
                if (result.getExitCode() == 0) {
                    return;
                }
            } catch (Exception ignored) {
            }
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        r.add("spring.jpa.show-sql", () -> "true");
        r.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
    }
}
