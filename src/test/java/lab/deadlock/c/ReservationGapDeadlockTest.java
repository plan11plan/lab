package lab.deadlock.c;

import lab.deadlock.c.reservation.Reservation;
import lab.deadlock.c.reservation.ReservationRepository;
import lab.deadlock.c.reservation.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §4.4 직접 재현해보기 — Range/Gap Cycle (회의실 예약).
 *
 * 시나리오:
 *   T1 = reserve(room, 10:00, 12:00), T2 = reserve(room, 14:00, 16:00).
 *   각자 다른 시간대로 overlap SELECT FOR UPDATE → 두 트랜잭션이 같은 인덱스 트리의
 *   빈 영역(gap)에 next-key/gap lock을 보유한 상태.
 *   그 뒤 T1은 13~15시(T2 슬롯 침범), T2는 11~13시(T1 슬롯 침범)로 INSERT 시도.
 *   insert intention lock이 상대 gap에 막히면서 cycle이 닫힌다.
 *
 * §4.3 (c) "빈 회의실도 잠긴다" 검증을 메인 시나리오로 둔다 —
 * 예약이 0건인 회의실에서도 동일하게 데드락이 발생한다는 점이 핵심 메시지이기 때문.
 */
class ReservationGapDeadlockTest extends ReservationDeadlockTestBase {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository repository;

    private static final LocalDateTime SLOT1_START = LocalDateTime.of(2026, 5, 16, 10, 0);
    private static final LocalDateTime SLOT1_END   = LocalDateTime.of(2026, 5, 16, 12, 0);
    private static final LocalDateTime SLOT2_START = LocalDateTime.of(2026, 5, 16, 14, 0);
    private static final LocalDateTime SLOT2_END   = LocalDateTime.of(2026, 5, 16, 16, 0);

    // T1이 자기 슬롯 잠근 뒤 시도할 INSERT — T2 슬롯과 겹치는 13~15시
    private static final LocalDateTime CROSS_FROM_T1_START = LocalDateTime.of(2026, 5, 16, 13, 0);
    private static final LocalDateTime CROSS_FROM_T1_END   = LocalDateTime.of(2026, 5, 16, 15, 0);

    // T2가 자기 슬롯 잠근 뒤 시도할 INSERT — T1 슬롯과 겹치는 11~13시
    private static final LocalDateTime CROSS_FROM_T2_START = LocalDateTime.of(2026, 5, 16, 11, 0);
    private static final LocalDateTime CROSS_FROM_T2_END   = LocalDateTime.of(2026, 5, 16, 13, 0);

    private static final Path OBSERVATION_PATH = Path.of(
        System.getProperty("user.dir"),
        ".claude/deadlock/blog/04-빈범위-INSERT/관측.txt"
    );

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("§4.4 메인 시나리오 — 빈 회의실에서 두 트랜잭션의 overlap+교차 INSERT가 데드락을 만든다")
    void empty_room_overlap_and_cross_insert_makes_deadlock() throws Exception {
        // room_id=1: 예약 0건
        // 인덱스 트리에는 supremum pseudo-record만 있다.
        // 두 트랜잭션의 overlap SELECT는 모두 supremum 쪽 gap에 next-key lock을 잡고,
        // 그 후 INSERT가 잡으려는 insert intention lock이 상대의 gap lock에 막히면서 cycle을 만든다.
        boolean deadlockHit = runScenarioWithCycle(1L);

        if (deadlockHit) {
            captureInnodbStatus("§4.4 빈 회의실에서 overlap + 교차 INSERT 데드락");
        }
        assertThat(deadlockHit)
            .as("§4.3 (c) — 예약이 하나도 없는 회의실에서도 overlap+INSERT 패턴은 데드락을 만든다")
            .isTrue();
    }

    @Test
    @DisplayName("§4.6 (a) RC로 낮추면 gap lock이 사라져 데드락이 안 난다")
    void read_committed_removes_gap_lock_so_no_deadlock() throws Exception {
        boolean deadlockUnderRc = runScenarioWithCycleUnderRc(2L);

        assertThat(deadlockUnderRc)
            .as("RC에서는 gap lock이 사라지므로 동일 시나리오에서 데드락이 발생하지 않는다")
            .isFalse();
    }

    @Test
    @DisplayName("§4.6 (a) trade-off — RC는 같은 시간대 동시 reserve를 둘 다 통과시켜 이중 예약을 만든다")
    void read_committed_allows_double_booking_for_same_slot() throws Exception {
        // 같은 회의실, 같은 시간대(10~12시)에 두 트랜잭션이 동시에 reserve 호출.
        // RC에서는 두 트랜잭션이 모두 overlap 검사 시점에 0건을 보고, 각자 INSERT를 통과한다.
        boolean bothInserted = runDoubleBookingUnderRc(3L);

        assertThat(bothInserted)
            .as("RC에서는 같은 시간대로 동시 reserve 시 두 트랜잭션 모두 INSERT 성공한다 (이중 예약)")
            .isTrue();

        long sameSlotCount = repository.findAll().stream()
            .filter(r -> r.getRoomId().equals(3L))
            .count();
        assertThat(sameSlotCount)
            .as("이중 예약이 실제로 발생했는지 — room=3의 row 개수")
            .isEqualTo(2);
    }

    // ----------------------------------------------------------------
    // 시나리오 본체
    // ----------------------------------------------------------------

    private boolean runScenarioWithCycle(Long roomId) throws Exception {
        TransactionTemplate rr = newRepeatableReadTemplate();
        return runCrossInsertScenario(roomId, rr);
    }

    private boolean runScenarioWithCycleUnderRc(Long roomId) throws Exception {
        TransactionTemplate rc = newReadCommittedTemplate();
        return runCrossInsertScenario(roomId, rc);
    }

    private boolean runCrossInsertScenario(Long roomId, TransactionTemplate template) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

        executor.submit(() -> {
            try {
                template.executeWithoutResult(status -> {
                    // step 1: T1은 10~12시 슬롯 overlap SELECT FOR UPDATE
                    List<Reservation> conflicts = reservationRepository.findOverlappingForUpdate(
                        roomId, SLOT1_START, SLOT1_END);
                    awaitBarrier(barrier);
                    // step 2: T2 슬롯을 침범하는 INSERT (13~15시)
                    if (conflicts.isEmpty()) {
                        reservationRepository.save(new Reservation(roomId, CROSS_FROM_T1_START, CROSS_FROM_T1_END));
                    }
                    reservationRepository.flush();
                });
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        executor.submit(() -> {
            try {
                template.executeWithoutResult(status -> {
                    // step 1: T2는 14~16시 슬롯 overlap SELECT FOR UPDATE
                    List<Reservation> conflicts = reservationRepository.findOverlappingForUpdate(
                        roomId, SLOT2_START, SLOT2_END);
                    awaitBarrier(barrier);
                    // step 2: T1 슬롯을 침범하는 INSERT (11~13시)
                    if (conflicts.isEmpty()) {
                        reservationRepository.save(new Reservation(roomId, CROSS_FROM_T2_START, CROSS_FROM_T2_END));
                    }
                    reservationRepository.flush();
                });
            } catch (Throwable t) {
                failures.add(t);
            }
        });

        executor.shutdown();
        boolean finished = executor.awaitTermination(60, TimeUnit.SECONDS);
        if (!finished) {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        return failures.stream().anyMatch(ConcurrencyTestSupport::isDeadlock);
    }

    private boolean runDoubleBookingUnderRc(Long roomId) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> successes = new ConcurrentLinkedQueue<>();
        TransactionTemplate rc = newReadCommittedTemplate();

        Runnable task = () -> {
            try {
                rc.executeWithoutResult(status -> {
                    List<Reservation> conflicts = reservationRepository.findOverlappingForUpdate(
                        roomId, SLOT1_START, SLOT1_END);
                    awaitBarrier(barrier);
                    if (conflicts.isEmpty()) {
                        reservationRepository.save(new Reservation(roomId, SLOT1_START, SLOT1_END));
                    }
                    reservationRepository.flush();
                });
                successes.add(Thread.currentThread().getName());
            } catch (Throwable t) {
                failures.add(t);
            }
        };

        executor.submit(task);
        executor.submit(task);
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        return successes.size() == 2 && failures.isEmpty();
    }

    private static void awaitBarrier(CyclicBarrier b) {
        try {
            b.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * SHOW ENGINE INNODB STATUS의 LATEST DETECTED DEADLOCK 부분을 캡처해 파일에 적재.
     * lab user에 PROCESS 권한이 없으므로, testcontainer 안에서 mysql CLI를 root로 실행해 출력을 가져온다.
     */
    private void captureInnodbStatus(String header) {
        String fullStatus;
        try {
            fullStatus = execShowInnodbStatusAsRoot();
        } catch (Exception e) {
            System.out.println("[CAPTURE] INNODB STATUS 캡처 실패 (root exec): " + e.getMessage());
            return;
        }
        if (fullStatus == null || fullStatus.isBlank()) {
            System.out.println("[CAPTURE] INNODB STATUS 캡처 결과가 비어 있음");
            return;
        }

        try {
            Files.createDirectories(OBSERVATION_PATH.getParent());
            // 매 실행마다 파일을 새로 쓴다(누적 누락 방지)
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(OBSERVATION_PATH,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING))) {
                pw.println("# SHOW ENGINE INNODB STATUS 캡처 — Range/Gap Cycle 데드락");
                pw.println("# Testcontainers MySQL 8.0, REPEATABLE READ");
                pw.println("# --innodb-print-all-deadlocks=ON, --innodb-lock-wait-timeout=50");
                pw.println();
                pw.println("=================================================================");
                pw.println("[" + header + "] " + java.time.LocalDateTime.now());
                pw.println("=================================================================");

                String section = extractLatestDeadlockSection(fullStatus);
                pw.println(section);
                pw.println();
            }
            System.out.println("[CAPTURE] wrote to " + OBSERVATION_PATH);
        } catch (Exception e) {
            System.out.println("[CAPTURE] INNODB STATUS 파일 적재 실패: " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }

    private String execShowInnodbStatusAsRoot() throws Exception {
        String[] passwordsToTry = { MYSQL.getPassword(), "test", "" };
        for (String pwd : passwordsToTry) {
            var result = MYSQL.execInContainer(
                "mysql",
                "-uroot",
                pwd.isEmpty() ? "--password=" : ("-p" + pwd),
                "-N",
                "-B",
                "-e",
                "SHOW ENGINE INNODB STATUS\\G"
            );
            if (result.getExitCode() == 0) {
                return result.getStdout();
            }
        }
        throw new IllegalStateException("root 인증 실패 — SHOW ENGINE INNODB STATUS 실행 불가");
    }

    private static String extractLatestDeadlockSection(String fullStatus) {
        int start = fullStatus.indexOf("LATEST DETECTED DEADLOCK");
        if (start < 0) {
            return "(LATEST DETECTED DEADLOCK 섹션이 없습니다)\n\n"
                + fullStatus.substring(0, Math.min(fullStatus.length(), 4000));
        }
        int end = fullStatus.indexOf("TRANSACTIONS", start + "LATEST DETECTED DEADLOCK".length());
        if (end < 0) end = Math.min(fullStatus.length(), start + 8000);
        // TRANSACTIONS 직전의 구분선까지 같이 잘라낸다
        int hr = fullStatus.lastIndexOf("\n------------\n", end);
        if (hr > start) end = hr;
        return fullStatus.substring(start, end);
    }
}
