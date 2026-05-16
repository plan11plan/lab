package lab.deadlock.b.signup;

import lab.deadlock.b.support.ConcurrencyTestSupport;
import lab.deadlock.b.support.DeadlockStatusCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = lab.Application.class)
@ActiveProfiles("test")
class SignupDeadlockTest {

    @Autowired UserRepository userRepository;
    @Autowired SignupService signupService;
    @Autowired PlatformTransactionManager txManager;
    @Autowired JdbcTemplate jdbc;

    TransactionTemplate tx;

    @BeforeEach
    void setup() {
        tx = new TransactionTemplate(txManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        jdbc.execute("SET GLOBAL innodb_print_all_deadlocks = ON");
    }

    @AfterEach
    void cleanup() {
        userRepository.deleteAll();
    }

    /**
     * 세 트랜잭션이 같은 unique 값으로 INSERT 를 시도할 때 데드락이 발생한다.
     * - T1: 같은 email INSERT 후 commit/rollback 을 잠시 미룬다 (slot 1)
     * - T2, T3: 같은 email INSERT → T1 의 충돌 row 에 묵시적 S-lock 들이 공존
     * - T1 이 rollback 된 직후, T2/T3 양쪽이 자기 row INSERT 를 위해 X-lock 격상 시도 → 데드락
     */
    @Test
    void 안티패턴_같은_email_세_트랜잭션_동시_INSERT_는_데드락을_일으킨다() throws Exception {
        int attempts = 8;
        boolean deadlockObserved = false;
        String capturedDeadlockSection = null;

        for (int round = 0; round < attempts && !deadlockObserved; round++) {
            userRepository.deleteAll();

            String email = "race-" + UUID.randomUUID() + "@test.com";

            var t1Inserted = new CountDownLatch(1);
            var t2t3Started = new CountDownLatch(2);
            var failures = new ConcurrentLinkedQueue<Throwable>();
            var executor = Executors.newFixedThreadPool(3);

            // T1 — 먼저 같은 email INSERT 후 잠시 대기 → rollback.
            Runnable t1 = () -> {
                try {
                    tx.executeWithoutResult(status -> {
                        signupService.register(new SignupForm(
                            email,
                            "nick-t1-" + UUID.randomUUID(),
                            "phone-t1-" + UUID.randomUUID()
                        ));
                        t1Inserted.countDown();
                        try {
                            // T2, T3 가 같은 email INSERT 를 시작하기를 기다린다.
                            t2t3Started.await(5, TimeUnit.SECONDS);
                            Thread.sleep(300); // T2/T3 가 S-lock 을 잡을 시간을 준다
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        status.setRollbackOnly();
                    });
                } catch (Throwable t) {
                    failures.add(t);
                }
            };

            Runnable t2t3 = () -> {
                try {
                    t1Inserted.await(5, TimeUnit.SECONDS);
                    t2t3Started.countDown();
                    tx.executeWithoutResult(status ->
                        signupService.register(new SignupForm(
                            email,
                            "nick-other-" + UUID.randomUUID(),
                            "phone-other-" + UUID.randomUUID()
                        ))
                    );
                } catch (Throwable t) {
                    failures.add(t);
                }
            };

            executor.submit(t1);
            executor.submit(t2t3);
            executor.submit(t2t3);
            executor.shutdown();
            executor.awaitTermination(20, TimeUnit.SECONDS);

            if (failures.stream().anyMatch(ConcurrencyTestSupport::isDeadlock)) {
                deadlockObserved = true;
                String status = DeadlockStatusCapture.fetchLatestDeadlock(jdbc);
                String latest = DeadlockStatusCapture.extractLatestDetectedDeadlock(status);
                if (latest.contains("LATEST DETECTED DEADLOCK") && latest.contains("b_user")) {
                    capturedDeadlockSection = latest;
                }
            }
        }

        assertThat(deadlockObserved)
            .as("3 트랜잭션 동시 같은 email INSERT 는 데드락을 일으킨다")
            .isTrue();
        assertThat(capturedDeadlockSection)
            .as("LATEST DETECTED DEADLOCK 섹션에 b_user 가 등장")
            .isNotNull();
        DeadlockStatusCapture.saveToFile(
            Path.of(".claude/deadlock/blog/03-공유락-격상/관측-signup.txt"),
            capturedDeadlockSection);
    }

    @Test
    void 처방_retry_전략은_DuplicateSignupException_으로_변환한다() {
        String email = "dup-" + UUID.randomUUID() + "@test.com";
        signupService.registerSafe(new SignupForm(email, "nick-" + UUID.randomUUID(), "p-" + UUID.randomUUID()));

        try {
            signupService.registerSafe(new SignupForm(email, "nick-" + UUID.randomUUID(), "p-" + UUID.randomUUID()));
        } catch (DuplicateSignupException e) {
            assertThat(e.getMessage()).contains("이미 사용 중");
            return;
        }
        throw new AssertionError("DuplicateSignupException 이 던져져야 한다");
    }
}
