package lab.deadlock.a.account;

import static lab.deadlock.a.account.ConcurrencyTestSupport.awaitBarrier;
import static lab.deadlock.a.account.ConcurrencyTestSupport.isDeadlock;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
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
class AccountTransferDeadlockTest extends AccountDeadlockTestBase {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountTransferService transferService;

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

        // 두 계좌만 깨끗하게 셋업한다.
        jdbcTemplate.update("DELETE FROM account");
        accountRepository.save(new Account(1L, new BigDecimal("1000.0000")));
        accountRepository.save(new Account(2L, new BigDecimal("1000.0000")));
    }

    /**
     * 안티패턴 — transfer(1,2) vs transfer(2,1) 동시 실행 시
     * 락 획득 순서가 [1,2] vs [2,1]로 엇갈려 데드락이 발생해야 한다.
     */
    @Test
    void 반대순서_이체_두건이_동시에_들어오면_데드락이_발생한다() throws Exception {
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        var failures = new ConcurrentLinkedQueue<Throwable>();

        executor.submit(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    accountRepository.findByIdForUpdate(1L); // T1 step 1: A 락
                    awaitBarrier(barrier);                   // T2 step 1 완료 대기
                    accountRepository.findByIdForUpdate(2L); // T1 step 2: B 락 → 대기
                });
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        executor.submit(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    accountRepository.findByIdForUpdate(2L); // T2 step 1: B 락
                    awaitBarrier(barrier);
                    accountRepository.findByIdForUpdate(1L); // T2 step 2: A 락 → cycle
                });
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(finished).as("두 스레드는 데드락 감지 후 정상 종료해야 한다").isTrue();

        // SHOW ENGINE INNODB STATUS 캡처 — 마지막 감지된 데드락 정보
        captureInnodbStatus();

        assertThat(failures)
                .as("최소 한 스레드는 DeadlockLoserDataAccessException 또는 SQLState 40001로 실패해야 한다")
                .anyMatch(ConcurrencyTestSupport::isDeadlock);
    }

    /**
     * 처방 — transferSafe는 Math.min/max로 정렬한 뒤 락을 잡으므로
     * 동일 시나리오에서 데드락이 발생하지 않아야 한다. 안정성 검증을 위해 10회 반복.
     */
    @RepeatedTest(10)
    void Math_minmax로_정렬한_뒤_락을잡으면_데드락이_사라진다() throws Exception {
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        var failures = new ConcurrentLinkedQueue<Throwable>();

        executor.submit(() -> {
            try {
                // T1: 1 → 2 이체. 락은 내부에서 [1, 2] 순서로 정렬돼 잡힘.
                transferService.transferSafe(1L, 2L, new BigDecimal("100.0000"));
                awaitBarrier(barrier);
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        executor.submit(() -> {
            try {
                // T2: 2 → 1 이체. 락은 내부에서 동일하게 [1, 2] 순서로 정렬돼 잡힘.
                transferService.transferSafe(2L, 1L, new BigDecimal("50.0000"));
                awaitBarrier(barrier);
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(finished).as("두 스레드는 짧은 시간 내에 종료해야 한다").isTrue();

        assertThat(failures)
                .as("정렬 처방 적용 시 데드락이 발생해선 안 된다")
                .noneMatch(ConcurrencyTestSupport::isDeadlock);
    }

    private void captureInnodbStatus() {
        // SHOW ENGINE INNODB STATUS는 PROCESS 권한이 필요해 일반 사용자(`lab`)로는 호출 불가.
        // 컨테이너 시작 옵션 --innodb-print-all-deadlocks=ON 덕에 데드락 정보는 MySQL의 stdout으로도
        // 출력되므로, 두 경로를 차례로 시도해 캡처한다.
        String captured = tryShowEngineViaContainer();
        if (captured == null) {
            captured = tryReadDeadlockFromLogs();
        }
        if (captured == null) {
            return;
        }
        try {
            Path target = Path.of(System.getProperty("user.dir"),
                    ".claude", "deadlock", "blog", "02-반대순서-잠금", "관측.txt");
            Files.createDirectories(target.getParent());
            Files.writeString(target, captured);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private String tryShowEngineViaContainer() {
        try {
            var result = AccountDeadlockTestBase.MYSQL.execInContainer(
                    "mysql",
                    "-h", "127.0.0.1",
                    "-u", "lab",
                    "-plab",
                    "-e", "SHOW ENGINE INNODB STATUS\\G");
            if (result.getExitCode() != 0) {
                return null;
            }
            String out = result.getStdout();
            return sliceLatestDeadlock(out);
        } catch (Exception ex) {
            return null;
        }
    }

    private String tryReadDeadlockFromLogs() {
        // innodb-print-all-deadlocks=ON 옵션으로 MySQL이 컨테이너 stdout에 데드락 상세를 찍는다.
        String logs = AccountDeadlockTestBase.MYSQL.getLogs();
        if (logs == null) {
            return null;
        }
        int start = logs.indexOf("LATEST DETECTED DEADLOCK");
        if (start >= 0) {
            return sliceLatestDeadlock(logs.substring(start));
        }
        // print-all-deadlocks의 헤더 변형 — 버전별로 메시지가 다를 수 있다.
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
        // 마지막 폴백 — innodb 로그가 찍은 TRANSACTION 블록만 추려서 반환한다.
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
            // 컨테이너 로그 케이스 — 헤더 없이 바로 데드락 본문
            int end = Math.min(status.length(), 8000);
            return status.substring(0, end);
        }
        // 다음 섹션 헤더("TRANSACTIONS" 또는 "FILE I/O") 전까지 잘라낸다.
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
