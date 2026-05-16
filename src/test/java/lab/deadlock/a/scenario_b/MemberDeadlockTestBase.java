package lab.deadlock.a.scenario_b;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;

@TestConfiguration
public abstract class MemberDeadlockTestBase {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("lab_deadlock_b")
            .withUsername("lab")
            .withPassword("lab")
            // 데드락이 감지될 때마다 컨테이너 stdout에 두 트랜잭션의 상세를 인쇄한다.
            // 일반 사용자에 PROCESS 권한이 없어 SHOW ENGINE INNODB STATUS를 호출할 수 없을 때,
            // 컨테이너 로그(MYSQL.getLogs())에서 같은 정보를 추출할 수 있다.
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
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
    }
}
