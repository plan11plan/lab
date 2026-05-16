package lab.deadlock.a.account;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;

@TestConfiguration
public abstract class AccountDeadlockTestBase {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("lab_deadlock")
            .withUsername("lab")
            .withPassword("lab")
            // SHOW ENGINE INNODB STATUS의 LATEST DETECTED DEADLOCK 섹션과 더불어
            // 에러 로그(컨테이너 stdout)에도 모든 데드락이 기록되도록 켠다.
            // 일반 사용자에 PROCESS 권한이 없어 직접 SHOW를 호출할 수 없을 때,
            // 컨테이너 로그(MYSQL.getLogs())에서 누적된 데드락 정보를 그대로 추출한다.
            .withCommand(
                    "--innodb-print-all-deadlocks=ON",
                    "--innodb-lock-wait-timeout=50",
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_unicode_ci");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.MySQLDialect");
        // 동시 스레드가 살아 있을 수 있도록 풀 크기를 명시적으로 키운다.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
    }
}
