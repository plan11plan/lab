package lab.deadlock.a.scenario_c;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §2.3 (c) — INSERT 시 여러 UNIQUE 인덱스 검사 순서.
 *
 * 핵심 — 한 트랜잭션이 여러 UNIQUE 인덱스 entry 에 차례로 락을 잡는데, 두 트랜잭션의 entry
 * 락 획득 순서가 엇갈리면서 cycle 이 닫힌다.
 *
 * §3.4 B(b) 와의 차이 — 거기는 두 트랜잭션이 *같은* unique 값으로 충돌해 *같은* 인덱스
 * entry 위에 묵시적 S-lock 이 공존하는 모양이다. A(c) 는 락이 걸리는 인덱스 entry 가
 * 트랜잭션마다 다르고, 양쪽이 *서로 다른 인덱스의 서로 다른 row* 에 부딪힌다.
 *
 * 재현 전략 — 단일 INSERT 만으로는 인덱스 검사 순서가 같은 트랜잭션에서 일관적으로 잡혀
 * cross-lock 이 잘 닫히지 않는다. InnoDB 가 첫 unique 충돌에서 즉시 short-circuit 하기도
 * 한다. 안정적 재현을 위해 *한 트랜잭션에서 두 INSERT* 를 *반대 순서* 로 실행한다 —
 * 첫 INSERT 가 한 인덱스 entry 의 묵시적 락을 깔고, 두 번째 INSERT 가 다른 인덱스 entry
 * 락을 시도하는 동안 상대가 이미 그 entry 를 들고 있다.
 */
class UserHandleDeadlockTest extends UserHandleDeadlockTestBase {

    @Autowired
    private UserHandleRepository repository;

    @Autowired
    private UserHandleService service;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private JdbcTemplate jdbc;

    private TransactionTemplate tx;

    // 시드 — 두 트랜잭션이 *각각 다른* UNIQUE 인덱스에서 부딪힐 두 row.
    //   SEED_A: email='alpha@x.com', phone='+82-1111'
    //   SEED_G: email='gamma@x.com', phone='+82-9999'
    private static final String SEED_A_EMAIL = "alpha@x.com";
    private static final String SEED_A_PHONE = "+82-1111";
    private static final String SEED_G_EMAIL = "gamma@x.com";
    private static final String SEED_G_PHONE = "+82-9999";

    private static final Path OBSERVATION_PATH = Path.of(
        System.getProperty("user.dir"),
        "docs/시나리오-c-관측.txt"
    );

    private static final AtomicInteger ROUND_COUNT = new AtomicInteger(0);
    private static final AtomicInteger DEADLOCK_HIT_COUNT = new AtomicInteger(0);
    private static volatile boolean CAPTURED = false;

    @BeforeEach
    void setup() {
        tx = new TransactionTemplate(txManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    private void reseed() {
        repository.deleteAll();
        repository.save(new UserHandle(SEED_A_EMAIL, SEED_A_PHONE));
        repository.save(new UserHandle(SEED_G_EMAIL, SEED_G_PHONE));
    }

    /**
     * 안티패턴 — 두 트랜잭션이 *서로 다른 UNIQUE 인덱스의 서로 다른 기존 row* 에 X-lock 을 차례로
     * 잡으려 한다. 한 트랜잭션 안에서 두 단계의 락 획득이 일어나야 cross 가 닫히므로, 한
     * 트랜잭션 안에서 두 단계 락을 명시적으로 분리한다.
     *
     * 단계 1 (CyclicBarrier 로 동기화):
     *   T1: SEED_A 의 email entry 락 (findByEmailForUpdate('alpha@x.com'))
     *        — uk_user_handle_email 인덱스의 'alpha' entry + 그 PK row 에 X-lock
     *   T2: SEED_G 의 phone entry 락 (findByPhoneForUpdate('+82-9999'))
     *        — uk_user_handle_phone 인덱스의 '+82-9999' entry + 그 PK row 에 X-lock
     *
     * 단계 2:
     *   T1: SEED_G 의 phone entry 락 시도 (findByPhoneForUpdate('+82-9999')) → T2 보유 중, 대기
     *   T2: SEED_A 의 email entry 락 시도 (findByEmailForUpdate('alpha@x.com')) → T1 보유 중, cycle
     *
     * INSERT 단계에서도 같은 cross 가 만들어진다 — 두 새 row 의 unique 검사가 서로의 entry 락에
     * 막힌다. 본 테스트는 INSERT 까지 가서 cycle 이 닫히는 모양을 확인한다.
     */
    @RepeatedTest(20)
    @DisplayName("안티패턴 — 두 단계 락이 서로 다른 UNIQUE 인덱스에서 엇갈리면 cross-lock 데드락이 닫힌다")
    void cross_unique_index_lock_order_makes_deadlock(org.junit.jupiter.api.RepetitionInfo info) throws Exception {
        ROUND_COUNT.incrementAndGet();
        reseed();

        var barrier = new CyclicBarrier(2);
        var failures = new ConcurrentLinkedQueue<Throwable>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            try {
                tx.executeWithoutResult(status -> {
                    // 단계 1 — SEED_A row 의 email entry 'alpha@x.com' 에 X-lock.
                    // 보조 인덱스 uk_user_handle_email 의 'alpha' entry + PK row 모두 잡힌다.
                    repository.findByEmailForUpdate(SEED_A_EMAIL).orElseThrow();
                    awaitBarrier(barrier);
                    // 단계 2 — SEED_G row 의 phone entry '+82-9999' 에 X-lock 요청.
                    // T2 가 보유 중이므로 대기.
                    repository.findByPhoneForUpdate(SEED_G_PHONE).orElseThrow();
                    // 여기에 도달했다면 cycle 이 안 닫혔다. 그래도 INSERT 한 번 더 던져 본다.
                    String newEmail = "ins1-" + UUID.randomUUID() + "@x.com";
                    String newPhone = "+82-NEW1-" + UUID.randomUUID();
                    service.register(newEmail, newPhone);
                });
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        executor.submit(() -> {
            try {
                tx.executeWithoutResult(status -> {
                    // 단계 1 — SEED_G row 의 phone entry '+82-9999' 에 X-lock.
                    repository.findByPhoneForUpdate(SEED_G_PHONE).orElseThrow();
                    awaitBarrier(barrier);
                    // 단계 2 — SEED_A row 의 email entry 'alpha@x.com' 에 X-lock 요청.
                    // T1 이 보유 중이므로 cycle 닫힘.
                    repository.findByEmailForUpdate(SEED_A_EMAIL).orElseThrow();
                    String newEmail = "ins2-" + UUID.randomUUID() + "@x.com";
                    String newPhone = "+82-NEW2-" + UUID.randomUUID();
                    service.register(newEmail, newPhone);
                });
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        executor.shutdown();
        boolean finished = executor.awaitTermination(30, TimeUnit.SECONDS);
        if (!finished) {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        boolean isDeadlock = failures.stream().anyMatch(ConcurrencyTestSupport::isDeadlock);

        if (isDeadlock) {
            DEADLOCK_HIT_COUNT.incrementAndGet();
            if (!CAPTURED) {
                CAPTURED = true;
                captureInnodbStatus("round=" + info.getCurrentRepetition()
                    + " — A(c) cross UNIQUE index lock order deadlock");
            }
        }

        // 각 라운드 자체는 데드락 또는 lock timeout 으로 막혀야 한다.
        // 한쪽이 정상 통과해도(다른 한쪽이 lock timeout) 적어도 failures 는 비어있지 않다.
        assertThat(failures)
            .as("두 트랜잭션 중 적어도 한쪽은 락 충돌로 실패해야 한다")
            .isNotEmpty();
    }

    @Test
    @DisplayName("재현률 보고 — A(c) 데드락 발생 빈도를 콘솔에 기록한다")
    void report_deadlock_rate() {
        int rounds = ROUND_COUNT.get();
        int hits = DEADLOCK_HIT_COUNT.get();
        System.out.println("[A(c) report] deadlock hits = " + hits + "/" + rounds);
        // 본문 산문에는 이 빈도를 그대로 박는다.
    }

    /**
     * 처방 — UNIQUE 인덱스 개수를 줄이거나 retry 로 의미 변환.
     */
    @Test
    @DisplayName("처방 — retry + 의미적 에러 변환으로 충돌을 DuplicateHandleException 으로 흘려보낸다")
    void retry_strategy_converts_conflict_to_domain_exception() {
        reseed();
        try {
            service.registerWithRetry(SEED_A_EMAIL, "+82-NEW-" + UUID.randomUUID());
            throw new AssertionError("DuplicateHandleException 이 던져져야 한다");
        } catch (DuplicateHandleException e) {
            assertThat(e.getMessage()).contains("이미 사용 중");
        }
    }

    // ----------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------

    private static void awaitBarrier(CyclicBarrier b) {
        try {
            b.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void captureInnodbStatus(String header) {
        String fullStatus = execShowInnodbStatusAsRoot();
        if (fullStatus == null || fullStatus.isBlank()) {
            return;
        }

        try {
            Files.createDirectories(OBSERVATION_PATH.getParent());
            try (var writer = Files.newBufferedWriter(OBSERVATION_PATH,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write("# SHOW ENGINE INNODB STATUS 캡처 — A(c) Cross UNIQUE Index Lock Order\n");
                writer.write("# Testcontainers MySQL 8.0, REPEATABLE READ\n");
                writer.write("# --innodb-print-all-deadlocks=ON, --innodb-lock-wait-timeout=2\n\n");
                writer.write("============================================\n");
                writer.write("[" + header + "] " + java.time.LocalDateTime.now() + "\n");
                writer.write("============================================\n");
                writer.write(extractLatestDeadlockSection(fullStatus));
                writer.write("\n");
            }
        } catch (IOException e) {
            System.out.println("[CAPTURE] 파일 적재 실패: " + e.getMessage());
        }
    }

    private String execShowInnodbStatusAsRoot() {
        String[] passwordsToTry = { MYSQL.getPassword(), "test", "" };
        for (String pwd : passwordsToTry) {
            try {
                var result = MYSQL.execInContainer(
                    "mysql",
                    "-uroot",
                    pwd.isEmpty() ? "--password=" : ("-p" + pwd),
                    "-N", "-B",
                    "-e", "SHOW ENGINE INNODB STATUS\\G"
                );
                if (result.getExitCode() == 0) {
                    return result.getStdout();
                }
            } catch (Exception ignored) {
            }
        }
        String logs = MYSQL.getLogs();
        if (logs != null && logs.contains("LATEST DETECTED DEADLOCK")) {
            return logs;
        }
        return null;
    }

    private static String extractLatestDeadlockSection(String fullStatus) {
        int start = fullStatus.indexOf("LATEST DETECTED DEADLOCK");
        if (start < 0) {
            return fullStatus.substring(0, Math.min(fullStatus.length(), 6000));
        }
        int end = fullStatus.indexOf("TRANSACTIONS", start + "LATEST DETECTED DEADLOCK".length());
        if (end < 0) {
            end = Math.min(fullStatus.length(), start + 8000);
        }
        int hr = fullStatus.lastIndexOf("\n------------\n", end);
        if (hr > start) end = hr;
        return fullStatus.substring(start, end);
    }
}
