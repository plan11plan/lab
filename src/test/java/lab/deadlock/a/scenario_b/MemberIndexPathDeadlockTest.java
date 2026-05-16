package lab.deadlock.a.scenario_b;

import static lab.deadlock.a.scenario_b.ConcurrencyTestSupport.awaitBarrier;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class MemberIndexPathDeadlockTest extends MemberDeadlockTestBase {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberLockService memberLockService;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void initState() {
        transactionTemplate = new TransactionTemplate(txManager);
        transactionTemplate.setIsolationLevel(
                TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        jdbcTemplate.update("DELETE FROM member");
        memberRepository.save(new Member(1L, "a@x.com"));
        memberRepository.save(new Member(2L, "b@y.com"));
    }

    /**
     * 안티패턴 — 한 트랜잭션은 보조 인덱스(email)로, 다른 트랜잭션은 PK(id)로 같은 row에
     * 접근한다. 두 경로의 락 획득 단계 수가 달라(보조 인덱스 entry + PK row vs PK row 한 단계)
     * 두 번째 락 시도 단계에서 cycle이 닫힌다.
     *
     *  T1: findByEmailForUpdate('a@x.com')   → uk_member_email + PK row 1 잠금
     *      barrier
     *      findByIdForUpdate(2)              → PK row 2 요청 → T2가 보유 → 대기
     *
     *  T2: findByIdForUpdate(2)              → PK row 2 잠금
     *      barrier
     *      findByEmailForUpdate('a@x.com')   → uk_member_email 요청 → T1이 보유 → cycle
     */
    @Test
    void 같은_row에_보조인덱스와_PK로_접근하면_데드락이_발생한다() throws Exception {
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        var failures = new ConcurrentLinkedQueue<Throwable>();

        executor.submit(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    memberRepository.findByEmailForUpdate("a@x.com"); // T1 step 1
                    awaitBarrier(barrier);
                    memberRepository.findByIdForUpdate(2L);            // T1 step 2 → 대기
                });
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        executor.submit(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    memberRepository.findByIdForUpdate(2L);            // T2 step 1
                    awaitBarrier(barrier);
                    memberRepository.findByEmailForUpdate("a@x.com");  // T2 step 2 → cycle
                });
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(finished).as("두 스레드는 데드락 감지 후 정상 종료해야 한다").isTrue();

        captureInnodbStatus();

        assertThat(failures)
                .as("최소 한 스레드는 DeadlockLoserDataAccessException 또는 SQLState 40001로 실패해야 한다")
                .anyMatch(ConcurrencyTestSupport::isDeadlock);
    }

    /**
     * 처방 — PK 경로로 통일하면 보조 인덱스 entry 락 단계가 사라지고,
     * 두 트랜잭션의 락 획득 모양이 동일해진다. 정렬까지 더해지면 cycle이 닫힐 면이 없어진다.
     */
    @RepeatedTest(10)
    void PK경로로_정렬해_잠그면_데드락이_사라진다() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var failures = new ConcurrentLinkedQueue<Throwable>();

        executor.submit(() -> {
            try {
                memberLockService.lockTwoMembersSafe(1L, 2L);
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        executor.submit(() -> {
            try {
                memberLockService.lockTwoMembersSafe(2L, 1L);
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(finished).as("두 스레드는 짧은 시간 내에 종료해야 한다").isTrue();

        assertThat(failures)
                .as("정렬 + PK 경로 통일 처방 적용 시 데드락이 발생해선 안 된다")
                .noneMatch(ConcurrencyTestSupport::isDeadlock);
    }

    private void captureInnodbStatus() {
        String captured = tryShowEngineViaContainer();
        if (captured == null) {
            captured = tryReadDeadlockFromLogs();
        }
        if (captured == null) {
            return;
        }
        try {
            Path target = Path.of(System.getProperty("user.dir"),
                    "docs", "시나리오-b-관측.txt");
            Files.createDirectories(target.getParent());
            Files.writeString(target, captured);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private String tryShowEngineViaContainer() {
        try {
            var result = MemberDeadlockTestBase.MYSQL.execInContainer(
                    "mysql",
                    "-h", "127.0.0.1",
                    "-u", "lab",
                    "-plab",
                    "-e", "SHOW ENGINE INNODB STATUS\\G");
            if (result.getExitCode() != 0) {
                return null;
            }
            return sliceLatestDeadlock(result.getStdout());
        } catch (Exception ex) {
            return null;
        }
    }

    private String tryReadDeadlockFromLogs() {
        String logs = MemberDeadlockTestBase.MYSQL.getLogs();
        if (logs == null) {
            return null;
        }
        int start = logs.indexOf("LATEST DETECTED DEADLOCK");
        if (start >= 0) {
            return sliceLatestDeadlock(logs.substring(start));
        }
        for (String marker : new String[]{
                "Transactions deadlock detected",
                "TRANSACTIONS DEADLOCK DETECTED",
                "*** (1) TRANSACTION",
                "*** (1) WAITING FOR THIS LOCK TO BE GRANTED"}) {
            int idx = logs.indexOf(marker);
            if (idx >= 0) {
                int lineStart = logs.lastIndexOf('\n', idx) + 1;
                int end = Math.min(logs.length(), idx + 8000);
                return logs.substring(lineStart, end);
            }
        }
        int lastTxn = logs.lastIndexOf("TRANSACTION ");
        if (lastTxn > 0) {
            int blockStart = logs.lastIndexOf("TRANSACTION ", lastTxn - 1);
            if (blockStart < 0) {
                blockStart = lastTxn;
            }
            int lineStart = logs.lastIndexOf('\n', blockStart) + 1;
            return logs.substring(lineStart);
        }
        return null;
    }

    private String sliceLatestDeadlock(String status) {
        int start = status.indexOf("LATEST DETECTED DEADLOCK");
        if (start < 0) {
            int end = Math.min(status.length(), 8000);
            return status.substring(0, end);
        }
        int end = status.indexOf("TRANSACTIONS", start + "LATEST DETECTED DEADLOCK".length());
        if (end < 0) {
            end = status.indexOf("FILE I/O", start);
        }
        if (end < 0) {
            end = Math.min(status.length(), start + 8000);
        }
        return status.substring(start, end);
    }
}
