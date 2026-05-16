package lab.deadlock.c.reservation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r FROM Reservation r
        WHERE r.roomId = :roomId
          AND r.startTime < :end
          AND r.endTime   > :start
        """)
    List<Reservation> findOverlappingForUpdate(
        @Param("roomId") Long roomId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);
}
