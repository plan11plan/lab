package lab.deadlock.a.scenario_d;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;

@TestConfiguration
public abstract class OrderProcessingDeadlockTestBase {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("lab_deadlock_d")
            .withUsername("lab")
            .withPassword("lab")
            // innodb-print-all-deadlocks=ON으로 데드락이 감지될 때마다 MySQL이 컨테이너 stdout에
            // 두 트랜잭션의 상세를 그대로 인쇄한다. PROCESS 권한이 없는 일반 사용자도 컨테이너
            // 로그(MYSQL.getLogs())에서 데드락 정보를 추출할 수 있다.
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
        // 두 트랜잭션이 동시에 살아 있도록 풀 크기를 명시적으로 키운다.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
    }
}
