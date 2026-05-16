package lab.deadlock.c;

import lab.Application;
import lab.deadlock.c.reservation.Reservation;
import lab.deadlock.c.reservation.ReservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;

@SpringBootTest(classes = Application.class)
@Testcontainers
public abstract class ReservationDeadlockTestBase {

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
        .withDatabaseName("lab")
        .withUsername("lab")
        .withPassword("lab")
        .withCommand(
            "--character-set-server=utf8mb4",
            "--collation-server=utf8mb4_unicode_ci",
            "--transaction-isolation=REPEATABLE-READ",
            "--innodb-print-all-deadlocks=ON",
            "--innodb-lock-wait-timeout=50"
        );

    static {
        MYSQL.start();
        // SHOW ENGINE INNODB STATUS 캡처를 위해 PROCESS 권한 부여.
        // testcontainers MySQLContainer는 root 패스워드를 user 패스워드와 동일하게 설정한다.
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

    @Autowired
    protected ReservationRepository reservationRepository;

    @Autowired
    protected PlatformTransactionManager txManager;

    @Autowired
    protected DataSource dataSource;

    protected TransactionTemplate newRepeatableReadTemplate() {
        TransactionTemplate t = new TransactionTemplate(txManager);
        t.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return t;
    }

    protected TransactionTemplate newReadCommittedTemplate() {
        TransactionTemplate t = new TransactionTemplate(txManager);
        t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return t;
    }

    protected void clearReservations() {
        reservationRepository.deleteAll();
    }

    protected Reservation insertExisting(Long roomId, java.time.LocalDateTime start, java.time.LocalDateTime end) {
        return reservationRepository.save(new Reservation(roomId, start, end));
    }
}
